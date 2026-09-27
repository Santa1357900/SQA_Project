package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JavaType;

public class TypeBindingsTest {

    @Test
    public void testEmptyBindings() throws Throwable {
        TypeBindings tb = TypeBindings.emptyBindings();
        assertNotNull(tb);
        assertTrue(tb.isEmpty());
        assertEquals(0, tb.size());
        assertEquals("<>", tb.toString());
        assertNull(tb.getBoundName(0));
        assertNull(tb.getBoundType(0));
        assertTrue(tb.getTypeParameters().isEmpty());
        assertFalse(tb.hasUnbound("T"));
    }

    @Test
    public void testCreateWithList() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType intType = tf.constructType(Integer.class);
        List<JavaType> list = new ArrayList<JavaType>();
        list.add(intType);

        TypeBindings tb = TypeBindings.create(List.class, list);
        assertNotNull(tb);
        assertFalse(tb.isEmpty());
        assertEquals(1, tb.size());
        assertEquals("E", tb.getBoundName(0));
        assertEquals(intType, tb.getBoundType(0));
        assertFalse(tb.hasUnbound("E"));
        assertTrue(tb.hasUnbound("X"));
    }

    @Test
    public void testCreateWithArray() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);
        JavaType intType = tf.constructType(Integer.class);
        JavaType[] types = new JavaType[] { strType, intType };

        TypeBindings tb = TypeBindings.create(Map.class, types);
        assertNotNull(tb);
        assertEquals(2, tb.size());
        assertEquals("K", tb.getBoundName(0));
        assertEquals("V", tb.getBoundName(1));
        assertEquals(strType, tb.getBoundType(0));
        assertEquals(intType, tb.getBoundType(1));
    }

    @Test
    public void testCreateSingleArg() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);

        TypeBindings tb = TypeBindings.create(Class.class, strType);
        assertNotNull(tb);
        assertEquals(1, tb.size());
        assertEquals(strType, tb.getBoundType(0));
    }

    @Test
    public void testCreateTwoArgs() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);
        JavaType intType = tf.constructType(Integer.class);

        TypeBindings tb = TypeBindings.create(HashMap.class, strType, intType);
        assertNotNull(tb);
        assertEquals(2, tb.size());
        assertEquals(strType, tb.getBoundType(0));
        assertEquals(intType, tb.getBoundType(1));
    }

    @Test
    public void testCreateIfNeeded() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);

        TypeBindings tb1 = TypeBindings.createIfNeeded(Object.class, (JavaType[]) null);
        assertSame(TypeBindings.emptyBindings(), tb1);

        TypeBindings tb2 = TypeBindings.createIfNeeded(List.class, strType);
        assertNotNull(tb2);
        assertEquals(1, tb2.size());
        assertEquals(strType, tb2.getBoundType(0));

        JavaType[] types = new JavaType[] { strType };
        TypeBindings tb3 = TypeBindings.createIfNeeded(List.class, types);
        assertNotNull(tb3);
        assertEquals(1, tb3.size());
    }

    @Test
    public void testWithUnboundVariable() throws Throwable {
        TypeBindings tb = TypeBindings.emptyBindings();
        TypeBindings tb2 = tb.withUnboundVariable("T");
        assertNotNull(tb2);
        assertTrue(tb2.hasUnbound("T"));
        assertFalse(tb.hasUnbound("T"));
    }

    @Test
    public void testFindBoundType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);
        TypeBindings tb = TypeBindings.create(List.class, strType);

        assertEquals(strType, tb.findBoundType("E"));
        assertNull(tb.findBoundType("NonExistent"));
    }

    @Test
    public void testBoundNameAndTypeEdgeCases() throws Throwable {
        TypeBindings tb = TypeBindings.emptyBindings();
        assertNull(tb.getBoundName(-1));
        assertNull(tb.getBoundName(10));
        assertNull(tb.getBoundType(-1));
        assertNull(tb.getBoundType(10));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType1 = tf.constructType(String.class);
        JavaType strType2 = tf.constructType(String.class);
        JavaType intType = tf.constructType(Integer.class);

        TypeBindings tb1 = TypeBindings.create(List.class, strType1);
        TypeBindings tb2 = TypeBindings.create(List.class, strType2);
        TypeBindings tb3 = TypeBindings.create(List.class, intType);
        TypeBindings tbEmpty1 = TypeBindings.emptyBindings();
        TypeBindings tbEmpty2 = TypeBindings.emptyBindings();

        assertTrue(tb1.equals(tb1));
        assertTrue(tb1.equals(tb2));
        assertFalse(tb1.equals(tb3));
        assertFalse(tb1.equals(null));
        assertFalse(tb1.equals("SomeString"));
        assertTrue(tbEmpty1.equals(tbEmpty2));

        assertEquals(tb1.hashCode(), tb2.hashCode());
    }

    @Test
    public void testToString() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);
        TypeBindings tb = TypeBindings.create(List.class, strType);
        String str = tb.toString();
        assertNotNull(str);
        assertTrue(str.length() > 0);
    }

    @Test
    public void testReadResolve() throws Throwable {
        TypeBindings tbEmpty = TypeBindings.emptyBindings();
        Object resolvedEmpty = tbEmpty.readResolve();
        assertSame(tbEmpty, resolvedEmpty);

        JavaType strType = TypeFactory.defaultInstance().constructType(String.class);
        TypeBindings tb = TypeBindings.create(List.class, strType);
        Object resolved = tb.readResolve();
        assertSame(tb, resolved);
    }

    @Test
    public void testTypeParamStashBranches() throws Throwable {
        // Trigger branches in TypeParamStash via create/createIfNeeded calls
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(String.class);

        assertNotNull(TypeBindings.create(Collection.class, t));
        assertNotNull(TypeBindings.create(AbstractList.class, t));
        assertNotNull(TypeBindings.create(ArrayList.class, t));
        assertNotNull(TypeBindings.create(Iterable.class, t));
        assertNotNull(TypeBindings.create(LinkedHashMap.class, t, t));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateMismatchException() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(String.class);
        // List expects 1 type parameter, passing 2 should fail
        TypeBindings.create(List.class, t, t);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateIfNeededMismatchException() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(String.class);
        TypeBindings.createIfNeeded(List.class, new JavaType[] { t, t });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateSingleArgMismatch() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(String.class);
        // Object.class has 0 type parameters, calling create with 1 arg should fail
        TypeBindings.create(Object.class, t);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateTwoArgsMismatch() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(String.class);
        // List.class has 1 type parameter, calling create with 2 args should fail
        TypeBindings.create(List.class, t, t);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateIfNeededSingleArgMismatch() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(String.class);
        // Object.class has 0 parameters, createIfNeeded with 1 should fail
        TypeBindings.createIfNeeded(Object.class, t);
    }
}