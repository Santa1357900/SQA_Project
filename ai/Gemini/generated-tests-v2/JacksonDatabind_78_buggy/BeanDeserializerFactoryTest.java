package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializerFactory;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class BeanDeserializerFactoryTest {

    @Test
    public void testConstructorAndInstance() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        assertNotNull(factory);
        
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory customFactory = new BeanDeserializerFactory(config);
        assertNotNull(customFactory);
    }

    @Test
    public void testWithConfigSame() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(config);
        DeserializerFactory result = factory.withConfig(config);
        assertSame(factory, result);
    }

    @Test(expected = IllegalStateException.class)
    public void testWithConfigSubclassException() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory subclassFactory = new BeanDeserializerFactory(config) {
            private static final long serialVersionUID = 1L;
        };
        subclassFactory.withConfig(new DeserializerFactoryConfig());
    }

    @Test
    public void testIsPotentialBeanTypeValid() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        // Normal class should be considered a potential bean type
        assertTrue(factory.isPotentialBeanType(String.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsPotentialBeanTypePrimitive() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        factory.isPotentialBeanType(int.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsPotentialBeanTypeArray() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        factory.isPotentialBeanType(String[].class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsPotentialBeanTypeEnum() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        factory.isPotentialBeanType(java.lang.annotation.ElementType.class);
    }
}