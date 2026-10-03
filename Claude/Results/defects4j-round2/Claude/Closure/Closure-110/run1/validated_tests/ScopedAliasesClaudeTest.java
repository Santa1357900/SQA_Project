package com.google.javascript.jscomp;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScopedAliasesClaudeTest {

  private Compiler compile(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    compiler.compile(externs, inputs, options);
    return compiler;
  }

  // Covers the SCOPING_METHOD_NAME constant used by isCallToScopeMethod.
  @Test
  public void testScopingMethodNameConstant() throws Throwable {
    assertEquals("goog.scope", ScopedAliases.SCOPING_METHOD_NAME);
  }

  // Sanity check that all declared DiagnosticType constants are initialized.
  @Test
  public void testDiagnosticTypeConstantsAreNotNull() throws Throwable {
    assertNotNull(ScopedAliases.GOOG_SCOPE_USED_IMPROPERLY);
    assertNotNull(ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    assertNotNull(ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
    assertNotNull(ScopedAliases.GOOG_SCOPE_USES_RETURN);
    assertNotNull(ScopedAliases.GOOG_SCOPE_USES_THROW);
    assertNotNull(ScopedAliases.GOOG_SCOPE_ALIAS_REDEFINED);
    assertNotNull(ScopedAliases.GOOG_SCOPE_ALIAS_CYCLE);
    assertNotNull(ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
  }

  // Basic alias: var dom = goog.dom; dom.createElement('DIV') should inline
  // to goog.dom.createElement, removing both the var decl and goog.scope(.
  @Test
  public void testProcess_basicAlias_inlinesAndRemovesScopeAndVarDecl() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var dom = goog.dom;\n"
        + "  dom.createElement('DIV');\n"
        + "});";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
    String src = compiler.toSource();
    assertTrue(src.contains("goog.dom.createElement("));
    assertFalse(src.contains("goog.scope("));
    assertFalse(src.contains("dom="));
  }

  // Transitive alias: var g = goog; var d = g.dom; should fully resolve to
  // goog.dom at the usage site.
  @Test
  public void testProcess_transitiveAlias_inlinesRootQualifiedName() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var g = goog;\n"
        + "  var d = g.dom;\n"
        + "  d.createElement('DIV');\n"
        + "});";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
    String src = compiler.toSource();
    assertTrue(src.contains("goog.dom.createElement("));
    assertFalse(src.contains("goog.scope("));
  }

  // Alias whose value is a bare (dot-less) qualified name still inlines.
  @Test
  public void testProcess_aliasWithoutDot_inlinesDirectly() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var g = goog;\n"
        + "  g.foo();\n"
        + "});";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
    assertTrue(compiler.toSource().contains("goog.foo("));
  }

  // Two independent aliases in the same block should both be inlined.
  @Test
  public void testProcess_multipleIndependentAliases_bothInlined() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var A = ns.A;\n"
        + "  var B = ns.B;\n"
        + "  A.x();\n"
        + "  B.y();\n"
        + "});";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
    String src = compiler.toSource();
    assertTrue(src.contains("ns.A.x("));
    assertTrue(src.contains("ns.B.y("));
  }

  // Alias usage nested inside an if-block (same scope depth) still inlines.
  @Test
  public void testProcess_aliasUsedInsideIfBlock_stillInlined() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var dom = goog.dom;\n"
        + "  if (1) {\n"
        + "    dom.createElement('DIV');\n"
        + "  }\n"
        + "});";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
    assertTrue(compiler.toSource().contains("goog.dom.createElement("));
  }

  // A plain local var with a non-qualified-name value is allowed (converted
  // to a global $jscomp.scope alias) and must not report an error.
  @Test
  public void testProcess_nonQualifiedLocalVar_noErrorReported() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var x = 3;\n"
        + "  use(x);\n"
        + "});";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
  }

  // Two aliases referencing each other form a cycle and must be reported.
  @Test
  public void testProcess_aliasCycle_reportsCycleError() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var a = b;\n"
        + "  var b = a;\n"
        + "});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }



  // The call to goog.scope must be alone in its own statement.
  @Test
  public void testProcess_scopeCallNotAloneInStatement_reportsUsedImproperly() throws Throwable {
    String js = "var x = goog.scope(function() {});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // goog.scope() with zero arguments has the wrong child count.
  @Test
  public void testProcess_scopeCallZeroArguments_reportsBadParameters() throws Throwable {
    String js = "goog.scope();";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // goog.scope must take exactly one argument.
  @Test
  public void testProcess_scopeCallTooManyArguments_reportsBadParameters() throws Throwable {
    String js = "goog.scope(function() {}, 1);";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // The single argument must itself be a function literal.
  @Test
  public void testProcess_scopeCallArgumentNotFunction_reportsBadParameters() throws Throwable {
    String js = "goog.scope(123);";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // The function literal passed to goog.scope must be anonymous.
  @Test
  public void testProcess_scopeCallNamedFunction_reportsBadParameters() throws Throwable {
    String js = "goog.scope(function namedFn() {});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // The function literal passed to goog.scope must take no parameters.
  @Test
  public void testProcess_scopeCallFunctionWithParams_reportsBadParameters() throws Throwable {
    String js = "goog.scope(function(a) {});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // The body of goog.scope must not reference 'this'.
  @Test
  public void testProcess_scopeBodyReferencesThis_reportsError() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  this.foo();\n"
        + "});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // The body of goog.scope must not use 'return'.
  @Test
  public void testProcess_scopeBodyUsesReturn_reportsError() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  return;\n"
        + "});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // The body of goog.scope must not use 'throw'.
  @Test
  public void testProcess_scopeBodyUsesThrow_reportsError() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  throw 1;\n"
        + "});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // Assigning to an alias a second time must report alias-redefined.
  @Test
  public void testProcess_aliasAssignedTwice_reportsRedefinedError() throws Throwable {
    String js = "goog.scope(function() {\n"
        + "  var x = goog.dom;\n"
        + "  x = goog.events;\n"
        + "});";
    Compiler compiler = compile(js);
    assertTrue(compiler.getErrorCount() > 0);
  }

  // Code with no goog.scope call at all must compile without errors.
  @Test
  public void testProcess_noScopeCallPresent_noErrors() throws Throwable {
    String js = "var x = 1;";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
  }

  // A call to a differently-named method must not be treated as goog.scope,
  // so 'return' inside it is perfectly legal.
  @Test
  public void testProcess_callToDifferentMethodName_notTreatedAsScope_noErrors() throws Throwable {
    String js = "other.scope(function() { return 1; });";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
    assertTrue(compiler.toSource().contains("other.scope("));
  }

  // An empty goog.scope body must be accepted and the call removed.
  @Test
  public void testProcess_emptyScopeBody_scopeCallRemovedNoErrors() throws Throwable {
    String js = "goog.scope(function() {});";
    Compiler compiler = compile(js);
    assertEquals(0, compiler.getErrorCount());
    assertFalse(compiler.toSource().contains("goog.scope("));
  }


}
