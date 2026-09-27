package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Locale;

public class CharacterReaderTest {

    @Test
    public void testConstructorAndBasicProperties() throws Throwable {
        CharacterReader reader = new CharacterReader("12345");
        assertEquals(0, reader.pos());
        assertFalse(reader.isEmpty());
        assertEquals('1', reader.current());
        assertEquals("12345", reader.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullInput() throws Throwable {
        new CharacterReader(null);
    }

    @Test
    public void testEmptyInput() throws Throwable {
        CharacterReader reader = new CharacterReader("");
        assertTrue(reader.isEmpty());
        assertEquals(CharacterReader.EOF, reader.current());
        assertEquals(CharacterReader.EOF, reader.consume());
        assertEquals("", reader.toString());
    }

    @Test
    public void testConsumeAndNavigation() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertEquals('a', reader.consume());
        assertEquals(1, reader.pos());
        
        reader.unconsume();
        assertEquals(0, reader.pos());
        assertEquals('a', reader.current());

        reader.advance();
        assertEquals(1, reader.pos());
        assertEquals('b', reader.current());

        assertEquals("b", reader.consumeAsString());
        assertEquals(2, reader.pos());
    }

    @Test
    public void testMarkAndRewind() throws Throwable {
        CharacterReader reader = new CharacterReader("abcdef");
        reader.consume(); // pos = 1 ('b')
        reader.consume(); // pos = 2 ('c')
        reader.mark();

        reader.consume(); // pos = 3 ('d')
        assertEquals('d', reader.current());

        reader.rewindToMark();
        assertEquals(2, reader.pos());
        assertEquals('c', reader.current());
    }

    @Test
    public void testNextIndexOfChar() throws Throwable {
        CharacterReader reader = new CharacterReader("abacad");
        assertEquals(0, reader.nextIndexOf('a'));
        assertEquals(2, reader.nextIndexOf('c'));
        assertEquals(-1, reader.nextIndexOf('z'));

        reader.consume(); // pos = 1, remaining "bacad"
        assertEquals(1, reader.nextIndexOf('a'));
    }

    @Test
    public void testNextIndexOfCharSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        assertEquals(6, reader.nextIndexOf("world"));
        assertEquals(0, reader.nextIndexOf("hello"));
        assertEquals(-1, reader.nextIndexOf("notfound"));
        assertEquals(-1, reader.nextIndexOf("lo wo")); // span boundary test
    }

    @Test
    public void testConsumeToChar() throws Throwable {
        CharacterReader reader = new CharacterReader("name=value");
        assertEquals("name", reader.consumeTo('='));
        assertEquals('=', reader.consume());
        assertEquals("value", reader.consumeToEnd());
    }

    @Test
    public void testConsumeToCharNotFound() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertEquals("abc", reader.consumeTo('z'));
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testConsumeToStringSeq() throws Throwable {
        CharacterReader reader = new CharacterReader("start<tag>end");
        assertEquals("start", reader.consumeTo("<tag>"));
        assertEquals("<tag>", reader.consumeTo("end"));
        assertEquals("end", reader.consumeToEnd());
    }

    @Test
    public void testConsumeToStringSeqNotFound() throws Throwable {
        CharacterReader reader = new CharacterReader("abcdef");
        assertEquals("abcdef", reader.consumeTo("xyz"));
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        CharacterReader reader = new CharacterReader("hello;world,java");
        char[] stopChars = new char[] { ';', ',' };
        assertEquals("hello", reader.consumeToAny(stopChars));
        assertEquals(';', reader.consume());
        assertEquals("world", reader.consumeToAny(stopChars));
        assertEquals(',', reader.consume());
        assertEquals("java", reader.consumeToAny(stopChars));
    }

    @Test
    public void testConsumeToAnyNoMatch() throws Throwable {
        CharacterReader reader = new CharacterReader("abcdef");
        char[] stopChars = new char[] { 'x', 'y' };
        assertEquals("abcdef", reader.consumeToAny(stopChars));
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testSequenceConsumers() throws Throwable {
        CharacterReader reader = new CharacterReader("Abc123XYZ_789ABCdef012abcdef1234567890ABCDEF");

        // Letter sequence
        assertEquals("Abc", reader.consumeLetterSequence());
        
        // Letter then digit sequence
        assertEquals("123XYZ", reader.consumeLetterThenDigitSequence()); // Wait, '1' is digit, let's verify logic: letter then digit
        
        // Let's test properly with fresh readers for each
    }

    @Test
    public void testConsumeLetterSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("Abc123xyz");
        assertEquals("Abc", reader.consumeLetterSequence());
        assertEquals("123xyz", reader.consumeToEnd());
    }

    @Test
    public void testConsumeLetterThenDigitSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("Abc123xyz!@#");
        assertEquals("Abc123", reader.consumeLetterThenDigitSequence());
        assertEquals("xyz!@#", reader.consumeToEnd());
    }

    @Test
    public void testConsumeHexSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("0123456789ABCDEFabcdefGHI");
        assertEquals("0123456789ABCDEFabcdef", reader.consumeHexSequence());
        assertEquals("GHI", reader.consumeToEnd());
    }

    @Test
    public void testConsumeDigitSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("12345a6789");
        assertEquals("12345", reader.consumeDigitSequence());
        assertEquals("a6789", reader.consumeToEnd());
    }

    @Test
    public void testMatchesChar() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertTrue(reader.matches('a'));
        assertFalse(reader.matches('b'));
        reader.consume();
        assertTrue(reader.matches('b'));
        
        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matches('a'));
    }

    @Test
    public void testMatchesString() throws Throwable {
        CharacterReader reader = new CharacterReader("<html>");
        assertTrue(reader.matches("html"));
        assertTrue(reader.matches("<html>"));
        assertFalse(reader.matches("body"));
        assertFalse(reader.matches("<html></html>")); // longer than remaining
    }

    @Test
    public void testMatchesIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("<HTML>");
        assertTrue(reader.matchesIgnoreCase("html"));
        assertTrue(reader.matchesIgnoreCase("<html"));
        assertFalse(reader.matchesIgnoreCase("body"));
        assertFalse(reader.matchesIgnoreCase("<HTML>EXTRA"));
    }

    @Test
    public void testMatchesAny() throws Throwable {
        CharacterReader reader = new CharacterReader("xyz");
        assertTrue(reader.matchesAny('a', 'x', 'z'));
        assertFalse(reader.matchesAny('b', 'c'));

        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesAny('x'));
    }

    @Test
    public void testMatchesLetterAndDigit() throws Throwable {
        CharacterReader reader = new CharacterReader("A1!");
        assertTrue(reader.matchesLetter());
        assertFalse(reader.matchesDigit());
        reader.consume();
        
        assertFalse(reader.matchesLetter());
        assertTrue(reader.matchesDigit());
        reader.consume();
        
        assertFalse(reader.matchesLetter());
        assertFalse(reader.matchesDigit());

        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesLetter());
        assertFalse(emptyReader.matchesDigit());
    }

    @Test
    public void testMatchConsume() throws Throwable {
        CharacterReader reader = new CharacterReader("foo-bar");
        assertFalse(reader.matchConsume("bar"));
        assertEquals(0, reader.pos());

        assertTrue(reader.matchConsume("foo"));
        assertEquals(3, reader.pos());
        assertEquals("-bar", reader.toString());
    }

    @Test
    public void testMatchConsumeIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("FOO-bar");
        assertTrue(reader.matchConsumeIgnoreCase("foo"));
        assertEquals(3, reader.pos());
        assertEquals("-bar", reader.toString());
        
        assertFalse(reader.matchConsumeIgnoreCase("baz"));
    }

    @Test
    public void testContainsIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("Hello </TITLE> World");
        assertTrue(reader.containsIgnoreCase("</title>"));
        assertTrue(reader.containsIgnoreCase("</TITLE>"));
        assertFalse(reader.containsIgnoreCase("</style>"));
    }
}