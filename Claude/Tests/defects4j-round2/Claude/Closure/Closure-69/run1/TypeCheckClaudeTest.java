package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class TypeCheckClaudeTest {

  private Result compile(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    return compiler.compile(externs, input, options);
  }

  // Covers Token.ADD/MUL with valid number operands - no type warnings expected
  @Test
  public void testValidArithmetic_noWarnings() throws Throwable {
    Result result = compile("var x = 1; var y = x + 2; var z = y * 3;");
    assertTrue(result.success);
    assertEquals(0, result.warnings.length);
  }

  // Covers visitVar: declared (non-inferred) type mismatch on initialization
  @Test
  public void testVarTypeAnnotationMismatch_reportsWarning() throws Throwable {
    Result result = compile("/** @type {number} */ var x = 'hello';");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitAssign NAME-lvalue branch falling through to expectCanAssignTo
  @Test
  public void testNameLvalueAssignmentMismatch_reportsWarning() throws Throwable {
    Result result = compile("/** @type {string} */ var s; s = 5;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers OBJECTLIT/enum handling with valid elements and property access
  @Test
  public void testValidEnumDeclaration_noWarnings() throws Throwable {
    Result result = compile(
        "/** @enum {number} */ var Color = {RED: 1, GREEN: 2}; var c = Color.RED;");
    assertTrue(result.success);
    assertEquals(0, result.warnings.length);
  }

  // Covers enum element duplication which violates the enum contract
  @Test
  public void testEnumDuplicateKey_compilationFails() throws Throwable {
    Result result = compile("/** @enum {number} */ var Color = {RED: 1, RED: 2};");
    assertFalse(result.success);
  }

  // Covers visitNew: NAME constructor referencing a non-function type
  @Test
  public void testNewOnNonConstructorNumber_reportsNotAConstructor() throws Throwable {
    Result result = compile("/** @type {number} */ var x = 1; var y = new x();");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitCall: constructor type invoked without 'new'
  @Test
  public void testConstructorCalledWithoutNew_reportsConstructorNotCallable() throws Throwable {
    Result result = compile("/** @constructor */ function Foo() {} Foo();");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitCall: calling a non-function (number) type
  @Test
  public void testCallOnNumber_reportsNotCallable() throws Throwable {
    Result result = compile("var x = 1; x();");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.DELPROP: deleting a non-reference (number literal)
  @Test
  public void testDeleteNumberLiteral_reportsBadDelete() throws Throwable {
    Result result = compile("delete 5;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.DELPROP: deleting an existing object property (valid reference)
  @Test
  public void testDeleteObjectProperty_noWarning() throws Throwable {
    Result result = compile("var obj = {prop: 1}; delete obj.prop;");
    assertEquals(0, result.warnings.length);
  }

  // Covers SHEQ: shallow equality between incompatible primitive types
  @Test
  public void testShallowEqualityNumberVsBoolean_reportsDeterministicTestNoResult()
      throws Throwable {
    Result result = compile("var r = (1 === true);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.LSH: left operand that cannot match int32 context
  @Test
  public void testLeftShiftOnObject_reportsBitOperation() throws Throwable {
    Result result = compile("var obj = {}; var r = obj << 1;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.BITNOT: operand that cannot match int32 context
  @Test
  public void testBitwiseNotOnObject_reportsBitOperation() throws Throwable {
    Result result = compile("var obj = {}; var r = ~obj;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers LT/GT family: non-number left operand compared against a number
  @Test
  public void testNumericComparisonObjectVsNumber_reportsWarning() throws Throwable {
    Result result = compile("var obj = {}; var r = obj < 5;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.IN: right operand that is not an object
  @Test
  public void testInOperatorNonObjectRight_reportsWarning() throws Throwable {
    Result result = compile("var r = ('a' in 5);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.INSTANCEOF: right operand that is not an actual object
  @Test
  public void testInstanceofNonObjectRight_reportsWarning() throws Throwable {
    Result result = compile("var r = ({} instanceof 5);");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.INSTANCEOF: left operand that can never be an object
  @Test
  public void testInstanceofPrimitiveLeft_reportsWarning() throws Throwable {
    Result result = compile("var r = (5 instanceof {});");
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitAssign object.property branch with declared, non-inferred type
  @Test
  public void testAssignWrongTypeToDeclaredProperty_reportsWarning() throws Throwable {
    String js = "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.bar = 1;\n"
        + "var f = new Foo(); f.bar = 'hello';";
    Result result = compile(js);
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitAssign: object.prototype = non-object value on a constructor
  @Test
  public void testPrototypeAssignedNonObject_reportsWarning() throws Throwable {
    Result result = compile("/** @constructor */ function Foo() {} Foo.prototype = 5;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers checkDeclaredPropertyInheritance: @override with incompatible superclass type
  @Test
  public void testOverrideIncompatibleType_reportsWarning() throws Throwable {
    String js = "/** @constructor */ function Base() {}\n"
        + "/** @type {number} */ Base.prototype.x = 1;\n"
        + "/**\n * @constructor\n * @extends {Base}\n */\n"
        + "function Sub() {}\n"
        + "/** @override */ Sub.prototype.x = 'hello';";
    Result result = compile(js);
    assertTrue(result.warnings.length > 0);
  }

  // Covers checkDeclaredPropertyInheritance: @override without any super property
  @Test
  public void testOverrideWithoutSuperProperty_reportsUnknownOverride() throws Throwable {
    String js = "/** @constructor */ function Foo() {}\n"
        + "/** @override */ Foo.prototype.bar = function() {};";
    Result result = compile(js);
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitInterfaceGetprop: interface method body must be empty
  @Test
  public void testInterfaceNonEmptyMethodBody_reportsInterfaceFunctionNotEmpty()
      throws Throwable {
    String js = "/** @interface */ function Foo() {}\n"
        + "Foo.prototype.bar = function() { return 1; };";
    Result result = compile(js);
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitInterfaceGetprop: empty interface method body is valid
  @Test
  public void testInterfaceEmptyMethodBody_noWarning() throws Throwable {
    String js = "/** @interface */ function Foo() {}\n"
        + "Foo.prototype.bar = function() {};";
    Result result = compile(js);
    assertEquals(0, result.warnings.length);
  }

  // Covers visitParameterList: too many arguments supplied to a function call
  @Test
  public void testCallTooManyArguments_reportsWrongArgumentCount() throws Throwable {
    String js = "/**\n * @param {number} a\n * @param {number} b\n */\n"
        + "function add(a, b) { return a + b; }\n"
        + "add(1, 2, 3);";
    Result result = compile(js);
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitParameterList: too few arguments supplied to a function call
  @Test
  public void testCallTooFewArguments_reportsWrongArgumentCount() throws Throwable {
    String js = "/**\n * @param {number} a\n * @param {number} b\n */\n"
        + "function add(a, b) { return a + b; }\n"
        + "add(1);";
    Result result = compile(js);
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitParameterList: correct argument count produces no warning
  @Test
  public void testCallCorrectArguments_noWarning() throws Throwable {
    String js = "/**\n * @param {number} a\n * @param {number} b\n */\n"
        + "function add(a, b) { return a + b; }\n"
        + "add(1, 2);";
    Result result = compile(js);
    assertEquals(0, result.warnings.length);
  }

  // Covers shouldTraverse: a nested function masking an outer variable
  @Test
  public void testFunctionMasksVariable_reportsWarning() throws Throwable {
    Result result = compile("function f() { var x = 1; function x() {} }");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.SUB: non-number left operand to a subtraction operator
  @Test
  public void testSubtractionOnObject_reportsWarning() throws Throwable {
    Result result = compile("var obj = {}; var r = obj - 1;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.BITAND: non-bitwiseable operand to a bitwise operator
  @Test
  public void testBitwiseAndOnObject_reportsWarning() throws Throwable {
    Result result = compile("var obj = {}; var r = obj & 1;");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.CASE: switch expression type incompatible with case type
  @Test
  public void testSwitchCaseTypeMismatch_reportsWarning() throws Throwable {
    Result result = compile("var x = 1; switch (x) { case 'a': break; }");
    assertTrue(result.warnings.length > 0);
  }

  // Covers Token.CASE: switch expression type matching case type
  @Test
  public void testSwitchCaseTypeMatch_noWarning() throws Throwable {
    Result result = compile("var x = 1; switch (x) { case 2: break; default: break; }");
    assertEquals(0, result.warnings.length);
  }

  // Covers visitReturn: declared return type incompatible with actual return value
  @Test
  public void testReturnTypeMismatch_reportsWarning() throws Throwable {
    String js = "/** @return {number} */ function f() { return 'hello'; }";
    Result result = compile(js);
    assertTrue(result.warnings.length > 0);
  }

  // Covers visitReturn: declared return type matching actual return value
  @Test
  public void testReturnTypeMatch_noWarning() throws Throwable {
    String js = "/** @return {number} */ function g() { return 5; }";
    Result result = compile(js);
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.NOT/VOID/TYPEOF: unary operators always produce their fixed type
  @Test
  public void testUnaryOperators_noWarnings() throws Throwable {
    Result result = compile("var a = !true; var b = void 0; var c = typeof a;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.ARRAYLIT/REGEXP: literal expressions ensure a fixed native type
  @Test
  public void testArrayAndRegexpLiterals_noWarnings() throws Throwable {
    Result result = compile("var a = [1, 2, 3]; var b = /test/;");
    assertEquals(0, result.warnings.length);
  }

  // Covers Token.TRY/CATCH/NAME-in-CATCH: exception variable is not separately typed
  @Test
  public void testTryCatchBlock_noWarnings() throws Throwable {
    Result result = compile("try { throw 1; } catch (e) { var y = e; }");
    assertEquals(0, result.warnings.length);
  }
}
