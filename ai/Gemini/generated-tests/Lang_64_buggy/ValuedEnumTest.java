package org.apache.commons.lang.enums;

import junit.framework.TestCase;

public class ValuedEnumTest extends TestCase {

    private static final class DummyValuedEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final DummyValuedEnum ITEM_1 = new DummyValuedEnum("Item1", 10);
        public static final DummyValuedEnum ITEM_2 = new DummyValuedEnum("Item2", 20);
        public static final DummyValuedEnum ITEM_3 = new DummyValuedEnum("Item3", 10);

        protected DummyValuedEnum(String name, int value) {
            super(name, value);
        }

        public static DummyValuedEnum getEnum(String name) {
            return (DummyValuedEnum) getEnum(DummyValuedEnum.class, name);
        }

        public static DummyValuedEnum getEnum(int value) {
            return (DummyValuedEnum) getEnum(DummyValuedEnum.class, value);
        }
    }

    private static final class OtherValuedEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final OtherValuedEnum OTHER_1 = new OtherValuedEnum("Other1", 15);

        protected OtherValuedEnum(String name, int value) {
            super(name, value);
        }
    }

    public ValuedEnumTest(String name) {
        super(name);
    }

    public void testConstructorAndGetValue() throws Throwable {
        DummyValuedEnum item = DummyValuedEnum.ITEM_1;
        assertEquals("Item1", item.getName());
        assertEquals(10, item.getValue());
    }

    public void testGetEnumByIntValue() throws Throwable {
        Enum result1 = DummyValuedEnum.getEnum(10);
        assertNotNull(result1);
        assertEquals("Item1", result1.getName());

        Enum result2 = DummyValuedEnum.getEnum(20);
        assertNotNull(result2);
        assertEquals("Item2", result2.getName());

        Enum result3 = DummyValuedEnum.getEnum(999);
        assertNull(result3);
    }

    public void testGetEnumByIntClassNull() throws Throwable {
        try {
            ValuedEnum.getEnum(null, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Enum Class") >= 0);
        }
    }

    public void testCompareTo() throws Throwable {
        DummyValuedEnum item1 = DummyValuedEnum.ITEM_1; // value 10
        DummyValuedEnum item2 = DummyValuedEnum.ITEM_2; // value 20
        DummyValuedEnum item3 = DummyValuedEnum.ITEM_3; // value 10

        assertTrue(item1.compareTo(item2) < 0);
        assertTrue(item2.compareTo(item1) > 0);
        assertEquals(0, item1.compareTo(item3));
    }

    public void testCompareToException() throws Throwable {
        DummyValuedEnum item1 = DummyValuedEnum.ITEM_1;
        try {
            item1.compareTo(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }

        try {
            item1.compareTo("NotAnEnum");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    public void testToString() throws Throwable {
        DummyValuedEnum item = DummyValuedEnum.ITEM_1;
        String str = item.toString();
        assertNotNull(str);
        assertTrue(str.indexOf("DummyValuedEnum") >= 0);
        assertTrue(str.indexOf("Item1") >= 0);
        assertTrue(str.indexOf("10") >= 0);
    }
}