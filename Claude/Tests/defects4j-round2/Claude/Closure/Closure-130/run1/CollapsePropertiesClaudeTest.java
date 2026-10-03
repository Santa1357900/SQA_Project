package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

public class CollapsePropertiesClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
    options.setCollapseProperties(true);
  }

  private String compileCode(String js) {
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    compiler.compile(externs, inputs, options);
    return compiler.toSource();
  }

  // process(): simple object-literal property with single set collapses to a flattened var.
  @Test
  public void testProcess_simpleObjectLiteralProperty_collapsesToFlattenedVar() throws Throwable {
    String out = compileCode("var a = {};\na.b = 1;\n");
    assertNotNull(out);
    assertTrue(out.contains("a$b"));
    assertFalse(out.contains("a.b"));
  }

  // process(): nested namespace of object literals + function flattens fully into one name.
  @Test
  public void testProcess_nestedNamespaceFunctionDeclaration_flattensToSingleName() throws Throwable {
    String out = compileCode("var goog = {};\ngoog.events = {};\ngoog.events.handleEvent = function() {};\n");
    assertTrue(out.contains("goog$events$handleEvent"));
  }

  // flattenPrefixes(): a prefix of a deeper qualified name is also flattened in later references.
  @Test
  public void testProcess_depthTwoPrefixFlattening_flattensReferenceToDescendant() throws Throwable {
    String out = compileCode("var a = {};\na.b = {};\na.b.c = 1;\nvar x = a.b.c;\n");
    assertTrue(out.contains("a$b$c"));
  }

  // checkNamespaces()/canCollapse(): aliasing a namespace (Javadoc example) prevents collapsing its property.
  @Test
  public void testProcess_aliasedNamespaceFromJavadocExample_doesNotCollapseAliasedProperty() throws Throwable {
    String out = compileCode("var a = {b: 0};\nvar c = a;\nc.b = 5;\n");
    assertFalse(out.contains("a$b"));
  }

  // checkNamespaces(): ALIASING_GET branch reports UNSAFE_NAMESPACE_WARNING for the same Javadoc example.
  @Test
  public void testProcess_aliasedNamespaceFromJavadocExample_producesUnsafeNamespaceWarning() throws Throwable {
    compileCode("var a = {b: 0};\nvar c = a;\nc.b = 5;\n");
    assertTrue(compiler.getWarningCount() > 0);
  }

  // checkNamespaces(): a clean single-set namespace with no aliasing emits no warnings.
  @Test
  public void testProcess_cleanSingleAssignmentNamespace_noWarningsEmitted() throws Throwable {
    compileCode("var a = {};\na.b = 1;\n");
    assertEquals(0, compiler.getWarningCount());
  }

  // checkNamespaces(): SET_FROM_GLOBAL after an initialized namespace triggers NAMESPACE_REDEFINED_WARNING.
  @Test
  public void testCheckNamespaces_namespaceReassignedAfterPropertySet_producesRedefinitionWarning() throws Throwable {
    compileCode("var a = {};\na.b = 1;\na = {};\n");
    assertTrue(compiler.getWarningCount() > 0);
  }

  // checkNamespaces(): reassigning an initialized object-literal namespace twice also warns.
  @Test
  public void testCheckNamespaces_namespaceReassignedTwiceWithDifferentLiterals_producesRedefinitionWarning()
      throws Throwable {
    compileCode("var a = {b: 1};\na = {c: 2};\n");
    assertTrue(compiler.getWarningCount() > 0);
  }

  // checkNamespaces(): DELETE_PROP branch on an initialized namespace triggers redefinition warning.
  @Test
  public void testCheckNamespaces_deletePropertyOnInitializedNamespace_producesRedefinitionWarning()
      throws Throwable {
    compileCode("var a = {b: 1};\ndelete a.b;\n");
    assertTrue(compiler.getWarningCount() > 0);
  }

  // checkNamespaces(): a single-assignment namespace with only one set emits no redefinition warning.
  @Test
  public void testCheckNamespaces_singleAssignmentNamespace_noRedefinitionWarning() throws Throwable {
    compileCode("var a = {b: 1};\n");
    assertEquals(0, compiler.getWarningCount());
  }

  // flattenNameRef(): call-target GETPROP is flattened into a NAME call expression.
  @Test
  public void testFlattenReferencesTo_callExpressionTarget_flattensCallSite() throws Throwable {
    String out = compileCode("var a = {};\na.b = function() { return 1; };\nvar x = a.b();\n");
    assertTrue(out.contains("a$b()"));
  }

  // flattenSimpleStubDeclaration(): a bare property-reference statement becomes a var stub.
  @Test
  public void testFlattenSimpleStubDeclaration_barePropertyStatement_convertsToVarStub() throws Throwable {
    String out = compileCode("var a = {};\na.b;\n");
    assertTrue(out.contains("a$b"));
    assertFalse(out.contains("a.b;"));
  }

  // addStubsForUndeclaredProperties(): a property only set in a local scope gets a global stub var.
  @Test
  public void testAddStubsForUndeclaredProperties_propertySetOnlyInsideLocalFunction_declaresGlobalStubVar()
      throws Throwable {
    String out = compileCode("var a = {};\nfunction f() {\n  a.b = 1;\n}\n");
    assertTrue(out.contains("a$b"));
  }

  // updateObjLitOrFunctionDeclaration()/Token.FUNCTION case: property on a named function declaration collapses.
  @Test
  public void testUpdateFunctionDeclarationAtFunctionNode_propertyOnNamedFunction_collapsesProperty()
      throws Throwable {
    String out = compileCode("function foo() {}\nfoo.bar = 1;\n");
    assertTrue(out.contains("foo$bar"));
  }

  // declareVarsForObjLitValues(): GET key is skipped (continue) while sibling normal keys still collapse.
  @Test
  public void testDeclareVarsForObjLitValues_getterKeySkipped_siblingAndOuterPropertiesStillCollapse()
      throws Throwable {
    String out = compileCode("var a = {};\na.b = 1;\na.c = { get d() { return 1; } };\n");
    assertTrue(out.contains("a$b"));
    assertTrue(out.contains("a$c"));
  }

  // declareVarsForObjLitValues(): a non-identifier quoted key produces an arbitrary numeric alias.
  @Test
  public void testDeclareVarsForObjLitValues_nonIdentifierKey_generatesArbitraryNumericAlias() throws Throwable {
    String out = compileCode("var a = {b: 1, '1a': 2};\n");
    assertTrue(out.contains("a$b"));
    assertTrue(out.contains("a$1"));
  }

  // appendPropForAlias(): a literal '$' inside a property name is encoded as "$0".
  @Test
  public void testAppendPropForAlias_dollarSignInPropertyName_encodesAsDollarZero() throws Throwable {
    String out = compileCode("var a = {};\na.b$c = 1;\n");
    assertTrue(out.contains("a$b$0c"));
  }

  // process(): bracket-form property access ([] ) is left untouched but the GETPROP declaration still collapses.
  @Test
  public void testProcess_bracketPropertyAccess_declarationStillCollapsed() throws Throwable {
    String out = compileCode("var a = {};\na.b = 1;\nvar y = a['b'];\n");
    assertTrue(out.contains("a$b"));
    assertTrue(out.contains("["));
  }

  // process(): a name with no properties at all is left with no flattened '$' identifiers introduced.
  @Test
  public void testProcess_nameWithoutAnyProperties_remainsUnmodified() throws Throwable {
    String out = compileCode("var a = 1;\nvar b = a;\n");
    assertFalse(out.contains("$"));
  }

  // checkForHosedThisReferences(): 'this' inside a collapsed function without @constructor/@this warns.
  @Test
  public void testCheckForHosedThisReferences_thisInsideCollapsedFunctionWithoutJsDoc_producesUnsafeThisWarning()
      throws Throwable {
    compileCode("var a = {};\na.b = function() { return this.x; };\n");
    assertTrue(compiler.getWarningCount() > 0);
  }

  // checkForHosedThisReferences(): no 'this' usage in the collapsed function means no warning.
  @Test
  public void testCheckForHosedThisReferences_noThisReferenceInCollapsedFunction_noWarning() throws Throwable {
    compileCode("var a = {};\na.b = function() { return 1; };\n");
    assertEquals(0, compiler.getWarningCount());
  }

  // updateSimpleDeclaration(): complex assignment (twin references) still flattens the set reference.
  @Test
  public void testUpdateSimpleDeclaration_complexAssignmentTwin_flattensSetReference() throws Throwable {
    String out = compileCode("var a = {};\nvar x;\nx = a.b = 5;\n");
    assertTrue(out.contains("a$b"));
  }

  // process(): no errors are produced for a well-formed, collapsible namespace program.
  @Test
  public void testProcess_wellFormedNamespaceProgram_noCompileErrors() throws Throwable {
    compileCode("var goog = {};\ngoog.events = {};\ngoog.events.handleEvent = function() {};\n");
    assertEquals(0, compiler.getErrorCount());
  }

  // process(): object-literal declaration is eliminated once all its properties are collapsed away.
  @Test
  public void testProcess_objectLiteralEliminatedAfterPropertyCollapse_noEmptyLiteralRemains() throws Throwable {
    String out = compileCode("var a = {};\na.b = 1;\n");
    assertFalse(out.contains("{}"));
  }
}
