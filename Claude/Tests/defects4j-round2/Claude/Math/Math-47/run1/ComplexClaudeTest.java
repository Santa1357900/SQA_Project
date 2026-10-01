package org.apache.commons.math.complex;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.NullArgumentException;
import org.apache.commons.math.exception.NotPositiveException;

public class ComplexClaudeTest {

    // Covers single-arg constructor delegating to (real, 0.0)
    @Test
    public void testConstructor_RealOnly_ImaginaryIsZero() throws Throwable {
        Complex c = new Complex(5.5);
        assertEquals(5.5, c.getReal(), 1e-9);
        assertEquals(0.0, c.getImaginary(), 1e-9);
    }

    // Covers two-arg constructor and basic accessors/flags
    @Test
    public void testConstructor_RealAndImaginary_FieldsSet() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(3.0, c.getReal(), 1e-9);
        assertEquals(4.0, c.getImaginary(), 1e-9);
        assertFalse(c.isNaN());
        assertFalse(c.isInfinite());
    }

    // Covers abs() isNaN branch
    @Test
    public void testAbs_NaN_ReturnsNaN() throws Throwable {
        assertTrue(Double.isNaN(Complex.NaN.abs()));
    }

    // Covers abs() isInfinite branch
    @Test
    public void testAbs_Infinite_ReturnsPositiveInfinity() throws Throwable {
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), Complex.INF.abs(), 1e-9);
    }

    // Covers abs() branch where |real| < |imaginary|
    @Test
    public void testAbs_RealSmallerThanImaginary_ComputesCorrectly() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertEquals(Math.sqrt(5.0), c.abs(), 1e-9);
    }

    // Covers abs() else branch with real == 0.0 (zero magnitude)
    @Test
    public void testAbs_ZeroZero_ReturnsZero() throws Throwable {
        assertEquals(0.0, Complex.ZERO.abs(), 1e-9);
    }

    // Covers abs() else branch with |real| >= |imaginary|, real != 0
    @Test
    public void testAbs_RealLargerThanImaginary_ComputesCorrectly() throws Throwable {
        Complex c = new Complex(3.0, 1.0);
        assertEquals(Math.sqrt(10.0), c.abs(), 1e-9);
    }

    // Covers add(Complex) normal addition
    @Test
    public void testAdd_Complex_Normal() throws Throwable {
        Complex result = new Complex(1.0, 2.0).add(new Complex(3.0, 4.0));
        assertEquals(4.0, result.getReal(), 1e-9);
        assertEquals(6.0, result.getImaginary(), 1e-9);
    }

    // Covers add(Complex) NaN propagation branch
    @Test
    public void testAdd_Complex_NaNPropagates() throws Throwable {
        Complex result = Complex.ONE.add(Complex.NaN);
        assertTrue(result.isNaN());
    }

    // Covers add(Complex) null argument throwing NullArgumentException
    @Test
    public void testAdd_Complex_NullThrowsNullArgumentException() throws Throwable {
        try {
            Complex.ONE.add((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Covers add(double) NaN propagation branch
    @Test
    public void testAdd_double_NaNPropagates() throws Throwable {
        Complex result = Complex.ONE.add(Double.NaN);
        assertTrue(result.isNaN());
    }

    // Covers conjugate() normal and isNaN branches
    @Test
    public void testConjugate_NormalAndNaN() throws Throwable {
        Complex result = new Complex(1.0, 2.0).conjugate();
        assertEquals(1.0, result.getReal(), 1e-9);
        assertEquals(-2.0, result.getImaginary(), 1e-9);
        assertTrue(Complex.NaN.conjugate().isNaN());
    }

    // Covers divide(Complex) branch |c| < |d|
    @Test
    public void testDivide_Complex_FirstBranch_AbsCLessThanAbsD() throws Throwable {
        Complex result = new Complex(1.0, 0.0).divide(new Complex(0.0, 2.0));
        assertEquals(0.0, result.getReal(), 1e-9);
        assertEquals(-0.5, result.getImaginary(), 1e-9);
    }

    // Covers divide(Complex) branch |c| >= |d|
    @Test
    public void testDivide_Complex_ElseBranch_AbsCGreaterEqualAbsD() throws Throwable {
        Complex result = new Complex(1.0, 0.0).divide(new Complex(2.0, 0.0));
        assertEquals(0.5, result.getReal(), 1e-9);
        assertEquals(0.0, result.getImaginary(), 1e-9);
    }

    // Covers divide(Complex) when both this and divisor are ZERO -> NaN per contract
    @Test
    public void testDivide_Complex_BothZero_ReturnsNaN() throws Throwable {
        Complex result = Complex.ZERO.divide(Complex.ZERO);
        assertTrue(result.isNaN());
    }

    // BUG ORACLE: contract says divisor==ZERO with non-zero this must return INF, not NaN
    @Test
    public void testDivide_Complex_DivisorZeroThisNonZero_ReturnsInf() throws Throwable {
        Complex result = Complex.ONE.divide(Complex.ZERO);
        assertEquals(Complex.INF, result);
    }

    // Covers divide(Complex) both infinite -> NaN
    @Test
    public void testDivide_Complex_BothInfinite_ReturnsNaN() throws Throwable {
        Complex result = Complex.INF.divide(Complex.INF);
        assertTrue(result.isNaN());
    }

    // Covers divide(Complex) finite this, infinite divisor -> ZERO
    @Test
    public void testDivide_Complex_ThisFiniteDivisorInfinite_ReturnsZero() throws Throwable {
        Complex result = Complex.ONE.divide(Complex.INF);
        assertEquals(Complex.ZERO, result);
    }

    // Covers divide(Complex) null argument throwing NullArgumentException
    @Test
    public void testDivide_Complex_NullThrowsNullArgumentException() throws Throwable {
        try {
            Complex.ONE.divide((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Covers divide(double) normal division
    @Test
    public void testDivide_double_Normal() throws Throwable {
        Complex result = new Complex(4.0, 2.0).divide(2.0);
        assertEquals(2.0, result.getReal(), 1e-9);
        assertEquals(1.0, result.getImaginary(), 1e-9);
    }

    // Covers divide(double) zero divisor branch -> NaN
    @Test
    public void testDivide_double_ZeroDivisor_ReturnsNaN() throws Throwable {
        Complex result = Complex.ONE.divide(0.0);
        assertTrue(result.isNaN());
    }

    // Covers divide(double) infinite divisor with finite this -> ZERO
    @Test
    public void testDivide_double_InfiniteDivisorFiniteThis_ReturnsZero() throws Throwable {
        Complex result = new Complex(1.0, 1.0).divide(Double.POSITIVE_INFINITY);
        assertEquals(Complex.ZERO, result);
    }

    // Covers equals() identity shortcut branch
    @Test
    public void testEquals_SameObject_True() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertTrue(c.equals(c));
    }

    // Covers equals() when other is not a Complex instance
    @Test
    public void testEquals_NotComplexInstance_False() throws Throwable {
        assertFalse(Complex.ONE.equals("not a complex"));
    }

    // Covers equals() both NaN -> considered equal
    @Test
    public void testEquals_BothNaN_True() throws Throwable {
        assertTrue(Complex.NaN.equals(new Complex(Double.NaN, 5.0)));
    }

    // Covers equals() one NaN one not -> false
    @Test
    public void testEquals_OneNaNOneNot_False() throws Throwable {
        assertFalse(new Complex(1.0, 2.0).equals(Complex.NaN));
    }

    // Covers equals() equal finite values -> true; different values -> false
    @Test
    public void testEquals_EqualValues_True() throws Throwable {
        assertTrue(new Complex(1.0, 2.0).equals(new Complex(1.0, 2.0)));
        assertFalse(new Complex(1.0, 2.0).equals(new Complex(1.0, 3.0)));
    }

    // Covers hashCode() isNaN branch returning constant 7
    @Test
    public void testHashCode_NaN_Returns7() throws Throwable {
        assertEquals(7, Complex.NaN.hashCode());
        assertEquals(7, new Complex(Double.NaN, 1.0).hashCode());
    }

    // Covers isNaN() true and false branches
    @Test
    public void testIsNaN_TrueAndFalse() throws Throwable {
        assertTrue(Complex.NaN.isNaN());
        assertFalse(Complex.ONE.isNaN());
    }

    // Covers isInfinite(): NaN part suppresses the infinite flag
    @Test
    public void testIsInfinite_NaNSuppressesInfiniteFlag() throws Throwable {
        assertTrue(Complex.INF.isInfinite());
        Complex c = new Complex(Double.NaN, Double.POSITIVE_INFINITY);
        assertTrue(c.isNaN());
        assertFalse(c.isInfinite());
    }

    // Covers multiply(Complex) normal multiplication formula
    @Test
    public void testMultiply_Complex_Normal() throws Throwable {
        Complex result = new Complex(1.0, 2.0).multiply(new Complex(3.0, 4.0));
        assertEquals(-5.0, result.getReal(), 1e-9);
        assertEquals(10.0, result.getImaginary(), 1e-9);
    }

    // Covers multiply(Complex) infinite part branch -> INF
    @Test
    public void testMultiply_Complex_Infinite_ReturnsInf() throws Throwable {
        Complex result = Complex.INF.multiply(Complex.ONE);
        assertEquals(Complex.INF, result);
    }

    // Covers multiply(Complex) null argument throwing NullArgumentException
    @Test
    public void testMultiply_Complex_NullThrows() throws Throwable {
        try {
            Complex.ONE.multiply((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Covers negate() normal and isNaN branches
    @Test
    public void testNegate_NormalAndNaN() throws Throwable {
        Complex result = new Complex(1.0, -2.0).negate();
        assertEquals(-1.0, result.getReal(), 1e-9);
        assertEquals(2.0, result.getImaginary(), 1e-9);
        assertTrue(Complex.NaN.negate().isNaN());
    }

    // Covers subtract(Complex) normal subtraction and null throwing NullArgumentException
    @Test
    public void testSubtract_Complex_NormalAndNullThrows() throws Throwable {
        Complex result = new Complex(5.0, 3.0).subtract(new Complex(2.0, 1.0));
        assertEquals(3.0, result.getReal(), 1e-9);
        assertEquals(2.0, result.getImaginary(), 1e-9);
        try {
            Complex.ONE.subtract((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Covers the shared isNaN guard branch across trig/hyperbolic/exp/log/sqrt
    @Test
    public void testTranscendentalMethods_NaNInput_ReturnsNaN() throws Throwable {
        Complex n = Complex.NaN;
        assertTrue(n.acos().isNaN());
        assertTrue(n.asin().isNaN());
        assertTrue(n.atan().isNaN());
        assertTrue(n.cos().isNaN());
        assertTrue(n.cosh().isNaN());
        assertTrue(n.exp().isNaN());
        assertTrue(n.log().isNaN());
        assertTrue(n.sin().isNaN());
        assertTrue(n.sinh().isNaN());
        assertTrue(n.sqrt().isNaN());
        assertTrue(n.tan().isNaN());
        assertTrue(n.tanh().isNaN());
    }

    // Covers acos(0)=pi/2 and atan(0)=0 known values
    @Test
    public void testAcosAndAtan_Zero_ReturnsExpectedValues() throws Throwable {
        Complex acos0 = Complex.ZERO.acos();
        assertEquals(Math.PI / 2.0, acos0.getReal(), 1e-9);
        assertEquals(0.0, acos0.getImaginary(), 1e-9);
        Complex atan0 = Complex.ZERO.atan();
        assertEquals(0.0, atan0.getReal(), 1e-9);
        assertEquals(0.0, atan0.getImaginary(), 1e-9);
    }

    // Covers exp(0)=1 and log(0+0i)=-Infinity + 0i per javadoc example
    @Test
    public void testExpAndLog_Zero_ReturnsExpectedValues() throws Throwable {
        Complex exp0 = Complex.ZERO.exp();
        assertEquals(1.0, exp0.getReal(), 1e-9);
        assertEquals(0.0, exp0.getImaginary(), 1e-9);
        Complex log0 = Complex.ZERO.log();
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), log0.getReal(), 1e-9);
        assertEquals(0.0, log0.getImaginary(), 1e-9);
    }

    // Covers sqrt() normal branch and sqrt1z() of ZERO
    @Test
    public void testSqrtAndSqrt1z_ReturnsCorrectValues() throws Throwable {
        Complex root = new Complex(3.0, 4.0).sqrt();
        assertEquals(2.0, root.getReal(), 1e-9);
        assertEquals(1.0, root.getImaginary(), 1e-9);
        assertEquals(Complex.ONE, Complex.ZERO.sqrt1z());
    }

    // Covers getArgument() for a point and for the origin
    @Test
    public void testGetArgument_NormalAndZero() throws Throwable {
        assertEquals(Math.PI / 4.0, new Complex(1.0, 1.0).getArgument(), 1e-9);
        assertEquals(0.0, Complex.ZERO.getArgument(), 1e-9);
    }

    // Covers nthRoot(n) n<=0 throwing NotPositiveException
    @Test
    public void testNthRoot_NonPositiveN_ThrowsNotPositiveException() throws Throwable {
        try {
            Complex.ONE.nthRoot(0);
            fail("expected NotPositiveException");
        } catch (NotPositiveException expected) {
        }
    }

    // Covers nthRoot() isNaN and isInfinite single-element list branches
    @Test
    public void testNthRoot_NaNAndInfinite_ReturnsSingleElementList() throws Throwable {
        List<Complex> nanRoots = Complex.NaN.nthRoot(3);
        assertEquals(1, nanRoots.size());
        assertTrue(nanRoots.get(0).isNaN());
        List<Complex> infRoots = Complex.INF.nthRoot(2);
        assertEquals(1, infRoots.size());
        assertEquals(Complex.INF, infRoots.get(0));
    }

    // Covers pow(double) using log/multiply/exp composition: 2^2 = 4
    @Test
    public void testPow_double_Normal() throws Throwable {
        Complex result = new Complex(2.0, 0.0).pow(2.0);
        assertEquals(4.0, result.getReal(), 1e-9);
        assertEquals(0.0, result.getImaginary(), 1e-9);
    }

    // Covers valueOf(double,double) NaN-detection branch and normal branch
    @Test
    public void testValueOf_NaNAndNormal() throws Throwable {
        assertTrue(Complex.valueOf(Double.NaN, 2.0).isNaN());
        Complex c = Complex.valueOf(3.0, 4.0);
        assertEquals(3.0, c.getReal(), 1e-9);
        assertEquals(4.0, c.getImaginary(), 1e-9);
    }

    // Covers toString() formatting contract
    @Test
    public void testToString_Format() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertEquals("(1.0, 2.0)", c.toString());
    }
}
