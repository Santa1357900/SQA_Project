package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Locale;

public class CharacterReaderTest {

    @Test
    public void testInitializationAndBasicNavigation() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertEquals(0, reader.pos());
        assertFalse(reader.isEmpty());
        assertEquals('a', reader.current());

        reader.advance();
        assertEquals(1, reader.pos());
        assertEquals('b', reader.current());

        reader.unconsume();
        assertEquals(0, reader.pos());
        assertEquals('a', reader.current());

        assertEquals('a', reader.consume());
        assertEquals(1, reader.pos());

        reader.mark();
        assertEquals('b', reader.consume());
        assertEquals('c', reader.consume());
        assertTrue(reader.isEmpty());
        assertEquals(CharacterReader.EOF, reader.current());
        assertEquals(CharacterReader.EOF, reader.consume());

        reader.rewindToMark();
        assertEquals(1, reader.pos());
        assertEquals('b', reader.current());
    }

    @Test
    public void testEmptyInput() throws Throwable {
        CharacterReader reader = new CharacterReader("");
        assertTrue(reader.isEmpty());
        assertEquals(CharacterReader.EOF, reader.current());
        assertEquals(CharacterReader.EOF, reader.consume());
        assertEquals(0, reader.pos());
        assertEquals("", reader.consumeToEnd());
        assertEquals("", reader.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInputThrowsException() throws Throwable {
        new CharacterReader(null);
    }

    @Test
    public void testConsumeAsString() throws Throwable {
        CharacterReader reader = new CharacterReader("test");
        assertEquals("t", reader.consumeAsString());
        assertEquals(1, reader.pos());
        assertEquals("e", reader.consumeAsString());
    }

    @Test
    public void testNextIndexOfChar() throws Throwable {
        CharacterReader reader = new CharacterReader("hello world");
        assertEquals(1, reader.nextIndexOf('e'));
        assertEquals(4, reader.nextIndexOf('o'));
        assertEquals(-1, reader.nextIndexOf('z'));

        reader.consume(); // pos = 1, "ello world"
        assertEquals(0, reader.nextIndexOf('e'));
        assertEquals(3, reader.nextIndexOf('o'));
    }

    @Test
    public void testNextIndexOfCharSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("the quick brown fox");
        assertEquals(4, reader.nextIndexOf("quick"));
        assertEquals(10, reader.nextIndexOf("brown"));
        assertEquals(-1, reader.nextIndexOf("dog"));
        assertEquals(0, reader.nextIndexOf("the"));
        assertEquals(-1, reader.nextIndexOf("toolongsequencethatisnotpresent"));
    }

    @Test
    public void testConsumeToChar() throws Throwable {
        CharacterReader reader = new CharacterReader("key=value");
        assertEquals("key", reader.consumeTo('='));
        assertEquals('=', reader.consume());
        assertEquals("value", reader.consumeTo('!')); // Not found, consumes to end
    }

    @Test
    public void testConsumeToString() throws Throwable {
        CharacterReader reader = new CharacterReader("start--end");
        assertEquals("start", reader.consumeTo("--"));
        assertEquals("--", reader.consumeAsString() + reader.consumeAsString());
        assertEquals("end", reader.consumeTo("missing"));
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        CharacterReader reader = new CharacterReader("hello, world;ft");
        char[] chars = new char[]{',', ';', '!'};
        assertEquals("hello", reader.consumeToAny(chars));
        assertEquals(',', reader.consume());
        assertEquals(" world", reader.consumeToAny(chars));
        assertEquals(';', reader.consume());
        assertEquals("ft", reader.consumeToAny(chars));
        
        CharacterReader reader2 = new CharacterReader("nodelimiter");
        assertEquals("nodelimiter", reader2.consumeToAny(chars));
    }

    @Test
    public void testConsumeToAnySorted() throws Throwable {
        CharacterReader reader = new CharacterReader("abc123xyz");
        char[] sortedChars = new char[]{'1', '2', '3'};
        // Arrays.binarySearch requires sorted array
        assertEquals("abc", reader.consumeToAnySorted(sortedChars));
        assertEquals("123", reader.consumeToAnySorted(new char[]{'x', 'y', 'z'}));
    }

    @Test
    public void testConsumeData() throws Throwable {
        CharacterReader reader = new CharacterReader("data&more<tag>\u0000null");
        assertEquals("data", reader.consumeData());
        assertEquals('&', reader.consume());
        assertEquals("more", reader.consumeData());
        assertEquals('<', reader.consume());
        assertEquals("tag", reader.consumeData());
        assertEquals('>', reader.consume());
        assertEquals(TokeniserState.nullChar, reader.consume());
        assertEquals("null", reader.consumeData());
    }

    @Test
    public void testConsumeTagName() throws Throwable {
        CharacterReader reader = new CharacterReader("div\nclass=\"foo\"\t>end");
        assertEquals("div", reader.consumeTagName());
        assertEquals('\n', reader.consume());
        assertEquals("class=\"foo\"", reader.consumeTagName());
    }

    @Test
    public void testConsumeLetterSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("AbCdEf123GhI");
        assertEquals("AbCdEf", reader.consumeLetterSequence());
        assertEquals("", reader.consumeLetterSequence()); // At digit
        reader.consume(); // consume '1'
        reader.consume(); // consume '2'
        reader.consume(); // consume '3'
        assertEquals("GhI", reader.consumeLetterSequence());
    }

    @Test
    public void testConsumeLetterThenDigitSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("Var123_rest");
        assertEquals("Var123", reader.consumeLetterThenDigitSequence());
        assertEquals("_", reader.consumeAsString());
        assertEquals("rest", reader.consumeLetterThenDigitSequence());
    }

    @Test
    public void testConsumeHexSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("0123456789ABCDEFabcdefG");
        assertEquals("0123456789ABCDEFabcdef", reader.consumeHexSequence());
        assertEquals("G", reader.consumeAsString());
    }

    @Test
    public void testConsumeDigitSequence() throws Throwable {
        CharacterReader reader = new CharacterReader("987654321a");
        assertEquals("987654321", reader.consumeDigitSequence());
        assertEquals("a", reader.consumeAsString());
    }

    @Test
    public void testMatches() throws Throwable {
        CharacterReader reader = new CharacterReader("test string");
        assertTrue(reader.matches('t'));
        assertFalse(reader.matches('e'));
        assertTrue(reader.matches("test"));
        assertFalse(reader.matches("best"));
        assertFalse(reader.matches("toolongstringtest"));

        // Exhaust reader
        while (!reader.isEmpty()) {
            reader.consume();
        }
        assertFalse(reader.matches('t'));
        assertFalse(reader.matches("test"));
    }

    @Test
    public void testMatchesIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("HeLLo World");
        assertTrue(reader.matchesIgnoreCase("hello"));
        assertTrue(reader.matchesIgnoreCase("HELLO WORLD"));
        assertFalse(reader.matchesIgnoreCase("world"));
        assertFalse(reader.matchesIgnoreCase("HeLLo World Extra"));
    }

    @Test
    public void testMatchesAny() throws Throwable {
        CharacterReader reader = new CharacterReader("abc");
        assertTrue(reader.matchesAny('z', 'a', 'b'));
        assertFalse(reader.matchesAny('x', 'y'));

        // Test empty
        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesAny('a'));
    }

    @Test
    public void testMatchesAnySorted() throws Throwable {
        char[] sorted = new char[]{'a', 'b', 'c'};
        CharacterReader reader = new CharacterReader("b_val");
        assertTrue(reader.matchesAnySorted(sorted));

        reader.consume(); // now at '_'
        assertFalse(reader.matchesAnySorted(sorted));

        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesAnySorted(sorted));
    }

    @Test
    public void testMatchesLetterAndDigit() throws Throwable {
        CharacterReader reader = new CharacterReader("a1_");
        assertTrue(reader.matchesLetter());
        assertFalse(reader.matchesDigit());

        reader.consume(); // at '1'
        assertFalse(reader.matchesLetter());
        assertTrue(reader.matchesDigit());

        reader.consume(); // at '_'
        assertFalse(reader.matchesLetter());
        assertFalse(reader.matchesDigit());

        CharacterReader emptyReader = new CharacterReader("");
        assertFalse(emptyReader.matchesLetter());
        assertFalse(emptyReader.matchesDigit());
    }

    @Test
    public void testMatchConsume() throws Throwable {
        CharacterReader reader = new CharacterReader("foobar");
        assertFalse(reader.matchConsume("bar"));
        assertEquals(0, reader.pos());
        assertTrue(reader.matchConsume("foo"));
        assertEquals(3, reader.pos());
        assertTrue(reader.matchConsume("bar"));
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testMatchConsumeIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("FooBar");
        assertFalse(reader.matchConsumeIgnoreCase("bar"));
        assertTrue(reader.matchConsumeIgnoreCase("FOO"));
        assertEquals(3, reader.pos());
        assertTrue(reader.matchConsumeIgnoreCase("bar"));
        assertTrue(reader.isEmpty());
    }

    @Test
    public void testContainsIgnoreCase() throws Throwable {
        CharacterReader reader = new CharacterReader("Prefix </TITLE> Suffix");
        assertTrue(reader.containsIgnoreCase("</title>"));
        assertTrue(reader.containsIgnoreCase("</TITLE>"));
        assertFalse(reader.containsIgnoreCase("</style>"));
    }

    @Test
    public void testCachingAndRangeEquals() throws Throwable {
        // Trigger cache logic for short strings (< maxCacheLen = 12)
        CharacterReader reader = new CharacterReader("short short short stringtoolongtobeachedrightnow");
        String s1 = reader.consumeTo(' ');
        reader.consume(); // space
        String s2 = reader.consumeTo(' ');
        
        // Test rangeEquals and cache hits/collisions internally via repeated identical short strings
        CharacterReader reader2 = new CharacterReader("abc abc abc");
        String a1 = reader2.consumeTo(' ');
        reader2.consume();
        String a2 = reader2.consumeTo(' ');
        assertSame(a1, a2); // Should hit cache and return the exact same String instance
        
        // Force cache collision/override or cache miss branches
        CharacterReader reader3 = new CharacterReader("abcdefghijklmnopq abcdefghijklmnopq"); // > 12 chars, no cache
        String long1 = reader3.consumeTo(' ');
        assertNotNull(long1);
    }
}