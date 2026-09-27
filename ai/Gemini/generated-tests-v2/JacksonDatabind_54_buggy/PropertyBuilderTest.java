package com.fasterxml.jackson.databind.ser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Annotations;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;

public class PropertyBuilderTest {

    static class DummyBean {
        public String getStringProp() { return "test"; }
        public int getIntProp() { return 42; }
    }

    @Test
    public void testConstructorAndGetClassAnnotations() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = config.introspect(mapper.constructType(DummyBean.class));

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);
        Annotations annotations = builder.getClassAnnotations();
        assertNotNull(annotations);
    }

    @Test
    public void testGetDefaultValuePrimitivesAndObjects() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = config.introspect(mapper.constructType(DummyBean.class));

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);

        JavaType intType = TypeFactory.defaultInstance().constructType(int.class);
        Object intDef = builder.getDefaultValue(intType);
        assertEquals(Integer.valueOf(0), intDef);

        JavaType stringType = TypeFactory.defaultInstance().constructType(String.class);
        Object strDef = builder.getDefaultValue(stringType);
        assertEquals("", strDef);

        JavaType listType = TypeFactory.defaultInstance().constructCollectionType(ArrayList.class, String.class);
        Object listDef = builder.getDefaultValue(listType);
        assertEquals(JsonInclude.Include.NON_EMPTY, listDef);
    }

    @Test
    public void testGetDefaultBean() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = config.introspect(mapper.constructType(DummyBean.class));

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc);
        
        // Access protected method via subclass or directly if package-private/accessible
        PropertyBuilder subBuilder = new PropertyBuilder(config, beanDesc) {
            public Object exposeGetDefaultBean() {
                return getDefaultBean();
            }
        };

        Object beanInstance = subBuilder.exposeGetDefaultBean();
        assertNotNull(beanInstance);
        assertTrue(beanInstance instanceof DummyBean);
    }

    @Test
    public void testThrowWrappedWithRuntimeException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = config.introspect(mapper.constructType(DummyBean.class));

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc) {
            public Object exposeThrowWrapped(Exception e, String name, Object bean) {
                return _throwWrapped(e, name, bean);
            }
        };

        IllegalArgumentException runtimeEx = new IllegalArgumentException("Original Runtime");
        try {
            builder.exposeThrowWrapped(runtimeEx, "propName", new DummyBean());
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Original Runtime", e.getMessage());
        }
    }

    @Test
    public void testThrowWrappedWithCheckedException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = config.introspect(mapper.constructType(DummyBean.class));

        PropertyBuilder builder = new PropertyBuilder(config, beanDesc) {
            public Object exposeThrowWrapped(Exception e, String name, Object bean) {
                return _throwWrapped(e, name, bean);
            }
        };

        Exception checkedEx = new Exception("Checked Exception");
        try {
            builder.exposeThrowWrapped(checkedEx, "propName", new DummyBean());
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Failed to get property 'propName'"));
        }
    }
}