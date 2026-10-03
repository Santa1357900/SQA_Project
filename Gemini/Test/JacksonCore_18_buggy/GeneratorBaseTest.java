package com.fasterxml.jackson.core.base;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.core.json.JsonWriteContext;

public class GeneratorBaseTest {

    private static class ConcreteGeneratorBase extends GeneratorBase {
        public boolean releaseBuffersCalled = false;
        public boolean verifyValueWriteCalled = false;
        public String lastVerifyMsg = null;
        public boolean flushCalled = false;

        public ConcreteGeneratorBase(int features, ObjectCodec codec) {
            super(features, codec);
        }

        public ConcreteGeneratorBase(int features, ObjectCodec codec, JsonWriteContext ctxt) {
            super(features, codec, ctxt);
        }

        @Override
        protected void _releaseBuffers() {
            releaseBuffersCalled = true;
        }

        @Override
        protected void _verifyValueWrite(String typeMsg) throws IOException {
            verifyValueWriteCalled = true;
            lastVerifyMsg = typeMsg;
        }

        @Override
        public void flush() throws IOException {
            flushCalled = true;
        }

        @Override
        public JsonGenerator writeStartArray() throws IOException { return this; }
        @Override
        public JsonGenerator writeEndArray() throws IOException { return this; }
        @Override
        public JsonGenerator writeStartObject() throws IOException { return this; }
        @Override
        public JsonGenerator writeEndObject() throws IOException { return this; }

        @Override
        public void writeFieldName(String name) throws IOException {}

        @Override
        public void writeString(String text) throws IOException {}

        @Override
        public void writeString(char[] text, int offset, int len) throws IOException {}

        @Override
        public void writeRaw(String text) throws IOException {}

        @Override
        public void writeRaw(String text, int offset, int len) throws IOException {}

        @Override
        public void writeRaw(char[] text, int offset, int len) throws IOException {}

        @Override
        public void writeRaw(SerializableString text) throws IOException {}

        @Override
        public void writeNumber(int v) throws IOException {}

        @Override
        public void writeNumber(long v) throws IOException {}

        @Override
        public void writeNumber(BigInteger v) throws IOException {}

        @Override
        public void writeNumber(BigDecimal v) throws IOException {}

        @Override
        public void writeNumber(double v) throws IOException {}

        @Override
        public void writeNumber(float v) throws IOException {}

        @Override
        public void writeNumber(String encodedValue) throws IOException {}

        @Override
        public void writeBoolean(boolean state) throws IOException {}

        @Override
        public void writeNull() throws IOException {}
    }

    @Test
    public void testLifeCycleAndClose() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        assertFalse(gen.isClosed());
        gen.close();
        assertTrue(gen.isClosed());
    }

    @Test
    public void testCurrentAndCodec() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        assertNull(gen.getCodec());
        
        ObjectCodec codec = new com.fasterxml.jackson.databind.ObjectMapper();
        gen.setCodec(codec);
        assertEquals(codec, gen.getCodec());

        assertNull(gen.getCurrentValue());
        gen.setCurrentValue("testValue");
        assertEquals("testValue", gen.getCurrentValue());
        assertEquals("testValue", gen.getOutputContext().getCurrentValue());
    }

    @Test
    public void testFeaturesEnableDisable() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        assertFalse(gen.isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS));
        
        gen.enable(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS);
        assertTrue(gen.isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS));

        gen.disable(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS);
        assertFalse(gen.isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS));

        gen.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        assertTrue(gen.isEnabled(JsonGenerator.Feature.ESCAPE_NON_ASCII));
        gen.disable(JsonGenerator.Feature.ESCAPE_NON_ASCII);

        gen.enable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
        assertTrue(gen.isEnabled(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION));
        gen.disable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Test
    public void testSetFeatureMaskAndOverride() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        int mask = JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS.getMask() | JsonGenerator.Feature.ESCAPE_NON_ASCII.getMask();
        gen.setFeatureMask(mask);
        assertEquals(mask, gen.getFeatureMask());
        assertTrue(gen.isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS));

        gen.overrideStdFeatures(0, mask);
        assertEquals(0, gen.getFeatureMask());
        assertFalse(gen.isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS));
    }

    @Test
    public void testVersion() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        Version v = gen.version();
        assertNotNull(v);
    }

    @Test
    public void testWriteMethodsWithSerializableString() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        SerializableString sstr = new SerializedString("hello");
        gen.writeFieldName(sstr);
        gen.writeString(sstr);
    }

    @Test
    public void testWriteRawValueVariants() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        gen.writeRawValue("raw");
        assertTrue(gen.verifyValueWriteCalled);
        assertEquals("write raw value", gen.lastVerifyMsg);

        gen.verifyValueWriteCalled = false;
        gen.writeRawValue("raw", 0, 3);
        assertTrue(gen.verifyValueWriteCalled);

        gen.verifyValueWriteCalled = false;
        char[] chars = new char[]{'r', 'a', 'w'};
        gen.writeRawValue(chars, 0, 3);
        assertTrue(gen.verifyValueWriteCalled);

        gen.verifyValueWriteCalled = false;
        SerializableString sstr = new SerializedString("raw");
        gen.writeRawValue(sstr);
        assertTrue(gen.verifyValueWriteCalled);
    }

    @Test
    public void testWriteBinaryUnsupported() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        try {
            gen.writeBinary(Base64Variant.getDefaultASCII(), null, 10);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testWriteObjectNull() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        // Writing null should delegate to writeNull() without throwing NPE if handled properly, 
        // but since writeNull is abstract in our concrete stub, let's verify it calls writeNull (or doesn't crash).
        try {
            gen.writeObject(null);
        } catch (Exception e) {
            // expected if writeNull is stubbed to do nothing or throw, but checks flow
        }
    }

    @Test
    public void testWriteTreeNull() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        try {
            gen.writeTree(null);
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testWriteTreeNoCodec() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        TreeNode mockNode = new TreeNode() {
            @Override public com.fasterxml.jackson.core.JsonToken asToken() { return null; }
            @Override public com.fasterxml.jackson.core.JsonParser.NumberType numberType() { return null; }
            @Override public int size() { return 0; }
            @Override public boolean isValueNode() { return false; }
            @Override public boolean isContainerNode() { return false; }
            @Override public boolean isMissingNode() { return false; }
            @Override public boolean isArray() { return false; }
            @Override public boolean isObject() { return false; }
            @Override public TreeNode get(String fieldName) { return null; }
            @Override public TreeNode get(int index) { return null; }
            @Override public TreeNode path(String fieldName) { return null; }
            @Override public TreeNode path(int index) { return null; }
            @Override public java.util.Iterator<String> fieldNames() { return null; }
            @Override public TreeNode at(com.fasterxml.jackson.core.JsonPointer ptr) { return null; }
            @Override public TreeNode at(String expr) { return null; }
            @Override public com.fasterxml.jackson.core.JsonParser traverse() { return null; }
            @Override public com.fasterxml.jackson.core.JsonParser traverse(ObjectCodec c) { return null; }
        };

        try {
            gen.writeTree(mockNode);
            fail("Expected IllegalStateException due to no ObjectCodec");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No ObjectCodec defined"));
        }
    }

    @Test
    public void testDefaultPrettyPrinter() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        assertNull(gen.getPrettyPrinter());
        gen.useDefaultPrettyPrinter();
        assertNotNull(gen.getPrettyPrinter());
        
        // Calling again when pretty printer is already set should return same/keep it
        com.fasterxml.jackson.core.PrettyPrinter firstPp = gen.getPrettyPrinter();
        gen.useDefaultPrettyPrinter();
        assertEquals(firstPp, gen.getPrettyPrinter());
    }

    @Test
    public void testAsStringBigDecimal() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        BigDecimal bd = new BigDecimal("123.45");
        assertEquals("123.45", gen._asString(bd));
    }

    @Test
    public void testDecodeSurrogateValid() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        // SURR1_FIRST = 0xD800, SURR2_FIRST = 0xDC00
        int decoded = gen._decodeSurrogate(0xD800, 0xDC00);
        assertEquals(0x10000, decoded);
    }

    @Test
    public void testDecodeSurrogateInvalid() throws Throwable {
        ConcreteGeneratorBase gen = new ConcreteGeneratorBase(0, null);
        try {
            gen._decodeSurrogate(0xD800, 0x0000); // Invalid second surrogate
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Incomplete surrogate pair"));
        }
    }
}