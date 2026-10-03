package com.fasterxml.jackson.core.filter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;

public class FilteringParserDelegateClaudeTest
{
    // Covers: constructor field assignment + getFilter() accessor
    @Test
    public void testConstructor_getFilter_returnsProvidedFilter() throws Throwable {
        JsonParser p = new JsonFactory().createParser("1");
        TokenFilter filter = TokenFilter.INCLUDE_ALL;
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, true, true);
        assertSame(filter, fp.getFilter());
    }

    // Covers: getMatchCount() initial state before any tokens are processed
    @Test
    public void testGetMatchCount_initial_returnsZero() throws Throwable {
        JsonParser p = new JsonFactory().createParser("1");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(0, fp.getMatchCount());
    }

    // Covers: getCurrentToken() and currentToken() equivalence
    @Test
    public void testGetCurrentToken_and_currentToken_consistent() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(fp.getCurrentToken(), fp.currentToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.currentToken());
    }

    // Covers: getCurrentTokenId()/currentTokenId() branch returning ID_NO_TOKEN when no token yet
    @Test
    public void testGetCurrentTokenId_beforeAnyToken_returnsIdNoToken() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonTokenId.ID_NO_TOKEN, fp.getCurrentTokenId());
        assertEquals(JsonTokenId.ID_NO_TOKEN, fp.currentTokenId());
    }

    // Covers: getCurrentTokenId() branch returning token's own id after a token is read
    @Test
    public void testGetCurrentTokenId_afterToken_matchesTokenId() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(JsonTokenId.ID_NUMBER_INT, fp.getCurrentTokenId());
    }

    // Covers: hasCurrentToken() false/true branches
    @Test
    public void testHasCurrentToken_falseBeforeTrueAfter() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        assertFalse(fp.hasCurrentToken());
        fp.nextToken();
        assertTrue(fp.hasCurrentToken());
    }

    // Covers: hasTokenId(int) match and mismatch branches
    @Test
    public void testHasTokenId_matchAndMismatch() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.hasTokenId(JsonTokenId.ID_NUMBER_INT));
        assertFalse(fp.hasTokenId(JsonTokenId.ID_STRING));
    }

    // Covers: hasToken(JsonToken) match and mismatch branches
    @Test
    public void testHasToken_matchAndMismatch() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.hasToken(JsonToken.VALUE_NUMBER_INT));
        assertFalse(fp.hasToken(JsonToken.VALUE_STRING));
    }

    // Covers: isExpectedStartArrayToken() true branch and isExpectedStartObjectToken() false branch
    @Test
    public void testIsExpectedStartArrayToken_trueForArrayStart() throws Throwable {
        JsonParser p = new JsonFactory().createParser("[1,2]");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.isExpectedStartArrayToken());
        assertFalse(fp.isExpectedStartObjectToken());
    }

    // Covers: isExpectedStartObjectToken() true branch and isExpectedStartArrayToken() false branch
    @Test
    public void testIsExpectedStartObjectToken_trueForObjectStart() throws Throwable {
        JsonParser p = new JsonFactory().createParser("{\"a\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.isExpectedStartObjectToken());
        assertFalse(fp.isExpectedStartArrayToken());
    }

    // Covers: getCurrentLocation() delegating to underlying parser
    @Test
    public void testGetCurrentLocation_notNull() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertNotNull(fp.getCurrentLocation());
    }

    // Covers: getParsingContext() returning the filter context
    @Test
    public void testGetParsingContext_notNull() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertNotNull(fp.getParsingContext());
    }

    // Covers: getCurrentName() default branch (non start-object/array) returning field name
    @Test
    public void testGetCurrentName_onFieldName_returnsName() throws Throwable {
        JsonParser p = new JsonFactory().createParser("{\"foo\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        fp.nextToken();
        assertEquals("foo", fp.getCurrentName());
    }

    // Covers: getCurrentName() branch for START_OBJECT looking up parent's current name
    @Test
    public void testGetCurrentName_onStartObjectAtRoot_returnsNull() throws Throwable {
        JsonParser p = new JsonFactory().createParser("{\"foo\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertNull(fp.getCurrentName());
    }

    // Covers: clearCurrentToken() branch setting lastClearedToken, and getLastClearedToken()
    @Test
    public void testClearCurrentToken_and_getLastClearedToken() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertNull(fp.getLastClearedToken());
        fp.clearCurrentToken();
        assertNull(fp.getCurrentToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.getLastClearedToken());
    }

    // Covers: overrideCurrentName() always throwing UnsupportedOperationException
    @Test
    public void testOverrideCurrentName_throwsUnsupportedOperationException() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        try {
            fp.overrideCurrentName("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // Covers: nextToken() with rootFilter == INCLUDE_ALL returning every token unchanged, in order
    @Test
    public void testNextToken_includeAllFilter_returnsAllTokensInOrder() throws Throwable {
        JsonParser p = new JsonFactory().createParser("{\"a\":1,\"b\":2}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_OBJECT, fp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, fp.nextToken());
        assertEquals("a", fp.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, fp.nextToken());
        assertEquals("b", fp.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(JsonToken.END_OBJECT, fp.nextToken());
        assertNull(fp.nextToken());
    }

    // Covers known bug: _matchCount must increment when a property filter transitions to INCLUDE_ALL
    @Test
    public void testNextToken_bugMatchCount_incrementsOnIncludeAllMatch() throws Throwable {
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(String name) {
                if ("b".equals(name)) {
                    return TokenFilter.INCLUDE_ALL;
                }
                return null;
            }
        };
        JsonParser p = new JsonFactory().createParser("{\"a\":1,\"b\":2}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, false, true);
        while (fp.nextToken() != null) { }
        assertEquals(1, fp.getMatchCount());
    }

    // Covers: multiple independent INCLUDE_ALL matches must each increment _matchCount
    @Test
    public void testNextToken_multipleMatches_matchCountIncrementsForEach() throws Throwable {
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(String name) {
                if ("a".equals(name) || "b".equals(name)) {
                    return TokenFilter.INCLUDE_ALL;
                }
                return null;
            }
        };
        JsonParser p = new JsonFactory().createParser("{\"a\":1,\"b\":2,\"c\":3}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, false, true);
        while (fp.nextToken() != null) { }
        assertEquals(2, fp.getMatchCount());
    }

    // Covers: ID_FIELD_NAME branch where includeProperty() returns null, so field+value are skipped
    @Test
    public void testNextToken_excludeProperty_fieldAndValueSkipped() throws Throwable {
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(String name) {
                if ("keep".equals(name)) {
                    return TokenFilter.INCLUDE_ALL;
                }
                return null;
            }
        };
        JsonParser p = new JsonFactory().createParser("{\"skip\":{\"x\":1},\"keep\":2}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, false, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(2, fp.getIntValue());
        assertNull(fp.nextToken());
    }

    // Covers: _includePath=true causing buffered path (START_OBJECT + FIELD_NAME) to be emitted before the match
    @Test
    public void testNextToken_includePath_bufferedPathBeforeMatch() throws Throwable {
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(String name) {
                if ("keep".equals(name)) {
                    return TokenFilter.INCLUDE_ALL;
                }
                return null;
            }
        };
        JsonParser p = new JsonFactory().createParser("{\"skip\":1,\"keep\":2}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, true, true);
        assertEquals(JsonToken.START_OBJECT, fp.nextToken());
        assertEquals(JsonToken.FIELD_NAME, fp.nextToken());
        assertEquals("keep", fp.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(2, fp.getIntValue());
        assertEquals(JsonToken.END_OBJECT, fp.nextToken());
        assertNull(fp.nextToken());
    }

    // Covers: !_allowMultipleMatches branch returning null right after the first scalar match at root
    @Test
    public void testNextToken_singleMatchOnly_stopsAfterFirstMatch() throws Throwable {
        JsonParser p = new JsonFactory().createParser("1 2 3");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, false, false);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(1, fp.getIntValue());
        assertNull(fp.nextToken());
    }

    // Covers: ID_START_ARRAY branch transitioning itemFilter to INCLUDE_ALL via filterStartArray()
    @Test
    public void testNextToken_startArrayIncludeAll_matchCountIncrements() throws Throwable {
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter filterStartArray() {
                return TokenFilter.INCLUDE_ALL;
            }
        };
        JsonParser p = new JsonFactory().createParser("[1,2,3]");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, false, true);
        while (fp.nextToken() != null) { }
        assertEquals(1, fp.getMatchCount());
    }

    // Covers: ID_START_OBJECT branch transitioning itemFilter to INCLUDE_ALL via filterStartObject()
    @Test
    public void testNextToken_startObjectIncludeAll_matchCountIncrements() throws Throwable {
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter filterStartObject() {
                return TokenFilter.INCLUDE_ALL;
            }
        };
        JsonParser p = new JsonFactory().createParser("{\"a\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, false, true);
        while (fp.nextToken() != null) { }
        assertEquals(1, fp.getMatchCount());
    }

    // Covers: default scalar branch where itemFilter != INCLUDE_ALL, inclusion decided via includeValue()
    @Test
    public void testNextToken_scalarIncludeValueAlwaysTrue_includesEachElement() throws Throwable {
        TokenFilter filter = new TokenFilter() {
            @Override
            public boolean includeValue(JsonParser parser) {
                return true;
            }
        };
        JsonParser p = new JsonFactory().createParser("[1,2,3]");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, filter, false, true);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(1, fp.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextToken());
        assertEquals(2, fp.getIntValue());
    }

    // Covers: nextValue() skipping FIELD_NAME to return the property's value token
    @Test
    public void testNextValue_skipsFieldNameReturnsValue() throws Throwable {
        JsonParser p = new JsonFactory().createParser("{\"a\":1}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        assertEquals(JsonToken.START_OBJECT, fp.nextValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.nextValue());
        assertEquals("a", fp.getCurrentName());
    }

    // Covers: skipChildren() branch returning immediately for non-container current token
    @Test
    public void testSkipChildren_onNonContainerToken_returnsSameParserNoChange() throws Throwable {
        JsonParser p = new JsonFactory().createParser("42");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        JsonParser result = fp.skipChildren();
        assertSame(fp, result);
        assertEquals(JsonToken.VALUE_NUMBER_INT, fp.getCurrentToken());
    }

    // Covers: skipChildren() loop counting nested START/END tokens until matching END_OBJECT is found
    @Test
    public void testSkipChildren_onStartObject_skipsToMatchingEndObject() throws Throwable {
        JsonParser p = new JsonFactory().createParser("{\"a\":{\"b\":1}}");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        fp.skipChildren();
        assertEquals(JsonToken.END_OBJECT, fp.getCurrentToken());
        assertNull(fp.nextToken());
    }

    // Covers: getText() delegating to underlying parser
    @Test
    public void testGetText_returnsUnderlyingText() throws Throwable {
        JsonParser p = new JsonFactory().createParser("\"hello\"");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals("hello", fp.getText());
    }

    // Covers: getIntValue() delegating to underlying parser
    @Test
    public void testGetIntValue_returnsCorrectInt() throws Throwable {
        JsonParser p = new JsonFactory().createParser("123");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(123, fp.getIntValue());
    }

    // Covers: getBooleanValue() delegating to underlying parser
    @Test
    public void testGetBooleanValue_returnsCorrectBoolean() throws Throwable {
        JsonParser p = new JsonFactory().createParser("true");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertTrue(fp.getBooleanValue());
    }

    // Covers: getLongValue() delegating to underlying parser for values beyond int range
    @Test
    public void testGetLongValue_returnsCorrectLong() throws Throwable {
        JsonParser p = new JsonFactory().createParser("9999999999");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(9999999999L, fp.getLongValue());
    }

    // Covers: getDoubleValue() delegating to underlying parser
    @Test
    public void testGetDoubleValue_returnsCorrectDouble() throws Throwable {
        JsonParser p = new JsonFactory().createParser("3.5");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(3.5, fp.getDoubleValue(), 1e-9);
    }

    // Covers: getBigIntegerValue() delegating to underlying parser for very large integers
    @Test
    public void testGetBigIntegerValue_returnsCorrectBigInteger() throws Throwable {
        JsonParser p = new JsonFactory().createParser("123456789012345678901234567890");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(new BigInteger("123456789012345678901234567890"), fp.getBigIntegerValue());
    }

    // Covers: getDecimalValue() delegating to underlying parser
    @Test
    public void testGetDecimalValue_returnsCorrectBigDecimal() throws Throwable {
        JsonParser p = new JsonFactory().createParser("3.14");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(new BigDecimal("3.14"), fp.getDecimalValue());
    }

    // Covers: getValueAsString() delegating to underlying parser
    @Test
    public void testGetValueAsString_returnsCorrectString() throws Throwable {
        JsonParser p = new JsonFactory().createParser("\"hi\"");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals("hi", fp.getValueAsString());
    }

    // Covers: getValueAsInt(int) delegating to underlying parser and parsing the numeric text
    @Test
    public void testGetValueAsInt_returnsParsedValue() throws Throwable {
        JsonParser p = new JsonFactory().createParser("77");
        FilteringParserDelegate fp = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        fp.nextToken();
        assertEquals(77, fp.getValueAsInt(-1));
    }
}
