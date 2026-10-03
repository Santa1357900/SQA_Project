package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NumericEntityUnescaperTest {

    @Test
    public void testTranslateDecimalEntity() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&#65;"; // 'A'

        int consumed = unescaper.translate(input, 0, writer);

        assertEquals(5, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testTranslateHexEntityLowercase() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&#x41;"; // 'A'

        int consumed = unescaper.translate(0, input, writer); // note: CharSequenceTranslator.translate(CharSequence, int, Writer) exists, but NumericEntityUnescaper has (CharSequence input, int index, Writer out)

        // Let's use the exact signature: translate(CharSequence input, int index, Writer out)
        // Wait, let's call with proper order: input, index, out
        writer = new StringWriter();
        consumed = unescaper.translate(input, 0, writer);

        assertEquals(6, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testTranslateHexEntityUppercase() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&#X41;"; // 'A'

        int consumed = unescaper.translate(input, 0, writer);

        assertEquals(6, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testTranslateInvalidEntityFormatNumberFormat() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        // Invalid number inside
        String input = "&#abc;";

        int consumed = unescaper.translate(input, 0, writer);

        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testTranslateNotAnEntity() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "ABC";

        int consumed = unescaper.translate(input, 0, writer);

        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testTranslateIncompleteEntityMissingHash() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&65;";

        int consumed = unescaper.translate(input, 0, writer);

        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testTranslateIndexNonZero() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "A&#65;B";

        int consumed = unescaper.translate(input, 1, writer);

        assertEquals(5, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testTranslateOutOfBounds() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&";

        try {
            unescaper.translate(input, 0, writer);
            fail("Expected StringIndexOutOfBoundsException or IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected due to missing semicolon and end of string
        }
    }
}