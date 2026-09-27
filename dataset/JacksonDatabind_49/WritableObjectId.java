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
package com.fasterxml.jackson.databind.ser.impl;

import java.io.IOException;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Simple value container used to keep track of Object Ids during
 * serialization.
 */
public final class WritableObjectId
{
    public final ObjectIdGenerator<?> generator;

    public Object id;

    /**
     * Marker to denote whether Object Id value has been written as part of an Object,
     * to be referencible. Remains false when forward-reference is written.
     */
    protected boolean idWritten = false;

    public WritableObjectId(ObjectIdGenerator<?> generator) {
        this.generator = generator;
    }

    public boolean writeAsId(JsonGenerator gen, SerializerProvider provider, ObjectIdWriter w) throws IOException
    {
        if ((id != null) && (idWritten || w.alwaysAsId)) {
            // 03-Aug-2013, tatu: Prefer Native Object Ids if available
            if (gen.canWriteObjectId()) {
                gen.writeObjectRef(String.valueOf(id));
            } else {
                w.serializer.serialize(id, gen, provider);
            }
            return true;
        }
        return false;
    }
    
    public Object generateId(Object forPojo) {
        // 04-Jun-2016, tatu: As per [databind#1255], need to consider possibility of
        //    id being generated for "alwaysAsId", but not being written as POJO; regardless,
        //    need to use existing id if there is one:
            id = generator.generateId(forPojo);
        return id;
    }

    /**
     * Method called to output Object Id as specified.
     */
    public void writeAsField(JsonGenerator gen, SerializerProvider provider,
            ObjectIdWriter w) throws IOException
    {
        idWritten = true;

        // 03-Aug-2013, tatu: Prefer Native Object Ids if available
        if (gen.canWriteObjectId()) {
            // Need to assume String(ified) ids, for now... could add 'long' variant?
            gen.writeObjectId(String.valueOf(id));
            return;
        }
        
        SerializableString name = w.propertyName;
        if (name != null) {
            gen.writeFieldName(name);
            w.serializer.serialize(id, gen, provider);
        }
    }
}