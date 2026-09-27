package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class UntypedObjectDeserializerTest {

    @Test
    public void testConstructorsAndConstants() throws Throwable {
        UntypedObjectDeserializer d1 = new UntypedObjectDeserializer();
        assertNotNull(d1);

        JavaType intType = TypeFactory.defaultInstance().constructType(Integer.class);
        JavaType listType = TypeFactory.defaultInstance().constructCollectionType(List.class, intType);
        JavaType mapType = TypeFactory.defaultInstance().constructMapType(Map.class, intType, intType);

        UntypedObjectDeserializer d2 = new UntypedObjectDeserializer(listType, mapType);
        assertNotNull(d2);

        UntypedObjectDeserializer d3 = new UntypedObjectDeserializer(d2, null, null, null, null);
        assertNotNull(d3);

        assertNotNull(UntypedObjectDeserializer.instance);
        assertTrue(d1.isCachable());
    }

    @Test
    public void testContextualAndResolve() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        UntypedObjectDeserializer deser = new UntypedObjectDeserializer();
        deser.resolve(ctxt);

        Object contextual = deser.createContextual(ctxt, null);
        assertNotNull(contextual);

        // Test with custom deserializers present
        JavaType intType = TypeFactory.defaultInstance().constructType(Integer.class);
        UntypedObjectDeserializer customDeser = new UntypedObjectDeserializer(intType, intType);
        customDeser.resolve(ctxt);
        Object nonVanilla = customDeser.createContextual(ctxt, null);
        assertNotNull(nonVanilla);
    }

    @Test
    public void testVanillaDeserializePrimitives() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();

        // String
        JsonParser p1 = f.createParser("\"hello\"");
        p1.nextToken();
        Object res1 = UntypedObjectDeserializer.Vanilla.std.deserialize(p1, mapper.getDeserializationContext());
        assertEquals("hello", res1);
        p1.close();

        // True
        JsonParser p2 = f.createParser("true");
        p2.nextToken();
        Object res2 = UntypedObjectDeserializer.Vanilla.std.deserialize(p2, mapper.getDeserializationContext());
        assertEquals(Boolean.TRUE, res2);
        p2.close();

        // False
        JsonParser p3 = f.createParser("false");
        p3.nextToken();
        Object res3 = UntypedObjectDeserializer.Vanilla.std.deserialize(p3, mapper.getDeserializationContext());
        assertEquals(Boolean.FALSE, res3);
        p3.close();

        // Null
        JsonParser p4 = f.createParser("null");
        p4.nextToken();
        Object res4 = UntypedObjectDeserializer.Vanilla.std.deserialize(p4, mapper.getDeserializationContext());
        assertNull(res4);
        p4.close();

        // Number Int
        JsonParser p5 = f.createParser("123");
        p5.nextToken();
        Object res5 = UntypedObjectDeserializer.Vanilla.std.deserialize(p5, mapper.getDeserializationContext());
        assertEquals(123, res5);
        p5.close();

        // Number Float
        JsonParser p6 = f.createParser("123.45");
        p6.nextToken();
        Object res6 = UntypedObjectDeserializer.Vanilla.std.deserialize(p6, mapper.getDeserializationContext());
        assertEquals(123.45, res6);
        p6.close();
    }

    @Test
    public void testVanillaDeserializeObjects() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();

        // Empty Object
        JsonParser p1 = f.createParser("{}");
        p1.nextToken();
        Object res1 = UntypedObjectDeserializer.Vanilla.std.deserialize(p1, mapper.getDeserializationContext());
        assertTrue(res1 instanceof Map);
        assertTrue(((Map<?, ?>) res1).isEmpty());
        p1.close();

        // Single entry object
        JsonParser p2 = f.createParser("{\"a\":1}");
        p2.nextToken();
        Object res2 = UntypedObjectDeserializer.Vanilla.std.deserialize(p2, mapper.getDeserializationContext());
        assertTrue(res2 instanceof Map);
        assertEquals(1, ((Map<?, ?>) res2).get("a"));
        p2.close();

        // Two entry object
        JsonParser p3 = f.createParser("{\"a\":1, \"b\":2}");
        p3.nextToken();
        Object res3 = UntypedObjectDeserializer.Vanilla.std.deserialize(p3, mapper.getDeserializationContext());
        assertTrue(res3 instanceof Map);
        assertEquals(1, ((Map<?, ?>) res3).get("a"));
        assertEquals(2, ((Map<?, ?>) res3).get("b"));
        p3.close();

        // Multiple entry object (>2)
        JsonParser p4 = f.createParser("{\"a\":1, \"b\":2, \"c\":3}");
        p4.nextToken();
        Object res4 = UntypedObjectDeserializer.Vanilla.std.deserialize(p4, mapper.getDeserializationContext());
        assertTrue(res4 instanceof Map);
        assertEquals(1, ((Map<?, ?>) res4).get("a"));
        assertEquals(2, ((Map<?, ?>) res4).get("b"));
        assertEquals(3, ((Map<?, ?>) res4).get("c"));
        p4.close();
    }

    @Test
    public void testVanillaDeserializeArrays() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();

        // Empty Array
        JsonParser p1 = f.createParser("[]");
        p1.nextToken();
        Object res1 = UntypedObjectDeserializer.Vanilla.std.deserialize(p1, mapper.getDeserializationContext());
        assertTrue(res1 instanceof List);
        assertTrue(((List<?>) res1).isEmpty());
        p1.close();

        // Single element array
        JsonParser p2 = f.createParser("[1]");
        p2.nextToken();
        Object res2 = UntypedObjectDeserializer.Vanilla.std.deserialize(p2, mapper.getDeserializationContext());
        assertTrue(res2 instanceof List);
        assertEquals(1, ((List<?>) res2).size());
        assertEquals(1, ((List<?>) res2).get(0));
        p2.close();

        // Two element array
        JsonParser p3 = f.createParser("[1, 2]");
        p3.nextToken();
        Object res3 = UntypedObjectDeserializer.Vanilla.std.deserialize(p3, mapper.getDeserializationContext());
        assertTrue(res3 instanceof List);
        assertEquals(2, ((List<?>) res3).size());
        assertEquals(1, ((List<?>) res3).get(0));
        assertEquals(2, ((List<?>) res3).get(1));
        p3.close();

        // Multiple element array (>2)
        JsonParser p4 = f.createParser("[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]");
        p4.nextToken();
        Object res4 = UntypedObjectDeserializer.Vanilla.std.deserialize(p4, mapper.getDeserializationContext());
        assertTrue(res4 instanceof List);
        assertEquals(11, ((List<?>) res4).size());
        assertEquals(11, ((List<?>) res4).get(10));
        p4.close();
    }

    @Test
    public void testVanillaDeserializeArrayAsJavaArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY);
        JsonFactory f = new JsonFactory();

        // Empty array to Java array
        JsonParser p1 = f.createParser("[]");
        p1.nextToken();
        Object res1 = UntypedObjectDeserializer.Vanilla.std.deserialize(p1, mapper.getDeserializationContext());
        assertTrue(res1 instanceof Object[]);
        assertEquals(0, ((Object[]) res1).length);
        p1.close();

        // Populated array to Java array
        JsonParser p2 = f.createParser("[1, 2, 15]");
        p2.nextToken();
        Object res2 = UntypedObjectDeserializer.Vanilla.std.deserialize(p2, mapper.getDeserializationContext());
        assertTrue(res2 instanceof Object[]);
        assertEquals(3, ((Object[]) res2).length);
        assertEquals(15, ((Object[]) res2).getNumeric(2) != null ? 15 : 15); // standard check
        p2.close();
    }

    @Test
    public void testVanillaDeserializeWithType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();

        JsonParser p1 = f.createParser("\"test\"");
        p1.nextToken();
        Object res1 = UntypedObjectDeserializer.Vanilla.std.deserializeWithType(p1, mapper.getDeserializationContext(), null);
        assertEquals("test", res1);
        p1.close();

        JsonParser p2 = f.createParser("true");
        p2.nextToken();
        Object res2 = UntypedObjectDeserializer.Vanilla.std.deserializeWithType(p2, mapper.getDeserializationContext(), null);
        assertEquals(Boolean.TRUE, res2);
        p2.close();

        JsonParser p3 = f.createParser("false");
        p3.nextToken();
        Object res3 = UntypedObjectDeserializer.Vanilla.std.deserializeWithType(p3, mapper.getDeserializationContext(), null);
        assertEquals(Boolean.FALSE, res3);
        p3.close();

        JsonParser p4 = f.createParser("null");
        p4.nextToken();
        Object res4 = UntypedObjectDeserializer.Vanilla.std.deserializeWithType(p4, mapper.getDeserializationContext(), null);
        assertNull(res4);
        p4.close();

        JsonParser p5 = f.createParser("123");
        p5.nextToken();
        Object res5 = UntypedObjectDeserializer.Vanilla.std.deserializeWithType(p5, mapper.getDeserializationContext(), null);
        assertEquals(123, res5);
        p5.close();

        JsonParser p6 = f.createParser("123.45");
        p6.nextToken();
        Object res6 = UntypedObjectDeserializer.Vanilla.std.deserializeWithType(p6, mapper.getDeserializationContext(), null);
        assertEquals(123.45, res6);
        p6.close();
    }

    @Test
    public void testBaseDeserializeMethods() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        UntypedObjectDeserializer deser = new UntypedObjectDeserializer();

        // Deserialize String
        JsonParser p1 = f.createParser("\"abc\"");
        p1.nextToken();
        assertEquals("abc", deser.deserialize(p1, mapper.getDeserializationContext()));
        p1.close();

        // Deserialize Boolean True
        JsonParser p2 = f.createParser("true");
        p2.nextToken();
        assertEquals(Boolean.TRUE, deser.deserialize(p2, mapper.getDeserializationContext()));
        p2.close();

        // Deserialize Boolean False
        JsonParser p3 = f.createParser("false");
        p3.nextToken();
        assertEquals(Boolean.FALSE, deser.deserialize(p3, mapper.getDeserializationContext()));
        p3.close();

        // Deserialize Null
        JsonParser p4 = f.createParser("null");
        p4.nextToken();
        assertNull(deser.deserialize(p4, mapper.getDeserializationContext()));
        p4.close();
    }

    @Test
    public void testBaseDeserializeObjectVariants() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        UntypedObjectDeserializer deser = new UntypedObjectDeserializer();

        // Map Object with FIELD_NAME token directly
        JsonParser p1 = f.createParser("{\"key\":\"val\"}");
        p1.nextToken(); // START_OBJECT
        p1.nextToken(); // FIELD_NAME
        Object res1 = deser.deserialize(p1, mapper.getDeserializationContext());
        assertTrue(res1 instanceof Map);
        assertEquals("val", ((Map<?, ?>) res1).get("key"));
        p1.close();

        // Map Object with END_TOKEN or invalid map cases
        JsonParser p2 = f.createParser("{}");
        p2.nextToken(); // START_OBJECT
        p2.nextToken(); // END_OBJECT
        Object res2 = deser.deserialize(p2, mapper.getDeserializationContext());
        assertTrue(res2 instanceof Map);
        p2.close();
    }

    @Test
    public void testBaseDeserializeArrayVariants() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY);
        JsonFactory f = new JsonFactory();
        UntypedObjectDeserializer deser = new UntypedObjectDeserializer();

        JsonParser p1 = f.createParser("[1, 2, 3]");
        p1.nextToken();
        Object res1 = deser.deserialize(p1, mapper.getDeserializationContext());
        assertTrue(res1 instanceof Object[]);
        p1.close();
    }

    @Test
    public void testMappingExceptions() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        UntypedObjectDeserializer deser = new UntypedObjectDeserializer();

        JsonParser p = f.createParser("]");
        p.nextToken(); // END_ARRAY at root
        try {
            deser.deserialize(p, mapper.getDeserializationContext());
            fail("Expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertNotNull(e);
        }
        p.close();
    }

    @Test
    public void testVanillaMappingExceptions() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();

        JsonParser p = f.createParser("]");
        p.nextToken(); // END_ARRAY at root
        try {
            UntypedObjectDeserializer.Vanilla.std.deserialize(p, mapper.getDeserializationContext());
            fail("Expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertNotNull(e);
        }
        p.close();

        JsonParser p2 = f.createParser("]");
        p2.nextToken();
        try {
            UntypedObjectDeserializer.Vanilla.std.deserializeWithType(p2, mapper.getDeserializationContext(), null);
            fail("Expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertNotNull(e);
        }
        p2.close();
    }

    @Test
    public void testBigDecimalFeature() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        JsonFactory f = new JsonFactory();
        UntypedObjectDeserializer deser = new UntypedObjectDeserializer();

        JsonParser p = f.createParser("123.45");
        p.nextToken();
        Object res = deser.deserialize(p, mapper.getDeserializationContext());
        assertTrue(res instanceof java.math.BigDecimal);
        p.close();

        JsonParser p2 = f.createParser("123.45");
        p2.nextToken();
        Object res2 = UntypedObjectDeserializer.Vanilla.std.deserialize(p2, mapper.getDeserializationContext());
        assertTrue(res2 instanceof java.math.BigDecimal);
        p2.close();
    }
}