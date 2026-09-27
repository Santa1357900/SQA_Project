package org.apache.commons.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class CSVPrinterTest {

    @Test
    public void testConstructorNullOut() throws Throwable {
        StringWriter sw = null;
        CSVFormat format = CSVFormat.DEFAULT;
        try {
            new CSVPrinter(sw, format);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testConstructorNullFormat() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = null;
        try {
            new CSVPrinter(sw, format);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testCloseWithCloseable() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.close();
        // Should close underlying Appendable without exception
    }

    @Test
    public void testFlushWithFlushable() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.flush();
        // Should flush underlying Appendable without exception
    }

    @Test
    public void testPrintNullValue() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print(null);
        printer.println();
        assertEquals("NULL\r\n", sw.toString());
    }

    @Test
    public void testPrintNullValueDefault() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print(null);
        printer.println();
        assertEquals("\r\n", sw.toString());
    }

    @Test
    public void testPrintWithEscaping() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withEscape('\\').withQuote(null);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("a,b\nc\\d\r");
        printer.println();
        assertEquals("a\\,b\\nc\\\\d\\r\r\n", sw.toString());
    }

    @Test
    public void testPrintQuoteAll() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.ALL);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("test");
        printer.println();
        assertEquals("\"test\"\r\n", sw.toString());
    }

    @Test
    public void testPrintQuoteNonNumeric() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NON_NUMERIC);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print(Integer.valueOf(123));
        printer.print("text");
        printer.println();
        assertEquals("123,\"text\"\r\n", sw.toString());
    }

    @Test
    public void testPrintQuoteNone() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NONE).withEscape('\\');
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("a,b");
        printer.println();
        assertEquals("a\\,b\r\n", sw.toString());
    }

    @Test
    public void testPrintQuoteMinimalEmptyRecord() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.MINIMAL);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("");
        printer.print("value");
        printer.println();
        assertEquals("\"\",value\r\n", sw.toString());
    }

    @Test
    public void testPrintQuoteMinimalSpecialChars() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.MINIMAL);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("\t");
        printer.print("a\nb");
        printer.println();
        assertEquals("\"\t\",\"a\nb\"\r\n", sw.toString());
    }

    @Test
    public void testPrintQuoteMinimalTrailingSpace() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.MINIMAL);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("abc ");
        printer.println();
        assertEquals("\"abc \",\r\n", sw.toString());
    }

    @Test
    public void testPrintQuoteCharDuplication() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuote('"');
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("a\"b");
        printer.println();
        assertEquals("\"a\"\"b\"\r\n", sw.toString());
    }

    @Test
    public void testPrintCommentDisabled() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart(null);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.printComment("This is a comment");
        assertEquals("", sw.toString());
    }

    @Test
    public void testPrintCommentEnabled() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.printComment("Line 1\r\nLine 2\nLine 3");
        assertEquals("# Line 1\r\n# Line 2\r\n# Line 3\r\n", sw.toString());
    }

    @Test
    public void testPrintCommentWithExistingRecord() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("val");
        printer.printComment("Comment");
        assertEquals("val\r\n# Comment\r\n", sw.toString());
    }

    @Test
    public void testPrintRecordIterable() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        List<String> record = new ArrayList<String>();
        record.add("A");
        record.add("B");
        printer.printRecord(record);
        assertEquals("A,B\r\n", sw.toString());
    }

    @Test
    public void testPrintRecordVarargs() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.printRecord("A", "B", "C");
        assertEquals("A,B,C\r\n", sw.toString());
    }

    @Test
    public void testPrintRecordsIterable() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        List<Object> records = new ArrayList<Object>();
        List<String> rec1 = new ArrayList<String>();
        rec1.add("A");
        rec1.add("B");
        String[] rec2 = new String[] { "C", "D" };
        records.add(rec1);
        records.add(rec2);
        records.add("E");

        printer.printRecords(records);
        assertEquals("A,B\r\nC,D\r\nE\r\n", sw.toString());
    }

    @Test
    public void testPrintRecordsArray() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        Object[] records = new Object[] {
            new String[] { "A", "B" },
            new ArrayList<String>() {{ add("C"); add("D"); }},
            "E"
        };

        printer.printRecords(records);
        assertEquals("A,B\r\nC,D\r\nE\r\n", sw.toString());
    }

    @Test
    public void testGetOut() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        assertEquals(sw, printer.getOut());
    }
}