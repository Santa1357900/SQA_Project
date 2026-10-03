package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.Reader;

public class CharacterReaderTest {

    @Test
    public void testStringConstructorAndBasicOps() throws Throwable {
        CharacterReader reader = new CharacterReader("teststring");
        assertFalse(reader.isEmpty());
        assertEquals(0, reader.pos());
        assertEquals('t', reader.current());
        assertEquals('t', reader.consume());
        assertEquals(1, reader.pos());
        reader.unconsume();
        assertEquals(0, reader.pos());
        reader.advance();
        assertEquals(1, reader.pos());
        assertEquals("teststring", reader.toString());
    }

    @Test
    public void testReaderConstructorWithValidMark() throws Throwable {
        StringReader sr = new StringReader("abcdef");
        CharacterReader reader = new CharacterReader(sr, 4);
        assertFalse(reader.isEmpty());
        assertEquals('a', reader.consume());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInputConstructor() throws Throwable {
        new CharacterReader((Reader) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMarkNotSupportedReader() throws Throwable {
        Reader unmarkable = new Reader() {
            public int cbuf(char[] cbuf, int off, int len) { return -1; }
            public int read(char[] cbuf, int off, int len) { return -1; }
            public void close() {}
            public boolean markSupported() { return false; }
        };
        new CharacterReader(unmarkable);
    }

    @Test
    public void testMarkAndRewind() throws Throwable {
        CharacterReader reader = new CharacterReader("hello");
        assertEquals('h', reader.consume());
        assertEquals('e', reader.consume());
        reader.mark();
        assertEquals('l', reader.consume());
        reader.rewindToMark();
        assertEquals('l', reader.consume());
    }

    @Test
    public void testNextIndexOfChar() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        assertEquals(1, reader.nextIndexOf('e'));
        assertEquals(-1, reader.nextIndexOf('z'));
    }

    @Test
    public void testNextIndexOfSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        assertEquals(6, reader.nextIndexOf("world"));
        assertEquals(0, reader.nextIndexOf("hello"));
        assertEquals(-1, reader.nextIndexOf("notfound"));
        assertEquals(-1, reader.nextIndexOf("toolongsequencethatdoesnotexist"));
    }

    @Test
    public void testConsumeToChar() throws Throwable {
        CharacterReader reader = new CharacterReader("key=value");
        assertEquals("key", reader.consumeTo('='));
        assertEquals('=', reader.consume());
        assertEquals("value", reader.consumeTo('x')); // consume to end if not found
    }

    @Test
    public void testConsumeToString() throws Throwable {
        CharacterReader reader = new CharacterReader("start_end");
        assertEquals("start", reader.consumeTo("_end"));
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        CharacterReader reader = new CharacterReader("abc;def,ghi");
        char[] delimiters = new char[]{';', ','};
        assertEquals("abc", reader.consumeToAny(delimiters));
        assertEquals(';', reader.consume());
        assertEquals("def", reader.consumeToAnySorted(delimiters));
    }

    @Test
    public void testConsumeSpecialSequences() throws Throwable {
        CharacterReader reader = new CharacterReader("abc123XYZ09-&<");
        assertEquals("abc", reader.consumeLetterSequence());
        assertEquals("123XYZ09", reader.consumeLetterThenDigitSequence()); // Wait, letter sequence stops at digits, then digits. Let's test properly:
        
        CharacterReader r2 = new CharacterReader("abc123XYZ");
        assertEquals("abc", r2.consumeLetterSequence());
        assertEquals("123", r2.consumeDigitSequence());

        CharacterReader r3 = new CharacterReader("123abc");
        assertEquals("123", r3.consumeDigitSequence());

        CharacterReader r4 = new CharacterReader("ABCdef0129abcdefABCDEF");
        assertEquals("ABCdef0129abcdefABCDEF", r4.consumeHexSequence());

        CharacterReader r5 = new CharacterReader("foo&bar");
        assertEquals("foo", r5.consumeData());

        CharacterReader r6 = new CharacterReader("div >");
        assertEquals("div", r6.consumeTagName());
    }

    @Test
    public void testConsumeToEnd() throws Throwable {
        CharacterReader reader = new CharacterReader("remain");
        reader.consume();
        assertEquals("emain", reader.consumeToEnd());
    }

    @Test
    public void testMatchesAndMatchConsume() throws Throwable {
        CharacterReader reader = new CharacterReader("<html>");
        assertTrue(reader.matches('<'));
        assertFalse(reader.matches('x'));
        assertTrue(reader.matches("html"));
        assertFalse(reader.matches("body"));

        assertTrue(reader.matchConsume("<ht"));
        assertEquals('m', reader.current());

        assertTrue(reader.matchConsumeIgnoreCase("ML>"));
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testMatchesAnyAndDigitAndLetter() throws Throwable {
        CharacterReader reader = new CharacterReader("A9_");
        assertTrue(reader.matchesLetter());
        assertTrue(reader.matchesAny('A', 'B'));
        assertTrue(reader.matchesAnySorted(new char[]{'A', 'B'}));
        reader.advance();
        assertTrue(reader.matchesDigit());
        reader.advance();
        assertFalse(reader.matchesLetter());
        assertFalse(reader.matchesDigit());
        assertFalse(reader.matchesAny('9'));
    }

    @Test
    public void testContainsIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("Hello </TITLE> World");
        assertTrue(reader.containsIgnoreCase("</title>"));
    }

    @Test
    public void testCacheStringAndRangeEquals() throws Throwable {
        CharacterReader reader = new CharacterReader("abcdefghijklmnopqrstuvwxyz");
        // Test cache string logic with short and long strings (> maxStringCacheLen which is 12)
        String shortStr = reader.consumeTo('j');
        assertEquals("abcdefghi", shortStr);
        
        // consume a long string > 12 chars
        String longStr = reader.consumeToEnd();
        assertEquals("jklmnopqrstuvwxyz", longStr);

        assertTrue(reader.rangeEquals(0, 3, "abc"));
        assertFalse(reader.rangeEquals(0, 3, "xyz"));
        assertFalse(reader.rangeEquals(0, 3, "abcd")); // different length
    }
}