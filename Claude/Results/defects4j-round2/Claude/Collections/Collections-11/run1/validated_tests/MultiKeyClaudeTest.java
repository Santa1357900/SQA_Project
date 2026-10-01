package org.apache.commons.collections.keyvalue;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;

public class MultiKeyClaudeTest {

    // Two-arg constructor: verify size and stored keys
    @Test
    public void testConstructorTwoKeys_validArgs_sizeAndKeysCorrect() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        assertEquals(2, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
    }

    // Three-arg constructor: verify size and stored keys
    @Test
    public void testConstructorThreeKeys_validArgs_sizeAndKeysCorrect() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C");
        assertEquals(3, mk.size());
        assertEquals("C", mk.getKey(2));
    }

    // Four-arg constructor: verify size and stored keys
    @Test
    public void testConstructorFourKeys_validArgs_sizeAndKeysCorrect() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C", "D");
        assertEquals(4, mk.size());
        assertEquals("D", mk.getKey(3));
    }

    // Five-arg constructor: verify size and stored keys
    @Test
    public void testConstructorFiveKeys_validArgs_sizeAndKeysCorrect() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C", "D", "E");
        assertEquals(5, mk.size());
        assertEquals("E", mk.getKey(4));
    }

    // Single-arg array constructor clones the array (makeClone defaults true)
    @Test
    public void testConstructorObjectArray_mutateOriginalAfterCreation_internalUnaffected() throws Throwable {
        Object[] arr = new Object[] {"A", "B"};
        MultiKey mk = new MultiKey(arr);
        arr[0] = "Z";
        assertEquals("A", mk.getKey(0));
    }

    // Single-arg array constructor: null array must throw
    @Test
    public void testConstructorObjectArray_nullArray_throwsIllegalArgumentException() throws Throwable {
        try {
            new MultiKey((Object[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Two-arg (array, boolean) constructor: null array must throw regardless of flag
    @Test
    public void testConstructorArrayBoolean_nullArray_throwsIllegalArgumentException() throws Throwable {
        try {
            new MultiKey((Object[]) null, false);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // makeClone = true branch: internal array must be independent copy
    @Test
    public void testConstructorArrayBooleanTrue_mutateOriginalAfterCreation_internalUnaffected() throws Throwable {
        Object[] arr = new Object[] {"A", "B"};
        MultiKey mk = new MultiKey(arr, true);
        arr[0] = "Z";
        assertEquals("A", mk.getKey(0));
    }

    // makeClone = false branch: internal array is the same reference, so mutation is visible
    @Test
    public void testConstructorArrayBooleanFalse_mutateOriginalAfterCreation_internalAffected() throws Throwable {
        Object[] arr = new Object[] {"A", "B"};
        MultiKey mk = new MultiKey(arr, false);
        arr[0] = "Z";
        assertEquals("Z", mk.getKey(0));
    }

    // getKeys returns a clone; mutating the returned array must not affect internal state
    @Test
    public void testGetKeys_mutateReturnedArray_internalUnaffected() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        Object[] returned = mk.getKeys();
        returned[0] = "Z";
        assertEquals("A", mk.getKey(0));
    }

    // getKeys returns correct contents and correct length
    @Test
    public void testGetKeys_returnsAllKeysInOrder() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C");
        Object[] returned = mk.getKeys();
        assertEquals(3, returned.length);
        assertEquals("A", returned[0]);
        assertEquals("B", returned[1]);
        assertEquals("C", returned[2]);
    }

    // getKey with valid index returns the correct key
    @Test
    public void testGetKey_validIndex_returnsCorrectKey() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C");
        assertEquals("B", mk.getKey(1));
    }

    // getKey with negative index must throw IndexOutOfBoundsException
    @Test
    public void testGetKey_negativeIndex_throwsIndexOutOfBoundsException() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        try {
            mk.getKey(-1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // getKey with index == size (boundary) must throw IndexOutOfBoundsException
    @Test
    public void testGetKey_indexEqualsSize_throwsIndexOutOfBoundsException() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        try {
            mk.getKey(2);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // size() returns number of keys for a 2-key instance
    @Test
    public void testSize_twoKeys_returnsTwo() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        assertEquals(2, mk.size());
    }

    // size() returns number of keys for a 5-key instance
    @Test
    public void testSize_fiveKeys_returnsFive() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C", "D", "E");
        assertEquals(5, mk.size());
    }

    // equals: same instance reference must be equal
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        assertTrue(mk.equals(mk));
    }

    // equals: two MultiKeys with equal key arrays are equal
    @Test
    public void testEquals_equalKeys_returnsTrue() throws Throwable {
        MultiKey mk1 = new MultiKey("A", "B");
        MultiKey mk2 = new MultiKey("A", "B");
        assertTrue(mk1.equals(mk2));
    }

    // equals: differing key content returns false
    @Test
    public void testEquals_differentKeyContent_returnsFalse() throws Throwable {
        MultiKey mk1 = new MultiKey("A", "B");
        MultiKey mk2 = new MultiKey("A", "C");
        assertFalse(mk1.equals(mk2));
    }

    // equals: differing number of keys returns false
    @Test
    public void testEquals_differentLength_returnsFalse() throws Throwable {
        MultiKey mk1 = new MultiKey("A", "B");
        MultiKey mk2 = new MultiKey("A", "B", "C");
        assertFalse(mk1.equals(mk2));
    }

    // equals: null argument returns false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        assertFalse(mk.equals(null));
    }

    // equals: argument of unrelated type returns false
    @Test
    public void testEquals_differentType_returnsFalse() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        assertFalse(mk.equals("A"));
    }

    // equals: null elements within the key arrays are handled correctly (Arrays.equals semantics)
    @Test
    public void testEquals_withNullKeyElements_returnsTrue() throws Throwable {
        MultiKey mk1 = new MultiKey(null, "B");
        MultiKey mk2 = new MultiKey(null, "B");
        assertTrue(mk1.equals(mk2));
    }

    // hashCode: combines individual key hash codes via XOR per documented algorithm
    @Test
    public void testHashCode_combinesKeyHashCodesViaXor() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        int expected = "A".hashCode() ^ "B".hashCode();
        assertEquals(expected, mk.hashCode());
    }

    // hashCode: null key element is skipped in the XOR combination
    @Test
    public void testHashCode_withNullKeyElement_skipsNull() throws Throwable {
        MultiKey mk = new MultiKey(null, "B");
        int expected = "B".hashCode();
        assertEquals(expected, mk.hashCode());
    }

    // hashCode: value is cached and stable across repeated calls
    @Test
    public void testHashCode_calledTwice_returnsSameValue() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        int first = mk.hashCode();
        int second = mk.hashCode();
        assertEquals(first, second);
    }

    // hashCode contract: equal objects must produce equal hash codes
    @Test
    public void testHashCode_equalObjects_haveSameHashCode() throws Throwable {
        MultiKey mk1 = new MultiKey("A", "B");
        MultiKey mk2 = new MultiKey("A", "B");
        assertTrue(mk1.equals(mk2));
        assertEquals(mk1.hashCode(), mk2.hashCode());
    }

    // toString: formats keys using Arrays.asList(...).toString() prefixed with class name
    @Test
    public void testToString_withTwoKeys_formatsAsExpected() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        assertEquals("MultiKey[A, B]", mk.toString());
    }

    // toString: includes literal "null" for null key elements
    @Test
    public void testToString_withNullKey_includesNullLiteral() throws Throwable {
        MultiKey mk = new MultiKey(null, "B");
        assertEquals("MultiKey[null, B]", mk.toString());
    }

    // Serialization: per Javadoc, hash code must be recalculated after deserialization
    @Test
    public void testSerialization_hashCodeRecalculatedAfterDeserialization() throws Throwable {
        MultiKey original = new MultiKey("A", "B");
        int originalHash = original.hashCode();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        MultiKey deserialized = (MultiKey) ois.readObject();
        ois.close();

        assertEquals(originalHash, deserialized.hashCode());
    }

    // Serialization: equals contract must hold between original and deserialized instance
    @Test
    public void testSerialization_equalsHoldsAfterDeserialization() throws Throwable {
        MultiKey original = new MultiKey("A", "B", "C");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        MultiKey deserialized = (MultiKey) ois.readObject();
        ois.close();

        assertTrue(original.equals(deserialized));
        assertTrue(deserialized.equals(original));
    }

    // Serialization: deserialized instance preserves size and key values
    @Test
    public void testSerialization_sizeAndKeysPreserved() throws Throwable {
        MultiKey original = new MultiKey("A", "B", "C");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        MultiKey deserialized = (MultiKey) ois.readObject();
        ois.close();

        assertEquals(3, deserialized.size());
        assertEquals("A", deserialized.getKey(0));
        assertEquals("C", deserialized.getKey(2));
    }
}
