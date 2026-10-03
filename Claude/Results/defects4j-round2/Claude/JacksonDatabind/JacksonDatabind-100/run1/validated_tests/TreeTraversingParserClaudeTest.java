package com.fasterxml.jackson.databind.node;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Base64Variants;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TreeTraversingParserClaudeTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // Covers constructor branch for array node: sets _nextToken=START_ARRAY
    @Test
    public void testConstructorOneArg_arrayNode_firstTokenStartArray() throws Throwable {
        ArrayNode arr = mapper.createArrayNode();
        arr.add("x");
        TreeTraversingParser parser = new TreeTraversingParser(arr);
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
    }

    // Covers constructor branch for object node plus two-arg constructor setting codec
    @Test
    public void testConstructorTwoArg_objectNode_codecSetAndFirstTokenStartObject() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("a", "b");
        TreeTraversingParser parser = new TreeTraversingParser(obj, mapper);
        assertSame(mapper, parser.getCodec());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
    }

    // Covers constructor else-branch for plain value node (RootCursor, no cached _nextToken)
    @Test
    public void testConstructor_valueNode_firstTokenIsValueTokenDirectly() throws Throwable {
        TextNode text = TextNode.valueOf("hello");
        TreeTraversingParser parser = new TreeTraversingParser(text);
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
    }

    // Covers setCodec/getCodec accessors
    @Test
    public void testSetCodec_and_getCodec_roundTrip() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("x"));
        assertNull(parser.getCodec());
        parser.setCodec(mapper);
        assertSame(mapper, parser.getCodec());
    }

    // Covers version(): must return a known, non-null Version per contract
    @Test
    public void testVersion_notNullAndKnown() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("x"));
        Version v = parser.version();
        assertNotNull(v);
        assertFalse(v.isUnknownVersion());
    }

    // Covers close(): sets closed flag, clears cursor/current token/name
    @Test
    public void testClose_setsClosedAndClearsCurrentTokenAndName() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("a", "b");
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        parser.nextToken();
        parser.close();
        assertTrue(parser.isClosed());
        assertNull(parser.getCurrentName());
        assertNull(parser.getText());
    }

    // Covers isClosed() initial state false
    @Test
    public void testIsClosed_initiallyFalse() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("x"));
        assertFalse(parser.isClosed());
    }

    // Covers nextToken() for empty root object: START_OBJECT, END_OBJECT, then null/closed
    @Test
    public void testNextToken_emptyRootObject_returnsStartThenEndThenNull() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
        assertTrue(parser.isClosed());
    }

    // Covers full traversal of object with one string field: FIELD_NAME + VALUE_STRING + names
    @Test
    public void testNextToken_objectWithOneStringField_fullSequence() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("name", "value1");
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("name", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("value1", parser.getText());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
    }

    // Covers descending into a non-empty nested array child (_startContainer true, has children)
    @Test
    public void testNextToken_nestedNonEmptyArrayInObject_descendsIntoChild() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        ArrayNode arr = obj.putArray("arr");
        arr.add("x");
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("x", parser.getText());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
    }

    // Covers _startContainer optimization for empty nested array (currentHasChildren false)
    @Test
    public void testNextToken_nestedEmptyArrayInObject_startContainerOptimizationEndArray() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        obj.putArray("arr");
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        parser.nextToken();
        parser.nextToken();
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
    }

    // Covers ternary true-branch of the empty-container optimization (nested empty object)
    @Test
    public void testNextToken_nestedEmptyObjectInObject_startContainerOptimizationEndObject() throws Throwable {
        ObjectNode outer = mapper.createObjectNode();
        outer.putObject("child");
        TreeTraversingParser parser = new TreeTraversingParser(outer);
        parser.nextToken();
        parser.nextToken();
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
    }

    // Covers skipChildren() when current token is START_OBJECT
    @Test
    public void testSkipChildren_onStartObject_setsEndObjectAndReturnsSameParser() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("a", 1);
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        parser.nextToken();
        JsonParser returned = parser.skipChildren();
        assertSame(parser, returned);
        assertEquals(JsonToken.END_OBJECT, parser.getCurrentToken());
    }

    // Covers skipChildren() when current token is START_ARRAY
    @Test
    public void testSkipChildren_onStartArray_setsEndArrayAndReturnsSameParser() throws Throwable {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(1);
        TreeTraversingParser parser = new TreeTraversingParser(arr);
        parser.nextToken();
        JsonParser returned = parser.skipChildren();
        assertSame(parser, returned);
        assertEquals(JsonToken.END_ARRAY, parser.getCurrentToken());
    }

    // Covers skipChildren() no-op branch for a non-container current token
    @Test
    public void testSkipChildren_onNonContainerToken_noStateChange() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("x"));
        parser.nextToken();
        parser.skipChildren();
        assertEquals(JsonToken.VALUE_STRING, parser.getCurrentToken());
    }

    // Covers overrideCurrentName(): delegates to cursor, changes value returned by getCurrentName()
    @Test
    public void testOverrideCurrentName_changesReturnedName() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("orig", "v");
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        parser.nextToken();
        parser.nextToken();
        parser.overrideCurrentName("renamed");
        assertEquals("renamed", parser.getCurrentName());
    }

    // Covers getParsingContext(), getTokenLocation(), getCurrentLocation()
    @Test
    public void testGetParsingContextAndLocations_notNullAndNA() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("x"));
        assertNotNull(parser.getParsingContext());
        assertSame(JsonLocation.NA, parser.getTokenLocation());
        assertSame(JsonLocation.NA, parser.getCurrentLocation());
    }



    // Covers getText() FIELD_NAME case
    @Test
    public void testGetText_fieldName_returnsFieldName() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("key1", "v");
        TreeTraversingParser parser = new TreeTraversingParser(obj);
        parser.nextToken();
        parser.nextToken();
        assertEquals("key1", parser.getText());
    }

    // Covers getText() VALUE_STRING case
    @Test
    public void testGetText_valueString_returnsTextValue() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("abc"));
        parser.nextToken();
        assertEquals("abc", parser.getText());
    }

    // Covers getText() VALUE_NUMBER_INT case
    @Test
    public void testGetText_valueNumberInt_returnsStringOfNumber() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(IntNode.valueOf(42));
        parser.nextToken();
        assertEquals("42", parser.getText());
    }

    // Covers getText() VALUE_NUMBER_FLOAT case
    @Test
    public void testGetText_valueNumberFloat_returnsStringOfNumber() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(DoubleNode.valueOf(3.5));
        parser.nextToken();
        assertEquals(String.valueOf(3.5), parser.getText());
    }

    // Covers getText() VALUE_EMBEDDED_OBJECT binary case: base64 text decodes to original bytes
    @Test
    public void testGetText_embeddedBinary_returnsBase64DecodableToOriginal() throws Throwable {
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        TreeTraversingParser parser = new TreeTraversingParser(BinaryNode.valueOf(data));
        parser.nextToken();
        String text = parser.getText();
        assertNotNull(text);
        byte[] decoded = Base64.getDecoder().decode(text);
        assertArrayEquals(data, decoded);
    }

    // Covers getText() closed-parser short-circuit returning null
    @Test
    public void testGetText_closedParser_returnsNull() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("x"));
        parser.nextToken();
        parser.close();
        assertNull(parser.getText());
    }

    // Covers getText() default-case fallthrough for boolean token via _currToken.asString()
    @Test
    public void testGetText_booleanDefaultCase_returnsTokenAsString() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(BooleanNode.valueOf(true));
        parser.nextToken();
        assertEquals("true", parser.getText());
    }

    // Covers getTextCharacters/getTextLength/getTextOffset/hasTextCharacters derived from getText()
    @Test
    public void testGetTextDerivedAccessors_matchGetText() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("hi"));
        parser.nextToken();
        String text = parser.getText();
        assertArrayEquals(text.toCharArray(), parser.getTextCharacters());
        assertEquals(text.length(), parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        assertFalse(parser.hasTextCharacters());
    }

    // Covers getNumberType/getBigIntegerValue/getDecimalValue/getLongValue/getIntValue/getNumberValue
    @Test
    public void testNumberAccessors_intNode_returnsExpectedValues() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(IntNode.valueOf(42));
        parser.nextToken();
        assertEquals(NumberType.INT, parser.getNumberType());
        assertEquals(BigInteger.valueOf(42), parser.getBigIntegerValue());
        assertEquals(0, parser.getDecimalValue().compareTo(BigDecimal.valueOf(42)));
        assertEquals(42L, parser.getLongValue());
        assertEquals(42, parser.getIntValue());
        assertEquals(42, parser.getNumberValue().intValue());
    }

    // Covers getDoubleValue/getFloatValue for a double-valued node
    @Test
    public void testDoubleAccessors_doubleNode_returnsExpectedValues() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(DoubleNode.valueOf(3.5));
        parser.nextToken();
        assertEquals(3.5, parser.getDoubleValue(), 1e-9);
        assertEquals(3.5f, parser.getFloatValue(), 1e-6f);
    }

    // Covers currentNumericNode() throw when current node is not numeric
    @Test
    public void testCurrentNumericNode_nonNumericNode_throwsJsonParseException() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("abc"));
        parser.nextToken();
        try {
            parser.getIntValue();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("not numeric"));
        }
    }

    // Covers currentNumericNode() throw when parser is closed (currentNode() is null)
    @Test
    public void testCurrentNumericNode_closedParser_throwsJsonParseException() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(IntNode.valueOf(1));
        parser.nextToken();
        parser.close();
        try {
            parser.getIntValue();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("not numeric"));
        }
    }

    // Covers getEmbeddedObject() for a POJO node
    @Test
    public void testGetEmbeddedObject_pojoNode_returnsPojo() throws Throwable {
        Object payload = "payload-object";
        TreeTraversingParser parser = new TreeTraversingParser(new POJONode(payload));
        parser.nextToken();
        assertSame(payload, parser.getEmbeddedObject());
    }

    // Covers getEmbeddedObject() returning null for non-embedded node and for closed parser
    @Test
    public void testGetEmbeddedObject_nonEmbeddedOrClosed_returnsNull() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("abc"));
        parser.nextToken();
        assertNull(parser.getEmbeddedObject());
        parser.close();
        assertNull(parser.getEmbeddedObject());
    }

    // Covers isNaN() true branch for a NaN numeric node
    @Test
    public void testIsNaN_nanDoubleNode_returnsTrue() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(DoubleNode.valueOf(Double.NaN));
        parser.nextToken();
        assertTrue(parser.isNaN());
    }

    // Covers isNaN() false branch for a normal numeric node and for a non-numeric node
    @Test
    public void testIsNaN_normalOrNonNumeric_returnsFalse() throws Throwable {
        TreeTraversingParser p1 = new TreeTraversingParser(DoubleNode.valueOf(1.0));
        p1.nextToken();
        assertFalse(p1.isNaN());
        TreeTraversingParser p2 = new TreeTraversingParser(TextNode.valueOf("x"));
        p2.nextToken();
        assertFalse(p2.isNaN());
    }

    // Covers getBinaryValue() direct binary node path and null result for non-binary node
    @Test
    public void testGetBinaryValue_binaryAndNonBinary_behaveCorrectly() throws Throwable {
        byte[] data = new byte[] {9, 8, 7};
        TreeTraversingParser p1 = new TreeTraversingParser(BinaryNode.valueOf(data));
        p1.nextToken();
        assertArrayEquals(data, p1.getBinaryValue(Base64Variants.getDefaultVariant()));
        TreeTraversingParser p2 = new TreeTraversingParser(IntNode.valueOf(5));
        p2.nextToken();
        assertNull(p2.getBinaryValue(Base64Variants.getDefaultVariant()));
    }

    // Covers getBinaryValue() decoding base64 text via TextNode.binaryValue()
    @Test
    public void testGetBinaryValue_textNodeBase64_decodesToExpectedBytes() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(TextNode.valueOf("aGVsbG8="));
        parser.nextToken();
        byte[] actual = parser.getBinaryValue(Base64Variants.getDefaultVariant());
        assertArrayEquals("hello".getBytes(), actual);
    }

    // Covers getBinaryValue() POJO-holding-byte-array branch
    @Test
    public void testGetBinaryValue_pojoNodeWithByteArray_returnsBytes() throws Throwable {
        byte[] data = new byte[] {1, 2, 3};
        TreeTraversingParser parser = new TreeTraversingParser(new POJONode(data));
        parser.nextToken();
        byte[] actual = parser.getBinaryValue(Base64Variants.getDefaultVariant());
        assertArrayEquals(data, actual);
    }

    // Covers readBinaryValue() writing available data and returning its length
    @Test
    public void testReadBinaryValue_writesDataAndReturnsLength() throws Throwable {
        byte[] data = new byte[] {4, 5, 6, 7};
        TreeTraversingParser parser = new TreeTraversingParser(BinaryNode.valueOf(data));
        parser.nextToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = parser.readBinaryValue(Base64Variants.getDefaultVariant(), out);
        assertEquals(data.length, len);
        assertArrayEquals(data, out.toByteArray());
    }

    // Covers readBinaryValue() returning 0 and writing nothing when no binary data is available
    @Test
    public void testReadBinaryValue_nullData_returnsZeroAndWritesNothing() throws Throwable {
        TreeTraversingParser parser = new TreeTraversingParser(IntNode.valueOf(9));
        parser.nextToken();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = parser.readBinaryValue(Base64Variants.getDefaultVariant(), out);
        assertEquals(0, len);
        assertEquals(0, out.toByteArray().length);
    }
}
