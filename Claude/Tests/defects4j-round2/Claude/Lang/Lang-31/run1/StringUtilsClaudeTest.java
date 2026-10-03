package org.apache.commons.lang3;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

public class StringUtilsClaudeTest {

    @Before
    public void setUp() throws Throwable {
        // no shared state needed
    }

    // Constructor coverage
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        StringUtils su = new StringUtils();
        assertNotNull(su);
    }

    // isEmpty / isNotEmpty branches: null, empty, non-empty
    @Test
    public void testIsEmptyIsNotEmpty_variousInputs() throws Throwable {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isNotEmpty(null));
        assertTrue(StringUtils.isNotEmpty("bob"));
    }

    // isBlank / isNotBlank branches: null, empty, whitespace loop, non-blank
    @Test
    public void testIsBlankIsNotBlank_variousInputs() throws Throwable {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank("   "));
        assertFalse(StringUtils.isBlank("bob"));
        assertTrue(StringUtils.isNotBlank("  bob  "));
    }

    // trim/trimToNull/trimToEmpty branches
    @Test
    public void testTrimFamily() throws Throwable {
        assertNull(StringUtils.trim(null));
        assertEquals("abc", StringUtils.trim("  abc  "));
        assertNull(StringUtils.trimToNull("     "));
        assertEquals("abc", StringUtils.trimToNull("  abc  "));
        assertEquals("", StringUtils.trimToEmpty(null));
    }

    // strip/stripToNull/stripToEmpty/strip(str,chars) branches
    @Test
    public void testStripFamily() throws Throwable {
        assertNull(StringUtils.strip(null));
        assertEquals("abc", StringUtils.strip(" ab c ".trim().equals("ab c") ? " abc " : " abc "));
        assertNull(StringUtils.stripToNull("   "));
        assertEquals("", StringUtils.stripToEmpty(null));
        assertEquals("  abc", StringUtils.strip("  abcyx", "xyz"));
    }

    // stripStart/stripEnd branches: null stripChars (whitespace), empty stripChars, custom chars
    @Test
    public void testStripStartStripEnd() throws Throwable {
        assertEquals("abc  ", StringUtils.stripStart("  abc  ", null));
        assertEquals("abc", StringUtils.stripStart("abc", ""));
        assertEquals("abc  ", StringUtils.stripStart("yxabc  ", "xyz"));
        assertEquals("  abc", StringUtils.stripEnd("  abc  ", null));
        assertEquals("  abc", StringUtils.stripEnd("  abcyx", "xyz"));
    }

    // stripAll: null array, null entry ignored, custom chars
    @Test
    public void testStripAll() throws Throwable {
        assertNull(StringUtils.stripAll((String[]) null));
        String[] result = StringUtils.stripAll(new String[] {"abc  ", null});
        assertEquals("abc", result[0]);
        assertNull(result[1]);
    }

    // equals / equalsIgnoreCase branches
    @Test
    public void testEqualsFamily() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals(null, "abc"));
        assertFalse(StringUtils.equals("abc", "ABC"));
        assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
        assertTrue(StringUtils.equalsIgnoreCase(null, null));
    }

    // indexOf char/String overloads, null/empty handling
    @Test
    public void testIndexOfCharAndString() throws Throwable {
        assertEquals(-1, StringUtils.indexOf(null, 'a'));
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b'));
        assertEquals(5, StringUtils.indexOf("aabaabaa", 'b', 3));
        assertEquals(-1, StringUtils.indexOf("abc", (String) null));
        assertEquals(1, StringUtils.indexOf("aabaabaa", "ab"));
    }

    // ordinalIndexOf / lastOrdinalIndexOf
    @Test
    public void testOrdinalIndexOfFamily() throws Throwable {
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "a", 2));
        assertEquals(-1, StringUtils.ordinalIndexOf(null, "a", 1));
        assertEquals(7, StringUtils.lastOrdinalIndexOf("aabaabaa", "a", 1));
        assertEquals(6, StringUtils.lastOrdinalIndexOf("aabaabaa", "a", 2));
    }

    // indexOfIgnoreCase / lastIndexOfIgnoreCase
    @Test
    public void testIgnoreCaseIndexFamily() throws Throwable {
        assertEquals(0, StringUtils.indexOfIgnoreCase("aabaabaa", "A"));
        assertEquals(2, StringUtils.indexOfIgnoreCase("aabaabaa", "B", 0));
        assertEquals(7, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "A"));
        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", -1));
    }

    // lastIndexOf char/String overloads
    @Test
    public void testLastIndexOfFamily() throws Throwable {
        assertEquals(-1, StringUtils.lastIndexOf("", 'a'));
        assertEquals(7, StringUtils.lastIndexOf("aabaabaa", 'a'));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", 'b', 0));
        assertEquals(1, StringUtils.lastIndexOf("aabaabaa", "ab"));
    }

    // contains / containsIgnoreCase
    @Test
    public void testContainsFamily() throws Throwable {
        assertFalse(StringUtils.contains(null, 'a'));
        assertTrue(StringUtils.contains("abc", 'a'));
        assertTrue(StringUtils.contains("abc", ""));
        assertFalse(StringUtils.contains(null, "a"));
        assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
    }

    // indexOfAny char[] and String overloads
    @Test
    public void testIndexOfAnyCharArrayAndString() throws Throwable {
        assertEquals(-1, StringUtils.indexOfAny((CharSequence) null, new char[] {'a'}));
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", new char[] {'z', 'a'}));
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", "by"));
        assertEquals(-1, StringUtils.indexOfAny("aba", "z"));
    }

    // containsAny basic BMP character behavior
    @Test
    public void testContainsAny_basicCharacters() throws Throwable {
        assertFalse(StringUtils.containsAny(null, new char[] {'a'}));
        assertFalse(StringUtils.containsAny("zzabyycdxx", (char[]) null));
        assertTrue(StringUtils.containsAny("zzabyycdxx", new char[] {'z', 'a'}));
        assertFalse(StringUtils.containsAny("aba", new char[] {'z'}));
    }

    // BUG TEST: containsAny must treat a high surrogate followed by a non-matching
    // low surrogate as part of one supplementary character, not match it against an
    // unrelated search entry that merely shares the same high surrogate.
    @Test
    public void testContainsAny_surrogatePair_mustNotMatchDifferentSupplementaryChar() throws Throwable {
        // cs represents supplementary codepoint U+20000 (high=\uD840, low=\uDC00)
        String cs = "\uD840\uDC00";
        // searchChars represents a DIFFERENT supplementary codepoint U+20001
        // (same high surrogate \uD840 but different low surrogate \uDC01)
        char[] searchChars = new char[] {'\uD840', '\uDC01'};
        assertFalse(StringUtils.containsAny(cs, searchChars));
    }

    // containsAny(CharSequence, String) delegation
    @Test
    public void testContainsAny_stringOverload() throws Throwable {
        assertTrue(StringUtils.containsAny("zzabyycdxx", "za"));
        assertFalse(StringUtils.containsAny("aba", "z"));
        assertFalse(StringUtils.containsAny("abc", (String) null));
    }

    // indexOfAnyBut char[] and String overloads
    @Test
    public void testIndexOfAnyBut() throws Throwable {
        assertEquals(-1, StringUtils.indexOfAnyBut((CharSequence) null, new char[] {'a'}));
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", new char[] {'z', 'a'}));
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("aba", "ab"));
    }

    // containsOnly and containsNone branches
    @Test
    public void testContainsOnlyContainsNone() throws Throwable {
        assertFalse(StringUtils.containsOnly(null, "abc"));
        assertTrue(StringUtils.containsOnly("", "abc"));
        assertTrue(StringUtils.containsOnly("abab", "abc"));
        assertFalse(StringUtils.containsOnly("ab1", "abc"));
        assertTrue(StringUtils.containsNone("abab", "xyz"));
        assertFalse(StringUtils.containsNone("abz", "xyz"));
    }

    // indexOfAny(String,String[]) and lastIndexOfAny branches
    @Test
    public void testIndexOfAnyStringArrayAndLastIndexOfAny() throws Throwable {
        assertEquals(2, StringUtils.indexOfAny("zzabyycdxx", new String[] {"ab", "cd"}));
        assertEquals(1, StringUtils.indexOfAny("zzabyycdxx", new String[] {"zab", "aby"}));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", new String[] {"mn", "op"}));
        assertEquals(6, StringUtils.lastIndexOfAny("zzabyycdxx", new String[] {"ab", "cd"}));
    }

    // substring(str,start) negative/overflow branches
    @Test
    public void testSubstringSingleArg() throws Throwable {
        assertNull(StringUtils.substring(null, 0));
        assertEquals("c", StringUtils.substring("abc", 2));
        assertEquals("", StringUtils.substring("abc", 4));
        assertEquals("bc", StringUtils.substring("abc", -2));
        assertEquals("abc", StringUtils.substring("abc", -4));
    }

    // substring(str,start,end) branches
    @Test
    public void testSubstringTwoArgs() throws Throwable {
        assertEquals("ab", StringUtils.substring("abc", 0, 2));
        assertEquals("", StringUtils.substring("abc", 2, 0));
        assertEquals("b", StringUtils.substring("abc", -2, -1));
        assertEquals("ab", StringUtils.substring("abc", -4, 2));
    }

    // left/right/mid branches including negative len
    @Test
    public void testLeftRightMid() throws Throwable {
        assertEquals("", StringUtils.left("abc", -1));
        assertEquals("ab", StringUtils.left("abc", 2));
        assertEquals("abc", StringUtils.right("abc", 4));
        assertEquals("", StringUtils.right("abc", 0));
        assertEquals("ab", StringUtils.mid("abc", -2, 2));
        assertEquals("", StringUtils.mid("abc", 4, 2));
    }

    // substringBefore/substringAfter branches
    @Test
    public void testSubstringBeforeAfter() throws Throwable {
        assertEquals("ab", StringUtils.substringBefore("abc", "c"));
        assertEquals("abc", StringUtils.substringBefore("abc", "d"));
        assertEquals("", StringUtils.substringBefore("abc", ""));
        assertEquals("", StringUtils.substringAfter(null == null ? "abc" : "", "d"));
        assertEquals("", StringUtils.substringAfter("abc", (String) null));
    }

    // substringBeforeLast/substringAfterLast branches
    @Test
    public void testSubstringBeforeAfterLast() throws Throwable {
        assertEquals("abc", StringUtils.substringBeforeLast("abcba", "b") .equals("abc") ? "abc" : StringUtils.substringBeforeLast("abcba", "b"));
        assertEquals("", StringUtils.substringBeforeLast("a", "a"));
        assertEquals("a", StringUtils.substringAfterLast("abcba", "b"));
        assertEquals("", StringUtils.substringAfterLast("abc", "c"));
    }

    // substringBetween branches
    @Test
    public void testSubstringBetweenFamily() throws Throwable {
        assertNull(StringUtils.substringBetween(null, "tag"));
        assertEquals("abc", StringUtils.substringBetween("tagabctag", "tag"));
        assertEquals("b", StringUtils.substringBetween("wx[b]yz", "[", "]"));
        assertNull(StringUtils.substringBetween("", "[", "]"));
    }

    // substringsBetween branches
    @Test
    public void testSubstringsBetween() throws Throwable {
        assertNull(StringUtils.substringsBetween(null, "[", "]"));
        String[] result = StringUtils.substringsBetween("[a][b][c]", "[", "]");
        assertEquals(3, result.length);
        assertEquals("a", result[0]);
        assertEquals("c", result[2]);
        assertNull(StringUtils.substringsBetween("abc", "[", "]"));
    }

    // split branches: null, empty, whitespace, char separator, adjacent separators
    @Test
    public void testSplitFamily() throws Throwable {
        assertNull(StringUtils.split(null));
        assertEquals(0, StringUtils.split("").length);
        assertArrayEquals(new String[] {"abc", "def"}, StringUtils.split("abc  def"));
        assertArrayEquals(new String[] {"a", "b", "c"}, StringUtils.split("a..b.c", '.'));
        assertArrayEquals(new String[] {"ab", "cd:ef"}, StringUtils.split("ab:cd:ef", ":", 2));
    }

    // splitByWholeSeparator branches
    @Test
    public void testSplitByWholeSeparatorFamily() throws Throwable {
        assertNull(StringUtils.splitByWholeSeparator(null, "-!-"));
        assertArrayEquals(new String[] {"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-"));
        assertArrayEquals(new String[] {"ab", "cd-!-ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-", 2));
    }

    // splitPreserveAllTokens branches
    @Test
    public void testSplitPreserveAllTokensFamily() throws Throwable {
        assertArrayEquals(new String[] {"", "abc", ""}, StringUtils.splitPreserveAllTokens(" abc "));
        assertArrayEquals(new String[] {"a", "", "b", "c"}, StringUtils.splitPreserveAllTokens("a..b.c", '.'));
        assertArrayEquals(new String[] {"a", "b", "c", ""}, StringUtils.splitPreserveAllTokens("a b c ", ' '));
    }

    // splitByCharacterType / CamelCase branches
    @Test
    public void testSplitByCharacterTypeFamily() throws Throwable {
        assertArrayEquals(new String[] {"number", "5"}, StringUtils.splitByCharacterType("number5"));
        assertArrayEquals(new String[] {"foo", "B", "ar"}, StringUtils.splitByCharacterType("fooBar"));
        assertArrayEquals(new String[] {"foo", "Bar"}, StringUtils.splitByCharacterTypeCamelCase("fooBar"));
        assertArrayEquals(new String[] {"ASF", "Rules"}, StringUtils.splitByCharacterTypeCamelCase("ASFRules"));
    }

    // join Object[] overloads
    @Test
    public void testJoinArrayFamily() throws Throwable {
        assertNull(StringUtils.join((Object[]) null));
        assertEquals("", StringUtils.join(new Object[0]));
        assertEquals("abc", StringUtils.join(new Object[] {"a", "b", "c"}));
        assertEquals("a;b;c", StringUtils.join(new Object[] {"a", "b", "c"}, ';'));
        assertEquals(";;a", StringUtils.join(new Object[] {null, "", "a"}, ';'));
        assertEquals("a--b--c", StringUtils.join(new Object[] {"a", "b", "c"}, "--"));
    }

    // join Object[] with start/end index
    @Test
    public void testJoinArrayWithIndices() throws Throwable {
        assertEquals("b;c", StringUtils.join(new Object[] {"a", "b", "c"}, ';', 1, 3));
        assertEquals("", StringUtils.join(new Object[] {"a", "b", "c"}, ';', 2, 1));
    }

    // join Iterator/Iterable overloads
    @Test
    public void testJoinIteratorIterableFamily() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        list.add("c");
        Iterator<String> it = list.iterator();
        assertEquals("a;b;c", StringUtils.join(it, ';'));
        assertEquals("a--b--c", StringUtils.join((Iterable<?>) list, "--"));
        List<String> empty = new ArrayList<String>();
        assertEquals("", StringUtils.join(empty.iterator(), ';'));
    }

    // deleteWhitespace branches
    @Test
    public void testDeleteWhitespace() throws Throwable {
        assertNull(StringUtils.deleteWhitespace(null));
        assertEquals("abc", StringUtils.deleteWhitespace("   ab  c  "));
        assertEquals("abc", StringUtils.deleteWhitespace("abc"));
    }

    // removeStart/removeEnd (+ ignore case) branches
    @Test
    public void testRemoveStartEndFamily() throws Throwable {
        assertEquals("domain.com", StringUtils.removeStart("www.domain.com", "www."));
        assertEquals("www.domain.com", StringUtils.removeStart("www.domain.com", "domain"));
        assertEquals("domain.com", StringUtils.removeStartIgnoreCase("www.domain.com", "WWW."));
        assertEquals("www.domain", StringUtils.removeEnd("www.domain.com", ".com"));
        assertEquals("www.domain", StringUtils.removeEndIgnoreCase("www.domain.com", ".COM"));
    }

    // remove(str,remove) and remove(str,char) branches
    @Test
    public void testRemoveFamily() throws Throwable {
        assertEquals("qd", StringUtils.remove("queued", "ue"));
        assertEquals("queued", StringUtils.remove("queued", "zz"));
        assertEquals("qeed", StringUtils.remove("queued", 'u'));
        assertEquals("queued", StringUtils.remove("queued", 'z'));
    }

    // replace/replaceOnce branches including max limit
    @Test
    public void testReplaceFamily() throws Throwable {
        assertEquals("zba", StringUtils.replaceOnce("aba", "a", "z"));
        assertEquals("b", StringUtils.replace("aba", "a", ""));
        assertEquals("zbza", StringUtils.replace("abaa", "a", "z", 2));
        assertEquals("any", StringUtils.replace("any", "", "x"));
        assertEquals("any", StringUtils.replace("any", "a", null));
    }

    // replaceEach / replaceEachRepeatedly branches
    @Test
    public void testReplaceEachFamily() throws Throwable {
        String result = StringUtils.replaceEach("abcde", new String[] {"ab", "d"}, new String[] {"w", "t"});
        assertEquals("wcte", result);
        String noRepeat = StringUtils.replaceEach("abcde", new String[] {"ab", "d"}, new String[] {"d", "t"});
        assertEquals("dcte", noRepeat);
        String repeated = StringUtils.replaceEachRepeatedly("abcde", new String[] {"ab", "d"}, new String[] {"d", "t"});
        assertEquals("tcte", repeated);
    }

    // replaceChars char and String overloads
    @Test
    public void testReplaceCharsFamily() throws Throwable {
        assertEquals("aycya", StringUtils.replaceChars("abcba", 'b', 'y'));
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yz"));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", ""));
        assertEquals("abc", StringUtils.replaceChars("abc", (String) null, "x"));
    }

    // overlay / chomp / chop branches
    @Test
    public void testOverlayChompChop() throws Throwable {
        assertEquals("abzzzzef", StringUtils.overlay("abcdef", "zzzz", 2, 4));
        assertEquals("abc", StringUtils.chomp("abc\r\n"));
        assertEquals("foo", StringUtils.chomp("foobar", "bar"));
        assertEquals("abc", StringUtils.chop("abc\r\n"));
        assertEquals("", StringUtils.chop("a"));
    }

    // repeat branches (int and String separator overloads)
    @Test
    public void testRepeatFamily() throws Throwable {
        assertNull(StringUtils.repeat(null, 2));
        assertEquals("", StringUtils.repeat("a", -2));
        assertEquals("aaa", StringUtils.repeat("a", 3));
        assertEquals("abab", StringUtils.repeat("ab", 2));
        assertEquals("?, ?, ?", StringUtils.repeat("?", ", ", 3));
    }

    // rightPad/leftPad branches (char and String pad)
    @Test
    public void testPaddingFamily() throws Throwable {
        assertEquals("bat  ", StringUtils.rightPad("bat", 5));
        assertEquals("batzz", StringUtils.rightPad("bat", 5, 'z'));
        assertEquals("batyzyzy", StringUtils.rightPad("bat", 8, "yz"));
        assertEquals("  bat", StringUtils.leftPad("bat", 5));
        assertEquals("yzyzybat", StringUtils.leftPad("bat", 8, "yz"));
    }

    // center branches (char and String pad)
    @Test
    public void testCenterFamily() throws Throwable {
        assertEquals("ab", StringUtils.center("ab", -1));
        assertEquals(" ab ", StringUtils.center("ab", 4));
        assertEquals("yayy", StringUtils.center("a", 4, 'y'));
        assertEquals("yayz", StringUtils.center("a", 4, "yz"));
    }

    // upperCase/lowerCase with and without locale
    @Test
    public void testCaseConversionFamily() throws Throwable {
        assertNull(StringUtils.upperCase(null));
        assertEquals("ABC", StringUtils.upperCase("aBc"));
        assertEquals("abc", StringUtils.lowerCase("aBc", Locale.ENGLISH));
        assertEquals("ABC", StringUtils.upperCase("aBc", Locale.ENGLISH));
    }

    // capitalize / uncapitalize branches
    @Test
    public void testCapitalizeUncapitalize() throws Throwable {
        assertNull(StringUtils.capitalize(null));
        assertEquals("", StringUtils.capitalize(""));
        assertEquals("Cat", StringUtils.capitalize("cat"));
        assertEquals("cat", StringUtils.uncapitalize("Cat"));
        assertEquals("cAT", StringUtils.uncapitalize("CAT"));
    }

    // swapCase branch
    @Test
    public void testSwapCase() throws Throwable {
        assertNull(StringUtils.swapCase(null));
        assertEquals("tHE DOG HAS A bone", StringUtils.swapCase("The dog has a BONE"));
    }

    // countMatches branches
    @Test
    public void testCountMatches() throws Throwable {
        assertEquals(0, StringUtils.countMatches(null, "a"));
        assertEquals(2, StringUtils.countMatches("abba", "a"));
        assertEquals(1, StringUtils.countMatches("abba", "ab"));
        assertEquals(0, StringUtils.countMatches("abba", "xxx"));
    }

    // character-class checks (isAlpha, isNumeric, isWhitespace, isAllLowerCase/UpperCase)
    @Test
    public void testCharacterTypeChecks() throws Throwable {
        assertFalse(StringUtils.isAlpha(null));
        assertFalse(StringUtils.isAlpha("ab2c"));
        assertTrue(StringUtils.isAlphaSpace("ab c"));
        assertTrue(StringUtils.isNumeric("123"));
        assertFalse(StringUtils.isNumeric("12.3"));
        assertTrue(StringUtils.isNumericSpace("12 3"));
        assertFalse(StringUtils.isAllLowerCase(""));
        assertTrue(StringUtils.isAllUpperCase("ABC"));
    }

    // isAsciiPrintable branch
    @Test
    public void testIsAsciiPrintable() throws Throwable {
        assertFalse(StringUtils.isAsciiPrintable(null));
        assertTrue(StringUtils.isAsciiPrintable("!ab-c~"));
        assertFalse(StringUtils.isAsciiPrintable("\u007f"));
    }

    // defaultString / defaultIfEmpty branches
    @Test
    public void testDefaultsFamily() throws Throwable {
        assertEquals("", StringUtils.defaultString(null));
        assertEquals("bat", StringUtils.defaultString("bat"));
        assertEquals("NULL", StringUtils.defaultString(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfEmpty("", "NULL"));
        assertNull(StringUtils.defaultIfEmpty("", null));
    }

    // reverse / reverseDelimited branches
    @Test
    public void testReverseFamily() throws Throwable {
        assertNull(StringUtils.reverse(null));
        assertEquals("tab", StringUtils.reverse("bat"));
        assertEquals("c.b.a", StringUtils.reverseDelimited("a.b.c", '.'));
        assertEquals("a.b.c", StringUtils.reverseDelimited("a.b.c", 'x'));
    }

    // abbreviate branches including exception
    @Test
    public void testAbbreviateFamily() throws Throwable {
        assertEquals("abc...", StringUtils.abbreviate("abcdefg", 6));
        assertEquals("abcdefg", StringUtils.abbreviate("abcdefg", 7));
        try {
            StringUtils.abbreviate("abcdefg", 3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // abbreviateMiddle branches
    @Test
    public void testAbbreviateMiddle() throws Throwable {
        assertEquals("abc", StringUtils.abbreviateMiddle(null, ".", 0) == null ? "abc" : StringUtils.abbreviateMiddle("abc", null, 0));
        assertEquals("abc", StringUtils.abbreviateMiddle("abc", ".", 3));
        assertEquals("ab.f", StringUtils.abbreviateMiddle("abcdef", ".", 4));
    }

    // difference / indexOfDifference branches
    @Test
    public void testDifferenceFamily() throws Throwable {
        assertEquals("", StringUtils.difference("abc", "abc"));
        assertEquals("robot", StringUtils.difference("i am a machine", "i am a robot"));
        assertEquals(-1, StringUtils.indexOfDifference("abc", "abc"));
        assertEquals(2, StringUtils.indexOfDifference("ab", "abxyz"));
        assertEquals(0, StringUtils.indexOfDifference("", "abc"));
    }

    // indexOfDifference(CharSequence[]) and getCommonPrefix branches
    @Test
    public void testIndexOfDifferenceArrayAndCommonPrefix() throws Throwable {
        assertEquals(-1, StringUtils.indexOfDifference(new String[] {"abc", "abc"}));
        assertEquals(7, StringUtils.indexOfDifference(new String[] {"i am a machine", "i am a robot"}));
        assertEquals("", StringUtils.getCommonPrefix((String[]) null));
        assertEquals("ab", StringUtils.getCommonPrefix(new String[] {"abcde", "abxyz"}));
    }

    // getLevenshteinDistance branches including exception
    @Test
    public void testGetLevenshteinDistance() throws Throwable {
        assertEquals(0, StringUtils.getLevenshteinDistance("", ""));
        assertEquals(1, StringUtils.getLevenshteinDistance("frog", "fog"));
        assertEquals(3, StringUtils.getLevenshteinDistance("fly", "ant"));
        try {
            StringUtils.getLevenshteinDistance(null, "a");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // startsWith / startsWithIgnoreCase branches
    @Test
    public void testStartsWithFamily() throws Throwable {
        assertTrue(StringUtils.startsWith(null, null));
        assertFalse(StringUtils.startsWith(null, "abc"));
        assertTrue(StringUtils.startsWith("abcdef", "abc"));
        assertFalse(StringUtils.startsWith("ABCDEF", "abc"));
        assertTrue(StringUtils.startsWithIgnoreCase("ABCDEF", "abc"));
    }

    // endsWith / endsWithIgnoreCase branches
    @Test
    public void testEndsWithFamily() throws Throwable {
        assertTrue(StringUtils.endsWith(null, null));
        assertFalse(StringUtils.endsWith(null, "def"));
        assertTrue(StringUtils.endsWith("abcdef", "def"));
        assertFalse(StringUtils.endsWith("ABCDEF", "def"));
        assertTrue(StringUtils.endsWithIgnoreCase("ABCDEF", "def"));
    }
}
