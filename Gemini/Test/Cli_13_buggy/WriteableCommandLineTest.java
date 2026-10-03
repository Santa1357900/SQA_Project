package org.apache.commons.cli2;

import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

public class WriteableCommandLineTest extends TestCase {

    private WriteableCommandLine dummyWriteableCommandLine;

    protected void setUp() throws Exception {
        super.setUp();
        dummyWriteableCommandLine = new WriteableCommandLine() {
            public void addOption(Option option) {
            }

            public void addValue(Option option, Object value) {
            }

            public void setDefaultValues(Option option, List defaultValues) {
            }

            public void addSwitch(Option option, boolean value) throws IllegalStateException {
            }

            public void setDefaultSwitch(Option option, Boolean defaultSwitch) {
            }

            public void addProperty(String property, String value) {
            }

            public boolean looksLikeOption(String argument) {
                if (argument == null) {
                    return false;
                }
                return argument.startsWith("-");
            }

            public List getOptionValues(Option option) {
                return new ArrayList<Object>();
            }

            public List getDefaultValues(Option option) {
                return new ArrayList<Object>();
            }

            public Boolean getSwitch(Option option) {
                return Boolean.FALSE;
            }

            public Boolean getDefaultSwitch(Option option) {
                return Boolean.FALSE;
            }

            public String getProperty(String property) {
                return null;
            }

            public java.util.Set getProperties() {
                return new java.util.HashSet<String>();
            }

            public java.util.Set getOptions() {
                return new java.util.HashSet<Option>();
            }

            public boolean hasOption(Option option) {
                return false;
            }

            public int getOptionCount(Option option) {
                return 0;
            }
        };
    }

    public void testLooksLikeOptionValid() throws Throwable {
        boolean result = dummyWriteableCommandLine.looksLikeOption("-f");
        assertTrue(result);
    }

    public void testLooksLikeOptionInvalid() throws Throwable {
        boolean result = dummyWriteableCommandLine.looksLikeOption("file.txt");
        assertFalse(result);
    }

    public void testLooksLikeOptionNull() throws Throwable {
        boolean result = dummyWriteableCommandLine.looksLikeOption(null);
        assertFalse(result);
    }

    public void testInterfaceMethodsDirectly() throws Throwable {
        Option dummyOption = new Option() {
            public String getId() { return "1"; }
            public String getDescription() { return "desc"; }
            public int getPreferredName() { return 0; }
            public java.util.Set getTriggers() { return new java.util.HashSet<String>(); }
            public boolean canProcess(WriteableCommandLine commandLine, List arguments) { return false; }
            public void process(WriteableCommandLine commandLine, List arguments) {}
            public void appendUsage(StringBuffer buffer, java.util.Set helpSettings, java.util.Comparator comparator) {}
            public Option findOption(String trigger) { return null; }
            public void validate(WriteableCommandLine commandLine) {}
        };

        dummyWriteableCommandLine.addOption(dummyOption);
        dummyWriteableCommandLine.addValue(dummyOption, "val");
        
        List<String> defaults = new ArrayList<String>();
        defaults.add("defaultVal");
        dummyWriteableCommandLine.setDefaultValues(dummyOption, defaults);

        dummyWriteableCommandLine.addSwitch(dummyOption, true);
        dummyWriteableCommandLine.setDefaultSwitch(dummyOption, Boolean.TRUE);
        dummyWriteableCommandLine.addProperty("propKey", "propVal");

        assertNotNull(dummyWriteableCommandLine);
    }
}