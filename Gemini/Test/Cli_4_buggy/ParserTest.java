package org.apache.commons.cli;

import junit.framework.TestCase;
import java.util.Properties;
import java.util.ListIterator;
import java.util.Arrays;

public class ParserTest extends TestCase {

    private static class ConcreteParser extends Parser {
        protected String[] flatten(Options opts, String[] arguments, boolean stopAtNonOption) {
            return arguments;
        }
    }

    public void testParseNullArguments() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        CommandLine cl = parser.parse(options, null);
        assertNotNull(cl);
    }

    public void testParseDoubleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[]{"--", "arg1", "arg2"};
        CommandLine cl = parser.parse(options, args);
        assertNotNull(cl);
        assertTrue(cl.getArgs().contains("arg1"));
        assertTrue(cl.getArgs().contains("arg2"));
    }

    public void testParseSingleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[]{"-"};
        CommandLine cl = parser.parse(options, args);
        assertNotNull(cl);
        assertTrue(cl.getArgs().contains("-"));
    }

    public void testParseSingleDashStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[]{"-", "extra"};
        CommandLine cl = parser.parse(options, args, true);
        assertNotNull(cl);
        assertTrue(cl.getArgs().contains("extra"));
    }

    public void testParseUnrecognizedOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[]{"-unknown"};
        try {
            parser.parse(options, args);
            fail("UnrecognizedOptionException should have been thrown");
        } catch (UnrecognizedOptionException e) {
            assertTrue(e.getMessage().contains("-unknown"));
        }
    }

    public void testParseStopAtNonOptionUnrecognized() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[]{"-unknown", "extra"};
        CommandLine cl = parser.parse(options, args, true);
        assertNotNull(cl);
        assertTrue(cl.getArgs().contains("-unknown"));
        assertTrue(cl.getArgs().contains("extra"));
    }

    public void testParseWithPropertiesBooleanTrue() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("b", false, "boolean option");
        Properties props = new Properties();
        props.setProperty("b", "true");
        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertTrue(cl.hasOption("b"));
    }

    public void testParseWithPropertiesBooleanYes() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("b", false, "boolean option");
        Properties props = new Properties();
        props.setProperty("b", "yes");
        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertTrue(cl.hasOption("b"));
    }

    public void testParseWithPropertiesBooleanOne() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("b", false, "boolean option");
        Properties props = new Properties();
        props.setProperty("b", "1");
        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertTrue(cl.hasOption("b"));
    }

    public void testParseWithPropertiesBooleanInvalid() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("b", false, "boolean option");
        Properties props = new Properties();
        props.setProperty("b", "invalid");
        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertFalse(cl.hasOption("b"));
    }

    public void testParseWithPropertiesWithArg() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(OptionBuilder.withArgName("arg").hasArg().create("a"));
        Properties props = new Properties();
        props.setProperty("a", "val");
        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertTrue(cl.hasOption("a"));
        assertEquals("val", cl.getOptionValue("a"));
    }

    public void testCheckRequiredOptionsMissing() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption(OptionBuilder.isRequired().create("req"));
        try {
            parser.parse(options, new String[0]);
            fail("MissingOptionException should have been thrown");
        } catch (MissingOptionException e) {
            assertTrue(e.getMessage().contains("req"));
        }
    }

    public void testProcessArgsMissingArgument() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option opt = OptionBuilder.hasArg().create("a");
        options.addOption(opt);
        
        List list = Arrays.asList(new String[]{"-other"});
        ListIterator iter = list.listIterator();
        try {
            parser.processArgs(opt, iter);
            fail("MissingArgumentException should have been thrown");
        } catch (MissingArgumentException e) {
            assertTrue(e.getMessage().contains("a"));
        }
    }

    public void testProcessArgsValid() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option opt = OptionBuilder.hasArg().create("a");
        options.addOption(opt);
        
        List list = Arrays.asList(new String[]{"value"});
        ListIterator iter = list.listIterator();
        parser.processArgs(opt, iter);
        assertEquals("value", opt.getValue());
    }

    public void testProcessArgsHitsAnotherOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option optA = OptionBuilder.hasArg().create("a");
        Option optB = OptionBuilder.create("b");
        options.addOption(optA);
        options.addOption(optB);
        
        List list = Arrays.asList(new String[]{"-b"});
        ListIterator iter = list.listIterator();
        
        try {
            parser.processArgs(optA, iter);
            fail("MissingArgumentException expected");
        } catch (MissingArgumentException e) {
            // expected because -b is treated as option not argument
        }
    }
}