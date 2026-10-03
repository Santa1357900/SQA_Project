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
        CommandLine cl = parser.parse(options, (String[]) null);
        assertNotNull(cl);
    }

    public void testParseDoubleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        String[] args = new String[] { "--", "-a" };
        CommandLine cl = parser.parse(options, args);
        assertNotNull(cl);
        assertTrue(cl.hasOption("a") == false);
        assertTrue(cl.getArgs().length == 1);
        assertEquals("-a", cl.getArgs()[0]);
    }

    public void testParseSingleDash() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-" };
        CommandLine cl = parser.parse(options, args);
        assertNotNull(cl);
        assertTrue(cl.getArgs().length == 1);
        assertEquals("-", cl.getArgs()[0]);
    }

    public void testParseSingleDashWithStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-" };
        CommandLine cl = parser.parse(options, args, true);
        assertNotNull(cl);
        assertTrue(cl.getArgs().length == 0);
    }

    public void testParseUnrecognizedOptionWithStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "-unknown" };
        CommandLine cl = parser.parse(options, args, true);
        assertNotNull(cl);
        assertTrue(cl.getArgs().length == 1);
        assertEquals("-unknown", cl.getArgs()[0]);
    }

    public void testParseRecognizedOptionWithStopAtNonOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("a", false, "alpha");
        String[] args = new String[] { "-a", "extra" };
        CommandLine cl = parser.parse(options, args, true);
        assertNotNull(cl);
        assertTrue(cl.hasOption("a"));
        assertTrue(cl.getArgs().length == 1);
        assertEquals("extra", cl.getArgs()[0]);
    }

    public void testParseArgumentAndEatTheRest() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        String[] args = new String[] { "arg1", "--", "arg2", "--", "arg3" };
        CommandLine cl = parser.parse(options, args);
        assertNotNull(cl);
        assertEquals(3, cl.getArgs().length);
        assertEquals("arg1", cl.getArgs()[0]);
        assertEquals("arg2", cl.getArgs()[1]);
        assertEquals("arg3", cl.getArgs()[2]);
    }

    public void testProcessPropertiesWithValidValues() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("b", false, "boolean opt");
        options.addOption("s", true, "string opt");
        
        Properties props = new Properties();
        props.setProperty("b", "true");
        props.setProperty("s", "val");

        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertTrue(cl.hasOption("b"));
        assertTrue(cl.hasOption("s"));
        assertEquals("val", cl.getOptionValue("s"));
    }

    public void testProcessPropertiesWithYesAndOne() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("y", false, "yes opt");
        options.addOption("o", false, "one opt");

        Properties props = new Properties();
        props.setProperty("y", "yes");
        props.setProperty("o", "1");

        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertTrue(cl.hasOption("y"));
        assertTrue(cl.hasOption("o"));
    }

    public void testProcessPropertiesInvalidBoolean() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("i", false, "invalid opt");

        Properties props = new Properties();
        props.setProperty("i", "no");

        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertFalse(cl.hasOption("i"));
    }

    public void testProcessPropertiesWithExistingValuesInOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option opt = new Option("s", true, "string opt");
        opt.addValueForProcessing("existing");
        options.addOption(opt);

        Properties props = new Properties();
        props.setProperty("s", "newval");

        CommandLine cl = parser.parse(options, new String[0], props);
        assertNotNull(cl);
        assertTrue(cl.hasOption("s"));
        assertEquals("existing", cl.getOptionValue("s"));
    }

    public void testCheckRequiredOptionsMissing() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option opt = new Option("r", "req", true, "required");
        opt.setRequired(true);
        options.addOption(opt);

        try {
            parser.parse(options, new String[0]);
            fail("Expected MissingOptionException");
        } catch (MissingOptionException e) {
            assertTrue(e.getMessage().contains("r"));
        }
    }

    public void testCheckRequiredOptionsMultiple() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option opt1 = new Option("r1", "req1", true, "required1");
        opt1.setRequired(true);
        Option opt2 = new Option("r2", "req2", true, "required2");
        opt2.setRequired(true);
        options.addOption(opt1);
        options.addOption(opt2);

        try {
            parser.parse(options, new String[0]);
            fail("Expected MissingOptionException");
        } catch (MissingOptionException e) {
            assertTrue(e.getMessage().contains("Missing required options"));
        }
    }

    public void testUnrecognizedOptionException() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        try {
            parser.parse(options, new String[] { "-bad" });
            fail("Expected UnrecognizedOptionException");
        } catch (UnrecognizedOptionException e) {
            assertTrue(e.getMessage().contains("-bad"));
        }
    }

    public void testMissingArgumentException() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("a", true, "arg required");
        try {
            parser.parse(options, new String[] { "-a" });
            fail("Expected MissingArgumentException");
        } catch (MissingArgumentException e) {
            assertTrue(e.getMessage().contains("a"));
        }
    }

    public void testProcessArgsWithSubsequentOption() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        options.addOption("a", true, "arg required");
        options.addOption("b", false, "flag");
        
        CommandLine cl = parser.parse(options, new String[] { "-a", "-b" });
        assertNotNull(cl);
        assertFalse(cl.hasOption("a"));
        assertTrue(cl.hasOption("b"));
    }

    public void testOptionGroupRequired() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option opt1 = new Option("a", false, "opt a");
        Option opt2 = new Option("b", false, "opt b");
        OptionGroup group = new OptionGroup();
        group.addOption(opt1);
        group.addOption(opt2);
        group.setRequired(true);
        options.addOptionGroup(group);

        CommandLine cl = parser.parse(options, new String[] { "-a" });
        assertNotNull(cl);
        assertTrue(cl.hasOption("a"));
    }

    public void testProcessArgsOptionalArgument() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        Option opt = new Option("a", true, "optional arg");
        opt.setOptionalArg(true);
        options.addOption(opt);

        CommandLine cl = parser.parse(options, new String[] { "-a" });
        assertNotNull(cl);
        assertTrue(cl.hasOption("a"));
        assertNull(cl.getOptionValues("a"));
    }

    public void testProcessPropertiesNull() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Options options = new Options();
        parser.setOptions(options);
        parser.cmd = new CommandLine();
        parser.processProperties(null);
        assertTrue(true);
    }
}