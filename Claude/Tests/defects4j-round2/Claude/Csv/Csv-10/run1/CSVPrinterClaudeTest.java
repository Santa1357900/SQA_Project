package org.apache.commons.csv;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Arrays;

public class CSVPrinterClaudeTest {

    // Constructor: Assertions.notNull(out,...) branch -> IllegalArgumentException per javadoc
    @Test
    public void testConstructor_nullOut_throwsIllegalArgumentException() throws Throwable {
        try {
            new CSVPrinter((Appendable) null, CSVFormat.DEFAULT);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Constructor: Assertions.notNull(format,...) branch -> IllegalArgumentException per javadoc
    @Test
    public void testConstructor_nullFormat_throwsIllegalArgumentException() throws Throwable {
        try {
            new CSVPrinter(new StringBuilder(), (CSVFormat) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Constructor: valid params stored, getOut() returns exact same instance
    @Test
    public void testConstructor_validParams_getOutReturnsSameAppendable() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        assertSame(sb, printer.getOut());
    }

    // close(): out instanceof Closeable branch executes without corrupting state
    @Test
    public void testClose_withCloseableAppendable_contentUnaffected() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.print("abc");
        printer.close();
        assertEquals("abc", sw.toString());
    }

    // flush(): out instanceof Flushable branch executes without corrupting state
    @Test
    public void testFlush_withFlushableAppendable_contentUnaffected() throws Throwable {
        StringWriter sw = new StringWriter();
        CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
        printer.print("abc");
        printer.flush();
        assertEquals("abc", sw.toString());
    }

    // print(null): default nullString null -> empty value; MINIMAL+len<=0+newRecord true -> quoted empty
    @Test
    public void testPrint_null_firstFieldDefaultNullString_printsEmptyQuoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print(null);
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + q, sb.toString());
    }

    // print(null) with custom nullString configured -> printed literally, not quoted (alnum)
    @Test
    public void testPrint_nullWithCustomNullString_printsNullStringUnquoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print(null);
        assertEquals("NULL", sb.toString());
    }

    // MINIMAL: plain alphanumeric single field -> no quoting needed
    @Test
    public void testPrint_simpleAlnumValue_notQuoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.print("abc");
        assertEquals("abc", sb.toString());
    }

    // MINIMAL: value containing delimiter -> wrapped in quotes
    @Test
    public void testPrint_valueContainingDelimiter_quoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        char delim = format.getDelimiter();
        String value = "a" + delim + "b";
        printer.print(value);
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + value + q, sb.toString());
    }

    // MINIMAL: value containing quote char -> wrapped in quotes and internal quote doubled
    @Test
    public void testPrint_valueContainingEmbeddedQuoteChar_quotedAndDoubled() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        char q = format.getQuoteChar().charValue();
        String value = "a" + q + "b";
        printer.print(value);
        String expected = "" + q + "a" + q + q + "b" + q;
        assertEquals(expected, sb.toString());
    }

    // MINIMAL: value containing CRLF -> wrapped in quotes, CR/LF preserved unmodified
    @Test
    public void testPrint_valueContainingCRLF_quoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        String value = "a\r\nb";
        printer.print(value);
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + value + q, sb.toString());
    }

    // second call to print(): delimiter prepended since not a new record anymore
    @Test
    public void testPrint_secondValue_prependsDelimiter() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("a");
        printer.print("b");
        assertEquals("a" + format.getDelimiter() + "b", sb.toString());
    }

    // MINIMAL: empty value on a non-first field is not forced to be quoted
    @Test
    public void testPrint_emptySecondField_notQuoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord("a", "");
        String expected = "a" + format.getDelimiter() + "" + format.getRecordSeparator();
        assertEquals(expected, sb.toString());
    }

    // MINIMAL: field starting with a char <= '#' is quoted even when it is not the first field
    @Test
    public void testPrint_fieldStartingWithControlCharNotFirst_stillQuoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("a");
        printer.print(" b");
        char q = format.getQuoteChar().charValue();
        assertEquals("a" + format.getDelimiter() + q + " b" + q, sb.toString());
    }

    // MINIMAL: value ending in a char <= space must be quoted to survive round trip
    @Test
    public void testPrint_trailingSpace_quoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("ab ");
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + "ab " + q, sb.toString());
    }

    // Quote.ALL: every value is quoted regardless of content
    @Test
    public void testPrint_quoteAllPolicy_alwaysQuotesEvenSimpleValue() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.ALL);
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("abc");
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + "abc" + q, sb.toString());
    }

    // Quote.NON_NUMERIC: Number instance is not quoted
    @Test
    public void testPrint_quoteNonNumericPolicy_numberNotQuoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NON_NUMERIC);
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print(Integer.valueOf(5));
        assertEquals("5", sb.toString());
    }

    // Quote.NON_NUMERIC: non-Number instance is quoted even though its content is numeric text
    @Test
    public void testPrint_quoteNonNumericPolicy_stringQuoted() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withQuotePolicy(Quote.NON_NUMERIC);
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("5");
        char q = format.getQuoteChar().charValue();
        assertEquals("" + q + "5" + q, sb.toString());
    }

    // Custom quote char via withQuoteChar is honored for encapsulation
    @Test
    public void testPrint_customQuoteChar_usedForEncapsulation() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withQuoteChar('\'');
        CSVPrinter printer = new CSVPrinter(sb, format);
        char delim = format.getDelimiter();
        String value = "a" + delim + "b";
        printer.print(value);
        assertEquals("'" + value + "'", sb.toString());
    }

    // println(): record separator appended and newRecord reset so next print has no leading delimiter
    @Test
    public void testPrintln_defaultRecordSeparator_appendedAndResetsNewRecord() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("x");
        printer.println();
        printer.print("y");
        assertEquals("x" + format.getRecordSeparator() + "y", sb.toString());
    }

    // println(): null record separator configured -> nothing appended
    @Test
    public void testPrintln_nullRecordSeparator_appendsNothing() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withRecordSeparator(null);
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("x");
        printer.println();
        assertEquals("x", sb.toString());
    }

    // printRecord(Object...): values separated by delimiter, ends with record separator
    @Test
    public void testPrintRecord_varargs_printsAllValuesWithDelimiterAndSeparator() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord("a", "b", "c");
        char d = format.getDelimiter();
        assertEquals("a" + d + "b" + d + "c" + format.getRecordSeparator(), sb.toString());
    }

    // printRecord(Object...): zero-length varargs -> loop runs zero times, only separator printed
    @Test
    public void testPrintRecord_emptyVarargs_printsOnlySeparator() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord();
        assertEquals(format.getRecordSeparator(), sb.toString());
    }

    // printRecord(Iterable): values from a collection are printed like varargs
    @Test
    public void testPrintRecord_iterable_printsAllValues() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord(Arrays.asList("a", "b"));
        assertEquals("a" + format.getDelimiter() + "b" + format.getRecordSeparator(), sb.toString());
    }

    // printRecords(Object[]): nested Object[] element is printed as its own record
    @Test
    public void testPrintRecords_arrayContainingNestedArray_printsAsSeparateRecords() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        Object[] outer = new Object[] { new Object[] { "a", "b" }, "c" };
        printer.printRecords(outer);
        String sep = format.getRecordSeparator();
        char d = format.getDelimiter();
        assertEquals("a" + d + "b" + sep + "c" + sep, sb.toString());
    }

    // printRecords(Object[]): nested Iterable element is printed as its own record
    @Test
    public void testPrintRecords_arrayContainingIterable_printsAsSeparateRecords() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        Object[] outer = new Object[] { Arrays.asList("x", "y") };
        printer.printRecords(outer);
        assertEquals("x" + format.getDelimiter() + "y" + format.getRecordSeparator(), sb.toString());
    }

    // printRecords(Iterable): each plain element becomes its own single-value record
    @Test
    public void testPrintRecords_iterableOfPlainValues_printsEachAsSingleValueRecord() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecords(Arrays.asList("p", "q"));
        String sep = format.getRecordSeparator();
        assertEquals("p" + sep + "q" + sep, sb.toString());
    }

    // printComment(): commenting disabled -> method is a no-op
    @Test
    public void testPrintComment_disabled_doesNothing() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVPrinter printer = new CSVPrinter(sb, CSVFormat.DEFAULT);
        printer.printComment("hello");
        assertEquals("", sb.toString());
    }

    // printComment(): enabled, first record -> no leading newline, marker+space+text+trailing newline
    @Test
    public void testPrintComment_enabled_prependsMarkerAndSpace() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printComment("hi");
        char cs = format.getCommentStart().charValue();
        assertEquals("" + cs + ' ' + "hi" + format.getRecordSeparator(), sb.toString());
    }

    // printComment(): LF inside comment starts a new commented line with marker again
    @Test
    public void testPrintComment_multilineWithLF_prependsMarkerOnEachLine() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printComment("line1\nline2");
        char cs = format.getCommentStart().charValue();
        String sep = format.getRecordSeparator();
        String expected = "" + cs + ' ' + "line1" + sep + cs + ' ' + "line2" + sep;
        assertEquals(expected, sb.toString());
    }

    // printComment(): CRLF pair is treated as a single newline, not two
    @Test
    public void testPrintComment_crlfTreatedAsSingleNewline() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printComment("a\r\nb");
        char cs = format.getCommentStart().charValue();
        String sep = format.getRecordSeparator();
        String expected = "" + cs + ' ' + "a" + sep + cs + ' ' + "b" + sep;
        assertEquals(expected, sb.toString());
    }

    // printComment(): if not a new record, a newline is emitted before the comment starts
    @Test
    public void testPrintComment_afterExistingContent_startsOnNewLineFirst() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT.withCommentStart('#');
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.print("x");
        printer.printComment("c");
        char cs = format.getCommentStart().charValue();
        String sep = format.getRecordSeparator();
        assertEquals("x" + sep + cs + ' ' + "c" + sep, sb.toString());
    }

    // Round trip: a value with an embedded quote and delimiter must parse back to the same original value
    @Test
    public void testRoundTrip_valueWithEmbeddedQuoteAndDelimiter_parsesBackToOriginal() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        String original = "a\"b,c";
        printer.printRecord(original, "second");
        CSVParser parser = CSVParser.parse(sb.toString(), format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(original, records.get(0).get(0));
        assertEquals("second", records.get(0).get(1));
    }

    // Round trip: multiple records printed must parse back with the same number of records and values
    @Test
    public void testRoundTrip_multipleRecords_parsesBackCorrectly() throws Throwable {
        StringBuilder sb = new StringBuilder();
        CSVFormat format = CSVFormat.DEFAULT;
        CSVPrinter printer = new CSVPrinter(sb, format);
        printer.printRecord("r1c1", "r1c2");
        printer.printRecord("r2c1", "r2c2");
        CSVParser parser = CSVParser.parse(sb.toString(), format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(2, records.size());
        assertEquals("r2c2", records.get(1).get(1));
    }
}
