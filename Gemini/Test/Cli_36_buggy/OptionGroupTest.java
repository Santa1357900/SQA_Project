package org.apache.commons.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;

import org.junit.Test;

public class OptionGroupTest {

    @Test
    public void testAddOptionAndGetOptions() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha");
        Option opt2 = new Option("b", "beta");

        group.addOption(opt1);
        group.addOption(opt2);

        Collection<Option> options = group.getOptions();
        assertNotNull(options);
        assertEquals(2, options.size());
        assertTrue(options.contains(opt1));
        assertTrue(options.contains(opt2));
    }

    @Test
    public void testGetNames() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha");
        Option opt2 = new Option("b", "beta");

        group.addOption(opt1);
        group.addOption(opt2);

        Collection<String> names = group.getNames();
        assertNotNull(names);
        assertEquals(2, names.size());
        assertTrue(names.contains("a"));
        assertTrue(names.contains("b"));
    }

    @Test
    public void testSetSelectedAndGetSelected() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha");
        Option opt2 = new Option("b", "beta");

        group.addOption(opt1);
        group.addOption(opt2);

        assertNull(group.getSelected());

        // Select first option
        group.setSelected(opt1);
        assertEquals("a", group.getSelected());

        // Reselect same option should not throw exception
        group.setSelected(opt1);
        assertEquals("a", group.getSelected());

        // Select null should reset selected option
        group.setSelected(null);
        assertNull(group.getSelected());
    }

    @Test
    public void testAlreadySelectedException() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha");
        Option opt2 = new Option("b", "beta");

        group.addOption(opt1);
        group.addOption(opt2);

        group.setSelected(opt1);

        try {
            group.setSelected(opt2);
            fail("Expected AlreadySelectedException to be thrown");
        } catch (AlreadySelectedException e) {
            assertEquals(group, e.getOptionGroup());
            assertEquals(opt2, e.getOption());
        }
    }

    @Test
    public void testRequired() throws Throwable {
        OptionGroup group = new OptionGroup();
        assertFalse(group.isRequired());

        group.setRequired(true);
        assertTrue(group.isRequired());

        group.setRequired(false);
        assertFalse(group.isRequired());
    }

    @Test
    public void testToStringWithOptAndDescription() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "first option");
        Option opt2 = new Option("b", "second option");

        group.addOption(opt1);
        group.addOption(opt2);

        String toStringResult = group.toString();
        assertNotNull(toStringResult);
        assertTrue(toStringResult.startsWith("["));
        assertTrue(toStringResult.endsWith("]"));
        assertTrue(toStringResult.contains("-a first option"));
        assertTrue(toStringResult.contains("-b second option"));
    }

    @Test
    public void testToStringWithLongOptOnly() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option(null, "long-a", false, "desc a");
        Option opt2 = new Option(null, "long-b", false, null);

        group.addOption(opt1);
        group.addOption(opt2);

        String toStringResult = group.toString();
        assertNotNull(toStringResult);
        assertTrue(toStringResult.contains("--long-a desc a"));
        assertTrue(toStringResult.contains("--long-b"));
    }
}