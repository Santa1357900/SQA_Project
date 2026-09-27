package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

public class LookupTranslatorTest {

    @Test
    public void testTranslateBasic() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {
            {"one", "1"},
            {"two", "2"}
        };
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("one", 0, out);
        assertEquals(3, consumed);
        assertEquals("1", out.toString());
    }

    @Test
    public void testTranslateGreedy() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {
            {"a", "A"},
            {"ab", "AB"},
            {"abc", "ABC"}
        };
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("abcd", 0, out);
        assertEquals(3, consumed);
        assertEquals("ABC", out.toString());
    }

    @Test
    public void testTranslateNoMatch() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {
            {"one", "1"}
        };
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("two", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateWithOffset() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {
            {"two", "2"}
        };
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("onetwothree", 3, out);
        assertEquals(3, consumed);
        assertEquals("2", out.toString());
    }

    @Test
    public void testNullLookup() throws Throwable {
        final LookupTranslator translator = new LookupTranslator((CharSequence[][]) null);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("test", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test(expected = NullPointerException.class)
    public void testNullInput() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {
            {"one", "1"}
        };
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        translator.translate(null, 0, out);
    }

    @Test
    public void testEmptyLookup() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {};
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("test", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testIndexPlusLongestExceedsLength() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {
            {"longerthaninput", "value"}
        };
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("short", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateSubSequenceMatch() throws Throwable {
        final CharSequence[][] lookup = new CharSequence[][] {
            {"b", "B"}
        };
        final LookupTranslator translator = new LookupTranslator(lookup);
        final StringWriter out = new StringWriter();
        
        final int consumed = translator.translate("abc", 1, out);
        assertEquals(1, consumed);
        assertEquals("B", out.toString());
    }
}