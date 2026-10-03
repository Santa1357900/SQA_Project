package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class CodeGeneratorClaudeTest {

  // isSimpleNumber: len == 0 -> false
  @Test
  public void testIsSimpleNumber_emptyString_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber(""));
  }

  // isSimpleNumber: len == 1 short-circuits leading-zero check -> true
  @Test
  public void testIsSimpleNumber_singleZero_returnsTrue() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("0"));
  }

  // isSimpleNumber: single non-zero digit -> true
  @Test
  public void testIsSimpleNumber_singleNonZeroDigit_returnsTrue() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("5"));
  }

  // isSimpleNumber: len > 1 with leading zero -> false
  @Test
  public void testIsSimpleNumber_multiDigitLeadingZero_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("012"));
  }

  // isSimpleNumber: len > 1, no leading zero -> true
  @Test
  public void testIsSimpleNumber_multiDigitNoLeadingZero_returnsTrue() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("123"));
  }

  // isSimpleNumber: non-digit char in loop -> false
  @Test
  public void testIsSimpleNumber_containsLetter_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("12a"));
  }

  // isSimpleNumber: '-' is below '0' in loop check -> false
  @Test
  public void testIsSimpleNumber_negativeSign_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("-5"));
  }

  // isSimpleNumber: '.' fails digit check -> false
  @Test
  public void testIsSimpleNumber_decimalPoint_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("1.5"));
  }

  // isSimpleNumber: leading whitespace fails digit check -> false
  @Test
  public void testIsSimpleNumber_leadingWhitespace_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber(" 1"));
  }

  // isSimpleNumber: multi-digit all zeros -> false (first char == '0')
  @Test
  public void testIsSimpleNumber_allZerosMultiDigit_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("00"));
  }

  // getSimpleNumber: isSimpleNumber false branch (non-digit) -> NaN
  @Test
  public void testGetSimpleNumber_nonDigitString_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("abc")));
  }

  // getSimpleNumber: isSimpleNumber false branch (empty) -> NaN
  @Test
  public void testGetSimpleNumber_emptyString_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("")));
  }

  // getSimpleNumber: isSimpleNumber false branch (leading zero) -> NaN
  @Test
  public void testGetSimpleNumber_leadingZero_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("01")));
  }

  // getSimpleNumber: simple case "0" -> 0.0
  @Test
  public void testGetSimpleNumber_zero_returnsZero() throws Throwable {
    assertEquals(0.0, CodeGenerator.getSimpleNumber("0"), 1e-9);
  }

  // getSimpleNumber: ordinary integer parses and returns its value
  @Test
  public void testGetSimpleNumber_simpleInteger_returnsValue() throws Throwable {
    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 1e-9);
  }

  // getSimpleNumber: value strictly below MAX_POSITIVE_INTEGER_NUMBER returns that value
  @Test
  public void testGetSimpleNumber_belowMaxBoundary_returnsValue() throws Throwable {
    long maxVal = (long) NodeUtil.MAX_POSITIVE_INTEGER_NUMBER;
    long below = maxVal - 1;
    double result = CodeGenerator.getSimpleNumber(String.valueOf(below));
    assertEquals((double) below, result, 1e-9);
  }

  // getSimpleNumber: value exactly equal to MAX_POSITIVE_INTEGER_NUMBER is still exactly
  // representable as a double and per the method contract must be returned, not NaN.
  // This is the boundary (< vs <=) condition that exposes the bug.
  @Test
  public void testGetSimpleNumber_atMaxBoundary_returnsValue() throws Throwable {
    long maxVal = (long) NodeUtil.MAX_POSITIVE_INTEGER_NUMBER;
    double result = CodeGenerator.getSimpleNumber(String.valueOf(maxVal));
    assertFalse("Value equal to MAX_POSITIVE_INTEGER_NUMBER must not be NaN",
        Double.isNaN(result));
    assertEquals((double) maxVal, result, 1e-9);
  }

  // getSimpleNumber: digit string too long for long -> NumberFormatException caught -> NaN
  @Test
  public void testGetSimpleNumber_overflowsLong_returnsNaN() throws Throwable {
    String huge = "99999999999999999999";
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber(huge)));
  }

  // identifierEscape: plain ASCII identifier (Latin) is returned unchanged
  @Test
  public void testIdentifierEscape_asciiIdentifier_returnsUnchanged() throws Throwable {
    assertEquals("validName123", CodeGenerator.identifierEscape("validName123"));
  }

  // identifierEscape: empty string returns empty string
  @Test
  public void testIdentifierEscape_emptyString_returnsEmpty() throws Throwable {
    assertEquals("", CodeGenerator.identifierEscape(""));
  }

  // identifierEscape: non-Latin char is hex-escaped as \\uXXXX
  @Test
  public void testIdentifierEscape_nonLatinChar_escapesToUnicode() throws Throwable {
    assertEquals("\\u4e2d", CodeGenerator.identifierEscape("\u4e2d"));
  }

  // identifierEscape: ASCII chars kept literal, non-Latin char escaped in same string
  @Test
  public void testIdentifierEscape_mixedAsciiAndNonLatin_escapesOnlyNonLatin() throws Throwable {
    assertEquals("a\\u4e2db", CodeGenerator.identifierEscape("a\u4e2db"));
  }

  // identifierEscape: char at 0x1F (not > 0x1F) falls into escape branch
  @Test
  public void testIdentifierEscape_controlCharBelowBoundary_escaped() throws Throwable {
    assertEquals("\\u4e2d\\u001f", CodeGenerator.identifierEscape("\u4e2d\u001F"));
  }

  // identifierEscape: char at 0x20 (> 0x1F and < 0x7F) is appended unescaped
  @Test
  public void testIdentifierEscape_spaceCharAtLowerBoundary_appendedUnescaped() throws Throwable {
    assertEquals("\\u4e2d ", CodeGenerator.identifierEscape("\u4e2d\u0020"));
  }

  // identifierEscape: char at 0x7E (< 0x7F) is appended unescaped
  @Test
  public void testIdentifierEscape_tildeCharAtUpperBoundary_appendedUnescaped() throws Throwable {
    assertEquals("\\u4e2d~", CodeGenerator.identifierEscape("\u4e2d~"));
  }

  // identifierEscape: char at 0x7F (not < 0x7F) falls into escape branch
  @Test
  public void testIdentifierEscape_delCharAboveBoundary_escaped() throws Throwable {
    assertEquals("\\u4e2d\\u007f", CodeGenerator.identifierEscape("\u4e2d\u007F"));
  }
}
