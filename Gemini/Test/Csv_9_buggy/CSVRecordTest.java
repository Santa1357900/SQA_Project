package org.apache.commons.csv;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CSVRecordTest {

    private enum TestEnum {
        A, B
    }

    @Test
    public void testGetByIndex() throws Throwable {
        String[] values = new String[] { "val1", "val2", "val3" };
        CSVRecord record = new CSVRecord(values, null, "comment", 1L);

        assertEquals("val1", record.get(0));
        assertEquals("val2", record.get(1));
        assertEquals("val3", record.get(2));
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetByIndexOutOfBounds() throws Throwable {
        String[] values = new String[] { "val1" };
        CSVRecord record = new CSVRecord(values, null, null, 1L);
        record.get(5);
    }

    @Test
    public void testGetByNameWithoutMapping() throws Throwable {
        String[] values = new String[] { "val1" };
        CSVRecord record = new CSVRecord(values, null, null, 1L);
        try {
            record.get("A");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No header mapping was specified"));
        }
    }

    @Test
    public void testGetByNameMappingNotFound() throws Throwable {
        String[] values = new String[] { "val1" };
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("B", Integer.valueOf(0));

        CSVRecord record = new CSVRecord(values, mapping, null, 1L);
        try {
            record.get("A");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Mapping for A not found"));
        }
    }

    @Test
    public void testGetByNameIndexOutOfBounds() throws Throwable {
        String[] values = new String[] { "val1" };
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(5));

        CSVRecord record = new CSVRecord(values, mapping, null, 1L);
        try {
            record.get("A");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Index for header 'A' is 5"));
        }
    }

    @Test
    public void testGetByNameSuccess() throws Throwable {
        String[] values = new String[] { "val1", "val2" };
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));
        mapping.put("B", Integer.valueOf(1));

        CSVRecord record = new CSVRecord(values, mapping, null, 1L);
        assertEquals("val1", record.get("A"));
        assertEquals("val2", record.get("B"));
    }

    @Test
    public void testGetByEnum() throws Throwable {
        String[] values = new String[] { "valA", "valB" };
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));
        mapping.put("B", Integer.valueOf(1));

        CSVRecord record = new CSVRecord(values, mapping, null, 1L);
        assertEquals("valA", record.get(TestEnum.A));
        assertEquals("valB", record.get(TestEnum.B));
    }

    @Test
    public void testGetComment() throws Throwable {
        CSVRecord record1 = new CSVRecord(null, null, "my comment", 10L);
        assertEquals("my comment", record1.getComment());

        CSVRecord record2 = new CSVRecord(null, null, null, 10L);
        assertNull(record2.getComment());
    }

    @Test
    public void testGetRecordNumber() throws Throwable {
        CSVRecord record = new CSVRecord(null, null, null, 42L);
        assertEquals(42L, record.getRecordNumber());
    }

    @Test
    public void testIsConsistent() throws Throwable {
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));
        mapping.put("B", Integer.valueOf(1));

        // mapping == null -> true
        CSVRecord recordNullMap = new CSVRecord(new String[] { "1" }, null, null, 1L);
        assertTrue(recordNullMap.isConsistent());

        // mapping.size() == values.length -> true
        CSVRecord recordConsistent = new CSVRecord(new String[] { "1", "2" }, mapping, null, 1L);
        assertTrue(recordConsistent.isConsistent());

        // mapping.size() != values.length -> false
        CSVRecord recordInconsistent = new CSVRecord(new String[] { "1" }, mapping, null, 1L);
        assertFalse(recordInconsistent.isConsistent());
    }

    @Test
    public void testIsMapped() throws Throwable {
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));

        CSVRecord record = new CSVRecord(new String[] { "1" }, mapping, null, 1L);
        assertFalse(record.isMapped(null));
        assertFalse(record.isMapped("B"));
        assertTrue(record.isMapped("A"));

        CSVRecord recordNullMap = new CSVRecord(new String[] { "1" }, null, null, 1L);
        assertFalse(recordNullMap.isMapped("A"));
    }

    @Test
    public void testIsSet() throws Throwable {
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));
        mapping.put("B", Integer.valueOf(5));

        CSVRecord record = new CSVRecord(new String[] { "1" }, mapping, null, 1L);
        assertTrue(record.isSet("A"));
        assertFalse(record.isSet("B")); // mapped but index >= values.length
        assertFalse(record.isSet("C")); // not mapped

        CSVRecord recordNullMap = new CSVRecord(new String[] { "1" }, null, null, 1L);
        assertFalse(recordNullMap.isSet("A"));
    }

    @Test
    public void testIterator() throws Throwable {
        String[] values = new String[] { "a", "b", "c" };
        CSVRecord record = new CSVRecord(values, null, null, 1L);
        Iterator<String> it = record.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testPutInAndToMap() throws Throwable {
        Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("A", Integer.valueOf(0));
        mapping.put("B", Integer.valueOf(1));
        mapping.put("C", Integer.valueOf(5)); // out of bounds, should be skipped

        String[] values = new String[] { "valA", "valB" };
        CSVRecord record = new CSVRecord(values, mapping, null, 1L);

        Map<String, String> targetMap = new HashMap<String, String>();
        Map<String, String> result = record.putIn(targetMap);
        assertEquals(targetMap, result);
        assertEquals("valA", result.get("A"));
        assertEquals("valB", result.get("B"));
        assertFalse(result.containsKey("C"));

        Map<String, String> mapFromToMap = record.toMap();
        assertNotNull(mapFromToMap);
        assertEquals("valA", mapFromToMap.get("A"));
        assertEquals("valB", mapFromToMap.get("B"));
    }

    @Test
    public void testSize() throws Throwable {
        CSVRecord record1 = new CSVRecord(null, null, null, 1L);
        assertEquals(0, record1.size());

        CSVRecord record2 = new CSVRecord(new String[] { "1", "2" }, null, null, 1L);
        assertEquals(2, record2.size());
    }

    @Test
    public void testToString() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] { "x", "y" }, null, null, 1L);
        assertEquals(Arrays.toString(new String[] { "x", "y" }), record.toString());
    }

    @Test
    public void testValuesInternal() throws Throwable {
        String[] values = new String[] { "1" };
        CSVRecord record = new CSVRecord(values, null, null, 1L);
        assertEquals(values, record.values());
    }
}