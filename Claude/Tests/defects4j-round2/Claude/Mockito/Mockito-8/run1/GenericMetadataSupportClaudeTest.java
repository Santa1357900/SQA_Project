package org.mockito.internal.util.reflection;

import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.exceptions.base.MockitoException;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenericMetadataSupportClaudeTest {

    private interface SimpleGeneric<K extends Comparable<K>> {
        Set<Number> returningSet();
        List<? super Integer> wildcardLowerClass();
        List<? extends K> wildcardUpperTypeVar();
        K returningK();
        <O extends K> List<O> paramTypeWithTypeParams();
        <S, T extends S> T twoTypeParams();
        <O extends Number & Comparable<O>> O numberAndComparable();
        Number returningNonGeneric();
        K[] returningArray();
    }

    private interface Holder {
        List<String> listField();
    }

    private static class PlainClass {
    }

    private static class BaseClass<T> {
        public T getValue() { return null; }
    }

    private static class SubClass extends BaseClass<String> {
    }

    private static class BaseClass3<T> {
        public T getValue() { return null; }
    }

    private static class SubClass3 extends BaseClass3<List<String>> {
    }

    private GenericMetadataSupport simpleGenericMetadata() {
        return GenericMetadataSupport.inferFrom(SimpleGeneric.class);
    }

    // rawType(): inferFrom(Class) exposes that same class as raw type
    @Test
    public void testRawType_inferFromClass_returnsSameClass() throws Throwable {
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom(PlainClass.class);
        assertEquals(PlainClass.class, md.rawType());
    }

    // rawType(): inferFrom(ParameterizedType) exposes the raw type of that parameterized type
    @Test
    public void testRawType_inferFromParameterizedType_returnsRawClass() throws Throwable {
        Method m = Holder.class.getMethod("listField");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom(pt);
        assertEquals(List.class, md.rawType());
    }

    // extraInterfaces()/rawExtraInterfaces()/hasRawExtraInterfaces(): default behaviour for a plain class source
    @Test
    public void testExtraInterfaces_defaultForClassSource_isEmpty() throws Throwable {
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom(PlainClass.class);
        assertTrue(md.extraInterfaces().isEmpty());
        assertEquals(0, md.rawExtraInterfaces().length);
        assertFalse(md.hasRawExtraInterfaces());
    }

    // extraInterfaces()/hasRawExtraInterfaces(): default behaviour for a parameterized type source
    @Test
    public void testExtraInterfaces_defaultForParameterizedTypeSource_isEmpty() throws Throwable {
        Method m = Holder.class.getMethod("listField");
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom((ParameterizedType) m.getGenericReturnType());
        assertTrue(md.extraInterfaces().isEmpty());
        assertFalse(md.hasRawExtraInterfaces());
    }

    // actualTypeArguments(): a non-generic class has no type parameters, map must be empty
    @Test
    public void testActualTypeArguments_plainClass_isEmptyMap() throws Throwable {
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom(PlainClass.class);
        assertTrue(md.actualTypeArguments().isEmpty());
    }

    // actualTypeArguments(): List<String> resolves List's type variable to String.class
    @Test
    public void testActualTypeArguments_parameterizedType_mapsTypeVariableToActualClass() throws Throwable {
        Method m = Holder.class.getMethod("listField");
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom((ParameterizedType) m.getGenericReturnType());
        Map<TypeVariable, Type> args = md.actualTypeArguments();
        assertEquals(1, args.size());
        assertEquals(String.class, args.values().iterator().next());
    }

    // actualTypeArguments(): wildcard lower bound (? super Integer) is registered as a WildCardBoundedType
    @Test
    public void testActualTypeArguments_wildcardLowerBound_resolvesToWildCardBoundedType() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        Type value = returnMeta.actualTypeArguments().values().iterator().next();
        assertTrue(value instanceof GenericMetadataSupport.WildCardBoundedType);
        GenericMetadataSupport.WildCardBoundedType bounded = (GenericMetadataSupport.WildCardBoundedType) value;
        assertEquals(Integer.class, bounded.firstBound());
    }

    // resolveGenericReturnType(): plain Class return type is wrapped as a non generic return type
    @Test
    public void testResolveGenericReturnType_classReturnType_rawTypeIsReturnType() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("returningNonGeneric");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(Number.class, returnMeta.rawType());
    }

    // resolveGenericReturnType(): fixed ParameterizedType return exposes its own raw type
    @Test
    public void testResolveGenericReturnType_parameterizedReturnType_rawTypeIsRawClass() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("returningSet");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(Set.class, returnMeta.rawType());
    }

    // resolveGenericReturnType(): TypeVariable return bound by Comparable<K> resolves raw type to Comparable
    @Test
    public void testResolveGenericReturnType_typeVariableReturnType_resolvesToUpperBound() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("returningK");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(Comparable.class, returnMeta.rawType());
    }

    // resolveGenericReturnType(): type variable with class+interface bound keeps the class as raw type
    @Test
    public void testResolveGenericReturnType_multipleBounds_rawTypeIsFirstBoundClass() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("numberAndComparable");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(Number.class, returnMeta.rawType());
    }

    // extraInterfaces()/rawExtraInterfaces()/hasRawExtraInterfaces() on a TypeVariable with two bounds
    @Test
    public void testExtraInterfaces_typeVariableWithTwoBounds_includesSecondBoundOnly() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("numberAndComparable");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(1, returnMeta.extraInterfaces().size());
        assertEquals(1, returnMeta.rawExtraInterfaces().length);
        assertEquals(Comparable.class, returnMeta.rawExtraInterfaces()[0]);
        assertTrue(returnMeta.hasRawExtraInterfaces());
    }

    // resolveGenericReturnType(): <S, T extends S> with no explicit bound defaults raw type to Object
    @Test
    public void testResolveGenericReturnType_unboundedChainedTypeVariable_defaultsToObject() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("twoTypeParams");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(Object.class, returnMeta.rawType());
    }

    // resolveGenericReturnType(): ParameterizedType using a method's own type parameter still exposes raw List type
    @Test
    public void testResolveGenericReturnType_parameterizedTypeWithOwnTypeParam_rawTypeIsList() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("paramTypeWithTypeParams");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(List.class, returnMeta.rawType());
    }

    // resolveGenericReturnType(): wildcard upper bound type variable still exposes raw List type
    @Test
    public void testResolveGenericReturnType_wildcardUpperBoundTypeVar_rawTypeIsList() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardUpperTypeVar");
        GenericMetadataSupport returnMeta = simpleGenericMetadata().resolveGenericReturnType(m);
        assertEquals(List.class, returnMeta.rawType());
    }

    // resolveGenericReturnType(): GenericArrayType is not supported and must raise MockitoException
    @Test
    public void testResolveGenericReturnType_genericArrayType_throwsMockitoException() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("returningArray");
        assertTrue(m.getGenericReturnType() instanceof GenericArrayType);
        try {
            simpleGenericMetadata().resolveGenericReturnType(m);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // rawType(): type variable resolved through a parameterized superclass chain (SubClass extends BaseClass<String>)
    @Test
    public void testResolveGenericReturnType_typeVariableResolvedThroughSuperclass_resolvesToString() throws Throwable {
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom(SubClass.class);
        Method getValue = BaseClass.class.getMethod("getValue");
        GenericMetadataSupport returnMeta = md.resolveGenericReturnType(getValue);
        assertEquals(String.class, returnMeta.rawType());
    }

    // extraInterfaces(): type variable resolved directly to a ParameterizedType is reported as a single extra interface
    @Test
    public void testExtraInterfaces_typeVariableResolvedToParameterizedType_isSingletonList() throws Throwable {
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom(SubClass3.class);
        Method getValue = BaseClass3.class.getMethod("getValue");
        GenericMetadataSupport returnMeta = md.resolveGenericReturnType(getValue);
        assertEquals(List.class, returnMeta.rawType());
        assertEquals(1, returnMeta.extraInterfaces().size());
        assertTrue(returnMeta.extraInterfaces().get(0) instanceof ParameterizedType);
    }

    // rawExtraInterfaces(): excludes an extra interface whose raw type collides with the already resolved raw type
    @Test
    public void testRawExtraInterfaces_collidingWithRawType_isExcluded() throws Throwable {
        GenericMetadataSupport md = GenericMetadataSupport.inferFrom(SubClass3.class);
        Method getValue = BaseClass3.class.getMethod("getValue");
        GenericMetadataSupport returnMeta = md.resolveGenericReturnType(getValue);
        assertEquals(0, returnMeta.rawExtraInterfaces().length);
        assertFalse(returnMeta.hasRawExtraInterfaces());
    }

    // inferFrom(): a Type that is neither Class nor ParameterizedType must raise MockitoException
    @Test
    public void testInferFrom_unsupportedType_throwsMockitoException() throws Throwable {
        TypeVariable[] typeParams = SimpleGeneric.class.getMethod("twoTypeParams").getTypeParameters();
        try {
            GenericMetadataSupport.inferFrom(typeParams[0]);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // TypeVarBoundedType.firstBound(): single declared bound Comparable<K> is reported as first bound
    @Test
    public void testTypeVarBoundedType_firstBound_returnsDeclaredBound() throws Throwable {
        TypeVariable k = SimpleGeneric.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bounded = new GenericMetadataSupport.TypeVarBoundedType(k);
        assertTrue(bounded.firstBound() instanceof ParameterizedType);
        assertEquals(Comparable.class, ((ParameterizedType) bounded.firstBound()).getRawType());
    }

    // TypeVarBoundedType.interfaceBounds(): a type variable with a single bound has no extra interface bounds
    @Test
    public void testTypeVarBoundedType_interfaceBounds_emptyWhenSingleBound() throws Throwable {
        TypeVariable k = SimpleGeneric.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bounded = new GenericMetadataSupport.TypeVarBoundedType(k);
        assertEquals(0, bounded.interfaceBounds().length);
    }

    // TypeVarBoundedType.typeVariable(): getter returns the wrapped type variable
    @Test
    public void testTypeVarBoundedType_typeVariable_returnsWrappedVariable() throws Throwable {
        TypeVariable k = SimpleGeneric.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bounded = new GenericMetadataSupport.TypeVarBoundedType(k);
        assertEquals(k, bounded.typeVariable());
    }

    // TypeVarBoundedType.equals(): reflexive case, same instance must be equal
    @Test
    public void testTypeVarBoundedType_equals_sameInstance_isTrue() throws Throwable {
        TypeVariable k = SimpleGeneric.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bounded = new GenericMetadataSupport.TypeVarBoundedType(k);
        assertTrue(bounded.equals(bounded));
    }

    // TypeVarBoundedType.equals(): two wrappers of the same type variable must be equal
    @Test
    public void testTypeVarBoundedType_equals_sameTypeVariable_isTrue() throws Throwable {
        TypeVariable k1 = SimpleGeneric.class.getTypeParameters()[0];
        TypeVariable k2 = SimpleGeneric.class.getTypeParameters()[0];
        assertTrue(new GenericMetadataSupport.TypeVarBoundedType(k1).equals(new GenericMetadataSupport.TypeVarBoundedType(k2)));
    }

    // TypeVarBoundedType.equals(): wrappers of different type variables must not be equal
    @Test
    public void testTypeVarBoundedType_equals_differentTypeVariable_isFalse() throws Throwable {
        TypeVariable k = SimpleGeneric.class.getTypeParameters()[0];
        TypeVariable s = SimpleGeneric.class.getMethod("twoTypeParams").getTypeParameters()[0];
        assertFalse(new GenericMetadataSupport.TypeVarBoundedType(k).equals(new GenericMetadataSupport.TypeVarBoundedType(s)));
    }

    // TypeVarBoundedType.equals(): null and an unrelated class must not be equal, and must not throw
    @Test
    public void testTypeVarBoundedType_equals_nullAndOtherClass_isFalse() throws Throwable {
        TypeVariable k = SimpleGeneric.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType bounded = new GenericMetadataSupport.TypeVarBoundedType(k);
        assertFalse(bounded.equals(null));
        assertFalse(bounded.equals("not a bounded type"));
    }

    // TypeVarBoundedType.hashCode(): equal instances must share the same hash code
    @Test
    public void testTypeVarBoundedType_hashCode_consistentWithEquals() throws Throwable {
        TypeVariable k1 = SimpleGeneric.class.getTypeParameters()[0];
        TypeVariable k2 = SimpleGeneric.class.getTypeParameters()[0];
        GenericMetadataSupport.TypeVarBoundedType b1 = new GenericMetadataSupport.TypeVarBoundedType(k1);
        GenericMetadataSupport.TypeVarBoundedType b2 = new GenericMetadataSupport.TypeVarBoundedType(k2);
        assertEquals(b1.hashCode(), b2.hashCode());
    }

    // WildCardBoundedType.firstBound(): lower bound present (? super Integer) takes precedence over upper bound
    @Test
    public void testWildCardBoundedType_firstBound_lowerBoundPresent_returnsLowerBound() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType bounded = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertEquals(Integer.class, bounded.firstBound());
    }

    // WildCardBoundedType.firstBound(): only upper bound present (? extends K) returns the upper bound
    @Test
    public void testWildCardBoundedType_firstBound_onlyUpperBound_returnsUpperBound() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardUpperTypeVar");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType bounded = new GenericMetadataSupport.WildCardBoundedType(wc);
        TypeVariable k = SimpleGeneric.class.getTypeParameters()[0];
        assertEquals(k, bounded.firstBound());
    }

    // WildCardBoundedType.interfaceBounds(): always empty since wildcards don't support multiple bounds
    @Test
    public void testWildCardBoundedType_interfaceBounds_alwaysEmpty() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType bounded = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertEquals(0, bounded.interfaceBounds().length);
    }

    // WildCardBoundedType.wildCard(): getter returns the wrapped wildcard instance
    @Test
    public void testWildCardBoundedType_wildCard_returnsWrappedWildcard() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType bounded = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertEquals(wc, bounded.wildCard());
    }

    // WildCardBoundedType.equals(): reflexive case, same instance must be equal
    @Test
    public void testWildCardBoundedType_equals_sameInstance_isTrue() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType bounded = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertTrue(bounded.equals(bounded));
    }

    // WildCardBoundedType.equals(): a different class must return false without throwing
    @Test
    public void testWildCardBoundedType_equals_differentClass_isFalse() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType bounded = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertFalse(bounded.equals("not a bounded type"));
    }

    // WildCardBoundedType.equals(): two wrappers of the very same wildcard must be reported equal
    @Test
    public void testWildCardBoundedType_equals_sameWildcard_isTrue() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType a = new GenericMetadataSupport.WildCardBoundedType(wc);
        GenericMetadataSupport.WildCardBoundedType b = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertTrue(a.equals(b));
    }

    // WildCardBoundedType.hashCode(): must match the wrapped wildcard's hash code
    @Test
    public void testWildCardBoundedType_hashCode_matchesWildcardHashCode() throws Throwable {
        Method m = SimpleGeneric.class.getMethod("wildcardLowerClass");
        ParameterizedType pt = (ParameterizedType) m.getGenericReturnType();
        WildcardType wc = (WildcardType) pt.getActualTypeArguments()[0];
        GenericMetadataSupport.WildCardBoundedType bounded = new GenericMetadataSupport.WildCardBoundedType(wc);
        assertEquals(wc.hashCode(), bounded.hashCode());
    }
}
