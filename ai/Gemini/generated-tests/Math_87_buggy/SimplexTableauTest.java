package org.apache.commons.math.optimization.linear;

import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math.linear.ArrayRealVector;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SimplexTableauTest {

    @Test
    public void testSimplexTableauCreationLEQ() throws Throwable {
        double[] coeffs = new double[] { 1.0, 2.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 3.0);
        
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 5.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, 1e-6);
        
        assertEquals(2, tableau.getNumVariables());
        assertEquals(1, tableau.getNumSlackVariables());
        assertEquals(0, tableau.getNumArtificialVariables());
        assertEquals(1, tableau.getNumDecisionVariables());
        assertTrue(tableau.getWidth() > 0);
        assertTrue(tableau.getHeight() > 0);
        assertNotNull(tableau.getData());
    }

    @Test
    public void testSimplexTableauCreationGEQ() throws Throwable {
        double[] coeffs = new double[] { 1.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 0.0);
        
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 2.0 }, Relationship.GEQ, 4.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MINIMIZE, true, 1e-6);
        
        assertEquals(2, tableau.getNumSlackVariables());
        assertEquals(1, tableau.getNumArtificialVariables());
        assertEquals(2, tableau.getNumObjectiveFunctions());
    }

    @Test
    public void testSimplexTableauCreationEQ() throws Throwable {
        double[] coeffs = new double[] { 1.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 0.0);
        
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.EQ, 3.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, false, 1e-6);
        
        assertEquals(0, tableau.getNumSlackVariables());
        assertEquals(1, tableau.getNumArtificialVariables());
        assertEquals(3, tableau.getNumDecisionVariables()); // 2 + 1 for unrestricted
    }

    @Test
    public void testNormalizeConstraintNegativeValue() throws Throwable {
        double[] coeffs = new double[] { 1.0, -2.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 0.0);
        
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { -1.0, -1.0 }, Relationship.LEQ, -5.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, 1e-6);
        List<LinearConstraint> normalized = tableau.getNormalizedConstraints();
        
        assertNotNull(normalized);
        assertEquals(1, normalized.size());
        assertEquals(5.0, normalized.get(0).getValue(), 1e-6);
        assertEquals(Relationship.GEQ, normalized.get(0).getRelationship());
    }

    @Test
    public void testGetInvertedCoefficientSum() throws Throwable {
        RealVector vector = new ArrayRealVector(new double[] { 1.0, -2.0, 3.0 });
        double sum = SimplexTableau.getInvertedCoeffiecientSum(vector);
        assertEquals(-2.0, sum, 1e-6);
    }

    @Test
    public void testRowOperations() throws Throwable {
        double[] coeffs = new double[] { 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 0.0);
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 2.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, 1e-6);
        
        double initialVal = tableau.getEntry(1, 0);
        tableau.divideRow(1, 2.0);
        assertEquals(initialVal / 2.0, tableau.getEntry(1, 0), 1e-6);
        
        tableau.subtractRow(1, 1, 1.0);
        assertEquals(0.0, tableau.getEntry(1, 0), 1e-6);
    }

    @Test
    public void testDiscardArtificialVariables() throws Throwable {
        double[] coeffs = new double[] { 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 0.0);
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.EQ, 2.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MINIMIZE, true, 1e-6);
        assertTrue(tableau.getNumArtificialVariables() > 0);
        
        tableau.discardArtificialVariables();
        assertEquals(0, tableau.getNumArtificialVariables());
    }

    @Test
    public void testGetSolution() throws Throwable {
        double[] coeffs = new double[] { 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 0.0);
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));
        
        SimplexTableau tableau = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, 1e-6);
        RealPointValuePair solution = tableau.getSolution();
        
        assertNotNull(solution);
        assertNotNull(solution.getPoint());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        double[] coeffs = new double[] { 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coeffs, 0.0);
        Collection<LinearConstraint> constraints1 = new ArrayList<LinearConstraint>();
        constraints1.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));
        
        Collection<LinearConstraint> constraints2 = new ArrayList<LinearConstraint>();
        constraints2.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));
        
        SimplexTableau tableau1 = new SimplexTableau(f, constraints1, GoalType.MAXIMIZE, true, 1e-6);
        SimplexTableau tableau2 = new SimplexTableau(f, constraints2, GoalType.MAXIMIZE, true, 1e-6);
        
        assertTrue(tableau1.equals(tableau1));
        assertFalse(tableau1.equals(null));
        assertFalse(tableau1.equals("Some String"));
        assertTrue(tableau1.equals(tableau2));
        assertEquals(tableau1.hashCode(), tableau2.hashCode());
    }
}