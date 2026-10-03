package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

import org.junit.Test;
import static org.junit.Assert.*;

public class InlineVariablesClaudeTest {

  private String inlineJs(String js, InlineVariables.Mode mode,
      boolean inlineAllStrings) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("input.js", js),
        options);
    Node externsRoot = compiler.getRoot().getFirstChild();
    Node mainRoot = externsRoot.getNext();
    InlineVariables pass = new InlineVariables(compiler, mode, inlineAllStrings);
    pass.process(externsRoot, mainRoot);
    return compiler.toSource();
  }

  // Mode enum must expose exactly the three documented constants.
  @Test
  public void testModeEnum_hasExpectedConstants() throws Throwable {
    assertEquals(3, InlineVariables.Mode.values().length);
  }

  // Mode.valueOf resolves ALL, used by getFilterForMode's ALL case.
  @Test
  public void testModeEnum_valueOfAll() throws Throwable {
    assertEquals(InlineVariables.Mode.ALL, InlineVariables.Mode.valueOf("ALL"));
  }

  // Mode.valueOf resolves CONSTANTS_ONLY, used by getFilterForMode's switch.
  @Test
  public void testModeEnum_valueOfConstantsOnly() throws Throwable {
    assertEquals(InlineVariables.Mode.CONSTANTS_ONLY,
        InlineVariables.Mode.valueOf("CONSTANTS_ONLY"));
  }

  // Mode.valueOf resolves LOCALS_ONLY, used by getFilterForMode's switch.
  @Test
  public void testModeEnum_valueOfLocalsOnly() throws Throwable {
    assertEquals(InlineVariables.Mode.LOCALS_ONLY,
        InlineVariables.Mode.valueOf("LOCALS_ONLY"));
  }

  // Single read of an immutable literal: refCount==firstRefAfterInit branch inlines it.
  @Test
  public void testProcess_singleImmutableReference_inlinesValue() throws Throwable {
    String out = inlineJs("var x = 1; foo(x);", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("foo(1)"));
    assertFalse(out.contains("var x"));
  }

  // Multiple reads of an immutable literal: isImmutableAndWellDefinedVariable branch.
  @Test
  public void testProcess_multipleImmutableReferences_inlinesAllOccurrences() throws Throwable {
    String out = inlineJs("var x = 1; foo(x); bar(x);", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("foo(1)"));
    assertTrue(out.contains("bar(1)"));
    assertFalse(out.contains("var x"));
  }

  // Variable never read: none of the inlining branches match, declaration untouched.
  @Test
  public void testProcess_unusedVariable_notTouched() throws Throwable {
    String out = inlineJs("var x = 1;", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("var x"));
  }

  // Variable reassigned before use must not be inlined with a stale value.
  @Test
  public void testProcess_reassignedVariable_notInlinedWithStaleValue() throws Throwable {
    String out = inlineJs("var x = 1; x = 2; foo(x);", InlineVariables.Mode.ALL, false);
    assertFalse(out.contains("foo(1)"));
    assertFalse(out.contains("foo(2)"));
    assertTrue(out.contains("foo(x)"));
  }

  // CONSTANTS_ONLY mode: non-constant var has no reference info, continue branch keeps it.
  @Test
  public void testProcess_constantsOnlyMode_doesNotInlineNonConstant() throws Throwable {
    String out = inlineJs("var x = 1; foo(x); foo(x);",
        InlineVariables.Mode.CONSTANTS_ONLY, false);
    assertTrue(out.contains("var x"));
    assertTrue(out.contains("foo(x)"));
  }

  // LOCALS_ONLY mode: IdentifyLocals.apply true for a function-local var, so it is inlined.
  @Test
  public void testProcess_localsOnlyMode_inlinesLocalVariable() throws Throwable {
    String out = inlineJs("function f() { var x = 1; return x; }",
        InlineVariables.Mode.LOCALS_ONLY, false);
    assertTrue(out.contains("return 1"));
    assertFalse(out.contains("var x"));
  }

  // LOCALS_ONLY mode: IdentifyLocals.apply false for a global var, so it is left alone.
  @Test
  public void testProcess_localsOnlyMode_doesNotInlineGlobalVariable() throws Throwable {
    String out = inlineJs("var x = 1; foo(x);", InlineVariables.Mode.LOCALS_ONLY, false);
    assertTrue(out.contains("var x"));
    assertTrue(out.contains("foo(x)"));
  }

  // GETPROP value used directly as a call target must not be inlined (changes "this").
  @Test
  public void testProcess_valueUsedAsCallTarget_getpropNotInlinedIntoCallPosition()
      throws Throwable {
    String out = inlineJs("var a = b.c; a();", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("var a"));
    assertTrue(out.contains("a()"));
  }

  // GETPROP value used as a plain argument is safe to inline.
  @Test
  public void testProcess_valueUsedAsArgument_getpropInlinedWhenNotCallTarget()
      throws Throwable {
    String out = inlineJs("var a = b.c; f(a);", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("f(b.c)"));
    assertFalse(out.contains("var a"));
  }

  // A function expression value can be moved aggressively into an argument position.
  @Test
  public void testProcess_functionExpressionValue_inlinedAsArgument() throws Throwable {
    String out = inlineJs("var fn = function() { return 1; }; foo(fn);",
        InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("foo(function"));
    assertFalse(out.contains("var fn"));
  }

  // inlineAllStrings=true forces isStringWorthInlining to always return true.
  @Test
  public void testProcess_stringInlineAllStringsTrue_alwaysInlines() throws Throwable {
    String js = "var s = 'abcdefghijabcdefghijabcdefghijabcdefghijabcdefghij';"
        + " foo(s); bar(s); baz(s);";
    String out = inlineJs(js, InlineVariables.Mode.ALL, true);
    assertFalse(out.contains("var s"));
  }

  // Long string used many times: byte heuristic favors keeping the declaration.
  @Test
  public void testProcess_stringInlineAllStringsFalse_longStringMultipleRefs_notInlined()
      throws Throwable {
    String js = "var s = 'abcdefghijabcdefghijabcdefghijabcdefghijabcdefghij';"
        + " foo(s); bar(s); baz(s); qux(s);";
    String out = inlineJs(js, InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("var s"));
  }

  // Short string with few references: byte heuristic favors inlining.
  @Test
  public void testProcess_stringInlineAllStringsFalse_shortStringFewRefs_inlined()
      throws Throwable {
    String out = inlineJs("var s = 'ok'; foo(s); bar(s);", InlineVariables.Mode.ALL, false);
    assertFalse(out.contains("var s"));
  }

  // isVarInlineForbidden: RENAME_PROPERTY_FUNCTION_NAME must never be inlined.
  @Test
  public void testProcess_renamePropertyFunctionName_neverInlined() throws Throwable {
    String name = RenameProperties.RENAME_PROPERTY_FUNCTION_NAME;
    String js = "var " + name + " = 1; foo(" + name + ");";
    String out = inlineJs(js, InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("var " + name));
  }

  // Declaration separate from initialization, single later read: both removed, value inlined.
  @Test
  public void testProcess_declarationSeparateFromInit_singleRead_inlines() throws Throwable {
    String out = inlineJs("var x; x = 1; foo(x);", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("foo(1)"));
    assertFalse(out.contains("var x"));
  }

  // Declaration separate from initialization with no further read: declaration is removed.
  @Test
  public void testProcess_declarationSeparateFromInit_noFurtherRead_removesDeclaration()
      throws Throwable {
    String out = inlineJs("var x; x = 1;", InlineVariables.Mode.ALL, false);
    assertFalse(out.contains("var x"));
  }

  // Immutable literal can be inlined across a control-structure boundary (pure value).
  @Test
  public void testProcess_immutableValueAcrossControlBlock_stillInlines() throws Throwable {
    String out = inlineJs("var x = 1; if (cond) { foo(x); }", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("foo(1)"));
    assertFalse(out.contains("var x"));
  }

  // Non-literal single-reference value in a different basic block must not be inlined.
  @Test
  public void testProcess_nonLiteralCrossBasicBlock_notInlined() throws Throwable {
    String out = inlineJs("var a = b.c; if (cond) { f(a); }", InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("var a"));
    assertFalse(out.contains("f(b.c)"));
  }

  // isValidDeclaration excludes FOR-loop declarations, so the loop variable stays.
  @Test
  public void testProcess_forLoopDeclaration_notInlined() throws Throwable {
    String out = inlineJs("for (var i = 0; i < 10; i++) { foo(i); }",
        InlineVariables.Mode.ALL, false);
    assertTrue(out.contains("var i"));
  }

  // A never-assigned variable read multiple times still has its declaration removed.
  @Test
  public void testProcess_neverAssignedVariable_multipleReads_removesDeclaration()
      throws Throwable {
    String out = inlineJs("var x; foo(x); bar(x);", InlineVariables.Mode.ALL, false);
    assertFalse(out.contains("var x"));
  }

  // A function declaration used exactly once is inlined and the declaration fully removed.
  @Test
  public void testProcess_functionDeclarationSingleUse_inlinedAndRemoved() throws Throwable {
    String out = inlineJs("function myFunc() { return 1; } g(myFunc);",
        InlineVariables.Mode.ALL, false);
    assertFalse(out.contains("myFunc"));
    assertTrue(out.contains("g(function"));
  }

  // Empty program: zero scope variables, process() must not throw.
  @Test
  public void testProcess_emptyProgram_noException() throws Throwable {
    String out = inlineJs("", InlineVariables.Mode.ALL, false);
    assertNotNull(out);
  }
}
