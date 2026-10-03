package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
import com.google.javascript.rhino.Node;
import com.google.common.collect.Lists;

public class FlowSensitiveInlineVariablesClaudeTest {

  // Helper: parses js, runs the pass on the script root, returns resulting source.
  private String compileAndInline(String js) throws Exception {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("externs.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("input.js", js));
    compiler.init(externs, inputs, options);
    compiler.parse();
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node jsRoot = root.getLastChild();
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    pass.process(externsRoot, jsRoot);
    return compiler.toSource();
  }

  // Covers enterScope(): global scope is skipped entirely, no inlining happens.
  @Test
  public void testEnterScope_globalScope_variableNotInlined() throws Throwable {
    String out = compileAndInline("var x = 1; print(x);");
    assertTrue(out.contains("x=1"));
    assertTrue(out.contains("print(x)"));
  }

  // Covers canInline() happy path: single def, single use, no side effects -> inlined.
  @Test
  public void testCanInline_singleUseSimpleLiteral_inlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; return x; }");
    assertTrue(out.contains("return 1"));
    assertFalse(out.contains("x=1"));
  }

  // Covers numUseWithinUseCfgNode != 1 branch: two uses in same statement block inlining.
  @Test
  public void testCanInline_multipleUsesInSameStatement_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; return x + x; }");
    assertTrue(out.contains("x=1"));
    assertTrue(out.contains("x+x"));
  }

  // Covers NodeUtil.isWithinLoop(use) branch: use inside a loop blocks inlining.
  @Test
  public void testCanInline_useWithinLoop_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; while (cond()) { print(x); } }");
    assertTrue(out.contains("x=1"));
    assertTrue(out.contains("print(x)"));
  }

  // Covers inlineVariable() ASSIGN branch: statement is fully removed after inlining.
  @Test
  public void testInlineVariable_assignDefinitionRemovesStatement_inlined() throws Throwable {
    String out = compileAndInline("function f() { var x; x = 5; return x; }");
    assertTrue(out.contains("return 5"));
    assertFalse(out.contains("x=5"));
  }

  // Covers NodeUtil.has(...) GETPROP exclusion: RHS with a property access blocks inlining.
  @Test
  public void testCanInline_getPropRhs_notInlined() throws Throwable {
    String out = compileAndInline("function f(o) { var x = o.prop; return x; }");
    assertTrue(out.contains("o.prop"));
    assertTrue(out.contains("return x"));
  }

  // Covers NodeUtil.has(...) ARRAYLIT exclusion: array literal RHS blocks inlining.
  @Test
  public void testCanInline_arrayLiteralRhs_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = [1,2]; return x; }");
    assertTrue(out.contains("[1,2]"));
    assertTrue(out.contains("return x"));
  }

  // Covers NodeUtil.has(...) OBJECTLIT exclusion: object literal RHS blocks inlining.
  @Test
  public void testCanInline_objectLiteralRhs_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = {}; return x; }");
    assertTrue(out.contains("x={}"));
    assertTrue(out.contains("return x"));
  }

  // Covers NodeUtil.has(...) NEW exclusion: constructor call RHS blocks inlining.
  @Test
  public void testCanInline_newExpressionRhs_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = new Foo(); return x; }");
    assertTrue(out.contains("new Foo"));
    assertTrue(out.contains("return x"));
  }

  // Covers NodeUtil.has(...) REGEXP exclusion: regexp literal RHS blocks inlining.
  @Test
  public void testCanInline_regexpRhs_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = /abc/; return x; }");
    assertTrue(out.contains("/abc/"));
    assertTrue(out.contains("return x"));
  }

  // Covers def.isAssign() && !isExprAssign(parent) branch: assign used as R-value not inlined.
  @Test
  public void testCanInline_assignUsedAsRValue_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x; var y = (x = 5); return x; }");
    assertTrue(out.contains("return x"));
    assertFalse(out.contains("return 5"));
  }

  // Covers two independent candidates in same function both being inlined.
  @Test
  public void testCanInline_twoIndependentVarDefs_bothInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; var y = 2; return x + y; }");
    assertTrue(out.contains("1+2"));
    assertFalse(out.contains("x=1"));
    assertFalse(out.contains("y=2"));
  }

  // Covers uses.size() != 1 branch: variable used in two separate statements blocks inlining.
  @Test
  public void testCanInline_multipleReachingUses_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; print(x); print(x); }");
    assertTrue(out.contains("x=1"));
    assertFalse(out.contains("print(1)"));
  }

  // Covers defCfgNode.isFunction() branch: a function parameter can never be inlined.
  @Test
  public void testCanInline_functionParameter_notInlined() throws Throwable {
    String out = compileAndInline("function f(a) { return a; }");
    assertTrue(out.contains("return a"));
  }

  // Covers checkLeftOf(): side effect to the left of the use in same statement blocks inlining.
  @Test
  public void testCanInline_checkLeftOfSideEffect_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; bar(), print(x); }");
    assertTrue(out.contains("x=1"));
    assertTrue(out.contains("print(x)"));
  }

  // Covers CheckPathsBetweenNodes branch: side-effecting call between def and use blocks inlining.
  @Test
  public void testCanInline_pathBetweenHasSideEffect_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; bar(); print(x); }");
    assertTrue(out.contains("x=1"));
    assertTrue(out.contains("print(x)"));
  }

  // Covers CheckPathsBetweenNodes false branch: no side effect between def and use -> inlined.
  @Test
  public void testCanInline_pathBetweenNoSideEffect_inlined() throws Throwable {
    String out = compileAndInline("function f() { var x = 1; 2; print(x); }");
    assertTrue(out.contains("print(1)"));
    assertFalse(out.contains("x=1"));
  }

  // Covers process()/enterScope() being applied independently to multiple function scopes.
  @Test
  public void testProcess_multipleFunctions_eachInlinedIndependently() throws Throwable {
    String out = compileAndInline(
        "function f() { var x = 1; return x; } function g() { var y = 2; return y; }");
    assertTrue(out.contains("return 1"));
    assertTrue(out.contains("return 2"));
  }

  // Covers NodeUtil.mayHaveSideEffects(def.getLastChild()) branch: side-effecting RHS not inlined.
  @Test
  public void testCanInline_sideEffectingCallRhs_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x = bar(); return x; }");
    assertTrue(out.contains("bar()"));
    assertTrue(out.contains("return x"));
  }

  // Covers parent.isCatch() exclusion in GatherCandiates: catch variable is not a candidate.
  @Test
  public void testProcess_catchVariable_noCrashAndNotInlined() throws Throwable {
    String out = compileAndInline(
        "function f() { try { risky(); } catch (e) { print(e); } return 1; }");
    assertTrue(out.contains("print(e)"));
    assertTrue(out.contains("return 1"));
  }

  // Covers defNode == null guard: a variable with no initializer has no reaching def to inline.
  @Test
  public void testCanInline_noInitializerVar_notInlined() throws Throwable {
    String out = compileAndInline("function f() { var x; print(x); }");
    assertTrue(out.contains("print(x)"));
  }

  // Covers process() on an empty script: traversal completes without modification or crash.
  @Test
  public void testProcess_emptyScript_producesEmptyOutput() throws Throwable {
    String out = compileAndInline("");
    assertEquals("", out.trim());
  }

  // Covers constructor + process(): pass can be constructed and run repeatedly without state leak.
  @Test
  public void testConstructor_reusablePassOnSeparateCompiles_inlinesEachIndependently() throws Throwable {
    String out1 = compileAndInline("function f() { var x = 7; return x; }");
    String out2 = compileAndInline("function g() { var z = 9; return z; }");
    assertTrue(out1.contains("return 7"));
    assertTrue(out2.contains("return 9"));
  }
}
