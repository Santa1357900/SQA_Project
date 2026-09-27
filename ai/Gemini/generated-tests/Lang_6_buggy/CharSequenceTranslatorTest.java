package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

public class CharSequenceTranslatorTest {

    private static class DummyTranslator extends CharSequenceTranslator {
        private final int consumeCount;

        public DummyTranslator(int consumeCount) {
            this.consumeCount = consumeCount;
        }

        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (index >= input.length()) {
                return 0;
            }
            if (consumeCount < 0) {
                return 0;
            }
            int codePoint = Character.codePointAt(input, index);
            out.write(Integer.toHexString(codePoint));
            return consumeCount;
        }
    }

    private static class ExceptionWriter extends Writer {
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("Simulated IO Exception");
        }

        public void flush() throws IOException {
            throw new IOException("Simulated IO Exception");
        }

        public void close() throws IOException {
            throw new IOException("Simulated IO Exception");
        }
    }

    @Test
    public void testTranslateNullInput() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        assertNull(translator.translate(null));
    }

    @Test
    public void testTranslateNullWriter() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        try {
            translator.translate("test", (Writer) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Writer must not be null"));
        }
    }

    @Test
    public void testTranslateStringNullInputToWriter() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        StringWriter writer = new StringWriter();
        translator.translate(null, writer);
        assertEquals("", writer.toString());
    }

    @Test
    public void testTranslateStringZeroConsumption() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(0);
        String result = translator.translate("a");
        assertEquals("61", result);
    }

    @Test
    public void testTranslateStringPositiveConsumption() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        String result = translator.translate("ab");
        assertEquals("6162", result);
    }

    @Test
    public void testTranslateSurrogatePairConsumption() throws Throwable {
        // Character.toCodePoint('\uD800', '\uDC00') is a surrogate pair (codepoint 0x10000)
        String s = "\uD800\uDC00";
        CharSequenceTranslator translator = new DummyTranslator(1);
        StringWriter writer = new StringWriter();
        translator.translate(s, writer);
        // codePointAt returns 0x10000 for index 0, charCount is 2. 
        // DummyTranslator writes hex of codePoint (10000) and returns 1.
        // loop pt=0 to <1, pos increases by charCount of codePoint at index 0 which is 2.
        assertEquals("10000", writer.toString());
    }

    @Test
    public void testTranslateWithIoExceptionWrapped() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(0);
        Writer writer = new ExceptionWriter();
        try {
            translator.translate("test", writer);
            fail("Should have thrown RuntimeException wrapping IOException");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testWithMethod() throws Throwable {
        CharSequenceTranslator t1 = new DummyTranslator(1);
        CharSequenceTranslator t2 = new DummyTranslator(1);
        CharSequenceTranslator merged = t1.with(t2);
        assertNotNull(merged);
        assertTrue(merged instanceof AggregateTranslator);
    }

    @Test
    public void testHexMethod() throws Throwable {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("A", CharSequenceTranslator.hex(10));
        assertEquals("7F", CharSequenceTranslator.hex(127));
        assertEquals("10FFFF", CharSequenceTranslator.hex(1114111));
    }
}