package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

public class HelpFormatterClaudeTest
{
    // getWidth default value
    @Test
    public void testGetWidth_default_returnsDefaultWidth() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_WIDTH, hf.getWidth());
    }

    // setWidth/getWidth round trip
    @Test
    public void testSetWidth_getWidth_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setWidth(100);
        assertEquals(100, hf.getWidth());
    }

    // getLeftPadding default value
    @Test
    public void testGetLeftPadding_default_returnsDefaultLeftPad() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_LEFT_PAD, hf.getLeftPadding());
    }

    // setLeftPadding/getLeftPadding round trip
    @Test
    public void testSetLeftPadding_getLeftPadding_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setLeftPadding(5);
        assertEquals(5, hf.getLeftPadding());
    }

    // getDescPadding default value
    @Test
    public void testGetDescPadding_default_returnsDefaultDescPad() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_DESC_PAD, hf.getDescPadding());
    }

    // setDescPadding/getDescPadding round trip
    @Test
    public void testSetDescPadding_getDescPadding_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setDescPadding(7);
        assertEquals(7, hf.getDescPadding());
    }

    // getSyntaxPrefix default value
    @Test
    public void testGetSyntaxPrefix_default_returnsDefaultPrefix() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_SYNTAX_PREFIX, hf.getSyntaxPrefix());
    }

    // setSyntaxPrefix/getSyntaxPrefix round trip
    @Test
    public void testSetSyntaxPrefix_getSyntaxPrefix_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setSyntaxPrefix("call: ");
        assertEquals("call: ", hf.getSyntaxPrefix());
    }

    // getNewLine default value equals system line separator
    @Test
    public void testGetNewLine_default_returnsSystemLineSeparator() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(System.getProperty("line.separator"), hf.getNewLine());
    }

    // setNewLine/getNewLine round trip
    @Test
    public void testSetNewLine_getNewLine_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setNewLine("\r\n");
        assertEquals("\r\n", hf.getNewLine());
    }

    // getOptPrefix default value
    @Test
    public void testGetOptPrefix_default_returnsDefaultOptPrefix() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_OPT_PREFIX, hf.getOptPrefix());
    }

    // setOptPrefix/getOptPrefix round trip
    @Test
    public void testSetOptPrefix_getOptPrefix_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setOptPrefix("/");
        assertEquals("/", hf.getOptPrefix());
    }

    // getLongOptPrefix default value
    @Test
    public void testGetLongOptPrefix_default_returnsDefaultLongOptPrefix() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_LONG_OPT_PREFIX, hf.getLongOptPrefix());
    }

    // setLongOptPrefix/getLongOptPrefix round trip
    @Test
    public void testSetLongOptPrefix_getLongOptPrefix_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setLongOptPrefix("==");
        assertEquals("==", hf.getLongOptPrefix());
    }

    // getArgName default value
    @Test
    public void testGetArgName_default_returnsDefaultArgName() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_ARG_NAME, hf.getArgName());
    }

    // setArgName/getArgName round trip
    @Test
    public void testSetArgName_getArgName_returnsSetValue() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setArgName("value");
        assertEquals("value", hf.getArgName());
    }

    // getOptionComparator default not null
    @Test
    public void testGetOptionComparator_default_isNotNull() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertNotNull(hf.getOptionComparator());
    }

    // setOptionComparator(null) resets to default case-insensitive sorting
    @Test
    public void testSetOptionComparator_null_resetsToDefaultSorting() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        hf.setOptionComparator(null);

        Options options = new Options();
        options.addOption("b", false, "desc b");
        options.addOption("a", false, "desc a");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();

        String result = sw.toString();
        assertTrue(result.indexOf("-a") < result.indexOf("-b"));
    }

    // setOptionComparator(custom) is applied when sorting options
    @Test
    public void testSetOptionComparator_custom_appliedInPrintUsage() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Comparator reverseComparator = new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                Option opt1 = (Option) o1;
                Option opt2 = (Option) o2;
                return opt2.getKey().compareTo(opt1.getKey());
            }
        };
        hf.setOptionComparator(reverseComparator);
        assertSame(reverseComparator, hf.getOptionComparator());

        Options options = new Options();
        options.addOption("a", false, "desc a");
        options.addOption("b", false, "desc b");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();

        String result = sw.toString();
        assertTrue(result.indexOf("-b") < result.indexOf("-a"));
    }

    // printHelp full signature: null cmdLineSyntax throws IllegalArgumentException
    @Test
    public void testPrintHelp_nullCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        try
        {
            hf.printHelp(pw, 80, null, null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // printHelp full signature: empty cmdLineSyntax throws IllegalArgumentException
    @Test
    public void testPrintHelp_emptyCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Options options = new Options();
        try
        {
            hf.printHelp(pw, 80, "", null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // printUsage: single required option without arg
    @Test
    public void testPrintUsage_singleRequiredOption_noBrackets() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Options options = new Options();
        Option a = new Option("a", false, "desc a");
        a.setRequired(true);
        options.addOption(a);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "myapp", options);
        pw.flush();

        assertEquals("usage: myapp -a", sw.toString().trim());
    }

    // printUsage: required OptionGroup renders without surrounding brackets
    @Test
    public void testPrintUsage_requiredOptionGroup_noBrackets() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "desc a"));
        group.addOption(new Option("b", false, "desc b"));
        group.setRequired(true);
        options.addOptionGroup(group);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();

        assertEquals("usage: app -a | -b", sw.toString().trim());
    }

    // printUsage: not-required OptionGroup renders with surrounding brackets
    @Test
    public void testPrintUsage_notRequiredOptionGroup_hasBrackets() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "desc a"));
        group.addOption(new Option("b", false, "desc b"));
        group.setRequired(false);
        options.addOptionGroup(group);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();

        assertEquals("usage: app [-a | -b]", sw.toString().trim());
    }

    // printUsage: option with arg and argName shows <argName>, not required -> brackets
    @Test
    public void testPrintUsage_optionWithArgName_showsAngleBrackets() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Options options = new Options();
        Option f = new Option("f", true, "file desc");
        f.setArgName("file");
        options.addOption(f);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();

        assertEquals("usage: app [-f <file>]", sw.toString().trim());
    }

    // printUsage(pw,width,cmdLineSyntax): no options, simple syntax line
    @Test
    public void testPrintUsage_cmdLineSyntaxOnly_prependsSyntaxPrefix() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printUsage(pw, 80, "git commit");
        pw.flush();

        assertEquals("usage: git commit", sw.toString().trim());
    }

    // printOptions: empty Options produces empty content
    @Test
    public void testPrintOptions_emptyOptions_producesEmptyOutput() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Options options = new Options();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printOptions(pw, 80, options, 1, 3);
        pw.flush();

        assertEquals("", sw.toString().trim());
    }

    // printOptions: single option contains opt and description
    @Test
    public void testPrintOptions_singleOption_containsOptAndDescription() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Options options = new Options();
        options.addOption("v", false, "verbose mode");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printOptions(pw, 80, options, 1, 3);
        pw.flush();

        String result = sw.toString();
        assertTrue(result.indexOf("-v") >= 0);
        assertTrue(result.indexOf("verbose mode") >= 0);
    }

    // renderWrappedText: text containing newline wraps at newline
    @Test
    public void testRenderWrappedText_withNewline_wrapsAtNewline() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        StringBuffer sb = new StringBuffer();
        hf.renderWrappedText(sb, 80, 0, "line1\nline2");

        String result = sb.toString();
        assertTrue(result.indexOf("line1") >= 0);
        assertTrue(result.indexOf("line2") >= 0);
        assertTrue(result.indexOf(hf.getNewLine()) >= 0);
    }

    // renderWrappedText: nextLineTabStop >= width throws IllegalStateException
    @Test
    public void testRenderWrappedText_tabStopGreaterOrEqualWidth_throwsIllegalStateException() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        StringBuffer sb = new StringBuffer();
        String text = "this is a long text that will not fit and need wrapping more content here";
        try
        {
            hf.renderWrappedText(sb, 10, 10, text);
            fail("expected IllegalStateException");
        }
        catch (IllegalStateException expected)
        {
        }
    }

    // findWrapPos: text fits entirely within width, returns -1
    @Test
    public void testFindWrapPos_textFitsWithinWidth_returnsMinusOne() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        int pos = hf.findWrapPos("short text", 80, 0);
        assertEquals(-1, pos);
    }

    // findWrapPos: newline found within width returns index+1
    @Test
    public void testFindWrapPos_newlineWithinWidth_returnsIndexPlusOne() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        int pos = hf.findWrapPos("abc\ndef", 10, 0);
        assertEquals(4, pos);
    }

    // findWrapPos: tab found within width returns index+1
    @Test
    public void testFindWrapPos_tabWithinWidth_returnsIndexPlusOne() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        int pos = hf.findWrapPos("abc\tdef", 10, 0);
        assertEquals(4, pos);
    }

    // findWrapPos: whitespace before startPos+width returns that whitespace index
    @Test
    public void testFindWrapPos_whitespaceBeforeWidth_returnsWhitespaceIndex() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        int pos = hf.findWrapPos("hello world", 8, 0);
        assertEquals(5, pos);
    }

    // BUG TEST: findWrapPos on text with no whitespace beyond width must return -1
    // per Javadoc ("-1 if the wrap position is at the end of the text"), not throw.
    @Test
    public void testFindWrapPos_noWhitespaceBeyondWidth_returnsMinusOne() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        String text = "aaaaaaaaaaaaaaaaaaaa";
        int pos = hf.findWrapPos(text, 10, 0);
        assertEquals(-1, pos);
    }

    // createPadding: returns string of spaces of requested length
    @Test
    public void testCreatePadding_returnsCorrectLengthOfSpaces() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        String padding = hf.createPadding(5);
        assertEquals(5, padding.length());
        assertEquals("     ", padding);
    }

    // rtrim: removes trailing whitespace only
    @Test
    public void testRtrim_trailingWhitespace_removed() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        String result = hf.rtrim(" abc  ");
        assertEquals(" abc", result);
    }

    // rtrim: null input returns null
    @Test
    public void testRtrim_nullInput_returnsNull() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertNull(hf.rtrim(null));
    }

    // rtrim: empty string returns empty string
    @Test
    public void testRtrim_emptyInput_returnsEmpty() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        assertEquals("", hf.rtrim(""));
    }

    // printHelp full signature: header/footer/autoUsage all present in output
    @Test
    public void testPrintHelp_fullSignature_containsHeaderFooterAndUsage() throws Throwable {
        HelpFormatter hf = new HelpFormatter();
        Options options = new Options();
        options.addOption("x", false, "option x desc");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        hf.printHelp(pw, 80, "myapp", "HEADERTEXT", options, 1, 3, "FOOTERTEXT", true);
        pw.flush();

        String result = sw.toString();
        assertTrue(result.indexOf("usage: myapp") >= 0);
        assertTrue(result.indexOf("HEADERTEXT") >= 0);
        assertTrue(result.indexOf("-x") >= 0);
        assertTrue(result.indexOf("option x desc") >= 0);
        assertTrue(result.indexOf("FOOTERTEXT") >= 0);
    }
}
