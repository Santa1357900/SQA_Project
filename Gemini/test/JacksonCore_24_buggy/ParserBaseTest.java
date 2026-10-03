package com.fasterxml.jackson.core.base;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

public class ParserBaseTest {

    private static class ConcreteParser extends ParserBase {
        public ConcreteParser(IOContext ctxt, int features) {
            super(ctxt, features);
        }

        @Override
        protected void _closeInput() throws IOException {
            // do nothing
        }

        @Override
        public ObjectCodec getCodec() {
            return null;
        }

        @Override
        public void setCodec(ObjectCodec c) {
        }
    }

    private IOContext createContext() {
        return new IOContext(new BufferRecycler(), new Object(), false);
    }

    @Test
    public void testLifeCycleAndVersion() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        Version v = parser.version();
        assertNotNull(v);
        
        assertFalse(parser.isClosed());
        parser.close();
        assertTrue(parser.isClosed());
        
        // Closing again should be safe
        parser.close();
        assertTrue(parser.isClosed());
    }

    @Test
    public void testCurrentValue() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        assertNull(parser.getCurrentValue());
        Object val = new Object();
        parser.setCurrentValue(val);
        assertEquals(val, parser.getCurrentValue());
    }

    @Test
    public void testFeatures() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        assertNotNull(parser.getParsingContext().getDupDetector());
        
        parser.disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        assertNull(parser.getParsingContext().getDupDetector());
        
        parser.setFeatureMask(JsonParser.Feature.STRICT_DUPLICATE_DETECTION.getMask());
        assertNotNull(parser.getParsingContext().getDupDetector());
        
        parser.overrideStdFeatures(0, JsonParser.Feature.STRICT_DUPLICATE_DETECTION.getMask());
        assertNull(parser.getParsingContext().getDupDetector());
    }

    @Test
    public void testCurrentNameAndOverrides() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        parser._currToken = JsonToken.START_OBJECT;
        assertNull(parser.getCurrentName());
        
        parser.overrideCurrentName("testName");
        assertEquals("testName", parser.getCurrentName());
    }

    @Test
    public void testLocations() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION.getMask());
        
        JsonLocation loc = parser.getCurrentLocation();
        assertNotNull(loc);
        
        JsonLocation tokLoc = parser.getTokenLocation();
        assertNotNull(tokLoc);
        
        assertEquals(0L, parser.getTokenCharacterOffset());
        assertEquals(1, parser.getTokenLineNr());
        assertEquals(-1, parser.getTokenColumnNr());
    }

    @Test
    public void testHasTextCharacters() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        parser._currToken = JsonToken.VALUE_STRING;
        assertTrue(parser.hasTextCharacters());
        
        parser._currToken = JsonToken.FIELD_NAME;
        parser._nameCopied = true;
        assertTrue(parser.hasTextCharacters());
        
        parser._nameCopied = false;
        assertFalse(parser.hasTextCharacters());
        
        parser._currToken = JsonToken.VALUE_NUMBER_INT;
        assertFalse(parser.hasTextCharacters());
    }

    @Test
    public void testGetBinaryValueNotString() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        parser._currToken = JsonToken.VALUE_NUMBER_INT;
        
        try {
            parser.getBinaryValue(Base64Variants.MIME);
            fail("Expected exception for non-string binary access");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("not VALUE_STRING"));
        }
    }

    @Test
    public void testEofAsNextChar() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        try {
            parser._eofAsNextChar();
            fail("Expected exception due to invalid EOF in non-root context");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("expected close marker"));
        }
    }

    @Test
    public void testByteArrayBuilder() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        ByteArrayBuilder builder1 = parser._getByteArrayBuilder();
        assertNotNull(builder1);
        
        ByteArrayBuilder builder2 = parser._getByteArrayBuilder();
        assertEquals(builder1, builder2);
    }

    @Test
    public void testResetMethodsAndNaN() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        JsonToken t1 = parser.reset(true, 5, 0, 0);
        assertEquals(JsonToken.VALUE_NUMBER_INT, t1);
        
        JsonToken t2 = parser.reset(false, 1, 2, 0);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t2);
        
        JsonToken t3 = parser.resetAsNaN("NaN", Double.NaN);
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t3);
        assertTrue(parser.isNaN());
        
        parser._numberDouble = Double.POSITIVE_INFINITY;
        assertTrue(parser.isNaN());
        
        parser._numberDouble = 10.0;
        assertFalse(parser.isNaN());
    }

    @Test
    public void testNumericParsingAndAccessors() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        parser._currToken = JsonToken.VALUE_NUMBER_INT;
        parser._intLength = 5;
        parser._numberNegative = false;
        parser._textBuffer.resetWith(new char[]{'1','2','3','4','5'}, 0, 5);
        
        assertEquals(12345, parser.getIntValue());
        assertEquals(12345L, parser.getLongValue());
        assertEquals(BigInteger.valueOf(12345), parser.getBigIntegerValue());
        assertEquals(12345.0, parser.getDoubleValue(), 0.001);
        assertEquals(12345f, parser.getFloatValue(), 0.001f);
        assertEquals(BigDecimal.valueOf(12345), parser.getDecimalValue());
        
        Number num = parser.getNumberValue();
        assertEquals(12345, num.intValue());
        
        assertEquals(JsonParser.NumberType.INT, parser.getNumberType());
    }

    @Test
    public void testSlowIntAndLongParsing() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        parser._currToken = JsonToken.VALUE_NUMBER_INT;
        parser._intLength = 12; // > 9, <= 18
        parser._numberNegative = false;
        parser._textBuffer.resetWith(new char[]{'1','2','3','4','5','6','7','8','9','0','1','2'}, 0, 12);
        
        assertEquals(123456789012L, parser.getLongValue());
        assertEquals(JsonParser.NumberType.LONG, parser.getNumberType());
    }

    @Test
    public void testSlowFloatParsing() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        parser._currToken = JsonToken.VALUE_NUMBER_FLOAT;
        parser._textBuffer.resetWith(new char[]{'1','2','3','.','4','5'}, 0, 6);
        
        assertEquals(123.45, parser.getDoubleValue(), 0.001);
        assertEquals(JsonParser.NumberType.DOUBLE, parser.getNumberType());
        
        parser._numTypesValid = 0; // reset
        assertEquals(BigDecimal.valueOf(123.45), parser.getDecimalValue());
        assertEquals(JsonParser.NumberType.BIG_DECIMAL, parser.getNumberType());
    }

    @Test
    public void testConversions() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        // Test convert number to long from int
        parser._numTypesValid = ParserBase.NR_INT;
        parser._numberInt = 100;
        assertEquals(100L, parser.getLongValue());
        
        // Test convert number to big int from long
        parser._numTypesValid = ParserBase.NR_LONG;
        parser._numberLong = 500L;
        assertEquals(BigInteger.valueOf(500L), parser.getBigIntegerValue());
        
        // Test convert number to double from big int
        parser._numTypesValid = ParserBase.NR_BIGINT;
        parser._numberBigInt = BigInteger.TEN;
        assertEquals(10.0, parser.getDoubleValue(), 0.001);
        
        // Test convert number to big decimal from double
        parser._numTypesValid = ParserBase.NR_DOUBLE;
        parser._numberDouble = 5.5;
        parser._textBuffer.resetWith(new char[]{'5','.','5'}, 0, 3);
        assertEquals(BigDecimal.valueOf(5.5), parser.getDecimalValue());
    }

    @Test
    public void testErrorHandlersAndBase64() throws Throwable {
        IOContext ctxt = createContext();
        ConcreteParser parser = new ConcreteParser(ctxt, 0);
        
        try {
            parser._reportMismatchedEndMarker('}', ']');
            fail("Expected exception");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unexpected close marker"));
        }
        
        try {
            parser._decodeBase64Escape(Base64Variants.MIME, 'a', 0);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Illegal character"));
        }
        
        try {
            parser._decodeBase64Escape(Base64Variants.MIME, 0x01, 0);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Illegal white space character"));
        }
        
        int[] grown = ParserBase.growArrayBy(null, 5);
        assertEquals(5, grown.length);
        
        int[] grown2 = ParserBase.growArrayBy(new int[]{1, 2}, 2);
        assertEquals(4, grown2.length);
        assertEquals(2, grown2[1]);
    }
}