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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.*;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.util.StdDateFormat;

@SuppressWarnings("serial")
public abstract class DateTimeSerializerBase<T>
    extends StdScalarSerializer<T>
    implements ContextualSerializer
{
    /**
     * Flag that indicates that serialization must be done as the
     * Java timestamp, regardless of other settings.
     */
    protected final Boolean _useTimestamp;

    /**
     * Specific format to use, if not default format: non null value
     * also indicates that serialization is to be done as JSON String,
     * not numeric timestamp, unless {@link #_useTimestamp} is true.
     */
    protected final DateFormat _customFormat;

    protected DateTimeSerializerBase(Class<T> type,
            Boolean useTimestamp, DateFormat customFormat)
    {
        super(type);
        _useTimestamp = useTimestamp;
        _customFormat = customFormat;
    }

    public abstract DateTimeSerializerBase<T> withFormat(Boolean timestamp, DateFormat customFormat);

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider serializers,
            BeanProperty property) throws JsonMappingException
    {
        if (property == null) {
            return this;
        }
        JsonFormat.Value format = findFormatOverrides(serializers, property, handledType());
        if (format == null) {
            return this;
        }
        // Simple case first: serialize as numeric timestamp?
        JsonFormat.Shape shape = format.getShape();
        if (shape.isNumeric()) {
            return withFormat(Boolean.TRUE, null);
        }

        // 08-Jun-2017, tatu: With [databind#1648], this gets bit tricky..
        // First: custom pattern will override things
                if ((shape == JsonFormat.Shape.STRING) || format.hasPattern()
                                || format.hasLocale() || format.hasTimeZone()) {
                    TimeZone tz = format.getTimeZone();
                    final String pattern = format.hasPattern()
                                    ? format.getPattern()
                                    : StdDateFormat.DATE_FORMAT_STR_ISO8601;
            final Locale loc = format.hasLocale()
                            ? format.getLocale()
                            : serializers.getLocale();
                    SimpleDateFormat df = new SimpleDateFormat(pattern, loc);
                    if (tz == null) {
                        tz = serializers.getTimeZone();
                    }
            df.setTimeZone(tz);
            return withFormat(Boolean.FALSE, df);
        }

        // Otherwise, need one of these changes:


        // Jackson's own `StdDateFormat` is quite easy to deal with...

        // 08-Jun-2017, tatu: Unfortunately there's no generally usable
        //    mechanism for changing `DateFormat` instances (or even clone()ing)
        //    So: require it be `SimpleDateFormat`; can't config other types
//            serializers.reportBadDefinition(handledType(), String.format(
            // Ugh. No way to change `Locale`, create copy; must re-crete completely:
        return this;
    }

    /*
    /**********************************************************
    /* Accessors
    /**********************************************************
     */

    @Deprecated
    @Override
    public boolean isEmpty(T value) {
        // let's assume "null date" (timestamp 0) qualifies for empty
        return (value == null) || (_timestamp(value) == 0L);
    }

    @Override
    public boolean isEmpty(SerializerProvider serializers, T value) {
        // let's assume "null date" (timestamp 0) qualifies for empty
        return (value == null) || (_timestamp(value) == 0L);
    }
    
    protected abstract long _timestamp(T value);
    
    @Override
    public JsonNode getSchema(SerializerProvider serializers, Type typeHint) {
        //todo: (ryan) add a format for the date in the schema?
        return createSchemaNode(_asTimestamp(serializers) ? "number" : "string", true);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException
    {
        _acceptJsonFormatVisitor(visitor, typeHint, _asTimestamp(visitor.getProvider()));
    }

    /*
    /**********************************************************
    /* Actual serialization
    /**********************************************************
     */

    @Override
    public abstract void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException;

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    protected boolean _asTimestamp(SerializerProvider serializers)
    {
        if (_useTimestamp != null) {
            return _useTimestamp.booleanValue();
        }
        if (_customFormat == null) {
            if (serializers != null) {
                return serializers.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
            // 12-Jun-2014, tatu: Is it legal not to have provider? Was NPE:ing earlier so leave a check
            throw new IllegalArgumentException("Null SerializerProvider passed for "+handledType().getName());
        }
        return false;
    }

    protected void _acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint,
		boolean asNumber) throws JsonMappingException
    {
        if (asNumber) {
            visitIntFormat(visitor, typeHint,
                    JsonParser.NumberType.LONG, JsonValueFormat.UTC_MILLISEC);
        } else {
            visitStringFormat(visitor, typeHint, JsonValueFormat.DATE_TIME);
        }
    }
}