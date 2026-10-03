package org.apache.commons.csv;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Test;

public class CSVFormatClaudeTest {

    // Covers DEFAULT constant fields per Javadoc: delimiter ',', quote '"', CRLF separator, ignoreEmptyLines(true)
    @Test
    public void testDEFAULT_fieldsMatchJavadoc() throws Throwable {
        assertEquals(',', CSVFormat.DEFAULT.getDelimiter());
        assertEquals(Character.valueOf('"'), CSVFormat.DEFAULT.getQuoteCharacter());
        assertEquals("\r\n", CSVFormat.DEFAULT.getRecordSeparator());
        assertTrue(CSVFormat.DEFAULT.getIgnoreEmptyLines());
        assertFalse(CSVFormat.DEFAULT.getIgnoreSurroundingSpaces());
    }

    // Covers RFC4180 branch: ignoreEmptyLines(false) per Javadoc
    @Test
    public void testRFC4180_ignoreEmptyLinesFalse() throws Throwable {
        assertFalse(CSVFormat.RFC4180.getIgnoreEmptyLines());
    }

    // Covers EXCEL branch: ignoreEmptyLines(false) and allowMissingColumnNames(true)
    @Test
    public void testEXCEL_ignoreEmptyLinesFalseAndAllowMissingColumnNamesTrue() throws Throwable {
        assertFalse(CSVFormat.EXCEL.getIgnoreEmptyLines());
        assertTrue(CSVFormat.EXCEL.getAllowMissingColumnNames());
    }

    // Covers TDF branch: tab delimiter and ignoreSurroundingSpaces(true)
    @Test
    public void testTDF_delimiterTabAndIgnoreSurroundingSpacesTrue() throws Throwable {
        assertEquals('\t', CSVFormat.TDF.getDelimiter());
        assertTrue(CSVFormat.TDF.getIgnoreSurroundingSpaces());
    }

    // Covers MYSQL branch: delimiter tab, escape backslash, quote disabled, record separator LF
    @Test
    public void testMYSQL_delimiterEscapeQuoteRecordSeparator() throws Throwable {
        assertEquals('\t', CSVFormat.MYSQL.getDelimiter());
        assertEquals(Character.valueOf('\\'), CSVFormat.MYSQL.getEscapeCharacter());
        assertNull(CSVFormat.MYSQL.getQuoteCharacter());
        assertEquals("\n", CSVFormat.MYSQL.getRecordSeparator());
    }

    // Bug oracle: Javadoc states MYSQL's default NULL string is "\\N"
    @Test
    public void testMYSQL_nullString_matchesJavadoc() throws Throwable {
        assertEquals("\\N", CSVFormat.MYSQL.getNullString());
    }

    // Covers MYSQL branch: ignoreEmptyLines(false) per Javadoc
    @Test
    public void testMYSQL_ignoreEmptyLinesFalse() throws Throwable {
        assertFalse(CSVFormat.MYSQL.getIgnoreEmptyLines());
    }

    // Covers newFormat(): all other fields initialized to null/false per Javadoc
    @Test
    public void testNewFormat_basicFieldsAllDisabled() throws Throwable {
        CSVFormat f = CSVFormat.newFormat(';');
        assertEquals(';', f.getDelimiter());
        assertNull(f.getQuoteCharacter());
        assertNull(f.getCommentMarker());
        assertNull(f.getEscapeCharacter());
        assertFalse(f.getIgnoreSurroundingSpaces());
        assertFalse(f.getIgnoreEmptyLines());
        assertNull(f.getRecordSeparator());
        assertNull(f.getHeader());
    }

    // Covers newFormat(): line break delimiter must throw IllegalArgumentException
    @Test
    public void testNewFormat_lineBreakDelimiter_throws() throws Throwable {
        try {
            CSVFormat.newFormat('\n');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers valueOf(): "Default" resolves to CSVFormat.DEFAULT
    @Test
    public void testValueOf_default_returnsSameAsDEFAULT() throws Throwable {
        assertSame(CSVFormat.DEFAULT, CSVFormat.valueOf("Default"));
    }

    // Covers valueOf(): "Excel" resolves to CSVFormat.EXCEL
    @Test
    public void testValueOf_excel_returnsSameAsEXCEL() throws Throwable {
        assertSame(CSVFormat.EXCEL, CSVFormat.valueOf("Excel"));
    }

    // Covers withDelimiter(char): line break must throw
    @Test
    public void testWithDelimiter_lineBreak_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withDelimiter('\r');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers withDelimiter(char): valid delimiter updates only the delimiter field
    @Test
    public void testWithDelimiter_validChar_updatesDelimiterOnly() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withDelimiter(';');
        assertEquals(';', f.getDelimiter());
        assertEquals(CSVFormat.DEFAULT.getQuoteCharacter(), f.getQuoteCharacter());
    }

    // Covers withCommentMarker(char) and withCommentMarker(Character) null-disable branch
    @Test
    public void testWithCommentMarker_charAndNull() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withCommentMarker('#');
        assertTrue(f.isCommentMarkerSet());
        assertEquals(Character.valueOf('#'), f.getCommentMarker());
        CSVFormat g = f.withCommentMarker((Character) null);
        assertFalse(g.isCommentMarkerSet());
    }

    // Covers withCommentMarker(): line break must throw
    @Test
    public void testWithCommentMarker_lineBreak_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withCommentMarker('\n');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers validate(): comment marker equal to delimiter must throw
    @Test
    public void testWithCommentMarker_sameAsDelimiter_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withCommentMarker(',');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers withEscape(char): sets escape character and flag
    @Test
    public void testWithEscape_charSetsFlag() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withEscape('\\');
        assertTrue(f.isEscapeCharacterSet());
        assertEquals(Character.valueOf('\\'), f.getEscapeCharacter());
    }

    // Covers withEscape(): line break must throw
    @Test
    public void testWithEscape_lineBreak_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withEscape('\r');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers validate(): escape equal to delimiter must throw
    @Test
    public void testWithEscape_sameAsDelimiter_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withEscape(',');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers withHeader(String...): sets header array
    @Test
    public void testWithHeader_varargsSetsHeaderArray() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withHeader("a", "b", "c");
        assertArrayEquals(new String[] { "a", "b", "c" }, f.getHeader());
    }

    // Covers validate(): duplicate header names must throw
    @Test
    public void testWithHeader_duplicateNames_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withHeader("a", "b", "a");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers withHeader() no-args: header parsed automatically, so array is empty not null
    @Test
    public void testWithHeader_noArgsReturnsEmptyArray() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withHeader();
        assertNotNull(f.getHeader());
        assertEquals(0, f.getHeader().length);
    }

    // Covers withHeaderComments(Object...): sets header comments array
    @Test
    public void testWithHeaderComments_setsCommentsArray() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withHeaderComments("Generated", "by", "test");
        assertArrayEquals(new String[] { "Generated", "by", "test" }, f.getHeaderComments());
    }

    // Covers withAllowMissingColumnNames() no-arg defaults to true
    @Test
    public void testWithAllowMissingColumnNames_noArgDefaultsTrue() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.withAllowMissingColumnNames().getAllowMissingColumnNames());
    }

    // Covers withIgnoreEmptyLines() no-arg defaults to true
    @Test
    public void testWithIgnoreEmptyLines_noArgDefaultsTrue() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.withIgnoreEmptyLines().getIgnoreEmptyLines());
    }

    // Covers withIgnoreSurroundingSpaces() no-arg defaults to true
    @Test
    public void testWithIgnoreSurroundingSpaces_noArgDefaultsTrue() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.withIgnoreSurroundingSpaces().getIgnoreSurroundingSpaces());
    }

    // Covers withIgnoreHeaderCase() no-arg defaults to true
    @Test
    public void testWithIgnoreHeaderCase_noArgDefaultsTrue() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.withIgnoreHeaderCase().getIgnoreHeaderCase());
    }

    // Covers withNullString(): sets nullString and flag
    @Test
    public void testWithNullString_setsAndFlagTrue() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withNullString("NULL");
        assertTrue(f.isNullStringSet());
        assertEquals("NULL", f.getNullString());
    }

    // Covers withQuote(char) and withQuote(Character) null-disable branch
    @Test
    public void testWithQuote_charAndNull() throws Throwable {
        CSVFormat f = CSVFormat.DEFAULT.withQuote('\'');
        assertTrue(f.isQuoteCharacterSet());
        assertEquals(Character.valueOf('\''), f.getQuoteCharacter());
        CSVFormat g = f.withQuote((Character) null);
        assertFalse(g.isQuoteCharacterSet());
    }

    // Covers withQuote(): line break must throw
    @Test
    public void testWithQuote_lineBreak_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote('\n');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers validate(): quote char equal to delimiter must throw
    @Test
    public void testWithQuote_sameAsDelimiter_throws() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote(',');
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers validate(): QuoteMode.NONE without escape throws, but with escape it succeeds
    @Test
    public void testWithQuoteMode_NONE_requiresEscape() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NONE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        CSVFormat f = CSVFormat.DEFAULT.withEscape('\\').withQuoteMode(QuoteMode.NONE);
        assertEquals(QuoteMode.NONE, f.getQuoteMode());
    }

    // Covers withRecordSeparator(char) and withRecordSeparator(String)
    @Test
    public void testWithRecordSeparator_charAndStringVariants() throws Throwable {
        CSVFormat f1 = CSVFormat.DEFAULT.withRecordSeparator('\n');
        assertEquals("\n", f1.getRecordSeparator());
        CSVFormat f2 = CSVFormat.DEFAULT.withRecordSeparator("\r\n");
        assertEquals("\r\n", f2.getRecordSeparator());
    }

    // Covers withSkipHeaderRecord() no-arg defaults to true
    @Test
    public void testWithSkipHeaderRecord_noArgDefaultsTrue() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.withSkipHeaderRecord().getSkipHeaderRecord());
    }

    // Covers format(): simple values joined by delimiter, trimmed of trailing separator
    @Test
    public void testFormat_simpleValues_joinedByDelimiter() throws Throwable {
        assertEquals("a,b,c", CSVFormat.DEFAULT.format("a", "b", "c"));
    }

    // Covers format(): value containing delimiter must be quoted per CSV standard
    @Test
    public void testFormat_valueContainingDelimiter_getsQuoted() throws Throwable {
        assertEquals("\"a,b\",c", CSVFormat.DEFAULT.format("a,b", "c"));
    }

    // Covers parse(Reader): returns a non-null CSVParser instance
    @Test
    public void testParse_returnsNonNullParser() throws Throwable {
        CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader("a,b,c"));
        assertNotNull(parser);
    }

    // Covers print(Appendable): returns a working CSVPrinter that writes records
    @Test
    public void testPrint_writesRecordUsingPrinter() throws Throwable {
        StringWriter out = new StringWriter();
        CSVPrinter printer = CSVFormat.DEFAULT.print(out);
        printer.printRecord("x", "y");
        assertTrue(out.toString().indexOf("x,y") >= 0);
    }

    // Covers equals(): same object, null, and different class branches
    @Test
    public void testEquals_sameObjectNullDifferentClass() throws Throwable {
        assertTrue(CSVFormat.DEFAULT.equals(CSVFormat.DEFAULT));
        assertFalse(CSVFormat.DEFAULT.equals(null));
        assertFalse(CSVFormat.DEFAULT.equals("not a format"));
    }

    // Covers equals(): different delimiter is false, equal configuration is true
    @Test
    public void testEquals_differentDelimiterFalseAndEqualFormatsTrue() throws Throwable {
        assertFalse(CSVFormat.DEFAULT.equals(CSVFormat.DEFAULT.withDelimiter(';')));
        CSVFormat a = CSVFormat.DEFAULT.withHeader("x");
        CSVFormat b = CSVFormat.DEFAULT.withHeader("x");
        assertTrue(a.equals(b));
    }

    // Covers hashCode(): equal objects must produce the same hash code
    @Test
    public void testHashCode_equalObjectsSameHashCode() throws Throwable {
        CSVFormat a = CSVFormat.DEFAULT.withNullString("N");
        CSVFormat b = CSVFormat.DEFAULT.withNullString("N");
        assertEquals(a.hashCode(), b.hashCode());
    }

    // Covers toString(): contains delimiter and skip header record info
    @Test
    public void testToString_containsDelimiterAndSkipHeaderRecord() throws Throwable {
        String s = CSVFormat.DEFAULT.toString();
        assertTrue(s.indexOf("Delimiter=<,>") >= 0);
        assertTrue(s.indexOf("SkipHeaderRecord:false") >= 0);
    }

    // Covers isCommentMarkerSet/isEscapeCharacterSet/isNullStringSet/isQuoteCharacterSet defaults
    @Test
    public void testIsFlags_defaultsOnDEFAULT() throws Throwable {
        assertFalse(CSVFormat.DEFAULT.isCommentMarkerSet());
        assertFalse(CSVFormat.DEFAULT.isEscapeCharacterSet());
        assertFalse(CSVFormat.DEFAULT.isNullStringSet());
        assertTrue(CSVFormat.DEFAULT.isQuoteCharacterSet());
    }

    // Covers Predefined enum getFormat(): returns the referenced constant format
    @Test
    public void testPredefined_getFormat_matchesConstants() throws Throwable {
        assertSame(CSVFormat.DEFAULT, CSVFormat.Predefined.Default.getFormat());
        assertSame(CSVFormat.MYSQL, CSVFormat.Predefined.MySQL.getFormat());
    }
}
