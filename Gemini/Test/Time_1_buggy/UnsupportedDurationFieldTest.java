package org.joda.time.field;

import junit.framework.TestCase;
import org.joda.time.DurationField;
import org.joda.time.DurationFieldType;

public class UnsupportedDurationFieldTest extends TestCase {

    public UnsupportedDurationFieldTest(String name) {
        super(name);
    }

    public void testGetInstanceCaching() throws Throwable {
        DurationFieldType type = DurationFieldType.days();
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(type);
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(type);
        assertNotNull(field1);
        assertSame(field1, field2);
    }

    public void testSimpleAccessors() throws Throwable {
        DurationFieldType type = DurationFieldType.hours();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);

        assertEquals(type, field.getType());
        assertEquals(type.getName(), field.getName());
        assertFalse(field.isSupported());
        assertTrue(field.isPrecise());
        assertEquals(0L, field.getUnitMillis());
    }

    public void testUnsupportedOperations() throws Throwable {
        DurationFieldType type = DurationFieldType.seconds();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getValue(100L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getValueAsLong(100L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getValue(100L, 200L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getValueAsLong(100L, 200L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getMillis(10);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getMillis(10L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getMillis(10, 200L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getMillis(10L, 200L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.add(200L, 10);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.add(200L, 10L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getDifference(300L, 200L);
            }
        });

        assertUnsupported(field, new Runnable() {
            public void run() {
                UnsupportedDurationField f = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
                f.getDifferenceAsLong(300L, 200L);
            }
        });
    }

    private void assertUnsupported(UnsupportedDurationField field, Runnable r) {
        try {
            r.run();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().indexOf("unsupported") >= 0);
        }
    }

    public void testCompareTo() throws Throwable {
        UnsupportedDurationField unsupportedField = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
        DurationField supportedField = UnsupportedDurationField.getInstance(DurationFieldType.days()).getType().getField(org.joda.time.ISOChronology.getInstanceUTC());
        
        assertEquals(0, unsupportedField.compareTo(unsupportedField));
        assertEquals(1, unsupportedField.compareTo(supportedField));
    }

    public void testEqualsAndHashCode() throws Throwable {
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(DurationFieldType.minutes());
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(DurationFieldType.minutes());
        UnsupportedDurationField field3 = UnsupportedDurationField.getInstance(DurationFieldType.hours());

        assertTrue(field1.equals(field1));
        assertTrue(field1.equals(field2));
        assertFalse(field1.equals(field3));
        assertFalse(field1.equals(null));
        assertFalse(field1.equals("SomeString"));

        assertEquals(field1.hashCode(), field2.hashCode());
    }

    public void testToString() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.halfdays());
        String str = field.toString();
        assertNotNull(str);
        assertTrue(str.indexOf("UnsupportedDurationField") >= 0);
        assertTrue(str.indexOf("halfdays") >= 0);
    }
}