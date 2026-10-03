package org.apache.commons.cli2;

import junit.framework.TestCase;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.ArrayList;

public class OptionTest extends TestCase {

    private static class DummyOption implements Option {
        private final int id;
        private final String preferredName;
        private final String description;
        private final boolean required;

        public DummyOption(int id, String preferredName, String description, boolean required) {
            this.id = id;
            this.preferredName = preferredName;
            this.description = description;
            this.required = required;
        }

        public void process(WriteableCommandLine commandLine, ListIterator args) throws OptionException {
            if (args != null && args.hasNext()) {
                args.next();
            }
        }

        public void defaults(WriteableCommandLine commandLine) {
        }

        public boolean canProcess(WriteableCommandLine commandLine, String argument) {
            return argument != null && argument.equals(preferredName);
        }

        public boolean canProcess(WriteableCommandLine commandLine, ListIterator arguments) {
            if (arguments != null && arguments.hasNext()) {
                Object next = arguments.next();
                arguments.previous();
                return next != null && next.equals(preferredName);
            }
            return false;
        }

        public Set getTriggers() {
            Set triggers = new HashSet();
            triggers.add(preferredName);
            return triggers;
        }

        public Set getPrefixes() {
            Set prefixes = new HashSet();
            prefixes.add("-");
            return prefixes;
        }

        public void validate(WriteableCommandLine commandLine) throws OptionException {
            if (required && commandLine == null) {
                throw new OptionException(this, "Missing required option");
            }
        }

        public List helpLines(int depth, Set helpSettings, Comparator comp) {
            return new ArrayList();
        }

        public void appendUsage(StringBuffer buffer, Set helpSettings, Comparator comp) {
            if (buffer != null) {
                buffer.append(preferredName);
            }
        }

        public String getPreferredName() {
            return preferredName;
        }

        public String getDescription() {
            return description;
        }

        public int getId() {
            return id;
        }

        public Option findOption(String trigger) {
            if (trigger != null && trigger.equals(preferredName)) {
                return this;
            }
            return null;
        }

        public boolean isRequired() {
            return required;
        }
    }

    public void testDummyOptionContract() throws Throwable {
        Option option = new DummyOption(123, "test", "A test option", true);

        assertEquals(123, option.getId());
        assertEquals("test", option.getPreferredName());
        assertEquals("A test option", option.getDescription());
        assertTrue(option.isRequired());

        Set triggers = option.getTriggers();
        assertNotNull(triggers);
        assertTrue(triggers.contains("test"));

        Set prefixes = option.getPrefixes();
        assertNotNull(prefixes);
        assertTrue(prefixes.contains("-"));

        assertTrue(option.canProcess(null, "test"));
        assertFalse(option.canProcess(null, "other"));

        List argsList = new ArrayList();
        argsList.add("test");
        ListIterator iterator = argsList.listIterator();
        assertTrue(option.canProcess(null, iterator));

        option.process(null, iterator);

        option.defaults(null);

        StringBuffer buffer = new StringBuffer();
        option.appendUsage(buffer, new HashSet(), null);
        assertEquals("test", buffer.toString());

        List helpLines = option.helpLines(0, new HashSet(), null);
        assertNotNull(helpLines);

        Option found = option.findOption("test");
        assertEquals(option, found);

        Option notFound = option.findOption("missing");
        assertNull(notFound);

        try {
            option.validate(null);
            fail("Should have thrown OptionException");
        } catch (OptionException e) {
            assertNotNull(e);
        }
    }

    public void testDummyOptionOptional() throws Throwable {
        Option option = new DummyOption(456, "opt", "Optional option", false);
        assertFalse(option.isRequired());
        option.validate(null);
    }
}