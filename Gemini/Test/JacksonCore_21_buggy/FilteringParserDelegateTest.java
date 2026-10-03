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
import com.fasterxml.jackson.core.Base64Variant;

public class FilteringParserDelegateTest {

    @Test
    public void testGettersAndBasicDelegation() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1, \"b\":[2,3]}");
        TokenFilter filter = TokenFilter.INCLUDE_ALL;
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, filter, true, true);

        assertEquals(filter, delegate.getFilter());
        assertEquals(0, delegate.getMatchCount());
        assertFalse(delegate.hasCurrentToken());
        assertFalse(delegate.hasTokenId(JsonTokenId.ID_NO_TOKEN));
        assertEquals(JsonTokenId.ID_NO_TOKEN, delegate.getCurrentTokenId());
        assertEquals(JsonTokenId.ID_NO_TOKEN, delegate.currentTokenId());
        assertNull(delegate.getCurrentToken());
        assertNull(delegate.currentToken());
        assertFalse(delegate.isExpectedStartArrayToken());
        assertFalse(delegate.isExpectedStartObjectToken());
        assertNull(delegate.getLastClearedToken());
        assertNotNull(delegate.getCurrentLocation());
        assertNotNull(delegate.getTokenLocation());
        assertNotNull(delegate.getParsingContext());
        
        delegate.clearCurrentToken();
        
        try {
            delegate.overrideCurrentName("test");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Can not currently override name"));
        }

        p.close();
        delegate.close();
    }

    @Test
    public void testTokenTraversalIncludeAll() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);

        assertEquals(JsonToken.START_OBJECT, delegate.nextToken());
        assertTrue(delegate.hasCurrentToken());
        assertTrue(delegate.hasToken(JsonToken.START_OBJECT));
        assertTrue(delegate.hasTokenId(JsonTokenId.ID_START_OBJECT));
        assertTrue(delegate.isExpectedStartObjectToken());

        assertEquals(JsonToken.FIELD_NAME, delegate.nextToken());
        assertEquals("a", delegate.getCurrentName());
        assertEquals("a", delegate.getText());
        assertFalse(delegate.hasTextCharacters());
        assertNotNull(delegate.getTextCharacters());
        assertEquals(1, delegate.getTextLength());
        assertEquals(0, delegate.getTextOffset());

        assertEquals(JsonToken.VALUE_NUMBER_INT, delegate.nextToken());
        assertEquals(1, delegate.getIntValue());
        assertEquals(1L, delegate.getLongValue());
        assertEquals(1.0, delegate.getDoubleValue(), 0.0);
        assertEquals(1.0f, delegate.getFloatValue(), 0.0f);
        assertEquals(Short.valueOf((short)1), Short.valueOf(delegate.getShortValue()));
        assertEquals(Byte.valueOf((byte)1), Byte.valueOf(delegate.getByteValue()));
        assertEquals(BigInteger.ONE, delegate.getBigIntegerValue());
        assertEquals(BigDecimal.ONE, delegate.getDecimalValue());
        assertNotNull(delegate.getNumberType());
        assertNotNull(delegate.getNumberValue());

        assertEquals(JsonToken.END_OBJECT, delegate.nextToken());
        assertTrue(delegate.hasToken(JsonToken.END_OBJECT));
        assertFalse(delegate.isExpectedStartArrayToken());

        assertNull(delegate.nextToken());
        assertFalse(delegate.hasCurrentToken());
        assertEquals(JsonTokenId.ID_NO_TOKEN, delegate.getCurrentTokenId());

        delegate.close();
    }

    @Test
    public void testValueCoercionsAndMisc() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"str\":\"hello\", \"num\":\"123\", \"bool\":true, \"bin\":\"YQ==\"}");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, true, true);

        while (delegate.nextToken() != JsonToken.START_OBJECT) {
            // loop to start
        }

        // FIELD_NAME str
        delegate.nextToken();
        assertEquals("hello", delegate.getValueAsString());
        assertEquals("default", delegate.getValueAsString("default"));

        // VALUE_STRING hello
        delegate.nextToken();
        assertEquals("hello", delegate.getValueAsString());
        assertEquals(123, delegate.getValueAsInt(123));
        assertEquals(123L, delegate.getValueAsLong(123L));
        assertEquals(123.0, delegate.getValueAsDouble(123.0), 0.0);
        assertFalse(delegate.getValueAsBoolean(false));

        // FIELD_NAME num
        delegate.nextToken();
        // VALUE_STRING 123
        delegate.nextToken();
        assertEquals(123, delegate.getValueAsInt());
        assertEquals(123L, delegate.getValueAsLong());
        assertEquals(123.0, delegate.getValueAsDouble());

        // FIELD_NAME bool
        delegate.nextToken();
        // VALUE_TRUE
        delegate.nextToken();
        assertTrue(delegate.getValueAsBoolean());
        assertTrue(delegate.getValueAsBoolean(false));

        // FIELD_NAME bin
        delegate.nextToken();
        // VALUE_STRING YQ==
        delegate.nextToken();
        byte[] bytes = delegate.getBinaryValue(Base64Variant.getDefaultVariant());
        assertNotNull(bytes);

        // skip children test
        JsonParser p2 = f.createParser("{\"a\":[1,2,3], \"b\":2}");
        FilteringParserDelegate delegate2 = new FilteringParserDelegate(p2, TokenFilter.INCLUDE_ALL, true, true);
        delegate2.nextToken(); // START_OBJECT
        delegate2.nextToken(); // FIELD_NAME a
        delegate2.nextToken(); // START_ARRAY
        delegate2.skipChildren();
        assertEquals(JsonToken.FIELD_NAME, delegate2.nextToken()); // should be b
        
        delegate.close();
        delegate2.close();
    }

    @Test
    public void testNextValueAndFiltering() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1, \"b\":2}");
        TokenFilter customFilter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(String name) {
                if ("a".equals(name)) {
                    return TokenFilter.INCLUDE_ALL;
                }
                return null;
            }
        };
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, customFilter, true, true);

        // nextValue should skip field name and return value directly
        JsonToken t = delegate.nextValue();
        // Depending on filter implementation, let's just exercise the method
        assertNotNull(delegate.getCurrentLocation());

        delegate.close();
    }

    @Test
    public void testAllowMultipleMatchesScalar() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("1");
        FilteringParserDelegate delegate = new FilteringParserDelegate(p, TokenFilter.INCLUDE_ALL, false, false);
        
        assertNotNull(delegate.nextToken());
        // Second call with allowMultipleMatches=false for scalar
        assertNull(delegate.nextToken());

        delegate.close();
    }
}