package com.google.javascript.jscomp;

import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class CheckSideEffectsClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
  }

  private Node[] parseJs(String js) {
    List<SourceFile> externs = Lists.newArrayList();
    List<SourceFile> inputs = Lists.newArrayList();
    inputs.add(SourceFile.fromCode("test.js", js));
    compiler.compile(externs, inputs, options);
    Node root = compiler.getRoot();
    return new Node[] { root.getFirstChild(), root.getLastChild() };
  }

  // n.isString() branch: bare string literal statement should warn with the "+" hint message.
  @Test
  public void testProcess_uselessStringLiteralStatement_reportsWarningWithPlusHint() throws Throwable {
    Node[] roots = parseJs("'hello';");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    JSError[] warnings = compiler.getWarnings();
    assertEquals(1, warnings.length);
    assertTrue(warnings[0].description.contains("+"));
  }

  // isSimpleOp branch: top level comparison operator result unused should warn.
  @Test
  public void testProcess_uselessComparisonOperatorStatement_reportsWarning() throws Throwable {
    Node[] roots = parseJs("x == 5;");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    JSError[] warnings = compiler.getWarnings();
    assertEquals(1, warnings.length);
  }

  // n.isEmpty() branch (stray semicolon) and mayHaveSideEffects true for CALL: no warnings.
  @Test
  public void testProcess_strayEmptySemicolonAfterCall_noWarnings() throws Throwable {
    Node[] roots = parseJs("foo();;");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // Assignment always has a side effect: no warning.
  @Test
  public void testProcess_assignmentStatement_noWarning() throws Throwable {
    Node[] roots = parseJs("x = 5;");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // Increment operator has a side effect: no warning.
  @Test
  public void testProcess_incrementStatement_noWarning() throws Throwable {
    Node[] roots = parseJs("x++;");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // Function call is conservatively assumed to have side effects: no warning.
  @Test
  public void testProcess_functionCallStatement_noWarning() throws Throwable {
    Node[] roots = parseJs("foo();");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // BLOCK loop with two useless statements: one warning per statement.
  @Test
  public void testProcess_multipleUselessStatements_reportsWarningEach() throws Throwable {
    Node[] roots = parseJs("'a'; 'b';");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(2, compiler.getWarnings().length);
  }

  // BLOCK loop with zero statements: zero warnings.
  @Test
  public void testProcess_emptyScript_noWarnings() throws Throwable {
    Node[] roots = parseJs("");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // COMMA branch: both operands discarded and side-effect free -> both flagged.
  @Test
  public void testProcess_commaExpressionBothOperandsUseless_reportsTwoWarnings() throws Throwable {
    Node[] roots = parseJs("1, 2;");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(2, compiler.getWarnings().length);
  }

  // COMMA branch: last operand has a side effect (call), only the first is flagged.
  @Test
  public void testProcess_commaExpressionLastOperandHasSideEffect_reportsOneWarning() throws Throwable {
    Node[] roots = parseJs("1, foo();");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(1, compiler.getWarnings().length);
  }

  // COMMA branch: isResultUsed true for last operand when assigned, only discarded operand flagged.
  @Test
  public void testProcess_commaExpressionResultUsedInAssignment_reportsOnlyDiscardedOperand() throws Throwable {
    Node[] roots = parseJs("x = (1, 2);");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(1, compiler.getWarnings().length);
  }

  // COMMA branch: last operand is a string literal -> "+" hint message used.
  @Test
  public void testProcess_commaLastOperandString_reportsPlusHintMessage() throws Throwable {
    Node[] roots = parseJs("foo(), 'bar';");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    JSError[] warnings = compiler.getWarnings();
    assertEquals(1, warnings.length);
    assertTrue(warnings[0].description.contains("+"));
  }

  // Nested COMMA chain: ancestor-walk loop must skip through inner COMMA nodes correctly.
  @Test
  public void testProcess_nestedCommaChainThreeOperands_reportsThreeWarnings() throws Throwable {
    Node[] roots = parseJs("1, 2, 3;");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(3, compiler.getWarnings().length);
  }

  // FOR branch: init slot (child 0) with no side effects should be flagged, condition should not.
  @Test
  public void testProcess_forLoopUselessInit_reportsWarningForInitOnly() throws Throwable {
    Node[] roots = parseJs("for (1; x<10; x++) {}");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(1, compiler.getWarnings().length);
  }

  // FOR branch: increment slot (child 2) with no side effects should be flagged, condition should not.
  @Test
  public void testProcess_forLoopUselessIncrement_reportsWarningForIncrementOnly() throws Throwable {
    Node[] roots = parseJs("for (x=0; x<10; y) {}");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(1, compiler.getWarnings().length);
  }

  // FOR branch: only a condition present (EMPTY init/increment), no warnings at all.
  @Test
  public void testProcess_forLoopOnlyConditionPresent_noWarnings() throws Throwable {
    Node[] roots = parseJs("for (;x<10;) {}");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // n.isQualifiedName() && JSDocInfo != null branch: JSDoc-only declarations are not flagged.
  @Test
  public void testProcess_qualifiedNameWithJSDoc_noWarning() throws Throwable {
    Node[] roots = parseJs("/** @type {number} */\nx.y;");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // Generic else-branch: comparison used as an if-condition has its value used, no warning.
  @Test
  public void testProcess_ifConditionComparisonValueUsed_noWarning() throws Throwable {
    Node[] roots = parseJs("if (x == 5) {}");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // Generic else-branch: comparison used as assignment RHS has its value used, no warning.
  @Test
  public void testProcess_assignmentRhsComparisonValueUsed_noWarning() throws Throwable {
    Node[] roots = parseJs("x = (y == 5);");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getWarnings().length);
  }

  // hotSwapScript override should behave like process() for a single script root.
  @Test
  public void testHotSwapScript_uselessStringLiteral_reportsWarning() throws Throwable {
    Node[] roots = parseJs("'hello';");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).hotSwapScript(roots[1], roots[1]);
    assertEquals(1, compiler.getWarnings().length);
  }

  // protectSideEffectFreeCode = true: problem (non-statement) node is wrapped in a protector CALL.
  @Test
  public void testProcess_protectSideEffectFreeCodeTrue_wrapsProblemNodeInProtectorCall() throws Throwable {
    Node[] roots = parseJs("'hello';");
    new CheckSideEffects(compiler, CheckLevel.WARNING, true).process(roots[0], roots[1]);
    Node exprResult = roots[1].getFirstChild().getFirstChild();
    Node call = exprResult.getFirstChild();
    assertTrue(call.isCall());
    assertEquals(CheckSideEffects.PROTECTOR_FN, call.getFirstChild().getString());
  }

  // protectSideEffectFreeCode = false: tree is left untouched even though a warning is reported.
  @Test
  public void testProcess_protectSideEffectFreeCodeFalse_doesNotWrapProblemNode() throws Throwable {
    Node[] roots = parseJs("'hello';");
    new CheckSideEffects(compiler, CheckLevel.WARNING, false).process(roots[0], roots[1]);
    Node exprResult = roots[1].getFirstChild().getFirstChild();
    assertTrue(exprResult.getFirstChild().isString());
  }

  // StripProtection: protector CALL wrapper is removed, original expression restored.
  @Test
  public void testStripProtection_removesProtectorCall_restoresOriginalExpression() throws Throwable {
    Node[] roots = parseJs("'hello';");
    new CheckSideEffects(compiler, CheckLevel.WARNING, true).process(roots[0], roots[1]);
    new CheckSideEffects.StripProtection(compiler).process(roots[0], roots[1]);
    Node exprResult = roots[1].getFirstChild().getFirstChild();
    assertTrue(exprResult.getFirstChild().isString());
    assertEquals("hello", exprResult.getFirstChild().getString());
  }

  // StripProtection: unrelated calls (not to PROTECTOR_FN) are left untouched.
  @Test
  public void testStripProtection_leavesUnrelatedCallUnchanged() throws Throwable {
    Node[] roots = parseJs("foo();");
    new CheckSideEffects.StripProtection(compiler).process(roots[0], roots[1]);
    Node call = roots[1].getFirstChild().getFirstChild().getFirstChild();
    assertTrue(call.isCall());
    assertEquals("foo", call.getFirstChild().getString());
  }

  // level = ERROR: warning is reported as an error, not a warning.
  @Test
  public void testProcess_levelError_reportsAsError() throws Throwable {
    Node[] roots = parseJs("'hello';");
    new CheckSideEffects(compiler, CheckLevel.ERROR, false).process(roots[0], roots[1]);
    assertEquals(1, compiler.getErrors().length);
    assertEquals(0, compiler.getWarnings().length);
  }

  // level = OFF: nothing is reported at all.
  @Test
  public void testProcess_levelOff_reportsNothing() throws Throwable {
    Node[] roots = parseJs("'hello';");
    new CheckSideEffects(compiler, CheckLevel.OFF, false).process(roots[0], roots[1]);
    assertEquals(0, compiler.getErrors().length);
    assertEquals(0, compiler.getWarnings().length);
  }
}
