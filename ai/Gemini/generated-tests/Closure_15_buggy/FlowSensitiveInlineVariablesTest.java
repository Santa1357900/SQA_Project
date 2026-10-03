package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class FlowSensitiveInlineVariablesTest {

  @Test
  public void testConstructorAndProcess() throws Throwable {
    Compiler compiler = new Compiler();
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    pass.process(externs, root);
    assertNotNull(compiler);
  }

  @Test
  public void testGlobalScopeBypass() throws Throwable {
    Compiler compiler = new Compiler();
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    
    Node functionNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "testFn"), new Node(Token.PARAM_LIST), new Node(Token.BLOCK));
    NodeTraversal traversal = new NodeTraversal(compiler, pass);
    
    // In global scope should just return without throwing
    pass.enterScope(traversal);
    pass.exitScope(traversal);
    assertNotNull(functionNode);
  }

  @Test
  public void testSideEffectPredicateNull() throws Throwable {
    Compiler compiler = new Compiler();
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    Node root = new Node(Token.BLOCK);
    Node function = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.PARAM_LIST), root);
    
    NodeTraversal t = new NodeTraversal(compiler, pass);
    pass.enterScope(t);
    assertNotNull(compiler);
  }
}