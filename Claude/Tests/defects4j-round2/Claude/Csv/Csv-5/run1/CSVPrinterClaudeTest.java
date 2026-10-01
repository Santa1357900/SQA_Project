package org.apache.commons.csv;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class CSVPrinterClaudeTest {

    private static class FlagWriter extends StringWriter {
        boolean flushed = false;
        boolean closed = false;

        public void flush() {
            flushed = true;
            super.flush();
        }

        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    // constructor: out == null -> Assertions.notNull throws IllegalArgumentException
    @Test
    public void testConstructor_nullOut_throwsIllegalArgumentException() throws Throwable {
        try {
            new CSVPrinter(null, CSVFormat.DEFAULT);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // constructor: format == null -> Assertions.notNull throws IllegalArgumentException
    @Test
    public void testConstructor_nullFormat_throwsIllegalArgumentException() throws Throwable {
        StringBuilder sb = new StringBuilder();
        try {
            new CSVPrinter(sb, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // constructor: valid args -> getOut() returns the same Appendable instance
    @Test
    public void testConstructor_validArguments_getOutReturnsSameInstance() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        assertSame(sb, printer.getOut());
    }

    // print: value==null, nullString==null -> empty string; MINIMAL+newRecord+len<=0 -> quoted empty
    @Test
    public void testPrint_nullValueDefaultFormat_quotedEmptyAtStart() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print(null);
        char q = CSVFormat.DEFAULT.getQuoteChar().charValue();
        String expected = "" + q + q;
        assertEquals(expected, sb.toString());
    }

    // print: value==null, nullString!=null -> printed text is the configured null string
    @Test
    public void testPrint_nullValueWithCustomNullString_roundTripsToNullString() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord(new Object[] { null });
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("NULL", record.get(0));
    }

    // print: MINIMAL policy, no special chars at all -> written unquoted as-is
    @Test
    public void testPrint_simpleAlnumValue_noQuotingApplied() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print("abc123");
        assertEquals("abc123", sb.toString());
    }

    // print: MINIMAL scan finds embedded quote char -> must round-trip via doubling
    @Test
    public void testPrint_valueWithEmbeddedQuote_roundTrips() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord(new Object[] { "a\"b" });
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("a\"b", record.get(0));
    }

    // print: MINIMAL scan finds embedded delimiter -> must round-trip via encapsulation
    @Test
    public void testPrint_valueWithEmbeddedDelimiter_roundTrips() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord(new Object[] { "a,b" });
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("a,b", record.get(0));
    }

    // print: MINIMAL scan finds embedded LF -> must round-trip via encapsulation
    @Test
    public void testPrint_valueWithEmbeddedLF_roundTrips() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord(new Object[] { "a\nb" });
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("a\nb", record.get(0));
    }

    // print: MINIMAL scan finds embedded CRLF -> must round-trip via encapsulation
    @Test
    public void testPrint_valueWithEmbeddedCRLF_roundTrips() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord(new Object[] { "a\r\nb" });
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("a\r\nb", record.get(0));
    }

    // print: MINIMAL, trailing char <= SP -> value must be encapsulated (quoted)
    @Test
    public void testPrint_trailingSpaceValue_quoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print("ab ");
        char q = CSVFormat.DEFAULT.getQuoteChar().charValue();
        assertTrue(sb.length() > 0 && sb.charAt(0) == q);
    }

    // print: !newRecord -> delimiter is prepended before second value in the same record
    @Test
    public void testPrint_secondValueInRecord_prependsDelimiter() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print("a");
        printer.print("b");
        char delim = CSVFormat.DEFAULT.getDelimiter();
        assertEquals("a" + delim + "b", sb.toString());
    }

    // printAndQuote: Quote.NON_NUMERIC, object instanceof Number -> quote=false
    @Test
    public void testPrint_quotePolicyNON_NUMERIC_numberNotQuoted() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NON_NUMERIC);
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print(Integer.valueOf(42));
        assertEquals("42", sb.toString());
    }

    // printAndQuote: Quote.NON_NUMERIC, object not instanceof Number -> quote=true
    @Test
    public void testPrint_quotePolicyNON_NUMERIC_stringIsQuoted() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NON_NUMERIC);
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("abc");
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + "abc" + q, sb.toString());
    }

    // printAndQuote: Quote.ALL -> always quote, even a plain alnum value
    @Test
    public void testPrint_quotePolicyALL_plainValueQuoted() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.ALL);
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("abc");
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + "abc" + q, sb.toString());
    }

    // printAndQuote: Quote.ALL with empty value -> quoted empty token
    @Test
    public void testPrint_quotePolicyALL_emptyValueQuoted() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.ALL);
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("");
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + q, sb.toString());
    }

    // println: appends the record separator and resets newRecord so next print has no delimiter
    @Test
    public void testPrintln_appendsRecordSeparatorAndResetsNewRecordFlag() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print("a");
        printer.println();
        printer.print("b");
        String sep = CSVFormat.DEFAULT.getRecordSeparator();
        assertEquals("a" + sep + "b", sb.toString());
    }

    // printRecord(Iterable): each element printed then println appended
    @Test
    public void testPrintRecord_Iterable_roundTripsValues() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        List<String> values = new ArrayList<String>();
        values.add("x");
        values.add("y");
        printer.printRecord(values);
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("x", record.get(0));
        assertEquals("y", record.get(1));
    }

    // printRecord(Iterable): zero-iteration loop -> only the record separator is emitted
    @Test
    public void testPrintRecord_IterableEmpty_onlyRecordSeparator() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.printRecord(new ArrayList<Object>());
        assertEquals(CSVFormat.DEFAULT.getRecordSeparator(), sb.toString());
    }

    // printRecord(Object...): each varargs element printed then println appended
    @Test
    public void testPrintRecord_varargs_roundTripsValues() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord("p", "q", "r");
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("p", record.get(0));
        assertEquals("q", record.get(1));
        assertEquals("r", record.get(2));
    }

    // printRecord(Object...): zero-length array -> only the record separator is emitted
    @Test
    public void testPrintRecord_varargsEmpty_onlyRecordSeparator() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.printRecord(new Object[0]);
        assertEquals(CSVFormat.DEFAULT.getRecordSeparator(), sb.toString());
    }

    // printRecords(Iterable): element instanceof Object[] -> delegates to printRecord(Object[])
    @Test
    public void testPrintRecords_IterableWithObjectArrayElement_flattensToSingleRecord() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        List<Object> rows = new ArrayList<Object>();
        rows.add(new Object[] { "m", "n" });
        printer.printRecords(rows);
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("m", record.get(0));
        assertEquals("n", record.get(1));
    }

    // printRecords(Iterable): element instanceof Iterable -> delegates to printRecord(Iterable)
    @Test
    public void testPrintRecords_IterableWithNestedIterableElement_flattensToSingleRecord() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        List<Object> rows = new ArrayList<Object>();
        List<String> inner = new ArrayList<String>();
        inner.add("s");
        inner.add("t");
        rows.add(inner);
        printer.printRecords(rows);
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("s", record.get(0));
        assertEquals("t", record.get(1));
    }

    // printRecords(Iterable): plain element -> delegates to printRecord(Object) single column
    @Test
    public void testPrintRecords_IterableWithPlainElement_printsAsOwnRecord() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        List<Object> rows = new ArrayList<Object>();
        rows.add("solo");
        printer.printRecords(rows);
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("solo", record.get(0));
    }

    // printRecords(Object[]): same flattening behavior as the Iterable overload
    @Test
    public void testPrintRecords_ObjectArrayVariant_sameFlatteningBehavior() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        Object[] rows = new Object[] { new Object[] { "u", "v" } };
        printer.printRecords(rows);
        CSVRecord record = CSVParser.parse(sb.toString(), format).getRecords().get(0);
        assertEquals("u", record.get(0));
        assertEquals("v", record.get(1));
    }

    // printComment: commenting disabled by default -> method returns immediately, no output
    @Test
    public void testPrintComment_disabledByDefault_noOutput() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.printComment("hello");
        assertEquals("", sb.toString());
    }

    // printComment: commenting enabled, no CR/LF in text -> single "<start> text" line
    @Test
    public void testPrintComment_enabledSingleLine_formatsWithPrefix() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printComment("hello");
        String sep = format.getRecordSeparator();
        assertEquals("# hello" + sep, sb.toString());
    }

    // printComment: LF inside comment -> new comment prefix emitted for following segment
    @Test
    public void testPrintComment_enabledLFSplit_eachLineGetsPrefix() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printComment("a\nb");
        String sep = format.getRecordSeparator();
        assertEquals("# a" + sep + "# b" + sep, sb.toString());
    }

    // printComment: CR immediately followed by LF -> treated as a single line break
    @Test
    public void testPrintComment_enabledCRLFSplit_treatedAsSingleLineBreak() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printComment("a\r\nb");
        String sep = format.getRecordSeparator();
        assertEquals("# a" + sep + "# b" + sep, sb.toString());
    }

    // printComment: called when !newRecord -> an extra println precedes the comment prefix
    @Test
    public void testPrintComment_afterExistingRecord_addsLeadingLineBreak() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("v");
        printer.printComment("c");
        String sep = format.getRecordSeparator();
        assertEquals("v" + sep + "# c" + sep, sb.toString());
    }

    // flush: out instanceof Flushable == true -> delegated flush() is actually invoked
    @Test
    public void testFlush_withFlushableAppendable_invokesUnderlyingFlush() throws Throwable {
        FlagWriter fw = new FlagWriter();
        CSVPrinter printer = new CSVPrinter(fw, CSVFormat.DEFAULT);
        printer.flush();
        assertTrue(fw.flushed);
    }

    // close: out instanceof Closeable == true -> delegated close() is actually invoked
    @Test
    public void testClose_withCloseableAppendable_invokesUnderlyingClose() throws Throwable {
        FlagWriter fw = new FlagWriter();
        CSVPrinter printer = new CSVPrinter(fw, CSVFormat.DEFAULT);
        printer.close();
        assertTrue(fw.closed);
    }

    // flush: out instanceof Flushable == false -> no-op, underlying content unchanged
    @Test
    public void testFlush_withNonFlushableAppendable_noStateChange() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print("z");
        printer.flush();
        assertEquals("z", sb.toString());
    }

    // close: out instanceof Closeable == false -> no-op, underlying content unchanged
    @Test
    public void testClose_withNonCloseableAppendable_noStateChange() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print("z");
        printer.close();
        assertEquals("z", sb.toString());
    }
}
