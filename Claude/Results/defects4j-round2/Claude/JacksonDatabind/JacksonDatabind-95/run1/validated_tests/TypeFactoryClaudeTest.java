package com.fasterxml.jackson.databind.type;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.util.LRUMap;

public class TypeFactoryClaudeTest
{
    public static class ListHolder {
        public List<String> stringList;
    }

    // defaultInstance() must always return the same shared singleton
    @Test
    public void testDefaultInstance_returnsSameSingletonInstance() throws Throwable {
        TypeFactory a = TypeFactory.defaultInstance();
        TypeFactory b = TypeFactory.defaultInstance();
        assertSame(a, b);
    }

    // withModifier(null) branch clears modifiers/cache and returns a new instance
    @Test
    public void testWithModifierNull_returnsDifferentInstance() throws Throwable {
        TypeFactory base = TypeFactory.defaultInstance();
        TypeFactory modified = base.withModifier(null);
        assertNotSame(base, modified);
        assertNull(modified.getClassLoader());
    }

    // withClassLoader sets the classloader field, reflected by getClassLoader()
    @Test
    public void testWithClassLoader_setsGivenClassLoader() throws Throwable {
        ClassLoader cl = this.getClass().getClassLoader();
        TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(cl);
        assertSame(cl, tf.getClassLoader());
    }

    // withCache returns a usable new instance that still constructs types correctly
    @Test
    public void testWithCache_newInstanceStillConstructsTypesCorrectly() throws Throwable {
        LRUMap<Object, JavaType> cache = new LRUMap<Object, JavaType>(16, 200);
        TypeFactory tf = TypeFactory.defaultInstance().withCache(cache);
        JavaType t = tf.constructType(String.class);
        assertEquals(String.class, t.getRawClass());
    }

    // clearCache should not throw and factory remains usable afterwards
    @Test
    public void testClearCache_doesNotThrowAndCacheStillUsable() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        tf.clearCache();
        JavaType t = tf.constructType(Integer.TYPE);
        assertEquals(Integer.TYPE, t.getRawClass());
    }

    // default instance has no classloader configured
    @Test
    public void testGetClassLoader_defaultInstanceIsNull() throws Throwable {
        assertNull(TypeFactory.defaultInstance().getClassLoader());
    }

    // unknownType() is documented as equivalent to Object.class simple type
    @Test
    public void testUnknownType_isObjectSimpleType() throws Throwable {
        JavaType t = TypeFactory.unknownType();
        assertEquals(Object.class, t.getRawClass());
    }

    // rawClass(Type) with a Class instance returns it directly (Class branch)
    @Test
    public void testRawClass_withClass_returnsSameClass() throws Throwable {
        assertEquals(String.class, TypeFactory.rawClass(String.class));
    }

    // rawClass(Type) with a non-Class Type resolves via constructType (else branch)
    @Test
    public void testRawClass_withParameterizedType_returnsRawClass() throws Throwable {
        Field f = ListHolder.class.getField("stringList");
        Type genericType = f.getGenericType();
        assertEquals(List.class, TypeFactory.rawClass(genericType));
    }

    // findClass resolves primitive type names without dotted package name
    @Test
    public void testFindClass_primitiveNames() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        assertEquals(Integer.TYPE, tf.findClass("int"));
        assertEquals(Boolean.TYPE, tf.findClass("boolean"));
    }

    // findClass resolves a fully-qualified class name via classloader lookup
    @Test
    public void testFindClass_fullyQualifiedClassName() throws Throwable {
        Class<?> c = TypeFactory.defaultInstance().findClass("java.lang.String");
        assertEquals(String.class, c);
    }

    // findClass throws ClassNotFoundException for unknown name (exception path)
    @Test
    public void testFindClass_unknownClassName_throwsClassNotFoundException() throws Throwable {
        try {
            TypeFactory.defaultInstance().findClass("no.such.ClassXyz123");
            fail("expected ClassNotFoundException");
        } catch (ClassNotFoundException expected) { }
    }

    // constructSpecializedType: rawBase == subclass short-circuit returns same instance
    @Test
    public void testConstructSpecializedType_sameRawType_returnsSameInstance() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(String.class);
        JavaType same = tf.constructSpecializedType(baseType, String.class);
        assertSame(baseType, same);
    }

    // constructSpecializedType: collection-like shortcut resolves content type for ArrayList
    @Test
    public void testConstructSpecializedType_listToArrayList_resolvesContentType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructCollectionType(List.class, String.class);
        JavaType specialized = tf.constructSpecializedType(baseType, ArrayList.class);
        assertEquals(ArrayList.class, specialized.getRawClass());
        assertEquals(String.class, specialized.getContentType().getRawClass());
    }

    // constructSpecializedType: map-like shortcut resolves key/value types for HashMap
    @Test
    public void testConstructSpecializedType_mapToHashMap_resolvesKeyValueTypes() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructMapType(Map.class, String.class, Integer.class);
        JavaType specialized = tf.constructSpecializedType(baseType, HashMap.class);
        assertEquals(HashMap.class, specialized.getRawClass());
        assertEquals(String.class, specialized.getKeyType().getRawClass());
        assertEquals(Integer.class, specialized.getContentType().getRawClass());
    }

    // constructSpecializedType: subclass not assignable to base raw class throws IllegalArgumentException
    @Test
    public void testConstructSpecializedType_incompatibleSubclass_throwsIllegalArgumentException() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(String.class);
        try {
            tf.constructSpecializedType(baseType, Integer.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // constructGeneralizedType: rawBase == superClass short-circuit returns same instance
    @Test
    public void testConstructGeneralizedType_sameRawType_returnsSameInstance() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructType(String.class);
        JavaType same = tf.constructGeneralizedType(baseType, String.class);
        assertSame(baseType, same);
    }

    // constructGeneralizedType: finds super type List<String> from ArrayList<String>
    @Test
    public void testConstructGeneralizedType_arrayListToList_resolvesContentType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructCollectionType(ArrayList.class, String.class);
        JavaType general = tf.constructGeneralizedType(baseType, List.class);
        assertEquals(List.class, general.getRawClass());
        assertEquals(String.class, general.getContentType().getRawClass());
    }

    // constructGeneralizedType: unrelated superclass throws IllegalArgumentException
    @Test
    public void testConstructGeneralizedType_unrelatedSuperclass_throwsIllegalArgumentException() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType baseType = tf.constructCollectionType(ArrayList.class, String.class);
        try {
            tf.constructGeneralizedType(baseType, Map.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // constructFromCanonical parses a plain class canonical name
    @Test
    public void testConstructFromCanonical_simpleStringType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructFromCanonical("java.lang.String");
        assertEquals(String.class, t.getRawClass());
    }

    // constructFromCanonical parses a parameterized generic canonical name
    @Test
    public void testConstructFromCanonical_parameterizedListType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructFromCanonical("java.util.List<java.lang.String>");
        assertEquals(List.class, t.getRawClass());
        assertEquals(String.class, t.getContentType().getRawClass());
    }

    // constructFromCanonical throws IllegalArgumentException for unresolvable class name
    @Test
    public void testConstructFromCanonical_unknownClass_throwsIllegalArgumentException() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        try {
            tf.constructFromCanonical("com.totally.bogus.NoSuchClass123");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // findTypeParameters resolves element type when supertype matches
    @Test
    public void testFindTypeParameters_collectionSubtype_returnsElementType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructCollectionType(ArrayList.class, String.class);
        JavaType[] params = tf.findTypeParameters(t, java.util.Collection.class);
        assertEquals(1, params.length);
        assertEquals(String.class, params[0].getRawClass());
    }

    // findTypeParameters returns empty array (NO_TYPES) when no matching supertype exists
    @Test
    public void testFindTypeParameters_unrelatedType_returnsEmptyArray() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(String.class);
        JavaType[] none = tf.findTypeParameters(t, List.class);
        assertEquals(0, none.length);
    }

    // moreSpecificType: null handling for either argument
    @Test
    public void testMoreSpecificType_nullHandling() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType a = tf.constructType(String.class);
        assertSame(a, tf.moreSpecificType(null, a));
        assertSame(a, tf.moreSpecificType(a, null));
    }

    // moreSpecificType: related types return the more specific (sub) type
    @Test
    public void testMoreSpecificType_relatedTypes_returnsSubtype() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType obj = tf.constructType(Object.class);
        JavaType str = tf.constructType(String.class);
        JavaType more = tf.moreSpecificType(obj, str);
        assertEquals(String.class, more.getRawClass());
    }

    // moreSpecificType: unrelated types simply return the first (primary) type
    @Test
    public void testMoreSpecificType_unrelatedTypes_returnsFirstType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType intType = tf.constructType(Integer.class);
        JavaType strType = tf.constructType(String.class);
        JavaType result = tf.moreSpecificType(intType, strType);
        assertEquals(Integer.class, result.getRawClass());
    }

    // constructType(Class) resolves primitive int to the well-known primitive type
    @Test
    public void testConstructType_primitiveIntClass() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(Integer.TYPE);
        assertEquals(Integer.TYPE, t.getRawClass());
        assertTrue(t.getRawClass().isPrimitive());
    }

    // constructType(TypeReference) resolves generic parameter via anonymous subclass
    @Test
    public void testConstructType_typeReferenceResolvesGenericList() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructType(new TypeReference<List<String>>() { });
        assertEquals(List.class, t.getRawClass());
        assertEquals(String.class, t.getContentType().getRawClass());
    }

    // constructArrayType(Class) builds array type with correct component/content type
    @Test
    public void testConstructArrayType_fromPrimitiveClass() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType at = tf.constructArrayType(Integer.TYPE);
        assertEquals(int[].class, at.getRawClass());
        assertEquals(Integer.TYPE, at.getContentType().getRawClass());
    }

    // constructArrayType(JavaType) builds array type from already-resolved element type
    @Test
    public void testConstructArrayType_fromJavaType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType elem = tf.constructType(String.class);
        JavaType at = tf.constructArrayType(elem);
        assertEquals(String[].class, at.getRawClass());
        assertEquals(String.class, at.getContentType().getRawClass());
    }

    // constructCollectionType(Class,Class) resolves element class into content type
    @Test
    public void testConstructCollectionType_fromClassPair() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructCollectionType(ArrayList.class, String.class);
        assertEquals(ArrayList.class, t.getRawClass());
        assertEquals(String.class, t.getContentType().getRawClass());
    }

    // constructCollectionType(Class,JavaType) accepts pre-resolved element JavaType
    @Test
    public void testConstructCollectionType_fromJavaTypeContent() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType elem = tf.constructType(Integer.class);
        JavaType t = tf.constructCollectionType(List.class, elem);
        assertEquals(List.class, t.getRawClass());
        assertEquals(Integer.class, t.getContentType().getRawClass());
    }

    // constructCollectionLikeType resolves content type similarly to constructCollectionType
    @Test
    public void testConstructCollectionLikeType_fromClasses() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructCollectionLikeType(ArrayList.class, String.class);
        assertEquals(String.class, t.getContentType().getRawClass());
    }

    // constructMapType(Class,Class,Class) resolves both key and value types normally
    @Test
    public void testConstructMapType_stringIntegerMap() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructMapType(HashMap.class, String.class, Integer.class);
        assertEquals(HashMap.class, t.getRawClass());
        assertEquals(String.class, t.getKeyType().getRawClass());
        assertEquals(Integer.class, t.getContentType().getRawClass());
    }



    // constructMapLikeType(Class,JavaType,JavaType) resolves key/value from pre-built JavaTypes
    @Test
    public void testConstructMapLikeType_fromJavaTypes() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType kt = tf.constructType(String.class);
        JavaType vt = tf.constructType(Integer.class);
        JavaType t = tf.constructMapLikeType(HashMap.class, kt, vt);
        assertEquals(String.class, t.getKeyType().getRawClass());
        assertEquals(Integer.class, t.getContentType().getRawClass());
    }

    // constructSimpleType with no type parameters yields well-known simple type
    @Test
    public void testConstructSimpleType_noParameters() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructSimpleType(String.class, new JavaType[0]);
        assertEquals(String.class, t.getRawClass());
    }

    // constructReferenceType wraps referenced type accessible via getContentType()
    @Test
    public void testConstructReferenceType_atomicReferenceOfString() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);
        JavaType t = tf.constructReferenceType(AtomicReference.class, strType);
        assertEquals(AtomicReference.class, t.getRawClass());
        assertEquals(String.class, t.getContentType().getRawClass());
    }

    // constructParametricType(Class, Class...) resolves parameter classes into content type
    @Test
    public void testConstructParametricType_classesVarargs() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructParametricType(List.class, String.class);
        assertEquals(List.class, t.getRawClass());
        assertEquals(String.class, t.getContentType().getRawClass());
    }

    // constructParametricType(Class, JavaType...) resolves pre-built parameter JavaType
    @Test
    public void testConstructParametricType_javaTypeVarargs() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType strType = tf.constructType(String.class);
        JavaType t = tf.constructParametricType(ArrayList.class, strType);
        assertEquals(ArrayList.class, t.getRawClass());
        assertEquals(String.class, t.getContentType().getRawClass());
    }

    // constructRawCollectionType uses unknownType() as content type (Object)
    @Test
    public void testConstructRawCollectionType_hasUnknownContentType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructRawCollectionType(ArrayList.class);
        assertEquals(Object.class, t.getContentType().getRawClass());
    }

    // constructRawMapType uses unknownType() for both key and value types
    @Test
    public void testConstructRawMapType_hasUnknownKeyAndValueTypes() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType t = tf.constructRawMapType(HashMap.class);
        assertEquals(Object.class, t.getKeyType().getRawClass());
        assertEquals(Object.class, t.getContentType().getRawClass());
    }
}
