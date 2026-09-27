package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.util.LRUMap;

public class TypeFactoryTest {

    @Test
    public void testDefaultInstanceAndCache() throws Throwable {
        TypeFactory tf1 = TypeFactory.defaultInstance();
        assertNotNull(tf1);

        TypeFactory tf2 = tf1.withCache(new LRUMap<Object, JavaType>(8, 100));
        assertNotNull(tf2);
        
        tf2.clearCache();

        TypeFactory tf3 = tf1.withModifier(null);
        assertNotNull(tf3);

        TypeFactory tf4 = tf1.withClassLoader(Thread.currentThread().getContextClassLoader());
        assertNotNull(tf4);
        assertNotNull(tf4.getClassLoader());
    }

    @Test
    public void testConstructPrimitiveTypes() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        
        JavaType boolType = tf.constructType(Boolean.TYPE);
        assertNotNull(boolType);
        assertTrue(boolType.isPrimitive());

        JavaType intType = tf.constructType(Integer.TYPE);
        assertNotNull(intType);
        assertTrue(intType.isPrimitive());

        JavaType longType = tf.constructType(Long.TYPE);
        assertNotNull(longType);
        assertTrue(longType.isPrimitive());

        JavaType stringType = tf.constructType(String.class);
        assertNotNull(stringType);
        assertEquals(String.class, stringType.getRawClass());

        JavaType objectType = tf.constructType(Object.class);
        assertNotNull(objectType);
        assertEquals(Object.class, objectType.getRawClass());

        JavaType compType = tf.constructType(Comparable.class);
        assertNotNull(compType);

        JavaType enumType = tf.constructType(Enum.class);
        assertNotNull(enumType);

        JavaType classType = tf.constructType(Class.class);
        assertNotNull(classType);
    }

    @Test
    public void testConstructArrayAndCollections() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        ArrayType arrType1 = tf.constructArrayType(String.class);
        assertNotNull(arrType1);
        assertTrue(arrType1.isArrayType());

        ArrayType arrType2 = tf.constructArrayType(tf.constructType(Integer.class));
        assertNotNull(arrType2);
        assertTrue(arrType2.isArrayType());

        CollectionType colType1 = tf.constructCollectionType(List.class, String.class);
        assertNotNull(colType1);
        assertTrue(colType1.isContainerType());

        CollectionType colType2 = tf.constructCollectionType(ArrayList.class, tf.constructType(Integer.class));
        assertNotNull(colType2);

        CollectionLikeType colLike1 = tf.constructCollectionLikeType(Collection.class, String.class);
        assertNotNull(colLike1);

        CollectionLikeType colLike2 = tf.constructCollectionLikeType(Collection.class, tf.constructType(String.class));
        assertNotNull(colLike2);

        CollectionType rawCol = tf.constructRawCollectionType(ArrayList.class);
        assertNotNull(rawCol);

        CollectionLikeType rawColLike = tf.constructRawCollectionLikeType(Collection.class);
        assertNotNull(rawColLike);
    }

    @Test
    public void testConstructMapsAndProperties() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        MapType mapType1 = tf.constructMapType(Map.class, String.class, Integer.class);
        assertNotNull(mapType1);
        assertTrue(mapType1.isMapLikeType());

        MapType propMap = tf.constructMapType(Properties.class, String.class, String.class);
        assertNotNull(propMap);

        MapType mapType2 = tf.constructMapType(HashMap.class, tf.constructType(String.class), tf.constructType(Object.class));
        assertNotNull(mapType2);

        MapLikeType mapLike1 = tf.constructMapLikeType(Map.class, String.class, Object.class);
        assertNotNull(mapLike1);

        MapLikeType mapLike2 = tf.constructMapLikeType(Map.class, tf.constructType(String.class), tf.constructType(Object.class));
        assertNotNull(mapLike2);

        MapType rawMap = tf.constructRawMapType(HashMap.class);
        assertNotNull(rawMap);

        MapLikeType rawMapLike = tf.constructRawMapLikeType(Map.class);
        assertNotNull(rawMapLike);
    }

    @Test
    public void testSpecialConstructorsAndHelpers() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        JavaType simple = tf.constructSimpleType(List.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(simple);

        @SuppressWarnings("deprecation")
        JavaType simpleDeprecated = tf.constructSimpleType(List.class, List.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(simpleDeprecated);

        JavaType refType = tf.constructReferenceType(AtomicReference.class, tf.constructType(String.class));
        assertNotNull(refType);

        @SuppressWarnings("deprecation")
        JavaType uncheck = tf.uncheckedSimpleType(String.class);
        assertNotNull(uncheck);

        JavaType paramType = tf.constructParametricType(List.class, String.class);
        assertNotNull(paramType);

        JavaType paramType2 = tf.constructParametricType(List.class, tf.constructType(String.class));
        assertNotNull(paramType2);

        JavaType paramed1 = tf.constructParametrizedType(List.class, Collection.class, tf.constructType(String.class));
        assertNotNull(paramed1);

        JavaType paramed2 = tf.constructParametrizedType(List.class, Collection.class, String.class);
        assertNotNull(paramed2);

        JavaType unknown = TypeFactory.unknownType();
        assertNotNull(unknown);

        Class<?> raw = TypeFactory.rawClass(String.class);
        assertEquals(String.class, raw);

        Class<?> raw2 = TypeFactory.rawClass(tf.constructType(Integer.class));
        assertEquals(Integer.TYPE, raw2);
    }

    @Test
    public void testFindClassAndPrimitives() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        Class<?> c1 = tf.findClass("java.lang.String");
        assertEquals(String.class, c1);

        Class<?> c2 = tf.findClass("int");
        assertEquals(Integer.TYPE, c2);

        Class<?> c3 = tf.findClass("long");
        assertEquals(Long.TYPE, c3);

        Class<?> c4 = tf.findClass("float");
        assertEquals(Float.TYPE, c4);

        Class<?> c5 = tf.findClass("double");
        assertEquals(Double.TYPE, c5);

        Class<?> c6 = tf.findClass("boolean");
        assertEquals(Boolean.TYPE, c6);

        Class<?> c7 = tf.findClass("byte");
        assertEquals(Byte.TYPE, c7);

        Class<?> c8 = tf.findClass("char");
        assertEquals(Character.TYPE, c8);

        Class<?> c9 = tf.findClass("short");
        assertEquals(Short.TYPE, c9);

        Class<?> c10 = tf.findClass("void");
        assertEquals(Void.TYPE, c10);

        try {
            tf.findClass("non.existent.ClassNameXYZ");
            fail("Should have thrown ClassNotFoundException");
        } catch (ClassNotFoundException e) {
            // expected
        }
    }

    @Test
    public void testSpecializedAndGeneralizedTypes() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        JavaType baseMap = tf.constructType(Map.class);
        JavaType specMap = tf.constructSpecializedType(baseMap, HashMap.class);
        assertNotNull(specMap);

        JavaType baseObj = tf.constructType(Object.class);
        JavaType specObj = tf.constructSpecializedType(baseObj, String.class);
        assertNotNull(specObj);

        JavaType listType = tf.constructCollectionType(ArrayList.class, String.class);
        JavaType specList = tf.constructSpecializedType(listType, ArrayList.class);
        assertNotNull(specList);

        JavaType sameList = tf.constructSpecializedType(listType, ArrayList.class);
        assertNotNull(sameList);

        JavaType enumSetType = tf.constructType(EnumSet.class);
        JavaType specEnumSet = tf.constructSpecializedType(enumSetType, EnumSet.class);
        assertNotNull(specEnumSet);

        JavaType genType = tf.constructGeneralizedType(listType, Collection.class);
        assertNotNull(genType);

        JavaType sameGen = tf.constructGeneralizedType(listType, ArrayList.class);
        assertNotNull(sameGen);

        try {
            tf.constructSpecializedType(baseMap, String.class);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            tf.constructGeneralizedType(listType, Integer.class);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testCanonicalAndTypeReference() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        JavaType fromCanon = tf.constructFromCanonical("java.lang.String");
        assertNotNull(fromCanon);
        assertEquals(String.class, fromCanon.getRawClass());

        JavaType fromRef = tf.constructType(new TypeReference<List<String>>() {});
        assertNotNull(fromRef);
        assertTrue(fromRef.isContainerType());

        JavaType withContextClass = tf.constructType(String.class, Object.class);
        assertNotNull(withContextClass);

        JavaType withContextJavaType = tf.constructType(String.class, tf.constructType(Object.class));
        assertNotNull(withContextJavaType);

        JavaType withNullContextJavaType = tf.constructType(String.class, (JavaType) null);
        assertNotNull(withNullContextJavaType);
    }

    @Test
    public void testMoreSpecificType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        JavaType tStr = tf.constructType(String.class);
        JavaType tObj = tf.constructType(Object.class);

        assertEquals(tStr, tf.moreSpecificType(null, tStr));
        assertEquals(tStr, tf.moreSpecificType(tStr, null));
        assertEquals(tStr, tf.moreSpecificType(tStr, tStr));
        assertEquals(tStr, tf.moreSpecificType(tObj, tStr));
        assertEquals(tStr, tf.moreSpecificType(tStr, tObj));
    }

    @Test
    public void testFindTypeParameters() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();

        JavaType listType = tf.constructCollectionType(ArrayList.class, String.class);
        JavaType[] params = tf.findTypeParameters(listType, Collection.class);
        assertNotNull(params);
        assertEquals(1, params.length);

        JavaType notFound = tf.constructType(String.class);
        JavaType[] emptyParams = tf.findTypeParameters(notFound, Map.class);
        assertNotNull(emptyParams);
        assertEquals(0, emptyParams.length);

        @SuppressWarnings("deprecation")
        JavaType[] paramsDeprecated1 = tf.findTypeParameters(ArrayList.class, Collection.class);
        assertNotNull(paramsDeprecated1);

        @SuppressWarnings("deprecation")
        JavaType[] paramsDeprecated2 = tf.findTypeParameters(ArrayList.class, Collection.class, TypeFactory.EMPTY_BINDINGS);
        assertNotNull(paramsDeprecated2);
    }

    @Test
    public void testModifierHandling() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        TypeModifier dummyMod = new TypeModifier() {
            @Override
            public JavaType modifyType(JavaType type, Type jdkType, TypeBindings bindings, TypeFactory typeFactory) {
                return type;
            }
            @Override
            public String toString() {
                return "DummyModifier";
            }
        };

        TypeFactory tfModified = tf.withModifier(dummyMod);
        assertNotNull(tfModified);
        
        JavaType modifiedResult = tfModified.constructType(String.class);
        assertNotNull(modifiedResult);

        TypeModifier nullReturningMod = new TypeModifier() {
            @Override
            public JavaType modifyType(JavaType type, Type jdkType, TypeBindings bindings, TypeFactory typeFactory) {
                return null;
            }
        };

        TypeFactory tfBadModified = tf.withModifier(nullReturningMod);
        try {
            tfBadModified.constructType(String.class);
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }
    }
}