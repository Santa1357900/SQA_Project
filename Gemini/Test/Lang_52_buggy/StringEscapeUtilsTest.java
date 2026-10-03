package org.apache.commons.lang;

import java.io.StringWriter;
import java.io.Writer;
import org.junit.Test;
import static org.junit.Assert.*;

public class StringEscapeUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        StringEscapeUtils utils = new StringEscapeUtils();
        assertNotNull(utils);
    }

    @Test
    public void testEscapeJava() throws Throwable {
        assertNull(StringEscapeUtils.escapeJava(null));
        assertEquals("test", StringEscapeUtils.escapeJava("test"));
        assertEquals("He didn\\'t say, \\\"Stop!\\\"", StringEscapeUtils.escapeJava("He didn't say, \"Stop!\""));
        assertEquals("\\t\\n\\r", StringEscapeUtils.escapeJava("\t\n\r"));
        assertEquals("\\b\\f", StringEscapeUtils.escapeJava("\b\f"));
        assertEquals("\\\\", StringEscapeUtils.escapeJava("\\"));
        assertEquals("\\u0001", StringEscapeUtils.escapeJava("\u0001"));
        assertEquals("\\u001f", StringEscapeUtils.escapeJava("\u001f"));
        assertEquals("\\u007f", StringEscapeUtils.escapeJava("\u007f"));
        assertEquals("\\u0080", StringEscapeUtils.escapeJava("\u0080"));
        assertEquals("\\u0100", StringEscapeUtils.escapeJava("\u0100"));
        assertEquals("\\u1000", StringEscapeUtils.escapeJava("\u1000"));
    }

    @Test
    public void testEscapeJavaWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeJava(writer, "He didn't say, \"Stop!\"");
        assertEquals("He didn\\'t say, \\\"Stop!\\\"", writer.toString());

        // Null string test
        StringWriter writerNull = new StringWriter();
        StringEscapeUtils.escapeJava(writerNull, (String) null);
        assertEquals("", writerNull.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEscapeJavaWriterNull() throws Throwable {
        StringEscapeUtils.escapeJava((Writer) null, "test");
    }

    @Test
    public void testEscapeJavaScript() throws Throwable {
        assertNull(StringEscapeUtils.escapeJavaScript(null));
        assertEquals("He didn\\'t say, \\\"Stop!\\\"", StringEscapeUtils.escapeJavaScript("He didn't say, \"Stop!\""));
    }

    @Test
    public void testEscapeJavaScriptWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeJavaScript(writer, "He didn't say, \"Stop!\"");
        assertEquals("He didn\\'t say, \\\"Stop!\\\"", writer.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEscapeJavaScriptWriterNull() throws Throwable {
        StringEscapeUtils.escapeJavaScript((Writer) null, "test");
    }

    @Test
    public void testUnescapeJava() throws Throwable {
        assertNull(StringEscapeUtils.unescapeJava(null));
        assertEquals("He didn't say, \"Stop!\"", StringEscapeUtils.unescapeJava("He didn\\'t say, \\\"Stop!\\\""));
        assertEquals("\t\n\r\b\f\\", StringEscapeUtils.unescapeJava("\\t\\n\\r\\b\\f\\\\"));
        assertEquals("\u0001", StringEscapeUtils.unescapeJava("\\u0001"));
        assertEquals("A", StringEscapeUtils.unescapeJava("\\u0041"));
        assertEquals("a", StringEscapeUtils.unescapeJava("\\"));
        
        // Test invalid unicode number format exception handling
        boolean exceptionThrown = false;
        try {
            StringEscapeUtils.unescapeJava("\\uZZZZ");
        } catch (Throwable e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testUnescapeJavaWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeJava(writer, "He didn\\'t say, \\\"Stop!\\\"");
        assertEquals("He didn't say, \"Stop!\"", writer.toString());

        // Null string test
        StringWriter writerNull = new StringWriter();
        StringEscapeUtils.unescapeJava(writerNull, (String) null);
        assertEquals("", writerNull.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnescapeJavaWriterNull() throws Throwable {
        StringEscapeUtils.unescapeJava((Writer) null, "test");
    }

    @Test
    public void testUnescapeJavaScript() throws Throwable {
        assertNull(StringEscapeUtils.unescapeJavaScript(null));
        assertEquals("test", StringEscapeUtils.unescapeJavaScript("test"));
    }

    @Test
    public void testUnescapeJavaScriptWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeJavaScript(writer, "test");
        assertEquals("test", writer.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnescapeJavaScriptWriterNull() throws Throwable {
        StringEscapeUtils.unescapeJavaScript((Writer) null, "test");
    }

    @Test
    public void testEscapeHtml() throws Throwable {
        assertNull(StringEscapeUtils.escapeHtml(null));
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", StringEscapeUtils.escapeHtml("\"bread\" & \"butter\""));
    }

    @Test
    public void testEscapeHtmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeHtml(writer, "\"bread\" & \"butter\"");
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", writer.toString());

        StringWriter writerNull = new StringWriter();
        StringEscapeUtils.escapeHtml(writerNull, (String) null);
        assertEquals("", writerNull.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEscapeHtmlWriterNull() throws Throwable {
        StringEscapeUtils.escapeHtml((Writer) null, "test");
    }

    @Test
    public void testUnescapeHtml() throws Throwable {
        assertNull(StringEscapeUtils.unescapeHtml(null));
        assertEquals("\"bread\" & \"butter\"", StringEscapeUtils.unescapeHtml("&quot;bread&quot; &amp; &quot;butter&quot;"));
        assertEquals("&zzzz;x", StringEscapeUtils.unescapeHtml("&zzzz;x"));
    }

    @Test
    public void testUnescapeHtmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeHtml(writer, "&quot;bread&quot; &amp; &quot;butter&quot;");
        assertEquals("\"bread\" & \"butter\"", writer.toString());

        StringWriter writerNull = new StringWriter();
        StringEscapeUtils.unescapeHtml(writerNull, (String) null);
        assertEquals("", writerNull.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnescapeHtmlWriterNull() throws Throwable {
        StringEscapeUtils.unescapeHtml((Writer) null, "test");
    }

    @Test
    public void testEscapeXml() throws Throwable {
        assertNull(StringEscapeUtils.escapeXml((String) null));
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", StringEscapeUtils.escapeXml("\"bread\" & \"butter\""));
    }

    @Test
    public void testEscapeXmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.escapeXml(writer, "\"bread\" & \"butter\"");
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;", writer.toString());

        StringWriter writerNull = new StringWriter();
        StringEscapeUtils.escapeXml(writerNull, (String) null);
        assertEquals("", writerNull.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEscapeXmlWriterNull() throws Throwable {
        StringEscapeUtils.escapeXml((Writer) null, "test");
    }

    @Test
    public void testUnescapeXml() throws Throwable {
        assertNull(StringEscapeUtils.unescapeXml((String) null));
        assertEquals("\"bread\" & \"butter\"", StringEscapeUtils.unescapeXml("&quot;bread&quot; &amp; &quot;butter&quot;"));
    }

    @Test
    public void testUnescapeXmlWriter() throws Throwable {
        StringWriter writer = new StringWriter();
        StringEscapeUtils.unescapeXml(writer, "&quot;bread&quot; &amp; &quot;butter&quot;");
        assertEquals("\"bread\" & \"butter\"", writer.toString());

        StringWriter writerNull = new StringWriter();
        StringEscapeUtils.unescapeXml(writerNull, (String) null);
        assertEquals("", writerNull.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnescapeXmlWriterNull() throws Throwable {
        StringEscapeUtils.unescapeXml((Writer) null, "test");
    }

    @Test
    public void testEscapeSql() throws Throwable {
        assertNull(StringEscapeUtils.escapeSql(null));
        assertEquals("McHale''s Navy", StringEscapeUtils.escapeSql("McHale's Navy"));
    }
}