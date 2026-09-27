package org.apache.commons.csv;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.Assert.*;

public class CSVParserTest {

    @Test
    public void testParseStringWithDefaultFormat() throws Throwable {
        String csv = "a,b,c\n1,2,3";
        CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT);
        assertNotNull(parser);
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertEquals("a", records.get(0).get(0));
        assertEquals("3", records.get(1).get(2));
        parser.close();
        assertTrue(parser.isClosed());
    }

    @Test
    public void testParseNullStringThrowsException() throws Throwable {
        try {
            CSVParser.parse((String) null, CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testParseNullFormatThrowsException() throws Throwable {
        try {
            CSVParser.parse("a,b,c", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testParseNullFileThrowsException() throws Throwable {
        try {
            CSVParser.parse((File) null, Charset.defaultCharset(), CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testParseNullUrlThrowsException() throws Throwable {
        try {
            CSVParser.parse((URL) null, Charset.defaultCharset(), CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testHeaderMapAndGetHeaderMap() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("Col1", "Col2", "Col3");
        CSVParser parser = CSVParser.parse("a,b,c\n1,2,3", format);
        
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(3, headerMap.size());
        assertEquals(Integer.valueOf(0), headerMap.get("Col1"));
        assertEquals(Integer.valueOf(1), headerMap.get("Col2"));
        assertEquals(Integer.valueOf(2), headerMap.get("Col3"));
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("1", records.get(0).get("Col1"));
        parser.close();
    }

    @Test
    public void testHeaderFromFirstLine() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader();
        CSVParser parser = CSVParser.parse("H1,H2,H3\n1,2,3", format);
        
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(2, parser.getRecords().size());
        parser.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateHeaderThrowsException() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("Col1", "Col1");
        CSVParser.parse("1,2", format);
    }

    @Test
    public void testIteratorAndNoSuchElement() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\n1,2", CSVFormat.DEFAULT);
        Iterator<CSVRecord> iterator = parser.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertFalse(iterator.hasNext());
        
        try {
            iterator.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // Expected
        }
        parser.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIteratorRemoveUnsupported() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b", CSVFormat.DEFAULT);
        Iterator<CSVRecord> iterator = parser.iterator();
        try {
            iterator.remove();
        } finally {
            parser.close();
        }
    }

    @Test
    public void testIteratorOnClosedParser() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b", CSVFormat.DEFAULT);
        parser.close();
        Iterator<CSVRecord> iterator = parser.iterator();
        assertFalse(iterator.hasNext());
        try {
            iterator.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // Expected
        }
    }

    @Test
    public void testGetLineNumberAndRecordNumber() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\nc,d\ne,f", CSVFormat.DEFAULT);
        assertEquals(0, parser.getRecordNumber());
        
        CSVRecord rec1 = parser.iterator().next();
        assertEquals(1, parser.getRecordNumber());
        assertTrue(parser.getCurrentLineNumber() >= 1);
        parser.close();
    }

    @Test
    public void testNullStringHandling() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVParser parser = CSVParser.parse("a,NULL,c", format);
        CSVRecord record = parser.iterator().next();
        assertEquals("a", record.get(0));
        assertNull(record.get(1));
        assertEquals("c", record.get(2));
        parser.close();
    }

    @Test
    public void testCommentsHandling() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentMarker('#');
        CSVParser parser = CSVParser.parse("# Comment 1\na,b,c\n# Comment 2\n1,2,3", format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertEquals("Comment 1\nComment 2", records.get(1).getComment());
        parser.close();
    }

    @Test
    public void testCustomCollectionGetRecords() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\n1,2", CSVFormat.DEFAULT);
        ArrayList<CSVRecord> list = new ArrayList<CSVRecord>();
        parser.getRecords(list);
        assertEquals(2, list.size());
        parser.close();
    }
}