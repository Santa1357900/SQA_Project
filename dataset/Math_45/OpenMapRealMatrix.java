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
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.math.linear;

import java.io.Serializable;

import org.apache.commons.math.exception.NumberIsTooLargeException;
import org.apache.commons.math.util.OpenIntToDoubleHashMap;

/**
 * Sparse matrix implementation based on an open addressed map.
 *
 * @version $Id$
 * @since 2.0
 */
public class OpenMapRealMatrix extends AbstractRealMatrix
    implements SparseRealMatrix, Serializable {
    /** Serializable version identifier. */
    private static final long serialVersionUID = -5962461716457143437L;
    /** Number of rows of the matrix. */
    private final int rows;
    /** Number of columns of the matrix. */
    private final int columns;
    /** Storage for (sparse) matrix elements. */
    private final OpenIntToDoubleHashMap entries;

    /**
     * Build a sparse matrix with the supplied row and column dimensions.
     *
     * @param rowDimension Number of rows of the matrix.
     * @param columnDimension Number of columns of the matrix.
     */
    public OpenMapRealMatrix(int rowDimension, int columnDimension) {
        super(rowDimension, columnDimension);
        this.rows = rowDimension;
        this.columns = columnDimension;
        this.entries = new OpenIntToDoubleHashMap(0.0);
    }

    /**
     * Build a matrix by copying another one.
     *
     * @param matrix matrix to copy.
     */
    public OpenMapRealMatrix(OpenMapRealMatrix matrix) {
        this.rows = matrix.rows;
        this.columns = matrix.columns;
        this.entries = new OpenIntToDoubleHashMap(matrix.entries);
    }

    /** {@inheritDoc} */
    @Override
    public OpenMapRealMatrix copy() {
        return new OpenMapRealMatrix(this);
    }

    /** {@inheritDoc} */
    @Override
    public OpenMapRealMatrix createMatrix(int rowDimension, int columnDimension) {
        return new OpenMapRealMatrix(rowDimension, columnDimension);
    }

    /** {@inheritDoc} */
    @Override
    public int getColumnDimension() {
        return columns;
    }

    /**
     * Compute the sum of this matrix and {@code m}.
     *
     * @param m Matrix to be added.
     * @return {@code this} + {@code m}.
     * @throws org.apache.commons.math.exception.DimensionMismatchException
     * if {@code m} is not the same size as this matrix.
     */
    public OpenMapRealMatrix add(OpenMapRealMatrix m) {

        // safety check
        MatrixUtils.checkAdditionCompatible(this, m);

        final OpenMapRealMatrix out = new OpenMapRealMatrix(this);
        for (OpenIntToDoubleHashMap.Iterator iterator = m.entries.iterator(); iterator.hasNext();) {
            iterator.advance();
            final int row = iterator.key() / columns;
            final int col = iterator.key() - row * columns;
            out.setEntry(row, col, getEntry(row, col) + iterator.value());
        }

        return out;

    }

    /** {@inheritDoc} */
    @Override
    public OpenMapRealMatrix subtract(final RealMatrix m) {
        try {
            return subtract((OpenMapRealMatrix) m);
        } catch (ClassCastException cce) {
            return (OpenMapRealMatrix) super.subtract(m);
        }
    }

    /**
     * Subtract {@code m} from this matrix.
     *
     * @param m Matrix to be subtracted.
     * @return {@code this} - {@code m}.
     * @throws org.apache.commons.math.exception.DimensionMismatchException
     * if {@code m} is not the same size as this matrix.
     */
    public OpenMapRealMatrix subtract(OpenMapRealMatrix m) {
        // Safety check.
        MatrixUtils.checkAdditionCompatible(this, m);

        final OpenMapRealMatrix out = new OpenMapRealMatrix(this);
        for (OpenIntToDoubleHashMap.Iterator iterator = m.entries.iterator(); iterator.hasNext();) {
            iterator.advance();
            final int row = iterator.key() / columns;
            final int col = iterator.key() - row * columns;
            out.setEntry(row, col, getEntry(row, col) - iterator.value());
        }

        return out;
    }

    /** {@inheritDoc} */
    @Override
    public RealMatrix multiply(final RealMatrix m) {
        try {
            return multiply((OpenMapRealMatrix) m);
        } catch (ClassCastException cce) {

            // safety check
            MatrixUtils.checkMultiplicationCompatible(this, m);

            final int outCols = m.getColumnDimension();
            final BlockRealMatrix out = new BlockRealMatrix(rows, outCols);
            for (OpenIntToDoubleHashMap.Iterator iterator = entries.iterator(); iterator.hasNext();) {
                iterator.advance();
                final double value = iterator.value();
                final int key      = iterator.key();
                final int i        = key / columns;
                final int k        = key % columns;
                for (int j = 0; j < outCols; ++j) {
                    out.addToEntry(i, j, value * m.getEntry(k, j));
                }
            }

            return out;
        }
    }

    /**
     * Postmultiply this matrix by {@code m}.
     *
     * @param m Matrix to postmultiply by.
     * @return {@code this} * {@code m}.
     * @throws MatrixDimensionMismatchException
     * if the number of rows of {@code m} differ from the number of columns
     * of this matrix.
     */
    public OpenMapRealMatrix multiply(OpenMapRealMatrix m) {
        // Safety check.
        MatrixUtils.checkMultiplicationCompatible(this, m);

        final int outCols = m.getColumnDimension();
        OpenMapRealMatrix out = new OpenMapRealMatrix(rows, outCols);
        for (OpenIntToDoubleHashMap.Iterator iterator = entries.iterator(); iterator.hasNext();) {
            iterator.advance();
            final double value = iterator.value();
            final int key      = iterator.key();
            final int i        = key / columns;
            final int k        = key % columns;
            for (int j = 0; j < outCols; ++j) {
                final int rightKey = m.computeKey(k, j);
                if (m.entries.containsKey(rightKey)) {
                    final int outKey = out.computeKey(i, j);
                    final double outValue =
                        out.entries.get(outKey) + value * m.entries.get(rightKey);
                    if (outValue == 0.0) {
                        out.entries.remove(outKey);
                    } else {
                        out.entries.put(outKey, outValue);
                    }
                }
            }
        }

        return out;
    }

    /** {@inheritDoc} */
    @Override
    public double getEntry(int row, int column) {
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        return entries.get(computeKey(row, column));
    }

    /** {@inheritDoc} */
    @Override
    public int getRowDimension() {
        return rows;
    }

    /** {@inheritDoc} */
    @Override
    public void setEntry(int row, int column, double value) {
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        if (value == 0.0) {
            entries.remove(computeKey(row, column));
        } else {
            entries.put(computeKey(row, column), value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addToEntry(int row, int column, double increment) {
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        final int key = computeKey(row, column);
        final double value = entries.get(key) + increment;
        if (value == 0.0) {
            entries.remove(key);
        } else {
            entries.put(key, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void multiplyEntry(int row, int column, double factor) {
        MatrixUtils.checkRowIndex(this, row);
        MatrixUtils.checkColumnIndex(this, column);
        final int key = computeKey(row, column);
        final double value = entries.get(key) * factor;
        if (value == 0.0) {
            entries.remove(key);
        } else {
            entries.put(key, value);
        }
    }

    /**
     * Compute the key to access a matrix element
     * @param row row index of the matrix element
     * @param column column index of the matrix element
     * @return key within the map to access the matrix element
     */
    private int computeKey(int row, int column) {
        return row * columns + column;
    }


}