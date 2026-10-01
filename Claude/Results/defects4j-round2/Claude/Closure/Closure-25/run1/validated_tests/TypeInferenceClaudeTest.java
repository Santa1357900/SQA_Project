package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.javascript.rhino.jstype.BooleanLiteralSet;

public class TypeInferenceClaudeTest {

  // ---- helpers -----------------------------------------------------

  private Result runTypeCheck(String js) {
    Compiler localCompiler = new Compiler();
    CompilerOptions localOptions = new CompilerOptions();
    localOptions.setCheckTypes(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", js);
    return localCompiler.compile(externs, input, localOptions);
  }

  private int diagnosticCount(Result result) {
    return result.warnings.length + result.errors.length;
  }

  // ---- TypeInference.getBooleanOutcomes -----------------------------
  // formula: right.union(left.intersection(BooleanLiteralSet.get(!condition)))

  // EMPTY intersect anything = EMPTY, EMPTY union EMPTY = EMPTY
  @Test
  public void testGetBooleanOutcomes_emptyEmptyTrue_returnsEmpty() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.EMPTY, BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.EMPTY, result);
  }

  // same as above with condition=false
  @Test
  public void testGetBooleanOutcomes_emptyEmptyFalse_returnsEmpty() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.EMPTY, BooleanLiteralSet.EMPTY, false);
    assertEquals(BooleanLiteralSet.EMPTY, result);
  }

  // BOTH is the universal set: BOTH.intersection(X) == X; EMPTY.union(X) == X
  @Test
  public void testGetBooleanOutcomes_bothEmptyConditionTrue_returnsGetFalse() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.BOTH, BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.get(false), result);
  }

  // condition=false flips which singleton is selected
  @Test
  public void testGetBooleanOutcomes_bothEmptyConditionFalse_returnsGetTrue() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.BOTH, BooleanLiteralSet.EMPTY, false);
    assertEquals(BooleanLiteralSet.get(true), result);
  }

  // right == BOTH forces the union to stay BOTH regardless of left
  @Test
  public void testGetBooleanOutcomes_emptyBothConditionTrue_returnsBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.EMPTY, BooleanLiteralSet.BOTH, true);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // right == BOTH, condition=false
  @Test
  public void testGetBooleanOutcomes_emptyBothConditionFalse_returnsBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.EMPTY, BooleanLiteralSet.BOTH, false);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // left == BOTH, right == BOTH -> still BOTH
  @Test
  public void testGetBooleanOutcomes_bothBothConditionTrue_returnsBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.BOTH, BooleanLiteralSet.BOTH, true);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // left == BOTH, right == BOTH, condition=false -> still BOTH
  @Test
  public void testGetBooleanOutcomes_bothBothConditionFalse_returnsBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.BOTH, BooleanLiteralSet.BOTH, false);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // get(true) and get(false) are disjoint singleton sets -> intersection is EMPTY
  @Test
  public void testGetBooleanOutcomes_getTrueEmptyConditionTrue_returnsEmpty() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.get(true), BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.EMPTY, result);
  }

  // get(true) intersect get(true) (condition=false -> !condition=true) is idempotent
  @Test
  public void testGetBooleanOutcomes_getTrueEmptyConditionFalse_returnsGetTrue() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.get(true), BooleanLiteralSet.EMPTY, false);
    assertEquals(BooleanLiteralSet.get(true), result);
  }

  // right == BOTH absorbs any left contribution
  @Test
  public void testGetBooleanOutcomes_getFalseBothConditionTrue_returnsBoth() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.get(false), BooleanLiteralSet.BOTH, true);
    assertEquals(BooleanLiteralSet.BOTH, result);
  }

  // disjoint singleton intersection yields EMPTY
  @Test
  public void testGetBooleanOutcomes_getFalseEmptyConditionFalse_returnsEmpty() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.get(false), BooleanLiteralSet.EMPTY, false);
    assertEquals(BooleanLiteralSet.EMPTY, result);
  }

  // idempotent intersection with same singleton
  @Test
  public void testGetBooleanOutcomes_getFalseEmptyConditionTrue_returnsGetFalse() throws Throwable {
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(
        BooleanLiteralSet.get(false), BooleanLiteralSet.EMPTY, true);
    assertEquals(BooleanLiteralSet.get(false), result);
  }

  // ---- DiagnosticType static fields ---------------------------------

  // field is initialized via DiagnosticType.warning(...)
  @Test
  public void testTemplateTypeNotObjectTypeDiagnostic_isDefined() throws Throwable {
    assertNotNull(TypeInference.TEMPLATE_TYPE_NOT_OBJECT_TYPE);
  }

  // field is initialized via DiagnosticType.warning(...)
  @Test
  public void testTemplateTypeOfThisExpectedDiagnostic_isDefined() throws Throwable {
    assertNotNull(TypeInference.TEMPLATE_TYPE_OF_THIS_EXPECTED);
  }

  // field is initialized via DiagnosticType.warning(...)
  @Test
  public void testFunctionLiteralUndefinedThisDiagnostic_isDefined() throws Throwable {
    assertNotNull(TypeInference.FUNCTION_LITERAL_UNDEFINED_THIS);
  }

  // the three diagnostics must be distinct instances
  @Test
  public void testDiagnosticTypesAreDistinctInstances() throws Throwable {
    assertNotSame(TypeInference.TEMPLATE_TYPE_NOT_OBJECT_TYPE,
        TypeInference.TEMPLATE_TYPE_OF_THIS_EXPECTED);
    assertNotSame(TypeInference.TEMPLATE_TYPE_OF_THIS_EXPECTED,
        TypeInference.FUNCTION_LITERAL_UNDEFINED_THIS);
  }

  // ---- traverseAdd (Token.ADD) branch: string wins over number -------

  // 'a' + 1 -> string,  // matches function expecting string, no diagnostics
  @Test
  public void testTraverseAdd_stringPlusNumber_inferredAsString_noWarning() throws Throwable {
    String js = "/** @param {string} s */\n"
        + "function f(s) {}\n"
        + "f('a' + 1);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // 1 + 2 -> number, mismatched against string param -> diagnostic
  @Test
  public void testTraverseAdd_numberPlusNumber_inferredAsNumber_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n"
        + "function f(s) {}\n"
        + "f(1 + 2);\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- arithmetic ops force NUMBER_TYPE (Token.MUL) -------------------

  // 2 * 3 -> number, matches number param
  @Test
  public void testTraverseChildren_multiplication_inferredAsNumber_noWarning() throws Throwable {
    String js = "/** @param {number} n */\n"
        + "function f(n) {}\n"
        + "f(2 * 3);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // 2 * 3 -> number, mismatched against string param -> diagnostic
  @Test
  public void testTraverseChildren_multiplication_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n"
        + "function f(s) {}\n"
        + "f(2 * 3);\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- comparison ops force BOOLEAN_TYPE (Token.LT) -------------------

  // 1 < 2 -> boolean, matches boolean param
  @Test
  public void testTraverseChildren_comparison_inferredAsBoolean_noWarning() throws Throwable {
    String js = "/** @param {boolean} b */\n"
        + "function f(b) {}\n"
        + "f(1 < 2);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // 1 < 2 -> boolean, mismatched against string param -> diagnostic
  @Test
  public void testTraverseChildren_comparison_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n"
        + "function f(s) {}\n"
        + "f(1 < 2);\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- Token.TYPEOF always yields STRING_TYPE -------------------------

  // typeof 5 -> string, matches string param
  @Test
  public void testTraverseChildren_typeof_inferredAsString_noWarning() throws Throwable {
    String js = "/** @param {string} s */\n"
        + "function f(s) {}\n"
        + "f(typeof 5);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // ---- traverseHook (Token.HOOK) least-supertype join -----------------

  // both branches number -> joined type number, matches number param
  @Test
  public void testTraverseHook_sameBranchTypes_noWarning() throws Throwable {
    String js = "/** @param {number} n */\n"
        + "function f(n) {}\n"
        + "f(true ? 1 : 2);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // branches number|string -> union not assignable to strict number param
  @Test
  public void testTraverseHook_mixedBranchTypes_warnsOnNumberParam() throws Throwable {
    String js = "/** @param {number} n */\n"
        + "function f(n) {}\n"
        + "f(true ? 1 : 'a');\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- traverseNew (Token.NEW) constructor instance type --------------

  // new Foo() matches the declared Foo parameter type
  @Test
  public void testTraverseNew_matchingConstructorType_noWarning() throws Throwable {
    String js = "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {Foo} f */\n"
        + "function bar(f) {}\n"
        + "bar(new Foo());\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // new Bar() passed where unrelated Foo type expected -> diagnostic
  @Test
  public void testTraverseNew_mismatchedConstructorType_warns() throws Throwable {
    String js = "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "/** @param {Foo} f */\n"
        + "function baz(f) {}\n"
        + "baz(new Bar());\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- traverseGetProp (Token.GETPROP) property type resolution -------

  // foo.x declared number, matches number param
  @Test
  public void testTraverseGetProp_matchingPropertyType_noWarning() throws Throwable {
    String js = "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {number} */\n"
        + "  this.x = 1;\n"
        + "}\n"
        + "/** @param {number} n */\n"
        + "function f(n) {}\n"
        + "var foo = new Foo();\n"
        + "f(foo.x);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // foo.x declared number, mismatched against string param -> diagnostic
  @Test
  public void testTraverseGetProp_mismatchedPropertyType_warns() throws Throwable {
    String js = "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {number} */\n"
        + "  this.x = 1;\n"
        + "}\n"
        + "/** @param {string} s */\n"
        + "function f(s) {}\n"
        + "var foo = new Foo();\n"
        + "f(foo.x);\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- traverseCatch (Token.CATCH) treats param as UNKNOWN_TYPE -------

  // an UNKNOWN typed catch param is assignable to a declared string var
  @Test
  public void testTraverseCatch_unknownType_assignableToAnything_noWarning() throws Throwable {
    String js = "function g() {\n"
        + "  try {\n"
        + "    throw 1;\n"
        + "  } catch (e) {\n"
        + "    /** @type {string} */\n"
        + "    var s = e;\n"
        + "  }\n"
        + "}\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // ---- Token.INC forces NUMBER_TYPE -----------------------------------

  // x++ -> number, mismatched against string param -> diagnostic
  @Test
  public void testTraverseName_incrementOperator_inferredAsNumber_warnsOnStringParam() throws Throwable {
    String js = "/** @param {string} s */\n"
        + "function f(s) {}\n"
        + "var x = 5;\n"
        + "f(x++);\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- traverseCall / backward parameter inference --------------------

  // number literal matches declared number parameter
  @Test
  public void testTraverseCall_matchingParamType_noWarning() throws Throwable {
    String js = "/** @param {number} x */\n"
        + "function f(x) {}\n"
        + "f(1);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }

  // string literal mismatched against declared number parameter
  @Test
  public void testTraverseCall_mismatchedParamType_warns() throws Throwable {
    String js = "/** @param {number} x */\n"
        + "function f(x) {}\n"
        + "f('a');\n";
    Result result = runTypeCheck(js);
    assertTrue(diagnosticCount(result) > 0);
  }

  // ---- Token.NEG forces NUMBER_TYPE ------------------------------------

  // -x -> number, matches number param
  @Test
  public void testTraverseChildren_unaryMinus_inferredAsNumber_noWarning() throws Throwable {
    String js = "/** @param {number} n */\n"
        + "function f(n) {}\n"
        + "var x = 5;\n"
        + "f(-x);\n";
    Result result = runTypeCheck(js);
    assertEquals(0, diagnosticCount(result));
  }


}
