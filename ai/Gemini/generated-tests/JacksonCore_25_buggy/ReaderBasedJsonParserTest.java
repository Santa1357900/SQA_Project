package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.CharsToNameCanonicalizer;
import com.fasterxml.jackson.core.util.BufferRecycler;

public class ReaderBasedJsonParserTest {

    @Test
    public void testLifeCycleAndGetters() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        StringReader reader = new StringReader("{\"key\": \"value\"}");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertNotNull(parser.getInputSource());
        assertNull(parser.getCodec());
        
        ObjectCodec mockCodec = null;
        parser.setCodec(mockCodec);
        assertNull(parser.getCodec());
        
        parser.close();
    }

    @Test
    public void testReleaseBuffered() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        char[] buffer = new char[] { 'a', 'b', 'c' };
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, null, null, CharsToNameCanonicalizer.createRoot(),
            buffer, 0, 3, true
        );
        
        StringWriter writer = new StringWriter();
        int count = parser.releaseBuffered(writer);
        assertEquals(3, count);
        assertEquals("abc", writer.toString());
        
        // Test empty buffer case
        ReaderBasedJsonParser parserEmpty = new ReaderBasedJsonParser(
            ctxt, 0, null, null, CharsToNameCanonicalizer.createRoot(),
            buffer, 3, 3, true
        );
        StringWriter writer2 = new StringWriter();
        assertEquals(0, parserEmpty.releaseBuffered(writer2));
    }

    @Test
    public void testNextTokenBasic() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        StringReader reader = new StringReader("{\"field\": 123}");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("field", parser.getCurrentName());
        assertEquals("field", parser.getText());
        
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(123, parser.getIntValue());
        
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testGetTextVariations() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        StringReader reader = new StringReader("[\"hello\", 45.67, true, false, null]");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertNull(parser.getText());
        
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("hello", parser.getText());
        assertEquals(5, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        assertNotNull(parser.getTextCharacters());
        
        StringWriter sw = new StringWriter();
        assertEquals(5, parser.getText(sw));
        assertEquals("hello", sw.toString());

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals("45.67", parser.getText());
        
        assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());
        assertEquals("true", parser.getText());

        assertEquals(JsonToken.VALUE_FALSE, parser.nextToken());
        assertEquals("false", parser.getText());

        assertEquals(JsonToken.VALUE_NULL, parser.nextToken());
        assertEquals("null", parser.getText());

        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testGetValueAsString() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        StringReader reader = new StringReader("{\"name\": \"val\"}");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        parser.nextToken(); // START_OBJECT
        parser.nextToken(); // FIELD_NAME
        
        assertEquals("name", parser.getValueAsString());
        assertEquals("name", parser.getValueAsString("def"));
        
        parser.nextToken(); // VALUE_STRING
        assertEquals("val", parser.getValueAsString());
        assertEquals("val", parser.getValueAsString("def"));

        parser.close();
    }

    @Test
    public void testGetBinaryValueAndReadBinary() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        // "TWFuaW4=" is base64 for "Manin"
        StringReader reader = new StringReader("\"TWFuaW4=\"");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        byte[] bytes = parser.getBinaryValue(Base64Variants.MIME);
        assertNotNull(bytes);
        
        // Test readBinaryValue with OutputStream
        StringReader reader2 = new StringReader("\"TWFuaW4=\"");
        ReaderBasedJsonParser parser2 = new ReaderBasedJsonParser(
            ctxt, 0, reader2, null, CharsToNameCanonicalizer.createRoot()
        );
        parser2.nextToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = parser2.readBinaryValue(Base64Variants.MIME, out);
        assertTrue(len > 0);
        
        parser.close();
        parser2.close();
    }

    @Test
    public void testNextFieldAndTextValueMethods() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        StringReader reader = new StringReader("{\"str\": \"abc\", \"num\": 10, \"lng\": 20, \"bool\": true}");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        assertEquals("str", parser.nextFieldName());
        assertEquals("abc", parser.nextTextValue());
        
        assertEquals("num", parser.nextFieldName());
        assertEquals(10, parser.nextIntValue(99));
        
        assertEquals("lng", parser.nextFieldName());
        assertEquals(20L, parser.nextLongValue(99L));
        
        assertEquals("bool", parser.nextFieldName());
        assertEquals(Boolean.TRUE, parser.nextBooleanValue());

        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testNumberParsingEdges() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        // Test positive, negative, zero, float, exponent
        StringReader reader = new StringReader("[0, 5, -5, 123.45, 1e2, -1E-2]");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(0, parser.getIntValue());

        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(5, parser.getIntValue());

        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(-5, parser.getIntValue());

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(123.45, parser.getDoubleValue(), 0.001);

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(100.0, parser.getDoubleValue(), 0.001);

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(-0.01, parser.getDoubleValue(), 0.0001);

        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testAllowCommentsAndSpecialValues() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        int features = JsonParser.Feature.ALLOW_COMMENTS.getMask() | 
                       JsonParser.Feature.ALLOW_YAML_COMMENTS.getMask() |
                       JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS.getMask() |
                       JsonParser.Feature.ALLOW_SINGLE_QUOTES.getMask();
        
        String json = "[ // comment \n # yaml comment\n 'single', NaN, Infinity, -Infinity ]";
        StringReader reader = new StringReader(json);
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, features, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("single", parser.getText());

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertTrue(Double.isNaN(parser.getDoubleValue()));

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), parser.getDoubleValue(), 0.0);

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), parser.getDoubleValue(), 0.0);

        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testTokenLocationAndCurrentLocation() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        StringReader reader = new StringReader("{\"a\": 1}");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        assertNotNull(parser.getCurrentLocation());
        
        parser.nextToken(); // START_OBJECT
        parser.nextToken(); // FIELD_NAME
        assertNotNull(parser.getTokenLocation());
        
        parser.close();
    }

    @Test
    public void testFinishToken() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        StringReader reader = new StringReader("\"some string\"");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, reader, null, CharsToNameCanonicalizer.createRoot()
        );

        parser.nextToken();
        parser.finishToken();
        
        parser.close();
    }
}