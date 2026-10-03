package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.net.URI;
import java.net.URL;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;

public class StdKeyDeserializerTest {

    @Test
    public void testForTypeStringAndObject() throws Throwable {
        StdKeyDeserializer deserStr = StdKeyDeserializer.forType(String.class);
        assertNotNull(deserStr);
        assertEquals(String.class, deserStr.getKeyClass());

        StdKeyDeserializer deserObj = StdKeyDeserializer.forType(Object.class);
        assertNotNull(deserObj);
        assertEquals(Object.class, deserObj.getKeyClass());
    }

    @Test
    public void testForTypePrimitivesAndCommon() throws Throwable {
        assertNotNull(StdKeyDeserializer.forType(UUID.class));
        assertNotNull(StdKeyDeserializer.forType(Integer.class));
        assertNotNull(StdKeyDeserializer.forType(Long.class));
        assertNotNull(StdKeyDeserializer.forType(Date.class));
        assertNotNull(StdKeyDeserializer.forType(Calendar.class));
        assertNotNull(StdKeyDeserializer.forType(Boolean.class));
        assertNotNull(StdKeyDeserializer.forType(Byte.class));
        assertNotNull(StdKeyDeserializer.forType(Character.class));
        assertNotNull(StdKeyDeserializer.forType(Short.class));
        assertNotNull(StdKeyDeserializer.forType(Float.class));
        assertNotNull(StdKeyDeserializer.forType(Double.class));
        assertNotNull(StdKeyDeserializer.forType(URI.class));
        assertNotNull(StdKeyDeserializer.forType(URL.class));
        assertNotNull(StdKeyDeserializer.forType(Class.class));
        assertNotNull(StdKeyDeserializer.forType(Locale.class));
        assertNotNull(StdKeyDeserializer.forType(Currency.class));
    }

    @Test
    public void testForTypeUnknownReturnsNull() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(StdKeyDeserializerTest.class);
        assertNull(deser);
    }

    @Test
    public void testDeserializeKeyNull() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Integer.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        Object result = deser.deserializeKey(null, ctxt);
        assertNull(result);
    }

    @Test
    public void testDeserializeKeyBoolean() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Boolean.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals(Boolean.TRUE, deser.deserializeKey("true", ctxt));
        assertEquals(Boolean.FALSE, deser.deserializeKey("false", ctxt));
    }

    @Test
    public void testDeserializeKeyInt() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Integer.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals(Integer.valueOf(123), deser.deserializeKey("123", ctxt));
    }

    @Test
    public void testDeserializeKeyLong() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Long.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals(Long.valueOf(123456789L), deser.deserializeKey("123456789", ctxt));
    }

    @Test
    public void testDeserializeKeyDoubleFloat() throws Throwable {
        StdKeyDeserializer deserD = StdKeyDeserializer.forType(Double.class);
        StdKeyDeserializer deserF = StdKeyDeserializer.forType(Float.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals(Double.valueOf(12.34), deserD.deserializeKey("12.34", ctxt));
        assertEquals(Float.valueOf(12.34f), deserF.deserializeKey("12.34", ctxt));
    }

    @Test
    public void testDeserializeKeyChar() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Character.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals(Character.valueOf('A'), deser.deserializeKey("A", ctxt));
    }

    @Test
    public void testDeserializeKeyUUID() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(UUID.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        String uuidStr = "d3b07384-d113-4ec6-a579-2699f997645f";
        UUID uuid = (UUID) deser.deserializeKey(uuidStr, ctxt);
        assertEquals(uuidStr, uuid.toString());
    }

    @Test
    public void testDeserializeKeyURI() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(URI.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        URI uri = (URI) deser.deserializeKey("http://example.com", ctxt);
        assertEquals("http://example.com", uri.toString());
    }

    @Test
    public void testDeserializeKeyURL() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(URL.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        URL url = (URL) deser.deserializeKey("http://example.com", ctxt);
        assertEquals("http://example.com", url.toString());
    }

    @Test
    public void testDeserializeKeyClass() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Class.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        Class<?> clazz = (Class<?>) deser.deserializeKey("java.lang.String", ctxt);
        assertEquals(String.class, clazz);
    }

    @Test
    public void testDeserializeKeyByte() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Byte.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals(Byte.valueOf((byte) 120), deser.deserializeKey("120", ctxt));
    }

    @Test
    public void testDeserializeKeyShort() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(Short.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals(Short.valueOf((short) 1200), deser.deserializeKey("1200", ctxt));
    }

    @Test
    public void testStringKDDeserialize() throws Throwable {
        StdKeyDeserializer deser = StdKeyDeserializer.forType(String.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        assertEquals("test-key", deser.deserializeKey("test-key", ctxt));
    }
}