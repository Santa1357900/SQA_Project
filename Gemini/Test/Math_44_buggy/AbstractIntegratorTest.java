package org.apache.commons.math.ode;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;

import org.apache.commons.math.exception.DimensionMismatchException;
import org.apache.commons.math.exception.NumberIsTooSmallException;
import org.apache.commons.math.ode.events.EventHandler;
import org.apache.commons.math.ode.sampling.StepHandler;
import org.apache.commons.math.ode.sampling.StepInterpolator;

public class AbstractIntegratorTest {

    private static class DummyIntegrator extends AbstractIntegrator {
        public DummyIntegrator(String name) {
            super(name);
        }

        @Override
        public void integrate(ExpandableStatefulODE equations, double t)
            throws DimensionMismatchException, NumberIsTooSmallException {
            sanityChecks(equations, t);
        }
    }

    private static class DummyEquations implements FirstOrderDifferentialEquations {
        public int getDimension() {
            return 2;
        }

        public void computeDerivatives(double t, double[] y, double[] yDot) {
            yDot[0] = 1.0;
            yDot[1] = -1.0;
        }
    }

    private static class DummyStepHandler implements StepHandler {
        private boolean handled = false;

        public void init(double t0, double[] y0, double t) {
            handled = false;
        }

        public void handleStep(StepInterpolator interpolator, boolean isLast) {
            handled = true;
        }

        public boolean isHandled() {
            return handled;
        }
    }

    private static class DummyEventHandler implements EventHandler {
        public void init(double t0, double[] y0, double t) {}
        public double g(double t, double[] y) { return 1.0; }
        public Action eventOccurred(double t, double[] y, boolean increasing) { return Action.CONTINUE; }
        public void resetState(double t, double[] y) {}
    }

    @Test
    public void testGetName() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("TestIntegrator");
        assertEquals("TestIntegrator", integrator.getName());
    }

    @Test
    public void testDefaultName() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator(null);
        assertNull(integrator.getName());
    }

    @Test
    public void testMaxEvaluations() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("Test");
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
        
        integrator.setMaxEvaluations(100);
        assertEquals(100, integrator.getMaxEvaluations());

        integrator.setMaxEvaluations(-5);
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
    }

    @Test
    public void testStepHandlers() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("Test");
        DummyStepHandler handler = new DummyStepHandler();
        
        assertTrue(integrator.getStepHandlers().isEmpty());
        
        integrator.addStepHandler(handler);
        Collection<StepHandler> handlers = integrator.getStepHandlers();
        assertEquals(1, handlers.size());
        
        integrator.clearStepHandlers();
        assertTrue(integrator.getStepHandlers().isEmpty());
    }

    @Test
    public void testEventHandlers() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("Test");
        DummyEventHandler handler = new DummyEventHandler();
        
        assertTrue(integrator.getEventHandlers().isEmpty());
        
        integrator.addEventHandler(handler, 1.0, 1e-6, 100);
        Collection<EventHandler> handlers = integrator.getEventHandlers();
        assertEquals(1, handlers.size());
        
        integrator.clearEventHandlers();
        assertTrue(integrator.getEventHandlers().isEmpty());
    }

    @Test
    public void testIntegrateValidationDimensionMismatchY0() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("Test");
        FirstOrderDifferentialEquations eq = new DummyEquations();
        double[] y0 = new double[1]; 
        double[] y = new double[2];

        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertEquals(1, e.getArgument());
            assertEquals(2, e.getDimension());
        }
    }

    @Test
    public void testIntegrateValidationDimensionMismatchY() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("Test");
        FirstOrderDifferentialEquations eq = new DummyEquations();
        double[] y0 = new double[2];
        double[] y = new double[1]; 

        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertEquals(1, e.getArgument());
            assertEquals(2, e.getDimension());
        }
    }

    @Test
    public void testSanityChecksTooSmallInterval() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("Test");
        ExpandableStatefulODE expandable = new ExpandableStatefulODE(new DummyEquations());
        expandable.setTime(0.0);

        try {
            integrator.integrate(expandable, 0.0);
            fail("Expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException e) {
            assertTrue(true);
        }
    }
}