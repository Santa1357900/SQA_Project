package org.apache.commons.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

import org.junit.Test;
import static org.junit.Assert.*;

public class HelpFormatterClaudeTest
{
    // Covers getWidth/setWidth - default value and custom assignment, no branching involved
    @Test
    public void testWidthGetterSetter_defaultAndCustomValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_WIDTH, formatter.getWidth());
        formatter.setWidth(100);
        assertEquals(100, formatter.getWidth());
    }

    // Covers getLeftPadding/setLeftPadding
    @Test
    public void testLeftPaddingGetterSetter_defaultAndCustomValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_LEFT_PAD, formatter.getLeftPadding());
        formatter.setLeftPadding(5);
        assertEquals(5, formatter.getLeftPadding());
    }

    // Covers getDescPadding/setDescPadding
    @Test
    public void testDescPaddingGetterSetter_defaultAndCustomValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_DESC_PAD, formatter.getDescPadding());
        formatter.setDescPadding(7);
        assertEquals(7, formatter.getDescPadding());
    }

    // Covers getSyntaxPrefix/setSyntaxPrefix
    @Test
    public void testSyntaxPrefixGetterSetter_defaultAndCustomValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_SYNTAX_PREFIX, formatter.getSyntaxPrefix());
        formatter.setSyntaxPrefix("call: ");
        assertEquals("call: ", formatter.getSyntaxPrefix());
    }

    // Covers setNewLine/getNewLine with a custom value
    @Test
    public void testNewLineSetter_customValue_getNewLineReturnsSameValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setNewLine("\r\n");
        assertEquals("\r\n", formatter.getNewLine());
    }

    // Covers getOptPrefix/setOptPrefix
    @Test
    public void testOptPrefixGetterSetter_defaultAndCustomValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_OPT_PREFIX, formatter.getOptPrefix());
        formatter.setOptPrefix("/");
        assertEquals("/", formatter.getOptPrefix());
    }

    // Covers getLongOptPrefix/setLongOptPrefix
    @Test
    public void testLongOptPrefixGetterSetter_defaultAndCustomValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_LONG_OPT_PREFIX, formatter.getLongOptPrefix());
        formatter.setLongOptPrefix("==");
        assertEquals("==", formatter.getLongOptPrefix());
    }

    // Covers getArgName/setArgName
    @Test
    public void testArgNameGetterSetter_defaultAndCustomValue() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals(HelpFormatter.DEFAULT_ARG_NAME, formatter.getArgName());
        formatter.setArgName("file");
        assertEquals("file", formatter.getArgName());
    }

    // Covers getOptionComparator default non-null value
    @Test
    public void testGetOptionComparator_default_notNull() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertNotNull(formatter.getOptionComparator());
    }

    // Covers setOptionComparator(null) branch which resets to default comparator
    @Test
    public void testSetOptionComparator_nullArgument_resetsToDefaultComparator() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null);
        assertNotNull(formatter.getOptionComparator());
    }

    // Covers setOptionComparator(non-null) branch and that the custom comparator is actually used for sorting
    @Test
    public void testSetOptionComparator_customComparator_usedToSortOptions() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Comparator reverseComparator = new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                Option opt1 = (Option) o1;
                Option opt2 = (Option) o2;
                return opt2.getKey().compareToIgnoreCase(opt1.getKey());
            }
        };
        formatter.setOptionComparator(reverseComparator);

        Options options = new Options();
        options.addOption("a", false, "desc a");
        options.addOption("b", false, "desc b");

        StringBuffer sb = new StringBuffer();
        formatter.renderOptions(sb, 80, options, 1, 3);
        String result = sb.toString();
        assertTrue(result.indexOf("-b") < result.indexOf("-a"));
    }

    // Covers printHelp full overload throwing IllegalArgumentException when cmdLineSyntax is null
    @Test
    public void testPrintHelp_nullCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try
        {
            formatter.printHelp(pw, 80, null, null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // Covers printHelp full overload throwing IllegalArgumentException when cmdLineSyntax is empty
    @Test
    public void testPrintHelp_emptyCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try
        {
            formatter.printHelp(pw, 80, "", null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    // Covers printHelp branches for non-empty header and footer, plus printOptions call, with autoUsage=false
    @Test
    public void testPrintHelp_withHeaderAndFooter_outputContainsAllParts() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("a", false, "option a description");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printHelp(pw, 80, "myapp", "header text", options, 1, 3, "footer text", false);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("myapp") != -1);
        assertTrue(output.indexOf("header text") != -1);
        assertTrue(output.indexOf("footer text") != -1);
        assertTrue(output.indexOf("-a") != -1);
    }

    // Covers printHelp autoUsage=true branch which triggers printUsage(pw,width,syntax,options)
    @Test
    public void testPrintHelp_autoUsageTrue_usageLineIncludesOption() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("x", false, "desc x");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printHelp(pw, 80, "myapp", null, options, 1, 3, null, true);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("[-x]") != -1);
    }

    // Covers the two-arg printHelp(String,Options) convenience overload chain, writing to System.out
    @Test
    public void testPrintHelp_twoArgOverload_writesToSystemOut() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("v", false, "verbose");
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        String output;
        try
        {
            formatter.printHelp("myapp", options);
        }
        finally
        {
            System.setOut(originalOut);
        }
        output = baos.toString();
        assertTrue(output.indexOf("myapp") != -1);
        assertTrue(output.indexOf("-v") != -1);
    }

    // Covers printUsage(pw,width,cmdLineSyntax) two-arg text overload with no options
    @Test
    public void testPrintUsage_noOptions_containsSyntaxPrefixAndApp() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printUsage(pw, 80, "myapp");
        pw.flush();
        assertEquals("usage: myapp", sw.toString().trim());
    }

    // Covers printUsage(pw,width,app,options) with a single non-required option having an explicit arg name
    @Test
    public void testPrintUsage_withOptions_containsOptionSyntax() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        Option opt = new Option("f", true, "file desc");
        opt.setArgName("file");
        options.addOption(opt);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("[-f <file>]") != -1);
    }

    // Covers appendOption required branch - no square brackets around a required option
    @Test
    public void testPrintUsage_requiredOption_noBrackets() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        Option opt = new Option("r", false, "required desc");
        opt.setRequired(true);
        options.addOption(opt);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("-r") != -1);
        assertTrue(output.indexOf("[-r]") == -1);
    }

    // Covers appendOptionGroup required branch - group options joined without outer brackets
    @Test
    public void testPrintUsage_requiredOptionGroup_noBracketsAroundGroup() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "a desc"));
        group.addOption(new Option("b", false, "b desc"));
        group.setRequired(true);
        options.addOptionGroup(group);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("-a | -b") != -1);
    }

    // Covers appendOptionGroup non-required branch - group wrapped in square brackets
    @Test
    public void testPrintUsage_nonRequiredOptionGroup_bracketedGroup() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "a desc"));
        group.addOption(new Option("b", false, "b desc"));
        options.addOptionGroup(group);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("[-a | -b]") != -1);
    }

    // Covers printOptions - renders option prefix and description text
    @Test
    public void testPrintOptions_singleOption_containsDescription() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("h", false, "print help message");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printOptions(pw, 80, options, 1, 3);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf("-h") != -1);
        assertTrue(output.indexOf("print help message") != -1);
    }

    // Covers printWrapped where findWrapPos returns -1 (text fits on one line)
    @Test
    public void testPrintWrapped_shortText_singleLineOutput() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        formatter.printWrapped(pw, 80, "short text");
        pw.flush();
        assertEquals("short text", sw.toString().trim());
    }

    // Covers printWrapped where text must be wrapped across multiple lines
    @Test
    public void testPrintWrapped_longText_wrapsIntoMultipleLines() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String text = "aaaaaaaaaa bbbbbbbbbb cccccccccc dddddddddd";
        formatter.printWrapped(pw, 20, text);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.indexOf(formatter.getNewLine()) != -1);
    }



    // Covers renderWrappedText when text contains an embedded newline character
    @Test
    public void testRenderWrappedText_textContainsNewline_splitsAtNewline() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        StringBuffer sb = new StringBuffer();
        String text = "line1\nline2";
        formatter.renderWrappedText(sb, 80, 0, text);
        String result = sb.toString();
        assertTrue(result.indexOf("line1") != -1);
        assertTrue(result.indexOf("line2") != -1);
    }

    // Covers findWrapPos newline branch with startPos=0 - returns position right after the newline
    @Test
    public void testFindWrapPos_newlineWithinWidth_returnsPosPlusOne() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "abc\ndef";
        int pos = formatter.findWrapPos(text, 10, 0);
        assertEquals(4, pos);
    }



    // Covers findWrapPos tab branch with startPos=0
    @Test
    public void testFindWrapPos_tabWithinWidth_returnsPosPlusOne() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "abc\tdef";
        int pos = formatter.findWrapPos(text, 10, 0);
        assertEquals(4, pos);
    }

    // Covers findWrapPos branch where the whole remaining text fits within width, returns -1
    @Test
    public void testFindWrapPos_textFitsWithinWidth_returnsMinusOne() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "short";
        int pos = formatter.findWrapPos(text, 20, 0);
        assertEquals(-1, pos);
    }

    // Covers findWrapPos backward whitespace scan finding whitespace before startPos+width
    @Test
    public void testFindWrapPos_whitespaceBeforeWidth_wrapsAtWhitespace() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "aaaaa bbbbbbbbbbbbbbbbbbbb";
        int pos = formatter.findWrapPos(text, 10, 0);
        assertEquals(5, pos);
    }

    // Covers findWrapPos forward whitespace scan when no whitespace exists before startPos+width
    @Test
    public void testFindWrapPos_noWhitespaceBeforeWidth_wrapsAtFirstWhitespaceAfter() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String text = "aaaaaaaaaaaa bbbb";
        int pos = formatter.findWrapPos(text, 5, 0);
        assertEquals(12, pos);
    }

    // Covers createPadding with zero length
    @Test
    public void testCreatePadding_zeroLength_returnsEmptyString() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String padding = formatter.createPadding(0);
        assertEquals("", padding);
    }

    // Covers createPadding with a positive length
    @Test
    public void testCreatePadding_positiveLength_returnsSpacesOfGivenLength() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        String padding = formatter.createPadding(4);
        assertEquals("    ", padding);
    }

    // Covers rtrim null-guard branch
    @Test
    public void testRtrim_nullInput_returnsNull() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertNull(formatter.rtrim(null));
    }

    // Covers rtrim empty-string guard branch
    @Test
    public void testRtrim_emptyString_returnsEmptyString() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals("", formatter.rtrim(""));
    }

    // Covers rtrim removing trailing whitespace characters
    @Test
    public void testRtrim_trailingWhitespace_removesTrailingSpaces() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals("hello", formatter.rtrim("hello   "));
    }

    // Covers rtrim when there is no trailing whitespace to remove
    @Test
    public void testRtrim_noTrailingWhitespace_returnsUnchanged() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        assertEquals("hello", formatter.rtrim("hello"));
    }

    // Covers renderOptions default comparator sorting multiple options alphabetically
    @Test
    public void testRenderOptions_multipleOptions_sortedAlphabetically() throws Throwable {
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();
        options.addOption("b", false, "desc b");
        options.addOption("a", false, "desc a");
        StringBuffer sb = new StringBuffer();
        formatter.renderOptions(sb, 80, options, 1, 3);
        String result = sb.toString();
        int idxA = result.indexOf("-a");
        int idxB = result.indexOf("-b");
        assertTrue(idxA < idxB);
    }
}
