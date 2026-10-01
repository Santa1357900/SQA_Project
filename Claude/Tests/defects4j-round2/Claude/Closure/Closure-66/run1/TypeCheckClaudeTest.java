package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class TypeCheckClaudeTest {

  // Helper: compiles the given JS source with type checking enabled and
  // returns the Compiler instance so tests can inspect warning/error counts.
  private Compiler compileJs(String js) {
    Compiler comp = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", js);
    comp.compile(externs, input, options);
    return comp;
  }

  // Covers Token.DELPROP branch: operand is not a reference (NUMBER) -> BAD_DELETE warning.
  @Test
  public void testDelete_nonReferenceOperand_producesBadDeleteWarning() throws Throwable {
    Compiler c = compileJs("delete 1;");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.DELPROP branch: operand IS a reference (NAME) -> no BAD_DELETE warning.
  @Test
  public void testDelete_referenceOperand_noWarning() throws Throwable {
    Compiler c = compileJs("var x = 1; delete x;");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.NEW branch: constructor type is null (not a FunctionType) -> NOT_A_CONSTRUCTOR.
  @Test
  public void testNew_nonConstructorType_producesNotAConstructorWarning() throws Throwable {
    Compiler c = compileJs("var x = {}; new x();");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.CALL branch: constructor called without 'new' -> CONSTRUCTOR_NOT_CALLABLE.
  @Test
  public void testCall_constructorWithoutNew_producesConstructorNotCallableWarning() throws Throwable {
    Compiler c = compileJs("/** @constructor */ function Foo() {} Foo();");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.CALL branch: childType cannot be called -> NOT_CALLABLE.
  @Test
  public void testCall_nonFunctionType_producesNotCallableWarning() throws Throwable {
    Compiler c = compileJs("var x = 1; x();");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers shouldTraverse FUNCTION branch: function masks an existing non-function var.
  @Test
  public void testFunction_masksVariable_producesWarning() throws Throwable {
    Compiler c = compileJs("var x = 1; function x() {}");
    assertTrue(c.getWarningCount() > 0);
  }

  // Covers visitParameterList: too many arguments -> WRONG_ARGUMENT_COUNT.
  @Test
  public void testCall_tooManyArguments_producesWrongArgumentCountWarning() throws Throwable {
    Compiler c = compileJs("/** @param {number} a */ function f(a) {} f(1, 2);");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitParameterList: too few arguments -> WRONG_ARGUMENT_COUNT.
  @Test
  public void testCall_tooFewArguments_producesWrongArgumentCountWarning() throws Throwable {
    Compiler c = compileJs("/** @param {number} a */ function f(a) {} f();");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitParameterList: correct argument count -> no warning.
  @Test
  public void testCall_correctArgumentCount_noWarning() throws Throwable {
    Compiler c = compileJs("/** @param {number} a */ function f(a) {} f(1);");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitAssign fall-through branch: compatible assignment -> no warning.
  @Test
  public void testAssign_validNumberReassignment_noWarning() throws Throwable {
    Compiler c = compileJs("var x = 1; x = 2;");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitAssign fall-through branch: incompatible assignment -> warning.
  @Test
  public void testAssign_typeMismatch_producesWarning() throws Throwable {
    Compiler c = compileJs("/** @type {number} */ var x; x = \"foo\";");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.ADD branch via visitBinaryOperator: numbers added -> no warning.
  @Test
  public void testAdd_numberPlusNumber_noWarning() throws Throwable {
    Compiler c = compileJs("var x = 1 + 2;");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.SUB branch via visitBinaryOperator: object operand -> expectNumber warning.
  @Test
  public void testSub_objectOperand_producesWarning() throws Throwable {
    Compiler c = compileJs("var x = ({}) - 1;");
    assertTrue(c.getWarningCount() > 0);
  }

  // Covers Token.INSTANCEOF branch: right side is not an object -> warning.
  @Test
  public void testInstanceof_nonObjectRightOperand_producesWarning() throws Throwable {
    Compiler c = compileJs("var x = (1 instanceof 2);");
    assertTrue(c.getWarningCount() > 0);
  }

  // Covers Token.IN branch: right side is not an object -> warning.
  @Test
  public void testIn_nonObjectRightOperand_producesWarning() throws Throwable {
    Compiler c = compileJs("var x = (\"a\" in 1);");
    assertTrue(c.getWarningCount() > 0);
  }

  // Covers Token.IN branch: valid string/object operands -> no warning.
  @Test
  public void testIn_validOperands_noWarning() throws Throwable {
    Compiler c = compileJs("var x = (\"a\" in {});");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers checkPropertyAccess branch for EnumType: missing element -> INEXISTENT_ENUM_ELEMENT.
  @Test
  public void testEnum_inexistentElement_producesWarning() throws Throwable {
    Compiler c = compileJs("/** @enum {number} */ var Color = {RED: 1}; var x = Color.BLUE;");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers checkPropertyAccess branch for EnumType: existing element -> no warning.
  @Test
  public void testEnum_existingElement_noWarning() throws Throwable {
    Compiler c = compileJs("/** @enum {number} */ var Color = {RED: 1}; var x = Color.RED;");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.BITNOT branch: object doesn't match int32 context -> BIT_OPERATION warning.
  @Test
  public void testBitnot_objectOperand_producesBitOperationWarning() throws Throwable {
    Compiler c = compileJs("var x = ~({});");
    assertTrue(c.getWarningCount() > 0);
  }

  // Covers Token.BITNOT branch: number matches int32 context -> no warning.
  @Test
  public void testBitnot_numberOperand_noWarning() throws Throwable {
    Compiler c = compileJs("var x = ~5;");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.LSH branch via visitBinaryOperator: left side object -> BIT_OPERATION warning.
  @Test
  public void testLeftShift_objectOperand_producesBitOperationWarning() throws Throwable {
    Compiler c = compileJs("var x = ({}) << 1;");
    assertTrue(c.getWarningCount() > 0);
  }

  // Covers visitInterfaceGetprop branch: non-empty interface method body -> INTERFACE_FUNCTION_NOT_EMPTY.
  @Test
  public void testInterface_nonEmptyMethodBody_producesWarning() throws Throwable {
    Compiler c = compileJs(
        "/** @interface */ function Foo() {} Foo.prototype.bar = function() { return 1; };");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitInterfaceGetprop branch: empty interface method body -> no warning.
  @Test
  public void testInterface_emptyMethodBody_noWarning() throws Throwable {
    Compiler c = compileJs(
        "/** @interface */ function Foo() {} Foo.prototype.bar = function() {};");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitInterfaceGetprop branch: non-function value assigned -> INVALID_INTERFACE_MEMBER_DECLARATION.
  @Test
  public void testInterface_propertyAssignedNonFunction_producesWarning() throws Throwable {
    Compiler c = compileJs("/** @interface */ function Foo() {} Foo.prototype.bar = 1;");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitFunction branch: constructor implementing a non-interface type -> BAD_IMPLEMENTED_TYPE.
  @Test
  public void testFunction_implementsNonInterface_producesWarning() throws Throwable {
    Compiler c = compileJs(
        "/** @constructor */ function Bar() {} "
        + "/** @constructor @implements {Bar} */ function Foo() {}");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitFunction branch: constructor extending an interface -> CONFLICTING_EXTENDED_TYPE.
  @Test
  public void testFunction_constructorExtendsInterface_producesWarning() throws Throwable {
    Compiler c = compileJs(
        "/** @interface */ function Foo() {} "
        + "/** @constructor @extends {Foo} */ function Bar() {}");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers checkDeclaredPropertyInheritance branch: @override with incompatible property type.
  @Test
  public void testAssign_overridePropertyTypeMismatch_producesWarning() throws Throwable {
    Compiler c = compileJs(
        "/** @constructor */ function A() {} A.prototype.foo = 1; "
        + "/** @constructor @extends {A} */ function B() {} "
        + "/** @override */ B.prototype.foo = \"bar\";");
    assertTrue(c.getWarningCount() > 0);
    assertEquals(0, c.getErrorCount());
  }

  // Covers visitGetElem branch: index type mismatch -> warning.
  @Test
  public void testGetElem_mismatchedIndexType_producesWarning() throws Throwable {
    Compiler c = compileJs("var a = [1, 2, 3]; var x = a[\"foo\"];");
    assertTrue(c.getWarningCount() > 0);
  }

  // Covers visitGetElem branch: valid numeric index -> no warning.
  @Test
  public void testGetElem_validIndexType_noWarning() throws Throwable {
    Compiler c = compileJs("var a = [1, 2, 3]; var x = a[0];");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.FOR branch (typeable=false) and numeric comparison/increment -> no warning.
  @Test
  public void testControlFlow_forLoop_noWarning() throws Throwable {
    Compiler c = compileJs("for (var i = 0; i < 10; i++) {}");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.WHILE branch (typeable=false) -> no warning.
  @Test
  public void testControlFlow_whileLoop_noWarning() throws Throwable {
    Compiler c = compileJs("var i = 0; while (i < 10) { i++; }");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.TRY/CATCH/THROW/NEW branches -> no warning for valid try/catch.
  @Test
  public void testControlFlow_tryCatch_noWarning() throws Throwable {
    Compiler c = compileJs("function f() { try { var y = 1; } catch (e) { } }");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.SWITCH/CASE/DEFAULT/BREAK branches -> no warning for valid switch.
  @Test
  public void testControlFlow_switchStatement_noWarning() throws Throwable {
    Compiler c = compileJs("var x = 1; switch (x) { case 1: break; default: break; }");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.TRUE/FALSE/NULL branches -> no warning for valid literals.
  @Test
  public void testLiterals_trueFalseNull_noWarning() throws Throwable {
    Compiler c = compileJs("var a = true; var b = false; var c = null;");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.COMMA/VOID/TYPEOF branches -> no warning.
  @Test
  public void testOperators_commaVoidTypeof_noWarning() throws Throwable {
    Compiler c = compileJs("var x = (1, 2); var a = void 0; var b = typeof a;");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Covers Token.REGEXP/ARRAYLIT branches -> no warning for valid literals.
  @Test
  public void testRegexAndArrayLiterals_noWarning() throws Throwable {
    Compiler c = compileJs("var r = /abc/; var arr = [1,2,3];");
    assertEquals(0, c.getWarningCount());
    assertEquals(0, c.getErrorCount());
  }

  // Sanity check that core diagnostic type constants used by the switch in visit() are initialized.
  @Test
  public void testStaticDiagnosticFields_notNull() throws Throwable {
    assertNotNull(TypeCheck.BAD_DELETE);
    assertNotNull(TypeCheck.NOT_A_CONSTRUCTOR);
    assertNotNull(TypeCheck.WRONG_ARGUMENT_COUNT);
    assertNotNull(TypeCheck.ALL_DIAGNOSTICS);
  }
}
