package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class ScopedAliasesClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
    options.setClosurePass(true);
    options.setPrettyPrint(true);
  }

  private Result compile(String js) {
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    return compiler.compile(externs, input, options);
  }

  // Basic documented example: chained alias usage is fully qualified in the call.
  @Test
  public void testProcess_simpleAliasChain_rewritesToFullyQualifiedCall() throws Throwable {
    String js = "var goog = {}; goog.dom = {}; goog.dom.TagName = {};"
        + "goog.dom.TagName.DIV = 'DIV'; goog.dom.createElement = function(x) {};"
        + "goog.scope(function() { var dom = goog.dom; var DIV = dom.TagName.DIV;"
        + "dom.createElement(DIV); });";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertTrue(output.contains("goog.dom.createElement(goog.dom.TagName.DIV)"));
  }



  // The local alias declarations ("var dom", "var DIV") must be removed from output.
  @Test
  public void testProcess_aliasDeclarationsRemovedFromOutput() throws Throwable {
    String js = "var goog = {}; goog.dom = {}; goog.dom.foo = function() {};"
        + "goog.scope(function() { var dom = goog.dom; dom.foo(); });";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertFalse(output.contains("var dom"));
    assertFalse(output.contains("goog.scope("));
  }

  // Code with no goog.scope calls should compile fine and keep the call intact.
  @Test
  public void testProcess_noScopeCalls_leavesUnrelatedCodeIntact() throws Throwable {
    String js = "var foo = {}; foo.bar = function() {}; foo.bar();";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertTrue(output.contains("foo.bar()"));
  }

  // goog.scope call must be alone in a single statement (not an rvalue).
  @Test
  public void testProcess_scopeCallNotAloneStatement_reportsUsedImproperly() throws Throwable {
    String js = "var y = goog.scope(function() {});";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // goog.scope must take exactly one argument; zero arguments is invalid.
  @Test
  public void testProcess_scopeCallMissingArgument_reportsBadParameters() throws Throwable {
    String js = "goog.scope();";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // goog.scope must take exactly one argument; extra arguments are invalid.
  @Test
  public void testProcess_scopeCallExtraArgument_reportsBadParameters() throws Throwable {
    String js = "goog.scope(function() {}, 1);";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // The anonymous function passed to goog.scope must take no parameters.
  @Test
  public void testProcess_scopeFunctionHasParameter_reportsBadParameters() throws Throwable {
    String js = "goog.scope(function(a) {});";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // The function passed to goog.scope must be anonymous (not named).
  @Test
  public void testProcess_scopeFunctionIsNamed_reportsBadParameters() throws Throwable {
    String js = "goog.scope(function foo() {});";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // The body of a goog.scope function cannot reference 'this'.
  @Test
  public void testProcess_scopeBodyReferencesThis_reportsError() throws Throwable {
    String js = "goog.scope(function() { this.foo(); });";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // The body of a goog.scope function cannot use 'return'.
  @Test
  public void testProcess_scopeBodyUsesReturn_reportsError() throws Throwable {
    String js = "goog.scope(function() { return 1; });";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // The body of a goog.scope function cannot use 'throw'.
  @Test
  public void testProcess_scopeBodyUsesThrow_reportsError() throws Throwable {
    String js = "goog.scope(function() { throw 1; });";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // An alias assigned a value more than once must be reported as an error.
  @Test
  public void testProcess_aliasReassigned_reportsRedefinedError() throws Throwable {
    String js = "goog.scope(function() { var x = goog.dom; x = goog.events; });";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // Two aliases that reference each other form a cycle and must be reported.
  @Test
  public void testProcess_twoAliasesFormCycle_reportsCycleError() throws Throwable {
    String js = "goog.scope(function() { var a = b; var b = a; });";
    Result result = compile(js);
    assertFalse(result.success);
  }

  // A local alias whose value is not a qualified name is wrapped via $jscomp.scope.
  @Test
  public void testProcess_aliasValueNotQualifiedName_wrapsInJscompScope() throws Throwable {
    String js = "var foo = function() { return {}; };"
        + "goog.scope(function() { var x = foo(); });";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertTrue(output.contains("$jscomp.scope.x"));
  }

  // Duplicate wrapped alias names across separate goog.scope blocks get an index suffix.
  @Test
  public void testProcess_duplicateWrappedAliasNameAcrossScopes_appendsIndexSuffix()
      throws Throwable {
    String js = "var foo = function() { return {}; };"
        + "goog.scope(function() { var x = foo(); });"
        + "goog.scope(function() { var x = foo(); });";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertTrue(output.contains("$jscomp.scope.x$1"));
  }

  // A hoisted function declaration used as an alias is wrapped as a global declaration.
  @Test
  public void testProcess_hoistedFunctionDeclarationAlias_wrapsFunctionDeclaration()
      throws Throwable {
    String js = "goog.scope(function() { function helper() { return 1; }"
        + "var result = helper(); });";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertTrue(output.contains("$jscomp.scope.helper"));
  }

  // Aliases are also rewritten when used inside a nested function expression.
  @Test
  public void testProcess_aliasUsedInsideNestedFunction_rewritesReference() throws Throwable {
    String js = "var goog = {}; goog.dom = {}; goog.dom.foo = function(a) {};"
        + "goog.scope(function() { var dom = goog.dom;"
        + "var f = function() { dom.foo(1); }; f(); });";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertTrue(output.contains("goog.dom.foo(1)"));
  }

  // A nested-function local that shadows a namespace root is renamed, not an error.
  @Test
  public void testProcess_namespaceShadowInNestedFunction_compilesSuccessfully()
      throws Throwable {
    String js = "var goog = {}; goog.dom = {};"
        + "goog.scope(function() { var dom = goog.dom;"
        + "var inner = function() { var goog = {}; return goog; }; inner(); });";
    Result result = compile(js);
    assertTrue(result.success);
  }

  // Type annotations referencing an alias are processed without error.
  @Test
  public void testProcess_typeAnnotationAlias_compilesSuccessfully() throws Throwable {
    String js = "var goog = {}; goog.Foo = function() {};"
        + "goog.scope(function() { var Foo = goog.Foo;"
        + "/** @type {Foo} */ var x = new Foo(); });";
    Result result = compile(js);
    assertTrue(result.success);
  }

  // Multiple independent aliases declared in the same scope are each resolved.
  @Test
  public void testProcess_multipleIndependentAliasesInOneScope_allApplied() throws Throwable {
    String js = "var goog = {}; goog.dom = {}; goog.events = {};"
        + "goog.dom.foo = function() {}; goog.events.bar = function() {};"
        + "goog.scope(function() { var dom = goog.dom; var events = goog.events;"
        + "dom.foo(); events.bar(); });";
    Result result = compile(js);
    assertTrue(result.success);
    String output = compiler.toSource();
    assertTrue(output.contains("goog.dom.foo()"));
    assertTrue(output.contains("goog.events.bar()"));
  }

  // An empty, well-formed goog.scope block collapses successfully with no aliases.
  @Test
  public void testProcess_emptyScopeBody_noErrors() throws Throwable {
    String js = "goog.scope(function() {});";
    Result result = compile(js);
    assertTrue(result.success);
  }
}
