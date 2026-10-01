package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class CharacterReaderClaudeTest {

    // covers Validate.notNull(input) throwing on null constructor argument
    @Test
    public void testConstructor_nullInput_throwsException() throws Throwable {
        try {
            new CharacterReader(null);
            fail("expected exception for null input");
        } catch (Exception expected) {
        }
    }

    // covers constructor happy path and pos() initial value
    @Test
    public void testConstructor_validInput_initialPosIsZero() throws Throwable {
        CharacterReader r = new CharacterReader("test");
        assertEquals(0, r.pos());
        assertFalse(r.isEmpty());
    }

    // covers isEmpty() true branch for empty string
    @Test
    public void testIsEmpty_emptyString_true() throws Throwable {
        CharacterReader r = new CharacterReader("");
        assertTrue(r.isEmpty());
    }

    // covers isEmpty() false branch when pos < length
    @Test
    public void testIsEmpty_nonEmptyAtStart_false() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        assertFalse(r.isEmpty());
    }

    // covers isEmpty() true branch after consuming all characters
    @Test
    public void testIsEmpty_afterConsumingAll_true() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        r.consume();
        assertTrue(r.isEmpty());
    }

    // covers current() non-empty branch, must not consume
    @Test
    public void testCurrent_nonEmpty_returnsCharWithoutConsuming() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertEquals('a', r.current());
        assertEquals(0, r.pos());
    }

    // covers current() isEmpty branch returning EOF
    @Test
    public void testCurrent_empty_returnsEOF() throws Throwable {
        CharacterReader r = new CharacterReader("");
        assertEquals(CharacterReader.EOF, r.current());
    }

    // covers consume() non-empty branch and pos advancing
    @Test
    public void testConsume_advancesPosAndReturnsChar() throws Throwable {
        CharacterReader r = new CharacterReader("ab");
        assertEquals('a', r.consume());
        assertEquals(1, r.pos());
        assertEquals('b', r.consume());
        assertEquals(2, r.pos());
    }

    // covers consume() isEmpty branch returning EOF
    @Test
    public void testConsume_atEmptyInput_returnsEOF() throws Throwable {
        CharacterReader r = new CharacterReader("");
        assertEquals(CharacterReader.EOF, r.consume());
    }

    // covers unconsume() restoring previous position
    @Test
    public void testUnconsume_afterConsume_restoresPreviousChar() throws Throwable {
        CharacterReader r = new CharacterReader("ab");
        r.consume();
        r.unconsume();
        assertEquals(0, r.pos());
        assertEquals('a', r.current());
    }

    // covers advance() incrementing pos
    @Test
    public void testAdvance_incrementsPos() throws Throwable {
        CharacterReader r = new CharacterReader("ab");
        r.advance();
        assertEquals(1, r.pos());
    }

    // covers mark() and rewindToMark() restoring saved position
    @Test
    public void testMarkAndRewindToMark_restoresPosition() throws Throwable {
        CharacterReader r = new CharacterReader("abcdef");
        r.advance();
        r.advance();
        r.mark();
        r.advance();
        r.advance();
        r.rewindToMark();
        assertEquals(2, r.pos());
    }



    // covers consumeTo(char) found branch
    @Test
    public void testConsumeToChar_found_returnsSubstringUpToChar() throws Throwable {
        CharacterReader r = new CharacterReader("abcdef");
        String s = r.consumeTo('d');
        assertEquals("abc", s);
        assertEquals(3, r.pos());
    }

    // covers consumeTo(char) not-found branch delegating to consumeToEnd()
    @Test
    public void testConsumeToChar_notFound_consumesEntireRemainingInput() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        String s = r.consumeTo('z');
        assertEquals("abc", s);
        assertTrue(r.isEmpty());
    }

    // covers consumeTo(String) found branch
    @Test
    public void testConsumeToString_found_returnsSubstringUpToSeq() throws Throwable {
        CharacterReader r = new CharacterReader("hello world");
        String s = r.consumeTo("world");
        assertEquals("hello ", s);
        assertEquals(6, r.pos());
    }

    // covers consumeTo(String) not-found branch delegating to consumeToEnd()
    @Test
    public void testConsumeToString_notFound_consumesEntireRemainingInput() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        String s = r.consumeTo("xyz");
        assertEquals("abc", s);
        assertTrue(r.isEmpty());
    }

    // covers consumeToAny() immediate match branch, 0 loop iterations
    @Test
    public void testConsumeToAny_immediateMatch_returnsEmptyString() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        String s = r.consumeToAny('a');
        assertEquals("", s);
        assertEquals(0, r.pos());
    }

    // covers consumeToAny() multiple loop iterations before match
    @Test
    public void testConsumeToAny_multipleChars_stopsAtMatch() throws Throwable {
        CharacterReader r = new CharacterReader("abcdef");
        String s = r.consumeToAny('d', 'e');
        assertEquals("abc", s);
        assertEquals(3, r.pos());
    }

    // covers consumeToAny() no-match branch consuming to end via isEmpty()
    @Test
    public void testConsumeToAny_noMatch_consumesToEnd() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        String s = r.consumeToAny('z');
        assertEquals("abc", s);
        assertTrue(r.isEmpty());
    }

    // covers consumeToEnd() returning the full remaining string including last char
    @Test
    public void testConsumeToEnd_returnsAllRemainingChars() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        String s = r.consumeToEnd();
        assertEquals("abc", s);
        assertTrue(r.isEmpty());
    }

    // covers consumeToEnd() on empty input, must not throw and return empty string
    @Test
    public void testConsumeToEnd_emptyInput_returnsEmptyString() throws Throwable {
        CharacterReader r = new CharacterReader("");
        String s = r.consumeToEnd();
        assertEquals("", s);
        assertTrue(r.isEmpty());
    }

    // covers consumeLetterSequence() stopping at first non-letter char
    @Test
    public void testConsumeLetterSequence_stopsAtNonLetter() throws Throwable {
        CharacterReader r = new CharacterReader("abc123");
        String s = r.consumeLetterSequence();
        assertEquals("abc", s);
        assertEquals(3, r.pos());
    }

    // covers consumeLetterSequence() 0 loop iterations, no letters at start
    @Test
    public void testConsumeLetterSequence_noLetters_returnsEmptyString() throws Throwable {
        CharacterReader r = new CharacterReader("123abc");
        String s = r.consumeLetterSequence();
        assertEquals("", s);
        assertEquals(0, r.pos());
    }

    // covers consumeLetterSequence() consuming all chars until isEmpty()
    @Test
    public void testConsumeLetterSequence_allLettersToEnd() throws Throwable {
        CharacterReader r = new CharacterReader("abcXYZ");
        String s = r.consumeLetterSequence();
        assertEquals("abcXYZ", s);
        assertTrue(r.isEmpty());
    }

    // covers consumeHexSequence() accepting digits and upper/lower hex letters
    @Test
    public void testConsumeHexSequence_mixedCaseHexDigits() throws Throwable {
        CharacterReader r = new CharacterReader("AF12xyz");
        String s = r.consumeHexSequence();
        assertEquals("AF12", s);
        assertEquals(4, r.pos());
    }

    // covers consumeHexSequence() stopping at a non-hex character
    @Test
    public void testConsumeHexSequence_stopsAtNonHexChar() throws Throwable {
        CharacterReader r = new CharacterReader("1a2G");
        String s = r.consumeHexSequence();
        assertEquals("1a2", s);
        assertEquals(3, r.pos());
    }

    // covers consumeDigitSequence() stopping at first non-digit char
    @Test
    public void testConsumeDigitSequence_stopsAtNonDigit() throws Throwable {
        CharacterReader r = new CharacterReader("42x");
        String s = r.consumeDigitSequence();
        assertEquals("42", s);
        assertEquals(2, r.pos());
    }

    // covers consumeDigitSequence() consuming all chars until isEmpty()
    @Test
    public void testConsumeDigitSequence_allDigitsToEnd() throws Throwable {
        CharacterReader r = new CharacterReader("007");
        String s = r.consumeDigitSequence();
        assertEquals("007", s);
        assertTrue(r.isEmpty());
    }

    // covers matches(char): match, mismatch and isEmpty short-circuit branches
    @Test
    public void testMatchesChar_trueAndFalseCases() throws Throwable {
        CharacterReader r = new CharacterReader("a");
        assertTrue(r.matches('a'));
        assertFalse(r.matches('b'));
        r.consume();
        assertFalse(r.matches('a'));
    }

    // covers matches(String) true and false branches via startsWith
    @Test
    public void testMatchesString_trueAndFalseCases() throws Throwable {
        CharacterReader r = new CharacterReader("hello");
        assertTrue(r.matches("hel"));
        assertFalse(r.matches("world"));
    }

    // covers matchesIgnoreCase() true for different case and false for mismatch
    @Test
    public void testMatchesIgnoreCase_trueForDifferentCase() throws Throwable {
        CharacterReader r = new CharacterReader("Hello");
        assertTrue(r.matchesIgnoreCase("hello"));
        assertTrue(r.matchesIgnoreCase("HELLO"));
        assertFalse(r.matchesIgnoreCase("world"));
    }

    // covers matchesAny() true branch, false branch, and isEmpty() branch
    @Test
    public void testMatchesAny_trueWhenCharPresent_falseWhenEmpty() throws Throwable {
        CharacterReader r = new CharacterReader("abc");
        assertTrue(r.matchesAny('x', 'a'));
        assertFalse(r.matchesAny('x', 'y'));
        CharacterReader empty = new CharacterReader("");
        assertFalse(empty.matchesAny('a'));
    }

    // covers matchesLetter() true and false branches
    @Test
    public void testMatchesLetter_trueAndFalse() throws Throwable {
        CharacterReader r = new CharacterReader("a1");
        assertTrue(r.matchesLetter());
        r.consume();
        assertFalse(r.matchesLetter());
    }

    // covers matchesDigit() true and false branches
    @Test
    public void testMatchesDigit_trueAndFalse() throws Throwable {
        CharacterReader r = new CharacterReader("1a");
        assertTrue(r.matchesDigit());
        r.consume();
        assertFalse(r.matchesDigit());
    }

    // covers matchConsume() true branch advancing pos and false branch leaving pos unchanged
    @Test
    public void testMatchConsume_matchAdvancesPos_noMatchLeavesPos() throws Throwable {
        CharacterReader r = new CharacterReader("hello world");
        assertTrue(r.matchConsume("hello"));
        assertEquals(5, r.pos());
        assertFalse(r.matchConsume("xyz"));
        assertEquals(5, r.pos());
    }

    // covers matchConsumeIgnoreCase() true branch advancing pos with case-insensitive match
    @Test
    public void testMatchConsumeIgnoreCase_matchAdvancesPos() throws Throwable {
        CharacterReader r = new CharacterReader("HELLO world");
        assertTrue(r.matchConsumeIgnoreCase("hello"));
        assertEquals(5, r.pos());
        assertFalse(r.matchConsumeIgnoreCase("xyz"));
    }

    // covers containsIgnoreCase() true for lowercase match and false when absent
    @Test
    public void testContainsIgnoreCase_trueForLowerAndUpperCase_falseWhenAbsent() throws Throwable {
        CharacterReader r1 = new CharacterReader("prefix </title> suffix");
        assertTrue(r1.containsIgnoreCase("</title>"));
        CharacterReader r2 = new CharacterReader("no closing tag here");
        assertFalse(r2.containsIgnoreCase("</title>"));
    }

    // covers toString() returning the remaining unconsumed input
    @Test
    public void testToString_returnsRemainingInput() throws Throwable {
        CharacterReader r = new CharacterReader("abcdef");
        r.consume();
        r.consume();
        assertEquals("cdef", r.toString());
    }
}
