package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class StringUtilsClaudeTest {

    // isEmpty/isNotEmpty: null, empty, non-empty branches
    @Test
    public void testIsEmpty_and_isNotEmpty() throws Throwable {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isNotEmpty(null));
        assertTrue(StringUtils.isNotEmpty("bob"));
    }

    // isBlank/isNotBlank: null, empty, whitespace loop, non-blank
    @Test
    public void testIsBlank_and_isNotBlank() throws Throwable {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank("   "));
        assertFalse(StringUtils.isBlank("bob"));
        assertFalse(StringUtils.isNotBlank(null));
        assertTrue(StringUtils.isNotBlank("bob"));
    }

    // trim / trimToNull / trimToEmpty: null and whitespace-only branches
    @Test
    public void testTrim_trimToNull_trimToEmpty() throws Throwable {
        assertNull(StringUtils.trim(null));
        assertEquals("abc", StringUtils.trim("  abc  "));
        assertNull(StringUtils.trimToNull("    "));
        assertEquals("abc", StringUtils.trimToNull(" abc "));
        assertEquals("", StringUtils.trimToEmpty(null));
    }

    // strip / stripToNull / stripToEmpty default whitespace branches
    @Test
    public void testStrip_stripToNull_stripToEmpty() throws Throwable {
        assertNull(StringUtils.strip(null));
        assertEquals("ab c", StringUtils.strip(" ab c "));
        assertNull(StringUtils.stripToNull("   "));
        assertEquals("", StringUtils.stripToEmpty(null));
    }

    // stripStart/stripEnd with custom chars and empty-chars branch; stripAll
    @Test
    public void testStripStart_stripEnd_and_stripAll() throws Throwable {
        assertEquals("abc  ", StringUtils.stripStart("yxabc  ", "xyz"));
        assertEquals("  abc", StringUtils.stripEnd("  abcyx", "xyz"));
        assertEquals("abc", StringUtils.stripStart("abc", ""));
        assertNull(StringUtils.stripAll(null));
        String[] r = StringUtils.stripAll(new String[]{"abc  ", null});
        assertEquals("abc", r[0]);
        assertNull(r[1]);
    }

    // equals / equalsIgnoreCase null-safety and case sensitivity branches
    @Test
    public void testEquals_and_equalsIgnoreCase() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals(null, "abc"));
        assertFalse(StringUtils.equals("abc", "ABC"));
        assertTrue(StringUtils.equalsIgnoreCase(null, null));
        assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
    }

    // indexOf(char) variants: null/empty/startPos negative-treated-as-zero
    @Test
    public void testIndexOfChar_variants() throws Throwable {
        assertEquals(-1, StringUtils.indexOf(null, 'a'));
        assertEquals(-1, StringUtils.indexOf("", 'a'));
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b'));
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b', -1));
        assertEquals(5, StringUtils.indexOf("aabaabaa", 'b', 3));
    }

    // indexOf(String) variants, including empty search beyond length (contract via String.indexOf)
    @Test
    public void testIndexOfString_variants() throws Throwable {
        assertEquals(-1, StringUtils.indexOf((String) null, "a"));
        assertEquals(0, StringUtils.indexOf("aabaabaa", ""));
        assertEquals(1, StringUtils.indexOf("aabaabaa", "ab"));
        assertEquals(5, StringUtils.indexOf("aabaabaa", "b", 3));
        assertEquals(3, StringUtils.indexOf("abc", "", 9));
    }

    // ordinalIndexOf / lastOrdinalIndexOf including empty search and null
    @Test
    public void testOrdinalIndexOf_and_lastOrdinalIndexOf() throws Throwable {
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "a", 2));
        assertEquals(-1, StringUtils.ordinalIndexOf(null, "a", 1));
        assertEquals(0, StringUtils.ordinalIndexOf("aabaabaa", "", 1));
        assertEquals(6, StringUtils.lastOrdinalIndexOf("aabaabaa", "a", 2));
        assertEquals(8, StringUtils.lastOrdinalIndexOf("aabaabaa", "", 1));
    }

    // indexOfIgnoreCase basic matches and startPos normal/negative/out-of-range (non-empty search)
    @Test
    public void testIndexOfIgnoreCase_basicAndStartPos() throws Throwable {
        assertEquals(0, StringUtils.indexOfIgnoreCase("aabaabaa", "A"));
        assertEquals(1, StringUtils.indexOfIgnoreCase("aabaabaa", "AB"));
        assertEquals(2, StringUtils.indexOfIgnoreCase("aabaabaa", "B", -1));
        assertEquals(-1, StringUtils.indexOfIgnoreCase("aabaabaa", "B", 9));
        assertEquals(-1, StringUtils.indexOfIgnoreCase(null, "a"));
    }

    // BUG-HUNTING TEST: per Javadoc, an empty search String always matches even when
    // startPos exceeds the string length (should clamp and return str.length()).
    @Test
    public void testIndexOfIgnoreCase_emptySearchStrStartPosBeyondLength_returnsStrLength() throws Throwable {
        int result = StringUtils.indexOfIgnoreCase("abc", "", 9);
        assertEquals(3, result);
    }

    // lastIndexOf(char/String) and lastIndexOfIgnoreCase branches
    @Test
    public void testLastIndexOf_and_lastIndexOfIgnoreCase() throws Throwable {
        assertEquals(-1, StringUtils.lastIndexOf((String) null, 'a'));
        assertEquals(7, StringUtils.lastIndexOf("aabaabaa", 'a'));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", 'b', 0));
        assertEquals(8, StringUtils.lastIndexOf("aabaabaa", ""));
        assertEquals(7, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "A"));
        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", -1));
    }

    // contains(char/String) and containsIgnoreCase branches
    @Test
    public void testContains_char_string_ignoreCase() throws Throwable {
        assertFalse(StringUtils.contains((String) null, 'a'));
        assertTrue(StringUtils.contains("abc", 'a'));
        assertTrue(StringUtils.contains("abc", ""));
        assertFalse(StringUtils.contains("abc", "z"));
        assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
        assertFalse(StringUtils.containsIgnoreCase(null, "a"));
    }

    // indexOfAny(char[]/String) null and match branches
    @Test
    public void testIndexOfAny_charArray_and_String() throws Throwable {
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", new char[]{'z', 'a'}));
        assertEquals(-1, StringUtils.indexOfAny("aba", new char[]{'z'}));
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", "by"));
        assertEquals(-1, StringUtils.indexOfAny("abc", (String) null));
    }

    // containsAny(char[]/String) null-handling and match branches
    @Test
    public void testContainsAny_charArray_and_String() throws Throwable {
        assertTrue(StringUtils.containsAny("zzabyycdxx", new char[]{'b', 'y'}));
        assertFalse(StringUtils.containsAny("aba", new char[]{'z'}));
        assertTrue(StringUtils.containsAny("zzabyycdxx", "za"));
        assertFalse(StringUtils.containsAny("abc", (String) null));
    }

    // indexOfAnyBut: null search, found and not-found branches
    @Test
    public void testIndexOfAnyBut() throws Throwable {
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("aba", "ab"));
        assertEquals(-1, StringUtils.indexOfAnyBut("abc", (String) null));
    }

    // containsOnly / containsNone null and match branches
    @Test
    public void testContainsOnly_and_containsNone() throws Throwable {
        assertTrue(StringUtils.containsOnly("abab", "abc"));
        assertFalse(StringUtils.containsOnly("ab1", "abc"));
        assertFalse(StringUtils.containsOnly("abc", (String) null));
        assertTrue(StringUtils.containsNone("abab", "xyz"));
        assertFalse(StringUtils.containsNone("abz", "xyz"));
    }

    // indexOfAny(String[]) / lastIndexOfAny(String[]) null and match branches
    @Test
    public void testIndexOfAny_and_lastIndexOfAny_StringArray() throws Throwable {
        assertEquals(2, StringUtils.indexOfAny("zzabyycdxx", new String[]{"ab", "cd"}));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", new String[]{"mn", "op"}));
        assertEquals(-1, StringUtils.indexOfAny((String) null, new String[]{"a"}));
        assertEquals(6, StringUtils.lastIndexOfAny("zzabyycdxx", new String[]{"ab", "cd"}));
    }

    // substring(start) and substring(start,end) negative/overflow/cross branches
    @Test
    public void testSubstring_startOnly_and_startEnd() throws Throwable {
        assertNull(StringUtils.substring(null, 0));
        assertEquals("c", StringUtils.substring("abc", 2));
        assertEquals("bc", StringUtils.substring("abc", -2));
        assertEquals("", StringUtils.substring("abc", 4));
        assertEquals("ab", StringUtils.substring("abc", -4, 2));
        assertEquals("", StringUtils.substring("abc", 2, 0));
    }

    // left/right/mid boundary branches
    @Test
    public void testLeft_right_mid() throws Throwable {
        assertEquals("", StringUtils.left("abc", 0));
        assertEquals("abc", StringUtils.left("abc", 4));
        assertEquals("", StringUtils.left("abc", -1));
        assertEquals("bc", StringUtils.right("abc", 2));
        assertEquals("", StringUtils.mid("abc", 4, 2));
        assertEquals("ab", StringUtils.mid("abc", -2, 2));
    }

    // substringBefore / substringAfter null-separator and not-found branches
    @Test
    public void testSubstringBefore_and_substringAfter() throws Throwable {
        assertEquals("", StringUtils.substringBefore("abc", "a"));
        assertEquals("abc", StringUtils.substringBefore("abc", null));
        assertEquals("", StringUtils.substringAfter("abc", "c"));
        assertEquals("abc", StringUtils.substringAfter("abc", ""));
        assertEquals("", StringUtils.substringAfter("abc", null));
    }

    // substringBeforeLast / substringAfterLast including exact-end match branch
    @Test
    public void testSubstringBeforeLast_and_substringAfterLast() throws Throwable {
        assertEquals("abc", StringUtils.substringBeforeLast("abcba", "b"));
        assertEquals("a", StringUtils.substringAfterLast("abcba", "b"));
        assertEquals("", StringUtils.substringAfterLast("a", "a"));
        assertEquals("a", StringUtils.substringBeforeLast("a", "z") == null ? null : "a");
    }

    // substringBetween / substringsBetween null and found branches
    @Test
    public void testSubstringBetween_and_substringsBetween() throws Throwable {
        assertEquals("abc", StringUtils.substringBetween("tagabctag", "tag"));
        assertNull(StringUtils.substringBetween("", "tag"));
        String[] r = StringUtils.substringsBetween("[a][b][c]", "[", "]");
        assertEquals(3, r.length);
        assertEquals("b", r[1]);
        assertNull(StringUtils.substringsBetween(null, "[", "]"));
    }

    // split default whitespace and char-separator merging-adjacent branches
    @Test
    public void testSplit_default_and_char() throws Throwable {
        assertNull(StringUtils.split((String) null));
        assertEquals(0, StringUtils.split("").length);
        String[] r = StringUtils.split("abc  def");
        assertEquals(2, r.length);
        String[] r2 = StringUtils.split("a..b.c", '.');
        assertEquals(3, r2.length);
    }

    // splitPreserveAllTokens: adjacent separators create empty tokens
    @Test
    public void testSplitPreserveAllTokens() throws Throwable {
        String[] r = StringUtils.splitPreserveAllTokens("a..b.c", '.');
        assertEquals(4, r.length);
        assertEquals("", r[1]);
    }

    // splitByWholeSeparator with multi-char separator
    @Test
    public void testSplitByWholeSeparator() throws Throwable {
        String[] r = StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-");
        assertEquals(3, r.length);
        assertEquals("cd", r[1]);
        assertNull(StringUtils.splitByWholeSeparator(null, ":"));
    }

    // splitByCharacterType and CamelCase variant branches
    @Test
    public void testSplitByCharacterType_and_CamelCase() throws Throwable {
        String[] r1 = StringUtils.splitByCharacterType("foo200Bar");
        assertEquals(4, r1.length);
        assertEquals("B", r1[2]);
        String[] r2 = StringUtils.splitByCharacterTypeCamelCase("foo200Bar");
        assertEquals(3, r2.length);
        assertEquals("Bar", r2[2]);
    }

    // join(Object[]) with null/char/String separator branches
    @Test
    public void testJoin_objectArray_variants() throws Throwable {
        assertNull(StringUtils.join((Object[]) null));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}));
        assertEquals(";;a", StringUtils.join(new Object[]{null, "", "a"}, ';'));
        assertEquals("a--b--c", StringUtils.join(new Object[]{"a", "b", "c"}, "--"));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}, null));
    }

    // join(Iterator) two-or-more-elements and null-iterator branches
    @Test
    public void testJoin_iterator() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        assertEquals("a;b", StringUtils.join((Iterator<?>) list.iterator(), ';'));
        assertNull(StringUtils.join((Iterator<?>) null, ';'));
    }

    // deleteWhitespace branches
    @Test
    public void testDeleteWhitespace() throws Throwable {
        assertNull(StringUtils.deleteWhitespace(null));
        assertEquals("abc", StringUtils.deleteWhitespace("   ab  c  "));
        assertEquals("abc", StringUtils.deleteWhitespace("abc"));
    }

    // removeStart/removeEnd and their ignoreCase variants
    @Test
    public void testRemoveStart_and_removeEnd_variants() throws Throwable {
        assertEquals("domain.com", StringUtils.removeStart("www.domain.com", "www."));
        assertEquals("www.domain", StringUtils.removeEnd("www.domain.com", ".com"));
        assertEquals("domain.com", StringUtils.removeStartIgnoreCase("www.domain.com", "WWW."));
        assertEquals("www.domain", StringUtils.removeEndIgnoreCase("www.domain.com", ".COM"));
    }

    // remove(String,String) and remove(String,char) branches
    @Test
    public void testRemove_string_and_char() throws Throwable {
        assertEquals("qd", StringUtils.remove("queued", "ue"));
        assertEquals("queued", StringUtils.remove("queued", "zz"));
        assertEquals("qeed", StringUtils.remove("queued", 'u'));
        assertEquals("queued", StringUtils.remove("queued", 'z'));
    }

    // replaceOnce / replace (unbounded) / replace with explicit max branches
    @Test
    public void testReplaceOnce_replace_and_replaceWithMax() throws Throwable {
        assertEquals("zba", StringUtils.replaceOnce("aba", "a", "z"));
        assertEquals("zbz", StringUtils.replace("aba", "a", "z"));
        assertEquals("aba", StringUtils.replace("aba", "a", null));
        assertEquals("zbaa", StringUtils.replace("abaa", "a", "z", 1));
        assertEquals("abaa", StringUtils.replace("abaa", "a", "z", 0));
    }

    // replaceEach (single pass) and mismatched array lengths exception branch
    @Test
    public void testReplaceEach_and_mismatchException() throws Throwable {
        assertEquals("wcte", StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"w", "t"}));
        assertEquals("dcte", StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"d", "t"}));
        try {
            StringUtils.replaceEach("aba", new String[]{"a", "b"}, new String[]{"x"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // replaceEachRepeatedly: repeating replacement until stable
    @Test
    public void testReplaceEachRepeatedly() throws Throwable {
        String result = StringUtils.replaceEachRepeatedly("abcde", new String[]{"ab", "d"}, new String[]{"d", "t"});
        assertEquals("tcte", result);
    }

    // replaceChars(char,char) and replaceChars(String,String) with short replacement
    @Test
    public void testReplaceChars() throws Throwable {
        assertEquals("aycya", StringUtils.replaceChars("abcba", 'b', 'y'));
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yz"));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", null));
    }

    // overlay: normal, negative-start and swapped start/end branches
    @Test
    public void testOverlay() throws Throwable {
        assertNull(StringUtils.overlay(null, "x", 0, 0));
        assertEquals("abzzzzef", StringUtils.overlay("abcdef", "zzzz", 2, 4));
        assertEquals("zzzzef", StringUtils.overlay("abcdef", "zzzz", -1, 4));
        assertEquals("abzzzzef", StringUtils.overlay("abcdef", "zzzz", 4, 2));
    }

    // chomp (newline variants) and chomp(separator) branches
    @Test
    public void testChomp_and_chompSeparator() throws Throwable {
        assertEquals("abc", StringUtils.chomp("abc\r\n"));
        assertEquals("abc\r\n", StringUtils.chomp("abc\r\n\r\n"));
        assertEquals("", StringUtils.chomp("\r\n"));
        assertEquals("foo", StringUtils.chomp("foobar", "bar"));
        assertEquals("", StringUtils.chomp("foo", "foo"));
    }

    // chop: normal, CRLF-ending and single/empty-length branches
    @Test
    public void testChop() throws Throwable {
        assertNull(StringUtils.chop(null));
        assertEquals("ab", StringUtils.chop("abc"));
        assertEquals("abc", StringUtils.chop("abc\r\n"));
        assertEquals("", StringUtils.chop("a"));
        assertEquals("", StringUtils.chop(""));
    }

    // repeat(String,int) and repeat(String,String,int) branches
    @Test
    public void testRepeat_variants() throws Throwable {
        assertNull(StringUtils.repeat(null, 2));
        assertEquals("", StringUtils.repeat("a", -2));
        assertEquals("aaa", StringUtils.repeat("a", 3));
        assertEquals("abab", StringUtils.repeat("ab", 2));
        assertEquals("?, ?, ?", StringUtils.repeat("?", ", ", 3));
    }

    // rightPad / leftPad with char and String pad branches
    @Test
    public void testRightPad_and_leftPad() throws Throwable {
        assertNull(StringUtils.rightPad(null, 5));
        assertEquals("bat", StringUtils.rightPad("bat", 1));
        assertEquals("batzz", StringUtils.rightPad("bat", 5, 'z'));
        assertEquals("batyzyzy", StringUtils.rightPad("bat", 8, "yz"));
        assertEquals("  bat", StringUtils.leftPad("bat", 5));
        assertEquals("yzbat", StringUtils.leftPad("bat", 5, "yz"));
    }

    // center with default space, custom char and custom String padding
    @Test
    public void testCenter_variants() throws Throwable {
        assertEquals("    ", StringUtils.center("", 4));
        assertEquals(" ab ", StringUtils.center("ab", 4));
        assertEquals("abcd", StringUtils.center("abcd", 2));
        assertEquals("yayy", StringUtils.center("a", 4, 'y'));
        assertEquals("  abc  ", StringUtils.center("abc", 7, (String) null));
    }

    // upperCase/lowerCase default and Locale-aware branches
    @Test
    public void testUpperLowerCase_and_withLocale() throws Throwable {
        assertNull(StringUtils.upperCase(null));
        assertEquals("ABC", StringUtils.upperCase("aBc"));
        assertEquals("abc", StringUtils.lowerCase("aBc"));
        assertEquals("ABC", StringUtils.upperCase("aBc", Locale.ENGLISH));
    }

    // capitalize / uncapitalize null and empty branches
    @Test
    public void testCapitalize_and_uncapitalize() throws Throwable {
        assertNull(StringUtils.capitalize(null));
        assertEquals("", StringUtils.capitalize(""));
        assertEquals("Cat", StringUtils.capitalize("cat"));
        assertEquals("cat", StringUtils.uncapitalize("Cat"));
        assertEquals("cAT", StringUtils.uncapitalize("CAT"));
    }

    // swapCase and countMatches branches
    @Test
    public void testSwapCase_and_countMatches() throws Throwable {
        assertNull(StringUtils.swapCase(null));
        assertEquals("tHE DOG HAS A bone", StringUtils.swapCase("The dog has a BONE"));
        assertEquals(2, StringUtils.countMatches("abba", "a"));
        assertEquals(0, StringUtils.countMatches("abba", ""));
        assertEquals(0, StringUtils.countMatches(null, "a"));
    }

    // isAlpha/isAlphaSpace/isAlphanumeric/isAlphanumericSpace character-class branches
    @Test
    public void testIsAlpha_family() throws Throwable {
        assertFalse(StringUtils.isAlpha(null));
        assertTrue(StringUtils.isAlpha("abc"));
        assertFalse(StringUtils.isAlpha("ab2c"));
        assertTrue(StringUtils.isAlphaSpace("ab c"));
        assertTrue(StringUtils.isAlphanumeric("ab2c"));
        assertTrue(StringUtils.isAlphanumericSpace("ab 2c"));
    }

    // isAsciiPrintable/isNumeric/isNumericSpace/isWhitespace branches
    @Test
    public void testAsciiPrintable_numeric_whitespace_family() throws Throwable {
        assertTrue(StringUtils.isAsciiPrintable("Ceki"));
        assertFalse(StringUtils.isAsciiPrintable("\u007f"));
        assertTrue(StringUtils.isNumeric("123"));
        assertFalse(StringUtils.isNumeric("12.3"));
        assertTrue(StringUtils.isNumericSpace("12 3"));
        assertTrue(StringUtils.isWhitespace("  "));
    }

    // isAllLowerCase/isAllUpperCase empty-returns-false branches
    @Test
    public void testIsAllLowerCase_and_isAllUpperCase() throws Throwable {
        assertFalse(StringUtils.isAllLowerCase(""));
        assertTrue(StringUtils.isAllLowerCase("abc"));
        assertFalse(StringUtils.isAllLowerCase("abC"));
        assertTrue(StringUtils.isAllUpperCase("ABC"));
        assertFalse(StringUtils.isAllUpperCase(null));
    }

    // defaultString / defaultIfEmpty branches
    @Test
    public void testDefaultString_and_defaultIfEmpty() throws Throwable {
        assertEquals("", StringUtils.defaultString(null));
        assertEquals("bat", StringUtils.defaultString("bat"));
        assertEquals("NULL", StringUtils.defaultString(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfEmpty("", "NULL"));
        assertEquals("bat", StringUtils.defaultIfEmpty("bat", "NULL"));
    }

    // reverse and reverseDelimited branches
    @Test
    public void testReverse_and_reverseDelimited() throws Throwable {
        assertNull(StringUtils.reverse(null));
        assertEquals("tab", StringUtils.reverse("bat"));
        assertEquals("c.b.a", StringUtils.reverseDelimited("a.b.c", '.'));
        assertNull(StringUtils.reverseDelimited(null, '.'));
    }

    // abbreviate: no-op, truncation and too-small-width exception branches
    @Test
    public void testAbbreviate_and_exception() throws Throwable {
        assertNull(StringUtils.abbreviate(null, 4));
        assertEquals("abcdefg", StringUtils.abbreviate("abcdefg", 7));
        assertEquals("abc...", StringUtils.abbreviate("abcdefg", 6));
        try {
            StringUtils.abbreviate("abcdefg", 3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // abbreviateMiddle: no-op and actual-middle-replacement branches
    @Test
    public void testAbbreviateMiddle() throws Throwable {
        assertEquals("abc", StringUtils.abbreviateMiddle("abc", ".", 0));
        assertEquals("ab.f", StringUtils.abbreviateMiddle("abcdef", ".", 4));
        assertNull(StringUtils.abbreviateMiddle(null, ".", 4));
    }

    // difference / indexOfDifference / getCommonPrefix branches
    @Test
    public void testDifference_indexOfDifference_getCommonPrefix() throws Throwable {
        assertEquals("xyz", StringUtils.difference("abcde", "abxyz"));
        assertEquals(2, StringUtils.indexOfDifference("ab", "abxyz"));
        assertEquals(-1, StringUtils.indexOfDifference("abc", "abc"));
        assertEquals("i am a ", StringUtils.getCommonPrefix(new String[]{"i am a machine", "i am a robot"}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"abcde", "xyz"}));
    }

    // getLevenshteinDistance normal case and null-input exception branch
    @Test
    public void testGetLevenshteinDistance_and_exception() throws Throwable {
        assertEquals(1, StringUtils.getLevenshteinDistance("frog", "fog"));
        assertEquals(0, StringUtils.getLevenshteinDistance("", ""));
        try {
            StringUtils.getLevenshteinDistance(null, "a");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // startsWith / startsWithIgnoreCase / startsWithAny branches
    @Test
    public void testStartsWith_family() throws Throwable {
        assertTrue(StringUtils.startsWith(null, null));
        assertFalse(StringUtils.startsWith(null, "abc"));
        assertTrue(StringUtils.startsWith("abcdef", "abc"));
        assertFalse(StringUtils.startsWith("ABCDEF", "abc"));
        assertTrue(StringUtils.startsWithIgnoreCase("ABCDEF", "abc"));
        assertTrue(StringUtils.startsWithAny("abcxyz", new String[]{null, "xyz", "abc"}));
    }

    // endsWith / endsWithIgnoreCase branches
    @Test
    public void testEndsWith_family() throws Throwable {
        assertTrue(StringUtils.endsWith(null, null));
        assertTrue(StringUtils.endsWith("abcdef", "def"));
        assertFalse(StringUtils.endsWith("ABCDEF", "def"));
        assertTrue(StringUtils.endsWithIgnoreCase("ABCDEF", "def"));
        assertFalse(StringUtils.endsWithIgnoreCase("ABCDEF", "cde"));
    }
}
