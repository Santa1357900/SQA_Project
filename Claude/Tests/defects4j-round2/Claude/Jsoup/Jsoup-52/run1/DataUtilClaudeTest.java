package org.jsoup.helper;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class DataUtilClaudeTest {

    // covers load(InputStream,String,String) with charsetName==null branch, no meta charset -> default UTF-8
    @Test
    public void testLoad_simpleHtmlNullCharset_parsesDocumentWithDefaultUtf8() throws Throwable {
        String html = "<html><head><title>Hello</title></head><body><p>World</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, null, "http://example.com/");
        assertEquals("Hello", doc.title());
    }

    // covers charsetName != null (else) branch with explicit ISO-8859-1 decode
    @Test
    public void testLoad_explicitCharsetName_decodesLatin1Correctly() throws Throwable {
        String html = "<html><body><p>caf\u00e9</p></body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        InputStream in = new ByteArrayInputStream(bytes);
        Document doc = DataUtil.load(in, "ISO-8859-1", "http://example.com/");
        Element p = doc.select("p").first();
        assertEquals("caf\u00e9", p.text());
    }

    // covers Validate.notEmpty throw branch when charsetName is empty string
    @Test
    public void testLoad_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        byte[] bytes = "<html></html>".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(bytes);
        try {
            DataUtil.load(in, "", "http://example.com/");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers load(InputStream,String,String,Parser) overload using xmlParser
    @Test
    public void testLoadWithParser_xmlParserBasic_parsesRootElement() throws Throwable {
        String xml = "<root><child>value</child></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/", Parser.xmlParser());
        Element child = doc.select("child").first();
        assertEquals("value", child.text());
    }

    // covers meta.hasAttr("http-equiv") branch calling getCharsetFromContentType
    @Test
    public void testParseByteData_metaHttpEquivContentType_detectsAndDecodesCharset() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"></head><body><p>caf\u00e9</p></body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.htmlParser());
        Element p = doc.select("p").first();
        assertEquals("caf\u00e9", p.text());
    }

    // covers meta.hasAttr("charset") HTML5-style meta charset branch
    @Test
    public void testParseByteData_metaCharsetAttribute_detectsAndDecodesCharset() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body><p>caf\u00e9</p></body></html>";
        byte[] bytes = html.getBytes("ISO-8859-1");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.htmlParser());
        Element p = doc.select("p").first();
        assertEquals("caf\u00e9", p.text());
    }

    // covers Charset.isSupported(...) false branch, foundCharset stays null, default UTF-8 kept
    @Test
    public void testParseByteData_unsupportedMetaCharset_fallsBackToDefaultUtf8() throws Throwable {
        String html = "<html><head><meta charset=\"totally-bogus-charset-xyz\"></head><body><p>Hello</p></body></html>";
        byte[] bytes = html.getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.htmlParser());
        Element p = doc.select("p").first();
        assertEquals("Hello", p.text());
    }

    // covers path where meta select returns null entirely
    @Test
    public void testParseByteData_noMetaInformation_defaultsToUtf8() throws Throwable {
        String html = "<html><body><p>Hello</p></body></html>";
        byte[] bytes = html.getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.htmlParser());
        Element p = doc.select("p").first();
        assertEquals("Hello", p.text());
    }

    // covers detectCharsetFromBom UTF-8 branch and BOM-skip via byteData.position(3)
    @Test
    public void testParseByteData_utf8Bom_stripsBomAndDecodesCorrectly() throws Throwable {
        String html = "<html><head><title>Hi</title></head></html>";
        byte[] htmlBytes = html.getBytes("UTF-8");
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] combined = new byte[bom.length + htmlBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(htmlBytes, 0, combined, bom.length, htmlBytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(combined);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hi", doc.title());
    }

    // covers detectCharsetFromBom UTF-16 branch (BE BOM produced by getBytes("UTF-16"))
    @Test
    public void testParseByteData_utf16Bom_decodesCorrectly() throws Throwable {
        String html = "<html><head><title>Hi</title></head><body>Hello</body></html>";
        byte[] bytes = html.getBytes("UTF-16");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hi", doc.title());
    }

    // covers remaining < 4 bytes branch in detectCharsetFromBom, must not crash
    @Test
    public void testParseByteData_shortInputBelowBomLength_noExceptionDefaultUtf8() throws Throwable {
        byte[] bytes = "<a>".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.htmlParser());
        assertNotNull(doc);
    }

    // covers XmlDeclaration prolog encoding detection branch
    @Test
    public void testParseByteData_xmlDeclarationEncoding_detectsAndDecodesCharset() throws Throwable {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root>caf\u00e9</root>";
        byte[] bytes = xml.getBytes("ISO-8859-1");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Document doc = DataUtil.parseByteData(buffer, null, "http://example.com/", Parser.xmlParser());
        Element root = doc.select("root").first();
        assertEquals("caf\u00e9", root.text());
    }

    // covers Validate.notEmpty throw in else branch directly on parseByteData
    @Test
    public void testParseByteData_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        byte[] bytes = "<html></html>".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try {
            DataUtil.parseByteData(buffer, "", "http://example.com/", Parser.htmlParser());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers crossStreams loop with multiple read/write iterations
    @Test
    public void testCrossStreams_copiesAllBytesExactly() throws Throwable {
        byte[] data = new byte[0x20000 + 100];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 251);
        InputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertArrayEquals(data, out.toByteArray());
    }

    // covers crossStreams loop with zero iterations (immediate -1)
    @Test
    public void testCrossStreams_emptyInput_writesNothing() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertEquals(0, out.toByteArray().length);
    }

    // covers readToByteBuffer(InputStream) overload delegating maxSize=0 (unlimited)
    @Test
    public void testReadToByteBuffer_noLimit_readsFullStream() throws Throwable {
        byte[] data = "Hello World".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertArrayEquals(data, result);
    }

    // covers capped branch: read > remaining triggers cap and break
    @Test
    public void testReadToByteBuffer_maxSizeSmallerThanInput_capsOutputAtMaxSize() throws Throwable {
        byte[] data = "Hello World".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 5);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertEquals("Hello", new String(result, "UTF-8"));
    }

    // covers capped branch boundary: read == remaining exactly (not > remaining)
    @Test
    public void testReadToByteBuffer_maxSizeEqualsInputLength_readsAllBytes() throws Throwable {
        byte[] data = "Hello".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, data.length);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertArrayEquals(data, result);
    }

    // covers capped=false branch (maxSize==0 means unlimited)
    @Test
    public void testReadToByteBuffer_zeroMaxSize_meansUnlimited() throws Throwable {
        byte[] data = "Some longer content here".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in, 0);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        assertArrayEquals(data, result);
    }

    // covers Validate.isTrue(maxSize >= 0, ...) throw branch
    @Test
    public void testReadToByteBuffer_negativeMaxSize_throwsIllegalArgumentException() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
        try {
            DataUtil.readToByteBuffer(in, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers loop with zero read iterations producing empty buffer
    @Test
    public void testReadToByteBuffer_emptyStream_returnsEmptyBuffer() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertEquals(0, buffer.remaining());
    }

    // covers contentType == null branch
    @Test
    public void testGetCharsetFromContentType_null_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
    }

    // covers m.find() == false branch (no charset in content type)
    @Test
    public void testGetCharsetFromContentType_noCharsetPresent_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
    }

    // covers m.find() true, unquoted charset value branch
    @Test
    public void testGetCharsetFromContentType_simpleValue_returnsCharset() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=UTF-8");
        assertTrue(Charset.forName(result).equals(Charset.forName("UTF-8")));
    }

    // covers optional double-quote group in regex, quotes stripped
    @Test
    public void testGetCharsetFromContentType_doubleQuotedValue_stripsQuotes() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=\"UTF-8\"");
        assertFalse(result.contains("\""));
        assertTrue(Charset.forName(result).equals(Charset.forName("UTF-8")));
    }

    // covers optional single-quote group in regex, quotes stripped
    @Test
    public void testGetCharsetFromContentType_singleQuotedValue_stripsQuotes() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset='UTF-8'");
        assertFalse(result.contains("'"));
        assertTrue(Charset.forName(result).equals(Charset.forName("UTF-8")));
    }

    // covers validateCharset returning null for an unsupported charset name
    @Test
    public void testGetCharsetFromContentType_unsupportedCharsetName_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=totally-bogus-charset-xyz"));
    }

    // covers (?i) case-insensitive matching of the "charset" keyword
    @Test
    public void testGetCharsetFromContentType_uppercaseCharsetKeyword_matchesCaseInsensitively() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; CHARSET=utf-8");
        assertTrue(Charset.forName(result).equals(Charset.forName("UTF-8")));
    }

    // covers group(1) captured as empty string -> validateCharset returns null (length 0)
    @Test
    public void testGetCharsetFromContentType_emptyCharsetValue_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset="));
    }

    // covers emptyByteBuffer() returning a zero-length buffer
    @Test
    public void testEmptyByteBuffer_hasZeroCapacityAndRemaining() throws Throwable {
        ByteBuffer buffer = DataUtil.emptyByteBuffer();
        assertEquals(0, buffer.capacity());
        assertEquals(0, buffer.remaining());
    }

    // covers mimeBoundary() loop producing boundaryLength chars from the allowed alphabet
    @Test
    public void testMimeBoundary_hasExpectedLengthAndValidCharacters() throws Throwable {
        String boundary = DataUtil.mimeBoundary();
        assertEquals(DataUtil.boundaryLength, boundary.length());
        String allowed = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < boundary.length(); i++) {
            assertTrue(allowed.indexOf(boundary.charAt(i)) >= 0);
        }
    }
}
