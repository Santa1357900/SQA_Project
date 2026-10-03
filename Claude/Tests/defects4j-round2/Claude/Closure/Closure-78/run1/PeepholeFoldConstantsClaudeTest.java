package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class PeepholeFoldConstantsClaudeTest {

  private String foldedSource(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setFoldConstants(true);
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(extern, input, options);
    return compiler.toSource();
  }

  private int errorCount(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setFoldConstants(true);
    SourceFile extern = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("test.js", js);
    compiler.compile(extern, input, options);
    return compiler.getErrors().length;
  }

  // tryFoldAdd: numeric ADD operands fold to sum
  @Test
  public void testTryFoldAdd_numericOperands_foldsToSum() throws Throwable {
    String out = foldedSource("var x = 1 + 2;");
    assertTrue(out.contains("x=3"));
  }

  // tryFoldAdd -> tryFoldAddConstantString: string ADD folds to concatenation
  @Test
  public void testTryFoldAdd_stringOperands_foldsToConcatenation() throws Throwable {
    String out = foldedSource("var x = 'a' + 'b';");
    assertTrue(out.contains("ab"));
  }

  // tryFoldArithmeticOp: SUB, MUL, DIV, MOD branches
  @Test
  public void testTryFoldArithmeticOp_subMulDivMod_foldCorrectly() throws Throwable {
    String out = foldedSource("var a = 5 - 3; var b = 4 * 5; var c = 10 / 2; var d = 10 % 3;");
    assertTrue(out.contains("a=2"));
    assertTrue(out.contains("b=20"));
    assertTrue(out.contains("c=5"));
    assertTrue(out.contains("d=1"));
  }

  // performArithmeticOp: divide by zero reports error, does not fold
  @Test
  public void testTryFoldArithmeticOp_divisionByZero_reportsError() throws Throwable {
    assertTrue(errorCount("var x = 10 / 0;") > 0);
  }

  // performArithmeticOp: result exceeding MAX_FOLD_NUMBER is not folded
  @Test
  public void testTryFoldArithmeticOp_largeResultExceedsMaxFold_doesNotFold() throws Throwable {
    String out = foldedSource("var x = 1e300 * 2;");
    assertTrue(out.contains("*"));
  }

  // tryFoldBinaryOperator: BITAND, BITOR, BITXOR fold to int32 result
  @Test
  public void testTryFoldArithmeticOp_bitwiseAndOrXor_foldToInt32Result() throws Throwable {
    String out = foldedSource("var a = 6 & 3; var b = 6 | 1; var c = 6 ^ 3;");
    assertTrue(out.contains("a=2"));
    assertTrue(out.contains("b=7"));
    assertTrue(out.contains("c=5"));
  }

  // tryFoldShift: LSH, RSH, URSH fold correctly
  @Test
  public void testTryFoldShift_leftRightUnsignedRight_foldCorrectly() throws Throwable {
    String out = foldedSource("var a = 1 << 3; var b = 8 >> 2; var c = 16 >>> 2;");
    assertTrue(out.contains("a=8"));
    assertTrue(out.contains("b=2"));
    assertTrue(out.contains("c=4"));
  }

  // tryFoldShift: shift amount out of [0,32) reports error
  @Test
  public void testTryFoldShift_amountOutOfBounds_reportsError() throws Throwable {
    assertTrue(errorCount("var x = 1 << 33;") > 0);
  }

  // tryFoldShift: fractional operand reports error
  @Test
  public void testTryFoldShift_fractionalOperand_reportsError() throws Throwable {
    assertTrue(errorCount("var x = 1.5 << 2;") > 0);
  }

  // tryFoldUnaryOperator NOT: non-zero/non-one number folds to boolean literal
  @Test
  public void testTryFoldUnaryOperator_not_nonZeroNonOneNumber_foldsToBoolean() throws Throwable {
    String out = foldedSource("var x = !5;");
    assertTrue(out.contains("false"));
  }

  // tryFoldUnaryOperator NOT: !0 and !1 are intentionally left unfolded
  @Test
  public void testTryFoldUnaryOperator_not_zeroOrOne_doesNotFold() throws Throwable {
    String out = foldedSource("var a = !0; var b = !1;");
    assertTrue(out.contains("!0"));
    assertTrue(out.contains("!1"));
  }

  // tryFoldUnaryOperator NEG: nested ADD result folds recursively to -5
  @Test
  public void testTryFoldUnaryOperator_neg_nestedAddition_foldsRecursively() throws Throwable {
    String out = foldedSource("var x = -(3+2);");
    assertTrue(out.contains("-5"));
  }

  // tryFoldUnaryOperator NEG: non-numeric operand reports error
  @Test
  public void testTryFoldUnaryOperator_neg_nonNumericOperand_reportsError() throws Throwable {
    assertTrue(errorCount("var x = -'abc';") > 0);
  }

  // tryFoldUnaryOperator BITNOT: folds to bitwise complement
  @Test
  public void testTryFoldUnaryOperator_bitnot_foldsToComplement() throws Throwable {
    String out = foldedSource("var x = ~5;");
    assertTrue(out.contains("-6"));
  }

  // tryFoldUnaryOperator BITNOT: out-of-range and fractional operands report errors
  @Test
  public void testTryFoldUnaryOperator_bitnot_outOfRangeAndFractional_reportErrors() throws Throwable {
    assertTrue(errorCount("var x = ~2147483648;") > 0);
    assertTrue(errorCount("var y = ~2.5;") > 0);
  }

  // tryFoldTypeof: literal number/string/null fold to their type strings
  @Test
  public void testTryFoldTypeof_literalOperands_foldToExpectedTypeStrings() throws Throwable {
    String out = foldedSource("var a=typeof 5; var b=typeof 'x'; var c=typeof null;");
    assertTrue(out.contains("number"));
    assertTrue(out.contains("string"));
    assertTrue(out.contains("object"));
  }

  // tryFoldTypeof: non-literal operand (call) is not folded
  @Test
  public void testTryFoldTypeof_nonLiteralOperand_doesNotFold() throws Throwable {
    String out = foldedSource("var x = typeof foo();");
    assertTrue(out.contains("typeof"));
  }

  // tryReduceVoid: non-zero operand without side effects reduces to "void 0"
  @Test
  public void testTryReduceVoid_nonZeroOperand_reducesToVoidZero() throws Throwable {
    String out = foldedSource("var x = void (1+2);");
    assertTrue(out.contains("void 0"));
  }

  // tryReduceVoid: operand with side effects is left untouched
  @Test
  public void testTryReduceVoid_sideEffectOperand_doesNotFold() throws Throwable {
    String out = foldedSource("var x = void foo();");
    assertTrue(out.contains("foo()"));
    assertTrue(out.contains("void"));
  }

  // tryFoldInstanceof: immutable literal left operand folds to false
  @Test
  public void testTryFoldInstanceof_immutableLeft_foldsToFalse() throws Throwable {
    String out = foldedSource("var x = 5 instanceof Object;");
    assertTrue(out.contains("false"));
  }

  // tryFoldInstanceof: object literal vs "Object" folds to true
  @Test
  public void testTryFoldInstanceof_objectLiteralAgainstObject_foldsToTrue() throws Throwable {
    String out = foldedSource("var x = {} instanceof Object;");
    assertTrue(out.contains("true"));
  }

  // tryFoldAssign: x = x + y folds to compound assignment x += y
  @Test
  public void testTryFoldAssign_matchingOperand_foldsToCompoundAssign() throws Throwable {
    String out = foldedSource("var a=1; var b=2; a = a + b;");
    assertTrue(out.contains("+="));
  }

  // tryFoldAssign: non-commutative SUB with mismatched operand order does not fold
  @Test
  public void testTryFoldAssign_nonCommutativeMismatch_doesNotFold() throws Throwable {
    String out = foldedSource("var a=1; var b=2; a = b - a;");
    assertFalse(out.contains("-="));
    assertTrue(out.contains("b-a"));
  }

  // tryFoldAndOr: truthy/falsy left operand picks correct branch per JS semantics
  @Test
  public void testTryFoldAndOr_variousTruthiness_foldCorrectly() throws Throwable {
    String out = foldedSource("var a = 1 && 2; var b = 0 || 5; var c = 0 && 5;");
    assertTrue(out.contains("a=2"));
    assertTrue(out.contains("b=5"));
    assertTrue(out.contains("c=0"));
  }

  // tryFoldComparison NUMBER: literal less-than folds to true
  @Test
  public void testTryFoldComparison_numberLessThan_foldsToTrue() throws Throwable {
    String out = foldedSource("var x = (3 < 5);");
    assertTrue(out.contains("true"));
  }

  // tryFoldComparison NAME: same name always false, different names unfolded
  @Test
  public void testTryFoldComparison_sameNameVsDifferentName_foldAppropriately() throws Throwable {
    String out = foldedSource("var x = (a < a); var y = (a < b);");
    assertTrue(out.contains("x=false"));
    assertTrue(out.contains("a<b"));
  }

  // tryFoldComparison STRING: equality compares string content
  @Test
  public void testTryFoldComparison_stringEquality_foldsCorrectly() throws Throwable {
    String out = foldedSource("var a = ('cat' == 'cat'); var b = ('cat' == 'dog');");
    assertTrue(out.contains("a=true"));
    assertTrue(out.contains("b=false"));
  }

  // tryFoldComparison NAME/undefined: loose equality true, strict equality false
  @Test
  public void testTryFoldComparison_nullVsUndefined_looseAndStrictEquality() throws Throwable {
    String out = foldedSource("var a = (undefined == null); var b = (undefined === null);");
    assertTrue(out.contains("a=true"));
    assertTrue(out.contains("b=false"));
  }

  // tryFoldComparison THIS: this == this / this != this fold correctly
  @Test
  public void testTryFoldComparison_thisEquality_foldsCorrectly() throws Throwable {
    String out = foldedSource(
        "function f(){ return this == this; } function g(){ return this != this; }");
    assertTrue(out.contains("true"));
    assertTrue(out.contains("false"));
  }

  // tryFoldComparison: mismatched literal types (string vs number) are not folded
  @Test
  public void testTryFoldComparison_mixedLiteralTypes_doesNotFold() throws Throwable {
    String out = foldedSource("var x = ('5' == 5);");
    assertTrue(out.contains("=="));
  }

  // tryFoldKnownStringMethods: toLowerCase / toUpperCase fold correctly
  @Test
  public void testTryFoldKnownStringMethods_caseConversion_folds() throws Throwable {
    String out = foldedSource("var a = 'ABC'.toLowerCase(); var b = 'abc'.toUpperCase();");
    assertTrue(out.contains("\"abc\""));
    assertTrue(out.contains("\"ABC\""));
  }

  // tryFoldStringIndexOf: indexOf and lastIndexOf fold correctly
  @Test
  public void testTryFoldStringIndexOf_indexOfAndLastIndexOf_fold() throws Throwable {
    String out = foldedSource(
        "var a = 'abcdef'.indexOf('cd'); var b = 'abcabc'.lastIndexOf('a');");
    assertTrue(out.contains("a=2"));
    assertTrue(out.contains("b=3"));
  }

  // tryFoldStringSubstr / tryFoldStringSubstring fold correctly
  @Test
  public void testTryFoldStringSubstrAndSubstring_fold() throws Throwable {
    String out = foldedSource(
        "var a = 'hello'.substr(1,3); var b = 'hello'.substring(1,3);");
    assertTrue(out.contains("\"ell\""));
    assertTrue(out.contains("\"el\""));
  }

  // tryFoldArrayJoin: non-empty and empty array join fold correctly
  @Test
  public void testTryFoldArrayJoin_nonEmptyAndEmpty_fold() throws Throwable {
    String out = foldedSource("var a = [1,2,3].join('-'); var b = [].join(',');");
    assertTrue(out.contains("\"1-2-3\""));
    assertTrue(out.contains("b=\"\""));
  }

  // tryFoldGetElem: valid numeric index into array literal folds to element
  @Test
  public void testTryFoldGetElem_validIndex_folds() throws Throwable {
    String out = foldedSource("var x = [10,20,30][1];");
    assertTrue(out.contains("x=20"));
  }

  // tryFoldGetElem: out-of-bounds and non-integer indices report errors
  @Test
  public void testTryFoldGetElem_outOfBoundsAndNonInteger_reportErrors() throws Throwable {
    assertTrue(errorCount("var x = [10,20,30][5];") > 0);
    assertTrue(errorCount("var y = [10,20,30][1.5];") > 0);
  }

  // tryFoldGetProp: array length and string length fold correctly
  @Test
  public void testTryFoldGetProp_arrayAndStringLength_fold() throws Throwable {
    String out = foldedSource("var a = [1,2,3].length; var b = 'hello'.length;");
    assertTrue(out.contains("a=3"));
    assertTrue(out.contains("b=5"));
  }

  // tryFoldCtorCall: new String(...) in forced string (GETELEM) context folds
  @Test
  public void testTryFoldCtorCall_newStringInGetElemContext_folds() throws Throwable {
    String out = foldedSource("var obj = {}; var x = obj[new String('foo')];");
    assertTrue(out.contains("foo"));
    assertFalse(out.contains("String"));
  }
}
