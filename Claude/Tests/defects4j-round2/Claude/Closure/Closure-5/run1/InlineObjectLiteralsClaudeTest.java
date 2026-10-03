package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;

import org.junit.Test;

import static org.junit.Assert.*;

public class InlineObjectLiteralsClaudeTest {

  private Compiler compiler;

  private String runPass(String js) throws Throwable {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(externs, input, options);
    Node externsRoot = compiler.getExternsRoot();
    Node jsRoot = compiler.getJsRoot();
    Supplier<String> idSupplier = new Supplier<String>() {
      private int nextId = 0;
      public String get() {
        return String.valueOf(nextId++);
      }
    };
    InlineObjectLiterals pass = new InlineObjectLiterals(compiler, idSupplier);
    pass.process(externsRoot, jsRoot);
    return compiler.toSource();
  }

  // Public constant contract check.
  @Test
  public void testVarPrefixConstant_matchesExpectedValue() throws Throwable {
    assertEquals("JSCompiler_object_inline_", InlineObjectLiterals.VAR_PREFIX);
  }

  // process() on an empty program must not throw and must produce empty output.
  @Test
  public void testProcess_emptyScript_doesNotThrowAndProducesEmptyOutput() throws Throwable {
    String out = runPass("");
    assertEquals("", out.trim());
  }

  // Object literal with two properties, only accessed via GETPROP -> should be split.
  @Test
  public void testProcess_twoPropertyObjectLiteral_splitsIntoSeparateVars() throws Throwable {
    String out = runPass("function f() { var x = {a:1, b:2}; return x.a + x.b; }");
    assertFalse(out.contains("x.a"));
    assertFalse(out.contains("x.b"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
    assertTrue(out.contains("JSCompiler_object_inline_b_1"));
  }

  // Single-property object literal is also split.
  @Test
  public void testProcess_singlePropertyObjectLiteral_splitsIntoSeparateVar() throws Throwable {
    String out = runPass("function f() { var x = {a:7}; return x.a; }");
    assertFalse(out.contains("x.a"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
    assertTrue(out.contains("7"));
  }

  // Direct full reference "x;" blocks inlining (isVarOrAssignExprLhs branch).
  @Test
  public void testProcess_directFullVariableReference_blocksInlining() throws Throwable {
    String out = runPass("function f() { var x = {a:1}; x; return x.a; }");
    assertTrue(out.contains("x.a"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // Passing the whole variable as a function argument also blocks inlining.
  @Test
  public void testProcess_variablePassedAsArgument_blocksInlining() throws Throwable {
    String out = runPass("function g(o) {} function f() { var x = {a:1}; g(x); return x.a; }");
    assertTrue(out.contains("x.a"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // x.fn() as a call target blocks inlining due to possible 'this' usage.
  @Test
  public void testProcess_methodCallOnProperty_blocksInliningDueToThisBinding() throws Throwable {
    String out = runPass("function f() { var x = {fn:function(){return 1;}}; return x.fn(); }");
    assertTrue(out.contains("x.fn()"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // Access to a property never defined on the literal (and never assigned) blocks inlining.
  @Test
  public void testProcess_accessToUndeclaredProperty_blocksInlining() throws Throwable {
    String out = runPass("function f() { var x = {a:1}; return x.a + x.c; }");
    assertTrue(out.contains("x.a"));
    assertTrue(out.contains("x.c"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // A property added via direct assignment (x.c = 5) becomes a valid, inlinable property.
  @Test
  public void testProcess_dynamicPropertyAssignmentThenRead_isInlined() throws Throwable {
    String out = runPass("function f() { var x = {a:1}; x.c = 5; return x.a + x.c; }");
    assertFalse(out.contains("x.a"));
    assertFalse(out.contains("x.c"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
    assertTrue(out.contains("JSCompiler_object_inline_c_1"));
  }

  // Self-referential assignment (x = {b: x.a}) must block inlining.
  @Test
  public void testProcess_selfReferentialObjectAssignment_blocksInlining() throws Throwable {
    String out = runPass("function f() { var x = {a:1}; x = {b: x.a}; return x.b; }");
    assertTrue(out.contains("x.b"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // Global (top-level) variables are excluded from inlining per isVarInlineForbidden.
  @Test
  public void testProcess_topLevelGlobalVariable_isNeverInlined() throws Throwable {
    String out = runPass("var x = {a:1, b:2}; var y = x.a + x.b;");
    assertTrue(out.contains("x.a"));
    assertTrue(out.contains("x.b"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // Non-object-literal assignment is never inlined (val.isObjectLit() check).
  @Test
  public void testProcess_nonObjectLiteralAssignment_isNeverInlined() throws Throwable {
    String out = runPass("function f() { var x = 5; return x; }");
    assertTrue(out.contains("var x"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // A variable that is declared but never assigned an object literal is never inlined.
  @Test
  public void testProcess_variableNeverAssignedObjectLiteral_isNeverInlined() throws Throwable {
    String out = runPass("function f() { var x; return x; }");
    assertTrue(out.contains("var x"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // "var x;" followed by a later "x = {...}" assignment still qualifies for inlining.
  @Test
  public void testProcess_declareThenAssignSeparately_stillInlinesCorrectly() throws Throwable {
    String out = runPass("function f() { var x; x = {a:1}; return x.a; }");
    assertFalse(out.contains("x.a"));
    assertFalse(out.contains("var x;"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
  }

  // The reserved RENAME_PROPERTY_FUNCTION_NAME variable must never be inlined.
  @Test
  public void testProcess_renamePropertyFunctionNameVariable_isNeverInlined() throws Throwable {
    String name = RenameProperties.RENAME_PROPERTY_FUNCTION_NAME;
    String out = runPass("function f() { var " + name + " = {a:1}; return " + name + ".a; }");
    assertTrue(out.contains(name + ".a"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // Multiple variables in the same scope: only the qualifying one is inlined.
  @Test
  public void testProcess_multipleVariablesInSameScope_onlyQualifyingOneInlined() throws Throwable {
    String out = runPass("function f() { var x = {a:1}; var y = 5; return x.a + y; }");
    assertFalse(out.contains("x.a"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
    assertTrue(out.contains("var y"));
  }

  // Object assigned conditionally in both branches of an if/else still inlines correctly.
  @Test
  public void testProcess_conditionalAssignmentInBranches_stillInlinesCorrectly() throws Throwable {
    String out = runPass(
        "function f(c) { var x; if (c) { x = {a:1}; } else { x = {a:2}; } return x.a; }");
    assertFalse(out.contains("x.a"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
  }

  // A variable local to a nested function is also a valid (non-global) inlining candidate.
  @Test
  public void testProcess_nestedFunctionLocalVariable_isInlined() throws Throwable {
    String out = runPass(
        "function outer() { function inner() { var x = {a:9}; return x.a; } return inner; }");
    assertFalse(out.contains("x.a"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
  }

  // Three properties: all keys must be split into their own variables.
  @Test
  public void testProcess_objectLiteralWithThreeProperties_allSplit() throws Throwable {
    String out = runPass("function f() { var x = {a:1, b:2, c:3}; return x.a + x.b + x.c; }");
    assertFalse(out.contains("x.a"));
    assertFalse(out.contains("x.b"));
    assertFalse(out.contains("x.c"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
    assertTrue(out.contains("JSCompiler_object_inline_b_1"));
    assertTrue(out.contains("JSCompiler_object_inline_c_2"));
  }

  // Full reassignment with the same key set still inlines and rewrites the reassignment.
  @Test
  public void testProcess_reassignmentWithSameKeys_stillInlines() throws Throwable {
    String out = runPass("function f() { var x = {a:1, b:2}; x = {a:4, b:5}; return x.a + x.b; }");
    assertFalse(out.contains("x.a"));
    assertFalse(out.contains("x.b"));
    assertFalse(out.contains("x="));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
  }

  // A declared-but-unused variable must not crash the pass and remains untouched.
  @Test
  public void testProcess_variableDeclaredWithoutInitializerNeverUsed_noCrash() throws Throwable {
    String out = runPass("function f() { var x; return 1; }");
    assertTrue(out.contains("var x"));
    assertFalse(out.contains("JSCompiler_object_inline"));
  }

  // The safeNameIdSupplier value is used verbatim to build generated variable names.
  @Test
  public void testConstructor_withCustomSupplier_usesSuppliedIds() throws Throwable {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js",
        "function f() { var x = {a:1}; return x.a; }");
    compiler.compile(externs, input, options);
    Supplier<String> idSupplier = new Supplier<String>() {
      public String get() { return "ZZZ"; }
    };
    InlineObjectLiterals pass = new InlineObjectLiterals(compiler, idSupplier);
    pass.process(compiler.getExternsRoot(), compiler.getJsRoot());
    assertTrue(compiler.toSource().contains("JSCompiler_object_inline_a_ZZZ"));
  }

  // Reassignment that drops a previously present key must still inline (undefined-fill branch).
  @Test
  public void testProcess_reassignmentMissingKeyUndefinedFill() throws Throwable {
    String out = runPass("function f() { var x = {a:1, b:2}; x = {a:3}; return x.a + x.b; }");
    assertFalse(out.contains("x.a"));
    assertFalse(out.contains("x.b"));
    assertTrue(out.contains("JSCompiler_object_inline_a_0"));
    assertTrue(out.contains("JSCompiler_object_inline_b_1"));
  }

  // A variable declared in externs must never be inlined.
  @Test
  public void testProcess_externVariable_isNeverInlined() throws Throwable {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    SourceFile externs = SourceFile.fromCode("externs.js", "var x = {a:1};");
    SourceFile input = SourceFile.fromCode("test.js", "function f() { return x.a; }");
    compiler.compile(externs, input, options);
    Supplier<String> idSupplier = new Supplier<String>() {
      public String get() { return "0"; }
    };
    InlineObjectLiterals pass = new InlineObjectLiterals(compiler, idSupplier);
    pass.process(compiler.getExternsRoot(), compiler.getJsRoot());
    assertTrue(compiler.toSource().contains("x.a"));
  }
}
