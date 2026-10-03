package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class DataUtilTest {

    @Test
    public void testGetCharsetFromContentType() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html;charset=utf-8"));
        assertEquals("ISO-8859-1", DataUtil.getCharsetFromContentType("text/html; charset=\"iso-8859-1\""));
        assertEquals("EUC-JP", DataUtil.getCharsetFromContentType("application/atom+xml; charset=EUC-JP"));
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("charset=utf-8"));
    }

    @Test
    public void testReadToByteBuffer() throws Throwable {
        byte[] data = "Hello, World!".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertNotNull(buffer);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertEquals("Hello, World!", new String(result, "UTF-8"));
    }

    @Test
    public void testLoadInputStream() throws Throwable {
        byte[] data = "<html><head><title>Test</title></head><body>Hello</body></html>".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.load((InputStream) in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Test", doc.title());
        assertEquals("Hello", doc.body().text());
    }

    @Test
    public void testLoadInputStreamWithParser() throws Throwable {
        byte[] data = "<root><child>Text</child></root>".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.load((InputStream) in, "UTF-8", "http://example.com", Parser.xmlParser());
        assertNotNull(doc);
        assertEquals("Text", doc.select("child").text());
    }

    @Test
    public void testParseByteDataNullCharsetAutoDetect() throws Throwable {
        byte[] data = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\"><title>Meta</title></head><body>Body</body></html>".getBytes("UTF-8");
        ByteBuffer byteData = ByteBuffer.wrap(data);
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("Meta", doc.title());
    }

    @Test
    public void testParseByteDataNullCharsetHtml5Meta() throws Throwable {
        byte[] data = "<html><head><meta charset=\"UTF-8\"><title>HTML5 Meta</title></head><body>Body</body></html>".getBytes("UTF-8");
        ByteBuffer byteData = ByteBuffer.wrap(data);
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("HTML5 Meta", doc.title());
    }

    @Test
    public void testParseByteDataNullCharsetRe-decode() throws Throwable {
        // Using a different charset in meta to trigger re-decode branch
        byte[] data = "<html><head><meta charset=\"ISO-8859-1\"><title>ISO</title></head><body>Body</body></html>".getBytes("ISO-8859-1");
        ByteBuffer byteData = ByteBuffer.wrap(data);
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("ISO", doc.title());
    }

    @Test
    public void testParseByteDataWithBOM() throws Throwable {
        // BOM for UTF-8 is 0xEF, 0xBB, 0xBF which corresponds to char 65279 if decoded or handled,
        // Let's manually prepend the char 65279 (\uFEFF) to docData simulation or test stream with BOM if possible.
        char bom = 65279;
        byte[] data = (bom + "<html><head><title>BOM</title></head><body>Body</body></html>").getBytes("UTF-8");
        ByteBuffer byteData = ByteBuffer.wrap(data);
        Document doc = DataUtil.parseByteData(byteData, "UTF-8", "http://example.com", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals("BOM", doc.title());
    }

    @Test
    public void testLoadFile() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".html");
        tempFile.deleteOnExit();
        java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile);
        out.write("<html><head><title>File Test</title></head><body>File Content</body></html>".getBytes("UTF-8"));
        out.close();

        Document doc = DataUtil.load(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("File Test", doc.title());
        assertEquals("File Content", doc.body().text());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseByteDataEmptyCharset() throws Throwable {
        byte[] data = "<html><body></body></html>".getBytes("UTF-8");
        ByteBuffer byteData = ByteBuffer.wrap(data);
        DataUtil.parseByteData(byteData, "", "http://example.com", Parser.htmlParser());
    }
}