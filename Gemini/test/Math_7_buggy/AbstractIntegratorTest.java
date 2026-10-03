package org.apache.commons.math3.ode;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.ode.events.EventHandler;
import org.apache.commons.math3.ode.sampling.StepHandler;
import org.apache.commons.math3.ode.sampling.StepInterpolator;

public class AbstractIntegratorTest {

    private static class DummyIntegrator extends AbstractIntegrator {
        public DummyIntegrator(String name) {
            super(name);
        }

        public void integrate(ExpandableStatefulODE equations, double t)
                throws NumberIsTooSmallException, DimensionMismatchException,
                MaxCountExceededException, NoBracketingException {
            sanityChecks(equations, t);
        }
    }

    private static class DummyEquations implements FirstOrderDifferentialEquations {
        public int getDimension() {
            return 2;
        }

        public void computeDerivatives(double t, double[] y, double[] yDot) {
            yDot[0] = y[1];
            yDot[1] = -y[0];
        }
    }

    private static class DummyStepHandler implements StepHandler {
        public void init(double t0, double[] y0, double t) {}
        public void handleStep(StepInterpolator interpolator, boolean isLast) {}
    }

    private static class DummyEventHandler implements EventHandler {
        public void init(double t0, double[] y0, double t) {}
        public double g(double t, double[] y) { return t - 1.0; }
        public Action eventOccurred(double t, double[] y, boolean increasing) { return Action.STOP; }
        public void resetState(double t, double[] y) {}
    }

    @Test
    public void testGetName() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("test-integrator");
        assertEquals("test-integrator", integrator.getName());
    }

    @Test
    public void testDefaultName() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator(null);
        assertNull(integrator.getName());
    }

    @Test
    public void testMaxEvaluations() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("eval-test");
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
        assertEquals(0, integrator.getEvaluations());

        integrator.setMaxEvaluations(100);
        assertEquals(100, integrator.getMaxEvaluations());

        integrator.setMaxEvaluations(-5);
        assertEquals(Integer.MAX_VALUE, integrator.getMaxEvaluations());
    }

    @Test
    public void testStepHandlers() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("handler-test");
        assertTrue(integrator.getStepHandlers().isEmpty());

        StepHandler handler = new DummyStepHandler();
        integrator.addStepHandler(handler);
        assertEquals(1, integrator.getStepHandlers().size());

        integrator.clearStepHandlers();
        assertTrue(integrator.getStepHandlers().isEmpty());
    }

    @Test
    public void testEventHandlers() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("event-test");
        assertTrue(integrator.getEventHandlers().isEmpty());

        EventHandler handler = new DummyEventHandler();
        integrator.addEventHandler(handler, 1.0, 1e-6, 100);
        assertEquals(1, integrator.getEventHandlers().size());

        integrator.clearEventHandlers();
        assertTrue(integrator.getEventHandlers().isEmpty());
    }

    @Test
    public void testSanityChecksException() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("sanity-test");
        FirstOrderDifferentialEquations eq = new DummyEquations();
        ExpandableStatefulODE expandable = new ExpandableStatefulODE(eq);
        expandable.setTime(0.0);
        expandable.setPrimaryState(new double[] { 1.0, 0.0 });

        boolean thrown = false;
        try {
            integrator.integrate(expandable, 0.0);
        } catch (NumberIsTooSmallException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testIntegrateArrayVersion() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("array-integrate");
        FirstOrderDifferentialEquations eq = new DummyEquations();
        double[] y0 = new double[] { 1.0, 0.0 };
        double[] y = new double[] { 0.0, 0.0 };

        double tEnd = integrator.integrate(eq, 0.0, y0, 0.1, y);
        assertEquals(0.1, tEnd, 1e-15);
    }

    @Test
    public void testDimensionMismatchDimensionY0() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("dim-test");
        FirstOrderDifferentialEquations eq = new DummyEquations();
        double[] y0 = new double[] { 1.0 };
        double[] y = new double[] { 0.0, 0.0 };

        boolean thrown = false;
        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
        } catch (DimensionMismatchException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testDimensionMismatchDimensionY() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("dim-test");
        FirstOrderDifferentialEquations eq = new DummyEquations();
        double[] y0 = new double[] { 1.0, 0.0 };
        double[] y = new double[] { 0.0 };

        boolean thrown = false;
        try {
            integrator.integrate(eq, 0.0, y0, 1.0, y);
        } catch (DimensionMismatchException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testStepStartAndSize() throws Throwable {
        AbstractIntegrator integrator = new DummyIntegrator("step-info");
        assertTrue(Double.isNaN(integrator.getCurrentStepStart()));
        assertTrue(Double.isNaN(integrator.getCurrentSignedStepsize()));
    }
}