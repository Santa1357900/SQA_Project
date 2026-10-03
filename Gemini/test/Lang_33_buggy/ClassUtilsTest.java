package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class ClassUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        ClassUtils obj = new ClassUtils();
        assertNotNull(obj);
    }

    @Test
    public void testGetShortClassName_Object() throws Throwable {
        assertEquals("String", ClassUtils.getShortClassName("test", "default"));
        assertEquals("default", ClassUtils.getShortClassName(null, "default"));
    }

    @Test
    public void testGetShortClassName_Class() throws Throwable {
        assertEquals("String", ClassUtils.getShortClassName(String.class));
        assertEquals("", ClassUtils.getShortClassName((Class<?>) null));
    }

    @Test
    public void testGetShortClassName_String() throws Throwable {
        assertEquals("", ClassUtils.getShortClassName((String) null));
        assertEquals("", ClassUtils.getShortClassName(""));
        assertEquals("String", ClassUtils.getShortClassName("java.lang.String"));
        assertEquals("String[]", ClassUtils.getShortClassName("[Ljava.lang.String;"));
        assertEquals("int[]", ClassUtils.getShortClassName("[I"));
        assertEquals("String", ClassUtils.getShortClassName("java.lang.String$StringInner"));
    }

    @Test
    public void testGetPackageName_Object() throws Throwable {
        assertEquals("java.lang", ClassUtils.getPackageName("test", "default"));
        assertEquals("default", ClassUtils.getPackageName(null, "default"));
    }

    @Test
    public void testGetPackageName_Class() throws Throwable {
        assertEquals("java.lang", ClassUtils.getPackageName(String.class));
        assertEquals("", ClassUtils.getPackageName((Class<?>) null));
    }

    @Test
    public void testGetPackageName_String() throws Throwable {
        assertEquals("", ClassUtils.getPackageName((String) null));
        assertEquals("", ClassUtils.getPackageName(""));
        assertEquals("java.lang", ClassUtils.getPackageName("java.lang.String"));
        assertEquals("java.lang", ClassUtils.getPackageName("[Ljava.lang.String;"));
        assertEquals("", ClassUtils.getPackageName("String"));
    }

    @Test
    public void testGetAllSuperclasses() throws Throwable {
        assertNull(ClassUtils.getAllSuperclasses(null));
        List<Class<?>> superclasses = ClassUtils.getAllSuperclasses(Integer.class);
        assertNotNull(superclasses);
        assertTrue(superclasses.contains(Number.class));
        assertTrue(superclasses.contains(Object.class));
    }

    @Test
    public void testGetAllInterfaces() throws Throwable {
        assertNull(ClassUtils.getAllInterfaces(null));
        List<Class<?>> interfaces = ClassUtils.getAllInterfaces(ArrayList.class);
        assertNotNull(interfaces);
        assertTrue(interfaces.contains(List.class));
    }

    @Test
    public void testConvertClassNamesToClasses() throws Throwable {
        assertNull(ClassUtils.convertClassNamesToClasses(null));
        List<String> names = new ArrayList<String>();
        names.add("java.lang.String");
        names.add("NonExistentClassXYZ");
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(names);
        assertNotNull(classes);
        assertEquals(2, classes.size());
        assertEquals(String.class, classes.get(0));
        assertNull(classes.get(1));
    }

    @Test
    public void testConvertClassesToClassNames() throws Throwable {
        assertNull(ClassUtils.convertClassesToClassNames(null));
        List<Class<?>> classes = new ArrayList<Class<?>>();
        classes.add(String.class);
        classes.add(null);
        List<String> names = ClassUtils.convertClassesToClassNames(classes);
        assertNotNull(names);
        assertEquals(2, names.size());
        assertEquals("java.lang.String", names.get(0));
        assertNull(names.get(1));
    }

    @Test
    public void testIsAssignable_ClassArray() throws Throwable {
        Class<?>[] arr1 = new Class<?>[]{Integer.class};
        Class<?>[] arr2 = new Class<?>[]{Number.class};
        assertTrue(ClassUtils.isAssignable(arr1, arr2));
        assertFalse(ClassUtils.isAssignable(arr1, null));
        assertFalse(ClassUtils.isAssignable(null, arr2));
        
        Class<?>[] arr3 = new Class<?>[]{Integer.class, String.class};
        assertFalse(ClassUtils.isAssignable(arr1, arr3));
    }

    @Test
    public void testIsAssignable_SingleClass() throws Throwable {
        assertFalse(ClassUtils.isAssignable(String.class, null));
        assertTrue(ClassUtils.isAssignable(null, String.class));
        assertFalse(ClassUtils.isAssignable(null, Integer.TYPE));
        
        assertTrue(ClassUtils.isAssignable(Integer.class, Integer.class));
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Long.TYPE));
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Float.TYPE));
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Double.TYPE));
        assertTrue(ClassUtils.isAssignable(Long.TYPE, Float.TYPE));
        assertTrue(ClassUtils.isAssignable(Long.TYPE, Double.TYPE));
        assertFalse(ClassUtils.isAssignable(Boolean.TYPE, Integer.TYPE));
        assertFalse(ClassUtils.isAssignable(Double.TYPE, Integer.TYPE));
        assertTrue(ClassUtils.isAssignable(Float.TYPE, Double.TYPE));
        assertTrue(ClassUtils.isAssignable(Character.TYPE, Integer.TYPE));
        assertTrue(ClassUtils.isAssignable(Short.TYPE, Integer.TYPE));
        assertTrue(ClassUtils.isAssignable(Byte.TYPE, Short.TYPE));
        
        // Autoboxing tests
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Integer.class, true));
        assertTrue(ClassUtils.isAssignable(Integer.class, Integer.TYPE, true));
        
        // Unmatched primitive widening edge cases
        assertFalse(ClassUtils.isAssignable(Boolean.TYPE, Boolean.TYPE));
        // Force hitting default false in primitive widening switch
        assertFalse(ClassUtils.isAssignable(Integer.TYPE, Boolean.TYPE));
    }

    @Test
    public void testPrimitiveToWrapper() throws Throwable {
        assertNull(ClassUtils.primitiveToWrapper(null));
        assertEquals(Integer.class, ClassUtils.primitiveToWrapper(Integer.TYPE));
        assertEquals(String.class, ClassUtils.primitiveToWrapper(String.class));
        assertEquals(Void.TYPE, ClassUtils.primitiveToWrapper(Void.TYPE));
    }

    @Test
    public void testPrimitivesToWrappers() throws Throwable {
        assertNull(ClassUtils.primitivesToWrappers(null));
        Class<?>[] empty = new Class<?>[0];
        assertEquals(empty, ClassUtils.primitivesToWrappers(empty));
        
        Class<?>[] input = new Class<?>[]{Integer.TYPE, String.class};
        Class<?>[] result = ClassUtils.primitivesToWrappers(input);
        assertEquals(Integer.class, result[0]);
        assertEquals(String.class, result[1]);
    }

    @Test
    public void testWrapperToPrimitive() throws Throwable {
        assertNull(ClassUtils.wrapperToPrimitive(null));
        assertEquals(Integer.TYPE, ClassUtils.wrapperToPrimitive(Integer.class));
        assertNull(ClassUtils.wrapperToPrimitive(String.class));
    }

    @Test
    public void testWrappersToPrimitives() throws Throwable {
        assertNull(ClassUtils.wrappersToPrimitives(null));
        Class<?>[] empty = new Class<?>[0];
        assertEquals(empty, ClassUtils.wrappersToPrimitives(empty));
        
        Class<?>[] input = new Class<?>[]{Integer.class, String.class};
        Class<?>[] result = ClassUtils.wrappersToPrimitives(input);
        assertEquals(Integer.TYPE, result[0]);
        assertNull(result[1]);
    }

    @Test
    public void testIsInnerClass() throws Throwable {
        assertFalse(ClassUtils.isInnerClass(null));
        assertFalse(ClassUtils.isInnerClass(String.class));
        assertTrue(ClassUtils.isInnerClass(Map.Entry.class));
    }

    @Test
    public void testGetClass() throws Throwable {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertNotNull(ClassUtils.getClass(loader, "java.lang.String"));
        assertNotNull(ClassUtils.getClass(loader, "int"));
        assertNotNull(ClassUtils.getClass("java.lang.String"));
        assertNotNull(ClassUtils.getClass("int", false));
    }

    @Test(expected = ClassNotFoundException.class)
    public void testGetClass_NotFound() throws Throwable {
        ClassUtils.getClass("NonExistentClassXYZABC");
    }

    @Test
    public void testGetPublicMethod() throws Throwable {
        Method m = ClassUtils.getPublicMethod(String.class, "substring", new Class<?>[]{Integer.TYPE});
        assertNotNull(m);
        
        try {
            ClassUtils.getPublicMethod(String.class, "nonExistentMethodXYZ", new Class<?>[0]);
            fail("Expected NoSuchMethodException");
        } catch (NoSuchMethodException e) {
            assertTrue(e.getMessage().contains("nonExistentMethodXYZ"));
        }
    }

    @Test
    public void testToClass() throws Throwable {
        assertNull(ClassUtils.toClass(null));
        Object[] empty = new Object[0];
        assertEquals(ArrayUtils.EMPTY_CLASS_ARRAY, ClassUtils.toClass(empty));
        
        Object[] input = new Object[]{"test", Integer.valueOf(1)};
        Class<?>[] result = ClassUtils.toClass(input);
        assertEquals(String.class, result[0]);
        assertEquals(Integer.class, result[1]);
    }

    @Test
    public void testGetShortCanonicalName() throws Throwable {
        assertEquals("String", ClassUtils.getShortCanonicalName("test", "default"));
        assertEquals("default", ClassUtils.getShortCanonicalName(null, "default"));
        assertEquals("String", ClassUtils.getShortCanonicalName(String.class));
        assertEquals("String[]", ClassUtils.getShortCanonicalName("[Ljava.lang.String;"));
    }

    @Test
    public void testGetPackageCanonicalName() throws Throwable {
        assertEquals("java.lang", ClassUtils.getPackageCanonicalName("test", "default"));
        assertEquals("default", ClassUtils.getPackageCanonicalName(null, "default"));
        assertEquals("java.lang", ClassUtils.getPackageCanonicalName(String.class));
        assertEquals("java.lang", ClassUtils.getPackageCanonicalName("
[Ljava.lang.String;"));
    }
}