package org.joda.time.field;

import junit.framework.TestCase;
import org.joda.time.DurationField;
import org.joda.time.DurationFieldType;

public class UnsupportedDurationFieldTest extends TestCase {

    public UnsupportedDurationFieldTest(String name) {
        super(name);
    }

    protected void setUp() throws Throwable {
        super.setUp();
    }

    protected void tearDown() throws Throwable {
        super.tearDown();
    }

    public void testGetInstanceCaching() throws Throwable {
        DurationFieldType type = DurationFieldType.days();
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(type);
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(type);

        assertNotNull(field1);
        assertSame(field1, field2);
        assertEquals(type, field1.getType());
        assertEquals("days", field1.getName());
    }

    public void testAccessorsAndConstants() throws Throwable {
        DurationFieldType type = DurationFieldType.hours();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);

        assertFalse(field.isSupported());
        assertTrue(field.isPrecise());
        assertEquals(0L, field.getUnitMillis());
    }

    public void testUnsupportedOperations() throws Throwable {
        DurationFieldType type = DurationFieldType.minutes();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);

        try {
            field.getValue(100L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getValueAsLong(100L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getValue(100L, 200L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getValueAsLong(100L, 200L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getMillis(5);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getMillis(5L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getMillis(5, 200L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getMillis(5L, 200L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.add(100L, 5);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.add(100L, 5L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getDifference(200L, 100L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }

        try {
            field.getDifferenceAsLong(200L, 100L);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("field is unsupported") >= 0);
        }
    }

    public void testCompareTo() throws Throwable {
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(DurationFieldType.days());
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(DurationFieldType.months());

        assertEquals(0, field1.compareTo(field2));
        assertEquals(0, field1.compareTo(null));
    }

    public void testEqualsAndHashCode() throws Throwable {
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(DurationFieldType.days());
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(DurationFieldType.days());
        UnsupportedDurationField field3 = UnsupportedDurationField.getInstance(DurationFieldType.months());

        assertTrue(field1.equals(field1));
        assertTrue(field1.equals(field2));
        assertFalse(field1.equals(field3));
        assertFalse(field1.equals(null));
        assertFalse(field1.equals("SomeString"));

        assertEquals(field1.hashCode(), field2.hashCode());
    }

    public void testToString() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.days());
        String str = field.toString();
        assertNotNull(str);
        assertTrue(str.indexOf("UnsupportedDurationField") >= 0);
        assertTrue(str.indexOf("days") >= 0);
    }
}