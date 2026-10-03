package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class PeepholeFoldConstantsClaudeTest {

  private Compiler lastCompiler;

  private Result runFold(String js) throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setFoldConstants(true);
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js", js);
    Result result = compiler.compile(externs, input, options);
    lastCompiler = compiler;
    return result;
  }

  private String source(String js) throws Throwable {
    runFold(js);
    return lastCompiler.toSource();
  }

  // tryFoldArithmeticOp/ADD: numeric literals are summed.
  @Test
  public void testAdd_twoNumbers_foldsToSum() throws Throwable {
    String out = source("var x = 1 + 2;");
    assertTrue(out.contains("x=3"));
  }

  // tryFoldAddConstantString: two string literals concatenate.
  @Test
  public void testAdd_twoStrings_foldsToConcat() throws Throwable {
    String out = source("var s = 'foo' + 'bar';");
    assertTrue(out.contains("foobar"));
  }

  // tryFoldAddConstantString: string + number literal concatenates using string form of number.
  @Test
  public void testAdd_stringPlusNumber_foldsToConcat() throws Throwable {
    String out = source("var s = 'a' + 5;");
    assertTrue(out.contains("a5"));
  }

  // tryFoldChildAddString: (foo() + 'a') + 'b' combines trailing string literals.
  @Test
  public void testChildAddString_chainWithNonLiteralLeft_combinesStrings() throws Throwable {
    String out = source("var s = foo() + 'a' + 'b';");
    assertTrue(out.contains("ab"));
    assertTrue(out.contains("foo("));
  }

  // tryFoldArithmeticOp/SUB: numeric subtraction.
  @Test
  public void testArithmetic_subtraction_foldsToDifference() throws Throwable {
    String out = source("var x = 10 - 3;");
    assertTrue(out.contains("x=7"));
  }

  // tryFoldArithmeticOp/DIV: numeric division.
  @Test
  public void testArithmetic_division_foldsToQuotient() throws Throwable {
    String out = source("var x = 10 / 4;");
    assertTrue(out.contains("2.5"));
  }

  // tryFoldArithmeticOp/MOD: numeric modulo.
  @Test
  public void testArithmetic_modulo_foldsToRemainder() throws Throwable {
    String out = source("var x = 10 % 3;");
    assertTrue(out.contains("x=1"));
  }



  // tryFoldTypeof: typeof of a string literal.
  @Test
  public void testTypeof_stringLiteral_foldsToStringType() throws Throwable {
    String out = source("var t = typeof 'hi';");
    assertTrue(out.contains("string"));
  }

  // tryFoldTypeof: typeof of a number literal.
  @Test
  public void testTypeof_numberLiteral_foldsToNumberType() throws Throwable {
    String out = source("var t = typeof 123;");
    assertTrue(out.contains("number"));
  }

  // tryFoldTypeof: typeof of a boolean literal.
  @Test
  public void testTypeof_booleanLiteral_foldsToBooleanType() throws Throwable {
    String out = source("var t = typeof true;");
    assertTrue(out.contains("boolean"));
  }

  // tryFoldTypeof: typeof null folds to "object" per JS spec.
  @Test
  public void testTypeof_null_foldsToObjectType() throws Throwable {
    String out = source("var t = typeof null;");
    assertTrue(out.contains("object"));
  }





  // tryFoldUnaryOperator/POS: unary plus on a numeric literal is a no-op removal.
  @Test
  public void testPos_numericLiteral_removesUnaryPlus() throws Throwable {
    String out = source("var x = +5;");
    assertTrue(out.contains("x=5"));
  }



  // tryFoldUnaryOperator/BITNOT: bitwise not of an integer.
  @Test
  public void testBitnot_integer_foldsToComplement() throws Throwable {
    String out = source("var x = ~5;");
    assertTrue(out.contains("-6"));
  }

  // tryFoldUnaryOperator/BITNOT: value outside int range reports BITWISE_OPERAND_OUT_OF_RANGE.
  @Test
  public void testBitnot_outOfIntRange_reportsError() throws Throwable {
    Result result = runFold("var x = ~3000000000;");
    assertFalse(result.success);
  }

  // tryFoldUnaryOperator/BITNOT: fractional operand reports FRACTIONAL_BITWISE_OPERAND.
  @Test
  public void testBitnot_fractionalOperand_reportsError() throws Throwable {
    Result result = runFold("var x = ~5.5;");
    assertFalse(result.success);
  }



  // tryReduceVoid: operand with side effects is left untouched.
  @Test
  public void testReduceVoid_sideEffectOperand_notNormalized() throws Throwable {
    String out = source("void foo();");
    assertTrue(out.contains("foo("));
    assertFalse(out.contains("void 0"));
  }

  // tryFoldGetProp: array literal .length folds to element count.
  @Test
  public void testGetProp_arrayLiteralLength_foldsToCount() throws Throwable {
    String out = source("var x = [1,2,3].length;");
    assertTrue(out.contains("x=3"));
  }

  // tryFoldGetProp: string literal .length folds to its character count.
  @Test
  public void testGetProp_stringLiteralLength_foldsToLength() throws Throwable {
    String out = source("var x = 'hello'.length;");
    assertTrue(out.contains("x=5"));
  }

  // tryFoldGetProp: array with side-effecting element is not folded for .length.
  @Test
  public void testGetProp_arrayWithSideEffects_lengthNotFolded() throws Throwable {
    String out = source("var x = [foo()].length;");
    assertTrue(out.contains("foo("));
    assertTrue(out.contains("length"));
  }

  // tryFoldObjectPropAccess: object literal property access resolves to its value.
  @Test
  public void testObjectPropAccess_simpleProperty_resolvesToValue() throws Throwable {
    String out = source("var x = ({a:5}).a;");
    assertTrue(out.contains("x=5"));
  }







  // tryFoldAndOr: FALSE || x reduces to x.
  @Test
  public void testAndOr_falseOr_reducesToRightOperand() throws Throwable {
    String out = source("var y = false || x;");
    assertTrue(out.contains("y=x"));
  }

  // tryFoldAndOr: truthy && x reduces to x.
  @Test
  public void testAndOr_truthyAnd_reducesToRightOperand() throws Throwable {
    String out = source("var y = 5 && x;");
    assertTrue(out.contains("y=x"));
  }

  // tryFoldShift/LSH: left shift of integers.
  @Test
  public void testShift_leftShift_foldsToShiftedValue() throws Throwable {
    String out = source("var x = 1 << 3;");
    assertTrue(out.contains("x=8"));
  }

  // tryFoldShift/RSH: arithmetic right shift of a negative integer.
  @Test
  public void testShift_rightShift_foldsPreservingSign() throws Throwable {
    String out = source("var x = -8 >> 1;");
    assertTrue(out.contains("-4"));
  }

  // tryFoldShift/URSH: unsigned right shift treats operand as unsigned 32-bit.
  @Test
  public void testShift_unsignedRightShift_foldsAsUnsigned() throws Throwable {
    String out = source("var x = -1 >>> 28;");
    assertTrue(out.contains("x=15"));
  }

  // tryFoldShift: shift amount outside [0,32) reports SHIFT_AMOUNT_OUT_OF_BOUNDS.
  @Test
  public void testShift_amountOutOfBounds_reportsError() throws Throwable {
    Result result = runFold("var x = 5 << 33;");
    assertFalse(result.success);
  }

  // tryFoldShift: fractional shift amount reports FRACTIONAL_BITWISE_OPERAND.
  @Test
  public void testShift_fractionalAmount_reportsError() throws Throwable {
    Result result = runFold("var x = 5 << 1.5;");
    assertFalse(result.success);
  }

  // tryFoldArrayAccess: index 0 on a non-empty array must fold to the first element (bug: off-by-one loop).
  @Test
  public void testArrayAccess_indexZero_foldsToFirstElement() throws Throwable {
    Result result = runFold("var x = [10, 20, 30][0];");
    assertTrue(result.success);
    assertTrue(lastCompiler.toSource().contains("x=10"));
  }

  // tryFoldArrayAccess: index 1 must fold to the second element, not the first (bug: off-by-one loop).
  @Test
  public void testArrayAccess_indexOne_foldsToSecondElement() throws Throwable {
    Result result = runFold("var x = [10, 20, 30][1];");
    assertTrue(result.success);
    assertTrue(lastCompiler.toSource().contains("x=20"));
  }

  // tryFoldArrayAccess: an index beyond the array length must report INDEX_OUT_OF_BOUNDS_ERROR.
  @Test
  public void testArrayAccess_indexBeyondLength_reportsOutOfBoundsError() throws Throwable {
    Result result = runFold("var x = [10, 20, 30][5];");
    assertFalse(result.success);
  }

  // tryFoldArrayAccess: negative index reports INDEX_OUT_OF_BOUNDS_ERROR.
  @Test
  public void testArrayAccess_negativeIndex_reportsError() throws Throwable {
    Result result = runFold("var x = [10, 20, 30][-1];");
    assertFalse(result.success);
  }

  // tryFoldArrayAccess: non-integer index reports INVALID_GETELEM_INDEX_ERROR.
  @Test
  public void testArrayAccess_fractionalIndex_reportsError() throws Throwable {
    Result result = runFold("var x = [10, 20, 30][1.5];");
    assertFalse(result.success);
  }










}
