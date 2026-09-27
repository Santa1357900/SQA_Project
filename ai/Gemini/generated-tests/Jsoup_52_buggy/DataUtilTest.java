package org.jsoup.helper;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class DataUtilTest {

    @Test
    public void testCrossStreams() throws Throwable {
        byte[] inputData = "Hello, World! This is a test for crossStreams.".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        DataUtil.crossStreams(in, out);

        byte[] outputData = out.toByteArray();
        assertArrayEquals(inputData, outputData);
    }

    @Test
    public void testReadToByteBuffer() throws Throwable {
        byte[] inputData = "ByteBuffer test content.".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);

        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertNotNull(buffer);
        
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertArrayEquals(inputData, result);
    }

    @Test
    public void testReadToByteBufferWithMaxSize() throws Throwable {
        byte[] inputData = "1234567890".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);

        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 5);
        assertNotNull(buffer);
        assertEquals(5, buffer.remaining());

        byte[] result = new byte[5];
        buffer.get(result);
        assertArrayEquals("12345".getBytes("UTF-8"), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadToByteBufferInvalidMaxSize() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        DataUtil.readToByteBuffer(in, -1);
    }

    @Test
    public void testReadFileToByteBuffer() throws Throwable {
        File tempFile = File.createTempFile("jsoup_test", ".txt");
        tempFile.deleteOnExit();

        java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
        fos.write("File content test".getBytes("UTF-8"));
        fos.close();

        ByteBuffer buffer = DataUtil.readFileToByteBuffer(tempFile);
        assertNotNull(buffer);
        
        String content = Charset.forName("UTF-8").decode(buffer).toString();
        assertEquals("File content test", content);
    }

    @Test
    public void testEmptyByteBuffer() throws Throwable {
        ByteBuffer buffer = DataUtil.emptyByteBuffer();
        assertNotNull(buffer);
        assertEquals(0, buffer.capacity());
        assertEquals(0, buffer.remaining());
    }

    @Test
    public void testGetCharsetFromContentType() throws Throwable {
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html; charset=utf-8"));
        assertEquals("ISO-8859-1", DataUtil.getCharsetFromContentType("text/html; charset=\"iso-8859-1\""));
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html;charset=UTF-8"));
        assertNull(DataUtil.getCharsetFromContentType(null));
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=invalid-charset-name-xyz"));
    }

    @Test
    public void testMimeBoundary() throws Throwable {
        String boundary1 = DataUtil.mimeBoundary();
        String boundary2 = DataUtil.mimeBoundary();

        assertNotNull(boundary1);
        assertNotNull(boundary2);
        assertEquals(DataUtil.boundaryLength, boundary1.length());
        assertEquals(DataUtil.boundaryLength, boundary2.length());
        assertFalse(boundary1.equals(boundary2));
    }

    @Test
    public void testLoadInputStream() throws Throwable {
        String html = "<html><head><meta charset=\"UTF-8\"></head><body>Hello</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, null, "http://example.com");

        assertNotNull(doc);
        assertTrue(doc.text().contains("Hello"));
    }

    @Test
    public void testLoadInputStreamWithCharset() throws Throwable {
        String html = "<html><head></head><body>Hello UTF-16</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-16"));
        Document doc = DataUtil.load(in, "UTF-16", "http://example.com");

        assertNotNull(doc);
        assertTrue(doc.text().contains("Hello UTF-16"));
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        String html = "<root><child>text</child></root>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com", Parser.xmlParser());

        assertNotNull(doc);
        assertEquals("text", doc.select("child").text());
    }

    @Test
    public void testParseByteDataWithBomUtf8() throws Throwable {
        byte[] bomData = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '<', 'h', 't', 'm', 'l', '>', '<', 'h', 'e', 'a', 'd', '>', '<', '/', 'h', 'e', 'a', 'd', '>', '<', 'b', 'o', 'd', 'y', '>', 'B', 'O', 'M', '<', '/', 'b', 'o', 'd', 'y', '>', '<', '/', 'h', 't', 'm', 'l', '>' };
        ByteBuffer byteData = ByteBuffer.wrap(bomData);
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());

        assertNotNull(doc);
        assertTrue(doc.text().contains("BOM"));
    }

    @Test
    public void testParseByteDataWithBomUtf16Be() throws Throwable {
        byte[] bomData = new byte[] { (byte) 0xFE, (byte) 0xFF, 0, '<', 0, 'h', 0, 't', 0, 'm', 0, 'l', 0, '>' };
        ByteBuffer byteData = ByteBuffer.wrap(bomData);
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
    }

    @Test
    public void testParseByteDataWithHttpEquiv() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"></head><body>ISO</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());

        assertNotNull(doc);
        assertEquals("ISO-8859-1", doc.outputSettings().charset().name());
    }

    @Test
    public void testParseByteDataWithXmlDeclaration() throws Throwable {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root>XML</root>";
        ByteBuffer byteData = ByteBuffer.wrap(xml.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.xmlParser());

        assertNotNull(doc);
        assertEquals("ISO-8859-1", doc.outputSettings().charset().name());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseByteDataBlankCharset() throws Throwable {
        ByteBuffer byteData = ByteBuffer.wrap("test".getBytes("UTF-8"));
        DataUtil.parseByteData(byteData, "", "http://example.com", Parser.htmlParser());
    }

    @Test
    public void testLoadFile() throws Throwable {
        File tempFile = File.createTempFile("jsoup_file_test", ".html");
        tempFile.deleteOnExit();

        java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
        fos.write("<html><body>File Load Test</body></html>".getBytes("UTF-8"));
        fos.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.text().contains("File Load Test"));
    }
}