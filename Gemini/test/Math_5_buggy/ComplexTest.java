package org.apache.commons.math3.complex;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;

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

        Complex cNan = new Complex(Double.NaN, 1.0);
        assertTrue(cNan.isNaN());
        assertFalse(cNan.isInfinite());

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertFalse(cInf.isNaN());
        assertTrue(cInf.isInfinite());
    }

    @Test
    public void testValueOf() throws Throwable {
        Complex c1 = Complex.valueOf(5.0, 6.0);
        assertEquals(5.0, c1.getReal(), 1e-12);
        assertEquals(6.0, c1.getImaginary(), 1e-12);

        Complex cNan1 = Complex.valueOf(Double.NaN, 6.0);
        assertSame(Complex.NaN, cNan1);

        Complex cNan2 = Complex.valueOf(5.0, Double.NaN);
        assertSame(Complex.NaN, cNan2);

        Complex c2 = Complex.valueOf(7.0);
        assertEquals(7.0, c2.getReal(), 1e-12);
        assertEquals(0.0, c2.getImaginary(), 1e-12);

        Complex cNan3 = Complex.valueOf(Double.NaN);
        assertSame(Complex.NaN, cNan3);
    }

    @Test
    public void testAbs() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-12);

        Complex cZero = new Complex(0.0, 0.0);
        assertEquals(0.0, cZero.abs(), 1e-12);

        Complex cNan = Complex.NaN;
        assertTrue(Double.isNaN(cNan.abs()));

        Complex cInf = Complex.INF;
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), cInf.abs(), 1e-12);

        Complex cRealAbs = new Complex(0.0, 5.0);
        assertEquals(5.0, cRealAbs.abs(), 1e-12);

        Complex cImagAbs = new Complex(5.0, 0.0);
        assertEquals(5.0, cImagAbs.abs(), 1e-12);

        Complex cLargeReal = new Complex(1e150, 1e150);
        assertTrue(cLargeReal.abs() > 0);
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

        Complex nanAdd = c1.add(Complex.NaN);
        assertSame(Complex.NaN, nanAdd);

        Complex nanAddDouble = c1.add(Double.NaN);
        assertSame(Complex.NaN, nanAddDouble);

        boolean exceptionThrown = false;
        try {
            c1.add((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
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

        Complex nanSub = c1.subtract(Complex.NaN);
        assertSame(Complex.NaN, nanSub);

        Complex nanSubDouble = c1.subtract(Double.NaN);
        assertSame(Complex.NaN, nanSubDouble);

        boolean exceptionThrown = false;
        try {
            c1.subtract((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
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

        Complex nanProd = c1.multiply(Complex.NaN);
        assertSame(Complex.NaN, nanProd);

        Complex nanProdDouble = c1.multiply(Double.NaN);
        assertSame(Complex.NaN, nanProdDouble);

        Complex infProd = c1.multiply(Complex.INF);
        assertSame(Complex.INF, infProd);

        Complex infProdInt = Complex.INF.multiply(2);
        assertSame(Complex.INF, infProdInt);

        Complex infProdDouble = c1.multiply(Double.POSITIVE_INFINITY);
        assertSame(Complex.INF, infProdDouble);

        boolean exceptionThrown = false;
        try {
            c1.multiply((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testDivide() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(1.0, -1.0);
        Complex div = c1.divide(c2);
        assertEquals(-0.5, div.getReal(), 1e-12);
        assertEquals(1.5, div.getImaginary(), 1e-12);

        Complex divDouble = c1.divide(2.0);
        assertEquals(0.5, divDouble.getReal(), 1e-12);
        assertEquals(1.0, divDouble.getImaginary(), 1e-12);

        Complex nanDiv = c1.divide(Complex.NaN);
        assertSame(Complex.NaN, nanDiv);

        Complex zeroDiv = c1.divide(Complex.ZERO);
        assertSame(Complex.NaN, zeroDiv);

        Complex zeroDivDouble = c1.divide(0.0);
        assertSame(Complex.NaN, zeroDivDouble);

        Complex infDiv = c1.divide(Complex.INF);
        assertSame(Complex.ZERO, infDiv);

        Complex infDivDouble = c1.divide(Double.POSITIVE_INFINITY);
        assertSame(Complex.ZERO, infDivDouble);

        Complex finiteDivInfinite = Complex.INF.divide(2.0);
        assertSame(Complex.INF, finiteDivInfinite);

        boolean exceptionThrown = false;
        try {
            c1.divide((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testReciprocal() throws Throwable {
        Complex c = new Complex(2.0, 0.0);
        Complex rec = c.reciprocal();
        assertEquals(0.5, rec.getReal(), 1e-12);
        assertEquals(0.0, rec.getImaginary(), 1e-12);

        Complex cZero = Complex.ZERO;
        assertSame(Complex.NaN, cZero.reciprocal());

        Complex cNan = Complex.NaN;
        assertSame(Complex.NaN, cNan.reciprocal());

        Complex cInf = Complex.INF;
        assertSame(Complex.ZERO, cInf.reciprocal());

        Complex cAlt = new Complex(1.0, 2.0);
        Complex recAlt = cAlt.reciprocal();
        assertEquals(1.0 / 5.0, recAlt.getReal(), 1e-12);
        assertEquals(-2.0 / 5.0, recAlt.getImaginary(), 1e-12);
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
        Complex c3 = new Complex(1.0, 3.0);

        assertEquals(c1, c1);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertFalse(c1.equals(c3));
        assertFalse(c1.equals(null));
        assertFalse(c1.equals("SomeString"));

        Complex nan1 = new Complex(Double.NaN, 1.0);
        Complex nan2 = new Complex(1.0, Double.NaN);
        assertEquals(nan1, nan2);
        assertEquals(nan1.hashCode(), nan2.hashCode());
        assertEquals(7, nan1.hashCode());
    }

    @Test
    public void testTrigonometricFunctions() throws Throwable {
        Complex c = new Complex(1.0, 1.0);

        assertNotNull(c.sin());
        assertNotNull(c.cos());
        assertNotNull(c.tan());
        assertNotNull(c.sinh());
        assertNotNull(c.cosh());
        assertNotNull(c.tanh());

        assertSame(Complex.NaN, Complex.NaN.sin());
        assertSame(Complex.NaN, Complex.NaN.cos());
        assertSame(Complex.NaN, Complex.NaN.tan());
        assertSame(Complex.NaN, Complex.NaN.sinh());
        assertSame(Complex.NaN, Complex.NaN.cosh());
        assertSame(Complex.NaN, Complex.NaN.tanh());

        assertEquals(Complex.NaN, new Complex(Double.POSITIVE_INFINITY, 0.0).tan());
        assertEquals(Complex.NaN, new Complex(0.0, Double.POSITIVE_INFINITY).tanh());

        assertEquals(new Complex(0.0, 1.0), new Complex(0.0, 25.0).tan());
        assertEquals(new Complex(0.0, -1.0), new Complex(0.0, -25.0).tan());
        assertEquals(new Complex(1.0, 0.0), new Complex(25.0, 0.0).tanh());
        assertEquals(new Complex(-1.0, 0.0), new Complex(-25.0, 0.0).tanh());
    }

    @Test
    public void testInverseTrigonometricFunctions() throws Throwable {
        Complex c = new Complex(0.5, 0.5);

        assertNotNull(c.asin());
        assertNotNull(c.acos());
        assertNotNull(c.atan());

        assertSame(Complex.NaN, Complex.NaN.asin());
        assertSame(Complex.NaN, Complex.NaN.acos());
        assertSame(Complex.NaN, Complex.NaN.atan());
    }

    @Test
    public void testExpLogPow() throws Throwable {
        Complex c = new Complex(1.0, 1.0);

        assertNotNull(c.exp());
        assertNotNull(c.log());
        assertNotNull(c.pow(c));
        assertNotNull(c.pow(2.0));

        assertSame(Complex.NaN, Complex.NaN.exp());
        assertSame(Complex.NaN, Complex.NaN.log());
        assertSame(Complex.NaN, Complex.NaN.pow(c));

        boolean exceptionThrown = false;
        try {
            c.pow((Complex) null);
        } catch (NullArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testSqrt() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        Complex sqrt = c.sqrt();
        assertNotNull(sqrt);

        Complex cNeg = new Complex(-3.0, 4.0);
        assertNotNull(cNeg.sqrt());

        Complex cZero = Complex.ZERO;
        assertEquals(0.0, cZero.sqrt().getReal(), 1e-12);

        assertSame(Complex.NaN, Complex.NaN.sqrt());
        assertNotNull(c.sqrt1z());
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
        assertSame(Complex.NaN, nanRoots.get(0));

        List<Complex> infRoots = Complex.INF.nthRoot(2);
        assertEquals(1, infRoots.size());
        assertSame(Complex.INF, infRoots.get(0));

        boolean exceptionThrown = false;
        try {
            c.nthRoot(0);
        } catch (NotPositiveException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testFieldAndSerializationHelpers() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertNotNull(c.getField());
        assertEquals("(1.0, 2.0)", c.toString());
        
        Object resolved = c.readResolve();
        assertTrue(resolved instanceof Complex);
        assertEquals(c, resolved);
    }
}