package com.google.javascript.jscomp;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.junit.Test;
import static org.junit.Assert.*;

public class CodeGeneratorClaudeTest {

  // jsString: singleq(0) == doubleq(0) tie -> defaults to double-quote wrap (else branch)
  @Test
  public void testJsString_emptyString_defaultDoubleQuote() throws Throwable {
    String result = CodeGenerator.jsString("", null);
    assertEquals("\"\"", result);
  }

  // jsString: plain text without quote chars -> double-quote wrapped unchanged
  @Test
  public void testJsString_plainText_defaultDoubleQuote() throws Throwable {
    String result = CodeGenerator.jsString("hello", null);
    assertEquals("\"hello\"", result);
  }

  // jsString: singleq(1) < doubleq(0) is false -> double-quote wrap, apostrophe literal
  @Test
  public void testJsString_singleQuoteOnly_wrapsDoubleQuote_literalApostrophe() throws Throwable {
    String result = CodeGenerator.jsString("it's", null);
    assertEquals("\"it's\"", result);
  }

  // jsString: singleq(0) < doubleq(2) is true -> single-quote wrap, double quotes literal
  @Test
  public void testJsString_doubleQuoteOnly_wrapsSingleQuote_literalDoubleQuote() throws Throwable {
    String input = "say " + "\"" + "hi" + "\"";
    String result = CodeGenerator.jsString(input, null);
    String expected = "'" + "say " + "\"" + "hi" + "\"" + "'";
    assertEquals(expected, result);
  }

  // jsString: singleq(1) == doubleq(1) tie -> defaults to double-quote wrap
  @Test
  public void testJsString_equalQuoteCounts_defaultsDoubleQuote() throws Throwable {
    String input = "'" + "\"";
    String result = CodeGenerator.jsString(input, null);
    String expected = "\"" + "'" + "\\\"" + "\"";
    assertEquals(expected, result);
  }

  // jsString: NUL char escaped as \0
  @Test
  public void testJsString_nullChar_escapedAsZero() throws Throwable {
    String result = CodeGenerator.jsString("\0", null);
    assertEquals("\"\\0\"", result);
  }

  // jsString: newline escaped as \n
  @Test
  public void testJsString_newline_escaped() throws Throwable {
    String result = CodeGenerator.jsString("\n", null);
    assertEquals("\"\\n\"", result);
  }

  // jsString: tab escaped as \t
  @Test
  public void testJsString_tab_escaped() throws Throwable {
    String result = CodeGenerator.jsString("\t", null);
    assertEquals("\"\\t\"", result);
  }

  // jsString: carriage return escaped as \r
  @Test
  public void testJsString_carriageReturn_escaped() throws Throwable {
    String result = CodeGenerator.jsString("\r", null);
    assertEquals("\"\\r\"", result);
  }

  // jsString: backslash is always doubled regardless of quote choice
  @Test
  public void testJsString_backslash_doubleEscaped() throws Throwable {
    String result = CodeGenerator.jsString("\\", null);
    assertEquals("\"\\\\\"", result);
  }

  // jsString: '<' immediately followed by "/script" (case-insensitive) is broken with a backslash
  @Test
  public void testJsString_closeScriptTag_escaped() throws Throwable {
    String input = "<" + "/script>";
    String result = CodeGenerator.jsString(input, null);
    String expected = "\"" + "<\\" + "/script>" + "\"";
    assertEquals(expected, result);
  }

  // jsString: '<' immediately followed by "!--" is broken with a backslash
  @Test
  public void testJsString_htmlCommentOpen_escaped() throws Throwable {
    String result = CodeGenerator.jsString("<!--", null);
    String expected = "\"" + "<\\" + "!--" + "\"";
    assertEquals(expected, result);
  }

  // jsString: "-->" becomes "--\>" to avoid closing an HTML comment
  @Test
  public void testJsString_doubleDashCloseAngle_escaped() throws Throwable {
    String result = CodeGenerator.jsString("-->", null);
    String expected = "\"" + "--" + "\\>" + "\"";
    assertEquals(expected, result);
  }

  // jsString: "]]>" becomes "]]\>" to avoid closing a CDATA section
  @Test
  public void testJsString_closeBracketCloseAngle_escaped() throws Throwable {
    String result = CodeGenerator.jsString("]]>", null);
    String expected = "\"" + "]]" + "\\>" + "\"";
    assertEquals(expected, result);
  }

  // jsString: '>' not preceded by "--" or "]]" is left literal
  @Test
  public void testJsString_angleNotSpecial_literal() throws Throwable {
    String result = CodeGenerator.jsString("a>", null);
    assertEquals("\"a>\"", result);
  }

  // jsString: control char below space (0x1f) must be hex-escaped
  @Test
  public void testJsString_controlCharBelowSpace_escaped() throws Throwable {
    String input = String.valueOf((char) 0x1f);
    String result = CodeGenerator.jsString(input, null);
    assertEquals("\"\\u001f\"", result);
  }

  // jsString: space (0x20) is the lowest printable ASCII char, passed through literally
  @Test
  public void testJsString_spaceBoundary_literal() throws Throwable {
    String result = CodeGenerator.jsString(" ", null);
    assertEquals("\" \"", result);
  }

  // jsString: '~' (0x7e) is the highest printable ASCII char, passed through literally
  @Test
  public void testJsString_tildeBoundary_literal() throws Throwable {
    String result = CodeGenerator.jsString("~", null);
    assertEquals("\"~\"", result);
  }

  // jsString: 0x7f (DEL) is a control char outside printable ASCII (0x20-0x7e); must be hex-escaped
  @Test
  public void testJsString_delCharBoundary_mustBeEscaped_bug() throws Throwable {
    String input = String.valueOf((char) 0x7f);
    String result = CodeGenerator.jsString(input, null);
    assertEquals("\"\\u007f\"", result);
  }

  // jsString: '<' not followed by "/script" or "!--" is left literal
  @Test
  public void testJsString_lessThanNotFollowedBySpecial_literal() throws Throwable {
    String result = CodeGenerator.jsString("a<b", null);
    assertEquals("\"a<b\"", result);
  }

  // regexpEscape(2-arg): wraps content in slashes, plain text unchanged
  @Test
  public void testRegexpEscape_plainText_wrapsSlash() throws Throwable {
    String result = CodeGenerator.regexpEscape("abc", null);
    assertEquals("/abc/", result);
  }

  // regexpEscape: a literal backslash is preserved as a single backslash (not doubled)
  @Test
  public void testRegexpEscape_backslashPreservedSingle() throws Throwable {
    String input = "\\d";
    String result = CodeGenerator.regexpEscape(input, null);
    assertEquals("/" + input + "/", result);
  }

  // regexpEscape: shares the </script escaping logic with jsString
  @Test
  public void testRegexpEscape_closeScriptTag_escaped() throws Throwable {
    String input = "<" + "/script";
    String result = CodeGenerator.regexpEscape(input, null);
    String expected = "/" + "<\\" + "/script" + "/";
    assertEquals(expected, result);
  }

  // regexpEscape: shares the "-->" escaping logic with jsString
  @Test
  public void testRegexpEscape_doubleDashCloseAngle_escaped() throws Throwable {
    String result = CodeGenerator.regexpEscape("-->", null);
    String expected = "/" + "--" + "\\>" + "/";
    assertEquals(expected, result);
  }

  // escapeToDoubleQuotedJsString: always double-quoted, apostrophe left literal
  @Test
  public void testEscapeToDoubleQuotedJsString_apostropheLiteral() throws Throwable {
    String result = CodeGenerator.escapeToDoubleQuotedJsString("it's");
    assertEquals("\"it's\"", result);
  }

  // escapeToDoubleQuotedJsString: a double quote inside content is escaped
  @Test
  public void testEscapeToDoubleQuotedJsString_doubleQuoteEscaped() throws Throwable {
    String input = "say " + "\"" + "hi" + "\"";
    String result = CodeGenerator.escapeToDoubleQuotedJsString(input);
    String expected = "\"" + "say " + "\\\"" + "hi" + "\\\"" + "\"";
    assertEquals(expected, result);
  }

  // escapeToDoubleQuotedJsString: always uses double quotes even with many double quotes present
  @Test
  public void testEscapeToDoubleQuotedJsString_alwaysDoubleQuote_evenWithManyDoubleQuotes() throws Throwable {
    String input = "\"" + "\"" + "\"";
    String result = CodeGenerator.escapeToDoubleQuotedJsString(input);
    String expected = "\"" + "\\\"" + "\\\"" + "\\\"" + "\"";
    assertEquals(expected, result);
  }

  // regexpEscape(1-arg): delegates to the 2-arg overload with a null encoder
  @Test
  public void testRegexpEscape_oneArgOverload_matchesTwoArgWithNull() throws Throwable {
    String result = CodeGenerator.regexpEscape("abc");
    assertEquals("/abc/", result);
  }

  // strEscape: with a real CharsetEncoder, an encodable char is passed through literally
  @Test
  public void testStrEscape_withEncoder_encodableCharPassthrough() throws Throwable {
    CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
    String result = CodeGenerator.strEscape("A", '"', "\\\"", "\'", "\\\\", encoder);
    assertEquals("\"A\"", result);
  }

  // strEscape: with a real CharsetEncoder, a non-encodable char is hex-escaped
  @Test
  public void testStrEscape_withEncoder_nonEncodableChar_hexEscaped() throws Throwable {
    CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
    String input = String.valueOf('\u20ac');
    String result = CodeGenerator.strEscape(input, '"', "\\\"", "\'", "\\\\", encoder);
    assertEquals("\"\\u20ac\"", result);
  }

  // strEscape: with an encoder, DEL (0x7f) is within the US-ASCII encodable range and passes through
  @Test
  public void testStrEscape_withEncoder_delWithinAsciiRange_passthrough() throws Throwable {
    CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
    String input = String.valueOf((char) 0x7f);
    String result = CodeGenerator.strEscape(input, '"', "\\\"", "\'", "\\\\", encoder);
    String expected = "\"" + input + "\"";
    assertEquals(expected, result);
  }

  // identifierEscape: plain ASCII identifier chars are returned unchanged
  @Test
  public void testIdentifierEscape_plainAsciiIdentifier_unchanged() throws Throwable {
    String result = CodeGenerator.identifierEscape("foo_Bar1");
    assertEquals("foo_Bar1", result);
  }

  // identifierEscape: a non-Latin character is hex-escaped
  @Test
  public void testIdentifierEscape_nonLatinChar_hexEscaped() throws Throwable {
    String input = "a" + "\u00e9" + "b";
    String result = CodeGenerator.identifierEscape(input);
    assertEquals("a\\u00e9b", result);
  }
}
