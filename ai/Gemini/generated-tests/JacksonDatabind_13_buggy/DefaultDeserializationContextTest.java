package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import com.fasterxml.jackson.annotation.SimpleObjectIdResolver;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.impl.ReadableObjectId;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class DefaultDeserializationContextTest {

    static class DummyObjectIdGenerator extends ObjectIdGenerator<Object> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean canUseFor(ObjectIdGenerator<?> gen) {
            return gen.getClass() == getClass();
        }

        @Override
        public ObjectIdGenerator<Object> forScope(Class<?> scope) {
            return this;
        }

        @Override
        public ObjectIdGenerator<Object> newForSerialization(Object context) {
            return this;
        }

        @Override
        public IdKey key(Object key) {
            return new IdKey(getClass(), null, key);
        }

        @Override
        public Object generateId(Object forPojo) {
            return "testId";
        }
    }

    @Test
    public void testCopyOnImpl() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        if (ctxt instanceof DefaultDeserializationContext) {
            DefaultDeserializationContext defaultCtxt = (DefaultDeserializationContext) ctxt;
            try {
                DefaultDeserializationContext copied = defaultCtxt.copy();
                assertNotNull(copied);
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("copy()"));
            }
        }
    }

    @Test
    public void testImplConstructorsAndFactoryMethods() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();

        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);
        assertNotNull(impl);

        DefaultDeserializationContext withFactory = impl.with(factory);
        assertNotNull(withFactory);

        JsonParser jp = null;
        InjectableValues values = null;
        DefaultDeserializationContext instance = impl.createInstance(config, jp, values);
        assertNotNull(instance);

        DefaultDeserializationContext copied = impl.copy();
        assertNotNull(copied);
    }

    @Test
    public void testFindObjectIdHandling() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        DummyObjectIdGenerator gen = new DummyObjectIdGenerator();
        ObjectIdResolver resolver = new SimpleObjectIdResolver();

        ReadableObjectId roid1 = impl.findObjectId("my-id", gen, resolver);
        assertNotNull(roid1);

        ReadableObjectId roid2 = impl.findObjectId("my-id", gen, resolver);
        assertNotNull(roid2);
        assertEquals(roid1, roid2);

        ReadableObjectId roidDeprecated = impl.findObjectId("my-id-dep", gen);
        assertNotNull(roidDeprecated);
    }

    @Test
    public void testCheckUnresolvedObjectIdNoExceptions() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        impl.checkUnresolvedObjectId();
    }

    @Test
    public void testDeserializerInstanceNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        JsonDeserializer<Object> deser = impl.deserializerInstance(ann, null);
        assertNull(deser);
    }

    @Test
    public void testDeserializerInstanceDirect() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        final JsonDeserializer<Object> dummyDeser = new JsonDeserializer<Object>() {
            @Override
            public Object deserialize(JsonParser p, DeserializationContext ctxt) {
                return null;
            }
        };

        Annotated ann = null;
        JsonDeserializer<Object> deser = impl.deserializerInstance(ann, dummyDeser);
        assertEquals(dummyDeser, deser);
    }

    @Test
    public void testDeserializerInstanceBogusClass() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        JsonDeserializer<Object> deser = impl.deserializerInstance(ann, JsonDeserializer.None.class);
        assertNull(deser);
    }

    @Test
    public void testKeyDeserializerInstanceNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        KeyDeserializer kdeser = impl.keyDeserializerInstance(ann, null);
        assertNull(kdeser);
    }

    @Test
    public void testKeyDeserializerInstanceDirect() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        final KeyDeserializer dummyKDeser = new KeyDeserializer() {
            @Override
            public Object deserializeKey(String key, DeserializationContext ctxt) {
                return key;
            }
        };

        Annotated ann = null;
        KeyDeserializer kdeser = impl.keyDeserializerInstance(ann, dummyKDeser);
        assertEquals(dummyKDeser, kdeser);
    }

    @Test
    public void testKeyDeserializerInstanceBogusClass() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        KeyDeserializer kdeser = impl.keyDeserializerInstance(ann, KeyDeserializer.None.class);
        assertNull(kdeser);
    }

    @Test
    public void testDeserializerInstanceInvalidType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        try {
            impl.deserializerInstance(ann, Integer.valueOf(123));
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected type JsonDeserializer"));
        }
    }

    @Test
    public void testDeserializerInstanceInvalidClass() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        try {
            impl.deserializerInstance(ann, String.class);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected Class<JsonDeserializer>"));
        }
    }

    @Test
    public void testKeyDeserializerInstanceInvalidType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        try {
            impl.keyDeserializerInstance(ann, Integer.valueOf(456));
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected type KeyDeserializer"));
        }
    }

    @Test
    public void testKeyDeserializerInstanceInvalidClass() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializerFactory factory = mapper.getDeserializationContext().getFactory();
        DefaultDeserializationContext.Impl impl = new DefaultDeserializationContext.Impl(factory);

        Annotated ann = null;
        try {
            impl.keyDeserializerInstance(ann, String.class);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected Class<KeyDeserializer>"));
        }
    }
}