package org.apache.commons.csv;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

public class CSVFormatTest {

    @Test
    public void testPredefinedFormats() throws Throwable {
        assertNotNull(CSVFormat.DEFAULT);
        assertNotNull(CSVFormat.EXCEL);
        assertNotNull(CSVFormat.INFORMIX_UNLOAD);
        assertNotNull(CSVFormat.INFORMIX_UNLOAD_CSV);
        assertNotNull(CSVFormat.MYSQL);
        assertNotNull(CSVFormat.RFC4180);
        assertNotNull(CSVFormat.TDF);

        assertEquals(CSVFormat.DEFAULT, CSVFormat.valueOf("Default"));
        assertEquals(CSVFormat.EXCEL, CSVFormat.valueOf("Excel"));
        assertEquals(CSVFormat.INFORMIX_UNLOAD, CSVFormat.valueOf("InformixUnload"));
        assertEquals(CSVFormat.INFORMIX_UNLOAD_CSV, CSVFormat.valueOf("InformixUnloadCsv"));
        assertEquals(CSVFormat.MYSQL, CSVFormat.valueOf("MySQL"));
        assertEquals(CSVFormat.RFC4180, CSVFormat.valueOf("RFC4180"));
        assertEquals(CSVFormat.TDF, CSVFormat.valueOf("TDF"));
    }

    @Test
    public void testNewFormatValid() throws Throwable {
        CSVFormat format = CSVFormat.newFormat(';');
        assertEquals(';', format.getDelimiter());
        assertFalse(format.isQuoteCharacterSet());
        assertFalse(format.isCommentMarkerSet());
        assertFalse(format.isEscapeCharacterSet());
        assertFalse(format.isNullStringSet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewFormatInvalidDelimiterLF() throws Throwable {
        CSVFormat.newFormat('\n');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewFormatInvalidDelimiterCR() throws Throwable {
        CSVFormat.newFormat('\r');
    }

    @Test
    public void testValidationDuplicateHeader() throws Throwable {
        try {
            CSVFormat.DEFAULT.withHeader("col1", "col2", "col1");
            fail("Should have thrown IllegalArgumentException for duplicate header");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate entry"));
        }
    }

    @Test
    public void testValidationSameDelimiterAndQuote() throws Throwable {
        try {
            CSVFormat.newFormat(',').withQuote(',');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("quoteChar character and the delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationSameDelimiterAndEscape() throws Throwable {
        try {
            CSVFormat.newFormat(',').withEscape(',');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("escape character and the delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationSameDelimiterAndComment() throws Throwable {
        try {
            CSVFormat.newFormat('#').withCommentMarker('#');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("comment start character and the delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationSameQuoteAndComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote('"').withCommentMarker('"');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("comment start character and the quoteChar cannot be the same"));
        }
    }

    @Test
    public void testValidationSameEscapeAndComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withEscape('\\').withCommentMarker('\\');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("comment start and the escape character cannot be the same"));
        }
    }

    @Test
    public void testValidationNoQuotesModeWithoutEscape() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote(null).withQuoteMode(QuoteMode.NONE);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No quotes mode set but no escape character is set"));
        }
    }

    @Test
    public void testWithMethods() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
                .withAllowMissingColumnNames(true)
                .withCommentMarker('#')
                .withDelimiter('|')
                .withEscape('\\')
                .withFirstRecordAsHeader()
                .withHeaderComments("Comment 1")
                .withIgnoreEmptyLines(true)
                .withIgnoreHeaderCase(true)
                .withIgnoreSurroundingSpaces(true)
                .withNullString("NULL")
                .withQuote('"')
                .withQuoteMode(QuoteMode.ALL)
                .withRecordSeparator("\n")
                .withSkipHeaderRecord(true)
                .withTrailingDelimiter(true)
                .withTrim(true);

        assertTrue(format.getAllowMissingColumnNames());
        assertEquals(Character.valueOf('#'), format.getCommentMarker());
        assertEquals('|', format.getDelimiter());
        assertEquals(Character.valueOf('\\'), format.getEscapeCharacter());
        assertNotNull(format.getHeaderComments());
        assertTrue(format.getIgnoreEmptyLines());
        assertTrue(format.getIgnoreHeaderCase());
        assertTrue(format.getIgnoreSurroundingSpaces());
        assertEquals("NULL", format.getNullString());
        assertEquals(Character.valueOf('"'), format.getQuoteCharacter());
        assertEquals(QuoteMode.ALL, format.getQuoteMode());
        assertEquals("\n", format.getRecordSeparator());
        assertTrue(format.getSkipHeaderRecord());
        assertTrue(format.getTrailingDelimiter());
        assertTrue(format.getTrim());
        assertTrue(format.isCommentMarkerSet());
        assertTrue(format.isEscapeCharacterSet());
        assertTrue(format.isNullStringSet());
        assertTrue(format.isQuoteCharacterSet());
    }

    @Test
    public void testWithHeaderEnum() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader(DummyEnum.class);
        assertNotNull(format.getHeader());
        assertEquals(2, format.getHeader().length);
        assertEquals("VAL1", format.getHeader()[0]);
        assertEquals("VAL2", format.getHeader()[1]);
    }

    private enum DummyEnum {
        VAL1, VAL2
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        CSVFormat f1 = CSVFormat.DEFAULT.withDelimiter(',');
        CSVFormat f2 = CSVFormat.DEFAULT.withDelimiter(',');
        CSVFormat f3 = CSVFormat.DEFAULT.withDelimiter(';');
        Object notFormat = new Object();

        assertTrue(f1.equals(f1));
        assertTrue(f1.equals(f2));
        assertEquals(f1.hashCode(), f2.hashCode());

        assertFalse(f1.equals(null));
        assertFalse(f1.equals(notFormat));
        assertFalse(f1.equals(f3));

        CSVFormat f4 = CSVFormat.DEFAULT.withQuote(null);
        CSVFormat f5 = CSVFormat.DEFAULT.withQuote('"');
        assertFalse(f4.equals(f5));
        assertFalse(f5.equals(f4));

        CSVFormat f6 = CSVFormat.DEFAULT.withCommentMarker(null);
        CSVFormat f7 = CSVFormat.DEFAULT.withCommentMarker('#');
        assertFalse(f6.equals(f7));
        assertFalse(f7.equals(f6));

        CSVFormat f8 = CSVFormat.DEFAULT.withEscape(null);
        CSVFormat f9 = CSVFormat.DEFAULT.withEscape('\\');
        assertFalse(f8.equals(f9));
        assertFalse(f9.equals(f8));

        CSVFormat f10 = CSVFormat.DEFAULT.withNullString(null);
        CSVFormat f11 = CSVFormat.DEFAULT.withNullString("N/A");
        assertFalse(f10.equals(f11));
        assertFalse(f11.equals(f10));

        CSVFormat f12 = CSVFormat.DEFAULT.withRecordSeparator(null);
        CSVFormat f13 = CSVFormat.DEFAULT.withRecordSeparator("\n");
        assertFalse(f12.equals(f13));
        assertFalse(f13.equals(f12));
    }

    @Test
    public void testToString() throws Throwable {
        String str = CSVFormat.DEFAULT.toString();
        assertNotNull(str);
        assertTrue(str.contains("Delimiter=<,>"));

        CSVFormat complex = CSVFormat.DEFAULT
                .withEscape('\\')
                .withQuote('"')
                .withCommentMarker('#')
                .withNullString("NULL")
                .withRecordSeparator("\n")
                .withIgnoreEmptyLines()
                .withIgnoreSurroundingSpaces()
                .withIgnoreHeaderCase()
                .withHeaderComments("H1")
                .withHeader("C1");
        
        String complexStr = complex.toString();
        assertTrue(complexStr.contains("Escape=<\\>"));
        assertTrue(complexStr.contains("QuoteChar=<\">"));
        assertTrue(complexStr.contains("CommentStart=<#>"));
        assertTrue(complexStr.contains("NullString=<NULL>"));
        assertTrue(complexStr.contains("RecordSeparator=<\n>"));
        assertTrue(complexStr.contains("EmptyLines:ignored"));
        assertTrue(complexStr.contains("SurroundingSpaces:ignored"));
        assertTrue(complexStr.contains("IgnoreHeaderCase:ignored"));
    }

    @Test
    public void testFormatAndPrint() throws Throwable {
        String formatted = CSVFormat.DEFAULT.format("a", "b", null);
        assertNotNull(formatted);

        StringWriter writer = new StringWriter();
        CSVPrinter printer = CSVFormat.DEFAULT.print(writer);
        assertNotNull(printer);
        printer.close();

        CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader("a,b"));
        assertNotNull(parser);
        parser.close();
    }

    @Test
    public void testPrintObjectVariations() throws Throwable {
        // Test quoting modes and print with escape/quote/none
        CSVFormat fmtEscape = CSVFormat.DEFAULT.withEscape('\\').withQuote(null);
        String res1 = fmtEscape.format("a\nb", "c,d");
        assertNotNull(res1);

        CSVFormat fmtQuoteAll = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL);
        String res2 = fmtQuoteAll.format("abc");
        assertNotNull(res2);

        CSVFormat fmtQuoteNonNumeric = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NON_NUMERIC);
        String res3 = fmtQuoteNonNumeric.format("abc", Integer.valueOf(123));
        assertNotNull(res3);

        CSVFormat fmtQuoteNone = CSVFormat.DEFAULT.withEscape('\\').withQuoteMode(QuoteMode.NONE);
        String res4 = fmtQuoteNone.format("abc");
        assertNotNull(res4);

        CSVFormat fmtMinimalEmpty = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.MINIMAL);
        String res5 = fmtMinimalEmpty.format("", "b");
        assertNotNull(res5);
    }
}