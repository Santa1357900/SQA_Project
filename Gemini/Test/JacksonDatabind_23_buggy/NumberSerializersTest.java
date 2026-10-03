package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonJsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.BeanProperty;

public class NumberSerializersTest {

    @Test
    public void testAddAll() throws Throwable {
        Map<String, com.fasterxml.jackson.databind.JsonSerializer<?>> map = new HashMap<String, com.fasterxml.jackson.databind.JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        
        assertTrue(map.containsKey(Integer.class.getName()));
        assertTrue(map.containsKey(Integer.TYPE.getName()));
        assertTrue(map.containsKey(Long.class.getName()));
        assertTrue(map.containsKey(Long.TYPE.getName()));
        assertTrue(map.containsKey(Byte.class.getName()));
        assertTrue(map.containsKey(Byte.TYPE.getName()));
        assertTrue(map.containsKey(Short.class.getName()));
        assertTrue(map.containsKey(Short.TYPE.getName()));
        assertTrue(map.containsKey(Float.class.getName()));
        assertTrue(map.containsKey(Float.TYPE.getName()));
        assertTrue(map.containsKey(Double.class.getName()));
        assertTrue(map.containsKey(Double.TYPE.getName()));
    }

    @Test
    public void testShortSerializer() throws Throwable {
        NumberSerializers.ShortSerializer serializer = new NumberSerializers.ShortSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProvider();
        
        JsonNode schema = serializer.getSchema(prov, null);
        assertNotNull(schema);

        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        serializer.serialize(Short.valueOf((short) 123), gen, prov);
        gen.flush();
        assertEquals("123", sw.toString());
    }

    @Test
    public void testIntegerSerializer() throws Throwable {
        NumberSerializers.IntegerSerializer serializer = new NumberSerializers.IntegerSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProvider();

        JsonNode schema = serializer.getSchema(prov, null);
        assertNotNull(schema);

        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        serializer.serialize(Integer.valueOf(456), gen, prov);
        gen.flush();
        assertEquals("456", sw.toString());

        sw = new StringWriter();
        gen = new JsonFactory().createGenerator(sw);
        serializer.serializeWithType(Integer.valueOf(789), gen, prov, null);
        gen.flush();
        assertEquals("789", sw.toString());
    }

    @Test
    public void testIntLikeSerializer() throws Throwable {
        NumberSerializers.IntLikeSerializer serializer = NumberSerializers.IntLikeSerializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProvider();

        JsonNode schema = serializer.getSchema(prov, null);
        assertNotNull(schema);

        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        serializer.serialize(Byte.valueOf((byte) 5), gen, prov);
        gen.flush();
        assertEquals("5", sw.toString());
    }

    @Test
    public void testLongSerializer() throws Throwable {
        NumberSerializers.LongSerializer serializer = NumberSerializers.LongSerializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProvider();

        JsonNode schema = serializer.getSchema(prov, null);
        assertNotNull(schema);

        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        serializer.serialize(Long.valueOf(123456789L), gen, prov);
        gen.flush();
        assertEquals("123456789", sw.toString());
    }

    @Test
    public void testFloatSerializer() throws Throwable {
        NumberSerializers.FloatSerializer serializer = NumberSerializers.FloatSerializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProvider();

        JsonNode schema = serializer.getSchema(prov, null);
        assertNotNull(schema);

        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        serializer.serialize(Float.valueOf(1.5f), gen, prov);
        gen.flush();
        assertEquals("1.5", sw.toString());
    }

    @Test
    public void testDoubleSerializer() throws Throwable {
        NumberSerializers.DoubleSerializer serializer = NumberSerializers.DoubleSerializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProvider();

        JsonNode schema = serializer.getSchema(prov, null);
        assertNotNull(schema);

        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        serializer.serialize(Double.valueOf(2.5), gen, prov);
        gen.flush();
        assertEquals("2.5", sw.toString());

        sw = new StringWriter();
        gen = new JsonFactory().createGenerator(sw);
        serializer.serializeWithType(Double.valueOf(3.5), gen, prov, null);
        gen.flush();
        assertEquals("3.5", sw.toString());
    }

    @Test
    public void testAcceptJsonFormatVisitor() throws Throwable {
        NumberSerializers.IntegerSerializer intSer = new NumberSerializers.IntegerSerializer();
        ObjectMapper mapper = new ObjectMapper();
        
        DummyJsonFormatVisitorWrapper visitor = new DummyJsonFormatVisitorWrapper();
        intSer.acceptJsonFormatVisitor(visitor, mapper.constructType(Integer.class));
        assertTrue(visitor.integerVisited);

        NumberSerializers.DoubleSerializer doubleSer = new NumberSerializers.DoubleSerializer();
        DummyJsonFormatVisitorWrapper visitor2 = new DummyJsonFormatVisitorWrapper();
        doubleSer.acceptJsonFormatVisitor(visitor2, mapper.constructType(Double.class));
        assertTrue(visitor2.numberVisited);
    }

    @Test
    public void testCreateContextual() throws Throwable {
        NumberSerializers.IntegerSerializer intSer = new NumberSerializers.IntegerSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProvider();
        
        com.fasterxml.jackson.databind.JsonSerializer<?> contextual = intSer.createContextual(prov, null);
        assertSame(intSer, contextual);
    }

    private static class DummyJsonFormatVisitorWrapper implements JsonFormatVisitorWrapper {
        public boolean integerVisited = false;
        public boolean numberVisited = false;

        public SerializerProvider getProvider() { return null; }
        public void setProvider(SerializerProvider p) {}

        public JsonIntegerFormatVisitor expectIntegerFormat(com.fasterxml.jackson.databind.JavaType type) {
            integerVisited = true;
            return new JsonIntegerFormatVisitor.Base() {
                public void numberType(com.fasterxml.jackson.core.JsonParser.NumberType t) {}
            };
        }

        public JsonNumberFormatVisitor expectNumberFormat(com.fasterxml.jackson.databind.JavaType type) {
            numberVisited = true;
            return new JsonNumberFormatVisitor.Base() {
                public void numberType(com.fasterxml.jackson.core.JsonParser.NumberType t) {}
            };
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor expectStringFormat(com.fasterxml.jackson.databind.JavaType type) { return null; }
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(com.fasterxml.jackson.databind.JavaType type) { return null; }
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(com.fasterxml.jackson.databind.JavaType type) { return null; }
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(com.fasterxml.jackson.databind.JavaType type) { return null; }
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(com.fasterxml.jackson.databind.JavaType type) { return null; }
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor expectMapFormat(com.fasterxml.jackson.databind.JavaType type) { return null; }
    }
}