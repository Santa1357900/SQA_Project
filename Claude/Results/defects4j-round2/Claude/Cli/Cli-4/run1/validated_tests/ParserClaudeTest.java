package org.apache.commons.cli;

import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParserClaudeTest {

    private GnuParser parser;

    @Before
    public void setUp() throws Throwable {
        parser = new GnuParser();
    }

    // arguments == null -> treated as empty array, no args, no options
    @Test
    public void testParse_nullArguments_returnsEmptyArgList() throws Throwable {
        Options options = new Options();
        CommandLine cmd = parser.parse(options, null);
        assertNotNull(cmd);
        assertTrue(cmd.getArgList().isEmpty());
    }

    // arguments == empty array -> no args
    @Test
    public void testParse_emptyArguments_returnsEmptyArgList() throws Throwable {
        Options options = new Options();
        CommandLine cmd = parser.parse(options, new String[0]);
        assertTrue(cmd.getArgList().isEmpty());
    }

    // "-" token with stopAtNonOption=false -> added as plain arg
    @Test
    public void testParse_singleDashNotStopAtNonOption_addedAsArg() throws Throwable {
        Options options = new Options();
        String[] args = new String[] { "-" };
        CommandLine cmd = parser.parse(options, args, false);
        assertTrue(cmd.getArgList().contains("-"));
    }

    // "-" token with stopAtNonOption=true -> eats remaining args, "-" itself not added
    @Test
    public void testParse_singleDashStopAtNonOption_eatsRemainingArgsExcludingDash() throws Throwable {
        Options options = new Options();
        String[] args = new String[] { "-", "foo" };
        CommandLine cmd = parser.parse(options, args, true);
        assertFalse(cmd.getArgList().contains("-"));
        assertTrue(cmd.getArgList().contains("foo"));
    }

    // "--" stops option processing, remaining tokens become plain args
    @Test
    public void testParse_doubleDash_stopsProcessingAndAddsRemainingArgsAsPlainArgs() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("x", false, "descX"));
        String[] args = new String[] { "--", "-x" };
        CommandLine cmd = parser.parse(options, args);
        assertTrue(cmd.getArgList().contains("-x"));
        assertFalse(cmd.hasOption("x"));
    }

    // a second "--" encountered while eating the rest must never be added as an arg
    @Test
    public void testParse_multipleDoubleDashTokens_doubleDashNeverAddedAsArg() throws Throwable {
        Options options = new Options();
        String[] args = new String[] { "--", "--", "foo" };
        CommandLine cmd = parser.parse(options, args);
        assertFalse(cmd.getArgList().contains("--"));
        assertTrue(cmd.getArgList().contains("foo"));
    }

    // unrecognized option token -> UnrecognizedOptionException
    @Test
    public void testParse_unrecognizedOption_throwsUnrecognizedOptionException() throws Throwable {
        Options options = new Options();
        String[] args = new String[] { "-z" };
        try {
            parser.parse(options, args);
            fail("expected UnrecognizedOptionException");
        } catch (UnrecognizedOptionException expected) {
            assertTrue(expected.getMessage().contains("Unrecognized option"));
        }
    }

    // recognized option without arg -> option added to command line
    @Test
    public void testParse_recognizedOptionNoArg_addsOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "descA"));
        String[] args = new String[] { "-a" };
        CommandLine cmd = parser.parse(options, args);
        assertTrue(cmd.hasOption("a"));
    }

    // option with arg -> value captured
    @Test
    public void testParse_optionWithArgProvided_setsValue() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("b", true, "descB"));
        String[] args = new String[] { "-b", "value1" };
        CommandLine cmd = parser.parse(options, args);
        assertEquals("value1", cmd.getOptionValue("b"));
    }

    // option requiring arg but none supplied -> MissingArgumentException
    @Test
    public void testParse_optionWithArgMissing_throwsMissingArgumentException() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("b", true, "descB"));
        String[] args = new String[] { "-b" };
        try {
            parser.parse(options, args);
            fail("expected MissingArgumentException");
        } catch (MissingArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing argument"));
        }
    }

    // arg consumption for an option stops when the next recognized option is reached
    @Test
    public void testParse_optionArgStopsAtNextRecognizedOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("b", true, "descB"));
        options.addOption(new Option("c", false, "descC"));
        String[] args = new String[] { "-b", "val", "-c" };
        CommandLine cmd = parser.parse(options, args);
        assertEquals("val", cmd.getOptionValue("b"));
        assertTrue(cmd.hasOption("c"));
    }

    // non-option argument, not stopping, subsequent option still processed
    @Test
    public void testParse_nonOptionArgumentNotStop_continuesProcessingOptions() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "descA"));
        String[] args = new String[] { "foo", "-a" };
        CommandLine cmd = parser.parse(options, args, false);
        assertTrue(cmd.getArgList().contains("foo"));
        assertTrue(cmd.hasOption("a"));
    }

    // non-option argument with stopAtNonOption=true -> eats rest including subsequent option token
    @Test
    public void testParse_nonOptionArgumentStopAtNonOption_eatsRemainingIncludingOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "descA"));
        String[] args = new String[] { "foo", "-a" };
        CommandLine cmd = parser.parse(options, args, true);
        assertTrue(cmd.getArgList().contains("foo"));
        assertTrue(cmd.getArgList().contains("-a"));
        assertFalse(cmd.hasOption("a"));
    }

    // unrecognized dash token with stopAtNonOption=true -> added as arg, no exception
    @Test
    public void testParse_unrecognizedDashArgStopAtNonOption_addedAsArgNoException() throws Throwable {
        Options options = new Options();
        String[] args = new String[] { "-z" };
        CommandLine cmd = parser.parse(options, args, true);
        assertTrue(cmd.getArgList().contains("-z"));
        assertFalse(cmd.hasOption("z"));
    }

    // required option missing -> MissingOptionException with key in message
    @Test
    public void testParse_requiredOptionMissing_throwsMissingOptionException() throws Throwable {
        Options options = new Options();
        Option req = new Option("r", false, "required option");
        req.setRequired(true);
        options.addOption(req);
        String[] args = new String[0];
        try {
            parser.parse(options, args);
            fail("expected MissingOptionException");
        } catch (MissingOptionException expected) {
            assertTrue(expected.getMessage().contains("r"));
        }
    }

    // required option provided -> no exception, option set
    @Test
    public void testParse_requiredOptionProvided_noExceptionAndOptionSet() throws Throwable {
        Options options = new Options();
        Option req = new Option("r", false, "required option");
        req.setRequired(true);
        options.addOption(req);
        String[] args = new String[] { "-r" };
        CommandLine cmd = parser.parse(options, args);
        assertTrue(cmd.hasOption("r"));
    }

    // only one of two required options provided -> only the missing one reported
    @Test
    public void testParse_requiredOptionPartiallyProvided_onlyMissingOneReported() throws Throwable {
        Options options = new Options();
        Option r1 = new Option("r1", false, "req1");
        r1.setRequired(true);
        Option r2 = new Option("r2", false, "req2");
        r2.setRequired(true);
        options.addOption(r1);
        options.addOption(r2);
        String[] args = new String[] { "-r1" };
        try {
            parser.parse(options, args);
            fail("expected MissingOptionException");
        } catch (MissingOptionException expected) {
            assertTrue(expected.getMessage().contains("r2"));
            assertFalse(expected.getMessage().contains("r1"));
        }
    }

    // properties == null -> no effect, method returns without adding options
    @Test
    public void testParse_propertiesNull_noOptionsAdded() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "descA"));
        CommandLine cmd = parser.parse(options, new String[0], (Properties) null);
        assertFalse(cmd.hasOption("a"));
    }

    // property supplies value for an option that takes an argument
    @Test
    public void testParse_propertiesOptionWithArg_setsValueFromProperties() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("f", true, "descF"));
        Properties props = new Properties();
        props.setProperty("f", "hello");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertEquals("hello", cmd.getOptionValue("f"));
    }

    // property value "true" for boolean option -> option added
    @Test
    public void testParse_propertiesBooleanOptionValidValue_addsOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "descV"));
        Properties props = new Properties();
        props.setProperty("v", "true");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("v"));
    }

    // property value not yes/true/1 for boolean option -> option NOT added
    @Test
    public void testParse_propertiesBooleanOptionInvalidValue_optionNotAdded() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "descV"));
        Properties props = new Properties();
        props.setProperty("v", "maybe");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertFalse(cmd.hasOption("v"));
    }

    // option already present on command line from args -> property value not overriding it
    @Test
    public void testParse_propertiesOptionAlreadySetOnCommandLine_notOverridden() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("f", true, "descF"));
        Properties props = new Properties();
        props.setProperty("f", "propVal");
        String[] args = new String[] { "-f", "argVal" };
        CommandLine cmd = parser.parse(options, args, props);
        assertEquals("argVal", cmd.getOptionValue("f"));
    }



    // reused Options instance across multiple parse calls clears previous option values (CLI-71)
    @Test
    public void testParse_reusedOptionsAcrossMultipleParses_valuesAreCleared() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("x", true, "descX"));

        parser.parse(options, new String[] { "-x", "first" });
        CommandLine cmd2 = parser.parse(options, new String[] { "-x", "second" });

        assertEquals("second", cmd2.getOptionValue("x"));
    }

    // multiple plain non-option arguments, all collected when not stopping
    @Test
    public void testParse_multipleNonOptionArgs_allAddedWhenNotStopAtNonOption() throws Throwable {
        Options options = new Options();
        String[] args = new String[] { "foo", "bar", "baz" };
        CommandLine cmd = parser.parse(options, args, false);
        List argList = cmd.getArgList();
        assertEquals(3, argList.size());
        assertTrue(argList.contains("foo"));
        assertTrue(argList.contains("bar"));
        assertTrue(argList.contains("baz"));
    }

    // quoted option value has leading/trailing quotes stripped
    @Test
    public void testParse_optionValueWithQuotes_stripsLeadingAndTrailingQuotes() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("b", true, "descB"));
        String[] args = new String[] { "-b", "\"quoted\"" };
        CommandLine cmd = parser.parse(options, args);
        assertEquals("quoted", cmd.getOptionValue("b"));
    }

    // parse(Options, String[]) overload delegates correctly and returns non-null command line
    @Test
    public void testParse_twoArgOverload_delegatesToFullParse() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", false, "descA"));
        CommandLine cmd = parser.parse(options, new String[] { "-a" });
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("a"));
    }

    // parse(Options, String[], boolean) overload with stopAtNonOption true delegates correctly
    @Test
    public void testParse_threeArgOverloadWithStop_delegatesCorrectly() throws Throwable {
        Options options = new Options();
        String[] args = new String[] { "foo", "-a" };
        CommandLine cmd = parser.parse(options, args, true);
        assertTrue(cmd.getArgList().contains("foo"));
        assertTrue(cmd.getArgList().contains("-a"));
    }
}
