package com.fasterxml.jackson.core.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.ObjectCodec;

public class UTF8StreamJsonParserClaudeTest
{
    private JsonParser createParser(String json) throws IOException {
        JsonFactory factory = new JsonFactory();
        byte[] bytes = json.getBytes("UTF-8");
        return factory.createParser(bytes);
    }

    private JsonParser createParser(String json, JsonParser.Feature feature, boolean enabled) throws IOException {
        JsonFactory factory = new JsonFactory();
        factory.configure(feature, enabled);
        byte[] bytes = json.getBytes("UTF-8");
        return factory.createParser(bytes);
    }

    // covers getCodec()/setCodec(): default codec is null, round trip works
    @Test
    public void testGetCodecSetCodec_returnsSameInstance() throws Throwable {
        JsonParser p = createParser("1");
        assertNull(p.getCodec());
        p.setCodec(null);
        assertNull(p.getCodec());
    }

    // covers releaseBuffered(): count equals full unconsumed buffer before any parsing
    @Test
    public void testReleaseBuffered_beforeAnyRead_returnsFullBuffer() throws Throwable {
        byte[] bytes = "{}".getBytes("UTF-8");
        JsonFactory factory = new JsonFactory();
        JsonParser p = factory.createParser(bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = p.releaseBuffered(out);
        assertEquals(2, count);
        assertArrayEquals(bytes, out.toByteArray());
    }

    // covers getInputSource(): returns exact InputStream instance passed to factory
    @Test
    public void testGetInputSource_returnsUnderlyingInputStream() throws Throwable {
        byte[] bytes = "1".getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        JsonFactory factory = new JsonFactory();
        JsonParser p = factory.createParser(in);
        assertSame(in, p.getInputSource());
    }

    // covers getText(): ID_FIELD_NAME branch of _getText2
    @Test
    public void testGetText_forFieldNameToken_returnsFieldName() throws Throwable {
        JsonParser p = createParser("{\"fieldName\":1}");
        p.nextToken();
        p.nextToken();
        assertEquals("fieldName", p.getText());
    }

    // covers getText(): VALUE_STRING branch, tokenIncomplete triggers _finishString
    @Test
    public void testGetText_forStringToken_returnsUnescapedValue() throws Throwable {
        JsonParser p = createParser("\"hello\\nworld\"");
        p.nextToken();
        assertEquals("hello\nworld", p.getText());
    }

    // covers getText(): ID_NUMBER_INT branch of _getText2
    @Test
    public void testGetText_forIntegerToken_returnsNumericString() throws Throwable {
        JsonParser p = createParser("42");
        p.nextToken();
        assertEquals("42", p.getText());
    }

    // covers getValueAsString(): VALUE_STRING branch
    @Test
    public void testGetValueAsString_forStringToken_returnsValue() throws Throwable {
        JsonParser p = createParser("\"hi\"");
        p.nextToken();
        assertEquals("hi", p.getValueAsString());
    }

    // covers getTextCharacters()/getTextLength()/getTextOffset() for VALUE_STRING
    @Test
    public void testGetTextCharactersLengthOffset_forStringToken() throws Throwable {
        JsonParser p = createParser("\"abcde\"");
        p.nextToken();
        char[] chars = p.getTextCharacters();
        int off = p.getTextOffset();
        int len = p.getTextLength();
        assertEquals("abcde", new String(chars, off, len));
    }

    // covers getCurrentLocation(): returns sane non-null location
    @Test
    public void testGetCurrentLocation_returnsNonNullLocation() throws Throwable {
        JsonParser p = createParser("{\"a\":1}");
        p.nextToken();
        JsonLocation loc = p.getCurrentLocation();
        assertNotNull(loc);
        assertTrue(loc.getLineNr() >= 1);
    }

    // covers getTokenLocation(): returns sane non-null location
    @Test
    public void testGetTokenLocation_returnsNonNullLocation() throws Throwable {
        JsonParser p = createParser("123");
        p.nextToken();
        JsonLocation loc = p.getTokenLocation();
        assertNotNull(loc);
        assertTrue(loc.getLineNr() >= 1);
    }

    // covers nextToken(): empty object START_OBJECT/END_OBJECT/null-at-eof
    @Test
    public void testNextToken_emptyObject_returnsStartAndEndObject() throws Throwable {
        JsonParser p = createParser("{}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // covers nextToken(): empty array START_ARRAY/END_ARRAY
    @Test
    public void testNextToken_emptyArray_returnsStartAndEndArray() throws Throwable {
        JsonParser p = createParser("[]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
    }

    // covers nextToken(): object with fields, field name + int + string values
    @Test
    public void testNextToken_objectWithFields_parsesFieldNamesAndValues() throws Throwable {
        JsonParser p = createParser("{\"a\":1,\"b\":\"text\"}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("a", p.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("b", p.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("text", p.getText());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
    }

    // covers nextToken(): nested array structure
    @Test
    public void testNextToken_nestedArray_parsesNestedTokens() throws Throwable {
        JsonParser p = createParser("[1,[2,3],4]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(2, p.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(3, p.getIntValue());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(4, p.getIntValue());
    }

    // covers nextToken(): i==INT_RCURLY while context is array -> mismatched end marker
    @Test
    public void testMismatchedEndMarker_arrayClosedWithCurly_throws() throws Throwable {
        JsonParser p = createParser("[1}");
        p.nextToken();
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers nextToken(): i==INT_RBRACKET while context is object -> mismatched end marker
    @Test
    public void testMismatchedEndMarker_objectClosedWithBracket_throws() throws Throwable {
        JsonParser p = createParser("{\"a\":1]");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers nextToken(): expectComma() true but no comma present
    @Test
    public void testMissingComma_throwsException() throws Throwable {
        JsonParser p = createParser("{\"a\":1 \"b\":2}");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("comma"));
        }
    }

    // covers nextToken(): field name not followed by colon
    @Test
    public void testMissingColon_throwsException() throws Throwable {
        JsonParser p = createParser("{\"a\" 1}");
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("colon"));
        }
    }

    // covers nextTextValue(): VALUE_STRING branch and non-string branch after FIELD_NAME
    @Test
    public void testNextTextValue_afterFieldName_returnsStringOrNull() throws Throwable {
        JsonParser p = createParser("{\"a\":\"hello\",\"b\":5}");
        p.nextToken();
        p.nextToken();
        assertEquals("hello", p.nextTextValue());
        p.nextToken();
        assertNull(p.nextTextValue());
    }

    // covers nextIntValue(): VALUE_NUMBER_INT branch and default-value branch after FIELD_NAME
    @Test
    public void testNextIntValue_afterFieldName_returnsValueOrDefault() throws Throwable {
        JsonParser p = createParser("{\"a\":5,\"b\":\"x\"}");
        p.nextToken();
        p.nextToken();
        assertEquals(5, p.nextIntValue(-1));
        p.nextToken();
        assertEquals(-1, p.nextIntValue(-1));
    }

    // covers nextBooleanValue(): ID_TRUE/ID_FALSE/default branches
    @Test
    public void testNextBooleanValue_returnsTrueFalseOrNull() throws Throwable {
        JsonParser p = createParser("[true,false,1]");
        p.nextToken();
        assertEquals(Boolean.TRUE, p.nextBooleanValue());
        assertEquals(Boolean.FALSE, p.nextBooleanValue());
        assertNull(p.nextBooleanValue());
    }

    // covers _parseNumber(): negative branch
    @Test
    public void testParseNumber_negativeInteger_parsesCorrectValue() throws Throwable {
        JsonParser p = createParser("-42");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(-42, p.getIntValue());
    }

    // covers _verifyNoLeadingZeroes(): feature disabled -> throws
    @Test
    public void testParseNumber_leadingZeroNotAllowed_throws() throws Throwable {
        JsonParser p = createParser("0123");
        try {
            p.nextToken();
            fail("expected JsonParseException for leading zero");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("Leading zeroes"));
        }
    }

    // covers _verifyNoLeadingZeroes(): single leading zero stripped when feature enabled
    @Test
    public void testParseNumber_leadingZeroAllowed_stripsZero() throws Throwable {
        JsonParser p = createParser("0123", JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(123, p.getIntValue());
    }

    // covers _verifyNoLeadingZeroes(): all-zero sequence collapses to single zero
    @Test
    public void testParseNumber_allZerosAllowed_returnsZero() throws Throwable {
        JsonParser p = createParser("000", JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(0, p.getIntValue());
    }

    // covers _parseFloat(): simple decimal fraction
    @Test
    public void testParseFloat_simpleDecimal_parsesCorrectValue() throws Throwable {
        JsonParser p = createParser("3.14");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(3.14, p.getDoubleValue(), 1e-9);
    }

    // covers _parseFloat(): exponent notation branch
    @Test
    public void testParseFloat_exponentNotation_parsesCorrectValue() throws Throwable {
        JsonParser p = createParser("1e3");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(1000.0, p.getDoubleValue(), 1e-9);
    }

    // covers _parseFloat(): decimal point not followed by digit -> throws
    @Test
    public void testParseFloat_decimalPointNotFollowedByDigit_throws() throws Throwable {
        JsonParser p = createParser("[1.]");
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("Decimal point"));
        }
    }

    // covers _parseFloat(): exponent indicator not followed by digit -> throws
    @Test
    public void testParseFloat_exponentNotFollowedByDigit_throws() throws Throwable {
        JsonParser p = createParser("[1e]");
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("Exponent indicator"));
        }
    }

    // covers _handleUnexpectedValue(): NaN token without feature enabled -> throws
    @Test
    public void testHandleUnexpectedValue_NaNNotAllowed_throws() throws Throwable {
        JsonParser p = createParser("NaN");
        try {
            p.nextToken();
            fail("expected JsonParseException for NaN");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("NaN"));
        }
    }

    // covers _handleUnexpectedValue(): NaN token with feature enabled -> Double.NaN
    @Test
    public void testHandleUnexpectedValue_NaNAllowed_returnsNaN() throws Throwable {
        JsonParser p = createParser("NaN", JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertTrue(Double.isNaN(p.getDoubleValue()));
    }

    // covers _handleUnexpectedValue(): Infinity token with feature enabled -> +Infinity
    @Test
    public void testHandleUnexpectedValue_InfinityAllowed_returnsPositiveInfinity() throws Throwable {
        JsonParser p = createParser("Infinity", JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), p.getDoubleValue(), 0.0);
    }

    // covers _handleInvalidNumberStart(): "-Infinity" with feature enabled -> -Infinity
    @Test
    public void testHandleInvalidNumberStart_negativeInfinityAllowed_returnsNegativeInfinity() throws Throwable {
        JsonParser p = createParser("-Infinity", JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), p.getDoubleValue(), 0.0);
    }

    // covers _handleUnexpectedValue(): single quote without feature enabled -> throws
    @Test
    public void testHandleUnexpectedValue_singleQuoteNotAllowed_throws() throws Throwable {
        JsonParser p = createParser("'value'");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("valid value"));
        }
    }

    // covers _handleApos(): single-quoted string parsed when feature enabled
    @Test
    public void testHandleApos_singleQuotedStringAllowed_parsesValue() throws Throwable {
        JsonParser p = createParser("'hello'", JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("hello", p.getText());
    }

    // covers _handleOddName(): unquoted field name parsed when feature enabled
    @Test
    public void testHandleOddName_unquotedFieldNameAllowed_parsesName() throws Throwable {
        JsonParser p = createParser("{abc:1}", JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        p.nextToken();
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("abc", p.getCurrentName());
    }

    // covers _handleOddName(): unquoted field name without feature enabled -> throws
    @Test
    public void testHandleOddName_unquotedFieldNameNotAllowed_throws() throws Throwable {
        JsonParser p = createParser("{abc:1}");
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("double-quote"));
        }
    }

    // covers _skipComment(): c-style comment skipped when feature enabled
    @Test
    public void testSkipComment_cStyleAllowed_skipsAndParsesNumber() throws Throwable {
        JsonParser p = createParser("/* comment */ 123", JsonParser.Feature.ALLOW_COMMENTS, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(123, p.getIntValue());
    }

    // covers _skipComment(): comment without feature enabled -> throws
    @Test
    public void testSkipComment_notAllowed_throws() throws Throwable {
        JsonParser p = createParser("/* x */123");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("comment"));
        }
    }

    // covers _skipYAMLComment(): yaml-style comment skipped when feature enabled
    @Test
    public void testSkipYAMLComment_allowed_skipsAndParsesNumber() throws Throwable {
        JsonParser p = createParser("# comment\n123", JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(123, p.getIntValue());
    }

    // covers _decodeEscaped(): mapped escapes \n and \t
    @Test
    public void testDecodeEscaped_newlineAndTabEscapes_parsesCorrectChars() throws Throwable {
        JsonParser p = createParser("\"\\n\\t\"");
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("\n\t", p.getText());
    }

    // covers _decodeEscaped(): mapped escapes \" and \\
    @Test
    public void testDecodeEscaped_quoteAndBackslashEscapes_parsesCorrectChars() throws Throwable {
        String quoteEscape = "\\\"";
        String back        String backslashEscape = "\\\\";
        String json = "\"" + quoteEscape + backslashEscape + "\"";
        JsonParser p = createParser(json);
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("\"\\", p.getText());
    }

    // covers _finishString2(): default branch, unescaped control char -> throws
    @Test
    public void testUnescapedControlChar_inString_throws() throws Throwable {
        byte[] bytes = new byte[] { '"', 0x01, '"' };
        JsonFactory factory = new JsonFactory();
        JsonParser p = factory.createParser(bytes);
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        try {
            p.getText();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers _verifyRootSpace(): default branch, missing separating whitespace between root values
    @Test
    public void testVerifyRootSpace_missingSpace_throws() throws Throwable {
        JsonParser p = createParser("1,2");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
