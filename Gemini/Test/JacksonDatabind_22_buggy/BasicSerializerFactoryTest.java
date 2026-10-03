package com.fasterxml.jackson.databind.ser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.RandomAccess;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.cfg.SerializerFactoryConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializers;

public class BasicSerializerFactoryTest {

    private static class ConcreteSerializerFactory extends BasicSerializerFactory {
        public ConcreteSerializerFactory(SerializerFactoryConfig config) {
            super(config);
        }

        @Override
        public SerializerFactory withConfig(SerializerFactoryConfig config) {
            return new ConcreteSerializerFactory(config);
        }

        @Override
        public JsonSerializer<Object> createSerializer(com.fasterxml.jackson.databind.SerializerProvider prov, JavaType type) {
            return null;
        }

        @Override
        protected Iterable<Serializers> customSerializers() {
            List<Serializers> list = new ArrayList<Serializers>();
            return list;
        }
    }

    private static class DummyRandomAccessList extends ArrayList<Object> implements RandomAccess {
        private static final long serialVersionUID = 1L;
    }

    @Test
    public void testConstructorAndConfig() throws Throwable {
        SerializerFactoryConfig config = new SerializerFactoryConfig();
        BasicSerializerFactory factory = new ConcreteSerializerFactory(config);
        assertNotNull(factory.getFactoryConfig());
        
        BasicSerializerFactory factoryNullConfig = new ConcreteSerializerFactory(null);
        assertNotNull(factoryNullConfig.getFactoryConfig());
    }

    @Test
    public void testWithMethods() throws Throwable {
        BasicSerializerFactory factory = new ConcreteSerializerFactory(null);
        SerializerFactory f1 = factory.withAdditionalSerializers(null);
        assertNotNull(f1);

        SerializerFactory f2 = factory.withAdditionalKeySerializers(null);
        assertNotNull(f2);

        SerializerFactory f3 = factory.withSerializerModifier(null);
        assertNotNull(f3);
    }

    @Test
    public void testIsIndexedList() throws Throwable {
        BasicSerializerFactory factory = new ConcreteSerializerFactory(null);
        assertTrue(factory.isIndexedList(ArrayList.class));
        assertTrue(factory.isIndexedList(DummyRandomAccessList.class));
        assertFalse(factory.isIndexedList(java.util.LinkedList.class));
    }

    @Test
    public void testVerifyAsClass() throws Throwable {
        BasicSerializerFactory factory = new ConcreteSerializerFactory(null);
        
        Class<?> resNull = factory._verifyAsClass(null, "method", Void.class);
        assertNull(resNull);

        Class<?> resValid = factory._verifyAsClass(String.class, "method", Void.class);
        assertEquals(String.class, resValid);

        Class<?> resNone = factory._verifyAsClass(Void.class, "method", Void.class);
        assertNull(resNone);
    }

    @Test(expected = IllegalStateException.class)
    public void testVerifyAsClassInvalidType() throws Throwable {
        BasicSerializerFactory factory = new ConcreteSerializerFactory(null);
        factory._verifyAsClass(new Object(), "method", Void.class);
    }
}