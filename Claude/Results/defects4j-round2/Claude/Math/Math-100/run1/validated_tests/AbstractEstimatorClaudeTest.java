package org.apache.commons.math.estimation;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AbstractEstimatorClaudeTest {

    private ConcreteEstimator estimator;

    @Before
    public void setUp() throws Throwable {
        estimator = new ConcreteEstimator();
    }

    // getCostEvaluations: initial value before any update must be 0
    @Test
    public void testGetCostEvaluations_initial_returnsZero() throws Throwable {
        assertEquals(0, estimator.getCostEvaluations());
    }

    // getJacobianEvaluations: initial value before any update must be 0
    @Test
    public void testGetJacobianEvaluations_initial_returnsZero() throws Throwable {
        assertEquals(0, estimator.getJacobianEvaluations());
    }

    // updateResidualsAndCost: single measurement, normal path, correct residual and cost
    @Test
    public void testUpdateResidualsAndCost_withinLimit_computesCorrectCostAndResidual() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.setMaxCostEval(5);
        estimator.updateResidualsAndCost();

        assertEquals(2.0, estimator.residuals[0], 1e-9);
        assertEquals(2.0, estimator.cost, 1e-9);
        assertEquals(1, estimator.getCostEvaluations());
    }

    // updateResidualsAndCost: multiple measurements, loop sums weighted squared residuals
    @Test
    public void testUpdateResidualsAndCost_multipleMeasurements_sumsWeightedSquaredResiduals() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(2.0, 7.0, 4.0, params, new double[] { 1.0 });
        WeightedMeasurement m2 = new CustomMeasurement(3.0, 9.0, 5.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1, m2 }, params);

        estimator.initializeEstimate(problem);
        estimator.setMaxCostEval(5);
        estimator.updateResidualsAndCost();

        assertEquals(Math.sqrt(66.0), estimator.cost, 1e-6);
        assertEquals(Math.sqrt(2.0) * 3.0, estimator.residuals[0], 1e-6);
        assertEquals(Math.sqrt(3.0) * 4.0, estimator.residuals[1], 1e-6);
    }

    // updateResidualsAndCost: counter increments once per call
    @Test
    public void testUpdateResidualsAndCost_incrementsCostEvaluationsCounter() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.setMaxCostEval(10);
        estimator.updateResidualsAndCost();
        estimator.updateResidualsAndCost();

        assertEquals(2, estimator.getCostEvaluations());
    }

    // updateResidualsAndCost: exceeding maxCostEval on a later call throws EstimationException
    @Test
    public void testUpdateResidualsAndCost_exceedsMaxCostEval_throwsEstimationException() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.setMaxCostEval(1);
        estimator.updateResidualsAndCost();
        try {
            estimator.updateResidualsAndCost();
            fail("expected EstimationException");
        } catch (EstimationException expected) {
            // expected
        }
    }

    // updateResidualsAndCost: boundary maxCostEval=0 throws on the very first call
    @Test
    public void testUpdateResidualsAndCost_maxCostEvalZero_throwsImmediately() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.setMaxCostEval(0);
        try {
            estimator.updateResidualsAndCost();
            fail("expected EstimationException");
        } catch (EstimationException expected) {
            // expected
        }
    }

    // updateResidualsAndCost: negative maxCostEval also throws immediately
    @Test
    public void testUpdateResidualsAndCost_negativeMaxCostEval_throwsImmediately() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.setMaxCostEval(-1);
        try {
            estimator.updateResidualsAndCost();
            fail("expected EstimationException");
        } catch (EstimationException expected) {
            // expected
        }
    }

    // updateResidualsAndCost: calls exactly equal to maxCostEval do not throw (boundary of > vs <=)
    @Test
    public void testUpdateResidualsAndCost_atExactMaxCostEval_doesNotThrow() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.setMaxCostEval(2);
        estimator.updateResidualsAndCost();
        estimator.updateResidualsAndCost();

        assertEquals(2, estimator.getCostEvaluations());
    }

    // updateJacobian: counter increments once per call
    @Test
    public void testUpdateJacobian_incrementsJacobianEvaluationsCounter() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.updateJacobian();
        estimator.updateJacobian();

        assertEquals(2, estimator.getJacobianEvaluations());
    }

    // updateJacobian: single row/column computes factor = -sqrt(weight) * partial
    @Test
    public void testUpdateJacobian_singleMeasurementSingleParameter_computesCorrectValue() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(9.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.updateJacobian();

        assertEquals(-3.0, estimator.jacobian[0], 1e-9);
    }

    // updateJacobian: zero measurements -> jacobian array has length 0 (loop runs 0 times)
    @Test
    public void testUpdateJacobian_zeroMeasurements_emptyJacobian() throws Throwable {
        EstimatedParameter[] params = new EstimatedParameter[0];
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[0], params);

        estimator.initializeEstimate(problem);
        estimator.updateJacobian();

        assertEquals(0, estimator.jacobian.length);
        assertEquals(1, estimator.getJacobianEvaluations());
    }

    // updateJacobian: zero parameters -> inner loop runs 0 times, jacobian still length 0
    @Test
    public void testUpdateJacobian_zeroParameters_emptyJacobian() throws Throwable {
        EstimatedParameter[] params = new EstimatedParameter[0];
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[0]);
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        estimator.updateJacobian();

        assertEquals(0, estimator.jacobian.length);
    }

    // updateJacobian: multiple rows/columns, verifies index flattening across both loops
    @Test
    public void testUpdateJacobian_multipleMeasurementsAndParameters_computesCorrectValues() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter p2 = new EstimatedParameter("p2", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1, p2 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 0.0, 0.0, params, new double[] { 1.0, 0.0 });
        WeightedMeasurement m2 = new CustomMeasurement(1.0, 0.0, 0.0, params, new double[] { 0.0, 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1, m2 }, params);

        estimator.initializeEstimate(problem);
        estimator.updateJacobian();

        assertEquals(-1.0, estimator.jacobian[0], 1e-9);
        assertEquals(0.0, estimator.jacobian[1], 1e-9);
        assertEquals(0.0, estimator.jacobian[2], 1e-9);
        assertEquals(-1.0, estimator.jacobian[3], 1e-9);
    }

    // getRMS: single measurement, RMS = sqrt(criterion / n) per Javadoc
    @Test
    public void testGetRMS_singleMeasurement_returnsSqrtOfCriterionOverN() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 8.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        assertEquals(5.0, estimator.getRMS(problem), 1e-9);
    }

    // getRMS: multiple measurements, criterion accumulates weight*residual^2 over all of them
    @Test
    public void testGetRMS_multipleMeasurements_returnsCorrectValue() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(2.0, 7.0, 4.0, params, new double[] { 1.0 });
        WeightedMeasurement m2 = new CustomMeasurement(2.0, 7.0, 4.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1, m2 }, params);

        assertEquals(Math.sqrt(18.0), estimator.getRMS(problem), 1e-9);
    }





    // getCovariances: single parameter, inverse of transpose(J).J
    @Test
    public void testGetCovariances_singleParameter_returnsCorrectInverse() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(4.0, 12.0, 10.0, params, new double[] { 1.0 });
        WeightedMeasurement m2 = new CustomMeasurement(4.0, 12.0, 10.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1, m2 }, params);

        estimator.initializeEstimate(problem);
        double[][] covar = estimator.getCovariances(problem);

        assertEquals(0.125, covar[0][0], 1e-9);
    }

    // getCovariances: two parameters, verifies off-diagonal and symmetric computation
    @Test
    public void testGetCovariances_twoParameters_returnsCorrectDiagonalInverse() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter p2 = new EstimatedParameter("p2", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1, p2 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 0.0, 0.0, params, new double[] { 2.0, 0.0 });
        WeightedMeasurement m2 = new CustomMeasurement(1.0, 0.0, 0.0, params, new double[] { 0.0, 3.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1, m2 }, params);

        estimator.initializeEstimate(problem);
        double[][] covar = estimator.getCovariances(problem);

        assertEquals(0.25, covar[0][0], 1e-9);
        assertEquals(1.0 / 9.0, covar[1][1], 1e-9);
        assertEquals(0.0, covar[0][1], 1e-9);
        assertEquals(0.0, covar[1][0], 1e-9);
    }

    // getCovariances: singular transpose(J).J matrix (zero weight) throws EstimationException
    @Test
    public void testGetCovariances_singularProblem_throwsEstimationException() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(0.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        estimator.initializeEstimate(problem);
        try {
            estimator.getCovariances(problem);
            fail("expected EstimationException");
        } catch (EstimationException expected) {
            // expected
        }
    }

    // guessParametersErrors: measurements <= parameters -> no degrees of freedom, throws
    @Test
    public void testGuessParametersErrors_insufficientDegreesOfFreedom_throwsEstimationException() throws Throwable {
        EstimatedParameter p1 = new EstimatedParameter("p1", 1.0);
        EstimatedParameter[] params = new EstimatedParameter[] { p1 };
        WeightedMeasurement m1 = new CustomMeasurement(1.0, 5.0, 3.0, params, new double[] { 1.0 });
        SimpleProblem problem = new SimpleProblem(new WeightedMeasurement[] { m1 }, params);

        try {
            estimator.guessParametersErrors(problem);
            fail("expected EstimationException");
        } catch (EstimationException expected) {
            // expected
        }
    }



    /** Concrete subclass needed because AbstractEstimator.estimate is abstract. */
    private static class ConcreteEstimator extends AbstractEstimator {
        public void estimate(EstimationProblem problem) throws EstimationException {
            initializeEstimate(problem);
        }
    }

    /** Minimal EstimationProblem backed by fixed arrays. */
    private static class SimpleProblem implements EstimationProblem {
        private final WeightedMeasurement[] measurements;
        private final EstimatedParameter[] parameters;

        SimpleProblem(WeightedMeasurement[] measurements, EstimatedParameter[] parameters) {
            this.measurements = measurements;
            this.parameters = parameters;
        }

        public WeightedMeasurement[] getMeasurements() {
            return measurements;
        }

        public EstimatedParameter[] getAllParameters() {
            return parameters;
        }

        public EstimatedParameter[] getUnboundParameters() {
            return parameters;
        }
    }

    /** WeightedMeasurement with fully configurable theoretical value and per-parameter partials. */
    private static class CustomMeasurement extends WeightedMeasurement {
        private final double theoretical;
        private final EstimatedParameter[] refParams;
        private final double[] partials;

        CustomMeasurement(double weight, double measuredValue, double theoretical,
                           EstimatedParameter[] refParams, double[] partials) {
            super(weight, measuredValue);
            this.theoretical = theoretical;
            this.refParams = refParams;
            this.partials = partials;
        }

        public double getTheoreticalValue() {
            return theoretical;
        }

        public double getPartial(EstimatedParameter parameter) {
            for (int i = 0; i < refParams.length; i++) {
                if (refParams[i] == parameter) {
                    return partials[i];
                }
            }
            return 0.0;
        }
    }
}
