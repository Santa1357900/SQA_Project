package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilClaudeTest {

    // --- stripLeadingHyphens ---

    // null input: String.startsWith on null receiver throws NullPointerException
    @Test(expected = NullPointerException.class)
    public void testStripLeadingHyphens_NullInput_ThrowsNPE() throws Throwable {
        Util.stripLeadingHyphens(null);
    }

    // empty string: neither branch taken, returned unchanged
    @Test
    public void testStripLeadingHyphens_EmptyString_ReturnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens(""));
    }

    // single "-" : else-if branch, substring(1,1) -> empty string
    @Test
    public void testStripLeadingHyphens_SingleHyphen_ReturnsEmptyString() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens("-"));
    }

    // exact "--" : if branch, substring(2,2) -> empty string
    @Test
    public void testStripLeadingHyphens_DoubleHyphen_ReturnsEmptyString() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens("--"));
    }

    // "-a" : else-if branch, strips single leading hyphen
    @Test
    public void testStripLeadingHyphens_SingleHyphenPrefix_StripsOneHyphen() throws Throwable {
        assertEquals("a", Util.stripLeadingHyphens("-a"));
    }

    // "--a" : if branch, strips leading double hyphen
    @Test
    public void testStripLeadingHyphens_DoubleHyphenPrefix_StripsTwoHyphens() throws Throwable {
        assertEquals("a", Util.stripLeadingHyphens("--a"));
    }

    // "---a" : if branch matches "--" only, leaves one hyphen remaining
    @Test
    public void testStripLeadingHyphens_TripleHyphenPrefix_StripsOnlyLeadingTwo() throws Throwable {
        assertEquals("-a", Util.stripLeadingHyphens("---a"));
    }

    // no hyphen prefix: falls through to final return str unchanged
    @Test
    public void testStripLeadingHyphens_NoHyphenPrefix_ReturnsUnchanged() throws Throwable {
        assertEquals("abc", Util.stripLeadingHyphens("abc"));
    }

    // hyphen in middle (not leading): unchanged
    @Test
    public void testStripLeadingHyphens_HyphenInMiddle_ReturnsUnchanged() throws Throwable {
        assertEquals("a-b", Util.stripLeadingHyphens("a-b"));
    }

    // trailing hyphen only, not leading: unchanged
    @Test
    public void testStripLeadingHyphens_TrailingHyphenOnly_ReturnsUnchanged() throws Throwable {
        assertEquals("opt-", Util.stripLeadingHyphens("opt-"));
    }

    // --- stripLeadingAndTrailingQuotes ---

    // null input: startsWith on null receiver throws NullPointerException
    @Test(expected = NullPointerException.class)
    public void testStripLeadingAndTrailingQuotes_NullInput_ThrowsNPE() throws Throwable {
        Util.stripLeadingAndTrailingQuotes(null);
    }

    // paired leading/trailing quotes as in javadoc example: both are stripped
    @Test
    public void testStripLeadingAndTrailingQuotes_PairedQuotes_StripsBoth() throws Throwable {
        assertEquals("one two", Util.stripLeadingAndTrailingQuotes("\"one two\""));
    }

    // only a leading quote (no trailing quote): a properly-quoted string requires
    // BOTH a leading and trailing quote; with only one side present it must stay unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_OnlyLeadingQuote_ReturnsUnchanged() throws Throwable {
        assertEquals("\"abc", Util.stripLeadingAndTrailingQuotes("\"abc"));
    }

    // only a trailing quote (no leading quote): must stay unchanged for the same reason
    @Test
    public void testStripLeadingAndTrailingQuotes_OnlyTrailingQuote_ReturnsUnchanged() throws Throwable {
        assertEquals("abc\"", Util.stripLeadingAndTrailingQuotes("abc\""));
    }

    // no quotes at all: unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_NoQuotes_ReturnsUnchanged() throws Throwable {
        assertEquals("abc", Util.stripLeadingAndTrailingQuotes("abc"));
    }

    // empty string: unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_EmptyString_ReturnsEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes(""));
    }

    // a single quote character is not a valid "leading and trailing" pair (too short
    // to have both a distinct leading and trailing quote), must remain unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_SingleQuoteChar_ReturnsUnchanged() throws Throwable {
        assertEquals("\"", Util.stripLeadingAndTrailingQuotes("\""));
    }

    // two quote characters form a valid empty quoted string, stripped to empty string
    @Test
    public void testStripLeadingAndTrailingQuotes_TwoQuoteChars_ReturnsEmptyString() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes("\"\""));
    }

    // an internal quote between the leading and trailing quotes means this is not a
    // simple quoted token, so the original string should be returned unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_InternalQuote_ReturnsUnchanged() throws Throwable {
        assertEquals("\"a\"b\"", Util.stripLeadingAndTrailingQuotes("\"a\"b\""));
    }

    // string with a space inside paired quotes, matching javadoc description precisely
    @Test
    public void testStripLeadingAndTrailingQuotes_QuotedStringWithSpace_StripsBoth() throws Throwable {
        assertEquals("hello world", Util.stripLeadingAndTrailingQuotes("\"hello world\""));
    }

    // multi-char string with leading quote only and longer content, still unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_LeadingQuoteMultiCharNoTrailing_ReturnsUnchanged() throws Throwable {
        assertEquals("\"multi char", Util.stripLeadingAndTrailingQuotes("\"multi char"));
    }

    // multi-char string with trailing quote only and longer content, still unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_TrailingQuoteMultiCharNoLeading_ReturnsUnchanged() throws Throwable {
        assertEquals("multi char\"", Util.stripLeadingAndTrailingQuotes("multi char\""));
    }

    // single non-quote character: neither branch entered, unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_SingleNonQuoteChar_ReturnsUnchanged() throws Throwable {
        assertEquals("a", Util.stripLeadingAndTrailingQuotes("a"));
    }
}
