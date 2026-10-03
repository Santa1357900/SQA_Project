package org.apache.commons.csv;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CSVParserTest {

    @Test
    public void testParseStringFormat() throws Throwable {
        String csv = "a,b,c\n1,2,3";
        CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT);
        assertNotNull(parser);
        assertFalse(parser.isClosed());

        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertEquals("a", records.get(0).get(0));
        assertEquals("3", records.get(1).get(2));
        parser.close();
        assertTrue(parser.isClosed());
    }

    @Test
    public void testParseFileNull() throws Throwable {
        File file = null;
        try {
            CSVParser.parse(file, CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testParseStringNull() throws Throwable {
        String str = null;
        try {
            CSVParser.parse(str, CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testParseUrlNull() throws Throwable {
        URL url = null;
        try {
            CSVParser.parse(url, Charset.defaultCharset(), CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testConstructorNullReader() throws Throwable {
        try {
            new CSVParser(null, CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testConstructorNullFormat() throws Throwable {
        try {
            new CSVParser(new StringReader("a"), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testHeaderMap() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("col1", "col2", "col3");
        CSVParser parser = CSVParser.parse("a,b,c\n1,2,3", format);
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(3, headerMap.size());
        assertEquals(Integer.valueOf(0), headerMap.get("col1"));
        assertEquals(Integer.valueOf(1), headerMap.get("col2"));
        assertEquals(Integer.valueOf(2), headerMap.get("col3"));
        parser.close();
    }

    @Test
    public void testHeaderMapAuto() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader();
        CSVParser parser = CSVParser.parse("col1,col2,col3\n1,2,3", format);
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(3, headerMap.size());
        assertEquals(Integer.valueOf(0), headerMap.get("col1"));
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("1", records.get(0).get("col1"));
        parser.close();
    }

    @Test
    public void testHeaderMapSkipHeaderRecord() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("col1", "col2").withSkipHeaderRecord(true);
        CSVParser parser = CSVParser.parse("col1,col2\n1,2", format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("1", records.get(0).get("col1"));
        parser.close();
    }

    @Test
    public void testHeaderMapNull() throws Throwable {
        CSVParser parser = CSVParser.parse("1,2,3", CSVFormat.DEFAULT);
        assertNull(parser.getHeaderMap());
        parser.close();
    }

    @Test
    public void testIterator() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\nc,d", CSVFormat.DEFAULT);
        Iterator<CSVRecord> iterator = parser.iterator();
        assertTrue(iterator.hasNext());
        CSVRecord record1 = iterator.next();
        assertEquals("a", record1.get(0));

        assertTrue(iterator.hasNext());
        CSVRecord record2 = iterator.next();
        assertEquals("c", record2.get(0));

        assertFalse(iterator.hasNext());
        
        try {
            iterator.remove();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
        parser.close();
    }

    @Test
    public void testIteratorNoSuchElement() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b", CSVFormat.DEFAULT);
        Iterator<CSVRecord> iterator = parser.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertFalse(iterator.hasNext());

        try {
            iterator.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }
        parser.close();
    }

    @Test
    public void testIteratorAfterClose() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b", CSVFormat.DEFAULT);
        parser.close();
        Iterator<CSVRecord> iterator = parser.iterator();
        assertFalse(iterator.hasNext());

        try {
            iterator.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }
    }

    @Test
    public void testGetRecordNumberAndLineNumber() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\nc,d", CSVFormat.DEFAULT);
        assertEquals(0, parser.getRecordNumber());
        
        CSVRecord rec1 = parser.nextRecord();
        assertNotNull(rec1);
        assertEquals(1, parser.getRecordNumber());
        assertEquals(1, parser.getCurrentLineNumber());

        CSVRecord rec2 = parser.nextRecord();
        assertNotNull(rec2);
        assertEquals(2, parser.getRecordNumber());
        
        parser.close();
    }

    @Test
    public void testComments() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        CSVParser parser = CSVParser.parse("#comment\na,b", format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("a", records.get(0).get(0));
        assertEquals("comment", records.get(0.0 > 1.0 ? 0 : 0).getComment());
        parser.close();
    }

    @Test
    public void testNullString() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVParser parser = CSVParser.parse("a,NULL,b", format);
        CSVRecord record = parser.nextRecord();
        assertNotNull(record);
        assertEquals("a", record.get(0));
        assertNull(record.get(1));
        assertEquals("b", record.get(2));
        parser.close();
    }

    @Test
    public void testGetRecordsIntoCollection() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\nc,d", CSVFormat.DEFAULT);
        List<CSVRecord> target = new ArrayList<CSVRecord>();
        List<CSVRecord> result = parser.getRecords(target);
        assertEquals(2, result.size());
        parser.close();
    }
}