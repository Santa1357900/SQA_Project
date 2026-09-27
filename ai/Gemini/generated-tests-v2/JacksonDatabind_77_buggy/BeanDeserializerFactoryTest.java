package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;

import java.util.HashMap;
import java.util.ArrayList;

public class BeanDeserializerFactoryTest {

    @Test
    public void testWithConfigSameInstance() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(config);
        
        BeanDeserializerFactory result = (BeanDeserializerFactory) factory.withConfig(config);
        assertSame(factory, result);
    }

    @Test
    public void testWithConfigDifferentInstanceSubtypeError() throws Throwable {
        DeserializerFactoryConfig config1 = new DeserializerFactoryConfig();
        DeserializerFactoryConfig config2 = new DeserializerFactoryConfig();
        
        BeanDeserializerFactory subFactory = new BeanDeserializerFactory(config1) {
            private static final long serialVersionUID = 1L;
        };

        try {
            subFactory.withConfig(config2);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Subtype of BeanDeserializerFactory"));
        }
    }

    @Test
    public void testIsPotentialBeanTypeValid() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        boolean result = factory.isPotentialBeanType(SimpleBean.class);
        assertTrue(result);
    }

    @Test
    public void testIsPotentialBeanTypeInvalid() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        try {
            factory.isPotentialBeanType(int.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not deserialize Class"));
        }
    }

    @Test
    public void testIsIgnorableTypeCaching() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        DeserializationConfig config = null; 
        BeanDescription beanDesc = null;
        Map<Class<?>, Boolean> ignoredTypes = new HashMap<Class<?>, Boolean>();
        ignoredTypes.put(String.class, Boolean.TRUE);

        boolean result = factory.isIgnorableType(config, beanDesc, String.class, ignoredTypes);
        assertTrue(result);
    }

    @Test
    public void testMaterializeAbstractTypeEmptyResolvers() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        DeserializationContext ctxt = null;
        JavaType type = null;
        BeanDescription beanDesc = null;

        JavaType result = factory.materializeAbstractType(ctxt, type, beanDesc);
        assertNull(result);
    }

    static class SimpleBean {
        public String name;
    }
}