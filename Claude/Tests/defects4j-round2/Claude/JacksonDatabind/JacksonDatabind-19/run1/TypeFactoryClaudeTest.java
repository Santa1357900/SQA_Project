package com.fasterxml.jackson.databind.type;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.core.type.TypeReference;

public class TypeFactoryClaudeTest
{
    private TypeFactory typeFactory;

    @Before
    public void setUp() throws Throwable {
        typeFactory = TypeFactory.defaultInstance();
    }

    // Helper: concrete (non-generic-parameter) reference to AtomicReference<String>,
    // but the class itself still declares its own unused type parameter T so that
    // usages of it can appear as a genuine ParameterizedType via reflection.
    public static class ConcreteStringAtomicRef<T> extends AtomicReference<String> {
    }

    // Helper holder with a public generic field, used to obtain a real ParameterizedType.
    public static class ListStringHolder {
        public List<String> value;
    }

    // covers defaultInstance(): singleton accessor
    @Test
    public void testDefaultInstance_returnsSameSingletonInstance() throws Throwable {
        TypeFactory a = TypeFactory.defaultInstance();
        TypeFactory b = TypeFactory.defaultInstance();
        assertSame(a, b);
    }

    // covers clearCache(): should not throw and type resolution still works afterwards
    @Test
    public void testClearCache_doesNotThrowAndTypeStillResolvesCorrectly() throws Throwable {
        typeFactory.constructType(Number.class);
        typeFactory.clearCache();
        JavaType t = typeFactory.constructType(Number.class);
        assertEquals(Number.class, t.getRawClass());
    }

    // covers unknownType() / _unknownType()
    @Test
    public void testUnknownType_returnsObjectRawClass() throws Throwable {
        JavaType t = TypeFactory.unknownType();
        assertEquals(Object.class, t.getRawClass());
    }

    // covers rawClass(Type) fast path: type instanceof Class
    @Test
    public void testRawClass_withClassInput_returnsSameClass() throws Throwable {
        Class<?> c = TypeFactory.rawClass(String.class);
        assertEquals(String.class, c);
    }

    // covers rawClass(Type) slow path: non-Class Type (ParameterizedType)
    @Test
    public void testRawClass_withParameterizedTypeInput_returnsErasedRawClass() throws Throwable {
        Field f = ListStringHolder.class.getField("value");
        Type t = f.getGenericType();
        Class<?> c = TypeFactory.rawClass(t);
        assertEquals(List.class, c);
    }

    // covers constructSpecializedType: quick-path when rawClass == subclass
    @Test
    public void testConstructSpecializedType_sameRawClass_returnsSameInstance() throws Throwable {
        JavaType baseType = typeFactory.constructType(String.class);
        JavaType result = typeFactory.constructSpecializedType(baseType, String.class);
        assertSame(baseType, result);
    }

    // covers constructSpecializedType: SimpleType base, Collection subclass, compatible -> success
    @Test
    public void testConstructSpecializedType_simpleTypeToCollectionSubclass_succeeds() throws Throwable {
        JavaType baseType = typeFactory.constructType(Object.class);
        JavaType result = typeFactory.constructSpecializedType(baseType, ArrayList.class);
        assertEquals(ArrayList.class, result.getRawClass());
    }

    // covers constructSpecializedType: incompatible subclass -> IllegalArgumentException
    @Test
    public void testConstructSpecializedType_incompatibleSubclass_throwsIllegalArgumentException() throws Throwable {
        JavaType baseType = typeFactory.constructType(Number.class);
        try {
            typeFactory.constructSpecializedType(baseType, ArrayList.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers constructSpecializedType: non-SimpleType base falls through to narrowBy
    @Test
    public void testConstructSpecializedType_nonSimpleTypeBase_usesNarrowBy() throws Throwable {
        JavaType baseType = typeFactory.constructCollectionType(List.class, String.class);
        JavaType result = typeFactory.constructSpecializedType(baseType, ArrayList.class);
        assertEquals(ArrayList.class, result.getRawClass());
    }

    // covers constructSpecializedType: SimpleType base but subclass not array/map/collection -> narrowBy
    @Test
    public void testConstructSpecializedType_simpleTypeNonContainerSubclass_usesNarrowBy() throws Throwable {
        JavaType baseType = typeFactory.constructType(Number.class);
        JavaType result = typeFactory.constructSpecializedType(baseType, Integer.class);
        assertEquals(Integer.class, result.getRawClass());
    }

    // covers constructSpecializedType: value/type handler copy branches
    @Test
    public void testConstructSpecializedType_copiesValueAndTypeHandlers() throws Throwable {
        JavaType baseType = typeFactory.constructType(Object.class);
        Object valHandler = new Object();
        Object typeHandler = new Object();
        JavaType withHandlers = baseType.withValueHandler(valHandler).withTypeHandler(typeHandler);
        JavaType result = typeFactory.constructSpecializedType(withHandlers, ArrayList.class);
        assertSame(valHandler, result.getValueHandler());
        assertSame(typeHandler, result.getTypeHandler());
    }

    // covers constructFromCanonical: valid canonical representation
    @Test
    public void testConstructFromCanonical_validClassName_returnsMatchingType() throws Throwable {
        JavaType t = typeFactory.constructFromCanonical("java.lang.String");
        assertEquals(String.class, t.getRawClass());
    }

    // covers constructFromCanonical: malformed / unknown class -> IllegalArgumentException
    @Test
    public void testConstructFromCanonical_malformedInput_throwsIllegalArgumentException() throws Throwable {
        try {
            typeFactory.constructFromCanonical("NoSuchClassAtAllXyz123");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers findTypeParameters(JavaType, Class): direct-path branch (expType == parameterSource)
    @Test
    public void testFindTypeParametersJavaType_directPath_returnsContainedTypes() throws Throwable {
        JavaType collType = typeFactory.constructCollectionType(ArrayList.class, String.class);
        Class<?> paramSource = collType.getParameterSource();
        JavaType[] params = typeFactory.findTypeParameters(collType, paramSource);
        assertEquals(1, params.length);
        assertEquals(String.class, params[0].getRawClass());
    }

    // covers findTypeParameters(Class, Class): not a subtype -> IllegalArgumentException
    @Test
    public void testFindTypeParametersClassClass_notSubtype_throwsIllegalArgumentException() throws Throwable {
        try {
            typeFactory.findTypeParameters(String.class, Collection.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers moreSpecificType: type1 == null -> returns type2
    @Test
    public void testMoreSpecificType_firstNull_returnsSecond() throws Throwable {
        JavaType type2 = typeFactory.constructType(String.class);
        JavaType result = typeFactory.moreSpecificType(null, type2);
        assertSame(type2, result);
    }

    // covers moreSpecificType: type2 == null -> returns type1
    @Test
    public void testMoreSpecificType_secondNull_returnsFirst() throws Throwable {
        JavaType type1 = typeFactory.constructType(String.class);
        JavaType result = typeFactory.moreSpecificType(type1, null);
        assertSame(type1, result);
    }

    // covers moreSpecificType: same raw class -> returns type1
    @Test
    public void testMoreSpecificType_sameRawClass_returnsFirst() throws Throwable {
        JavaType type1 = typeFactory.constructType(String.class);
        JavaType type2 = typeFactory.constructType(String.class);
        JavaType result = typeFactory.moreSpecificType(type1, type2);
        assertSame(type1, result);
    }

    // covers moreSpecificType: raw1.isAssignableFrom(raw2) -> returns type2 (more specific)
    @Test
    public void testMoreSpecificType_assignableFromSecond_returnsSecond() throws Throwable {
        JavaType type1 = typeFactory.constructType(Number.class);
        JavaType type2 = typeFactory.constructType(Integer.class);
        JavaType result = typeFactory.moreSpecificType(type1, type2);
        assertSame(type2, result);
    }

    // covers moreSpecificType: unrelated raw classes -> returns type1 (primary)
    @Test
    public void testMoreSpecificType_unrelatedTypes_returnsFirst() throws Throwable {
        JavaType type1 = typeFactory.constructType(String.class);
        JavaType type2 = typeFactory.constructType(Integer.class);
        JavaType result = typeFactory.moreSpecificType(type1, type2);
        assertSame(type1, result);
    }

    // covers constructType(Type): simple Class input
    @Test
    public void testConstructType_withClass_returnsExpectedRawClass() throws Throwable {
        JavaType t = typeFactory.constructType(String.class);
        assertEquals(String.class, t.getRawClass());
    }

    // covers constructType(Type, TypeBindings): explicit null bindings
    @Test
    public void testConstructType_withNullTypeBindings_behavesSameAsWithoutBindings() throws Throwable {
        JavaType t = typeFactory.constructType(String.class, (TypeBindings) null);
        assertEquals(String.class, t.getRawClass());
    }

    // covers constructType(Type, Class<?>): null context and non-null context branches
    @Test
    public void testConstructType_withContextClassNullAndNonNull() throws Throwable {
        JavaType t1 = typeFactory.constructType(String.class, (Class<?>) null);
        assertEquals(String.class, t1.getRawClass());
        JavaType t2 = typeFactory.constructType(String.class, Object.class);
        assertEquals(String.class, t2.getRawClass());
    }

    // covers constructType(Type, JavaType): null context and non-null context branches
    @Test
    public void testConstructType_withContextJavaTypeNullAndNonNull() throws Throwable {
        JavaType ctx = typeFactory.constructType(Object.class);
        JavaType t1 = typeFactory.constructType(String.class, (JavaType) null);
        assertEquals(String.class, t1.getRawClass());
        JavaType t2 = typeFactory.constructType(String.class, ctx);
        assertEquals(String.class, t2.getRawClass());
    }

    // covers constructType(TypeReference<?>): generic List<String> resolution
    @Test
    public void testConstructType_withTypeReference_genericList_resolvesElementType() throws Throwable {
        TypeReference<List<String>> ref = new TypeReference<List<String>>() { };
        JavaType t = typeFactory.constructType(ref);
        assertEquals(List.class, t.getRawClass());
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers _fromParamType's AtomicReference-subtype else branch: pts.length == 1 case.
    // Targets the known bug where the condition was "!= 1" instead of "== 1",
    // causing the resolved referenced type to be lost (falls back to Object/unknown).
    @Test
    public void testConstructType_atomicReferenceSubclassParameterizedType_resolvesReferencedTypeFromConcreteSuperclassArg() throws Throwable {
        TypeReference<ConcreteStringAtomicRef<Integer>> typeRef =
            new TypeReference<ConcreteStringAtomicRef<Integer>>() { };
        JavaType type = typeFactory.constructType(typeRef);
        assertEquals(1, type.containedTypeCount());
        assertEquals(String.class, type.containedType(0).getRawClass());
    }

    // covers constructArrayType(Class<?>)
    @Test
    public void testConstructArrayTypeFromClass_returnsArrayTypeWithElementType() throws Throwable {
        ArrayType at = typeFactory.constructArrayType(String.class);
        assertEquals(String.class, at.containedType(0).getRawClass());
    }

    // covers constructArrayType(JavaType)
    @Test
    public void testConstructArrayTypeFromJavaType_returnsArrayType() throws Throwable {
        JavaType elem = typeFactory.constructType(Integer.class);
        ArrayType at = typeFactory.constructArrayType(elem);
        assertEquals(Integer.class, at.containedType(0).getRawClass());
    }

    // covers constructCollectionType(Class, Class)
    @Test
    public void testConstructCollectionType_classElement_returnsCollectionTypeWithElementType() throws Throwable {
        CollectionType ct = typeFactory.constructCollectionType(ArrayList.class, String.class);
        assertEquals(ArrayList.class, ct.getRawClass());
        assertEquals(String.class, ct.containedType(0).getRawClass());
    }

    // covers constructCollectionType(Class, JavaType)
    @Test
    public void testConstructCollectionType_javaTypeElement_returnsCollectionTypeWithElementType() throws Throwable {
        JavaType elem = typeFactory.constructType(Integer.class);
        CollectionType ct = typeFactory.constructCollectionType(ArrayList.class, elem);
        assertEquals(Integer.class, ct.containedType(0).getRawClass());
    }

    // covers constructMapType(Class, Class, Class)
    @Test
    public void testConstructMapType_classKeyValue_returnsMapTypeWithKeyAndValue() throws Throwable {
        MapType mt = typeFactory.constructMapType(HashMap.class, String.class, Integer.class);
        assertEquals(String.class, mt.containedType(0).getRawClass());
        assertEquals(Integer.class, mt.containedType(1).getRawClass());
    }

    // covers constructMapType(Class, JavaType, JavaType)
    @Test
    public void testConstructMapType_javaTypeKeyValue_returnsMapTypeWithKeyAndValue() throws Throwable {
        JavaType key = typeFactory.constructType(String.class);
        JavaType val = typeFactory.constructType(Integer.class);
        MapType mt = typeFactory.constructMapType(HashMap.class, key, val);
        assertEquals(String.class, mt.containedType(0).getRawClass());
        assertEquals(Integer.class, mt.containedType(1).getRawClass());
    }

    // covers deprecated constructSimpleType(Class, JavaType[]) matching parameter count (0)
    @Test
    public void testConstructSimpleType_deprecatedTwoArg_matchingCount_returnsExpectedType() throws Throwable {
        JavaType t = typeFactory.constructSimpleType(Object.class, new JavaType[0]);
        assertEquals(Object.class, t.getRawClass());
    }

    // covers constructSimpleType(Class, Class, JavaType[]): parameter count mismatch -> exception
    @Test
    public void testConstructSimpleType_mismatchCount_throwsIllegalArgumentException() throws Throwable {
        try {
            typeFactory.constructSimpleType(List.class, List.class, new JavaType[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers constructReferenceType(Class, JavaType)
    @Test
    public void testConstructReferenceType_returnsReferenceTypeWithGivenContent() throws Throwable {
        JavaType content = typeFactory.constructType(String.class);
        JavaType ref = typeFactory.constructReferenceType(AtomicReference.class, content);
        assertEquals(String.class, ref.containedType(0).getRawClass());
    }

    // covers uncheckedSimpleType(Class)
    @Test
    public void testUncheckedSimpleType_returnsSimpleTypeWithGivenRawClass() throws Throwable {
        JavaType t = typeFactory.uncheckedSimpleType(Number.class);
        assertEquals(Number.class, t.getRawClass());
    }

    // covers constructParametrizedType: array branch, wrong parameter count -> exception
    @Test
    public void testConstructParametrizedType_arrayMismatchCount_throwsIllegalArgumentException() throws Throwable {
        try {
            typeFactory.constructParametrizedType(String[].class, String[].class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers constructParametrizedType: Map branch, wrong parameter count -> exception
    @Test
    public void testConstructParametrizedType_mapMismatchCount_throwsIllegalArgumentException() throws Throwable {
        try {
            typeFactory.constructParametrizedType(HashMap.class, Map.class, String.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers constructParametrizedType: Collection branch, wrong parameter count -> exception
    @Test
    public void testConstructParametrizedType_collectionMismatchCount_throwsIllegalArgumentException() throws Throwable {
        try {
            typeFactory.constructParametrizedType(ArrayList.class, List.class, String.class, Integer.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers constructParametrizedType(Class, Class, Class...): Map success path
    @Test
    public void testConstructParametrizedType_mapClassVarargs_returnsExpectedKeyValue() throws Throwable {
        JavaType t = typeFactory.constructParametrizedType(HashMap.class, Map.class, String.class, Integer.class);
        assertEquals(String.class, t.containedType(0).getRawClass());
        assertEquals(Integer.class, t.containedType(1).getRawClass());
    }

    // covers constructParametrizedType(Class, Class, Class...): Collection success path
    @Test
    public void testConstructParametrizedType_collectionClassVarargs_returnsExpectedElement() throws Throwable {
        JavaType t = typeFactory.constructParametrizedType(ArrayList.class, List.class, String.class);
        assertEquals(String.class, t.containedType(0).getRawClass());
    }

    // covers constructRawCollectionType(Class): unknown element type
    @Test
    public void testConstructRawCollectionType_elementIsUnknownObjectType() throws Throwable {
        CollectionType ct = typeFactory.constructRawCollectionType(ArrayList.class);
        assertEquals(Object.class, ct.containedType(0).getRawClass());
    }

    // covers constructRawMapType(Class): unknown key/value types
    @Test
    public void testConstructRawMapType_keyAndValueAreUnknownObjectType() throws Throwable {
        MapType mt = typeFactory.constructRawMapType(HashMap.class);
        assertEquals(Object.class, mt.containedType(0).getRawClass());
        assertEquals(Object.class, mt.containedType(1).getRawClass());
    }

    // covers withModifier(null): fallback branch returning a still-functional factory
    @Test
    public void testWithModifier_nullModifier_returnsFunctionalFactory() throws Throwable {
        TypeFactory tf2 = typeFactory.withModifier(null);
        JavaType t = tf2.constructType(String.class);
        assertEquals(String.class, t.getRawClass());
    }

    // covers withModifier(mod) + _constructType's modifier-invocation branch for non-container types
    @Test
    public void testWithModifier_customModifier_appliesToNonContainerType() throws Throwable {
        TypeModifier marker = new TypeModifier() {
            public JavaType modifyType(JavaType type, Type origType, TypeBindings context, TypeFactory tf) {
                return TypeFactory.unknownType();
            }
        };
        TypeFactory tfWithMod = typeFactory.withModifier(marker);
        JavaType result = tfWithMod.constructType(String.class);
        assertEquals(Object.class, result.getRawClass());
    }
}
