package org.apache.commons.cli;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GnuParserClaudeTest {

    private GnuParser parser;
    private Options options;

    @Before
    public void setUp() throws Throwable {
        parser = new GnuParser();
        options = new Options();
        options.addOption("a", false, "option a, no arg");
        options.addOption("b", false, "option b, no arg");
        options.addOption("D", true, "option D, has arg, property style");
        options.addOption("f", "foo", true, "long option foo, has arg");
    }

    // covers: "--".equals(arg) branch, eatTheRest loop runs 0 times
    @Test
    public void testFlatten_doubleHyphenAlone_returnsSingleToken() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"--"}, false);
        assertArrayEquals(new String[]{"--"}, result);
    }

    // covers: "--" branch with eatTheRest loop running multiple iterations
    @Test
    public void testFlatten_doubleHyphenFollowedByArgs_eatsRestOfArgs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"--", "-a", "foo"}, false);
        assertArrayEquals(new String[]{"--", "-a", "foo"}, result);
    }

    // covers: "--" branch with eatTheRest loop running exactly one iteration
    @Test
    public void testFlatten_doubleHyphenFollowedByOneArg_eatsOneArg() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"--", "value"}, false);
        assertArrayEquals(new String[]{"--", "value"}, result);
    }

    // covers: "-".equals(arg) branch
    @Test
    public void testFlatten_singleHyphenAlone_returnsSingleToken() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-"}, false);
        assertArrayEquals(new String[]{"-"}, result);
    }

    // covers: "-" branch followed by more args, eatTheRest is untouched (stays false)
    @Test
    public void testFlatten_singleHyphenFollowedByArgs_doesNotEatRest() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-", "foo", "-a"}, false);
        assertArrayEquals(new String[]{"-", "foo", "-a"}, result);
    }

    // covers: arg.startsWith("-") true, options.hasOption(opt) true -> tokens.add(arg)
    @Test
    public void testFlatten_knownShortOption_addsArgAsIs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-a"}, false);
        assertArrayEquals(new String[]{"-a"}, result);
    }

    // covers same branch with a 2-char option string matching whole opt exactly
    @Test
    public void testFlatten_knownOptionD_addsArgAsIs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-D"}, false);
        assertArrayEquals(new String[]{"-D"}, result);
    }

    // covers: opt not registered whole, but first 2 chars match registered option -> split (javadoc rule 1)
    @Test
    public void testFlatten_propertyStyleOption_splitsIntoOptAndValue() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-Dproperty=value"}, false);
        assertArrayEquals(new String[]{"-D", "property=value"}, result);
    }

    // covers same branch, value without "=" sign
    @Test
    public void testFlatten_propertyStyleOptionNoEquals_splitsIntoOptAndValue() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-Dvalue"}, false);
        assertArrayEquals(new String[]{"-D", "value"}, result);
    }

    // covers: unknown option, first 2 chars not registered, stopAtNonOption false -> continues normally
    @Test
    public void testFlatten_unknownOption_stopAtNonOptionFalse_continuesProcessing() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-x", "-a"}, false);
        assertArrayEquals(new String[]{"-x", "-a"}, result);
    }

    // covers: unknown option, stopAtNonOption true -> eatTheRest becomes true, rest swallowed raw
    @Test
    public void testFlatten_unknownOption_stopAtNonOptionTrue_eatsRestOfArgs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-x", "-a", "foo"}, true);
        assertArrayEquals(new String[]{"-x", "-a", "foo"}, result);
    }

    // covers: unknown option is last arg, stopAtNonOption true -> inner eatTheRest loop runs 0 times
    @Test
    public void testFlatten_unknownOptionLastArg_stopAtNonOptionTrue_noExtraArgs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-x"}, true);
        assertArrayEquals(new String[]{"-x"}, result);
    }

    // covers: arg does not start with "-" -> tokens.add(arg) directly
    @Test
    public void testFlatten_plainArgument_addsAsIs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"plainArg"}, false);
        assertArrayEquals(new String[]{"plainArg"}, result);
    }

    // covers: outer for-loop runs zero times -> returns empty array
    @Test
    public void testFlatten_emptyArguments_returnsEmptyArray() throws Throwable {
        String[] result = parser.flatten(options, new String[]{}, false);
        assertEquals(0, result.length);
    }

    // covers multiple sequential tokens mixing known-option and plain-argument branches
    @Test
    public void testFlatten_mixedArguments_processesEachToken() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-a", "value1", "-b"}, false);
        assertArrayEquals(new String[]{"-a", "value1", "-b"}, result);
    }

    // bug detector: GNU-style "--foo=value" long option must be split into "--foo" and "value"
    // per the domain-standard GNU long-option convention this GnuParser implements
    @Test
    public void testFlatten_longOptionWithEquals_splitsIntoOptionAndValue() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"--foo=bar"}, false);
        assertArrayEquals(new String[]{"--foo", "bar"}, result);
    }

    // covers known no-arg option "-b" matching whole opt branch
    @Test
    public void testFlatten_knownOptionB_addsArgAsIs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-b"}, false);
        assertArrayEquals(new String[]{"-b"}, result);
    }

    // covers "--" branch triggered mid-array, swallowing remaining tokens raw
    @Test
    public void testFlatten_doubleHyphenMidArray_eatsRestFromThatPoint() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-a", "--", "-x", "-y"}, false);
        assertArrayEquals(new String[]{"-a", "--", "-x", "-y"}, result);
    }

    // covers known-option branch is unaffected by stopAtNonOption flag (eatTheRest stays false)
    @Test
    public void testFlatten_knownOption_stopAtNonOptionTrue_doesNotEatRest() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-a", "-b"}, true);
        assertArrayEquals(new String[]{"-a", "-b"}, result);
    }

    // covers arg length exactly 2, substring(0,2) equals whole arg, no registered match -> else branch
    @Test
    public void testFlatten_unknownTwoCharOption_stopAtNonOptionFalse_addsAsIs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-z"}, false);
        assertArrayEquals(new String[]{"-z"}, result);
    }

    // covers eatTheRest loop appending a mix of option-like and plain raw tokens unchanged
    @Test
    public void testFlatten_unknownOptionEatsRestIncludingPlainArgs() throws Throwable {
        String[] result = parser.flatten(options, new String[]{"-x", "plain", "-a"}, true);
        assertArrayEquals(new String[]{"-x", "plain", "-a"}, result);
    }
}
