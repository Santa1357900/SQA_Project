package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.SortedMap;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.deser.std.ObjectArrayDeserializer;

public class BasicDeserializerFactoryTest {

    private static class ConcreteDeserializerFactory extends BasicDeserializerFactory {
        public ConcreteDeserializerFactory() {
            super(new DeserializerFactoryConfig());
        }

        @Override
        public DeserializerFactory withConfig(DeserializerFactoryConfig config) {
            return new ConcreteDeserializerFactory();
        }
    }

    @Test
    public void testFactoryConfigAndCopy() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        assertNotNull(factory.getFactoryConfig());
        
        DeserializerFactory newFactory = factory.withAdditionalDeserializers(new Deserializers.Base());
        assertNotNull(newFactory);
    }

    @Test
    public void testMapAbstractTypeInvalidResolution() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        
        JavaType type = TypeFactory.defaultInstance().constructType(Map.class);
        
        try {
            factory.mapAbstractType(config, type);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid abstract type resolution"));
        }
    }

    @Test
    public void testValueInstantiatorInstanceNull() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        
        ValueInstantiator inst = factory._valueInstantiatorInstance(config, null, null);
        assertNull(inst);
    }

    @Test
    public void testValueInstantiatorInstanceDirect() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        
        ValueInstantiator dummyInst = new ValueInstantiator.Base(Object.class);
        ValueInstantiator inst = factory._valueInstantiatorInstance(config, null, dummyInst);
        assertEquals(dummyInst, inst);
    }

    @Test
    public void testValueInstantiatorInstanceInvalidType() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        
        try {
            factory._valueInstantiatorInstance(config, null, "NotAClassOrInstantiator");
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected type KeyDeserializer or Class"));
        }
    }

    @Test
    public void testCreateArrayDeserializerPrimitive() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        ArrayType arrayType = TypeFactory.defaultInstance().constructArrayType(int.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(arrayType);
        
        JsonDeserializer<?> deser = factory.createArrayDeserializer(ctxt, arrayType, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateArrayDeserializerString() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        ArrayType arrayType = TypeFactory.defaultInstance().constructArrayType(String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(arrayType);
        
        JsonDeserializer<?> deser = factory.createArrayDeserializer(ctxt, arrayType, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateArrayDeserializerObject() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        ArrayType arrayType = TypeFactory.defaultInstance().constructArrayType(Object.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(arrayType);
        
        JsonDeserializer<?> deser = factory.createArrayDeserializer(ctxt, arrayType, beanDesc);
        assertNotNull(deser);
        assertTrue(deser instanceof ObjectArrayDeserializer);
    }

    @Test
    public void testFindDefaultDeserializerString() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        
        JsonDeserializer<?> deser = factory.findDefaultDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
        assertTrue(deser instanceof StringDeserializer);
    }

    @Test
    public void testFindDefaultDeserializerObject() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JavaType type = TypeFactory.defaultInstance().constructType(Object.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        
        JsonDeserializer<?> deser = factory.findDefaultDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateCollectionDeserializerAbstract() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        CollectionType type = TypeFactory.defaultInstance().constructCollectionType(List.class, String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        
        JsonDeserializer<?> deser = factory.createCollectionDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCreateMapDeserializerAbstract() throws Throwable {
        BasicDeserializerFactory factory = new ConcreteDeserializerFactory();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        MapType type = TypeFactory.defaultInstance().constructMapType(Map.class, String.class, Object.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        
        JsonDeserializer<?> deser = factory.createMapDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }
}