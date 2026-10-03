package org.jfree.data;

import java.util.List;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class KeyedObjects2DClaudeTest {

    private KeyedObjects2D data;

    @Before
    public void setUp() throws Throwable {
        data = new KeyedObjects2D();
    }

    // Constructor: new instance is empty
    @Test
    public void testConstructor_emptyByDefault() throws Throwable {
        assertEquals(0, data.getRowCount());
        assertEquals(0, data.getColumnCount());
    }

    // getRowCount after adding rows via setObject
    @Test
    public void testGetRowCount_afterAddingRows() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R2", "C1");
        assertEquals(2, data.getRowCount());
    }

    // getColumnCount after adding columns via setObject
    @Test
    public void testGetColumnCount_afterAddingColumns() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R1", "C2");
        assertEquals(2, data.getColumnCount());
    }

    // getObject(int,int) returns the stored object
    @Test
    public void testGetObjectByIndex_returnsObject() throws Throwable {
        data.setObject("value", "R1", "C1");
        assertEquals("value", data.getObject(0, 0));
    }

    // getObject(int,int) out of range row -> IndexOutOfBoundsException
    @Test
    public void testGetObjectByIndex_rowOutOfBounds_throws() throws Throwable {
        data.setObject("value", "R1", "C1");
        try {
            data.getObject(5, 0);
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // getRowKey returns the correct key at index
    @Test
    public void testGetRowKey_returnsCorrectKey() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertEquals("R1", data.getRowKey(0));
    }

    // getRowKey out of range -> IndexOutOfBoundsException
    @Test
    public void testGetRowKey_outOfBounds_throws() throws Throwable {
        try {
            data.getRowKey(0);
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // getRowIndex finds an existing key
    @Test
    public void testGetRowIndex_found() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertEquals(0, data.getRowIndex("R1"));
    }

    // getRowIndex returns -1 for unknown key
    @Test
    public void testGetRowIndex_notFound_returnsMinusOne() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertEquals(-1, data.getRowIndex("UNKNOWN"));
    }

    // getRowKeys returns unmodifiable list
    @Test
    public void testGetRowKeys_unmodifiable() throws Throwable {
        data.setObject("v", "R1", "C1");
        List keys = data.getRowKeys();
        try {
            keys.add("X");
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected) {
        }
    }

    // getColumnKey returns the correct key at index
    @Test
    public void testGetColumnKey_returnsCorrectKey() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertEquals("C1", data.getColumnKey(0));
    }

    // getColumnKey out of range -> IndexOutOfBoundsException
    @Test
    public void testGetColumnKey_outOfBounds_throws() throws Throwable {
        try {
            data.getColumnKey(0);
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // getColumnIndex finds an existing key
    @Test
    public void testGetColumnIndex_found() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertEquals(0, data.getColumnIndex("C1"));
    }

    // getColumnIndex returns -1 for unknown key
    @Test
    public void testGetColumnIndex_notFound_returnsMinusOne() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertEquals(-1, data.getColumnIndex("UNKNOWN"));
    }

    // getColumnKeys returns unmodifiable list
    @Test
    public void testGetColumnKeys_unmodifiable() throws Throwable {
        data.setObject("v", "R1", "C1");
        List keys = data.getColumnKeys();
        try {
            keys.add("X");
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected) {
        }
    }

    // getObject(Comparable,Comparable) returns stored object
    @Test
    public void testGetObjectByKeys_returnsObject() throws Throwable {
        data.setObject("value", "R1", "C1");
        assertEquals("value", data.getObject("R1", "C1"));
    }

    // getObject(Comparable,Comparable) null rowKey -> IllegalArgumentException
    @Test
    public void testGetObjectByKeys_nullRowKey_throwsIAE() throws Throwable {
        try {
            data.getObject((Comparable) null, "C1");
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // getObject(Comparable,Comparable) null columnKey -> IllegalArgumentException
    @Test
    public void testGetObjectByKeys_nullColumnKey_throwsIAE() throws Throwable {
        try {
            data.getObject("R1", (Comparable) null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // getObject(Comparable,Comparable) unknown row key -> UnknownKeyException
    @Test
    public void testGetObjectByKeys_unknownRowKey_throwsUnknownKeyException() throws Throwable {
        data.setObject("v", "R1", "C1");
        try {
            data.getObject("BAD_ROW", "C1");
            fail("expected UnknownKeyException");
        }
        catch (UnknownKeyException expected) {
        }
    }

    // getObject(Comparable,Comparable) unknown column key -> UnknownKeyException
    @Test
    public void testGetObjectByKeys_unknownColumnKey_throwsUnknownKeyException() throws Throwable {
        data.setObject("v", "R1", "C1");
        try {
            data.getObject("R1", "BAD_COL");
            fail("expected UnknownKeyException");
        }
        catch (UnknownKeyException expected) {
        }
    }

    // addObject delegates to setObject and stores value correctly
    @Test
    public void testAddObject_delegatesToSetObject() throws Throwable {
        data.addObject("added", "R1", "C1");
        assertEquals("added", data.getObject("R1", "C1"));
    }

    // setObject null rowKey -> IllegalArgumentException
    @Test
    public void testSetObject_nullRowKey_throwsIAE() throws Throwable {
        try {
            data.setObject("v", null, "C1");
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // setObject null columnKey -> IllegalArgumentException
    @Test
    public void testSetObject_nullColumnKey_throwsIAE() throws Throwable {
        try {
            data.setObject("v", "R1", null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // setObject with new row and new column creates both
    @Test
    public void testSetObject_newRowNewColumn() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertEquals(1, data.getRowCount());
        assertEquals(1, data.getColumnCount());
    }

    // setObject with existing row and new column does not duplicate row
    @Test
    public void testSetObject_existingRowNewColumn() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R1", "C2");
        assertEquals(1, data.getRowCount());
        assertEquals(2, data.getColumnCount());
    }

    // setObject on existing row/column updates value without adding keys
    @Test
    public void testSetObject_existingRowExistingColumn_updates() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R1", "C1");
        assertEquals("v2", data.getObject("R1", "C1"));
        assertEquals(1, data.getRowCount());
        assertEquals(1, data.getColumnCount());
    }

    // removeObject: row becomes all-null and is removed
    @Test
    public void testRemoveObject_rowBecomesEmpty_rowRemoved() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.removeObject("R1", "C1");
        assertEquals(0, data.getRowCount());
    }

    // removeObject: column becomes all-null and must also be removed per javadoc
    @Test
    public void testRemoveObject_columnBecomesEmpty_columnRemoved() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.removeObject("R1", "C1");
        assertEquals(0, data.getColumnCount());
    }

    // removeObject: row not fully empty remains present
    @Test
    public void testRemoveObject_rowNotEmpty_rowRemains() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R1", "C2");
        data.removeObject("R1", "C1");
        assertEquals(1, data.getRowCount());
        assertNull(data.getObject("R1", "C1"));
    }

    // removeRow(int) removes row and shifts indices
    @Test
    public void testRemoveRowByIndex() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R2", "C1");
        data.removeRow(0);
        assertEquals(1, data.getRowCount());
        assertEquals("R2", data.getRowKey(0));
    }

    // removeRow(Comparable) removes the correct row by key
    @Test
    public void testRemoveRowByKey() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R2", "C1");
        data.removeRow("R1");
        assertEquals(1, data.getRowCount());
        assertEquals("R2", data.getRowKey(0));
    }

    // removeRow(Comparable) with unknown key leads to IndexOutOfBoundsException
    @Test
    public void testRemoveRowByKey_unknownKey_throws() throws Throwable {
        data.setObject("v1", "R1", "C1");
        try {
            data.removeRow("UNKNOWN");
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // removeColumn(int) removes the column and its values
    @Test
    public void testRemoveColumnByIndex() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R1", "C2");
        data.removeColumn(0);
        assertEquals(1, data.getColumnCount());
        assertEquals("C2", data.getColumnKey(0));
    }

    // removeColumn(Comparable) removes the correct column by key
    @Test
    public void testRemoveColumnByKey() throws Throwable {
        data.setObject("v1", "R1", "C1");
        data.setObject("v2", "R1", "C2");
        data.removeColumn("C1");
        assertEquals(1, data.getColumnCount());
        assertEquals("C2", data.getColumnKey(0));
    }

    // removeColumn(Comparable) with unknown key -> UnknownKeyException
    @Test
    public void testRemoveColumnByKey_unknownKey_throws() throws Throwable {
        data.setObject("v1", "R1", "C1");
        try {
            data.removeColumn("UNKNOWN");
            fail("expected UnknownKeyException");
        }
        catch (UnknownKeyException expected) {
        }
    }

    // equals: same instance reference returns true
    @Test
    public void testEquals_sameInstance() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertTrue(data.equals(data));
    }

    // equals: different type returns false
    @Test
    public void testEquals_differentType() throws Throwable {
        data.setObject("v", "R1", "C1");
        assertFalse(data.equals("not a KeyedObjects2D"));
    }

    // equals: two objects with same keys and values are equal
    @Test
    public void testEquals_equalObjects() throws Throwable {
        data.setObject("v", "R1", "C1");
        KeyedObjects2D other = new KeyedObjects2D();
        other.setObject("v", "R1", "C1");
        assertTrue(data.equals(other));
    }

    // equals: different row keys returns false
    @Test
    public void testEquals_differentRowKeys() throws Throwable {
        data.setObject("v", "R1", "C1");
        KeyedObjects2D other = new KeyedObjects2D();
        other.setObject("v", "R2", "C1");
        assertFalse(data.equals(other));
    }

    // equals: different column keys returns false
    @Test
    public void testEquals_differentColumnKeys() throws Throwable {
        data.setObject("v", "R1", "C1");
        KeyedObjects2D other = new KeyedObjects2D();
        other.setObject("v", "R1", "C2");
        assertFalse(data.equals(other));
    }

    // equals: same keys but different stored values returns false
    @Test
    public void testEquals_differentValues() throws Throwable {
        data.setObject("v1", "R1", "C1");
        KeyedObjects2D other = new KeyedObjects2D();
        other.setObject("v2", "R1", "C1");
        assertFalse(data.equals(other));
    }

    // hashCode: equal objects produce equal hash codes
    @Test
    public void testHashCode_consistentWithEquals() throws Throwable {
        data.setObject("v", "R1", "C1");
        KeyedObjects2D other = new KeyedObjects2D();
        other.setObject("v", "R1", "C1");
        assertEquals(data.hashCode(), other.hashCode());
    }

    // clone produces an independent, equal copy
    @Test
    public void testClone_independentCopy() throws Throwable {
        data.setObject("v1", "R1", "C1");
        KeyedObjects2D clone = (KeyedObjects2D) data.clone();
        assertTrue(data.equals(clone));
        clone.setObject("v2", "R1", "C1");
        assertEquals("v1", data.getObject("R1", "C1"));
        assertEquals("v2", clone.getObject("R1", "C1"));
    }
}
