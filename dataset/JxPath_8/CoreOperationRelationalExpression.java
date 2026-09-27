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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jxpath.ri.compiler;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.InfoSetUtil;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.SelfContext;

/**
 * Base implementation of Expression for the operations "&gt;", "&gt;=", "&lt;", "&lt;=".
 * @since JXPath 1.3
 *
 * @author Matt Benson
 * @version $Revision$ $Date$
 */
public abstract class CoreOperationRelationalExpression extends CoreOperation {

    protected CoreOperationRelationalExpression(Expression[] args) {
        super(args);
    }

    public final Object computeValue(EvalContext context) {
        return compute(args[0].computeValue(context), args[1]
                .computeValue(context)) ? Boolean.TRUE : Boolean.FALSE;
    }

    protected final int getPrecedence() {
        return 3;
    }

    protected final boolean isSymmetric() {
        return false;
    }

    protected abstract boolean evaluateCompare(int compare);

    private boolean compute(Object left, Object right) {
        left = reduce(left);
        right = reduce(right);

        if (left instanceof InitialContext) {
            ((InitialContext) left).reset();
        }
        if (right instanceof InitialContext) {
            ((InitialContext) right).reset();
        }
        if (left instanceof Iterator && right instanceof Iterator) {
            return findMatch((Iterator) left, (Iterator) right);
        }
        if (left instanceof Iterator) {
            return containsMatch((Iterator) left, right);
        }
        if (right instanceof Iterator) {
            return containsMatch((Iterator) right, left);
        }
        double ld = InfoSetUtil.doubleValue(left);
        double rd = InfoSetUtil.doubleValue(right);
        return evaluateCompare(ld == rd ? 0 : ld < rd ? -1 : 1);
    }

    private Object reduce(Object o) {
        if (o instanceof SelfContext) {
            o = ((EvalContext) o).getSingleNodePointer();
        }
        if (o instanceof Collection) {
            o = ((Collection) o).iterator();
        }
        return o;
    }

    private boolean containsMatch(Iterator it, Object value) {
        while (it.hasNext()) {
            Object element = it.next();
            if (compute(element, value)) {
                return true;
            }
        }
        return false;
    }

    private boolean findMatch(Iterator lit, Iterator rit) {
        HashSet left = new HashSet();
        while (lit.hasNext()) {
            left.add(lit.next());
        }
        while (rit.hasNext()) {
            if (containsMatch(left.iterator(), rit.next())) {
                return true;
            }
        }
        return false;
    }

}