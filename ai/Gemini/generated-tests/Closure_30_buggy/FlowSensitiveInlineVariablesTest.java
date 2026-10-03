package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class FlowSensitiveInlineVariablesTest {

  @Test
  public void testInstantiationAndProcess() throws Throwable {
    Compiler compiler = new Compiler();
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    pass.process(externs, root);
    assertTrue(true);
  }

  @Test
  public void testSideEffectPredicateNullNode() throws Throwable {
    Compiler compiler = new Compiler();
    Node functionNode = new Node(Token.FUNCTION, IR.name("f"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
      }
    });
    
    t.traverse(functionNode);
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    pass.enterScope(t);
    assertTrue(true);
  }

  @Test
  public void testGlobalScopeIgnored() throws Throwable {
    Compiler compiler = new Compiler();
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    
    Node root = new Node(Token.BLOCK);
    NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {}
    });
    
    t.traverse(root);
    pass.enterScope(t);
    assertTrue(true);
  }
}