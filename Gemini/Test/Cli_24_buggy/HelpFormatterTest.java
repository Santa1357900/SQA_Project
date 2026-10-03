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

        formatter.setArgName("parameter");
        assertEquals("parameter", formatter.getArgName());

        assertNotNull(formatter.getOptionComparator());
        formatter.setOptionComparator(null);
        assertNotNull(formatter.getOptionComparator());

        Comparator<Object> customComparator = new Comparator<Object>() {
            public int compare(Object o1, Object o2) {
                return 0;
            }
        };
        formatter.setOptionComparator(customComparator);
        assertEquals(customComparator, formatter.getOptionComparator());
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

    public void testPrintHelpWithOptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", "all", false, "do all things");
        options.addOption("b", "bval", true, "block size");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printHelp(pw, 80, "testapp", "header text", options, 2, 4, "footer text", true);
        pw.flush();

        String output = sw.toString();
        assertTrue(output.length() > 0);
        assertTrue(output.indexOf("header text") != -1);
        assertTrue(output.indexOf("footer text") != -1);
    }

    public void testPrintHelpWithoutAutoUsage() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("c", "count", true, "count items");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printHelp(pw, 80, "testapp", "header", options, 1, 3, "footer", false);
        pw.flush();

        String output = sw.toString();
        assertTrue(output.indexOf("header") != -1);
    }

    public void testOptionGroupUsage() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();

        OptionGroup group = new OptionGroup();
        group.addOption(new Option("x", "xval", false, "x option"));
        group.addOption(new Option("y", "yval", false, "y option"));
        group.setRequired(true);
        options.addOptionGroup(group);

        OptionGroup optionalGroup = new OptionGroup();
        optionalGroup.addOption(new Option("m", "mval", false, "m option"));
        optionalGroup.addOption(new Option("n", "nval", false, "n option"));
        optionalGroup.setRequired(false);
        options.addOptionGroup(optionalGroup);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printHelp(pw, 80, "appname", null, options, 1, 3, null, true);
        pw.flush();

        assertTrue(sw.toString().indexOf("-x") != -1);
    }

    public void testPrintWrappedWithIllegalState() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        try {
            formatter.printWrapped(pw, 10, 15, "This is a very long text that exceeds tab stop and width constraints.");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Total width is less than"));
        }
    }

    public void testFindWrapPosAndRtrim() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        
        String text = "Line one\nLine two\twith tab";
        int pos = formatter.findWrapPos(text, 15, 0);
        assertTrue(pos != -2);

        String trimmed = formatter.rtrim("   some text   ");
        assertEquals("   some text", trimmed);

        assertNull(formatter.rtrim(null));
        assertEquals("", formatter.rtrim(""));
    }

    public void testCreatePadding() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String padding = formatter.createPadding(4);
        assertEquals("    ", padding);
    }

    public void testRenderOptionsEdgeCases() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        
        Option optLongOnly = new Option(null, "longonly", false, "has only long opt");
        options.addOption(optLongOnly);

        Option optWithArgName = new Option("f", "file", true, "input file");
        optWithArgName.setArgName("file");
        options.addOption(optWithArgName);

        Option optWithoutArgName = new Option("p", "port", true, "port number");
        options.addOption(optWithoutArgName);

        StringBuffer sb = new StringBuffer();
        formatter.renderOptions(sb, 80, options, 2, 4);
        assertTrue(sb.length() > 0);
    }

    public void testPrintUsageSimple() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printUsage(pw, 80, "syntax string");
        pw.flush();
        assertTrue(sw.toString().length() > 0);
    }

    public void testPrintOptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("z", "zero", false, "zero option");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        formatter.printOptions(pw, 80, options, 2, 2);
        pw.flush();
        assertTrue(sw.toString().indexOf("-z") != -1);
    }

    public void testFindWrapPosEndOfText() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        int pos = formatter.findWrapPos("Short", 10, 0);
        assertEquals(-1, pos);
    }
}