package com.google.javascript.jscomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class TypedScopeCreatorClaudeTest {

  private Result compileCode(String js) throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    return compiler.compile(externs, input, options);
  }

  private Scope buildInitialScope() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    Node root = IR.block();
    return scopeCreator.createInitialScope(root);
  }

  // Covers declareNativeFunctionType straight-line declarations of core constructor/prototype types.
  @Test
  public void testCreateInitialScope_nativeTypesDeclared_varsNotNull() throws Throwable {
    Scope s = buildInitialScope();
    assertNotNull(s.getVar("Object"));
    assertNotNull(s.getVar("Object.prototype"));
    assertNotNull(s.getVar("Array"));
    assertNotNull(s.getVar("Function"));
    assertNotNull(s.getVar("Date"));
  }

  // Covers declareNativeValueType("undefined", VOID_TYPE) and ActiveXObject (NO_OBJECT_TYPE) declarations.
  @Test
  public void testCreateInitialScope_undefinedAndActiveXObjectDeclared() throws Throwable {
    Scope s = buildInitialScope();
    assertNotNull(s.getVar("undefined"));
    assertNotNull(s.getVar("ActiveXObject"));
  }

  // Covers "new Scope(root, compiler)" producing a global scope.
  @Test
  public void testCreateInitialScope_returnsGlobalScope() throws Throwable {
    Scope s = buildInitialScope();
    assertTrue(s.isGlobal());
  }

  // defineVar: multiple VAR children with JSDocInfo present -> MULTIPLE_VAR_DEF reported (see code comment).
  @Test
  public void testDefineVar_multipleChildrenWithJsDoc_reportsDiagnostic() throws Throwable {
    Result result = compileCode("/** @type {number} */ var x = 1, y = 2;");
    assertTrue(result.errors.length + result.warnings.length >= 1);
  }

  // defineVar: multiple VAR children without JSDocInfo -> no MULTIPLE_VAR_DEF diagnostic.
  @Test
  public void testDefineVar_multipleChildrenWithoutJsDoc_noDiagnostic() throws Throwable {
    Result result = compileCode("var x = 1, y = 2;");
    assertEquals(0, result.warnings.length);
    assertEquals(0, result.errors.length);
  }

  // defineSlot: @constructor var with no real function initializer -> CTOR_INITIALIZER warning.
  @Test
  public void testDefineSlot_constructorWithoutInitializer_reportsCtorInitializerWarning() throws Throwable {
    Result result = compileCode("/** @constructor */\nvar Foo;");
    assertTrue(result.warnings.length >= 1);
  }

  // defineSlot: @constructor declared as an actual function literal -> no CTOR_INITIALIZER warning.
  @Test
  public void testDefineSlot_constructorAsFunctionLiteral_noWarning() throws Throwable {
    Result result = compileCode("/** @constructor */\nfunction Foo() {}");
    assertEquals(0, result.warnings.length);
  }

  // defineSlot: @interface var with no real function initializer -> IFACE_INITIALIZER warning.
  @Test
  public void testDefineSlot_interfaceWithoutInitializer_reportsIfaceInitializerWarning() throws Throwable {
    Result result = compileCode("/** @interface */\nvar Foo;");
    assertTrue(result.warnings.length >= 1);
  }

  // defineSlot: @interface declared as an actual function literal -> no IFACE_INITIALIZER warning.
  @Test
  public void testDefineSlot_interfaceAsFunctionLiteral_noWarning() throws Throwable {
    Result result = compileCode("/** @interface */\nfunction Foo() {}");
    assertEquals(0, result.warnings.length);
  }

  // defineSlot: EnumType whose initial value is a number literal (not object lit/qualified name) -> ENUM_INITIALIZER.
  @Test
  public void testDefineSlot_enumInitializedToNumberLiteral_reportsEnumInitializerWarning() throws Throwable {
    Result result = compileCode("/** @enum {number} */\nvar Foo = 5;");
    assertTrue(result.warnings.length >= 1);
  }

  // defineSlot: EnumType initialized with an object literal -> valid initializer, no warning.
  @Test
  public void testDefineSlot_enumInitializedToObjectLiteral_noWarning() throws Throwable {
    Result result = compileCode("/** @enum {number} */\nvar Foo = {A: 1, B: 2};");
    assertEquals(0, result.warnings.length);
  }

  // defineSlot: EnumType aliased via a simple qualified-name reference -> valid initializer, no warning.
  @Test
  public void testDefineSlot_enumAliasedByQualifiedName_noWarning() throws Throwable {
    String js = "/** @enum {number} */\nvar Foo = {A: 1};\n"
        + "/** @enum {number} */\nvar Bar = Foo;";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // createEnumTypeFromNodes: GETTER key inside enum object literal has no string value -> ENUM_NOT_CONSTANT.
  @Test
  public void testCreateEnumTypeFromNodes_getterKey_reportsDiagnostic() throws Throwable {
    String js = "/** @enum {number} */\nvar Foo = {\n  get A() { return 1; }\n};";
    Result result = compileCode(js);
    assertTrue(result.errors.length + result.warnings.length >= 1);
  }

  // defineObjectLiteral: @lends referencing an undeclared variable -> UNKNOWN_LENDS warning.
  @Test
  public void testDefineObjectLiteral_lendsOnUndeclaredName_reportsUnknownLendsWarning() throws Throwable {
    Result result = compileCode("/** @lends {NotDeclared} */\n({foo: 1});");
    assertTrue(result.warnings.length >= 1);
  }

  // defineObjectLiteral: @lends on a declared non-object-typed variable -> LENDS_ON_NON_OBJECT warning.
  @Test
  public void testDefineObjectLiteral_lendsOnNonObjectType_reportsLendsOnNonObjectWarning() throws Throwable {
    String js = "/** @type {number} */\nvar num;\n/** @lends {num} */\n({foo: 1});";
    Result result = compileCode(js);
    assertTrue(result.warnings.length >= 1);
  }

  // defineObjectLiteral: @lends on a valid object type (Foo.prototype) -> no warning.
  @Test
  public void testDefineObjectLiteral_lendsOnValidPrototype_noWarning() throws Throwable {
    String js = "/** @constructor */\nfunction Foo() {}\n"
        + "/** @lends {Foo.prototype} */\n({bar: function() {}});";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // maybeDeclareQualifiedName: stub property declaration (bare GETPROP statement, no value) resolves silently.
  @Test
  public void testMaybeDeclareQualifiedName_stubPropertyDeclaration_noWarning() throws Throwable {
    String js = "/** @constructor */\nfunction Foo() {}\nFoo.prototype.bar;";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
    assertEquals(0, result.errors.length);
  }

  // maybeDeclareQualifiedName: F.prototype reassigned to an object literal with no explicit supertype -> allowed.
  @Test
  public void testMaybeDeclareQualifiedName_prototypeReassignedToObjectLiteral_noWarning() throws Throwable {
    String js = "/** @constructor */\nfunction Foo() {}\nFoo.prototype = {bar: 1};";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // Baseline: simple inferred var declaration with no JSDoc -> no diagnostics.
  @Test
  public void testDefineVar_simpleInferredDeclaration_noWarning() throws Throwable {
    Result result = compileCode("var x = 1;");
    assertEquals(0, result.warnings.length);
    assertEquals(0, result.errors.length);
  }

  // createFunctionTypeFromNodes: function literal with @param/@return JSDoc and one parameter.
  @Test
  public void testDefineFunctionLiteral_withParamAndReturnJsDoc_noWarning() throws Throwable {
    String js = "/**\n * @param {number} a\n * @return {number}\n */\nfunction f(a) { return a; }";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // declareArguments: zero-parameter loop iteration.
  @Test
  public void testDeclareArguments_zeroParameters_noWarning() throws Throwable {
    String js = "/**\n * @return {number}\n */\nfunction f() { return 1; }";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // declareArguments: multiple (two) parameter loop iterations.
  @Test
  public void testDeclareArguments_multipleParameters_noWarning() throws Throwable {
    String js = "/**\n * @param {number} a\n * @param {string} b\n */\nfunction f(a, b) { return a; }";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // getDeclaredType: @const annotation on a literal with a known, non-unknown type -> treated as declared.
  @Test
  public void testGetDeclaredType_constAnnotationWithKnownType_noWarning() throws Throwable {
    Result result = compileCode("/** @const */\nvar X = 5;");
    assertEquals(0, result.warnings.length);
  }

  // defineCatch: catch parameter declared, combined with a hoisted function declaration call.
  @Test
  public void testDefineCatch_catchParameterDeclared_noWarning() throws Throwable {
    String js = "function foo() {}\ntry {\n  foo();\n} catch (e) {\n}\n";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
    assertEquals(0, result.errors.length);
  }

  // maybeDeclareQualifiedName: ASSIGN to a GETPROP with @param function literal declares a method type.
  @Test
  public void testMaybeDeclareQualifiedName_assignFunctionLiteralWithJsDoc_noWarning() throws Throwable {
    String js = "var ns = {};\n/** @param {number} a */\nns.bar = function(a) {};";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // maybeDeclareQualifiedName: ASSIGN to a GETPROP with @type annotation declares a property type.
  @Test
  public void testMaybeDeclareQualifiedName_assignWithTypeAnnotation_noWarning() throws Throwable {
    String js = "var ns = {};\n/** @type {number} */\nns.value = 5;";
    Result result = compileCode(js);
    assertEquals(0, result.warnings.length);
  }

  // checkForTypedef: @typedef resolving to a concrete, known type -> no MALFORMED_TYPEDEF warning.
  @Test
  public void testCheckForTypedef_validTypedef_noWarning() throws Throwable {
    Result result = compileCode("/** @typedef {number} */\nvar MyTypedef;");
    assertEquals(0, result.warnings.length);
  }
}
