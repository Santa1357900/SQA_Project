package com.fasterxml.jackson.core.base;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser.NumberType;

public class ParserBaseClaudeTest {

    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable {
        factory = new JsonFactory();
    }

    private JsonParser createParser(String json) throws IOException {
        return factory.createParser(json);
    }

    private JsonParser createNonNumericParser(String json) throws IOException {
        JsonFactory f = new JsonFactory();
        f.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
        return f.createParser(json);
    }

    // version() should never return null
    @Test
    public void testVersion_notNull() throws Throwable {
        JsonParser p = createParser("1");
        assertNotNull(p.version());
    }

    // isClosed() false right after creation
    @Test
    public void testIsClosed_initiallyFalse() throws Throwable {
        JsonParser p = createParser("1");
        assertFalse(p.isClosed());
    }

    // close() sets closed flag true and is idempotent (no exception on second call)
    @Test
    public void testClose_setsClosedTrueAndIdempotent() throws Throwable {
        JsonParser p = createParser("1");
        p.close();
        assertTrue(p.isClosed());
        p.close();
        assertTrue(p.isClosed());
    }

    // getParsingContext() starts in root context
    @Test
    public void testGetParsingContext_initiallyInRoot() throws Throwable {
        JsonParser p = createParser("1");
        assertTrue(p.getParsingContext().inRoot());
    }

    // getTokenLineNr() initial value is 1
    @Test
    public void testGetTokenLineNr_initial() throws Throwable {
        JsonParser p = createParser("1");
        assertEquals(1, p.getTokenLineNr());
    }

    // getTokenColumnNr(): 0-based col=0 converted to 1-based -> 1 initially
    @Test
    public void testGetTokenColumnNr_initial() throws Throwable {
        JsonParser p = createParser("1");
        assertEquals(1, p.getTokenColumnNr());
    }

    // getTokenCharacterOffset() initial value is 0
    @Test
    public void testGetTokenCharacterOffset_initial() throws Throwable {
        JsonParser p = createParser("1");
        assertEquals(0L, p.getTokenCharacterOffset());
    }

    // getCurrentLocation() returns a non-null JsonLocation
    @Test
    public void testGetCurrentLocation_notNull() throws Throwable {
        JsonParser p = createParser("1");
        JsonLocation loc = p.getCurrentLocation();
        assertNotNull(loc);
    }

    // getTokenLocation() returns a non-null JsonLocation
    @Test
    public void testGetTokenLocation_notNull() throws Throwable {
        JsonParser p = createParser("1");
        JsonLocation loc = p.getTokenLocation();
        assertNotNull(loc);
    }

    // getCurrentName() at root-level START_OBJECT: root has no name, should be null
    @Test
    public void testGetCurrentName_atRootStartObject_returnsNull() throws Throwable {
        JsonParser p = createParser("{}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertNull(p.getCurrentName());
    }

    // getCurrentName() at FIELD_NAME returns the field's name
    @Test
    public void testGetCurrentName_atFieldName_returnsFieldName() throws Throwable {
        JsonParser p = createParser("{\"foo\":1}");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("foo", p.getCurrentName());
    }

    // overrideCurrentName() changes the name visible via getCurrentName()
    @Test
    public void testOverrideCurrentName_changesFieldName() throws Throwable {
        JsonParser p = createParser("{\"foo\":1}");
        p.nextToken();
        p.nextToken();
        p.overrideCurrentName("bar");
        assertEquals("bar", p.getCurrentName());
    }

    // hasTextCharacters() true for VALUE_STRING token
    @Test
    public void testHasTextCharacters_stringToken_true() throws Throwable {
        JsonParser p = createParser("\"hello\"");
        p.nextToken();
        assertTrue(p.hasTextCharacters());
    }

    // hasTextCharacters() false for VALUE_NUMBER_INT token
    @Test
    public void testHasTextCharacters_intToken_false() throws Throwable {
        JsonParser p = createParser("123");
        p.nextToken();
        assertFalse(p.hasTextCharacters());
    }

    // hasTextCharacters() false for FIELD_NAME when name copy buffer not used
    @Test
    public void testHasTextCharacters_fieldNameTokenDefault_false() throws Throwable {
        JsonParser p = createParser("{\"foo\":1}");
        p.nextToken();
        p.nextToken();
        assertFalse(p.hasTextCharacters());
    }

    // getBinaryValue() on a non-VALUE_STRING token throws JsonParseException
    @Test
    public void testGetBinaryValue_wrongToken_throws() throws Throwable {
        JsonParser p = createParser("123");
        p.nextToken();
        try {
            p.getBinaryValue(Base64Variants.getDefaultVariant());
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
        }
    }

    // getBinaryValue() correctly decodes base64 string content
    @Test
    public void testGetBinaryValue_validBase64_decodesCorrectly() throws Throwable {
        JsonParser p = createParser("\"TWFu\"");
        p.nextToken();
        byte[] result = p.getBinaryValue(Base64Variants.getDefaultVariant());
        assertArrayEquals(new byte[] { 77, 97, 110 }, result);
    }

    // getIntValue() simple positive small number
    @Test
    public void testGetIntValue_simplePositive() throws Throwable {
        JsonParser p = createParser("123");
        p.nextToken();
        assertEquals(123, p.getIntValue());
    }

    // getIntValue() exactly at Integer.MAX_VALUE boundary
    @Test
    public void testGetIntValue_maxIntBoundary() throws Throwable {
        JsonParser p = createParser("2147483647");
        p.nextToken();
        assertEquals(Integer.MAX_VALUE, p.getIntValue());
    }

    // getIntValue() one beyond Integer.MAX_VALUE must throw (overflow)
    @Test
    public void testGetIntValue_overflowBeyondMaxInt_throws() throws Throwable {
        JsonParser p = createParser("2147483648");
        p.nextToken();
        try {
            p.getIntValue();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
        }
    }

    // getIntValue() exactly at Integer.MIN_VALUE boundary
    @Test
    public void testGetIntValue_minIntBoundary() throws Throwable {
        JsonParser p = createParser("-2147483648");
        p.nextToken();
        assertEquals(Integer.MIN_VALUE, p.getIntValue());
    }

    // getLongValue() simple positive number that fits in long but not int
    @Test
    public void testGetLongValue_simple() throws Throwable {
        JsonParser p = createParser("99999999999");
        p.nextToken();
        assertEquals(99999999999L, p.getLongValue());
    }

    // getLongValue() exactly at Long.MAX_VALUE boundary
    @Test
    public void testGetLongValue_maxLongBoundary() throws Throwable {
        JsonParser p = createParser("9223372036854775807");
        p.nextToken();
        assertEquals(Long.MAX_VALUE, p.getLongValue());
    }

    // getLongValue() one beyond Long.MAX_VALUE must throw (overflow)
    @Test
    public void testGetLongValue_overflowBeyondMaxLong_throws() throws Throwable {
        JsonParser p = createParser("9223372036854775808");
        p.nextToken();
        try {
            p.getLongValue();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
        }
    }

    // getBigIntegerValue() for number too large for long
    @Test
    public void testGetBigIntegerValue_largeNumber() throws Throwable {
        JsonParser p = createParser("99999999999999999999");
        p.nextToken();
        assertEquals(new BigInteger("99999999999999999999"), p.getBigIntegerValue());
    }

    // getDoubleValue() basic floating point parsing
    @Test
    public void testGetDoubleValue_simple() throws Throwable {
        JsonParser p = createParser("3.14");
        p.nextToken();
        assertEquals(3.14, p.getDoubleValue(), 1e-9);
    }

    // getFloatValue() basic floating point parsing
    @Test
    public void testGetFloatValue_simple() throws Throwable {
        JsonParser p = createParser("2.5");
        p.nextToken();
        assertEquals(2.5f, p.getFloatValue(), 1e-6f);
    }

    // getDecimalValue() preserves exact decimal representation
    @Test
    public void testGetDecimalValue_simple() throws Throwable {
        JsonParser p = createParser("123.456");
        p.nextToken();
        BigDecimal value = p.getDecimalValue();
        assertEquals(0, value.compareTo(new BigDecimal("123.456")));
    }

    // getNumberType() returns INT for small integer
    @Test
    public void testGetNumberType_int() throws Throwable {
        JsonParser p = createParser("5");
        p.nextToken();
        assertEquals(NumberType.INT, p.getNumberType());
    }

    // getNumberType() returns LONG when value exceeds int range but fits long
    @Test
    public void testGetNumberType_long() throws Throwable {
        JsonParser p = createParser("99999999999");
        p.nextToken();
        assertEquals(NumberType.LONG, p.getNumberType());
    }

    // getNumberType() returns DOUBLE for plain floating point literal
    @Test
    public void testGetNumberType_double() throws Throwable {
        JsonParser p = createParser("3.14");
        p.nextToken();
        assertEquals(NumberType.DOUBLE, p.getNumberType());
    }

    // getNumberValue() for small int returns an Integer instance with correct value
    @Test
    public void testGetNumberValue_intToken_returnsInteger() throws Throwable {
        JsonParser p = createParser("5");
        p.nextToken();
        Number n = p.getNumberValue();
        assertTrue(n instanceof Integer);
        assertEquals(5, n.intValue());
    }

    // isNaN() false for regular finite floating point value
    @Test
    public void testIsNaN_regularFloat_false() throws Throwable {
        JsonParser p = createParser("3.14");
        p.nextToken();
        assertFalse(p.isNaN());
    }

    // isNaN() true for literal NaN when non-numeric numbers are allowed
    @Test
    public void testIsNaN_NaNLiteral_true() throws Throwable {
        JsonParser p = createNonNumericParser("NaN");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertTrue(p.isNaN());
    }

    // enable/disable STRICT_DUPLICATE_DETECTION toggles the dup detector on context
    @Test
    public void testEnableDisableStrictDuplicateDetection() throws Throwable {
        JsonParser p = createParser("{}");
        assertNull(p.getParsingContext().getDupDetector());
        p.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        assertNotNull(p.getParsingContext().getDupDetector());
        p.disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        assertNull(p.getParsingContext().getDupDetector());
    }

    // overrideStdFeatures() toggles dup detector based on values/mask
    @Test
    public void testOverrideStdFeatures_togglesDupDetector() throws Throwable {
        JsonParser p = createParser("{}");
        int mask = JsonParser.Feature.STRICT_DUPLICATE_DETECTION.getMask();
        p.overrideStdFeatures(mask, mask);
        assertNotNull(p.getParsingContext().getDupDetector());
        p.overrideStdFeatures(0, mask);
        assertNull(p.getParsingContext().getDupDetector());
    }

    // setFeatureMask() (deprecated) still triggers dup detector update
    @Test
    public void testSetFeatureMask_enablesDupDetector() throws Throwable {
        JsonParser p = createParser("{}");
        int mask = JsonParser.Feature.STRICT_DUPLICATE_DETECTION.getMask();
        p.setFeatureMask(mask);
        assertNotNull(p.getParsingContext().getDupDetector());
    }

    // growArrayBy() with null array returns fresh array of requested size
    @Test
    public void testGrowArrayBy_nullArray_returnsNewArray() throws Throwable {
        int[] result = ParserBase.growArrayBy(null, 5);
        assertEquals(5, result.length);
    }

    // growArrayBy() with existing array copies contents and grows by given amount
    @Test
    public void testGrowArrayBy_existingArray_growsAndCopiesData() throws Throwable {
        int[] arr = new int[] { 1, 2, 3 };
        int[] result = ParserBase.growArrayBy(arr, 2);
        assertEquals(5, result.length);
        assertEquals(1, result[0]);
        assertEquals(3, result[2]);
        assertEquals(0, result[4]);
    }

    // unclosed array reaching EOF must report error mentioning "Array"
    @Test
    public void testHandleEOF_unclosedArray_throwsWithArrayMarker() throws Throwable {
        JsonParser p = createParser("[1,2");
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        try {
            p.nextToken();
            fail("expected JsonParseException due to unclosed array");
        } catch (JsonParseException expected) {
            assertTrue(expected.getMessage().contains("Array"));
        }
    }

    // clean EOF at root level (no open context) should not throw, returns null
    @Test
    public void testNextToken_cleanRootEOF_returnsNull() throws Throwable {
        JsonParser p = createParser("123");
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertNull(p.nextToken());
    }
}
