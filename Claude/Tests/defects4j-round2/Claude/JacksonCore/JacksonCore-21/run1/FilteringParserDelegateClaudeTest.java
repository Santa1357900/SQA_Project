package com.fasterxml.jackson.core.filter;

import java.io.IOException;
import java.math.BigInteger;
import java.math.BigDecimal;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;

public class FilteringParserDelegateClaudeTest
{
    private static class NameMatchFilter extends TokenFilter
    {
        private final String _name;
        NameMatchFilter(String name) { _name = name; }
        @Override
        public TokenFilter includeProperty(String name) {
            if (_name.equals(name)) {
                return TokenFilter.INCLUDE_ALL;
            }
            return null;
        }
    }

    private static class ValueMatchFilter extends TokenFilter
    {
        private final int _match;
        ValueMatchFilter(int match) { _match = match; }
        @Override
        public boolean includeValue(JsonParser p) throws IOException {
            return p.getIntValue() == _match;
        }
    }

    private FilteringParserDelegate createDelegate(String json, TokenFilter filter,
            boolean includePath, boolean allowMultipleMatches) throws IOException {
        JsonParser p = new JsonFactory().createParser(json);
        return new FilteringParserDelegate(p, filter, includePath, allowMultipleMatches);
    }

    // constructor stores filter; getFilter() returns exact instance
    @Test
    public void testGetFilter_returnsProvidedFilter() throws Throwable {
        TokenFilter filter = TokenFilter.INCLUDE_ALL;
        FilteringParserDelegate d = createDelegate("1", filter, true, true);
        assertSame(filter, d.getFilter());
    }

    // getMatchCount() must start at zero before any parsing
    @Test
    public void testGetMatchCount_initiallyZero() throws Throwable {
        FilteringParserDelegate d = createDelegate("1", TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(0, d.getMatchCount());
    }

    // contract: matchCount counts tokens where INCLUDE_ALL was returned by filter -> must be >0 after a match
    @Test
    public void testGetMatchCount_afterPropertyMatch_isPositive() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"a\":1,\"b\":2}",
                new NameMatchFilter("b"), true, true);
        while (d.nextToken() != null) { }
        assertTrue(d.getMatchCount() > 0);
    }

    // getCurrentToken()/currentToken() null before first nextToken() call
    @Test
    public void testGetCurrentToken_beforeNext_isNull() throws Throwable {
        FilteringParserDelegate d = createDelegate("1", TokenFilter.INCLUDE_ALL, true, true);
        assertNull(d.getCurrentToken());
        assertNull(d.currentToken());
    }

    // getCurrentTokenId()/currentTokenId() return ID_NO_TOKEN before any token read
    @Test
    public void testCurrentTokenId_beforeNext_isNoToken() throws Throwable {
        FilteringParserDelegate d = createDelegate("1", TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonTokenId.ID_NO_TOKEN, d.getCurrentTokenId());
        assertEquals(JsonTokenId.ID_NO_TOKEN, d.currentTokenId());
    }

    // hasCurrentToken() false before, true after nextToken()
    @Test
    public void testHasCurrentToken_falseBeforeTrueAfter() throws Throwable {
        FilteringParserDelegate d = createDelegate("true", TokenFilter.INCLUDE_ALL, true, true);
        assertFalse(d.hasCurrentToken());
        d.nextToken();
        assertTrue(d.hasCurrentToken());
    }

    // hasTokenId(ID_NO_TOKEN) true initially, false for other ids
    @Test
    public void testHasTokenId_matchesNoTokenInitially() throws Throwable {
        FilteringParserDelegate d = createDelegate("true", TokenFilter.INCLUDE_ALL, true, true);
        assertTrue(d.hasTokenId(JsonTokenId.ID_NO_TOKEN));
        assertFalse(d.hasTokenId(JsonTokenId.ID_START_OBJECT));
    }

    // hasToken(null) true while current token is null; false for START_OBJECT before object seen
    @Test
    public void testHasToken_nullMatchesInitialState() throws Throwable {
        FilteringParserDelegate d = createDelegate("true", TokenFilter.INCLUDE_ALL, true, true);
        assertTrue(d.hasToken(null));
        assertFalse(d.hasToken(JsonToken.START_OBJECT));
    }

    // isExpectedStartArrayToken() true only when currentToken is START_ARRAY
    @Test
    public void testIsExpectedStartArrayToken_trueForArrayStart() throws Throwable {
        FilteringParserDelegate d = createDelegate("[1,2]", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertTrue(d.isExpectedStartArrayToken());
        assertFalse(d.isExpectedStartObjectToken());
    }

    // isExpectedStartObjectToken() true only when currentToken is START_OBJECT
    @Test
    public void testIsExpectedStartObjectToken_trueForObjectStart() throws Throwable {
        FilteringParserDelegate d = createDelegate("{}", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertTrue(d.isExpectedStartObjectToken());
        assertFalse(d.isExpectedStartArrayToken());
    }

    // getCurrentLocation() delegates to underlying parser, must not be null
    @Test
    public void testGetCurrentLocation_notNull() throws Throwable {
        FilteringParserDelegate d = createDelegate("1", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertNotNull(d.getCurrentLocation());
    }

    // getParsingContext() returns non-null context, reflects object (not array) state
    @Test
    public void testGetParsingContext_notNullAndReflectsState() throws Throwable {
        FilteringParserDelegate d = createDelegate("{}", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertNotNull(d.getParsingContext());
        assertFalse(d.getParsingContext().inArray());
    }

    // getCurrentName() returns field name while positioned on FIELD_NAME token
    @Test
    public void testGetCurrentName_atFieldName_returnsName() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"fieldA\":1}", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        d.nextToken();
        assertEquals("fieldA", d.getCurrentName());
    }

    // clearCurrentToken() nulls current token and records last cleared token
    @Test
    public void testClearCurrentToken_setsLastClearedAndNullsCurrent() throws Throwable {
        FilteringParserDelegate d = createDelegate("true", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        d.clearCurrentToken();
        assertNull(d.getCurrentToken());
        assertEquals(JsonToken.VALUE_TRUE, d.getLastClearedToken());
    }

    // getLastClearedToken() null before any clearCurrentToken() call
    @Test
    public void testGetLastClearedToken_initiallyNull() throws Throwable {
        FilteringParserDelegate d = createDelegate("true", TokenFilter.INCLUDE_ALL, true, true);
        assertNull(d.getLastClearedToken());
    }

    // overrideCurrentName() must throw UnsupportedOperationException per implementation contract
    @Test
    public void testOverrideCurrentName_throwsUnsupportedOperationException() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"a\":1}", TokenFilter.INCLUDE_ALL, true, true);
        try {
            d.overrideCurrentName("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }

    // full passthrough with INCLUDE_ALL filter: sequence matches raw parser tokens exactly
    @Test
    public void testNextToken_includeAllPassthrough_matchesRawSequence() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"x\":1}", TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_OBJECT, d.nextToken());
        assertEquals(JsonToken.FIELD_NAME, d.nextToken());
        assertEquals("x", d.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, d.nextToken());
        assertEquals(1, d.getIntValue());
        assertEquals(JsonToken.END_OBJECT, d.nextToken());
        assertNull(d.nextToken());
    }

    // nextToken() at EOF returns null and remains null on subsequent calls
    @Test
    public void testNextToken_afterEOF_returnsNullRepeatedly() throws Throwable {
        FilteringParserDelegate d = createDelegate("1", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertNull(d.nextToken());
        assertNull(d.nextToken());
    }

    // nextValue() skips FIELD_NAME token and returns the value token
    @Test
    public void testNextValue_skipsFieldNameReturnsValueToken() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"a\":1}", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        JsonToken t = d.nextValue();
        assertEquals(JsonToken.VALUE_NUMBER_INT, t);
        assertEquals(1, d.getIntValue());
    }

    // skipChildren() on START_OBJECT skips nested content until matching END_OBJECT
    @Test
    public void testSkipChildren_onStartObject_skipsNested() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"a\":{\"b\":1},\"c\":2}",
                TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        d.nextToken();
        d.nextToken();
        d.skipChildren();
        assertEquals(JsonToken.END_OBJECT, d.getCurrentToken());
        assertEquals(JsonToken.FIELD_NAME, d.nextToken());
        assertEquals("c", d.getCurrentName());
    }

    // skipChildren() on non-structured token is a no-op
    @Test
    public void testSkipChildren_onScalarToken_noOp() throws Throwable {
        FilteringParserDelegate d = createDelegate("42", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        d.skipChildren();
        assertEquals(JsonToken.VALUE_NUMBER_INT, d.getCurrentToken());
        assertEquals(42, d.getIntValue());
    }

    // getText() returns string value content
    @Test
    public void testGetText_onStringValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("\"hello\"", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals("hello", d.getText());
    }

    // getIntValue() returns parsed int
    @Test
    public void testGetIntValue_onIntValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("123", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals(123, d.getIntValue());
    }

    // getBooleanValue() returns parsed boolean
    @Test
    public void testGetBooleanValue_onBooleanValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("true", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertTrue(d.getBooleanValue());
    }

    // getDoubleValue() returns parsed double with delta tolerance
    @Test
    public void testGetDoubleValue_onDoubleValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("3.5", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals(3.5, d.getDoubleValue(), 1e-9);
    }

    // getLongValue() returns parsed long for large integer literal
    @Test
    public void testGetLongValue_onLongValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("1234567890123", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals(1234567890123L, d.getLongValue());
    }

    // getBigIntegerValue() returns correct BigInteger
    @Test
    public void testGetBigIntegerValue_onIntValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("123", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals(BigInteger.valueOf(123), d.getBigIntegerValue());
    }

    // getDecimalValue() returns correct BigDecimal
    @Test
    public void testGetDecimalValue_onDoubleValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("3.5", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals(0, new BigDecimal("3.5").compareTo(d.getDecimalValue()));
    }

    // getValueAsInt() default and explicit overloads both return parsed int
    @Test
    public void testGetValueAsInt_defaultAndExplicit() throws Throwable {
        FilteringParserDelegate d = createDelegate("123", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals(123, d.getValueAsInt());
        assertEquals(123, d.getValueAsInt(999));
    }

    // getValueAsString() returns string content
    @Test
    public void testGetValueAsString_onStringValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("\"hello\"", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals("hello", d.getValueAsString());
    }

    // getNumberType() returns INT for integer literal
    @Test
    public void testGetNumberType_onIntValue() throws Throwable {
        FilteringParserDelegate d = createDelegate("123", TokenFilter.INCLUDE_ALL, true, true);
        d.nextToken();
        assertEquals(JsonParser.NumberType.INT, d.getNumberType());
    }

    // property-name filter with includePath=true reconstructs full path around matched property
    @Test
    public void testNameMatchFilter_includePathTrue_fullSequence() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"a\":1,\"b\":2}",
                new NameMatchFilter("b"), true, true);
        assertEquals(JsonToken.START_OBJECT, d.nextToken());
        assertEquals(JsonToken.FIELD_NAME, d.nextToken());
        assertEquals("b", d.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, d.nextToken());
        assertEquals(2, d.getIntValue());
        assertEquals(JsonToken.END_OBJECT, d.nextToken());
        assertNull(d.nextToken());
    }

    // property-name filter that matches nothing yields no output at all
    @Test
    public void testNameMatchFilter_noMatch_returnsNullImmediately() throws Throwable {
        FilteringParserDelegate d = createDelegate("{\"a\":1,\"b\":2}",
                new NameMatchFilter("z"), true, true);
        assertNull(d.nextToken());
        assertNull(d.getCurrentToken());
    }

    // scalar-value filter (includeValue) that matches includes the root scalar
    @Test
    public void testIncludeValueFilter_scalarMatch_included() throws Throwable {
        FilteringParserDelegate d = createDelegate("42", new ValueMatchFilter(42), false, false);
        assertEquals(JsonToken.VALUE_NUMBER_INT, d.nextToken());
        assertEquals(42, d.getIntValue());
    }

    // scalar-value filter (includeValue) that does not match excludes the root scalar
    @Test
    public void testIncludeValueFilter_scalarNoMatch_excluded() throws Throwable {
        FilteringParserDelegate d = createDelegate("7", new ValueMatchFilter(42), false, false);
        assertNull(d.nextToken());
    }
}
