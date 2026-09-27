package org.apache.commons.cli;

import junit.framework.TestCase;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HelpFormatterTest extends TestCase {

    public void testGetSetters() throws Throwable {
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

        formatter.setLongOptPrefix("---");
        assertEquals("---", formatter.getLongOptPrefix());

        formatter.setArgName("filename");
        assertEquals("filename", formatter.getArgName());
    }

    public void testPrintHelpExceptionsAndEdgeCases() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();

        try {
            formatter.printHelp("", options);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cmdLineSyntax not provided"));
        }

        try {
            formatter.printHelp(null, options);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cmdLineSyntax not provided"));
        }
    }

    public void testPrintHelpVariants() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "all", false, "do all things");
        options.addOption("b", "brief", true, "be brief");

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printHelp(pw, 80, "testApp", "Header text", options, 2, 4, "Footer text", true);
        pw.flush();
        String result = out.toString();
        assertTrue(result.length() > 0);

        StringWriter out2 = new StringWriter();
        PrintWriter pw2 = new PrintWriter(out2);
        formatter.printHelp(pw2, 80, "testApp", "Header text", options, 2, 4, "Footer text", false);
        pw2.flush();
        assertTrue(out2.toString().length() > 0);

        formatter.printHelp("testApp", options, true);
        formatter.printHelp("testApp", options, false);
        formatter.printHelp("testApp", "Header", options, "Footer");
        formatter.printHelp("testApp", "Header", options, "Footer", true);
        formatter.printHelp(80, "testApp", "Header", options, "Footer");
        formatter.printHelp(80, "testApp", "Header", options, "Footer", true);
    }

    public void testPrintUsageWithOptionsAndGroups() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        
        Option optA = new Option("a", "alpha", false, "alpha option");
        optA.setRequired(true);
        options.addOption(optA);

        Option optB = new Option(null, "beta", true, "beta option");
        options.addOption(optB);

        OptionGroup group = new OptionGroup();
        Option optC = new Option("c", "charles", false, "charles");
        Option optD = new Option("d", "david", false, "david");
        group.addOption(optC);
        group.addOption(optD);
        group.setRequired(false);
        options.addOptionGroup(group);

        OptionGroup reqGroup = new OptionGroup();
        Option optE = new Option("e", "edward", false, "edward");
        Option optF = new Option("f", "frank", false, "frank");
        reqGroup.addOption(optE);
        reqGroup.addOption(optF);
        reqGroup.setRequired(true);
        options.addOptionGroup(reqGroup);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printUsage(pw, 40, "myapp", options);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testPrintWrapped() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printWrapped(pw, 20, 5, "This is a very long text that needs to be wrapped properly across multiple lines.");
        formatter.printWrapped(pw, 10, "Short");
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    def TestRenderWrappedTextEdgeCases() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        
        StringBuffer sb = new StringBuffer();
        formatter.renderWrappedText(sb, 10, 2, "Short");
        assertTrue(sb.length() > 0);

        StringBuffer sb2 = new StringBuffer();
        formatter.renderWrappedText(sb2, 5, 2, "Line with \n newline and \t tab characters embedded.");
        assertTrue(sb2.length() > 0);
    }

    public void testFindWrapPosEdges() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        
        String text = "Line1\nLine2\tTabbed line with some more text to wrap around.";
        int pos1 = formatter.findWrapPos(text, 10, 0);
        assertTrue(pos1 != 0);

        int pos2 = formatter.findWrapPos("NoWhitespaceHereAtAllLongWord", 5, 0);
        assertTrue(pos2 != 0);

        int pos3 = formatter.findWrapPos("Short", 50, 0);
        assertEquals(-1, pos3);
    }

    public void testRtrim() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        
        assertNull(formatter.rtrim(null));
        assertEquals("", formatter.rtrim(""));
        assertEquals("abc", formatter.rtrim("abc   "));
        assertEquals("abc", formatter.rtrim("abc"));
    }

    public void testCreatePadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String padding = formatter.createPadding(4);
        assertEquals("    ", padding);
    }

    public void testOptionComparator() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        Option o1 = new Option("z", "zeta", false, "zeta");
        Option o2 = new Option("a", "alpha", false, "alpha");
        
        options.addOption(o1);
        options.addOption(o2);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printOptions(pw, 80, options, 2, 4);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }
}