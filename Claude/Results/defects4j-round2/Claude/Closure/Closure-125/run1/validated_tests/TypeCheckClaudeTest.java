package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;

public class TypeCheckClaudeTest {

  private Result compileJs(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", js);
    return compiler.compile(externs, input, options);
  }

  // Sanity: empty program should type-check cleanly.
  @Test
  public void testEmptyScript_noErrorsNoWarnings() throws Throwable {
    Result result = compileJs("");
    assertTrue(result.success);
    assertEquals(0, result.warnings.length);
    assertEquals(0, result.errors.length);
  }

  // Token.CAST: casting a number to number is a trivial, legal cast.
  @Test
  public void testCastNumberToNumber_noWarning() throws Throwable {
    Result result = compileJs("var x = /** @type {number} */ (5);");
    assertEquals(0, result.warnings.length);
  }

  // Token.CAST: casting a number to an unrelated type (string) is illegal.
  @Test
  public void testCastNumberToString_warns() throws Throwable {
    Result result = compileJs("var x = /** @type {string} */ (5);");
    assertTrue(result.warnings.length > 0);
  }

  // Token.NUMBER / visitVar: number literal assigned to @type {number} is valid.
  @Test
  public void testNumberVarWithNumberLiteral_noWarning() throws Throwable {
    Result result = compileJs("/** @type {number} */ var x = 5;");
    assertEquals(0, result.warnings.length);
  }

  // Token.ARRAYLIT: array literal is always typed, no warning expected.
  @Test
  public void testArrayLiteral_typedWithoutWarning() throws Throwable {
    Result result = compileJs("var a = [];");
    assertEquals(0, result.warnings.length);
  }

  // Token.REGEXP: regex literal is always typed, no warning expected.
  @Test
  public void testRegexLiteral_typedWithoutWarning() throws Throwable {
    Result result = compileJs("var r = /abc/;");
    assertEquals(0, result.warnings.length);
  }

  // visitVar: assigning a string literal to a @type {number} variable is a mismatch.
  @Test
  public void testStringLiteralAssignedToNumberType_warns() throws Throwable {
    Result result = compileJs("/** @type {number} */ var x = 'hello';");
    assertTrue(result.warnings.length > 0);
  }

  // visitVar: assigning a boolean literal to a @type {string} variable is a mismatch.
  @Test
  public void testBooleanAssignedToStringType_warns() throws Throwable {
    Result result = compileJs("/** @type {string} */ var s = true;");
    assertTrue(result.warnings.length > 0);
  }

  // visitVar: assigning an array literal to a @type {number} variable is a mismatch.
  @Test
  public void testArrayAssignedToNumberType_warns() throws Throwable {
    Result result = compileJs("/** @type {number} */ var x = [];");
    assertTrue(result.warnings.length > 0);
  }

  // visitNew: instantiating a non-constructor (number) type must warn.
  @Test
  public void testNewOnNumberType_notConstructor_warns() throws Throwable {
    Result result = compileJs("/** @type {number} */ var n = 5; new n();");
    assertTrue(result.warnings.length > 0);
  }

  // visitNew: calling new on a real @constructor is valid.
  @Test
  public void testNewOnFunctionConstructor_noWarning() throws Throwable {
    Result result = compileJs("/** @constructor */ function Foo() {} var f = new Foo();");
    assertEquals(0, result.warnings.length);
  }

  // visitCall: calling a number value is not callable.
  @Test
  public void testCallOnNumberType_notCallable_warns() throws Throwable {
    Result result = compileJs("/** @type {number} */ var n = 5; n();");
    assertTrue(result.warnings.length > 0);
  }

  // visitCall: calling a declared function with correct arity is valid.
  @Test
  public void testCallOnFunctionType_noWarning() throws Throwable {
    Result result = compileJs("function f() {} f();");
    assertEquals(0, result.warnings.length);
  }

  // visitParameterList: calling with fewer args than required warns.
  @Test
  public void testFunctionCallMissingArgument_warns() throws Throwable {
    Result result = compileJs("/** @param {number} a */ function f(a) {} f();");
    assertTrue(result.warnings.length > 0);
  }

  // visitParameterList: calling with more args than allowed warns.
  @Test
  public void testFunctionCallTooManyArguments_warns() throws Throwable {
    Result result = compileJs("/** @param {number} a */ function f(a) {} f(1, 2);");
    assertTrue(result.warnings.length > 0);
  }

  // visitParameterList: calling with the exact required argument count is valid.
  @Test
  public void testFunctionCallCorrectArgCount_noWarning() throws Throwable {
    Result result = compileJs("/** @param {number} a */ function f(a) {} f(1);");
    assertEquals(0, result.warnings.length);
  }

  // visitParameterList: passing a wrongly-typed argument warns.
  @Test
  public void testFunctionCallArgTypeMismatch_warns() throws Throwable {
    Result result = compileJs("/** @param {number} a */ function f(a) {} f('str');");
    assertTrue(result.warnings.length > 0);
  }

  // visitReturn: returning a value whose type mismatches @return warns.
  @Test
  public void testReturnTypeMismatch_warns() throws Throwable {
    Result result = compileJs("/** @return {string} */ function f() { return 5; }");
    assertTrue(result.warnings.length > 0);
  }

  // visitReturn: returning a value matching the declared @return type is valid.
  @Test
  public void testReturnTypeMatch_noWarning() throws Throwable {
    Result result = compileJs("/** @return {number} */ function f() { return 5; }");
    assertEquals(0, result.warnings.length);
  }

  // visitReturn: returning a value from a void function warns.
  @Test
  public void testVoidFunctionReturningValue_warns() throws Throwable {
    Result result = compileJs("/** @return {void} */ function f() { return 5; }");
    assertTrue(result.warnings.length > 0);
  }

  // checkTypeofString: comparing typeof to an invalid string literal warns.
  @Test
  public void testTypeofInvalidComparisonString_warns() throws Throwable {
    Result result = compileJs("var x = 1; var r = (typeof x == 'foo');");
    assertTrue(result.warnings.length > 0);
  }

  // checkTypeofString: comparing typeof to a valid string literal is fine.
  @Test
  public void testTypeofValidComparisonString_noWarning() throws Throwable {
    Result result = compileJs("var x = 1; var r = (typeof x == 'number');");
    assertEquals(0, result.warnings.length);
  }

  // Token.SHEQ: comparing unrelated primitive types (number vs string) is deterministically false.
  @Test
  public void testStrictEqualityDifferentPrimitiveTypes_deterministicWarns() throws Throwable {
    Result result = compileJs("/** @type {number} */ var n = 5; var b = (n === 'test');");
    assertTrue(result.warnings.length > 0);
  }

  // Token.SHEQ: comparing two values of the same type is not deterministic, no warning.
  @Test
  public void testStrictEqualitySameType_noWarning() throws Throwable {
    Result result = compileJs(
        "/** @type {number} */ var n = 5; /** @type {number} */ var m = 6; var b = (n === m);");
    assertEquals(0, result.warnings.length);
  }

  // Token.EQ: comparing two generic numbers is not deterministic, no warning.
  @Test
  public void testEqualityBetweenSameNumbers_noWarning() throws Throwable {
    Result result = compileJs("var a = 1; var b = 2; var c = (a == b);");
    assertEquals(0, result.warnings.length);
  }



  // Token.IN: an object literal is a valid right operand of 'in'.
  @Test
  public void testInOperatorObjectRight_noWarning() throws Throwable {
    Result result = compileJs("var b = ('a' in {});");
    assertEquals(0, result.warnings.length);
  }

  // Token.INSTANCEOF: right operand must be an object; a number is not.
  @Test
  public void testInstanceofNonObjectRight_warns() throws Throwable {
    Result result = compileJs(
        "/** @type {number} */ var n = 5; /** @type {number} */ var m = 6; "
        + "var b = (n instanceof m);");
    assertTrue(result.warnings.length > 0);
  }

  // Token.INSTANCEOF: a constructor is a valid right operand.
  @Test
  public void testInstanceofObjectRight_noWarning() throws Throwable {
    Result result = compileJs(
        "/** @constructor */ function Foo() {} var f = new Foo(); "
        + "var b = (f instanceof Foo);");
    assertEquals(0, result.warnings.length);
  }



  // Token.OBJECTLIT: a plain object literal with fresh properties is valid.
  @Test
  public void testObjectLiteralWithProperties_noWarning() throws Throwable {
    Result result = compileJs("var o = { a: 1, b: 'x' };");
    assertEquals(0, result.warnings.length);
  }

  // Token.ASSIGN: assigning a mismatched type to a declared variable warns.
  @Test
  public void testAssignNumberToDeclaredStringVar_warns() throws Throwable {
    Result result = compileJs("/** @type {string} */ var s; s = 5;");
    assertTrue(result.warnings.length > 0);
  }

  // Token.ASSIGN: assigning a matching type to a declared variable is valid.
  @Test
  public void testAssignMatchingTypes_noWarning() throws Throwable {
    Result result = compileJs("/** @type {string} */ var s; s = 'hi';");
    assertEquals(0, result.warnings.length);
  }

  // Token.ADD: adding two numbers never produces a type warning.
  @Test
  public void testAddNumbers_noWarning() throws Throwable {
    Result result = compileJs("var x = 1 + 2;");
    assertEquals(0, result.warnings.length);
  }

  // Token.NOT: logical not always yields boolean, never warns.
  @Test
  public void testLogicalNotAlwaysBoolean_noWarning() throws Throwable {
    Result result = compileJs("var b = !5;");
    assertEquals(0, result.warnings.length);
  }

  // Token.VOID: void operator always yields void type, never warns.
  @Test
  public void testVoidOperator_noWarning() throws Throwable {
    Result result = compileJs("var v = void 0;");
    assertEquals(0, result.warnings.length);
  }

  // Token.INC: incrementing a declared number variable is valid.
  @Test
  public void testIncrementOnNumber_noWarning() throws Throwable {
    Result result = compileJs("/** @type {number} */ var x = 5; x++;");
    assertEquals(0, result.warnings.length);
  }

  // Token.LT: comparing two numbers never warns.
  @Test
  public void testNumericComparison_noWarning() throws Throwable {
    Result result = compileJs("var b = (1 < 2);");
    assertEquals(0, result.warnings.length);
  }
}
