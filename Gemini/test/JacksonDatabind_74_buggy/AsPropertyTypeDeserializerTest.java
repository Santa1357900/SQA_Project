package com.fasterxml.jackson.databind.jsontype.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.TokenBuffer;

public class AsPropertyTypeDeserializerTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        TypeIdResolver idRes = new AsArrayTypeDeserializer(baseType, null, "type", false, baseType).getTypeIdResolver();
        
        AsPropertyTypeDeserializer des1 = new AsPropertyTypeDeserializer(baseType, idRes, "type", true, baseType);
        assertEquals(As.PROPERTY, des1.getTypeInclusion());
        
        AsPropertyTypeDeserializer des2 = new AsPropertyTypeDeserializer(baseType, idRes, "type", true, baseType, As.EXISTING_PROPERTY);
        assertEquals(As.EXISTING_PROPERTY, des2.getTypeInclusion());
        
        AsPropertyTypeDeserializer des3 = new AsPropertyTypeDeserializer(des1, (BeanProperty) null);
        assertEquals(As.PROPERTY, des3.getTypeInclusion());
        
        TypeDeserializer forProp = des1.forProperty(null);
        assertNotNull(forProp);
    }

    @Test
    public void testDeserializeTypedFromAnyWithArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        TypeIdResolver idRes = new AsArrayTypeDeserializer(baseType, null, "type", false, baseType).getTypeIdResolver();
        
        AsPropertyTypeDeserializer des = new AsPropertyTypeDeserializer(baseType, idRes, "type", false, baseType);
        
        String json = "[]";
        JsonParser p = mapper.getFactory().createParser(json);
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        
        DeserializationContext ctxt = mapper.getDeserializationContext();
        try {
            des.deserializeTypedFromAny(p, ctxt);
        } catch (Exception e) {
            assertNotNull(e);
        }
        p.close();
    }

    @Test
    public void testDeserializeTypedFromObjectNotStartObject() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        TypeIdResolver idRes = new AsArrayTypeDeserializer(baseType, null, "type", false, baseType).getTypeIdResolver();
        
        AsPropertyTypeDeserializer des = new AsPropertyTypeDeserializer(baseType, idRes, "type", false, baseType);
        
        String json = "\"someString\"";
        JsonParser p = mapper.getFactory().createParser(json);
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        
        DeserializationContext ctxt = mapper.getDeserializationContext();
        try {
            des.deserializeTypedFromObject(p, ctxt);
            fail("Expected exception for missing property or default impl");
        } catch (Exception e) {
            assertNotNull(e);
        }
        p.close();
    }

    @Test
    public void testDeserializeTypedFromObjectWithField() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        TypeIdResolver idRes = new AsArrayTypeDeserializer(baseType, null, "type", false, baseType).getTypeIdResolver();
        
        AsPropertyTypeDeserializer des = new AsPropertyTypeDeserializer(baseType, idRes, "type", false, baseType);
        
        String json = "{\"otherField\":\"val\"}";
        JsonParser p = mapper.getFactory().createParser(json);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        
        DeserializationContext ctxt = mapper.getDeserializationContext();
        try {
            des.deserializeTypedFromObject(p, ctxt);
        } catch (Exception e) {
            assertNotNull(e);
        }
        p.close();
    }

    @Test
    public void testDeserializeTypedFromObjectWithMatchingTypeId() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        TypeIdResolver idRes = new AsArrayTypeDeserializer(baseType, null, "type", false, baseType).getTypeIdResolver();
        
        AsPropertyTypeDeserializer des = new AsPropertyTypeDeserializer(baseType, idRes, "type", true, baseType);
        
        String json = "{\"type\":\"string\",\"val\":\"test\"}";
        JsonParser p = mapper.getFactory().createParser(json);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        
        DeserializationContext ctxt = mapper.getDeserializationContext();
        try {
            des.deserializeTypedFromObject(p, ctxt);
        } catch (Exception e) {
            assertNotNull(e);
        }
        p.close();
    }
}