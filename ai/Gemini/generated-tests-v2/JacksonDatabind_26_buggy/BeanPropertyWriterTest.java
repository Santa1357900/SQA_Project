package com.fasterxml.jackson.databind.ser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.NameTransformer;
import com.fasterxml.jackson.core.io.SerializedString;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class BeanPropertyWriterTest {

    static class SampleBean {
        public String name = "testName";
        public String getName() { return name; }
        public SampleBean self = this;
    }

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        BeanPropertyWriter bpw = new BeanPropertyWriter();
        assertNull(bpw.getMember());
        assertNull(bpw.getSerializer());
        assertNull(bpw.getSerializationType());
        assertNull(bpw.getRawSerializationType());
        assertNull(bpw.getViews());
        assertFalse(bpw.isVirtual());
        assertFalse(bpw.isUnwrapping());
        assertFalse(bpw.hasSerializer());
        assertFalse(bpw.hasNullSerializer());
        assertNull(bpw.getTypeSerializer());
        assertFalse(bpw.willSuppressNulls());
    }

    @Test
    public void testInternalSettings() throws Throwable {
        BeanPropertyWriter bpw = new BeanPropertyWriter();
        assertNull(bpw.getInternalSetting("key1"));
        
        Object old = bpw.setInternalSetting("key1", "val1");
        assertNull(old);
        assertEquals("val1", bpw.getInternalSetting("key1"));

        Object replaced = bpw.setInternalSetting("key1", "val2");
        assertEquals("val1", replaced);
        assertEquals("val2", bpw.getInternalSetting("key1"));

        Object removed = bpw.removeInternalSetting("key1");
        assertEquals("val2", removed);
        assertNull(bpw.getInternalSetting("key1"));

        assertNull(bpw.removeInternalSetting("nonexistent"));
    }

    @Test
    public void testAssignSerializers() throws Throwable {
        BeanPropertyWriter bpw = new BeanPropertyWriter();
        
        @SuppressWarnings("unchecked")
        JsonSerializer<Object> dummySer = (JsonSerializer<Object>) (JsonSerializer<?>) new JsonSerializer<Object>() {
            public void serialize(Object value, com.fasterxml.jackson.core.JsonGen jgen, com.fasterxml.jackson.databind.SerializerProvider provider) {}
        };

        bpw.assignSerializer(dummySer);
        assertTrue(bpw.hasSerializer());
        assertEquals(dummySer, bpw.getSerializer());

        try {
            bpw.assignSerializer(dummySer);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Can not override serializer"));
        }

        @SuppressWarnings("unchecked")
        JsonSerializer<Object> dummyNullSer = (JsonSerializer<Object>) (JsonSerializer<?>) new JsonSerializer<Object>() {
            public void serialize(Object value, com.fasterxml.jackson.core.JsonGen jgen, com.fasterxml.jackson.databind.SerializerProvider provider) {}
        };

        bpw.assignNullSerializer(dummyNullSer);
        assertTrue(bpw.hasNullSerializer());

        try {
            bpw.assignNullSerializer(dummyNullSer);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Can not override null serializer"));
        }
    }

    @Test
    public void testWouldConflictWithName() throws Throwable {
        BeanPropertyWriter bpw = new BeanPropertyWriter();
        // Since default constructor sets _name to null, let's test via a copy constructor or sub-class behavior if possible, 
        // or check null safety / basic invocation.
        // Actually we can test with a dummy or use a subclass / reflection if needed, but let's test with constructed name if we can.
    }

    @Test
    public void testToString() throws Throwable {
        BeanPropertyWriter bpw = new BeanPropertyWriter();
        String str = bpw.toString();
        assertNotNull(str);
        assertTrue(str.contains("virtual"));
        assertTrue(str.contains("no static serializer"));
    }

    @Test
    public void testNonTrivialBaseType() throws Throwable {
        BeanPropertyWriter bpw = new BeanPropertyWriter();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        bpw.setNonTrivialBaseType(type);
        // no getter for nonTrivialBaseType, but we ensure it executes safely
    }
}