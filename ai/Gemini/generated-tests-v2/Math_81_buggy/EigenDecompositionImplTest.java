package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.InvalidMatrixException;

public class EigenDecompositionImplTest {

    @Test(expected = InvalidMatrixException.class)
    public void testNonSymmetricMatrixThrowsException() throws Throwable {
        double[][] data = new double[][] {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        new EigenDecompositionImpl(matrix, 1.0e-9);
    }

    @Test
    public void test1DimensionDecomposition() throws Throwable {
        double[] main = new double[] { 5.0 };
        double[] secondary = new double[] { };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        double[] realEigenvalues = ed.getRealEigenvalues();
        assertEquals(1, realEigenvalues.length);
        assertEquals(5.0, realEigenvalues[0], 1.0e-12);

        double[] imagEigenvalues = ed.getImagEigenvalues();
        assertEquals(1, imagEigenvalues.length);
        assertEquals(0.0, imagEigenvalues[0], 1.0e-12);

        assertEquals(5.0, ed.getRealEigenvalue(0), 1.0e-12);
        assertEquals(0.0, ed.getImagEigenvalue(0), 1.0e-12);
        assertEquals(5.0, ed.getDeterminant(), 1.0e-12);

        RealMatrix v = ed.getV();
        assertEquals(1, v.getRowDimension());
        assertEquals(1, v.getColumnDimension());
        assertEquals(1.0, v.getEntry(0, 0), 1.0e-12);

        RealMatrix vt = ed.getVT();
        assertEquals(1, vt.getRowDimension());
        assertEquals(1, vt.getColumnDimension());

        RealMatrix d = ed.getD();
        assertEquals(5.0, d.getEntry(0, 0), 1.0e-12);

        RealVector ev = ed.getEigenvector(0);
        assertEquals(1, ev.getDimension());

        DecompositionSolver solver = ed.getSolver();
        assertTrue(solver.isNonSingular());
        double[] b = new double[] { 10.0 };
        double[] x = solver.solve(b);
        assertEquals(1, x.length);
        assertEquals(2.0, x[0], 1.0e-12);

        RealVector bVec = new ArrayRealVector(new double[] { 10.0 }, false);
        RealVector xVec = solver.solve(bVec);
        assertEquals(2.0, xVec.getEntry(0), 1.0e-12);

        RealMatrix bMat = MatrixUtils.createRealMatrix(new double[][] { { 10.0 } });
        RealMatrix xMat = solver.solve(bMat);
        assertEquals(2.0, xMat.getEntry(0, 0), 1.0e-12);

        RealMatrix inv = solver.getInverse();
        assertEquals(0.2, inv.getEntry(0, 0), 1.0e-12);
    }

    @Test
    public void test2DimensionDecomposition() throws Throwable {
        double[] main = new double[] { 2.0, 3.0 };
        double[] secondary = new double[] { 1.0 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        double[] realEigenvalues = ed.getRealEigenvalues();
        assertEquals(2, realEigenvalues.length);

        RealMatrix v = ed.getV();
        assertEquals(2, v.getRowDimension());
        assertEquals(2, v.getColumnDimension());

        RealMatrix vt = ed.getVT();
        assertEquals(2, vt.getRowDimension());
        assertEquals(2, vt.getColumnDimension());

        DecompositionSolver solver = ed.getSolver();
        assertTrue(solver.isNonSingular());
        double[] b = new double[] { 1.0, 1.0 };
        double[] x = solver.solve(b);
        assertEquals(2, x.length);
    }

    @Test
    public void test3DimensionDecomposition() throws Throwable {
        double[] main = new double[] { 2.0, 2.0, 2.0 };
        double[] secondary = new double[] { 0.1, 0.1 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        double[] realEigenvalues = ed.getRealEigenvalues();
        assertEquals(3, realEigenvalues.length);

        DecompositionSolver solver = ed.getSolver();
        assertTrue(solver.isNonSingular());
        RealMatrix inv = solver.getInverse();
        assertEquals(3, inv.getRowDimension());
    }

    @Test
    public void testGeneralBlockDecomposition() throws Throwable {
        double[] main = new double[] { 4.0, 4.0, 4.0, 4.0, 4.0 };
        double[] secondary = new double[] { 1.0, 1.0, 1.0, 1.0 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        double[] realEigenvalues = ed.getRealEigenvalues();
        assertEquals(5, realEigenvalues.length);
        
        DecompositionSolver solver = ed.getSolver();
        assertTrue(solver.isNonSingular());
    }

    @Test
    public void testSingularMatrixSolver() throws Throwable {
        double[] main = new double[] { 0.0, 2.0 };
        double[] secondary = new double[] { 0.0 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        DecompositionSolver solver = ed.getSolver();
        assertFalse(solver.isNonSingular());
    }

    @Test(expected = InvalidMatrixException.class)
    public void testSingularMatrixInverseThrowsException() throws Throwable {
        double[] main = new double[] { 0.0, 2.0 };
        double[] secondary = new double[] { 0.0 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        DecompositionSolver solver = ed.getSolver();
        solver.getInverse();
    }

    @Test(expected = InvalidMatrixException.class)
    public void testSingularMatrixSolveThrowsException() throws Throwable {
        double[] main = new double[] { 0.0, 2.0 };
        double[] secondary = new double[] { 0.0 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        DecompositionSolver solver = ed.getSolver();
        solver.solve(new double[] { 1.0, 1.0 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVectorLengthMismatchSolve() throws Throwable {
        double[] main = new double[] { 2.0, 3.0 };
        double[] secondary = new double[] { 0.1 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        DecompositionSolver solver = ed.getSolver();
        solver.solve(new double[] { 1.0 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRealVectorLengthMismatchSolve() throws Throwable {
        double[] main = new double[] { 2.0, 3.0 };
        double[] secondary = new double[] { 0.1 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        DecompositionSolver solver = ed.getSolver();
        solver.solve(new ArrayRealVector(new double[] { 1.0 }, false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRealMatrixDimensionMismatchSolve() throws Throwable {
        double[] main = new double[] { 2.0, 3.0 };
        double[] secondary = new double[] { 0.1 };
        EigenDecompositionImpl ed = new EigenDecompositionImpl(main, secondary, 1.0e-9);

        DecompositionSolver solver = ed.getSolver();
        solver.solve(MatrixUtils.createRealMatrix(new double[][] { { 1.0 } }));
    }

    @Test
    public void testMatrixConstructorWithSymmetricMatrix() throws Throwable {
        double[][] data = new double[][] {
            {4.0, 1.0},
            {1.0, 3.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        EigenDecompositionImpl ed = new EigenDecompositionImpl(matrix, 1.0e-9);
        assertNotNull(ed.getRealEigenvalues());
    }

}