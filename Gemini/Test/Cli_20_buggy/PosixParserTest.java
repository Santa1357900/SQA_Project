package org.apache.commons.cli;

import junit.framework.TestCase;

public class PosixParserTest extends TestCase {

    private PosixParser parser;
    private Options options;

    protected void setUp() throws Exception {
        super.setUp();
        parser = new PosixParser();
        options = new Options();
        options.addOption(new Option("a", "a-opt", false, "alpha"));
        options.addOption(new Option("b", "b-opt", true, "beta"));
        options.addOption(new Option("c", "c-opt", false, "charlie"));
    }

    public void testLongOptionWithEqual() throws Throwable {
        String[] args = new String[] { "--b-opt=value" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("--b-opt", flattened[0]);
        assertEquals("value", flattened[1]);
    }

    public void testLongOptionWithoutEqual() throws Throwable {
        String[] args = new String[] { "--a-opt" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("--a-opt", flattened[0]);
    }

    public void testSingleHyphen() throws Throwable {
        String[] args = new String[] { "-" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-", flattened[0]);
    }

    public void testDoubleHyphen() throws Throwable {
        String[] args = new String[] { "--", "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("-a", flattened[1]);
    }

    public void testSimpleOption() throws Throwable {
        String[] args = new String[] { "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-a", flattened[0]);
    }

    public void testUnrecognizedOptionWithStopAtNonOption() throws Throwable {
        String[] args = new String[] { "-x", "rest" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("-x", flattened[0]);
        assertEquals("--", flattened[1]);
        assertEquals("rest", flattened[2]);
    }

    public void testUnrecognizedOptionWithoutStopAtNonOption() throws Throwable {
        String[] args = new String[] { "-x" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-x", flattened[0]);
    }

    public void testBurstingValidOption() throws Throwable {
        String[] args = new String[] { "-ac" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("-c", flattened[1]);
    }

    public void testBurstingOptionWithArg() throws Throwable {
        String[] args = new String[] { "-bval" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-b", flattened[0]);
        assertEquals("val", flattened[1]);
    }

    public void testBurstingInvalidOptionWithStop() throws Throwable {
        String[] args = new String[] { "-ax" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("--", flattened[1]);
        assertEquals("x", flattened[2]);
    }

    public void testBurstingInvalidOptionWithoutStop() throws Throwable {
        String[] args = new String[] { "-ax" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-a", flattened[0]);
        // '-ax' is not recognized as a whole, but 'a' is valid, then 'x' is invalid and added as is because stopAtNonOption is false
        assertEquals("-ax", flattened[1]);
    }

    public void testNonOptionWithoutStop() throws Throwable {
        String[] args = new String[] { "nonoption" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("nonoption", flattened[0]);
    }

    public void testNonOptionWithStop() throws Throwable {
        String[] args = new String[] { "nonoption", "-a" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("nonoption", flattened[1]);
        // Since stopAtNonOption is true, eatTheRest is set and remainder are gobbled
        assertEquals("-a", flattened[2]);
    }

    public void testProcessWithCurrentOptionHavingArg() throws Throwable {
        Options opts = new Options();
        Option optB = new Option("b", true, "beta");
        opts.addOption(optB);

        String[] args = new String[] { "-b", "argumentValue" };
        String[] flattened = new PosixParser().flatten(opts, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-b", flattened[0]);
        assertEquals("argumentValue", flattened[1]);
    }
}