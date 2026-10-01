package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class OptionClaudeTest
{
    // Constructor (opt, description): no long opt, no argument capability
    @Test
    public void testConstructorTwoArg() throws Throwable {
        Option opt = new Option("a", "alpha description");
        assertEquals("a", opt.getOpt());
        assertEquals("alpha description", opt.getDescription());
        assertNull(opt.getLongOpt());
        assertFalse(opt.hasArg());
        assertEquals(Option.UNINITIALIZED, opt.getArgs());
    }

    // Constructor (opt, hasArg=true, description): numberOfArgs set to 1
    @Test
    public void testConstructorThreeArgHasArgTrue() throws Throwable {
        Option opt = new Option("b", true, "desc");
        assertTrue(opt.hasArg());
        assertEquals(1, opt.getArgs());
    }

    // Constructor (opt, hasArg=false, description): no argument
    @Test
    public void testConstructorThreeArgHasArgFalse() throws Throwable {
        Option opt = new Option("c", false, "desc");
        assertFalse(opt.hasArg());
        assertEquals(Option.UNINITIALIZED, opt.getArgs());
    }

    // Constructor (opt, longOpt, hasArg, description): all fields set
    @Test
    public void testConstructorFourArg() throws Throwable {
        Option opt = new Option("d", "delta", true, "desc-d");
        assertEquals("d", opt.getOpt());
        assertEquals("delta", opt.getLongOpt());
        assertTrue(opt.hasLongOpt());
        assertTrue(opt.hasArg());
        assertEquals("desc-d", opt.getDescription());
    }

    // getId: derived from short opt first character
    @Test
    public void testGetId_fromShortOpt() throws Throwable {
        Option opt = new Option("x", "desc");
        assertEquals((int) 'x', opt.getId());
    }

    // getId: when opt is null, falls back to long opt (via getKey)
    @Test
    public void testGetId_fromLongOptWhenOptNull() throws Throwable {
        Option opt = new Option(null, "yankee", false, "desc");
        assertEquals((int) 'y', opt.getId());
    }

    // getKey: returns opt when opt is not null
    @Test
    public void testGetKey_returnsOptWhenNotNull() throws Throwable {
        Option opt = new Option("k", "keyopt", false, "desc");
        assertEquals("k", opt.getKey());
    }

    // getKey: returns longOpt when opt is null
    @Test
    public void testGetKey_returnsLongOptWhenOptNull() throws Throwable {
        Option opt = new Option(null, "keylong", false, "desc");
        assertEquals("keylong", opt.getKey());
    }



    // getLongOpt/setLongOpt
    @Test
    public void testGetSetLongOpt() throws Throwable {
        Option opt = new Option("l", "desc");
        assertNull(opt.getLongOpt());
        opt.setLongOpt("longform");
        assertEquals("longform", opt.getLongOpt());
    }

    // hasLongOpt: true when longOpt set, false otherwise
    @Test
    public void testHasLongOpt() throws Throwable {
        Option opt = new Option("m", "desc");
        assertFalse(opt.hasLongOpt());
        opt.setLongOpt("mlong");
        assertTrue(opt.hasLongOpt());
    }

    // setOptionalArg/hasOptionalArg
    @Test
    public void testSetGetOptionalArg() throws Throwable {
        Option opt = new Option("o", true, "desc");
        assertFalse(opt.hasOptionalArg());
        opt.setOptionalArg(true);
        assertTrue(opt.hasOptionalArg());
    }

    // hasArg: false for default (uninitialized) numberOfArgs
    @Test
    public void testHasArg_uninitialized_false() throws Throwable {
        Option opt = new Option("p", "desc");
        assertFalse(opt.hasArg());
    }

    // hasArg: true when numberOfArgs == 1
    @Test
    public void testHasArg_oneArg_true() throws Throwable {
        Option opt = new Option("q", true, "desc");
        assertTrue(opt.hasArg());
    }

    // hasArg: false when numberOfArgs explicitly set to 0
    @Test
    public void testHasArg_zeroArgsExplicit_false() throws Throwable {
        Option opt = new Option("r", "desc");
        opt.setArgs(0);
        assertFalse(opt.hasArg());
    }

    // hasArg: true when numberOfArgs == UNLIMITED_VALUES
    @Test
    public void testHasArg_unlimitedValues_true() throws Throwable {
        Option opt = new Option("s", "desc");
        opt.setArgs(Option.UNLIMITED_VALUES);
        assertTrue(opt.hasArg());
    }

    // getDescription/setDescription
    @Test
    public void testGetSetDescription() throws Throwable {
        Option opt = new Option("u", "initial");
        assertEquals("initial", opt.getDescription());
        opt.setDescription("updated");
        assertEquals("updated", opt.getDescription());
    }

    // isRequired/setRequired
    @Test
    public void testIsRequired() throws Throwable {
        Option opt = new Option("v", "desc");
        assertFalse(opt.isRequired());
        opt.setRequired(true);
        assertTrue(opt.isRequired());
    }

    // setArgName/getArgName/hasArgName: null, empty, non-empty
    @Test
    public void testArgName() throws Throwable {
        Option opt = new Option("w", "desc");
        assertNull(opt.getArgName());
        assertFalse(opt.hasArgName());
        opt.setArgName("");
        assertFalse(opt.hasArgName());
        opt.setArgName("FILE");
        assertTrue(opt.hasArgName());
        assertEquals("FILE", opt.getArgName());
    }

    // hasArgs: boundary at 1 (false), 2 (true), UNLIMITED_VALUES (true)
    @Test
    public void testHasArgs_boundary() throws Throwable {
        Option opt = new Option("z", "desc");
        opt.setArgs(1);
        assertFalse(opt.hasArgs());
        opt.setArgs(2);
        assertTrue(opt.hasArgs());
        opt.setArgs(Option.UNLIMITED_VALUES);
        assertTrue(opt.hasArgs());
    }

    // setArgs/getArgs
    @Test
    public void testSetGetArgs() throws Throwable {
        Option opt = new Option("aa", "desc");
        opt.setArgs(5);
        assertEquals(5, opt.getArgs());
    }

    // setValueSeparator/getValueSeparator/hasValueSeparator
    @Test
    public void testValueSeparator() throws Throwable {
        Option opt = new Option("bb", "desc");
        assertFalse(opt.hasValueSeparator());
        opt.setValueSeparator('=');
        assertTrue(opt.hasValueSeparator());
        assertEquals('=', opt.getValueSeparator());
    }

    // addValueForProcessing: numberOfArgs UNINITIALIZED throws RuntimeException
    @Test
    public void testAddValueForProcessing_uninitializedThrows() throws Throwable {
        Option opt = new Option("cc", "desc");
        try
        {
            opt.addValueForProcessing("value");
            fail("expected RuntimeException");
        }
        catch (RuntimeException expected)
        {
            assertTrue(expected.getMessage().indexOf("NO_ARGS_ALLOWED") >= 0);
        }
    }

    // addValueForProcessing: single-arg option accepts one value, then overflow throws
    @Test
    public void testAddValueForProcessing_overflowThrows() throws Throwable {
        Option opt = new Option("dd", true, "desc");
        opt.addValueForProcessing("first");
        assertEquals("first", opt.getValue());
        try
        {
            opt.addValueForProcessing("second");
            fail("expected RuntimeException");
        }
        catch (RuntimeException expected)
        {
            // list full, as documented in add()
        }
    }

    // processValue: value has no separator char -> while loop executes 0 rounds
    @Test
    public void testProcessValue_zeroLoopRounds() throws Throwable {
        Option opt = new Option("ee", "desc");
        opt.setArgs(2);
        opt.setValueSeparator('=');
        opt.addValueForProcessing("noSeparatorHere");
        assertEquals("noSeparatorHere", opt.getValue());
        assertEquals(1, opt.getValuesList().size());
    }

    // processValue: separator loop executes once then breaks at numberOfArgs-1
    @Test
    public void testProcessValue_oneRoundThenBreak() throws Throwable {
        Option opt = new Option("ff", "desc");
        opt.setArgs(2);
        opt.setValueSeparator('=');
        opt.addValueForProcessing("a=b=c");
        assertEquals("a", opt.getValue(0));
        assertEquals("b=c", opt.getValue(1));
        assertEquals(2, opt.getValuesList().size());
    }

    // processValue: unlimited args -> loop splits all tokens, multiple rounds
    @Test
    public void testProcessValue_unlimitedMultipleRounds() throws Throwable {
        Option opt = new Option("gg", "desc");
        opt.setArgs(Option.UNLIMITED_VALUES);
        opt.setValueSeparator(',');
        opt.addValueForProcessing("a,b,c");
        String[] values = opt.getValues();
        assertEquals(3, values.length);
        assertEquals("a", values[0]);
        assertEquals("b", values[1]);
        assertEquals("c", values[2]);
    }

    // getValue(int): valid index returns corresponding value
    @Test
    public void testGetValueIndex_valid() throws Throwable {
        Option opt = new Option("hh", true, "desc");
        opt.addValueForProcessing("only");
        assertEquals("only", opt.getValue(0));
    }

    // getValue(int): out-of-bounds index throws IndexOutOfBoundsException
    @Test
    public void testGetValueIndex_outOfBounds() throws Throwable {
        Option opt = new Option("ii", true, "desc");
        opt.addValueForProcessing("only");
        try
        {
            opt.getValue(5);
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected)
        {
        }
    }

    // getValue(String default): returns default when no value, actual value otherwise
    @Test
    public void testGetValueWithDefault() throws Throwable {
        Option opt = new Option("jj", true, "desc");
        assertEquals("def", opt.getValue("def"));
        opt.addValueForProcessing("real");
        assertEquals("real", opt.getValue("def"));
    }

    // getValues: null when no values have been added
    @Test
    public void testGetValues_noValuesReturnsNull() throws Throwable {
        Option opt = new Option("kk", true, "desc");
        assertNull(opt.getValues());
        assertNull(opt.getValue());
    }

    // getValues: array reflects added values, in insertion order
    @Test
    public void testGetValues_withValues() throws Throwable {
        Option opt = new Option("ll", "desc");
        opt.setArgs(Option.UNLIMITED_VALUES);
        opt.addValueForProcessing("v1");
        opt.addValueForProcessing("v2");
        String[] values = opt.getValues();
        assertEquals(2, values.length);
        assertEquals("v1", values[0]);
        assertEquals("v2", values[1]);
    }

    // toString: includes opt and description text
    @Test
    public void testToString_containsInfo() throws Throwable {
        Option opt = new Option("mm", "my description");
        String s = opt.toString();
        assertTrue(s.indexOf("mm") >= 0);
        assertTrue(s.indexOf("my description") >= 0);
    }

    // equals: reflexive, equal opt+longOpt, differing opt, null/other class
    @Test
    public void testEquals_variants() throws Throwable {
        Option a1 = new Option("nn", "nnlong", false, "desc1");
        Option a2 = new Option("nn", "nnlong", false, "desc2");
        Option b = new Option("oo", "nnlong", false, "desc1");
        assertTrue(a1.equals(a1));
        assertTrue(a1.equals(a2));
        assertFalse(a1.equals(b));
        assertFalse(a1.equals(null));
        assertFalse(a1.equals("not an option"));
    }

    // hashCode: equal objects produce equal hash codes
    @Test
    public void testHashCode_consistent() throws Throwable {
        Option a1 = new Option("pp", "pplong", false, "d1");
        Option a2 = new Option("pp", "pplong", false, "d2");
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    // clone: produces an equal but independent copy (separate values list)
    @Test
    public void testClone_independentValuesList() throws Throwable {
        Option opt = new Option("qq", true, "desc");
        opt.addValueForProcessing("v1");
        Option clone = (Option) opt.clone();
        assertEquals(opt.getOpt(), clone.getOpt());
        assertEquals("v1", clone.getValue());
        clone.clearValues();
        assertNull(clone.getValue());
        assertEquals("v1", opt.getValue());
    }

    // clearValues: removes previously added values
    @Test
    public void testClearValues() throws Throwable {
        Option opt = new Option("rr", true, "desc");
        opt.addValueForProcessing("v1");
        opt.clearValues();
        assertNull(opt.getValue());
        assertTrue(opt.getValuesList().isEmpty());
    }

    // addValue: deprecated method always throws UnsupportedOperationException
    @Test
    public void testAddValue_deprecatedThrows() throws Throwable {
        Option opt = new Option("ss", "desc");
        try
        {
            opt.addValue("x");
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
        }
    }

    // acceptsArg: false for an option with no argument capability
    @Test
    public void testAcceptsArg_noArgCapability() throws Throwable {
        Option opt = new Option("tt", "desc");
        assertFalse(opt.acceptsArg());
    }

    // requiresArg: false once optionalArg flag is set, even if arg capable
    @Test
    public void testRequiresArg_optionalArg() throws Throwable {
        Option opt = new Option("uu", true, "desc");
        assertTrue(opt.requiresArg());
        opt.setOptionalArg(true);
        assertFalse(opt.requiresArg());
    }

    // requiresArg: for UNLIMITED_VALUES, true before any value, false after one value
    @Test
    public void testRequiresArg_unlimitedValues() throws Throwable {
        Option opt = new Option("vv", "desc");
        opt.setArgs(Option.UNLIMITED_VALUES);
        assertTrue(opt.requiresArg());
        opt.addValueForProcessing("v1");
        assertFalse(opt.requiresArg());
    }

    // public constants retain documented values
    @Test
    public void testConstants() throws Throwable {
        assertEquals(-1, Option.UNINITIALIZED);
        assertEquals(-2, Option.UNLIMITED_VALUES);
    }
}
