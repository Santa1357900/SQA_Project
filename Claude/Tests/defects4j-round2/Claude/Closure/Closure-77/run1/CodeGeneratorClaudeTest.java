package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.CharsetEncoder;

public class CodeGeneratorClaudeTest {

  private String print(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", js);
    compiler.compile(externs, input, options);
    return compiler.toSource();
  }

  // jsString: empty string -> no quotes counted, falls to else branch -> double quote delimiter
  @Test
  public void testJsString_emptyString_usesDoubleQuotes() throws Throwable {
    String result = CodeGenerator.jsString("", (CharsetEncoder) null);
    assertEquals("\"\"", result);
  }

  // jsString: singleq(1) < doubleq(0) is false -> double-quote delimiter, single quote unescaped
  @Test
  public void testJsString_moreSingleQuotesThanDouble_usesDoubleQuoteDelimiter() throws Throwable {
    String result = CodeGenerator.jsString("it's", (CharsetEncoder) null);
    assertEquals("\"it's\"", result);
  }

  // jsString: singleq(0) < doubleq(2) true -> single-quote delimiter, double quotes left unescaped
  @Test
  public void testJsString_moreDoubleQuotesThanSingle_usesSingleQuoteDelimiter() throws Throwable {
    String result = CodeGenerator.jsString("he said \"hi\"", (CharsetEncoder) null);
    assertEquals("'he said \"hi\"'", result);
  }

  // jsString: '\n' case branch escapes to literal backslash-n
  @Test
  public void testJsString_newlineEscaped() throws Throwable {
    String result = CodeGenerator.jsString("a\nb", (CharsetEncoder) null);
    assertEquals("\"a\\nb\"", result);
  }

  // jsString: '\t' case branch escapes to literal backslash-t
  @Test
  public void testJsString_tabEscaped() throws Throwable {
    String result = CodeGenerator.jsString("a\tb", (CharsetEncoder) null);
    assertEquals("\"a\\tb\"", result);
  }

  // jsString: backslash case branch doubles the backslash
  @Test
  public void testJsString_backslashDoubled() throws Throwable {
    String result = CodeGenerator.jsString("a\\b", (CharsetEncoder) null);
    assertEquals("\"a\\\\b\"", result);
  }

  // jsString: '<' followed by "/script" (case-insensitive) gets escaped to avoid </script>
  @Test
  public void testJsString_closingScriptTag_escaped() throws Throwable {
    String result = CodeGenerator.jsString("</script>", (CharsetEncoder) null);
    assertEquals("\"<\\/script>\"", result);
  }

  // jsString: '>' preceded by "--" is escaped to avoid "-->"
  @Test
  public void testJsString_htmlCommentClose_escaped() throws Throwable {
    String result = CodeGenerator.jsString("a-->b", (CharsetEncoder) null);
    assertEquals("\"a--\\>b\"", result);
  }

  // jsString: '>' preceded by "]]" is escaped to avoid "]]>"
  @Test
  public void testJsString_doubleBracketCloseAngle_escaped() throws Throwable {
    String result = CodeGenerator.jsString("a]]>b", (CharsetEncoder) null);
    assertEquals("\"a]]\\>b\"", result);
  }

  // jsString: default branch, no encoder, non-ascii char gets unicode-escaped
  @Test
  public void testJsString_nonAsciiChar_unicodeEscaped() throws Throwable {
    String result = CodeGenerator.jsString("\u00e9", (CharsetEncoder) null);
    assertEquals("\"\\u00e9\"", result);
  }

  // regexpEscape: wraps content with '/' delimiters
  @Test
  public void testRegexpEscape_wrapsInSlashes() throws Throwable {
    String result = CodeGenerator.regexpEscape("abc");
    assertEquals("/abc/", result);
  }

  // regexpEscape: backslash is NOT doubled (unlike jsString)
  @Test
  public void testRegexpEscape_backslashNotDoubled() throws Throwable {
    String result = CodeGenerator.regexpEscape("a\\b");
    assertEquals("/a\\b/", result);
  }

  // escapeToDoubleQuotedJsString: single quote left unescaped, always double-quote delimiter
  @Test
  public void testEscapeToDoubleQuotedJsString_singleQuoteNotEscaped() throws Throwable {
    String result = CodeGenerator.escapeToDoubleQuotedJsString("a'b");
    assertEquals("\"a'b\"", result);
  }

  // escapeToDoubleQuotedJsString: embedded double quote is escaped
  @Test
  public void testEscapeToDoubleQuotedJsString_doubleQuoteEscaped() throws Throwable {
    String result = CodeGenerator.escapeToDoubleQuotedJsString("a\"b");
    assertEquals("\"a\\\"b\"", result);
  }

  // identifierEscape: plain ascii identifier returned unchanged (isLatin true)
  @Test
  public void testIdentifierEscape_asciiUnchanged() throws Throwable {
    String result = CodeGenerator.identifierEscape("abc");
    assertEquals("abc", result);
  }

  // identifierEscape: non-latin char gets unicode-escaped character by character
  @Test
  public void testIdentifierEscape_nonLatinCharEscaped() throws Throwable {
    String result = CodeGenerator.identifierEscape("\u4e2d");
    assertEquals("\\u4e2d", result);
  }

  // add(Node): binary '+' printed compactly without surrounding spaces
  @Test
  public void testAdd_binaryAddition_noSpaces() throws Throwable {
    String out = print("var x = 1 + 2;");
    assertTrue(out.contains("1+2"));
  }

  // add(Node): right side with same associative operator merges without parens
  @Test
  public void testAdd_rightSideAssociative_mergesWithoutParens() throws Throwable {
    String out = print("var x = 1 + (2 + 3);");
    assertTrue(out.contains("1+2+3"));
    assertFalse(out.contains("("));
  }

  // add(Node): right side with non-associative operator must be wrapped in parens
  @Test
  public void testAdd_rightSideNonAssociative_wrapsInParens() throws Throwable {
    String out = print("var x = 1 - (2 - 3);");
    assertTrue(out.contains("1-(2-3)"));
  }

  // Token.VAR: multiple declarations joined by comma, no spaces
  @Test
  public void testVar_multipleDeclarations_commaSeparated() throws Throwable {
    String out = print("var a, b;");
    assertTrue(out.contains("var a,b"));
  }

  // Token.ARRAYLIT: elision hole preserved as empty slot between commas
  @Test
  public void testArrayLit_withHole_preservesComma() throws Throwable {
    String out = print("var a = [1,,3];");
    assertTrue(out.contains("[1,,3]"));
  }

  // Token.TYPEOF / Token.VOID unary operators
  @Test
  public void testUnary_typeofAndVoid() throws Throwable {
    String out = print("typeof a;void b;");
    assertTrue(out.contains("typeof a"));
    assertTrue(out.contains("void b"));
  }

  // Token.NEG: negated number literal collapses into a single negative number
  @Test
  public void testNeg_numberLiteral_collapsesToNegativeNumber() throws Throwable {
    String out = print("var x = -2;");
    assertTrue(out.contains("-2"));
    assertFalse(out.contains("- 2"));
  }

  // Token.HOOK: ternary printed compactly
  @Test
  public void testHook_ternary() throws Throwable {
    String out = print("var x = a ? b : c;");
    assertTrue(out.contains("a?b:c"));
  }

  // Token.FUNCTION: function expression at START_OF_EXPR wrapped in parens (IIFE)
  @Test
  public void testFunctionExpression_atStartOfExpr_wrappedInParens() throws Throwable {
    String out = print("(function(){return 1;})();");
    assertTrue(out.contains("(function("));
    assertTrue(out.contains(")()"));
  }

  // Token.FUNCTION: declaration at STATEMENT context is not wrapped in parens
  @Test
  public void testFunctionDeclaration_basic() throws Throwable {
    String out = print("function f(){return 1;}");
    assertTrue(out.contains("function f("));
    assertFalse(out.startsWith("("));
  }

  // Token.FOR: classic 3-clause for loop with var init
  @Test
  public void testFor_classicWithVar() throws Throwable {
    String out = print("for(var i = 0; i < 10; i++){a();}");
    assertTrue(out.contains("for(var i=0;i<10;i++)"));
  }

  // Token.FOR: for-in loop with var declared key
  @Test
  public void testForIn_loop() throws Throwable {
    String out = print("for(var k in obj){a();}");
    assertTrue(out.contains("for(var k in obj)"));
  }

  // Token.DO: do-while loop structure
  @Test
  public void testDoWhile_loop() throws Throwable {
    String out = print("do{a();}while(b);");
    int doIdx = out.indexOf("do");
    int whileIdx = out.indexOf("while(b)");
    assertTrue(doIdx >= 0 && whileIdx > doIdx);
  }

  // Token.WHILE: while loop structure (distinct from do-while)
  @Test
  public void testWhile_loop() throws Throwable {
    String out = print("while(a){b();}");
    assertTrue(out.contains("while(a)"));
  }

  // Token.IF: if/else branches both printed
  @Test
  public void testIfElse_basic() throws Throwable {
    String out = print("if(a){b();}else{c();}");
    assertTrue(out.contains("if(a)"));
    assertTrue(out.contains("else"));
  }

  // Token.GETPROP: numeric receiver must be wrapped in parens to avoid invalid "1.toString"
  @Test
  public void testGetProp_numberReceiver_wrappedInParens() throws Throwable {
    String out = print("(1).toString();");
    assertTrue(out.contains("(1).toString"));
  }

  // Token.GETELEM: bracket property access printed as-is
  @Test
  public void testGetElem_basic() throws Throwable {
    String out = print("a[b];");
    assertTrue(out.contains("a[b]"));
  }

  // Token.INC/DEC: postfix vs prefix operator placement
  @Test
  public void testIncDec_postfixVsPrefix() throws Throwable {
    String out = print("x++;++y;");
    assertTrue(out.contains("x++"));
    assertTrue(out.contains("++y"));
  }

  // Token.CALL: normal call with argument list
  @Test
  public void testCall_basic() throws Throwable {
    String out = print("f(a,b);");
    assertTrue(out.contains("f(a,b)"));
  }

  // Token.NEW: without explicit arguments the "()" is omitted
  @Test
  public void testNew_withoutArgsOmitsParens() throws Throwable {
    String out = print("new Foo;");
    assertTrue(out.contains("new Foo"));
    assertFalse(out.contains("Foo("));
  }

  // Token.TRY: try/catch/finally all printed
  @Test
  public void testTryCatchFinally() throws Throwable {
    String out = print("try{a();}catch(e){b();}finally{c();}");
    assertTrue(out.contains("try"));
    assertTrue(out.contains("catch(e)"));
    assertTrue(out.contains("finally"));
  }

  // Token.SWITCH/CASE/DEFAULT: switch body with case and default labels
  @Test
  public void testSwitchCaseDefault() throws Throwable {
    String out = print("switch(a){case 1:b();break;default:c();}");
    assertTrue(out.contains("case 1:"));
    assertTrue(out.contains("default:"));
  }

  // Token.LABEL / Token.CONTINUE: labeled loop with continue referencing label
  @Test
  public void testLabel_withContinue() throws Throwable {
    String out = print("lbl:while(a){continue lbl;}");
    assertTrue(out.contains("lbl:"));
    assertTrue(out.contains("continue lbl"));
  }

  // Token.DELPROP: delete expression on a property access
  @Test
  public void testDelProp() throws Throwable {
    String out = print("delete a.b;");
    assertTrue(out.contains("delete a.b"));
  }
}
