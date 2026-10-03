package org.mockito.internal.matchers;

import org.junit.Test;
import static org.junit.Assert.*;

public class EqualityTest {

    @Test
    public void testBothNull() throws Throwable {
        assertTrue(Equality.areEqual(null, null));
    }

    @Test
    public void testFirstNull() throws Throwable {
        assertFalse(Equality.areEqual(null, "non-null"));
    }

    @Test
    public void testSecondNull() throws Throwable {
        assertFalse(Equality.areEqual("non-null", null));
    }

    @Test
    public void testEqualObjects() throws Throwable {
        assertTrue(Equality.areEqual("test", "test"));
        assertTrue(Equality.areEqual(Integer.valueOf(123), Integer.valueOf(123)));
    }

    @Test
    public void testNotEqualObjects() throws Throwable {
        assertFalse(Equality.areEqual("test1", "test2"));
        assertFalse(Equality.areEqual(Integer.valueOf(123), Integer.valueOf(456)));
    }

    @Test
    public void testIsArrayWithNonArray() throws Throwable {
        assertFalse(Equality.isArray("not an array"));
        assertFalse(Equality.isArray(Integer.valueOf(5)));
    }

    @Test
    public void testIsArrayWithArray() throws Throwable {
        assertTrue(Equality.isArray(new int[0]));
        assertTrue(Equality.isArray(new String[2]));
        assertTrue(Equality.isArray(new Object[1][1]));
    }

    @Test
    public void testPrimitiveArraysEqual() throws Throwable {
        int[] arr1 = new int[] { 1, 2, 3 };
        int[] arr2 = new int[] { 1, 2, 3 };
        assertTrue(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testPrimitiveArraysDifferentLength() throws Throwable {
        int[] arr1 = new int[] { 1, 2, 3 };
        int[] arr2 = new int[] { 1, 2 };
        assertFalse(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testPrimitiveArraysDifferentElements() throws Throwable {
        int[] arr1 = new int[] { 1, 2, 3 };
        int[] arr2 = new int[] { 1, 2, 4 };
        assertFalse(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testObjectArraysEqual() throws Throwable {
        String[] arr1 = new String[] { "a", "b", "c" };
        String[] arr2 = new String[] { "a", "b", "c" };
        assertTrue(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testObjectArraysDifferentElements() throws Throwable {
        String[] arr1 = new String[] { "a", "b", "c" };
        String[] arr2 = new String[] { "a", "x", "c" };
        assertFalse(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testMultiDimensionalArraysEqual() throws Throwable {
        int[][] arr1 = new int[][] { { 1, 2 }, { 3, 4 } };
        int[][] arr2 = new int[][] { { 1, 2 }, { 3, 4 } };
        assertTrue(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testMultiDimensionalArraysDifferent() throws Throwable {
        int[][] arr1 = new int[][] { { 1, 2 }, { 3, 4 } };
        int[][] arr2 = new int[][] { { 1, 2 }, { 3, 99 } };
        assertFalse(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testArrayComparedWithNonArray() throws Throwable {
        int[] arr = new int[] { 1, 2, 3 };
        assertFalse(Equality.areEqual(arr, "not an array"));
        assertFalse(Equality.areEqual("not an array", arr));
    }

    @Test
    public void testEmptyArraysEqual() throws Throwable {
        int[] arr1 = new int[0];
        int[] arr2 = new int[0];
        assertTrue(Equality.areEqual(arr1, arr2));
    }

    @Test
    public void testArraysWithNullElements() throws Throwable {
        String[] arr1 = new String[] { null, "b" };
        String[] arr2 = new String[] { null, "b" };
        String[] arr3 = new String[] { "a", "b" };
        
        assertTrue(Equality.areEqual(arr1, arr2));
        assertFalse(Equality.areEqual(arr1, arr3));
    }
}