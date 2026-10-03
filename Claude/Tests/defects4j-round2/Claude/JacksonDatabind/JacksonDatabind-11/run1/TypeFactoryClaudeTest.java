package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.lang.reflect.Type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;

public class TypeFactoryClaudeTest {

    private TypeFactory factory;

    public static class Box<T> { }
    public static class StringBox extends Box<String> { }
    public static class StringList extends ArrayList<String> { }
    public static class GenericHolder<T> {
        public T[] arr;
        public List<? extends Number> wildcardList;
    }

    @Before
    public void setUp() throws Throwable {
        // fresh instance with its own empty type cache, avoids cross-test cache sharing
        factory = TypeFactory.defaultInstance().withModifier(null);
    }

    // covers defaultInstance() returning the globally shared singleton
    @Test
    public void testDefaultInstance_returnsSameSingletonAcrossCalls() throws Throwable {
        TypeFactory f1 = TypeFactory.defaultInstance();
        TypeFactory f2 = TypeFactory.defaultInstance();
        assertSame(f1, f2);
    }

    // covers withModifier(null) branch creating a new but functional TypeFactory instance
    @Test
    public void testWithModifier_null_returnsNewFunctionalInstance() throws Throwable {
        TypeFactory base = TypeFactory.defaultInstance();
        TypeFactory copy = base.withModifier(null);
        assertNotSame(base, copy);
        assertEquals(String.class, copy.constructType(String.class).getRawClass());
    }

    // covers clearCache() emptying _typeCache so a new instance gets created afterwards
    @Test
    public void testClearCache_afterClear_returnsFreshInstance() throws Throwable {
        JavaType a = factory.constructType(Number.class);
        factory.clearCache();
        JavaType b = factory.constructType(Number.class);
        assertNotSame(a, b);
        assertEquals(Number.class, b.getRawClass());
    }

    // covers static unknownType() marker for java.lang.Object
    @Test
    public void testUnknownType_returnsObjectRawClass() throws Throwable {
        JavaType t = TypeFactory.unknownType();
        assertEquals(Object.class, t.getRawClass());
    }

    // covers rawClass(Type) fast path for instanceof Class<?>
    @Test
    public void testRawClass_withClassInput_returnsSameClass() throws Throwable {
        Class<?> c = TypeFactory.rawClass(String.class);
        assertEquals(String.class, c);
    }

    // covers rawClass(Type) fallback path resolving via constructType(t).getRawClass()
    @Test
    public void testRawClass_withParameterizedTypeInput_returnsRawClass() throws Throwable {
        Type generic = StringBox.class.getGenericSuperclass();
        Class<?> c = TypeFactory.rawClass(generic);
        assertEquals(Box.class, c);
    }

    // covers constructSpecializedType fast path: baseType.getRawClass() == subclass
    @Test
    public void testConstructSpecializedType_sameRawClass_returnsSameInstance() throws Throwable {
        JavaType base = factory.constructType(String.class);
        JavaType result = factory.constructSpecializedType(base, String.class);
        assertSame(base, result);
    }

    // covers fallthrough to baseType.narrowBy(subclass) for a non array/map/collection subclass
    @Test
    public void testConstructSpecializedType_narrowingRegularType_returnsNarrowedRawClass() throws Throwable {
        JavaType base = factory.constructType(Number.class);
        JavaType result = factory.constructSpecializedType(base, Integer.class);
        assertEquals(Integer.class, result.getRawClass());
    }

    // covers SimpleType + Collection-assignable subclass success branch
    @Test
    public void testConstructSpecializedType_simpleTypeToCollectionSubclass_returnsCollectionType() throws Throwable {
        JavaType base = factory.constructType(Object.class);
        JavaType result = factory.constructSpecializedType(base, ArrayList.class);
        assertTrue(result instanceof CollectionType);
        assertEquals(ArrayList.class, result.getRawClass());
    }

    // BUG: exception message must name the actual subclass (java.util.ArrayList), not "java.lang.Class"
    @Test
    public void testConstructSpecializedType_incompatibleSubclass_throwsWithCorrectClassNameInMessage() throws Throwable {
        JavaType base = factory.constructType(Number.class);
        try {
            factory.constructSpecializedType(base, ArrayList.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("ArrayList"));
        }
    }

    // covers TypeParser happy path for a plain fully-qualified class name
    @Test
    public void testConstructFromCanonical_simpleClassName_returnsCorrectRawClass() throws Throwable {
        JavaType t = factory.constructFromCanonical("java.lang.String");
        assertEquals(String.class, t.getRawClass());
    }

    // covers malformed/unknown canonical representation throwing IllegalArgumentException
    @Test
    public void testConstructFromCanonical_invalidCanonical_throwsIllegalArgumentException() throws Throwable {
        try {
            factory.constructFromCanonical("com.example.NoSuchClassXyz");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // exception type confirms rejection of unknown class
        }
    }

    // covers direct-return branch: expType == type.getParameterSource()
    @Test
    public void testFindTypeParametersJavaType_directParameterSource_returnsContainedTypes() throws Throwable {
        JavaType strType = factory.constructType(String.class);
        JavaType paramType = factory.constructSimpleType(ArrayList.class, List.class, new JavaType[] { strType });
        JavaType[] params = factory.findTypeParameters(paramType, List.class);
        assertEquals(1, params.length);
        assertEquals(String.class, params[0].getRawClass());
    }

    // covers findTypeParameters(Class,Class) resolving List<E> for a concrete subclass
    @Test
    public void testFindTypeParametersClass_subtypeOfGeneric_returnsElementType() throws Throwable {
        JavaType[] params = factory.findTypeParameters(StringList.class, List.class);
        assertEquals(1, params.length);
        assertEquals(String.class, params[0].getRawClass());
    }

    // covers _findSuperTypeChain returning null -> IllegalArgumentException
    @Test
    public void testFindTypeParametersClass_notSubtype_throwsIllegalArgumentException() throws Throwable {
        try {
            factory.findTypeParameters(String.class, Collection.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("String"));
        }
    }

    // covers moreSpecificType(null, type2) branch
    @Test
    public void testMoreSpecificType_firstNull_returnsSecond() throws Throwable {
        JavaType t2 = factory.constructType(String.class);
        JavaType result = factory.moreSpecificType(null, t2);
        assertSame(t2, result);
    }

    // covers moreSpecificType(type1, null) branch
    @Test
    public void testMoreSpecificType_secondNull_returnsFirst() throws Throwable {
        JavaType t1 = factory.constructType(String.class);
        JavaType result = factory.moreSpecificType(t1, null);
        assertSame(t1, result);
    }

    // covers raw1 == raw2 branch
    @Test
    public void testMoreSpecificType_sameRawClass_returnsFirst() throws Throwable {
        JavaType t1 = factory.constructType(String.class);
        JavaType t2 = factory.constructType(String.class);
        JavaType result = factory.moreSpecificType(t1, t2);
        assertSame(t1, result);
    }

    // covers raw1.isAssignableFrom(raw2) -> returns the more specific type2
    @Test
    public void testMoreSpecificType_assignableSecondMoreSpecific_returnsSecond() throws Throwable {
        JavaType number = factory.constructType(Number.class);
        JavaType integer = factory.constructType(Integer.class);
        JavaType result = factory.moreSpecificType(number, integer);
        assertSame(integer, result);
    }

    // covers fallback: unrelated raw classes -> return type1
    @Test
    public void testMoreSpecificType_unrelatedTypes_returnsFirst() throws Throwable {
        JavaType str = factory.constructType(String.class);
        JavaType integer = factory.constructType(Integer.class);
        JavaType result = factory.moreSpecificType(str, integer);
        assertSame(str, result);
    }

    // covers _fromClass shared-constant caching for primitive core types
    @Test
    public void testConstructType_corePrimitiveTypes_areCachedAndSame() throws Throwable {
        JavaType a = factory.constructType(int.class);
        JavaType b = factory.constructType(int.class);
        assertSame(a, b);
        assertEquals(int.class, a.getRawClass());
    }

    // covers _typeCache hit branch for a non-core class
    @Test
    public void testConstructType_nonCoreClass_isCachedAcrossCalls() throws Throwable {
        JavaType a = factory.constructType(Number.class);
        JavaType b = factory.constructType(Number.class);
        assertSame(a, b);
    }

    // covers constructType(TypeReference<?>) and ParameterizedType->Collection resolution
    @Test
    public void testConstructType_typeReference_resolvesGenericListOfString() throws Throwable {
        TypeReference<List<String>> ref = new TypeReference<List<String>>() { };
        JavaType t = factory.constructType(ref);
        assertEquals(List.class, t.getRawClass());
        assertEquals(1, t.containedTypeCount());
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers constructType(Type, Class) and constructType(Type, JavaType) non-null context branches
    @Test
    public void testConstructType_withClassAndJavaTypeContext_resolvesSimpleType() throws Throwable {
        JavaType t1 = factory.constructType(String.class, Number.class);
        JavaType numberType = factory.constructType(Number.class);
        JavaType t2 = factory.constructType(String.class, numberType);
        assertEquals(String.class, t1.getRawClass());
        assertEquals(String.class, t2.getRawClass());
    }

    // covers _fromArrayType and _fromVariable(context==null) resolving unresolved T to Object
    @Test
    public void testConstructType_genericArrayTypeWithUnresolvedVariable_resolvesToObjectArray() throws Throwable {
        Type arrType = GenericHolder.class.getField("arr").getGenericType();
        JavaType t = factory.constructType(arrType);
        assertEquals(Object[].class, t.getRawClass());
    }

    // covers _fromWildcard using the upper bound (Number) inside a parameterized Collection
    @Test
    public void testConstructType_wildcardTypeUpperBound_resolvesToBoundType() throws Throwable {
        Type listType = GenericHolder.class.getField("wildcardList").getGenericType();
        JavaType t = factory.constructType(listType);
        assertEquals(Number.class, t.containedType(0).getRawClass());
    }

    // covers both constructArrayType(Class) and constructArrayType(JavaType) overloads
    @Test
    public void testConstructArrayType_fromClassAndFromJavaType_returnArrayTypeWithArrayRawClass() throws Throwable {
        ArrayType a1 = factory.constructArrayType(String.class);
        ArrayType a2 = factory.constructArrayType(factory.constructType(int.class));
        assertEquals(String[].class, a1.getRawClass());
        assertEquals(int[].class, a2.getRawClass());
    }

    // covers constructCollectionType(Class, Class)
    @Test
    public void testConstructCollectionType_classAndClass_returnsCorrectElementType() throws Throwable {
        CollectionType t = factory.constructCollectionType(List.class, String.class);
        assertEquals(List.class, t.getRawClass());
        assertEquals(1, t.containedTypeCount());
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers constructCollectionType(Class, JavaType)
    @Test
    public void testConstructCollectionType_classAndJavaType_returnsCorrectElementType() throws Throwable {
        JavaType elem = factory.constructType(Integer.class);
        CollectionType t = factory.constructCollectionType(List.class, elem);
        assertEquals(Integer.class, t.containedType(0).getRawClass());
    }

    // covers constructCollectionLikeType(Class, Class)
    @Test
    public void testConstructCollectionLikeType_classAndClass_returnsCorrectElementType() throws Throwable {
        CollectionLikeType t = factory.constructCollectionLikeType(List.class, String.class);
        assertEquals(1, t.containedTypeCount());
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers constructMapType(Class, JavaType, JavaType)
    @Test
    public void testConstructMapType_classAndJavaTypes_returnsCorrectKeyValueTypes() throws Throwable {
        JavaType key = factory.constructType(String.class);
        JavaType value = factory.constructType(Integer.class);
        MapType t = factory.constructMapType(HashMap.class, key, value);
        assertEquals(String.class, t.containedType(0).getRawClass());
        assertEquals(Integer.class, t.containedType(1).getRawClass());
    }

    // covers constructMapType(Class, Class, Class)
    @Test
    public void testConstructMapType_classAndClasses_returnsCorrectKeyValueTypes() throws Throwable {
        MapType t = factory.constructMapType(HashMap.class, String.class, Integer.class);
        assertEquals(String.class, t.containedType(0).getRawClass());
        assertEquals(Integer.class, t.containedType(1).getRawClass());
    }

    // covers constructMapLikeType(Class, JavaType, JavaType)
    @Test
    public void testConstructMapLikeType_classAndJavaTypes_returnsCorrectKeyValueTypes() throws Throwable {
        JavaType key = factory.constructType(String.class);
        JavaType value = factory.constructType(Integer.class);
        MapLikeType t = factory.constructMapLikeType(HashMap.class, key, value);
        assertEquals(String.class, t.containedType(0).getRawClass());
        assertEquals(Integer.class, t.containedType(1).getRawClass());
    }

    // covers constructSimpleType success path with matching parameter counts
    @Test
    public void testConstructSimpleType_matchingParamCount_returnsSimpleTypeWithParam() throws Throwable {
        JavaType strType = factory.constructType(String.class);
        JavaType t = factory.constructSimpleType(ArrayList.class, List.class, new JavaType[] { strType });
        assertEquals(ArrayList.class, t.getRawClass());
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers constructSimpleType parameter-count mismatch throwing IllegalArgumentException
    @Test
    public void testConstructSimpleType_mismatchedParamCount_throwsIllegalArgumentException() throws Throwable {
        try {
            factory.constructSimpleType(ArrayList.class, List.class, new JavaType[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // exception confirms parameter-count validation
        }
    }

    // covers uncheckedSimpleType bypassing cache/modifiers
    @Test
    public void testUncheckedSimpleType_returnsSimpleTypeIgnoringCache() throws Throwable {
        JavaType t = factory.uncheckedSimpleType(Number.class);
        assertEquals(Number.class, t.getRawClass());
    }

    // covers constructParametrizedType Collection-target branch
    @Test
    public void testConstructParametrizedType_collectionTarget_returnsCollectionType() throws Throwable {
        JavaType t = factory.constructParametrizedType(ArrayList.class, List.class, String.class);
        assertTrue(t instanceof CollectionType);
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers array-target branch requiring exactly 1 parameter type
    @Test
    public void testConstructParametrizedType_arrayTarget_wrongParamCount_throwsIllegalArgumentException() throws Throwable {
        JavaType strType = factory.constructType(String.class);
        JavaType intType = factory.constructType(Integer.class);
        try {
            factory.constructParametrizedType(String[].class, String[].class, strType, intType);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // exception confirms arity validation for array targets
        }
    }

    // covers Map-target branch requiring exactly 2 parameter types
    @Test
    public void testConstructParametrizedType_mapTarget_wrongParamCount_throwsIllegalArgumentException() throws Throwable {
        try {
            factory.constructParametrizedType(HashMap.class, Map.class, String.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // exception confirms arity validation for map targets
        }
    }

    // covers the generic "else" branch delegating to constructSimpleType
    @Test
    public void testConstructParametrizedType_genericSimpleTarget_returnsSimpleTypeWithParam() throws Throwable {
        JavaType t = factory.constructParametrizedType(Box.class, Box.class, String.class);
        assertEquals(Box.class, t.getRawClass());
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers constructRawCollectionType using unknownType() for missing parameterization
    @Test
    public void testConstructRawCollectionType_returnsUnknownElementType() throws Throwable {
        CollectionType t = factory.constructRawCollectionType(List.class);
        assertEquals(Object.class, t.containedType(0).getRawClass());
    }

    // covers constructRawMapType using unknownType() for both key and value
    @Test
    public void testConstructRawMapType_returnsUnknownKeyAndValueTypes() throws Throwable {
        MapType t = factory.constructRawMapType(Map.class);
        assertEquals(Object.class, t.containedType(0).getRawClass());
        assertEquals(Object.class, t.containedType(1).getRawClass());
    }
}
