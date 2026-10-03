package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import java.util.List;

public class FlowSensitiveInlineVariablesClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
  }

  private String compileAndGetSource(String js) throws Throwable {
    CompilerOptions options = new CompilerOptions();
    options.inlineVariables = true;
    List<SourceFile> externs = Lists.newArrayList();
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("input.js", js));
    compiler.compile(externs, inputs, options);
    return compiler.toSource();
  }

  private String stripped(String s) {
    return s.replaceAll("\\s+", "");
  }

  // Covers the public constructor of FlowSensitiveInlineVariables.
  @Test
  public void testConstructor_withRealCompiler_doesNotThrow() throws Throwable {
    FlowSensitiveInlineVariables pass = new FlowSensitiveInlineVariables(compiler);
    assertNotNull(pass);
  }



  // Covers the main success path: single def, single use, no side effects -> inlined.
  @Test
  public void testProcess_simpleLocalVariable_inlinedToUse() throws Throwable {
    String js = "function f(){var x=1;return x;}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("return1"));
  }

  // Covers inlineVariable() assignment branch: def.isAssign() and whole statement removed.
  @Test
  public void testProcess_assignmentDefinition_inlinedAndStatementRemoved() throws Throwable {
    String js = "function f(){var x;x=2;return x;}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("return2"));
    assertFalse(out.contains("x=2"));
  }



  // Covers getDefCfgNode().isFunction() branch: parameters cannot be inlined.
  @Test
  public void testProcess_parameterUse_notInlined() throws Throwable {
    String js = "function f(a){return a;}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("returna"));
  }



  // Covers NodeUtil.mayHaveSideEffects(def.getLastChild()) branch via comma expr rhs.
  @Test
  public void testProcess_rhsHasSideEffectViaComma_notInlined() throws Throwable {
    String js = "function s(c){return c;} function printer(v){return v;} "
        + "function f(b){var x=(s(b),5);printer(x);}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("printer(x)"));
    assertFalse(out.contains("printer(5)"));
  }

  // Covers NodeUtil.mayHaveSideEffects branch via direct call as rhs.
  @Test
  public void testProcess_rhsHasSideEffectViaCall_notInlined() throws Throwable {
    String js = "function make(){return 5;} function printer(v){return v;} "
        + "function f(){var x=make();printer(x);}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("printer(x)"));
    assertFalse(out.contains("printer(5)"));
  }













  // Covers Candidate.getDefinition() failing to find a def (x++ is not a NAME-with-children
  // or ASSIGN def), forcing canInline() to return false via def == null.
  @Test
  public void testProcess_defFromIncrement_notInlined() throws Throwable {
    String js = "function f(){var x=1;x++;return x;}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("returnx"));
  }





  // Covers CheckPathsBetweenNodes branch with NO side effect between def and use:
  // inlining is allowed across a pure intermediate statement.
  @Test
  public void testProcess_noSideEffectBetweenDefAndUse_inlined() throws Throwable {
    String js = "function printer(v){return v;} function f(){var x=1;var y=2;printer(x);}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("printer(1)"));
  }

  // Covers the adjacent-statement case where getDefCfgNode().getNext() == useCfgNode,
  // so the path check is skipped and inlining proceeds directly.
  @Test
  public void testProcess_adjacentDefAndUse_inlinedWithoutPathCheck() throws Throwable {
    String js = "function printer(v){return v;} function f(){var x=7;printer(x);}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("printer(7)"));
  }



  // Covers the catch-parameter scenario: a caught exception variable is not inlined.
  @Test
  public void testProcess_catchVariable_notInlined() throws Throwable {
    String js = "function f(){try{throw 1;}catch(e){return e;}}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("catch(e)"));
    assertTrue(out.contains("returne"));
  }

  // Covers process() with multiple independent function scopes, each analyzed separately.
  @Test
  public void testProcess_multipleFunctionScopes_eachInlinedIndependently() throws Throwable {
    String js = "function a(){var x=1;return x;} function b(){var y=2;return y;}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("return1"));
    assertTrue(out.contains("return2"));
  }

  // Covers an empty function body: candidates list stays empty, no crash, process() is a
  // no-op for this scope.
  @Test
  public void testProcess_emptyFunctionBody_doesNotThrowAndKeepsSource() throws Throwable {
    String js = "function f(){}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("functionf()"));
  }



  // Companion check for the same bug: a side effect strictly to the right of the def
  // node's own level (within the def's statement) must also block inlining, verifying
  // the def-side counterpart (checkRightOf) still behaves per contract for var defs
  // whose right-hand side directly embeds a side effecting call as the def's rhs.
  @Test
  public void testProcess_sideEffectInDefRhsCommaExpr_notInlined() throws Throwable {
    String js = "function se(b){return b;} function printer(v){return v;} "
        + "function f(b){var x=(5,se(b));printer(x);}";
    String out = stripped(compileAndGetSource(js));
    assertTrue(out.contains("printer(x)"));
    assertFalse(out.contains("printer(5)"));
  }
}
