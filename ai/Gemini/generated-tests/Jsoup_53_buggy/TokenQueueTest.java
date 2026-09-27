package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenQueueTest {

    @Test
    public void testConstructorAndBasicOps() throws Throwable {
        TokenQueue tq = new TokenQueue("Hello World");
        assertFalse(tq.isEmpty());
        assertEquals('H', tq.peek());
        assertEquals("Hello World", tq.toString());
        assertEquals('H', tq.consume());
        assertEquals("ello World", tq.toString());
    }

    @Test
    public void testAddFirst() throws Throwable {
        TokenQueue tq = new TokenQueue("World");
        tq.addFirst("Hello ");
        assertEquals("Hello World", tq.toString());
        tq.addFirst(Character.valueOf('!'));
        assertEquals("!Hello World", tq.toString());
    }

    @Test
    public void testMatches() throws Throwable {
        TokenQueue tq = new TokenQueue("TestString");
        assertTrue(tq.matches("test"));
        assertTrue(tq.matchesCS("Test"));
        assertFalse(tq.matchesCS("test"));
        
        assertTrue(tq.matchesAny("abc", "test"));
        assertFalse(tq.matchesAny("abc", "xyz"));

        assertTrue(tq.matchesAny('T', 'x'));
        assertFalse(tq.matchesAny('a', 'b'));

        TokenQueue emptyTq = new TokenQueue("");
        assertFalse(emptyTq.matchesAny('a'));
    }

    @Test
    public void testMatchesStartTag() throws Throwable {
        TokenQueue tq = new TokenQueue("<div");
        assertTrue(tq.matchesStartTag());

        TokenQueue tq2 = new TokenQueue("<1");
        assertFalse(tq2.matchesStartTag());

        TokenQueue tq3 = new TokenQueue("<");
        assertFalse(tq3.matchesStartTag());
    }

    @Test
    public void testMatchChomp() throws Throwable {
        TokenQueue tq = new TokenQueue("PrefixContent");
        assertTrue(tq.matchChomp("prefix"));
        assertEquals("Content", tq.toString());
        assertFalse(tq.matchChomp("missing"));
    }

    @Test
    public void testMatchesWhitespaceAndWord() throws Throwable {
        TokenQueue tq = new TokenQueue("   abc123");
        assertTrue(tq.matchesWhitespace());
        assertFalse(tq.matchesWord());

        tq.consumeWhitespace();
        assertFalse(tq.matchesWhitespace());
        assertTrue(tq.matchesWord());
    }

    @Test
    public void testAdvance() throws Throwable {
        TokenQueue tq = new TokenQueue("ab");
        tq.advance();
        assertEquals('b', tq.peek());
        tq.advance();
        assertTrue(tq.isEmpty());
        tq.advance(); // should not fail on empty
    }

    @Test
    public void testConsumeValidAndExceptions() throws Throwable {
        TokenQueue tq = new TokenQueue("ExpectedSequence");
        tq.consume("expected");
        assertEquals("Sequence", tq.toString());

        TokenQueue tq2 = new TokenQueue("Short");
        boolean thrown = false;
        try {
            tq2.consume("LongerSequenceThanQueue");
        } catch (IllegalStateException e) {
            thrown = true;
        }
        assertTrue(thrown);

        boolean thrown2 = false;
        try {
            tq2.consume("NotMatch");
        } catch (IllegalStateException e) {
            thrown2 = true;
        }
        assertTrue(thrown2);
    }

    @Test
    public void testConsumeTo() throws Throwable {
        TokenQueue tq = new TokenQueue("one,two,three");
        assertEquals("one", tq.consumeTo(","));
        assertEquals(",two,three", tq.toString());

        TokenQueue tq2 = new TokenQueue("nomatch");
        assertEquals("nomatch", tq2.consumeTo("missing"));
        assertTrue(tq2.isEmpty());
    }

    @Test
    public void testConsumeToIgnoreCase() throws Throwable {
        TokenQueue tq = new TokenQueue("ABC[def]GHI");
        assertEquals("ABC", tq.consumeToIgnoreCase("[DEF]"));
        assertEquals("[def]GHI", tq.toString());

        TokenQueue tq2 = new TokenQueue("no_match_here");
        assertEquals("no_match_here", tq2.consumeToIgnoreCase("xyz"));

        TokenQueue tq3 = new TokenQueue("aaaaa");
        assertEquals("aaaaa", tq3.consumeToIgnoreCase("b"));
    }

    @Test
    public void testConsumeToAny() throws Throwable {
        TokenQueue tq = new TokenQueue("word1;word2,word3");
        assertEquals("word1", tq.consumeToAny(";", ","));
    }

    @Test
    public void testChompTo() throws Throwable {
        TokenQueue tq = new TokenQueue("content:remainder");
        assertEquals("content", tq.chompTo(":"));
        assertEquals("remainder", tq.toString());
    }

    @Test
    public void testChompToIgnoreCase() throws Throwable {
        TokenQueue tq = new TokenQueue("content:remainder");
        assertEquals("content", tq.chompToIgnoreCase(":"));
        assertEquals("remainder", tq.toString());
    }

    @Test
    public void testChompBalanced() throws Throwable {
        TokenQueue tq = new TokenQueue("(one (two) three) four");
        assertEquals("one (two) three", tq.chompBalanced('(', ')'));
        assertEquals(" four", tq.toString());

        TokenQueue tqEmpty = new TokenQueue("");
        assertEquals("", tqEmpty.chompBalanced('(', ')'));
    }

    @Test
    public void testUnescape() throws Throwable {
        assertEquals("abc", TokenQueue.unescape("a\\b\\c"));
        assertEquals("a\\c", TokenQueue.unescape("a\\\\c"));
    }

    @Test
    public void testConsumeWordAndSelectors() throws Throwable {
        TokenQueue tq = new TokenQueue("abc-123_456:789|xyz");
        assertEquals("abc", tq.consumeWord());
        assertEquals("-123_456:789|xyz", tq.toString());

        TokenQueue tq2 = new TokenQueue("tag-name_123:sub");
        assertEquals("tag-name_123:sub", tq2.consumeTagName());

        TokenQueue tq3 = new TokenQueue("elem|name_123");
        assertEquals("elem|name_123", tq3.consumeElementSelector());

        TokenQueue tq4 = new TokenQueue("id-name_123");
        assertEquals("id-name_123", tq4.consumeCssIdentifier());

        TokenQueue tq5 = new TokenQueue("attr-key_123:val");
        assertEquals("attr-key_123:val", tq5.consumeAttributeKey());
    }

    @Test
    public void testRemainder() throws Throwable {
        TokenQueue tq = new TokenQueue("leftover");
        tq.consume();
        assertEquals("eftover", tq.remainder());
        assertTrue(tq.isEmpty());
    }
}