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

        formatter.setLongOptPrefix("---");
        assertEquals("---", formatter.getLongOptPrefix());

        formatter.setLongOptSeparator("=");
        assertEquals("=", formatter.getLongOptSeparator());

        formatter.setArgName("parameter");
        assertEquals("parameter", formatter.getArgName());

        Comparator comp = new Comparator() {
            public int compare(Object o1, Object o2) {
                return 0;
            }
        };
        formatter.setOptionComparator(comp);
        assertEquals(comp, formatter.getOptionComparator());

        formatter.setOptionComparator(null);
        assertNotNull(formatter.getOptionComparator());
    }

    public void testPrintHelpExceptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        try {
            formatter.printHelp(pw, 80, null, "header", options, 1, 3, "footer", false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cmdLineSyntax not provided"));
        }

        try {
            formatter.printHelp(pw, 80, "", "header", options, 1, 3, "footer", false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cmdLineSyntax not provided"));
        }
    }

    public void testPrintHelpBasic() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha description");
        options.addOption("b", "beta", true, "beta description");

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printHelp(pw, 80, "app", "Header text", options, 2, 4, "Footer text", true);
        pw.flush();
        String output = out.toString();

        assertTrue(output.contains("usage: app"));
        assertTrue(output.contains("Header text"));
        assertTrue(output.contains("-a,--alpha"));
        assertTrue(output.contains("Footer text"));
    }

    public void testPrintHelpWithoutAutoUsage() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", false, "option a");

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printHelp(pw, 80, "app [options]", null, options, 1, 2, null, false);
        pw.flush();
        String output = out.toString();

        assertTrue(output.contains("usage: app [options]"));
    }

    public void testPrintHelpSimpleSignatures() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        
        // Just verify these methods execute without throwing unexpected exceptions
        formatter.printHelp("syntax", options);
        formatter.printHelp("syntax", options, true);
        formatter.printHelp("syntax", "header", options, "footer");
        formatter.printHelp("syntax", "header", options, "footer", true);
        formatter.printHelp(80, "syntax", "header", options, "footer");
    }

    public void testPrintOptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("x", "longx", true, "description x");
        options.addOption(null, "longonly", false, "description long only");

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printOptions(pw, 80, options, 2, 4);
        pw.flush();
        String output = out.toString();

        assertTrue(output.contains("-x,--longx"));
        assertTrue(output.contains("--longonly"));
    }

    public void testPrintWrapped() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printWrapped(pw, 20, "This is a long text that needs wrapping properly.");
        formatter.printWrapped(pw, 20, 5, "This is another long text with tab stop.");
        pw.flush();
        
        assertTrue(out.toString().length() > 0);
    }

    public void testFindWrapPos() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "Line one\nLine two with a very long sentence that wraps.";
        
        int pos1 = formatter.findWrapPos(text, 10, 0);
        assertTrue(pos1 != -1);

        int pos2 = formatter.findWrapPos("Short", 10, 0);
        assertEquals(-1, pos2);

        int pos3 = formatter.findWrapPos("Line\tTab", 10, 0);
        assertTrue(pos3 != -1);
    }

    public void testCreatePadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String padding = formatter.createPadding(5);
        assertEquals(5, padding.length());
        assertEquals("     ", padding);
    }

    public void testRtrim() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertNull(formatter.rtrim(null));
        assertEquals("", formatter.rtrim(""));
        assertEquals("abc", formatter.rtrim("abc   "));
        assertEquals("abc", formatter.rtrim("abc"));
    }

    public void testOptionGroupsAndRequired() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();

        Option opt1 = new Option("1", "one", false, "one");
        Option opt2 = new Option("2", "two", false, "two");
        OptionGroup group = new OptionGroup();
        group.addOption(opt1);
        group.addOption(opt2);
        group.setRequired(true);
        options.addOptionGroup(group);

        Option reqOpt = new Option("r", "req", true, "required opt");
        reqOpt.setRequired(true);
        options.addOption(reqOpt);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printUsage(pw, 80, "app", options);
        pw.flush();

        assertTrue(out.toString().contains("-1 | -2"));
        assertTrue(out.toString().contains("-r"));
    }

    public void testRenderWrappedTextEdgeCases() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringBuffer sb = new StringBuffer();
        
        // nextLineTabStop >= width condition
        formatter.renderWrappedText(sb, 10, 15, "A very long text to force wrapping with large tab stop");
        assertTrue(sb.length() > 0);
    }

    public void testRenderOptionsBlankArgName() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        Option opt = new Option("b", "blank", true, "blank arg");
        opt.setArgName("");
        options.addOption(opt);

        StringBuffer sb = new StringBuffer();
        formatter.renderOptions(sb, 80, options, 2, 4);
        assertTrue(sb.toString().contains("-b,--blank"));
    }
}