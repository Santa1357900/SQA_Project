package org.apache.commons.math.stat;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Comparator;
import java.util.Iterator;

public class FrequencyTest {

    @Test
    public void testDefaultConstructorAndEmptyFrequency() throws Throwable {
        Frequency f = new Frequency();
        assertEquals(0, f.getSumFreq());
        assertTrue(Double.isNaN(f.getPct(1)));
        assertTrue(Double.isNaN(f.getCumPct(1)));
        assertEquals(0, f.getCumFreq(1));
        assertEquals(0, f.getCount(1));
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
        f.addValue(1L);
        f.addValue(2L);
        assertEquals(2, f.getSumFreq());
        assertEquals(1, f.getCount(1L));
        assertEquals(1, f.getCount(2L));
    }

    @Test
    public void testAddValueVariantsAndCounts() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10);
        f.addValue(Integer.valueOf(20));
        f.addValue(30L);
        f.addValue(40L);
        f.addValue('a');
        f.addValue("test");
        f.addValue((Object) Long.valueOf(50L));

        assertEquals(7, f.getSumFreq());
        assertEquals(1, f.getCount(10));
        assertEquals(1, f.getCount(Integer.valueOf(20)));
        assertEquals(1, f.getCount(30L));
        assertEquals(1, f.getCount(40L));
        assertEquals(1, f.getCount('a'));
        assertEquals(1, f.getCount(Character.valueOf('a')));
        assertEquals(1, f.getCount("test"));
        assertEquals(1, f.getCount((Object) Long.valueOf(50L)));
        assertEquals(0, f.getCount(999));
        assertEquals(0, f.getCount("nonexistent"));
    }

    @Test
    public void testPercentagesAndCumulative() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1L);
        f.addValue(2L);
        f.addValue(2L);
        f.addValue(3L);

        assertEquals(4, f.getSumFreq());

        assertEquals(0.25, f.getPct(1L), 0.0001);
        assertEquals(0.25, f.getPct(1), 0.0001);
        assertEquals(0.50, f.getPct(2L), 0.0001);
        assertEquals(0.25, f.getPct(3L), 0.0001);
        assertEquals(0.00, f.getPct(99L), 0.0001);

        assertEquals(1, f.getCumFreq(1L));
        assertEquals(3, f.getCumFreq(2L));
        assertEquals(4, f.getCumFreq(3L));
        assertEquals(0, f.getCumFreq(0L));
        assertEquals(4, f.getCumFreq(99L));

        assertEquals(0.25, f.getCumPct(1L), 0.0001);
        assertEquals(0.75, f.getCumPct(2L), 0.0001);
        assertEquals(1.00, f.getCumPct(3L), 0.0001);
        assertEquals(0.00, f.getCumPct(0L), 0.0001);
        assertEquals(1.00, f.getCumPct(99L), 0.0001);
    }

    @Test
    public void testCharacterFrequencies() throws Throwable {
        Frequency f = new Frequency();
        f.addValue('x');
        f.addValue('y');
        f.addValue('y');

        assertEquals(3, f.getSumFreq());
        assertEquals(1, f.getCount('x'));
        assertEquals(2, f.getCount('y'));
        assertEquals(1, f.getCumFreq('x'));
        assertEquals(3, f.getCumFreq('y'));
        assertEquals(0.3333, f.getPct('x'), 0.001);
        assertEquals(0.6666, f.getPct('y'), 0.001);
        assertEquals(0.3333, f.getCumPct('x'), 0.001);
        assertEquals(1.0000, f.getCumPct('y'), 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIncompatibleTypesThrowExceptionStringAndLong() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1L);
        f.addValue("string");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIncompatibleTypesThrowExceptionLongAndChar() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1L);
        f.addValue('a');
    }

    @Test
    public void testGetCountAndCumFreqWithIncompatibleType() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1L);
        
        assertEquals(0, f.getCount("incompatible"));
        assertEquals(0, f.getCumFreq("incompatible"));
        assertEquals(0.0, f.getCumPct("incompatible"), 0.0001);
    }

    @Test
    public void testClear() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1L);
        f.addValue(2L);
        assertEquals(2, f.getSumFreq());

        f.clear();
        assertEquals(0, f.getSumFreq());
        assertTrue(Double.isNaN(f.getPct(1L)));
    }

    @Test
    public void testValuesIterator() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10L);
        f.addValue(20L);

        Iterator iter = f.valuesIterator();
        assertNotNull(iter);
        assertTrue(iter.hasNext());
        assertEquals(Long.valueOf(10L), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(Long.valueOf(20L), iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testToStringOutput() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1L);
        String str = f.toString();
        assertNotNull(str);
        assertTrue(str.contains("Value"));
        assertTrue(str.contains("Freq."));
        assertTrue(str.contains("Pct."));
        assertTrue(str.contains("Cum Pct."));
        assertTrue(str.contains("1"));
    }

    @Test
    public void testCumFreqEdgeCasesWithCustomComparator() throws Throwable {
        Comparator<Long> customComp = new Comparator<Long>() {
            public int compare(Long o1, Long o2) {
                return o1.compareTo(o2);
            }
        };
        Frequency f = new Frequency(customComp);
        f.addValue(5L);
        f.addValue(10L);
        f.addValue(15L);

        // v < firstKey
        assertEquals(0, f.getCumFreq(2L));
        // v >= lastKey
        assertEquals(3, f.getCumFreq(20L));
        // middle value
        assertEquals(2, f.getCumFreq(10L));
    }
}