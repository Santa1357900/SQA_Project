package org.apache.commons.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HelpFormatterClaudeTest
{
    private HelpFormatter formatter;
    private StringWriter sw;
    private PrintWriter pw;
    private static final String NL = System.getProperty("line.separator");

    @Before
    public void setUp() throws Throwable
    {
        formatter = new HelpFormatter();
        sw = new StringWriter();
        pw = new PrintWriter(sw);
    }

    // ---- constants ----

    // covers the documented default constant values
    @Test
    public void testDefaultConstants_values_matchSpecification() throws Throwable {
        assertEquals(74, HelpFormatter.DEFAULT_WIDTH);
        assertEquals(1, HelpFormatter.DEFAULT_LEFT_PAD);
        assertEquals(3, HelpFormatter.DEFAULT_DESC_PAD);
        assertEquals("usage: ", HelpFormatter.DEFAULT_SYNTAX_PREFIX);
        assertEquals("-", HelpFormatter.DEFAULT_OPT_PREFIX);
        assertEquals("--", HelpFormatter.DEFAULT_LONG_OPT_PREFIX);
        assertEquals(" ", HelpFormatter.DEFAULT_LONG_OPT_SEPARATOR);
        assertEquals("arg", HelpFormatter.DEFAULT_ARG_NAME);
    }

    // ---- getters/setters ----

    // covers setWidth/getWidth pass-through
    @Test
    public void testSetWidth_customValue_getWidthReturnsIt() throws Throwable {
        formatter.setWidth(100);
        assertEquals(100, formatter.getWidth());
    }

    // covers setLeftPadding/getLeftPadding pass-through
    @Test
    public void testSetLeftPadding_customValue_getLeftPaddingReturnsIt() throws Throwable {
        formatter.setLeftPadding(5);
        assertEquals(5, formatter.getLeftPadding());
    }

    // covers setDescPadding/getDescPadding pass-through
    @Test
    public void testSetDescPadding_customValue_getDescPaddingReturnsIt() throws Throwable {
        formatter.setDescPadding(7);
        assertEquals(7, formatter.getDescPadding());
    }

    // covers setSyntaxPrefix/getSyntaxPrefix pass-through
    @Test
    public void testSetSyntaxPrefix_customValue_getSyntaxPrefixReturnsIt() throws Throwable {
        formatter.setSyntaxPrefix("call: ");
        assertEquals("call: ", formatter.getSyntaxPrefix());
    }

    // covers setNewLine/getNewLine pass-through
    @Test
    public void testSetNewLine_customValue_getNewLineReturnsIt() throws Throwable {
        formatter.setNewLine("\n");
        assertEquals("\n", formatter.getNewLine());
    }

    // covers setOptPrefix/getOptPrefix pass-through
    @Test
    public void testSetOptPrefix_customValue_getOptPrefixReturnsIt() throws Throwable {
        formatter.setOptPrefix("/");
        assertEquals("/", formatter.getOptPrefix());
    }

    // covers setLongOptPrefix/getLongOptPrefix pass-through
    @Test
    public void testSetLongOptPrefix_customValue_getLongOptPrefixReturnsIt() throws Throwable {
        formatter.setLongOptPrefix("==");
        assertEquals("==", formatter.getLongOptPrefix());
    }

    // covers setLongOptSeparator/getLongOptSeparator pass-through
    @Test
    public void testSetLongOptSeparator_customValue_getLongOptSeparatorReturnsIt() throws Throwable {
        formatter.setLongOptSeparator("=");
        assertEquals("=", formatter.getLongOptSeparator());
    }

    // covers setArgName/getArgName pass-through
    @Test
    public void testSetArgName_customValue_getArgNameReturnsIt() throws Throwable {
        formatter.setArgName("VALUE");
        assertEquals("VALUE", formatter.getArgName());
    }

    // ---- option comparator ----

    // covers default comparator is non-null
    @Test
    public void testGetOptionComparator_defaultInstance_isNotNull() throws Throwable {
        assertNotNull(formatter.getOptionComparator());
    }

    // covers setOptionComparator(null) resets to default alphabetical ordering
    @Test
    public void testSetOptionComparator_null_resetsToDefaultOrdering() throws Throwable {
        Comparator reverse = new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                Option a = (Option) o1;
                Option b = (Option) o2;
                return b.getKey().compareToIgnoreCase(a.getKey());
            }
        };
        formatter.setOptionComparator(reverse);
        formatter.setOptionComparator(null);

        Options options = new Options();
        Option a = new Option("a", false, "descA");
        Option z = new Option("z", false, "descZ");
        options.addOption(a);
        options.addOption(z);

        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        String out = sw.toString();
        assertTrue(out.indexOf("-a") < out.indexOf("-z"));
    }

    // covers custom comparator being used for sorting
    @Test
    public void testSetOptionComparator_customComparator_usedForOrdering() throws Throwable {
        Comparator reverse = new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                Option a = (Option) o1;
                Option b = (Option) o2;
                return b.getKey().compareToIgnoreCase(a.getKey());
            }
        };
        formatter.setOptionComparator(reverse);

        Options options = new Options();
        Option a = new Option("a", false, "descA");
        Option z = new Option("z", false, "descZ");
        options.addOption(a);
        options.addOption(z);

        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        String out = sw.toString();
        assertTrue(out.indexOf("-z") < out.indexOf("-a"));
    }

    // ---- printHelp exceptions ----

    // covers null cmdLineSyntax throwing IllegalArgumentException
    @Test
    public void testPrintHelp_nullSyntax_throwsIllegalArgumentException() throws Throwable {
        try
        {
            formatter.printHelp(pw, 80, null, null, null, 1, 3, null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().indexOf("cmdLineSyntax") >= 0);
        }
    }

    // covers empty cmdLineSyntax throwing IllegalArgumentException
    @Test
    public void testPrintHelp_emptySyntax_throwsIllegalArgumentException() throws Throwable {
        try
        {
            formatter.printHelp(pw, 80, "", null, null, 1, 3, null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().indexOf("cmdLineSyntax") >= 0);
        }
    }

    // ---- printHelp behavior ----

    // covers printHelp without autoUsage: syntax printed literally, no option clause appended
    @Test
    public void testPrintHelp_noAutoUsage_printsSyntaxThenOptions() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", false, "desc");
        a.setRequired(true);
        options.addOption(a);

        formatter.printHelp(pw, 80, "myapp", null, options, 1, 3, null, false);
        pw.flush();

        String expected = "usage: myapp" + NL + " -a   desc" + NL;
        assertEquals(expected, sw.toString());
    }

    // covers printHelp with autoUsage: syntax includes generated option clause
    @Test
    public void testPrintHelp_autoUsage_printsSyntaxWithOptionClause() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", false, "desc");
        a.setRequired(true);
        options.addOption(a);

        formatter.printHelp(pw, 80, "myapp", null, options, 1, 3, null, true);
        pw.flush();

        String expected = "usage: myapp -a" + NL + " -a   desc" + NL;
        assertEquals(expected, sw.toString());
    }

    // covers header and footer being printed when non-blank
    @Test
    public void testPrintHelp_headerAndFooterProvided_bothIncluded() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", false, "desc");
        a.setRequired(true);
        options.addOption(a);

        formatter.printHelp(pw, 80, "myapp", "Header text", options, 1, 3, "Footer text", false);
        pw.flush();

        String expected = "usage: myapp" + NL + "Header text" + NL + " -a   desc" + NL + "Footer text" + NL;
        assertEquals(expected, sw.toString());
    }

    // covers blank (whitespace only) header/footer being skipped
    @Test
    public void testPrintHelp_blankHeaderFooter_notPrinted() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", false, "desc");
        a.setRequired(true);
        options.addOption(a);

        formatter.printHelp(pw, 80, "myapp", "   ", options, 1, 3, "  \t ", false);
        pw.flush();

        String expected = "usage: myapp" + NL + " -a   desc" + NL;
        assertEquals(expected, sw.toString());
    }

    // ---- printUsage ----

    // covers printUsage with no options: only prefix+app printed, trailing space trimmed
    @Test
    public void testPrintUsage_noOptions_onlyPrefixAndAppPrinted() throws Throwable {
        Options options = new Options();
        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        assertEquals("usage: myapp" + NL, sw.toString());
    }

    // covers required option not wrapped in brackets
    @Test
    public void testPrintUsage_requiredOption_noBrackets() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", false, "desc");
        a.setRequired(true);
        options.addOption(a);

        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        assertEquals("usage: myapp -a" + NL, sw.toString());
    }

    // covers optional option wrapped in brackets
    @Test
    public void testPrintUsage_optionalOption_wrappedInBrackets() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", false, "desc");
        options.addOption(a);

        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        assertEquals("usage: myapp [-a]" + NL, sw.toString());
    }

    // covers option with arg and explicit argName rendered as <argName>
    @Test
    public void testPrintUsage_optionWithArgName_includesAngleBracketArg() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", true, "desc");
        a.setArgName("FILE");
        options.addOption(a);

        formatter.printUsage(pw, 80, "myapp", options);
        pw.flush();
        assertEquals("usage: myapp [-a <FILE>]" + NL, sw.toString());
    }

    // covers required OptionGroup rendered without outer brackets, options joined by " | "
    @Test
    public void testPrintUsage_requiredOptionGroup_noOuterBrackets() throws Throwable {
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        Option a = new Option("a", false, "descA");
        Option b = new Option("b", false, "descB");
        group.addOption(a);
        group.addOption(b);
        group.setRequired(true);
        options.addOptionGroup(group);

        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app -a | -b" + NL, sw.toString());
    }

    // covers optional OptionGroup rendered with outer brackets
    @Test
    public void testPrintUsage_optionalOptionGroup_wrappedInBrackets() throws Throwable {
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        Option a = new Option("a", false, "descA");
        Option b = new Option("b", false, "descB");
        group.addOption(a);
        group.addOption(b);
        group.setRequired(false);
        options.addOptionGroup(group);

        formatter.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app [-a | -b]" + NL, sw.toString());
    }

    // ---- printOptions / renderOptions ----

    // covers short+long opt with default arg name rendering
    @Test
    public void testPrintOptions_shortAndLongOptWithArg_formattedCorrectly() throws Throwable {
        Options options = new Options();
        Option a = new Option("a", true, "description");
        a.setLongOpt("aaa");
        options.addOption(a);

        formatter.printOptions(pw, 80, options, 1, 3);
        pw.flush();

        String expected = " -a,--aaa <arg>" + "   " + "description" + NL;
        assertEquals(expected, sw.toString());
    }

    // covers blank argName branch: single space with no angle brackets
    @Test
    public void testPrintOptions_blankArgName_noAngleBrackets() throws Throwable {
        Options options = new Options();
        Option f = new Option("f", true, "file desc");
        f.setArgName("");
        options.addOption(f);

        formatter.printOptions(pw, 80, options, 1, 3);
        pw.flush();

        String expected = " -f " + "   " + "file desc" + NL;
        assertEquals(expected, sw.toString());
    }

    // covers multiple options sorted alphabetically by key
    @Test
    public void testPrintOptions_multipleOptions_sortedAlphabetically() throws Throwable {
        Options options = new Options();
        Option b = new Option("b", false, "descB");
        Option a = new Option("a", false, "descA");
        options.addOption(b);
        options.addOption(a);

        formatter.printOptions(pw, 80, options, 1, 3);
        pw.flush();

        String out = sw.toString();
        assertTrue(out.indexOf("-a") < out.indexOf("-b"));
    }

    // ---- printWrapped / renderWrappedText ----

    // covers short text needing no wrap
    @Test
    public void testPrintWrapped_shortText_noWrapSingleLine() throws Throwable {
        formatter.printWrapped(pw, 80, "Hello World");
        pw.flush();
        assertEquals("Hello World" + NL, sw.toString());
    }

    // covers long text wrapping at a whitespace boundary within width
    @Test
    public void testPrintWrapped_longText_wrapsAtWhitespaceBoundary() throws Throwable {
        formatter.printWrapped(pw, 9, "aaaa bbbb cccc dddd");
        pw.flush();
        String expected = "aaaa bbbb" + NL + "cccc dddd" + NL;
        assertEquals(expected, sw.toString());
    }

    // covers continuation lines padded using nextLineTabStop
    @Test
    public void testRenderWrappedText_nonZeroTabStop_padsContinuationLine() throws Throwable {
        StringBuffer sb = new StringBuffer();
        formatter.renderWrappedText(sb, 9, 3, "aaaa bbbb cccc");
        String expected = "aaaa bbbb" + NL + "   cccc";
        assertEquals(expected, sb.toString());
    }

    // ---- findWrapPos ----

    // covers newline found before width at startPos 0
    @Test
    public void testFindWrapPos_newlineBeforeWidth_returnsPositionAfterNewline() throws Throwable {
        assertEquals(4, formatter.findWrapPos("abc\ndef", 10, 0));
    }

    // covers backward whitespace search finding the last space before width
    @Test
    public void testFindWrapPos_whitespaceFoundBackward_returnsThatPosition() throws Throwable {
        assertEquals(5, formatter.findWrapPos("hello world foo", 8, 0));
    }

    // covers text entirely fitting within width returning -1
    @Test
    public void testFindWrapPos_textFitsWithinWidth_returnsMinusOne() throws Throwable {
        assertEquals(-1, formatter.findWrapPos("abcdefgh", 20, 0));
    }

    // bug-catching test: newline detection must account for startPos offset, not just width
    @Test
    public void testFindWrapPos_newlineWithNonZeroStartPos_accountsForStartPosOffset() throws Throwable {
        String text = "0123456789\nABCDEF";
        int result = formatter.findWrapPos(text, 5, 8);
        assertEquals(11, result);
    }

    // ---- createPadding / rtrim ----

    // covers positive length padding of spaces
    @Test
    public void testCreatePadding_positiveLength_returnsThatManySpaces() throws Throwable {
        String padding = formatter.createPadding(5);
        assertEquals("     ", padding);
        assertEquals(5, padding.length());
    }

    // covers zero length padding returns empty string
    @Test
    public void testCreatePadding_zeroLength_returnsEmptyString() throws Throwable {
        assertEquals("", formatter.createPadding(0));
    }

    // covers trailing whitespace removed, leading whitespace kept
    @Test
    public void testRtrim_trailingWhitespace_removedLeadingKept() throws Throwable {
        assertEquals(" hello", formatter.rtrim(" hello   "));
    }

    // covers null input returns null
    @Test
    public void testRtrim_nullInput_returnsNull() throws Throwable {
        assertNull(formatter.rtrim(null));
    }

    // covers empty string returns empty string
    @Test
    public void testRtrim_emptyString_returnsEmptyString() throws Throwable {
        assertEquals("", formatter.rtrim(""));
    }
}
