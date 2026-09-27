package org.apache.commons.cli;

import junit.framework.TestCase;

public class PosixParserTest extends TestCase {

    private PosixParser parser;
    private Options options;

    protected void setUp() throws Exception {
        super.setUp();
        parser = new PosixParser();
        options = new Options();
    }

    public void testLongOptionWithEquals() throws Throwable {
        options.addOption(OptionBuilder.withLongOpt("foo").hasArg().create());
        String[] args = new String[] { "--foo=bar" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("--foo", flattened[0]);
        assertEquals("bar", flattened[1]);
    }

    public void testLongOptionWithoutEquals() throws Throwable {
        options.addOption(OptionBuilder.withLongOpt("foo").create());
        String[] args = new String[] { "--foo" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("--foo", flattened[0]);
    }

    public void testSingleHyphen() throws Throwable {
        String[] args = new String[] { "-" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-", flattened[0]);
    }

    public void testValidShortOption() throws Throwable {
        options.addOption("a", false, "alpha");
        String[] args = new String[] { "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-a", flattened[0]);
    }

    public void testInvalidShortOptionWithStopAtNonOption() throws Throwable {
        String[] args = new String[] { "-b", "extra" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("-b", flattened[1]);
        assertEquals("extra", flattened[2]);
    }

    public void testLongOptionAsShortLengthExceptionOrHandling() throws Throwable {
        options.addOption(OptionBuilder.withLongOpt("longopt").create());
        String[] args = new String[] { "--longopt" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("--longopt", flattened[0]);
    }

    public void testBurstTokenValidWithArg() throws Throwable {
        options.addOption("a", true, "alpha");
        String[] args = new String[] { "-abar" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("bar", flattened[1]);
    }

    public void testBurstTokenInvalidWithStopAtNonOption() throws Throwable {
        String[] args = new String[] { "-abc" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(2, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("-abc", flattened[1]);
    }

    public void testBurstTokenInvalidWithoutStopAtNonOption() throws Throwable {
        String[] args = new String[] { "-abc" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-abc", flattened[0]);
    }

    public void testNonOptionWithoutStop() throws Throwable {
        String[] args = new String[] { "nonoption" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("nonoption", flattened[0]);
    }

    public void testProcessWithCurrentOptionHasArgs() throws Throwable {
        options.addOption(OptionBuilder.withLongOpt("foo").hasArgs().create());
        String[] args = new String[] { "--foo", "val1", "val2" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(3, flattened.length);
        assertEquals("--foo", flattened[0]);
        assertEquals("val1", flattened[1]);
        assertEquals("val2", flattened[2]);
    }

    public void testLongOptionWithOptionsHasOptionMatch() throws Throwable {
        options.addOption("a", false, "alpha");
        String[] args = new String[] { "-abc" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-abc", flattened[0]);
    }
}