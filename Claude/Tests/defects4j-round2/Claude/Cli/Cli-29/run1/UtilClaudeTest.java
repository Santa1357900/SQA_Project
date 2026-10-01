package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilClaudeTest
{
    // stripLeadingHyphens: str == null branch -> returns null
    @Test
    public void testStripLeadingHyphens_null_returnsNull() throws Throwable {
        assertNull(Util.stripLeadingHyphens(null));
    }

    // stripLeadingHyphens: empty string, neither startsWith branch triggers
    @Test
    public void testStripLeadingHyphens_emptyString_returnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens(""));
    }

    // stripLeadingHyphens: exactly "-" triggers single-hyphen branch, result empty
    @Test
    public void testStripLeadingHyphens_singleHyphen_returnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens("-"));
    }

    // stripLeadingHyphens: exactly "--" triggers double-hyphen branch, result empty
    @Test
    public void testStripLeadingHyphens_doubleHyphen_returnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens("--"));
    }

    // stripLeadingHyphens: "-a" triggers single-hyphen branch (not double)
    @Test
    public void testStripLeadingHyphens_singleHyphenWithOption_returnsOption() throws Throwable {
        assertEquals("a", Util.stripLeadingHyphens("-a"));
    }

    // stripLeadingHyphens: "--a" triggers double-hyphen branch
    @Test
    public void testStripLeadingHyphens_doubleHyphenWithOption_returnsOption() throws Throwable {
        assertEquals("a", Util.stripLeadingHyphens("--a"));
    }

    // stripLeadingHyphens: no leading hyphen, returns str unchanged (falls through both ifs)
    @Test
    public void testStripLeadingHyphens_noHyphen_returnsUnchanged() throws Throwable {
        assertEquals("abc", Util.stripLeadingHyphens("abc"));
    }

    // stripLeadingHyphens: only leading hyphen stripped, internal hyphen kept
    @Test
    public void testStripLeadingHyphens_hyphenInMiddle_onlyLeadingStripped() throws Throwable {
        assertEquals("foo-bar", Util.stripLeadingHyphens("--foo-bar"));
    }

    // stripLeadingHyphens: single non-hyphen char, returns unchanged
    @Test
    public void testStripLeadingHyphens_singleCharNonHyphen_returnsUnchanged() throws Throwable {
        assertEquals("a", Util.stripLeadingHyphens("a"));
    }

    // stripLeadingAndTrailingQuotes: Javadoc example, both leading and trailing stripped
    @Test
    public void testStripLeadingAndTrailingQuotes_javadocExample() throws Throwable {
        assertEquals("one two", Util.stripLeadingAndTrailingQuotes("\"one two\""));
    }

    // stripLeadingAndTrailingQuotes: simple word with both quotes
    @Test
    public void testStripLeadingAndTrailingQuotes_bothQuotes_simpleWord() throws Throwable {
        assertEquals("foo", Util.stripLeadingAndTrailingQuotes("\"foo\""));
    }

    // stripLeadingAndTrailingQuotes: no quotes present, neither if triggers
    @Test
    public void testStripLeadingAndTrailingQuotes_noQuotes_returnsUnchanged() throws Throwable {
        assertEquals("foo", Util.stripLeadingAndTrailingQuotes("foo"));
    }

    // stripLeadingAndTrailingQuotes: only leading quote present, only first if triggers
    @Test
    public void testStripLeadingAndTrailingQuotes_onlyLeadingQuote_stripsLeading() throws Throwable {
        assertEquals("foo", Util.stripLeadingAndTrailingQuotes("\"foo"));
    }

    // stripLeadingAndTrailingQuotes: only trailing quote present, only second if triggers
    @Test
    public void testStripLeadingAndTrailingQuotes_onlyTrailingQuote_stripsTrailing() throws Throwable {
        assertEquals("foo", Util.stripLeadingAndTrailingQuotes("foo\""));
    }

    // stripLeadingAndTrailingQuotes: two-char quoted empty string -> empty result
    @Test
    public void testStripLeadingAndTrailingQuotes_emptyQuotedString_returnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes("\"\""));
    }

    // stripLeadingAndTrailingQuotes: single quote char, leading strip consumes it, trailing check on empty
    @Test
    public void testStripLeadingAndTrailingQuotes_singleQuoteChar_returnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes("\""));
    }

    // stripLeadingAndTrailingQuotes: empty string input, neither branch triggers
    @Test
    public void testStripLeadingAndTrailingQuotes_emptyString_returnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes(""));
    }

    // stripLeadingAndTrailingQuotes: quotes not at boundaries (surrounded by spaces) -> unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_surroundedBySpaces_notStripped() throws Throwable {
        assertEquals(" \"foo\" ", Util.stripLeadingAndTrailingQuotes(" \"foo\" "));
    }

    // stripLeadingAndTrailingQuotes: double quote on each side strips only one from each side
    @Test
    public void testStripLeadingAndTrailingQuotes_doubleQuotesEachSide_stripsOneEach() throws Throwable {
        assertEquals("\"foo\"", Util.stripLeadingAndTrailingQuotes("\"\"foo\"\""));
    }

    // stripLeadingAndTrailingQuotes: quote embedded in middle, not at start/end -> unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_quoteInMiddle_returnsUnchanged() throws Throwable {
        assertEquals("foo\"bar", Util.stripLeadingAndTrailingQuotes("foo\"bar"));
    }

    // stripLeadingAndTrailingQuotes: null input causes NullPointerException (no null guard in method)
    @Test
    public void testStripLeadingAndTrailingQuotes_nullInput_throwsNPE() throws Throwable {
        try
        {
            Util.stripLeadingAndTrailingQuotes(null);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected)
        {
        }
    }
}
