package org.apache.commons.math3.optimization.linear;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.junit.Test;

import static org.junit.Assert.*;

public class SimplexSolverTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        assertNotNull(solver);
    }

    @Test
    public void testParameterizedConstructor() throws Throwable {
        SimplexSolver solver = new SimplexSolver(1.0e-5, 5);
        assertNotNull(solver);
    }

    @Test
    public void testSolveSimpleLinearProblem() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 0.0 }, Relationship.LEQ, 1.0));
        constraints.add(new LinearConstraint(new double[] { 0.0, 1.0 }, Relationship.LEQ, 1.0));

        SimplexSolver solver = new SimplexSolver();
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertNotNull(solution);
        assertEquals(2.0, solution.getValue(), 1.0e-6);
    }

    @Test(expected = NoFeasibleSolutionException.class)
    public void testNoFeasibleSolution() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.EQ, 10.0));
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.EQ, 5.0));

        SimplexSolver solver = new SimplexSolver();
        solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
    }

    @Test(expected = UnboundedSolutionException.class)
    public void testUnboundedSolution() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { -1.0, 0.0 }, Relationship.LEQ, 0.0));

        SimplexSolver solver = new SimplexSolver();
        solver.optimize(f, constraints, GoalType.MAXIMIZE, false);
    }
}