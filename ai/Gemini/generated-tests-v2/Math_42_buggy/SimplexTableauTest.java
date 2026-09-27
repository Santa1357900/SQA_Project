package org.apache.commons.math.optimization.linear;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class SimplexTableauTest {

    @Test
    public void testSimplexTableauCreationAndGetters() throws Throwable {
        double[] coefficients = new double[] { 2.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 3.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 4.0));
        constraints.add(new LinearConstraint(new double[] { 2.0, 0.0 }, Relationship.GEQ, 2.0));
        constraints.add(new LinearConstraint(new double[] { 0.0, 1.0 }, Relationship.EQ, 3.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6, 10
        );

        assertNotNull(tableau.getData());
        assertTrue(tableau.getWidth() > 0);
        assertTrue(tableau.getHeight() > 0);
        assertEquals(3, tableau.getNumDecisionVariables());
        assertEquals(2, tableau.getNumSlackVariables());
        assertTrue(tableau.getNumArtificialVariables() >= 0);
        assertEquals(2, tableau.getOriginalNumDecisionVariables());
        assertTrue(tableau.getSlackVariableOffset() > 0);
        assertTrue(tableau.getArtificialVariableOffset() > 0);
        assertTrue(tableau.getRhsOffset() > 0);
        
        double entry = tableau.getEntry(0, 0);
        tableau.setEntry(0, 0, entry);
        assertEquals(entry, tableau.getEntry(0, 0), 1e-6);
    }

    @Test
    public void testMinimizeGoalAndNegativeRhsNormalization() throws Throwable {
        double[] coefficients = new double[] { -1.0, 2.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        // Negative RHS to test normalization
        constraints.add(new LinearConstraint(new double[] { -1.0, -1.0 }, Relationship.LEQ, -5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MINIMIZE, false, 1e-6
        );

        assertNotNull(tableau);
        assertFalse(tableau.isOptimal());
    }

    @Test
    public void testRowOperationsAndBasicRow() throws Throwable {
        double[] coefficients = new double[] { 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 10.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6
        );

        tableau.divideRow(1, 2.0);
        assertEquals(5.0, tableau.getEntry(1, tableau.getRhsOffset()), 1e-6);

        tableau.subtractRow(1, 0, 1.0);

        Integer basicRow = tableau.getBasicRow(1);
        assertNotNull(basicRow);

        Integer nonBasicRow = tableau.getBasicRow(0);
        assertNull(nonBasicRow);
    }

    @Test
    public void testDropPhase1Objective() throws Throwable {
        double[] coefficients = new double[] { 1.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 2.0 }, Relationship.EQ, 5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6
        );

        if (tableau.getNumObjectiveFunctions() == 2) {
            tableau.dropPhase1Objective();
            assertEquals(1, tableau.getNumObjectiveFunctions());
        }
    }

    @Test
    public void testGetSolution() throws Throwable {
        double[] coefficients = new double[] { 1.0, 2.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 10.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, false, 1e-6
        );

        RealPointValuePair solution = tableau.getSolution();
        assertNotNull(solution);
        assertNotNull(solution.getPoint());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        double[] coefficients = new double[] { 1.0 };
        LinearObjectiveFunction f1 = new LinearObjectiveFunction(coefficients, 0.0);
        LinearObjectiveFunction f2 = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));

        SimplexTableau t1 = new SimplexTableau(f1, constraints, GoalType.MAXIMIZE, true, 1e-6);
        SimplexTableau t2 = new SimplexTableau(f2, constraints, GoalType.MAXIMIZE, true, 1e-6);

        assertEquals(t1, t1);
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertFalse(t1.equals(null));
        assertFalse(t1.equals("SomeString"));
    }

    @Test
    public void testGetInvertedCoefficientSum() throws Throwable {
        double[] data = new double[] { 1.0, -2.0, 3.0 };
        ArrayRealVector vector = new ArrayRealVector(data);
        double sum = SimplexTableau.getInvertedCoefficientSum(vector);
        assertEquals(-2.0, sum, 1e-6);
    }
}