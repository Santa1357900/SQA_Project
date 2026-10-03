package org.apache.commons.lang;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ClassUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        ClassUtils utils = new ClassUtils();
        assertNotNull(utils);
    }

    @Test
    public void testGetShortClassNameObject() throws Throwable {
        assertEquals("String", ClassUtils.getShortClassName("test", "default"));
        assertEquals("default", ClassUtils.getShortClassName(null, "default"));
        assertNull(ClassUtils.getShortClassName(null, null));
    }

    @Test
    public void testGetShortClassNameClass() throws Throwable {
        assertEquals("String", ClassUtils.getShortClassName(String.class));
        assertEquals("", ClassUtils.getShortClassName((Class<?>) null));
        assertEquals("ClassUtilsTest", ClassUtils.getShortClassName(ClassUtilsTest.class));
    }

    @Test
    public void testGetShortClassNameString() throws Throwable {
        assertEquals("", ClassUtils.getShortClassName((String) null));
        assertEquals("", ClassUtils.getShortClassName(""));
        assertEquals("String", ClassUtils.getShortClassName("java.lang.String"));
        assertEquals("String", ClassUtils.getShortClassName("String"));
        assertEquals("ClassUtils.Inner", ClassUtils.getShortClassName("org.apache.commons.lang.ClassUtils$Inner"));
    }

    @Test
    public void testGetPackageNameObject() throws Throwable {
        assertEquals("java.lang", ClassUtils.getPackageName("test", "default"));
        assertEquals("default", ClassUtils.getPackageName(null, "default"));
        assertNull(ClassUtils.getPackageName(null, null));
    }

    @Test
    public void testGetPackageNameClass() throws Throwable {
        assertEquals("java.lang", ClassUtils.getPackageName(String.class));
        assertEquals("", ClassUtils.getPackageName((Class<?>) null));
    }

    @Test
    public void testGetPackageNameString() throws Throwable {
        assertEquals("", ClassUtils.getPackageName((String) null));
        assertEquals("", ClassUtils.getPackageName(""));
        assertEquals("java.lang", ClassUtils.getPackageName("java.lang.String"));
        assertEquals("", ClassUtils.getPackageName("String"));
    }

    @Test
    public void testGetAllSuperclasses() throws Throwable {
        assertNull(ClassUtils.getAllSuperclasses(null));
        List<Class<?>> superclasses = ClassUtils.getAllSuperclasses(ArrayList.class);
        assertNotNull(superclasses);
        assertTrue(superclasses.contains(AbstractList.class));
        assertTrue(superclasses.contains(Object.class));
    }

    @Test
    public void testGetAllInterfaces() throws Throwable {
        assertNull(ClassUtils.getAllInterfaces(null));
        List<Class<?>> interfaces = ClassUtils.getAllInterfaces(ArrayList.class);
        assertNotNull(interfaces);
        assertTrue(interfaces.contains(List.class));
        assertTrue(interfaces.contains(java.io.Serializable.class));
    }

    @Test
    public void testConvertClassNamesToClasses() throws Throwable {
        assertNull(ClassUtils.convertClassNamesToClasses(null));
        List<String> names = new ArrayList<String>();
        names.add("java.lang.String");
        names.add("non.existent.ClassName");
        names.add(null);

        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(names);
        assertNotNull(classes);
        assertEquals(3, classes.size());
        assertEquals(String.class, classes.get(0));
        assertNull(classes.get(1));
        assertNull(classes.get(2));
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
    public void testIsAssignableArray() throws Throwable {
        Class<?>[] array1 = new Class<?>[] { Integer.class };
        Class<?>[] array2 = new Class<?>[] { Number.class };
        Class<?>[] array3 = new Class<?>[] { String.class };

        assertTrue(ClassUtils.isAssignable(array1, array2, true));
        assertFalse(ClassUtils.isAssignable(array1, array3, true));
        assertFalse(ClassUtils.isAssignable(array1, new Class<?>[] { Integer.class, Number.class }, true));
        assertTrue(ClassUtils.isAssignable((Class<?>[]) null, (Class<?>[]) null, true));
        assertTrue(ClassUtils.isAssignable(null, new Class<?>[] { Object.class }, true));
        assertFalse(ClassUtils.isAssignable(new Class<?>[] { Object.class }, null, true));
    }

    @Test
    public void testIsAssignableClass() throws Throwable {
        assertFalse(ClassUtils.isAssignable(String.class, null));
        assertTrue(ClassUtils.isAssignable(null, Object.class));
        assertFalse(ClassUtils.isAssignable(null, Integer.TYPE));

        // Autoboxing tests
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Integer.class, true));
        assertTrue(ClassUtils.isAssignable(Integer.class, Integer.TYPE, true));
        assertFalse(ClassUtils.isAssignable(Integer.TYPE, null, true));
        assertFalse(ClassUtils.isAssignable(String.class, Integer.TYPE, true));

        // Primitive widenings
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Long.TYPE));
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Float.TYPE));
        assertTrue(ClassUtils.isAssignable(Integer.TYPE, Double.TYPE));
        assertTrue(ClassUtils.isAssignable(Long.TYPE, Float.TYPE));
        assertTrue(ClassUtils.isAssignable(Long.TYPE, Double.TYPE));
        assertTrue(ClassUtils.isAssignable(Float.TYPE, Double.TYPE));
        assertTrue(ClassUtils.isAssignable(Character.TYPE, Integer.TYPE));
        assertTrue(ClassUtils.isAssignable(Short.TYPE, Integer.TYPE));
        assertTrue(ClassUtils.isAssignable(Byte.TYPE, Short.TYPE));
        assertFalse(ClassUtils.isAssignable(Boolean.TYPE, Integer.TYPE));
        assertFalse(ClassUtils.isAssignable(Double.TYPE, Integer.TYPE));

        assertFalse(ClassUtils.isAssignable(Integer.TYPE, String.class));
        assertTrue(ClassUtils.isAssignable(String.class, Object.class));
    }

    @Test
    public void testPrimitiveToWrapper() throws Throwable {
        assertEquals(Boolean.class, ClassUtils.primitiveToWrapper(Boolean.TYPE));
        assertEquals(Byte.class, ClassUtils.primitiveToWrapper(Byte.TYPE));
        assertEquals(Character.class, ClassUtils.primitiveToWrapper(Character.TYPE));
        assertEquals(Short.class, ClassUtils.primitiveToWrapper(Short.TYPE));
        assertEquals(Integer.class, ClassUtils.primitiveToWrapper(Integer.TYPE));
        assertEquals(Long.class, ClassUtils.primitiveToWrapper(Long.TYPE));
        assertEquals(Float.class, ClassUtils.primitiveToWrapper(Float.TYPE));
        assertEquals(Double.class, ClassUtils.primitiveToWrapper(Double.TYPE));
        assertEquals(Void.TYPE, ClassUtils.primitiveToWrapper(Void.TYPE));
        assertEquals(String.class, ClassUtils.primitiveToWrapper(String.class));
        assertNull(ClassUtils.primitiveToWrapper(null));
    }

    @Test
    public void testPrimitivesToWrappers() throws Throwable {
        assertNull(ClassUtils.primitivesToWrappers(null));
        Class<?>[] empty = new Class<?>[0];
        assertArrayEquals(empty, ClassUtils.primitivesToWrappers(empty));

        Class<?>[] primitives = new Class<?>[] { Integer.TYPE, String.class };
        Class<?>[] wrappers = ClassUtils.primitivesToWrappers(primitives);
        assertEquals(Integer.class, wrappers[0]);
        assertEquals(String.class, wrappers[1]);
    }

    @Test
    public void testWrapperToPrimitive() throws Throwable {
        assertEquals(Boolean.TYPE, ClassUtils.wrapperToPrimitive(Boolean.class));
        assertEquals(Byte.TYPE, ClassUtils.wrapperToPrimitive(Byte.class));
        assertEquals(Character.TYPE, ClassUtils.wrapperToPrimitive(Character.class));
        assertEquals(Short.TYPE, ClassUtils.wrapperToPrimitive(Short.class));
        assertEquals(Integer.TYPE, ClassUtils.wrapperToPrimitive(Integer.class));
        assertEquals(Long.TYPE, ClassUtils.wrapperToPrimitive(Long.class));
        assertEquals(Float.TYPE, ClassUtils.wrapperToPrimitive(Float.class));
        assertEquals(Double.TYPE, ClassUtils.wrapperToPrimitive(Double.class));
        assertNull(ClassUtils.wrapperToPrimitive(Void.TYPE));
        assertNull(ClassUtils.wrapperToPrimitive(String.class));
        assertNull(ClassUtils.wrapperToPrimitive(null));
    }

    @Test
    public void testWrappersToPrimitives() throws Throwable {
        assertNull(ClassUtils.wrappersToPrimitives(null));
        Class<?>[] empty = new Class<?>[0];
        assertArrayEquals(empty, ClassUtils.wrappersToPrimitives(empty));

        Class<?>[] wrappers = new Class<?>[] { Integer.class, String.class };
        Class<?>[] primitives = ClassUtils.wrappersToPrimitives(wrappers);
        assertEquals(Integer.TYPE, primitives[0]);
        assertNull(primitives[1]);
    }

    @Test
    public void testIsInnerClass() throws Throwable {
        assertFalse(ClassUtils.isInnerClass(null));
        assertFalse(ClassUtils.isInnerClass(String.class));
        assertTrue(ClassUtils.isInnerClass(Thread.State.class));
    }

    @Test
    public void testGetClass() throws Throwable {
        assertEquals(String.class, ClassUtils.getClass("java.lang.String"));
        assertEquals(String.class, ClassUtils.getClass("java.lang.String", false));
        assertEquals(String.class, ClassUtils.getClass(Thread.currentThread().getContextClassLoader(), "java.lang.String"));
        assertEquals(String.class, ClassUtils.getClass(Thread.currentThread().getContextClassLoader(), "java.lang.String", false));
        assertEquals(Integer.TYPE, ClassUtils.getClass("int"));
        assertEquals(String[].class, ClassUtils.getClass("java.lang.String[]"));
        assertEquals(int[].class, ClassUtils.getClass("int[]"));
        assertEquals(int[].class, ClassUtils.getClass("[I"));
    }

    @Test(expected = ClassNotFoundException.class)
    public void testGetClassException() throws Throwable {
        ClassUtils.getClass("non.existent.Class");
    }

    @Test
    public void testGetPublicMethod() throws Throwable {
        Method m = ClassUtils.getPublicMethod(String.class, "substring", new Class<?>[] { Integer.TYPE });
        assertNotNull(m);

        try {
            ClassUtils.getPublicMethod(String.class, "nonExistentMethod", new Class<?>[0]);
            fail("Expected NoSuchMethodException");
        } catch (NoSuchMethodException e) {
            assertTrue(e.getMessage().contains("Can't find a public method"));
        }

        try {
            ClassUtils.getPublicMethod(null, "substring", new Class<?>[0]);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testToClass() throws Throwable {
        assertNull(ClassUtils.toClass(null));
        assertArrayEquals(new Class<?>[0], ClassUtils.toClass(new Object[0]));

        Object[] array = new Object[] { "test", Integer.valueOf(1) };
        Class<?>[] classes = ClassUtils.toClass(array);
        assertEquals(String.class, classes[0]);
        assertEquals(Integer.class, classes[1]);
    }

    @Test
    public void testCanonicalNames() throws Throwable {
        assertEquals("String", ClassUtils.getShortCanonicalName("java.lang.String"));
        assertEquals("String", ClassUtils.getShortCanonicalName(String.class));
        assertEquals("String", ClassUtils.getShortCanonicalName("test", "default"));
        assertNull(ClassUtils.getShortCanonicalName(null, null));
        assertEquals("", ClassUtils.getShortCanonicalName((Class<?>) null));

        assertEquals("java.lang", ClassUtils.getPackageCanonicalName("java.lang.String"));
        assertEquals("java.lang", ClassUtils.getPackageCanonicalName(String.class));
        assertEquals("java.lang", ClassUtils.getPackageCanonicalName("test", "default"));
        assertNull(ClassUtils.getPackageCanonicalName(null, null));
        assertEquals("", ClassUtils.getPackageCanonicalName((Class<?>) null));

        assertEquals("int[]", ClassUtils.getClass("[I").getCanonicalName() == null ? "int[]" : ClassUtils.getClass("
[I").getName()); // just exercises internal canonical mapping paths via getClass or similar
    }
}