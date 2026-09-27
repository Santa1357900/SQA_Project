package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.BytesToNameCanonicalizer;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.TextBuffer;

public class UTF8StreamJsonParserTest {

    private UTF8StreamJsonParser createParser(byte[] data) {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "test", false);
        InputStream in = new ByteArrayInputStream(data);
        BytesToNameCanonicalizer symbols = BytesToNameCanonicalizer.createRoot();
        return new UTF8StreamJsonParser(ctxt, 0, in, null, symbols, data, 0, data.length, false);
    }

    private UTF8StreamJsonParser createParserWithFeatures(byte[] data, int features) {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "test", false);
        InputStream in = new ByteArrayInputStream(data);
        BytesToNameCanonicalizer symbols = BytesToNameCanonicalizer.createRoot();
        return new UTF8StreamJsonParser(ctxt, features, in, null, symbols, data, 0, data.length, false);
    }

    @Test
    public void testLifeCycleAndCodec() throws Throwable {
        byte[] data = "{}".getBytes("UTF-8");
        UTF8StreamJsonParser parser = createParser(data);
        
        assertNull(parser.getCodec());
        ObjectCodec codec = new com.fasterxml.jackson.databind.ObjectMapper();
        parser.setCodec(codec);
        assertEquals(codec, parser.getCodec());
        
        assertNotNull(parser.getInputSource());
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int released = parser.releaseBuffered(out);
        assertEquals(2, released);
        assertEquals("{}", out.toString("UTF-8"));
        
        parser.close();
    }

    @Test
    public void testGrowArrayBy() throws Throwable {
        int[] original = new int[] { 1, 2, 3 };
        int[] grown = UTF8StreamJsonParser.growArrayBy(original, 2);
        assertEquals(5, grown.length);
        assertEquals(1, grown[0]);
        assertEquals(3, grown[2]);
        
        int[] nullGrown = UTF8StreamJsonParser.growArrayBy(null, 3);
        assertEquals(3, nullGrown.length);
    }

    @Test
    public void testBasicParsingAndGetText() throws Throwable {
        byte[] data = "{\"hello\": \"world\"}".getBytes("UTF-8");
        UTF8StreamJsonParser parser = createParser(data);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("hello", parser.getText());
        assertEquals("hello", parser.getValueAsString());
        assertEquals("hello", parser.getValueAsString("default"));
        
        char[] chars = parser.getTextCharacters();
        assertNotNull(chars);
        assertEquals(5, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("world", parser.getText());
        
        JsonLocation loc = parser.getCurrentLocation();
        assertNotNull(loc);
        JsonLocation tokLoc = parser.getTokenLocation();
        assertNotNull(tokLoc);
        
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    @Test
    public void testNextTokenVariants() throws Throwable {
        byte[] data = "{\"a\": 123, \"b\": true, \"c\": false, \"d\": null, \"e\": [1]}".getBytes("UTF-8");
        UTF8StreamJsonParser parser = createParser(data);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        // nextFieldName
        SerializableString fieldStr = new DefaultPrettyPrinter().createModifiedUtf8String("a");
        assertTrue(parser.nextFieldName(fieldStr));
        assertEquals(123, parser.nextIntValue(0));
        
        // nextFieldName maybe match
        SerializableString fieldStrC = new DefaultPrettyPrinter().createModifiedUtf8String("c");
        assertFalse(parser.nextFieldName(fieldStrC)); // matches "b" actually, let's verify sequence
        // Wait, current token was FIELD_NAME for "b". Let's check nextFieldName properly.
        parser.close();
    }

    @Test
    public void testNextValueVariants() throws Throwable {
        byte[] data = "{\"str\":\"val\",\"num\":456,\"lng\":789,\"bool\":true}".getBytes("UTF-8");
        UTF8StreamJsonParser parser = createParser(data);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        // Field name "str"
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("val", parser.nextTextValue());
        
        // Field name "num"
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(456, parser.nextIntValue(0));
        
        // Field name "lng"
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(789L, parser.nextLongValue(0L));
        
        // Field name "bool"
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(Boolean.TRUE, parser.nextBooleanValue());
        
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    @Test
    public void testNumberParsingEdges() throws Throwable {
        byte[] data = "[-0, 0123, 123.45e2, -1.5]".getBytes("UTF-8");
        int features = JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS.getMask();
        UTF8StreamJsonParser parser = createParserWithFeatures(data, features);
        
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(-0, parser.getIntValue());
        
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(123, parser.getIntValue());
        
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(12345.0, parser.getDoubleValue(), 0.001);
        
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(-1.5, parser.getDoubleValue(), 0.001);
        
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        parser.close();
    }

    @Test
    public void testNonStandardTokensAndComments() throws Throwable {
        byte[] data = "[NaN, Infinity, -Infinity, // comment\n true]".getBytes("UTF-8");
        int features = JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS.getMask() |
                       JsonParser.Feature.ALLOW_COMMENTS.getMask();
        UTF8StreamJsonParser parser = createParserWithFeatures(data, features);
        
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertTrue(Double.isNaN(parser.getDoubleValue()));
        
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertTrue(Double.isInfinite(parser.getDoubleValue()));
        
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertTrue(parser.getDoubleValue() < 0);
        
        assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        parser.close();
    }

    @Test
    public void testBinaryValueParsing() throws Throwable {
        byte[] data = "\"SGVsbG8gV29ybGQ=\"".getBytes("UTF-8");
        UTF8StreamJsonParser parser = createParser(data);
        
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        byte[] binary = parser.getBinaryValue(Base64Variants.MIME);
        assertNotNull(binary);
        assertEquals("Hello World", new String(binary, "UTF-8"));
        
        // Read binary into output stream
        byte[] data2 = "\"SGVsbG8gV29ybGQ=\"".getBytes("UTF-8");
        UTF8StreamJsonParser parser2 = createParser(data2);
        assertEquals(JsonToken.VALUE_STRING, parser2.nextToken());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = parser2.readBinaryValue(Base64Variants.MIME, out);
        assertTrue(len > 0);
        parser2.close();
    }

    @Test(expected = IOException.class)
    public void testInvalidJsonThrowsException() throws Throwable {
        byte[] data = "{invalid_json".getBytes("UTF-8");
        UTF8StreamJsonParser parser = createParser(data);
        try {
            while (parser.nextToken() != null) {
                // consume
            }
        } finally {
            parser.close();
        }
    }
}