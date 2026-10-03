package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class EigenDecompositionImplTest {

    @Test
    public void testSymmetricMatrixDecomposition() throws Throwable {
        double[][] data = {
            {2.0, 1.0},
            {1.0, 2.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        EigenDecompositionImpl ed = new EigenDecompositionImpl(matrix, 1.0e-9);

        RealMatrix v = ed.getV();
        RealMatrix d = ed.getD();
        RealMatrix vt = ed.getVT();

        assertNotNull(v);
        assertNotNull(d);
        assertNotNull(vt);

        double[] realEigenvalues = ed.getRealEigenvalues();
        assertEquals(2, realEigenvalues.length);
        assertEquals(3.0, ed.getRealEigenvalue(0), 1.0e-6);
        assertEquals(1.0, ed.getRealEigenvalue(1), 1.0e-6);

        double[] imagEigenvalues = ed.getImagEigenvalues();
        assertEquals(2, imagEigenvalues.length);
        assertEquals(0.0, ed.getImagEigenvalue(0), 1.0e-6);

        RealVector ev = ed.getEigenvector(0);
        assertNotNull(ev);

        double det = ed.getDeterminant();
        assertEquals(3.0, det, 1.0e-6);

        DecompositionSolver solver = ed.getSolver();
        assertNotNull(solver);
        assertTrue(solver.isNonSingular());

        double[] b = {1.0, 1.0};
        double[] x = solver.solve(b);
        assertEquals(2, x.length);

        RealVector bVec = new ArrayRealVector(b);
        RealVector xVec = solver.solve(bVec);
        assertNotNull(xVec);

        RealMatrix bMat = MatrixUtils.createRealMatrix(new double[][]{{1.0}, {1.0}});
        RealMatrix xMat = solver.solve(bMat);
        assertNotNull(xMat);

        RealMatrix inv = solver.getInverse();
        assertNotNull(inv);
    }

    @Test
    public void testTridiagonalArraysConstructor() throws Throwable {
        double[] main = {2.0, 2.0, 2.0};
        double[] secondary = {1.0, 1.0};
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        double[] realEigenvalues = ed.getRealEigenvalues();
        assertEquals(3, realEigenvalues.length);
    }

    @Test
    public void testAsymmetricMatrixThrowsException() throws Throwable {
        double[][] data = {
            {2.0, 1.0},
            {0.0, 2.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        try {
            new EigenDecompositionImpl(matrix, 1.0e-9);
            fail("Expected InvalidMatrixException for asymmetric matrix");
        } catch (InvalidMatrixException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testSingularMatrixSolverThrowsException() throws Throwable {
        double[] main = {0.0, 2.0};
        double[] secondary = {0.0};
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);
        DecompositionSolver solver = ed.getSolver();
        assertFalse(solver.isNonSingular());

        try {
            solver.solve(new double[]{1.0, 1.0});
            fail("Expected SingularMatrixException");
        } catch (SingularMatrixException e) {
            assertTrue(true);
        }

        try {
            solver.getInverse();
            fail("Expected SingularMatrixException");
        } catch (SingularMatrixException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testDimensionMismatchSolver() throws Throwable {
        double[] main = {2.0, 2.0};
        double[] secondary = {1.0};
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);
        DecompositionSolver solver = ed.getSolver();

        try {
            solver.solve(new double[]{1.0});
            fail("Expected IllegalArgumentException due to dimension mismatch");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            solver.solve(new ArrayRealVector(new double[]{1.0}));
            fail("Expected IllegalArgumentException due to dimension mismatch");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            solver.solve(MatrixUtils.createRealMatrix(new double[][]{{1.0}}));
            fail("Expected IllegalArgumentException due to dimension mismatch");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testProcess1RowBlock() throws Throwable {
        double[] main = {5.0};
        double[] secondary = new double[0];
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);
        assertEquals(5.0, ed.getRealEigenvalue(0), 1.0e-6);
    }

    @Test
    public void testProcess2RowsBlock() throws Throwable {
        double[] main = {2.0, 3.0};
        double[] secondary = {0.1};
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);
        assertEquals(2, ed.getRealEigenvalues().length);
    }

    @Test
    public void testProcess3RowsBlock() throws Throwable {
        double[] main = {2.0, 3.0, 4.0};
        double[] secondary = {0.1, 0.1};
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);
        assertEquals(3, ed.getRealEigenvalues().length);
    }
}