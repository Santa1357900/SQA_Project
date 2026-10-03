package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

public class NumberSerializerTest {

    @Test
    public void testInstance() throws Throwable {
        assertNotNull(NumberSerializer.instance);
        assertTrue(NumberSerializer.instance.handledType() == Number.class);
    }

    @Test
    public void testConstructorsAndTypes() throws Throwable {
        NumberSerializer bigIntSerializer = new NumberSerializer(BigInteger.class);
        assertNotNull(bigIntSerializer);

        NumberSerializer bigDecSerializer = new NumberSerializer(BigDecimal.class);
        assertNotNull(bigDecSerializer);

        NumberSerializer customNumSerializer = new NumberSerializer(CustomNumber.class);
        assertNotNull(customNumSerializer);
    }

    @Test
    public void testGetSchema() throws Throwable {
        NumberSerializer bigIntSerializer = new NumberSerializer(BigInteger.class);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        
        JsonNode schemaInt = bigIntSerializer.getSchema(provider, null);
        assertNotNull(schemaInt);

        NumberSerializer bigDecSerializer = new NumberSerializer(BigDecimal.class);
        JsonNode schemaNum = bigDecSerializer.getSchema(provider, null);
        assertNotNull(schemaNum);
    }

    @Test
    public void testAcceptJsonFormatVisitor() throws Throwable {
        NumberSerializer bigIntSerializer = new NumberSerializer(BigInteger.class);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        JavaType type = provider.constructType(BigInteger.class);

        DummyJsonFormatVisitorWrapper visitor = new DummyJsonFormatVisitorWrapper();
        
        // Test _isInt == true branch
        bigIntSerializer.acceptJsonFormatVisitor(visitor, type);

        // Test BigDecimal branch (_isInt == false, handledType == BigDecimal.class)
        NumberSerializer bigDecSerializer = new NumberSerializer(BigDecimal.class);
        JavaType typeDec = provider.constructType(BigDecimal.class);
        bigDecSerializer.acceptJsonFormatVisitor(visitor, typeDec);

        // Test fallback generic number branch
        NumberSerializer customSerializer = new NumberSerializer(CustomNumber.class);
        JavaType typeCustom = provider.constructType(CustomNumber.class);
        customSerializer.acceptJsonFormatVisitor(visitor, typeCustom);
    }

    @Test
    public void testSerializeVariants() throws Throwable {
        NumberSerializer serializer = NumberSerializer.instance;
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        // BigDecimal
        DummyJsonGenerator g1 = new DummyJsonGenerator();
        serializer.serialize(new BigDecimal("123.45"), g1, provider);
        assertTrue(g1.wroteBigDecimal);

        // BigInteger
        DummyJsonGenerator g2 = new DummyJsonGenerator();
        serializer.serialize(new BigInteger("123456"), g2, provider);
        assertTrue(g2.wroteBigInteger);

        // Integer
        DummyJsonGenerator g3 = new DummyJsonGenerator();
        serializer.serialize(Integer.valueOf(10), g3, provider);
        assertTrue(g3.wroteInt);

        // Long
        DummyJsonGenerator g4 = new DummyJsonGenerator();
        serializer.serialize(Long.valueOf(20L), g4, provider);
        assertTrue(g4.wroteLong);

        // Double
        DummyJsonGenerator g5 = new DummyJsonGenerator();
        serializer.serialize(Double.valueOf(30.0), g5, provider);
        assertTrue(g5.wroteDouble);

        // Float
        DummyJsonGenerator g6 = new DummyJsonGenerator();
        serializer.serialize(Float.valueOf(40.0f), g6, provider);
        assertTrue(g6.wroteFloat);

        // Byte
        DummyJsonGenerator g7 = new DummyJsonGenerator();
        serializer.serialize(Byte.valueOf((byte) 5), g7, provider);
        assertTrue(g7.wroteInt);

        // Short
        DummyJsonGenerator g8 = new DummyJsonGenerator();
        serializer.serialize(Short.valueOf((short) 6), g8, provider);
        assertTrue(g8.wroteInt);

        // Custom Fallback Number
        DummyJsonGenerator g9 = new DummyJsonGenerator();
        serializer.serialize(new CustomNumber(99), g9, provider);
        assertTrue(g9.wroteString);
    }

    private static class CustomNumber extends Number {
        private final int val;

        public CustomNumber(int val) {
            this.val = val;
        }

        @Override
        public int intValue() {
            return val;
        }

        @Override
        public long longValue() {
            return val;
        }

        @Override
        public float floatValue() {
            return val;
        }

        @Override
        public double doubleValue() {
            return val;
        }

        @Override
        public String toString() {
            return String.valueOf(val);
        }
    }

    private static class DummyJsonFormatVisitorWrapper implements JsonFormatVisitorWrapper {
        private SerializerProvider provider;

        @Override
        public SerializerProvider getProvider() {
            return provider;
        }

        @Override
        public void setProvider(SerializerProvider provider) {
            this.provider = provider;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(JavaType type) throws JsonMappingException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(JavaType type) throws JsonMappingException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor expectStringFormat(JavaType type) throws JsonMappingException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor expectNumberFormat(JavaType type) throws JsonMappingException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) throws JsonMappingException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) throws JsonMappingException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(JavaType type) throws JsonMappingException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonAnyFormatVisitor expectAnyFormat(JavaType type) throws JsonMappingException {
            return null;
        }
    }

    private static class DummyJsonGenerator extends com.fasterxml.jackson.core.util.JsonGeneratorDelegate {
        public boolean wroteBigDecimal = false;
        public boolean wroteBigInteger = false;
        public boolean wroteInt = false;
        public boolean wroteLong = false;
        public boolean wroteDouble = false;
        public boolean wroteFloat = false;
        public boolean wroteString = false;

        public DummyJsonGenerator() {
            super(null);
        }

        @Override
        public void writeNumber(BigDecimal v) throws IOException {
            wroteBigDecimal = true;
        }

        @Override
        public void writeNumber(BigInteger v) throws IOException {
            wroteBigInteger = true;
        }

        @Override
        public void writeNumber(int v) throws IOException {
            wroteInt = true;
        }

        @Override
        public void writeNumber(long v) throws IOException {
            wroteLong = true;
        }

        @Override
        public void writeNumber(double v) throws IOException {
            wroteDouble = true;
        }

        @Override
        public void writeNumber(float v) throws IOException {
            wroteFloat = true;
        }

        @Override
        public void writeNumber(String v) throws IOException {
            wroteString = true;
        }
    }
}