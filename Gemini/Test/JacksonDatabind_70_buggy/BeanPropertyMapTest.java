package com.fasterxml.jackson.databind.deser.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.util.NameTransformer;

public class BeanPropertyMapTest {

    private static class DummySettableBeanProperty extends SettableBeanProperty {
        private final String _name;
        private int _index;

        public DummySettableBeanProperty(String name) {
            super(name, null, null, null);
            _name = name;
            _index = -1;
        }

        @Override
        public String getName() {
            return _name;
        }

        @Override
        public SettableBeanProperty withName(com.fasterxml.jackson.databind.PropertyName newName) {
            return new DummySettableBeanProperty(newName.getSimpleName());
        }

        @Override
        public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> des) {
            return this;
        }

        @Override
        public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
            // no-op
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return null;
        }

        @Override
        public void set(Object instance, Object value) throws IOException {
            // no-op
        }

        @Override
        public Object setAndReturn(Object instance, Object value) throws IOException {
            return null;
        }

        @Override
        public void assignIndex(int index) {
            _index = index;
        }

        @Override
        public int getPropertyIndex() {
            return _index;
        }
    }

    @Test
    public void testBeanPropertyMapConstructionAndBasicAccess() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        props.add(new DummySettableBeanProperty("prop1"));
        props.add(new DummySettableBeanProperty("prop2"));
        props.add(new DummySettableBeanProperty("prop3"));
        props.add(new DummySettableBeanProperty("prop4"));
        props.add(new DummySettableBeanProperty("prop5"));
        props.add(new DummySettableBeanProperty("prop6")); // triggers larger size

        BeanPropertyMap map = BeanPropertyMap.construct(props, false);
        assertEquals(6, map.size());
        assertNotNull(map.find("prop1"));
        assertNotNull(map.find("prop6"));
        assertNull(map.find("nonExistent"));

        // Test finding by index
        SettableBeanProperty assigned = map.assignIndexes().find(0);
        assertNotNull(assigned);

        // Test iterator and insertion order
        Iterator<SettableBeanProperty> it = map.iterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());

        SettableBeanProperty[] inOrder = map.getPropertiesInInsertionOrder();
        assertEquals(6, inOrder.length);

        // Test toString
        String str = map.toString();
        assertTrue(str.contains("prop1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindNullKey() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        props.add(new DummySettableBeanProperty("prop1"));
        BeanPropertyMap map = BeanPropertyMap.construct(props, false);
        map.find(null);
    }

    @Test
    public void testCaseInsensitiveMap() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        props.add(new DummySettableBeanProperty("PropOne"));
        BeanPropertyMap map = BeanPropertyMap.construct(props, true);

        assertNotNull(map.find("propone"));
        assertNotNull(map.find("PROPONE"));

        BeanPropertyMap insensitiveMap = map.withCaseInsensitivity(true);
        assertSame(map, insensitiveMap);

        BeanPropertyMap sensitiveMap = map.withCaseInsensitivity(false);
        assertNotSame(map, sensitiveMap);
        assertNull(sensitiveMap.find("propone"));
        assertNotNull(sensitiveMap.find("PropOne"));
    }

    @Test
    public void testWithPropertyAndReplacement() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        SettableBeanProperty p1 = new DummySettableBeanProperty("prop1");
        props.add(p1);
        BeanPropertyMap map = BeanPropertyMap.construct(props, false);

        // Add new property causing potential spill or extension
        SettableBeanProperty p2 = new DummySettableBeanProperty("prop2");
        map.withProperty(p2);
        assertEquals(2, map.size());

        // Replace existing property
        SettableBeanProperty p1Replacement = new DummySettableBeanProperty("prop1");
        map.replace(p1Replacement);
        assertSame(p1Replacement, map.find("prop1"));

        // Replace non-existent should throw NoSuchElementException
        try {
            map.replace(new DummySettableBeanProperty("nonExistent"));
            fail("Should have thrown NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("nonExistent"));
        }
    }

    @Test
    public void testRemoveProperty() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        SettableBeanProperty p1 = new DummySettableBeanProperty("prop1");
        SettableBeanProperty p2 = new DummySettableBeanProperty("prop2");
        props.add(p1);
        props.add(p2);
        BeanPropertyMap map = BeanPropertyMap.construct(props, false);

        map.remove(p1);
        assertEquals(1, map.size());
        assertNull(map.find("prop1"));
        assertNotNull(map.find("prop2"));

        // Remove non-existent should throw NoSuchElementException
        try {
            map.remove(new DummySettableBeanProperty("nonExistent"));
            fail("Should have thrown NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("nonExistent"));
        }
    }

    @Test
    public void testWithoutProperties() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        props.add(new DummySettableBeanProperty("prop1"));
        props.add(new DummySettableBeanProperty("prop2"));
        BeanPropertyMap map = BeanPropertyMap.construct(props, false);

        BeanPropertyMap reduced = map.withoutProperties(Arrays.asList("prop1"));
        assertEquals(1, reduced.size());
        assertNull(reduced.find("prop1"));
        assertNotNull(reduced.find("prop2"));

        BeanPropertyMap emptyExclude = map.withoutProperties(Collections.<String>emptyList());
        assertSame(map, emptyExclude);
    }

    @Test
    public void testRenameAll() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        props.add(new DummySettableBeanProperty("prop1"));
        BeanPropertyMap map = BeanPropertyMap.construct(props, false);

        BeanPropertyMap renamed = map.renameAll(NameTransformer.simplePrefix("pre_"));
        assertNotNull(renamed.find("pre_prop1"));

        BeanPropertyMap nopRenamed = map.renameAll(NameTransformer.NOP);
        assertSame(map, nopRenamed);

        BeanPropertyMap nullRenamed = map.renameAll(null);
        assertSame(map, nullRenamed);
    }

    @Test
    public void testFindDeserializeAndSet() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        props.add(new DummySettableBeanProperty("prop1"));
        BeanPropertyMap map = BeanPropertyMap.construct(props, false);

        boolean found = map.findDeserializeAndSet(null, null, new Object(), "prop1");
        assertTrue(found);

        boolean notFound = map.findDeserializeAndSet(null, null, new Object(), "missing");
        assertFalse(notFound);
    }

    @Test
    public void testWrapAndThrowExceptions() throws Throwable {
        ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
        props.add(new DummySettableBeanProperty("prop1"));
        BeanPropertyMap map = BeanPropertyMap.construct(props, false);

        // Test wrapping IOException, Error, RuntimeException via a public method if possible, 
        // or directly triggering via internal wrapAndThrow logic if accessible.
        // Since wrapAndThrow is protected, we can subclass or test via findDeserializeAndSet or directly if package-private/protected.
        BeanPropertyMapTestSubclass testMap = new BeanPropertyMapTestSubclass(false, props);
        
        try {
            testMap.publicWrapAndThrow(new IOException("io error"), new Object(), "prop1", null);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertEquals("io error", e.getMessage());
        }

        try {
            testMap.publicWrapAndThrow(new Error("fatal error"), new Object(), "prop1", null);
            fail("Should have thrown Error");
        } catch (Error e) {
            assertEquals("fatal error", e.getMessage());
        }

        try {
            testMap.publicWrapAndThrow(new RuntimeException("runtime error"), new Object(), "prop1", null);
            fail("Should have thrown JsonMappingException or RuntimeException");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("runtime error"));
        }
    }

    private static class BeanPropertyMapTestSubclass extends BeanPropertyMap {
        public BeanPropertyMapTestSubclass(boolean caseInsensitive, Collection<SettableBeanProperty> props) {
            super(caseInsensitive, props);
        }

        public void publicWrapAndThrow(Throwable t, Object bean, String fieldName, DeserializationContext ctxt) throws IOException {
            wrapAndThrow(t, bean, fieldName, ctxt);
        }
    }
}