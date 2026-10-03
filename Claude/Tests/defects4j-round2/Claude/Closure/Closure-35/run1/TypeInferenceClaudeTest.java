package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;

import org.junit.Test;

import java.util.List;

public class TypeInferenceClaudeTest {

  private static final String EXTERNS =
      "/**\n" +
      " * @constructor\n" +
      " * @param {*=} opt_value\n" +
      " * @return {!Object}\n" +
      " */\n" +
      "function Object(opt_value) {}\n" +
      "/**\n" +
      " * @constructor\n" +
      " * @param {...*} var_args\n" +
      " */\n" +
      "function Function(var_args) {}\n" +
      "/**\n" +
      " * @constructor\n" +
      " * @param {*=} opt_value\n" +
      " * @return {string}\n" +
      " */\n" +
      "function String(opt_value) {}\n" +
      "/**\n" +
      " * @constructor\n" +
      " * @param {*=} opt_value\n" +
      " * @return {number}\n" +
      " */\n" +
      "function Number(opt_value) {}\n" +
      "/**\n" +
      " * @constructor\n" +
      " * @param {*=} opt_value\n" +
      " */\n" +
      "function Boolean(opt_value) {}\n" +
      "/**\n" +
      " * @constructor\n" +
      " * @param {...*} var_args\n" +
      " * @return {!Array}\n" +
      " */\n" +
      "function Array(var_args) {}\n";

  private Result compileAndCheck(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    List<SourceFile> externs = Lists.newArrayList();
    externs.add(SourceFile.fromCode("externs.js", EXTERNS));
    List<SourceFile> inputs = Lists.newArrayList();
    inputs.add(SourceFile.fromCode("test.js", js));
    return compiler.compile(externs, inputs, options);
  }

  // ADD: number + number is inferred as number (isAddedAsNumber branch), matches declared type
  @Test
  public void testTraverseAdd_numberPlusNumber_assignToNumber_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = (1 + 2);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // ADD: number + number inferred as number, mismatches declared string -> warning
  @Test
  public void testTraverseAdd_numberPlusNumber_assignToString_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {string} */\nvar x = (1 + 2);\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // ADD: string + number inferred as string (string branch), matches declared type
  @Test
  public void testTraverseAdd_stringPlusNumber_assignToString_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {string} */\nvar x = (\"a\" + 1);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // ADD: string + number inferred as string, mismatches declared number -> warning
  @Test
  public void testTraverseAdd_stringPlusNumber_assignToNumber_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = (\"a\" + 1);\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // ADD: boolean + number is addedAsNumber -> number, matches declared type
  @Test
  public void testTraverseAdd_booleanPlusNumber_assignToNumber_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = (true + 1);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // ADD: null + number is addedAsNumber -> number, matches declared type
  @Test
  public void testTraverseAdd_nullPlusNumber_assignToNumber_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = (null + 1);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // HOOK: ternary least-supertype of two numbers is number, matches declared type
  @Test
  public void testTraverseHook_ternaryNumber_assignToNumber_noWarning() throws Throwable {
    String js = "/**\n * @param {boolean} b\n */\n" +
        "function f(b) {\n" +
        "  /** @type {number} */\n" +
        "  var x = b ? 1 : 2;\n" +
        "}\n";
    Result r = compileAndCheck(js);
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // HOOK: ternary of two numbers mismatches declared string -> warning
  @Test
  public void testTraverseHook_ternaryNumber_assignToString_warning() throws Throwable {
    String js = "/**\n * @param {boolean} b\n */\n" +
        "function f(b) {\n" +
        "  /** @type {string} */\n" +
        "  var x = b ? 1 : 2;\n" +
        "}\n";
    Result r = compileAndCheck(js);
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // AND: string && string inferred as string, matches declared type
  @Test
  public void testTraverseShortCircuit_andStrings_assignToString_noWarning() throws Throwable {
    String js = "/**\n * @param {string} a\n * @param {string} b\n */\n" +
        "function f(a, b) {\n" +
        "  /** @type {string} */\n" +
        "  var x = a && b;\n" +
        "}\n";
    Result r = compileAndCheck(js);
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // AND: string && string mismatches declared number -> warning
  @Test
  public void testTraverseShortCircuit_andStrings_assignToNumber_warning() throws Throwable {
    String js = "/**\n * @param {string} a\n * @param {string} b\n */\n" +
        "function f(a, b) {\n" +
        "  /** @type {number} */\n" +
        "  var x = a && b;\n" +
        "}\n";
    Result r = compileAndCheck(js);
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // ARRAYLIT: array literal inferred as Array, matches declared type
  @Test
  public void testTraverseArrayLiteral_assignToArray_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {Array} */\nvar x = [1, 2, 3];\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // ARRAYLIT: array literal mismatches declared string -> warning
  @Test
  public void testTraverseArrayLiteral_assignToString_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {string} */\nvar x = [1, 2, 3];\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // NEW: constructor call infers instance type, matches declared type
  @Test
  public void testTraverseNew_assignToObject_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {Object} */\nvar x = new Object();\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // NEW: constructor call mismatches declared number -> warning
  @Test
  public void testTraverseNew_assignToNumber_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = new Object();\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // TYPEOF: typeof always inferred as string, matches declared type
  @Test
  public void testTraverseTypeof_assignToString_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {string} */\nvar x = typeof 5;\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // TYPEOF: typeof mismatches declared number -> warning
  @Test
  public void testTraverseTypeof_assignToNumber_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = typeof 5;\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // LT: comparison operators always inferred as boolean, matches declared type
  @Test
  public void testTraverseComparison_lt_assignToBoolean_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {boolean} */\nvar x = (1 < 2);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // LT: comparison result mismatches declared string -> warning
  @Test
  public void testTraverseComparison_lt_assignToString_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {string} */\nvar x = (1 < 2);\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // DIV: arithmetic operators always inferred as number, matches declared type
  @Test
  public void testTraverseArithmetic_div_assignToNumber_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = (10 / 2);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // DIV: arithmetic result mismatches declared string -> warning
  @Test
  public void testTraverseArithmetic_div_assignToString_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {string} */\nvar x = (10 / 2);\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // COMMA: comma expression takes type of last child (number), matches declared type
  @Test
  public void testTraverseComma_assignToNumber_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {number} */\nvar x = (1, 2);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // COMMA: comma expression type mismatches declared string -> warning
  @Test
  public void testTraverseComma_assignToString_warning() throws Throwable {
    Result r = compileAndCheck("/** @type {string} */\nvar x = (1, 2);\n");
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // RETURN: returned value type matches declared @return type, no warning
  @Test
  public void testTraverseReturn_matchingType_noWarning() throws Throwable {
    String js = "/**\n * @return {number}\n */\n" +
        "function f() {\n" +
        "  return 1;\n" +
        "}\n";
    Result r = compileAndCheck(js);
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // RETURN: returned value type mismatches declared @return type -> warning
  @Test
  public void testTraverseReturn_mismatchedType_warning() throws Throwable {
    String js = "/**\n * @return {string}\n */\n" +
        "function f() {\n" +
        "  return 1;\n" +
        "}\n";
    Result r = compileAndCheck(js);
    assertEquals(0, r.errors.length);
    assertTrue(r.warnings.length > 0);
  }

  // CATCH: caught exception variable is treated as unknown type, so no mismatch warning
  @Test
  public void testTraverseCatch_unknownType_noWarning() throws Throwable {
    String js = "function f() {\n" +
        "  try {\n" +
        "  } catch (e) {\n" +
        "    /** @type {number} */\n" +
        "    var x = e;\n" +
        "  }\n" +
        "}\n";
    Result r = compileAndCheck(js);
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // INSTANCEOF: instanceof always inferred as boolean, matches declared type
  @Test
  public void testTraverseInstanceof_assignToBoolean_noWarning() throws Throwable {
    Result r = compileAndCheck("/** @type {boolean} */\nvar x = (1 instanceof Object);\n");
    assertEquals(0, r.errors.length);
    assertEquals(0, r.warnings.length);
  }

  // getBooleanOutcomes AND: left always true -> outcome equals right (TRUE)
  @Test
  public void testGetBooleanOutcomes_andLeftAlwaysTrue_resultTrue() throws Throwable {
    BooleanLiteralSet left = BooleanLiteralSet.get(true);
    BooleanLiteralSet right = BooleanLiteralSet.get(true);
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(left, right, true);
    assertEquals(BooleanLiteralSet.get(true), result);
  }

  // getBooleanOutcomes AND: left always false -> outcome is union(right, FALSE) = BOTH
  @Test
  public void testGetBooleanOutcomes_andLeftAlwaysFalse_resultBoth() throws Throwable {
    BooleanLiteralSet left = BooleanLiteralSet.get(false);
    BooleanLiteralSet right = BooleanLiteralSet.get(true);
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(left, right, true);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // getBooleanOutcomes AND: left BOTH, right EMPTY -> outcome is FALSE
  @Test
  public void testGetBooleanOutcomes_andLeftBothRightEmpty_resultFalse() throws Throwable {
    BooleanLiteralSet result =
        TypeInference.getBooleanOutcomes(BooleanLiteralSet.BOTH, BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.get(false), result);
  }

  // getBooleanOutcomes AND: left EMPTY, right TRUE -> outcome is TRUE
  @Test
  public void testGetBooleanOutcomes_andLeftEmptyRightTrue_resultTrue() throws Throwable {
    BooleanLiteralSet result =
        TypeInference.getBooleanOutcomes(BooleanLiteralSet.EMPTY, BooleanLiteralSet.get(true), true);
    assertEquals(BooleanLiteralSet.get(true), result);
  }

  // getBooleanOutcomes OR: left TRUE, right FALSE -> outcome is union(FALSE, TRUE) = BOTH
  @Test
  public void testGetBooleanOutcomes_orLeftTrueRightFalse_resultBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.get(true), BooleanLiteralSet.get(false), false);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // getBooleanOutcomes OR: left FALSE, right FALSE -> outcome is FALSE
  @Test
  public void testGetBooleanOutcomes_orLeftFalseRightFalse_resultFalse() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.get(false), BooleanLiteralSet.get(false), false);
    assertEquals(BooleanLiteralSet.get(false), result);
  }

  // getBooleanOutcomes OR: left BOTH, right EMPTY -> outcome is TRUE
  @Test
  public void testGetBooleanOutcomes_orLeftBothRightEmpty_resultTrue() throws Throwable {
    BooleanLiteralSet result =
        TypeInference.getBooleanOutcomes(BooleanLiteralSet.BOTH, BooleanLiteralSet.EMPTY, false);
    assertEquals(BooleanLiteralSet.get(true), result);
  }

  // getBooleanOutcomes OR: left EMPTY, right BOTH -> outcome is BOTH
  @Test
  public void testGetBooleanOutcomes_orLeftEmptyRightBoth_resultBoth() throws Throwable {
    BooleanLiteralSet result =
        TypeInference.getBooleanOutcomes(BooleanLiteralSet.EMPTY, BooleanLiteralSet.BOTH, false);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // getBooleanOutcomes AND: left BOTH, right BOTH -> outcome is BOTH
  @Test
  public void testGetBooleanOutcomes_andLeftBothRightBoth_resultBoth() throws Throwable {
    BooleanLiteralSet result =
        TypeInference.getBooleanOutcomes(BooleanLiteralSet.BOTH, BooleanLiteralSet.BOTH, true);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // getBooleanOutcomes AND: left EMPTY, right EMPTY -> outcome is EMPTY
  @Test
  public void testGetBooleanOutcomes_andLeftEmptyRightEmpty_resultEmpty() throws Throwable {
    BooleanLiteralSet result =
        TypeInference.getBooleanOutcomes(BooleanLiteralSet.EMPTY, BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.EMPTY, result);
  }
}
