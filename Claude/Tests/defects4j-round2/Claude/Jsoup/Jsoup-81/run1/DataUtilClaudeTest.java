package org.jsoup.helper;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class DataUtilClaudeTest {

    // Covers: load(File,...) when underlying FileInputStream fails to open a missing file
    @Test
    public void testLoad_fileNotFound_throwsIOException() throws Throwable {
        File missing = new File("non_existent_dir_xyz/missing_file_12345.html");
        try {
            DataUtil.load(missing, "UTF-8", "http://example.com/");
            fail("expected FileNotFoundException");
        } catch (FileNotFoundException expected) {
        }
    }

    // Covers: load(InputStream, charsetName, baseUri) public entry with explicit charset
    @Test
    public void testLoad_inputStreamWithCharset_parsesDocument() throws Throwable {
        String html = "<html><head><title>Hello</title></head><body><p>World</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/");
        assertEquals("Hello", doc.title());
        assertEquals("World", doc.body().text());
    }

    // Covers: load(InputStream, charsetName, baseUri, parser) overload using an alternate Parser
    @Test
    public void testLoad_inputStreamWithParser_usesXmlParser() throws Throwable {
        String xml = "<root><child>value</child></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/", Parser.xmlParser());
        assertEquals("value", doc.select("child").text());
    }

    // Covers: crossStreams loop executing zero iterations (empty input stream)
    @Test
    public void testCrossStreams_emptyStream_writesNothing() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertEquals(0, out.size());
    }

    // Covers: crossStreams loop with data, verifying exact byte-for-byte copy
    @Test
    public void testCrossStreams_withData_copiesAllBytes() throws Throwable {
        byte[] data = "Hello crossStreams".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertArrayEquals(data, out.toByteArray());
    }

    // Covers: parseInputStream input==null branch, returns an empty Document
    @Test
    public void testParseInputStream_nullInput_returnsEmptyDocument() throws Throwable {
        Document doc = DataUtil.parseInputStream(null, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertNotNull(doc);
        assertEquals(0, doc.childNodeSize());
    }

    // Covers: charsetName specified but empty -> Validate.notEmpty rejects it
    @Test
    public void testParseInputStream_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        InputStream in = new ByteArrayInputStream("<html></html>".getBytes("UTF-8"));
        try {
            DataUtil.parseInputStream(in, "", "http://example.com/", Parser.htmlParser());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: charsetName explicitly specified branch, skips meta/BOM detection entirely
    @Test
    public void testParseInputStream_explicitCharset_parsesCorrectly() throws Throwable {
        String html = "<html><body><p>Explicit</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertEquals("Explicit", doc.body().text());
    }

    // Covers: detectCharsetFromBom UTF-8 BOM branch (offset=true), overriding null charsetName
    @Test
    public void testParseInputStream_utf8Bom_detectsAndStripsBom() throws Throwable {
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = "<html><body><p>BomTest</p></body></html>".getBytes("UTF-8");
        byte[] full = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, full, 0, bom.length);
        System.arraycopy(body, 0, full, bom.length, body.length);
        InputStream in = new ByteArrayInputStream(full);
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("BomTest", doc.body().text());
    }

    // Covers: HTML5 <meta charset> detection, triggering re-decode when charset != default UTF-8
    @Test
    public void testParseInputStream_metaCharsetHtml5_overridesDefaultAndReparses() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body><p>MetaCharset</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("MetaCharset", doc.body().text());
    }

    // Covers: meta[http-equiv=content-type] branch calling getCharsetFromContentType
    @Test
    public void testParseInputStream_metaHttpEquivContentType_detectsCharset() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">"
                + "</head><body><p>HttpEquiv</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("HttpEquiv", doc.body().text());
    }

    // Covers: <?xml encoding='...'?> declaration detection branch (decl.name() == "xml")
    @Test
    public void testParseInputStream_xmlDeclarationEncoding_detected() throws Throwable {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root><value>XmlDecl</value></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.xmlParser());
        assertEquals("XmlDecl", doc.select("value").text());
    }

    // Covers: no meta/xml charset found, content fully read on first pass -> reuse initial parse
    @Test
    public void testParseInputStream_shortContentFullyRead_reusesFirstParse() throws Throwable {
        String html = "<html><body><p>Short</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Short", doc.body().text());
    }

    // Covers: content exceeds first-read buffer (not fullyRead) -> full re-parse of entire content
    @Test
    public void testParseInputStream_largeContentNotFullyRead_reparsesFullContent() throws Throwable {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("0123456789");
        }
        String marker = "ENDMARKER";
        String html = "<html><body><p>" + sb.toString() + marker + "</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseInputStream(in, null, "http://example.com/", Parser.htmlParser());
        assertTrue(doc.body().text().contains(marker));
    }

    // Covers: Validate.isTrue(maxSize >= 0) branch - negative maxSize rejected
    @Test
    public void testReadToByteBuffer_negativeMaxSize_throwsIllegalArgumentException() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        try {
            DataUtil.readToByteBuffer(in, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: maxSize == 0 means unlimited read of the entire stream
    @Test
    public void testReadToByteBuffer_zeroMaxSizeUnlimited_readsEntireStream() throws Throwable {
        byte[] data = "full content data".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 0);
        byte[] actual = new byte[buf.remaining()];
        buf.get(actual);
        assertArrayEquals(data, actual);
    }

    // Covers: maxSize > 0 limits number of bytes read to exactly maxSize
    @Test
    public void testReadToByteBuffer_positiveMaxSizeLimitsRead_truncatesContent() throws Throwable {
        byte[] data = "0123456789".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in, 5);
        byte[] actual = new byte[buf.remaining()];
        buf.get(actual);
        assertEquals(5, actual.length);
        assertArrayEquals(new byte[] { '0', '1', '2', '3', '4' }, actual);
    }

    // Covers: package-private readToByteBuffer(InputStream) overload delegating to maxSize=0
    @Test
    public void testReadToByteBuffer_noMaxSizeOverload_readsEntireStream() throws Throwable {
        byte[] data = "overload test".getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(data);
        ByteBuffer buf = DataUtil.readToByteBuffer(in);
        byte[] actual = new byte[buf.remaining()];
        buf.get(actual);
        assertArrayEquals(data, actual);
    }

    // Covers: emptyByteBuffer() returns a zero-capacity buffer
    @Test
    public void testEmptyByteBuffer_returnsZeroCapacityBuffer() throws Throwable {
        ByteBuffer buf = DataUtil.emptyByteBuffer();
        assertEquals(0, buf.capacity());
        assertEquals(0, buf.remaining());
    }

    // Covers: getCharsetFromContentType contentType == null branch
    @Test
    public void testGetCharsetFromContentType_nullInput_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
    }

    // Covers: pattern not found (m.find() false) branch
    @Test
    public void testGetCharsetFromContentType_noCharsetPresent_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
    }

    // Covers: validateCharset first isSupported(cs) true branch, already-correct casing
    @Test
    public void testGetCharsetFromContentType_alreadyUppercaseSupported_returnsAsIs() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=UTF-8");
        assertEquals("UTF-8", result);
    }

    // BUG HUNT: Javadoc states "Charset is trimmed and uppercased." lowercase input must come back uppercased
    @Test
    public void testGetCharsetFromContentType_lowercaseCharset_returnsUppercasedPerJavadocContract() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=utf-8");
        assertEquals("UTF-8", result);
    }

    // Covers: pattern's optional quote group around the charset value
    @Test
    public void testGetCharsetFromContentType_quotedCharset_returnsValue() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=\"UTF-8\"");
        assertEquals("UTF-8", result);
    }

    // Covers: regex capture stops at semicolon delimiter, ignoring trailing params
    @Test
    public void testGetCharsetFromContentType_trailingSemicolon_capturesCharsetOnly() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("charset=UTF-8; boundary=something");
        assertEquals("UTF-8", result);
    }

    // Covers: validateCharset both isSupported checks false -> returns null for unknown charset
    @Test
    public void testGetCharsetFromContentType_unsupportedCharset_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=not-a-real-charset-zzz"));
    }

    // Covers: mimeBoundary() produces a boundaryLength string drawn from mimeBoundaryChars
    @Test
    public void testMimeBoundary_lengthAndCharsetValid() throws Throwable {
        String allowed = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String boundary = DataUtil.mimeBoundary();
        assertEquals(32, boundary.length());
        for (int i = 0; i < boundary.length(); i++) {
            assertTrue(allowed.indexOf(boundary.charAt(i)) >= 0);
        }
    }
}
