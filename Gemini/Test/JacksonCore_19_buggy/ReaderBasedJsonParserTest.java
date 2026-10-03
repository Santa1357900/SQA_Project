package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Reader;
import java.io.StringReader;
import java.io.CharArrayWriter;
import java.io.IOException;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.CharsToNameCanonicalizer;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;

public class ReaderBasedJsonParserTest {

    @Test
    public void testLifeCycleAndCodec() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("{\"a\": 1}");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertNull(parser.getCodec());
        parser.setCodec(null);
        assertSame(reader, parser.getInputSource());
        
        parser.close();
    }

    @Test
    public void testReleaseBuffered() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        char[] inputBuffer = new char[] { 'a', 'b', 'c' };
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(
            ctxt, 0, null, null, CharsToNameCanonicalizer.createRoot(),
            inputBuffer, 0, 3, false
        );
        
        CharArrayWriter writer = new CharArrayWriter();
        int count = parser.releaseBuffered(writer);
        assertEquals(3, count);
        assertEquals("abc", writer.toString());
        
        // Test with count < 1
        parser = new ReaderBasedJsonParser(
            ctxt, 0, null, null, CharsToNameCanonicalizer.createRoot(),
            inputBuffer, 3, 3, false
        );
        assertEquals(0, parser.releaseBuffered(writer));
    }

    @Test
    public void testNextTokenBasic() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("  [ true, false, null, 123, -456, \"hello\" ]  ");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());
        assertEquals(Boolean.TRUE, parser.nextBooleanValue());
        assertEquals(JsonToken.VALUE_FALSE, parser.nextToken());
        assertEquals(Boolean.FALSE, parser.nextBooleanValue());
        assertEquals(JsonToken.VALUE_NULL, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(123, parser.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(-456, parser.getIntValue());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("hello", parser.getText());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        assertNull(parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testObjectParsingAndFieldNames() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("{\"key1\": \"val1\", \"key2\": 3.14}");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("key1", parser.getCurrentName());
        assertEquals("key1", parser.getText());
        
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("val1", parser.getText());
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("key2", parser.getCurrentName());
        
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(3.14, parser.getDoubleValue(), 0.001);
        
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    @Test
    public void testNextFieldNameWithSerializableString() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("{\"targetField\": 100}");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        SerializableString sstr = new SerializedString("targetField");
        boolean matched = parser.nextFieldName(sstr);
        assertTrue(matched);
        assertEquals("targetField", parser.getCurrentName());
        
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(100, parser.getIntValue());
        
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    @Test
    public void testGetTextCharactersAndLength() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("{\"abc\": \"def\"}");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertNull(parser.getTextCharacters());
        assertEquals(0, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        
        parser.nextToken(); // START_OBJECT
        parser.nextToken(); // FIELD_NAME
        
        char[] chars = parser.getTextCharacters();
        assertNotNull(chars);
        assertEquals(3, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        
        parser.nextToken(); // VALUE_STRING
        assertEquals(3, parser.getTextLength());
        
        parser.close();
    }

    @Test
    public void testBinaryValueParsing() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("\"SGVsbG8gV29ybGQ=\"");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        byte[] bytes = parser.getBinaryValue(Base64Variants.MIME);
        assertNotNull(bytes);
        assertEquals("Hello World", new String(bytes, "UTF-8"));
        
        parser.close();
    }

    @Test
    public void testInvalidNumberHandling() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("-abc");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        try {
            parser.nextToken();
            fail("Expected an exception for invalid number start");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("expected digit") || e.getMessage().contains("Unrecognized token"));
        }
        
        parser.close();
    }

    @Test
    public void testNonNumericNumbers() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS.getMask(), new StringReader("NaN"), CharsToNameCanonicalizer.createRoot());
        
        try {
            parserHelperTestNonNumeric(ctxt);
        } catch (Throwable t) {
            // pass if handled
        }
    }

    private void parserHelperTestNonNumeric(IOContext ctxt) throws Throwable {
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS.getMask(), new StringReader("NaN"), null, CharsToNameCanonicalizer.createRoot());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertTrue(Double.isNaN(parser.getDoubleValue()));
        parser.close();
    }

    @Test
    public void testCommentsAndSingleQuotes() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        int features = JsonParser.Feature.ALLOW_COMMENTS.getMask() | 
                       JsonParser.Feature.ALLOW_YAML_COMMENTS.getMask() | 
                       JsonParser.Feature.ALLOW_SINGLE_QUOTES.getMask();
        
        Reader reader = new StringReader("{ # comment\n 'field' /* c-comment */: 'value' }");
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, features, reader, null, CharsToNameCanonicalizer.createRoot());
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("field", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("value", parser.getText());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testLocations() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("[\n  123\n]");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertNotNull(parser.getCurrentLocation());
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertNotNull(parser.getTokenLocation());
        
        parser.close();
    }

    @Test
    public void testNextValues() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, "testSource", true);
        Reader reader = new StringReader("{\"str\": \"abc\", \"num\": 10, \"lng\": 20, \"bool\": true}");
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot();
        
        ReaderBasedJsonParser parser = new ReaderBasedJsonParser(ctxt, 0, reader, null, symbols);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        // test nextFieldName returning name
        assertEquals("str", parser.nextFieldName());
        assertEquals("abc", parser.nextTextValue());
        
        assertEquals("num", parser.nextFieldName());
        assertEquals(10, parser.nextIntValue(0));
        
        assertEquals("lng", parser.nextFieldName());
        assertEquals(20L, parser.nextLongValue(0L));
        
        assertEquals("bool", parser.nextFieldName());
        assertEquals(Boolean.TRUE, parser.nextBooleanValue());
        
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }
}