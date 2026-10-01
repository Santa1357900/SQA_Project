package org.apache.commons.cli2.option;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.commons.cli2.DisplaySetting;
import org.apache.commons.cli2.HelpLine;
import org.apache.commons.cli2.OptionException;
import org.junit.Test;
import static org.junit.Assert.*;

public class GroupImplClaudeTest {

    // Constructor: empty options list -> both options and anonymous are empty
    @Test
    public void testConstructor_emptyOptionsList_optionsAndAnonymousEmpty() throws Throwable {
        List options = new ArrayList();
        GroupImpl group = new GroupImpl(options, "name", "desc", 0, 1);
        assertTrue(group.getOptions().isEmpty());
        assertTrue(group.getAnonymous().isEmpty());
    }

    // Constructor: name == null is allowed per Javadoc ("the name of this Group, or null")
    @Test
    public void testConstructor_nullName_getPreferredNameReturnsNull() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), null, "desc", 0, 1);
        assertNull(group.getPreferredName());
    }

    // canProcess: arg == null must short-circuit and return false
    @Test
    public void testCanProcess_nullArg_returnsFalse() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        assertFalse(group.canProcess(null, null));
    }

    // getPrefixes: no options -> empty prefixes set
    @Test
    public void testGetPrefixes_emptyOptions_returnsEmptySet() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        assertTrue(group.getPrefixes().isEmpty());
    }

    // getPrefixes: returned set must be unmodifiable
    @Test
    public void testGetPrefixes_unmodifiable_throwsOnAdd() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        try {
            group.getPrefixes().add("-");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getTriggers: no options -> empty trigger set
    @Test
    public void testGetTriggers_emptyOptions_returnsEmptySet() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        assertTrue(group.getTriggers().isEmpty());
    }

    // getTriggers: options with no triggers of their own contribute nothing
    @Test
    public void testGetTriggers_withChildGroupsNoTriggers_returnsEmptySet() throws Throwable {
        List options = new ArrayList();
        options.add(new GroupImpl(new ArrayList(), "c1", "d", 0, 1));
        options.add(new GroupImpl(new ArrayList(), "c2", "d", 0, 1));
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 2);
        assertTrue(parent.getTriggers().isEmpty());
    }

    // getTriggers: returned set must be unmodifiable
    @Test
    public void testGetTriggers_unmodifiable_throwsOnAdd() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        try {
            group.getTriggers().add("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // process: empty argument iterator -> while loop executes zero times, no exception, iterator unchanged
    @Test
    public void testProcess_emptyArguments_noExceptionNoChange() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        ListIterator it = new ArrayList().listIterator();
        group.process(null, it);
        assertFalse(it.hasNext());
    }

    // validate: minimum == 0 with no options present -> present(0) < minimum(0) is false, no exception
    @Test
    public void testValidate_minimumZeroEmptyOptions_noException() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        try {
            group.validate(null);
        } catch (OptionException e) {
            fail("did not expect OptionException");
        }
        assertTrue(group.getOptions().isEmpty());
    }

    // validate: minimum == 1 with no options present -> too few options, must throw OptionException
    @Test
    public void testValidate_minimumOneEmptyOptions_throwsOptionException() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 1, 5);
        try {
            group.validate(null);
            fail("expected OptionException");
        } catch (OptionException expected) {
        }
    }

    // validate: nested child Group is always validated (instanceof Group), propagates its OptionException
    @Test
    public void testValidate_nestedRequiredChildGroup_throwsOptionException() throws Throwable {
        GroupImpl requiredChild = new GroupImpl(new ArrayList(), "child", "d", 1, 1);
        List parentOptions = new ArrayList();
        parentOptions.add(requiredChild);
        GroupImpl parent = new GroupImpl(parentOptions, "parent", "d", 0, 1);
        try {
            parent.validate(null);
            fail("expected OptionException");
        } catch (OptionException expected) {
        }
    }

    // getPreferredName: non-null name is returned as-is
    @Test
    public void testGetPreferredName_nonNullName_returnsName() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "myGroup", "desc", 0, 1);
        assertEquals("myGroup", group.getPreferredName());
    }

    // getDescription: returns exactly the description given at construction
    @Test
    public void testGetDescription_returnsGivenDescription() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "n", "my description", 0, 1);
        assertEquals("my description", group.getDescription());
    }

    // appendUsage: no display settings -> named branch only, output equals the name
    @Test
    public void testAppendUsage_noSettings_appendsNameOnly() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "grp", "d", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        group.appendUsage(buffer, settings, null);
        assertEquals("grp", buffer.toString());
    }

    // appendUsage: DISPLAY_GROUP_EXPANDED only, empty options -> named=false, nothing appended
    @Test
    public void testAppendUsage_expandedOnly_appendsEmptyForEmptyOptions() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "grp", "d", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        group.appendUsage(buffer, settings, null);
        assertEquals("", buffer.toString());
    }

    // appendUsage: EXPANDED + NAME -> both branch true, produces "name ()"
    @Test
    public void testAppendUsage_expandedAndName_appendsNameWithParens() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "grp", "d", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        settings.add(DisplaySetting.DISPLAY_GROUP_NAME);
        group.appendUsage(buffer, settings, null);
        assertEquals("grp ()", buffer.toString());
    }

    // appendUsage: DISPLAY_OPTIONAL with minimum==0 -> optional true, wraps name in brackets
    @Test
    public void testAppendUsage_optionalMinimumZero_wrapsInBrackets() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "grp", "d", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_OPTIONAL);
        group.appendUsage(buffer, settings, null);
        assertEquals("[grp]", buffer.toString());
    }

    // appendUsage: DISPLAY_OPTIONAL with minimum!=0 -> optional false (edge on minimum==0 check), no brackets
    @Test
    public void testAppendUsage_optionalMinimumNonZero_noBrackets() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "grp", "d", 1, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_OPTIONAL);
        group.appendUsage(buffer, settings, null);
        assertEquals("grp", buffer.toString());
    }

    // appendUsage (3-arg overload): default separator "|" used between two expanded child options
    @Test
    public void testAppendUsage_defaultSeparator_twoChildrenExpanded_usesPipe() throws Throwable {
        List options = new ArrayList();
        options.add(new GroupImpl(new ArrayList(), "c1", "d", 0, 1));
        options.add(new GroupImpl(new ArrayList(), "c2", "d", 0, 1));
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        parent.appendUsage(buffer, settings, null);
        assertEquals("|", buffer.toString());
    }

    // appendUsage (4-arg overload): custom separator is honoured between two expanded child options
    @Test
    public void testAppendUsage_customSeparator_twoChildrenExpanded_usesCustomSeparator() throws Throwable {
        List options = new ArrayList();
        options.add(new GroupImpl(new ArrayList(), "c1", "d", 0, 1));
        options.add(new GroupImpl(new ArrayList(), "c2", "d", 0, 1));
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        parent.appendUsage(buffer, settings, null, ",");
        assertEquals(",", buffer.toString());
    }

    // appendUsage: single expanded child -> loop has no next(), no separator appended
    @Test
    public void testAppendUsage_singleChildExpanded_noSeparatorAppended() throws Throwable {
        List options = new ArrayList();
        options.add(new GroupImpl(new ArrayList(), "c1", "d", 0, 1));
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        parent.appendUsage(buffer, settings, null);
        assertEquals("", buffer.toString());
    }

    // helpLines: no display settings -> nothing added, empty result
    @Test
    public void testHelpLines_noSettings_returnsEmptyList() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        Set settings = new HashSet();
        List lines = group.helpLines(0, settings, null);
        assertTrue(lines.isEmpty());
    }

    // helpLines: DISPLAY_GROUP_NAME -> exactly one HelpLine for this group
    @Test
    public void testHelpLines_displayGroupName_returnsSingleHelpLine() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_NAME);
        List lines = group.helpLines(0, settings, null);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0) instanceof HelpLine);
    }

    // helpLines: NAME + EXPANDED with two children -> one line per group level (1 parent + 2 children)
    @Test
    public void testHelpLines_expandedWithChildren_returnsHelpLineForEachLevel() throws Throwable {
        List options = new ArrayList();
        options.add(new GroupImpl(new ArrayList(), "c1", "d", 0, 1));
        options.add(new GroupImpl(new ArrayList(), "c2", "d", 0, 1));
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 1);
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_NAME);
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        List lines = parent.helpLines(0, settings, null);
        assertEquals(3, lines.size());
    }

    // helpLines: DISPLAY_GROUP_ARGUMENT with no anonymous arguments -> loop 0 iterations, empty result
    @Test
    public void testHelpLines_displayGroupArgumentNoAnonymous_returnsEmptyList() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        Set settings = new HashSet();
        settings.add(DisplaySetting.DISPLAY_GROUP_ARGUMENT);
        List lines = group.helpLines(0, settings, null);
        assertTrue(lines.isEmpty());
    }

    // getOptions: non-Argument options are kept, in the original order, as the same instances
    @Test
    public void testGetOptions_withNonArgumentChildren_returnsSameElements() throws Throwable {
        GroupImpl child1 = new GroupImpl(new ArrayList(), "c1", "d", 0, 1);
        GroupImpl child2 = new GroupImpl(new ArrayList(), "c2", "d", 0, 1);
        List options = new ArrayList();
        options.add(child1);
        options.add(child2);
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 2);
        List result = parent.getOptions();
        assertEquals(2, result.size());
        assertSame(child1, result.get(0));
        assertSame(child2, result.get(1));
    }

    // getOptions: returned list must be unmodifiable
    @Test
    public void testGetOptions_unmodifiable_throwsOnAdd() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        try {
            group.getOptions().add(new GroupImpl(new ArrayList(), "x", "d", 0, 1));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getAnonymous: no Argument instances in the options list -> empty anonymous list
    @Test
    public void testGetAnonymous_noArguments_returnsEmptyList() throws Throwable {
        List options = new ArrayList();
        options.add(new GroupImpl(new ArrayList(), "c", "d", 0, 1));
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 1);
        assertTrue(parent.getAnonymous().isEmpty());
    }

    // getAnonymous: returned list must be unmodifiable
    @Test
    public void testGetAnonymous_unmodifiable_throwsOnAdd() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        try {
            group.getAnonymous().add(new GroupImpl(new ArrayList(), "x", "d", 0, 1));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // findOption: empty options list -> no option to search, returns null
    @Test
    public void testFindOption_emptyOptions_returnsNull() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "g", "d", 0, 1);
        assertNull(group.findOption("--x"));
    }

    // findOption: children present but none match the trigger -> returns null after full loop
    @Test
    public void testFindOption_withChildGroupsNoMatch_returnsNull() throws Throwable {
        List options = new ArrayList();
        options.add(new GroupImpl(new ArrayList(), "c1", "d", 0, 1));
        options.add(new GroupImpl(new ArrayList(), "c2", "d", 0, 1));
        GroupImpl parent = new GroupImpl(options, "p", "d", 0, 2);
        assertNull(parent.findOption("--nonexistent"));
    }

    // getMinimum: returns exactly the value given at construction, including boundary MAX_VALUE
    @Test
    public void testGetMinimum_returnsGivenValue() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "n", "d", Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, group.getMinimum());
    }

    // getMaximum: returns exactly the value given at construction, including boundary MIN_VALUE
    @Test
    public void testGetMaximum_returnsGivenValue() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "n", "d", 0, Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, group.getMaximum());
    }

    // isRequired: minimum == 0 -> not required (boundary of the > 0 comparison)
    @Test
    public void testIsRequired_minimumZero_returnsFalse() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "n", "d", 0, 1);
        assertFalse(group.isRequired());
    }

    // isRequired: minimum == 1 -> required (just above the boundary)
    @Test
    public void testIsRequired_minimumOne_returnsTrue() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "n", "d", 1, 1);
        assertTrue(group.isRequired());
    }

    // isRequired: negative minimum -> still not required, since check is strictly > 0
    @Test
    public void testIsRequired_minimumNegative_returnsFalse() throws Throwable {
        GroupImpl group = new GroupImpl(new ArrayList(), "n", "d", -1, 1);
        assertFalse(group.isRequired());
    }
}
