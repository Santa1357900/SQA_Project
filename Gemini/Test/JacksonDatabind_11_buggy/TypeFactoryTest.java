package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;

public class TypeFactoryTest {

    @Test
    public void testDefaultInstance() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertNotNull(tf);
        assertSame(tf, TypeFactory.defaultInstance());
    }

    @Test
    public void testClearCache() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        tf.clearCache();
        JavaType t = tf.constructType(String.class);
        assertNotNull(t);
        tf.clearCache();
    }

    @Test
    public void testUnknownType() throws Throwable {
        JavaType unknown = TypeFactory.unknownType();
        assertNotNull(unknown);
        assertEquals(Object.class, unknown.getRawClass());
    }

    @Test
    public void testRawClass() throws Throwable {
        Class<?> raw = TypeFactory.rawClass(String.class);
        assertEquals(String.class, raw);

        JavaType stringType = TypeFactory.defaultInstance().constructType(String.class);
        Class<?> rawFromType = TypeFactory.rawClass(stringType);
        assertEquals(String.class, rawFromType);
    }

    @Test
    public void testConstructTypeVariations() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        // Class
        JavaType t1 = tf.constructType(String.class);
        assertEquals(String.class, t1.getRawClass());

        // Primitives
        assertEquals(Boolean.TYPE, tf.constructType(Boolean.TYPE).getRawClass());
        assertEquals(Integer.TYPE, tf.constructType(Integer.TYPE).getRawClass());
        assertEquals(Long.TYPE, tf.constructType(Long.TYPE).getRawClass());

        // TypeReference
        JavaType t2 = tf.constructType(new TypeReference<List<String>>() {});
        assertNotNull(t2);
        assertTrue(List.class.isAssignableFrom(t2.getRawClass()));

        // Context Class
        JavaType t3 = tf.constructType(String.class, Object.class);
        assertEquals(String.class, t3.getRawClass());

        // Context JavaType
        JavaType t4 = tf.constructType(String.class, t1);
        assertEquals(String.class, t4.getRawClass());

        // Null context
        JavaType t5 = tf.constructType(String.class, (Class<?>) null);
        assertEquals(String.class, t5.getRawClass());

        JavaType t6 = tf.constructType(String.class, (JavaType) null);
        assertEquals(String.class, t6.getRawClass());
    }

    @Test
    public void testConstructArrayType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        ArrayType at1 = tf.constructArrayType(String.class);
        assertNotNull(at1);
        assertTrue(at1.isArrayType());

        JavaType stringType = tf.constructType(String.class);
        ArrayType at2 = tf.constructArrayType(stringType);
        assertNotNull(at2);
        assertTrue(at2.isArrayType());
    }

    @Test
    public void testConstructCollectionType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        CollectionType ct1 = tf.constructCollectionType(List.class, String.class);
        assertNotNull(ct1);
        assertTrue(ct1.isContainerType());

        JavaType stringType = tf.constructType(String.class);
        CollectionType ct2 = tf.constructCollectionType(List.class, stringType);
        assertNotNull(ct2);
    }

    @Test
    public void testConstructCollectionLikeType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        CollectionLikeType clt1 = tf.constructCollectionLikeType(Collection.class, String.class);
        assertNotNull(clt1);

        JavaType stringType = tf.constructType(String.class);
        CollectionLikeType clt2 = tf.constructCollectionLikeType(Collection.class, stringType);
        assertNotNull(clt2);
    }

    @Test
    public void testConstructMapType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType keyType = tf.constructType(String.class);
        JavaType valType = tf.constructType(Integer.class);

        MapType mt1 = tf.constructMapType(Map.class, keyType, valType);
        assertNotNull(mt1);

        MapType mt2 = tf.constructMapType(Map.class, String.class, Integer.class);
        assertNotNull(mt2);
    }

    @Test
    public void testConstructMapLikeType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType keyType = tf.constructType(String.class);
        JavaType valType = tf.constructType(Integer.class);

        MapLikeType mlt1 = tf.constructMapLikeType(Map.class, keyType, valType);
        assertNotNull(mlt1);

        MapLikeType mlt2 = tf.constructMapLikeType(Map.class, String.class, Integer.class);
        assertNotNull(mlt2);
    }

    @Test
    public void testConstructRawTypes() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertNotNull(tf.constructRawCollectionType(List.class));
        assertNotNull(tf.constructRawCollectionLikeType(Collection.class));
        assertNotNull(tf.constructRawMapType(Map.class));
        assertNotNull(tf.constructRawMapLikeType(Map.class));
    }

    @Test
    public void testUncheckedSimpleType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType st = tf.uncheckedSimpleType(String.class);
        assertNotNull(st);
        assertEquals(String.class, st.getRawClass());
    }

    @Test
    public void testConstructSimpleTypeWithMismatch() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        // List has 1 type parameter, we give 0 or 2 to trigger IllegalArgumentException
        try {
            tf.constructSimpleType(List.class, List.class, new JavaType[0]);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Parameter type mismatch"));
        }
    }

    @Test
    public void testConstructParametrizedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        // Array parametrized
        JavaType at = tf.constructParametrizedType(String[].class, String[].class, String.class);
        assertTrue(at.isArrayType());

        // Map parametrized
        JavaType mt = tf.constructParametrizedType(Map.class, Map.class, String.class, Integer.class);
        assertTrue(mt.isMapType());

        // Collection parametrized
        JavaType ct = tf.constructParametrizedType(List.class, List.class, String.class);
        assertTrue(ct.isCollectionType());

        // Simple parametrized
        JavaType st = tf.constructParametrizedType(List.class, List.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(st);
    }

    @Test
    public void testConstructParametrizedTypeErrors() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        // Array with wrong parameter count
        try {
            tf.constructParametrizedType(String[].class, String[].class, new JavaType[0]);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Need exactly 1 parameter type for arrays"));
        }

        // Map with wrong parameter count
        try {
            tf.constructParametrizedType(Map.class, Map.class, tf.constructType(String.class));
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Need exactly 2 parameter types for Map types"));
        }

        // Collection with wrong parameter count
        try {
            tf.constructParametrizedType(List.class, List.class, new JavaType[0]);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Need exactly 1 parameter type for Collection types"));
        }
    }

    @Test
    public void testConstructFromCanonical() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructFromCanonical("java.lang.String");
        assertEquals(String.class, t.getRawClass());
    }

    @Test
    public void testWithModifier() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        TypeFactory tf2 = tf.withModifier(null);
        assertNotNull(tf2);

        TypeModifier dummyMod = new TypeModifier() {
            public JavaType modifyType(JavaType annotated, Type actualType, TypeBindings bindings, TypeFactory typeFactory) {
                return annotated;
            }
            public String toString() { return "Dummy"; }
        };

        TypeFactory tf3 = tf.withModifier(dummyMod);
        assertNotNull(tf3);

        TypeFactory tf4 = tf3.withModifier(dummyMod);
        assertNotNull(tf4);
    }

    @Test
    public void testMoreSpecificType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t1 = tf.constructType(String.class);
        JavaType t2 = tf.constructType(CharSequence.class);
        JavaType t3 = tf.constructType(Integer.class);

        assertNull(tf.moreSpecificType(null, null));
        assertSame(t1, tf.moreSpecificType(null, t1));
        assertSame(t1, tf.moreSpecificType(t1, null));
        assertSame(t1, tf.moreSpecificType(t1, t1));

        // CharSequence is assignable from String, so String (t1) is more specific than CharSequence (t2)
        assertSame(t1, tf.moreSpecificType(t2, t1));
        assertSame(t1, tf.moreSpecificType(t1, t2));

        // Unrelated types (String and Integer), should return first
        assertSame(t1, tf.moreSpecificType(t1, t3));
    }

    @Test
    public void testFindTypeParameters() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType mapType = tf.constructType(new TypeReference<HashMap<String, Integer>>() {});
        JavaType[] params = tf.findTypeParameters(mapType, Map.class);
        assertNotNull(params);
        assertEquals(2, params.length);

        JavaType[] paramsClass = tf.findTypeParameters(HashMap.class, Map.class);
        assertNotNull(paramsClass);

        // Class not a subtype
        try {
            tf.findTypeParameters(String.class, Map.class);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is not a subtype of"));
        }
    }

    @Test
    public void testConstructSpecializedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType base = tf.constructType(Map.class);
        JavaType spec = tf.constructSpecializedType(base, HashMap.class);
        assertEquals(HashMap.class, spec.getRawClass());

        // Same class optimization
        assertSame(base, tf.constructSpecializedType(base, Map.class));

        // Incompatible subclass
        try {
            tf.constructSpecializedType(tf.constructType(Map.class), String.class);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not subtype of"));
        }
    }

    @Test
    public void testUnrecognizedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        // Pass a mock or unsupported Type implementation via reflection or anonymous class
        Type weirdType = new Type() {
            public String toString() { return "WeirdType"; }
        };
        try {
            tf.constructType(weirdType);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unrecognized Type"));
        }

        try {
            tf.constructType(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("[null]"));
        }
    }

    @Test
    public void testMapEntryAndCacheBranches() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        // Map.Entry handling
        JavaType entryType = tf.constructType(Map.Entry.class);
        assertNotNull(entryType);

        // Fetch again to hit cache
        JavaType entryTypeCached = tf.constructType(Map.Entry.class);
        assertNotNull(entryTypeCached);
    }
}