package org.apache.commons.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HelpFormatterClaudeTest
{
    private HelpFormatter hf;

    @Before
    public void setUp() throws Throwable
    {
        hf = new HelpFormatter();
    }

    // getWidth() returns default width constant
    @Test
    public void testGetWidth_default_returnsDefaultWidth() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_WIDTH, hf.getWidth());
    }

    // setWidth()/getWidth() round trip
    @Test
    public void testSetWidth_getWidth_returnsSetValue() throws Throwable {
        hf.setWidth(100);
        assertEquals(100, hf.getWidth());
    }

    // getLeftPadding() returns default constant
    @Test
    public void testGetLeftPadding_default_returnsDefaultLeftPad() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_LEFT_PAD, hf.getLeftPadding());
    }

    // setLeftPadding()/getLeftPadding() round trip
    @Test
    public void testSetLeftPadding_getLeftPadding_returnsSetValue() throws Throwable {
        hf.setLeftPadding(5);
        assertEquals(5, hf.getLeftPadding());
    }

    // getDescPadding() returns default constant
    @Test
    public void testGetDescPadding_default_returnsDefaultDescPad() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_DESC_PAD, hf.getDescPadding());
    }

    // setDescPadding()/getDescPadding() round trip
    @Test
    public void testSetDescPadding_getDescPadding_returnsSetValue() throws Throwable {
        hf.setDescPadding(7);
        assertEquals(7, hf.getDescPadding());
    }

    // getSyntaxPrefix() returns default "usage: "
    @Test
    public void testGetSyntaxPrefix_default_returnsUsagePrefix() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_SYNTAX_PREFIX, hf.getSyntaxPrefix());
    }

    // setSyntaxPrefix()/getSyntaxPrefix() round trip
    @Test
    public void testSetSyntaxPrefix_getSyntaxPrefix_returnsSetValue() throws Throwable {
        hf.setSyntaxPrefix("call: ");
        assertEquals("call: ", hf.getSyntaxPrefix());
    }

    // getNewLine() returns system line separator by default
    @Test
    public void testGetNewLine_default_matchesSystemLineSeparator() throws Throwable {
        assertEquals(System.getProperty("line.separator"), hf.getNewLine());
    }

    // setNewLine()/getNewLine() round trip
    @Test
    public void testSetNewLine_getNewLine_returnsSetValue() throws Throwable {
        hf.setNewLine("\n");
        assertEquals("\n", hf.getNewLine());
    }

    // getOptPrefix() returns default "-"
    @Test
    public void testGetOptPrefix_default_returnsDash() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_OPT_PREFIX, hf.getOptPrefix());
    }

    // setOptPrefix()/getOptPrefix() round trip
    @Test
    public void testSetOptPrefix_getOptPrefix_returnsSetValue() throws Throwable {
        hf.setOptPrefix("+");
        assertEquals("+", hf.getOptPrefix());
    }

    // getLongOptPrefix() returns default "--"
    @Test
    public void testGetLongOptPrefix_default_returnsDoubleDash() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_LONG_OPT_PREFIX, hf.getLongOptPrefix());
    }

    // setLongOptPrefix()/getLongOptPrefix() round trip
    @Test
    public void testSetLongOptPrefix_getLongOptPrefix_returnsSetValue() throws Throwable {
        hf.setLongOptPrefix("==");
        assertEquals("==", hf.getLongOptPrefix());
    }

    // getArgName() returns default "arg"
    @Test
    public void testGetArgName_default_returnsArg() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_ARG_NAME, hf.getArgName());
    }

    // setArgName()/getArgName() round trip
    @Test
    public void testSetArgName_getArgName_returnsSetValue() throws Throwable {
        hf.setArgName("value");
        assertEquals("value", hf.getArgName());
    }

    // default comparator is not null and sorts case-insensitively by key
    @Test
    public void testGetOptionComparator_default_sortsCaseInsensitive() throws Throwable {
        Comparator c = hf.getOptionComparator();
        assertNotNull(c);
        Option a = new Option("a", false, "desc a");
        Option b = new Option("b", false, "desc b");
        assertTrue(c.compare(a, b) < 0);
    }

    // setOptionComparator(null) resets to default case-insensitive ordering
    @Test
    public void testSetOptionComparator_null_resetsToDefaultOrdering() throws Throwable {
        Options opts = new Options();
        opts.addOption(new Option("B", false, "desc B"));
        opts.addOption(new Option("a", false, "desc a"));
        hf.setOptionComparator(null);
        StringBuffer sb = new StringBuffer();
        hf.renderOptions(sb, 80, opts, 1, 3);
        String out = sb.toString();
        assertTrue(out.indexOf("-a") < out.indexOf("-B"));
    }

    // setOptionComparator(custom) stores exact instance
    @Test
    public void testSetOptionComparator_custom_returnsSameInstance() throws Throwable {
        Comparator custom = new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                return 0;
            }
        };
        hf.setOptionComparator(custom);
        assertSame(custom, hf.getOptionComparator());
    }

    // printHelp(PrintWriter...) with null cmdLineSyntax throws IllegalArgumentException
    @Test
    public void testPrintHelpPW_nullCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        PrintWriter pw = new PrintWriter(new StringWriter());
        Options opts = new Options();
        try
        {
            hf.printHelp(pw, 80, null, null, opts, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // printHelp(PrintWriter...) with empty cmdLineSyntax throws IllegalArgumentException
    @Test
    public void testPrintHelpPW_emptyCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        PrintWriter pw = new PrintWriter(new StringWriter());
        Options opts = new Options();
        try
        {
            hf.printHelp(pw, 80, "", null, opts, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // printHelp(PrintWriter...) with header/footer prints all sections
    @Test
    public void testPrintHelpPW_withHeaderFooter_producesFullOutput() throws Throwable {
        Options opts = new Options();
        opts.addOption(new Option("a", false, "option a description"));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printHelp(pw, 80, "myapp", "header text", opts, 1, 3, "footer text", false);
        pw.flush();
        String out = sw.toString();
        assertTrue(out.indexOf("usage: myapp") >= 0);
        assertTrue(out.indexOf("header text") >= 0);
        assertTrue(out.indexOf("-a") >= 0);
        assertTrue(out.indexOf("option a description") >= 0);
        assertTrue(out.indexOf("footer text") >= 0);
    }

    // printHelp(PrintWriter..., autoUsage=true) includes generated option syntax
    @Test
    public void testPrintHelpPW_autoUsageTrue_includesOptionSyntax() throws Throwable {
        Options opts = new Options();
        opts.addOption(new Option("a", false, "option a description"));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printHelp(pw, 80, "myapp", null, opts, 1, 3, null, true);
        pw.flush();
        String out = sw.toString();
        assertTrue(out.indexOf("[-a]") >= 0);
    }

    // printUsage(pw,width,app,options) wraps optional option in brackets
    @Test
    public void testPrintUsage_optionalOption_bracketed() throws Throwable {
        Options opts = new Options();
        opts.addOption(new Option("x", false, "desc"));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", opts);
        pw.flush();
        assertTrue(sw.toString().indexOf("[-x]") >= 0);
    }

    // printUsage(pw,width,app,options) does not bracket required option
    @Test
    public void testPrintUsage_requiredOption_noBrackets() throws Throwable {
        Options opts = new Options();
        Option y = new Option("y", false, "desc");
        y.setRequired(true);
        opts.addOption(y);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", opts);
        pw.flush();
        String out = sw.toString();
        assertTrue(out.indexOf("-y") >= 0);
        assertFalse(out.indexOf("[-y]") >= 0);
    }

    // printUsage(pw,width,app,options) shows arg name placeholder for options with arg
    @Test
    public void testPrintUsage_optionWithArgName_includesArgPlaceholder() throws Throwable {
        Options opts = new Options();
        Option f = new Option("f", true, "file option");
        f.setArgName("file");
        opts.addOption(f);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", opts);
        pw.flush();
        assertTrue(sw.toString().indexOf("<file>") >= 0);
    }

    // printUsage with required OptionGroup does not add outer brackets
    @Test
    public void testPrintUsage_optionGroupRequired_noOuterBrackets() throws Throwable {
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("p", false, "desc p"));
        group.addOption(new Option("q", false, "desc q"));
        group.setRequired(true);
        Options opts = new Options();
        opts.addOptionGroup(group);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", opts);
        pw.flush();
        String out = sw.toString();
        assertTrue(out.indexOf("-p | -q") >= 0);
        assertFalse(out.indexOf("[-p | -q]") >= 0);
    }

    // printUsage with optional OptionGroup adds outer brackets
    @Test
    public void testPrintUsage_optionGroupOptional_bracketed() throws Throwable {
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("p", false, "desc p"));
        group.addOption(new Option("q", false, "desc q"));
        group.setRequired(false);
        Options opts = new Options();
        opts.addOptionGroup(group);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", opts);
        pw.flush();
        assertTrue(sw.toString().indexOf("[-p | -q]") >= 0);
    }

    // printUsage(pw,width,cmdLineSyntax) with no space just appends prefix
    @Test
    public void testPrintUsageSimple_noSpaceInSyntax_prependsPrefixOnly() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "myapp");
        pw.flush();
        assertEquals("usage: myapp", sw.toString().trim());
    }

    // printOptions() with empty Options prints only an empty line
    @Test
    public void testPrintOptions_emptyOptions_printsEmptyLine() throws Throwable {
        Options opts = new Options();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printOptions(pw, 80, opts, 1, 3);
        pw.flush();
        assertEquals("", sw.toString().trim());
    }

    // printOptions() with options including a long opt renders both and descriptions
    @Test
    public void testPrintOptions_withOptions_containsDescriptions() throws Throwable {
        Options opts = new Options();
        opts.addOption(new Option("a", false, "short option"));
        Option c = new Option("c", false, "desc c");
        c.setLongOpt("cee");
        opts.addOption(c);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printOptions(pw, 80, opts, 1, 3);
        pw.flush();
        String out = sw.toString();
        assertTrue(out.indexOf("-a") >= 0);
        assertTrue(out.indexOf("short option") >= 0);
        assertTrue(out.indexOf("-c,--cee") >= 0);
    }

    // printWrapped() with short text leaves it unchanged (only rtrimmed)
    @Test
    public void testPrintWrapped_shortText_unchanged() throws Throwable {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printWrapped(pw, 80, "short text");
        pw.flush();
        assertEquals("short text", sw.toString().trim());
    }

    // printWrapped() with long text wraps within the given width
    @Test
    public void testPrintWrapped_longText_wrapsWithinWidth() throws Throwable {
        hf.setNewLine("\n");
        String text = "1234567890 1234567890 1234567890";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printWrapped(pw, 10, text);
        pw.flush();
        String out = sw.toString();
        int idx = out.indexOf("\n");
        assertTrue(idx > 0 && idx <= 10);
        assertTrue(out.length() > idx + 1);
    }

    // renderWrappedText() indents continuation lines with nextLineTabStop spaces
    @Test
    public void testRenderWrappedText_withTabStop_indentsSubsequentLines() throws Throwable {
        hf.setNewLine("\n");
        String text = "aaaa bbbb cccc dddd eeee";
        StringBuffer sb = new StringBuffer();
        hf.renderWrappedText(sb, 10, 4, text);
        String[] lines = sb.toString().split("\n");
        assertTrue(lines.length > 1);
        assertTrue(lines[1].startsWith("    "));
    }

    // findWrapPos: newline within startPos+width window must be honored (regression for off-by-startPos bug)
    @Test
    public void testFindWrapPos_newlineWithinStartPosWindow_returnsPosAfterNewline() throws Throwable {
        String text = "0123456789\nabcdefghijklmnopqrstuvwxyz";
        int result = hf.findWrapPos(text, 5, 5);
        assertEquals(11, result);
    }

    // findWrapPos: tab character found returns position right after the tab
    @Test
    public void testFindWrapPos_tabCharacter_returnsPosPlusOne() throws Throwable {
        String text = "abc\tdefghij";
        int result = hf.findWrapPos(text, 10, 0);
        assertEquals(4, result);
    }

    // findWrapPos: text shorter than width returns -1 (no wrap needed)
    @Test
    public void testFindWrapPos_shortText_returnsMinusOne() throws Throwable {
        int result = hf.findWrapPos("short", 10, 0);
        assertEquals(-1, result);
    }

    // findWrapPos: finds last whitespace before startPos+width
    @Test
    public void testFindWrapPos_whitespaceBeforeWidth_returnsPos() throws Throwable {
        String text = "aaaaa bbbbbbbbbb";
        int result = hf.findWrapPos(text, 10, 0);
        assertEquals(5, result);
    }

    // findWrapPos: no whitespace backward, falls forward to next whitespace
    @Test
    public void testFindWrapPos_noBackwardWhitespace_searchesForward() throws Throwable {
        String text = "aaaaaaaaaaaaaaaa ";
        int result = hf.findWrapPos(text, 5, 0);
        assertEquals(16, result);
    }

    // createPadding(0) returns empty string
    @Test
    public void testCreatePadding_zeroLength_emptyString() throws Throwable {
        assertEquals("", hf.createPadding(0));
    }

    // createPadding(n) returns n spaces
    @Test
    public void testCreatePadding_positiveLength_correctSpaces() throws Throwable {
        String pad = hf.createPadding(5);
        assertEquals(5, pad.length());
        assertEquals("     ", pad);
    }

    // rtrim(null) returns null
    @Test
    public void testRtrim_nullInput_returnsNull() throws Throwable {
        assertNull(hf.rtrim(null));
    }

    // rtrim("") returns empty string
    @Test
    public void testRtrim_emptyString_returnsEmpty() throws Throwable {
        assertEquals("", hf.rtrim(""));
    }

    // rtrim trims trailing whitespace only
    @Test
    public void testRtrim_trailingWhitespace_trimmed() throws Throwable {
        assertEquals("abc", hf.rtrim("abc   "));
    }

    // rtrim leaves string without trailing whitespace unchanged
    @Test
    public void testRtrim_noTrailingWhitespace_unchanged() throws Throwable {
        assertEquals("abc", hf.rtrim("abc"));
    }

    // renderOptions() sorts options by key using the option comparator
    @Test
    public void testRenderOptions_sortedByKey() throws Throwable {
        Options opts = new Options();
        opts.addOption(new Option("b", false, "desc b"));
        opts.addOption(new Option("a", false, "desc a"));
        StringBuffer sb = new StringBuffer();
        hf.renderOptions(sb, 80, opts, 1, 3);
        String out = sb.toString();
        assertTrue(out.indexOf("-a") < out.indexOf("-b"));
    }
}
