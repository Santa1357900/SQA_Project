package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class CharSequenceTranslatorTest {

    private static class DummyTranslator extends CharSequenceTranslator {
        private final int consumeCount;

        public DummyTranslator(int consumeCount) {
            this.consumeCount = consumeCount;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (consumeCount < 0) {
                throw new IOException("Simulated IO Exception");
            }
            if (consumeCount == 0) {
                return 0;
            }
            int codePoint = Character.codePointAt(input, index);
            out.write(Character.toChars(codePoint));
            return consumeCount;
        }
    }

    @Test
    public void testTranslateNullInputString() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        assertNull(translator.translate((String) null));
    }

    @Test
    public void testTranslateEmptyString() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        assertEquals("", translator.translate(""));
    }

    @Test
    public void testTranslateZeroConsumed() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(0);
        String result = translator.translate("abc");
        assertEquals("abc", result);
    }

    @Test
    public void testTranslatePositiveConsumed() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        String result = translator.translate("abc");
        assertEquals("abc", result);
    }

    @Test
    public void testTranslateSurrogatePair() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        String highLow = "\uD800\uDC00";
        String result = translator.translate(highLow);
        assertEquals(highLow, result);
    }

    @Test
    public void testTranslateWriterNullInput() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        StringWriter writer = new StringWriter();
        translator.translate(null, writer);
        assertEquals("", writer.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateWriterNullWriter() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(1);
        translator.translate("test", null);
    }

    @Test
    public void testTranslateWriterIOException() throws Throwable {
        CharSequenceTranslator translator = new DummyTranslator(-1);
        Writer writer = new StringWriter();
        try {
            translator.translate("test", writer);
            fail("Expected RuntimeException due to IOException");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testWithTranslators() throws Throwable {
        CharSequenceTranslator t1 = new DummyTranslator(1);
        CharSequenceTranslator t2 = new DummyTranslator(1);
        CharSequenceTranslator combined = t1.with(t2);
        assertNotNull(combined);
        assertTrue(combined instanceof AggregateTranslator);
    }

    @Test
    public void testHex() throws Throwable {
        assertEquals("7F", CharSequenceTranslator.hex(127));
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("FFFF", CharSequenceTranslator.hex(65535));
    }
}