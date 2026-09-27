package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenQueueTest {

    @Test
    public void testConstructorAndIsEmpty() throws Throwable {
        TokenQueue tq = new TokenQueue("test");
        assertFalse(tq.isEmpty());
        
        TokenQueue emptyTq = new TokenQueue("");
        assertTrue(emptyTq.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNull() throws Throwable {
        new TokenQueue(null);
    }

    @Test
    public void testPeekAndAdvance() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        assertEquals('a', tq.peek());
        tq.advance();
        assertEquals('b', tq.peek());
        tq.advance();
        tq.advance();
        assertEquals(0, tq.peek());
        tq.advance(); // should not throw exception when empty
    }

    @Test
    public void testAddFirst() throws Throwable {
        TokenQueue tq = new TokenQueue("world");
        tq.addFirst("Hello ");
        assertEquals("Hello world", tq.remainder());

        TokenQueue tqChar = new TokenQueue("bc");
        tqChar.addFirst(Character.valueOf('a'));
        assertEquals("abc", tqChar.remainder());
    }

    @Test
    public void testMatches() throws Throwable {
        TokenQueue tq = new TokenQueue("Hello World");
        assertTrue(tq.matches("hello"));
        assertFalse(tq.matches("world"));
    }

    @Test
    public void testMatchesCS() throws Throwable {
        TokenQueue tq = new TokenQueue("Hello World");
        assertTrue(tq.matchesCS("Hello"));
        assertFalse(tq.matchesCS("hello"));
    }

    @Test
    public void testMatchesAnyStringArray() throws Throwable {
        TokenQueue tq = new TokenQueue("foo bar");
        assertTrue(tq.matchesAny("baz", "foo"));
        assertFalse(tq.matchesAny("bar", "baz"));
    }

    @Test
    public void testMatchesAnyCharArray() throws Throwable {
        TokenQueue tq = new TokenQueue("123");
        assertTrue(tq.matchesAny('9', '1', '2'));
        assertFalse(tq.matchesAny('2', '3'));

        TokenQueue emptyTq = new TokenQueue("");
        assertFalse(emptyTq.matchesAny('a', 'b'));
    }

    @Test
    public void testMatchesStartTag() throws Throwable {
        TokenQueue tq1 = new TokenQueue("<div");
        assertTrue(tq1.matchesStartTag());

        TokenQueue tq2 = new TokenQueue("<1");
        assertFalse(tq2.matchesStartTag());

        TokenQueue tq3 = new TokenQueue("x");
        assertFalse(tq3.matchesStartTag());
    }

    @Test
    public void testMatchChomp() throws Throwable {
        TokenQueue tq = new TokenQueue("abcdef");
        assertTrue(tq.matchChomp("abc"));
        assertEquals("def", tq.remainder());

        assertFalse(tq.matchChomp("xyz"));
    }

    @Test
    public void testMatchesWhitespace() throws Throwable {
        TokenQueue tq = new TokenQueue("   abc");
        assertTrue(tq.matchesWhitespace());
        tq.advance();
        tq.advance();
        tq.advance();
        assertFalse(tq.matchesWhitespace());
        
        TokenQueue emptyTq = new TokenQueue("");
        assertFalse(emptyTq.matchesWhitespace());
    }

    @Test
    public void testMatchesWord() throws Throwable {
        TokenQueue tq = new TokenQueue("a1_");
        assertTrue(tq.matchesWord());
        tq.advance();
        tq.advance();
        assertFalse(tq.matchesWord());

        TokenQueue emptyTq = new TokenQueue("");
        assertFalse(emptyTq.matchesWord());
    }

    @Test
    public void testConsume() throws Throwable {
        TokenQueue tq = new TokenQueue("ab");
        assertEquals('a', tq.consume());
        assertEquals('b', tq.consume());
    }

    @Test
    public void testConsumeString() throws Throwable {
        TokenQueue tq = new TokenQueue("hello world");
        tq.consume("HELLO");
        assertEquals(" world", tq.remainder());
    }

    @Test(expected = IllegalStateException.class)
    public void testConsumeStringMismatch() throws Throwable {
        TokenQueue tq = new TokenQueue("hello");
        tq.consume("world");
    }

    @Test(expected = IllegalStateException.class)
    public void testConsumeStringTooLong() throws Throwable {
        TokenQueue tq = new TokenQueue("hello");
        tq.consume("hello world extra");
    }

    @Test
    public void testConsumeTo() throws Throwable {
        TokenQueue tq = new TokenQueue("one,two,three");
        assertEquals("one", tq.consumeTo(","));
        assertEquals(",", tq.consumeTo("t"));
    }

    @Test
    public void testConsumeToNotFound() throws Throwable {
        TokenQueue tq = new TokenQueue("onetwo");
        assertEquals("onetwo", tq.consumeTo("missing"));
        assertTrue(tq.isEmpty());
    }

    @Test
    public void testConsumeToIgnoreCase() throws Throwable {
        TokenQueue tq = new TokenQueue("OneABCThree");
        assertEquals("One", tq.consumeToIgnoreCase("abc"));
        
        TokenQueue tqCanScan = new TokenQueue("abcdef");
        assertEquals("abc", tqCanScan.consumeToIgnoreCase("d"));

        TokenQueue tqSkipZero = new TokenQueue("da");
        assertEquals("", tqSkipZero.consumeToIgnoreCase("a"));
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        TokenQueue tq = new TokenQueue("one-two.three");
        assertEquals("one", tq.consumeToAny("-", "."));
    }

    @Test
    public void testChompTo() throws Throwable {
        TokenQueue tq = new TokenQueue("one.two");
        assertEquals("one", tq.chompTo("."));
        assertEquals("two", tq.remainder());
    }

    @Test
    public void testChompToIgnoreCase() throws Throwable {
        TokenQueue tq = new TokenQueue("One.Two");
        assertEquals("One", tq.chompToIgnoreCase("."));
        assertEquals("Two", tq.remainder());
    }

    @Test
    public void testChompBalanced() throws Throwable {
        TokenQueue tq = new TokenQueue("(one (two) three) four");
        String balanced = tq.chompBalanced('(', ')');
        assertEquals("one (two) three", balanced);
        assertEquals(" four", tq.remainder());
    }

    @Test
    public void testChompBalancedQuotesAndEscapes() throws Throwable {
        TokenQueue tq = new TokenQueue("(\"(\" \\) test) remainder");
        String balanced = tq.chompBalanced('(', ')');
        assertEquals("\"(\" \\) test", balanced);
    }

    @Test
    public void testChompBalancedEmpty() throws Throwable {
        TokenQueue tq = new TokenQueue("");
        assertEquals("", tq.chompBalanced('(', ')'));
    }

    @Test
    public void testUnescape() throws Throwable {
        String escaped = "abc\\de\\\\f";
        assertEquals("abcde\\f", TokenQueue.unescape(escaped));
    }

    @Test
    public void testConsumeWhitespace() throws Throwable {
        TokenQueue tq = new TokenQueue("   abc");
        assertTrue(tq.consumeWhitespace());
        assertEquals("abc", tq.remainder());

        TokenQueue tqNone = new TokenQueue("abc");
        assertFalse(tqNone.consumeWhitespace());
    }

    @Test
    public void testConsumeWord() throws Throwable {
        TokenQueue tq = new TokenQueue("abc123_xyz");
        assertEquals("abc123", tq.consumeWord());
        assertEquals("_xyz", tq.remainder());
    }

    @Test
    public void testConsumeTagName() throws Throwable {
        TokenQueue tq = new TokenQueue("div:tag_name-foo bar");
        assertEquals("div:tag_name-foo", tq.consumeTagName());
    }

    @Test
    public void testConsumeElementSelector() throws Throwable {
        TokenQueue tq = new TokenQueue("*|div|name_foo-bar baz");
        assertEquals("*|div|name_foo-bar", tq.consumeElementSelector());
    }

    @Test
    public void testConsumeCssIdentifier() throws Throwable {
        TokenQueue tq = new TokenQueue("my-id_123 456");
        assertEquals("my-id_123", tq.consumeCssIdentifier());
    }

    @Test
    public void testConsumeAttributeKey() throws Throwable {
        TokenQueue tq = new TokenQueue("data-attr:key_1 value");
        assertEquals("data-attr:key_1", tq.consumeAttributeKey());
    }

    @Test
    public void testToStringMethod() throws Throwable {
        TokenQueue tq = new TokenQueue("hello");
        tq.advance();
        assertEquals("ello", tq.toString());
    }
}