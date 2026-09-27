package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.DeserializerFactory;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class BeanDeserializerFactoryTest {

    @Test
    public void testWithConfigSameConfig() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(config);
        DeserializerFactory result = factory.withConfig(config);
        assertSame(factory, result);
    }

    @Test
    public void testWithConfigDifferentConfigSubtype() throws Throwable {
        DeserializerFactoryConfig config1 = new DeserializerFactoryConfig();
        DeserializerFactoryConfig config2 = new DeserializerFactoryConfig();
        
        BeanDeserializerFactory customFactory = new BeanDeserializerFactory(config1) {
            private static final long serialVersionUID = 1L;
        };

        try {
            customFactory.withConfig(config2);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Subtype of BeanDeserializerFactory"));
        }
    }

    @Test
    public void testIsPotentialBeanTypeValid() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        boolean result = factory.isPotentialBeanType(String.class);
        assertTrue(result);
    }

    @Test
    public void testIsPotentialBeanTypePrimitive() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        try {
            factory.isPotentialBeanType(int.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not deserialize Class"));
        }
    }
}