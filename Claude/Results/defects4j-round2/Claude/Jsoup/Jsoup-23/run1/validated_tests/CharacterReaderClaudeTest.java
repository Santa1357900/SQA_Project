package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class CharacterReaderClaudeTest {

    // pos() should start at 0 for a freshly constructed reader
    @Test
    public void testPos_initial_returnsZero() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertEquals(0, r.pos());
    }

    // isEmpty() false branch: pos < length
    @Test
    public void testIsEmpty_notEmpty_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        assertFalse(r.isEmpty());
    }

    // isEmpty() true branch: pos >= length after consuming all input
    @Test
    public void testIsEmpty_afterConsumingAll_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        r.consume();
        assertTrue(r.isEmpty());
    }

    // current() non-empty branch returns first char without advancing
    @Test
    public void testCurrent_nonEmpty_returnsFirstChar() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertEquals('a', r.current());
        assertEquals(0, r.pos());
    }

    // current() empty branch returns EOF
    @Test
    public void testCurrent_empty_returnsEOF() throws Throwable {
        CharacterReader r = new CharacterReader("");
        assertEquals(CharacterReader.EOF, r.current());
    }

    // consume() non-empty branch returns char and advances pos
    @Test
    public void testConsume_nonEmpty_returnsCharAndAdvances() throws Throwable {
        CharacterReader r = new CharacterReader("ab");
        char c = r.consume();
        assertEquals('a', c);
        assertEquals(1, r.pos());
    }

    // consume() at end returns EOF
    @Test
    public void testConsume_atEnd_returnsEOF() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        r.consume();
        char c = r.consume();
        assertEquals(CharacterReader.EOF, c);
    }

    // unconsume() moves pos back by one after a consume
    @Test
    public void testUnconsume_afterConsume_movesBack() throws Throwable {
        CharacterReader r = new CharacterReader("ab");
        r.consume();
        r.unconsume();
        assertEquals(0, r.pos());
        assertEquals('a', r.current());
    }

    // advance() increments pos by one
    @Test
    public void testAdvance_incrementsPos() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        r.advance();
        assertEquals(1, r.pos());
    }

    // mark()/rewindToMark() restores pos to marked position
    @Test
    public void testMarkAndRewindToMark_restoresPos() throws Throwable {
        CharacterReader r = new CharacterReader("abcd");
        r.advance();
        r.advance();
        r.mark();
        r.advance();
        r.rewindToMark();
        assertEquals(2, r.pos());
    }



    // consumeTo(char) found branch: returns substring up to char and advances
    @Test
    public void testConsumeTo_charFound_returnsSubstringAndAdvances() throws Throwable {
        CharacterReader r = new CharacterReader("foo,bar");
        String s = r.consumeTo(',');
        assertEquals("foo", s);
        assertEquals(3, r.pos());
        assertEquals(',', r.current());
    }

    // consumeTo(char) not found branch: delegates to consumeToEnd
    @Test
    public void testConsumeTo_charNotFound_consumesToEnd() throws Throwable {
        CharacterReader r = new CharacterReader("foobar");
        String s = r.consumeTo(',');
        assertEquals("foobar", s);
        assertTrue(r.isEmpty());
    }

    // consumeTo(String) found branch: returns substring up to sequence
    @Test
    public void testConsumeToString_seqFound_returnsSubstring() throws Throwable {
        CharacterReader r = new CharacterReader("foo</title>bar");
        String s = r.consumeTo("</title>");
        assertEquals("foo", s);
    }

    // consumeTo(String) not found branch: consumes to end
    @Test
    public void testConsumeToString_seqNotFound_consumesToEnd() throws Throwable {
        CharacterReader r = new CharacterReader("foobar");
        String s = r.consumeTo("xyz");
        assertEquals("foobar", s);
    }

    // consumeToAny stops at first matching char in seq
    @Test
    public void testConsumeToAny_matchFound_stopsAtFirstMatch() throws Throwable {
        CharacterReader r = new CharacterReader("foo&bar");
        String s = r.consumeToAny('&', ';');
        assertEquals("foo", s);
        assertEquals('&', r.current());
    }

    // consumeToAny no match consumes entire remaining input
    @Test
    public void testConsumeToAny_noMatch_consumesToEnd() throws Throwable {
        CharacterReader r = new CharacterReader("foobar");
        String s = r.consumeToAny('&', ';');
        assertEquals("foobar", s);
        assertTrue(r.isEmpty());
    }

    // consumeToAny on empty input returns empty string (zero-iteration loop)
    @Test
    public void testConsumeToAny_emptyInput_returnsEmptyString() throws Throwable {
        CharacterReader r = new CharacterReader("");
        String s = r.consumeToAny('&');
        assertEquals("", s);
    }

    // consumeToEnd returns remaining input and advances pos to length
    @Test
    public void testConsumeToEnd_returnsRemainder() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        r.advance();
        String s = r.consumeToEnd();
        assertEquals("ello", s);
        assertTrue(r.isEmpty());
    }

    // consumeLetterSequence stops at first non-letter char
    @Test
    public void testConsumeLetterSequence_lettersOnly_returnsLetters() throws Throwable {
        CharacterReader r = new CharacterReader("abc123");
        String s = r.consumeLetterSequence();
        assertEquals("abc", s);
        assertEquals(3, r.pos());
    }

    // consumeLetterSequence with no leading letters returns empty
    @Test
    public void testConsumeLetterSequence_noLetters_returnsEmpty() throws Throwable {
        CharacterReader r = new CharacterReader("123abc");
        String s = r.consumeLetterSequence();
        assertEquals("", s);
    }

    // consumeHexSequence stops at first non-hex char
    @Test
    public void testConsumeHexSequence_hexDigits_returnsHex() throws Throwable {
        CharacterReader r = new CharacterReader("1a2b;");
        String s = r.consumeHexSequence();
        assertEquals("1a2b", s);
        assertEquals(';', r.current());
    }

    // consumeHexSequence with no hex chars returns empty
    @Test
    public void testConsumeHexSequence_nonHex_returnsEmpty() throws Throwable {
        CharacterReader r = new CharacterReader("zzz");
        String s = r.consumeHexSequence();
        assertEquals("", s);
    }

    // consumeDigitSequence stops at first non-digit char
    @Test
    public void testConsumeDigitSequence_digits_returnsDigits() throws Throwable {
        CharacterReader r = new CharacterReader("123abc");
        String s = r.consumeDigitSequence();
        assertEquals("123", s);
    }

    // consumeDigitSequence with no digits returns empty
    @Test
    public void testConsumeDigitSequence_nonDigit_returnsEmpty() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        String s = r.consumeDigitSequence();
        assertEquals("", s);
    }

    // matches(char) true branch: current char equals arg
    @Test
    public void testMatchesChar_matchTrue_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertTrue(r.matches('a'));
    }

    // matches(char) false branch: current char differs
    @Test
    public void testMatchesChar_noMatch_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertFalse(r.matches('b'));
    }

    // matches(String) true branch: input starts with seq at pos
    @Test
    public void testMatchesString_matchTrue_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("hello world");
        assertTrue(r.matches("hello"));
    }

    // matches(String) false branch: input does not start with seq
    @Test
    public void testMatchesString_noMatch_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        assertFalse(r.matches("world"));
    }

    // matchesIgnoreCase true branch: case-insensitive region match
    @Test
    public void testMatchesIgnoreCase_caseInsensitiveMatch_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("HELLO");
        assertTrue(r.matchesIgnoreCase("hello"));
    }

    // matchesIgnoreCase false branch
    @Test
    public void testMatchesIgnoreCase_noMatch_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        assertFalse(r.matchesIgnoreCase("world"));
    }

    // matchesAny true branch: one of the seq chars equals current char
    @Test
    public void testMatchesAny_matchFound_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertTrue(r.matchesAny('x', 'a'));
    }

    // matchesAny false branch: none of the seq chars match
    @Test
    public void testMatchesAny_noMatch_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertFalse(r.matchesAny('x', 'y'));
    }

    // matchesAny on empty input returns false via isEmpty short-circuit
    @Test
    public void testMatchesAny_emptyInput_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("");
        assertFalse(r.matchesAny('a'));
    }

    // matchesLetter true branch for an alphabetic current char
    @Test
    public void testMatchesLetter_letterChar_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertTrue(r.matchesLetter());
    }

    // matchesDigit true branch for a numeric current char
    @Test
    public void testMatchesDigit_digitChar_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("123");
        assertTrue(r.matchesDigit());
    }

    // matchConsume true branch: matches and advances pos by seq length
    @Test
    public void testMatchConsume_matches_advancesAndReturnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("hello world");
        boolean matched = r.matchConsume("hello");
        assertTrue(matched);
        assertEquals(5, r.pos());
    }

    // matchConsume false branch: no match, pos unchanged
    @Test
    public void testMatchConsume_noMatch_returnsFalseAndNoAdvance() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        boolean matched = r.matchConsume("world");
        assertFalse(matched);
        assertEquals(0, r.pos());
    }

    // matchConsumeIgnoreCase true branch: case-insensitive match advances pos
    @Test
    public void testMatchConsumeIgnoreCase_matches_advancesAndReturnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("HELLO");
        boolean matched = r.matchConsumeIgnoreCase("hello");
        assertTrue(matched);
        assertEquals(5, r.pos());
    }

    // matchConsumeIgnoreCase false branch: no match, pos unchanged
    @Test
    public void testMatchConsumeIgnoreCase_noMatch_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        boolean matched = r.matchConsumeIgnoreCase("world");
        assertFalse(matched);
        assertEquals(0, r.pos());
    }

    // containsIgnoreCase true branch via lowercase indexOf
    @Test
    public void testContainsIgnoreCase_lowerCaseMatch_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("<p>text</title>");
        assertTrue(r.containsIgnoreCase("</title>"));
    }

    // containsIgnoreCase true branch via uppercase indexOf (second half of ||)
    @Test
    public void testContainsIgnoreCase_upperCaseMatch_returnsTrue() throws Throwable {
        CharacterReader r = new CharacterReader("<P>TEXT</TITLE>");
        assertTrue(r.containsIgnoreCase("</title>"));
    }

    // containsIgnoreCase false branch: neither case variant present
    @Test
    public void testContainsIgnoreCase_noMatch_returnsFalse() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        assertFalse(r.containsIgnoreCase("</title>"));
    }

    // toString returns the remaining unconsumed input
    @Test
    public void testToString_returnsRemainingInput() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        r.advance();
        assertEquals("ello", r.toString());
    }

    // constructor normalises \r\n and lone \r into \n
    @Test
    public void testConstructor_normalizesCarriageReturns_toNewline() throws Throwable {
        CharacterReader r = new CharacterReader("x\r\ny\rz");
        assertEquals("x\ny\nz", r.toString());
    }
}
