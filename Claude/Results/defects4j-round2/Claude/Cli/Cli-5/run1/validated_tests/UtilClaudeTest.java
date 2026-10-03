package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilClaudeTest {

    // --- stripLeadingHyphens ---



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



    // two quote characters form a valid empty quoted string, stripped to empty string
    @Test
    public void testStripLeadingAndTrailingQuotes_TwoQuoteChars_ReturnsEmptyString() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes("\"\""));
    }



    // string with a space inside paired quotes, matching javadoc description precisely
    @Test
    public void testStripLeadingAndTrailingQuotes_QuotedStringWithSpace_StripsBoth() throws Throwable {
        assertEquals("hello world", Util.stripLeadingAndTrailingQuotes("\"hello world\""));
    }





    // single non-quote character: neither branch entered, unchanged
    @Test
    public void testStripLeadingAndTrailingQuotes_SingleNonQuoteChar_ReturnsUnchanged() throws Throwable {
        assertEquals("a", Util.stripLeadingAndTrailingQuotes("a"));
    }
}
