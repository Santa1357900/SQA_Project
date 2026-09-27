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
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

/**
 * For use with CodeGenerator to determine the cost of generated code.
 *
 * @see CodeGenerator
 * @see CodePrinter
 */
class InlineCostEstimator {
  // For now simply assume identifiers are 2 characters.
  private static final String ESTIMATED_IDENTIFIER = "ab";
  static final int ESTIMATED_IDENTIFIER_COST = ESTIMATED_IDENTIFIER.length();

  private InlineCostEstimator() {
  }

  /**
   * Determines the size of the js code.
   */
  static int getCost(Node root) {
    return getCost(root, Integer.MAX_VALUE);
  }

  /**
   * Determines the estimated size of the js snippet represented by the node.
   */
  static int getCost(Node root, int costThreshhold) {
    CompiledSizeEstimator estimator = new CompiledSizeEstimator(costThreshhold);
    estimator.add(root);
    return estimator.getCost();
  }

  /**
   * Code consumer that estimates compiled size by assuming names are
   * shortened and all whitespace is stripped.
   */
  private static class CompiledSizeEstimator extends CodeConsumer {
    private int maxCost;
    private int cost = 0;
    private char last = '\0';
    private boolean continueProcessing = true;

    CompiledSizeEstimator(int costThreshhold) {
      this.maxCost = costThreshhold;
    }

    void add(Node root) {
      CodeGenerator cg = new CodeGenerator(this);
      cg.add(root);
    }

    int getCost() {
      return cost;
    }

    @Override
    boolean continueProcessing() {
      return continueProcessing;
    }

    @Override
    char getLastChar() {
      return last;
    }

    @Override
    void append(String str){
      last = str.charAt(str.length() - 1);
      cost += str.length();
      if (maxCost <= cost) {
        continueProcessing = false;
      }
    }

    @Override
    void addIdentifier(String identifier) {
      add(ESTIMATED_IDENTIFIER);
    }

    /**
     * Constants (true, false, null) are considered basically free,
     * because it's likely that they will get folded when we're done.
     */
  }
}