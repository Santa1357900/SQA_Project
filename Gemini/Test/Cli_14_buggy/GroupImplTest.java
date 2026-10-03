package org.apache.commons.cli2.option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ListIterator;

import junit.framework.TestCase;

import org.apache.commons.cli2.DisplaySetting;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.HelpLine;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.OptionException;
import org.apache.commons.cli2.WriteableCommandLine;
import org.apache.commons.cli2.commandline.WriteableCommandLineImpl;
import org.apache.commons.cli2.resource.ResourceConstants;
import org.apache.commons.cli2.validation.Validator;

public class GroupImplTest extends TestCase {

    private static class DummyOption extends OptionImpl {
        private final Set triggers;
        private final Set prefixes;
        private final boolean required;
        private final String preferredName;

        public DummyOption(final String name, final boolean required) {
            super(0, required);
            this.preferredName = name;
            this.required = required;
            this.triggers = new HashSet<String>();
            if (name != null) {
                this.triggers.add(name);
            }
            this.prefixes = new HashSet<String>();
            this.prefixes.add("-");
        }

        public void appendUsage(StringBuffer buffer, Set helpSettings, Comparator comp) {
            if (preferredName != null) {
                buffer.append(preferredName);
            }
        }

        public List helpLines(int depth, Set helpSettings, Comparator comp) {
            List<HelpLine> lines = new ArrayList<HelpLine>();
            lines.add(new HelpLineImpl(this, depth));
            return lines;
        }

        public Set getTriggers() {
            return triggers;
        }

        public Set getPrefixes() {
            return prefixes;
        }

        public void process(WriteableCommandLine commandLine, ListIterator arguments) throws OptionException {
            if (arguments.hasNext()) {
                arguments.next();
            }
            commandLine.addOption(this);
        }

        public void validate(WriteableCommandLine commandLine) throws OptionException {
            if (required && !commandLine.hasOption(this)) {
                throw new OptionException(this, ResourceConstants.MISSING_OPTION);
            }
        }

        public String getPreferredName() {
            return preferredName;
        }

        public boolean canProcess(WriteableCommandLine commandLine, String arg) {
            return triggers.contains(arg);
        }
    }

    public void testGroupCreationAndBasics() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        Option optA = new DummyOption("-a", false);
        Option optB = new DummyOption("-b", false);
        options.add(optA);
        options.add(optB);

        GroupImpl group = new GroupImpl(options, "groupName", "groupDescription", 1, 2);

        assertEquals("groupName", group.getPreferredName());
        assertEquals("groupDescription", group.getDescription());
        assertEquals(1, group.getMinimum());
        assertEquals(2, group.getMaximum());
        assertTrue(group.isRequired());

        Set prefixes = group.getPrefixes();
        assertNotNull(prefixes);
        assertTrue(prefixes.contains("-"));

        Set triggers = group.getTriggers();
        assertNotNull(triggers);
        assertTrue(triggers.contains("-a"));
        assertTrue(triggers.contains("-b"));

        assertNotNull(group.getOptions());
        assertNotNull(group.getAnonymous());
        assertNotNull(group.findOption("-a"));
        assertNull(group.findOption("-nonexistent"));
    }

    public void testCanProcess() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        Option optA = new DummyOption("-a", false);
        options.add(optA);

        GroupImpl group = new GroupImpl(options, "g", "desc", 0, 1);
        WriteableCommandLine commandLine = new WriteableCommandLineImpl(group, new ArrayList<String>());

        assertTrue(group.canProcess(commandLine, "-a"));
        assertFalse(group.canProcess(commandLine, null));
        assertFalse(group.canProcess(commandLine, "-unknown"));
    }

    public void testProcess() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        Option optA = new DummyOption("-a", false);
        options.add(optA);

        GroupImpl group = new GroupImpl(options, "g", "desc", 0, 1);
        WriteableCommandLine commandLine = new WriteableCommandLineImpl(group, new ArrayList<String>());

        List<String> argsList = new ArrayList<String>();
        argsList.add("-a");
        ListIterator iterator = argsList.listIterator();

        group.process(commandLine, iterator);
        assertTrue(commandLine.hasOption(optA));
    }

    public void testValidateSuccess() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        Option optA = new DummyOption("-a", false);
        options.add(optA);

        GroupImpl group = new GroupImpl(options, "g", "desc", 0, 1);
        WriteableCommandLine commandLine = new WriteableCommandLineImpl(group, new ArrayList<String>());

        group.validate(commandLine);
    }

    public void testValidateMissingOption() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        Option optA = new DummyOption("-a", true);
        options.add(optA);

        GroupImpl group = new GroupImpl(options, "g", "desc", 1, 1);
        WriteableCommandLine commandLine = new WriteableCommandLineImpl(group, new ArrayList<String>());

        try {
            group.validate(commandLine);
            fail("Expected OptionException");
        } catch (OptionException e) {
            assertNotNull(e);
        }
    }

    public void testValidateUnexpectedOption() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        Option optA = new DummyOption("-a", false);
        Option optB = new DummyOption("-b", false);
        options.add(optA);
        options.add(optB);

        GroupImpl group = new GroupImpl(options, "g", "desc", 0, 1);
        WriteableCommandLine commandLine = new WriteableCommandLineImpl(group, new ArrayList<String>());
        commandLine.addOption(optA);
        commandLine.addOption(optB);

        try {
            group.validate(commandLine);
            fail("Expected OptionException for too many options");
        } catch (OptionException e) {
            assertNotNull(e);
        }
    }

    public void testAppendUsageVariants() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        options.add(new DummyOption("-a", false));

        GroupImpl group = new GroupImpl(options, "group", "desc", 0, 1);
        StringBuffer buffer = new StringBuffer();
        Set<DisplaySetting> settings = new HashSet<DisplaySetting>();
        settings.add(DisplaySetting.DISPLAY_OPTIONAL);
        settings.add(DisplaySetting.DISPLAY_GROUP_NAME);
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        settings.add(DisplaySetting.DISPLAY_GROUP_OUTER);
        settings.add(DisplaySetting.DISPLAY_GROUP_ARGUMENT);

        group.appendUsage(buffer, settings, null);
        assertNotNull(buffer.toString());

        StringBuffer buffer2 = new StringBuffer();
        group.appendUsage(buffer2, settings, new Comparator<Option>() {
            public int compare(Option o1, Option o2) {
                return o1.getPreferredName().compareTo(o2.getPreferredName());
            }
        }, ",");
        assertNotNull(buffer2.toString());
    }

    public void testHelpLines() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        options.add(new DummyOption("-a", false));

        GroupImpl group = new GroupImpl(options, "group", "desc", 0, 1);
        Set<DisplaySetting> settings = new HashSet<DisplaySetting>();
        settings.add(DisplaySetting.DISPLAY_GROUP_NAME);
        settings.add(DisplaySetting.DISPLAY_GROUP_EXPANDED);
        settings.add(DisplaySetting.DISPLAY_GROUP_ARGUMENT);

        List lines = group.helpLines(0, settings, null);
        assertNotNull(lines);
        assertFalse(lines.isEmpty());
    }

    public void testDefaults() throws Throwable {
        List<Option> options = new ArrayList<Option>();
        options.add(new DummyOption("-a", false));

        GroupImpl group = new GroupImpl(options, "group", "desc", 0, 1);
        WriteableCommandLine commandLine = new WriteableCommandLineImpl(group, new ArrayList<String>());
        group.defaults(commandLine);
    }
}