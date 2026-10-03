package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;

public class TokenBufferTest {

    @Test
    public void testConstructorsAndLifecycle() throws Throwable {
        TokenBuffer tb1 = new TokenBuffer((ObjectCodec) null);
        assertNotNull(tb1);
        assertFalse(tb1.isClosed());
        tb1.close();
        assertTrue(tb1.isClosed());

        TokenBuffer tb2 = new TokenBuffer(null, true);
        assertNotNull(tb2);
        assertTrue(tb2.canWriteTypeId());
        assertTrue(tb2.canWriteObjectId());

        TokenBuffer tb3 = new TokenBuffer((ObjectCodec) null, false);
        assertNotNull(tb3);
        assertFalse(tb3.canWriteTypeId());
        assertFalse(tb3.canWriteObjectId());
    }

    @Test
    public void testVersionAndCodec() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        Version version = tb.version();
        assertNotNull(version);

        tb.setCodec(null);
        assertNull(tb.getCodec());
    }

    @Test
    public void testFeaturesAndConfiguration() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.enable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        assertTrue(tb.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));

        tb.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        assertFalse(tb.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));

        int mask = tb.getFeatureMask();
        tb.setFeatureMask(mask);
        assertEquals(mask, tb.getFeatureMask());

        assertSame(tb, tb.useDefaultPrettyPrinter());
        assertSame(tb, tb.forceUseOfBigDecimal(true));
    }

    @Test
    public void testLowLevelOutput() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.flush();
        assertTrue(tb.canWriteBinaryNatively());
    }

    @Test
    public void testUnsupportedOperations() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        try {
            tb.writeRaw("test");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            tb.writeRaw("test", 0, 4);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            tb.writeRaw((SerializableString) new SerializedString("test"));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            tb.writeRaw(new char[]{'a'}, 0, 1);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            tb.writeRaw('a');
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            tb.writeRawUTF8String(new byte[0], 0, 0);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            tb.writeUTF8String(new byte[0], 0, 0);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            tb.writeBinary(Base64Variants.MIME, (InputStream) null, 10);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testWriteStructuralTokens() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        assertNull(tb.firstToken());

        tb.writeStartArray();
        assertEquals(JsonToken.START_ARRAY, tb.firstToken());
        tb.writeString("item");
        tb.writeEndArray();

        tb.writeStartObject();
        tb.writeFieldName("key");
        tb.writeNumber(123);
        tb.writeEndObject();
    }

    @Test
    public void testWriteFieldNamesAndStrings() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeStartObject();
        tb.writeFieldName("fieldStr");
        tb.writeString("valStr");

        tb.writeFieldName(new SerializedString("fieldSer"));
        tb.writeString((SerializableString) new SerializedString("valSer"));

        tb.writeString((String) null);
        tb.writeString((SerializableString) null);

        tb.writeString(new char[]{'a', 'b', 'c'}, 0, 3);
        tb.writeEndObject();
    }

    @Test
    public void testWriteRawValues() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeRawValue("raw1");
        tb.writeRawValue("raw2-long", 0, 4);
        tb.writeRawValue(new char[]{'r', 'a', 'w', '3'}, 0, 4);
    }

    @Test
    public void testWriteNumbers() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeNumber((short) 1);
        tb.writeNumber(2);
        tb.writeNumber(3L);
        tb.writeNumber(4.0d);
        tb.writeNumber(5.0f);
        tb.writeNumber((BigDecimal) null);
        tb.writeNumber(BigDecimal.TEN);
        tb.writeNumber((BigInteger) null);
        tb.writeNumber(BigInteger.ONE);
        tb.writeNumber("123.45");
        tb.writeBoolean(true);
        tb.writeBoolean(false);
        tb.writeNull();
    }

    @Test
    public void testWriteObjectsAndBinary() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeObject(null);
        tb.writeObject(new byte[]{1, 2, 3});
        tb.writeObject("plainObject");
        tb.writeTree(null);
        tb.writeTree(new com.fasterxml.jackson.databind.node.TextNode("treeNode"));

        tb.writeBinary(Base64Variants.MIME, new byte[]{1, 2, 3}, 0, 3);
    }

    @Test
    public void testNativeIds() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null, true);
        tb.writeObjectId("objId");
        tb.writeTypeId("typeId");
        tb.writeString("valueWithIds");
        assertTrue(tb.canWriteTypeId());
        assertTrue(tb.canWriteObjectId());
    }

    @Test
    public void testParserOperations() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeStartObject();
        tb.writeFieldName("f");
        tb.writeNumber(100);
        tb.writeEndObject();

        JsonParser p = tb.asParser();
        assertNotNull(p);
        assertNotNull(p.version());
        assertNotNull(p.getParsingContext());
        assertNotNull(p.getTokenLocation());
        assertNotNull(p.getCurrentLocation());

        assertNull(p.peekNextToken()); // before nextToken or matching

        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("f", p.getCurrentName());
        assertEquals("f", p.getText());
        assertNotNull(p.getTextCharacters());
        assertEquals(1, p.getTextLength());
        assertEquals(0, p.getTextOffset());
        assertFalse(p.hasTextCharacters());

        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(100, p.getIntValue());
        assertEquals(100L, p.getLongValue());
        assertEquals(100.0, p.getDoubleValue(), 0.01);
        assertEquals(100.0f, p.getFloatValue(), 0.01f);
        assertEquals(BigInteger.valueOf(100), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(100), p.getDecimalValue());
        assertEquals(JsonParser.NumberType.INT, p.getNumberType());
        assertNotNull(p.getNumberValue());

        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());

        p.close();
        assertTrue(p.isClosed());
    }

    @Test
    public void testParserEdgeCases() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeString("123.45");
        tb.writeNumber(BigInteger.TEN);
        tb.writeNull();

        JsonParser p = tb.asParser();
        p.nextToken();
        assertEquals(123.45, p.getDoubleValue(), 0.01);

        p.nextToken();
        assertEquals(BigDecimal.TEN, p.getDecimalValue());

        p.nextToken();
        assertNull(p.getEmbeddedObject());

        TokenBuffer tbBin = new TokenBuffer((ObjectCodec) null);
        tbBin.writeBinary(Base64Variants.MIME, new byte[]{1, 2}, 0, 2);
        JsonParser pBin = tbBin.asParser();
        pBin.nextToken();
        byte[] bytes = pBin.getBinaryValue(Base64Variants.MIME);
        assertNotNull(bytes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int readLen = pBin.readBinaryValue(Base64Variants.MIME, out);
        assertEquals(2, readLen);

        TokenBuffer tbStrBin = new TokenBuffer((ObjectCodec) null);
        tbStrBin.writeString("AQID");
        JsonParser pStrBin = tbStrBin.asParser();
        pStrBin.nextToken();
        assertNotNull(pStrBin.getBinaryValue(Base64Variants.MIME));
    }

    @Test
    public void testParserNumericErrors() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeString("notANumber");
        JsonParser p = tb.asParser();
        p.nextToken();
        try {
            p.getIntValue();
            fail("Expected JsonParseException");
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            // expected
        }
    }

    @Test
    public void testSerializeAndToString() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeStartArray();
        tb.writeString("test");
        tb.writeNumber(1);
        tb.writeBoolean(true);
        tb.writeNull();
        tb.writeRawValue("raw");
        tb.writeEndArray();

        String str = tb.toString();
        assertNotNull(str);
        assertTrue(str.contains("TokenBuffer"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.fasterxml.jackson.core.JsonGenerator gen = new com.fasterxml.jackson.core.json.JsonFactory().createGenerator(out);
        tb.serialize(gen);
        gen.close();
        assertTrue(out.size() > 0);
    }

    @Test
    public void testAppendAndDeserialize() throws Throwable {
        TokenBuffer tb1 = new TokenBuffer((ObjectCodec) null);
        tb1.writeStartObject();
        tb1.writeFieldName("a");
        tb1.writeNumber(1);
        tb1.writeEndObject();

        TokenBuffer tb2 = new TokenBuffer((ObjectCodec) null);
        tb2.append(tb1);

        assertNotNull(tb2.firstToken());

        TokenBuffer tb3 = new TokenBuffer((ObjectCodec) null);
        JsonParser p = tb1.asParser();
        p.nextToken(); // START_OBJECT
        tb3.deserialize(p, null);
        assertNotNull(tb3.firstToken());
    }

    @Test
    public void testSegmentOverflow() throws Throwable {
        TokenBuffer tb = new TokenBuffer((ObjectCodec) null);
        tb.writeStartArray();
        for (int i = 0; i < 20; i++) {
            tb.writeNumber(i);
        }
        tb.writeEndArray();

        JsonParser p = tb.asParser();
        assertNotNull(p.nextToken());
        while (p.nextToken() != null) {
            // iterate through all to exercise segment transitions
        }
    }
}