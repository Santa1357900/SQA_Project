package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;

public class CodeConsumerClaudeTest {

  /** Minimal concrete subclass implementing only the two abstract methods. */
  private static class TestCodeConsumer extends CodeConsumer {
    StringBuilder sb = new StringBuilder();

    @Override
    char getLastChar() {
      if (sb.length() == 0) {
        return '\0';
      }
      return sb.charAt(sb.length() - 1);
    }

    @Override
    void append(String str) {
      sb.append(str);
    }
  }

  // Covers startSourceMapping/endSourceMapping default no-op behavior
  @Test
  public void testStartAndEndSourceMapping_noOutputChange() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    Node n = IR.name("test");
    cc.startSourceMapping(n);
    cc.endSourceMapping(n);
    assertEquals("", cc.sb.toString());
  }

  // Covers continueProcessing default return value
  @Test
  public void testContinueProcessing_defaultReturnsTrue() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    assertTrue(cc.continueProcessing());
  }

  // Covers addIdentifier delegating to add()
  @Test
  public void testAddIdentifier_delegatesToAdd() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.addIdentifier("foo");
    assertEquals("foo", cc.sb.toString());
  }

  // Covers appendBlockStart appending "{"
  @Test
  public void testAppendBlockStart_appendsOpenBrace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.appendBlockStart();
    assertEquals("{", cc.sb.toString());
  }

  // Covers appendBlockEnd appending "}"
  @Test
  public void testAppendBlockEnd_appendsCloseBrace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.appendBlockEnd();
    assertEquals("}", cc.sb.toString());
  }

  // Covers group of no-op formatting methods: startNewLine, maybeCutLine, endLine,
  // notePreferredLineBreak, endCaseBody, endFile
  @Test
  public void testNoOpFormattingMethods_doNotAppendOrThrow() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.startNewLine();
    cc.maybeCutLine();
    cc.endLine();
    cc.notePreferredLineBreak();
    cc.endCaseBody();
    cc.endFile();
    assertEquals("", cc.sb.toString());
  }

  // Covers maybeLineBreak delegating to the no-op maybeCutLine
  @Test
  public void testMaybeLineBreak_delegatesToMaybeCutLine_noOutputChange() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.maybeLineBreak();
    assertEquals("", cc.sb.toString());
  }

  // Covers beginBlock() when statementNeedsEnded is false (no leading ';')
  @Test
  public void testBeginBlock_statementNotNeeded_appendsBraceOnly() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.beginBlock();
    assertEquals("{", cc.sb.toString());
    assertFalse(cc.statementNeedsEnded);
  }

  // Covers beginBlock() when statementNeedsEnded is true (adds ';' before '{')
  @Test
  public void testBeginBlock_statementNeeded_appendsSemicolonThenBrace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.statementNeedsEnded = true;
    cc.beginBlock();
    assertEquals(";{", cc.sb.toString());
    assertFalse(cc.statementNeedsEnded);
  }

  // Covers endBlock() default (shouldEndLine=false) and flag reset
  @Test
  public void testEndBlock_default_appendsCloseBraceAndResetsFlag() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.statementNeedsEnded = true;
    cc.endBlock();
    assertEquals("}", cc.sb.toString());
    assertFalse(cc.statementNeedsEnded);
  }

  // Covers endBlock(true) branch (shouldEndLine=true)
  @Test
  public void testEndBlock_withShouldEndLineTrue_appendsCloseBrace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.statementNeedsEnded = true;
    cc.endBlock(true);
    assertEquals("}", cc.sb.toString());
    assertFalse(cc.statementNeedsEnded);
  }

  // Covers listSeparator appending ',' via add()
  @Test
  public void testListSeparator_appendsCommaAfterContent() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("a");
    cc.listSeparator();
    assertEquals("a,", cc.sb.toString());
  }

  // Covers endStatement() default when statementStarted is false: no flag set, no output
  @Test
  public void testEndStatement_default_noStatementStarted_noChange() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.endStatement();
    assertEquals("", cc.sb.toString());
    assertFalse(cc.statementNeedsEnded);
  }

  // Covers endStatement(true) branch: appends ';' immediately
  @Test
  public void testEndStatement_needSemiColonTrue_appendsSemicolon() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.endStatement(true);
    assertEquals(";", cc.sb.toString());
    assertFalse(cc.statementNeedsEnded);
  }

  // Covers endStatement(false) branch when statementStarted is true: defers via flag
  @Test
  public void testEndStatement_needSemiColonFalse_statementStarted_setsFlag() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.statementStarted = true;
    cc.endStatement(false);
    assertTrue(cc.statementNeedsEnded);
    assertEquals("", cc.sb.toString());
  }

  // Covers maybeEndStatement() when statementNeedsEnded is true
  @Test
  public void testMaybeEndStatement_flagTrue_appendsSemicolonAndSetsStarted() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.statementNeedsEnded = true;
    cc.maybeEndStatement();
    assertEquals(";", cc.sb.toString());
    assertFalse(cc.statementNeedsEnded);
    assertTrue(cc.statementStarted);
  }

  // Covers maybeEndStatement() when statementNeedsEnded is false
  @Test
  public void testMaybeEndStatement_flagFalse_onlySetsStarted() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.maybeEndStatement();
    assertEquals("", cc.sb.toString());
    assertTrue(cc.statementStarted);
  }

  // Covers endFunction() default sets sawFunction
  @Test
  public void testEndFunction_default_setsSawFunction() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.endFunction();
    assertTrue(cc.sawFunction);
  }

  // Covers endFunction(true) branch (statementContext=true)
  @Test
  public void testEndFunction_statementContextTrue_setsSawFunctionNoThrow() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.endFunction(true);
    assertTrue(cc.sawFunction);
    assertEquals("", cc.sb.toString());
  }

  // Covers beginCaseBody appending ':'
  @Test
  public void testBeginCaseBody_appendsColon() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.beginCaseBody();
    assertEquals(":", cc.sb.toString());
  }

  // Covers add("") early-return branch while still marking statementStarted
  @Test
  public void testAdd_emptyString_noAppendButMarksStarted() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.add("");
    assertEquals("", cc.sb.toString());
    assertTrue(cc.statementStarted);
  }

  // Covers add() inserting a space when both last char and new char are word chars
  @Test
  public void testAdd_wordCharAfterWordChar_insertsSpace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("foo");
    cc.add("bar");
    assertEquals("foo bar", cc.sb.toString());
  }

  // Covers add() NOT inserting a space when last char is not a word char
  @Test
  public void testAdd_wordCharAfterNonWordChar_noSpace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("foo(");
    cc.add("bar");
    assertEquals("foo(bar", cc.sb.toString());
  }

  // Covers add() inserting a space when new code starts with backslash
  @Test
  public void testAdd_backslashAfterWordChar_insertsSpace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("foo");
    cc.add("\\bar");
    assertEquals("foo \\bar", cc.sb.toString());
  }

  // Covers appendOp appending the operator string as-is
  @Test
  public void testAppendOp_appendsOperatorDirectly() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.appendOp("&&", true);
    assertEquals("&&", cc.sb.toString());
  }

  // Covers addOp branch: same +/- sign as previous char needs a separating space
  @Test
  public void testAddOp_samePlusSign_insertsSpace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("+");
    cc.addOp("+", true);
    assertEquals("+ +", cc.sb.toString());
  }

  // Covers addOp branch: letter-starting op (e.g. instanceof) after a word char needs a space
  @Test
  public void testAddOp_letterOpAfterWordChar_insertsSpace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("x");
    cc.addOp("instanceof", true);
    assertEquals("x instanceof", cc.sb.toString());
  }

  // Covers addOp branch: '-' followed by '>' needs a space to avoid forming "-->"
  @Test
  public void testAddOp_dashThenGreaterThan_insertsSpace() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("-");
    cc.addOp(">", true);
    assertEquals("- >", cc.sb.toString());
  }

  // Covers addOp default branch: no space needed, op appended directly
  @Test
  public void testAddOp_noSpaceNeeded_appendsDirectly() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("x");
    cc.addOp("+", true);
    assertEquals("x+", cc.sb.toString());
  }

  // Covers addNumber(0): abs<100 branch skipped, plain "0" printed
  @Test
  public void testAddNumber_zero_printsZero() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.addNumber(0);
    assertEquals("0", cc.sb.toString());
  }

  // Covers addNumber with a small positive integer below the 100 threshold
  @Test
  public void testAddNumber_smallPositive_printsPlainInteger() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.addNumber(42);
    assertEquals("42", cc.sb.toString());
  }

  // Covers addNumber's space-insertion rule preventing "x--4" misparse of "x - -4"
  @Test
  public void testAddNumber_negativeAfterDash_preventsDoubleMinusMisparse() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.append("-");
    cc.addNumber(-4);
    assertEquals("- -4", cc.sb.toString());
  }

  // Covers addNumber exponent branch: 1000 has 3 trailing zeros stripped -> "1E3"
  @Test
  public void testAddNumber_thousand_usesScientificNotation() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.addNumber(1000);
    assertEquals("1E3", cc.sb.toString());
  }

  // Covers addNumber non-exponent branch: only 2 trailing zeros stripped -> plain value
  @Test
  public void testAddNumber_twelveThousandThreeHundred_noScientificNotation() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.addNumber(12300);
    assertEquals("12300", cc.sb.toString());
  }

  // Covers addNumber else branch for non-integer doubles
  @Test
  public void testAddNumber_fractional_usesDoubleToString() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.addNumber(3.14);
    assertEquals("3.14", cc.sb.toString());
  }

  // Bug-hunting: -0.0 must remain distinguishable from 0 (IEEE-754/JS semantics,
  // e.g. 1/-0 !== 1/0), so the emitted literal must retain the negative sign.
  @Test
  public void testAddNumber_negativeZero_preservesSign() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    cc.addNumber(-0.0);
    assertTrue(cc.sb.toString().indexOf('-') >= 0);
  }

  // Covers isWordChar() for underscore, dollar, letter, digit, and non-word chars
  @Test
  public void testIsWordChar_variousChars() throws Throwable {
    assertTrue(CodeConsumer.isWordChar('_'));
    assertTrue(CodeConsumer.isWordChar('$'));
    assertTrue(CodeConsumer.isWordChar('a'));
    assertTrue(CodeConsumer.isWordChar('5'));
    assertFalse(CodeConsumer.isWordChar(' '));
    assertFalse(CodeConsumer.isWordChar('+'));
  }

  // Covers shouldPreserveExtraBlocks default return value
  @Test
  public void testShouldPreserveExtraBlocks_returnsFalse() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    assertFalse(cc.shouldPreserveExtraBlocks());
  }

  // Covers breakAfterBlockFor returning the statementContext value for both booleans
  @Test
  public void testBreakAfterBlockFor_returnsStatementContextValue() throws Throwable {
    TestCodeConsumer cc = new TestCodeConsumer();
    Node n = IR.block();
    assertTrue(cc.breakAfterBlockFor(n, true));
    assertFalse(cc.breakAfterBlockFor(n, false));
  }
}
