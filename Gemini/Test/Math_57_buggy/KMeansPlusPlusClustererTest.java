package org.apache.commons.math.stat.clustering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class KMeansPlusPlusClustererTest {

    private static class DummyPoint implements Clusterable<DummyPoint> {
        private final double[] coords;

        public DummyPoint(double... coords) {
            this.coords = coords;
        }

        public double distanceFrom(DummyPoint p) {
            double sum = 0.0;
            for (int i = 0; i < coords.length; i++) {
                double diff = coords[i] - p.coords[i];
                sum += diff * diff;
            }
            return Math.sqrt(sum);
        }

        public DummyPoint centroidOf(Collection<DummyPoint> points) {
            double[] means = new double[coords.length];
            for (DummyPoint p : points) {
                for (int i = 0; i < p.coords.length; i++) {
                    means[i] += p.coords[i];
                }
            }
            int size = points.size();
            if (size > 0) {
                for (int i = 0; i < means.length; i++) {
                    means[i] /= size;
                }
            }
            return new DummyPoint(means);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DummyPoint)) {
                return false;
            }
            DummyPoint other = (DummyPoint) obj;
            if (other.coords.length != coords.length) {
                return false;
            }
            for (int i = 0; i < coords.length; i++) {
                if (coords[i] != other.coords[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            for (double c : coords) {
                hash ^= Double.valueOf(c).hashCode();
            }
            return hash;
        }
    }

    @Test
    public void testClusterBasic() throws Throwable {
        List<DummyPoint> points = new ArrayList<DummyPoint>();
        points.add(new DummyPoint(0.0, 0.0));
        points.add(new DummyPoint(0.1, 0.1));
        points.add(new DummyPoint(10.0, 10.0));
        points.add(new DummyPoint(10.1, 10.1));

        KMeansPlusPlusClusterer<DummyPoint> clusterer = 
            new KMeansPlusPlusClusterer<DummyPoint>(new Random(42L));

        List<Cluster<DummyPoint>> clusters = clusterer.cluster(points, 2, 10);
        assertNotNull(clusters);
        assertEquals(2, clusters.size());
    }

    @Test
    public void testClusterMaxIterationsNegative() throws Throwable {
        List<DummyPoint> points = new ArrayList<DummyPoint>();
        points.add(new DummyPoint(1.0, 1.0));
        points.add(new DummyPoint(2.0, 2.0));

        KMeansPlusPlusClusterer<DummyPoint> clusterer = 
            new KMeansPlusPlusClusterer<DummyPoint>(new Random(0L), KMeansPlusPlusClusterer.EmptyClusterStrategy.LARGEST_VARIANCE);

        List<Cluster<DummyPoint>> clusters = clusterer.cluster(points, 1, -1);
        assertNotNull(clusters);
        assertEquals(1, clusters.size());
    }

    @Test
    public void testEmptyClusterStrategyLargestVariance() throws Throwable {
        List<DummyPoint> points = new ArrayList<DummyPoint>();
        points.add(new DummyPoint(0.0, 0.0));
        points.add(new DummyPoint(0.0, 0.0));

        KMeansPlusPlusClusterer<DummyPoint> clusterer = 
            new KMeansPlusPlusClusterer<DummyPoint>(new Random(1L), KMeansPlusPlusClusterer.EmptyClusterStrategy.LARGEST_VARIANCE);

        List<Cluster<DummyPoint>> clusters = clusterer.cluster(points, 2, 5);
        assertNotNull(clusters);
    }

    @Test
    public void testEmptyClusterStrategyLargestPointsNumber() throws Throwable {
        List<DummyPoint> points = new ArrayList<DummyPoint>();
        points.add(new DummyPoint(1.0, 1.0));
        points.add(new DummyPoint(1.1, 1.1));
        points.add(new DummyPoint(10.0, 10.0));

        KMeansPlusPlusClusterer<DummyPoint> clusterer = 
            new KMeansPlusPlusClusterer<DummyPoint>(new Random(1L), KMeansPlusPlusClusterer.EmptyClusterStrategy.LARGEST_POINTS_NUMBER);

        List<Cluster<DummyPoint>> clusters = clusterer.cluster(points, 2, 5);
        assertNotNull(clusters);
    }

    @Test
    public void testEmptyClusterStrategyFarthestPoint() throws Throwable {
        List<DummyPoint> points = new ArrayList<DummyPoint>();
        points.add(new DummyPoint(0.0, 0.0));
        points.add(new DummyPoint(100.0, 100.0));

        KMeansPlusPlusClusterer<DummyPoint> clusterer = 
            new KMeansPlusPlusClusterer<DummyPoint>(new Random(1L), KMeansPlusPlusClusterer.EmptyClusterStrategy.FarthestPoint);

        List<Cluster<DummyPoint>> clusters = clusterer.cluster(points, 2, 5);
        assertNotNull(clusters);
    }

    @Test
    public void testEmptyClusterStrategyError() throws Throwable {
        List<DummyPoint> points = new ArrayList<DummyPoint>();
        points.add(new DummyPoint(1.0, 1.0));
        points.add(new DummyPoint(1.0, 1.0));

        KMeansPlusPlusClusterer<DummyPoint> clusterer = 
            new KMeansPlusPlusClusterer<DummyPoint>(new Random(1L), KMeansPlusPlusClusterer.EmptyClusterStrategy.ERROR);

        try {
            clusterer.cluster(points, 2, 5);
            fail("Expected ConvergenceException");
        } catch (org.apache.commons.math.exception.ConvergenceException e) {
            assertNotNull(e);
        }
    }
}