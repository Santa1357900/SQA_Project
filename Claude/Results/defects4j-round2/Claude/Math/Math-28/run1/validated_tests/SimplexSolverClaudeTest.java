package org.apache.commons.math3.optimization.linear;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;

public class SimplexSolverClaudeTest {

    // covers default constructor plus a full two-iteration doOptimize path (LP with a unique interior vertex)
    @Test
    public void testConstructor_default_solvesSimpleMaximization() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 2.0}, Relationship.LEQ, 4.0));
        constraints.add(new LinearConstraint(new double[] {3.0, 2.0}, Relationship.LEQ, 6.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertEquals(2.5, solution.getValue(), 1.0e-6);
        assertEquals(1.0, solution.getPoint()[0], 1.0e-6);
        assertEquals(1.5, solution.getPoint()[1], 1.0e-6);
    }

    // covers SimplexSolver(double,int) constructor with custom epsilon/maxUlps, same LP as above
    @Test
    public void testConstructor_withEpsilonAndUlps_solvesSimpleMaximization() throws Throwable {
        SimplexSolver solver = new SimplexSolver(1.0e-8, 5);
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 2.0}, Relationship.LEQ, 4.0));
        constraints.add(new LinearConstraint(new double[] {3.0, 2.0}, Relationship.LEQ, 6.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertEquals(2.5, solution.getValue(), 1.0e-6);
    }

    // covers doIteration: pivot column/row selection, divideRow and subtractRow loop with a single pivot
    @Test
    public void testDoIteration_direct_singlePivotReachesOptimal() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.LEQ, 5.0));
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, 1.0e-6, 10);
        SimplexSolver solver = new SimplexSolver();
        solver.solvePhase1(tableau);
        tableau.dropPhase1Objective();
        assertFalse(tableau.isOptimal());
        solver.doIteration(tableau);
        assertTrue(tableau.isOptimal());
        PointValuePair solution = tableau.getSolution();
        assertEquals(5.0, solution.getValue(), 1.0e-6);
        assertEquals(5.0, solution.getPoint()[0], 1.0e-6);
    }

    // covers doIteration: pivotRow == null -> UnboundedSolutionException branch
    @Test
    public void testDoIteration_direct_unboundedProblem_throwsUnboundedSolutionException() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, 1.0e-6, 10);
        SimplexSolver solver = new SimplexSolver();
        solver.solvePhase1(tableau);
        tableau.dropPhase1Objective();
        try {
            solver.doIteration(tableau);
            fail("expected UnboundedSolutionException");
        } catch (UnboundedSolutionException expected) {
            // expected
        }
    }

    // covers solvePhase1: numArtificialVariables == 0 -> immediate return branch, no iterations performed
    @Test
    public void testSolvePhase1_direct_noArtificialVariables_returnsWithoutIterating() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 1.0}, Relationship.LEQ, 10.0));
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MINIMIZE, true, 1.0e-6, 10);
        SimplexSolver solver = new SimplexSolver();
        assertEquals(0, tableau.getNumArtificialVariables());
        solver.solvePhase1(tableau);
        assertEquals(0, tableau.getNumArtificialVariables());
    }

    // covers solvePhase1: phase1 objective W != 0 -> NoFeasibleSolutionException branch
    @Test
    public void testSolvePhase1_direct_infeasibleProblem_throwsNoFeasibleSolutionException() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.LEQ, 1.0));
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.GEQ, 5.0));
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MINIMIZE, true, 1.0e-6, 10);
        SimplexSolver solver = new SimplexSolver();
        try {
            solver.solvePhase1(tableau);
            fail("expected NoFeasibleSolutionException");
        } catch (NoFeasibleSolutionException expected) {
            // expected
        }
    }

    // covers doOptimize -> solvePhase1 -> NoFeasibleSolutionException propagated through the public API
    @Test
    public void testDoOptimize_infeasibleViaOptimize_throwsNoFeasibleSolutionException() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.LEQ, 1.0));
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.GEQ, 10.0));
        try {
            solver.optimize(f, constraints, GoalType.MINIMIZE, true);
            fail("expected NoFeasibleSolutionException");
        } catch (NoFeasibleSolutionException expected) {
            // expected
        }
    }

    // covers doOptimize -> doIteration -> UnboundedSolutionException propagated through the public API
    @Test
    public void testDoOptimize_unboundedViaOptimize_throwsUnboundedSolutionException() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        try {
            solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
            fail("expected UnboundedSolutionException");
        } catch (UnboundedSolutionException expected) {
            // expected
        }
    }

    // covers doOptimize with a GEQ constraint requiring an artificial variable in phase 1
    @Test
    public void testDoOptimize_minimizationWithGeqConstraint_returnsCorrectValue() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 1.0}, Relationship.GEQ, 2.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, true);
        assertEquals(2.0, solution.getValue(), 1.0e-6);
    }

    // covers doOptimize with an empty constraint collection: origin already optimal, zero phase-2 iterations
    @Test
    public void testDoOptimize_emptyConstraints_originAlreadyOptimalZeroIterations() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, true);
        assertEquals(0.0, solution.getValue(), 1.0e-6);
    }

    // covers doOptimize with restrictToNonNegative = false and an EQ constraint pinning the objective value
    @Test
    public void testDoOptimize_equalityConstraintFreeVariables_returnsZero() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 1.0}, Relationship.EQ, 0.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, false);
        assertEquals(0.0, solution.getValue(), 1.0e-6);
    }

    // covers doOptimize needing exactly one pivot (negative objective coefficient, single LEQ bound)
    @Test
    public void testDoOptimize_oneIterationNeeded_minimizeNegativeX() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {-1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.LEQ, 5.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, true);
        assertEquals(-5.0, solution.getValue(), 1.0e-6);
        assertEquals(5.0, solution.getPoint()[0], 1.0e-6);
    }

    // covers doOptimize with both an EQ and a GEQ constraint combined, exercising phase1 with two artificial vars
    @Test
    public void testDoOptimize_mixedEqAndGeqConstraints_returnsCorrectValue() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 0.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, -1.0}, Relationship.EQ, 2.0));
        constraints.add(new LinearConstraint(new double[] {0.0, 1.0}, Relationship.GEQ, 1.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, true);
        assertEquals(3.0, solution.getValue(), 1.0e-6);
        assertEquals(3.0, solution.getPoint()[0], 1.0e-6);
        assertEquals(1.0, solution.getPoint()[1], 1.0e-6);
    }

    // covers doOptimize with restrictToNonNegative = false producing a negative optimal value
    @Test
    public void testDoOptimize_restrictToNonNegativeFalse_negativeOptimalValue() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.GEQ, -5.0));
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.LEQ, 10.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, false);
        assertEquals(-5.0, solution.getValue(), 1.0e-6);
        assertEquals(-5.0, solution.getPoint()[0], 1.0e-6);
    }

    // covers doOptimize maximizing with a GEQ lower bound combined with a LEQ upper bound
    @Test
    public void testDoOptimize_maximizeWithGeqConstraint_returnsCorrectValue() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 1.0}, Relationship.LEQ, 10.0));
        constraints.add(new LinearConstraint(new double[] {1.0, 0.0}, Relationship.GEQ, 2.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertEquals(10.0, solution.getValue(), 1.0e-6);
    }



    // covers getPivotRow: an exact tie in the minimum ratio test between two identical LEQ constraints
    @Test
    public void testDoOptimize_tieBreakDuplicateConstraints_returnsCorrectValue() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.LEQ, 5.0));
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.LEQ, 5.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertEquals(5.0, solution.getValue(), 1.0e-6);
        assertEquals(5.0, solution.getPoint()[0], 1.0e-6);
    }

    // covers doOptimize where the origin is already optimal despite real constraints being present
    @Test
    public void testDoOptimize_zeroAtOriginWithConstraintsPresent() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {2.0, 3.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 1.0}, Relationship.LEQ, 10.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, true);
        assertEquals(0.0, solution.getValue(), 1.0e-6);
        assertEquals(0.0, solution.getPoint()[0], 1.0e-6);
        assertEquals(0.0, solution.getPoint()[1], 1.0e-6);
    }

    // covers doOptimize needing several iterations across three variables; optimum verified via LP duality
    @Test
    public void testDoOptimize_multiIterationThreeVariableResourceAllocation() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 2.0, 3.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 1.0, 1.0}, Relationship.LEQ, 10.0));
        constraints.add(new LinearConstraint(new double[] {0.0, 1.0, 1.0}, Relationship.LEQ, 6.0));
        constraints.add(new LinearConstraint(new double[] {0.0, 0.0, 1.0}, Relationship.LEQ, 4.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertEquals(20.0, solution.getValue(), 1.0e-6);
        assertEquals(4.0, solution.getPoint()[0], 1.0e-6);
        assertEquals(2.0, solution.getPoint()[1], 1.0e-6);
        assertEquals(4.0, solution.getPoint()[2], 1.0e-6);
    }

    // covers getPivotColumn choosing the variable with the larger beneficial coefficient under a shared constraint
    @Test
    public void testDoOptimize_prefersHigherCoefficientVariable_singleConstraint() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {5.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 1.0}, Relationship.LEQ, 1.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertEquals(5.0, solution.getValue(), 1.0e-6);
        assertEquals(1.0, solution.getPoint()[0], 1.0e-6);
        assertEquals(0.0, solution.getPoint()[1], 1.0e-6);
    }

    // covers doOptimize with two independent GEQ lower-bound constraints requiring phase1
    @Test
    public void testDoOptimize_allGeqConstraintsFeasible_returnsCorrectValue() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0, 1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0, 0.0}, Relationship.GEQ, 1.0));
        constraints.add(new LinearConstraint(new double[] {0.0, 1.0}, Relationship.GEQ, 1.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MINIMIZE, true);
        assertEquals(2.0, solution.getValue(), 1.0e-6);
        assertEquals(1.0, solution.getPoint()[0], 1.0e-6);
        assertEquals(1.0, solution.getPoint()[1], 1.0e-6);
    }

    // covers doOptimize maximizing with a single EQ constraint pinning the variable exactly
    @Test
    public void testDoOptimize_maximizeEqualityOnly_returnsExactValue() throws Throwable {
        SimplexSolver solver = new SimplexSolver();
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1.0}, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1.0}, Relationship.EQ, 7.0));
        PointValuePair solution = solver.optimize(f, constraints, GoalType.MAXIMIZE, true);
        assertEquals(7.0, solution.getValue(), 1.0e-6);
        assertEquals(7.0, solution.getPoint()[0], 1.0e-6);
    }
}
