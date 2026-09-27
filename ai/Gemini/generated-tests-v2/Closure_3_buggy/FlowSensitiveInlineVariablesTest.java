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

  public void testFlowSensitiveInlineVariablesPassInitialization() throws Throwable {
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    assertNotNull(pass);

    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    pass.process(externs, root);
  }

  public void testGlobalScopeBypass() throws Throwable {
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.SCRIPT, new Node(Token.NAME, "globalVar"));
    
    NodeTraversal t = new NodeTraversal(compiler, pass);
    pass.enterScope(t);
    pass.exitScope(t);
    assertNotNull(pass);
  }

  public void testSideEffectPredicateWithNull() throws Throwable {
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.FUNCTION, 
        new Node(Token.NAME, ""), 
        new Node(Token.PARAM_LIST), 
        new Node(Token.BLOCK));
    
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    pass.process(externs, root);
    assertNotNull(compiler);
  }
}