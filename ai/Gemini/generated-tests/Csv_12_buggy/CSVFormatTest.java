package org.apache.commons.csv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.Test;

public class CSVFormatTest {

    @Test
    public void testNewFormatValid() throws Throwable {
        CSVFormat format = CSVFormat.newFormat(';');
        assertEquals(';', format.getDelimiter());
        assertNull(format.getQuoteCharacter());
        assertNull(format.getCommentMarker());
        assertNull(format.getEscapeCharacter());
        assertFalse(format.getIgnoreSurroundingSpaces());
        assertFalse(format.getIgnoreEmptyLines());
        assertNull(format.getRecordSeparator());
        assertNull(format.getNullString());
        assertNull(format.getHeader());
        assertFalse(format.getSkipHeaderRecord());
        assertFalse(format.getAllowMissingColumnNames());
    }

    @Test
    public void testNewFormatLineBreakDelimiter() throws Throwable {
        try {
            CSVFormat.newFormat('\n');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("line break"));
        }

        try {
            CSVFormat.newFormat('\r');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("line break"));
        }
    }

    @Test
    public void testPredefinedFormats() throws Throwable {
        assertNotNull(CSVFormat.DEFAULT);
        assertNotNull(CSVFormat.RFC4180);
        assertNotNull(CSVFormat.EXCEL);
        assertNotNull(CSVFormat.TDF);
        assertNotNull(CSVFormat.MYSQL);

        assertEquals(',', CSVFormat.DEFAULT.getDelimiter());
        assertEquals('"', CSVFormat.DEFAULT.getQuoteCharacter().charValue());
        assertTrue(CSVFormat.DEFAULT.getIgnoreEmptyLines());

        assertEquals(',', CSVFormat.RFC4180.getDelimiter());
        assertFalse(CSVFormat.RFC4180.getIgnoreEmptyLines());

        assertEquals('\t', CSVFormat.TDF.getDelimiter());
        assertTrue(CSVFormat.TDF.getIgnoreSurroundingSpaces());

        assertEquals('\t', CSVFormat.MYSQL.getDelimiter());
        assertNull(CSVFormat.MYSQL.getQuoteCharacter());
        assertEquals('\\', CSVFormat.MYSQL.getEscapeCharacter().charValue());
        assertFalse(CSVFormat.MYSQL.getIgnoreEmptyLines());
    }

    @Test
    public void testValidationDelimiterEqualsQuote() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote(',');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationDelimiterEqualsEscape() throws Throwable {
        try {
            CSVFormat.DEFAULT.withEscape(',');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationDelimiterEqualsComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withCommentMarker(',');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationQuoteEqualsComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote('#').withCommentMarker('#');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be the same"));
        }
    }

    @Test
    public void testValidationEscapeEqualsComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withEscape('#').withCommentMarker('#');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be the same"));
        }
    }

    @Test
    public void testValidationNoQuotesModeWithoutEscape() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NONE).withEscape((Character) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No quotes mode set"));
        }
    }

    @Test
    public void testDuplicateHeaderValidation() throws Throwable {
        try {
            CSVFormat.DEFAULT.withHeader("Col1", "Col2", "Col1");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate entry"));
        }
    }

    @Test
    public void testWithMethodsLineBreakChecks() throws Throwable {
        CSVFormat fmt = CSVFormat.DEFAULT;

        try {
            fmt.withCommentMarker('\n');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("line break"));
        }

        try {
            fmt.withEscape('\r');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("line break"));
        }

        try {
            fmt.withQuote('\n');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("line break"));
        }
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        CSVFormat format1 = CSVFormat.DEFAULT.withCommentMarker('#');
        CSVFormat format2 = CSVFormat.DEFAULT.withCommentMarker('#');
        CSVFormat format3 = CSVFormat.DEFAULT.withCommentMarker('!');

        assertEquals(format1, format1);
        assertEquals(format1, format2);
        assertEquals(format1.hashCode(), format2.hashCode());
        assertFalse(format1.equals(format3));
        assertFalse(format1.equals(null));
        assertFalse(format1.equals("SomeString"));

        // Test quoteCharacter null branches
        CSVFormat fNullQuote1 = CSVFormat.DEFAULT.withQuote((Character) null);
        CSVFormat fNullQuote2 = CSVFormat.DEFAULT.withQuote((Character) null);
        CSVFormat fNonNullQuote = CSVFormat.DEFAULT.withQuote('"');
        assertEquals(fNullQuote1, fNullQuote2);
        assertFalse(fNullQuote1.equals(fNonNullQuote));
        assertFalse(fNonNullQuote.equals(fNullQuote1));

        // Test commentMarker null branches
        CSVFormat fNullComment1 = CSVFormat.DEFAULT.withCommentMarker((Character) null);
        CSVFormat fNullComment2 = CSVFormat.DEFAULT.withCommentMarker((Character) null);
        CSVFormat fNonNullComment = CSVFormat.DEFAULT.withCommentMarker('#');
        assertEquals(fNullComment1, fNullComment2);
        assertFalse(fNullComment1.equals(fNonNullComment));
        assertFalse(fNonNullComment.equals(fNullComment1));

        // Test escapeCharacter null branches
        CSVFormat fNullEscape1 = CSVFormat.DEFAULT.withEscape((Character) null);
        CSVFormat fNullEscape2 = CSVFormat.DEFAULT.withEscape((Character) null);
        CSVFormat fNonNullEscape = CSVFormat.DEFAULT.withEscape('\\');
        assertEquals(fNullEscape1, fNullEscape2);
        assertFalse(fNullEscape1.equals(fNonNullEscape));
        assertFalse(fNonNullEscape.equals(fNullEscape1));

        // Test nullString null branches
        CSVFormat fNullStr1 = CSVFormat.DEFAULT.withNullString(null);
        CSVFormat fNullStr2 = CSVFormat.DEFAULT.withNullString(null);
        CSVFormat fNonNullStr = CSVFormat.DEFAULT.withNullString("NULL");
        assertEquals(fNullStr1, fNullStr2);
        assertFalse(fNullStr1.equals(fNonNullStr));
        assertFalse(fNonNullStr.equals(fNullStr1));

        // Test recordSeparator null branches
        CSVFormat fNullRec1 = CSVFormat.DEFAULT.withRecordSeparator((String) null);
        CSVFormat fNullRec2 = CSVFormat.DEFAULT.withRecordSeparator((String) null);
        CSVFormat fNonNullRec = CSVFormat.DEFAULT.withRecordSeparator("\n");
        assertEquals(fNullRec1, fNullRec2);
        assertFalse(fNullRec1.equals(fNonNullRec));
        assertFalse(fNonNullRec.equals(fNullRec1));

        // Test quoteMode branches
        CSVFormat fMode1 = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL);
        CSVFormat fMode2 = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL);
        CSVFormat fMode3 = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.MINIMAL);
        assertEquals(fMode1, fMode2);
        assertFalse(fMode1.equals(fMode3));

        // Test delimiter branch
        CSVFormat fDelim1 = CSVFormat.DEFAULT.withDelimiter(',');
        CSVFormat fDelim2 = CSVFormat.DEFAULT.withDelimiter(';');
        assertFalse(fDelim1.equals(fDelim2));

        // Test ignoreSurroundingSpaces branch
        CSVFormat fSpaces1 = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces(true);
        CSVFormat fSpaces2 = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces(false);
        assertFalse(fSpaces1.equals(fSpaces2));

        // Test ignoreEmptyLines branch
        CSVFormat fEmpty1 = CSVFormat.DEFAULT.withIgnoreEmptyLines(true);
        CSVFormat fEmpty2 = CSVFormat.DEFAULT.withIgnoreEmptyLines(false);
        assertFalse(fEmpty1.equals(fEmpty2));

        // Test skipHeaderRecord branch
        CSVFormat fSkip1 = CSVFormat.DEFAULT.withSkipHeaderRecord(true);
        CSVFormat fSkip2 = CSVFormat.DEFAULT.withSkipHeaderRecord(false);
        assertFalse(fSkip1.equals(fSkip2));
    }

    @Test
    public void testGettersAndStateCheckers() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
                .withCommentMarker('#')
                .withEscape('\\')
                .withNullString("NULL")
                .withQuote('"')
                .withHeader("A", "B");

        assertTrue(format.isCommentMarkerSet());
        assertTrue(format.isEscapeCharacterSet());
        assertTrue(format.isNullStringSet());
        assertTrue(format.isQuoteCharacterSet());

        assertEquals('#', format.getCommentMarker().charValue());
        assertEquals('\\', format.getEscapeCharacter().charValue());
        assertEquals("NULL", format.getNullString());
        assertEquals('"', format.getQuoteCharacter().charValue());
        assertArrayEquals(new String[] { "A", "B" }, format.getHeader());
        assertNotNull(format.toString());
    }

    @Test
    public void testParseAndPrint() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        Reader reader = new StringReader("a,b,c\r\n1,2,3");
        CSVParser parser = format.parse(reader);
        assertNotNull(parser);

        StringWriter writer = new StringWriter();
        CSVPrinter printer = format.print(writer);
        assertNotNull(printer);

        String formatted = format.format("val1", "val2");
        assertNotNull(formatted);
    }
}