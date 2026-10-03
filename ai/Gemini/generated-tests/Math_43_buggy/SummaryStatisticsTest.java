package org.apache.commons.math.stat.descriptive;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.NullArgumentException;
import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.Variance;
import org.apache.commons.math.stat.descriptive.rank.Max;
import org.apache.commons.math.stat.descriptive.rank.Min;
import org.apache.commons.math.stat.descriptive.summary.Sum;

public class SummaryStatisticsTest {

    @Test
    public void testConstructorAndInitialState() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        assertEquals(0, stats.getN());
        assertTrue(Double.isNaN(stats.getSum()));
        assertTrue(Double.isNaN(stats.getSumsq()));
        assertTrue(Double.isNaN(stats.getMean()));
        assertTrue(Double.isNaN(stats.getStandardDeviation()));
        assertTrue(Double.isNaN(stats.getVariance()));
        assertTrue(Double.isNaN(stats.getPopulationVariance()));
        assertTrue(Double.isNaN(stats.getMax()));
        assertTrue(Double.isNaN(stats.getMin()));
        assertTrue(Double.isNaN(stats.getGeometricMean()));
        assertTrue(Double.isNaN(stats.getSumOfLogs()));
        assertTrue(Double.isNaN(stats.getSecondMoment()));
        assertNotNull(stats.toString());
        assertNotNull(stats.getSummary());
        assertNotNull(stats.getSumImpl());
        assertNotNull(stats.getSumsqImpl());
        assertNotNull(stats.getMinImpl());
        assertNotNull(stats.getMaxImpl());
        assertNotNull(stats.getSumLogImpl());
        assertNotNull(stats.getGeoMeanImpl());
        assertNotNull(stats.getMeanImpl());
        assertNotNull(stats.getVarianceImpl());
    }

    @Test
    public void testCopyConstructor() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.addValue(2.0);

        SummaryStatistics copy = new SummaryStatistics(stats);
        assertEquals(stats.getN(), copy.getN());
        assertEquals(stats.getSum(), copy.getSum(), 1e-14);
        assertEquals(stats.getMean(), copy.getMean(), 1e-14);
        assertEquals(stats, copy);
        assertEquals(stats.hashCode(), copy.hashCode());
    }

    @Test
    public void testAddValueAndBasicStats() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(2.0);
        stats.addValue(4.0);
        stats.addValue(4.0);
        stats.addValue(4.0);
        stats.addValue(5.0);
        stats.addValue(5.0);
        stats.addValue(7.0);
        stats.addValue(9.0);

        assertEquals(8, stats.getN());
        assertEquals(40.0, stats.getSum(), 1e-14);
        assertEquals(228.0, stats.getSumsq(), 1e-14);
        assertEquals(5.0, stats.getMean(), 1e-14);
        assertEquals(2.0, stats.getMin(), 1e-14);
        assertEquals(9.0, stats.getMax(), 1e-14);
        assertEquals(4.0, stats.getStandardDeviation(), 1e-14);
        assertEquals(16.0, stats.getVariance(), 1e-14);
        assertEquals(14.0, stats.getPopulationVariance(), 1e-14);
        assertEquals(4.296339178736341, stats.getGeometricMean(), 1e-10);
        assertEquals(14.582845686034177, stats.getSumOfLogs(), 1e-10);
        assertEquals(32.0, stats.getSecondMoment(), 1e-14);
        assertNotNull(stats.toString());
    }

    @Test
    public void testSingleValueEdgeCase() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(5.5);

        assertEquals(1, stats.getN());
        assertEquals(5.5, stats.getSum(), 1e-14);
        assertEquals(5.5, stats.getMean(), 1e-14);
        assertEquals(5.5, stats.getMin(), 1e-14);
        assertEquals(5.5, stats.getMax(), 1e-14);
        assertEquals(0.0, stats.getStandardDeviation(), 1e-14);
        assertEquals(0.0, stats.getVariance(), 1e-14);
        assertEquals(0.0, stats.getPopulationVariance(), 1e-14);
        assertEquals(0.0, stats.getSecondMoment(), 1e-14);
    }

    @Test
    public void testClear() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.addValue(2.0);
        stats.clear();

        assertEquals(0, stats.getN());
        assertTrue(Double.isNaN(stats.getSum()));
        assertTrue(Double.isNaN(stats.getMean()));
    }

    @Test
    public void testSettersAndCustomImpls() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.setSumImpl(new Sum());
        stats.setSumsqImpl(new org.apache.commons.math.stat.descriptive.summary.SumOfSquares());
        stats.setMinImpl(new Min());
        stats.setMaxImpl(new Max());
        stats.setSumLogImpl(new org.apache.commons.math.stat.descriptive.summary.SumOfLogs());
        stats.setGeoMeanImpl(new org.apache.commons.math.stat.descriptive.moment.GeometricMean());
        stats.setMeanImpl(new Mean());
        stats.setVarianceImpl(new Variance());

        assertNotNull(stats.getSumImpl());
        assertNotNull(stats.getSumsqImpl());
        assertNotNull(stats.getMinImpl());
        assertNotNull(stats.getMaxImpl());
        assertNotNull(stats.getSumLogImpl());
        assertNotNull(stats.getGeoMeanImpl());
        assertNotNull(stats.getMeanImpl());
        assertNotNull(stats.getVarianceImpl());

        stats.addValue(10.0);
        assertEquals(10.0, stats.getSum(), 1e-14);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetSumImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setSumImpl(new Sum());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetSumsqImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setSumsqImpl(new org.apache.commons.math.stat.descriptive.summary.SumOfSquares());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetMinImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setMinImpl(new Min());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetMaxImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setMaxImpl(new Max());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetSumLogImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setSumLogImpl(new org.apache.commons.math.stat.descriptive.summary.SumOfLogs());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetGeoMeanImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setGeoMeanImpl(new org.apache.commons.math.stat.descriptive.moment.GeometricMean());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetMeanImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setMeanImpl(new Mean());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetVarianceImplAfterAddValueThrowsException() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.addValue(1.0);
        stats.setVarianceImpl(new Variance());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        SummaryStatistics stats1 = new SummaryStatistics();
        SummaryStatistics stats2 = new SummaryStatistics();

        assertTrue(stats1.equals(stats1));
        assertTrue(stats1.equals(stats2));
        assertEquals(stats1.hashCode(), stats2.hashCode());

        assertFalse(stats1.equals(null));
        assertFalse(stats1.equals(new Object()));

        stats1.addValue(1.0);
        assertFalse(stats1.equals(stats2));

        stats2.addValue(1.0);
        assertTrue(stats1.equals(stats2));
        assertEquals(stats1.hashCode(), stats2.hashCode());
    }

    @Test
    public void testCopyStaticMethod() throws Throwable {
        SummaryStatistics source = new SummaryStatistics();
        source.addValue(5.0);
        source.addValue(10.0);

        SummaryStatistics dest = new SummaryStatistics();
        SummaryStatistics.copy(source, dest);

        assertEquals(source.getN(), dest.getN());
        assertEquals(source.getSum(), dest.getSum(), 1e-14);
        assertEquals(source.getMean(), dest.getMean(), 1e-14);
    }

    @Test(expected = NullArgumentException.class)
    public void testCopyNullSourceThrowsException() throws Throwable {
        SummaryStatistics dest = new SummaryStatistics();
        SummaryStatistics.copy(null, dest);
    }

    @Test(expected = NullArgumentException.class)
    public void testCopyNullDestThrowsException() throws Throwable {
        SummaryStatistics source = new SummaryStatistics();
        SummaryStatistics.copy(source, null);
    }

    @Test
    public void testCopyWithCustomImpls() throws Throwable {
        SummaryStatistics source = new SummaryStatistics();
        source.setMeanImpl(new Mean());
        source.setVarianceImpl(new Variance());
        source.setGeoMeanImpl(new org.apache.commons.math.stat.descriptive.moment.GeometricMean());
        source.addValue(3.0);

        SummaryStatistics dest = source.copy();
        assertEquals(source.getMean(), dest.getMean(), 1e-14);
        assertEquals(source.getVariance(), dest.getVariance(), 1e-14);
    }

    @Test
    public void testClearWithCustomMeanAndVariance() throws Throwable {
        SummaryStatistics stats = new SummaryStatistics();
        stats.setMeanImpl(new Mean());
        stats.setVarianceImpl(new Variance());
        stats.addValue(2.0);
        stats.clear();
        assertEquals(0, stats.getN());
    }
}