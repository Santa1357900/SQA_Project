package org.apache.commons.math.ode.nonstiff;

import org.apache.commons.math.ode.ExpandableStatefulODE;
import org.apache.commons.math.ode.FirstOrderDifferentialEquations;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EmbeddedRungeKuttaIntegratorTest {

    private static class DummyIntegrator extends EmbeddedRungeKuttaIntegrator {
        protected DummyIntegrator(boolean fsal, double[] c, double[][] a, double[] b,
                                  RungeKuttaStepInterpolator prototype,
                                  double minStep, double maxStep,
                                  double scalAbsoluteTolerance, double scalRelativeTolerance) {
            super("Dummy", fsal, c, a, b, prototype, minStep, maxStep, scalAbsoluteTolerance, scalRelativeTolerance);
        }

        protected DummyIntegrator(boolean fsal, double[] c, double[][] a, double[] b,
                                  RungeKuttaStepInterpolator prototype,
                                  double minStep, double maxStep,
                                  double[] vecAbsoluteTolerance, double[] vecRelativeTolerance) {
            super("Dummy", fsal, c, a, b, prototype, minStep, maxStep, vecAbsoluteTolerance, vecRelativeTolerance);
        }

        public int getOrder() {
            return 4;
        }

        protected double estimateError(double[][] yDotK, double[] y0, double[] y1, double h) {
            return 0.1;
        }
    }

    private static class DummyEquations implements FirstOrderDifferentialEquations {
        public int getDimension() {
            return 1;
        }

        public void computeDerivatives(double t, double[] y, double[] yDot) {
            yDot[0] = y[0];
        }
    }

    @Test
    public void testParametersAndGettersSetters() throws Throwable {
        double[] c = new double[] { 0.5 };
        double[][] a = new double[][] { { 0.5 } };
        double[] b = new double[] { 0.2, 0.8 };
        RungeKuttaStepInterpolator prototype = new DormandPrince54StepInterpolator();

        DummyIntegrator integrator = new DummyIntegrator(
            true, c, a, b, prototype,
            1.0e-10, 100.0, 1.0e-6, 1.0e-6
        );

        assertEquals(0.9, integrator.getSafety(), 1.0e-12);
        integrator.setSafety(0.8);
        assertEquals(0.8, integrator.getSafety(), 1.0e-12);

        assertEquals(0.2, integrator.getMinReduction(), 1.0e-12);
        integrator.setMinReduction(0.1);
        assertEquals(0.1, integrator.getMinReduction(), 1.0e-12);

        assertEquals(10.0, integrator.getMaxGrowth(), 1.0e-12);
        integrator.setMaxGrowth(5.0);
        assertEquals(5.0, integrator.getMaxGrowth(), 1.0e-12);
    }

    @Test
    public void testIntegrationWithScalarTolerance() throws Throwable {
        double[] c = new double[] { 0.5 };
        double[][] a = new double[][] { { 0.5 } };
        double[] b = new double[] { 0.5, 0.5 };
        RungeKuttaStepInterpolator prototype = new DormandPrince54StepInterpolator();

        DummyIntegrator integrator = new DummyIntegrator(
            false, c, a, b, prototype,
            1.0e-10, 100.0, 1.0e-6, 1.0e-6
        );

        ExpandableStatefulODE expandable = new ExpandableStatefulODE(new DummyEquations());
        expandable.setTime(0.0);
        expandable.setCompleteState(new double[] { 1.0 });

        integrator.integrate(expandable, 0.1);
        assertEquals(0.1, expandable.getTime(), 1.0e-12);
        assertNotNull(expandable.getCompleteState());
    }

    @Test
    public void testIntegrationWithVectorTolerance() throws Throwable {
        double[] c = new double[] { 0.5 };
        double[][] a = new double[][] { { 0.5 } };
        double[] b = new double[] { 0.5, 0.5 };
        RungeKuttaStepInterpolator prototype = new DormandPrince54StepInterpolator();

        double[] vecAbsoluteTolerance = new double[] { 1.0e-6 };
        double[] vecRelativeTolerance = new double[] { 1.0e-6 };

        DummyIntegrator integrator = new DummyIntegrator(
            true, c, a, b, prototype,
            1.0e-10, 100.0, vecAbsoluteTolerance, vecRelativeTolerance
        );

        ExpandableStatefulODE expandable = new ExpandableStatefulODE(new DummyEquations());
        expandable.setTime(0.0);
        expandable.setCompleteState(new double[] { 1.0 });

        integrator.integrate(expandable, -0.1);
        assertEquals(-0.1, expandable.getTime(), 1.0e-12);
        assertNotNull(expandable.getCompleteState());
    }
}