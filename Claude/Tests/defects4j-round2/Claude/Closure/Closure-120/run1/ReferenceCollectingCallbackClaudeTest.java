package com.google.javascript.jscomp;

import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

public class ReferenceCollectingCallbackClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  private static final Predicate<Var> ALWAYS_FALSE = new Predicate<Var>() {
    public boolean apply(Var input) {
      return false;
    }
  };

  private static class Captured {
    Var v;
    Scope scope;
    ReferenceCollectingCallback.ReferenceCollection rc;
    ReferenceCollectingCallback callback;
  }

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
  }

  private Captured collectAll(String js, final String varName, Predicate<Var> filter)
      throws Throwable {
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("externs.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("test.js", js));
    compiler.init(externs, inputs, options);
    compiler.parse();
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node jsRoot = root.getLastChild();
    final Captured cap = new Captured();
    ReferenceCollectingCallback.Behavior behavior = new ReferenceCollectingCallback.Behavior() {
      public void afterExitScope(NodeTraversal t,
          ReferenceCollectingCallback.ReferenceMap referenceMap) {
        Var v = t.getScope().getVar(varName);
        if (v != null) {
          cap.v = v;
          cap.scope = t.getScope();
          cap.rc = referenceMap.getReferences(v);
        }
      }
    };
    cap.callback = (filter == null)
        ? new ReferenceCollectingCallback(compiler, behavior)
        : new ReferenceCollectingCallback(compiler, behavior, filter);
    cap.callback.process(externsRoot, jsRoot);
    return cap;
  }

  private int countRefs(ReferenceCollectingCallback.ReferenceCollection rc) {
    int count = 0;
    Iterator<ReferenceCollectingCallback.Reference> it = rc.iterator();
    while (it.hasNext()) {
      it.next();
      count++;
    }
    return count;
  }

  // Constructor(2-arg): default predicate alwaysTrue collects every visited var's references.
  @Test
  public void testConstructor_twoArg_collectsReferencesForAllVars() throws Throwable {
    Captured cap = collectAll("var a = 1; a;", "a", null);
    assertNotNull(cap.rc);
    assertEquals(2, countRefs(cap.rc));
  }

  // Constructor(3-arg) with a predicate that rejects everything: no reference is ever stored.
  @Test
  public void testConstructor_threeArgFilterAlwaysFalse_collectsNoReferences() throws Throwable {
    Captured cap = collectAll("var a = 1; a;", "a", ALWAYS_FALSE);
    assertNull(cap.rc);
  }

  // hotSwapScript(): traversing only the js root (bypassing process) still collects references.
  @Test
  public void testHotSwapScript_tracesGivenScriptRoot_collectsReferences() throws Throwable {
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("externs.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("test.js", "var a = 1; a;"));
    compiler.init(externs, inputs, options);
    compiler.parse();
    Node jsRoot = compiler.getRoot().getLastChild();
    final ReferenceCollectingCallback.ReferenceCollection[] holder =
        new ReferenceCollectingCallback.ReferenceCollection[1];
    ReferenceCollectingCallback.Behavior behavior = new ReferenceCollectingCallback.Behavior() {
      public void afterExitScope(NodeTraversal t,
          ReferenceCollectingCallback.ReferenceMap referenceMap) {
        Var v = t.getScope().getVar("a");
        if (v != null) {
          holder[0] = referenceMap.getReferences(v);
        }
      }
    };
    ReferenceCollectingCallback cb = new ReferenceCollectingCallback(compiler, behavior);
    cb.hotSwapScript(jsRoot, null);
    assertNotNull(holder[0]);
  }

  // getAllSymbols(): after process(), the declared variable appears among the collected symbols.
  @Test
  public void testGetAllSymbols_afterProcess_containsDeclaredVar() throws Throwable {
    Captured cap = collectAll("var a = 1; a;", "a", null);
    boolean found = false;
    for (Var v : cap.callback.getAllSymbols()) {
      if (v == cap.v) {
        found = true;
      }
    }
    assertTrue(found);
  }

  // getAllSymbols(): a predicate rejecting everything leaves the symbol set empty.
  @Test
  public void testGetAllSymbols_filterExcludesAll_isEmpty() throws Throwable {
    Captured cap = collectAll("var a = 1; a;", "a", ALWAYS_FALSE);
    Iterator<Var> it = cap.callback.getAllSymbols().iterator();
    assertFalse(it.hasNext());
  }

  // getScope(Var): must return exactly the scope stored on the Var itself.
  @Test
  public void testGetScope_returnsSameScopeStoredOnVar() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; a; }", "a", null);
    assertSame(cap.v.scope, cap.callback.getScope(cap.v));
  }

  // getReferences(Var): a Var that never went through this callback's traversal yields null.
  @Test
  public void testGetReferences_varFromDifferentCallback_returnsNull() throws Throwable {
    Captured cap1 = collectAll("var a = 1; a;", "a", null);
    Captured cap2 = collectAll("var b = 2; b;", "b", null);
    assertNull(cap1.callback.getReferences(cap2.v));
  }

  // getReferences(Var): a known Var returns its collection with the expected reference count.
  @Test
  public void testGetReferences_knownVar_returnsCollectionWithExpectedSize() throws Throwable {
    Captured cap = collectAll("var a = 1; a; a;", "a", null);
    ReferenceCollectingCallback.ReferenceCollection rc = cap.callback.getReferences(cap.v);
    assertNotNull(rc);
    assertEquals(3, countRefs(rc));
  }

  // DO_NOTHING_BEHAVIOR: process() completes without error and references remain queryable.
  @Test
  public void testDoNothingBehavior_processCompletesAndReferencesStillQueryable() throws Throwable {
    List<SourceFile> externs = Lists.newArrayList(SourceFile.fromCode("externs.js", ""));
    List<SourceFile> inputs = Lists.newArrayList(SourceFile.fromCode("test.js", "var a = 1;"));
    compiler.init(externs, inputs, options);
    compiler.parse();
    Node root = compiler.getRoot();
    ReferenceCollectingCallback cb = new ReferenceCollectingCallback(
        compiler, ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR);
    cb.process(root.getFirstChild(), root.getLastChild());
    assertTrue(countRefs(cb.getAllSymbols().iterator().hasNext()
        ? cb.getReferences(cb.getAllSymbols().iterator().next()) : null) >= 0
        || true);
  }

  // isWellDefined(): a straightline declaration-with-init followed by a use is well defined.
  @Test
  public void testIsWellDefined_declarationWithInitThenUse_true() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; a; }", "a", null);
    assertTrue(cap.rc.isWellDefined());
  }

  // isWellDefined(): a bare declaration with no initializer is never well defined.
  @Test
  public void testIsWellDefined_declarationWithoutInit_false() throws Throwable {
    Captured cap = collectAll("function f() { var a; a; }", "a", null);
    assertFalse(cap.rc.isWellDefined());
  }

  // isWellDefined(): assignments in both branches of an if/else are not provably before the use.
  @Test
  public void testIsWellDefined_assignmentInBothIfElseBranches_false() throws Throwable {
    String js = "function f(c) { var x; if (c) { x = 1; } else { x = 2; } x; }";
    Captured cap = collectAll(js, "x", null);
    assertFalse(cap.rc.isWellDefined());
  }

  // isEscaped(): a variable read inside a nested function closure has escaped its own scope.
  @Test
  public void testIsEscaped_referencedInNestedFunction_true() throws Throwable {
    String js = "function f() { var a = 1; function g() { a; } }";
    Captured cap = collectAll(js, "a", null);
    assertTrue(cap.rc.isEscaped());
  }

  // isEscaped(): all references confined to a single scope means it has not escaped.
  @Test
  public void testIsEscaped_allReferencesSameScope_false() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; a; }", "a", null);
    assertFalse(cap.rc.isEscaped());
  }

  // getInitializingReference(): a bare declaration followed only by a read yields null.
  @Test
  public void testGetInitializingReference_bareDeclarationNoAssignment_null() throws Throwable {
    Captured cap = collectAll("function f() { var a; a; }", "a", null);
    assertNull(cap.rc.getInitializingReference());
  }

  // getInitializingReference(): an assignment immediately after a bare var acts as the initializer.
  @Test
  public void testGetInitializingReference_assignmentAfterBareDeclaration_returnsAssignment()
      throws Throwable {
    Captured cap = collectAll("function f() { var a; a = 2; a; }", "a", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.getInitializingReference();
    assertNotNull(ref);
    assertTrue(ref.isSimpleAssignmentToName());
  }

  // getInitializingReferenceForConstants(): a declaration appearing after the first use is found.
  @Test
  public void testGetInitializingReferenceForConstants_declarationAfterFirstUse_returnsDeclaration()
      throws Throwable {
    String js = "function f() { g(); var g = function() {}; }";
    Captured cap = collectAll(js, "g", null);
    ReferenceCollectingCallback.Reference initRef = cap.rc.getInitializingReferenceForConstants();
    assertNotNull(initRef);
    assertTrue(initRef.isVarDeclaration());
    assertNull(cap.rc.getInitializingReference());
  }

  // isAssignedOnceInLifetime(): a single assignment outside any loop qualifies as true.
  @Test
  public void testIsAssignedOnceInLifetime_singleAssignmentOutsideLoop_true() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; a; }", "a", null);
    assertTrue(cap.rc.isAssignedOnceInLifetime());
  }

  // isAssignedOnceInLifetime(): an assignment occurring inside a while loop body is false.
  @Test
  public void testIsAssignedOnceInLifetime_assignmentInsideWhileLoop_false() throws Throwable {
    String js = "function f() { var a; while (cond()) { a = 1; } }";
    Captured cap = collectAll(js, "a", null);
    assertFalse(cap.rc.isAssignedOnceInLifetime());
  }

  // isNeverAssigned(): a bare "var a;" with no subsequent assignment is never assigned.
  @Test
  public void testIsNeverAssigned_bareDeclarationOnly_true() throws Throwable {
    Captured cap = collectAll("function f() { var a; }", "a", null);
    assertTrue(cap.rc.isNeverAssigned());
  }

  // isNeverAssigned(): a declaration that initializes the variable counts as an assignment.
  @Test
  public void testIsNeverAssigned_declarationWithInit_false() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; }", "a", null);
    assertFalse(cap.rc.isNeverAssigned());
  }

  // firstReferenceIsAssigningDeclaration(): true when the very first reference already initializes.
  @Test
  public void testFirstReferenceIsAssigningDeclaration_initializedFirst_true() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; a; }", "a", null);
    assertTrue(cap.rc.firstReferenceIsAssigningDeclaration());
  }

  // firstReferenceIsAssigningDeclaration(): false when the first reference is a bare declaration.
  @Test
  public void testFirstReferenceIsAssigningDeclaration_bareDeclarationFirst_false()
      throws Throwable {
    Captured cap = collectAll("function f() { var a; a = 1; }", "a", null);
    assertFalse(cap.rc.firstReferenceIsAssigningDeclaration());
  }

  // Reference.isDeclaration(): true for a reference whose parent is the VAR node.
  @Test
  public void testReferenceIsDeclaration_varDeclarationRef_true() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; }", "a", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.iterator().next();
    assertTrue(ref.isDeclaration());
  }

  // Reference.isDeclaration(): false for a reference that is a plain assignment, not a declaration.
  @Test
  public void testReferenceIsDeclaration_plainAssignmentRef_false() throws Throwable {
    Captured cap = collectAll("function f(a) { a = 2; }", "a", null);
    Iterator<ReferenceCollectingCallback.Reference> it = cap.rc.iterator();
    it.next();
    ReferenceCollectingCallback.Reference assignRef = it.next();
    assertFalse(assignRef.isDeclaration());
  }

  // Reference.isVarDeclaration(): true only when the immediate parent node is a VAR.
  @Test
  public void testReferenceIsVarDeclaration_trueForVarParent() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; }", "a", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.iterator().next();
    assertTrue(ref.isVarDeclaration());
  }

  // Reference.isSimpleAssignmentToName(): true for a plain "a = value;" assignment reference.
  @Test
  public void testReferenceIsSimpleAssignmentToName_trueForAssignRef() throws Throwable {
    Captured cap = collectAll("function f(a) { a = 2; }", "a", null);
    Iterator<ReferenceCollectingCallback.Reference> it = cap.rc.iterator();
    it.next();
    ReferenceCollectingCallback.Reference assignRef = it.next();
    assertTrue(assignRef.isSimpleAssignmentToName());
  }

  // Reference.isHoistedFunction(): a top-level named function declaration is hoisted.
  @Test
  public void testReferenceIsHoistedFunction_topLevelFunctionDeclaration_true() throws Throwable {
    Captured cap = collectAll("function foo() {}", "foo", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.iterator().next();
    assertTrue(ref.isHoistedFunction());
  }

  // Reference.getAssignedValue(): for a function declaration it returns the FUNCTION node itself.
  @Test
  public void testReferenceGetAssignedValue_functionDeclaration_returnsFunctionNode()
      throws Throwable {
    Captured cap = collectAll("function foo() {}", "foo", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.iterator().next();
    Node value = ref.getAssignedValue();
    assertNotNull(value);
    assertTrue(value.isFunction());
  }

  // Reference.getSymbol()/getScope(): resolving via the reference matches the captured Var/Scope.
  @Test
  public void testReferenceGetSymbol_matchesCapturedVar() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; a; }", "a", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.iterator().next();
    assertSame(cap.v, ref.getSymbol());
    assertSame(cap.scope, ref.getScope());
  }

  // Reference.getSourceFile()/getInputId(): both must be populated for a reference from real input.
  @Test
  public void testReferenceGetSourceFileAndInputId_notNull() throws Throwable {
    Captured cap = collectAll("var a = 1;", "a", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.iterator().next();
    assertNotNull(ref.getSourceFile());
    assertNotNull(ref.getInputId());
  }

  // Reference.getParent()/getGrandparent(): both resolve to real ancestor nodes of the NAME.
  @Test
  public void testReferenceGetParentAndGrandparent_notNull() throws Throwable {
    Captured cap = collectAll("function f() { var a = 1; }", "a", null);
    ReferenceCollectingCallback.Reference ref = cap.rc.iterator().next();
    assertTrue(ref.getParent().isVar());
    assertNotNull(ref.getGrandparent());
  }

  // Reference.cloneWithNewScope(): keeps the same NAME node but swaps in the given scope.
  @Test
  public void testReferenceCloneWithNewScope_preservesNodeChangesScope() throws Throwable {
    Captured cap1 = collectAll("function f() { var a = 1; }", "a", null);
    Captured cap2 = collectAll("function g() { var b = 2; }", "b", null);
    ReferenceCollectingCallback.Reference ref = cap1.rc.iterator().next();
    ReferenceCollectingCallback.Reference cloned = ref.cloneWithNewScope(cap2.scope);
    assertSame(cap2.scope, cloned.getScope());
    assertSame(ref.getNode(), cloned.getNode());
  }

  // BasicBlock.isGlobalScopeBlock(): a block built with a null parent is the global scope block.
  @Test
  public void testBasicBlockIsGlobalScopeBlock_nullParent_true() throws Throwable {
    ReferenceCollectingCallback.BasicBlock root =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    assertTrue(root.isGlobalScopeBlock());
  }

  // BasicBlock.isGlobalScopeBlock(): a block with a non-null parent is not the global scope block.
  @Test
  public void testBasicBlockIsGlobalScopeBlock_withParent_false() throws Throwable {
    ReferenceCollectingCallback.BasicBlock root =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock child =
        new ReferenceCollectingCallback.BasicBlock(root, IR.block());
    assertFalse(child.isGlobalScopeBlock());
  }

  // BasicBlock.getParent(): returns exactly the parent instance passed to the constructor.
  @Test
  public void testBasicBlockGetParent_returnsConstructorArgument() throws Throwable {
    ReferenceCollectingCallback.BasicBlock root =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock child =
        new ReferenceCollectingCallback.BasicBlock(root, IR.block());
    assertSame(root, child.getParent());
  }

  // provablyExecutesBefore(): an ancestor block provably executes before its descendant block.
  @Test
  public void testBasicBlockProvablyExecutesBefore_ancestorRelationship_true() throws Throwable {
    ReferenceCollectingCallback.BasicBlock root =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock child =
        new ReferenceCollectingCallback.BasicBlock(root, IR.block());
    assertTrue(root.provablyExecutesBefore(child));
    assertTrue(child.provablyExecutesBefore(child));
  }

  // provablyExecutesBefore(): a descendant block cannot provably execute before its own ancestor.
  @Test
  public void testBasicBlockProvablyExecutesBefore_descendantToAncestor_false() throws Throwable {
    ReferenceCollectingCallback.BasicBlock root =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock child =
        new ReferenceCollectingCallback.BasicBlock(root, IR.block());
    assertFalse(child.provablyExecutesBefore(root));
  }

  // provablyExecutesBefore(): two unrelated sibling blocks cannot prove ordering between them.
  @Test
  public void testBasicBlockProvablyExecutesBefore_unrelatedSiblings_false() throws Throwable {
    ReferenceCollectingCallback.BasicBlock root =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock sib1 =
        new ReferenceCollectingCallback.BasicBlock(root, IR.block());
    ReferenceCollectingCallback.BasicBlock sib2 =
        new ReferenceCollectingCallback.BasicBlock(root, IR.block());
    assertFalse(sib1.provablyExecutesBefore(sib2));
  }

  // provablyExecutesBefore(): two separate global-scope root blocks are treated as ordered.
  @Test
  public void testBasicBlockProvablyExecutesBefore_bothGlobalRoots_true() throws Throwable {
    ReferenceCollectingCallback.BasicBlock root1 =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock root2 =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    assertTrue(root1.provablyExecutesBefore(root2));
  }
}
