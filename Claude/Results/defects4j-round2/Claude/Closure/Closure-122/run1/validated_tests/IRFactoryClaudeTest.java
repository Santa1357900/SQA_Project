package com.google.javascript.jscomp.parsing;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class IRFactoryClaudeTest {

  private Result compileWithMode(String js, CompilerOptions.LanguageMode mode)
      throws Throwable {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(mode);
    Compiler compiler = new Compiler();
    return compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("input.js", js),
        options);
  }

  private Node parseFirstStatementWithMode(String js, CompilerOptions.LanguageMode mode)
      throws Throwable {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(mode);
    Compiler compiler = new Compiler();
    Result result = compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("input.js", js),
        options);
    assertTrue("expected successful compile for: " + js, result.success);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    Node script = mainRoot.getFirstChild();
    return script.getFirstChild();
  }

  private Node parseFirstStatement(String js) throws Throwable {
    return parseFirstStatementWithMode(js, CompilerOptions.LanguageMode.ECMASCRIPT5);
  }

  // Covers processVariableDeclaration + processVariableInitializer + NUMBER literal
  @Test
  public void testVarDeclaration_simpleNumber() throws Throwable {
    Node stmt = parseFirstStatement("var x = 1;");
    assertEquals(Token.VAR, stmt.getType());
    Node name = stmt.getFirstChild();
    assertEquals("x", name.getString());
    Node value = name.getFirstChild();
    assertTrue(value.isNumber());
    assertEquals(1.0, value.getDouble(), 1e-9);
  }

  // Covers processFunctionNode: named function statement, params, empty block
  @Test
  public void testFunctionDeclaration_namedWithParams() throws Throwable {
    Node fn = parseFirstStatement("function foo(a, b) {}");
    assertEquals(Token.FUNCTION, fn.getType());
    Node name = fn.getFirstChild();
    assertEquals("foo", name.getString());
    Node params = name.getNext();
    assertEquals(Token.PARAM_LIST, params.getType());
    assertEquals("a", params.getFirstChild().getString());
    assertTrue(params.getNext().isBlock());
  }

  // Covers processFunctionNode: unnamed function expression gets an empty-string NAME
  @Test
  public void testFunctionExpression_unnamedHasEmptyName() throws Throwable {
    Node varStmt = parseFirstStatement("var f = function() {};");
    Node fn = varStmt.getFirstChild().getFirstChild();
    assertEquals(Token.FUNCTION, fn.getType());
    assertEquals("", fn.getFirstChild().getString());
  }

  // Covers processArrayLiteral with multiple elements
  @Test
  public void testArrayLiteral_elements() throws Throwable {
    Node varStmt = parseFirstStatement("var a = [1, 2, 3];");
    Node arr = varStmt.getFirstChild().getFirstChild();
    assertEquals(Token.ARRAYLIT, arr.getType());
    assertEquals(1.0, arr.getFirstChild().getDouble(), 1e-9);
    assertEquals(3.0, arr.getFirstChild().getNext().getNext().getDouble(), 1e-9);
  }

  // Covers processArrayLiteral with zero elements (0-iteration loop)
  @Test
  public void testArrayLiteral_empty() throws Throwable {
    Node varStmt = parseFirstStatement("var a = [];");
    Node arr = varStmt.getFirstChild().getFirstChild();
    assertEquals(Token.ARRAYLIT, arr.getType());
    assertFalse(arr.hasChildren());
  }

  // Covers processArrayLiteral: destructuring on LHS is forbidden
  @Test
  public void testArrayLiteral_destructuring_reportsError() throws Throwable {
    Result result = compileWithMode(
        "var [a, b] = [1, 2];", CompilerOptions.LanguageMode.ECMASCRIPT5);
    assertFalse(result.success);
  }

  // Covers getStringValue: integer-valued double key renders without ".0"
  @Test
  public void testObjectLiteral_numberKeyAsInteger() throws Throwable {
    Node varStmt = parseFirstStatement("var o = {1: 'a'};");
    Node obj = varStmt.getFirstChild().getFirstChild();
    Node key = obj.getFirstChild();
    assertEquals(Token.STRING_KEY, key.getType());
    assertEquals("1", key.getString());
  }

  // Covers getStringValue: non-integer double key keeps its decimal representation
  @Test
  public void testObjectLiteral_numberKeyNonInteger() throws Throwable {
    Node varStmt = parseFirstStatement("var o = {1.5: 'a'};");
    Node key = varStmt.getFirstChild().getFirstChild().getFirstChild();
    assertEquals("1.5", key.getString());
  }

  // Covers processObjectLiteral / transformAsString with a quoted string key
  @Test
  public void testObjectLiteral_stringKeyQuoted() throws Throwable {
    Node varStmt = parseFirstStatement("var o = {'a': 1};");
    Node key = varStmt.getFirstChild().getFirstChild().getFirstChild();
    assertEquals(Token.STRING_KEY, key.getType());
    assertEquals("a", key.getString());
  }

  // Covers processObjectLiteral getter handling in ES5 mode
  @Test
  public void testObjectLiteral_getterInES5() throws Throwable {
    Node varStmt = parseFirstStatementWithMode(
        "var o = {get x() { return 1; }};", CompilerOptions.LanguageMode.ECMASCRIPT5);
    Node key = varStmt.getFirstChild().getFirstChild().getFirstChild();
    assertEquals(Token.GETTER_DEF, key.getType());
  }

  // Covers processObjectLiteral: getters forbidden in ES3
  @Test
  public void testObjectLiteral_getterInES3_reportsError() throws Throwable {
    Result result = compileWithMode(
        "var o = {get x() { return 1; }};", CompilerOptions.LanguageMode.ECMASCRIPT3);
    assertFalse(result.success);
  }

  // Covers processObjectLiteral: setters forbidden in ES3
  @Test
  public void testObjectLiteral_setterInES3_reportsError() throws Throwable {
    Result result = compileWithMode(
        "var o = {set x(v) { }};", CompilerOptions.LanguageMode.ECMASCRIPT3);
    assertFalse(result.success);
  }

  // Covers processObjectLiteral: getters must have zero parameters
  @Test
  public void testObjectLiteral_getterWithParam_reportsError() throws Throwable {
    Result result = compileWithMode(
        "var o = {get x(a) { }};", CompilerOptions.LanguageMode.ECMASCRIPT5);
    assertFalse(result.success);
  }

  // Covers processObjectLiteral: setters must have exactly one parameter
  @Test
  public void testObjectLiteral_setterWithWrongParamCount_reportsError() throws Throwable {
    Result result = compileWithMode(
        "var o = {set x(a, b) { }};", CompilerOptions.LanguageMode.ECMASCRIPT5);
    assertFalse(result.success);
  }

  // Covers processObjectLiteral: setter with exactly one param is valid
  @Test
  public void testObjectLiteral_setterWithOneParam_succeeds() throws Throwable {
    Node varStmt = parseFirstStatementWithMode(
        "var o = {set x(v) { }};", CompilerOptions.LanguageMode.ECMASCRIPT5);
    Node key = varStmt.getFirstChild().getFirstChild().getFirstChild();
    assertEquals(Token.SETTER_DEF, key.getType());
  }

  // Covers processUnaryExpression: NEG on a NUMBER literal folds into the literal
  @Test
  public void testUnaryMinus_foldsIntoNumberLiteral() throws Throwable {
    Node stmt = parseFirstStatement("var x = -5;");
    Node value = stmt.getFirstChild().getFirstChild();
    assertTrue(value.isNumber());
    assertEquals(-5.0, value.getDouble(), 1e-9);
  }

  // Covers processUnaryExpression: NEG on a non-number keeps the NEG wrapper
  @Test
  public void testUnaryMinus_onNonNumberStaysNegNode() throws Throwable {
    Node stmt = parseFirstStatement("var x = -y;");
    Node value = stmt.getFirstChild().getFirstChild();
    assertEquals(Token.NEG, value.getType());
    assertEquals(Token.NAME, value.getFirstChild().getType());
  }

  // Covers processUnaryExpression: NEG folding must preserve the -0 sign (IEEE754)
  @Test
  public void testUnaryMinusZero_preservesNegativeZero() throws Throwable {
    Node stmt = parseFirstStatement("var x = -0;");
    Node value = stmt.getFirstChild().getFirstChild();
    assertTrue(value.isNumber());
    assertEquals(Double.doubleToLongBits(-0.0),
        Double.doubleToLongBits(value.getDouble()));
  }

  // Covers processUnaryExpression: postfix INC on a valid NAME target
  @Test
  public void testPostfixIncrement_structure() throws Throwable {
    Node stmt = parseFirstStatement("x++;");
    Node expr = stmt.getFirstChild();
    assertEquals(Token.INC, expr.getType());
    assertEquals(Token.NAME, expr.getFirstChild().getType());
  }

  // Covers processUnaryExpression: INC on an invalid assignment target is an error
  @Test
  public void testPrefixIncrement_invalidTarget_reportsError() throws Throwable {
    Result result = compileWithMode("++1;", CompilerOptions.LanguageMode.ECMASCRIPT5);
    assertFalse(result.success);
  }

  // Covers processUnaryExpression: DELPROP operand must be a name/property access
  @Test
  public void testDelete_invalidOperand_reportsError() throws Throwable {
    Result result = compileWithMode("delete 5;", CompilerOptions.LanguageMode.ECMASCRIPT5);
    assertFalse(result.success);
  }

  // Covers processUnaryExpression: DELPROP operand is a GETPROP, which is valid
  @Test
  public void testDelete_validOperand_succeeds() throws Throwable {
    Node stmt = parseFirstStatement("delete x.y;");
    Node expr = stmt.getFirstChild();
    assertEquals(Token.DELPROP, expr.getType());
    assertEquals(Token.GETPROP, expr.getFirstChild().getType());
  }

  // Covers processLabeledStatement: single label wrapping a statement
  @Test
  public void testLabeledStatement_structure() throws Throwable {
    Node stmt = parseFirstStatement("lbl: x;");
    assertEquals(Token.LABEL, stmt.getType());
    Node labelName = stmt.getFirstChild();
    assertEquals(Token.LABEL_NAME, labelName.getType());
    assertEquals("lbl", labelName.getString());
  }







  // Covers processSwitchStatement/processSwitchCase: CASE followed by DEFAULT_CASE
  @Test
  public void testSwitchStatement_caseAndDefault() throws Throwable {
    Node stmt = parseFirstStatement("switch (x) { case 1: break; default: break; }");
    assertEquals(Token.SWITCH, stmt.getType());
    Node caseNode = stmt.getFirstChild().getNext();
    assertEquals(Token.CASE, caseNode.getType());
    assertEquals(Token.DEFAULT_CASE, caseNode.getNext().getType());
  }

  // Covers processTryStatement: try/catch/finally are all present and ordered
  @Test
  public void testTryCatchFinally_structure() throws Throwable {
    Node stmt = parseFirstStatement("try { x(); } catch (e) { y(); } finally { z(); }");
    assertEquals(Token.TRY, stmt.getType());
    Node tryBlock = stmt.getFirstChild();
    assertTrue(tryBlock.isBlock());
    Node catchesBlock = tryBlock.getNext();
    assertEquals(Token.CATCH, catchesBlock.getFirstChild().getType());
    assertTrue(catchesBlock.getNext().isBlock());
  }

  // Covers processRegExpLiteral: pattern and flags both present
  @Test
  public void testRegExpLiteral_withFlags() throws Throwable {
    Node stmt = parseFirstStatement("var r = /abc/gi;");
    Node regexp = stmt.getFirstChild().getFirstChild();
    assertEquals(Token.REGEXP, regexp.getType());
    assertEquals("abc", regexp.getFirstChild().getString());
    assertEquals("gi", regexp.getFirstChild().getNext().getString());
  }

  // Covers processRegExpLiteral: no flags means a single child
  @Test
  public void testRegExpLiteral_noFlags() throws Throwable {
    Node stmt = parseFirstStatement("var r = /abc/;");
    Node regexp = stmt.getFirstChild().getFirstChild();
    assertEquals(Token.REGEXP, regexp.getType());
    assertTrue(regexp.hasOneChild());
  }

  // Covers processConditionalExpression: HOOK with test/true/false children
  @Test
  public void testConditionalExpression_structure() throws Throwable {
    Node stmt = parseFirstStatement("var x = a ? b : c;");
    Node hook = stmt.getFirstChild().getFirstChild();
    assertEquals(Token.HOOK, hook.getType());
    assertEquals("a", hook.getFirstChild().getString());
    assertEquals("c", hook.getFirstChild().getNext().getNext().getString());
  }

  // Covers processNewExpression: constructor name plus argument list
  @Test
  public void testNewExpression_withArgs() throws Throwable {
    Node stmt = parseFirstStatement("var x = new Foo(1, 2);");
    Node newExpr = stmt.getFirstChild().getFirstChild();
    assertEquals(Token.NEW, newExpr.getType());
    assertEquals("Foo", newExpr.getFirstChild().getString());
    assertEquals(2.0, newExpr.getFirstChild().getNext().getNext().getDouble(), 1e-9);
  }

  // Covers processForLoop: init then condition children
  @Test
  public void testForLoop_structure() throws Throwable {
    Node stmt = parseFirstStatement("for (var i = 0; i < 10; i++) {}");
    assertEquals(Token.FOR, stmt.getType());
    Node init = stmt.getFirstChild();
    assertEquals(Token.VAR, init.getType());
    assertEquals(Token.LT, init.getNext().getType());
  }

  // Covers processForInLoop: standard for-in (not for-each)
  @Test
  public void testForInLoop_structure() throws Throwable {
    Node stmt = parseFirstStatement("for (var k in obj) {}");
    assertEquals(Token.FOR, stmt.getType());
    assertEquals(Token.VAR, stmt.getFirstChild().getType());
  }



  // Covers processDoLoop: body then condition
  @Test
  public void testDoWhileLoop_structure() throws Throwable {
    Node stmt = parseFirstStatement("do { x(); } while (y);");
    assertEquals(Token.DO, stmt.getType());
    assertTrue(stmt.getFirstChild().isBlock());
    assertEquals(Token.NAME, stmt.getFirstChild().getNext().getType());
  }

  // Covers processWithStatement: object then body block
  @Test
  public void testWithStatement_structure() throws Throwable {
    Node stmt = parseFirstStatement("with (obj) { x; }");
    assertEquals(Token.WITH, stmt.getType());
    assertTrue(stmt.getFirstChild().getNext().isBlock());
  }

  // Covers parseDirectives: 'use strict' is removed as a regular statement
  @Test
  public void testUseStrictDirective_strippedFromBody() throws Throwable {
    Node stmt = parseFirstStatement("'use strict'; var x = 1;");
    assertEquals(Token.VAR, stmt.getType());
  }

  // Covers isReservedKeyword: 'yield' is reserved only in ES5 strict mode
  @Test
  public void testReservedKeywordAsIdentifier_ES5Strict_reportsError() throws Throwable {
    Result result = compileWithMode(
        "var yield = 1;", CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT);
    assertFalse(result.success);
  }

  // Covers isReservedKeyword: 'yield' is NOT reserved in plain ES5 mode
  @Test
  public void testReservedKeywordAsIdentifier_ES5NonStrict_allowed() throws Throwable {
    Node stmt = parseFirstStatementWithMode(
        "var yield = 1;", CompilerOptions.LanguageMode.ECMASCRIPT5);
    assertEquals(Token.VAR, stmt.getType());
  }

  // Covers isAllowedProp/INVALID_ES3_PROP_NAME: keyword prop access warns in ES3
  @Test
  public void testKeywordPropertyAccess_ES3_warns() throws Throwable {
    Result result = compileWithMode(
        "var o = {}; o.in = 1;", CompilerOptions.LanguageMode.ECMASCRIPT3);
    assertTrue(result.success);
    assertTrue(result.warnings.length > 0);
  }

  // Covers isAllowedProp: keyword prop access is fine from ES5 onward
  @Test
  public void testKeywordPropertyAccess_ES5_noWarning() throws Throwable {
    Result result = compileWithMode(
        "var o = {}; o.in = 1;", CompilerOptions.LanguageMode.ECMASCRIPT5);
    assertTrue(result.success);
    assertEquals(0, result.warnings.length);
  }

  // Covers processStringLiteral: basic string value extraction
  @Test
  public void testStringLiteral_value() throws Throwable {
    Node stmt = parseFirstStatement("var s = 'hello';");
    Node str = stmt.getFirstChild().getFirstChild();
    assertEquals(Token.STRING, str.getType());
    assertEquals("hello", str.getString());
  }
}
