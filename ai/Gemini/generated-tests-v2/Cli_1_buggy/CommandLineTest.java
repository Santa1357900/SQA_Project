package org.apache.commons.cli;

import junit.framework.TestCase;
import java.util.List;
import java.util.Iterator;

public class CommandLineTest extends TestCase {

    public void testCommandLineCreation() throws Throwable {
        CommandLine cmd = new CommandLine();
        assertNotNull(cmd);
        assertNotNull(cmd.getArgs());
        assertEquals(0, cmd.getArgs().length);
        assertNotNull(cmd.getArgList());
        assertTrue(cmd.getArgList().isEmpty());
    }

    public void testAddArg() throws Throwable {
        CommandLine cmd = new CommandLine();
        cmd.addArg("arg1");
        cmd.addArg("arg2");

        String[] args = cmd.getArgs();
        assertNotNull(args);
        assertEquals(2, args.length);
        assertEquals("arg1", args[0]);
        assertEquals("arg2", args[1]);

        List argList = cmd.getArgList();
        assertNotNull(argList);
        assertEquals(2, argList.size());
        assertEquals("arg1", argList.get(0));
        assertEquals("arg2", argList.get(1));
    }

    public void testHasOption() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt = new Option("a", "alpha", false, "alpha option");
        cmd.addOption(opt);

        assertTrue(cmd.hasOption("a"));
        assertTrue(cmd.hasOption('a'));
        assertTrue(cmd.hasOption("alpha"));
        assertFalse(cmd.hasOption("b"));
        assertFalse(cmd.hasOption('b'));
    }

    public void testGetOptionValueString() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt = new Option("b", "beta", true, "beta option");
        opt.setValue("valueB");
        cmd.addOption(opt);

        assertEquals("valueB", cmd.getOptionValue("b"));
        assertEquals("valueB", cmd.getOptionValue('b'));
        assertEquals("valueB", cmd.getOptionValue("beta"));
        assertNull(cmd.getOptionValue("nonexistent"));
        assertNull(cmd.getOptionValue('z'));
    }

    public void testGetOptionValueWithDefault() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt = new Option("c", "char", true, "char option");
        opt.setValue("valC");
        cmd.addOption(opt);

        assertEquals("valC", cmd.getOptionValue("c", "defaultVal"));
        assertEquals("valC", cmd.getOptionValue('c', "defaultVal"));
        assertEquals("defaultVal", cmd.getOptionValue("missing", "defaultVal"));
        assertEquals("defaultVal", cmd.getOptionValue('m', "defaultVal"));
    }

    public void testGetOptionValues() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt = new Option("d", "delta", true, "delta option");
        opt.setValue("val1");
        opt.setValue("val2");
        cmd.addOption(opt);

        String[] values = cmd.getOptionValues("d");
        assertNotNull(values);
        assertEquals(2, values.length);
        assertEquals("val1", values[0]);
        assertEquals("val2", values[1]);

        String[] valuesChar = cmd.getOptionValues('d');
        assertNotNull(valuesChar);
        assertEquals(2, valuesChar.length);

        String[] valuesLong = cmd.getOptionValues("delta");
        assertNotNull(valuesLong);
        assertEquals(2, valuesLong.length);

        assertNull(cmd.getOptionValues("missing"));
        assertNull(cmd.getOptionValues('x'));
    }

    public void testGetOptionObject() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt = new Option("e", "echo", true, "echo option");
        opt.setType(java.util.Date.class);
        opt.setValue("2023-01-01");
        cmd.addOption(opt);

        assertNotNull(cmd.getOptionObject("e"));
        assertNotNull(cmd.getOptionObject('e'));
        assertNotNull(cmd.getOptionObject("echo"));

        assertNull(cmd.getOptionObject("missing"));
        assertNull(cmd.getOptionObject('z'));
    }

    public void testGetOptionObjectWithoutValue() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt = new Option("f", "foxtrot", true, "foxtrot option");
        opt.setType(java.util.Date.class);
        cmd.addOption(opt);

        assertNull(cmd.getOptionObject("f"));
    }

    public void testIteratorAndGetOptions() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt1 = new Option("g", "golf", false, "golf");
        Option opt2 = new Option("h", "hotel", false, "hotel");
        cmd.addOption(opt1);
        cmd.addOption(opt2);

        Option[] options = cmd.getOptions();
        assertNotNull(options);
        assertEquals(2, options.length);

        Iterator it = cmd.iterator();
        assertNotNull(it);
        int count = 0;
        while (it.hasNext()) {
            assertNotNull(it.next());
            count++;
        }
        assertEquals(2, count);
    }

    public void testAddOptionWithoutKey() throws Throwable {
        CommandLine cmd = new CommandLine();
        Option opt = new Option(null, "longonly", false, "long only");
        cmd.addOption(opt);

        assertTrue(cmd.hasOption("longonly"));
    }
}