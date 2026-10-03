package org.mockito.internal.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrimitivesClaudeTest {

    // primitiveTypeOf: clazz.isPrimitive() == true branch, int.class
    @Test
    public void testPrimitiveTypeOf_intPrimitive_returnsSameClass() throws Throwable {
        Class<Integer> result = Primitives.primitiveTypeOf(int.class);
        assertSame(int.class, result);
    }

    // primitiveTypeOf: clazz.isPrimitive() == true branch, boolean.class
    @Test
    public void testPrimitiveTypeOf_booleanPrimitive_returnsSameClass() throws Throwable {
        Class<Boolean> result = Primitives.primitiveTypeOf(boolean.class);
        assertSame(boolean.class, result);
    }

    // primitiveTypeOf: clazz.isPrimitive() == true branch, double.class
    @Test
    public void testPrimitiveTypeOf_doublePrimitive_returnsSameClass() throws Throwable {
        Class<Double> result = Primitives.primitiveTypeOf(double.class);
        assertSame(double.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Integer.class -> int.class
    @Test
    public void testPrimitiveTypeOf_integerWrapper_returnsIntPrimitive() throws Throwable {
        Class<Integer> result = Primitives.primitiveTypeOf(Integer.class);
        assertSame(int.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Boolean.class -> boolean.class
    @Test
    public void testPrimitiveTypeOf_booleanWrapper_returnsBooleanPrimitive() throws Throwable {
        Class<Boolean> result = Primitives.primitiveTypeOf(Boolean.class);
        assertSame(boolean.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Character.class -> char.class
    @Test
    public void testPrimitiveTypeOf_characterWrapper_returnsCharPrimitive() throws Throwable {
        Class<Character> result = Primitives.primitiveTypeOf(Character.class);
        assertSame(char.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Byte.class -> byte.class
    @Test
    public void testPrimitiveTypeOf_byteWrapper_returnsBytePrimitive() throws Throwable {
        Class<Byte> result = Primitives.primitiveTypeOf(Byte.class);
        assertSame(byte.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Short.class -> short.class
    @Test
    public void testPrimitiveTypeOf_shortWrapper_returnsShortPrimitive() throws Throwable {
        Class<Short> result = Primitives.primitiveTypeOf(Short.class);
        assertSame(short.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Long.class -> long.class
    @Test
    public void testPrimitiveTypeOf_longWrapper_returnsLongPrimitive() throws Throwable {
        Class<Long> result = Primitives.primitiveTypeOf(Long.class);
        assertSame(long.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Float.class -> float.class
    @Test
    public void testPrimitiveTypeOf_floatWrapper_returnsFloatPrimitive() throws Throwable {
        Class<Float> result = Primitives.primitiveTypeOf(Float.class);
        assertSame(float.class, result);
    }

    // primitiveTypeOf: wrapper lookup branch, Double.class -> double.class
    @Test
    public void testPrimitiveTypeOf_doubleWrapper_returnsDoublePrimitive() throws Throwable {
        Class<Double> result = Primitives.primitiveTypeOf(Double.class);
        assertSame(double.class, result);
    }

    // primitiveTypeOf: map miss branch, non-wrapper non-primitive class -> null
    @Test
    public void testPrimitiveTypeOf_nonWrapperClass_returnsNull() throws Throwable {
        Class<String> result = Primitives.primitiveTypeOf(String.class);
        assertNull(result);
    }

    // isPrimitiveWrapper: Boolean.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_booleanClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Boolean.class));
    }

    // isPrimitiveWrapper: Character.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_characterClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Character.class));
    }

    // isPrimitiveWrapper: Byte.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_byteClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Byte.class));
    }

    // isPrimitiveWrapper: Short.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_shortClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Short.class));
    }

    // isPrimitiveWrapper: Integer.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_integerClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Integer.class));
    }

    // isPrimitiveWrapper: Long.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_longClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Long.class));
    }

    // isPrimitiveWrapper: Float.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_floatClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Float.class));
    }

    // isPrimitiveWrapper: Double.class is a key -> true
    @Test
    public void testIsPrimitiveWrapper_doubleClass_true() throws Throwable {
        assertTrue(Primitives.isPrimitiveWrapper(Double.class));
    }

    // isPrimitiveWrapper: String.class is not a key -> false
    @Test
    public void testIsPrimitiveWrapper_stringClass_false() throws Throwable {
        assertFalse(Primitives.isPrimitiveWrapper(String.class));
    }

    // isPrimitiveWrapper: primitive int.class itself is not a wrapper key -> false
    @Test
    public void testIsPrimitiveWrapper_primitiveIntClass_false() throws Throwable {
        assertFalse(Primitives.isPrimitiveWrapper(int.class));
    }

    // primitiveWrapperOf: Boolean.class -> default value false
    @Test
    public void testPrimitiveWrapperOf_booleanClass_returnsFalse() throws Throwable {
        Boolean result = Primitives.primitiveWrapperOf(Boolean.class);
        assertFalse(result.booleanValue());
    }

    // primitiveWrapperOf: Character.class -> default value '\u0000'
    @Test
    public void testPrimitiveWrapperOf_characterClass_returnsNullChar() throws Throwable {
        Character result = Primitives.primitiveWrapperOf(Character.class);
        assertEquals('\u0000', result.charValue());
    }

    // primitiveWrapperOf: Byte.class -> default value 0
    @Test
    public void testPrimitiveWrapperOf_byteClass_returnsZero() throws Throwable {
        Byte result = Primitives.primitiveWrapperOf(Byte.class);
        assertEquals((byte) 0, result.byteValue());
    }

    // primitiveWrapperOf: Short.class -> default value 0
    @Test
    public void testPrimitiveWrapperOf_shortClass_returnsZero() throws Throwable {
        Short result = Primitives.primitiveWrapperOf(Short.class);
        assertEquals((short) 0, result.shortValue());
    }

    // primitiveWrapperOf: Integer.class -> default value 0
    @Test
    public void testPrimitiveWrapperOf_integerClass_returnsZero() throws Throwable {
        Integer result = Primitives.primitiveWrapperOf(Integer.class);
        assertEquals(0, result.intValue());
    }

    // primitiveWrapperOf: Long.class -> default value 0L
    @Test
    public void testPrimitiveWrapperOf_longClass_returnsZero() throws Throwable {
        Long result = Primitives.primitiveWrapperOf(Long.class);
        assertEquals(0L, result.longValue());
    }

    // primitiveWrapperOf: Float.class -> default value 0F
    @Test
    public void testPrimitiveWrapperOf_floatClass_returnsZero() throws Throwable {
        Float result = Primitives.primitiveWrapperOf(Float.class);
        assertEquals(0F, result.floatValue(), 1e-9);
    }

    // primitiveWrapperOf: Double.class -> default value 0D
    @Test
    public void testPrimitiveWrapperOf_doubleClass_returnsZero() throws Throwable {
        Double result = Primitives.primitiveWrapperOf(Double.class);
        assertEquals(0D, result.doubleValue(), 1e-9);
    }

    // primitiveWrapperOf: map miss branch, non-wrapper class -> null
    @Test
    public void testPrimitiveWrapperOf_nonWrapperClass_returnsNull() throws Throwable {
        String result = Primitives.primitiveWrapperOf(String.class);
        assertNull(result);
    }

    // primitiveValueOrNullFor: boolean.class -> default value false
    @Test
    public void testPrimitiveValueOrNullFor_booleanType_returnsFalse() throws Throwable {
        Boolean result = Primitives.primitiveValueOrNullFor(boolean.class);
        assertFalse(result.booleanValue());
    }

    // primitiveValueOrNullFor: char.class -> default value '\u0000'
    @Test
    public void testPrimitiveValueOrNullFor_charType_returnsNullChar() throws Throwable {
        Character result = Primitives.primitiveValueOrNullFor(char.class);
        assertEquals('\u0000', result.charValue());
    }

    // primitiveValueOrNullFor: byte.class -> default value 0
    @Test
    public void testPrimitiveValueOrNullFor_byteType_returnsZero() throws Throwable {
        Byte result = Primitives.primitiveValueOrNullFor(byte.class);
        assertEquals((byte) 0, result.byteValue());
    }

    // primitiveValueOrNullFor: short.class -> default value 0
    @Test
    public void testPrimitiveValueOrNullFor_shortType_returnsZero() throws Throwable {
        Short result = Primitives.primitiveValueOrNullFor(short.class);
        assertEquals((short) 0, result.shortValue());
    }

    // primitiveValueOrNullFor: int.class -> default value 0
    @Test
    public void testPrimitiveValueOrNullFor_intType_returnsZero() throws Throwable {
        Integer result = Primitives.primitiveValueOrNullFor(int.class);
        assertEquals(0, result.intValue());
    }

    // primitiveValueOrNullFor: long.class -> default value 0L
    @Test
    public void testPrimitiveValueOrNullFor_longType_returnsZero() throws Throwable {
        Long result = Primitives.primitiveValueOrNullFor(long.class);
        assertEquals(0L, result.longValue());
    }

    // primitiveValueOrNullFor: float.class -> default value 0F
    @Test
    public void testPrimitiveValueOrNullFor_floatType_returnsZero() throws Throwable {
        Float result = Primitives.primitiveValueOrNullFor(float.class);
        assertEquals(0F, result.floatValue(), 1e-9);
    }

    // primitiveValueOrNullFor: double.class -> contract requires boxed Double 0.0;
    // bug stores an Integer for double.class, causing ClassCastException here on buggy code
    @Test
    public void testPrimitiveValueOrNullFor_doubleType_returnsDoubleZero() throws Throwable {
        Double result = Primitives.primitiveValueOrNullFor(double.class);
        assertEquals(0D, result.doubleValue(), 1e-9);
    }

    // primitiveValueOrNullFor: map miss branch, non-primitive class -> null
    @Test
    public void testPrimitiveValueOrNullFor_nonPrimitiveClass_returnsNull() throws Throwable {
        String result = Primitives.primitiveValueOrNullFor(String.class);
        assertNull(result);
    }
}
