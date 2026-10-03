package org.jfree.chart.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ShapeListClaudeTest {

    // getShape on a brand-new empty list must return null for any unset index
    @Test
    public void testGetShape_indexNotSet_returnsNull() throws Throwable {
        ShapeList list = new ShapeList();
        assertNull(list.getShape(0));
    }

    // setShape then getShape at same index returns the exact same Shape instance
    @Test
    public void testGetShape_afterSetShape_returnsSameShapeInstance() throws Throwable {
        ShapeList list = new ShapeList();
        Rectangle r = new Rectangle(1, 2, 3, 4);
        list.setShape(0, r);
        assertSame(r, list.getShape(0));
    }

    // setShape at a higher index expands the list; lower unset indices remain null
    @Test
    public void testSetShape_expandsList_unsetIndicesReturnNull() throws Throwable {
        ShapeList list = new ShapeList();
        Rectangle r = new Rectangle(0, 0, 5, 5);
        list.setShape(4, r);
        assertNull(list.getShape(1));
        assertSame(r, list.getShape(4));
    }

    // overwriting an existing index updates the stored shape
    @Test
    public void testSetShape_overwriteExistingIndex_updatesValue() throws Throwable {
        ShapeList list = new ShapeList();
        Rectangle r1 = new Rectangle(0, 0, 1, 1);
        Rectangle r2 = new Rectangle(0, 0, 2, 2);
        list.setShape(0, r1);
        list.setShape(0, r2);
        assertSame(r2, list.getShape(0));
    }

    // setShape can explicitly store null, overwriting a previous shape
    @Test
    public void testSetShape_nullShape_storesNull() throws Throwable {
        ShapeList list = new ShapeList();
        list.setShape(0, new Rectangle(0, 0, 1, 1));
        list.setShape(0, null);
        assertNull(list.getShape(0));
    }

    // equals: obj == this branch returns true
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        ShapeList list = new ShapeList();
        assertTrue(list.equals(list));
    }

    // equals: null argument must return false, never throw
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        ShapeList list = new ShapeList();
        assertFalse(list.equals(null));
    }

    // equals: non-ShapeList instance must return false (instanceof branch)
    @Test
    public void testEquals_differentClassObject_returnsFalse() throws Throwable {
        ShapeList list = new ShapeList();
        assertFalse(list.equals("not a shape list"));
    }

    // equals: two freshly created empty lists are equal
    @Test
    public void testEquals_twoEmptyLists_returnsTrue() throws Throwable {
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        assertTrue(a.equals(b));
    }

    // equals: same Shape reference at same index -> equal
    @Test
    public void testEquals_sameShapeReferenceAtSameIndex_returnsTrue() throws Throwable {
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        Rectangle r = new Rectangle(1, 1, 2, 2);
        a.setShape(0, r);
        b.setShape(0, r);
        assertTrue(a.equals(b));
    }

    // equals: structurally identical shapes held in different GeneralPath instances
    // must be considered equal (GeneralPath does not override Object.equals(), so the
    // list's equals() must compare shape geometry, not object identity)
    @Test
    public void testEquals_structurallyEqualDifferentShapeInstances_returnsTrue() throws Throwable {
        GeneralPath p1 = new GeneralPath();
        p1.moveTo(1.0f, 1.0f);
        p1.lineTo(2.0f, 2.0f);
        p1.lineTo(3.0f, 1.0f);
        p1.closePath();
        GeneralPath p2 = new GeneralPath();
        p2.moveTo(1.0f, 1.0f);
        p2.lineTo(2.0f, 2.0f);
        p2.lineTo(3.0f, 1.0f);
        p2.closePath();
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        a.setShape(0, p1);
        b.setShape(0, p2);
        assertTrue(a.equals(b));
    }

    // equals must be symmetric for structurally equal shapes
    @Test
    public void testEquals_symmetric_forStructurallyEqualShapes() throws Throwable {
        GeneralPath p1 = new GeneralPath();
        p1.moveTo(0.0f, 0.0f);
        p1.lineTo(5.0f, 0.0f);
        GeneralPath p2 = new GeneralPath();
        p2.moveTo(0.0f, 0.0f);
        p2.lineTo(5.0f, 0.0f);
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        a.setShape(0, p1);
        b.setShape(0, p2);
        assertTrue(b.equals(a));
    }

    // equals: different shape geometry must return false
    @Test
    public void testEquals_differentShapeGeometry_returnsFalse() throws Throwable {
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        a.setShape(0, new Rectangle(0, 0, 10, 10));
        b.setShape(0, new Rectangle(0, 0, 20, 20));
        assertFalse(a.equals(b));
    }

    // equals: both null shape at same index -> equal
    @Test
    public void testEquals_bothNullShapeAtIndex_returnsTrue() throws Throwable {
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        a.setShape(0, null);
        b.setShape(0, null);
        assertTrue(a.equals(b));
    }

    // equals: one null, other non-null at same index -> not equal
    @Test
    public void testEquals_oneNullOneNonNullShape_returnsFalse() throws Throwable {
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        a.setShape(0, null);
        b.setShape(0, new Rectangle(0, 0, 1, 1));
        assertFalse(a.equals(b));
    }

    // equals: modifying one list after they were equal makes them unequal again
    @Test
    public void testEquals_reflexiveAfterModification_returnsFalse() throws Throwable {
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        Rectangle r = new Rectangle(0, 0, 1, 1);
        a.setShape(0, r);
        b.setShape(0, r);
        assertTrue(a.equals(b));
        b.setShape(0, new Rectangle(9, 9, 9, 9));
        assertFalse(a.equals(b));
    }

    // hashCode: equal (empty) lists must produce equal hash codes
    @Test
    public void testHashCode_equalEmptyLists_sameHashCode() throws Throwable {
        ShapeList a = new ShapeList();
        ShapeList b = new ShapeList();
        assertEquals(a.hashCode(), b.hashCode());
    }

    // hashCode: repeated calls on the same instance are consistent
    @Test
    public void testHashCode_consistentAcrossMultipleCalls() throws Throwable {
        ShapeList list = new ShapeList();
        list.setShape(0, new Rectangle(1, 1, 1, 1));
        int h1 = list.hashCode();
        int h2 = list.hashCode();
        assertEquals(h1, h2);
    }

    // clone must return an instance of ShapeList
    @Test
    public void testClone_returnsShapeListInstance() throws Throwable {
        ShapeList list = new ShapeList();
        list.setShape(0, new Rectangle(1, 2, 3, 4));
        Object clone = list.clone();
        assertTrue(clone instanceof ShapeList);
    }

    // clone must be equal to the original right after cloning
    @Test
    public void testClone_isEqualToOriginalImmediatelyAfterClone() throws Throwable {
        ShapeList list = new ShapeList();
        list.setShape(0, new Rectangle(1, 2, 3, 4));
        ShapeList clone = (ShapeList) list.clone();
        assertTrue(list.equals(clone));
    }

    // clone must be independent: replacing a shape in the clone does not affect original
    @Test
    public void testClone_isIndependentAfterModification() throws Throwable {
        ShapeList list = new ShapeList();
        Rectangle original = new Rectangle(1, 1, 1, 1);
        list.setShape(0, original);
        ShapeList clone = (ShapeList) list.clone();
        clone.setShape(0, new Rectangle(9, 9, 9, 9));
        assertSame(original, list.getShape(0));
    }

    // clone of an empty list works and is equal to the original
    @Test
    public void testClone_emptyListClonesSuccessfully() throws Throwable {
        ShapeList list = new ShapeList();
        ShapeList clone = (ShapeList) list.clone();
        assertTrue(list.equals(clone));
    }

    // serialization: shape data at a set index is preserved across write/read
    @Test
    public void testSerialization_preservesShapeData() throws Throwable {
        ShapeList list = new ShapeList();
        Rectangle2D.Double r = new Rectangle2D.Double(1.0, 2.0, 3.0, 4.0);
        list.setShape(0, r);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(list);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray()));
        ShapeList restored = (ShapeList) ois.readObject();
        ois.close();

        Shape restoredShape = restored.getShape(0);
        assertTrue(restoredShape instanceof Rectangle2D);
        Rectangle2D rr = (Rectangle2D) restoredShape;
        assertEquals(1.0, rr.getX(), 1e-9);
        assertEquals(2.0, rr.getY(), 1e-9);
        assertEquals(3.0, rr.getWidth(), 1e-9);
        assertEquals(4.0, rr.getHeight(), 1e-9);
    }

    // serialization: null entries are preserved (writeInt(-1) branch in writeObject)
    @Test
    public void testSerialization_preservesNullEntries() throws Throwable {
        ShapeList list = new ShapeList();
        list.setShape(0, new Rectangle2D.Double(0.0, 0.0, 1.0, 1.0));
        list.setShape(1, null);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(list);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray()));
        ShapeList restored = (ShapeList) ois.readObject();
        ois.close();

        assertNull(restored.getShape(1));
        assertNotNull(restored.getShape(0));
    }
}
