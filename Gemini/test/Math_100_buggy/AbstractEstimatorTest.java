package org.apache.commons.math.estimation;

import junit.framework.TestCase;
import org.apache.commons.math.linear.InvalidMatrixException;

public class AbstractEstimatorTest extends TestCase {

    private static class ConcreteEstimator extends AbstractEstimator {
        @Override
        public void estimate(EstimationProblem problem) throws EstimationException {
            initializeEstimate(problem);
            updateResidualsAndCost();
            updateJacobian();
        }

        public void publicInitializeEstimate(EstimationProblem problem) {
            initializeEstimate(problem);
        }

        public void publicUpdateResidualsAndCost() throws EstimationException {
            updateResidualsAndCost();
        }

        public void publicUpdateJacobian() {
            updateJacobian();
        }

        public double getCost() {
            return cost;
        }

        public double[] getResiduals() {
            return residuals;
        }
    }

    private static class DummyParameter extends EstimatedParameter {
        private static final long serialVersionUID = 1L;
        private double val;

        public DummyParameter(String name, double value) {
            super(name, value);
            this.val = value;
        }

        @Override
        public void setEstimate(double estimate) {
            this.val = estimate;
        }

        @Override
        public double getEstimate() {
            return val;
        }
    }

    private static class DummyMeasurement extends WeightedMeasurement {
        private static final long serialVersionUID = 1L;
        private final double residual;
        private final double weight;
        private final EstimatedParameter param;
        private final double partial;

        public DummyMeasurement(double weight, double residual, EstimatedParameter param, double partial) {
            super(weight, residual);
            this.weight = weight;
            this.residual = residual;
            this.param = param;
            this.partial = partial;
        }

        @Override
        public double getResidual() {
            return residual;
        }

        @Override
        public double getPartial(EstimatedParameter parameter) {
            if (parameter == param) {
                return partial;
            }
            return 0.0;
        }
    }

    private static class DummyProblem implements EstimationProblem {
        private final WeightedMeasurement[] measurements;
        private final EstimatedParameter[] parameters;

        public DummyProblem(WeightedMeasurement[] measurements, EstimatedParameter[] parameters) {
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

    public void testCostEvaluationsAndMaxEval() throws Throwable {
        ConcreteEstimator estimator = new ConcreteEstimator();
        estimator.setMaxCostEval(1);

        EstimatedParameter p = new DummyParameter("p1", 1.0);
        WeightedMeasurement m = new DummyMeasurement(1.0, 2.0, p, 1.0);
        DummyProblem problem = new DummyProblem(
            new WeightedMeasurement[] { m },
            new EstimatedParameter[] { p }
        );

        estimator.publicInitializeEstimate(problem);
        
        // First evaluation should pass
        estimator.publicUpdateResidualsAndCost();
        assertEquals(1, estimator.getCostEvaluations());

        // Second evaluation should exceed maxCostEval (1) and throw EstimationException
        try {
            estimator.publicUpdateResidualsAndCost();
            fail("Expected EstimationException");
        } catch (EstimationException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testGetRMSAndChiSquare() throws Throwable {
        ConcreteEstimator estimator = new ConcreteEstimator();
        EstimatedParameter p = new DummyParameter("p1", 1.0);
        WeightedMeasurement m1 = new DummyMeasurement(2.0, 3.0, p, 1.0);
        WeightedMeasurement m2 = new DummyMeasurement(4.0, 5.0, p, 1.0);
        
        DummyProblem problem = new DummyProblem(
            new WeightedMeasurement[] { m1, m2 },
            new EstimatedParameter[] { p }
        );

        // criterion = 2.0 * 3.0^2 + 4.0 * 5.0^2 = 2 * 9 + 4 * 25 = 18 + 100 = 118
        // RMS = sqrt(118 / 2) = sqrt(59)
        double rms = estimator.getRMS(problem);
        assertEquals(Math.sqrt(59.0), rms, 1e-12);

        // chiSquare = (3.0^2 / 2.0) + (5.0^2 / 4.0) = (9 / 2) + (25 / 4) = 4.5 + 6.25 = 10.75
        double chiSquare = estimator.getChiSquare(problem);
        assertEquals(10.75, chiSquare, 1e-12);
    }

    public void testUpdateJacobianAndResiduals() throws Throwable {
        ConcreteEstimator estimator = new ConcreteEstimator();
        EstimatedParameter p = new DummyParameter("p1", 1.0);
        WeightedMeasurement m = new DummyMeasurement(4.0, 3.0, p, 2.0);
        
        DummyProblem problem = new DummyProblem(
            new WeightedMeasurement[] { m },
            new EstimatedParameter[] { p }
        );

        estimator.publicInitializeEstimate(problem);
        estimator.publicUpdateJacobian();

        assertEquals(1, estimator.getJacobianEvaluations());
        
        // Update residuals and cost
        estimator.publicUpdateResidualsAndCost();
        // residual = sqrt(4.0) * 3.0 = 2.0 * 3.0 = 6.0
        // cost = sqrt(4.0 * 3.0 * 3.0) = sqrt(36) = 6.0
        assertEquals(6.0, estimator.getCost(), 1e-12);
        assertEquals(6.0, estimator.getResiduals()[0], 1e-12);
    }

    public void testGuessParametersErrorsNoDegreesOfFreedom() throws Throwable {
        ConcreteEstimator estimator = new ConcreteEstimator();
        EstimatedParameter p1 = new DummyParameter("p1", 1.0);
        EstimatedParameter p2 = new DummyParameter("p2", 2.0);
        WeightedMeasurement m = new DummyMeasurement(1.0, 1.0, p1, 1.0);

        // m = 1 measurement, p = 2 parameters -> m <= p
        DummyProblem problem = new DummyProblem(
            new WeightedMeasurement[] { m },
            new EstimatedParameter[] { p1, p2 }
        );

        try {
            estimator.guessParametersErrors(problem);
            fail("Expected EstimationException due to lack of degrees of freedom");
        } catch (EstimationException e) {
            assertTrue(e.getMessage().contains("no degrees of freedom"));
        }
    }

    public void testGetCovariancesSingularProblem() throws Throwable {
        ConcreteEstimator estimator = new ConcreteEstimator();
        EstimatedParameter p1 = new DummyParameter("p1", 1.0);
        // Partial derivative is 0, leading to a zero matrix (singular)
        WeightedMeasurement m1 = new DummyMeasurement(1.0, 1.0, p1, 0.0);
        WeightedMeasurement m2 = new DummyMeasurement(1.0, 1.0, p1, 0.0);

        DummyProblem problem = new DummyProblem(
            new WeightedMeasurement[] { m1, m2 },
            new EstimatedParameter[] { p1 }
        );

        estimator.publicInitializeEstimate(problem);

        try {
            estimator.getCovariances(problem);
            fail("Expected EstimationException due to singular problem");
        } catch (EstimationException e) {
            assertTrue(e.getMessage().contains("singular problem"));
        }
    }
}