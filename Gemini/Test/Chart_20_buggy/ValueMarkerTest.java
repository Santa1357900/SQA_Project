package org.jfree.chart.plot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Stroke;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValueMarkerTest {

    @Test
    public void testConstructorOneParam() throws Throwable {
        ValueMarker marker = new ValueMarker(100.5);
        assertEquals(100.5, marker.getValue(), 0.0001);
        assertNotNull(marker.getPaint());
        assertNotNull(marker.getStroke());
    }

    @Test
    public void testConstructorThreeParams() throws Throwable {
        Paint paint = Color.RED;
        Stroke stroke = new BasicStroke(1.0f);
        ValueMarker marker = new ValueMarker(200.0, paint, stroke);
        
        assertEquals(200.0, marker.getValue(), 0.0001);
        assertEquals(paint, marker.getPaint());
        assertEquals(stroke, marker.getStroke());
    }

    @Test
    public void testConstructorSixParams() throws Throwable {
        Paint paint = Color.BLUE;
        Stroke stroke = new BasicStroke(2.0f);
        Paint outlinePaint = Color.GREEN;
        Stroke outlineStroke = new BasicStroke(3.0f);
        float alpha = 0.5f;

        ValueMarker marker = new ValueMarker(300.0, paint, stroke, outlinePaint, outlineStroke, alpha);
        
        assertEquals(300.0, marker.getValue(), 0.0001);
        assertEquals(paint, marker.getPaint());
        assertEquals(stroke, marker.getStroke());
        assertEquals(outlinePaint, marker.getOutlinePaint());
        assertEquals(outlineStroke, marker.getOutlineStroke());
        assertEquals(alpha, marker.getAlpha(), 0.0001);
    }

    @Test
    public void testSetValue() throws Throwable {
        ValueMarker marker = new ValueMarker(50.0);
        assertEquals(50.0, marker.getValue(), 0.0001);
        
        marker.setValue(150.0);
        assertEquals(150.0, marker.getValue(), 0.0001);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        ValueMarker m1 = new ValueMarker(100.0, Color.RED, new BasicStroke(1.0f));
        ValueMarker m2 = new ValueMarker(100.0, Color.RED, new BasicStroke(1.0f));
        ValueMarker m3 = new ValueMarker(200.0, Color.RED, new BasicStroke(1.0f));
        ValueMarker m4 = new ValueMarker(100.0, Color.BLUE, new BasicStroke(1.0f));

        assertTrue(m1.equals(m1));
        assertTrue(m1.equals(m2));
        assertTrue(m2.equals(m1));

        assertFalse(m1.equals(null));
        assertFalse(m1.equals("Some String"));
        assertFalse(m1.equals(m3)); // Different value
        assertFalse(m1.equals(m4)); // Different paint/super equals
    }
}