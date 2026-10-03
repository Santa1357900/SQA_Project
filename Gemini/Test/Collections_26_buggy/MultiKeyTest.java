package org.apache.commons.collections4.keyvalue;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class MultiKeyTest {

    @Test
    public void testTwoKeysConstructor() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        assertEquals(2, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
    }

    @Test
    public void testThreeKeysConstructor() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B", "C");
        assertEquals(3, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        assertEquals("C", mk.getKey(2));
    }

    @Test
    public void testFourKeysConstructor() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B", "C", "D");
        assertEquals(4, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        assertEquals("C", mk.getKey(2));
        assertEquals("D", mk.getKey(3));
    }

    @Test
    public void testFiveKeysConstructor() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B", "C", "D", "E");
        assertEquals(5, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        assertEquals("C", mk.getKey(2));
        assertEquals("D", mk.getKey(3));
        assertEquals("E", mk.getKey(4));
    }

    @Test
    public void testArrayConstructorWithClone() throws Throwable {
        String[] keys = new String[] { "X", "Y" };
        MultiKey<String> mk = new MultiKey<String>(keys, true);
        assertEquals(2, mk.size());
        assertEquals("X", mk.getKey(0));
        assertEquals("Y", mk.getKey(1));

        // Mutating original array should not affect MultiKey because it was cloned
        keys[0] = "Z";
        assertEquals("X", mk.getKey(0));
    }

    @Test
    public void testArrayConstructorWithoutClone() throws Throwable {
        String[] keys = new String[] { "X", "Y" };
        MultiKey<String> mk = new MultiKey<String>(keys, false);
        assertEquals(2, mk.size());
        assertEquals("X", mk.getKey(0));
        assertEquals("Y", mk.getKey(1));
    }

    @Test
    public void testArrayConstructorSingleArg() throws Throwable {
        String[] keys = new String[] { "M", "N" };
        MultiKey<String> mk = new MultiKey<String>(keys);
        assertEquals(2, mk.size());
        assertEquals("M", mk.getKey(0));
        assertEquals("N", mk.getKey(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArrayConstructorNullKeys() throws Throwable {
        String[] keys = null;
        new MultiKey<String>(keys);
    }

    @Test(expected = IllegalArgumentException.class)
    publicoid testArrayConstructorNullKeysWithBoolean() throws Throwable {
        String[] keys = null;
        new MultiKey<String>(keys, true);
    }

    @Test
    public void testGetKeys() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        String[] keys = mk.getKeys();
        assertNotNull(keys);
        assertEquals(2, keys.length);
        assertEquals("A", keys[0]);
        assertEquals("B", keys[1]);

        // Modifying returned array should not affect internal state
        keys[0] = "Changed";
        assertEquals("A", mk.getKey(0));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>("A", "B");
        MultiKey<String> mk2 = new MultiKey<String>("A", "B");
        MultiKey<String> mk3 = new MultiKey<String>("A", "C");
        MultiKey<String> mk4 = new MultiKey<String>("A", "B", "C");

        assertTrue(mk1.equals(mk1));
        assertTrue(mk1.equals(mk2));
        assertEquals(mk1.hashCode(), mk2.hashCode());

        assertFalse(mk1.equals(mk3));
        assertFalse(mk1.equals(mk4));
        assertFalse(mk1.equals(null));
        assertFalse(mk1.equals("NotA-MultiKey"));
    }

    @Test
    public void testEqualsWithNullKeys() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>(null, "B");
        MultiKey<String> mk2 = new MultiKey<String>(null, "B");
        MultiKey<String> mk3 = new MultiKey<String>("A", "B");

        assertTrue(mk1.equals(mk2));
        assertFalse(mk1.equals(mk3));
        assertEquals(mk1.hashCode(), mk2.hashCode());
    }

    @Test
    public void testToString() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        String str = mk.toString();
        assertNotNull(str);
        assertTrue(str.contains("MultiKey"));
        assertTrue(str.contains("A"));
        assertTrue(str.contains("B"));
    }

    @Test
    public void testSerialization() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("Hello", "World");
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(mk);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object obj = ois.readObject();
        ois.close();

        assertTrue(obj instanceof MultiKey);
        MultiKey<?> deserialized = (MultiKey<?>) obj;
        assertEquals(mk, deserialized);
        assertEquals(mk.hashCode(), deserialized.hashCode());
    }
}