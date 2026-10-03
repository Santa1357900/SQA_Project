package org.apache.commons.cli;

import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParserClaudeTest {

    private PosixParser parser;

    @Before
    public void setUp() throws Throwable {
        parser = new PosixParser();
    }

    // covers setOptions()/getOptions(): stored instance is returned unchanged
    @Test
    public void testSetOptionsGetOptions_returnsSameInstance() throws Throwable {
        Options options = new Options();
        parser.setOptions(options);
        assertSame(options, parser.getOptions());
    }

    // covers getRequiredOptions(): reflects the required option keys of the Options object
    @Test
    public void testGetRequiredOptions_reflectsOptionsRequiredList() throws Throwable {
        Option optR = new Option("r", false, "required option");
        optR.setRequired(true);
        Options options = new Options();
        options.addOption(optR);
        parser.setOptions(options);
        List required = parser.getRequiredOptions();
        assertTrue(required.contains("r"));
    }

    // covers parse(Options,String[]) delegating to full parse, option recognized
    @Test
    public void testParseOptionsArguments_recognizedOption_addsOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        CommandLine cmd = parser.parse(options, new String[] {"-v"});
        assertTrue(cmd.hasOption("v"));
    }

    // covers parse(Options,String[],Properties) delegating properly, applies property when absent from args
    @Test
    public void testParseOptionsArgumentsProperties_appliesPropertiesWhenNotInArgs() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("p", false, "prop option"));
        Properties props = new Properties();
        props.setProperty("p", "true");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("p"));
    }

    // covers parse(Options,String[],boolean) with false, normal option parsing
    @Test
    public void testParseOptionsArgumentsStopAtNonOption_false_parsesNormally() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        CommandLine cmd = parser.parse(options, new String[] {"-v"}, false);
        assertTrue(cmd.hasOption("v"));
    }

    // covers parse(Options,String[],boolean) with true, stops at first non-option and eats the rest
    @Test
    public void testParseOptionsArgumentsStopAtNonOption_true_stopsAtFirstNonOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        CommandLine cmd = parser.parse(options, new String[] {"nonopt", "-v"}, true);
        assertFalse(cmd.hasOption("v"));
        List argList = cmd.getArgList();
        assertTrue(argList.contains("nonopt"));
        assertTrue(argList.contains("-v"));
    }

    // covers "arguments == null" defensive branch in full parse
    @Test
    public void testParse_nullArguments_treatedAsEmptyArray() throws Throwable {
        Options options = new Options();
        CommandLine cmd = parser.parse(options, null);
        assertEquals(0, cmd.getArgs().length);
    }

    // covers empty arguments array resulting in empty CommandLine
    @Test
    public void testParse_emptyArguments_returnsEmptyCommandLine() throws Throwable {
        Options options = new Options();
        CommandLine cmd = parser.parse(options, new String[0]);
        assertEquals(0, cmd.getArgs().length);
    }

    // covers "--" branch: stops option processing, only one dash token consumed
    @Test
    public void testParse_doubleDash_stopsOptionParsing() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        CommandLine cmd = parser.parse(options, new String[] {"--", "-v"});
        assertFalse(cmd.hasOption("v"));
        assertTrue(cmd.getArgList().contains("-v"));
    }

    // covers "-" branch with stopAtNonOption false: added as an arg
    @Test
    public void testParse_singleDashNotStopAtNonOption_addsDashAsArg() throws Throwable {
        Options options = new Options();
        CommandLine cmd = parser.parse(options, new String[] {"-"});
        assertTrue(cmd.getArgList().contains("-"));
    }

    // covers "-" branch with stopAtNonOption true: dash itself not added, rest eaten as args
    @Test
    public void testParse_singleDashStopAtNonOption_eatsRestWithoutAddingDashItself() throws Throwable {
        Options options = new Options();
        CommandLine cmd = parser.parse(options, new String[] {"-", "x"}, true);
        assertFalse(cmd.getArgList().contains("-"));
        assertTrue(cmd.getArgList().contains("x"));
    }

    // covers unrecognized "-x" option with stopAtNonOption false: throws UnrecognizedOptionException
    @Test
    public void testParse_unrecognizedOptionNotStopAtNonOption_throwsUnrecognizedOptionException() throws Throwable {
        Options options = new Options();
        try {
            parser.parse(options, new String[] {"-x"});
            fail("expected UnrecognizedOptionException");
        } catch (UnrecognizedOptionException expected) {
            assertTrue(expected.getMessage().contains("-x"));
        }
    }

    // covers unrecognized "-x" option with stopAtNonOption true: added as arg, rest eaten
    @Test
    public void testParse_unrecognizedOptionStopAtNonOption_addsAsArgAndEatsRest() throws Throwable {
        Options options = new Options();
        CommandLine cmd = parser.parse(options, new String[] {"-x", "y"}, true);
        assertTrue(cmd.getArgList().contains("-x"));
        assertTrue(cmd.getArgList().contains("y"));
    }

    // covers plain argument branch with stopAtNonOption false: subsequent option still processed
    @Test
    public void testParse_nonOptionArgumentNotStopAtNonOption_continuesProcessing() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        CommandLine cmd = parser.parse(options, new String[] {"arg1", "-v"});
        assertTrue(cmd.getArgList().contains("arg1"));
        assertTrue(cmd.hasOption("v"));
    }

    // covers plain argument branch with stopAtNonOption true: eats remaining tokens as args
    @Test
    public void testParse_nonOptionArgumentStopAtNonOption_eatsRemainingArgs() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        CommandLine cmd = parser.parse(options, new String[] {"arg1", "-v"}, true);
        assertTrue(cmd.getArgList().contains("arg1"));
        assertTrue(cmd.getArgList().contains("-v"));
        assertFalse(cmd.hasOption("v"));
    }

    // covers CLI-71 clearing loop: reused Options object does not retain stale values
    @Test
    public void testParse_reusedOptionsObject_clearsPreviousValues() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "arg option"));
        CommandLine cmd1 = parser.parse(options, new String[] {"-a", "first"});
        assertEquals("first", cmd1.getOptionValue("a"));
        CommandLine cmd2 = parser.parse(options, new String[0]);
        assertFalse(cmd2.hasOption("a"));
    }

    // covers processOption required-option removal branch, no exception thrown
    @Test
    public void testProcessOption_requiredOptionPresent_removedFromRequiredAndNoException() throws Throwable {
        Options options = new Options();
        Option optR = new Option("r", false, "required");
        optR.setRequired(true);
        options.addOption(optR);
        CommandLine cmd = parser.parse(options, new String[] {"-r"});
        assertTrue(cmd.hasOption("r"));
    }

    // covers processOption OptionGroup required branch, satisfied selection
    @Test
    public void testProcessOption_requiredOptionGroupSatisfied_noException() throws Throwable {
        Options options = new Options();
        Option optA = new Option("a", false, "option a");
        Option optB = new Option("b", false, "option b");
        OptionGroup group = new OptionGroup();
        group.addOption(optA);
        group.addOption(optB);
        group.setRequired(true);
        options.addOptionGroup(group);
        CommandLine cmd = parser.parse(options, new String[] {"-a"});
        assertTrue(cmd.hasOption("a"));
    }

    // covers processArgs collecting a single value for an option requiring an argument
    @Test
    public void testProcessArgs_singleValue_setsOptionValue() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "arg option"));
        CommandLine cmd = parser.parse(options, new String[] {"-a", "value1"});
        assertEquals("value1", cmd.getOptionValue("a"));
    }

    // covers processArgs with no following token: throws MissingArgumentException
    @Test
    public void testProcessArgs_missingArgumentAtEnd_throwsMissingArgumentException() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "arg option"));
        try {
            parser.parse(options, new String[] {"-a"});
            fail("expected MissingArgumentException");
        } catch (MissingArgumentException expected) {
            assertTrue(expected.getMessage().contains("a"));
        }
    }

    // covers processArgs stopping when the next token is a recognized option, leaving value missing
    @Test
    public void testProcessArgs_stopsAtNextRecognizedOption_throwsMissingArgumentException() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "arg option"));
        options.addOption(new Option("b", false, "flag option"));
        try {
            parser.parse(options, new String[] {"-a", "-b"});
            fail("expected MissingArgumentException");
        } catch (MissingArgumentException expected) {
            assertTrue(expected.getMessage().contains("a"));
        }
    }

    // covers checkRequiredOptions single missing required option, message contains its key
    @Test
    public void testCheckRequiredOptions_singleMissing_throwsWithOptionKeyInMessage() throws Throwable {
        Options options = new Options();
        Option optR = new Option("r", false, "required");
        optR.setRequired(true);
        options.addOption(optR);
        try {
            parser.parse(options, new String[0]);
            fail("expected MissingOptionException");
        } catch (MissingOptionException expected) {
            assertTrue(expected.getMessage().contains("r"));
        }
    }

    // covers checkRequiredOptions multiple missing required options, message contains all keys
    @Test
    public void testCheckRequiredOptions_multipleMissing_messageContainsAllKeys() throws Throwable {
        Options options = new Options();
        Option optR1 = new Option("r1", false, "required 1");
        optR1.setRequired(true);
        Option optR2 = new Option("r2", false, "required 2");
        optR2.setRequired(true);
        options.addOption(optR1);
        options.addOption(optR2);
        try {
            parser.parse(options, new String[0]);
            fail("expected MissingOptionException");
        } catch (MissingOptionException expected) {
            assertTrue(expected.getMessage().indexOf("r1") >= 0);
            assertTrue(expected.getMessage().indexOf("r2") >= 0);
        }
    }

    // covers processProperties null-properties early return branch
    @Test
    public void testProcessProperties_nullProperties_doesNothing() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("v", false, "verbose"));
        CommandLine cmd = parser.parse(options, new String[0], (Properties) null);
        assertFalse(cmd.hasOption("v"));
    }

    // covers processProperties skip branch when option already present on the command line
    @Test
    public void testProcessProperties_optionAlreadyOnCommandLine_notOverwritten() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "arg option"));
        Properties props = new Properties();
        props.setProperty("a", "propValue");
        CommandLine cmd = parser.parse(options, new String[] {"-a", "cliValue"}, props);
        assertEquals("cliValue", cmd.getOptionValue("a"));
    }

    // covers processProperties hasArg branch adding a value when none exists yet
    @Test
    public void testProcessProperties_hasArgOptionNoExistingValue_addsValueFromProperties() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("a", true, "arg option"));
        Properties props = new Properties();
        props.setProperty("a", "propValue");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertEquals("propValue", cmd.getOptionValue("a"));
    }

    // covers processProperties trigger value "yes" (case-insensitive) adds boolean option
    @Test
    public void testProcessProperties_triggerValueYes_addsOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("y", false, "yes option"));
        Properties props = new Properties();
        props.setProperty("y", "YES");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("y"));
    }

    // covers processProperties trigger value "1" adds boolean option
    @Test
    public void testProcessProperties_triggerValueNumericOne_addsOption() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("o", false, "one option"));
        Properties props = new Properties();
        props.setProperty("o", "1");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("o"));
    }

    // covers processProperties invalid trigger value: current option is not added
    @Test
    public void testProcessProperties_invalidTriggerValue_optionNotAdded() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("n", false, "no option"));
        Properties props = new Properties();
        props.setProperty("n", "maybe");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertFalse(cmd.hasOption("n"));
    }

    // BUG ORACLE: an invalid boolean-trigger property must only skip that option,
    // not stop processing of the remaining properties (contract: continue, not break)
    @Test
    public void testProcessProperties_invalidTriggerValue_doesNotStopProcessingRemainingProperties() throws Throwable {
        Options options = new Options();
        options.addOption(new Option("x", false, "option x"));
        options.addOption(new Option("y", false, "option y"));
        final Vector orderedKeys = new Vector();
        orderedKeys.add("x");
        orderedKeys.add("y");
        Properties props = new Properties() {
            public Enumeration propertyNames() {
                return orderedKeys.elements();
            }
        };
        props.setProperty("x", "no");
        props.setProperty("y", "true");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertFalse(cmd.hasOption("x"));
        assertTrue(cmd.hasOption("y"));
    }
}
