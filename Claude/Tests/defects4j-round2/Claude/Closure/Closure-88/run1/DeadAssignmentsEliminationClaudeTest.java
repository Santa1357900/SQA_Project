package com.google.javascript.jscomp;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class DeadAssignmentsEliminationClaudeTest {

  private String runPass(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    Result result = compiler.compile(externs, inputs, options);
    assertTrue("compilation should succeed", result.success);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node jsRoot = root.getLastChild();
    DeadAssignmentsElimination pass = new DeadAssignmentsElimination(compiler);
    pass.process(externsRoot, jsRoot);
    return compiler.toSource();
  }

  // Constructor should create a usable instance without throwing.
  @Test
  public void testConstructor_withValidCompiler_createsNonNullInstance() throws Throwable {
    Compiler compiler = new Compiler();
    DeadAssignmentsElimination pass = new DeadAssignmentsElimination(compiler);
    assertNotNull(pass);
  }

  // process(): Preconditions.checkNotNull(externs) branch.
  @Test
  public void testProcess_nullExterns_throwsNullPointerException() throws Throwable {
    Compiler compiler = new Compiler();
    DeadAssignmentsElimination pass = new DeadAssignmentsElimination(compiler);
    try {
      pass.process(null, IR.block());
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // process(): Preconditions.checkNotNull(root) branch.
  @Test
  public void testProcess_nullRoot_throwsNullPointerException() throws Throwable {
    Compiler compiler = new Compiler();
    DeadAssignmentsElimination pass = new DeadAssignmentsElimination(compiler);
    try {
      pass.process(IR.block(), null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // enterScope(): global scope is skipped entirely, dead assignment preserved.
  @Test
  public void testProcess_globalScope_deadAssignmentNotRemoved() throws Throwable {
    String js = "var x=1;x=2;";
    String output = runPass(js);
    assertTrue(output.contains("x=1"));
  }

  // enterScope(): scope containing an inner function is skipped entirely.
  @Test
  public void testProcess_functionWithInnerFunction_deadAssignmentNotRemoved() throws Throwable {
    String js = "function f(){var x=1;x=2;function g(){return 1;}return x;}";
    String output = runPass(js);
    assertTrue(output.contains("x=1"));
  }

  // enterScope(): no removable assignments -> early return, code unchanged.
  @Test
  public void testProcess_noRemovableAssignments_codeUnchanged() throws Throwable {
    String js = "function f(){var x=1;return x;}";
    String output = runPass(js);
    assertTrue(output.contains("var x=1"));
    assertTrue(output.contains("return x"));
  }

  // tryRemoveAssignment(): dead assign x=1 removed, live assign x=2 preserved.
  @Test
  public void testProcess_simpleDeadAssignment_removed() throws Throwable {
    String js = "function f(){var x;x=1;x=2;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=1"));
    assertTrue(output.contains("x=2"));
  }

  // tryRemoveDeadAssignments(): Token.IF branch processes condition expression.
  @Test
  public void testProcess_deadAssignmentInIfCondition_removed() throws Throwable {
    String js = "function f(y){var x;if(x=y){}x=5;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=y"));
    assertTrue(output.contains("if(y)"));
  }

  // tryRemoveDeadAssignments(): Token.WHILE branch processes condition expression.
  @Test
  public void testProcess_deadAssignmentInWhileCondition_removed() throws Throwable {
    String js = "function f(y){var x;while(x=y){}x=5;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=y"));
    assertTrue(output.contains("while(y)"));
  }

  // tryRemoveDeadAssignments(): Token.DO branch processes condition expression.
  @Test
  public void testProcess_deadAssignmentInDoWhileCondition_removed() throws Throwable {
    String js = "function f(y){var x;do{}while(x=y);x=5;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=y"));
    assertTrue(output.contains("while(y)"));
  }

  // tryRemoveDeadAssignments(): Token.FOR branch processes condition expression (non for-in).
  @Test
  public void testProcess_deadAssignmentInForCondition_removed() throws Throwable {
    String js = "function f(y){var x;for(;x=y;){}x=5;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=y"));
    assertTrue(output.contains("for(;y;"));
  }

  // tryRemoveDeadAssignments(): Token.RETURN branch processes return expression.
  @Test
  public void testProcess_deadAssignmentInReturnExpression_removed() throws Throwable {
    String js = "function f(y){var x;return x=y;}";
    String output = runPass(js);
    assertFalse(output.contains("x=y"));
    assertTrue(output.contains("return y"));
  }

  // tryRemoveDeadAssignments(): Token.RETURN with no children does not crash, other dead assign still removed.
  @Test
  public void testProcess_emptyReturnStatement_noCrashOtherDeadAssignRemoved() throws Throwable {
    String js = "function f(c){var x;x=1;x=2;if(c){return;}return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=1"));
  }

  // tryRemoveAssignment(): identity assignment a=a is always removed regardless of liveness.
  @Test
  public void testProcess_identityAssignment_alwaysRemoved() throws Throwable {
    String js = "function f(){var x=1;x=x;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=x"));
    assertTrue(output.contains("return x"));
  }

  // tryRemoveAssignment(): dead compound assignment is converted into a plain binary expression.
  @Test
  public void testProcess_deadCompoundAssignment_convertedToBinaryOp() throws Throwable {
    String js = "function f(){var x=1;x+=2;x=3;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("+="));
  }

  // tryRemoveAssignment(): dead increment as expression statement replaced with void.
  @Test
  public void testProcess_deadIncrementExpressionStatement_replacedWithVoid() throws Throwable {
    String js = "function f(){var x=1;x++;x=3;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x++"));
  }

  // tryRemoveAssignment(): dead increment in for-loop update clause replaced with empty node.
  @Test
  public void testProcess_deadIncrementInForUpdateClause_replacedWithEmpty() throws Throwable {
    String js = "function f(y){var x=0;for(;y;x++){}return y;}";
    String output = runPass(js);
    assertFalse(output.contains("x++"));
  }

  // BUG: dead increment inside a comma expression must be removed (parent, not n, must be checked for COMMA).
  @Test
  public void testProcess_deadIncrementInCommaExpression_removed() throws Throwable {
    String js = "function f(y){var x=0;var z;z=(x++,y);return z;}";
    String output = runPass(js);
    assertFalse(output.contains("x++"));
  }

  // tryRemoveAssignment(): increment used as rhs of assignment cannot be safely transformed, left intact.
  @Test
  public void testProcess_incrementAsRhsOfAssignment_notTransformed() throws Throwable {
    String js = "function f(){var x=1;var y;y=x++;return y;}";
    String output = runPass(js);
    assertTrue(output.contains("x++"));
  }

  // tryRemoveAssignment(): state.getOut().isLive(var) true -> assignment is kept.
  @Test
  public void testProcess_liveAssignment_notRemoved() throws Throwable {
    String js = "function f(){var x;x=5;return x;}";
    String output = runPass(js);
    assertTrue(output.contains("x=5"));
  }

  // tryRemoveAssignment(): lhs is not a NAME node (property assignment) -> skipped entirely.
  @Test
  public void testProcess_propertyAssignmentLhsNotName_notTouched() throws Throwable {
    String js = "function f(){var obj={};var x=1;obj.prop=2;x=3;return x;}";
    String output = runPass(js);
    assertTrue(output.contains("obj.prop=2"));
    assertFalse(output.contains("x=1"));
  }

  // Correctness guard: a variable read again in a later loop iteration must stay live, not removed.
  @Test
  public void testProcess_variableLiveAcrossLoopIteration_notRemoved() throws Throwable {
    String js = "function f(cond){var x=0;while(cond){x=x+1;}return x;}";
    String output = runPass(js);
    assertTrue(output.contains("x=x+1"));
  }

  // Combined if/else merge point: dead branch assignments removed, used ones kept.
  @Test
  public void testProcess_ifElseMergeDeadAssignments_removedSelectively() throws Throwable {
    String js = "function f(cond){var a=1;var b;if(cond){b=a;a=2;}else{a=3;}a=4;return a+b;}";
    String output = runPass(js);
    assertFalse(output.contains("a=2"));
    assertFalse(output.contains("a=3"));
    assertTrue(output.contains("a=4"));
  }

  // tryRemoveAssignment(): recursion through nested blocks still finds dead assignment.
  @Test
  public void testProcess_nestedBlockDeadAssignment_removed() throws Throwable {
    String js = "function f(){var x=1;{{x=2;}}x=3;return x;}";
    String output = runPass(js);
    assertFalse(output.contains("x=2"));
    assertTrue(output.contains("x=3"));
  }
}
