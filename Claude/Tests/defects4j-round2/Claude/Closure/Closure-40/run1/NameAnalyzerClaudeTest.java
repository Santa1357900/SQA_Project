package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;

public class NameAnalyzerClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
  }

  private Node[] parseJs(String externsCode, String jsCode) {
    CompilerOptions options = new CompilerOptions();
    SourceFile externs = SourceFile.fromCode("externs.js", externsCode);
    SourceFile input = SourceFile.fromCode("input.js", jsCode);
    compiler.compile(externs, input, options);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();
    return new Node[] {externsRoot, mainRoot};
  }

  private boolean containsName(Node n, String name) {
    if (n.isName() && name.equals(n.getString())) {
      return true;
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (containsName(c, name)) {
        return true;
      }
    }
    return false;
  }

  // Covers DEFAULT_GLOBAL_NAMES contents: must contain "window" and "goog.global"
  @Test
  public void testDefaultGlobalNames_containsWindowAndGoogGlobal() throws Throwable {
    assertTrue(NameAnalyzer.DEFAULT_GLOBAL_NAMES.contains("window"));
    assertTrue(NameAnalyzer.DEFAULT_GLOBAL_NAMES.contains("goog.global"));
  }

  // Covers DEFAULT_GLOBAL_NAMES exact size (branch: only two default names)
  @Test
  public void testDefaultGlobalNames_sizeIsTwo() throws Throwable {
    assertEquals(2, NameAnalyzer.DEFAULT_GLOBAL_NAMES.size());
  }

  // Covers JsNameRefNode.remove() VAR branch: unreferenced var is removed
  @Test
  public void testProcess_unreferencedVar_isRemoved() throws Throwable {
    Node[] roots = parseJs("", "var a = 1;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertFalse(containsName(roots[1], "a"));
  }

  // Covers process() branch: removeUnreferenced==false skips removeUnreferenced()
  @Test
  public void testProcess_unreferencedVar_removeUnreferencedFalse_isNotRemoved() throws Throwable {
    Node[] roots = parseJs("", "var a = 1;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, false);
    analyzer.process(roots[0], roots[1]);
    assertTrue(containsName(roots[1], "a"));
  }

  // Covers removeUnreferenced() called manually after process(false)
  @Test
  public void testRemoveUnreferenced_calledManually_removesUnreferencedVar() throws Throwable {
    Node[] roots = parseJs("", "var a = 1;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, false);
    analyzer.process(roots[0], roots[1]);
    analyzer.removeUnreferenced();
    assertFalse(containsName(roots[1], "a"));
  }

  // Covers reference chain WINDOW -> window.b -> a keeping "a" referenced
  @Test
  public void testProcess_varReferencedViaWindowProperty_isKept() throws Throwable {
    Node[] roots = parseJs("", "var a = 1; window.b = a;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertTrue(containsName(roots[1], "a"));
  }

  // Covers removal of multiple independent unreferenced vars
  @Test
  public void testProcess_multipleUnreferencedVars_allRemoved() throws Throwable {
    Node[] roots = parseJs("", "var a = 1; var b = 2;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertFalse(containsName(roots[1], "a"));
    assertFalse(containsName(roots[1], "b"));
  }

  // Covers JsNameRefNode.remove() FUNCTION branch: unreferenced function removed
  @Test
  public void testProcess_unreferencedFunctionDeclaration_isRemoved() throws Throwable {
    Node[] roots = parseJs("", "function f() {}");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertFalse(containsName(roots[1], "f"));
  }

  // Covers function kept when referenced via window property assignment
  @Test
  public void testProcess_functionReferencedViaWindowProperty_isKept() throws Throwable {
    Node[] roots = parseJs("", "function f() {} window.g = f;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertTrue(containsName(roots[1], "f"));
  }

  // Covers PrototypeSetNode removal when class is unreferenced
  @Test
  public void testProcess_unreferencedPrototypeMethod_classIsRemoved() throws Throwable {
    Node[] roots = parseJs("", "function A() {} A.prototype.foo = function() {};");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertFalse(containsName(roots[1], "A"));
  }

  // Covers PrototypeSetNode kept when class is referenced via window property
  @Test
  public void testProcess_referencedPrototypeClass_isKept() throws Throwable {
    Node[] roots = parseJs("", "function A() {} A.prototype.foo = function() {}; window.b = A;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertTrue(containsName(roots[1], "A"));
  }

  // Covers special-case prototype-assign branch in createNameInformation (A.prototype = {...})
  @Test
  public void testProcess_prototypeAssignSpecialCase_unreferencedRemoved() throws Throwable {
    Node[] roots = parseJs("", "function A() {} A.prototype = {};");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertFalse(containsName(roots[1], "A"));
  }

  // Covers InstanceOfCheckNode removal when checked class is unreferenced
  @Test
  public void testProcess_instanceofOnUnreferencedClass_classRemoved() throws Throwable {
    Node[] roots = parseJs("", "function A() {} var b; if (b instanceof A) {}");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertFalse(containsName(roots[1], "A"));
  }

  // Covers calculateReferences(): window and Function names always created
  @Test
  public void testProcess_emptyProgram_onlyWindowAndFunctionCreated() throws Throwable {
    Node[] roots = parseJs("", "");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Total Names: 2"));
  }

  // Covers JsNameRefNode.remove() VAR branch Preconditions.checkState failure for multi-name var
  @Test
  public void testProcess_multiNameVarDeclaration_removalThrowsIllegalStateException() throws Throwable {
    Node[] roots = parseJs("", "var a, b;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    try {
      analyzer.process(roots[0], roots[1]);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      // expected per Preconditions.checkState(parent.hasOneChild())
    }
  }

  // Covers getHtmlReport() before process(): allNames empty, all counts are zero
  @Test
  public void testGetHtmlReport_beforeProcess_totalNamesZero() throws Throwable {
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Total Names: 0"));
  }

  // Covers getHtmlReport() after processing empty program: two names (window, Function)
  @Test
  public void testGetHtmlReport_emptyProgramAfterProcess_totalNamesTwo() throws Throwable {
    Node[] roots = parseJs("", "");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Total Names: 2"));
  }

  // Covers countOf(TriState.TRUE, BOTH): class detection via non-empty prototypeNames
  @Test
  public void testGetHtmlReport_withPrototypeClass_totalClassesOne() throws Throwable {
    Node[] roots = parseJs("", "function A() {} A.prototype.foo = function() {};");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Total Classes: 1"));
  }

  // Covers countOf(TriState.FALSE, BOTH): static (non-class) names are window and Function
  @Test
  public void testGetHtmlReport_withPrototypeClass_staticFunctionsCountsWindowAndFunction() throws Throwable {
    Node[] roots = parseJs("", "function A() {} A.prototype.foo = function() {};");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Total Static Functions: 2"));
  }

  // Covers countOf(BOTH, TRUE): window and Function are always marked referenced
  @Test
  public void testGetHtmlReport_withUnreferencedClass_referencedNamesCountsTwo() throws Throwable {
    Node[] roots = parseJs("", "function A() {} A.prototype.foo = function() {};");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Referenced Names: 2"));
  }

  // Covers overall HTML structure: starts/ends with fixed literal tags from getHtmlReport()
  @Test
  public void testGetHtmlReport_structureStartsAndEndsWithHtmlTags() throws Throwable {
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    String report = analyzer.getHtmlReport();
    assertTrue(report.startsWith("<html>"));
    assertTrue(report.endsWith("</body></html>"));
  }

  // Covers the "ALL NAMES" section header literal being present in report
  @Test
  public void testGetHtmlReport_containsAllNamesSection() throws Throwable {
    Node[] roots = parseJs("", "var a = 1;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("ALL NAMES"));
  }

  // Covers ProcessExternals: names declared in externs are externallyDefined and excluded from countOf
  @Test
  public void testProcess_externallyDefinedVarInExterns_excludedFromTotalNames() throws Throwable {
    Node[] roots = parseJs("var extVar;", "");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Total Names: 2"));
  }

  // Covers isObjectLitKey handling: object literal var kept when referenced via window
  @Test
  public void testProcess_objectLiteralVarReferencedViaWindow_isKept() throws Throwable {
    Node[] roots = parseJs("", "var ns = {foo: 1}; window.x = ns;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertTrue(containsName(roots[1], "ns"));
  }

  // Covers removeUnreferenced() no-op when refNode's name is externallyDefined
  @Test
  public void testProcess_externallyDefinedFunctionNotRemovedEvenIfUnreferenced() throws Throwable {
    Node[] roots = parseJs("function extFn() {}", "");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, true);
    analyzer.process(roots[0], roots[1]);
    assertFalse(containsName(roots[1], "extFn"));
  }

  // Covers process() with removeUnreferenced=false still computing a usable report
  @Test
  public void testProcess_removeUnreferencedFalse_htmlReportStillComputesCounts() throws Throwable {
    Node[] roots = parseJs("", "var a = 1;");
    NameAnalyzer analyzer = new NameAnalyzer(compiler, false);
    analyzer.process(roots[0], roots[1]);
    String report = analyzer.getHtmlReport();
    assertTrue(report.contains("Total Names: 3"));
  }
}
