package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.Reader;
import java.io.IOException;

public class CharacterReaderTest {

    @Test
    public void testConstructorsAndBasics() throws Throwable {
        CharacterReader r1 = new CharacterReader("abcdef");
        assertFalse(r1.isEmpty());
        assertEquals('a', r1.current());
        assertEquals(0, r1.pos());

        CharacterReader r2 = new CharacterReader(new StringReader("test"), 10);
        assertFalse(r2.isEmpty());
        assertEquals('t', r2.current());

        CharacterReader r3 = new CharacterReader(new StringReader("test"));
        assertFalse(r3.isEmpty());
        assertEquals('t', r3.current());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() throws Throwable {
        new CharacterReader((Reader) null);
    }

    @Test
    public void testConsumeAndAdvance() throws Throwable {
        CharacterReader reader = new CharacterReader("hello");
        assertEquals('h', reader.consume());
        assertEquals(1, reader.pos());
        reader.advance();
        assertEquals('l', reader.current());
        assertEquals(2, reader.pos());

        reader.unconsume();
        assertEquals('e', reader.current());
    }

    @Test
    public void testMarkAndRewind() throws Throwable {
        CharacterReader reader = new CharacterReader("abcdef");
        assertEquals('a', reader.consume());
        assertEquals('b', reader.consume());
        reader.mark();
        assertEquals('c', reader.consume());
        assertEquals('d', reader.consume());
        reader.rewindToMark();
        assertEquals('c', reader.current());
    }

    @Test
    public void testNextIndexOfChar() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        assertEquals(4, reader.nextIndexOf('o'));
        assertEquals(-1, reader.nextIndexOf('z'));
    }

    @Test
    public void testNextIndexOfSeq() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        assertEquals(6, reader.nextIndexOf("world"));
        assertEquals(0, reader.nextIndexOf("hello"));
        assertEquals(-1, reader.nextIndexOf("notfound"));
        assertEquals(-1, reader.nextIndexOf("toolongsequencethatisnotpresenthere"));
    }

    @Test
    public void testConsumeToChar() throws Throwable {
        CharacterReader reader = new CharacterReader("hello:world");
        assertEquals("hello", reader.consumeTo(':'));
        assertEquals(':', reader.consume());
        assertEquals("world", reader.consumeTo('x'));
    }

    @Test
    public void testConsumeToSeq() throws Throwable {
        CharacterReader reader = new CharacterReader("hello--world");
        assertEquals("hello", reader.consumeTo("--"));
        assertEquals("--", reader.consumeTo("world"));
        assertEquals("world", reader.consumeTo("missing"));
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        CharacterReader reader = new CharacterReader("hello, world!");
        char[] delimiters = new char[]{',', '!'};
        assertEquals("hello", reader.consumeToAny(delimiters));
        assertEquals(',', reader.consume());
        assertEquals(" world", reader.consumeToAny(delimiters));
    }

    @Test
    public void testConsumeToAnySorted() throws Throwable {
        CharacterReader reader = new CharacterReader("abc123xyz");
        char[] sorted = new char[]{'1', '2', '3'};
        assertEquals("abc", reader.consumeToAnySorted(sorted));
        assertEquals("123", reader.consumeToAnySorted(new char[]{'x', 'y', 'z'}));
    }

    @Test
    public void testConsumeData() throws Throwable {
        CharacterReader reader = new CharacterReader("abc&def<ghi");
        assertEquals("abc", reader.consumeData());
        assertEquals('&', reader.consume());
        assertEquals("def", reader.consumeData());
        assertEquals('<', reader.consume());
        assertEquals("ghi", reader.consumeData());
    }

    @Test
    public void testConsumeTagName() throws Throwable {
        CharacterReader reader = new CharacterReader("div id=\"foo\"");
        assertEquals("div", reader.consumeTagName());
        assertEquals(' ', reader.consume());
        assertEquals("id=\"foo\"", reader.consumeToEnd());
    }

    @Test
    public void testConsumeSequences() throws Throwable {
        CharacterReader reader = new CharacterReader("ABC123xyz_456ABCdefABCDEF0123456789ABC");
        assertEquals("ABC123xyz", reader.consumeLetterThenDigitSequence());
        
        CharacterReader reader2 = new CharacterReader("abcXYZ");
        assertEquals("abcXYZ", reader2.consumeLetterSequence());

        CharacterReader reader3 = new CharacterReader("12345abc");
        assertEquals("12345", reader3.consumeDigitSequence());

        CharacterReader reader4 = new CharacterReader("A1b2F3z_");
        assertEquals("A1b2F3", reader4.consumeHexSequence());
    }

    @Test
    public void testMatches() throws Throwable {
        CharacterReader reader = new CharacterReader("TestString");
        assertTrue(reader.matches('T'));
        assertFalse(reader.matches('t'));
        assertTrue(reader.matches("Test"));
        assertFalse(reader.matches("Bad"));
        assertFalse(reader.matches("TestStringLonger"));
    }

    @Test
    public void testMatchesIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("TestString");
        assertTrue(reader.matchesIgnoreCase("test"));
        assertTrue(reader.matchesIgnoreCase("TEST"));
        assertFalse(reader.matchesIgnoreCase("bad"));
        assertFalse(reader.matchesIgnoreCase("TestStringLonger"));
    }

    @Test
    public void testMatchesAny() throws Throwable {
        CharacterReader reader = new CharacterReader("TestString");
        assertTrue(reader.matchesAny('a', 'T', 'b'));
        assertFalse(reader.matchesAny('x', 'y'));
        
        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesAny('T'));
    }

    @Test
    public void testMatchesAnySorted() throws Throwable {
        CharacterReader reader = new CharacterReader("M123");
        char[] sorted = new char[]{'A', 'B', 'M'};
        assertTrue(reader.matchesAnySorted(sorted));
        assertFalse(new CharacterReader("Z123").matchesAnySorted(sorted));
        assertFalse(new CharacterReader("").matchesAnySorted(sorted));
    }

    @Test
    public void testMatchesLetterAndDigit() throws Throwable {
        CharacterReader reader = new CharacterReader("A1");
        assertTrue(reader.matchesLetter());
        assertFalse(new CharacterReader("1A").matchesLetter());
        assertFalse(new CharacterReader("").matchesLetter());

        reader.advance();
        assertTrue(reader.matchesDigit());
        assertFalse(new CharacterReader("A1").matchesDigit());
        assertFalse(new CharacterReader("").matchesDigit());
    }

    @Test
    public void testMatchConsume() throws Throwable {
        CharacterReader reader = new CharacterReader("HelloWorld");
        assertTrue(reader.matchConsume("Hello"));
        assertEquals('W', reader.current());
        assertFalse(reader.matchConsume("Bad"));

        assertTrue(reader.matchConsumeIgnoreCase("world"));
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testContainsIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("Hello</TITLE>World");
        assertTrue(reader.containsIgnoreCase("</title>"));
        assertFalse(reader.containsIgnoreCase("missing"));
    }

    @Test
    public void testToStringAndCaching() throws Throwable {
        CharacterReader reader = new CharacterReader("Short string and a very long string that exceeds cache length limit.");
        assertEquals("Short string and a very long string that exceeds cache length limit.", reader.toString());

        // Test string caching branches (short string vs long string)
        String s1 = reader.consumeTo(' ');
        String s2 = reader.consumeTo(' ');
        assertNotNull(s1);
        assertNotNull(s2);

        // Test range equals
        assertTrue(reader.rangeEquals(0, 5, "Short"));
        assertFalse(reader.rangeEquals(0, 5, "NotMatch"));
        assertFalse(reader.rangeEquals(0, 100, "Short"));
    }

    @Test
    public void testBufferUpEdgeCases() throws Throwable {
        // Reader with mark not supported should fail
        Reader unmarkable = new Reader() {
            public int cbufPos = 0;
            public String data = "test";
            public int read(char[] cbuf, int off, int len) throws IOException {
                if (cbufPos >= data.length()) return -1;
                cbuf[off] = data.charAt(cbufPos++);
                return 1;
            }
            public void close() throws IOException {}
            public boolean markSupported() { return false; }
        };

        try {
            new CharacterReader(unmarkable);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}