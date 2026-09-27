package org.apache.commons.math.linear;

import junit.framework.TestCase;
import java.math.BigDecimal;

public class BigMatrixImplTest extends TestCase {

    public void testConstructorsAndDimensions() throws Throwable {
        BigMatrixImpl m1 = new BigMatrixImpl();
        assertNull(m1.data);

        BigMatrixImpl m2 = new BigMatrixImpl(2, 3);
        assertEquals(2, m2.getRowDimension());
        assertEquals(3, m2.getColumnDimension());

        try {
            new BigMatrixImpl(0, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            new BigMatrixImpl(2, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        BigDecimal[][] d = {
            {new BigDecimal("1"), new BigDecimal("2")},
            {new BigDecimal("3"), new BigDecimal("4")}
        };
        BigMatrixImpl m3 = new BigMatrixImpl(d);
        assertEquals(2, m3.getRowDimension());
        assertEquals(2, m3.getColumnDimension());

        BigMatrixImpl m4 = new BigMatrixImpl(d, false);
        assertEquals(d, m4.getDataRef());

        BigMatrixImpl m5 = new BigMatrixImpl(d, true);
        assertNotSame(d, m5.getDataRef());

        try {
            new BigMatrixImpl((BigDecimal[][]) null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }

        try {
            new BigMatrixImpl(new BigDecimal[0][0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigDecimal[][] nonRect = {
                {new BigDecimal("1")},
                {new BigDecimal("2"), new BigDecimal("3")}
            };
            new BigMatrixImpl(nonRect);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigDecimal[][] emptyCols = { {} };
            new BigMatrixImpl(emptyCols);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // double[][] constructor
        double[][] dblData = {{1.0, 2.0}, {3.0, 4.0}};
        BigMatrixImpl m6 = new BigMatrixImpl(dblData);
        assertEquals(2, m6.getRowDimension());

        try {
            new BigMatrixImpl(new double[0][0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            double[][] emptyColDbl = { {} };
            new BigMatrixImpl(emptyColDbl);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            double[][] nonRectDbl = {{1.0}, {2.0, 3.0}};
            new BigMatrixImpl(nonRectDbl);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // String[][] constructor
        String[][] strData = {{"1", "2"}, {"3", "4"}};
        BigMatrixImpl m7 = new BigMatrixImpl(strData);
        assertEquals(2, m7.getRowDimension());

        try {
            new BigMatrixImpl(new String[0][0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            String[][] emptyColStr = { {} };
            new BigMatrixImpl(emptyColStr);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            String[][] nonRectStr = {{"1"}, {"2", "3"}};
            new BigMatrixImpl(nonRectStr);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // BigDecimal[] column vector constructor
        BigDecimal[] v = {new BigDecimal("1"), new BigDecimal("2")};
        BigMatrixImpl m8 = new BigMatrixImpl(v);
        assertEquals(2, m8.getRowDimension());
        assertEquals(1, m8.getColumnDimension());
    }

    public void testCopyAndClone() throws Throwable {
        BigDecimal[][] d = {{new BigDecimal("1")}};
        BigMatrixImpl m = new BigMatrixImpl(d);
        BigMatrix copy = m.copy();
        assertEquals(m, copy);
    }

    public void testAddAndSubtract() throws Throwable {
        BigDecimal[][] d1 = {{new BigDecimal("1"), new BigDecimal("2")}};
        BigDecimal[][] d2 = {{new BigDecimal("3"), new BigDecimal("4")}};
        BigMatrixImpl m1 = new BigMatrixImpl(d1);
        BigMatrixImpl m2 = new BigMatrixImpl(d2);

        BigMatrix sum = m1.add(m2);
        assertEquals(new BigDecimal("4"), sum.getEntry(0, 0));
        assertEquals(new BigDecimal("6"), sum.getEntry(0, 1));

        // Add non-BigMatrixImpl BigMatrix to trigger ClassCastException branch
        BigMatrix dummyMatrix = new BigMatrix() {
            public int getRowDimension() { return 1; }
            public int getColumnDimension() { return 2; }
            public BigMatrix add(BigMatrix m) { return null; }
            public BigMatrix subtract(BigMatrix m) { return null; }
            public BigMatrix scalarAdd(BigDecimal d) { return null; }
            public BigMatrix scalarMultiply(BigDecimal d) { return null; }
            public BigMatrix multiply(BigMatrix m) { return null; }
            public BigMatrix preMultiply(BigMatrix m) { return null; }
            public BigDecimal[][] getData() { return null; }
            public double[][] getDataAsDoubleArray() { return null; }
            public BigDecimal[][] getDataRef() { return null; }
            public BigDecimal getNorm() { return null; }
            public BigMatrix getSubMatrix(int sRow, int eRow, int sCol, int eCol) { return null; }
            public BigMatrix getSubMatrix(int[] r, int[] c) { return null; }
            public void setSubMatrix(BigDecimal[][] sub, int r, int c) {}
            public BigMatrix getRowMatrix(int r) { return null; }
            public BigMatrix getColumnMatrix(int c) { return null; }
            public BigDecimal[] getRow(int r) { return null; }
            public double[] getRowAsDoubleArray(int r) { return null; }
            public BigDecimal[] getColumn(int c) { return null; }
            public double[] getColumnAsDoubleArray(int c) { return null; }
            public BigDecimal getEntry(int r, int c) { return new BigDecimal("3"); }
            public double getEntryAsDouble(int r, int c) { return 0; }
            public BigMatrix transpose() { return null; }
            public BigMatrix inverse() { return null; }
            public BigDecimal getDeterminant() { return null; }
            public boolean isSquare() { return true; }
            public boolean isSingular() { return false; }
            public BigDecimal getTrace() { return null; }
            public BigDecimal[] operate(BigDecimal[] v) { return null; }
            public BigDecimal[] operate(double[] v) { return null; }
            public BigDecimal[] preMultiply(BigDecimal[] v) { return null; }
            public BigDecimal[] solve(BigDecimal[] b) { return null; }
            public BigDecimal[] solve(double[] b) { return null; }
            public BigMatrix solve(BigMatrix b) { return null; }
            public void luDecompose() {}
            public String toString() { return ""; }
            public boolean equals(Object o) { return false; }
            public int hashCode() { return 0; }
        };

        BigMatrix sumDummy = m1.add(dummyMatrix);
        assertEquals(new BigDecimal("4"), sumDummy.getEntry(0, 0));

        try {
            BigMatrixImpl mBad = new BigMatrixImpl(2, 2);
            m1.add(mBad);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigMatrixImpl mBad = new BigMatrixImpl(2, 2);
            m1.add((BigMatrixImpl) mBad);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        BigMatrix diff = m2.subtract(m1);
        assertEquals(new BigDecimal("2"), diff.getEntry(0, 0));

        BigMatrix diffDummy = m2.subtract(dummyMatrix);
        assertEquals(new BigDecimal("0"), diffDummy.getEntry(0, 0));

        try {
            BigMatrixImpl mBad = new BigMatrixImpl(2, 2);
            m2.subtract(mBad);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigMatrixImpl mBad = new BigMatrixImpl(2, 2);
            m2.subtract((BigMatrixImpl) mBad);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testScalarOperations() throws Throwable {
        BigDecimal[][] d = {{new BigDecimal("1"), new BigDecimal("2")}};
        BigMatrixImpl m = new BigMatrixImpl(d);

        BigMatrix sAdd = m.scalarAdd(new BigDecimal("10"));
        assertEquals(new BigDecimal("11"), sAdd.getEntry(0, 0));

        BigMatrix sMul = m.scalarMultiply(new BigDecimal("3"));
        assertEquals(new BigDecimal("6"), sMul.getEntry(0, 1));
    }

    public void testMultiply() throws Throwable {
        BigDecimal[][] d1 = {
            {new BigDecimal("1"), new BigDecimal("2")},
            {new BigDecimal("3"), new BigDecimal("4")}
        };
        BigDecimal[][] d2 = {
            {new BigDecimal("5"), new BigDecimal("6")},
            {new BigDecimal("7"), new BigDecimal("8")}
        };
        BigMatrixImpl m1 = new BigMatrixImpl(d1);
        BigMatrixImpl m2 = new BigMatrixImpl(d2);

        BigMatrix prod = m1.multiply(m2);
        assertEquals(new BigDecimal("19"), prod.getEntry(0, 0));

        BigMatrix dummyMatrix = new BigMatrix() {
            public int getRowDimension() { return 2; }
            public int getColumnDimension() { return 2; }
            public BigMatrix add(BigMatrix m) { return null; }
            public BigMatrix subtract(BigMatrix m) { return null; }
            public BigMatrix scalarAdd(BigDecimal d) { return null; }
            public BigMatrix scalarMultiply(BigDecimal d) { return null; }
            public BigMatrix multiply(BigMatrix m) { return null; }
            public BigMatrix preMultiply(BigMatrix m) { return null; }
            public BigDecimal[][] getData() { return null; }
            public double[][] getDataAsDoubleArray() { return null; }
            public BigDecimal[][] getDataRef() { return null; }
            public BigDecimal getNorm() { return null; }
            public BigMatrix getSubMatrix(int sRow, int eRow, int sCol, int eCol) { return null; }
            public BigMatrix getSubMatrix(int[] r, int[] c) { return null; }
            public void setSubMatrix(BigDecimal[][] sub, int r, int c) {}
            public BigMatrix getRowMatrix(int r) { return null; }
            public BigMatrix getColumnMatrix(int c) { return null; }
            public BigDecimal[] getRow(int r) { return null; }
            public double[] getRowAsDoubleArray(int r) { return null; }
            public BigDecimal[] getColumn(int c) { return null; }
            public double[] getColumnAsDoubleArray(int c) { return null; }
            public BigDecimal getEntry(int r, int c) { return new BigDecimal("1"); }
            public double getEntryAsDouble(int r, int c) { return 0; }
            public BigMatrix transpose() { return null; }
            public BigMatrix inverse() { return null; }
            public BigDecimal getDeterminant() { return null; }
            public boolean isSquare() { return true; }
            public boolean isSingular() { return false; }
            public BigDecimal getTrace() { return null; }
            public BigDecimal[] operate(BigDecimal[] v) { return null; }
            public BigDecimal[] operate(double[] v) { return null; }
            public BigDecimal[] preMultiply(BigDecimal[] v) { return null; }
            public BigDecimal[] solve(BigDecimal[] b) { return null; }
            public BigDecimal[] solve(double[] b) { return null; }
            public BigMatrix solve(BigMatrix b) { return null; }
            public void luDecompose() {}
            public String toString() { return ""; }
            public boolean equals(Object o) { return false; }
            public int hashCode() { return 0; }
        };

        BigMatrix prodDummy = m1.multiply(dummyMatrix);
        assertEquals(2, prodDummy.getRowDimension());

        try {
            BigMatrixImpl mBad = new BigMatrixImpl(3, 3);
            m1.multiply(mBad);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigMatrixImpl mBad = new BigMatrixImpl(3, 3);
            m1.multiply((BigMatrixImpl) mBad);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        BigMatrix pre = m1.preMultiply(m2);
        assertEquals(2, pre.getRowDimension());
    }

    public void testDataGettersAndSettings() throws Throwable {
        BigDecimal[][] d = {{newBigDecimal("1.5"), new BigDecimal("2.5")}};
        BigMatrixImpl m = new BigMatrixImpl(d);

        BigDecimal[][] dataCopy = m.getData();
        assertEquals(1, dataCopy.length);

        double[][] dblArray = m.getDataAsDoubleArray();
        assertEquals(1.5, dblArray[0][0], 1e-12);

        BigDecimal[][] dataRef = m.getDataRef();
        assertSame(d[0][0], dataRef[0][0]);

        m.setRoundingMode(BigDecimal.ROUND_DOWN);
        assertEquals(BigDecimal.ROUND_DOWN, m.getRoundingMode());

        m.setScale(32);
        assertEquals(32, m.getScale());

        BigDecimal norm = m.getNorm();
        assertTrue(norm.doubleValue() > 0);
    }

    public void testSubMatrices() throws Throwable {
        BigDecimal[][] d = {
            {new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3")},
            {new BigDecimal("4"), new BigDecimal("5"), new BigDecimal("6")}
        };
        BigMatrixImpl m = new BigMatrixImpl(d);

        BigMatrix sub = m.getSubMatrix(0, 1, 1, 2);
        assertEquals(2, sub.getRowDimension());
        assertEquals(2, sub.getColumnDimension());
        assertEquals(new BigDecimal("2"), sub.getEntry(0, 0));

        try {
            m.getSubMatrix(-1, 1, 0, 1);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        int[] rows = {0, 1};
        int[] cols = {0, 2};
        BigMatrix subIndices = m.getSubMatrix(rows, cols);
        assertEquals(2, subIndices.getRowDimension());
        assertEquals(2, subIndices.getColumnDimension());
        assertEquals(new BigDecimal("3"), subIndices.getEntry(0, 1));

        try {
            m.getSubMatrix(new int[0], cols);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        try {
            int[] badRows = {0, 10};
            m.getSubMatrix(badRows, cols);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        BigDecimal[][] replacement = {{new BigDecimal("9"), new BigDecimal("9")}};
        m.setSubMatrix(replacement, 0, 1);
        assertEquals(new BigDecimal("9"), m.getEntry(0, 1));

        try {
            m.setSubMatrix(replacement, -1, 0);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        try {
            m.setSubMatrix(new BigDecimal[0][0], 0, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigDecimal[][] emptyColSub = { {} };
            m.setSubMatrix(emptyColSub, 0, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigDecimal[][] nonRectSub = {{new BigDecimal("1")}, {new BigDecimal("2"), new BigDecimal("3")}};
            m.setSubMatrix(nonRectSub, 0, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        BigMatrixImpl uninit = new BigMatrixImpl();
        try {
            uninit.setSubMatrix(replacement, 1, 1);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }
        uninit.setSubMatrix(replacement, 0, 0);
        assertEquals(2, uninit.getColumnDimension());

        try {
            m.setSubMatrix(replacement, 1, 5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }
    }

    public void testRowAndColumnExtractions() throws Throwable {
        BigDecimal[][] d = {
            {new BigDecimal("1"), new BigDecimal("2")},
            {new BigDecimal("3"), new BigDecimal("4")}
        };
        BigMatrixImpl m = new BigMatrixImpl(d);

        BigMatrix rowMat = m.getRowMatrix(0);
        assertEquals(1, rowMat.getRowDimension());
        assertEquals(2, rowMat.getColumnDimension());

        try {
            m.getRowMatrix(5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        BigMatrix colMat = m.getColumnMatrix(1);
        assertEquals(2, colMat.getRowDimension());
        assertEquals(1, colMat.getColumnDimension());

        try {
            m.getColumnMatrix(5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        BigDecimal[] rowArr = m.getRow(0);
        assertEquals(2, rowArr.length);

        try {
            m.getRow(5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        double[] rowDbl = m.getRowAsDoubleArray(0);
        assertEquals(2.0, rowDbl[1], 1e-12);

        try {
            m.getRowAsDoubleArray(5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        BigDecimal[] colArr = m.getColumn(0);
        assertEquals(2, colArr.length);

        try {
            m.getColumn(5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        double[] colDbl = m.getColumnAsDoubleArray(0);
        assertEquals(3.0, colDbl[1], 1e-12);

        try {
            m.getColumnAsDoubleArray(5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }
    }

    public void testEntriesAndAccessors() throws Throwable {
        BigDecimal[][] d = {{new BigDecimal("10")}};
        BigMatrixImpl m = new BigMatrixImpl(d);

        BigDecimal entry = m.getEntry(0, 0);
        assertEquals(new BigDecimal("10"), entry);

        try {
            m.getEntry(5, 5);
            fail("Expected MatrixIndexException");
        } catch (MatrixIndexException e) {
            // expected
        }

        double entryDbl = m.getEntryAsDouble(0, 0);
        assertEquals(10.0, entryDbl, 1e-12);

        BigMatrix trans = m.transpose();
        assertEquals(1, trans.getRowDimension());

        assertTrue(m.isSquare());
        assertFalse(new BigMatrixImpl(2, 3).isSquare());
    }

    public void testLinearSystemAndDeterminant() throws Throwable {
        BigDecimal[][] d = {
            {new BigDecimal("2"), new BigDecimal("1")},
            {new BigDecimal("1"), new BigDecimal("3")}
        };
        BigMatrixImpl m = new BigMatrixImpl(d);

        assertFalse(m.isSingular());
        BigDecimal det = m.getDeterminant();
        assertTrue(det.doubleValue() != 0);

        BigMatrix inv = m.inverse();
        assertEquals(2, inv.getRowDimension());

        BigDecimal[] b = {new BigDecimal("3"), new BigDecimal("4")};
        BigDecimal[] solution = m.solve(b);
        assertEquals(2, solution.length);

        double[] bDbl = {3.0, 4.0};
        BigDecimal[] solutionDbl = m.solve(bDbl);
        assertEquals(2, solutionDbl.length);

        try {
            m.solve(new BigDecimal[3]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        BigDecimal[][] bMatData = {{new BigDecimal("3")}, {new BigDecimal("4")}};
        BigMatrix bMat = new BigMatrixImpl(bMatData);
        BigMatrix solvedMat = m.solve(bMat);
        assertEquals(2, solvedMat.getRowDimension());

        try {
            BigMatrixImpl badRowB = new BigMatrixImpl(new BigDecimal[3][1]);
            m.solve(badRowB);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BigMatrixImpl nonSquare = new BigMatrixImpl(2, 3);
            nonSquare.solve(new BigMatrixImpl(2, 1));
            fail("Expected InvalidMatrixException");
        } catch (InvalidMatrixException e) {
            // expected
        }

        try {
            BigDecimal[][] singularData = {
                {new BigDecimal("1"), new BigDecimal("2")},
                {new BigDecimal("2"), new BigDecimal("4")}
            };
            BigMatrixImpl singular = new BigMatrixImpl(singularData);
            singular.solve(new BigDecimal[2]);
            fail("Expected InvalidMatrixException");
        } catch (InvalidMatrixException e) {
            // expected
        }

        try {
            BigDecimal[][] singularData = {
                {new BigDecimal("1"), new BigDecimal("2")},
                {new BigDecimal("2"), new BigDecimal("4")}
            };
            BigMatrixImpl singular = new BigMatrixImpl(singularData);
            singular.getDeterminant();
            fail("Expected InvalidMatrixException");
        } catch (InvalidMatrixException e) {
            // expected
        }

        try {
            BigMatrixImpl nonSquare = new BigMatrixImpl(2, 3);
            nonSquare.getDeterminant();
            fail("Expected InvalidMatrixException");
        } catch (InvalidMatrixException e) {
            // expected
        }

        try {
            BigMatrixImpl nonSquare = new BigMatrixImpl(2, 3);
            nonSquare.luDecompose();
            fail("Expected InvalidMatrixException");
        } catch (InvalidMatrixException e) {
            // expected
        }
    }

    public void testTraceAndOperate() throws Throwable {
        BigDecimal[][] d = {
            {new BigDecimal("1"), new BigDecimal("2")},
            {new BigDecimal("3"), new BigDecimal("4")}
        };
        BigMatrixImpl m = new BigMatrixImpl(d);

        BigDecimal trace = m.getTrace();
        assertEquals(new BigDecimal("5"), trace);

        try {
            new BigMatrixImpl(2, 3).getTrace();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        BigDecimal[] v = {new BigDecimal("1"), new BigDecimal("1")};
        BigDecimal[] op = m.operate(v);
        assertEquals(2, op.length);

        try {
            m.operate(new BigDecimal[3]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        double[] vDbl = {1.0, 1.0};
        BigDecimal[] opDbl = m.operate(vDbl);
        assertEquals(2, opDbl.length);

        BigDecimal[] preOp = m.preMultiply(v);
        assertEquals(2, preOp.length);

        try {
            m.preMultiply(new BigDecimal[3]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testToStringEqualsAndHashCode() throws Throwable {
        BigDecimal[][] d1 = {{new BigDecimal("1")}};
        BigDecimal[][] d2 = {{new BigDecimal("1")}};
        BigDecimal[][] d3 = {{new BigDecimal("2")}};

        BigMatrixImpl m1 = new BigMatrixImpl(d1);
        BigMatrixImpl m2 = new BigMatrixImpl(d2);
        BigMatrixImpl m3 = new BigMatrixImpl(d3);

        assertTrue(m1.equals(m1));
        assertTrue(m1.equals(m2));
        assertFalse(m1.equals(null));
        assertFalse(m1.equals("NotAMatrix"));
        assertFalse(m1.equals(new BigMatrixImpl(2, 2)));
        assertFalse(m1.equals(m3));

        BigMatrixImpl mDiffSizeCol = new BigMatrixImpl(new BigDecimal[1][2]);
        assertFalse(m1.equals(mDiffSizeCol));

        assertNotNull(m1.toString());
        assertTrue(m1.hashCode() != 0);
    }

    public void testProtectedAndHelperMethods() throws Throwable {
        BigDecimal[][] d = {
            {new BigDecimal("2"), new BigDecimal("1")},
            {new BigDecimal("1"), new BigDecimal("3")}
        };
        BigMatrixImpl m = new BigMatrixImpl(d);

        BigMatrix luMat = m.getLUMatrix();
        assertNotNull(luMat);

        int[] perm = m.getPermutation();
        assertNotNull(perm);
    }

    private BigDecimal newBigDecimal(String val) {
        return new BigDecimal(val);
    }
}