package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

public class StringArrayDeserializerTest {

    @Test
    public void testDefaultInstance() throws Throwable {
        assertNotNull(StringArrayDeserializer.instance);
    }

    @Test
    public void testDeserializeStandardArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "[\"a\", \"b\", null, \"c\"]";
        JsonParser jp = mapper.getFactory().createParser(json);
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken(); // move to START_ARRAY

        String[] result = StringArrayDeserializer.instance.deserialize(jp, ctxt);
        assertNotNull(result);
        assertEquals(4, result.length);
        assertEquals("a", result[0]);
        assertEquals("b", result[1]);
        assertNull(result[2]);
        assertEquals("c", result[3]);
    }

    @Test
    public void testDeserializeEmptyArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "[]";
        JsonParser jp = mapper.getFactory().createParser(json);
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken();

        String[] result = StringArrayDeserializer.instance.deserialize(jp, ctxt);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDeserializeLargeArrayForChunkBuffering() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 5000; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"val").append(i).append("\"");
        }
        sb.append("]");

        JsonParser jp = mapper.getFactory().createParser(sb.toString());
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken();

        String[] result = StringArrayDeserializer.instance.deserialize(jp, ctxt);
        assertNotNull(result);
        assertEquals(5000, result.length);
        assertEquals("val0", result[0]);
        assertEquals("val4999", result[4999]);
    }

    @Test
    public void testHandleNonArrayThrowsException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "\"not-an-array\"";
        JsonParser jp = mapper.getFactory().createParser(json);
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken(); // VALUE_STRING

        try {
            StringArrayDeserializer.instance.deserialize(jp, ctxt);
            fail("Expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testHandleNonArrayAcceptSingleValueAsArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        String json = "\"singleVal\"";
        JsonParser jp = mapper.getFactory().createParser(json);
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken();

        String[] result = StringArrayDeserializer.instance.deserialize(jp, ctxt);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("singleVal", result[0]);
    }

    @Test
    public void testHandleNonArrayAcceptEmptyStringAsNullObject() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        String json = "\"\"";
        JsonParser jp = mapper.getFactory().createParser(json);
        DeserializationContext ctxt = mapper.getDeserializationContext();

        jp.nextToken();

        String[] result = StringArrayDeserializer.instance.deserialize(jp, ctxt);
        assertNull(result);
    }

    @Test
    public void testCreateContextualWithDefaultStringDeserializer() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JsonDeserializer<?> deser = StringArrayDeserializer.instance.createContextual(ctxt, null);
        assertSame(StringArrayDeserializer.instance, deser);
    }

    @Test
    public void testDeserializeWithType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "[\"test\"]";
        JsonParser jp = mapper.getFactory().createParser(json);
        DeserializationContext ctxt = mapper.getDeserializationContext();
        TypeDeserializer typeDeser = mapper.getDeserializationConfig()
                .findTypeDeserializer(mapper.constructType(String[].class));

        jp.nextToken();
        Object result = StringArrayDeserializer.instance.deserializeWithType(jp, ctxt, typeDeser);
        assertNotNull(result);
        assertTrue(result instanceof String[]);
    }
}