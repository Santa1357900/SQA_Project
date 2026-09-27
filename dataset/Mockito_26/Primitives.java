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
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.util;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class Primitives {
    
    public static <T> Class<T> primitiveTypeOf(Class<T> clazz) {
        if(clazz.isPrimitive()) {
            return clazz;
        }
        return (Class<T>) primitiveTypes.get(clazz);
    }

    public static boolean isPrimitiveWrapper(Class<?> type) {
        return wrapperReturnValues.containsKey(type);
    }

    public static <T> T primitiveWrapperOf(Class<T> type) {
        return (T) wrapperReturnValues.get(type);
    }

    public static <T> T primitiveValueOrNullFor(Class<T> primitiveType) {
        return (T) primitiveValues.get(primitiveType);
    }
    private static Map<Class<?>, Class<?>> wrapperTypes = new HashMap<Class<?>, Class<?>>();
    private static Map<Class<?>, Class<?>> primitiveTypes = new HashMap<Class<?>, Class<?>>();
    private static Map<Class<?>, Object> wrapperReturnValues = new HashMap<Class<?>, Object>();
    private static Map<Class<?>, Object> primitiveValues = new HashMap<Class<?>, Object>();

    static {
        primitiveTypes.put(Boolean.class, Boolean.TYPE);
        primitiveTypes.put(Character.class, Character.TYPE);
        primitiveTypes.put(Byte.class, Byte.TYPE);
        primitiveTypes.put(Short.class, Short.TYPE);
        primitiveTypes.put(Integer.class, Integer.TYPE);
        primitiveTypes.put(Long.class, Long.TYPE);
        primitiveTypes.put(Float.class, Float.TYPE);
        primitiveTypes.put(Double.class, Double.TYPE);
    }

    static {
        wrapperReturnValues.put(Boolean.class, false);
        wrapperReturnValues.put(Character.class, '\u0000');
        wrapperReturnValues.put(Byte.class, (byte) 0);
        wrapperReturnValues.put(Short.class, (short) 0);
        wrapperReturnValues.put(Integer.class, 0);
        wrapperReturnValues.put(Long.class, 0L);
        wrapperReturnValues.put(Float.class, 0F);
        wrapperReturnValues.put(Double.class, 0D);
    }

    static {
        primitiveValues.put(boolean.class, false);
        primitiveValues.put(char.class, '\u0000');
        primitiveValues.put(byte.class, (byte) 0);
        primitiveValues.put(short.class, (short) 0);
        primitiveValues.put(int.class, 0);
        primitiveValues.put(long.class, 0L);
        primitiveValues.put(float.class, 0F);
        primitiveValues.put(double.class, 0);
    }
}