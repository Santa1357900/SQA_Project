package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
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
        String charset = DataUtil.getCharsetFromContentType("text/html;charset=utf-8");
        assertEquals("utf-8", charset);
    }

    @Test
    public void testGetCharsetFromContentTypeWithQuotes() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType("text/html; charset=\"UTF-8\"");
        assertEquals("UTF-8", charset);
    }

    @Test
    public void testGetCharsetFromContentTypeUnsupported() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType("text/html; charset=not-a-real-charset");
        assertNull(charset);
    }

    @Test
    public void testGetCharsetFromContentTypeEmpty() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType("text/html; charset=");
        assertNull(charset);
    }

    @Test
    public void testReadToByteBuffer() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertNotNull(buffer);
        assertEquals("Hello, World!", new String(buffer.array(), 0, buffer.remaining(), "UTF-8"));
    }

    @Test
    public void testReadToByteBufferCapped() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 5);
        assertNotNull(buffer);
        assertEquals("Hello", new String(buffer.array(), 0, buffer.remaining(), "UTF-8"));
    }

    @Test
    public void testLoadInputStream() throws Throwable {
        byte[] data = "<html><head><title>Test</title></head><body>Hello</body></html>".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Test", doc.title());
    }

    @Test
    public void testLoadInputStreamAutoDetectCharset() throws Throwable {
        byte[] data = "<html><head><meta charset=\"UTF-8\"><title>Auto</title></head></html>".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
        assertEquals("Auto", doc.title());
    }

    @Test
    public void testLoadInputStreamHttpEquivCharset() throws Throwable {
        byte[] data = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\"><title>HttpEquiv</title></head></html>".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
        assertEquals("HttpEquiv", doc.title());
    }

    @Test
    public void testLoadInputStreamWithBOM() throws Throwable {
        byte[] bomData = new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF, '<', 'h', 't', 'm', 'l', '>', '<', 'h', 'e', 'a', 'd', '>', '<', 't', 'i', 't', 'l', 'e', '>', 'B', 'O', 'M', '<', '/', 't', 'i', 't', 'l', 'e', '>', '<', '/', 'h', 'e', 'a', 'd', '>', '<', '/', 'h', 't', 'm', 'l', '>' };
        ByteArrayInputStream in = new ByteArrayInputStream(bomData);
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
        assertEquals("BOM", doc.title());
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        byte[] data = "<root><elem>Value</elem></root>".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
        assertEquals("Value", doc.select("elem").text());
    }

    @Test
    public void testLoadFile() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".html");
        tempFile.deleteOnExit();
        
        FileOutputStream out = new FileOutputStream(tempFile);
        out.write("<html><head><title>FileTest</title></head></html>".getBytes("UTF-8"));
        out.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("FileTest", doc.title());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadToByteBufferInvalidMaxSize() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        DataUtil.readToByteBuffer(in, -1);
    }
}