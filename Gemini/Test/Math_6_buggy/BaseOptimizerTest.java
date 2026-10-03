package org.apache.commons.math3.optim;

import org.junit.Test;
import static org.junit.Assert.*;

public class BaseOptimizerTest {

    private static class ConcreteOptimizer extends BaseOptimizer<String> {
        private final String result;

        public ConcreteOptimizer(ConvergenceChecker<String> checker, String result) {
            super(checker);
            this.result = result;
        }

        @Override
        protected String doOptimize() {
            return result;
        }

        public void callParseOptimizationData(OptimizationData... optData) {
            parseOptimizationData(optData);
        }

        public void callIncrementEvaluationCount() {
            incrementEvaluationCount();
        }

        public void callIncrementIterationCount() {
            incrementIterationCount();
        }
    }

    @Test
    public void testInitialState() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer(null, "test");
        assertNull(optimizer.getConvergenceChecker());
        assertEquals(0, optimizer.getMaxEvaluations());
        assertEquals(0, optimizer.getEvaluations());
        assertEquals(0, optimizer.getMaxIterations());
        assertEquals(0, optimizer.getIterations());
    }

    @Test
    public void testParseOptimizationDataMaxEvalAndMaxIter() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer(null, "test");
        OptimizationData maxEval = new MaxEval(10);
        OptimizationData maxIter = new MaxIter(20);

        optimizer.callParseOptimizationData(maxEval, maxIter);

        assertEquals(10, optimizer.getMaxEvaluations());
        assertEquals(20, optimizer.getMaxIterations());
    }

    @Test
    public void testParseOptimizationDataNullArray() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer(null, "test");
        optimizer.callParseOptimizationData((OptimizationData[]) null);
        assertEquals(0, optimizer.getMaxEvaluations());
        assertEquals(0, optimizer.getMaxIterations());
    }

    @Test
    public void testOptimizeSuccess() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer(null, "optimal");
        OptimizationData maxEval = new MaxEval(5);
        OptimizationData maxIter = new MaxIter(5);

        String result = optimizer.optimize(maxEval, maxIter);
        assertEquals("optimal", result);
        assertEquals(0, optimizer.getEvaluations());
        assertEquals(0, optimizer.getIterations());
    }

    @Test
    public void testIncrementEvaluationCountExceeded() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer(null, "test");
        optimizer.callParseOptimizationData(new MaxEval(1));

        optimizer.callIncrementEvaluationCount();
        assertEquals(1, optimizer.getEvaluations());

        try {
            optimizer.callIncrementEvaluationCount();
            fail("Expected TooManyEvaluationsException");
        } catch (org.apache.commons.math3.exception.TooManyEvaluationsException e) {
            assertTrue(e.getMessage().contains("1") || e.getMessage() != null);
        }
    }

    @Test
    public void testIncrementIterationCountExceeded() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer(null, "test");
        optimizer.callParseOptimizationData(new MaxIter(1));

        optimizer.callIncrementIterationCount();
        assertEquals(1, optimizer.getIterations());

        try {
            optimizer.callIncrementIterationCount();
            fail("Expected TooManyIterationsException");
        } catch (org.apache.commons.math3.exception.TooManyIterationsException e) {
            assertTrue(e.getMessage().contains("1") || e.getMessage() != null);
        }
    }

    @Test
    public void testRetainPreviousOptimizationData() throws Throwable {
        ConcreteOptimizer optimizer = new ConcreteOptimizer(null, "test");
        optimizer.callParseOptimizationData(new MaxEval(15), new MaxIter(25));

        // Call optimize without arguments, should retain previous max eval/iter
        String result = optimizer.optimize();
        assertEquals("test", result);
        assertEquals(15, optimizer.getMaxEvaluations());
        assertEquals(25, optimizer.getMaxIterations());
    }
}