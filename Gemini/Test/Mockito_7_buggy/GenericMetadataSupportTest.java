package org.mockito.internal.util.reflection;

import org.junit.Test;
import org.mockito.exceptions.base.MockitoException;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class GenericMetadataSupportTest {

    private interface SampleGenericInterface<K extends Comparable<K> & Cloneable> extends Map<K, Set<Number>> {
        Set<Number> remove(Object key);
        List<? super Integer> returning_wildcard_with_class_lower_bound();
        List<? super K> returning_wildcard_with_typeVar_lower_bound();
        List<? extends K> returning_wildcard_with_typeVar_upper_bound();
        K returningK();
        <O extends K> List<O> paramType_with_type_params();
        <S, T extends S> T two_type_params();
        <O extends K> O typeVar_with_type_params();
        Number returningNonGeneric();
    }

    private static class SampleSubClass implements SampleGenericInterface<String> {
        public Set<Number> remove(Object key) { return null; }
        public List<? super Integer> returning_wildcard_with_class_lower_bound() { return null; }
        public List<? super String> returning_wildcard_with_typeVar_lower_bound() { return null; }
        public List<? extends String> returning_wildcard_with_typeVar_upper_bound() { return null; }
        public String returningK() { return null; }
        public <O extends String> List<O> paramType_with_type_params() { return null; }
        public <S, T extends S> T two_type_params() { return null; }
        public <O extends String> O typeVar_with_type_params() { return null; }
        public Number returningNonGeneric() { return null; }
        public int size() { return 0; }
        public boolean isEmpty() { return false; }
        public boolean containsKey(Object key) { return false; }
        public boolean containsValue(Object value) { return false; }
        public Set<Number> get(Object key) { return null; }
        public Set<Number> put(String key, Set<Number> value) { return null; }
        public Set<Number> remove(Object key, Object value) { return false; }
        public void putAll(Map<? extends String, ? extends Set<Number>> m) {}
        public void clear() {}
        public Set<String> keySet() { return null; }
        public Collection<Set<Number>> values() { return null; }
        public Set<Map.Entry<String, Set<Number>>> entrySet() { return null; }
    }

    @Test
    public void testInferFromClass() throws Throwable {
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(String.class);
        assertNotNull(metadata);
        assertEquals(String.class, metadata.rawType());
        assertTrue(metadata.extraInterfaces().isEmpty());
        assertEquals(0, metadata.rawExtraInterfaces().length);
        assertFalse(metadata.hasRawExtraInterfaces());
        assertNotNull(metadata.actualTypeArguments());
    }

    @Test
    public void testInferFromParameterizedType() throws Throwable {
        Type genericSuperclass = SampleSubClass.class.getGenericInterfaces()[0];
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(genericSuperclass);
        assertNotNull(metadata);
        assertEquals(Map.class, metadata.rawType());
    }

    @Test
    public void testInferFromNullThrowsException() throws Throwable {
        try {
            GenericMetadataSupport.inferFrom(null);
            fail("Should have thrown MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("type"));
        }
    }

    @Test
    public void testInferFromUnsupportedTypeThrowsException() throws Throwable {
        Type unsupportedType = new Type() {};
        try {
            GenericMetadataSupport.inferFrom(unsupportedType);
            fail("Should have thrown MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Type meta-data"));
        }
    }

    @Test
    public void testResolveGenericReturnTypeNotGeneric() throws Throwable {
        Method method = SampleGenericInterface.class.getMethod("returningNonGeneric");
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(SampleSubClass.class);
        GenericMetadataSupport returnMetadata = metadata.resolveGenericReturnType(method);
        assertNotNull(returnMetadata);
        assertEquals(Number.class, returnMetadata.rawType());
    }

    @Test
    public void testResolveGenericReturnTypeParameterized() throws Throwable {
        Method method = SampleGenericInterface.class.getMethod("remove", Object.class);
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(SampleSubClass.class);
        GenericMetadataSupport returnMetadata = metadata.resolveGenericReturnType(method);
        assertNotNull(returnMetadata);
        assertEquals(Set.class, returnMetadata.rawType());
    }

    @Test
    public void testResolveGenericReturnTypeVariable() throws Throwable {
        Method method = SampleGenericInterface.class.getMethod("returningK");
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(SampleSubClass.class);
        GenericMetadataSupport returnMetadata = metadata.resolveGenericReturnType(method);
        assertNotNull(returnMetadata);
        assertEquals(String.class, returnMetadata.rawType());
    }

    @Test
    public void testTypeVarBoundedTypeEqualsAndHashCode() throws Throwable {
        TypeVariable[] typeParams = SampleGenericInterface.class.getTypeParameters();
        GenericMetadataSupport.TypeVarBoundedType bound1 = new GenericMetadataSupport.TypeVarBoundedType(typeParams[0]);
        GenericMetadataSupport.TypeVarBoundedType bound2 = new GenericMetadataSupport.TypeVarBoundedType(typeParams[0]);

        assertEquals(bound1, bound2);
        assertEquals(bound1.hashCode(), bound2.hashCode());
        assertEquals(bound1, bound1);
        assertFalse(bound1.equals(null));
        assertFalse(bound1.equals("some string"));
        assertNotNull(bound1.toString());
        assertEquals(typeParams[0], bound1.typeVariable());
    }

    @Test
    public void testWildCardBoundedTypeMethods() throws Throwable {
        Method method = SampleGenericInterface.class.getMethod("returning_wildcard_with_class_lower_bound");
        Type genericReturnType = method.getGenericReturnType();
        ParameterizedType parameterizedType = (ParameterizedType) genericReturnType;
        java.lang.reflect.WildcardType wildcardType = (java.lang.reflect.WildcardType) parameterizedType.getActualTypeArguments()[0];

        GenericMetadataSupport.WildCardBoundedType wildCardBoundedType = new GenericMetadataSupport.WildCardBoundedType(wildcardType);
        assertNotNull(wildCardBoundedType.firstBound());
        assertEquals(0, wildCardBoundedType.interfaceBounds().length);
        assertNotNull(wildCardBoundedType.toString());
        assertEquals(wildcardType, wildCardBoundedType.wildCard());
        
        assertEquals(wildCardBoundedType, wildCardBoundedType);
        assertFalse(wildCardBoundedType.equals(null));
        assertFalse(wildCardBoundedType.equals("test"));
        assertEquals(wildcardType.hashCode(), wildCardBoundedType.hashCode());
    }
}