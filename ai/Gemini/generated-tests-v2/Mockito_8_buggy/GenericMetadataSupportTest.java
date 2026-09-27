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

    private interface SampleGenericInterface<T extends Comparable<T> & Cloneable> extends List<T> {
        Set<Number> removeKey(Object key);
        T getT();
    }

    @Test
    public void testInferFromClass() throws Throwable {
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(String.class);
        assertNotNull(metadata);
        assertEquals(String.class, metadata.rawType());
        assertTrue(metadata.extraInterfaces().isEmpty());
        assertTrue(metadata.rawExtraInterfaces().length == 0);
        assertFalse(metadata.hasRawExtraInterfaces());
    }

    @Test
    public void testInferFromNull() throws Throwable {
        try {
            GenericMetadataSupport.inferFrom(null);
            fail("Should have thrown MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("type"));
        }
    }

    @Test
    public void testInferFromUnsupportedType() throws Throwable {
        Type unsupportedType = new Type() {};
        try {
            GenericMetadataSupport.inferFrom(unsupportedType);
            fail("Should have thrown MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("not supported"));
        }
    }

    @Test
    public void testInferFromParameterizedType() throws Throwable {
        Type genericSuperinterface = SampleGenericInterface.class.getGenericInterfaces()[0];
        if (genericSuperinterface instanceof ParameterizedType) {
            GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(genericSuperinterface);
            assertNotNull(metadata);
            assertEquals(List.class, metadata.rawType());
        }
    }

    @Test
    public void testResolveGenericReturnTypeClass() throws Throwable {
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(SampleGenericInterface.class);
        Method method = String.class.getMethod("toString");
        GenericMetadataSupport returnMetadata = metadata.resolveGenericReturnType(method);
        assertNotNull(returnMetadata);
        assertEquals(String.class, returnMetadata.rawType());
    }

    @Test
    public void testResolveGenericReturnTypeParameterized() throws Throwable {
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(SampleGenericInterface.class);
        Method method = SampleGenericInterface.class.getMethod("removeKey", Object.class);
        GenericMetadataSupport returnMetadata = metadata.resolveGenericReturnType(method);
        assertNotNull(returnMetadata);
        assertEquals(Set.class, returnMetadata.rawType());
    }

    @Test
    public void testResolveGenericReturnTypeVariable() throws Throwable {
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(SampleGenericInterface.class);
        Method method = SampleGenericInterface.class.getMethod("getT");
        GenericMetadataSupport returnMetadata = metadata.resolveGenericReturnType(method);
        assertNotNull(returnMetadata);
        assertEquals(Comparable.class, returnMetadata.rawType());
    }

    @Test
    public void testActualTypeArguments() throws Throwable {
        GenericMetadataSupport metadata = GenericMetadataSupport.inferFrom(SampleGenericInterface.class);
        Map<TypeVariable, Type> args = metadata.actualTypeArguments();
        assertNotNull(args);
    }

    @Test
    public void testTypeVarBoundedTypeEqualsAndHashCode() throws Throwable {
        TypeVariable[] typeParameters = SampleGenericInterface.class.getTypeParameters();
        if (typeParameters.length > 0) {
            GenericMetadataSupport.TypeVarBoundedType bound1 = new GenericMetadataSupport.TypeVarBoundedType(typeParameters[0]);
            GenericMetadataSupport.TypeVarBoundedType bound2 = new GenericMetadataSupport.TypeVarBoundedType(typeParameters[0]);
            assertEquals(bound1, bound2);
            assertEquals(bound1.hashCode(), bound2.hashCode());
            assertEquals(bound1, bound1);
            assertFalse(bound1.equals(null));
            assertFalse(bound1.equals("some string"));
            assertNotNull(bound1.toString());
            assertEquals(typeParameters[0], bound1.typeVariable());
            assertNotNull(bound1.firstBound());
            assertNotNull(bound1.interfaceBounds());
        }
    }
}