package org.apache.commons.math.stat.descriptive.moment;

import org.junit.Test;
import static org.junit.Assert.*;

public class VarianceTest {

    @Test
    public void testDefaultConstructorAndBasicOperations() throws Throwable {
        Variance variance = new Variance();
        assertTrue(variance.isBiasCorrected());
        assertEquals(Double.valueOf(Double.NaN), variance.getResult(), 1e-10);
        assertEquals(0L, variance.getN());

        variance.increment(1.0);
        assertEquals(0.0, variance.getResult(), 1e-10);
        assertEquals(1L, variance.getN());

        variance.increment(3.0);
        assertEquals(2.0, variance.getResult(), 1e-10);
        assertEquals(2L, variance.getN());

        variance.clear();
        assertEquals(Double.valueOf(Double.NaN), variance.getResult(), 1e-10);
        assertEquals(0L, variance.getN());
    }

    @Test
    public void testConstructorsAndBiasCorrection() throws Throwable {
        Variance v1 = new Variance(false);
        assertFalse(v1.isBiasCorrected());

        SecondMoment m2 = new SecondMoment();
        Variance v2 = new Variance(m2);
        assertFalse(v2.incMoment);

        Variance v3 = new Variance(false, m2);
        assertFalse(v3.isBiasCorrected());
        assertFalse(v3.incMoment);

        v1.setBiasCorrected(true);
        assertTrue(v1.isBiasCorrected());
    }

    @Test
    public void testCopyConstructorAndCopyMethod() throws Throwable {
        Variance v1 = new Variance();
        v1.increment(2.0);
        v1.increment(4.0);

        Variance v2 = new Variance(v1);
        assertEquals(v1.getResult(), v2.getResult(), 1e-10);
        assertEquals(v1.getN(), v2.getN());
        assertEquals(v1.isBiasCorrected(), v2.isBiasCorrected());

        Variance v3 = v1.copy();
        assertEquals(v1.getResult(), v3.getResult(), 1e-10);
        assertEquals(v1.getN(), v3.getN());

        Variance.copy(v1, v3);
        assertEquals(v1.getN(), v3.getN());
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testCopyNullSource() throws Throwable {
        Variance dest = new Variance();
        Variance.copy(null, dest);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testCopyNullDest() throws Throwable {
        Variance source = new Variance();
        Variance.copy(source, null);
    }

    @Test
    public void testEvaluateArrayDouble() throws Throwable {
        Variance variance = new Variance();
        double[] values = new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 };

        assertEquals(2.5, variance.evaluate(values), 1e-10);

        Variance popVariance = new Variance(false);
        assertEquals(2.0, popVariance.evaluate(values), 1e-10);

        assertEquals(0.0, variance.evaluate(new double[] { 5.0 }), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), variance.evaluate(new double[] {}), 1e-10);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testEvaluateNullArray() throws Throwable {
        Variance variance = new Variance();
        variance.evaluate((double[]) null);
    }

    @Test
    public void testEvaluateArraySubpart() throws Throwable {
        Variance variance = new Variance();
        double[] values = new double[] { 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0 };

        assertEquals(2.5, variance.evaluate(values, 1, 5), 1e-10);
        assertEquals(0.0, variance.evaluate(values, 2, 1), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), variance.evaluate(values, 2, 0), 1e-10);
    }

    @Test
    public void testEvaluateWithPrecomputedMean() throws Throwable {
        Variance variance = new Variance();
        double[] values = new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 };

        assertEquals(2.5, variance.evaluate(values, 3.0), 1e-10);
        assertEquals(2.0, variance.evaluate(values, 3.0, 0, 5), 1e-10); // using population formula block when isBiasCorrected = false
        
        Variance popVariance = new Variance(false);
        assertEquals(2.0, popVariance.evaluate(values, 3.0), 1e-10);
        assertEquals(0.0, popVariance.evaluate(values, 5.0, 2, 1), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), popVariance.evaluate(values, 3.0, 2, 0), 1e-10);
    }

    @Test
    public void testEvaluateWeighted() throws Throwable {
        Variance variance = new Variance();
        double[] values = new double[] { 1.0, 2.0, 3.0 };
        double[] weights = new double[] { 1.0, 1.0, 1.0 };

        assertEquals(1.0, variance.evaluate(values, weights), 1e-10);
        assertEquals(1.0, variance.evaluate(values, weights, 0, 3), 1e-10);
        assertEquals(0.0, variance.evaluate(values, weights, 0, 1), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), variance.evaluate(values, weights, 0, 0), 1e-10);

        Variance popVariance = new Variance(false);
        assertEquals(2.0 / 3.0, popVariance.evaluate(values, weights), 1e-10);

        assertEquals(1.0, variance.evaluate(values, weights, 2.0), 1e-10);
        assertEquals(1.0, variance.evaluate(values, weights, 2.0, 0, 3), 1e-10);

        assertEquals(2.0 / 3.0, popVariance.evaluate(values, weights, 2.0), 1e-10);
    }

    @Test
    public void testExternalSecondMomentIncrement() throws Throwable {
        SecondMoment m2 = new SecondMoment();
        Variance variance = new Variance(m2);

        // incrementing Variance directly should do nothing when incMoment is false
        variance.increment(10.0);
        assertEquals(0L, variance.getN());

        // incrementing SecondMoment should update both
        m2.increment(1.0);
        m2.increment(3.0);
        assertEquals(2.0, variance.getResult(), 1e-10);
        assertEquals(2L, variance.getN());

        // clear on Variance with external moment does nothing
        variance.clear();
        assertEquals(2L, variance.getN());
    }
}