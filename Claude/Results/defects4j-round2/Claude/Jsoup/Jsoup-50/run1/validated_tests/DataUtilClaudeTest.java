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

public class DataUtilClaudeTest {

    // covers load(InputStream, charsetName, baseUri) with explicit charset -> html parser path
    @Test
    public void testLoad_withExplicitCharset_parsesDocumentCorrectly() throws Throwable {
        String html = "<html><head><title>Hello</title></head><body><p>World</p></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "http://example.com/");
        assertEquals("Hello", doc.title());
        assertEquals("World", doc.select("p").text());
    }

    // covers load(InputStream, charsetName=null, baseUri) default utf-8 detection branch
    @Test
    public void testLoad_withNullCharset_detectsDefaultUtf8() throws Throwable {
        String html = "<html><head><title>Hello</title></head><body></body></html>";
        InputStream in = new ByteArrayInputStream(html.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, null, "http://example.com/");
        assertEquals("Hello", doc.title());
    }

    // covers load(InputStream, charsetName, baseUri, parser) with xml parser
    @Test
    public void testLoad_withParserArgument_usesGivenParser() throws Throwable {
        String xml = "<root><child>Value</child></root>";
        InputStream in = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = DataUtil.load(in, "UTF-8", "", Parser.xmlParser());
        Element child = doc.select("child").first();
        assertNotNull(child);
        assertEquals("Value", child.text());
    }

    // covers crossStreams loop with multiple bytes, 1+ iterations
    @Test
    public void testCrossStreams_copiesAllBytesFromInputToOutput() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertArrayEquals(data, out.toByteArray());
    }

    // covers crossStreams loop with zero iterations (empty stream)
    @Test
    public void testCrossStreams_emptyInput_writesNothing() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataUtil.crossStreams(in, out);
        assertEquals(0, out.toByteArray().length);
    }

    // covers parseByteData charsetName != null branch, direct decode
    @Test
    public void testParseByteData_specifiedCharset_decodesCorrectly() throws Throwable {
        String html = "<html><head><title>Direct</title></head><body></body></html>";
        ByteBuffer bb = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(bb, "UTF-8", "http://example.com/", Parser.htmlParser());
        assertEquals("Direct", doc.title());
    }

    // covers Validate.notEmpty throw when charsetName is empty string
    @Test
    public void testParseByteData_emptyCharsetName_throwsIllegalArgumentException() throws Throwable {
        ByteBuffer bb = ByteBuffer.wrap("<html></html>".getBytes("UTF-8"));
        try {
            DataUtil.parseByteData(bb, "", "http://example.com/", Parser.htmlParser());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers charsetName==null branch, meta http-equiv detection, redecode path
    @Test
    public void testParseByteData_nullCharset_metaHttpEquivDetectsCharsetAndDecodes() throws Throwable {
        String html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">"
                + "<title>caf\u00e9</title></head><body></body></html>";
        ByteBuffer bb = ByteBuffer.wrap(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(bb, null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.title());
    }

    // covers charsetName==null branch, meta[charset] attribute detection, redecode path
    @Test
    public void testParseByteData_nullCharset_metaCharsetAttributeDetectsCharsetAndDecodes() throws Throwable {
        String html = "<html><head><meta charset=\"ISO-8859-1\"><title>caf\u00e9</title></head><body></body></html>";
        ByteBuffer bb = ByteBuffer.wrap(html.getBytes("ISO-8859-1"));
        Document doc = DataUtil.parseByteData(bb, null, "http://example.com/", Parser.htmlParser());
        assertEquals("caf\u00e9", doc.title());
    }

    // covers meta[charset] with unsupported name, caught exception, falls back to default utf-8
    @Test
    public void testParseByteData_nullCharset_unsupportedMetaCharsetFallsBackToDefault() throws Throwable {
        String html = "<html><head><meta charset=\"Bogus-Charset-Name-Xyz\"><title>Plain</title></head><body></body></html>";
        ByteBuffer bb = ByteBuffer.wrap(html.getBytes("UTF-8"));
        Document doc = DataUtil.parseByteData(bb, null, "http://example.com/", Parser.htmlParser());
        assertEquals("Plain", doc.title());
    }

    // covers BOM detection branch, stripping BOM and re-decoding with default charset
    @Test
    public void testParseByteData_bomPresent_stripsBomAndUsesDefaultCharset() throws Throwable {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] htmlBytes = "<html><head><title>BOMTest</title></head><body></body></html>".getBytes("UTF-8");
        byte[] combined = new byte[bom.length + htmlBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(htmlBytes, 0, combined, bom.length, htmlBytes.length);
        ByteBuffer bb = ByteBuffer.wrap(combined);
        Document doc = DataUtil.parseByteData(bb, null, "http://example.com/", Parser.htmlParser());
        assertEquals("BOMTest", doc.title());
    }

    // covers readToByteBuffer unlimited branch (maxSize == 0, not capped)
    @Test
    public void testReadToByteBuffer_maxSizeZero_readsAllBytes() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ByteBuffer buf = DataUtil.readToByteBuffer(new ByteArrayInputStream(data), 0);
        assertArrayEquals(data, buf.array());
    }

    // covers readToByteBuffer capped branch where read equals remaining exactly (boundary)
    @Test
    public void testReadToByteBuffer_maxSizeEqualsLength_readsAllBytes() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ByteBuffer buf = DataUtil.readToByteBuffer(new ByteArrayInputStream(data), 10);
        assertArrayEquals(data, buf.array());
    }

    // covers readToByteBuffer capped branch where read > remaining, truncation occurs
    @Test
    public void testReadToByteBuffer_maxSizeLessThanLength_truncatesData() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ByteBuffer buf = DataUtil.readToByteBuffer(new ByteArrayInputStream(data), 5);
        byte[] expected = new byte[]{1, 2, 3, 4, 5};
        assertArrayEquals(expected, buf.array());
    }

    // covers Validate.isTrue throw for negative maxSize
    @Test
    public void testReadToByteBuffer_negativeMaxSize_throwsIllegalArgumentException() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{1, 2, 3});
        try {
            DataUtil.readToByteBuffer(in, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers single-arg readToByteBuffer overload delegating to unlimited read
    @Test
    public void testReadToByteBuffer_singleArgOverload_readsAllBytes() throws Throwable {
        byte[] data = new byte[]{9, 8, 7, 6, 5};
        ByteBuffer buf = DataUtil.readToByteBuffer(new ByteArrayInputStream(data));
        assertArrayEquals(data, buf.array());
    }

    // covers emptyByteBuffer returning zero-capacity buffer
    @Test
    public void testEmptyByteBuffer_returnsZeroCapacityBuffer() throws Throwable {
        ByteBuffer buf = DataUtil.emptyByteBuffer();
        assertEquals(0, buf.capacity());
        assertEquals(0, buf.limit());
    }

    // covers getCharsetFromContentType null-input early return
    @Test
    public void testGetCharsetFromContentType_nullInput_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType(null));
    }

    // covers getCharsetFromContentType when no charset= token present, m.find() false
    @Test
    public void testGetCharsetFromContentType_noCharsetPresent_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html"));
    }

    // covers getCharsetFromContentType when captured charset group is empty
    @Test
    public void testGetCharsetFromContentType_emptyCharsetValue_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("charset="));
    }

    // covers getCharsetFromContentType supported charset already uppercase, returned as-is
    @Test
    public void testGetCharsetFromContentType_uppercaseCharset_returnsAsIs() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=UTF-8");
        assertEquals("UTF-8", result);
    }



    // covers getCharsetFromContentType with quoted charset value parsing
    @Test
    public void testGetCharsetFromContentType_quotedCharset_parsesCorrectly() throws Throwable {
        String result = DataUtil.getCharsetFromContentType("text/html; charset=\"ISO-8859-1\"");
        assertEquals("ISO-8859-1", result);
    }

    // covers getCharsetFromContentType with syntactically valid but unsupported charset name
    @Test
    public void testGetCharsetFromContentType_unsupportedCharsetName_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=NotARealCharsetXyz123"));
    }

    // covers IllegalCharsetNameException catch branch with syntactically illegal charset name
    @Test
    public void testGetCharsetFromContentType_illegalCharsetSyntax_returnsNull() throws Throwable {
        assertNull(DataUtil.getCharsetFromContentType("text/html; charset=inva/lid"));
    }

    // covers mimeBoundary loop producing correct fixed length string
    @Test
    public void testMimeBoundary_hasCorrectLength() throws Throwable {
        String boundary = DataUtil.mimeBoundary();
        assertEquals(32, boundary.length());
    }

    // covers mimeBoundary loop using only allowed mime boundary characters
    @Test
    public void testMimeBoundary_containsOnlyAllowedCharacters() throws Throwable {
        String allowed = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String boundary = DataUtil.mimeBoundary();
        for (int i = 0; i < boundary.length(); i++) {
            char c = boundary.charAt(i);
            assertTrue(allowed.indexOf(c) >= 0);
        }
    }
}
