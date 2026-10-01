package org.apache.commons.collections4.keyvalue;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class MultiKeyClaudeTest {

    // Covers 2-arg constructor: array of size 2, order preserved
    @Test
    public void testTwoArgConstructor_setsKeysAndSize() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        assertEquals(2, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
    }

    // Covers 3-arg constructor: array of size 3, order preserved
    @Test
    public void testThreeArgConstructor_setsKeysAndSize() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B", "C");
        assertEquals(3, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        assertEquals("C", mk.getKey(2));
    }

    // Covers 4-arg constructor: array of size 4, last key correct
    @Test
    public void testFourArgConstructor_setsKeysAndSize() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B", "C", "D");
        assertEquals(4, mk.size());
        assertEquals("D", mk.getKey(3));
    }

    // Covers 5-arg constructor: array of size 5, last key correct
    @Test
    public void testFiveArgConstructor_setsKeysAndSize() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B", "C", "D", "E");
        assertEquals(5, mk.size());
        assertEquals("E", mk.getKey(4));
    }

    // Covers MultiKey(K[]) with zero-length array: loop executes 0 times
    @Test
    public void testArrayConstructor_emptyArray_sizeZero() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>(new String[0]);
        assertEquals(0, mk.size());
    }

    // Covers MultiKey(K[]) clones the array: mutating original does not affect instance
    @Test
    public void testArrayConstructor_clonesArray_mutationDoesNotAffectMultiKey() throws Throwable {
        String[] original = new String[] {"A", "B"};
        MultiKey<String> mk = new MultiKey<String>(original);
        original[0] = "Z";
        assertEquals("A", mk.getKey(0));
    }

    // Covers MultiKey(K[]) null check: throws IllegalArgumentException
    @Test
    public void testArrayConstructor_nullArray_throwsIllegalArgumentException() throws Throwable {
        try {
            new MultiKey<String>((String[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("null"));
        }
    }

    // Covers MultiKey(K[], boolean) makeClone=true branch
    @Test
    public void testArrayBooleanConstructor_makeCloneTrue_clonesArray() throws Throwable {
        String[] arr = new String[] {"A", "B"};
        MultiKey<String> mk = new MultiKey<String>(arr, true);
        arr[0] = "Z";
        assertEquals("A", mk.getKey(0));
    }

    // Covers MultiKey(K[], boolean) makeClone=false branch: array reference shared
    @Test
    public void testArrayBooleanConstructor_makeCloneFalse_sharesArrayReference() throws Throwable {
        String[] arr = new String[] {"A", "B"};
        MultiKey<String> mk = new MultiKey<String>(arr, false);
        arr[0] = "Z";
        assertEquals("Z", mk.getKey(0));
    }

    // Covers MultiKey(K[], boolean) null check: throws IllegalArgumentException
    @Test
    public void testArrayBooleanConstructor_nullArray_throwsIllegalArgumentException() throws Throwable {
        try {
            new MultiKey<String>((String[]) null, false);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("null"));
        }
    }

    // Covers getKeys(): returns a clone independent of internal array
    @Test
    public void testGetKeys_returnsCloneIndependentOfInternalArray() throws Throwable {
        String[] original = new String[] {"A", "B"};
        MultiKey<String> mk = new MultiKey<String>(original);
        String[] result = mk.getKeys();
        result[0] = "Z";
        assertEquals("A", mk.getKey(0));
    }

    // Covers getKeys(): content matches original values exactly
    @Test
    public void testGetKeys_contentMatchesOriginalValues() throws Throwable {
        String[] original = new String[] {"A", "B", "C"};
        MultiKey<String> mk = new MultiKey<String>(original);
        String[] result = mk.getKeys();
        assertArrayEquals(original, result);
    }

    // Covers getKey(index): negative index throws IndexOutOfBoundsException
    @Test
    public void testGetKey_negativeIndex_throwsIndexOutOfBoundsException() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        try {
            mk.getKey(-1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // Covers getKey(index): index equal to size (boundary) throws IndexOutOfBoundsException
    @Test
    public void testGetKey_indexEqualsSize_throwsIndexOutOfBoundsException() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        try {
            mk.getKey(2);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // Covers getKey(index) on zero-length keys array: always out of bounds
    @Test
    public void testGetKey_emptyArray_throwsIndexOutOfBoundsException() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>(new String[0]);
        try {
            mk.getKey(0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // Covers size(): matches length of array passed to array constructor
    @Test
    public void testSize_arrayConstructorMatchesArrayLength() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>(new String[] {"A", "B", "C", "D", "E"});
        assertEquals(5, mk.size());
    }

    // Covers equals(): identity branch (other == this)
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        assertTrue(mk.equals(mk));
    }

    // Covers equals(): same values different instances, symmetric
    @Test
    public void testEquals_equalKeysDifferentInstances_returnsTrueAndSymmetric() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>("A", "B");
        MultiKey<String> mk2 = new MultiKey<String>("A", "B");
        assertTrue(mk1.equals(mk2));
        assertTrue(mk2.equals(mk1));
    }

    // Covers equals(): order of keys matters, different order returns false
    @Test
    public void testEquals_differentOrder_returnsFalse() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>("A", "B");
        MultiKey<String> mk2 = new MultiKey<String>("B", "A");
        assertFalse(mk1.equals(mk2));
    }

    // Covers equals(): different number of keys returns false
    @Test
    public void testEquals_differentNumberOfKeys_returnsFalse() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>("A", "B");
        MultiKey<String> mk2 = new MultiKey<String>("A", "B", "C");
        assertFalse(mk1.equals(mk2));
    }

    // Covers equals(): non-MultiKey object returns false (instanceof branch)
    @Test
    public void testEquals_nonMultiKeyObject_returnsFalse() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        assertFalse(mk.equals("A"));
    }

    // Covers equals(): null argument returns false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        assertFalse(mk.equals(null));
    }

    // Covers equals(): null keys inside array are compared correctly
    @Test
    public void testEquals_withNullKeys_returnsTrueWhenMatching() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>(null, "B");
        MultiKey<String> mk2 = new MultiKey<String>(null, "B");
        assertTrue(mk1.equals(mk2));
    }

    // Covers equals(): cross construction method (multi-arg vs array ctor) with same values
    @Test
    public void testEquals_crossConstructionMethod_returnsTrueWhenValuesMatch() throws Throwable {
        MultiKey<String> mkTwo = new MultiKey<String>("A", "B");
        MultiKey<String> mkArray = new MultiKey<String>(new String[] {"A", "B"});
        assertTrue(mkTwo.equals(mkArray));
    }

    // Covers equals(): both zero-length key arrays are equal
    @Test
    public void testEquals_bothEmptyArrays_returnsTrue() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>(new String[0]);
        MultiKey<String> mk2 = new MultiKey<String>(new String[0]);
        assertTrue(mk1.equals(mk2));
    }

    // Covers hashCode(): equal objects must produce equal hash codes (Object contract)
    @Test
    public void testHashCode_equalObjectsHaveEqualHashCode() throws Throwable {
        MultiKey<String> mk1 = new MultiKey<String>("A", "B");
        MultiKey<String> mk2 = new MultiKey<String>("A", "B");
        assertEquals(mk1.hashCode(), mk2.hashCode());
    }

    // Covers calculateHashCode(): documented XOR combination of key hash codes
    @Test
    public void testHashCode_matchesXorFormulaOfKeyHashCodes() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        int expected = "A".hashCode() ^ "B".hashCode();
        assertEquals(expected, mk.hashCode());
    }

    // Covers calculateHashCode(): null key branch contributes 0 to hash
    @Test
    public void testHashCode_withNullKey_ignoresNullContribution() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>(null, "B");
        assertEquals("B".hashCode(), mk.hashCode());
    }

    // Covers calculateHashCode(): all-null keys produce hash code 0
    @Test
    public void testHashCode_allNullKeys_isZero() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>(null, null);
        assertEquals(0, mk.hashCode());
    }

    // Covers calculateHashCode(): zero-length array, loop runs 0 times, hash is 0
    @Test
    public void testHashCode_emptyArray_isZero() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>(new String[0]);
        assertEquals(0, mk.hashCode());
    }

    // Covers hashCode(): cached value is consistent across repeated calls
    @Test
    public void testHashCode_isCachedConsistentAcrossMultipleCalls() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        int first = mk.hashCode();
        int second = mk.hashCode();
        assertEquals(first, second);
    }

    // Covers toString(): debugging string contains class name and key values
    @Test
    public void testToString_containsClassNameAndKeyValues() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        String s = mk.toString();
        assertTrue(s.contains("MultiKey"));
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
    }

    // Covers readResolve(): hash code recalculated correctly after deserialization
    @Test
    public void testSerialization_preservesEqualityAndHashCode() throws Throwable {
        MultiKey<String> mk = new MultiKey<String>("A", "B");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(mk);
        oos.close();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        MultiKey<?> result = (MultiKey<?>) ois.readObject();
        ois.close();
        assertTrue(mk.equals(result));
        assertEquals(mk.hashCode(), result.hashCode());
    }

    // Covers primary usage contract from class Javadoc: usable as HashMap key
    @Test
    public void testUsedAsHashMapKey_retrievesValueWithEquivalentKey() throws Throwable {
        Map<MultiKey<String>, String> map = new HashMap<MultiKey<String>, String>();
        MultiKey<String> key1 = new MultiKey<String>("X", "Y");
        map.put(key1, "value1");
        MultiKey<String> key2 = new MultiKey<String>("X", "Y");
        assertEquals("value1", map.get(key2));
    }
}
