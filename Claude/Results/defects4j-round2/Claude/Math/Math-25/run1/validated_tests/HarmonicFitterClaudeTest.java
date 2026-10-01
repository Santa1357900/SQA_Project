package org.apache.commons.math3.optimization.fitting;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math3.exception.NumberIsTooSmallException;

public class HarmonicFitterClaudeTest {

    // Constructor: observations.length == 0 (< 4) must throw NumberIsTooSmallException
    @Test
    public void testConstructor_zeroObservations_throwsNumberIsTooSmallException() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[0];
        try {
            new HarmonicFitter.ParameterGuesser(obs);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
        }
    }

    // Constructor: observations.length == 1 (< 4) must throw NumberIsTooSmallException
    @Test
    public void testConstructor_oneObservation_throwsNumberIsTooSmallException() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 0.0)
        };
        try {
            new HarmonicFitter.ParameterGuesser(obs);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
        }
    }

    // Constructor: observations.length == 2 (< 4) must throw NumberIsTooSmallException
    @Test
    public void testConstructor_twoObservations_throwsNumberIsTooSmallException() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 0.0),
            new WeightedObservedPoint(1.0, 1.0, 1.0)
        };
        try {
            new HarmonicFitter.ParameterGuesser(obs);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
        }
    }

    // Constructor: observations.length == 3 (< 4, boundary just below threshold) must throw
    @Test
    public void testConstructor_threeObservations_throwsNumberIsTooSmallException() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 0.0),
            new WeightedObservedPoint(1.0, 1.0, 1.0),
            new WeightedObservedPoint(1.0, 2.0, 0.0)
        };
        try {
            new HarmonicFitter.ParameterGuesser(obs);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
        }
    }

    // Constructor: observations.length == 4 (boundary, exactly at threshold) must NOT throw
    @Test
    public void testConstructor_fourObservations_doesNotThrow() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 0.0),
            new WeightedObservedPoint(1.0, 1.0, 1.0),
            new WeightedObservedPoint(1.0, 2.0, 0.0),
            new WeightedObservedPoint(1.0, 3.0, -1.0)
        };
        HarmonicFitter.ParameterGuesser guesser = new HarmonicFitter.ParameterGuesser(obs);
        assertNotNull(guesser);
    }

    // guess() must return an array of exactly 3 elements: amplitude, omega, phi
    @Test
    public void testGuess_returnsArrayOfLengthThree() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 100.0),
            new WeightedObservedPoint(1.0, 1.0, 0.0),
            new WeightedObservedPoint(1.0, 2.0, 0.0),
            new WeightedObservedPoint(1.0, 3.0, 0.0)
        };
        double[] result = new HarmonicFitter.ParameterGuesser(obs).guess();
        assertEquals(3, result.length);
    }





    // Fallback branch: omega = 2*PI / xRange, deterministic check independent of the amplitude bug
    @Test
    public void testGuess_fallbackBranch_omegaValue() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 100.0),
            new WeightedObservedPoint(1.0, 1.0, 0.0),
            new WeightedObservedPoint(1.0, 2.0, 0.0),
            new WeightedObservedPoint(1.0, 3.0, 0.0)
        };
        double[] result = new HarmonicFitter.ParameterGuesser(obs).guess();
        assertEquals(2.0 * Math.PI / 3.0, result[1], 1e-9);
    }

    // Fallback branch: phi must be a finite value within atan2's contractual range [-PI, PI]
    @Test
    public void testGuess_fallbackBranch_phiIsFinite() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 100.0),
            new WeightedObservedPoint(1.0, 1.0, 0.0),
            new WeightedObservedPoint(1.0, 2.0, 0.0),
            new WeightedObservedPoint(1.0, 3.0, 0.0)
        };
        double[] result = new HarmonicFitter.ParameterGuesser(obs).guess();
        assertFalse(Double.isNaN(result[2]));
        assertTrue(result[2] >= -Math.PI && result[2] <= Math.PI);
    }





    // Well-conditioned branch (else): dense exact harmonic samples should recover a and omega closely
    @Test
    public void testGuess_smoothHarmonicData_amplitudeAndOmegaApproximatelyCorrect() throws Throwable {
        double aTrue = 3.0, omegaTrue = 2.0, phiTrue = 0.3;
        WeightedObservedPoint[] obs = new WeightedObservedPoint[101];
        for (int i = 0; i < 101; i++) {
            double t = i * 0.1;
            obs[i] = new WeightedObservedPoint(1.0, t, aTrue * Math.cos(omegaTrue * t + phiTrue));
        }
        double[] result = new HarmonicFitter.ParameterGuesser(obs).guess();
        assertEquals(aTrue, result[0], 0.5);
        assertEquals(omegaTrue, result[1], 0.3);
    }

    // Well-conditioned branch: per javadoc contract both amplitude and angular frequency are assumed positive
    @Test
    public void testGuess_smoothHarmonicData_amplitudeAndOmegaArePositive() throws Throwable {
        double aTrue = 3.0, omegaTrue = 2.0, phiTrue = 0.3;
        WeightedObservedPoint[] obs = new WeightedObservedPoint[101];
        for (int i = 0; i < 101; i++) {
            double t = i * 0.1;
            obs[i] = new WeightedObservedPoint(1.0, t, aTrue * Math.cos(omegaTrue * t + phiTrue));
        }
        double[] result = new HarmonicFitter.ParameterGuesser(obs).guess();
        assertTrue(result[0] > 0.0);
        assertTrue(result[1] > 0.0);
    }

    // Well-conditioned branch: phi guessed via atan2 must stay within its contractual range
    @Test
    public void testGuess_smoothHarmonicData_phiWithinRange() throws Throwable {
        double aTrue = 3.0, omegaTrue = 2.0, phiTrue = 0.3;
        WeightedObservedPoint[] obs = new WeightedObservedPoint[101];
        for (int i = 0; i < 101; i++) {
            double t = i * 0.1;
            obs[i] = new WeightedObservedPoint(1.0, t, aTrue * Math.cos(omegaTrue * t + phiTrue));
        }
        double[] result = new HarmonicFitter.ParameterGuesser(obs).guess();
        assertTrue(result[2] >= -Math.PI && result[2] <= Math.PI);
    }



    // Fallback branch with outlier at the LAST point: bug does not manifest since index 0 is not special there
    @Test
    public void testGuess_fallbackBranch_outlierAtLastPoint_amplitudeUnaffectedByBug() throws Throwable {
        WeightedObservedPoint[] obs = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 0.0),
            new WeightedObservedPoint(1.0, 1.0, 0.0),
            new WeightedObservedPoint(1.0, 2.0, 0.0),
            new WeightedObservedPoint(1.0, 3.0, 100.0)
        };
        double[] result = new HarmonicFitter.ParameterGuesser(obs).guess();
        assertEquals(50.0, result[0], 1e-9);
    }






}
