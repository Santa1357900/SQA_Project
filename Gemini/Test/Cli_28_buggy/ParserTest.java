package org.apache.commons.cli;

import junit.framework.TestCase;
import java.util.Properties;
import java.util.ListIterator;
import java.util.ArrayList;

public class ParserTest extends TestCase {

    private static class ConcreteParser extends Parser {
        protected String[] flatten(Options opts, String[] arguments, boolean stopAtNonOption) throws ParseException {
            return arguments;
        }
    }

    public void testParseNullArguments() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        CommandLine cmd = parser.parse(options, (String[]) null);
        assertNotNull(cmd);
    }

    public void testParseDoubleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("a", "alpha", false, "alpha option"));
        String[] args = new String[] { "--", "-a" };
        CommandLine cmd = parser.parse(options, args);
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("a") == false);
        assertTrue(cmd.getArgs().length == 1);
        assertEquals("-a", cmd.getArgs()[0]);
    }

    public void testParseSingleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-" };
        CommandLine cmd = parser.parse(options, args, false);
        assertNotNull(cmd);
        assertEquals(1, cmd.getArgs().length);
        assertEquals("-", cmd.getArgs()[0]);
    }

    public void testParseSingleDashStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-" };
        CommandLine cmd = parser.parse(options, args, true);
        assertNotNull(cmd);
        assertEquals(0, cmd.getArgs().length);
    }

    public void testParseUnrecognizedOptionThrowsException() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-unknown" };
        try {
            parser.parse(options, args, false);
            fail("Expected UnrecognizedOptionException");
        } catch (UnrecognizedOptionException e) {
            assertEquals("-unknown", e.getOption());
        }
    }

    public void testParseUnrecognizedOptionStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-unknown", "extra" };
        CommandLine cmd = parser.parse(options, args, true);
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("-unknown") == false);
        assertEquals(2, cmd.getArgs().length);
        assertEquals("-unknown", cmd.getArgs()[0]);
        assertEquals("extra", cmd.getArgs()[1]);
    }

    public void testParseArgumentWithoutStop() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "arg1" };
        CommandLine cmd = parser.parse(options, args, false);
        assertNotNull(cmd);
        assertEquals(1, cmd.getArgs().length);
        assertEquals("arg1", cmd.getArgs()[0]);
    }

    public void testParseArgumentStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "arg1", "arg2" };
        CommandLine cmd = parser.parse(options, args, true);
        assertNotNull(cmd);
        assertEquals(2, cmd.getArgs().length);
        assertEquals("arg1", cmd.getArgs()[0]);
        assertEquals("arg2", cmd.getArgs()[1]);
    }

    public void testProcessPropertiesNull() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        parser.setOptions(new Options());
        parser.cmd = new CommandLine();
        parser.processProperties(null);
        assertNotNull(parser.cmd);
    }

    public void testProcessPropertiesWithOptionWithoutArgValid() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("b", false, "beta"));
        parser.setOptions(options);
        parser.cmd = new CommandLine();

        Properties props = new Properties();
        props.setProperty("b", "true");

        parser.processProperties(props);
        assertTrue(parser.cmd.hasOption("b"));
    }

    public void testProcessPropertiesWithOptionWithoutArgInvalidValue() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("b", false, "beta"));
        parser.setOptions(options);
        parser.cmd = new CommandLine();

        Properties props = new Properties();
        props.setProperty("b", "false");

        parser.processProperties(props);
        assertFalse(parser.cmd.hasOption("b"));
    }

    public void testProcessPropertiesWithOptionWithArg() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("c", true, "charlie"));
        parser.setOptions(options);
        parser.cmd = new CommandLine();

        Properties props = new Properties();
        props.setProperty("c", "val");

        parser.processProperties(props);
        assertTrue(parser.cmd.hasOption("c"));
        assertEquals("val", parser.cmd.getOptionValue("c"));
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
            fail("Expected MissingOptionException");
        } catch (MissingOptionException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testProcessArgsMissingArgument() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        parser.setOptions(new Options());
        Option opt = new Option("d", true, "delta");
        
        List<String> list = new ArrayList<String>();
        ListIterator iter = list.listIterator();

        try {
            parser.processArgs(opt, iter);
            fail("Expected MissingArgumentException");
        } catch (MissingArgumentException e) {
            assertEquals(opt, e.getOption());
        }
    }

    public void testProcessArgsStopsAtNextOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(new Option("next", false, "next option"));
        parser.setOptions(options);

        Option opt = new Option("d", true, "delta");
        List<String> list = new ArrayList<String>();
        list.add("-next");
        ListIterator iter = list.listIterator();

        try {
            parser.processArgs(opt, iter);
            fail("Expected MissingArgumentException");
        } catch (MissingArgumentException e) {
            assertTrue(iter.hasPrevious());
            assertEquals("-next", iter.previous());
        }
    }

    public void testProcessOptionRequiredAndGroup() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        
        Option reqOpt = new Option("x", false, "req");
        reqOpt.setRequired(true);
        options.addOption(reqOpt);

        OptionGroup group = new OptionGroup();
        Option gOpt1 = new Option("y", false, "group1");
        Option gOpt2 = new Option("z", false, "group2");
        group.addOption(gOpt1);
        group.addOption(gOpt2);
        group.setRequired(true);
        options.addOptionGroup(group);

        parser.setOptions(options);
        parser.cmd = new CommandLine();

        List<String> list = new ArrayList<String>();
        ListIterator iter = list.listIterator();

        parser.processOption("-x", iter);
        parser.processOption("-y", iter);

        assertTrue(parser.cmd.hasOption("x"));
        assertTrue(parser.cmd.hasOption("y"));
        assertEquals(gOpt1, group.getSelected());
    }

    public void testParseWithPropertiesAndStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "arg" };
        Properties props = new Properties();
        CommandLine cmd = parser.parse(options, args, props, true);
        assertNotNull(cmd);
        assertEquals(1, cmd.getArgs().length);
    }
}