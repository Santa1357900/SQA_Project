package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenQueueClaudeTest {

    // Constructor: Validate.notNull should reject null input
    @Test
    public void testConstructor_nullData_throwsIllegalArgumentException() throws Throwable {
        try {
            new TokenQueue((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isEmpty: empty string -> true
    @Test
    public void testIsEmpty_emptyQueue_returnsTrue() throws Throwable {
        TokenQueue tq = new TokenQueue("");
        assertTrue(tq.isEmpty());
    }

    // isEmpty: non-empty -> false
    @Test
    public void testIsEmpty_nonEmptyQueue_returnsFalse() throws Throwable {
        TokenQueue tq = new TokenQueue("a");
        assertFalse(tq.isEmpty());
    }

    // peek: empty queue returns 0 char
    @Test
    public void testPeek_emptyQueue_returnsZeroChar() throws Throwable {
        TokenQueue tq = new TokenQueue("");
        assertEquals(0, tq.peek());
    }

    // peek: non-empty returns first char without consuming
    @Test
    public void testPeek_nonEmptyQueue_returnsFirstChar() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        assertEquals('a', tq.peek());
        assertEquals('a', tq.peek());
    }

    // addFirst(Character): prepends single character
    @Test
    public void testAddFirstCharacter_prependsChar() throws Throwable {
        TokenQueue tq = new TokenQueue("bc");
        tq.addFirst(Character.valueOf('a'));
        assertEquals("abc", tq.toString());
    }

    // addFirst(String): prepends string
    @Test
    public void testAddFirstString_prependsString() throws Throwable {
        TokenQueue tq = new TokenQueue("world");
        tq.addFirst("hello ");
        assertEquals("hello world", tq.toString());
    }

    // matches: case insensitive true/false
    @Test
    public void testMatches_caseInsensitive() throws Throwable {
        TokenQueue tq = new TokenQueue("Hello World");
        assertTrue(tq.matches("hello"));
        assertFalse(tq.matches("world"));
    }

    // matchesCS: case sensitive true/false
    @Test
    public void testMatchesCS_caseSensitive() throws Throwable {
        TokenQueue tq = new TokenQueue("Hello");
        assertFalse(tq.matchesCS("hello"));
        assertTrue(tq.matchesCS("Hello"));
    }

    // matchesAny(String...): matched and not matched
    @Test
    public void testMatchesAnyStrings_variants() throws Throwable {
        TokenQueue tq = new TokenQueue("foobar");
        assertTrue(tq.matchesAny("baz", "foo"));
        assertFalse(tq.matchesAny("baz", "qux"));
    }

    // matchesAny(char...): empty queue -> false
    @Test
    public void testMatchesAnyChars_emptyQueue_returnsFalse() throws Throwable {
        TokenQueue tq = new TokenQueue("");
        assertFalse(tq.matchesAny('a', 'b'));
    }

    // matchesAny(char...): non-empty match found
    @Test
    public void testMatchesAnyChars_nonEmptyQueue_matchFound() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        assertTrue(tq.matchesAny('x', 'a'));
        assertFalse(tq.matchesAny('x', 'y'));
    }

    // matchesStartTag: valid tag, non-letter after '<', and too-short queue
    @Test
    public void testMatchesStartTag_variants() throws Throwable {
        TokenQueue tq1 = new TokenQueue("<div>");
        assertTrue(tq1.matchesStartTag());
        TokenQueue tq2 = new TokenQueue("<1div>");
        assertFalse(tq2.matchesStartTag());
        TokenQueue tq3 = new TokenQueue("<");
        assertFalse(tq3.matchesStartTag());
    }

    // matchChomp: found removes seq, not found leaves queue unchanged
    @Test
    public void testMatchChomp_found_and_notFound() throws Throwable {
        TokenQueue tq1 = new TokenQueue("Hello World");
        assertTrue(tq1.matchChomp("hello"));
        assertEquals(" World", tq1.remainder());

        TokenQueue tq2 = new TokenQueue("Hello");
        assertFalse(tq2.matchChomp("World"));
        assertEquals("Hello", tq2.toString());
    }

    // matchesWhitespace: empty, whitespace, non-whitespace
    @Test
    public void testMatchesWhitespace_variants() throws Throwable {
        TokenQueue tq1 = new TokenQueue("");
        assertFalse(tq1.matchesWhitespace());
        TokenQueue tq2 = new TokenQueue(" a");
        assertTrue(tq2.matchesWhitespace());
        TokenQueue tq3 = new TokenQueue("a");
        assertFalse(tq3.matchesWhitespace());
    }

    // matchesWord: digit/letter true, whitespace false
    @Test
    public void testMatchesWord_variants() throws Throwable {
        TokenQueue tq1 = new TokenQueue("1a");
        assertTrue(tq1.matchesWord());
        TokenQueue tq2 = new TokenQueue(" a");
        assertFalse(tq2.matchesWord());
    }

    // advance: non-empty advances; empty is no-op (no exception)
    @Test
    public void testAdvance_nonEmptyAndEmpty() throws Throwable {
        TokenQueue tq = new TokenQueue("ab");
        tq.advance();
        assertEquals('b', tq.peek());

        TokenQueue empty = new TokenQueue("");
        empty.advance();
        assertTrue(empty.isEmpty());
    }

    // consume: returns char and advances position
    @Test
    public void testConsume_returnsFirstCharAndAdvances() throws Throwable {
        TokenQueue tq = new TokenQueue("ab");
        char c = tq.consume();
        assertEquals('a', c);
        assertEquals("b", tq.toString());
    }

    // consume(String): mismatch throws IllegalStateException
    @Test
    public void testConsumeString_mismatch_throwsIllegalStateException() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        try {
            tq.consume("xyz");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // consume(String): match advances queue past matched sequence
    @Test
    public void testConsumeString_match_advancesQueue() throws Throwable {
        TokenQueue tq = new TokenQueue("Hello World");
        tq.consume("hello");
        assertEquals(" World", tq.toString());
    }

    // consumeTo: found, consumes up to (not including) sequence
    @Test
    public void testConsumeTo_found() throws Throwable {
        TokenQueue tq = new TokenQueue("one,two");
        assertEquals("one", tq.consumeTo(","));
        assertEquals(",two", tq.toString());
    }

    // consumeTo: not found, consumes entire remainder
    @Test
    public void testConsumeTo_notFound_consumesAll() throws Throwable {
        TokenQueue tq = new TokenQueue("abcdef");
        assertEquals("abcdef", tq.consumeTo("xyz"));
        assertTrue(tq.isEmpty());
    }

    // consumeToIgnoreCase: canScan branch covering skip==0 and skip>0
    @Test
    public void testConsumeToIgnoreCase_nonCasedFirstChar_scanBranches() throws Throwable {
        TokenQueue tq = new TokenQueue("1xyz1abc");
        assertEquals("1xyz", tq.consumeToIgnoreCase("1abc"));
        assertEquals("1abc", tq.toString());
    }

    // consumeToIgnoreCase: no occurrence of scan char -> consumes all (skip<0 branch)
    @Test
    public void testConsumeToIgnoreCase_noOccurrence_consumesAll() throws Throwable {
        TokenQueue tq = new TokenQueue("abcdef");
        assertEquals("abcdef", tq.consumeToIgnoreCase("1xyz"));
        assertTrue(tq.isEmpty());
    }

    // consumeToIgnoreCase: cased first char -> step-by-step (canScan false) branch
    @Test
    public void testConsumeToIgnoreCase_casedFirstChar_stepByStep() throws Throwable {
        TokenQueue tq = new TokenQueue("xxABCyy");
        assertEquals("xx", tq.consumeToIgnoreCase("ABC"));
        assertEquals("ABCyy", tq.toString());
    }

    // consumeToAny: found stops at terminator, leaving terminator on queue
    @Test
    public void testConsumeToAny_found() throws Throwable {
        TokenQueue tq = new TokenQueue("abc;def,ghi");
        assertEquals("abc", tq.consumeToAny(";", ","));
        assertEquals(";def,ghi", tq.toString());
    }

    // consumeToAny: not found, consumes entire queue
    @Test
    public void testConsumeToAny_notFound_consumesAll() throws Throwable {
        TokenQueue tq = new TokenQueue("abcdef");
        assertEquals("abcdef", tq.consumeToAny("xyz"));
        assertTrue(tq.isEmpty());
    }

    // chompTo: consumes up to and removes the terminator
    @Test
    public void testChompTo_removesMatchedTerminator() throws Throwable {
        TokenQueue tq = new TokenQueue("one,two");
        assertEquals("one", tq.chompTo(","));
        assertEquals("two", tq.toString());
    }

    // chompToIgnoreCase: consumes up to and removes terminator, case insensitive
    @Test
    public void testChompToIgnoreCase_removesMatchedTerminatorCaseInsensitive() throws Throwable {
        TokenQueue tq = new TokenQueue("helloWORLDend");
        assertEquals("hello", tq.chompToIgnoreCase("world"));
        assertEquals("end", tq.toString());
    }

    // chompBalanced: basic Javadoc example, nested balanced parens
    @Test
    public void testChompBalanced_javadocExample() throws Throwable {
        TokenQueue tq = new TokenQueue("(one (two) three) four");
        String result = tq.chompBalanced('(', ')');
        assertEquals("one (two) three", result);
        assertEquals(" four", tq.toString());
    }

    // chompBalanced: quoted closer must not unbalance the match (bug catcher, per Javadoc contract)
    @Test
    public void testChompBalanced_quotedCloser_shouldNotUnbalance_bugCatcher() throws Throwable {
        TokenQueue tq = new TokenQueue("(a 'b)' c)");
        String result = tq.chompBalanced('(', ')');
        assertEquals("a 'b)' c", result);
        assertEquals("", tq.remainder());
    }



    // chompBalanced: no closing character, consumes remainder of queue
    @Test
    public void testChompBalanced_noClosingChar_consumesRemainder() throws Throwable {
        TokenQueue tq = new TokenQueue("(open");
        String result = tq.chompBalanced('(', ')');
        assertEquals("open", result);
        assertTrue(tq.isEmpty());
    }

    // unescape: single escape removes backslash before non-escape char
    @Test
    public void testUnescape_singleEscape_removesBackslash() throws Throwable {
        assertEquals("ab", TokenQueue.unescape("a\\b"));
    }

    // unescape: double backslash yields one literal backslash
    @Test
    public void testUnescape_doubleEscape_keepsOneBackslash() throws Throwable {
        assertEquals("a\\b", TokenQueue.unescape("a\\\\b"));
    }

    // consumeWhitespace: first call true (consumes ws), second call false (none left)
    @Test
    public void testConsumeWhitespace_seenTrueThenFalse() throws Throwable {
        TokenQueue tq = new TokenQueue(" \t abc");
        assertTrue(tq.consumeWhitespace());
        assertEquals("abc", tq.toString());
        assertFalse(tq.consumeWhitespace());
    }

    // consumeWord: letters and digits consumed, stops at non-word char
    @Test
    public void testConsumeWord_lettersAndDigits() throws Throwable {
        TokenQueue tq = new TokenQueue("abc123 def");
        assertEquals("abc123", tq.consumeWord());
        assertEquals(" def", tq.toString());
    }

    // consumeTagName: word chars plus colon/underscore/hyphen consumed
    @Test
    public void testConsumeTagName_wordColonUnderscoreHyphen() throws Throwable {
        TokenQueue tq = new TokenQueue("div:sub-tag_x rest");
        assertEquals("div:sub-tag_x", tq.consumeTagName());
        assertEquals(" rest", tq.toString());
    }

    // consumeElementSelector: pipe char supported instead of colon
    @Test
    public void testConsumeElementSelector_pipeCharSupported() throws Throwable {
        TokenQueue tq = new TokenQueue("div|foo bar");
        assertEquals("div|foo", tq.consumeElementSelector());
        assertEquals(" bar", tq.toString());
    }

    // consumeCssIdentifier: hyphen and underscore supported
    @Test
    public void testConsumeCssIdentifier_hyphenUnderscore() throws Throwable {
        TokenQueue tq = new TokenQueue("-webkit_flex end");
        assertEquals("-webkit_flex", tq.consumeCssIdentifier());
        assertEquals(" end", tq.toString());
    }

    // consumeAttributeKey: colon supported in attribute key
    @Test
    public void testConsumeAttributeKey_colonSupported() throws Throwable {
        TokenQueue tq = new TokenQueue("xml:lang=\"en\"");
        assertEquals("xml:lang", tq.consumeAttributeKey());
        assertEquals("=\"en\"", tq.toString());
    }

    // remainder: returns remaining text and empties the queue
    @Test
    public void testRemainder_returnsAndEmptiesQueue() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        tq.consume();
        String rem = tq.remainder();
        assertEquals("bc", rem);
        assertTrue(tq.isEmpty());
    }

    // toString: reflects remaining queue without consuming characters
    @Test
    public void testToString_doesNotConsume() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        tq.consume();
        assertEquals("bc", tq.toString());
        assertEquals("bc", tq.toString());
    }
}
