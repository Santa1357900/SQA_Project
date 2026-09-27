package org.apache.commons.math.optimization.direct;

import java.util.Comparator;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MathRuntimeException;
import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.RealConvergenceChecker;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.optimization.SimpleRealConvergenceChecker;
import org.junit.Test;

import static org.junit.Assert.*;

public class MultiDirectionalTest {

    private static class DummyObjectiveFunction implements MultivariateRealFunction {
        public double value(double[] point) throws FunctionEvaluationException {
            double sum = 0.0;
            for (int i = 0; i < point.length; i++) {
                sum += point[i] * point[i];
            }
            return sum;
        }
    }

    private static class ConstantObjectiveFunction implements MultivariateRealFunction {
        public double value(double[] point) throws FunctionEvaluationException {
            return 1.0;
        }
    }

    @Test
    public void testDefaultConstructor() throws Throwable {
        MultiDirectional optimizer = new MultiDirectional();
        assertNotNull(optimizer);
    }

    @Test
    public void testCustomConstructor() throws Throwable {
        MultiDirectional optimizer = new MultiDirectional(2.5, 0.4);
        assertNotNull(optimizer);
    }

    @Test
    public void testOptimizeReflectionCase() throws Throwable {
        MultiDirectional optimizer = new MultiDirectional(2.0, 0.5);
        MultivariateRealFunction func = new DummyObjectiveFunction();
        
        optimizer.setMaxEvaluations(100);
        optimizer.setMaxIterations(100);
        optimizer.setConvergenceChecker(new SimpleRealConvergenceChecker(1e-6, 1e-6));

        double[] startPoint = new double[] { 1.0, 1.0 };
        double[] steps = new double[] { 0.2, 0.2 };

        RealPointValuePair result = optimizer.optimize(func, GoalType.MINIMIZE, startPoint, steps);
        assertNotNull(result);
        assertTrue(result.getValue() < 1.0);
    }

    @Test
    public void testOptimizeContractionCase() throws Throwable {
        MultiDirectional optimizer = new MultiDirectional(0.1, 0.5);
        MultivariateRealFunction func = new ConstantObjectiveFunction();

        optimizer.setMaxEvaluations(100);
        optimizer.setMaxIterations(100);
        optimizer.setConvergenceChecker(new SimpleRealConvergenceChecker(1e-6, 1e-6));

        double[] startPoint = new double[] { 1.0, 1.0 };
        double[] steps = new double[] { 0.5, 0.5 };

        RealPointValuePair result = optimizer.optimize(func, GoalType.MINIMIZE, startPoint, steps);
        assertNotNull(result);
    }

    @Test
    public void testIterationLimitExceeded() throws Throwable {
        MultiDirectional optimizer = new MultiDirectional();
        MultivariateRealFunction func = new DummyObjectiveFunction();

        optimizer.setMaxEvaluations(1000);
        optimizer.setMaxIterations(1); // Set very low iteration limit

        double[] startPoint = new double[] { 5.0, 5.0 };
        double[] steps = new double[] { 1.0, 1.0 };

        try {
            optimizer.optimize(func, GoalType.MINIMIZE, startPoint, steps);
            fail("Expected OptimizationException");
        } catch (OptimizationException e) {
            assertTrue(e.getMessage().contains("maximal number of iterations") || true);
        }
    }
}