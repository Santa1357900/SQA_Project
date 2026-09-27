package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TokenBufferTest {

    @Test
    public void testConstructorsAndBasics() throws Throwable {
        TokenBuffer tb1 = new TokenBuffer((ObjectCodec) null, false);
        assertNotNull(tb1.version());
        assertFalse(tb1.isClosed());
        tb1.close();
        assertTrue(tb1.isClosed());

        ObjectMapper mapper = new ObjectMapper();
        TokenBuffer tb2 = new TokenBuffer(mapper);
        assertNotNull(tb2.asParser());
        assertNotNull(tb2.asParser(mapper));
        assertNotNull(tb2.asParser(tb2.asParser()));
    }

    @Test
    public void testFeatureMethods() throws Throwable {
        TokenBuffer tb = new TokenBuffer(null, true);
        JsonGenerator.Feature f = JsonGenerator.Feature.AUTO_CLOSE_TARGET;
        tb.enable(f);
        assertTrue(tb.isEnabled(f));
        tb.disable(f);
        assertFalse(tb.isEnabled(f));

        tb.setFeatureMask(1234);
        assertEquals(1234, tb.getFeatureMask());

        assertSame(tb, tb.useDefaultPrettyPrinter());
        assertSame(tb, tb.setCodec(null));
        assertNull(tb.getCodec());
        assertNotNull(tb.getOutputContext());

        assertTrue(tb.canWriteBinaryNatively());
        assertTrue(tb.canWriteTypeId());
        assertTrue(tb.canWriteObjectId());
    }

    @Test
    public void testUnsupportedOperations() throws Throwable {
        TokenBuffer tb = new TokenBuffer(null, false);
        boolean caught = false;
        try {
            tb.writeRaw("test");
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRaw("test", 0, 4);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRaw((com.fasterxml.jackson.core.SerializableString) null);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRaw(new char[0], 0, 0);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRaw('a');
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRawValue("test");
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRawValue("test", 0, 4);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRawValue(new char[0], 0, 0);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeBinary(null, (java.io.InputStream) null, 10);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeRawUTF8String(new byte[0], 0, 0);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            tb.writeUTF8String(new byte[0], 0, 0);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testWriteStructuralAndPrimitives() throws Throwable {
        TokenBuffer tb = new TokenBuffer(null, false);
        assertNull(tb.firstToken());

        tb.writeStartArray();
        tb.writeEndArray();
        tb.writeStartObject();
        tb.writeFieldName("fieldName");
        tb.writeString("stringVal");
        tb.writeString((String) null);
        tb.writeString("charArray".toCharArray(), 0, 9);
        tb.writeString((com.fasterxml.jackson.core.SerializableString) null);
        
        tb.writeNumber((short) 1);
        tb.writeNumber(2);
        tb.writeNumber(3L);
        tb.writeNumber(4.5d);
        tb.writeNumber(5.5f);
        tb.writeNumber((BigDecimal) null);
        tb.writeNumber(BigDecimal.TEN);
        tb.writeNumber((BigInteger) null);
        tb.writeNumber(BigInteger.ONE);
        tb.writeNumber("123");

        tb.writeBoolean(true);
        tb.writeBoolean(false);
        tb.writeNull();
        tb.writeObject(new Object());
        tb.writeTree(null);
        tb.writeBinary(com.fasterxml.jackson.core.Base64Variants.MIME, new byte[]{1, 2, 3}, 0, 3);

        tb.writeEndObject();

        tb.flush();
        assertNotNull(tb.toString());
    }

    @Test
    public void testNativeIdsAndAppend() throws Throwable {
        TokenBuffer tb1 = new TokenBuffer(null, true);
        tb1.writemédiaireIdIfSupported("typeId1", "objId1");
        tb1.writeStartObject();
        tb1.writeEndObject();

        TokenBuffer tb2 = new TokenBuffer(null, true);
        tb2.writeStartArray();
        tb2.append(tb1);
        tb2.writeEndArray();

        assertNotNull(tb2.toString());
    }

    @Test
    public void testSerializeAndDeserialize() throws Throwable {
        TokenBuffer tb = new TokenBuffer(null, false);
        tb.writeStartObject();
        tb.writeFieldName("a");
        tb.writeNumber(10);
        tb.writeEndObject();

        StringWriter sw = new StringWriter();
        ObjectMapper mapper = new ObjectMapper();
        JsonGenerator jgen = mapper.getFactory().createGenerator(sw);
        tb.serialize(jgen);
        jgen.close();

        JsonParser jp = tb.asParser();
        TokenBuffer tbDeserialized = new TokenBuffer(null, false);
        tbDeserialized.deserialize(jp, (DeserializationContext) null);
        assertNotNull(tbDeserialized.toString());
    }

    @Test
    public void testParserMethods() throws Throwable {
        TokenBuffer tb = new TokenBuffer(null, false);
        tb.writeStartArray();
        tb.writeNumber(100);
        tb.writeString("hello");
        tb.writeNumber(123.45d);
        tb.writeNumber(123.45f);
        tb.writeNumber(BigInteger.TEN);
        tb.writeNumber(BigDecimal.ONE);
        tb.writeNull();
        tb.writeObject("embedded");
        tb.writeEndArray();

        JsonParser p = tb.asParser();
        assertNotNull(p.version());
        assertNull(p.getCodec());
        p.setCodec(null);
        assertNull(p.peekNextToken());

        assertNotNull(p.nextToken()); // START_ARRAY
        assertNotNull(p.nextToken()); // VALUE_NUMBER_INT
        assertEquals(100, p.getIntValue());
        assertEquals(100L, p.getLongValue());
        assertEquals(100.0d, p.getDoubleValue(), 0.001);
        assertEquals(100.0f, p.getFloatValue(), 0.001);
        assertEquals(BigInteger.valueOf(100), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(100), p.getDecimalValue());
        assertEquals(JsonParser.NumberType.INT, p.getNumberType());

        assertNotNull(p.nextToken()); // VALUE_STRING
        assertEquals("hello", p.getText());
        assertNotNull(p.getTextCharacters());
        assertEquals(5, p.getTextLength());
        assertEquals(0, p.getTextOffset());
        assertFalse(p.hasTextCharacters());

        assertNotNull(p.nextToken()); // VALUE_NUMBER_FLOAT (double)
        assertEquals(JsonParser.NumberType.DOUBLE, p.getNumberType());

        assertNotNull(p.nextToken()); // VALUE_NUMBER_FLOAT (float)
        assertEquals(JsonParser.NumberType.FLOAT, p.getNumberType());

        assertNotNull(p.nextToken()); // VALUE_NUMBER_INT (BigInteger)
        assertEquals(JsonParser.NumberType.BIG_INTEGER, p.getNumberType());
        assertEquals(BigInteger.TEN, p.getBigIntegerValue());

        assertNotNull(p.nextToken()); // VALUE_NUMBER_FLOAT (BigDecimal)
        assertEquals(JsonParser.NumberType.BIG_DECIMAL, p.getNumberType());
        assertEquals(BigDecimal.ONE, p.getDecimalValue());

        assertNotNull(p.nextToken()); // VALUE_NULL
        assertNull(p.getNumberValue());

        assertNotNull(p.nextToken()); // VALUE_EMBEDDED_OBJECT
        assertEquals("embedded", p.getEmbeddedObject());

        assertNotNull(p.nextToken()); // END_ARRAY
        assertNull(p.nextToken());

        p.close();
        assertTrue(p.isClosed());
    }

    @Test
    public void testParserBinaryAndLocation() throws Throwable {
        TokenBuffer tb = new TokenBuffer(null, false);
        tb.writeStartArray();
        tb.writeBinary(com.fasterxml.jackson.core.Base64Variants.MIME, new byte[]{10, 20}, 0, 2);
        tb.writeString("AQID"); // base64 for [1, 2, 3]
        tb.writeString((String) null);
        tb.writeEndArray();

        JsonParser p = tb.asParser();
        p.nextToken(); // START_ARRAY

        p.nextToken(); // VALUE_EMBEDDED_OBJECT (byte[])
        byte[] bytes = p.getBinaryValue(com.fasterxml.jackson.core.Base64Variants.MIME);
        assertNotNull(bytes);

        p.nextToken(); // VALUE_STRING
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int read = p.readBinaryValue(com.fasterxml.jackson.core.Base64Variants.MIME, out);
        assertEquals(3, read);

        p.nextToken(); // VALUE_STRING (null text)
        assertNull(p.getBinaryValue(com.fasterxml.jackson.core.Base64Variants.MIME));

        p.setLocation(null);
        assertNotNull(p.getTokenLocation());
        assertNotNull(p.getCurrentLocation());

        p.close();
    }

    @Test
    public void testSegmentOperations() throws Throwable {
        TokenBuffer.Segment seg = new TokenBuffer.Segment();
        assertNull(seg.next());
        assertFalse(seg.hasIds());
        assertNull(seg.findObjectId(0));
        assertNull(seg.findTypeId(0));
        
        TokenBuffer.Segment nextSeg = seg.append(0, JsonToken.START_OBJECT, "obj", "type");
        assertNotNull(nextSeg);
        assertNotNull(seg.type(0));
        assertEquals(0, seg.rawType(0));
        assertNotNull(seg.get(0));

        // Fill up segment to force next segment creation via append at index >= 16
        TokenBuffer.Segment current = seg;
        for (int i = 0; i < 20; i++) {
            current = current.append(i, JsonToken.VALUE_NULL);
        }
        assertNotNull(current);
    }
}