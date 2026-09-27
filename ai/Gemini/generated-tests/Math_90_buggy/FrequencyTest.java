package org.apache.commons.math.stat;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Comparator;
import java.util.Iterator;

public class FrequencyTest {

    @Test
    public void testDefaultConstructorAndEmptyFrequency() throws Throwable {
        Frequency f = new Frequency();
        assertEquals(0L, f.getSumFreq());
        assertTrue(Double.isNaN(f.getPct(1)));
        assertTrue(Double.isNaN(f.getCumPct(1)));
        assertEquals(0L, f.getCumFreq(1));
        assertEquals(0L, f.getCount(1));
        assertNotNull(f.toString());
    }

    @Test
    public void testComparatorConstructor() throws Throwable {
        Comparator<Long> descComparator = new Comparator<Long>() {
            public int compare(Long o1, Long o2) {
                return o2.compareTo(o1);
            }
        };
        Frequency f = new Frequency(descComparator);
        f.addValue(10L);
        f.addValue(20L);
        assertEquals(2L, f.getSumFreq());
        assertEquals(1L, f.getCount(10L));
        assertEquals(1L, f.getCount(20L));
        assertNotNull(f.toString());
    }

    @Test
    public void testAddValueIntegerAndLongAndInt() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1);
        f.addValue(Integer.valueOf(2));
        f.addValue(3L);
        assertEquals(3L, f.getSumFreq());
        assertEquals(1L, f.getCount(1));
        assertEquals(1L, f.getCount(2L));
        assertEquals(1L, f.getCount(Integer.valueOf(3)));
        assertEquals(0.3333333333333333, f.getPct(1), 1e-12);
        assertEquals(1.0, f.getCumPct(3), 1e-12);
    }

    @Test
    public void testAddValueChar() throws Throwable {
        Frequency f = new Frequency();
        f.addValue('a');
        f.addValue('b');
        f.addValue(Character.valueOf('a'));
        assertEquals(3L, f.getSumFreq());
        assertEquals(2L, f.getCount('a'));
        assertEquals(1L, f.getCount(Character.valueOf('b')));
        assertEquals(2L, f.getCumFreq('a'));
        assertEquals(3L, f.getCumFreq('z'));
        assertEquals(0L, f.getCumFreq('`'));
    }

    @Test
    public void testAddValueObject() throws Throwable {
        Frequency f = new Frequency();
        f.addValue((Object) "test");
        f.addValue("test");
        assertEquals(2L, f.getSumFreq());
        assertEquals(2L, f.getCount("test"));
        assertEquals(2L, f.getCumFreq("test"));
        assertEquals(1.0, f.getPct("test"), 1e-12);
        assertEquals(1.0, f.getCumPct("test"), 1e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddValueIncompatibleType() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1L);
        f.addValue("string");
    }

    @Test
    public void testClear() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10);
        assertEquals(1L, f.getSumFreq());
        f.clear();
        assertEquals(0L, f.getSumFreq());
        assertTrue(Double.isNaN(f.getPct(10)));
    }

    @Test
    public void testValuesIterator() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(5);
        f.addValue(10);
        Iterator iter = f.valuesIterator();
        assertNotNull(iter);
        assertTrue(iter.hasNext());
        Object val = iter.next();
        assertNotNull(val);
    }

    @Test
    public void testGetCountObjectInvalidAndNonComparable() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10L);
        assertEquals(0L, f.getCount(Integer.valueOf(5)));
        
        Object nonComparable = new Object();
        assertEquals(0L, f.getCount(nonComparable));
        assertEquals(0L, f.getCumFreq(nonComparable));
    }

    @Test
    public void testGetCumFreqEdgeCases() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10L);
        f.addValue(20L);
        f.addValue(30L);

        // Less than first key
        assertEquals(0L, f.getCumFreq(5L));
        assertEquals(0L, f.getCumFreq(5));
        assertEquals(0L, f.getCumFreq(Character.valueOf('a'))); // Incompatible/less check behavior

        // Greater than or equal to last key
        assertEquals(3L, f.getCumFreq(40L));

        // Intermediate values
        assertEquals(1L, f.getCumFreq(10L));
        assertEquals(2L, f.getCumFreq(15L));
        assertEquals(2L, f.getCumFreq(20L));
    }

    @Test
    public void testGetPctAndCumPctVariants() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(5L);
        f.addValue(5);
        f.addValue(10L);

        assertEquals(0.6666666666666666, f.getPct(5), 1e-12);
        assertEquals(0.6666666666666666, f.getPct(Integer.valueOf(5)), 1e-12);
        assertEquals(0.3333333333333333, f.getPct(10L), 1e-12);
        assertEquals(0.0, f.getPct(99), 1e-12);

        assertEquals(0.6666666666666666, f.getCumPct(5), 1e-12);
        assertEquals(0.6666666666666666, f.getCumPct(Integer.valueOf(5)), 1e-12);
        assertEquals(1.0, f.getCumPct(10L), 1e-12);
        assertEquals(0.0, f.getCumPct(1L), 1e-12);
    }

    @Test
    public void testCharPctAndCumPct() throws Throwable {
        Frequency f = new Frequency();
        f.addValue('x');
        f.addValue('y');
        
        assertEquals(0.5, f.getPct('x'), 1e-12);
        assertEquals(1.0, f.getCumPct('y'), 1e-12);
        assertEquals(0.0, f.getCumPct('a'), 1e-12);
    }
}