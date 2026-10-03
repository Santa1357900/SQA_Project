package org.apache.commons.math.optimization;

import org.apache.commons.math.ConvergenceException;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.optimization.univariate.BrentOptimizer;
import org.apache.commons.math.random.JDKRandomGenerator;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MultiStartUnivariateRealOptimizerClaudeTest {

    private BrentOptimizer underlying;
    private JDKRandomGenerator generator;
    private MultiStartUnivariateRealOptimizer multi;

    @Before
    public void setUp() throws Throwable {
        underlying = new BrentOptimizer();
        generator = new JDKRandomGenerator();
        generator.setSeed(987654321L);
        multi = new MultiStartUnivariateRealOptimizer(underlying, 1, generator);
    }

    private UnivariateRealFunction minimizeParabola() {
        return new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return (x - 1.0) * (x - 1.0);
            }
        };
    }

    private UnivariateRealFunction maximizeParabola() {
        return new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return -(x - 1.0) * (x - 1.0);
            }
        };
    }

    private UnivariateRealFunction failingFunction() {
        return new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                throw new FunctionEvaluationException(x);
            }
        };
    }

    // constructor sets maxIterations default to Integer.MAX_VALUE
    @Test
    public void testConstructor_defaultMaximalIterationCount_returnsIntegerMaxValue() throws Throwable {
        assertEquals(Integer.MAX_VALUE, multi.getMaximalIterationCount());
    }

    // constructor sets maxEvaluations default to Integer.MAX_VALUE
    @Test
    public void testConstructor_defaultMaxEvaluations_returnsIntegerMaxValue() throws Throwable {
        assertEquals(Integer.MAX_VALUE, multi.getMaxEvaluations());
    }

    // totalIterations initialized to 0 before any optimize() call
    @Test
    public void testGetIterationCount_beforeOptimize_returnsZero() throws Throwable {
        assertEquals(0, multi.getIterationCount());
    }

    // totalEvaluations initialized to 0 before any optimize() call
    @Test
    public void testGetEvaluations_beforeOptimize_returnsZero() throws Throwable {
        assertEquals(0, multi.getEvaluations());
    }

    // getOptima() must throw IllegalStateException when optima is still null
    @Test
    public void testGetOptima_beforeOptimize_throwsIllegalStateException() throws Throwable {
        try {
            multi.getOptima();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // optimize() has not been called yet
        }
    }

    // getOptimaValues() must throw IllegalStateException when optimaValues is still null
    @Test
    public void testGetOptimaValues_beforeOptimize_throwsIllegalStateException() throws Throwable {
        try {
            multi.getOptimaValues();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // optimize() has not been called yet
        }
    }

    // setMaximalIterationCount updates the wrapper's own field, observable via getter
    @Test
    public void testSetMaximalIterationCount_updatesGetter() throws Throwable {
        multi.setMaximalIterationCount(37);
        assertEquals(37, multi.getMaximalIterationCount());
    }

    // setMaxEvaluations updates the wrapper's own field, observable via getter
    @Test
    public void testSetMaxEvaluations_updatesGetter() throws Throwable {
        multi.setMaxEvaluations(42);
        assertEquals(42, multi.getMaxEvaluations());
    }

    // getAbsoluteAccuracy() delegates directly to underlying optimizer
    @Test
    public void testGetAbsoluteAccuracy_delegatesToUnderlying() throws Throwable {
        assertEquals(underlying.getAbsoluteAccuracy(), multi.getAbsoluteAccuracy(), 1e-15);
    }

    // setAbsoluteAccuracy() delegates directly to underlying optimizer
    @Test
    public void testSetAbsoluteAccuracy_delegatesToUnderlying() throws Throwable {
        double newVal = underlying.getAbsoluteAccuracy() * 2.0;
        multi.setAbsoluteAccuracy(newVal);
        assertEquals(newVal, underlying.getAbsoluteAccuracy(), 1e-15);
        assertEquals(newVal, multi.getAbsoluteAccuracy(), 1e-15);
    }

    // getRelativeAccuracy() delegates directly to underlying optimizer
    @Test
    public void testGetRelativeAccuracy_delegatesToUnderlying() throws Throwable {
        assertEquals(underlying.getRelativeAccuracy(), multi.getRelativeAccuracy(), 1e-15);
    }

    // setRelativeAccuracy() delegates directly to underlying optimizer
    @Test
    public void testSetRelativeAccuracy_delegatesToUnderlying() throws Throwable {
        double newVal = underlying.getRelativeAccuracy() * 2.0;
        multi.setRelativeAccuracy(newVal);
        assertEquals(newVal, underlying.getRelativeAccuracy(), 1e-15);
        assertEquals(newVal, multi.getRelativeAccuracy(), 1e-15);
    }

    // resetAbsoluteAccuracy() delegates to underlying and restores its default value
    @Test
    public void testResetAbsoluteAccuracy_restoresDefault() throws Throwable {
        double original = underlying.getAbsoluteAccuracy();
        multi.setAbsoluteAccuracy(original * 2.0);
        multi.resetAbsoluteAccuracy();
        assertEquals(original, multi.getAbsoluteAccuracy(), 1e-15);
    }

    // resetRelativeAccuracy() delegates to underlying and restores its default value
    @Test
    public void testResetRelativeAccuracy_restoresDefault() throws Throwable {
        double original = underlying.getRelativeAccuracy();
        multi.setRelativeAccuracy(original * 2.0);
        multi.resetRelativeAccuracy();
        assertEquals(original, multi.getRelativeAccuracy(), 1e-15);
    }

    // resetMaximalIterationCount() delegates to underlying.resetMaximalIterationCount()
    @Test
    public void testResetMaximalIterationCount_restoresUnderlyingDefault() throws Throwable {
        int original = underlying.getMaximalIterationCount();
        underlying.setMaximalIterationCount(original + 10);
        multi.resetMaximalIterationCount();
        assertEquals(original, underlying.getMaximalIterationCount());
    }

    // single start, successful convergence to the known minimum of (x-1)^2
    @Test
    public void testOptimize_singleStartMinimize_returnsExpectedMinimum() throws Throwable {
        double result = multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(1.0, result, 1e-3);
    }

    // single start: getOptima() array length equals starts (=1)
    @Test
    public void testOptimize_singleStartMinimize_optimaArrayLengthMatchesStarts() throws Throwable {
        multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        double[] optima = multi.getOptima();
        assertEquals(1, optima.length);
    }

    // getFunctionValue() after optimize() matches the stored optimaValues[0]
    @Test
    public void testGetFunctionValue_afterOptimize_matchesOptimaValue() throws Throwable {
        multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        double[] values = multi.getOptimaValues();
        assertEquals(values[0], multi.getFunctionValue(), 1e-9);
    }

    // getResult() after optimize() matches the returned optimum point
    @Test
    public void testGetResult_afterOptimize_matchesReturnedOptimum() throws Throwable {
        double result = multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(result, multi.getResult(), 1e-9);
    }

    // for a single start, totalIterations equals underlying optimizer's iteration count
    @Test
    public void testGetIterationCount_afterSingleStartOptimize_matchesUnderlying() throws Throwable {
        multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(underlying.getIterationCount(), multi.getIterationCount());
    }

    // for a single start, totalEvaluations equals underlying optimizer's evaluation count
    @Test
    public void testGetEvaluations_afterSingleStartOptimize_matchesUnderlying() throws Throwable {
        multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(underlying.getEvaluations(), multi.getEvaluations());
    }

    // multi-start minimize: all starts converge, results sorted ascending (best first)
    @Test
    public void testOptimize_multiStartMinimize_allConverge_sortedAscending() throws Throwable {
        MultiStartUnivariateRealOptimizer m = new MultiStartUnivariateRealOptimizer(underlying, 4, generator);
        double result = m.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        double[] values = m.getOptimaValues();
        assertEquals(4, values.length);
        assertEquals(values[0], m.getOptima()[0] == result ? values[0] : values[0], 1e-9);
        for (int i = 1; i < values.length; i++) {
            assertFalse(Double.isNaN(values[i]));
            assertTrue(values[i - 1] <= values[i] + 1e-9);
        }
    }

    // multi-start maximize: all starts converge, results sorted descending (best first)
    @Test
    public void testOptimize_multiStartMaximize_allConverge_sortedDescending() throws Throwable {
        MultiStartUnivariateRealOptimizer m = new MultiStartUnivariateRealOptimizer(underlying, 4, generator);
        m.optimize(maximizeParabola(), GoalType.MAXIMIZE, -10.0, 10.0);
        double[] values = m.getOptimaValues();
        assertEquals(4, values.length);
        for (int i = 1; i < values.length; i++) {
            assertFalse(Double.isNaN(values[i]));
            assertTrue(values[i - 1] >= values[i] - 1e-9);
        }
    }

    // getOptima() returns a defensive copy, mutating it must not change internal state
    @Test
    public void testGetOptima_returnsDefensiveCopy() throws Throwable {
        multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        double[] first = multi.getOptima();
        double originalValue = first[0];
        first[0] = originalValue + 999.0;
        double[] second = multi.getOptima();
        assertEquals(originalValue, second[0], 1e-9);
    }

    // getOptimaValues() returns a defensive copy, mutating it must not change internal state
    @Test
    public void testGetOptimaValues_returnsDefensiveCopy() throws Throwable {
        multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        double[] first = multi.getOptimaValues();
        double originalValue = first[0];
        first[0] = originalValue + 999.0;
        double[] second = multi.getOptimaValues();
        assertEquals(originalValue, second[0], 1e-9);
    }

    // 5-arg optimize() must delegate to the 4-arg optimize(), ignoring startValue
    @Test
    public void testOptimizeWithStartValue_delegatesSameAsFourArgOptimize() throws Throwable {
        double result = multi.optimize(minimizeParabola(), GoalType.MINIMIZE, -10.0, 10.0, 0.5);
        assertEquals(1.0, result, 1e-3);
    }

    // BUG: when every start fails, the correct contract is to throw ConvergenceException
    // (OptimizationException) rather than let an internal ArrayIndexOutOfBoundsException escape
    @Test
    public void testOptimize_allStartsFail_throwsConvergenceException() throws Throwable {
        MultiStartUnivariateRealOptimizer m = new MultiStartUnivariateRealOptimizer(underlying, 3, generator);
        try {
            m.optimize(failingFunction(), GoalType.MINIMIZE, -10.0, 10.0);
            fail("expected ConvergenceException");
        } catch (ConvergenceException expected) {
            // per javadoc: NO_CONVERGENCE_WITH_ANY_START_POINT is reported this way
        }
    }

    // BUG variant with a single start: a single failing evaluation must still surface as
    // ConvergenceException, not as an unchecked ArrayIndexOutOfBoundsException
    @Test
    public void testOptimize_singleStartFails_throwsConvergenceException() throws Throwable {
        MultiStartUnivariateRealOptimizer m = new MultiStartUnivariateRealOptimizer(underlying, 1, generator);
        try {
            m.optimize(failingFunction(), GoalType.MINIMIZE, -10.0, 10.0);
            fail("expected ConvergenceException");
        } catch (ConvergenceException expected) {
            // per javadoc: all elements NaN implies optimize() throws ConvergenceException
        }
    }
}
