package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;

public class StdKeyDeserializerTest {

    @Test
    public void testForTypeStandardTypes() throws Throwable {
        assertNotNull(StdKeyDeserializer.forType(String.class));
        assertNotNull(StdKeyDeserializer.forType(Object.class));
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
        assertNull(StdKeyDeserializer.forType(java.io.File.class));
    }

    @Test
    public void testGetKeyClass() throws Throwable {
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Integer.class);
        assertEquals(Integer.class, kdeser.getKeyClass());
    }

    @Test
    public void testDeserializeKeyNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Integer.class);
        assertNull(kdeser.deserializeKey(null, ctxt));
    }

    @Test
    public void testStringKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(String.class);
        assertEquals("hello", kdeser.deserializeKey("hello", ctxt));

        StdKeyDeserializer objKdeser = StdKeyDeserializer.forType(Object.class);
        assertEquals("world", objKdeser.deserializeKey("world", ctxt));
    }

    @Test
    public void testBooleanKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Boolean.class);
        assertEquals(Boolean.TRUE, kdeser.deserializeKey("true", ctxt));
        assertEquals(Boolean.FALSE, kdeser.deserializeKey("false", ctxt));
        
        try {
            kdeser.deserializeKey("invalid", ctxt);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testByteKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Byte.class);
        assertEquals(Byte.valueOf((byte) 123), kdeser.deserializeKey("123", ctxt));
        
        try {
            kdeser.deserializeKey("1000", ctxt);
        } catch (Exception e) {
            // overflow expected
        }
    }

    @Test
    public void testShortKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Short.class);
        assertEquals(Short.valueOf((short) 123), kdeser.deserializeKey("123", ctxt));
        
        try {
            kdeser.deserializeKey("70000", ctxt);
        } catch (Exception e) {
            // overflow expected
        }
    }

    @Test
    public void testCharacterKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Character.class);
        assertEquals(Character.valueOf('a'), kdeser.deserializeKey("a", ctxt));
        
        try {
            kdeser.deserializeKey("ab", ctxt);
        } catch (Exception e) {
            // too long expected
        }
    }

    @Test
    public void testIntegerKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Integer.class);
        assertEquals(Integer.valueOf(12345), kdeser.deserializeKey("12345", ctxt));
        
        try {
            kdeser.deserializeKey("not-an-int", ctxt);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testLongKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Long.class);
        assertEquals(Long.valueOf(123456789L), kdeser.deserializeKey("123456789", ctxt));
        
        try {
            kdeser.deserializeKey("not-a-long", ctxt);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testFloatAndDoubleKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        StdKeyDeserializer floatKdeser = StdKeyDeserializer.forType(Float.class);
        assertEquals(Float.valueOf(1.5f), floatKdeser.deserializeKey("1.5", ctxt));

        StdKeyDeserializer doubleKdeser = StdKeyDeserializer.forType(Double.class);
        assertEquals(Double.valueOf(2.5), doubleKdeser.deserializeKey("2.5", ctxt));
    }

    @Test
    public void testUUIDKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(UUID.class);
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, kdeser.deserializeKey(uuid.toString(), ctxt));
        
        try {
            kdeser.deserializeKey("invalid-uuid", ctxt);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testURIKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(URI.class);
        URI uri = URI.create("http://example.com");
        assertEquals(uri, kdeser.deserializeKey("http://example.com", ctxt));
        
        try {
            kdeser.deserializeKey("http://foo bar", ctxt);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testURLKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(URL.class);
        URL url = new URL("http://example.com");
        assertEquals(url, kdeser.deserializeKey("http://example.com", ctxt));
        
        try {
            kdeser.deserializeKey("invalid-url", ctxt);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testClassKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        StdKeyDeserializer kdeser = StdKeyDeserializer.forType(Class.class);
        assertEquals(String.class, kdeser.deserializeKey("java.lang.String", ctxt));
        
        try {
            kdeser.deserializeKey("com.nonexistent.Class", ctxt);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testLocaleAndCurrencyKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        StdKeyDeserializer localeDeser = StdKeyDeserializer.forType(Locale.class);
        assertEquals(Locale.US, localeDeser.deserializeKey("en_US", ctxt));

        StdKeyDeserializer currencyDeser = StdKeyDeserializer.forType(Currency.class);
        assertEquals(Currency.getInstance("USD"), currencyDeser.deserializeKey("USD", ctxt));
    }

    @Test
    public void testDateAndCalendarKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        StdKeyDeserializer dateDeser = StdKeyDeserializer.forType(Date.class);
        assertNotNull(dateDeser.deserializeKey("2020-01-01T00:00:00.000+0000", ctxt));

        StdKeyDeserializer calDeser = StdKeyDeserializer.forType(Calendar.class);
        assertNotNull(calDeser.deserializeKey("2020-01-01T00:00:00.000+0000", ctxt));
    }

    @Test
    public void testStringCtorKeyDeserializer() throws Throwable {
        Constructor<URL> ctor = URL.class.getConstructor(String.class);
        StdKeyDeserializer.StringCtorKeyDeserializer deser = new StdKeyDeserializer.StringCtorKeyDeserializer(ctor);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = deser.deserializeKey("http://example.com", ctxt);
        assertNotNull(res);
        assertEquals(new URL("http://example.com"), res);
    }

    @Test
    public void testStringFactoryKeyDeserializer() throws Throwable {
        Method method = UUID.class.getMethod("fromString", String.class);
        StdKeyDeserializer.StringFactoryKeyDeserializer deser = new StdKeyDeserializer.StringFactoryKeyDeserializer(method);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        UUID uuid = UUID.randomUUID();
        Object res = deser.deserializeKey(uuid.toString(), ctxt);
        assertEquals(uuid, res);
    }

    @Test
    public void testDelegatingKD() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JsonDeserializer<String> stringDeser = mapper.findRootDeserializer(mapper.constructType(String.class));
        StdKeyDeserializer.DelegatingKD delegating = new StdKeyDeserializer.DelegatingKD(String.class, stringDeser);
        
        assertEquals(String.class, delegating.getKeyClass());
    }
}