package org.apache.commons.math.ode.nonstiff;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math.ode.DerivativeException;

public class EmbeddedRungeKuttaIntegratorClaudeTest {

    private Order2RK newOrder2RK(double minStep, double maxStep, double tol) {
        double[] c = {1.0};
        double[][] a = {{1.0}};
        double[] b = {0.5, 0.5};
        return new Order2RK(c, a, b, minStep, maxStep, tol, tol);
    }

    private Order2RK newOrder2RKVector(double minStep, double maxStep,
                                        double[] absTol, double[] relTol) {
        double[] c = {1.0};
        double[][] a = {{1.0}};
        double[] b = {0.5, 0.5};
        return new Order2RK(c, a, b, minStep, maxStep, absTol, relTol);
    }

    private FsalOrder1RK newFsalRK(double minStep, double maxStep, double tol) {
        double[] c = {1.0};
        double[][] a = {{1.0}};
        double[] b = {1.0, 0.0};
        return new FsalOrder1RK(c, a, b, minStep, maxStep, tol, tol);
    }

    // covers abstract getOrder() returning subclass-defined value (order=2)
    @Test
    public void testGetOrder_order2Integrator_returnsTwo() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        assertEquals(2, rk.getOrder());
    }

    // covers abstract getOrder() with a fsal-configured subclass (order=1)
    @Test
    public void testGetOrder_fsalIntegrator_returnsOne() throws Throwable {
        FsalOrder1RK rk = newFsalRK(1.0e-6, 1.0, 1.0e-8);
        assertEquals(1, rk.getOrder());
    }

    // covers default value set in constructor: setSafety(0.9)
    @Test
    public void testGetSafety_defaultConstructorValue_isPointNine() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        assertEquals(0.9, rk.getSafety(), 1.0e-9);
    }

    // covers setSafety with a typical in-range positive value
    @Test
    public void testSetSafety_positiveValue_updatesValue() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setSafety(0.5);
        assertEquals(0.5, rk.getSafety(), 1.0e-9);
    }

    // covers setSafety edge value 0 (boundary)
    @Test
    public void testSetSafety_zeroValue_storesZero() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setSafety(0.0);
        assertEquals(0.0, rk.getSafety(), 1.0e-9);
    }

    // covers setSafety with negative value (simple setter stores as-is)
    @Test
    public void testSetSafety_negativeValue_storesAsIs() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setSafety(-2.5);
        assertEquals(-2.5, rk.getSafety(), 1.0e-9);
    }

    // covers setSafety with value greater than one
    @Test
    public void testSetSafety_valueGreaterThanOne_storesAsIs() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setSafety(5.0);
        assertEquals(5.0, rk.getSafety(), 1.0e-9);
    }

    // covers default value set in constructor: setMinReduction(0.2)
    @Test
    public void testGetMinReduction_defaultConstructorValue_isPointTwo() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        assertEquals(0.2, rk.getMinReduction(), 1.0e-9);
    }

    // covers setMinReduction with typical value
    @Test
    public void testSetMinReduction_updatesValue() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setMinReduction(0.05);
        assertEquals(0.05, rk.getMinReduction(), 1.0e-9);
    }

    // covers setMinReduction with value greater than one (edge)
    @Test
    public void testSetMinReduction_valueGreaterThanOne_storesAsIs() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setMinReduction(3.0);
        assertEquals(3.0, rk.getMinReduction(), 1.0e-9);
    }

    // covers default value set in constructor: setMaxGrowth(10.0)
    @Test
    public void testGetMaxGrowth_defaultConstructorValue_isTen() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        assertEquals(10.0, rk.getMaxGrowth(), 1.0e-9);
    }

    // covers setMaxGrowth with typical value
    @Test
    public void testSetMaxGrowth_updatesValue() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setMaxGrowth(2.0);
        assertEquals(2.0, rk.getMaxGrowth(), 1.0e-9);
    }

    // covers setMaxGrowth with value less than one (edge)
    @Test
    public void testSetMaxGrowth_valueLessThanOne_storesAsIs() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setMaxGrowth(0.5);
        assertEquals(0.5, rk.getMaxGrowth(), 1.0e-9);
    }

    // covers the vector-tolerance constructor overload and its defaults
    @Test
    public void testConstructor_vectorToleranceOverload_defaultsMatch() throws Throwable {
        double[] tol = {1.0e-8};
        Order2RK rk = newOrder2RKVector(1.0e-6, 1.0, tol, tol);
        assertEquals(0.9, rk.getSafety(), 1.0e-9);
        assertEquals(2, rk.getOrder());
    }

    // covers forward branch (t > t0) and the accepted-step main loop path
    @Test
    public void testIntegrate_forwardConstantDerivative_returnsAnalyticalResult() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        ConstantDerivative eq = new ConstantDerivative(new double[] {3.0});
        double[] y0 = {0.0};
        double[] y = new double[1];
        rk.integrate(eq, 0.0, y0, 1.0, y);
        assertEquals(3.0, y[0], 1.0e-6);
    }

    // covers backward branch (t < t0, forward = false)
    @Test
    public void testIntegrate_backwardConstantDerivative_returnsAnalyticalResult() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        ConstantDerivative eq = new ConstantDerivative(new double[] {2.0});
        double[] y0 = {5.0};
        double[] y = new double[1];
        rk.integrate(eq, 1.0, y0, 0.0, y);
        assertEquals(3.0, y[0], 1.0e-6);
    }

    // covers vecAbsoluteTolerance/vecRelativeTolerance scale computation branch
    @Test
    public void testIntegrate_vectorTolerance_twoDimensionalSystem_matchesAnalytical() throws Throwable {
        double[] absTol = {1.0e-8, 1.0e-8};
        double[] relTol = {1.0e-8, 1.0e-8};
        Order2RK rk = newOrder2RKVector(1.0e-6, 1.0, absTol, relTol);
        ConstantDerivative eq = new ConstantDerivative(new double[] {1.0, -2.0});
        double[] y0 = {0.0, 10.0};
        double[] y = new double[2];
        rk.integrate(eq, 0.0, y0, 2.0, y);
        assertEquals(2.0, y[0], 1.0e-6);
        assertEquals(6.0, y[1], 1.0e-6);
    }

    // covers fsal=true branch: reuse of last stage derivative across multiple steps
    @Test
    public void testIntegrate_fsalMethodMultipleSteps_matchesAnalytical() throws Throwable {
        FsalOrder1RK rk = newFsalRK(1.0e-6, 0.1, 1.0e-8);
        ConstantDerivative eq = new ConstantDerivative(new double[] {4.0});
        double[] y0 = {0.0};
        double[] y = new double[1];
        rk.integrate(eq, 0.0, y0, 1.0, y);
        assertEquals(4.0, y[0], 1.0e-6);
    }

    // covers error > 1 rejection branch and stepsize reduction factor path
    @Test
    public void testIntegrate_stepRejectedOnce_stillReachesExactResult() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        rk.setRejectCount(1);
        ConstantDerivative eq = new ConstantDerivative(new double[] {7.0});
        double[] y0 = {0.0};
        double[] y = new double[1];
        rk.integrate(eq, 0.0, y0, 1.0, y);
        assertEquals(7.0, y[0], 1.0e-6);
    }

    // covers (y != y0) true branch: initial values copied into y, y0 left untouched
    @Test
    public void testIntegrate_yDifferentFromY0_doesNotMutateY0() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        ConstantDerivative eq = new ConstantDerivative(new double[] {5.0});
        double[] y0 = {1.0};
        double[] y = new double[1];
        rk.integrate(eq, 0.0, y0, 1.0, y);
        assertEquals(6.0, y[0], 1.0e-6);
        assertEquals(1.0, y0[0], 1.0e-9);
    }

    // covers (y != y0) false branch: same array reference updated in place
    @Test
    public void testIntegrate_ySameReferenceAsY0_inPlaceUpdate() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        ConstantDerivative eq = new ConstantDerivative(new double[] {5.0});
        double[] y0 = {1.0};
        rk.integrate(eq, 0.0, y0, 1.0, y0);
        assertEquals(6.0, y0[0], 1.0e-6);
    }

    // covers exception propagation path from computeDerivatives inside the main loop
    @Test
    public void testIntegrate_derivativeThrowsException_propagatesException() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        ThrowingEquations eq = new ThrowingEquations();
        double[] y0 = {0.0};
        double[] y = new double[1];
        try {
            rk.integrate(eq, 0.0, y0, 1.0, y);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("computeDerivatives"));
        }
    }

    // covers returned stopTime contract: result equals requested target time
    @Test
    public void testIntegrate_returnedStopTime_equalsTargetTime() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        ConstantDerivative eq = new ConstantDerivative(new double[] {1.0});
        double[] y0 = {0.0};
        double[] y = new double[1];
        double stop = rk.integrate(eq, 0.0, y0, 1.0, y);
        assertEquals(1.0, stop, 1.0e-6);
    }

    // covers stage computation when derivative sum is zero
    @Test
    public void testIntegrate_zeroDerivative_resultEqualsInitialValue() throws Throwable {
        Order2RK rk = newOrder2RK(1.0e-6, 1.0, 1.0e-8);
        ConstantDerivative eq = new ConstantDerivative(new double[] {0.0});
        double[] y0 = {9.5};
        double[] y = new double[1];
        rk.integrate(eq, 0.0, y0, 1.0, y);
        assertEquals(9.5, y[0], 1.0e-9);
    }

    static class Order2RK extends EmbeddedRungeKuttaIntegrator {
        private int rejectCount = 0;

        Order2RK(double[] c, double[][] a, double[] b,
                 double minStep, double maxStep,
                 double scalAbsTol, double scalRelTol) {
            super("Order2RK", false, c, a, b, null, minStep, maxStep, scalAbsTol, scalRelTol);
        }

        Order2RK(double[] c, double[][] a, double[] b,
                 double minStep, double maxStep,
                 double[] vecAbsTol, double[] vecRelTol) {
            super("Order2RK", false, c, a, b, null, minStep, maxStep, vecAbsTol, vecRelTol);
        }

        public int getOrder() {
            return 2;
        }

        public void setRejectCount(int n) {
            rejectCount = n;
        }

        protected double estimateError(double[][] yDotK, double[] y0, double[] y1, double h) {
            if (rejectCount > 0) {
                rejectCount--;
                return 2.0;
            }
            return 0.0;
        }
    }

    static class FsalOrder1RK extends EmbeddedRungeKuttaIntegrator {
        FsalOrder1RK(double[] c, double[][] a, double[] b,
                     double minStep, double maxStep,
                     double scalAbsTol, double scalRelTol) {
            super("FsalOrder1RK", true, c, a, b, null, minStep, maxStep, scalAbsTol, scalRelTol);
        }

        public int getOrder() {
            return 1;
        }

        protected double estimateError(double[][] yDotK, double[] y0, double[] y1, double h) {
            return 0.0;
        }
    }

    static class ConstantDerivative implements FirstOrderDifferentialEquations {
        private final double[] derivative;

        ConstantDerivative(double[] derivative) {
            this.derivative = derivative;
        }

        public int getDimension() {
            return derivative.length;
        }

        public void computeDerivatives(double t, double[] y, double[] yDot) throws DerivativeException {
            System.arraycopy(derivative, 0, yDot, 0, derivative.length);
        }
    }

    static class ThrowingEquations implements FirstOrderDifferentialEquations {
        public int getDimension() {
            return 1;
        }

        public void computeDerivatives(double t, double[] y, double[] yDot) throws DerivativeException {
            throw new IllegalStateException("computeDerivatives failure");
        }
    }
}
