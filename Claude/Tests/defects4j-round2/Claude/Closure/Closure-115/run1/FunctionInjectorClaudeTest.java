package com.google.javascript.jscomp;

import com.google.javascript.jscomp.FunctionInjector.CanInlineResult;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.common.base.Supplier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

public class FunctionInjectorClaudeTest {

  private Compiler compiler;

  private Node parse(String js) {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.compile(
        SourceFile.fromCode("externs.js", ""),
        SourceFile.fromCode("test.js", js),
        options);
    return compiler.getRoot();
  }

  private Supplier<String> newIdSupplier() {
    return new Supplier<String>() {
      private int id = 0;
      public String get() {
        return "$inline$" + (id++);
      }
    };
  }

  private FunctionInjector newInjector(
      boolean allowDecomposition, boolean assumeStrictThis,
      boolean assumeMinimumCapture) {
    return new FunctionInjector(
        compiler, newIdSupplier(), allowDecomposition,
        assumeStrictThis, assumeMinimumCapture);
  }

  private Node findNodeOfType(Node n, int type) {
    if (n.getType() == type) {
      return n;
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      Node found = findNodeOfType(c, type);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private Node findFunctionNamed(Node n, String name) {
    if (n.getType() == Token.FUNCTION) {
      Node nameNode = n.getFirstChild();
      if (nameNode != null && nameNode.isName()
          && name.equals(nameNode.getString())) {
        return n;
      }
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      Node found = findFunctionNamed(c, name);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private Node findCallTo(Node n, String calleeName) {
    if (n.getType() == Token.CALL) {
      Node callee = n.getFirstChild();
      if (callee != null && callee.isName()
          && calleeName.equals(callee.getString())) {
        return n;
      }
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      Node found = findCallTo(c, calleeName);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private CanInlineResult runCanInline(
      final Node fnNode, final Node callNode, final Set<String> needAliases,
      final InliningMode mode, final boolean referencesThis,
      final boolean containsFunctions, final FunctionInjector injector) {
    final CanInlineResult[] holder = new CanInlineResult[1];
    NodeTraversal.traverse(compiler, compiler.getRoot(),
        new NodeTraversal.Callback() {
      public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        return true;
      }
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n == callNode) {
          holder[0] = injector.canInlineReferenceToFunction(
              t, callNode, fnNode, needAliases, mode,
              referencesThis, containsFunctions);
        }
      }
    });
    return holder[0];
  }

  // Constructor: null compiler must throw NullPointerException (Preconditions.checkNotNull).
  @Test
  public void testConstructor_nullCompiler_throwsNullPointerException() throws Throwable {
    try {
      new FunctionInjector(null, newIdSupplier(), false, false, false);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Constructor: null safeNameIdSupplier must throw NullPointerException.
  @Test
  public void testConstructor_nullSupplier_throwsNullPointerException() throws Throwable {
    parse("var x = 1;");
    try {
      new FunctionInjector(compiler, null, false, false, false);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // setKnownConstants: second call must throw IllegalStateException (Preconditions.checkState).
  @Test
  public void testSetKnownConstants_calledTwice_throwsIllegalStateException() throws Throwable {
    parse("var x = 1;");
    FunctionInjector injector = newInjector(false, false, false);
    injector.setKnownConstants(new HashSet<String>());
    try {
      injector.setKnownConstants(new HashSet<String>());
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // doesFunctionMeetMinimumRequirements: plain function, no arguments/eval/self-reference -> true.
  @Test
  public void testDoesFunctionMeetMinimumRequirements_simpleFunction_returnsTrue() throws Throwable {
    Node root = parse("function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertTrue(injector.doesFunctionMeetMinimumRequirements("foo", fn));
  }

  // doesFunctionMeetMinimumRequirements: references "arguments" directly -> false.
  @Test
  public void testDoesFunctionMeetMinimumRequirements_referencesArguments_returnsFalse() throws Throwable {
    Node root = parse("function foo(a) { return arguments[0]; }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertFalse(injector.doesFunctionMeetMinimumRequirements("foo", fn));
  }

  // doesFunctionMeetMinimumRequirements: references "eval" -> false.
  @Test
  public void testDoesFunctionMeetMinimumRequirements_referencesEval_returnsFalse() throws Throwable {
    Node root = parse("function foo(a) { return eval(a); }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertFalse(injector.doesFunctionMeetMinimumRequirements("foo", fn));
  }

  // doesFunctionMeetMinimumRequirements: self-recursive call to its own declared name -> false.
  @Test
  public void testDoesFunctionMeetMinimumRequirements_referencesRecursiveName_returnsFalse() throws Throwable {
    Node root = parse("function foo(a) { return foo(a); }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertFalse(injector.doesFunctionMeetMinimumRequirements("foo", fn));
  }

  // doesFunctionMeetMinimumRequirements: body references the externally supplied fnName -> false.
  @Test
  public void testDoesFunctionMeetMinimumRequirements_referencesFnNameParam_returnsFalse() throws Throwable {
    Node root = parse("function bar(a) { return baz; }");
    Node fn = findFunctionNamed(root, "bar");
    FunctionInjector injector = newInjector(false, false, false);
    assertFalse(injector.doesFunctionMeetMinimumRequirements("baz", fn));
  }

  // doesFunctionMeetMinimumRequirements: anonymous function (empty recursion name) with no conflicts -> true.
  @Test
  public void testDoesFunctionMeetMinimumRequirements_anonymousFunction_returnsTrue() throws Throwable {
    Node root = parse("var foo = function(a) { return a; };");
    Node fn = findNodeOfType(root, Token.FUNCTION);
    FunctionInjector injector = newInjector(false, false, false);
    assertTrue(injector.doesFunctionMeetMinimumRequirements("foo", fn));
  }

  // isDirectCallNodeReplacementPossible: empty function body -> true (special cased).
  @Test
  public void testIsDirectCallNodeReplacementPossible_emptyBody_returnsTrue() throws Throwable {
    Node root = parse("function foo() {}");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertTrue(injector.isDirectCallNodeReplacementPossible(fn));
  }

  // isDirectCallNodeReplacementPossible: single return with an expression -> true.
  @Test
  public void testIsDirectCallNodeReplacementPossible_singleReturnWithValue_returnsTrue() throws Throwable {
    Node root = parse("function foo() { return 1; }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertTrue(injector.isDirectCallNodeReplacementPossible(fn));
  }

  // isDirectCallNodeReplacementPossible: single return without an expression -> false.
  @Test
  public void testIsDirectCallNodeReplacementPossible_singleReturnNoValue_returnsFalse() throws Throwable {
    Node root = parse("function foo() { return; }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertFalse(injector.isDirectCallNodeReplacementPossible(fn));
  }

  // isDirectCallNodeReplacementPossible: more than one statement in body -> false.
  @Test
  public void testIsDirectCallNodeReplacementPossible_multipleStatements_returnsFalse() throws Throwable {
    Node root = parse("function foo() { var a = 1; return a; }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    assertFalse(injector.isDirectCallNodeReplacementPossible(fn));
  }

  // maybePrepareCall: simple call statement classifies as SIMPLE_CALL -> no rewrite needed.
  @Test
  public void testMaybePrepareCall_simpleCall_leavesCallAttached() throws Throwable {
    Node root = parse("foo(1); function foo(a) { return a; }");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    injector.maybePrepareCall(call);
    assertNotNull(call.getParent());
  }

  // maybePrepareCall: simple assignment classifies as SIMPLE_ASSIGNMENT -> no rewrite needed.
  @Test
  public void testMaybePrepareCall_simpleAssignment_leavesCallAttached() throws Throwable {
    Node root = parse("var x; x = foo(1); function foo(a) { return a; }");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    injector.maybePrepareCall(call);
    assertNotNull(call.getParent());
  }

  // maybePrepareCall: var declaration classifies as VAR_DECL_SIMPLE_ASSIGNMENT -> no rewrite needed.
  @Test
  public void testMaybePrepareCall_varDeclAssignment_leavesCallAttached() throws Throwable {
    Node root = parse("var x = foo(1); function foo(a) { return a; }");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    injector.maybePrepareCall(call);
    assertNotNull(call.getParent());
  }

  // canInlineReferenceToFunction: DIRECT mode, simple pure function and literal arg -> YES.
  @Test
  public void testCanInlineReferenceToFunction_directSimpleCall_returnsYes() throws Throwable {
    Node root = parse("foo(1); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, false, false, injector);
    assertEquals(CanInlineResult.YES, result);
  }

  // canInlineReferenceToFunction: side-effecting argument referenced twice in body -> NO (javadoc example).
  @Test
  public void testCanInlineReferenceToFunction_sideEffectArgUsedTwice_returnsNo() throws Throwable {
    Node root = parse("x = foo(i++); function foo(a) { return a + a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, false, false, injector);
    assertEquals(CanInlineResult.NO, result);
  }

  // canInlineReferenceToFunction: referencesThis true on a plain (non ".call") call -> NO.
  @Test
  public void testCanInlineReferenceToFunction_referencesThisPlainCall_returnsNo() throws Throwable {
    Node root = parse("foo(1); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, true, false, injector);
    assertEquals(CanInlineResult.NO, result);
  }

  // canInlineReferenceToFunction: ".call" with explicit "this" argument is supported -> YES.
  @Test
  public void testCanInlineReferenceToFunction_callWithExplicitThis_returnsYes() throws Throwable {
    Node root = parse("foo.call(this, 1); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findNodeOfType(root, Token.CALL);
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, true, false, injector);
    assertEquals(CanInlineResult.YES, result);
  }

  // canInlineReferenceToFunction: ".call" without a literal "this" arg fails the direct-inline check -> NO.
  @Test
  public void testCanInlineReferenceToFunction_callWithoutExplicitThisStrict_returnsNo() throws Throwable {
    Node root = parse("foo.call(1); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findNodeOfType(root, Token.CALL);
    FunctionInjector injector = newInjector(false, true, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, true, false, injector);
    assertEquals(CanInlineResult.NO, result);
  }

  // canInlineReferenceToFunction: ".call" with no "this" arg and assumeStrictThis=false -> unsupported -> NO.
  @Test
  public void testCanInlineReferenceToFunction_callWithNoArgsNotStrictThis_returnsNo() throws Throwable {
    Node root = parse("foo.call(); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findNodeOfType(root, Token.CALL);
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, false, false, injector);
    assertEquals(CanInlineResult.NO, result);
  }

  // canInlineReferenceToFunction: ".apply" call sites are never supported -> NO.
  @Test
  public void testCanInlineReferenceToFunction_applyCall_returnsNo() throws Throwable {
    Node root = parse("foo.apply(this, x); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findNodeOfType(root, Token.CALL);
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, false, false, injector);
    assertEquals(CanInlineResult.NO, result);
  }

  // canInlineReferenceToFunction: containsFunctions=true is allowed at the global scope -> YES.
  @Test
  public void testCanInlineReferenceToFunction_containsFunctionsGlobalScope_returnsYes() throws Throwable {
    Node root = parse("foo(1); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, false, true, injector);
    assertEquals(CanInlineResult.YES, result);
  }

  // canInlineReferenceToFunction: containsFunctions=true in a local scope without minimum capture -> NO.
  @Test
  public void testCanInlineReferenceToFunction_containsFunctionsLocalScope_returnsNo() throws Throwable {
    Node root = parse("function outer() { foo(1); } function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, false, true, injector);
    assertEquals(CanInlineResult.NO, result);
  }

  // canInlineReferenceToFunction: containsFunctions=true in local scope but assumeMinimumCapture=true -> YES.
  @Test
  public void testCanInlineReferenceToFunction_containsFunctionsLocalScopeMinCapture_returnsYes() throws Throwable {
    Node root = parse("function outer() { foo(1); } function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, true);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.DIRECT, false, true, injector);
    assertEquals(CanInlineResult.YES, result);
  }

  // canInlineReferenceToFunction: BLOCK mode, simple call site, no vars/no inner functions -> YES.
  @Test
  public void testCanInlineReferenceToFunction_blockModeSimpleCall_returnsYes() throws Throwable {
    Node root = parse("foo(1); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.BLOCK, false, false, injector);
    assertEquals(CanInlineResult.YES, result);
  }

  // canInlineReferenceToFunction: BLOCK mode, callee has vars and caller scope has an inner function -> NO.
  @Test
  public void testCanInlineReferenceToFunction_blockModeForbidTemps_returnsNo() throws Throwable {
    Node root = parse(
        "function outer() { function helper() {} foo(x); } "
        + "function foo(a) { var b = a; return b; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    CanInlineResult result = runCanInline(fn, call, new HashSet<String>(),
        InliningMode.BLOCK, false, false, injector);
    assertEquals(CanInlineResult.NO, result);
  }

  // inliningLowersCost: zero references always lowers cost -> true.
  @Test
  public void testInliningLowersCost_zeroReferences_returnsTrue() throws Throwable {
    Node root = parse("function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    List<Reference> refs = new ArrayList<Reference>();
    boolean result = injector.inliningLowersCost(
        null, fn, refs, new HashSet<String>(), true, false);
    assertTrue(result);
  }

  // inliningLowersCost: single removable direct reference is always cheaper -> true.
  @Test
  public void testInliningLowersCost_singleRemovableDirectReference_returnsTrue() throws Throwable {
    Node root = parse("foo(1); function foo(a) { return a; }");
    Node fn = findFunctionNamed(root, "foo");
    Node call = findCallTo(root, "foo");
    FunctionInjector injector = newInjector(false, false, false);
    List<Reference> refs = new ArrayList<Reference>();
    refs.add(new Reference(call, null, InliningMode.DIRECT));
    boolean result = injector.inliningLowersCost(
        null, fn, refs, new HashSet<String>(), true, false);
    assertTrue(result);
  }
}
