package org.apache.commons.cli;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PosixParserClaudeTest {

    private PosixParser parser;
    private Options options;

    @Before
    public void setUp() throws Throwable
    {
        parser = new PosixParser();
        options = new Options();
        options.addOption("a", false, "option a, no arg");
        options.addOption("b", true, "option b, has arg");
        options.addOption("c", false, "option c, no arg");
    }

    // covers: token.startsWith("--") && indexOf('=') != -1
    @Test
    public void testFlatten_longOptionWithEquals_splitsIntoTwoTokens() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"--foo=bar"}, false);
        assertArrayEquals(new String[] {"--foo", "bar"}, result);
    }

    // covers: token.startsWith("--") && indexOf('=') == -1
    @Test
    public void testFlatten_longOptionWithoutEquals_addsSingleToken() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"--foo"}, false);
        assertArrayEquals(new String[] {"--foo"}, result);
    }

    // covers: "-".equals(token) branch
    @Test
    public void testFlatten_singleHyphen_addsAsToken() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-"}, false);
        assertArrayEquals(new String[] {"-"}, result);
    }

    // covers: token.length()==2, valid option id, no-arg option
    @Test
    public void testFlatten_twoCharValidOption_noArg_addsToken() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-a"}, false);
        assertArrayEquals(new String[] {"-a"}, result);
    }

    // covers: two-char valid option with hasArg, followed by value token
    @Test
    public void testFlatten_twoCharValidOption_hasArg_followedByValue() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-b", "value"}, false);
        assertArrayEquals(new String[] {"-b", "value"}, result);
    }

    // covers: two-char invalid option id, stopAtNonOption=true -> token added and eatTheRest copies remaining raw
    @Test
    public void testFlatten_twoCharInvalidOption_stopAtNonOptionTrue_addsTokenAndEatsRest() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-z", "foo", "bar"}, true);
        assertArrayEquals(new String[] {"-z", "foo", "bar"}, result);
    }



    // covers: burstToken with multiple valid no-arg options, loop continues without break
    @Test
    public void testFlatten_burstToken_multipleValidNoArgOptions() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-ac"}, false);
        assertArrayEquals(new String[] {"-a", "-c"}, result);
    }

    // covers: burstToken with hasArg option, remainder of token becomes its value, then break
    @Test
    public void testFlatten_burstToken_optionWithArg_consumesRemainder() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-bvalue"}, false);
        assertArrayEquals(new String[] {"-b", "value"}, result);
    }

    // covers: burstToken invalid char, stopAtNonOption=false -> whole token added, break
    @Test
    public void testFlatten_burstToken_invalidChar_stopAtNonOptionFalse_addsWholeToken() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-xyz"}, false);
        assertArrayEquals(new String[] {"-xyz"}, result);
    }

    // covers: burstToken invalid char, stopAtNonOption=true, no currentOption -> process() adds "--" and value
    @Test
    public void testFlatten_burstToken_invalidChar_stopAtNonOptionTrue_noCurrentOption() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-xyz"}, true);
        assertArrayEquals(new String[] {"--", "xyz"}, result);
    }

    // covers: non-option token, stopAtNonOption=false -> added directly
    @Test
    public void testFlatten_nonOptionToken_stopAtNonOptionFalse_addsDirectly() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"foo"}, false);
        assertArrayEquals(new String[] {"foo"}, result);
    }

    // covers: non-option token, stopAtNonOption=true, no currentOption -> "--" then value
    @Test
    public void testFlatten_nonOptionToken_stopAtNonOptionTrue_noCurrentOption() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"foo"}, true);
        assertArrayEquals(new String[] {"--", "foo"}, result);
    }

    // covers: non-option token, stopAtNonOption=true, currentOption has arg -> value consumed, currentOption cleared
    @Test
    public void testFlatten_nonOptionToken_stopAtNonOptionTrue_currentOptionHasArg() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-b", "value"}, true);
        assertArrayEquals(new String[] {"-b", "value"}, result);
    }

    // covers: empty arguments array -> zero-iteration loop
    @Test
    public void testFlatten_emptyArguments_returnsEmptyArray() throws Throwable {
        String[] result = parser.flatten(options, new String[0], false);
        assertEquals(0, result.length);
    }

    // covers: mixture of long option with '=' and short valid option and following non-option value
    @Test
    public void testFlatten_mixedTokens_longAndShortOptions() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"--foo=bar", "-a", "baz"}, false);
        assertArrayEquals(new String[] {"--foo", "bar", "-a", "baz"}, result);
    }

    // covers: '=' at the very end of a long option token -> empty value substring
    @Test
    public void testFlatten_longOptionWithEqualsAtEnd_emptyValue() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"--foo="}, false);
        assertArrayEquals(new String[] {"--foo", ""}, result);
    }

    // covers: '=' near the start of a long option token -> empty name substring
    @Test
    public void testFlatten_longOptionWithEqualsAtStart_emptyName() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"--=foo"}, false);
        assertArrayEquals(new String[] {"--", "foo"}, result);
    }

    // covers: single two-char invalid option token with stopAtNonOption=true, no trailing args
    @Test
    public void testFlatten_shortOptionInvalid_stopAtNonOptionTrue_singleArg() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-z"}, true);
        assertArrayEquals(new String[] {"-z"}, result);
    }

    // covers: burstToken valid char then invalid char with stopAtNonOption=true -> process() called with currentOption set but no-arg
    @Test
    public void testFlatten_burstToken_validThenInvalid_stopAtNonOptionTrue() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-ax"}, true);
        assertArrayEquals(new String[] {"-a", "--", "x"}, result);
    }

    // covers: two-char hasArg option followed by multiple non-option values with stopAtNonOption=false
    @Test
    public void testFlatten_twoCharHasArgOption_multipleFollowingValues() throws Throwable {
        String[] result = parser.flatten(options, new String[] {"-b", "v1", "v2"}, false);
        assertArrayEquals(new String[] {"-b", "v1", "v2"}, result);
    }



    // covers: token.length()>2 && options.hasOption(token) true directly (no bursting needed)
    @Test
    public void testFlatten_longerTokenDirectlyRegisteredAsOption_addsDirectly() throws Throwable {
        Options opts = new Options();
        opts.addOption("abc", false, "combined option id");
        String[] result = parser.flatten(opts, new String[] {"-abc"}, false);
        assertArrayEquals(new String[] {"-abc"}, result);
    }

    // covers: burstToken called on a fresh parser instance to ensure init() resets prior state
    @Test
    public void testFlatten_calledTwice_stateResetBetweenCalls() throws Throwable {
        String[] first = parser.flatten(options, new String[] {"-a"}, false);
        assertArrayEquals(new String[] {"-a"}, first);
        String[] second = parser.flatten(options, new String[] {"-c"}, false);
        assertArrayEquals(new String[] {"-c"}, second);
    }
}
