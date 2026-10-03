package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosixParserClaudeTest {

    // flatten: "--" long option token containing '=' must split into name and value
    @Test
    public void testFlatten_longOptionWithEquals_splitsIntoTwoTokens() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--foo=bar" }, true);
        assertArrayEquals(new String[] { "--foo", "bar" }, result);
    }

    // flatten: "--" long option token without '=' is added as a whole token
    @Test
    public void testFlatten_longOptionWithoutEquals_addsWholeToken() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--verbose" }, true);
        assertArrayEquals(new String[] { "--verbose" }, result);
    }

    // flatten: '=' at position 2 yields empty-ish option name split "--" and value
    @Test
    public void testFlatten_longOptionEqualsAtStart_emptyOptionNamePart() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--=value" }, true);
        assertArrayEquals(new String[] { "--", "value" }, result);
    }

    // flatten: '=' at end of token yields empty value part
    @Test
    public void testFlatten_longOptionEqualsAtEnd_emptyValuePart() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "--opt=" }, true);
        assertArrayEquals(new String[] { "--opt", "" }, result);
    }

    // flatten: single hyphen "-" token is always added as-is
    @Test
    public void testFlatten_singleHyphen_addsAsToken() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "-" }, true);
        assertArrayEquals(new String[] { "-" }, result);
    }

    // flatten: 2-char valid option token is added and currentOption is set internally
    @Test
    public void testFlatten_twoCharValidOption_addsTokenAndSetsCurrentOption() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("a", false, "a option"));
        String[] result = parser.flatten(options, new String[] { "-a" }, true);
        assertArrayEquals(new String[] { "-a" }, result);
    }

    // flatten: 2-char invalid option with stopAtNonOption=true eats the remainder
    @Test
    public void testFlatten_twoCharInvalidOption_stopAtNonOptionTrue_eatsRemaining() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "-z", "extra" }, true);
        assertArrayEquals(new String[] { "-z", "extra" }, result);
    }



    // flatten: token longer than 2 chars matching a registered multi-char option bypasses bursting
    @Test
    public void testFlatten_longTokenExactOptionMatch_addsWholeTokenNoBurst() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("xyz", false, "desc"));
        String[] result = parser.flatten(options, new String[] { "-xyz" }, true);
        assertArrayEquals(new String[] { "-xyz" }, result);
    }

    // flatten: exact long-token match does NOT set currentOption even if option has an arg
    @Test
    public void testFlatten_longTokenExactMatchWithArgOption_doesNotSetCurrentOption() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("xyz", true, "desc"));
        String[] result = parser.flatten(options, new String[] { "-xyz", "value" }, true);
        assertArrayEquals(new String[] { "-xyz", "--", "value" }, result);
    }

    // burstToken: all characters valid no-arg options, each added separately
    @Test
    public void testFlatten_burstToken_allValidNoArgOptions() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("a", false, "a"));
        options.addOption(new Option("b", false, "b"));
        options.addOption(new Option("c", false, "c"));
        String[] result = parser.flatten(options, new String[] { "-abc" }, true);
        assertArrayEquals(new String[] { "-a", "-b", "-c" }, result);
    }

    // burstToken: an option with arg consumes the remainder of the token as its value
    @Test
    public void testFlatten_burstToken_optionWithArgConsumesRemainder() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("f", true, "file"));
        String[] result = parser.flatten(options, new String[] { "-fvalue" }, true);
        assertArrayEquals(new String[] { "-f", "value" }, result);
    }

    // burstToken: option with arg at last position with no remainder does not add a value token
    @Test
    public void testFlatten_burstToken_optionWithArgNoRemainder_noValueAdded() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("a", false, "a"));
        options.addOption(new Option("f", true, "file"));
        String[] result = parser.flatten(options, new String[] { "-af" }, true);
        assertArrayEquals(new String[] { "-a", "-f" }, result);
    }

    // burstToken: invalid char found after a valid one, stopAtNonOption=true calls process()
    @Test
    public void testFlatten_burstToken_invalidCharAfterValid_stopAtNonOptionTrue_processCalled() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("a", false, "a"));
        String[] result = parser.flatten(options, new String[] { "-ax" }, true);
        assertArrayEquals(new String[] { "-a", "--", "x" }, result);
    }

    // burstToken: invalid char at very first position, stopAtNonOption=true calls process()
    @Test
    public void testFlatten_burstToken_invalidCharAtStart_stopAtNonOptionTrue_processCalled() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "-xa" }, true);
        assertArrayEquals(new String[] { "--", "xa" }, result);
    }

    // burstToken: invalid char with stopAtNonOption=false adds the whole original token
    @Test
    public void testFlatten_burstToken_invalidChar_stopAtNonOptionFalse_addsWholeOriginalToken() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "-xy" }, false);
        assertArrayEquals(new String[] { "-xy" }, result);
    }

    // flatten: non-option token, stopAtNonOption=false, added directly without process()
    @Test
    public void testFlatten_nonOptionToken_stopAtNonOptionFalse_addsToken() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "file.txt" }, false);
        assertArrayEquals(new String[] { "file.txt" }, result);
    }

    // flatten: non-option token, stopAtNonOption=true, no current option => "--" marker then eats rest
    @Test
    public void testFlatten_nonOptionToken_stopAtNonOptionTrue_noCurrentOption_eatsRest() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[] { "file.txt", "more" }, true);
        assertArrayEquals(new String[] { "--", "file.txt", "more" }, result);
    }

    // process: single-arg current option consumes value and resets currentOption
    @Test
    public void testFlatten_processSingleArgOption_addsValueAndResetsCurrentOption() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("f", true, "file"));
        String[] result = parser.flatten(options, new String[] { "-f", "value", "other" }, true);
        assertArrayEquals(new String[] { "-f", "value", "--", "other" }, result);
    }



    // flatten: empty arguments array returns empty token array
    @Test
    public void testFlatten_emptyArguments_returnsEmptyArray() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        String[] result = parser.flatten(options, new String[0], true);
        assertEquals(0, result.length);
    }

    // burstToken triggers process()+eatTheRest, then gobble() consumes multiple remaining tokens
    @Test
    public void testFlatten_burstTokenStopAtNonOption_thenMultipleRemainingArgsGobbled() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("a", false, "a"));
        String[] result = parser.flatten(options, new String[] { "-ax", "after1", "after2" }, true);
        assertArrayEquals(new String[] { "-a", "--", "x", "after1", "after2" }, result);
    }

    // integration: mix of long option with '=', short no-arg option and plain token
    @Test
    public void testFlatten_mixedLongAndShortOptions_integration() throws Throwable {
        PosixParser parser = new PosixParser();
        Options options = new Options();
        options.addOption(new Option("a", false, "a option"));
        String[] result = parser.flatten(options, new String[] { "--name=val", "-a", "plain" }, false);
        assertArrayEquals(new String[] { "--name", "val", "-a", "plain" }, result);
    }
}
