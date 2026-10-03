package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberDeserializersTest {

    @Test
    public void testFindPrimitives() throws Throwable {
        assertNotNull(NumberDeserializers.find(Integer.TYPE, "int"));
        assertNotNull(NumberDeserializers.find(Boolean.TYPE, "boolean"));
        assertNotNull(NumberDeserializers.find(Long.TYPE, "long"));
        assertNotNull(NumberDeserializers.find(Double.TYPE, "double"));
        assertNotNull(NumberDeserializers.find(Character.TYPE, "char"));
        assertNotNull(NumberDeserializers.find(Byte.TYPE, "byte"));
        assertNotNull(NumberDeserializers.find(Short.TYPE, "short"));
        assertNotNull(NumberDeserializers.find(Float.TYPE, "float"));
    }

    @Test
    public void testFindWrappersAndOthers() throws Throwable {
        assertNotNull(NumberDeserializers.find(Integer.class, "java.lang.Integer"));
        assertNotNull(NumberDeserializers.find(Boolean.class, "java.lang.Boolean"));
        assertNotNull(NumberDeserializers.find(Long.class, "java.lang.Long"));
        assertNotNull(NumberDeserializers.find(Double.class, "java.lang.Double"));
        assertNotNull(NumberDeserializers.find(Character.class, "java.lang.Character"));
        assertNotNull(NumberDeserializers.find(Byte.class, "java.lang.Byte"));
        assertNotNull(NumberDeserializers.find(Short.class, "java.lang.Short"));
        assertNotNull(NumberDeserializers.find(Float.class, "java.lang.Float"));
        assertNotNull(NumberDeserializers.find(Number.class, "java.lang.Number"));
        assertNotNull(NumberDeserializers.find(BigDecimal.class, "java.math.BigDecimal"));
        assertNotNull(NumberDeserializers.find(BigInteger.class, "java.math.BigInteger"));
    }

    @Test
    public void testFindUnknownClass() throws Throwable {
        JsonDeserializer<?> deser = NumberDeserializers.find(Object.class, "java.lang.Object");
        assertNull(deser);
    }

    @Test
    public void testFindInternalError() throws Throwable {
        boolean thrown = false;
        try {
            NumberDeserializers.find(Void.TYPE, "void");
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Internal error"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testBooleanDeserializerNullValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        NumberDeserializers.BooleanDeserializer deser = NumberDeserializers.BooleanDeserializer.wrapperInstance;
        assertNull(deser.getNullValue(ctxt));
        assertNull(deser.getNullValue());
    }

    @Test
    public void testIntegerDeserializerCachable() throws Throwable {
        NumberDeserializers.IntegerDeserializer deser = NumberDeserializers.IntegerDeserializer.wrapperInstance;
        assertTrue(deser.isCachable());
    }

    @Test
    public void testLongDeserializerCachable() throws Throwable {
        NumberDeserializers.LongDeserializer deser = NumberDeserializers.LongDeserializer.wrapperInstance;
        assertTrue(deser.isCachable());
    }
}