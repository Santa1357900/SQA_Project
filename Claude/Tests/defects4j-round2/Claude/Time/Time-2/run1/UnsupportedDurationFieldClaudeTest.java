package org.joda.time.field;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;

import org.joda.time.DurationField;
import org.joda.time.DurationFieldType;

public class UnsupportedDurationFieldClaudeTest {

    // getInstance: cache branch, calling twice with same type must return the same cached object
    @Test
    public void testGetInstance_sameType_returnsSameCachedInstance() throws Throwable {
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(DurationFieldType.halfdays());
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(DurationFieldType.halfdays());
        assertSame(field1, field2);
    }

    // getInstance: different types must produce different instances
    @Test
    public void testGetInstance_differentTypes_returnsDifferentInstances() throws Throwable {
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(DurationFieldType.weekyears());
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(DurationFieldType.eras());
        assertNotSame(field1, field2);
    }

    // getInstance + getName with millis type
    @Test
    public void testGetInstance_withMillisType_nameMatches() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        assertEquals("millis", field.getName());
    }

    // getInstance + getName with years type
    @Test
    public void testGetInstance_withYearsType_nameMatches() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.years());
        assertEquals("years", field.getName());
    }

    // getType must return exactly the type passed to getInstance
    @Test
    public void testGetType_returnsTypePassedToGetInstance() throws Throwable {
        DurationFieldType type = DurationFieldType.days();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);
        assertSame(type, field.getType());
    }

    // getName delegates to type.getName()
    @Test
    public void testGetName_matchesTypeGetName() throws Throwable {
        DurationFieldType type = DurationFieldType.hours();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);
        assertEquals(type.getName(), field.getName());
    }

    // isSupported always returns false per javadoc
    @Test
    public void testIsSupported_alwaysReturnsFalse() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
        assertFalse(field.isSupported());
    }

    // isPrecise always returns true per javadoc
    @Test
    public void testIsPrecise_alwaysReturnsTrue() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
        assertTrue(field.isPrecise());
    }

    // getValue(long) must always throw UnsupportedOperationException
    @Test
    public void testGetValue_long_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getValue(100L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getValue(long) exception message should reference "unsupported"
    @Test
    public void testGetValue_long_exceptionMessageContainsUnsupported() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.minutes());
        try {
            field.getValue(1L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("unsupported"));
        }
    }

    // getValueAsLong(long) must always throw
    @Test
    public void testGetValueAsLong_long_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getValueAsLong(100L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getValue(long, long) must always throw
    @Test
    public void testGetValue_longLong_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getValue(100L, 0L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getValueAsLong(long, long) must always throw
    @Test
    public void testGetValueAsLong_longLong_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getValueAsLong(100L, 0L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getMillis(int) must always throw
    @Test
    public void testGetMillis_int_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getMillis(5);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getMillis(long) must always throw
    @Test
    public void testGetMillis_long_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getMillis(5L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getMillis(int, long) must always throw
    @Test
    public void testGetMillis_intLong_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getMillis(5, 100L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getMillis(long, long) must always throw
    @Test
    public void testGetMillis_longLong_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getMillis(5L, 100L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // add(long, int) must always throw
    @Test
    public void testAdd_longInt_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.add(100L, 5);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // add(long, long) must always throw
    @Test
    public void testAdd_longLong_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.add(100L, 5L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getDifference(long, long) must always throw
    @Test
    public void testGetDifference_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getDifference(100L, 50L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getDifferenceAsLong(long, long) must always throw
    @Test
    public void testGetDifferenceAsLong_throwsUnsupportedOperationException() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        try {
            field.getDifferenceAsLong(100L, 50L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // getUnitMillis always returns zero per javadoc
    @Test
    public void testGetUnitMillis_returnsZero() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        assertEquals(0L, field.getUnitMillis());
    }

    // compareTo always returns zero regardless of the other field
    @Test
    public void testCompareTo_returnsZeroRegardlessOfArgument() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        DurationField other = UnsupportedDurationField.getInstance(DurationFieldType.seconds());
        assertEquals(0, field.compareTo(other));
    }

    // compareTo returns zero even when argument is null (contract: "zero always")
    @Test
    public void testCompareTo_withNullArgument_returnsZero() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.millis());
        assertEquals(0, field.compareTo(null));
    }

    // equals: identity branch (this == obj)
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.weeks());
        assertTrue(field.equals(field));
    }

    // equals: instanceof branch, different name -> false
    @Test
    public void testEquals_differentTypeInstance_returnsFalse() throws Throwable {
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(DurationFieldType.weeks());
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(DurationFieldType.months());
        assertFalse(field1.equals(field2));
    }

    // equals: obj is null -> not instanceof -> false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.weeks());
        assertFalse(field.equals(null));
    }

    // equals: obj is a different class entirely -> false
    @Test
    public void testEquals_differentClassType_returnsFalse() throws Throwable {
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(DurationFieldType.weeks());
        assertFalse(field.equals("weeks"));
    }

    // hashCode must equal getName().hashCode()
    @Test
    public void testHashCode_matchesNameHashCode() throws Throwable {
        DurationFieldType type = DurationFieldType.years();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);
        assertEquals(type.getName().hashCode(), field.hashCode());
    }

    // equals/hashCode contract: equal objects must have equal hashcodes
    @Test
    public void testHashCode_equalForEqualObjects() throws Throwable {
        UnsupportedDurationField field1 = UnsupportedDurationField.getInstance(DurationFieldType.centuries());
        UnsupportedDurationField field2 = UnsupportedDurationField.getInstance(DurationFieldType.centuries());
        assertTrue(field1.equals(field2));
        assertEquals(field1.hashCode(), field2.hashCode());
    }

    // toString debug format: "UnsupportedDurationField[" + name + ']'
    @Test
    public void testToString_formatContainsTypeName() throws Throwable {
        DurationFieldType type = DurationFieldType.eras();
        UnsupportedDurationField field = UnsupportedDurationField.getInstance(type);
        String result = field.toString();
        assertTrue(result.indexOf(type.getName()) >= 0);
        assertTrue(result.startsWith("UnsupportedDurationField["));
        assertTrue(result.endsWith("]"));
    }

    // readResolve: deserialized instance must resolve to the singleton from getInstance
    @Test
    public void testSerialization_readResolve_returnsSingletonInstance() throws Throwable {
        UnsupportedDurationField original = UnsupportedDurationField.getInstance(DurationFieldType.minutes());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object resolved = ois.readObject();
        ois.close();

        assertSame(UnsupportedDurationField.getInstance(DurationFieldType.minutes()), resolved);
    }
}
