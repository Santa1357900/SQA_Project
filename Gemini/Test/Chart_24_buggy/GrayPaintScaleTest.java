package org.jfree.chart.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Color;
import java.awt.Paint;

import org.junit.Test;

public class GrayPaintScaleTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale();
        assertEquals(0.0, scale.getLowerBound(), 0.0000001);
        assertEquals(1.0, scale.getUpperBound(), 0.0000001);
    }

    @Test
    public void testParameterizedConstructorValid() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale(-5.0, 5.0);
        assertEquals(-5.0, scale.getLowerBound(), 0.0000001);
        assertEquals(5.0, scale.getUpperBound(), 0.0000001);
    }

    @Test
    public void testParameterizedConstructorInvalidLowerGreater() throws Throwable {
        try {
            new GrayPaintScale(2.0, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Requires lowerBound < upperBound"));
        }
    }

    @Test
    public void testParameterizedConstructorInvalidEqual() throws Throwable {
        try {
            new GrayPaintScale(1.0, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Requires lowerBound < upperBound"));
        }
    }

    @Test
    public void testGetPaintWithinRange() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale(0.0, 100.0);
        Paint paint = scale.getPaint(50.0);
        assertNotNull(paint);
        assertTrue(paint instanceof Color);
        Color color = (Color) paint;
        int expected = (int) ((50.0 - 0.0) / (100.0 - 0.0) * 255.0);
        assertEquals(expected, color.getRed());
        assertEquals(expected, color.getGreen());
        assertEquals(expected, color.getBlue());
    }

    @Test
    public void testGetPaintBelowLowerBound() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale(10.0, 20.0);
        Paint paint = scale.getPaint(5.0);
        assertNotNull(paint);
        Color color = (Color) paint;
        int expected = (int) ((5.0 - 10.0) / (20.0 - 10.0) * 255.0);
        assertEquals(expected, color.getRed());
    }

    @Test
    public void testGetPaintAboveUpperBound() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale(10.0, 20.0);
        Paint paint = scale.getPaint(25.0);
        assertNotNull(paint);
        Color color = (Color) paint;
        int expected = (int) ((25.0 - 10.0) / (20.0 - 10.0) * 255.0);
        assertEquals(expected, color.getRed());
    }

    @Test
    public void testEqualsSameInstance() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale(0.0, 1.0);
        assertTrue(scale.equals(scale));
    }

    @Test
    public void testEqualsNull() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale(0.0, 1.0);
        assertFalse(scale.equals(null));
    }

    @Test
    public void testEqualsDifferentType() throws Throwable {
        GrayPaintScale scale = new GrayPaintScale(0.0, 1.0);
        assertFalse(scale.equals("NotAGrayPaintScale"));
    }

    @Test
    public void testEqualsDifferentLowerBound() throws Throwable {
        GrayPaintScale scale1 = new GrayPaintScale(0.0, 1.0);
        GrayPaintScale scale2 = new GrayPaintScale(0.1, 1.0);
        assertFalse(scale1.equals(scale2));
    }

    @Test
    public void testEqualsDifferentUpperBound() throws Throwable {
        GrayPaintScale scale1 = new GrayPaintScale(0.0, 1.0);
        GrayPaintScale scale2 = new GrayPaintScale(0.0, 2.0);
        assertFalse(scale1.equals(scale2));
    }

    @Test
    public void testEqualsIdenticalValues() throws Throwable {
        GrayPaintScale scale1 = new GrayPaintScale(0.0, 1.0);
        GrayPaintScale scale2 = new GrayPaintScale(0.0, 1.0);
        assertTrue(scale1.equals(scale2));
    }

    @Test
    public void testClone() throws Throwable {
        GrayPaintScale scale1 = new GrayPaintScale(1.5, 4.5);
        Object clonedObj = scale1.clone();
        assertNotNull(clonedObj);
        assertTrue(clonedObj instanceof GrayPaintScale);
        GrayPaintScale scale2 = (GrayPaintScale) clonedObj;
        assertTrue(scale1.equals(scale2));
        assertFalse(scale1 == scale2);
    }
}