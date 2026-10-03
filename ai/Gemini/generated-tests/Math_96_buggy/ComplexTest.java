package org.apache.commons.math.complex;

import org.junit.Test;
import static org.junit.Assert.*;

public class ComplexTest {

    @Test
    public void testConstants() throws Throwable {
        assertEquals(0.0, Complex.I.getReal(), 0.0);
        assertEquals(1.0, Complex.I.getImaginary(), 0.0);

        assertTrue(Complex.NaN.isNaN());
        assertTrue(Complex.INF.isInfinite());
        
        assertEquals(1.0, Complex.ONE.getReal(), 0.0);
        assertEquals(0.0, Complex.ONE.getImaginary(), 0.0);

        assertEquals(0.0, Complex.ZERO.getReal(), 0.0);
        assertEquals(0.0, Complex.ZERO.getImaginary(), 0.0);
    }

    @Test
    public void testAbs() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-12);

        Complex cZeroImag = new Complex(3.0, 0.0);
        assertEquals(3.0, cZeroImag.abs(), 1e-12);

        Complex cZeroReal = new Complex(0.0, 4.0);
        assertEquals(4.0, cZeroReal.abs(), 1e-12);

        Complex cNano = new Complex(Double.NaN, 1.0);
        assertTrue(Double.isNaN(cNano.abs()));

        Complex cInf = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), cInf.abs(), 0.0);

        Complex cAlt = new Complex(1.0, 3.0);
        assertEquals(Math.sqrt(10.0), cAlt.abs(), 1e-12);
    }

    @Test
    public void testAdd() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex sum = c1.add(c2);
        assertEquals(4.0, sum.getReal(), 0.0);
        assertEquals(6.0, sum.getImaginary(), 0.0);
    }

    @Test(expected = NullPointerException.class)
    public void testAddNull() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        c.add(null);
    }

    @Test
    public void testConjugate() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        Complex conj = c.conjugate();
        assertEquals(1.0, conj.getReal(), 0.0);
        assertEquals(-2.0, conj.getImaginary(), 0.0);

        Complex cNaN = new Complex(Double.NaN, 2.0);
        assertTrue(cNaN.conjugate().isNaN());

        Complex cInfImag = new Complex(1.0, Double.POSITIVE_INFINITY);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), cInfImag.conjugate().getImaginary(), 0.0);
    }

    @Test
    public void testDivide() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex res = c1.divide(c2);
        assertNotNull(res);

        Complex cNaN = new Complex(Double.NaN, 0.0);
        assertTrue(c1.divide(cNaN).isNaN());
        assertTrue(cNaN.divide(c1).isNaN());

        Complex cZero = new Complex(0.0, 0.0);
        assertTrue(c1.divide(cZero).isNaN());

        Complex inf = new Complex(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertTrue(c1.divide(inf).equals(Complex.ZERO));
        assertTrue(inf.divide(inf).isNaN());

        Complex cAbsCmp1 = new Complex(1.0, 5.0);
        Complex cAbsCmp2 = new Complex(2.0, 1.0);
        assertNotNull(cAbsCmp1.divide(cAbsCmp2));

        Complex cZeroC = new Complex(0.0, 2.0);
        Complex cZeroD = new Complex(0.0, 1.0);
        assertNotNull(cZeroC.divide(cZeroD));
    }

    @Test(expected = NullPointerException.class)
    public void testDivideNull() throws Throwable {
        new Complex(1.0, 2.0).divide(null);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex c3 = new Complex(2.0, 1.0);
        Complex cNaN1 = new Complex(Double.NaN, 1.0);
        Complex cNaN2 = new Complex(1.0, Double.NaN);

        assertTrue(c1.equals(c1));
        assertTrue(c1.equals(c2));
        assertFalse(c1.equals(c3));
        assertFalse(c1.equals(null));
        assertFalse(c1.equals("NotAComplex"));

        assertTrue(cNaN1.equals(cNaN2));
        assertFalse(c1.equals(cNaN1));
        
        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals(Complex.NaN.hashCode(), cNaN1.hashCode());
    }

    @Test
    public void testIsInfiniteAndNaN() throws Throwable {
        Complex normal = new Complex(1.0, 2.0);
        assertFalse(normal.isNaN());
        assertFalse(normal.isInfinite());

        Complex nan = new Complex(Double.NaN, 2.0);
        assertTrue(nan.isNaN());
        assertFalse(nan.isInfinite());

        Complex inf = new Complex(Double.POSITIVE_INFINITY, 2.0);
        assertFalse(inf.isNaN());
        assertTrue(inf.isInfinite());
    }

    @Test
    public void testMultiply() throws Throwable {
        Complex c1 = new Complex(1.0, 2.0);
        Complex c2 = new Complex(3.0, 4.0);
        Complex prod = c1.multiply(c2);
        assertEquals(-5.0, prod.getReal(), 0.0);
        assertEquals(10.0, prod.getImaginary(), 0.0);

        Complex cNaN = new Complex(Double.NaN, 0.0);
        assertTrue(c1.multiply(cNaN).isNaN());
        assertTrue(cNaN.multiply(c1).isNaN());

        Complex inf = new Complex(Double.POSITIVE_INFINITY, 0.0);
        assertEquals(Complex.INF, c1.multiply(inf));
    }

    @Test(expected = NullPointerException.class)
    public void testMultiplyNull() throws Throwable {
        new Complex(1.0, 1.0).multiply(null);
    }

    @Test
    public void testNegate() throws Throwable {
        Complex c = new Complex(1.0, -2.0);
        Complex neg = c.negate();
        assertEquals(-1.0, neg.getReal(), 0.0);
        assertEquals(2.0, neg.getImaginary(), 0.0);

        assertTrue(new Complex(Double.NaN, 1.0).negate().isNaN());
    }

    @Test
    public void testSubtract() throws Throwable {
        Complex c1 = new Complex(5.0, 6.0);
        Complex c2 = new Complex(1.0, 2.0);
        Complex sub = c1.subtract(c2);
        assertEquals(4.0, sub.getReal(), 0.0);
        assertEquals(4.0, sub.getImaginary(), 0.0);

        assertTrue(c1.subtract(new Complex(Double.NaN, 0.0)).isNaN());
    }

    @Test(expected = NullPointerException.class)
    public void testSubtractNull() throws Throwable {
        new Complex(1.0, 1.0).subtract(null);
    }

    @Test
    public void testTrigFunctions() throws Throwable {
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
        assertTrue(Complex.NaN.tan().isNaN());
        assertTrue(Complex.NaN.tanh().isNaN());
    }

    @Test
    public void testPow() throws Throwable {
        Complex c = new Complex(2.0, 1.0);
        Complex exp = new Complex(1.0, 0.0);
        assertNotNull(c.pow(exp));
    }

    @Test(expected = NullPointerException.class)
    public void testPowNull() throws Throwable {
        new Complex(1.0, 1.0).pow(null);
    }

    @Test
    public void testSqrt() throws Throwable {
        Complex cPos = new Complex(4.0, 0.0);
        Complex sqrtPos = cPos.sqrt();
        assertEquals(2.0, sqrtPos.getReal(), 1e-12);
        assertEquals(0.0, sqrtPos.getImaginary(), 1e-12);

        Complex cNeg = new Complex(-4.0, 0.0);
        Complex sqrtNeg = cNeg.sqrt();
        assertEquals(0.0, sqrtNeg.getReal(), 1e-12);
        assertEquals(2.0, sqrtNeg.getImaginary(), 1e-12);

        Complex cZero = new Complex(0.0, 0.0);
        assertEquals(Complex.ZERO, cZero.sqrt());

        assertTrue(Complex.NaN.sqrt().isNaN());
    }

    @Test
    public void testSqrt1z() throws Throwable {
        Complex c = new Complex(0.5, 0.5);
        assertNotNull(c.sqrt1z());
    }

    @Test
    public void testCreateComplex() throws Throwable {
        Complex c = new Complex(1.0, 1.0);
        Complex created = c.createComplex(2.0, 3.0);
        assertEquals(2.0, created.getReal(), 0.0);
        assertEquals(3.0, created.getImaginary(), 0.0);
    }
}