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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Test;

public class CSVParserTest {

    @Test
    public void testParseStringFormat() throws Throwable {
        String csv = "a,b,c\n1,2,3";
        CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT);
        assertNotNull(parser);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertEquals("a", records.get(0).get(0));
        assertEquals("3", records.get(1).get(2));
        parser.close();
    }

    @Test
    public void testParseReaderFormat() throws Throwable {
        StringReader reader = new StringReader("x,y\n4,5");
        CSVParser parser = CSVParser.parse((java.io.Reader) reader, CSVFormat.DEFAULT);
        assertNotNull(parser);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("4", records.get(0).get(0));
        parser.close();
    }

    @Test
    public void testParseFileCharsetFormat() throws Throwable {
        File tempFile = File.createTempFile("csvParserTest", ".csv");
        tempFile.deleteOnExit();
        java.nio.charset.Charset charset = Charset.forName("UTF-8");
        Files.write(tempFile.toPath(), ArraysAsList("col1,col2", "val1,val2"), charset);

        CSVParser parser = CSVParser.parse(tempFile, charset, CSVFormat.DEFAULT);
        assertNotNull(parser);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("val1", records.get(0).get(0));
        parser.close();
    }

    @Test
    public void testParsePathCharsetFormat() throws Throwable {
        Path tempPath = Files.createTempFile("csvParserTestPath", ".csv");
        tempPath.toFile().deleteOnExit();
        Charset charset = Charset.forName("UTF-8");
        Files.write(tempPath, ArraysAsList("h1,h2", "d1,d2"), charset);

        CSVParser parser = CSVParser.parse(tempPath, charset, CSVFormat.DEFAULT);
        assertNotNull(parser);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("d1", records.get(0).get(0));
        parser.close();
    }

    @Test
    public void testParseInputStreamCharsetFormat() throws Throwable {
        File tempFile = File.createTempFile("csvParserTestStream", ".csv");
        tempFile.deleteOnExit();
        Charset charset = Charset.forName("UTF-8");
        Files.write(tempFile.toPath(), ArraysAsList("a,b", "1,2"), charset);

        java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
        CSVParser parser = CSVParser.parse((java.io.InputStream) fis, charset, CSVFormat.DEFAULT);
        assertNotNull(parser);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("1", records.get(0).get(0));
        parser.close();
    }

    @Test
    public void testParseUrlCharsetFormat() throws Throwable {
        File tempFile = File.createTempFile("csvParserTestUrl", ".csv");
        tempFile.deleteOnExit();
        Charset charset = Charset.forName("UTF-8");
        Files.write(tempFile.toPath(), ArraysAsList("a,b", "9,8"), charset);

        URL url = tempFile.toURI().toURL();
        CSVParser parser = CSVParser.parse(url, charset, CSVFormat.DEFAULT);
        assertNotNull(parser);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("9", records.get(0).get(0));
        parser.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullString() throws Throwable {
        CSVParser.parse((String) null, CSVFormat.DEFAULT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullFile() throws Throwable {
        CSVParser.parse((File) null, Charset.forName("UTF-8"), CSVFormat.DEFAULT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullPath() throws Throwable {
        CSVParser.parse((Path) null, Charset.forName("UTF-8"), CSVFormat.DEFAULT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullInputStream() throws Throwable {
        CSVParser.parse((java.io.InputStream) null, Charset.forName("UTF-8"), CSVFormat.DEFAULT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullUrl() throws Throwable {
        CSVParser.parse((URL) null, Charset.forName("UTF-8"), CSVFormat.DEFAULT);
    }

    @Test
    public void testHeaderMapAndGetters() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("A", "B", "C");
        CSVParser parser = CSVParser.parse("1,2,3\n4,5,6", format);
        
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(3, headerMap.size());
        assertEquals(Integer.valueOf(0), headerMap.get("A"));
        assertEquals(Integer.valueOf(1), headerMap.get("B"));
        assertEquals(Integer.valueOf(2), headerMap.get("C"));

        assertEquals(0L, parser.getRecordNumber());
        assertEquals(1L, parser.getCurrentLineNumber());

        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertEquals(2L, parser.getRecordNumber());

        parser.close();
        assertTrue(parser.isClosed());
    }

    @Test
    public void testHeaderCaseInsensitiveAndSkip() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("Col1", "Col2").withIgnoreHeaderCase().withSkipHeaderRecord(true);
        CSVParser parser = CSVParser.parse("Col1,Col2\nval1,val2", format);
        
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertTrue(headerMap.containsKey("col1"));
        
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("val1", records.get(0).get("COL1"));
        parser.close();
    }

    @Test
    public void testEmptyHeaderGeneration() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader();
        CSVParser parser = CSVParser.parse("H1,H2\nV1,V2", format);
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertNotNull(headerMap);
        assertEquals(2, headerMap.size());
        parser.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateHeaderException() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("A", "A");
        CSVParser.parse("1,2", format);
    }

    @Test
    public void testIteratorBehaviors() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\n1,2", CSVFormat.DEFAULT);
        Iterator<CSVRecord> iterator = parser.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext()); // repeat check

        CSVRecord record = iterator.next();
        assertNotNull(record);
        assertEquals("a", record.get(0));

        assertTrue(iterator.hasNext());
        CSVRecord record2 = iterator.next();
        assertNotNull(record2);

        assertFalse(iterator.hasNext());

        try {
            iterator.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }

        try {
            iterator.remove();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        parser.close();
    }

    @Test
    public void testIteratorClosedThrowsNoSuchElement() throws Throwable {
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
    public void testGetFirstEndOfLine() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\r\n1,2", CSVFormat.DEFAULT);
        parser.getRecords();
        String eol = parser.getFirstEndOfLine();
        // Depending on lexer internal implementation, can be checked or just invoked
        parser.close();
    }

    @Test
    public void testCommentsAndTrailingDelimiter() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentMarker('#').withTrailingDelimiter(true);
        String csv = "# This is a comment\na,b,\n";
        CSVParser parser = CSVParser.parse(csv, format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertEquals("a", records.get(0).get(0));
        assertNotNull(records.get(0).getComment());
        parser.close();
    }

    @Test
    public void testNullStringHandling() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVParser parser = CSVParser.parse("a,NULL,c", format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertNull(records.get(0).get(1));
        parser.close();
    }

    @Test(expected = IOException.class)
    public void testInvalidParseSequence() throws Throwable {
        // Constructing a scenario that might trigger invalid token or forcing lexer error if possible
        // Using a format with unexpected quote configuration or directly passing invalid input
        CSVFormat format = CSVFormat.DEFAULT.withQuote('\'');
        CSVParser parser = CSVParser.parse("'unclosed quote", format);
        parser.getRecords();
        parser.close();
    }

    private static List<String> ArraysAsList(String... elements) {
        return java.util.Arrays.asList(elements);
    }
}