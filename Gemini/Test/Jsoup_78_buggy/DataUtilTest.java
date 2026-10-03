package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class DataUtilTest {

    @Test
    public void testLoadFileWithCharsetAndBaseUri() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".html");
        tempFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write("<html><head><title>Test File</title></head><body>Hello</body></html>".getBytes("UTF-8"));
        fos.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Test File", doc.title());
        assertEquals("http://example.com", doc.baseUri());
    }

    @Test
    public void testLoadInputStreamWithCharset() throws Throwable {
        String html = "<html><head><meta charset=\"UTF-8\"></head><body>Stream Test</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.body().text().contains("Stream Test"));
    }

    @Test
    public void testLoadInputStreamWithoutCharset() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"></head><body>ISO Test</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        String xml = "<root><child>value</child></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
        assertEquals("value", doc.select("child").text());
    }

    @Test
    public void testLoadNullInputStream() throws Throwable {
        Document doc = DataUtil.load((InputStream) null, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("http://example.com", doc.baseUri());
    }

    @Test
    public void testCrossStreams() throws Throwable {
        byte[] data = "Cross streams test data".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        DataUtil.crossStreams(in, out);
        byte[] result = out.toByteArray();
        assertArrayEquals(data, result);
    }

    @Test
    public void testReadToByteBufferWithMaxSize() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 5);
        assertNotNull(buffer);
        assertEquals(5, buffer.remaining());
    }

    @Test
    public void testReadToByteBufferDefault() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertNotNull(buffer);
        assertEquals(data.length, buffer.remaining());
    }

    @Test
    public void testReadFileToByteBuffer() throws Throwable {
        File tempFile = File.createTempFile("jsoup-buf", ".txt");
        tempFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write("File Buffer Test".getBytes("UTF-8"));
        fos.close();

        ByteBuffer buffer = DataUtil.readFileToByteBuffer(tempFile);
        assertNotNull(buffer);
        assertTrue(buffer.remaining() > 0);
    }

    @Test
    public void testEmptyByteBuffer() throws Throwable {
        ByteBuffer buffer = DataUtil.emptyByteBuffer();
        assertNotNull(buffer);
        assertEquals(0, buffer.capacity());
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
    public void testGetCharsetFromContentTypeNull() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType(null);
        assertNull(charset);
    }

    @Test
    public void testGetCharsetFromContentTypeNotFound() throws Throwable {
        String charset = DataUtil.getCharsetFromContentType("text/html; foo=bar");
        assertNull(charset);
    }

    @Test
    public void testMimeBoundary() throws Throwable {
        String boundary1 = DataUtil.mimeBoundary();
        String boundary2 = DataUtil.mimeBoundary();
        assertNotNull(boundary1);
        assertNotNull(boundary2);
        assertEquals(DataUtil.boundaryLength, boundary1.length());
        assertFalse(boundary1.equals(boundary2));
    }

    @Test
    public void testParseInputStreamWithBomUtf8() throws Throwable {
        byte[] bomBytes = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '<', 'h', 't', 'm', 'l', '>', 'O', 'K', '<', '/', 'h', 't', 'm', 'l', '>' };
        InputStream in = new ByteArrayInputStream(bomBytes);
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.body().text().contains("OK"));
    }

    @Test
    public void testParseInputStreamWithXmlDeclaration() throws Throwable {
        String xml = "<?xml encoding='ISO-8859-1'?><root>Prolog Test</root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("ISO-8859-1"));
        Document doc = DataUtil.load(in, null, "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadToByteBufferInvalidMaxSize() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        DataUtil.readToByteBuffer(in, -1);
    }
}