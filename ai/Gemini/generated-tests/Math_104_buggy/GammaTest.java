package org.apache.commons.math.special;

import junit.framework.TestCase;
import org.apache.commons.math.MathException;
import org.apache.commons.math.MaxIterationsExceededException;

public class GammaTest extends TestCase {

    public void testLogGammaInvalidValues() throws Throwable {
        assertTrue(Double.isNaN(Gamma.logGamma(Double.NaN)));
        assertTrue(Double.isNaN(Gamma.logGamma(0.0)));
        assertTrue(Double.isNaN(Gamma.logGamma(-1.5)));
    }

    public void testLogGammaValidValues() throws Throwable {
        double result = Gamma.logGamma(1.0);
        assertEquals(0.0, result, 1e-9);

        double result2 = Gamma.logGamma(2.0);
        assertEquals(0.0, result2, 1e-9);

        double result3 = Gamma.logGamma(5.0);
        // Gamma(5) = 24, log(24) approx 3.17805383
        assertEquals(3.17805383, result3, 1e-5);
    }

    public void testRegularizedGammaPInvalidValues() throws Throwable {
        assertTrue(Double.isNaN(Gamma.regularizedGammaP(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaP(1.0, Double.NaN)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaP(0.0, 1.0)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaP(-1.0, 1.0)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaP(1.0, -1.0)));
    }

    public void testRegularizedGammaPZeroX() throws Throwable {
        assertEquals(0.0, Gamma.regularizedGammaP(1.0, 0.0), 1e-9);
    }

    public void testRegularizedGammaPGreaterBranch() throws Throwable {
        // a >= 1.0 and x > a branch triggers 1.0 - regularizedGammaQ
        double val = Gamma.regularizedGammaP(2.0, 5.0);
        assertTrue(val >= 0.0 && val <= 1.0);
    }

    public void testRegularizedGammaPSeries() throws Throwable {
        // Series branch: a < 1.0 or x <= a
        double val = Gamma.regularizedGammaP(0.5, 0.5);
        assertTrue(val >= 0.0 && val <= 1.0);
    }

    public void testRegularizedGammaPMaxIterationsExceeded() throws Throwable {
        try {
            Gamma.regularizedGammaP(1.0, 10.0, 1e-15, 1);
            fail("Expected MaxIterationsExceededException");
        } catch (MaxIterationsExceededException e) {
            assertNotNull(e);
        }
    }

    public void testRegularizedGammaQInvalidValues() throws Throwable {
        assertTrue(Double.isNaN(Gamma.regularizedGammaQ(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaQ(1.0, Double.NaN)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaQ(0.0, 1.0)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaQ(-1.0, 1.0)));
        assertTrue(Double.isNaN(Gamma.regularizedGammaQ(1.0, -1.0)));
    }

    public void testRegularizedGammaQZeroX() throws Throwable {
        assertEquals(1.0, Gamma.regularizedGammaQ(1.0, 0.0), 1e-9);
    }

    public void testRegularizedGammaQFallbackToP() throws Throwable {
        // x < a or a < 1.0 branch triggers 1.0 - regularizedGammaP
        double val = Gamma.regularizedGammaQ(2.0, 1.0);
        assertTrue(val >= 0.0 && val <= 1.0);
    }

    public void testRegularizedGammaQContinuedFraction() throws Throwable {
        // Continued fraction branch: x >= a and a >= 1.0
        double val = Gamma.regularizedGammaQ(1.0, 5.0);
        assertTrue(val >= 0.0 && val <= 1.0);
    }

    public void testRegularizedGammaDefaultMethods() throws Throwable {
        double pVal = Gamma.regularizedGammaP(2.0, 2.0);
        double qVal = Gamma.regularizedGammaQ(2.0, 2.0);
        assertEquals(1.0, pVal + qVal, 1e-7);
    }
}