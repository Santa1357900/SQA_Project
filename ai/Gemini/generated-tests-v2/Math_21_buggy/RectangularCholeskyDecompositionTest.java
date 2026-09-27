package org.apache.commons.math3.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class RectangularCholeskyDecompositionTest {

    @Test(expected = NonPositiveDefiniteMatrixException.class)
    public void testNonPositiveDefiniteFirstElement() throws Throwable {
        double[][] data = new double[][] {
            {-1.0, 0.0},
            {0.0, -1.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        new RectangularCholeskyDecomposition(matrix, 1e-10);
    }

    @Test(expected = NonPositiveDefiniteMatrixException.class)
    public void testNegativeDiagonalRemaining() throws Throwable {
        double[][] data = new double[][] {
            {4.0, 0.0},
            {0.0, -0.5}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        new RectangularCholeskyDecomposition(matrix, 1e-5);
    }

    @Test
    public void testIdentityMatrix() throws Throwable {
        double[][] data = new double[][] {
            {1.0, 0.0},
            {0.0, 1.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        RectangularCholeskyDecomposition decomposition = new RectangularCholeskyDecomposition(matrix, 1e-10);

        RealMatrix root = decomposition.getRootMatrix();
        assertEquals(2, decomposition.getRank());
        assertEquals(2, root.getRowDimension());
        assertEquals(2, root.getColumnDimension());
    }

    @Test
    public void testPositiveSemiDefiniteMatrixWithRankDeficiency() throws Throwable {
        double[][] data = new double[][] {
            {1.0, 2.0, 1.0},
            {2.0, 4.0, 2.0},
            {1.0, 2.0, 1.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        RectangularCholeskyDecomposition decomposition = new RectangularCholeskyDecomposition(matrix, 1e-10);

        assertTrue(decomposition.getRank() < 3);
        assertNotNull(decomposition.getRootMatrix());
    }

    @Test
    public void testSwapLogicAndOrdering() throws Throwable {
        double[][] data = new double[][] {
            {1.0, 0.0},
            {0.0, 4.0}
        };
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        RectangularCholeskyDecomposition decomposition = new RectangularCholeskyDecomposition(matrix, 1e-10);

        assertEquals(2, decomposition.getRank());
        RealMatrix root = decomposition.getRootMatrix();
        assertNotNull(root);
    }
}