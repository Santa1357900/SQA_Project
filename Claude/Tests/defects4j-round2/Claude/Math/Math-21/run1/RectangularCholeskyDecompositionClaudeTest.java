package org.apache.commons.math3.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class RectangularCholeskyDecompositionClaudeTest {

    private RealMatrix diag(double[] values) {
        int n = values.length;
        RealMatrix m = MatrixUtils.createRealMatrix(n, n);
        for (int i = 0; i < n; i++) {
            m.setEntry(i, i, values[i]);
        }
        return m;
    }

    private RealMatrix buildMatrix(double[][] data) {
        int n = data.length;
        RealMatrix m = MatrixUtils.createRealMatrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m.setEntry(i, j, data[i][j]);
            }
        }
        return m;
    }

    private void assertReconstructs(double[][] expected, RealMatrix root, double delta) {
        int rows = root.getRowDimension();
        int cols = root.getColumnDimension();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < rows; j++) {
                double sum = 0.0;
                for (int k = 0; k < cols; k++) {
                    sum += root.getEntry(i, k) * root.getEntry(j, k);
                }
                assertEquals(expected[i][j], sum, delta);
            }
        }
    }

    // all diagonal values equal: strict '>' comparison never swaps, full rank reached
    @Test
    public void testConstructor_identicalDiagonalValues_noSwap_fullRank() throws Throwable {
        RealMatrix m = diag(new double[]{5, 5, 5});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(3, d.getRank());
        assertReconstructs(new double[][]{{5,0,0},{0,5,0},{0,0,5}}, d.getRootMatrix(), 1e-9);
    }

    // diagonal already descending: no swap needed, full rank, exact reconstruction
    @Test
    public void testConstructor_descendingDiagonalValues_fullRank_reconstructs() throws Throwable {
        RealMatrix m = diag(new double[]{16, 9, 4});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(3, d.getRank());
        assertReconstructs(new double[][]{{16,0,0},{0,9,0},{0,0,4}}, d.getRootMatrix(), 1e-9);
    }

    // off-diagonal 2x2, second diagonal larger than first: swap[r]!=r branch executes
    @Test
    public void testConstructor_offDiagonal2x2_swapOccurs_reconstructs() throws Throwable {
        double[][] data = {{2, 1}, {1, 5}};
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(buildMatrix(data), 1e-10);
        assertEquals(2, d.getRank());
        assertReconstructs(data, d.getRootMatrix(), 1e-9);
    }

    // off-diagonal 2x2, first diagonal already max: swap[r]==r branch, elimination with e!=0
    @Test
    public void testConstructor_offDiagonal2x2_noSwapNeeded_reconstructs() throws Throwable {
        double[][] data = {{4, 2}, {2, 3}};
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(buildMatrix(data), 1e-10);
        assertEquals(2, d.getRank());
        assertReconstructs(data, d.getRootMatrix(), 1e-9);
    }

    // order 1, value above threshold: rank 1, root = sqrt(value)
    @Test
    public void testConstructor_order1_aboveThreshold_rankOne() throws Throwable {
        RealMatrix m = diag(new double[]{5.0});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(1, d.getRank());
        assertEquals(Math.sqrt(5.0), d.getRootMatrix().getEntry(0, 0), 1e-9);
    }

    // order 1, value below threshold at r==0: must throw NonPositiveDefiniteMatrixException
    @Test
    public void testConstructor_order1_belowThreshold_throwsAtFirstPivot() throws Throwable {
        RealMatrix m = diag(new double[]{1e-15});
        try {
            new RectangularCholeskyDecomposition(m, 1e-10);
            fail("expected NonPositiveDefiniteMatrixException");
        } catch (NonPositiveDefiniteMatrixException expected) {
        }
    }

    // all-zero 2x2 matrix: max diagonal (0) below positive threshold at r==0
    @Test
    public void testConstructor_allZero2x2_throwsAtFirstPivot() throws Throwable {
        RealMatrix m = diag(new double[]{0, 0});
        try {
            new RectangularCholeskyDecomposition(m, 1e-10);
            fail("expected NonPositiveDefiniteMatrixException");
        } catch (NonPositiveDefiniteMatrixException expected) {
        }
    }

    // all-negative 2x2 diagonal: even the max is below threshold at r==0
    @Test
    public void testConstructor_allNegativeDiagonal2x2_throwsAtFirstPivot() throws Throwable {
        RealMatrix m = diag(new double[]{-5, -3});
        try {
            new RectangularCholeskyDecomposition(m, 1e-10);
            fail("expected NonPositiveDefiniteMatrixException");
        } catch (NonPositiveDefiniteMatrixException expected) {
        }
    }

    // r==0 succeeds, but a later diagonal is below -small: r!=0 "remaining" throw branch
    @Test
    public void testConstructor_negativeDiagonalInRemainingCheck_throws() throws Throwable {
        RealMatrix m = diag(new double[]{-5, 1});
        try {
            new RectangularCholeskyDecomposition(m, 1e-10);
            fail("expected NonPositiveDefiniteMatrixException");
        } catch (NonPositiveDefiniteMatrixException expected) {
        }
    }

    // boundary: remaining diagonal exactly == -small must NOT throw (strict '<' required)
    @Test
    public void testConstructor_diagonalExactlyAtNegativeThresholdBoundary_noThrow() throws Throwable {
        RealMatrix m = diag(new double[]{5.0, -0.01});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 0.01);
        assertEquals(2, d.getRank());
        assertEquals(Math.sqrt(5.0), d.getRootMatrix().getEntry(0, 0), 1e-9);
        assertEquals(0.0, d.getRootMatrix().getEntry(1, 1), 1e-9);
    }

    // boundary: remaining diagonal strictly below -small must throw
    @Test
    public void testConstructor_diagonalJustBeyondNegativeThresholdBoundary_throws() throws Throwable {
        RealMatrix m = diag(new double[]{5.0, -0.011});
        try {
            new RectangularCholeskyDecomposition(m, 0.01);
            fail("expected NonPositiveDefiniteMatrixException");
        } catch (NonPositiveDefiniteMatrixException expected) {
        }
    }

    // tail diagonal entries below threshold (but not negative beyond -small): rank truncated
    @Test
    public void testConstructor_rankDeficientTailBelowThreshold_truncatesRank() throws Throwable {
        RealMatrix m = diag(new double[]{100, 1e-12, 1e-13});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(2, d.getRank());
        assertEquals(3, d.getRootMatrix().getRowDimension());
        assertEquals(2, d.getRootMatrix().getColumnDimension());
        assertEquals(10.0, d.getRootMatrix().getEntry(0, 0), 1e-9);
    }

    // threshold exactly zero with strictly positive diagonal: all processed, full rank
    @Test
    public void testConstructor_thresholdZero_allPositiveDiagonal_fullRank() throws Throwable {
        RealMatrix m = diag(new double[]{5, 3, 1});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 0.0);
        assertEquals(3, d.getRank());
        assertReconstructs(new double[][]{{5,0,0},{0,3,0},{0,0,1}}, d.getRootMatrix(), 1e-9);
    }

    // valid PSD matrix whose true maximal diagonal is NOT at the first slot: wrong pivoting
    // would incorrectly select a near-zero diagonal at r==0 and throw; correct pivoting must not throw
    @Test
    public void testConstructor_bugPivotSelection_validPSDMatrix_shouldNotThrow() throws Throwable {
        RealMatrix m = diag(new double[]{1e-15, 1.0, 1e-12});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertTrue(d.getRank() >= 1 && d.getRank() <= 3);
        RealMatrix root = d.getRootMatrix();
        assertEquals(3, root.getRowDimension());
        double sumSq = 0.0;
        for (int k = 0; k < root.getColumnDimension(); k++) {
            sumSq += root.getEntry(1, k) * root.getEntry(1, k);
        }
        assertEquals(1.0, sumSq, 1e-9);
    }

    // root matrix dimensions must be order x rank
    @Test
    public void testGetRootMatrix_dimensions_rowsOrderColsRank() throws Throwable {
        RealMatrix m = diag(new double[]{16, 9, 4});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(3, d.getRootMatrix().getRowDimension());
        assertEquals(d.getRank(), d.getRootMatrix().getColumnDimension());
    }

    // getRank on a full-rank 2x2 positive definite matrix
    @Test
    public void testGetRank_fullRankOrder2() throws Throwable {
        double[][] data = {{4, 2}, {2, 3}};
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(buildMatrix(data), 1e-10);
        assertEquals(2, d.getRank());
    }

    // getRootMatrix must satisfy B.B^T == A for a coupled 2x2 matrix
    @Test
    public void testGetRootMatrix_reconstructsOriginal_2x2() throws Throwable {
        double[][] data = {{2, 1}, {1, 5}};
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(buildMatrix(data), 1e-10);
        assertReconstructs(data, d.getRootMatrix(), 1e-9);
    }

    // getRootMatrix must satisfy B.B^T == A for a 3x3 diagonal matrix
    @Test
    public void testGetRootMatrix_reconstructsOriginal_3x3Diagonal() throws Throwable {
        double[][] data = {{16,0,0},{0,9,0},{0,0,4}};
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(buildMatrix(data), 1e-10);
        assertReconstructs(data, d.getRootMatrix(), 1e-9);
    }

    // order 1 root entry equals sqrt of the single diagonal value
    @Test
    public void testConstructor_order1_rootEntryMatchesSqrt() throws Throwable {
        RealMatrix m = diag(new double[]{7.0});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(Math.sqrt(7.0), d.getRootMatrix().getEntry(0, 0), 1e-9);
    }

    // identity matrix: root must equal identity (diagonal 1s, zero elsewhere)
    @Test
    public void testConstructor_identityMatrix3x3_rootEqualsIdentity() throws Throwable {
        RealMatrix m = diag(new double[]{1, 1, 1});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(3, d.getRank());
        RealMatrix root = d.getRootMatrix();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(i == j ? 1.0 : 0.0, root.getEntry(i, j), 1e-9);
            }
        }
    }

    // last diagonal exactly equal to threshold: NOT discarded, value is actually computed
    @Test
    public void testConstructor_positiveThresholdBoundaryExact_lastPivotProcessed() throws Throwable {
        RealMatrix m = diag(new double[]{10, 5, 2.0});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 2.0);
        assertEquals(3, d.getRank());
        assertEquals(Math.sqrt(2.0), d.getRootMatrix().getEntry(2, 2), 1e-9);
    }

    // last diagonal just below threshold: discarded, entry stays uncomputed (zero)
    @Test
    public void testConstructor_justBelowPositiveThresholdBoundary_lastPivotDiscarded() throws Throwable {
        RealMatrix m = diag(new double[]{10, 5, 1.999999});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 2.0);
        assertEquals(0.0, d.getRootMatrix().getEntry(2, 2), 1e-9);
    }

    // order 4 strictly descending diagonal: exercises multiple loop rounds, full rank
    @Test
    public void testConstructor_order4DescendingDiagonal_fullRank_reconstructs() throws Throwable {
        RealMatrix m = diag(new double[]{25, 16, 9, 4});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(4, d.getRank());
        double[][] expected = {{25,0,0,0},{0,16,0,0},{0,0,9,0},{0,0,0,4}};
        assertReconstructs(expected, d.getRootMatrix(), 1e-9);
    }

    // dedicated getRank check for the rank-deficient tail scenario
    @Test
    public void testGetRank_rankDeficientMatrix_equalsTwo() throws Throwable {
        RealMatrix m = diag(new double[]{100, 1e-12, 1e-13});
        RectangularCholeskyDecomposition d = new RectangularCholeskyDecomposition(m, 1e-10);
        assertEquals(2, d.getRank());
    }
}
