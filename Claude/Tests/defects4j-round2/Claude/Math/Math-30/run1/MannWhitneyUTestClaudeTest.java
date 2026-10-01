package org.apache.commons.math3.stat.inference;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.TiesStrategy;

public class MannWhitneyUTestClaudeTest {

    private MannWhitneyUTest test;

    @Before
    public void setUp() throws Throwable {
        test = new MannWhitneyUTest();
    }

    // default constructor produces a usable instance: simple no-tie case x all less than y
    @Test
    public void testDefaultConstructor_createsUsableInstance() throws Throwable {
        double[] x = {1, 2};
        double[] y = {3, 4};
        double u = test.mannWhitneyU(x, y);
        assertEquals(4.0, u, 1e-9);
    }

    // parameterized constructor with same strategies as default behaves equivalently
    @Test
    public void testParameterizedConstructor_createsUsableInstance() throws Throwable {
        MannWhitneyUTest custom = new MannWhitneyUTest(NaNStrategy.FIXED, TiesStrategy.AVERAGE);
        double[] x = {1, 2};
        double[] y = {3, 4};
        double u = custom.mannWhitneyU(x, y);
        assertEquals(4.0, u, 1e-9);
    }

    // ensureDataConformance: x == null branch -> NullArgumentException
    @Test
    public void testMannWhitneyU_xNull_throwsNullArgumentException() throws Throwable {
        try {
            test.mannWhitneyU((double[]) null, new double[] {1.0});
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // ensureDataConformance: y == null branch -> NullArgumentException
    @Test
    public void testMannWhitneyU_yNull_throwsNullArgumentException() throws Throwable {
        try {
            test.mannWhitneyU(new double[] {1.0}, (double[]) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // ensureDataConformance: both x and y null -> NullArgumentException
    @Test
    public void testMannWhitneyU_bothNull_throwsNullArgumentException() throws Throwable {
        try {
            test.mannWhitneyU((double[]) null, (double[]) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // ensureDataConformance: x.length == 0 branch -> NoDataException
    @Test
    public void testMannWhitneyU_xEmpty_throwsNoDataException() throws Throwable {
        try {
            test.mannWhitneyU(new double[0], new double[] {1.0});
            fail("expected NoDataException");
        } catch (NoDataException expected) {
        }
    }

    // ensureDataConformance: y.length == 0 branch -> NoDataException
    @Test
    public void testMannWhitneyU_yEmpty_throwsNoDataException() throws Throwable {
        try {
            test.mannWhitneyU(new double[] {1.0}, new double[0]);
            fail("expected NoDataException");
        } catch (NoDataException expected) {
        }
    }

    // ensureDataConformance: both empty -> NoDataException
    @Test
    public void testMannWhitneyU_bothEmpty_throwsNoDataException() throws Throwable {
        try {
            test.mannWhitneyU(new double[0], new double[0]);
            fail("expected NoDataException");
        } catch (NoDataException expected) {
        }
    }

    // no ties, all x < all y: U1=0,U2=n1*n2 -> max = n1*n2
    @Test
    public void testMannWhitneyU_xAllLessThanY_noTies_returnsNxN() throws Throwable {
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {4.0, 5.0, 6.0};
        double u = test.mannWhitneyU(x, y);
        assertEquals(9.0, u, 1e-9);
    }

    // no ties, all x > all y: U1=n1*n2,U2=0 -> max = n1*n2 (opposite branch of max)
    @Test
    public void testMannWhitneyU_xAllGreaterThanY_noTies_returnsNxN() throws Throwable {
        double[] x = {4.0, 5.0, 6.0};
        double[] y = {1.0, 2.0, 3.0};
        double u = test.mannWhitneyU(x, y);
        assertEquals(9.0, u, 1e-9);
    }

    // identical tied samples: ties -> average ranking, U1=U2=n1*n2/2
    @Test
    public void testMannWhitneyU_identicalSamplesWithTies_returnsHalfProduct() throws Throwable {
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {1.0, 2.0, 3.0};
        double u = test.mannWhitneyU(x, y);
        assertEquals(4.5, u, 1e-9);
    }

    // single-element arrays, loop runs exactly once in sumRankX accumulation
    @Test
    public void testMannWhitneyU_singleElementEachXLessY_computesCorrectly() throws Throwable {
        double[] x = {1.0};
        double[] y = {2.0};
        double u = test.mannWhitneyU(x, y);
        assertEquals(1.0, u, 1e-9);
    }

    // different sample lengths (n1 != n2), no ties
    @Test
    public void testMannWhitneyU_differentLengths_computesCorrectly() throws Throwable {
        double[] x = {5.0, 6.0, 7.0};
        double[] y = {1.0, 2.0, 3.0, 4.0};
        double u = test.mannWhitneyU(x, y);
        assertEquals(12.0, u, 1e-9);
    }

    // max(U1,U2) must be symmetric regardless of which sample is passed first
    @Test
    public void testMannWhitneyU_argumentOrderSwapped_sameResult() throws Throwable {
        double[] x = {5.0, 6.0, 7.0};
        double[] y = {1.0, 2.0, 3.0, 4.0};
        double u1 = test.mannWhitneyU(x, y);
        double u2 = test.mannWhitneyU(y, x);
        assertEquals(12.0, u1, 1e-9);
        assertEquals(u1, u2, 1e-9);
    }

    // large n1*n2 exceeding Integer.MAX_VALUE must not overflow: U1+U2 == n1*n2 per contract
    @Test
    public void testMannWhitneyU_largeArrays_noIntegerOverflow() throws Throwable {
        int n = 50000;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = i;
            y[i] = n + i;
        }
        double u = test.mannWhitneyU(x, y);
        assertEquals(2500000000.0, u, 1.0);
    }

    // multiple ties within both samples (all values identical) -> U1=U2=n1*n2/2
    @Test
    public void testMannWhitneyU_multipleTiesAcrossSamples_computesCorrectly() throws Throwable {
        double[] x = {2.0, 2.0, 2.0};
        double[] y = {2.0, 2.0, 2.0};
        double u = test.mannWhitneyU(x, y);
        assertEquals(4.5, u, 1e-9);
    }

    // mannWhitneyUTest: x null -> NullArgumentException (propagated from ensureDataConformance)
    @Test
    public void testMannWhitneyUTest_xNull_throwsNullArgumentException() throws Throwable {
        try {
            test.mannWhitneyUTest((double[]) null, new double[] {1.0});
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // mannWhitneyUTest: y null -> NullArgumentException
    @Test
    public void testMannWhitneyUTest_yNull_throwsNullArgumentException() throws Throwable {
        try {
            test.mannWhitneyUTest(new double[] {1.0}, (double[]) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // mannWhitneyUTest: x empty -> NoDataException
    @Test
    public void testMannWhitneyUTest_xEmpty_throwsNoDataException() throws Throwable {
        try {
            test.mannWhitneyUTest(new double[0], new double[] {1.0});
            fail("expected NoDataException");
        } catch (NoDataException expected) {
        }
    }

    // mannWhitneyUTest: y empty -> NoDataException
    @Test
    public void testMannWhitneyUTest_yEmpty_throwsNoDataException() throws Throwable {
        try {
            test.mannWhitneyUTest(new double[] {1.0}, new double[0]);
            fail("expected NoDataException");
        } catch (NoDataException expected) {
        }
    }

    // identical tied samples: Umin == EU exactly -> z = 0 -> p-value = 2*Phi(0) = 1.0 exactly
    @Test
    public void testMannWhitneyUTest_identicalTiedSamples_pValueEqualsOne() throws Throwable {
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {1.0, 2.0, 3.0};
        double p = test.mannWhitneyUTest(x, y);
        assertEquals(1.0, p, 1e-9);
    }

    // fully separated no-tie samples: known z = -1.963961..., p-value ~ 0.049526
    @Test
    public void testMannWhitneyUTest_separatedSamples_returnsExpectedPValue() throws Throwable {
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {4.0, 5.0, 6.0};
        double p = test.mannWhitneyUTest(x, y);
        assertEquals(0.049526, p, 1e-3);
    }

    // two-sided p-value must be invariant to swapping which sample is passed first
    @Test
    public void testMannWhitneyUTest_argumentOrderSwapped_samePValue() throws Throwable {
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {4.0, 5.0, 6.0};
        double p1 = test.mannWhitneyUTest(x, y);
        double p2 = test.mannWhitneyUTest(y, x);
        assertEquals(p1, p2, 1e-9);
    }

    // single-element samples: z = -1.0 exactly, p-value = 2*(1-Phi(1)) = 0.3173105...
    @Test
    public void testMannWhitneyUTest_singleElementEach_returnsExpectedPValue() throws Throwable {
        double[] x = {1.0};
        double[] y = {2.0};
        double p = test.mannWhitneyUTest(x, y);
        assertEquals(0.3173105, p, 1e-4);
    }

    // sanity bound: any valid asymptotic p-value must lie within (0, 1]
    @Test
    public void testMannWhitneyUTest_pValueWithinValidRange() throws Throwable {
        double[] x = {10.0, 20.0, 30.0, 40.0, 50.0};
        double[] y = {15.0, 25.0, 35.0};
        double p = test.mannWhitneyUTest(x, y);
        assertTrue(p > 0.0 && p <= 1.0);
    }
}
