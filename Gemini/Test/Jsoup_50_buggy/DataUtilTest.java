package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class DataUtilTest {

    @Test
    public void testCrossStreams() throws Throwable {
        byte[] inputData = "Hello, World! CrossStreams test data.".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DataUtil.crossStreams(in, out);

        byte[] resultBytes = out.toByteArray();
        assertArrayEquals(inputData, resultBytes);
    }

    @Test
    public void testReadToByteBufferInputStream() throws Throwable {
        byte[] inputData = "Test stream to byte buffer".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);

        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertNotNull(buffer);
        
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertArrayEquals(inputData, result);
    }

    @Test
    public void testReadToByteBufferWithMaxSizeUnlimited() throws Throwable {
        byte[] inputData = "Unlimited max size test".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);

        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 0);
        assertNotNull(buffer);
        assertEquals(inputData.length, buffer.remaining());
    }

    @Test
    public void testReadToByteBufferWithMaxSizeCapped() throws Throwable {
        byte[] inputData = "Capped max size test".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(inputData);

        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 6);
        assertNotNull(buffer);
        assertEquals(6, buffer.remaining());
        
        byte[] result = new byte[6];
        buffer.get(result);
        assertArrayEquals("Capped".getBytes("UTF-8"), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadToByteBufferNegativeMaxSize() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        DataUtil.readToByteBuffer(in, -1);
    }

    @Test
    public void testReadFileToByteBuffer() throws Throwable {
        File tempFile = File.createTempFile("jsoup_test", ".tmp");
        tempFile.deleteOnExit();

        OutputStream out = new FileOutputStream(tempFile);
        byte[] fileContent = "File content bytes".getBytes("UTF-8");
        out.write(fileContent);
        out.close();

        ByteBuffer buffer = DataUtil.readFileToByteBuffer(tempFile);
        assertNotNull(buffer);
        
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertArrayEquals(fileContent, result);
    }

    @Test
    public void testEmptyByteBuffer() throws Throwable {
        ByteBuffer buffer = DataUtil.emptyByteBuffer();
        assertNotNull(buffer);
        assertEquals(0, buffer.capacity());
        assertEquals(0, buffer.remaining());
    }

    @Test
    public void testGetCharsetFromContentTypeValid() throws Throwable {
        String ct1 = "text/html; charset=UTF-8";
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType(ct1));

        String ct2 = "text/html; charset=\"ISO-8859-1\"";
        assertEquals("ISO-8859-1", DataUtil.getCharsetFromContentType(ct2));

        String ct3 = "text/html; charset='euc-jp'";
        assertEquals("EUC-JP", DataUtil.getCharsetFromContentType(ct3));
    }

    @Test
    public void testGetCharsetFromContentTypeNullAndMissing() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset="));
    }

    @Test
    public void testGetCharsetFromContentTypeInvalidCharset() throws Throwable {
        String ct = "text/html; charset=invalid-charset-name-xyz";
        assertNull(DataUtil.getCharsetFromContentType(ct));
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
    public void testParseByteDataNullCharsetWithMetaHttpEquiv() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"></head><body>Hello</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("ISO-8859-1"));

        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("ISO-8859-1", doc.outputSettings().charset().name());
    }

    @Test
    public void testParseByteDataNullCharsetWithMetaCharset() throws Throwable {
        String html = "<html><head><meta charset=\"Shift_JIS\"></head><body>Hello</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("Shift_JIS"));

        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("SHIFT_JIS", doc.outputSettings().charset().name().toUpperCase());
    }

    @Test
    public void testParseByteDataNullCharsetWithInvalidMetaCharset() throws Throwable {
        String html = "<html><head><meta charset=\"invalid-charset-abc\"></head><body>Hello</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("UTF-8"));

        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("UTF-8", doc.outputSettings().charset().name());
    }

    @Test
    public void testParseByteDataWithBOM() throws Throwable {
        byte[] bomBytes = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'e', 'l', 'l', 'o' };
        ByteBuffer byteData = ByteBuffer.wrap(bomBytes);

        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertTrue(doc.text().contains("Hello"));
        assertEquals("UTF-8", doc.outputSettings().charset().name());
    }

    @Test
    public void testParseByteDataExplicitCharset() throws Throwable {
        String html = "<html><body>Explicit Charset Test</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes("UTF-16"));

        Document doc = DataUtil.parseByteData(byteData, "UTF-16", "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertTrue(doc.text().contains("Explicit Charset Test"));
        assertEquals("UTF-16", doc.outputSettings().charset().name());
    }

    @Test
    public void testLoadInputStream() throws Throwable {
        String html = "<html><body>Load InputStream Test</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));

        Document doc = DataUtil.load(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.text().contains("Load InputStream Test"));
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        String xml = "<root><element>Load XML Parser Test</element></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));

        Document doc = DataUtil.load(in, "UTF-8", "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
        assertTrue(doc.text().contains("Load XML Parser Test"));
    }

    @Test
    public void testLoadFile() throws Throwable {
        File tempFile = File.createTempFile("jsoup_load_test", ".html");
        tempFile.deleteOnExit();

        OutputStream out = new FileOutputStream(tempFile);
        byte[] content = "<html><body>Load File Test</body></html>".getBytes("UTF-8");
        out.write(content);
        out.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.text().contains("Load File Test"));
    }
}