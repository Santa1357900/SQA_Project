package org.jsoup.helper;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

public class DataUtilClaudeTest {

    // covers: contentType == null branch, returns null immediately
    @Test
    public void testGetCharsetFromContentType_nullInput_returnsNull() throws Throwable {
        String result = DataUtil.getCharsetFromContentType(null);
        assertNull(result);
    }

    // covers: matcher.find() == false branch (no charset keyword present)
    @Test
    public void testGetCharsetFromContentType_noCharsetPresent_returnsNull() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html");
        assertNull(result);
    }

    // covers: matcher.find() == true, group extraction, already-uppercase charset
    @Test
    public void testGetCharsetFromContentType_standardCharset_returnsUppercaseTrimmed() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=EUC-JP");
        assertEquals("EUC-JP", result);
    }

    // covers: quoted charset value stripped of quotes by regex group
    @Test
    public void testGetCharsetFromContentType_quotedCharset_stripsQuotes() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=\"ISO-8859-1\"");
        assertEquals("ISO-8859-1", result);
    }

    // covers: toUpperCase() conversion applied to matched charset group
    @Test
    public void testGetCharsetFromContentType_lowercaseCharset_returnsUppercase() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=utf-8");
        assertEquals("UTF-8", result);
    }

    // covers: regex boundary stopping at whitespace character (\s in char class)
    @Test
    public void testGetCharsetFromContentType_charsetFollowedBySpace_extractsUpToSpace() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("charset=UTF-8 other=value");
        assertEquals("UTF-8", result);
    }

    // covers: matcher.find() false on empty input string
    @Test
    public void testGetCharsetFromContentType_emptyString_returnsNull() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("");
        assertNull(result);
    }

    // covers: while loop 0 iterations (read == -1 immediately on empty stream)
    @Test
    public void testReadToByteBuffer_emptyInputStream_returnsEmptyBuffer() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertEquals(0, buffer.remaining());
    }

    // covers: while loop single iteration, read < bufferSize
    @Test
    public void testReadToByteBuffer_smallInput_returnsSameBytes() throws Throwable {
        byte[] data = "Hello World".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        assertArrayEquals(data, out);
    }

    // covers: while loop multiple iterations (input larger than bufferSize 0x20000)
    @Test
    public void testReadToByteBuffer_inputLargerThanBufferSize_readsAllBytes() throws Throwable {
        byte[] data = new byte[0x20000 + 100];
        Arrays.fill(data, (byte) 65);
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteBuffer buffer = DataUtil.readToByteBuffer(in);
        assertEquals(data.length, buffer.remaining());
    }

    // covers: charsetName == null, meta == null branch (no redecode occurs)
    @Test
    public void testParseByteData_charsetNullNoMeta_parsesWithDefaultUtf8() throws Throwable {
        String html = "<html><head><title>NoMeta</title></head><body>Content</body></html>";
        ByteBuffer buf = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(buf, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Content", doc.body().text());
    }

    // covers: charsetName == null, meta[charset] present, foundCharset != default -> redecode branch
    @Test
    public void testParseByteData_charsetNullMetaCharsetAttr_redecodesWithFoundCharset() throws Throwable {
        String bodyText = "caf\u00e9";
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body>" + bodyText + "</body></html>";
        ByteBuffer buf = ByteBuffer.wrap(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(buf, null, "http://example.com/", Parser.htmlParser());
        assertEquals(bodyText, doc.body().text());
    }

    // covers: charsetName == null, meta[http-equiv] present, getCharsetFromContentType path used
    @Test
    public void testParseByteData_charsetNullMetaHttpEquiv_redecodesWithFoundCharset() throws Throwable {
        String bodyText = "caf\u00e9";
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"></head><body>" + bodyText + "</body></html>";
        ByteBuffer buf = ByteBuffer.wrap(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(buf, null, "http://example.com/", Parser.htmlParser());
        assertEquals(bodyText, doc.body().text());
    }

    // covers: foundCharset.equals(defaultCharset) true -> redecode skipped
    @Test
    public void testParseByteData_charsetNullMetaCharsetEqualsDefault_noRedecodeNeeded() throws Throwable {
        String html = "<html><head><meta charset=\"UTF-8\"></head><body>Hello</body></html>";
        ByteBuffer buf = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(buf, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Hello", doc.body().text());
    }

    // covers: charsetName != null branch, direct decode without meta scanning
    @Test
    public void testParseByteData_charsetSpecified_bypassesMetaDetection() throws Throwable {
        String html = "<html><body>Specified</body></html>";
        ByteBuffer buf = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(buf, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertEquals("Specified", doc.body().text());
    }

    // covers: Validate.notEmpty throws when charsetName is empty string
    @Test
    public void testParseByteData_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        ByteBuffer buf = ByteBuffer.wrap("<html></html>".getBytes("UTF-8"));
        try {
            DataUtil.parseByteData(buf, "", "http://example.com/", Parser.htmlParser());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: BOM stripping contract stated in javadoc comment (bug: BOM not stripped)
    @Test
    public void testParseByteData_bomPresent_shouldBeStrippedFromOutput() throws Throwable {
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] htmlBytes = "<html><head><title>T</title></head><body>One</body></html>".getBytes("UTF-8");
        byte[] combined = new byte[bom.length + htmlBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(htmlBytes, 0, combined, bom.length, htmlBytes.length);
        ByteBuffer buf = ByteBuffer.wrap(combined);
        Document doc = DataUtil.parseByteData(buf, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertFalse(doc.text().contains("\uFEFF"));
    }

    // covers: meta[http-equiv] present but content lacks charset -> foundCharset == null branch
    @Test
    public void testParseByteData_metaHttpEquivWithoutCharset_noRedecode() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html\"></head><body>Plain</body></html>";
        ByteBuffer buf = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(buf, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Plain", doc.body().text());
    }

    // covers: foundCharset.length() == 0 branch (empty charset attribute value)
    @Test
    public void testParseByteData_metaCharsetEmptyValue_noRedecode() throws Throwable {
        String html = "<html><head><meta charset=\"\"></head><body>Empty</body></html>";
        ByteBuffer buf = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(buf, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Empty", doc.body().text());
    }

    // covers: load(InputStream, charsetName, baseUri) full pipeline, basic parse
    @Test
    public void testLoadInputStreamThreeArg_basicHtml_parsesCorrectly() throws Throwable {
        String html = "<html><head><title>Basic</title></head><body>Data</body></html>";
        ByteArrayInputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/");
        assertEquals("Basic", doc.title());
    }

    // covers: full load() pipeline with leading BOM bytes (integration-level bug check)
    @Test
    public void testLoadInputStreamThreeArg_bomPresent_bomStrippedFromBody() throws Throwable {
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] htmlBytes = "<html><head><title>Test</title></head><body>One</body></html>".getBytes("UTF-8");
        byte[] combined = new byte[bom.length + htmlBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(htmlBytes, 0, combined, bom.length, htmlBytes.length);
        ByteArrayInputStream in = new ByteArrayInputStream(combined);
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/");
        assertFalse(doc.text().contains("\uFEFF"));
    }

    // covers: load(InputStream, charsetName, baseUri, parser) with explicit XML parser
    @Test
    public void testLoadInputStreamFourArg_withXmlParser_parsesAsXml() throws Throwable {
        String xml = "<root><child>Value</child></root>";
        ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/", Parser.xmlParser());
        Element child = doc.select("child").first();
        assertEquals("Value", child.text());
    }

    // covers: load() with charsetName null triggers full meta-detection path end-to-end
    @Test
    public void testLoadInputStreamThreeArg_nullCharsetName_detectsMetaCharset() throws Throwable {
        String bodyText = "caf\u00e9";
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head><body>" + bodyText + "</body></html>";
        ByteArrayInputStream in = new ByteArrayInputStream(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.load(in, null, "http://example.com/");
        assertEquals(bodyText, doc.body().text());
    }
}
