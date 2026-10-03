package org.jfree.chart.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;

public class ShapeListTest {

    @Test
    public void testConstructorAndGetters() throws Throwable {
        ShapeList list = new ShapeList();
        assertEquals(0, list.size());
        assertNull(list.getShape(0));
    }

    @Test
    public void testSetAndGetShape() throws Throwable {
        ShapeList list = new ShapeList();
        Shape shape1 = new Rectangle2D.Double(1.0, 2.0, 3.0, 4.0);
        Shape shape2 = new Line2D.Double(0.0, 0.0, 10.0, 10.0);

        list.setShape(0, shape1);
        list.setShape(5, shape2);

        assertEquals(6, list.size());
        assertEquals(shape1, list.getShape(0));
        assertEquals(shape2, list.getShape(5));
        assertNull(list.getShape(1));
    }

    @Test
    public void testSetNullShape() throws Throwable {
        ShapeList list = new ShapeList();
        Shape shape = new Rectangle(1, 1);
        list.setShape(0, shape);
        assertEquals(shape, list.getShape(0));

        list.setShape(0, null);
        assertNull(list.getShape(0));
    }

    @Test
    public void testClone() throws Throwable {
        ShapeList list = new ShapeList();
        Shape shape = new Rectangle2D.Double(0, 0, 5, 5);
        list.setShape(1, shape);

        ShapeList clone = (ShapeList) list.clone();
        assertNotNull(clone);
        assertEquals(list, clone);
        assertTrue(clone instanceof ShapeList);
        assertEquals(shape, clone.getShape(1));
    }

    @Test
    public void testEquals() throws Throwable {
        ShapeList list1 = new ShapeList();
        ShapeList list2 = new ShapeList();

        assertTrue(list1.equals(list1));
        assertTrue(list1.equals(list2));

        Shape shape = new Rectangle2D.Double(0, 0, 1, 1);
        list1.setShape(0, shape);
        assertFalse(list1.equals(list2));

        list2.setShape(0, shape);
        assertTrue(list1.equals(list2));

        assertFalse(list1.equals(null));
        assertFalse(list1.equals("SomeString"));
    }

    @Test
    public void testHashCode() throws Throwable {
        ShapeList list1 = new ShapeList();
        ShapeList list2 = new ShapeList();

        assertEquals(list1.hashCode(), list2.hashCode());

        Shape shape = new Rectangle2D.Double(0, 0, 1, 1);
        list1.setShape(0, shape);
        list2.setShape(0, shape);
        assertEquals(list1.hashCode(), list2.hashCode());
    }

    @Test
    public void testSerialization() throws Throwable {
        ShapeList list = new ShapeList();
        Shape shape1 = new Rectangle2D.Double(1.0, 2.0, 3.0, 4.0);
        list.setShape(0, shape1);
        list.setShape(2, null); // Test index == -1 branch

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(list);
        out.close();

        ByteArrayInputStream ins = new ByteArrayInputStream(buffer.toByteArray());
        ObjectInputStream in = new ObjectInputStream(ins);
        ShapeList deserialized = (ShapeList) in.readObject();
        in.close();

        assertEquals(list, deserialized);
        assertEquals(shape1, deserialized.getShape(0));
        assertNull(deserialized.getShape(2));
    }
}