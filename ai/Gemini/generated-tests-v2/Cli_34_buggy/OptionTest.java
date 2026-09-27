package org.apache.commons.cli;

import junit.framework.TestCase;
import java.util.List;

public class OptionTest extends TestCase {

    public void testConstructorsAndGetters() throws Throwable {
        Option opt1 = new Option("a", "desc");
        assertEquals("a", opt1.getOpt());
        assertNull(opt1.getLongOpt());
        assertEquals("desc", opt1.getDescription());
        assertFalse(opt1.hasArg());
        assertFalse(opt1.hasArgs());

        Option opt2 = new Option("b", true, "descB");
        assertEquals("b", opt2.getOpt());
        assertTrue(opt2.hasArg());
        assertEquals(1, opt2.getArgs());

        Option opt3 = new Option("c", "longC", true, "descC");
        assertEquals("c", opt3.getOpt());
        assertEquals("longC", opt3.getLongOpt());
        assertTrue(opt3.hasArg());
        assertEquals("descC", opt3.getDescription());
    }

    public void testIdAndKey() throws Throwable {
        Option opt1 = new Option("a", "desc");
        assertEquals('a', opt1.getId());
        assertEquals("a", opt1.getKey());

        Option opt2 = new Option(null, "longOpt", false, "desc");
        assertEquals("longOpt", opt2.getKey());
    }

    public void testType() throws Throwable {
        Option opt = new Option("a", "desc");
        assertNull(opt.getType());
        opt.setType(Integer.class);
        assertEquals(Integer.class, opt.getType());
    }

    public void testLongOpt() throws Throwable {
        Option opt = new Option("a", "desc");
        assertFalse(opt.hasLongOpt());
        opt.setLongOpt("long");
        assertTrue(opt.hasLongOpt());
        assertEquals("long", opt.getLongOpt());
    }

    public void testOptionalArg() throws Throwable {
        Option opt = new Option("a", "desc");
        assertFalse(opt.hasOptionalArg());
        opt.setOptionalArg(true);
        assertTrue(opt.hasOptionalArg());
    }

    public void testRequired() throws Throwable {
        Option opt = new Option("a", "desc");
        assertFalse(opt.isRequired());
        opt.setRequired(true);
        assertTrue(opt.isRequired());
    }

    public void testArgName() throws Throwable {
        Option opt = new Option("a", "desc");
        assertFalse(opt.hasArgName());
        assertNull(opt.getArgName());

        opt.setArgName("");
        assertFalse(opt.hasArgName());

        opt.setArgName("value");
        assertTrue(opt.hasArgName());
        assertEquals("value", opt.getArgName());
    }

    public void testArgsCount() throws Throwable {
        Option opt = new Option("a", "desc");
        assertEquals(Option.UNINITIALIZED, opt.getArgs());
        assertFalse(opt.hasArgs());

        opt.setArgs(Option.UNLIMITED_VALUES);
        assertTrue(opt.hasArgs());
        assertTrue(opt.hasArg());

        opt.setArgs(2);
        assertTrue(opt.hasArgs());
        assertTrue(opt.hasArg());

        opt.setArgs(1);
        assertFalse(opt.hasArgs());
        assertTrue(opt.hasArg());
    }

    public void testValueSeparator() throws Throwable {
        Option opt = new Option("a", "desc");
        assertFalse(opt.hasValueSeparator());
        assertEquals('\0', opt.getValueSeparator());

        opt.setValueSeparator('=');
        assertTrue(opt.hasValueSeparator());
        assertEquals('=', opt.getValueSeparator());
    }

    public void testAddValueForProcessingUninitialized() throws Throwable {
        Option opt = new Option("a", "desc");
        try {
            opt.addValueForProcessing("val");
            fail("Expected RuntimeException for uninitialized args");
        } catch (RuntimeException e) {
            assertEquals("NO_ARGS_ALLOWED", e.getMessage());
        }
    }

    public void testProcessValueWithSeparator() throws Throwable {
        Option opt = new Option("a", true, "desc");
        opt.setArgs(Option.UNLIMITED_VALUES);
        opt.setValueSeparator(',');

        opt.addValueForProcessing("v1,v2,v3");

        String[] values = opt.getValues();
        assertEquals(3, values.length);
        assertEquals("v1", values[0]);
        assertEquals("v2", values[1]);
        assertEquals("v3", values[2]);
    }

    public void testProcessValueWithSeparatorLimited() throws Throwable {
        Option opt = new Option("a", true, "desc");
        opt.setArgs(2);
        opt.setValueSeparator(',');

        opt.addValueForProcessing("v1,v2,v3");

        String[] values = opt.getValues();
        assertEquals(2, values.length);
        assertEquals("v1", values[0]);
        assertEquals("v2,v3", values[1]);
    }

    public void testAddValueListFull() throws Throwable {
        Option opt = new Option("a", true, "desc");
        opt.setArgs(1);
        opt.addValueForProcessing("v1");
        
        try {
            opt.addValueForProcessing("v2");
            fail("Expected RuntimeException when list is full");
        } catch (RuntimeException e) {
            assertEquals("Cannot add value, list full.", e.getMessage());
        }
    }

    public void testGetValueMethods() throws Throwable {
        Option opt = new Option("a", true, "desc");
        opt.setArgs(Option.UNLIMITED_VALUES);

        assertNull(opt.getValue());
        assertNull(opt.getValues());
        assertNotNull(opt.getValuesList());
        assertTrue(opt.getValuesList().isEmpty());

        opt.addValueForProcessing("val1");
        opt.addValueForProcessing("val2");

        assertEquals("val1", opt.getValue());
        assertEquals("val1", opt.getValue(0));
        assertEquals("val2", opt.getValue(1));
        assertEquals("val1", opt.getValue("default"));

        String[] values = opt.getValues();
        assertEquals(2, values.length);

        Option optNoVal = new Option("b", "desc");
        assertNull(optNoVal.getValue("default"));
        assertNull(optNoVal.getValues());

        try {
            optNoVal.getValue(0);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    public void testToString() throws Throwable {
        Option opt = new Option("a", "longA", true, "descA");
        opt.setType(String.class);
        String str = opt.toString();
        assertTrue(str.contains("a"));
        assertTrue(str.contains("longA"));
        assertTrue(str.contains("descA"));
        assertTrue(str.contains("class java.lang.String"));

        Option optNoArgs = new Option("b", "descB");
        assertTrue(optNoArgs.toString().contains("descB"));
    }

    public void testEqualsAndHashCode() throws Throwable {
        Option opt1 = new Option("a", "longA", true, "descA");
        Option opt2 = new Option("a", "longA", true, "descA");
        Option opt3 = new Option("b", "longA", true, "descA");
        Option opt4 = new Option("a", "longB", true, "descA");
        Option opt5 = new Option(null, "longA", true, "descA");

        assertEquals(opt1, opt1);
        assertEquals(opt1, opt2);
        assertEquals(opt1.hashCode(), opt2.hashCode());

        assertFalse(opt1.equals(null));
        assertFalse(opt1.equals("someString"));
        assertFalse(opt1.equals(opt3));
        assertFalse(opt1.equals(opt4));
        assertFalse(opt1.equals(opt5));

        Option optNullOpt1 = new Option(null, "longA", false, "desc");
        Option optNullOpt2 = new Option("a", "longA", false, "desc");
        assertFalse(optNullOpt1.equals(optNullOpt2));
        assertFalse(optNullOpt2.equals(optNullOpt1));
        
        Option optBothNullOpt = new Option(null, null, false, "desc");
        assertFalse(optBothNullOpt.equals(opt1));
        assertFalse(opt1.equals(optBothNullOpt));
        
        Option optBothNullOpt2 = new Option(null, null, false, "desc");
        assertEquals(optBothNullOpt, optBothNullOpt2);
        assertEquals(optBothNullOpt.hashCode(), optBothNullOpt2.hashCode());
    }

    public void testClone() throws Throwable {
        Option opt = new Option("a", "longA", true, "descA");
        opt.addValueForProcessing("v1");

        Option clone = (Option) opt.clone();
        assertEquals(opt.getOpt(), clone.getOpt());
        assertEquals(opt.getLongOpt(), clone.getLongOpt());
        assertEquals(1, clone.getValuesList().size());

        opt.clearValues();
        assertEquals(0, opt.getValuesList().size());
        assertEquals(1, clone.getValuesList().size());
    }

    public void testDeprecatedAddValue() throws Throwable {
        Option opt = new Option("a", "desc");
        try {
            opt.addValue("val");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("The addValue method is not intended"));
        }
    }

    public void testAcceptsAndRequiresArg() throws Throwable {
        Option opt = new Option("a", true, "desc");
        assertTrue(opt.acceptsArg());
        assertTrue(opt.requiresArg());

        opt.addValueForProcessing("v1");
        assertFalse(opt.acceptsArg());
        assertFalse(opt.requiresArg());

        Option optOptional = new Option("b", true, "desc");
        optOptional.setOptionalArg(true);
        assertTrue(optOptional.acceptsArg());
        assertFalse(optOptional.requiresArg());

        Option optUnlimited = new Option("c", true, "desc");
        optUnlimited.setArgs(Option.UNLIMITED_VALUES);
        assertTrue(optUnlimited.acceptsArg());
        assertTrue(optUnlimited.requiresArg());
        optUnlimited.addValueForProcessing("v1");
        assertTrue(optUnlimited.acceptsArg());
        assertFalse(optUnlimited.requiresArg());
    }
}