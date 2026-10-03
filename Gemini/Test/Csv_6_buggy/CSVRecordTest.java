package org.apache.commons.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;

public class CSVRecordTest {

    private enum TestEnum {
        FIRST, SECOND
    }

    @Test
    public void testGetByIndex() throws Throwable {
        final String[] values = new String[] { "A", "B", "C" };
        final CSVRecord record = new CSVRecord(values, null, "comment", 1L);

        assertEquals("A", record.get(0));
        assertEquals("B", record.get(1));
        assertEquals("C", record.get(2));
        assertEquals(3, record.size());
        assertEquals("comment", record.getComment());
        assertEquals(1L, record.getRecordNumber());
        assertEquals("[A, B, C]", record.toString());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetByIndexOutOfBounds() throws Throwable {
        final String[] values = new String[] { "A" };
        final CSVRecord record = new CSVRecord(values, null, null, 0L);
        record.get(5);
    }

    @Test
    public void testNullValuesArray() throws Throwable {
        final CSVRecord record = new CSVRecord(null, null, null, 10L);
        assertEquals(0, record.size());
        assertNotNull(record.values());
        assertEquals(0, record.values().length);
    }

    @Test
    public void testGetByNameWithoutMapping() throws Throwable {
        final String[] values = new String[] { "A" };
        final CSVRecord record = new CSVRecord(values, null, null, 1L);

        try {
            record.get("Col1");
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("No header mapping was specified"));
        }
    }

    @Test
    public void testGetByNameMappingNotFound() throws Throwable {
        final String[] values = new String[] { "A" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("Col2", Integer.valueOf(0));

        final CSVRecord record = new CSVRecord(values, mapping, null, 1L);

        try {
            record.get("Col1");
            fail("Expected IllegalArgumentException");
        } catch (final IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Mapping for Col1 not found"));
        }
    }

    @Test
    public void testGetByNameIndexOutOfBounds() throws Throwable {
        final String[] values = new String[] { "A" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("Col1", Integer.valueOf(5));

        final CSVRecord record = new CSVRecord(values, mapping, null, 1L);

        try {
            record.get("Col1");
            fail("Expected IllegalArgumentException");
        } catch (final IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Index for header 'Col1' is 5"));
        }
    }

    @Test
    public void testGetByNameSuccess() throws Throwable {
        final String[] values = new String[] { "ValA", "ValB" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));
        mapping.put("B", Integer.valueOf(1));

        final CSVRecord record = new CSVRecord(values, mapping, "my comment", 42L);

        assertEquals("ValA", record.get("A"));
        assertEquals("ValB", record.get("B"));
        assertTrue(record.isMapped("A"));
        assertFalse(record.isMapped("C"));
        assertTrue(record.isSet("A"));
    }

    @Test
    public void testIsSetEdgeCases() throws Throwable {
        final String[] values = new String[] { "ValA" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));
        mapping.put("B", Integer.valueOf(1)); // Mapped but index 1 >= values.length (1)

        final CSVRecord record = new CSVRecord(values, mapping, null, 1L);

        assertTrue(record.isMapped("B"));
        assertFalse(record.isSet("B"));
        assertFalse(record.isMapped("C"));
        assertFalse(record.isSet("C"));
    }

    @Test
    public void testIsConsistent() throws Throwable {
        final String[] values = new String[] { "A", "B" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("Col1", Integer.valueOf(0));
        mapping.put("Col2", Integer.valueOf(1));

        final CSVRecord record1 = new CSVRecord(values, null, null, 1L);
        assertTrue(record1.isConsistent());

        final CSVRecord record2 = new CSVRecord(values, mapping, null, 2L);
        assertTrue(record2.isConsistent());

        final Map<String, Integer> inconsistentMapping = new HashMap<String, Integer>();
        inconsistentMapping.put("Col1", Integer.valueOf(0));
        final CSVRecord record3 = new CSVRecord(values, inconsistentMapping, null, 3L);
        assertFalse(record3.isConsistent());
    }

    @Test
    public void testGetByEnum() throws Throwable {
        final String[] values = new String[] { "FirstVal", "SecondVal" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("FIRST", Integer.valueOf(0));
        mapping.put("SECOND", Integer.valueOf(1));

        final CSVRecord record = new CSVRecord(values, mapping, null, 1L);

        assertEquals("FirstVal", record.get(TestEnum.FIRST));
        assertEquals("SecondVal", record.get(TestEnum.SECOND));
    }

    @Test
    public void testIteratorAndToListAndToMap() throws Throwable {
        final String[] values = new String[] { "X", "Y" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("C1", Integer.valueOf(0));
        mapping.put("C2", Integer.valueOf(1));

        final CSVRecord record = new CSVRecord(values, mapping, null, 1L);

        final Iterator<String> iterator = record.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals("X", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("Y", iterator.next());
        assertFalse(iterator.hasNext());

        final Map<String, String> map = record.toMap();
        assertNotNull(map);
        assertEquals(2, map.size());
        assertEquals("X", map.get("C1"));
        assertEquals("Y", map.get("C2"));
    }

    @Test
    public void testToMapWithoutMapping() throws Throwable {
        final String[] values = new String[] { "X" };
        final CSVRecord record = new CSVRecord(values, null, null, 1L);
        try {
            record.toMap();
            fail("Expected NullPointerException or IllegalStateException when mapping is null in toMap/putIn");
        } catch (final NullPointerException e) {
            // Expected because mapping.entrySet() throws NPE if mapping is null
        }
    }
}