package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class StringUtilsClaudeTest {

    // isEmpty: null -> true, "" -> true, non-empty/space -> false
    @Test
    public void testIsEmpty_nullEmptyNonEmpty() throws Throwable {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty("bob"));
        assertFalse(StringUtils.isEmpty(" "));
    }

    // isBlank: null/empty/whitespace -> true, non-blank text -> false
    @Test
    public void testIsBlank_nullEmptyWhitespaceText() throws Throwable {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank("   "));
        assertFalse(StringUtils.isBlank("bob"));
    }

    // trim: null -> null, surrounding spaces removed, all-space -> ""
    @Test
    public void testTrim_nullAndSpaces() throws Throwable {
        assertNull(StringUtils.trim(null));
        assertEquals("abc", StringUtils.trim("   abc   "));
        assertEquals("", StringUtils.trim("     "));
    }

    // trimToNull: blank/null -> null, text -> trimmed text
    @Test
    public void testTrimToNull_blankAndText() throws Throwable {
        assertNull(StringUtils.trimToNull("   "));
        assertNull(StringUtils.trimToNull(null));
        assertEquals("abc", StringUtils.trimToNull("  abc  "));
    }

    // strip(String): null-safe whitespace stripping from both ends
    @Test
    public void testStrip_defaultWhitespace() throws Throwable {
        assertNull(StringUtils.strip(null));
        assertEquals("ab c", StringUtils.strip(" ab c "));
    }

    // stripStart: custom stripChars removes only leading matches, empty stripChars -> unchanged
    @Test
    public void testStripStart_customChars() throws Throwable {
        assertEquals("abc  ", StringUtils.stripStart("yxabc  ", "xyz"));
        assertEquals("abc", StringUtils.stripStart("abc", ""));
    }

    // stripEnd: empty stripChars returns input unchanged (branch: stripChars.length()==0)
    @Test
    public void testStripEnd_emptyStripCharsUnchanged() throws Throwable {
        assertEquals("abc", StringUtils.stripEnd("abc", ""));
        assertEquals("12", StringUtils.stripEnd("120.00", ".0"));
    }

    // equals: both null -> true, one null -> false, case-sensitive mismatch -> false
    @Test
    public void testEquals_nullSafeCaseSensitive() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals(null, "abc"));
        assertFalse(StringUtils.equals("abc", "ABC"));
        assertTrue(StringUtils.equals("abc", "abc"));
    }

    // equalsIgnoreCase: case-insensitive match, length mismatch -> false, both null -> true
    @Test
    public void testEqualsIgnoreCase_caseInsensitive() throws Throwable {
        assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
        assertFalse(StringUtils.equalsIgnoreCase("abc", "abcd"));
        assertTrue(StringUtils.equalsIgnoreCase(null, null));
    }

    // indexOf: char-empty->-1 / found index; CharSequence null->-1, empty search->0
    @Test
    public void testIndexOf_charAndSequenceVariants() throws Throwable {
        assertEquals(-1, StringUtils.indexOf("", 'a'));
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b'));
        assertEquals(-1, StringUtils.indexOf("abc", (CharSequence) null));
        assertEquals(0, StringUtils.indexOf("aabaabaa", ""));
    }

    // ordinalIndexOf: nth occurrence found; ordinal<=0 returns -1
    @Test
    public void testOrdinalIndexOf_nthOccurrence() throws Throwable {
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "a", 2));
        assertEquals(-1, StringUtils.ordinalIndexOf("aabaabaa", "a", 0));
    }



    // lastIndexOf(char,startPos) and lastOrdinalIndexOf: backward search variants
    @Test
    public void testLastIndexOfChar_and_lastOrdinalIndexOf() throws Throwable {
        assertEquals(2, StringUtils.lastIndexOf("aabaabaa", 'b', 4));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", 'b', 0));
        assertEquals(2, StringUtils.lastOrdinalIndexOf("aabaabaa", "b", 2));
    }

    // contains: char found/null-safe false; empty search CharSequence always matches
    @Test
    public void testContains_charAndSequence() throws Throwable {
        assertTrue(StringUtils.contains("abc", 'a'));
        assertFalse(StringUtils.contains(null, 'a'));
        assertTrue(StringUtils.contains("abc", ""));
    }

    // containsIgnoreCase: case-insensitive substring match true/false
    @Test
    public void testContainsIgnoreCase_trueAndFalse() throws Throwable {
        assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
        assertFalse(StringUtils.containsIgnoreCase("abc", "Z"));
    }

    // containsOnly/containsNone: valid-char-set membership checks
    @Test
    public void testContainsOnlyAndContainsNone() throws Throwable {
        assertTrue(StringUtils.containsOnly("abab", new char[]{'a', 'b', 'c'}));
        assertFalse(StringUtils.containsOnly("ab1", new char[]{'a', 'b', 'c'}));
        assertTrue(StringUtils.containsNone("abab", "xyz"));
        assertFalse(StringUtils.containsNone("abz", "xyz"));
    }

    // substring(str,start): negative counts from end; overflow start returns empty
    @Test
    public void testSubstring_negativeAndOverflowStart() throws Throwable {
        assertEquals("bc", StringUtils.substring("abc", -2));
        assertEquals("", StringUtils.substring("abc", 4));
    }

    // substring(str,start,end): start>end -> empty; negative end counts from end
    @Test
    public void testSubstring_startGreaterThanEndOrNegativeEnd() throws Throwable {
        assertEquals("", StringUtils.substring("abc", 2, 0));
        assertEquals("b", StringUtils.substring("abc", -2, -1));
    }

    // left/right/mid: boundary lengths (zero, exceeding length)
    @Test
    public void testLeftRightMid_boundaries() throws Throwable {
        assertEquals("", StringUtils.left("abc", 0));
        assertEquals("abc", StringUtils.right("abc", 4));
        assertEquals("", StringUtils.mid("abc", 4, 2));
    }

    // substringBefore/After: separator found, not found, null separator
    @Test
    public void testSubstringBeforeAfter_separatorHandling() throws Throwable {
        assertEquals("ab", StringUtils.substringBefore("abc", "c"));
        assertEquals("abc", StringUtils.substringBefore("abc", null));
        assertEquals("", StringUtils.substringAfter("abc", "d"));
    }

    // substringBetween(open,close): finds nested substring; no match -> null
    @Test
    public void testSubstringBetween_openCloseTags() throws Throwable {
        assertEquals("b", StringUtils.substringBetween("wx[b]yz", "[", "]"));
        assertNull(StringUtils.substringBetween("", "[", "]"));
    }

    // substringsBetween: multiple matches returned as an array
    @Test
    public void testSubstringsBetween_multipleMatches() throws Throwable {
        String[] result = StringUtils.substringsBetween("[a][b][c]", "[", "]");
        assertArrayEquals(new String[]{"a", "b", "c"}, result);
    }

    // split(str): adjacent whitespace treated as a single separator; null -> null
    @Test
    public void testSplit_whitespaceAdjacentSeparators() throws Throwable {
        String[] result = StringUtils.split("abc  def");
        assertArrayEquals(new String[]{"abc", "def"}, result);
        assertNull(StringUtils.split(null));
    }

    // split(str,sepChars,max): max limits tokens, remainder kept whole in last token
    @Test
    public void testSplit_withMaxLimit() throws Throwable {
        String[] result = StringUtils.split("ab:cd:ef", ":", 2);
        assertArrayEquals(new String[]{"ab", "cd:ef"}, result);
    }

    // join(Object[],char): null/empty entries become empty strings
    @Test
    public void testJoin_objectArrayWithChar() throws Throwable {
        assertEquals("a;b;c", StringUtils.join(new Object[]{"a", "b", "c"}, ';'));
        assertEquals(";;a", StringUtils.join(new Object[]{null, "", "a"}, ';'));
    }

    // removeStart/removeEnd: prefix/suffix removed only when actually present
    @Test
    public void testRemoveStartAndEnd_prefixSuffix() throws Throwable {
        assertEquals("domain.com", StringUtils.removeStart("www.domain.com", "www."));
        assertEquals("www.domain", StringUtils.removeEnd("www.domain.com", ".com"));
        assertEquals("www.domain.com", StringUtils.removeEnd("www.domain.com", ".com."));
    }

    // remove(String,String) and remove(String,char)
    @Test
    public void testRemove_stringAndChar() throws Throwable {
        assertEquals("qd", StringUtils.remove("queued", "ue"));
        assertEquals("qeed", StringUtils.remove("queued", 'u'));
    }

    // replace(text,search,repl,max): limits replacements; max=0 is a no-op
    @Test
    public void testReplace_withMaxCount() throws Throwable {
        assertEquals("zbzz", StringUtils.replace("abaa", "a", "z", -1));
        assertEquals("abaa", StringUtils.replace("abaa", "a", "z", 0));
        assertEquals("zbaa", StringUtils.replace("abaa", "a", "z", 1));
    }

    // replaceEach: simultaneous non-repeating replacement does not chain outputs into inputs
    @Test
    public void testReplaceEach_doesNotRepeat() throws Throwable {
        String result = StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"d", "t"});
        assertEquals("dcte", result);
    }



    // replaceChars(String,String): positional char mapping; shorter replace deletes extra chars
    @Test
    public void testReplaceChars_positionalMapping() throws Throwable {
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yz"));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", ""));
    }

    // overlay: negative/overflow indices clamped; start/end swapped when start>end
    @Test
    public void testOverlay_negativeAndSwappedIndices() throws Throwable {
        assertEquals("zzzzef", StringUtils.overlay("abcdef", "zzzz", -1, 4));
        assertEquals("abzzzzef", StringUtils.overlay("abcdef", "zzzz", 4, 2));
    }



    // chop: removes last char, or both CR and LF together when present
    @Test
    public void testChop_removesLastCharOrCRLF() throws Throwable {
        assertEquals("abc", StringUtils.chop("abc\r\n"));
        assertEquals("ab", StringUtils.chop("abc"));
        assertEquals("", StringUtils.chop("a"));
    }

    // repeat(str,int) and repeat(char,int): negative repeat -> empty
    @Test
    public void testRepeat_stringAndChar() throws Throwable {
        assertEquals("abab", StringUtils.repeat("ab", 2));
        assertEquals("", StringUtils.repeat("a", -2));
        assertEquals("eee", StringUtils.repeat('e', 3));
    }

    // leftPad/rightPad with custom char: no-op when size <= current length
    @Test
    public void testPad_leftAndRight() throws Throwable {
        assertEquals("zzbat", StringUtils.leftPad("bat", 5, 'z'));
        assertEquals("bat", StringUtils.rightPad("bat", 1, 'z'));
        assertEquals("batzz", StringUtils.rightPad("bat", 5, 'z'));
    }

    // center: odd remaining padding goes to the right side
    @Test
    public void testCenter_oddSizeDistribution() throws Throwable {
        assertEquals(" a  ", StringUtils.center("a", 4, ' '));
        assertEquals("abcd", StringUtils.center("abcd", 2));
    }

    // capitalize/uncapitalize/swapCase: only first char changes for cap/uncap
    @Test
    public void testCapitalizeUncapitalizeSwapCase() throws Throwable {
        assertEquals("Cat", StringUtils.capitalize("cat"));
        assertEquals("cat", StringUtils.uncapitalize("Cat"));
        assertEquals("CAt", StringUtils.capitalize("cAt"));
    }

    // countMatches: counts non-overlapping occurrences; no match -> 0
    @Test
    public void testCountMatches_basic() throws Throwable {
        assertEquals(2, StringUtils.countMatches("abba", "a"));
        assertEquals(1, StringUtils.countMatches("abba", "ab"));
        assertEquals(0, StringUtils.countMatches("abba", "xxx"));
    }

    // isAlpha/isNumeric: empty string returns false per contract (changed in 3.0)
    @Test
    public void testIsAlphaIsNumeric_emptyReturnsFalse() throws Throwable {
        assertFalse(StringUtils.isAlpha(""));
        assertTrue(StringUtils.isAlpha("abc"));
        assertFalse(StringUtils.isNumeric("12.3"));
        assertTrue(StringUtils.isNumeric("123"));
    }

    // defaultString/defaultIfBlank: null and blank handling
    @Test
    public void testDefaultString_and_defaultIfBlank() throws Throwable {
        assertEquals("", StringUtils.defaultString(null));
        assertEquals("NULL", StringUtils.defaultIfBlank("  ", "NULL"));
        assertEquals("bat", StringUtils.defaultIfBlank("bat", "NULL"));
    }

    // reverse and reverseDelimited: character-level vs delimiter-aware reversal
    @Test
    public void testReverse_and_reverseDelimited() throws Throwable {
        assertEquals("tab", StringUtils.reverse("bat"));
        assertEquals("c.b.a", StringUtils.reverseDelimited("a.b.c", '.'));
    }

    // abbreviate: maxWidth below 4 throws IllegalArgumentException
    @Test
    public void testAbbreviate_throwsForWidthBelowFour() throws Throwable {
        try {
            StringUtils.abbreviate("abcdefg", 3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getLevenshteinDistance: basic distance and threshold-bounded variant
    @Test
    public void testGetLevenshteinDistance_basicAndThreshold() throws Throwable {
        assertEquals(1, StringUtils.getLevenshteinDistance("frog", "fog"));
        assertEquals(-1, StringUtils.getLevenshteinDistance("elephant", "hippo", 6));
        assertEquals(7, StringUtils.getLevenshteinDistance("elephant", "hippo", 7));
    }

    // startsWith/endsWith: null-null true, case sensitivity differs from ignoreCase variants
    @Test
    public void testStartsWithEndsWith_caseSensitivity() throws Throwable {
        assertTrue(StringUtils.startsWith(null, null));
        assertFalse(StringUtils.startsWith("ABCDEF", "abc"));
        assertTrue(StringUtils.startsWithIgnoreCase("ABCDEF", "abc"));
        assertFalse(StringUtils.endsWith("ABCDEF", "def"));
        assertTrue(StringUtils.endsWithIgnoreCase("ABCDEF", "def"));
    }
}
