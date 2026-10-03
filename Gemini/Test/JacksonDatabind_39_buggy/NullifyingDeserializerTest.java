package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

import java.io.IOException;

public class NullifyingDeserializerTest {

    @Test
    public void testInstanceNotNull() throws Throwable {
        assertNotNull(NullifyingDeserializer.instance);
    }

    @Test
    public void testConstructor() throws Throwable {
        NullifyingDeserializer deserializer = new NullifyingDeserializer();
        assertNotNull(deserializer);
    }

    @Test
    public void testDeserialize() throws Throwable {
        NullifyingDeserializer deserializer = new NullifyingDeserializer();
        
        JsonParser p = new DummyJsonParser();
        DeserializationContext ctxt = null;
        
        Object result = deserializer.deserialize(p, ctxt);
        assertNull(result);
    }

    @Test
    public void testDeserializeWithTypeStartArray() throws Throwable {
        NullifyingDeserializer deserializer = new NullifyingDeserializer();
        DummyJsonParser p = new DummyJsonParser();
        p.currentTokenId = JsonTokenId.ID_START_ARRAY;
        
        DummyTypeDeserializer typeDeserializer = new DummyTypeDeserializer();
        
        Object result = deserializer.deserializeWithType(p, null, typeDeserializer);
        assertEquals("deserializeTypedFromAny", result);
    }

    @Test
    public void testDeserializeWithTypeStartObject() throws Throwable {
        NullifyingDeserializer deserializer = new NullifyingDeserializer();
        DummyJsonParser p = new DummyJsonParser();
        p.currentTokenId = JsonTokenId.ID_START_OBJECT;
        
        DummyTypeDeserializer typeDeserializer = new DummyTypeDeserializer();
        
        Object result = deserializer.deserializeWithType(p, null, typeDeserializer);
        assertEquals("deserializeTypedFromAny", result);
    }

    @Test
    public void testDeserializeWithTypeFieldName() throws Throwable {
        NullifyingDeserializer deserializer = new NullifyingDeserializer();
        DummyJsonParser p = new DummyJsonParser();
        p.currentTokenId = JsonTokenId.ID_FIELD_NAME;
        
        DummyTypeDeserializer typeDeserializer = new DummyTypeDeserializer();
        
        Object result = deserializer.deserializeWithType(p, null, typeDeserializer);
        assertEquals("deserializeTypedFromAny", result);
    }

    @Test
    public void testDeserializeWithTypeDefault() throws Throwable {
        NullifyingDeserializer deserializer = new NullifyingDeserializer();
        DummyJsonParser p = new DummyJsonParser();
        p.currentTokenId = JsonTokenId.ID_STRING;
        
        DummyTypeDeserializer typeDeserializer = new DummyTypeDeserializer();
        
        Object result = deserializer.deserializeWithType(p, null, typeDeserializer);
        assertNull(result);
    }

    private static class DummyJsonParser extends com.fasterxml.jackson.core.util.JsonParserDelegate {
        public int currentTokenId = JsonTokenId.ID_NO_TOKEN;

        public DummyJsonParser() {
            super(null);
        }

        @Override
        public JsonParser skipChildren() throws IOException {
            return this;
        }

        @Override
        public int getCurrentTokenId() {
            return currentTokenId;
        }
    }

    private static class DummyTypeDeserializer extends TypeDeserializer {
        public DummyTypeDeserializer() {
            super(null, null, null, false, null);
        }

        @Override
        public com.fasterxml.jackson.annotation.JsonTypeInfo.As getTypeInclusion() {
            return null;
        }

        @Override
        public String getPropertyName() {
            return null;
        }

        @Override
        public TypeDeserializer forProperty(com.fasterxml.jackson.databind.BeanProperty prop) {
            return this;
        }

        @Override
        public Object deserializeTypedFromObject(JsonParser jp, DeserializationContext ctxt) throws IOException {
            return "deserializeTypedFromObject";
        }

        @Override
        public Object deserializeTypedFromArray(JsonParser jp, DeserializationContext ctxt) throws IOException {
            return "deserializeTypedFromArray";
        }

        @Override
        public Object deserializeTypedFromScalar(JsonParser jp, DeserializationContext ctxt) throws IOException {
            return "deserializeTypedFromScalar";
        }

        @Override
        public Object deserializeTypedFromAny(JsonParser jp, DeserializationContext ctxt) throws IOException {
            return "deserializeTypedFromAny";
        }
    }
}