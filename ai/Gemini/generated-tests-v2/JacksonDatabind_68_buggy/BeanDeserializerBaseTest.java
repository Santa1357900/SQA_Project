package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.impl.BeanPropertyMap;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.NameTransformer;

public class BeanDeserializerBaseTest {

    private static class DummyBeanDeserializer extends BeanDeserializerBase {
        private static final long serialVersionUID = 1L;

        public DummyBeanDeserializer(BeanDeserializerBase src) {
            super(src);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, boolean ignoreAllUnknown) {
            super(src, ignoreAllUnknown);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, NameTransformer unwrapper) {
            super(src, unwrapper);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, com.fasterxml.jackson.databind.deser.impl.ObjectIdReader oir) {
            super(src, oir);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, Set<String> ignorableProps) {
            super(src, ignorableProps);
        }

        public DummyBeanDeserializer(BeanDeserializerBase src, BeanPropertyMap beanProps) {
            super(src, beanProps);
        }

        public DummyBeanDeserializer(BeanDeserializerBuilder builder, BeanDescription beanDesc,
                BeanPropertyMap properties, Map<String, SettableBeanProperty> backRefs,
                Set<String> ignorableProps, boolean ignoreAllUnknown, boolean hasViews) {
            super(builder, beanDesc, properties, backRefs, ignorableProps, ignoreAllUnknown, hasViews);
        }

        @Override
        public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper) {
            return new DummyBeanDeserializer(this, unwrapper);
        }

        @Override
        public BeanDeserializerBase withObjectIdReader(com.fasterxml.jackson.databind.deser.impl.ObjectIdReader oir) {
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

    private DummyBeanDeserializer createDummyDeserializer() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        BeanDescription beanDesc = mapper.getSerializationConfig().introspect(type);
        
        BeanPropertyMap propertyMap = BeanPropertyMap.construct(new ArrayList<SettableBeanProperty>(), false);
        ValueInstantiator.Base instantiator = new ValueInstantiator.Base(type);

        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, mapper.getDeserializationConfig());
        builder.setValueInstantiator(instantiator);

        return new DummyBeanDeserializer(
            builder, beanDesc, propertyMap, null, null, false, false
        );
    }

    @Test
    public void testBasicAccessors() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        assertTrue(deser.isCachable());
        assertEquals(String.class, deser.handledType());
        assertNull(deser.getObjectIdReader());
        assertFalse(deser.hasViews());
        assertEquals(0, deser.getPropertyCount());
        assertNotNull(deser.getKnownPropertyNames());
        assertEquals(String.class, deser.getBeanClass());
        assertNotNull(deser.getValueType());
        assertNotNull(deser.getValueInstantiator());
    }

    @Test
    public void testHasProperty() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        assertFalse(deser.hasProperty("nonExistent"));
    }

    @Test
    public void testFindPropertyByName() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        assertNull(deser.findProperty("missing"));
        assertNull(deser.findProperty(new PropertyName("missing")));
    }

    @Test
    public void testFindPropertyByIndex() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        assertNull(deser.findProperty(0));
        assertNull(deser.findProperty(-1));
    }

    @Test
    public void testFindBackReference() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        assertNull(deser.findBackReference("anyRef"));
    }

    @Test
    public void testConstructorsAndMutantFactories() throws Throwable {
        DummyBeanDeserializer src = createDummyDeserializer();
        
        DummyBeanDeserializer copy1 = new DummyBeanDeserializer(src);
        assertNotNull(copy1);

        DummyBeanDeserializer copy2 = new DummyBeanDeserializer(src, true);
        assertNotNull(copy2);

        DummyBeanDeserializer copy3 = new DummyBeanDeserializer(src, NameTransformer.NOP);
        assertNotNull(copy3);

        DummyBeanDeserializer copy4 = new DummyBeanDeserializer(src, (com.fasterxml.jackson.databind.deser.impl.ObjectIdReader) null);
        assertNotNull(copy4);

        Set<String> ignorable = new HashSet<String>();
        ignorable.add("prop");
        DummyBeanDeserializer copy5 = new DummyBeanDeserializer(src, ignorable);
        assertNotNull(copy5);

        BeanPropertyMap newProps = BeanPropertyMap.construct(new ArrayList<SettableBeanProperty>(), false);
        DummyBeanDeserializer copy6 = new DummyBeanDeserializer(src, newProps);
        assertNotNull(copy6);

        assertNotNull(src.unwrappingDeserializer(null));
        assertNotNull(src.withObjectIdReader(null));
        assertNotNull(src.withIgnorableProperties(ignorable));
        assertNotNull(src.withBeanProperties(newProps));
    }

    @Test
    public void testWrapAndThrowExceptions() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Throwable cause = new RuntimeException("test error");
        try {
            deser.wrapAndThrow(cause, "beanInstance", "fieldName", ctxt);
            fail("Should have thrown exception");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("test error"));
        }
    }

    @Test
    public void testWrapAndThrowError() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Error err = new OutOfMemoryError("oom");
        try {
            deser.wrapAndThrow(err, "beanInstance", "fieldName", ctxt);
            fail("Should have thrown Error");
        } catch (OutOfMemoryError e) {
            assertEquals("oom", e.getMessage());
        }
    }

    @Test
    public void testWrapInstantiationProblem() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        IOException ioEx = new IOException("io error");
        try {
            deser.wrapInstantiationProblem(ioEx, ctxt);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertEquals("io error", e.getMessage());
        }
    }

    @Test
    public void testCreatorPropertiesIterator() throws Throwable {
        DummyBeanDeserializer deser = createDummyDeserializer();
        assertNotNull(deser.creatorProperties());
        assertFalse(deser.creatorProperties().hasNext());
    }

    @Test
    public void testPropertiesIteratorThrowsWhenNotResolved() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        BeanDescription beanDesc = mapper.getSerializationConfig().introspect(type);
        ValueInstantiator.Base instantiator = new ValueInstantiator.Base(type);
        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, mapper.getSerializationConfig());
        builder.setValueInstantiator(instantiator);

        DummyBeanDeserializer uninitDeser = new DummyBeanDeserializer(
            builder, beanDesc, null, null, null, false, false
        );

        try {
            uninitDeser.properties();
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("BeanDeserializer has been resolved"));
        }
    }
}