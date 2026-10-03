package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;

public class CheckAccessControlsClaudeTest {

  private Compiler compileOnly(String js) throws Throwable {
    Compiler comp = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", js);
    comp.compile(externs, input, options);
    return comp;
  }

  private Compiler runPass(String js) throws Throwable {
    Compiler comp = compileOnly(js);
    Node root = comp.getRoot();
    CheckAccessControls pass = new CheckAccessControls(comp);
    pass.process(root.getFirstChild(), root.getLastChild());
    return comp;
  }

  private Compiler runPassHotSwap(String js) throws Throwable {
    Compiler comp = compileOnly(js);
    Node root = comp.getRoot();
    CheckAccessControls pass = new CheckAccessControls(comp);
    pass.hotSwapScript(root.getLastChild());
    return comp;
  }

  private boolean anyWarningContains(JSError[] warnings, String substr) {
    for (int i = 0; i < warnings.length; i++) {
      if (warnings[i].toString().contains(substr)) {
        return true;
      }
    }
    return false;
  }

  // shouldTraverse must unconditionally return true, even with null traversal/parent.
  @Test
  public void testShouldTraverse_withNullArguments_returnsTrue() throws Throwable {
    Compiler comp = compileOnly("");
    CheckAccessControls pass = new CheckAccessControls(comp);
    assertTrue(pass.shouldTraverse(null, IR.block(), null));
  }

  // shouldTraverse returns true for a real Node too (no branch depends on args).
  @Test
  public void testShouldTraverse_withRealNodeArgument_returnsTrue() throws Throwable {
    Compiler comp = compileOnly("");
    CheckAccessControls pass = new CheckAccessControls(comp);
    Node n = IR.name("x");
    assertTrue(pass.shouldTraverse(null, n, n));
  }

  // checkConstantProperty: reassigning an @const instance property must warn.
  @Test
  public void testConstReassignment_sameConstructorInstanceProperty_warns() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n this.x = 2;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(warnings.length >= 1);
  }

  // checkConstantProperty: only a single assignment to an @const property is legal.
  @Test
  public void testConstProperty_singleAssignmentOnly_noWarning() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertEquals(0, warnings.length);
  }

  // checkConstantProperty: a non-const property can be reassigned freely.
  @Test
  public void testNonConstProperty_reassignedTwice_noWarning() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " this.x = 1;\n this.x = 2;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertEquals(0, warnings.length);
  }

  // Reading a property on the RHS of an assignment is not a reassignment target.
  @Test
  public void testConstProperty_readAccessOnRhsNotTreatedAsReassignment_noWarning() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n this.y = this.x;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertEquals(0, warnings.length);
  }

  // checkConstantProperty: the increment operator counts as a reassignment.
  @Test
  public void testConstProperty_incrementOperator_treatedAsReassignment_warns() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n this.x++;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(warnings.length >= 1);
  }

  // checkConstantProperty: the decrement operator counts as a reassignment.
  @Test
  public void testConstProperty_decrementOperator_treatedAsReassignment_warns() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n this.x--;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(warnings.length >= 1);
  }

  // checkConstantProperty: a compound assignment operator counts as a reassignment.
  @Test
  public void testConstProperty_compoundAssignmentOperator_treatedAsReassignment_warns() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n }\n"
        + "function f() {\n var a = new Foo();\n a.x += 2;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(warnings.length >= 1);
  }

  // Different property names must not cross-contaminate the reassignment map.
  @Test
  public void testConstProperty_differentPropertyNames_warnsOnlyForReassignedOne() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.foo = 1;\n"
        + " /** @const */ this.bar = 1;\n"
        + " this.bar = 2;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(warnings.length >= 1);
    assertFalse(anyWarningContains(warnings, "foo"));
  }

  // The warning message must mention the reassigned property's name.
  @Test
  public void testConstProperty_warningDescribesReassignedPropertyName_warns() throws Throwable {
    String js = "/** @constructor */\n function Qux() {\n"
        + " /** @const */ this.qux = 1;\n this.qux = 2;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(anyWarningContains(warnings, "qux"));
  }

  // Tracking is per-type: a second instance reassigning the same const field also warns.
  @Test
  public void testConstProperty_multipleInstancesShareSameType_reassignmentDetected_warns() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n }\n"
        + "function f() {\n var a = new Foo();\n var b = new Foo();\n b.x = 2;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(warnings.length >= 1);
  }

  // Only the constructor's own assignment occurs; later reads must not warn.
  @Test
  public void testConstProperty_onlyConstructorAssignment_noWarning() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n }\n"
        + "function f() {\n var a = new Foo();\n return a.x;\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertEquals(0, warnings.length);
  }

  // Control flow (if-branch) does not prevent detection of a reassignment.
  @Test
  public void testConstProperty_assignmentInsideIfBranch_stillDetectsReassignment_warns() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n }\n"
        + "function f(cond) {\n var a = new Foo();\n if (cond) {\n a.x = 2;\n }\n }";
    JSError[] warnings = runPass(js).getWarnings();
    assertTrue(warnings.length >= 1);
  }

  // Deprecated variable usage with a reason must never surface as a compile error.
  @Test
  public void testDeprecatedNameWithReason_noCompileError() throws Throwable {
    String js = "/** @deprecated Use something else. */\n var foo = 1;\n"
        + "function f() { return foo; }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Deprecated variable usage without a reason must never surface as a compile error.
  @Test
  public void testDeprecatedNameWithoutReason_noCompileError() throws Throwable {
    String js = "/** @deprecated */\n var foo = 1;\n"
        + "function f() { return foo; }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Deprecated class instantiation with a reason must never surface as a compile error.
  @Test
  public void testDeprecatedClassWithReason_noCompileError() throws Throwable {
    String js = "/** @deprecated Use Bar instead.\n * @constructor */\n function Foo() {}\n"
        + "function f() { return new Foo(); }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Deprecated class instantiation without a reason must never surface as a compile error.
  @Test
  public void testDeprecatedClassWithoutReason_noCompileError() throws Throwable {
    String js = "/** @deprecated\n * @constructor */\n function Foo() {}\n"
        + "function f() { return new Foo(); }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Deprecated property access with a reason must never surface as a compile error.
  @Test
  public void testDeprecatedPropertyWithReason_noCompileError() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @deprecated Use y instead. */\n this.x = 1;\n }\n"
        + "function f() {\n var a = new Foo();\n return a.x;\n }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Deprecated property access without a reason must never surface as a compile error.
  @Test
  public void testDeprecatedPropertyWithoutReason_noCompileError() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @deprecated */\n this.x = 1;\n }\n"
        + "function f() {\n var a = new Foo();\n return a.x;\n }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Private property access from an outer scope must never surface as a compile error.
  @Test
  public void testPrivatePropertyAccessFromOutside_noCompileError() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @private */\n this.secret_ = 1;\n }\n"
        + "function f() {\n var foo = new Foo();\n return foo.secret_;\n }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Protected property access from outside the hierarchy must never surface as a compile error.
  @Test
  public void testProtectedPropertyAccessFromOutside_noCompileError() throws Throwable {
    String js = "/** @constructor */\n function Foo() {}\n"
        + "/** @protected */\n Foo.prototype.bar = 1;\n"
        + "function f() {\n var foo = new Foo();\n return foo.bar;\n }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Private global variable access must never surface as a compile error.
  @Test
  public void testPrivateNameAccess_noCompileError() throws Throwable {
    String js = "/** @private */\n var secret = 1;\n"
        + "function f() { return secret; }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Calling a private constructor with 'new' is a special-cased legality check; never an error.
  @Test
  public void testPrivateConstructorAccessedViaNew_noCompileError() throws Throwable {
    String js = "/** @private\n * @constructor */\n function Secret_() {}\n"
        + "function f() { return new Secret_(); }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Exercises enterScope/exitScope and getClassOfMethod for a prototype method definition.
  @Test
  public void testMethodScopeTraversal_prototypeMethodDefinition_noCompileError() throws Throwable {
    String js = "/** @constructor */\n function Foo() {}\n"
        + "Foo.prototype.bar = function() {\n return 1;\n };\n"
        + "function g() {\n var f = new Foo();\n return f.bar();\n }";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // Exercises deprecatedDepth tracking: a deprecated class's own method body accesses a deprecated name.
  @Test
  public void testDeprecatedMethodBodyCanAccessDeprecatedName_noCompileError() throws Throwable {
    String js = "/** @deprecated */\n var oldVar = 1;\n"
        + "/** @deprecated\n * @constructor */\n function Foo() {}\n"
        + "Foo.prototype.useOld = function() {\n return oldVar;\n };";
    JSError[] errors = runPass(js).getErrors();
    assertEquals(0, errors.length);
  }

  // hotSwapScript must perform the same traversal/checks as process().
  @Test
  public void testHotSwapScript_detectsConstReassignment_warns() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + " /** @const */ this.x = 1;\n this.x = 2;\n }";
    JSError[] warnings = runPassHotSwap(js).getWarnings();
    assertTrue(warnings.length >= 1);
  }

  // An empty program must produce no warnings and no errors.
  @Test
  public void testProcess_withEmptyScript_noWarnings() throws Throwable {
    Compiler comp = runPass("");
    assertEquals(0, comp.getWarnings().length);
    assertEquals(0, comp.getErrors().length);
  }
}
