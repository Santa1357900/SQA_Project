package org.apache.commons.math.complex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.apache.commons.math.exception.NullArgumentException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComplexClaudeTest {

    // covers constructor with finite values, isNaN/isInfinite false
    @Test
    public void testConstructor_finiteValues_fieldsAndFlags() throws Throwable {
        Complex c = new Complex(2.5, -3.5);
        assertEquals(2.5, c.getReal(), 1e-9);
        assertEquals(-3.5, c.getImaginary(), 1e-9);
        assertFalse(c.isNaN());
        assertFalse(c.isInfinite());
    }

    // covers isNaN true branch, isInfinite true branch, and isNaN overriding isInfinite
    @Test
    public void testConstructor_NaNAndInfiniteCombinations() throws Throwable {
        Complex nanReal = new Complex(Double.NaN, 1.0);
        assertTrue(nanReal.isNaN());
        assertFalse(nanReal.isInfinite());
        Complex infReal = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertTrue(infReal.isInfinite());
        Complex nanWithInf = new Complex(Double.NaN, Double.POSITIVE_INFINITY);
        assertTrue(nanWithInf.isNaN());
        assertFalse(nanWithInf.isInfinite());
    }

    // covers abs() normal computation branch (else branch, real!=0)
    @Test
    public void testAbs_pythagoreanTriple() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-9);
    }

    // covers abs() isNaN branch
    @Test
    public void testAbs_NaN_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(Complex.NaN.abs()));
    }

    // covers abs() isInfinite branch
    @Test
    public void testAbs_infinite_returnsPositiveInfinity() throws Throwable {
        Complex c = new Complex(Double.POSITIVE_INFINITY, 2.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), c.abs(), 0.0);
    }

    // covers add() and subtract() definitional formulas
    @Test
    public void testAddAndSubtract_finiteValues() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(3.0, 4.0);
        Complex sum = a.add(b);
        assertEquals(4.0, sum.getReal(), 1e-9);
        assertEquals(6.0, sum.getImaginary(), 1e-9);
        Complex diff = b.subtract(a);
        assertEquals(2.0, diff.getReal(), 1e-9);
        assertEquals(2.0, diff.getImaginary(), 1e-9);
    }

    // covers add() null-check throwing NullArgumentException
    @Test
    public void testAdd_null_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).add(null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
            // expected
        }
    }

    // covers conjugate() normal branch negating imaginary part
    @Test
    public void testConjugate_finiteValue() throws Throwable {
        Complex c = new Complex(2.0, 5.0);
        Complex conj = c.conjugate();
        assertEquals(2.0, conj.getReal(), 1e-9);
        assertEquals(-5.0, conj.getImaginary(), 1e-9);
    }

    // covers conjugate() isNaN branch
    @Test
    public void testConjugate_NaN_returnsNaN() throws Throwable {
        assertTrue(Complex.NaN.conjugate().isNaN());
    }

    // covers divide() abs(c)<abs(d) branch with exact result
    @Test
    public void testDivide_finiteValues() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(1.0, 3.0);
        Complex q = a.divide(b);
        assertEquals(0.7, q.getReal(), 1e-9);
        assertEquals(-0.1, q.getImaginary(), 1e-9);
    }

    // covers divide() rhs-equals-ZERO branch
    @Test
    public void testDivide_byZeroComplex_returnsNaN() throws Throwable {
        Complex result = new Complex(1.0, 1.0).divide(Complex.ZERO);
        assertTrue(result.isNaN());
    }

    // covers divide() null-check
    @Test
    public void testDivide_null_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).divide(null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
            // expected
        }
    }

    // covers divide() rhs infinite / this finite branch returning ZERO
    @Test
    public void testDivide_rhsInfiniteThisFinite_returnsZero() throws Throwable {
        Complex result = new Complex(1.0, 1.0).divide(Complex.INF);
        assertEquals(0.0, result.getReal(), 1e-9);
        assertEquals(0.0, result.getImaginary(), 1e-9);
    }

    // covers equals() same-value branch and NaN-equals-NaN branch
    @Test
    public void testEquals_sameValuesAndNaNVariants() throws Throwable {
        assertTrue(new Complex(1.0, 2.0).equals(new Complex(1.0, 2.0)));
        assertTrue(new Complex(Double.NaN, 1.0).equals(new Complex(2.0, Double.NaN)));
        assertFalse(new Complex(1.0, 2.0).equals(new Complex(1.0, 3.0)));
    }

    // covers equals() instanceof-false branch for wrong type and null
    @Test
    public void testEquals_differentTypeOrNull_false() throws Throwable {
        Complex c = new Complex(1.0, 1.0);
        assertFalse(c.equals("not a complex"));
        assertFalse(c.equals(null));
    }

    // covers hashCode() normal branch and isNaN branch returning 7
    @Test
    public void testHashCode_equalObjectsAndNaN() throws Throwable {
        Complex a = new Complex(1.5, 2.5);
        Complex b = new Complex(1.5, 2.5);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(7, Complex.NaN.hashCode());
    }

    // covers getReal() and getImaginary() accessors
    @Test
    public void testGetRealGetImaginary() throws Throwable {
        Complex c = new Complex(3.5, -2.5);
        assertEquals(3.5, c.getReal(), 1e-9);
        assertEquals(-2.5, c.getImaginary(), 1e-9);
    }

    // covers isNaN()/isInfinite() accessor truth values
    @Test
    public void testIsNaNIsInfinite_flags() throws Throwable {
        assertTrue(Complex.NaN.isNaN());
        assertFalse(Complex.NaN.isInfinite());
        assertTrue(Complex.INF.isInfinite());
        assertFalse(Complex.INF.isNaN());
    }

    // covers multiply(Complex) definitional formula branch
    @Test
    public void testMultiplyComplex_finiteValues() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(3.0, 4.0);
        Complex p = a.multiply(b);
        assertEquals(-5.0, p.getReal(), 1e-9);
        assertEquals(10.0, p.getImaginary(), 1e-9);
    }

    // covers multiply(Complex) null-check
    @Test
    public void testMultiplyComplex_null_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).multiply((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
            // expected
        }
    }

    // covers multiply(Complex) infinite-operand branch returning INF
    @Test
    public void testMultiplyComplex_infiniteOperand_returnsINF() throws Throwable {
        Complex p = new Complex(1.0, 1.0).multiply(Complex.INF);
        assertTrue(p.isInfinite());
        assertFalse(p.isNaN());
    }

    // covers multiply(double) NaN branch and infinite branch
    @Test
    public void testMultiplyDouble_NaNAndInfiniteScalar() throws Throwable {
        assertTrue(new Complex(1.0, 1.0).multiply(Double.NaN).isNaN());
        Complex p = new Complex(1.0, 1.0).multiply(Double.POSITIVE_INFINITY);
        assertTrue(p.isInfinite());
    }

    // covers negate() normal branch and isNaN branch
    @Test
    public void testNegate_finiteAndNaN() throws Throwable {
        Complex c = new Complex(2.0, -3.0).negate();
        assertEquals(-2.0, c.getReal(), 1e-9);
        assertEquals(3.0, c.getImaginary(), 1e-9);
        assertTrue(Complex.NaN.negate().isNaN());
    }

    // covers subtract() null-check
    @Test
    public void testSubtract_null_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).subtract(null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
            // expected
        }
    }

    // covers acos() and asin() formulas at known boundary values
    @Test
    public void testAcosAndAsin_boundaryValues() throws Throwable {
        Complex acosOne = Complex.ONE.acos();
        assertEquals(0.0, acosOne.getReal(), 1e-6);
        assertEquals(0.0, acosOne.getImaginary(), 1e-6);
        Complex asinZero = Complex.ZERO.asin();
        assertEquals(0.0, asinZero.getReal(), 1e-6);
        assertEquals(0.0, asinZero.getImaginary(), 1e-6);
    }

    // covers atan() formula at zero
    @Test
    public void testAtan_zero_returnsZero() throws Throwable {
        Complex r = Complex.ZERO.atan();
        assertEquals(0.0, r.getReal(), 1e-6);
        assertEquals(0.0, r.getImaginary(), 1e-6);
    }

    // covers cos() and sin() formulas at zero
    @Test
    public void testCosAndSin_zero() throws Throwable {
        Complex cos0 = Complex.ZERO.cos();
        assertEquals(1.0, cos0.getReal(), 1e-9);
        assertEquals(0.0, cos0.getImaginary(), 1e-9);
        Complex sin0 = Complex.ZERO.sin();
        assertEquals(0.0, sin0.getReal(), 1e-9);
        assertEquals(0.0, sin0.getImaginary(), 1e-9);
    }

    // covers cosh() and sinh() formulas at zero
    @Test
    public void testCoshSinh_zero() throws Throwable {
        Complex cosh0 = Complex.ZERO.cosh();
        assertEquals(1.0, cosh0.getReal(), 1e-9);
        assertEquals(0.0, cosh0.getImaginary(), 1e-9);
        Complex sinh0 = Complex.ZERO.sinh();
        assertEquals(0.0, sinh0.getReal(), 1e-9);
        assertEquals(0.0, sinh0.getImaginary(), 1e-9);
    }

    // covers exp() formula at zero
    @Test
    public void testExp_zero_returnsOne() throws Throwable {
        Complex r = Complex.ZERO.exp();
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // covers log() at 1 (zero result) and at 0 (-Infinity result, per javadoc example)
    @Test
    public void testLog_zeroAndOne() throws Throwable {
        Complex logOne = Complex.ONE.log();
        assertEquals(0.0, logOne.getReal(), 1e-9);
        assertEquals(0.0, logOne.getImaginary(), 1e-9);
        Complex logZero = Complex.ZERO.log();
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), logZero.getReal(), 0.0);
    }

    // covers log() with positive infinite real part, documented example log(INF+i)=INF+0i
    @Test
    public void testLog_infiniteReal_argZero() throws Throwable {
        Complex r = new Complex(Double.POSITIVE_INFINITY, 1.0).log();
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r.getReal(), 0.0);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // covers log() with negative infinite real part, documented example log(-INF+i)=INF+PIi
    @Test
    public void testLog_negativeInfiniteReal_argPi() throws Throwable {
        Complex r = new Complex(Double.NEGATIVE_INFINITY, 1.0).log();
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r.getReal(), 0.0);
        assertEquals(Math.PI, r.getImaginary(), 1e-6);
    }

    // covers pow() null-check
    @Test
    public void testPow_null_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).pow(null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
            // expected
        }
    }

    // covers pow() formula: 1^x = 1 for any finite x
    @Test
    public void testPow_oneRaisedToAny_returnsOne() throws Throwable {
        Complex r = Complex.ONE.pow(new Complex(2.0, 0.0));
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // covers sqrt() real>=0 branch with exact known result (2+i)^2=3+4i
    @Test
    public void testSqrt_threePlusFourI() throws Throwable {
        Complex r = new Complex(3.0, 4.0).sqrt();
        assertEquals(2.0, r.getReal(), 1e-9);
        assertEquals(1.0, r.getImaginary(), 1e-9);
    }

    // covers sqrt() real>=0 branch with infinite real, and real<0 branch with infinite real
    @Test
    public void testSqrt_infiniteCases() throws Throwable {
        Complex r1 = new Complex(Double.POSITIVE_INFINITY, 1.0).sqrt();
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r1.getReal(), 0.0);
        assertEquals(0.0, r1.getImaginary(), 1e-9);
        Complex r2 = new Complex(Double.NEGATIVE_INFINITY, 1.0).sqrt();
        assertEquals(0.0, r2.getReal(), 1e-9);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r2.getImaginary(), 0.0);
    }

    // covers sqrt() isNaN branch
    @Test
    public void testSqrt_NaN_returnsNaN() throws Throwable {
        assertTrue(Complex.NaN.sqrt().isNaN());
    }

    // covers tan() formula at zero
    @Test
    public void testTan_zero_returnsZero() throws Throwable {
        Complex r = Complex.ZERO.tan();
        assertEquals(0.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // covers tanh() documented example at imaginary = pi/2 (NaN + INFINITY i)
    @Test
    public void testTanh_piHalfImaginary_returnsNaNInfinity() throws Throwable {
        Complex r = new Complex(0.0, Math.PI / 2.0).tanh();
        assertTrue(Double.isNaN(r.getReal()));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r.getImaginary(), 0.0);
    }

    // covers getArgument() across quadrants via atan2
    @Test
    public void testGetArgument_quadrants() throws Throwable {
        assertEquals(Math.PI / 4.0, new Complex(1.0, 1.0).getArgument(), 1e-6);
        assertEquals(Math.PI, new Complex(-1.0, 0.0).getArgument(), 1e-6);
        assertEquals(-Math.PI / 2.0, new Complex(0.0, -1.0).getArgument(), 1e-6);
    }

    // covers nthRoot() n<=0 branch throwing IllegalArgumentException
    @Test
    public void testNthRoot_nonPositiveN_throwsIllegalArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).nthRoot(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers nthRoot() isNaN branch and isInfinite branch
    @Test
    public void testNthRoot_NaNAndInfinite() throws Throwable {
        List<Complex> naRoots = Complex.NaN.nthRoot(3);
        assertEquals(1, naRoots.size());
        assertTrue(naRoots.get(0).isNaN());
        List<Complex> infRoots = Complex.INF.nthRoot(3);
        assertEquals(1, infRoots.size());
        assertTrue(infRoots.get(0).isInfinite());
    }

    // covers nthRoot() main loop producing n roots in correct order (4th roots of unity)
    @Test
    public void testNthRoot_fourthRootsOfOne() throws Throwable {
        List<Complex> roots = Complex.ONE.nthRoot(4);
        assertEquals(4, roots.size());
        assertEquals(1.0, roots.get(0).getReal(), 1e-6);
        assertEquals(0.0, roots.get(0).getImaginary(), 1e-6);
        assertEquals(0.0, roots.get(1).getReal(), 1e-6);
        assertEquals(1.0, roots.get(1).getImaginary(), 1e-6);
        assertEquals(-1.0, roots.get(2).getReal(), 1e-6);
        assertEquals(0.0, roots.get(3).getReal(), 1e-6);
        assertEquals(-1.0, roots.get(3).getImaginary(), 1e-6);
    }

    // covers toString() formatting and getField() accessor
    @Test
    public void testToStringAndGetField() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertEquals("(1.0, 2.0)", c.toString());
        assertNotNull(c.getField());
    }

    // covers readResolve() via Java serialization round-trip
    @Test
    public void testSerialization_roundTrip_preservesValue() throws Throwable {
        Complex original = new Complex(3.25, -4.75);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(original);
        oos.close();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        Complex restored = (Complex) ois.readObject();
        assertEquals(original, restored);
    }
}
