package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.VersionUtil;

public class JsonGeneratorImplTest {

    private static class DummyJsonGeneratorImpl extends JsonGeneratorImpl {
        public DummyJsonGeneratorImpl(IOContext ctxt, int features, ObjectCodec codec) {
            super(ctxt, features, codec);
        }

        @Override public void writeFieldName(String name) throws IOException {}
        @Override public void writeFieldName(SerializableString name) throws IOException {}
        @Override protected void _writeString(String text) throws IOException {}
        @Override protected void _writeString(char[] buffer, int offset, int len) throws IOException {}
        @Override public void writeRawUTF8String(byte[] buffer, int offset, int len) throws IOException {}
        @Override public void writeUTF8String(byte[] buffer, int offset, int len) throws IOException {}
        @Override public void writeRaw(String text) throws IOException {}
        @Override public void writeRaw(String text, int offset, int len) throws IOException {}
        @Override public void writeRaw(SerializableString text) throws IOException {}
        @Override public void writeRaw(char[] text, int offset, int len) throws IOException {}
        @Override public void writeRaw(char c) throws IOException {}
        @Override public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws IOException {}
        @Override public void writeNumber(short v) throws IOException {}
        @Override public void writeNumber(int v) throws IOException {}
        @Override public void writeNumber(long v) throws IOException {}
        @Override public void writeNumber(BigInteger v) throws IOException {}
        @Override public void writeNumber(double v) throws IOException {}
        @Override public void writeNumber(float v) throws IOException {}
        @Override public void writeNumber(BigDecimal v) throws IOException {}
        @Override public void writeNumber(String encodedValue) throws IOException {}
        @Override public void writeBoolean(boolean state) throws IOException {}
        @Override public void writeNull() throws IOException {}
        @Override public void writeStartArray() throws IOException {}
        @Override public void writeEndArray() throws IOException {}
        @Override public void writeStartObject() throws IOException {}
        @Override public void writeEndObject() throws IOException {}
        @Override public void copyCurrentEvent(JsonParser p) throws IOException {}
        @Override public void copyCurrentStructure(JsonParser p) throws IOException {}
        @Override public JsonStreamContext getOutputContext() { return null; }
        @Override public void flush() throws IOException {}
        @Override public boolean isClosed() { return false; }
        @Override public void close() throws IOException {}
    }

    private static class CustomCharacterEscapes extends CharacterEscapes {
        private final int[] asciiEscapes;

        public CustomCharacterEscapes(int[] escapes) {
            this.asciiEscapes = escapes;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes;
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            return null;
        }
    }

    @Test
    public void testConstructorAndFeatures() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        int features = JsonGenerator.Feature.ESCAPE_NON_ASCII.getMask() 
                | JsonGenerator.Feature.QUOTE_FIELD_NAMES.getMask();
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, features, null);
        
        assertEquals(127, gen.getHighestEscapedChar());
        assertFalse(gen._cfgUnqNames);
    }

    @Test
    public void testConstructorWithoutEscapeNonAscii() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        int features = 0; // neither ESCAPE_NON_ASCII nor QUOTE_FIELD_NAMES disabled
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, features, null);
        
        assertEquals(0, gen.getHighestEscapedChar());
        assertTrue(gen._cfgUnqNames);
    }

    @Test
    public void testEnableFeatureQuoteFieldNames() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, 0, null);
        assertTrue(gen._cfgUnqNames);

        gen.enable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
        assertFalse(gen._cfgUnqNames);
    }

    @Test
    public void testSetHighestNonEscapedChar() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, 0, null);
        
        gen.setHighestNonEscapedChar(250);
        assertEquals(250, gen.getHighestEscapedChar());

        gen.setHighestNonEscapedChar(-10);
        assertEquals(0, gen.getHighestEscapedChar());
    }

    @Test
    public void testSetCharacterEscapes() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, 0, null);
        assertNull(gen.getCharacterEscapes());

        int[] customEscapes = new int[128];
        customEscapes['a'] = CharacterEscapes.ESCAPE_CUSTOM;
        CustomCharacterEscapes esc = new CustomCharacterEscapes(customEscapes);

        gen.setCharacterEscapes(esc);
        assertEquals(esc, gen.getCharacterEscapes());
        assertEquals(customEscapes, gen._outputEscapes);

        gen.setCharacterEscapes(null);
        assertNull(gen.getCharacterEscapes());
        assertEquals(JsonGeneratorImpl.sOutputEscapes, gen._outputEscapes);
    }

    @Test
    public void testSetRootValueSeparator() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, 0, null);
        
        SerializableString sep = new DefaultPrettyPrinter().getDefaultRootValueSeparator();
        gen.setRootValueSeparator(sep);
        assertEquals(sep, gen._rootValueSeparator);

        gen.setRootValueSeparator(null);
        assertNull(gen._rootValueSeparator);
    }

    @Test
    public void testVersion() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, 0, null);
        Version v = gen.version();
        assertNotNull(v);
    }

    @Test
    public void testWriteStringField() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        
        DummyJsonGeneratorImpl gen = new DummyJsonGeneratorImpl(ctxt, 0, null);
        // Just invoking to ensure no exception and coverage of final writeStringField
        gen.writeStringField("testField", "testValue");
    }
}