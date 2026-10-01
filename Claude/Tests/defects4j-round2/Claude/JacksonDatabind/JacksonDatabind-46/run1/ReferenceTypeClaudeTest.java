package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;

public class ReferenceTypeClaudeTest {

    private TypeFactory typeFactory;
    private JavaType stringType;
    private JavaType intType;

    @Before
    public void setUp() throws Throwable {
        typeFactory = new ObjectMapper().getTypeFactory();
        stringType = typeFactory.constructType(String.class);
        intType = typeFactory.constructType(Integer.class);
    }

    // construct(): ผลลัพธ์ต้องไม่เป็น null และ referencedType ต้องตรงกับที่ส่งเข้าไป
    @Test
    public void testConstruct_basic_returnsReferenceTypeWithReferencedType() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertNotNull(rt);
        assertSame(stringType, rt.getReferencedType());
    }

    // construct(): valueHandler/typeHandler ที่ส่งเข้าไปใน construct() ไม่ถูกนำไปใช้ (คงเป็น null เสมอตาม implementation)
    @Test
    public void testConstruct_handlersIgnored_defaultNullHandlers() throws Throwable {
        Object vh = new Object();
        Object th = new Object();
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, vh, th);
        assertNull(rt.<Object>getTypeHandler());
        assertNull(rt.<Object>getValueHandler());
    }

    // isReferenceType(): ต้องเป็น true เสมอ
    @Test
    public void testIsReferenceType_alwaysTrue() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertTrue(rt.isReferenceType());
    }

    // getReferencedType(): ต้องคืน instance เดียวกับที่ส่งเข้าไปตอน construct
    @Test
    public void testGetReferencedType_returnsSameInstance() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, intType, null, null);
        assertSame(intType, rt.getReferencedType());
    }

    // withTypeHandler(): h เท่ากับ typeHandler เดิม (null==null) ต้องคืน this ตัวเดิม
    @Test
    public void testWithTypeHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType result = rt.withTypeHandler(null);
        assertSame(rt, result);
    }

    // withTypeHandler(): h ต่างจาก typeHandler เดิม ต้องคืน instance ใหม่ที่มี typeHandler เป็น h
    @Test
    public void testWithTypeHandler_differentHandler_returnsNewInstanceWithHandler() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        Object handler = new Object();
        ReferenceType result = rt.withTypeHandler(handler);
        assertNotSame(rt, result);
        assertSame(handler, result.<Object>getTypeHandler());
    }

    // withContentTypeHandler(): h เท่ากับ typeHandler ของ referencedType เดิม (null) ต้องคืน this
    @Test
    public void testWithContentTypeHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType result = rt.withContentTypeHandler(null);
        assertSame(rt, result);
    }

    // withContentTypeHandler(): h ต่างออกไป ต้องคืน instance ใหม่ที่ referencedType มี typeHandler เป็น h
    @Test
    public void testWithContentTypeHandler_differentHandler_setsOnReferencedType() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        Object handler = new Object();
        ReferenceType result = rt.withContentTypeHandler(handler);
        assertNotSame(rt, result);
        assertSame(handler, result.getReferencedType().<Object>getTypeHandler());
    }

    // withValueHandler(): h เท่ากับ valueHandler เดิม (null) ต้องคืน this
    @Test
    public void testWithValueHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType result = rt.withValueHandler(null);
        assertSame(rt, result);
    }

    // withValueHandler(): h ต่างจาก valueHandler เดิม ต้องคืน instance ใหม่ที่มี valueHandler เป็น h
    @Test
    public void testWithValueHandler_differentHandler_returnsNewInstanceWithHandler() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        Object handler = new Object();
        ReferenceType result = rt.withValueHandler(handler);
        assertNotSame(rt, result);
        assertSame(handler, result.<Object>getValueHandler());
    }

    // withContentValueHandler(): h เท่ากับ valueHandler ของ referencedType เดิม (null) ต้องคืน this
    @Test
    public void testWithContentValueHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType result = rt.withContentValueHandler(null);
        assertSame(rt, result);
    }

    // withContentValueHandler(): h ต่างออกไป ต้องคืน instance ใหม่ที่ referencedType มี valueHandler เป็น h
    @Test
    public void testWithContentValueHandler_differentHandler_setsOnReferencedType() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        Object handler = new Object();
        ReferenceType result = rt.withContentValueHandler(handler);
        assertNotSame(rt, result);
        assertSame(handler, result.getReferencedType().<Object>getValueHandler());
    }

    // withStaticTyping(): เรียกครั้งแรกจาก non-static ต้องได้ instance ใหม่ (ไม่ใช่ this)
    @Test
    public void testWithStaticTyping_fromNonStatic_returnsNewInstance() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType result = rt.withStaticTyping();
        assertNotSame(rt, result);
    }

    // withStaticTyping(): เรียกซ้ำบน instance ที่ static แล้ว ต้องคืน this ตัวเดิม (idempotent)
    @Test
    public void testWithStaticTyping_alreadyStatic_returnsSameInstance() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType staticOnce = rt.withStaticTyping();
        ReferenceType staticTwice = staticOnce.withStaticTyping();
        assertSame(staticOnce, staticTwice);
    }

    // ล่าบั๊ก: buildCanonicalName ต้องปิดวงเล็บ '<' ด้วย '>' ตามรูปแบบ canonical generic type มาตรฐาน
    @Test
    public void testBuildCanonicalName_shouldCloseAngleBracket() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        String canonical = rt.buildCanonicalName();
        String expected = AtomicReference.class.getName() + "<" + stringType.toCanonical() + ">";
        assertEquals(expected, canonical);
    }

    // toCanonical(): ต้องสอดคล้องกับ buildCanonicalName ที่ถูกต้อง คือปิดวงเล็บ '>' เสมอ
    @Test
    public void testToCanonical_endsWithClosingAngleBracket() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        String canonical = rt.toCanonical();
        assertTrue(canonical.endsWith(">"));
    }

    // _narrow(): ต้องคืน ReferenceType ที่มี raw class ใหม่ แต่ referencedType เดิม
    @Test
    public void testNarrow_changesRawClass_keepsReferencedType() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        JavaType narrowed = rt._narrow(Object.class);
        assertTrue(narrowed instanceof ReferenceType);
        ReferenceType narrowedRt = (ReferenceType) narrowed;
        assertEquals(Object.class, narrowedRt.getParameterSource());
        assertSame(stringType, narrowedRt.getReferencedType());
    }

    // containedTypeCount(): ต้องเป็น 1 เสมอ
    @Test
    public void testContainedTypeCount_alwaysOne() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertEquals(1, rt.containedTypeCount());
    }

    // containedType(0): ต้องคืน referencedType
    @Test
    public void testContainedType_indexZero_returnsReferencedType() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertSame(stringType, rt.containedType(0));
    }

    // containedType(1): index อื่นนอกจาก 0 ต้องคืน null
    @Test
    public void testContainedType_indexOne_returnsNull() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertNull(rt.containedType(1));
    }

    // containedType(-1): index ติดลบต้องคืน null ด้วยเช่นกัน
    @Test
    public void testContainedType_negativeIndex_returnsNull() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertNull(rt.containedType(-1));
    }

    // containedTypeName(0): ต้องคืน "T"
    @Test
    public void testContainedTypeName_indexZero_returnsT() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertEquals("T", rt.containedTypeName(0));
    }

    // containedTypeName(1): index อื่นต้องคืน null
    @Test
    public void testContainedTypeName_indexOne_returnsNull() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertNull(rt.containedTypeName(1));
    }

    // getParameterSource(): ต้องคืน raw class ของ ReferenceType เอง
    @Test
    public void testGetParameterSource_returnsRawClass() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertEquals(AtomicReference.class, rt.getParameterSource());
    }

    // getErasedSignature(): ผลลัพธ์ต้องไม่เป็น null และมีความยาวมากกว่า 0
    @Test
    public void testGetErasedSignature_returnsSameBuilderNonEmpty() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        StringBuilder sb = new StringBuilder();
        StringBuilder result = rt.getErasedSignature(sb);
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    // getGenericSignature(): ต้องมี '<' อยู่ในผลลัพธ์ และจบด้วย ';' ตามโครงสร้างโค้ด
    @Test
    public void testGetGenericSignature_containsAngleBracketAndEndsWithSemicolon() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        StringBuilder sb = new StringBuilder();
        StringBuilder result = rt.getGenericSignature(sb);
        assertNotNull(result);
        String sig = result.toString();
        assertTrue(sig.indexOf('<') >= 0);
        assertTrue(sig.endsWith(";"));
    }

    // toString(): ต้องขึ้นต้นด้วย "[reference type, class " และลงท้ายด้วย "]"
    @Test
    public void testToString_hasExpectedPrefixAndSuffix() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        String s = rt.toString();
        assertTrue(s.startsWith("[reference type, class "));
        assertTrue(s.endsWith("]"));
    }

    // equals(): เปรียบเทียบกับตัวเอง ต้องเป็น true (reflexive)
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertTrue(rt.equals(rt));
    }

    // equals(): เปรียบเทียบกับ null ต้องเป็น false
    @Test
    public void testEquals_null_false() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertFalse(rt.equals(null));
    }

    // equals(): เปรียบเทียบกับ object คนละ class ต้องเป็น false
    @Test
    public void testEquals_differentClass_false() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertFalse(rt.equals(stringType));
    }

    // equals(): raw class ต่างกัน (แม้ referencedType เท่ากัน) ต้องเป็น false
    @Test
    public void testEquals_differentRawClass_false() throws Throwable {
        ReferenceType rt1 = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType rt2 = (ReferenceType) rt1._narrow(Object.class);
        assertFalse(rt1.equals(rt2));
    }

    // equals(): raw class เหมือนกัน แต่ referencedType ต่างกัน ต้องเป็น false
    @Test
    public void testEquals_differentReferencedType_false() throws Throwable {
        ReferenceType rt1 = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType rt2 = ReferenceType.construct(AtomicReference.class, intType, null, null);
        assertFalse(rt1.equals(rt2));
    }

    // equals(): raw class และ referencedType เหมือนกันทุกอย่าง ต้องเป็น true
    @Test
    public void testEquals_sameClassAndReferencedType_true() throws Throwable {
        ReferenceType rt1 = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        ReferenceType rt2 = ReferenceType.construct(AtomicReference.class, stringType, null, null);
        assertTrue(rt1.equals(rt2));
    }
}
