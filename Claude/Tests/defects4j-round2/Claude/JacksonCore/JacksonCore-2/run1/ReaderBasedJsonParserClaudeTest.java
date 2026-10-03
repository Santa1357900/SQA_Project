package com.fasterxml.jackson.core.json;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.ObjectCodec;

public class ReaderBasedJsonParserClaudeTest
{
    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable
    {
        factory = new JsonFactory();
    }

    // covers setCodec(null) / getCodec() returning what was set
    @Test
    public void testSetCodecGetCodec_nullValue_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("1");
        p.setCodec(null);
        assertNull(p.getCodec());
    }

    // covers releaseBuffered(Writer) writing remaining buffered chars and returning count
    @Test
    public void testReleaseBuffered_remainingCharsAfterFirstToken_returnsRemainder() throws Throwable {
        JsonParser p = factory.createParser("1 2");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        StringWriter sw = new StringWriter();
        int count = p.releaseBuffered(sw);
        assertEquals(2, count);
        assertEquals(" 2", sw.toString());
    }

    // covers getInputSource() returning underlying Reader instance
    @Test
    public void testGetInputSource_returnsReaderInstance() throws Throwable {
        JsonParser p = factory.createParser("1");
        Object src = p.getInputSource();
        assertTrue(src instanceof Reader);
    }

    // covers close() triggering _closeInput() which nulls out the reader
    @Test
    public void testClose_setsInputSourceToNull() throws Throwable {
        JsonParser p = factory.createParser("1");
        p.close();
        assertNull(p.getInputSource());
    }

    // covers nextToken() for empty object: START_OBJECT, END_OBJECT, then EOF null
    @Test
    public void testNextToken_emptyObject_returnsStartEndThenNull() throws Throwable {
        JsonParser p = factory.createParser("{}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // covers nextToken() for empty array
    @Test
    public void testNextToken_emptyArray_returnsStartEndThenNull() throws Throwable {
        JsonParser p = factory.createParser("[]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }

    // covers object-context field name + value parsing branch
    @Test
    public void testNextToken_objectWithStringField_parsesNameAndValue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":\"b\"}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("a", p.getText());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("b", p.getText());
    }

    // covers literal matching branches for true/false/null
    @Test
    public void testNextToken_booleanAndNullLiterals_parsedCorrectly() throws Throwable {
        JsonParser p = factory.createParser("[true,false,null]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(JsonToken.VALUE_FALSE, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
    }

    // covers missing-colon branch: _reportUnexpectedChar after field name
    @Test
    public void testNextToken_missingColonAfterFieldName_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("{\"a\" 1}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers expectComma() branch: missing comma between array elements
    @Test
    public void testNextToken_missingCommaInArray_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("[1 2]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers mismatched end marker: closing array with '}' while inArray
    @Test
    public void testNextToken_mismatchedEndMarker_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("[1}");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers default switch branch in nextToken() leading to _handleOddValue for unexpected char
    @Test
    public void testNextToken_unexpectedCharAsValue_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("[,]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers getText() for VALUE_NUMBER_INT via fast-path _parseNumber
    @Test
    public void testGetText_forIntegerNumber_returnsDigitsAndValue() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals("123", p.getText());
        assertEquals(123, p.getIntValue());
    }

    // covers getText() for VALUE_NUMBER_FLOAT with exponent part
    @Test
    public void testGetText_forFloatWithExponent_returnsCorrectTextAndValue() throws Throwable {
        JsonParser p = factory.createParser("-12.5e2");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals("-12.5e2", p.getText());
        assertEquals(-1250.0, p.getDoubleValue(), 0.0001);
    }

    // covers _finishString2 escape decoding: \t \n \u0041
    @Test
    public void testGetText_stringWithEscapeSequences_decodesCorrectly() throws Throwable {
        String json = "\"a\\tb\\n\\u0041\"";
        JsonParser p = factory.createParser(json);
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("a\tb\nA", p.getText());
    }

    // covers default branch of _getText2 using JsonToken.asString()
    @Test
    public void testGetText_forStartObjectToken_usesTokenAsString() throws Throwable {
        JsonParser p = factory.createParser("{}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.START_OBJECT.asString(), p.getText());
    }

    // covers leading-zero rejection when ALLOW_NUMERIC_LEADING_ZEROS is disabled (default)
    @Test
    public void testParseNumber_leadingZeroDisallowedByDefault_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("01");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers leading-zero normalization when feature enabled
    @Test
    public void testParseNumber_leadingZeroAllowedWithFeature_valueIsNormalized() throws Throwable {
        JsonFactory f2 = new JsonFactory();
        f2.enable(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS);
        JsonParser p = f2.createParser("007");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(7, p.getIntValue());
    }

    // covers negative sign not followed by digit -> EOF error
    @Test
    public void testParseNumber_negativeSignWithoutDigit_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("-");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers decimal point not followed by digit
    @Test
    public void testParseNumber_decimalPointWithoutDigit_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("1.a");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers exponent indicator not followed by digit
    @Test
    public void testParseNumber_exponentWithoutDigit_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("1ea");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers _handleOddValue single-quote branch when feature disabled
    @Test
    public void testHandleOddValue_singleQuoteDisabled_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("'abc'");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers single-quote string parsing when feature enabled
    @Test
    public void testHandleOddValue_singleQuoteEnabled_parsesStringValue() throws Throwable {
        JsonFactory f2 = new JsonFactory();
        f2.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        JsonParser p = f2.createParser("'abc'");
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("abc", p.getText());
    }

    // covers NaN handling when ALLOW_NON_NUMERIC_NUMBERS enabled
    @Test
    public void testHandleOddValue_NaNEnabled_returnsNaNDoubleValue() throws Throwable {
        JsonFactory f2 = new JsonFactory();
        f2.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        JsonParser p = f2.createParser("NaN");
        p.nextToken();
        assertTrue(Double.isNaN(p.getDoubleValue()));
    }

    // covers NaN handling when feature disabled -> error
    @Test
    public void testHandleOddValue_NaNDisabled_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("NaN");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers '+' followed by non-'I' digit -> invalid number start error
    @Test
    public void testHandleOddValue_plusFollowedByDigit_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("+5");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers '+Infinity' handling via _handleInvalidNumberStart when feature enabled
    @Test
    public void testHandleOddValue_plusInfinityEnabled_returnsPositiveInfinity() throws Throwable {
        JsonFactory f2 = new JsonFactory();
        f2.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        JsonParser p = f2.createParser("+Infinity");
        p.nextToken();
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), p.getDoubleValue(), 0.0);
    }

    // covers unquoted field name parsing when feature enabled
    @Test
    public void testHandleOddName_unquotedFieldNameEnabled_parsesName() throws Throwable {
        JsonFactory f2 = new JsonFactory();
        f2.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        JsonParser p = f2.createParser("{abc:1}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("abc", p.getText());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
    }

    // covers unquoted field name rejection when feature disabled (default)
    @Test
    public void testHandleOddName_unquotedFieldNameDisabled_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("{abc:1}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers comment skipping when ALLOW_COMMENTS enabled
    @Test
    public void testSkipComment_lineCommentEnabled_skipsAndContinues() throws Throwable {
        JsonFactory f2 = new JsonFactory();
        f2.enable(JsonParser.Feature.ALLOW_COMMENTS);
        JsonParser p = f2.createParser("[1, //comment\n2]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(2, p.getIntValue());
    }

    // covers comment rejection when feature disabled (default)
    @Test
    public void testSkipComment_disabled_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("[1 //x\n]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers YAML-style '#' comment skipping when feature enabled
    @Test
    public void testSkipYAMLComment_enabled_skipsLeadingCommentLine() throws Throwable {
        JsonFactory f2 = new JsonFactory();
        f2.enable(JsonParser.Feature.ALLOW_YAML_COMMENTS);
        JsonParser p = f2.createParser("# comment\n[1]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
    }

    // covers nextTextValue() buffered-FIELD_NAME fast path returning string
    @Test
    public void testNextTextValue_afterFieldName_returnsStringValue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":\"b\"}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        assertEquals("b", p.nextTextValue());
    }

    // covers nextIntValue(defaultValue) buffered-FIELD_NAME fast path
    @Test
    public void testNextIntValue_afterFieldName_returnsIntValue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":5}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        assertEquals(5, p.nextIntValue(-1));
    }

    // covers nextLongValue(defaultValue) buffered-FIELD_NAME fast path
    @Test
    public void testNextLongValue_afterFieldName_returnsLongValue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":123456789012}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        assertEquals(123456789012L, p.nextLongValue(-1L));
    }

    // covers nextBooleanValue() buffered-FIELD_NAME fast path for TRUE
    @Test
    public void testNextBooleanValue_afterFieldNameTrue_returnsTrue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":true}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        assertEquals(Boolean.TRUE, p.nextBooleanValue());
    }

    // covers nextBooleanValue() buffered-FIELD_NAME fast path for FALSE
    @Test
    public void testNextBooleanValue_afterFieldNameFalse_returnsFalse() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":false}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        assertEquals(Boolean.FALSE, p.nextBooleanValue());
    }

    // covers getTextLength() for VALUE_NUMBER_INT branch
    @Test
    public void testGetTextLength_forNumberToken_returnsDigitCount() throws Throwable {
        JsonParser p = factory.createParser("12345");
        p.nextToken();
        assertEquals(5, p.getTextLength());
    }

    // covers getTextOffset() for FIELD_NAME branch (always 0)
    @Test
    public void testGetTextOffset_forFieldName_returnsZero() throws Throwable {
        JsonParser p = factory.createParser("{\"abc\":1}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        assertEquals(0, p.getTextOffset());
    }

    // covers getValueAsString() override for VALUE_STRING token
    @Test
    public void testGetValueAsString_noArg_forStringToken_returnsText() throws Throwable {
        JsonParser p = factory.createParser("\"hello\"");
        p.nextToken();
        assertEquals("hello", p.getValueAsString());
    }

    // covers getValueAsString(default) when there is no current token yet
    @Test
    public void testGetValueAsString_withDefault_noCurrentToken_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertEquals("fallback", p.getValueAsString("fallback"));
    }

    // covers getTextCharacters() FIELD_NAME branch using name-copy buffer
    @Test
    public void testGetTextCharacters_forFieldName_returnsNameChars() throws Throwable {
        JsonParser p = factory.createParser("{\"name\":1}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        char[] chars = p.getTextCharacters();
        assertEquals("name", new String(chars, 0, p.getTextLength()));
    }

    // covers _matchToken mismatch branch producing invalid token error
    @Test
    public void testMatchToken_mismatchedTrueLiteral_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("truX");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // covers _handleOddValue fallback: Java-identifier-like unknown token
    @Test
    public void testHandleOddValue_unknownIdentifierToken_throwsJsonParseException() throws Throwable {
        JsonParser p = factory.createParser("banana");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }
}
