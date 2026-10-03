package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ArrayUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        ArrayUtils utils = new ArrayUtils();
        assertNotNull(utils);
    }

    @Test
    public void testToStringObject() throws Throwable {
        assertEquals("{}", ArrayUtils.toString(null));
        assertEquals("{}", ArrayUtils.toString(null, "{}"));
        assertEquals("CUSTOM", ArrayUtils.toString(null, "CUSTOM"));
        
        Integer[] arr = new Integer[] { Integer.valueOf(1), Integer.valueOf(2) };
        String str = ArrayUtils.toString(arr);
        assertNotNull(str);
    }

    @Test
    public void testIsEquals() throws Throwable {
        Integer[] arr1 = new Integer[] { Integer.valueOf(1) };
        Integer[] arr2 = new Integer[] { Integer.valueOf(1) };
        Integer[] arr3 = new Integer[] { Integer.valueOf(2) };
        
        assertTrue(ArrayUtils.isEquals(arr1, arr2));
        assertFalse(ArrayUtils.isEquals(arr1, arr3));
        assertTrue(ArrayUtils.isEquals(null, null));
        assertFalse(ArrayUtils.isEquals(arr1, null));
    }

    @Test
    public void testToMap() throws Throwable {
        assertNull(ArrayUtils.toMap(null));

        Object[] validArray = new Object[] {
            new Object[] { "key1", "val1" },
            new HashMap.SimpleEntry<Object, Object>("key2", "val2")
        };
        Map<Object, Object> map = ArrayUtils.toMap(validArray);
        assertEquals(2, map.size());
        assertEquals("val1", map.get("key1"));
        assertEquals("val2", map.get("key2"));
    }

    @Test
    public void testToMapInvalidLength() throws Throwable {
        Object[] invalidArray = new Object[] {
            new Object[] { "key1" }
        };
        boolean thrown = false;
        try {
            ArrayUtils.toMap(invalidArray);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testToMapInvalidType() throws Throwable {
        Object[] invalidArray = new Object[] {
            "NotAnEntryOrArray"
        };
        boolean thrown = false;
        try {
            ArrayUtils.toMap(invalidArray);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testCloneObjectArray() throws Throwable {
        assertNull(ArrayUtils.clone((String[]) null));
        String[] arr = new String[] { "a", "b" };
        String[] cloned = ArrayUtils.clone(arr);
        assertNotNull(cloned);
        assertEquals(2, cloned.length);
        assertNotSame(arr, cloned);
    }

    @Test
    public void testClonePrimitives() throws Throwable {
        assertNull(ArrayUtils.clone((long[]) null));
        assertNull(ArrayUtils.clone((int[]) null));
        assertNull(ArrayUtils.clone((short[]) null));
        assertNull(ArrayUtils.clone((char[]) null));
        assertNull(ArrayUtils.clone((byte[]) null));
        assertNull(ArrayUtils.clone((double[]) null));
        assertNull(ArrayUtils.clone((float[]) null));
        assertNull(ArrayUtils.clone((boolean[]) null));

        assertNotNull(ArrayUtils.clone(new long[] { 1L }));
        assertNotNull(ArrayUtils.clone(new int[] { 1 }));
        assertNotNull(ArrayUtils.clone(new short[] { 1 }));
        assertNotNull(ArrayUtils.clone(new char[] { 'a' }));
        assertNotNull(ArrayUtils.clone(new byte[] { 1 }));
        assertNotNull(ArrayUtils.clone(new double[] { 1.0 }));
        assertNotNull(ArrayUtils.clone(new float[] { 1.0f }));
        assertNotNull(ArrayUtils.clone(new boolean[] { true }));
    }

    @Test
    public void testSubarrayObject() throws Throwable {
        assertNull(ArrayUtils.subarray((String[]) null, 0, 2));
        String[] arr = new String[] { "a", "b", "c" };
        
        String[] sub1 = ArrayUtils.subarray(arr, -1, 5);
        assertEquals(3, sub1.length);

        String[] sub2 = ArrayUtils.subarray(arr, 1, 2);
        assertEquals(1, sub2.length);
        assertEquals("b", sub2[0]);

        String[] sub3 = ArrayUtils.subarray(arr, 2, 1);
        assertEquals(0, sub3.length);
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

        assertEquals(1, ArrayUtils.subarray(new long[] { 1L, 2L }, 0, 1).length);
        assertEquals(1, ArrayUtils.subarray(new int[] { 1, 2 }, 0, 1).length);
        assertEquals(1, ArrayUtils.subarray(new short[] { 1, 2 }, 0, 1).length);
        assertEquals(1, ArrayUtils.subarray(new char[] { 'a', 'b' }, 0, 1).length);
        assertEquals(1, ArrayUtils.subarray(new byte[] { 1, 2 }, 0, 1).length);
        assertEquals(1, ArrayUtils.subarray(new double[] { 1.0, 2.0 }, 0, 1).length);
        assertEquals(1, ArrayUtils.subarray(new float[] { 1.0f, 2.0f }, 0, 1).length);
        assertEquals(1, ArrayUtils.subarray(new boolean[] { true, false }, 0, 1).length);
        
        assertEquals(0, ArrayUtils.subarray(new int[] { 1, 2 }, 2, 1).length);
    }

    @Test
    public void testIsSameLength() throws Throwable {
        assertTrue(ArrayUtils.isSameLength((Object[]) null, (Object[]) null));
        assertTrue(ArrayUtils.isSameLength((Object[]) null, new Object[0]));
        assertTrue(ArrayUtils.isSameLength(new Object[0], (Object[]) null));
        assertFalse(ArrayUtils.isSameLength((Object[]) null, new Object[1]));
        assertFalse(ArrayUtils.isSameLength(new Object[1], (Object[]) null));
        assertFalse(ArrayUtils.isSameLength(new Object[1], new Object[2]));
        assertTrue(ArrayUtils.isSameLength(new Object[1], new Object[1]));

        assertTrue(ArrayUtils.isSameLength((long[]) null, (long[]) null));
        assertTrue(ArrayUtils.isSameLength((int[]) null, (int[]) null));
        assertTrue(ArrayUtils.isSameLength((short[]) null, (short[]) null));
        assertTrue(ArrayUtils.isSameLength((char[]) null, (char[]) null));
        assertTrue(ArrayUtils.isSameLength((byte[]) null, (byte[]) null));
        assertTrue(ArrayUtils.isSameLength((double[]) null, (double[]) null));
        assertTrue(ArrayUtils.isSameLength((float[]) null, (float[]) null));
        assertTrue(ArrayUtils.isSameLength((boolean[]) null, (boolean[]) null));

        assertFalse(ArrayUtils.isSameLength(new long[1], new long[2]));
        assertFalse(ArrayUtils.isSameLength(new int[1], new int[2]));
        assertFalse(ArrayUtils.isSameLength(new short[1], new short[2]));
        assertFalse(ArrayUtils.isSameLength(new char[1], new char[2]));
        assertFalse(ArrayUtils.isSameLength(new byte[1], new byte[2]));
        assertFalse(ArrayUtils.isSameLength(new double[1], new double[2]));
        assertFalse(ArrayUtils.isSameLength(new float[1], new float[2]));
        assertFalse(ArrayUtils.isSameLength(new boolean[1], new boolean[2]));
    }

    @Test
    public void testGetLength() throws Throwable {
        assertEquals(0, ArrayUtils.getLength(null));
        assertEquals(2, ArrayUtils.getLength(new int[] { 1, 2 }));
        
        boolean thrown = false;
        try {
            ArrayUtils.getLength("NotAnArray");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testIsSameType() throws Throwable {
        boolean thrown = false;
        try {
            ArrayUtils.isSameType(null, new int[0]);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        assertTrue(ArrayUtils.isSameType(new int[0], new int[1]));
        assertFalse(ArrayUtils.isSameType(new int[0], new long[0]));
    }

    @Test
    public void testReverse() throws Throwable {
        ArrayUtils.reverse((Object[]) null);
        ArrayUtils.reverse((long[]) null);
        ArrayUtils.reverse((int[]) null);
        ArrayUtils.reverse((short[]) null);
        ArrayUtils.reverse((char[]) null);
        ArrayUtils.reverse((byte[]) null);
        ArrayUtils.reverse((double[]) null);
        ArrayUtils.reverse((float[]) null);
        ArrayUtils.reverse((boolean[]) null);

        Integer[] objArr = new Integer[] { Integer.valueOf(1), Integer.valueOf(2) };
        ArrayUtils.reverse(objArr);
        assertEquals(Integer.valueOf(2), objArr[0]);

        long[] longArr = new long[] { 1L, 2L };
        ArrayUtils.reverse(longArr);
        assertEquals(2L, longArr[0]);

        int[] intArr = new int[] { 1, 2 };
        ArrayUtils.reverse(intArr);
        assertEquals(2, intArr[0]);

        short[] shortArr = new short[] { 1, 2 };
        ArrayUtils.reverse(shortArr);
        assertEquals((short) 2, shortArr[0]);

        char[] charArr = new char[] { 'a', 'b' };
        ArrayUtils.reverse(charArr);
        assertEquals('b', charArr[0]);

        byte[] byteArr = new byte[] { 1, 2 };
        ArrayUtils.reverse(byteArr);
        assertEquals((byte) 2, byteArr[0]);

        double[] doubleArr = new double[] { 1.0, 2.0 };
        ArrayUtils.reverse(doubleArr);
        assertEquals(2.0, doubleArr[0], 0.0);

        float[] floatArr = new float[] { 1.0f, 2.0f };
        ArrayUtils.reverse(floatArr);
        assertEquals(2.0f, floatArr[0], 0.0f);

        boolean[] boolArr = new boolean[] { true, false };
        ArrayUtils.reverse(boolArr);
        assertFalse(boolArr[0]);
    }

    @Test
    public void testIndexOfObject() throws Throwable {
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(null, "a"));
        String[] arr = new String[] { null, "a", "b", "a" };
        assertEquals(0, ArrayUtils.indexOf(arr, null));
        assertEquals(1, ArrayUtils.indexOf(arr, "a"));
        assertEquals(1, ArrayUtils.indexOf(arr, "a", -5));
        assertEquals(3, ArrayUtils.indexOf(arr, "a", 2));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(arr, "z"));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf(arr, Integer.valueOf(1))); // component type check
    }

    @Test
    public void testLastIndexOfObject() throws Throwable {
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(null, "a"));
        String[] arr = new String[] { null, "a", "b", "a" };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(arr, "a", -1));
        assertEquals(3, ArrayUtils.lastIndexOf(arr, "a", 10));
        assertEquals(1, ArrayUtils.lastIndexOf(arr, "a", 2));
        assertEquals(0, ArrayUtils.lastIndexOf(arr, null, 5));
        assertEquals(3, ArrayUtils.lastIndexOf(arr, "a"));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(arr, Integer.valueOf(1)));
    }

    @Test
    public void testContainsObject() throws Throwable {
        assertFalse(ArrayUtils.contains(null, "a"));
        String[] arr = new String[] { "a", "b" };
        assertTrue(ArrayUtils.contains(arr, "a"));
        assertFalse(ArrayUtils.contains(arr, "z"));
    }

    @Test
    public void testPrimitiveIndexOfAndLastAndContains() throws Throwable {
        // long
        long[] lArr = new long[] { 1L, 2L, 2L };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((long[]) null, 1L));
        assertEquals(0, ArrayUtils.indexOf(lArr, 1L, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(lArr, 2L, 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((long[]) null, 1L));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(lArr, 2L, -1));
        assertTrue(ArrayUtils.contains(lArr, 1L));
        assertFalse(ArrayUtils.contains((long[]) null, 1L));

        // int
        int[] iArr = new int[] { 1, 2, 2 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((int[]) null, 1));
        assertEquals(0, ArrayUtils.indexOf(iArr, 1, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(iArr, 2, 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((int[]) null, 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(iArr, 2, -1));
        assertTrue(ArrayUtils.contains(iArr, 1));
        assertFalse(ArrayUtils.contains((int[]) null, 1));

        // short
        short[] sArr = new short[] { 1, 2, 2 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((short[]) null, (short) 1));
        assertEquals(0, ArrayUtils.indexOf(sArr, (short) 1, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(sArr, (short) 2, 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((short[]) null, (short) 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(sArr, (short) 2, -1));
        assertTrue(ArrayUtils.contains(sArr, (short) 1));
        assertFalse(ArrayUtils.contains((short[]) null, (short) 1));

        // char
        char[] cArr = new char[] { 'a', 'b', 'b' };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((char[]) null, 'a'));
        assertEquals(0, ArrayUtils.indexOf(cArr, 'a', -1));
        assertEquals(2, ArrayUtils.lastIndexOf(cArr, 'b', 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((char[]) null, 'a'));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(cArr, 'b', -1));
        assertTrue(ArrayUtils.contains(cArr, 'a'));
        assertFalse(ArrayUtils.contains((char[]) null, 'a'));

        // byte
        byte[] bArr = new byte[] { 1, 2, 2 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((byte[]) null, (byte) 1));
        assertEquals(0, ArrayUtils.indexOf(bArr, (byte) 1, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(bArr, (byte) 2, 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((byte[]) null, (byte) 1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(bArr, (byte) 2, -1));
        assertTrue(ArrayUtils.contains(bArr, (byte) 1));
        assertFalse(ArrayUtils.contains((byte[]) null, (byte) 1));

        // boolean
        boolean[] boolArr = new boolean[] { true, false, false };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((boolean[]) null, true));
        assertEquals(0, ArrayUtils.indexOf(boolArr, true, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(boolArr, false, 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((boolean[]) null, true));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(boolArr, false, -1));
        assertTrue(ArrayUtils.contains(boolArr, true));
        assertFalse(ArrayUtils.contains((boolean[]) null, true));
    }

    @Test
    public void testDoubleIndexOfAndLastAndContains() throws Throwable {
        double[] dArr = new double[] { 1.0, 2.0, 2.0 };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((double[]) null, 1.0));
        assertEquals(0, ArrayUtils.indexOf(dArr, 1.0, 0.1));
        assertEquals(0, ArrayUtils.indexOf(dArr, 1.0, -1));
        assertEquals(2, ArrayUtils.indexOf(dArr, 2.0, 1, 0.1));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 2.0));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 2.0, 0.1));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 2.0, 10));
        assertEquals(2, ArrayUtils.lastIndexOf(dArr, 2.0, 10, 0.1));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((double[]) null, 1.0));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(dArr, 1.0, -1));
        assertTrue(ArrayUtils.contains(dArr, 1.0));
        assertTrue(ArrayUtils.contains(dArr, 1.0, 0.1));
        assertFalse(ArrayUtils.contains((double[]) null, 1.0));
    }

    @Test
    public void testFloatIndexOfAndLastAndContains() throws Throwable {
        float[] fArr = new float[] { 1.0f, 2.0f, 2.0f };
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.indexOf((float[]) null, 1.0f));
        assertEquals(0, ArrayUtils.indexOf(fArr, 1.0f, -1));
        assertEquals(2, ArrayUtils.lastIndexOf(fArr, 2.0f));
        assertEquals(2, ArrayUtils.lastIndexOf(fArr, 2.0f, 10));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf((float[]) null, 1.0f));
        assertEquals(ArrayUtils.INDEX_NOT_FOUND, ArrayUtils.lastIndexOf(fArr, 1.0f, -1));
        assertTrue(ArrayUtils.contains(fArr, 1.0f));
        assertFalse(ArrayUtils.contains((float[]) null, 1.0f));
    }

    @Test
    public void testToPrimitiveConverters() throws Throwable {
        assertNull(ArrayUtils.toPrimitive((Character[]) null));
        assertNull(ArrayUtils.toPrimitive((Character[]) null, 'x'));
        assertEquals(0, ArrayUtils.toPrimitive(new Character[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Character[0], 'x').length);

        Character[] charObj = new Character[] { Character.valueOf('a'), null };
        char[] primChar = ArrayUtils.toPrimitive(charObj, 'b');
        assertEquals('a', primChar[0]);
        assertEquals('b', primChar[1]);

        boolean nullEx = false;
        try {
            ArrayUtils.toPrimitive(new Character[] { null });
        } catch (NullPointerException e) {
            nullEx = true;
        }
        assertTrue(nullEx);

        // Long
        assertNull(ArrayUtils.toPrimitive((Long[]) null));
        assertNull(ArrayUtils.toPrimitive((Long[]) null, 0L));
        assertEquals(0, ArrayUtils.toPrimitive(new Long[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Long[0], 0L).length);
        long[] primLong = ArrayUtils.toPrimitive(new Long[] { Long.valueOf(1L), null }, 9L);
        assertEquals(1L, primLong[0]);
        assertEquals(9L, primLong[1]);
        assertNotNull(ArrayUtils.toObject(new long[] { 1L }));
        assertNull(ArrayUtils.toObject((long[]) null));
        assertEquals(0, ArrayUtils.toObject(new long[0]).length);

        // Integer
        assertNull(ArrayUtils.toPrimitive((Integer[]) null));
        assertNull(ArrayUtils.toPrimitive((Integer[]) null, 0));
        assertEquals(0, ArrayUtils.toPrimitive(new Integer[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Integer[0], 0).length);
        int[] primInt = ArrayUtils.toPrimitive(new Integer[] { Integer.valueOf(1), null }, 9);
        assertEquals(1, primInt[0]);
        assertEquals(9, primInt[1]);
        assertNotNull(ArrayUtils.toObject(new int[] { 1 }));
        assertNull(ArrayUtils.toObject((int[]) null));
        assertEquals(0, ArrayUtils.toObject(new int[0]).length);

        // Short
        assertNull(ArrayUtils.toPrimitive((Short[]) null));
        assertNull(ArrayUtils.toPrimitive((Short[]) null, (short) 0));
        assertEquals(0, ArrayUtils.toPrimitive(new Short[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Short[0], (short) 0).length);
        short[] primShort = ArrayUtils.toPrimitive(new Short[] { Short.valueOf((short) 1), null }, (short) 9);
        assertEquals((short) 1, primShort[0]);
        assertEquals((short) 9, primShort[1]);
        assertNotNull(ArrayUtils.toObject(new short[] { 1 }));
        assertNull(ArrayUtils.toObject((short[]) null));
        assertEquals(0, ArrayUtils.toObject(new short[0]).length);

        // Byte
        assertNull(ArrayUtils.toPrimitive((Byte[]) null));
        assertNull(ArrayUtils.toPrimitive((Byte[]) null, (byte) 0));
        assertEquals(0, ArrayUtils.toPrimitive(new Byte[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Byte[0], (byte) 0).length);
        byte[] primByte = ArrayUtils.toPrimitive(new Byte[] { Byte.valueOf((byte) 1), null }, (byte) 9);
        assertEquals((byte) 1, primByte[0]);
        assertEquals((byte) 9, primByte[1]);
        assertNotNull(ArrayUtils.toObject(new byte[] { 1 }));
        assertNull(ArrayUtils.toObject((byte[]) null));
        assertEquals(0, ArrayUtils.toObject(new byte[0]).length);

        // Double
        assertNull(ArrayUtils.toPrimitive((Double[]) null));
        assertNull(ArrayUtils.toPrimitive((Double[]) null, 0.0));
        assertEquals(0, ArrayUtils.toPrimitive(new Double[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Double[0], 0.0).length);
        double[] primDouble = ArrayUtils.toPrimitive(new Double[] { Double.valueOf(1.0), null }, 9.0);
        assertEquals(1.0, primDouble[0], 0.0);
        assertEquals(9.0, primDouble[1], 0.0);
        assertNotNull(ArrayUtils.toObject(new double[] { 1.0 }));
        assertNull(ArrayUtils.toObject((double[]) null));
        assertEquals(0, ArrayUtils.toObject(new double[0]).length);

        // Float
        assertNull(ArrayUtils.toPrimitive((Float[]) null));
        assertNull(ArrayUtils.toPrimitive((Float[]) null, 0.0f));
        assertEquals(0, ArrayUtils.toPrimitive(new Float[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Float[0], 0.0f).length);
        float[] primFloat = ArrayUtils.toPrimitive(new Float[] { Float.valueOf(1.0f), null }, 9.0f);
        assertEquals(1.0f, primFloat[0], 0.0f);
        assertEquals(9.0f, primFloat[1], 0.0f);
        assertNotNull(ArrayUtils.toObject(new float[] { 1.0f }));
        assertNull(ArrayUtils.toObject((float[]) null));
        assertEquals(0, ArrayUtils.toObject(new float[0]).length);

        // Boolean
        assertNull(ArrayUtils.toPrimitive((Boolean[]) null));
        assertNull(ArrayUtils.toPrimitive((Boolean[]) null, false));
        assertEquals(0, ArrayUtils.toPrimitive(new Boolean[0]).length);
        assertEquals(0, ArrayUtils.toPrimitive(new Boolean[0], false).length);
        boolean[] primBool = ArrayUtils.toPrimitive(new Boolean[] { Boolean.TRUE, null }, false);
        assertTrue(primBool[0]);
        assertFalse(primBool[1]);
        assertNotNull(ArrayUtils.toObject(new boolean[] { true }));
        assertNull(ArrayUtils.toObject((boolean[]) null));
        assertEquals(0, ArrayUtils.toObject(new boolean[0]).length);
        
        // Character object extra
        assertNotNull(ArrayUtils.toObject(new char[] { 'a' }));
        assertNull(ArrayUtils.toObject((char[]) null));
        assertEquals(0, ArrayUtils.toObject(new char[0]).length);
    }

    @Test
    public void testIsEmpty() throws Throwable {
        assertTrue(ArrayUtils.isEmpty((Object[]) null));
        assertTrue(ArrayUtils.isEmpty(new Object[0]));
        assertFalse(ArrayUtils.isEmpty(new Object[1]));

        assertTrue(ArrayUtils.isEmpty((long[]) null));
        assertTrue(ArrayUtils.isEmpty(new long[0]));
        assertFalse(ArrayUtils.isEmpty(new long[1]));

        assertTrue(ArrayUtils.isEmpty((int[]) null));
        assertTrue(ArrayUtils.isEmpty(new int[0]));
        assertFalse(ArrayUtils.isEmpty(new int[1]));

        assertTrue(ArrayUtils.isEmpty((short[]) null));
        assertTrue(ArrayUtils.isEmpty(new short[0]));
        assertFalse(ArrayUtils.isEmpty(new short[1]));

        assertTrue(ArrayUtils.isEmpty((char[]) null));
        assertTrue(ArrayUtils.isEmpty(new char[0]));
        assertFalse(ArrayUtils.isEmpty(new char[1]));

        assertTrue(ArrayUtils.isEmpty((byte[]) null));
        assertTrue(ArrayUtils.isEmpty(new byte[0]));
        assertFalse(ArrayUtils.isEmpty(new byte[1]));

        assertTrue(ArrayUtils.isEmpty((double[]) null));
        assertTrue(ArrayUtils.isEmpty(new double[0]));
        assertFalse(ArrayUtils.isEmpty(new double[1]));

        assertTrue(ArrayUtils.isEmpty((float[]) null));
        assertTrue(ArrayUtils.isEmpty(new float[0]));
        assertFalse(ArrayUtils.isEmpty(new float[1]));

        assertTrue(ArrayUtils.isEmpty((boolean[]) null));
        assertTrue(ArrayUtils.isEmpty(new boolean[0]));
        assertFalse(ArrayUtils.isEmpty(new boolean[1]));
    }

    @Test
    public void testAddAll() throws Throwable {
        assertNull(ArrayUtils.addAll((String[]) null, (String[]) null));
        assertNotNull(ArrayUtils.addAll(new String[] { "a" }, (String[]) null));
        assertNotNull(ArrayUtils.addAll((String[]) null, new String[] { "b" }));
        assertEquals(2, ArrayUtils.addAll(new String[] { "a" }, new String[] { "b" }).length);

        assertEquals(2, ArrayUtils.addAll(new boolean[] { true }, new boolean[] { false }).length);
        assertNotNull(ArrayUtils.addAll((boolean[]) null, new boolean[] { true }));
        assertNotNull(ArrayUtils.addAll(new boolean[] { true }, (boolean[]) null));

        assertEquals(2, ArrayUtils.addAll(new char[] { 'a' }, new char[] { 'b' }).length);
        assertNotNull(ArrayUtils.addAll((char[]) null, new char[] { 'a' }));
        assertNotNull(ArrayUtils.addAll(new char[] { 'a' }, (char[]) null));

        assertEquals(2, ArrayUtils.addAll(new byte[] { 1 }, new byte[] { 2 }).length);
        assertNotNull(ArrayUtils.addAll((byte[]) null, new byte[] { 1 }));
        assertNotNull(ArrayUtils.addAll(new byte[] { 1 }, (byte[]) null));

        assertEquals(2, ArrayUtils.addAll(new short[] { 1 }, new short[] { 2 }).length);
        assertNotNull(ArrayUtils.addAll((short[]) null, new short[] { 1 }));
        assertNotNull(ArrayUtils.addAll(new short[] { 1 }, (short[]) null));

        assertEquals(2, ArrayUtils.addAll(new int[] { 1 }, new int[] { 2 }).length);
        assertNotNull(ArrayUtils.addAll((int[]) null, new int[] { 1 }));
        assertNotNull(ArrayUtils.addAll(new int[] { 1 }, (int[]) null));

        assertEquals(2, ArrayUtils.addAll(new long[] { 1L }, new long[] { 2L }).length);
        assertNotNull(ArrayUtils.addAll((long[]) null, new long[] { 1L }));
        assertNotNull(ArrayUtils.addAll(new long[] { 1L }, (long[]) null));

        assertEquals(2, ArrayUtils.addAll(new float[] { 1.0f }, new float[] { 2.0f }).length);
        assertNotNull(ArrayUtils.addAll((float[]) null, new float[] { 1.0f }));
        assertNotNull(ArrayUtils.addAll(new float[] { 1.0f }, (float[]) null));

        assertEquals(2, ArrayUtils.addAll(new double[] { 1.0 }, new double[] { 2.0 }).length);
        assertNotNull(ArrayUtils.addAll((double[]) null, new double[] { 1.0 }));
        assertNotNull(ArrayUtils.addAll(new double[] { 1.0 }, (double[]) null));
    }

    @Test
    public void testAddElement() throws Throwable {
        String[] res1 = ArrayUtils.add((String[]) null, "a");
        assertEquals(1, res1.length);
        assertEquals("a", res1[0]);

        String[] res2 = ArrayUtils.add(new String[] { "a" }, "b");
        assertEquals(2, res2.length);

        assertEquals(2, ArrayUtils.add((boolean[]) null, true).length);
        assertEquals(2, ArrayUtils.add(new boolean[] { true }, false).length);

        assertEquals(2, ArrayUtils.add((byte[]) null, (byte) 1).length);
        assertEquals(2, ArrayUtils.add(new byte[] { 1 }, (byte) 2).length);

        assertEquals(2, ArrayUtils.add((char[]) null, 'a').length);
        assertEquals(2, ArrayUtils.add(new char[] { 'a' }, 'b').length);

        assertEquals(2, ArrayUtils.add((double[]) null, 1.0).length);
        assertEquals(2, ArrayUtils.add(new double[] { 1.0 }, 2.0).length);

        assertEquals(2, ArrayUtils.add((float[]) null, 1.0f).length);
        assertEquals(2, ArrayUtils.add(new float[] { 1.0f }, 2.0f).length);

        assertEquals(2, ArrayUtils.add((int[]) null, 1).length);
        assertEquals(2, ArrayUtils.add(new int[] { 1 }, 2).length);

        assertEquals(2, ArrayUtils.add((long[]) null, 1L).length);
        assertEquals(2, ArrayUtils.add(new long[] { 1L }, 2L).length);

        assertEquals(2, ArrayUtils.add((short[]) null, (short) 1).length);
        assertEquals(2, ArrayUtils.add(new short[] { 1 }, (short) 2).length);
    }

    @Test
    public void testAddAtIndex() throws Throwable {
        String[] res1 = ArrayUtils.add(null, 0, "a");
        assertEquals(1, res1.length);

        boolean ex1 = false;
        try {
            ArrayUtils.add(null, 1, "a");
        } catch (IndexOutOfBoundsException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        String[] res2 = ArrayUtils.add(new String[] { "a" }, 1, "b");
        assertEquals(2, res2.length);
        assertEquals("b", res2[1]);

        boolean ex2 = false;
        try {
            ArrayUtils.add(new String[] { "a" }, 5, "b");
        } catch (IndexOutOfBoundsException e) {
            ex2 = true;
        }
        assertTrue(ex2);

        // Primitive adds at index
        assertEquals(2, ArrayUtils.add(new boolean[] { true }, 0, false).length);
        assertEquals(2, ArrayUtils.add(new char[] { 'a' }, 0, 'b').length);
        assertEquals(2, ArrayUtils.add(new byte[] { 1 }, 0, (byte) 2).length);
        assertEquals(2, ArrayUtils.add(new short[] { 1 }, 0, (short) 2).length);
        assertEquals(2, ArrayUtils.add(new int[] { 1 }, 0, 2).length);
        assertEquals(2, ArrayUtils.add(new long[] { 1L }, 0, 2L).length);
        assertEquals(2, ArrayUtils.add(new float[] { 1.0f }, 0, 2.0f).length);
        assertEquals(2, ArrayUtils.add(new double[] { 1.0 }, 0, 2.0).length);
        
        // null elements with null array for generic add
        Object[] nullAdd = ArrayUtils.add((Object[]) null, 0, (Object) null);
        assertNotNull(nullAdd);
    }

    @Test
    public void testRemove() throws Throwable {
        String[] arr = new String[] { "a", "b", "c" };
        String[] removed = ArrayUtils.remove(arr, 1);
        assertEquals(2, removed.length);
        assertEquals("c", removed[1]);

        boolean ex = false;
        try {
            ArrayUtils.remove(arr, 5);
        } catch (IndexOutOfBoundsException e) {
            ex = true;
        }
        assertTrue(ex);

        // Primitive removes
        assertEquals(1, ArrayUtils.remove(new boolean[] { true, false }, 0).length);
        assertEquals(1, ArrayUtils.remove(new byte[] { 1, 2 }, 0).length);
        assertEquals(1, ArrayUtils.remove(new char[] { 'a', 'b' }, 0).length);
        assertEquals(1, ArrayUtils.remove(new double[] { 1.0, 2.0 }, 0).length);
        assertEquals(1, ArrayUtils.remove(new float[] { 1.0f, 2.0f }, 0).length);
        assertEquals(1, ArrayUtils.remove(new int[] { 1, 2 }, 0).length);
        assertEquals(1, ArrayUtils.remove(new long[] { 1L, 2L }, 0).length);
        assertEquals(1, ArrayUtils.remove(new short[] { 1, 2 }, 0).length);
    }

    @Test
    public void testRemoveElement() throws Throwable {
        String[] arr = new String[] { "a", "b", "a" };
        String[] res = ArrayUtils.removeElement(arr, "a");
        assertEquals(2, res.length);
        assertEquals("b", res[0]);

        String[] notFound = ArrayUtils.removeElement(arr, "z");
        assertEquals(3, notFound.length);
        assertNull(ArrayUtils.removeElement((String[]) null, "a"));

        // Primitive remove elements
        assertNotNull(ArrayUtils.removeElement(new boolean[] { true }, true));
        assertNotNull(ArrayUtils.removeElement((boolean[]) null, true));
        assertEquals(1, ArrayUtils.removeElement(new boolean[] { true, false }, true).length);

        assertNotNull(ArrayUtils.removeElement(new byte[] { 1 }, (byte) 1));
        assertNotNull(ArrayUtils.removeElement((byte[]) null, (byte) 1));
        assertEquals(1, ArrayUtils.removeElement(new byte[] { 1, 2 }, (byte) 1).length);

        assertNotNull(ArrayUtils.removeElement(new char[] { 'a' }, 'a'));
        assertNotNull(ArrayUtils.removeElement((char[]) null, 'a'));
        assertEquals(1, ArrayUtils.removeElement(new char[] { 'a', 'b' }, 'a').length);

        assertNotNull(ArrayUtils.removeElement(new double[] { 1.0 }, 1.0));
        assertNotNull(ArrayUtils.removeElement((double[]) null, 1.0));
        assertEquals(1, ArrayUtils.removeElement(new double[] { 1.0, 2.0 }, 1.0).length);

        assertNotNull(ArrayUtils.removeElement(new float[] { 1.0f }, 1.0f));
        assertNotNull(ArrayUtils.removeElement((float[]) null, 1.0f));
        assertEquals(1, ArrayUtils.removeElement(new float[] { 1.0f, 2.0f }, 1.0f).length);

        assertNotNull(ArrayUtils.removeElement(new int[] { 1 }, 1));
        assertNotNull(ArrayUtils.removeElement((int[]) null, 1));
        assertEquals(1, ArrayUtils.removeElement(new int[] { 1, 2 }, 1).length);

        assertNotNull(ArrayUtils.removeElement(new long[] { 1L }, 1L));
        assertNotNull(ArrayUtils.removeElement((long[]) null, 1L));
        assertEquals(1, ArrayUtils.removeElement(new long[] { 1L, 2L }, 1L).length);

        assertNotNull(ArrayUtils.removeElement(new short[] { 1 }, (short) 1));
        assertNotNull(ArrayUtils.removeElement((short[]) null, (short) 1));
        assertEquals(1, ArrayUtils.removeElement(new short[] { 1, 2 }, (short) 1).length);
    }
}