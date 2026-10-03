package org.apache.commons.math.complex;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.math.exception.NullArgumentException;
import org.apache.commons.math.exception.NotPositiveException;

public class ComplexTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        Complex c1 = new Complex(3.0);
        assertEquals(3.0, c1.getReal(), 1e-12);
        assertEquals(0.0, c1.getImaginary(), 1e-12);
        assertFalse(c1.isNaN());
        assertFalse(c1.isInfinite());

        Complex c2 = new Complex(3.0, 4.0);
        assertEquals(3.0, c2.getReal(), 1e-12);
        assertEquals(4.0, c2.getImaginary(), 1e-12);
        assertFalse(c2.isNaN());
        assertFalse(c2.isInfinite());

        Complex cNaN = new Complex(Double.NaN, 1.0);
        assertTrue(cNaN.isNaN());

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertTrue(cInf.isInfinite());
    }

    @Test
    public void testAbs() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-12);

        Complex cNaN = Complex.NaN;
        assertTrue(Double.isNaN(cNaN.abs()));

        Complex cInf = Complex.INF;
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), cInf.abs(), 1e-12);

        Complex cZeroImag = new Complex(3.0, 0.0);
        assertEquals(3.0, cZeroImag.abs(), 1e-12);

        Complex cZeroReal = new Complex(0.0, 4.0);
        assertEquals(4.0, cZeroReal.abs(), 1e-12);

        Complex cRealGreater = new Complex(4.0, 3.0);
        assertEquals(5.0, cRealGreater.abs(), 1e-12);
    }

    @Test
    public void testAdd() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex sum = c1.add(c2);
        assertEquals(4.0, sum.getReal(), 1e-12);
        assertEquals(6.0, sum.getImaginary(), 1e-12);

        Complex sumDouble = c1.add(2.0);
        assertEquals(3.0, sumDouble.getReal(), 1e-12);
        assertEquals(2.0, sumDouble.getImaginary(), 1e-12);

        // NaN cases
        assertEquals(Complex.NaN, c1.add(Complex.NaN));
        assertEquals(Complex.NaN, Complex.NaN.add(c1));
        assertEquals(Complex.NaN, c1.add(Double.NaN));

        boolean exceptionThrown = false;
        try {
            c1.add((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testConjugate() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Complex conj = c.conjugate();
        assertEquals(1.0, conj.getReal(), 1e-12);
        assertEquals(-2.0, conj.getImaginary(), 1e-12);

        assertEquals(Complex.NaN, Complex.NaN.conjugate());
    }

    @Test
    public void testDivide() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex div = c1.divide(c2);
        // (1*3 + 2*4)/(3^2+4^2) + ((2*3 - 1*4)/(3^2+4^2))i = 11/25 + 2/25 i = 0.44 + 0.08i
        assertEquals(0.44, div.getReal(), 1e-12);
        assertEquals(0.08, div.getImaginary(), 1e-12);

        Complex divDouble = c1.divide(2.0);
        assertEquals(0.5, divDouble.getReal(), 1e-12);
        assertEquals(1.0, divDouble.getImaginary(), 1e-12);

        // Special cases
        assertEquals(Complex.NaN, c1.divide(Complex.NaN));
        assertEquals(Complex.NaN, Complex.NaN.divide(c1));
        assertEquals(Complex.NaN, Complex.ZERO.divide(Complex.ZERO));
        assertEquals(Complex.INF, c1.divide(Complex.ZERO));
        assertEquals(Complex.ZERO, c1.divide(Complex.INF));
        assertEquals(Complex.NaN, c1.divide(Double.NaN));
        assertEquals(Complex.NaN, Complex.ZERO.divide(0.0));
        assertEquals(Complex.INF, c1.divide(0.0));
        assertEquals(Complex.ZERO, c1.divide(Double.POSITIVE_INFINITY));

        // cabs(c) < cabs(d) branch
        Complex c3 = new Complex(1.0, 10.0);
        Complex c4 = new Complex(2.0, 20.0);
        Complex div2 = c3.divide(c4);
        assertFalse(div2.isNaN());

        boolean exceptionThrown = false;
        try {
            c1.divide((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex c3 = new Complex(2.0, 1.0);

        assertTrue(c1.equals(c1));
        assertTrue(c1.equals(c2));
        assertFalse(c1.equals(c3));
        assertFalse(c1.equals(null));
        assertFalse(c1.equals("Not a Complex"));

        assertTrue(Complex.NaN.equals(new Complex(Double.NaN, 5.0)));
        assertFalse(c1.equals(Complex.NaN));

        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals(7, Complex.NaN.hashCode());
    }

    @Test
    public void testMultiply() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex mult = c1.multiply(c2);
        // (1*3 - 2*4) + (1*4 + 2*3)i = -5 + 10i
        assertEquals(-5.0, mult.getReal(), 1e-12);
        assertEquals(10.0, mult.getImaginary(), 1e-12);

        Complex multDouble = c1.multiply(2.0);
        assertEquals(2.0, multDouble.getReal(), 1e-12);
        assertEquals(4.0, multDouble.getImaginary(), 1e-12);

        assertEquals(Complex.NaN, c1.multiply(Complex.NaN));
        assertEquals(Complex.NaN, Complex.NaN.multiply(c1));
        assertEquals(Complex.INF, c1.multiply(Complex.INF));
        assertEquals(Complex.NaN, c1.multiply(Double.NaN));
        assertEquals(Complex.INF, c1.multiply(Double.POSITIVE_INFINITY));

        boolean exceptionThrown = false;
        try {
            c1.multiply((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testNegate() throws Throwable {
        Complex c = new Complex(1.0, -2.0);
        Complex neg = c.negate();
        assertEquals(-1.0, neg.getReal(), 1e-12);
        assertEquals(2.0, neg.getImaginary(), 1e-12);

        assertEquals(Complex.NaN, Complex.NaN.negate());
    }

    @Test
    public void testSubtract() throws Throwable {
        Complex c1 = new Complex(3.0, 4.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex sub = c1.subtract(c2);
        assertEquals(2.0, sub.getReal(), 1e-12);
        assertEquals(2.0, sub.getImaginary(), 1e-12);

        Complex subDouble = c1.subtract(1.0);
        assertEquals(2.0, subDouble.getReal(), 1e-12);
        assertEquals(4.0, subDouble.getImaginary(), 1e-12);

        assertEquals(Complex.NaN, c1.subtract(Complex.NaN));
        assertEquals(Complex.NaN, Complex.NaN.subtract(c1));
        assertEquals(Complex.NaN, c1.subtract(Double.NaN));

        boolean exceptionThrown = false;
        try {
            c1.subtract((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testTrigFunctions() throws Throwable {
        Complex c = new Complex(1.0, 1.0);

        assertFalse(c.sin().isNaN());
        assertFalse(c.cos().isNaN());
        assertFalse(c.tan().isNaN());
        assertFalse(c.sinh().isNaN());
        assertFalse(c.cosh().isNaN());
        assertFalse(c.tanh().isNaN());
        assertFalse(c.asin().isNaN());
        assertFalse(c.acos().isNaN());
        assertFalse(c.atan().isNaN());

        assertEquals(Complex.NaN, Complex.NaN.sin());
        assertEquals(Complex.NaN, Complex.NaN.cos());
        assertEquals(Complex.NaN, Complex.NaN.tan());
        assertEquals(Complex.NaN, Complex.NaN.sinh());
        assertEquals(Complex.NaN, Complex.NaN.cosh());
        assertEquals(Complex.NaN, Complex.NaN.tanh());
        assertEquals(Complex.NaN, Complex.NaN.asin());
        assertEquals(Complex.NaN, Complex.NaN.acos());
        assertEquals(Complex.NaN, Complex.NaN.atan());
    }

    @Test
    public void testExpLogPow() throws Throwable {
        Complex c = new Complex(1.0, 1.0);

        assertFalse(c.exp().isNaN());
        assertFalse(c.log().isNaN());
        assertFalse(c.pow(c).isNaN());
        assertFalse(c.pow(2.0).isNaN());

        assertEquals(Complex.NaN, Complex.NaN.exp());
        assertEquals(Complex.NaN, Complex.NaN.log());
        assertEquals(Complex.NaN, Complex.NaN.pow(c));
    }

    @Test
    public void testSqrt() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertFalse(c.sqrt().isNaN());
        assertFalse(c.sqrt1z().isNaN());

        assertEquals(Complex.ZERO, Complex.ZERO.sqrt());
        assertEquals(Complex.NaN, Complex.NaN.sqrt());

        Complex negativeReal = new Complex(-3.0, 4.0);
        assertFalse(negativeReal.sqrt().isNaN());
    }

    @Test
    public void testGetArgument() throws Throwable {
        Complex c = new Complex(0.0, 1.0);
        assertEquals(Math.PI / 2.0, c.getArgument(), 1e-12);
    }

    @Test
    public void testNthRoot() throws Throwable {
        Complex c = new Complex(1.0, 0.0);
        List<Complex> roots = c.nthRoot(2);
        assertEquals(2, roots.size());

        List<Complex> nanRoots = Complex.NaN.nthRoot(2);
        assertEquals(1, nanRoots.size());
        assertEquals(Complex.NaN, nanRoots.get(0));

        List<Complex> infRoots = Complex.INF.nthRoot(2);
        assertEquals(1, infRoots.size());
        assertEquals(Complex.INF, infRoots.get(0));

        boolean exceptionThrown = false;
        try {
            c.nthRoot(0);
        } catch (NotPositiveException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testFactoryAndUtilityMethods() throws Throwable {
        assertEquals(Complex.NaN, Complex.valueOf(Double.NaN, 1.0));
        assertEquals(Complex.NaN, Complex.valueOf(1.0, Double.NaN));
        assertEquals(Complex.NaN, Complex.valueOf(Double.NaN));

        assertFalse(Complex.valueOf(1.0, 2.0).isNaN());
        assertFalse(Complex.valueOf(1.0).isNaN());

        assertNotNull(Complex.ONE.getField());
        assertEquals("(1.0, 2.0)", new Complex(1.0, 2.0).toString());
        assertNotNull(new Complex(1.0, 2.0).readResolve());
    }
}