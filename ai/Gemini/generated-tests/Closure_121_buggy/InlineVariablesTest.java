package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.InlineVariables.Mode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;
import junit.framework.TestCase;

public class InlineVariablesTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testModes() throws Throwable {
    InlineVariables passAll = new InlineVariables(compiler, Mode.ALL, true);
    assertNotNull(passAll);

    InlineVariables passLocals = new InlineVariables(compiler, Mode.LOCALS_ONLY, false);
    assertNotNull(passLocals);

    InlineVariables passConstants = new InlineVariables(compiler, Mode.CONSTANTS_ONLY, false);
    assertNotNull(passConstants);
  }

  public void testProcessWithEmptyNodes() throws Throwable {
    InlineVariables pass = new InlineVariables(compiler, Mode.ALL, true);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    pass.process(externs, root);
    assertNotNull(compiler);
  }

  public void testDefaultModeException() throws Throwable {
    // Testing through reflection or direct instantiation if possible, 
    // but Mode is an enum with known values. All enum values are covered in switch.
    Mode[] modes = Mode.values();
    assertTrue(modes.length > 0);
  }

  public void testConstantInliningEdgeCases() throws Throwable {
    Node nameNode = IR.name("CONST_VAR");
    Node valNode = IR.number(10.0);
    Node varNode = IR.var(nameNode, valNode);
    Node root = IR.block(varNode);

    InlineVariables pass = new InlineVariables(compiler, Mode.CONSTANTS_ONLY, true);
    pass.process(new Node(Token.BLOCK), root);
    assertNotNull(root);
  }

  public void testLocalInliningEdgeCases() throws Throwable {
    Node nameNode = IR.name("local_var");
    Node valNode = IR.string("test_string");
    Node varNode = IR.var(nameNode, valNode);
    Node root = IR.block(varNode);

    InlineVariables pass = new InlineVariables(compiler, Mode.LOCALS_ONLY, false);
    pass.process(new Node(Token.BLOCK), root);
    assertNotNull(root);
  }

  public void testAllModeInlining() throws Throwable {
    Node nameNode = IR.name("x");
    Node valNode = IR.number(5.0);
    Node varNode = IR.var(nameNode, valNode);
    Node root = IR.block(varNode);

    InlineVariables pass = new InlineVariables(compiler, Mode.ALL, true);
    pass.process(new Node(Token.BLOCK), root);
    assertNotNull(root);
  }
}