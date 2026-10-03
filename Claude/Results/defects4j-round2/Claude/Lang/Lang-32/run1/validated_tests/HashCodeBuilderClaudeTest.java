package org.apache.commons.lang3.builder;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import static org.junit.Assert.*;

public class HashCodeBuilderClaudeTest {

    private static class SingleFieldFixture {
        private int value;
        SingleFieldFixture(int value) { this.value = value; }
    }

    private static class TransientFixture {
        private transient int tValue;
        TransientFixture(int t) { this.tValue = t; }
    }

    private static class StaticFieldFixture {
        static int s = 123;
        int value = 5;
    }

    private static class TwoFieldFixture {
        int a;
        int b;
        TwoFieldFixture(int a, int b) { this.a = a; this.b = b; }
    }

    private static class BaseFixture {
        int baseVal = 10;
    }

    private static class SubFixture extends BaseFixture {
        int subVal = 20;
    }

    // default constructor: iConstant=37, iTotal starts at 17, no appends
    @Test
    public void testDefaultConstructor_noAppend_returnsSeventeen() throws Throwable {
        assertEquals(17, new HashCodeBuilder().toHashCode());
    }

    // param constructor with valid odd numbers sets iTotal=initial
    @Test
    public void testParamConstructor_validOddNumbers_setsInitialAsTotal() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder(19, 41);
        assertEquals(19, b.toHashCode());
    }

    // constructor: initial == 0 branch throws
    @Test
    public void testParamConstructor_initialZero_throwsIllegalArgumentException() throws Throwable {
        try {
            new HashCodeBuilder(0, 37);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // constructor: initial % 2 == 0 branch throws
    @Test
    public void testParamConstructor_initialEven_throwsIllegalArgumentException() throws Throwable {
        try {
            new HashCodeBuilder(4, 37);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // constructor: multiplier == 0 branch throws
    @Test
    public void testParamConstructor_multiplierZero_throwsIllegalArgumentException() throws Throwable {
        try {
            new HashCodeBuilder(17, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // constructor: multiplier % 2 == 0 branch throws
    @Test
    public void testParamConstructor_multiplierEven_throwsIllegalArgumentException() throws Throwable {
        try {
            new HashCodeBuilder(17, 4);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // negative odd numbers are valid (edge case for %2 with negatives)
    @Test
    public void testParamConstructor_negativeOddNumbers_doesNotThrow() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder(-3, -5);
        assertEquals(-3, b.toHashCode());
    }

    // append(boolean) true branch adds 0
    @Test
    public void testAppendBoolean_true_addsZero() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(true);
        assertEquals(17 * 37 + 0, b.toHashCode());
    }

    // append(boolean) false branch adds 1
    @Test
    public void testAppendBoolean_false_addsOne() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(false);
        assertEquals(17 * 37 + 1, b.toHashCode());
    }

    // append(boolean[]) null branch: only multiply, no loop iterations
    @Test
    public void testAppendBooleanArray_null_sameAsMultiplyOnly() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append((boolean[]) null);
        assertEquals(17 * 37, b.toHashCode());
    }

    // append(boolean[]) multiple elements: loop matches elementwise append
    @Test
    public void testAppendBooleanArray_withElements_matchesElementwiseAppend() throws Throwable {
        boolean[] arr = {true, false, true};
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(arr);
        int expected = 17;
        expected = expected * 37 + 0;
        expected = expected * 37 + 1;
        expected = expected * 37 + 0;
        assertEquals(expected, b.toHashCode());
    }

    // append(byte) adds raw value (including negative)
    @Test
    public void testAppendByte_value_addsValue() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append((byte) -5);
        assertEquals(17 * 37 - 5, b.toHashCode());
    }

    // append(byte[]) null branch and multi-element branch
    @Test
    public void testAppendByteArray_null_and_withElements() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((byte[]) null);
        assertEquals(17 * 37, b1.toHashCode());
        byte[] arr = {1, 2, 3};
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        int expected = 17;
        expected = expected * 37 + 1;
        expected = expected * 37 + 2;
        expected = expected * 37 + 3;
        assertEquals(expected, b2.toHashCode());
    }

    // append(char) adds char code point
    @Test
    public void testAppendChar_value_addsValue() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append('A');
        assertEquals(17 * 37 + 65, b.toHashCode());
    }

    // append(char[]) null branch and multi-element branch
    @Test
    public void testAppendCharArray_null_and_withElements() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((char[]) null);
        assertEquals(17 * 37, b1.toHashCode());
        char[] arr = {'a', 'b'};
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        int expected = 17;
        expected = expected * 37 + 'a';
        expected = expected * 37 + 'b';
        assertEquals(expected, b2.toHashCode());
    }

    // append(double) delegates via Double.doubleToLongBits then long formula
    @Test
    public void testAppendDouble_usesDoubleToLongBits() throws Throwable {
        double val = 3.14;
        long bits = Double.doubleToLongBits(val);
        int expectedPart = (int) (bits ^ (bits >> 32));
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(val);
        assertEquals(17 * 37 + expectedPart, b.toHashCode());
    }

    // append(double) -0.0 vs 0.0 differ due to doubleToLongBits bit pattern
    @Test
    public void testAppendDouble_negativeZeroDiffersFromPositiveZero() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append(0.0);
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(-0.0);
        assertTrue(b1.toHashCode() != b2.toHashCode());
    }

    // append(double[]) null branch and multi-element branch
    @Test
    public void testAppendDoubleArray_null_and_withElements() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((double[]) null);
        assertEquals(17 * 37, b1.toHashCode());
        double[] arr = {1.5, 2.5};
        long bits1 = Double.doubleToLongBits(1.5);
        long bits2 = Double.doubleToLongBits(2.5);
        int expected = 17;
        expected = expected * 37 + (int) (bits1 ^ (bits1 >> 32));
        expected = expected * 37 + (int) (bits2 ^ (bits2 >> 32));
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        assertEquals(expected, b2.toHashCode());
    }

    // append(float) uses Float.floatToIntBits
    @Test
    public void testAppendFloat_usesFloatToIntBits() throws Throwable {
        float val = 2.5f;
        int bits = Float.floatToIntBits(val);
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(val);
        assertEquals(17 * 37 + bits, b.toHashCode());
    }

    // append(float) NaN uses canonical NaN bit pattern
    @Test
    public void testAppendFloat_NaN_usesCanonicalBits() throws Throwable {
        int bits = Float.floatToIntBits(Float.NaN);
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(Float.NaN);
        assertEquals(17 * 37 + bits, b.toHashCode());
    }

    // append(float[]) null branch and multi-element branch
    @Test
    public void testAppendFloatArray_null_and_withElements() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((float[]) null);
        assertEquals(17 * 37, b1.toHashCode());
        float[] arr = {1.0f, 2.0f};
        int expected = 17;
        expected = expected * 37 + Float.floatToIntBits(1.0f);
        expected = expected * 37 + Float.floatToIntBits(2.0f);
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        assertEquals(expected, b2.toHashCode());
    }

    // append(int) adds raw value including overflow-near MAX_VALUE
    @Test
    public void testAppendInt_value_addsValue() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(Integer.MAX_VALUE);
        int expected = 17 * 37 + Integer.MAX_VALUE;
        assertEquals(expected, b.toHashCode());
    }

    // append(int[]) null branch and multi-element branch
    @Test
    public void testAppendIntArray_null_and_withElements() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((int[]) null);
        assertEquals(17 * 37, b1.toHashCode());
        int[] arr = {10, 20};
        int expected = 17;
        expected = expected * 37 + 10;
        expected = expected * 37 + 20;
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        assertEquals(expected, b2.toHashCode());
    }

    // append(long) formula uses xor of high/low 32 bits
    @Test
    public void testAppendLong_formula_matchesXorShift() throws Throwable {
        long val = 123456789012345L;
        int expectedPart = (int) (val ^ (val >> 32));
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(val);
        assertEquals(17 * 37 + expectedPart, b.toHashCode());
    }

    // append(long) formula at Long.MIN_VALUE edge
    @Test
    public void testAppendLong_minValue_matchesFormula() throws Throwable {
        long val = Long.MIN_VALUE;
        int expectedPart = (int) (val ^ (val >> 32));
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(val);
        assertEquals(17 * 37 + expectedPart, b.toHashCode());
    }

    // append(long[]) null branch and multi-element branch
    @Test
    public void testAppendLongArray_null_and_withElements() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((long[]) null);
        assertEquals(17 * 37, b1.toHashCode());
        long[] arr = {1L, 2L};
        int expected = 17;
        expected = expected * 37 + (int) (1L ^ (1L >> 32));
        expected = expected * 37 + (int) (2L ^ (2L >> 32));
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        assertEquals(expected, b2.toHashCode());
    }

    // append(Object) null branch: only multiply
    @Test
    public void testAppendObject_null_sameAsMultiplyOnly() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append((Object) null);
        assertEquals(17 * 37, b.toHashCode());
    }

    // append(Object) non-array branch uses object.hashCode()
    @Test
    public void testAppendObject_nonArray_usesObjectHashCode() throws Throwable {
        String s = "hello";
        HashCodeBuilder b = new HashCodeBuilder();
        b.append((Object) s);
        assertEquals(17 * 37 + s.hashCode(), b.toHashCode());
    }

    // append(Object) dispatches primitive array to append(int[])
    @Test
    public void testAppendObject_primitiveArrayDispatch_matchesDirectArrayAppend() throws Throwable {
        int[] arr = {1, 2, 3};
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((Object) arr);
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        assertEquals(b2.toHashCode(), b1.toHashCode());
    }

    // append(Object) dispatches Object[] array to append(Object[])
    @Test
    public void testAppendObject_objectArrayDispatch_matchesDirectArrayAppend() throws Throwable {
        String[] arr = {"a", "b"};
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((Object) arr);
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        assertEquals(b2.toHashCode(), b1.toHashCode());
    }

    // append(Object[]) null branch and multi-element branch including a null element
    @Test
    public void testAppendObjectArray_null_and_withElementsIncludingNull() throws Throwable {
        HashCodeBuilder bn = new HashCodeBuilder();
        bn.append((Object[]) null);
        assertEquals(17 * 37, bn.toHashCode());
        Object[] arr = {"x", null, Integer.valueOf(5)};
        int expected = 17;
        expected = expected * 37 + "x".hashCode();
        expected = expected * 37;
        expected = expected * 37 + Integer.valueOf(5).hashCode();
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(arr);
        assertEquals(expected, b.toHashCode());
    }

    // append(short) adds raw value including negative
    @Test
    public void testAppendShort_value_addsValue() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append((short) -1000);
        assertEquals(17 * 37 - 1000, b.toHashCode());
    }

    // append(short[]) null branch and multi-element branch
    @Test
    public void testAppendShortArray_null_and_withElements() throws Throwable {
        HashCodeBuilder b1 = new HashCodeBuilder();
        b1.append((short[]) null);
        assertEquals(17 * 37, b1.toHashCode());
        short[] arr = {7, 8};
        int expected = 17;
        expected = expected * 37 + 7;
        expected = expected * 37 + 8;
        HashCodeBuilder b2 = new HashCodeBuilder();
        b2.append(arr);
        assertEquals(expected, b2.toHashCode());
    }

    // appendSuper adds the given super hash code directly
    @Test
    public void testAppendSuper_addsSuperHashCode() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.appendSuper(99);
        assertEquals(17 * 37 + 99, b.toHashCode());
    }

    // toHashCode() and hashCode() must be consistent
    @Test
    public void testToHashCode_and_hashCode_consistent() throws Throwable {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(5);
        assertEquals(b.toHashCode(), b.hashCode());
    }

    // reflectionHashCode(int,int,Object) with a single reflected field matches manual append
    @Test
    public void testReflectionHashCode_singleField_matchesManualAppend() throws Throwable {
        SingleFieldFixture obj = new SingleFieldFixture(42);
        int expected = new HashCodeBuilder(17, 37).append(42).toHashCode();
        assertEquals(expected, HashCodeBuilder.reflectionHashCode(17, 37, obj));
    }

    // reflectionHashCode(Object) null throws IllegalArgumentException
    @Test
    public void testReflectionHashCode_nullObject_throwsIllegalArgumentException() throws Throwable {
        try {
            HashCodeBuilder.reflectionHashCode((Object) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // reflectionAppend excludes static fields (Modifier.isStatic branch)
    @Test
    public void testReflectionHashCode_staticFieldExcluded() throws Throwable {
        StaticFieldFixture obj = new StaticFieldFixture();
        int expected = new HashCodeBuilder(17, 37).append(5).toHashCode();
        assertEquals(expected, HashCodeBuilder.reflectionHashCode(17, 37, obj));
    }

    // reflectionAppend excludes transient fields when testTransients=false (default)
    @Test
    public void testReflectionHashCode_transientFieldExcludedByDefault() throws Throwable {
        TransientFixture obj = new TransientFixture(99);
        assertEquals(17, HashCodeBuilder.reflectionHashCode(17, 37, obj));
    }

    // reflectionAppend includes transient fields when testTransients=true
    @Test
    public void testReflectionHashCode_testTransientsTrue_included() throws Throwable {
        TransientFixture obj = new TransientFixture(99);
        int expected = new HashCodeBuilder(17, 37).append(99).toHashCode();
        assertEquals(expected, HashCodeBuilder.reflectionHashCode(17, 37, obj, true));
    }

    // reflectionAppend excludes named field via String[] excludeFields
    @Test
    public void testReflectionHashCode_excludeFieldsArray_excludesNamedField() throws Throwable {
        TwoFieldFixture obj = new TwoFieldFixture(3, 4);
        String[] exclude = {"b"};
        int expected = new HashCodeBuilder(17, 37).append(3).toHashCode();
        assertEquals(expected, HashCodeBuilder.reflectionHashCode(obj, exclude));
    }

    // reflectionAppend excludes named field via Collection<String> excludeFields
    @Test
    public void testReflectionHashCode_excludeFieldsCollection_excludesNamedField() throws Throwable {
        TwoFieldFixture obj = new TwoFieldFixture(3, 4);
        Collection<String> exclude = new ArrayList<String>();
        exclude.add("b");
        int expected = new HashCodeBuilder(17, 37).append(3).toHashCode();
        assertEquals(expected, HashCodeBuilder.reflectionHashCode(obj, exclude));
    }

    // reflectUpToClass stops before superclass fields (loop boundary)
    @Test
    public void testReflectionHashCode_reflectUpToClass_excludesSuperclassFields() throws Throwable {
        SubFixture obj = new SubFixture();
        int expected = new HashCodeBuilder(17, 37).append(20).toHashCode();
        assertEquals(expected, HashCodeBuilder.reflectionHashCode(17, 37, obj, false, SubFixture.class));
    }

    // reflectUpToClass=null includes superclass fields too (full traversal)
    @Test
    public void testReflectionHashCode_reflectUpToClassNull_includesSuperclassFields() throws Throwable {
        SubFixture obj = new SubFixture();
        int expected = new HashCodeBuilder(17, 37).append(20).append(10).toHashCode();
        assertEquals(expected, HashCodeBuilder.reflectionHashCode(17, 37, obj, false, null));
    }

    // registry lifecycle: register/isRegistered/unregister package-private helpers
    @Test
    public void testRegisterUnregisterIsRegistered_lifecycle() throws Throwable {
        Object obj = new Object();
        assertFalse(HashCodeBuilder.isRegistered(obj));
        HashCodeBuilder.register(obj);
        assertTrue(HashCodeBuilder.isRegistered(obj));
        HashCodeBuilder.unregister(obj);
        assertFalse(HashCodeBuilder.isRegistered(obj));
    }
}
