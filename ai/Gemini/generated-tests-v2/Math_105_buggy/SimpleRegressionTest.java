package org.apache.commons.math.stat.regression;

import junit.framework.TestCase;
import org.apache.commons.math.MathException;

public class SimpleRegressionTest extends TestCase {

    public SimpleRegressionTest(String name) {
        super(name);
    }

    public void testEmptyModel() throws Throwable {
        SimpleRegression regression = new SimpleRegression();
        assertEquals(0L, regression.getN());
        assertTrue(Double.isNaN(regression.getSlope()));
        assertTrue(Double.isNaN(regression.getIntercept()));
        assertTrue(Double.isNaN(regression.getSumSquaredErrors()));
        assertTrue(Double.isNaN(regression.getTotalSumSquares()));
        assertTrue(Double.isNaN(regression.getRegressionSumSquares()));
        assertTrue(Double.isNaN(regression.getMeanSquareError()));
        assertTrue(Double.isNaN(regression.getR()));
        assertTrue(Double.isNaN(regression.getRSquare()));
        assertTrue(Double.isNaN(regression.getInterceptStdErr()));
        assertTrue(Double.isNaN(regression.getSlopeStdErr()));
        assertTrue(Double.isNaN(regression.getSlopeConfidenceInterval()));
        assertTrue(Double.isNaN(regression.getSignificance()));
        assertTrue(Double.isNaN(regression.predict(1.0)));
    }

    public void testAddDataAndBasicStats() throws Throwable {
        SimpleRegression regression = new SimpleRegression();
        regression.addData(1.0, 2.0);
        assertEquals(1L, regression.getN());

        regression.addData(2.0, 4.0);
        assertEquals(2L, regression.getN());
        assertEquals(2.0, regression.getSlope(), 1.0e-10);
        assertEquals(0.0, regression.getIntercept(), 1.0e-10);
        assertEquals(0.0, regression.getSumSquaredErrors(), 1.0e-10);
        assertEquals(2.0, regression.getTotalSumSquares(), 1.0e-10);
        assertEquals(2.0, regression.getRegressionSumSquares(), 1.0e-10);
        assertEquals(1.0, regression.getR(), 1.0e-10);
        assertEquals(1.0, regression.getRSquare(), 1.0e-10);
        assertEquals(3.0, regression.predict(1.5), 1.0e-10);
    }

    public void testThreeObservations() throws Throwable {
        SimpleRegression regression = new SimpleRegression();
        double[][] data = {
            {1.0, 2.0},
            {2.0, 3.0},
            {3.0, 5.0}
        };
        regression.addData(data);
        assertEquals(3L, regression.getN());
        
        // Slope = (3*sum(xy) - sum(x)sum(y)) / (3*sum(x^2) - (sum(x))^2)
        // x: 1, 2, 3 (sum=6, sq=14)
        // y: 2, 3, 5 (sum=10, sq=38)
        // xy: 2+6+15 = 23
        double slope = regression.getSlope();
        double intercept = regression.getIntercept();
        double sse = regression.getSumSquaredErrors();
        double mse = regression.getMeanSquareError();
        double interceptStdErr = regression.getInterceptStdErr();
        double slopeStdErr = regression.getSlopeStdErr();
        double slopeConfInterval = regression.getSlopeConfidenceInterval();
        double significance = regression.getSignificance();

        assertFalse(Double.isNaN(slope));
        assertFalse(Double.isNaN(intercept));
        assertFalse(Double.isNaN(sse));
        assertFalse(Double.isNaN(mse));
        assertFalse(Double.isNaN(interceptStdErr));
        assertFalse(Double.isNaN(slopeStdErr));
        assertFalse(Double.isNaN(slopeConfInterval));
        assertFalse(Double.isNaN(significance));
    }

    public void testNoVariationInX() throws Throwable {
        SimpleRegression regression = new SimpleRegression();
        regression.addData(1.0, 2.0);
        regression.addData(1.0, 3.0);
        regression.addData(1.0, 4.0);

        assertEquals(3L, regression.getN());
        assertTrue(Double.isNaN(regression.getSlope()));
        assertTrue(Double.isNaN(regression.getIntercept()));
        assertTrue(Double.isNaN(regression.getSumSquaredErrors()));
        assertTrue(Double.isNaN(regression.getTotalSumSquares()));
        assertTrue(Double.isNaN(regression.getRegressionSumSquares()));
        assertTrue(Double.isNaN(regression.getMeanSquareError()));
        assertTrue(Double.isNaN(regression.getR()));
        assertTrue(Double.isNaN(regression.getRSquare()));
        assertTrue(Double.isNaN(regression.getInterceptStdErr()));
        assertTrue(Double.isNaN(regression.getSlopeStdErr()));
        assertTrue(Double.isNaN(regression.getSlopeConfidenceInterval()));
        assertTrue(Double.isNaN(regression.getSignificance()));
    }

    public void testClear() throws Throwable {
        SimpleRegression regression = new SimpleRegression();
        regression.addData(1.0, 2.0);
        regression.addData(2.0, 3.0);
        assertEquals(2L, regression.getN());

        regression.clear();
        assertEquals(0L, regression.getN());
        assertTrue(Double.isNaN(regression.getSlope()));
    }

    public void testNegativeSlopeAndR() throws Throwable {
        SimpleRegression regression = new SimpleRegression();
        regression.addData(1.0, 5.0);
        regression.addData(2.0, 3.0);
        regression.addData(3.0, 1.0);

        assertEquals(-2.0, regression.getSlope(), 1.0e-10);
        assertEquals(-1.0, regression.getR(), 1.0e-10);
    }

    public void testConfidenceIntervalExceptions() throws Throwable {
        SimpleRegression regression = new SimpleRegression();
        regression.addData(1.0, 2.0);
        regression.addData(2.0, 3.0);
        regression.addData(3.0, 4.0);

        boolean caught = false;
        try {
            regression.getSlopeConfidenceInterval(1.5);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            regression.getSlopeConfidenceInterval(0.0);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }
}