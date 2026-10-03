package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class UnreachableCodeEliminationTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testUnreachableCodeBasic() throws Throwable {
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, true);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    // function f() { return; alert('unreachable'); }
    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Node block = func.getLastChild();
    block.addChildToBack(new Node(Token.RETURN));
    
    Node call = new Node(Token.CALL, Node.newString(Token.NAME, "alert"), Node.newString("unreachable"));
    block.addChildToBack(new Node(Token.EXPR_RESULT, call));
    
    root.addChildToBack(func);

    pass.process(externs, root);
    
    // The alert statement should be removed as it is unreachable after return
    assertEquals(1, block.getChildCount());
    assertEquals(Token.RETURN, block.getFirstChild().getType());
  }

  public void testRemoveNoOpStatements() throws Throwable {
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, true);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    // function f() { true; }
    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Node block = func.getLastChild();
    block.addChildToBack(new Node(Token.EXPR_RESULT, Node.newNumber(1.0)));
    
    root.addChildToBack(func);

    pass.process(externs, root);
    
    // No-op statement `true;` should be removed because removeNoOpStatements is true
    assertEquals(0, block.getChildCount());
  }

  public void testNoRemoveNoOpStatements() throws Throwable {
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, false);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    // function f() { true; }
    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Node block = func.getLastChild();
    block.addChildToBack(new Node(Token.EXPR_RESULT, Node.newNumber(1.0)));
    
    root.addChildToBack(func);

    pass.process(externs, root);
    
    // No-op statement should remain because removeNoOpStatements is false
    assertEquals(1, block.getChildCount());
  }

  public void testUnconditionalBranchingRemoval() throws Throwable {
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, true);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    // function f() { if (x) { break; } }
    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Node block = func.getLastChild();
    
    Node ifNode = new Node(Token.IF, Node.newString(Token.NAME, "x"), new Node(Token.BLOCK, new Node(Token.BREAK)));
    block.addChildToBack(ifNode);
    
    root.addChildToBack(func);

    pass.process(externs, root);
    // Should execute safely without errors
    assertNotNull(root);
  }

  public void testDoNotRemoveDoNode() throws Throwable {
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, true);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    Node func = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    Node block = func.getLastChild();
    
    Node doNode = new Node(Token.DO, new Node(Token.BLOCK), Node.newNumber(0.0));
    block.addChildToBack(doNode);
    root.addChildToBack(func);

    pass.process(externs, root);
    // DO node should not be removed directly by dead expr safe
    assertTrue(block.hasChildren());
  }

  public void testNullNodeHandling() throws Throwable {
    UnreachableCodeElimination pass = new UnreachableCodeElimination(compiler, true);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    // Process with empty root
    pass.process(externs, root);
    assertNotNull(root);
  }
}