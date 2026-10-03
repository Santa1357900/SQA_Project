package org.mockito.internal.stubbing.defaultanswers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Test;

public class ReturnsEmptyValuesTest {

    @Test
    public void testReturnValueForPrimitivesAndWrappers() throws Throwable {
        ReturnsEmptyValues returns = new ReturnsEmptyValues();

        assertEquals(Boolean.FALSE, returns.returnValueFor(boolean.class));
        assertEquals(Boolean.FALSE, returns.returnValueFor(Boolean.class));
        assertEquals(Character.valueOf('\0'), returns.returnValueFor(char.class));
        assertEquals(Character.valueOf('\0'), returns.returnValueFor(Character.class));
        assertEquals(Byte.valueOf((byte) 0), returns.returnValueFor(byte.class));
        assertEquals(Byte.valueOf((byte) 0), returns.returnValueFor(Byte.class));
        assertEquals(Short.valueOf((short) 0), returns.returnValueFor(short.class));
        assertEquals(Short.valueOf((short) 0), returns.returnValueFor(Short.class));
        assertEquals(Integer.valueOf(0), returns.returnValueFor(int.class));
        assertEquals(Integer.valueOf(0), returns.returnValueFor(Integer.class));
        assertEquals(Long.valueOf(0L), returns.returnValueFor(long.class));
        assertEquals(Long.valueOf(0L), returns.returnValueFor(Long.class));
        assertEquals(Float.valueOf(0.0f), returns.returnValueFor(float.class));
        assertEquals(Float.valueOf(0.0f), returns.returnValueFor(Float.class));
        assertEquals(Double.valueOf(0.0d), returns.returnValueFor(double.class));
        assertEquals(Double.valueOf(0.0d), returns.returnValueFor(Double.class));
    }

    @Test
    public void testReturnValueForCollections() throws Throwable {
        ReturnsEmptyValues returns = new ReturnsEmptyValues();

        assertTrue(returns.returnValueFor(Collection.class) instanceof LinkedList);
        assertTrue(returns.returnValueFor(List.class) instanceof LinkedList);
        assertTrue(returns.returnValueFor(LinkedList.class) instanceof LinkedList);
        assertTrue(returns.returnValueFor(ArrayList.class) instanceof ArrayList);
        
        assertTrue(returns.returnValueFor(Set.class) instanceof HashSet);
        assertTrue(returns.returnValueFor(HashSet.class) instanceof HashSet);
        assertTrue(returns.returnValueFor(SortedSet.class) instanceof TreeSet);
        assertTrue(returns.returnValueFor(TreeSet.class) instanceof TreeSet);
        assertTrue(returns.returnValueFor(LinkedHashSet.class) instanceof LinkedHashSet);
    }

    @Test
    public void testReturnValueForMaps() throws Throwable {
        ReturnsEmptyValues returns = new ReturnsEmptyValues();

        assertTrue(returns.returnValueFor(Map.class) instanceof HashMap);
        assertTrue(returns.returnValueFor(HashMap.class) instanceof HashMap);
        assertTrue(returns.returnValueFor(SortedMap.class) instanceof TreeMap);
        assertTrue(returns.returnValueFor(TreeMap.class) instanceof TreeMap);
        assertTrue(returns.returnValueFor(LinkedHashMap.class) instanceof LinkedHashMap);
    }

    @Test
    public void testReturnValueForUnknownType() throws Throwable {
        ReturnsEmptyValues returns = new ReturnsEmptyValues();
        assertNull(returns.returnValueFor(String.class));
        assertNull(returns.returnValueFor(Object.class));
        assertNull(returns.returnValueFor(void.class));
    }
}