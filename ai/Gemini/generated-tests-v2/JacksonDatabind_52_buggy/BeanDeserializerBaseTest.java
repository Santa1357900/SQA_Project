package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.impl.*;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.NameTransformer;

public class BeanDeserializerBaseTest {

    static class DummyBeanDeserializer extends BeanDeserializerBase {
        public DummyBeanDeserializer(BeanDeserializerBuilder builder, BeanDescription beanDesc,
                BeanPropertyMap properties, Map<String, SettableBeanProperty> backRefs,
                Set<String> ignorableProps, boolean ignoreAllUnknown, boolean hasViews) {
            super(builder, beanDesc, properties, backRefs, ignorableProps, ignoreAllUnknown, hasViews);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src) {
            super(src);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, boolean ignoreAllUnknown) {
            super(src, ignoreAllUnknown);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, NameTransformer unwrapper) {
            super(src, unwrapper);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, ObjectIdReader oir) {
            super(src, oir);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, Set<String> ignorableProps) {
            super(src, ignorableProps);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, BeanPropertyMap beanProps) {
            super(src, beanProps);
        }

        @Override
        public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper) {
            return new DummyBeanDeserializer(this, unwrapper);
        }

        @Override
        public BeanDeserializerBase withObjectIdReader(ObjectIdReader oir) {
            return new DummyBeanDeserializer(this, oir);
        }

        @Override
        public BeanDeserializerBase withIgnorableProperties(Set<String> ignorableProps) {
            return new DummyBeanDeserializer(this, ignorableProps);
        }

        @Override
        protected BeanDeserializerBase asArrayDeserializer() {
            return this;
        }

        @Override
        public Object deserializeFromObject(JsonParser p, DeserializationContext ctxt) throws IOException {
            return null;
        }

        @Override
        protected Object _deserializeUsingPropertyBased(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            return null;
        }
    }

    @Test
    public void testBasicAccessors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, mapper.getDeserializationConfig());
        BeanPropertyMap properties = BeanPropertyMap.construct(new ArrayList<SettableBeanProperty>(), false);
        Map<String, SettableBeanProperty> backRefs = new HashMap<String, SettableBeanProperty>();
        Set<String> ignorableProps = new HashSet<String>();
        ignorableProps.add("ignoredField");

        DummyBeanDeserializer deser = new DummyBeanDeserializer(builder, beanDesc, properties,
                backRefs, ignorableProps, true, true);

        assertTrue(deser.isCachable());
        assertEquals(String.class, deser.handledType());
        assertTrue(deser.hasViews());
        assertEquals(0, deser.getPropertyCount());
        assertNotNull(deser.getKnownPropertyNames());
        assertEquals(type, deser.getValueType());
        assertNotNull(deser.getValueInstantiator());
        assertNull(deser.getObjectIdReader());
    }

    @Test
    public void testFindPropertyMethods() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, mapper.getDeserializationConfig());
        BeanPropertyMap properties = BeanPropertyMap.construct(new ArrayList<SettableBeanProperty>(), false);

        DummyBeanDeserializer deser = new DummyBeanDeserializer(builder, beanDesc, properties,
                null, null, false, false);

        assertFalse(deser.hasProperty("nonExistent"));
        assertNull(deser.findProperty("nonExistent"));
        assertNull(deser.findProperty(PropertyName.construct("nonExistent")));
        assertNull(deser.findProperty(0));
        assertNull(deser.findBackReference("nonExistent"));
    }

    @Test
    public void testWrapAndThrowWithErrors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, mapper.getDeserializationConfig());
        BeanPropertyMap properties = BeanPropertyMap.construct(new ArrayList<SettableBeanProperty>(), false);

        DummyBeanDeserializer deser = new DummyBeanDeserializer(builder, beanDesc, properties,
                null, null, false, false);

        Error testError = new OutOfMemoryError("OOM Test");
        boolean caught = false;
        try {
            deser.wrapAndThrow(testError, new Object(), "field", ctxt);
        } catch (Error e) {
            caught = true;
            assertEquals("OOM Test", e.getMessage());
        }
        assertTrue(caught);
    }

    @Test
    public void testWrapAndThrowWithIOException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspect(type);
        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, mapper.getDeserializationConfig());
        BeanPropertyMap properties = BeanPropertyMap.construct(new ArrayList<SettableBeanProperty>(), false);

        DummyBeanDeserializer deser = new DummyBeanDeserializer(builder, beanDesc, properties,
                null, null, false, false);

        IOException testIoEx = new IOException("IO Test");
        boolean caught = false;
        try {
            deser.wrapAndThrow(testIoEx, new Object(), "field", ctxt);
        } catch (IOException e) {
            caught = true;
            assertEquals("IO Test", e.getMessage());
        }
        assertTrue(caught);
    }
}