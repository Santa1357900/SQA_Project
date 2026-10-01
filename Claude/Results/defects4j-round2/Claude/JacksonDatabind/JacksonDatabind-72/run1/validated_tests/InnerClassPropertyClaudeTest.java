package com.fasterxml.jackson.databind.deser.impl;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public class InnerClassPropertyClaudeTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    public static class Outer {
        private String outerName;
        private Inner inner;

        public String getOuterName() { return outerName; }
        public void setOuterName(String outerName) { this.outerName = outerName; }
        public Inner getInner() { return inner; }
        public void setInner(Inner inner) { this.inner = inner; }

        public class Inner {
            private String name;
            private int value;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public int getValue() { return value; }
            public void setValue(int value) { this.value = value; }

            public boolean isSameOuterAs(Outer o) {
                return Outer.this == o;
            }
        }
    }

    public static class PolyOuter {
        private PolyInner inner;

        public PolyInner getInner() { return inner; }
        public void setInner(PolyInner inner) { this.inner = inner; }

        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
        public class PolyInner {
            private String name;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public boolean isSameOuterAs(PolyOuter o) {
                return PolyOuter.this == o;
            }
        }
    }

    // สาขา "usual case": token ไม่ใช่ null, ไม่มี typeDeserializer -> สร้าง instance ด้วย _creator แล้ว deserialize ค่าเข้าไป
    @Test
    public void testDeserializeAndSet_basicInnerClass_populatesNameAndValue() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"name\":\"foo\",\"value\":5}}", Outer.class);
        assertNotNull(result.getInner());
        assertEquals("foo", result.getInner().getName());
        assertEquals(5, result.getInner().getValue());
    }

    // สาขา t == VALUE_NULL -> value = _valueDeserializer.getNullValue(ctxt) ควรเป็น null
    @Test
    public void testDeserializeAndSet_nullToken_setsNullValue() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":null}", Outer.class);
        assertNull(result.getInner());
    }

    // ไม่มี property "inner" เลย -> field ยังเป็น null ตามค่าเริ่มต้น ไม่เข้า deserializeAndSet
    @Test
    public void testDeserializeAndSet_missingProperty_defaultsToNull() throws Throwable {
        Outer result = mapper.readValue("{}", Outer.class);
        assertNull(result.getInner());
    }

    // JSON object ว่าง -> instance ถูกสร้างแต่ field ภายในยังเป็นค่า default (0 / null)
    @Test
    public void testDeserializeAndSet_emptyInnerObject_defaultFieldValues() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{}}", Outer.class);
        assertNotNull(result.getInner());
        assertNull(result.getInner().getName());
        assertEquals(0, result.getInner().getValue());
    }

    // ค่าขอบ: -1
    @Test
    public void testDeserializeAndSet_negativeIntValue() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"value\":-1}}", Outer.class);
        assertEquals(-1, result.getInner().getValue());
    }

    // ค่าขอบ: Integer.MAX_VALUE
    @Test
    public void testDeserializeAndSet_maxIntValue() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"value\":2147483647}}", Outer.class);
        assertEquals(2147483647, result.getInner().getValue());
    }

    // ค่าขอบ: Integer.MIN_VALUE
    @Test
    public void testDeserializeAndSet_minIntValue() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"value\":-2147483648}}", Outer.class);
        assertEquals(-2147483648, result.getInner().getValue());
    }

    // ค่าขอบ: 0
    @Test
    public void testDeserializeAndSet_zeroIntValue() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"value\":0}}", Outer.class);
        assertEquals(0, result.getInner().getValue());
    }

    // Unicode string ใน property ของ inner class
    @Test
    public void testDeserializeAndSet_unicodeName() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"name\":\"\u65e5\u672c\u8a9e\"}}", Outer.class);
        assertEquals("\u65e5\u672c\u8a9e", result.getInner().getName());
    }

    // empty string
    @Test
    public void testDeserializeAndSet_emptyStringName() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"name\":\"\"}}", Outer.class);
        assertEquals("", result.getInner().getName());
    }

    // whitespace only string
    @Test
    public void testDeserializeAndSet_whitespaceOnlyName() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"name\":\" \"}}", Outer.class);
        assertEquals(" ", result.getInner().getName());
    }

    // escape sequence (newline) ต้องถูกเก็บตามค่าจริง
    @Test
    public void testDeserializeAndSet_nameWithEscapedNewline() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"name\":\"line1\\nline2\"}}", Outer.class);
        assertEquals("line1\nline2", result.getInner().getName());
    }

    // มีเฉพาะ value field -> name ยังคง default null
    @Test
    public void testDeserializeAndSet_innerObjectWithOnlyValueField_nameStaysNull() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"value\":9}}", Outer.class);
        assertNull(result.getInner().getName());
        assertEquals(9, result.getInner().getValue());
    }

    // มีเฉพาะ name field -> value ยังคง default 0
    @Test
    public void testDeserializeAndSet_innerObjectWithOnlyNameField_valueStaysZero() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"name\":\"onlyName\"}}", Outer.class);
        assertEquals("onlyName", result.getInner().getName());
        assertEquals(0, result.getInner().getValue());
    }

    // สอง instance ต่างกัน ต้องมี state ที่เป็นอิสระต่อกัน (ไม่มีการแชร์ field)
    @Test
    public void testDeserializeAndSet_multipleInnerInstances_independentState() throws Throwable {
        Outer first = mapper.readValue("{\"inner\":{\"name\":\"A\",\"value\":1}}", Outer.class);
        Outer second = mapper.readValue("{\"inner\":{\"name\":\"B\",\"value\":2}}", Outer.class);
        assertEquals("A", first.getInner().getName());
        assertEquals("B", second.getInner().getName());
        assertEquals(1, first.getInner().getValue());
        assertEquals(2, second.getInner().getValue());
    }

    // ทั้ง outerName field และ inner field ของ outer เดียวกันถูกตั้งค่าครบ
    @Test
    public void testDeserializeAndSet_outerNameAndInnerBothPopulated() throws Throwable {
        Outer result = mapper.readValue(
                "{\"outerName\":\"myOuter\",\"inner\":{\"name\":\"nested\",\"value\":3}}", Outer.class);
        assertEquals("myOuter", result.getOuterName());
        assertEquals("nested", result.getInner().getName());
        assertEquals(3, result.getInner().getValue());
    }

    // ตรวจสัญญาหลักของ InnerClassProperty: instance ของ inner class ต้องอ้างถึง bean (outer) ที่ถูก deserialize อยู่จริง
    @Test
    public void testDeserializeAndSet_innerHasCorrectOuterReference() throws Throwable {
        Outer result = mapper.readValue("{\"inner\":{\"name\":\"x\"}}", Outer.class);
        assertTrue(result.getInner().isSameOuterAs(result));
    }

    // deserialize สอง instance แยกกัน -> outer reference ของแต่ละ inner ต้องตรงกับ instance ของตนเองเท่านั้น
    @Test
    public void testDeserializeAndSet_twoSeparateOuterInstances_haveDistinctInnerOuterRefs() throws Throwable {
        Outer first = mapper.readValue("{\"inner\":{\"name\":\"first\"}}", Outer.class);
        Outer second = mapper.readValue("{\"inner\":{\"name\":\"second\"}}", Outer.class);
        assertTrue(first.getInner().isSameOuterAs(first));
        assertFalse(first.getInner().isSameOuterAs(second));
        assertTrue(second.getInner().isSameOuterAs(second));
        assertFalse(second.getInner().isSameOuterAs(first));
    }



    // สาขา t == VALUE_NULL ยังต้องถูกตรวจก่อนเสมอ แม้ property จะมี typeDeserializer
    @Test
    public void testDeserializeAndSet_polymorphicInnerClass_nullValue() throws Throwable {
        PolyOuter result = mapper.readValue("{\"inner\":null}", PolyOuter.class);
        assertNull(result.getInner());
    }

    // unknown property ภายใน inner object -> ควร throw JsonMappingException (ค่า default FAIL_ON_UNKNOWN_PROPERTIES=true)
    @Test
    public void testDeserializeAndSet_unknownProperty_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("{\"inner\":{\"name\":\"x\",\"extraField\":\"y\"}}", Outer.class);
            fail("expected JsonMappingException for unknown property");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().contains("extraField"));
        }
    }

    // เรียก deserialize ซ้ำสองครั้งด้วย JSON เดียวกัน -> ได้ instance คนละตัว แต่ค่าถูกต้องเท่ากันทุกครั้ง
    @Test
    public void testDeserializeAndSet_repeatedDeserialization_producesIndependentInstances() throws Throwable {
        String json = "{\"inner\":{\"name\":\"same\",\"value\":7}}";
        Outer first = mapper.readValue(json, Outer.class);
        Outer second = mapper.readValue(json, Outer.class);
        assertNotSame(first, second);
        assertNotSame(first.getInner(), second.getInner());
        assertEquals(first.getInner().getName(), second.getInner().getName());
        assertEquals(first.getInner().getValue(), second.getInner().getValue());
    }
}
