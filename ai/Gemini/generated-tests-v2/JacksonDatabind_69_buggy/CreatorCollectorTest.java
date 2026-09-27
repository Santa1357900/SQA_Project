package com.fasterxml.jackson.databind.deser.impl;

import junit.framework.TestCase;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams;

public class CreatorCollectorTest extends TestCase {

    private static class DummyMapperConfig extends MapperConfig<DummyMapperConfig> {
        private static final long serialVersionUID = 1L;

        protected DummyMapperConfig() {
            super(null, null);
        }

        @Override
        public boolean canOverrideAccessModifiers() {
            return false;
        }

        @Override
        public boolean shouldSortPropertiesAlphabetically() {
            return false;
        }

        @Override
        public VisibilityChecker<?> getDefaultVisibilityChecker() {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsontype.SubtypeResolver getSubtypeResolver() {
            return null;
        }

        @Override
        public final com.fasterxml.jackson.databind.type.TypeFactory getTypeFactory() {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder<?> getDefaultTyper(JavaType baseType) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.PropertyNamingStrategy getPropertyNamingStrategy() {
            return null;
        }
    }

    private static class DummyAnnotatedWithParams extends AnnotatedWithParams {
        private static final long serialVersionUID = 1L;
        private final Class<?> _paramType;

        public DummyAnnotatedWithParams(Class<?> paramType) {
            super(null, null, null);
            _paramType = paramType;
        }

        @Override
        public Member getAnnotated() {
            return null;
        }

        @Override
        public int getParameterCount() {
            return 1;
        }

        @Override
        public Class<?> getRawParameterType(int index) {
            return _paramType;
        }

        @Override
        public JavaType getParameterType(int index) {
            return null;
        }

        @Override
        public Object call() throws Exception {
            return null;
        }

        @Override
        public Object call(Object[] args) throws Exception {
            return null;
        }

        @Override
        public Object call1(Object arg) throws Exception {
            return null;
        }
    }

    private static class DummySettableBeanProperty extends SettableBeanProperty {
        private static final long serialVersionUID = 1L;
        private final String _name;
        private final Object _injectableId;

        public DummySettableBeanProperty(String name, Object injectableId) {
            super(name, null, null, null);
            _name = name;
            _injectableId = injectableId;
        }

        @Override
        public String getName() {
            return _name;
        }

        @Override
        public Object getInjectableValueId() {
            return _injectableId;
        }

        @Override
        public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object instance) throws java.io.IOException {
        }

        @Override
        public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws java.io.IOException {
            return null;
        }

        @Override
        public void set(Object instance, Object value) throws java.io.IOException {
        }

        @Override
        public Object setAndReturn(Object instance, Object value) throws java.io.IOException {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.introspect.AnnotatedMember getMember() {
            return null;
        }

        @Override
        public SettableBeanProperty withName(PropertyName newName) {
            return this;
        }

        @Override
        public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> des) {
            return this;
        }
    }

    public void testConstructValueInstantiatorVanilla() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        
        BeanDescription collectionDesc = mapper.getSerializationConfig().introspectClassAnnotations(ArrayList.class);
        CreatorCollector collectorCollection = new CreatorCollector(collectionDesc, mapper.getSerializationConfig());
        ValueInstantiator instCol = collectorCollection.constructValueInstantiator(config);
        assertNotNull(instCol);
        assertTrue(instCol.canInstantiate());

        BeanDescription mapDesc = mapper.getSerializationConfig().introspectClassAnnotations(LinkedHashMap.class);
        CreatorCollector collectorMap = new CreatorCollector(mapDesc, mapper.getSerializationConfig());
        ValueInstantiator instMap = collectorMap.constructValueInstantiator(config);
        assertNotNull(instMap);

        BeanDescription hashMapDesc = mapper.getSerializationConfig().introspectClassAnnotations(HashMap.class);
        CreatorCollector collectorHashMap = new CreatorCollector(hashMapDesc, mapper.getSerializationConfig());
        ValueInstantiator instHashMap = collectorHashMap.constructValueInstantiator(config);
        assertNotNull(instHashMap);
    }

    public void testSettersAndAccessors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription desc = mapper.getSerializationConfig().introspectClassAnnotations(Object.class);
        CreatorCollector collector = new CreatorCollector(desc, mapper.getSerializationConfig());

        assertFalse(collector.hasDefaultCreator());
        assertFalse(collector.hasDelegatingCreator());
        assertFalse(collector.hasPropertyBasedCreator());

        DummyAnnotatedWithParams creator = new DummyAnnotatedWithParams(String.class);
        collector.setDefaultCreator(creator);
        assertTrue(collector.hasDefaultCreator());

        collector.addStringCreator(creator, true);
        collector.addIntCreator(creator, true);
        collector.addLongCreator(creator, true);
        collector.addDoubleCreator(creator, true);
        collector.addBooleanCreator(creator, true);

        collector.addStringCreator(creator);
        collector.addIntCreator(creator);
        collector.addLongCreator(creator);
        collector.addDoubleCreator(creator);
        collector.addBooleanCreator(creator);

        AnnotatedParameter param = new AnnotatedParameter(null, null, null, 0);
        collector.addIncompeteParameter(param);
        collector.addIncompeteParameter(null);
    }

    public void testAddPropertyCreatorDuplicates() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription desc = mapper.getSerializationConfig().introspectClassAnnotations(Object.class);
        CreatorCollector collector = new CreatorCollector(desc, mapper.getSerializationConfig());

        DummyAnnotatedWithParams creator = new DummyAnnotatedWithParams(String.class);
        SettableBeanProperty[] props = new SettableBeanProperty[] {
            new DummySettableBeanProperty("prop1", null),
            new DummySettableBeanProperty("prop1", null)
        };

        try {
            collector.addPropertyCreator(creator, true, props);
            fail("Should have thrown IllegalArgumentException due to duplicate property names");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Duplicate creator property"));
        }
    }

    public void testAddPropertyCreatorInjectableSkip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription desc = mapper.getSerializationConfig().introspectClassAnnotations(Object.class);
        CreatorCollector collector = new CreatorCollector(desc, mapper.getSerializationConfig());

        DummyAnnotatedWithParams creator = new DummyAnnotatedWithParams(String.class);
        SettableBeanProperty[] props = new SettableBeanProperty[] {
            new DummySettableBeanProperty("", "injectableId"),
            new DummySettableBeanProperty("", "injectableId2")
        };

        collector.addPropertyCreator(creator, true, props);
        assertTrue(collector.hasPropertyBasedCreator());
    }

    public void testVerifyNonDupConflicting() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription desc = mapper.getSerializationConfig().introspectClassAnnotations(Object.class);
        CreatorCollector collector = new CreatorCollector(desc, mapper.getSerializationConfig());

        DummyAnnotatedWithParams creator1 = new DummyAnnotatedWithParams(String.class);
        DummyAnnotatedWithParams creator2 = new DummyAnnotatedWithParams(String.class);

        collector.addStringCreator(creator1, true);
        try {
            collector.addStringCreator(creator2, true);
            fail("Should throw IllegalArgumentException for conflicting creators");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Conflicting"));
        }
    }

    public void testVanillaInstantiatorValues() throws Throwable {
        CreatorCollector.Vanilla vanillaCol = new CreatorCollector.Vanilla(CreatorCollector.Vanilla.TYPE_COLLECTION);
        assertEquals(ArrayList.class.getName(), vanillaCol.getValueTypeDesc());
        assertTrue(vanillaCol.canInstantiate());
        assertTrue(vanillaCol.canCreateUsingDefault());
        assertNotNull(vanillaCol.createUsingDefault(null));

        CreatorCollector.Vanilla vanillaMap = new CreatorCollector.Vanilla(CreatorCollector.Vanilla.TYPE_MAP);
        assertEquals(LinkedHashMap.class.getName(), vanillaMap.getValueTypeDesc());
        assertNotNull(vanillaMap.createUsingDefault(null));

        CreatorCollector.Vanilla vanillaHashMap = new CreatorCollector.Vanilla(CreatorCollector.Vanilla.TYPE_HASH_MAP);
        assertEquals(HashMap.class.getName(), vanillaHashMap.getValueTypeDesc());
        assertNotNull(vanillaHashMap.createUsingDefault(null));

        CreatorCollector.Vanilla vanillaUnknown = new CreatorCollector.Vanilla(999);
        assertEquals(Object.class.getName(), vanillaUnknown.getValueTypeDesc());
        try {
            vanillaUnknown.createUsingDefault(null);
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Unknown type"));
        }
    }
}