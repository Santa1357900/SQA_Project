package org.apache.commons.math.optimization.linear;

import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SimplexSolverTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        assertNotNull(solver);
    }

    @Test
    public void testCustomEpsilonConstructor() throws Throwable {
        SimplexSolver solver = new SimplexSolver(1.0e-5);
        assertNotNull(solver);
    }

    @Test
    public void testIsOptimalWithArtificialVariables() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 10.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, 1.0e-6);
        
        boolean optimal = solver.isOptimal(tableau);
        assertFalse(optimal);
    }

    @Test
    public void testDoOptimizeSimple() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 2.0 }, Relationship.LEQ, 10.0));
        constraints.add(new LinearConstraint(new double[] { 2.0, 1.0 }, Relationship.LEQ, 10.0));

        solver.setMaxIterations(100);
        RealPointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertNotNull(solution);
        assertTrue(solution.getValue() >= 0.0);
    }

    @Test
    public void testUnboundedSolution() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { -1.0, 1.0 }, Relationship.LEQ, 0.0));

        solver.setMaxIterations(100);
        try {
            solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
            fail("Expected UnboundedSolutionException");
        } catch (UnboundedSolutionException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testNoFeasibleSolution() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.EQ, 10.0));
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.EQ, 20.0));

        solver.setMaxIterations(100);
        try {
            solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
            fail("Expected NoFeasibleSolutionException");
        } catch (NoFeasibleSolutionException e) {
            assertNotNull(e);
        }
    }
}