package org.jsoup.parser;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.StringReader;

public class CharacterReaderClaudeTest {

    // Covers CharacterReader(String): initial position is 0 and current() yields first char
    @Test
    public void testConstructorString_initializesAtPositionZero() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertEquals(0, r.pos());
        assertEquals('a', r.current());
    }

    // Covers CharacterReader(Reader, int): buffers and reads content correctly
    @Test
    public void testConstructorReaderWithSize_readsContentCorrectly() throws Throwable {
        CharacterReader r = new CharacterReader(new StringReader("abc"), 10);
        assertEquals('a', r.current());
        assertEquals(0, r.pos());
    }

    // Covers pos(): tracks cursor position accurately as content is advanced
    @Test
    public void testPos_afterAdvance_returnsCorrectPosition() throws Throwable {
        CharacterReader r = new CharacterReader("abcdef");
        r.advance();
        r.advance();
        assertEquals(2, r.pos());
    }

    // Covers isEmpty(): false branch when data remains, true branch after consuming all
    @Test
    public void testIsEmpty_falseThenTrueAfterConsumingAll() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        assertFalse(r.isEmpty());
        r.advance();
        assertTrue(r.isEmpty());
    }

    // Covers current(): normal branch and EOF branch after exhausting buffer
    @Test
    public void testCurrent_atStartAndAtEOF() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        assertEquals('a', r.current());
        r.advance();
        assertEquals(CharacterReader.EOF, r.current());
    }

    // Covers consume(): normal branch (returns char, advances) and EOF branch
    @Test
    public void testConsume_returnsCharAdvancesAndEOFSentinel() throws Throwable {
        CharacterReader r = new CharacterReader("x");
        char c1 = r.consume();
        assertEquals('x', c1);
        assertEquals(1, r.pos());
        char c2 = r.consume();
        assertEquals(CharacterReader.EOF, c2);
    }

    // Covers unconsume(): position decrements, previous char becomes current again
    @Test
    public void testUnconsume_movesPositionBackByOne() throws Throwable {
        CharacterReader r = new CharacterReader("xy");
        r.consume();
        r.unconsume();
        assertEquals('x', r.current());
        assertEquals(0, r.pos());
    }

    // Covers advance(): moves cursor position forward by exactly one
    @Test
    public void testAdvance_movesPositionForwardByOne() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        r.advance();
        assertEquals(1, r.pos());
        assertEquals('b', r.current());
    }

    // Covers mark() and rewindToMark(): restores position saved earlier
    @Test
    public void testMarkAndRewindToMark_restoresPosition() throws Throwable {
        CharacterReader r = new CharacterReader("abcdef");
        r.advance();
        r.advance();
        r.advance();
        r.mark();
        r.advance();
        r.advance();
        r.rewindToMark();
        assertEquals(3, r.pos());
        assertEquals('d', r.current());
    }

    // Covers nextIndexOf(char): found returns offset, not found returns -1
    @Test
    public void testNextIndexOfChar_foundAndNotFound() throws Throwable {
        CharacterReader r = new CharacterReader("abcde");
        assertEquals(2, r.nextIndexOf('c'));
        assertEquals(-1, r.nextIndexOf('z'));
    }

    // Covers nextIndexOf(CharSequence): found returns offset, not found returns -1
    @Test
    public void testNextIndexOfSeq_foundAndNotFound() throws Throwable {
        CharacterReader r = new CharacterReader("xxabcyy");
        assertEquals(2, r.nextIndexOf("abc"));
        assertEquals(-1, r.nextIndexOf("zzz"));
    }

    // Covers consumeTo(char): found branch stops before delimiter, not-found consumes to end
    @Test
    public void testConsumeToChar_foundAndNotFound() throws Throwable {
        CharacterReader r1 = new CharacterReader("foo,bar");
        assertEquals("foo", r1.consumeTo(','));
        assertEquals(',', r1.current());

        CharacterReader r2 = new CharacterReader("foobar");
        assertEquals("foobar", r2.consumeTo('x'));
        assertTrue(r2.isEmpty());
    }

    // Covers consumeTo(String): found branch and not-found branch (delegates to consumeToEnd)
    @Test
    public void testConsumeToSeq_foundAndNotFound() throws Throwable {
        CharacterReader r1 = new CharacterReader("hello--world");
        assertEquals("hello", r1.consumeTo("--"));

        CharacterReader r2 = new CharacterReader("helloworld");
        assertEquals("helloworld", r2.consumeTo("--"));
    }

    // Covers consumeToAny(): stops at first matching delimiter among several
    @Test
    public void testConsumeToAny_stopsAtFirstDelimiter() throws Throwable {
        CharacterReader r = new CharacterReader("a,b;c");
        String s = r.consumeToAny(',', ';');
        assertEquals("a", s);
        assertEquals(',', r.current());
    }

    // Covers consumeToAny(): delimiter at position 0 yields empty string (bufPos>start branch false)
    @Test
    public void testConsumeToAny_delimiterAtStart_returnsEmptyString() throws Throwable {
        CharacterReader r = new CharacterReader(",abc");
        String s = r.consumeToAny(',', ';');
        assertEquals("", s);
    }

    // Covers consumeToAnySorted(): stops when a sorted delimiter is encountered
    @Test
    public void testConsumeToAnySorted_stopsAtDelimiter() throws Throwable {
        CharacterReader r = new CharacterReader("abc;def");
        String s = r.consumeToAnySorted(new char[] {';'});
        assertEquals("abc", s);
    }

    // Covers consumeData(): stops at '&' delimiter
    @Test
    public void testConsumeData_stopsAtAmpersand() throws Throwable {
        CharacterReader r = new CharacterReader("foo&bar");
        assertEquals("foo", r.consumeData());
    }

    // Covers consumeData(): stops at '<' delimiter
    @Test
    public void testConsumeData_stopsAtLessThan() throws Throwable {
        CharacterReader r = new CharacterReader("foo<bar");
        assertEquals("foo", r.consumeData());
    }

    // Covers consumeTagName(): stops at whitespace delimiter
    @Test
    public void testConsumeTagName_stopsAtWhitespace() throws Throwable {
        CharacterReader r = new CharacterReader("div class");
        assertEquals("div", r.consumeTagName());
    }

    // Covers consumeTagName(): stops at '>' delimiter
    @Test
    public void testConsumeTagName_stopsAtGreaterThan() throws Throwable {
        CharacterReader r = new CharacterReader("img>");
        assertEquals("img", r.consumeTagName());
    }

    // Covers consumeToEnd(): consumes and returns all remaining content
    @Test
    public void testConsumeToEnd_returnsRemainingAndEmptiesBuffer() throws Throwable {
        CharacterReader r = new CharacterReader("remainder");
        assertEquals("remainder", r.consumeToEnd());
        assertTrue(r.isEmpty());
    }

    // Covers consumeLetterSequence(): stops at first non-letter character
    @Test
    public void testConsumeLetterSequence_stopsAtNonLetter() throws Throwable {
        CharacterReader r = new CharacterReader("abc123");
        assertEquals("abc", r.consumeLetterSequence());
    }

    // Covers consumeLetterSequence(): Character.isLetter branch for non-ASCII letters
    @Test
    public void testConsumeLetterSequence_includesUnicodeLetters() throws Throwable {
        CharacterReader r = new CharacterReader("caf\u00e9123");
        assertEquals("caf\u00e9", r.consumeLetterSequence());
    }

    // Covers consumeLetterThenDigitSequence(): letters then digits both consumed
    @Test
    public void testConsumeLetterThenDigitSequence_lettersFollowedByDigits() throws Throwable {
        CharacterReader r = new CharacterReader("amp123;");
        assertEquals("amp123", r.consumeLetterThenDigitSequence());
    }

    // Covers consumeLetterThenDigitSequence(): zero letters, then digit run consumed
    @Test
    public void testConsumeLetterThenDigitSequence_onlyDigitsNoLeadingLetters() throws Throwable {
        CharacterReader r = new CharacterReader("123abc");
        assertEquals("123", r.consumeLetterThenDigitSequence());
    }

    // Covers consumeHexSequence(): stops at first non-hex-digit character
    @Test
    public void testConsumeHexSequence_stopsAtNonHexChar() throws Throwable {
        CharacterReader r = new CharacterReader("1A2fg");
        assertEquals("1A2f", r.consumeHexSequence());
    }

    // Covers consumeDigitSequence(): stops at first non-digit character
    @Test
    public void testConsumeDigitSequence_stopsAtNonDigit() throws Throwable {
        CharacterReader r = new CharacterReader("456ab");
        assertEquals("456", r.consumeDigitSequence());
    }

    // Covers matches(char): true when current char equals, false otherwise
    @Test
    public void testMatchesChar_trueAndFalseCases() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertTrue(r.matches('a'));
        assertFalse(r.matches('b'));
    }

    // Covers matches(String): true for exact prefix, false when seq longer than remaining
    @Test
    public void testMatchesString_trueForExactPrefixAndFalseWhenTooLong() throws Throwable {
        CharacterReader r1 = new CharacterReader("hello world");
        assertTrue(r1.matches("hello"));

        CharacterReader r2 = new CharacterReader("hi");
        assertFalse(r2.matches("hello"));
    }

    // Covers matchesIgnoreCase(): true when letters match regardless of case
    @Test
    public void testMatchesIgnoreCase_matchesRegardlessOfCase() throws Throwable {
        CharacterReader r = new CharacterReader("HeLLo world");
        assertTrue(r.matchesIgnoreCase("hello"));
    }

    // Covers matchesAny(): true when current char in set, false otherwise, false when empty
    @Test
    public void testMatchesAny_trueFalseAndEmptyCases() throws Throwable {
        CharacterReader r = new CharacterReader("xabc");
        assertTrue(r.matchesAny('x', 'y'));
        assertFalse(r.matchesAny('a', 'b'));

        CharacterReader empty = new CharacterReader("");
        assertFalse(empty.matchesAny('a', 'b'));
    }

    // Covers matchesAnySorted(): true when current char found via binary search in sorted array
    @Test
    public void testMatchesAnySorted_trueWhenCurrentCharInSortedSet() throws Throwable {
        CharacterReader r = new CharacterReader("bxyz");
        assertTrue(r.matchesAnySorted(new char[] {'a', 'b', 'c'}));
    }

    // Covers matchesLetter(): true for letter, false for digit, false when empty
    @Test
    public void testMatchesLetter_trueForLetterFalseForDigitAndEmpty() throws Throwable {
        CharacterReader r = new CharacterReader("a1");
        assertTrue(r.matchesLetter());
        r.advance();
        assertFalse(r.matchesLetter());

        CharacterReader empty = new CharacterReader("");
        assertFalse(empty.matchesLetter());
    }

    // Covers matchesDigit(): true for digit, false for letter
    @Test
    public void testMatchesDigit_trueForDigitFalseForLetter() throws Throwable {
        CharacterReader r = new CharacterReader("1a");
        assertTrue(r.matchesDigit());
        r.advance();
        assertFalse(r.matchesDigit());
    }

    // Covers matchConsume(): success advances position by seq length, failure leaves it unchanged
    @Test
    public void testMatchConsume_successAndFailure() throws Throwable {
        CharacterReader r = new CharacterReader("foobar");
        assertTrue(r.matchConsume("foo"));
        assertEquals(3, r.pos());
        assertFalse(r.matchConsume("baz"));
        assertEquals(3, r.pos());
    }

    // Covers matchConsumeIgnoreCase(): success consumes ignoring case, failure does not consume
    @Test
    public void testMatchConsumeIgnoreCase_successAndFailure() throws Throwable {
        CharacterReader r = new CharacterReader("FOObar");
        assertTrue(r.matchConsumeIgnoreCase("foo"));
        assertEquals(3, r.pos());
        assertFalse(r.matchConsumeIgnoreCase("xyz"));
    }

    // Covers containsIgnoreCase(): finds consistent-case occurrence, false when absent
    @Test
    public void testContainsIgnoreCase_foundAndNotFound() throws Throwable {
        CharacterReader r1 = new CharacterReader("blah blah </TITLE> more");
        assertTrue(r1.containsIgnoreCase("</title>"));

        CharacterReader r2 = new CharacterReader("blah blah");
        assertFalse(r2.containsIgnoreCase("</title>"));
    }

    // Covers toString(): returns remaining unconsumed content from current position
    @Test
    public void testToString_returnsRemainingUnconsumedContent() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        r.advance();
        r.advance();
        assertEquals("llo", r.toString());
    }

    // Covers rangeEquals(): true on exact match, false on content mismatch, false on length mismatch
    @Test
    public void testRangeEquals_trueFalseContentAndLength() throws Throwable {
        CharacterReader r = new CharacterReader("hello world");
        assertTrue(r.rangeEquals(0, 5, "hello"));
        assertFalse(r.rangeEquals(0, 5, "HELLO"));
        assertFalse(r.rangeEquals(0, 4, "hello"));
    }
}
