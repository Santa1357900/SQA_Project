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

    @Test
    public void testGetByIndex() throws Throwable {
        final String[] values = new String[] { "A", "B", "C" };
        final CSVRecord record = new CSVRecord(values, null, "comment", 1L);

        assertEquals("A", record.get(0));
        assertEquals("B", record.get(1));
        assertEquals("C", record.get(2));
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetByIndexOutOfBounds() throws Throwable {
        final String[] values = new String[] { "A" };
        final CSVRecord record = new CSVRecord(values, null, null, 1L);
        record.get(5);
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
    public void testGetByNameWithMapping() throws Throwable {
        final String[] values = new String[] { "Val1", "Val2" };
        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("First", Integer.valueOf(0));
        mapping.put("Second", Integer.valueOf(1));
        mapping.put("Missing", Integer.valueOf(5));

        final CSVRecord record = new CSVRecord(values, mapping, "my comment", 42L);

        assertEquals("Val1", record.get("First"));
        assertEquals("Val2", record.get("Second"));
        assertNull(record.get("Missing"));
        assertNull(record.get("NonExistent"));
    }

    @Test
    public void testIsConsistent() throws Throwable {
        final String[] values = new String[] { "A", "B" };
        
        // mapping == null -> true
        final CSVRecord record1 = new CSVRecord(values, null, null, 1L);
        assertTrue(record1.isConsistent());

        // mapping.size() == values.length -> true
        final Map<String, Integer> mappingEqual = new HashMap<String, Integer>();
        mappingEqual.put("Col1", Integer.valueOf(0));
        mappingEqual.put("Col2", Integer.valueOf(1));
        final CSVRecord record2 = new CSVRecord(values, mappingEqual, null, 1L);
        assertTrue(record2.isConsistent());

        // mapping.size() != values.length -> false
        final Map<String, Integer> mappingDiff = new HashMap<String, Integer>();
        mappingDiff.put("Col1", Integer.valueOf(0));
        final CSVRecord record3 = new CSVRecord(values, mappingDiff, null, 1L);
        assertFalse(record3.isConsistent());
    }

    @Test
    public void testIsMapped() throws Throwable {
        final String[] values = new String[] { "A" };
        
        // mapping == null -> false
        final CSVRecord record1 = new CSVRecord(values, null, null, 1L);
        assertFalse(record1.isMapped("Col1"));

        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("Col1", Integer.valueOf(0));
        final CSVRecord record2 = new CSVRecord(values, mapping, null, 1L);
        
        assertTrue(record2.isMapped("Col1"));
        assertFalse(record2.isMapped("Col2"));
    }

    @Test
    public void testIsSet() throws Throwable {
        final String[] values = new String[] { "A" };
        
        // mapping == null -> false
        final CSVRecord record1 = new CSVRecord(values, null, null, 1L);
        assertFalse(record1.isSet("Col1"));

        final Map<String, Integer> mapping = new HashMap<String, Integer>();
        mapping.put("Col1", Integer.valueOf(0));
        mapping.put("ColOutOfBounds", Integer.valueOf(5));
        final CSVRecord record2 = new CSVRecord(values, mapping, null, 1L);

        assertTrue(record2.isSet("Col1"));
        assertFalse(record2.isSet("ColOutOfBounds"));
        assertFalse(record2.isSet("NonExistent"));
    }

    @Test
    public void testIterator() throws Throwable {
        final String[] values = new String[] { "X", "Y", "Z" };
        final CSVRecord record = new CSVRecord(values, null, null, 1L);

        final Iterator<String> iterator = record.iterator();
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        assertEquals("X", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("Y", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("Z", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testNullValuesHandling() throws Throwable {
        final CSVRecord record = new CSVRecord(null, null, null, 0L);
        assertEquals(0, record.size());
        assertNotNull(record.values());
        assertEquals(0, record.values().length);
    }

    @Test
    public void testGetCommentAndRecordNumberAndSize() throws Throwable {
        final String[] values = new String[] { "a", "b" };
        final CSVRecord record = new CSVRecord(values, null, "A comment string", 123L);

        assertEquals("A comment string", record.getComment());
        assertEquals(123L, record.getRecordNumber());
        assertEquals(2, record.size());
        assertEquals("[a, b]", record.toString());
    }
}