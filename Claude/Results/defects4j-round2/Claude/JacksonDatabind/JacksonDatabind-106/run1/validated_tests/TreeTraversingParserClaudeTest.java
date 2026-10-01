package com.fasterxml.jackson.databind.node;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TreeTraversingParserClaudeTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // constructor: array node -> first token via _nextToken branch is START_ARRAY
    @Test
    public void testConstructor_arrayNode_firstTokenIsStartArray() throws Throwable {
        JsonNode node = mapper.readTree("[1]");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
    }

    // constructor: object node -> first token via _nextToken branch is START_OBJECT
    @Test
    public void testConstructor_objectNode_firstTokenIsStartObject() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
    }

    // constructor: scalar (value) node branch does not set _nextToken -> current token null before nextToken()
    @Test
    public void testConstructor_scalarNode_currentTokenNullBeforeNextToken() throws Throwable {
        JsonNode node = mapper.readTree("123");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertNull(p.getCurrentToken());
    }

    // single-arg constructor sets codec to null
    @Test
    public void testConstructorSingleArg_codecIsNull() throws Throwable {
        JsonNode node = mapper.readTree("1");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertNull(p.getCodec());
    }

    // two-arg constructor stores given codec
    @Test
    public void testConstructorWithCodec_getCodecReturnsSameInstance() throws Throwable {
        JsonNode node = mapper.readTree("1");
        TreeTraversingParser p = new TreeTraversingParser(node, mapper);
        assertSame(mapper, p.getCodec());
    }

    // setCodec updates the codec reference returned by getCodec
    @Test
    public void testSetCodec_updatesCodecReference() throws Throwable {
        JsonNode node = mapper.readTree("1");
        TreeTraversingParser p = new TreeTraversingParser(node);
        ObjectMapper another = new ObjectMapper();
        p.setCodec(another);
        assertSame(another, p.getCodec());
    }

    // version() must return a non-null Version instance
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        JsonNode node = mapper.readTree("1");
        TreeTraversingParser p = new TreeTraversingParser(node);
        Version v = p.version();
        assertNotNull(v);
    }

    // close(): sets closed flag, clears current name state, and is idempotent
    @Test
    public void testClose_setsClosedClearsStateAndIsIdempotent() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.close();
        assertTrue(p.isClosed());
        assertNull(p.getCurrentName());
        p.close();
        assertTrue(p.isClosed());
    }

    // isClosed() is false before any close() call
    @Test
    public void testIsClosed_falseBeforeClose() throws Throwable {
        JsonNode node = mapper.readTree("1");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertFalse(p.isClosed());
    }

    // nextToken(): full traversal of a multi-element array, 0/1/many-element loop coverage
    @Test
    public void testNextToken_arrayTraversal_fullSequence() throws Throwable {
        JsonNode node = mapper.readTree("[1,2,3]");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }

    // nextToken(): empty array triggers the "currentHasChildren() == false" optimization branch (root level)
    @Test
    public void testNextToken_emptyArray_immediateEnd() throws Throwable {
        JsonNode node = mapper.readTree("[]");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }

    // nextToken(): empty object triggers same optimization branch for objects
    @Test
    public void testNextToken_emptyObject_immediateEnd() throws Throwable {
        JsonNode node = mapper.readTree("{}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // nextToken(): object with multiple fields covers FIELD_NAME/value alternation loop (2 iterations)
    @Test
    public void testNextToken_objectTraversal_fieldNamesAndValues() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1,\"b\":\"x\"}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("a", p.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("b", p.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
    }

    // nextToken(): nested array inside object exercises the _startContainer descend branch
    @Test
    public void testNextToken_nestedArrayInObject_startContainerHandling() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":[1,2]}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // nextToken(): scalar root value eventually terminates the stream with null
    @Test
    public void testNextToken_scalarRoot_eventuallyReturnsNull() throws Throwable {
        JsonNode node = mapper.readTree("\"hello\"");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        JsonToken t = null;
        int guard = 0;
        while (guard < 10) {
            t = p.nextToken();
            if (t == null) {
                break;
            }
            guard++;
        }
        assertNull(t);
    }

    // skipChildren(): on START_OBJECT immediately sets END_OBJECT and disables descent
    @Test
    public void testSkipChildren_onStartObject_setsEndObjectToken() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.skipChildren();
        assertEquals(JsonToken.END_OBJECT, p.getCurrentToken());
    }

    // skipChildren(): on START_ARRAY immediately sets END_ARRAY
    @Test
    public void testSkipChildren_onStartArray_setsEndArrayToken() throws Throwable {
        JsonNode node = mapper.readTree("[1,2]");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.skipChildren();
        assertEquals(JsonToken.END_ARRAY, p.getCurrentToken());
    }

    // skipChildren(): on a non-container token, current token is left unchanged
    @Test
    public void testSkipChildren_onNonContainerToken_noStateChange() throws Throwable {
        JsonNode node = mapper.readTree("42");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.skipChildren();
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.getCurrentToken());
    }

    // getCurrentName(): returns field name while positioned on FIELD_NAME token
    @Test
    public void testGetCurrentName_onFieldNameToken_returnsName() throws Throwable {
        JsonNode node = mapper.readTree("{\"k\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.nextToken();
        assertEquals("k", p.getCurrentName());
    }

    // getCurrentName(): after close(), _nodeCursor is null so name must be null
    @Test
    public void testGetCurrentName_afterClose_returnsNull() throws Throwable {
        JsonNode node = mapper.readTree("{\"k\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.close();
        assertNull(p.getCurrentName());
    }

    // overrideCurrentName(): changes the name reported by getCurrentName()
    @Test
    public void testOverrideCurrentName_changesFieldName() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.nextToken();
        p.overrideCurrentName("b");
        assertEquals("b", p.getCurrentName());
    }

    // overrideCurrentName(): guarded no-op when _nodeCursor already null (closed)
    @Test
    public void testOverrideCurrentName_afterClose_noExceptionAndNameStaysNull() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.close();
        p.overrideCurrentName("x");
        assertNull(p.getCurrentName());
    }

    // getParsingContext(): non-null while open, null after close (nodeCursor cleared)
    @Test
    public void testGetParsingContext_nonNullWhileOpen_nullAfterClose() throws Throwable {
        JsonNode node = mapper.readTree("[1]");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        JsonStreamContext ctx = p.getParsingContext();
        assertNotNull(ctx);
        p.close();
        assertNull(p.getParsingContext());
    }

    // getTokenLocation() and getCurrentLocation() always return JsonLocation.NA
    @Test
    public void testLocations_alwaysReturnNA() throws Throwable {
        JsonNode node = mapper.readTree("1");
        TreeTraversingParser p = new TreeTraversingParser(node);
        assertEquals(JsonLocation.NA, p.getTokenLocation());
        assertEquals(JsonLocation.NA, p.getCurrentLocation());
    }



    // getText(): _closed branch returns null regardless of prior token
    @Test
    public void testGetText_afterClose_returnsNull() throws Throwable {
        JsonNode node = mapper.readTree("123");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.close();
        assertNull(p.getText());
    }

    // getText(): FIELD_NAME case returns the current field name
    @Test
    public void testGetText_fieldNameToken_returnsFieldName() throws Throwable {
        JsonNode node = mapper.readTree("{\"k\":1}");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.nextToken();
        assertEquals("k", p.getText());
    }

    // getText(): VALUE_STRING case returns the node's text value
    @Test
    public void testGetText_stringValueToken_returnsStringValue() throws Throwable {
        JsonNode node = mapper.readTree("\"hi\"");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals("hi", p.getText());
    }

    // getText(): VALUE_NUMBER_INT case returns numeric string form
    @Test
    public void testGetText_numberIntToken_returnsNumericString() throws Throwable {
        JsonNode node = mapper.readTree("42");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals("42", p.getText());
    }

    // getText(): default branch uses JsonToken.asString() for boolean tokens
    @Test
    public void testGetText_booleanToken_returnsTrueString() throws Throwable {
        JsonNode node = mapper.readTree("true");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals("true", p.getText());
    }

    // getTextCharacters(): returns char array matching the textual content
    @Test
    public void testGetTextCharacters_returnsCharArrayOfText() throws Throwable {
        JsonNode node = mapper.readTree("\"hello\"");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        char[] expected = "hello".toCharArray();
        assertArrayEquals(expected, p.getTextCharacters());
    }

    // getTextLength()/getTextOffset()/hasTextCharacters(): simple accessor group
    @Test
    public void testTextAccessors_lengthOffsetAndHasChars() throws Throwable {
        JsonNode node = mapper.readTree("\"hello\"");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals(5, p.getTextLength());
        assertEquals(0, p.getTextOffset());
        assertFalse(p.hasTextCharacters());
    }

    // getNumberType(): integer literal maps to NumberType.INT
    @Test
    public void testGetNumberType_intNode_returnsInt() throws Throwable {
        JsonNode node = mapper.readTree("42");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals(JsonParser.NumberType.INT, p.getNumberType());
    }

    // getNumberType(): floating literal maps to NumberType.DOUBLE
    @Test
    public void testGetNumberType_doubleNode_returnsDouble() throws Throwable {
        JsonNode node = mapper.readTree("1.5");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals(JsonParser.NumberType.DOUBLE, p.getNumberType());
    }

    // getBigIntegerValue(): returns exact BigInteger for an integer node
    @Test
    public void testGetBigIntegerValue_returnsCorrectValue() throws Throwable {
        JsonNode node = mapper.readTree("123");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals(BigInteger.valueOf(123), p.getBigIntegerValue());
    }

    // getDoubleValue(): returns correct double for a simple int node
    @Test
    public void testGetDoubleValue_returnsCorrectValue() throws Throwable {
        JsonNode node = mapper.readTree("5");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals(5.0, p.getDoubleValue(), 1e-9);
    }

    // getFloatValue(): returns correct float value
    @Test
    public void testGetFloatValue_returnsCorrectValue() throws Throwable {
        JsonNode node = mapper.readTree("2.5");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals(2.5f, p.getFloatValue(), 1e-6f);
    }

    // getIntValue(): returns correct int value
    @Test
    public void testGetIntValue_returnsCorrectValue() throws Throwable {
        JsonNode node = mapper.readTree("100");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertEquals(100, p.getIntValue());
    }



    // getNumberValue(): returns a Number whose int value matches the node
    @Test
    public void testGetNumberValue_returnsNumberInstance() throws Throwable {
        JsonNode node = mapper.readTree("7");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        Number num = p.getNumberValue();
        assertNotNull(num);
        assertEquals(7, num.intValue());
    }

    // currentNumericNode(): non-numeric current token must throw JsonParseException
    @Test
    public void testCurrentNumericNode_nonNumericToken_throwsJsonParseException() throws Throwable {
        JsonNode node = mapper.readTree("\"abc\"");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        try {
            p.getDoubleValue();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().indexOf("numeric") >= 0);
        }
    }

    // getEmbeddedObject(): returns null when current node is neither pojo nor binary
    @Test
    public void testGetEmbeddedObject_nonEmbeddedToken_returnsNull() throws Throwable {
        JsonNode node = mapper.readTree("\"abc\"");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertNull(p.getEmbeddedObject());
    }

    // isNaN(): a regular finite number must not be reported as NaN
    @Test
    public void testIsNaN_regularNumber_returnsFalse() throws Throwable {
        JsonNode node = mapper.readTree("3.14");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        assertFalse(p.isNaN());
    }

    // currentNode() (protected, same package): after close() must return null
    @Test
    public void testCurrentNode_afterClose_returnsNull() throws Throwable {
        JsonNode node = mapper.readTree("5");
        TreeTraversingParser p = new TreeTraversingParser(node);
        p.nextToken();
        p.close();
        assertNull(p.currentNode());
    }
}
