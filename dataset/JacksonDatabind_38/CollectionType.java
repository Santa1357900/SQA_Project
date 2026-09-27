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
package com.fasterxml.jackson.databind.type;


import com.fasterxml.jackson.databind.JavaType;

/**
 * Type that represents Java Collection types (Lists, Sets).
 */
public final class CollectionType
    extends CollectionLikeType
{
    private static final long serialVersionUID = 1L;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    private CollectionType(Class<?> collT, TypeBindings bindings,
            JavaType superClass, JavaType[] superInts, JavaType elemT,
            Object valueHandler, Object typeHandler, boolean asStatic)
    {
        super(collT, bindings, superClass, superInts, elemT, valueHandler, typeHandler, asStatic);
    }

    /**
     * @since 2.7
     */
    protected CollectionType(TypeBase base, JavaType elemT) {
        super(base, elemT);
    }

    /**
     * @since 2.7
     */
    public static CollectionType construct(Class<?> rawType, TypeBindings bindings,
            JavaType superClass, JavaType[] superInts, JavaType elemT) {
        return new CollectionType(rawType, bindings, superClass, superInts, elemT,
                null, null, false);
    }

    /**
     * @deprecated Since 2.7, remove from 2.8
     */
    @Deprecated // since 2.7
    public static CollectionType construct(Class<?> rawType, JavaType elemT) {
        // First: may need to fabricate TypeBindings (needed for refining into
        // concrete collection types, as per [databind#1102])
        return new CollectionType(rawType, null,
                // !!! TODO: Wrong, does have supertypes, but:
                _bogusSuperClass(rawType), null, elemT,
                null, null, false);
    }

    @Deprecated // since 2.7
    @Override
    protected JavaType _narrow(Class<?> subclass) {
        return new CollectionType(subclass, _bindings,
                _superClass, _superInterfaces, _elementType, null, null, _asStatic);
    }

    @Override
    public JavaType withContentType(JavaType contentType) {
        if (_elementType == contentType) {
            return this;
        }
        return new CollectionType(_class, _bindings, _superClass, _superInterfaces,
                contentType, _valueHandler, _typeHandler, _asStatic);
    }
    
    @Override
    public CollectionType withTypeHandler(Object h) {
        return new CollectionType(_class, _bindings,
                _superClass, _superInterfaces, _elementType, _valueHandler, h, _asStatic);
    }

    @Override
    public CollectionType withContentTypeHandler(Object h)
    {
        return new CollectionType(_class, _bindings,
                _superClass, _superInterfaces, _elementType.withTypeHandler(h),
                _valueHandler, _typeHandler, _asStatic);
    }

    @Override
    public CollectionType withValueHandler(Object h) {
        return new CollectionType(_class, _bindings,
                _superClass, _superInterfaces, _elementType, h, _typeHandler, _asStatic);
    }

    @Override
    public  CollectionType withContentValueHandler(Object h) {
        return new CollectionType(_class, _bindings,
                _superClass, _superInterfaces, _elementType.withValueHandler(h),
                _valueHandler, _typeHandler, _asStatic);
    }

    @Override
    public CollectionType withStaticTyping() {
        if (_asStatic) {
            return this;
        }
        return new CollectionType(_class, _bindings,
                _superClass, _superInterfaces, _elementType.withStaticTyping(),
                _valueHandler, _typeHandler, true);
    }

    @Override
    public JavaType refine(Class<?> rawType, TypeBindings bindings,
            JavaType superClass, JavaType[] superInterfaces) {
        return new CollectionType(rawType, bindings,
                superClass, superInterfaces, _elementType,
                _valueHandler, _typeHandler, _asStatic);
    }

    /*
    /**********************************************************
    /* Standard methods
    /**********************************************************
     */

    @Override
    public String toString()
    {
        return "[collection type; class "+_class.getName()+", contains "+_elementType+"]";
    }
}