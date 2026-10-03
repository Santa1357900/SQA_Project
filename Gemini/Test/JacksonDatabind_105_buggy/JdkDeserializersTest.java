package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonDeserializer;

public class JdkDeserializersTest {

    @Test
    public void testFindWithUnknownClassName() throws Throwable {
        JsonDeserializer<?> deserializer = JdkDeserializers.find(String.class, "java.lang.String");
        assertNull(deserializer);
    }

    @Test
    public void testFindWithNullRawTypeAndUnknownName() throws Throwable {
        JsonDeserializer<?> deserializer = JdkDeserializers.find(null, "non.existent.Class");
        assertNull(deserializer);
    }

    @Test
    public void testFindUuidDeserializer() throws Throwable {
        JsonDeserializer<?> deserializer = JdkDeserializers.find(UUID.class, UUID.class.getName());
        assertNotNull(deserializer);
    }

    @Test
    public void testFindAtomicBooleanDeserializer() throws Throwable {
        JsonDeserializer<?> deserializer = JdkDeserializers.find(AtomicBoolean.class, AtomicBoolean.class.getName());
        assertNotNull(deserializer);
    }

    @Test
    public void testFindStackTraceElementDeserializer() throws Throwable {
        JsonDeserializer<?> deserializer = JdkDeserializers.find(StackTraceElement.class, StackTraceElement.class.getName());
        assertNotNull(deserializer);
    }

    @Test
    public void testFindByteBufferDeserializer() throws Throwable {
        JsonDeserializer<?> deserializer = JdkDeserializers.find(ByteBuffer.class, ByteBuffer.class.getName());
        assertNotNull(deserializer);
    }

    @Test
    public void testFindFromStringDeserializerType() throws Throwable {
        Class<?>[] fromStringTypes = FromStringDeserializer.types();
        if (fromStringTypes != null && fromStringTypes.length > 0) {
            Class<?> targetClass = fromStringTypes[0];
            JsonDeserializer<?> deserializer = JdkDeserializers.find(targetClass, targetClass.getName());
            assertNotNull(deserializer);
        }
    }

    @Test
    public void testFindInClassNamesButNoMatchingDeserializer() throws Throwable {
        JsonDeserializer<?> deserializer = JdkDeserializers.find(JdkDeserializersTest.class, UUID.class.getName());
        assertNull(deserializer);
    }
}