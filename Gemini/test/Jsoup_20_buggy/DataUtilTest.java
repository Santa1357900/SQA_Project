package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class DataUtilTest {

    @Test
    public void testGetCharsetFromContentTypeNull() throws Throwable {
        String result = DataUtil.getCharsetFromContentType(null);
        assertNull(result);
    }

    @Test
    public void testGetCharsetFromContentTypeValidHtml5() throws Throwable {
        String contentType = "text/html; charset=utf-8";
        String charset = DataUtil.getCharsetFromContentType(contentType);
        assertEquals("UTF-8", charset);
    }

    @Test
    public void testGetCharsetFromContentTypeHttpEquiv() throws Throwable {
        String contentType = "text/html;charset=\"ISO-8859-1\"";
        String charset = DataUtil.getCharsetFromContentType(contentType);
        assertEquals("ISO-8859-1", charset);
    }

    @Test
    public void testGetCharsetFromContentTypeNoMatch() throws Throwable {
        String contentType = "text/plain";
        String charset = DataUtil.getCharsetFromContentType(contentType);
        assertNull(charset);
    }

    @Test
    public void testReadToByteBuffer() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertNotNull(buffer);
        assertEquals(data.length, buffer.remaining());
    }

    @Test
    public void testParseByteDataWithNullCharset() throws Throwable {
        String html = "<html><head><meta charset=\"UTF-8\"></head><body>Hello</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("Hello", doc.body().text());
    }

    @Test
    public void testParseByteDataWithMetaCharsetSwitch() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"></head><body>Café</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
    }

    @Test
    public void testParseByteDataWithSpecifiedCharset() throws Throwable {
        String html = "<html><head></head><body>Specified Charset</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(byteData, "UTF-8", "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("Specified Charset", doc.body().text());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseByteDataWithEmptyCharset() throws Throwable {
        String html = "<html><body>Test</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("UTF-8"));
        DataUtil.parseByteData(byteData, "", "http://example.com", Parser.htmlParser());
    }

    @Test
    public void testLoadInputStream() throws Throwable {
        String html = "<html><body>Stream Load</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Stream Load", doc.body().text());
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        String html = "<root><child>XML Load</child></root>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
        assertEquals("XML Load", doc.select("child").text());
    }

    @Test
    public void testLoadFileNonExistent() throws Throwable {
        File nonExistent = new File("non_existent_file_12345.html");
        try {
            DataUtil.load(nonExistent, "UTF-8", "http://example.com");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e != null);
        }
    }
}