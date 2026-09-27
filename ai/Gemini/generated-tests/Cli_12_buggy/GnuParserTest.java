package org.apache.commons.cli;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class GnuParserTest {

    @Test
    public void testFlattenNullArguments() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();
        try {
            parser.flatten(options, null, true);
        } catch (NullPointerException e) {
            // Expected
        } catch (Exception e) {
            // Expected depending on Parser implementation
        }
    }

    @Test
    public void testFlattenEmptyArguments() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();
        String[] args = new String[0];
        String[] result = parser.flatten(options, args, false);
        assertNotNull(result);
        assertArrayEquals(new String[0], result);
    }

    @Test
    public void testFlattenDoubleHyphen() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();
        String[] args = new String[] { "--", "non-option", "-foo" };
        String[] result = parser.flatten(options, args, false);
        assertArrayEquals(new String[] { "--", "non-option", "-foo" }, result);
    }

    @Test
    public void testFlattenSingleHyphen() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();
        String[] args = new String[] { "-" };
        String[] result = parser.flatten(options, args, false);
        assertArrayEquals(new String[] { "-" }, result);
    }

    @Test
    public void testFlattenExistingOption() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        
        String[] args = new String[] { "-a" };
        String[] result = parser.flatten(options, args, false);
        assertArrayEquals(new String[] { "-a" }, result);
    }

    @Test
    public void testFlattenSplittableOptionWithEquals() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();
        options.addOption("D", "define", true, "define option");

        String[] args = new String[] { "-Dproperty=value" };
        String[] result = parser.flatten(options, args, false);
        assertArrayEquals(new String[] { "-D", "property=value" }, result);
    }

    @Test
    public void testFlattenUnknownOptionWithStopAtNonOption() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();

        String[] args = new String[] { "-unknown", "rest" };
        String[] result = parser.flatten(options, args, true);
        assertArrayEquals(new String[] { "-unknown", "rest" }, result);
    }

    @Test
    public void testFlattenUnknownOptionWithoutStopAtNonOption() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();

        String[] args = new String[] { "-unknown", "rest" };
        String[] result = parser.flatten(options, args, false);
        assertArrayEquals(new String[] { "-unknown", "rest" }, result);
    }

    @Test
    public void testFlattenPlainArguments() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();

        String[] args = new String[] { "arg1", "arg2" };
        String[] result = parser.flatten(options, args, false);
        assertArrayEquals(new String[] { "arg1", "arg2" }, result);
    }

    @Test
    public void testFlattenStopAtNonOptionWithDoubleHyphenAndEatTheRest() throws Throwable {
        GnuParser parser = new GnuParser();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha");

        String[] args = new String[] { "-a", "--", "-b", "arg" };
        String[] result = parser.flatten(options, args, true);
        assertArrayEquals(new String[] { "-a", "--", "-b", "arg" }, result);
    }
}