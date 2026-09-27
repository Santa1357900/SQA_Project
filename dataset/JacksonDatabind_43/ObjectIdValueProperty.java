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
package com.fasterxml.jackson.databind.deser.impl;

import java.io.IOException;
import java.lang.annotation.Annotation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;

/**
 * Specialized {@link SettableBeanProperty} implementation used
 * for virtual property that represents Object Id that is used
 * for some POJO types (or properties).
 */
public final class ObjectIdValueProperty
    extends SettableBeanProperty
{
    private static final long serialVersionUID = 1L;

    protected final ObjectIdReader _objectIdReader;

    public ObjectIdValueProperty(ObjectIdReader objectIdReader,
            PropertyMetadata metadata)
    {
        super(objectIdReader.propertyName, objectIdReader.getIdType(), metadata,
                objectIdReader.getDeserializer());
        _objectIdReader = objectIdReader;
    }

    protected ObjectIdValueProperty(ObjectIdValueProperty src, JsonDeserializer<?> deser)
    {
        super(src, deser);
        _objectIdReader = src._objectIdReader;
    }

    protected ObjectIdValueProperty(ObjectIdValueProperty src, PropertyName newName) {
        super(src, newName);
        _objectIdReader = src._objectIdReader;
    }

    @Override
    public ObjectIdValueProperty withName(PropertyName newName) {
        return new ObjectIdValueProperty(this, newName);
    }

    @Override
    public ObjectIdValueProperty withValueDeserializer(JsonDeserializer<?> deser) {
        return new ObjectIdValueProperty(this, deser);
    }
    
    // // // BeanProperty impl
    
    @Override
    public <A extends Annotation> A getAnnotation(Class<A> acls) {
        return null;
    }

    @Override public AnnotatedMember getMember() {  return null; }

    /*
    /**********************************************************
    /* Deserialization methods
    /**********************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object instance) throws IOException
    {
        deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
    		DeserializationContext ctxt, Object instance) throws IOException
    {
        Object id = _valueDeserializer.deserialize(p, ctxt);
        /* 02-Apr-2015, tatu: Actually, as per [databind#742], let it be;
         *  missing or null id is needed for some cases, such as cases where id
         *  will be generated externally, at a later point, and is not available
         *  quite yet. Typical use case is with DB inserts.
         */
        // note: no null checks (unlike usually); deserializer should fail if one found
        if (id == null) {
            return null;
        }
        ReadableObjectId roid = ctxt.findObjectId(id, _objectIdReader.generator, _objectIdReader.resolver);
        roid.bindItem(instance);
        // also: may need to set a property value as well
        SettableBeanProperty idProp = _objectIdReader.idProperty;
        if (idProp != null) {
            return idProp.setAndReturn(instance, id);
        }
        return instance;
    }

    @Override
    public void set(Object instance, Object value) throws IOException {
        setAndReturn(instance, value);
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException
    {
        SettableBeanProperty idProp = _objectIdReader.idProperty;
        if (idProp == null) {
            throw new UnsupportedOperationException(
                    "Should not call set() on ObjectIdProperty that has no SettableBeanProperty");
        }
        return idProp.setAndReturn(instance, value);
    }
}