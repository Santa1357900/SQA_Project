package com.fasterxml.jackson.core;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class JsonGeneratorTest {

    private static class DummyJsonGenerator extends JsonGenerator {
        private int featureMask = Feature.collectDefaults();
        private boolean closed = false;
        private ObjectCodec codec;
        private FormatSchema schema;

        @Override
        public JsonGenerator setCodec(ObjectCodec oc) {
            this.codec = oc;
            return this;
        }

        @Override
        public ObjectCodec getCodec() {
            return this.codec;
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public JsonGenerator enable(Feature f) {
            featureMask |= f.getMask();
            return this;
        }

        @Override
        public JsonGenerator disable(Feature f) {
            featureMask &= ~f.getMask();
            return this;
        }

        @Override
        public boolean isEnabled(Feature f) {
            return f.enabledIn(featureMask);
        }

        @Override
        public int getFeatureMask() {
            return featureMask;
        }

        @Override
        public JsonGenerator setFeatureMask(int values) {
            featureMask = values;
            return this;
        }

        @Override
        public JsonGenerator useDefaultPrettyPrinter() {
            return this;
        }

        @Override
        public void writeStartArray() throws IOException {}

        @Override
        public void writeEndArray() throws IOException {}

        @Override
        public void writeStartObject() throws IOException {}

        @Override
        public void writeEndObject() throws IOException {}

        @Override
        public void writeFieldName(String name) throws IOException {}

        @Override
        public void writeFieldName(SerializableString name) throws IOException {}

        @Override
        public void writeString(String text) throws IOException {}

        @Override
        public void writeString(char[] text, int offset, int len) throws IOException {}

        @Override
        public void writeString(SerializableString text) throws IOException {}

        @Override
        public void writeRawUTF8String(byte[] text, int offset, int length) throws IOException {}

        @Override
        public void writeUTF8String(byte[] text, int offset, int length) throws IOException {}

        @Override
        public void writeRaw(String text) throws IOException {}

        @Override
        public void writeRaw(String text, int offset, int len) throws IOException {}

        @Override
        public void writeRaw(char[] text, int offset, int len) throws IOException {}

        @Override
        public void writeRaw(char c) throws IOException {}

        @Override
        public void writeRawValue(String text) throws IOException {}

        @Override
        public void writeRawValue(String text, int offset, int len) throws IOException {}

        @Override
        public void writeRawValue(char[] text, int offset, int len) throws IOException {}

        @Override
        public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws IOException {}

        @Override
        public int writeBinary(Base64Variant bv, InputStream data, int dataLength) throws IOException {
            return 0;
        }

        @Override
        public void writeNumber(int v) throws IOException {}

        @Override
        public void writeNumber(long v) throws IOException {}

        @Override
        public void writeNumber(BigInteger v) throws IOException {}

        @Override
        public void writeNumber(double v) throws IOException {}

        @Override
        public void writeNumber(float v) throws IOException {}

        @Override
        public void writeNumber(BigDecimal v) throws IOException {}

        @Override
        public void writeNumber(String encodedValue) throws IOException {}

        @Override
        public void writeBoolean(boolean state) throws IOException {}

        @Override
        public void writeNull() throws IOException {}

        @Override
        public void writeObject(Object pojo) throws IOException {}

        @Override
        public void writeTree(TreeNode rootNode) throws IOException {}

        @Override
        public JsonStreamContext getOutputContext() {
            return null;
        }

        @Override
        public void flush() throws IOException {}

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    @Test
    public void testFeatureMethods() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        assertTrue(gen.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
        gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        assertFalse(gen.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
        gen.enable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        assertTrue(gen.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));

        gen.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        assertFalse(gen.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
        gen.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, true);
        assertTrue(gen.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));

        int mask = gen.getFeatureMask();
        gen.setFeatureMask(0);
        assertEquals(0, gen.getFeatureMask());
        gen.setFeatureMask(mask);
        assertEquals(mask, gen.getFeatureMask());

        gen.overrideStdFeatures(0, JsonGenerator.Feature.AUTO_CLOSE_TARGET.getMask());
        assertFalse(gen.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));

        assertEquals(0, gen.getFormatFeatures());
        
        try {
            gen.overrideFormatFeatures(1, 1);
            fail("Expected UnsupportedOperationException or IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No FormatFeatures defined"));
        }
    }

    @Test
    public void testSchemaMethods() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        assertNull(gen.getSchema());
        assertFalse(gen.canUseSchema(null));

        try {
            gen.setSchema(new FormatSchema() {
                @Override
                public String getSchemaType() { return "test"; }
            });
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("does not support schema"));
        }
    }

    @Test
    public void testPrettyPrinterAndExtras() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        assertNull(gen.getPrettyPrinter());
        PrettyPrinter pp = new com.fasterxml.jackson.core.util.DefaultPrettyPrinter();
        gen.setPrettyPrinter(pp);
        assertEquals(pp, gen.getPrettyPrinter());

        assertEquals(gen, gen.setHighestNonEscapedChar(127));
        assertEquals(0, gen.getHighestEscapedChar());
        assertNull(gen.getCharacterEscapes());
        assertEquals(gen, gen.setCharacterEscapes(null));

        try {
            gen.setRootValueSeparator(null);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        assertNull(gen.getOutputTarget());
        assertEquals(-1, gen.getOutputBuffered());
        assertNull(gen.getCurrentValue());
        gen.setCurrentValue("test");
        assertNull(gen.getCurrentValue()); // context is null in dummy
    }

    @Test
    public void testCapabilities() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        assertFalse(gen.canWriteObjectId());
        assertFalse(gen.canWriteTypeId());
        assertFalse(gen.canWriteBinaryNatively());
        assertTrue(gen.canOmitFields());
        assertFalse(gen.canWriteFormattedNumbers());
    }

    @Test
    public void testWriteArrayVariants() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        
        int[] intArray = new int[] { 1, 2, 3 };
        gen.writeArray(intArray, 0, 3);

        long[] longArray = new long[] { 1L, 2L };
        gen.writeArray(longArray, 0, 2);

        double[] doubleArray = new double[] { 1.0, 2.0 };
        gen.writeArray(doubleArray, 0, 2);

        try {
            gen.writeArray((int[]) null, 0, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null array"));
        }

        try {
            gen.writeArray((long[]) null, 0, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null array"));
        }

        try {
            gen.writeArray((double[]) null, 0, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null array"));
        }

        try {
            gen.writeArray(intArray, -1, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("invalid argument"));
        }
    }

    @Test
    public void testWriteRawAndFieldShortcuts() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        SerializableString rawStr = new com.fasterxml.jackson.core.io.SerializedString("raw");
        gen.writeRaw(rawStr);
        gen.writeRawValue(rawStr);

        gen.writeNumber((short) 10);
        gen.writeBinary(new byte[0]);
        gen.writeBinary(new byte[0], 0, 0);

        gen.writeStringField("f1", "v1");
        gen.writeBooleanField("f2", true);
        gen.writeNullField("f3");
        gen.writeNumberField("f4", 10);
        gen.writeNumberField("f5", 10L);
        gen.writeNumberField("f6", 10.0);
        gen.writeNumberField("f7", 10.0f);
        gen.writeNumberField("f8", new BigDecimal("10.0"));
        gen.writeBinaryField("f9", new byte[0]);
        gen.writeArrayFieldStart("f10");
        gen.writeEndArray();
        gen.writeObjectFieldStart("f11");
        gen.writeEndObject();
        gen.writeObjectField("f12", "obj");
        gen.writeOmittedField("f13");
        gen.writeFieldId(123L);

        try {
            gen.writeEmbeddedObject("test");
            fail("Expected JsonGenerationException");
        } catch (JsonGenerationException e) {
            assertTrue(e.getMessage().contains("No native support"));
        }

        try {
            gen.writeObjectId("id");
            fail("Expected JsonGenerationException");
        } catch (JsonGenerationException e) {
            assertTrue(e.getMessage().contains("No native support"));
        }

        try {
            gen.writeObjectRef("id");
            fail("Expected JsonGenerationException");
        } catch (JsonGenerationException e) {
            assertTrue(e.getMessage().contains("No native support"));
        }

        try {
            gen.writeTypeId("id");
            fail("Expected JsonGenerationException");
        } catch (JsonGenerationException e) {
            assertTrue(e.getMessage().contains("No native support"));
        }
    }

    @Test
    public void testSimpleObjectWriting() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        gen._writeSimpleObject(null);
        gen._writeSimpleObject("string");
        gen._writeSimpleObject(Integer.valueOf(1));
        gen._writeSimpleObject(Long.valueOf(2L));
        gen._writeSimpleObject(Double.valueOf(3.0));
        gen._writeSimpleObject(Float.valueOf(4.0f));
        gen._writeSimpleObject(Short.valueOf((short) 5));
        gen._writeSimpleObject(Byte.valueOf((byte) 6));
        gen._writeSimpleObject(BigInteger.TEN);
        gen._writeSimpleObject(BigDecimal.TEN);
        gen._writeSimpleObject(new AtomicInteger(7));
        gen._writeSimpleObject(new AtomicLong(8L));
        gen._writeSimpleObject(new byte[0]);
        gen._writeSimpleObject(Boolean.TRUE);
        gen._writeSimpleObject(new AtomicBoolean(true));

        try {
            gen._writeSimpleObject(new Object());
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No ObjectCodec defined"));
        }
    }

    @Test
    public void testHelperErrorMethods() throws Throwable {
        DummyJsonGenerator gen = new DummyJsonGenerator();
        try {
            gen._reportError("error");
            fail("Expected JsonGenerationException");
        } catch (JsonGenerationException e) {
            assertTrue(e.getMessage().contains("error"));
        }

        try {
            gen._reportUnsupportedOperation();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Operation not supported"));
        }
    }
}