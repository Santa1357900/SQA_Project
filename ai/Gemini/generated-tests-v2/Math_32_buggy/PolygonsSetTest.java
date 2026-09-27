package org.apache.commons.math3.geometry.euclidean.twod;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.geometry.partitioning.BSPTree;
import org.apache.commons.math3.geometry.partitioning.SubHyperplane;
import org.apache.commons.math3.geometry.euclidean.oned.Euclidean1D;

public class PolygonsSetTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        PolygonsSet set = new PolygonsSet();
        assertNotNull(set);
        Vector2D[][] vertices = set.getVertices();
        assertNotNull(vertices);
    }

    @Test
    public void testBSPTreeConstructor() throws Throwable {
        BSPTree<Euclidean2D> tree = new BSPTree<Euclidean2D>(Boolean.TRUE);
        PolygonsSet set = new PolygonsSet(tree);
        assertNotNull(set);
        Vector2D[][] vertices = set.getVertices();
        assertNotNull(vertices);
    }

    @Test
    public void testBoxConstructor() throws Throwable {
        PolygonsSet set = new PolygonsSet(0.0, 1.0, 0.0, 1.0);
        assertNotNull(set);
        Vector2D[][] vertices = set.getVertices();
        assertTrue(vertices.length > 0);
    }

    @Test
    public void testCollectionConstructor() throws Throwable {
        Collection<SubHyperplane<Euclidean2D>> boundary = new ArrayList<SubHyperplane<Euclidean2D>>();
        PolygonsSet set = new PolygonsSet(boundary);
        assertNotNull(set);
    }

    @Test
    public void testBuildNew() throws Throwable {
        PolygonsSet set = new PolygonsSet();
        BSPTree<Euclidean2D> tree = new BSPTree<Euclidean2D>(Boolean.FALSE);
        PolygonsSet newSet = set.buildNew(tree);
        assertNotNull(newSet);
    }

    @Test
    public void testGetVerticesInfiniteSpace() throws Throwable {
        PolygonsSet set = new PolygonsSet(new BSPTree<Euclidean2D>(Boolean.TRUE));
        Vector2D[][] vertices = set.getVertices();
        assertNotNull(vertices);
        assertEquals(0, vertices.length);
    }

    @Test
    public void testComputeGeometricalPropertiesEmpty() throws Throwable {
        PolygonsSet set = new PolygonsSet(new BSPTree<Euclidean2D>(Boolean.FALSE));
        Vector2D[][] vertices = set.getVertices();
        assertNotNull(vertices);
    }

    @Test
    public void testBoxGeometricalProperties() throws Throwable {
        PolygonsSet set = new PolygonsSet(0.0, 2.0, 0.0, 3.0);
        assertEquals(6.0, set.getSize(), 1.0e-10);
        Vector2D barycenter = set.getBarycenter();
        assertNotNull(barycenter);
        assertEquals(1.0, barycenter.getX(), 1.0e-10);
        assertEquals(1.5, barycenter.getY(), 1.0e-10);
    }
}