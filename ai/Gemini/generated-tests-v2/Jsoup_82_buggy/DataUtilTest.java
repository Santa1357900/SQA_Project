package org.jsoup.helper;

import org.junit.Test;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class DataUtilTest {

    @Test
    public void testLoadNullInputStream() throws Throwable {
        Document doc = DataUtil.load((InputStream) null, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("http://example.com", doc.baseUri());
    }

    @Test
    public void testLoadInputStreamWithCharset() throws Throwable {
        String html = "<html><head><title>Test</title></head><body>Hello</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Test", doc.title());
    }

    @Test
    public void testLoadInputStreamAutoDetectCharset() throws Throwable {
        String html = "<html><head><meta charset=\"UTF-8\"><title>Auto</title></head><body>Body</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
        assertEquals("Auto", doc.title());
    }

    @Test
    public void testLoadInputStreamHttpEquivCharset() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\"><title>HttpEquiv</title></head><body>Body</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Parser parser = Parser.htmlParser();
        Document doc = DataUtil.load(in, null, "http://example.com", parser);
        assertNotNull(doc);
        assertEquals("HttpEquiv", doc.title());
    }

    @Test
    public void testLoadInputStreamXmlDeclaration() throws Throwable {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><child>Data</child></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Parser parser = Parser.xmlParser();
        Document doc = DataUtil.load(in, null, "http://example.com", parser);
        assertNotNull(doc);
    }

    @Test
    public void testLoadFile() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".html");
        tempFile.deleteOnExit();
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("<html><head><title>FileTest</title></head><body>File Content</body></html>");
        writer.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("FileTest", doc.title());
    }

    @Test
    public void testReadToByteBuffer() throws Throwable {
        String content = "Hello ByteBuffer";
        InputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"));
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 100);
        assertNotNull(buffer);
        assertTrue(buffer.remaining() > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadToByteBufferInvalidMaxSize() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        DataUtil.readToByteBuffer(in, -1);
    }

    @Test
    public void testEmptyByteBuffer() throws Throwable {
        ByteBuffer buf = DataUtil.emptyByteBuffer();
        assertNotNull(buf);
        assertEquals(0, buf.capacity());
    }

    @Test
    public void testGetCharsetFromContentType() throws Throwable {
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html; charset=UTF-8"));
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html; charset=\"UTF-8\""));
        assertNull(DataUtil.getCharsetFromContentType(null));
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=INVALID-CHARSET-XYZ"));
    }

    @Test
    public void testMimeBoundary() throws Throwable {
        String boundary1 = DataUtil.mimeBoundary();
        String boundary2 = DataUtil.mimeBoundary();
        assertNotNull(boundary1);
        assertNotNull(boundary2);
        assertEquals(32, boundary1.length());
        assertEquals(32, boundary2.length());
    }

    @Test
    public void testCrossStreams() throws Throwable {
        String inputData = "Cross stream test data";
        ByteArrayInputStream in = new ByteArrayInputStream(inputData.getBytes("UTF-8"));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        
        // Invoke package-private crossStreams via reflection or direct call if in same package
        DataUtil.crossStreams(in, out);
        
        assertEquals(inputData, new String(out.toByteArray(), "UTF-8"));
    }

    @Test
    public void testBomDetectionUtf8() throws Throwable {
        byte[] bomData = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'i' };
        InputStream in = new ByteArrayInputStream(bomData);
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
    }

    @Test
    public void testBomDetectionUtf16Be() throws Throwable {
        byte[] bomData = new byte[] { (byte) 0xFE, (byte) 0xFF, 0x00, 'H' };
        InputStream in = new ByteArrayInputStream(bomData);
        Document doc = DataUtil.load(in, null, "http://example.com");
        assertNotNull(doc);
    }
}