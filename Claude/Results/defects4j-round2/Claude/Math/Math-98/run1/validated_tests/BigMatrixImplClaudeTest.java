package org.apache.commons.math.linear;

import java.math.BigDecimal;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class BigMatrixImplClaudeTest {

    private BigMatrixImpl a;
    private BigMatrixImpl b;
    private BigMatrixImpl rect;
    private BigMatrixImpl singular;
    private BigMatrixImpl diag;

    @Before
    public void setUp() throws Throwable {
        a = new BigMatrixImpl(bd(new int[][] { {1,2}, {3,4} }));
        b = new BigMatrixImpl(bd(new int[][] { {5,6}, {7,8} }));
        rect = new BigMatrixImpl(bd(new int[][] { {1,2,3}, {4,5,6} }));
        singular = new BigMatrixImpl(bd(new int[][] { {1,2}, {2,4} }));
        diag = new BigMatrixImpl(bd(new int[][] { {2,0}, {0,2} }));
    }

    private BigDecimal[][] bd(int[][] vals) {
        BigDecimal[][] r = new BigDecimal[vals.length][vals[0].length];
        for (int i = 0; i < vals.length; i++) {
            for (int j = 0; j < vals[0].length; j++) {
                r[i][j] = new BigDecimal(vals[i][j]);
            }
        }
        return r;
    }

    private BigDecimal[] bdVec(int[] vals) {
        BigDecimal[] r = new BigDecimal[vals.length];
        for (int i = 0; i < vals.length; i++) {
            r[i] = new BigDecimal(vals[i]);
        }
        return r;
    }

    // covers: rowDimension<=0 branch and columnDimension<=0 branch of BigMatrixImpl(int,int)
    @Test
    public void testConstructorIntInt_invalidDimensions_throwsIllegalArgumentException() throws Throwable {
        try {
            new BigMatrixImpl(0, 2);
            fail("expected IllegalArgumentException for non-positive row dimension");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new BigMatrixImpl(2, -1);
            fail("expected IllegalArgumentException for non-positive column dimension");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: normal path of BigMatrixImpl(int,int) setting correct dimensions
    @Test
    public void testConstructorIntInt_validDimensions_setsRowAndColumnDimension() throws Throwable {
        BigMatrixImpl m = new BigMatrixImpl(3, 5);
        assertEquals(3, m.getRowDimension());
        assertEquals(5, m.getColumnDimension());
    }

    // covers: ragged-row branch, empty-array branch and null branch of BigMatrixImpl(BigDecimal[][])
    @Test
    public void testConstructorBigDecimalArray_invalidInput_throwsExpectedExceptions() throws Throwable {
        try {
            new BigMatrixImpl(new BigDecimal[][] { {new BigDecimal(1)}, {new BigDecimal(1), new BigDecimal(2)} });
            fail("expected IllegalArgumentException for ragged rows");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new BigMatrixImpl(new BigDecimal[0][0]);
            fail("expected IllegalArgumentException for empty array");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new BigMatrixImpl((BigDecimal[][]) null);
            fail("expected NullPointerException for null array");
        } catch (NullPointerException expected) {
        }
    }



    // covers: ragged-row branch of BigMatrixImpl(double[][])
    @Test
    public void testConstructorDoubleArray_raggedRows_throwsIllegalArgumentException() throws Throwable {
        double[][] ragged = { {1.0}, {1.0, 2.0} };
        try {
            new BigMatrixImpl(ragged);
            fail("expected IllegalArgumentException for ragged rows");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: normal path of BigMatrixImpl(String[][])
    @Test
    public void testConstructorStringArray_valid_createsCorrectEntries() throws Throwable {
        String[][] s = { {"1", "2"}, {"3", "4"} };
        BigMatrixImpl m = new BigMatrixImpl(s);
        assertEquals(0, m.getEntry(0, 0).compareTo(new BigDecimal(1)));
        assertEquals(0, m.getEntry(1, 1).compareTo(new BigDecimal(4)));
    }

    // covers: ragged-row branch of BigMatrixImpl(String[][])
    @Test
    public void testConstructorStringArray_raggedRows_throwsIllegalArgumentException() throws Throwable {
        String[][] ragged = { {"1"}, {"1", "2"} };
        try {
            new BigMatrixImpl(ragged);
            fail("expected IllegalArgumentException for ragged rows");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: BigMatrixImpl(BigDecimal[]) column-vector construction
    @Test
    public void testConstructorBigDecimalVector_createsSingleColumnMatrix() throws Throwable {
        BigDecimal[] v = { new BigDecimal(1), new BigDecimal(2), new BigDecimal(3) };
        BigMatrixImpl m = new BigMatrixImpl(v);
        assertEquals(3, m.getRowDimension());
        assertEquals(1, m.getColumnDimension());
        assertEquals(0, m.getEntry(1, 0).compareTo(new BigDecimal(2)));
    }

    // covers: copy() returning an equal but independent matrix
    @Test
    public void testCopy_returnsEqualButIndependentMatrix() throws Throwable {
        BigMatrix copyM = a.copy();
        assertTrue(a.equals(copyM));
        a.getDataRef()[0][0] = new BigDecimal(999);
        assertEquals(0, ((BigMatrixImpl) copyM).getEntry(0, 0).compareTo(new BigDecimal(1)));
    }

    // covers: add(BigMatrixImpl) normal path and the dimension-mismatch throw
    @Test
    public void testAddBigMatrixImpl_validAndMismatchedDimensions() throws Throwable {
        BigMatrixImpl sum = a.add(b);
        assertEquals(0, sum.getEntry(0, 0).compareTo(new BigDecimal(6)));
        assertEquals(0, sum.getEntry(1, 1).compareTo(new BigDecimal(12)));
        try {
            a.add(rect);
            fail("expected IllegalArgumentException for dimension mismatch");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: public add(BigMatrix) try-path when argument is a BigMatrixImpl
    @Test
    public void testAddBigMatrix_publicMethodWithBigMatrixImplArgument_returnsSum() throws Throwable {
        BigMatrix sum = a.add((BigMatrix) b);
        assertEquals(0, sum.getEntry(0, 1).compareTo(new BigDecimal(8)));
    }

    // covers: subtract(BigMatrixImpl) normal path and the dimension-mismatch throw
    @Test
    public void testSubtractBigMatrixImpl_validAndMismatchedDimensions() throws Throwable {
        BigMatrixImpl diff = a.subtract(b);
        assertEquals(0, diff.getEntry(0, 0).compareTo(new BigDecimal(-4)));
        try {
            a.subtract(rect);
            fail("expected IllegalArgumentException for dimension mismatch");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: scalarAdd and scalarMultiply applying the operation to every entry
    @Test
    public void testScalarAddAndScalarMultiply_applyToEachEntry() throws Throwable {
        BigMatrix added = a.scalarAdd(new BigDecimal(10));
        assertEquals(0, added.getEntry(0, 0).compareTo(new BigDecimal(11)));
        BigMatrix mult = a.scalarMultiply(new BigDecimal(2));
        assertEquals(0, mult.getEntry(1, 1).compareTo(new BigDecimal(8)));
    }

    // covers: multiply(BigMatrixImpl) normal path and the dimension-mismatch throw
    @Test
    public void testMultiplyBigMatrixImpl_validAndMismatchedDimensions() throws Throwable {
        BigMatrixImpl prod = a.multiply(b);
        assertEquals(0, prod.getEntry(0, 0).compareTo(new BigDecimal(19)));
        assertEquals(0, prod.getEntry(1, 1).compareTo(new BigDecimal(50)));
        try {
            rect.multiply(a);
            fail("expected IllegalArgumentException for dimension mismatch");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: preMultiply(BigMatrix) delegating to m.multiply(this)
    @Test
    public void testPreMultiply_returnsCorrectProduct() throws Throwable {
        BigMatrix result = a.preMultiply((BigMatrix) b);
        assertEquals(0, result.getEntry(0, 0).compareTo(new BigDecimal(23)));
        assertEquals(0, result.getEntry(1, 1).compareTo(new BigDecimal(46)));
    }

    // covers: getData() returning a fresh copy (mutation does not affect source)
    @Test
    public void testGetData_returnsFreshCopy() throws Throwable {
        BigDecimal[][] copyData = a.getData();
        copyData[0][0] = new BigDecimal(999);
        assertEquals(0, a.getEntry(0, 0).compareTo(new BigDecimal(1)));
    }

    // covers: getDataAsDoubleArray converting entries to double
    @Test
    public void testGetDataAsDoubleArray_returnsDoubleValues() throws Throwable {
        double[][] d = a.getDataAsDoubleArray();
        assertEquals(1.0, d[0][0], 1e-9);
        assertEquals(4.0, d[1][1], 1e-9);
    }

    // covers: getDataRef() returning a live reference (mutation affects matrix)
    @Test
    public void testGetDataRef_modifyingReturnedArrayChangesMatrixState() throws Throwable {
        BigDecimal[][] ref = a.getDataRef();
        ref[0][0] = new BigDecimal(123);
        assertEquals(0, a.getEntry(0, 0).compareTo(new BigDecimal(123)));
    }

    // covers: default values and set/get round-trip for roundingMode and scale
    @Test
    public void testRoundingModeAndScale_getAndSet() throws Throwable {
        BigMatrixImpl m = new BigMatrixImpl(1, 1);
        assertEquals(BigDecimal.ROUND_HALF_UP, m.getRoundingMode());
        assertEquals(64, m.getScale());
        m.setRoundingMode(BigDecimal.ROUND_DOWN);
        m.setScale(10);
        assertEquals(BigDecimal.ROUND_DOWN, m.getRoundingMode());
        assertEquals(10, m.getScale());
    }

    // covers: getNorm() loop over columns/rows with multiple entries
    @Test
    public void testGetNorm_symmetricMatrix_returnsMaxAbsoluteSum() throws Throwable {
        BigMatrixImpl sym = new BigMatrixImpl(bd(new int[][] { {1,2}, {2,1} }));
        assertEquals(0, sym.getNorm().compareTo(new BigDecimal(3)));
    }

    // covers: getSubMatrix(int,int,int,int) normal in-range selection
    @Test
    public void testGetSubMatrixRange_validRange_returnsExpectedSubMatrix() throws Throwable {
        BigMatrix sub = rect.getSubMatrix(0, 1, 1, 2);
        assertEquals(0, sub.getEntry(0, 0).compareTo(new BigDecimal(2)));
        assertEquals(0, sub.getEntry(1, 1).compareTo(new BigDecimal(6)));
    }





    // covers: startRow>endRow branch and negative startRow branch of getSubMatrix(int,int,int,int)
    @Test
    public void testGetSubMatrixRange_invalidOrdering_throwsMatrixIndexException() throws Throwable {
        try {
            rect.getSubMatrix(1, 0, 0, 1);
            fail("expected MatrixIndexException for startRow>endRow");
        } catch (MatrixIndexException expected) {
        }
        try {
            rect.getSubMatrix(-1, 1, 0, 1);
            fail("expected MatrixIndexException for negative startRow");
        } catch (MatrixIndexException expected) {
        }
    }

    // covers: getSubMatrix(int[],int[]) normal selection and the empty-selection throw
    @Test
    public void testGetSubMatrixArrays_validAndEmptySelection() throws Throwable {
        BigMatrix sel = rect.getSubMatrix(new int[] {0, 1}, new int[] {0, 2});
        assertEquals(0, sel.getEntry(0, 0).compareTo(new BigDecimal(1)));
        assertEquals(0, sel.getEntry(1, 1).compareTo(new BigDecimal(6)));
        try {
            rect.getSubMatrix(new int[] {}, new int[] {0});
            fail("expected MatrixIndexException for empty selection");
        } catch (MatrixIndexException expected) {
        }
    }

    // covers: setSubMatrix normal replacement of entries
    @Test
    public void testSetSubMatrix_validReplace_updatesEntries() throws Throwable {
        a.setSubMatrix(bd(new int[][] { {99} }), 1, 1);
        assertEquals(0, a.getEntry(1, 1).compareTo(new BigDecimal(99)));
        assertEquals(0, a.getEntry(0, 0).compareTo(new BigDecimal(1)));
    }

    // covers: negative row/column branch and oversized-submatrix branch of setSubMatrix
    @Test
    public void testSetSubMatrix_invalidIndices_throwsMatrixIndexException() throws Throwable {
        try {
            a.setSubMatrix(bd(new int[][] { {1} }), -1, 0);
            fail("expected MatrixIndexException for negative row index");
        } catch (MatrixIndexException expected) {
        }
        try {
            a.setSubMatrix(bd(new int[][] { {1, 2, 3} }), 0, 0);
            fail("expected MatrixIndexException for oversized submatrix");
        } catch (MatrixIndexException expected) {
        }
    }

    // covers: getRowMatrix and getColumnMatrix normal paths
    @Test
    public void testGetRowMatrixAndColumnMatrix_valid_returnsExpectedSubMatrices() throws Throwable {
        BigMatrix rowM = a.getRowMatrix(0);
        assertEquals(1, rowM.getRowDimension());
        assertEquals(0, rowM.getEntry(0, 1).compareTo(new BigDecimal(2)));
        BigMatrix colM = a.getColumnMatrix(1);
        assertEquals(2, colM.getRowDimension());
        assertEquals(0, colM.getEntry(1, 0).compareTo(new BigDecimal(4)));
    }

    // covers: invalid-index throw branches of getRowMatrix and getColumnMatrix
    @Test
    public void testGetRowMatrixAndColumnMatrix_invalidIndex_throwsMatrixIndexException() throws Throwable {
        try {
            a.getRowMatrix(5);
            fail("expected MatrixIndexException for invalid row");
        } catch (MatrixIndexException expected) {
        }
        try {
            a.getColumnMatrix(-1);
            fail("expected MatrixIndexException for invalid column");
        } catch (MatrixIndexException expected) {
        }
    }

    // covers: getRow and getColumn normal paths returning arrays
    @Test
    public void testGetRowAndGetColumn_validIndices_returnsExpectedArrays() throws Throwable {
        BigDecimal[] row = a.getRow(1);
        assertEquals(0, row[0].compareTo(new BigDecimal(3)));
        BigDecimal[] col = a.getColumn(0);
        assertEquals(0, col[1].compareTo(new BigDecimal(3)));
    }

    // covers: getEntry invalid-index throw branch (caught ArrayIndexOutOfBoundsException rethrown)
    @Test
    public void testGetEntry_invalidIndex_throwsMatrixIndexException() throws Throwable {
        try {
            a.getEntry(10, 10);
            fail("expected MatrixIndexException for invalid index");
        } catch (MatrixIndexException expected) {
        }
    }

    // covers: getEntryAsDouble normal conversion path
    @Test
    public void testGetEntryAsDouble_validIndex_returnsDoubleValue() throws Throwable {
        assertEquals(4.0, a.getEntryAsDouble(1, 1), 1e-9);
    }

    // covers: transpose() swapping rows and columns
    @Test
    public void testTranspose_swapsRowsAndColumns() throws Throwable {
        BigMatrix t = a.transpose();
        assertEquals(0, t.getEntry(0, 1).compareTo(new BigDecimal(3)));
        assertEquals(0, t.getEntry(1, 0).compareTo(new BigDecimal(2)));
    }

    // covers: inverse() of an invertible matrix via solve(identity)
    @Test
    public void testInverse_ofInvertibleMatrix_returnsCorrectInverse() throws Throwable {
        BigMatrix inv = diag.inverse();
        assertEquals(0, inv.getEntry(0, 0).compareTo(new BigDecimal("0.5")));
        assertEquals(0, inv.getEntry(1, 1).compareTo(new BigDecimal("0.5")));
        assertEquals(0, inv.getEntry(0, 1).compareTo(BigDecimal.ZERO));
    }



    // covers: isSquare() true/false branches and isSingular() true/false branches
    @Test
    public void testIsSquareAndIsSingular_variousMatrices() throws Throwable {
        assertTrue(a.isSquare());
        assertFalse(rect.isSquare());
        assertFalse(a.isSingular());
        assertTrue(singular.isSingular());
    }

    // covers: getRowDimension and getColumnDimension accessors
    @Test
    public void testGetRowDimensionAndColumnDimension_returnsCorrectCounts() throws Throwable {
        assertEquals(2, rect.getRowDimension());
        assertEquals(3, rect.getColumnDimension());
    }

    // covers: getTrace() square sum path and the non-square throw branch
    @Test
    public void testGetTrace_squareAndNonSquareMatrix() throws Throwable {
        assertEquals(0, a.getTrace().compareTo(new BigDecimal(5)));
        try {
            rect.getTrace();
            fail("expected IllegalArgumentException for non-square matrix");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: operate(BigDecimal[]) normal path and the wrong-length throw branch
    @Test
    public void testOperate_validAndWrongLength() throws Throwable {
        BigDecimal[] out = a.operate(bdVec(new int[] {1, 1}));
        assertEquals(0, out[0].compareTo(new BigDecimal(3)));
        assertEquals(0, out[1].compareTo(new BigDecimal(7)));
        try {
            a.operate(bdVec(new int[] {1, 1, 1}));
            fail("expected IllegalArgumentException for wrong vector length");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: preMultiply(BigDecimal[]) normal path
    @Test
    public void testPreMultiplyVector_returnsCorrectResult() throws Throwable {
        BigDecimal[] out = a.preMultiply(bdVec(new int[] {1, 1}));
        assertEquals(0, out[0].compareTo(new BigDecimal(4)));
        assertEquals(0, out[1].compareTo(new BigDecimal(6)));
    }

    // covers: solve(BigDecimal[]) normal path and the wrong-length throw branch
    @Test
    public void testSolveBigDecimalArray_validAndWrongLength() throws Throwable {
        BigDecimal[] sol = diag.solve(bdVec(new int[] {4, 6}));
        assertEquals(0, sol[0].compareTo(new BigDecimal(2)));
        assertEquals(0, sol[1].compareTo(new BigDecimal(3)));
        try {
            diag.solve(bdVec(new int[] {1, 2, 3}));
            fail("expected IllegalArgumentException for wrong vector length");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: solve(BigMatrix) non-square-coefficient throw and singular-coefficient throw branches
    @Test
    public void testSolveBigMatrix_nonSquareAndSingularCoefficientMatrix() throws Throwable {
        BigMatrix rhs = new BigMatrixImpl(bd(new int[][] { {1}, {1} }));
        try {
            rect.solve(rhs);
            fail("expected InvalidMatrixException for non-square coefficient matrix");
        } catch (InvalidMatrixException expected) {
        }
        try {
            singular.solve(rhs);
            fail("expected InvalidMatrixException for singular coefficient matrix");
        } catch (InvalidMatrixException expected) {
        }
    }

    // covers: luDecompose() non-square throw branch and getLUMatrix() normal path (protected, same package)
    @Test
    public void testLuDecomposeAndGetLUMatrix_nonSquareThrows_andValidReturnsDimensions() throws Throwable {
        try {
            rect.luDecompose();
            fail("expected InvalidMatrixException for non-square matrix");
        } catch (InvalidMatrixException expected) {
        }
        BigMatrix lu = a.getLUMatrix();
        assertEquals(2, lu.getRowDimension());
        assertEquals(2, lu.getColumnDimension());
    }

    // covers: toString() producing a textual representation containing the entries
    @Test
    public void testToString_containsMatrixEntries() throws Throwable {
        String s = a.toString();
        assertTrue(s.indexOf("BigMatrixImpl") >= 0);
        assertTrue(s.indexOf("1") >= 0);
    }

    // covers: equals() same-values, different-values, different-type and different-dimension branches, plus hashCode consistency
    @Test
    public void testEqualsAndHashCode_variousComparisons() throws Throwable {
        BigMatrixImpl a2 = new BigMatrixImpl(bd(new int[][] { {1,2}, {3,4} }));
        assertTrue(a.equals(a2));
        assertEquals(a.hashCode(), a2.hashCode());
        assertFalse(a.equals(b));
        assertFalse(a.equals("not a matrix"));
        assertFalse(a.equals(rect));
    }
}
