package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

public class StringArrayDeserializerTest {

    @Test
    public void testDefaultInstanceAndConstructors() throws Throwable {
        assertNotNull(StringArrayDeserializer.instance);
        
        StringArrayDeserializer deser = new StringArrayDeserializer();
        assertNotNull(deser);

        JsonDeserializer<String> dummyDeser = new JsonDeserializer<String>() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) {
                return "custom";
            }
        };
        StringArrayDeserializer customDeser = new StringArrayDeserializer(dummyDeser);
        assertNotNull(customDeser);
    }

    @Test
    public void testDeserializeWithType() throws Throwable {
        StringArrayDeserializer deser = StringArrayDeserializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        JsonParser jp = mapper.getFactory().createParser("[]");
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        TypeDeserializer typeDeser = new TypeDeserializer() {
            @Override
            public com.fasterxml.jackson.annotation.JsonTypeInfo.As getTypeInclusion() { return null; }
            @Override
            public String getPropertyName() { return ""; }
            @Override
            public com.fasterxml.jackson.databind.jsontype.TypeIdResolver getTypeIdResolver() { return null; }
            @Override
            public Class<?> getAccessibleType() { return null; }
            @Override
            public Object deserializeTypedFromObject(JsonParser jp, DeserializationContext ctxt) throws java.io.IOException { return null; }
            @Override
            public Object deserializeTypedFromAny(JsonParser jp, DeserializationContext ctxt) throws java.io.IOException { return null; }
            @Override
            public Object deserializeTypedFromScalar(JsonParser jp, DeserializationContext ctxt) throws java.io.IOException { return null; }
            @Override
            public Object deserializeTypedFromArray(JsonParser jp, DeserializationContext ctxt) throws java.io.IOException {
                return new String[] { "typed" };
            }
        };

        jp.nextToken();
        Object result = deser.deserializeWithType(jp, ctxt, typeDeser);
        assertNotNull(result);
        assertTrue(result instanceof String[]);
        jp.close();
    }

    @Test
    public void testHandleNonArrayThrowsException() throws Throwable {
        StringArrayDeserializer deser = StringArrayDeserializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        JsonParser jp = mapper.getFactory().createParser("\"someString\"");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken(); // VALUE_STRING
        try {
            deser.deserialize(jp, ctxt);
            fail("Expected mapping exception for non-array input");
        } catch (JsonMappingException e) {
            assertNotNull(e);
        }
        jp.close();
    }

    @Test
    public void testHandleNonArrayAcceptSingleValue() throws Throwable {
        StringArrayDeserializer deser = StringArrayDeserializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        JsonParser jp = mapper.getFactory().createParser("\"singleValue\"");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken();
        String[] result = deser.deserialize(jp, ctxt);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("singleValue", result[0]);
        jp.close();
    }

    @Test
    public void testHandleNonArrayAcceptEmptyStringAsNull() throws Throwable {
        StringArrayDeserializer deser = StringArrayDeserializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        JsonParser jp = mapper.getFactory().createParser("\"\"");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken();
        String[] result = deser.deserialize(jp, ctxt);
        assertNull(result);
        jp.close();
    }

    @Test
    public void testCreateContextual() throws Throwable {
        StringArrayDeserializer deser = StringArrayDeserializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JsonDeserializer<?> contextual = deser.createContextual(ctxt, null);
        assertNotNull(contextual);
    }
}