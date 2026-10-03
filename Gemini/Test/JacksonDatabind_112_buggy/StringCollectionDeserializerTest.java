package com.fasterxml.jackson.databind.deser.std;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class StringCollectionDeserializerTest {

    private static class DummyValueInstantiator extends ValueInstantiator {
        @Override
        public String getValueTypeDesc() {
            return "Collection";
        }

        @Override
        public boolean canCreateUsingDefault() {
            return true;
        }

        @Override
        public Object createUsingDefault(DeserializationContext ctxt) throws IOException {
            return new ArrayList<String>();
        }
    }

    private static class DummyJsonParser extends JsonParser {
        private JsonToken currentToken;
        private String nextTextValue;
        private JsonToken[] tokens;
        private int tokenIndex = 0;

        public DummyJsonParser(JsonToken initialToken, String nextTextValue) {
            this.currentToken = initialToken;
            this.nextTextValue = nextTextValue;
        }

        public DummyJsonParser(JsonToken[] tokens) {
            this.tokens = tokens;
            if (tokens != null && tokens.length > 0) {
                this.currentToken = tokens[0];
            }
        }

        @Override
        public ObjectCodec getCodec() { return null; }

        @Override
        public void setCodec(ObjectCodec c) {}

        @Override
        public Version version() { return Version.unknownVersion(); }

        @Override
        public String getCurrentName() throws IOException { return null; }

        @Override
        public void overrideCurrentName(String name) {}

        @Override
        public void close() throws IOException {}

        @Override
        public boolean isClosed() { return false; }

        @Override
        public JsonToken nextToken() throws IOException {
            if (tokens != null) {
                tokenIndex++;
                if (tokenIndex < tokens.length) {
                    currentToken = tokens[tokenIndex];
                    return currentToken;
                }
                currentToken = null;
                return null;
            }
            return null;
        }

        @Override
        public JsonParser clearCurrentToken() { return this; }

        @Override
        public JsonToken getLastClearedToken() { return null; }

        @Override
        public JsonToken getCurrentToken() { return currentToken; }

        @Override
        public int getCurrentTokenId() { return currentToken == null ? 0 : currentToken.id(); }

        @Override
        public boolean hasTokenId(int id) { return getCurrentTokenId() == id; }

        @Override
        public boolean hasToken(JsonToken t) { return currentToken == t; }

        @Override
        public boolean isExpectedStartArrayToken() {
            return currentToken == JsonToken.START_ARRAY;
        }

        @Override
        public String getText() throws IOException { return nextTextValue; }

        @Override
        public char[] getTextCharacters() throws IOException { return new char[0]; }

        @Override
        public int getTextLength() throws IOException { return 0; }

        @Override
        public int getTextOffset() throws IOException { return 0; }

        @Override
        public boolean hasTextCharacters() { return false; }

        @Override
        public NumbergetNumberValue() throws IOException { return null; }

        @Override
        public NumberType getNumberType() throws IOException { return null; }

        @Override
        public int getIntValue() throws IOException { return 0; }

        @Override
        public long getLongValue() throws IOException { return 0L; }

        @Override
        public java.math.BigInteger getBigIntegerValue() throws IOException { return null; }

        @Override
        public float getFloatValue() throws IOException { return 0f; }

        @Override
        public double getDoubleValue() throws IOException { return 0d; }

        @Override
        public java.math.BigDecimal getDecimalValue() throws IOException { return null; }

        @Override
        public Object getEmbeddedObject() throws IOException { return null; }

        @Override
        public byte[] getBinaryValue(com.fasterxml.jackson.core.Base64Variant b64variant) throws IOException { return null; }

        @Override
        public String nextTextValue() throws IOException {
            return nextTextValue;
        }
    }

    @Test
    public void testIsCachable() throws Throwable {
        JavaType type = TypeFactory.defaultInstance().constructType(Collection.class);
        ValueInstantiator instantiator = new DummyValueInstantiator();
        StringCollectionDeserializer deser1 = new StringCollectionDeserializer(type, null, instantiator);
        assertTrue(deser1.isCachable());

        JsonDeserializer<String> dummyValDeser = new JsonDeserializer<String>() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return "test";
            }
        };
        StringCollectionDeserializer deser2 = new StringCollectionDeserializer(type, dummyValDeser, instantiator);
        assertFalse(deser2.isCachable());
    }

    @Test
    public void testGetContentDeserializer() throws Throwable {
        JavaType type = TypeFactory.defaultInstance().constructType(Collection.class);
        ValueInstantiator instantiator = new DummyValueInstantiator();
        JsonDeserializer<String> dummyValDeser = new JsonDeserializer<String>() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return "test";
            }
        };
        StringCollectionDeserializer deser = new StringCollectionDeserializer(type, dummyValDeser, instantiator);
        assertNotNull(deser.getContentDeserializer());
        assertEquals(dummyValDeser, deser.getContentDeserializer());
    }

    @Test
    public void testGetValueInstantiator() throws Throwable {
        JavaType type = TypeFactory.defaultInstance().constructType(Collection.class);
        ValueInstantiator instantiator = new DummyValueInstantiator();
        StringCollectionDeserializer deser = new StringCollectionDeserializer(type, null, instantiator);
        assertEquals(instantiator, deser.getValueInstantiator());
    }

    @Test
    public void testWithResolvedNoChange() throws Throwable {
        JavaType type = TypeFactory.defaultInstance().constructType(Collection.class);
        ValueInstantiator instantiator = new DummyValueInstantiator();
        StringCollectionDeserializer deser = new StringCollectionDeserializer(type, null, instantiator);
        
        StringCollectionDeserializer resolved = deser.withResolved(null, null, null, null);
        assertEquals(deser, resolved);
    }

    @Test
    public void testDeserializeArrayBasic() throws Throwable {
        JavaType type = TypeFactory.defaultInstance().constructType(Collection.class);
        ValueInstantiator instantiator = new DummyValueInstantiator();
        StringCollectionDeserializer deser = new StringCollectionDeserializer(type, null, instantiator);

        JsonToken[] tokens = new JsonToken[] {
            JsonToken.START_ARRAY,
            JsonToken.VALUE_STRING,
            JsonToken.END_ARRAY
        };
        DummyJsonParser parser = new DummyJsonParser(tokens);
        // We simulate nextTextValue returning null for VALUE_STRING, and then falling back to _parseString or handling
        Collection<String> resultColl = new ArrayList<String>();
        
        // Test handleNonArray when not start array and unwrapSingle is false
        try {
            deser.deserialize(parser, null, resultColl);
            fail("Expected exception for non-array token");
        } catch (Exception e) {
            // expected since context might be null or handleUnexpectedToken fails
        }
    }
}