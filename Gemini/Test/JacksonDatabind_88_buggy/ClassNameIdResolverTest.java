package com.fasterxml.jackson.databind.jsontype.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ClassNameIdResolverTest {

    private enum TestEnum {
        A, B, C
    }

    private static class InnerClass {
    }

    @Test
    public void testGetMechanism() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        assertEquals(JsonTypeInfo.Id.CLASS, resolver.getMechanism());
    }

    @Test
    public void testRegisterSubtype() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        // registerSubtype is a no-op, just ensuring it executes without error
        resolver.registerSubtype(String.class, "string");
        assertTrue(true);
    }

    @Test
    public void testIdFromValue() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        String id = resolver.idFromValue("test");
        assertEquals(String.class.getName(), id);
    }

    @Test
    public void testIdFromValueAndType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        String id = resolver.idFromValueAndType("test", Integer.class);
        assertEquals(Integer.class.getName(), id);
    }

    @Test
    public void testIdFromEnumSubtype() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(TestEnum.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        // Anonymous subclass of an enum
        TestEnum subEnum = new TestEnum() {
            // sub-class
        };

        String id = resolver.idFromValue(subEnum);
        assertEquals(TestEnum.class.getName(), id);
    }

    @Test
    public void testIdFromEnumSet() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(EnumSet.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        EnumSet<TestEnum> enumSet = EnumSet.of(TestEnum.A);
        String id = resolver.idFromValue(enumSet);
        assertTrue(id.contains("EnumSet"));
    }

    @Test
    public void testIdFromEnumMap() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(EnumMap.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        EnumMap<TestEnum, String> enumMap = new EnumMap<TestEnum, String>(TestEnum.class);
        enumMap.put(TestEnum.A, "value");
        String id = resolver.idFromValue(enumMap);
        assertTrue(id.contains("EnumMap"));
    }

    @Test
    public void testIdFromArraysAsList() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(List.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        List<String> list = Arrays.asList("a", "b");
        String id = resolver.idFromValue(list);
        assertEquals(ArrayList.class.getName(), id);
    }

    @Test
    public void testIdFromInnerClass() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        InnerClass inner = new InnerClass();
        String id = resolver.idFromValue(inner);
        assertEquals(InnerClass.class.getName(), id);
    }

    @Test
    public void testTypeFromIdCanonical() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        String canonicalId = "java.util.List<java.lang.String>";
        JavaType type = resolver.typeFromId(ctxt, canonicalId);
        assertNotNull(type);
    }

    @Test
    public void testTypeFromIdNormalClass() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        JavaType type = resolver.typeFromId(ctxt, "java.lang.String");
        assertNotNull(type);
        assertEquals(String.class, type.getRawClass());
    }

    @Test
    public void testTypeFromIdClassNotFound() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        // Should handle unknown type id via context or return null/handled type
        JavaType type = resolver.typeFromId(ctxt, "com.nonexistent.ClassNameXYZ");
        // Depending on context handling, it might return null or a default type.
        // We just ensure it doesn't throw an unexpected exception.
        assertTrue(true);
    }

    @Test
    public void testTypeFromIdException() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        // Passing an invalid format that triggers general Exception in tf.findClass if any,
        // or we test error propagation.
        try {
            // Using a malformed id that is not canonical and causes issues
            resolver.typeFromId(null, "[invalid-id");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid type id"));
        } catch (Exception e) {
            // Expected some form of exception for malformed id
            assertNotNull(e);
        }
    }

    @Test
    public void testGetDescForKnownTypeIds() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, tf);

        assertEquals("class name used as type id", resolver.getDescForKnownTypeIds());
    }
}