package org.apache.commons.cli;

import junit.framework.TestCase;

public class PosixParserTest extends TestCase
{
    private PosixParser parser;
    private Options options;

    protected void setUp() throws Exception
    {
        super.setUp();
        parser = new PosixParser();
        options = new Options();
        options.addOption("a", "aoption", false, "a description");
        options.addOption("b", "boption", true, "b description");
        options.addOption("c", "coption", false, "c description");
    }

    public void testLongOptionWithoutArg() throws Throwable
    {
        String[] args = new String[] { "--aoption" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("--aoption", flattened[0]);
    }

    public void testLongOptionWithArg() throws Throwable
    {
        String[] args = new String[] { "--boption=value" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("--boption", flattened[0]);
        assertEquals("value", flattened[1]);
    }

    public void testLongOptionUnknown() throws Throwable
    {
        String[] args = new String[] { "--unknown" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("--unknown", flattened[1]);
    }

    public void testSingleHyphen() throws Throwable
    {
        String[] args = new String[] { "-" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-", flattened[0]);
    }

    public void testSingleCharOptionValid() throws Throwable
    {
        String[] args = new String[] { "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-a", flattened[0]);
    }

    public void testSingleCharOptionStopAtNonOption() throws Throwable
    {
        String[] args = new String[] { "-z", "foo" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("-z", flattened[1]);
        assertEquals("foo", flattened[2]);
    }

    public void testBurstingValidOptionWithoutArg() throws Throwable
    {
        String[] args = new String[] { "-ac" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("-c", flattened[1]);
    }

    public void testBurstingValidOptionWithArg() throws Throwable
    {
        String[] args = new String[] { "-bval" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("-b", flattened[0]);
        assertEquals("val", flattened[1]);
    }

    public void testBurstingUnknownOptionWithStop() throws Throwable
    {
        String[] args = new String[] { "-ax" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("--", flattened[1]);
        assertEquals("x", flattened[2]);
    }

    public void testBurstingUnknownOptionWithoutStop() throws Throwable
    {
        String[] args = new String[] { "-ax" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(1, flattened.length);
        assertEquals("-ax", flattened[0]);
    }

    public void testNonOptionWithStopAtNonOption() throws Throwable
    {
        String[] args = new String[] { "nonoption", "-a" };
        String[] flattened = parser.flatten(options, args, true);
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("nonoption", flattened[1]);
        assertEquals("-a", flattened[2]);
    }

    public void testNonOptionWithoutStopAtNonOption() throws Throwable
    {
        String[] args = new String[] { "nonoption", "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("nonoption", flattened[0]);
        assertEquals("-a", flattened[1]);
    }

    public void testDoubleHyphen() throws Throwable
    {
        String[] args = new String[] { "--", "-a" };
        String[] flattened = parser.flatten(options, args, false);
        assertEquals(2, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("-a", flattened[1]);
    }
}