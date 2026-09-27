package org.apache.commons.csv;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CSVPrinterTest {

    @Test
    public void testConstructorNullOut() throws Throwable {
        StringWriter sw = new StringWriter();
        try {
            new CSVPrinter(null, CSVFormat.DEFAULT);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testConstructorNullFormat() throws Throwable {
        StringWriter sw = new StringWriter();
        try {
            new CSVPrinter(sw, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testClose() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.close();
    }

    @Test
    public void testFlush() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.flush();
    }

    @Test
    public void testPrintNull() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print(null);
        assertEquals("NULL", sw.toString());
    }

    @Test
    public void testPrintNullDefault() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print(null);
        assertEquals("", sw.toString());
    }

    @Test
    public void testPrintAndEscape() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withEscape('\\').withQuoteChar(null);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("a\nb\rc,d\\e");
        assertTrue(sw.toString().contains("\\n"));
        assertTrue(sw.toString().contains("\\r"));
    }

    @Test
    public void testPrintAndQuoteAll() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.ALL);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("test");
        assertEquals("\"test\"", sw.toString());
    }

    @Test
    public void testPrintAndQuoteNonNumeric() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NON_NUMERIC);
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print(Integer.valueOf(123));
        assertEquals("123", sw.toString());

        StringWriter sw2 = new StringWriter();
        CSVPrinter printer2 = new CSVPrinter(sw2, format);
        printer2.print("123");
        assertEquals("\"123\"", sw2.toString());
    }

    @Test
    public void testPrintAndQuoteNone() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NONE).withEscape('\\');
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("test,ing");
        assertTrue(sw.toString().contains("\\"));
    }

    @Test
    public void testPrintAndQuoteMinimalEmpty() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("");
        assertEquals("\"\"", sw.toString());
    }

    @Test
    public void testPrintAndQuoteMinimalSpecialChar() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("#comment");
        assertTrue(sw.toString().startsWith("\""));
    }

    @Test
    public void testPrintAndQuoteMinimalTrailingSpace() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("test ");
        assertTrue(sw.toString().endsWith("\""));
    }

    @Test
    public void testPrintAndQuoteEncapsulation() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.print("te\"st");
        assertEquals("\"te\"\"st\"", sw.toString());
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
        printer.printComment("Line 1\nLine 2\rLine 3\r\nLine 4");
        assertTrue(sw.toString().contains("# Line 1"));
    }

    @Test
    public void testPrintln() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.withRecordSeparator("\n");
        CSVPrinter printer = new CSVPrinter(sw, format);
        printer.println();
        assertEquals("\n", sw.toString());
    }

    @Test
    public void testPrintRecordIterable() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        List<String> list = new ArrayList<String>();
        list.add("v1");
        list.add("v2");
        printer.printRecord(list);
        assertEquals("v1,v2\r\n", sw.toString());
    }

    @Test
    public void testPrintRecordVarargs() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.printRecord("v1", "v2");
        assertEquals("v1,v2\r\n", sw.toString());
    }

    @Test
    public void testPrintRecordsIterable() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        List<Object> records = new ArrayList<Object>();
        List<String> innerList = new ArrayList<String>();
        innerList.add("a");
        innerList.add("b");
        records.add(innerList);
        records.add(new Object[]{"c", "d"});
        records.add("e");
        printer.printRecords(records);
        assertTrue(sw.toString().length() > 0);
    }

    @Test
    public void testPrintRecordsArray() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        Object[] records = new Object[] {
            new Object[]{"a", "b"},
            new ArrayList<String>(),
            "c"
        };
        printer.printRecords(records);
        assertTrue(sw.toString().length() > 0);
    }

    @Test
    public void testGetOut() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        assertEquals(sw, printer.getOut());
    }
}