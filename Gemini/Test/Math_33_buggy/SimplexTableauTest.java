package org.apache.commons.math3.optimization.linear;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class SimplexTableauTest {

    @Test
    public void testTableauCreationAndBasicGetters() throws Throwable {
        double[] coefficients = new double[] { 2.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 3.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 4.0));
        constraints.add(new LinearConstraint(new double[] { 2.0, 0.0 }, Relationship.GEQ, 2.0));
        constraints.add(new LinearConstraint(new double[] { 0.0, 1.0 }, Relationship.EQ, 3.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6, 10
        );

        assertEquals(2, tableau.getOriginalNumDecisionVariables());
        assertEquals(3, tableau.getNumDecisionVariables()); // restrictToNonNegative = true -> 2 + 0
        assertEquals(2, tableau.getNumSlackVariables()); // LEQ (1) + GEQ (1)
        assertEquals(2, tableau.getNumArtificialVariables()); // EQ (1) + GEQ (1)
        assertEquals(2, tableau.getNumObjectiveFunctions()); // artificial variables > 0 -> phase 1 and 2
        assertTrue(tableau.getWidth() > 0);
        assertTrue(tableau.getHeight() > 0);
        assertNotNull(tableau.getData());
        assertNotNull(tableau.getSolution());
    }

    @Test
    public void testNormalizeConstraintsNegativeRhs() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { -1.0 }, Relationship.LEQ, -5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MINIMIZE, true, 1e-6
        );

        List<LinearConstraint> normalized = tableau.normalizeConstraints(constraints);
        assertEquals(1, normalized.size());
        assertEquals(5.0, normalized.get(0).getValue(), 1e-6);
        assertEquals(Relationship.GEQ, normalized.get(0).getRelationship());
    }

    @Test
    public void testTableauOperations() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 0.0 }, Relationship.LEQ, 10.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MINIMIZE, true, 1e-6
        );

        int height = tableau.getHeight();
        int width = tableau.getWidth();
        assertTrue(height > 0);
        assertTrue(width > 0);

        double originalEntry = tableau.getEntry(0, 0);
        tableau.setEntry(0, 0, originalEntry + 1.0);
        assertEquals(originalEntry + 1.0, tableau.getEntry(0, 0), 1e-6);

        tableau.divideRow(0, 2.0);
        tableau.subtractRow(1, 0, 1.0);

        assertFalse(tableau.isOptimal());
    }

    @Test
    public void testDropPhase1Objective() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.EQ, 5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6
        );

        assertEquals(2, tableau.getNumObjectiveFunctions());
        tableau.dropPhase1Objective();
        assertEquals(1, tableau.getNumObjectiveFunctions());
    }

    @Test
    public void testGetBasicRowAndInvertedCoefficientSum() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] { 1.0, 2.0 }, 0.0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MINIMIZE, false, 1e-6
        );

        Integer basicRow = tableau.getBasicRow(0);
        // Depending on tableau structure, may be null or a row index
        if (basicRow != null) {
            assertTrue(basicRow >= 0);
        }

        double invSum = SimplexTableau.getInvertedCoefficientSum(new ArrayRealVector(new double[] { 1.0, -2.0, 3.0 }));
        assertEquals(-2.0, invSum, 1e-6);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        LinearObjectiveFunction f1 = new LinearObjectiveFunction(new double[] { 1.0 }, 0.0);
        List<LinearConstraint> constraints1 = new ArrayList<LinearConstraint>();
        constraints1.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));

        LinearObjectiveFunction f2 = new LinearObjectiveFunction(new double[] { 1.0 }, 0.0);
        List<LinearConstraint> constraints2 = new ArrayList<LinearConstraint>();
        constraints2.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));

        SimplexTableau t1 = new SimplexTableau(f1, constraints1, GoalType.MAXIMIZE, true, 1e-6);
        SimplexTableau t2 = new SimplexTableau(f2, constraints2, GoalType.MAXIMIZE, true, 1e-6);

        assertTrue(t1.equals(t1));
        assertFalse(t1.equals(null));
        assertFalse(t1.equals(new Object()));
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }
}