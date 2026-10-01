package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class TypedScopeCreatorClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
    options.setCheckTypes(true);
  }

  private void runTypeCheck(String js) {
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(externs, input, options);
  }

  private void runTypeCheckWithExterns(String externsCode, String js) {
    SourceFile externs = SourceFile.fromCode("externs.js", externsCode);
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(externs, input, options);
  }

  // createScope: empty script still builds native-type scope without crashing or erroring
  @Test
  public void testCreateScope_emptyScript_noErrors() throws Throwable {
    runTypeCheck("");
    assertEquals(0, compiler.getErrorCount());
  }

  // createScope + defineVar: plain var with no jsdoc, inferred type, no errors
  @Test
  public void testCreateScope_simpleVarDeclaration_noErrors() throws Throwable {
    runTypeCheck("var x = 1;");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineVar: multiple names sharing one jsdoc -> MULTIPLE_VAR_DEF warning branch
  @Test
  public void testDefineVar_multipleNamesWithJsDoc_reportsWarning() throws Throwable {
    runTypeCheck("/** @type {number} */ var x = 1, y = 2;");
    assertTrue(compiler.getWarningCount() >= 1);
  }

  // defineVar: multiple names without jsdoc -> no MULTIPLE_VAR_DEF warning
  @Test
  public void testDefineVar_multipleNamesWithoutJsDoc_noWarning() throws Throwable {
    runTypeCheck("var x = 1, y = 2;");
    assertEquals(0, compiler.getWarningCount());
  }

  // defineVar: single name with jsdoc, no warning branch taken
  @Test
  public void testDefineVar_singleNameWithJsDoc_noWarning() throws Throwable {
    runTypeCheck("/** @type {number} */ var x = 1;");
    assertEquals(0, compiler.getWarningCount());
  }

  // defineDeclaredFunction: plain function declaration/use, no errors
  @Test
  public void testDefineDeclaredFunction_plainFunction_noErrors() throws Throwable {
    runTypeCheck("function foo() { return 1; } foo();");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineDeclaredFunction + getFunctionType: constructor declaration, instantiable
  @Test
  public void testDefineDeclaredFunction_constructorDeclaration_noErrors() throws Throwable {
    runTypeCheck("/** @constructor */ function Foo() {} new Foo();");
    assertEquals(0, compiler.getErrorCount());
  }

  // getFunctionType inferInheritance: subclass instance assignable to superclass-typed var
  @Test
  public void testGetFunctionType_subclassAssignableToSuperclass_noErrors() throws Throwable {
    runTypeCheck("/** @constructor */ function A() {}"
        + "/** @constructor @extends {A} */ function B() {}"
        + "/** @type {A} */ var a = new B();");
    assertEquals(0, compiler.getErrorCount());
  }

  // getFunctionType inferInheritance: superclass instance NOT assignable to subclass-typed var
  @Test
  public void testGetFunctionType_superclassNotAssignableToSubclass_reportsError()
      throws Throwable {
    runTypeCheck("/** @constructor */ function A() {}"
        + "/** @constructor @extends {A} */ function B() {}"
        + "/** @type {B} */ var b = new A();");
    assertTrue(compiler.getErrorCount() >= 1);
  }

  // getFunctionType: global function alias of a constructor registers same instance type
  @Test
  public void testGetFunctionType_globalFunctionAliasOfConstructor_noErrors() throws Throwable {
    runTypeCheck("/** @constructor */ function A() {}"
        + "/** @constructor */ var B = A;"
        + "/** @type {A} */ var a2 = new B();");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineNamedTypeAssign: constructor assigned to a qualified name, instantiable
  @Test
  public void testDefineNamedTypeAssign_constructorOnQualifiedName_noErrors() throws Throwable {
    runTypeCheck("var ns = {};"
        + "/** @constructor */ ns.Foo = function() {};"
        + "new ns.Foo();");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineNamedTypeAssign + getEnumType: valid enum object literal, no errors/warnings
  @Test
  public void testDefineNamedTypeAssign_enumValidObjectLiteral_noErrors() throws Throwable {
    runTypeCheck("/** @enum {number} */ var Color = {RED: 1, GREEN: 2};"
        + "var c = Color.RED;");
    assertEquals(0, compiler.getErrorCount());
    assertEquals(0, compiler.getWarningCount());
  }

  // getEnumType: duplicate key in object literal reports ENUM_DUP warning
  @Test
  public void testGetEnumType_duplicateKey_reportsWarning() throws Throwable {
    runTypeCheck("/** @enum {number} */ var Color = {RED: 1, RED: 2};");
    assertTrue(compiler.getWarningCount() >= 1);
  }

  // getEnumType: non-object, non-enum initializer reports ENUM_INITIALIZER warning
  @Test
  public void testGetEnumType_nonObjectInitializer_reportsWarning() throws Throwable {
    runTypeCheck("/** @enum {number} */ var Color = 5;");
    assertTrue(compiler.getWarningCount() >= 1);
  }

  // getEnumType: referencing an existing enum as initializer is accepted
  @Test
  public void testGetEnumType_referenceToExistingEnum_noErrors() throws Throwable {
    runTypeCheck("/** @enum {number} */ var Color = {RED: 1};"
        + "/** @enum {number} */ var Color2 = Color;");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineCatch: catch parameter declared as a variable in its block
  @Test
  public void testDefineCatch_catchParameterDeclared_noErrors() throws Throwable {
    runTypeCheck("try { throw 1; } catch (e) { var x = e; }");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineSlot: conflicting redeclaration of a global var reports a warning
  @Test
  public void testDefineSlot_conflictingRedeclaration_reportsWarning() throws Throwable {
    runTypeCheck("/** @type {number} */ var x = 1;"
        + "/** @type {string} */ var x = 'a';");
    assertTrue(compiler.getWarningCount() >= 1);
  }

  // getFunctionType/inferParameterTypes: wrong argument type on call reports an error
  @Test
  public void testGetFunctionType_paramTypeMismatchOnCall_reportsError() throws Throwable {
    runTypeCheck("/** @param {number} a @return {number} */ function f(a) { return a; }"
        + "f('str');");
    assertTrue(compiler.getErrorCount() >= 1);
  }

  // LocalScopeBuilder.declareArguments: correct argument type produces no errors
  @Test
  public void testLocalScopeBuilder_declareArguments_correctUsage_noErrors() throws Throwable {
    runTypeCheck("/** @param {number} a @return {number} */ function f(a) { return a + 1; }"
        + "f(5);");
    assertEquals(0, compiler.getErrorCount());
  }

  // LocalScopeBuilder.handleFunctionInputs: bleeding function name accessible in own body
  @Test
  public void testLocalScopeBuilder_bleedingFunctionName_noErrors() throws Throwable {
    runTypeCheck("var f = function g() { return g; };");
    assertEquals(0, compiler.getErrorCount());
  }

  // attachLiteralTypes: OBJECTLIT literal gets a type attached without error
  @Test
  public void testAttachLiteralTypes_objectLiteral_noErrors() throws Throwable {
    runTypeCheck("var o = {};");
    assertEquals(0, compiler.getErrorCount());
  }

  // attachLiteralTypes: null/void/boolean/regexp literal nodes all type without error
  @Test
  public void testAttachLiteralTypes_variousLiterals_noErrors() throws Throwable {
    runTypeCheck("var a = null; var b = void 0; var c = true; var d = false; var e = /abc/;");
    assertEquals(0, compiler.getErrorCount());
  }

  // getPrototypePropertyOwner + maybeDeclareQualifiedName: prototype method declared on owner
  @Test
  public void testGetPrototypePropertyOwner_prototypeMethodDeclaration_noErrors()
      throws Throwable {
    runTypeCheck("/** @constructor */ function Foo() {}"
        + "Foo.prototype.bar = function() { return 1; };"
        + "(new Foo()).bar();");
    assertEquals(0, compiler.getErrorCount());
  }

  // maybeDeclareQualifiedName: @type annotated property declared and used with correct type
  @Test
  public void testMaybeDeclareQualifiedName_declaredPropertyWithType_noErrors() throws Throwable {
    runTypeCheck("var ns = {};"
        + "/** @type {number} */ ns.x = 5;"
        + "var y = ns.x + 1;");
    assertEquals(0, compiler.getErrorCount());
  }

  // checkForClassDefiningCalls: plain call with no class-defining relationship is harmless
  @Test
  public void testCheckForClassDefiningCalls_plainCall_noErrors() throws Throwable {
    runTypeCheck("function foo() {} foo();");
    assertEquals(0, compiler.getErrorCount());
  }

  // GlobalScopeBuilder.checkForTypedef: typedef declared then used as a type, no errors
  @Test
  public void testCheckForTypedef_typedefUsedAsType_noErrors() throws Throwable {
    runTypeCheck("/** @typedef {number} */ var MyNum;"
        + "/** @type {MyNum} */ var x = 5;");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineName: inferred (no jsdoc) variable can be reassigned a different type
  @Test
  public void testDefineName_inferredVariableReassignedDifferentType_noErrors()
      throws Throwable {
    runTypeCheck("var x = 1; x = 'hello';");
    assertEquals(0, compiler.getErrorCount());
  }

  // defineSlot shouldDeclareOnGlobalThis: global var accessible as a property of global this
  @Test
  public void testDefineSlot_globalVarAccessibleAsGlobalThisProperty_noErrors()
      throws Throwable {
    runTypeCheck("var x = 1; var y = this.x;");
    assertEquals(0, compiler.getErrorCount());
  }

  // resolveStubDeclarations: extern stub property resolved to unknown type, usable
  @Test
  public void testResolveStubDeclarations_externStubProperty_noErrors() throws Throwable {
    runTypeCheckWithExterns(
        "/** @constructor */ function Foo() {}; Foo.prototype.bar;",
        "var f = new Foo(); var x = f.bar;");
    assertEquals(0, compiler.getErrorCount());
  }

  // maybeDeclareQualifiedName stub branch: non-extern stub property registered on owner type
  @Test
  public void testMaybeDeclareQualifiedName_nonExternStubProperty_noErrors() throws Throwable {
    runTypeCheck("var ns = {}; ns.prop;");
    assertEquals(0, compiler.getErrorCount());
  }

  // findOverriddenFunction: method overriding interface infers return type, mismatch reported
  @Test
  public void testFindOverriddenFunction_interfaceReturnTypeMismatch_reportsError()
      throws Throwable {
    runTypeCheck("/** @interface */ function I() {}"
        + "/** @return {number} */ I.prototype.foo = function() {};"
        + "/** @constructor @implements {I} */ function C() {}"
        + "C.prototype.foo = function() { return 1; };"
        + "/** @type {string} */ var s = (new C()).foo();");
    assertTrue(compiler.getErrorCount() >= 1);
  }

  // DiscoverEnums/defineNamedTypeAssign: enum declared via qualified-name assignment
  @Test
  public void testDiscoverEnums_assignEnumExpression_noErrors() throws Throwable {
    runTypeCheck("var ns = {};"
        + "/** @enum {number} */ ns.Color = {RED: 1};"
        + "var r = ns.Color.RED;");
    assertEquals(0, compiler.getErrorCount());
  }

  // getFunctionType: constructor function with no extends has Object as implicit supertype
  @Test
  public void testGetFunctionType_constructorWithoutExtends_instantiable() throws Throwable {
    runTypeCheck("/** @constructor */ function Plain() {}"
        + "var p = new Plain();");
    assertEquals(0, compiler.getErrorCount());
  }

}
