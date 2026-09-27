package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class OptionBuilderTest {

    @Test
    public void testCreateWithChar() throws Throwable {
        Option option = OptionBuilder.withLongOpt("longoption")
                .withDescription("description")
                .hasArg()
                .withArgName("argName")
                .isRequired(true)
                .withValueSeparator(':')
                .withType(String.class)
                .create('a');

        assertNotNull(option);
        assertEquals("a", option.getOpt());
        assertEquals("longoption", option.getLongOpt());
        assertEquals("description", option.getDescription());
        assertEquals(1, option.getArgs());
        assertEquals("argName", option.getArgName());
        assertTrue(option.isRequired());
        assertEquals(':', option.getValueSeparator());
        assertEquals(String.class, option.getType());
    }

    @Test
    public void testCreateWithNoArgs() throws Throwable {
        Option option = OptionBuilder.withLongOpt("longonly")
                .create();

        assertNotNull(option);
        assertNull(option.getOpt());
        assertEquals("longonly", option.getLongOpt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNoLongOptThrowsException() throws Throwable {
        OptionBuilder.create();
    }

    @Test
    public void testCreateWithString() throws Throwable {
        Option option = OptionBuilder.withDescription("desc")
                .create("b");

        assertNotNull(option);
        assertEquals("b", option.getOpt());
        assertEquals("desc", option.getDescription());
    }

    @Test
    public void testHasArgBoolean() throws Throwable {
        Option optTrue = OptionBuilder.hasArg(true).withLongOpt("truearg").create();
        assertEquals(1, optTrue.getArgs());

        Option optFalse = OptionBuilder.hasArg(false).withLongOpt("falsearg").create();
        assertEquals(Option.UNINITIALIZED, optFalse.getArgs());
    }

    @Test
    public void testHasArgsVariants() throws Throwable {
        Option optUnlimited = OptionBuilder.hasArgs().withLongOpt("unlimited").create();
        assertEquals(Option.UNLIMITED_VALUES, optUnlimited.getArgs());

        Option optCount = OptionBuilder.hasArgs(5).withLongOpt("count").create();
        assertEquals(5, optCount.getArgs());
    }

    @Test
    public void testOptionalArgsVariants() throws Throwable {
        Option optOptional = OptionBuilder.hasOptionalArg().withLongOpt("optarg").create();
        assertEquals(1, optOptional.getArgs());
        assertTrue(optOptional.hasOptionalArg());

        Option optOptionalUnlimited = OptionBuilder.hasOptionalArgs().withLongOpt("optunlimited").create();
        assertEquals(Option.UNLIMITED_VALUES, optOptionalUnlimited.getArgs());
        assertTrue(optOptionalUnlimited.hasOptionalArg());

        Option optOptionalCount = OptionBuilder.hasOptionalArgs(3).withLongOpt("optcount").create();
        assertEquals(3, optOptionalCount.getArgs());
        assertTrue(optOptionalCount.hasOptionalArg());
    }

    @Test
    public void testValueSeparatorDefault() throws Throwable {
        Option option = OptionBuilder.withValueSeparator().withLongOpt("valsep").create();
        assertEquals('=', option.getValueSeparator());
    }

    @Test
    public void testIsRequiredDefault() throws Throwable {
        Option option = OptionBuilder.isRequired().withLongOpt("req").create();
        assertTrue(option.isRequired());
    }
}