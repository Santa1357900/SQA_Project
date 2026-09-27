package org.apache.commons.lang;

import junit.framework.TestCase;

public class WordUtilsTest extends TestCase {

    public void testConstructor() throws Throwable {
        WordUtils utils = new WordUtils();
        assertNotNull(utils);
    }

    public void testWrap_StringInt() throws Throwable {
        assertNull(WordUtils.wrap(null, 10));
        assertEquals("", WordUtils.wrap("", 10));
        
        String input = "Here is one long string of text that needs wrapping.";
        String expected = "Here is\none long\nstring of\ntext that\nneeds\nwrapping.";
        assertEquals(expected, WordUtils.wrap(input, 10));
    }

    public void testWrap_StringIntStringBoolean() throws Throwable {
        assertNull(WordUtils.wrap(null, 10, "\n", true));
        assertEquals("", WordUtils.wrap("", 10, "\n", true));

        // wrapLength < 1 handled as 1
        assertEquals("a\nb\nc", WordUtils.wrap("abc", 0, "\n", true));

        String input = "To wrap or not to wrap http://www.apache.org";
        
        // wrapLongWords = false, long word/URL extends beyond limit
        String resultNoWrapLong = WordUtils.wrap(input, 10, "<br>", false);
        assertNotNull(resultNoWrapLong);

        // wrapLongWords = true
        String resultWrapLong = WordUtils.wrap("abcdefghijk", 5, "<br>", true);
        assertEquals("abcde<br>fghij<br>k", resultWrapLong);

        // spaceToWrapAt < offset branch (no space found after wrapLength)
        String noSpaceAfterOffset = WordUtils.wrap("short longword", 5, "\n", false);
        assertEquals("short\nlongword", noSpaceAfterOffset);
    }

    public void testCapitalize_String() throws Throwable {
        assertNull(WordUtils.capitalize(null));
        assertEquals("", WordUtils.capitalize(""));
        assertEquals("I Am FINE", WordUtils.capitalize("i am FINE"));
    }

    public void testCapitalize_StringCharArray() throws Throwable {
        assertNull(WordUtils.capitalize(null, new char[]{'.'}));
        assertEquals("", WordUtils.capitalize("", new char[]{'.'}));
        
        char[] emptyDelims = new char[0];
        assertEquals("i aM fine", WordUtils.capitalize("i aM fine", emptyDelims));

        assertEquals("I Am Fine", WordUtils.capitalize("i am fine", null));
        assertEquals("I aM.Fine", WordUtils.capitalize("i aM.fine", new char[]{'.'}));
    }

    public void testCapitalizeFully_String() throws Throwable {
        assertNull(WordUtils.capitalizeFully(null));
        assertEquals("", WordUtils.capitalizeFully(""));
        assertEquals("I Am Fine", WordUtils.capitalizeFully("i am FINE"));
    }

    public void testCapitalizeFully_StringCharArray() throws Throwable {
        assertNull(WordUtils.capitalizeFully(null, new char[]{'.'}));
        assertEquals("", WordUtils.capitalizeFully("", new char[]{'.'}));

        char[] emptyDelims = new char[0];
        assertEquals("i am fine", WordUtils.capitalizeFully("i am fine", emptyDelims));

        assertEquals("I Am Fine", WordUtils.capitalizeFully("i am fine", null));
        assertEquals("I am.Fine", WordUtils.capitalizeFully("i aM.fine", new char[]{'.'}));
    }

    public void testUncapitalize_String() throws Throwable {
        assertNull(WordUtils.uncapitalize(null));
        assertEquals("", WordUtils.uncapitalize(""));
        assertEquals("i am fINE", WordUtils.uncapitalize("I Am FINE"));
    }

    public void testUncapitalize_StringCharArray() throws Throwable {
        assertNull(WordUtils.uncapitalize(null, new char[]{'.'}));
        assertEquals("", WordUtils.uncapitalize("", new char[]{'.'}));

        char[] emptyDelims = new char[0];
        assertEquals("I AM.FINE", WordUtils.uncapitalize("I AM.FINE", emptyDelims));

        assertEquals("i am fine", WordUtils.uncapitalize("I AM FINE", null));
        assertEquals("i AM.fINE", WordUtils.uncapitalize("I AM.FINE", new char[]{'.'}));
    }

    public void testSwapCase() throws Throwable {
        assertNull(WordUtils.swapCase(null));
        assertEquals("", WordUtils.swapCase(""));
        
        // Testing Upper, Title, Lower, and non-letter/whitespace
        String input = "The dog has a BONE. 123";
        String expected = "tHE DOG HAS A bone. 123";
        assertEquals(expected, WordUtils.swapCase(input));
    }

    public void testInitials_String() throws Throwable {
        assertNull(WordUtils.initials(null));
        assertEquals("", WordUtils.initials(""));
        assertEquals("BJL", WordUtils.initials("Ben John Lee"));
        assertEquals("BJ", WordUtils.initials("Ben J.Lee"));
    }

    public void testInitials_StringCharArray() throws Throwable {
        assertNull(WordUtils.initials(null, new char[]{'.'}));
        assertEquals("", WordUtils.initials("", new char[]{'.'}));

        char[] emptyDelims = new char[0];
        assertEquals("", WordUtils.initials("Ben John Lee", emptyDelims));

        assertEquals("BJL", WordUtils.initials("Ben John Lee", null));
        assertEquals("BJL", WordUtils.initials("Ben J.Lee", new char[]{' ', '.'}));
    }

    public void testAbbreviate() throws Throwable {
        assertNull(WordUtils.abbreviate(null, 0, 10, "..."));
        assertEquals("", WordUtils.abbreviate("", 0, 10, "..."));

        String str = "The quick brown fox jumps over the lazy dog";
        
        // upper == -1
        String res1 = WordUtils.abbreviate(str, 5, -1, "...");
        assertTrue(res1.startsWith("The quick"));

        // upper > str.length()
        String res2 = WordUtils.abbreviate(str, 5, 100, "...");
        assertEquals(str, res2);

        // upper < lower
        String res3 = WordUtils.abbreviate(str, 15, 5, "...");
        assertNotNull(res3);

        // index == -1 (no space found after lower)
        String noSpaceStr = "Supercalifragilistic";
        String res4 = WordUtils.abbreviate(noSpaceStr, 0, 5, "...");
        assertEquals("Super...", res4);

        // index > upper
        String res5 = WordUtils.abbreviate("Hello World", 0, 3, "...");
        assertEquals("Hel...", res5);

        // index <= upper (normal break at space)
        String res6 = WordUtils.abbreviate("Hello World", 2, 8, "...");
        assertEquals("Hello...", res6);
    }
}