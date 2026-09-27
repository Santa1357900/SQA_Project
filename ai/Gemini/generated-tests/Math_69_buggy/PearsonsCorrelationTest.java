package org.apache.commons.math.stat.correlation;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.MathException;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.BlockRealMatrix;

public class PearsonsCorrelationTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        PearsonsCorrelation pc = new PearsonsCorrelation();
        assertNull(pc.getCorrelationMatrix());
    }

    @Test
    public void testDoubleArrayConstructorValid() throws Throwable {
        double[][] data = {
            {1.0, 2.0},
            {2.0, 4.0},
            {3.0, 6.0}
        };
        PearsonsCorrelation pc = new PearsonsCorrelation(data);
        RealMatrix matrix = pc.getCorrelationMatrix();
        assertNotNull(matrix);
        assertEquals(2, matrix.getRowDimension());
        assertEquals(2, matrix.getColumnDimension());
        assertEquals(1.0, matrix.getEntry(0, 0), 1e-12);
    }

    @Test
    public void testDoubleArrayConstructorInvalidDimensions() throws Throwable {
        double[][] data = {
            {1.0, 2.0}
        };
        try {
            new PearsonsCorrelation(data);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void testRealMatrixConstructorValid() throws Throwable {
        RealMatrix rm = new BlockRealMatrix(new double[][] {
            {1.0, 2.0},
            {2.0, 4.0},
            {3.0, 6.0}
        });
        PearsonsCorrelation pc = new PearsonsCorrelation(rm);
        assertNotNull(pc.getCorrelationMatrix());
    }

    @Test
    public void testRealMatrixConstructorInsufficientData() throws Throwable {
        RealMatrix rm = new BlockRealMatrix(new double[][] {
            {1.0, 2.0}
        });
        try {
            new PearsonsCorrelation(rm);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void testCovarianceConstructorValid() throws Throwable {
        double[][] data = {
            {1.0, 2.0},
            {2.0, 4.0},
            {3.0, 6.0}
        };
        Covariance cov = new Covariance(data);
        PearsonsCorrelation pc = new PearsonsCorrelation(cov);
        assertNotNull(pc.getCorrelationMatrix());
    }

    @Test
    public void testCovarianceConstructorNullMatrix() throws Throwable {
        Covariance cov = new Covariance();
        try {
            new PearsonsCorrelation(cov);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void testRealMatrixAndIntConstructor() throws Throwable {
        RealMatrix rm = new BlockRealMatrix(new double[][] {
            {1.0, 0.5},
            {0.5, 1.0}
        });
        PearsonsCorrelation pc = new PearsonsCorrelation(rm, 5);
        assertNotNull(pc.getCorrelationMatrix());
        RealMatrix se = pc.getCorrelationStandardErrors();
        assertNotNull(se);
    }

    @Test
    public void testGetCorrelationStandardErrors() throws Throwable {
        double[][] data = {
            {1.0, 2.0, 3.0},
            {2.0, 4.0, 6.0},
            {3.0, 5.0, 9.0}
        };
        PearsonsCorrelation pc = new PearsonsCorrelation(data);
        RealMatrix se = pc.getCorrelationStandardErrors();
        assertNotNull(se);
        assertEquals(3, se.getColumnDimension());
    }

    @Test
    public void testGetCorrelationPValues() throws Throwable, MathException {
        double[][] data = {
            {1.0, 2.0, 3.0},
            {2.0, 4.0, 5.0},
            {3.0, 7.0, 9.0}
        };
        PearsonsCorrelation pc = new PearsonsCorrelation(data);
        RealMatrix pValues = pc.getCorrelationPValues();
        assertNotNull(pValues);
        assertEquals(3, pValues.getRowDimension());
        assertEquals(0.0, pValues.getEntry(0, 0), 1e-12);
    }

    @Test
    public void testComputeCorrelationMatrixDataArray() throws Throwable {
        double[][] data = {
            {1.0, 2.0},
            {2.0, 3.0},
            {3.0, 4.0}
        };
        PearsonsCorrelation pc = new PearsonsCorrelation();
        RealMatrix mat = pc.computeCorrelationMatrix(data);
        assertNotNull(mat);
        assertEquals(2, mat.getColumnDimension());
    }

    @Test
    public void testCorrelationValidArrays() throws Throwable {
        PearsonsCorrelation pc = new PearsonsCorrelation();
        double[] x = {1.0, 2.0, 3.0, 4.0};
        double[] y = {2.0, 4.0, 6.0, 8.0};
        double corr = pc.correlation(x, y);
        assertEquals(1.0, corr, 1e-12);
    }

    @Test
    public void testCorrelationInvalidLengthMismatch() throws Throwable {
        PearsonsCorrelation pc = new PearsonsCorrelation();
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {1.0, 2.0};
        try {
            pc.correlation(x, y);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void testCorrelationInsufficientSize() throws Throwable {
        PearsonsCorrelation pc = new PearsonsCorrelation();
        double[] x = {1.0};
        double[] y = {2.0};
        try {
            pc.correlation(x, y);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void testCovarianceToCorrelation() throws Throwable {
        RealMatrix covMat = new BlockRealMatrix(new double[][] {
            {4.0, 2.0},
            {2.0, 9.0}
        });
        PearsonsCorrelation pc = new PearsonsCorrelation();
        RealMatrix corrMat = pc.covarianceToCorrelation(covMat);
        assertNotNull(corrMat);
        assertEquals(1.0, corrMat.getEntry(0, 0), 1e-12);
        assertEquals(2.0 / (Math.sqrt(4.0) * Math.sqrt(9.0)), corrMat.getEntry(0, 1), 1e-12);
    }
}