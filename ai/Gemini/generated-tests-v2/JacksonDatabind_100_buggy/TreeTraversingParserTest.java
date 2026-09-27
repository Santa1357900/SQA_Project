package com.fasterxml.jackson.databind.node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.databind.JsonNode;

public class TreeTraversingParserTest {

    @Test
    public void testArrayNodeTraversal() throws Throwable {
        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
        arrayNode.add("testValue");
        arrayNode.add(123);

        TreeTraversingParser parser = new TreeTraversingParser(arrayNode);
        assertFalse(parser.isClosed());
        assertNotNull(parser.version());

        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("testValue", parser.getText());
        assertEquals(9, parser.getTextCharacters().length);
        assertEquals(9, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        assertFalse(parser.hasTextCharacters());

        assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals("123", parser.getText());
        assertEquals(123, parser.getIntValue());
        assertEquals(123L, parser.getLongValue());
        assertEquals(123.0, parser.getDoubleValue(), 0.0);
        assertEquals(123.0f, parser.getFloatValue(), 0.0f);
        assertEquals(BigInteger.valueOf(123), parser.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(123), parser.getDecimalValue());
        assertEquals(JsonParser.NumberType.INT, parser.getNumberType());
        assertEquals(Integer.valueOf(123), parser.getNumberValue());

        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        assertNull(parser.nextToken());
        assertTrue(parser.isClosed());

        parser.close();
    }

    @Test
    public void testObjectNodeTraversal() throws Throwable {
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("fieldKey", "fieldVal");

        TreeTraversingParser parser = new TreeTraversingParser(objectNode);
        
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("fieldKey", parser.getCurrentName());
        assertEquals("fieldKey", parser.getText());

        parser.overrideCurrentName("overriddenKey");
        assertEquals("overriddenKey", parser.getCurrentName());

        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("fieldVal", parser.getText());

        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
        
        parser.close();
    }

    @Test
    public void testValueNodeTraversal() throws Throwable {
        TextNode textNode = TextNode.valueOf("rootText");
        TreeTraversingParser parser = new TreeTraversingParser(textNode, null);

        assertNull(parser.getCodec());
        ObjectCodec dummyCodec = new com.fasterxml.jackson.databind.ObjectMapper();
        parser.setCodec(dummyCodec);
        assertEquals(dummyCodec, parser.getCodec());

        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("rootText", parser.getText());
        assertNotNull(parser.getParsingContext());
        assertEquals(com.fasterxml.jackson.core.JsonLocation.NA, parser.getTokenLocation());
        assertEquals(com.fasterxml.jackson.core.JsonLocation.NA, parser.getCurrentLocation());

        assertNull(parser.nextToken());
        parser.close();
    }

    @Test
    public void testSkipChildrenObject() throws Throwable {
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("a", 1);

        TreeTraversingParser parser = new TreeTraversingParser(objectNode);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        JsonParser skipped = parser.skipChildren();
        assertNotNull(skipped);
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    @Test
    public void testSkipChildrenArray() throws Throwable {
        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
        arrayNode.add(1);

        TreeTraversingParser parser = new TreeTraversingParser(arrayNode);
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        JsonParser skipped = parser.skipChildren();
        assertNotNull(skipped);
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        parser.close();
    }

    @Test
    public void testNumericNodeEdgeCasesAndNaN() throws Throwable {
        NumericNode doubleNode = DoubleNode.valueOf(Double.NaN);
        TreeTraversingParser parser = new TreeTraversingParser(doubleNode);

        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertTrue(parser.isNaN());
        assertEquals("NaN", parser.getText());
        parser.close();

        NumericNode intNode = IntNode.valueOf(42);
        TreeTraversingParser parser2 = new TreeTraversingParser(intNode);
        assertEquals(JsonToken.VALUE_NUMBER_INT, parser2.nextToken());
        assertFalse(parser2.isNaN());
        parser2.close();
    }

    @Test
    public void testEmbeddedAndBinaryObjects() throws Throwable {
        byte[] binaryData = new byte[] { 1, 2, 3, 4 };
        BinaryNode binaryNode = BinaryNode.valueOf(binaryData);
        TreeTraversingParser parser = new TreeTraversingParser(binaryNode);

        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, parser.nextToken());
        assertArrayEquals(binaryData, parser.getBinaryValue(Base64Variants.MIME));
        assertNotNull(parser.getText()); // Base64 text representation

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written = parser.readBinaryValue(Base64Variants.MIME, out);
        assertEquals(4, written);
        assertArrayEquals(binaryData, out.toByteArray());

        POJONode pojoNode = new POJONode(binaryData);
        TreeTraversingParser parserPojo = new TreeTraversingParser(pojoNode);
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, parserPojo.nextToken());
        assertArrayEquals(binaryData, parserPojo.getBinaryValue(Base64Variants.MIME));
        assertEquals(binaryData, parserPojo.getEmbeddedObject());

        parser.close();
        parserPojo.close();
    }

    @Test
    public void testClosedParserBehaviors() throws Throwable {
        TextNode textNode = TextNode.valueOf("hello");
        TreeTraversingParser parser = new TreeTraversingParser(textNode);
        parser.close();

        assertNull(parser.getText());
        assertNull(parser.getEmbeddedObject());
        assertFalse(parser.isNaN());
        assertNull(parser.getCurrentName());
    }

    @Test
    public void testCurrentNumericNodeException() throws Throwable {
        TextNode textNode = TextNode.valueOf("notANumber");
        TreeTraversingParser parser = new TreeTraversingParser(textNode);
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());

        boolean exceptionThrown = false;
        try {
            parser.getIntValue();
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("not numeric"));
        }
        assertTrue(exceptionThrown);
        parser.close();
    }

    @Test
    public void testEmptyContainerOptimization() throws Throwable {
        ArrayNode emptyArray = JsonNodeFactory.instance.arrayNode();
        TreeTraversingParser parser = new TreeTraversingParser(emptyArray);
        
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        assertNull(parser.nextToken());
        parser.close();

        ObjectNode emptyObject = JsonNodeFactory.instance.objectNode();
        TreeTraversingParser parserObj = new TreeTraversingParser(emptyObject);
        
        assertEquals(JsonToken.START_OBJECT, parserObj.nextToken());
        assertEquals(JsonToken.END_OBJECT, parserObj.nextToken());
        assertNull(parserObj.nextToken());
        parserObj.close();
    }
}