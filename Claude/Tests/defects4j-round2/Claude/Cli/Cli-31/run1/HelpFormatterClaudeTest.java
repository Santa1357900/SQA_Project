package org.apache.commons.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HelpFormatterClaudeTest
{
    private HelpFormatter formatter;
    private String nl;

    @Before
    public void setUp() throws Throwable
    {
        formatter = new HelpFormatter();
        nl = System.getProperty("line.separator");
    }

    private int countOccurrences(String haystack, String needle)
    {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1)
        {
            count++;
            idx += needle.length();
        }
        return count;
    }

    // getWidth/setWidth round trip
    @Test
    public void testGetSetWidth_roundTrip() throws Throwable {
        formatter.setWidth(100);
        assertEquals(100, formatter.getWidth());
    }

    // getLeftPadding/setLeftPadding round trip
    @Test
    public void testGetSetLeftPadding_roundTrip() throws Throwable {
        formatter.setLeftPadding(5);
        assertEquals(5, formatter.getLeftPadding());
    }

    // getDescPadding/setDescPadding round trip
    @Test
    public void testGetSetDescPadding_roundTrip() throws Throwable {
        formatter.setDescPadding(7);
        assertEquals(7, formatter.getDescPadding());
    }

    // getSyntaxPrefix/setSyntaxPrefix round trip
    @Test
    public void testGetSetSyntaxPrefix_roundTrip() throws Throwable {
        formatter.setSyntaxPrefix("call: ");
        assertEquals("call: ", formatter.getSyntaxPrefix());
    }

    // getNewLine/setNewLine round trip
    @Test
    public void testGetSetNewLine_roundTrip() throws Throwable {
        formatter.setNewLine("\n");
        assertEquals("\n", formatter.getNewLine());
    }

    // getOptPrefix/setOptPrefix round trip
    @Test
    public void testGetSetOptPrefix_roundTrip() throws Throwable {
        formatter.setOptPrefix("+");
        assertEquals("+", formatter.getOptPrefix());
    }

    // getLongOptPrefix/setLongOptPrefix round trip
    @Test
    public void testGetSetLongOptPrefix_roundTrip() throws Throwable {
        formatter.setLongOptPrefix("++");
        assertEquals("++", formatter.getLongOptPrefix());
    }

    // getLongOptSeparator/setLongOptSeparator round trip
    @Test
    public void testGetSetLongOptSeparator_roundTrip() throws Throwable {
        formatter.setLongOptSeparator("=");
        assertEquals("=", formatter.getLongOptSeparator());
    }

    // getArgName/setArgName round trip
    @Test
    public void testGetSetArgName_roundTrip() throws Throwable {
        formatter.setArgName("VALUE");
        assertEquals("VALUE", formatter.getArgName());
    }

    // default option comparator sorts case-insensitively by key
    @Test
    public void testGetOptionComparator_defaultCaseInsensitive() throws Throwable {
        Comparator comparator = formatter.getOptionComparator();
        Option optA = new Option("a", false, "desc a");
        Option optB = new Option("B", false, "desc b");
        assertTrue(comparator.compare(optA, optB) < 0);
    }

    // setOptionComparator with custom comparator is stored and retrievable
    @Test
    public void testSetOptionComparator_custom_isStored() throws Throwable {
        Comparator custom = new ReverseOptComparator();
        formatter.setOptionComparator(custom);
        assertSame(custom, formatter.getOptionComparator());
    }

    // setOptionComparator(null) resets to a default comparator instance
    @Test
    public void testSetOptionComparator_null_resetsToDefault() throws Throwable {
        Comparator custom = new ReverseOptComparator();
        formatter.setOptionComparator(custom);
        formatter.setOptionComparator(null);
        assertNotSame(custom, formatter.getOptionComparator());
    }

    // printHelp(PrintWriter,...) throws IllegalArgumentException for null cmdLineSyntax
    @Test
    public void testPrintHelp_nullCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        PrintWriter pw = new PrintWriter(new StringWriter());
        Options options = new Options();
        try
        {
            formatter.printHelp(pw, 80, null, null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // printHelp(PrintWriter,...) throws IllegalArgumentException for empty cmdLineSyntax
    @Test
    public void testPrintHelp_emptyCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        PrintWriter pw = new PrintWriter(new StringWriter());
        Options options = new Options();
        try
        {
            formatter.printHelp(pw, 80, "", null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // printHelp with header and footer prints both plus usage and options
    @Test
    public void testPrintHelp_withHeaderAndFooter_containsAllSections() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        options.addOption("a", false, "option a");
        formatter.printHelp(pw, 80, "app", "HEADER", options, 1, 3, "FOOTER", false);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("usage: app") >= 0);
        assertTrue(output.indexOf("HEADER") >= 0);
        assertTrue(output.indexOf("FOOTER") >= 0);
        assertTrue(output.indexOf("-a") >= 0);
    }

    // printHelp with blank header/footer skips printing them (only usage+options lines)
    @Test
    public void testPrintHelp_blankHeaderFooter_onlyTwoLinesPrinted() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        options.addOption("a", false, "option a");
        formatter.printHelp(pw, 80, "app", "   ", options, 1, 3, "  ", false);
        pw.flush();
        assertEquals(2, countOccurrences(sw.toString(), nl));
    }

    // printHelp with autoUsage=true builds usage clause from Options
    @Test
    public void testPrintHelp_autoUsageTrue_buildsUsageFromOptions() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        options.addOption("a", false, "option a");
        formatter.printHelp(pw, 80, "app", null, options, 1, 3, null, true);
        pw.flush();
        assertTrue(sw.toString().indexOf("usage: app [-a]") >= 0);
    }

    // two-arg printHelp(String,Options) writes usage and options to System.out
    @Test
    public void testPrintHelp_twoArgOverload_writesToSystemOut() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "option a");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try
        {
            formatter.printHelp("app", options);
        }
        finally
        {
            System.setOut(original);
        }
        String output = baos.toString();
        assertTrue(output.indexOf("usage: app") >= 0);
        assertTrue(output.indexOf("-a") >= 0);
    }

    // printUsage with no options produces just the syntax prefix and app name
    @Test
    public void testPrintUsage_noOptions_printsSyntaxOnly() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app" + nl, sw.toString());
    }

    // printUsage with a non-required option with explicit arg name wraps it in brackets
    @Test
    public void testPrintUsage_nonRequiredOptionWithArgName_bracketed() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        Option opt = new Option("a", true, "desc");
        opt.setArgName("file");
        options.addOption(opt);
        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app [-a <file>]" + nl, sw.toString());
    }

    // printUsage with a required option omits the surrounding brackets
    @Test
    public void testPrintUsage_requiredOption_noBrackets() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        Option opt = new Option("a", true, "desc");
        opt.setArgName("file");
        opt.setRequired(true);
        options.addOption(opt);
        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app -a <file>" + nl, sw.toString());
    }

    // printUsage with a required OptionGroup shows alternatives without outer brackets
    @Test
    public void testPrintUsage_requiredOptionGroup_noOuterBrackets() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "desc a"));
        group.addOption(new Option("b", false, "desc b"));
        group.setRequired(true);
        options.addOptionGroup(group);
        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app -a | -b" + nl, sw.toString());
    }

    // printUsage with a non-required OptionGroup wraps alternatives in brackets
    @Test
    public void testPrintUsage_nonRequiredOptionGroup_bracketed() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "desc a"));
        group.addOption(new Option("b", false, "desc b"));
        options.addOptionGroup(group);
        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app [-a | -b]" + nl, sw.toString());
    }

    // printUsage(pw,width,cmdLineSyntax) with no space in syntax uses tab stop 0
    @Test
    public void testPrintUsage_noSpaceInSyntax_printsAsIs() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printUsage(pw, 80, "cmd");
        pw.flush();
        assertEquals("usage: cmd" + nl, sw.toString());
    }

    // printOptions renders a single option line with padding and description
    @Test
    public void testPrintOptions_singleOption_rendersLine() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        options.addOption("a", false, "test desc");
        formatter.printOptions(pw, 80, options, 1, 3);
        pw.flush();
        assertEquals(" -a   test desc" + nl, sw.toString());
    }

    // printOptions renders multiple options sorted alphabetically
    @Test
    public void testPrintOptions_multipleOptions_sortedAlphabetically() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        options.addOption("b", false, "bbb");
        options.addOption("a", false, "aaa");
        formatter.printOptions(pw, 80, options, 1, 3);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("-a") < output.indexOf("-b"));
    }

    // renderOptions with blank arg name appends a single space, no angle brackets
    @Test
    public void testRenderOptions_blankArgName_noAngleBrackets() throws Throwable {
        Options options = new Options();
        Option opt = new Option("a", true, "desc");
        opt.setArgName("");
        options.addOption(opt);
        StringBuffer sb = new StringBuffer();
        formatter.renderOptions(sb, 80, options, 1, 3);
        assertTrue(sb.toString().indexOf("<") == -1);
    }

    // renderOptions with a long opt appends the comma-separated long form
    @Test
    public void testRenderOptions_withLongOpt_includesLongForm() throws Throwable {
        Options options = new Options();
        Option opt = new Option("a", false, "desc");
        opt.setLongOpt("alpha");
        options.addOption(opt);
        StringBuffer sb = new StringBuffer();
        formatter.renderOptions(sb, 80, options, 1, 3);
        assertTrue(sb.toString().indexOf("--alpha") >= 0);
    }

    // renderWrappedText returns text unchanged (rtrimmed) when shorter than width
    @Test
    public void testRenderWrappedText_shortText_returnsUnchanged() throws Throwable {
        StringBuffer sb = new StringBuffer();
        formatter.renderWrappedText(sb, 80, 0, "short text");
        assertEquals("short text", sb.toString());
    }

    // renderWrappedText wraps long text over multiple lines
    @Test
    public void testRenderWrappedText_longText_producesMultipleLines() throws Throwable {
        StringBuffer sb = new StringBuffer();
        String text = "one two three four five six seven eight nine ten";
        formatter.renderWrappedText(sb, 10, 0, text);
        assertTrue(countOccurrences(sb.toString(), nl) >= 2);
    }

    // renderWrappedText with nextLineTabStop >= width triggers the infinite-loop guard
    @Test(timeout = 5000)
    public void testRenderWrappedText_tabStopExceedsWidth_terminates() throws Throwable {
        StringBuffer sb = new StringBuffer();
        formatter.renderWrappedText(sb, 5, 10, "abcdef ghijk lmnop");
        assertTrue(sb.length() > 0);
    }

    // findWrapPos returns position right after a newline when within width
    @Test
    public void testFindWrapPos_newlineWithinWidth_returnsAfterNewline() throws Throwable {
        int pos = formatter.findWrapPos("ab\ncd", 10, 0);
        assertEquals(3, pos);
    }

    // findWrapPos returns position right after a tab when within width
    @Test
    public void testFindWrapPos_tabWithinWidth_returnsAfterTab() throws Throwable {
        int pos = formatter.findWrapPos("ab\tcd", 10, 0);
        assertEquals(3, pos);
    }

    // findWrapPos returns -1 when text fits entirely within width
    @Test
    public void testFindWrapPos_textFitsWidth_returnsMinusOne() throws Throwable {
        int pos = formatter.findWrapPos("short", 80, 0);
        assertEquals(-1, pos);
    }

    // findWrapPos finds the last whitespace at/before startPos+width
    @Test
    public void testFindWrapPos_whitespaceAtBoundary_returnsThatPosition() throws Throwable {
        int pos = formatter.findWrapPos("aaaaa bbbbb", 5, 0);
        assertEquals(5, pos);
    }

    // Bug hunt: no whitespace anywhere after startPos+width must yield -1 (wrap at text end), not throw
    @Test
    public void testFindWrapPos_noWhitespaceAtAll_returnsMinusOne() throws Throwable {
        int pos = formatter.findWrapPos("aaaaaa", 3, 0);
        assertEquals(-1, pos);
    }

    // Bug hunt via public API: printWrapped must not throw for text without any whitespace
    @Test
    public void testPrintWrapped_noWhitespaceLongerThanWidth_doesNotThrow() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printWrapped(pw, 3, "aaaaaa");
        pw.flush();
        assertEquals("aaaaaa" + nl, sw.toString());
    }

    // printWrapped(pw,width,text) prints short text unchanged plus newline
    @Test
    public void testPrintWrapped_shortText_printsUnchanged() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printWrapped(pw, 80, "hello world");
        pw.flush();
        assertEquals("hello world" + nl, sw.toString());
    }

    // printWrapped(pw,width,tabStop,text) with tab stop but short text ignores tab stop
    @Test
    public void testPrintWrapped_withTabStop_shortTextIgnoresTabStop() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printWrapped(pw, 80, 5, "hello world");
        pw.flush();
        assertEquals("hello world" + nl, sw.toString());
    }

    // createPadding(0) returns empty string
    @Test
    public void testCreatePadding_zero_returnsEmptyString() throws Throwable {
        assertEquals("", formatter.createPadding(0));
    }

    // createPadding(n) returns n spaces
    @Test
    public void testCreatePadding_positive_returnsSpaces() throws Throwable {
        assertEquals("     ", formatter.createPadding(5));
    }

    // rtrim(null) returns null
    @Test
    public void testRtrim_null_returnsNull() throws Throwable {
        assertNull(formatter.rtrim(null));
    }

    // rtrim("") returns ""
    @Test
    public void testRtrim_empty_returnsEmpty() throws Throwable {
        assertEquals("", formatter.rtrim(""));
    }

    // rtrim removes only trailing whitespace, keeps leading/inner content
    @Test
    public void testRtrim_trailingWhitespace_removed() throws Throwable {
        assertEquals("  abc", formatter.rtrim("  abc   "));
    }

    // helper comparator used only to exercise setOptionComparator branch
    private static class ReverseOptComparator implements Comparator
    {
        public int compare(Object o1, Object o2)
        {
            Option opt1 = (Option) o1;
            Option opt2 = (Option) o2;
            return opt2.getOpt().compareTo(opt1.getOpt());
        }
    }
}
