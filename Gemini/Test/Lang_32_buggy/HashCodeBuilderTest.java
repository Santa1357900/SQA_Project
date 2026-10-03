package org.apache.commons.lang3.builder;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HashCodeBuilderTest {

    private static class SimpleClass {
        public int id = 5;
        public String name = "test";
        protected transient int transientField = 10;
        private static int staticField = 20;
    }

    private static class SubClass extends SimpleClass {
        public boolean active = true;
    }

    private static class CircularA {
        public CircularB b;
    }

    private static class CircularB {
        public CircularA a;
    }

    @Test
    public void testDefaultConstructor() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder();
        assertEquals(17, builder.toHashCode());
        assertEquals(17, builder.hashCode());
    }

    @Test
    public void testParameterizedConstructorValid() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(3, 5);
        assertEquals(3, builder.toHashCode());
    }

    @Test
    public void testParameterizedConstructorZeroInitial() throws Throwable {
        try {
            new HashCodeBuilder(0, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("initial value"));
        }
    }

    @Test
    public void testParameterizedConstructorEvenInitial() throws Throwable {
        try {
            new HashCodeBuilder(4, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("initial value"));
        }
    }

    @Test
    public void testParameterizedConstructorZeroMultiplier() throws Throwable {
        try {
            new HashCodeBuilder(3, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("multiplier"));
        }
    }

    @Test
    public void testParameterizedConstructorEvenMultiplier() throws Throwable {
        try {
            new HashCodeBuilder(3, 4);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("multiplier"));
        }
    }

    @Test
    public void testAppendBoolean() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append(true);
        builder.append(false);
        int hash = builder.toHashCode();
        // 17 * 37 + 0 = 629; 629 * 37 + 1 = 23274
        assertEquals(23274, hash);
    }

    @Test
    public void testAppendBooleanArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        boolean[] array = new boolean[] { true, false };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        boolean[] nullArray = null;
        builderNull.append(nullArray);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendByte() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append((byte) 5);
        assertEquals(17 * 37 + 5, builder.toHashCode());
    }

    @Test
    public void testAppendByteArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        byte[] array = new byte[] { 1, 2 };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((byte[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendChar() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append('a');
        assertEquals(17 * 37 + 'a', builder.toHashCode());
    }

    @Test
    public void testAppendCharArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        char[] array = new char[] { 'a', 'b' };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((char[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendDouble() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append(5.5);
        assertTrue(builder.toHashCode() != 17);
    }

    @Test
    public void testAppendDoubleArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        double[] array = new double[] { 1.1, 2.2 };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((double[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendFloat() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append(5.5f);
        assertTrue(builder.toHashCode() != 17);
    }

    @Test
    public void testAppendFloatArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        float[] array = new float[] { 1.1f, 2.2f };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((float[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendInt() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append(100);
        assertEquals(17 * 37 + 100, builder.toHashCode());
    }

    @Test
    public void testAppendIntArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        int[] array = new int[] { 1, 2 };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((int[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendLong() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append(100L);
        assertTrue(builder.toHashCode() != 17);
    }

    @Test
    public void testAppendLongArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        long[] array = new long[] { 1L, 2L };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((long[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendObject() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append((Object) "hello");
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((Object) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendObjectArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        Object[] array = new Object[] { "a", "b" };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((Object[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendShort() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append((short) 10);
        assertEquals(17 * 37 + 10, builder.toHashCode());
    }

    @Test
    public void testAppendShortArray() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        short[] array = new short[] { 1, 2 };
        builder.append(array);
        assertTrue(builder.toHashCode() != 17);

        HashCodeBuilder builderNull = new HashCodeBuilder(17, 37);
        builderNull.append((short[]) null);
        assertEquals(17 * 37, builderNull.toHashCode());
    }

    @Test
    public void testAppendSuper() throws Throwable {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.appendSuper(42);
        assertEquals(17 * 37 + 42, builder.toHashCode());
    }

    @Test
    public void testReflectionHashCodeNull() throws Throwable {
        try {
            HashCodeBuilder.reflectionHashCode(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }

        try {
            HashCodeBuilder.reflectionHashCode(17, 37, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void testReflectionHashCodeBasic() throws Throwable {
        SimpleClass obj = new SimpleClass();
        int hash1 = HashCodeBuilder.reflectionHashCode(obj);
        int hash2 = HashCodeBuilder.reflectionHashCode(17, 37, obj);
        int hash3 = HashCodeBuilder.reflectionHashCode(obj, true);
        int hash4 = HashCodeBuilder.reflectionHashCode(17, 37, obj, true);
        int hash5 = HashCodeBuilder.reflectionHashCode(17, 37, obj, false, Object.class);
        int hash6 = HashCodeBuilder.reflectionHashCode(17, 37, obj, false, Object.class, new String[] { "name" });

        assertTrue(hash1 != 0);
        assertTrue(hash2 != 0);
        assertTrue(hash3 != 0);
        assertTrue(hash4 != 0);
        assertTrue(hash5 != 0);
        assertTrue(hash6 != 0);
    }

    @Test
    public void testReflectionHashCodeWithCollectionsAndExclusions() throws Throwable {
        SimpleClass obj = new SimpleClass();
        Collection<String> excludeList = new ArrayList<String>();
        excludeList.add("name");

        int hash1 = HashCodeBuilder.reflectionHashCode(obj, excludeList);
        int hash2 = HashCodeBuilder.reflectionHashCode(obj, new String[] { "name" });
        assertTrue(hash1 != 0);
        assertTrue(hash2 != 0);
    }

    @Test
    public void testReflectionHashCodeSubClass() throws Throwable {
        SubClass obj = new SubClass();
        int hash = HashCodeBuilder.reflectionHashCode(17, 37, obj, false, SimpleClass.class);
        assertTrue(hash != 0);
    }

    @Test
    public void testRegistryAndCyclicReferences() throws Throwable {
        CircularA a = new CircularA();
        CircularB b = new CircularB();
        a.b = b;
        b.a = a;

        int hash = HashCodeBuilder.reflectionHashCode(a);
        assertTrue(hash != 0);
    }

    @Test
    public void testExplicitRegistrationMethods() throws Throwable {
        Object obj = new Object();
        assertFalse(HashCodeBuilder.isRegistered(obj));
        HashCodeBuilder.register(obj);
        assertTrue(HashCodeBuilder.isRegistered(obj));
        assertNotNull(HashCodeBuilder.getRegistry());
        HashCodeBuilder.unregister(obj);
        assertFalse(HashCodeBuilder.isRegistered(obj));
    }
}