package org.apache.commons.lang;

import junit.framework.TestCase;
import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;
import org.apache.commons.lang.exception.NestableRuntimeException;

public class StringEscapeUtilsTest extends TestCase {

    public void testConstructor() throws Throwable {
        StringEscapeUtils utils = new StringEscapeUtils();
        assertNotNull(utils);
    }

    public void testEscapeJava() throws Throwable {
        assertNull(StringEscapeUtils.escapeJava(null));
        assertEquals("abc", StringEscapeUtils.escapeJava("abc"));
        assertEquals("He didn't say, \\\"Stop!\\\"", StringEscapeUtils.escapeJava("He didn't say, \"Stop!\""));
        assertEquals("\\t\\n\\r", StringEscapeUtils.escapeJava("\t\n\r"));
        assertEquals("\\b\\f\\\\\\/", StringEscapeUtils.escapeJava("\b\f\\/"));
        assertEquals("\\u0001", StringEscapeUtils.escapeJava("\u0001"));
        assertEquals("\\u001f", StringEscapeUtils.escapeJava("\u001f"));
        assertEquals("\\u0080", StringEscapeUtils.escapeJava("\u0080"));
        assertEquals("\\u0100", StringEscapeUtils.escapeJava("\u0100"));
        assertEquals("\\u1000", StringEscapeUtils.escapeJava("\u1000"));
    }

    public void testEscapeJavaWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeJava(writer, "abc");
        assertEquals("abc", writer.toString());

        StringWriter nullWriter = new StringWriter();
        StringEscapeUtils.escapeJava(nullWriter, null);
        assertEquals("", nullWriter.toString());

        try {
            StringEscapeUtils.escapeJava(null, "abc");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testEscapeJavaScript() throws Throwable {
        assertNull(StringEscapeUtils.escapeJavaScript(null));
        assertEquals("He didn\\'t say, \\\"Stop!\\\"", StringEscapeUtils.escapeJavaScript("He didn't say, \"Stop!\""));
    }

    public void testEscapeJavaScriptWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeJavaScript(writer, "He didn't say, \"Stop!\"");
        assertEquals("He didn\\'t say, \\\"Stop!\\\"", writer.toString());
    }

    public void testUnescapeJava() throws Throwable {
        assertNull(StringEscapeUtils.unescapeJava(null));
        assertEquals("abc", StringEscapeUtils.unescapeJava("abc"));
        assertEquals("\"", StringEscapeUtils.unescapeJava("\\\""));
        assertEquals("\\", StringEscapeUtils.unescapeJava("\\\\"));
        assertEquals("\r", StringEscapeUtils.unescapeJava("\\r"));
        assertEquals("\f", StringEscapeUtils.unescapeJava("\\f"));
        assertEquals("\t", StringEscapeUtils.unescapeJava("\\t"));
        assertEquals("\n", StringEscapeUtils.unescapeJava("\\n"));
        assertEquals("\b", StringEscapeUtils.unescapeJava("\\b"));
        assertEquals("'", StringEscapeUtils.unescapeJava("\\'"));
        assertEquals("\u000a", StringEscapeUtils.unescapeJava("\\u000a"));
        assertEquals("a", StringEscapeUtils.unescapeJava("\\v")); // default switch case
        assertEquals("\\", StringEscapeUtils.unescapeJava("\\")); // trailing slash

        try {
            StringEscapeUtils.unescapeJava("\\u00ZZ");
            fail("Expected NestableRuntimeException");
        } catch (NestableRuntimeException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testUnescapeJavaWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeJava(writer, "abc");
        assertEquals("abc", writer.toString());

        StringWriter nullWriter = new StringWriter();
        StringEscapeUtils.unescapeJava(nullWriter, null);
        assertEquals("", nullWriter.toString());

        try {
            StringEscapeUtils.unescapeJava(null, "abc");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testUnescapeJavaScript() throws Throwable {
        assertNull(StringEscapeUtils.unescapeJavaScript(null));
        assertEquals("abc", StringEscapeUtils.unescapeJavaScript("abc"));
    }

    public void testUnescapeJavaScriptWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeJavaScript(writer, "abc");
        assertEquals("abc", writer.toString());
    }

    public void testEscapeHtml() throws Throwable {
        assertNull(StringEscapeUtils.escapeHtml(null));
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", StringEscapeUtils.escapeHtml("\"bread\" & \"butter\""));
    }

    public void testEscapeHtmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeHtml(writer, "\"bread\" & \"butter\"");
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", writer.toString());

        StringWriter nullWriter = new StringWriter();
        StringEscapeUtils.escapeHtml(nullWriter, null);
        assertEquals("", nullWriter.toString());

        try {
            StringEscapeUtils.escapeHtml(null, "abc");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testUnescapeHtml() throws Throwable {
        assertNull(StringEscapeUtils.unescapeHtml(null));
        assertEquals("\"bread\" & \"butter\"", StringEscapeUtils.unescapeHtml("&quot;bread&quot; &amp; &quot;butter&quot;"));
        assertEquals("&zzzz;x", StringEscapeUtils.unescapeHtml("&zzzz;x"));
    }

    public void testUnescapeHtmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeHtml(writer, "&quot;bread&quot; &amp; &quot;butter&quot;");
        assertEquals("\"bread\" & \"butter\"", writer.toString());

        StringWriter nullWriter = new StringWriter();
        StringEscapeUtils.unescapeHtml(nullWriter, null);
        assertEquals("", nullWriter.toString());

        try {
            StringEscapeUtils.unescapeHtml(null, "abc");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testEscapeXml() throws Throwable {
        assertNull(StringEscapeUtils.escapeXml(null));
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", StringEscapeUtils.escapeXml("\"bread\" & \"butter\""));
    }

    public void testEscapeXmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeXml(writer, "\"bread\" & \"butter\"");
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", writer.toString());

        StringWriter nullWriter = new StringWriter();
        StringEscapeUtils.escapeXml(nullWriter, null);
        assertEquals("", nullWriter.toString());

        try {
            StringEscapeUtils.escapeXml(null, "abc");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testUnescapeXml() throws Throwable {
        assertNull(StringEscapeUtils.unescapeXml(null));
        assertEquals("\"bread\" & \"butter\"", StringEscapeUtils.unescapeXml("&quot;bread&quot; &amp; &quot;butter&quot;"));
    }

    public void testUnescapeXmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeXml(writer, "&quot;bread&quot; &amp; &quot;butter&quot;");
        assertEquals("\"bread\" & \"butter\"", writer.toString());

        StringWriter nullWriter = new StringWriter();
        StringEscapeUtils.unescapeXml(nullWriter, null);
        assertEquals("", nullWriter.toString());

        try {
            StringEscapeUtils.unescapeXml(null, "abc");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testEscapeSql() throws Throwable {
        assertNull(StringEscapeUtils.escapeSql(null));
        assertEquals("McHale''s Navy", StringEscapeUtils.escapeSql("McHale's Navy"));
    }

    public void testEscapeCsv() throws Throwable {
        assertNull(StringEscapeUtils.escapeCsv(null));
        assertEquals("abc", StringEscapeUtils.escapeCsv("abc"));
        assertEquals("\"a,b\"", StringEscapeUtils.escapeCsv("a,b"));
        assertEquals("\"a\"\"b\"", StringEscapeUtils.escapeCsv("a\"b"));
        assertEquals("\"a\nb\"", StringEscapeUtils.escapeCsv("a\nb"));
        assertEquals("\"a\rb\"", StringEscapeUtils.escapeCsv("a\rb"));
    }

    public void testEscapeCsvWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeCsv(writer, "a,b");
        assertEquals("\"a,b\"", writer.toString());

        StringWriter nullWriter = new StringWriter();
        StringEscapeUtils.escapeCsv(nullWriter, null);
        assertEquals("", nullWriter.toString());
    }

    public void testUnescapeCsv() throws Throwable {
        assertNull(StringEscapeUtils.unescapeCsv(null));
        assertEquals("abc", StringEscapeUtils.unescapeCsv("abc"));
        assertEquals("a", StringEscapeUtils.unescapeCsv("a"));
        assertEquals("ab", StringEscapeUtils.unescapeCsv("ab"));
        assertEquals("abc", StringEscapeUtils.unescapeCsv("abc"));
        assertEquals("a,b", StringEscapeUtils.unescapeCsv("\"a,b\""));
        assertEquals("a\"b", StringEscapeUtils.unescapeCsv("\"a\"\"b\""));
        assertEquals("a,b", StringEscapeUtils.unescapeCsv("\"a,b\""));
        assertEquals("a", StringEscapeUtils.unescapeCsv("\"a\""));
    }

    public void testUnescapeCsvWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeCsv(writer, null);
        assertEquals("", writer.toString());

        StringWriter writer2 = new StringWriter();
        StringEscapeUtils.unescapeCsv(writer2, "a");
        assertEquals("a", writer2.toString());

        StringWriter writer3 = new StringWriter();
        StringEscapeUtils.unescapeCsv(writer3, "ab");
        assertEquals("ab", writer3.toString());

        StringWriter writer4 = new StringWriter();
        StringEscapeUtils.unescapeCsv(writer4, "abc");
        assertEquals("abc", writer4.toString());
    }
}