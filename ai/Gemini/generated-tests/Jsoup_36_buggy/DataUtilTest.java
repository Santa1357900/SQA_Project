package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class DataUtilTest {

    @Test
    public void testGetCharsetFromContentTypeNull() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType(null);
        assertNull(charset);
    }

    @Test
    public void testGetCharsetFromContentTypeValid() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType("text/html; charset=UTF-8");
        assertEquals("UTF-8", charset);
    }

    @Test
    public void testGetCharsetFromContentTypeQuoted() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType("text/html; charset=\"ISO-8859-1\"");
        assertEquals("ISO-8859-1", charset);
    }

    @Test
    public void testGetCharsetFromContentTypeUnsupportedFallbackToUpperCase() throws Throwable {
        // If charset is unsupported lowercase, but supported uppercase (or invalid entirely)
        // Let's test with a valid standard one like utf-8 vs UTF-8
        String charset = DataUtil.getCharsetFromContentType("text/html; charset=utf-8");
        assertEquals("utf-8", charset);
    }

    @Test
    public void testGetCharsetFromContentTypeNotFound() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType("text/html");
        assertNull(charset);
    }

    @Test
    public void testReadToByteBufferUnlimited() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 0);
        assertNotNull(buffer);
        assertEquals(data.length, buffer.remaining());
    }

    @Test
    public void testReadToByteBufferCapped() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 5);
        assertNotNull(buffer);
        assertEquals(5, buffer.remaining());
    }

    @Test
    public void testReadToByteBufferCappedLargerThanStream() throws Throwable {
        byte[] data = "Hello".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 10);
        assertNotNull(buffer);
        assertEquals(5, buffer.remaining());
    }

    @Test
    public void testReadToByteBufferSimple() throws Throwable {
        byte[] data = "Simple test".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertNotNull(buffer);
        assertEquals(data.length, buffer.remaining());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadToByteBufferInvalidMax() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        DataUtil.readToByteBuffer(in, -1);
    }

    @Test
    public void testLoadInputStreamBasic() throws Throwable {
        String html = "<html><head><title>Test</title></head><body>Hello</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Test", doc.title());
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        String xml = "<root><child>Text</child></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
        assertEquals("Text", doc.select("child").text());
    }

    @Test
    public void testLoadInputStreamNullCharsetAutoDetect() throws Throwable {
        String html = "<html><head><meta charset=\"UTF-8\"><title>Auto</title></head><body></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
        assertEquals("Auto", doc.title());
    }

    @Test
    public void testLoadInputStreamNullCharsetWithHttpEquiv() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"><title>HttpEquiv</title></head><body></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
        assertEquals("HttpEquiv", doc.title());
    }

    @Test
    public void testLoadInputStreamWithBOM() throws Throwable {
        // BOM for UTF-8 is 0xEF, 0xBB, 0xBF, but code checks char at 0 == 65279 ('\uFEFF')
        String html = "\uFEFF<html><head><title>BOM</title></head><body></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("BOM", doc.title());
    }

    @Test
    public void testLoadFile() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".html");
        tempFile.deleteOnExit();
        
        FileOutputStream out = new FileOutputStream(tempFile);
        out.write("<html><head><title>FileTest</title></head><body>File Content</body></html>".getBytes("UTF-8"));
        out.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("FileTest", doc.title());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseByteDataEmptyCharsetArg() throws Throwable {
        ByteBuffer byteData = ByteBuffer.wrap("<html></html>".getBytes("UTF-8"));
        DataUtil.parseByteData(byteData, "", "http://example.com", Parser.htmlParser());
    }
}