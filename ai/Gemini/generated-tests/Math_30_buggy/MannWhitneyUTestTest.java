package org.apache.commons.math3.stat.inference;

import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.TiesStrategy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class MannWhitneyUTestTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        assertNotNull(test);
    }

    @Test
    public void testParameterizedConstructor() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest(NaNStrategy.FIXED, TiesStrategy.AVERAGE);
        assertNotNull(test);
    }

    @Test
    public void testMannWhitneyUNullX() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] y = new double[] {1.0, 2.0};
        try {
            test.mannWhitneyU(null, y);
            fail("Expected NullArgumentException");
        } catch (NullArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testMannWhitneyUNullY() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] x = new double[] {1.0, 2.0};
        try {
            test.mannWhitneyU(x, null);
            fail("Expected NullArgumentException");
        } catch (NullArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testMannWhitneyUEmptyX() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] x = new double[] {};
        double[] y = new double[] {1.0, 2.0};
        try {
            test.mannWhitneyU(x, y);
            fail("Expected NoDataException");
        } catch (NoDataException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testMannWhitneyUEmptyY() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] x = new double[] {1.0, 2.0};
        double[] y = new double[] {};
        try {
            test.mannWhitneyU(x, y);
            fail("Expected NoDataException");
        } catch (NoDataException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testMannWhitneyUValid() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] x = new double[] {1.0, 2.0, 3.0};
        double[] y = new double[] {4.0, 5.0, 6.0};
        double u = test.mannWhitneyU(x, y);
        assertEquals(9.0, u, 1e-6);
    }

    @Test
    public void testMannWhitneyUTestValid() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] x = new double[] {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] y = new double[] {6.0, 7.0, 8.0, 9.0, 10.0};
        double pValue = test.mannWhitneyUTest(x, y);
        assertEquals(true, pValue >= 0.0 && pValue <= 1.0);
    }

    @Test
    public void testMannWhitneyUTestNullX() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] y = new double[] {1.0, 2.0};
        try {
            test.mannWhitneyUTest(null, y);
            fail("Expected NullArgumentException");
        } catch (NullArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testMannWhitneyUTestEmptyX() throws Throwable {
        MannWhitneyUTest test = new MannWhitneyUTest();
        double[] x = new double[] {};
        double[] y = new double[] {1.0, 2.0};
        try {
            test.mannWhitneyUTest(x, y);
            fail("Expected NoDataException");
        } catch (NoDataException e) {
            assertNotNull(e);
        }
    }
}