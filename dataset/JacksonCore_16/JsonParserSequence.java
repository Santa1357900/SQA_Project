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
package com.fasterxml.jackson.core.util;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.*;

/**
 * Helper class that can be used to sequence multiple physical
 * {@link JsonParser}s to create a single logical sequence of
 * tokens, as a single {@link JsonParser}.
 *<p>
 * Fairly simple use of {@link JsonParserDelegate}: only need
 * to override {@link #nextToken} to handle transition
 */
public class JsonParserSequence extends JsonParserDelegate
{
    /**
     * Parsers other than the first one (which is initially assigned
     * as delegate)
     */
    protected final JsonParser[] _parsers;
    
    /**
     * Index of the next parser in {@link #_parsers}.
     */
    protected int _nextParser;

    /**
     * Flag used to indicate that `JsonParser.nextToken()` should not be called,
     * due to parser already pointing to a token.
     *
     * @since 2.8
     */
    
    /*
     *******************************************************
     * Construction
     *******************************************************
     */

    protected JsonParserSequence(JsonParser[] parsers)
    {
        super(parsers[0]);
        _parsers = parsers;
        _nextParser = 1;
    }

    /**
     * Method that will construct a parser (possibly a sequence) that
     * contains all given sub-parsers.
     * All parsers given are checked to see if they are sequences: and
     * if so, they will be "flattened", that is, contained parsers are
     * directly added in a new sequence instead of adding sequences
     * within sequences. This is done to minimize delegation depth,
     * ideally only having just a single level of delegation.
     */
    public static JsonParserSequence createFlattened(JsonParser first, JsonParser second)
    {
        if (!(first instanceof JsonParserSequence || second instanceof JsonParserSequence)) {
            // simple:
            return new JsonParserSequence(new JsonParser[] { first, second });
        }
        ArrayList<JsonParser> p = new ArrayList<JsonParser>();
        if (first instanceof JsonParserSequence) {
            ((JsonParserSequence) first).addFlattenedActiveParsers(p);
        } else {
            p.add(first);
        }
        if (second instanceof JsonParserSequence) {
            ((JsonParserSequence) second).addFlattenedActiveParsers(p);
        } else {
            p.add(second);
        }
        return new JsonParserSequence(p.toArray(new JsonParser[p.size()]));
    }

    @SuppressWarnings("resource")
    protected void addFlattenedActiveParsers(List<JsonParser> result)
    {
        for (int i = _nextParser-1, len = _parsers.length; i < len; ++i) {
            JsonParser p = _parsers[i];
            if (p instanceof JsonParserSequence) {
                ((JsonParserSequence) p).addFlattenedActiveParsers(result);
            } else {
                result.add(p);
            }
        }
    }
    
    /*
     *******************************************************
     * Overridden methods, needed: cases where default
     * delegation does not work
     *******************************************************
     */
    
    @Override
    public void close() throws IOException {
        do { delegate.close(); } while (switchToNext());
    }

    @Override
    public JsonToken nextToken() throws IOException, JsonParseException
    {
        JsonToken t = delegate.nextToken();
        if (t != null) return t;
        while (switchToNext()) {
            t = delegate.nextToken();
            if (t != null) return t;
        }
        return null;
    }

    /*
    /*******************************************************
    /* Additional extended API
    /*******************************************************
     */

    /**
     * Method that is most useful for debugging or testing;
     * returns actual number of underlying parsers sequence
     * was constructed with (nor just ones remaining active)
     */
    public int containedParsersCount() {
        return _parsers.length;
    }
    
    /*
    /*******************************************************
    /* Helper methods
    /*******************************************************
     */

    /**
     * Method that will switch active parser from the current one
     * to next parser in sequence, if there is another parser left,
     * making this the new delegate. Old delegate is returned if
     * switch succeeds.
     * 
     * @return True if switch succeeded; false otherwise
     */
    protected boolean switchToNext()
    {
        if (_nextParser >= _parsers.length) {
            return false;
        }
        delegate = _parsers[_nextParser++];
        return true;
    }
}