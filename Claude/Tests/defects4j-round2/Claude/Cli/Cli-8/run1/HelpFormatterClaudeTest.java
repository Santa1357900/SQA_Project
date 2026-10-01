package org.apache.commons.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HelpFormatterClaudeTest
{
    private HelpFormatter hf;
    private StringWriter sw;
    private PrintWriter pw;

    @Before
    public void setUp() throws Throwable
    {
        hf = new HelpFormatter();
        sw = new StringWriter();
        pw = new PrintWriter(sw);
    }

    // Covers all default field values exposed via getters
    @Test
    public void testDefaultValues_allDefaultsCorrect() throws Throwable {
        assertEquals(HelpFormatter.DEFAULT_WIDTH, hf.getWidth());
        assertEquals(HelpFormatter.DEFAULT_LEFT_PAD, hf.getLeftPadding());
        assertEquals(HelpFormatter.DEFAULT_DESC_PAD, hf.getDescPadding());
        assertEquals(HelpFormatter.DEFAULT_SYNTAX_PREFIX, hf.getSyntaxPrefix());
        assertEquals(System.getProperty("line.separator"), hf.getNewLine());
        assertEquals(HelpFormatter.DEFAULT_OPT_PREFIX, hf.getOptPrefix());
        assertEquals(HelpFormatter.DEFAULT_LONG_OPT_PREFIX, hf.getLongOptPrefix());
        assertEquals(HelpFormatter.DEFAULT_ARG_NAME, hf.getArgName());
    }

    // Covers every setter updating its corresponding getter value
    @Test
    public void testSetters_allSettersUpdateValues() throws Throwable {
        hf.setWidth(50);
        hf.setLeftPadding(5);
        hf.setDescPadding(7);
        hf.setSyntaxPrefix("call: ");
        hf.setNewLine("\n");
        hf.setOptPrefix("+");
        hf.setLongOptPrefix("++");
        hf.setArgName("val");
        assertEquals(50, hf.getWidth());
        assertEquals(5, hf.getLeftPadding());
        assertEquals(7, hf.getDescPadding());
        assertEquals("call: ", hf.getSyntaxPrefix());
        assertEquals("\n", hf.getNewLine());
        assertEquals("+", hf.getOptPrefix());
        assertEquals("++", hf.getLongOptPrefix());
        assertEquals("val", hf.getArgName());
    }

    // printHelp throws IllegalArgumentException when cmdLineSyntax is null
    @Test
    public void testPrintHelp_nullCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        Options options = new Options();
        try
        {
            hf.printHelp(pw, 80, null, null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
            // expected
        }
    }

    // printHelp throws IllegalArgumentException when cmdLineSyntax is empty
    @Test
    public void testPrintHelp_emptyCmdLineSyntax_throwsIllegalArgumentException() throws Throwable {
        Options options = new Options();
        try
        {
            hf.printHelp(pw, 80, "", null, options, 1, 3, null, false);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
            // expected
        }
    }

    // autoUsage=true branch: options are rendered into the usage line
    @Test
    public void testPrintHelp_autoUsageTrue_includesOptionUsage() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "desc");
        hf.printHelp(pw, 80, "app", null, options, 1, 3, null, true);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("usage: app [-a]"));
    }

    // autoUsage=false branch: usage line contains only the cmdLineSyntax
    @Test
    public void testPrintHelp_autoUsageFalse_usageLineExcludesOptions() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "desc");
        hf.printHelp(pw, 80, "app", null, options, 1, 3, null, false);
        pw.flush();
        String firstLine = sw.toString().split("\\r?\\n")[0];
        assertEquals("usage: app", firstLine);
    }

    // header and footer non-blank: both must appear in the output
    @Test
    public void testPrintHelp_headerAndFooterProvided_includedInOutput() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "desc");
        hf.printHelp(pw, 80, "app", "HeaderText", options, 1, 3, "FooterText", false);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("HeaderText"));
        assertTrue(output.contains("FooterText"));
    }

    // header/footer null: branch is skipped, no "null" literal leaks into output
    @Test
    public void testPrintHelp_nullHeaderFooter_noExtraLines() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "desc");
        hf.printHelp(pw, 80, "app", null, options, 1, 3, null, false);
        pw.flush();
        assertFalse(sw.toString().contains("null"));
    }

    // 2-arg printHelp(String, Options) convenience overload writes to System.out
    @Test
    public void testPrintHelp_twoArgOverload_writesUsageToSystemOut() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "desc");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try
        {
            hf.printHelp("app", options);
        }
        finally
        {
            System.setOut(original);
        }
        assertTrue(baos.toString().contains("usage: app"));
    }

    // 4-arg printHelp(String, String, Options, String) delegates and includes header/footer
    @Test
    public void testPrintHelp_fourArgOverload_includesHeaderAndFooter() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "desc");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try
        {
            hf.printHelp("app", "HEAD", options, "FOOT");
        }
        finally
        {
            System.setOut(original);
        }
        String output = baos.toString();
        assertTrue(output.contains("HEAD"));
        assertTrue(output.contains("FOOT"));
    }

    // optional single-letter option is wrapped in square brackets
    @Test
    public void testPrintUsage_optionalOption_bracketed() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "desc");
        hf.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app [-a]", sw.toString().trim());
    }

    // required option is NOT wrapped in square brackets
    @Test
    public void testPrintUsage_requiredOption_notBracketed() throws Throwable {
        Options options = new Options();
        Option opt = new Option("a", false, "desc");
        opt.setRequired(true);
        options.addOption(opt);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app -a", sw.toString().trim());
    }

    // option with an argument and explicit argName shows the <ARGNAME> placeholder
    @Test
    public void testPrintUsage_optionWithArgName_showsPlaceholder() throws Throwable {
        Options options = new Options();
        Option opt = new Option("f", true, "file desc");
        opt.setArgName("FILE");
        options.addOption(opt);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app [-f <FILE>]", sw.toString().trim());
    }

    // required OptionGroup: options are piped without an outer bracket pair
    @Test
    public void testPrintUsage_requiredOptionGroup_pipedNoBrackets() throws Throwable {
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "descA"));
        group.addOption(new Option("b", false, "descB"));
        group.setRequired(true);
        options.addOptionGroup(group);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app -a | -b", sw.toString().trim());
    }

    // optional OptionGroup: options are piped and wrapped in a single bracket pair
    @Test
    public void testPrintUsage_optionalOptionGroup_bracketedWithPipe() throws Throwable {
        Options options = new Options();
        OptionGroup group = new OptionGroup();
        group.addOption(new Option("a", false, "descA"));
        group.addOption(new Option("b", false, "descB"));
        options.addOptionGroup(group);
        hf.printUsage(pw, 80, "app", options);
        pw.flush();
        assertEquals("usage: app [-a | -b]", sw.toString().trim());
    }

    // printUsage(pw, width, cmdLineSyntax) simply prefixes the syntax prefix
    @Test
    public void testPrintUsage_cmdLineSyntaxOnly_prefixedWithUsage() throws Throwable {
        hf.printUsage(pw, 80, "myapp -x");
        pw.flush();
        assertEquals("usage: myapp -x", sw.toString().trim());
    }

    // single option with description is present in printOptions output
    @Test
    public void testPrintOptions_singleOption_containsOptAndDescription() throws Throwable {
        Options options = new Options();
        options.addOption("a", false, "the description");
        hf.printOptions(pw, 80, options, 1, 3);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("-a"));
        assertTrue(output.contains("the description"));
    }

    // multiple options are rendered sorted alphabetically by key
    @Test
    public void testPrintOptions_multipleOptions_sortedAlphabetically() throws Throwable {
        Options options = new Options();
        options.addOption("b", false, "bdesc");
        options.addOption("a", false, "adesc");
        hf.printOptions(pw, 80, options, 1, 3);
        pw.flush();
        String output = sw.toString();
        int idxA = output.indexOf("-a");
        int idxB = output.indexOf("-b");
        assertTrue(idxA >= 0 && idxB >= 0 && idxA < idxB);
    }

    // option that has both a short and a long opt: both forms should appear
    @Test
    public void testPrintOptions_optionWithLongOpt_includesBothForms() throws Throwable {
        Options options = new Options();
        Option opt = new Option("v", "verbose", false, "verbose desc");
        options.addOption(opt);
        hf.printOptions(pw, 80, options, 1, 3);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("-v"));
        assertTrue(output.contains("--verbose"));
    }

    // short text that fits width is printed unwrapped on a single line
    @Test
    public void testPrintWrapped_shortText_singleLine() throws Throwable {
        hf.printWrapped(pw, 80, "short text");
        pw.flush();
        assertEquals("short text", sw.toString().trim());
    }

    // long text is wrapped so no line exceeds the requested width
    @Test
    public void testPrintWrapped_longText_wrapsAtWidth() throws Throwable {
        String text = "This is a fairly long piece of text that should wrap";
        hf.printWrapped(pw, 20, text);
        pw.flush();
        String[] lines = sw.toString().split("\\r?\\n");
        assertTrue(lines.length > 1);
        for (int i = 0; i < lines.length; i++)
        {
            assertTrue(lines[i].length() <= 20);
        }
    }

    // explicit newline before the width boundary forces a wrap at that newline
    @Test
    public void testRenderWrappedText_explicitNewlineWithinWidth_wrapsAtNewline() throws Throwable {
        StringBuffer sb = new StringBuffer();
        hf.renderWrappedText(sb, 80, 0, "abc\ndef");
        String[] lines = sb.toString().split("\\r?\\n");
        assertEquals("abc", lines[0]);
        assertEquals("def", lines[1]);
    }

    // explicit tab before the width boundary forces a wrap at that tab
    @Test
    public void testRenderWrappedText_tabWithinWidth_wrapsAtTab() throws Throwable {
        StringBuffer sb = new StringBuffer();
        hf.renderWrappedText(sb, 80, 0, "abc\tdef");
        String[] lines = sb.toString().split("\\r?\\n");
        assertEquals("abc", lines[0]);
        assertEquals("def", lines[1]);
    }

    // text fits entirely within width: returns -1 (no wrap needed)
    @Test
    public void testFindWrapPos_textFitsWithinWidth_returnsMinusOne() throws Throwable {
        int pos = hf.findWrapPos("short text", 80, 0);
        assertEquals(-1, pos);
    }

    // text exceeds width, whitespace exists exactly at the boundary
    @Test
    public void testFindWrapPos_textExceedsWidth_wrapsAtLastSpace() throws Throwable {
        String text = "01234 6789 123456789";
        int pos = hf.findWrapPos(text, 10, 0);
        assertEquals(10, pos);
    }

    // no whitespace before the boundary: falls back to forward search
    @Test
    public void testFindWrapPos_noWhitespaceBeforeBoundary_returnsForwardWhitespacePos() throws Throwable {
        String text = "0123456789abc def";
        int pos = hf.findWrapPos(text, 10, 0);
        assertEquals(13, pos);
    }

    // no whitespace anywhere after the boundary until the very end of text:
    // per Javadoc contract this must return -1 ("wrap position is at the end of the text")
    @Test
    public void testFindWrapPos_noWhitespaceAnywhereAfterBoundary_returnsMinusOne() throws Throwable {
        String text = "0123456789abcdefghij";
        int pos = hf.findWrapPos(text, 10, 0);
        assertEquals(-1, pos);
    }

    // createPadding with length 0 returns empty string
    @Test
    public void testCreatePadding_zeroLength_returnsEmptyString() throws Throwable {
        assertEquals("", hf.createPadding(0));
    }

    // createPadding with positive length returns that many spaces
    @Test
    public void testCreatePadding_positiveLength_returnsSpaces() throws Throwable {
        assertEquals("     ", hf.createPadding(5));
    }

    // rtrim on null input returns null
    @Test
    public void testRtrim_nullInput_returnsNull() throws Throwable {
        assertNull(hf.rtrim(null));
    }

    // rtrim on empty string returns empty string
    @Test
    public void testRtrim_emptyString_returnsEmpty() throws Throwable {
        assertEquals("", hf.rtrim(""));
    }

    // rtrim removes trailing whitespace only
    @Test
    public void testRtrim_trailingWhitespace_removed() throws Throwable {
        assertEquals("hello", hf.rtrim("hello   "));
    }

    // rtrim leaves a string without trailing whitespace unchanged
    @Test
    public void testRtrim_noTrailingWhitespace_unchanged() throws Throwable {
        assertEquals("hello", hf.rtrim("hello"));
    }
}
