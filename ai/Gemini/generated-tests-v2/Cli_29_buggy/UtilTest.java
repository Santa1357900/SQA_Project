package org.apache.commons.cli;

import junit.framework.TestCase;

public class UtilTest extends TestCase {

    public void testStripLeadingHyphensNull() throws Throwable {
        assertNull(Util.stripLeadingHyphens(null));
    }

    public void testStripLeadingHyphensDouble() throws Throwable {
        assertEquals("foo", Util.stripLeadingHyphens("--foo"));
    }

    public void testStripLeadingHyphensSingle() throws Throwable {
        assertEquals("foo", Util.stripLeadingHyphens("-foo"));
    }

    public void testStripLeadingHyphensNone() throws Throwable {
        assertEquals("foo", Util.stripLeadingHyphens("foo"));
    }

    public void testStripLeadingHyphensEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens(""));
    }

    public void testStripLeadingHyphensOnlyHyphen() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens("-"));
    }

    public void testStripLeadingHyphensOnlyDoubleHyphen() throws Throwable {
        assertEquals("", Util.stripLeadingHyphens("--"));
    }

    public void testStripLeadingAndTrailingQuotesBoth() throws Throwable {
        assertEquals("foo", Util.stripLeadingAndTrailingQuotes("\"foo\""));
    }

    public void testStripLeadingAndTrailingQuotesLeadingOnly() throws Throwable {
        assertEquals("foo\"", Util.stripLeadingAndTrailingQuotes("\"foo\""));
    }

    public void testStripLeadingAndTrailingQuotesTrailingOnly() throws Throwable {
        assertEquals("\"foo", Util.stripLeadingAndTrailingQuotes("\"foo"));
    }

    public void testStripLeadingAndTrailingQuotesNone() throws Throwable {
        assertEquals("foo", Util.stripLeadingAndTrailingQuotes("foo"));
    }

    public void testStripLeadingAndTrailingQuotesEmpty() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes(""));
    }

    public void testStripLeadingAndTrailingQuotesSingleQuote() throws Throwable {
        assertEquals("", Util.stripLeadingAndTrailingQuotes("\"\""));
    }
}