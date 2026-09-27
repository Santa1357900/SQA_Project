package org.apache.commons.collections.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class CaseInsensitiveMapTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    public void testCapacityConstructor() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap(16);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityConstructorInvalid() throws Throwable {
        new CaseInsensitiveMap(0);
    }

    @Test
    public void testCapacityAndLoadFactorConstructor() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap(16, 0.75f);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityAndLoadFactorConstructorInvalid() throws Throwable {
        new CaseInsensitiveMap(-1, 0.75f);
    }

    @Test
    public void testMapConstructor() throws Throwable {
        Map<String, String> source = new HashMap<String, String>();
        source.put("Key", "Value");
        
        CaseInsensitiveMap map = new CaseInsensitiveMap(source);
        assertEquals(1, map.size());
        assertEquals("Value", map.get("key"));
        assertEquals("Value", map.get("KEY"));
    }

    @Test(expected = NullPointerException.class)
    public void testMapConstructorNull() throws Throwable {
        new CaseInsensitiveMap((Map) null);
    }

    @Test
    public void testPutAndGetCaseInsensitivity() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("One", "Value1");
        map.put("TWO", "Value2");
        map.put(null, "ValueNull");

        assertEquals(3, map.size());
        assertEquals("Value1", map.get("one"));
        assertEquals("Value1", map.get("ONE"));
        assertEquals("Value1", map.get("oNe"));
        assertEquals("Value2", map.get("two"));
        assertEquals("ValueNull", map.get(null));
    }

    @Test
    public void testContainsKey() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("Hello", "World");
        map.put(null, "NullValue");

        assertTrue(map.containsKey("hello"));
        assertTrue(map.containsKey("HELLO"));
        assertTrue(map.containsKey(null));
        assertFalse(map.containsKey("notfound"));
    }

    @Test
    public void testContainsValue() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("Key", "Value");

        assertTrue(map.containsValue("Value"));
        assertFalse(map.containsValue("OtherValue"));
        assertFalse(map.containsValue(null));
    }

    @Test
    public void testRemove() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("RemoveMe", "Success");
        map.put(null, "NullRemoved");

        assertEquals("Success", map.remove("REMOVEME"));
        assertNull(map.get("removeme"));
        assertEquals(1, map.size());

        assertEquals("NullRemoved", map.remove(null));
        assertTrue(map.isEmpty());
    }

    @Test
    public void testKeySet() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("UPPER", "1");
        map.put("lower", "2");
        map.put(null, "3");

        Set keys = map.keySet();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("upper"));
        assertTrue(keys.contains("lower"));
        assertTrue(keys.contains(null));
    }

    @Test
    public void testClone() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("Key", "Value");

        CaseInsensitiveMap cloned = (CaseInsensitiveMap) map.clone();
        assertNotNull(cloned);
        assertEquals(map.size(), cloned.size());
        assertEquals("Value", cloned.get("key"));
    }

    @Test
    public void testSerialization() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        map.put("Key", "Value");
        map.put(null, "NullVal");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(map);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        CaseInsensitiveMap deserialized = (CaseInsensitiveMap) ois.readObject();
        ois.close();

        assertNotNull(deserialized);
        assertEquals(map.size(), deserialized.size());
        assertEquals("Value", deserialized.get("key"));
        assertEquals("NullVal", deserialized.get(null));
    }

    @Test
    public void testConvertKeyNonString() throws Throwable {
        CaseInsensitiveMap map = new CaseInsensitiveMap();
        Integer intKey = Integer.valueOf(123);
        map.put(intKey, "IntegerValue");

        assertEquals("IntegerValue", map.get(Integer.valueOf(123)));
        assertTrue(map.containsKey(Integer.valueOf(123)));
    }
}