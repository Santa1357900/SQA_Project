package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class StringUtilsClaudeTest {

    // isEmpty/isNotEmpty: null, empty, non-empty; length(): null and non-null
    @Test
    public void testIsEmptyIsNotEmptyAndLength_variousInputs() throws Throwable {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isNotEmpty(null));
        assertTrue(StringUtils.isNotEmpty("bob"));
        assertEquals(0, StringUtils.length(null));
        assertEquals(3, StringUtils.length("abc"));
    }

    // isBlank/isNotBlank: null, whitespace-only, text
    @Test
    public void testIsBlankIsNotBlank_variousInputs() throws Throwable {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(" "));
        assertFalse(StringUtils.isBlank("bob"));
        assertTrue(StringUtils.isNotBlank("  bob  "));
        assertFalse(StringUtils.isNotBlank(" "));
    }

    // trim/trimToNull/trimToEmpty: null and whitespace handling branches
    @Test
    public void testTrimTrimToNullTrimToEmpty_variousInputs() throws Throwable {
        assertNull(StringUtils.trim(null));
        assertEquals("abc", StringUtils.trim("    abc    "));
        assertNull(StringUtils.trimToNull("     "));
        assertEquals("abc", StringUtils.trimToNull("  abc  "));
        assertEquals("", StringUtils.trimToEmpty(null));
        assertEquals("abc", StringUtils.trimToEmpty("  abc  "));
    }

    // strip(str, stripChars): custom chars branch, default whitespace branch, null branch
    @Test
    public void testStrip_withCustomStripChars() throws Throwable {
        assertEquals("  abc", StringUtils.strip("  abcyx", "xyz"));
        assertEquals("ab c", StringUtils.strip(" ab c "));
        assertNull(StringUtils.strip(null));
    }

    // stripStart/stripEnd: empty stripChars returns unchanged, null stripChars uses whitespace
    @Test
    public void testStripStartStripEnd_edgeCases() throws Throwable {
        assertEquals("abc  ", StringUtils.stripStart("yxabc  ", "xyz"));
        assertEquals("abc", StringUtils.stripStart("abc", ""));
        assertEquals("  abc", StringUtils.stripEnd("  abcyx", "xyz"));
        assertEquals("abc", StringUtils.stripEnd("abc  ", null));
    }

    // equals/equalsIgnoreCase: both null, one null, case sensitivity branches
    @Test
    public void testEqualsEqualsIgnoreCase_nullAndCase() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals("abc", null));
        assertFalse(StringUtils.equals("abc", "ABC"));
        assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
        assertFalse(StringUtils.equalsIgnoreCase(null, "abc"));
    }

    // indexOf(str,char,startPos): negative startPos treated as zero, positive startPos
    @Test
    public void testIndexOfChar_negativeStartPosTreatedAsZero() throws Throwable {
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b', -1));
        assertEquals(5, StringUtils.indexOf("aabaabaa", 'b', 3));
        assertEquals(-1, StringUtils.indexOf("", 'a', 0));
    }

    // indexOf(str,searchStr,startPos): empty search string special case branch
    @Test
    public void testIndexOfString_emptySearchStringSpecialCase() throws Throwable {
        assertEquals(3, StringUtils.indexOf("abc", "", 9));
        assertEquals(2, StringUtils.indexOf("aabaabaa", "", 2));
        assertEquals(-1, StringUtils.indexOf(null, "a"));
    }

    // lastIndexOf(str,char,startPos): found, not found at low startPos; empty search string
    @Test
    public void testLastIndexOfChar_startPosVariants() throws Throwable {
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", 'b'));
        assertEquals(2, StringUtils.lastIndexOf("aabaabaa", 'b', 4));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", 'b', 0));
        assertEquals(8, StringUtils.lastIndexOf("aabaabaa", ""));
    }

    // contains(char)/contains(String): found, not found, empty search, null string
    @Test
    public void testContains_charAndStringVariants() throws Throwable {
        assertTrue(StringUtils.contains("abc", 'a'));
        assertFalse(StringUtils.contains("abc", 'z'));
        assertTrue(StringUtils.contains("abc", ""));
        assertFalse(StringUtils.contains(null, "a"));
    }

    // containsIgnoreCase: case-insensitive match, no match, null
    @Test
    public void testContainsIgnoreCase_caseInsensitiveMatch() throws Throwable {
        assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
        assertFalse(StringUtils.containsIgnoreCase("abc", "Z"));
        assertFalse(StringUtils.containsIgnoreCase(null, "a"));
    }

    // indexOfAny/containsAny(char[]): found, not found branches
    @Test
    public void testIndexOfAnyContainsAny_charsFoundAndNotFound() throws Throwable {
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", new char[]{'b', 'y'}));
        assertEquals(-1, StringUtils.indexOfAny("aba", new char[]{'z'}));
        assertTrue(StringUtils.containsAny("zzabyycdxx", new char[]{'b', 'y'}));
        assertFalse(StringUtils.containsAny("aba", new char[]{'z'}));
    }

    // indexOfAnyBut/containsOnly/containsNone: valid, invalid, null array branches
    @Test
    public void testIndexOfAnyButContainsOnlyContainsNone_mixedValidity() throws Throwable {
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("aba", "ab"));
        assertTrue(StringUtils.containsOnly("abab", "abc"));
        assertFalse(StringUtils.containsOnly("ab1", "abc"));
        assertFalse(StringUtils.containsOnly("ab", (char[]) null));
        assertTrue(StringUtils.containsNone("abab", "xyz"));
        assertFalse(StringUtils.containsNone("abz", "xyz"));
    }

    // ordinalIndexOf: nth occurrence found, ordinal<=0 branch, null input branch
    @Test
    public void testOrdinalIndexOf_nthOccurrenceAndInvalidOrdinal() throws Throwable {
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "a", 2));
        assertEquals(-1, StringUtils.ordinalIndexOf("a", "a", 0));
        assertEquals(-1, StringUtils.ordinalIndexOf(null, "a", 1));
    }

    // substring(str,start): null input, negative start, negative beyond length, start>length
    @Test
    public void testSubstring_negativeAndOutOfRangeStart() throws Throwable {
        assertNull(StringUtils.substring(null, 0));
        assertEquals("bc", StringUtils.substring("abc", -2));
        assertEquals("abc", StringUtils.substring("abc", -4));
        assertEquals("", StringUtils.substring("abc", 4));
    }

    // substring(str,start,end): start>end, negative indices, null input
    @Test
    public void testSubstring_startEndWithNegativeIndices() throws Throwable {
        assertEquals("", StringUtils.substring("abc", 2, 0));
        assertEquals("ab", StringUtils.substring("abc", -4, 2));
        assertEquals("b", StringUtils.substring("abc", -2, -1));
        assertNull(StringUtils.substring(null, 0, 1));
    }

    // left/right/mid: negative length, exact and beyond-length positions
    @Test
    public void testLeftRightMid_boundaryLengths() throws Throwable {
        assertEquals("ab", StringUtils.left("abc", 2));
        assertEquals("", StringUtils.left("abc", -1));
        assertEquals("bc", StringUtils.right("abc", 2));
        assertEquals("", StringUtils.mid("abc", 4, 2));
        assertEquals("ab", StringUtils.mid("abc", -2, 2));
    }

    // substringBefore/substringAfter: separator found, null separator branch
    @Test
    public void testSubstringBeforeAfter_separatorPresentAndAbsent() throws Throwable {
        assertEquals("a", StringUtils.substringBefore("abcba", "b"));
        assertEquals("abc", StringUtils.substringBefore("abc", null));
        assertEquals("cba", StringUtils.substringAfter("abcba", "b"));
        assertEquals("", StringUtils.substringAfter("abc", null));
    }

    // substringBetween/substringsBetween: tag found, not found, multiple matches
    @Test
    public void testSubstringBetweenAndSubstringsBetween_tagMatching() throws Throwable {
        assertEquals("b", StringUtils.substringBetween("wx[b]yz", "[", "]"));
        assertNull(StringUtils.substringBetween(null, "a", "b"));
        assertEquals("abc", StringUtils.substringBetween("tagabctag", "tag"));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.substringsBetween("[a][b][c]", "[", "]"));
        assertNull(StringUtils.substringsBetween(null, "[", "]"));
    }

    // split(str): null, empty, whitespace separated with adjacent spaces collapsed
    @Test
    public void testSplit_defaultWhitespaceAndEmptyAndNull() throws Throwable {
        assertNull(StringUtils.split(null));
        assertEquals(0, StringUtils.split("").length);
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.split("abc  def"));
    }

    // split(str,char): adjacent separators collapse, non-matching separator keeps whole string
    @Test
    public void testSplitChar_adjacentSeparatorsCollapse() throws Throwable {
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.split("a..b.c", '.'));
        assertArrayEquals(new String[]{"a:b:c"}, StringUtils.split("a:b:c", '.'));
    }

    // splitPreserveAllTokens: adjacent separators create empty tokens, null input
    @Test
    public void testSplitPreserveAllTokens_keepsEmptyTokens() throws Throwable {
        assertArrayEquals(new String[]{"abc", "", "def"}, StringUtils.splitPreserveAllTokens("abc  def"));
        assertNull(StringUtils.splitPreserveAllTokens(null));
    }

    // join(Object[],char): normal join, nulls become empty, null array returns null
    @Test
    public void testJoinObjectArray_withCharSeparatorAndNulls() throws Throwable {
        assertEquals("a;b;c", StringUtils.join(new Object[]{"a", "b", "c"}, ';'));
        assertEquals(";;a", StringUtils.join(new Object[]{null, "", "a"}, ';'));
        assertNull(StringUtils.join((Object[]) null, ';'));
    }

    // join(Iterator,String): multiple elements with separator
    @Test
    public void testJoinIterator_withStringSeparator() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("x");
        list.add("y");
        assertEquals("x--y", StringUtils.join(list.iterator(), "--"));
    }

    // deleteWhitespace: mixed whitespace removed, null returns null
    @Test
    public void testDeleteWhitespace_removesAllWhitespaceOrNull() throws Throwable {
        assertEquals("abc", StringUtils.deleteWhitespace("   ab  c  "));
        assertNull(StringUtils.deleteWhitespace(null));
    }

    // removeStart/removeEnd: prefix/suffix found, empty remove string branch
    @Test
    public void testRemoveStartRemoveEnd_prefixSuffixHandling() throws Throwable {
        assertEquals("domain.com", StringUtils.removeStart("www.domain.com", "www."));
        assertEquals("abc", StringUtils.removeStart("abc", ""));
        assertEquals("www.domain", StringUtils.removeEnd("www.domain.com", ".com"));
    }

    // remove(String,String)/remove(String,char): found and not found branches
    @Test
    public void testRemove_stringAndCharVariants() throws Throwable {
        assertEquals("qd", StringUtils.remove("queued", "ue"));
        assertEquals("qeed", StringUtils.remove("queued", 'u'));
        assertEquals("queued", StringUtils.remove("queued", "zz"));
    }

    // replace with max: limited replacements, max=0 no-op, replaceOnce
    @Test
    public void testReplace_withMaxCountLimitsReplacements() throws Throwable {
        assertEquals("zbza", StringUtils.replace("abaa", "a", "z", 2));
        assertEquals("zba", StringUtils.replaceOnce("aba", "a", "z"));
        assertEquals("abaa", StringUtils.replace("abaa", "a", "z", 0));
    }

    // replaceChars(char,char) and (String,String): substitution and deletion branches
    @Test
    public void testReplaceChars_charAndStringVariants() throws Throwable {
        assertEquals("aycya", StringUtils.replaceChars("abcba", 'b', 'y'));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", (String) null));
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yz"));
    }

    // overlay: mid-range overlay, null input, reversed negative indices
    @Test
    public void testOverlay_insertsIntoRangeOrNull() throws Throwable {
        assertEquals("abzzzzef", StringUtils.overlay("abcdef", "zzzz", 2, 4));
        assertNull(StringUtils.overlay(null, "x", 0, 0));
        assertEquals("zzzzabcdef", StringUtils.overlay("abcdef", "zzzz", -2, -3));
    }

    // chomp/chop: CRLF handling, single character, separator-based chomp
    @Test
    public void testChompChop_trailingCharacterRemoval() throws Throwable {
        assertEquals("abc", StringUtils.chomp("abc\r\n"));
        assertEquals("foo", StringUtils.chomp("foobar", "bar"));
        assertEquals("ab", StringUtils.chop("abc"));
        assertEquals("", StringUtils.chop("a"));
    }

    // repeat: normal repeat, negative treated as zero, repeat with separator
    @Test
    public void testRepeat_countAndSeparatorVariants() throws Throwable {
        assertEquals("abab", StringUtils.repeat("ab", 2));
        assertEquals("", StringUtils.repeat("a", -2));
        assertEquals("?, ?, ?", StringUtils.repeat("?", ", ", 3));
    }

    // leftPad/rightPad: default space pad, char pad, multi-char pad string
    @Test
    public void testLeftPadRightPad_defaultAndCustomPad() throws Throwable {
        assertEquals("  bat", StringUtils.leftPad("bat", 5));
        assertEquals("batzz", StringUtils.rightPad("bat", 5, 'z'));
        assertEquals("batyzyzy", StringUtils.rightPad("bat", 8, "yz"));
    }

    // center: pads evenly with custom char, size smaller than length returns unchanged
    @Test
    public void testCenter_padsEvenlyWithCustomChar() throws Throwable {
        assertEquals("yayy", StringUtils.center("a", 4, 'y'));
        assertEquals("abcd", StringUtils.center("abcd", 2));
    }

    // upperCase/lowerCase: default locale conversion, null input
    @Test
    public void testUpperCaseLowerCase_defaultLocale() throws Throwable {
        assertEquals("ABC", StringUtils.upperCase("aBc"));
        assertEquals("abc", StringUtils.lowerCase("aBc"));
        assertNull(StringUtils.upperCase(null));
    }

    // capitalize/uncapitalize/swapCase: first-char-only and full case inversion
    @Test
    public void testCapitalizeUncapitalizeSwapCase_caseTransforms() throws Throwable {
        assertEquals("Cat", StringUtils.capitalize("cat"));
        assertEquals("cAT", StringUtils.uncapitalize("CAT"));
        assertEquals("tHE DOG HAS A bone", StringUtils.swapCase("The dog has a BONE"));
    }

    // countMatches: overlapping-free counting, empty substring returns zero
    @Test
    public void testCountMatches_overlappingAndEmptySub() throws Throwable {
        assertEquals(2, StringUtils.countMatches("abba", "a"));
        assertEquals(0, StringUtils.countMatches("abba", ""));
        assertEquals(1, StringUtils.countMatches("abba", "ab"));
    }

    // isAlpha/isNumeric/isWhitespace: character class checks including null and mixed chars
    @Test
    public void testIsAlphaIsNumericIsWhitespace_characterClassChecks() throws Throwable {
        assertTrue(StringUtils.isAlpha("abc"));
        assertFalse(StringUtils.isAlpha("ab2c"));
        assertFalse(StringUtils.isAlpha(null));
        assertTrue(StringUtils.isNumeric("123"));
        assertFalse(StringUtils.isNumeric("12.3"));
        assertTrue(StringUtils.isWhitespace("  "));
    }

    // isAllLowerCase/isAllUpperCase: empty string returns false branch
    @Test
    public void testIsAllLowerCaseIsAllUpperCase_emptyReturnsFalse() throws Throwable {
        assertTrue(StringUtils.isAllLowerCase("abc"));
        assertFalse(StringUtils.isAllLowerCase(""));
        assertTrue(StringUtils.isAllUpperCase("ABC"));
        assertFalse(StringUtils.isAllUpperCase("aBC"));
    }

    // defaultString/defaultIfEmpty: null fallback and empty fallback branches
    @Test
    public void testDefaultStringDefaultIfEmpty_fallbackValues() throws Throwable {
        assertEquals("", StringUtils.defaultString(null));
        assertEquals("bat", StringUtils.defaultString("bat", "NULL"));
        assertEquals("NULL", StringUtils.defaultString(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfEmpty("", "NULL"));
    }

    // reverse/reverseDelimited: full reverse, delimiter-preserving token reverse
    @Test
    public void testReverseReverseDelimited_reversesStringsAndTokens() throws Throwable {
        assertEquals("tab", StringUtils.reverse("bat"));
        assertEquals("c.b.a", StringUtils.reverseDelimited("a.b.c", '.'));
    }

    // abbreviate: within width unchanged, truncated with ellipsis, too-small width throws
    @Test
    public void testAbbreviate_truncatesAndThrowsOnSmallWidth() throws Throwable {
        assertEquals("abc...", StringUtils.abbreviate("abcdefg", 6));
        assertEquals("abcdefg", StringUtils.abbreviate("abcdefg", 7));
        try {
            StringUtils.abbreviate("abcdefg", 3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // difference/indexOfDifference/getCommonPrefix: divergence point and shared prefix
    @Test
    public void testDifferenceIndexOfDifferenceGetCommonPrefix_findsDivergence() throws Throwable {
        assertEquals("robot", StringUtils.difference("i am a machine", "i am a robot"));
        assertEquals(-1, StringUtils.indexOfDifference("abc", "abc"));
        assertEquals("ab", StringUtils.getCommonPrefix(new String[]{"abcde", "abxyz"}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"abcde", "xyz"}));
    }

    // getLevenshteinDistance: known distance values and IllegalArgumentException on null
    @Test
    public void testGetLevenshteinDistance_computesDistanceAndThrowsOnNull() throws Throwable {
        assertEquals(1, StringUtils.getLevenshteinDistance("frog", "fog"));
        assertEquals(0, StringUtils.getLevenshteinDistance("", ""));
        try {
            StringUtils.getLevenshteinDistance(null, "x");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // startsWith/endsWith (plain and ignore-case): null equality, case sensitivity branches
    @Test
    public void testStartsWithEndsWith_plainAndIgnoreCaseVariants() throws Throwable {
        assertTrue(StringUtils.startsWith("abcdef", "abc"));
        assertTrue(StringUtils.startsWith(null, null));
        assertFalse(StringUtils.startsWith("ABCDEF", "abc"));
        assertTrue(StringUtils.startsWithIgnoreCase("ABCDEF", "abc"));
        assertTrue(StringUtils.endsWith("abcdef", "def"));
        assertFalse(StringUtils.endsWith(null, "def"));
        assertTrue(StringUtils.endsWithIgnoreCase("ABCDEF", "def"));
    }
}
