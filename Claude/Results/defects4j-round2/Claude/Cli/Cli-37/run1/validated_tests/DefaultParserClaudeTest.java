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

    // branch: simple short option, no argument
    @Test
    public void testParse_simpleShortOption_noArg() throws Throwable {
        options.addOption("a", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-a" });
        assertTrue(cmd.hasOption("a"));
    }

    // branch: short option with separate argument token
    @Test
    public void testParse_shortOptionWithArgSeparate() throws Throwable {
        options.addOption("b", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-b", "value" });
        assertEquals("value", cmd.getOptionValue("b"));
    }

    // branch: short option with attached argument (-bVALUE)
    @Test
    public void testParse_shortOptionWithArgAttached() throws Throwable {
        options.addOption("b", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-bvalue" });
        assertEquals("value", cmd.getOptionValue("b"));
    }

    // branch: short option with equals sign (-b=value)
    @Test
    public void testParse_shortOptionWithEqualsSign() throws Throwable {
        options.addOption("b", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-b=value" });
        assertEquals("value", cmd.getOptionValue("b"));
    }

    // branch: long option with equals sign
    @Test
    public void testParse_longOptionWithEqualsSign() throws Throwable {
        options.addOption("b", "bee", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "--bee=value" });
        assertEquals("value", cmd.getOptionValue("bee"));
    }

    // branch: long option with separate argument token
    @Test
    public void testParse_longOptionWithSeparateArg() throws Throwable {
        options.addOption("b", "bee", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "--bee", "value" });
        assertEquals("value", cmd.getOptionValue("bee"));
    }

    // branch: long option flag with no argument
    @Test
    public void testParse_longOptionNoArgFlag() throws Throwable {
        options.addOption("h", "help", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "--help" });
        assertTrue(cmd.hasOption("help"));
    }

    // branch: "--" stops parsing, remaining tokens become raw args
    @Test
    public void testParse_doubleDashStopsParsing() throws Throwable {
        options.addOption("a", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "--", "-a" });
        assertFalse(cmd.hasOption("a"));
        assertArrayEquals(new String[] { "-a" }, cmd.getArgs());
    }

    // branch: lone "-" treated as non-option argument
    @Test
    public void testParse_singleDashTreatedAsArgument() throws Throwable {
        CommandLine cmd = parser.parse(options, new String[] { "-" });
        assertArrayEquals(new String[] { "-" }, cmd.getArgs());
    }

    // branch: unrecognized short option throws
    @Test
    public void testParse_unrecognizedShortOption_throws() throws Throwable {
        try
        {
            parser.parse(options, new String[] { "-x" });
            fail("expected UnrecognizedOptionException");
        }
        catch (UnrecognizedOptionException expected)
        {
            assertTrue(expected.getMessage().contains("Unrecognized option"));
        }
    }

    // branch: unrecognized long option throws
    @Test
    public void testParse_unrecognizedLongOption_throws() throws Throwable {
        try
        {
            parser.parse(options, new String[] { "--unknown" });
            fail("expected UnrecognizedOptionException");
        }
        catch (UnrecognizedOptionException expected)
        {
            assertTrue(expected.getMessage().contains("Unrecognized option"));
        }
    }

    // branch: plain token not starting with '-' becomes an arg
    @Test
    public void testParse_nonOptionArgument_addedToArgs() throws Throwable {
        options.addOption("a", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "foo" });
        assertArrayEquals(new String[] { "foo" }, cmd.getArgs());
    }

    // branch: stopAtNonOption=true with unknown token stops parsing, remaining raw
    @Test
    public void testParse_stopAtNonOptionTrue_unknownTokenStopsParsing() throws Throwable {
        options.addOption("a", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-x", "-a" }, true);
        assertArrayEquals(new String[] { "-x", "-a" }, cmd.getArgs());
        assertFalse(cmd.hasOption("a"));
    }

    // branch: missing required argument throws MissingArgumentException
    @Test
    public void testParse_missingRequiredArgument_throwsMissingArgumentException() throws Throwable {
        options.addOption("b", true, "desc");
        try
        {
            parser.parse(options, new String[] { "-b" });
            fail("expected MissingArgumentException");
        }
        catch (MissingArgumentException expected)
        {
        }
    }

    // branch: required option missing throws MissingOptionException
    @Test
    public void testParse_requiredOptionMissing_throwsMissingOptionException() throws Throwable {
        Option req = new Option("r", false, "desc");
        req.setRequired(true);
        options.addOption(req);
        try
        {
            parser.parse(options, new String[] {});
            fail("expected MissingOptionException");
        }
        catch (MissingOptionException expected)
        {
        }
    }

    // branch: required option provided, no exception
    @Test
    public void testParse_requiredOptionProvided_noException() throws Throwable {
        Option req = new Option("r", false, "desc");
        req.setRequired(true);
        options.addOption(req);
        CommandLine cmd = parser.parse(options, new String[] { "-r" });
        assertTrue(cmd.hasOption("r"));
    }

    // branch: ambiguous long option prefix throws AmbiguousOptionException
    @Test
    public void testParse_ambiguousLongOption_throwsAmbiguousOptionException() throws Throwable {
        options.addOption("a", "alpha", false, "desc");
        options.addOption("b", "alphabet", false, "desc");
        try
        {
            parser.parse(options, new String[] { "--al" });
            fail("expected AmbiguousOptionException");
        }
        catch (AmbiguousOptionException expected)
        {
        }
    }

    // branch: negative number consumed as value even though it matches a registered short option
    @Test
    public void testParse_negativeNumberAsOptionValue() throws Throwable {
        Option one = new Option("1", false, "desc");
        options.addOption(one);
        options.addOption("n", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-n", "-1" });
        assertEquals("-1", cmd.getOptionValue("n"));
        assertFalse(cmd.hasOption("1"));
    }

    // branch: properties supply default value for option with argument
    @Test
    public void testParse_propertiesDefaultValueApplied() throws Throwable {
        options.addOption("d", true, "desc");
        Properties props = new Properties();
        props.setProperty("d", "val");
        CommandLine cmd = parser.parse(options, new String[] {}, props);
        assertEquals("val", cmd.getOptionValue("d"));
    }

    // branch: properties boolean "true" adds flag option
    @Test
    public void testParse_propertiesBooleanTrueAddsOption() throws Throwable {
        options.addOption("v", false, "desc");
        Properties props = new Properties();
        props.setProperty("v", "true");
        CommandLine cmd = parser.parse(options, new String[] {}, props);
        assertTrue(cmd.hasOption("v"));
    }

    // branch: properties boolean "false" does not add flag option
    @Test
    public void testParse_propertiesBooleanFalseNotAdded() throws Throwable {
        options.addOption("v", false, "desc");
        Properties props = new Properties();
        props.setProperty("v", "false");
        CommandLine cmd = parser.parse(options, new String[] {}, props);
        assertFalse(cmd.hasOption("v"));
    }

    // branch: unrecognized property key throws UnrecognizedOptionException
    @Test
    public void testParse_propertiesUnrecognizedOption_throws() throws Throwable {
        Properties props = new Properties();
        props.setProperty("zzz", "val");
        try
        {
            parser.parse(options, new String[] {}, props);
            fail("expected UnrecognizedOptionException");
        }
        catch (UnrecognizedOptionException expected)
        {
        }
    }

    // branch: property value skipped because option already present on command line
    @Test
    public void testParse_propertiesSkippedWhenAlreadyOnCommandLine() throws Throwable {
        options.addOption("f", true, "desc");
        Properties props = new Properties();
        props.setProperty("f", "propVal");
        CommandLine cmd = parser.parse(options, new String[] { "-f", "cliVal" }, props);
        assertEquals("cliVal", cmd.getOptionValue("f"));
    }

    // branch: option group, single option selected, no exception
    @Test
    public void testParse_optionGroupSelection_noException() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option a = new Option("a", false, "desc");
        Option b = new Option("b", false, "desc");
        group.addOption(a);
        group.addOption(b);
        options.addOptionGroup(group);
        CommandLine cmd = parser.parse(options, new String[] { "-a" });
        assertTrue(cmd.hasOption("a"));
    }

    // branch: option group, two mutually exclusive options given throws AlreadySelectedException
    @Test
    public void testParse_optionGroupAlreadySelected_throwsAlreadySelectedException() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option a = new Option("a", false, "desc");
        Option b = new Option("b", false, "desc");
        group.addOption(a);
        group.addOption(b);
        options.addOptionGroup(group);
        try
        {
            parser.parse(options, new String[] { "-a", "-b" });
            fail("expected AlreadySelectedException");
        }
        catch (AlreadySelectedException expected)
        {
        }
    }

    // branch: required option group satisfied by one member, no exception
    @Test
    public void testParse_requiredOptionGroup_satisfied_noException() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option a = new Option("a", false, "desc");
        Option b = new Option("b", false, "desc");
        group.addOption(a);
        group.addOption(b);
        group.setRequired(true);
        options.addOptionGroup(group);
        CommandLine cmd = parser.parse(options, new String[] { "-a" });
        assertTrue(cmd.hasOption("a"));
    }

    // branch: required option group missing throws MissingOptionException
    @Test
    public void testParse_requiredOptionGroup_missing_throwsMissingOptionException() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option a = new Option("a", false, "desc");
        Option b = new Option("b", false, "desc");
        group.addOption(a);
        group.addOption(b);
        group.setRequired(true);
        options.addOptionGroup(group);
        try
        {
            parser.parse(options, new String[] {});
            fail("expected MissingOptionException");
        }
        catch (MissingOptionException expected)
        {
        }
    }

    // branch: concatenated short flags "-abc" all no-arg
    @Test
    public void testParse_concatenatedShortOptions_allFlags() throws Throwable {
        options.addOption("a", false, "desc");
        options.addOption("b", false, "desc");
        options.addOption("c", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-abc" });
        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
        assertTrue(cmd.hasOption("c"));
    }

    // branch: concatenated short options with trailing value attached to arg-accepting option
    @Test
    public void testParse_concatenatedShortOptionsWithTrailingValue() throws Throwable {
        options.addOption("a", false, "desc");
        options.addOption("b", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-abVALUE" });
        assertTrue(cmd.hasOption("a"));
        assertEquals("VALUE", cmd.getOptionValue("b"));
    }

    // branch: unique long option prefix match
    @Test
    public void testParse_longOptionPrefixMatchUnique() throws Throwable {
        options.addOption("h", "help", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "--he" });
        assertTrue(cmd.hasOption("help"));
    }

    // branch: getLongPrefix matches long option embedded before value (-Xmx512m)
    @Test
    public void testParse_longPrefixOptionLikeJavaMx() throws Throwable {
        options.addOption("X", "Xmx", true, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-Xmx512m" });
        assertEquals("512m", cmd.getOptionValue("Xmx"));
    }

    // branch: java-property style option (-Dkey=value) via isJavaProperty
    @Test
    public void testParse_javaPropertyStyleOption() throws Throwable {
        Option d = new Option("D", true, "desc");
        d.setArgs(2);
        options.addOption(d);
        CommandLine cmd = parser.parse(options, new String[] { "-Dkey=value" });
        String[] vals = cmd.getOptionValues("D");
        assertArrayEquals(new String[] { "key", "value" }, vals);
    }

    // branch: handleConcatenatedOptions with unknown trailing char throws
    @Test
    public void testHandleConcatenatedOptions_unknownCharacter_throwsUnrecognizedOptionException() throws Throwable {
        options.addOption("a", false, "desc");
        try
        {
            parser.parse(options, new String[] { "-ax" });
            fail("expected UnrecognizedOptionException");
        }
        catch (UnrecognizedOptionException expected)
        {
        }
    }

    // branch: two-arg parse overload delegates correctly
    @Test
    public void testParse_twoArgOverload_basic() throws Throwable {
        options.addOption("a", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-a" });
        assertTrue(cmd.hasOption("a"));
    }

    // branch: three-arg overload with explicit stopAtNonOption flag
    @Test
    public void testParse_threeArgOverloadStopAtNonOption() throws Throwable {
        options.addOption("a", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "extra", "-a" }, true);
        assertArrayEquals(new String[] { "extra", "-a" }, cmd.getArgs());
        assertFalse(cmd.hasOption("a"));
    }

    // branch: multiple options and values in sequence correctly separated
    @Test
    public void testParse_multipleOptionsSequence_valuesAssignedCorrectly() throws Throwable {
        options.addOption("n", true, "desc");
        options.addOption("a", false, "desc");
        CommandLine cmd = parser.parse(options, new String[] { "-n", "val", "-a" });
        assertEquals("val", cmd.getOptionValue("n"));
        assertTrue(cmd.hasOption("a"));
    }
}
