package org.apache.commons.math.optimization.linear;

import org.apache.commons.math.linear.RealVectorImpl;
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
        double[] coefficients = new double[] { 1.0, 2.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 3.0);

        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 10.0));
        constraints.add(new LinearConstraint(new double[] { 2.0, 1.0 }, Relationship.GEQ, 8.0));
        constraints.add(new LinearConstraint(new double[] { 1.0, 0.0 }, Relationship.EQ, 5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6
        );

        assertEquals(2, tableau.getNumVariables());
        assertEquals(3, tableau.getNumDecisionVariables());
        assertEquals(2, tableau.getOriginalNumDecisionVariables());
        assertEquals(2, tableau.getNumSlackVariables());
        assertEquals(2, tableau.getNumArtificialVariables());
        assertTrue(tableau.getWidth() > 0);
        assertTrue(tableau.getHeight() > 0);
        assertNotNull(tableau.getData());
    }

    @Test
    public void testSimplexTableauMinimizeAndNegativeRhs() throws Throwable {
        double[] coefficients = new double[] { -1.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);

        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        // Negative RHS should trigger normalization
        constraints.add(new LinearConstraint(new double[] { -1.0, -2.0 }, Relationship.LEQ, -5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MINIMIZE, false, 1e-6
        );

        List<LinearConstraint> normalized = tableau.getNormalizedConstraints();
        assertNotNull(normalized);
        assertEquals(1, normalized.size());
        assertTrue(normalized.get(0).getValue() >= 0);
    }

    @Test
    public void testTableauRowOperationsAndDiscardArtificial() throws Throwable {
        double[] coefficients = new double[] { 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);

        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.EQ, 2.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6
        );

        assertEquals(2, tableau.getNumArtificialVariables());
        
        tableau.divideRow(1, 2.0);
        assertEquals(0.5, tableau.getEntry(1, 0), 1e-6);

        tableau.subtractRow(1, 1, 0.0);
        assertEquals(0.0, tableau.getEntry(1, 0), 1e-6);

        tableau.discardArtificialVariables();
        assertEquals(0, tableau.getNumArtificialVariables());
    }

    @Test
    public void testGetSolution() throws Throwable {
        double[] coefficients = new double[] { 1.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);

        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 0.0 }, Relationship.LEQ, 4.0));
        constraints.add(new LinearConstraint(new double[] { 0.0, 1.0 }, Relationship.LEQ, 4.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1e-6
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

        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));

        SimplexTableau t1 = new SimplexTableau(f1, constraints, GoalType.MAXIMIZE, true, 1e-6);
        SimplexTableau t2 = new SimplexTableau(f2, constraints, GoalType.MAXIMIZE, true, 1e-6);

        assertEquals(t1, t1);
        assertFalse(t1.equals(null));
        assertFalse(t1.equals("SomeString"));
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    public void testInvertedCoefficientSum() throws Throwable {
        RealVectorImpl vector = new RealVectorImpl(new double[] { 1.0, -2.0, 3.0 });
        double sum = SimplexTableau.getInvertedCoeffiecientSum(vector);
        assertEquals(-2.0, sum, 1e-6);
    }
}