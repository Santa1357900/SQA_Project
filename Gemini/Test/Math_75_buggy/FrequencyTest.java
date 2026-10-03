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
        Comparator<Comparable<Integer>> comp = new Comparator<Comparable<Integer>>() {
            public int compare(Comparable<Integer> o1, Comparable<Integer> o2) {
                return o2.compareTo((Integer) o1); // Reverse natural order
            }
        };
        Frequency f = new Frequency(comp);
        f.addValue(1);
        f.addValue(2);
        assertEquals(2, f.getSumFreq());
        assertEquals(1, f.getCount(1));
        assertEquals(1, f.getCount(2));
    }

    @Test
    public void testAddValueIntAndLongAndInteger() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10);
        f.addValue(Integer.valueOf(10));
        f.addValue(10L);
        assertEquals(3, f.getSumFreq());
        assertEquals(3, f.getCount(10));
        assertEquals(3, f.getCount(10L));
        assertEquals(3, f.getCount(Integer.valueOf(10)));
        assertEquals(1.0, f.getPictDoubleHelper(f, 10), 0.0001);
    }

    private double getPictDoubleHelper(Frequency f, int val) {
        return f.getPct(val);
    }

    @Test
    public void testAddValueChar() throws Throwable {
        Frequency f = new Frequency();
        f.addValue('a');
        f.addValue('b');
        f.addValue('a');
        assertEquals(3, f.getSumFreq());
        assertEquals(2, f.getCount('a'));
        assertEquals(1, f.getCount('b'));
        assertEquals(0, f.getCount('c'));
        assertEquals(2.0 / 3.0, f.getPct('a'), 0.0001);
    }

    @Test
    public void testAddValueObjectComparable() throws Throwable {
        Frequency f = new Frequency();
        f.addValue((Object) "test");
        f.addValue((Comparable<String>) "test");
        assertEquals(2, f.getCount("test"));
        assertEquals(2, f.getSumFreq());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddValueObjectNotComparable() throws Throwable {
        Frequency f = new Frequency();
        Object notComparable = new Object();
        f.addValue(notComparable);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddValueClassCastException() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10);
        f.addValue("string"); // Should throw IllegalArgumentException via ClassCastException handling
    }

    @Test
    public void testGetCountObjectAndPrimitiveVariants() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(5);
        assertEquals(1, f.getCount((Object) 5));
        assertEquals(1, f.getCount(5L));
        assertEquals(1, f.getCount(Integer.valueOf(5)));
        assertEquals(1, f.getCount('5')); // '5' is char, different from int 5
        assertEquals(0, f.getCount("notThere"));
    }

    @Test
    public void testGetCountClassCastHandling() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10);
        // Looking up a String when Integer/Long is stored should return 0 safely
        long count = f.getCount((Comparable<?>) "incompatibleKey");
        assertEquals(0, count);
    }

    @Test
    public void testGetPctVariants() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1);
        f.addValue(2);
        f.addValue(2);
        f.addValue(3);
        
        assertEquals(0.25, f.getPct(1), 0.0001);
        assertEquals(0.50, f.getPct(2L), 0.0001);
        assertEquals(0.25, f.getPct(Integer.valueOf(3)), 0.0001);
        assertEquals(0.0, f.getPct(99), 0.0001);
        assertEquals(0.25, f.getPct((Object) 1), 0.0001);
    }

    @Test
    public void testGetCumFreqAndCumPct() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10);
        f.addValue(20);
        f.addValue(20);
        f.addValue(30);

        // SumFreq is 4
        assertEquals(4, f.getSumFreq());

        // Less than firstKey
        assertEquals(0, f.getCumFreq(5));
        assertEquals(0.0, f.getCumPct(5), 0.0001);

        // Equal to firstKey
        assertEquals(1, f.getCumFreq(10));
        assertEquals(0.25, f.getCumPct(10), 0.0001);

        // Between keys
        assertEquals(1, f.getCumFreq(15));
        assertEquals(0.25, f.getCumPct(15), 0.0001);

        // Equal to middle key (with duplicates)
        assertEquals(3, f.getCumFreq(20));
        assertEquals(0.75, f.getCumPct(20), 0.0001);

        // Greater than lastKey
        assertEquals(4, f.getCumFreq(40));
        assertEquals(1.0, f.getCumPct(40), 0.0001);

        // Overloaded methods for char, long, Integer, Object
        assertEquals(3, f.getCumFreq(20L));
        assertEquals(3, f.getCumFreq(Integer.valueOf(20)));
        assertEquals(3, f.getCumFreq((Object) 20));
        assertEquals(0.75, f.getCumPct(20L), 0.0001);
        assertEquals(0.75, f.getCumPct(Integer.valueOf(20)), 0.0001);
        assertEquals(0.75, f.getCumPct((Object) 20), 0.0001);
        
        // Character cum freq
        Frequency charFreq = new Frequency();
        charFreq.addValue('a');
        charFreq.addValue('c');
        assertEquals(1, charFreq.getCumFreq('b'));
        assertEquals(2, charFreq.getCumFreq('d'));
        assertEquals(0, charFreq.getCumFreq('0'));
    }

    @Test
    public void testGetCumFreqIncompatibleType() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(10);
        long res = f.getCumFreq((Comparable<?>) "stringKey");
        assertEquals(0, res);
    }

    @Test
    public void testValuesIteratorAndClear() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1);
        f.addValue(2);
        
        Iterator<Comparable<?>> iter = f.valuesIterator();
        assertTrue(iter.hasNext());
        assertNotNull(iter.next());

        f.clear();
        assertEquals(0, f.getSumFreq());
        assertFalse(f.valuesIterator().hasNext());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Frequency f1 = new Frequency();
        Frequency f2 = new Frequency();
        
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());

        f1.addValue(1);
        assertFalse(f1.equals(f2));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("SomeString"));

        f2.addValue(1);
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testToStringOutput() throws Throwable {
        Frequency f = new Frequency();
        f.addValue(1);
        String str = f.toString();
        assertNotNull(str);
        assertTrue(str.contains("Value"));
        assertTrue(str.contains("Freq"));
        assertTrue(str.contains("1"));
    }
}