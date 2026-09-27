package org.apache.commons.cli;

import junit.framework.TestCase;
import java.util.Properties;

public class ParserTest extends TestCase {

    private static class ConcreteParser extends Parser {
        protected String[] flatten(Options opts, String[] arguments, boolean stopAtNonOption) {
            return arguments;
        }
    }

    public void testParseNullArguments() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        CommandLine cl = parser.parse(options, (String[]) null);
        assertNotNull(cl);
        assertEquals(0, cl.getArgs().length);
    }

    public void testParseDoubleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("a", "alpha", false, "alpha option"));
        String[] args = new String[] { "--", "-a" };
        CommandLine cl = parser.parse(options, args);
        assertNotNull(cl);
        assertTrue(cl.hasOption("a") == false);
        assertEquals(1, cl.getArgs().length);
        assertEquals("-a", cl.getArgs()[0]);
    }

    public void testParseSingleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-" };
        CommandLine cl = parser.parse(options, args, false);
        assertNotNull(cl);
        assertEquals(1, cl.getArgs().length);
        assertEquals("-", cl.getArgs()[0]);
    }

    public void testParseSingleDashStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-" };
        CommandLine cl = parser.parse(options, args, true);
        assertNotNull(cl);
        assertEquals(0, cl.getArgs().length);
    }

    public void testParseUnrecognizedOptionWithStopAt() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-unknown" };
        CommandLine cl = parser.parse(options, args, true);
        assertNotNull(cl);
        assertEquals(1, cl.getArgs().length);
        assertEquals("-unknown", cl.getArgs()[0]);
    }

    public void testParseUnrecognizedOptionThrowsException() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-unknown" };
        try {
            parser.parse(options, args, false);
            fail("Should have thrown UnrecognizedOptionException");
        } catch (UnrecognizedOptionException e) {
            assertTrue(e.getMessage().contains("-unknown"));
        }
    }

    public void testProcessPropertiesNull() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        parser.setOptions(options);
        parser.cmd = new CommandLine();
        parser.processProperties(null);
        assertEquals(0, parser.cmd.getOptions().length);
    }

    public void testProcessPropertiesBooleanValues() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("b", false, "bool"));
        parser.setOptions(options);
        parser.cmd = new CommandLine();

        Properties props = new Properties();
        props.setProperty("b", "yes");
        parser.processProperties(props);
        assertTrue(parser.cmd.hasOption("b"));
    }

    public void testProcessPropertiesInvalidBoolean() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("b", false, "bool"));
        parser.setOptions(options);
        parser.cmd = new CommandLine();

        Properties props = new Properties();
        props.setProperty("b", "no");
        parser.processProperties(props);
        assertFalse(parser.cmd.hasOption("b"));
    }

    public void testCheckRequiredOptionsMissing() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option reqOpt = new Option("r", true, "required");
        reqOpt.setRequired(true);
        options.addOption(reqOpt);
        parser.setOptions(options);

        try {
            parser.checkRequiredOptions();
            fail("Should throw MissingOptionException");
        } catch (MissingOptionException e) {
            assertTrue(e.getMessage().contains("r"));
        }
    }
}