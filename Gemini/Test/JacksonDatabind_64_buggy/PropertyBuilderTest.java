package com.fasterxml.jackson.databind.ser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Annotations;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;

public class PropertyBuilderTest {

    static class DummyBean {
        public int intField = 42;
        public String stringField = "test";
        public Object objectField = null;
    }

    @Test
    public void testPropertyBuilderInstantiationAndGetClassAnnotations() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        JavaType type = mapper.constructType(DummyBean.class);
        BeanDescription beanDesc = config.introspect(type);

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);
        Annotations anns = builder.getClassAnnotations();
        assertNotNull(anns);
    }

    @Test
    public void testGetDefaultBean() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        JavaType type = mapper.constructType(DummyBean.class);
        BeanDescription beanDesc = config.introspect(type);

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);
        Object defBean = builder.getDefaultBean();
        assertNotNull(defBean);
        assertTrue(defBean instanceof DummyBean);

        // Call again to hit cached branch (_defaultBean != null)
        Object defBeanCached = builder.getDefaultBean();
        assertNotNull(defBeanCached);
    }

    @Test
    public void testGetDefaultValuePrimitivesAndObjects() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        JavaType type = mapper.constructType(DummyBean.class);
        BeanDescription beanDesc = config.introspect(type);

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);

        JavaType intType = mapper.constructType(int.class);
        Object intDef = builder.getDefaultValue(intType);
        assertEquals(Integer.valueOf(0), intDef);

        JavaType stringType = mapper.constructType(String.class);
        Object stringDef = builder.getDefaultValue(stringType);
        assertEquals("", stringDef);

        JavaType mapType = mapper.constructType(HashMap.class);
        Object mapDef = builder.getDefaultValue(mapType);
        assertEquals(JsonInclude.Include.NON_EMPTY, mapDef);

        JavaType objType = mapper.constructType(Object.class);
        Object objDef = builder.getDefaultValue(objType);
        assertNull(objDef);
    }

    @Test
    public void testThrowWrappedWithRuntimeException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        JavaType type = mapper.constructType(DummyBean.class);
        BeanDescription beanDesc = config.introspect(type);

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);
        RuntimeException runtimeEx = new RuntimeException("Runtime failure");
        
        try {
            builder._throwWrapped(runtimeEx, "propName", new DummyBean());
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Runtime failure", e.getMessage());
        }
    }

    @Test
    public void testThrowWrappedWithNestedException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        JavaType type = mapper.constructType(DummyBean.class);
        BeanDescription beanDesc = config.introspect(type);

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);
        Exception nestedEx = new Exception("Root cause", new IllegalArgumentException("Nested cause"));
        
        try {
            builder._throwWrapped(nestedEx, "propName", new DummyBean());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Check if it wrapped or rethrown correctly
            assertNotNull(e);
        }
    }

    @Test
    public void testFindSerializationTypeIllegal() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        JavaType type = mapper.constructType(DummyBean.class);
        BeanDescription beanDesc = config.introspect(type);

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);
        
        AnnotatedMethod am = beanDesc.findMethod("toString", new Class<?>[0]);
        JavaType declaredType = mapper.constructType(Integer.class);

        // If secondary type is not a super-type or related properly, it should throw IAE or similar
        try {
            // Passing a declared type that is completely unrelated to what refineSerializationType might return
            // Or we test findSerializationType directly with valid parameters
            JavaType result = builder.findSerializationType(am, false, declaredType);
            // Might be null if no annotation
            assertNull(result);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }
}