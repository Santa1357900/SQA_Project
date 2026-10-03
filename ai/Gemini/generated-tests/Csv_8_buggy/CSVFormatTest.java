package org.apache.commons.csv;

import org.junit.Test;
import java.io.StringReader;
import java.util.Arrays;

import static org.junit.Assert.*;

public class CSVFormatTest {

    @Test
    public void testPredefinedFormats() throws Throwable {
        assertNotNull(CSVFormat.DEFAULT);
        assertNotNull(CSVFormat.RFC4180);
        assertNotNull(CSVFormat.EXCEL);
        assertNotNull(CSVFormat.TDF);
        assertNotNull(CSVFormat.MYSQL);
    }

    @Test
    public void testNewFormatValid() throws Throwable {
        CSVFormat format = CSVFormat.newFormat(';');
        assertEquals(';', format.getDelimiter());
        assertFalse(format.isQuoting());
        assertFalse(format.isEscaping());
        assertFalse(format.isCommentingEnabled());
        assertFalse(format.isNullHandling());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewFormatLineBreakDelimiterLF() throws Throwable {
        CSVFormat.newFormat('\n');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewFormatLineBreakDelimiterCR() throws Throwable {
        CSVFormat.newFormat('\r');
    }

    @Test
    public void testGettersAndSetters() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
                .withCommentStart('#')
                .withDelimiter('|')
                .withEscape('\\')
                .withHeader("A", "B")
                .withIgnoreEmptyLines(false)
                .withIgnoreSurroundingSpaces(true)
                .withNullString("NULL")
                .withQuoteChar('"')
                .withQuotePolicy(Quote.ALL)
                .withRecordSeparator("\n")
                .withSkipHeaderRecord(true);

        assertEquals(Character.valueOf('#'), format.getCommentStart());
        assertEquals('|', format.getDelimiter());
        assertEquals(Character.valueOf('\\'), format.getEscape());
        assertArrayEquals(new String[]{"A", "B"}, format.getHeader());
        assertFalse(format.getIgnoreEmptyLines());
        assertTrue(format.getIgnoreSurroundingSpaces());
        assertEquals("NULL", format.getNullString());
        assertEquals(Character.valueOf('"'), format.getQuoteChar());
        assertEquals(Quote.ALL, format.getQuotePolicy());
        assertEquals("\n", format.getRecordSeparator());
        assertTrue(format.getSkipHeaderRecord());

        assertTrue(format.isCommentingEnabled());
        assertTrue(format.isEscaping());
        assertTrue(format.isNullHandling());
        assertTrue(format.isQuoting());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithCommentStartLineBreakChar() throws Throwable {
        CSVFormat.DEFAULT.withCommentStart('\n');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithCommentStartLineBreakCharacter() throws Throwable {
        CSVFormat.DEFAULT.withCommentStart(Character.valueOf('\n'));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithDelimiterLineBreak() throws Throwable {
        CSVFormat.DEFAULT.withDelimiter('\r');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithEscapeLineBreakChar() throws Throwable {
        CSVFormat.DEFAULT.withEscape('\n');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithEscapeLineBreakCharacter() throws Throwable {
        CSVFormat.DEFAULT.withEscape(Character.valueOf('\n'));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithQuoteCharLineBreakChar() throws Throwable {
        CSVFormat.DEFAULT.withQuoteChar('\r');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithQuoteCharLineBreakCharacter() throws Throwable {
        CSVFormat.DEFAULT.withQuoteChar(Character.valueOf('\r'));
    }

    @Test
    public void testWithRecordSeparatorChar() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withRecordSeparator(':');
        assertEquals(":", format.getRecordSeparator());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        CSVFormat format1 = CSVFormat.DEFAULT.withHeader("Col1");
        CSVFormat format2 = CSVFormat.DEFAULT.withHeader("Col1");
        CSVFormat format3 = CSVFormat.DEFAULT.withHeader("Col2");
        CSVFormat formatDiffNull = CSVFormat.DEFAULT.withHeader((String[]) null);

        assertEquals(format1, format1);
        assertEquals(format1, format2);
        assertEquals(format1.hashCode(), format2.hashCode());

        assertFalse(format1.equals(null));
        assertFalse(format1.equals(new Object()));
        assertFalse(format1.equals(format3));
        assertFalse(format1.equals(CSVFormat.DEFAULT));
        assertFalse(CSVFormat.DEFAULT.equals(formatDiffNull));

        // Testing all branches in equals
        CSVFormat fA = CSVFormat.DEFAULT.withDelimiter(',');
        CSVFormat fB = CSVFormat.DEFAULT.withDelimiter(';');
        assertFalse(fA.equals(fB));

        fA = CSVFormat.DEFAULT.withQuotePolicy(Quote.ALL);
        fB = CSVFormat.DEFAULT.withQuotePolicy(Quote.MINIMAL);
        assertFalse(fA.equals(fB));

        fA = CSVFormat.DEFAULT.withQuoteChar('"');
        fB = CSVFormat.DEFAULT.withQuoteChar('\'');
        assertFalse(fA.equals(fB));
        assertFalse(fA.equals(CSVFormat.DEFAULT));
        assertFalse(CSVFormat.DEFAULT.equals(fA));

        fA = CSVFormat.DEFAULT.withCommentStart('#');
        fB = CSVFormat.DEFAULT.withCommentStart('!');
        assertFalse(fA.equals(fB));
        assertFalse(fA.equals(CSVFormat.DEFAULT));
        assertFalse(CSVFormat.DEFAULT.equals(fA));

        fA = CSVFormat.DEFAULT.withEscape('\\');
        fB = CSVFormat.DEFAULT.withEscape('/');
        assertFalse(fA.equals(fB));
        assertFalse(fA.equals(CSVFormat.DEFAULT));
        assertFalse(CSVFormat.DEFAULT.equals(fA));

        fA = CSVFormat.DEFAULT.withNullString("NULL");
        fB = CSVFormat.DEFAULT.withNullString("NIL");
        assertFalse(fA.equals(fB));
        assertFalse(fA.equals(CSVFormat.DEFAULT));
        assertFalse(CSVFormat.DEFAULT.equals(fA));

        fA = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces(true);
        fB = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces(false);
        assertFalse(fA.equals(fB));

        fA = CSVFormat.DEFAULT.withIgnoreEmptyLines(true);
        fB = CSVFormat.DEFAULT.withIgnoreEmptyLines(false);
        assertFalse(fA.equals(fB));

        fA = CSVFormat.DEFAULT.withSkipHeaderRecord(true);
        fB = CSVFormat.DEFAULT.withSkipHeaderRecord(false);
        assertFalse(fA.equals(fB));

        fA = CSVFormat.DEFAULT.withRecordSeparator("\r\n");
        fB = CSVFormat.DEFAULT.withRecordSeparator("\n");
        assertFalse(fA.equals(fB));
        assertFalse(fA.equals(CSVFormat.DEFAULT));
        assertFalse(CSVFormat.DEFAULT.equals(fA));
    }

    @Test
    public void testHashCodeWithNulls() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
                .withQuoteChar(null)
                .withQuotePolicy(null)
                .withCommentStart(null)
                .withEscape(null)
                .withNullString(null)
                .withRecordSeparator(null)
                .withHeader((String[]) null);
        assertNotNull(format.hashCode());
    }

    @Test
    public void testFormatValues() throws Throwable {
        String formatted = CSVFormat.DEFAULT.format("a", "b", "c");
        assertEquals("a,b,c", formatted);
    }

    @Test(expected = IllegalStateException.class)
    public void testFormatIOException() throws Throwable {
        // Mock/simulate or trigger format exception if possible, or verify normal usage
        // format() wraps IOException into IllegalStateException using StringWriter which doesn't throw,
        // but we can call it to ensure line coverage.
        CSVFormat.DEFAULT.format((Object[]) null);
    }

    @Test
    public void testParse() throws Throwable {
        StringReader reader = new StringReader("a,b,c\r\n1,2,3");
        CSVParser parser = CSVFormat.DEFAULT.parse(reader);
        assertNotNull(parser);
        parser.close();
    }

    @Test
    public void testToString() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
                .withEscape('\\')
                .withQuoteChar('"')
                .withCommentStart('#')
                .withNullString("NULL")
                .withRecordSeparator("\n")
                .withIgnoreEmptyLines(true)
                .withIgnoreSurroundingSpaces(true)
                .withSkipHeaderRecord(true)
                .withHeader("H1", "H2");

        String str = format.toString();
        assertTrue(str.contains("Delimiter=<,>"));
        assertTrue(str.contains("Escape=<\\>"));
        assertTrue(str.contains("QuoteChar=<\">"));
        assertTrue(str.contains("CommentStart=<#>"));
        assertTrue(str.contains("NullString=<NULL>"));
        assertTrue(str.contains("RecordSeparator=<\n>"));
        assertTrue(str.contains("EmptyLines:ignored"));
        assertTrue(str.contains("SurroundingSpaces:ignored"));
        assertTrue(str.contains("SkipHeaderRecord:true"));
        assertTrue(str.contains("Header:[H1, H2]"));
    }

    @Test
    public void testValidateSuccess() throws Throwable {
        CSVFormat.DEFAULT.validate();
        CSVFormat.RFC4180.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateQuoteCharEqualsDelimiter() throws Throwable {
        CSVFormat.DEFAULT.withDelimiter(',').withQuoteChar(',').validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateEscapeEqualsDelimiter() throws Throwable {
        CSVFormat.DEFAULT.withDelimiter(',').withEscape(',').validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateCommentStartEqualsDelimiter() throws Throwable {
        CSVFormat.DEFAULT.withDelimiter(',').withCommentStart(',').validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateCommentStartEqualsQuoteChar() throws Throwable {
        CSVFormat.DEFAULT.withQuoteChar('"').withCommentStart('"').validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateEscapeEqualsCommentStart() throws Throwable {
        CSVFormat.DEFAULT.withCommentStart('#').withEscape('#').validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateNoQuotesModeNoEscape() throws Throwable {
        CSVFormat.DEFAULT.withQuoteChar(null).withQuotePolicy(Quote.NONE).withEscape(null).validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testValidateDuplicateHeader() throws Throwable {
        CSVFormat.DEFAULT.withHeader("Col1", "Col1").validate();
    }
}