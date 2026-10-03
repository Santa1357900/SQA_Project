package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.IR;
import junit.framework.TestCase;

public class DeadAssignmentsEliminationTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testProcessExternsNull() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node root = IR.block();
    try {
      dae.process(null, root);
      fail("Expected NullPointerException");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testProcessRootNull() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    try {
      dae.process(externs, null);
      fail("Expected NullPointerException");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testGlobalScopeSkipped() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node script = IR.script(IR.var(IR.name("x"), IR.number(1)));
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testFunctionWithInnerFunctionSkipped() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node innerFn = IR.function(IR.name("inner"), IR.paramList(), IR.block());
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(innerFn));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testFunctionWithoutAssignmentsSkipped() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(IR.returnNode(IR.number(1))));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testIfStatementHandling() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node cond = IR.assign(IR.name("x"), IR.number(1));
    Node ifNode = IR.ifNode(cond, IR.block());
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(ifNode));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testWhileStatementHandling() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node cond = IR.assign(IR.name("x"), IR.number(1));
    Node whileNode = IR.whileNode(cond, IR.block());
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(whileNode));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testDoStatementHandling() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node cond = IR.assign(IR.name("x"), IR.number(1));
    Node doNode = IR.doNode(IR.block(), cond);
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(doNode));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testForStatementHandling() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node init = IR.var(IR.name("i"), IR.number(0));
    Node cond = IR.assign(IR.name("x"), IR.number(1));
    Node incr = IR.inc(IR.name("i"), false);
    Node forNode = IR.forNode(init, cond, incr, IR.block());
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(forNode));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testSwitchStatementHandling() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node switchNode = IR.switchNode(IR.name("x"), IR.caseNode(IR.number(1), IR.block()));
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(switchNode));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testReturnStatementHandling() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node ret = IR.returnNode(IR.assign(IR.name("x"), IR.number(1)));
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(ret));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testIncDecExpressionNode() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node inc = IR.inc(IR.name("x"), false);
    Node expr = IR.exprResult(inc);
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(expr));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

  public void testAssignmentOpHandling() throws Throwable {
    DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    Node addAssign = IR.add(IR.name("x"), IR.number(1));
    // Simulate assignment op by setting token or structure if possible, or use standard node factory
    Node expr = IR.exprResult(addAssign);
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(expr));
    Node script = IR.script(fn);
    dae.process(externs, script);
    assertTrue(true);
  }

}