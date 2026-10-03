package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class CholeskyDecompositionImplTest {

    @Test
    public void testValidCholeskyDecomposition() throws Throwable {
        double[][] data = {
            {4.0, 1.0, -1.0},
            {1.0, 3.0,  0.0},
            {-1.0, 0.0, 3.0}
        };
        RealMatrix matrix = new RealMatrixImpl(data);
        CholeskyDecomposition decomp = new CholeskyDecompositionImpl(matrix);

        RealMatrix l = decomp.getL();
        RealMatrix lt = decomp.getLT();
        assertNotNull(l);
        assertNotNull(lt);

        double det = decomp.getDeterminant();
        assertTrue(det > 0.0);

        DecompositionSolver solver = decomp.getSolver();
        assertTrue(solver.isNonSingular());

        double[] b = {1.0, 2.0, 3.0};
        double[] x = solver.solve(b);
        assertNotNull(x);
        assertEquals(3, x.length);

        RealVector bVec = new RealVectorImpl(b);
        RealVector xVec = solver.solve(bVec);
        assertNotNull(xVec);
        assertEquals(3, xVec.getDimension());

        RealVectorImpl bVecImpl = new RealVectorImpl(b);
        RealVectorImpl xVecImpl = solver.solve(bVecImpl);
        assertNotNull(xVecImpl);

        RealMatrix bMat = new RealMatrixImpl(new double[][]{{1.0}, {2.0}, {3.0}});
        RealMatrix xMat = solver.solve(bMat);
        assertNotNull(xMat);

        RealMatrix inverse = solver.getInverse();
        assertNotNull(inverse);
    }

    @Test
    public void testNonSquareMatrixException() throws Throwable {
        double[][] data = {
            {1.0, 2.0, 3.0},
            {4.0, 5.0, 6.0}
        };
        RealMatrix matrix = new RealMatrixImpl(data);
        try {
            new CholeskyDecompositionImpl(matrix);
            fail("Expected NonSquareMatrixException");
        } catch (NonSquareMatrixException e) {
            // Expected
        }
    }

    @Test
    public void testNotSymmetricMatrixException() throws Throwable {
        double[][] data = {
            {4.0, 1.5, -1.0},
            {1.0, 3.0,  0.0},
            {-1.0, 0.0, 3.0}
        };
        RealMatrix matrix = new RealMatrixImpl(data);
        try {
            new CholeskyDecompositionImpl(matrix);
            fail("Expected NotSymmetricMatrixException");
        } catch (NotSymmetricMatrixException e) {
            // Expected
        }
    }

    @Test
    public void testNotPositiveDefiniteMatrixException() throws Throwable {
        double[][] data = {
            {-1.0, 0.0, 0.0},
            {0.0, -1.0, 0.0},
            {0.0, 0.0, -1.0}
        };
        RealMatrix matrix = new RealMatrixImpl(data);
        try {
            new CholeskyDecompositionImpl(matrix);
            fail("Expected NotPositiveDefiniteMatrixException");
        } catch (NotPositiveDefiniteMatrixException e) {
            // Expected
        }
    }

    @Test
    public void testSolverDimensionMismatch() throws Throwable {
        double[][] data = {
            {4.0, 1.0},
            {1.0, 3.0}
        };
        RealMatrix matrix = new RealMatrixImpl(data);
        CholeskyDecomposition decomp = new CholeskyDecompositionImpl(matrix);
        DecompositionSolver solver = decomp.getSolver();

        try {
            solver.solve(new double[]{1.0, 2.0, 3.0});
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            solver.solve(new RealVectorImpl(new double[]{1.0, 2.0, 3.0}));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            solver.solve(new RealMatrixImpl(new double[][]{{1.0}, {2.0}, {3.0}}));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}