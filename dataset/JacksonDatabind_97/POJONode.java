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
package com.fasterxml.jackson.databind.node;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.RawValue;

/**
 * Value node that contains a wrapped POJO, to be serialized as
 * a JSON constructed through data mapping (usually done by
 * calling {@link com.fasterxml.jackson.databind.ObjectMapper}).
 */
public class POJONode
    extends ValueNode
{
    protected final Object _value;

    public POJONode(Object v) { _value = v; }

    /*
    /**********************************************************
    /* Base class overrides
    /**********************************************************
     */

    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.POJO;
    }

    @Override public JsonToken asToken() { return JsonToken.VALUE_EMBEDDED_OBJECT; }

    /**
     * As it is possible that some implementations embed byte[] as POJONode
     * (despite optimal being {@link BinaryNode}), let's add support for exposing
     * binary data here too.
     */
    @Override
    public byte[] binaryValue() throws IOException
    {
        if (_value instanceof byte[]) {
            return (byte[]) _value;
        }
        return super.binaryValue();
    }
    
    /* 
    /**********************************************************
    /* General type coercions
    /**********************************************************
     */

    @Override
    public String asText() { return (_value == null) ? "null" : _value.toString(); }

    @Override public String asText(String defaultValue) {
        return (_value == null) ? defaultValue : _value.toString();
    }
    
    @Override
    public boolean asBoolean(boolean defaultValue)
    {
        if (_value != null && _value instanceof Boolean) {
            return ((Boolean) _value).booleanValue();
        }
        return defaultValue;
    }
    
    @Override
    public int asInt(int defaultValue)
    {
        if (_value instanceof Number) {
            return ((Number) _value).intValue();
        }
        return defaultValue;
    }

    @Override
    public long asLong(long defaultValue)
    {
        if (_value instanceof Number) {
            return ((Number) _value).longValue();
        }
        return defaultValue;
    }
    
    @Override
    public double asDouble(double defaultValue)
    {
        if (_value instanceof Number) {
            return ((Number) _value).doubleValue();
        }
        return defaultValue;
    }
    
    /*
    /**********************************************************
    /* Public API, serialization
    /**********************************************************
     */

    @Override
    public final void serialize(JsonGenerator gen, SerializerProvider ctxt) throws IOException
    {
        if (_value == null) {
            ctxt.defaultSerializeNull(gen);
        } else if (_value instanceof JsonSerializable) {
            ((JsonSerializable) _value).serialize(gen, ctxt);
        } else {
            // 25-May-2018, tatu: [databind#1991] do not call via generator but through context;
            //    this to preserve contextual information
            gen.writeObject(_value);
        }
    }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    /**
     * Method that can be used to access the POJO this node wraps.
     */
    public Object getPojo() { return _value; }

    /*
    /**********************************************************
    /* Overridden standard methods
    /**********************************************************
     */

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null) return false;
        if (o instanceof POJONode) {
            return _pojoEquals((POJONode) o);
        }
        return false;
    }

    /**
     * @since 2.3
     */
    protected boolean _pojoEquals(POJONode other)
    {
        if (_value == null) {
            return other._value == null;
        }
        return _value.equals(other._value);
    }
    
    @Override
    public int hashCode() { return _value.hashCode(); }

    @Override
    public String toString()
    {
        // [databind#743]: Let's try indicating content type, for debugging purposes
        if (_value instanceof byte[]) {
            return String.format("(binary value of %d bytes)", ((byte[]) _value).length);
        }
        if (_value instanceof RawValue) {
            return String.format("(raw value '%s')", ((RawValue) _value).toString());
        }
        return String.valueOf(_value);
    }
}