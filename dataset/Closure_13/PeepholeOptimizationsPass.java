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
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;

/**
 * A compiler pass to run various peephole optimizations (e.g. constant folding,
 * some useless code removal, some minimizations).
 *
 * @author dcc@google.com (Devin Coughlin)
 * @author acleung@google.com (Alan Leung)(
 */
class PeepholeOptimizationsPass
    implements CompilerPass {
  private AbstractCompiler compiler;

  // Use an array here for faster iteration compared to ImmutableSet
  private final AbstractPeepholeOptimization[] peepholeOptimizations;

  // Track whether the a scope has been modified so that it can be revisited
  // immediately.
  private StateStack traversalState = new StateStack();

  static private class ScopeState {
    boolean changed;
    boolean traverseChildScopes;
    ScopeState() {
      reset();
    }

    void reset() {
      changed = false;
      traverseChildScopes = true;
    }
  }

  static private class StateStack {
    private ArrayList<ScopeState> states = Lists.newArrayList();
    private int currentDepth = 0;

    StateStack() {
      states.add(new ScopeState());
    }

    ScopeState peek() {
      return states.get(currentDepth);
    }

    void push() {
      currentDepth++;
      if (states.size() <= currentDepth) {
        states.add(new ScopeState());
      } else {
        states.get(currentDepth).reset();
      }
    }

    void pop() {
      currentDepth--;
    }
  }

  private class PeepholeChangeHandler implements CodeChangeHandler {
    @Override
    public void reportChange() {
      traversalState.peek().changed = true;
    }
  }

  /**
   * Creates a peephole optimization pass that runs the given
   * optimizations.
   */
  PeepholeOptimizationsPass(AbstractCompiler compiler,
      AbstractPeepholeOptimization... optimizations) {
    this.compiler = compiler;
    this.peepholeOptimizations = optimizations;
  }

  public AbstractCompiler getCompiler() {
    return compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    PeepholeChangeHandler handler = new PeepholeChangeHandler();
    compiler.addChangeHandler(handler);
    beginTraversal();
    traverse(root);
    endTraversal();
    compiler.removeChangeHandler(handler);
  }

  private void traverse(Node node) {
    // The goal here is to avoid retraversing
    // the entire AST to catch newly created opportunities.
    // So we track whether a "unit of code" has changed,
    // and revisit immediately.
    if (!shouldVisit(node)) {
      return;
    }

    int visits = 0;
    do {
      Node c = node.getFirstChild();
      while(c != null) {
        traverse(c);
        Node next = c.getNext();
        c = next;
      }

      visit(node);
      visits++;

      Preconditions.checkState(visits < 10000, "too many interations");
    } while (shouldRetraverse(node));

    exitNode(node);
  }

  private boolean shouldRetraverse(Node node) {
    if (node.getParent() != null && node.isFunction() || node.isScript()) {
      ScopeState state = traversalState.peek();
      if (state.changed) {
        // prepare to re-visit the scope:
        // when revisiting, only visit the immediate scope
        // this reduces the cost of getting to a fixed
        // point in global scope.
        state.changed = false;
        state.traverseChildScopes = false;
        return true;
      }
    }
    return false;
  }

  private boolean shouldVisit(Node node) {
    if (node.isFunction() || node.isScript()) {
      ScopeState previous = traversalState.peek();
      if (!previous.traverseChildScopes) {
        return false;
      }
      traversalState.push();
    }
    return true;
  }

  private void exitNode(Node node) {
    if (node.isFunction() || node.isScript()) {
      traversalState.pop();
    }
  }

  public void visit(Node n) {
    Node currentVersionOfNode = n;
    boolean somethingChanged = false;

    do {
      somethingChanged = false;
      for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
        Node newVersionOfNode =
            optimization.optimizeSubtree(currentVersionOfNode);

        if (newVersionOfNode != currentVersionOfNode) {
          somethingChanged = true;

          currentVersionOfNode = newVersionOfNode;
        }

        if (currentVersionOfNode == null) {
          return;
        }
      }
    } while(somethingChanged);
  }

  /**
   * Make sure that all the optimizations have the current traversal so they
   * can report errors.
   */
  private void beginTraversal() {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.beginTraversal(compiler);
    }
  }

  private void endTraversal() {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.endTraversal(compiler);
    }
  }
}