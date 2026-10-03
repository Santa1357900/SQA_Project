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

    public void testPrintHelpExceptionsAndEdgeCases() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        
        boolean thrown = false;
        try {
            formatter.printHelp("", options);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            formatter.printHelp(null, options);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        formatter.printHelp(pw, 80, "test", "header", options, 1, 3, "footer", true);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testPrintHelpWithOptionsAndGroups() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        
        Option optA = new Option("a", "alpha", true, "Alpha option");
        optA.setRequired(true);
        options.addOption(optA);

        Option optB = new Option("b", "beta", false, "Beta option");
        options.addOption(optB);

        Option optNoOpt = new Option(null, "longonly", true, "Long only option");
        options.addOption(optNoOpt);

        Option optBlankArg = new Option("c", "charles", true, "Blank arg name");
        optBlankArg.setArgName("");
        options.addOption(optBlankArg);

        OptionGroup group = new OptionGroup();
        Option optX = new Option("x", "xray", false, "Xray");
        Option optY = new Option("y", "ypsilon", false, "Ypsilon");
        group.addOption(optX);
        group.addOption(optY);
        group.setRequired(false);
        options.addOptionGroup(group);

        OptionGroup reqGroup = new OptionGroup();
        Option optR1 = new Option("r", "req1", false, "Req 1");
        Option optR2 = new Option("s", "req2", false, "Req 2");
        reqGroup.addOption(optR1);
        reqGroup.addOption(optR2);
        reqGroup.setRequired(true);
        options.addOptionGroup(reqGroup);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printHelp(pw, 40, "myapp", "Header text here", options, 2, 2, "Footer text here", true);
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testRenderWrappedTextEdgeCases() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        formatter.printWrapped(pw, 10, 15, "Short");
        
        formatter.printWrapped(pw, 5, 6, "A very long text that needs wrapping across multiple lines without spaces");

        formatter.printWrapped(pw, 10, 0, "Line1\nLine2\tTabbed");
        pw.flush();
        assertTrue(out.toString().length() > 0);
    }

    public void testFindWrapPos() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        
        class TestableHelpFormatter extends HelpFormatter {
            public int callFindWrapPos(String text, int width, int startPos) {
                return findWrapPos(text, width, startPos);
            }
            public String callRtrim(String s) {
                return rtrim(s);
            }
            public String callCreatePadding(int len) {
                return createPadding(len);
            }
        }

        TestableHelpFormatter tf = new TestableHelpFormatter();
        
        int pos = tf.callFindWrapPos("abc def ghi", 5, 0);
        assertTrue(pos != -1);

        int posNewline = tf.callFindWrapPos("abc\ndef", 10, 0);
        assertTrue(posNewline > 0);

        int posNoWrap = tf.callFindWrapPos("short", 10, 0);
        assertEquals(-1, posNoWrap);

        assertEquals("abc", tf.callRtrim("abc   "));
        assertNull(tf.callRtrim(null));
        assertEquals("", tf.callRtrim(""));

        assertEquals("   ", tf.callCreatePadding(3));
    }
}