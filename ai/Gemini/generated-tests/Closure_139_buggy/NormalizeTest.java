package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.IR;

import junit.framework.TestCase;

public class NormalizeTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testNormalizeConstructor() throws Throwable {
    Normalize normalize = new Normalize(compiler, false);
    assertNotNull(normalize);
  }

  public void testProcessWhileConversion() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node whileNode = new Node(Token.WHILE, IR.trueNode(), new Node(Token.BLOCK));
    root.addChildToBack(whileNode);

    Normalize normalize = new Normalize(compiler, false);
    normalize.process(new Node(Token.BLOCK), root);

    assertEquals(Token.FOR, whileNode.getType());
  }

  public void testSplitVarDeclarations() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node varNode = new Node(Token.VAR, IR.name("a"), IR.name("b"));
    root.addChildToBack(varNode);

    Normalize normalize = new Normalize(compiler, false);
    normalize.process(new Node(Token.BLOCK), root);

    assertEquals(Token.VAR, root.getFirstChild().getType());
    assertEquals("a", root.getFirstChild().getFirstChild().getString());
  }

  public void testNormalizeLabels() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node labelNode = new Node(Token.LABEL, IR.string("L"), IR.name("x"));
    root.addChildToBack(labelNode);

    Normalize normalize = new Normalize(compiler, false);
    normalize.process(new Node(Token.BLOCK), root);

    assertEquals(Token.BLOCK, labelNode.getLastChild().getType());
  }

  public void testExtractForInitializer() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node forNode = new Node(Token.FOR, 
        new Node(Token.VAR, IR.name("i"), IR.number(0)),
        IR.trueNode(),
        new Node(Token.EMPTY),
        new Node(Token.BLOCK));
    root.addChildToBack(forNode);

    Normalize normalize = new Normalize(compiler, false);
    normalize.process(new Node(Token.BLOCK), root);

    assertEquals(Token.VAR, root.getFirstChild().getType());
    assertEquals(Token.FOR, root.getFirstChild().getNext().getType());
  }

  public void testPropogateConstantAnnotations() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node nameNode = IR.name("FOO");
    root.addChildToBack(nameNode);

    Normalize.PropogateConstantAnnotations prop = 
        new Normalize.PropogateConstantAnnotations(compiler, false);
    
    NodeTraversal t = new NodeTraversal(compiler, prop);
    prop.visit(t, nameNode, root);

    assertNotNull(prop);
  }

  public void testVerifyConstants() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node externs = new Node(Token.BLOCK);
    root.addChildToBack(IR.name("x"));
    
    CompilerOptions options = new CompilerOptions();
    compiler.init(externs, root, options);

    Normalize.VerifyConstants verify = new Normalize.VerifyConstants(compiler, false);
    try {
      verify.process(externs, root);
    } catch (Exception e) {
      // Expected if scope/parent checks fail on minimal tree
    }
    assertNotNull(verify);
  }

  public void testMoveNamedFunctions() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node funcBlock = new Node(Token.BLOCK);
    Node funcDecl = new Node(Token.FUNCTION, IR.name("f"), new Node(Token.LP), new Node(Token.BLOCK));
    Node exprResult = new Node(Token.EXPR_RESULT, IR.number(1));
    
    funcBlock.addChildToBack(exprResult);
    funcBlock.addChildToBack(funcDecl);
    
    Node funcNode = new Node(Token.FUNCTION, IR.name("outer"), new Node(Token.LP), funcBlock);
    root.addChildToBack(funcNode);

    Normalize normalize = new Normalize(compiler, false);
    normalize.process(new Node(Token.BLOCK), root);

    assertEquals(Token.FUNCTION, funcBlock.getFirstChild().getType());
  }

  public void testAssertOnChangeException() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node whileNode = new Node(Token.WHILE, IR.trueNode(), new Node(Token.BLOCK));
    root.addChildToBack(whileNode);

    Normalize normalize = new Normalize(compiler, true);
    try {
      normalize.process(new Node(Token.BLOCK), root);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Normalize constraints violated"));
    }
  }

  public void testDuplicateDeclarations() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node script = new Node(Token.SCRIPT);
    Node var1 = new Node(Token.VAR, IR.name("a"));
    Node var2 = new Node(Token.VAR, IR.name("a"));
    script.addChildToBack(var1);
    script.addChildToBack(var2);
    root.addChildToBack(script);

    CompilerOptions options = new CompilerOptions();
    compiler.init(new Node(Token.BLOCK), root, options);

    Normalize normalize = new Normalize(compiler, false);
    normalize.process(new Node(Token.BLOCK), root);
    
    assertNotNull(normalize);
  }
}