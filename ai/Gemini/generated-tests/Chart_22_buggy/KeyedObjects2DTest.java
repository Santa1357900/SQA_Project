package org.jfree.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;

public class KeyedObjects2DTest {

    @Test
    public void testConstructorAndInitialState() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        assertEquals(0, ko2d.getRowCount());
        assertEquals(0, ko2d.getColumnCount());
        assertNotNull(ko2d.getRowKeys());
        assertNotNull(ko2d.getColumnKeys());
        assertTrue(ko2d.getRowKeys().isEmpty());
        assertTrue(ko2d.getColumnKeys().isEmpty());
    }

    @Test
    public void testAddAndGetObject() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("Val1", "R1", "C1");
        ko2d.setObject("Val2", "R1", "C2");

        assertEquals(1, ko2d.getRowCount());
        assertEquals(2, ko2d.getColumnCount());

        assertEquals("R1", ko2d.getRowKey(0));
        assertEquals(0, ko2d.getRowIndex("R1"));
        assertEquals(-1, ko2d.getRowIndex("NonExistent"));

        assertEquals("C1", ko2d.getColumnKey(0));
        assertEquals("C2", ko2d.getColumnKey(1));
        assertEquals(0, ko2d.getColumnIndex("C1"));
        assertEquals(1, ko2d.getColumnIndex("C2"));
        assertEquals(-1, ko2d.getColumnIndex("NonExistent"));

        assertEquals("Val1", ko2d.getObject(0, 0));
        assertEquals("Val2", ko2d.getObject(0, 1));
        assertEquals("Val1", ko2d.getObject("R1", "C1"));
        assertEquals("Val2", ko2d.getObject("R1", "C2"));
    }

    @Test
    public void testGetObjectWithIndexScenarios() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("ValueA", "Row1", "Col1");
        
        // Test getObject(int, int) when rowData is null (via internal or boundary, though normally rows match rowKeys)
        // Let's test valid indices and out-of-bounds or edge structures if possible.
        assertEquals("ValueA", ko2d.getObject(0, 0));

        // Test getting with keys
        assertEquals("ValueA", ko2d.getObject("Row1", "Col1"));
    }

    @Test
    public void testGetObjectExceptions() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        
        try {
            ko2d.getObject(null, "Col1");
            fail("Expected IllegalArgumentException for null rowKey");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("rowKey"));
        }

        try {
            ko2d.getObject("Row1", null);
            fail("Expected IllegalArgumentException for null columnKey");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("columnKey"));
        }

        try {
            ko2d.getObject("NonExistentRow", "Col1");
            fail("Expected UnknownKeyException for unknown rowKey");
        } catch (UnknownKeyException e) {
            assertTrue(e.getMessage().contains("Row key"));
        }

        ko2d.addObject("Val", "Row1", "Col1");
        try {
            ko2d.getObject("Row1", "NonExistentCol");
            fail("Expected UnknownKeyException for unknown columnKey");
        } catch (UnknownKeyException e) {
            assertTrue(e.getMessage().contains("Column key"));
        }
    }

    @Test
    public void testSetObjectValidation() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        
        try {
            ko2d.setObject("Val", null, "Col1");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("rowKey"));
        }

        try {
            ko2d.setObject("Val", "Row1", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("columnKey"));
        }
    }

    @Test
    public void testRemoveObject() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("Val1", "R1", "C1");
        ko2d.addObject("Val2", "R1", "C2");

        // Remove single object, row R1 still has Val2, so row should remain
        ko2d.removeObject("R1", "C1");
        assertNull(ko2d.getObject("R1", "C1"));
        assertEquals(1, ko2d.getRowCount());

        // Remove second object, row R1 becomes all null, so row should be removed automatically
        ko2d.removeObject("R1", "C2");
        assertEquals(0, ko2d.getRowCount());
    }

    @Test
    public void testRemoveRowByIndex() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("Val1", "R1", "C1");
        ko2d.addObject("Val2", "R2", "C1");

        assertEquals(2, ko2d.getRowCount());
        ko2d.removeRow(0);
        assertEquals(1, ko2d.getRowCount());
        assertEquals("R2", ko2d.getRowKey(0));
    }

    @Test
    public void testRemoveRowByKey() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("Val1", "R1", "C1");
        ko2d.addObject("Val2", "R2", "C1");

        ko2d.removeRow("R1");
        assertEquals(1, ko2d.getRowCount());
        assertEquals("R2", ko2d.getRowKey(0));
    }

    @Test
    public void testRemoveColumnByIndex() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("Val1", "R1", "C1");
        ko2d.addObject("Val2", "R1", "C2");

        assertEquals(2, ko2d.getColumnCount());
        ko2d.removeColumn(0);
        assertEquals(1, ko2d.getColumnCount());
        assertEquals("C2", ko2d.getColumnKey(0));
    }

    @Test
    public void testRemoveColumnByKey() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("Val1", "R1", "C1");
        ko2d.addObject("Val2", "R1", "C2");

        ko2d.removeColumn("C1");
        assertEquals(1, ko2d.getColumnCount());
        assertEquals("C2", ko2d.getColumnKey(0));

        try {
            ko2d.removeColumn("NonExistentColumn");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            assertTrue(e.getMessage().contains("Column key"));
        }
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        KeyedObjects2D ko2d1 = new KeyedObjects2D();
        KeyedObjects2D ko2d2 = new KeyedObjects2D();

        assertTrue(ko2d1.equals(ko2d1));
        assertFalse(ko2d1.equals(null));
        assertFalse(ko2d1.equals("SomeString"));

        assertTrue(ko2d1.equals(ko2d2));
        assertEquals(ko2d1.hashCode(), ko2d2.hashCode());

        ko2d1.addObject("Val", "R1", "C1");
        assertFalse(ko2d1.equals(ko2d2));

        ko2d2.addObject("Val", "R1", "C1");
        assertTrue(ko2d1.equals(ko2d2));
        assertEquals(ko2d1.hashCode(), ko2d2.hashCode());

        // Test row keys mismatch
        KeyedObjects2D ko2d3 = new KeyedObjects2D();
        ko2d3.addObject("Val", "R2", "C1");
        assertFalse(ko2d1.equals(ko2d3));

        // Test column keys mismatch
        KeyedObjects2D ko2d4 = new KeyedObjects2D();
        ko2d4.addObject("Val", "R1", "C2");
        assertFalse(ko2d1.equals(ko2d4));

        // Test row count / col count mismatch or null values
        KeyedObjects2D ko2d5 = new KeyedObjects2D();
        ko2d5.addObject(null, "R1", "C1");
        assertFalse(ko2d1.equals(ko2d5));

        KeyedObjects2D ko2d6 = new KeyedObjects2D();
        ko2d6.addObject(null, "R1", "C1");
        assertTrue(ko2d5.equals(ko2d6));
        
        // v1 is not null, v2 is null
        assertFalse(ko2d1.equals(ko2d5));
    }

    @Test
    public void testClone() throws Throwable {
        KeyedObjects2D ko2d = new KeyedObjects2D();
        ko2d.addObject("Val1", "R1", "C1");

        KeyedObjects2D clone = (KeyedObjects2D) ko2d.clone();
        assertNotNull(clone);
        assertTrue(ko2d.equals(clone));
        assertFalse(ko2d == clone);

        // Modify original, ensure clone is independent
        ko2d.addObject("Val2", "R2", "C2");
        assertFalse(ko2d.equals(clone));
    }
}