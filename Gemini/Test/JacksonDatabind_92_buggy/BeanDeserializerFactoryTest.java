package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.DeserializerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;

public class BeanDeserializerFactoryTest {

    @Test
    public void testInstanceAndConfig() throws Throwable {
        assertNotNull(BeanDeserializerFactory.instance);
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(config);
        
        DeserializerFactory updated = factory.withConfig(config);
        assertSame(factory, updated);
        
        DeserializerFactoryConfig newConfig = new DeserializerFactoryConfig();
        DeserializerFactory updatedNew = factory.withConfig(newConfig);
        assertNotSame(factory, updatedNew);
    }

    @Test
    public void testSubclassWithConfigThrows() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory subFactory = new BeanDeserializerFactory(config) {
            private static final long serialVersionUID = 1L;
        };
        try {
            subFactory.withConfig(config);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Subtype of BeanDeserializerFactory"));
        }
    }

    @Test
    public void testIsPotentialBeanTypeValid() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JavaType type = ctxt.constructType(String.class);
        BeanDescription beanDesc = ctxt.getConfig().introspect(type);
        
        JsonDeserializer<Object> deser = factory.createBeanDeserializer(ctxt, type, beanDesc);
        assertNotNull(deser);
    }

    @Test
    public void testCheckIllegalTypes() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JavaType type = ctxt.constructType(org.apache.commons.collections.functors.InvokerTransformer.class);
        BeanDescription beanDesc = ctxt.getConfig().introspect(type);
        
        try {
            factory.createBeanDeserializer(ctxt, type, beanDesc);
            fail("Expected JsonMappingException for illegal type");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("Illegal type"));
        }
    }

    @Test
    public void testMaterializeAbstractTypeReturnsNullWhenNoResolver() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JavaType type = ctxt.constructType(Runnable.class);
        BeanDescription beanDesc = ctxt.getConfig().introspect(type);
        
        JsonDeserializer<Object> deser = factory.createBeanDeserializer(ctxt, type, beanDesc);
        assertNull(deser);
    }
}