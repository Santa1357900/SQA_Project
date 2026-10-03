package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class DeadAssignmentsEliminationClaudeTest {

  private static String normalize(String s) {
    return s.replaceAll("\\s+", "");
  }

  private Result compileCode(String code) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setDeadAssignmentElimination(true);
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", code));
    return compiler.compile(externs, inputs, options);
  }

  private String compileAndGetSource(String code) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setDeadAssignmentElimination(true);
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", code));
    compiler.compile(externs, inputs, options);
    return normalize(compiler.toSource());
  }

  // covers Preconditions.checkNotNull(externs) in process()
  @Test
  public void testConstructorAndProcess_nullExterns_throwsNullPointerException() throws Throwable {
    Compiler compiler = new Compiler();
    DeadAssignmentsElimination pass = new DeadAssignmentsElimination(compiler);
    Node root = IR.block();
    try {
      pass.process(null, root);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // covers Preconditions.checkNotNull(root) in process()
  @Test
  public void testConstructorAndProcess_nullRoot_throwsNullPointerException() throws Throwable {
    Compiler compiler = new Compiler();
    DeadAssignmentsElimination pass = new DeadAssignmentsElimination(compiler);
    Node externs = IR.block();
    try {
      pass.process(externs, null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // covers enterScope() early return when scope.isGlobal() is true
  @Test
  public void testProcess_globalScopeAssignment_notRemoved() throws Throwable {
    String code = "var x = 0; x = 1; x = 2;";
    String out = compileAndGetSource(code);
    assertTrue(out.contains("x=1"));
    assertTrue(out.contains("x=2"));
  }

  // covers enterScope() early return when no removable assigns exist
  @Test
  public void testProcess_noRemovableAssigns_unchanged() throws Throwable {
    String code = "function f(a) { return a; }";
    String out = compileAndGetSource(code);
    assertTrue(out.contains("returna"));
  }

  // covers zero-iteration loop over cfg nodes for a trivial empty function
  @Test
  public void testProcess_emptyFunctionBody_noCrash() throws Throwable {
    Result result = compileCode("function f() {}");
    assertTrue(result.success);
  }

  // covers plain ASSIGN removal when variable is dead after assignment
  @Test
  public void testProcess_simpleDeadAssignment_removed() throws Throwable {
    String code = "function f() { var x; x = 1; x = 2; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x=1"));
    assertTrue(out.contains("x=2"));
    assertTrue(out.contains("returnx"));
  }

  // covers state.getOut().isLive(var) returning true, assignment preserved
  @Test
  public void testProcess_liveAssignment_notRemoved() throws Throwable {
    String code = "function f() { var x = 0; x = 1; return x; }";
    String out = compileAndGetSource(code);
    assertTrue(out.contains("x=1"));
  }

  // covers liveness across a conditional branch keeping assignment alive
  @Test
  public void testProcess_assignmentLiveThroughConditionalBranch_notRemoved() throws Throwable {
    String code = "function f(a) { var x = 0; x = 1; if (a) { return x; } return 0; }";
    String out = compileAndGetSource(code);
    assertTrue(out.contains("x=1"));
  }

  // covers identity assignment x=x always removed regardless of liveness
  @Test
  public void testProcess_identityAssignment_alwaysRemoved() throws Throwable {
    String code = "function f() { var x = 0; x = x; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x=x"));
    assertTrue(out.contains("returnx"));
  }

  // covers INC as dead statement replaced with void 0
  @Test
  public void testProcess_deadIncrementStatement_replacedWithVoid() throws Throwable {
    String code = "function f() { var x = 0; x++; x = 5; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x++"));
    assertTrue(out.contains("void0"));
    assertTrue(out.contains("x=5"));
  }

  // covers DEC as dead statement replaced with void 0
  @Test
  public void testProcess_deadDecrementStatement_replacedWithVoid() throws Throwable {
    String code = "function f() { var x = 0; x--; x = 5; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x--"));
    assertTrue(out.contains("void0"));
    assertTrue(out.contains("x=5"));
  }

  // covers compound assignment op converted to plain binary op when dead
  @Test
  public void testProcess_deadCompoundAssignment_convertedToBinaryOp() throws Throwable {
    String code = "function f() { var x = 0; x += 1; x = 5; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x+=1"));
    assertTrue(out.contains("x+1"));
    assertTrue(out.contains("x=5"));
  }

  // covers Token.IF case in tryRemoveDeadAssignments
  @Test
  public void testProcess_ifConditionDeadAssignment_removed() throws Throwable {
    String code = "function f(a) { var x = 0; if (x = a) { } x = 2; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x=a"));
    assertTrue(out.contains("if(a)"));
    assertTrue(out.contains("x=2"));
  }

  // covers Token.WHILE case in tryRemoveDeadAssignments
  @Test
  public void testProcess_whileConditionDeadAssignment_removed() throws Throwable {
    String code = "function f(a) { var x = 0; while (x = a) { } x = 2; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x=a"));
    assertTrue(out.contains("x=2"));
  }

  // covers Token.DO case in tryRemoveDeadAssignments, verifies no crash
  @Test
  public void testProcess_doWhileCondition_noCrash() throws Throwable {
    String code = "function f(a) { var x = 0; do { } while (x = a); x = 2; return x; }";
    Result result = compileCode(code);
    assertTrue(result.success);
  }

  // covers Token.FOR non-forin branch in tryRemoveDeadAssignments
  @Test
  public void testProcess_forConditionOnlyDeadAssignment_removed() throws Throwable {
    String code = "function f(a) { var x = 0; for (; x = a; ) { } x = 2; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x=a"));
    assertTrue(out.contains("x=2"));
  }

  // covers Token.FOR forin skip branch (NodeUtil.isForIn true)
  @Test
  public void testProcess_forInLoop_noCrash() throws Throwable {
    String code = "function f(obj) { var x = 0; for (var k in obj) { x = k; } x = 2; return x; }";
    Result result = compileCode(code);
    assertTrue(result.success);
  }

  // covers Token.SWITCH case using first child as discriminant expression
  @Test
  public void testProcess_switchDiscriminantDeadAssignment_removed() throws Throwable {
    String code = "function f(a, b) { var x = 0; switch (x = b) { case 1: break; } x = 2; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("switch(x=b)"));
    assertTrue(out.contains("switch(b)"));
    assertTrue(out.contains("x=2"));
  }

  // covers Token.CASE case using first child as case expression
  @Test
  public void testProcess_caseExpressionDeadAssignment_removed() throws Throwable {
    String code = "function f(a, b) { var x = 0; switch (a) { case x = b: break; } x = 2; return x; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("casex=b"));
    assertTrue(out.contains("caseb"));
    assertTrue(out.contains("x=2"));
  }

  // covers Token.RETURN case and recursive dead-assignment removal inside it
  @Test
  public void testProcess_returnExpressionDeadAssignment_removed() throws Throwable {
    String code = "function f() { var x = 0; x = 1; return x = 2; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("returnx"));
    assertTrue(out.contains("return2"));
    assertFalse(out.contains("x=1"));
  }

  // covers enterScope skip when NodeUtil.containsFunction(fnBlock) is true
  @Test
  public void testProcess_functionWithInnerFunction_assignmentsPreserved() throws Throwable {
    String code = "function outer() { var x = 0; x = 1; function inner() { return x; } "
        + "x = 2; return inner(); }";
    String out = compileAndGetSource(code);
    assertTrue(out.contains("x=1"));
  }

  // covers that a nested function's own scope is still processed independently
  @Test
  public void testProcess_innerFunctionOwnScope_deadAssignmentRemoved() throws Throwable {
    String code = "function outer() { function inner() { var y = 0; y = 1; y = 2; return y; } "
        + "return inner(); }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("y=1"));
    assertTrue(out.contains("y=2"));
    assertTrue(out.contains("returny"));
  }

  // regression for isVariableReadBeforeKill mishandling INC as LHS (Closure-76)
  @Test
  public void testProcess_incInLogicalAndExpression_doesNotThrowIllegalState() throws Throwable {
    String code = "function f(a) { var x = 0; if ((x = a) && (x++)) { } x = 2; return x; }";
    Result result = compileCode(code);
    assertTrue(result.success);
  }

  // regression for isVariableReadBeforeKill mishandling DEC as LHS (Closure-76)
  @Test
  public void testProcess_decInLogicalAndExpression_doesNotThrowIllegalState() throws Throwable {
    String code = "function f(a) { var x = 0; if ((x = a) && (x--)) { } x = 2; return x; }";
    Result result = compileCode(code);
    assertTrue(result.success);
  }

  // covers NodeTraversal visiting multiple independent function scopes
  @Test
  public void testProcess_multipleFunctions_eachScopeProcessedIndependently() throws Throwable {
    String code = "function f() { var x = 0; x = 1; x = 2; return x; } "
        + "function g() { var y = 0; y = 1; return y; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x=1"));
    assertTrue(out.contains("x=2"));
    assertTrue(out.contains("y=1"));
    assertTrue(out.contains("returny"));
  }

  // covers recursive rhs-first cleanup for chained assignment x = y = 1
  @Test
  public void testProcess_chainedAssignment_bothDeadRemoved() throws Throwable {
    String code = "function f() { var x, y; x = y = 1; x = 2; y = 3; return x + y; }";
    String out = compileAndGetSource(code);
    assertFalse(out.contains("x=y"));
    assertFalse(out.contains("y=1"));
    assertTrue(out.contains("x=2"));
    assertTrue(out.contains("y=3"));
  }
}
