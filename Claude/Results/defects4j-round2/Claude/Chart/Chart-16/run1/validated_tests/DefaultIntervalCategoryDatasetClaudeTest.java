package org.jfree.data.category;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jfree.data.UnknownKeyException;

public class DefaultIntervalCategoryDatasetClaudeTest {

    private Comparable[] seriesKeys;
    private Comparable[] categoryKeys;
    private Number[][] starts;
    private Number[][] ends;
    private DefaultIntervalCategoryDataset dataset;

    @Before
    public void setUp() throws Throwable {
        seriesKeys = new Comparable[] {"S1", "S2"};
        categoryKeys = new Comparable[] {"C1", "C2", "C3"};
        starts = new Number[][] {
            {1.0, 2.0, 3.0},
            {4.0, 5.0, 6.0}
        };
        ends = new Number[][] {
            {1.5, 2.5, 3.5},
            {4.5, 5.5, 6.5}
        };
        dataset = new DefaultIntervalCategoryDataset(seriesKeys, categoryKeys, starts, ends);
    }

    // covers double[][] constructor + auto generated series/category keys
    @Test
    public void testConstructorDoubleArray_validData_createsDatasetWithAutoKeys() throws Throwable {
        double[][] s = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] e = {{1.5, 2.5}, {3.5, 4.5}};
        DefaultIntervalCategoryDataset ds = new DefaultIntervalCategoryDataset(s, e);
        assertEquals(2, ds.getSeriesCount());
        assertEquals(2, ds.getCategoryCount());
        assertEquals("Series 1", ds.getSeriesKey(0).toString());
        assertEquals("Category 1", ds.getColumnKey(0).toString());
    }

    // covers 4-arg constructor with explicit keys
    @Test
    public void testConstructorWithKeys_valid_usesProvidedKeys() throws Throwable {
        assertEquals("S1", dataset.getSeriesKey(0).toString());
        assertEquals("C2", dataset.getColumnKey(1).toString());
    }

    // covers series count mismatch branch -> IllegalArgumentException
    @Test
    public void testConstructor_seriesCountMismatch_throwsIllegalArgumentException() throws Throwable {
        Number[][] s = { {1.0} };
        Number[][] e = { {1.0}, {2.0} };
        try {
            new DefaultIntervalCategoryDataset(s, e);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers category count mismatch branch -> IllegalArgumentException
    @Test
    public void testConstructor_categoryCountMismatch_throwsIllegalArgumentException() throws Throwable {
        Number[][] s = { {1.0, 2.0} };
        Number[][] e = { {1.0} };
        try {
            new DefaultIntervalCategoryDataset(s, e);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers seriesKeys.length mismatch -> IllegalArgumentException
    @Test
    public void testConstructor_seriesKeysLengthMismatch_throwsIllegalArgumentException() throws Throwable {
        Comparable[] sk = new Comparable[] {"OnlyOne"};
        Number[][] s = { {1.0}, {2.0} };
        Number[][] e = { {1.5}, {2.5} };
        try {
            new DefaultIntervalCategoryDataset(sk, null, s, e);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers categoryKeys.length mismatch -> IllegalArgumentException
    @Test
    public void testConstructor_categoryKeysLengthMismatch_throwsIllegalArgumentException() throws Throwable {
        Comparable[] ck = new Comparable[] {"OnlyOne"};
        Number[][] s = { {1.0, 2.0} };
        Number[][] e = { {1.5, 2.5} };
        try {
            new DefaultIntervalCategoryDataset(null, ck, s, e);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // bug-revealing: empty dataset getRowCount must return 0 per javadoc, not NPE
    @Test
    public void testGetRowCount_emptyDataset_returnsZeroNotThrow() throws Throwable {
        Number[][] s = new Number[0][];
        Number[][] e = new Number[0][];
        DefaultIntervalCategoryDataset ds = new DefaultIntervalCategoryDataset(s, e);
        assertEquals(0, ds.getRowCount());
    }

    // bug-revealing: empty dataset getColumnCount must return 0 per javadoc, not NPE
    @Test
    public void testGetColumnCount_emptyDataset_returnsZeroNotThrow() throws Throwable {
        Number[][] s = new Number[0][];
        Number[][] e = new Number[0][];
        DefaultIntervalCategoryDataset ds = new DefaultIntervalCategoryDataset(s, e);
        assertEquals(0, ds.getColumnCount());
    }

    // covers getSeriesCount/getCategoryCount when startData is null
    @Test
    public void testGetSeriesCount_nullStartData_returnsZero() throws Throwable {
        DefaultIntervalCategoryDataset ds = new DefaultIntervalCategoryDataset(
                (Comparable[]) null, (Comparable[]) null, (Number[][]) null, (Number[][]) null);
        assertEquals(0, ds.getSeriesCount());
        assertEquals(0, ds.getCategoryCount());
    }

    // covers normal getSeriesCount branch
    @Test
    public void testGetSeriesCount_normalData_returnsCorrectCount() throws Throwable {
        assertEquals(2, dataset.getSeriesCount());
    }

    // covers getSeriesIndex found branch
    @Test
    public void testGetSeriesIndex_knownKey_returnsCorrectIndex() throws Throwable {
        assertEquals(1, dataset.getSeriesIndex("S2"));
    }

    // covers getSeriesIndex not-found branch
    @Test
    public void testGetSeriesIndex_unknownKey_returnsMinusOne() throws Throwable {
        assertEquals(-1, dataset.getSeriesIndex("ZZZ"));
    }

    // covers getSeriesKey valid index branch
    @Test
    public void testGetSeriesKey_validIndex_returnsKey() throws Throwable {
        assertEquals("S1", dataset.getSeriesKey(0).toString());
    }

    // covers getSeriesKey out-of-range branch -> IllegalArgumentException
    @Test
    public void testGetSeriesKey_invalidIndex_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.getSeriesKey(5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setSeriesKeys null argument branch
    @Test
    public void testSetSeriesKeys_null_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.setSeriesKeys(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setSeriesKeys wrong length branch
    @Test
    public void testSetSeriesKeys_wrongLength_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.setSeriesKeys(new Comparable[] {"OnlyOne"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setSeriesKeys valid update branch
    @Test
    public void testSetSeriesKeys_valid_updatesKeys() throws Throwable {
        dataset.setSeriesKeys(new Comparable[] {"X", "Y"});
        assertEquals("X", dataset.getSeriesKey(0).toString());
        assertEquals("Y", dataset.getSeriesKey(1).toString());
    }

    // covers getCategoryCount normal branch
    @Test
    public void testGetCategoryCount_normalData_returnsCorrectCount() throws Throwable {
        assertEquals(3, dataset.getCategoryCount());
    }

    // covers getColumnKeys populated branch
    @Test
    public void testGetColumnKeys_populated_returnsCorrectList() throws Throwable {
        List keys = dataset.getColumnKeys();
        assertEquals(3, keys.size());
        assertEquals("C1", keys.get(0).toString());
    }

    // covers getColumnKeys null categoryKeys branch
    @Test
    public void testGetColumnKeys_nullCategoryKeys_returnsEmptyList() throws Throwable {
        Number[][] s = new Number[0][];
        Number[][] e = new Number[0][];
        DefaultIntervalCategoryDataset ds = new DefaultIntervalCategoryDataset(s, e);
        List keys = ds.getColumnKeys();
        assertEquals(0, keys.size());
    }

    // covers setCategoryKeys null branch
    @Test
    public void testSetCategoryKeys_null_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.setCategoryKeys(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setCategoryKeys wrong length branch
    @Test
    public void testSetCategoryKeys_wrongLength_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.setCategoryKeys(new Comparable[] {"A", "B"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setCategoryKeys contains-null branch
    @Test
    public void testSetCategoryKeys_containsNull_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.setCategoryKeys(new Comparable[] {"A", null, "C"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setCategoryKeys valid update branch
    @Test
    public void testSetCategoryKeys_valid_updatesKeys() throws Throwable {
        dataset.setCategoryKeys(new Comparable[] {"X1", "X2", "X3"});
        assertEquals("X1", dataset.getColumnKey(0).toString());
    }

    // covers getValue(Comparable,Comparable) unknown series branch
    @Test
    public void testGetValueComparable_unknownSeries_throwsUnknownKeyException() throws Throwable {
        try {
            dataset.getValue("ZZZ", "C1");
            fail("expected UnknownKeyException");
        } catch (UnknownKeyException expected) {
            // expected
        }
    }

    // covers getValue(Comparable,Comparable) unknown category branch
    @Test
    public void testGetValueComparable_unknownCategory_throwsUnknownKeyException() throws Throwable {
        try {
            dataset.getValue("S1", "ZZZ");
            fail("expected UnknownKeyException");
        } catch (UnknownKeyException expected) {
            // expected
        }
    }

    // covers getValue(Comparable,Comparable) valid branch, returns end value; also verifies getValue(int,int) delegation
    @Test
    public void testGetValueComparable_valid_returnsEndValue() throws Throwable {
        Number v = dataset.getValue("S1", "C1");
        assertEquals(1.5, v.doubleValue(), 1e-9);
        assertEquals(dataset.getEndValue(0, 0).doubleValue(), dataset.getValue(0, 0).doubleValue(), 1e-9);
    }

    // covers getStartValue(int,int) series out-of-range branch
    @Test
    public void testGetStartValueIntInt_seriesOutOfRange_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.getStartValue(-1, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers getStartValue(int,int) category out-of-range branch
    @Test
    public void testGetStartValueIntInt_categoryOutOfRange_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.getStartValue(0, 10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers getStartValue(Comparable,Comparable) valid branch
    @Test
    public void testGetStartValueComparable_valid_returnsCorrectValue() throws Throwable {
        Number v = dataset.getStartValue("S2", "C3");
        assertEquals(6.0, v.doubleValue(), 1e-9);
    }

    // covers getEndValue(Comparable,Comparable) valid branch
    @Test
    public void testGetEndValueComparable_valid_returnsCorrectValue() throws Throwable {
        Number v = dataset.getEndValue("S2", "C3");
        assertEquals(6.5, v.doubleValue(), 1e-9);
    }

    // covers setStartValue invalid series branch
    @Test
    public void testSetStartValue_invalidSeries_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.setStartValue(5, "C1", new Double(9.9));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setStartValue invalid category branch (getCategoryIndex returns -1)
    @Test
    public void testSetStartValue_invalidCategory_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.setStartValue(0, "ZZZ", new Double(9.9));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setStartValue valid update branch
    @Test
    public void testSetStartValue_valid_updatesValue() throws Throwable {
        dataset.setStartValue(0, "C1", new Double(99.0));
        assertEquals(99.0, dataset.getStartValue(0, 0).doubleValue(), 1e-9);
    }

    // covers setEndValue valid update branch
    @Test
    public void testSetEndValue_valid_updatesValue() throws Throwable {
        dataset.setEndValue(0, "C1", new Double(88.0));
        assertEquals(88.0, dataset.getEndValue(0, 0).doubleValue(), 1e-9);
    }

    // covers getCategoryIndex not-found branch
    @Test
    public void testGetCategoryIndex_unknownCategory_returnsMinusOne() throws Throwable {
        assertEquals(-1, dataset.getCategoryIndex("ZZZ"));
    }

    // covers getColumnKey valid index branch
    @Test
    public void testGetColumnKey_validIndex_returnsKey() throws Throwable {
        assertEquals("C2", dataset.getColumnKey(1).toString());
    }

    // covers getColumnIndex null argument branch
    @Test
    public void testGetColumnIndex_nullKey_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.getColumnIndex(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers getRowIndex delegation to getSeriesIndex
    @Test
    public void testGetRowIndex_delegatesToSeriesIndex() throws Throwable {
        assertEquals(dataset.getSeriesIndex("S2"), dataset.getRowIndex("S2"));
    }

    // covers getRowKeys populated branch
    @Test
    public void testGetRowKeys_populated_returnsCorrectList() throws Throwable {
        List keys = dataset.getRowKeys();
        assertEquals(2, keys.size());
        assertEquals("S1", keys.get(0).toString());
    }

    // covers getRowKey out-of-range branch
    @Test
    public void testGetRowKey_invalidIndex_throwsIllegalArgumentException() throws Throwable {
        try {
            dataset.getRowKey(10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers equals: self reference, wrong type, null argument branches
    @Test
    public void testEquals_selfAndVariants() throws Throwable {
        assertTrue(dataset.equals(dataset));
        assertFalse(dataset.equals("not a dataset"));
        assertFalse(dataset.equals(null));
    }

    // covers equals: same data different instances branch -> true
    @Test
    public void testEquals_sameDataDifferentInstances_returnsTrue() throws Throwable {
        Comparable[] sk = new Comparable[] {"S1", "S2"};
        Comparable[] ck = new Comparable[] {"C1", "C2", "C3"};
        Number[][] s = { {1.0, 2.0, 3.0}, {4.0, 5.0, 6.0} };
        Number[][] e = { {1.5, 2.5, 3.5}, {4.5, 5.5, 6.5} };
        DefaultIntervalCategoryDataset ds2 = new DefaultIntervalCategoryDataset(sk, ck, s, e);
        assertTrue(dataset.equals(ds2));
    }

    // covers equals: different start data branch -> false
    @Test
    public void testEquals_differentData_returnsFalse() throws Throwable {
        Comparable[] sk = new Comparable[] {"S1", "S2"};
        Comparable[] ck = new Comparable[] {"C1", "C2", "C3"};
        Number[][] s = { {9.0, 2.0, 3.0}, {4.0, 5.0, 6.0} };
        Number[][] e = { {1.5, 2.5, 3.5}, {4.5, 5.5, 6.5} };
        DefaultIntervalCategoryDataset ds2 = new DefaultIntervalCategoryDataset(sk, ck, s, e);
        assertFalse(dataset.equals(ds2));
    }

    // covers clone: produces equal but independent copy
    @Test
    public void testClone_independentCopy() throws Throwable {
        DefaultIntervalCategoryDataset clone = (DefaultIntervalCategoryDataset) dataset.clone();
        assertTrue(dataset.equals(clone));
        clone.setStartValue(0, "C1", new Double(123.0));
        assertEquals(1.0, dataset.getStartValue(0, 0).doubleValue(), 1e-9);
        assertEquals(123.0, clone.getStartValue(0, 0).doubleValue(), 1e-9);
    }
}
