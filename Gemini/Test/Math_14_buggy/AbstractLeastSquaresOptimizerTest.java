package org.apache.commons.math3.optim.nonlinear.vector.jacobian;

import org.junit.Test;
import org.junit.Assert;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.PointVectorValuePair;
import org.apache.commons.math3.optim.nonlinear.vector.Weight;

public class AbstractLeastSquaresOptimizerTest {

    private static class ConcreteLeastSquaresOptimizer extends AbstractLeastSquaresOptimizer {
        public ConcreteLeastSquaresOptimizer(ConvergenceChecker<PointVectorValuePair> checker) {
            super(checker);
        }

        @Override
        public double[][] computeJacobian(double[] params) {
            // Return a simple 2x2 identity matrix for testing
            return new double[][] {
                { 1.0, 0.0 },
                { 0.0, 1.0 }
            };
        }

        public void exposeParseOptimizationData(org.apache.commons.math3.optim.OptimizationData... optData) {
            // Using reflection or package-private access if available, but since parseOptimizationData is private,
            // we can call optimize() which invokes parseOptimizationData.
            optimize(optData);
        }
    }

    @Test
    public void testCostAndChiSquareMethods() throws Throwable {
        ConcreteLeastSquaresOptimizer optimizer = new ConcreteLeastSquaresOptimizer(null);
        
        // Test setCost and getChiSquare / getCost via indirect calculations
        optimizer.setCost(4.0);
        Assert.assertEquals(16.0, optimizer.getChiSquare(), 1e-12);
    }

    @Test
    public void testComputeResiduals() throws Throwable {
        ConcreteLeastSquaresOptimizer optimizer = new ConcreteLeastSquaresOptimizer(null);
        
        // We need target set. Let's pass OptimizationData to optimize or mock target via Weight if needed,
        // but computeResiduals uses getTarget(). Since getTarget comes from base, let's test DimensionMismatchException first.
        double[] objectiveValue = new double[2];
        try {
            optimizer.computeResiduals(objectiveValue);
            Assert.fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            // Expected
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testGetWeightSquareRootAndOptimizationDataParsing() throws Throwable {
        ConcreteLeastSquaresOptimizer optimizer = new ConcreteLeastSquaresOptimizer(null);
        
        double[][] weightData = new double[][] {
            { 4.0, 0.0 },
            { 0.0, 9.0 }
        };
        Weight weight = new Weight(new Array2DRowRealMatrix(weightData));
        
        optimizer.optimize(weight);
        
        RealMatrix weightSqrt = optimizer.getWeightSquareRoot();
        Assert.assertNotNull(weightSqrt);
        Assert.assertEquals(2, weightSqrt.getRowDimension());
        Assert.assertEquals(2, weightSqrt.getColumnDimension());
        // Square root of diagonal 4 and 9 should be 2 and 3
        Assert.assertEquals(2.0, weightSqrt.getEntry(0, 0), 1e-12);
        Assert.assertEquals(3.0, weightSqrt.getEntry(1, 1), 1e-12);
    }
}