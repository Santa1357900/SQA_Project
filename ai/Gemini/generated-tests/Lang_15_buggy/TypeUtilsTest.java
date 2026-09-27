package org.apache.commons.lang3.reflect;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Array;
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
import java.util.Set;

public class TypeUtilsTest {

    private static interface SampleInterface<T> {
    }

    private static class SampleClass<T> implements SampleInterface<T> {
    }

    private static class ChildClass extends SampleClass<String> {
    }

    @Test
    public void testConstructor() throws Throwable {
        TypeUtils utils = new TypeUtils();
        assertNotNull(utils);
    }

    @Test
    public void testIsAssignableBasic() throws Throwable {
        assertTrue(TypeUtils.isAssignable(String.class, Object.class));
        assertFalse(TypeUtils.isAssignable(Object.class, String.class));
        assertTrue(TypeUtils.isAssignable(null, Object.class));
        assertFalse(TypeUtils.isAssignable(null, int.class));
        assertTrue(TypeUtils.isAssignable(int.class, int.class));
        assertFalse(TypeUtils.isAssignable(String.class, null));
    }

    @Test
    public void testIsInstance() throws Throwable {
        assertFalse(TypeUtils.isInstance("test", null));
        assertTrue(TypeUtils.isInstance(null, Object.class));
        assertFalse(TypeUtils.isInstance(null, int.class));
        assertTrue(TypeUtils.isInstance("test", String.class));
        assertFalse(TypeUtils.isInstance(new Object(), String.class));
    }

    @Test
    public void testNormalizeUpperBounds() throws Throwable {
        Type[] singleBound = new Type[] { String.class };
        assertSame(singleBound, TypeUtils.normalizeUpperBounds(singleBound));

        Type[] bounds = new Type[] { CharSequence.class, String.class };
        Type[] normalized = TypeUtils.normalizeUpperBounds(bounds);
        assertNotNull(normalized);
    }

    @Test
    public void testGetImplicitBounds() throws Throwable {
        TypeVariable<Class<SampleClass>>[] typeParams = SampleClass.class.getTypeParameters();
        if (typeParams.length > 0) {
            Type[] bounds = TypeUtils.getImplicitBounds(typeParams[0]);
            assertNotNull(bounds);
            assertTrue(bounds.length > 0);
        }
    }

    @Test
    public void testGetArrayComponentType() throws Throwable {
        assertNull(TypeUtils.getArrayComponentType(String.class));
        assertEquals(String.class, TypeUtils.getArrayComponentType(String[].class));
    }

    @Test
    public void testIsArrayType() throws Throwable {
        assertTrue(TypeUtils.isArrayType(String[].class));
        assertFalse(TypeUtils.isArrayType(String.class));
    }

    @Test
    public void testGetTypeArgumentsClass() throws Throwable {
        Map<TypeVariable<?>, Type> map = TypeUtils.getTypeArguments(ChildClass.class, SampleClass.class);
        assertNotNull(map);
    }

    @Test
    public void testTypesSatisfyVariables() throws Throwable {
        Map<TypeVariable<?>, Type> map = new HashMap<TypeVariable<?>, Type>();
        assertTrue(TypeUtils.typesSatisfyVariables(map));
    }

    @Test
    public void testGetRawTypeWithClass() throws Throwable {
        assertEquals(String.class, TypeUtils.getRawType(String.class, Object.class));
        assertNull(TypeUtils.getRawType(null, null));
    }
}