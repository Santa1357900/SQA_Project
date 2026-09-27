package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.ReferenceType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    public void testFactoryConfigAndFluentMethods() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        assertSame(config, factory.getFactoryConfig());

        assertNotNull(factory.withAdditionalDeserializers(null));
        assertNotNull(factory.withAdditionalKeyDeserializers(null));
        assertNotNull(factory.withDeserializerModifier(null));
        assertNotNull(factory.withAbstractTypeResolver(null));
        assertNotNull(factory.withValueInstantiators(null));
    }

    @Test
    public void testMapAbstractTypeInvalidResolution() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig deserConfig = mapper.getDeserializationConfig();
        JavaType type = TypeFactory.defaultInstance().constructType(Map.class);

        try {
            factory.mapAbstractType(deserConfig, type);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid abstract type resolution"));
        }
    }

    @Test
    public void testCreateTreeDeserializer() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig deserConfig = mapper.getDeserializationConfig();
        JavaType nodeType = TypeFactory.defaultInstance().constructType(JsonNode.class);

        JsonDeserializer<?> deserializer = factory.createTreeDeserializer(deserConfig, nodeType, null);
        assertNotNull(deserializer);
    }

    @Test
    public void testFindValueInstantiatorStdCollections() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JavaType setType = TypeFactory.defaultInstance().constructType(Collections.EMPTY_SET.getClass());
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(setType);

        ValueInstantiator inst = factory.findValueInstantiator(ctxt, beanDesc);
        assertNotNull(inst);
    }

    @Test
    public void testCreateCollectionDeserializerFallback() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        CollectionType type = TypeFactory.defaultInstance().constructCollectionType(List.class, String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);

        JsonDeserializer<?> deser = factory.createCollectionDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateMapDeserializerFallback() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        MapType type = TypeFactory.defaultInstance().constructMapType(Map.class, String.class, String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);

        JsonDeserializer<?> deser = factory.createMapDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateArrayDeserializerPrimitive() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        ArrayType type = TypeFactory.defaultInstance().constructArrayType(int.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);

        JsonDeserializer<?> deser = factory.createArrayDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateEnumDeserializer() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JavaType type = TypeFactory.defaultInstance().constructType(Thread.State.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);

        JsonDeserializer<?> deser = factory.createEnumDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateKeyDeserializerEnum() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JavaType type = TypeFactory.defaultInstance().constructType(Thread.State.class);

        KeyDeserializer keyDeser = factory.createKeyDeserializer(ctxt, type);
        assertNotNull(keyDeser);
    }

    @Test
    public void testFindDefaultDeserializerObject() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new DummyDeserializerFactory(config);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JavaType type = TypeFactory.defaultInstance().constructType(Object.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);

        JsonDeserializer<?> deser = factory.findDefaultDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }
}