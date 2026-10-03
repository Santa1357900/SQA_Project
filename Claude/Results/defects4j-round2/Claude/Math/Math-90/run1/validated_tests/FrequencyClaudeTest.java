package org.apache.commons.math.stat;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import java.util.Comparator;
import java.util.Iterator;

public class FrequencyClaudeTest {

    private Frequency freq;

    @Before
    public void setUp() throws Throwable {
        freq = new Frequency();
    }

    // ctor: default constructor produces empty table -> sumFreq 0
    @Test
    public void testDefaultConstructor_newInstance_sumFreqZero() throws Throwable {
        assertEquals(0L, freq.getSumFreq());
    }

    // ctor: custom comparator controls iteration order (descending)
    @Test
    public void testComparatorConstructor_customComparator_ordersDescending() throws Throwable {
        Comparator desc = new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Long) o2).compareTo((Long) o1);
            }
        };
        Frequency f = new Frequency(desc);
        f.addValue(1);
        f.addValue(2);
        f.addValue(3);
        Iterator it = f.valuesIterator();
        assertEquals(Long.valueOf(3), it.next());
    }

    // addValue(int): single add -> count 1
    @Test
    public void testAddValueInt_singleValue_countOne() throws Throwable {
        freq.addValue(5);
        assertEquals(1L, freq.getCount(5));
    }

    // addValue(int): multiple adds accumulate count
    @Test
    public void testAddValueInt_multipleCalls_countIncrements() throws Throwable {
        freq.addValue(5);
        freq.addValue(5);
        freq.addValue(5);
        assertEquals(3L, freq.getCount(5));
    }

    // addValue(long): stored and retrievable via long overload
    @Test
    public void testAddValueLong_singleValue_countOne() throws Throwable {
        freq.addValue(100000000000L);
        assertEquals(1L, freq.getCount(100000000000L));
    }

    // addValue(char): stored and retrievable via char overload
    @Test
    public void testAddValueChar_singleValue_countOne() throws Throwable {
        freq.addValue('a');
        assertEquals(1L, freq.getCount('a'));
    }

    // addValue(Integer): wrapper combined with primitive int into same bucket
    @Test
    public void testAddValueIntegerWrapper_sameAsInt_countsCombined() throws Throwable {
        freq.addValue(Integer.valueOf(7));
        freq.addValue(7);
        assertEquals(2L, freq.getCount(7));
    }

    // addValue(Object) deprecated path: a comparable Object is stored
    @Test
    public void testAddValueObject_withString_countOne() throws Throwable {
        freq.addValue((Object) "hello");
        assertEquals(1L, freq.getCount((Object) "hello"));
    }

    // addValue(Object): incomparable type vs existing entries throws IllegalArgumentException
    @Test
    public void testAddValueObject_incomparableType_throwsIllegalArgumentException() throws Throwable {
        freq.addValue(1);
        try {
            freq.addValue((Object) "a string");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // clear(): resets the table so sumFreq returns to 0
    @Test
    public void testClear_afterAddingValues_sumFreqZero() throws Throwable {
        freq.addValue(1);
        freq.addValue(2);
        freq.clear();
        assertEquals(0L, freq.getSumFreq());
    }

    // valuesIterator(): empty table -> no elements
    @Test
    public void testValuesIterator_emptyTable_hasNextFalse() throws Throwable {
        assertFalse(freq.valuesIterator().hasNext());
    }

    // valuesIterator(): integral values are stored/returned as Long
    @Test
    public void testValuesIterator_integersConvertedToLong_returnsLongType() throws Throwable {
        freq.addValue(5);
        Object val = freq.valuesIterator().next();
        assertTrue(val instanceof Long);
        assertEquals(Long.valueOf(5), val);
    }

    // getSumFreq(): empty table returns 0
    @Test
    public void testGetSumFreq_emptyTable_returnsZero() throws Throwable {
        assertEquals(0L, freq.getSumFreq());
    }

    // getSumFreq(): sums all counts across distinct values
    @Test
    public void testGetSumFreq_multipleValues_returnsTotalCount() throws Throwable {
        freq.addValue(1);
        freq.addValue(1);
        freq.addValue(2);
        assertEquals(3L, freq.getSumFreq());
    }

    // getCount(Object): value present returns its accumulated count
    @Test
    public void testGetCountObject_valuePresent_returnsCorrectCount() throws Throwable {
        freq.addValue(5);
        freq.addValue(5);
        assertEquals(2L, freq.getCount((Object) Long.valueOf(5)));
    }

    // getCount(Object): value absent (but comparable) returns 0
    @Test
    public void testGetCountObject_valueAbsent_returnsZero() throws Throwable {
        freq.addValue(5);
        assertEquals(0L, freq.getCount((Object) Long.valueOf(99)));
    }

    // getCount(Object): incomparable type returns 0 per javadoc
    @Test
    public void testGetCountObject_incomparableType_returnsZero() throws Throwable {
        freq.addValue(5);
        assertEquals(0L, freq.getCount((Object) "not comparable"));
    }

    // getCount(int): int, long and Integer additions are all unified
    @Test
    public void testGetCountInt_matchesLongAndIntegerAdds_sameCount() throws Throwable {
        freq.addValue(3);
        freq.addValue(3L);
        freq.addValue(Integer.valueOf(3));
        assertEquals(3L, freq.getCount(3));
    }

    // getCount(char): char overload matches previously added chars
    @Test
    public void testGetCountChar_matchesAddedChar_returnsCorrectCount() throws Throwable {
        freq.addValue('x');
        freq.addValue('x');
        assertEquals(2L, freq.getCount('x'));
    }

    // getPct(Object): empty table returns NaN per javadoc
    @Test
    public void testGetPctObject_emptyTable_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(freq.getPct((Object) Long.valueOf(1))));
    }

    // getPct(Object): value absent returns 0 proportion
    @Test
    public void testGetPctObject_valueAbsent_returnsZero() throws Throwable {
        freq.addValue(1);
        assertEquals(0.0, freq.getPct((Object) Long.valueOf(99)), 1e-9);
    }

    // getPct(Object): correct proportion for a present value
    @Test
    public void testGetPctObject_valuePresent_returnsCorrectProportion() throws Throwable {
        freq.addValue(1);
        freq.addValue(1);
        freq.addValue(2);
        assertEquals(2.0 / 3.0, freq.getPct((Object) Long.valueOf(1)), 1e-9);
    }

    // getPct(int): proportion computed over total sum
    @Test
    public void testGetPctInt_correctProportion() throws Throwable {
        freq.addValue(1);
        freq.addValue(2);
        freq.addValue(2);
        freq.addValue(2);
        assertEquals(0.75, freq.getPct(2), 1e-9);
    }

    // getPct(char): proportion computed over total sum for char values
    @Test
    public void testGetPctChar_correctProportion() throws Throwable {
        freq.addValue('a');
        freq.addValue('a');
        freq.addValue('b');
        assertEquals(2.0 / 3.0, freq.getPct('a'), 1e-9);
    }

    // getCumFreq(Object): empty table returns 0
    @Test
    public void testGetCumFreqObject_emptyTable_returnsZero() throws Throwable {
        assertEquals(0L, freq.getCumFreq((Object) Long.valueOf(1)));
    }

    // getCumFreq(Object): value strictly less than the smallest key returns 0
    @Test
    public void testGetCumFreqObject_lessThanFirst_returnsZero() throws Throwable {
        freq.addValue(5);
        freq.addValue(10);
        assertEquals(0L, freq.getCumFreq((Object) Long.valueOf(1)));
    }

    // getCumFreq(Object): value equal to first key returns that key's own count
    @Test
    public void testGetCumFreqObject_equalsFirst_returnsFirstCount() throws Throwable {
        freq.addValue(5);
        freq.addValue(5);
        freq.addValue(10);
        assertEquals(2L, freq.getCumFreq((Object) Long.valueOf(5)));
    }

    // getCumFreq(Object): value equal to last key returns the full sum
    @Test
    public void testGetCumFreqObject_equalsLast_returnsSumFreq() throws Throwable {
        freq.addValue(5);
        freq.addValue(10);
        freq.addValue(10);
        freq.addValue(10);
        assertEquals(4L, freq.getCumFreq((Object) Long.valueOf(10)));
    }

    // getCumFreq(Object): value strictly greater than last key returns full sum
    @Test
    public void testGetCumFreqObject_greaterThanLast_returnsSumFreq() throws Throwable {
        freq.addValue(1);
        freq.addValue(2);
        assertEquals(2L, freq.getCumFreq((Object) Long.valueOf(99)));
    }

    // getCumFreq(Object): value not present but between existing keys sums only smaller ones
    @Test
    public void testGetCumFreqObject_valueNotPresentBetweenTwo_returnsPartialSum() throws Throwable {
        freq.addValue(1);
        freq.addValue(1);
        freq.addValue(5);
        freq.addValue(5);
        freq.addValue(5);
        assertEquals(2L, freq.getCumFreq((Object) Long.valueOf(3)));
    }

    // getCumFreq(Object): incomparable type returns 0 per javadoc
    @Test
    public void testGetCumFreqObject_incomparableType_returnsZero() throws Throwable {
        freq.addValue(1);
        assertEquals(0L, freq.getCumFreq((Object) "incomparable"));
    }

    // getCumFreq(int): boundary at a middle existing value
    @Test
    public void testGetCumFreqInt_middleBoundary() throws Throwable {
        freq.addValue(1);
        freq.addValue(2);
        freq.addValue(3);
        assertEquals(2L, freq.getCumFreq(2));
    }

    // getCumFreq(long): boundary at last existing value
    @Test
    public void testGetCumFreqLong_lastBoundary() throws Throwable {
        freq.addValue(100L);
        freq.addValue(200L);
        assertEquals(2L, freq.getCumFreq(200L));
    }

    // getCumFreq(char): boundary at a middle char value
    @Test
    public void testGetCumFreqChar_middleBoundary() throws Throwable {
        freq.addValue('a');
        freq.addValue('b');
        freq.addValue('c');
        assertEquals(2L, freq.getCumFreq('b'));
    }

    // getCumPct(Object): empty table returns NaN per javadoc
    @Test
    public void testGetCumPctObject_emptyTable_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(freq.getCumPct((Object) Long.valueOf(1))));
    }

    // getCumPct(Object): correct cumulative proportion for a present value
    @Test
    public void testGetCumPctObject_valuePresent_returnsCorrectProportion() throws Throwable {
        freq.addValue(1);
        freq.addValue(2);
        freq.addValue(2);
        freq.addValue(3);
        assertEquals(0.75, freq.getCumPct((Object) Long.valueOf(2)), 1e-9);
    }

    // getCumPct(Object): incomparable type returns 0 per javadoc
    @Test
    public void testGetCumPctObject_incomparableType_returnsZero() throws Throwable {
        freq.addValue(1);
        assertEquals(0.0, freq.getCumPct((Object) "x"), 1e-9);
    }

    // getCumPct(int): value equal to last key yields cumulative proportion of 1.0
    @Test
    public void testGetCumPctInt_equalsLast_returnsOne() throws Throwable {
        freq.addValue(1);
        freq.addValue(2);
        freq.addValue(2);
        assertEquals(1.0, freq.getCumPct(2), 1e-9);
    }

    // getCumPct(char): correct cumulative proportion for char values
    @Test
    public void testGetCumPctChar_correctProportion() throws Throwable {
        freq.addValue('a');
        freq.addValue('b');
        assertEquals(0.5, freq.getCumPct('a'), 1e-9);
    }

    // toString(): produces a non-empty report containing the expected header
    @Test
    public void testToString_containsHeaderAndData() throws Throwable {
        freq.addValue(1);
        String s = freq.toString();
        assertTrue(s.contains("Value"));
        assertTrue(s.contains("Freq"));
    }
}
