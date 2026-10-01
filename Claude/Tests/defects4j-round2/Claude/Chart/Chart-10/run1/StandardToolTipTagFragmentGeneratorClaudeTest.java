package org.jfree.chart.imagemap;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class StandardToolTipTagFragmentGeneratorClaudeTest {

    private StandardToolTipTagFragmentGenerator generator;

    @Before
    public void setUp() throws Throwable {
        generator = new StandardToolTipTagFragmentGenerator();
    }

    // Constructor: instance should be created and implement the expected interface
    @Test
    public void testConstructor_createsInstanceImplementingInterface() throws Throwable {
        assertTrue(generator instanceof ToolTipTagFragmentGenerator);
    }

    // Basic plain text with no special characters must be wrapped unchanged
    @Test
    public void testGenerateToolTipFragment_plainText_wrapsUnchanged() throws Throwable {
        String result = generator.generateToolTipFragment("Hello World");
        assertEquals(" title=\"Hello World\" alt=\"\"", result);
    }

    // Empty string input produces empty title attribute
    @Test
    public void testGenerateToolTipFragment_emptyString_producesEmptyTitle() throws Throwable {
        String result = generator.generateToolTipFragment("");
        assertEquals(" title=\"\" alt=\"\"", result);
    }

    // Whitespace-only input is preserved as-is inside title attribute
    @Test
    public void testGenerateToolTipFragment_whitespaceOnly_preserved() throws Throwable {
        String result = generator.generateToolTipFragment(" ");
        assertEquals(" title=\" \" alt=\"\"", result);
    }

    // Leading and trailing spaces in tooltip text must be preserved
    @Test
    public void testGenerateToolTipFragment_leadingTrailingSpaces_preserved() throws Throwable {
        String result = generator.generateToolTipFragment("  padded  ");
        assertEquals(" title=\"  padded  \" alt=\"\"", result);
    }

    // Ampersand must be escaped to &amp; to keep valid HTML attribute (fixes bug 1400917)
    @Test
    public void testGenerateToolTipFragment_ampersand_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("A & B");
        assertEquals(" title=\"A &amp; B\" alt=\"\"", result);
    }

    // Less-than character must be escaped to &lt;
    @Test
    public void testGenerateToolTipFragment_lessThan_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("5 < 10");
        assertEquals(" title=\"5 &lt; 10\" alt=\"\"", result);
    }

    // Greater-than character must be escaped to &gt;
    @Test
    public void testGenerateToolTipFragment_greaterThan_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("10 > 5");
        assertEquals(" title=\"10 &gt; 5\" alt=\"\"", result);
    }

    // Double quote inside tooltip text must be escaped to &quot; to avoid breaking the attribute
    @Test
    public void testGenerateToolTipFragment_doubleQuote_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("Say \"Hi\"");
        assertEquals(" title=\"Say &quot;Hi&quot;\" alt=\"\"", result);
    }

    // Combination of all key XML special characters must all be escaped together
    @Test
    public void testGenerateToolTipFragment_allSpecialChars_allEscaped() throws Throwable {
        String result = generator.generateToolTipFragment("<b>A & \"B\"</b>");
        assertEquals(" title=\"&lt;b&gt;A &amp; &quot;B&quot;&lt;/b&gt;\" alt=\"\"", result);
    }

    // Repeated occurrences of the same special character must each be escaped
    @Test
    public void testGenerateToolTipFragment_repeatedAmpersands_allEscaped() throws Throwable {
        String result = generator.generateToolTipFragment("&&&");
        assertEquals(" title=\"&amp;&amp;&amp;\" alt=\"\"", result);
    }

    // Repeated quote characters must each be escaped independently
    @Test
    public void testGenerateToolTipFragment_repeatedQuotes_allEscaped() throws Throwable {
        String result = generator.generateToolTipFragment("\"\"");
        assertEquals(" title=\"&quot;&quot;\" alt=\"\"", result);
    }

    // Non-ASCII unicode characters are not part of XML special set and remain unchanged
    @Test
    public void testGenerateToolTipFragment_unicodeChars_unchanged() throws Throwable {
        String result = generator.generateToolTipFragment("caf\u00e9");
        assertEquals(" title=\"caf\u00e9\" alt=\"\"", result);
    }

    // Newline character is not an XML-escaped character and should be preserved
    @Test
    public void testGenerateToolTipFragment_newlineChar_preserved() throws Throwable {
        String result = generator.generateToolTipFragment("line1\nline2");
        assertEquals(" title=\"line1\nline2\" alt=\"\"", result);
    }

    // Tab character is not an XML-escaped character and should be preserved
    @Test
    public void testGenerateToolTipFragment_tabChar_preserved() throws Throwable {
        String result = generator.generateToolTipFragment("col1\tcol2");
        assertEquals(" title=\"col1\tcol2\" alt=\"\"", result);
    }

    // Long text input is concatenated correctly without truncation
    @Test
    public void testGenerateToolTipFragment_longText_fullyIncluded() throws Throwable {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("x");
        }
        String input = sb.toString();
        String result = generator.generateToolTipFragment(input);
        assertEquals(" title=\"" + input + "\" alt=\"\"", result);
    }

    // Output must always start with the literal ' title="' prefix
    @Test
    public void testGenerateToolTipFragment_output_startsWithTitlePrefix() throws Throwable {
        String result = generator.generateToolTipFragment("abc");
        assertTrue(result.startsWith(" title=\""));
    }

    // Output must always end with the literal 'alt=""' suffix
    @Test
    public void testGenerateToolTipFragment_output_endsWithAltSuffix() throws Throwable {
        String result = generator.generateToolTipFragment("abc");
        assertTrue(result.endsWith("alt=\"\""));
    }

    // Numeric-looking text is treated as plain text and wrapped unchanged
    @Test
    public void testGenerateToolTipFragment_numericText_wrappedUnchanged() throws Throwable {
        String result = generator.generateToolTipFragment("12345");
        assertEquals(" title=\"12345\" alt=\"\"", result);
    }

    // Calling the method twice with same input is deterministic and returns equal results
    @Test
    public void testGenerateToolTipFragment_calledTwiceSameInput_deterministic() throws Throwable {
        String result1 = generator.generateToolTipFragment("Repeat & Check");
        String result2 = generator.generateToolTipFragment("Repeat & Check");
        assertEquals(result1, result2);
    }

    // Single ampersand at very start of string is escaped correctly (boundary case)
    @Test
    public void testGenerateToolTipFragment_ampersandAtStart_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("&start");
        assertEquals(" title=\"&amp;start\" alt=\"\"", result);
    }

    // Single special character at the very end of string is escaped correctly (boundary case)
    @Test
    public void testGenerateToolTipFragment_lessThanAtEnd_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("end<");
        assertEquals(" title=\"end&lt;\" alt=\"\"", result);
    }

    // Mixed text with special char in the middle surrounded by plain text
    @Test
    public void testGenerateToolTipFragment_greaterThanInMiddle_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("left>right");
        assertEquals(" title=\"left&gt;right\" alt=\"\"", result);
    }

    // Returned string is never null for a valid non-null input
    @Test
    public void testGenerateToolTipFragment_validInput_neverReturnsNull() throws Throwable {
        String result = generator.generateToolTipFragment("value");
        assertNotNull(result);
    }

    // Text consisting solely of a double quote character is fully escaped
    @Test
    public void testGenerateToolTipFragment_onlyDoubleQuote_escaped() throws Throwable {
        String result = generator.generateToolTipFragment("\"");
        assertEquals(" title=\"&quot;\" alt=\"\"", result);
    }
}
