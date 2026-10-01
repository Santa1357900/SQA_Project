package org.apache.commons.lang;

import java.io.StringWriter;
import java.io.Writer;

import org.apache.commons.lang.exception.NestableRuntimeException;

import org.junit.Test;
import static org.junit.Assert.*;

public class StringEscapeUtilsClaudeTest {

    // Public constructor must be usable by tools requiring a JavaBean instance
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        StringEscapeUtils instance = new StringEscapeUtils();
        assertNotNull(instance);
    }

    // escapeJava: null input branch -> returns null
    @Test
    public void testEscapeJava_nullInput_returnsNull() throws Throwable {
        assertNull(StringEscapeUtils.escapeJava(null));
    }

    // escapeJava: empty string, zero loop iterations
    @Test
    public void testEscapeJava_emptyString_returnsEmpty() throws Throwable {
        assertEquals("", StringEscapeUtils.escapeJava(""));
    }

    // escapeJava: control char switch case '\t'
    @Test
    public void testEscapeJava_tabCharacter_escapesToBackslashT() throws Throwable {
        assertEquals("\\t", StringEscapeUtils.escapeJava("\t"));
    }

    // escapeJava: switch case '"'
    @Test
    public void testEscapeJava_doubleQuote_escapesToBackslashQuote() throws Throwable {
        assertEquals("\\\"", StringEscapeUtils.escapeJava("\""));
    }

    // escapeJava: switch case '\\'
    @Test
    public void testEscapeJava_backslash_escapesToDoubleBackslash() throws Throwable {
        assertEquals("\\\\", StringEscapeUtils.escapeJava("\\"));
    }

    // escapeJava: switch case '\'' with escapeSingleQuotes=false -> not escaped
    @Test
    public void testEscapeJava_singleQuote_notEscaped() throws Throwable {
        assertEquals("'", StringEscapeUtils.escapeJava("'"));
    }

    // Bug hunt: per Javadoc, Java string literals never need to escape '/'
    // (only JavaScript vs Java differ on the single quote). Buggy code escapes it as "\/".
    @Test
    public void testEscapeJava_forwardSlash_notEscapedPerContract() throws Throwable {
        assertEquals("/", StringEscapeUtils.escapeJava("/"));
    }

    // escapeJava: ch<32, not a named escape, ch<=0xf -> "\u000X" branch
    @Test
    public void testEscapeJava_controlCharLow_escapesToFourDigitUnicode() throws Throwable {
        String result = StringEscapeUtils.escapeJava(String.valueOf('\u0002'));
        assertEquals("\\u0002", result);
    }

    // escapeJava: ch<32, not a named escape, ch>0xf -> "\u00XX" branch
    @Test
    public void testEscapeJava_controlCharHigh_escapesToFourDigitUnicode() throws Throwable {
        String result = StringEscapeUtils.escapeJava(String.valueOf('\u0015'));
        assertEquals("\\u0015", result);
    }

    // escapeJava: ch>0x7f and <=0xff -> "\u00" + hex branch
    @Test
    public void testEscapeJava_charAbove0x7f_escapesTo00Unicode() throws Throwable {
        String result = StringEscapeUtils.escapeJava(String.valueOf('\u00e9'));
        assertEquals("\\u00E9", result);
    }

    // escapeJava: ch>0xff and <=0xfff -> "\u0" + hex branch
    @Test
    public void testEscapeJava_charAbove0xff_escapesTo0Unicode() throws Throwable {
        String result = StringEscapeUtils.escapeJava(String.valueOf('\u0100'));
        assertEquals("\\u0100", result);
    }

    // escapeJava: ch>0xfff -> "\u" + hex branch
    @Test
    public void testEscapeJava_charAbove0xfff_escapesToFullUnicode() throws Throwable {
        String result = StringEscapeUtils.escapeJava(String.valueOf('\u1000'));
        assertEquals("\\u1000", result);
    }

    // escapeJava(Writer,String): null writer -> IllegalArgumentException
    @Test
    public void testEscapeJavaWriter_nullWriter_throwsIllegalArgumentException() throws Throwable {
        try {
            StringEscapeUtils.escapeJava((Writer) null, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // escapeJava(Writer,String): null string input has no effect
    @Test
    public void testEscapeJavaWriter_nullString_noEffect() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeJava(writer, null);
        assertEquals("", writer.toString());
    }

    // escapeJavaScript: single quote IS escaped (JS vs Java difference); forward slash unescaped per contract
    @Test
    public void testEscapeJavaScript_singleQuoteEscaped_andForwardSlashNotEscaped() throws Throwable {
        assertEquals("\\'", StringEscapeUtils.escapeJavaScript("'"));
        assertEquals("/", StringEscapeUtils.escapeJavaScript("/"));
    }

    // escapeJavaScript: null input branch
    @Test
    public void testEscapeJavaScript_nullInput_returnsNull() throws Throwable {
        assertNull(StringEscapeUtils.escapeJavaScript(null));
    }

    // unescapeJava: null input branch
    @Test
    public void testUnescapeJava_nullInput_returnsNull() throws Throwable {
        assertNull(StringEscapeUtils.unescapeJava(null));
    }

    // unescapeJava: hadSlash + case 't'
    @Test
    public void testUnescapeJava_backslashT_unescapesToTab() throws Throwable {
        assertEquals("\t", StringEscapeUtils.unescapeJava("\\t"));
    }

    // unescapeJava: hadSlash + case 'n'
    @Test
    public void testUnescapeJava_backslashN_unescapesToNewline() throws Throwable {
        assertEquals("\n", StringEscapeUtils.unescapeJava("\\n"));
    }

    // unescapeJava: hadSlash + case '\\'
    @Test
    public void testUnescapeJava_doubleBackslash_unescapesToSingleBackslash() throws Throwable {
        assertEquals("\\", StringEscapeUtils.unescapeJava("\\\\"));
    }

    // unescapeJava: hadSlash + case 'u' -> valid 4-digit unicode
    @Test
    public void testUnescapeJava_backslashUnicode_unescapesToCharacter() throws Throwable {
        assertEquals("A", StringEscapeUtils.unescapeJava("\\u0041"));
    }

    // unescapeJava: invalid hex digits in unicode escape -> NestableRuntimeException
    @Test
    public void testUnescapeJava_invalidUnicode_throwsNestableRuntimeException() throws Throwable {
        try {
            StringEscapeUtils.unescapeJava("\\u00ZZ");
            fail("expected NestableRuntimeException");
        } catch (NestableRuntimeException expected) {
        }
    }

    // unescapeJava: trailing lone backslash at end of string is output as-is
    @Test
    public void testUnescapeJava_trailingBackslash_preservesBackslash() throws Throwable {
        assertEquals("abc\\", StringEscapeUtils.unescapeJava("abc\\"));
    }

    // unescapeJava: hadSlash + unrecognized char -> default writes the char literally
    @Test
    public void testUnescapeJava_unknownEscapeChar_writesCharLiterally() throws Throwable {
        assertEquals("z", StringEscapeUtils.unescapeJava("\\z"));
    }

    // unescapeJava(Writer,String): null writer -> IllegalArgumentException
    @Test
    public void testUnescapeJavaWriter_nullWriter_throwsIllegalArgumentException() throws Throwable {
        try {
            StringEscapeUtils.unescapeJava((Writer) null, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // unescapeJavaScript delegates to unescapeJava
    @Test
    public void testUnescapeJavaScript_delegatesToUnescapeJava() throws Throwable {
        assertEquals("\t", StringEscapeUtils.unescapeJavaScript("\\t"));
    }

    // escapeHtml: basic entity '<' -> &lt;, and null input branch
    @Test
    public void testEscapeHtml_lessThan_andNullInput() throws Throwable {
        assertEquals("&lt;", StringEscapeUtils.escapeHtml("<"));
        assertNull(StringEscapeUtils.escapeHtml(null));
    }

    // escapeHtml: Javadoc's own example with quotes and ampersand
    @Test
    public void testEscapeHtml_ampersandAndQuote_matchesJavadocExample() throws Throwable {
        String input = "\"bread\" & \"butter\"";
        String expected = "&quot;bread&quot; &amp; &quot;butter&quot;";
        assertEquals(expected, StringEscapeUtils.escapeHtml(input));
    }

    // escapeHtml(Writer,String): null writer -> IllegalArgumentException
    @Test
    public void testEscapeHtmlWriter_nullWriter_throwsIllegalArgumentException() throws Throwable {
        try {
            StringEscapeUtils.escapeHtml((Writer) null, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // unescapeHtml: Javadoc's own example with a named entity
    @Test
    public void testUnescapeHtml_entity_unescapesToChar() throws Throwable {
        String input = "&lt;Fran&ccedil;ais&gt;";
        String expected = "<Fran" + "\u00e7" + "ais>";
        assertEquals(expected, StringEscapeUtils.unescapeHtml(input));
    }

    // unescapeHtml: unrecognized entity left verbatim in the output
    @Test
    public void testUnescapeHtml_unknownEntity_leftUnchanged() throws Throwable {
        assertEquals("&zzzz;", StringEscapeUtils.unescapeHtml("&zzzz;"));
    }

    // unescapeHtml(Writer,String): null writer -> IllegalArgumentException
    @Test
    public void testUnescapeHtmlWriter_nullWriter_throwsIllegalArgumentException() throws Throwable {
        try {
            StringEscapeUtils.unescapeHtml((Writer) null, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // escapeXml: basic entity '<'
    @Test
    public void testEscapeXml_lessThan_escapesToEntity() throws Throwable {
        assertEquals("&lt;", StringEscapeUtils.escapeXml("<"));
    }

    // escapeXml: apostrophe is one of the five basic XML entities
    @Test
    public void testEscapeXml_apostrophe_escapesToEntity() throws Throwable {
        assertEquals("&apos;", StringEscapeUtils.escapeXml("'"));
    }

    // escapeXml(Writer,String): null writer -> IllegalArgumentException
    @Test
    public void testEscapeXmlWriter_nullWriter_throwsIllegalArgumentException() throws Throwable {
        try {
            StringEscapeUtils.escapeXml((Writer) null, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // unescapeXml: basic entity round trip
    @Test
    public void testUnescapeXml_entity_unescapesToChar() throws Throwable {
        assertEquals("<", StringEscapeUtils.unescapeXml("&lt;"));
    }

    // unescapeXml(Writer,String): null writer -> IllegalArgumentException
    @Test
    public void testUnescapeXmlWriter_nullWriter_throwsIllegalArgumentException() throws Throwable {
        try {
            StringEscapeUtils.unescapeXml((Writer) null, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // escapeSql: single quote doubled (Javadoc example), and null input branch
    @Test
    public void testEscapeSql_singleQuote_and_nullInput() throws Throwable {
        assertEquals("McHale''s Navy", StringEscapeUtils.escapeSql("McHale's Navy"));
        assertNull(StringEscapeUtils.escapeSql(null));
    }

    // escapeCsv: plain value unchanged, and null input branch (containsNone short-circuit)
    @Test
    public void testEscapeCsv_plainAndNullInput() throws Throwable {
        assertEquals("abc", StringEscapeUtils.escapeCsv("abc"));
        assertNull(StringEscapeUtils.escapeCsv(null));
    }

    // escapeCsv: value containing comma gets wrapped in double quotes
    @Test
    public void testEscapeCsv_containsComma_wrappedInQuotes() throws Throwable {
        assertEquals("\"a,b\"", StringEscapeUtils.escapeCsv("a,b"));
    }

    // escapeCsv: embedded double quote is doubled and whole value wrapped in quotes
    @Test
    public void testEscapeCsv_containsQuote_escapedAndWrapped() throws Throwable {
        assertEquals("\"a\"\"b\"", StringEscapeUtils.escapeCsv("a\"b"));
    }

    // unescapeCsv: null input branch, and non-quoted value returned unchanged
    @Test
    public void testUnescapeCsv_null_and_notQuoted() throws Throwable {
        assertNull(StringEscapeUtils.unescapeCsv(null));
        assertEquals("abc", StringEscapeUtils.unescapeCsv("abc"));
    }

    // unescapeCsv: quoted value containing a comma has its surrounding quotes stripped
    @Test
    public void testUnescapeCsv_quotedWithComma_quotesStripped() throws Throwable {
        assertEquals("a,b", StringEscapeUtils.unescapeCsv("\"a,b\""));
    }
}
