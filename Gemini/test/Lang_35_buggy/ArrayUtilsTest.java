package org.apache.commons.lang3;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.AbstractMap;
import java.util.Map;

import org.junit.Test;

public class ArrayUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        ArrayUtils utils = new ArrayUtils();
        assertNotNull(utils);
    }

    @Test
    public void testToStringObject() throws Throwable {
        assertEquals("{}", ArrayUtils.toString(null));
        String[] arr = new String[] { "a", "b" };
        String res = ArrayUtils.toString(arr);
        assertNotNull(res);
    }

    @Test
    public void testToStringObjectWithNullDefault() throws Throwable {
        assertEquals("default", ArrayUtils.toString(null, "default"));
        String[] arr = new String[] { "a" };
        String res = ArrayUtils.toString(arr, "default");
        assertNotNull(res);
    }

    @Test
    public void testIsEquals() throws Throwable {
        int[] arr1 = new int[] { 1, 2 };
        int[] arr2 = new int[] { 1, 2 };
        int[] arr3 = new int[] { 1, 3 };
        assertTrue(ArrayUtils.isEquals(arr1, arr2));
        assertFalse(ArrayUtils.isEquals(arr1, arr3));
    }

    @Test
    public void testToMap() throws Throwable {
        assertNull(ArrayUtils.toMap(null));

        Object[] validArray = new Object[] {
            new Object[] { "key1", "val1" },
            new AbstractMap.SimpleEntry<String, String>("key2", "val2")
        };
        Map<Object, Object> map = ArrayUtils.toMap(validArray);
        assertNotNull(map);
        assertEquals("val1", map.get("key1"));
        assertEquals("val2", map.get("key2"));

        try {
            ArrayUtils.toMap(new Object[] { new Object[] { "key1" } });
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("has a length less than 2"));
        }

        try {
            ArrayUtils.toMap(new Object[] { "notAnEntryOrArray" });
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is neither of type Map.Entry nor an Array"));
        }
    }

    @Test
    public void testToArray() throws Throwable {
        String[] res = ArrayUtils.toArray("a", "b");
        assertArrayEquals(new String[] { "a", "b" }, res);

        String[] emptyRes = ArrayUtils.toArray();
        assertNotNull(emptyRes);
        assertEquals(0, emptyRes.length);
    }

    @Test
    public void testCloneArrays() throws Throwable {
        assertNull(ArrayUtils.clone((String[]) null));
        assertNull(ArrayUtils.clone((long[]) null));
        assertNull(ArrayUtils.clone((int[]) null));
        assertNull(ArrayUtils.clone((short[]) null));
        assertNull(ArrayUtils.clone((char[]) null));
        assertNull(ArrayUtils.clone((byte[]) null));
        assertNull(ArrayUtils.clone((double[]) null));
        assertNull(ArrayUtils.clone((float[]) null));
        assertNull(ArrayUtils.clone((boolean[]) null));

        assertArrayEquals(new String[] { "a" }, ArrayUtils.clone(new String[] { "a" }));
        assertArrayEquals(new long[] { 1L }, ArrayUtils.clone(new long[] { 1L }), 0L);
        assertArrayEquals(new int[] { 1 }, ArrayUtils.clone(new int[] { 1 }));
        assertArrayEquals(new short[] { 1 }, ArrayUtils.clone(new short[] { 1 }));
        assertArrayEquals(new char[] { 'a' }, ArrayUtils.clone(new char[] { 'a' }));
        assertArrayEquals(new byte[] { 1 }, ArrayUtils.clone(new byte[] { 1 }));
        assertArrayEquals(new double[] { 1.0 }, ArrayUtils.clone(new double[] { 1.0 }), 0.0);
        assertArrayEquals(new float[] { 1.0f }, ArrayUtils.clone(new float[] { 1.0f }), 0.0f);
        assertTrue(ArrayUtils.clone(new boolean[] { true })[0]);
    }

    @Test
    public void testSubarrayObject() throws Throwable {
        assertNull(ArrayUtils.subarray((String[]) null, 0, 1));
        String[] arr = new String[] { "a", "b", "c" };
        assertArrayEquals(new String[] { "a", "b" }, ArrayUtils.subarray(arr, -1, 2));
        assertArrayEquals(new String[] { "b", "c" }, ArrayUtils.subarray(arr, 1, 5));
        assertArrayEquals(new String[0], ArrayUtils.subarray(arr, 2, 1));
        assertArrayEquals(new String[0], ArrayUtils.subarray(arr, 3, 4));
    }

    @Test
    public void testSubarrayPrimitives() throws Throwable {
        assertNull(ArrayUtils.subarray((long[]) null, 0, 1));
        assertNull(ArrayUtils.subarray((int[]) null, 0, 1));
        assertNull(ArrayUtils.subarray((short[]) null, 0, 1));
        assertNull(ArrayUtils.subarray((char[]) null, 0, 1));
        assertNull(ArrayUtils.subarray((byte[]) null, 0, 1));
        assertNull(ArrayUtils.subarray((double[]) null, 0, 1));
        assertNull(ArrayUtils.subarray((float[]) null, 0, 1));
        assertNull(ArrayUtils.subarray((boolean[]) null, 0, 1));

        assertArrayEquals(new long[] { 2L }, ArrayUtils.subarray(new long[] { 1L, 2L, 3L }, 1, 2), 0L);
        assertArrayEquals(new int[] { 2 }, ArrayUtils.subarray(new int[] { 1, 2, 3 }, 1, 2));
        assertArrayEquals(new short[] { 2 }, ArrayUtils.subarray(new short[] { 1, 2, 3 }, 1, 2));
        assertArrayEquals(new char[] { 'b' }, ArrayUtils.subarray(new char[] { 'a', 'b', 'c' }, 1, 2));
        assertArrayEquals(new byte[] { 2 }, ArrayUtils.subarray(new byte[] { 1, 2, 3 }, 1, 2));
        assertArrayEquals(new double[] { 2.0 }, ArrayUtils.subarray(new double[] { 1.0, 2.0, 3.0 }, 1, 2), 0.0);
        assertArrayEquals(new float[] { 2.0f }, ArrayUtils.subarray(new float[] { 1.0f, 2.0f, 3.0f }, 1, 2), 0.0f);
        assertTrue(ArrayUtils.subarray(new boolean[] { true, false, true }, 1, 2)[0] == false);

        // test newSize <= 0 branch for primitives
        assertArrayEquals(EMPTY_LONG_ARRAY, ArrayUtils.subarray(new long[] { 1L }, 1, 0), 0L);
        assertArrayEquals(EMPTY_INT_ARRAY, ArrayUtils.subarray(new int[] { 1 }, 1, 0));
        assertArrayEquals(EMPTY_SHORT_ARRAY, ArrayUtils.subarray(new short[] { 1 }, 1, 0));
        assertArrayEquals(EMPTY_CHAR_ARRAY, ArrayUtils.subarray(new char[] { 'a' }, 1, 0));
        assertArrayEquals(EMPTY_BYTE_ARRAY, ArrayUtils.subarray(new byte[] { 1 }, 1, 0));
        assertArrayEquals(EMPTY_DOUBLE_ARRAY, ArrayUtils.subarray(new double[] { 1.0 }, 1, 0), 0.0);
        assertArrayEquals(EMPTY_FLOAT_ARRAY, ArrayUtils.subarray(new float[] { 1.0f }, 1, 0), 0.0f);
        assertArrayEquals(new boolean[0], ArrayUtils.subarray(new boolean[] { true }, 1, 0));
    }

    @Test
    public void testIsSameLength() throws Throwable {
        assertTrue(ArrayUtils.isSameLength((Object[]) null, (Object[]) null));
        assertTrue(ArrayUtils.isSameLength((Object[]) null, new String[0]));
        assertTrue(ArrayUtils.isSameLength(new String[0], (Object[]) null));
        assertFalse(ArrayUtils.isSameLength((Object[]) null, new String[] { "a" }));
        assertFalse(ArrayUtils.isSameLength(new String[] { "a" }, (Object[]) null));
        assertFalse(ArrayUtils.isSameLength(new String[] { "a" }, new String[] { "a", "b" }));
        assertTrue(ArrayUtils.isSameLength(new String[] { "a" }, new String[] { "b" }));

        // Primitives
        assertTrue(ArrayUtils.isSameLength((long[]) null, (long[]) null));
        assertFalse(ArrayUtils.isSameLength((long[]) null, new long[] { 1L }));
        assertFalse(ArrayUtils.isSameLength(new long[] { 1L }, new long[] { 1L, 2L }));
        assertTrue(ArrayUtils.isSameLength(new long[] { 1L }, new long[] { 2L }));

        assertTrue(ArrayUtils.isSameLength((int[]) null, (int[]) null));
        assertFalse(ArrayUtils.isSameLength((int[]) null, new int[] { 1 }));
        assertFalse(ArrayUtils.isSameLength(new int[] { 1 }, new int[] { 1, 2 }));
        assertTrue(ArrayUtils.isSameLength(new int[] { 1 }, new int[] { 2 }));

        assertTrue(ArrayUtils.isSameLength((short[]) null, (short[]) null));
        assertFalse(ArrayUtils.isSameLength((short[]) null, new short[] { 1 }));
        assertFalse(ArrayUtils.isSameLength(new short[] { 1 }, new short[] { 1, 2 }));
        assertTrue(ArrayUtils.isSameLength(new short[] { 1 }, new short[] { 2 }));

        assertTrue(ArrayUtils.isSameLength((char[]) null, (char[]) null));
        assertFalse(ArrayUtils.isSameLength((char[]) null, new char[] { 'a' }));
        assertFalse(ArrayUtils.isSameLength(new char[] { 'a' }, new char[] { 'a', 'b' }));
        assertTrue(ArrayUtils.isSameLength(new char[] { 'a' }, new char[] { 'b' }));

        assertTrue(ArrayUtils.isSameLength((byte[]) null, (byte[]) null));
        assertFalse(ArrayUtils.isSameLength((byte[]) null, new byte[] { 1 }));
        assertFalse(ArrayUtils.isSameLength(new byte[] { 1 }, new byte[] { 1, 2 }));
        assertTrue(ArrayUtils.isSameLength(new byte[] { 1 }, new byte[] { 2 }));

        assertTrue(ArrayUtils.isSameLength((double[]) null, (double[]) null));
        assertFalse(ArrayUtils.isSameLength((double[]) null, new double[] { 1.0 }));
        assertFalse(ArrayUtils.isSameLength(new double[] { 1.0 }, new double[] { 1.0, 2.0 }));
        assertTrue(ArrayUtils.isSameLength(new double[] { 1.0 }, new double[] { 2.0 }));

        assertTrue(ArrayUtils.isSameLength((float[]) null, (float[]) null));
        assertFalse(ArrayUtils.isSameLength((float[]) null, new float[] { 1.0f }));
        assertFalse(ArrayUtils.isSameLength(new float[] { 1.0f }, new float[] { 1.0f, 2.0f }));
        assertTrue(ArrayUtils.isSameLength(new float[] { 1.0f }, new float[] { 2.0f }));

        assertTrue(ArrayUtils.isSameLength((boolean[]) null, (boolean[]) null));
        assertFalse(ArrayUtils.isSameLength((boolean[]) null, new boolean[] { true }));
        assertFalse(ArrayUtils.isSameLength(new boolean[] { true }, new boolean[] { true, false }));
        assertTrue(ArrayUtils.isSameLength(new boolean[] { true }, new boolean[] { false }));
    }

    @Test
    public void testGetLength() throws Throwable {
        assertEquals(0, ArrayUtils.getLength(null));
        assertEquals(0, ArrayUtils.getLength(new int[0]));
        assertEquals(1, ArrayUtils.getLength(new Object[] { null }));
        assertEquals(2, ArrayUtils.getLength(new boolean[] { true, false }));

        try {
            ArrayUtils.getLength("notAnArray");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testIsSameType() throws Throwable {
        assertTrue(ArrayUtils.isSameType(new String[] { "a" }, new String[] { "b" }));
        assertFalse(ArrayUtils.isSameType(new String[] { "a" }, new Integer[] { 1 }));

        try {
            ArrayUtils.isSameType(null, new String[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("The Array must not be null"));
        }

        try {
            ArrayUtils.isSameType(new String[0], null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("The Array must not be null"));
        }
    }

    @Test
    public void testReverse() throws Throwable {
        ArrayUtils.reverse((Object[]) null);
        String[] objArr = new String[] { "a", "b", "c" };
        ArrayUtils.reverse(objArr);
        assertArrayEquals(new String[] { "c", "b", "a" }, objArr);

        ArrayUtils.reverse((long[]) null);
        long[] lArr = new long[] { 1L, 2L };
        ArrayUtils.reverse(lArr);
        assertArrayEquals(new long[] { 2L, 1L }, lArr, 0L);

        ArrayUtils.reverse((int[]) null);
        int[] iArr = new int[] { 1, 2 };
        ArrayUtils.reverse(iArr);
        assertArrayEquals(new int[] { 2, 1 }, iArr);

        ArrayUtils.reverse((short[]) null);
        short[] sArr = new short[] { 1, 2 };
        ArrayUtils.reverse(sArr);
        assertArrayEquals(new short[] { 2, 1 }, sArr);

        ArrayUtils.reverse((char[]) null);
        char[] cArr = new char[] { 'a', 'b' };
        ArrayUtils.reverse(cArr);
        assertArrayEquals(new char[] { 'b', 'a' }, cArr);

        ArrayUtils.reverse((byte[]) null);
        byte[] bArr = new byte[] { 1, 2 };
        ArrayUtils.reverse(bArr);
        assertArrayEquals(new byte[] { 2, 1 }, bArr);

        ArrayUtils.reverse((double[]) null);
        double[] dArr = new double[] { 1.0, 2.0 };
        ArrayUtils.reverse(dArr);
        assertArrayEquals(new double[] { 2.0, 1.0 }, dArr, 0.0);

        ArrayUtils.reverse((float[]) null);
        float[] fArr = new float[] { 1.0f, 2.0f };
        ArrayUtils.reverse(fArr);
        assertArrayEquals(new float[] { 2.0f, 1.0f }, fArr, 0.0f);

        ArrayUtils.reverse((boolean[]) null);
        boolean[] boolArr = new boolean[] { true, false };
        ArrayUtils.reverse(boolArr);
        assertTrue(boolArr[0] == false && boolArr[1] == true);
    }

    @Test
    public void testIndexOfObject() throws Throwable {
        String[] arr = new String[] { "a", null, "b", "a" };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((Object[]) null, "a"));
        assertEquals(0, ArrayUtils.indexOf(arr, "a"));
        assertEquals(0, ArrayUtils.indexOf(arr, "a", -5));
        assertEquals(3, ArrayUtils.indexOf(arr, "a", 1));
        assertEquals(1, ArrayUtils.indexOf(arr, null));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(arr, "notPresent"));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(arr, "a", 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(new String[0], Integer.valueOf(1))); // component type check
    }

    @Test
    public void testLastIndexOfObject() throws Throwable {
        String[] arr = new String[] { "a", null, "b", "a" };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((Object[]) null, "a"));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(arr, "a", -1));
        assertEquals(3, ArrayUtils.lastIndexOf(arr, "a", 10));
        assertEquals(3, ArrayUtils.lastIndexOf(arr, "a"));
        assertEquals(1, ArrayUtils.lastIndexOf(arr, null));
        assertEquals(ArrayUtils.lastIndexOf(arr, null, 0), ArrayUtils.INDEX_NOT_FOUND); // based on index
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(arr, "notPresent"));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(new String[0], Integer.valueOf(1)));
    }

    @Test
    public void testContainsObject() throws Throwable {
        assertFalse(ArrayUtils.contains((Object[]) null, "a"));
        assertTrue(ArrayUtils.contains(new String[] { "a" }, "a"));
        assertFalse(ArrayUtils.contains(new String[] { "a" }, "b"));
    }

    @Test
    public void testPrimitiveIndexOfAndLastAndContains() throws Throwable {
        // Long
        long[] lArr = new long[] { 1L, 2L, 1L };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((long[]) null, 1L));
        assertEquals(0, ArrayUtils.indexOf(lArr, 1L, -2));
        assertEquals(2, ArrayUtils.indexOf(lArr, 1L, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(lArr, 9L));
        assertEquals(2, ArrayUtils.lastIndexOf(lArr, 1L));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((long[]) null, 1L));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(lArr, 1L, -1));
        assertEquals(0, ArrayUtils.lastIndexOf(lArr, 1L, 0));
        assertEquals(2, ArrayUtils.lastIndexOf(lArr, 1L, 10));
        assertTrue(ArrayUtils.contains(lArr, 2L));
        assertFalse(ArrayUtils.contains((long[]) null, 2L));

        // Int
        int[] iArr = new int[] { 1, 2, 1 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((int[]) null, 1));
        assertEquals(0, ArrayUtils.indexOf(iArr, 1, -1));
        assertEquals(2, ArrayUtils.indexOf(iArr, 1, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(iArr, 9));
        assertEquals(2, ArrayUtils.lastIndexOf(iArr, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((int[]) null, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(iArr, 1, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(iArr, 1, 10));
        assertTrue(ArrayUtils.contains(iArr, 2));
        assertFalse(ArrayUtils.contains((int[]) null, 2));

        // Short
        short[] sArr = new short[] { 1, 2, 1 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((short[]) null, (short) 1));
        assertEquals(0, ArrayUtils.indexOf(sArr, (short) 1, -1));
        assertEquals(2, ArrayUtils.indexOf(sArr, (short) 1, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(sArr, (short) 9));
        assertEquals(2, ArrayUtils.lastIndexOf(sArr, (short) 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((short[]) null, (short) 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(sArr, (short) 1, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(sArr, (short) 1, 10));
        assertTrue(ArrayUtils.contains(sArr, (short) 2));
        assertFalse(ArrayUtils.contains((short[]) null, (short) 2));

        // Char
        char[] cArr = new char[] { 'a', 'b', 'a' };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((char[]) null, 'a'));
        assertEquals(0, ArrayUtils.indexOf(cArr, 'a', -1));
        assertEquals(2, ArrayUtils.indexOf(cArr, 'a', 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(cArr, 'z'));
        assertEquals(2, ArrayUtils.lastIndexOf(cArr, 'a'));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((char[]) null, 'a'));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(cArr, 'a', -1));
        assertEquals(2, ArrayUtils.lastIndexOf(cArr, 'a', 10));
        assertTrue(ArrayUtils.contains(cArr, 'b'));
        assertFalse(ArrayUtils.contains((char[]) null, 'b'));

        // Byte
        byte[] bArr = new byte[] { 1, 2, 1 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((byte[]) null, (byte) 1));
        assertEquals(0, ArrayUtils.indexOf(bArr, (byte) 1, -1));
        assertEquals(2, ArrayUtils.indexOf(bArr, (byte) 1, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(bArr, (byte) 9));
        assertEquals(2, ArrayUtils.lastIndexOf(bArr, (byte) 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((byte[]) null, (byte) 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(bArr, (byte) 1, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(bArr, (byte) 1, 10));
        assertTrue(ArrayUtils.contains(bArr, (byte) 2));
        assertFalse(ArrayUtils.contains((byte[]) null, (byte) 2));
    }

    @Test
    public void testDoubleIndexOfAndLastAndContains() throws Throwable {
        double[] dArr = new double[] { 1.0, 2.0, 1.0 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((double[]) null, 1.0));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(new double[0], 1.0));
        assertEquals(0, ArrayUtils.indexOf(dArr, 1.0, -1));
        assertEquals(0, ArrayUtils.indexOf(dArr, 1.0, 0, 0.1));
        assertEquals(2, ArrayUtils.indexOf(dArr, 1.0, 1));
        assertEquals(1, ArrayUtils.indexOf(dArr, 2.1, 0, 0.2));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(dArr, 9.0));

        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((double[]) null, 1.0));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(new double[0], 1.0));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 1.0));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 1.0, 0.1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(dArr, 1.0, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 1.0, 10));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 1.0, 10, 0.1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(dArr, 9.0, 10));

        assertTrue(ArrayUtils.contains(dArr, 2.0));
        assertTrue(ArrayUtils.contains(dArr, 2.1, 0.2));
        assertFalse(ArrayUtils.contains((double[]) null, 2.0));
        assertFalse(ArrayUtils.contains((double[]) null, 2.0, 0.1));
    }

    @Test
    public void testFloatIndexOfAndLastAndContains() throws Throwable {
        float[] fArr = new float[] { 1.0f, 2.0f, 1.0f };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((float[]) null, 1.0f));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(new float[0], 1.0f));
        assertEquals(0, ArrayUtils.indexOf(fArr, 1.0f, -1));
        assertEquals(2, ArrayUtils.indexOf(fArr, 1.0f, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(fArr, 9.0f));

        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((float[]) null, 1.0f));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(new float[0], 1.0f));
        assertEquals(2, ArrayUtils.lastIndexOf(fArr, 1.0f));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(fArr, 1.0f, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(fArr, 1.0f, 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(fArr, 9.0f, 10));

        assertTrue(ArrayUtils.contains(fArr, 2.0f));
        assertFalse(ArrayUtils.contains((float[]) null, 2.0f));
    }

    @Test
    public void testBooleanIndexOfAndLastAndContains() throws Throwable {
        boolean[] bArr = new boolean[] { true, false, true };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((boolean[]) null, true));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(new boolean[0], true));
        assertEquals(0, ArrayUtils.indexOf(bArr, true, -1));
        assertEquals(2, ArrayUtils.indexOf(bArr, true, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(bArr, (boolean) true, 10)); // out of bounds or not found if false logic etc, actually loop just ends

        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((boolean[]) null, true));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(new boolean[0], true));
        assertEquals(2, ArrayUtils.lastIndexOf(bArr, true));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(bArr, true, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(bArr, true, 10));

        assertTrue(ArrayUtils.contains(bArr, false));
        assertFalse(ArrayUtils.contains((boolean[]) null, false));
    }

    @Test
    public void testConvertersCharacter() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Character[]) null));
        assertArrayEquals(EMPTY_CHAR_ARRAY, ArrayUtils.toPrimitive(new Character[0]));
        assertArrayEquals(new char[] { 'a' }, ArrayUtils.toPrimitive(new Character[] { Character.valueOf('a') }));

        assertNull(ArrayUtils.toPrimitive((Character[]) null, 'x'));
        assertArrayEquals(EMPTY_CHAR_ARRAY, ArrayUtils.toPrimitive(new Character[0], 'x'));
        assertArrayEquals(new char[] { 'a', 'x' }, ArrayUtils.toPrimitive(new Character[] { Character.valueOf('a'), null }, 'x'));

        assertNull(ArrayUtils.toObject((char[]) null));
        assertArrayEquals(EMPTY_CHARACTER_OBJECT_ARRAY, ArrayUtils.toObject(new char[0]));
        assertArrayEquals(new Character[] { Character.valueOf('a') }, ArrayUtils.toObject(new char[] { 'a' }));
    }

    @Test
    public void testConvertersLong() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Long[]) null));
        assertArrayEquals(EMPTY_LONG_ARRAY, ArrayUtils.toPrimitive(new Long[0]), 0L);
        assertArrayEquals(new long[] { 1L }, ArrayUtils.toPrimitive(new Long[] { Long.valueOf(1L) }), 0L);

        assertNull(ArrayUtils.toPrimitive((Long[]) null, 0L));
        assertArrayEquals(EMPTY_LONG_ARRAY, ArrayUtils.toPrimitive(new Long[0], 0L), 0L);
        assertArrayEquals(new long[] { 1L, 0L }, ArrayUtils.toPrimitive(new Long[] { Long.valueOf(1L), null }, 0L), 0L);

        assertNull(ArrayUtils.toObject((long[]) null));
        assertArrayEquals(EMPTY_LONG_OBJECT_ARRAY, ArrayUtils.toObject(new long[0]));
        assertArrayEquals(new Long[] { Long.valueOf(1L) }, ArrayUtils.toObject(new long[] { 1L }));
    }

    @Test
    public void testConvertersInt() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Integer[]) null));
        assertArrayEquals(EMPTY_INT_ARRAY, ArrayUtils.toPrimitive(new Integer[0]));
        assertArrayEquals(new int[] { 1 }, ArrayUtils.toPrimitive(new Integer[] { Integer.valueOf(1) }));

        assertNull(ArrayUtils.toPrimitive((Integer[]) null, 0));
        assertArrayEquals(EMPTY_INT_ARRAY, ArrayUtils.toPrimitive(new Integer[0], 0));
        assertArrayEquals(new int[] { 1, 0 }, ArrayUtils.toPrimitive(new Integer[] { Integer.valueOf(1), null }, 0));

        assertNull(ArrayUtils.toObject((int[]) null));
        assertArrayEquals(EMPTY_INTEGER_OBJECT_ARRAY, ArrayUtils.toObject(new int[0]));
        assertArrayEquals(new Integer[] { Integer.valueOf(1) }, ArrayUtils.toObject(new int[] { 1 }));
    }

    @Test
    public void testConvertersShort() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Short[]) null));
        assertArrayEquals(EMPTY_SHORT_ARRAY, ArrayUtils.toPrimitive(new Short[0]));
        assertArrayEquals(new short[] { 1 }, ArrayUtils.toPrimitive(new Short[] { Short.valueOf((short) 1) }));

        assertNull(ArrayUtils.toPrimitive((Short[]) null, (short) 0));
        assertArrayEquals(EMPTY_SHORT_ARRAY, ArrayUtils.toPrimitive(new Short[0], (short) 0));
        assertArrayEquals(new short[] { 1, 0 }, ArrayUtils.toPrimitive(new Short[] { Short.valueOf((short) 1), null }, (short) 0));

        assertNull(ArrayUtils.toObject((short[]) null));
        assertArrayEquals(EMPTY_SHORT_OBJECT_ARRAY, ArrayUtils.toObject(new short[0]));
        assertArrayEquals(new Short[] { Short.valueOf((short) 1) }, ArrayUtils.toObject(new short[] { 1 }));
    }

    @Test
    public void testConvertersByte() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Byte[]) null));
        assertArrayEquals(EMPTY_BYTE_ARRAY, ArrayUtils.toPrimitive(new Byte[0]));
        assertArrayEquals(new byte[] { 1 }, ArrayUtils.toPrimitive(new Byte[] { Byte.valueOf((byte) 1) }));

        assertNull(ArrayUtils.toPrimitive((Byte[]) null, (byte) 0));
        assertArrayEquals(EMPTY_BYTE_ARRAY, ArrayUtils.toPrimitive(new Byte[0], (byte) 0));
        assertArrayEquals(new byte[] { 1, 0 }, ArrayUtils.toPrimitive(new Byte[] { Byte.valueOf((byte) 1), null }, (byte) 0));

        assertNull(ArrayUtils.toObject((byte[]) null));
        assertArrayEquals(EMPTY_BYTE_OBJECT_ARRAY, ArrayUtils.toObject(new byte[0]));
        assertArrayEquals(new Byte[] { Byte.valueOf((byte) 1) }, ArrayUtils.toObject(new byte[] { 1 }));
    }

    @Test
    public void testConvertersDouble() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Double[]) null));
        assertArrayEquals(EMPTY_DOUBLE_ARRAY, ArrayUtils.toPrimitive(new Double[0]), 0.0);
        assertArrayEquals(new double[] { 1.0 }, ArrayUtils.toPrimitive(new Double[] { Double.valueOf(1.0) }), 0.0);

        assertNull(ArrayUtils.toPrimitive((Double[]) null, 0.0));
        assertArrayEquals(EMPTY_DOUBLE_ARRAY, ArrayUtils.toPrimitive(new Double[0], 0.0), 0.0);
        assertArrayEquals(new double[] { 1.0, 0.0 }, ArrayUtils.toPrimitive(new Double[] { Double.valueOf(1.0), null }, 0.0), 0.0);

        assertNull(ArrayUtils.toObject((double[]) null));
        assertArrayEquals(EMPTY_DOUBLE_OBJECT_ARRAY, ArrayUtils.toObject(new double[0]));
        assertArrayEquals(new Double[] { Double.valueOf(1.0) }, ArrayUtils.toObject(new double[] { 1.0 }));
    }

    @Test
    public void testConvertersFloat() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Float[]) null));
        assertArrayEquals(EMPTY_FLOAT_ARRAY, ArrayUtils.toPrimitive(new Float[0]), 0.0f);
        assertArrayEquals(new float[] { 1.0f }, ArrayUtils.toPrimitive(new Float[] { Float.valueOf(1.0f) }), 0.0f);

        assertNull(ArrayUtils.toPrimitive((Float[]) null, 0.0f));
        assertArrayEquals(EMPTY_FLOAT_ARRAY, ArrayUtils.toPrimitive(new Float[0], 0.0f), 0.0f);
        assertArrayEquals(new float[] { 1.0f, 0.0f }, ArrayUtils.toPrimitive(new Float[] { Float.valueOf(1.0f), null }, 0.0f), 0.0f);

        assertNull(ArrayUtils.toObject((float[]) null));
        assertArrayEquals(EMPTY_FLOAT_OBJECT_ARRAY, ArrayUtils.toObject(new float[0]));
        assertArrayEquals(new Float[] { Float.valueOf(1.0f) }, ArrayUtils.toObject(new float[] { 1.0f }));
    }

    @Test
    public void testConvertersBoolean() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Boolean[]) null));
        assertArrayEquals(EMPTY_BOOLEAN_ARRAY, ArrayUtils.toPrimitive(new Boolean[0]));
        assertTrue(ArrayUtils.toPrimitive(new Boolean[] { Boolean.TRUE })[0]);

        assertNull(ArrayUtils.toPrimitive((Boolean[]) null, false));
        assertArrayEquals(EMPTY_BOOLEAN_ARRAY, ArrayUtils.toPrimitive(new Boolean[0], false));
        boolean[] pBool = ArrayUtils.toPrimitive(new Boolean[] { Boolean.TRUE, null }, false);
        assertTrue(pBool[0] && !pBool[1]);

        assertNull(ArrayUtils.toObject((boolean[]) null));
        assertArrayEquals(EMPTY_BOOLEAN_OBJECT_ARRAY, ArrayUtils.toObject(new boolean[0]));
        assertArrayEquals(new Boolean[] { Boolean.TRUE }, ArrayUtils.toObject(new boolean[] { true }));
    }

    @Test
    public void testIsEmpty() throws Throwable {
        assertTrue(ArrayUtils.isEmpty((String[]) null));
        assertTrue(ArrayUtils.isEmpty(new String[0]));
        assertFalse(ArrayUtils.isEmpty(new String[] { "a" }));

        assertTrue(ArrayUtils.isEmpty((long[]) null));
        assertTrue(ArrayUtils.isEmpty(new long[0]));
        assertFalse(ArrayUtils.isEmpty(new long[] { 1L }));

        assertTrue(ArrayUtils.isEmpty((int[]) null));
        assertTrue(ArrayUtils.isEmpty(new int[0]));
        assertFalse(ArrayUtils.isEmpty(new int[] { 1 }));

        assertTrue(ArrayUtils.isEmpty((short[]) null));
        assertTrue(ArrayUtils.isEmpty(new short[0]));
        assertFalse(ArrayUtils.isEmpty(new short[] { 1 }));

        assertTrue(ArrayUtils.isEmpty((char[]) null));
        assertTrue(ArrayUtils.isEmpty(new char[0]));
        assertFalse(ArrayUtils.isEmpty(new char[] { 'a' }));

        assertTrue(ArrayUtils.isEmpty((byte[]) null));
        assertTrue(ArrayUtils.isEmpty(new byte[0]));
        assertFalse(ArrayUtils.isEmpty(new byte[] { 1 }));

        assertTrue(ArrayUtils.isEmpty((double[]) null));
        assertTrue(ArrayUtils.isEmpty(new double[0]));
        assertFalse(ArrayUtils.isEmpty(new double[] { 1.0 }));

        assertTrue(ArrayUtils.isEmpty((float[]) null));
        assertTrue(ArrayUtils.isEmpty(new float[0]));
        assertFalse(ArrayUtils.isEmpty(new float[] { 1.0f }));

        assertTrue(ArrayUtils.isEmpty((boolean[]) null));
        assertTrue(ArrayUtils.isEmpty(new boolean[0]));
        assertFalse(ArrayUtils.isEmpty(new boolean[] { true }));
    }

    @Test
    public void testAddAll() throws Throwable {
        assertNull(ArrayUtils.addAll((String[]) null, (String[]) null));
        assertArrayEquals(new String[] { "a" }, ArrayUtils.addAll(new String[] { "a" }, (String[]) null));
        assertArrayEquals(new String[] { "b" }, ArrayUtils.addAll((String[]) null, new String[] { "b" }));
        assertArrayEquals(new String[] { "a", "b" }, ArrayUtils.addAll(new String[] { "a" }, new String[] { "b" }));

        try {
            ArrayUtils.addAll(new String[] { "a" }, new Integer[] { 1 });
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Cannot store"));
        }

        // Primitive addAll
        assertArrayEquals(new boolean[] { true }, ArrayUtils.addAll((boolean[]) null, true));
        assertArrayEquals(new boolean[] { true }, ArrayUtils.addAll(new boolean[] { true }, (boolean[]) null));
        assertArrayEquals(new boolean[] { true, false }, ArrayUtils.addAll(new boolean[] { true }, false));

        assertArrayEquals(new char[] { 'a' }, ArrayUtils.addAll((char[]) null, 'a'));
        assertArrayEquals(new char[] { 'a' }, ArrayUtils.addAll(new char[] { 'a' }, (char[]) null));
        assertArrayEquals(new char[] { 'a', 'b' }, ArrayUtils.addAll(new char[] { 'a' }, 'b'));

        assertArrayEquals(new byte[] { 1 }, ArrayUtils.addAll((byte[]) null, (byte) 1));
        assertArrayEquals(new byte[] { 1 }, ArrayUtils.addAll(new byte[] { 1 }, (byte[]) null));
        assertArrayEquals(new byte[] { 1, 2 }, ArrayUtils.addAll(new byte[] { 1 }, (byte) 2));

        assertArrayEquals(new short[] { 1 }, ArrayUtils.addAll((short[]) null, (short) 1));
        assertArrayEquals(new short[] { 1 }, ArrayUtils.addAll(new short[] { 1 }, (short[]) null));
        assertArrayEquals(new short[] { 1, 2 }, ArrayUtils.addAll(new short[] { 1 }, (short) 2));

        assertArrayEquals(new int[] { 1 }, ArrayUtils.addAll((int[]) null, 1));
        assertArrayEquals(new int[] { 1 }, ArrayUtils.addAll(new int[] { 1 }, (int[]) null));
        assertArrayEquals(new int[] { 1, 2 }, ArrayUtils.addAll(new int[] { 1 }, 2));

        assertArrayEquals(new long[] { 1L }, ArrayUtils.addAll((long[]) null, 1L), 0L);
        assertArrayEquals(new long[] { 1L }, ArrayUtils.addAll(new long[] { 1L }, (long[]) null), 0L);
        assertArrayEquals(new long[] { 1L, 2L }, ArrayUtils.addAll(new long[] { 1L }, 2L), 0L);

        assertArrayEquals(new float[] { 1.0f }, ArrayUtils.addAll((float[]) null, 1.0f), 0.0f);
        assertArrayEquals(new float[] { 1.0f }, ArrayUtils.addAll(new float[] { 1.0f }, (float[]) null), 0.0f);
        assertArrayEquals(new float[] { 1.0f, 2.0f }, ArrayUtils.addAll(new float[] { 1.0f }, 2.0f), 0.0f);

        assertArrayEquals(new double[] { 1.0 }, ArrayUtils.addAll((double[]) null, 1.0), 0.0);
        assertArrayEquals(new double[] { 1.0 }, ArrayUtils.addAll(new double[] { 1.0 }, (double[]) null), 0.0);
        assertArrayEquals(new double[] { 1.0, 2.0 }, ArrayUtils.addAll(new double[] { 1.0 }, 2.0), 0.0);
    }

    @Test
    public void testAddElement() throws Throwable {
        assertArrayEquals(new String[] { "a" }, ArrayUtils.add(null, "a"));
        assertArrayEquals(new Object[] { null }, ArrayUtils.add((String[]) null, (String) null));
        assertArrayEquals(new String[] { "a", "b" }, ArrayUtils.add(new String[] { "a" }, "b"));

        assertArrayEquals(new boolean[] { true }, ArrayUtils.add(null, true));
        assertArrayEquals(new boolean[] { true, false }, ArrayUtils.add(new boolean[] { true }, false));

        assertArrayEquals(new byte[] { 1 }, ArrayUtils.add(null, (byte) 1));
        assertArrayEquals(new byte[] { 1, 2 }, ArrayUtils.add(new byte[] { 1 }, (byte) 2));

        assertArrayEquals(new char[] { 'a' }, ArrayUtils.add(null, 'a'));
        assertArrayEquals(new char[] { 'a', 'b' }, ArrayUtils.add(new char[] { 'a' }, 'b'));

        assertArrayEquals(new double[] { 1.0 }, ArrayUtils.add(null, 1.0), 0.0);
        assertArrayEquals(new double[] { 1.0, 2.0 }, ArrayUtils.add(new double[] { 1.0 }, 2.0), 0.0);

        assertArrayEquals(new float[] { 1.0f }, ArrayUtils.add(null, 1.0f), 0.0f);
        assertArrayEquals(new float[] { 1.0f, 2.0f }, ArrayUtils.add(new float[] { 1.0f }, 2.0f), 0.0f);

        assertArrayEquals(new int[] { 1 }, ArrayUtils.add(null, 1));
        assertArrayEquals(new int[] { 1, 2 }, ArrayUtils.add(new int[] { 1 }, 2));

        assertArrayEquals(new long[] { 1L }, ArrayUtils.add(null, 1L), 0L);
        assertArrayEquals(new long[] { 1L, 2L }, ArrayUtils.add(new long[] { 1L }, 2L), 0L);

        assertArrayEquals(new short[] { 1 }, ArrayUtils.add(null, (short) 1));
        assertArrayEquals(new short[] { 1, 2 }, ArrayUtils.add(new short[] { 1 }, (short) 2));
    }

    @Test
    public void testAddAtIndex() throws Throwable {
        assertArrayEquals(new String[] { "a" }, ArrayUtils.add(null, 0, "a"));
        assertArrayEquals(new Object[] { null }, ArrayUtils.add((String[]) null, 0, (String) null));
        assertArrayEquals(new String[] { "b", "a" }, ArrayUtils.add(new String[] { "a" }, 0, "b"));

        try {
            ArrayUtils.add((String[]) null, 1, "a");
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertNotNull(e);
        }

        try {
            ArrayUtils.add(new String[] { "a" }, 2, "b");
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertNotNull(e);
        }

        // Primitive add at index
        assertArrayEquals(new boolean[] { true }, ArrayUtils.add(null, 0, true));
        assertArrayEquals(new boolean[] { false, true }, ArrayUtils.add(new boolean[] { true }, 0, false));

        assertArrayEquals(new char[] { 'a' }, ArrayUtils.add(null, 0, 'a'));
        assertArrayEquals(new char[] { 'b', 'a' }, ArrayUtils.add(new char[] { 'a' }, 0, 'b'));

        assertArrayEquals(new byte[] { 1 }, ArrayUtils.add(null, 0, (byte) 1));
        assertArrayEquals(new byte[] { 2, 1 }, ArrayUtils.add(new byte[] { 1 }, 0, (byte) 2));

        assertArrayEquals(new short[] { 1 }, ArrayUtils.add(null, 0, (short) 1));
        assertArrayEquals(new short[] { 2, 1 }, ArrayUtils.add(new short[] { 1 }, 0, (short) 2));

        assertArrayEquals(new int[] { 1 }, ArrayUtils.add(null, 0, 1));
        assertArrayEquals(new int[] { 2, 1 }, ArrayUtils.add(new int[] { 1 }, 0, 2));

        assertArrayEquals(new long[] { 1L }, ArrayUtils.add(null, 0, 1L), 0L);
        assertArrayEquals(new long[] { 2L, 1L }, ArrayUtils.add(new long[] { 1L }, 0, 2L), 0L);

        assertArrayEquals(new float[] { 1.0f }, ArrayUtils.add(null, 0, 1.0f), 0.0f);
        assertArrayEquals(new float[] { 2.0f, 1.0f }, ArrayUtils.add(new float[] { 1.0f }, 0, 2.0f), 0.0f);

        assertArrayEquals(new double[] { 1.0 }, ArrayUtils.add(null, 0, 1.0), 0.0);
        assertArrayEquals(new double[] { 2.0, 1.0 }, ArrayUtils.add(new double[] { 1.0 }, 0, 2.0), 0.0);
    }

    @Test
    public void testRemove() throws Throwable {
        String[] arr = new String[] { "a", "b", "c" };
        assertArrayEquals(new String[] { "b", "c" }, ArrayUtils.remove(arr, 0));
        assertArrayEquals(new String[] { "a", "c" }, ArrayUtils.remove(arr, 1));
        assertArrayEquals(new String[] { "a", "b" }, ArrayUtils.remove(arr, 2));

        try {
            ArrayUtils.remove(arr, -1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertNotNull(e);
        }

        try {
            ArrayUtils.remove(arr, 3);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertNotNull(e);
        }

        // removeElement Object
        assertNull(ArrayUtils.removeElement((String[]) null, "a"));
        assertArrayEquals(new String[0], ArrayUtils.removeElement(new String[0], "a"));
        assertArrayEquals(new String[] { "a" }, ArrayUtils.removeElement(new String[] { "a" }, "b"));
        assertArrayEquals(new String[] { "b" }, ArrayUtils.removeElement(new String[] { "a", "b" }, "a"));

        // Primitive remove and removeElement
        assertArrayEquals(new boolean[] { false }, ArrayUtils.remove(new boolean[] { true, false }, 0));
        assertArrayEquals(new boolean[] { true }, ArrayUtils.removeElement(new boolean[] { true, false }, false));
        assertArrayEquals(new boolean[] { true, false }, ArrayUtils.removeElement(new boolean[] { true, false }, true)); // wait, removeElement removes *first* occurrence, so removing true from [true, false] leaves [false]? Let's check logic: indexOf(true) is 0, remove(0) leaves [false].

        assertArrayEquals(new byte[] { 0 }, ArrayUtils.remove(new byte[] { 1, 0 }, 0));
        assertArrayEquals(new byte[] { 1 }, ArrayUtils.removeElement(new byte[] { 1, 0 }, (byte) 0));
        assertArrayEquals(new byte[] { 1, 0 }, ArrayUtils.removeElement(new byte[] { 1, 0 }, (byte) 99));

        assertArrayEquals(new char[] { 'b' }, ArrayUtils.remove(new char[] { 'a', 'b' }, 0));
        assertArrayEquals(new char[] { 'a' }, ArrayUtils.removeElement(new char[] { 'a', 'b' }, 'b'));
        assertArrayEquals(new char[] { 'a', 'b' }, ArrayUtils.removeElement(new char[] { 'a', 'b' }, 'z'));

        assertArrayEquals(new double[] { 6.0 }, ArrayUtils.remove(new double[] { 2.5, 6.0 }, 0), 0.0);
        assertArrayEquals(new double[] { 2.3 }, ArrayUtils.removeElement(new double[] { 1.1, 2.3 }, 1.1), 0.0);
        assertArrayEquals(new double[] { 1.1, 2.3 }, ArrayUtils.removeElement(new double[] { 1.1, 2.3 }, 9.9), 0.0);

        assertArrayEquals(new float[] { 6.0f }, ArrayUtils.remove(new float[] { 2.5f, 6.0f }, 0), 0.0f);
        assertArrayEquals(new float[] { 2.3f }, ArrayUtils.removeElement(new float[] { 1.1f, 2.3f }, 1.1f), 0.0f);
        assertArrayEquals(new float[] { 1.1f, 2.3f }, ArrayUtils.removeElement(new float[] { 1.1f, 2.3f }, 9.9f), 0.0f);

        assertArrayEquals(new int[] { 6 }, ArrayUtils.remove(new int[] { 2, 6 }, 0));
        assertArrayEquals(new int[] { 3 }, ArrayUtils.removeElement(new int[] { 1, 3 }, 1));
        assertArrayEquals(new int[] { 1, 3 }, ArrayUtils.removeElement(new int[] { 1, 3 }, 99));

        assertArrayEquals(new long[] { 6L }, ArrayUtils.remove(new long[] { 2L, 6L }, 0), 0L);
        assertArrayEquals(new long[] { 3L }, ArrayUtils.removeElement(new long[] { 1L, 3L }, 1L), 0L);
        assertArrayEquals(new long[] { 1L, 3L }, ArrayUtils.removeElement(new long[] { 1L, 3L }, 99L), 0L);

        assertArrayEquals(new short[] { 6 }, ArrayUtils.remove(new short[] { 2, 6 }, 0));
        assertArrayEquals(new short[] { 3 }, ArrayUtils.removeElement(new short[] { 1, 3 }, (short) 1));
        assertArrayEquals(new short[] { 1, 3 }, ArrayUtils.removeElement(new short[] { 1, 3 }, (short) 99));
    }
}