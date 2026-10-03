package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class DataUtilClaudeTest {

    private static byte[] bytes(String s) throws IOException {
        return s.getBytes("UTF-8");
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] r = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        System.arraycopy(c, 0, r, a.length + b.length, c.length);
        return r;
    }

    private static String repeatChar(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    // load(InputStream, charsetName, baseUri): normal html parse + baseUri propagation
    @Test
    public void testLoadInputStream_threeArg_parsesHtmlAndSetsBaseUri() throws Throwable {
        InputStream in = new ByteArrayInputStream(bytes("<html><body>Hello</body></html>"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/");
        assertEquals("Hello", doc.body().text());
        assertEquals("http://example.com/", doc.baseUri());
    }

    // load(InputStream, charsetName, baseUri, parser): parser argument is actually used (xml keeps tag case)
    @Test
    public void testLoadInputStream_fourArgWithXmlParser_preservesCaseSensitiveTagName() throws Throwable {
        InputStream in = new ByteArrayInputStream(bytes("<Foo>Bar</Foo>"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/", Parser.xmlParser());
        Elements els = doc.select("Foo");
        assertEquals(1, els.size());
        assertEquals("Bar", els.first().text());
    }

    // crossStreams: while loop runs multiple times, all bytes copied exactly
    @Test
    public void testCrossStreams_copiesAllBytes() throws Throwable {
        byte[] data = bytes("abcdef12345");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertArrayEquals(data, out.toByteArray());
    }

    // crossStreams: while loop runs zero times on empty input
    @Test
    public void testCrossStreams_emptyInput_writesNothing() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertEquals(0, out.toByteArray().length);
    }

    // parseInputStream: input == null -> empty Document with the given baseUri
    @Test
    public void testParseInputStream_nullInput_returnsEmptyDocumentWithBaseUri() throws Throwable {
        Document doc = DataUtil.parseInputStream(null, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertEquals("http://example.com/", doc.baseUri());
        assertEquals(0, doc.childNodeSize());
    }

    // parseInputStream: explicit charset (ISO-8859-1) used to decode a non-ascii byte correctly
    @Test
    public void testParseInputStream_explicitCharset_decodesBodyCorrectly() throws Throwable {
        byte[] data = concat(bytes("<html><body>"), new byte[]{(byte) 0xE9}, bytes("</body></html>"));
        InputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.parseInputStream(in, "ISO-8859-1", "http://example.com/", Parser.htmlParser());
        assertEquals("\u00E9", doc.body().text());
    }

    // parseInputStream: empty-string charsetName must be rejected (Validate.notEmpty contract)
    @Test
    public void testParseInputStream_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        InputStream in = new ByteArrayInputStream(bytes("<html></html>"));
        try {
            DataUtil.parseInputStream(in, "", "http://example.com/", Parser.htmlParser());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // parseInputStream: UTF-8 BOM detected, overrides null charsetName and is stripped from body text
    @Test
    public void testParseInputStream_utf8Bom_detectsCharsetAndStripsBom() throws Throwable {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] data = concat(bom, bytes("<html><body>hi</body></html>"), new byte[0]);
        InputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("hi", doc.body().text());
        assertTrue(doc.outputSettings().charset().name().equalsIgnoreCase("UTF-8"));
    }

    // parseInputStream: meta http-equiv content-type charset found -> reparsed with detected charset
    @Test
    public void testParseInputStream_metaHttpEquivCharset_reparsesWithDetectedCharset() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">"
                + "</head><body>hi</body></html>";
        InputStream in = new ByteArrayInputStream(bytes(html));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("hi", doc.body().text());
        assertTrue(doc.outputSettings().charset().name().equalsIgnoreCase("ISO-8859-1"));
    }

    // parseInputStream: html5 <meta charset="..."> attribute found directly -> reparsed
    @Test
    public void testParseInputStream_metaCharsetAttribute_detectsCharsetDirectly() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body>hi</body></html>";
        InputStream in = new ByteArrayInputStream(bytes(html));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("hi", doc.body().text());
        assertTrue(doc.outputSettings().charset().name().equalsIgnoreCase("ISO-8859-1"));
    }

    // parseInputStream: no charset found anywhere, small fully-read content keeps initial UTF-8 parse
    @Test
    public void testParseInputStream_noCharsetFoundSmallContent_keepsParsedBody() throws Throwable {
        InputStream in = new ByteArrayInputStream(bytes("<html><body>hello world</body></html>"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("hello world", doc.body().text());
    }

    // parseInputStream: <?xml encoding='...'?> prolog charset detected -> reparsed with that charset
    @Test
    public void testParseInputStream_xmlDeclarationEncoding_reparsesWithDetectedCharset() throws Throwable {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root>hi</root>";
        InputStream in = new ByteArrayInputStream(bytes(xml));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.xmlParser());
        Elements root = doc.select("root");
        assertEquals("hi", root.text());
        assertTrue(doc.outputSettings().charset().name().equalsIgnoreCase("ISO-8859-1"));
    }

    // parseInputStream: content larger than the first read buffer must be read in full, not truncated
    @Test
    public void testParseInputStream_largeContentBeyondFirstBuffer_readsFullContent() throws Throwable {
        String body = repeatChar('a', 40000);
        String html = "<html><body>" + body + "</body></html>";
        InputStream in = new ByteArrayInputStream(bytes(html));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals(40000, doc.body().text().length());
    }

    // parseInputStream: explicit UTF-8 charset decodes a multi-byte character correctly
    @Test
    public void testParseInputStream_explicitUtf8MultibyteCharacter_decodesCorrectly() throws Throwable {
        byte[] data = concat(bytes("<html><body>caf"), new byte[]{(byte) 0xC3, (byte) 0xA9}, bytes("</body></html>"));
        InputStream in = new ByteArrayInputStream(data);
        Document doc = DataUtil.parseInputStream(in, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00E9", doc.body().text());
    }

    // readToByteBuffer: negative maxSize violates Validate.isTrue contract
    @Test
    public void testReadToByteBuffer_negativeMaxSize_throwsIllegalArgumentException() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes("data"));
        try {
            DataUtil.readToByteBuffer(in, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // readToByteBuffer: maxSize 0 means unlimited, full content is read
    @Test
    public void testReadToByteBuffer_unlimitedMaxSize_readsFullContent() throws Throwable {
        byte[] data = bytes("Hello World");
        ByteBuffer bb = DataUtil.readToByteBuffer(new ByteArrayInputStream(data), 0);
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        assertEquals("Hello World", new String(out, "UTF-8"));
    }

    // readToByteBuffer: maxSize smaller than available data truncates the result to maxSize
    @Test
    public void testReadToByteBuffer_maxSizeSmallerThanData_truncatesToMaxSize() throws Throwable {
        byte[] data = bytes("0123456789");
        ByteBuffer bb = DataUtil.readToByteBuffer(new ByteArrayInputStream(data), 5);
        assertEquals(5, bb.remaining());
    }

    // readToByteBuffer: maxSize exactly equal to data length reads it all without truncation
    @Test
    public void testReadToByteBuffer_maxSizeEqualsDataLength_readsExactData() throws Throwable {
        byte[] data = bytes("exactlyten");
        ByteBuffer bb = DataUtil.readToByteBuffer(new ByteArrayInputStream(data), data.length);
        assertEquals(data.length, bb.remaining());
    }

    // readToByteBuffer(InputStream): package-private overload delegates to unlimited read
    @Test
    public void testReadToByteBufferNoMaxSizeArg_readsFullContent() throws Throwable {
        byte[] data = bytes("package-private-overload");
        ByteBuffer bb = DataUtil.readToByteBuffer(new ByteArrayInputStream(data));
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        assertEquals("package-private-overload", new String(out, "UTF-8"));
    }

    // emptyByteBuffer: zero-capacity, zero-remaining buffer
    @Test
    public void testEmptyByteBuffer_hasZeroRemaining() throws Throwable {
        ByteBuffer bb = DataUtil.emptyByteBuffer();
        assertEquals(0, bb.remaining());
        assertEquals(0, bb.capacity());
    }

    // getCharsetFromContentType: null input returns null
    @Test
    public void testGetCharsetFromContentType_nullInput_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
    }

    // getCharsetFromContentType: regex finds no match when there is no charset param
    @Test
    public void testGetCharsetFromContentType_noCharsetParam_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
    }

    // getCharsetFromContentType: simple charset value is extracted and validated as-is
    @Test
    public void testGetCharsetFromContentType_simpleCharset_returnsCharset() throws Throwable {
        String cs = DataUtil.getCharsetFromContentType("text/html; charset=iso-8859-1");
        assertEquals("iso-8859-1", cs);
    }

    // getCharsetFromContentType: quoted charset value has quotes stripped by the capture group
    @Test
    public void testGetCharsetFromContentType_quotedCharset_returnsUnquotedCharset() throws Throwable {
        String cs = DataUtil.getCharsetFromContentType("text/html; charset=\"ISO-8859-1\"");
        assertEquals("ISO-8859-1", cs);
    }

    // getCharsetFromContentType: unsupported charset name fails validateCharset and returns null
    @Test
    public void testGetCharsetFromContentType_unsupportedCharset_returnsNull() throws Throwable {
        String cs = DataUtil.getCharsetFromContentType("text/html; charset=not-a-real-charset-xyz");
        assertNull(cs);
    }

    // getCharsetFromContentType: \b word boundary prevents matching "charset=" inside another word
    @Test
    public void testGetCharsetFromContentType_charsetWithoutWordBoundary_returnsNull() throws Throwable {
        String cs = DataUtil.getCharsetFromContentType("notcharset=UTF-8");
        assertNull(cs);
    }

    // getCharsetFromContentType: \s* allows whitespace right after charset= and value is trimmed
    @Test
    public void testGetCharsetFromContentType_extraWhitespaceAfterEquals_returnsTrimmedCharset() throws Throwable {
        String cs = DataUtil.getCharsetFromContentType("charset=   utf-16");
        assertEquals("utf-16", cs);
    }

    // getCharsetFromContentType: trailing ';' terminates the capture group before extra params
    @Test
    public void testGetCharsetFromContentType_trailingSemicolon_returnsCharsetWithoutSemicolon() throws Throwable {
        String cs = DataUtil.getCharsetFromContentType("text/html; charset=UTF-8;boundary=xyz");
        assertEquals("UTF-8", cs);
    }

    // mimeBoundary: result has the declared boundaryLength and only uses allowed characters
    @Test
    public void testMimeBoundary_lengthAndCharacterSet_areValid() throws Throwable {
        String allowed = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String boundary = DataUtil.mimeBoundary();
        assertEquals(DataUtil.boundaryLength, boundary.length());
        for (int i = 0; i < boundary.length(); i++) {
            assertTrue(allowed.indexOf(boundary.charAt(i)) >= 0);
        }
    }
}
