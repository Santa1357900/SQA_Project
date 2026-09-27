package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.type.*;

public class BasicDeserializerFactoryTest {

    static class DummyDeserializerFactory extends BasicDeserializerFactory {
        public DummyDeserializerFactory(DeserializerFactoryConfig config) {
            super(config);
        }

        @Override
        protected DeserializerFactory withConfig(DeserializerFactoryConfig config) {
            return new DummyDeserializerFactory(config);
        }
    }

    @Test
    public void testFactoryLifeCycleAndConfig() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);
        
        assertNotNull(factory.getFactoryConfig());
        assertEquals(config, factory.getFactoryConfig());

        DeserializerFactory newFactory = factory.withAdditionalDeserializers(null);
        assertNotNull(newFactory);

        DeserializerFactory newFactoryKey = factory.withAdditionalKeyDeserializers(null);
        assertNotNull(newFactoryKey);

        DeserializerFactory newFactoryMod = factory.withDeserializerModifier(null);
        assertNotNull(newFactoryMod);

        DeserializerFactory newFactoryRes = factory.withAbstractTypeResolver(null);
        assertNotNull(newFactoryRes);

        DeserializerFactory newFactoryInst = factory.withValueInstantiators(null);
        assertNotNull(newFactoryInst);
    }

    @Test
    public void testMapAbstractTypeInvalidResolution() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);
        
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig desConfig = mapper.getDeserializationConfig();
        JavaType type = TypeFactory.defaultInstance().constructType(Map.class);

        try {
            factory.mapAbstractType(desConfig, type);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid abstract type resolution"));
        }
    }

    @Test
    public void testValueInstantiatorInstanceHandling() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig desConfig = mapper.getDeserializationConfig();

        Object res = factory._valueInstantiatorInstance(desConfig, null, null);
        assertNull(res);

        try {
            factory._valueInstantiatorInstance(desConfig, null, "NotAClassOrInstantiator");
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected type"));
        }

        try {
            factory._valueInstantiatorInstance(desConfig, null, String.class);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected Class<ValueInstantiator>"));
        }
    }
}