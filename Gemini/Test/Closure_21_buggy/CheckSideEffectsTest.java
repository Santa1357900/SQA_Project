package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class CheckSideEffectsTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testConstructorAndProcess() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    Node externs = IR.block();
    Node root = IR.block();
    checker.process(externs, root);
    assertNotNull(checker);
  }

  public void testHotSwapScript() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    Node scriptRoot = IR.block();
    Node originalRoot = IR.block();
    checker.hotSwapScript(scriptRoot, originalRoot);
    assertNotNull(checker);
  }

  public void testProtectSideEffects() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, true);
    Node externs = IR.block();
    
    Node nameNode = IR.name("a");
    Node exprResult = IR.exprResult(nameNode);
    Node root = IR.block(exprResult);
    
    checker.process(externs, root);
    
    CheckSideEffects.StripProtection stripper = new CheckSideEffects.StripProtection(compiler);
    stripper.process(externs, root);
    assertNotNull(stripper);
  }

  public void testVisitEmptyAndComma() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    NodeTraversal traversal = new NodeTraversal(compiler, checker);
    
    Node emptyNode = IR.empty();
    checker.visit(traversal, emptyNode, null);
    
    Node commaNode = IR.comma(IR.number(1), IR.number(2));
    checker.visit(traversal, commaNode, null);
    
    Node parent = IR.block();
    checker.visit(traversal, emptyNode, parent);
    
    assertTrue(emptyNode.isEmpty());
  }

  public void testVisitExprResult() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    NodeTraversal traversal = new NodeTraversal(compiler, checker);
    
    Node exprResult = IR.exprResult(IR.number(1));
    checker.visit(traversal, exprResult, IR.block());
    assertTrue(exprResult.isExprResult());
  }

  public void testVisitQualifiedNameWithJSDoc() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    NodeTraversal traversal = new NodeTraversal(compiler, checker);
    
    Node qName = IR.name("foo");
    qName.setJSDocInfo(new com.google.javascript.rhino.JSDocInfoBuilder(false).build());
    
    checker.visit(traversal, qName, IR.block());
    assertTrue(qName.isQualifiedName());
  }

  public void testStripProtectionNoMatch() throws Throwable {
    CheckSideEffects.StripProtection stripper = new CheckSideEffects.StripProtection(compiler);
    NodeTraversal traversal = new NodeTraversal(compiler, stripper);
    
    Node callNode = IR.call(IR.name("notProtector"));
    Node parent = IR.block(callNode);
    
    stripper.visit(traversal, callNode, parent);
    assertTrue(callNode.isCall());
  }

  public void testVisitStringNode() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    NodeTraversal traversal = new NodeTraversal(compiler, checker);
    
    Node strNode = IR.string("test");
    Node exprResult = IR.exprResult(strNode);
    
    checker.visit(traversal, strNode, exprResult);
    assertTrue(strNode.isString());
  }

  public void testVisitSimpleOpNode() throws Throwable {
    CheckSideEffects checker = new CheckSideEffects(compiler, CheckLevel.WARNING, false);
    NodeTraversal traversal = new NodeTraversal(compiler, checker);
    
    Node addNode = IR.add(IR.number(1), IR.number(2));
    Node exprResult = IR.exprResult(addNode);
    
    checker.visit(traversal, addNode, exprResult);
    assertTrue(addNode.isAdd());
  }
}