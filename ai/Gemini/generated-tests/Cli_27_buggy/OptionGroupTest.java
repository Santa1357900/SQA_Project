package org.apache.commons.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Iterator;

import org.junit.Test;

public class OptionGroupTest {

    @Test
    public void testAddAndGetOptions() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha");
        Option opt2 = new Option("b", "beta");

        group.addOption(opt1);
        group.addOption(opt2);

        Collection options = group.getOptions();
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

        Collection names = group.getNames();
        assertNotNull(names);
        assertEquals(2, names.size());
        assertTrue(names.contains("a"));
        assertTrue(names.contains("b"));
    }

    @Test
    public void testRequiredProperty() throws Throwable {
        OptionGroup group = new OptionGroup();
        assertFalse(group.isRequired());

        group.setRequired(true);
        assertTrue(group.isRequired());

        group.setRequired(false);
        assertFalse(group.isRequired());
    }

    @Test
    public void testSetSelectedNormal() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha");
        Option opt2 = new Option("b", "beta");

        group.addOption(opt1);
        group.addOption(opt2);

        assertNull(group.getSelected());

        group.setSelected(opt1);
        assertEquals("a", group.getSelected());

        // Reselecting the same option should not throw an exception
        group.setSelected(opt1);
        assertEquals("a", group.getSelected());
    }

    @Test
    public void testSetSelectedNull() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha");

        group.addOption(opt1);
        group.setSelected(opt1);
        assertEquals("a", group.getSelected());

        // Reset selected with null
        group.setSelected(null);
        assertNull(group.getSelected());
    }

    @Test
    public void testSetSelectedAlreadySelectedException() throws Throwable {
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
            assertNotNull(e);
            assertEquals(group, e.getOptionGroup());
            assertEquals(opt2, e.getOption());
        }
    }

    @Test
    public void testToStringWithOptAndLongOpt() throws Throwable {
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("a", "alpha description");
        Option opt2 = null;
        try {
            opt2 = new Option(null, "beta", false, "beta description");
        } catch (IllegalArgumentException e) {
            opt2 = new Option("b", "beta", false, "beta description");
            opt2.setOpt(null);
        }

        group.addOption(opt1);
        group.addOption(opt2);

        String str = group.toString();
        assertNotNull(str);
        assertTrue(str.startsWith("["));
        assertTrue(str.endsWith("]"));
        assertTrue(str.contains("-a alpha description"));
        assertTrue(str.contains("--beta beta description"));
    }
}