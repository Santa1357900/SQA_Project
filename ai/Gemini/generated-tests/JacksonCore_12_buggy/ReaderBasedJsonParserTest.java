package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.CharArrayWriter;
import java.io.IOException;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.sym.CharsToNameCanonicalizer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Base64Variants;

public class ReaderBasedJsonParserTest {

    @Test
    public void testBasicParsingAndAccessors() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "test-source", true);
        StringReader reader = new StringReader("{\"hello\": \"world\", \"num\": 123, \"flag\": true, \"nil\": null, \"arr\": [1, 2], \"neg\": -456, \"flt\": 12.34}");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, symbols
        );

        assertNotNull(parser.getInputSource());
        assertNull(parser.getCodec());
        parser.setCodec(null);

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        assertEquals("hello", parser.nextFieldName());
        assertEquals("hello", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("world", parser.getText());
        assertEquals("world", parser.getValueAsString());
        assertEquals("world", parser.getValueAsString("def"));
        assertNotNull(parser.getTextCharacters());
        assertTrue(parser.getTextLength() > 0);
        assertEquals(0, parser.getTextOffset());

        assertEquals("num", parser.nextFieldName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(123, parser.getIntValue());
        assertEquals(123L, parser.getLongValue());
        assertEquals("123", parser.getText());

        assertEquals("flag", parser.nextFieldName());
        assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());
        assertEquals(Boolean.TRUE, parser.nextBooleanValue());

        assertEquals("nil", parser.nextFieldName());
        assertEquals(JsonToken.VALUE_NULL, parser.nextToken());

        assertEquals("arr", parser.nextFieldName());
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(1, parser.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(2, parser.getIntValue());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());

        assertEquals("neg", parser.nextFieldName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(-456, parser.getIntValue());

        assertEquals("flt", parser.nextFieldName());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(12.34, parser.getDoubleValue(), 0.001);

        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testReleaseBuffered() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "test-source", true);
        char[] buffer = new char[] { 'a', 'b', 'c' };
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, null, null, CharsToNameCanonicalizer.createRoot(),
            buffer, 0, 3, true
        );

        CharArrayWriter writer = new CharArrayWriter();
        int count = parser.releaseBuffered(writer);
        assertEquals(3, count);
        assertEquals("abc", writer.toString());
        parser.close();
    }

    @Test
    public void testNextTextValueAndIntLong() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "test-source", true);
        StringReader reader = new StringReader("{\"str\": \"val\", \"int\": 99, \"lng\": 888, \"bool\": false}");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        String fieldName = parser.nextFieldName();
        assertEquals("str", fieldName);
        assertEquals("val", parser.nextTextValue());

        fieldName = parser.nextFieldName();
        assertEquals("int", fieldName);
        assertEquals(99, parser.nextIntValue(0));

        fieldName = parser.nextFieldName();
        assertEquals("lng", fieldName);
        assertEquals(888L, parser.nextLongValue(0L));

        fieldName = parser.nextFieldName();
        assertEquals("bool", fieldName);
        assertEquals(Boolean.FALSE, parser.nextBooleanValue());

        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    @Test
    public void testBinaryValueParsing() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "test-source", true);
        StringReader reader = new StringReader("\"TWFuIg==\"");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        byte[] bytes = parser.getBinaryValue(Base64Variants.MIME);
        assertNotNull(bytes);

        parser.close();
    }

    @Test
    public void testErrorScenarios() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "test-source", true);
        StringReader reader = new StringReader("{invalidToken}");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        try {
            parser.nextToken();
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().length() > 0);
        } finally {
            parser.close();
        }
    }
}