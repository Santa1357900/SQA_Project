package org.mockito.internal.stubbing.defaultanswers;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.LinkedHashMap;

public class ReturnsEmptyValuesTest {

    @Test
    public void testReturnValueForPrimitivesAndWrappers() throws Throwable {
        ReturnsEmptyValues returnsEmptyValues = new ReturnsEmptyValues();

        assertEquals(Boolean.FALSE, returnsEmptyValues.returnValueFor(boolean.class));
        assertEquals(Boolean.FALSE, returnsEmptyValues.returnValueFor(Boolean.class));
        assertEquals(Character.valueOf('\0'), returnsEmptyValues.returnValueFor(char.class));
        assertEquals(Character.valueOf('\0'), returnsEmptyValues.returnValueFor(Character.class));
        assertEquals(Byte.valueOf((byte) 0), returnsEmptyValues.returnValueFor(byte.class));
        assertEquals(Byte.valueOf((byte) 0), returnsEmptyValues.returnValueFor(Byte.class));
        assertEquals(Short.valueOf((short) 0), returnsEmptyValues.returnValueFor(short.class));
        assertEquals(Short.valueOf((short) 0), returnsEmptyValues.returnValueFor(Short.class));
        assertEquals(Integer.valueOf(0), returnsEmptyValues.returnValueFor(int.class));
        assertEquals(Integer.valueOf(0), returnsEmptyValues.returnValueFor(Integer.class));
        assertEquals(Long.valueOf(0L), returnsEmptyValues.returnValueFor(long.class));
        assertEquals(Long.valueOf(0L), returnsEmptyValues.returnValueFor(Long.class));
        assertEquals(Float.valueOf(0.0f), returnsEmptyValues.returnValueFor(float.class));
        assertEquals(Float.valueOf(0.0f), returnsEmptyValues.returnValueFor(Float.class));
        assertEquals(Double.valueOf(0.0d), returnsEmptyValues.returnValueFor(double.class));
        assertEquals(Double.valueOf(0.0d), returnsEmptyValues.returnValueFor(Double.class));
    }

    @Test
    public void testReturnValueForCollections() throws Throwable {
        ReturnsEmptyValues returnsEmptyValues = new ReturnsEmptyValues();

        assertTrue(returnsEmptyValues.returnValueFor(Collection.class) instanceof LinkedList);
        assertTrue(returnsEmptyValues.returnValueFor(Set.class) instanceof HashSet);
        assertTrue(returnsEmptyValues.returnValueFor(HashSet.class) instanceof HashSet);
        assertTrue(returnsEmptyValues.returnValueFor(SortedSet.class) instanceof TreeSet);
        assertTrue(returnsEmptyValues.returnValueFor(TreeSet.class) instanceof TreeSet);
        assertTrue(returnsEmptyValues.returnValueFor(LinkedHashSet.class) instanceof LinkedHashSet);
        assertTrue(returnsEmptyValues.returnValueFor(List.class) instanceof LinkedList);
        assertTrue(returnsEmptyValues.returnValueFor(LinkedList.class) instanceof LinkedList);
        assertTrue(returnsEmptyValues.returnValueFor(ArrayList.class) instanceof ArrayList);
        assertTrue(returnsEmptyValues.returnValueFor(Map.class) instanceof HashMap);
        assertTrue(returnsEmptyValues.returnValueFor(HashMap.class) instanceof HashMap);
        assertTrue(returnsEmptyValues.returnValueFor(SortedMap.class) instanceof TreeMap);
        assertTrue(returnsEmptyValues.returnValueFor(TreeMap.class) instanceof TreeMap);
        assertTrue(returnsEmptyValues.returnValueFor(LinkedHashMap.class) instanceof LinkedHashMap);
    }

    @Test
    public void testReturnValueForUnknownType() throws Throwable {
        ReturnsEmptyValues returnsEmptyValues = new ReturnsEmptyValues();
        Object result = returnsEmptyValues.returnValueFor(Object.class);
        assertNull(result);
    }
}