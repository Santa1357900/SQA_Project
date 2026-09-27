package org.apache.commons.cli;

import junit.framework.TestCase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.List;

public class HelpFormatterTest extends TestCase {

    public void testGetSetWidth() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_WIDTH, formatter.getWidth());
        formatter.setWidth(100);
        assertEquals(100, formatter.getWidth());
    }

    public void testGetSetLeftPadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_LEFT_PAD, formatter.getLeftPadding());
        formatter.setLeftPadding(5);
        assertEquals(5, formatter.getLeftPadding());
    }

    public void testGetSetDescPadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_DESC_PAD, formatter.getDescPadding());
        formatter.setDescPadding(10);
        assertEquals(10, formatter.getDescPadding());
    }

    public void testGetSetSyntaxPrefix() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_SYNTAX_PREFIX, formatter.getSyntaxPrefix());
        formatter.setSyntaxPrefix("syntax: ");
        assertEquals("syntax: ", formatter.getSyntaxPrefix());
    }

    public void testGetSetNewLine() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(System.getProperty("line.separator"), formatter.getNewLine());
        formatter.setNewLine("\n");
        assertEquals("\n", formatter.getNewLine());
    }

    public void testGetSetOptPrefix() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_OPT_PREFIX, formatter.getOptPrefix());
        formatter.setOptPrefix("/");
        assertEquals("/", formatter.getOptPrefix());
    }

    public void testGetSetLongOptPrefix() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_LONG_OPT_PREFIX, formatter.getLongOptPrefix());
        formatter.setLongOptPrefix("/");
        assertEquals("/", formatter.getLongOptPrefix());
    }

    public void testGetSetArgName() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_ARG_NAME, formatter.getArgName());
        formatter.setArgName("parameter");
        assertEquals("parameter", formatter.getArgName());
    }

    public void testGetSetOptionComparator() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertNotNull(formatter.getOptionComparator());

        Comparator<Object> customComparator = new Comparator<Object>() {
            public int compare(Object o1, Object o2) {
                return 0;
            }
        };

        formatter.setOptionComparator(customComparator);
        assertEquals(customComparator, formatter.getOptionComparator());

        formatter.setOptionComparator(null);
        assertNotNull(formatter.getOptionComparator());
    }

    public void testPrintHelpNullSyntax() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        Options options = new Options();

        try {
            formatter.printHelp(pw, 80, null, "header", options, 1, 3, "footer", false);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cmdLineSyntax not provided"));
        }

        try {
            formatter.printHelp(pw, 80, "", "header", options, 1, 3, "footer", false);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cmdLineSyntax not provided"));
        }
    }

    public void testPrintHelpWithOptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        Options options = new Options();
        options.addOption("a", "alpha", false, "alpha option");
        options.addOption("b", "beta", true, "beta option");

        formatter.printHelp(pw, 80, "testApp", "Header text", options, 2, 4, "Footer text", true);
        pw.flush();
        String result = out.toString();

        assertTrue(result.length() > 0);
    }

    public void testPrintHelpWithoutAutoUsage() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        Options options = new Options();
        formatter.printHelp(pw, 80, "testApp", "Header", options, 1, 3, "Footer", false);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testPrintHelpSimpleOverloads() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("x", "x-opt", false, "x option");

        // Just invoke to ensure no exceptions
        formatter.printHelp("syntax", options);
        formatter.printHelp("syntax", options, true);
        formatter.printHelp("syntax", "header", options, "footer");
        formatter.printHelp("syntax", "header", options, "footer", true);
        formatter.printHelp(80, "syntax", "header", options, "footer");
        formatter.printHelp(80, "syntax", "header", options, "footer", true);
    }

    public void testPrintOptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        Options options = new Options();
        options.addOption("a", "long-a", false, "desc a");
        options.addOption(null, "long-b", true, "desc b");

        formatter.printOptions(pw, 80, options, 2, 4);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testPrintWrapped() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printWrapped(pw, 40, "This is a very long text that should definitely be wrapped across multiple lines properly.");
        formatter.printWrapped(pw, 40, 5, "This is another very long text with next line tab stop applied.");
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testRenderWrappedTextInfiniteLoopProtection() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringBuffer sb = new StringBuffer();
        
        try {
            // Width too small for a word should trigger RuntimeException [CLI-162]
            formatter.renderWrappedText(sb, 2, 0, "A_Very_Long_Word_Without_Spaces");
            fail("RuntimeException expected to prevent infinite loop");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Text too long for line"));
        }
    }

    public void testFindWrapPos() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "Line one\nLine two\tLine three with some more text to wrap around.";

        int pos1 = formatter.findWrapPos(text, 10, 0);
        assertTrue(pos1 != 0);

        int pos2 = formatter.findWrapPos("Short", 10, 0);
        assertEquals(-1, pos2);

        int pos3 = formatter.findWrapPos("A B C", 1, 0);
        assertTrue(pos3 >= 0);
    }

    public void testCreatePadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String padding = formatter.createPadding(5);
        assertEquals("     ", padding);
    }

    public void testRtrim() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertNull(formatter.rtrim(null));
        assertEquals("", formatter.rtrim(""));
        assertEquals("abc", formatter.rtrim("abc   "));
        assertEquals("abc", formatter.rtrim("abc"));
    }

    public void testOptionGroupInUsage() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        Options options = new Options();
        OptionGroup group = new OptionGroup();
        Option opt1 = new Option("1", "one", false, "one");
        Option opt2 = new Option("2", "two", false, "two");
        group.addOption(opt1);
        group.addOption(opt2);
        group.setRequired(true);
        options.addOptionGroup(group);

        Option reqOpt = new Option("r", "req", false, "required");
        reqOpt.setRequired(true);
        options.addOption(reqOpt);

        formatter.printHelp(pw, 80, "app", "header", options, 1, 3, "footer", true);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }
}