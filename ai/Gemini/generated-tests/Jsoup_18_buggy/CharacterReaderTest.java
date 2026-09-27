package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class CharacterReaderTest {

    @Test
    public void testConstructorNotNull() throws Throwable {
        CharacterReader reader = new CharacterReader("test");
        assertNotNull(reader);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullInput() throws Throwable {
        new CharacterReader(null);
    }

    @Test
    public void testPosAndIsEmpty() throws Throwable {
        CharacterReader reader = new CharacterReader("a");
        assertEquals(0, reader.pos());
        assertFalse(reader.isEmpty());
        
        reader.consume();
        assertEquals(1, reader.pos());
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testCurrentAndConsumeAndUnconsume() throws Throwable {
        CharacterReader reader = new CharacterReader("ab");
        assertEquals('a', reader.current());
        assertEquals('a', reader.consume());
        assertEquals('b', reader.current());
        
        reader.unconsume();
        assertEquals('b', reader.current());
        
        reader.advance();
        assertEquals('b', reader.consume());
        assertEquals(CharacterReader.EOF, reader.current());
        assertEquals(CharacterReader.EOF, reader.consume());
    }

    @Test
    public void testMarkAndRewind() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertEquals('a', reader.consume());
        reader.mark();
        assertEquals('b', reader.consume());
        assertEquals('c', reader.current());
        
        reader.rewindToMark();
        assertEquals('b', reader.current());
    }

    @Test
    public void testConsumeAsString() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        String s = reader.consumeAsString();
        assertEquals("a", s);
        assertEquals(1, reader.pos());
    }

    @Test
    public void testConsumeToChar() throws Throwable {
        CharacterReader reader = new CharacterReader("abcde");
        String consumed = reader.consumeTo('c');
        assertEquals("ab", consumed);
        assertEquals('c', reader.current());

        // Test consumeTo when char not present
        CharacterReader reader2 = new CharacterReader("abcde");
        reader2.consumeTo('z');
        assertTrue(reader2.isEmpty());
    }

    @Test
    public void testConsumeToStringSeq() throws Throwable {
        CharacterReader reader = new CharacterReader("abcdef");
        String consumed = reader.consumeTo("de");
        assertEquals("abc", consumed);
        assertEquals('d', reader.current());

        // Test when sequence not present
        CharacterReader reader2 = new CharacterReader("abcdef");
        reader2.consumeTo("xyz");
        assertTrue(reader2.isEmpty());
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        CharacterReader reader = new CharacterReader("abc_123");
        char[] seek = new char[] { '_', '-' };
        String consumed = reader.consumeToAny(seek);
        assertEquals("abc", consumed);
        assertEquals('_', reader.current());

        // Test when none match
        CharacterReader reader2 = new CharacterReader("abc");
        String consumed2 = reader2.consumeToAny(seek);
        assertEquals("abc", consumed2);
    }

    @Test
    public void testConsumeLetterSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("abc123XYZ");
        assertEquals("abc", reader.consumeLetterSequence());
        assertEquals('1', reader.current());

        CharacterReader reader2 = new CharacterReader("123abc");
        assertEquals("", reader2.consumeLetterSequence());
    }

    @Test
    public void testConsumeHexSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("0123456789ABCDEFabcdefG");
        assertEquals("0123456789ABCDEFabcdef", reader.consumeHexSequence());
        assertEquals('G', reader.current());

        CharacterReader reader2 = new CharacterReader("GHI");
        assertEquals("", reader2.consumeHexSequence());
    }

    @Test
    public void testConsumeDigitSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("12345a678");
        assertEquals("12345", reader.consumeDigitSequence());
        assertEquals('a', reader.current());

        CharacterReader reader2 = new CharacterReader("abc");
        assertEquals("", reader2.consumeDigitSequence());
    }

    @Test
    public void testMatchesChar() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertTrue(reader.matches('a'));
        assertFalse(reader.matches('b'));
        
        // Exhaust
        reader.consume();
        reader.consume();
        reader.consume();
        assertFalse(reader.matches('a'));
    }

    @Test
    public void testMatchesString() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        assertTrue(reader.matches("hello"));
        assertFalse(reader.matches("world"));
    }

    @Test
    public void testMatchesIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("HeLLo");
        assertTrue(reader.matchesIgnoreCase("hElLo"));
        assertFalse(reader.matchesIgnoreCase("world"));
    }

    @Test
    public void testMatchesAny() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        char[] seek = new char[] { 'x', 'a', 'z' };
        assertTrue(reader.matchesAny(seek));
        
        reader.consume();
        assertFalse(reader.matchesAny(seek));

        // Empty reader
        CharacterReader readerEmpty = new CharacterReader("");
        assertFalse(readerEmpty.matchesAny(seek));
    }

    @Test
    public void testMatchesLetterAndDigit() throws Throwable {
        CharacterReader readerLetter = new CharacterReader("a1");
        assertTrue(readerLetter.matchesLetter());
        assertFalse(readerLetter.matchesDigit());

        readerLetter.consume();
        assertFalse(readerLetter.matchesLetter());
        assertTrue(readerLetter.matchesDigit());

        CharacterReader readerEmpty = new CharacterReader("");
        assertFalse(readerEmpty.matchesLetter());
        assertFalse(readerEmpty.matchesDigit());
    }

    @Test
    public void testMatchConsume() throws Throwable {
        CharacterReader reader = new CharacterReader("hello");
        assertTrue(reader.matchConsume("he"));
        assertEquals('l', reader.current());
        
        assertFalse(reader.matchConsume("lo"));
        assertEquals('l', reader.current());
    }

    @Test
    public void testMatchConsumeIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("HeLLo");
        assertTrue(reader.matchConsumeIgnoreCase("he"));
        assertEquals('L', reader.current());

        assertFalse(reader.matchConsumeIgnoreCase("xx"));
        assertEquals('L', reader.current());
    }

    @Test
    public void testContainsIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("This is a</TITLE> test");
        assertTrue(reader.containsIgnoreCase("</title>"));
        assertTrue(reader.containsIgnoreCase("</STYLE>"));
        assertFalse(reader.containsIgnoreCase("missing"));
    }

    @Test
    public void testToStringMethod() throws Throwable {
        CharacterReader reader = new CharacterReader("hello");
        reader.consume();
        assertEquals("ello", reader.toString());
    }
}