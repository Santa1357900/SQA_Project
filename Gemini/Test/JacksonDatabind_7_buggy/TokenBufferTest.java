package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.DeserializationContext;

public class TokenBufferTest {

    @Test
    public void testLifeCycleAndFeatures() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, true);
        assertNotNull(buf.version());
        assertFalse(buf.isClosed());
        buf.close();
        assertTrue(buf.isClosed());

        buf.enable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        assertTrue(buf.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
        buf.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        assertFalse(buf.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));

        int mask = buf.getFeatureMask();
        buf.setFeatureMask(mask);
        assertEquals(mask, buf.getFeatureMask());

        assertSame(buf, buf.useDefaultPrettyPrinter());
        assertSame(buf, buf.setCodec(null));
        assertNull(buf.getCodec());
        assertNotNull(buf.getOutputContext());
        assertTrue(buf.canWriteBinaryNatively());
        assertTrue(buf.canWriteTypeId());
        assertTrue(buf.canWriteObjectId());

        buf.flush();
    }

    @Test
    public void testConstructors() throws Throwable {
        TokenBuffer buf1 = new TokenBuffer(null);
        assertNotNull(buf1.asParser());

        TokenBuffer buf2 = new TokenBuffer(null, false);
        assertNotNull(buf2.asParser());

        TokenParserTestDummyParser dummyParser = new TokenParserTestDummyParser();
        TokenBuffer buf3 = new TokenBuffer(dummyParser);
        assertNotNull(buf3.asParser(dummyParser));
    }

    @Test
    public void testWriteStructuralAndNulls() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeStartArray();
        buf.writeStartObject();
        buf.writeFieldName("field1");
        buf.writeString("value1");
        buf.writeFieldName(new SerializedString("field2"));
        buf.writeString(new SerializedString("value2"));
        buf.writeNull();
        buf.writeBoolean(true);
        buf.writeBoolean(false);
        buf.writeEndObject();
        buf.writeEndArray();

        assertNotNull(buf.firstToken());
        assertEquals("[TokenBuffer: START_ARRAY, START_OBJECT, FIELD_NAME(field1), VALUE_STRING, FIELD_NAME(field2), VALUE_STRING, VALUE_NULL, VALUE_TRUE, VALUE_FALSE, END_OBJECT, END_ARRAY]", buf.toString());
        
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("field1", p.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("value1", p.getText());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("value2", p.getText());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.getEmbeddedObject());
        assertEquals(JsonToken.VALUE_TRUE, p.nextToken());
        assertTrue(p.getBooleanValue());
        assertEquals(JsonToken.VALUE_FALSE, p.nextToken());
        assertFalse(p.getBooleanValue());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }

    @Test
    public void testWriteStringVariants() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeString((String) null);
        buf.writeString((SerializableString) null);
        buf.writeString("hello".toCharArray(), 0, 5);
        buf.writeNumber((short) 123);
        buf.writeNumber(12345);
        buf.writeNumber(123456789L);
        buf.writeNumber(1.5d);
        buf.writeNumber(2.5f);
        buf.writeNumber((BigDecimal) null);
        buf.writeNumber((BigInteger) null);
        buf.writeNumber(new BigDecimal("10.5"));
        buf.writeNumber(new BigInteger("100"));
        buf.writeNumber("999");

        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("hello", p.getText());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(123, p.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(12345, p.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(123456789L, p.getLongValue());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(1.5d, p.getDoubleValue(), 0.001);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(2.5f, p.getFloatValue(), 0.001f);
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(new BigDecimal("10.5"), p.getDecimalValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(new BigInteger("100"), p.getBigIntegerValue());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(999L, p.getLongValue());
    }

    @Test
    public void testWriteObjectsAndBinary() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeObject(null);
        buf.writeObject(new byte[] { 1, 2, 3 });
        buf.writeBinary(Base64Variants.MIME, new byte[] { 4, 5, 6 }, 0, 3);
        buf.writeTree(null);

        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[]) p.getEmbeddedObject());
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertArrayEquals(new byte[] { 4, 5, 6 }, (byte[]) p.getEmbeddedObject());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        p = buf.asParser();
        p.nextToken(); // NULL
        p.nextToken(); // EMBEDDED
        assertArrayEquals(new byte[] { 1, 2, 3 }, p.getBinaryValue(Base64Variants.MIME));
        
        p.nextToken(); // EMBEDDED
        assertEquals(3, p.readBinaryValue(Base64Variants.MIME, out));
        assertArrayEquals(new byte[] { 4, 5, 6 }, out.toByteArray());
    }

    @Test
    public void testNativeIdsAndAppend() throws Throwable {
        TokenBuffer buf1 = new TokenBuffer(null, true);
        buf1.writeObjectId("objId");
        buf1.writeTypeId("typeId");
        buf1.writeNumber(42);

        TokenBuffer buf2 = new TokenBuffer(null, true);
        buf2.append(buf1);

        JsonParser p = buf2.asParser();
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals("objId", p.getObjectId());
        assertEquals("typeId", p.getTypeId());
        assertEquals(42, p.getIntValue());
    }

    @Test
    public void testParserEdgeCasesAndNumbers() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeNumber(100);
        buf.writeNumber(200L);
        buf.writeNumber(300.0d);
        buf.writeNumber(400.0f);
        buf.writeNumber(new BigDecimal("500.5"));
        buf.writeNumber(new BigInteger("600"));
        buf.writeString("123.45");

        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(100, p.getIntValue());
        assertEquals(BigInteger.valueOf(100), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(100), p.getDecimalValue());

        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(200L, p.getLongValue());

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(300.0d, p.getDoubleValue(), 0.001);

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(400.0f, p.getFloatValue(), 0.001f);

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(new BigDecimal("500.5"), p.getDecimalValue());
        assertEquals(BigInteger.valueOf(500), p.getBigIntegerValue());

        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(new BigInteger("600"), p.getBigIntegerValue());

        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(123.45, p.getDoubleValue(), 0.001);

        assertNotNull(p.getTokenLocation());
        assertNotNull(p.getCurrentLocation());
        assertNotNull(p.version());
        p.setCodec(null);
        assertNull(p.getCodec());
        p.overrideCurrentName("newName");
        assertFalse(p.hasTextCharacters());
        assertEquals(0, p.getTextOffset());
        assertNotNull(p.getTextCharacters());
        assertEquals(6, p.getTextLength());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportedOperations() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeRaw("test");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportedOperationStreamBinary() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeBinary(Base64Variants.MIME, null, 10);
    }

    @Test
    public void testParserPeekAndMisc() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeStartArray();
        buf.writeEndArray();

        TokenBuffer.Parser p = (TokenBuffer.Parser) buf.asParser();
        assertEquals(JsonToken.START_ARRAY, p.peekNextToken());
        p.close();
        assertNull(p.peekNextToken());
    }

    @Test
    public void testSerializeAndDeserialize() throws Throwable {
        TokenBuffer buf = new TokenBuffer(null, false);
        buf.writeStartObject();
        buf.writeFieldName("a");
        buf.writeNumber(1);
        buf.writeEndObject();

        TokenBuffer buf2 = new TokenBuffer(null, false);
        buf2.deserialize(buf.asParser(), null);
        assertNotNull(buf2.firstToken());
    }

    private static class TokenParserTestDummyParser extends com.fasterxml.jackson.core.base.ParserBase {
        public TokenParserTestDummyParser() {
            super(null, 0);
        }
        @Override public com.fasterxml.jackson.core.ObjectCodec getCodec() { return null; }
        @Override public void setCodec(com.fasterxml.jackson.core.ObjectCodec c) {}
        @Override public com.fasterxml.jackson.core.Version version() { return com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION; }
        @Override public JsonToken nextToken() throws IOException { return null; }
        @Override protected void _finishString() throws IOException {}
        @Override protected void _parseNumericValue(int i) throws IOException {}
        @Override protected void _parseName() throws IOException {}
        @Override protected void _handleEOF() throws IOException {}
    }
}