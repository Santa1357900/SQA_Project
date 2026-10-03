package org.apache.commons.math3.ode;

import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.analysis.solvers.BracketingNthOrderBrentSolver;
import org.apache.commons.math3.analysis.solvers.UnivariateSolver;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.ode.events.EventHandler;
import org.apache.commons.math3.ode.sampling.StepHandler;
import org.apache.commons.math3.ode.sampling.StepInterpolator;

public class AbstractIntegratorClaudeTest {

    private SimpleIntegrator integrator;

    @Before
    public void setUp() throws Throwable {
        integrator = new SimpleIntegrator("testIntegrator");
    }

    private static class SimpleIntegrator extends AbstractIntegrator {
        public SimpleIntegrator(String name) {
            super(name);
        }
        public SimpleIntegrator() {
            super();
        }
        public void integrate(ExpandableStatefulODE equations, double t)
            throws NumberIsTooSmallException, DimensionMismatchException,
                   MaxCountExceededException, NoBracketingException {
            equations.setTime(t);
        }
    }

    private static class ConstantEquations implements FirstOrderDifferentialEquations {
        private final int dimension;
        public ConstantEquations(int dimension) {
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

    private static class RecordingStepHandler implements StepHandler {
        boolean initCalled = false;
        public void init(double t0, double[] y0, double t) {
            initCalled = true;
        }
        public void handleStep(StepInterpolator interpolator, boolean isLast)
            throws MaxCountExceededException {
            // not exercised in these tests
        }
    }

    // Constructor with name sets name field used by getName()
    @Test
    public void testConstructor_withName_getNameReturnsName() throws Throwable {
        SimpleIntegrator named = new SimpleIntegrator("myMethod");
        assertEquals("myMethod", named.getName());
    }

    // Protected no-arg constructor delegates to this(null)
    @Test
    public void testConstructor_protectedNoArg_getNameReturnsNull() throws Throwable {
        SimpleIntegrator noName = new SimpleIntegrator();
        assertNull(noName.getName());
    }

    // addStepHandler adds the handler, visible via getStepHandlers
    @Test
    public void testAddStepHandler_singleHandler_getStepHandlersContainsIt() throws Throwable {
        RecordingStepHandler handler = new RecordingStepHandler();
        integrator.addStepHandler(handler);
        Collection<StepHandler> handlers = integrator.getStepHandlers();
        assertEquals(1, handlers.size());
        assertTrue(handlers.contains(handler));
    }

    // getStepHandlers returns an unmodifiable view
    @Test
    public void testGetStepHandlers_returnedCollection_isUnmodifiable() throws Throwable {
        Collection<StepHandler> handlers = integrator.getStepHandlers();
        try {
            handlers.add(new RecordingStepHandler());
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // clearStepHandlers empties the collection after an add
    @Test
    public void testClearStepHandlers_afterAdd_collectionBecomesEmpty() throws Throwable {
        integrator.addStepHandler(new RecordingStepHandler());
        integrator.clearStepHandlers();
        assertTrue(integrator.getStepHandlers().isEmpty());
    }

    // 4-arg addEventHandler overload builds internal solver and stores the handler
    @Test
    public void testAddEventHandler_fourArgOverload_increasesEventHandlerCount() throws Throwable {
        assertEquals(0, integrator.getEventHandlers().size());
        integrator.addEventHandler((EventHandler) null, 1.0, 1e-10, 100);
        assertEquals(1, integrator.getEventHandlers().size());
    }

    // 5-arg addEventHandler overload uses the supplied solver directly
    @Test
    public void testAddEventHandler_fiveArgOverloadWithSolver_increasesEventHandlerCount() throws Throwable {
        UnivariateSolver solver = new BracketingNthOrderBrentSolver(1e-10, 5);
        assertEquals(0, integrator.getEventHandlers().size());
        integrator.addEventHandler((EventHandler) null, 1.0, 1e-10, 100, solver);
        assertEquals(1, integrator.getEventHandlers().size());
    }

    // getEventHandlers returns an unmodifiable view
    @Test
    public void testGetEventHandlers_returnedCollection_isUnmodifiable() throws Throwable {
        Collection<EventHandler> handlers = integrator.getEventHandlers();
        try {
            handlers.add((EventHandler) null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // clearEventHandlers empties the collection after an add
    @Test
    public void testClearEventHandlers_afterAdd_collectionBecomesEmpty() throws Throwable {
        integrator.addEventHandler((EventHandler) null, 1.0, 1e-10, 100);
        integrator.clearEventHandlers();
        assertTrue(integrator.getEventHandlers().isEmpty());
    }

    // stepStart is initialized to NaN in the constructor
    @Test
    public void testGetCurrentStepStart_initialState_isNaN() throws Throwable {
        assertTrue(Double.isNaN(integrator.getCurrentStepStart()));
    }

    // stepSize is initialized to NaN in the constructor
    @Test
    public void testGetCurrentSignedStepsize_initialState_isNaN() throws Throwable {
        assertTrue(Double.isNaN(integrator.getCurrentSignedStepsize()));
    }

    // Constructor calls setMaxEvaluations(-1) -> Integer.MAX_VALUE
    @Test
    public void testGetMaxEvaluations_initialState_isIntegerMaxValue() throws Throwable {
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
    }

    // Negative branch: maxEvaluations < 0 maps to Integer.MAX_VALUE
    @Test
    public void testSetMaxEvaluations_negativeValue_setsIntegerMaxValue() throws Throwable {
        integrator.setMaxEvaluations(-5);
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
    }

    // Boundary: zero is not < 0, so it is stored as-is
    @Test
    public void testSetMaxEvaluations_zeroValue_setsZeroNotMaxValue() throws Throwable {
        integrator.setMaxEvaluations(0);
        assertEquals(0, integrator.getMaxEvaluations());
    }

    // Positive branch: value stored unchanged
    @Test
    public void testSetMaxEvaluations_positiveValue_setsExactValue() throws Throwable {
        integrator.setMaxEvaluations(500);
        assertEquals(500, integrator.getMaxEvaluations());
    }

    // evaluations counter starts at zero
    @Test
    public void testGetEvaluations_initialState_isZero() throws Throwable {
        assertEquals(0, integrator.getEvaluations());
    }

    // initIntegration with empty handler/event collections resets evaluation count
    @Test
    public void testInitIntegration_withNoHandlersOrEvents_resetsEvaluationsToZero() throws Throwable {
        ExpandableStatefulODE eq = new ExpandableStatefulODE(new ConstantEquations(1));
        integrator.setEquations(eq);
        double[] y = new double[] {0.0};
        double[] yDot = new double[1];
        integrator.computeDerivatives(0.0, y, yDot);
        integrator.computeDerivatives(0.0, y, yDot);
        assertEquals(2, integrator.getEvaluations());
        integrator.initIntegration(0.0, y, 1.0);
        assertEquals(0, integrator.getEvaluations());
    }

    // initIntegration loop over stepHandlers (1 round) calls handler.init
    @Test
    public void testInitIntegration_withStepHandler_callsHandlerInit() throws Throwable {
        RecordingStepHandler handler = new RecordingStepHandler();
        integrator.addStepHandler(handler);
        assertFalse(handler.initCalled);
        integrator.initIntegration(0.0, new double[] {1.0}, 2.0);
        assertTrue(handler.initCalled);
    }



    // integrate(...) throws DimensionMismatchException when y0.length mismatches equations dimension
    @Test
    public void testIntegrate_y0DimensionMismatch_throwsDimensionMismatchException() throws Throwable {
        ConstantEquations eq = new ConstantEquations(2);
        double[] y0 = new double[] {1.0};
        double[] y = new double[2];
        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
        }
    }

    // integrate(...) throws DimensionMismatchException when y.length mismatches equations dimension
    @Test
    public void testIntegrate_yDimensionMismatch_throwsDimensionMismatchException() throws Throwable {
        ConstantEquations eq = new ConstantEquations(2);
        double[] y0 = new double[] {1.0, 2.0};
        double[] y = new double[1];
        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
        }
    }

    // integrate(...) with matching dimensions returns final time and copies back state
    @Test
    public void testIntegrate_validDimensions_returnsTargetTimeAndCopiesState() throws Throwable {
        ConstantEquations eq = new ConstantEquations(1);
        double[] y0 = new double[] {5.0};
        double[] y = new double[1];
        double result = integrator.integrate(eq, 0.0, y0, 3.0, y);
        assertEquals(3.0, result, 1e-9);
        assertEquals(5.0, y[0], 1e-9);
    }

    // computeDerivatives increments the evaluations counter
    @Test
    public void testComputeDerivatives_incrementsEvaluationCount() throws Throwable {
        ExpandableStatefulODE eq = new ExpandableStatefulODE(new ConstantEquations(1));
        integrator.setEquations(eq);
        double[] y = new double[] {0.0};
        double[] yDot = new double[1];
        integrator.computeDerivatives(0.0, y, yDot);
        assertEquals(1, integrator.getEvaluations());
    }

    // computeDerivatives without setEquations dereferences a null expandable field
    @Test
    public void testComputeDerivatives_withoutSetEquations_throwsNullPointerException() throws Throwable {
        double[] y = new double[] {0.0};
        double[] yDot = new double[1];
        try {
            integrator.computeDerivatives(0.0, y, yDot);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // computeDerivatives delegates to the configured equations and fills yDot correctly
    @Test
    public void testComputeDerivatives_computesExpectedDerivativeValues() throws Throwable {
        ExpandableStatefulODE eq = new ExpandableStatefulODE(new ConstantEquations(2));
        integrator.setEquations(eq);
        double[] y = new double[] {0.0, 0.0};
        double[] yDot = new double[2];
        integrator.computeDerivatives(0.0, y, yDot);
        assertEquals(1.0, yDot[0], 1e-9);
        assertEquals(1.0, yDot[1], 1e-9);
    }

    // sanityChecks: dt <= threshold (same time, dt = 0) must throw NumberIsTooSmallException
    @Test
    public void testSanityChecks_dtAtOrBelowThreshold_throwsNumberIsTooSmallException() throws Throwable {
        ExpandableStatefulODE eq = new ExpandableStatefulODE(new ConstantEquations(1));
        eq.setTime(0.0);
        try {
            integrator.sanityChecks(eq, 0.0);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
        }
    }

    // sanityChecks: dt clearly above threshold does not throw and leaves equations' time unchanged
    @Test
    public void testSanityChecks_dtAboveThreshold_doesNotThrowAndEquationsUnchanged() throws Throwable {
        ExpandableStatefulODE eq = new ExpandableStatefulODE(new ConstantEquations(1));
        eq.setTime(0.0);
        integrator.sanityChecks(eq, 10.0);
        assertEquals(0.0, eq.getTime(), 1e-9);
    }
}
