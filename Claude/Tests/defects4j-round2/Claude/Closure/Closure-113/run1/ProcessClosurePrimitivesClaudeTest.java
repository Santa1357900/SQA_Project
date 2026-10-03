package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ProcessClosurePrimitivesClaudeTest {

  private Compiler lastCompiler;

  private Result compileCode(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.closurePass = true;
    options.setPrettyPrint(true);
    List<SourceFile> externs = new ArrayList<SourceFile>();
    externs.add(SourceFile.fromCode("externs.js", ""));
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    Result result = compiler.compile(externs, inputs, options);
    this.lastCompiler = compiler;
    return result;
  }

  // Covers constructor field initialization and getExportedVariableNames() initial empty set
  @Test
  public void testConstructor_initialState_exportedVariableNamesEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    ProcessClosurePrimitives pass =
        new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR);
    assertTrue(pass.getExportedVariableNames().isEmpty());
  }

  // Covers goog.provide with simple namespace: creates "var foo = {};" declaration
  @Test
  public void testProcess_simpleProvide_declaresNamespaceVar() throws Throwable {
    Result result = compileCode("goog.provide('foo');");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("goog.provide"));
    assertTrue(source.contains("var foo = {}"));
  }

  // Covers goog.provide with dotted namespace: creates prefix var and assignment expr
  @Test
  public void testProcess_dottedProvide_declaresPrefixAndAssignment() throws Throwable {
    Result result = compileCode("goog.provide('foo.bar');");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertTrue(source.contains("var foo = {}"));
    assertTrue(source.contains("foo.bar = {}"));
  }

  // Covers DUPLICATE_NAMESPACE_ERROR branch when same namespace provided twice
  @Test
  public void testProcess_duplicateProvide_reportsDuplicateNamespaceErrorAndFails() throws Throwable {
    Result result = compileCode("goog.provide('foo'); goog.provide('foo');");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers INVALID_PROVIDE_ERROR branch for invalid identifier segment
  @Test
  public void testProcess_provideInvalidIdentifier_reportsInvalidProvideErrorAndFails() throws Throwable {
    Result result = compileCode("goog.provide('123bad');");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers NULL_ARGUMENT_ERROR branch when goog.provide called with no args
  @Test
  public void testProcess_provideNoArgument_reportsNullArgumentErrorAndFails() throws Throwable {
    Result result = compileCode("goog.provide();");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers INVALID_ARGUMENT_ERROR branch when argument isn't a string literal
  @Test
  public void testProcess_provideNonStringArgument_reportsInvalidArgumentErrorAndFails() throws Throwable {
    Result result = compileCode("goog.provide(123);");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers TOO_MANY_ARGUMENTS_ERROR branch for provide with extra argument
  @Test
  public void testProcess_provideTooManyArguments_reportsTooManyArgumentsErrorAndFails() throws Throwable {
    Result result = compileCode("goog.provide('foo', 'bar');");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers goog.require matched by a provide: require call is detached from AST
  @Test
  public void testProcess_requireWithMatchingProvide_removesRequireCall() throws Throwable {
    Result result = compileCode("goog.provide('foo'); goog.require('foo');");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("goog.require"));
  }

  // Covers goog.require with no matching provide: call is preserved (not detached) per contract
  @Test
  public void testProcess_requireWithoutProvide_keepsRequireCallInOutput() throws Throwable {
    compileCode("goog.require('foo');");
    String source = lastCompiler.toSource();
    assertTrue(source.contains("goog.require"));
  }

  // Covers NULL_ARGUMENT_ERROR branch for goog.require with no args
  @Test
  public void testProcess_requireNoArgument_reportsNullArgumentErrorAndFails() throws Throwable {
    Result result = compileCode("goog.require();");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers INVALID_ARGUMENT_ERROR branch for goog.require with non-string arg
  @Test
  public void testProcess_requireNonStringArgument_reportsInvalidArgumentErrorAndFails() throws Throwable {
    Result result = compileCode("goog.require(123);");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers goog.exportSymbol branch: call left untouched in AST (no transform)
  @Test
  public void testProcess_exportSymbol_callLeftUnchangedInOutput() throws Throwable {
    Result result = compileCode("var foo = {}; goog.exportSymbol('foo', foo);");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertTrue(source.contains("goog.exportSymbol"));
  }

  // Covers goog.addDependency branch: call replaced with numeric literal, removed from source
  @Test
  public void testProcess_addDependency_removesCallFromOutput() throws Throwable {
    Result result = compileCode("goog.addDependency('a.js', [], []);");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("addDependency"));
  }

  // Covers goog.define with proper @define annotation: call replaced with declaration
  @Test
  public void testProcess_defineWithAnnotation_removesDefineCallAndSucceeds() throws Throwable {
    Result result = compileCode("/** @define {boolean} */\ngoog.define('FOO', true);");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("goog.define"));
  }

  // Covers MISSING_DEFINE_ANNOTATION branch when @define jsdoc is absent
  @Test
  public void testProcess_defineWithoutAnnotation_reportsMissingDefineAnnotationAndFails() throws Throwable {
    Result result = compileCode("goog.define('FOO', true);");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers INVALID_DEFINE_NAME_ERROR branch for an invalid identifier name
  @Test
  public void testProcess_defineInvalidName_reportsInvalidDefineNameErrorAndFails() throws Throwable {
    Result result = compileCode("goog.define('123bad', true);");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers NULL_ARGUMENT_ERROR branch for goog.define with no arguments
  @Test
  public void testProcess_defineNoArguments_reportsNullArgumentErrorAndFails() throws Throwable {
    Result result = compileCode("goog.define();");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers TOO_MANY_ARGUMENTS_ERROR branch for goog.define with extra argument
  @Test
  public void testProcess_defineTooManyArguments_reportsTooManyArgumentsErrorAndFails() throws Throwable {
    Result result = compileCode(
        "/** @define {boolean} */\ngoog.define('FOO', true, 'extra');");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers goog.setCssNameMapping valid object literal: call removed, no diagnostics
  @Test
  public void testProcess_setCssNameMappingValid_removesCallAndSucceeds() throws Throwable {
    Result result = compileCode("goog.setCssNameMapping({'foo': 'bar'});");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("setCssNameMapping"));
  }

  // Covers NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR branch for non-string value
  @Test
  public void testProcess_setCssNameMappingNonStringValue_reportsNonStringErrorAndFails() throws Throwable {
    Result result = compileCode("goog.setCssNameMapping({'foo': 1});");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers INVALID_STYLE_ERROR branch for an unknown css renaming map style
  @Test
  public void testProcess_setCssNameMappingInvalidStyle_reportsInvalidStyleErrorAndFails() throws Throwable {
    Result result = compileCode("goog.setCssNameMapping({'foo': 'bar'}, 'NOT_A_STYLE');");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers EXPECTED_OBJECTLIT_ERROR branch when first arg isn't an object literal
  @Test
  public void testProcess_setCssNameMappingNonObjectLitArg_reportsExpectedObjectLitErrorAndFails() throws Throwable {
    Result result = compileCode("goog.setCssNameMapping('foo');");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers NULL_ARGUMENT_ERROR branch when setCssNameMapping called with no args
  @Test
  public void testProcess_setCssNameMappingNoArgument_reportsNullArgumentErrorAndFails() throws Throwable {
    Result result = compileCode("goog.setCssNameMapping();");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers EXPECTED_STRING_ERROR branch for a non-string second argument
  @Test
  public void testProcess_setCssNameMappingNonStringSecondArg_reportsExpectedStringErrorAndFails() throws Throwable {
    Result result = compileCode("goog.setCssNameMapping({'foo':'bar'}, 123);");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers TOO_MANY_ARGUMENTS_ERROR branch for setCssNameMapping with 3 arguments
  @Test
  public void testProcess_setCssNameMappingTooManyArguments_reportsTooManyArgumentsErrorAndFails() throws Throwable {
    Result result = compileCode(
        "goog.setCssNameMapping({'foo':'bar'}, 'BY_PART', 'extra');");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers BY_PART style validation branch: key containing '-' triggers a warning
  @Test
  public void testProcess_setCssNameMappingByPartWithDash_reportsInvalidCssRenamingMapWarning() throws Throwable {
    Result result = compileCode("goog.setCssNameMapping({'foo-bar': 'baz'});");
    assertTrue(result.warnings.length >= 1);
  }

  // Covers BY_WHOLE style consistency check branch: inconsistent mapping triggers warning
  @Test
  public void testProcess_setCssNameMappingByWholeInconsistent_reportsInvalidCssRenamingMapWarning() throws Throwable {
    Result result = compileCode(
        "goog.setCssNameMapping({'a': 'A', 'b': 'B', 'a-b': 'X'}, 'BY_WHOLE');");
    assertTrue(result.warnings.length >= 1);
  }

  // Covers goog.base rewrite in a constructor: replaced with BaseClass.call(this)
  @Test
  public void testProcess_baseCallInConstructor_rewritesToBaseClassCall() throws Throwable {
    Result result = compileCode(
        "function Foo() { goog.base(this); }\ngoog.inherits(Foo, Bar);");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("goog.base"));
    assertTrue(source.contains("Bar.call"));
  }

  // Covers goog.base rewrite in a prototype method: replaced with superClass_ call
  @Test
  public void testProcess_baseCallInPrototypeMethod_rewritesToSuperClassCall() throws Throwable {
    Result result = compileCode(
        "function Foo() {}\ngoog.inherits(Foo, Bar);\n"
        + "Foo.prototype.bar = function() { goog.base(this, 'bar'); };");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("goog.base"));
    assertTrue(source.contains("superClass_"));
  }

  // Covers BASE_CLASS_ERROR branch when first argument to goog.base isn't 'this'
  @Test
  public void testProcess_baseCallFirstArgNotThis_reportsBaseClassErrorAndFails() throws Throwable {
    Result result = compileCode("function Foo() { goog.base(foo); }");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers BASE_CLASS_ERROR branch when goog.base is called with no arguments
  @Test
  public void testProcess_baseCallNoArguments_reportsBaseClassErrorAndFails() throws Throwable {
    Result result = compileCode("function Foo() { goog.base(); }");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers BASE_CLASS_ERROR branch when goog.base is called outside any function
  @Test
  public void testProcess_baseCallOutsideFunction_reportsBaseClassErrorAndFails() throws Throwable {
    Result result = compileCode("goog.base(this);");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers GETPROP branch: referencing goog.base without calling it directly is an error
  @Test
  public void testProcess_googBaseReferencedNotCalled_reportsBaseClassErrorAndFails() throws Throwable {
    Result result = compileCode("var x = goog.base;");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers FUNCTION_NAMESPACE_ERROR branch: a provided name later declared as a function
  @Test
  public void testProcess_provideThenFunctionDeclaration_reportsFunctionNamespaceErrorAndFails() throws Throwable {
    Result result = compileCode("goog.provide('foo');\nfunction foo() {}");
    assertFalse(result.success);
    assertTrue(result.errors.length >= 1);
  }

  // Covers candidate-definition replace path: plain assignment is converted to var decl
  @Test
  public void testProcess_provideWithMatchingAssignment_convertsToVarDeclaration() throws Throwable {
    Result result = compileCode("goog.provide('foo');\nfoo = {};");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("goog.provide"));
    assertTrue(source.contains("var foo = {}"));
  }

  // Covers candidate-definition replace path: dotted assignment stays an expression statement
  @Test
  public void testProcess_provideWithMatchingDottedAssignment_keepsAssignmentExpression() throws Throwable {
    Result result = compileCode("goog.provide('foo.bar');\nfoo.bar = {};");
    String source = lastCompiler.toSource();
    assertTrue(result.success);
    assertFalse(source.contains("goog.provide"));
    assertTrue(source.contains("foo.bar = {}"));
  }
}
