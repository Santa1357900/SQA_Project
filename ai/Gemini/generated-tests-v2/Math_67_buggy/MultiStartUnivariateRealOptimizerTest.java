package org.apache.commons.math.optimization;

import org.apache.commons.math.ConvergenceException;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.random.RandomGenerator;
import org.junit.Test;

import static org.junit.Assert.*;

public class MultiStartUnivariateRealOptimizerTest {

    private static class DummyOptimizer implements UnivariateRealOptimizer {
        private double result;
        private double functionValue;
        private int iterationCount;
        private int evaluations;
        private double absoluteAccuracy;
        private double relativeAccuracy;
        private int maxIter;
        private int maxEval;
        private boolean throwConvergence;
        private boolean throwEvaluation;
        private double fixedReturn;

        public DummyOptimizer(double fixedReturn, boolean throwConvergence, boolean throwEvaluation) {
            this.fixedReturn = fixedReturn;
            this.throwConvergence = throwConvergence;
            this.throwEvaluation = throwEvaluation;
        }

        public double getFunctionValue() {
            return functionValue;
        }

        public double getResult() {
            return result;
        }

        public double getAbsoluteAccuracy() {
            return absoluteAccuracy;
        }

        public int getIterationCount() {
            return iterationCount;
        }

        public int getMaximalIterationCount() {
            return maxIter;
        }

        public int getMaxEvaluations() {
            return maxEval;
        }

        public int getEvaluations() {
            return evaluations;
        }

        public double getRelativeAccuracy() {
            return relativeAccuracy;
        }

        public void resetAbsoluteAccuracy() {
            absoluteAccuracy = 0.0;
        }

        public void resetMaximalIterationCount() {
            maxIter = Integer.MAX_VALUE;
        }

        public void resetRelativeAccuracy() {
            relativeAccuracy = 0.0;
        }

        public void setAbsoluteAccuracy(double accuracy) {
            absoluteAccuracy = accuracy;
        }

        public void setMaximalIterationCount(int count) {
            maxIter = count;
        }

        public void setMaxEvaluations(int maxEvaluations) {
            maxEval = maxEvaluations;
        }

        public void setRelativeAccuracy(double accuracy) {
            relativeAccuracy = accuracy;
        }

        public double optimize(UnivariateRealFunction f, GoalType goalType, double min, double max)
                throws ConvergenceException, FunctionEvaluationException {
            if (throwConvergence) {
                throw new ConvergenceException();
            }
            if (throwEvaluation) {
                throw new FunctionEvaluationException(min);
            }
            result = fixedReturn;
            functionValue = f.value(result);
            iterationCount = 5;
            evaluations = 10;
            return result;
        }

        public double optimize(UnivariateRealFunction f, GoalType goalType, double min, double max, double startValue)
                throws ConvergenceException, FunctionEvaluationException {
            return optimize(f, goalType, min, max);
        }
    }

    private static class DummyRandomGenerator implements RandomGenerator {
        private double nextValue;

        public DummyRandomGenerator(double nextValue) {
            this.nextValue = nextValue;
        }

        public void setSeed(long seed) {}
        public void setSeed(int[] seed) {}
        public void setSeed(int seed) {}
        public void nextBytes(byte[] bytes) {}
        public int nextInt() { return 0; }
        public int nextInt(int n) { return 0; }
        public long nextLong() { return 0L; }
        public boolean nextBoolean() { return false; }
        public float nextFloat() { return 0f; }
        public double nextDouble() { return nextValue; }
        public double nextGaussian() { return 0.0; }
    }

    @Test(expected = IllegalStateException.class)
    public void testGetOptimaBeforeOptimize() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(1.0, false, false);
        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 3, new DummyRandomGenerator(0.5));
        optimizer.getOptima();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetOptimaValuesBeforeOptimize() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(1.0, false, false);
        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 3, new DummyRandomGenerator(0.5));
        optimizer.getOptimaValues();
    }

    @Test
    public void testDelegatedGettersAndSetters() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(1.0, false, false);
        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 3, new DummyRandomGenerator(0.5));

        optimizer.setAbsoluteAccuracy(0.01);
        assertEquals(0.01, optimizer.getAbsoluteAccuracy(), 1e-12);
        optimizer.resetAbsoluteAccuracy();

        optimizer.setRelativeAccuracy(0.02);
        assertEquals(0.02, optimizer.getRelativeAccuracy(), 1e-12);
        optimizer.resetRelativeAccuracy();

        optimizer.setMaximalIterationCount(100);
        assertEquals(100, optimizer.getMaximalIterationCount());
        optimizer.resetMaximalIterationCount();

        optimizer.setMaxEvaluations(200);
        assertEquals(200, optimizer.getMaxEvaluations());
    }

    @Test
    public void testMinimizeSorting() throws Throwable {
        final double[] values = new double[] { 3.0, 1.0, 2.0 };
        final int[] index = new int[] { 0 };
        
        UnivariateRealOptimizer underlying = new UnivariateRealOptimizer() {
            private int call = 0;
            public double getFunctionValue() { return values[call - 1]; }
            public double getResult() { return values[call - 1]; }
            public double getAbsoluteAccuracy() { return 0; }
            public int getIterationCount() { return 1; }
            public int getMaximalIterationCount() { return 10; }
            public int getMaxEvaluations() { return 10; }
            public int getEvaluations() { return 1; }
            public double getRelativeAccuracy() { return 0; }
            public void resetAbsoluteAccuracy() {}
            public void resetMaximalIterationCount() {}
            public void resetRelativeAccuracy() {}
            public void setAbsoluteAccuracy(double accuracy) {}
            public void setMaximalIterationCount(int count) {}
            public void setMaxEvaluations(int maxEvaluations) {}
            public void setRelativeAccuracy(double accuracy) {}
            public double optimize(UnivariateRealFunction f, GoalType goalType, double min, double max) {
                double r = values[call];
                call++;
                return r;
            }
            public double optimize(UnivariateRealFunction f, GoalType goalType, double min, double max, double startValue) {
                return optimize(f, goalType, min, max);
            }
        };

        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 3, new DummyRandomGenerator(0.5));
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x; }
        };

        double best = optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 10.0);
        assertEquals(1.0, best, 1e-12);

        double[] optima = optimizer.getOptima();
        double[] optimaVals = optimizer.getOptimaValues();
        assertEquals(3, optima.length);
        assertEquals(1.0, optimaVals[0], 1e-12);
        assertEquals(2.0, optimaVals[1], 1e-12);
        assertEquals(3.0, optimaVals[2], 1e-12);
    }

    @Test
    public void testMaximizeSorting() throws Throwable {
        final double[] values = new double[] { 1.0, 3.0, 2.0 };
        
        UnivariateRealOptimizer underlying = new UnivariateRealOptimizer() {
            private int call = 0;
            public double getFunctionValue() { return values[call - 1]; }
            public double getResult() { return values[call - 1]; }
            public double getAbsoluteAccuracy() { return 0; }
            public int getIterationCount() { return 1; }
            public int getMaximalIterationCount() { return 10; }
            public int getMaxEvaluations() { return 10; }
            public int getEvaluations() { return 1; }
            public double getRelativeAccuracy() { return 0; }
            public void resetAbsoluteAccuracy() {}
            public void resetMaximalIterationCount() {}
            public void resetRelativeAccuracy() {}
            public void setAbsoluteAccuracy(double accuracy) {}
            public void setMaximalIterationCount(int count) {}
            public void setMaxEvaluations(int maxEvaluations) {}
            public void setRelativeAccuracy(double accuracy) {}
            public double optimize(UnivariateRealFunction f, GoalType goalType, double min, double max) {
                double r = values[call];
                call++;
                return r;
            }
            public double optimize(UnivariateRealFunction f, GoalType goalType, double min, double max, double startValue) {
                return optimize(f, goalType, min, max);
            }
        };

        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 3, new DummyRandomGenerator(0.5));
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x; }
        };

        double best = optimizer.optimize(f, GoalType.MAXIMIZE, 0.0, 10.0);
        assertEquals(3.0, best, 1e-12);

        double[] optima = optimizer.getOptima();
        double[] optimaVals = optimizer.getOptimaValues();
        assertEquals(3.0, optimaVals[0], 1e-12);
        assertEquals(2.0, optimaVals[1], 1e-12);
        assertEquals(1.0, optimaVals[2], 1e-12);
    }

    @Test
    public void testExceptionsHandledAsNaN() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(1.0, true, false);
        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 2, new DummyRandomGenerator(0.5));
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x; }
        };

        boolean thrown = false;
        try {
            optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 10.0);
        } catch (OptimizationException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testFunctionEvaluationExceptionHandledAsNaN() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(1.0, false, true);
        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 2, new DummyRandomGenerator(0.5));
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x; }
        };

        boolean thrown = false;
        try {
            optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 10.0);
        } catch (OptimizationException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testStartValueOverload() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(5.0, false, false);
        MultiStartUnivariateRealOptimizer optimizer = new MultiStartUnivariateRealOptimizer(underlying, 1, new DummyRandomGenerator(0.5));
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x; }
        };

        double result = optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 10.0, 2.0);
        assertEquals(5.0, result, 1e-12);
        assertEquals(5, optimizer.getIterationCount());
        assertEquals(10, optimizer.getEvaluations());
    }
}