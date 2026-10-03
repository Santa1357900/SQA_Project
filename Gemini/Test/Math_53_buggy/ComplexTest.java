package org.apache.commons.math.complex;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import org.apache.commons.math.exception.NullArgumentException;

public class ComplexTest {

    @Test
    public void testConstants() throws Throwable {
        assertEquals(0.0, Complex.I.getReal(), 1e-12);
        assertEquals(1.0, Complex.I.getImaginary(), 1e-12);

        assertTrue(Complex.NaN.isNaN());
        assertTrue(Complex.INF.isInfinite());

        assertEquals(1.0, Complex.ONE.getReal(), 1e-12);
        assertEquals(0.0, Complex.ONE.getImaginary(), 1e-12);

        assertEquals(0.0, Complex.ZERO.getReal(), 1e-12);
        assertEquals(0.0, Complex.ZERO.getImaginary(), 1e-12);
    }

    @Test
    public void testAbs() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-12);

        Complex cZero = new Complex(0.0, 0.0);
        assertEquals(0.0, cZero.abs(), 1e-12);

        Complex cRealZero = new Complex(0.0, 3.0);
        assertEquals(3.0, cRealZero.abs(), 1e-12);

        Complex cImagZero = new Complex(3.0, 0.0);
        assertEquals(3.0, cImagZero.abs(), 1e-12);

        Complex cNaN = new Complex(Double.NaN, 1.0);
        assertTrue(Double.isNaN(cNaN.abs()));

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), cInf.abs(), 1e-12);
    }

    @Test
    public void testAdd() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex sum = c1.add(c2);
        assertEquals(4.0, sum.getReal(), 1e-12);
        assertEquals(6.0, sum.getImaginary(), 1e-12);

        try {
            c1.add(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testConjugate() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Complex conj = c.conjugate();
        assertEquals(1.0, conj.getReal(), 1e-12);
        assertEquals(-2.0, conj.getImaginary(), 1e-12);

        Complex cNaN = new Complex(Double.NaN, Double.NaN);
        assertTrue(cNaN.conjugate().isNaN());

        Complex cInf = new Complex(1.0, Double.POSITIVE_INFINITY);
        assertEquals(1.0, cInf.conjugate().getReal(), 1e-12);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), cInf.conjugate().getImaginary(), 1e-12);
    }

    @Test
    public void testDivide() throws Throwable {
        Complex c1 = new Complex(1.0, 1.0);
        Complex c2 = new Complex(1.0, 1.0);
        Complex div = c1.divide(c2);
        assertEquals(1.0, div.getReal(), 1e-12);
        assertEquals(0.0, div.getImaginary(), 1e-12);

        Complex cZero = new Complex(0.0, 0.0);
        assertTrue(c1.divide(cZero).isNaN());
        assertTrue(new Complex(Double.NaN, 1.0).divide(c2).isNaN());
        assertTrue(c1.divide(new Complex(Double.NaN, 1.0)).isNaN());

        Complex finite = new Complex(1.0, 1.0);
        Complex infinite = new Complex(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertEquals(Complex.ZERO, finite.divide(infinite));

        assertTrue(infinite.divide(infinite).isNaN());

        Complex cAlt = new Complex(3.0, 4.0);
        Complex cAlt2 = new Complex(1.0, 2.0);
        Complex divAlt = cAlt.divide(cAlt2);
        assertEquals(2.2, divAlt.getReal(), 1e-12);
        assertEquals(-0.4, divAlt.getImaginary(), 1e-12);

        try {
            c1.divide(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex c3 = new Complex(2.0, 1.0);
        Complex cNaN1 = new Complex(Double.NaN, 1.0);
        Complex cNaN2 = new Complex(1.0, Double.NaN);

        assertEquals(c1, c1);
        assertEquals(c1, c2);
        assertFalse(c1.equals(c3));
        assertFalse(c1.equals(null));
        assertFalse(c1.equals("Not a Complex"));

        assertEquals(cNaN1, cNaN2);
        assertEquals(cNaN1.hashCode(), cNaN2.hashCode());
        assertEquals(7, cNaN1.hashCode());
        assertTrue(c1.hashCode() != 7);
    }

    @Test
    public void testGettersAndPredicates() throws Throwable {
        Complex c = new Complex(1.5, 2.5);
        assertEquals(1.5, c.getReal(), 1e-12);
        assertEquals(2.5, c.getImaginary(), 1e-12);
        assertFalse(c.isNaN());
        assertFalse(c.isInfinite());

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 0.0);
        assertTrue(cInf.isInfinite());
        assertFalse(cInf.isNaN());
    }

    @Test
    public void testMultiplyComplex() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex prod = c1.multiply(c2);
        assertEquals(-5.0, prod.getReal(), 1e-12);
        assertEquals(10.0, prod.getImaginary(), 1e-12);

        assertTrue(new Complex(Double.NaN, 1.0).multiply(c2).isNaN());
        assertTrue(c1.multiply(new Complex(Double.NaN, 1.0)).isNaN());

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertEquals(Complex.INF, c1.multiply(cInf));
        assertEquals(Complex.INF, cInf.multiply(c1));

        try {
            c1.multiply((Complex) null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testMultiplyScalar() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Complex prod = c.multiply(2.5);
        assertEquals(2.5, prod.getReal(), 1e-12);
        assertEquals(5.0, prod.getImaginary(), 1e-12);

        assertTrue(new Complex(Double.NaN, 1.0).multiply(2.5).isNaN());
        assertTrue(c.multiply(Double.NaN).isNaN());

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertEquals(Complex.INF, cInf.multiply(2.5));
        assertEquals(Complex.INF, c.multiply(Double.POSITIVE_INFINITY));
    }

    @Test
    public void testNegate() throws Throwable {
        Complex c = new Complex(1.0, -2.0);
        Complex neg = c.negate();
        assertEquals(-1.0, neg.getReal(), 1e-12);
        assertEquals(2.0, neg.getImaginary(), 1e-12);

        assertTrue(new Complex(Double.NaN, 1.0).negate().isNaN());
    }

    @Test
    public void testSubtract() throws Throwable {
        Complex c1 = new Complex(3.0, 5.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex diff = c1.subtract(c2);
        assertEquals(2.0, diff.getReal(), 1e-12);
        assertEquals(3.0, diff.getImaginary(), 1e-12);

        assertTrue(new Complex(Double.NaN, 1.0).subtract(c2).isNaN());
        assertTrue(c1.subtract(new Complex(Double.NaN, 1.0)).isNaN());

        try {
            c1.subtract(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testTrigFunctions() throws Throwable {
        Complex c = new Complex(1.0, 1.0);
        assertTrue(!c.sin().isNaN());
        assertTrue(!c.cos().isNaN());
        assertTrue(!c.tan().isNaN());
        assertTrue(!c.sinh().isNaN());
        assertTrue(!c.cosh().isNaN());
        assertTrue(!c.tanh().isNaN());
        assertTrue(!c.asin().isNaN());
        assertTrue(!c.acos().isNaN());
        assertTrue(!c.atan().isNaN());

        Complex cNaN = new Complex(Double.NaN, 0.0);
        assertTrue(cNaN.sin().isNaN());
        assertTrue(cNaN.cos().isNaN());
        assertTrue(cNaN.tan().isNaN());
        assertTrue(cNaN.sinh().isNaN());
        assertTrue(cNaN.cosh().isNaN());
        assertTrue(cNaN.tanh().isNaN());
        assertTrue(cNaN.asin().isNaN());
        assertTrue(cNaN.acos().isNaN());
        assertTrue(cNaN.atan().isNaN());
    }

    @Test
    public void testExpLogPow() throws Throwable {
        Complex c = new Complex(1.0, 1.0);
        assertTrue(!c.exp().isNaN());
        assertTrue(!c.log().isNaN());
        assertTrue(!c.sqrt().isNaN());
        assertTrue(!c.sqrt1z().isNaN());

        Complex cNaN = new Complex(Double.NaN, 0.0);
        assertTrue(cNaN.exp().isNaN());
        assertTrue(cNaN.log().isNaN());
        assertTrue(cNaN.sqrt().isNaN());

        Complex cZero = new Complex(0.0, 0.0);
        assertEquals(cZero, cZero.sqrt());

        Complex cNeg = new Complex(-1.0, 0.0);
        assertTrue(!cNeg.sqrt().isNaN());

        Complex p = c.pow(new Complex(2.0, 0.0));
        assertTrue(!p.isNaN());

        try {
            c.pow(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testNthRoot() throws Throwable {
        Complex c = new Complex(1.0, 0.0);
        List<Complex> roots = c.nthRoot(2);
        assertEquals(2, roots.size());

        Complex cNaN = new Complex(Double.NaN, 1.0);
        List<Complex> nanRoots = cNaN.nthRoot(2);
        assertEquals(1, nanRoots.size());
        assertTrue(nanRoots.get(0).isNaN());

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        List<Complex> infRoots = cInf.nthRoot(2);
        assertEquals(1, infRoots.size());
        assertEquals(Complex.INF, infRoots.get(0));

        try {
            c.nthRoot(0);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testMiscellaneous() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertNotNull(c.getField());
        assertEquals("(3.0, 4.0)", c.toString());
        assertEquals(Math.atan2(4.0, 3.0), c.getArgument(), 1e-12);
        
        Complex resolved = (Complex) c.readResolve();
        assertEquals(c, resolved);
    }
}