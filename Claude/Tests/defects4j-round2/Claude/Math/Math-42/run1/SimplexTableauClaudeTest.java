package org.apache.commons.math.optimization.linear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;

public class SimplexTableauClaudeTest {

    private static final double EPS = 1e-9;

    private List<LinearConstraint> singleton(LinearConstraint c) {
        List<LinearConstraint> list = new ArrayList<LinearConstraint>();
        list.add(c);
        return list;
    }

    // constructor: restrictToNonNegative=true -> numDecisionVariables = f dim, 1 obj function
    @Test
    public void testConstructor_restrictToNonNegative_dimensionsCorrect() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 0}, Relationship.LEQ, 4);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertEquals(2, t.getNumDecisionVariables());
        assertEquals(1, t.getNumSlackVariables());
        assertEquals(0, t.getNumArtificialVariables());
        assertEquals(1, t.getNumObjectiveFunctions());
        assertEquals(5, t.getWidth());
        assertEquals(2, t.getHeight());
    }

    // constructor: restrictToNonNegative=false -> one extra decision variable (x-)
    @Test
    public void testConstructor_notRestrictToNonNegative_extraDecisionVariable() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 0}, Relationship.LEQ, 4);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, false, 1e-6);
        assertEquals(3, t.getNumDecisionVariables());
        assertEquals(2, t.getOriginalNumDecisionVariables());
    }

    // constructor: GEQ constraint adds slack(excess) and artificial, triggers phase 1 (2 objective funcs)
    @Test
    public void testConstructor_geqConstraint_addsSlackAndArtificialVariable() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 1}, Relationship.GEQ, 2);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertEquals(1, t.getNumSlackVariables());
        assertEquals(1, t.getNumArtificialVariables());
        assertEquals(2, t.getNumObjectiveFunctions());
    }

    // constructor: EQ constraint adds artificial only, no slack
    @Test
    public void testConstructor_eqConstraint_addsArtificialOnlyNoSlack() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 1}, Relationship.EQ, 4);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertEquals(0, t.getNumSlackVariables());
        assertEquals(1, t.getNumArtificialVariables());
    }

    // constructor: 5-arg ctor must delegate to 6-arg with DEFAULT_ULPS(10) -> resulting tableaus equal
    @Test
    public void testConstructor_fiveArgDelegatesToDefaultUlps_tableausEqual() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        List<LinearConstraint> empty = new ArrayList<LinearConstraint>();
        SimplexTableau t5 = new SimplexTableau(f, empty, GoalType.MINIMIZE, true, 1e-6);
        SimplexTableau t6 = new SimplexTableau(f, empty, GoalType.MINIMIZE, true, 1e-6, 10);
        assertTrue(t5.equals(t6));
        assertEquals(t5.hashCode(), t6.hashCode());
    }

    // createTableau: EQ constraint, numObjectiveFunctions==2 branch, hand-computed initial rows
    @Test
    public void testCreateTableau_equalityConstraint_initialRowsMatchExpected() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.EQ, 5);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6, 0);
        double[][] expected = {
            {-1, 0, -1, 0, -5},
            {0, -1, 1, 0, 0},
            {0, 0, 1, 1, 5}
        };
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 5; col++) {
                assertEquals(expected[r][col], t.getEntry(r, col), EPS);
            }
        }
    }

    // createTableau: !restrictToNonNegative branch populates x- column using getInvertedCoefficientSum
    @Test
    public void testCreateTableau_notRestrictToNonNegative_negativeVarColumnPopulated() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{2, 3}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 1}, Relationship.LEQ, 5);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, false, 1e-6);
        assertEquals(-5.0, t.getEntry(0, 3), EPS);
        assertEquals(-2.0, t.getEntry(1, 3), EPS);
        assertEquals(1.0, t.getEntry(1, 4), EPS);
        assertEquals(5.0, t.getEntry(1, 5), EPS);
    }

    // normalizeConstraints: negative RHS flips coefficients sign and relationship
    @Test
    public void testNormalizeConstraints_negativeValue_flipsSignAndRelationship() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        SimplexTableau t = new SimplexTableau(f, singleton(new LinearConstraint(new double[]{1}, Relationship.EQ, 1)),
                GoalType.MINIMIZE, true, 1e-6);
        LinearConstraint negative = new LinearConstraint(new double[]{1, 1}, Relationship.LEQ, -5);
        List<LinearConstraint> normalized = t.normalizeConstraints(singleton(negative));
        LinearConstraint norm = normalized.get(0);
        assertEquals(Relationship.GEQ, norm.getRelationship());
        assertEquals(5.0, norm.getValue(), EPS);
        assertEquals(-1.0, norm.getCoefficients().getEntry(0), EPS);
        assertEquals(-1.0, norm.getCoefficients().getEntry(1), EPS);
    }

    // normalizeConstraints: non-negative RHS stays unchanged
    @Test
    public void testNormalizeConstraints_nonNegativeValue_unchanged() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        SimplexTableau t = new SimplexTableau(f, singleton(new LinearConstraint(new double[]{1}, Relationship.EQ, 1)),
                GoalType.MINIMIZE, true, 1e-6);
        LinearConstraint c = new LinearConstraint(new double[]{2, -1}, Relationship.LEQ, 3);
        LinearConstraint norm = t.normalizeConstraints(singleton(c)).get(0);
        assertEquals(Relationship.LEQ, norm.getRelationship());
        assertEquals(3.0, norm.getValue(), EPS);
        assertEquals(2.0, norm.getCoefficients().getEntry(0), EPS);
        assertEquals(-1.0, norm.getCoefficients().getEntry(1), EPS);
    }

    // normalizeConstraints: EQ relationship's opposite is EQ (domain standard)
    @Test
    public void testNormalizeConstraints_eqRelationship_oppositeStaysEq() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        SimplexTableau t = new SimplexTableau(f, singleton(new LinearConstraint(new double[]{1}, Relationship.EQ, 1)),
                GoalType.MINIMIZE, true, 1e-6);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.EQ, -2);
        LinearConstraint norm = t.normalizeConstraints(singleton(c)).get(0);
        assertEquals(Relationship.EQ, norm.getRelationship());
        assertEquals(2.0, norm.getValue(), EPS);
        assertEquals(-1.0, norm.getCoefficients().getEntry(0), EPS);
    }

    // getInvertedCoefficientSum: -1*(sum of positive coefficients)
    @Test
    public void testGetInvertedCoefficientSum_positiveCoefficients_negatedSum() throws Throwable {
        RealVector v = new ArrayRealVector(new double[]{1, 2, 3});
        assertEquals(-6.0, SimplexTableau.getInvertedCoefficientSum(v), EPS);
    }

    // getInvertedCoefficientSum: negative coefficients produce a positive sum
    @Test
    public void testGetInvertedCoefficientSum_negativeCoefficients_positiveSum() throws Throwable {
        RealVector v = new ArrayRealVector(new double[]{-1, -2});
        assertEquals(3.0, SimplexTableau.getInvertedCoefficientSum(v), EPS);
    }

    // getBasicRow: exactly one 1 and rest 0 -> returns that row index
    @Test
    public void testGetBasicRow_singleOneRestZero_returnsRowIndex() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6, 0);
        t.setEntry(0, 1, 0);
        t.setEntry(1, 1, 1);
        assertEquals(Integer.valueOf(1), t.getBasicRow(1));
    }

    // getBasicRow: two entries equal to 1 in the column -> not basic, returns null
    @Test
    public void testGetBasicRow_twoOnesInColumn_returnsNull() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6, 0);
        t.setEntry(0, 2, 1);
        t.setEntry(1, 2, 1);
        assertNull(t.getBasicRow(2));
    }

    // getBasicRow: all-zero column -> no row has value 1, returns null
    @Test
    public void testGetBasicRow_allZeroColumn_returnsNull() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6, 0);
        t.setEntry(0, 3, 0);
        t.setEntry(1, 3, 0);
        assertNull(t.getBasicRow(3));
    }

    // dropPhase1Objective: no artificial variables -> getNumObjectiveFunctions()==1, no-op
    @Test
    public void testDropPhase1Objective_noArtificialVariables_isNoOp() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        int widthBefore = t.getWidth();
        int heightBefore = t.getHeight();
        t.dropPhase1Objective();
        assertEquals(widthBefore, t.getWidth());
        assertEquals(heightBefore, t.getHeight());
    }

    // dropPhase1Objective: drops W column, keeps basic artificial column structure, updates counts
    @Test
    public void testDropPhase1Objective_withArtificialVariable_dropsWColumnAndUpdatesCounts() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.EQ, 5);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6, 0);
        t.dropPhase1Objective();
        assertEquals(0, t.getNumArtificialVariables());
        assertEquals(1, t.getNumObjectiveFunctions());
        assertEquals(4, t.getWidth());
        assertEquals(2, t.getHeight());
        double[] expectedRow0 = {-1, 1, 0, 0};
        double[] expectedRow1 = {0, 1, 1, 5};
        for (int j = 0; j < 4; j++) {
            assertEquals(expectedRow0[j], t.getEntry(0, j), EPS);
            assertEquals(expectedRow1[j], t.getEntry(1, j), EPS);
        }
    }

    // isOptimal: all reduced costs >= 0 -> true
    @Test
    public void testIsOptimal_allNonNegativeReducedCosts_returnsTrue() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        t.setEntry(0, 1, 0);
        t.setEntry(0, 2, 5);
        assertTrue(t.isOptimal());
    }

    // isOptimal: a reduced cost clearly negative beyond epsilon -> false
    @Test
    public void testIsOptimal_negativeReducedCostBeyondEpsilon_returnsFalse() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        t.setEntry(0, 1, -5);
        assertFalse(t.isOptimal());
    }

    // isOptimal: negative value within epsilon tolerance is treated as optimal (boundary condition)
    @Test
    public void testIsOptimal_negativeWithinEpsilonTolerance_returnsTrue() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 0.1);
        t.setEntry(0, 1, -0.05);
        t.setEntry(0, 2, 1);
        assertTrue(t.isOptimal());
    }

    // getSolution: single basic variable per original variable, restrictToNonNegative=true
    @Test
    public void testGetSolution_singleBasicVariable_returnsCorrectPointAndValue() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{3, 2}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 0}, Relationship.LEQ, 4);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6, 0);
        t.subtractRow(0, 1, 3.0);
        RealPointValuePair sol = t.getSolution();
        assertArrayEquals(new double[]{4, 0}, sol.getPoint(), EPS);
        assertEquals(12.0, sol.getValue(), EPS);
    }

    // getSolution: two columns basic in the same row -> first keeps value, second is set to 0
    @Test
    public void testGetSolution_duplicateBasicRow_secondVariableSetToZero() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{0, 0}, Relationship.LEQ, 0);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6, 0);
        t.setEntry(0, 1, 0);
        t.setEntry(0, 2, 0);
        t.setEntry(1, 1, 1);
        t.setEntry(1, 2, 1);
        t.setEntry(1, 4, 7);
        RealPointValuePair sol = t.getSolution();
        assertArrayEquals(new double[]{7, 0}, sol.getPoint(), EPS);
        assertEquals(7.0, sol.getValue(), EPS);
    }

    // getSolution: restrictToNonNegative=false subtracts the shared negative-shift (x-) value
    @Test
    public void testGetSolution_notRestrictToNonNegative_subtractsMostNegative() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 5);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, false, 1e-6, 0);
        t.setEntry(0, 1, 0);
        t.setEntry(0, 2, 0);
        t.setEntry(1, 1, 1);
        t.setEntry(1, 2, 1);
        t.setEntry(1, 4, 10);
        RealPointValuePair sol = t.getSolution();
        assertArrayEquals(new double[]{0}, sol.getPoint(), EPS);
        assertEquals(0.0, sol.getValue(), EPS);
    }

    // divideRow: every entry of the row divided by the divisor
    @Test
    public void testDivideRow_dividesEveryEntryInRow() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        t.setEntry(0, 0, 10);
        t.setEntry(0, 1, 4);
        t.setEntry(0, 2, -6);
        t.setEntry(0, 3, 2);
        t.divideRow(0, 2.0);
        double[] expected = {5, 2, -3, 1};
        for (int j = 0; j < 4; j++) {
            assertEquals(expected[j], t.getEntry(0, j), EPS);
        }
    }

    // subtractRow: minuendRow = minuendRow - multiple * subtrahendRow
    @Test
    public void testSubtractRow_subtractsMultipleOfRow() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        t.setEntry(0, 0, 10);
        t.setEntry(0, 1, 4);
        t.setEntry(0, 2, -6);
        t.setEntry(0, 3, 2);
        t.setEntry(1, 0, 1);
        t.setEntry(1, 1, 1);
        t.setEntry(1, 2, 1);
        t.setEntry(1, 3, 1);
        t.subtractRow(0, 1, 2.0);
        double[] expected = {8, 2, -8, 0};
        for (int j = 0; j < 4; j++) {
            assertEquals(expected[j], t.getEntry(0, j), EPS);
        }
    }

    // getWidth/getHeight/getEntry/setEntry: dimensions and round-trip write/read are consistent
    @Test
    public void testGetWidthHeightEntrySetEntry_consistentWithDimensions() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertEquals(4, t.getWidth());
        assertEquals(2, t.getHeight());
        t.setEntry(0, 0, 99.5);
        assertEquals(99.5, t.getEntry(0, 0), EPS);
    }

    // offsets: slack/artificial/RHS offsets computed from objective/decision/slack counts
    @Test
    public void testGetOffsets_slackArtificialRhs_computedFromCounts() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 1}, Relationship.GEQ, 2);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertEquals(4, t.getSlackVariableOffset());
        assertEquals(5, t.getArtificialVariableOffset());
        assertEquals(6, t.getRhsOffset());
    }

    // getNumDecisionVariables/getOriginalNumDecisionVariables: differ by 1 when not restricted to non-negative
    @Test
    public void testGetNumDecisionAndOriginalDecisionVariables_notRestrictToNonNegative() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 0}, Relationship.LEQ, 4);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, false, 1e-6);
        assertEquals(2, t.getOriginalNumDecisionVariables());
        assertEquals(3, t.getNumDecisionVariables());
    }

    // getData: returned array dimensions and values match getWidth/getHeight/getEntry
    @Test
    public void testGetData_matchesGetEntryAndDimensions() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        t.setEntry(1, 2, 7.25);
        double[][] data = t.getData();
        assertEquals(t.getHeight(), data.length);
        assertEquals(t.getWidth(), data[0].length);
        assertEquals(7.25, data[1][2], EPS);
    }

    // equals: reflexive case (this == other) returns true; hashCode stable across calls
    @Test
    public void testEquals_sameInstance_trueAndHashCodeConsistent() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertTrue(t.equals(t));
        assertEquals(t.hashCode(), t.hashCode());
    }

    // equals: comparing with null must return false (not throw)
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertFalse(t.equals(null));
    }

    // equals: comparing with an unrelated type returns false
    @Test
    public void testEquals_differentType_returnsFalse() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1}, Relationship.LEQ, 1);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        assertFalse(t.equals("not a tableau"));
    }

    // equals: different epsilon short-circuits the && chain to false
    @Test
    public void testEquals_differentEpsilon_returnsFalse() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1}, 0);
        List<LinearConstraint> empty = new ArrayList<LinearConstraint>();
        SimplexTableau t1 = new SimplexTableau(f, empty, GoalType.MINIMIZE, true, 1e-6);
        SimplexTableau t2 = new SimplexTableau(f, empty, GoalType.MINIMIZE, true, 1e-3);
        assertFalse(t1.equals(t2));
    }

    // serialization round trip: writeObject/readObject must restore an equal tableau
    @Test
    public void testSerialization_roundTrip_producesEqualTableau() throws Throwable {
        LinearObjectiveFunction f = new LinearObjectiveFunction(new double[]{1, 1}, 0);
        LinearConstraint c = new LinearConstraint(new double[]{1, 0}, Relationship.LEQ, 4);
        SimplexTableau t = new SimplexTableau(f, singleton(c), GoalType.MINIMIZE, true, 1e-6);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(t);
        oos.close();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        SimplexTableau restored = (SimplexTableau) ois.readObject();
        assertTrue(t.equals(restored));
    }
}
