package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

public class StringUtilsClaudeTest {

    // isEmpty: null branch -> true
    @Test
    public void testIsEmpty_nullInput_returnsTrue() throws Throwable {
        assertTrue(StringUtils.isEmpty(null));
    }

    // isEmpty: non-empty string branch -> false
    @Test
    public void testIsEmpty_nonEmptyString_returnsFalse() throws Throwable {
        assertFalse(StringUtils.isEmpty("bob"));
    }

    // isBlank: whitespace-only string -> true (loop runs, all whitespace)
    @Test
    public void testIsBlank_whitespaceOnly_returnsTrue() throws Throwable {
        assertTrue(StringUtils.isBlank("   "));
    }

    // isBlank: non-blank string -> false (loop finds non-whitespace char)
    @Test
    public void testIsBlank_nonBlankString_returnsFalse() throws Throwable {
        assertFalse(StringUtils.isBlank("  bob  "));
    }

    // trim: null input -> null
    @Test
    public void testTrim_nullInput_returnsNull() throws Throwable {
        assertNull(StringUtils.trim(null));
    }

    // trimToNull: blank string trims to empty -> null
    @Test
    public void testTrimToNull_blankString_returnsNull() throws Throwable {
        assertNull(StringUtils.trimToNull("     "));
    }

    // trimToEmpty: null input branch -> EMPTY
    @Test
    public void testTrimToEmpty_nullInput_returnsEmptyString() throws Throwable {
        assertEquals("", StringUtils.trimToEmpty(null));
    }

    // stripStart: stripChars null -> whitespace branch strips leading whitespace only
    @Test
    public void testStripStart_nullStripChars_removesLeadingWhitespace() throws Throwable {
        assertEquals("abc ", StringUtils.stripStart(" abc ", null));
    }

    // stripEnd: custom stripChars branch, mixed chars removed from end
    @Test
    public void testStripEnd_customStripChars_removesTrailingMatchingChars() throws Throwable {
        assertEquals("12", StringUtils.stripEnd("120.00", ".0"));
    }

    // equals: both null -> true branch (cs1 == cs2)
    @Test
    public void testEquals_bothNull_returnsTrue() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
    }

    // equals: one null one non-null -> false branch
    @Test
    public void testEquals_oneNullOneNonNull_returnsFalse() throws Throwable {
        assertFalse(StringUtils.equals("abc", null));
    }

    // equalsIgnoreCase: different case, both non-null -> regionMatches ignoreCase true
    @Test
    public void testEqualsIgnoreCase_differentCase_returnsTrue() throws Throwable {
        assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
    }

    // indexOf(CharSequence,int): null seq -> isEmpty branch returns -1
    @Test
    public void testIndexOf_nullSeq_returnsNegativeOne() throws Throwable {
        assertEquals(-1, StringUtils.indexOf(null, 'a'));
    }

    // indexOf(CharSequence,int,int): negative startPos treated as zero per contract
    @Test
    public void testIndexOf_charNegativeStartPos_treatedAsZero() throws Throwable {
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b', -1));
    }

    // indexOfIgnoreCase: empty search string with startPos far beyond length;
    // per javadoc contract must return str.length(), exposes the defect (code returns -1)
    @Test
    public void testIndexOfIgnoreCase_emptySearchStartPosBeyondLength_returnsStrLength() throws Throwable {
        assertEquals(3, StringUtils.indexOfIgnoreCase("abc", "", 9));
    }

    // indexOfIgnoreCase: normal case-insensitive match within range
    @Test
    public void testIndexOfIgnoreCase_caseInsensitiveMatch_returnsIndex() throws Throwable {
        assertEquals(1, StringUtils.indexOfIgnoreCase("aabaabaa", "AB", 0));
    }

    // lastIndexOfIgnoreCase: startPos beyond length clamps to str.length()-searchStr.length()
    @Test
    public void testLastIndexOfIgnoreCase_startPosBeyondLength_clampsAndMatches() throws Throwable {
        assertEquals(5, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", 9));
    }

    // contains(CharSequence,CharSequence): empty search sequence always matches -> true
    @Test
    public void testContains_emptySearchSeq_returnsTrue() throws Throwable {
        assertTrue(StringUtils.contains("abc", ""));
    }

    // indexOfAny(CharSequence,char...): finds first index among given chars
    @Test
    public void testIndexOfAny_charsFound_returnsFirstIndex() throws Throwable {
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", 'b', 'y'));
    }

    // containsNone(CharSequence,String): invalid char present -> false branch
    @Test
    public void testContainsNone_matchingChar_returnsFalse() throws Throwable {
        assertFalse(StringUtils.containsNone("abz", "xyz"));
    }

    // substring(String,int): negative start counts back from end of string
    @Test
    public void testSubstring_negativeStart_countsFromEnd() throws Throwable {
        assertEquals("bc", StringUtils.substring("abc", -2));
    }

    // substring(String,int,int): start > end branch returns EMPTY
    @Test
    public void testSubstring_startGreaterThanEnd_returnsEmpty() throws Throwable {
        assertEquals("", StringUtils.substring("abc", 2, 0));
    }

    // left: negative len branch returns EMPTY
    @Test
    public void testLeft_negativeLen_returnsEmpty() throws Throwable {
        assertEquals("", StringUtils.left("abc", -1));
    }

    // right: len exceeds string length branch returns whole string
    @Test
    public void testRight_lenExceedsLength_returnsWholeString() throws Throwable {
        assertEquals("abc", StringUtils.right("abc", 4));
    }

    // mid: negative len branch returns EMPTY
    @Test
    public void testMid_negativeLen_returnsEmpty() throws Throwable {
        assertEquals("", StringUtils.mid("abc", 0, -1));
    }

    // substringBefore: null separator branch returns original string
    @Test
    public void testSubstringBefore_nullSeparator_returnsOriginalString() throws Throwable {
        assertEquals("abc", StringUtils.substringBefore("abc", null));
    }

    // substringAfter: separator not found branch returns EMPTY
    @Test
    public void testSubstringAfter_separatorNotFound_returnsEmpty() throws Throwable {
        assertEquals("", StringUtils.substringAfter("abc", "d"));
    }

    // split(String): adjacent whitespace separators treated as one
    @Test
    public void testSplit_defaultWhitespace_splitsAdjacentSeparatorsAsOne() throws Throwable {
        String[] result = StringUtils.split("abc  def");
        assertArrayEquals(new String[]{"abc", "def"}, result);
    }

    // join(Object[],char): null and empty elements represented as empty strings
    @Test
    public void testJoin_objectArrayWithNullAndEmpty_representedAsEmptyStrings() throws Throwable {
        assertEquals(";;a", StringUtils.join(new Object[]{null, "", "a"}, ';'));
    }

    // remove(String,char): all occurrences of the char removed
    @Test
    public void testRemove_char_removesAllOccurrences() throws Throwable {
        assertEquals("qeed", StringUtils.remove("queued", 'u'));
    }

    // replace(String,String,String,int): max == 0 branch returns text unchanged
    @Test
    public void testReplace_maxZero_returnsTextUnchanged() throws Throwable {
        assertEquals("abaa", StringUtils.replace("abaa", "a", "z", 0));
    }

    // replaceEach: mismatched search/replacement array lengths -> IllegalArgumentException
    @Test
    public void testReplaceEach_mismatchedArrayLengths_throwsIllegalArgumentException() throws Throwable {
        try {
            StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"w"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // overlay: negative start index treated as zero
    @Test
    public void testOverlay_negativeStart_treatedAsZero() throws Throwable {
        assertEquals("zzzzef", StringUtils.overlay("abcdef", "zzzz", -1, 4));
    }

    // chomp: trailing \r\n\r\n removes only the last CRLF pair
    @Test
    public void testChomp_multipleCrLf_removesOnlyLastPair() throws Throwable {
        assertEquals("abc\r\n", StringUtils.chomp("abc\r\n\r\n"));
    }

    // repeat(String,int): negative repeat count branch returns EMPTY
    @Test
    public void testRepeat_negativeRepeatCount_returnsEmptyString() throws Throwable {
        assertEquals("", StringUtils.repeat("a", -2));
    }

    // rightPad(String,int): size <= length branch returns original string unchanged
    @Test
    public void testRightPad_sizeLessThanLength_returnsOriginalString() throws Throwable {
        assertEquals("bat", StringUtils.rightPad("bat", 1));
    }

    // capitalize: only first letter changed via toTitleCase
    @Test
    public void testCapitalize_lowercaseFirstLetter_capitalizesOnlyFirst() throws Throwable {
        assertEquals("Cat", StringUtils.capitalize("cat"));
    }

    // countMatches: overlapping occurrences counted non-overlapping (idx advances by sub length)
    @Test
    public void testCountMatches_overlappingPattern_countsNonOverlapping() throws Throwable {
        assertEquals(2, StringUtils.countMatches("abba", "a"));
    }

    // defaultIfBlank: blank input branch returns the supplied default value
    @Test
    public void testDefaultIfBlank_blankInput_returnsDefaultValue() throws Throwable {
        assertEquals("NULL", StringUtils.defaultIfBlank(" ", "NULL"));
    }

    // reverse: normal string reversal via StringBuilder.reverse()
    @Test
    public void testReverse_normalString_reversesCharacters() throws Throwable {
        assertEquals("tab", StringUtils.reverse("bat"));
    }

    // abbreviate: maxWidth < 4 branch throws IllegalArgumentException
    @Test
    public void testAbbreviate_maxWidthTooSmall_throwsIllegalArgumentException() throws Throwable {
        try {
            StringUtils.abbreviate("abcdefg", 3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getLevenshteinDistance: null input branch throws IllegalArgumentException
    @Test
    public void testGetLevenshteinDistance_nullInput_throwsIllegalArgumentException() throws Throwable {
        try {
            StringUtils.getLevenshteinDistance(null, "a");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // normalizeSpace: trims ends and collapses internal whitespace blocks to single space
    @Test
    public void testNormalizeSpace_multipleInnerSpaces_collapsedToSingleSpace() throws Throwable {
        assertEquals("abc def", StringUtils.normalizeSpace(" abc   def "));
    }
}
