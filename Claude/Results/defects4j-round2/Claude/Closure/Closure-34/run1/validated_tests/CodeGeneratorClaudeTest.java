package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class CodeGeneratorClaudeTest {

  // isSimpleNumber: empty string -> len == 0 branch -> false
  @Test
  public void testIsSimpleNumber_emptyString_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber(""));
  }



  // isSimpleNumber: leading zero with more than one digit -> false
  @Test
  public void testIsSimpleNumber_leadingZeroMultiDigit_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("00"));
  }

  // isSimpleNumber: all-digit string not starting with zero -> true
  @Test
  public void testIsSimpleNumber_allDigitsNoLeadingZero_returnsTrue() throws Throwable {
    assertTrue(CodeGenerator.isSimpleNumber("10"));
  }

  // isSimpleNumber: non-digit character present -> loop returns false early
  @Test
  public void testIsSimpleNumber_nonDigitChar_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("12a"));
  }

  // isSimpleNumber: negative sign is not a digit -> false
  @Test
  public void testIsSimpleNumber_negativeSign_returnsFalse() throws Throwable {
    assertFalse(CodeGenerator.isSimpleNumber("-5"));
  }



  // getSimpleNumber: normal multi-digit number parses correctly
  @Test
  public void testGetSimpleNumber_validNumber_returnsValue() throws Throwable {
    assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 1e-9);
  }

  // getSimpleNumber: non-numeric string -> NaN
  @Test
  public void testGetSimpleNumber_nonNumericString_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("abc")));
  }

  // getSimpleNumber: empty string -> NaN
  @Test
  public void testGetSimpleNumber_emptyString_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("")));
  }

  // getSimpleNumber: leading zero multi-digit -> not simple -> NaN
  @Test
  public void testGetSimpleNumber_leadingZeroMultiDigit_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("007")));
  }

  // getSimpleNumber: number too large for long -> NumberFormatException caught -> NaN
  @Test
  public void testGetSimpleNumber_overflow_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("9999999999999999999999999")));
  }

  // getSimpleNumber: negative sign not simple -> NaN
  @Test
  public void testGetSimpleNumber_negativeSign_returnsNaN() throws Throwable {
    assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("-5")));
  }

  // identifierEscape: plain ASCII string returned unchanged regardless of isLatin path
  @Test
  public void testIdentifierEscape_asciiString_returnsUnchanged() throws Throwable {
    assertEquals("foo", CodeGenerator.identifierEscape("foo"));
  }

  // identifierEscape: empty string returned unchanged
  @Test
  public void testIdentifierEscape_emptyString_returnsEmpty() throws Throwable {
    assertEquals("", CodeGenerator.identifierEscape(""));
  }

  // regexpEscape: simple ascii string wrapped in slashes, no escaping needed
  @Test
  public void testRegexpEscape_simpleString_wrapsInSlashes() throws Throwable {
    assertEquals("/abc/", CodeGenerator.regexpEscape("abc"));
  }

  // regexpEscape: newline char escaped to \n
  @Test
  public void testRegexpEscape_newline_escaped() throws Throwable {
    assertEquals("/a\\nb/", CodeGenerator.regexpEscape("a\nb"));
  }

  // regexpEscape: tab char escaped to \t
  @Test
  public void testRegexpEscape_tab_escaped() throws Throwable {
    assertEquals("/\\t/", CodeGenerator.regexpEscape("\t"));
  }

  // regexpEscape: carriage return escaped to \r
  @Test
  public void testRegexpEscape_carriageReturn_escaped() throws Throwable {
    assertEquals("/\\r/", CodeGenerator.regexpEscape("\r"));
  }

  // regexpEscape: null char escaped to \x00
  @Test
  public void testRegexpEscape_nullChar_escaped() throws Throwable {
    assertEquals("/\\x00/", CodeGenerator.regexpEscape("\0"));
  }

  // regexpEscape: vertical tab (useSlashV=false) escaped to hardcoded \x0B
  @Test
  public void testRegexpEscape_verticalTab_escapedAsHex() throws Throwable {
    assertEquals("/\\x0B/", CodeGenerator.regexpEscape("\u000B"));
  }

  // regexpEscape: backslash left unchanged (regex escape char is itself)
  @Test
  public void testRegexpEscape_backslash_unchanged() throws Throwable {
    assertEquals("/a\\b/", CodeGenerator.regexpEscape("a\\b"));
  }

  // regexpEscape: double quote left unchanged (regex quote escape is literal)
  @Test
  public void testRegexpEscape_doubleQuote_unchanged() throws Throwable {
    assertEquals("/a\"b/", CodeGenerator.regexpEscape("a\"b"));
  }

  // regexpEscape: "-->" triggers comment-breaking escape of '>'
  @Test
  public void testRegexpEscape_commentDashDashGreaterThan_escaped() throws Throwable {
    assertEquals("/--\\>/", CodeGenerator.regexpEscape("-->"));
  }

  // regexpEscape: "]]>" triggers comment-breaking escape of '>'
  @Test
  public void testRegexpEscape_bracketBracketGreaterThan_escaped() throws Throwable {
    assertEquals("/]]\\>/", CodeGenerator.regexpEscape("]]>"));
  }

  // regexpEscape: "</SCRIPT>" triggers case-insensitive /script break
  @Test
  public void testRegexpEscape_scriptTagCaseInsensitive_escaped() throws Throwable {
    assertEquals("/<\\/SCRIPT>/", CodeGenerator.regexpEscape("</SCRIPT>"));
  }

  // regexpEscape: "<!--" triggers case-sensitive html comment start break
  @Test
  public void testRegexpEscape_htmlCommentStart_escaped() throws Throwable {
    assertEquals("/<\\!--/", CodeGenerator.regexpEscape("<!--"));
  }

  // regexpEscape: '<' not matching any special pattern is left unchanged
  @Test
  public void testRegexpEscape_lessThanNoMatch_unchanged() throws Throwable {
    assertEquals("/<div>/", CodeGenerator.regexpEscape("<div>"));
  }

  // regexpEscape: high unicode char outside ascii range hex-escaped, lowercase hex digits
  @Test
  public void testRegexpEscape_highUnicodeChar_escapedAsHex() throws Throwable {
    assertEquals("/\\u00e9/", CodeGenerator.regexpEscape("\u00e9"));
  }

  // escapeToDoubleQuotedJsString: simple ascii string wrapped in double quotes
  @Test
  public void testEscapeToDoubleQuotedJsString_simpleString() throws Throwable {
    assertEquals("\"abc\"", CodeGenerator.escapeToDoubleQuotedJsString("abc"));
  }

  // escapeToDoubleQuotedJsString: double quote char escaped with backslash
  @Test
  public void testEscapeToDoubleQuotedJsString_doubleQuote_escaped() throws Throwable {
    assertEquals("\"a\\\"b\"", CodeGenerator.escapeToDoubleQuotedJsString("a\"b"));
  }

  // escapeToDoubleQuotedJsString: single quote left unchanged in double-quoted string
  @Test
  public void testEscapeToDoubleQuotedJsString_singleQuote_unchanged() throws Throwable {
    assertEquals("\"a'b\"", CodeGenerator.escapeToDoubleQuotedJsString("a'b"));
  }

  // escapeToDoubleQuotedJsString: backslash escaped by doubling
  @Test
  public void testEscapeToDoubleQuotedJsString_backslash_escaped() throws Throwable {
    assertEquals("\"a\\\\b\"", CodeGenerator.escapeToDoubleQuotedJsString("a\\b"));
  }

  // escapeToDoubleQuotedJsString: null char escaped to \x00
  @Test
  public void testEscapeToDoubleQuotedJsString_nullChar() throws Throwable {
    assertEquals("\"\\x00\"", CodeGenerator.escapeToDoubleQuotedJsString("\0"));
  }

  // escapeToDoubleQuotedJsString: vertical tab escaped to hardcoded \x0B (useSlashV always false here)
  @Test
  public void testEscapeToDoubleQuotedJsString_verticalTab() throws Throwable {
    assertEquals("\"\\x0B\"", CodeGenerator.escapeToDoubleQuotedJsString("\u000B"));
  }

  // escapeToDoubleQuotedJsString: high unicode char hex-escaped lowercase
  @Test
  public void testEscapeToDoubleQuotedJsString_highUnicodeChar() throws Throwable {
    assertEquals("\"\\u00e9\"", CodeGenerator.escapeToDoubleQuotedJsString("\u00e9"));
  }

  // escapeToDoubleQuotedJsString: boundary control char (0x1F) just below printable range escaped
  @Test
  public void testEscapeToDoubleQuotedJsString_controlCharBoundary() throws Throwable {
    assertEquals("\"\\u001f\"", CodeGenerator.escapeToDoubleQuotedJsString("\u001F"));
  }

  // escapeToDoubleQuotedJsString: space char (0x20) at lower boundary passes through unescaped
  @Test
  public void testEscapeToDoubleQuotedJsString_spaceCharBoundary() throws Throwable {
    assertEquals("\" \"", CodeGenerator.escapeToDoubleQuotedJsString(" "));
  }

  // escapeToDoubleQuotedJsString: DEL char (0x7F) at upper boundary is escaped
  @Test
  public void testEscapeToDoubleQuotedJsString_delCharBoundary() throws Throwable {
    assertEquals("\"\\u007f\"", CodeGenerator.escapeToDoubleQuotedJsString("\u007F"));
  }

  // escapeToDoubleQuotedJsString: "-->" comment-break escape of '>' applies regardless of quote char
  @Test
  public void testEscapeToDoubleQuotedJsString_commentBreak() throws Throwable {
    assertEquals("\"--\\>\"", CodeGenerator.escapeToDoubleQuotedJsString("-->"));
  }

  // escapeToDoubleQuotedJsString: "</SCRIPT>" script-tag-break escape applies regardless of quote char
  @Test
  public void testEscapeToDoubleQuotedJsString_scriptTagBreak() throws Throwable {
    assertEquals("\"<\\/SCRIPT>\"", CodeGenerator.escapeToDoubleQuotedJsString("</SCRIPT>"));
  }
}
