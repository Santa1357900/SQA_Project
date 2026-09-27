package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;

public class TypeFactoryTest {

    @Test
    public void testDefaultInstance() throws Throwable {
        TypeFactory tf1 = TypeFactory.defaultInstance();
        TypeFactory tf2 = TypeFactory.defaultInstance();
        assertNotNull(tf1);
        assertEquals(tf1, tf2);
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
    public void testUnknownType() throws Throwable {
        JavaType unknown = TypeFactory.unknownType();
        assertNotNull(unknown);
        assertEquals(Object.class, unknown.getRawClass());
    }

    @Test
    public void testRawClass() throws Throwable {
        Class<?> raw = TypeFactory.rawClass(String.class);
        assertEquals(String.class, raw);

        Class<?> rawFromType = TypeFactory.rawClass(new TypeReference<List<String>>() {}.getType());
        assertEquals(List.class, rawFromType);
    }

    @Test
    public void testConstructSpecializedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Map.class);
        JavaType specialized = tf.constructSpecializedType(baseType, HashMap.class);
        assertNotNull(specialized);
        assertEquals(HashMap.class, specialized.getRawClass());

        // Same class optimization
        JavaType same = tf.constructSpecializedType(baseType, Map.class);
        assertEquals(baseType, same);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructSpecializedTypeInvalid() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(Map.class);
        tf.constructSpecializedType(baseType, ArrayList.class);
    }

    @Test
    public void testConstructFromCanonical() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType type = tf.constructFromCanonical("java.lang.String");
        assertNotNull(type);
        assertEquals(String.class, type.getRawClass());
    }

    @Test
    public void testFindTypeParameters() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType mapType = tf.constructMapType(HashMap.class, String.class, Integer.class);
        JavaType[] params = tf.findTypeParameters(mapType, Map.class);
        assertNotNull(params);
        assertEquals(2, params.length);
        assertEquals(String.class, params[0].getRawClass());
        assertEquals(Integer.class, params[1].getRawClass());

        JavaType[] paramsByClass = tf.findTypeParameters(HashMap.class, Map.class);
        assertNotNull(paramsByClass);

        JavaType[] paramsDirect = tf.findTypeParameters(mapType, HashMap.class);
        assertNotNull(paramsDirect);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindTypeParametersInvalid() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        tf.findTypeParameters(ArrayList.class, Map.class);
    }

    @Test
    public void testMoreSpecificType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType stringType = tf.constructType(String.class);
        JavaType objectType = tf.constructType(Object.class);

        assertEquals(stringType, tf.moreSpecificType(objectType, stringType));
        assertEquals(stringType, tf.moreSpecificType(stringType, objectType));
        assertEquals(stringType, tf.moreSpecificType(stringType, null));
        assertEquals(stringType, tf.moreSpecificType(null, stringType));
        assertNull(tf.moreSpecificType(null, null));
        assertEquals(stringType, tf.moreSpecificType(stringType, stringType));
    }

    @Test
    public void testConstructTypeVariations() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        
        assertNotNull(tf.constructType((Type) String.class));
        assertNotNull(tf.constructType(String.class, (Class<?>) null));
        assertNotNull(tf.constructType(String.class, String.class));
        assertNotNull(tf.constructType(String.class, tf.constructType(String.class)));
        assertNotNull(tf.constructType((JavaType) null)); // covered via TypeReference or similar if applicable, but let's test TypeReference:
        assertNotNull(tf.constructType(new TypeReference<List<String>>() {}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructTypeUnrecognized() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        Type weirdType = new Type() {};
        tf.constructType(weirdType);
    }

    @Test
    public void testDirectArrayAndCollectionConstructors() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        
        assertNotNull(tf.constructArrayType(String.class));
        assertNotNull(tf.constructArrayType(tf.constructType(String.class)));

        assertNotNull(tf.constructCollectionType(ArrayList.class, String.class));
        assertNotNull(tf.constructCollectionType(ArrayList.class, tf.constructType(String.class)));

        assertNotNull(tf.constructCollectionLikeType(Collection.class, String.class));
        assertNotNull(tf.constructCollectionLikeType(Collection.class, tf.constructType(String.class)));

        assertNotNull(tf.constructMapType(HashMap.class, String.class, Integer.class));
        assertNotNull(tf.constructMapType(HashMap.class, tf.constructType(String.class), tf.constructType(Integer.class)));

        assertNotNull(tf.constructMapLikeType(Map.class, String.class, Integer.class));
        assertNotNull(tf.constructMapLikeType(Map.class, tf.constructType(String.class), tf.constructType(Integer.class)));
    }

    @Test
    public void testConstructSimpleType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType st = tf.constructSimpleType(List.class, List.class, new JavaType[] { tf.constructType(String.class) });
        assertNotNull(st);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructSimpleTypeMismatch() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        tf.constructSimpleType(List.class, List.class, new JavaType[0]);
    }

    @Test
    public void testConstructReferenceType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType ref = tf.constructReferenceType(AtomicReference.class, tf.constructType(String.class));
        assertNotNull(ref);
    }

    @Test
    public void testUncheckedSimpleType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType ust = tf.uncheckedSimpleType(String.class);
        assertNotNull(ust);
    }

    @Test
    public void testConstructParametrizedType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        
        JavaType p1 = tf.constructParametrizedType(ArrayList.class, List.class, String.class);
        assertNotNull(p1);

        JavaType p2 = tf.constructParametricType(ArrayList.class, String.class);
        assertNotNull(p2);

        JavaType p3 = tf.constructParametrizedType(ArrayList.class, List.class, tf.constructType(String.class));
        assertNotNull(p3);

        JavaType p4 = tf.constructParametricType(ArrayList.class, tf.constructType(String.class));
        assertNotNull(p4);

        JavaType mapP = tf.constructParametrizedType(HashMap.class, Map.class, String.class, Integer.class);
        assertNotNull(mapP);

        JavaType arrayP = tf.constructParametrizedType(String[].class, String[].class, String.class);
        assertNotNull(arrayP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructParametrizedTypeArrayError() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        tf.constructParametrizedType(String[].class, String[].class, new JavaType[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructParametrizedTypeMapError() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        tf.constructParametrizedType(HashMap.class, Map.class, new JavaType[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructParametrizedTypeCollectionError() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        tf.constructParametrizedType(ArrayList.class, List.class, new JavaType[0]);
    }

    @Test
    public void testRawConstructors() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertNotNull(tf.constructRawCollectionType(ArrayList.class));
        assertNotNull(tf.constructRawCollectionLikeType(Collection.class));
        assertNotNull(tf.constructRawMapType(HashMap.class));
        assertNotNull(tf.constructRawMapLikeType(Map.class));
    }

    @Test
    public void testWithModifier() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        TypeFactory tfModified = tf.withModifier(null);
        assertNotNull(tfModified);

        TypeFactory tfModified2 = tf.withModifier(new TypeModifier() {
            public JavaType modifyType(JavaType annotated, Type actualType, TypeBindings bindings, TypeFactory typeFactory) {
                return annotated;
            }
            public String toString() { return "Dummy"; }
        });
        assertNotNull(tfModified2);

        TypeFactory tfModified3 = tfModified2.withModifier(new TypeModifier() {
            public JavaType modifyType(JavaType annotated, Type actualType, TypeBindings bindings, TypeFactory typeFactory) {
                return annotated;
            }
            public String toString() { return "Dummy2"; }
        });
        assertNotNull(tfModified3);
    }

    @Test
    public void testInternalTypeResolutions() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        
        // Test primitives and special classes via constructType
        assertNotNull(tf.constructType(boolean.class));
        assertNotNull(tf.constructType(int.class));
        assertNotNull(tf.constructType(long.class));
        assertNotNull(tf.constructType(Thread.State.class)); // enum
        assertNotNull(tf.constructType(AtomicReference.class));
        assertNotNull(tf.constructType(new TypeReference<AtomicReference<String>>() {}.getType()));
        assertNotNull(tf.constructType(new TypeReference<Map.Entry<String, String>>() {}.getType()));
    }
}