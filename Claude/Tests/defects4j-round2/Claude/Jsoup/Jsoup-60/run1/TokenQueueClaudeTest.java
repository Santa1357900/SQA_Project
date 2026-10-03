package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenQueueClaudeTest {

    // Constructor: Validate.notNull should reject null data
    @Test
    public void testConstructor_nullData_throwsIllegalArgumentException() throws Throwable {
        try {
            new TokenQueue(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // isEmpty: empty string -> true
    @Test
    public void testIsEmpty_emptyString_true() throws Throwable {
        TokenQueue tq = new TokenQueue("");
        assertTrue(tq.isEmpty());
    }

    // isEmpty: non-empty string -> false
    @Test
    public void testIsEmpty_nonEmptyString_false() throws Throwable {
        TokenQueue tq = new TokenQueue("a");
        assertFalse(tq.isEmpty());
    }

    // peek: non-empty returns first char without consuming
    @Test
    public void testPeek_nonEmpty_returnsFirstChar() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        assertEquals('a', tq.peek());
        assertEquals('a', tq.peek());
    }

    // peek: empty queue returns 0 char
    @Test
    public void testPeek_empty_returnsZeroChar() throws Throwable {
        TokenQueue tq = new TokenQueue("");
        assertEquals(0, tq.peek());
    }

    // addFirst(Character): prepends a single char
    @Test
    public void testAddFirstCharacter_prependsChar() throws Throwable {
        TokenQueue tq = new TokenQueue("bc");
        tq.addFirst(Character.valueOf('a'));
        assertEquals("abc", tq.remainder());
    }

    // addFirst(String): prepends a string, resets pos
    @Test
    public void testAddFirstString_prependsString() throws Throwable {
        TokenQueue tq = new TokenQueue("world");
        tq.consume();
        tq.addFirst("hello ");
        assertEquals("hello orld", tq.remainder());
    }

    // matches: case-insensitive match true
    @Test
    public void testMatches_caseInsensitiveTrue() throws Throwable {
        TokenQueue tq = new TokenQueue("ABCdef");
        assertTrue(tq.matches("abc"));
        assertFalse(tq.matches("xyz"));
    }

    // matches: seq longer than remaining -> false (regionMatches bounds)
    @Test
    public void testMatches_seqLongerThanRemaining_false() throws Throwable {
        TokenQueue tq = new TokenQueue("ab");
        assertFalse(tq.matches("abc"));
    }

    // matchesCS: case sensitive comparison
    @Test
    public void testMatchesCS_caseSensitive() throws Throwable {
        TokenQueue tq = new TokenQueue("ABC");
        assertFalse(tq.matchesCS("abc"));
        assertTrue(tq.matchesCS("ABC"));
    }

    // matchesAny(String...): true if any match, false if none
    @Test
    public void testMatchesAnyStrings_trueAndFalse() throws Throwable {
        TokenQueue tq = new TokenQueue("hello");
        assertTrue(tq.matchesAny("xx", "he"));
        assertFalse(tq.matchesAny("zz", "yy"));
    }

    // matchesAny(char...): empty queue returns false
    @Test
    public void testMatchesAnyChars_emptyQueue_false() throws Throwable {
        TokenQueue tq = new TokenQueue("");
        assertFalse(tq.matchesAny('a', 'b'));
    }

    // matchesAny(char...): found char returns true
    @Test
    public void testMatchesAnyChars_found_true() throws Throwable {
        TokenQueue tq = new TokenQueue("xyz");
        assertTrue(tq.matchesAny('x', 'q'));
        assertFalse(tq.matchesAny('a', 'b'));
    }

    // matchesStartTag: remaining length < 2 -> false
    @Test
    public void testMatchesStartTag_remainingLessThanTwo_false() throws Throwable {
        TokenQueue tq = new TokenQueue("<");
        assertFalse(tq.matchesStartTag());
    }

    // matchesStartTag: next char not a letter -> false
    @Test
    public void testMatchesStartTag_nextNotLetter_false() throws Throwable {
        TokenQueue tq = new TokenQueue("<1abc");
        assertFalse(tq.matchesStartTag());
    }

    // matchesStartTag: '<' followed by letter -> true
    @Test
    public void testMatchesStartTag_valid_true() throws Throwable {
        TokenQueue tq = new TokenQueue("<a href");
        assertTrue(tq.matchesStartTag());
    }

    // matchChomp: found -> removes seq and returns true
    @Test
    public void testMatchChomp_found_removesAndTrue() throws Throwable {
        TokenQueue tq = new TokenQueue("HELLOworld");
        assertTrue(tq.matchChomp("hello"));
        assertEquals("world", tq.remainder());
    }

    // matchChomp: not found -> false, queue unchanged
    @Test
    public void testMatchChomp_notFound_false() throws Throwable {
        TokenQueue tq = new TokenQueue("world");
        assertFalse(tq.matchChomp("hello"));
        assertEquals("world", tq.remainder());
    }

    // matchesWhitespace: true/false and empty cases
    @Test
    public void testMatchesWhitespace_trueFalseEmpty() throws Throwable {
        assertTrue(new TokenQueue(" a").matchesWhitespace());
        assertFalse(new TokenQueue("a").matchesWhitespace());
        assertFalse(new TokenQueue("").matchesWhitespace());
    }

    // matchesWord: true/false and empty cases
    @Test
    public void testMatchesWord_trueFalseEmpty() throws Throwable {
        assertTrue(new TokenQueue("a1").matchesWord());
        assertFalse(new TokenQueue(" a").matchesWord());
        assertFalse(new TokenQueue("").matchesWord());
    }

    // advance: on non-empty moves pos, on empty does nothing (no exception)
    @Test
    public void testAdvance_nonEmptyAndEmpty() throws Throwable {
        TokenQueue tq = new TokenQueue("ab");
        tq.advance();
        assertEquals("b", tq.remainder());
        TokenQueue empty = new TokenQueue("");
        empty.advance();
        assertTrue(empty.isEmpty());
    }

    // consume: returns current char and advances position
    @Test
    public void testConsume_returnsCharAndAdvancesPos() throws Throwable {
        TokenQueue tq = new TokenQueue("xy");
        assertEquals('x', tq.consume());
        assertEquals('y', tq.consume());
        assertTrue(tq.isEmpty());
    }

    // consume(String): matching sequence advances position
    @Test
    public void testConsumeString_success() throws Throwable {
        TokenQueue tq = new TokenQueue("FooBar");
        tq.consume("foo");
        assertEquals("Bar", tq.remainder());
    }

    // consume(String): mismatched sequence throws IllegalStateException
    @Test
    public void testConsumeString_mismatch_throwsIllegalStateException() throws Throwable {
        TokenQueue tq = new TokenQueue("Bar");
        try {
            tq.consume("foo");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // consumeTo: seq found returns prefix, leaves rest on queue
    @Test
    public void testConsumeTo_found() throws Throwable {
        TokenQueue tq = new TokenQueue("abc|def");
        assertEquals("abc", tq.consumeTo("|"));
        assertEquals("|def", tq.remainder());
    }

    // consumeTo: seq not found consumes entire remainder
    @Test
    public void testConsumeTo_notFound_returnsRemainder() throws Throwable {
        TokenQueue tq = new TokenQueue("abcdef");
        assertEquals("abcdef", tq.consumeTo("xyz"));
        assertTrue(tq.isEmpty());
    }

    // consumeToIgnoreCase: case-insensitive scan up to terminator
    @Test
    public void testConsumeToIgnoreCase_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("hello</SCRIPT>");
        String data = tq.consumeToIgnoreCase("</script");
        assertEquals("hello", data);
        assertTrue(tq.matches("</SCRIPT>"));
    }

    // consumeToAny: stops at first matching terminator among several
    @Test
    public void testConsumeToAny_multipleTerminators() throws Throwable {
        TokenQueue tq = new TokenQueue("one,two;three");
        String result = tq.consumeToAny(",", ";");
        assertEquals("one", result);
        assertEquals(",two;three", tq.remainder());
    }

    // chompTo: consumes prefix and removes matched terminator from queue
    @Test
    public void testChompTo_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("abc]def");
        String data = tq.chompTo("]");
        assertEquals("abc", data);
        assertEquals("def", tq.remainder());
    }

    // chompToIgnoreCase: consumes prefix case-insensitively and removes terminator
    @Test
    public void testChompToIgnoreCase_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("abcEND rest");
        String data = tq.chompToIgnoreCase("end");
        assertEquals("abc", data);
        assertEquals(" rest", tq.remainder());
    }

    // chompBalanced: simple balanced group leaves remainder untouched
    @Test
    public void testChompBalanced_simple() throws Throwable {
        TokenQueue tq = new TokenQueue(":contains(one (two) three) four");
        String pre = tq.consumeTo("(");
        String guts = tq.chompBalanced('(', ')');
        assertEquals(":contains", pre);
        assertEquals("one (two) three", guts);
        assertEquals(" four", tq.remainder());
    }

    // chompBalanced: nested groups counted correctly via depth
    @Test
    public void testChompBalanced_nested() throws Throwable {
        TokenQueue tq = new TokenQueue("(a(b(c)d)e)rest");
        String guts = tq.chompBalanced('(', ')');
        assertEquals("a(b(c)d)e", guts);
        assertEquals("rest", tq.remainder());
    }

    // chompBalanced: unbalanced input grabs as much as possible until queue empties
    @Test
    public void testChompBalanced_unbalanced_grabsAsMuchAsPossible() throws Throwable {
        TokenQueue tq = new TokenQueue("unbalanced(something(or another");
        tq.consumeTo("(");
        String match = tq.chompBalanced('(', ')');
        assertEquals("something(or another", match);
    }

    // chompBalanced: quoted closer inside is not counted toward depth
    @Test
    public void testChompBalanced_quotedContent() throws Throwable {
        TokenQueue tq = new TokenQueue("(a[b='(')rest");
        String guts = tq.chompBalanced('(', ')');
        assertEquals("a[b='('", guts);
        assertEquals("rest", tq.remainder());
    }

    // unescape: no backslash present, string returned unchanged
    @Test
    public void testUnescape_noEscape() throws Throwable {
        assertEquals("hello", TokenQueue.unescape("hello"));
    }

    // unescape: single escaped char removes the backslash, keeps char
    @Test
    public void testUnescape_singleEscape() throws Throwable {
        assertEquals("a(b", TokenQueue.unescape("a\\(b"));
    }

    // unescape: escaped backslash (\\) yields one literal backslash
    @Test
    public void testUnescape_doubleBackslash_escapedBackslash() throws Throwable {
        assertEquals("a\\b", TokenQueue.unescape("a\\\\b"));
    }

    // Bug hunt: three consecutive backslashes then a char must pair first two
    // into one literal backslash, then the third backslash escapes the char,
    // yielding a single backslash followed by the char (not two backslashes).
    @Test
    public void testUnescape_tripleBackslashFollowedByChar_pairsCorrectly() throws Throwable {
        String input = "\\\\\\(";
        String result = TokenQueue.unescape(input);
        assertEquals("\\(", result);
    }

    // Combined classic scenario: chompBalanced preserves escapes, unescape cleans them
    @Test
    public void testChompEscapedBalanced_combo() throws Throwable {
        TokenQueue tq = new TokenQueue(":contains(one (two) \\( three) four");
        String pre = tq.consumeTo("(");
        String guts = tq.chompBalanced('(', ')');
        assertEquals(":contains", pre);
        assertEquals("one (two) \\( three", guts);
        assertEquals("one (two) ( three", TokenQueue.unescape(guts));
        assertEquals(" four", tq.remainder());
    }

    // consumeWhitespace: true when whitespace consumed, false otherwise
    @Test
    public void testConsumeWhitespace_trueFalse() throws Throwable {
        TokenQueue tq = new TokenQueue("   abc");
        assertTrue(tq.consumeWhitespace());
        assertEquals("abc", tq.remainder());
        assertFalse(new TokenQueue("abc").consumeWhitespace());
    }

    // consumeWord: pulls run of letters/digits
    @Test
    public void testConsumeWord_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("abc123 def");
        assertEquals("abc123", tq.consumeWord());
        assertEquals(" def", tq.remainder());
    }

    // consumeTagName: word chars plus :,_,- allowed
    @Test
    public void testConsumeTagName_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("div:foo-bar baz");
        assertEquals("div:foo-bar", tq.consumeTagName());
        assertEquals(" baz", tq.remainder());
    }

    // consumeElementSelector: word chars plus *|,|,_,- allowed
    @Test
    public void testConsumeElementSelector_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("a|b rest");
        assertEquals("a|b", tq.consumeElementSelector());
        assertEquals(" rest", tq.remainder());
    }

    // consumeCssIdentifier: word chars plus -,_ allowed
    @Test
    public void testConsumeCssIdentifier_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("my-class_1 rest");
        assertEquals("my-class_1", tq.consumeCssIdentifier());
        assertEquals(" rest", tq.remainder());
    }

    // consumeAttributeKey: word chars plus -,_,: allowed, stops at '='
    @Test
    public void testConsumeAttributeKey_basic() throws Throwable {
        TokenQueue tq = new TokenQueue("data-foo:bar=val");
        assertEquals("data-foo:bar", tq.consumeAttributeKey());
        assertEquals("=val", tq.remainder());
    }

    // remainder: returns rest of queue and empties it
    @Test
    public void testRemainder_consumesRest_andIsEmpty() throws Throwable {
        TokenQueue tq = new TokenQueue("abc");
        tq.consume();
        assertEquals("bc", tq.remainder());
        assertTrue(tq.isEmpty());
    }

    // toString: reflects remaining content without consuming
    @Test
    public void testToString_reflectsRemaining() throws Throwable {
        TokenQueue tq = new TokenQueue("abcdef");
        tq.consume();
        tq.consume();
        assertEquals("cdef", tq.toString());
        assertEquals("cdef", tq.remainder());
    }
}
