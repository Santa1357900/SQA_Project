package org.apache.commons.csv;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Test;
import static org.junit.Assert.*;

public class CSVRecordClaudeTest {

    private enum Header {
        NAME, AGE
    }

    private Map<String, Integer> mapOf(String[] names) {
        Map<String, Integer> m = new HashMap<String, Integer>();
        for (int i = 0; i < names.length; i++) {
            m.put(names[i], Integer.valueOf(i));
        }
        return m;
    }

    // get(Enum) delegates to get(String) using enum's toString()
    @Test
    public void testGetEnum_validMapping_returnsValue() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John", "30"}, mapping, null, 1L);
        assertEquals("John", record.get(Header.NAME));
    }

    // get(int) valid first index
    @Test
    public void testGetInt_firstIndex_returnsFirstValue() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a", "b", "c"}, null, null, 1L);
        assertEquals("a", record.get(0));
    }

    // get(int) valid last index
    @Test
    public void testGetInt_lastIndex_returnsLastValue() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a", "b", "c"}, null, null, 1L);
        assertEquals("c", record.get(2));
    }

    // get(int) out of bounds throws ArrayIndexOutOfBoundsException
    @Test
    public void testGetInt_outOfBounds_throwsArrayIndexOutOfBounds() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, null, 1L);
        try {
            record.get(5);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    // get(String) no mapping -> IllegalStateException
    @Test
    public void testGetString_noMapping_throwsIllegalStateException() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, null, 1L);
        try {
            record.get("name");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // get(String) name not mapped -> IllegalArgumentException
    @Test
    public void testGetString_nameNotMapped_throwsIllegalArgumentException() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        try {
            record.get("AGE");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // get(String) mapped but index out of bounds (inconsistent record) -> IllegalArgumentException
    @Test
    public void testGetString_mappedButIndexOutOfBounds_throwsIllegalArgumentException() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        try {
            record.get("AGE");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // get(String) valid retrieval
    @Test
    public void testGetString_validMapping_returnsValue() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John", "30"}, mapping, null, 1L);
        assertEquals("30", record.get("AGE"));
    }

    // getComment returns null when no comment provided
    @Test
    public void testGetComment_noComment_returnsNull() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, null, 1L);
        assertNull(record.getComment());
    }

    // getComment returns provided comment
    @Test
    public void testGetComment_withComment_returnsComment() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, "a comment", 1L);
        assertEquals("a comment", record.getComment());
    }

    // getRecordNumber returns exact value, edge zero
    @Test
    public void testGetRecordNumber_zero_returnsZero() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, null, 0L);
        assertEquals(0L, record.getRecordNumber());
    }

    // getRecordNumber returns exact value, max long
    @Test
    public void testGetRecordNumber_maxValue_returnsMaxValue() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, null, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, record.getRecordNumber());
    }

    // isConsistent: mapping null -> true (no header, trivially consistent)
    @Test
    public void testIsConsistent_nullMapping_returnsTrue() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a", "b"}, null, null, 1L);
        assertTrue(record.isConsistent());
    }

    // isConsistent: mapping size equals values length -> true
    @Test
    public void testIsConsistent_matchingSizes_returnsTrue() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John", "30"}, mapping, null, 1L);
        assertTrue(record.isConsistent());
    }

    // isConsistent: mapping size differs from values length -> false
    @Test
    public void testIsConsistent_mismatchedSizes_returnsFalse() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        assertFalse(record.isConsistent());
    }

    // isMapped: mapping null -> false
    @Test
    public void testIsMapped_nullMapping_returnsFalse() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, null, 1L);
        assertFalse(record.isMapped("a"));
    }

    // isMapped: name present in mapping -> true
    @Test
    public void testIsMapped_namePresent_returnsTrue() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        assertTrue(record.isMapped("NAME"));
    }

    // isMapped: name absent from mapping -> false
    @Test
    public void testIsMapped_nameAbsent_returnsFalse() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        assertFalse(record.isMapped("AGE"));
    }

    // isSet: mapped and index within values bounds -> true
    @Test
    public void testIsSet_mappedWithinBounds_returnsTrue() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John", "30"}, mapping, null, 1L);
        assertTrue(record.isSet("AGE"));
    }

    // isSet: mapped but index beyond values length -> false
    @Test
    public void testIsSet_mappedOutOfBounds_returnsFalse() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        assertFalse(record.isSet("AGE"));
    }

    // isSet: name not mapped -> false
    @Test
    public void testIsSet_notMapped_returnsFalse() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        assertFalse(record.isSet("AGE"));
    }

    // iterator: empty values array -> zero iterations
    @Test
    public void testIterator_emptyValues_hasNoElements() throws Throwable {
        CSVRecord record = new CSVRecord(new String[0], null, null, 1L);
        Iterator<String> it = record.iterator();
        assertFalse(it.hasNext());
    }

    // iterator: multiple values returned in order
    @Test
    public void testIterator_multipleValues_returnsInOrder() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a", "b", "c"}, null, null, 1L);
        Iterator<String> it = record.iterator();
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
        assertFalse(it.hasNext());
    }

    // iterator: calling next() past end throws NoSuchElementException
    @Test
    public void testIterator_exhausted_throwsNoSuchElementException() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a"}, null, null, 1L);
        Iterator<String> it = record.iterator();
        it.next();
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // putIn: populates given map with all mapped values within bounds
    @Test
    public void testPutIn_mappingWithinBounds_populatesMap() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John", "30"}, mapping, null, 1L);
        Map<String, String> result = record.putIn(new HashMap<String, String>());
        assertEquals("John", result.get("NAME"));
        assertEquals("30", result.get("AGE"));
    }

    // putIn: excludes columns whose mapped index is beyond values length
    @Test
    public void testPutIn_columnBeyondValuesLength_excludedFromMap() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John"}, mapping, null, 1L);
        Map<String, String> result = record.putIn(new HashMap<String, String>());
        assertFalse(result.containsKey("AGE"));
        assertTrue(result.containsKey("NAME"));
    }

    // size: zero for empty values array
    @Test
    public void testSize_emptyArray_returnsZero() throws Throwable {
        CSVRecord record = new CSVRecord(new String[0], null, null, 1L);
        assertEquals(0, record.size());
    }

    // size: matches number of values
    @Test
    public void testSize_threeValues_returnsThree() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a", "b", "c"}, null, null, 1L);
        assertEquals(3, record.size());
    }

    // toMap: with valid mapping returns populated map
    @Test
    public void testToMap_withMapping_returnsPopulatedMap() throws Throwable {
        Map<String, Integer> mapping = mapOf(new String[] {"NAME", "AGE"});
        CSVRecord record = new CSVRecord(new String[] {"John", "30"}, mapping, null, 1L);
        Map<String, String> result = record.toMap();
        assertEquals("John", result.get("NAME"));
        assertEquals("30", result.get("AGE"));
        assertEquals(2, result.size());
    }

    // toMap: Javadoc states the map is empty if the record has no headers (mapping == null)
    @Test
    public void testToMap_nullMapping_returnsEmptyMap() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"a", "b"}, null, null, 1L);
        Map<String, String> result = record.toMap();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // toString: matches Arrays.toString representation of values
    @Test
    public void testToString_returnsArrayRepresentation() throws Throwable {
        CSVRecord record = new CSVRecord(new String[] {"x", "y"}, null, null, 1L);
        assertEquals("[x, y]", record.toString());
    }

    // toString: empty values array yields empty brackets
    @Test
    public void testToString_emptyValues_returnsEmptyBrackets() throws Throwable {
        CSVRecord record = new CSVRecord(new String[0], null, null, 1L);
        assertEquals("[]", record.toString());
    }

    // values(): returns the underlying values content
    @Test
    public void testValues_returnsUnderlyingArray() throws Throwable {
        String[] input = new String[] {"p", "q"};
        CSVRecord record = new CSVRecord(input, null, null, 1L);
        assertArrayEquals(input, record.values());
    }

    // constructor: null values array is substituted with empty array
    @Test
    public void testConstructor_nullValuesArray_resultsInEmptyRecord() throws Throwable {
        CSVRecord record = new CSVRecord(null, null, null, 1L);
        assertEquals(0, record.size());
    }
}
