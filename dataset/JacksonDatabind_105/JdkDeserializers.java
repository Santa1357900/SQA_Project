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
package com.fasterxml.jackson.databind.deser.std;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.*;

/**
 * Container class that contains serializers for JDK types that
 * require special handling for some reason.
 */
public class JdkDeserializers
{
    private final static HashSet<String> _classNames = new HashSet<String>();
    static {
        // note: can skip primitive types; other ways to check them:
        Class<?>[] types = new Class<?>[] {
                UUID.class,
                AtomicBoolean.class,
                StackTraceElement.class,
                ByteBuffer.class
        };
        for (Class<?> cls : types) { _classNames.add(cls.getName()); }
        for (Class<?> cls : FromStringDeserializer.types()) { _classNames.add(cls.getName()); }
    }

    public static JsonDeserializer<?> find(Class<?> rawType, String clsName)
    {
        if (_classNames.contains(clsName)) {
            JsonDeserializer<?> d = FromStringDeserializer.findDeserializer(rawType);
            if (d != null) {
                return d;
            }
            if (rawType == UUID.class) {
                return new UUIDDeserializer();
            }
            if (rawType == StackTraceElement.class) {
                return new StackTraceElementDeserializer();
            }
            if (rawType == AtomicBoolean.class) {
                // (note: AtomicInteger/Long work due to single-arg constructor. For now?
                return new AtomicBooleanDeserializer();
            }
            if (rawType == ByteBuffer.class) {
                return new ByteBufferDeserializer();
            }
        }
        return null;
    }
}