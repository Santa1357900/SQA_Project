package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.List;
import java.util.Map;

public class ReferenceCollectingCallbackClaudeTest {

  private static class RunResult {
    ReferenceCollectingCallback pass;
    Map<Var, ReferenceCollectingCallback.ReferenceCollection> referenceMap;
  }

  private RunResult runProcess(String js, Predicate<Var> filter) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList();
    List<SourceFile> inputs = Lists.newArrayList();
    inputs.add(SourceFile.fromCode("input.js", js));
    compiler.compile(externs, inputs, options);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();
    final RunResult result = new RunResult();
    ReferenceCollectingCallback.Behavior behavior =
        new ReferenceCollectingCallback.Behavior() {
          public void afterExitScope(NodeTraversal t,
              Map<Var, ReferenceCollectingCallback.ReferenceCollection> referenceMap) {
            result.referenceMap = referenceMap;
          }
        };
    ReferenceCollectingCallback pass =
        new ReferenceCollectingCallback(compiler, behavior, filter);
    pass.process(externsRoot, mainRoot);
    result.pass = pass;
    return result;
  }

  private RunResult runProcess(String js) {
    return runProcess(js, Predicates.<Var>alwaysTrue());
  }

  private ReferenceCollectingCallback.ReferenceCollection findByName(
      Map<Var, ReferenceCollectingCallback.ReferenceCollection> map, String name) {
    for (ReferenceCollectingCallback.ReferenceCollection rc : map.values()) {
      if (!rc.references.isEmpty()
          && rc.references.get(0).getNameNode().getString().equals(name)) {
        return rc;
      }
    }
    return null;
  }

  private Var findVarByName(
      Map<Var, ReferenceCollectingCallback.ReferenceCollection> map, String name) {
    for (Map.Entry<Var, ReferenceCollectingCallback.ReferenceCollection> entry
        : map.entrySet()) {
      if (!entry.getValue().references.isEmpty()
          && entry.getValue().references.get(0).getNameNode().getString().equals(name)) {
        return entry.getKey();
      }
    }
    return null;
  }

  // BasicBlock.provablyExecutesBefore: thatBlock == this -> loop body never runs, returns true
  @Test
  public void testBasicBlockProvablyExecutesBefore_sameBlock_returnsTrue() throws Throwable {
    ReferenceCollectingCallback.BasicBlock block =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    assertTrue(block.provablyExecutesBefore(block));
  }

  // BasicBlock.provablyExecutesBefore: thatBlock is a descendant of this -> true
  @Test
  public void testBasicBlockProvablyExecutesBefore_descendantBlock_returnsTrue() throws Throwable {
    ReferenceCollectingCallback.BasicBlock parent =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock child =
        new ReferenceCollectingCallback.BasicBlock(parent, IR.block());
    assertTrue(parent.provablyExecutesBefore(child));
  }

  // BasicBlock.provablyExecutesBefore: unrelated blocks -> walk reaches null, returns false
  @Test
  public void testBasicBlockProvablyExecutesBefore_unrelatedBlock_returnsFalse() throws Throwable {
    ReferenceCollectingCallback.BasicBlock a =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock b =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    assertFalse(a.provablyExecutesBefore(b));
  }

  // BasicBlock.getParent returns constructor argument
  @Test
  public void testBasicBlockGetParent_returnsConstructorArgument() throws Throwable {
    ReferenceCollectingCallback.BasicBlock parent =
        new ReferenceCollectingCallback.BasicBlock(null, IR.block());
    ReferenceCollectingCallback.BasicBlock child =
        new ReferenceCollectingCallback.BasicBlock(parent, IR.block());
    assertSame(parent, child.getParent());
  }

  // ReferenceCollection.isWellDefined: size == 0 branch -> false
  @Test
  public void testIsWellDefined_emptyCollection_returnsFalse() throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection rc =
        new ReferenceCollectingCallback.ReferenceCollection();
    assertFalse(rc.isWellDefined());
  }

  // ReferenceCollection.isNeverAssigned: loop over 0 refs -> vacuously true
  @Test
  public void testIsNeverAssigned_emptyCollection_returnsTrue() throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection rc =
        new ReferenceCollectingCallback.ReferenceCollection();
    assertTrue(rc.isNeverAssigned());
  }

  // ReferenceCollection.isAssignedOnceInLifetime: no assignment found -> false
  @Test
  public void testIsAssignedOnceInLifetime_emptyCollection_returnsFalse() throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection rc =
        new ReferenceCollectingCallback.ReferenceCollection();
    assertFalse(rc.isAssignedOnceInLifetime());
  }

  // ReferenceCollection.firstReferenceIsAssigningDeclaration: size == 0 -> false
  @Test
  public void testFirstReferenceIsAssigningDeclaration_emptyCollection_returnsFalse()
      throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection rc =
        new ReferenceCollectingCallback.ReferenceCollection();
    assertFalse(rc.firstReferenceIsAssigningDeclaration());
  }

  // ReferenceCollection.getInitializingReference: index 0 access on empty list throws
  @Test
  public void testGetInitializingReference_emptyCollection_throwsIndexOutOfBoundsException()
      throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection rc =
        new ReferenceCollectingCallback.ReferenceCollection();
    try {
      rc.getInitializingReference();
      fail("expected IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  // ReferenceCollection.getInitializingReferenceForConstants: loop over 0 refs -> null
  @Test
  public void testGetInitializingReferenceForConstants_emptyCollection_returnsNull()
      throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection rc =
        new ReferenceCollectingCallback.ReferenceCollection();
    assertNull(rc.getInitializingReferenceForConstants());
  }

  // ReferenceCollection.isEscaped: loop over 0 refs -> scope stays null -> false
  @Test
  public void testIsEscaped_emptyCollection_returnsFalse() throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection rc =
        new ReferenceCollectingCallback.ReferenceCollection();
    assertFalse(rc.isEscaped());
  }

  // DO_NOTHING_BEHAVIOR.afterExitScope: body is empty, map must remain unmodified
  @Test
  public void testDoNothingBehavior_afterExitScope_leavesMapUnchanged() throws Throwable {
    Map<Var, ReferenceCollectingCallback.ReferenceCollection> map = Maps.newHashMap();
    ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR.afterExitScope(null, map);
    assertTrue(map.isEmpty());
  }

  // getReferenceCollection: unknown/null key -> map.get returns null
  @Test
  public void testGetReferenceCollection_nullVar_returnsNull() throws Throwable {
    RunResult result = runProcess("var a = 1;");
    assertNull(result.pass.getReferenceCollection(null));
  }

  // getReferenceCollection: known var returns same instance stored in the map
  @Test
  public void testGetReferenceCollection_knownVar_returnsSameCollectionAsMap() throws Throwable {
    RunResult result = runProcess("var a = 1;");
    Var v = findVarByName(result.referenceMap, "a");
    assertNotNull(v);
    ReferenceCollectingCallback.ReferenceCollection viaMethod =
        result.pass.getReferenceCollection(v);
    assertSame(result.referenceMap.get(v), viaMethod);
  }

  // 3-arg constructor with an always-false filter: no variables collected
  @Test
  public void testConstructorWithFilter_alwaysFalseFilter_noReferencesCollected()
      throws Throwable {
    RunResult result = runProcess("var a = 1;", Predicates.<Var>alwaysFalse());
    assertTrue(result.referenceMap.isEmpty());
  }

  // 2-arg constructor uses default always-true filter and collects references
  @Test
  public void testTwoArgConstructor_defaultFilterCollectsAllVariables() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = Lists.newArrayList();
    List<SourceFile> inputs = Lists.newArrayList();
    inputs.add(SourceFile.fromCode("input2.js", "var a = 1;"));
    compiler.compile(externs, inputs, options);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();
    final Map<Var, ReferenceCollectingCallback.ReferenceCollection>[] captured = new Map[1];
    ReferenceCollectingCallback.Behavior behavior =
        new ReferenceCollectingCallback.Behavior() {
          public void afterExitScope(NodeTraversal t,
              Map<Var, ReferenceCollectingCallback.ReferenceCollection> referenceMap) {
            captured[0] = referenceMap;
          }
        };
    ReferenceCollectingCallback pass = new ReferenceCollectingCallback(compiler, behavior);
    pass.process(externsRoot, mainRoot);
    assertFalse(captured[0].isEmpty());
  }

  // "var a;" with no initializer: not an initializing declaration, never assigned, not well-defined
  @Test
  public void testProcess_varNoInitializer_isNeverAssignedTrueAndNotWellDefined()
      throws Throwable {
    RunResult result = runProcess("var a;");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertNotNull(rc);
    assertTrue(rc.isNeverAssigned());
    assertFalse(rc.isWellDefined());
    assertFalse(rc.firstReferenceIsAssigningDeclaration());
  }

  // "var a = 1;" initializes at declaration: well-defined, assigned once, declaration-first
  @Test
  public void testProcess_varWithInitializer_isWellDefinedTrueAndAssignedOnce() throws Throwable {
    RunResult result = runProcess("var a = 1;");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertTrue(rc.isWellDefined());
    assertTrue(rc.isAssignedOnceInLifetime());
    assertTrue(rc.firstReferenceIsAssigningDeclaration());
    assertFalse(rc.isNeverAssigned());
  }

  // "var a; a = 1;" the assignment right after the declaration is the initializing reference
  @Test
  public void testProcess_varThenAssignment_getInitializingReferenceReturnsAssignment()
      throws Throwable {
    RunResult result = runProcess("var a; a = 1;");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    ReferenceCollectingCallback.Reference init = rc.getInitializingReference();
    assertNotNull(init);
    assertTrue(init.isSimpleAssignmentToName());
  }

  // Bug-catching test: a single assignment located inside a loop body cannot be guaranteed to
  // occur only once in the variable's lifetime, so isAssignedOnceInLifetime() must be false.
  @Test
  public void testProcess_assignmentInsideForLoopBody_isAssignedOnceInLifetimeFalse()
      throws Throwable {
    RunResult result = runProcess("var a; for (var i = 0; i < 1; i++) { a = 1; }");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertNotNull(rc);
    assertFalse(rc.isAssignedOnceInLifetime());
  }

  // Variable assigned inside an inner function scope escapes its declaring scope
  @Test
  public void testProcess_varAssignedInsideInnerFunction_isEscapedTrue() throws Throwable {
    RunResult result = runProcess("var a; function f() { a = 1; } f();");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertTrue(rc.isEscaped());
  }

  // Variable only referenced within a single (global) scope does not escape
  @Test
  public void testProcess_varOnlyInGlobalScope_isEscapedFalse() throws Throwable {
    RunResult result = runProcess("var a = 1; a = 2;");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertFalse(rc.isEscaped());
  }

  // Constants may be defined after their first (deferred) use: getInitializingReferenceForConstants
  // scans forward and still finds the declaration.
  @Test
  public void testProcess_usageBeforeVarDeclaration_getInitializingReferenceForConstantsFindsDeclaration()
      throws Throwable {
    RunResult result = runProcess("function f() { return A; } var A = 1; f();");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "A");
    ReferenceCollectingCallback.Reference ref = rc.getInitializingReferenceForConstants();
    assertNotNull(ref);
    assertTrue(ref.isVarDeclaration());
  }

  // Same scenario: the strict getInitializingReference (used for non-constants) finds nothing
  // because the first reference is a use, not a declaration/assignment pair.
  @Test
  public void testProcess_usageBeforeVarDeclaration_getInitializingReferenceReturnsNull()
      throws Throwable {
    RunResult result = runProcess("function f() { return A; } var A = 1; f();");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "A");
    assertNull(rc.getInitializingReference());
  }

  // Catch parameter counts as a declaration and as an initializing declaration
  @Test
  public void testProcess_catchParameter_isDeclarationAndInitializingDeclaration()
      throws Throwable {
    RunResult result = runProcess("try { } catch (e) { e = 1; }");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "e");
    assertNotNull(rc);
    ReferenceCollectingCallback.Reference first = rc.references.get(0);
    assertTrue(first.isDeclaration());
    assertTrue(first.isInitializingDeclaration());
  }

  // Function parameter counts as a declaration but not as a var declaration
  @Test
  public void testProcess_functionParameter_isDeclarationTrueButNotVarDeclaration()
      throws Throwable {
    RunResult result = runProcess("function f(p) { p = 1; }");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "p");
    ReferenceCollectingCallback.Reference first = rc.references.get(0);
    assertTrue(first.isDeclaration());
    assertFalse(first.isVarDeclaration());
  }

  // For a named function declaration, getAssignedValue() returns the FUNCTION node itself
  @Test
  public void testProcess_namedFunctionDeclaration_getAssignedValueReturnsParentFunctionNode()
      throws Throwable {
    RunResult result = runProcess("function foo() { } foo();");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "foo");
    ReferenceCollectingCallback.Reference first = rc.references.get(0);
    assertSame(first.getParent(), first.getAssignedValue());
  }

  // Named top-level function declarations are hoisted
  @Test
  public void testProcess_namedFunctionDeclaration_isHoistedFunctionTrue() throws Throwable {
    RunResult result = runProcess("function foo() { } foo();");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "foo");
    ReferenceCollectingCallback.Reference first = rc.references.get(0);
    assertTrue(first.isHoistedFunction());
  }

  // Assignments in sibling if/else branches are not provably ordered -> not well-defined
  @Test
  public void testProcess_assignmentsInIfElseBranches_isWellDefinedFalse() throws Throwable {
    RunResult result = runProcess("var cond; var a; if (cond) { a = 1; } else { a = 2; }");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertFalse(rc.isWellDefined());
  }

  // DO block boundary: declaration + assignment inside the loop body + condition read
  @Test
  public void testProcess_doWhileLoop_collectsAllReferencesForVariable() throws Throwable {
    RunResult result = runProcess("var a; do { a = 1; } while (a < 10);");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertEquals(4, rc.references.size());
  }

  // TRY/CATCH/FINALLY each create their own basic block but all references are still collected
  @Test
  public void testProcess_tryCatchFinally_collectsReferencesAcrossAllBlocks() throws Throwable {
    RunResult result = runProcess("var a; try { a = 1; } catch (e) { a = 2; } finally { a = 3; }");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertEquals(4, rc.references.size());
  }

  // SWITCH/CASE boundary: declaration + switch expression read + each case assignment
  @Test
  public void testProcess_switchStatement_collectsReferencesInEachCase() throws Throwable {
    RunResult result = runProcess("var a; switch (a) { case 1: a = 2; break; default: a = 3; }");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertEquals(4, rc.references.size());
  }

  // HOOK/AND/OR boundaries: every operand beyond the first child is its own block, but all NAME
  // occurrences are still collected as references.
  @Test
  public void testProcess_ternaryAndLogicalOperators_collectsAllOperandReferences()
      throws Throwable {
    RunResult result = runProcess("var a; var b = a ? a : a; var c = a && a; var d = a || a;");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    assertEquals(8, rc.references.size());
  }

  // INC operator marks the reference as an lvalue
  @Test
  public void testProcess_incrementOperator_isLvalueTrue() throws Throwable {
    RunResult result = runProcess("var a = 0; a++;");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    ReferenceCollectingCallback.Reference last = rc.references.get(rc.references.size() - 1);
    assertTrue(last.isLvalue());
  }

  // Reference carries the originating source file name and a non-null scope
  @Test
  public void testProcess_reference_getSourceNameAndScopeNotNull() throws Throwable {
    RunResult result = runProcess("var a = 1;");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "a");
    ReferenceCollectingCallback.Reference first = rc.references.get(0);
    assertEquals("input.js", first.getSourceName());
    assertNotNull(first.getScope());
  }

  // for-in loop variable is considered an lvalue via isLhsOfForInExpression
  @Test
  public void testProcess_forInLoopVariable_isLvalueTrue() throws Throwable {
    RunResult result = runProcess("var obj = {}; for (var k in obj) { }");
    ReferenceCollectingCallback.ReferenceCollection rc = findByName(result.referenceMap, "k");
    ReferenceCollectingCallback.Reference first = rc.references.get(0);
    assertTrue(first.isLvalue());
  }
}
