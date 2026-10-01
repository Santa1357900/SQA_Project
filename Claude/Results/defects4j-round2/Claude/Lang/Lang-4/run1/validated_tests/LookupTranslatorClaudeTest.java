package org.apache.commons.lang3.text.translate;

import java.io.StringWriter;

import org.junit.Test;
import static org.junit.Assert.*;

public class LookupTranslatorClaudeTest {

    // covers: lookup == null -> if (lookup != null) branch skipped, shortest/longest stay at defaults
    @Test
    public void testConstructor_nullLookup_translateReturnsZero() throws Throwable {
        LookupTranslator translator = new LookupTranslator((CharSequence[][]) null);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abc", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // covers: lookup != null but zero-length array, for loop executes 0 times
    @Test
    public void testConstructor_emptyLookupArray_translateReturnsZero() throws Throwable {
        LookupTranslator translator = new LookupTranslator(new CharSequence[0][]);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abc", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // covers: single entry, shortest == longest, exact-length match found on first loop iteration
    @Test
    public void testConstructor_singleEntry_exactLengthMatch() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "cat", "dog" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("cat", 0, writer);
        assertEquals(3, consumed);
        assertEquals("dog", writer.toString());
    }

    // covers: truncation branch (index+longest>length) combined with loop stopping at shortest bound
    @Test
    public void testConstructor_singleEntry_shorterInputNeverMatches() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "cat", "dog" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("ca", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // covers: greedy loop (descending from max) prefers the longest matching key over a shorter one
    @Test
    public void testTranslate_greedyMatch_prefersLongestKey() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", "X" }, { "ab", "Y" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("ab", 0, writer);
        assertEquals(2, consumed);
        assertEquals("Y", writer.toString());
    }

    // covers: loop decrements past a non-matching longer length and falls back to a shorter match
    @Test
    public void testTranslate_fallbackToShorterKey_whenLongerNotFound() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", "X" }, { "abc", "Z" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("ab", 0, writer);
        assertEquals(1, consumed);
        assertEquals("X", writer.toString());
    }

    // covers: for loop completes all iterations with no match, falls through to return 0
    @Test
    public void testTranslate_noMatchAnyLength_returnsZero() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "xyz", "Q" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abc", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // covers: index parameter used as offset into subSequence, not always 0
    @Test
    public void testTranslate_matchAtNonZeroIndex_usesOffsetCorrectly() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "bc", "Y" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abcd", 1, writer);
        assertEquals(2, consumed);
        assertEquals("Y", writer.toString());
    }

    // covers: index == input.length(), max truncated to 0, loop skipped since shortest > 0
    @Test
    public void testTranslate_indexAtEndOfInput_returnsZero() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", "X" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abc", 3, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // covers: index + longest <= input.length() branch, max stays equal to longest (no truncation)
    @Test
    public void testTranslate_noTruncationBranch_maxEqualsLongest() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "ab", "Z" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abcd", 0, writer);
        assertEquals(2, consumed);
        assertEquals("Z", writer.toString());
    }

    // covers: index + longest > input.length() truncates max, loop still finds a shorter key
    @Test
    public void testTranslate_truncationBranch_maxReducedAndMatchFound() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "x", "X" }, { "xyz", "Z" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("wxy", 1, writer);
        assertEquals(1, consumed);
        assertEquals("X", writer.toString());
    }

    // covers: HashMap.put overwrite semantics when two table entries share the same key
    @Test
    public void testConstructor_duplicateKeys_lastEntryWins() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", "first" }, { "a", "second" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("a", 0, writer);
        assertEquals(1, consumed);
        assertEquals("second", writer.toString());
    }

    // covers: seq[0].length() called on a null key -> NullPointerException, no null-check exists
    @Test
    public void testConstructor_nullKey_throwsNullPointerException() throws Throwable {
        try {
            new LookupTranslator(new CharSequence[][] { { null, "x" } });
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // covers: seq[1] access on a sub-array shorter than 2 -> ArrayIndexOutOfBoundsException
    @Test
    public void testConstructor_subArrayTooShort_throwsArrayIndexOutOfBoundsException() throws Throwable {
        try {
            new LookupTranslator(new CharSequence[][] { { "a" } });
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    // covers: result != null check skips an entry whose mapped value is null
    @Test
    public void testTranslate_nullValueEntry_neverMatchesDueToNullCheck() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", null } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("a", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // covers: shortest == 0 (empty-string key) lets the loop reach i == 0 and match "" subSequence
    @Test
    public void testTranslate_emptyKey_matchedAtZeroLengthSubsequence() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "", "EMPTY" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abc", 0, writer);
        assertEquals(0, consumed);
        assertEquals("EMPTY", writer.toString());
    }

    // covers: non-ASCII content is matched correctly through subSequence/equals
    @Test
    public void testTranslate_unicodeKey_matchesCorrectly() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "\u00e9", "e" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("caf\u00e9", 3, writer);
        assertEquals(1, consumed);
        assertEquals("e", writer.toString());
    }

    // covers: translator instance can be reused across multiple independent translate() calls
    @Test
    public void testTranslate_calledTwiceOnSameTranslator_consistentResults() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", "1" }, { "b", "2" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer1 = new StringWriter();
        int consumed1 = translator.translate("a", 0, writer1);
        StringWriter writer2 = new StringWriter();
        int consumed2 = translator.translate("b", 0, writer2);
        assertEquals(1, consumed1);
        assertEquals("1", writer1.toString());
        assertEquals(1, consumed2);
        assertEquals("2", writer2.toString());
    }

    // BUG: per Javadoc/LANG-882 the lookup key must behave like a String (via toString()) for
    // hashCode/equals so a non-String CharSequence key still matches an equivalent String input.
    // StringBuilder implements CharSequence but does not override equals()/hashCode(), so without
    // the toString() conversion the match silently fails.
    @Test
    public void testConstructor_nonStringCharSequenceKey_matchesByContentPerContract() throws Throwable {
        CharSequence key = new StringBuilder("cat");
        CharSequence[][] lookup = new CharSequence[][] { { key, "dog" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("cat", 0, writer);
        assertEquals(3, consumed);
        assertEquals("dog", writer.toString());
    }

    // covers: shortest == longest == 1 for multiple single-character keys
    @Test
    public void testTranslate_singleCharacterKeys_matchEachCharacter() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "x", "1" }, { "y", "2" }, { "z", "3" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("xyz", 1, writer);
        assertEquals(1, consumed);
        assertEquals("2", writer.toString());
    }

    // covers: boundary index + longest == input.length() (not >), so truncation branch is NOT taken
    @Test
    public void testTranslate_indexPlusLongestEqualsLength_noTruncationBoundary() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "yz", "M" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("xyz", 1, writer);
        assertEquals(2, consumed);
        assertEquals("M", writer.toString());
    }

    // covers: longest tracks the maximum key length among several varying-length entries
    @Test
    public void testConstructor_variousLengthKeys_longestUsedForMaxCalculation() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", "1" }, { "abcde", "5" }, { "abc", "3" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("abcde", 0, writer);
        assertEquals(5, consumed);
        assertEquals("5", writer.toString());
    }

    // covers: empty input string, max truncated to 0, loop skipped since shortest > 0
    @Test
    public void testTranslate_emptyInputString_returnsZero() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "a", "X" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // covers: loop lower bound i >= shortest, substrings shorter than shortest are never attempted
    @Test
    public void testTranslate_loopNeverTriesBelowShortestLength() throws Throwable {
        CharSequence[][] lookup = new CharSequence[][] { { "xy", "Y" } };
        LookupTranslator translator = new LookupTranslator(lookup);
        StringWriter writer = new StringWriter();
        int consumed = translator.translate("xz", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }
}
