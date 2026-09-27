package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

public class NumberSerializerTest {

    private static class DummyJsonGenerator extends com.fasterxml.jackson.core.base.GeneratorBase {
        public StringBuilder output = new StringBuilder();
        public DummyJsonGenerator() {
            super(0, null);
        }
        @Override public void writeStartArray() throws IOException {}
        @Override public void writeEndArray() throws IOException {}
        @Override public void writeStartObject() throws IOException {}
        @Override public void writeEndObject() throws IOException {}
        @Override public void writeFieldName(String name) throws IOException {}
        @Override public void writeString(String text) throws IOException {}
        @Override public void writeString(char[] text, int offset, int len) throws IOException {}
        @Override public void writeRawUTF8String(byte[] text, int offset, int len) throws IOException {}
        @Override public void writeUTF8String(byte[] text, int offset, int len) throws IOException {}
        @Override public void writeRaw(String text) throws IOException {}
        @Override public void writeRaw(String text, int offset, int len) throws IOException {}
        @Override public void writeRaw(char[] text, int offset, int len) throws IOException {}
        @Override public void writeRaw(char c) throws IOException {}
        @Override public void writeBinary(com.fasterxml.jackson.core.Base64Variant bvariant, byte[] data, int offset, int len) throws IOException {}
        @Override public void writeNumber(short v) throws IOException { output.append(v); }
        @Override public void writeNumber(int v) throws IOException { output.append(v); }
        @Override public void writeNumber(long v) throws IOException { output.append(v); }
        @Override public void writeNumber(BigInteger v) throws IOException { output.append(v); }
        @Override public void writeNumber(double v) throws IOException { output.append(v); }
        @Override public void writeNumber(float v) throws IOException { output.append(v); }
        @Override public void writeNumber(BigDecimal v) throws IOException { output.append(v); }
        @Override public void writeNumber(String encodedValue) throws IOException { output.append(encodedValue); }
        @Override public void flush() throws IOException {}
        @Override protected void _releaseBuffers() {}
        @Override protected void _verifyValueWrite(String typeMsg) throws IOException {}
        @Override public boolean canUseSchema(com.fasterxml.jackson.core.FormatSchema schema) { return false; }
    }

    private static class CustomNumber extends Number {
        private final double val;
        public CustomNumber(double val) { this.val = val; }
        public int intValue() { return (int) val; }
        public long longValue() { return (long) val; }
        public float floatValue() { return (float) val; }
        public double doubleValue() { return val; }
        @Override
        public String toString() {
            return "Custom:" + val;
        }
    }

    @Test
    public void testConstructorsAndConstants() throws Throwable {
        assertNotNull(NumberSerializer.instance);
        NumberSerializer bigIntSer = new NumberSerializer(BigInteger.class);
        assertNotNull(bigIntSer);
        NumberSerializer bigDecSer = new NumberSerializer(BigDecimal.class);
        assertNotNull(bigDecSer);
    }

    @Test
    public void testCreateContextual() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();
        
        NumberSerializer serializer = new NumberSerializer(BigDecimal.class);
        com.fasterxml.jackson.databind.ser.JsonSerializer<?> contextual = serializer.createContextual(prov, null);
        assertNotNull(contextual);

        // Test with JsonFormat shape STRING
        BeanProperty.Std property = new BeanProperty.Std(
                com.fasterxml.jackson.databind.PropertyName.construct("test"),
                null, null, null, null,
                JsonFormat.Value.forShape(JsonFormat.Shape.STRING),
                com.fasterxml.jackson.databind.cfg.MapperConfig.OVERRIDE_AMUL);
        
        com.fasterxml.jackson.databind.ser.JsonSerializer<?> stringContextual = serializer.createContextual(prov, property);
        assertTrue(stringContextual instanceof ToStringSerializer);
    }

    @Test
    public void testSerializeBigDecimal() throws Throwable {
        NumberSerializer serializer = new NumberSerializer(BigDecimal.class);
        DummyJsonGenerator g = new DummyJsonGenerator();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();

        BigDecimal bd = new BigDecimal("123.456");
        serializer.serialize(bd, g, prov);
        assertEquals("123.456", g.output.toString());
    }

    @Test
    public void testSerializeBigInteger() throws Throwable {
        NumberSerializer serializer = new NumberSerializer(BigInteger.class);
        DummyJsonGenerator g = new DummyJsonGenerator();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();

        BigInteger bi = new BigInteger("987654321");
        serializer.serialize(bi, g, prov);
        assertEquals("987654321", g.output.toString());
    }

    @Test
    public void testSerializeLong() throws Throwable {
        NumberSerializer serializer = new NumberSerializer(Number.class);
        DummyJsonGenerator g = new DummyJsonGenerator();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();

        Long l = Long.valueOf(123456789L);
        serializer.serialize(l, g, prov);
        assertEquals("123456789", g.output.toString());
    }

    @Test
    public void testSerializeDouble() throws Throwable {
        NumberSerializer serializer = new NumberSerializer(Number.class);
        DummyJsonGenerator g = new DummyJsonGenerator();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();

        Double d = Double.valueOf(3.14);
        serializer.serialize(d, g, prov);
        assertEquals("3.14", g.output.toString());
    }

    @Test
    public void testSerializeFloat() throws Throwable {
        NumberSerializer serializer = new NumberSerializer(Number.class);
        DummyJsonGenerator g = new DummyJsonGenerator();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();

        Float f = Float.valueOf(2.5f);
        serializer.serialize(f, g, prov);
        assertEquals("2.5", g.output.toString());
    }

    @Test
    public void testSerializeIntegerByteShort() throws Throwable {
        NumberSerializer serializer = new NumberSerializer(Number.class);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();

        DummyJsonGenerator g1 = new DummyJsonGenerator();
        serializer.serialize(Integer.valueOf(42), g1, prov);
        assertEquals("42", g1.output.toString());

        DummyJsonGenerator g2 = new DummyJsonGenerator();
        serializer.serialize(Byte.valueOf((byte) 7), g2, prov);
        assertEquals("7", g2.output.toString());

        DummyJsonGenerator g3 = new DummyJsonGenerator();
        serializer.serialize(Short.valueOf((short) 15), g3, prov);
        assertEquals("15", g3.output.toString());
    }

    @Test
    public void testSerializeFallback() throws Throwable {
        NumberSerializer serializer = new NumberSerializer(Number.class);
        DummyJsonGenerator g = new DummyJsonGenerator();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();

        CustomNumber cn = new CustomNumber(99.9);
        serializer.serialize(cn, g, prov);
        assertEquals("Custom:99.9", g.output.toString());
    }

    @Test
    public void testGetSchema() throws Throwable {
        NumberSerializer intSer = new NumberSerializer(BigInteger.class);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();
        JsonNode intSchema = intSer.getSchema(prov, null);
        assertNotNull(intSchema);

        NumberSerializer numSer = new NumberSerializer(Number.class);
        JsonNode numSchema = numSer.getSchema(prov, null);
        assertNotNull(numSchema);
    }

    @Test
    public void testAcceptJsonFormatVisitor() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider prov = mapper.getSerializerProviderInstance();
        JavaType type = mapper.constructType(Number.class);

        // Dummy visitor implementation using anonymous inner class (Java 6 compatible)
        JsonFormatVisitorWrapper visitor = new JsonFormatVisitorWrapper() {
            public SerializerProvider getProvider() { return null; }
            public void setProvider(SerializerProvider provider) {}
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonAnyFormatVisitor expectAnyFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor expectNumberFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor expectMapFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor expectStringFormat(JavaType type) throws JsonMappingException { return null; }
        };

        NumberSerializer bigIntSer = new NumberSerializer(BigInteger.class);
        bigIntSer.acceptJsonFormatVisitor(visitor, type);

        NumberSerializer bigDecSer = new NumberSerializer(BigDecimal.class);
        bigDecSer.acceptJsonFormatVisitor(visitor, mapper.constructType(BigDecimal.class));

        NumberSerializer otherSer = new NumberSerializer(Number.class);
        otherSer.acceptJsonFormatVisitor(visitor, type);
    }
}