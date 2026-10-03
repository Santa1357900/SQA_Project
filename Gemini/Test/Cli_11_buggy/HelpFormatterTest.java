package org.apache.commons.cli;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

public class HelpFormatterTest extends TestCase {

    public void testConstantsAndDefaults() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_WIDTH, formatter.getWidth());
        assertEquals(HelpFormatter.DEFAULT_LEFT_PAD, formatter.getLeftPadding());
        assertEquals(HelpFormatter.DEFAULT_DESC_PAD, formatter.getDescPadding());
        assertEquals(HelpFormatter.DEFAULT_SYNTAX_PREFIX, formatter.getSyntaxPrefix());
        assertEquals(HelpFormatter.DEFAULT_OPT_PREFIX, formatter.getOptPrefix());
        assertEquals(HelpFormatter.DEFAULT_LONG_OPT_PREFIX, formatter.getLongOptPrefix());
        assertEquals(HelpFormatter.DEFAULT_ARG_NAME, formatter.getArgName());
        assertNotNull(formatter.getNewLine());
        assertNotNull(formatter.getOptionComparator());
    }

    public void testSettersAndGetters() throws Throwable {
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

    public void testPrintHelpNullSyntax() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        try {
            formatter.printHelp((String) null, options);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cmdLineSyntax not provided"));
        }

        try {
            formatter.printHelp("", options);
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

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printHelp(pw, 80, "testapp", "Header text", options, 2, 4, "Footer text", true);
        pw.flush();

        String output = sw.toString();
        assertTrue(output.length() > 0);
        assertTrue(output.indexOf("Header text") != -1);
        assertTrue(output.indexOf("Footer text") != -1);
    }

    public void testPrintHelpWithoutAutoUsage() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha description");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printHelp(pw, 80, "testapp arg1", "Header", options, 2, 4, "Footer", false);
        pw.flush();

        String output = sw.toString();
        assertTrue(output.indexOf("testapp") != -1);
    }

    public void testPrintHelpOverloads() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha description");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);

        formatter.printHelp("syntax", options);
        formatter.printHelp("syntax", options, true);
        formatter.printHelp("syntax", "header", options, "footer");
        formatter.printHelp("syntax", "header", options, "footer", true);
        formatter.printHelp(80, "syntax", "header", options, "footer");
        formatter.printHelp(80, "syntax", "header", options, "footer", true);
        formatter.printHelp(pw, 80, "syntax", "header", options, 1, 3, "footer");
    }

    public void testPrintUsageVariants() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("x", "xopt", false, "x desc"));
        group.addOption(new Option("y", "yopt", false, "y desc"));
        options.addOptionGroup(group);

        Option reqOpt = new Option("r", "req", true, "req desc");
        reqOpt.setRequired(true);
        options.addOption(reqOpt);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        assertTrue(sw.toString().length() > 0);

        StringWriter sw2 = new StringWriter();
        PrintWriter pw2 = new PrintWriter(sw2);
        formatter.printUsage(pw2, 80, "myapp arg1 arg2");
        pw2.flush();
        assertTrue(sw2.toString().length() > 0);
    }

    public void testPrintOptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha description");
        options.addOption(null, "longonly", true, "long only desc");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printOptions(pw, 80, options, 2, 4);
        pw.flush();
        assertTrue(sw.toString().length() > 0);
    }

    public void testPrintWrapped() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printWrapped(pw, 40, "This is a very long text that needs to be wrapped properly across multiple lines.");
        formatter.printWrapped(pw, 40, 5, "This is another very long text with next line tab stop to be wrapped properly.");
        pw.flush();
        assertTrue(sw.toString().length() > 0);
    }

    public void testFindWrapPos() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "Line one\nLine two\twith tab and some very long text that exceeds limit.";
        int pos1 = formatter.findWrapPos(text, 15, 0);
        assertTrue(pos1 >= -1);

        int pos2 = formatter.findWrapPos("Short", 10, 0);
        assertEquals(-1, pos2);

        int pos3 = formatter.findWrapPos("NoSpaceHereButLongEnoughToBeWrappedWithoutWhitespace", 10, 0);
        assertTrue(pos3 != -1);
    }

    public void testCreatePadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String padding = formatter.createPadding(5);
        assertEquals("     ", padding);

        String emptyPadding = formatter.createPadding(0);
        assertEquals("", emptyPadding);
    }

    public void testRtrim() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertNull(formatter.rtrim(null));
        assertEquals("", formatter.rtrim(""));
        assertEquals("abc", formatter.rtrim("abc   "));
        assertEquals("abc", formatter.rtrim("abc\t\n"));
    }

    public void testOptionComparator() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Comparator comp = formatter.getOptionComparator();
        Option o1 = new Option("b", "beta", false, "beta");
        Option o2 = new Option("a", "alpha", false, "alpha");
        int result = comp.compare(o1, o2);
        assertTrue(result > 0);
    }
}