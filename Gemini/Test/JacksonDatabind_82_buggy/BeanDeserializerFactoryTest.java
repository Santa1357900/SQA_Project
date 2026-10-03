package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class BeanDeserializerFactoryTest {

    @Test
    public void testInstanceAndConstructor() throws Throwable {
        assertNotNull(BeanDeserializerFactory.instance);
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(config);
        assertNotNull(factory);
    }

    @Test
    public void testWithConfigSame() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(config);
        assertSame(factory, factory.withConfig(config));
    }

    @Test
    public void testWithConfigSubclassException() throws Throwable {
        DeserializerFactoryConfig config = new DeserializerFactoryConfig();
        BeanDeserializerFactory subFactory = new BeanDeserializerFactory(config) {
            private static final long serialVersionUID = 1L;
        };
        try {
            subFactory.withConfig(config);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("has not properly overridden method"));
        }
    }

    @Test
    public void testIsPotentialBeanTypeValid() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        // Testing with a normal class like String or Object should pass (return true)
        assertTrue(factory.isPotentialBeanType(Object.class));
    }

    @Test
    public void testIsPotentialBeanTypePrimitive() throws Throwable {
        BeanDeserializerFactory factory = BeanDeserializerFactory.instance;
        try {
            factory.isPotentialBeanType(int.class);
            fail("Expected IllegalArgumentException for primitive");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not deserialize Class"));
        }
    }

    @Test
    public void testDefaultNoDeserClassNames() throws Throwable {
        assertNotNull(BeanDeserializerFactory.DEFAULT_NO_DESER_CLASS_NAMES);
        assertTrue(BeanDeserializerFactory.DEFAULT_NO_DESER_CLASS_NAMES.contains("org.apache.commons.collections.functors.InvokerTransformer"));
        assertTrue(BeanDeserializerFactory.DEFAULT_NO_DESER_CLASS_NAMES.contains("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl"));
    }
}