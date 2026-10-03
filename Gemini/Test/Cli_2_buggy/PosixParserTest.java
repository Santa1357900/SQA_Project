package org.apache.commons.cli;

import junit.framework.TestCase;

public class PosixParserTest extends TestCase {

    private PosixParser parser;
    private Options options;

    protected void setUp() throws Throwable {
        super.setUp();
        parser = new PosixParser();
        options = new Options();
    }

    public void testLongOptionWithEquals() throws Throwable {
        options.addOption("b", "block-size", true, "use SIZE-byte blocks");
        String[] args = new String[] { "--block-size=1024" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("--block-size", flattened[0]);
        assertEquals("1024", flattened[1]);
    }

    public void testLongOptionWithoutEquals() throws Throwable {
        options.addOption("t", "test", false, "test option");
        String[] args = new String[] { "--test" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("--test", flattened[0]);
    }

    public void testSingleHyphen() throws Throwable {
        String[] args = new String[] { "-" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-", flattened[0]);
    }

    public void testValidShortOption() throws Throwable {
        options.addOption("a", "all", false, "do not ignore entries starting with .");
        String[] args = new String[] { "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-a", flattened[0]);
    }

    public void testOptionLengthGreaterThanTwoInOptions() throws Throwable {
        options.addOption("abc", "abc-opt", false, "long option with single dash");
        String[] args = new String[] { "-abc" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-abc", flattened[0]);
    }

    public void testNonOptionWithStopAtNonOptionTrue() throws Throwable {
        String[] args = new String[] { "non-option", "-a" };
        String[] flattened = parser.flatten(options, args, true);
        assertTrue(flattened.length > 0);
        assertEquals("--", flattened[0]);
        assertEquals("non-option", flattened[1]);
    }

    public void testNonOptionWithStopAtNonOptionFalse() throws Throwable {
        String[] args = new String[] { "non-option", "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("non-option", flattened[0]);
        assertEquals("-a", flattened[1]);
    }

    public void testBurstTokenWithArgument() throws Throwable {
        options.addOption("f", "file", true, "file option");
        String[] args = new String[] { "-ffilename.txt" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-f", flattened[0]);
        assertEquals("filename.txt", flattened[1]);
    }

    public void testBurstTokenWithoutArgumentStopAtNonOption() throws Throwable {
        String[] args = new String[] { "-xyz" };
        String[] flattened = parser.flatten(options, args, true);
        assertTrue(flattened.length >= 2);
        assertEquals("--", flattened[0]);
        assertEquals("xyz", flattened[1]);
    }

    public void testBurstTokenWithoutArgumentStopAtNonOptionFalse() throws Throwable {
        String[] args = new String[] { "-xyz" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(3, flattened.length);
        assertEquals("-x", flattened[0]);
        assertEquals("-y", flattened[1]);
        assertEquals("-z", flattened[2]);
    }

    public void testProcessWithCurrentOptionHasArgs() throws Throwable {
        options.addOption(OptionBuilder.withLongOpt("multiple").hasArgs().create('m'));
        String[] args = new String[] { "-m", "arg1", "arg2" };
        String[] flattened = parser.flatten(options, args, true);
        assertTrue(flattened.length > 0);
    }
}