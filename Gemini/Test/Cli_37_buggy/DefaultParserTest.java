package org.apache.commons.cli;

import junit.framework.TestCase;
import java.util.Properties;

public class DefaultParserTest extends TestCase {

    public void testSimpleParse() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        options.addOption("b", "beta", true, "beta option");

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-a", "-b", "value"});

        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
        assertEquals("value", cmd.getOptionValue("b"));
    }

    public void testLongOptionWithEqual() throws Throwable {
        Options options = new Options();
        options.addOption("b", "beta", true, "beta option");

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"--beta=myvalue"});

        assertTrue(cmd.hasOption("beta"));
        assertEquals("myvalue", cmd.getOptionValue("beta"));
    }

    public void testStopAtNonOption() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-a", "nonoption", "-a"}, true);

        assertTrue(cmd.hasOption("a"));
        assertEquals(2, cmd.getArgs().length);
        assertEquals("nonoption", cmd.getArgs()[0]);
        assertEquals("-a", cmd.getArgs()[1]);
    }

    public void testDoubleDash() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"--", "-a"});

        assertFalse(cmd.hasOption("a"));
        assertEquals(1, cmd.getArgs().length);
        assertEquals("-a", cmd.getArgs()[0]);
    }

    public void testUnrecognizedOption() throws Throwable {
        Options options = new Options();
        DefaultParser parser = new DefaultParser();
        try {
            parser.parse(options, new String[]{"-x"});
            fail("Expected UnrecognizedOptionException");
        } catch (UnrecognizedOptionException e) {
            assertTrue(e.getMessage().contains("Unrecognized option"));
        }
    }

    public void testMissingArgument() throws Throwable {
        Options options = new Options();
        options.addOption("b", "beta", true, "beta option");

        DefaultParser parser = new DefaultParser();
        try {
            parser.parse(options, new String[]{"-b"});
            fail("Expected MissingArgumentException");
        } catch (MissingArgumentException e) {
            assertNotNull(e);
        }
    }

    public void testMissingOption() throws Throwable {
        Options options = new Options();
        Option opt = new Option("r", "required", true, "required option");
        opt.setRequired(true);
        options.addOption(opt);

        DefaultParser parser = new DefaultParser();
        try {
            parser.parse(options, new String[]{});
            fail("Expected MissingOptionException");
        } catch (MissingOptionException e) {
            assertNotNull(e);
        }
    }

    public void testPropertiesParsing() throws Throwable {
        Options options = new Options();
        options.addOption("b", "beta", true, "beta option");
        options.addOption("f", "flag", false, "flag option");

        Properties props = new Properties();
        props.setProperty("b", "propValue");
        props.setProperty("f", "true");

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{}, props, false);

        assertTrue(cmd.hasOption("b"));
        assertEquals("propValue", cmd.getOptionValue("b"));
        assertTrue(cmd.hasOption("f"));
    }

    public void testAmbiguousLongOption() throws Throwable {
        Options options = new Options();
        options.addOption("version", "version", false, "version");
        options.addOption("verbose", "verbose", false, "verbose");

        DefaultParser parser = new DefaultParser();
        try {
            parser.parse(options, new String[]{"--ver"});
            fail("Expected AmbiguousOptionException");
        } catch (AmbiguousOptionException e) {
            assertNotNull(e);
        }
    }

    public void testConcatenatedOptions() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "a");
        options.addOption("b", false, "b");
        options.addOption("c", true, "c");

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-abcval"});

        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
        assertTrue(cmd.hasOption("c"));
        assertEquals("val", cmd.getOptionValue("c"));
    }

    public void testNegativeNumberArgument() throws Throwable {
        Options options = new Options();
        options.addOption("n", "number", true, "number option");

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-n", "-123.45"});

        assertTrue(cmd.hasOption("n"));
        assertEquals("-123.45", cmd.getOptionValue("n"));
    }
}