package org.apache.commons.cli;

import junit.framework.TestCase;
import java.util.Properties;

public class DefaultParserTest extends TestCase {

    public void testParseSimpleOption() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-a"});
        
        assertTrue(cmd.hasOption("a"));
    }

    public void testParseLongOption() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"--alpha"});
        
        assertTrue(cmd.hasOption("alpha"));
    }

    public void testParseOptionWithArgument() throws Throwable {
        Options options = new Options();
        options.addOption("b", "beta", true, "beta option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-b", "value"});
        
        assertTrue(cmd.hasOption("b"));
        assertEquals("value", cmd.getOptionValue("b"));
    }

    public void testParseOptionWithArgumentEquals() throws Throwable {
        Options options = new Options();
        options.addOption("b", "beta", true, "beta option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-b=value"});
        
        assertTrue(cmd.hasOption("b"));
        assertEquals("value", cmd.getOptionValue("b"));
    }

    public void testParseLongOptionWithArgumentEquals() throws Throwable {
        Options options = new Options();
        options.addOption("b", "beta", true, "beta option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"--beta=value"});
        
        assertTrue(cmd.hasOption("beta"));
        assertEquals("value", cmd.getOptionValue("beta"));
    }

    public void testStopAtNonOption() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-a", "non-option", "-a"}, true);
        
        assertTrue(cmd.hasOption("a"));
        assertEquals(2, cmd.getArgs().length);
        assertEquals("non-option", cmd.getArgs()[0]);
        assertEquals("-a", cmd.getArgs()[1]);
    }

    public void testDoubleDashSkipParsing() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"--", "-a"});
        
        assertFalse(cmd.hasOption("a"));
        assertEquals(1, cmd.getArgs().length);
        assertEquals("-a", cmd.getArgs()[0]);
    }

    public void testMissingArgumentException() throws Throwable {
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

    public void testMissingOptionException() throws Throwable {
        Options options = new Options();
        Option opt = new Option("r", "required", false, "required option");
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

    public void testUnrecognizedOptionException() throws Throwable {
        Options options = new Options();
        
        DefaultParser parser = new DefaultParser();
        try {
            parser.parse(options, new String[]{"-x"});
            fail("Expected UnrecognizedOptionException");
        } catch (UnrecognizedOptionException e) {
            assertNotNull(e);
        }
    }

    public void testAmbiguousOptionException() throws Throwable {
        Options options = new Options();
        options.addOption(null, "version", false, "version");
        options.addOption(null, "verbose", false, "verbose");
        
        DefaultParser parser = new DefaultParser();
        try {
            parser.parse(options, new String[]{"--v"});
            fail("Expected AmbiguousOptionException");
        } catch (AmbiguousOptionException e) {
            assertNotNull(e);
        }
    }

    public void testPropertiesHandling() throws Throwable {
        Options options = new Options();
        options.addOption("a", "alpha", true, "alpha option");
        
        Properties props = new Properties();
        props.setProperty("a", "propValue");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{}, props, false);
        
        assertTrue(cmd.hasOption("a"));
        assertEquals("propValue", cmd.getOptionValue("a"));
    }

    public void testPropertiesBooleanHandling() throws Throwable {
        Options options = new Options();
        options.addOption("c", "char", false, "char option");
        
        Properties props = new Properties();
        props.setProperty("c", "true");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{}, props, false);
        
        assertTrue(cmd.hasOption("c"));
    }

    public void testConcatenatedOptions() throws Throwable {
        Options options = new Options();
        options.addOption("x", false, "x option");
        options.addOption("y", false, "y option");
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"-xy"});
        
        assertTrue(cmd.hasOption("x"));
        assertTrue(cmd.hasOption("y"));
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