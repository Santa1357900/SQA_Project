package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;

public class UTF8JsonGeneratorTest {

    @Test
    public void testLifeCycleAndBasicGetters() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        assertSame(out, gen.getOutputTarget());
        assertEquals(0, gen.getOutputBuffered());
        
        gen.flush();
        gen.close();
    }

    @Test
    public void testWriteNull() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        gen.writeNull();
        gen.flush();
        
        assertEquals("null", out.toString("UTF-8"));
        gen.close();
    }

    @Test
    public void testWriteBoolean() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        gen.writeBoolean(true);
        gen.writeBoolean(false);
        gen.flush();
        
        assertEquals("truefalse", out.toString("UTF-8"));
        gen.close();
    }

    @Test
    public void testWriteNumbers() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        gen.writeNumber((short) 123);
        gen.writeNumber(456);
        gen.writeNumber(789L);
        gen.writeNumber(new BigInteger("123456789"));
        gen.writeNumber(new BigDecimal("123.45"));
        gen.writeNumber(1.5);
        gen.writeNumber(2.5f);
        gen.writeNumber("3.14");
        gen.flush();
        
        String result = out.toString("UTF-8");
        assertTrue(result.contains("123"));
        assertTrue(result.contains("456"));
        assertTrue(result.contains("789"));
        assertTrue(result.contains("123456789"));
        assertTrue(result.contains("123.45"));
        gen.close();
    }

    @Test
    public void testWriteStructural() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        gen.writeStartArray();
        gen.writeEndArray();
        
        gen.writeStartObject();
        gen.writeFieldName("key");
        gen.writeString("value");
        gen.writeEndObject();
        
        gen.flush();
        assertEquals("[]{\"key\":\"value\"}", out.toString("UTF-8"));
        gen.close();
    }

    @Test
    public void testWriteBinary() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        gen.writeBinary(Base64Variants.MIME, data, 0, data.length);
        gen.flush();
        
        assertTrue(out.toString("UTF-8").length() > 2);
        gen.close();
    }

    @Test
    public void testWriteBinaryStreamKnownLength() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        byte[] data = new byte[] { 10, 20, 30, 40 };
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        
        gen.writeBinary(Base64Variants.MIME, in, 4);
        gen.flush();
        gen.close();
    }

    @Test
    public void testWriteBinaryStreamUnknownLength() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        byte[] data = new byte[] { 10, 20, 30, 40 };
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        
        gen.writeBinary(Base64Variants.MIME, in, -1);
        gen.flush();
        gen.close();
    }

    @Test
    public void testWriteRawMethods() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        gen.writeRaw("rawText");
        gen.writeRaw(new char[] { 'a', 'b', 'c' }, 0, 3);
        gen.writeRaw('X');
        gen.flush();
        
        assertEquals("rawTextabcX", out.toString("UTF-8"));
        gen.close();
    }

    @Test
    public void testWriteStringsAndSegments() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out);
        
        gen.writeString("Hello \u0080 \uFFFF");
        gen.writeString(new char[] { 't', 'e', 's', 't' }, 0, 4);
        gen.flush();
        gen.close();
    }

    @Test
    public void testCustomBufferConstructor() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, out, true);
        byte[] buffer = new byte[100];
        
        UTF8JsonGenerator gen = new UTF8JsonGenerator(ctxt, 0, null, out, buffer, 0, false);
        gen.writeNumber(10);
        gen.flush();
        gen.close();
    }
}