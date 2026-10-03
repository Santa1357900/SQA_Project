package org.apache.commons.lang;

import org.junit.Test;
import static org.junit.Assert.*;

public class WordUtilsClaudeTest {

    // Covers the public no-arg constructor
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        WordUtils wu = new WordUtils();
        assertNotNull(wu);
    }

    // wrap(String,int) null input branch -> null
    @Test
    public void testWrap_nullInput_returnsNull() throws Throwable {
        assertNull(WordUtils.wrap(null, 5));
    }

    // wrap(String,int) empty input branch -> ""
    @Test
    public void testWrap_emptyInput_returnsEmpty() throws Throwable {
        assertEquals("", WordUtils.wrap("", 5));
    }

    // wrap(String,int) delegates to 4-arg overload with default newline and wrapLongWords=false
    @Test
    public void testWrap_twoArgOverload_delegatesCorrectly() throws Throwable {
        String ls = SystemUtils.LINE_SEPARATOR;
        assertEquals("ab" + ls + "cd", WordUtils.wrap("ab cd", 2));
    }

    // newLineStr == null branch uses SystemUtils.LINE_SEPARATOR, multiple loop iterations, normal space-wrap branch
    @Test
    public void testWrap_defaultNewLineStr_usesSystemLineSeparator() throws Throwable {
        String ls = SystemUtils.LINE_SEPARATOR;
        String expected = "1" + ls + "2" + ls + "3" + ls + "4" + ls + "5";
        assertEquals(expected, WordUtils.wrap("1 2 3 4 5", 2, null, false));
    }

    // charAt(offset) == ' ' branch: consecutive spaces get silently skipped (continue)
    @Test
    public void testWrap_leadingSpaceAtOffset_skipsSpace() throws Throwable {
        assertEquals("a|b", WordUtils.wrap("a  b", 1, "|", false));
    }

    // wrapLongWords=true branch: long word with no spaces gets force-wrapped repeatedly
    @Test
    public void testWrap_wrapLongWordsTrue_breaksLongWord() throws Throwable {
        assertEquals("abc-def-gh", WordUtils.wrap("abcdefgh", 3, "-", true));
    }

    // wrapLongWords=false branch with a space found beyond the limit -> wraps at that space
    @Test
    public void testWrap_wrapLongWordsFalse_withSpaceBeyondLimit_wrapsAtSpace() throws Throwable {
        assertEquals("abcdefgh-ij", WordUtils.wrap("abcdefgh ij", 3, "-", false));
    }

    // wrapLongWords=false branch with no space anywhere -> word extends beyond limit unwrapped
    @Test
    public void testWrap_wrapLongWordsFalse_noSpaceFound_returnsUnwrapped() throws Throwable {
        assertEquals("abcdefgh", WordUtils.wrap("abcdefgh", 3, "-", false));
    }

    // wrapLength < 1 is clamped to 1 per Javadoc contract
    @Test
    public void testWrap_wrapLengthLessThanOne_treatedAsOne() throws Throwable {
        assertEquals("ab", WordUtils.wrap("ab", 0, "-", false));
    }

    // capitalize(String) null input
    @Test
    public void testCapitalize_nullInput_returnsNull() throws Throwable {
        assertNull(WordUtils.capitalize(null));
    }

    // capitalize(String) empty input
    @Test
    public void testCapitalize_emptyInput_returnsEmpty() throws Throwable {
        assertEquals("", WordUtils.capitalize(""));
    }

    // capitalize default whitespace delimiters, mixed case, per Javadoc example
    @Test
    public void testCapitalize_defaultDelimiters_capitalizesEachWord() throws Throwable {
        assertEquals("I Am FINE", WordUtils.capitalize("i am FINE"));
    }

    // capitalize with empty delimiters array returns string unchanged (delimLen==0 branch)
    @Test
    public void testCapitalize_emptyDelimitersArray_returnsUnchanged() throws Throwable {
        assertEquals("abc def", WordUtils.capitalize("abc def", new char[0]));
    }

    // capitalize with custom delimiter '.' per Javadoc example
    @Test
    public void testCapitalize_customDelimiters_capitalizesAfterDelimiter() throws Throwable {
        assertEquals("I aM.Fine", WordUtils.capitalize("i aM.fine", new char[] {'.'}));
    }

    // capitalizeFully(String) null input
    @Test
    public void testCapitalizeFully_nullInput_returnsNull() throws Throwable {
        assertNull(WordUtils.capitalizeFully(null));
    }

    // capitalizeFully default whitespace delimiters lowercases rest of each word
    @Test
    public void testCapitalizeFully_defaultDelimiters_capitalizesAndLowercasesRest() throws Throwable {
        assertEquals("I Am Fine", WordUtils.capitalizeFully("i am FINE"));
    }

    // capitalizeFully with empty delimiters array returns string unchanged
    @Test
    public void testCapitalizeFully_emptyDelimitersArray_returnsUnchanged() throws Throwable {
        assertEquals("abc def", WordUtils.capitalizeFully("abc def", new char[0]));
    }

    // capitalizeFully with custom delimiter '.' per Javadoc example
    @Test
    public void testCapitalizeFully_customDelimiters_lowercasesExceptAfterDelimiter() throws Throwable {
        assertEquals("I am.Fine", WordUtils.capitalizeFully("i aM.fine", new char[] {'.'}));
    }

    // uncapitalize(String) null input
    @Test
    public void testUncapitalize_nullInput_returnsNull() throws Throwable {
        assertNull(WordUtils.uncapitalize(null));
    }

    // uncapitalize default whitespace delimiters per Javadoc example
    @Test
    public void testUncapitalize_defaultDelimiters_uncapitalizesEachWord() throws Throwable {
        assertEquals("i am fINE", WordUtils.uncapitalize("I Am FINE"));
    }

    // uncapitalize with empty delimiters array returns string unchanged
    @Test
    public void testUncapitalize_emptyDelimitersArray_returnsUnchanged() throws Throwable {
        assertEquals("ABC DEF", WordUtils.uncapitalize("ABC DEF", new char[0]));
    }

    // uncapitalize with custom delimiter '.' per Javadoc example
    @Test
    public void testUncapitalize_customDelimiters_uncapitalizesAfterDelimiter() throws Throwable {
        assertEquals("i AM.fINE", WordUtils.uncapitalize("I AM.FINE", new char[] {'.'}));
    }

    // swapCase null input
    @Test
    public void testSwapCase_nullInput_returnsNull() throws Throwable {
        assertNull(WordUtils.swapCase(null));
    }

    // swapCase empty input
    @Test
    public void testSwapCase_emptyInput_returnsEmpty() throws Throwable {
        assertEquals("", WordUtils.swapCase(""));
    }

    // swapCase per Javadoc example covers upper->lower, lower-after-whitespace->title, other lower->upper
    @Test
    public void testSwapCase_mixedCaseSentence_swapsPerWordRules() throws Throwable {
        assertEquals("tHE DOG HAS A bone", WordUtils.swapCase("The dog has a BONE"));
    }

    // initials(String) null input
    @Test
    public void testInitials_nullInput_returnsNull() throws Throwable {
        assertNull(WordUtils.initials(null));
    }

    // initials(String) empty input
    @Test
    public void testInitials_emptyInput_returnsEmpty() throws Throwable {
        assertEquals("", WordUtils.initials(""));
    }

    // initials default whitespace delimiters, multi-word and dotted-word per Javadoc examples
    @Test
    public void testInitials_defaultWhitespaceDelimiters_returnsFirstLetters() throws Throwable {
        assertEquals("BJL", WordUtils.initials("Ben John Lee"));
        assertEquals("BJ", WordUtils.initials("Ben J.Lee"));
    }

    // initials with custom delimiters space and dot per Javadoc example
    @Test
    public void testInitials_customDelimiters_returnsFirstLetters() throws Throwable {
        assertEquals("BJL", WordUtils.initials("Ben J.Lee", new char[] {' ', '.'}));
    }

    // initials with empty delimiters array returns empty string regardless of input
    @Test
    public void testInitials_emptyDelimitersArray_returnsEmptyString() throws Throwable {
        assertEquals("", WordUtils.initials("Ben John Lee", new char[0]));
    }

    // abbreviate null input
    @Test
    public void testAbbreviate_nullInput_returnsNull() throws Throwable {
        assertNull(WordUtils.abbreviate(null, 0, 10, "-"));
    }

    // abbreviate empty input returns empty string
    @Test
    public void testAbbreviate_emptyInput_returnsEmpty() throws Throwable {
        assertEquals("", WordUtils.abbreviate("", 0, 10, "-"));
    }

    // index == -1 (no space found) and upper equals string length -> unabbreviated, no append
    @Test
    public void testAbbreviate_noSpaceFound_upperEqualsLength_returnsUnabbreviated() throws Throwable {
        assertEquals("abcdefghij", WordUtils.abbreviate("abcdefghij", 0, 10, "..."));
    }

    // index == -1 (no space found) and upper less than length -> truncated with appendToEnd
    @Test
    public void testAbbreviate_noSpaceFound_upperLessThanLength_abbreviatesAndAppends() throws Throwable {
        assertEquals("abcde...", WordUtils.abbreviate("abcdefghij", 0, 5, "..."));
    }

    // index found and index <= upper -> abbreviate at that space, append appendToEnd
    @Test
    public void testAbbreviate_spaceFoundWithinUpper_abbreviatesAtSpace() throws Throwable {
        assertEquals("Now...", WordUtils.abbreviate("Now is the time", 0, 10, "..."));
    }

    // index found but index > upper -> abbreviate forcibly at upper, append appendToEnd
    @Test
    public void testAbbreviate_spaceFoundBeyondUpper_abbreviatesAtUpper() throws Throwable {
        assertEquals("No...", WordUtils.abbreviate("Now is the time", 0, 2, "..."));
    }

    // upper == -1 is treated as no limit (set to string length) -> full string, no truncation
    @Test
    public void testAbbreviate_upperMinusOne_treatedAsStringLength() throws Throwable {
        assertEquals("abcdef", WordUtils.abbreviate("abcdef", 0, -1, "X"));
    }

    // upper < lower is raised to lower per Javadoc, then truncated/appended accordingly
    @Test
    public void testAbbreviate_upperLessThanLower_adjustedToLower() throws Throwable {
        assertEquals("abcdefgh-", WordUtils.abbreviate("abcdefghij", 8, 3, "-"));
    }

    // null appendToEnd is treated as empty string (StringUtils.defaultString branch)
    @Test
    public void testAbbreviate_nullAppendToEnd_treatedAsEmptyString() throws Throwable {
        assertEquals("abcde", WordUtils.abbreviate("abcdefghij", 0, 5, null));
    }

    // Javadoc: "if the lower value is greater than the length of the string, set to the length
    // of the string". Bug: the code never clamps lower, only upper, which on the buggy version
    // causes substring(0, upper) with upper computed beyond the string length and throws
    // StringIndexOutOfBoundsException instead of returning the unabbreviated string.
    @Test
    public void testAbbreviate_lowerGreaterThanStringLength_shouldClampAndNotThrow() throws Throwable {
        assertEquals("0123456789", WordUtils.abbreviate("0123456789", 15, -1, "-"));
    }

    // Same contract violation as above but with an explicit (large) upper limit instead of -1.
    @Test
    public void testAbbreviate_lowerGreaterThanStringLengthWithExplicitUpper_shouldClampAndNotThrow() throws Throwable {
        assertEquals("0123456789", WordUtils.abbreviate("0123456789", 12, 20, "X"));
    }
}
