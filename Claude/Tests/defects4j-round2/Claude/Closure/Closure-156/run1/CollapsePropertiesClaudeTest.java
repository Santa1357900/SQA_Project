package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CollapsePropertiesClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;
  private List<SourceFile> externs;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
    options.collapseProperties = true;
    externs = new ArrayList<SourceFile>();
  }

  private Result compile(String js) {
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    return compiler.compile(externs, inputs, options);
  }

  // Constructor: both collapse/inline flags false must build an instance without error
  @Test
  public void testConstructor_falseFalse_instanceCreated() throws Throwable {
    CollapseProperties pass = new CollapseProperties(compiler, false, false);
    assertNotNull(pass);
  }

  // Constructor: both collapse/inline flags true must build an instance without error
  @Test
  public void testConstructor_trueTrue_instanceCreated() throws Throwable {
    CollapseProperties pass = new CollapseProperties(compiler, true, true);
    assertNotNull(pass);
  }

  // process(): globalNames forest has zero entries -> loops run 0 times, no crash
  @Test
  public void testProcess_emptySource_resultSuccess() throws Throwable {
    Result result = compile("");
    assertTrue(result.success);
  }

  // process(): a plain scalar var has no props, flatten/collapse loops skip it (n.props == null)
  @Test
  public void testProcess_noNamespaceSimpleVar_compilesSuccessfully() throws Throwable {
    Result result = compile("var x = 5; var y = x + 1;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("x"));
  }

  // declareVarsForObjLitValues: two identifier keys become separate global vars
  @Test
  public void testProcess_simpleTwoPropObjectLiteral_collapsedToGlobalVars() throws Throwable {
    Result result = compile("var a = {b: 1, c: 2}; var d = a.b + a.c;");
    assertTrue(result.success);
    String out = compiler.toSource();
    assertTrue(out.contains("a$b"));
    assertTrue(out.contains("a$c"));
  }

  // declareVarsForObjLitValues: function-valued key collapsed and still invocable
  @Test
  public void testProcess_objectLiteralWithFunctionValue_collapsedAndInvocable() throws Throwable {
    Result result = compile("var a = {b: function() { return 1; }}; var r = a.b();");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$b"));
  }

  // flattenReferencesTo + flattenPrefixes: goog.events.handleEvent() style three-level flattening
  @Test
  public void testProcess_threeLevelNamespace_flattenedFunctionCall() throws Throwable {
    Result result = compile(
        "var goog = {}; goog.events = {}; "
        + "goog.events.handleEvent = function() {}; goog.events.handleEvent();");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("goog$events$handleEvent"));
  }

  // flattenPrefixes recursion depth > 1: four-level dotted chain fully flattened
  @Test
  public void testProcess_fourLevelNestedProperty_flattenedAllLevels() throws Throwable {
    Result result = compile(
        "var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = 1; var e = a.b.c.d;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$b$c$d"));
  }

  // process(): globalNames forest with 2 independent roots, each collapsed independently
  @Test
  public void testProcess_multipleIndependentNamespaces_bothCollapsed() throws Throwable {
    Result result = compile("var a = {b: 1}; var x = {y: 2}; var c = a.b + x.y;");
    assertTrue(result.success);
    String out = compiler.toSource();
    assertTrue(out.contains("a$b"));
    assertTrue(out.contains("x$y"));
  }

  // updateObjLitOrFunctionDeclarationAtAssignNode: isObjLit && canEliminate removes the literal
  @Test
  public void testProcess_assignWithoutVarKeyword_eliminatesObjectLiteral() throws Throwable {
    Result result = compile("a = {b: 1, c: 2}; var d = a.b + a.c;");
    assertTrue(result.success);
    String out = compiler.toSource();
    assertTrue(out.contains("a$b"));
    assertTrue(out.contains("a$c"));
  }

  // updateFunctionDeclarationAtFunctionNode: name declared via FUNCTION node, property collapsed
  @Test
  public void testProcess_functionDeclarationThenProperty_propertyCollapsed() throws Throwable {
    Result result = compile("function a() {} a.b = 1; var c = a.b;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$b"));
  }

  // addStubsForUndeclaredProperties: property only ever set inside a local function scope
  @Test
  public void testProcess_propertySetOnlyInFunctionScope_stubCreatedAndFlattened() throws Throwable {
    Result result = compile(
        "var a = {}; function f() { a.b = 1; } f(); var c = a.b;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$b"));
  }

  // javadoc: "doesn't flatten property accesses of the form a[b]" while dotted access still collapses
  @Test
  public void testProcess_dotAccessStillFlattenedDespiteBracketAccessElsewhere() throws Throwable {
    Result result = compile("var a = {b: 1}; var k = 'b'; var m = a[k]; var v = a.b;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$b"));
  }

  // appendPropForAlias: '$' inside a property name is encoded as "$0" per source comment
  @Test
  public void testProcess_dollarSignInPropertyName_encodedAsDollarZero() throws Throwable {
    Result result = compile("var a = {$x: 1}; var v = a.$x;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$$0x"));
  }

  // declareVarsForObjLitValues: non-identifier key uses an arbitrary numeric name, no crash
  @Test
  public void testProcess_nonIdentifierObjectLiteralKey_compilesWithoutError() throws Throwable {
    Result result = compile("var a = {'b-c': 1};");
    assertTrue(result.success);
    assertEquals(0, result.errors.length);
  }

  // declareVarsForObjLitValues: NUMBER-typed key forces arbitrary name path, no crash
  @Test
  public void testProcess_numericObjectLiteralKey_compilesWithoutError() throws Throwable {
    Result result = compile("var a = {0: 'zero', 1: 'one'};");
    assertTrue(result.success);
    assertEquals(0, result.errors.length);
  }

  // checkNamespaces: namespace never aliased/redefined produces zero warnings
  @Test
  public void testProcess_safeNonAliasedNamespace_zeroWarnings() throws Throwable {
    Result result = compile("var a = {b: 1}; var c = a.b;");
    assertTrue(result.success);
    assertEquals(0, result.warnings.length);
  }

  // checkNamespaces + warnAboutNamespaceAliasing: aliasingGets > 0 triggers UNSAFE_NAMESPACE_WARNING
  @Test
  public void testProcess_aliasedNamespaceObject_unsafeNamespaceWarningReported() throws Throwable {
    Result result = compile("var a = {b: 0}; var c = a; c.b = 5;");
    assertTrue(result.success);
    assertTrue(result.warnings.length > 0);
  }

  // checkNamespaces + warnAboutNamespaceRedefinition: two global sets of same namespace property
  @Test
  public void testProcess_redefinedNamespaceProperty_redefinitionWarningReported() throws Throwable {
    Result result = compile("var a = {}; a.b = {}; a.b = {};");
    assertTrue(result.success);
    assertTrue(result.warnings.length > 0);
  }

  // checkForHosedThisReferences: collapsed static function referencing 'this' w/o @this or @constructor
  @Test
  public void testProcess_unannotatedThisInCollapsedFunction_unsafeThisWarningReported() throws Throwable {
    Result result = compile("var a = {}; a.b = function() { this.c = 1; }; a.b();");
    assertTrue(result.success);
    assertTrue(result.warnings.length > 0);
  }

  // updateSimpleDeclaration: TWIN reference from a complex assignment still flattens correctly
  @Test
  public void testProcess_complexAssignExpression_collapsesWithoutError() throws Throwable {
    Result result = compile("var a = {}; var c; c = a.b = 5; var d = a.b;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$b"));
  }

  // collapseDeclarationOfNameAndDescendants: nested object literal values unroll fully to leaves
  @Test
  public void testProcess_deeplyNestedObjectLiteralValues_allLevelsCollapsed() throws Throwable {
    Result result = compile("var a = {b: {c: 1}}; var d = a.b.c;");
    assertTrue(result.success);
    assertTrue(compiler.toSource().contains("a$b$c"));
  }

  // toSource(): after a successful pass run, output is a non-null non-empty string
  @Test
  public void testToSource_afterProcessing_returnsNonNullNonEmptyString() throws Throwable {
    compile("var a = {b: 1}; var d = a.b;");
    String out = compiler.toSource();
    assertNotNull(out);
    assertTrue(out.length() > 0);
  }

  // compile(): valid syntax yields an error-free Result
  @Test
  public void testCompile_validSyntax_resultErrorsEmpty() throws Throwable {
    Result result = compile("var a = {b: 1, c: {d: 2}}; var e = a.b + a.c.d;");
    assertEquals(0, result.errors.length);
    assertTrue(result.success);
  }

  // process(): a name with a single prop and single global set collapses to exactly one new var
  @Test
  public void testProcess_singlePropertyNamespace_exactAliasAppearsOnce() throws Throwable {
    Result result = compile("var ns = {}; ns.val = 42; var r = ns.val;");
    assertTrue(result.success);
    String out = compiler.toSource();
    assertTrue(out.contains("ns$val"));
    assertFalse(out.contains("ns.val"));
  }
}
