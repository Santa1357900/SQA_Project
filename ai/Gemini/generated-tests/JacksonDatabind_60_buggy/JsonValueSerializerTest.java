package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.BeanProperty;

public class JsonValueSerializerTest {

    private static enum SampleEnum {
        A("ValueA"),
        B("ValueB");

        private final String val;
        SampleEnum(String v) { this.val = v; }

        @com.fasterxml.jackson.annotation.JsonValue
        public String getValue() {
            return val;
        }
    }

    private static class DummyBean {
        public String getStringValue() {
            return "testString";
        }

        public String getNullValue() {
            return null;
        }

        public Object getErrorValue() {
            throw new RuntimeException("Expected error in accessor");
        }

        public Object getInvocationErrorValue() throws Exception {
            throw new InvocationTargetException(new IOException("Cause error"));
        }
        
        public Object getErrorObject() throws Exception {
            throw new InvocationTargetException(new Error("Fatal Error"));
        }
    }

    private AnnotatedMethod getAnnotatedMethod(Class<?> targetClass, String methodName) throws Exception {
        Method m = targetClass.getMethod(methodName);
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        AnnotatedClass ac = AnnotatedClass.construct(targetClass, config, config);
        return new AnnotatedMethod(ac, m, null, null);
    }

    @Test
    public void testConstructorsAndResolved() throws Throwable {
        AnnotatedMethod am = getAnnotatedMethod(DummyBean.class, "getStringValue");
        JsonValueSerializer ser = new JsonValueSerializer(am, null);
        assertNotNull(ser);

        JsonValueSerializer resolved = ser.withResolved(null, ser, false);
        assertNotNull(resolved);

        // Test withResolved same parameters branch
        JsonValueSerializer same = resolved.withResolved(null, resolved, false);
        assertEquals(same, resolved);
    }

    @Test
    public void testToString() throws Throwable {
        AnnotatedMethod am = getAnnotatedMethod(DummyBean.class, "getStringValue");
        JsonValueSerializer ser = new JsonValueSerializer(am, null);
        String str = ser.toString();
        assertTrue(str.contains("JsonValue serializer for method"));
    }

    @Test
    public void testGetSchema() throws Throwable {
        AnnotatedMethod am = getAnnotatedMethod(DummyBean.class, "getStringValue");
        JsonValueSerializer ser = new JsonValueSerializer(am, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        
        JsonNode node = ser.getSchema(provider, null);
        assertNotNull(node);
    }

    @Test
    public void testAcceptJsonFormatVisitorEnum() throws Throwable {
        AnnotatedMethod am = getAnnotatedMethod(SampleEnum.class, "getValue");
        JsonValueSerializer ser = new JsonValueSerializer(am, null);
        
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        
        // Mock JsonFormatVisitorWrapper
        JsonFormatVisitorWrapper visitor = new JsonFormatVisitorWrapper() {
            public SerializerProvider getProvider() { return provider; }
            public void setProvider(SerializerProvider p) {}
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor expectNumberFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor expectStringFormat(JavaType type) throws JsonMappingException {
                return new JsonStringFormatVisitor() {
                    public void format(com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat format) {}
                    public void enumTypes(Set<String> enums) {
                        assertNotNull(enums);
                        assertTrue(enums.contains("ValueA"));
                        assertTrue(enums.contains("ValueB"));
                    }
                };
            }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(JavaType type) throws JsonMappingException { return null; }
            public void expectAnyFormat(JavaType type) throws JsonMappingException {}
        };

        ser.acceptJsonFormatVisitor(visitor, mapper.constructType(SampleEnum.class));
    }

    @Test
    public void testIsNaturalTypeWithStdHandling() throws Throwable {
        AnnotatedMethod am = getAnnotatedMethod(DummyBean.class, "getStringValue");
        JsonValueSerializer ser = new JsonValueSerializer(am, null);

        // Test primitives and wrappers and non-natural
        assertTrue(ser.isNaturalTypeWithStdHandling(String.class, new StringSerializer()));
        assertTrue(ser.isNaturalTypeWithStdHandling(Integer.class, new com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer(null)));
        assertFalse(ser.isNaturalTypeWithStdHandling(Object.class, new StringSerializer()));
        
        // Primitive checks
        assertTrue(ser.isNaturalTypeWithStdHandling(Integer.TYPE, new com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer(null)));
        assertFalse(ser.isNaturalTypeWithStdHandling(Long.TYPE, new com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer(null)));
    }

    @Test
    public void testNotEnumAcceptJsonFormatVisitor() throws Throwable {
        AnnotatedMethod am = getAnnotatedMethod(DummyBean.class, "getStringValue");
        JsonValueSerializer ser = new JsonValueSerializer(am, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();

        JsonFormatVisitorWrapper visitor = new JsonFormatVisitorWrapper() {
            public SerializerProvider getProvider() { return provider; }
            public void setProvider(SerializerProvider p) {}
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor expectNumberFormat(JavaType type) throws JsonMappingException { return null; }
            public JsonStringFormatVisitor expectStringFormat(JavaType type) throws JsonMappingException { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(JavaType type) throws JsonMappingException { return null; }
            public void expectAnyFormat(JavaType type) throws JsonMappingException {}
        };

        ser.acceptJsonFormatVisitor(visitor, mapper.constructType(String.class));
    }
}