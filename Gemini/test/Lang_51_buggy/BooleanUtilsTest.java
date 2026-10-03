package org.apache.commons.lang;

import org.junit.Test;
import static org.junit.Assert.*;

public class BooleanUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        BooleanUtils util = new BooleanUtils();
        assertNotNull(util);
    }

    @Test
    public void testNegate() throws Throwable {
        assertEquals(Boolean.FALSE, BooleanUtils.negate(Boolean.TRUE));
        assertEquals(Boolean.TRUE, BooleanUtils.negate(Boolean.FALSE));
        assertNull(BooleanUtils.negate(null));
    }

    @Test
    public void testIsTrue() throws Throwable {
        assertTrue(BooleanUtils.isTrue(Boolean.TRUE));
        assertFalse(BooleanUtils.isTrue(Boolean.FALSE));
        assertFalse(BooleanUtils.isTrue(null));
    }

    @Test
    public void testIsNotTrue() throws Throwable {
        assertFalse(BooleanUtils.isNotTrue(Boolean.TRUE));
        assertTrue(BooleanUtils.isNotTrue(Boolean.FALSE));
        assertTrue(BooleanUtils.isNotTrue(null));
    }

    @Test
    public void testIsFalse() throws Throwable {
        assertFalse(BooleanUtils.isFalse(Boolean.TRUE));
        assertTrue(BooleanUtils.isFalse(Boolean.FALSE));
        assertFalse(BooleanUtils.isFalse(null));
    }

    @Test
    public void testIsNotFalse() throws Throwable {
        assertTrue(BooleanUtils.isNotFalse(Boolean.TRUE));
        assertFalse(BooleanUtils.isNotFalse(Boolean.FALSE));
        assertTrue(BooleanUtils.isNotFalse(null));
    }

    @Test
    public void testToBooleanObjectFromBoolean() throws Throwable {
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(true));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(false));
    }

    @Test
    public void testToBooleanFromBooleanObject() throws Throwable {
        assertTrue(BooleanUtils.toBoolean(Boolean.TRUE));
        assertFalse(BooleanUtils.toBoolean(Boolean.FALSE));
        assertFalse(BooleanUtils.toBoolean(null));
    }

    @Test
    public void testToBooleanDefaultIfNull() throws Throwable {
        assertTrue(BooleanUtils.toBooleanDefaultIfNull(Boolean.TRUE, false));
        assertFalse(BooleanUtils.toBooleanDefaultIfNull(Boolean.FALSE, true));
        assertTrue(BooleanUtils.toBooleanDefaultIfNull(null, true));
        assertFalse(BooleanUtils.toBooleanDefaultIfNull(null, false));
    }

    @Test
    public void testToBooleanInt() throws Throwable {
        assertFalse(BooleanUtils.toBoolean(0));
        assertTrue(BooleanUtils.toBoolean(1));
        assertTrue(BooleanUtils.toBoolean(-1));
        assertTrue(BooleanUtils.toBoolean(2));
    }

    @Test
    public void testToBooleanObjectInt() throws Throwable {
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(0));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(1));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(-1));
    }

    @Test
    public void testToBooleanObjectInteger() throws Throwable {
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(Integer.valueOf(0)));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(Integer.valueOf(1)));
        assertNull(BooleanUtils.toBooleanObject((Integer) null));
    }

    @Test
    public void testToBooleanIntValues() throws Throwable {
        assertTrue(BooleanUtils.toBoolean(1, 1, 0));
        assertFalse(BooleanUtils.toBoolean(0, 1, 0));
        assertFalse(BooleanUtils.toBoolean(2, 1, 2));
        assertTrue(BooleanUtils.toBoolean(2, 2, 0));

        boolean thrown = false;
        try {
            BooleanUtils.toBoolean(5, 1, 0);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testToBooleanIntegerValues() throws Throwable {
        Integer one = Integer.valueOf(1);
        Integer zero = Integer.valueOf(0);
        Integer two = Integer.valueOf(2);

        assertTrue(BooleanUtils.toBoolean(zero, zero, one));
        assertFalse(BooleanUtils.toBoolean(one, zero, one));
        assertTrue(BooleanUtils.toBoolean(null, null, zero));
        assertFalse(BooleanUtils.toBoolean(null, zero, null));

        boolean thrown = false;
        try {
            BooleanUtils.toBoolean(two, one, zero);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            BooleanUtils.toBoolean(null, one, zero);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testToBooleanObjectIntValues() throws Throwable {
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(0, 0, 2, 3));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(2, 1, 2, 3));
        assertNull(BooleanUtils.toBooleanObject(3, 1, 2, 3));

        boolean thrown = false;
        try {
            BooleanUtils.toBooleanObject(5, 1, 2, 3);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testToBooleanObjectIntegerValues() throws Throwable {
        Integer i0 = Integer.valueOf(0);
        Integer i1 = Integer.valueOf(1);
        Integer i2 = Integer.valueOf(2);
        Integer i3 = Integer.valueOf(3);

        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(i0, i0, i2, i3));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(i2, i1, i2, i3));
        assertNull(BooleanUtils.toBooleanObject(i3, i1, i2, i3));

        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(null, null, i2, i3));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(null, i1, null, i3));
        assertNull(BooleanUtils.toBooleanObject(null, i1, i2, null));

        boolean thrown = false;
        try {
            BooleanUtils.toBooleanObject(i1, i2, i3, i0);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            BooleanUtils.toBooleanObject(null, i1, i2, i3);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testToIntegerPrimitive() throws Throwable {
        assertEquals(1, BooleanUtils.toInteger(true));
        assertEquals(0, BooleanUtils.toInteger(false));
    }

    @Test
    public void testToIntegerObjectBoolean() throws Throwable {
        assertEquals(NumberUtils.INTEGER_ONE, BooleanUtils.toIntegerObject(true));
        assertEquals(NumberUtils.INTEGER_ZERO, BooleanUtils.toIntegerObject(false));
    }

    @Test
    public void testToIntegerObjectBooleanObj() throws Throwable {
        assertEquals(NumberUtils.INTEGER_ONE, BooleanUtils.toIntegerObject(Boolean.TRUE));
        assertEquals(NumberUtils.INTEGER_ZERO, BooleanUtils.toIntegerObject(Boolean.FALSE));
        assertNull(BooleanUtils.toIntegerObject((Boolean) null));
    }

    @Test
    public void testToIntegerWithValues() throws Throwable {
        assertEquals(5, BooleanUtils.toInteger(true, 5, 3));
        assertEquals(3, BooleanUtils.toInteger(false, 5, 3));
    }

    @Test
    public void testToIntegerWithNullValue() throws Throwable {
        assertEquals(5, BooleanUtils.toInteger(Boolean.TRUE, 5, 3, 1));
        assertEquals(3, BooleanUtils.toInteger(Boolean.FALSE, 5, 3, 1));
        assertEquals(1, BooleanUtils.toInteger(null, 5, 3, 1));
    }

    @Test
    public void testToIntegerObjectWithValues() throws Throwable {
        Integer i5 = Integer.valueOf(5);
        Integer i3 = Integer.valueOf(3);
        assertEquals(i5, BooleanUtils.toIntegerObject(true, i5, i3));
        assertEquals(i3, BooleanUtils.toIntegerObject(false, i5, i3));
    }

    @Test
    public void testToIntegerObjectWithNullValue() throws Throwable {
        Integer i5 = Integer.valueOf(5);
        Integer i3 = Integer.valueOf(3);
        Integer i1 = Integer.valueOf(1);
        assertEquals(i5, BooleanUtils.toIntegerObject(Boolean.TRUE, i5, i3, i1));
        assertEquals(i3, BooleanUtils.toIntegerObject(Boolean.FALSE, i5, i3, i1));
        assertEquals(i1, BooleanUtils.toIntegerObject(null, i5, i3, i1));
    }

    @Test
    public void testToBooleanObjectString() throws Throwable {
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject("true"));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject("TRUE"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject("false"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject("FALSE"));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject("on"));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject("ON"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject("off"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject("OFF"));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject("yes"));
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject("YES"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject("no"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject("NO"));
        assertNull(BooleanUtils.toBooleanObject("blue"));
        assertNull(BooleanUtils.toBooleanObject((String) null));
    }

    @Test
    public void testToBooleanObjectStringMatch() throws Throwable {
        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject("true", "true", "false", "null"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject("false", "true", "false", "null"));
        assertNull(BooleanUtils.toBooleanObject("null", "true", "false", "null"));

        assertEquals(Boolean.TRUE, BooleanUtils.toBooleanObject(null, null, "false", "null"));
        assertEquals(Boolean.FALSE, BooleanUtils.toBooleanObject(null, "true", null, "null"));
        assertNull(BooleanUtils.toBooleanObject(null, "true", "false", null));

        boolean thrown = false;
        try {
            BooleanUtils.toBooleanObject("other", "true", "false", "null");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            BooleanUtils.toBooleanObject(null, "true", "false", "null");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testToBooleanStringOptimised() throws Throwable {
        // Test str == "true" optimisation branch
        String internedTrue = "true";
        assertTrue(BooleanUtils.toBoolean(internedTrue));

        assertFalse(BooleanUtils.toBoolean((String) null));

        // length 2
        assertTrue(BooleanUtils.toBoolean("on"));
        assertTrue(BooleanUtils.toBoolean("ON"));
        assertFalse(BooleanUtils.toBoolean("ok"));

        // length 3
        assertTrue(BooleanUtils.toBoolean("yes"));
        assertTrue(BooleanUtils.toBoolean("YES"));
        assertTrue(BooleanUtils.toBoolean("Yes"));
        assertFalse(BooleanUtils.toBoolean("yET"));

        // length 4
        assertTrue(BooleanUtils.toBoolean("true"));
        assertTrue(BooleanUtils.toBoolean("TRUE"));
        assertTrue(BooleanUtils.toBoolean("True"));
        assertFalse(BooleanUtils.toBoolean("text"));

        // other lengths
        assertFalse(BooleanUtils.toBoolean("f"));
        assertFalse(BooleanUtils.toBoolean("longerString"));
    }

    @Test
    public void testToBooleanStringMatch() throws Throwable {
        assertTrue(BooleanUtils.toBoolean("true", "true", "false"));
        assertFalse(BooleanUtils.toBoolean("false", "true", "false"));

        assertTrue(BooleanUtils.toBoolean(null, null, "false"));
        assertFalse(BooleanUtils.toBoolean(null, "true", null));

        boolean thrown = false;
        try {
            BooleanUtils.toBoolean("other", "true", "false");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            BooleanUtils.toBoolean(null, "true", "false");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testToStringTrueFalse() throws Throwable {
        assertEquals("true", BooleanUtils.toStringTrueFalse(Boolean.TRUE));
        assertEquals("false", BooleanUtils.toStringTrueFalse(Boolean.FALSE));
        assertNull(BooleanUtils.toStringTrueFalse((Boolean) null));

        assertEquals("true", BooleanUtils.toStringTrueFalse(true));
        assertEquals("false", BooleanUtils.toStringTrueFalse(false));
    }

    @Test
    public void testToStringOnOff() throws Throwable {
        assertEquals("on", BooleanUtils.toStringOnOff(Boolean.TRUE));
        assertEquals("off", BooleanUtils.toStringOnOff(Boolean.FALSE));
        assertNull(BooleanUtils.toStringOnOff((Boolean) null));

        assertEquals("on", BooleanUtils.toStringOnOff(true));
        assertEquals("off", BooleanUtils.toStringOnOff(false));
    }

    @Test
    public void testToStringYesNo() throws Throwable {
        assertEquals("yes", BooleanUtils.toStringYesNo(Boolean.TRUE));
        assertEquals("no", BooleanUtils.toStringYesNo(Boolean.FALSE));
        assertNull(BooleanUtils.toStringYesNo((Boolean) null));

        assertEquals("yes", BooleanUtils.toStringYesNo(true));
        assertEquals("no", BooleanUtils.toStringYesNo(false));
    }

    @Test
    public void testToStringCustom() throws Throwable {
        assertEquals("A", BooleanUtils.toString(Boolean.TRUE, "A", "B", "C"));
        assertEquals("B", BooleanUtils.toString(Boolean.FALSE, "A", "B", "C"));
        assertEquals("C", BooleanUtils.toString(null, "A", "B", "C"));

        assertEquals("A", BooleanUtils.toString(true, "A", "B"));
        assertEquals("B", BooleanUtils.toString(false, "A", "B"));
    }

    @Test
    public void testXorPrimitive() throws Throwable {
        assertTrue(BooleanUtils.xor(new boolean[] { true }));
        assertTrue(BooleanUtils.xor(new boolean[] { true, false }));
        assertFalse(BooleanUtils.xor(new boolean[] { true, true }));
        assertFalse(BooleanUtils.xor(new boolean[] { false, false }));
        assertTrue(BooleanUtils.xor(new boolean[] { false, true, false }));
        assertFalse(BooleanUtils.xor(new boolean[] { true, true, false }));

        boolean thrown = false;
        try {
            BooleanUtils.xor((boolean[]) null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            BooleanUtils.xor(new boolean[0]);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testXorBooleanObject() throws Throwable {
        assertEquals(Boolean.TRUE, BooleanUtils.xor(new Boolean[] { Boolean.TRUE, Boolean.FALSE }));
        assertEquals(Boolean.FALSE, BooleanUtils.xor(new Boolean[] { Boolean.TRUE, Boolean.TRUE }));
        assertEquals(Boolean.FALSE, BooleanUtils.xor(new Boolean[] { Boolean.FALSE, Boolean.FALSE }));

        boolean thrown = false;
        try {
            BooleanUtils.xor((Boolean[]) null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            BooleanUtils.xor(new Boolean[0]);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            BooleanUtils.xor(new Boolean[] { Boolean.TRUE, null });
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }
}