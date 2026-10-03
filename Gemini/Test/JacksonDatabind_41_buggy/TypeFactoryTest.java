package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
        JavaType type = tf.constructType(String.class);
        assertNotNull(type);
        tf.clearCache();
    }

    @Test
    public void testGetClassLoader() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertNull(tf.getClassLoader());

        ClassLoader customLoader = new ClassLoader() {};
        TypeFactory tfWithLoader = tf.withClassLoader(customLoader);
        assertSame(customLoader, tfWithLoader.getClassLoader());
    }

    @Test
    public void testWithModifier() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        TypeModifier mod = new TypeModifier() {
            public JavaType modifyType(JavaType type, Type jdkType, TypeBindings context, TypeFactory typeFactory) {
                return type;
            }
            public String toString() {
                return "DummyModifier";
            }
        };

        TypeFactory tf2 = tf.withModifier(null);
        assertNotNull(tf2);

        TypeFactory tf3 = tf.withModifier(mod);
        assertNotNull(tf3);

        TypeFactory tf4 = tf3.withModifier(mod);
        assertNotNull(tf4);
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

        TypeReference<List<String>> ref = new TypeReference<List<String>>() {};
        Class<?> rawFromType = TypeFactory.rawClass(ref.getType());
        assertEquals(List.class, rawFromType);
    }

    @Test
    public void testFindClass() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        Class<?> cls = tf.findClass("java.lang.String");
        assertEquals(String.class, cls);

        Class<?> intPrim = tf.findClass("int");
        assertEquals(Integer.TYPE, intPrim);

        try {
            tf.findClass("non.existent.ClassName12345");
            fail("Expected ClassNotFoundException");
        } catch (ClassNotFoundException e) {
            // expected
        }
    }

    @Test
    public void testConstructSpecializedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(List.class);
        JavaType specType = tf.constructSpecializedType(baseType, ArrayList.class);
        assertEquals(ArrayList.class, specType.getRawClass());

        // Same class optimization
        assertSame(baseType, tf.constructSpecializedType(baseType, List.class));

        // Object base type
        JavaType objType = tf.constructType(Object.class);
        JavaType specObj = tf.constructSpecializedType(objType, String.class);
        assertEquals(String.class, specObj.getRawClass());

        // Map types specialized
        JavaType mapBase = tf.constructType(Map.class);
        JavaType hashmapSpec = tf.constructSpecializedType(mapBase, HashMap.class);
        assertEquals(HashMap.class, hashmapSpec.getRawClass());

        JavaType linkedHashmapSpec = tf.constructSpecializedType(mapBase, LinkedHashMap.class);
        assertEquals(LinkedHashMap.class, linkedHashmapSpec.getRawClass());

        JavaType enumMapSpec = tf.constructSpecializedType(mapBase, EnumMap.class);
        assertEquals(EnumMap.class, enumMapSpec.getRawClass());

        JavaType treeMapSpec = tf.constructSpecializedType(mapBase, TreeMap.class);
        assertEquals(TreeMap.class, treeMapSpec.getRawClass());

        // Collection types specialized
        JavaType collBase = tf.constructType(Collection.class);
        JavaType arrayListSpec = tf.constructSpecializedType(collBase, ArrayList.class);
        assertEquals(ArrayList.class, arrayListSpec.getRawClass());

        JavaType linkedListSpec = tf.constructSpecializedType(collBase, LinkedList.class);
        assertEquals(LinkedList.class, linkedListSpec.getRawClass());

        JavaType hashSetSpec = tf.constructSpecializedType(collBase, HashSet.class);
        assertEquals(HashSet.class, hashSetSpec.getRawClass());

        JavaType treeSetSpec = tf.constructSpecializedType(collBase, TreeSet.class);
        assertEquals(TreeSet.class, treeSetSpec.getRawClass());

        // EnumSet case
        JavaType enumSetBase = tf.constructType(EnumSet.class);
        assertSame(enumSetBase, tf.constructSpecializedType(enumSetBase, EnumSet.class));

        // Interface refinement
        JavaType intfType = tf.constructType(List.class);
        JavaType refined = tf.constructSpecializedType(intfType, ArrayList.class);
        assertNotNull(refined);

        // Invalid subtype
        try {
            tf.constructSpecializedType(tf.constructType(String.class), Integer.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not subtype of"));
        }
    }

    @Test
    public void testConstructGeneralizedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(ArrayList.class);
        JavaType genType = tf.constructGeneralizedType(baseType, List.class);
        assertEquals(List.class, genType.getRawClass());

        // Same raw class optimization
        assertSame(baseType, tf.constructGeneralizedType(baseType, ArrayList.class));

        // Invalid super-type
        try {
            tf.constructGeneralizedType(baseType, String.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not a super-type") || e.getMessage().contains("Internal error"));
        }
    }

    @Test
    public void testConstructFromCanonical() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType type = tf.constructFromCanonical("java.lang.String");
        assertEquals(String.class, type.getRawClass());
    }

    @Test
    public void testFindTypeParameters() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType type = tf.constructType(new TypeReference<List<String>>() {});
        JavaType[] params = tf.findTypeParameters(type, Collection.class);
        assertEquals(1, params.length);
        assertEquals(String.class, params[0].getRawClass());

        JavaType nonGeneric = tf.constructType(String.class);
        JavaType[] emptyParams = tf.findTypeParameters(nonGeneric, Collection.class);
        assertEquals(0, emptyParams.length);
    }

    @Test
    public void testMoreSpecificType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType stringType = tf.constructType(String.class);
        JavaType objType = tf.constructType(Object.class);

        assertSame(stringType, tf.moreSpecificType(null, stringType));
        assertSame(stringType, tf.moreSpecificType(stringType, null));
        assertSame(stringType, tf.moreSpecificType(stringType, stringType));
        assertSame(stringType, tf.moreSpecificType(objType, stringType));
        assertSame(stringType, tf.moreSpecificType(stringType, objType));
    }

    @Test
    public void testConstructTypeVariants() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertNotNull(tf.constructType((Type) String.class));
        assertNotNull(tf.constructType(String.class, TypeFactory.EMPTY_BINDINGS));
        assertNotNull(tf.constructType(new TypeReference<List<String>>() {}));
        assertNotNull(tf.constructType(String.class, String.class));
        assertNotNull(tf.constructType(String.class, tf.constructType(String.class)));
    }

    @Test
    public void testConstructArrayType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        ArrayType at1 = tf.constructArrayType(String.class);
        assertNotNull(at1);
        assertEquals(String[].class, at1.getRawClass());

        ArrayType at2 = tf.constructArrayType(tf.constructType(String.class));
        assertNotNull(at2);
        assertEquals(String[].class, at2.getRawClass());
    }

    @Test
    public void testConstructCollectionType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        CollectionType ct1 = tf.constructCollectionType(ArrayList.class, String.class);
        assertNotNull(ct1);
        assertEquals(ArrayList.class, ct1.getRawClass());

        CollectionType ct2 = tf.constructCollectionType(ArrayList.class, tf.constructType(String.class));
        assertNotNull(ct2);
        assertEquals(ArrayList.class, ct2.getRawClass());
    }

    @Test
    public void testConstructCollectionLikeType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        CollectionLikeType clt1 = tf.constructCollectionLikeType(Collection.class, String.class);
        assertNotNull(clt1);

        CollectionLikeType clt2 = tf.constructCollectionLikeType(Collection.class, tf.constructType(String.class));
        assertNotNull(clt2);
    }

    @Test
    public void testConstructMapType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        MapType mt1 = tf.constructMapType(HashMap.class, String.class, Integer.class);
        assertNotNull(mt1);

        MapType mtProps = tf.constructMapType(Properties.class, String.class, Integer.class);
        assertNotNull(mtProps);

        MapType mt2 = tf.constructMapType(HashMap.class, tf.constructType(String.class), tf.constructType(Integer.class));
        assertNotNull(mt2);
    }

    @Test
    public void testConstructMapLikeType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        MapLikeType mlt1 = tf.constructMapLikeType(Map.class, String.class, Integer.class);
        assertNotNull(mlt1);

        MapLikeType mlt2 = tf.constructMapLikeType(Map.class, tf.constructType(String.class), tf.constructType(Integer.class));
        assertNotNull(mlt2);
    }

    @Test
    public void testConstructSimpleType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType st1 = tf.constructSimpleType(ArrayList.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(st1);

        JavaType st2 = tf.constructSimpleType(ArrayList.class, ArrayList.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(st2);
    }

    @Test
    public void testConstructReferenceType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType rt = tf.constructReferenceType(AtomicReference.class, tf.constructType(String.class));
        assertNotNull(rt);
    }

    @Test
    public void testUncheckedSimpleType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType ust = tf.uncheckedSimpleType(String.class);
        assertNotNull(ust);
        assertEquals(String.class, ust.getRawClass());
    }

    @Test
    public void testConstructParametricType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType pt1 = tf.constructParametricType(ArrayList.class, new Class<?>[] { String.class });
        assertNotNull(pt1);

        JavaType pt2 = tf.constructParametricType(ArrayList.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(pt2);

        JavaType pt3 = tf.constructParametrizedType(ArrayList.class, List.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(pt3);

        JavaType pt4 = tf.constructParametrizedType(ArrayList.class, List.class, new Class<?>[] { String.class });
        assertNotNull(pt4);
    }

    @Test
    public void testConstructRawTypes() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertNotNull(tf.constructRawCollectionType(ArrayList.class));
        assertNotNull(tf.constructRawCollectionLikeType(Collection.class));
        assertNotNull(tf.constructRawMapType(HashMap.class));
        assertNotNull(tf.constructRawMapLikeType(Map.class));
    }

    @Test
    public void testPrimitiveTypesAndWellKnown() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertNotNull(tf.constructType(boolean.TYPE));
        assertNotNull(tf.constructType(int.TYPE));
        assertNotNull(tf.constructType(long.TYPE));
        assertNotNull(tf.constructType(float.TYPE));
        assertNotNull(tf.constructType(double.TYPE));
        assertNotNull(tf.constructType(byte.TYPE));
        assertNotNull(tf.constructType(char.TYPE));
        assertNotNull(tf.constructType(short.TYPE));
        assertNotNull(tf.constructType(void.TYPE));
        assertNotNull(tf.constructType(Enum.class));
        assertNotNull(tf.constructType(Comparable.class));
        assertNotNull(tf.constructType(Class.class));
    }

    @Test
    public void testUnrecognizedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        try {
            tf.constructType((Type) new Type() {});
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unrecognized Type"));
        }
    }
}