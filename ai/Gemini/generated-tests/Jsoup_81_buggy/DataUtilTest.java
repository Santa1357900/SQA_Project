package org.jsoup.helper;

import org.junit.Test;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.XmlDeclaration;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class DataUtilTest {

    @Test
    public void testCrossStreams() throws Throwable {
        byte[] inputData = "Hello, CrossStreams!".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        DataUtil.crossStreams(in, out);

        byte[] result = out.toByteArray();
        assertArrayEquals(inputData, result);
    }

    @Test
    public void testParseInputStreamNullInput() throws Throwable {
        Document doc = DataUtil.parseInputStream(null, "UTF-8", "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("http://example.com", doc.baseUri());
    }

    @Test
    public void testParseInputStreamWithBomUtf8() throws Throwable {
        byte[] bomData = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '<', 'h', 't', 'm', 'l', '>', '<', 'b', 'o', 'd', 'y', '>', 'H', 'i', '<', '/', 'b', 'o', 'd', 'y', '>', '<', '/', 'h', 't', 'm', 'l', '>' };
        InputStream in = new ByteArrayInputStream(bomData);
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("UTF-8", doc.outputSettings().charset().name());
    }

    @Test
    public void testParseInputStreamWithBomUtf16Be() throws Throwable {
        byte[] bomData = new byte[] { (byte) 0xFE, (byte) 0xFF, 0, '<', 0, 'h', 0, 't', 0, 'm', 0, 'l', 0, '>', 0, '<', 0, '/', 0, 'h', 0, 't', 0, 'm', 0, 'l', 0, '>' };
        InputStream in = new ByteArrayInputStream(bomData);
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
    }

    @Test
    public void testParseInputStreamWithMetaCharset() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body>Some text with special chars </body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
    }

    @Test
    public void testParseInputStreamWithMetaHttpEquiv() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;charset=Shift_JIS\"></head><body>Body</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
    }

    @Test
    public void testParseInputStreamWithXmlDeclaration() throws Throwable {
        String xml = "<?xml encoding=\"ISO-8859-1\"?><root>Data</root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
    }

    @Test
    public void testParseInputStreamWithExplicitCharset() throws Throwable {
        String html = "<html><body>Explicit Charset</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, "UTF-8", "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseInputStreamEmptyCharset() throws Throwable {
        String html = "<html><body>Empty Charset Arg</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        DataUtil.parseInputStream(in, "", "http://example.com", Parser.htmlParser());
    }

    @Test
    public void testReadToByteBufferWithMaxSize() throws Throwable {
        byte[] data = "1234567890".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 5);
        assertEquals(5, buffer.remaining());
    }

    @Test
    public void testReadToByteBufferUnlimited() throws Throwable {
        byte[] data = "1234567890".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 0);
        assertEquals(10, buffer.remaining());
    }

    @Test
    public void testReadFileToByteBuffer() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".tmp");
        tempFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write("File Content Test".getBytes("UTF-8"));
        fos.close();

        ByteBuffer buffer = DataUtil.readFileToByteBuffer(tempFile);
        assertNotNull(buffer);
        assertTrue(buffer.remaining() > 0);
    }

    @Test
    public void testEmptyByteBuffer() throws Throwable {
        ByteBuffer buf = DataUtil.emptyByteBuffer();
        assertNotNull(buf);
        assertEquals(0, buf.capacity());
    }

    @Test
    public void testGetCharsetFromContentType() throws Throwable {
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html;charset=UTF-8"));
        assertEquals("ISO-8859-1", DataUtil.getCharsetFromContentType("text/html; charset=\"ISO-8859-1\""));
        assertNull(DataUtil.getCharsetFromContentType(null));
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=invalid-charset-name-xyz"));
    }

    @Test
    public void testMimeBoundary() throws Throwable {
        String boundary = DataUtil.mimeBoundary();
        assertNotNull(boundary);
        assertEquals(32, boundary.length());
    }

    @Test
    public void testLoadFile() throws Throwable {
        File tempFile = File.createTempFile("jsoup-load-test", ".html");
        tempFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write("<html><head><title>Test File</title></head><body>Hello</body></html>".getBytes("UTF-8"));
        fos.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Test File", doc.title());
    }

    @Test
    public void testLoadInputStream() throws Throwable {
        InputStream in = new ByteArrayInputStream("<html><body>Stream</body></html>".getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Stream", doc.body().text());
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        InputStream in = new ByteArrayInputStream("<root>XML Stream</root>".getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
    }
}