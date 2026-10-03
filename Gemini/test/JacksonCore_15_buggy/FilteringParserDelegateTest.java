package com.fasterxml.jackson.core.filter;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.util.JsonParserDelegate;

public class FilteringParserDelegateTest {

    private static class DummyJsonParser extends JsonParser {
        private JsonToken[] tokens;
        private int index = 0;
        private String currentName;
        private JsonLocation location = new JsonLocation(ContentReference.unknown(), 0L, 0, 0);

        public DummyJsonParser(JsonToken[] tokens) {
            this.tokens = tokens;
        }

        @Override
        public ObjectCodec getCodec() { return null; }

        @Override
        public void setCodec(ObjectCodec c) {}

        @Override
        public Version version() { return Version.unknownVersion(); }

        @Override
        public void close() throws IOException {}

        @Override
        public boolean isClosed() { return false; }

        @Override
        public JsonStreamContext getParsingContext() {
            return null;
        }

        @Override
        public void overrideCurrentName(String name) { this.currentName = name; }

        @Override
        public String getCurrentName() throws IOException { return currentName; }

        @Override
        public void assignCurrentName(String name) { this.currentName = name; }

        @Override
        public JsonToken nextToken() throws IOException {
            if (index < tokens.length) {
                return tokens[index++];
            }
            return null;
        }

        @Override
        public JsonParser skipChildren() throws IOException {
            int open = 1;
            while (index < tokens.length) {
                JsonToken t = tokens[index++];
                if (t != null) {
                    if (t.isStructStart()) {
                        open++;
                    } else if (t.isStructEnd()) {
                        open--;
                        if (open == 0) break;
                    }
                }
            }
            return this;
        }

        @Override
        public boolean hasTextCharacters() { return false; }

        @Override
        public byte[] getBinaryValue(Base64Variant b64variant) throws IOException { return new byte[0]; }

        @Override
        public JsonLocation getCurrentLocation() { return location; }

        @Override
        public String getText() throws IOException { return "dummyText"; }

        @Override
        public char[] getTextCharacters() throws IOException { return new char[0]; }

        @Override
        public int getTextLength() throws IOException { return 0; }

        @Override
        public int getTextOffset() throws IOException { return 0; }

        @Override
        public BigInteger getBigIntegerValue() throws IOException { return BigInteger.ZERO; }

        @Override
        public byte getByteValue() throws IOException { return 0; }

        @Override
        public short getShortValue() throws IOException { return 0; }

        @Override
        public BigDecimal getDecimalValue() throws IOException { return BigDecimal.ZERO; }

        @Override
        public double getDoubleValue() throws IOException { return 0.0; }

        @Override
        public float getFloatValue() throws IOException { return 0.0f; }

        @Override
        public int getIntValue() throws IOException { return 0; }

        @Override
        public long getLongValue() throws IOException { return 0L; }

        @Override
        public NumberType getNumberType() throws IOException { return NumberType.INT; }

        @Override
        public Number getNumberValue() throws IOException { return Integer.valueOf(0); }

        @Override
        public JsonLocation getTokenLocation() { return location; }
    }

    @Test
    public void testConstructionAndBasicGetters() throws Throwable {
        JsonToken[] tokens = new JsonToken[] { JsonToken.START_OBJECT };
        DummyJsonParser dummy = new DummyJsonParser(tokens);
        TokenFilter filter = TokenFilter.INCLUDE_ALL;
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, filter, true, true);

        assertNotNull(delegate.getFilter());
        assertEquals(0, delegate.getMatchCount());
        assertFalse(delegate.hasCurrentToken());
        assertEquals(JsonTokenId.ID_NO_TOKEN, delegate.getCurrentTokenId());
        assertNull(delegate.getCurrentToken());
        assertFalse(delegate.isExpectedStartArrayToken());
        assertFalse(delegate.isExpectedStartObjectToken());
        assertNull(delegate.getLastClearedToken());
    }

    @Test
    public void testClearAndCurrentToken() throws Throwable {
        JsonToken[] tokens = new JsonToken[] { JsonToken.START_OBJECT };
        DummyJsonParser dummy = new DummyJsonParser(tokens);
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, TokenFilter.INCLUDE_ALL, true, true);

        assertNull(delegate.nextToken());
        // Force set or test clearCurrentToken when _currToken is null
        delegate.clearCurrentToken();
        assertNull(delegate.getLastClearedToken());
    }

    @Test
    public void testOverrideCurrentNameThrows() throws Throwable {
        JsonToken[] tokens = new JsonToken[] { JsonToken.START_OBJECT };
        DummyJsonParser dummy = new DummyJsonParser(tokens);
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, TokenFilter.INCLUDE_ALL, true, true);

        try {
            delegate.overrideCurrentName("test");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Can not currently override name"));
        }
    }

    @Test
    public void testNextTokenIncludeAllObject() throws Throwable {
        JsonToken[] tokens = new JsonToken[] {
            JsonToken.START_OBJECT,
            JsonToken.FIELD_NAME,
            JsonToken.VALUE_NUMBER_INT,
            JsonToken.END_OBJECT
        };
        DummyJsonParser dummy = new DummyJsonParser(tokens);
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(JsonToken.START_OBJECT, delegate.nextToken());
        assertTrue(delegate.hasToken(JsonToken.START_OBJECT));
        assertTrue(delegate.hasTokenId(JsonTokenId.ID_START_OBJECT));
        assertTrue(delegate.isExpectedStartObjectToken());

        assertEquals(JsonToken.FIELD_NAME, delegate.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextToken());
        assertEquals(JsonToken.END_OBJECT, delegate.nextToken());
        assertNull(delegate.nextToken());
    }

    @Test
    public void testSkipChildrenWithoutStruct() throws Throwable {
        JsonToken[] tokens = new JsonToken[] {
            JsonToken.VALUE_STRING
        };
        DummyJsonParser dummy = new DummyJsonParser(tokens);
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, TokenFilter.INCLUDE_ALL, true, true);

        JsonParser result = delegate.skipChildren();
        assertNotNull(result);
    }

    @Test
    public void testNextValue() throws Throwable {
        JsonToken[] tokens = new JsonToken[] {
            JsonToken.START_OBJECT,
            JsonToken.FIELD_NAME,
            JsonToken.VALUE_NUMBER_INT,
            JsonToken.END_OBJECT
        };
        DummyJsonParser dummy = new DummyJsonParser(tokens);
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(JsonToken.START_OBJECT, delegate.nextValue());
        // Next value encounters FIELD_NAME, should skip to the value
        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextValue());
    }

    @Test
    public void testValueAccessors() throws Throwable {
        DummyJsonParser dummy = new DummyJsonParser(new JsonToken[0]);
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(0, delegate.getValueAsInt());
        assertEquals(5, delegate.getValueAsInt(5));
        assertEquals(0L, delegate.getValueAsLong());
        assertEquals(5L, delegate.getValueAsLong(5L));
        assertEquals(0.0, delegate.getValueAsDouble(), 0.0);
        assertEquals(5.0, delegate.getValueAsDouble(5.0), 0.0);
        assertFalse(delegate.getValueAsBoolean());
        assertTrue(delegate.getValueAsBoolean(true));
        assertNull(delegate.getValueAsString());
        assertEquals("default", delegate.getValueAsString("default"));
        assertNull(delegate.getEmbeddedObject());
        assertNotNull(delegate.getTokenLocation());
        assertNotNull(delegate.getCurrentLocation());
    }

    @Test
    public void testNumericAccessors() throws Throwable {
        DummyJsonParser dummy = new DummyJsonParser(new JsonToken[0]);
        FilteringParserDelegate delegate = new FilteringParserDelegate(dummy, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(BigInteger.ZERO, delegate.getBigIntegerValue());
        assertFalse(delegate.getBooleanValue());
        assertEquals((byte)0, delegate.getByteValue());
        assertEquals((short)0, delegate.getShortValue());
        assertEquals(BigDecimal.ZERO, delegate.getDecimalValue());
        assertEquals(0.0, delegate.getDoubleValue(), 0.0);
        assertEquals(0.0f, delegate.getFloatValue(), 0.0f);
        assertEquals(0, delegate.getIntValue());
        assertEquals(0L, delegate.getLongValue());
        assertEquals(NumberType.INT, delegate.getNumberType());
        assertNotNull(delegate.getNumberValue());
        assertFalse(delegate.hasTextCharacters());
        assertEquals("dummyText", delegate.getText());
    }
}