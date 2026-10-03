package com.fasterxml.jackson.databind.jsontype.impl;

import java.io.IOException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.FileHandler;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class SubTypeValidatorClaudeTest {

    private static DeserializationContext capturedContext;

    private ObjectMapper mapper = new ObjectMapper();

    public static class Holder {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class SafePojo {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class AbstractApplicationContext {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    static class ContextCaptureDeserializer extends JsonDeserializer<Holder> {
        @Override
        public Holder deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            capturedContext = ctxt;
            p.skipChildren();
            return new Holder();
        }
    }

    // ดึง DeserializationContext จริงจาก pipeline การ parse เพื่อใช้กับกรณีที่คาดว่าจะ throw
    private DeserializationContext createRealContext() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Holder.class, new ContextCaptureDeserializer());
        m.registerModule(module);
        m.readValue("{}", Holder.class);
        return capturedContext;
    }

    // instance() ต้องคืน object เดียวกันทุกครั้ง (singleton)
    @Test
    public void testInstance_multipleCalls_returnsSameInstance() throws Throwable {
        SubTypeValidator first = SubTypeValidator.instance();
        SubTypeValidator second = SubTypeValidator.instance();
        assertSame(first, second);
    }

    // instance() ต้องไม่คืน null
    @Test
    public void testInstance_returnsNonNullInstance() throws Throwable {
        assertNotNull(SubTypeValidator.instance());
    }

    // คลาส JDK ธรรมดาไม่อยู่ใน blacklist และไม่ใช่ spring -> ไม่ throw
    @Test
    public void testValidateSubType_ordinaryJdkStringClass_doesNotThrow() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(String.class);
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("String.class should be safe: " + e.getMessage());
        }
        assertEquals(String.class, type.getRawClass());
    }

    // POJO ที่กำหนดเองไม่อยู่ใน blacklist -> ไม่ throw
    @Test
    public void testValidateSubType_customSafePojo_doesNotThrow() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(SafePojo.class);
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("SafePojo should be safe: " + e.getMessage());
        }
        assertEquals(SafePojo.class, type.getRawClass());
    }

    // raw.isInterface() == true -> เข้าสาขา if แรก (ไม่ทำอะไร) แล้วไม่ throw
    @Test
    public void testValidateSubType_mapInterface_doesNotThrowAndIsInterface() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(Map.class);
        assertTrue(type.isInterface());
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("interface type should be safe: " + e.getMessage());
        }
        assertTrue(type.isInterface());
    }

    // interface อื่น (Runnable) ก็ต้องไม่ throw เช่นกัน
    @Test
    public void testValidateSubType_runnableInterface_doesNotThrowAndIsInterface() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(Runnable.class);
        assertTrue(type.isInterface());
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("Runnable should be safe: " + e.getMessage());
        }
        assertEquals(Runnable.class, type.getRawClass());
    }

    // คลาส concrete ทั่วไปของ JDK ที่ไม่อยู่ blacklist -> ไม่ throw
    @Test
    public void testValidateSubType_arrayListClass_doesNotThrow() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(ArrayList.class);
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("ArrayList should be safe: " + e.getMessage());
        }
        assertEquals(ArrayList.class, type.getRawClass());
    }

    // Object.class ไม่ใช่ interface ไม่ใช่ spring -> ไม่ throw
    @Test
    public void testValidateSubType_objectClass_doesNotThrow() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(Object.class);
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("Object.class should be safe: " + e.getMessage());
        }
        assertEquals(Object.class, type.getRawClass());
    }

    // array type ไม่ใช่ interface และไม่อยู่ blacklist -> ไม่ throw
    @Test
    public void testValidateSubType_arrayType_doesNotThrowAndIsArrayType() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(int[].class);
        assertTrue(type.isArrayType());
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("int[] should be safe: " + e.getMessage());
        }
        assertEquals(int[].class, type.getRawClass());
    }

    // primitive type ไม่อยู่ blacklist และไม่ใช่ interface -> ไม่ throw
    @Test
    public void testValidateSubType_primitiveIntType_doesNotThrowAndIsPrimitive() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(int.class);
        assertTrue(type.isPrimitive());
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("int should be safe: " + e.getMessage());
        }
        assertEquals(int.class, type.getRawClass());
    }

    // ชื่อ simple name ตรงกับ "AbstractApplicationContext" แต่ package ไม่ใช่ org.springframework -> ไม่ throw
    @Test
    public void testValidateSubType_classNamedLikeSpringButWrongPackage_doesNotThrow() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(AbstractApplicationContext.class);
        assertFalse(type.getRawClass().getName().startsWith("org.springframework."));
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("non-spring class should be safe even with same simple name: " + e.getMessage());
        }
        assertEquals(AbstractApplicationContext.class, type.getRawClass());
    }

    // ชื่อคลาสอยู่ใน DEFAULT_NO_DESER_CLASS_NAMES พอดี -> ต้อง throw JsonMappingException
    @Test
    public void testValidateSubType_illegalFileHandler_throwsJsonMappingException() throws Throwable {
        DeserializationContext ctxt = createRealContext();
        JavaType type = mapper.getTypeFactory().constructType(FileHandler.class);
        try {
            SubTypeValidator.instance().validateSubType(ctxt, type);
            fail("expected JsonMappingException for java.util.logging.FileHandler");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("prevented for security reasons"));
        }
    }

    // ข้อความ exception ต้องมีชื่อคลาสที่ผิดกฎรวมอยู่ด้วย
    @Test
    public void testValidateSubType_illegalFileHandler_messageContainsClassName() throws Throwable {
        DeserializationContext ctxt = createRealContext();
        JavaType type = mapper.getTypeFactory().constructType(FileHandler.class);
        try {
            SubTypeValidator.instance().validateSubType(ctxt, type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("java.util.logging.FileHandler"));
        }
    }

    // อีกหนึ่ง entry ของ blacklist ที่เป็น JDK class -> ต้อง throw
    @Test
    public void testValidateSubType_illegalUnicastRemoteObject_throwsJsonMappingException() throws Throwable {
        DeserializationContext ctxt = createRealContext();
        JavaType type = mapper.getTypeFactory().constructType(UnicastRemoteObject.class);
        try {
            SubTypeValidator.instance().validateSubType(ctxt, type);
            fail("expected JsonMappingException for java.rmi.server.UnicastRemoteObject");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("prevented for security reasons"));
        }
    }

    // ข้อความ exception ต้องมีชื่อคลาส UnicastRemoteObject รวมอยู่ด้วย
    @Test
    public void testValidateSubType_illegalUnicastRemoteObject_messageContainsClassName() throws Throwable {
        DeserializationContext ctxt = createRealContext();
        JavaType type = mapper.getTypeFactory().constructType(UnicastRemoteObject.class);
        try {
            SubTypeValidator.instance().validateSubType(ctxt, type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("java.rmi.server.UnicastRemoteObject"));
        }
    }

    // เรียกซ้ำสองครั้งด้วยชนิดต้องห้ามเดิม ต้อง throw ทั้งสองครั้ง (ไม่มี state ค้าง)
    @Test
    public void testValidateSubType_illegalType_calledTwice_bothThrow() throws Throwable {
        DeserializationContext ctxt = createRealContext();
        JavaType type = mapper.getTypeFactory().constructType(UnicastRemoteObject.class);
        int exceptionCount = 0;
        for (int i = 0; i < 2; i++) {
            try {
                SubTypeValidator.instance().validateSubType(ctxt, type);
            } catch (JsonMappingException e) {
                exceptionCount++;
            }
        }
        assertEquals(2, exceptionCount);
    }

    // เรียกด้วยชนิดผิดกฎก่อน แล้วตามด้วยชนิดปลอดภัย ต้องไม่มี state รั่วไหลระหว่างกัน
    @Test
    public void testValidateSubType_safeCallAfterIllegalCall_stillSafe() throws Throwable {
        DeserializationContext ctxt = createRealContext();
        JavaType illegal = mapper.getTypeFactory().constructType(FileHandler.class);
        JavaType safe = mapper.getTypeFactory().constructType(SafePojo.class);
        try {
            SubTypeValidator.instance().validateSubType(ctxt, illegal);
            fail("expected exception for illegal type");
        } catch (JsonMappingException e) {
            // expected
        }
        try {
            SubTypeValidator.instance().validateSubType(ctxt, safe);
        } catch (JsonMappingException e) {
            fail("safe pojo should not throw: " + e.getMessage());
        }
        assertEquals(SafePojo.class, safe.getRawClass());
    }

    // เรียกด้วยชนิดปลอดภัยก่อน แล้วตามด้วยชนิดผิดกฎ ยังต้อง throw ปกติ
    @Test
    public void testValidateSubType_illegalCallAfterSafeCall_stillThrows() throws Throwable {
        DeserializationContext ctxt = createRealContext();
        JavaType safe = mapper.getTypeFactory().constructType(SafePojo.class);
        JavaType illegal = mapper.getTypeFactory().constructType(UnicastRemoteObject.class);
        try {
            SubTypeValidator.instance().validateSubType(ctxt, safe);
        } catch (JsonMappingException e) {
            fail("safe pojo should not throw: " + e.getMessage());
        }
        try {
            SubTypeValidator.instance().validateSubType(ctxt, illegal);
            fail("expected exception for illegal type");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("prevented for security reasons"));
        }
    }

    // ตรวจสอบว่าคลาสช่วยจับ context (Holder) เองก็ไม่อยู่ใน blacklist
    @Test
    public void testValidateSubType_holderHelperClass_doesNotThrow() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(Holder.class);
        try {
            SubTypeValidator.instance().validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("Holder should be safe: " + e.getMessage());
        }
        assertEquals(Holder.class, type.getRawClass());
    }
}
