package org.apache.commons.math.linear;

import org.apache.commons.math.util.MathUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class EigenDecompositionImplClaudeTest {

    private EigenDecompositionImpl m1() throws Throwable {
        // dense symmetric 2x2 -> eigenvalues {3,1} (process2RowsBlock)
        double[][] data = {{2.0, 1.0}, {1.0, 2.0}};
        return new EigenDecompositionImpl(MatrixUtils.createRealMatrix(data), MathUtils.SAFE_MIN);
    }

    private EigenDecompositionImpl m2() throws Throwable {
        // tridiagonal, zero off-diagonal -> 3 separate 1-row blocks, eigen {3,2,1}
        return new EigenDecompositionImpl(new double[]{3.0, 1.0, 2.0}, new double[]{0.0, 0.0}, MathUtils.SAFE_MIN);
    }

    private EigenDecompositionImpl m3() throws Throwable {
        // genuine 3-row tridiagonal block -> process3RowsBlock
        return new EigenDecompositionImpl(new double[]{2.0, 2.0, 2.0}, new double[]{1.0, 1.0}, MathUtils.SAFE_MIN);
    }

    private EigenDecompositionImpl m4() throws Throwable {
        // genuine 4-row tridiagonal block -> processGeneralBlock (default case)
        return new EigenDecompositionImpl(new double[]{2.0, 2.0, 2.0, 2.0}, new double[]{1.0, 1.0, 1.0}, MathUtils.SAFE_MIN);
    }

    private EigenDecompositionImpl m5() throws Throwable {
        // dense symmetric 3x3 requiring Householder tridiagonalization, eigen {4,1,1}
        double[][] data = {{2.0, 1.0, 1.0}, {1.0, 2.0, 1.0}, {1.0, 1.0, 2.0}};
        return new EigenDecompositionImpl(MatrixUtils.createRealMatrix(data), MathUtils.SAFE_MIN);
    }

    private EigenDecompositionImpl m6() throws Throwable {
        // trivial 1x1 matrix
        double[][] data = {{5.0}};
        return new EigenDecompositionImpl(MatrixUtils.createRealMatrix(data), MathUtils.SAFE_MIN);
    }

    private EigenDecompositionImpl m9() throws Throwable {
        // nonsingular diagonal 2x2 matrix diag(2,4) for solver tests
        return new EigenDecompositionImpl(new double[]{2.0, 4.0}, new double[]{0.0}, MathUtils.SAFE_MIN);
    }

    private EigenDecompositionImpl m10() throws Throwable {
        // singular diagonal 2x2 matrix diag(0,5) for solver tests
        return new EigenDecompositionImpl(new double[]{0.0, 5.0}, new double[]{0.0}, MathUtils.SAFE_MIN);
    }

    private void assertReconstruction(double[][] original, EigenDecompositionImpl ed, double delta) throws Throwable {
        RealMatrix v = ed.getV();
        RealMatrix d = ed.getD();
        RealMatrix vt = ed.getVT();
        int n = original.length;
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                double sum = 0.0;
                for (int k = 0; k < n; ++k) {
                    sum += v.getEntry(i, k) * d.getEntry(k, k) * vt.getEntry(k, j);
                }
                assertEquals(original[i][j], sum, delta);
            }
        }
    }

    // constructor: matrix with mij != mji -> InvalidMatrixException
    @Test
    public void testConstructor_nonSymmetricMatrix_throwsInvalidMatrixException() throws Throwable {
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}};
        try {
            new EigenDecompositionImpl(MatrixUtils.createRealMatrix(data), MathUtils.SAFE_MIN);
            fail("expected InvalidMatrixException");
        } catch (InvalidMatrixException expected) {
            // expected
        }
    }

    // process2RowsBlock: closed form roots of 2x2 symmetric matrix
    @Test
    public void testConstructor_2x2Symmetric_eigenvaluesMatchClosedForm() throws Throwable {
        EigenDecompositionImpl ed = m1();
        double[] eig = ed.getRealEigenvalues();
        assertEquals(2, eig.length);
        assertEquals(3.0, eig[0], 1e-9);
        assertEquals(1.0, eig[1], 1e-9);
    }

    // computeSplits: all secondary entries zero -> multiple process1RowBlock calls, sorted descending
    @Test
    public void testTridiagonalConstructor_threeDistinctDiagonal_eigenvaluesSortedDescending() throws Throwable {
        EigenDecompositionImpl ed = m2();
        double[] eig = ed.getRealEigenvalues();
        assertEquals(3.0, eig[0], 1e-9);
        assertEquals(2.0, eig[1], 1e-9);
        assertEquals(1.0, eig[2], 1e-9);
    }

    // process3RowsBlock with known tridiagonal spectrum a+2b*cos(k*pi/(n+1))
    @Test
    public void testTridiagonalConstructor_3x3_eigenvaluesMatchClosedForm() throws Throwable {
        EigenDecompositionImpl ed = m3();
        double[] eig = ed.getRealEigenvalues();
        assertEquals(2.0 + Math.sqrt(2.0), eig[0], 1e-6);
        assertEquals(2.0, eig[1], 1e-6);
        assertEquals(2.0 - Math.sqrt(2.0), eig[2], 1e-6);
    }

    // default case in findEigenvalues (n=4) -> processGeneralBlock dqds path
    @Test
    public void testTridiagonalConstructor_4x4_eigenvaluesMatchClosedForm() throws Throwable {
        EigenDecompositionImpl ed = m4();
        double[] eig = ed.getRealEigenvalues();
        double c1 = Math.cos(Math.PI / 5.0);
        double c2 = Math.cos(2.0 * Math.PI / 5.0);
        assertEquals(2.0 + 2.0 * c1, eig[0], 1e-6);
        assertEquals(2.0 + 2.0 * c2, eig[1], 1e-6);
        assertEquals(2.0 - 2.0 * c2, eig[2], 1e-6);
        assertEquals(2.0 - 2.0 * c1, eig[3], 1e-6);
    }

    // trivial 1x1 block
    @Test
    public void testConstructor_1x1Matrix_eigenvalueEqualsEntry() throws Throwable {
        EigenDecompositionImpl ed = m6();
        assertEquals(1, ed.getRealEigenvalues().length);
        assertEquals(5.0, ed.getRealEigenvalue(0), 1e-9);
    }



    // getD(): diagonal matrix matches eigenvalues, off-diagonal zero
    @Test
    public void testGetD_diagonalEntriesMatchEigenvalues_offDiagonalZero() throws Throwable {
        EigenDecompositionImpl ed = m4();
        RealMatrix d = ed.getD();
        double[] eig = ed.getRealEigenvalues();
        for (int i = 0; i < 4; ++i) {
            assertEquals(eig[i], d.getEntry(i, i), 1e-6);
        }
        assertEquals(0.0, d.getEntry(0, 1), 0.0);
        assertEquals(0.0, d.getEntry(2, 3), 0.0);
    }

    // getV()/getVT(): VT must be the exact transpose of V
    @Test
    public void testGetV_getVT_transposeConsistency() throws Throwable {
        EigenDecompositionImpl ed = m1();
        RealMatrix v = ed.getV();
        RealMatrix vt = ed.getVT();
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                assertEquals(v.getEntry(i, j), vt.getEntry(j, i), 1e-12);
            }
        }
    }

    // reconstruction A = V*D*V^T for tridiagonal-array constructed matrix
    @Test
    public void testReconstruction_VDVt_equalsOriginal_tridiagonalArrayCtor() throws Throwable {
        double[][] original = {{2.0, 1.0, 0.0}, {1.0, 2.0, 1.0}, {0.0, 1.0, 2.0}};
        assertReconstruction(original, m3(), 1e-6);
    }



    // getRealEigenvalues() must return a defensive clone
    @Test
    public void testGetRealEigenvalues_returnsIndependentClone() throws Throwable {
        EigenDecompositionImpl ed = m1();
        double[] a = ed.getRealEigenvalues();
        a[0] = -999.0;
        double[] b = ed.getRealEigenvalues();
        assertEquals(3.0, b[0], 1e-9);
    }

    // getRealEigenvalue(i): valid index returns expected value
    @Test
    public void testGetRealEigenvalue_validIndex() throws Throwable {
        EigenDecompositionImpl ed = m2();
        assertEquals(2.0, ed.getRealEigenvalue(1), 1e-9);
    }

    // getRealEigenvalue(i): negative index out of bounds
    @Test
    public void testGetRealEigenvalue_negativeIndex_throws() throws Throwable {
        EigenDecompositionImpl ed = m1();
        try {
            ed.getRealEigenvalue(-1);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // expected
        }
    }

    // getRealEigenvalue(i): index == length out of bounds
    @Test
    public void testGetRealEigenvalue_tooLargeIndex_throws() throws Throwable {
        EigenDecompositionImpl ed = m1();
        try {
            ed.getRealEigenvalue(2);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // expected
        }
    }



    // getImagEigenvalue(i): valid index is zero
    @Test
    public void testGetImagEigenvalue_validIndex_isZero() throws Throwable {
        EigenDecompositionImpl ed = m1();
        assertEquals(0.0, ed.getImagEigenvalue(0), 0.0);
    }

    // getImagEigenvalue(i): out of bounds
    @Test
    public void testGetImagEigenvalue_outOfBounds_throws() throws Throwable {
        EigenDecompositionImpl ed = m1();
        try {
            ed.getImagEigenvalue(2);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // expected
        }
    }



    // getEigenvector(i): out of bounds
    @Test
    public void testGetEigenvector_outOfBounds_throws() throws Throwable {
        EigenDecompositionImpl ed = m1();
        try {
            ed.getEigenvector(2);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // expected
        }
    }

    // determinant = product of eigenvalues, diagonal matrix
    @Test
    public void testGetDeterminant_diagonalMatrix() throws Throwable {
        EigenDecompositionImpl ed = m2();
        assertEquals(6.0, ed.getDeterminant(), 1e-6);
    }





    // solve(double[]): mismatched vector length -> IllegalArgumentException
    @Test
    public void testGetSolver_solveDoubleArray_dimensionMismatch_throws() throws Throwable {
        DecompositionSolver solver = m9().getSolver();
        try {
            solver.solve(new double[]{1.0});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }



    // solve(RealVector): mismatched dimension -> IllegalArgumentException
    @Test
    public void testGetSolver_solveRealVector_dimensionMismatch_throws() throws Throwable {
        DecompositionSolver solver = m9().getSolver();
        try {
            solver.solve(new ArrayRealVector(new double[]{1.0, 2.0, 3.0}, false));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }



    // solve(RealMatrix): mismatched row dimension -> IllegalArgumentException
    @Test
    public void testGetSolver_solveRealMatrix_dimensionMismatch_throws() throws Throwable {
        DecompositionSolver solver = m9().getSolver();
        double[][] bad = {{1.0}, {2.0}, {3.0}};
        try {
            solver.solve(MatrixUtils.createRealMatrix(bad));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // isNonSingular(): true when no eigenvalue is zero
    @Test
    public void testGetSolver_isNonSingular_trueForNonSingular() throws Throwable {
        DecompositionSolver solver = m9().getSolver();
        assertTrue(solver.isNonSingular());
    }

    // isNonSingular(): false when an eigenvalue is exactly zero
    @Test
    public void testGetSolver_isNonSingular_falseForSingular() throws Throwable {
        DecompositionSolver solver = m10().getSolver();
        assertFalse(solver.isNonSingular());
    }

    // solve on a singular matrix must throw SingularMatrixException
    @Test
    public void testGetSolver_solve_singularMatrix_throwsSingularMatrixException() throws Throwable {
        DecompositionSolver solver = m10().getSolver();
        try {
            solver.solve(new double[]{1.0, 1.0});
            fail("expected SingularMatrixException");
        } catch (SingularMatrixException expected) {
            // expected
        }
    }



    // getInverse() of a singular matrix must throw SingularMatrixException
    @Test
    public void testGetSolver_getInverse_singularMatrix_throws() throws Throwable {
        DecompositionSolver solver = m10().getSolver();
        try {
            solver.getInverse();
            fail("expected SingularMatrixException");
        } catch (SingularMatrixException expected) {
            // expected
        }
    }
}
