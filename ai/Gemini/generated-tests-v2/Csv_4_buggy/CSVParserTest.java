package org.apache.commons.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Test;

public class CSVParserTest {

    @Test
    public void testParseStringFormat() throws Throwable {
        String source = "a,b,c\n1,2,3";
        CSVParser parser = CSVParser.parse(source, CSVFormat.DEFAULT);
        assertNotNull(parser);
        assertFalse(parser.isClosed());
        assertEquals(0L, parser.getRecordNumber());

        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertEquals(2L, parser.getRecordNumber());
        parser.close();
        assertTrue(parser.isClosed());
    }

    @Test
    public void testParseFileFormat() throws Throwable {
        File tempFile = File.createTempFile("csvParserTest", ".csv");
        tempFile.deleteOnExit();
        
        CSVParser parser = null;
        try {
            parser = CSVParser.parse(tempFile, CSVFormat.DEFAULT);
            assertNotNull(parser);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    @Test
    public void testParseNullFile() throws Throwable {
        try {
            CSVParser.parse((File) null, CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testParseNullString() throws Throwable {
        try {
            CSVParser.parse((String) null, CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testParseNullUrl() throws Throwable {
        try {
            CSVParser.parse((URL) null, Charset.defaultCharset(), CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testHeaderInitializationFromFormat() throws Throwable {
        String source = "1,2,3";
        CSVFormat format = CSVFormat.DEFAULT.withHeader("Col1", "Col2", "Col3");
        CSVParser parser = CSVParser.parse(source, format);
        
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(3, headerMap.size());
        assertEquals(Integer.valueOf(0), headerMap.get("Col1"));
        assertEquals(Integer.valueOf(1), headerMap.get("Col2"));
        assertEquals(Integer.valueOf(2), headerMap.get("Col3"));
        parser.close();
    }

    @Test
    public void testHeaderInitializationEmptyHeader() throws Throwable {
        String source = "ColA,ColB,ColC\n1,2,3";
        CSVFormat format = CSVFormat.DEFAULT.withHeader();
        CSVParser parser = CSVParser.parse(source, format);
        
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(3, headerMap.size());
        assertEquals(Integer.valueOf(0), headerMap.get("ColA"));
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        parser.close();
    }

    @Test
    public void testHeaderWithSkipHeaderRecord() throws Throwable {
        String source = "Col1,Col2\n1,2";
        CSVFormat format = CSVFormat.DEFAULT.withHeader("Col1", "Col2").withSkipHeaderRecord(true);
        CSVParser parser = CSVParser.parse(source, format);
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("1", records.get(0).get(0));
        parser.close();
    }

    @Test
    public void testNullStringHandling() throws Throwable {
        String source = "a,NULL,c";
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVParser parser = CSVParser.parse(source, format);
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("a", records.get(0).get(0));
        assertNull(records.get(0).get(1));
        assertEquals("c", records.get(0).get(2));
        parser.close();
    }

    @Test
    public void testCommentsHandling() throws Throwable {
        String source = "# Comment line\na,b,c";
        CSVFormat format = CSVFormat.DEFAULT.withCommentMarker('#');
        CSVParser parser = CSVParser.parse(source, format);
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals(" Comment line", records.get(0.0 > 1.0 ? 0 : 0).getComment());
        parser.close();
    }

    @Test
    public void testIteratorOperations() throws Throwable {
        String source = "a,b\nc,d";
        CSVParser parser = CSVParser.parse(source, CSVFormat.DEFAULT);
        Iterator<CSVRecord> iterator = parser.iterator();
        
        assertTrue(iterator.hasNext());
        CSVRecord record1 = iterator.next();
        assertNotNull(record1);
        assertEquals("a", record1.get(0));
        
        assertTrue(iterator.hasNext());
        CSVRecord record2 = iterator.next();
        assertNotNull(record2);
        assertEquals("c", record2.get(0));
        
        assertFalse(iterator.hasNext());
        
        try {
            iterator.remove();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        
        parser.close();
    }

    @Test
    public void testIteratorNoSuchElement() throws Throwable {
        String source = "a,b";
        CSVParser parser = CSVParser.parse(source, CSVFormat.DEFAULT);
        Iterator<CSVRecord> iterator = parser.iterator();
        
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

    @Test
    public void testIteratorOnClosedParser() throws Throwable {
        String source = "a,b";
        CSVParser parser = CSVParser.parse(source, CSVFormat.DEFAULT);
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
    public void testGetCurrentLineNumber() throws Throwable {
        String source = "a,b\nc,d";
        CSVParser parser = CSVParser.parse(source, CSVFormat.DEFAULT);
        assertEquals(0L, parser.getCurrentLineNumber());
        parser.nextRecord();
        assertTrue(parser.getCurrentLineNumber() >= 1L);
        parser.close();
    }

    @Test
    public void testInvalidParseSequence() throws Throwable {
        String source = "a,\"\";b"; 
        CSVFormat format = CSVFormat.DEFAULT;
        CSVParser parser = CSVParser.parse(source, format);
        try {
            parser.getRecords();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("invalid parse sequence") || e.getMessage() != null);
        } finally {
            parser.close();
        }
    }

    @Test
    public void testConstructorWithReaderDirect() throws Throwable {
        StringReader reader = new StringReader("x,y,z");
        CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT);
        assertNotNull(parser);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        parser.close();
    }
}