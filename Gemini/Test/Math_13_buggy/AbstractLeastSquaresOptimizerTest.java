package org.apache.commons.math3.optimization.general;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.differentiation.MultivariateDifferentiableVectorFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.optimization.InitialGuess;
import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.Target;
import org.apache.commons.math3.optimization.Weight;
import org.junit.Assert;
import org.junit.Test;

public class AbstractLeastSquaresOptimizerTest {

    private static class ConcreteOptimizer extends AbstractLeastSquaresOptimizer {
        public ConcreteOptimizer() {
            super();
        }

        public ConcreteOptimizer(ConvergenceChecker<PointVectorValuePair> checker) {
            super(checker);
        }

        public void callSetUp() {
            setUp();
        }
    }

    private static class DummyMultivariateDifferentiableVectorFunction implements MultivariateDifferentiableVectorFunction {
        private final int valueLength;

        public DummyMultivariateDifferentiableVectorFunction(int valueLength) {
            this.valueLength = valueLength;
        }

        public double[] value(double[] point) {
            double[] res = new double[valueLength];
            for (int i = 0; i < valueLength; i++) {
                res[i] = point[0];
            }
            return res;
        }

        public DerivativeStructure[] value(DerivativeStructure[] point) {
            DerivativeStructure[] res = new DerivativeStructure[valueLength];
            for (int i = 0; i < valueLength; i++) {
                res[i] = point[0].multiply(1.0);
            }
            return res;
        }
    }

    @Test
    public void testGettersAndBasicState() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer();
        Assert.assertEquals(0, optimizer.getJacobianEvaluations());
        
        optimizer.setCost(5.0);
        Assert.assertEquals(25.0, optimizer.getChiSquare(), 1e-12);
    }

    @Test
    public void testComputeCostAndResiduals() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer();
        
        double[] target = new double[] { 1.0, 2.0, 3.0 };
        double[] weights = new double[] { 1.0, 1.0, 1.0 };
        double[] startPoint = new double[] { 0.0 };

        optimizer.optimize(100, 
                new DummyMultivariateDifferentiableVectorFunction(3), 
                target, weights, startPoint);

        double[] objectiveValue = new double[] { 1.5, 1.5, 3.5 };
        double[] residuals = optimizer.computeResiduals(objectiveValue);
        Assert.assertEquals(3, residuals.length);
        Assert.assertEquals(-0.5, residuals[0], 1e-12);
        Assert.assertEquals(0.5, residuals[1], 1e-12);
        Assert.assertEquals(-0.5, residuals[2], 1e-12);

        double cost = optimizer.computeCost(residuals);
        Assert.assertTrue(cost >= 0.0);
    }

    @Test
    public void testComputeResidualsDimensionMismatch() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer();
        
        double[] target = new double[] { 1.0, 2.0 };
        double[] weights = new double[] { 1.0, 1.0 };
        double[] startPoint = new double[] { 0.0 };

        optimizer.optimize(100, 
                new DummyMultivariateDifferentiableVectorFunction(2), 
                target, weights, startPoint);

        try {
            optimizer.computeResiduals(new double[] { 1.0 });
            Assert.fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testComputeWeightedJacobianAndCovariances() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer();
        
        double[] target = new double[] { 1.0, 2.0 };
        double[] weights = new double[] { 1.0, 1.0 };
        double[] startPoint = new double[] { 1.0 };

        optimizer.optimize(100, 
                new DummyMultivariateDifferentiableVectorFunction(2), 
                target, weights, startPoint);

        double[] params = new double[] { 1.0 };
        RealMatrix jacobian = optimizer.computeWeightedJacobian(params);
        Assert.assertNotNull(jacobian);
        Assert.assertEquals(2, jacobian.getRowDimension());
        Assert.assertEquals(1, jacobian.getColumnDimension());

        double[][] covariances = optimizer.computeCovariances(params, 1e-14);
        Assert.assertNotNull(covariances);

        double[] sigma = optimizer.computeSigma(params, 1e-14);
        Assert.assertNotNull(sigma);
        Assert.assertEquals(1, sigma.length);
    }

    @Test
    public void testGuessParametersErrorsNoDegreesOfFreedom() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer();
        
        double[] target = new double[] { 1.0 };
        double[] weights = new double[] { 1.0 };
        double[] startPoint = new double[] { 1.0, 2.0 };

        optimizer.optimize(100, 
                new DummyMultivariateDifferentiableVectorFunction(1), 
                target, weights, startPoint);

        try {
            optimizer.guessParametersErrors();
            Assert.fail("Expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testGetWeightSquareRoot() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer();
        
        double[] target = new double[] { 1.0, 1.0 };
        double[] weights = new double[] { 4.0, 9.0 };
        double[] startPoint = new double[] { 0.0 };

        optimizer.optimize(100, 
                new DummyMultivariateDifferentiableVectorFunction(2), 
                target, weights, startPoint);

        RealMatrix sqrtWeight = optimizer.getWeightSquareRoot();
        Assert.assertNotNull(sqrtWeight);
        Assert.assertEquals(2, sqrtWeight.getRowDimension());
        Assert.assertEquals(2, sqrtWeight.getColumnDimension());
        Assert.assertEquals(2.0, sqrtWeight.getEntry(0, 0), 1e-12);
        Assert.assertEquals(3.0, sqrtWeight.getEntry(1, 1), 1e-12);
    }
}