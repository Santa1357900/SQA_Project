package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
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
    }

    @Test
    public void testStripToEmpty() throws Throwable {
        assertEquals("", StringUtils.stripToEmpty(null));
        assertEquals("", StringUtils.stripToEmpty(""));
        assertEquals("", StringUtils.stripToEmpty("   "));
        assertEquals("abc", StringUtils.stripToEmpty("abc"));
        assertEquals("abc", StringUtils.stripToEmpty("  abc"));
    }

    @Test
    public void testStripStart() throws Throwable {
        assertNull(StringUtils.stripStart(null, ""));
        assertEquals("", StringUtils.stripStart("", ""));
        assertEquals("abc", StringUtils.stripStart("abc", ""));
        assertEquals("abc", StringUtils.stripStart("abc", null));
        assertEquals("abc", StringUtils.stripStart("  abc", null));
        assertEquals("abc  ", StringUtils.stripStart("abc  ", null));
        assertEquals("abc ", StringUtils.stripStart(" abc ", null));
        assertEquals("abc  ", StringUtils.stripStart("yxabc  ", "xyz"));
    }

    @Test
    public void testStripEnd() throws Throwable {
        assertNull(StringUtils.stripEnd(null, ""));
        assertEquals("", StringUtils.stripEnd("", ""));
        assertEquals("abc", StringUtils.stripEnd("abc", ""));
        assertEquals("abc", StringUtils.stripEnd("abc", null));
        assertEquals("  abc", StringUtils.stripEnd("  abc", null));
        assertEquals("abc", StringUtils.stripEnd("abc  ", null));
        assertEquals(" abc", StringUtils.stripEnd(" abc ", null));
        assertEquals("  abc", StringUtils.stripEnd("  abcyx", "xyz"));
        assertEquals("12", StringUtils.stripEnd("120.00", ".0"));
    }

    @Test
    public void testStripAll() throws Throwable {
        assertNull(StringUtils.stripAll((String[]) null));
        assertArrayEquals(new String[0], StringUtils.stripAll(new String[0]));
        assertArrayEquals(new String[]{"abc", "abc"}, StringUtils.stripAll(new String[]{"abc", "  abc"}));
        assertArrayEquals(new String[]{"abc", null}, StringUtils.stripAll(new String[]{"abc  ", null}, null));
        assertArrayEquals(new String[]{"abc  ", null}, StringUtils.stripAll(new String[]{"abc  ", null}, "yz"));
        assertArrayEquals(new String[]{"abc", null}, StringUtils.stripAll(new String[]{"yabcz", null}, "yz"));
    }

    @Test
    public void testStripAccents() throws Throwable {
        assertNull(StringUtils.stripAccents(null));
        assertEquals("", StringUtils.stripAccents(""));
        assertEquals("control", StringUtils.stripAccents("control"));
        assertEquals("eclair", StringUtils.stripAccents("\u00e9clair"));
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
        assertEquals(-1, StringUtils.indexOf("a", null));
        assertEquals(0, StringUtils.indexOf("", ""));
        assertEquals(-1, StringUtils.indexOf("", "a"));
        assertEquals(0, StringUtils.indexOf("aabaabaa", "a"));
        assertEquals(2, StringUtils.indexOf("aabaabaa", "b"));
        assertEquals(1, StringUtils.indexOf("aabaabaa", "ab"));
        assertEquals(0, StringUtils.indexOf("aabaabaa", ""));

        assertEquals(-1, StringUtils.indexOf(null, "a", 0));
        assertEquals(-1, StringUtils.indexOf("a", null, 0));
        assertEquals(0, StringUtils.indexOf("", "", 0));
        assertEquals(-1, StringUtils.indexOf("", "a", 0));
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
        assertEquals(-1, StringUtils.ordinalIndexOf("a", null, 1));
        assertEquals(-1, StringUtils.ordinalIndexOf("a", "a", 0));
        assertEquals(0, StringUtils.ordinalIndexOf("", "", 1));
        assertEquals(0, StringUtils.ordinalIndexOf("aabaabaa", "a", 1));
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "a", 2));
        assertEquals(2, StringUtils.ordinalIndexOf("aabaabaa", "b", 1));
        assertEquals(5, StringUtils.ordinalIndexOf("aabaabaa", "b", 2));
        assertEquals(1, StringUtils.ordinalIndexOf("aabaabaa", "ab", 1));
        assertEquals(4, StringUtils.ordinalIndexOf("aabaabaa", "ab", 2));
        assertEquals(0, StringUtils.ordinalIndexOf("aabaabaa", "", 1));
        assertEquals(-1, StringUtils.ordinalIndexOf("aabaabaa", "notfound", 1));
    }

    @Test
    public void testIndexOfIgnoreCase() throws Throwable {
        assertEquals(-1, StringUtils.indexOfIgnoreCase(null, "a"));
        assertEquals(-1, StringUtils.indexOfIgnoreCase("a", null));
        assertEquals(0, StringUtils.indexOfIgnoreCase("", ""));
        assertEquals(0, StringUtils.indexOfIgnoreCase("aabaabaa", "a"));
        assertEquals(2, StringUtils.indexOfIgnoreCase("aabaabaa", "b"));
        assertEquals(1, StringUtils.indexOfIgnoreCase("aabaabaa", "ab"));

        assertEquals(-1, StringUtils.indexOfIgnoreCase(null, "a", 0));
        assertEquals(-1, StringUtils.indexOfIgnoreCase("a", null, 0));
        assertEquals(0, StringUtils.indexOfIgnoreCase("", "", 0));
        assertEquals(0, StringUtils.indexOfIgnoreCase("aabaabaa", "A", 0));
        assertEquals(2, StringUtils.indexOfIgnoreCase("aabaabaa", "B", 0));
        assertEquals(1, StringUtils.indexOfIgnoreCase("aabaabaa", "AB", 0));
        assertEquals(5, StringUtils.indexOfIgnoreCase("aabaabaa", "B", 3));
        assertEquals(-1, StringUtils.indexOfIgnoreCase("aabaabaa", "B", 9));
        assertEquals(2, StringUtils.indexOfIgnoreCase("aabaabaa", "B", -1));
        assertEquals(2, StringUtils.indexOfIgnoreCase("aabaabaa", "", 2));
        assertEquals(3, StringUtils.indexOfIgnoreCase("abc", "", 9));
        assertEquals(-1, StringUtils.indexOfIgnoreCase("abc", "abc", 5));
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
        assertEquals(-1, StringUtils.lastIndexOf("a", null));
        assertEquals(0, StringUtils.lastIndexOf("", ""));
        assertEquals(7, StringUtils.lastIndexOf("aabaabaa", "a"));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", "b"));
        assertEquals(4, StringUtils.lastIndexOf("aabaabaa", "ab"));
        assertEquals(8, StringUtils.lastIndexOf("aabaabaa", ""));

        assertEquals(-1, StringUtils.lastIndexOf(null, "a", 8));
        assertEquals(-1, StringUtils.lastIndexOf("a", null, 8));
        assertEquals(7, StringUtils.lastIndexOf("aabaabaa", "a", 8));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", "b", 8));
        assertEquals(4, StringUtils.lastIndexOf("aabaabaa", "ab", 8));
        assertEquals(5, StringUtils.lastIndexOf("aabaabaa", "b", 9));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", "b", -1));
        assertEquals(0, StringUtils.lastIndexOf("aabaabaa", "a", 0));
        assertEquals(-1, StringUtils.lastIndexOf("aabaabaa", "b", 0));
    }

    @Test
    public void testLastOrdinalIndexOf() throws Throwable {
        assertEquals(-1, StringUtils.lastOrdinalIndexOf(null, "a", 1));
        assertEquals(-1, StringUtils.lastOrdinalIndexOf("a", null, 1));
        assertEquals(0, StringUtils.lastOrdinalIndexOf("", "", 1));
        assertEquals(7, StringUtils.lastOrdinalIndexOf("aabaabaa", "a", 1));
        assertEquals(6, StringUtils.lastOrdinalIndexOf("aabaabaa", "a", 2));
        assertEquals(5, StringUtils.lastOrdinalIndexOf("aabaabaa", "b", 1));
        assertEquals(2, StringUtils.lastOrdinalIndexOf("aabaabaa", "b", 2));
        assertEquals(4, StringUtils.lastOrdinalIndexOf("aabaabaa", "ab", 1));
        assertEquals(1, StringUtils.lastOrdinalIndexOf("aabaabaa", "ab", 2));
        assertEquals(8, StringUtils.lastOrdinalIndexOf("aabaabaa", "", 1));
        assertEquals(-1, StringUtils.lastOrdinalIndexOf("aabaabaa", "notfound", 1));
    }

    @Test
    public void testLastIndexOfIgnoreCase() throws Throwable {
        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase(null, "a"));
        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase("a", null));
        assertEquals(7, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "A"));
        assertEquals(5, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B"));
        assertEquals(4, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "AB"));

        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase(null, "a", 8));
        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase("a", null, 8));
        assertEquals(7, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "A", 8));
        assertEquals(5, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", 8));
        assertEquals(4, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "AB", 8));
        assertEquals(5, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", 9));
        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", -1));
        assertEquals(0, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "A", 0));
        assertEquals(-1, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", 0));
        assertEquals(8, StringUtils.lastIndexOfIgnoreCase("aabaabaa", "", 10));
    }

    @Test
    public void testContains() throws Throwable {
        assertFalse(StringUtils.contains(null, 'a'));
        assertFalse(StringUtils.contains("", 'a'));
        assertTrue(StringUtils.contains("abc", 'a'));
        assertFalse(StringUtils.contains("abc", 'z'));

        assertFalse(StringUtils.contains(null, "a"));
        assertFalse(StringUtils.contains("a", null));
        assertTrue(StringUtils.contains("", ""));
        assertTrue(StringUtils.contains("abc", ""));
        assertTrue(StringUtils.contains("abc", "a"));
        assertFalse(StringUtils.contains("abc", "z"));
    }

    @Test
    public void testContainsIgnoreCase() throws Throwable {
        assertFalse(StringUtils.containsIgnoreCase(null, "a"));
        assertFalse(StringUtils.containsIgnoreCase("a", null));
        assertTrue(StringUtils.containsIgnoreCase("", ""));
        assertTrue(StringUtils.containsIgnoreCase("abc", ""));
        assertTrue(StringUtils.containsIgnoreCase("abc", "a"));
        assertFalse(StringUtils.containsIgnoreCase("abc", "z"));
        assertTrue(StringUtils.containsIgnoreCase("abc", "A"));
        assertFalse(StringUtils.containsIgnoreCase("abc", "Z"));
    }

    @Test
    public void testContainsWhitespace() throws Throwable {
        assertFalse(StringUtils.containsWhitespace(null));
        assertFalse(StringUtils.containsWhitespace(""));
        assertTrue(StringUtils.containsWhitespace("a b"));
        assertTrue(StringUtils.containsWhitespace(" "));
        assertFalse(StringUtils.containsWhitespace("abc"));
    }

    @Test
    public void testIndexOfAny() throws Throwable {
        assertEquals(-1, StringUtils.indexOfAny(null, 'z', 'a'));
        assertEquals(-1, StringUtils.indexOfAny("", 'z', 'a'));
        assertEquals(-1, StringUtils.indexOfAny("abc", (char[]) null));
        assertEquals(-1, StringUtils.indexOfAny("abc", new char[0]));
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", 'z', 'a'));
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", 'b', 'y'));
        assertEquals(-1, StringUtils.indexOfAny("aba", 'z'));
        
        // High surrogate test case
        String highSurr = "\ud800\udc00";
        assertEquals(0, StringUtils.indexOfAny(highSurr, highSurr.toCharArray()));

        assertEquals(-1, StringUtils.indexOfAny(null, "za"));
        assertEquals(-1, StringUtils.indexOfAny("", "za"));
        assertEquals(-1, StringUtils.indexOfAny("abc", null));
        assertEquals(-1, StringUtils.indexOfAny("abc", ""));
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", "za"));
        assertEquals(3, StringUtils.indexOfAny("zzabyycdxx", "by"));
        assertEquals(-1, StringUtils.indexOfAny("aba", "z"));

        assertEquals(-1, StringUtils.indexOfAny(null, (CharSequence[]) null));
        assertEquals(-1, StringUtils.indexOfAny("abc", (CharSequence[]) null));
        assertEquals(-1, StringUtils.indexOfAny(null, "abc"));
        assertEquals(-1, StringUtils.indexOfAny("abc", new CharSequence[0]));
        assertEquals(2, StringUtils.indexOfAny("zzabyycdxx", "ab", "cd"));
        assertEquals(2, StringUtils.indexOfAny("zzabyycdxx", "cd", "ab"));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", "mn", "op"));
        assertEquals(1, StringUtils.indexOfAny("zzabyycdxx", "zab", "aby"));
        assertEquals(0, StringUtils.indexOfAny("zzabyycdxx", ""));
        assertEquals(0, StringUtils.indexOfAny("", ""));
        assertEquals(-1, StringUtils.indexOfAny("", "a"));
        assertEquals(-1, StringUtils.indexOfAny("zzabyycdxx", null, "ab"));
    }

    @Test
    public void testContainsAny() throws Throwable {
        assertFalse(StringUtils.containsAny(null, 'z', 'a'));
        assertFalse(StringUtils.containsAny("", 'z', 'a'));
        assertFalse(StringUtils.containsAny("abc", (char[]) null));
        assertFalse(StringUtils.containsAny("abc", new char[0]));
        assertTrue(StringUtils.containsAny("zzabyycdxx", 'z', 'a'));
        assertTrue(StringUtils.containsAny("zzabyycdxx", 'b', 'y'));
        assertFalse(StringUtils.containsAny("aba", 'z'));

        String highSurr = "\ud800\udc00";
        assertTrue(StringUtils.containsAny(highSurr, highSurr.toCharArray()));
        assertTrue(StringUtils.containsAny("\ud800", "\ud800"));

        assertFalse(StringUtils.containsAny(null, "za"));
        assertFalse(StringUtils.containsAny("", "za"));
        assertFalse(StringUtils.containsAny("abc", (String) null));
        assertTrue(StringUtils.containsAny("zzabyycdxx", "za"));
        assertTrue(StringUtils.containsAny("zzabyycdxx", "by"));
        assertFalse(StringUtils.containsAny("aba", "z"));
    }

    @Test
    public void testIndexOfAnyBut() throws Throwable {
        assertEquals(-1, StringUtils.indexOfAnyBut(null, 'z', 'a'));
        assertEquals(-1, StringUtils.indexOfAnyBut("", 'z', 'a'));
        assertEquals(-1, StringUtils.indexOfAnyBut("abc", (char[]) null));
        assertEquals(-1, StringUtils.indexOfAnyBut("abc", new char[0]));
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", new char[] {'z', 'a'}));
        assertEquals(0, StringUtils.indexOfAnyBut("aba", new char[] {'z'}));
        assertEquals(-1, StringUtils.indexOfAnyBut("aba", new char[] {'a', 'b'}));

        String highSurr = "\ud800\udc00";
        assertEquals(-1, StringUtils.indexOfAnyBut(highSurr, highSurr.toCharArray()));

        assertEquals(-1, StringUtils.indexOfAnyBut(null, "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("", "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("abc", null));
        assertEquals(-1, StringUtils.indexOfAnyBut("abc", ""));
        assertEquals(3, StringUtils.indexOfAnyBut("zzabyycdxx", "za"));
        assertEquals(-1, StringUtils.indexOfAnyBut("zzabyycdxx", ""));
        assertEquals(-1, StringUtils.indexOfAnyBut("aba", "ab"));
        assertEquals(0, StringUtils.indexOfAnyBut(highSurr, "a"));
    }

    @Test
    public void testContainsOnly() throws Throwable {
        assertFalse(StringUtils.containsOnly(null, 'z', 'a'));
        assertFalse(StringUtils.containsOnly("ab", (char[]) null));
        assertTrue(StringUtils.containsOnly("", 'z', 'a'));
        assertFalse(StringUtils.containsOnly("ab", new char[0]));
        assertTrue(StringUtils.containsOnly("abab", 'a', 'b', 'c'));
        assertFalse(StringUtils.containsOnly("ab1", 'a', 'b', 'c'));
        assertFalse(StringUtils.containsOnly("abz", 'a', 'b', 'c'));

        assertFalse(StringUtils.containsOnly(null, "za"));
        assertFalse(StringUtils.containsOnly("ab", null));
        assertTrue(StringUtils.containsOnly("", "za"));
        assertFalse(StringUtils.containsOnly("ab", ""));
        assertTrue(StringUtils.containsOnly("abab", "abc"));
        assertFalse(StringUtils.containsOnly("ab1", "abc"));
    }

    @Test
    public void testContainsNone() throws Throwable {
        assertTrue(StringUtils.containsNone(null, 'z', 'a'));
        assertTrue(StringUtils.containsNone("ab", (char[]) null));
        assertTrue(StringUtils.containsNone("", 'z', 'a'));
        assertTrue(StringUtils.containsNone("abab", 'x', 'y', 'z'));
        assertTrue(StringUtils.containsNone("ab1", 'x', 'y', 'z'));
        assertFalse(StringUtils.containsNone("abz", 'x', 'y', 'z'));

        String highSurr = "\ud800\udc00";
        assertFalse(StringUtils.containsNone(highSurr, highSurr.toCharArray()));
        assertTrue(StringUtils.containsNone(highSurr, "b"));

        assertTrue(StringUtils.containsNone(null, "za"));
        assertTrue(StringUtils.containsNone("ab", null));
        assertTrue(StringUtils.containsNone("", "za"));
        assertTrue(StringUtils.containsNone("ab", ""));
        assertTrue(StringUtils.containsNone("abab", "xyz"));
        assertFalse(StringUtils.containsNone("abz", "xyz"));
    }

    @Test
    public void testLastIndexOfAny() throws Throwable {
        assertEquals(-1, StringUtils.lastIndexOfAny(null, "ab"));
        assertEquals(-1, StringUtils.lastIndexOfAny("abc", (CharSequence[]) null));
        assertEquals(-1, StringUtils.lastIndexOfAny("abc", new CharSequence[0]));
        assertEquals(-1, StringUtils.lastIndexOfAny("abc", new CharSequence[]{null}));
        assertEquals(6, StringUtils.lastIndexOfAny("zzabyycdxx", "ab", "cd"));
        assertEquals(6, StringUtils.lastIndexOfAny("zzabyycdxx", "cd", "ab"));
        assertEquals(-1, StringUtils.lastIndexOfAny("zzabyycdxx", "mn", "op"));
        assertEquals(10, StringUtils.lastIndexOfAny("zzabyycdxx", "mn", ""));
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
    }

    @Test
    public void testLeftRightMid() throws Throwable {
        assertNull(StringUtils.left(null, 0));
        assertEquals("", StringUtils.left("abc", -1));
        assertEquals("", StringUtils.left("", 2));
        assertEquals("", StringUtils.left("abc", 0));
        assertEquals("ab", StringUtils.left("abc", 2));
        assertEquals("abc", StringUtils.left("abc", 4));

        assertNull(StringUtils.right(null, 0));
        assertEquals("", StringUtils.right("abc", -1));
        assertEquals("", StringUtils.right("", 2));
        assertEquals("", StringUtils.right("abc", 0));
        assertEquals("bc", StringUtils.right("abc", 2));
        assertEquals("abc", StringUtils.right("abc", 4));

        assertNull(StringUtils.mid(null, 0, 0));
        assertEquals("", StringUtils.mid("abc", 0, -1));
        assertEquals("", StringUtils.mid("", 0, 2));
        assertEquals("ab", StringUtils.mid("abc", 0, 2));
        assertEquals("abc", StringUtils.mid("abc", 0, 4));
        assertEquals("c", StringUtils.mid("abc", 2, 4));
        assertEquals("", StringUtils.mid("abc", 4, 2));
        assertEquals("ab", StringUtils.mid("abc", -2, 2));
        assertEquals("c", StringUtils.mid("abc", 2, 10));
    }

    @Test
    public void testSubstringBeforeAfter() throws Throwable {
        assertNull(StringUtils.substringBefore(null, "a"));
        assertEquals("", StringUtils.substringBefore("", "a"));
        assertEquals("abc", StringUtils.substringBefore("abc", null));
        assertEquals("", StringUtils.substringBefore("abc", ""));
        assertEquals("", StringUtils.substringBefore("abc", "a"));
        assertEquals("a", StringUtils.substringBefore("abcba", "b"));
        assertEquals("ab", StringUtils.substringBefore("abc", "c"));
        assertEquals("abc", StringUtils.substringBefore("abc", "d"));

        assertNull(StringUtils.substringAfter(null, "a"));
        assertEquals("", StringUtils.substringAfter("", "a"));
        assertEquals("", StringUtils.substringAfter("abc", null));
        assertEquals("abc", StringUtils.substringAfter("abc", ""));
        assertEquals("bc", StringUtils.substringAfter("abc", "a"));
        assertEquals("cba", StringUtils.substringAfter("abcba", "b"));
        assertEquals("", StringUtils.substringAfter("abc", "c"));
        assertEquals("", StringUtils.substringAfter("abc", "d"));

        assertNull(StringUtils.substringBeforeLast(null, "a"));
        assertEquals("", StringUtils.substringBeforeLast("", "a"));
        assertEquals("abc", StringUtils.substringBeforeLast("abc", null));
        assertEquals("abc", StringUtils.substringBeforeLast("abc", ""));
        assertEquals("abc", StringUtils.substringBeforeLast("abcba", "b"));
        assertEquals("ab", StringUtils.substringBeforeLast("abc", "c"));
        assertEquals("", StringUtils.substringBeforeLast("a", "a"));
        assertEquals("a", StringUtils.substringBeforeLast("a", "z"));

        assertNull(StringUtils.substringAfterLast(null, "a"));
        assertEquals("", StringUtils.substringAfterLast("", "a"));
        assertEquals("", StringUtils.substringAfterLast("abc", null));
        assertEquals("", StringUtils.substringAfterLast("abc", ""));
        assertEquals("bc", StringUtils.substringAfterLast("abc", "a"));
        assertEquals("a", StringUtils.substringAfterLast("abcba", "b"));
        assertEquals("", StringUtils.substringAfterLast("abc", "c"));
        assertEquals("", StringUtils.substringAfterLast("a", "a"));
        assertEquals("", StringUtils.substringAfterLast("a", "z"));
    }

    @Test
    public void testSubstringBetween() throws Throwable {
        assertNull(StringUtils.substringBetween(null, "a"));
        assertEquals("", StringUtils.substringBetween("", ""));
        assertNull(StringUtils.substringBetween("", "tag"));
        assertNull(StringUtils.substringBetween("tagabctag", null));
        assertEquals("", StringUtils.substringBetween("tagabctag", ""));
        assertEquals("abc", StringUtils.substringBetween("tagabctag", "tag"));

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
        assertArrayEquals(new String[0], StringUtils.substringsBetween("", "[", "]"));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.substringsBetween("[a][b][c]", "[", "]"));
        assertNull(StringUtils.substringsBetween("no match", "[", "]"));
        assertNull(StringUtils.substringsBetween("[open only", "[", "]"));
    }

    @Test
    public void testSplit() throws Throwable {
        assertNull(StringUtils.split(null));
        assertArrayEquals(new String[0], StringUtils.split(""));
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.split("abc def"));
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.split("abc  def"));
        assertArrayEquals(new String[]{"abc"}, StringUtils.split(" abc "));

        assertNull(StringUtils.split(null, '.'));
        assertArrayEquals(new String[0], StringUtils.split("", '.'));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.split("a.b.c", '.'));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.split("a..b.c", '.'));
        assertArrayEquals(new String[]{"a:b:c"}, StringUtils.split("a:b:c", '.'));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.split("a b c", ' '));

        assertNull(StringUtils.split(null, "."));
        assertArrayEquals(new String[0], StringUtils.split("", "."));
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.split("abc def", null));
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.split("abc def", " "));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.split("ab:cd:ef", ":", 0));
        assertArrayEquals(new String[]{"ab", "cd:ef"}, StringUtils.split("ab:cd:ef", ":", 2));

        assertNull(StringUtils.splitByWholeSeparator(null, ""));
        assertArrayEquals(new String[0], StringUtils.splitByWholeSeparator("", ""));
        assertArrayEquals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparator("ab de fg", null));
        assertArrayEquals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparator("ab   de fg", null));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab:cd:ef", ":"));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-"));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab de fg", null, 0));
        assertArrayEquals(new String[]{"ab", "cd:ef"}, StringUtils.splitByWholeSeparator("ab:cd:ef", ":", 2));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-", 5));
        assertArrayEquals(new String[]{"ab", "cd-!-ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", "-!-", 2));
        assertArrayEquals(new String[]{"ab", "cd-!-ef"}, StringUtils.splitByWholeSeparator("ab-!-cd-!-ef", ""));

        assertNull(StringUtils.splitByWholeSeparatorPreserveAllTokens(null, ""));
        assertArrayEquals(new String[0], StringUtils.splitByWholeSeparatorPreserveAllTokens("", ""));
        assertArrayEquals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab de fg", null));
        assertArrayEquals(new String[]{"ab", "", "", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab   de fg", null));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab:cd:ef", ":"));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab-!-cd-!-ef", "-!-"));
        assertArrayEquals(new String[]{"ab", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab de fg", null, 0));
        assertArrayEquals(new String[]{"ab", "", "", "de", "fg"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab   de fg", null, 0));
        assertArrayEquals(new String[]{"ab", "cd:ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab:cd:ef", ":", 2));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab-!-cd-!-ef", "-!-", 5));
        assertArrayEquals(new String[]{"ab", "cd-!-ef"}, StringUtils.splitByWholeSeparatorPreserveAllTokens("ab-!-cd-!-ef", "-!-", 2));

        assertNull(StringUtils.splitPreserveAllTokens(null));
        assertArrayEquals(new String[0], StringUtils.splitPreserveAllTokens(""));
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.splitPreserveAllTokens("abc def"));
        assertArrayEquals(new String[]{"abc", "", "def"}, StringUtils.splitPreserveAllTokens("abc  def"));
        assertArrayEquals(new String[]{"", "abc", ""}, StringUtils.splitPreserveAllTokens(" abc "));

        assertNull(StringUtils.splitPreserveAllTokens(null, '.'));
        assertArrayEquals(new String[0], StringUtils.splitPreserveAllTokens("", '.'));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.splitPreserveAllTokens("a.b.c", '.'));
        assertArrayEquals(new String[]{"a", "", "b", "c"}, StringUtils.splitPreserveAllTokens("a..b.c", '.'));

        assertNull(StringUtils.splitPreserveAllTokens(null, "."));
        assertArrayEquals(new String[0], StringUtils.splitPreserveAllTokens("", "."));
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.splitPreserveAllTokens("abc def", null));
        assertArrayEquals(new String[]{"abc", "def"}, StringUtils.splitPreserveAllTokens("abc def", " "));
        assertArrayEquals(new String[]{"abc", "", "def"}, StringUtils.splitPreserveAllTokens("abc  def", " "));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitPreserveAllTokens("ab:cd:ef", ":"));
        assertArrayEquals(new String[]{"ab", "cd", "ef", ""}, StringUtils.splitPreserveAllTokens("ab:cd:ef:", ":"));
        assertArrayEquals(new String[]{"ab", "cd", "ef", "", ""}, StringUtils.splitPreserveAllTokens("ab:cd:ef::", ":"));
        assertArrayEquals(new String[]{"ab", "", "cd", "ef"}, StringUtils.splitPreserveAllTokens("ab::cd:ef", ":"));
        assertArrayEquals(new String[]{"", "cd", "ef"}, StringUtils.splitPreserveAllTokens(":cd:ef", ":"));
        assertArrayEquals(new String[]{"", "", "cd", "ef"}, StringUtils.splitPreserveAllTokens("::cd:ef", ":"));
        assertArrayEquals(new String[]{"", "cd", "ef", ""}, StringUtils.splitPreserveAllTokens(":cd:ef:", ":"));
        assertArrayEquals(new String[]{"ab", "cd", "ef"}, StringUtils.splitPreserveAllTokens("ab de fg", null, 0));
        assertArrayEquals(new String[]{"ab", "", " de fg"}, StringUtils.splitPreserveAllTokens("ab   de fg", null, 3));

        assertNull(StringUtils.splitByCharacterType(null));
        assertArrayEquals(new String[0], StringUtils.splitByCharacterType(""));
        assertArrayEquals(new String[]{"ab", " ", "de", " ", "fg"}, StringUtils.splitByCharacterType("ab de fg"));
        assertArrayEquals(new String[]{"number", "5"}, StringUtils.splitByCharacterType("number5"));

        assertNull(StringUtils.splitByCharacterTypeCamelCase(null));
        assertArrayEquals(new String[0], StringUtils.splitByCharacterTypeCamelCase(""));
        assertArrayEquals(new String[]{"foo", "Bar"}, StringUtils.splitByCharacterTypeCamelCase("fooBar"));
        assertArrayEquals(new String[]{"foo", "200", "Bar"}, StringUtils.splitByCharacterTypeCamelCase("foo200Bar"));
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
        assertEquals("", StringUtils.join(new Object[]{"a"}, ';', 1, 1));

        assertNull(StringUtils.join((Object[]) null, "--"));
        assertEquals("", StringUtils.join(new Object[0], "--"));
        assertEquals("", StringUtils.join(new Object[]{null}, "--"));
        assertEquals("a--b--c", StringUtils.join(new Object[]{"a", "b", "c"}, "--"));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}, null));
        assertEquals("abc", StringUtils.join(new Object[]{"a", "b", "c"}, ""));
        assertEquals(",,a", StringUtils.join(new Object[]{null, "", "a"}, ','));
        assertEquals("", StringUtils.join(new Object[]{"a"}, "--", 1, 1));

        assertNull(StringUtils.join((Iterator<?>) null, ';'));
        assertEquals("", StringUtils.join(Arrays.asList(new String[0]).iterator(), ';'));
        assertEquals("a", StringUtils.join(Arrays.asList(new String[]{"a"}).iterator(), ';'));
        assertEquals("a;b", StringUtils.join(Arrays.asList(new String[]{"a", "b"}).iterator(), ';'));
        assertEquals("ab", StringUtils.join(Arrays.asList(new String[]{null, "b"}).iterator(), ';'));

        assertNull(StringUtils.join((Iterator<?>) null, "--"));
        assertEquals("", StringUtils.join(Arrays.asList(new String[0]).iterator(), "--"));
        assertEquals("a", StringUtils.join(Arrays.asList(new String[]{"a"}).iterator(), "--"));
        assertEquals("a--b", StringUtils.join(Arrays.asList(new String[]{"a", "b"}).iterator(), "--"));
        assertEquals("ab", StringUtils.join(Arrays.asList(new String[]{null, "b"}).iterator(), null));

        assertNull(StringUtils.join((Iterable<?>) null, ';'));
        assertEquals("a;b", StringUtils.join(Arrays.asList(new String[]{"a", "b"}), ';'));
        assertNull(StringUtils.join((Iterable<?>) null, "--"));
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
    public void testRemoveStartEnd() throws Throwable {
        assertNull(StringUtils.removeStart(null, ""));
        assertEquals("", StringUtils.removeStart("", ""));
        assertEquals("star", StringUtils.removeStart("star", null));
        assertEquals("domain.com", StringUtils.removeStart("www.domain.com", "www."));
        assertEquals("domain.com", StringUtils.removeStart("domain.com", "www."));
        assertEquals("www.domain.com", StringUtils.removeStart("www.domain.com", "domain"));
        assertEquals("abc", StringUtils.removeStart("abc", ""));

        assertNull(StringUtils.removeStartIgnoreCase(null, ""));
        assertEquals("", StringUtils.removeStartIgnoreCase("", ""));
        assertEquals("star", StringUtils.removeStartIgnoreCase("star", null));
        assertEquals("domain.com", StringUtils.removeStartIgnoreCase("www.domain.com", "www."));
        assertEquals("domain.com", StringUtils.removeStartIgnoreCase("www.domain.com", "WWW."));
        assertEquals("domain.com", StringUtils.removeStartIgnoreCase("domain.com", "www."));
        assertEquals("www.domain.com", StringUtils.removeStartIgnoreCase("www.domain.com", "domain"));
        assertEquals("abc", StringUtils.removeStartIgnoreCase("abc", ""));

        assertNull(StringUtils.removeEnd(null, ""));
        assertEquals("", StringUtils.removeEnd("", ""));
        assertEquals("star", StringUtils.removeEnd("star", null));
        assertEquals("www.domain.com", StringUtils.removeEnd("www.domain.com", ".com."));
        assertEquals("www.domain", StringUtils.removeEnd("www.domain.com", ".com"));
        assertEquals("www.domain.com", StringUtils.removeEnd("www.domain.com", "domain"));
        assertEquals("abc", StringUtils.removeEnd("abc", ""));

        assertNull(StringUtils.removeEndIgnoreCase(null, ""));
        assertEquals("", StringUtils.removeEndIgnoreCase("", ""));
        assertEquals("star", StringUtils.removeEndIgnoreCase("star", null));
        assertEquals("www.domain.com", StringUtils.removeEndIgnoreCase("www.domain.com", ".com."));
        assertEquals("www.domain", StringUtils.removeEndIgnoreCase("www.domain.com", ".com"));
        assertEquals("www.domain.com", StringUtils.removeEndIgnoreCase("www.domain.com", "domain"));
        assertEquals("abc", StringUtils.removeEndIgnoreCase("abc", ""));
        assertEquals("www.domain", StringUtils.removeEndIgnoreCase("www.domain.com", ".COM"));
        assertEquals("www.domain", StringUtils.removeEndIgnoreCase("www.domain.COM", ".com"));
    }

    @Test
    public void testRemove() throws Throwable {
        assertNull(StringUtils.remove(null, ""));
        assertEquals("", StringUtils.remove("", ""));
        assertEquals("star", StringUtils.remove("star", null));
        assertEquals("star", StringUtils.remove("star", ""));
        assertEquals("qd", StringUtils.remove("queued", "ue"));
        assertEquals("queued", StringUtils.remove("queued", "zz"));

        assertNull(StringUtils.remove(null, 'u'));
        assertEquals("", StringUtils.remove("", 'u'));
        assertEquals("queued", StringUtils.remove("queued", 'z'));
        assertEquals("qeed", StringUtils.remove("queued", 'u'));
    }

    @Test
    public void testReplace() throws Throwable {
        assertNull(StringUtils.replaceOnce(null, "", ""));
        assertEquals("", StringUtils.replaceOnce("", "", ""));
        assertEquals("any", StringUtils.replaceOnce("any", null, ""));
        assertEquals("any", StringUtils.replaceOnce("any", "", null));
        assertEquals("any", StringUtils.replaceOnce("any", "", ""));
        assertEquals("aba", StringUtils.replaceOnce("aba", "a", null));
        assertEquals("ba", StringUtils.replaceOnce("aba", "a", ""));
        assertEquals("zba", StringUtils.replaceOnce("aba", "a", "z"));

        assertNull(StringUtils.replace(null, "", ""));
        assertEquals("", StringUtils.replace("", "", ""));
        assertEquals("any", StringUtils.replace("any", null, ""));
        assertEquals("any", StringUtils.replace("any", "", null));
        assertEquals("any", StringUtils.replace("any", "", ""));
        assertEquals("aba", StringUtils.replace("aba", "a", null));
        assertEquals("b", StringUtils.replace("aba", "a", ""));
        assertEquals("zbz", StringUtils.replace("aba", "a", "z"));

        assertNull(StringUtils.replace(null, "", "", 1));
        assertEquals("", StringUtils.replace("", "", "", 1));
        assertEquals("any", StringUtils.replace("any", null, "", 1));
        assertEquals("any", StringUtils.replace("any", "", null, 1));
        assertEquals("any", StringUtils.replace("any", "", "", 1));
        assertEquals("any", StringUtils.replace("any", "", "", 0));
        assertEquals("abaa", StringUtils.replace("abaa", "a", null, -1));
        assertEquals("b", StringUtils.replace("abaa", "a", "", -1));
        assertEquals("abaa", StringUtils.replace("abaa", "a", "z", 0));
        assertEquals("zbaa", StringUtils.replace("abaa", "a", "z", 1));
        assertEquals("zbza", StringUtils.replace("abaa", "a", "z", 2));
        assertEquals("zbzz", StringUtils.replace("abaa", "a", "z", -1));
        assertEquals("abaa", StringUtils.replace("abaa", "notfound", "z", -1));

        assertNull(StringUtils.replaceEach(null, new String[0], new String[0]));
        assertEquals("", StringUtils.replaceEach("", new String[0], new String[0]));
        assertEquals("aba", StringUtils.replaceEach("aba", null, null));
        assertEquals("aba", StringUtils.replaceEach("aba", new String[0], null));
        assertEquals("aba", StringUtils.replaceEach("aba", null, new String[0]));
        assertEquals("aba", StringUtils.replaceEach("aba", new String[]{"a"}, null));
        assertEquals("b", StringUtils.replaceEach("aba", new String[]{"a"}, new String[]{""}));
        assertEquals("aba", StringUtils.replaceEach("aba", new String[]{null}, new String[]{"a"}));
        assertEquals("wcte", StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"w", "t"}));
        assertEquals("dcte", StringUtils.replaceEach("abcde", new String[]{"ab", "d"}, new String[]{"d", "t"}));

        try {
            StringUtils.replaceEach("abcde", new String[]{"ab"}, new String[]{"w", "t"});
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Search and Replace array lengths don't match"));
        }

        assertEquals("aba", StringUtils.replaceEachRepeatedly("aba", null, null));
        assertEquals("tcte", StringUtils.replaceEachRepeatedly("abcde", new String[]{"ab", "d"}, new String[]{"t", "t"}));
        
        try {
            StringUtils.replaceEachRepeatedly("abcde", new String[]{"ab", "d"}, new String[]{"d", "ab"});
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Aborting to protect against StackOverflowError"));
        }
    }

    @Test
    public void testReplaceChars() throws Throwable {
        assertNull(StringUtils.replaceChars(null, 'a', 'b'));
        assertEquals("", StringUtils.replaceChars("", 'a', 'b'));
        assertEquals("aycya", StringUtils.replaceChars("abcba", 'b', 'y'));
        assertEquals("abcba", StringUtils.replaceChars("abcba", 'z', 'y'));

        assertNull(StringUtils.replaceChars(null, "a", "b"));
        assertEquals("", StringUtils.replaceChars("", "a", "b"));
        assertEquals("abc", StringUtils.replaceChars("abc", null, "b"));
        assertEquals("abc", StringUtils.replaceChars("abc", "", "b"));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", null));
        assertEquals("ac", StringUtils.replaceChars("abc", "b", ""));
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yz"));
        assertEquals("ayya", StringUtils.replaceChars("abcba", "bc", "y"));
        assertEquals("ayzya", StringUtils.replaceChars("abcba", "bc", "yzx"));
        assertEquals("abc", StringUtils.replaceChars("abc", "z", "y"));
    }

    @Test
    public void testOverlay() throws Throwable {
        assertNull(StringUtils.overlay(null, "", 0, 0));
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
    public void testChompChop() throws Throwable {
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

        assertNull(StringUtils.chop(null));
        assertEquals("", StringUtils.chop(""));
        assertEquals("abc ", StringUtils.chop("abc \r"));
        assertEquals("abc", StringUtils.chop("abc\n"));
        assertEquals("abc", StringUtils.chop("abc\r\n"));
        assertEquals("ab", StringUtils.chop("abc"));
        assertEquals("abc\nab", StringUtils.chop("abc\nabc"));
        assertEquals("", StringUtils.chop("a"));
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
        assertEquals("aaaa", StringUtils.repeat("a", 4));
        assertEquals("ababab", StringUtils.repeat("ab", 3));

        assertNull(StringUtils.repeat(null, null, 2));
        assertNull(StringUtils.repeat(null, "x", 2));
        assertEquals("", StringUtils.repeat("", null, 0));
        assertEquals("", StringUtils.repeat("", "", 2));
        assertEquals("xxx", StringUtils.repeat("", "x", 3));
        assertEquals("?, ?, ?", StringUtils.repeat("?", ", ", 3));

        assertEquals("", StringUtils.repeat('e', 0));
        assertEquals("eee", StringUtils.repeat('e', 3));
        assertEquals("", StringUtils.repeat('e', -2));
    }

    @Test
    public void testPadding() throws Throwable {
        assertNull(StringUtils.rightPad(null, 0));
        assertEquals("   ", StringUtils.rightPad("", 3));
        assertEquals("bat", StringUtils.rightPad("bat", 3));
        assertEquals("bat  ", StringUtils.rightPad("bat", 5));
        assertEquals("bat", StringUtils.rightPad("bat", 1));
        assertEquals("bat", StringUtils.rightPad("bat", -1));
        assertEquals("batzz", StringUtils.rightPad("bat", 8500, 'z'));

        assertNull(StringUtils.rightPad(null, 0, 'z'));
        assertEquals("zzz", StringUtils.rightPad("", 3, 'z'));
        assertEquals("bat", StringUtils.rightPad("bat", 3, 'z'));
        assertEquals("batzz", StringUtils.rightPad("bat", 5, 'z'));
        assertEquals("bat", StringUtils.rightPad("bat", 1, 'z'));
        assertEquals("bat", StringUtils.rightPad("bat", -1, 'z'));

        assertNull(StringUtils.rightPad(null, 0, "z"));
        assertEquals("zzz", StringUtils.rightPad("", 3, "z"));
        assertEquals("bat", StringUtils.rightPad("bat", 3, "yz"));
        assertEquals("batyz", StringUtils.rightPad("bat", 5, "yz"));
        assertEquals("batyzyzy", StringUtils.rightPad("bat", 8, "yz"));
        assertEquals("bat", StringUtils.rightPad("bat", 1, "yz"));
        assertEquals("bat", StringUtils.rightPad("bat", -1, "yz"));
        assertEquals("bat  ", StringUtils.rightPad("bat", 5, null));
        assertEquals("bat  ", StringUtils.rightPad("bat", 5, ""));
        assertEquals("batyz", StringUtils.rightPad("bat", 5, "y"));
        assertEquals("batyzy", StringUtils.rightPad("bat", 6, "yz"));

        assertNull(StringUtils.leftPad(null, 0));
        assertEquals("   ", StringUtils.leftPad("", 3));
        assertEquals("bat", StringUtils.leftPad("bat", 3));
        assertEquals("  bat", StringUtils.leftPad("bat", 5));
        assertEquals("bat", StringUtils.leftPad("bat", 1));
        assertEquals("bat", StringUtils.leftPad("bat", -1));
        assertEquals("zzbat", StringUtils.leftPad("bat", 8500, 'z'));

        assertNull(StringUtils.leftPad(null, 0, 'z'));
        assertEquals("zzz", StringUtils.leftPad("", 3, 'z'));
        assertEquals("bat", StringUtils.leftPad("bat", 3, 'z'));
        assertEquals("zzbat", StringUtils.leftPad("bat", 5, 'z'));
        assertEquals("bat", StringUtils.leftPad("bat", 1, 'z'));
        assertEquals("bat", StringUtils.leftPad("bat", -1, 'z'));

        assertNull(StringUtils.leftPad(null, 0, "z"));
        assertEquals("zzz", StringUtils.leftPad("", 3, "z"));
        assertEquals("bat", StringUtils.leftPad("bat", 3, "yz"));
        assertEquals("yzbat", StringUtils.leftPad("bat", 5, "yz"));
        assertEquals("yzyzybat", StringUtils.leftPad("bat", 8, "yz"));
        assertEquals("bat", StringUtils.leftPad("bat", 1, "yz"));
        assertEquals("bat", StringUtils.leftPad("bat", -1, "yz"));
        assertEquals("  bat", StringUtils.leftPad("bat", 5, null));
        assertEquals("  bat", StringUtils.leftPad("bat", 5, ""));
        assertEquals("yzbat", StringUtils.leftPad("bat", 5, "y"));
        assertEquals("yzybat", StringUtils.leftPad("bat", 6, "yz"));

        assertEquals(0, StringUtils.length(null));
        assertEquals(3, StringUtils.length("abc"));
    }

    @Test
    public void testCenter() throws Throwable {
        assertNull(StringUtils.center(null, 0));
        assertEquals("    ", StringUtils.center("", 4));
        assertEquals("ab", StringUtils.center("ab", -1));
        assertEquals(" ab ", StringUtils.center("ab", 4));
        assertEquals("abcd", StringUtils.center("abcd", 2));
        assertEquals(" a  ", StringUtils.center("a", 4));

        assertNull(StringUtils.center(null, 0, ' '));
        assertEquals("    ", StringUtils.center("", 4, ' '));
        assertEquals("ab", StringUtils.center("ab", -1, ' '));
        assertEquals(" ab", StringUtils.center("ab", 4, ' '));
        assertEquals("abcd", StringUtils.center("abcd", 2, ' '));
        assertEquals(" a  ", StringUtils.center("a", 4, ' '));
        assertEquals("yayy", StringUtils.center("a", 4, 'y'));

        assertNull(StringUtils.center(null, 0, " "));
        assertEquals("    ", StringUtils.center("", 4, " "));
        assertEquals("ab", StringUtils.center("ab", -1, " "));
        assertEquals(" ab", StringUtils.center("ab", 4, " "));
        assertEquals("abcd", StringUtils.center("abcd", 2, " "));
        assertEquals(" a  ", StringUtils.center("a", 4, " "));
        assertEquals("yayz", StringUtils.center("a", 4, "yz"));
        assertEquals("  abc  ", StringUtils.center("abc", 7, null));
        assertEquals("  abc  ", StringUtils.center("abc", 7, ""));
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
        assertEquals(0, StringUtils.countMatches(null, ""));
        assertEquals(0, StringUtils.countMatches("", ""));
        assertEquals(0, StringUtils.countMatches("abba", null));
        assertEquals(0, StringUtils.countMatches("abba", ""));
        assertEquals(2, StringUtils.countMatches("abba", "a"));
        assertEquals(1, StringUtils.countMatches("abba", "ab"));
        assertEquals(0, StringUtils.countMatches("abba", "xxx"));
    }

    @Test
    public void testCharacterTests() throws Throwable {
        assertFalse(StringUtils.isAlpha(null));
        assertFalse(StringUtils.isAlpha(""));
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

        assertFalse(StringUtils.isAlphanumeric(null));
        assertFalse(StringUtils.isAlphanumeric(""));
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
        assertTrue(StringUtils.isAsciiPrintable("ab2c"));
        assertTrue(StringUtils.isAsciiPrintable("!ab-c~"));
        assertFalse(StringUtils.isAsciiPrintable("\u007f"));

        assertFalse(StringUtils.isNumeric(null));
        assertFalse(StringUtils.isNumeric(""));
        assertFalse(StringUtils.isNumeric("  "));
        assertTrue(StringUtils.isNumeric("123"));
        assertFalse(StringUtils.isNumeric("12 3"));
        assertFalse(StringUtils.isNumeric("ab2c"));
        assertFalse(StringUtils.isNumeric("12.3"));

        assertFalse(StringUtils.isNumericSpace(null));
        assertTrue(StringUtils.isNumericSpace(""));
        assertTrue(StringUtils.isNumericSpace("  "));
        assertTrue(StringUtils.isNumericSpace("123"));
        assertTrue(StringUtils.isNumericSpace("12 3"));
        assertFalse(StringUtils.isNumericSpace("ab2c"));
        assertFalse(StringUtils.isNumericSpace("12.3"));

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

        assertEquals("NULL", StringUtils.defaultIfBlank(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfBlank("", "NULL"));
        assertEquals("NULL", StringUtils.defaultIfBlank(" ", "NULL"));
        assertEquals("bat", StringUtils.defaultIfBlank("bat", "NULL"));
        assertNull(StringUtils.defaultIfBlank("", null));

        assertEquals("NULL", StringUtils.defaultIfEmpty(null, "NULL"));
        assertEquals("NULL", StringUtils.defaultIfEmpty("", "NULL"));
        assertEquals(" ", StringUtils.defaultIfEmpty(" ", "NULL"));
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
        assertNull(StringUtils.abbreviate(null, 4));
        assertEquals("", StringUtils.abbreviate("", 4));
        assertEquals("abc...", StringUtils.abbreviate("abcdefg", 6));
        assertEquals("abcdefg", StringUtils.abbreviate("abcdefg", 7));
        assertEquals("abcdefg", StringUtils.abbreviate("abcdefg", 8));
        assertEquals("a...", StringUtils.abbreviate("abcdefg", 4));

        try {
            StringUtils.abbreviate("abcdefg", 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minimum abbreviation width is 4"));
        }

        assertNull(StringUtils.abbreviate(null, 0, 4));
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

        try {
            StringUtils.abbreviate("abcdefghij", 0, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minimum abbreviation width is 4"));
        }

        try {
            StringUtils.abbreviate("abcdefghij", 5, 6);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minimum abbreviation width with offset is 7"));
        }

        assertNull(StringUtils.abbreviateMiddle(null, null, 0));
        assertEquals("abc", StringUtils.abbreviateMiddle("abc", null, 0));
        assertEquals("abc", StringUtils.abbreviateMiddle("abc", ".", 0));
        assertEquals("abc", StringUtils.abbreviateMiddle("abc", ".", 3));
        assertEquals("ab.f", StringUtils.abbreviateMiddle("abcdef", ".", 4));
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

        assertEquals(-1, StringUtils.indexOfDifference((CharSequence[]) null));
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

        assertEquals("", StringUtils.getCommonPrefix((String[]) null));
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
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Strings must not be null"));
        }

        try {
            StringUtils.getLevenshteinDistance("a", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Strings must not be null"));
        }

        assertEquals(0, StringUtils.getLevenshteinDistance("", ""));
        assertEquals(1, StringUtils.getLevenshteinDistance("", "a"));
        assertEquals(7, StringUtils.getLevenshteinDistance("aaapppp", ""));
        assertEquals(7, StringUtils.getLevenshteinDistance("", "aaapppp"));
        assertEquals(1, StringUtils.getLevenshteinDistance("frog", "fog"));
        assertEquals(3, StringUtils.getLevenshteinDistance("fly", "ant"));
        assertEquals(7, StringUtils.getLevenshteinDistance("elephant", "hippo"));
        assertEquals(7, StringUtils.getLevenshteinDistance("hippo", "elephant"));
        assertEquals(8, StringUtils.getLevenshteinDistance("hippo", "zzzzzzzz"));
        assertEquals(1, StringUtils.getLevenshteinDistance("hello", "hallo"));

        try {
            StringUtils.getLevenshteinDistance(null, "a", 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Strings must not be null"));
        }

        try {
            StringUtils.getLevenshteinDistance("a", null, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Strings must not be null"));
        }

        try {
            StringUtils.getLevenshteinDistance("a", "a", -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Threshold must not be negative"));
        }

        assertEquals(0, StringUtils.getLevenshteinDistance("", "", 0));
        assertEquals(7, StringUtils.getLevenshteinDistance("aaapppp", "", 8));
        assertEquals(7, StringUtils.getLevenshteinDistance("aaapppp", "", 7));
        assertEquals(-1, StringUtils.getLevenshteinDistance("aaapppp", "", 6));
        assertEquals(7, StringUtils.getLevenshteinDistance("elephant", "hippo", 7));
        assertEquals(-1, StringUtils.getLevenshteinDistance("elephant", "hippo", 6));
        assertEquals(7, StringUtils.getLevenshteinDistance("hippo", "elephant", 7));
        assertEquals(-1, StringUtils.getLevenshteinDistance("hippo", "elephant", 6));
        assertEquals(1, StringUtils.getLevenshteinDistance("frog", "fog", 1));
        assertEquals(-1, StringUtils.getLevenshteinDistance("fly", "ant", 1));
        assertEquals(-1, StringUtils.getLevenshteinDistance("a", "abcdef", 1));
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

        assertFalse(StringUtils.startsWithAny(null, (CharSequence[]) null));
        assertFalse(StringUtils.startsWithAny("abcxyz", (CharSequence[]) null));
        assertFalse(StringUtils.startsWithAny("abcxyz", new CharSequence[]{""}));
        assertTrue(StringUtils.startsWithAny("abcxyz", new CharSequence[]{"abc"}));
        assertTrue(StringUtils.startsWithAny("abcxyz", new CharSequence[]{null, "xyz", "abc"}));

        assertTrue(StringUtils.endsWith(null, null));
        assertFalse(StringUtils.endsWith(null, "def"));
        assertFalse(StringUtils.endsWith("abcdef", null));
        assertTrue(StringUtils.endsWith("abcdef", "def"));
        assertFalse(StringUtils.endsWith("ABCDEF", "def"));
        assertFalse(StringUtils.endsWith("ABCDEF", "cde"));
        assertFalse(StringUtils.endsWith("abc", "abcdef"));

        assertTrue(StringUtils.endsWithIgnoreCase(null, null));
        assertFalse(StringUtils.endsWithIgnoreCase(null, "def"));
        assertFalse(StringUtils.endsWithIgnoreCase("abcdef", null));
        assertTrue(StringUtils.endsWithIgnoreCase("abcdef", "def"));
        assertTrue(StringUtils.endsWithIgnoreCase("ABCDEF", "def"));
        assertFalse(StringUtils.endsWithIgnoreCase("ABCDEF", "cde"));
        assertFalse(StringUtils.endsWithIgnoreCase("abc", "abcdef"));

        assertFalse(StringUtils.endsWithAny(null, (CharSequence[]) null));
        assertFalse(StringUtils.endsWithAny("abcxyz", (CharSequence[]) null));
        assertTrue(StringUtils.endsWithAny("abcxyz", new CharSequence[]{""}));
        assertTrue(StringUtils.endsWithAny("abcxyz", new CharSequence[]{"xyz"}));
        assertTrue(StringUtils.endsWithAny("abcxyz", new CharSequence[]{null, "xyz", "abc"}));
    }

    @Test
    public void testNormalizeSpace() throws Throwable {
        assertNull(StringUtils.normalizeSpace(null));
        assertEquals("", StringUtils.normalizeSpace(""));
        assertEquals("abc def", StringUtils.normalizeSpace("  abc   def  "));
    }

    @Test
    public void testToStringBytes() throws Throwable {
        byte[] bytes = "test".getBytes("UTF-8");
        assertEquals("test", StringUtils.toString(bytes, "UTF-8"));
        assertEquals("test", StringUtils.toString(bytes, null));

        try {
            StringUtils.toString(bytes, "INVALID_CHARSET");
            fail("Expected UnsupportedEncodingException");
        } catch (UnsupportedEncodingException e) {
            assertNotNull(e);
        }
    }
}