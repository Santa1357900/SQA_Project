package org.mockito.internal.util.reflection;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.exceptions.base.MockitoException;

public class GenericMetadataSupportClaudeTest {

    private interface Box<T> {
    }

    private interface Container<U> {
    }

    private interface BoxHolder {
        Box<String> getBox();
        Box<? extends Number> getWildcardBox();
    }

    private interface WildcardHolder {
        List<? extends Number> upperWildcard();
        List<? super Integer> lowerWildcard();
    }

    private interface SimpleBound<E extends Number> {
        E get();
    }

    private interface BoundedMulti<E extends Number & Comparable<E>> {
        E get();
    }

    private interface ArrayReturner<T> {
        T[] returnArray();
    }

    private interface GenericsNest<K extends Comparable<K>> {
        Set<Number> remove(Object key);
        K returningK();
        List<? extends K> returning_wildcard_with_typeVar_upper_bound();
        <S, T extends S> T two_type_params();
        Number returningNonGeneric();
    }

    private static class TestableMetadata extends GenericMetadataSupport {
        private final Class<?> raw;

        TestableMetadata(Class<?> raw) {
            this.raw = raw;
        }

        @Override
        public Class<?> rawType() {
            return raw;
        }
    }

    // Covers registerTypeVariablesOn: classType is not a ParameterizedType -> early return, no registration
    @Test
    public void testRegisterTypeVariablesOn_notParameterizedType_noOp() throws Throwable {
        TestableMetadata meta = new TestableMetadata(Object.class);
        meta.registerTypeVariablesOn(String.class);
        assertTrue(meta.contextualActualTypeParameters.isEmpty());
    }

    // Covers registerTypeVariablesOn: concrete (non-wildcard) actual type argument is registered directly
    @Test
    public void testRegisterTypeVariablesOn_concreteTypeArgument_registersActualType() throws Throwable {
        Method m = BoxHolder.class.getMethod("getBox");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        TestableMetadata meta = new TestableMetadata(Box.class);
        meta.registerTypeVariablesOn(pt);
        TypeVariable tv = Box.class.getTypeParameters()[0];
        assertEquals(String.class, meta.getActualTypeArgumentFor(tv));
    }

    // Covers registerTypeVariablesOn: WildcardType actual type argument is wrapped into a BoundedType
    @Test
    public void testRegisterTypeVariablesOn_wildcardTypeArgument_registersBoundedType() throws Throwable {
        Method m = BoxHolder.class.getMethod("getWildcardBox");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        TestableMetadata meta = new TestableMetadata(Box.class);
        meta.registerTypeVariablesOn(pt);
        TypeVariable tv = Box.class.getTypeParameters()[0];
        assertTrue(meta.getActualTypeArgumentFor(tv) instanceof GenericMetadataSupport.BoundedType);
    }

    // Covers registerTypeParametersOn: already-registered TypeVariable is not overwritten on second call
    @Test
    public void testRegisterTypeParametersOn_alreadyPresent_doesNotOverwrite() throws Throwable {
        TypeVariable[] params = Box.class.getTypeParameters();
        TestableMetadata meta = new TestableMetadata(Box.class);
        meta.registerTypeParametersOn(params);
        Type before = meta.contextualActualTypeParameters.get(params[0]);
        meta.registerTypeParametersOn(params);
        Type after = meta.contextualActualTypeParameters.get(params[0]);
        assertSame(before, after);
    }

    // Covers inferFrom(Class): rawType() returns the class used to build the metadata
    @Test
    public void testInferFrom_class_rawTypeMatchesInput() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(Box.class);
        assertEquals(Box.class, meta.rawType());
    }

    // Covers inferFrom(ParameterizedType): rawType() returns the raw type of the parameterized type
    @Test
    public void testInferFrom_parameterizedType_rawTypeMatchesRawType() throws Throwable {
        Method m = BoxHolder.class.getMethod("getBox");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(pt);
        assertEquals(Box.class, meta.rawType());
    }

    // Covers extraInterfaces() default implementation (not overridden) -> empty list
    @Test
    public void testExtraInterfaces_default_emptyList() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(Box.class);
        assertTrue(meta.extraInterfaces().isEmpty());
    }

    // Covers extraInterfaces() for a TypeVariable with a single Class bound -> no extra interfaces
    @Test
    public void testExtraInterfaces_singleClassBound_returnsEmptyList() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(SimpleBound.class);
        Method m = SimpleBound.class.getMethod("get");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertTrue(result.extraInterfaces().isEmpty());
    }

    // Covers extraInterfaces() for a TypeVariable bounded by Class & Interface -> returns the extra interface bound
    @Test
    public void testExtraInterfaces_boundedTypeVariableWithInterfaceBound_returnsComparable() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(BoundedMulti.class);
        Method m = BoundedMulti.class.getMethod("get");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertEquals(1, result.extraInterfaces().size());
    }

    // Covers rawExtraInterfaces() default implementation -> empty array
    @Test
    public void testRawExtraInterfaces_default_emptyArray() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(Box.class);
        assertEquals(0, meta.rawExtraInterfaces().length);
    }

    // Covers rawExtraInterfaces() excluding the raw type itself from the extracted raw interfaces
    @Test
    public void testRawExtraInterfaces_boundedTypeVariable_excludesRawTypeItself() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(BoundedMulti.class);
        Method m = BoundedMulti.class.getMethod("get");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        Class<?>[] rawExtra = result.rawExtraInterfaces();
        assertEquals(1, rawExtra.length);
        assertEquals(Comparable.class, rawExtra[0]);
    }

    // Covers hasRawExtraInterfaces() default -> false when rawExtraInterfaces() is empty
    @Test
    public void testHasRawExtraInterfaces_default_false() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(Box.class);
        assertFalse(meta.hasRawExtraInterfaces());
    }

    // Covers hasRawExtraInterfaces() -> true when rawExtraInterfaces() is non-empty
    @Test
    public void testHasRawExtraInterfaces_boundedTypeVariable_true() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(BoundedMulti.class);
        Method m = BoundedMulti.class.getMethod("get");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertTrue(result.hasRawExtraInterfaces());
    }

    // Covers actualTypeArguments(): unbound type parameter resolves to a TypeVarBoundedType wrapper
    @Test
    public void testActualTypeArguments_noConcreteBinding_returnsTypeVarBoundedType() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(Box.class);
        Map<TypeVariable, Type> args = meta.actualTypeArguments();
        TypeVariable tv = Box.class.getTypeParameters()[0];
        assertTrue(args.get(tv) instanceof GenericMetadataSupport.TypeVarBoundedType);
    }

    // Covers actualTypeArguments(): concrete parameterized type resolves type parameter to the actual class
    @Test
    public void testActualTypeArguments_parameterizedConcreteType_resolvesToActualClass() throws Throwable {
        Method m = BoxHolder.class.getMethod("getBox");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(pt);
        Map<TypeVariable, Type> args = meta.actualTypeArguments();
        TypeVariable tv = Box.class.getTypeParameters()[0];
        assertEquals(String.class, args.get(tv));
    }

    // Covers getActualTypeArgumentFor(): recursive resolution through a chain of TypeVariables to a final class
    @Test
    public void testGetActualTypeArgumentFor_chainedTypeVariable_resolvesFinalType() throws Throwable {
        TypeVariable tv1 = Box.class.getTypeParameters()[0];
        TypeVariable tv2 = Container.class.getTypeParameters()[0];
        TestableMetadata meta = new TestableMetadata(Box.class);
        meta.contextualActualTypeParameters.put(tv1, tv2);
        meta.contextualActualTypeParameters.put(tv2, String.class);
        assertEquals(String.class, meta.getActualTypeArgumentFor(tv1));
    }

    // Covers resolveGenericReturnType: genericReturnType instanceof Class -> NotGenericReturnTypeSupport
    @Test
    public void testResolveGenericReturnType_classReturnType_notGenericSupport() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(GenericsNest.class);
        Method m = GenericsNest.class.getMethod("returningNonGeneric");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertEquals(Number.class, result.rawType());
    }

    // Covers resolveGenericReturnType: genericReturnType instanceof ParameterizedType -> ParameterizedReturnType
    @Test
    public void testResolveGenericReturnType_parameterizedReturnType_rawTypeIsSet() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(GenericsNest.class);
        Method m = GenericsNest.class.getMethod("remove", Object.class);
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertEquals(Set.class, result.rawType());
    }

    // Covers resolveGenericReturnType: genericReturnType instanceof TypeVariable -> resolves to its interface bound
    @Test
    public void testResolveGenericReturnType_typeVariableReturnType_resolvesInterfaceBound() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(GenericsNest.class);
        Method m = GenericsNest.class.getMethod("returningK");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertEquals(Comparable.class, result.rawType());
    }

    // Covers resolveGenericReturnType: TypeVariable bound by another unbounded TypeVariable resolves to Object
    @Test
    public void testResolveGenericReturnType_typeVariableChainedBound_resolvesToObject() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(GenericsNest.class);
        Method m = GenericsNest.class.getMethod("two_type_params");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertEquals(Object.class, result.rawType());
    }

    // Covers resolveGenericReturnType: unsupported generic return type (GenericArrayType) throws MockitoException
    @Test
    public void testResolveGenericReturnType_genericArrayType_throwsMockitoException() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(ArrayReturner.class);
        Method m = ArrayReturner.class.getMethod("returnArray");
        try {
            meta.resolveGenericReturnType(m);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Covers resolveGenericReturnType with a wildcard type argument whose upper bound is a TypeVariable
    @Test
    public void testResolveGenericReturnType_wildcardTypeArgument_rawTypeIsListClass() throws Throwable {
        GenericMetadataSupport meta = GenericMetadataSupport.inferFrom(GenericsNest.class);
        Method m = GenericsNest.class.getMethod("returning_wildcard_with_typeVar_upper_bound");
        GenericMetadataSupport result = meta.resolveGenericReturnType(m);
        assertEquals(List.class, result.rawType());
    }

    // Covers inferFrom: Type that is neither Class nor ParameterizedType throws MockitoException
    @Test
    public void testInferFrom_unsupportedType_throwsMockitoException() throws Throwable {
        Method m = ArrayReturner.class.getMethod("returnArray");
        Type genericArrayType = m.getGenericReturnType();
        try {
            GenericMetadataSupport.inferFrom(genericArrayType);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Covers TypeVarBoundedType.firstBound()/interfaceBounds() when no explicit bound is declared (defaults to Object)
    @Test
    public void testTypeVarBoundedType_noExplicitBound_firstBoundIsObjectClass() throws Throwable {
        TypeVariable tv = Box.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bt = new GenericMetadataSupport.TypeVarBoundedType(tv);
        assertEquals(Object.class, bt.firstBound());
        assertEquals(0, bt.interfaceBounds().length);
    }

    // Covers TypeVarBoundedType.interfaceBounds() with a Class + Interface bound -> array of length 1
    @Test
    public void testTypeVarBoundedType_multipleBounds_interfaceBoundsContainsExtra() throws Throwable {
        TypeVariable tv = BoundedMulti.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bt = new GenericMetadataSupport.TypeVarBoundedType(tv);
        assertEquals(Number.class, bt.firstBound());
        assertEquals(1, bt.interfaceBounds().length);
    }

    // Covers TypeVarBoundedType.equals()/hashCode() for the same wrapped TypeVariable, and typeVariable() accessor
    @Test
    public void testTypeVarBoundedType_equalsAndHashCode_sameTypeVariable() throws Throwable {
        TypeVariable tv = Box.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bt1 = new GenericMetadataSupport.TypeVarBoundedType(tv);
        GenericMetadataSupport.TypeVarBoundedType bt2 = new GenericMetadataSupport.TypeVarBoundedType(tv);
        assertTrue(bt1.equals(bt2));
        assertEquals(bt1.hashCode(), bt2.hashCode());
        assertSame(tv, bt1.typeVariable());
    }

    // Covers TypeVarBoundedType.equals() with null and with an unrelated object type -> false
    @Test
    public void testTypeVarBoundedType_equals_nullAndDifferentClass_returnsFalse() throws Throwable {
        TypeVariable tv = Box.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bt = new GenericMetadataSupport.TypeVarBoundedType(tv);
        assertFalse(bt.equals(null));
        assertFalse(bt.equals("not a bounded type"));
    }

    // Covers TypeVarBoundedType.toString() content
    @Test
    public void testTypeVarBoundedType_toString_containsFirstBound() throws Throwable {
        TypeVariable tv = Box.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bt = new GenericMetadataSupport.TypeVarBoundedType(tv);
        assertTrue(bt.toString().contains("firstBound="));
    }

    // Covers WildCardBoundedType.firstBound() ternary: upper-bound branch (no lower bounds declared)
    @Test
    public void testWildCardBoundedType_upperBound_firstBoundIsUpperBoundClass() throws Throwable {
        Method m = WildcardHolder.class.getMethod("upperWildcard");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType wb = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertEquals(Number.class, wb.firstBound());
        assertEquals(0, wb.interfaceBounds().length);
        assertSame(wc, wb.wildCard());
    }

    // Covers WildCardBoundedType.firstBound() ternary: lower-bound branch (super wildcard)
    @Test
    public void testWildCardBoundedType_lowerBound_firstBoundIsLowerBoundClass() throws Throwable {
        Method m = WildcardHolder.class.getMethod("lowerWildcard");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType wb = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertEquals(Integer.class, wb.firstBound());
    }

    // Covers WildCardBoundedType.equals(): null argument -> false (contract, before any cast happens)
    @Test
    public void testWildCardBoundedType_equals_null_returnsFalse() throws Throwable {
        Method m = WildcardHolder.class.getMethod("upperWildcard");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType wb = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertFalse(wb.equals(null));
    }

    // Covers WildCardBoundedType.equals(): different runtime class -> false (contract, before any cast happens)
    @Test
    public void testWildCardBoundedType_equals_differentClass_returnsFalse() throws Throwable {
        Method m = WildcardHolder.class.getMethod("upperWildcard");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType wb = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertFalse(wb.equals("not a wildcard bounded type"));
    }



    // Covers WildCardBoundedType.toString() content
    @Test
    public void testWildCardBoundedType_toString_containsFirstBound() throws Throwable {
        Method m = WildcardHolder.class.getMethod("upperWildcard");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType wb = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertTrue(wb.toString().contains("firstBound="));
    }
}
