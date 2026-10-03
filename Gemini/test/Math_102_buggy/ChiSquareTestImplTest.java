package org.apache.commons.math.stat.inference;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;

public class ChiSquareTestImplTest {

    @Test
    public void testConstructors() throws Throwable {
        ChiSquareTestImpl test1 = new ChiSquareTestImpl();
        assertNotNull(test1);
        assertNotNull(test1.getDistributionFactory());

        ChiSquareTestImpl test2 = new ChiSquareTestImpl(new ChiSquaredDistributionImpl(2.0));
        assertNotNull(test2);
    }

    @Test
    public void testChiSquareExpectedObservedValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0, 20.0, 30.0 };
        long[] observed = new long[] { 12L, 18L, 33L };
        double result = test.chiSquare(expected, observed);
        assertTrue(result >= 0.0);
    }

    @Test
    public void testChiSquareExpectedObservedLengthMismatch() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0, 20.0 };
        long[] observed = new long[] { 12L };
        try {
            test.chiSquare(expected, observed);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lengths incorrect"));
        }
    }

    @Test
    public void testChiSquareExpectedObservedLengthTooShort() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0 };
        long[] observed = new long[] { 12L };
        try {
            test.chiSquare(expected, observed);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lengths incorrect"));
        }
    }

    @Test
    public void testChiSquareExpectedNotPositive() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0, 0.0 };
        long[] observed = new long[] { 12L, 18L };
        try {
            test.chiSquare(expected, observed);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("non-negative"));
        }
    }

    @Test
    public void testChiSquareObservedNegative() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0, 20.0 };
        long[] observed = new long[] { 12L, -1L };
        try {
            test.chiSquare(expected, observed);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("non-negative"));
        }
    }

    @Test
    public void testChiSquareTestExpectedObserved() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0, 20.0, 30.0 };
        long[] observed = new long[] { 10L, 20L, 30L };
        double pValue = test.chiSquareTest(expected, observed);
        assertTrue(pValue >= 0.0 && pValue <= 1.0);
    }

    @Test
    public void testChiSquareTestWithAlphaValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0, 20.0, 30.0 };
        long[] observed = new long[] { 10L, 20L, 30L };
        boolean reject = test.chiSquareTest(expected, observed, 0.05);
        assertFalse(reject);
    }

    @Test
    public void testChiSquareTestWithAlphaInvalid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        double[] expected = new double[] { 10.0, 20.0, 30.0 };
        long[] observed = new long[] { 10L, 20L, 30L };
        try {
            test.chiSquareTest(expected, observed, 0.6);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("bad significance level"));
        }
        try {
            test.chiSquareTest(expected, observed, 0.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("bad significance level"));
        }
    }

    @Test
    public void testChiSquareTableValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L, 15L },
            { 20L, 25L }
        };
        double stat = test.chiSquare(counts);
        assertTrue(stat >= 0.0);
    }

    @Test
    public void testChiSquareTableInvalidRows() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L, 15L }
        };
        try {
            test.chiSquare(counts);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("at least two rows"));
        }
    }

    @Test
    public void testChiSquareTableInvalidCols() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L },
            { 20L }
        };
        try {
            test.chiSquare(counts);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("at least two columns"));
        }
    }

    @Test
    public void testChiSquareTableNotRectangular() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L, 15L },
            { 20L }
        };
        try {
            test.chiSquare(counts);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("rectangular"));
        }
    }

    @Test
    public void testChiSquareTableNegativeEntries() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L, -15L },
            { 20L, 25L }
        };
        try {
            test.chiSquare(counts);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("non-negative"));
        }
    }

    @Test
    public void testChiSquareTestTableValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L, 15L },
            { 20L, 25L }
        };
        double pValue = test.chiSquareTest(counts);
        assertTrue(pValue >= 0.0 && pValue <= 1.0);
    }

    @Test
    public void testChiSquareTestTableWithAlphaValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L, 15L },
            { 20L, 25L }
        };
        boolean reject = test.chiSquareTest(counts, 0.05);
        assertFalse(reject);
    }

    @Test
    public void testChiSquareTestTableWithAlphaInvalid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[][] counts = new long[][] {
            { 10L, 15L },
            { 20L, 25L }
        };
        try {
            test.chiSquareTest(counts, -0.1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("bad significance level"));
        }
    }

    @Test
    public void testChiSquareDataSetsComparisonValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 10L, 20L, 30L };
        long[] obs2 = new long[] { 12L, 18L, 32L };
        double stat = test.chiSquareDataSetsComparison(obs1, obs2);
        assertTrue(stat >= 0.0);
    }

    @Test
    public void testChiSquareDataSetsComparisonUnequalCounts() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 10L, 20L, 30L };
        long[] obs2 = new long[] { 120L, 180L, 320L };
        double stat = test.chiSquareDataSetsComparison(obs1, obs2);
        assertTrue(stat >= 0.0);
    }

    @Test
    public void testChiSquareDataSetsComparisonLengthMismatch() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 10L, 20L };
        long[] obs2 = new long[] { 12L };
        try {
            test.chiSquareDataSetsComparison(obs1, obs2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lengths incorrect"));
        }
    }

    @Test
    public void testChiSquareDataSetsComparisonNegative() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 10L, -20L };
        long[] obs2 = new long[] { 12L, 18L };
        try {
            test.chiSquareDataSetsComparison(obs1, obs2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("non-negative"));
        }
    }

    @Test
    public void testChiSquareDataSetsComparisonAllZeros() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 0L, 0L };
        long[] obs2 = new long[] { 0L, 0L };
        try {
            test.chiSquareDataSetsComparison(obs1, obs2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot all be 0"));
        }
    }

    @Test
    public void testChiSquareDataSetsComparisonPairZeros() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 0L, 10L };
        long[] obs2 = new long[] { 0L, 20L };
        try {
            test.chiSquareDataSetsComparison(obs1, obs2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not both be zero"));
        }
    }

    @Test
    public void testChiSquareTestDataSetsComparisonValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 10L, 20L, 30L };
        long[] obs2 = new long[] { 12L, 18L, 32L };
        double pValue = test.chiSquareTestDataSetsComparison(obs1, obs2);
        assertTrue(pValue >= 0.0 && pValue <= 1.0);
    }

    @Test
    public void testChiSquareTestDataSetsComparisonAlphaValid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 10L, 20L, 30L };
        long[] obs2 = new long[] { 12L, 18L, 32L };
        boolean reject = test.chiSquareTestDataSetsComparison(obs1, obs2, 0.05);
        assertFalse(reject);
    }

    @Test
    public void testChiSquareTestDataSetsComparisonAlphaInvalid() throws Throwable {
        ChiSquareTestImpl test = new ChiSquareTestImpl();
        long[] obs1 = new long[] { 10L, 20L, 30L };
        long[] obs2 = new long[] { 12L, 18L, 32L };
        try {
            test.chiSquareTestDataSetsComparison(obs1, obs2, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("bad significance level"));
        }
    }
}