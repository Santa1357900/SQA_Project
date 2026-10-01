package org.apache.commons.csv;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Test;
import static org.junit.Assert.*;

public class CSVParserClaudeTest {

    // parse(File,...): file null -> Assertions.notNull throws before any IO
    @Test
    public void testParseFile_nullFile_throwsIllegalArgumentException() throws Throwable {
        try {
            CSVParser.parse((File) null, CSVFormat.DEFAULT);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parse(File,...): format null -> throws before FileReader is opened (no real IO)
    @Test
    public void testParseFile_nullFormat_throwsIllegalArgumentException() throws Throwable {
        try {
            CSVParser.parse(new File("dummy.csv"), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parse(String,...): string null branch
    @Test
    public void testParseString_nullString_throwsIllegalArgumentException() throws Throwable {
        try {
            CSVParser.parse((String) null, CSVFormat.DEFAULT);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parse(String,...): format null branch
    @Test
    public void testParseString_nullFormat_throwsIllegalArgumentException() throws Throwable {
        try {
            CSVParser.parse("a,b\n", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parse(String,...): happy path returns usable parser
    @Test
    public void testParseString_validInput_returnsParserWithRecords() throws Throwable {
        CSVParser parser = CSVParser.parse("a,b\nc,d\n", CSVFormat.DEFAULT);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
    }

    // parse(URL,...): url null branch (checked before any network access)
    @Test
    public void testParseUrl_nullUrl_throwsIllegalArgumentException() throws Throwable {
        try {
            CSVParser.parse((URL) null, Charset.forName("UTF-8"), CSVFormat.DEFAULT);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parse(URL,...): charset null branch (checked before openStream())
    @Test
    public void testParseUrl_nullCharset_throwsIllegalArgumentException() throws Throwable {
        URL url = new URL("http://example.com/data.csv");
        try {
            CSVParser.parse(url, null, CSVFormat.DEFAULT);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parse(URL,...): format null branch (checked before openStream())
    @Test
    public void testParseUrl_nullFormat_throwsIllegalArgumentException() throws Throwable {
        URL url = new URL("http://example.com/data.csv");
        try {
            CSVParser.parse(url, Charset.forName("UTF-8"), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // constructor: reader null branch
    @Test
    public void testConstructor_nullReader_throwsIllegalArgumentException() throws Throwable {
        try {
            new CSVParser((Reader) null, CSVFormat.DEFAULT);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // constructor: format null branch
    @Test
    public void testConstructor_nullFormat_throwsIllegalArgumentException() throws Throwable {
        try {
            new CSVParser(new StringReader("a,b\n"), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getHeaderMap(): when no header is configured, headerMap field is null; contract says
    // getHeaderMap() must still return gracefully (null), not blow up wrapping a null map.
    @Test
    public void testGetHeaderMap_noHeaderDefined_returnsNull() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b,c\n"), CSVFormat.DEFAULT);
        assertNull(parser.getHeaderMap());
    }

    // getHeaderMap(): explicit header names branch -> correct name/index mapping
    @Test
    public void testGetHeaderMap_withHeaderFromFormat_returnsCorrectMapping() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("X", "Y", "Z");
        CSVParser parser = new CSVParser(new StringReader("1,2,3\n"), format);
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertEquals(Integer.valueOf(0), headerMap.get("X"));
        assertEquals(Integer.valueOf(1), headerMap.get("Y"));
        assertEquals(Integer.valueOf(2), headerMap.get("Z"));
    }

    // getHeaderMap(): formatHeader.length==0 branch -> header read from first input line
    @Test
    public void testGetHeaderMap_readHeaderFromFirstLine_returnsCorrectMapping() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader();
        CSVParser parser = new CSVParser(new StringReader("name,age\nJohn,25\n"), format);
        Map<String, Integer> headerMap = parser.getHeaderMap();
        assertEquals(Integer.valueOf(0), headerMap.get("name"));
        assertEquals(Integer.valueOf(1), headerMap.get("age"));
    }

    // getHeaderMap(): returned map is a copy, mutating it must not affect the parser
    @Test
    public void testGetHeaderMap_isCopy_mutatingReturnedMapDoesNotAffectParser() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("A", "B");
        CSVParser parser = new CSVParser(new StringReader("1,2\n"), format);
        Map<String, Integer> map1 = parser.getHeaderMap();
        map1.put("C", Integer.valueOf(99));
        Map<String, Integer> map2 = parser.getHeaderMap();
        assertFalse(map2.containsKey("C"));
    }

    // getRecordNumber(): initial value before any record is read
    @Test
    public void testGetRecordNumber_beforeParsing_returnsZero() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b\n"), CSVFormat.DEFAULT);
        assertEquals(0L, parser.getRecordNumber());
    }

    // getRecordNumber(): increments once per parsed record
    @Test
    public void testGetRecordNumber_afterParsingRecords_returnsCorrectCount() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a\nb\nc\n"), CSVFormat.DEFAULT);
        parser.getRecords();
        assertEquals(3L, parser.getRecordNumber());
    }

    // getCurrentLineNumber(): progresses as more lines are consumed
    @Test
    public void testGetCurrentLineNumber_afterReadingLines_increases() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b\nc,d\ne,f\n"), CSVFormat.DEFAULT);
        long before = parser.getCurrentLineNumber();
        parser.nextRecord();
        parser.nextRecord();
        long after = parser.getCurrentLineNumber();
        assertTrue(after > before);
    }

    // getRecords(): empty input -> empty list (EOF branch, record stays empty)
    @Test
    public void testGetRecords_emptyInput_returnsEmptyList() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader(""), CSVFormat.DEFAULT);
        List<CSVRecord> records = parser.getRecords();
        assertTrue(records.isEmpty());
    }

    // getRecords(): single record with trailing newline (EORECORD branch)
    @Test
    public void testGetRecords_singleRecord_returnsCorrectValues() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b,c\n"), CSVFormat.DEFAULT);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertArrayEquals(new String[] {"a", "b", "c"}, records.get(0).values());
    }

    // getRecords(): multiple records, loop runs several times
    @Test
    public void testGetRecords_multipleRecords_returnsAllRecords() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b\nc,d\ne,f\n"), CSVFormat.DEFAULT);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(3, records.size());
        assertArrayEquals(new String[] {"e", "f"}, records.get(2).values());
    }

    // getRecords(): no trailing newline -> EOF branch with isReady true still captures last record
    @Test
    public void testGetRecords_noTrailingNewline_lastRecordCaptured() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b,c"), CSVFormat.DEFAULT);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertArrayEquals(new String[] {"a", "b", "c"}, records.get(0).values());
    }

    // getRecords(): explicit header, skipHeaderRecord=false (default) -> header line stays as data
    @Test
    public void testGetRecords_withExplicitHeaderNoSkip_headerRowIncludedAsData() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("A", "B");
        CSVParser parser = new CSVParser(new StringReader("A,B\n1,2\n"), format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertArrayEquals(new String[] {"A", "B"}, records.get(0).values());
        assertArrayEquals(new String[] {"1", "2"}, records.get(1).values());
    }

    // getRecords(): explicit header, skipHeaderRecord=true -> header line consumed, not in data
    @Test
    public void testGetRecords_withExplicitHeaderAndSkip_headerRowExcluded() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("A", "B").withSkipHeaderRecord(true);
        CSVParser parser = new CSVParser(new StringReader("A,B\n1,2\n"), format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1, records.size());
        assertArrayEquals(new String[] {"1", "2"}, records.get(0).values());
    }

    // getRecords(): quoted field containing the delimiter is parsed as one value
    @Test
    public void testGetRecords_quotedValueWithDelimiter_parsesCorrectly() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,\"b,c\",d\n"), CSVFormat.DEFAULT);
        List<CSVRecord> records = parser.getRecords();
        assertArrayEquals(new String[] {"a", "b,c", "d"}, records.get(0).values());
    }

    // getRecords(): COMMENT branch is skipped from data but following record still parsed
    @Test
    public void testGetRecords_withCommentMarker_commentLinesIgnoredDataPreserved() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentMarker('#');
        CSVParser parser = new CSVParser(new StringReader("a,b\n#comment line\nc,d\n"), format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertArrayEquals(new String[] {"a", "b"}, records.get(0).values());
        assertArrayEquals(new String[] {"c", "d"}, records.get(1).values());
    }

    // addRecordValue(): nullString configured and matched (case-insensitive) -> value becomes null
    @Test
    public void testGetRecords_withNullString_convertsMatchingValueToNull() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVParser parser = new CSVParser(new StringReader("a,NULL,c\n"), format);
        List<CSVRecord> records = parser.getRecords();
        String[] vals = records.get(0).values();
        assertEquals("a", vals[0]);
        assertNull(vals[1]);
        assertEquals("c", vals[2]);
    }

    // nextRecord(): empty input returns null directly
    @Test
    public void testNextRecord_emptyInput_returnsNull() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader(""), CSVFormat.DEFAULT);
        assertNull(parser.nextRecord());
    }

    // nextRecord(): single line produces one record with expected values
    @Test
    public void testNextRecord_singleLine_returnsRecordWithValues() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("x,y\n"), CSVFormat.DEFAULT);
        CSVRecord rec = parser.nextRecord();
        assertArrayEquals(new String[] {"x", "y"}, rec.values());
    }

    // nextRecord(): sequential calls increment recordNumber each time
    @Test
    public void testNextRecord_sequentialCalls_incrementsRecordNumber() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a\nb\n"), CSVFormat.DEFAULT);
        parser.nextRecord();
        assertEquals(1L, parser.getRecordNumber());
        parser.nextRecord();
        assertEquals(2L, parser.getRecordNumber());
    }

    // isClosed(): false before close() is called
    @Test
    public void testIsClosed_beforeClose_returnsFalse() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b\n"), CSVFormat.DEFAULT);
        assertFalse(parser.isClosed());
    }

    // close(): sets isClosed() to true
    @Test
    public void testClose_setsIsClosedTrue() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b\n"), CSVFormat.DEFAULT);
        parser.close();
        assertTrue(parser.isClosed());
    }

    // close(): calling twice does not throw and stays closed
    @Test
    public void testClose_calledTwice_noException() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a,b\n"), CSVFormat.DEFAULT);
        parser.close();
        parser.close();
        assertTrue(parser.isClosed());
    }

    // iterator(): hasNext()/next() walk through all records in order
    @Test
    public void testIterator_hasNextAndNext_iteratesAllRecords() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a\nb\n"), CSVFormat.DEFAULT);
        Iterator<CSVRecord> it = parser.iterator();
        assertTrue(it.hasNext());
        CSVRecord r1 = it.next();
        assertArrayEquals(new String[] {"a"}, r1.values());
        assertTrue(it.hasNext());
        CSVRecord r2 = it.next();
        assertArrayEquals(new String[] {"b"}, r2.values());
        assertFalse(it.hasNext());
    }

    // iterator(): next() works even when hasNext() was not called first
    @Test
    public void testIterator_nextWithoutHasNext_returnsRecord() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("x\n"), CSVFormat.DEFAULT);
        Iterator<CSVRecord> it = parser.iterator();
        CSVRecord rec = it.next();
        assertArrayEquals(new String[] {"x"}, rec.values());
    }

    // iterator(): next() on exhausted iterator throws NoSuchElementException
    @Test
    public void testIterator_exhausted_nextThrowsNoSuchElementException() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader(""), CSVFormat.DEFAULT);
        Iterator<CSVRecord> it = parser.iterator();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // iterator(): remove() is unsupported
    @Test
    public void testIterator_remove_throwsUnsupportedOperationException() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a\n"), CSVFormat.DEFAULT);
        Iterator<CSVRecord> it = parser.iterator();
        try {
            it.remove();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // iterator(): after parser is closed, hasNext() reports false
    @Test
    public void testIterator_afterClose_hasNextReturnsFalse() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a\nb\n"), CSVFormat.DEFAULT);
        Iterator<CSVRecord> it = parser.iterator();
        parser.close();
        assertFalse(it.hasNext());
    }

    // iterator(): after parser is closed, next() throws NoSuchElementException
    @Test
    public void testIterator_afterClose_nextThrowsNoSuchElementException() throws Throwable {
        CSVParser parser = new CSVParser(new StringReader("a\nb\n"), CSVFormat.DEFAULT);
        Iterator<CSVRecord> it = parser.iterator();
        parser.close();
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }
}
