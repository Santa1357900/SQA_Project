ว่าคลาสมี Constructor อะไรบ้าง
   - ❌ ห้ามส่ง `null` ให้ parameter ที่เป็น primitive type (`int`, `double`, `boolean`, `char`, `long`) เด็ดขาด เพราะ javac จะฟ้อง type incompatible
   - ❌ ห้ามเรียกใช้ `private` method / constructor หรือ private field หรือ private inner class (เช่น `Assign` ใน `RemoveUnusedVars`) โดยตรง
   - ❌ ห้ามใช้ `assertEquals(null, val)` ให้ใช้ `assertNull(val)` แทนเสมอ
   - ❌ ห้ามใช้ `assertEquals(Double.NaN, val)` เพราะ ambiguous ใน JUnit 4 ให้ใช้ `assertEquals(Double.valueOf(Double.NaN), val)` หรือ `assertTrue(Double.isNaN(val))`
5. Full Imports Required:
   - ต้อง Import ทุกคลาสที่เรียกใช้อย่างครบถ้วน ไม่ละเลย Utility หรือ Annotation package (เช่น `com.fasterxml.jackson.databind.util.ClassUtil`, `com.fasterxml.jackson.annotation.*`, `org.jsoup.parser.*`, `org.jsoup.nodes.*`)

[TEST DESIGN & FAULT DETECTION DIRECTIVES (เพิ่มโอกาส REVEALING และลด INCONCLUSIVE)]
1. Oracle Precision (ลด Inconclusive จากการ Assert เปราะบาง):
   - ❌ อย่า Assert ข้อความ Exception หรือ toString() แบบ Exact Match (เพราะข้อความอาจเปลี่ยนตามเวอร์ชัน) ให้เน้น Assert ชนิดของ Exception หรือใช้ `assertTrue(e.getMessage().contains(...))` แทน
   - สำหรับกรณีทดสอบที่คาดว่าจะเกิด Exception ให้ใช้โครงสร้าง `try { method(); fail("Should throw exception"); } catch (SpecificException e) { /* Expected */ }` หรือ `@Test(expected = SpecificException.class)`
2. Bug-Hunting Edge Cases (กระตุ้นจุดบกพร่องที่ซ่อนอยู่):
   - ค่าขอบเขตวิกฤต: ค่าศูนย์ (0), ค่าลบ (-1), Maximum/Minimum values, Integer overflow/underflow
   - ปัญหา Null Safety: ส่ง `null` เข้าไปยัง Public method ที่รับ Object เพื่อดูว่ามี NullPointerException โดยไม่ได้จัดการหรือไม่
   - กรณี Collection/Array: ทดสอบ Empty, Single element, Duplicate elements, Null elements, Unmodifiable collection
   - กรณี String: Empty string `""`, Whitespace only `"   "`, String containing delimiters, Escape sequences, Special Unicode characters
   - Boundary & Loop conditions: จุดเปลี่ยนเงื่อนไข `<` vs `<=`, Off-by-one errors, Empty input streams

[TARGET SOURCE CODE]
/* ===========================================================
 * JFreeChart : a free chart library for the Java(tm) platform
 * ===========================================================
 *
 * (C) Copyright 2000-2007, by Object Refinery Limited and Contributors.
 *
 * Project Info:  http://www.jfree.org/jfreechart/index.html
 *
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by 
 * the Free Software Foundation; either version 2.1 of the License, or 
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, 
 * USA.  
 *
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc. 
 * in the United States and other countries.]
 *
 * -------------------
 * GrayPaintScale.java
 * -------------------
 * (C) Copyright 2006, 2007, by Object Refinery Limited.
 *
 * Original Author:  David Gilbert (for Object Refinery Limited);
 * Contributor(s):   -;
 *
 * $Id: GrayPaintScale.java,v 1.1.2.1 2007/01/31 14:15:16 mungady Exp $
 *
 * Changes
 * -------
 * 05-Jul-2006 : Version 1 (DG);
 * 31-Jan-2007 : Renamed min and max to lowerBound and upperBound (DG);
 * 21-Jun-2007 : Removed JCommon dependencies (DG);
 * 26-Sep-2007 : Fixed bug 1767315, problem in getPaint() method (DG);
 * 
 */

package org.jfree.chart.renderer;

import java.awt.Color;
import java.awt.Paint;
import java.io.Serializable;

import org.jfree.chart.util.PublicCloneable;

/**
 * A paint scale that returns shades of gray.
 * 
 * @since 1.0.4
 */
public class GrayPaintScale 
        implements PaintScale, PublicCloneable, Serializable {

    /** The lower bound. */
    private double lowerBound;
    
    /** The upper bound. */
    private double upperBound;
    
    /**
     * Creates a new <code>GrayPaintScale</code> instance with default values.
     */
    public GrayPaintScale() {
        this(0.0, 1.0);
    }
    
    /**
     * Creates a new paint scale for values in the specified range.
     * 
     * @param lowerBound  the lower bound.
     * @param upperBound  the upper bound.
     * 
     * @throws IllegalArgumentException if <code>lowerBound</code> is not
     *       less than <code>upperBound</code>.
     */
    public GrayPaintScale(double lowerBound, double upperBound) {
        if (lowerBound >= upperBound) {
            throw new IllegalArgumentException(
                    "Requires lowerBound < upperBound.");
        }
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }
    
    /**
     * Returns the lower bound.
     * 
     * @return The lower bound.
     * 
     * @see #getUpperBound()
     */
    public double getLowerBound() {
        return this.lowerBound;
    }

    /**
     * Returns the upper bound.
     * 
     * @return The upper bound.
     * 
     * @see #getLowerBound()
     */
    public double getUpperBound() {
        return this.upperBound;
    }

    /**
     * Returns a paint for the specified value.
     * 
     * @param value  the value (must be within the range specified by the
     *         lower and upper bounds for the scale).
     * 
     * @return A paint for the specified value.
     */
    public Paint getPaint(double value) {
        double v = Math.max(value, this.lowerBound);
        v = Math.min(v, this.upperBound);
        int g = (int) ((value - this.lowerBound) / (this.upperBound 
                - this.lowerBound) * 255.0);
        return new Color(g, g, g);
    }
    
    /**
     * Tests this <code>GrayPaintScale</code> instance for equality with an
     * arbitrary object.  This method returns <code>true</code> if and only
     * if:
     * <ul>
     * <li><code>obj</code> is not <code>null</code>;</li>
     * <li><code>obj</code> is an instance of <code>GrayPaintScale</code>;</li>
     * </ul>
     * 
     * @param obj  the object (<code>null</code> permitted).
     * 
     * @return A boolean.
     */
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof GrayPaintScale)) {
            return false;
        }
        GrayPaintScale that = (GrayPaintScale) obj;
        if (this.lowerBound != that.lowerBound) {
            return false;
        }
        if (this.upperBound != that.upperBound) {
            return false;
        }
        return true;    
    }
    
    /**
     * Returns a clone of this <code>GrayPaintScale</code> instance.
     * 
     * @return A clone.
     * 
     * @throws CloneNotSupportedException if there is a problem cloning this
     *     instance.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
    
}