package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class SingularValueDecompositionImplTest {

    @Test
    public void testSquareMatrixDecomposition() throws Throwable {
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        RealMatrix matrix = new Array2DRowRealMatrix(data);
        SingularValueDecompositionImpl svd = new SingularValueDecompositionImpl(matrix);

        RealMatrix u = svd.getU();
        RealMatrix ut = svd.getUT();
        RealMatrix s = svd.getS();
        RealMatrix v = svd.getV();
        RealMatrix vt = svd.getVT();
        double[] singularValues = svd.getSingularValues();

        assertNotNull(u);
        assertNotNull(ut);
        assertNotNull(s);
        assertNotNull(v);
        assertNotNull(vt);
        assertNotNull(singularValues);
        assertEquals(2, singularValues.length);
        assertTrue(svd.getNorm() > 0.0);
        assertTrue(svd.getConditionNumber() >= 1.0);
        assertEquals(2, svd.getRank());

        DecompositionSolver solver = svd.getSolver();
        assertNotNull(solver);
        assertTrue(solver.isNonSingular());
        assertNotNull(solver.getInverse());

        double[] b = {1.0, 1.0};
        double[] solutionVector = solver.solve(b);
        assertEquals(2, solutionVector.length);

        RealVector bVector = new ArrayRealVector(b);
        RealVector solutionRealVector = solver.solve(bVector);
        assertNotNull(solutionRealVector);

        RealMatrix bMatrix = new Array2DRowRealMatrix(new double[][]{{1.0}, {1.0}});
        RealMatrix solutionMatrix = solver.solve(bMatrix);
        assertNotNull(solutionMatrix);
    }

    @Data
    @Test
    public void testTallMatrixDecomposition() throws Throwable {
        double[][] data = {
            {1.0, 0.0},
            {0.0, 2.0},
            {0.0, 0.0}
        };
        RealMatrix matrix = new Array2DRowRealMatrix(data);
        SingularValueDecompositionImpl svd = new SingularValueDecompositionImpl(matrix, 2);

        assertNotNull(svd.getU());
        assertNotNull(svd.getV());
        assertEquals(2, svd.getSingularValues().length);
    }

    @Test
    public void testWideMatrixDecomposition() throws Throwable {
        double[][] data = {
            {1.0, 0.0, 0.0},
            {0.0, 2.0, 0.0}
        };
        RealMatrix matrix = new Array2DRowRealMatrix(data);
        SingularValueDecompositionImpl svd = new SingularValueDecompositionImpl(matrix);

        assertNotNull(svd.getU());
        assertNotNull(svd.getV());
        assertEquals(2, svd.getSingularValues().length);
    }

    @Test
    public void testGetCovariance() throws Throwable {
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0},
            {5.0, 6.0}
        };
        RealMatrix matrix = new Array2DRowRealMatrix(data);
        SingularValueDecompositionImpl svd = new SingularValueDecompositionImpl(matrix);

        RealMatrix covariance = svd.getCovariance(1e-2);
        assertNotNull(covariance);
    }

    @Test
    public void testGetCovarianceThrowsException() throws Throwable {
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        RealMatrix matrix = new Array2DRowRealMatrix(data);
        SingularValueDecompositionImpl svd = new SingularValueDecompositionImpl(matrix);

        try {
            svd.getCovariance(1e10);
            fail("Expected exception due to high cutoff singular value");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cutoff singular value"));
        }
    }

    @Test
    public void testRankWithZeroSingularValues() throws Throwable {
        double[][] data = {
            {0.0, 0.0},
            {0.0, 0.0}
        };
        RealMatrix matrix = new Array2DRowRealMatrix(data);
        SingularValueDecompositionImpl svd = new SingularValueDecompositionImpl(matrix);
        assertEquals(0, svd.getRank());
        assertFalse(svd.getSolver().isNonSingular());
    }
}