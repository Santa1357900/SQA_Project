package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests for {@link TypedScopeCreator}, exercised indirectly through the full
 * compiler pipeline (type checking), since TypedScopeCreator builds the typed
 * global/local scopes that the type checker relies on.
 */
public class TypedScopeCreatorClaudeTest {

  private Compiler compileCode(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(externs, input, options);
    return compiler;
  }

  private boolean containsMessage(Compiler compiler, String keyword) {
    JSError[] errors = compiler.getErrors();
    for (int i = 0; i < errors.length; i++) {
      if (errors[i].toString().contains(keyword)) {
        return true;
      }
    }
    JSError[] warnings = compiler.getWarnings();
    for (int i = 0; i < warnings.length; i++) {
      if (warnings[i].toString().contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  // defineName/defineVar: @type annotation matches initializer -> no diagnostics.
  @Test
  public void testDefineVar_typeMatchesInitializer_noDiagnostics() throws Throwable {
    Compiler compiler = compileCode("/** @type {number} */ var x = 1;");
    assertEquals(0, compiler.getErrors().length);
    assertEquals(0, compiler.getWarnings().length);
  }

  // defineName/getDeclaredType: @type annotation conflicts with initializer -> type mismatch reported.
  @Test
  public void testDefineVar_typeMismatchesInitializer_reportsDiagnostic() throws Throwable {
    Compiler compiler = compileCode("/** @type {number} */ var x = 'hello';");
    assertTrue(compiler.getErrors().length + compiler.getWarnings().length >= 1);
  }

  // defineVar: multiple names declared with JSDoc on the VAR node -> MULTIPLE_VAR_DEF warning.
  @Test
  public void testDefineVar_multipleNamesWithJSDoc_reportsWarning() throws Throwable {
    Compiler compiler = compileCode("/** @type {number} */ var a = 1, b = 2;");
    assertTrue(compiler.getWarnings().length >= 1);
  }

  // defineVar: multiple names without JSDoc -> no MULTIPLE_VAR_DEF warning.
  @Test
  public void testDefineVar_multipleNamesWithoutJSDoc_noWarning() throws Throwable {
    Compiler compiler = compileCode("var a = 1, b = 2;");
    assertEquals(0, compiler.getErrors().length);
    assertEquals(0, compiler.getWarnings().length);
  }

  // defineSlot: constructor declared without an initializer -> CTOR_INITIALIZER warning.
  @Test
  public void testDefineSlot_constructorWithoutInitializer_reportsCtorInitializer() throws Throwable {
    Compiler compiler = compileCode("/** @constructor */ var Foo;");
    assertTrue(containsMessage(compiler, "Constructor Foo must be initialized at declaration"));
  }

  // defineSlot: constructor declared with an initializer -> no CTOR_INITIALIZER warning.
  @Test
  public void testDefineSlot_constructorWithInitializer_noCtorInitializerWarning() throws Throwable {
    Compiler compiler = compileCode("/** @constructor */ var Foo = function() {};");
    assertFalse(containsMessage(compiler, "must be initialized at declaration"));
  }

  // defineSlot: interface declared without an initializer -> IFACE_INITIALIZER warning (distinct from ctor message).
  @Test
  public void testDefineSlot_interfaceWithoutInitializer_reportsIfaceInitializer() throws Throwable {
    Compiler compiler = compileCode("/** @interface */ var Foo;");
    assertTrue(containsMessage(compiler, "Interface Foo must be initialized at declaration"));
  }

  // defineSlot: interface declared with an initializer -> no IFACE_INITIALIZER warning.
  @Test
  public void testDefineSlot_interfaceWithInitializer_noIfaceInitializerWarning() throws Throwable {
    Compiler compiler = compileCode("/** @interface */ var Foo = function() {};");
    assertFalse(containsMessage(compiler, "Interface Foo must be initialized"));
  }

  // defineSlot: enum initialized with an object literal -> no ENUM_INITIALIZER warning.
  @Test
  public void testDefineSlot_enumWithObjectLiteralInitializer_noEnumInitializerWarning() throws Throwable {
    Compiler compiler = compileCode("/** @enum {number} */ var Color = {RED: 1, GREEN: 2};");
    assertFalse(containsMessage(compiler, "enum initializer must be an object literal"));
  }

  // defineSlot: enum initialized with a non-object, non-enum value -> ENUM_INITIALIZER warning.
  @Test
  public void testDefineSlot_enumWithInvalidInitializer_reportsEnumInitializer() throws Throwable {
    Compiler compiler = compileCode("/** @enum {number} */ var Color = 5;");
    assertTrue(containsMessage(compiler,
        "enum initializer must be an object literal or an enum"));
  }

  // createEnumTypeFromNodes: enum aliased via a qualified name reference -> valid initializer, no warning.
  @Test
  public void testDefineSlot_enumAliasedByQualifiedName_noEnumInitializerWarning() throws Throwable {
    Compiler compiler = compileCode(
        "/** @enum {number} */ var Color = {RED: 1};\n"
        + "/** @enum {number} */ var Color2 = Color;");
    assertFalse(containsMessage(compiler, "enum initializer must be an object literal"));
  }

  // createEnumTypeFromNodes: duplicate keys in an enum object literal -> at least one diagnostic reported.
  @Test
  public void testCreateEnumTypeFromNodes_duplicateKey_reportsDiagnostic() throws Throwable {
    Compiler compiler = compileCode("/** @enum {number} */ var Color = {RED: 1, RED: 2};");
    assertTrue(compiler.getErrors().length + compiler.getWarnings().length >= 1);
  }

  // defineObjectLiteral @lends: lends name not declared in scope -> UNKNOWN_LENDS warning.
  @Test
  public void testDefineObjectLiteral_lendsOnUndeclaredVariable_reportsUnknownLends() throws Throwable {
    Compiler compiler = compileCode("/** @lends {UndefinedVar} */ ({x: 1});");
    assertTrue(containsMessage(compiler, "not declared before @lends annotation"));
  }

  // defineObjectLiteral @lends: lends target is not an object type -> LENDS_ON_NON_OBJECT warning.
  @Test
  public void testDefineObjectLiteral_lendsOnNonObjectVariable_reportsLendsOnNonObject() throws Throwable {
    Compiler compiler = compileCode(
        "/** @type {number} */ var N = 1;\n"
        + "/** @lends {N} */ ({x: 1});");
    assertTrue(containsMessage(compiler, "May only lend properties to object types"));
  }

  // defineObjectLiteral @lends: lends target is a valid object -> no lends-related warning.
  @Test
  public void testDefineObjectLiteral_lendsOnObjectVariable_noLendsWarning() throws Throwable {
    Compiler compiler = compileCode(
        "/** @type {Object} */ var O = {};\n"
        + "/** @lends {O} */ ({x: 1});");
    assertFalse(containsMessage(compiler, "not declared before @lends annotation"));
    assertFalse(containsMessage(compiler, "May only lend properties to object types"));
  }

  // LocalScopeBuilder.declareArguments: @param type from JSDoc is enforced on usage inside the function.
  @Test
  public void testDeclareArguments_paramTypeFromJSDoc_enforcesTypeMismatch() throws Throwable {
    Compiler compiler = compileCode(
        "/** @param {number} a */ function f(a) { var /** string */ s = a; }");
    assertTrue(compiler.getErrors().length + compiler.getWarnings().length >= 1);
  }

  // createFunctionTypeFromNodes: @return type from JSDoc is enforced against actual return value.
  @Test
  public void testCreateFunctionTypeFromNodes_returnTypeMismatch_reportsDiagnostic() throws Throwable {
    Compiler compiler = compileCode("/** @return {number} */ function f() { return 'str'; }");
    assertTrue(compiler.getErrors().length + compiler.getWarnings().length >= 1);
  }

  // createFunctionTypeFromNodes: @return type matches the actual return value -> no diagnostics.
  @Test
  public void testCreateFunctionTypeFromNodes_returnTypeMatches_noDiagnostics() throws Throwable {
    Compiler compiler = compileCode("/** @return {number} */ function f() { return 1; }");
    assertEquals(0, compiler.getErrors().length);
    assertEquals(0, compiler.getWarnings().length);
  }

  // LocalScopeBuilder.handleFunctionInputs: bleeding function name is visible inside its own body.
  @Test
  public void testHandleFunctionInputs_bleedingFunctionName_accessibleInsideItself() throws Throwable {
    Compiler compiler = compileCode("var f = function foo() { return foo; };");
    assertEquals(0, compiler.getErrors().length);
  }

  // defineCatch: catch parameter has an inferred (unknown) type, compatible with any declared type.
  @Test
  public void testDefineCatch_catchParamInferredType_noDiagnostics() throws Throwable {
    Compiler compiler = compileCode("try { throw 1; } catch (e) { var x = e; }");
    assertEquals(0, compiler.getErrors().length);
    assertEquals(0, compiler.getWarnings().length);
  }

  // resolveStubDeclarations: a stubbed prototype property with no JSDoc resolves to unknown, no error.
  @Test
  public void testResolveStubDeclarations_prototypeStubProperty_noError() throws Throwable {
    Compiler compiler = compileCode("/** @constructor */ function Foo() {}\nFoo.prototype.bar;");
    assertEquals(0, compiler.getErrors().length);
  }

  // resolveStubDeclarations: a stubbed qualified-name property with no JSDoc resolves without error.
  @Test
  public void testResolveStubDeclarations_qualifiedNameStub_noError() throws Throwable {
    Compiler compiler = compileCode("var ns = {};\nns.foo;");
    assertEquals(0, compiler.getErrors().length);
  }

  // getDeclaredType @const idiom: "var x = x || {}" is a documented special case, must not error.
  @Test
  public void testGetDeclaredType_constOrIdiom_noDiagnostics() throws Throwable {
    Compiler compiler = compileCode("/** @const */ var x = x || {};");
    assertEquals(0, compiler.getErrors().length);
  }

  // shouldTraverse: hoisted function declarations are available before their textual position.
  @Test
  public void testShouldTraverse_hoistedFunctionDeclaration_availableBeforeCall() throws Throwable {
    Compiler compiler = compileCode("f(); function f() {}");
    assertEquals(0, compiler.getErrors().length);
  }

  // processObjectLitProperties: a key with @type annotation declares the property's type on the owner.
  @Test
  public void testProcessObjectLitProperties_typedKey_enforcesTypeOnLaterAssignment() throws Throwable {
    Compiler compiler = compileCode(
        "var obj = { /** @type {number} */ foo: 1 };\nobj.foo = 'str';");
    assertTrue(compiler.getErrors().length + compiler.getWarnings().length >= 1);
  }

  // maybeDeclareQualifiedName: @type annotation on a qualified-name assignment declares its type.
  @Test
  public void testMaybeDeclareQualifiedName_typedProperty_enforcesTypeOnReassignment() throws Throwable {
    Compiler compiler = compileCode(
        "var ns = {};\n/** @type {number} */ ns.x = 1;\nns.x = 'str';");
    assertTrue(compiler.getErrors().length + compiler.getWarnings().length >= 1);
  }

  // maybeDeclareQualifiedName precedence rule #2: a function-literal ASSIGN is a declaration only
  // the first time; the second assignment is treated as inferred and must not silently succeed
  // when its signature conflicts with the already-declared one.
  @Test
  public void testMaybeDeclareQualifiedName_secondFunctionAssignment_isNotRedeclared() throws Throwable {
    Compiler compiler = compileCode(
        "var ns = {};\n"
        + "ns.foo = function() { return 1; };\n"
        + "ns.foo = function() { return 'str'; };");
    assertTrue(compiler.getErrors().length + compiler.getWarnings().length >= 1);
  }
}
