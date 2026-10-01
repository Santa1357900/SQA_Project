package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Tests for FunctionTypeBuilder. These tests drive the builder indirectly
 * through a full compile (type-checking enabled) because the builder itself
 * is package-private and depends on live Scope/JSTypeRegistry/Compiler
 * objects that are normally only produced by TypedScopeCreator during a
 * real compilation pass.
 */
public class FunctionTypeBuilderClaudeTest {

  // Compiles a single JS source string with type checking enabled.
  private Result compile(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    List<SourceFile> externs = Lists.newArrayList();
    List<SourceFile> inputs = Lists.newArrayList();
    inputs.add(SourceFile.fromCode("test.js", js));
    return compiler.compile(externs, inputs, options);
  }

  // Returns true if any reported error/warning has the given diagnostic type.
  private boolean hasDiagnostic(Result result, DiagnosticType type) {
    return countDiagnostic(result, type) > 0;
  }

  // Counts how many times the given diagnostic type was reported.
  private int countDiagnostic(Result result, DiagnosticType type) {
    int count = 0;
    JSError[] errors = result.errors;
    for (int i = 0; i < errors.length; i++) {
      if (type == errors[i].getType()) {
        count++;
      }
    }
    JSError[] warnings = result.warnings;
    for (int i = 0; i < warnings.length; i++) {
      if (type == warnings[i].getType()) {
        count++;
      }
    }
    return count;
  }

  // Branch: inferInheritance - @extends without @constructor/@interface.
  @Test
  public void testInferInheritance_extendsWithoutConstructor_warnsExtendsWithoutTypedef()
      throws Throwable {
    String js = "/** @extends {Object} */\nfunction Foo() {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.EXTENDS_WITHOUT_TYPEDEF));
  }

  // Branch: inferInheritance - valid @constructor + @extends {Object}, no warning.
  @Test
  public void testInferInheritance_extendsWithConstructorAndObjectType_noWarning()
      throws Throwable {
    String js = "/**\n * @constructor\n * @extends {Object}\n */\nfunction Foo() {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.EXTENDS_WITHOUT_TYPEDEF));
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.EXTENDS_NON_OBJECT));
  }

  // Branch: ExtendedTypeValidator - @extends a non-object type.
  @Test
  public void testInferInheritance_extendsNonObjectType_warnsExtendsNonObject()
      throws Throwable {
    String js = "/**\n * @constructor\n * @extends {number}\n */\nfunction Foo() {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.EXTENDS_NON_OBJECT));
  }

  // Branch: inferInheritance - @implements without @constructor/@interface.
  @Test
  public void testInferInheritance_implementsWithoutConstructor_warns()
      throws Throwable {
    String js = "/** @implements {Object} */\nfunction Bar() {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.IMPLEMENTS_WITHOUT_CONSTRUCTOR));
  }

  // Branch: inferInheritance - @implements with @constructor, no warning.
  @Test
  public void testInferInheritance_implementsWithConstructor_noWarning()
      throws Throwable {
    String js = "/** @interface */\nfunction Base() {}\n"
        + "/**\n * @constructor\n * @implements {Base}\n */\nfunction Foo() {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.IMPLEMENTS_WITHOUT_CONSTRUCTOR));
  }

  // Branch: addParameter - var_args not last parameter.
  @Test
  public void testAddParameter_varArgsNotLast_warnsVarArgsMustBeLast() throws Throwable {
    String js = "/**\n * @param {...number} a\n * @param {number} b\n */\n"
        + "function f(a, b) {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.VAR_ARGS_MUST_BE_LAST));
  }

  // Branch: addParameter - var_args correctly last, no warning.
  @Test
  public void testAddParameter_varArgsLast_noWarning() throws Throwable {
    String js = "/**\n * @param {number} a\n * @param {...number} b\n */\n"
        + "function f(a, b) {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.VAR_ARGS_MUST_BE_LAST));
  }

  // Branch: addParameter - required param after optional param.
  @Test
  public void testAddParameter_optionalArgNotAtEnd_warnsOptionalArgAtEnd() throws Throwable {
    String js = "/**\n * @param {number=} a\n * @param {number} b\n */\n"
        + "function f(a, b) {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.OPTIONAL_ARG_AT_END));
  }

  // Branch: addParameter - optional param correctly at end, no warning.
  @Test
  public void testAddParameter_optionalArgAtEnd_noWarning() throws Throwable {
    String js = "/**\n * @param {number} a\n * @param {number=} b\n */\n"
        + "function f(a, b) {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.OPTIONAL_ARG_AT_END));
  }

  // Branch: addParameter latch - only one warning even with multiple trailing params.
  @Test
  public void testAddParameter_multipleTrailingParams_warnsOnlyOnce() throws Throwable {
    String js = "/**\n * @param {...number} a\n * @param {number} b\n"
        + " * @param {number} c\n */\nfunction f(a, b, c) {}";
    Result result = compile(js);
    assertEquals(1, countDiagnostic(result, FunctionTypeBuilder.VAR_ARGS_MUST_BE_LAST));
  }

  // Branch: inferParameterTypes - jsdoc param name not present in function params.
  @Test
  public void testInferParameterTypes_paramNotInFunction_warnsInexistantParam()
      throws Throwable {
    String js = "/** @param {number} c */\nfunction f(a, b) {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.INEXISTANT_PARAM));
  }

  // Branch: inferParameterTypes - jsdoc param name matches, no warning.
  @Test
  public void testInferParameterTypes_paramMatchesFunction_noWarning() throws Throwable {
    String js = "/** @param {number} a */\nfunction f(a) {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.INEXISTANT_PARAM));
  }

  // Branch: ThisTypeValidator - @this type not an object (not subtype of Object).
  @Test
  public void testInferThisType_nonObjectType_warnsThisTypeNonObject() throws Throwable {
    String js = "/** @this {number} */\nfunction f() {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.THIS_TYPE_NON_OBJECT));
  }

  // Branch: ThisTypeValidator - @this type is an object type, no warning.
  @Test
  public void testInferThisType_objectType_noWarning() throws Throwable {
    String js = "/** @this {Object} */\nfunction f() {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.THIS_TYPE_NON_OBJECT));
  }

  // Branch: inferParameterTypes - same template type used by more than one param.
  @Test
  public void testTemplate_duplicatedUsage_warnsTemplateTypeDuplicated() throws Throwable {
    String js = "/**\n * @template T\n * @param {T} a\n * @param {T} b\n */\n"
        + "function f(a, b) {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.TEMPLATE_TYPE_DUPLICATED));
  }

  // Branch: inferParameterTypes - @template declared but never used as a param type.
  @Test
  public void testTemplate_notUsedInParams_warnsTemplateTypeExpected() throws Throwable {
    String js = "/**\n * @template T\n * @param {number} a\n */\nfunction f(a) {}";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.TEMPLATE_TYPE_EXPECTED));
  }

  // Branch: inferReturnType - return type is the bare template type.
  @Test
  public void testInferReturnType_bareTemplateReturn_warnsTemplateTypeExpected()
      throws Throwable {
    String js = "/**\n * @template T\n * @param {T} a\n * @return {T}\n */\n"
        + "function f(a) { return a; }";
    Result result = compile(js);
    assertTrue(hasDiagnostic(result, FunctionTypeBuilder.TEMPLATE_TYPE_EXPECTED));
  }

  // Branch: template type used exactly once as param, non-template return type: no warnings.
  @Test
  public void testTemplate_validUsage_noWarnings() throws Throwable {
    String js = "/**\n * @template T\n * @param {T} a\n * @return {boolean}\n */\n"
        + "function f(a) { return true; }";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.TEMPLATE_TYPE_DUPLICATED));
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.TEMPLATE_TYPE_EXPECTED));
  }

  // Branch: buildAndRegister - plain function with explicit types compiles successfully.
  @Test
  public void testBuildAndRegister_plainFunction_compilesSuccessfully() throws Throwable {
    String js = "/**\n * @param {number} a\n * @return {number}\n */\n"
        + "function f(a) { return a; }";
    Result result = compile(js);
    assertTrue(result.success);
  }

  // Branch: buildAndRegister - isConstructor path, new-ing the type succeeds.
  @Test
  public void testBuildAndRegister_constructor_compilesSuccessfully() throws Throwable {
    String js = "/** @constructor */\nfunction Foo() {}\nvar f = new Foo();";
    Result result = compile(js);
    assertTrue(result.success);
  }

  // Branch: buildAndRegister - isInterface path compiles successfully.
  @Test
  public void testBuildAndRegister_interface_compilesSuccessfully() throws Throwable {
    String js = "/** @interface */\nfunction Foo() {}";
    Result result = compile(js);
    assertTrue(result.success);
  }

  // Branch: inferInheritance - interface extending another interface, no extends warnings.
  @Test
  public void testInferInheritance_interfaceExtendsInterface_noExtendsWarnings()
      throws Throwable {
    String js = "/** @interface */\nfunction Base() {}\n"
        + "/**\n * @interface\n * @extends {Base}\n */\nfunction Sub() {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.EXTENDS_WITHOUT_TYPEDEF));
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.EXTENDS_NON_OBJECT));
  }

  // Branch: inferInheritance - constructor implementing a declared interface, no warning,
  // and overall compile succeeds (exercises implementedInterfaces list population).
  @Test
  public void testInferInheritance_constructorImplementsInterface_success()
      throws Throwable {
    String js = "/** @interface */\nfunction Base() {}\n"
        + "/**\n * @constructor\n * @implements {Base}\n */\nfunction Foo() {}";
    Result result = compile(js);
    assertTrue(result.success);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.IMPLEMENTS_WITHOUT_CONSTRUCTOR));
  }

  // Branch: inferParameterTypes with zero jsdoc params and zero function params: no
  // INEXISTANT_PARAM warning should ever fire since there is nothing to mismatch.
  @Test
  public void testInferParameterTypes_zeroParams_noInexistantParamWarning()
      throws Throwable {
    String js = "function f() {}";
    Result result = compile(js);
    assertFalse(hasDiagnostic(result, FunctionTypeBuilder.INEXISTANT_PARAM));
  }

  // Branch: inferInheritance - plain function (no @constructor/@interface) with
  // @implements AND no @extends still only reports the implements warning once.
  @Test
  public void testInferInheritance_implementsWithoutConstructor_warnsExactlyOnce()
      throws Throwable {
    String js = "/** @implements {Object} */\nfunction Bar() {}";
    Result result = compile(js);
    assertEquals(1,
        countDiagnostic(result, FunctionTypeBuilder.IMPLEMENTS_WITHOUT_CONSTRUCTOR));
  }
}
