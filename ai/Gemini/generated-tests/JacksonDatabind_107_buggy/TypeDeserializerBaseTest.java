package com.fasterxml.jackson.databind.jsontype.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class TypeDeserializerBaseTest {

    private static class ConcreteTypeDeserializer extends TypeDeserializerBase {
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

    @Test
    public void testConstructorsAndAccessors() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        JavaType defaultImpl = tf.constructType(String.class);

        ConcreteTypeDeserializer deser = new ConcreteTypeDeserializer(
                baseType, null, "typeProp", true, defaultImpl);

        assertEquals("java.lang.Object", deser.baseTypeName());
        assertEquals("typeProp", deser.getPropertyName());
        assertNull(deser.getTypeIdResolver());
        assertEquals(String.class, deser.getDefaultImpl());
        assertEquals(baseType, deser.baseType());

        String toStringVal = deser.toString();
        assertTrue(toStringVal.contains("ConcreteTypeDeserializer"));
        assertTrue(toStringVal.contains("base-type"));

        TypeDeserializer copy = deser.forProperty(null);
        assertNotNull(copy);
        assertEquals(JsonTypeInfo.As.PROPERTY, copy.getTypeInclusion());
    }

    @Test
    public void testBaseTypeNameWithCustomBase() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Integer.class);
        ConcreteTypeDeserializer deser = new ConcreteTypeDeserializer(
                baseType, null, "", false, null);

        assertEquals("java.lang.Integer", deser.baseTypeName());
        assertEquals("", deser.getPropertyName());
        assertNull(deser.getDefaultImpl());
    }

    @Test
    public void testToStringFormatting() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(String.class);
        ConcreteTypeDeserializer deser = new ConcreteTypeDeserializer(
                baseType, null, null, false, null);

        String result = deser.toString();
        assertNotNull(result);
        assertTrue(result.startsWith("["));
        assertTrue(result.endsWith("]"));
    }
}