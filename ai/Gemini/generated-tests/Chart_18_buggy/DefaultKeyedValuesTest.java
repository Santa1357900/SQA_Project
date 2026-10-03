package org.jfree.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.jfree.chart.util.SortOrder;

public class DefaultKeyedValuesTest {

    @Test
    public void testConstructorAndInitialState() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        assertEquals(0, data.getItemCount());
        assertNotNull(data.getKeys());
        assertTrue(data.getKeys().isEmpty());
    }

    @Test
    public void testAddAndGetValue() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("Key1", 10.5);
        data.addValue("Key2", (Number) null);

        assertEquals(2, data.getItemCount());
        assertEquals(0, data.getIndex("Key1"));
        assertEquals(1, data.getIndex("Key2"));

        assertEquals("Key1", data.getKey(0));
        assertEquals("Key2", data.getKey(1));

        assertEquals(Double.valueOf(10.5), data.getValue(0));
        assertNull(data.getValue(1));

        assertEquals(Double.valueOf(10.5), data.getValue("Key1"));
        assertNull(data.getValue("Key2"));
    }

    @Test
    public void testSetValueUpdateExisting() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.setValue("Key1", 1.0);
        data.setValue("Key1", 2.0);

        assertEquals(1, data.getItemCount());
        assertEquals(Double.valueOf(2.0), data.getValue("Key1"));
    }

    @Test
    public void testGetIndexNullKey() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        try {
            data.getIndex(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'key' argument"));
        }
    }

    @Test
    public void testGetIndexNotFound() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        assertEquals(-1, data.getIndex("NonExistent"));
    }

    @Test
    public void testGetValueUnknownKey() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        try {
            data.getValue("Unknown");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            assertTrue(e.getMessage().contains("Key not found"));
        }
    }

    @Test
    public void testSetValueNullKey() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        try {
            data.setValue(null, 5.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'key' argument"));
        }
    }

    @Test
    public void testInsertValue() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("B", 2.0);
        data.addValue("C", 3.0);

        // Insert at beginning
        data.insertValue(0, "A", 1.0);
        assertEquals(3, data.getItemCount());
        assertEquals("A", data.getKey(0));
        assertEquals(Double.valueOf(1.0), data.getValue(0));

        // Insert with existing key at same position
        data.insertValue(0, "A", 1.5);
        assertEquals(3, data.getItemCount());
        assertEquals("A", data.getKey(0));
        assertEquals(Double.valueOf(1.5), data.getValue(0));

        // Insert existing key moving position
        data.insertValue(2, "A", 1.8);
        assertEquals("A", data.getKey(2));
        assertEquals(Double.valueOf(1.8), data.getValue(2));
    }

    @Test
    public void testInsertValueOutOfBounds() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        try {
            data.insertValue(-1, "Key", 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("out of bounds"));
        }

        try {
            data.insertValue(1, "Key", 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("out of bounds"));
        }
    }

    @Test
    public void testInsertValueNullKey() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        try {
            data.insertValue(0, null, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'key' argument"));
        }
    }

    @Test
    public void testRemoveValueByIndex() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("Key1", 1.0);
        data.addValue("Key2", 2.0);
        data.addValue("Key3", 3.0);

        data.removeValue(1); // remove Key2
        assertEquals(2, data.getItemCount());
        assertEquals("Key1", data.getKey(0));
        assertEquals("Key3", data.getKey(1));
        
        // Remove last index where index < keys.size() condition is checked
        data.removeValue(1);
        assertEquals(1, data.getItemCount());
    }

    @Test
    public void testRemoveValueByKey() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("Key1", 1.0);
        data.addValue("Key2", 2.0);

        data.removeValue("Key1");
        assertEquals(1, data.getItemCount());
        assertEquals("Key2", data.getKey(0));

        // Remove non-existent key should do nothing
        data.removeValue("NonExistent");
        assertEquals(1, data.getItemCount());
    }

    @Test
    public void testClear() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("Key1", 1.0);
        data.addValue("Key2", 2.0);

        data.clear();
        assertEquals(0, data.getItemCount());
        assertTrue(data.getKeys().isEmpty());
    }

    @Test
    public void testSortByKeys() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("Z", 26.0);
        data.addValue("A", 1.0);
        data.addValue("M", 13.0);

        data.sortByKeys(SortOrder.ASCENDING);
        assertEquals("A", data.getKey(0));
        assertEquals("M", data.getKey(1));
        assertEquals("Z", data.getKey(2));

        data.sortByKeys(SortOrder.DESCENDING);
        assertEquals("Z", data.getKey(0));
        assertEquals("M", data.getKey(1));
        assertEquals("A", data.getKey(2));
    }

    @Test
    public void testSortByValues() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("Low", 1.0);
        data.addValue("High", 10.0);
        data.addValue("Null", (Number) null);
        data.addValue("Mid", 5.0);

        data.sortByValues(SortOrder.ASCENDING);
        assertEquals(Double.valueOf(1.0), data.getValue(0));
        assertEquals(Double.valueOf(5.0), data.getValue(1));
        assertEquals(Double.valueOf(10.0), data.getValue(2));
        assertNull(data.getValue(3)); // Null values sort to the end

        data.sortByValues(SortOrder.DESCENDING);
        assertEquals(Double.valueOf(10.0), data.getValue(0));
        assertEquals(Double.valueOf(5.0), data.getValue(1));
        assertEquals(Double.valueOf(1.0), data.getValue(2));
        assertNull(data.getValue(3));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        DefaultKeyedValues data1 = new DefaultKeyedValues();
        DefaultKeyedValues data2 = new DefaultKeyedValues();

        assertTrue(data1.equals(data1));
        assertFalse(data1.equals(null));
        assertFalse(data1.equals("SomeString"));

        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());

        data1.addValue("Key1", 1.0);
        assertFalse(data1.equals(data2));

        data2.addValue("Key1", 1.0);
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());

        // Different values count / structure
        data2.addValue("Key2", (Number) null);
        assertFalse(data1.equals(data2));

        // Same keys, different values
        DefaultKeyedValues data3 = new DefaultKeyedValues();
        data3.addValue("Key1", 2.0);
        assertFalse(data1.equals(data3));

        // Same keys, one null value vs non-null
        DefaultKeyedValues data4 = new DefaultKeyedValues();
        data4.addValue("Key1", (Number) null);
        DefaultKeyedValues data5 = new DefaultKeyedValues();
        data5.addValue("Key1", 1.0);
        assertFalse(data4.equals(data5));
        assertTrue(data4.equals(data4));
    }

    @Test
    public void testClone() throws Throwable {
        DefaultKeyedValues data = new DefaultKeyedValues();
        data.addValue("Key1", 1.0);

        DefaultKeyedValues clone = (DefaultKeyedValues) data.clone();
        assertNotNull(clone);
        assertTrue(data.equals(clone));
        assertNot(data, clone);
    }

    private void assertNot(Object o1, Object o2) {
        assertTrue(o1 != o2);
    }
}