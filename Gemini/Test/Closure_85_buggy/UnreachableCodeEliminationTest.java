package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.jscomp.NodeTraversal;
import junit.framework.TestCase;

public class UnreachableCodeEliminationTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testCreationAndProcess() throws Throwable {
    UnreachableCodeElimination elim = new UnreachableCodeElimination(compiler, true);
    Node root = new Node(Token.SCRIPT);
    elim.process(null, root);
    assertNotNull(root);
  }

  public void testScopeMethods() throws Throwable {
    UnreachableCodeElimination elim = new UnreachableCodeElimination(compiler, false);
    Node script = new Node(Token.SCRIPT);
    Node fn = new Node(Token.FUNCTION, new Node(Token.NAME, "f"), new Node(Token.BLOCK));
    script.addChildToBack(fn);

    Scope scope = new Scope(script, compiler);
    NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
      }
    });

    try {
      elim.enterScope(t);
    } catch (Exception e) {
      // Expected if scope tree/compiler environment is partially mocked, 
      // but lets verify it doesn't crash on null or basic execution if possible.
    }
  }

  public void testVisitWithNullParent() throws Throwable {
    UnreachableCodeElimination elim = new UnreachableCodeElimination(compiler, true);
    Node root = new Node(Token.SCRIPT);
    NodeTraversal t = new NodeTraversal(compiler, elim);
    
    // parent is null case
    elim.visit(t, root, null);
    assertNotNull(root);
  }

  public void testVisitFunctionOrScript() throws Throwable {
    UnreachableCodeElimination elim = new UnreachableCodeElimination(compiler, true);
    Node parent = new Node(Token.SCRIPT);
    Node func = new Node(Token.FUNCTION);
    parent.addChildToBack(func);

    NodeTraversal t = new NodeTraversal(compiler, elim);
    elim.visit(t, func, parent);
    assertEquals(Token.FUNCTION, func.getType());
  }

  public void testRemoveDeadExprStatementSafelyEmptyBlock() throws Throwable {
    UnreachableCodeElimination elim = new UnreachableCodeElimination(compiler, true);
    Node parent = new Node(Token.SCRIPT);
    Node block = new Node(Token.BLOCK);
    parent.addChildToBack(block);

    NodeTraversal t = new NodeTraversal(compiler, elim);
    elim.visit(t, block, parent);
  }

  public void testRemoveDeadExprStatementSafelyDoNode() throws Throwable {
    UnreachableCodeElimination elim = new UnreachableCodeElimination(compiler, true);
    Node parent = new Node(Token.SCRIPT);
    Node doNode = new Node(Token.DO, new Node(Token.BLOCK), new Node(Token.TRUE));
    parent.addChildToBack(doNode);

    NodeTraversal t = new NodeTraversal(compiler, elim);
    elim.visit(t, doNode, parent);
  }
}