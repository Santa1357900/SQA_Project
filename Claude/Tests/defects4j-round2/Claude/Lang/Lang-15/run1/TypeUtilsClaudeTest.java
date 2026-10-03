package org.apache.commons.lang3.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class TypeUtilsClaudeTest {

    // Covers: public no-arg constructor.
    @Test
    public void testConstructor_createsInstance_notNull() throws Throwable {
        TypeUtils instance = new TypeUtils();
        assertNotNull(instance);
    }

    // Covers isAssignable(Type,Type): Class-to-Class, ClassUtils.isAssignable true branch.
    @Test
    public void testIsAssignable_classAssignableToSuperclass_true() throws Throwable {
        assertTrue(TypeUtils.isAssignable(Integer.class, Number.class));
    }

    // Covers isAssignable(Type,Type): Class-to-Class, ClassUtils.isAssignable false branch.
    @Test
    public void testIsAssignable_unrelatedClasses_false() throws Throwable {
        assertFalse(TypeUtils.isAssignable(String.class, Integer.class));
    }

    // Covers isAssignable(Type,Class): type==null, toClass not primitive -> true.
    @Test
    public void testIsAssignable_nullTypeToNonPrimitiveClass_true() throws Throwable {
        assertTrue(TypeUtils.isAssignable(null, Object.class));
    }

    // Covers isAssignable(Type,Class): type==null, toClass primitive -> false.
    @Test
    public void testIsAssignable_nullTypeToPrimitiveClass_false() throws Throwable {
        assertFalse(TypeUtils.isAssignable(null, int.class));
    }

    // Covers isAssignable(Type,Class): toClass==null, type!=null -> false.
    @Test
    public void testIsAssignable_nonNullTypeToNullClass_false() throws Throwable {
        assertFalse(TypeUtils.isAssignable(String.class, (Type) null));
    }

    // Covers isAssignable(Type,Class): toClass.equals(type) reflexive branch -> true.
    @Test
    public void testIsAssignable_sameClass_true() throws Throwable {
        assertTrue(TypeUtils.isAssignable(String.class, String.class));
    }

    // Covers isAssignable(Type,ParameterizedType): compatible subtype with matching type arg -> true.
    @Test
    public void testIsAssignable_parameterizedType_compatibleTypeArg_true() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("plainListField");
        Type listStringType = f.getGenericType();
        assertTrue(TypeUtils.isAssignable(StringList.class, listStringType));
    }

    // Covers isAssignable(Type,ParameterizedType): mismatched type argument -> false.
    @Test
    public void testIsAssignable_parameterizedType_mismatchedTypeArg_false() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("plainListField");
        Type listStringType = f.getGenericType();
        assertFalse(TypeUtils.isAssignable(IntegerList.class, listStringType));
    }

    // Covers isAssignable(Type,ParameterizedType): raw type not assignable -> fromTypeVarAssigns null -> false.
    @Test
    public void testIsAssignable_parameterizedType_incompatibleRawType_false() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("plainListField");
        Type listStringType = f.getGenericType();
        assertFalse(TypeUtils.isAssignable(String.class, listStringType));
    }

    // Covers isAssignable(Type,WildcardType): subject satisfies upper bound -> true.
    @Test
    public void testIsAssignable_wildcardUpperBound_satisfied_true() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("upperWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        Type wildcard = pt.getActualTypeArguments()[0];
        assertTrue(TypeUtils.isAssignable(Integer.class, wildcard));
    }

    // Covers isAssignable(Type,WildcardType): subject violates upper bound -> false.
    @Test
    public void testIsAssignable_wildcardUpperBound_violated_false() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("upperWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        Type wildcard = pt.getActualTypeArguments()[0];
        assertFalse(TypeUtils.isAssignable(String.class, wildcard));
    }

    // Covers isAssignable(Type,WildcardType): subject satisfies lower bound -> true.
    @Test
    public void testIsAssignable_wildcardLowerBound_satisfied_true() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("lowerWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        Type wildcard = pt.getActualTypeArguments()[0];
        assertTrue(TypeUtils.isAssignable(Number.class, wildcard));
    }

    // Covers isAssignable(Type,WildcardType): subject violates lower bound -> false.
    @Test
    public void testIsAssignable_wildcardLowerBound_violated_false() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("lowerWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        Type wildcard = pt.getActualTypeArguments()[0];
        assertFalse(TypeUtils.isAssignable(String.class, wildcard));
    }

    // Covers isAssignable(Type,GenericArrayType): reflexive equal types -> true.
    @Test
    public void testIsAssignable_genericArrayType_reflexive_true() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("arrayField");
        Type arrType = f.getGenericType();
        assertTrue(TypeUtils.isAssignable(arrType, arrType));
    }

    // Covers isAssignable(Type,TypeVariable): a concrete Class is never assignable to a type variable -> false.
    @Test
    public void testIsAssignable_classToTypeVariable_false() throws Throwable {
        TypeVariable<?> tv = GenericHolder.class.getTypeParameters()[0];
        assertFalse(TypeUtils.isAssignable(Integer.class, tv));
    }

    // Covers isAssignable(Type,Type): unrecognized Type implementation throws IllegalStateException.
    @Test
    public void testIsAssignable_unknownType_throwsIllegalStateException() throws Throwable {
        try {
            TypeUtils.isAssignable(String.class, new UnknownType());
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // Covers isInstance: null value, non-primitive target type -> true.
    @Test
    public void testIsInstance_nullValueNonPrimitiveType_true() throws Throwable {
        assertTrue(TypeUtils.isInstance(null, Object.class));
    }

    // Covers isInstance: null value, primitive target type -> false.
    @Test
    public void testIsInstance_nullValuePrimitiveType_false() throws Throwable {
        assertFalse(TypeUtils.isInstance(null, int.class));
    }

    // Covers isInstance: type==null -> false.
    @Test
    public void testIsInstance_nullType_false() throws Throwable {
        assertFalse(TypeUtils.isInstance("x", null));
    }

    // Covers isInstance: non-null value assignable to target type -> true.
    @Test
    public void testIsInstance_nonNullValueAssignable_true() throws Throwable {
        assertTrue(TypeUtils.isInstance("hello", CharSequence.class));
    }

    // Covers isInstance: non-null value not assignable to target type -> false.
    @Test
    public void testIsInstance_nonNullValueNotAssignable_false() throws Throwable {
        assertFalse(TypeUtils.isInstance("hello", Integer.class));
    }

    // Covers normalizeUpperBounds: fewer than 2 bounds returned unchanged.
    @Test
    public void testNormalizeUpperBounds_singleBound_returnsSame() throws Throwable {
        Type[] bounds = new Type[] { Number.class };
        Type[] result = TypeUtils.normalizeUpperBounds(bounds);
        assertArrayEquals(bounds, result);
    }

    // Covers normalizeUpperBounds: redundant supertype removed, most specific subtype retained.
    @Test
    public void testNormalizeUpperBounds_removesRedundantSupertype() throws Throwable {
        Type[] bounds = new Type[] { Number.class, Integer.class };
        Type[] result = TypeUtils.normalizeUpperBounds(bounds);
        assertEquals(1, result.length);
        assertEquals(Integer.class, result[0]);
    }

    // Covers getImplicitBounds: explicit bound on type variable is returned.
    @Test
    public void testGetImplicitBounds_explicitBound_returnsBound() throws Throwable {
        TypeVariable<?> tv = GenericHolder.class.getTypeParameters()[0];
        Type[] bounds = TypeUtils.getImplicitBounds(tv);
        assertEquals(1, bounds.length);
        assertEquals(Number.class, bounds[0]);
    }

    // Covers getImplicitUpperBounds: explicit upper bound on wildcard is returned.
    @Test
    public void testGetImplicitUpperBounds_explicitBound_returnsBound() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("upperWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        Type[] upper = TypeUtils.getImplicitUpperBounds(wc);
        assertEquals(1, upper.length);
        assertEquals(Number.class, upper[0]);
    }

    // Covers getImplicitLowerBounds: no explicit lower bound -> array containing null.
    @Test
    public void testGetImplicitLowerBounds_noExplicitBound_returnsNullElement() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("upperWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        Type[] lower = TypeUtils.getImplicitLowerBounds(wc);
        assertEquals(1, lower.length);
        assertNull(lower[0]);
    }

    // Covers getImplicitLowerBounds: explicit lower bound on wildcard is returned.
    @Test
    public void testGetImplicitLowerBounds_explicitBound_returnsBound() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("lowerWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        Type[] lower = TypeUtils.getImplicitLowerBounds(wc);
        assertEquals(1, lower.length);
        assertEquals(Integer.class, lower[0]);
    }

    // Covers getTypeArguments(ParameterizedType): maps interface's type variable to its argument.
    @Test
    public void testGetTypeArguments_parameterizedType_mapsTypeVariableToArgument() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("plainListField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        Map<TypeVariable<?>, Type> args = TypeUtils.getTypeArguments(pt);
        TypeVariable<?> listVar = List.class.getTypeParameters()[0];
        assertEquals(String.class, args.get(listVar));
    }

    // Covers getTypeArguments(Type,Class): subclass with explicit type witness resolves ancestor's variable.
    @Test
    public void testGetTypeArguments_classToInterface_resolvesTypeVariable() throws Throwable {
        Map<TypeVariable<?>, Type> args = TypeUtils.getTypeArguments(StringList.class, List.class);
        TypeVariable<?> listVar = List.class.getTypeParameters()[0];
        assertEquals(String.class, args.get(listVar));
    }

    // Covers getTypeArguments(Type,Class): type not assignable to toClass -> null.
    @Test
    public void testGetTypeArguments_notAssignable_returnsNull() throws Throwable {
        Map<TypeVariable<?>, Type> args = TypeUtils.getTypeArguments(String.class, List.class);
        assertNull(args);
    }

    // LANG-15: getTypeArguments(Class,Class,Map) must keep walking the hierarchy toward toClass
    // even when an intermediate raw ancestor merely declares type parameters; it must not stop
    // there unless that ancestor actually equals toClass. The hierarchy between ArrayList (raw)
    // and Collection carries type-variable information, so the resulting map must not be empty.
    @Test
    public void testGetTypeArguments_rawAncestorWithTypeParams_walkReachesTargetInterface() throws Throwable {
        Map<TypeVariable<?>, Type> args = TypeUtils.getTypeArguments(RawListSubclass.class, Collection.class);
        assertNotNull(args);
        assertFalse(args.isEmpty());
    }

    // Covers determineTypeArguments: resolves ancestor's type variable from subclass's concrete argument.
    @Test
    public void testDetermineTypeArguments_resolvesSubclassTypeVariable() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("plainListField");
        ParameterizedType listStringType = (ParameterizedType) f.getGenericType();
        Map<TypeVariable<?>, Type> args = TypeUtils.determineTypeArguments(StringList.class, listStringType);
        TypeVariable<?> arrayListVar = ArrayList.class.getTypeParameters()[0];
        assertEquals(String.class, args.get(arrayListVar));
    }

    // Covers getRawType(Type,Type): ParameterizedType -> its raw Class.
    @Test
    public void testGetRawType_parameterizedType_returnsRawClass() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("plainListField");
        Type listStringType = f.getGenericType();
        assertEquals(List.class, TypeUtils.getRawType(listStringType, null));
    }

    // Covers getRawType(Type,Type): TypeVariable with null assigningType -> null.
    @Test
    public void testGetRawType_typeVariableNullAssigningType_returnsNull() throws Throwable {
        TypeVariable<?> tv = GenericHolder.class.getTypeParameters()[0];
        assertNull(TypeUtils.getRawType(tv, null));
    }

    // Covers getRawType(Type,Type): TypeVariable resolved via assigningType -> concrete argument class.
    @Test
    public void testGetRawType_typeVariableWithAssigningType_resolvesClass() throws Throwable {
        TypeVariable<?> listVar = List.class.getTypeParameters()[0];
        Field f = GenericHolder.class.getDeclaredField("plainListField");
        Type listStringType = f.getGenericType();
        assertEquals(String.class, TypeUtils.getRawType(listVar, listStringType));
    }

    // Covers getRawType(Type,Type): WildcardType -> null.
    @Test
    public void testGetRawType_wildcardType_returnsNull() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("upperWildField");
        ParameterizedType pt = (ParameterizedType) f.getGenericType();
        Type wildcard = pt.getActualTypeArguments()[0];
        assertNull(TypeUtils.getRawType(wildcard, null));
    }

    // Covers getRawType(Type,Type): GenericArrayType -> array class of resolved component.
    @Test
    public void testGetRawType_genericArrayType_returnsArrayClass() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("arrayField");
        Type arrType = f.getGenericType();
        Class<?> raw = TypeUtils.getRawType(arrType, null);
        assertTrue(raw.isArray());
        assertEquals(List.class, raw.getComponentType());
    }

    // Covers isArrayType: array Class -> true.
    @Test
    public void testIsArrayType_classArray_true() throws Throwable {
        assertTrue(TypeUtils.isArrayType(String[].class));
    }

    // Covers isArrayType: non-array Class -> false.
    @Test
    public void testIsArrayType_nonArrayClass_false() throws Throwable {
        assertFalse(TypeUtils.isArrayType(String.class));
    }

    // Covers getArrayComponentType: array Class -> its component type.
    @Test
    public void testGetArrayComponentType_classArray_returnsComponent() throws Throwable {
        assertEquals(String.class, TypeUtils.getArrayComponentType(String[].class));
    }

    // Covers getArrayComponentType: non-array Class -> null.
    @Test
    public void testGetArrayComponentType_nonArrayClass_returnsNull() throws Throwable {
        assertNull(TypeUtils.getArrayComponentType(String.class));
    }

    // Covers getArrayComponentType: GenericArrayType -> its generic component type.
    @Test
    public void testGetArrayComponentType_genericArrayType_returnsGenericComponent() throws Throwable {
        Field f = GenericHolder.class.getDeclaredField("arrayField");
        Type arrType = f.getGenericType();
        Type component = TypeUtils.getArrayComponentType(arrType);
        assertTrue(component instanceof ParameterizedType);
    }

    // Covers typesSatisfyVariables: assigned type satisfies the type variable's bound -> true.
    @Test
    public void testTypesSatisfyVariables_boundSatisfied_true() throws Throwable {
        TypeVariable<?> tv = GenericHolder.class.getTypeParameters()[0];
        Map<TypeVariable<?>, Type> assigns = new HashMap<TypeVariable<?>, Type>();
        assigns.put(tv, Integer.class);
        assertTrue(TypeUtils.typesSatisfyVariables(assigns));
    }

    // Covers typesSatisfyVariables: assigned type violates the type variable's bound -> false.
    @Test
    public void testTypesSatisfyVariables_boundViolated_false() throws Throwable {
        TypeVariable<?> tv = GenericHolder.class.getTypeParameters()[0];
        Map<TypeVariable<?>, Type> assigns = new HashMap<TypeVariable<?>, Type>();
        assigns.put(tv, String.class);
        assertFalse(TypeUtils.typesSatisfyVariables(assigns));
    }

    private static class StringList extends ArrayList<String> {
    }

    private static class IntegerList extends ArrayList<Integer> {
    }

    private static class RawListSubclass extends ArrayList {
    }

    private static class UnknownType implements Type {
    }

    private static class GenericHolder<T extends Number> {
        List<String> plainListField;
        List<? extends Number> upperWildField;
        List<? super Integer> lowerWildField;
        List<String>[] arrayField;
        T typeVarField;
    }
}
