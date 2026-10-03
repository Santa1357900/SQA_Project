package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilTest {

    @Test
    public void testStripLeadingHyphensDouble() throws Throwable {
        String result = Util.stripLeadingHyphens("--foo");
        assertEquals("foo", result);
    }

    @Test
    public void testStripLeadingHyphensSingle() throws Throwable {
        String result = Util.stripLeadingHyphens("-foo");
        assertEquals("foo", result);
    }

    @Test
    public void testStripLeadingHyphensNone() throws Throwable {
        String result = Util.stripLeadingHyphens("foo");
        assertEquals("foo", result);
    }

    @Test
    public void testStripLeadingHyphensEmpty() throws Throwable {
        String result = Util.stripLeadingHyphens("");
        assertEquals("", result);
    }

    @Test
    public void testStripLeadingHyphensNull() throws Throwable {
        try {
            Util.stripLeadingHyphens(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testStripLeadingAndTrailingQuotesBoth() throws Throwable {
        String result = Util.stripLeadingAndTrailingQuotes("\"one two\"");
        assertEquals("one two", result);
    }

    @Test
    public void testStripLeadingAndTrailingQuotesLeadingOnly() throws Throwable {
        String result = Util.stripLeadingAndTrailingQuotes("\"one two");
        assertEquals("one two", result);
    }

    @Test
    public void testStripLeadingAndTrailingQuotesTrailingOnly() throws Throwable {
        String result = Util.stripLeadingAndTrailingQuotes("one two\"");
        assertEquals("one two", result);
    }

    @Test
    public void testStripLeadingAndTrailingQuotesNone() throws Throwable {
        String result = Util.stripLeadingAndTrailingQuotes("one two");
        assertEquals("one two", result);
    }

    @Test
    public void testStripLeadingAndTrailingQuotesSingleQuote() throws Throwable {
        String result = Util.stripLeadingAndTrailingQuotes("\"");
        assertEquals("", result);
    }

    @Test
    public void testStripLeadingAndTrailingQuotesEmpty() throws Throwable {
        String result = Util.stripLeadingAndTrailingQuotes("");
        assertEquals("", result);
    }

    @Test
    public void testStripLeadingAndTrailingQuotesNull() throws Throwable {
        try {
            Util.stripLeadingAndTrailingQuotes(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }
}