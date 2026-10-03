package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

public class OptionsTest {

    @Test
    public void testAddOptionShortDesc() throws Throwable {
        Options options = new Options();
        Options result = options.addOption("a", "description a");
        assertSame(options, result);
        assertTrue(options.hasOption("a"));
        assertTrue(options.hasShortOption("a"));
        assertFalse(options.hasLongOption("a"));
        
        Option opt = options.getOption("a");
        assertNotNull(opt);
        assertEquals("a", opt.getOpt());
        assertEquals("description a", opt.getDescription());
        assertFalse(opt.hasArg());
    }

    @Test
    public void testAddOptionShortArgDesc() throws Throwable {
        Options options = new Options();
        Options result = options.addOption("b", true, "description b");
        assertSame(options, result);
        assertTrue(options.hasOption("b"));
        assertTrue(options.hasShortOption("b"));
        
        Option opt = options.getOption("b");
        assertNotNull(opt);
        assertEquals("b", opt.getOpt());
        assertTrue(opt.hasArg());
        assertEquals("description b", opt.getDescription());
    }

    @Test
    public void testAddOptionFull() throws Throwable {
        Options options = new Options();
        Options result = options.addOption("c", "longC", true, "description c");
        assertSame(options, result);
        
        assertTrue(options.hasOption("c"));
        assertTrue(options.hasOption("longC"));
        assertTrue(options.hasShortOption("c"));
        assertTrue(options.hasLongOption("longC"));
        
        Option opt1 = options.getOption("c");
        Option opt2 = options.getOption("longC");
        assertEquals(opt1, opt2);
        assertEquals("longC", opt1.getLongOpt());
        assertTrue(opt1.hasArg());
    }

    @Test
    public void testAddOptionInstanceRequired() throws Throwable {
        Options options = new Options();
        Option opt = new Option("d", "longD", false, "desc d");
        opt.setRequired(true);
        
        options.addOption(opt);
        
        assertTrue(options.hasOption("d"));
        assertTrue(options.hasOption("longD"));
        
        List required = options.getRequiredOptions();
        assertEquals(1, required.size());
        assertEquals("d", required.get(0));
        
        // Add again with required to test replacement/existing check
        Option opt2 = new Option("d", "longD", false, "desc d");
        opt2.setRequired(true);
        options.addOption(opt2);
        assertEquals(1, options.getRequiredOptions().size());
    }

    @Test
    public void testAddOptionGroup() throws Throwable {
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.setRequired(true);
        
        Option opt1 = new Option("e", "longE", false, "desc e");
        Option opt2 = new Option("f", "longF", false, "desc f");
        group.addOption(opt1);
        group.addOption(opt2);
        
        options.addOptionGroup(group);
        
        Collection<OptionGroup> groups = options.getOptionGroups();
        assertEquals(1, groups.size());
        
        List required = options.getRequiredOptions();
        assertTrue(required.contains(group));
        
        assertFalse(opt1.isRequired());
        assertFalse(opt2.isRequired());
        
        assertSame(group, options.getOptionGroup(opt1));
        assertSame(group, options.getOptionGroup(opt2));
        
        assertTrue(options.hasOption("e"));
        assertTrue(options.hasOption("f"));
    }

    @Test
    public void testGetOptionsAndHelpOptions() throws Throwable {
        Options options = new Options();
        options.addOption("g", "desc g");
        options.addOption("h", "longH", true, "desc h");
        
        Collection<Option> allOpts = options.getOptions();
        assertEquals(2, allOpts.size());
        
        List<Option> helpOpts = options.helpOptions();
        assertEquals(2, helpOpts.size());
    }

    @Test
    public void testGetMatchingOptions() throws Throwable {
        Options options = new Options();
        options.addOption("i", "include", false, "include");
        options.addOption("j", "indent", false, "indent");
        options.addOption("k", "index", false, "index");
        options.addOption("l", "other", false, "other");

        List<String> matches = options.getMatchingOptions("in");
        assertEquals(3, matches.size());
        assertTrue(matches.contains("include"));
        assertTrue(matches.contains("indent"));
        assertTrue(matches.contains("index"));
        assertFalse(matches.contains("other"));

        List<String> exactMatches = options.getMatchingOptions("--include");
        assertEquals(1, exactMatches.size());
        assertEquals("include", exactMatches.get(0));
    }

    @Test
    public void testGetOptionWithHyphens() throws Throwable {
        Options options = new Options();
        options.addOption("m", "my-long", true, "desc m");
        
        assertNotNull(options.getOption("-m"));
        assertNotNull(options.getOption("--my-long"));
        assertNotNull(options.getOption("-my-long"));
        assertNull(options.getOption("nonexistent"));
        
        assertTrue(options.hasOption("-m"));
        assertTrue(options.hasOption("--my-long"));
        assertFalse(options.hasOption("-nonexistent"));
        
        assertTrue(options.hasShortOption("-m"));
        assertFalse(options.hasShortOption("--my-long"));
        
        assertTrue(options.hasLongOption("--my-long"));
        assertFalse(options.hasLongOption("-m"));
    }

    @Test
    public void testToString() throws Throwable {
        Options options = new Options();
        options.addOption("n", "desc n");
        String str = options.toString();
        assertNotNull(str);
        assertTrue(str.contains("Options"));
        assertTrue(str.contains("n"));
    }
}