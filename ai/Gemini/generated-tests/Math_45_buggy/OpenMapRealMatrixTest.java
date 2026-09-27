package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenMapRealMatrixTest {

    @Test
    public void testConstructorsAndDimensions() throws Throwable {
        OpenMapRealMatrix matrix = new OpenMapRealMatrix(3, 4);
        assertEquals(3, matrix.getRowDimension());
        assertEquals(4, matrix.getColumnDimension());

        OpenMapRealMatrix copy = new OpenMapRealMatrix(matrix);
        assertEquals(3, copy.getRowDimension());
        assertEquals(4, copy.getColumnDimension());

        OpenMapRealMatrix created = matrix.createMatrix(2, 2);
        assertEquals(2, created.getRowDimension());
        assertEquals(2, created.getColumnDimension());

        OpenMapRealMatrix cloned = matrix.copy();
        assertEquals(3, cloned.getRowDimension());
        assertEquals(4, cloned.getColumnDimension());
    }

    @Test
    public void testSetAndGetEntry() throws Throwable {
        OpenMapRealMatrix matrix = new OpenMapRealMatrix(3, 3);
        assertEquals(0.0, matrix.getEntry(0, 0), 1e-12);

        matrix.setEntry(0, 0, 5.0);
        assertEquals(5.0, matrix.getEntry(0, 0), 1e-12);

        // Setting to zero should remove it internally
        matrix.setEntry(0, 0, 0.0);
        assertEquals(0.0, matrix.getEntry(0, 0), 1e-12);
    }

    @Test
    public void testAddToEntry() throws Throwable {
        OpenMapRealMatrix matrix = new OpenMapRealMatrix(2, 2);
        matrix.addToEntry(1, 1, 3.5);
        assertEquals(3.5, matrix.getEntry(1, 1), 1e-12);

        matrix.addToEntry(1, 1, -3.5);
        assertEquals(0.0, matrix.getEntry(1, 1), 1e-12);
    }

    @Test
    public void testMultiplyEntry() throws Throwable {
        OpenMapRealMatrix matrix = new OpenMapRealMatrix(2, 2);
        matrix.setEntry(0, 0, 4.0);
        matrix.multiplyEntry(0, 0, 2.5);
        assertEquals(10.0, matrix.getEntry(0, 0), 1e-12);

        matrix.multiplyEntry(0, 0, 0.0);
        assertEquals(0.0, matrix.getEntry(0, 0), 1e-12);
    }

    @Test
    public void testAdd() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 1.0);
        m1.setEntry(1, 1, 2.0);

        OpenMapRealMatrix m2 = new OpenMapRealMatrix(2, 2);
        m2.setEntry(0, 0, 3.0);
        m2.setEntry(0, 1, 4.0);

        OpenMapRealMatrix sum = m1.add(m2);
        assertEquals(4.0, sum.getEntry(0, 0), 1e-12);
        assertEquals(4.0, sum.getEntry(0, 1), 1e-12);
        assertEquals(0.0, sum.getEntry(1, 0), 1e-12);
        assertEquals(2.0, sum.getEntry(1, 1), 1e-12);
    }

    @Test
    public void testSubtractOpenMap() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 5.0);
        m1.setEntry(1, 1, 3.0);

        OpenMapRealMatrix m2 = new OpenMapRealMatrix(2, 2);
        m2.setEntry(0, 0, 2.0);
        m2.setEntry(1, 1, 1.0);

        OpenMapRealMatrix diff = m1.subtract(m2);
        assertEquals(3.0, diff.getEntry(0, 0), 1e-12);
        assertEquals(2.0, diff.getEntry(1, 1), 1e-12);
    }

    @Test
    public void testSubtractRealMatrix() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 5.0);

        RealMatrix m2 = new Array2DRowRealMatrix(new double[][] {
            {2.0, 0.0},
            {0.0, 1.0}
        });

        RealMatrix diff = m1.subtract(m2);
        assertEquals(3.0, diff.getEntry(0, 0), 1e-12);
        assertEquals(-1.0, diff.getEntry(1, 1), 1e-12);
    }

    @Test
    public void testMultiplyOpenMap() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 2.0);
        m1.setEntry(0, 1, 3.0);

        OpenMapRealMatrix m2 = new OpenMapRealMatrix(2, 2);
        m2.setEntry(0, 0, 1.0);
        m2.setEntry(1, 0, 4.0);

        OpenMapRealMatrix prod = m1.multiply(m2);
        // Result should have non-zero becoming zero tested as well
        m1.setEntry(0, 0, 1.0);
        m1.setEntry(0, 1, -1.0);
        m2.setEntry(0, 0, 1.0);
        m2.setEntry(1, 0, 1.0);
        OpenMapRealMatrix prod2 = m1.multiply(m2);
        assertEquals(0.0, prod2.getEntry(0, 0), 1e-12);
    }

    @Test
    public void testMultiplyRealMatrixFallback() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 2.0);

        RealMatrix m2 = new Array2DRowRealMatrix(new double[][] {
            {3.0, 4.0},
            {5.0, 6.0}
        });

        RealMatrix prod = m1.multiply(m2);
        assertEquals(6.0, prod.getEntry(0, 0), 1e-12);
        assertEquals(8.0, prod.getEntry(0, 1), 1e-12);
    }
}