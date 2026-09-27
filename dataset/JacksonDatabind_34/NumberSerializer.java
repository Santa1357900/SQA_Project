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
package com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

/**
 * As a fallback, we may need to use this serializer for other
 * types of {@link Number}s: both custom types and "big" numbers
 * like {@link BigInteger} and {@link BigDecimal}.
 */
@JacksonStdImpl
@SuppressWarnings("serial")
public class NumberSerializer
    extends StdScalarSerializer<Number>
{
    /**
     * Static instance that is only to be used for {@link java.lang.Number}.
     */
    public final static NumberSerializer instance = new NumberSerializer(Number.class);

    protected final boolean _isInt;

    /**
     * @since 2.5
     */
    public NumberSerializer(Class<? extends Number> rawType) {
        super(rawType, false);
        // since this will NOT be constructed for Integer or Long, only case is:
        _isInt = (rawType == BigInteger.class);
    }

    @Override
    public void serialize(Number value, JsonGenerator g, SerializerProvider provider) throws IOException
    {
        // should mostly come in as one of these two:
        if (value instanceof BigDecimal) {
            g.writeNumber((BigDecimal) value);
        } else if (value instanceof BigInteger) {
            g.writeNumber((BigInteger) value);
            
        /* These shouldn't match (as there are more specific ones),
         * but just to be sure:
         */
        } else if (value instanceof Integer) {
            g.writeNumber(value.intValue());
        } else if (value instanceof Long) {
            g.writeNumber(value.longValue());
        } else if (value instanceof Double) {
            g.writeNumber(value.doubleValue());
        } else if (value instanceof Float) {
            g.writeNumber(value.floatValue());
        } else if ((value instanceof Byte) || (value instanceof Short)) {
            g.writeNumber(value.intValue()); // doesn't need to be cast to smaller numbers
        } else {
            // We'll have to use fallback "untyped" number write method
            g.writeNumber(value.toString());
        }
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint) {
        return createSchemaNode(_isInt ? "integer" : "number", true);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException
    {
        if (_isInt) {
            visitIntFormat(visitor, typeHint, JsonParser.NumberType.BIG_INTEGER);
        } else {
            Class<?> h = handledType();
            if (h == BigDecimal.class) {
                visitFloatFormat(visitor, typeHint, JsonParser.NumberType.BIG_INTEGER);
            } else {
                // otherwise bit unclear what to call... but let's try:
                /*JsonNumberFormatVisitor v2 =*/ visitor.expectNumberFormat(typeHint);
            }
        }
    }
}