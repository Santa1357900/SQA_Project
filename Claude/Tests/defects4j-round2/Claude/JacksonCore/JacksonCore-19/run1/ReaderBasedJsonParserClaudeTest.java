package com.fasterxml.jackson.core.json;

import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParseException;

public class ReaderBasedJsonParserClaudeTest
{
    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable {
        factory = new JsonFactory();
    }

    // getCodec(): default factory produces parser with null codec
    @Test
    public void testGetCodec_defaultFactory_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertNull(p.getCodec());
    }

    // setCodec(null) then getCodec() must return null
    @Test
    public void testSetCodec_null_getCodecReturnsNull() throws Throwable {
        JsonParser p = factory.createParser("123");
        p.setCodec(null);
        assertNull(p.getCodec());
    }

    // releaseBuffered before any read: no buffered content, returns 0
    @Test
    public void testReleaseBuffered_beforeAnyRead_returnsZero() throws Throwable {
        JsonParser p = factory.createParser("123");
        StringWriter w = new StringWriter();
        assertEquals(0, p.releaseBuffered(w));
    }

    // getText() before any token has been read must return null
    @Test
    public void testGetText_beforeFirstToken_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertNull(p.getText());
    }

    // getTextLength/getTextOffset/getTextCharacters before any token
    @Test
    public void testTextAccessors_beforeFirstToken_returnDefaults() throws Throwable {
        JsonParser p = factory.createParser("123");
        assertEquals(0, p.getTextLength());
        assertEquals(0, p.getTextOffset());
        assertNull(p.getTextCharacters());
    }

    // empty input: nextToken returns null immediately (EOF)
    @Test
    public void testNextToken_emptyInput_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("");
        assertNull(p.nextToken());
    }

    // whitespace-only input: nextToken skips ws and returns null (EOF)
    @Test
    public void testNextToken_whitespaceOnlyInput_returnsNull() throws Throwable {
        JsonParser p = factory.createParser(" \t\n ");
        assertNull(p.nextToken());
    }

    // basic object: FIELD_NAME/VALUE_NUMBER_INT/END_OBJECT sequence
    @Test
    public void testNextToken_simpleObject_returnsExpectedSequence() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("a", p.getText());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // basic array: START_ARRAY / three ints / END_ARRAY
    @Test
    public void testNextToken_simpleArray_returnsExpectedSequence() throws Throwable {
        JsonParser p = factory.createParser("[1,2,3]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(2, p.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(3, p.getIntValue());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
    }

    // nested array inside object: child array context created via _nextAfterName
    @Test
    public void testNextToken_arrayInsideObject_parsesNestedValues() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":[1,2]}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(2, p.getIntValue());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
    }

    // object inside array: child object context created via main switch
    @Test
    public void testNextToken_objectInsideArray_parsesNestedValues() throws Throwable {
        JsonParser p = factory.createParser("[{\"a\":1}]");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
    }

    // mismatched closer: array closed with '}' must throw
    @Test
    public void testNextToken_mismatchedArrayCloseWithBrace_throwsException() throws Throwable {
        JsonParser p = factory.createParser("[1}");
        p.nextToken();
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // mismatched closer: object closed with ']' must throw
    @Test
    public void testNextToken_mismatchedObjectCloseWithBracket_throwsException() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1]");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // leading zero disallowed by default per JSON number grammar
    @Test
    public void testNextToken_leadingZeroDisallowedByDefault_throwsException() throws Throwable {
        JsonParser p = factory.createParser("01");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // leading zeros allowed when ALLOW_NUMERIC_LEADING_ZEROS enabled
    @Test
    public void testNextToken_leadingZeroAllowedWhenFeatureEnabled_parsesValue() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS);
        JsonParser p = f.createParser("007");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(7, p.getIntValue());
    }

    // negative integer parsing
    @Test
    public void testNextToken_negativeNumber_parsesCorrectly() throws Throwable {
        JsonParser p = factory.createParser("-42");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(-42, p.getIntValue());
    }

    // float with fraction part
    @Test
    public void testNextToken_floatNumber_parsesCorrectly() throws Throwable {
        JsonParser p = factory.createParser("3.14");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals("3.14", p.getText());
    }

    // float with exponent part
    @Test
    public void testNextToken_exponentNumber_parsesCorrectly() throws Throwable {
        JsonParser p = factory.createParser("1e3");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals("1e3", p.getText());
    }

    // lone minus sign with no following digit must throw EOF error
    @Test
    public void testNextToken_loneMinusSign_throwsException() throws Throwable {
        JsonParser p = factory.createParser("-");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // leading '+' is not valid JSON number start
    @Test
    public void testNextToken_plusSignNumber_throwsException() throws Throwable {
        JsonParser p = factory.createParser("+5");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // NaN token not allowed by default
    @Test
    public void testNextToken_nanDisallowedByDefault_throwsException() throws Throwable {
        JsonParser p = factory.createParser("NaN");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // NaN allowed when ALLOW_NON_NUMERIC_NUMBERS enabled
    @Test
    public void testNextToken_nanAllowedWhenFeatureEnabled_returnsFloatToken() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        JsonParser p = f.createParser("NaN");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
    }

    // Infinity allowed when ALLOW_NON_NUMERIC_NUMBERS enabled
    @Test
    public void testNextToken_infinityAllowedWhenFeatureEnabled_returnsFloatToken() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        JsonParser p = f.createParser("Infinity");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
    }

    // comments disallowed by default
    @Test
    public void testNextToken_lineCommentDisallowedByDefault_throwsException() throws Throwable {
        JsonParser p = factory.createParser("// c\n123");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // line comment skipped when ALLOW_COMMENTS enabled
    @Test
    public void testNextToken_lineCommentAllowedWhenEnabled_skipsComment() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_COMMENTS);
        JsonParser p = f.createParser("// c\n123");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(123, p.getIntValue());
    }

    // block comment skipped when ALLOW_COMMENTS enabled
    @Test
    public void testNextToken_blockCommentAllowedWhenEnabled_skipsComment() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_COMMENTS);
        JsonParser p = f.createParser("/* c */456");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(456, p.getIntValue());
    }

    // YAML style comment skipped when ALLOW_YAML_COMMENTS enabled
    @Test
    public void testNextToken_yamlCommentAllowedWhenEnabled_skipsComment() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_YAML_COMMENTS);
        JsonParser p = f.createParser("# c\n789");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(789, p.getIntValue());
    }

    // single-quoted strings disallowed by default
    @Test
    public void testNextToken_singleQuoteStringDisallowedByDefault_throwsException() throws Throwable {
        JsonParser p = factory.createParser("'hello'");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // single-quoted strings allowed when ALLOW_SINGLE_QUOTES enabled
    @Test
    public void testNextToken_singleQuoteStringAllowedWhenEnabled_parsesCorrectly() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        JsonParser p = f.createParser("'hello'");
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("hello", p.getText());
    }

    // unquoted field name disallowed by default
    @Test
    public void testNextToken_unquotedFieldNameDisallowedByDefault_throwsException() throws Throwable {
        JsonParser p = factory.createParser("{abc:1}");
        p.nextToken();
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // unquoted field name allowed when ALLOW_UNQUOTED_FIELD_NAMES enabled
    @Test
    public void testNextToken_unquotedFieldNameAllowedWhenEnabled_parsesCorrectly() throws Throwable {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        JsonParser p = f.createParser("{abc:1}");
        p.nextToken();
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("abc", p.getText());
    }

    // bare unrecognized identifier value must throw
    @Test
    public void testNextToken_invalidBareToken_throwsException() throws Throwable {
        JsonParser p = factory.createParser("abc");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // non-identifier invalid value start character must throw
    @Test
    public void testNextToken_invalidValueStartChar_throwsException() throws Throwable {
        JsonParser p = factory.createParser("#value");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // root-level values require whitespace separation
    @Test
    public void testNextToken_rootValuesWithoutSeparatingSpace_throwsException() throws Throwable {
        JsonParser p = factory.createParser("12,34");
        try {
            p.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // root-level values separated by whitespace parse sequentially
    @Test
    public void testNextToken_rootValuesWithSpace_parsesSequentially() throws Throwable {
        JsonParser p = factory.createParser("12 34");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(12, p.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(34, p.getIntValue());
    }

    // unicode escape sequence decodes to correct character
    @Test
    public void testNextToken_stringWithUnicodeEscape_decodesCorrectly() throws Throwable {
        JsonParser p = factory.createParser("\"\\u0041\"");
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("A", p.getText());
    }

    // standard escape sequences decode correctly
    @Test
    public void testNextToken_stringWithStandardEscapes_decodesCorrectly() throws Throwable {
        JsonParser p = factory.createParser("\"a\\tb\\n\"");
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("a\tb\n", p.getText());
    }

    // raw unescaped control char inside string must throw on materialization
    @Test
    public void testGetText_rawControlCharInString_throwsException() throws Throwable {
        String json = "\"a" + "\n" + "b\"";
        JsonParser p = factory.createParser(json);
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        try {
            p.getText();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // skipping an incomplete string (via nextToken) works across escapes
    @Test
    public void testNextToken_skipIncompleteStringWithEscape_advancesToNext() throws Throwable {
        JsonParser p = factory.createParser("[\"skip\\tme\",\"next\"]");
        p.nextToken();
        p.nextToken();
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("next", p.getText());
    }

    // getTextCharacters/getTextLength/getTextOffset for string value
    @Test
    public void testGetTextCharacters_forStringValue_returnsCorrectChars() throws Throwable {
        JsonParser p = factory.createParser("\"hi\"");
        p.nextToken();
        char[] chars = p.getTextCharacters();
        assertEquals('h', chars[0]);
        assertEquals('i', chars[1]);
        assertEquals(2, p.getTextLength());
        assertEquals(0, p.getTextOffset());
    }

    // getTextCharacters for FIELD_NAME token
    @Test
    public void testGetTextCharacters_forFieldName_returnsCorrectChars() throws Throwable {
        JsonParser p = factory.createParser("{\"key\":1}");
        p.nextToken();
        p.nextToken();
        char[] chars = p.getTextCharacters();
        assertEquals('k', chars[0]);
        assertEquals(3, p.getTextLength());
    }

    // getValueAsString for VALUE_STRING token
    @Test
    public void testGetValueAsString_forStringToken_returnsValue() throws Throwable {
        JsonParser p = factory.createParser("\"abc\"");
        p.nextToken();
        assertEquals("abc", p.getValueAsString());
        assertEquals("abc", p.getValueAsString("default"));
    }

    // getValueAsString for FIELD_NAME token returns current name
    @Test
    public void testGetValueAsString_forFieldName_returnsFieldName() throws Throwable {
        JsonParser p = factory.createParser("{\"f\":1}");
        p.nextToken();
        p.nextToken();
        assertEquals("f", p.getValueAsString());
    }

    // nextTextValue after FIELD_NAME with string value returns the text
    @Test
    public void testNextTextValue_stringAfterFieldName_returnsText() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":\"hello\"}");
        p.nextToken();
        p.nextToken();
        assertEquals("hello", p.nextTextValue());
    }

    // nextTextValue after FIELD_NAME with non-string value returns null
    @Test
    public void testNextTextValue_nonStringAfterFieldName_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":5}");
        p.nextToken();
        p.nextToken();
        assertNull(p.nextTextValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.getCurrentToken());
    }

    // nextIntValue after FIELD_NAME with int value returns the value
    @Test
    public void testNextIntValue_intAfterFieldName_returnsValue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":42}");
        p.nextToken();
        p.nextToken();
        assertEquals(42, p.nextIntValue(-1));
    }

    // nextIntValue after FIELD_NAME with non-int value returns default
    @Test
    public void testNextIntValue_nonIntAfterFieldName_returnsDefault() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":\"x\"}");
        p.nextToken();
        p.nextToken();
        assertEquals(-1, p.nextIntValue(-1));
    }

    // nextLongValue after FIELD_NAME with long value returns the value
    @Test
    public void testNextLongValue_longAfterFieldName_returnsValue() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":123456789012}");
        p.nextToken();
        p.nextToken();
        assertEquals(123456789012L, p.nextLongValue(-1L));
    }

    // nextBooleanValue for true/false after FIELD_NAME
    @Test
    public void testNextBooleanValue_trueAndFalseAfterFieldName_returnsCorrectBoolean() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":true,\"b\":false}");
        p.nextToken();
        p.nextToken();
        assertEquals(Boolean.TRUE, p.nextBooleanValue());
        p.nextToken();
        assertEquals(Boolean.FALSE, p.nextBooleanValue());
    }

    // nextBooleanValue after FIELD_NAME with non-boolean value returns null
    @Test
    public void testNextBooleanValue_nonBooleanAfterFieldName_returnsNull() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":null}");
        p.nextToken();
        p.nextToken();
        assertNull(p.nextBooleanValue());
        assertEquals(JsonToken.VALUE_NULL, p.getCurrentToken());
    }

    // getTokenLocation/getCurrentLocation return non-null location objects
    @Test
    public void testLocations_afterToken_areNotNull() throws Throwable {
        JsonParser p = factory.createParser("123");
        p.nextToken();
        assertNotNull(p.getTokenLocation());
        assertNotNull(p.getCurrentLocation());
    }
}
