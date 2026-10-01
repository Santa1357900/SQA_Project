package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

public class TypeCheckClaudeTest {

  // Helper: compiles a JS snippet with type checking enabled and returns the Result.
  private Result compile(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    return compiler.compile(externs, input, options);
  }

  // Baseline: a trivial well-typed program should produce no warnings/errors.
  @Test
  public void testEmptyProgram_noWarnings() throws Throwable {
    Result result = compile("var x = 1;");
    assertTrue(result.success);
    assertEquals(0, result.warnings.length);
    assertEquals(0, result.errors.length);
  }

  // Covers shouldTraverse's FUNCTION_MASKS_VARIABLE branch (IE bug masking check).
  @Test
  public void testFunctionDeclaration_masksOuterVariable_warns() throws Throwable {
    Result result = compile("function f() { var x = 3; function x() {} }");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.VAR / visitVar: matching declared type produces no warning.
  @Test
  public void testVarWithNumberType_matchingAssignment_noWarning() throws Throwable {
    Result result = compile("/** @type {number} */ var x = 1;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.VAR / visitVar: mismatched declared type produces a warning.
  @Test
  public void testVarWithNumberType_mismatchedAssignment_warns() throws Throwable {
    Result result = compile("/** @type {number} */ var x = 'str';");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitVar's loop over multiple names in a single VAR node (0/1/many iterations -> 2 here).
  @Test
  public void testVarMultipleNames_noWarning() throws Throwable {
    Result result = compile("var a = 1, b = 2;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.ARRAYLIT: ensureTyped with ARRAY_TYPE, well-typed usage.
  @Test
  public void testArrayLiteral_noWarning() throws Throwable {
    Result result = compile("var a = [1, 2, 3];");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.REGEXP: ensureTyped with REGEXP_TYPE.
  @Test
  public void testRegexLiteral_noWarning() throws Throwable {
    Result result = compile("var r = /abc/;");
    assertEquals(0, result.warnings.length);
  }

  // Covers visitGetProp: property access on a null-typed expression warns (not null/undefined check).
  @Test
  public void testGetPropOnNullType_warns() throws Throwable {
    Result result = compile("var x = null; var y = x.foo;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitGetElem: valid numeric index into array, no warning.
  @Test
  public void testGetElem_arrayIndexing_noWarning() throws Throwable {
    Result result = compile("var a = [1, 2, 3]; var b = a[0];");
    assertEquals(0, result.warnings.length);
  }

  // Covers visitNew: constructor value is not actually a constructor -> NOT_A_CONSTRUCTOR.
  @Test
  public void testNewNonConstructor_warns() throws Throwable {
    Result result = compile("var x = 1; new x();");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitNew: valid constructor call with 'new', no warning.
  @Test
  public void testNewConstructor_noWarning() throws Throwable {
    Result result = compile("/** @constructor */ function Foo() {} new Foo();");
    assertEquals(0, result.warnings.length);
  }

  // Covers visitCall: calling a non-function value -> NOT_CALLABLE.
  @Test
  public void testCallNonFunction_warns() throws Throwable {
    Result result = compile("var x = 1; x();");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitCall + visitParameterList: correctly typed call with matching arg count, no warning.
  @Test
  public void testCallFunctionCorrectArgs_noWarning() throws Throwable {
    Result result = compile("/** @param {number} a */ function f(a) {} f(1);");
    assertEquals(0, result.warnings.length);
  }

  // Covers visitParameterList: WRONG_ARGUMENT_COUNT for too few arguments.
  @Test
  public void testCallTooFewArguments_warns() throws Throwable {
    Result result = compile(
        "/** @param {number} a\n@param {number} b */ function f(a, b) {} f(1);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitParameterList: WRONG_ARGUMENT_COUNT for too many arguments.
  @Test
  public void testCallTooManyArguments_warns() throws Throwable {
    Result result = compile("/** @param {number} a */ function f(a) {} f(1, 2);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitReturn: returned value type incompatible with declared @return type.
  @Test
  public void testReturnTypeMismatch_warns() throws Throwable {
    Result result = compile("/** @return {number} */ function f() { return 'str'; }");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitReturn: returned value matches declared @return type, no warning.
  @Test
  public void testReturnTypeMatch_noWarning() throws Throwable {
    Result result = compile("/** @return {number} */ function f() { return 1; }");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.INC on a numeric operand, no warning.
  @Test
  public void testIncrementDecrement_validNumber_noWarning() throws Throwable {
    Result result = compile("var x = 1; x++;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.INC: non-number operand triggers "increment/decrement" expectNumber warning.
  @Test
  public void testIncrementDecrement_nonNumberOperand_warns() throws Throwable {
    Result result = compile("var o = {}; o++;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.VOID and Token.TYPEOF: both are always typed, no warning for valid operands.
  @Test
  public void testVoidAndTypeofOperators_noWarning() throws Throwable {
    Result result = compile("var x = 1; var y = void x; var z = typeof x;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.POS and Token.NEG on a numeric operand, no warning.
  @Test
  public void testUnaryPosNeg_noWarning() throws Throwable {
    Result result = compile("var x = 1; var y = -x; var z = +x;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.LT numeric comparison branch: non-number left with number right warns.
  @Test
  public void testNumericComparison_objectVsNumber_warns() throws Throwable {
    Result result = compile("var o = {}; var b = (o < 5);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.LT numeric comparison branch: both numbers, no warning.
  @Test
  public void testNumericComparison_bothNumbers_noWarning() throws Throwable {
    Result result = compile("var a = 1; var b = 2; var c = (a < b);");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.IN: right operand must be an object, a number right side warns.
  @Test
  public void testInOperator_nonObjectRight_warns() throws Throwable {
    Result result = compile("var b = ('a' in 5);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.INSTANCEOF: non-object operands on both sides warn.
  @Test
  public void testInstanceofNonObjectOperands_warns() throws Throwable {
    Result result = compile("var a = 1; var b = 2; var c = (a instanceof b);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.INSTANCEOF: valid object instance and constructor, no warning.
  @Test
  public void testInstanceofValidOperands_noWarning() throws Throwable {
    Result result = compile(
        "/** @constructor */ function Foo() {} var f = new Foo(); var b = (f instanceof Foo);");
    assertEquals(0, result.warnings.length);
  }

  // Covers visitAssign's prototype-overwrite check: overriding prototype with non-object warns.
  @Test
  public void testPrototypeOverriddenWithNonObject_warns() throws Throwable {
    Result result = compile("/** @constructor */ function Foo() {} Foo.prototype = 1;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitBinaryOperator BITAND branch: non-bitwiseable operand warns.
  @Test
  public void testBitwiseOperator_nonBitwiseableOperand_warns() throws Throwable {
    Result result = compile("var o = {}; var b = (o & 1);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitBinaryOperator BITAND branch: numeric operands, no warning.
  @Test
  public void testBitwiseOperator_numbers_noWarning() throws Throwable {
    Result result = compile("var a = 1; var b = 2; var c = (a & b);");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.DELPROP: deletion always typed boolean, valid usage produces no warning.
  @Test
  public void testDeleteOperator_noWarning() throws Throwable {
    Result result = compile("var o = {a: 1}; var b = delete o.a;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.CASE: switch discriminant type differs from case type, warns.
  @Test
  public void testSwitchCaseTypeMismatch_warns() throws Throwable {
    Result result = compile("var x = 1; switch (x) { case 'str': break; }");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.WITH: non-object expression in a with-statement warns.
  @Test
  public void testWithStatement_nonObjectExpression_warns() throws Throwable {
    Result result = compile("with (5) { }");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitFunction: a constructor extending an interface -> CONFLICTING_EXTENDED_TYPE.
  @Test
  public void testConstructorExtendsInterface_conflictingExtendedType_warns() throws Throwable {
    Result result = compile(
        "/** @interface */ function Foo() {}\n"
        + "/** @constructor\n@extends {Foo} */ function Bar() {}");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitFunction: an interface extending a constructor -> CONFLICTING_EXTENDED_TYPE.
  @Test
  public void testInterfaceExtendsConstructor_conflictingExtendedType_warns() throws Throwable {
    Result result = compile(
        "/** @constructor */ function Foo() {}\n"
        + "/** @interface\n@extends {Foo} */ function Bar() {}");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitFunction: an interface implementing another interface -> CONFLICTING_IMPLEMENTED_TYPE.
  @Test
  public void testInterfaceImplementsInterface_conflictingImplementedType_warns() throws Throwable {
    Result result = compile(
        "/** @interface */ function IFoo() {}\n"
        + "/** @interface\n@implements {IFoo} */ function IBar() {}");
    assertTrue(result.warnings.length > 0);
  }

  // Covers checkDeclaredPropertyInheritance: @override with no matching super property -> UNKNOWN_OVERRIDE.
  @Test
  public void testOverrideWithoutSuperclassProperty_unknownOverride_warns() throws Throwable {
    Result result = compile(
        "/** @constructor */ function Foo() {}\n"
        + "/** @override */ Foo.prototype.bar = function() {};");
    assertTrue(result.warnings.length > 0);
  }

  // Covers checkPropertyAccess for enums: accessing a nonexistent enum element warns.
  @Test
  public void testEnumInexistentElement_warns() throws Throwable {
    Result result = compile(
        "/** @enum {number} */ var Color = {RED: 1, GREEN: 2}; var c = Color.BLUE;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers checkPropertyAccess for enums: accessing an existing enum element, no warning.
  @Test
  public void testEnumValidElement_noWarning() throws Throwable {
    Result result = compile(
        "/** @enum {number} */ var Color = {RED: 1, GREEN: 2}; var c = Color.RED;");
    assertEquals(0, result.warnings.length);
  }

  // Covers visitObjLitKey: object literal value type incompatible with declared record type.
  @Test
  public void testObjectLiteralPropertyTypeMismatch_warns() throws Throwable {
    Result result = compile("/** @type {{a: number}} */ var obj = {a: 'str'};");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitCall: function with explicit @this type called without GETPROP/GETELEM -> EXPECTED_THIS_TYPE.
  @Test
  public void testExpectedThisType_warns() throws Throwable {
    Result result = compile(
        "/** @constructor */ function Foo() {}\n"
        + "/** @this {Foo} */ function bar() {}\n"
        + "bar();");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitCall: calling a constructor without 'new' -> CONSTRUCTOR_NOT_CALLABLE.
  @Test
  public void testConstructorCalledWithoutNew_warns() throws Throwable {
    Result result = compile("/** @constructor */ function Foo() {} Foo();");
    assertTrue(result.warnings.length > 0);
  }
}
