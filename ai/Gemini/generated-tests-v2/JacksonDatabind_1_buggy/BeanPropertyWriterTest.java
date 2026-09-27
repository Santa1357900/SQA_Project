package com.fasterxml.jackson.databind.ser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.util.Annotations;
import com.fasterxml.jackson.databind.util.NameTransformer;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;

public class BeanPropertyWriterTest {

    static class DummyBean {
        public String sampleField = "value";
        private String sampleMethodProp = "methodValue";
        public String getSampleMethod() {
            return sampleMethodProp;
        }
    }

    static class DummyBeanPropertyDefinition extends BeanPropertyDefinition {
        private final String _name;
        private final boolean _isRequired;

        public DummyBeanPropertyDefinition(String name, boolean isRequired) {
            _name = name;
            _isRequired = isRequired;
        }

        @Override public String getName() { return _name; }
        @Override public String getInternalName() { return _name; }
        @Override public PropertyName getWrapperName() { return null; }
        @Override public boolean isRequired() { return _isRequired; }
        @Override public boolean hasGetter() { return false; }
        @Override public boolean hasSetter() { return false; }
        @Override public boolean hasField() { return false; }
        @Override public boolean hasConstructorParameter() { return false; }
        @Override public com.fasterxml.jackson.databind.introspect.AnnotatedMethod getGetter() { return null; }
        @Override public com.fasterxml.jackson.databind.introspect.AnnotatedMethod getSetter() { return null; }
        @Override public com.fasterxml.jackson.databind.introspect.AnnotatedField getField() { return null; }
        @Override public com.fasterxml.jackson.databind.introspect.AnnotatedParameter getConstructorParameter() { return null; }
    }

    @Test
    public void testConstantsAndBasicGetters() throws Throwable {
        assertNotNull(BeanPropertyWriter.MARKER_FOR_EMPTY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidMember() throws Throwable {
        BeanPropertyDefinition propDef = new DummyBeanPropertyDefinition("testProp", false);
        Field field = DummyBean.class.getField("sampleField");
        // Pass a raw Field or something not AnnotatedMember to trigger IllegalArgumentException
        new BeanPropertyWriter(propDef, null, null, null, null, null, null, false, null);
    }

    @Test
    public void testInternalSettings() throws Throwable {
        BeanPropertyDefinition propDef = new DummyBeanPropertyDefinition("testProp", false);
        Field field = DummyBean.class.getField("sampleField");
        AnnotatedField annField = new AnnotatedField(null, field, null);
        
        BeanPropertyWriter bpw = new BeanPropertyWriter(
            propDef, annField, null, null, null, null, null, false, null
        );

        assertNull(bpw.getInternalSetting("key1"));
        assertNull(bpw.setInternalSetting("key1", "val1"));
        assertEquals("val1", bpw.getInternalSetting("key1"));
        assertEquals("val1", bpw.removeInternalSetting("key1"));
        assertNull(bpw.getInternalSetting("key1"));
        assertNull(bpw.removeInternalSetting("nonexistent"));
    }

    @Test
    public void testAssignSerializerAndNullSerializer() throws Throwable {
        BeanPropertyDefinition propDef = new DummyBeanPropertyDefinition("testProp", false);
        Field field = DummyBean.class.getField("sampleField");
        AnnotatedField annField = new AnnotatedField(null, field, null);

        BeanPropertyWriter bpw = new BeanPropertyWriter(
            propDef, annField, null, null, null, null, null, false, null
        );

        assertFalse(bpw.hasSerializer());
        assertFalse(bpw.hasNullSerializer());
        assertNull(bpw.getSerializer());

        @SuppressWarnings("unchecked")
        JsonSerializer<Object> dummySer = (JsonSerializer<Object>) (JsonSerializer<?>) new com.fasterxml.jackson.databind.ser.std.StdSerializer<Object>(Object.class) {
            @Override
            public void serialize(Object value, com.fasterxml.jackson.core.JsonGenerator jgen, SerializerProvider provider) {}
        };

        bpw.assignSerializer(dummySer);
        assertTrue(bpw.hasSerializer());
        assertEquals(dummySer, bpw.getSerializer());

        try {
            bpw.assignSerializer(dummySer); // same should pass or allow
        } catch (IllegalStateException e) {
            // expected if different
        }

        try {
            @SuppressWarnings("unchecked")
            JsonSerializer<Object> dummySer2 = (JsonSerializer<Object>) (JsonSerializer<?>) new com.fasterxml.jackson.databind.ser.std.StdSerializer<Object>(Object.class) {
                @Override
                public void serialize(Object value, com.fasterxml.jackson.core.JsonGenerator jgen, SerializerProvider provider) {}
            };
            bpw.assignSerializer(dummySer2);
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Can not override serializer"));
        }

        bpw.assignNullSerializer(dummySer);
        assertTrue(bpw.hasNullSerializer());

        try {
            @SuppressWarnings("unchecked")
            JsonSerializer<Object> dummySer2 = (JsonSerializer<Object>) (JsonSerializer<?>) new com.fasterxml.jackson.databind.ser.std.StdSerializer<Object>(Object.class) {
                @Override
                public void serialize(Object value, com.fasterxml.jackson.core.JsonGenerator jgen, SerializerProvider provider) {}
            };
            bpw.assignNullSerializer(dummySer2);
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Can not override null serializer"));
        }
    }

    @Test
    public void testRenameAndToStringWithField() throws Throwable {
        BeanPropertyDefinition propDef = new DummyBeanPropertyDefinition("oldName", true);
        Field field = DummyBean.class.getField("sampleField");
        AnnotatedField annField = new AnnotatedField(null, field, null);

        BeanPropertyWriter bpw = new BeanPropertyWriter(
            propDef, annField, null, null, null, null, null, false, null
        );

        assertEquals("oldName", bpw.getName());
        assertTrue(bpw.isRequired());

        NameTransformer transformer = new NameTransformer() {
            @Override
            public String transform(String name) {
                return name + "_transformed";
            }
            @Override
            public String reverse(String transformed) {
                return transformed;
            }
        };

        BeanPropertyWriter renamed = bpw.rename(transformer);
        assertNotNull(renamed);
        assertEquals("oldName_transformed", renamed.getName());

        BeanPropertyWriter same = bpw.rename(NameTransformer.NOP);
        assertEquals(bpw, same);

        String str = bpw.toString();
        assertTrue(str.contains("oldName"));
        assertTrue(str.contains("sampleField"));
    }

    @Test
    public void testToStringWithMethod() throws Throwable {
        BeanPropertyDefinition propDef = new DummyBeanPropertyDefinition("methodProp", false);
        Method method = DummyBean.class.getMethod("sampleMethod");
        AnnotatedMethod annMethod = new AnnotatedMethod(null, method, null);

        BeanPropertyWriter bpw = new BeanPropertyWriter(
            propDef, annMethod, null, null, null, null, null, false, null
        );

        assertEquals(String.class, bpw.getPropertyType());
        assertEquals(String.class, bpw.getGenericPropertyType());

        DummyBean bean = new DummyBean();
        assertEquals("methodValue", bpw.get(bean));

        String str = bpw.toString();
        assertTrue(str.contains("methodProp"));
        assertTrue(str.contains("sampleMethod"));
    }

    @Test
    public void testUnwrappingWriter() throws Throwable {
        BeanPropertyDefinition propDef = new DummyBeanPropertyDefinition("prop", false);
        Field field = DummyBean.class.getField("sampleField");
        AnnotatedField annField = new AnnotatedField(null, field, null);

        BeanPropertyWriter bpw = new BeanPropertyWriter(
            propDef, annField, null, null, null, null, null, false, null
        );

        BeanPropertyWriter unwrapped = bpw.unwrappingWriter(NameTransformer.NOP);
        assertNotNull(unwrapped);
    }
}