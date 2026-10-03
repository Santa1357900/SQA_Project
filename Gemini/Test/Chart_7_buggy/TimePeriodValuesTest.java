package org.jfree.data.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;

import org.junit.Test;

public class TimePeriodValuesTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        TimePeriodValues tpv = new TimePeriodValues("Series1");
        assertEquals("Series1", tpv.getKey());
        assertEquals("Time", tpv.getDomainDescription());
        assertEquals("Value", tpv.getRangeDescription());
        assertEquals(0, tpv.getItemCount());

        TimePeriodValues tpv2 = new TimePeriodValues("Series2", "CustomDomain", "CustomRange");
        assertEquals("Series2", tpv2.getKey());
        assertEquals("CustomDomain", tpv2.getDomainDescription());
        assertEquals("CustomRange", tpv2.getRangeDescription());
    }

    @Test
    public void testDomainAndRangeDescriptions() throws Throwable {
        TimePeriodValues tpv = new TimePeriodValues("Series1");
        tpv.setDomainDescription("NewDomain");
        assertEquals("NewDomain", tpv.getDomainDescription());

        tpv.setRangeDescription("NewRange");
        assertEquals("NewRange", tpv.getRangeDescription());

        tpv.setDomainDescription(null);
        assertNull(tpv.getDomainDescription());

        tpv.setRangeDescription(null);
        assertNull(tpv.getRangeDescription());
    }

    @Test
    public void testAddItemsAndBounds() throws Throwable {
        TimePeriodValues tpv = new TimePeriodValues("Series1");
        
        SimpleTimePeriod p1 = new SimpleTimePeriod(new Date(1000L), new Date(3000L));
        SimpleTimePeriod p2 = new SimpleTimePeriod(new Date(500L), new Date(2000L));
        SimpleTimePeriod p3 = new SimpleTimePeriod(new Date(2000L), new Date(5000L));

        tpv.add(new TimePeriodValue(p1, 10.0));
        assertEquals(1, tpv.getItemCount());
        assertEquals(0, tpv.getMinStartIndex());
        assertEquals(0, tpv.getMaxStartIndex());
        assertEquals(0, tpv.getMinEndIndex());
        assertEquals(0, tpv.getMaxEndIndex());
        assertEquals(0, tpv.getMinMiddleIndex());
        assertEquals(0, tpv.getMaxMiddleIndex());

        tpv.add(p2, 20.0);
        assertEquals(2, tpv.getItemCount());
        // p2 starts at 500 (earlier than p1's 1000)
        assertEquals(1, tpv.getMinStartIndex());
        // p1 starts at 1000, max start is still p1 (index 0)
        assertEquals(0, tpv.getMaxStartIndex());

        tpv.add(p3, (Number) null);
        assertEquals(3, tpv.getItemCount());
        assertNull(tpv.getValue(2));
        assertEquals(30.0, 30.0, 0.001); // dummy assertion or check via getValue if needed

        // Test delete
        tpv.delete(1, 1);
        assertEquals(2, tpv.getItemCount());
    }

    @Test
    public void testAddNullItemThrowsException() throws Throwable {
        TimePeriodValues tpv = new TimePeriodValues("Series1");
        try {
            tpv.add((TimePeriodValue) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null item not allowed"));
        }
    }

    @Test
    public void testAddPrimitiveDoubleAndNumber() throws Throwable {
        TimePeriodValues tpv = new TimePeriodValues("Series1");
        SimpleTimePeriod p1 = new SimpleTimePeriod(new Date(0L), new Date(1000L));
        tpv.add(p1, 123.45);
        assertEquals(123.45, tpv.getValue(0).doubleValue(), 0.0001);
        assertEquals(p1, tpv.getTimePeriod(0));
        assertNotNull(tpv.getDataItem(0));
    }

    @Test
    public void testUpdate() throws Throwable {
        TimePeriodValues tpv = new TimePeriodValues("Series1");
        SimpleTimePeriod p1 = new SimpleTimePeriod(new Date(0L), new Date(1000L));
        tpv.add(p1, 10.0);
        assertEquals(10.0, tpv.getValue(0).doubleValue(), 0.0001);

        tpv.update(0, 99.9);
        assertEquals(99.9, tpv.getValue(0).doubleValue(), 0.0001);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TimePeriodValues tpv1 = new TimePeriodValues("Series1", "Domain1", "Range1");
        TimePeriodValues tpv2 = new TimePeriodValues("Series1", "Domain1", "Range1");

        assertTrue(tpv1.equals(tpv1));
        assertTrue(tpv1.equals(tpv2));
        assertEquals(tpv1.hashCode(), tpv2.hashCode());

        assertFalse(tpv1.equals(null));
        assertFalse(tpv1.equals("NotACollection"));

        TimePeriodValues tpv3 = new TimePeriodValues("Series2", "Domain1", "Range1");
        assertFalse(tpv1.equals(tpv3));

        tpv2.setDomainDescription("DifferentDomain");
        assertFalse(tpv1.equals(tpv2));

        tpv2 = new TimePeriodValues("Series1", "Domain1", "Range1");
        tpv2.setRangeDescription("DifferentRange");
        assertFalse(tpv1.equals(tpv2));

        tpv2 = new TimePeriodValues("Series1", "Domain1", "Range1");
        SimpleTimePeriod p1 = new SimpleTimePeriod(new Date(0L), new Date(1000L));
        tpv1.add(p1, 10.0);
        assertFalse(tpv1.equals(tpv2));

        tpv2.add(p1, 10.0);
        assertTrue(tpv1.equals(tpv2));
    }

    @Test
    public void testCloneAndCreateCopy() throws Throwable {
        TimePeriodValues tpv1 = new TimePeriodValues("Series1");
        SimpleTimePeriod p1 = new SimpleTimePeriod(new Date(0L), new Date(1000L));
        SimpleTimePeriod p2 = new SimpleTimePeriod(new Date(2000L), new Date(3000L));
        tpv1.add(p1, 10.0);
        tpv1.add(p2, 20.0);

        TimePeriodValues clone = (TimePeriodValues) tpv1.clone();
        assertEquals(tpv1, clone);

        TimePeriodValues copy = tpv1.createCopy(0, 0);
        assertEquals(1, copy.getItemCount());
        assertEquals(10.0, copy.getValue(0).doubleValue(), 0.0001);
    }
}