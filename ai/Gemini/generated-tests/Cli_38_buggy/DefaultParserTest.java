package org.apache.commons.cli;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultParserTest
{
    private DefaultParser parser;
    private Options options;

    @Before
    public void setUp() throws Throwable
    {
        parser = new DefaultParser();
        options = new Options();
    }

    @Test
    public void testParseSimpleLongOption() throws Throwable
    {
        options.addOption(new Option("a", "apple", false, "an apple"));
        CommandLine cmd = parser.parse(options, new String[]{"--apple"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("apple"));
        assertTrue(cmd.hasOption("a"));
    }

    @Test
    public void testParseLongOptionWithEqual() throws Throwable
    {
        options.addOption(new Option("b", "banana", true, "a banana"));
        CommandLine cmd = parser.parse(options, new String[]{"--banana=yellow"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("banana"));
        assertEquals("yellow", cmd.getOptionValue("banana"));
    }

    @Test
    public void testParseLongOptionWithoutEqualWithArgument() throws Throwable
    {
        options.addOption(new Option("b", "banana", true, "a banana"));
        CommandLine cmd = parser.parse(options, new String[]{"--banana", "sweet"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("banana"));
        assertEquals("sweet", cmd.getOptionValue("banana"));
    }

    @Test
    public void testParseShortOption() throws Throwable
    {
        options.addOption(new Option("x", "x-opt", false, "x option"));
        CommandLine cmd = parser.parse(options, new String[]{"-x"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("x"));
    }

    @Test
    public void testParseShortOptionWithArgumentAttached() throws Throwable
    {
        options.addOption(new Option("p", "port", true, "port option"));
        CommandLine cmd = parser.parse(options, new String[]{"-p8080"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("p"));
        assertEquals("8080", cmd.getOptionValue("p"));
    }

    @Test
    public void testParseShortOptionWithArgumentSeparated() throws Throwable
    {
        options.addOption(new Option("p", "port", true, "port option"));
        CommandLine cmd = parser.parse(options, new String[]{"-p", "9090"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("p"));
        assertEquals("9090", cmd.getOptionValue("p"));
    }

    @Test
    public void testParseShortOptionWithEqual() throws Throwable
    {
        options.addOption(new Option("p", "port", true, "port option"));
        CommandLine cmd = parser.parse(options, new String[]{"-p=80"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("p"));
        assertEquals("80", cmd.getOptionValue("p"));
    }

    @Test
    public void testParseConcatenatedShortOptions() throws Throwable
    {
        options.addOption(new Option("a", false, "alpha"));
        options.addOption(new Option("b", false, "beta"));
        CommandLine cmd = parser.parse(options, new String[]{"-ab"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
    }

    @Test
    public void testParseConcatenatedShortOptionsWithArg() throws Throwable
    {
        options.addOption(new Option("a", false, "alpha"));
        options.addOption(new Option("b", true, "beta with arg"));
        CommandLine cmd = parser.parse(options, new String[]{"-abval"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption("b"));
        assertEquals("val", cmd.getOptionValue("b"));
    }

    @Test
    public void testStopAtNonOption() throws Throwable
    {
        options.addOption(new Option("a", false, "alpha"));
        CommandLine cmd = parser.parse(options, new String[]{"-a", "non-option", "-b"}, true);
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.getArgList().contains("non-option"));
        assertTrue(cmd.getArgList().contains("-b"));
    }

    @Test
    public void testDoubleDashSkipsParsing() throws Throwable
    {
        options.addOption(new Option("a", false, "alpha"));
        CommandLine cmd = parser.parse(options, new String[]{"--", "-a"});
        assertNotNull(cmd);
        assertFalse(cmd.hasOption("a"));
        assertTrue(cmd.getArgList().contains("-a"));
    }

    @Test(expected = UnrecognizedOptionException.class)
    public void testUnrecognizedOptionThrowsException() throws Throwable
    {
        parser.parse(options, new String[]{"-u"});
    }

    @Test(expected = UnrecognizedOptionException.class)
    public void testUnrecognizedLongOptionThrowsException() throws Throwable
    {
        parser.parse(options, new String[]{"--unknown"});
    }

    @Test(expected = MissingArgumentException.class)
    public void testMissingArgumentThrowsException() throws Throwable
    {
        options.addOption(new Option("f", "file", true, "file option"));
        parser.parse(options, new String[]{"-f"});
    }

    @Test(expected = MissingOptionException.class)
    public void testMissingRequiredOptionThrowsException() throws Throwable
    {
        Option req = new Option("r", "req", false, "required option");
        req.setRequired(true);
        options.addOption(req);
        parser.parse(options, new String[]{});
    }

    @Test
    public void testOptionGroup() throws Throwable
    {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("1", "one", false, "one");
        Option opt2 = new Option("2", "two", false, "two");
        group.addOption(opt1);
        group.addOption(opt2);
        options.addOptionGroup(group);

        CommandLine cmd = parser.parse(options, new String[]{"--one"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("one"));
        assertFalse(cmd.hasOption("two"));
    }

    @Test(expected = AlreadySelectedException.class)
    public void testOptionGroupAlreadySelectedThrowsException() throws Throwable
    {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("1", "one", false, "one");
        Option opt2 = new Option("2", "two", false, "two");
        group.addOption(opt1);
        group.addOption(opt2);
        options.addOptionGroup(group);

        parser.parse(options, new String[]{"--one", "--two"});
    }

    @Test
    public void testHandlePropertiesWithBooleanValues() throws Throwable
    {
        options.addOption(new Option("s", "stat", false, "stat option"));
        Properties props = new Properties();
        props.setProperty("stat", "true");

        CommandLine cmd = parser.parse(options, new String[]{}, props, false);
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("stat"));
    }

    @Test
    public void testHandlePropertiesWithArgValues() throws Throwable
    {
        options.addOption(new Option("d", "dir", true, "dir option"));
        Properties props = new Properties();
        props.setProperty("dir", "/tmp");

        CommandLine cmd = parser.parse(options, new String[]{}, props, false);
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("dir"));
        assertEquals("/tmp", cmd.getOptionValue("dir"));
    }

    @Test(expected = UnrecognizedOptionException.class)
    public void testHandlePropertiesUnrecognizedOptionThrowsException() throws Throwable
    {
        Properties props = new Properties();
        props.setProperty("unknown", "true");
        parser.parse(options, new String[]{}, props, false);
    }

    @Test(expected = AmbiguousOptionException.class)
    public void testAmbiguousOptionThrowsException() throws Throwable
    {
        options.addOption(new Option("opt1", false, "option 1"));
        options.addOption(new Option("option2", false, "option 2"));
        parser.parse(options, new String[]{"--op"});
    }

    @Test
    public void testNegativeNumberAsArgument() throws Throwable
    {
        options.addOption(new Option("n", "number", true, "number option"));
        CommandLine cmd = parser.parse(options, new String[]{"--number", "-123.45"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("number"));
        assertEquals("-123.45", cmd.getOptionValue("number"));
    }

    @Test
    public void testJavaPropertyArgumentSyntax() throws Throwable
    {
        Option opt = new Option("D", "define", true, "define property");
        opt.setArgs(2);
        options.addOption(opt);

        CommandLine cmd = parser.parse(options, new String[]{"-Dkey=value"});
        assertNotNull(cmd);
        assertTrue(cmd.hasOption("D"));
        String[] values = cmd.getOptionValues("D");
        assertNotNull(values);
        assertTrue(values.length >= 1);
    }

    @Test
    public void testNullArgumentsArray() throws Throwable
    {
        CommandLine cmd = parser.parse(options, (String[]) null);
        assertNotNull(cmd);
    }

    @Test
    public void testNullProperties() throws Throwable
    {
        CommandLine cmd = parser.parse(options, new String[]{}, (Properties) null);
        assertNotNull(cmd);
    }
}