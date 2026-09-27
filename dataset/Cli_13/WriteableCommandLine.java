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
/**
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
package org.apache.commons.cli2;

import java.util.List;

/**
 * A CommandLine that detected values and options can be written to.
 */
public interface WriteableCommandLine extends CommandLine {

    /**
     * Adds an Option to the CommandLine
     * @param option the Option to add
     */
    void addOption(final Option option);

    /**
     * Adds a value to an Option in the CommandLine.
     * @param option the Option to add to
     * @param value the value to add
     */
    void addValue(final Option option, final Object value);

    /**
     * Retrieves the Argument values specified on the command line for the
     * specified Option, this doesn't return any values supplied
     * programmatically as defaults.
     *
     * @param option the Option associated with the values
     * @return a list of values or an empty List if none are found
     */

    /**
     * Sets the default values for an Option in the CommandLine
     * @param option the Option to add to
     * @param defaultValues the defaults for the option
     */
    void setDefaultValues(final Option option, final List defaultValues);

    /**
     * Adds a switch value to an Option in the CommandLine.
     * @param option the Option to add to
     * @param value the switch value to add
     * @throws IllegalStateException if the switch has already been added
     */
    void addSwitch(final Option option, final boolean value) throws IllegalStateException;

    /**
     * Sets the default state for a Switch in the CommandLine.
     * @param option the Option to add to
     * @param defaultSwitch the defaults state for ths switch
     */
    void setDefaultSwitch(final Option option, final Boolean defaultSwitch);

    /**
     * Adds a property value to a name in the CommandLine.
     * Replaces any existing value for the property.
     *
     * @param property the name of the property
     * @param value the value of the property
     */
    void addProperty(final String property, final String value);

    /**
     * Detects whether the argument looks like an Option trigger
     * @param argument the argument to test
     * @return true if the argument looks like an Option trigger
     */
    boolean looksLikeOption(final String argument);
}