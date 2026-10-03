package org.apache.commons.math.optimization.univariate;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Comparator;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.exception.MathIllegalStateException;
import org.apache.commons.math.exception.ConvergenceException;
import org.apache.commons.math.random.RandomGenerator;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.ConvergenceChecker;

public class MultiStartUnivariateRealOptimizerTest {

    private static class DummyOptimizer implements BaseUnivariateRealOptimizer<UnivariateRealFunction> {
        private ConvergenceChecker<UnivariateRealPointValuePair> checker;
        private int maxEvaluations;
        private int evaluations;
        private UnivariateRealPointValuePair nextResult;
        private boolean throwConvergenceOnOptimize;
        private boolean throwFunctionEvalOnOptimize;

        public DummyOptimizer(UnivariateRealPointValuePair nextResult) {
            this.nextResult = nextResult;
            this.evaluations = 10;
        }

        public void setConvergenceChecker(ConvergenceChecker<UnivariateRealPointValuePair> checker) {
            this.checker = checker;
        }

        public ConvergenceChecker<UnivariateRealPointValuePair> getConvergenceChecker() {
            return checker;
        }

        public int getMaxEvaluations() {
            return maxEvaluations;
        }

        public int getEvaluations() {
            return evaluations;
        }

        public void setMaxEvaluations(int maxEvaluations) {
            this.maxEvaluations = maxEvaluations;
        }

        public UnivariateRealPointValuePair optimize(UnivariateRealFunction f, GoalType goal, double min, double max)
            throws FunctionEvaluationException {
            if (throwConvergenceOnOptimize) {
                throw new ConvergenceException();
            }
            if (throwFunctionEvalOnOptimize) {
                throw new FunctionEvaluationException(0.0);
            }
            return nextResult;
        }

        public UnivariateRealPointValuePair optimize(UnivariateRealFunction f, GoalType goal, double min, double max, double startValue)
            throws FunctionEvaluationException {
            return optimize(f, goal, min, max);
        }
    }

    private static class DummyRandomGenerator implements RandomGenerator {
        private double nextVal = 0.5;

        public DummyRandomGenerator(double nextVal) {
            this.nextVal = nextVal;
        }

        public void setSeed(int seed) {}
        public void setSeed(int[] seed) {}
        public void setSeed(long seed) {}
        public void nextBytes(byte[] bytes) {}
        public int nextInt() { return 0; }
        public int nextInt(int n) { return 0; }
        public long nextLong() { return 0L; }
        public boolean nextBoolean() { return false; }
        public float nextFloat() { return 0.0f; }
        public double nextDouble() { return nextVal; }
        public double nextGaussian() { return 0.0; }
    }

    @Test(expected = MathIllegalStateException.class)
    public void testGetOptimaBeforeOptimize() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(new UnivariateRealPointValuePair(0.0, 0.0));
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> optimizer =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(underlying, 2, new DummyRandomGenerator(0.5));
        
        optimizer.getOptima();
    }

    @Test
    public void testGettersAndSetters() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(new UnivariateRealPointValuePair(0.0, 0.0));
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> optimizer =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(underlying, 3, new DummyRandomGenerator(0.5));

        optimizer.setMaxEvaluations(100);
        assertEquals(100, optimizer.getMaxEvaluations());

        ConvergenceChecker<UnivariateRealPointValuePair> checker = new ConvergenceChecker<UnivariateRealPointValuePair>() {
            public boolean converged(int iteration, UnivariateRealPointValuePair previous, UnivariateRealPointValuePair current) {
                return true;
            }
        };
        optimizer.setConvergenceChecker(checker);
        assertEquals(checker, optimizer.getConvergenceChecker());
    }

    @Test
    public void testOptimizeMinimizeSorting() throws Throwable {
        final UnivariateRealPointValuePair p1 = new UnivariateRealPointValuePair(1.0, 5.0);
        final UnivariateRealPointValuePair p2 = new UnivariateRealPointValuePair(2.0, 2.0);
        
        DummyOptimizer underlying = new DummyOptimizer(p1) {
            private int callCount = 0;
            public UnivariateRealPointValuePair optimize(UnivariateRealFunction f, GoalType goal, double min, double max) {
                callCount++;
                if (callCount == 1) {
                    return p1;
                }
                return p2;
            }
        };

        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> optimizer =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(underlying, 2, new DummyRandomGenerator(0.5));

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };

        optimizer.setMaxEvaluations(100);
        UnivariateRealPointValuePair best = optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 10.0);

        assertEquals(2.0, best.getValue(), 1e-10);
        UnivariateRealPointValuePair[] optima = optimizer.getOptima();
        assertEquals(2, optima.length);
        assertEquals(2.0, optima[0].getValue(), 1e-10);
        assertEquals(5.0, optima[1].getValue(), 1e-10);
        assertEquals(20, optimizer.getEvaluations());
    }

    @Test
    public void testOptimizeMaximizeSorting() throws Throwable {
        final UnivariateRealPointValuePair p1 = new UnivariateRealPointValuePair(1.0, 2.0);
        final UnivariateRealPointValuePair p2 = new UnivariateRealPointValuePair(2.0, 8.0);
        
        DummyOptimizer underlying = new DummyOptimizer(p1) {
            private int callCount = 0;
            public UnivariateRealPointValuePair optimize(UnivariateRealFunction f, GoalType goal, double min, double max) {
                callCount++;
                if (callCount == 1) {
                    return p1;
                }
                return p2;
            }
        };

        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> optimizer =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(underlying, 2, new DummyRandomGenerator(0.5));

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };

        optimizer.setMaxEvaluations(100);
        UnivariateRealPointValuePair best = optimizer.optimize(f, GoalType.MAXIMIZE, 0.0, 10.0);

        assertEquals(8.0, best.getValue(), 1e-10);
        UnivariateRealPointValuePair[] optima = optimizer.getOptima();
        assertEquals(2, optima.length);
        assertEquals(8.0, optima[0].getValue(), 1e-10);
        assertEquals(2.0, optima[1].getValue(), 1e-10);
    }

    @Test
    public void testExceptionsAndNullOptimaSorting() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(null) {
            private int callCount = 0;
            public UnivariateRealPointValuePair optimize(UnivariateRealFunction f, GoalType goal, double min, double max)
                throws FunctionEvaluationException {
                callCount++;
                if (callCount == 1) {
                    throw new FunctionEvaluationException(0.0);
                } else if (callCount == 2) {
                    throw new ConvergenceException();
                }
                return new UnivariateRealPointValuePair(1.0, 3.0);
            }
        };

        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> optimizer =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(underlying, 3, new DummyRandomGenerator(0.5));

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };

        optimizer.setMaxEvaluations(100);
        UnivariateRealPointValuePair best = optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 10.0);
        
        assertEquals(3.0, best.getValue(), 1e-10);
        UnivariateRealPointValuePair[] optima = optimizer.getOptima();
        assertEquals(3, optima.length);
        assertNotNull(optima[0]);
        assertNull(optima[1]);
        assertNull(optima[2]);
    }

    @Test(expected = ConvergenceException.class)
    public void testAllStartsFailConvergence() throws Throwable {
        DummyOptimizer underlying = new DummyOptimizer(null) {
            public UnivariateRealPointValuePair optimize(UnivariateRealFunction f, GoalType goal, double min, double max)
                throws ConvergenceException {
                throw new ConvergenceException();
            }
        };

        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> optimizer =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(underlying, 2, new DummyRandomGenerator(0.5));

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };

        optimizer.setMaxEvaluations(100);
        optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 10.0);
    }
}