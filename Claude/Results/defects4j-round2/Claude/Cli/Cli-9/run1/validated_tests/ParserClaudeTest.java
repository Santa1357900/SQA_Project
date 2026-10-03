package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Vector;
import java.util.Enumeration;

public class ParserClaudeTest {

    // covers token startsWith '-' branch with recognized option, no argument
    @Test
    public void testParse_singleOptionNoArg_addsOption() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "option a");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"-a"});
        assertTrue(cmd.hasOption("a"));
    }

    // covers processArgs consuming a following value token
    @Test
    public void testParse_optionWithArgument_setsOptionValue() throws Throwable {
        Options options = new Options();
        options.addOption("b", true, "option b");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"-b", "value"});
        assertEquals("value", cmd.getOptionValue("b"));
    }



    // covers checkRequiredOptions when one required option missing (singular message)
    @Test
    public void testParse_missingRequiredOption_throwsMissingOptionException() throws Throwable {
        Options options = new Options();
        Option r = new Option("r", false, "required option");
        r.setRequired(true);
        options.addOption(r);
        PosixParser parser = new PosixParser();
        try {
            parser.parse(options, new String[0]);
            fail("expected MissingOptionException");
        } catch (MissingOptionException expected) {
            assertTrue(expected.getMessage().contains("Missing required option"));
        }
    }

    // covers checkRequiredOptions ternary branch for plural 'options'
    @Test
    public void testParse_multipleMissingRequiredOptions_pluralMessage() throws Throwable {
        Options options = new Options();
        Option p = new Option("p", false, "req p");
        p.setRequired(true);
        Option q = new Option("q", false, "req q");
        q.setRequired(true);
        options.addOption(p);
        options.addOption(q);
        PosixParser parser = new PosixParser();
        try {
            parser.parse(options, new String[0]);
            fail("expected MissingOptionException");
        } catch (MissingOptionException expected) {
            assertTrue(expected.getMessage().contains("Missing required options"));
        }
    }

    // covers processOption removing satisfied required option from requiredOptions list
    @Test
    public void testParse_requiredOptionProvided_noExceptionAndOptionPresent() throws Throwable {
        Options options = new Options();
        Option r = new Option("r", false, "required option");
        r.setRequired(true);
        options.addOption(r);
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"-r"});
        assertTrue(cmd.hasOption("r"));
    }

    // covers processArgs missing value at end of tokens
    @Test
    public void testParse_missingArgumentValue_throwsMissingArgumentException() throws Throwable {
        Options options = new Options();
        options.addOption("b", true, "option b");
        PosixParser parser = new PosixParser();
        try {
            parser.parse(options, new String[] {"-b"});
            fail("expected MissingArgumentException");
        } catch (MissingArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing argument"));
        }
    }

    // covers processArgs branch where next token is itself a recognized option (no value consumed)
    @Test
    public void testParse_argFollowedByRecognizedOption_throwsMissingArgumentException() throws Throwable {
        Options options = new Options();
        options.addOption("b", true, "option b");
        options.addOption("c", false, "option c");
        PosixParser parser = new PosixParser();
        try {
            parser.parse(options, new String[] {"-b", "-c"});
            fail("expected MissingArgumentException");
        } catch (MissingArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing argument"));
        }
    }

    // covers '--' branch, eatTheRest loop, and skipping any additional '--' tokens
    @Test
    public void testParse_doubleDash_stopsOptionParsingAndSkipsExtraDashes() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "option a");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"-a", "--", "x", "--", "y"});
        assertTrue(cmd.hasOption("a"));
        List args = cmd.getArgList();
        assertEquals(2, args.size());
        assertEquals("x", args.get(0));
        assertEquals("y", args.get(1));
    }

    // covers '-' token branch when stopAtNonOption is false
    @Test
    public void testParse_singleDashNotStopAtNonOption_addsDashAsArg() throws Throwable {
        Options options = new Options();
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"-"});
        List args = cmd.getArgList();
        assertEquals(1, args.size());
        assertEquals("-", args.get(0));
    }

    // covers '-' token branch when stopAtNonOption true (dash itself not added, rest eaten)
    @Test
    public void testParse_singleDashStopAtNonOption_eatsRestExcludingDash() throws Throwable {
        Options options = new Options();
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"-", "extra"}, true);
        List args = cmd.getArgList();
        assertEquals(1, args.size());
        assertEquals("extra", args.get(0));
    }



    // covers plain-argument branch with stopAtNonOption true
    @Test
    public void testParse_stopAtNonOptionPlainArgument_eatsRest() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "option a");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"arg1", "-a"}, true);
        List args = cmd.getArgList();
        assertEquals(2, args.size());
        assertEquals("arg1", args.get(0));
        assertEquals("-a", args.get(1));
        assertFalse(cmd.hasOption("a"));
    }

    // covers plain-argument branch with stopAtNonOption false: parsing continues afterwards
    @Test
    public void testParse_plainArgumentNotStopAtNonOption_continuesProcessingOptions() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "option a");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"arg1", "-a"});
        List args = cmd.getArgList();
        assertEquals(1, args.size());
        assertEquals("arg1", args.get(0));
        assertTrue(cmd.hasOption("a"));
    }

    // covers arguments == null -> replaced with empty array
    @Test
    public void testParse_nullArguments_returnsEmptyCommandLine() throws Throwable {
        Options options = new Options();
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, null);
        assertEquals(0, cmd.getArgs().length);
    }

    // covers parse(Options, String[], boolean) overload delegation
    @Test
    public void testParse_threeArgOverloadStopAtNonOption_delegatesCorrectly() throws Throwable {
        Options options = new Options();
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"nonopt"}, true);
        List args = cmd.getArgList();
        assertEquals(1, args.size());
        assertEquals("nonopt", args.get(0));
    }

    // covers parse(Options, String[], Properties) overload delegation
    @Test
    public void testParse_propertiesOverload_appliesProperties() throws Throwable {
        Options options = new Options();
        options.addOption("f", false, "flag f");
        Properties props = new Properties();
        props.setProperty("f", "true");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("f"));
    }

    // covers processProperties boolean-option valid value ("1") adds option
    @Test
    public void testParseProperties_booleanTrueValue_addsOption() throws Throwable {
        Options options = new Options();
        options.addOption("f", false, "flag f");
        Properties props = new Properties();
        props.setProperty("f", "1");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("f"));
    }

    // covers processProperties boolean-option invalid value not added (single entry)
    @Test
    public void testParseProperties_booleanFalseValueSingleEntry_optionNotAdded() throws Throwable {
        Options options = new Options();
        options.addOption("f", false, "flag f");
        Properties props = new Properties();
        props.setProperty("f", "false");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertFalse(cmd.hasOption("f"));
    }

    // covers processProperties skip when cmd.hasOption already true from command line args
    @Test
    public void testParseProperties_alreadySetOptionFromArgs_notOverriddenByProperties() throws Throwable {
        Options options = new Options();
        options.addOption("b", true, "option b");
        Properties props = new Properties();
        props.setProperty("b", "propValue");
        PosixParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, new String[] {"-b", "argValue"}, props);
        assertEquals("argValue", cmd.getOptionValue("b"));
    }



    // covers processArgs loop with zero iterations and missing-argument check
    @Test
    public void testProcessArgs_noValuesNotOptionalArg_throwsMissingArgumentException() throws Throwable {
        Parser parser = new PosixParser();
        Option opt = new Option("t", true, "desc");
        List tokens = new ArrayList();
        ListIterator iter = tokens.listIterator();
        try {
            parser.processArgs(opt, iter);
            fail("expected MissingArgumentException");
        } catch (MissingArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing argument"));
        }
    }

    // covers processArgs loop consuming a single plain value
    @Test
    public void testProcessArgs_singleValue_addsValueToOption() throws Throwable {
        Parser parser = new PosixParser();
        Options options = new Options();
        options.addOption("t", true, "desc");
        parser.setOptions(options);

        Option opt = new Option("t", true, "desc");
        List tokens = new ArrayList();
        tokens.add("value1");
        ListIterator iter = tokens.listIterator();

        parser.processArgs(opt, iter);

        assertEquals("value1", opt.getValues()[0]);
    }

    // covers processArgs branch where next token is itself a defined option -> break without consuming
    @Test
    public void testProcessArgs_nextTokenIsRecognizedOption_breaksWithoutConsumingValue() throws Throwable {
        Parser parser = new PosixParser();
        Options options = new Options();
        options.addOption("t", true, "desc");
        options.addOption("c", false, "desc c");
        parser.setOptions(options);

        Option opt = new Option("t", true, "desc");
        List tokens = new ArrayList();
        tokens.add("-c");
        ListIterator iter = tokens.listIterator();

        try {
            parser.processArgs(opt, iter);
            fail("expected MissingArgumentException");
        } catch (MissingArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing argument"));
        }
    }

    // covers processOption's hasOption==false branch (direct call)
    @Test
    public void testProcessOption_unrecognizedOption_throwsUnrecognizedOptionException() throws Throwable {
        Parser parser = new PosixParser();
        Options options = new Options();
        parser.setOptions(options);
        List tokens = new ArrayList();
        ListIterator iter = tokens.listIterator();
        try {
            parser.processOption("-z", iter);
            fail("expected UnrecognizedOptionException");
        } catch (UnrecognizedOptionException expected) {
            assertTrue(expected.getMessage().contains("Unrecognized option"));
        }
    }

    // covers processOption success path for option without argument (direct call)
    @Test
    public void testProcessOption_recognizedNoArgOption_addsToCommandLine() throws Throwable {
        Parser parser = new PosixParser();
        Options options = new Options();
        options.addOption("v", false, "verbose");
        parser.setOptions(options);
        parser.cmd = new CommandLine();

        List tokens = new ArrayList();
        ListIterator iter = tokens.listIterator();
        parser.processOption("-v", iter);

        assertTrue(parser.cmd.hasOption("v"));
    }

    /**
     * Helper Properties subclass that guarantees a deterministic iteration
     * order for propertyNames(), regardless of Hashtable bucket layout.
     */
    private static class OrderedProperties extends Properties {

        private Vector orderedKeys = new Vector();

        public void setOrdered(String key, String value) {
            this.setProperty(key, value);
            orderedKeys.add(key);
        }

        public synchronized Enumeration propertyNames() {
            return orderedKeys.elements();
        }
    }
}
