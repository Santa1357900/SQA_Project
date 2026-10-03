package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.jstype.BooleanLiteralSet;

/**
 * Unit tests for {@link TypeInference}.
 *
 * Most of TypeInference's logic is exercised indirectly by running the real
 * Closure {@link Compiler} with type checking enabled and inspecting the
 * resulting warnings: the contract for each AST node type (documented in
 * TypeInference.traverse) determines whether a type mismatch warning must
 * appear when the inferred value is passed to a strictly typed parameter.
 */
public class TypeInferenceClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
    options.setCheckTypes(true);
  }

  private int getWarningCount(String js) {
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(externs, input, options);
    return compiler.getWarnings().length;
  }

  // --- static getBooleanOutcomes(left, right, condition) branch coverage ---
  // formula: right.union(left.intersection(BooleanLiteralSet.get(!condition)))

  // BOTH ∩ get(false) = get(false); EMPTY ∪ get(false) = get(false)
  @Test
  public void testGetBooleanOutcomes_bothEmptyConditionTrue_returnsSetForFalse() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.BOTH, BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.get(false), result);
  }

  // EMPTY ∩ get(false) = EMPTY; BOTH ∪ EMPTY = BOTH
  @Test
  public void testGetBooleanOutcomes_emptyBothConditionTrue_returnsBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.EMPTY, BooleanLiteralSet.BOTH, true);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // BOTH ∩ get(true) = get(true); BOTH ∪ get(true) = BOTH
  @Test
  public void testGetBooleanOutcomes_bothBothConditionFalse_returnsBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.BOTH, BooleanLiteralSet.BOTH, false);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // EMPTY ∩ get(false) = EMPTY; EMPTY ∪ EMPTY = EMPTY
  @Test
  public void testGetBooleanOutcomes_emptyEmptyConditionTrue_returnsEmpty() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.EMPTY, BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.EMPTY, result);
  }

  // BOTH ∩ get(true) = get(true); EMPTY ∪ get(true) = get(true)
  @Test
  public void testGetBooleanOutcomes_bothEmptyConditionFalse_returnsSetForTrue() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.BOTH, BooleanLiteralSet.EMPTY, false);
    assertEquals(BooleanLiteralSet.get(true), result);
  }

  // Token.POS branch: n.setJSType(NUMBER_TYPE) unconditionally
  @Test
  public void testTraverse_posOperator_resultIsNumber_noWarning() throws Throwable {
    String js = "/** @param {number} n */\n function f(n) {}\n"
        + "var x = +'5';\n f(x);";
    assertEquals(0, getWarningCount(js));
  }

  // Token.NEG branch: n.setJSType(NUMBER_TYPE) unconditionally
  @Test
  public void testTraverse_negOperator_resultIsNumber_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n function f(s) {}\n"
        + "var y = -5;\n f(y);";
    assertTrue(getWarningCount(js) > 0);
  }

  // Token.TYPEOF branch: n.setJSType(STRING_TYPE) unconditionally
  @Test
  public void testTraverse_typeofOperator_resultIsString_warnsOnNumberParam() throws Throwable {
    String js = "/** @param {number} n */\n function f(n) {}\n"
        + "var t = typeof 5;\n f(t);";
    assertTrue(getWarningCount(js) > 0);
  }

  // Token.LT (comparison group) branch: n.setJSType(BOOLEAN_TYPE)
  @Test
  public void testTraverse_comparisonOperator_resultIsBoolean_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n function f(s) {}\n"
        + "var b = (1 < 2);\n f(b);";
    assertTrue(getWarningCount(js) > 0);
  }

  // Token.NOT (boolean-result group) branch
  @Test
  public void testTraverse_notOperator_resultIsBoolean_warnsOnNumberParam() throws Throwable {
    String js = "/** @param {number} n */\n function f(n) {}\n f(!true);";
    assertTrue(getWarningCount(js) > 0);
  }

  // Token.DIV (number-result arithmetic group) branch
  @Test
  public void testTraverse_divOperator_resultIsNumber_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n function f(s) {}\n f(10 / 2);";
    assertTrue(getWarningCount(js) > 0);
  }

  // Token.ARRAYLIT branch: n.setJSType(ARRAY_TYPE)
  @Test
  public void testTraverse_arrayLiteral_resultIsArrayType_warnsOnNumberParam() throws Throwable {
    String js = "/** @param {number} n */\n function f(n) {}\n f([1, 2, 3]);";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseAdd: left is string -> result type is STRING_TYPE
  @Test
  public void testTraverseAdd_stringPlusNumber_resultIsString_warnsOnNumberParam() throws Throwable {
    String js = "/** @param {number} n */\n function f(n) {}\n f('a' + 5);";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseAdd: both operands addedAsNumber -> result type is NUMBER_TYPE
  @Test
  public void testTraverseAdd_numberPlusNumber_resultIsNumber_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n function f(s) {}\n f(1 + 2);";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseHook: joined type of both branches via getLeastSupertype
  @Test
  public void testTraverseHook_joinsBranchTypes_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n function f(s) {}\n"
        + "var cond = true;\n f(cond ? 1 : 2);";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseAdd + ASSIGN_ADD: updateScopeForTypeChange keeps NUMBER_TYPE
  @Test
  public void testTraverseAssignAdd_updatesNumberType_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n function f(s) {}\n"
        + "var n = 5;\n n += 1;\n f(n);";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseCatch: untyped catch param is treated as UNKNOWN_TYPE (matches any param)
  @Test
  public void testTraverseCatch_untypedParam_isUnknownType_noWarning() throws Throwable {
    String js = "function outer() {\n"
        + "  /** @param {number} n */\n  function f(n) {}\n"
        + "  try { throw 'err'; } catch (e) { f(e); }\n}";
    assertEquals(0, getWarningCount(js));
  }

  // BUG-TARGETING TEST: traverseObjectLiteral re-assigning a qualified property
  // with a new value type must widen the inferred var type to the union of
  // old and new types (per the same contract used in updateScopeForTypeChange:
  // oldType.getLeastSupertype(newType)). If the property is re-read from a
  // different function scope, only the global Var's inferred type is used.
  @Test
  public void testTraverseObjectLiteral_reassignedPropertyDifferentType_joinsToUnion_warnsOnNumberParam()
      throws Throwable {
    String js = "var a = {};\n"
        + "a.b = {x: 1};\n"
        + "a.b = {x: 'hello'};\n"
        + "/** @param {number} n */\n function g(n) {}\n"
        + "function h() { g(a.b.x); }";
    assertTrue("Expected a type mismatch warning because a.b.x's inferred "
        + "type should widen to (number|string) after reassignment",
        getWarningCount(js) > 0);
  }

  // Companion case: single object-literal assignment (oldType == null branch),
  // not affected by the join logic above; type should be exactly number.
  @Test
  public void testTraverseObjectLiteral_singleAssignment_matchesNumberParam_noWarning() throws Throwable {
    String js = "var a = {};\n"
        + "a.b = {x: 1};\n"
        + "/** @param {number} n */\n function g(n) {}\n"
        + "function h() { g(a.b.x); }";
    assertEquals(0, getWarningCount(js));
  }

  // traverseNew: n.setJSType(ct.getInstanceType())
  @Test
  public void testTraverseNew_constructorReturnsInstanceType_warnsOnStringParam() throws Throwable {
    String js = "/** @constructor */\n function Foo() {}\n"
        + "/** @param {string} s */\n function f(s) {}\n"
        + "f(new Foo());";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseCall: n.setJSType(fnType.getReturnType())
  @Test
  public void testTraverseCall_returnTypeInferred_warnsOnStringParam() throws Throwable {
    String js = "/** @return {number} */\n function foo() { return 1; }\n"
        + "/** @param {string} s */\n function f(s) {}\n"
        + "f(foo());";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseGetProp: property type resolved from declared constructor property
  @Test
  public void testTraverseGetProp_declaredPropertyType_warnsOnStringParam() throws Throwable {
    String js = "/** @constructor */\n function Foo() {\n"
        + "  /** @type {number} */\n  this.bar = 1;\n}\n"
        + "/** @param {string} s */\n function f(s) {}\n"
        + "var foo = new Foo();\n f(foo.bar);";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseAssign: plain (non-var) assignment narrows flow type, no warning
  @Test
  public void testTraverseAssign_plainAssignmentNarrowsType_noWarning() throws Throwable {
    String js = "/** @param {number} n */\n function f(n) {}\n"
        + "var x;\n x = 5;\n f(x);";
    assertEquals(0, getWarningCount(js));
  }

  // Constructor: unassigned VAR-declared local gets entry type VOID
  @Test
  public void testTraverseName_unassignedVarIsVoid_warnsOnNumberParam() throws Throwable {
    String js = "function outer() {\n"
        + "  var y;\n"
        + "  /** @param {number} n */\n  function f(n) {}\n"
        + "  f(y);\n}";
    assertTrue(getWarningCount(js) > 0);
  }

  // traverseReturn: return value matches declared return type -> no warning
  @Test
  public void testTraverseReturn_matchingDeclaredType_noWarning() throws Throwable {
    String js = "/** @return {number} */\n function foo() { return 5; }";
    assertEquals(0, getWarningCount(js));
  }

  // traverseReturn: return value mismatches declared return type -> warning
  @Test
  public void testTraverseReturn_mismatchedDeclaredType_warns() throws Throwable {
    String js = "/** @return {string} */\n function foo() { return 5; }";
    assertTrue(getWarningCount(js) > 0);
  }

  // Token.COMMA branch: n.setJSType(getJSType(n.getLastChild()))
  @Test
  public void testTraverseComma_resultIsLastChildType_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n function f(s) {}\n f((1, 2));";
    assertTrue(getWarningCount(js) > 0);
  }
}
