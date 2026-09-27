package com.fasterxml.jackson.databind.deser.std;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.*;

public class JsonNodeDeserializerTest {

    @Test
    public void testGetDeserializer() throws Throwable {
        JsonDeserializer<? extends JsonNode> objDeser = JsonNodeDeserializer.getDeserializer(ObjectNode.class);
        assertNotNull(objDeser);
        assertTrue(objDeser instanceof JsonNodeDeserializer.ObjectDeserializer);

        JsonDeserializer<? extends JsonNode> arrDeser = JsonNodeDeserializer.getDeserializer(ArrayNode.class);
        assertNotNull(arrDeser);
        assertTrue(arrDeser instanceof JsonNodeDeserializer.ArrayDeserializer);

        JsonDeserializer<? extends JsonNode> genericDeser = JsonNodeDeserializer.getDeserializer(JsonNode.class);
        assertNotNull(genericDeser);
        assertTrue(genericDeser instanceof JsonNodeDeserializer);
    }

    @Test
    public void testNullValues() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JsonNodeDeserializer deser = new JsonNodeDeserializer();

        assertNotNull(deser.getNullValue(ctxt));
        assertNotNull(deser.getNullValue());
        assertEquals(NullNode.getInstance(), deser.getNullValue(ctxt));
    }

    @Test
    public void testIsCachable() throws Throwable {
        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        assertTrue(deser.isCachable());
    }

    @Test
    public void testDeserializeObjectNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"key\":\"value\",\"num\":123,\"bool\":true,\"nullVal\":null,\"arr\":[1],\"obj\":{\"sub\":2}}");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer.ObjectDeserializer objDeser = JsonNodeDeserializer.ObjectDeserializer.getInstance();
        JsonNode result = objDeser.deserialize(p, ctxt);

        assertNotNull(result);
        assertTrue(result.isObject());
        ObjectNode obj = (ObjectNode) result;
        assertEquals("value", obj.get("key").asText());
        assertEquals(123, obj.get("num").asInt());
        assertTrue(obj.get("bool").asBoolean());
        assertTrue(obj.get("nullVal").isNull());
        assertTrue(obj.get("arr").isArray());
        assertTrue(obj.get("obj").isObject());
    }

    @Test
    public void testDeserializeArrayNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("[1, \"text\", true, null, {}, []]");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer.ArrayDeserializer arrDeser = JsonNodeDeserializer.ArrayDeserializer.getInstance();
        JsonNode result = arrDeser.deserialize(p, ctxt);

        assertNotNull(result);
        assertTrue(result.isArray());
        ArrayNode arr = (ArrayNode) result;
        assertEquals(6, arr.size());
        assertEquals(1, arr.get(0).asInt());
        assertEquals("text", arr.get(1).asText());
        assertTrue(arr.get(2).asBoolean());
        assertTrue(arr.get(3).isNull());
        assertTrue(arr.get(4).isObject());
        assertTrue(arr.get(5).isArray());
    }

    @Test
    public void testDeserializeGenericNullAndScalars() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("null");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        JsonNode result = deser.deserialize(p, ctxt);
        assertTrue(result.isNull());
    }

    @Test
    public void testDeserializeGenericBooleanTrue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("true");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        JsonNode result = deser.deserialize(p, ctxt);
        assertTrue(result.asBoolean());
    }

    @Test
    public void testDeserializeGenericBooleanFalse() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("false");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        JsonNode result = deser.deserialize(p, ctxt);
        assertFalse(result.asBoolean());
    }

    @Test
    public void testDeserializeGenericString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("\"hello world\"");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        JsonNode result = deser.deserialize(p, ctxt);
        assertEquals("hello world", result.asText());
    }

    @Test
    public void testDeserializeGenericIntAndLong() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("42");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        JsonNode result = deser.deserialize(p, ctxt);
        assertEquals(42, result.asInt());
    }

    @Test
    public void testDeserializeGenericFloatAndDecimal() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("3.14");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        JsonNode result = deser.deserialize(p, ctxt);
        assertEquals(3.14, result.asDouble(), 0.001);
    }

    @Test
    public void testObjectDeserializerMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("123");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer.ObjectDeserializer objDeser = JsonNodeDeserializer.ObjectDeserializer.getInstance();
        try {
            objDeser.deserialize(p, ctxt);
            fail("Expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testArrayDeserializerMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("123");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer.ArrayDeserializer arrDeser = JsonNodeDeserializer.ArrayDeserializer.getInstance();
        try {
            arrDeser.deserialize(p, ctxt);
            fail("Expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testDuplicateFieldHandling() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"dup\":1,\"dup\":2}");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        JsonNode result = deser.deserialize(p, ctxt);
        assertTrue(result.isObject());
        assertEquals(2, result.get("dup").asInt());
    }

    @Test
    public void testDuplicateFieldFailFeature() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"dup\":1,\"dup\":2}");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        try {
            deser.deserialize(p, ctxt);
            fail("Expected JsonMappingException due to duplicate key");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("Duplicate field"));
        }
    }

    @Test
    public void testEmptyObjectAndArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonNodeDeserializer deser = new JsonNodeDeserializer();

        JsonFactory f1 = new JsonFactory();
        JsonParser p1 = f1.createParser("{}");
        JsonNode r1 = deser.deserialize(p1, ctxt_helper(mapper));
        assertTrue(r1.isObject());
        assertTrue(r1.size() == 0);

        JsonFactory f2 = new JsonFactory();
        JsonParser p2 = f2.createParser("[]");
        JsonNode r2 = deser.deserialize(p2, ctxt_helper(mapper));
        assertTrue(r2.isArray());
        assertTrue(r2.size() == 0);
    }

    private DeserializationContext ctxt_helper(ObjectMapper mapper) {
        return mapper.getDeserializationContext();
    }
}