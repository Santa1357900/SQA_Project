package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

public class NameAnalyzerClaudeTest {

  private static final String EXTERNS =
      "var window; function alert(x) {}";

  private String compile(String js, boolean smartNameRemoval) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setSmartNameRemoval(smartNameRemoval);
    List<SourceFile> externs = new ArrayList<SourceFile>();
    externs.add(SourceFile.fromCode("externs.js", EXTERNS));
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    compiler.compile(externs, inputs, options);
    return compiler.toSource();
  }

  private String compile(String js) {
    return compile(js, true);
  }

  // Covers static field DEFAULT_GLOBAL_NAMES content
  @Test
  public void testDefaultGlobalNames_containsWindowAndGoogGlobal() throws Throwable {
    assertTrue(NameAnalyzer.DEFAULT_GLOBAL_NAMES.contains("window"));
    assertTrue(NameAnalyzer.DEFAULT_GLOBAL_NAMES.contains("goog.global"));
  }

  // Covers getHtmlReport() with empty allNames map (constructor branch, no process())
  @Test
  public void testGetHtmlReport_freshInstance_showsZeroCounts() throws Throwable {
    Compiler compiler = new Compiler();
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("<html>"));
    assertTrue(report.contains("Total Names: 0"));
  }

  // Covers removal of a wholly unreferenced var (JsNameRefNode VAR branch)
  @Test
  public void testProcess_unusedVar_removed() throws Throwable {
    String result = compile("var unused = 1;");
    assertFalse(result.contains("unused"));
  }

  // Covers externally referenceable name (starts with "window") kept
  @Test
  public void testProcess_usedVarViaWindow_kept() throws Throwable {
    String result = compile("window.foo = function() { return 1; };");
    assertTrue(result.contains("foo"));
  }

  // Covers removal of a wholly unreferenced function declaration (FUNCTION branch)
  @Test
  public void testProcess_unusedFunction_removed() throws Throwable {
    String result = compile("function unusedFn() { return 1; }");
    assertFalse(result.contains("unusedFn"));
  }

  // Covers function kept when its result is assigned to an externally referenceable name
  @Test
  public void testProcess_functionUsedInAssignment_kept() throws Throwable {
    String result = compile(
        "function usedFn() { return 1; } window.result = usedFn();");
    assertTrue(result.contains("usedFn"));
  }

  // Covers "all or nothing" removal of prototype class when unreferenced
  @Test
  public void testProcess_unusedPrototypeClass_removed() throws Throwable {
    String result = compile(
        "function Foo() {} Foo.prototype.bar = function() { return 1; };");
    assertFalse(result.contains("bar"));
  }

  // Covers "all or nothing" preservation of prototype properties when class is referenced
  @Test
  public void testProcess_prototypeMethodsKeptWhenClassReferenced() throws Throwable {
    String result = compile(
        "function Foo() {} Foo.prototype.bar = function() { return 1; };"
        + "window.Foo = Foo;");
    assertTrue(result.contains("Foo"));
    assertTrue(result.contains("bar"));
  }

  // Covers removal of a fully unreferenced qualified-name assignment
  @Test
  public void testProcess_qualifiedNameAssignment_unused_removed() throws Throwable {
    String result = compile("var a = {}; a.b = function() { return 1; };");
    assertEquals("", result.trim());
  }

  // Covers preservation of qualified-name assignment chain when referenced externally
  @Test
  public void testProcess_qualifiedNameAssignment_usedViaWindow_kept() throws Throwable {
    String result = compile(
        "var a = {}; a.b = function() { return 1; }; window.out = a.b;");
    assertTrue(result.contains("a.b"));
  }

  // Covers removal of unused var whose initializer is an object literal
  @Test
  public void testProcess_objectLiteralKey_unused_removed() throws Throwable {
    String result = compile("var a = { b: function() { return 1; } };");
    assertEquals("", result.trim());
  }

  // Covers preservation of object literal key when referenced externally
  @Test
  public void testProcess_objectLiteralKey_usedViaWindow_kept() throws Throwable {
    String result = compile(
        "var a = { b: function() { return 1; } }; window.out = a.b;");
    assertTrue(result.contains("a.b"));
  }

  // Covers javadoc contract: "instanceof checks do not prevent removal of an unaliased name"
  @Test
  public void testProcess_instanceofOnUnaliasedClass_classRemovedCheckReplacedWithFalse()
      throws Throwable {
    String result = compile(
        "function Foo() {} window.result = (window.something instanceof Foo);");
    assertFalse(result.contains("Foo"));
    assertTrue(result.contains("false"));
  }

  // Covers class-level javadoc alias example: written descendant triggers alias->original edge
  @Test
  public void testProcess_classDocExample_aliasWithWrittenDescendant_allKept()
      throws Throwable {
    String result = compile(
        "var a = {}; var b = a; a.foo = 3; alert(b.foo);");
    assertTrue(result.contains("a.foo"));
    assertTrue(result.contains("b.foo"));
    assertTrue(result.contains("alert"));
  }

  // Covers "goog.global" being treated as externally referenceable like "window"
  @Test
  public void testProcess_googGlobalNamespace_treatedAsExternallyReferenceable()
      throws Throwable {
    String result = compile(
        "var goog = {}; goog.global = {}; "
        + "goog.global.foo = function() { return 1; };");
    assertTrue(result.contains("foo"));
  }

  // Covers removal of multiple independent unreferenced vars
  @Test
  public void testProcess_multipleUnusedVars_allRemoved() throws Throwable {
    String result = compile("var x = 1; var y = 2; var z = 3;");
    assertFalse(result.contains("x"));
    assertFalse(result.contains("y"));
    assertFalse(result.contains("z"));
  }

  // Covers class javadoc: "eliminate code containing circular references" when unreachable
  @Test
  public void testProcess_circularReference_unreachable_bothRemoved() throws Throwable {
    String result = compile(
        "function A() { return B(); } function B() { return A(); }");
    assertEquals("", result.trim());
  }

  // Covers circular reference kept when reachable from external entry point
  @Test
  public void testProcess_circularReference_reachable_bothKept() throws Throwable {
    String result = compile(
        "function A() { return B(); } function B() { return A(); } "
        + "window.entry = A();");
    assertTrue(result.contains("A"));
    assertTrue(result.contains("B"));
  }

  // Covers class javadoc example: "Foo in window['foo'] = new Foo();" marks Foo referenced
  @Test
  public void testProcess_getElemOnWindow_classDocExample_fooKept() throws Throwable {
    String result = compile("function Foo() {} window['foo'] = new Foo();");
    assertTrue(result.contains("Foo"));
  }

  // Covers CompilerOptions gate: pass not applied when smart name removal disabled
  @Test
  public void testProcess_smartNameRemovalDisabled_codeNotRemoved() throws Throwable {
    String result = compile("var unused = 1;", false);
    assertTrue(result.contains("unused"));
  }

  // Covers function expression alias assigned then referenced externally
  @Test
  public void testProcess_functionExpressionAssignedThenUsedExternally_kept()
      throws Throwable {
    String result = compile(
        "var handler = function() { return 1; }; window.onClick = handler;");
    assertTrue(result.contains("handler"));
  }

  // Covers referenceParentNames() propagation through a nested qualified name chain
  @Test
  public void testProcess_nestedQualifiedNameChain_keptWhenLeafReferenced()
      throws Throwable {
    String result = compile(
        "var a = {}; a.b = {}; a.b.c = function() { return 1; }; "
        + "window.x = a.b.c();");
    assertTrue(result.contains("a.b.c"));
  }

  // Covers process() on an empty program producing empty output
  @Test
  public void testProcess_emptyProgram_producesEmptyOutput() throws Throwable {
    String result = compile("");
    assertEquals("", result.trim());
  }

  // Covers removal of uninitialized var declaration (VAR with no RHS)
  @Test
  public void testProcess_varWithoutInitializer_unused_removed() throws Throwable {
    String result = compile("var x; window.y = 1;");
    assertFalse(result.contains("x"));
    assertTrue(result.contains("window.y"));
  }

  // Covers removal of two separate unreferenced function declarations
  @Test
  public void testProcess_twoSeparateUnusedFunctions_bothRemoved() throws Throwable {
    String result = compile("function Foo() {} function Bar() {}");
    assertEquals("", result.trim());
  }

  // Covers nested assignment chain where both sides are externally referenceable
  @Test
  public void testProcess_assignmentChain_bothSidesExternallyReferenceable_kept()
      throws Throwable {
    String result = compile("window.a = window.b = 5;");
    assertTrue(result.contains("window.a"));
    assertTrue(result.contains("window.b"));
  }

  // Covers throw statement call dependency propagated from enclosing function scope
  @Test
  public void testProcess_throwStatementWithFunctionCall_dependencyTracked()
      throws Throwable {
    String result = compile(
        "function makeError() { return 1; } function run() { throw makeError(); } "
        + "window.go = run();");
    assertTrue(result.contains("makeError"));
    assertTrue(result.contains("run"));
  }
}
