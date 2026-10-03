package org.apache.commons.math.optimization.univariate;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.exception.MathIllegalStateException;
import org.apache.commons.math.exception.ConvergenceException;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.random.JDKRandomGenerator;
import org.apache.commons.math.util.FastMath;

public class MultiStartUnivariateRealOptimizerClaudeTest {

    private UnivariateRealFunction createParabola() {
        return new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return (x - 2.0) * (x - 2.0) + 1.0;
            }
        };
    }

    private UnivariateRealFunction createInvertedParabola() {
        return new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return -(x - 2.0) * (x - 2.0) + 1.0;
            }
        };
    }

    private UnivariateRealFunction createAlwaysFailingFunction() {
        return new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                throw new FunctionEvaluationException(x);
            }
        };
    }

    private MultiStartUnivariateRealOptimizer<UnivariateRealFunction> createOptimizer(int starts) {
        BrentOptimizer brent = new BrentOptimizer(1e-10, 1e-14);
        JDKRandomGenerator rg = new JDKRandomGenerator();
        rg.setSeed(42);
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(brent, starts, rg);
        ms.setMaxEvaluations(2000);
        return ms;
    }

    // covers getMaxEvaluations() before setMaxEvaluations has ever been called
    @Test
    public void testGetMaxEvaluations_initialState_returnsZero() throws Throwable {
        BrentOptimizer brent = new BrentOptimizer(1e-10, 1e-14);
        JDKRandomGenerator rg = new JDKRandomGenerator();
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(brent, 2, rg);
        assertEquals(0, ms.getMaxEvaluations());
    }

    // covers getEvaluations() before any optimize() call
    @Test
    public void testGetEvaluations_initialState_returnsZero() throws Throwable {
        BrentOptimizer brent = new BrentOptimizer(1e-10, 1e-14);
        JDKRandomGenerator rg = new JDKRandomGenerator();
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms =
            new MultiStartUnivariateRealOptimizer<UnivariateRealFunction>(brent, 2, rg);
        assertEquals(0, ms.getEvaluations());
    }

    // covers setMaxEvaluations delegating and storing the value for later retrieval
    @Test
    public void testSetMaxEvaluations_thenGet_returnsSetValue() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        ms.setMaxEvaluations(777);
        assertEquals(777, ms.getMaxEvaluations());
    }

    // covers setConvergenceChecker/getConvergenceChecker pure delegation branch
    @Test
    public void testSetConvergenceChecker_nullValue_getReturnsNull() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        ms.setConvergenceChecker(null);
        assertNull(ms.getConvergenceChecker());
    }

    // covers getOptima() branch: optima not computed yet -> MathIllegalStateException
    @Test
    public void testGetOptima_beforeOptimize_throwsMathIllegalStateException() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(2);
        try {
            ms.getOptima();
            fail("expected MathIllegalStateException");
        } catch (MathIllegalStateException expected) {
        }
    }

    // covers optimize(f,goal,min,max) delegating to the 5-arg form, minimize branch
    @Test
    public void testOptimize4Arg_minimizeParabola_returnsPointNearVertex() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        UnivariateRealPointValuePair result = ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(2.0, result.getPoint(), 1e-4);
        assertEquals(1.0, result.getValue(), 1e-4);
    }

    // covers optimize(f,goal,min,max), maximize branch via inverted parabola
    @Test
    public void testOptimize4Arg_maximizeInvertedParabola_returnsPointNearVertex() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        UnivariateRealPointValuePair result = ms.optimize(createInvertedParabola(), GoalType.MAXIMIZE, -10.0, 10.0);
        assertEquals(2.0, result.getPoint(), 1e-4);
        assertEquals(1.0, result.getValue(), 1e-4);
    }

    // covers that the returned point always lies within the requested [min,max] bounds
    @Test
    public void testOptimize4Arg_resultWithinBounds() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        UnivariateRealPointValuePair result = ms.optimize(createParabola(), GoalType.MINIMIZE, -5.0, 5.0);
        assertTrue(result.getPoint() >= -5.0 && result.getPoint() <= 5.0);
    }

    // covers optimize(f,goal,min,max,startValue): startValue must actually reach the wrapped optimizer (bug check)
    @Test
    public void testOptimize5Arg_customStartValue_functionEvaluatedAtStartValue() throws Throwable {
        final double startValue = 7.3;
        final boolean[] hitStart = new boolean[1];
        UnivariateRealFunction tracker = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                if (FastMath.abs(x - startValue) < 1e-9) {
                    hitStart[0] = true;
                }
                return (x - 2.0) * (x - 2.0) + 1.0;
            }
        };
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        ms.optimize(tracker, GoalType.MINIMIZE, -10.0, 10.0, startValue);
        assertTrue("start value should be forwarded to the wrapped optimizer", hitStart[0]);
    }

    // covers getEvaluations() accumulating a positive value after a successful optimize call
    @Test
    public void testOptimize_singleStart_evaluationsPositiveAfterCall() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertTrue(ms.getEvaluations() > 0);
    }

    // covers optima array sized exactly to the "starts" constructor parameter
    @Test
    public void testOptimize_afterSuccess_getOptimaLengthEqualsStarts() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(3);
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(3, ms.getOptima().length);
    }

    // covers that the returned best point matches optima[0] per the getOptima() javadoc contract
    @Test
    public void testOptimize_resultEqualsOptimaFirstElement() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(3);
        UnivariateRealPointValuePair result = ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        UnivariateRealPointValuePair[] optima = ms.getOptima();
        assertNotNull(optima[0]);
        assertEquals(result.getValue(), optima[0].getValue(), 1e-9);
    }

    // covers getOptima() returning a cloned array, not the internal storage itself
    @Test
    public void testGetOptima_returnsDefensiveCopy() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(2);
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        UnivariateRealPointValuePair[] first = ms.getOptima();
        first[0] = null;
        UnivariateRealPointValuePair[] second = ms.getOptima();
        assertNotNull(second[0]);
    }

    // covers the multi-start loop (several iterations) and ascending sortPairs order for MINIMIZE
    @Test
    public void testOptimize_multiStartMinimize_allConvergeAndSortedAscending() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(3);
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        UnivariateRealPointValuePair[] optima = ms.getOptima();
        for (int i = 0; i < optima.length; i++) {
            assertNotNull(optima[i]);
        }
        for (int i = 1; i < optima.length; i++) {
            assertTrue(optima[i - 1].getValue() <= optima[i].getValue() + 1e-9);
        }
    }

    // covers the multi-start loop and descending sortPairs order for MAXIMIZE
    @Test
    public void testOptimize_multiStartMaximize_allConvergeAndSortedDescending() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(3);
        ms.optimize(createInvertedParabola(), GoalType.MAXIMIZE, -10.0, 10.0);
        UnivariateRealPointValuePair[] optima = ms.getOptima();
        for (int i = 0; i < optima.length; i++) {
            assertNotNull(optima[i]);
        }
        for (int i = 1; i < optima.length; i++) {
            assertTrue(optima[i - 1].getValue() >= optima[i].getValue() - 1e-9);
        }
    }

    // covers the branch where optima[0] stays null after sorting -> ConvergenceException is thrown
    @Test
    public void testOptimize_functionAlwaysFails_throwsConvergenceException() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(3);
        try {
            ms.optimize(createAlwaysFailingFunction(), GoalType.MINIMIZE, -10.0, 10.0);
            fail("expected ConvergenceException");
        } catch (ConvergenceException expected) {
        }
    }

    // covers the catch(FunctionEvaluationException) branch setting optima[i] = null for every start
    @Test
    public void testOptimize_functionAlwaysFails_optimaAllNull() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(3);
        try {
            ms.optimize(createAlwaysFailingFunction(), GoalType.MINIMIZE, -10.0, 10.0);
        } catch (ConvergenceException expected) {
        }
        UnivariateRealPointValuePair[] optima = ms.getOptima();
        for (int i = 0; i < optima.length; i++) {
            assertNull(optima[i]);
        }
    }

    // covers totalEvaluations = 0 being reset at the start of every optimize() call
    @Test
    public void testOptimize_calledTwice_totalEvaluationsResetNotAccumulated() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        int firstRunEvaluations = ms.getEvaluations();
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        int secondRunEvaluations = ms.getEvaluations();
        assertEquals(firstRunEvaluations, secondRunEvaluations);
    }

    // covers "optima = new ...[starts]" being re-assigned (not appended) on each call
    @Test
    public void testOptimize_calledTwice_optimaArrayReplacedNotAppended() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(2);
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(2, ms.getOptima().length);
    }

    // covers the starts == 1 edge value producing an optima array of exactly one element
    @Test
    public void testOptimize_startsOne_optimaLengthOne() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(1);
        ms.optimize(createInvertedParabola(), GoalType.MAXIMIZE, -10.0, 10.0);
        assertEquals(1, ms.getOptima().length);
    }

    // covers that start index 0 always uses the full [min,max] bounds, guaranteeing a correct global result
    @Test
    public void testOptimize_multiStart_bestResultStillNearGlobalMinimum() throws Throwable {
        MultiStartUnivariateRealOptimizer<UnivariateRealFunction> ms = createOptimizer(3);
        UnivariateRealPointValuePair result = ms.optimize(createParabola(), GoalType.MINIMIZE, -10.0, 10.0);
        assertEquals(2.0, result.getPoint(), 1e-3);
        assertEquals(1.0, result.getValue(), 1e-3);
    }
}
