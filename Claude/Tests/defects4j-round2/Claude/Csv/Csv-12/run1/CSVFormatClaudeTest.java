package org.apache.commons.csv;

import static org.junit.Assert.*;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Test;

public class CSVFormatClaudeTest {

    // covers newFormat(char): valid delimiter creates format with null quote/header
    @Test
    public void testNewFormat_validDelimiter_createsFormat() throws Throwable {
        CSVFormat format = CSVFormat.newFormat(';');
        assertEquals(';', format.getDelimiter());
        assertNull(format.getQuoteCharacter());
        assertNull(format.getHeader());
    }

    // covers constructor's isLineBreak(delimiter) check via newFormat
    @Test
    public void testNewFormat_lineBreakDelimiter_throwsException() throws Throwable {
        try {
            CSVFormat.newFormat('\n');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers equals(): this == obj branch
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.equals(CSVFormat.DEFAULT));
    }

    // covers equals(): obj == null and getClass() != obj.getClass() branches
    @Test
    public void testEquals_nullAndDifferentClass_returnsFalse() throws Throwable {
        assertFalse(CSVFormat.DEFAULT.equals(null));
        assertFalse(CSVFormat.DEFAULT.equals("not a format"));
    }

    // covers equals(): delimiter mismatch branch
    @Test
    public void testEquals_differentDelimiter_returnsFalse() throws Throwable {
        CSVFormat other = CSVFormat.DEFAULT.withDelimiter(';');
        assertFalse(CSVFormat.DEFAULT.equals(other));
    }

    // covers equals(): all fields matching returns true
    @Test
    public void testEquals_equalFormats_returnsTrue() throws Throwable {
        CSVFormat a = CSVFormat.newFormat(',').withQuote('"').withIgnoreEmptyLines(true).withRecordSeparator("\r\n");
        assertTrue(CSVFormat.DEFAULT.equals(a));
    }

    // covers format(Object...): simple values joined by delimiter, trailing separator trimmed
    @Test
    public void testFormat_simpleValues_returnsCommaJoined() throws Throwable {
        String result = CSVFormat.DEFAULT.format("a", "b", "c");
        assertEquals("a,b,c", result);
    }

    // covers getCommentMarker/getEscapeCharacter/getNullString default null
    @Test
    public void testDefaultFormat_optionalFieldsAreNull() throws Throwable {
        assertNull(CSVFormat.DEFAULT.getCommentMarker());
        assertNull(CSVFormat.DEFAULT.getEscapeCharacter());
        assertNull(CSVFormat.DEFAULT.getNullString());
    }

    // covers getDelimiter/getQuoteCharacter/getRecordSeparator/getAllowMissingColumnNames/getIgnoreSurroundingSpaces per Javadoc
    @Test
    public void testDefaultFormat_basicFieldValues() throws Throwable {
        assertEquals(',', CSVFormat.DEFAULT.getDelimiter());
        assertEquals(Character.valueOf('"'), CSVFormat.DEFAULT.getQuoteCharacter());
        assertEquals("\r\n", CSVFormat.DEFAULT.getRecordSeparator());
        assertFalse(CSVFormat.DEFAULT.getAllowMissingColumnNames());
        assertFalse(CSVFormat.DEFAULT.getIgnoreSurroundingSpaces());
    }

    // covers getHeader(): header == null returns null branch
    @Test
    public void testGetHeader_null_returnsNull() throws Throwable {
        assertNull(CSVFormat.DEFAULT.getHeader());
    }

    // covers getHeader(): returns defensive copy, mutation does not affect internal state
    @Test
    public void testGetHeader_returnsDefensiveCopy() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("a", "b");
        String[] header1 = format.getHeader();
        header1[0] = "changed";
        String[] header2 = format.getHeader();
        assertEquals("a", header2[0]);
    }

    // Javadoc for EXCEL states withAllowMissingColumnNames(true) is applied; this pins the Csv-12 contract
    @Test
    public void testEXCEL_allowMissingColumnNamesTrue_perJavadoc() throws Throwable {
        assertTrue(CSVFormat.EXCEL.getAllowMissingColumnNames());
    }

    // covers getIgnoreEmptyLines(): DEFAULT true, RFC4180 false
    @Test
    public void testGetIgnoreEmptyLines_defaultTrue_RFC4180False() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.getIgnoreEmptyLines());
        assertFalse(CSVFormat.RFC4180.getIgnoreEmptyLines());
    }

    // covers hashCode(): equal objects produce equal hash codes
    @Test
    public void testHashCode_equalFormats_sameHashCode() throws Throwable {
        CSVFormat a = CSVFormat.newFormat(',').withQuote('"').withIgnoreEmptyLines(true).withRecordSeparator("\r\n");
        assertEquals(CSVFormat.DEFAULT.hashCode(), a.hashCode());
    }

    // covers isCommentMarkerSet(): false when unset, true when set
    @Test
    public void testIsCommentMarkerSet_defaultFalse_thenTrue() throws Throwable {
        assertFalse(CSVFormat.DEFAULT.isCommentMarkerSet());
        assertTrue(CSVFormat.DEFAULT.withCommentMarker('#').isCommentMarkerSet());
    }

    // covers isEscapeCharacterSet(): false when unset, true when set (MYSQL)
    @Test
    public void testIsEscapeCharacterSet_defaultFalse_thenTrueForMYSQL() throws Throwable {
        assertFalse(CSVFormat.DEFAULT.isEscapeCharacterSet());
        assertTrue(CSVFormat.MYSQL.isEscapeCharacterSet());
    }

    // covers isNullStringSet(): false when unset, true when set
    @Test
    public void testIsNullStringSet_defaultFalse_thenTrue() throws Throwable {
        assertFalse(CSVFormat.DEFAULT.isNullStringSet());
        assertTrue(CSVFormat.DEFAULT.withNullString("N/A").isNullStringSet());
    }

    // covers isQuoteCharacterSet(): true by default, false when disabled via null
    @Test
    public void testIsQuoteCharacterSet_defaultTrue_thenFalse() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.isQuoteCharacterSet());
        CSVFormat noQuote = CSVFormat.DEFAULT.withQuote((Character) null);
        assertFalse(noQuote.isQuoteCharacterSet());
    }

    // covers parse(Reader): returns a non-null CSVParser
    @Test
    public void testParse_returnsNonNullParser() throws Throwable {
        Reader in = new StringReader("a,b,c");
        CSVParser parser = CSVFormat.DEFAULT.parse(in);
        assertNotNull(parser);
    }

    // covers print(Appendable): returns a non-null CSVPrinter
    @Test
    public void testPrint_returnsNonNullPrinter() throws Throwable {
        StringWriter out = new StringWriter();
        CSVPrinter printer = CSVFormat.DEFAULT.print(out);
        assertNotNull(printer);
    }

    // covers toString(): contains delimiter and skipHeaderRecord tokens
    @Test
    public void testToString_containsExpectedTokens() throws Throwable {
        String s = CSVFormat.DEFAULT.toString();
        assertTrue(s.contains("Delimiter=<,>"));
        assertTrue(s.contains("SkipHeaderRecord:false"));
    }

    // covers withCommentMarker(char): sets marker via valueOf overload
    @Test
    public void testWithCommentMarker_char_setsMarker() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withCommentMarker('#');
        assertEquals(Character.valueOf('#'), format.getCommentMarker());
    }

    // covers withCommentMarker(Character): line break throws exception
    @Test
    public void testWithCommentMarker_lineBreak_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withCommentMarker('\n');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers validate(): comment marker equal to delimiter throws exception
    @Test
    public void testWithCommentMarker_sameAsDelimiter_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withCommentMarker(',');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers withDelimiter(char): line break throws exception
    @Test
    public void testWithDelimiter_lineBreak_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withDelimiter('\r');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers validate(): escape character equal to delimiter throws exception
    @Test
    public void testWithEscape_sameAsDelimiter_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withEscape(',');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers withHeader(String...): sets header names
    @Test
    public void testWithHeader_setsHeader() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader("col1", "col2");
        String[] header = format.getHeader();
        assertEquals(2, header.length);
        assertEquals("col1", header[0]);
        assertEquals("col2", header[1]);
    }

    // covers constructor: duplicate header entries throw exception
    @Test
    public void testWithHeader_duplicateEntries_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withHeader("col1", "col1");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers withHeader() with no args: sets empty header array (auto-detect mode)
    @Test
    public void testWithHeader_noArgs_setsEmptyHeader() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader();
        assertNotNull(format.getHeader());
        assertEquals(0, format.getHeader().length);
    }

    // covers withAllowMissingColumnNames(boolean): sets value to true
    @Test
    public void testWithAllowMissingColumnNames_setsValue() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withAllowMissingColumnNames(true);
        assertTrue(format.getAllowMissingColumnNames());
    }

    // covers withIgnoreEmptyLines(boolean): sets value to false
    @Test
    public void testWithIgnoreEmptyLines_setsValue() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withIgnoreEmptyLines(false);
        assertFalse(format.getIgnoreEmptyLines());
    }

    // covers withIgnoreSurroundingSpaces(boolean): sets value to true
    @Test
    public void testWithIgnoreSurroundingSpaces_setsValue() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces(true);
        assertTrue(format.getIgnoreSurroundingSpaces());
    }

    // covers withNullString(String): sets conversion string
    @Test
    public void testWithNullString_setsValue() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withNullString("NULL");
        assertEquals("NULL", format.getNullString());
    }

    // covers validate(): quote character equal to delimiter throws exception
    @Test
    public void testWithQuote_sameAsDelimiter_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote(',');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers withQuote(Character): line break throws exception
    @Test
    public void testWithQuote_lineBreak_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote('\n');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers validate(): escapeCharacter == null && quoteMode == NONE throws exception
    @Test
    public void testWithQuoteMode_NONEWithoutEscape_throwsException() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NONE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers withQuoteMode(QuoteMode): NONE with escape character set succeeds
    @Test
    public void testWithQuoteMode_NONEWithEscape_succeeds() throws Throwable {
        CSVFormat format = CSVFormat.MYSQL.withQuoteMode(QuoteMode.NONE);
        assertEquals(QuoteMode.NONE, format.getQuoteMode());
    }

    // covers withRecordSeparator(char): delegates to withRecordSeparator(String)
    @Test
    public void testWithRecordSeparator_char_setsValue() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withRecordSeparator('\n');
        assertEquals("\n", format.getRecordSeparator());
    }

    // covers withSkipHeaderRecord(boolean): default false, set to true
    @Test
    public void testWithSkipHeaderRecord_setsValue() throws Throwable {
        assertFalse(CSVFormat.DEFAULT.getSkipHeaderRecord());
        CSVFormat format = CSVFormat.DEFAULT.withSkipHeaderRecord(true);
        assertTrue(format.getSkipHeaderRecord());
    }

    // covers MYSQL predefined format settings per Javadoc
    @Test
    public void testMYSQL_settings_matchesJavadoc() throws Throwable {
        assertEquals('\t', CSVFormat.MYSQL.getDelimiter());
        assertNull(CSVFormat.MYSQL.getQuoteCharacter());
        assertEquals(Character.valueOf('\\'), CSVFormat.MYSQL.getEscapeCharacter());
        assertFalse(CSVFormat.MYSQL.getIgnoreEmptyLines());
        assertEquals("\n", CSVFormat.MYSQL.getRecordSeparator());
    }

    // covers TDF predefined format settings per Javadoc
    @Test
    public void testTDF_settings_matchesJavadoc() throws Throwable {
        assertEquals('\t', CSVFormat.TDF.getDelimiter());
        assertTrue(CSVFormat.TDF.getIgnoreSurroundingSpaces());
        assertEquals(Character.valueOf('"'), CSVFormat.TDF.getQuoteCharacter());
    }
}
