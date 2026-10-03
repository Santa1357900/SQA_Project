package org.apache.commons.math.optimization.general;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.VectorialPointValuePair;
import org.apache.commons.math.optimization.DifferentiableMultivariateVectorialFunction;
import org.apache.commons.math.optimization.VectorialConvergenceChecker;

public class LevenbergMarquardtOptimizerTest {

    @Test
    public void testDefaultConstructorAndSetters() throws Throwable {
        LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();
        optimizer.setInitialStepBoundFactor(50.0);
        optimizer.setCostRelativeTolerance(1.0e-8);
        optimizer.setParRelativeTolerance(1.0e-8);
        optimizer.setOrthoTolerance(1.0e-8);
        optimizer.setQRRankingThreshold(1.0e-12);
        
        assertEquals(1000, optimizer.getMaxIterations());
    }

    @Test
    public void testSimpleOptimization() throws Throwable {
        LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();

        DifferentiableMultivariateVectorialFunction f = new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] point) {
                double[] res = new double[1];
                res[0] = point[0] - 2.0;
                return res;
            }
            public org.apache.commons.math.optimization.MultivariateMatrixFunction jacobian() {
                return new org.apache.commons.math.optimization.MultivariateMatrixFunction() {
                    public double[][] value(double[] point) {
                        double[][] jac = new double[1][1];
                        jac[0][0] = 1.0;
                        return jac;
                    }
                };
            }
        };

        double[] startPoint = new double[] { 0.0 };
        double[] target = new double[] { 0.0 };

        VectorialPointValuePair result = optimizer.optimize(f, target, new double[] { 1.0 }, startPoint);
        assertNotNull(result);
        assertEquals(2.0, result.getPoint()[0], 1.0e-5);
    }

    @Test
    public void testOptimizationWithConvergenceChecker() throws Throwable {
        LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();
        optimizer.setConvergenceChecker(new VectorialConvergenceChecker() {
            public boolean converged(int iteration, VectorialPointValuePair previous, VectorialPointValuePair current) {
                return iteration >= 2;
            }
        });

        DifferentiableMultivariateVectorialFunction f = new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] point) {
                double[] res = new double[1];
                res[0] = point[0] - 5.0;
                return res;
            }
            public org.apache.commons.math.optimization.MultivariateMatrixFunction jacobian() {
                return new org.apache.commons.math.optimization.MultivariateMatrixFunction() {
                    public double[][] value(double[] point) {
                        double[][] jac = new double[1][1];
                        jac[0][0] = 1.0;
                        return jac;
                    }
                };
            }
        };

        double[] startPoint = new double[] { 0.0 };
        double[] target = new double[] { 0.0 };

        VectorialPointValuePair result = optimizer.optimize(f, target, new double[] { 1.0 }, startPoint);
        assertNotNull(result);
    }

    @Test
    public void testQRDecompositionRankDeficient() throws Throwable {
        LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();

        DifferentiableMultivariateVectorialFunction f = new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] point) {
                double[] res = new double[2];
                res[0] = point[0];
                res[1] = point[0];
                return res;
            }
            public org.apache.commons.math.optimization.MultivariateMatrixFunction jacobian() {
                return new org.apache.commons.math.optimization.MultivariateMatrixFunction() {
                    public double[][] value(double[] point) {
                        double[][] jac = new double[2][1];
                        jac[0][0] = 1.0;
                        jac[1][0] = 1.0;
                        return jac;
                    }
                };
            }
        };

        double[] startPoint = new double[] { 1.0 };
        double[] target = new double[] { 0.0, 0.0 };

        VectorialPointValuePair result = optimizer.optimize(f, target, new double[] { 1.0, 1.0 }, startPoint);
        assertNotNull(result);
    }

    @Test
    public void testUnableToPerformQRDecompositionException() throws Throwable {
        LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();

        DifferentiableMultivariateVectorialFunction f = new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] point) {
                double[] res = new double[1];
                res[0] = Double.NaN;
                return res;
            }
            public org.apache.commons.math.optimization.MultivariateMatrixFunction jacobian() {
                return new org.apache.commons.math.optimization.MultivariateMatrixFunction() {
                    public double[][] value(double[] point) {
                        double[][] jac = new double[1][1];
                        jac[0][0] = Double.NaN;
                        return jac;
                    }
                };
            }
        };

        double[] startPoint = new double[] { 0.0 };
        double[] target = new double[] { 0.0 };

        try {
            optimizer.optimize(f, target, new double[] { 1.0 }, startPoint);
            fail("Should have thrown OptimizationException");
        } catch (OptimizationException e) {
            assertNotNull(e);
        }
    }
}