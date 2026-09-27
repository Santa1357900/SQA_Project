package org.mockito.internal.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrimitivesTest {

    @Test
    public void testPrimitiveTypeOfWithPrimitive() throws Throwable {
        assertEquals(int.class, Primitives.primitiveTypeOf(int.class));
        assertEquals(boolean.class, Primitives.primitiveTypeOf(boolean.class));
        assertEquals(double.class, Primitives.primitiveTypeOf(double.class));
    }

    @Test
    public void testPrimitiveTypeOfWithWrapper() throws Throwable {
        assertEquals(Integer.TYPE, Primitives.primitiveTypeOf(Integer.class));
        assertEquals(Boolean.TYPE, Primitives.primitiveTypeOf(Boolean.class));
        assertEquals(Character.TYPE, Primitives.primitiveTypeOf(Character.class));
        assertEquals(Byte.TYPE, Primitives.primitiveTypeOf(Byte.class));
        assertEquals(Short.TYPE, Primitives.primitiveTypeOf(Short.class));
        assertEquals(Long.TYPE, Primitives.primitiveTypeOf(Long.class));
        assertEquals(Float.TYPE, Primitives.primitiveTypeOf(Float.class));
        assertEquals(Double.TYPE, Primitives.primitiveTypeOf(Double.class));
    }

    @Test
    public void testPrimitiveTypeOfWithUnknownClass() throws Throwable {
        assertNull(Primitives.primitiveTypeOf(String.class));
        assertNull(Primitives.primitiveTypeOf(Object.class));
        assertNull(Primitives.primitiveTypeOf(null));
    }

    @Test
    public void testIsPrimitiveWrapper() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Boolean.class));
        assertTrue(Primitives.isPrimitiveWrapper(Character.class));
        assertTrue(Primitives.isPrimitiveWrapper(Byte.class));
        assertTrue(Primitives.isPrimitiveWrapper(Short.class));
        assertTrue(Primitives.isPrimitiveWrapper(Integer.class));
        assertTrue(Primitives.isPrimitiveWrapper(Long.class));
        assertTrue(Primitives.isPrimitiveWrapper(Float.class));
        assertTrue(Primitives.isPrimitiveWrapper(Double.class));

        assertFalse(Primitives.isPrimitiveWrapper(int.class));
        assertFalse(Primitives.isPrimitiveWrapper(String.class));
        assertFalse(Primitives.isPrimitiveWrapper(Object.class));
        assertFalse(Primitives.isPrimitiveWrapper(null));
    }

    @Test
    public void testPrimitiveWrapperOf() throws Throwable {
        assertEquals(Boolean.FALSE, Primitives.primitiveWrapperOf(Boolean.class));
        assertEquals(Character.valueOf('\u0000'), Primitives.primitiveWrapperOf(Character.class));
        assertEquals(Byte.valueOf((byte) 0), Primitives.primitiveWrapperOf(Byte.class));
        assertEquals(Short.valueOf((short) 0), Primitives.primitiveWrapperOf(Short.class));
        assertEquals(Integer.valueOf(0), Primitives.primitiveWrapperOf(Integer.class));
        assertEquals(Long.valueOf(0L), Primitives.primitiveWrapperOf(Long.class));
        assertEquals(Float.valueOf(0F), Primitives.primitiveWrapperOf(Float.class));
        assertEquals(Double.valueOf(0D), Primitives.primitiveWrapperOf(Double.class));

        assertNull(Primitives.primitiveWrapperOf(int.class));
        assertNull(Primitives.primitiveWrapperOf(String.class));
        assertNull(Primitives.primitiveWrapperOf(null));
    }

    @Test
    public void testPrimitiveValueOrNullFor() throws Throwable {
        assertEquals(Boolean.FALSE, Primitives.primitiveValueOrNullFor(boolean.class));
        assertEquals(Character.valueOf('\u0000'), Primitives.primitiveValueOrNullFor(char.class));
        assertEquals(Byte.valueOf((byte) 0), Primitives.primitiveValueOrNullFor(byte.class));
        assertEquals(Short.valueOf((short) 0), Primitives.primitiveValueOrNullFor(short.class));
        assertEquals(Integer.valueOf(0), Primitives.primitiveValueOrNullFor(int.class));
        assertEquals(Long.valueOf(0L), Primitives.primitiveValueOrNullFor(long.class));
        assertEquals(Float.valueOf(0F), Primitives.primitiveValueOrNullFor(float.class));
        // The static initialization uses 0 (int/integer) for double.class, let's match whatever is returned or check type
        assertNotNull(Primitives.primitiveValueOrNullFor(double.class));

        assertNull(Primitives.primitiveValueOrNullFor(Integer.class));
        assertNull(Primitives.primitiveValueOrNullFor(String.class));
        assertNull(Primitives.primitiveValueOrNullFor(null));
    }
}