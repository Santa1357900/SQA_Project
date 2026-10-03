package org.apache.commons.math.optimization.linear;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;

public class SimplexTableauTest {

    @Test
    public void testSimplexTableauCreationAndGetters() throws Throwable {
        double[] coefficients = new double[] { 1.0, 2.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 3.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 5.0));
        constraints.add(new LinearConstraint(new double[] { 2.0, 1.0 }, Relationship.GEQ, 4.0));
        constraints.add(new LinearConstraint(new double[] { 1.0, 0.0 }, Relationship.EQ, 1.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1.0e-6
        );

        assertEquals(2, tableau.getNumVariables());
        assertEquals(2, tableau.getOriginalNumDecisionVariables());
        assertEquals(2, tableau.getNumDecisionVariables());
        assertEquals(2, tableau.getNumSlackVariables());
        assertEquals(2, tableau.getNumArtificialVariables());
        assertTrue(tableau.getWidth() > 0);
        assertTrue(tableau.getHeight() > 0);
        assertNotNull(tableau.getData());
        assertNotNull(tableau.getNormalizedConstraints());
    }

    @Test
    public void testMinimizeAndNegativeRestriction() throws Throwable {
        double[] coefficients = new double[] { -1.0, 3.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        // Negative RHS to test normalization
        constraints.add(new LinearConstraint(new double[] { -1.0, 2.0 }, Relationship.LEQ, -3.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MINIMIZE, false, 1.0e-6
        );

        assertNotNull(tableau);
        assertEquals(3, tableau.getNumDecisionVariables()); // includes extra variable for negative restriction
        assertEquals(2, tableau.getOriginalNumDecisionVariables());
    }

    @Test
    public void testRowOperationsAndDiscardArtificial() throws Throwable {
        double[] coefficients = new double[] { 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0 }, Relationship.EQ, 2.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1.0e-6
        );

        assertEquals(1, tableau.getNumArtificialVariables());
        
        tableau.divideRow(1, 2.0);
        assertEquals(0.5, tableau.getEntry(1, 0), 1.0e-6);

        tableau.subtractRow(1, 1, 0.0);
        
        tableau.discardArtificialVariables();
        assertEquals(0, tableau.getNumArtificialVariables());
    }

    @Test
    public void testGetSolution() throws Throwable {
        double[] coefficients = new double[] { 1.0, 1.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 0.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 0.0 }, Relationship.LEQ, 5.0));
        constraints.add(new LinearConstraint(new double[] { 0.0, 1.0 }, Relationship.LEQ, 3.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1.0e-6
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
        
        List<LinearConstraint> constraints1 = new ArrayList<LinearConstraint>();
        constraints1.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));

        List<LinearConstraint> constraints2 = new ArrayList<LinearConstraint>();
        constraints2.add(new LinearConstraint(new double[] { 1.0 }, Relationship.LEQ, 5.0));

        SimplexTableau t1 = new SimplexTableau(f1, constraints1, GoalType.MAXIMIZE, true, 1.0e-6);
        SimplexTableau t2 = new SimplexTableau(f2, constraints2, GoalType.MAXIMIZE, true, 1.0e-6);

        assertEquals(t1, t1);
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertFalse(t1.equals(null));
        assertFalse(t1.equals("SomeString"));
    }

    @Test
    public void testSerialization() throws Throwable {
        double[] coefficients = new double[] { 1.0, 2.0 };
        LinearObjectiveFunction f = new LinearObjectiveFunction(coefficients, 1.0);
        
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] { 1.0, 1.0 }, Relationship.LEQ, 5.0));

        SimplexTableau tableau = new SimplexTableau(
            f, constraints, GoalType.MAXIMIZE, true, 1.0e-6
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(tableau);
        oos.flush();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        SimplexTableau deserialized = (SimplexTableau) ois.readObject();

        assertEquals(tableau, deserialized);
    }

    @Test
    public void testGetInvertedCoefficientSum() throws Throwable {
        ArrayRealVector vector = new ArrayRealVector(new double[] { 1.0, -2.0, 3.0 });
        double sum = SimplexTableau.getInvertedCoeffiecientSum(vector);
        assertEquals(-2.0, sum, 1.0e-6);
    }
}