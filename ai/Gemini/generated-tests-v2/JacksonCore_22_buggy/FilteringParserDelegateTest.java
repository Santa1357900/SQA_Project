package com.fasterxml.jackson.core.filter;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonParser.NumberType;

public class FilteringParserDelegateTest {

    @Test
    public void testConstructionAndBasicGetters() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1}");
        TokenFilter filter = TokenFilter.INCLUDE_ALL;
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, filter, true, true);

        assertSame(filter, delegate.getFilter());
        assertEquals(0, delegate.getMatchCount());
        assertFalse(delegate.hasCurrentToken());
        assertNull(delegate.getCurrentToken());
        assertNull(delegate.currentToken());
        assertEquals(JsonTokenId.ID_NO_TOKEN, delegate.getCurrentTokenId());
        assertEquals(JsonTokenId.ID_NO_TOKEN, delegate.currentTokenId());
        assertFalse(delegate.hasTokenId(JsonTokenId.ID_NO_TOKEN));
        assertFalse(delegate.hasToken(JsonToken.START_OBJECT));
        assertFalse(delegate.isExpectedStartArrayToken());
        assertFalse(delegate.isExpectedStartObjectToken());
        assertNotNull(delegate.getCurrentLocation());
        assertNotNull(delegate.getParsingContext());
        assertNull(delegate.getCurrentName());
        assertNull(delegate.getLastClearedToken());

        delegate.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testOverrideCurrentNameThrowsException() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        try {
            delegate.overrideCurrentName("test");
        } finally {
            delegate.close();
        }
    }

    @Test
    public void testClearCurrentToken() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        
        // Initially null, clear should do nothing
        delegate.clearCurrentToken();
        assertNull(delegate.getLastClearedToken());

        // Read first token
        JsonToken t = delegate.nextToken();
        assertNotNull(t);
        assertTrue(delegate.hasCurrentToken());

        delegate.clearCurrentToken();
        assertFalse(delegate.hasCurrentToken());
        assertEquals(t, delegate.getLastClearedToken());

        delegate.close();
    }

    @Test
    public void testFilteringSimpleObjectIncludeAll() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(JsonToken.START_OBJECT, delegate.nextToken());
        assertTrue(delegate.hasToken(JsonToken.START_OBJECT));
        assertEquals(JsonToken.FIELD_NAME, delegate.nextToken());
        assertEquals("a", delegate.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextToken());
        assertEquals(1, delegate.getIntValue());
        assertEquals(JsonToken.END_OBJECT, delegate.nextToken());
        assertNull(delegate.nextToken());

        delegate.close();
    }

    @Test
    public void testFilteringSimpleArrayIncludeAll() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("[1, 2]");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(JsonToken.START_ARRAY, delegate.nextToken());
        assertTrue(delegate.isExpectedStartArrayToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextToken());
        assertEquals(1, delegate.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextToken());
        assertEquals(2, delegate.getIntValue());
        assertEquals(JsonToken.END_ARRAY, delegate.nextToken());
        assertNull(delegate.nextToken());

        delegate.close();
    }

    @Test
    public void testNextValue() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(JsonToken.START_OBJECT, delegate.nextValue());
        // FIELD_NAME followed by VALUE_NUMBER_INT should return VALUE_NUMBER_INT for nextValue()
        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextValue());
        assertEquals(1, delegate.getIntValue());
        assertEquals(JsonToken.END_OBJECT, delegate.nextValue());
        assertNull(delegate.nextValue());

        delegate.close();
    }

    @Test
    public void testSkipChildren() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":[1, 2], \"b\":2}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);

        // Not at start of struct
        assertSame(delegate, delegate.skipChildren());

        assertEquals(JsonToken.START_OBJECT, delegate.nextToken());
        // Now at START_OBJECT, skipChildren should skip until END_OBJECT
        assertSame(delegate, delegate.skipChildren());
        assertEquals(JsonToken.END_OBJECT, delegate.getCurrentToken());

        delegate.close();
    }

    @Test
    public void testValueAccessorsAndCoercions() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"str\":\"test\", \"num\":123, \"bool\":true, \"bigInt\":123456789, \"dec\":12.34, \"bin\":\"YQ==\"}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);

        while (delegate.nextToken() != null) {
            // Exercise wrapper methods to ensure delegation works without error
            delegate.getText();
            delegate.hasTextCharacters();
            delegate.getTextCharacters();
            delegate.getTextLength();
            delegate.getTextOffset();
            delegate.getBigIntegerValue();
            delegate.getBooleanValue();
            delegate.getByteValue();
            delegate.getShortValue();
            delegate.getDecimalValue();
            delegate.getDoubleValue();
            delegate.getFloatValue();
            delegate.getIntValue();
            delegate.getLongValue();
            delegate.getNumberType();
            delegate.getNumberValue();
            delegate.getValueAsInt();
            delegate.getValueAsInt(0);
            delegate.getValueAsLong();
            delegate.getValueAsLong(0L);
            delegate.getValueAsDouble();
            delegate.getValueAsDouble(0.0);
            delegate.getValueAsBoolean();
            delegate.getValueAsBoolean(false);
            delegate.getValueAsString();
            delegate.getValueAsString("default");
            delegate.getEmbeddedObject();
            delegate.getTokenLocation();
            if (delegate.getCurrentToken() == JsonToken.VALUE_STRING) {
                delegate.getBinaryValue(Base64Variant.getDefaultVariant());
            }
        }

        delegate.close();
    }

    @Test
    public void testAllowMultipleMatchesFalseScalar() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("1");
        // allowMultipleMatches = false, includePath = false
        TokenFilter filter = TokenFilter.INCLUDE_ALL;
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, filter, false, false);

        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextToken());
        // Second call should return null because allowMultipleMatches is false and it's a scalar matched once
        assertNull(delegate.nextToken());

        delegate.close();
    }

    @Test
    public void testSpecificTokenFilterExclusions() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1, \"b\":2}");
        
        // Custom filter that only includes property "a"
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(string name) {
                if ("a".equals(name)) {
                    return TokenFilter.INCLUDE_ALL;
                }
                return null;
            }
            @Override
            public TokenFilter includeRootValue(int index) {
                return TokenFilter.INCLUDE_ALL;
            }
        };

        FilteringParserDelegate delegate = new FilteringParserDelegate(p, filter, true, true);
        
        JsonToken t;
        boolean foundA = false;
        while ((t = delegate.nextToken()) != null) {
            if (t == JsonToken.FIELD_NAME && "a".equals(delegate.getCurrentName())) {
                foundA = true;
            }
        }
        assertTrue(foundA);

        delegate.close();
    }

    @Test
    public void testDelegateReadBinaryValue() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("\"YQ==\"");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        while (delegate.nextToken() != null) {
            if (delegate.getCurrentToken() == JsonToken.VALUE_STRING) {
                delegate.readBinaryValue(Base64Variant.getDefaultVariant(), out);
            }
        }
        delegate.close();
    }
}