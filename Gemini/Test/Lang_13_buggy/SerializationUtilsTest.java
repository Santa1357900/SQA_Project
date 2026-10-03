package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class SerializationUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        SerializationUtils utils = new SerializationUtils();
        assertNotNull(utils);
    }

    @Test
    public void testCloneNull() throws Throwable {
        String cloned = SerializationUtils.clone(null);
        assertNull(cloned);
    }

    @Test
    public void testCloneValid() throws Throwable {
        String original = "Hello, SerializationUtils!";
        String cloned = SerializationUtils.clone(original);
        assertNotNull(cloned);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void testCloneNonSerializable() throws Throwable {
        Object nonSerializable = new Object();
        try {
            SerializationUtils.clone((Serializable) nonSerializable);
            fail("Expected SerializationException");
        } catch (SerializationException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testSerializeNullObjectToOutputStream() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] bytes = baos.toByteArray();
        assertTrue(bytes.length > 0);

        Object deserialized = SerializationUtils.deserialize(bytes);
        assertNull(deserialized);
    }

    @Test
    public void testSerializeNullOutputStream() throws Throwable {
        try {
            SerializationUtils.serialize("Test", (OutputStream) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testSerializeAndDeserializeValid() throws Throwable {
        Map<String, String> map = new HashMap<String, String>();
        map.put("key1", "value1");

        byte[] serialized = SerializationUtils.serialize(map);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        Object deserialized = SerializationUtils.deserialize(serialized);
        assertNotNull(deserialized);
        assertTrue(deserialized instanceof Map);
        assertEquals(map, deserialized);
    }

    @Test
    public void testDeserializeNullInputStream() throws Throwable {
        try {
            SerializationUtils.deserialize((InputStream) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testDeserializeNullByteArray() throws Throwable {
        try {
            SerializationUtils.deserialize((byte[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testDeserializeInvalidData() throws Throwable {
        byte[] invalidData = new byte[] { 1, 2, 3, 4 };
        try {
            SerializationUtils.deserialize(invalidData);
            fail("Expected SerializationException");
        } catch (SerializationException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testClassLoaderAwareObjectInputStream() throws Throwable {
        String original = "ClassLoaderAwareTest";
        byte[] serialized = SerializationUtils.serialize(original);

        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        SerializationUtils.ClassLoaderAwareObjectInputStream in = 
            new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, original.getClass().getClassLoader());
        
        Object result = in.readObject();
        assertEquals(original, result);
        in.close();
    }
}