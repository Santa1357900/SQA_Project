package com.fasterxml.jackson.databind.jsontype.impl;

import junit.framework.TestCase;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class TypeDeserializerBaseTest extends TestCase {

    private static class ConcreteTypeDeserializer extends TypeDeserializerBase {
        private static final long serialVersionUID = 1L;

        public ConcreteTypeDeserializer(JavaType baseType, TypeIdResolver idRes,
                String typePropertyName, boolean typeIdVisible, JavaType defaultImpl) {
            super(baseType, idRes, typePropertyName, typeIdVisible, defaultImpl);
        }

        public ConcreteTypeDeserializer(TypeDeserializerBase src, BeanProperty property) {
            super(src, property);
        }

        @Override
        public TypeDeserializer forProperty(BeanProperty prop) {
            return new ConcreteTypeDeserializer(this, prop);
        }

        @Override
        public JsonTypeInfo.As getTypeInclusion() {
            return JsonTypeInfo.As.PROPERTY;
        }
    }

    public void testConstructorsAndAccessors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        TypeFactory tf = mapper.getTypeFactory();
        JavaType baseType = tf.constructType(Object.class);
        
        ConcreteTypeDeserializer deser = new ConcreteTypeDeserializer(
                baseType, null, "typeProp", true, null);

        assertEquals("java.lang.Object", deser.baseTypeName());
        assertEquals("typeProp", deser.getPropertyName());
        assertNull(deser.getTypeIdResolver());
        assertNull(deser.getDefaultImpl());
        assertEquals(JsonTypeInfo.As.PROPERTY, deser.getTypeInclusion());

        String toStringVal = deser.toString();
        assertTrue(toStringVal.contains(ConcreteTypeDeserializer.class.getName()));
        assertTrue(toStringVal.contains("base-type"));

        TypeDeserializer copy = deser.forProperty(null);
        assertNotNull(copy);
    }

    public void testNullTypePropertyName() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType baseType = mapper.constructType(String.class);

        ConcreteTypeDeserializer deser = new ConcreteTypeDeserializer(
                baseType, null, null, false, null);

        assertEquals("", deser.getPropertyName());
    }

    public void testDefaultImplHandling() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType baseType = mapper.constructType(String.class);
        JavaType defaultImpl = mapper.constructType(Integer.class);

        ConcreteTypeDeserializer deser = new ConcreteTypeDeserializer(
                baseType, null, "class", false, defaultImpl);

        assertEquals(Integer.class, deser.getDefaultImpl());
    }
}