package org.apache.commons.cli;

import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefaultParserClaudeTest
{
    private DefaultParser parser;
    private Options options;

    @Before
    public void setUp() throws Throwable
    {
        parser = new DefaultParser();
        options = new Options();
    }

    // covers: handleToken -> handleShortAndLongOption -> t.length()==1 -> hasShortOption true
    @Test
    public void testParse_simpleShortOption_optionPresent() throws Throwable {
        options.addOption("a", false, "flag a");
        CommandLine cmd = parser.parse(options, new String[] {"-a"});
        assertTrue(cmd.hasOption("a"));
    }

    // covers: handleToken -> currentOption acceptsArg && isArgument branch for separate value token
    @Test
    public void testParse_shortOptionWithSeparateArg_valueCaptured() throws Throwable {
        options.addOption("f", true, "file");
        CommandLine cmd = parser.parse(options, new String[] {"-f", "test.txt"});
        assertEquals("test.txt", cmd.getOptionValue("f"));
    }

    // covers: handleShortAndLongOption -> getLongPrefix null, isJavaProperty false -> handleConcatenatedOptions attached value
    @Test
    public void testParse_shortOptionAttachedValue_valueCaptured() throws Throwable {
        options.addOption("f", true, "file");
        CommandLine cmd = parser.parse(options, new String[] {"-ftest.txt"});
        assertEquals("test.txt", cmd.getOptionValue("f"));
    }

    // covers: handleShortAndLongOption -> pos != -1 -> opt.length()==1 branch (-S=V)
    @Test
    public void testParse_shortOptionWithEqualsSign_valueCaptured() throws Throwable {
        options.addOption("f", true, "file");
        CommandLine cmd = parser.parse(options, new String[] {"-f=test.txt"});
        assertEquals("test.txt", cmd.getOptionValue("f"));
    }

    // covers: handleLongOption -> handleLongOptionWithEqual -> option.acceptsArg() true
    @Test
    public void testParse_longOptionWithEqualsSign_valueCaptured() throws Throwable {
        options.addOption("f", "file", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] {"--file=test.txt"});
        assertEquals("test.txt", cmd.getOptionValue("file"));
    }

    // covers: handleLongOption -> handleLongOptionWithoutEqual -> single match, then separate arg value
    @Test
    public void testParse_longOptionWithSeparateArg_valueCaptured() throws Throwable {
        options.addOption("f", "file", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] {"--file", "test.txt"});
        assertEquals("test.txt", cmd.getOptionValue("file"));
    }

    // covers: handleConcatenatedOptions loop with all known no-arg options (multiple iterations)
    @Test
    public void testParse_concatenatedShortOptions_allFlagsPresent() throws Throwable {
        options.addOption("a", false, "a");
        options.addOption("b", false, "b");
        options.addOption("c", false, "c");
        CommandLine cmd = parser.parse(options, new String[] {"-abc"});
        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
        assertTrue(cmd.hasOption("c"));
    }

    // covers: handleConcatenatedOptions -> currentOption!=null && token.length()!=(i+1) -> trailing value to last opt
    @Test
    public void testParse_concatenatedShortOptionsWithTrailingValue_valueAssignedToLastOption() throws Throwable {
        options.addOption("a", false, "a");
        options.addOption("b", false, "b");
        options.addOption("c", true, "c");
        CommandLine cmd = parser.parse(options, new String[] {"-abcVALUE"});
        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
        assertEquals("VALUE", cmd.getOptionValue("c"));
    }

    // covers: handleToken -> "--".equals(token) -> skipParsing true, remaining raw tokens as args
    @Test
    public void testParse_doubleDashStopsParsing_remainingTokensAsArgs() throws Throwable {
        options.addOption("a", false, "a");
        CommandLine cmd = parser.parse(options, new String[] {"--", "-a", "foo"});
        assertFalse(cmd.hasOption("a"));
        String[] args = cmd.getArgs();
        assertEquals(2, args.length);
        assertEquals("-a", args[0]);
        assertEquals("foo", args[1]);
        assertTrue(parser.skipParsing);
    }

    // covers: handleUnknownToken -> startsWith('-') && length>1 && !stopAtNonOption -> throws
    @Test
    public void testParse_unrecognizedOption_throwsUnrecognizedOptionException() throws Throwable {
        options.addOption("a", false, "a");
        try
        {
            parser.parse(options, new String[] {"-x"});
            fail("expected UnrecognizedOptionException");
        }
        catch (UnrecognizedOptionException expected)
        {
        }
    }

    // covers: handleUnknownToken -> stopAtNonOption true -> added as arg, no exception, skipParsing set
    @Test
    public void testParse_unrecognizedOptionWithStopAtNonOption_addedAsArgNoException() throws Throwable {
        options.addOption("a", false, "a");
        CommandLine cmd = parser.parse(options, new String[] {"-x", "-a"}, true);
        assertFalse(cmd.hasOption("a"));
        String[] args = cmd.getArgs();
        assertEquals(2, args.length);
        assertEquals("-x", args[0]);
        assertEquals("-a", args[1]);
        assertTrue(parser.skipParsing);
    }

    // covers: checkRequiredOptions -> expectedOpts not empty -> MissingOptionException
    @Test
    public void testParse_missingRequiredOption_throwsMissingOptionException() throws Throwable {
        Option req = new Option("r", false, "required flag");
        req.setRequired(true);
        options.addOption(req);
        try
        {
            parser.parse(options, new String[0]);
            fail("expected MissingOptionException");
        }
        catch (MissingOptionException expected)
        {
        }
    }

    // covers: updateRequiredOptions removes satisfied required option, checkRequiredOptions passes
    @Test
    public void testParse_requiredOptionProvided_noExceptionAndExpectedOptsCleared() throws Throwable {
        Option req = new Option("r", false, "required flag");
        req.setRequired(true);
        options.addOption(req);
        CommandLine cmd = parser.parse(options, new String[] {"-r"});
        assertTrue(cmd.hasOption("r"));
        assertTrue(parser.expectedOpts.isEmpty());
    }

    // covers: checkRequiredArgs -> currentOption.requiresArg() true at end of parsing -> MissingArgumentException
    @Test
    public void testParse_missingArgumentAtEnd_throwsMissingArgumentException() throws Throwable {
        options.addOption("f", true, "file");
        try
        {
            parser.parse(options, new String[] {"-f"});
            fail("expected MissingArgumentException");
        }
        catch (MissingArgumentException expected)
        {
        }
    }

    // covers: updateRequiredOptions -> group.setSelected throws AlreadySelectedException on conflict
    @Test
    public void testParse_optionGroupConflict_throwsAlreadySelectedException() throws Throwable {
        Option a = new Option("a", false, "a");
        Option b = new Option("b", false, "b");
        OptionGroup group = new OptionGroup();
        group.addOption(a);
        group.addOption(b);
        options.addOptionGroup(group);
        try
        {
            parser.parse(options, new String[] {"-a", "-b"});
            fail("expected AlreadySelectedException");
        }
        catch (AlreadySelectedException expected)
        {
        }
    }

    // covers: updateRequiredOptions -> group.setSelected(option) sets selected on single use
    @Test
    public void testParse_optionGroupSingleSelection_groupSelectedSet() throws Throwable {
        Option a = new Option("a", false, "a");
        Option b = new Option("b", false, "b");
        OptionGroup group = new OptionGroup();
        group.addOption(a);
        group.addOption(b);
        options.addOptionGroup(group);
        CommandLine cmd = parser.parse(options, new String[] {"-a"});
        assertTrue(cmd.hasOption("a"));
        assertEquals("a", group.getSelected());
    }

    // covers: handleProperties -> !opt.hasArg() && value "true" -> handleOption called
    @Test
    public void testParse_propertyDefaultForFlagOption_trueValue_optionAdded() throws Throwable {
        options.addOption("v", false, "verbose");
        Properties props = new Properties();
        props.setProperty("v", "true");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("v"));
    }

    // covers: handleProperties -> value not yes/true/1 -> continue, option not added
    @Test
    public void testParse_propertyDefaultForFlagOption_noValue_optionNotAdded() throws Throwable {
        options.addOption("v", false, "verbose");
        Properties props = new Properties();
        props.setProperty("v", "no");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertFalse(cmd.hasOption("v"));
    }

    // covers: handleProperties -> value "1" is accepted per code contract
    @Test
    public void testParse_propertyDefaultForFlagOption_oneValue_optionAdded() throws Throwable {
        options.addOption("v", false, "verbose");
        Properties props = new Properties();
        props.setProperty("v", "1");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertTrue(cmd.hasOption("v"));
    }

    // covers: handleProperties -> opt.hasArg() true, getValues empty -> addValueForProcessing(value)
    @Test
    public void testParse_propertyDefaultForArgOption_valueAssignedWhenNotSet() throws Throwable {
        options.addOption("f", true, "file");
        Properties props = new Properties();
        props.setProperty("f", "default.txt");
        CommandLine cmd = parser.parse(options, new String[0], props);
        assertEquals("default.txt", cmd.getOptionValue("f"));
    }

    // covers: handleProperties -> !cmd.hasOption(option) guard skips when already present from args
    @Test
    public void testParse_propertyIgnoredWhenOptionAlreadyPresentFromArgs() throws Throwable {
        options.addOption("f", true, "file");
        Properties props = new Properties();
        props.setProperty("f", "default.txt");
        CommandLine cmd = parser.parse(options, new String[] {"-f", "explicit.txt"}, props);
        assertEquals("explicit.txt", cmd.getOptionValue("f"));
    }

    // covers: handleShortAndLongOption -> isJavaProperty true -> -Dkey=value style
    @Test
    public void testParse_javaPropertyStyleOption_keyValueParsed() throws Throwable {
        Option d = new Option("D", true, "define");
        d.setArgs(2);
        options.addOption(d);
        CommandLine cmd = parser.parse(options, new String[] {"-Dkey=value"});
        String[] values = cmd.getOptionValues("D");
        assertEquals("key", values[0]);
        assertEquals("value", values[1]);
    }

    // covers: getLongPrefix finds a matching long option prefix -> -Xmx512m style
    @Test
    public void testParse_longPrefixStyleOption_valueParsed() throws Throwable {
        Option x = new Option("X", "Xmx", true, "max mem");
        options.addOption(x);
        CommandLine cmd = parser.parse(options, new String[] {"-Xmx512m"});
        assertEquals("512m", cmd.getOptionValue("Xmx"));
    }

    // covers: isNegativeNumber -> isArgument returns true for negative number following an arg option
    @Test
    public void testParse_negativeNumberAsOptionArgument_treatedAsValue() throws Throwable {
        options.addOption("n", true, "number");
        CommandLine cmd = parser.parse(options, new String[] {"-n", "-1"});
        assertEquals("-1", cmd.getOptionValue("n"));
    }

    // covers: handleToken -> Util.stripLeadingAndTrailingQuotes applied to value
    @Test
    public void testParse_quotedArgumentValue_quotesStripped() throws Throwable {
        options.addOption("b", true, "bar");
        CommandLine cmd = parser.parse(options, new String[] {"-b", "\"quoted value\""});
        assertEquals("quoted value", cmd.getOptionValue("b"));
    }

    // covers: handleConcatenatedOptions -> unknown at i==1, stopAtNonOption false -> throws with whole token
    @Test
    public void testHandleConcatenatedOptions_unknownAtFirstPosition_throwsUnrecognized() throws Throwable {
        options.addOption("q", false, "q");
        try
        {
            parser.parse(options, new String[] {"-xq"});
            fail("expected UnrecognizedOptionException");
        }
        catch (UnrecognizedOptionException expected)
        {
        }
    }

    // covers: handleConcatenatedOptions -> unknown at i==1, stopAtNonOption true -> whole token added as arg (i>1 false branch)
    @Test
    public void testHandleConcatenatedOptions_unknownAtFirstPosition_stopAtNonOption_wholeTokenAsArg() throws Throwable {
        options.addOption("q", false, "q");
        CommandLine cmd = parser.parse(options, new String[] {"-xq"}, true);
        assertFalse(cmd.hasOption("q"));
        String[] args = cmd.getArgs();
        assertEquals(1, args.length);
        assertEquals("-xq", args[0]);
    }

    // covers: handleConcatenatedOptions -> unknown at i==2, stopAtNonOption false -> throws with whole token
    @Test
    public void testHandleConcatenatedOptions_unknownAfterOneKnownOption_throwsUnrecognized() throws Throwable {
        options.addOption("a", false, "a");
        try
        {
            parser.parse(options, new String[] {"-ax"});
            fail("expected UnrecognizedOptionException");
        }
        catch (UnrecognizedOptionException expected)
        {
        }
    }

    // covers: handleConcatenatedOptions -> unknown at i==2 (i>1 boundary), stopAtNonOption true -> tail added
    @Test
    public void testHandleConcatenatedOptions_unknownAfterOneKnownOption_stopAtNonOption_tailAsArg() throws Throwable {
        options.addOption("a", false, "a");
        CommandLine cmd = parser.parse(options, new String[] {"-ax"}, true);
        assertTrue(cmd.hasOption("a"));
        String[] args = cmd.getArgs();
        assertEquals(1, args.length);
        assertEquals("x", args[0]);
    }

    // covers: handleConcatenatedOptions -> unknown at i==3 after two known options, stopAtNonOption true -> tail added
    @Test
    public void testHandleConcatenatedOptions_unknownAfterTwoKnownOptions_stopAtNonOption_tailAsArg() throws Throwable {
        options.addOption("a", false, "a");
        options.addOption("b", false, "b");
        CommandLine cmd = parser.parse(options, new String[] {"-abx"}, true);
        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
        String[] args = cmd.getArgs();
        assertEquals(1, args.length);
        assertEquals("x", args[0]);
    }

    // covers: parse -> arguments == null -> loop skipped entirely
    @Test
    public void testParse_nullArguments_returnsEmptyCommandLine() throws Throwable {
        CommandLine cmd = parser.parse(options, null);
        assertEquals(0, cmd.getArgs().length);
    }

    // covers: handleToken -> else branch when token equals "-" (not treated as short/long option)
    @Test
    public void testParse_singleDashToken_treatedAsPlainArgument() throws Throwable {
        CommandLine cmd = parser.parse(options, new String[] {"-"});
        String[] args = cmd.getArgs();
        assertEquals(1, args.length);
        assertEquals("-", args[0]);
    }

    // covers: handleLongOptionWithoutEqual -> matchingOpts.size() > 1 -> AmbiguousOptionException
    @Test
    public void testParse_ambiguousLongOptionPrefix_throwsAmbiguousOptionException() throws Throwable {
        options.addOption("f", "foo", false, "desc foo");
        options.addOption("b", "foobar", false, "desc foobar");
        try
        {
            parser.parse(options, new String[] {"--fo"});
            fail("expected AmbiguousOptionException");
        }
        catch (AmbiguousOptionException expected)
        {
        }
    }

    // covers: checkRequiredOptions -> required OptionGroup not satisfied -> MissingOptionException
    @Test
    public void testParse_groupRequired_missingThrowsMissingOptionException() throws Throwable {
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "a"));
        group.addOption(new Option("b", false, "b"));
        group.setRequired(true);
        options.addOptionGroup(group);
        try
        {
            parser.parse(options, new String[0]);
            fail("expected MissingOptionException");
        }
        catch (MissingOptionException expected)
        {
        }
    }

    // covers: updateRequiredOptions -> required group satisfied removes group from expectedOpts
    @Test
    public void testParse_groupRequired_satisfiedNoException() throws Throwable {
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "a"));
        group.addOption(new Option("b", false, "b"));
        group.setRequired(true);
        options.addOptionGroup(group);
        CommandLine cmd = parser.parse(options, new String[] {"-a"});
        assertTrue(cmd.hasOption("a"));
        assertTrue(parser.expectedOpts.isEmpty());
    }

    // covers: handleUnknownToken -> plain non-dash token added directly as argument
    @Test
    public void testParse_plainArgument_addedToArgList() throws Throwable {
        CommandLine cmd = parser.parse(options, new String[] {"plainArg"});
        String[] args = cmd.getArgs();
        assertEquals(1, args.length);
        assertEquals("plainArg", args[0]);
    }

    // covers: full 4-arg parse overload combining Properties and stopAtNonOption
    @Test
    public void testParse_fourArgOverload_combinesPropertiesAndStopAtNonOption() throws Throwable {
        options.addOption("a", false, "a");
        Properties props = new Properties();
        CommandLine cmd = parser.parse(options, new String[] {"-x"}, props, true);
        assertFalse(cmd.hasOption("a"));
        String[] args = cmd.getArgs();
        assertEquals(1, args.length);
        assertEquals("-x", args[0]);
    }
}
