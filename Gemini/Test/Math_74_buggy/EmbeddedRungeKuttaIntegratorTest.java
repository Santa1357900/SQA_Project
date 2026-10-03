package org.apache.commons.math.ode.nonstiff;

import junit.framework.TestCase;
import org.apache.commons.math.ode.DerivativeException;
import org.apache.commons.math.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math.ode.IntegratorException;
import org.apache.commons.math.ode.sampling.StepHandler;
import org.apache.commons.math.ode.sampling.StepInterpolator;

public class EmbeddedRungeKuttaIntegratorTest extends TestCase {

    private static class DummyODE implements FirstOrderDifferentialEquations {
        private final int dimension;

        public DummyODE(int dimension) {
            this.dimension = dimension;
        }

        public int getDimension() {
            return dimension;
        }

        public void computeDerivatives(double t, double[] y, double[] yDot) throws DerivativeException {
            for (int i = 0; i < dimension; ++i) {
                yDot[i] = y[i];
            }
        }
    }

    private static class ConcreteEmbeddedRungeKuttaIntegrator extends EmbeddedRungeKuttaIntegrator {
        public ConcreteEmbeddedRungeKuttaIntegrator(boolean fsal, double[] c, double[][] a, double[] b,
                                                    RungeKuttaStepInterpolator prototype,
                                                    double minStep, double maxStep,
                                                    double scalAbsoluteTolerance, double scalRelativeTolerance) {
            super("TestRK", fsal, c, a, b, prototype, minStep, maxStep, scalAbsoluteTolerance, scalRelativeTolerance);
        }

        public ConcreteEmbeddedRungeKuttaIntegrator(boolean fsal, double[] c, double[][] a, double[] b,
                                                    RungeKuttaStepInterpolator prototype,
                                                    double minStep, double maxStep,
                                                    double[] vecAbsoluteTolerance, double[] vecRelativeTolerance) {
            super("TestRK", fsal, c, a, b, prototype, minStep, maxStep, vecAbsoluteTolerance, vecRelativeTolerance);
        }

        public int getOrder() {
            return 2;
        }

        protected double estimateError(double[][] yDotK, double[] y0, double[] y1, double h) {
            return 0.1;
        }
    }

    public void testGettersAndSetters() throws Throwable {
        double[] c = new double[] { 0.5 };
        double[][] a = new double[][] { { 0.5 } };
        double[] b = new double[] { 0.0, 1.0 };
        RungeKuttaStepInterpolator prototype = new DormandPrince54StepInterpolator();

        ConcreteEmbeddedRungeKuttaIntegrator integrator = new ConcreteEmbeddedRungeKuttaIntegrator(
                true, c, a, b, prototype, 1.0e-12, 10.0, 1.0e-5, 1.0e-5
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

    public void testIntegrationScalarTolerance() throws Throwable {
        double[] c = new double[] { 0.5 };
        double[][] a = new double[][] { { 0.5 } };
        double[] b = new double[] { 0.0, 1.0 };
        RungeKuttaStepInterpolator prototype = new DormandPrince54StepInterpolator();

        ConcreteEmbeddedRungeKuttaIntegrator integrator = new ConcreteEmbeddedRungeKuttaIntegrator(
                true, c, a, b, prototype, 1.0e-12, 10.0, 1.0e-5, 1.0e-5
        );

        DummyODE ode = new DummyODE(1);
        double[] y0 = new double[] { 1.0 };
        double[] y = new double[] { 0.0 };

        double tEnd = integrator.integrate(ode, 0.0, y0, 1.0, y);
        assertEquals(1.0, tEnd, 1.0e-12);
    }

    public void testIntegrationVectorTolerance() throws Throwable {
        double[] c = new double[] { 0.5 };
        double[][] a = new double[][] { { 0.5 } };
        double[] b = new double[] { 0.0, 1.0 };
        RungeKuttaStepInterpolator prototype = new DormandPrince54StepInterpolator();

        double[] absTol = new double[] { 1.0e-5 };
        double[] relTol = new double[] { 1.0e-5 };

        ConcreteEmbeddedRungeKuttaIntegrator integrator = new ConcreteEmbeddedRungeKuttaIntegrator(
                false, c, a, b, prototype, 1.0e-12, 10.0, absTol, relTol
        );

        DummyODE ode = new DummyODE(1);
        double[] y0 = new double[] { 1.0 };
        double[] y = new double[] { 0.0 };

        double tEnd = integrator.integrate(ode, 0.0, y0, 1.0, y);
        assertEquals(1.0, tEnd, 1.0e-12);
    }

    public void testIntegrationWithStepHandler() throws Throwable {
        double[] c = new double[] { 0.5 };
        double[][] a = new double[][] { { 0.5 } };
        double[] b = new double[] { 0.0, 1.0 };
        RungeKuttaStepInterpolator prototype = new DormandPrince54StepInterpolator();

        ConcreteEmbeddedRungeKuttaIntegrator integrator = new ConcreteEmbeddedRungeKuttaIntegrator(
                true, c, a, b, prototype, 1.0e-12, 10.0, 1.0e-5, 1.0e-5
        );

        integrator.addStepHandler(new StepHandler() {
            public void reset() {
            }

            public void handleStep(StepInterpolator interpolator, boolean isLast) throws DerivativeException {
                double t = interpolator.getCurrentTime();
                assertTrue(!Double.isNaN(t));
            }

            public boolean requiresDenseOutput() {
                return true;
            }
        });

        DummyODE ode = new DummyODE(1);
        double[] y0 = new double[] { 1.0 };
        double[] y = new double[] { 0.0 };

        double tEnd = integrator.integrate(ode, 0.0, y0, 0.5, y);
        assertEquals(0.5, tEnd, 1.0e-12);
    }
}