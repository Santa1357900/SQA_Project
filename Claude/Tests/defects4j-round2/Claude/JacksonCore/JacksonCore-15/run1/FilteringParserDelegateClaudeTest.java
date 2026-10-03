package com.fasterxml.jackson.core.filter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilteringParserDelegateClaudeTest {

    // Filter that only includes property "b" of an object, wrapping path context
    private static class IncludeOnlyBFilter extends TokenFilter {
        @Override
        public TokenFilter filterStartObject() {
            return this;
        }
        @Override
        public TokenFilter includeProperty(String name) {
            if ("b".equals(name)) {
                return TokenFilter.INCLUDE_ALL;
            }
            return null;
        }
    }

    // Filter used to detect wrong lifecycle callback invocation (bug hunt)
    private static class RecordingFilter extends TokenFilter {
        boolean finishArrayCalled = false;
        @Override
        public TokenFilter filterStartObject() {
            return this;
        }
        @Override
        public TokenFilter includeProperty(String name) {
            return TokenFilter.INCLUDE_ALL;
        }
        @Override
        public void filterFinishArray() {
            finishArrayCalled = true;
        }
    }

    private static class ExcludeValueFilter extends TokenFilter {
        @Override
        public boolean includeValue(JsonParser parser) {
            return false;
        }
    }

    private static class IncludeValueFilter extends TokenFilter {
        @Override
        public boolean includeValue(JsonParser parser) {
            return true;
        }
    }

    private static class NullStartArrayFilter extends TokenFilter {
        @Override
        public TokenFilter filterStartArray() {
            return null;
        }
    }

    // covers constructor + getFilter()
    @Test
    public void testConstructor_getFilter_returnsProvidedFilter() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        TokenFilter filter = TokenFilter.INCLUDE_ALL;
        FilteringParserDelegate fp = new FilteringParserDelegate(under, filter, true, false);
        assertSame(filter, fp.getFilter());
    }

    // covers getMatchCount() default value
    @Test
    public void testGetMatchCount_initiallyZero() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(0, fp.getMatchCount());
    }

    // covers getCurrentTokenId()/hasCurrentToken()/getCurrentToken()/hasTokenId() before any token
    @Test
    public void testGetCurrentTokenId_beforeAnyToken_returnsNoToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonTokenId.ID_NO_TOKEN, fp.getCurrentTokenId());
        assertFalse(fp.hasCurrentToken());
        assertNull(fp.getCurrentToken());
        assertTrue(fp.hasTokenId(JsonTokenId.ID_NO_TOKEN));
    }

    // covers hasCurrentToken() before/after nextToken()
    @Test
    public void testHasCurrentToken_beforeAndAfterNextToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertFalse(fp.hasCurrentToken());
        fp.nextToken();
        assertTrue(fp.hasCurrentToken());
    }

    // covers INCLUDE_ALL root filter passing through array tokens unchanged
    @Test
    public void testNextToken_includeAllFilter_passesThroughArrayTokens() throws Throwable {
        JsonParser under = new JsonFactory().createParser("[1,2,3]");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_ARRAY, fp.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(1, fp.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(2, fp.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(3, fp.getIntValue());
        assertEquals(JsonToken.END_ARRAY, fp.nextToken());
        assertNull(fp.nextToken());
    }

    // covers hasTokenId()/getCurrentTokenId() after a real token
    @Test
    public void testHasTokenId_and_getCurrentTokenId_afterToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("[1]");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(JsonTokenId.ID_START_ARRAY, fp.getCurrentTokenId());
        assertTrue(fp.hasTokenId(JsonTokenId.ID_START_ARRAY));
        assertFalse(fp.hasTokenId(JsonTokenId.ID_END_ARRAY));
    }

    // covers hasToken(JsonToken)
    @Test
    public void testHasToken_matchesExactToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("[1]");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.hasToken(JsonToken.START_ARRAY));
        assertFalse(fp.hasToken(JsonToken.END_ARRAY));
    }

    // covers isExpectedStartArrayToken()/isExpectedStartObjectToken() for array
    @Test
    public void testIsExpectedStartArrayToken_true_forArrayStart() throws Throwable {
        JsonParser under = new JsonFactory().createParser("[1]");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.isExpectedStartArrayToken());
        assertFalse(fp.isExpectedStartObjectToken());
    }

    // covers isExpectedStartObjectToken()/isExpectedStartArrayToken() for object
    @Test
    public void testIsExpectedStartObjectToken_true_forObjectStart() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"a\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.isExpectedStartObjectToken());
        assertFalse(fp.isExpectedStartArrayToken());
    }

    // covers getCurrentLocation() delegation
    @Test
    public void testGetCurrentLocation_notNull() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertNotNull(fp.getCurrentLocation());
    }

    // covers getParsingContext()
    @Test
    public void testGetParsingContext_notNull() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertNotNull(fp.getParsingContext());
    }

    // covers getCurrentName() for a FIELD_NAME token
    @Test
    public void testGetCurrentName_forFieldName() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"foo\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken(); // START_OBJECT
        fp.nextToken(); // FIELD_NAME
        assertEquals("foo", fp.getCurrentName());
    }

    // covers getCurrentName() branch: START_OBJECT returns parent's current name
    @Test
    public void testGetCurrentName_forStartObject_returnsParentFieldName() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"a\":{\"b\":1}}");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_OBJECT, fp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, fp.nextToken());
        assertEquals(JsonToken.START_OBJECT, fp.nextToken());
        assertEquals("a", fp.getCurrentName());
    }

    // covers getCurrentName() branch: root-level START_OBJECT has no parent name
    @Test
    public void testGetCurrentName_forRootStartObject_returnsNull() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"a\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_OBJECT, fp.nextToken());
        assertNull(fp.getCurrentName());
    }

    // covers clearCurrentToken()/getLastClearedToken() when a token is set
    @Test
    public void testClearCurrentToken_clearsAndRecordsLastCleared() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        fp.clearCurrentToken();
        assertNull(fp.getCurrentToken());
        assertFalse(fp.hasCurrentToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.getLastClearedToken());
    }

    // covers clearCurrentToken() no-op branch when there is no current token
    @Test
    public void testClearCurrentToken_noCurrentToken_isNoOp() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.clearCurrentToken();
        assertNull(fp.getLastClearedToken());
    }

    // covers overrideCurrentName() always throwing
    @Test
    public void testOverrideCurrentName_throwsUnsupportedOperationException() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        try {
            fp.overrideCurrentName("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // covers property-based filtering with includePath=true wrapping structure
    @Test
    public void testNextToken_propertyFilterIncludePathTrue_producesWrappedStructure() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"a\":1,\"b\":2,\"c\":3}");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, new IncludeOnlyBFilter(), true, true);
        assertEquals(JsonToken.START_OBJECT, fp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, fp.nextToken());
        assertEquals("b", fp.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(2, fp.getIntValue());
        assertEquals(JsonToken.END_OBJECT, fp.nextToken());
        assertNull(fp.nextToken());
    }

    // covers property-based filtering with includePath=false, only raw value output
    @Test
    public void testNextToken_propertyFilterIncludePathFalse_producesRawValueOnly() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"a\":1,\"b\":2,\"c\":3}");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, new IncludeOnlyBFilter(), false, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(2, fp.getIntValue());
        assertNull(fp.nextToken());
    }

    // covers ID_START_ARRAY branch where filterStartArray() returns null -> whole array excluded
    @Test
    public void testNextToken_filterStartArrayReturnsNull_excludesEntireArray() throws Throwable {
        JsonParser under = new JsonFactory().createParser("[1,2,3]");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, new NullStartArrayFilter(), true, true);
        assertNull(fp.nextToken());
    }

    // covers branch where root item filter itself is null -> skipChildren used
    @Test
    public void testNextToken_nullRootFilter_skipsChildrenAndReturnsNull() throws Throwable {
        JsonParser under = new JsonFactory().createParser("[1,2,3]");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, null, true, true);
        assertNull(fp.nextToken());
    }

    // covers scalar default branch: includeValue() returning false excludes scalar
    @Test
    public void testNextToken_includeValueFalse_excludesScalar() throws Throwable {
        JsonParser under = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, new ExcludeValueFilter(), true, true);
        assertNull(fp.nextToken());
    }

    // covers scalar default branch: includeValue() returning true includes scalar
    @Test
    public void testNextToken_includeValueTrue_includesScalar() throws Throwable {
        JsonParser under = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, new IncludeValueFilter(), true, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(42, fp.getIntValue());
    }

    // covers nextValue() skipping FIELD_NAME to return the actual value token
    @Test
    public void testNextValue_skipsFieldNameAndReturnsValueToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"a\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_OBJECT, fp.nextValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextValue());
        assertEquals(1, fp.getIntValue());
    }

    // covers skipChildren() skipping a nested array to its matching END_ARRAY
    @Test
    public void testSkipChildren_onArrayStart_skipsToMatchingEndArray() throws Throwable {
        JsonParser under = new JsonFactory().createParser("[1,[2,3],4]");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_ARRAY, fp.nextToken());
        JsonParser result = fp.skipChildren();
        assertSame(fp, result);
        assertEquals(JsonToken.END_ARRAY, fp.getCurrentToken());
        assertNull(fp.nextToken());
    }

    // covers skipChildren() no-op branch for a non-structural current token
    @Test
    public void testSkipChildren_onScalarToken_isNoOp() throws Throwable {
        JsonParser under = new JsonFactory().createParser("true");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_TRUE, fp.nextToken());
        fp.skipChildren();
        assertEquals(JsonToken.VALUE_TRUE, fp.getCurrentToken());
    }

    // covers getText()/getTextLength() delegation for a string token
    @Test
    public void testGetText_and_getTextLength_forStringToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("\"hello\"");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_STRING, fp.nextToken());
        assertEquals("hello", fp.getText());
        assertEquals(5, fp.getTextLength());
    }

    // covers getBigIntegerValue() delegation
    @Test
    public void testGetBigIntegerValue_forLargeIntegerToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("123456789012345");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(new BigInteger("123456789012345"), fp.getBigIntegerValue());
    }

    // covers getBooleanValue() delegation
    @Test
    public void testGetBooleanValue_forBooleanToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("true");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_TRUE, fp.nextToken());
        assertTrue(fp.getBooleanValue());
    }

    // covers getDecimalValue() delegation
    @Test
    public void testGetDecimalValue_forDecimalToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1.5");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, fp.nextToken());
        assertEquals(0, fp.getDecimalValue().compareTo(new BigDecimal("1.5")));
    }

    // covers getDoubleValue()/getFloatValue() delegation
    @Test
    public void testGetDoubleValue_and_getFloatValue_forDecimalToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1.5");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, fp.nextToken());
        assertEquals(1.5, fp.getDoubleValue(), 1e-9);
        assertEquals(1.5f, fp.getFloatValue(), 1e-6f);
    }

    // covers getIntValue()/getLongValue() delegation
    @Test
    public void testGetIntValue_and_getLongValue_forIntToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("100");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(100, fp.getIntValue());
        assertEquals(100L, fp.getLongValue());
    }

    // covers getNumberType() delegation
    @Test
    public void testGetNumberType_forIntToken() throws Throwable {
        JsonParser under = new JsonFactory().createParser("7");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(JsonParser.NumberType.INT, fp.getNumberType());
    }

    // covers getValueAsInt()/getValueAsString() coercion delegation
    @Test
    public void testGetValueAsInt_and_getValueAsString_conversions() throwsThrowable {
        JsonParser under = new JsonFactory().createParser("\"123\"");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_STRING, fp.nextToken());
        assertEquals(123, fp.getValueAsInt());
        assertEquals("123", fp.getValueAsString());
    }

    // covers getValueAsBoolean(default) returning default when conversion not possible
    @Test
    public void testGetValueAsBoolean_withDefaultValue() throws Throwable {
        JsonParser under = new JsonFactory().createParser("\"notBoolean\"");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_STRING, fp.nextToken());
        assertFalse(fp.getValueAsBoolean(false));
    }

    // covers getEmbeddedObject() delegation for a plain scalar token
    @Test
    public void testGetEmbeddedObject_forScalarToken_returnsNull() throws Throwable {
        JsonParser under = new JsonFactory().createParser("123");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertNull(fp.getEmbeddedObject());
    }

    // covers getTokenLocation() delegation
    @Test
    public void testGetTokenLocation_notNull() throws Throwable {
        JsonParser under = new JsonFactory().createParser("123");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertNotNull(fp.getTokenLocation());
    }

    // Bug hunt: closing a JSON OBJECT must not invoke the array-only filterFinishArray() callback
    @Test
    public void testNextToken_endObject_mustNotInvokeFilterFinishArray() throws Throwable {
        JsonParser under = new JsonFactory().createParser("{\"a\":1}");
        RecordingFilter filter = new RecordingFilter();
        FilteringParserDelegate fp = new FilteringParserDelegate(under, filter, false, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertNull(fp.nextToken());
        assertFalse("filterFinishArray() must not be invoked for an OBJECT end", filter.finishArrayCalled);
    }

    // covers nextToken() returning null at end of input and remaining stable on repeated calls
    @Test
    public void testNextToken_nullAtEndOfInput_repeatedCallsReturnNull() throws Throwable {
        JsonParser under = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(under, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertNull(fp.nextToken());
        assertNull(fp.nextToken());
    }
}
