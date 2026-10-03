package org.apache.commons.math.ode;

import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.DimensionMismatchException;
import org.apache.commons.math.exception.MathIllegalArgumentException;
import org.apache.commons.math.exception.MathIllegalStateException;
import org.apache.commons.math.exception.MaxCountExceededException;
import org.apache.commons.math.exception.NumberIsTooSmallException;
import org.apache.commons.math.ode.events.EventHandler;
import org.apache.commons.math.ode.sampling.StepHandler;

public class AbstractIntegratorClaudeTest {

    private AbstractIntegrator integrator;

    private static class SimpleEquations implements FirstOrderDifferentialEquations {
        private final int dimension;

        SimpleEquations(int dimension) {
            this.dimension = dimension;
        }

        public int getDimension() {
            return dimension;
        }

        public void computeDerivatives(double t, double[] y, double[] yDot) {
            for (int i = 0; i < yDot.length; i++) {
                yDot[i] = 1.0;
            }
        }
    }

    private static class ConcreteIntegrator extends AbstractIntegrator {
        ConcreteIntegrator(String name) {
            super(name);
        }

        ConcreteIntegrator() {
            super();
        }

        public void integrate(ExpandableStatefulODE equations, double t)
            throws MathIllegalStateException, MathIllegalArgumentException {
            sanityChecks(equations, t);
            setEquations(equations);
            double[] y = equations.getPrimaryState();
            double[] yDot = new double[y.length];
            computeDerivatives(equations.getTime(), y, yDot);
            equations.setTime(t);
            isLastStep = true;
        }
    }

    @Before
    public void setUp() throws Throwable {
        integrator = new ConcreteIntegrator("testIntegrator");
    }

    // covers constructor(name) storing name field, getName() returning it
    @Test
    public void testConstructor_withName_getNameReturnsGivenName() throws Throwable {
        AbstractIntegrator local = new ConcreteIntegrator("myMethod");
        assertEquals("myMethod", local.getName());
    }

    // covers protected no-arg constructor delegating to this(null)
    @Test
    public void testConstructor_protectedNoArg_getNameReturnsNull() throws Throwable {
        AbstractIntegrator local = new ConcreteIntegrator();
        assertNull(local.getName());
    }

    // covers constructor with empty string name edge case
    @Test
    public void testConstructor_withEmptyName_getNameReturnsEmptyString() throws Throwable {
        AbstractIntegrator local = new ConcreteIntegrator("");
        assertEquals("", local.getName());
    }

    // covers addStepHandler storing a single handler into the internal list
    @Test
    public void testAddStepHandler_singleHandler_appearsInGetStepHandlers() throws Throwable {
        assertTrue(integrator.getStepHandlers().isEmpty());
        integrator.addStepHandler(null);
        assertEquals(1, integrator.getStepHandlers().size());
    }

    // covers addStepHandler being called multiple times, growing the list
    @Test
    public void testAddStepHandler_multipleHandlers_sizeGrows() throws Throwable {
        integrator.addStepHandler(null);
        integrator.addStepHandler(null);
        assertEquals(2, integrator.getStepHandlers().size());
    }

    // covers Collections.unmodifiableCollection wrapping in getStepHandlers
    @Test
    public void testGetStepHandlers_returnedCollection_isUnmodifiable() throws Throwable {
        Collection<StepHandler> handlers = integrator.getStepHandlers();
        try {
            handlers.add(null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // covers clearStepHandlers clearing a non-empty internal list
    @Test
    public void testClearStepHandlers_afterAdding_resultsEmpty() throws Throwable {
        integrator.addStepHandler(null);
        integrator.clearStepHandlers();
        assertTrue(integrator.getStepHandlers().isEmpty());
    }

    // covers clearStepHandlers no-op branch on an already empty collection
    @Test
    public void testClearStepHandlers_onEmptyCollection_remainsEmpty() throws Throwable {
        integrator.clearStepHandlers();
        assertTrue(integrator.getStepHandlers().isEmpty());
    }

    // covers getEventHandlers with zero registered event states (0-iteration loop)
    @Test
    public void testGetEventHandlers_initialState_isEmpty() throws Throwable {
        Collection<EventHandler> handlers = integrator.getEventHandlers();
        assertTrue(handlers.isEmpty());
    }

    // covers clearEventHandlers no-op on an already empty collection
    @Test
    public void testClearEventHandlers_onEmptyCollection_remainsEmpty() throws Throwable {
        integrator.clearEventHandlers();
        assertTrue(integrator.getEventHandlers().isEmpty());
    }

    // covers stepStart field initialized to Double.NaN in constructor
    @Test
    public void testGetCurrentStepStart_initial_isNaN() throws Throwable {
        assertTrue(Double.isNaN(integrator.getCurrentStepStart()));
    }

    // covers stepSize field initialized to Double.NaN in constructor
    @Test
    public void testGetCurrentSignedStepsize_initial_isNaN() throws Throwable {
        assertTrue(Double.isNaN(integrator.getCurrentSignedStepsize()));
    }

    // covers ternary condition maxEvaluations < 0, with boundary value -1
    @Test
    public void testSetMaxEvaluations_negativeOne_setsIntegerMaxValue() throws Throwable {
        integrator.setMaxEvaluations(-1);
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
    }

    // covers ternary negative branch with an extreme negative edge value
    @Test
    public void testSetMaxEvaluations_largeNegative_setsIntegerMaxValue() throws Throwable {
        integrator.setMaxEvaluations(Integer.MIN_VALUE);
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
    }

    // covers ternary boundary: 0 is not negative, so it is stored verbatim
    @Test
    public void testSetMaxEvaluations_zero_setsExactZero() throws Throwable {
        integrator.setMaxEvaluations(0);
        assertEquals(0, integrator.getMaxEvaluations());
    }

    // covers ternary false branch with a plain positive value
    @Test
    public void testSetMaxEvaluations_positiveValue_setsExactValue() throws Throwable {
        integrator.setMaxEvaluations(42);
        assertEquals(42, integrator.getMaxEvaluations());
    }

    // covers constructor calling setMaxEvaluations(-1) by default
    @Test
    public void testGetMaxEvaluations_defaultConstructorValue_isIntegerMaxValue() throws Throwable {
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
    }

    // covers constructor calling resetEvaluations(), evaluations start at zero
    @Test
    public void testGetEvaluations_initialState_isZero() throws Throwable {
        assertEquals(0, integrator.getEvaluations());
    }

    // covers computeDerivatives incrementing evaluations and delegating to expandable
    @Test
    public void testComputeDerivatives_singleCall_incrementsEvaluationCount() throws Throwable {
        ExpandableStatefulODE ode = new ExpandableStatefulODE(new SimpleEquations(2));
        ode.setTime(0.0);
        integrator.setEquations(ode);
        double[] y = new double[] {1.0, 2.0};
        double[] yDot = new double[2];
        integrator.computeDerivatives(0.0, y, yDot);
        assertEquals(1, integrator.getEvaluations());
    }

    // covers evaluations counter incrementing across several successive calls
    @Test
    public void testComputeDerivatives_multipleCalls_countsEachCall() throws Throwable {
        ExpandableStatefulODE ode = new ExpandableStatefulODE(new SimpleEquations(1));
        ode.setTime(0.0);
        integrator.setEquations(ode);
        double[] y = new double[] {1.0};
        double[] yDot = new double[1];
        integrator.computeDerivatives(0.0, y, yDot);
        integrator.computeDerivatives(0.0, y, yDot);
        assertEquals(2, integrator.getEvaluations());
    }

    // covers evaluations.incrementCount() throwing once the maximal count is exceeded
    @Test
    public void testComputeDerivatives_exceedsMaxEvaluations_throwsMaxCountExceededException() throws Throwable {
        ExpandableStatefulODE ode = new ExpandableStatefulODE(new SimpleEquations(1));
        ode.setTime(0.0);
        integrator.setEquations(ode);
        integrator.setMaxEvaluations(1);
        double[] y = new double[] {1.0};
        double[] yDot = new double[1];
        integrator.computeDerivatives(0.0, y, yDot);
        try {
            integrator.computeDerivatives(0.0, y, yDot);
            fail("expected MaxCountExceededException");
        } catch (MaxCountExceededException expected) {
            // expected
        }
    }

    // covers resetEvaluations() setting the counter back to zero after use
    @Test
    public void testResetEvaluations_afterCalls_resetsToZero() throws Throwable {
        ExpandableStatefulODE ode = new ExpandableStatefulODE(new SimpleEquations(1));
        ode.setTime(0.0);
        integrator.setEquations(ode);
        integrator.computeDerivatives(0.0, new double[] {1.0}, new double[1]);
        integrator.resetEvaluations();
        assertEquals(0, integrator.getEvaluations());
    }

    // covers sanityChecks throwing when dt is zero (too small integration span)
    @Test
    public void testSanityChecks_identicalTimes_throwsNumberIsTooSmallException() throws Throwable {
        ExpandableStatefulODE ode = new ExpandableStatefulODE(new SimpleEquations(1));
        ode.setTime(5.0);
        try {
            integrator.sanityChecks(ode, 5.0);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
            // expected
        }
    }

    // covers sanityChecks passing branch when dt is clearly above the threshold
    @Test
    public void testSanityChecks_sufficientSpan_doesNotThrow() throws Throwable {
        ExpandableStatefulODE ode = new ExpandableStatefulODE(new SimpleEquations(1));
        ode.setTime(0.0);
        integrator.sanityChecks(ode, 10.0);
        assertEquals(0.0, ode.getTime(), 1e-9);
    }

    // covers FastMath.abs handling negative dt direction (backward integration, t0 > t)
    @Test
    public void testSanityChecks_backwardIntegration_sufficientSpan_doesNotThrow() throws Throwable {
        ExpandableStatefulODE ode = new ExpandableStatefulODE(new SimpleEquations(1));
        ode.setTime(10.0);
        integrator.sanityChecks(ode, 0.0);
        assertEquals(10.0, ode.getTime(), 1e-9);
    }

    // covers y0.length != equations.getDimension() branch in the 5-arg integrate
    @Test
    public void testIntegrate_fiveArg_dimensionMismatchOnY0_throwsDimensionMismatchException() throws Throwable {
        FirstOrderDifferentialEquations eq = new SimpleEquations(2);
        double[] y0 = new double[] {1.0};
        double[] y = new double[2];
        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
            // expected
        }
    }

    // covers y.length != equations.getDimension() branch (reached after y0 check passes)
    @Test
    public void testIntegrate_fiveArg_dimensionMismatchOnY_throwsDimensionMismatchException() throws Throwable {
        FirstOrderDifferentialEquations eq = new SimpleEquations(2);
        double[] y0 = new double[2];
        double[] y = new double[] {1.0};
        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
            // expected
        }
    }

    // covers full success path: dimensions ok, delegates to abstract integrate, extracts results
    @Test
    public void testIntegrate_fiveArg_validInputs_returnsFinalTimeAndPopulatesY() throws Throwable {
        FirstOrderDifferentialEquations eq = new SimpleEquations(2);
        double[] y0 = new double[] {3.0, 4.0};
        double[] y = new double[2];
        double finalTime = integrator.integrate(eq, 0.0, y0, 5.0, y);
        assertEquals(5.0, finalTime, 1e-9);
        assertEquals(3.0, y[0], 1e-9);
        assertEquals(4.0, y[1], 1e-9);
    }

    // covers sanityChecks exception propagating through the abstract integrate call
    @Test
    public void testIntegrate_fiveArg_tooSmallSpan_throwsNumberIsTooSmallException() throws Throwable {
        FirstOrderDifferentialEquations eq = new SimpleEquations(1);
        double[] y0 = new double[] {1.0};
        double[] y = new double[1];
        try {
            integrator.integrate(eq, 2.0, y0, 2.0, y);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
            // expected
        }
    }
}
