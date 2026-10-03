package com.fasterxml.jackson.core.base;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;

public class ParserMinimalBaseClaudeTest
{
    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable {
        factory = new JsonFactory();
    }

    // getCurrentToken(): before any nextToken() call, current token must be null
    @Test
    public void testGetCurrentToken_beforeNextToken_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertNull(p.getCurrentToken());
        p.close();
    }

    // getCurrentTokenId(): before any token -> ID_NO_TOKEN
    @Test
    public void testGetCurrentTokenId_beforeNextToken_returnsIdNoToken() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertEquals(JsonTokenId.ID_NO_TOKEN, p.getCurrentTokenId());
        p.close();
    }

    // getCurrentTokenId(): after nextToken() -> matches token's id
    @Test
    public void testGetCurrentTokenId_afterNextToken_matchesTokenId() throws Throwable {
        JsonParser p = factory.createParser("123");
        p.nextToken();
        assertEquals(JsonTokenId.ID_NUMBER_INT, p.getCurrentTokenId());
        p.close();
    }

    // hasCurrentToken(): false before, true after nextToken()
    @Test
    public void testHasCurrentToken_beforeAndAfterNextToken() throws Throwable {
        JsonParser p = factory.createParser("true");
        assertFalse(p.hasCurrentToken());
        p.nextToken();
        assertTrue(p.hasCurrentToken());
        p.close();
    }

    // hasTokenId(id): before any token, only ID_NO_TOKEN matches
    @Test
    public void testHasTokenId_beforeNextToken_onlyNoTokenMatches() throws Throwable {
        JsonParser p = factory.createParser("true");
        assertTrue(p.hasTokenId(JsonTokenId.ID_NO_TOKEN));
        assertFalse(p.hasTokenId(JsonTokenId.ID_TRUE));
        p.close();
    }

    // hasTokenId(id): after token, matches current token id only
    @Test
    public void testHasTokenId_afterNextToken_matchesCurrentTokenId() throws Throwable {
        JsonParser p = factory.createParser("[1]");
        p.nextToken();
        assertTrue(p.hasTokenId(JsonTokenId.ID_START_ARRAY));
        assertFalse(p.hasTokenId(JsonTokenId.ID_END_ARRAY));
        p.close();
    }

    // hasToken(JsonToken): matches exact current token instance
    @Test
    public void testHasToken_matchesCurrentTokenExactly() throws Throwable {
        JsonParser p = factory.createParser("[1]");
        p.nextToken();
        assertTrue(p.hasToken(JsonToken.START_ARRAY));
        assertFalse(p.hasToken(JsonToken.END_ARRAY));
        p.close();
    }

    // isExpectedStartArrayToken(): true only when current token is START_ARRAY
    @Test
    public void testIsExpectedStartArrayToken_trueAndFalseCases() throws Throwable {
        JsonParser p = factory.createParser("[1]");
        p.nextToken();
        assertTrue(p.isExpectedStartArrayToken());
        p.nextToken();
        assertFalse(p.isExpectedStartArrayToken());
        p.close();
    }

    // isExpectedStartObjectToken(): true only when current token is START_OBJECT
    @Test
    public void testIsExpectedStartObjectToken_trueAndFalseCases() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1}");
        p.nextToken();
        assertTrue(p.isExpectedStartObjectToken());
        p.nextToken();
        assertFalse(p.isExpectedStartObjectToken());
        p.close();
    }

    // nextValue(): when nextToken() yields FIELD_NAME, advances again to the value token
    @Test
    public void testNextValue_skipsFieldNameToValue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1}");
        p.nextToken(); // START_OBJECT
        JsonToken t = p.nextValue();
        assertEquals(JsonToken.VALUE_NUMBER_INT, t);
        assertEquals("a", p.getCurrentName());
        p.close();
    }

    // nextValue(): when nextToken() does not yield FIELD_NAME, returns that token directly
    @Test
    public void testNextValue_nonFieldNameReturnedDirectly() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1}");
        p.nextToken(); // START_OBJECT
        p.nextValue(); // FIELD_NAME a -> VALUE_NUMBER_INT
        JsonToken t = p.nextValue(); // nextToken() -> END_OBJECT (not FIELD_NAME)
        assertEquals(JsonToken.END_OBJECT, t);
        p.close();
    }

    // skipChildren(): current token not START_OBJECT/START_ARRAY -> no-op, returns same parser
    @Test
    public void testSkipChildren_notStructStart_returnsSelfUnchanged() throws Throwable {
        JsonParser p = factory.createParser("5");
        p.nextToken();
        JsonParser result = p.skipChildren();
        assertSame(p, result);
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.getCurrentToken());
        p.close();
    }

    // skipChildren(): START_OBJECT with nested object -> advances to matching outer END_OBJECT
    @Test
    public void testSkipChildren_nestedObject_advancesToMatchingEnd() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":{\"b\":1},\"c\":2}");
        p.nextToken(); // START_OBJECT
        p.skipChildren();
        assertEquals(JsonToken.END_OBJECT, p.getCurrentToken());
        p.close();
    }

    // skipChildren(): START_ARRAY with nested array -> advances to matching outer END_ARRAY
    @Test
    public void testSkipChildren_nestedArray_advancesToMatchingEnd() throws Throwable {
        JsonParser p = factory.createParser("[[1,2],3]");
        p.nextToken(); // START_ARRAY
        p.skipChildren();
        assertEquals(JsonToken.END_ARRAY, p.getCurrentToken());
        p.close();
    }

    // clearCurrentToken(): when a current token exists, clears it and records last cleared token
    @Test
    public void testClearCurrentToken_withCurrentToken_clearsAndRecordsLast() throws Throwable {
        JsonParser p = factory.createParser("true");
        p.nextToken();
        p.clearCurrentToken();
        assertNull(p.getCurrentToken());
        assertEquals(JsonToken.VALUE_TRUE, p.getLastClearedToken());
        p.close();
    }

    // clearCurrentToken(): when current token already null, is a no-op
    @Test
    public void testClearCurrentToken_whenAlreadyNull_isNoOp() throws Throwable {
        JsonParser p = factory.createParser("true");
        p.clearCurrentToken();
        assertNull(p.getCurrentToken());
        assertNull(p.getLastClearedToken());
        p.close();
    }

    // getLastClearedToken(): before any clear call, returns null
    @Test
    public void testGetLastClearedToken_beforeAnyClear_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("true");
        p.nextToken();
        assertNull(p.getLastClearedToken());
        p.close();
    }

    // getValueAsBoolean(): string "true" -> true
    @Test
    public void testGetValueAsBoolean_stringTrue_returnsTrue() throws Throwable {
        JsonParser p = factory.createParser("\"true\"");
        p.nextToken();
        assertTrue(p.getValueAsBoolean(false));
        p.close();
    }

    // getValueAsBoolean(): string "false" -> false
    @Test
    public void testGetValueAsBoolean_stringFalse_returnsFalse() throws Throwable {
        JsonParser p = factory.createParser("\"false\"");
        p.nextToken();
        assertFalse(p.getValueAsBoolean(true));
        p.close();
    }

    // getValueAsBoolean(): textual "null" string -> false
    @Test
    public void testGetValueAsBoolean_textualNull_returnsFalse() throws Throwable {
        JsonParser p = factory.createParser("\"null\"");
        p.nextToken();
        assertFalse(p.getValueAsBoolean(true));
        p.close();
    }

    // getValueAsBoolean(): unrecognized string content -> falls back to defaultValue
    @Test
    public void testGetValueAsBoolean_unrecognizedString_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("\"hello\"");
        p.nextToken();
        assertTrue(p.getValueAsBoolean(true));
        p.close();
    }

    // getValueAsBoolean(): non-zero int -> true, zero int -> false
    @Test
    public void testGetValueAsBoolean_numberInt_nonZeroTrueZeroFalse() throws Throwable {
        JsonParser p1 = factory.createParser("7");
        p1.nextToken();
        assertTrue(p1.getValueAsBoolean(false));
        p1.close();

        JsonParser p2 = factory.createParser("0");
        p2.nextToken();
        assertFalse(p2.getValueAsBoolean(true));
        p2.close();
    }

    // getValueAsBoolean(): VALUE_TRUE/VALUE_FALSE/VALUE_NULL tokens
    @Test
    public void testGetValueAsBoolean_booleanAndNullTokens() throws Throwable {
        JsonParser p1 = factory.createParser("true");
        p1.nextToken();
        assertTrue(p1.getValueAsBoolean(false));
        p1.close();

        JsonParser p2 = factory.createParser("false");
        p2.nextToken();
        assertFalse(p2.getValueAsBoolean(true));
        p2.close();

        JsonParser p3 = factory.createParser("null");
        p3.nextToken();
        assertFalse(p3.getValueAsBoolean(true));
        p3.close();
    }

    // getValueAsBoolean(): non-convertible token type (START_ARRAY) -> defaultValue
    @Test
    public void testGetValueAsBoolean_nonConvertibleToken_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("[1]");
        p.nextToken();
        assertFalse(p.getValueAsBoolean(false));
        p.close();
    }

    // getValueAsBoolean(): VALUE_NUMBER_FLOAT is not explicitly handled -> falls back to defaultValue
    @Test
    public void testGetValueAsBoolean_numberFloat_fallsBackToDefault() throws Throwable {
        JsonParser p = factory.createParser("3.5");
        p.nextToken();
        assertTrue(p.getValueAsBoolean(true));
        p.close();
    }

    // getValueAsInt(): VALUE_NUMBER_INT -> exact int value
    @Test
    public void testGetValueAsInt_numberInt_returnsExactValue() throws Throwable {
        JsonParser p = factory.createParser("42");
        p.nextToken();
        assertEquals(42, p.getValueAsInt());
        p.close();
    }

    // getValueAsInt(): VALUE_NUMBER_FLOAT -> truncated int value
    @Test
    public void testGetValueAsInt_numberFloat_returnsTruncatedValue() throws Throwable {
        JsonParser p = factory.createParser("3.9");
        p.nextToken();
        assertEquals(3, p.getValueAsInt());
        p.close();
    }

    // getValueAsInt(int): numeric string parses to int
    @Test
    public void testGetValueAsIntWithDefault_string_parsesNumericString() throws Throwable {
        JsonParser p = factory.createParser("\"123\"");
        p.nextToken();
        assertEquals(123, p.getValueAsInt(-1));
        p.close();
    }

    // getValueAsInt(int): textual "null" string -> 0
    @Test
    public void testGetValueAsIntWithDefault_textualNull_returnsZero() throws Throwable {
        JsonParser p = factory.createParser("\"null\"");
        p.nextToken();
        assertEquals(0, p.getValueAsInt(-1));
        p.close();
    }

    // getValueAsInt(int): non-numeric string -> defaultValue
    @Test
    public void testGetValueAsIntWithDefault_nonNumericString_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("\"abc\"");
        p.nextToken();
        assertEquals(-7, p.getValueAsInt(-7));
        p.close();
    }

    // getValueAsInt(int): TRUE token -> 1, FALSE token -> 0, NULL token -> 0
    @Test
    public void testGetValueAsIntWithDefault_booleanAndNullTokens() throws Throwable {
        JsonParser p1 = factory.createParser("true");
        p1.nextToken();
        assertEquals(1, p1.getValueAsInt(-1));
        p1.close();

        JsonParser p2 = factory.createParser("false");
        p2.nextToken();
        assertEquals(0, p2.getValueAsInt(-1));
        p2.close();

        JsonParser p3 = factory.createParser("null");
        p3.nextToken();
        assertEquals(0, p3.getValueAsInt(-1));
        p3.close();
    }

    // getValueAsLong(): VALUE_NUMBER_INT -> exact long value
    @Test
    public void testGetValueAsLong_numberInt_returnsExactValue() throws Throwable {
        JsonParser p = factory.createParser("9999999999");
        p.nextToken();
        assertEquals(9999999999L, p.getValueAsLong());
        p.close();
    }

    // getValueAsLong(long): numeric string parses to long
    @Test
    public void testGetValueAsLongWithDefault_string_parsesNumericString() throws Throwable {
        JsonParser p = factory.createParser("\"123456789012\"");
        p.nextToken();
        assertEquals(123456789012L, p.getValueAsLong(-1L));
        p.close();
    }

    // getValueAsLong(long): non-numeric string -> defaultValue
    @Test
    public void testGetValueAsLongWithDefault_nonNumericString_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("\"xyz\"");
        p.nextToken();
        assertEquals(-5L, p.getValueAsLong(-5L));
        p.close();
    }

    // getValueAsDouble(double): VALUE_NUMBER_FLOAT -> exact double value
    @Test
    public void testGetValueAsDouble_numberFloat_returnsExactValue() throws Throwable {
        JsonParser p = factory.createParser("3.5");
        p.nextToken();
        assertEquals(3.5, p.getValueAsDouble(-1.0), 1e-9);
        p.close();
    }

    // getValueAsDouble(double): numeric string parses to double
    @Test
    public void testGetValueAsDouble_string_parsesNumericString() throws Throwable {
        JsonParser p = factory.createParser("\"2.5\"");
        p.nextToken();
        assertEquals(2.5, p.getValueAsDouble(-1.0), 1e-9);
        p.close();
    }

    // getValueAsDouble(double): non-numeric string -> defaultValue
    @Test
    public void testGetValueAsDouble_nonNumericString_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("\"nope\"");
        p.nextToken();
        assertEquals(-9.0, p.getValueAsDouble(-9.0), 1e-9);
        p.close();
    }

    // getValueAsDouble(double): TRUE token -> 1.0, FALSE token -> 0.0
    @Test
    public void testGetValueAsDouble_booleanTokens() throws Throwable {
        JsonParser p1 = factory.createParser("true");
        p1.nextToken();
        assertEquals(1.0, p1.getValueAsDouble(-1.0), 1e-9);
        p1.close();

        JsonParser p2 = factory.createParser("false");
        p2.nextToken();
        assertEquals(0.0, p2.getValueAsDouble(-1.0), 1e-9);
        p2.close();
    }

    // getValueAsString(): VALUE_STRING -> returns text content directly
    @Test
    public void testGetValueAsString_stringToken_returnsText() throws Throwable {
        JsonParser p = factory.createParser("\"hello\"");
        p.nextToken();
        assertEquals("hello", p.getValueAsString());
        p.close();
    }

    // getValueAsString(): VALUE_NULL -> returns null default
    @Test
    public void testGetValueAsString_nullToken_returnsNullDefault() throws Throwable {
        JsonParser p = factory.createParser("null");
        p.nextToken();
        assertNull(p.getValueAsString());
        p.close();
    }

    // getValueAsString(String): VALUE_NULL -> returns provided default value
    @Test
    public void testGetValueAsStringWithDefault_nullToken_returnsProvidedDefault() throws Throwable {
        JsonParser p = factory.createParser("null");
        p.nextToken();
        assertEquals("fallback", p.getValueAsString("fallback"));
        p.close();
    }

    // getValueAsString(): scalar non-string token (number) -> returns textual representation
    @Test
    public void testGetValueAsString_numberToken_returnsTextualRepresentation() throws Throwable {
        JsonParser p = factory.createParser("123");
        p.nextToken();
        assertEquals("123", p.getValueAsString());
        p.close();
    }

    // getValueAsString(String): non-scalar token (START_OBJECT) -> returns provided default
    @Test
    public void testGetValueAsStringWithDefault_nonScalarToken_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1}");
        p.nextToken();
        assertEquals("fallback", p.getValueAsString("fallback"));
        p.close();
    }

    // getValueAsString(): no current token yet (before nextToken) -> returns null default
    @Test
    public void testGetValueAsString_noCurrentToken_returnsNullDefault() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertNull(p.getValueAsString());
        p.close();
    }

    // getValueAsString(): scalar boolean token -> returns its textual representation
    @Test
    public void testGetValueAsString_booleanToken_returnsTextualRepresentation() throws Throwable {
        JsonParser p = factory.createParser("true");
        p.nextToken();
        assertEquals("true", p.getValueAsString());
        p.close();
    }
}
