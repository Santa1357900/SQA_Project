package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import java.util.List;

public class InlineObjectLiteralsClaudeTest {

  private String runInline(String js) throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList();
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("test.js", js));
    compiler.compile(externs, inputs, options);
    Node externsRoot = compiler.getExternsRoot();
    Node jsRoot = compiler.getJsRoot();
    Supplier<String> supplier = new Supplier<String>() {
      private int counter = 0;
      public String get() {
        return String.valueOf(counter++);
      }
    };
    InlineObjectLiterals pass = new InlineObjectLiterals(compiler, supplier);
    pass.process(externsRoot, jsRoot);
    return compiler.toSource();
  }

  // Field: VAR_PREFIX constant must match the documented prefix used for generated names.
  @Test
  public void testVarPrefix_constantValue_matchesDocumentedPrefix() throws Throwable {
    assertEquals("JSCompiler_object_inline_", InlineObjectLiterals.VAR_PREFIX);
  }

  // Constructor: creating the pass with a real compiler/supplier must not throw.
  @Test
  public void testConstructor_withCompilerAndSupplier_doesNotThrow() throws Throwable {
    Compiler compiler = new Compiler();
    Supplier<String> supplier = new Supplier<String>() {
      public String get() {
        return "0";
      }
    };
    InlineObjectLiterals pass = new InlineObjectLiterals(compiler, supplier);
    assertNotNull(pass);
  }

  // process(): running on an empty program must not throw and produce empty output.
  @Test
  public void testProcess_emptyScript_producesEmptyOutput() throws Throwable {
    String output = runInline("");
    assertEquals("", output.trim());
  }

  // isVarInlineForbidden: global variables must never be inlined (var.isGlobal()).
  @Test
  public void testProcess_globalVar_isNeverInlined() throws Throwable {
    String output = runInline("var x = {a: 1}; x.a;");
    assertTrue(output.contains("x.a"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
  }

  // isVarInlineForbidden: the RENAME_PROPERTY_FUNCTION_NAME var must never be inlined.
  @Test
  public void testProcess_renamePropertyFunctionNameVar_isNeverInlined() throws Throwable {
    String name = RenameProperties.RENAME_PROPERTY_FUNCTION_NAME;
    String js = "function f() { var " + name + " = {a: 1}; return " + name + ".a; }";
    String output = runInline(js);
    assertTrue(output.contains(name + ".a"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
  }

  // isInlinableObject: a local var only ever accessed via GETPROP must be split into vars.
  @Test
  public void testProcess_localVarWithTwoProps_inlinesIntoSeparateVars() throws Throwable {
    String output = runInline("function f() { var x = {a: 1, b: 2}; return x.a + x.b; }");
    assertFalse(output.contains("x.a"));
    assertFalse(output.contains("x.b"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "b_1"));
  }

  // isVarOrAssignExprLhs: a full reference like "return x" prevents inlining.
  @Test
  public void testProcess_objectReferencedInFull_notInlined() throws Throwable {
    String output = runInline("function f() { var x = {a: 1}; return x; }");
    assertTrue(output.contains("var x"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
  }

  // isInlinableObject: a call target using the object as 'this' (x.fn()) forbids inlining.
  @Test
  public void testProcess_callTargetUsesObjectAsThis_notInlined() throws Throwable {
    String js = "function f() { var x = {a: 1, fn: function() { return this.a; }}; "
        + "return x.fn(); }";
    String output = runInline(js);
    assertTrue(output.contains("x.fn"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
  }

  // isInlinableObject: a var declared with no assignment and never assigned an object literal
  // must not be inlined.
  @Test
  public void testProcess_varWithNoAssignment_notInlined() throws Throwable {
    String output = runInline("function f() { var x; x.a; }");
    assertTrue(output.contains("var x"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
  }

  // isInlinableObject: assigning a non-object-literal value (array) forbids inlining.
  @Test
  public void testProcess_arrayLiteralAssignment_notInlined() throws Throwable {
    String output = runInline("function f() { var x = [1, 2]; return x[0]; }");
    assertTrue(output.contains("var x"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
  }

  // isInlinableObject: self-referential object literal assignment (x = {b: x.a}) forbids inlining.
  @Test
  public void testProcess_selfReferentialAssignment_notInlined() throws Throwable {
    String js = "function f() { var x = {a: 1}; x = {b: x.a}; return x.b; }";
    String output = runInline(js);
    assertTrue(output.contains("x.a"));
    assertTrue(output.contains("x.b"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
  }

  // computeVarList: repeated access to the same key must reuse a single generated variable.
  @Test
  public void testProcess_duplicatePropertyAccess_reusesSameVariable() throws Throwable {
    String output = runInline("function f() { var x = {a: 1}; return x.a + x.a; }");
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_1"));
  }

  // computeVarList: distinct keys get sequential generated ids in declaration order.
  @Test
  public void testProcess_multiplePropertiesSameOrder_generatesSequentialIds() throws Throwable {
    String js = "function f() { var x = {a: 1, b: 2, c: 3}; return x.a + x.b + x.c; }";
    String output = runInline(js);
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "b_1"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "c_2"));
  }

  // computeVarList/splitObject: two distinct inlinable vars in the same scope each get their
  // own generated prefix names.
  @Test
  public void testProcess_multipleVariablesSameScope_eachGetOwnPrefix() throws Throwable {
    String js = "function f() { var x = {a: 1}; var y = {b: 2}; return x.a + y.b; }";
    String output = runInline(js);
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "b_1"));
    assertFalse(output.contains("x.a"));
    assertFalse(output.contains("y.b"));
  }

  // afterExitScope: independent function scopes are each processed, ids continue across scopes.
  @Test
  public void testProcess_multipleFunctionScopes_eachInlinedIndependently() throws Throwable {
    String js = "function f() { var x = {a: 1}; return x.a; } "
        + "function g() { var y = {b: 2}; return y.b; }";
    String output = runInline(js);
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "b_1"));
    assertFalse(output.contains("x.a"));
    assertFalse(output.contains("y.b"));
  }

  // fillInitialValues: numeric initial value of a property must be preserved on the new var.
  @Test
  public void testProcess_initialNumericValuePreserved() throws Throwable {
    String output = runInline("function f() { var x = {a: 42}; return x.a; }");
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains("42"));
  }

  // fillInitialValues: string initial value of a property must be preserved on the new var.
  @Test
  public void testProcess_initialStringValuePreserved() throws Throwable {
    String output = runInline("function f() { var x = {a: 'hello'}; return x.a; }");
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains("hello"));
  }

  // fillInitialValues: boolean initial value of a property must be preserved on the new var.
  @Test
  public void testProcess_initialBooleanValuePreserved() throws Throwable {
    String output = runInline("function f() { var x = {a: true}; return x.a; }");
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains("true"));
  }

  // fillInitialValues: a function-valued property referenced (not called directly) is still
  // inlinable and its value preserved.
  @Test
  public void testProcess_functionPropertyValuePreserved_whenNotCalledDirectly() throws Throwable {
    String js = "function f() { var x = {a: 1, cb: function() { return 2; }}; "
        + "var r = x.cb; return x.a + (r ? 1 : 0); }";
    String output = runInline(js);
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertFalse(output.contains("x.a"));
    assertFalse(output.contains("x.cb"));
    assertTrue(output.contains("function"));
  }

  // splitObject (defined=false path): a var declared without value then assigned an object
  // literal later must still be inlined correctly.
  @Test
  public void testProcess_varDeclaredThenAssignedLater_stillInlines() throws Throwable {
    String js = "function f() { var x; x = {a: 1, b: 2}; return x.a + x.b; }";
    String output = runInline(js);
    assertFalse(output.contains("x.a"));
    assertFalse(output.contains("x.b"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "b_1"));
  }

  // replaceAssignmentExpression: keys missing from a later reassignment still get generated
  // variables (union of keys across assignments), and property access is fully eliminated.
  @Test
  public void testProcess_reassignmentWithDifferentKeys_unionKeysHandled() throws Throwable {
    String js = "function f() { var x = {a: 1}; x = {b: 2}; return x.a + x.b; }";
    String output = runInline(js);
    assertFalse(output.contains("x.a"));
    assertFalse(output.contains("x.b"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "b_1"));
  }

  // replaceAssignmentExpression: a conditional reassignment (different branch) still results
  // in property accesses being eliminated via generated variables.
  @Test
  public void testProcess_conditionalReassignment_unionKeysHandled() throws Throwable {
    String js = "function f(cond) { var x = {a: 1}; if (cond) { x = {b: 2}; } "
        + "return x.a + x.b; }";
    String output = runInline(js);
    assertFalse(output.contains("x.a"));
    assertFalse(output.contains("x.b"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "b_1"));
  }

  // splitObject: a chained property access (x.a.c) has the GETPROP for x.a replaced by the
  // generated variable while preserving the nested value and the rest of the chain.
  @Test
  public void testProcess_nestedGetPropChain_replacesWithVariable() throws Throwable {
    String output = runInline("function f() { var x = {a: {c: 1}}; return x.a.c; }");
    assertFalse(output.contains("x.a"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0.c"));
    assertTrue(output.contains("1"));
  }

  // splitObject: an object literal with no keys results in no generated variables and the
  // original declaration is entirely removed.
  @Test
  public void testProcess_emptyObjectLiteral_removesDeclarationEntirely() throws Throwable {
    String output = runInline("function f() { var x = {}; return 1; }");
    assertFalse(output.contains("var x"));
    assertFalse(output.contains(InlineObjectLiterals.VAR_PREFIX));
    assertTrue(output.contains("1"));
  }

  // splitObject: a var only ever declared with an object literal and never referenced again
  // is still split, and the original declaration disappears.
  @Test
  public void testProcess_declarationOnlyNeverUsed_stillInlines() throws Throwable {
    String output = runInline("function f() { var x = {a: 1}; }");
    assertFalse(output.contains("var x"));
    assertTrue(output.contains(InlineObjectLiterals.VAR_PREFIX + "a_0"));
    assertTrue(output.contains("1"));
  }
}
