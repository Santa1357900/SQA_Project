package org.apache.commons.cli;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class PosixParserClaudeTest
{
    private PosixParser parser;

    @Before
    public void setUp() throws Throwable
    {
        parser = new PosixParser();
    }

    // covers: token.startsWith("--") && indexOf('=') != -1 -> split into key/value
    @Test
    public void testFlatten_doubleDashWithEquals_splitsKeyValue() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"--foo=bar"}, false);
        assertArrayEquals(new String[] {"--foo", "bar"}, result);
    }

    // covers: token.startsWith("--") && indexOf('=') == -1 -> added as single token
    @Test
    public void testFlatten_doubleDashWithoutEquals_addsAsSingleToken() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"--verbose"}, false);
        assertArrayEquals(new String[] {"--verbose"}, result);
    }

    // covers: '=' located right after "--" -> empty key suffix edge case
    @Test
    public void testFlatten_doubleDashEqualsAtStart_splitsEmptyKeyPrefix() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"--=value"}, false);
        assertArrayEquals(new String[] {"--", "value"}, result);
    }

    // covers: multiple '=' chars -> indexOf uses the first occurrence only
    @Test
    public void testFlatten_doubleDashMultipleEquals_usesFirstOccurrence() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"--key=val=ue"}, false);
        assertArrayEquals(new String[] {"--key", "val=ue"}, result);
    }

    // covers: empty arguments array -> loop runs zero times
    @Test
    public void testFlatten_emptyArguments_returnsEmptyArray() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[0], false);
        assertEquals(0, result.length);
    }

    // covers: "-".equals(token) -> processSingleHyphen adds the hyphen directly
    @Test
    public void testFlatten_singleHyphen_addedDirectly() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"-"}, false);
        assertArrayEquals(new String[] {"-"}, result);
    }

    // covers: tokenLength == 2 && options.hasOption(token) true -> add token, set currentOption
    @Test
    public void testFlatten_twoCharKnownOption_addsTokenSetsCurrentOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "desc"));
        String[] result = parser.flatten(options, new String[] {"-a"}, false);
        assertArrayEquals(new String[] {"-a"}, result);
    }

    // covers: tokenLength == 2, unknown option, stopAtNonOption false -> token ignored
    @Test
    public void testFlatten_twoCharUnknownOption_stopAtNonOptionFalse_ignoresToken() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("b", false, "desc"));
        String[] result = parser.flatten(options, new String[] {"-x"}, false);
        assertEquals(0, result.length);
    }

    // covers: unknown two-char option, stopAtNonOption true -> eatTheRest gobbles following tokens raw
    @Test
    public void testFlatten_twoCharUnknownOption_stopAtNonOptionTrue_eatsRest() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"-x", "foo", "bar"}, true);
        assertArrayEquals(new String[] {"foo", "bar"}, result);
    }

    // covers: same as above but no remaining tokens -> gobble loop runs zero times
    @Test
    public void testFlatten_twoCharUnknownOption_stopAtNonOptionTrue_noRemainingArgs() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"-x"}, true);
        assertEquals(0, result.length);
    }

    // covers: tokenLength > 2, whole token not a registered option -> falls through to burstToken with known chars
    @Test
    public void testFlatten_longTokenNoMatch_burstsKnownChars() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "desc"));
        options.addOption(new Option("b", false, "desc"));
        String[] result = parser.flatten(options, new String[] {"-ab"}, false);
        assertArrayEquals(new String[] {"-a", "-b"}, result);
    }

    // covers: burstToken - matched option hasArg() true, remaining chars present -> remainder added as value, then break
    @Test
    public void testFlatten_burstToken_argOptionConsumesRemainder() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "desc"));
        String[] result = parser.flatten(options, new String[] {"-axyz"}, false);
        assertArrayEquals(new String[] {"-a", "xyz"}, result);
    }

    // covers: burstToken - matched option hasArg() true but token.length()==(i+1) -> no remainder added
    @Test
    public void testFlatten_burstToken_argOptionAtEndNoRemainder() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "desc"));
        options.addOption(new Option("b", true, "desc"));
        String[] result = parser.flatten(options, new String[] {"-ab"}, false);
        assertArrayEquals(new String[] {"-a", "-b"}, result);
    }

    // covers: burstToken - unknown char with stopAtNonOption true -> delegates to process(remainder)
    @Test
    public void testFlatten_burstToken_unknownCharStopAtNonOptionTrue_processesRemainderAsNonOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "desc"));
        String[] result = parser.flatten(options, new String[] {"-az", "extra"}, true);
        assertArrayEquals(new String[] {"-a", "--", "z", "extra"}, result);
    }

    // covers: burstToken - unknown char, stopAtNonOption false -> per Javadoc must add the char prefixed with "-"
    // (regression test for the flatten/burstToken contract mismatch)
    @Test
    public void testFlatten_burstToken_unknownCharStopAtNonOptionFalse_addsCharWithDashPrefix() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "desc"));
        String[] result = parser.flatten(options, new String[] {"-ab"}, false);
        assertArrayEquals(new String[] {"-a", "-b"}, result);
    }

    // covers: burstToken - unknown char is the very first character (i==1), stopAtNonOption false
    @Test
    public void testFlatten_burstToken_firstCharUnknownStopAtNonOptionFalse_addsCharWithDashPrefix() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"-xy"}, false);
        assertArrayEquals(new String[] {"-x"}, result);
    }

    // covers: else branch (not starting with "-"), stopAtNonOption false -> added directly
    @Test
    public void testFlatten_plainToken_stopAtNonOptionFalse_addsDirectly() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"plainArg"}, false);
        assertArrayEquals(new String[] {"plainArg"}, result);
    }

    // covers: process() - currentOption is null -> "--" + value added, eatTheRest set true
    @Test
    public void testFlatten_plainToken_stopAtNonOptionTrue_currentOptionNull_addsDoubleDashAndValue() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"plainArg"}, true);
        assertArrayEquals(new String[] {"--", "plainArg"}, result);
    }

    // covers: process() - currentOption set and hasArg() true -> value consumed, currentOption cleared
    @Test
    public void testFlatten_plainToken_stopAtNonOptionTrue_currentOptionHasArg_addsValueAndClearsOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "desc"));
        String[] result = parser.flatten(options, new String[] {"-a", "value"}, true);
        assertArrayEquals(new String[] {"-a", "value"}, result);
    }

    // covers: process() full lifecycle across iterations - option consumes first value, second plain value triggers eatRest
    @Test
    public void testFlatten_plainToken_afterOptionConsumed_stopAtNonOptionTrue_secondPlainTokenTriggersEatRest() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "desc"));
        String[] result = parser.flatten(options, new String[] {"-a", "val1", "val2"}, true);
        assertArrayEquals(new String[] {"-a", "val1", "--", "val2"}, result);
    }

    // covers: multiple known two-char options processed sequentially via processOptionToken
    @Test
    public void testFlatten_multipleKnownTwoCharOptions_sequential() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "desc"));
        options.addOption(new Option("b", false, "desc"));
        String[] result = parser.flatten(options, new String[] {"-a", "-b"}, false);
        assertArrayEquals(new String[] {"-a", "-b"}, result);
    }

    // covers: "--" literal token handled unconditionally regardless of stopAtNonOption
    @Test
    public void testFlatten_doubleDashLiteralToken_unaffectedByStopAtNonOption() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"--"}, true);
        assertArrayEquals(new String[] {"--"}, result);
    }

    // covers: two consecutive "--" prefixed tokens without '=' processed independently
    @Test
    public void testFlatten_twoConsecutiveDoubleDashTokens() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"--alpha", "--beta"}, false);
        assertArrayEquals(new String[] {"--alpha", "--beta"}, result);
    }

    // covers: mixed token types processed in a single flatten call (double-dash, known option, plain)
    @Test
    public void testFlatten_multipleTokens_mixedTypes() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        String[] result = parser.flatten(options, new String[] {"-v", "--name=value", "plain"}, false);
        assertArrayEquals(new String[] {"-v", "--name", "value", "plain"}, result);
    }

    // covers: return type contract - flatten must return a String[] array
    @Test
    public void testFlatten_returnType_isStringArray() throws Throwable {
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] {"plain"}, false);
        assertTrue(result instanceof String[]);
    }
}
