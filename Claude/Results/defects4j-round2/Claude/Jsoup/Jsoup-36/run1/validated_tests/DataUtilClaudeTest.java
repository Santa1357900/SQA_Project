package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import org.junit.Test;
import static org.junit.Assert.*;

public class DataUtilClaudeTest {

    // load(InputStream,...): null charset triggers default UTF-8 detection and parses ASCII content correctly
    @Test
    public void testLoad_inputStream_nullCharset_detectsDefaultUTF8() throws Throwable {
        String html = "<html><head><title>Test</title></head><body><p>Hello</p></body></html>";
        ByteArrayInputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, null, "http://example.com/");
        assertEquals("Hello", doc.select("p").first().text());
    }

    // load(InputStream,...): explicit charset name is used directly to decode bytes
    @Test
    public void testLoad_inputStream_explicitCharset_usesGivenCharset() throws Throwable {
        String html = "<html><body><p>caf\u00e9</p></body></html>";
        ByteArrayInputStream in = new ByteArrayInputStream(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.load(in, "ISO-8859-1", "http://example.com/");
        assertEquals("caf\u00e9", doc.select("p").first().text());
    }

    // load(InputStream,...,Parser): alternate parser (xml) is used to parse the input
    @Test
    public void testLoad_inputStream_withXmlParser_parsesAsXml() throws Throwable {
        String xml = "<root><child>Value</child></root>";
        ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/", Parser.xmlParser());
        assertEquals("Value", doc.select("child").first().text());
    }

    // load(InputStream,...) wrapper: null charset detects HTML5 meta charset attribute via delegation
    @Test
    public void testLoad_inputStream_nullCharsetWithMetaCharset_detectsFromMetaViaWrapper() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body>caf\u00e9</body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        Document doc = DataUtil.load(in, null, "http://example.com/");
        assertEquals("caf\u00e9", doc.body().text());
    }

    // parseByteData: meta http-equiv content-type with charset in content triggers correct re-decode
    @Test
    public void testParseByteData_metaHttpEquivWithCharsetInContent_redecodesCorrectly() throws Throwable {
        String html = "<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=ISO-8859-1\"></head><body>caf\u00e9</body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.body().text());
    }

    // parseByteData: HTML5 <meta charset=...> attribute alone triggers correct re-decode
    @Test
    public void testParseByteData_metaCharsetHtml5Attribute_redecodesCorrectly() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body>caf\u00e9</body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.body().text());
    }

    // parseByteData: found charset equals default (UTF-8) skips re-decode branch but still parses correctly
    @Test
    public void testParseByteData_metaCharsetEqualsDefault_noRedecodeNeeded() throws Throwable {
        String html = "<html><head><meta charset=\"UTF-8\"></head><body>Hello</body></html>";
        byte[] bytes = html.getBytes("UTF-8");
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hello", doc.body().text());
    }

    // parseByteData: no meta tag present, stays on default UTF-8 decode
    @Test
    public void testParseByteData_noMetaTag_staysDefaultUTF8() throws Throwable {
        String html = "<html><body>Hello</body></html>";
        byte[] bytes = html.getBytes("UTF-8");
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hello", doc.body().text());
    }

    // parseByteData: explicit non-null charsetName bypasses meta-detection entirely
    @Test
    public void testParseByteData_explicitCharsetName_bypassesMetaDetection() throws Throwable {
        String html = "<html><body>caf\u00e9</body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), "ISO-8859-1", "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.body().text());
    }

    // parseByteData: empty charsetName triggers Validate.notEmpty -> IllegalArgumentException
    @Test
    public void testParseByteData_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        byte[] bytes = "<html></html>".getBytes("UTF-8");
        try {
            DataUtil.parseByteData(ByteBuffer.wrap(bytes), "", "http://example.com/", Parser.htmlParser());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseByteData: leading UTF-8 BOM is stripped before parsing
    @Test
    public void testParseByteData_bomPresent_stripsBomBeforeParsing() throws Throwable {
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] htmlBytes = "<html><body>Hello</body></html>".getBytes("UTF-8");
        byte[] combined = new byte[bom.length + htmlBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(htmlBytes, 0, combined, bom.length, htmlBytes.length);
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(combined), "UTF-8", "http://example.com/", Parser.htmlParser());
        assertEquals("Hello", doc.body().text());
    }

    // BUG: meta has http-equiv but content lacks a parseable charset; per spec should fall back to charset attribute
    @Test
    public void testParseByteData_metaHttpEquivMissingCharsetButHasCharsetAttr_fallsBackToCharsetAttr() throws Throwable {
        String html = "<html><head><meta http-equiv=\"content-type\" content=\"text/html\" charset=\"ISO-8859-1\"></head><body>caf\u00e9</body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.body().text());
    }

    // parseByteData: meta charset attribute present but empty skips re-decode (length()==0 check)
    @Test
    public void testParseByteData_metaCharsetAttrEmpty_noRedecode() throws Throwable {
        String html = "<html><head><meta charset=\"\"></head><body>Hello</body></html>";
        byte[] bytes = html.getBytes("UTF-8");
        Document doc = DataUtil.parseByteData(ByteBuffer.wrap(bytes), null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hello", doc.body().text());
    }

    // readToByteBuffer: negative maxSize triggers Validate.isTrue -> IllegalArgumentException
    @Test
    public void testReadToByteBuffer_negativeMaxSize_throwsIllegalArgumentException() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream("abc".getBytes("UTF-8"));
        try {
            DataUtil.readToByteBuffer(in, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // readToByteBuffer: maxSize 0 means unlimited, reads the full stream
    @Test
    public void testReadToByteBuffer_maxSizeZero_readsEntireStream() throws Throwable {
        byte[] data = "HelloWorld".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 0);
        assertEquals(data.length, buf.limit());
    }

    // readToByteBuffer: maxSize smaller than stream length caps output at maxSize
    @Test
    public void testReadToByteBuffer_maxSizeLessThanStreamLength_capsOutput() throws Throwable {
        byte[] data = "HelloWorld".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 5);
        assertEquals(5, buf.limit());
    }

    // readToByteBuffer: maxSize larger than stream length reads entire stream (no padding)
    @Test
    public void testReadToByteBuffer_maxSizeGreaterThanStreamLength_readsEntireStream() throws Throwable {
        byte[] data = "Hi".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 100);
        assertEquals(2, buf.limit());
    }

    // readToByteBuffer: maxSize exactly equal to stream length reads exactly that many bytes
    @Test
    public void testReadToByteBuffer_maxSizeEqualsStreamLength_readsExact() throws Throwable {
        byte[] data = "Hello".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 5);
        assertEquals(5, buf.limit());
    }

    // readToByteBuffer(InputStream) single-arg delegates to unlimited read
    @Test
    public void testReadToByteBuffer_singleArgDelegate_readsUnlimited() throws Throwable {
        byte[] data = "DelegateTest".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in);
        assertEquals(data.length, buf.limit());
    }

    // getCharsetFromContentType: null input returns null
    @Test
    public void testGetCharsetFromContentType_nullInput_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
    }

    // getCharsetFromContentType: no charset token present returns null
    @Test
    public void testGetCharsetFromContentType_noCharsetPresent_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
    }

    // getCharsetFromContentType: standard javadoc example extracts charset value
    @Test
    public void testGetCharsetFromContentType_standardExample_returnsCharset() throws Throwable {
        assertEquals("EUC-JP", DataUtil.getCharsetFromContentType("text/html; charset=EUC-JP"));
    }

    // getCharsetFromContentType: quoted charset value is extracted without quotes
    @Test
    public void testGetCharsetFromContentType_quotedCharset_returnsUnquotedValue() throws Throwable {
        assertEquals("UTF-8", DataUtil.getCharsetFromContentType("text/html; charset=\"UTF-8\""));
    }

    // getCharsetFromContentType: charset stops correctly at trailing semicolon boundary
    @Test
    public void testGetCharsetFromContentType_charsetFollowedBySemicolon_returnsCharset() throws Throwable {
        assertEquals("Shift_JIS", DataUtil.getCharsetFromContentType("text/html;charset=Shift_JIS;boundary=xyz"));
    }

    // getCharsetFromContentType: unsupported charset name returns null after both case attempts fail
    @Test
    public void testGetCharsetFromContentType_unsupportedCharset_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=not-a-real-charset-zzz"));
    }
}
