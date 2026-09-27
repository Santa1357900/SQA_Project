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
package com.fasterxml.jackson.core.json;

import com.fasterxml.jackson.core.*;

/**
 * Extension of {@link JsonStreamContext}, which implements
 * core methods needed, and also exposes
 * more complete API to generator implementation classes.
 */
public class JsonWriteContext extends JsonStreamContext
{
    // // // Return values for writeValue()

    public final static int STATUS_OK_AS_IS = 0;
    public final static int STATUS_OK_AFTER_COMMA = 1;
    public final static int STATUS_OK_AFTER_COLON = 2;
    public final static int STATUS_OK_AFTER_SPACE = 3; // in root context
    public final static int STATUS_EXPECT_VALUE = 4;
    public final static int STATUS_EXPECT_NAME = 5;

    /**
     * Parent context for this context; null for root context.
     */
    protected final JsonWriteContext _parent;

    // // // Optional duplicate detection

    protected DupDetector _dups;

    /*
    /**********************************************************
    /* Simple instance reuse slots; speed up things
    /* a bit (10-15%) for docs with lots of small
    /* arrays/objects
    /**********************************************************
     */

    protected JsonWriteContext _child = null;

    /*
    /**********************************************************
    /* Location/state information (minus source reference)
    /**********************************************************
     */
    
    /**
     * Name of the field of which value is to be parsed; only
     * used for OBJECT contexts
     */
    protected String _currentName;

    /**
     * @since 2.5
     */
    protected Object _currentValue;
    
    /**
     * Marker used to indicate that we just received a name, and
     * now expect a value
     */
    protected boolean _gotName;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    protected JsonWriteContext(int type, JsonWriteContext parent, DupDetector dups) {
        super();
        _type = type;
        _parent = parent;
        _dups = dups;
        _index = -1;
    }

    protected JsonWriteContext reset(int type) {
        _type = type;
        _index = -1;
        _currentName = null;
        _gotName = false;
        _currentValue = null;
        if (_dups != null) { _dups.reset(); }
        return this;
    }

    public JsonWriteContext withDupDetector(DupDetector dups) {
        _dups = dups;
        return this;
    }

    @Override
    public Object getCurrentValue() {
        return _currentValue;
    }

    @Override
    public void setCurrentValue(Object v) {
        _currentValue = v;
    }
    
    /*
    /**********************************************************
    /* Factory methods
    /**********************************************************
     */

    /**
     * @deprecated Since 2.3; use method that takes argument
     */
    @Deprecated
    public static JsonWriteContext createRootContext() { return createRootContext(null); }

    public static JsonWriteContext createRootContext(DupDetector dd) {
        return new JsonWriteContext(TYPE_ROOT, null, dd);
    }

    public JsonWriteContext createChildArrayContext() {
        JsonWriteContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new JsonWriteContext(TYPE_ARRAY, this, (_dups == null) ? null : _dups.child());
            return ctxt;
        }
        return ctxt.reset(TYPE_ARRAY);
    }

    public JsonWriteContext createChildObjectContext() {
        JsonWriteContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new JsonWriteContext(TYPE_OBJECT, this, (_dups == null) ? null : _dups.child());
            return ctxt;
        }
        return ctxt.reset(TYPE_OBJECT);
    }

    // // // Shared API

    @Override public final JsonWriteContext getParent() { return _parent; }
    @Override public final String getCurrentName() { return _currentName; }

    public DupDetector getDupDetector() {
        return _dups;
    }
    
    // // // API sub-classes are to implement

    /**
     * Method that writer is to call before it writes a field name.
     *
     * @return Index of the field entry (0-based)
     */
    public int writeFieldName(String name) throws JsonProcessingException {
        if (_gotName) {
            return JsonWriteContext.STATUS_EXPECT_VALUE;
        }
        _gotName = true;
        _currentName = name;
        if (_dups != null) { _checkDup(_dups, name); }
        return (_index < 0) ? STATUS_OK_AS_IS : STATUS_OK_AFTER_COMMA;
    }

    private final void _checkDup(DupDetector dd, String name) throws JsonProcessingException {
        if (dd.isDup(name)) { throw new JsonGenerationException("Duplicate field '"+name+"'"); }
    }
    
    public int writeValue() {
        // Most likely, object:
        if (_type == TYPE_OBJECT) {
            _gotName = false;
            ++_index;
            return STATUS_OK_AFTER_COLON;
        }

        // Ok, array?
        if (_type == TYPE_ARRAY) {
            int ix = _index;
            ++_index;
            return (ix < 0) ? STATUS_OK_AS_IS : STATUS_OK_AFTER_COMMA;
        }
        
        // Nope, root context
        // No commas within root context, but need space
        ++_index;
        return (_index == 0) ? STATUS_OK_AS_IS : STATUS_OK_AFTER_SPACE;
    }

    // // // Internally used abstract methods

    protected void appendDesc(StringBuilder sb) {
        if (_type == TYPE_OBJECT) {
            sb.append('{');
            if (_currentName != null) {
                sb.append('"');
                // !!! TODO: Name chars should be escaped?
                sb.append(_currentName);
                sb.append('"');
            } else {
                sb.append('?');
            }
            sb.append('}');
        } else if (_type == TYPE_ARRAY) {
            sb.append('[');
            sb.append(getCurrentIndex());
            sb.append(']');
        } else {
            // nah, ROOT:
            sb.append("/");
        }
    }

    // // // Overridden standard methods

    /**
     * Overridden to provide developer writeable "JsonPath" representation
     * of the context.
     */
    @Override public String toString() {
        StringBuilder sb = new StringBuilder(64);
        appendDesc(sb);
        return sb.toString();
    }
}