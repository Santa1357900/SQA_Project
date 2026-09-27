package org.apache.commons.cli2.commandline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.option.Command;
import org.apache.commons.cli2.option.DefaultOption;
import org.apache.commons.cli2.option.PropertyOption;

public class WriteableCommandLineImplTest extends TestCase {

    private WriteableCommandLineImpl cl;
    private Option rootOption;
    private List arguments;

    protected void setUp() throws Exception {
        super.setUp();
        arguments = new ArrayList();
        arguments.add("arg1");
        arguments.add("arg with space");

        Set prefixes = new HashSet();
        prefixes.add("-");
        prefixes.add("--");

        rootOption = new DefaultOption("-h", "--help", "help", false, null, null, null, 0, 0, null, prefixes, null, 0);
    }

    public void testConstructorAndToString() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        assertNotNull(cl);
        assertEquals("arg1 \"arg with space\"", cl.toString());
        assertEquals(arguments, cl.getNormalised());
    }

    public void testAddAndGetOption() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        Option opt = new Command("cmd", "Command", new HashSet(), new HashSet(), ' ', null, null, false);
        
        assertFalse(cl.hasOption(opt));
        assertNull(cl.getOption("cmd"));

        cl.addOption(opt);
        assertTrue(cl.hasOption(opt));
        assertEquals(opt, cl.getOption("cmd"));
        assertEquals(1, cl.getOptions().size());
        assertTrue(cl.getOptionTriggers().contains("cmd"));
    }

    public void testAddValue() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        Option opt = new DefaultOption("-f", "--file", "file", false, null, null, null, 0, 0, null, new HashSet(), null, 0);
        
        cl.addValue(opt, "value1");
        cl.addValue(opt, "value2");

        List values = cl.getValues(opt, Collections.EMPTY_LIST);
        assertEquals(2, values.size());
        assertEquals("value1", values.get(0));
        assertEquals("value2", values.get(1));

        List undefaulted = cl.getUndefaultedValues(opt);
        assertEquals(2, undefaulted.size());
    }

    public void testAddValueWithArgument() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        Argument arg = new Argument("arg", "argument", 0, 1, ' ', ' ', null, null, null, 0);

        cl.addValue(arg, "val");
        assertTrue(cl.hasOption(arg));
    }

    public void testAddSwitch() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        Option opt = new DefaultOption("-v", "--verbose", "verbose", false, null, null, null, 0, 0, null, new HashSet(), null, 0);

        cl.addSwitch(opt, true);
        assertEquals(Boolean.TRUE, cl.getSwitch(opt, Boolean.FALSE));
        assertTrue(cl.hasOption(opt));

        try {
            cl.addSwitch(opt, false);
            fail("Expected IllegalStateException for duplicate switch");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testGetValuesWithDefaults() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        Option opt = new DefaultOption("-o", "--opt", "opt", false, null, null, null, 0, 0, null, new HashSet(), null, 0);

        List methodDefaults = new ArrayList();
        methodDefaults.add("methodDefault1");

        List defaultValuesList = new ArrayList();
        defaultValuesList.add("optionDefault1");

        // 1. Test values are empty, method defaults are provided
        List result = cl.getValues(opt, methodDefaults);
        assertEquals(methodDefaults, result);

        // 2. Test values are empty, method defaults are empty, option defaults are provided
        cl.setDefaultValues(opt, defaultValuesList);
        result = cl.getValues(opt, Collections.EMPTY_LIST);
        assertEquals(defaultValuesList, result);

        // 3. Test values are empty, method defaults are null, option defaults are provided
        result = cl.getValues(opt, null);
        assertEquals(defaultValuesList, result);

        // 4. Test values are empty, both method and option defaults are null/empty
        cl.setDefaultValues(opt, null);
        result = cl.getValues(opt, Collections.EMPTY_LIST);
        assertEquals(Collections.EMPTY_LIST, result);
    }

    public void testGetSwitchWithDefaults() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        Option opt = new DefaultOption("-s", "--switch", "switch", false, null, null, null, 0, 0, null, new HashSet(), null, 0);

        // 1. Neither set, return default parameter
        assertEquals(Boolean.TRUE, cl.getSwitch(opt, Boolean.TRUE));
        assertNull(cl.getSwitch(opt, null));

        // 2. Set default switch on option
        cl.setDefaultSwitch(opt, Boolean.TRUE);
        assertEquals(Boolean.TRUE, cl.getSwitch(opt, null));

        // 3. Explicit switch added overrides default
        cl.addSwitch(opt, false);
        assertEquals(Boolean.FALSE, cl.getSwitch(opt, Boolean.TRUE));

        // 4. Test removing default switch
        cl.setDefaultSwitch(opt, null);
    }

    public void testPropertiesHandling() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);
        Option opt = new PropertyOption();

        assertNull(cl.getProperty(opt, "prop1", null));
        assertEquals("default", cl.getProperty(opt, "prop1", "default"));
        assertTrue(cl.getProperties(opt).isEmpty());

        cl.addProperty(opt, "prop1", "val1");
        cl.addProperty("prop2", "val2");

        assertEquals("val1", cl.getProperty(opt, "prop1", null));
        assertEquals("val1", cl.getProperty("prop1"));
        assertNotNull(cl.getProperties(opt));
        assertNotNull(cl.getProperties());
    }

    public void testLooksLikeOption() throws Throwable {
        cl = new WriteableCommandLineImpl(rootOption, arguments);

        assertTrue(cl.looksLikeOption("-h"));
        assertTrue(cl.looksLikeOption("--help"));
        assertFalse(cl.looksLikeOption("help"));
        assertFalse(cl.looksLikeOption(""));
    }
}