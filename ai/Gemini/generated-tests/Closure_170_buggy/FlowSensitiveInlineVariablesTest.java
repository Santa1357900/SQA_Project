package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class FlowSensitiveInlineVariablesTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testProcessGlobalScope() throws Throwable {
    Node root = new Node(Token.BLOCK);
    Node externs = new Node(Token.BLOCK);
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    pass.process(externs, root);
    assertNotNull(compiler);
  }

  public void testSideEffectPredicateNull() throws Throwable {
    Node root = new Node(Token.FUNCTION, new Node(Token.NAME, "test"), new Node(Token.LP), new Node(Token.BLOCK));
    NodeTraversal t = new NodeTraversal(compiler, new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
      }
    });
    t.traverse(root);
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    pass.enterScope(t);
    assertNotNull(compiler);
  }
}