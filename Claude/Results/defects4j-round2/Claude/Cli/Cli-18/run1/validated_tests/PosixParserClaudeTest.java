package org.apache.commons.cli;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PosixParserClaudeTest {

    private PosixParser parser;

    @Before
    public void setUp() throws Throwable {
        parser = new PosixParser();
    }

    // covers: empty arguments array -> no tokens produced
    @Test
    public void testFlatten_emptyArguments_returnsEmptyArray() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {}, false);
        assertEquals(0, result.length);
    }

    // covers: "--" token with '=' present -> split into key/value tokens
    @Test
    public void testFlatten_doubleDashWithEquals_splitsKeyAndValue() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--foo=bar" }, false);
        assertEquals(2, result.length);
        assertEquals("--foo", result[0]);
        assertEquals("bar", result[1]);
    }

    // covers: "--" token with '=' at the very end -> value is empty string
    @Test
    public void testFlatten_doubleDashWithEqualsAtEnd_splitsWithEmptyValue() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--foo=" }, false);
        assertEquals(2, result.length);
        assertEquals("--foo", result[0]);
        assertEquals("", result[1]);
    }

    // covers: "--" token without '=' -> added as single token
    @Test
    public void testFlatten_doubleDashWithoutEquals_addsAsSingleToken() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--foo" }, false);
        assertEquals(1, result.length);
        assertEquals("--foo", result[0]);
    }

    // covers: exact "--" token -> added directly (no '=' present)
    @Test
    public void testFlatten_exactDoubleDashToken_addsAsIs() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--" }, false);
        assertEquals(1, result.length);
        assertEquals("--", result[0]);
    }

    // covers: "-" single hyphen -> processSingleHyphen adds literal "-"
    @Test
    public void testFlatten_singleHyphenToken_addsHyphenLiteral() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "-" }, false);
        assertEquals(1, result.length);
        assertEquals("-", result[0]);
    }

    // covers: two-char token matching registered option -> sets current option, adds token
    @Test
    public void testFlatten_twoCharValidOption_setsCurrentOptionAndAddsToken() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-a" }, false);
        assertEquals(1, result.length);
        assertEquals("-a", result[0]);
    }



    // covers: two-char unregistered option, stopAtNonOption false -> token ignored entirely
    @Test
    public void testFlatten_twoCharInvalidOption_stopAtNonOptionFalse_ignoresTokenAndKeepsNext() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "-x", "foo" }, false);
        assertEquals(1, result.length);
        assertEquals("foo", result[0]);
    }

    // covers: plain non-option token, stopAtNonOption false -> added directly without processing
    @Test
    public void testFlatten_plainNonOption_stopAtNonOptionFalse_addsTokenDirectly() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "file1", "file2" }, false);
        assertEquals(2, result.length);
        assertEquals("file1", result[0]);
        assertEquals("file2", result[1]);
    }

    // covers: process() with no current option -> adds "--" marker then value, then gobbles rest
    @Test
    public void testFlatten_plainNonOption_stopAtNonOptionTrue_noCurrentOption_addsDoubleDashMarker() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "file1", "file2" }, true);
        assertEquals(3, result.length);
        assertEquals("--", result[0]);
        assertEquals("file1", result[1]);
        assertEquals("file2", result[2]);
    }

    // covers: process() with current option that hasArg -> value consumed, currentOption reset to null
    @Test
    public void testFlatten_plainNonOption_stopAtNonOptionTrue_currentOptionHasArg_consumesValueAndResetsOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-a", "value1" }, true);
        assertEquals(2, result.length);
        assertEquals("-a", result[0]);
        assertEquals("value1", result[1]);
    }

    // covers: after option's arg is consumed, subsequent plain token triggers "--" marker again (currentOption null)
    @Test
    public void testFlatten_plainNonOption_stopAtNonOptionTrue_afterOptionConsumed_eatsRemainingWithMarker() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-a", "value1", "extra" }, true);
        assertEquals(4, result.length);
        assertEquals("-a", result[0]);
        assertEquals("value1", result[1]);
        assertEquals("--", result[2]);
        assertEquals("extra", result[3]);
    }

    // covers: two-char option with hasArg true followed by plain value, stopAtNonOption false path (else branch)
    @Test
    public void testFlatten_twoCharOptionWithArg_stopAtNonOptionFalse_valueAddedDirectly() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-a", "val" }, false);
        assertEquals(2, result.length);
        assertEquals("-a", result[0]);
        assertEquals("val", result[1]);
    }

    // covers: burstToken loop where every character is a valid no-arg option -> loop runs to completion, no break
    @Test
    public void testBurstToken_allCharsValidNoArg_addsEachCharAsSeparateToken() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "opt a"));
        options.addOption(new Option("b", false, "opt b"));
        options.addOption(new Option("c", false, "opt c"));
        String[] result = parser.flatten(options, new String[] { "-abc" }, false);
        assertEquals(3, result.length);
        assertEquals("-a", result[0]);
        assertEquals("-b", result[1]);
        assertEquals("-c", result[2]);
    }

    // covers: burstToken char with hasArg true and remaining characters present -> remainder added as value, breaks
    @Test
    public void testBurstToken_charWithArgAndRemainingChars_addsRemainderAsValueAndStops() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-afoo" }, false);
        assertEquals(2, result.length);
        assertEquals("-a", result[0]);
        assertEquals("foo", result[1]);
    }

    // covers: burstToken char with hasArg true but no remaining chars in token -> no remainder added, loop continues
    @Test
    public void testBurstToken_charWithArgNoRemainingChars_noRemainderAddedContinuesLoop() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("b", false, "opt b"));
        options.addOption(new Option("a", true, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-ba" }, false);
        assertEquals(2, result.length);
        assertEquals("-b", result[0]);
        assertEquals("-a", result[1]);
    }

    // covers: burstToken invalid char with stopAtNonOption true -> delegates remainder to process()
    @Test
    public void testBurstToken_invalidCharWithStopAtNonOption_delegatesToProcess() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-ax" }, true);
        assertEquals(3, result.length);
        assertEquals("-a", result[0]);
        assertEquals("--", result[1]);
        assertEquals("x", result[2]);
    }





    // covers: sequential processing - single hyphen followed by a valid two-char option token
    @Test
    public void testFlatten_singleHyphenFollowedByValidOption_bothTokensAddedIndependently() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-", "-a" }, false);
        assertEquals(2, result.length);
        assertEquals("-", result[0]);
        assertEquals("-a", result[1]);
    }

    // covers: mixed sequence combining double-dash, plain option, and plain non-option token (stopAtNonOption false)
    @Test
    public void testFlatten_mixedArguments_multipleOptionsAndPlainArgs() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "opt a"));
        String[] result = parser.flatten(options, new String[] { "-a", "--foo=bar", "plain" }, false);
        assertEquals(4, result.length);
        assertEquals("-a", result[0]);
        assertEquals("--foo", result[1]);
        assertEquals("bar", result[2]);
        assertEquals("plain", result[3]);
    }

    // covers: burstToken invoked on a fresh parser instance reusing internal state correctly after init()
    @Test
    public void testFlatten_calledTwiceOnSameParser_resetsStateBetweenCalls() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "opt a"));
        parser.flatten(options, new String[] { "-a", "leftover" }, true);
        String[] second = parser.flatten(options, new String[] { "plain" }, false);
        assertEquals(1, second.length);
        assertEquals("plain", second[0]);
    }


}
