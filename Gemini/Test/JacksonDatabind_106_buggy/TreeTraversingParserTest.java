package com.fasterxml.jackson.databind.node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TreeTraversingParserTest {

    @Test
    public void testLifecycleAndCodec() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode();
        TreeTraversingParser parser = new TreeTraversingParser(node, mapper);

        assertNotNull(parser.version());
        assertEquals(mapper, parser.getCodec());

        ObjectCodec newCodec = new ObjectMapper();
        parser.setCodec(newCodec);
        assertEquals(newCodec, parser.getCodec());

        assertFalse(parser.isClosed());
        parser.close();
        assertTrue(parser.isClosed());
        assertNull(parser.nextToken());
    }

    @Test
    public void testRootValueTraversing() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.getNodeFactory().textNode("hello");
        TreeTraversingParser parser = new TreeTraversingParser(node);

        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("hello", parser.getText());
        assertEquals(5, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        assertNotNull(parser.getTextCharacters());
        assertFalse(parser.hasTextCharacters());
        assertNull(parser.getCurrentName());
        assertNotNull(parser.getParsingContext());
        assertEquals(JsonLocation.NA, parser.getTokenLocation());
        assertEquals(JsonLocation.NA, parser.getCurrentLocation());

        assertNull(parser.nextToken());
        assertTrue(parser.isClosed());
    }

    @Test
    public void testObjectTraversing() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("key", "val");
        TreeTraversingParser parser = new TreeTraversingParser(obj);

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("key", parser.getCurrentName());
        assertEquals("key", parser.getText());

        parser.overrideCurrentName("newKey");
        assertEquals("newKey", parser.getCurrentName());

        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("val", parser.getText());

        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
    }

    @Test
    public void testArrayTraversing() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();
        arr.add(123);
        TreeTraversingParser parser = new TreeTraversingParser(arr);

        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals("123", parser.getText());
        assertEquals(123, parser.getIntValue());
        assertEquals(123L, parser.getLongValue());
        assertEquals(BigInteger.valueOf(123), parser.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(123), parser.getDecimalValue());
        assertEquals(123.0, parser.getDoubleValue(), 0.0);
        assertEquals(123.0f, parser.getFloatValue(), 0.0f);
        assertEquals(123, parser.getNumberValue().intValue());
        assertNotNull(parser.getNumberType());

        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        assertNull(parser.nextToken());
    }

    @Test
    public void testSkipChildren() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("child", mapper.createArrayNode().add(1));
        
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        JsonParser skipped = parser.skipChildren();
        assertNotNull(skipped);
        assertEquals(JsonToken.END_ARRAY, parser.currentToken());
    }

    @Test
    public void testTextAndEmbeddedAndBinary() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        
        // Binary node
        byte[] binaryData = new byte[] { 1, 2, 3 };
        BinaryNode binNode = mapper.getNodeFactory().binaryNode(binaryData);
        TreeTraversingParser parser = new TreeTraversingParser(binNode);
        
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, parser.nextToken());
        assertArrayEquals(binaryData, parser.getBinaryValue(Base64Variants.MIME));
        assertNotNull(parser.getEmbeddedObject());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written = parser.readBinaryValue(Base64Variants.MIME, out);
        assertEquals(3, written);
        assertArrayEquals(binaryData, out.toByteArray());
    }

    @Test
    public void testPojoNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        POJONode pojoNode = new POJONode("test-pojo");
        TreeTraversingParser parser = new TreeTraversingParser(pojoNode);

        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, parser.nextToken());
        assertEquals("test-pojo", parser.getEmbeddedObject());
    }

    @Test
    public void testNaNSupport() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        NumericNode nanNode = DoubleNode.valueOf(Double.NaN);
        TreeTraversingParser parser = new TreeTraversingParser(nanNode);

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertTrue(parser.isNaN());
    }

    @Test
    public void testNumericAccessorErrors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode strNode = mapper.getNodeFactory().textNode("not-a-number");
        TreeTraversingParser parser = new TreeTraversingParser(strNode);

        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        try {
            parser.getIntValue();
            fail("Expected JsonParseException");
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            assertTrue(e.getMessage().contains("not numeric"));
        }
    }

    @Test(expected = com.fasterxml.jackson.core.JsonParseException.class)
    public void testHandleEOF() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        TreeTraversingParser parser = new TreeTraversingParser(mapper.getNodeFactory().textNode("abc"));
        parser._handleEOF();
    }
}