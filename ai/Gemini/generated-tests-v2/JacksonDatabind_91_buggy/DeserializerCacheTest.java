package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class DeserializerCacheTest {

    @Test
    public void testLifeCycleAndCacheCount() throws Throwable {
        DeserializerCache cache = new DeserializerCache();
        assertEquals(0, cache.cachedDeserializersCount());

        cache.flushCachedDeserializers();
        assertEquals(0, cache.cachedDeserializersCount());

        Object replaced = cache.writeReplace();
        assertNotNull(replaced);
    }

    @Test
    public void testFindCachedDeserializerNullType() throws Throwable {
        DeserializerCache cache = new DeserializerCache();
        try {
            cache._findCachedDeserializer(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null JavaType passed"));
        }
    }

    @Test
    public void testHasValueDeserializerForNullType() throws Throwable {
        DeserializerCache cache = new DeserializerCache();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        DeserializerFactory factory = new BasicDeserializerFactory(null) {
            private static final long serialVersionUID = 1L;
            @Override
            public DeserializerFactory withConfig(DeserializerFactoryConfig config) {
                return this;
            }
        };

        try {
            cache.hasValueDeserializerFor(ctxt, factory, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null JavaType passed"));
        }
    }

    @Test
    public void testFindKeyDeserializerNullHandling() throws Throwable {
        DeserializerCache cache = new DeserializerCache();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        DeserializerFactory factory = new BasicDeserializerFactory(null) {
            private static final long serialVersionUID = 1L;
            @Override
            public DeserializerFactory withConfig(DeserializerFactoryConfig config) {
                return this;
            }
            @Override
            public KeyDeserializer createKeyDeserializer(DeserializationContext ctxt, JavaType type)
                throws com.fasterxml.jackson.databind.JsonMappingException {
                return null;
            }
        };
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);

        try {
            cache.findKeyDeserializer(ctxt, factory, type);
            fail("Should have thrown exception for unknown key deserializer");
        } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
            assertTrue(e.getMessage().contains("Can not find a (Map) Key deserializer"));
        }
    }

}