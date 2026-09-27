package com.fasterxml.jackson.databind.ser.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class WritableObjectIdTest {

    private static class DummyObjectIdGenerator extends ObjectIdGenerator<Object> {
        private final Object idToReturn;

        public DummyObjectIdGenerator(Object idToReturn) {
            this.idToReturn = idToReturn;
        }

        @Override
        public boolean canUseFor(ObjectIdGenerator<?> gen) {
            return gen.getClass() == getClass();
        }

        @Override
        public ObjectIdGenerator<Object> forScope(Class<?> scope) {
            return this;
        }

        @Override
        public ObjectIdGenerator<Object> newForSerialization(Object context) {
            return this;
        }

        @Override
        public Object generateId(Object forPojo) {
            return idToReturn;
        }

        @Override
        public com.fasterxml.jackson.annotation.ObjectIdGenerator.IdKey key(Object key) {
            return new IdKey(getClass(), null, key);
        }
    }

    private static class DummyJsonGenerator extends com.fasterxml.jackson.core.base.GeneratorBase {
        public boolean canWriteObjectIdCalled = false;
        public boolean writeObjectRefCalled = false;
        public boolean writeObjectIdCalled = false;
        public boolean writeFieldNameCalled = false;
        private final boolean nativeObjectIdSupported;

        public DummyJsonGenerator(boolean nativeObjectIdSupported) {
            super(0, null);
            this.nativeObjectIdSupported = nativeObjectIdSupported;
        }

        @Override
        public boolean canWriteObjectId() {
            canWriteObjectIdCalled = true;
            return nativeObjectIdSupported;
        }

        @Override
        public void writeObjectRef(Object ref) throws IOException {
            writeObjectRefCalled = true;
        }

        @Override
        public void writeObjectId(Object id) throws IOException {
            writeObjectIdCalled = true;
        }

        @Override
        public void writeFieldName(String name) throws IOException {
            writeFieldNameCalled = true;
        }

        @Override
        public void writeFieldName(SerializableString name) throws IOException {
            writeFieldNameCalled = true;
        }

        @Override public void flush() throws IOException {}
        @Override protected void _releaseBuffers() {}
        @Override protected void _verifyValueWrite(String typeMsg) throws IOException {}
        @Override public void writeStartArray() throws IOException {}
        @Override public void writeEndArray() throws IOException {}
        @Override public void writeStartObject() throws IOException {}
        @Override public void writeEndObject() throws IOException {}
        @Override public void writeString(String text) throws IOException {}
        @Override public void writeString(char[] buffer, int offset, int len) throws IOException {}
        @Override public void writeRawUTF8String(byte[] buffer, int offset, int len) throws IOException {}
        @Override public void writeUTF8String(byte[] buffer, int offset, int len) throws IOException {}
        @Override public void writeRaw(String text) throws IOException {}
        @Override public void writeRaw(String text, int offset, int len) throws IOException {}
        @Override public void writeRaw(char[] text, int offset, int len) throws IOException {}
        @Override public void writeRaw(char c) throws IOException {}
        @Override public void writeRawValue(String text) throws IOException {}
        @Override public void writeRawValue(String text, int offset, int len) throws IOException {}
        @Override public void writeRawValue(char[] text, int offset, int len) throws IOException {}
        @Override public void writeNumber(short v) throws IOException {}
        @Override public void writeNumber(int v) throws IOException {}
        @Override public void writeNumber(long v) throws IOException {}
        @Override public void writeNumber(java.math.BigInteger v) throws IOException {}
        @Override public void writeNumber(double v) throws IOException {}
        @Override public void writeNumber(float v) throws IOException {}
        @Override public void writeNumber(java.math.BigDecimal v) throws IOException {}
        @Override public void writeNumber(String encodedValue) throws IOException {}
        @Override public void writeBoolean(boolean state) throws IOException {}
        @Override public void writeNull() throws IOException {}
        @Override public Object getOutputTarget() { return null; }
        @Override public int getOutputBuffered() { return 0; }
    }

    @Test
    public void testConstructorAndGenerateId() throws Throwable {
        ObjectIdGenerator<Object> gen = new DummyObjectIdGenerator("test-id");
        WritableObjectId woid = new WritableObjectId(gen);

        assertNotNull(woid.generator);
        assertNull(woid.id);

        Object generated = woid.generateId("poji-val");
        assertEquals("test-id", generated);
        assertEquals("test-id", woid.id);
    }

    @Test
    public void testWriteAsIdNullId() throws Throwable {
        ObjectIdGenerator<Object> gen = new DummyObjectIdGenerator(null);
        WritableObjectId woid = new WritableObjectId(gen);
        DummyJsonGenerator jgen = new DummyJsonGenerator(false);

        boolean result = woid.writeAsId(jgen, null, null);
        assertFalse(result);
    }

    @Test
    public void testWriteAsIdNotNullIdNotWrittenNotAlwaysAsId() throws Throwable {
        ObjectIdGenerator<Object> gen = new DummyObjectIdGenerator("id123");
        WritableObjectId woid = new WritableObjectId(gen);
        woid.id = "id123";
        DummyJsonGenerator jgen = new DummyJsonGenerator(false);
        ObjectIdWriter writer = new ObjectIdWriter(null, null, null, null, false);

        boolean result = woid.writeAsId(jgen, null, writer);
        assertFalse(result);
    }

    @Test
    public void testWriteAsIdAlwaysAsIdNative() throws Throwable {
        ObjectIdGenerator<Object> gen = new DummyObjectIdGenerator("id123");
        WritableObjectId woid = new WritableObjectId(gen);
        woid.id = "id123";
        DummyJsonGenerator jgen = new DummyJsonGenerator(true);
        ObjectIdWriter writer = new ObjectIdWriter(null, null, null, null, true);

        boolean result = woid.writeAsId(jgen, null, writer);
        assertTrue(result);
        assertTrue(jgen.canWriteObjectIdCalled);
        assertTrue(jgen.writeObjectRefCalled);
    }

    @Test
    public void testWriteAsFieldNative() throws Throwable {
        ObjectIdGenerator<Object> gen = new DummyObjectIdGenerator("id123");
        WritableObjectId woid = new WritableObjectId(gen);
        woid.id = "id123";
        DummyJsonGenerator jgen = new DummyJsonGenerator(true);
        ObjectIdWriter writer = new ObjectIdWriter(null, null, null, null, false);

        woid.writeAsField(jgen, null, writer);
        assertTrue(jgen.canWriteObjectIdCalled);
        assertTrue(jgen.writeObjectIdCalled);
    }
}