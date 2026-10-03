package org.apache.commons.cli;

import junit.framework.TestCase;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

public class HelpFormatterTest extends TestCase {

    public void testGettersAndSetters() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();

        formatter.setWidth(100);
        assertEquals(100, formatter.getWidth());

        formatter.setLeftPadding(5);
        assertEquals(5, formatter.getLeftPadding());

        formatter.setDescPadding(10);
        assertEquals(10, formatter.getDescPadding());

        formatter.setSyntaxPrefix("syntax: ");
        assertEquals("syntax: ", formatter.getSyntaxPrefix());

        formatter.setNewLine("\n");
        assertEquals("\n", formatter.getNewLine());

        formatter.setOptPrefix("/");
        assertEquals("/", formatter.getOptPrefix());

        formatter.setLongOptPrefix("===");
        assertEquals("===", formatter.getLongOptPrefix());

        formatter.setLongOptSeparator("=");
        assertEquals("=", formatter.getLongOptSeparator());

        formatter.setArgName("argument");
        assertEquals("argument", formatter.getArgName());

        Comparator comp = formatter.getOptionComparator();
        assertNotNull(comp);

        formatter.setOptionComparator(null);
        assertNotNull(formatter.getOptionComparator());

        formatter.setOptionComparator(comp);
        assertEquals(comp, formatter.getOptionComparator());
    }

    public void testPrintHelpExceptionsAndBasic() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");

        boolean exceptionThrown = false;
        try {
            formatter.printHelp("", options);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            formatter.printHelp(null, options);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printHelp(pw, 80, "testapp", "Header text", options, 2, 4, "Footer text", true);
        pw.flush();
        String output = out.toString();
        assertTrue(output.contains("testapp"));
        assertTrue(output.contains("Header text"));
        assertTrue(output.contains("Footer text"));
        assertTrue(output.contains("-a"));
    }

    public void testPrintHelpVariants() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("b", "beta", true, "beta option");

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printHelp(pw, 80, "syntax", null, options, 1, 3, null, false);
        pw.flush();
        assertTrue(out.toString().length() > 0);

        formatter.printHelp("syntax", options);
        formatter.printHelp("syntax", options, true);
        formatter.printHelp("syntax", "header", options, "footer");
        formatter.printHelp("syntax", "header", options, "footer", true);
        formatter.printHelp(80, "syntax", "header", options, "footer");
    }

    public void testPrintUsageWithOptionGroups() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();

        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("x", "optionX", false, "X");
        Option opt2 = new Option("y", "optionY", false, "Y");
        group.addOption(opt1);
        group.addOption(opt2);
        group.setRequired(false);
        options.addOptionGroup(group);

        OptionGroup reqGroup = new OptionGroup();
        Option opt3 = new Option("m", "optionM", false, "M");
        Option opt4 = new Option("n", "optionN", false, "N");
        reqGroup.addOption(opt3);
        reqGroup.addOption(opt4);
        reqGroup.setRequired(true);
        options.addOptionGroup(reqGroup);

        Option reqOpt = new Option("r", "req", true, "Required option with arg");
        reqOpt.setRequired(true);
        options.addOption(reqOpt);

        Option longOnlyOpt = new Option(null, "longonly", true, "Long only option");
        options.addOption(longOnlyOpt);

        Option blankArgNameOpt = new Option("b", "blank", true, "Blank arg name");
        blankArgNameOpt.setArgName("");
        options.addOption(blankArgNameOpt);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        assertTrue(out.toString().contains("myapp"));
    }

    public void testRenderOptionsEdgeCases() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        
        Option longOpt = new Option(null, "long-only-no-opt", false, null);
        options.addOption(longOpt);

        Option bothOpt = new Option("s", "short-and-long", true, "Description here");
        options.addOption(bothOpt);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printOptions(pw, 40, options, 2, 2);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testFindWrapPosAndTextWrapping() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        
        String text = "Line one\nLine two with\ttab and verylongwordthatcannotbeeasilywrappedwithoutexceedingthewidthlimit";
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printWrapped(pw, 20, 5, text);
        formatter.printWrapped(pw, 10, text);
        
        // Test nextLineTabStop >= width scenario
        formatter.printWrapped(pw, 10, 15, "Short text");
        pw.flush();
        
        int pos = formatter.findWrapPos("short text", 20, 0);
        assertEquals(-1, pos);

        int posNewline = formatter.findWrapPos("line1\nline2", 10, 0);
        assertTrue(posNewline > 0);
        
        int posTab = formatter.findWrapPos("line1\tline2", 10, 0);
        assertTrue(posTab > 0);
    }

    public void testRtrimAndPadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        
        assertNull(formatter.rtrim(null));
        assertEquals("", formatter.rtrim(""));
        assertEquals("   abc", formatter.rtrim("   abc   "));
        assertEquals("", formatter.rtrim("   "));

        String padding = formatter.createPadding(5);
        assertEquals("     ", padding);
    }

    public void testOptionComparatorDirectly() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Comparator comp = formatter.getOptionComparator();

        Option o1 = new Option("b", "beta", false, "");
        Option o2 = new Option("a", "alpha", false, "");

        int result = comp.compare(o1, o2);
        assertTrue(result > 0);
    }
}