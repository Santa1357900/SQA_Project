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
        assertFalse(cNaN.isInfinite());

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertFalse(cInf.isNaN());
        assertTrue(cInf.isInfinite());
    }

    @Test
    public void testValueOf() throws Throwable {
        Complex c1 = Complex.valueOf(3.0, 4.0);
        assertEquals(3.0, c1.getReal(), 1e-12);
        assertEquals(4.0, c1.getImaginary(), 1e-12);

        Complex cNaN = Complex.valueOf(Double.NaN, 4.0);
        assertSame(Complex.NaN, cNaN);

        Complex cRealNaN = Complex.valueOf(Double.NaN);
        assertSame(Complex.NaN, cRealNaN);

        Complex cReal = Complex.valueOf(5.0);
        assertEquals(5.0, cReal.getReal(), 1e-12);
        assertEquals(0.0, cReal.getImaginary(), 1e-12);
    }

    @Test
    public void testAbs() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-12);

        Complex cZeroImag = new Complex(3.0, 0.0);
        assertEquals(3.0, cZeroImag.abs(), 1e-12);

        Complex cZeroReal = new Complex(0.0, 4.0);
        assertEquals(4.0, cZeroReal.abs(), 1e-12);

        Complex cNaN = new Complex(Double.NaN, 4.0);
        assertTrue(Double.isNaN(cNaN.abs()));

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 4.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), cInf.abs(), 1e-12);
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

        assertSame(Complex.NaN, c1.add(Complex.NaN));
        assertSame(Complex.NaN, Complex.NaN.add(c1));
        assertSame(Complex.NaN, c1.add(Double.NaN));

        boolean thrown = false;
        try {
            c1.add(null);
        } catch (NullArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testSubtract() throws Throwable {
        Complex c1 = new Complex(5.0, 6.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex diff = c1.subtract(c2);
        assertEquals(4.0, diff.getReal(), 1e-12);
        assertEquals(4.0, diff.getImaginary(), 1e-12);

        Complex diffDouble = c1.subtract(2.0);
        assertEquals(3.0, diffDouble.getReal(), 1e-12);
        assertEquals(6.0, diffDouble.getImaginary(), 1e-12);

        assertSame(Complex.NaN, c1.subtract(Complex.NaN));
        assertSame(Complex.NaN, Complex.NaN.subtract(c1));
        assertSame(Complex.NaN, c1.subtract(Double.NaN));

        boolean thrown = false;
        try {
            c1.subtract(null);
        } catch (NullArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testMultiply() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex prod = c1.multiply(c2);
        assertEquals(-5.0, prod.getReal(), 1e-12);
        assertEquals(10.0, prod.getImaginary(), 1e-12);

        Complex prodInt = c1.multiply(2);
        assertEquals(2.0, prodInt.getReal(), 1e-12);
        assertEquals(4.0, prodInt.getImaginary(), 1e-12);

        Complex prodDouble = c1.multiply(2.0);
        assertEquals(2.0, prodDouble.getReal(), 1e-12);
        assertEquals(4.0, prodDouble.getImaginary(), 1e-12);

        assertSame(Complex.NaN, c1.multiply(Complex.NaN));
        assertSame(Complex.NaN, Complex.NaN.multiply(c1));
        assertSame(Complex.NaN, c1.multiply(Double.NaN));

        Complex inf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertSame(Complex.INF, c1.multiply(inf));
        assertSame(Complex.INF, inf.multiply(c1));

        assertSame(Complex.INF, c1.multiply(200)); // wait, multiply(int) checks isInfinite() on real/imag
        Complex infReal = new Complex(Double.POSITIVE_INFINITY, 0.0);
        assertSame(Complex.INF, infReal.multiply(2));
        assertSame(Complex.NaN, Complex.NaN.multiply(2));

        boolean thrown = false;
        try {
            c1.multiply(null);
        } catch (NullArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testDivide() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex div = c1.divide(c2);
        assertEquals(11.0 / 25.0, div.getReal(), 1e-12);
        assertEquals(2.0 / 25.0, div.getImaginary(), 1e-12);

        Complex divDouble = c1.divide(2.0);
        assertEquals(0.5, divDouble.getReal(), 1e-12);
        assertEquals(1.0, divDouble.getImaginary(), 1e-12);

        assertSame(Complex.NaN, c1.divide(Complex.NaN));
        assertSame(Complex.NaN, Complex.NaN.divide(c1));
        assertSame(Complex.NaN, c1.divide(Complex.ZERO));
        assertSame(Complex.NaN, c1.divide(0.0));
        assertSame(Complex.NaN, c1.divide(Double.NaN));

        Complex inf = new Complex(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertSame(Complex.NaN, inf.divide(inf));

        Complex finite = new Complex(1.0, 1.0);
        assertSame(Complex.ZERO, finite.divide(inf));

        Complex cAbsCMinorD = new Complex(1.0, 10.0);
        Complex cAbsCMinorDDivisor = new Complex(2.0, 1.0);
        Complex resAlt = cAbsCMinorD.divide(cAbsCMinorDDivisor);
        assertNotNull(resAlt);

        boolean thrown = false;
        try {
            c1.divide(null);
        } catch (NullArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testReciprocal() throws Throwable {
        Complex c = new Complex(2.0, 0.0);
        Complex rec = c.reciprocal();
        assertEquals(0.5, rec.getReal(), 1e-12);
        assertEquals(0.0, rec.getImaginary(), 1e-12);

        assertSame(Complex.NaN, Complex.NaN.reciprocal());
        assertSame(Complex.NaN, Complex.ZERO.reciprocal());
        assertSame(Complex.ZERO, Complex.INF.reciprocal());

        Complex cAlt = new Complex(1.0, 2.0);
        Complex recAlt = cAlt.reciprocal();
        assertNotNull(recAlt);
    }

    @Test
    public void testConjugate() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Complex conj = c.conjugate();
        assertEquals(1.0, conj.getReal(), 1e-12);
        assertEquals(-2.0, conj.getImaginary(), 1e-12);

        assertSame(Complex.NaN, Complex.NaN.conjugate());
    }

    @Test
    public void testNegate() throws Throwable {
        Complex c = new Complex(1.0, -2.0);
        Complex neg = c.negate();
        assertEquals(-1.0, neg.getReal(), 1e-12);
        assertEquals(2.0, neg.getImaginary(), 1e-12);

        assertSame(Complex.NaN, Complex.NaN.negate());
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
        assertFalse(c1.equals("SomeString"));

        assertTrue(Complex.NaN.equals(Complex.NaN));
        Complex cNaN1 = new Complex(Double.NaN, 1.0);
        Complex cNaN2 = new Complex(1.0, Double.NaN);
        assertTrue(cNaN1.equals(cNaN2));
        assertFalse(c1.equals(cNaN1));

        assertEquals(7, Complex.NaN.hashCode());
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    public void testTrigFunctions() throws Throwable {
        Complex c = new Complex(1.0, 1.0);

        assertNotNull(c.sin());
        assertNotNull(c.cos());
        assertNotNull(c.tan());
        assertNotNull(c.sinh());
        assertNotNull(c.cosh());
        assertNotNull(c.tanh());
        assertNotNull(c.asin());
        assertNotNull(c.acos());
        assertNotNull(c.atan());

        assertSame(Complex.NaN, Complex.NaN.sin());
        assertSame(Complex.NaN, Complex.NaN.cos());
        assertSame(Complex.NaN, Complex.NaN.tan());
        assertSame(Complex.NaN, Complex.NaN.sinh());
        assertSame(Complex.NaN, Complex.NaN.cosh());
        assertSame(Complex.NaN, Complex.NaN.tanh());
        assertSame(Complex.NaN, Complex.NaN.asin());
        assertSame(Complex.NaN, Complex.NaN.acos());
        assertSame(Complex.NaN, Complex.NaN.atan());
    }

    @Test
    public void testExpLogPow() throws Throwable {
        Complex c = new Complex(1.0, 1.0);

        assertNotNull(c.exp());
        assertNotNull(c.log());
        assertNotNull(c.pow(2.0));
        assertNotNull(c.pow(c));

        assertSame(Complex.NaN, Complex.NaN.exp());
        assertSame(Complex.NaN, Complex.NaN.log());

        boolean thrown = false;
        try {
            c.pow((Complex) null);
        } catch (NullArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testSqrtAndOthers() throws Throwable {
        Complex c = new Complex(4.0, 3.0);
        assertNotNull(c.sqrt());
        assertNotNull(c.sqrt1z());

        Complex cNegativeReal = new Complex(-4.0, 3.0);
        assertNotNull(cNegativeReal.sqrt());

        Complex cZero = new Complex(0.0, 0.0);
        assertEquals(0.0, cZero.sqrt().getReal(), 1e-12);

        assertSame(Complex.NaN, Complex.NaN.sqrt());

        assertEquals(Math.atan2(2.0, 1.0), new Complex(1.0, 2.0).getArgument(), 1e-12);
        assertNotNull(new Complex(1.0, 2.0).getField());
        assertEquals("(1.0, 2.0)", new Complex(1.0, 2.0).toString());
    }

    @Test
    public void testNthRoot() throws Throwable {
        Complex c = new Complex(1.0, 0.0);
        List<Complex> roots = c.nthRoot(2);
        assertEquals(2, roots.size());

        List<Complex> rootsNaN = Complex.NaN.nthRoot(2);
        assertEquals(1, rootsNaN.size());
        assertSame(Complex.NaN, rootsNaN.get(0));

        List<Complex> rootsInf = Complex.INF.nthRoot(2);
        assertEquals(1, rootsInf.size());
        assertSame(Complex.INF, rootsInf.get(0));

        boolean thrown = false;
        try {
            c.nthRoot(0);
        } catch (NotPositiveException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testSerializationAndReadResolve() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Object resolved = c.readResolve();
        assertTrue(resolved instanceof Complex);
        assertEquals(c, resolved);
    }
}