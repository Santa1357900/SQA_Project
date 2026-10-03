package org.apache.commons.cli;

import junit.framework.TestCase;
import java.lang.reflect.Method;

public class PosixParserTest extends TestCase {

    private PosixParser parser;
    private Options options;

    protected void setUp() throws Exception {
        super.setUp();
        parser = new PosixParser();
        options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        options.addOption("b", "beta", true, "beta option with arg");
        options.addOption("c", "char", false, "char option");
    }

    protected void tearDown() throws Exception {
        parser = null;
        options = null;
        super.tearDown();
    }

    public void testFlattenLongOptionWithEquals() throws Throwable {
        String[] args = new String[]{"--beta=value"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(2, flattened.length);
        assertEquals("--beta", flattened[0]);
        assertEquals("value", flattened[1]);
    }

    public void testFlattenLongOptionWithoutEquals() throws Throwable {
        String[] args = new String[]{"--alpha"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(1, flattened.length);
        assertEquals("--alpha", flattened[0]);
    }

    public void testFlattenSingleHyphen() throws Throwable {
        String[] args = new String[]{"-"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(1, flattened.length);
        assertEquals("-", flattened[0]);
    }

    public void testFlattenValidShortOption() throws Throwable {
        String[] args = new String[]{"-a"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(1, flattened.length);
        assertEquals("-a", flattened[0]);
    }

    public void testFlattenOptionWithArgAndStopAtNonOption() throws Throwable {
        String[] args = new String[]{"-b", "argValue", "nonOption"};
        String[] flattened = parser.flatten(options, args, true);
        assertNotNull(flattened);
        assertEquals(3, flattened.length);
        assertEquals("-b", flattened[0]);
        assertEquals("argValue", flattened[1]);
        assertEquals("nonOption", flattened[2]);
    }

    public void testFlattenBurstingValidOptions() throws Throwable {
        String[] args = new String[]{"-ac"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(2, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("-c", flattened[1]);
    }

    public void testFlattenBurstingWithOptionsAndArgument() throws Throwable {
        // 'b' has an argument, so -abvalue should burst into -a and -b and value
        String[] args = new String[]{"-abvalue"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(3, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("-b", flattened[1]);
        assertEquals("value", flattened[2]);
    }

    public void testFlattenUnknownOptionWithStopAtNonOptionTrue() throws Throwable {
        String[] args = new String[]{"-z", "extra"};
        String[] flattened = parser.flatten(options, args, true);
        assertNotNull(flattened);
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("-z", flattened[1]);
        assertEquals("extra", flattened[2]);
    }

    public void testFlattenUnknownOptionWithStopAtNonOptionFalse() throws Throwable {
        String[] args = new String[]{"-z"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(1, flattened.length);
        assertEquals("-z", flattened[0]);
    }

    public void testFlattenBurstingUnknownOptionStopAtNonOption() throws Throwable {
        String[] args = new String[]{"-azx"};
        String[] flattened = parser.flatten(options, args, true);
        assertNotNull(flattened);
        assertEquals(3, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("--", flattened[1]);
        assertEquals("zx", flattened[2]);
    }

    public void testFlattenBurstingUnknownOptionStopAtNonOptionFalse() throws Throwable {
        String[] args = new String[]{"-azx"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(1, flattened.length);
        assertEquals("-azx", flattened[0]);
    }

    public void testFlattenNonOptionWithoutStop() throws Throwable {
        String[] args = new String[]{"nonOptionVal"};
        String[] flattened = parser.flatten(options, args, false);
        assertNotNull(flattened);
        assertEquals(1, flattened.length);
        assertEquals("nonOptionVal", flattened[0]);
    }

    public void testFlattenNonOptionWithStop() throws Throwable {
        String[] args = new String[]{"nonOptionVal", "other"};
        String[] flattened = parser.flatten(options, args, true);
        assertNotNull(flattened);
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("nonOptionVal", flattened[1]);
        assertEquals("other", flattened[2]);
    }

    public void testProtectedBurstTokenDirectly() throws Throwable {
        Method m = PosixParser.class.getDeclaredMethod("burstToken", new Class[]{String.class, boolean.class});
        m.setAccessible(true);
        // Using reflection to invoke protected method with valid state
        parser.flatten(options, new String[0], false);
        m.invoke(parser, new Object[]{"-ac", Boolean.FALSE});
    }
}