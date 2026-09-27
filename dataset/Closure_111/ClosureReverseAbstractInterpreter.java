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
 * Copyright 2007 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.type;

import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_VOID;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_STRING_BOOLEAN;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Visitor;

import java.util.Map;

/**
 * A reverse abstract interpreter (RAI) for specific closure patterns such as
 * {@code goog.isDef}.
 *
 */
public class ClosureReverseAbstractInterpreter
    extends ChainableReverseAbstractInterpreter {

  /**
   * For when {@code goog.isArray} returns true.
   */
  private final Visitor<JSType> restrictToArrayVisitor =
      new RestrictByTrueTypeOfResultVisitor() {
        @Override
        protected JSType caseTopType(JSType topType) {
          return topType;
        }

        @Override
        public JSType caseObjectType(ObjectType type) {
          JSType arrayType = getNativeType(ARRAY_TYPE);
          return arrayType.isSubtype(type) ? arrayType : null;
        }
      };

  /**
   * For when {@code goog.isArray} returns false.
   */
  private final Visitor<JSType> restrictToNotArrayVisitor =
      new RestrictByFalseTypeOfResultVisitor() {
        @Override
        public JSType caseObjectType(ObjectType type) {
          return type.isSubtype(getNativeType(ARRAY_TYPE)) ? null : type;
        }
      };

  /**
   * For when {@code goog.isObject} returns true. This includes functions, but
   * not {@code null}.
   */
  private final Visitor<JSType> restrictToObjectVisitor =
      new RestrictByTrueTypeOfResultVisitor() {
        @Override
        protected JSType caseTopType(JSType topType) {
          return getNativeType(NO_OBJECT_TYPE);
        }

        @Override
        public JSType caseObjectType(ObjectType type) {
          return type;
        }

        @Override
        public JSType caseFunctionType(FunctionType type) {
          return type;
        }
      };

  /**
   * For when {@code goog.isObject} returns false.
   */
  private final Visitor<JSType> restrictToNotObjectVisitor =
      new RestrictByFalseTypeOfResultVisitor() {

        @Override
        public JSType caseAllType() {
          return typeRegistry.createUnionType(
              getNativeType(NUMBER_STRING_BOOLEAN), getNativeType(NULL_VOID));
        }

        @Override
        public JSType caseObjectType(ObjectType type) {
          return null;
        }

        @Override
        public JSType caseFunctionType(FunctionType type) {
          return null;
        }
      };

  /** Functions used to restrict types. */
  private Map<String, Function<TypeRestriction, JSType>> restricters;

  /**
   * Creates a {@link ClosureReverseAbstractInterpreter}.
   */
  public ClosureReverseAbstractInterpreter(CodingConvention convention,
      final JSTypeRegistry typeRegistry) {
    super(convention, typeRegistry);
    this.restricters =
      new ImmutableMap.Builder<String, Function<TypeRestriction, JSType>>()
      .put("isDef", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          if (p.outcome) {
            return getRestrictedWithoutUndefined(p.type);
          } else {
            return  p.type != null ?
                getNativeType(VOID_TYPE).getGreatestSubtype(p.type) : null;
          }
         }
      })
      .put("isNull", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          if (p.outcome) {
            return p.type != null ?
                getNativeType(NULL_TYPE).getGreatestSubtype(p.type) : null;
          } else {
            return getRestrictedWithoutNull(p.type);
          }
        }
      })
      .put("isDefAndNotNull", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          if (p.outcome) {
            return getRestrictedWithoutUndefined(
                getRestrictedWithoutNull(p.type));
          } else {
            return p.type != null ?
                getNativeType(NULL_VOID).getGreatestSubtype(p.type) : null;
          }
        }
      })
      .put("isString", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "string", p.outcome);
        }
      })
      .put("isBoolean", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "boolean", p.outcome);
        }
      })
      .put("isNumber", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "number", p.outcome);
        }
      })
      .put("isFunction", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "function", p.outcome);
        }
      })
      .put("isArray", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          if (p.type == null) {
            return p.outcome ? getNativeType(ARRAY_TYPE) : null;
          }

          Visitor<JSType> visitor = p.outcome ? restrictToArrayVisitor :
              restrictToNotArrayVisitor;
          return p.type.visit(visitor);
        }
      })
      .put("isObject", new Function<TypeRestriction, JSType>() {
        @Override
        public JSType apply(TypeRestriction p) {
          if (p.type == null) {
            return p.outcome ? getNativeType(OBJECT_TYPE) : null;
          }

          Visitor<JSType> visitor = p.outcome ? restrictToObjectVisitor :
              restrictToNotObjectVisitor;
          return p.type.visit(visitor);
        }
      })
      .build();
  }

  @Override
  public FlowScope getPreciserScopeKnowingConditionOutcome(Node condition,
      FlowScope blindScope, boolean outcome) {
    if (condition.isCall() && condition.getChildCount() == 2) {
      Node callee = condition.getFirstChild();
      Node param = condition.getLastChild();
      if (callee.isGetProp() && param.isQualifiedName()) {
        JSType paramType =  getTypeIfRefinable(param, blindScope);
        Node left = callee.getFirstChild();
        Node right = callee.getLastChild();
        if (left.isName() && "goog".equals(left.getString()) &&
            right.isString()) {
          Function<TypeRestriction, JSType> restricter =
              restricters.get(right.getString());
          if (restricter != null) {
            return restrictParameter(param, paramType, blindScope, restricter,
                outcome);
          }
        }
      }
    }
    return nextPreciserScopeKnowingConditionOutcome(
        condition, blindScope, outcome);
  }

  private FlowScope restrictParameter(Node parameter, JSType type,
      FlowScope blindScope, Function<TypeRestriction, JSType> restriction,
      boolean outcome) {
    // restricting
    type = restriction.apply(new TypeRestriction(type, outcome));

    // changing the scope
    if (type != null) {
      FlowScope informed = blindScope.createChildFlowScope();
      declareNameInScope(informed, parameter, type);
      return informed;
    } else {
      return blindScope;
    }
  }

  private static class TypeRestriction {
    private final JSType type;
    private final boolean outcome;

    private TypeRestriction(JSType type, boolean outcome) {
      this.type = type;
      this.outcome = outcome;
    }
  }
}