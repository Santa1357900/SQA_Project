package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.CharArrayWriter;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.CharsToNameCanonicalizer;
import com.fasterxml.jackson.core.util.BufferRecycler;

public class ReaderBasedJsonParserTest {

    @Test
    public void testLifecycleAndCodec() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("{\"key\": \"value\"}");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertNull(parser.getCodec());
        parser.setCodec(null);
        assertNull(parser.getInputSource());
        
        parser.close();
    }

    @Test
    public void testReleaseBuffered() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("test");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        CharArrayWriter writer = new CharArrayWriter();
        
        int released = parser.releaseBuffered(writer);
        assertEquals(0, released);
        
        parser.close();
    }

    @Test
    public void testGetTextMethods() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("{\"a\": 123, \"b\": \"hello\"}");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertNull(parser.getText());
        assertNull(parser.getTextCharacters());
        assertEquals(0, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        assertNull(parser.getValueAsString());
        assertEquals("default", parser.getValueAsString("default"));
        
        parser.close();
    }

    @Test
    public void testBinaryValueAccess() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("\"TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlzIHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2YgdGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJwZXR1YWwgZWFnZXJuZXNzIHRvIGZpbmQgb3V0IHRoZSBjdXNhbGl0eSBvZiB0aGluZ3MsIHB1dHMgdGhlIGluZWN0dWFsdWFscy4=\"");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        try {
            parser.getBinaryValue(Base64Variants.MIME);
            fail("Should have thrown exception due to current token not being VALUE_STRING");
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            assertTrue(e.getMessage().contains("not VALUE_STRING"));
        }
        
        parser.close();
    }

    @Test
    public void testNextTokenParsing() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("{\"name\": true, \"arr\": [1, 2], \"val\": null}");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, JsonParser.Feature.ALLOW_COMMENTS.getMask(), reader, null, symbols);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("name", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());
        assertEquals(Boolean.TRUE, parser.nextBooleanValue());
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("arr", parser.getCurrentName());
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(1, parser.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(2, parser.getIntValue());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("val", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_NULL, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        
        assertNull(parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testNextValues() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("{\"str\": \"abc\", \"num\": 42, \"long\": 100, \"bool\": false}");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("abc", parser.nextTextValue());
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(42, parser.nextIntValue(0));
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(100L, parser.nextLongValue(0L));
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(Boolean.FALSE, parser.nextBooleanValue());
        
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testInvalidNumberParsing() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("-INVALID");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        try {
            parser.nextToken();
            fail("Should throw exception for invalid number start");
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            assertTrue(e.getMessage().contains("expected digit") || e.getMessage().contains("Non-standard token"));
        }
        
        parser.close();
    }

    @Test
    public void testReadBinaryStream() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, br, true);
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        StringReader reader = new StringReader("\"SGVsbG8gV29ybGQ=\"");
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int bytesRead = parser.readBinaryValue(Base64Variants.MIME, out);
        assertTrue(bytesRead > 0);
        assertEquals("Hello World", new String(out.toByteArray(), "UTF-8"));
        
        parser.close();
    }
}