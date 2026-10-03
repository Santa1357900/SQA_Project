package com.fasterxml.jackson.core.base;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

public class ParserMinimalBaseTest {

    private static class ConcreteParser extends ParserMinimalBase {
        private JsonToken nextTokenResult = null;
        private String currentNameResult = null;
        private String textResult = null;
        private char[] textCharsResult = null;
        private int textLenResult = 0;
        private int textOffsetResult = 0;
        private Object embeddedObjectResult = null;
        private int intValueResult = 0;
        private long longValueResult = 0L;
        private double doubleValueResult = 0.0;
        private boolean closed = false;

        public ConcreteParser() {
            super();
        }

        public ConcreteParser(int features) {
            super(features);
        }

        public void setNextTokenResult(JsonToken t) {
            this._currToken = t;
        }

        public void setNextTokenToReturn(JsonToken t) {
            this.nextTokenResult = t;
        }

        public void setCurrentNameResult(String name) {
            this.currentNameResult = name;
        }

        public void setTextResult(String text) {
            this.textResult = text;
        }

        public void setEmbeddedObjectResult(Object obj) {
            this.embeddedObjectResult = obj;
        }

        public void setIntValueResult(int v) {
            this.intValueResult = v;
        }

        public void setLongValueResult(long v) {
            this.longValueResult = v;
        }

        public void setDoubleValueResult(double v) {
            this.doubleValueResult = v;
        }

        @Override
        public JsonToken nextToken() throws IOException {
            return nextTokenResult;
        }

        @Override
        protected void _handleEOF() throws JsonParseException {
            _reportError("EOF");
        }

        @Override
        public String getCurrentName() throws IOException {
            return currentNameResult;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public JsonStreamContext getParsingContext() {
            return null;
        }

        @Override
        public void overrideCurrentName(String name) {
            this.currentNameResult = name;
        }

        @Override
        public String getText() throws IOException {
            return textResult;
        }

        @Override
        public char[] getTextCharacters() throws IOException {
            return textCharsResult;
        }

        @Override
        public boolean hasTextCharacters() {
            return textCharsResult != null;
        }

        @Override
        public int getTextLength() throws IOException {
            return textLenResult;
        }

        @Override
        public int getTextOffset() throws IOException {
            return textOffsetResult;
        }

        @Override
        public byte[] getBinaryValue(Base64Variant b64variant) throws IOException {
            return new byte[0];
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public ObjectCodec getCodec() {
            return null;
        }

        @Override
        public void setCodec(ObjectCodec c) {
        }

        @Override
        public JsonLocation getCurrentLocation() {
            return null;
        }

        @Override
        public JsonLocation getTokenLocation() {
            return null;
        }

        @Override
        public Number getNumberValue() throws IOException {
            return Integer.valueOf(intValueResult);
        }

        @Override
        public NumberTypegetNumberType() throws IOException {
            return NumberType.INT;
        }

        @Override
        public int getIntValue() throws IOException {
            return intValueResult;
        }

        @Override
        public long getLongValue() throws IOException {
            return longValueResult;
        }

        @Override
        public BigInteger getBigIntegerValue() throws IOException {
            return BigInteger.valueOf(longValueResult);
        }

        @Override
        public float getFloatValue() throws IOException {
            return (float) doubleValueResult;
        }

        @Override
        public double getDoubleValue() throws IOException {
            return doubleValueResult;
        }

        @Override
        public BigDecimal getDecimalValue() throws IOException {
            return BigDecimal.valueOf(doubleValueResult);
        }

        @Override
        public Object getEmbeddedObject() throws IOException {
            return embeddedObjectResult;
        }
    }

    @Test
    public void testTokenStateMethods() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        assertFalse(parser.hasCurrentToken());
        assertEquals(JsonTokenId.ID_NO_TOKEN, parser.getCurrentTokenId());
        assertNull(parser.getCurrentToken());
        assertFalse(parser.hasTokenId(JsonTokenId.ID_NO_TOKEN));
        assertTrue(parser.hasTokenId(JsonTokenId.ID_NULL));
        assertFalse(parser.hasToken(JsonToken.START_ARRAY));
        assertFalse(parser.isExpectedStartArrayToken());
        assertFalse(parser.isExpectedStartObjectToken());

        parser.setNextTokenResult(JsonToken.START_ARRAY);
        assertTrue(parser.hasCurrentToken());
        assertEquals(JsonTokenId.ID_START_ARRAY, parser.getCurrentTokenId());
        assertEquals(JsonToken.START_ARRAY, parser.getCurrentToken());
        assertTrue(parser.hasTokenId(JsonTokenId.ID_START_ARRAY));
        assertTrue(parser.hasToken(JsonToken.START_ARRAY));
        assertTrue(parser.isExpectedStartArrayToken());
        assertFalse(parser.isExpectedStartObjectToken());

        parser.setNextTokenResult(JsonToken.START_OBJECT);
        assertTrue(parser.isExpectedStartObjectToken());

        parser.clearCurrentToken();
        assertNull(parser.getCurrentToken());
        assertEquals(JsonToken.START_OBJECT, parser.getLastClearedToken());
    }

    @Test
    public void testNextValue() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        parser.setNextTokenResult(JsonToken.FIELD_NAME);
        // nextValue() should return nextToken() after FIELD_NAME
        parser.setNextTokenToReturn(JsonToken.VALUE_STRING);
        
        // Override nextToken behavior for nextValue test simulation by overriding class or subclass
        ConcreteParser customParser = new ConcreteParser() {
            private int callCount = 0;
            @Override
            public JsonToken nextToken() throws IOException {
                callCount++;
                if (callCount == 1) {
                    return JsonToken.FIELD_NAME;
                }
                return JsonToken.VALUE_STRING;
            }
        };

        JsonToken val = customParser.nextValue();
        assertEquals(JsonToken.VALUE_STRING, val);
    }

    @Test
    public void testSkipChildren() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        // If not start object or array, returns itself
        parser.setNextTokenResult(JsonToken.VALUE_STRING);
        assertSame(parser, parser.skipChildren());

        // Test with start object
        ConcreteParser structParser = new ConcreteParser() {
            private int step = 0;
            @Override
            public JsonToken nextToken() throws IOException {
                step++;
                if (step == 1) return JsonToken.START_OBJECT;
                if (step == 2) return JsonToken.FIELD_NAME;
                if (step == 3) return JsonToken.VALUE_STRING;
                if (step == 4) return JsonToken.END_OBJECT;
                return null;
            }
        };
        structParser.setNextTokenResult(JsonToken.START_OBJECT);
        assertSame(structParser, structParser.skipChildren());
    }

    @Test
    public void testSkipChildrenEOF() throws Throwable {
        ConcreteParser structParser = new ConcreteParser() {
            @Override
            public JsonToken nextToken() throws IOException {
                return null;
            }
        };
        structParser.setNextTokenResult(JsonToken.START_OBJECT);
        try {
            structParser.skipChildren();
            fail("Expected exception on EOF");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unexpected end-of-input"));
        }
    }

    @Test
    public void testGetValueAsBoolean() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        
        // Default value when no token
        assertFalse(parser.getValueAsBoolean(false));
        assertTrue(parser.getValueAsBoolean(true));

        // ID_TRUE
        parser.setNextTokenResult(JsonToken.VALUE_TRUE);
        assertTrue(parser.getValueAsBoolean(false));

        // ID_FALSE
        parser.setNextTokenResult(JsonToken.VALUE_FALSE);
        assertFalse(parser.getValueAsBoolean(true));

        // ID_NULL
        parser.setNextTokenResult(JsonToken.VALUE_NULL);
        assertFalse(parser.getValueAsBoolean(true));

        // ID_NUMBER_INT
        parser.setNextTokenResult(JsonToken.VALUE_NUMBER_INT);
        parser.setIntValueResult(0);
        assertFalse(parser.getValueAsBoolean(true));
        parser.setIntValueResult(5);
        assertTrue(parser.getValueAsBoolean(false));

        // ID_STRING
        parser.setNextTokenResult(JsonToken.VALUE_STRING);
        parser.setTextResult("true");
        assertTrue(parser.getValueAsBoolean(false));
        parser.setTextResult("false");
        assertFalse(parser.getValueAsBoolean(true));
        parser.setTextResult("null");
        assertFalse(parser.getValueAsBoolean(true));
        parser.setTextResult("other");
        assertFalse(parser.getValueAsBoolean(false));

        // ID_EMBEDDED_OBJECT
        parser.setNextTokenResult(JsonToken.VALUE_EMBEDDED_OBJECT);
        parser.setEmbeddedObjectResult(Boolean.TRUE);
        assertTrue(parser.getValueAsBoolean(false));
        parser.setEmbeddedObjectResult("not-a-boolean");
        assertFalse(parser.getValueAsBoolean(false));
    }

    @Test
    public void testGetValueAsInt() throws Throwable {
        ConcreteParser parser = new ConcreteParser();

        assertEquals(0, parser.getValueAsInt());
        assertEquals(42, parser.getValueAsInt(42));

        // VALUE_NUMBER_INT
        parser.setNextTokenResult(JsonToken.VALUE_NUMBER_INT);
        parser.setIntValueResult(100);
        assertEquals(100, parser.getValueAsInt());

        // VALUE_NUMBER_FLOAT
        parser.setNextTokenResult(JsonToken.VALUE_NUMBER_FLOAT);
        parser.setIntValueResult(200);
        assertEquals(200, parser.getValueAsInt());

        // STRING coercion
        parser.setNextTokenResult(JsonToken.VALUE_STRING);
        parser.setTextResult("123");
        assertEquals(123, parser.getValueAsInt(0));
        parser.setTextResult("null");
        assertEquals(0, parser.getValueAsInt(5));

        // TRUE / FALSE / NULL
        parser.setNextTokenResult(JsonToken.VALUE_TRUE);
        assertEquals(1, parser.getValueAsInt(0));
        parser.setNextTokenResult(JsonToken.VALUE_FALSE);
        assertEquals(0, parser.getValueAsInt(1));
        parser.setNextTokenResult(JsonToken.VALUE_NULL);
        assertEquals(0, parser.getValueAsInt(1));

        // EMBEDDED_OBJECT
        parser.setNextTokenResult(JsonToken.VALUE_EMBEDDED_OBJECT);
        parser.setEmbeddedObjectResult(Integer.valueOf(99));
        assertEquals(99, parser.getValueAsInt(0));
        parser.setEmbeddedObjectResult("string");
        assertEquals(0, parser.getValueAsInt(0));
    }

    @Test
    public void testGetValueAsLong() throws Throwable {
        ConcreteParser parser = new ConcreteParser();

        assertEquals(0L, parser.getValueAsLong());
        assertEquals(42L, parser.getValueAsLong(42L));

        // VALUE_NUMBER_INT
        parser.setNextTokenResult(JsonToken.VALUE_NUMBER_INT);
        parser.setLongValueResult(100L);
        assertEquals(100L, parser.getValueAsLong());

        // VALUE_NUMBER_FLOAT
        parser.setNextTokenResult(JsonToken.VALUE_NUMBER_FLOAT);
        parser.setLongValueResult(200L);
        assertEquals(200L, parser.getValueAsLong());

        // STRING coercion
        parser.setNextTokenResult(JsonToken.VALUE_STRING);
        parser.setTextResult("123456");
        assertEquals(123456L, parser.getValueAsLong(0L));
        parser.setTextResult("null");
        assertEquals(0L, parser.getValueAsLong(5L));

        // TRUE / FALSE / NULL
        parser.setNextTokenResult(JsonToken.VALUE_TRUE);
        assertEquals(1L, parser.getValueAsLong(0L));
        parser.setNextTokenResult(JsonToken.VALUE_FALSE);
        assertEquals(0L, parser.getValueAsLong(1L));
        parser.setNextTokenResult(JsonToken.VALUE_NULL);
        assertEquals(0L, parser.getValueAsLong(1L));

        // EMBEDDED_OBJECT
        parser.setNextTokenResult(JsonToken.VALUE_EMBEDDED_OBJECT);
        parser.setEmbeddedObjectResult(Long.valueOf(99L));
        assertEquals(99L, parser.getValueAsLong(0L));
        parser.setEmbeddedObjectResult("string");
        assertEquals(0L, parser.getValueAsLong(0L));
    }

    @Test
    public void testGetValueAsDouble() throws Throwable {
        ConcreteParser parser = new ConcreteParser();

        assertEquals(10.0, parser.getValueAsDouble(10.0), 0.0);

        // STRING coercion
        parser.setNextTokenResult(JsonToken.VALUE_STRING);
        parser.setTextResult("12.34");
        assertEquals(12.34, parser.getValueAsDouble(0.0), 0.0);
        parser.setTextResult("null");
        assertEquals(0.0, parser.getValueAsDouble(5.0), 0.0);

        // NUMBER INT / FLOAT
        parser.setNextTokenResult(JsonToken.VALUE_NUMBER_INT);
        parser.setDoubleValueResult(55.5);
        assertEquals(55.5, parser.getValueAsDouble(0.0), 0.0);

        // TRUE / FALSE / NULL
        parser.setNextTokenResult(JsonToken.VALUE_TRUE);
        assertEquals(1.0, parser.getValueAsDouble(0.0), 0.0);
        parser.setNextTokenResult(JsonToken.VALUE_FALSE);
        assertEquals(0.0, parser.getValueAsDouble(1.0), 0.0);
        parser.setNextTokenResult(JsonToken.VALUE_NULL);
        assertEquals(0.0, parser.getValueAsDouble(1.0), 0.0);

        // EMBEDDED_OBJECT
        parser.setNextTokenResult(JsonToken.VALUE_EMBEDDED_OBJECT);
        parser.setEmbeddedObjectResult(Double.valueOf(77.7));
        assertEquals(77.7, parser.getValueAsDouble(0.0), 0.0);
        parser.setEmbeddedObjectResult("string");
        assertEquals(0.0, parser.getValueAsDouble(0.0), 0.0);
    }

    @Test
    public void testGetValueAsString() throws Throwable {
        ConcreteParser parser = new ConcreteParser();

        assertNull(parser.getValueAsString());
        assertEquals("default", parser.getValueAsString("default"));

        parser.setNextTokenResult(JsonToken.VALUE_STRING);
        parser.setTextResult("hello");
        assertEquals("hello", parser.getValueAsString());
        assertEquals("hello", parser.getValueAsString("default"));

        parser.setNextTokenResult(JsonToken.VALUE_NULL);
        assertNull(parser.getValueAsString());
        assertEquals("default", parser.getValueAsString("default"));

        parser.setNextTokenResult(JsonToken.START_OBJECT);
        assertNull(parser.getValueAsString());
    }

    @Test
    public void testDecodeBase64() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        ByteArrayBuilder builder = new ByteArrayBuilder();
        Base64Variant variant = Base64Variants.MIME;

        // Valid base64 decode
        parser._decodeBase64("YWJjZA==", builder, variant);
        assertEquals(4, builder.toByteArray().length);

        // Invalid base64 triggering IllegalArgumentException -> _reportError
        try {
            parser._decodeBase64("invalid_base64_string!!!", builder, variant);
            fail("Expected JsonParseException");
        } catch (JsonParseException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testReportInvalidBase64() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        Base64Variant variant = Base64Variants.MIME;

        try {
            parser._reportInvalidBase64(variant, '\t', 0, "msg");
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Illegal white space character"));
        }

        try {
            parser._reportInvalidBase64(variant, '=', 0, null);
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unexpected padding character"));
        }

        try {
            parser._reportInvalidBase64(variant, (char) 1, 0, null);
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Illegal character"));
        }

        try {
            parser._reportInvalidBase64(variant, 'A', 0, null);
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Illegal character 'A'"));
        }
    }

    @Test
    public void testReportBase64EOF() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        try {
            parser._reportBase64EOF();
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unexpected end-of-String in base64 content"));
        }
    }

    @Test
    public void testErrorReportingMethods() throws Throwable {
        ConcreteParser parser = new ConcreteParser();
        parser.setNextTokenResult(JsonToken.VALUE_STRING);

        try {
            parser._reportUnexpectedChar(-1, "comment");
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unexpected end-of-input"));
        }

        try {
            parser._reportUnexpectedChar('x', null);
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unexpected character"));
        }

        try {
            parser._reportInvalidEOF();
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unexpected end-of-input"));
        }

        try {
            parser._reportInvalidEOFInValue();
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("in a value"));
        }

        try {
            parser._reportMissingRootWS('a');
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Expected space separating root-level values"));
        }

        try {
            parser._throwInvalidSpace('\t');
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Illegal character"));
        }

        try {
            parser._throwUnquotedSpace(0x0001, "context");
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Illegal unquoted character"));
        }

        try {
            parser._wrapError("wrapped", new RuntimeException("cause"));
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("wrapped"));
        }

        try {
            parser._throwInternal();
            fail("Expected exception");
        } catch (RuntimeException e) {
            // expected internal error
        }
    }

    @Test
    public void testHandleUnrecognizedCharacterEscape() throws Throwable {
        ConcreteParser parser = new ConcreteParser(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.getMask());
        char escaped = parser._handleUnrecognizedCharacterEscape('x');
        assertEquals('x', escaped);

        ConcreteParser parser2 = new ConcreteParser(JsonParser.Feature.ALLOW_SINGLE_QUOTES.getMask());
        char escapedQuote = parser2._handleUnrecognizedCharacterEscape('\'');
        assertEquals('\'', escapedQuote);

        ConcreteParser parser3 = new ConcreteParser();
        try {
            parser3._handleUnrecognizedCharacterEscape('z');
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unrecognized character escape"));
        }
    }

    @Test
    public void testAsciiHelpers() throws Throwable {
        byte[] bytes = ParserMinimalBase._asciiBytes("test");
        assertEquals(4, bytes.length);

        String str = ParserMinimalBase._ascii(bytes);
        assertEquals("test", str);
    }
}