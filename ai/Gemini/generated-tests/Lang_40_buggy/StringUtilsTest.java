package org.apache.commons.lang;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class StringUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        StringUtils utils = new StringUtils();
        assertNotNull(utils);
    }

    @Test
    public void testIsEmpty() throws Throwable {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isEmpty("bob"));
        assertFalse(StringUtils.isEmpty("  bob  "));
    }

    @Test
    public void testIsNotEmpty() throws Throwable {
        assertFalse(StringUtils.isNotEmpty(null));
        assertFalse(StringUtils.isNotEmpty(""));
        assertTrue(StringUtils.isNotEmpty(" "));
        assertTrue(StringUtils.isNotEmpty("bob"));
    }

    @Test
    public void testIsBlank() throws Throwable {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank("   "));
        assertFalse(StringUtils.isBlank("bob"));
        assertFalse(StringUtils.isBlank("  bob  "));
        assertFalse(StringUtils.isBlank(" a "));
    }

    @Test
    public void testIsNotBlank() throws Throwable {
        assertFalse(StringUtils.isNotBlank(null));
        assertFalse(StringUtils.isNotBlank(""));
        assertFalse(StringUtils.isNotBlank("   "));
        assertTrue(StringUtils.isNotBlank("bob"));
        assertTrue(StringUtils.isNotBlank("  bob  "));
    }

    @Test
    public void testTrim() throws Throwable {
        assertNull(StringUtils.trim(null));
        assertEquals("", StringUtils.trim(""));
        assertEquals("", StringUtils.trim("     "));
        assertEquals("abc", StringUtils.trim("abc"));
        assertEquals("abc", StringUtils.trim("    abc    "));
    }

    @Test
    public void testTrimToNull() throws Throwable {
        assertNull(StringUtils.trimToNull(null));
        assertNull(StringUtils.trimToNull(""));
        assertNull(StringUtils.trimToNull("     "));
        assertEquals("abc", StringUtils.trimToNull("abc"));
        assertEquals("abc", StringUtils.trimToNull("    abc    "));
    }

    @Test
    public void testTrimToEmpty() throws Throwable {
        assertEquals("", StringUtils.trimToEmpty(null));
        assertEquals("", StringUtils.trimToEmpty(""));
        assertEquals("", StringUtils.trimToEmpty("     "));
        assertEquals("abc", StringUtils.trimToEmpty("abc"));
        assertEquals("abc", StringUtils.trimToEmpty("    abc    "));
    }

    @Test
    public void testStrip() throws Throwable {
        assertNull(StringUtils.strip(null));
        assertEquals("", StringUtils.strip(""));
        assertEquals("", StringUtils.strip("   "));
        assertEquals("abc", StringUtils.strip("abc"));
        assertEquals("abc", StringUtils.strip("  abc"));
        assertEquals("abc", StringUtils.strip("abc  "));
        assertEquals("abc", StringUtils.strip(" abc "));
        assertEquals("ab c", StringUtils.strip(" ab c "));
        
        assertNull(StringUtils.strip(null, "xyz"));
        assertEquals("", StringUtils.strip("", "xyz"));
        assertEquals("abc", StringUtils.strip("abc", null));
        assertEquals("abc", StringUtils.strip("  abc", null));
        assertEquals("abc", StringUtils.strip("abc  ", null));
        assertEquals("abc", StringUtils.strip(" abc ", null));
        assertEquals("  abc", StringUtils.strip("  abcyx", "xyz"));
    }

    @Test
    public void testStripToNull() throws Throwable {
        assertNull(StringUtils.stripToNull(null));
        assertNull(StringUtils.stripToNull(""));
        assertNull(StringUtils.stripToNull("   "));
        assertEquals("abc", StringUtils.stripToNull("abc"));
        assertEquals("abc", StringUtils.stripToNull("  abc"));
        assertEquals("abc", StringUtils.stripToNull("abc  "));
        assertEquals("abc", StringUtils.stripToNull(" abc "));
        assertEquals("ab c", StringUtils.stripToNull(" ab c "));
    }

    @Test
    public void testStripToEmpty() throws Throwable {
        assertEquals("", StringUtils.stripToEmpty(null));
        assertEquals("", StringUtils.stripToEmpty(""));
        assertEquals("", StringUtils.stripToEmpty("   "));
        assertEquals("abc", StringUtils.stripToEmpty("abc"));
        assertEquals("abc", StringUtils.stripToEmpty("  abc"));
        assertEquals("abc", StringUtils.stripToEmpty("abc  "));
        assertEquals("abc", StringUtils.stripToEmpty(" abc "));
        assertEquals("ab c", StringUtils.stripToEmpty(" ab c "));
    }

    @Test
    public void testStripStart() throws Throwable {
        assertNull(StringUtils.stripStart(null, "xyz"));
        assertEquals("", StringUtils.stripStart("", "xyz"));
        assertEquals("abc", StringUtils.stripStart("abc", ""));
        assertEquals("abc", StringUtils.stripStart("abc", null));
        assertEquals("abc", StringUtils.stripStart("  abc", null));
        assertEquals("abc  ", StringUtils.stripStart("abc  ", null));
        assertEquals("abc ", StringUtils.stripStart(" abc ", null));
        assertEquals("abc  ", StringUtils.stripStart("yxabc  ", "xyz"));
    }

    @Test
    public void testStripEnd() throws Throwable {
        assertNull(StringUtils.stripEnd(null, "xyz"));
        assertEquals("", StringUtils.stripEnd("", "xyz"));
        assertEquals("abc", StringUtils.stripEnd("abc", ""));
        assertEquals("abc", StringUtils.stripEnd("abc", null));
        assertEquals("  abc", StringUtils.stripEnd("  abc", null));
        assertEquals("abc", StringUtils.stripEnd("abc  ", null));
        assertEquals(" abc", StringUtils.stripEnd(" abc ", null));
        assertEquals("  abc", StringUtils.stripEnd("  abcyx", "xyz"));
    }

    @Test
    public void testStripAll() throws Throwable {
        assertNull(StringUtils.stripAll(null));
        assertNull(StringUtils.stripAll(null, "xyz"));
        
        String[] emptyArr = new String[0];
        assertSame(emptyArr, StringUtils.stripAll(emptyArr));
        assertSame(emptyArr, StringUtils.stripAll(emptyArr, "xyz"));

        String[] input = new String[]{"abc", "  abc", null};
        String[] expected = new String[]{"abc", "abc", null};
        assertTrue(Arrays.equals(expected, StringUtils.stripAll(input)));

        String[] input2 = new String[]{"abc  ", null};
        String[] expected2 = new String[]{"abc", null};
        assertTrue(Arrays.equals(expected2, StringUtils.stripAll(input2, null)));

        String[] input3 = new String[]{"yabcz", null};
        String[] expected3 = new String[]{"abc", null};
        assertTrue(Arrays.equals(expected3, StringUtils.stripAll(input3, "yz")));
    }

    @Test
    public void testEquals() throws Throwable {
        assertTrue(StringUtils.equals(null, null));
        assertFalse(StringUtils.equals(null, "abc"));
        assertFalse(StringUtils.equals("abc", null));
        assertTrue(StringUtils.equals("abc", "abc"));
        assertFalse(StringUtils.equals("abc", "ABC"));
    }

    @Test
    public void testEqualsIgnoreCase() throws Throwable {
        assertTrue(StringUtils.equalsIgnoreCase(null, null));
        assertFalse(StringUtils.equalsIgnoreCase(null, "abc"));
        assertFalse(StringUtils.equalsIgnoreCase("abc", null));
        assertTrue(StringUtils.equalsIgnoreCase("abc", "abc"));
        assertTrue(StringUtils.equalsIgnoreCase("abc", "ABC"));
        assertFalse(StringUtils.equalsIgnoreCase("abc", "abcd"));
    }

    @Test
    public void testIndexOf() throws Throwable {
        assertEquals(-1, StringUtils.indexOf(null, 'a'));
        assertEquals(-1, StringUtils.indexOf("", 'a'));
        assertEquals(0, StringUtils.indexOf("aabaabaa", 'a'));
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b'));

        assertEquals(-1, StringUtils.indexOf(null, 'b', 0));
        assertEquals(-1, StringUtils.indexOf("", 'b', 0));
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b', 0));
        assertEquals(5, StringUtils.indexOf("aabaabaa", 'b', 3));
        assertEquals(-1, StringUtils.indexOf("aabaabaa", 'b', 9));
        assertEquals(2, StringUtils.indexOf("aabaabaa", 'b', -1));

        assertEquals(-1, StringUtils.indexOf(null, "a"));
        assertEquals(-1, StringUtils.indexOf("abc", null));
        assertEquals(0, StringUtils.indexOf("", ""));
        assertEquals(0, StringUtils.indexOf("aabaabaa", "a"));
        assertEquals(2, StringUtils.indexOf("aabaabaa", "b"));
        assertEquals(1, StringUtils.indexOf("aabaabaa", "ab"));
        assertEquals(0, StringUtils.indexOf("aabaabaa", ""));

        assertEquals(-1, StringUtils.indexOf(null, "a", 0));
        assertEquals(-1, StringUtils.indexOf("abc", null, 0));
        assertEquals(0, StringUtils.indexOf("", "", 0));
        assertEquals(0, StringUtils.indexOf("aabaabaa", "a", 0));
        assertEquals(2, StringUtils.indexOf("aabaabaa", "b", 0));
        assertEquals(1, StringUtils.indexOf("aabaabaa", "ab", 0));
        assertEquals(5, StringUtils.indexOf("aabaabaa", "b", 3));
        assertEquals(-1, StringUtils.indexOf("aabaabaa", "b", 9));
        assertEquals(2, StringUtils.indexOf("aabaabaa", "b", -1));
        assertEquals(2, StringUtils.indexOf("aabaabaa", "", 2));
        assertEquals(3, StringUtils.indexOf("abc", "", 9));
    }

    @Test
    public void testOrdinalIndexOf() throws Throwable {
        assertEquals(-1, StringUtils.ordinalIndexOf(null, "a", 1));
        assertEquals(-1, StringUtils.ordinalIndexOf("abc", null, 1));
        assertEquals(-1, StringUtils.ordinalIndexOf("abc", "a", 0));
        assertEquals(-1, StringUtils.ordinalIndexOf("abc", "a", -1));
        assertEquals(0, StringUtils.ordinalIndexOf("", "", 1));
        assertEquals(0, StringUtils.ordinalIndexOf("aabaabaa", "a", 1));
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "a", 2));
        assertEquals(2, StringUtils.ordinalIndexOf("aabaabaa", "b", 1));
        assertEquals(5, StringUtils.ordinalIndexOf("aabaabaa", "b", 2));
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "ab", 1));
        assertEquals(4, StringUtils.ordinalIndexOf("aabaabaa", "ab", 2));
        assertEquals(0, StringUtils.ordinalIndexOf("aabaabaa", "", 1));
        assertEquals(-1, StringUtils.ordinalIndexOf("a", "aa", 1));
    }

    @Test
    public void testLastIndexOf() throws Throwable {
        assertEquals(-1, StringUtils.lastIndexOf(null, 'a'));
        assertEquals(-1, StringUtils.lastIndexOf("", 'a'));
        assertEquals(7, StringUtils.lastIndexOf("aabaabaa", 'a'));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", 'b'));

        assertEquals(-1, StringUtils.lastIndexOf(null, 'b', 8));
        assertEquals(-1, StringUtils.lastIndexOf("", 'b', 8));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", 'b', 8));
        assertEquals(2, StringUtils.lastIndexOf("aabaabaa", 'b', 4));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", 'b', 0));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", 'b', 9));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", 'b', -1));
        assertEquals(0, StringUtils.lastIndexOf("aabaabaa", 'a', 0));

        assertEquals(-1, StringUtils.lastIndexOf(null, "a"));
        assertEquals(-1, StringUtils.lastIndexOf("abc", null));
        assertEquals(0, StringUtils.lastIndexOf("", ""));
        assertEquals(0, StringUtils.lastIndexOf("aabaabaa", "a"));
        assertEquals(2, StringUtils.lastIndexOf("aabaabaa", "b"));
        assertEquals(1, StringUtils.lastIndexOf("aabaabaa", "ab"));
        assertEquals(8, StringUtils.lastIndexOf("aabaabaa", ""));

        assertEquals(-1, StringUtils.lastIndexOf(null, "a", 8));
        assertEquals(-1, StringUtils.lastIndexOf("abc", null, 8));
        assertEquals(7, StringUtils.lastIndexOf("aabaabaa", "a", 8));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", "b", 8));
        assertEquals(4, StringUtils.lastIndexOf("aabaabaa", "ab", 8));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", "b", 9));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", "b", -1));
        assertEquals(0, StringUtils.lastIndexOf("aabaabaa", "a", 0));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", "b", 0));
    }

    @Test
    public void testContains() throws Throwable {
        assertFalse(StringUtils.contains(null, 'a'));
        assertFalse(StringUtils.contains("", 'a'));
        assertTrue(StringUtils.contains("abc", 'a'));
        assertFalse(StringUtils.contains("abc", 'z'));

        assertFalse(StringUtils.contains(null, "a"));
        assertFalse(StringUtils.contains("abc", null));
        assertTrue(StringUtils.contains("", ""));
        assertTrue(StringUtils.contains("abc", ""));
        assertTrue(StringUtils.contains("abc", "a"));
        assertFalse(StringUtils.contains("abc", "z"));

        assertFalse(StringUtils.containsIgnoreCase(null, "a"));
        assertFalse(StringUtils.containsIgnoreCase("abc", null));
        assertTrue(StringUtils.containsIgnoreCase("", ""));
        assertTrue(StringUtils.containsIgnoreCase("abc", "a"));
        assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
        assertFalse(StringUtils.containsIgnoreCase("abc", "Z"));
    }

    @Test
    public void testIndexOfAny() throws Throwable {
        assertEquals(-1, StringUtils.indexOfAny(null, new char[]{'z', 'a'}));
        assertEquals(-1, StringUtils.indexOfAny("", new char[]{'z', 'a'}));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", (char[]) null));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", new char[0]));
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", new char[]{'z', 'a'}));
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", new char[]{'b', 'y'}));
        assertEquals(-1, StringUtils.indexOfAny("aba", new char[]{'z'}));

        assertEquals(-1, StringUtils.indexOfAny(null, "za"));
        assertEquals(-1, StringUtils.indexOfAny("", "za"));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", (String) null));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", ""));
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", "za"));
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", "by"));
        assertEquals(-1, StringUtils.indexOfAny("aba", "z"));

        assertEquals(-1, StringUtils.indexOfAny(null, new String[]{"ab", "cd"}));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", (String[]) null));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", new String[0]));
        assertEquals(2, StringUtils.indexOfAny("zzabyycdxx", new String[]{"ab", "cd"}));
        assertEquals(2, StringUtils.indexOfAny("zzabyycdxx", new String[]{"cd", "ab"}));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", new String[]{"mn", "op"}));
        assertEquals(1, StringUtils.indexOfAny("zzabyycdxx", new String[]{"zab", "aby"}));
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", new String[]{null, ""}));
        assertEquals(0, StringUtils.indexOfAny("", new String[]{""}));
        assertEquals(-1, StringUtils.indexOfAny("", new String[]{"a"}));
    }

    @Test
    public void testContainsAny() throws Throwable {
        assertFalse(StringUtils.containsAny(null, new char[]{'z', 'a'}));
        assertFalse(StringUtils.containsAny("", new char[]{'z', 'a'}));
        assertFalse(StringUtils.containsAny("zzabyycdxx", (char[]) null));
        assertFalse(StringUtils.containsAny("zzabyycdxx", new char[0]));
        assertTrue(StringUtils.containsAny("zzabyycdxx", new char[]{'z', 'a'}));
        assertTrue(StringUtils.containsAny("zzabyycdxx", new char[]{'b', 'y'}));
        assertFalse(StringUtils.containsAny("aba", new char[]{'z'}));

        assertFalse(StringUtils.containsAny(null, "za"));
        assertFalse(StringUtils.containsAny("", "za"));
        assertFalse(StringUtils.containsAny("zzabyycdxx", (String) null));
        assertFalse(StringUtils.containsAny("zzabyycdxx", ""));
        assertTrue(StringUtils.containsAny("zzabyycdxx", "za"));
        assertTrue(StringUtils.containsAny("zzabyycdxx", "by"));
        assertFalse(StringUtils.containsAny("aba", "z"));
    }

    @Test
    public void testIndexOfAnyBut() throws Throwable {
        assertEquals(-1, StringUtils.indexOfAnyBut(null, new char[]{'z', 'a'}));
        assertEquals(-1, StringUtils.indexOfAnyBut("", new char[]{'z', 'a'}));
        assertEquals(-1, StringUtils.indexOfAnyBut("zzabyycdxx", (char[]) null));
        assertEquals(-1, StringUtils.indexOfAnyBut("zzabyycdxx", new char[0]));
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", new char[]{'z', 'a'}));
        assertEquals(0, StringUtils.indexOfAnyBut("zzabyycdxx", new char[0])); // wait, empty array returns -1 per implementation
        assertEquals(-1, StringUtils.indexOfAnyBut("aba", new char[]{'a', 'b'}));
        
        assertEquals(-1, StringUtils.indexOfAnyBut(null, "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("", "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("zzabyycdxx", (String) null));
        assertEquals(-1, StringUtils.indexOfAnyBut("zzabyycdxx", ""));
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", "za"));
        assertEquals(0, StringUtils.indexOfAnyBut("zzabyycdxx", ""));
        assertEquals(-1, StringUtils.indexOfAnyBut("aba", "ab"));
    }

    @Test
    public void testContainsOnly() throws Throwable {
        assertFalse(StringUtils.containsOnly(null, new char[]{'a'}));
        assertFalse(StringUtils.containsOnly("ab", (char[]) null));
        assertTrue(StringUtils.containsOnly("", new char[]{'a'}));
        assertFalse(StringUtils.containsOnly("ab", new char[0]));
        assertTrue(StringUtils.containsOnly("abab", new char[]{'a', 'b', 'c'}));
        assertFalse(StringUtils.containsOnly("ab1", new char[]{'a', 'b', 'c'}));

        assertFalse(StringUtils.containsOnly(null, "abc"));
        assertFalse(StringUtils.containsOnly("ab", (String) null));
        assertTrue(StringUtils.containsOnly("", "abc"));
        assertFalse(StringUtils.containsOnly("ab", ""));
        assertTrue(StringUtils.containsOnly("abab", "abc"));
        assertFalse(StringUtils.containsOnly("ab1", "abc"));
        assertFalse(StringUtils.containsOnly("abz", "abc"));
    }

    @Test
    public void testContainsNone() throws Throwable {
        assertTrue(StringUtils.containsNone(null, new char[]{'a'}));
        assertTrue(StringUtils.containsNone("ab", (char[]) null));
        assertTrue(StringUtils.containsNone("", new char[]{'a'}));
        assertTrue(StringUtils.containsNone("ab", new char[0]));
        assertTrue(StringUtils.containsNone("abab", new char[]{'x', 'y', 'z'}));
        assertTrue(StringUtils.containsNone("ab1", new char[]{'x', 'y', 'z'}));
        assertFalse(StringUtils.containsNone("abz", new char[]{'x', 'y', 'z'}));

        assertTrue(StringUtils.containsNone(null, "abc"));
        assertTrue(StringUtils.containsNone("ab", (String) null));
        assertTrue(StringUtils.containsNone("", "abc"));
        assertTrue(StringUtils.containsNone("ab", ""));
        assertTrue(StringUtils.containsNone("abab", "xyz"));
        assertTrue(StringUtils.containsNone("ab1", "xyz"));
        assertFalse(StringUtils.containsNone("abz", "xyz"));
    }

    @Test
    public void testLastIndexOfAny() throws Throwable {
        assertEquals(-1, StringUtils.lastIndexOfAny(null, new String[]{"ab"}));
        assertEquals(-1, StringUtils.lastIndexOfAny("zzabyycdxx", (String[]) null));
        assertEquals(-1, StringUtils.lastIndexOfAny("zzabyycdxx", new String[0]));
        assertEquals(-1, StringUtils.lastIndexOfAny("zzabyycdxx", new String[]{null}));
        assertEquals(6, StringUtils.lastIndexOfAny("zzabyycdxx", new String[]{"ab", "cd"}));
        assertEquals(6, StringUtils.lastIndexOfAny("zzabyycdxx", new String[]{"cd", "ab"}));
        assertEquals(-1, StringUtils.lastIndexOfAny("zzabyycdxx", new String[]{"mn", "op"}));
        assertEquals(10, StringUtils.lastIndexOfAny("zzabyycdxx", new String[]{"mn", ""}));
    }

    @Test
    public void testSubstring() throws Throwable {
        assertNull(StringUtils.substring(null, 0));
        assertEquals("", StringUtils.substring("", 0));
        assertEquals("abc", StringUtils.substring("abc", 0));
        assertEquals("c", StringUtils.substring("abc", 2));
        assertEquals("", StringUtils.substring("abc", 4));
        assertEquals("bc", StringUtils.substring("abc", -2));
        assertEquals("abc", StringUtils.substring("abc", -4));

        assertNull(StringUtils.substring(null, 0, 0));
        assertEquals("", StringUtils.substring("", 0, 0));
        assertEquals("ab", StringUtils.substring("abc", 0, 2));
        assertEquals("", StringUtils.substring("abc", 2, 0));
        assertEquals("c", StringUtils.substring("abc", 2, 4));
        assertEquals("", StringUtils.substring("abc", 4, 6));
        assertEquals("", StringUtils.substring("abc", 2, 2));
        assertEquals("b", StringUtils.substring("abc", -2, -1));
        assertEquals("ab", StringUtils.substring("abc", -4, 2));
        assertEquals("abc", StringUtils.substring("abc", -4, 10));
    }

    @Test
    public void testLeftRightMid() throws Throwable {
        assertNull(StringUtils.left(null, 2));
        assertEquals("", StringUtils.left("abc", -1));
        assertEquals("", StringUtils.left("", 2));
        assertEquals("", StringUtils.left("abc", 0));
        assertEquals("ab", StringUtils.left("abc", 2));
        assertEquals("abc", StringUtils.left("abc", 4));

        assertNull(StringUtils.right(null, 2));
        assertEquals("", StringUtils.right("abc", -1));
        assertEquals("", StringUtils.right("", 2));
        assertEquals("", StringUtils.right("abc", 0));
        assertEquals("bc", StringUtils.right("abc", 2));
        assertEquals("abc", StringUtils.right("abc", 4));

        assertNull(StringUtils.mid(null, 0, 2));
        assertEquals("", StringUtils.mid("abc", 0, -1));
        assertEquals("", StringUtils.mid("", 0, 2));
        assertEquals("ab", StringUtils.mid("abc", 0, 2));
        assertEquals("abc", StringUtils.mid("abc", 0, 4));
        assertEquals("c", StringUtils.mid("abc", 2, 4));
        assertEquals("", StringUtils.mid("abc", 4, 2));
        assertEquals("ab", StringUtils.mid("abc", -2, 2));
        assertEquals("bc", StringUtils.mid("abc", 1, 2));
    }

    @Test
    public void testSubstringBeforeAfter() throws Throwable {
        assertNull(StringUtils.substringBefore(null, "a"));
        assertEquals("", StringUtils.substringBefore("", "a"));
        assertEquals("", StringUtils.substringBefore("abc", "a"));
        assertEquals("a", StringUtils.substringBefore("abcba", "b"));
        assertEquals("ab", StringUtils.substringBefore("abc", "c"));
        assertEquals("abc", StringUtils.substringBefore("abc", "d"));
        assertEquals("", StringUtils.substringBefore("abc", ""));
        assertEquals("abc", StringUtils.substringBefore("abc", null));

        assertNull(StringUtils.substringAfter(null, "a"));
        assertEquals("", StringUtils.substringAfter("", "a"));
        assertEquals("", StringUtils.substringAfter("abc", null));
        assertEquals("bc", StringUtils.substringAfter("abc", "a"));
        assertEquals("cba", StringUtils.substringAfter("abcba", "b"));
        assertEquals("", StringUtils.substringAfter("abc", "c"));
        assertEquals("", StringUtils.substringAfter("abc", "d"));
        assertEquals("abc", StringUtils.substringAfter("abc", ""));

        assertNull(StringUtils.substringBeforeLast(null, "a"));
        assertEquals("", StringUtils.substringBeforeLast("", "a"));
        assertEquals("abc", StringUtils.substringBeforeLast("abcba", "b"));
        assertEquals("ab", StringUtils.substringBeforeLast("abc", "c"));
        assertEquals("", StringUtils.substringBeforeLast("a", "a"));
        assertEquals("a", StringUtils.substringBeforeLast("a", "z"));
        assertEquals("a", StringUtils.substringBeforeLast("a", null));
        assertEquals("a", StringUtils.substringBeforeLast("a", ""));

        assertNull(StringUtils.substringAfterLast(null, "a"));
        assertEquals("", StringUtils.substringAfterLast("", "a"));
        assertEquals("", StringUtils.substringAfterLast("abc", ""));
        assertEquals("", StringUtils.substringAfterLast("abc", null));
        assertEquals("bc", StringUtils.substringAfterLast("abc", "a"));
        assertEquals("a", StringUtils.substringAfterLast("abcba", "b"));
        assertEquals("", StringUtils.substringAfterLast("abc", "c"));
        assertEquals("", StringUtils.substringAfterLast("a", "a"));
        assertEquals("", StringUtils.substringAfterLast("a", "z"));
    }

    @Test
    public void testSubstringBetween() throws Throwable {
        assertNull(StringUtils.substringBetween(null, "tag"));
        assertEquals("", StringUtils.substringBetween("", ""));
        assertNull(StringUtils.substringBetween("", "tag"));
        assertNull(StringUtils.substringBetween("tagabctag", null));
        assertEquals("", StringUtils.substringBetween("tagabctag", ""));
        assertEquals("abc", StringUtils.substringBetween("tagabctag", "tag"));

        assertEquals("b", StringUtils.substringBetween("wx[b]yz", "[", "]"));
        assertNull(StringUtils.substringBetween(null, "[", "]"));
        assertNull(StringUtils.substringBetween("wx[b]yz", null, "]"));
        assertNull(StringUtils.substringBetween("wx[b]yz", "[", null));
        assertEquals("", StringUtils.substringBetween("", "", ""));
        assertNull(StringUtils.substringBetween("", "", "]"));
        assertNull(StringUtils.substringBetween("", "[", "]"));
        assertEquals("", StringUtils.substringBetween("yabcz", "", ""));
        assertEquals("abc", StringUtils.substringBetween("yabcz", "y", "z"));
        assertEquals("abc", StringUtils.substringBetween("yabczyabcz", "y", "z"));

        assertNull(StringUtils.substringsBetween(null, "[", "]"));
        assertNull(StringUtils.substringsBetween("[a][b][c]", null, "]"));
        assertNull(StringUtils.substringsBetween("[a][b][c]", "[", null));
        assertNull(StringUtils.substringsBetween("[a][b][c]", "", "]"));
        assertNull(StringUtils.substringsBetween("[a][b][c]", "[", ""));
        assertTrue(Arrays.equals(new String[0], StringUtils.substringsBetween("", "[", "]")));
        assertTrue(Arrays.equals(new String[]{"a", "b", "c"}, StringUtils.substringsBetween("[a][b][c]", "[", "]")));
        assertNull(StringUtils.substringsBetween("abc", "[", "]"));
    }

    @Test
    public void testSplit() throws Throwable {
        assertNull(StringUtils.split(null));
        assertTrue(Arrays.equals(new String[0], StringUtils.split("")));
        assertTrue(Arrays.equals(new String[]{"abc", "def"}, StringUtils.split("abc def")));
        assertTrue(Arrays.equals(new String[]{"abc", "def"}, StringUtils.split("abc  def")));
        assertTrue(Arrays.equals(new String[]{"abc"}, StringUtils.split(" abc ")));

        assertNull(StringUtils.split(null, '.'));
        assertTrue(Arrays.equals(new String[0], StringUtils.split("", '.')));
        assertTrue(Arrays.equals(new String[]{"a", "b", "c"}, StringUtils.split("a.b.c", '.')));
        assertTrue(Arrays.equals(new String[]{"a", "b", "c"}, StringUtils.split("a..b.c", '.')));
        assertTrue(Arrays.equals(new String[]{"a:b:c"}, StringUtils.split("a:b:c", '.')));
        assertTrue(Arrays.equals(new String[]{"a", "b", "c"}, StringUtils.split("a b c", ' ')));

        assertNull(StringUtils.split(null, ""));
        assertTrue(Arrays.equals(new String[0], StringUtils.split("", "")));
        assertTrue(Arrays.equals(new String[]{"abc", "def"}, StringUtils.split("abc def", null)));
        assertTrue(Arrays.equals(new String[]{"abc", "def"}, StringUtils.split("abc def", " ")));
        assertTrue(Arrays.equals(new String[]{"abc", "def"}, StringUtils.split("abc  def", " ")));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.split("ab:cd:ef", ":")));

        assertNull(StringUtils.split(null, "", 2));
        assertTrue(Arrays.equals(new String[0], StringUtils.split("", "", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd:ef"}, StringUtils.split("ab:cd:ef", ":", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.split("ab:cd:ef", ":", 0)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.split("ab:cd:ef", ":", -1)));

        assertNull(StringUtils.splitByWholeSeparator(null, ""));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitByWholeSeparator("", "")));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparator("ab de fg", null)));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparator("ab   de fg", null)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab:cd:ef", ":")));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-")));

        assertNull(StringUtils.splitByWholeSeparator(null, "", 2));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitByWholeSeparator("", "", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparator("ab de fg", null, 0)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd:ef"}, StringUtils.splitByWholeSeparator("ab:cd:ef", ":", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-", 5)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd-!-ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparator("ab de fg", "")));

        assertNull(StringUtils.splitByWholeSeparatorPreserveAllTokens(null, ""));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitByWholeSeparatorPreserveAllTokens("", "")));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab de fg", null)));
        assertTrue(Arrays.equals(new String[]{"ab", "", "", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab   de fg", null)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab:cd:ef", ":")));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab-!-cd-!-ef", "-!-")));

        assertNull(StringUtils.splitByWholeSeparatorPreserveAllTokens(null, "", 2));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitByWholeSeparatorPreserveAllTokens("", "", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab de fg", null, 0)));
        assertTrue(Arrays.equals(new String[]{"ab", "", "", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab   de fg", null, 0)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd:ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab:cd:ef", ":", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab-!-cd-!-ef", "-!-", 5)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd-!-ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab-!-cd-!-ef", "-!-", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab de fg", "")));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab de fg", "", 0)));

        assertNull(StringUtils.splitPreserveAllTokens(null));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitPreserveAllTokens("")));
        assertTrue(Arrays.equals(new String[]{"abc", "def"}, StringUtils.splitPreserveAllTokens("abc def")));
        assertTrue(Arrays.equals(new String[]{"abc", "", "def"}, StringUtils.splitPreserveAllTokens("abc  def")));
        assertTrue(Arrays.equals(new String[]{"", "abc", ""}, StringUtils.splitPreserveAllTokens(" abc ")));

        assertNull(StringUtils.splitPreserveAllTokens(null, '.'));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitPreserveAllTokens("", '.')));
        assertTrue(Arrays.equals(new String[]{"a", "b", "c"}, StringUtils.splitPreserveAllTokens("a.b.c", '.')));
        assertTrue(Arrays.equals(new String[]{"a", "", "b", "c"}, StringUtils.splitPreserveAllTokens("a..b.c", '.')));

        assertNull(StringUtils.splitPreserveAllTokens(null, ""));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitPreserveAllTokens("", "")));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef"}, StringUtils.splitPreserveAllTokens("ab:cd:ef", ":")));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef", ""}, StringUtils.splitPreserveAllTokens("ab:cd:ef:", ":")));
        assertTrue(Arrays.equals(new String[]{"ab", "cd", "ef", "", ""}, StringUtils.splitPreserveAllTokens("ab:cd:ef::", ":")));

        assertNull(StringUtils.splitPreserveAllTokens(null, "", 2));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitPreserveAllTokens("", "", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "de", "fg"}, StringUtils.splitPreserveAllTokens("ab de fg", null, 0)));
        assertTrue(Arrays.equals(new String[]{"ab", "cd:ef"}, StringUtils.splitPreserveAllTokens("ab:cd:ef", ":", 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "  de fg"}, StringUtils.splitPreserveAllTokens("ab   de fg", null, 2)));
        assertTrue(Arrays.equals(new String[]{"ab", "", " de fg"}, StringUtils.splitPreserveAllTokens("ab   de fg", null, 3)));
        assertTrue(Arrays.equals(new String[]{"ab", "", "", "de fg"}, StringUtils.splitPreserveAllTokens("ab   de fg", null, 4)));

        assertNull(StringUtils.splitByCharacterType(null));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitByCharacterType("")));
        assertTrue(Arrays.equals(new String[]{"number", "5"}, StringUtils.splitByCharacterType("number5")));

        assertNull(StringUtils.splitByCharacterTypeCamelCase(null));
        assertTrue(Arrays.equals(new String[0], StringUtils.splitByCharacterTypeCamelCase("")));
        assertTrue(Arrays.equals(new String[]{"foo", "Bar"}, StringUtils.splitByCharacterTypeCamelCase("fooBar")));
        assertTrue(Arrays.equals(new String[]{"foo", "200", "Bar"}, StringUtils.splitByCharacterTypeCamelCase("foo200Bar")));
    }

    @Test
    public void testJoin() throws Throwable {
        assertNull(StringUtils.join((Object[]) null));
        assertEquals("", StringUtils.join(new Object[0]));
        assertEquals("", StringUtils.join(new Object[]{null}));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}));
        assertEquals("a", StringUtils.join(new Object[]{null, "", "a"}));

        assertNull(StringUtils.join((Object[]) null, ';'));
        assertEquals("", StringUtils.join(new Object[0], ';'));
        assertEquals("", StringUtils.join(new Object[]{null}, ';'));
        assertEquals("a;b;c", StringUtils.join(new Object[]{"a", "b", "c"}, ';'));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}, (char) 0));
        assertEquals(";;a", StringUtils.join(new Object[]{null, "", "a"}, ';'));
        assertEquals("", StringUtils.join(new Object[]{"a", "b", "c"}, ';', 0, 0));

        assertNull(StringUtils.join((Object[]) null, "--"));
        assertEquals("", StringUtils.join(new Object[0], "--"));
        assertEquals("", StringUtils.join(new Object[]{null}, "--"));
        assertEquals("a--b--c", StringUtils.join(new Object[]{"a", "b", "c"}, "--"));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}, null));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}, ""));
        assertEquals(",,a", StringUtils.join(new Object[]{null, "", "a"}, ','));
        assertEquals("", StringUtils.join(new Object[]{"a", "b", "c"}, "--", 0, 0));
        assertEquals("b", StringUtils.join(new Object[]{"a", "b", "c"}, "--", 1, 2));

        assertNull(StringUtils.join((Iterator<?>) null, ';'));
        assertEquals("", StringUtils.join(new ArrayList<String>().iterator(), ';'));
        assertEquals("a", StringUtils.join(Arrays.asList(new String[]{"a"}).iterator(), ';'));
        assertEquals("a;b", StringUtils.join(Arrays.asList(new String[]{"a", "b"}).iterator(), ';'));
        assertEquals("ab", StringUtils.join(Arrays.asList(new String[]{"a", "b"}).iterator(), null));
        assertEquals(";b", StringUtils.join(Arrays.asList(new String[]{null, "b"}).iterator(), ';'));

        assertNull(StringUtils.join((Iterator<?>) null, "--"));
        assertEquals("", StringUtils.join(new ArrayList<String>().iterator(), "--"));
        assertEquals("a", StringUtils.join(Arrays.asList(new String[]{"a"}).iterator(), "--"));
        assertEquals("a--b", StringUtils.join(Arrays.asList(new String[]{"a", "b"}).iterator(), "--"));
        assertEquals("ab", StringUtils.join(Arrays.asList(new String[]{"a", "b"}).iterator(), null));

        assertNull(StringUtils.join((Collection<?>) null, ';'));
        assertEquals("a;b", StringUtils.join(Arrays.asList(new String[]{"a", "b"}), ';'));
        assertNull(StringUtils.join((Collection<?>) null, "--"));
        assertEquals("a--b", StringUtils.join(Arrays.asList(new String[]{"a", "b"}), "--"));
    }

    @Test
    public void testDeleteWhitespace() throws Throwable {
        assertNull(StringUtils.deleteWhitespace(null));
        assertEquals("", StringUtils.deleteWhitespace(""));
        assertEquals("abc", StringUtils.deleteWhitespace("abc"));
        assertEquals("abc", StringUtils.deleteWhitespace("   ab  c  "));
    }

    @Test
    public void testRemove() throws Throwable {
        assertNull(StringUtils.removeStart(null, "www."));
        assertEquals("", StringUtils.removeStart("", "www."));
        assertEquals("www.domain.com", StringUtils.removeStart("www.domain.com", null));
        assertEquals("www.domain.com", StringUtils.removeStart("www.domain.com", ""));
        assertEquals("domain.com", StringUtils.removeStart("www.domain.com", "www."));
        assertEquals("domain.com", StringUtils.removeStart("domain.com", "www."));
        assertEquals("www.domain.com", StringUtils.removeStart("www.domain.com", "domain"));

        assertNull(StringUtils.removeStartIgnoreCase(null, "www."));
        assertEquals("", StringUtils.removeStartIgnoreCase("", "www."));
        assertEquals("www.domain.com", StringUtils.removeStartIgnoreCase("www.domain.com", null));
        assertEquals("domain.com", StringUtils.removeStartIgnoreCase("www.domain.com", "WWW."));

        assertNull(StringUtils.removeEnd(null, ".com"));
        assertEquals("", StringUtils.removeEnd("", ".com"));
        assertEquals("www.domain.com", StringUtils.removeEnd("www.domain.com", null));
        assertEquals("www.domain.com", StringUtils.removeEnd("www.domain.com", ""));
        assertEquals("www.domain", StringUtils.removeEnd("www.domain.com", ".com"));
        assertEquals("www.domain.com", StringUtils.removeEnd("www.domain.com", "domain"));

        assertNull(StringUtils.removeEndIgnoreCase(null, ".com"));
        assertEquals("", StringUtils.removeEndIgnoreCase("", ".com"));
        assertEquals("www.domain.com", StringUtils.removeEndIgnoreCase("www.domain.com", null));
        assertEquals("www.domain", StringUtils.removeEndIgnoreCase("www.domain.com", ".COM"));

        assertNull(StringUtils.remove(null, "ue"));
        assertEquals("", StringUtils.remove("", "ue"));
        assertEquals("queued", StringUtils.remove("queued", null));
        assertEquals("queued", StringUtils.remove("queued", ""));
        assertEquals("qd", StringUtils.remove("queued", "ue"));
        assertEquals("queued", StringUtils.remove("queued", "zz"));

        assertNull(StringUtils.remove(null, 'u'));
        assertEquals("", StringUtils.remove("", 'u'));
        assertEquals("queued", StringUtils.remove("queued", 'z'));
        assertEquals("qeed", StringUtils.remove("queued", 'u'));
    }

    @Test
    public void testReplace() throws Throwable {
        assertNull(StringUtils.replaceOnce(null, "a", "z"));
        assertEquals("", StringUtils.replaceOnce("", "a", "z"));
        assertEquals("any", StringUtils.replaceOnce("any", null, "z"));
        assertEquals("any", StringUtils.replaceOnce("any", "a", null));
        assertEquals("any", StringUtils.replaceOnce("any", "", "z"));
        assertEquals("aba", StringUtils.replaceOnce("aba", "a", null));
        assertEquals("ba", StringUtils.replaceOnce("aba", "a", ""));
        assertEquals("zba", StringUtils.replaceOnce("aba", "a", "z"));

        assertNull(StringUtils.replace(null, "a", "z"));
        assertEquals("", StringUtils.replace("", "a", "z"));
        assertEquals("any", StringUtils.replace("any", null, "z"));
        assertEquals("any", StringUtils.replace("any", "a", null));
        assertEquals("any", StringUtils.replace("any", "", "z"));
        assertEquals("aba", StringUtils.replace("aba", "a", null));
        assertEquals("b", StringUtils.replace("aba", "a", ""));
        assertEquals("zbz", StringUtils.replace("aba", "a", "z"));

        assertNull(StringUtils.replace(null, "a", "z", 1));
        assertEquals("", StringUtils.replace("", "a", "z", 1));
        assertEquals("any", StringUtils.replace("any", null, "z", 1));
        assertEquals("any", StringUtils.replace("any", "a", null, 1));
        assertEquals("any", StringUtils.replace("any", "", "z", 1));
        assertEquals("any", StringUtils.replace("any", "a", "z", 0));
        assertEquals("abaa", StringUtils.replace("abaa", "a", null, -1));
        assertEquals("b", StringUtils.replace("abaa", "a", "", -1));
        assertEquals("abaa", StringUtils.replace("abaa", "a", "z", 0));
        assertEquals("zbaa", StringUtils.replace("abaa", "a", "z", 1));
        assertEquals("zbza", StringUtils.replace("abaa", "a", "z", 2));
        assertEquals("zbzz", StringUtils.replace("abaa", "a", "z", -1));
        assertEquals("abaa", StringUtils.replace("abaa", "x", "z", 1));

        assertNull(StringUtils.replaceEach(null, new String[]{"a"}, new String[]{"z"}));
        assertEquals("", StringUtils.replaceEach("", new String[]{"a"}, new String[]{"z"}));
        assertEquals("aba", StringUtils.replaceEach("aba", null, null));
        assertEquals("aba", StringUtils.replaceEach("aba", new String[0], null));
        assertEquals("aba", StringUtils.replaceEach("aba", null, new String[0]));
        assertEquals("aba", StringUtils.replaceEach("aba", new String[]{"a"}, null));
        assertEquals("b", StringUtils.replaceEach("aba", new String[]{"a"}, new String[]{""}));
        assertEquals("aba", StringUtils.replaceEach("aba", new String[]{null}, new String[]{"a"}));
        assertEquals("wcte", StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"w", "t"}));
        assertEquals("dcte", StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"d", "t"}));

        try {
            StringUtils.replaceEach("aba", new String[]{"a"}, new String[]{"z", "y"});
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            StringUtils.replaceEachRepeatedly("abcde", new String[]{"ab", "d"}, new String[]{"d", "ab"});
            fail("Should throw IllegalArgumentException or IllegalStateException");
        } catch (Exception e) {
            // Expected
        }

        assertEquals("tcte", StringUtils.replaceEachRepeatedly("abcde", new String[]{"ab", "d"}, new String[]{"d", "t"}));
    }

    @Test
    public void testReplaceChars() throws Throwable {
        assertNull(StringUtils.replaceChars(null, 'b', 'y'));
        assertEquals("", StringUtils.replaceChars("", 'b', 'y'));
        assertEquals("aycya", StringUtils.replaceChars("abcba", 'b', 'y'));
        assertEquals("abcba", StringUtils.replaceChars("abcba", 'z', 'y'));

        assertNull(StringUtils.replaceChars(null, "bc", "yz"));
        assertEquals("", StringUtils.replaceChars("", "bc", "yz"));
        assertEquals("abc", StringUtils.replaceChars("abc", null, "yz"));
        assertEquals("abc", StringUtils.replaceChars("abc", "", "yz"));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", null));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", ""));
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yz"));
        assertEquals("ayya", StringUtils.replaceChars("abcba", "bc", "y"));
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yzx"));
        assertEquals("abcba", StringUtils.replaceChars("abcba", "z", "y"));
    }

    @Test
    public void testOverlay() throws Throwable {
        assertNull(StringUtils.overlay(null, "abc", 2, 4));
        assertEquals("abc", StringUtils.overlay("", "abc", 0, 0));
        assertEquals("abef", StringUtils.overlay("abcdef", null, 2, 4));
        assertEquals("abef", StringUtils.overlay("abcdef", "", 2, 4));
        assertEquals("abef", StringUtils.overlay("abcdef", "", 4, 2));
        assertEquals("abzzzzef", StringUtils.overlay("abcdef", "zzzz", 2, 4));
        assertEquals("abzzzzef", StringUtils.overlay("abcdef", "zzzz", 4, 2));
        assertEquals("zzzzef", StringUtils.overlay("abcdef", "zzzz", -1, 4));
        assertEquals("abzzzz", StringUtils.overlay("abcdef", "zzzz", 2, 8));
        assertEquals("zzzzabcdef", StringUtils.overlay("abcdef", "zzzz", -2, -3));
        assertEquals("abcdefzzzz", StringUtils.overlay("abcdef", "zzzz", 8, 10));
    }

    @Test
    public void testChomp() throws Throwable {
        assertNull(StringUtils.chomp(null));
        assertEquals("", StringUtils.chomp(""));
        assertEquals("abc ", StringUtils.chomp("abc \r"));
        assertEquals("abc", StringUtils.chomp("abc\n"));
        assertEquals("abc", StringUtils.chomp("abc\r\n"));
        assertEquals("abc\r\n", StringUtils.chomp("abc\r\n\r\n"));
        assertEquals("abc\n", StringUtils.chomp("abc\n\r"));
        assertEquals("abc\n\rabc", StringUtils.chomp("abc\n\rabc"));
        assertEquals("", StringUtils.chomp("\r"));
        assertEquals("", StringUtils.chomp("\n"));
        assertEquals("", StringUtils.chomp("\r\n"));
        assertEquals("a", StringUtils.chomp("a"));

        assertNull(StringUtils.chomp(null, "bar"));
        assertEquals("", StringUtils.chomp("", "bar"));
        assertEquals("foo", StringUtils.chomp("foobar", "bar"));
        assertEquals("foobar", StringUtils.chomp("foobar", "baz"));
        assertEquals("", StringUtils.chomp("foo", "foo"));
        assertEquals("foo ", StringUtils.chomp("foo ", "foo"));
        assertEquals(" ", StringUtils.chomp(" foo", "foo"));
        assertEquals("foo", StringUtils.chomp("foo", "foooo"));
        assertEquals("foo", StringUtils.chomp("foo", ""));
        assertEquals("foo", StringUtils.chomp("foo", null));
    }

    @Test
    public void testChop() throws Throwable {
        assertNull(StringUtils.chop(null));
        assertEquals("", StringUtils.chop(""));
        assertEquals("", StringUtils.chop("a"));
        assertEquals("abc ", StringUtils.chop("abc \r"));
        assertEquals("abc", StringUtils.chop("abc\n"));
        assertEquals("abc", StringUtils.chop("abc\r\n"));
        assertEquals("ab", StringUtils.chop("abc"));
        assertEquals("abc\nab", StringUtils.chop("abc\nabc"));
        assertEquals("", StringUtils.chop("\r"));
        assertEquals("", StringUtils.chop("\n"));
        assertEquals("", StringUtils.chop("\r\n"));
    }

    @Test
    public void testRepeat() throws Throwable {
        assertNull(StringUtils.repeat(null, 2));
        assertEquals("", StringUtils.repeat("", 0));
        assertEquals("", StringUtils.repeat("", 2));
        assertEquals("aaa", StringUtils.repeat("a", 3));
        assertEquals("abab", StringUtils.repeat("ab", 2));
        assertEquals("", StringUtils.repeat("a", -2));
        assertEquals("a", StringUtils.repeat("a", 1));
        assertEquals("ababab", StringUtils.repeat("ab", 3));

        assertNull(StringUtils.repeat(null, null, 2));
        assertNull(StringUtils.repeat(null, "x", 2));
        assertEquals("", StringUtils.repeat("", null, 0));
        assertEquals("", StringUtils.repeat("", "", 2));
        assertEquals("xxx", StringUtils.repeat("", "x", 3));
        assertEquals("?, ?, ?", StringUtils.repeat("?", ", ", 3));
        assertEquals("ab", StringUtils.repeat("ab", null, 1));
    }

    @Test
    public void testPadding() throws Throwable {
        assertEquals("", StringUtils.rightPad(null, 3));
        assertNull(StringUtils.rightPad(null, 3, 'z'));
        assertNull(StringUtils.rightPad(null, 3, "z"));

        assertEquals("zzz", StringUtils.rightPad("", 3, 'z'));
        assertEquals("bat", StringUtils.rightPad("bat", 3, 'z'));
        assertEquals("batzz", StringUtils.rightPad("bat", 5, 'z'));
        assertEquals("bat", StringUtils.rightPad("bat", 1, 'z'));
        assertEquals("bat", StringUtils.rightPad("bat", -1, 'z'));
        assertEquals("batzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", StringUtils.rightPad("bat", 9000, 'z'));

        assertEquals("zzz", StringUtils.rightPad("", 3, "z"));
        assertEquals("bat", StringUtils.rightPad("bat", 3, "yz"));
        assertEquals("batyz", StringUtils.rightPad("bat", 5, "yz"));
        assertEquals("batyzyzy", StringUtils.rightPad("bat", 8, "yz"));
        assertEquals("bat", StringUtils.rightPad("bat", 1, "yz"));
        assertEquals("bat", StringUtils.rightPad("bat", -1, "yz"));
        assertEquals("bat  ", StringUtils.rightPad("bat", 5, null));
        assertEquals("bat  ", StringUtils.rightPad("bat", 5, ""));
        assertEquals("batyz", StringUtils.rightPad("bat", 5, "yz"));
        assertEquals("baty", StringUtils.rightPad("bat", 4, "yz"));

        assertEquals("", StringUtils.leftPad(null, 3));
        assertNull(StringUtils.leftPad(null, 3, 'z'));
        assertNull(StringUtils.leftPad(null, 3, "z"));

        assertEquals("zzz", StringUtils.leftPad("", 3, 'z'));
        assertEquals("bat", StringUtils.leftPad("bat", 3, 'z'));
        assertEquals("zzbat", StringUtils.leftPad("bat", 5, 'z'));
        assertEquals("bat", StringUtils.leftPad("bat", 1, 'z'));
        assertEquals("bat", StringUtils.leftPad("bat", -1, 'z'));
        assertEquals("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzbat", StringUtils.leftPad("bat", 9000, 'z'));

        assertEquals("zzz", StringUtils.leftPad("", 3, "z"));
        assertEquals("bat", StringUtils.leftPad("bat", 3, "yz"));
        assertEquals("yzbat", StringUtils.leftPad("bat", 5, "yz"));
        assertEquals("yzyzybat", StringUtils.leftPad("bat", 8, "yz"));
        assertEquals("bat", StringUtils.leftPad("bat", 1, "yz"));
        assertEquals("bat", StringUtils.leftPad("bat", -1, "yz"));
        assertEquals("  bat", StringUtils.leftPad("bat", 5, null));
        assertEquals("  bat", StringUtils.leftPad("bat", 5, ""));
        assertEquals("yzbat", StringUtils.leftPad("bat", 5, "yz"));
        assertEquals("ybat", StringUtils.leftPad("bat", 4, "yz"));
    }

    @Test
    public void testLength() throws Throwable {
        assertEquals(0, StringUtils.length(null));
        assertEquals(3, StringUtils.length("abc"));
    }

    @Test
    public void testCenter() throws Throwable {
        assertNull(StringUtils.center(null, 4));
        assertNull(StringUtils.center(null, 4, ' '));
        assertNull(StringUtils.center(null, 4, " "));

        assertEquals("    ", StringUtils.center("", 4));
        assertEquals("ab", StringUtils.center("ab", -1));
        assertEquals(" ab ", StringUtils.center("ab", 4));
        assertEquals("abcd", StringUtils.center("abcd", 2));
        assertEquals(" a  ", StringUtils.center("a", 4));

        assertEquals("    ", StringUtils.center("", 4, ' '));
        assertEquals("ab", StringUtils.center("ab", -1, ' '));
        assertEquals(" ab", StringUtils.center("ab", 4, ' '));
        assertEquals("abcd", StringUtils.center("abcd", 2, ' '));
        assertEquals(" a  ", StringUtils.center("a", 4, ' '));
        assertEquals("yayy", StringUtils.center("a", 4, 'y'));
        assertEquals("ab", StringUtils.center("ab", 1, ' '));

        assertEquals("    ", StringUtils.center("", 4, " "));
        assertEquals("ab", StringUtils.center("ab", -1, " "));
        assertEquals(" ab", StringUtils.center("ab", 4, " "));
        assertEquals("abcd", StringUtils.center("abcd", 2, " "));
        assertEquals(" a  ", StringUtils.center("a", 4, " "));
        assertEquals("yayz", StringUtils.center("a", 4, "yz"));
        assertEquals("  abc  ", StringUtils.center("abc", 7, null));
        assertEquals("  abc  ", StringUtils.center("abc", 7, ""));
        assertEquals("ab", StringUtils.center("ab", 1, " "));
    }

    @Test
    public void testCaseConversion() throws Throwable {
        assertNull(StringUtils.upperCase(null));
        assertEquals("", StringUtils.upperCase(""));
        assertEquals("ABC", StringUtils.upperCase("aBc"));

        assertNull(StringUtils.upperCase(null, Locale.ENGLISH));
        assertEquals("", StringUtils.upperCase("", Locale.ENGLISH));
        assertEquals("ABC", StringUtils.upperCase("aBc", Locale.ENGLISH));

        assertNull(StringUtils.lowerCase(null));
        assertEquals("", StringUtils.lowerCase(""));
        assertEquals("abc", StringUtils.lowerCase("aBc"));

        assertNull(StringUtils.lowerCase(null, Locale.ENGLISH));
        assertEquals("", StringUtils.lowerCase("", Locale.ENGLISH));
        assertEquals("abc", StringUtils.lowerCase("aBc", Locale.ENGLISH));

        assertNull(StringUtils.capitalize(null));
        assertEquals("", StringUtils.capitalize(""));
        assertEquals("Cat", StringUtils.capitalize("cat"));
        assertEquals("CAt", StringUtils.capitalize("cAt"));

        assertNull(StringUtils.uncapitalize(null));
        assertEquals("", StringUtils.uncapitalize(""));
        assertEquals("cat", StringUtils.uncapitalize("Cat"));
        assertEquals("cAT", StringUtils.uncapitalize("CAT"));

        assertNull(StringUtils.swapCase(null));
        assertEquals("", StringUtils.swapCase(""));
        assertEquals("tHE DOG HAS A bone", StringUtils.swapCase("The dog has a BONE"));
    }

    @Test
    public void testCountMatches() throws Throwable {
        assertEquals(0, StringUtils.countMatches(null, "a"));
        assertEquals(0, StringUtils.countMatches("", "a"));
        assertEquals(0, StringUtils.countMatches("abba", null));
        assertEquals(0, StringUtils.countMatches("abba", ""));
        assertEquals(2, StringUtils.countMatches("abba", "a"));
        assertEquals(1, StringUtils.countMatches("abba", "ab"));
        assertEquals(0, StringUtils.countMatches("abba", "xxx"));
    }

    @Test
    public void testCharacterTests() throws Throwable {
        assertFalse(StringUtils.isAlpha(null));
        assertTrue(StringUtils.isAlpha(""));
        assertFalse(StringUtils.isAlpha("  "));
        assertTrue(StringUtils.isAlpha("abc"));
        assertFalse(StringUtils.isAlpha("ab2c"));
        assertFalse(StringUtils.isAlpha("ab-c"));

        assertFalse(StringUtils.isAlphaSpace(null));
        assertTrue(StringUtils.isAlphaSpace(""));
        assertTrue(StringUtils.isAlphaSpace("  "));
        assertTrue(StringUtils.isAlphaSpace("abc"));
        assertTrue(StringUtils.isAlphaSpace("ab c"));
        assertFalse(StringUtils.isAlphaSpace("ab2c"));
        assertFalse(StringUtils.isAlphaSpace("ab-c"));

        assertFalse(StringUtils.isAlphanumeric(null));
        assertTrue(StringUtils.isAlphanumeric(""));
        assertFalse(StringUtils.isAlphanumeric("  "));
        assertTrue(StringUtils.isAlphanumeric("abc"));
        assertFalse(StringUtils.isAlphanumeric("ab c"));
        assertTrue(StringUtils.isAlphanumeric("ab2c"));
        assertFalse(StringUtils.isAlphanumeric("ab-c"));

        assertFalse(StringUtils.isAlphanumericSpace(null));
        assertTrue(StringUtils.isAlphanumericSpace(""));
        assertTrue(StringUtils.isAlphanumericSpace("  "));
        assertTrue(StringUtils.isAlphanumericSpace("abc"));
        assertTrue(StringUtils.isAlphanumericSpace("ab c"));
        assertTrue(StringUtils.isAlphanumericSpace("ab2c"));
        assertFalse(StringUtils.isAlphanumericSpace("ab-c"));

        assertFalse(StringUtils.isAsciiPrintable(null));
        assertTrue(StringUtils.isAsciiPrintable(""));
        assertTrue(StringUtils.isAsciiPrintable(" "));
        assertTrue(StringUtils.isAsciiPrintable("Ceki"));
        assertTrue(StringUtils.isAsciiPrintable("!ab-c~"));
        assertFalse(StringUtils.isAsciiPrintable("\u007f"));

        assertFalse(StringUtils.isNumeric(null));
        assertTrue(StringUtils.isNumeric(""));
        assertFalse(StringUtils.isNumeric("  "));
        assertTrue(StringUtils.isNumeric("123"));
        assertFalse(StringUtils.isNumeric("12 3"));
        assertFalse(StringUtils.isNumeric("ab2c"));
        assertFalse(StringUtils.isNumeric("12-3"));
        assertFalse(StringUtils.isNumeric("12.3"));

        assertFalse(StringUtils.isNumericSpace(null));
        assertTrue(StringUtils.isNumericSpace(""));
        assertTrue(StringUtils.isNumericSpace("  "));
        assertTrue(StringUtils.isNumericSpace("123"));
        assertTrue(StringUtils.isNumericSpace("12 3"));
        assertFalse(StringUtils.isNumericSpace("ab2c"));

        assertFalse(StringUtils.isWhitespace(null));
        assertTrue(StringUtils.isWhitespace(""));
        assertTrue(StringUtils.isWhitespace("  "));
        assertFalse(StringUtils.isWhitespace("abc"));

        assertFalse(StringUtils.isAllLowerCase(null));
        assertFalse(StringUtils.isAllLowerCase(""));
        assertFalse(StringUtils.isAllLowerCase("  "));
        assertTrue(StringUtils.isAllLowerCase("abc"));
        assertFalse(StringUtils.isAllLowerCase("abC"));

        assertFalse(StringUtils.isAllUpperCase(null));
        assertFalse(StringUtils.isAllUpperCase(""));
        assertFalse(StringUtils.isAllUpperCase("  "));
        assertTrue(StringUtils.isAllUpperCase("ABC"));
        assertFalse(StringUtils.isAllUpperCase("aBC"));
    }

    @Test
    public void testDefaults() throws Throwable {
        assertEquals("", StringUtils.defaultString(null));
        assertEquals("", StringUtils.defaultString(""));
        assertEquals("bat", StringUtils.defaultString("bat"));

        assertEquals("NULL", StringUtils.defaultString(null, "NULL"));
        assertEquals("", StringUtils.defaultString("", "NULL"));
        assertEquals("bat", StringUtils.defaultString("bat", "NULL"));

        assertEquals("NULL", StringUtils.defaultIfEmpty(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfEmpty("", "NULL"));
        assertEquals("bat", StringUtils.defaultIfEmpty("bat", "NULL"));
        assertNull(StringUtils.defaultIfEmpty("", null));
    }

    @Test
    public void testReversing() throws Throwable {
        assertNull(StringUtils.reverse(null));
        assertEquals("", StringUtils.reverse(""));
        assertEquals("tab", StringUtils.reverse("bat"));

        assertNull(StringUtils.reverseDelimited(null, '.'));
        assertEquals("", StringUtils.reverseDelimited("", '.'));
        assertEquals("a.b.c", StringUtils.reverseDelimited("a.b.c", 'x'));
        assertEquals("c.b.a", StringUtils.reverseDelimited("a.b.c", '.'));
    }

    @Test
    public void testAbbreviate() throws Throwable {
        assertNull(StringUtils.abbreviate(null, 6));
        assertEquals("", StringUtils.abbreviate("", 4));
        assertEquals("abc...", StringUtils.abbreviate("abcdefg", 6));
        assertEquals("abcdefg", StringUtils.abbreviate("abcdefg", 7));
        assertEquals("abcdefg", StringUtils.abbreviate("abcdefg", 8));
        assertEquals("a...", StringUtils.abbreviate("abcdefg", 4));

        try {
            StringUtils.abbreviate("abcdefg", 3);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        assertNull(StringUtils.abbreviate(null, 0, 6));
        assertEquals("", StringUtils.abbreviate("", 0, 4));
        assertEquals("abcdefg...", StringUtils.abbreviate("abcdefghijklmno", -1, 10));
        assertEquals("abcdefg...", StringUtils.abbreviate("abcdefghijklmno", 0, 10));
        assertEquals("abcdefg...", StringUtils.abbreviate("abcdefghijklmno", 1, 10));
        assertEquals("abcdefg...", StringUtils.abbreviate("abcdefghijklmno", 4, 10));
        assertEquals("...fghi...", StringUtils.abbreviate("abcdefghijklmno", 5, 10));
        assertEquals("...ghij...", StringUtils.abbreviate("abcdefghijklmno", 6, 10));
        assertEquals("...ijklmno", StringUtils.abbreviate("abcdefghijklmno", 8, 10));
        assertEquals("...ijklmno", StringUtils.abbreviate("abcdefghijklmno", 10, 10));
        assertEquals("...ijklmno", StringUtils.abbreviate("abcdefghijklmno", 12, 10));
        assertEquals("...ijklmno", StringUtils.abbreviate("abcdefghijklmno", 20, 10));

        try {
            StringUtils.abbreviate("abcdefghij", 0, 3);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            StringUtils.abbreviate("abcdefghij", 5, 6);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testDifference() throws Throwable {
        assertNull(StringUtils.difference(null, null));
        assertEquals("", StringUtils.difference("", ""));
        assertEquals("abc", StringUtils.difference("", "abc"));
        assertEquals("", StringUtils.difference("abc", ""));
        assertEquals("", StringUtils.difference("abc", "abc"));
        assertEquals("xyz", StringUtils.difference("ab", "abxyz"));
        assertEquals("xyz", StringUtils.difference("abcde", "abxyz"));
        assertEquals("xyz", StringUtils.difference("abcde", "xyz"));

        assertEquals(-1, StringUtils.indexOfDifference(null, null));
        assertEquals(-1, StringUtils.indexOfDifference("", ""));
        assertEquals(0, StringUtils.indexOfDifference("", "abc"));
        assertEquals(0, StringUtils.indexOfDifference("abc", ""));
        assertEquals(-1, StringUtils.indexOfDifference("abc", "abc"));
        assertEquals(2, StringUtils.indexOfDifference("ab", "abxyz"));
        assertEquals(2, StringUtils.indexOfDifference("abcde", "abxyz"));
        assertEquals(0, StringUtils.indexOfDifference("abcde", "xyz"));

        assertEquals(-1, StringUtils.indexOfDifference(null));
        assertEquals(-1, StringUtils.indexOfDifference(new String[0]));
        assertEquals(-1, StringUtils.indexOfDifference(new String[]{"abc"}));
        assertEquals(-1, StringUtils.indexOfDifference(new String[]{null, null}));
        assertEquals(-1, StringUtils.indexOfDifference(new String[]{"", ""}));
        assertEquals(0, StringUtils.indexOfDifference(new String[]{"", null}));
        assertEquals(0, StringUtils.indexOfDifference(new String[]{"abc", null, null}));
        assertEquals(0, StringUtils.indexOfDifference(new String[]{null, null, "abc"}));
        assertEquals(0, StringUtils.indexOfDifference(new String[]{"", "abc"}));
        assertEquals(0, StringUtils.indexOfDifference(new String[]{"abc", ""}));
        assertEquals(-1, StringUtils.indexOfDifference(new String[]{"abc", "abc"}));
        assertEquals(1, StringUtils.indexOfDifference(new String[]{"abc", "a"}));
        assertEquals(2, StringUtils.indexOfDifference(new String[]{"ab", "abxyz"}));
        assertEquals(2, StringUtils.indexOfDifference(new String[]{"abcde", "abxyz"}));
        assertEquals(0, StringUtils.indexOfDifference(new String[]{"abcde", "xyz"}));
        assertEquals(0, StringUtils.indexOfDifference(new String[]{"xyz", "abcde"}));
        assertEquals(7, StringUtils.indexOfDifference(new String[]{"i am a machine", "i am a robot"}));

        assertEquals("", StringUtils.getCommonPrefix(null));
        assertEquals("", StringUtils.getCommonPrefix(new String[0]));
        assertEquals("abc", StringUtils.getCommonPrefix(new String[]{"abc"}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{null, null}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"", ""}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"", null}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"abc", null, null}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{null, null, "abc"}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"", "abc"}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"abc", ""}));
        assertEquals("abc", StringUtils.getCommonPrefix(new String[]{"abc", "abc"}));
        assertEquals("a", StringUtils.getCommonPrefix(new String[]{"abc", "a"}));
        assertEquals("ab", StringUtils.getCommonPrefix(new String[]{"ab", "abxyz"}));
        assertEquals("ab", StringUtils.getCommonPrefix(new String[]{"abcde", "abxyz"}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"abcde", "xyz"}));
        assertEquals("", StringUtils.getCommonPrefix(new String[]{"xyz", "abcde"}));
        assertEquals("i am a ", StringUtils.getCommonPrefix(new String[]{"i am a machine", "i am a robot"}));
    }

    @Test
    public void testLevenshteinDistance() throws Throwable {
        try {
            StringUtils.getLevenshteinDistance(null, "a");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            StringUtils.getLevenshteinDistance("a", null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        assertEquals(0, StringUtils.getLevenshteinDistance("", ""));
        assertEquals(1, StringUtils.getLevenshteinDistance("", "a"));
        assertEquals(7, StringUtils.getLevenshteinDistance("aaapppp", ""));
        assertEquals(1, StringUtils.getLevenshteinDistance("frog", "fog"));
        assertEquals(3, StringUtils.getLevenshteinDistance("fly", "ant"));
        assertEquals(7, StringUtils.getLevenshteinDistance("elephant", "hippo"));
        assertEquals(7, StringUtils.getLevenshteinDistance("hippo", "elephant"));
        assertEquals(8, StringUtils.getLevenshteinDistance("hippo", "zzzzzzzz"));
        assertEquals(1, StringUtils.getLevenshteinDistance("hello", "hallo"));
        assertEquals(3, StringUtils.getLevenshteinDistance("a", "abcdef"));
    }

    @Test
    public void testStartsWithAndEndsWith() throws Throwable {
        assertTrue(StringUtils.startsWith(null, null));
        assertFalse(StringUtils.startsWith(null, "abc"));
        assertFalse(StringUtils.startsWith("abcdef", null));
        assertTrue(StringUtils.startsWith("abcdef", "abc"));
        assertFalse(StringUtils.startsWith("ABCDEF", "abc"));
        assertFalse(StringUtils.startsWith("abc", "abcdef"));

        assertTrue(StringUtils.startsWithIgnoreCase(null, null));
        assertFalse(StringUtils.startsWithIgnoreCase(null, "abc"));
        assertFalse(StringUtils.startsWithIgnoreCase("abcdef", null));
        assertTrue(StringUtils.startsWithIgnoreCase("abcdef", "abc"));
        assertTrue(StringUtils.startsWithIgnoreCase("ABCDEF", "abc"));
        assertFalse(StringUtils.startsWithIgnoreCase("abc", "abcdef"));

        assertFalse(StringUtils.startsWithAny(null, null));
        assertFalse(StringUtils.startsWithAny(null, new String[]{"abc"}));
        assertFalse(StringUtils.startsWithAny("abcxyz", null));
        assertFalse(StringUtils.startsWithAny("abcxyz", new String[]{""}));
        assertTrue(StringUtils.startsWithAny("abcxyz", new String[]{"abc"}));
        assertTrue(StringUtils.startsWithAny("abcxyz", new String[]{null, "xyz", "abc"}));

        assertTrue(StringUtils.endsWith(null, null));
        assertFalse(StringUtils.endsWith(null, "def"));
        assertFalse(StringUtils.endsWith("abcdef", null));
        assertTrue(StringUtils.endsWith("abcdef", "def"));
        assertFalse(StringUtils.endsWith("ABCDEF", "def"));
        assertFalse(StringUtils.endsWith("ABCDEF", "cde"));
        assertFalse(StringUtils.endsWith("def", "abcdef"));

        assertTrue(StringUtils.endsWithIgnoreCase(null, null));
        assertFalse(StringUtils.endsWithIgnoreCase(null, "def"));
        assertFalse(StringUtils.endsWithIgnoreCase("abcdef", null));
        assertTrue(StringUtils.endsWithIgnoreCase("abcdef", "def"));
        assertTrue(StringUtils.endsWithIgnoreCase("ABCDEF", "def"));
        assertFalse(StringUtils.endsWithIgnoreCase("ABCDEF", "cde"));
        assertFalse(StringUtils.endsWithIgnoreCase("def", "abcdef"));
    }
}