package org.apache.commons.math3.distribution;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.NonPositiveDefiniteMatrixException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MultivariateNormalDistributionTest {

    @Test
    public void testValidConstructionAndGetters() throws Throwable {
        double[] means = new double[]{1.0, 2.0};
        double[][] covariances = new double[][]{
            {1.0, 0.0},
            {0.0, 1.0}
        };

        MultivariateNormalDistribution dist = new MultivariateNormalDistribution(means, covariances);

        assertEquals(2, dist.getDimension());
        
        double[] retrievedMeans = dist.getMeans();
        assertEquals(2, retrievedMeans.length);
        assertEquals(1.0, retrievedMeans[0], 1e-12);
        assertEquals(2.0, retrievedMeans[1], 1e-12);

        RealMatrix retrievedCov = dist.getCovariances();
        assertEquals(2, retrievedCov.getRowDimension());
        assertEquals(2, retrievedCov.getColumnDimension());
        assertEquals(1.0, retrievedCov.getEntry(0, 0), 1e-12);

        double[] stdDevs = dist.getStandardDeviations();
        assertEquals(2, stdDevs.length);
        assertEquals(1.0, stdDevs[0], 1e-12);
        assertEquals(1.0, stdDevs[1], 1e-12);
    }

    @Test
    public void testConstructionWithRng() throws Throwable {
        Well19937c rng = new Well19937c(123L);
        double[] means = new double[]{0.0};
        double[][] covariances = new double[][]{{1.0}};

        MultivariateNormalDistribution dist = new MultivariateNormalDistribution(rng, means, covariances);
        assertEquals(1, dist.getDimension());
        assertNotNull(dist.sample());
    }

    @Test
    public void testDimensionMismatchMeansCovariances() throws Throwable {
        double[] means = new double[]{1.0, 2.0};
        double[][] covariances = new double[][]{
            {1.0}
        };

        try {
            new MultivariateNormalDistribution(means, covariances);
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testDimensionMismatchRowLength() throws Throwable {
        double[] means = new double[]{1.0, 2.0};
        double[][] covariances = new double[][]{
            {1.0, 0.0},
            {0.0}
        };

        try {
            new MultivariateNormalDistribution(means, covariances);
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSingularMatrixException() throws Throwable {
        double[] means = new double[]{0.0, 0.0};
        double[][] covariances = new double[][]{
            {1.0, 1.0},
            {1.0, 1.0}
        };

        try {
            new MultivariateNormalDistribution(means, covariances);
            fail("Expected SingularMatrixException");
        } catch (SingularMatrixException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testNonPositiveDefiniteMatrixException() throws Throwable {
        double[] means = new double[]{0.0, 0.0};
        double[][] covariances = new double[][]{
            {-1.0, 0.0},
            {0.0, 1.0}
        };

        try {
            new MultivariateNormalDistribution(means, covariances);
            fail("Expected NonPositiveDefiniteMatrixException");
        } catch (NonPositiveDefiniteMatrixException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testDensityCalculation() throws Throwable {
        double[] means = new double[]{0.0, 0.0};
        double[][] covariances = new double[][]{
            {1.0, 0.0},
            {0.0, 1.0}
        };

        MultivariateNormalDistribution dist = new MultivariateNormalDistribution(means, covariances);
        double density = dist.density(new double[]{0.0, 0.0});
        assertTrue(density > 0.0);
        
        double expectedApprox = 1.0 / (2.0 * Math.PI);
        assertEquals(expectedApprox, density, 1e-6);
    }

    @Test
    public void testDensityDimensionMismatch() throws Throwable {
        double[] means = new double[]{0.0, 0.0};
        double[][] covariances = new double[][]{
            {1.0, 0.0},
            {0.0, 1.0}
        };

        MultivariateNormalDistribution dist = new MultivariateNormalDistribution(means, covariances);
        try {
            dist.density(new double[]{0.0});
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSampling() throws Throwable {
        double[] means = new double[]{10.0, 20.0};
        double[][] covariances = new double[][]{
            {1.0, 0.1},
            {0.1, 1.0}
        };

        MultivariateNormalDistribution dist = new MultivariateNormalDistribution(means, covariances);
        double[] sample1 = dist.sample();
        double[] sample2 = dist.sample();

        assertEquals(2, sample1.length);
        assertEquals(2, sample2.length);
    }
}