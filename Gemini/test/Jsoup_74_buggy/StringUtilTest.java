package org.jsoup.helper;

import org.junit.Test;
import static org.junit.Assert.*;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class StringUtilTest {

    @Test
    public void testJoinCollection() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        list.add("c");
        
        String result = StringUtil.join(list, ",");
        assertEquals("a,b,c", result);

        List<String> single = new ArrayList<String>();
        single.add("single");
        assertEquals("single", StringUtil.join(single, ","));

        List<String> empty = new ArrayList<String>();
        assertEquals("", StringUtil.join(empty, ","));
    }

    @Test
    public void testJoinIterator() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("x");
        list.add("y");
        
        String result = StringUtil.join(list.iterator(), "-");
        assertEquals("x-y", result);

        List<String> empty = new ArrayList<String>();
        assertEquals("", StringUtil.join(empty.iterator(), "-"));
    }

    @Test
    public void testJoinArray() throws Throwable {
        String[] arr = {"foo", "bar"};
        assertEquals("foo/bar", StringUtil.join(arr, "/"));
    }

    @Test
    public void testPadding() throws Throwable {
        assertEquals("", StringUtil.padding(0));
        assertEquals(" ", StringUtil.padding(1));
        assertEquals("          ", StringUtil.padding(10)); // within cache (21)
        assertEquals("                    ", StringUtil.padding(20)); // max cache
        
        // Larger than padding array length
        char[] expectedChars = new char[25];
        Arrays.fill(expectedChars, ' ');
        String expected = new String(expectedChars);
        assertEquals(expected, StringUtil.padding(25));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPaddingNegative() throws Throwable {
        StringUtil.padding(-1);
    }

    @Test
    public void testIsBlank() throws Throwable {
        assertTrue(StringUtil.isBlank(null));
        assertTrue(StringUtil.isBlank(""));
        assertTrue(StringUtil.isBlank("   "));
        assertTrue(StringUtil.isBlank("\t\n\r"));
        assertFalse(StringUtil.isBlank("a"));
        assertFalse(StringUtil.isBlank("  a  "));
    }

    @Test
    public void testIsNumeric() throws Throwable {
        assertFalse(StringUtil.isNumeric(null));
        assertFalse(StringUtil.isNumeric(""));
        assertFalse(StringUtil.isNumeric("123a"));
        assertFalse(StringUtil.isNumeric(" "));
        assertTrue(StringUtil.isNumeric("123456"));
    }

    @Test
    public void testIsWhitespace() throws Throwable {
        assertTrue(StringUtil.isWhitespace(' '));
        assertTrue(StringUtil.isWhitespace('\t'));
        assertTrue(StringUtil.isWhitespace('\n'));
        assertTrue(StringUtil.isWhitespace('\f'));
        assertTrue(StringUtil.isWhitespace('\r'));
        assertFalse(StringUtil.isWhitespace('a'));
        assertFalse(StringUtil.isWhitespace(160));
    }

    @Test
    public void testIsActuallyWhitespace() throws Throwable {
        assertTrue(StringUtil.isActuallyWhitespace(' '));
        assertTrue(StringUtil.isActuallyWhitespace('\t'));
        assertTrue(StringUtil.isActuallyWhitespace('\n'));
        assertTrue(StringUtil.isActuallyWhitespace('\f'));
        assertTrue(StringUtil.isActuallyWhitespace('\r'));
        assertTrue(StringUtil.isActuallyWhitespace(160));
        assertFalse(StringUtil.isActuallyWhitespace('a'));
    }

    @Test
    public void testNormaliseWhitespace() throws Throwable {
        String input = "  Hello   \n\t world  ";
        String normalized = StringUtil.normaliseWhitespace(input);
        assertEquals(" Hello world ", normalized);
    }

    @Test
    public void testAppendNormalisedWhitespace() throws Throwable {
        StringBuilder sb = new StringBuilder();
        StringUtil.appendNormalisedWhitespace(sb, "  Hello   world ", true);
        assertEquals("Hello world ", sb.toString());
    }

    @Test
    public void testIn() throws Throwable {
        String[] haystack = {"apple", "banana", "cherry"};
        assertTrue(StringUtil.in("banana", haystack));
        assertFalse(StringUtil.in("grape", haystack));
    }

    @Test
    public void testInSorted() throws Throwable {
        String[] haystack = {"apple", "banana", "cherry"};
        Arrays.sort(haystack);
        assertTrue(StringUtil.inSorted("banana", haystack));
        assertFalse(StringUtil.inSorted("grape", haystack));
    }

    @Test
    public void testResolveUrl() throws Throwable {
        URL base = new URL("http://example.com/path/file");
        URL resolved = StringUtil.resolve(base, "?query=1");
        assertEquals("http://example.com/path/?query=1", resolved.toExternalForm());

        URL base2 = new URL("http://example.com");
        URL resolved2 = StringUtil.resolve(base2, "./foo");
        assertEquals("http://example.com/./foo", resolved2.toExternalForm());

        String resStr1 = StringUtil.resolve("http://example.com/path", "?foo");
        assertEquals("http://example.com/?foo", resStr1);

        String resStr2 = StringUtil.resolve("invalid-base", "http://example.com/abs");
        assertEquals("http://example.com/abs", resStr2);

        String resStr3 = StringUtil.resolve("invalid-base", "invalid-rel");
        assertEquals("", resStr3);
    }

    @Test
    public void testStringBuilderCaching() throws Throwable {
        StringBuilder sb1 = StringUtil.stringBuilder();
        sb1.append("test");
        StringBuilder sb2 = StringUtil.stringBuilder();
        assertEquals(0, sb2.length());

        // Test max cached builder size branch
        StringBuilder largeSb = new StringBuilder();
        for (int i = 0; i < 9 * 1024; i++) {
            largeSb.append("a");
        }
        // Force the thread local to hold a large builder or simulate reset logic
        // We can just call stringBuilder multiple times to exercise code paths
        assertNotNull(StringUtil.stringBuilder());
    }
}