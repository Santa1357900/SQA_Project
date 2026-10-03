package org.apache.commons.math.dfp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DfpClaudeTest {

    private DfpField field;

    @Before
    public void setUp() throws Throwable {
        field = new DfpField(20);
    }

    // Constructor(field,long) positive: verifies mant/exp/sign per class javadoc representation
    @Test
    public void testConstructorLong_positive_setsMantExpSign() throws Throwable {
        Dfp d = new Dfp(field, 1000L);
        assertEquals(1000, d.mant[d.mant.length - 1]);
        assertEquals(1, d.exp);
        assertEquals(1, d.sign);
    }

    // Constructor(field,long) negative branch: sign set to -1, magnitude stored positive
    @Test
    public void testConstructorLong_negative_setsSignNegative() throws Throwable {
        Dfp d = new Dfp(field, -5L);
        assertEquals(-1, d.sign);
        assertEquals(5, d.mant[d.mant.length - 1]);
    }

    // Constructor(field,double) normal finite path round-trips through toDouble
    @Test
    public void testConstructorDouble_simpleValue_roundTrips() throws Throwable {
        Dfp d = new Dfp(field, 2.5);
        assertEquals(2.5, d.toDouble(), 1e-9);
    }

    // Constructor(field,double) NaN branch (x!=x) sets QNAN
    @Test
    public void testConstructorDouble_NaN_setsQNaN() throws Throwable {
        Dfp d = new Dfp(field, Double.NaN);
        assertTrue(d.isNaN());
        assertEquals(Dfp.QNAN, d.classify());
    }

    // Constructor(field,double) +/- infinity branches set sign accordingly
    @Test
    public void testConstructorDouble_infinities_setSignCorrectly() throws Throwable {
        Dfp pos = new Dfp(field, Double.POSITIVE_INFINITY);
        Dfp neg = new Dfp(field, Double.NEGATIVE_INFINITY);
        assertTrue(pos.isInfinite());
        assertEquals(1, pos.sign);
        assertTrue(neg.isInfinite());
        assertEquals(-1, neg.sign);
    }

    // Constructor(field,String) normal decimal parsing path
    @Test
    public void testConstructorString_decimal_parsesCorrectly() throws Throwable {
        Dfp d = new Dfp(field, "123.5");
        assertEquals(123.5, d.toDouble(), 1e-9);
    }

    // Constructor(field,String) scientific notation branch (exponent parsing loop)
    @Test
    public void testConstructorString_scientific_parsesCorrectly() throws Throwable {
        Dfp d = new Dfp(field, "1.5e3");
        assertEquals(1500, d.intValue());
    }

    // Constructor(field,String) special tokens: Infinity, -Infinity, NaN
    @Test
    public void testConstructorString_specialTokens_parseCorrectly() throws Throwable {
        assertTrue(new Dfp(field, "Infinity").isInfinite());
        Dfp negInf = new Dfp(field, "-Infinity");
        assertTrue(negInf.isInfinite());
        assertEquals(-1, negInf.sign);
        assertTrue(new Dfp(field, "NaN").isNaN());
    }

    // Copy constructor clones mantissa array (independent storage)
    @Test
    public void testCopyConstructor_independentMantArray() throws Throwable {
        Dfp a = new Dfp(field, 42L);
        Dfp b = new Dfp(a);
        assertTrue(a.equals(b));
        assertNotSame(a.mant, b.mant);
    }

    // newInstance() zero-arg and newInstance(byte) factory branches
    @Test
    public void testNewInstance_zeroAndByte_returnCorrectValues() throws Throwable {
        Dfp base = new Dfp(field, 1L);
        Dfp zero = base.newInstance();
        Dfp fromByte = base.newInstance((byte) 7);
        assertEquals(0, zero.intValue());
        assertEquals(7, fromByte.intValue());
    }

    // newInstance(Dfp) mismatched precision triggers invalid trap -> QNAN
    @Test
    public void testNewInstance_fromDfp_mismatchedPrecision_trapsToNaN() throws Throwable {
        DfpField field2 = new DfpField(5);
        Dfp a = new Dfp(field, 1L);
        Dfp b = new Dfp(field2, 3L);
        Dfp r = a.newInstance(b);
        assertTrue(r.isNaN());
    }



    // getZero/getOne/getTwo return the expected numeric constants
    @Test
    public void testGetZeroOneTwo_expectedIntValues() throws Throwable {
        Dfp d = new Dfp(field, 1L);
        assertEquals(0, d.getZero().intValue());
        assertEquals(1, d.getOne().intValue());
        assertEquals(2, d.getTwo().intValue());
    }

    // lessThan normal ordering branch and NaN-involved branch (returns false)
    @Test
    public void testLessThan_normalAndNaNCases() throws Throwable {
        Dfp a = new Dfp(field, 1L);
        Dfp b = new Dfp(field, 2L);
        Dfp nan = new Dfp(field, (byte) 1, Dfp.QNAN);
        assertTrue(a.lessThan(b));
        assertFalse(b.lessThan(a));
        assertFalse(a.lessThan(nan));
    }

    // greaterThan mismatched precision branch returns false unconditionally
    @Test
    public void testGreaterThan_mismatchedPrecision_returnsFalse() throws Throwable {
        DfpField field2 = new DfpField(8);
        Dfp a = new Dfp(field, 5L);
        Dfp b = new Dfp(field2, 1L);
        assertFalse(a.greaterThan(b));
    }

    // isInfinite/isNaN classification flags for finite, infinite and NaN values
    @Test
    public void testIsInfiniteIsNaN_flagsCorrect() throws Throwable {
        Dfp inf = new Dfp(field, (byte) 1, Dfp.INFINITE);
        Dfp nan = new Dfp(field, (byte) 1, Dfp.QNAN);
        Dfp finite = new Dfp(field, 5L);
        assertTrue(inf.isInfinite());
        assertFalse(inf.isNaN());
        assertTrue(nan.isNaN());
        assertFalse(finite.isInfinite());
    }

    // equals: NaN never equals itself; equal finite values compare equal
    @Test
    public void testEquals_NaNNeverEqual_sameValueEqual() throws Throwable {
        Dfp nan = new Dfp(field, (byte) 1, Dfp.QNAN);
        Dfp a = new Dfp(field, 100L);
        Dfp b = new Dfp(field, 100L);
        assertFalse(nan.equals(nan));
        assertTrue(a.equals(b));
    }

    // unequal: true for different values, false for the same value
    @Test
    public void testUnequal_trueAndFalseCases() throws Throwable {
        Dfp a = new Dfp(field, 1L);
        Dfp b = new Dfp(field, 2L);
        Dfp c = new Dfp(field, 1L);
        assertTrue(a.unequal(b));
        assertFalse(a.unequal(c));
    }

    // floor() rounds toward negative infinity for a negative fractional value
    @Test
    public void testFloor_negativeHalf_roundsTowardNegativeInfinity() throws Throwable {
        Dfp d = new Dfp(field, "-2.5");
        assertEquals(-3, d.floor().intValue());
    }

    // ceil() rounds toward positive infinity for a positive fractional value
    @Test
    public void testCeil_positiveHalf_roundsTowardPositiveInfinity() throws Throwable {
        Dfp d = new Dfp(field, "2.5");
        assertEquals(3, d.ceil().intValue());
    }

    // rint() round-half-even tie breaking at .5 boundary (both even-up and even-down)
    @Test
    public void testRint_tieBreak_roundsToEven() throws Throwable {
        Dfp d1 = new Dfp(field, "2.5");
        Dfp d2 = new Dfp(field, "3.5");
        assertEquals(2, d1.rint().intValue());
        assertEquals(4, d2.rint().intValue());
    }

    // remainder(): IEEE remainder this - n*d, n = nearest integer to this/d
    @Test
    public void testRemainder_basicCase_matchesIEEERemainder() throws Throwable {
        Dfp a = new Dfp(field, 7L);
        Dfp b = new Dfp(field, 3L);
        assertEquals(1, a.remainder(b).intValue());
    }

    // intValue(): clamps results above/below int range per javadoc contract
    @Test
    public void testIntValue_overflowAndUnderflow_clampToIntBounds() throws Throwable {
        Dfp big = new Dfp(field, 1e20);
        Dfp smallNeg = new Dfp(field, -1e20);
        assertEquals(2147483647, big.intValue());
        assertEquals(-2147483648, smallNeg.intValue());
    }

    // log10K(): greatest power-of-10000 exponent <= value, i.e. exp-1
    @Test
    public void testLog10K_matchesContractDefinition() throws Throwable {
        Dfp d = new Dfp(field, 1000L);
        assertEquals(0, d.log10K());
    }

    // power10K(e): produces 10000^e exactly
    @Test
    public void testPower10K_producesCorrectPower() throws Throwable {
        Dfp base = new Dfp(field, 1L);
        Dfp d = base.power10K(2);
        assertEquals(100000000, d.intValue());
    }



    // log10() on a non-boundary mantissa value follows the normal (correct) branch
    @Test
    public void testLog10_nonBoundaryValue_matchesFloorLog10() throws Throwable {
        Dfp d = new Dfp(field, 5000L);
        assertEquals(3, d.log10());
    }

    // power10(e): positive, exact-radix and negative exponent branches
    @Test
    public void testPower10_variousExponents_matchesExpectedValue() throws Throwable {
        Dfp base = new Dfp(field, 1L);
        assertEquals(10, base.power10(1).intValue());
        assertEquals(10000, base.power10(4).intValue());
        assertEquals(0.1, base.power10(-1).toDouble(), 1e-9);
    }

    // add(): NaN operand propagates; infinity + finite stays infinite
    @Test
    public void testAdd_specialCases_NaNAndInfinitePlusFinite() throws Throwable {
        Dfp nan = new Dfp(field, (byte) 1, Dfp.QNAN);
        Dfp inf = new Dfp(field, (byte) 1, Dfp.INFINITE);
        Dfp finite = new Dfp(field, 5L);
        assertTrue(nan.add(finite).isNaN());
        assertTrue(inf.add(finite).isInfinite());
    }

    // add(): opposite-sign infinities and mismatched precision both trap to NaN
    @Test
    public void testAdd_oppositeInfinitiesAndMismatchedPrecision_returnNaN() throws Throwable {
        Dfp posInf = new Dfp(field, (byte) 1, Dfp.INFINITE);
        Dfp negInf = new Dfp(field, (byte) -1, Dfp.INFINITE);
        assertTrue(posInf.add(negInf).isNaN());
        DfpField field2 = new DfpField(6);
        Dfp other = new Dfp(field2, 1L);
        assertTrue(posInf.add(other).isNaN());
    }

    // add(): operands with different exponents exercise align() before summing
    @Test
    public void testAdd_differentExponents_alignsAndSumsCorrectly() throws Throwable {
        Dfp a = new Dfp(field, 1000000L);
        Dfp b = new Dfp(field, 1L);
        assertEquals(1000001, a.add(b).intValue());
    }

    // negate(): flips sign, leaves magnitude unchanged
    @Test
    public void testNegate_flipsSign() throws Throwable {
        Dfp d = new Dfp(field, 5L);
        assertEquals(-5, d.negate().intValue());
    }

    // subtract(): delegates to add(negate(x)) for basic finite case
    @Test
    public void testSubtract_basicCase() throws Throwable {
        Dfp a = new Dfp(field, 5L);
        Dfp b = new Dfp(field, 3L);
        assertEquals(2, a.subtract(b).intValue());
    }

    // multiply(Dfp): normal finite product and infinity*0 invalid branch -> NaN
    @Test
    public void testMultiply_normalAndInfinityTimesZero() throws Throwable {
        Dfp a = new Dfp(field, 6L);
        Dfp b = new Dfp(field, 7L);
        assertEquals(42, a.multiply(b).intValue());
        Dfp inf = new Dfp(field, (byte) 1, Dfp.INFINITE);
        Dfp zero = new Dfp(field, 0L);
        assertTrue(inf.multiply(zero).isNaN());
    }

    // multiply(int): normal case plus out-of-range (negative / >=RADIX) invalid branch
    @Test
    public void testMultiplyInt_normalAndInvalidRange() throws Throwable {
        Dfp a = new Dfp(field, 6L);
        assertEquals(42, a.multiply(7).intValue());
        assertTrue(a.multiply(-1).isNaN());
        assertTrue(a.multiply(Dfp.RADIX).isNaN());
    }

    // divide(Dfp): normal quotient, divide-by-zero -> infinite, zero/zero -> NaN
    @Test
    public void testDivide_dfp_normalAndErrorCases() throws Throwable {
        Dfp a = new Dfp(field, 10L);
        Dfp b = new Dfp(field, 4L);
        assertEquals(2.5, a.divide(b).toDouble(), 1e-9);
        Dfp zero = new Dfp(field, 0L);
        assertTrue(a.divide(zero).isInfinite());
        assertTrue(zero.divide(zero).isNaN());
    }

    // divide(int): normal quotient, divide-by-zero -> infinite, out-of-range -> NaN
    @Test
    public void testDivideInt_normalAndErrorCases() throws Throwable {
        Dfp a = new Dfp(field, 10L);
        assertEquals(2.5, a.divide(4).toDouble(), 1e-9);
        assertTrue(a.divide(0).isInfinite());
        assertTrue(a.divide(-1).isNaN());
    }

    // sqrt(): perfect square, zero short-circuit, negative -> invalid NaN branch
    @Test
    public void testSqrt_perfectSquareZeroAndNegative() throws Throwable {
        Dfp sixteen = new Dfp(field, 16L);
        assertEquals(4, sixteen.sqrt().intValue());
        Dfp zero = new Dfp(field, 0L);
        assertEquals(0, zero.sqrt().intValue());
        Dfp neg = new Dfp(field, -4L);
        assertTrue(neg.sqrt().isNaN());
    }

    // toString(): non-finite representations (Infinity, -Infinity, NaN)
    @Test
    public void testToString_specialValueRepresentations() throws Throwable {
        Dfp inf = new Dfp(field, (byte) 1, Dfp.INFINITE);
        Dfp negInf = new Dfp(field, (byte) -1, Dfp.INFINITE);
        Dfp nan = new Dfp(field, (byte) 1, Dfp.QNAN);
        assertEquals("Infinity", inf.toString());
        assertEquals("-Infinity", negInf.toString());
        assertEquals("NaN", nan.toString());
    }

    // classify(): returns the matching type code for each state
    @Test
    public void testClassify_returnsCorrectCode() throws Throwable {
        Dfp finite = new Dfp(field, 5L);
        Dfp inf = new Dfp(field, (byte) 1, Dfp.INFINITE);
        Dfp nan = new Dfp(field, (byte) 1, Dfp.QNAN);
        assertEquals(Dfp.FINITE, finite.classify());
        assertEquals(Dfp.INFINITE, inf.classify());
        assertEquals(Dfp.QNAN, nan.classify());
    }

    // copysign(): result has magnitude of x and sign of y
    @Test
    public void testCopysign_appliesSignFromSecondArgument() throws Throwable {
        Dfp x = new Dfp(field, 5L);
        Dfp y = new Dfp(field, -1L);
        Dfp r = Dfp.copysign(x, y);
        assertEquals(-5, r.intValue());
    }

    // nextAfter(): equal operands short-circuit to the same value
    @Test
    public void testNextAfter_equalValues_returnsSameValue() throws Throwable {
        Dfp a = new Dfp(field, 3L);
        Dfp r = a.nextAfter(a);
        assertEquals(3, r.intValue());
    }

    // nextAfter(): moves strictly toward a larger target without overshooting it
    @Test
    public void testNextAfter_increasesTowardLargerTarget() throws Throwable {
        Dfp a = new Dfp(field, 1L);
        Dfp target = new Dfp(field, 2L);
        Dfp r = a.nextAfter(target);
        assertTrue(a.lessThan(r));
        assertTrue(r.lessThan(target) || r.equals(target));
    }

    // toDouble(): round-trips a simple finite decimal value
    @Test
    public void testToDouble_roundTripsSimpleDecimal() throws Throwable {
        Dfp d = new Dfp(field, 3.14159);
        assertEquals(3.14159, d.toDouble(), 1e-9);
    }

    // toSplitDouble(): the two parts must sum back to toDouble()'s value
    @Test
    public void testToSplitDouble_sumMatchesToDouble() throws Throwable {
        Dfp d = new Dfp(field, 123.456);
        double[] parts = d.toSplitDouble();
        assertEquals(d.toDouble(), parts[0] + parts[1], 1e-9);
    }
}
