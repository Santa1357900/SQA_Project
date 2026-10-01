package org.apache.commons.math.optimization.linear;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math.optimization.GoalType;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimplexTableauClaudeTest {

    private static final double EPS = 1.0e-6;

    // getNumVariables: returns dimension of objective function coefficients
    @Test
    public void testGetNumVariables_threeCoefficients_returnsThree() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 2, 3}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(3, t.getNumVariables());
    }

    // createTableau: zero constraints loop (0 rounds) still builds correct objective row
    @Test
    public void testCreateTableau_emptyConstraints_widthHeightAndRow() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 2}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(4, t.getWidth());
        assertEquals(1, t.getHeight());
        assertEquals(1.0, t.getEntry(0, 0), 1e-9);
        assertEquals(-1.0, t.getEntry(0, 1), 1e-9);
        assertEquals(-2.0, t.getEntry(0, 2), 1e-9);
        assertEquals(0.0, t.getEntry(0, 3), 1e-9);
    }

    // empty constraints: normalized list stays empty, no artificial/slack vars
    @Test
    public void testConstructor_emptyConstraints_zeroSlackAndArtificial() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 2}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(0, t.getNumSlackVariables());
        assertEquals(0, t.getNumArtificialVariables());
        assertTrue(t.getNormalizedConstraints().isEmpty());
    }

    // EQ constraint: dimensions and two objective functions (phase1 + phase2)
    @Test
    public void testCreateTableau_eqConstraint_dimensionsAndObjectiveFunctionCount() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(8, t.getWidth());
        assertEquals(5, t.getHeight());
        assertEquals(2, t.getNumObjectiveFunctions());
    }

    // EQ constraint: phase1 objective row (W) is zeroed via initialize()
    @Test
    public void testCreateTableau_eqConstraint_rowZeroEntriesAfterInitialize() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(-1.0, t.getEntry(0, 0), 1e-9);
        assertEquals(-1.0, t.getEntry(0, 2), 1e-9);
        assertEquals(-1.0, t.getEntry(0, 3), 1e-9);
        assertEquals(0.0, t.getEntry(0, 6), 1e-9);
        assertEquals(-4.0, t.getEntry(0, 7), 1e-9);
    }

    // EQ constraint: phase2 objective row (Z) holds negated maximize coefficients
    @Test
    public void testCreateTableau_eqConstraint_rowOneEntries() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(1.0, t.getEntry(1, 1), 1e-9);
        assertEquals(-15.0, t.getEntry(1, 2), 1e-9);
        assertEquals(-10.0, t.getEntry(1, 3), 1e-9);
        assertEquals(0.0, t.getEntry(1, 7), 1e-9);
    }

    // EQ constraint: constraint rows have decision/slack/artificial coefficients
    @Test
    public void testCreateTableau_eqConstraint_constraintRowsEntries() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(1.0, t.getEntry(2, 2), 1e-9);
        assertEquals(1.0, t.getEntry(2, 4), 1e-9);
        assertEquals(2.0, t.getEntry(2, 7), 1e-9);
        assertEquals(1.0, t.getEntry(4, 2), 1e-9);
        assertEquals(1.0, t.getEntry(4, 3), 1e-9);
        assertEquals(1.0, t.getEntry(4, 6), 1e-9);
        assertEquals(4.0, t.getEntry(4, 7), 1e-9);
    }

    // restrictToNonNegative=false, minimize: objective row gets x- column via getInvertedCoeffiecientSum
    @Test
    public void testCreateTableau_minimizeNotRestricted_objectiveRowXMinusColumn() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MINIMIZE, false, EPS);
        assertEquals(-1.0, t.getEntry(0, 0), 1e-9);
        assertEquals(1.0, t.getEntry(0, 1), 1e-9);
        assertEquals(1.0, t.getEntry(0, 2), 1e-9);
        assertEquals(-2.0, t.getEntry(0, 3), 1e-9);
        assertEquals(0.0, t.getEntry(0, 5), 1e-9);
    }

    // restrictToNonNegative=false: constraint row also carries x- column and slack
    @Test
    public void testCreateTableau_minimizeNotRestricted_constraintRowXMinusAndSlack() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MINIMIZE, false, EPS);
        assertEquals(1.0, t.getEntry(1, 1), 1e-9);
        assertEquals(-1.0, t.getEntry(1, 3), 1e-9);
        assertEquals(1.0, t.getEntry(1, 4), 1e-9);
        assertEquals(5.0, t.getEntry(1, 5), 1e-9);
    }

    // GEQ constraint: slack coefficient is -1 (excess) and artificial variable is set
    @Test
    public void testCreateTableau_geqConstraint_slackIsNegativeOneAndArtificialSet() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.GEQ, 1));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(1.0, t.getEntry(2, 2), 1e-9);
        assertEquals(-1.0, t.getEntry(2, 3), 1e-9);
        assertEquals(1.0, t.getEntry(2, 4), 1e-9);
        assertEquals(1.0, t.getEntry(2, 5), 1e-9);
    }

    // GEQ constraint: phase1 objective row after initialize() reflects the artificial elimination
    @Test
    public void testCreateTableau_geqConstraint_rowZeroAfterInitialize() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.GEQ, 1));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(-1.0, t.getEntry(0, 0), 1e-9);
        assertEquals(-1.0, t.getEntry(0, 2), 1e-9);
        assertEquals(1.0, t.getEntry(0, 3), 1e-9);
        assertEquals(0.0, t.getEntry(0, 4), 1e-9);
        assertEquals(-1.0, t.getEntry(0, 5), 1e-9);
    }

    // GEQ constraint: offsets for slack/artificial/RHS computed correctly
    @Test
    public void testGeqConstraint_offsets() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.GEQ, 1));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(3, t.getSlackVariableOffset());
        assertEquals(4, t.getArtificialVariableOffset());
        assertEquals(5, t.getRhsOffset());
    }

    // getNormalizedConstraints: negative RHS flips sign of coefficients and relationship
    @Test
    public void testGetNormalizedConstraints_negativeValue_flipsSignAndRelationship() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.LEQ, -5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        LinearConstraint norm = t.getNormalizedConstraints().get(0);
        assertEquals(5.0, norm.getValue(), 1e-9);
        assertEquals(Relationship.GEQ, norm.getRelationship());
        assertEquals(-1.0, norm.getCoefficients().getData()[0], 1e-9);
        assertEquals(-1.0, norm.getCoefficients().getData()[1], 1e-9);
    }

    // getNormalizedConstraints: boundary value exactly 0 is NOT flipped (strict < 0 check)
    @Test
    public void testGetNormalizedConstraints_zeroValue_unchanged() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 0));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        LinearConstraint norm = t.getNormalizedConstraints().get(0);
        assertEquals(0.0, norm.getValue(), 1e-9);
        assertEquals(Relationship.LEQ, norm.getRelationship());
    }

    // getNormalizedConstraints: positive RHS stays unchanged
    @Test
    public void testGetNormalizedConstraints_positiveValue_unchanged() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {2}, Relationship.GEQ, 7));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        LinearConstraint norm = t.getNormalizedConstraints().get(0);
        assertEquals(7.0, norm.getValue(), 1e-9);
        assertEquals(Relationship.GEQ, norm.getRelationship());
        assertEquals(2.0, norm.getCoefficients().getData()[0], 1e-9);
    }

    // getNumObjectiveFunctions: no EQ/GEQ constraint means no artificial vars -> 1
    @Test
    public void testGetNumObjectiveFunctions_noArtificialVariables_returnsOne() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(1, t.getNumObjectiveFunctions());
    }

    // getNumObjectiveFunctions: EQ constraint creates artificial var -> 2
    @Test
    public void testGetNumObjectiveFunctions_withArtificialVariables_returnsTwo() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.EQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(2, t.getNumObjectiveFunctions());
    }

    // getNumDecisionVariables: restrictToNonNegative=true keeps count equal to number of vars
    @Test
    public void testGetNumDecisionVariables_restrictToNonNegativeTrue() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(2, t.getNumDecisionVariables());
    }

    // getOriginalNumDecisionVariables: restrictToNonNegative=false excludes the extra x- variable
    @Test
    public void testGetOriginalNumDecisionVariables_restrictToNonNegativeFalse() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MINIMIZE, false, EPS);
        assertEquals(3, t.getNumDecisionVariables());
        assertEquals(2, t.getOriginalNumDecisionVariables());
    }

    // getNumSlackVariables: counts LEQ + GEQ constraints only
    @Test
    public void testGetNumSlackVariables_mixedConstraints() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 1));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.GEQ, 2));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 3));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(2, t.getNumSlackVariables());
    }

    // getNumArtificialVariables: counts EQ + GEQ constraints only
    @Test
    public void testGetNumArtificialVariables_mixedConstraints() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 1));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.GEQ, 2));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 3));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(2, t.getNumArtificialVariables());
    }

    // offsets: slack/artificial/RHS offsets on the EQ example match the documented layout
    @Test
    public void testGetSlackAndArtificialAndRhsOffset_eqExample() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertEquals(4, t.getSlackVariableOffset());
        assertEquals(6, t.getArtificialVariableOffset());
        assertEquals(7, t.getRhsOffset());
    }

    // getData: returned matrix snapshot matches known entries of the EQ example
    @Test
    public void testGetData_matchesKnownEntries() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        double[][] data = t.getData();
        assertEquals(1.0, data[1][1], 1e-9);
        assertEquals(-15.0, data[1][2], 1e-9);
        assertEquals(4.0, data[4][7], 1e-9);
    }

    // discardArtificialVariables: no-op when there are no artificial variables
    @Test
    public void testDiscardArtificialVariables_noArtificial_noOp() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        int wBefore = t.getWidth();
        int hBefore = t.getHeight();
        t.discardArtificialVariables();
        assertEquals(wBefore, t.getWidth());
        assertEquals(hBefore, t.getHeight());
        assertEquals(0, t.getNumArtificialVariables());
    }

    // discardArtificialVariables: removes W row, W column and artificial columns -> new dimensions
    @Test
    public void testDiscardArtificialVariables_withArtificial_dimensionsReduced() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        t.discardArtificialVariables();
        assertEquals(6, t.getWidth());
        assertEquals(4, t.getHeight());
        assertEquals(0, t.getNumArtificialVariables());
        assertEquals(1, t.getNumObjectiveFunctions());
    }

    // discardArtificialVariables: resulting entries equal the shifted original rows/columns
    @Test
    public void testDiscardArtificialVariables_withArtificial_entriesCorrect() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {15, 10}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1, 0}, Relationship.LEQ, 2));
        constraints.add(new LinearConstraint(new double[] {0, 1}, Relationship.LEQ, 3));
        constraints.add(new LinearConstraint(new double[] {1, 1}, Relationship.EQ, 4));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        t.discardArtificialVariables();
        assertEquals(1.0, t.getEntry(0, 0), 1e-9);
        assertEquals(-15.0, t.getEntry(0, 1), 1e-9);
        assertEquals(0.0, t.getEntry(1, 0), 1e-9);
        assertEquals(1.0, t.getEntry(1, 1), 1e-9);
        assertEquals(4.0, t.getEntry(3, 5), 1e-9);
    }

    // equals: comparing instance to itself is reflexively true (shortcut branch)
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertTrue(t.equals(t));
    }

    // equals: comparing against null returns false
    @Test
    public void testEquals_null_false() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertFalse(t.equals(null));
    }

    // equals: comparing against an incompatible type is caught and returns false
    @Test
    public void testEquals_differentType_false() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertFalse(t.equals("not a tableau"));
    }

    // equals/hashCode: two tableaus built from identical shared components are equal and consistent
    @Test
    public void testEquals_sameComponents_trueAndHashCodeConsistent() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 5));
        SimplexTableau t1 = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        SimplexTableau t2 = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        assertTrue(t1.equals(t2));
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    // setEntry/getEntry: round trip of a written value
    @Test
    public void testSetEntryAndGetEntry_roundTrip() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 2}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        t.setEntry(0, 0, 42.0);
        assertEquals(42.0, t.getEntry(0, 0), 1e-9);
    }

    // divideRow: every element of the row is divided by the divisor
    @Test
    public void testDivideRow_dividesEachElement() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1, 2}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        t.divideRow(0, 2.0);
        assertEquals(0.5, t.getEntry(0, 0), 1e-9);
        assertEquals(-0.5, t.getEntry(0, 1), 1e-9);
        assertEquals(-1.0, t.getEntry(0, 2), 1e-9);
    }

    // subtractRow: minuendRow = minuendRow - multiple * subtrahendRow
    @Test
    public void testSubtractRow_subtractsMultipleOfRow() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] {1}, 0);
        List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        constraints.add(new LinearConstraint(new double[] {1}, Relationship.LEQ, 5));
        SimplexTableau t = new SimplexTableau(f, constraints, GoalType.MAXIMIZE, true, EPS);
        t.subtractRow(0, 1, 2.0);
        assertEquals(1.0, t.getEntry(0, 0), 1e-9);
        assertEquals(-3.0, t.getEntry(0, 1), 1e-9);
        assertEquals(-2.0, t.getEntry(0, 2), 1e-9);
        assertEquals(-10.0, t.getEntry(0, 3), 1e-9);
    }
}
