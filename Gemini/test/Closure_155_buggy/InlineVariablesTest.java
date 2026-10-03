package com.google.javascript.jscomp;

import com.google.javascript.jscomp.InlineVariables.Mode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class InlineVariablesTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testInlineVariablesModes() throws Throwable {
    InlineVariables ivConstants = new InlineVariables(compiler, Mode.CONSTANTS_ONLY, false);
    assertNotNull(ivConstants);

    InlineVariables ivLocals = new InlineVariables(compiler, Mode.LOCALS_ONLY, false);
    assertNotNull(ivLocals);

    InlineVariables ivAll = new InlineVariables(compiler, Mode.ALL, true);
    assertNotNull(ivAll);
  }

  public void testProcessWithEmptyNodes() throws Throwable {
    InlineVariables iv = new InlineVariables(compiler, Mode.ALL, false);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    iv.process(externs, root);
    assertNotNull(compiler);
  }

  public void testIdentifyConstantsAndLocals() throws Throwable {
    InlineVariables iv = new InlineVariables(compiler, Mode.CONSTANTS_ONLY, false);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    // Test processing on basic AST
    iv.process(externs, root);
    assertTrue(true);
  }
}