package org.apache.commons.collections.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaseInsensitiveMapClaudeTest {

    private CaseInsensitiveMap map;

    @Before
    public void setUp() throws Throwable {
        map = new CaseInsensitiveMap();
    }

    // Default constructor creates an empty map
    @Test
    public void testDefaultConstructor_createsEmptyMap() throws Throwable {
        CaseInsensitiveMap m = new CaseInsensitiveMap();
        assertEquals(0, m.size());
        assertTrue(m.isEmpty());
    }

    // int constructor with positive capacity succeeds
    @Test
    public void testIntConstructor_positiveCapacity_createsEmptyMap() throws Throwable {
        CaseInsensitiveMap m = new CaseInsensitiveMap(10);
        assertEquals(0, m.size());
        assertTrue(m.isEmpty());
    }

    // int constructor: capacity == 0 is "less than one" -> throws
    @Test
    public void testIntConstructor_zeroCapacity_throwsIllegalArgumentException() throws Throwable {
        try {
            new CaseInsensitiveMap(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // int constructor: negative capacity -> throws
    @Test
    public void testIntConstructor_negativeCapacity_throwsIllegalArgumentException() throws Throwable {
        try {
            new CaseInsensitiveMap(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // int,float constructor with valid args succeeds
    @Test
    public void testIntFloatConstructor_validArgs_createsEmptyMap() throws Throwable {
        CaseInsensitiveMap m = new CaseInsensitiveMap(10, 0.75f);
        assertEquals(0, m.size());
        assertTrue(m.isEmpty());
    }

    // int,float constructor: capacity < 1 -> throws
    @Test
    public void testIntFloatConstructor_zeroCapacity_throwsIllegalArgumentException() throws Throwable {
        try {
            new CaseInsensitiveMap(0, 0.75f);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // int,float constructor: loadFactor < 0 -> throws
    @Test
    public void testIntFloatConstructor_negativeLoadFactor_throwsIllegalArgumentException() throws Throwable {
        try {
            new CaseInsensitiveMap(10, -1f);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // Map constructor: null map -> NullPointerException
    @Test
    public void testMapConstructor_nullMap_throwsNullPointerException() throws Throwable {
        try {
            new CaseInsensitiveMap((Map) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // ok
        }
    }

    // Map constructor merges entries whose keys differ only by case
    @Test
    public void testMapConstructor_mergesCaseInsensitiveDuplicates() throws Throwable {
        Map source = new LinkedHashMap();
        source.put("One", "1");
        source.put("ONE", "2");
        source.put("Two", "Two2");
        CaseInsensitiveMap copy = new CaseInsensitiveMap(source);
        assertEquals(2, copy.size());
        assertTrue(copy.containsKey("one"));
        assertTrue(copy.containsKey("two"));
    }

    // Map constructor with empty source map creates empty map
    @Test
    public void testMapConstructor_emptySourceMap_createsEmptyMap() throws Throwable {
        CaseInsensitiveMap copy = new CaseInsensitiveMap(new HashMap());
        assertEquals(0, copy.size());
        assertTrue(copy.isEmpty());
    }

    // convertKey: non-null string key is lower-cased
    @Test
    public void testConvertKey_nonNullStringKey_returnsLowercase() throws Throwable {
        Object converted = map.convertKey("ABC");
        assertEquals("abc", converted);
    }

    // convertKey: null key returns AbstractHashedMap.NULL marker
    @Test
    public void testConvertKey_nullKey_returnsNullConstant() throws Throwable {
        Object converted = map.convertKey(null);
        assertEquals(AbstractHashedMap.NULL, converted);
    }

    // convertKey: non-string key uses its toString() representation
    @Test
    public void testConvertKey_nonStringKey_usesToString() throws Throwable {
        Object converted = map.convertKey(Integer.valueOf(123));
        assertEquals("123", converted);
    }

    // put: javadoc example - case-insensitive overwrite plus null key support
    @Test
    public void testPut_javadocExample_caseInsensitiveOverwriteAndNullKey() throws Throwable {
        map.put("One", "One");
        map.put("Two", "Two");
        map.put(null, "Three");
        map.put("one", "Four");
        assertEquals(3, map.size());
        assertEquals("Three", map.get(null));
        assertEquals("Four", map.get("ONE"));
    }

    // get: keys that differ only by case retrieve the same value
    @Test
    public void testGet_caseInsensitiveLookup_returnsSameValue() throws Throwable {
        map.put("Hello", "World");
        assertEquals("World", map.get("hello"));
        assertEquals("World", map.get("HELLO"));
        assertEquals("World", map.get("HeLLo"));
    }

    // get: missing key returns null
    @Test
    public void testGet_missingKey_returnsNull() throws Throwable {
        assertNull(map.get("missing"));
    }

    // containsKey: true for a key differing only in case from a stored one
    @Test
    public void testContainsKey_caseInsensitive_trueForDifferentCase() throws Throwable {
        map.put("Key", "Value");
        assertTrue(map.containsKey("KEY"));
        assertTrue(map.containsKey("key"));
    }

    // containsKey: null key supported
    @Test
    public void testContainsKey_nullKey_trueAfterPut() throws Throwable {
        map.put(null, "NullValue");
        assertTrue(map.containsKey(null));
    }

    // containsValue: after put, stored value is found; absent value is not
    @Test
    public void testContainsValue_afterPut() throws Throwable {
        map.put("Key", "Value");
        assertTrue(map.containsValue("Value"));
        assertFalse(map.containsValue("NotPresent"));
    }

    // remove: case-insensitive removal returns previous value and clears entry
    @Test
    public void testRemove_caseInsensitive_returnsValueAndRemoves() throws Throwable {
        map.put("Key", "Value");
        Object removed = map.remove("KEY");
        assertEquals("Value", removed);
        assertFalse(map.containsKey("key"));
        assertEquals(0, map.size());
    }

    // remove: non existent key returns null and leaves map untouched
    @Test
    public void testRemove_nonExistentKey_returnsNull() throws Throwable {
        map.put("Key", "Value");
        Object removed = map.remove("missing");
        assertNull(removed);
        assertEquals(1, map.size());
    }

    // size: reflects number of distinct lowercase keys after merging duplicates
    @Test
    public void testSize_reflectsDistinctLowercaseKeys() throws Throwable {
        map.put("A", "1");
        map.put("a", "2");
        map.put("B", "3");
        assertEquals(2, map.size());
    }

    // isEmpty: true initially, false after a put
    @Test
    public void testIsEmpty_trueInitiallyFalseAfterPut() throws Throwable {
        assertTrue(map.isEmpty());
        map.put("K", "V");
        assertFalse(map.isEmpty());
    }

    // clear: removes all entries
    @Test
    public void testClear_removesAllEntries() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    // keySet: returns lowercase keys plus null, per javadoc example
    @Test
    public void testKeySet_lowercaseKeysAndNull() throws Throwable {
        map.put("One", "One");
        map.put("Two", "Two");
        map.put(null, "Three");
        map.put("one", "Four");
        Set keys = map.keySet();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("one"));
        assertTrue(keys.contains("two"));
        assertTrue(keys.contains(null));
    }

    // putAll: entries copied with keys converted to lowercase
    @Test
    public void testPutAll_mergesCaseInsensitively() throws Throwable {
        Map source = new HashMap();
        source.put("Alpha", "A");
        source.put("BETA", "B");
        map.putAll(source);
        assertEquals(2, map.size());
        assertEquals("A", map.get("alpha"));
        assertEquals("B", map.get("beta"));
    }

    // clone: shallow copy is independent of subsequent modifications to original
    @Test
    public void testClone_shallowIndependentCopy() throws Throwable {
        map.put("Key", "Value");
        CaseInsensitiveMap clone = (CaseInsensitiveMap) map.clone();
        assertEquals("Value", clone.get("key"));
        map.put("Key2", "Value2");
        assertFalse(clone.containsKey("key2"));
        assertEquals(1, clone.size());
    }

    // serialization: round trip via ObjectOutputStream/ObjectInputStream preserves entries
    @Test
    public void testSerialization_roundTripPreservesEntries() throws Throwable {
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

        assertEquals(2, deserialized.size());
        assertEquals("Value", deserialized.get("key"));
        assertEquals("NullVal", deserialized.get(null));
    }

    // Javadoc: lowercasing must be "locale-independent"; must not depend on default Locale
    @Test
    public void testConvertKey_turkishLocale_localeIndependentLowercasing() throws Throwable {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            CaseInsensitiveMap m = new CaseInsensitiveMap();
            m.put("I", "value");
            assertEquals("value", m.get("i"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
