package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class CharacterReaderTest {

    @Test
    public void testInitializationAndCarriageReturnNormalization() throws Throwable {
        CharacterReader reader = new CharacterReader("a\r\nb\rc\n");
        assertEquals(0, reader.pos());
        assertFalse(reader.isEmpty());
        assertEquals('a', reader.current());
        assertEquals("a\nb\nc\n", reader.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInputThrowsException() throws Throwable {
        new CharacterReader(null);
    }

    @Test
    public void testNavigationAndPositionMethods() throws Throwable {
        CharacterReader reader = new CharacterReader("test");
        assertEquals(0, reader.pos());
        
        reader.advance();
        assertEquals(1, reader.pos());
        assertEquals('e', reader.current());

        char consumed = reader.consume();
        assertEquals('e', consumed);
        assertEquals(2, reader.pos());

        reader.unconsume();
        assertEquals(1, reader.pos());
        assertEquals('e', reader.current());

        reader.mark();
        reader.advance();
        reader.advance();
        assertEquals(3, reader.pos());

        reader.rewindToMark();
        assertEquals(1, reader.pos());
    }

    @Test
    public void testEmptyReaderBehavior() throws Throwable {
        CharacterReader reader = new CharacterReader("");
        assertTrue(reader.isEmpty());
        assertEquals(CharacterReader.EOF, reader.current());
        assertEquals(CharacterReader.EOF, reader.consume());
        assertEquals("", reader.consumeAsString());
        assertEquals("", reader.consumeToEnd());
    }

    @Test
    public void testConsumeAsString() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertEquals("a", reader.consumeAsString());
        assertEquals(1, reader.pos());
    }

    @Test
    public void testConsumeToChar() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        String consumed = reader.consumeTo('o');
        assertEquals("hell", consumed);
        assertEquals(4, reader.pos());

        // Test consumeTo when char not present
        CharacterReader reader2 = new CharacterReader("test");
        String consumedToEnd = reader2.consumeTo('x');
        assertEquals("test", consumedToEnd);
        assertTrue(reader2.isEmpty());
    }

    @Test
    public void testConsumeToString() throws Throwable {
        CharacterReader reader = new CharacterReader("foo.bar.baz");
        String consumed = reader.consumeTo(".bar");
        assertEquals("foo", consumed);
        assertEquals(3, reader.pos());

        // Test when sequence not present
        CharacterReader reader2 = new CharacterReader("foo");
        assertEquals("foo", reader2.consumeTo("missing"));
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        CharacterReader reader = new CharacterReader("abc,def;ghi");
        char[] seekers = new char[] {',', ';'};
        String consumed = reader.consumeToAny(seekers);
        assertEquals("abc", consumed);
        assertEquals(',', reader.current());

        // Test with no match
        CharacterReader reader2 = new CharacterReader("abcdef");
        assertEquals("abcdef", reader2.consumeToAny('z', 'y'));
        assertTrue(reader2.isEmpty());
    }

    @Test
    public void testConsumeLetterSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("abc123XYZ");
        assertEquals("abc", reader.consumeLetterSequence());
        assertEquals('1', reader.current());

        // Test non-letter at start
        CharacterReader reader2 = new CharacterReader("123abc");
        assertEquals("", reader2.consumeLetterSequence());
    }

    @Test
    public void testConsumeHexSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("0123456789ABCDEFabcdefGHI");
        assertEquals("0123456789ABCDEFabcdef", reader.consumeHexSequence());
        assertEquals('G', reader.current());
    }

    @Test
    public void testConsumeDigitSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("98765abc");
        assertEquals("98765", reader.consumeDigitSequence());
        assertEquals('a', reader.current());
    }

    @Test
    public void testMatchesChar() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertTrue(reader.matches('a'));
        assertFalse(reader.matches('b'));
        
        // At EOF
        reader.consumeToEnd();
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
        assertFalse(reader.matchesAny('x', 'y'));
        assertTrue(reader.matchesAny('x', 'a', 'b'));

        // At EOF
        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesAny('a', 'b'));
    }

    @Test
    public void testMatchesLetterAndDigit() throws Throwable {
        CharacterReader reader = new CharacterReader("A1_");
        assertTrue(reader.matchesLetter());
        assertTrue(reader.matchesDigit() == false);

        reader.advance();
        assertFalse(reader.matchesLetter());
        assertTrue(reader.matchesDigit());

        reader.advance();
        assertFalse(reader.matchesLetter());
        assertFalse(reader.matchesDigit());

        // At EOF
        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesLetter());
        assertFalse(emptyReader.matchesDigit());
    }

    @Test
    public void testMatchConsume() throws Throwable {
        CharacterReader reader = new CharacterReader("prefix_suffix");
        assertTrue(reader.matchConsume("pre"));
        assertEquals("fix_suffix", reader.toString());

        assertFalse(reader.matchConsume("wrong"));
        assertEquals("fix_suffix", reader.toString());
    }

    @Test
    public void testMatchConsumeIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("HTMLparser");
        assertTrue(reader.matchConsumeIgnoreCase("html"));
        assertEquals("parser", reader.toString());

        assertFalse(reader.matchConsumeIgnoreCase("wrong"));
        assertEquals("parser", reader.toString());
    }

    @Test
    public void testContainsIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("<div>Hello World</TITLE></div>");
        reader.consumeTo("Hello");
        assertTrue(reader.containsIgnoreCase("</title>"));
        assertFalse(reader.containsIgnoreCase("missing"));
    }
}