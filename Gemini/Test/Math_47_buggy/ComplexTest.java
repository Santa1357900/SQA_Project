package org.apache.commons.math.complex;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.math.exception.NullArgumentException;
import org.apache.commons.math.exception.NotPositiveException;

public class ComplexTest {

    @Test
    public void testConstructors() throws Throwable {
        Complex c1 = new Complex(3.0);
        assertEquals(3.0, c1.getReal(), 1e-12);
        assertEquals(0.0, c1.getImaginary(), 1e-12);
        assertFalse(c1.isNaN());
        assertFalse(c1.isInfinite());

        Complex c2 = new Complex(Double.NaN, 2.0);
        assertTrue(c2.isNaN());
        assertFalse(c2.isInfinite());

        Complex c3 = new Complex(2.0, Double.NaN);
        assertTrue(c3.isNaN());

        Complex c4 = new Complex(Double.POSITIVE_INFINITY, 2.0);
        assertFalse(c4.isNaN());
        assertTrue(c4.isInfinite());

        Complex c5 = new Complex(2.0, Double.NEGATIVE_INFINITY);
        assertFalse(c5.isNaN());
        assertTrue(c5.isInfinite());
    }

    @Test
    public void testAbs() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-12);

        Complex cZero = new Complex(0.0, 0.0);
        assertEquals(0.0, cZero.abs(), 1e-12);

        Complex cNaN = Complex.NaN;
        assertTrue(Double.isNaN(cNaN.abs()));

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), cInf.abs(), 1e-12);

        Complex cAlt = new Complex(1.0, 3.0);
        assertTrue(cAlt.abs() > 0);

        Complex cRealZero = new Complex(0.0, 5.0);
        assertEquals(5.0, cRealZero.abs(), 1e-12);

        Complex cImagZero = new Complex(5.0, 0.0);
        assertEquals(5.0, cImagZero.abs(), 1e-12);
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
        assertTrue(nanAdd.isNaN());

        Complex doubleNanAdd = c1.add(Double.NaN);
        assertTrue(doubleNanAdd.isNaN());

        try {
            c1.add(null);
            fail("Should have thrown NullArgumentException");
        } catch (NullArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testConjugate() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Complex conj = c.conjugate();
        assertEquals(1.0, conj.getReal(), 1e-12);
        assertEquals(-2.0, conj.getImaginary(), 1e-12);

        Complex nanConj = Complex.NaN.conjugate();
        assertTrue(nanConj.isNaN());
    }

    @Test
    public void testDivide() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex div = c1.divide(c2);
        assertNotNull(div);

        Complex divDouble = c1.divide(2.0);
        assertEquals(0.5, divDouble.getReal(), 1e-12);
        assertEquals(1.0, divDouble.getImaginary(), 1e-12);

        assertTrue(c1.divide(Complex.NaN).isNaN());
        assertTrue(c1.divide(Complex.ZERO).isNaN());
        assertTrue(Complex.ZERO.divide(Complex.ZERO).isNaN());

        Complex infDiv = Complex.INF.divide(Complex.INF);
        assertTrue(infDiv.isNaN());

        Complex finiteDivInf = c1.divide(Complex.INF);
        assertEquals(0.0, finiteDivInf.getReal(), 1e-12);
        assertEquals(0.0, finiteDivInf.getImaginary(), 1e-12);

        assertTrue(c1.divide(Double.NaN).isNaN());
        assertTrue(c1.divide(0.0).isNaN());

        Complex divInfDouble = c1.divide(Double.POSITIVE_INFINITY);
        assertEquals(0.0, divInfDouble.getReal(), 1e-12);

        Complex divInfDoubleInf = Complex.INF.divide(Double.POSITIVE_INFINITY);
        assertTrue(divInfDoubleInf.isNaN());

        Complex cAbsCmp = new Complex(3.0, 4.0);
        Complex cAbsCmp2 = new Complex(1.0, 2.0);
        assertNotNull(cAbsCmp2.divide(cAbsCmp));

        try {
            c1.divide(null);
            fail("Should have thrown NullArgumentException");
        } catch (NullArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex c3 = new Complex(2.0, 1.0);

        assertEquals(c1, c1);
        assertEquals(c1, c2);
        assertFalse(c1.equals(c3));
        assertFalse(c1.equals(null));
        assertFalse(c1.equals("Not a complex"));

        assertEquals(Complex.NaN, Complex.NaN);
        Complex cNanCustom = new Complex(Double.NaN, 5.0);
        assertEquals(Complex.NaN, cNanCustom);
        assertEquals(cNanCustom, Complex.NaN);

        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals(7, Complex.NaN.hashCode());
    }

    @Test
    public void testAccessorsAndChecks() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertEquals(1.0, c.getReal(), 1e-12);
        assertEquals(2.0, c.getImaginary(), 1e-12);
        assertFalse(c.isNaN());
        assertFalse(c.isInfinite());

        assertNotNull(c.getField());
        assertNotNull(c.toString());
    }

    @Test
    public void testMultiply() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex prod = c1.multiply(c2);
        assertNotNull(prod);

        Complex prodDouble = c1.multiply(2.0);
        assertEquals(2.0, prodDouble.getReal(), 1e-12);
        assertEquals(4.0, prodDouble.getImaginary(), 1e-12);

        assertTrue(c1.multiply(Complex.NaN).isNaN());
        assertTrue(Complex.INF.multiply(c1).isInfinite());
        assertTrue(c1.multiply(Double.NaN).isNaN());
        assertTrue(Complex.INF.multiply(2.0).isInfinite());

        try {
            c1.multiply(null);
            fail("Should have thrown NullArgumentException");
        } catch (NullArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testNegate() throws Throwable {
        Complex c = new Complex(1.0, -2.0);
        Complex neg = c.negate();
        assertEquals(-1.0, neg.getReal(), 1e-12);
        assertEquals(2.0, neg.getImaginary(), 1e-12);

        assertTrue(Complex.NaN.negate().isNaN());
    }

    @Test
    public void testSubtract() throws Throwable {
        Complex c1 = new Complex(3.0, 5.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex sub = c1.subtract(c2);
        assertEquals(2.0, sub.getReal(), 1e-12);
        assertEquals(3.0, sub.getImaginary(), 1e-12);

        Complex subDouble = c1.subtract(1.0);
        assertEquals(2.0, subDouble.getReal(), 1e-12);
        assertEquals(5.0, subDouble.getImaginary(), 1e-12);

        assertTrue(c1.subtract(Complex.NaN).isNaN());
        assertTrue(c1.subtract(Double.NaN).isNaN());

        try {
            c1.subtract(null);
            fail("Should have thrown NullArgumentException");
        } catch (NullArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testTrigonometricAndHyperbolic() throws Throwable {
        Complex c = new Complex(1.0, 1.0);

        assertNotNull(c.acos());
        assertNotNull(c.asin());
        assertNotNull(c.atan());
        assertNotNull(c.cos());
        assertNotNull(c.cosh());
        assertNotNull(c.exp());
        assertNotNull(c.log());
        assertNotNull(c.sin());
        assertNotNull(c.sinh());
        assertNotNull(c.sqrt());
        assertNotNull(c.sqrt1z());
        assertNotNull(c.tan());
        assertNotNull(c.tanh());

        assertTrue(Complex.NaN.acos().isNaN());
        assertTrue(Complex.NaN.asin().isNaN());
        assertTrue(Complex.NaN.atan().isNaN());
        assertTrue(Complex.NaN.cos().isNaN());
        assertTrue(Complex.NaN.cosh().isNaN());
        assertTrue(Complex.NaN.exp().isNaN());
        assertTrue(Complex.NaN.log().isNaN());
        assertTrue(Complex.NaN.sin().isNaN());
        assertTrue(Complex.NaN.sinh().isNaN());
        assertTrue(Complex.NaN.sqrt().isNaN());
        assertTrue(Complex.NaN.tan().isNaN());
        assertTrue(Complex.NaN.tanh().isNaN());

        Complex cZero = new Complex(0.0, 0.0);
        assertNotNull(cZero.sqrt());

        Complex cNegReal = new Complex(-1.0, 2.0);
        assertNotNull(cNegReal.sqrt());
    }

    @Test
    public void testPow() throws Throwable {
        Complex c = new Complex(2.0, 1.0);
        Complex exp = new Complex(1.0, 1.0);
        assertNotNull(c.pow(exp));
        assertNotNull(c.pow(2.0));

        try {
            c.pow((Complex) null);
            fail("Should have thrown NullArgumentException");
        } catch (NullArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testGetArgument() throws Throwable {
        Complex c = new Complex(1.0, 1.0);
        assertEquals(Math.PI / 4, c.getArgument(), 1e-12);
    }

    @Test
    public void testNthRoot() throws Throwable {
        Complex c = new Complex(1.0, 0.0);
        List<Complex> roots = c.nthRoot(2);
        assertEquals(2, roots.size());

        List<Complex> nanRoots = Complex.NaN.nthRoot(2);
        assertEquals(1, nanRoots.size());
        assertTrue(nanRoots.get(0).isNaN());

        List<Complex> infRoots = Complex.INF.nthRoot(2);
        assertEquals(1, infRoots.size());
        assertEquals(Complex.INF, infRoots.get(0));

        try {
            c.nthRoot(0);
            fail("Should have thrown NotPositiveException");
        } catch (NotPositiveException e) {
            // Expected
        }
    }

    @Test
    public void testValueOf() throws Throwable {
        Complex c1 = Complex.valueOf(1.0, 2.0);
        assertEquals(1.0, c1.getReal(), 1e-12);
        assertEquals(2.0, c1.getImaginary(), 1e-12);

        Complex c2 = Complex.valueOf(Double.NaN, 2.0);
        assertTrue(c2.isNaN());

        Complex c3 = Complex.valueOf(1.0);
        assertEquals(1.0, c3.getReal(), 1e-12);
        assertEquals(0.0, c3.getImaginary(), 1e-12);

        Complex c4 = Complex.valueOf(Double.NaN);
        assertTrue(c4.isNaN());
    }

    @Test
    public void testReadResolve() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Object resolved = c.readResolve();
        assertNotNull(resolved);
        assertTrue(resolved instanceof Complex);
        assertEquals(c, resolved);
    }
}