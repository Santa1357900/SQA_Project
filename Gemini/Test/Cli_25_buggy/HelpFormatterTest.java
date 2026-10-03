package org.apache.commons.cli;

import junit.framework.TestCase;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

public class HelpFormatterTest extends TestCase
{
    public void testGettersAndSetters() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();

        formatter.setWidth(80);
        assertEquals(80, formatter.getWidth());

        formatter.setLeftPadding(5);
        assertEquals(5, formatter.getLeftPadding());

        formatter.setDescPadding(4);
        assertEquals(4, formatter.getDescPadding());

        formatter.setSyntaxPrefix("syntax: ");
        assertEquals("syntax: ", formatter.getSyntaxPrefix());

        formatter.setNewLine("\n");
        assertEquals("\n", formatter.getNewLine());

        formatter.setOptPrefix("/");
        assertEquals("/", formatter.getOptPrefix());

        formatter.setLongOptPrefix("---");
        assertEquals("---", formatter.getLongOptPrefix());

        formatter.setArgName("filename");
        assertEquals("filename", formatter.getArgName());

        Comparator customComp = new Comparator() {
            public int compare(Object o1, Object o2) {
                return 0;
            }
        };
        formatter.setOptionComparator(customComp);
        assertEquals(customComp, formatter.getOptionComparator());

        formatter.setOptionComparator(null);
        assertNotNull(formatter.getOptionComparator());
    }

    public void testPrintHelpExceptions() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        try {
            formatter.printHelp(pw, 80, null, "header", options, 1, 3, "footer", true);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("cmdLineSyntax not provided") != -1);
        }

        try {
            formatter.printHelp(pw, 80, "", "header", options, 1, 3, "footer", false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("cmdLineSyntax not provided") != -1);
        }
    }

    public void testPrintHelpWithOptions() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "alpha", false, "Alpha option");
        options.addOption("b", "beta", true, "Beta option with arg");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printHelp(pw, 60, "app", "Header text", options, 2, 4, "Footer text", true);
        String output = sw.toString();
        assertTrue(output.indexOf("usage:") != -1);
        assertTrue(output.indexOf("Header text") != -1);
        assertTrue(output.indexOf("Footer text") != -1);
    }

    public void testPrintHelpWithoutAutoUsage() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("c", "char", false, "Char option");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printHelp(pw, 80, "myApp arg1", "Header", options, 2, 2, "Footer", false);
        String output = sw.toString();
        assertTrue(output.indexOf("usage: myApp arg1") != -1);
    }

    public void testPrintHelpSimpleOverloads() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();

        formatter.printHelp("syntax", options);
        formatter.printHelp("syntax", options, true);
        formatter.printHelp("syntax", "header", options, "footer");
        formatter.printHelp("syntax", "header", options, "footer", true);
        formatter.printHelp(80, "syntax", "header", options, "footer");
        formatter.printHelp(80, "syntax", "header", options, "footer", true);
    }

    public void testFindWrapPosEdges() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        
        String text = "Line one\nLine two\twith tab";
        int pos = formatter.findWrapPos(text, 15, 0);
        assertTrue(pos != -1);

        String longText = "This is a very long text without spaces insideitandwrappedforcefully";
        int wrapPos = formatter.findWrapPos(longText, 10, 0);
        assertTrue(wrapPos != -1);

        int noWrap = formatter.findWrapPos("Short", 10, 0);
        assertEquals(-1, noWrap);
    }

    public void testRtrim() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        assertNull(formatter.rtrim(null));
        assertEquals("", formatter.rtrim(""));
        assertEquals("abc", formatter.rtrim("abc   "));
        assertEquals("abc", formatter.rtrim("abc"));
    }

    public void testRenderWrappedTextInfiniteLoopProtection() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // nextLineTabStop >= width should trigger line width adjustment
        formatter.printWrapped(pw, 10, 15, "Some long text here");
        assertTrue(sw.toString().length() > 0);
    }

    public void testOptionGroupsInUsage() throws Throwable
    {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("x", "xopt", false, "X opt");
        Option opt2 = new Option("y", "yopt", false, "Y opt");
        group.addOption(opt1);
        group.addOption(opt2);
        group.setRequired(true);
        options.addOptionGroup(group);

        OptionGroup optionalGroup = new OptionGroup();
        Option opt3 = new Option("m", "mopt", false, "M opt");
        Option opt4 = new Option("n", "nopt", false, "N opt");
        optionalGroup.addOption(opt3);
        optionalGroup.addOption(opt4);
        optionalGroup.setRequired(false);
        options.addOptionGroup(optionalGroup);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printUsage(pw, 80, "testApp", options);
        String output = sw.toString();
        assertTrue(output.indexOf("-x") != -1 || output.indexOf("-y") != -1);
    }
}