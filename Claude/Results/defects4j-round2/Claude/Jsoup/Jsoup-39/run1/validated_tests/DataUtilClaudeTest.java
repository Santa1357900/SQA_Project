package org.jsoup.helper;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

public class DataUtilClaudeTest {

    // charsetName explicitly supplied -> else branch, direct decode, no meta lookup
    @Test
    public void testParseByteData_explicitCharset_decodesAscii() throws Throwable {
        String html = "<html><head><title>Test</title></head><body>Hello World</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes(Charset.forName("UTF-8")));
        Document doc = DataUtil.parseByteData(byteData, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertEquals("Test", doc.select("title").first().text());
    }

    // charsetName null, no meta tag present -> defaults to UTF-8
    @Test
    public void testParseByteData_nullCharsetNoMeta_defaultsUtf8() throws Throwable {
        String html = "<html><head><title>Test</title></head><body>Hello World</body></html>";
        ByteBuffer byteData = ByteBuffer.wrap(html.getBytes(Charset.forName("UTF-8")));
        Document doc = DataUtil.parseByteData(byteData, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Test", doc.select("title").first().text());
    }

    // meta http-equiv content-type with different charset than default -> redecode branch
    @Test
    public void testParseByteData_metaHttpEquivDifferentCharset_redecodesCorrectly() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"><title>T</title></head><body><p>caf\u00e9</p></body></html>";
        byte[] bytes = html.getBytes(Charset.forName("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.select("p").first().text());
    }

    // meta http-equiv charset equals default (UTF-8) -> no redecode, content still correct
    @Test
    public void testParseByteData_metaHttpEquivSameAsDefault_noCrashCorrectText() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>T</title></head><body><p>caf\u00e9</p></body></html>";
        byte[] bytes = html.getBytes(Charset.forName("UTF-8"));
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.select("p").first().text());
    }

    // meta http-equiv with unsupported charset -> getCharsetFromContentType returns null, falls back to default, no crash
    @Test
    public void testParseByteData_metaHttpEquivInvalidCharset_fallsBackToDefault() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=totally-bogus-charset-xyz\"><title>T</title></head><body>Hello</body></html>";
        byte[] bytes = html.getBytes(Charset.forName("UTF-8"));
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("T", doc.select("title").first().text());
        assertEquals("Hello", doc.select("body").first().text());
    }

    // meta http-equiv content without charset, but separate charset attribute present -> fallback branch redecode
    @Test
    public void testParseByteData_metaHttpEquivNoCharsetButSeparateCharsetAttr_redecodes() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html\" charset=\"ISO-8859-1\"><title>T</title></head><body><p>na\u00efve</p></body></html>";
        byte[] bytes = html.getBytes(Charset.forName("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("na\u00efve", doc.select("p").first().text());
    }

    // HTML5 style meta charset attribute, different from default -> redecode branch (else of hasAttr http-equiv)
    @Test
    public void testParseByteData_html5MetaCharset_redecodesCorrectly() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"><title>T</title></head><body><p>caf\u00e9</p></body></html>";
        byte[] bytes = html.getBytes(Charset.forName("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.select("p").first().text());
    }

    // HTML5 meta charset empty value -> length()==0 skips redecode, stays default
    @Test
    public void testParseByteData_html5MetaCharsetEmpty_noRedecode() throws Throwable {
        String html = "<html><head><meta charset=\"\"><title>T</title></head><body>Hello</body></html>";
        byte[] bytes = html.getBytes(Charset.forName("UTF-8"));
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hello", doc.select("body").first().text());
    }



    // UTF-8 BOM prefix -> BOM stripped, content still decoded correctly
    @Test
    public void testParseByteData_utf8Bom_stripsBomKeepsCorrectTitle() throws Throwable {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        String html = "<html><head><title>Hi</title></head><body>Hello</body></html>";
        byte[] htmlBytes = html.getBytes(Charset.forName("UTF-8"));
        byte[] combined = new byte[bom.length + htmlBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(htmlBytes, 0, combined, bom.length, htmlBytes.length);
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(combined), null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hi", doc.select("title").first().text());
    }

    // empty charsetName -> Validate.notEmpty throws IllegalArgumentException
    @Test
    public void testParseByteData_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        byte[] bytes = "<html></html>".getBytes(Charset.forName("UTF-8"));
        try {
            DataUtil.parseByteData(ByteBuffer.wrap(bytes), "", "http://example.com/", Parser.htmlParser());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // maxSize 0 (unlimited) -> reads all available bytes
    @Test
    public void testReadToByteBuffer_unlimited_readsAllBytes() throws Throwable {
        byte[] data = new byte[500];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 128);
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 0);
        assertEquals(500, buf.remaining());
    }

    // maxSize smaller than content -> caps output at maxSize
    @Test
    public void testReadToByteBuffer_cappedSmallerThanContent_readsOnlyMaxSize() throws Throwable {
        byte[] data = new byte[50];
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 10);
        assertEquals(10, buf.remaining());
    }

    // maxSize larger than content -> reads all content, no truncation
    @Test
    public void testReadToByteBuffer_cappedLargerThanContent_readsAllBytes() throws Throwable {
        byte[] data = new byte[20];
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 100);
        assertEquals(20, buf.remaining());
    }

    // negative maxSize -> Validate.isTrue throws IllegalArgumentException
    @Test
    public void testReadToByteBuffer_negativeMaxSize_throwsIllegalArgumentException() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        try {
            DataUtil.readToByteBuffer(in, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // zero-length stream -> loop exits immediately (0 iterations), empty buffer
    @Test
    public void testReadToByteBuffer_emptyStream_returnsEmptyBuffer() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 0);
        assertEquals(0, buf.remaining());
    }

    // content larger than internal buffer size forces multiple read loop iterations
    @Test
    public void testReadToByteBuffer_largeContentMultipleBufferIterations_readsAllBytes() throws Throwable {
        byte[] data = new byte[150000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) 1;
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 0);
        assertEquals(150000, buf.remaining());
    }

    // default overload delegates to capped overload with maxSize 0
    @Test
    public void testReadToByteBufferDefaultOverload_readsAllBytes() throws Throwable {
        byte[] data = new byte[100];
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in);
        assertEquals(100, buf.remaining());
    }

    // null contentType -> returns null immediately
    @Test
    public void testGetCharsetFromContentType_null_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
    }

    // no charset token in contentType -> regex does not match -> null
    @Test
    public void testGetCharsetFromContentType_noCharsetPresent_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
    }

    // valid supported uppercase charset -> returned as-is
    @Test
    public void testGetCharsetFromContentType_validUppercaseCharset_returnsCharset() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=UTF-8");
        assertEquals("UTF-8", result);
    }

    // double-quoted charset value -> quotes excluded by regex character class
    @Test
    public void testGetCharsetFromContentType_doubleQuotedCharset_returnsCharsetWithoutQuotes() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=\"UTF-8\"");
        assertEquals("UTF-8", result);
    }

    // unsupported charset name -> isSupported false both original and uppercased -> null
    @Test
    public void testGetCharsetFromContentType_unsupportedCharset_returnsNull() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=totally-bogus-charset-xyz");
        assertNull(result);
    }

    // single-quoted charset value -> quotes excluded by regex character class
    @Test
    public void testGetCharsetFromContentType_singleQuotedCharset_returnsCharset() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset='UTF-8'");
        assertEquals("UTF-8", result);
    }

    // load(InputStream, charset, baseUri) basic happy path
    @Test
    public void testLoadInputStream_basic_parsesDocument() throws Throwable {
        String html = "<html><head><title>LoadTest</title></head><body>Content</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes(Charset.forName("UTF-8")));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/");
        assertEquals("LoadTest", doc.select("title").first().text());
    }

    // load(InputStream, null, baseUri) -> null charset path defaults to UTF-8
    @Test
    public void testLoadInputStream_nullCharset_defaultsUtf8() throws Throwable {
        String html = "<html><head><title>NoCharset</title></head><body>Data</body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes(Charset.forName("UTF-8")));
        Document doc = DataUtil.load(in, null, "http://example.com/");
        assertEquals("NoCharset", doc.select("title").first().text());
    }

    // load(InputStream, charset, baseUri, parser) with xml parser preserves custom tag as-is
    @Test
    public void testLoadInputStreamWithParser_xmlParser_parsesAsXml() throws Throwable {
        String xml = "<greeting>Hello</greeting>";
        InputStream in = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/", Parser.xmlParser());
        assertEquals("Hello", doc.select("greeting").first().text());
    }

    // load(InputStream, "", baseUri) -> propagates IllegalArgumentException from parseByteData
    @Test
    public void testLoadInputStream_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        String html = "<html></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes(Charset.forName("UTF-8")));
        try {
            DataUtil.load(in, "", "http://example.com/");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }
}
