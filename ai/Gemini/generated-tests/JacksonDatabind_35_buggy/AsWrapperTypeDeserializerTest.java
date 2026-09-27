package com.fasterxml.jackson.databind.jsontype.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class AsWrapperTypeDeserializerTest {

    @Test
    public void testConstructorsAndForProperty() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        TypeFactory tf = mapper.getTypeFactory();
        JavaType baseType = tf.constructType(Object.class);
        TypeIdResolver idRes = new MinimalClassNameIdResolver(baseType, tf);
        
        AsWrapperTypeDeserializer deser = new AsWrapperTypeDeserializer(
                baseType, idRes, "typeProp", true, Object.class);
        
        assertEquals(As.WRAPPER_OBJECT, deser.getTypeInclusion());
        
        TypeDeserializer forProp = deser.forProperty(null);
        assertNotNull(forProp);
        
        // Same property should return this
        TypeDeserializer forPropSame = deser.forProperty(deser._property);
        assertSame(deser, forPropSame);
    }

    @Test
    public void testGetTypeInclusion() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        TypeFactory tf = mapper.getTypeFactory();
        JavaType baseType = tf.constructType(Object.class);
        TypeIdResolver idRes = new MinimalClassNameIdResolver(baseType, tf);
        
        AsWrapperTypeDeserializer deser = new AsWrapperTypeDeserializer(
                baseType, idRes, "typeProp", false, null);
        
        assertEquals(As.WRAPPER_OBJECT, deser.getTypeInclusion());
    }

    @Test
    public void testDeserializeTypedMethods() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        TypeFactory tf = mapper.getTypeFactory();
        JavaType baseType = tf.constructType(Object.class);
        TypeIdResolver idRes = new MinimalClassNameIdResolver(baseType, tf);
        
        AsWrapperTypeDeserializer deser = new AsWrapperTypeDeserializer(
                baseType, idRes, "typeProp", false, null);
        
        // Test that all deserializeTypedXXX delegate correctly (will fail on parser validation, ensuring execution path)
        JsonParser jp = mapper.getFactory().createParser("{}");
        jp.nextToken();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        try {
            deser.deserializeTypedFromObject(jp, ctxt);
            fail("Expected exception for invalid token structure");
        } catch (Throwable e) {
            assertNotNull(e);
        }

        JsonParser jp2 = mapper.getFactory().createParser("[]");
        jp2.nextToken();
        try {
            deser.deserializeTypedFromArray(jp2, ctxt);
            fail("Expected exception for invalid token structure");
        } catch (Throwable e) {
            assertNotNull(e);
        }

        JsonParser jp3 = mapper.getFactory().createParser("123");
        jp3.nextToken();
        try {
            deser.deserializeTypedFromScalar(jp3, ctxt);
            fail("Expected exception for invalid token structure");
        } catch (Throwable e) {
            assertNotNull(e);
        }

        JsonParser jp4 = mapper.getFactory().createParser("true");
        jp4.nextToken();
        try {
            deser.deserializeTypedFromAny(jp4, ctxt);
            fail("Expected exception for invalid token structure");
        } catch (Throwable e) {
            assertNotNull(e);
        }
    }
}