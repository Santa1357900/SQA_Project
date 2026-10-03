package com.fasterxml.jackson.databind.deser.std;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;

public class NumberDeserializersClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // find(): rawType.isPrimitive() -> Integer.TYPE branch
    @Test
    public void testFind_primitiveInt_returnsPrimitiveIntegerInstance() throws Throwable {
        Object d = NumberDeserializers.find(Integer.TYPE, Integer.TYPE.getName());
        assertSame(NumberDeserializers.IntegerDeserializer.primitiveInstance, d);
    }

    // find(): Boolean.TYPE branch
    @Test
    public void testFind_primitiveBoolean_returnsPrimitiveBooleanInstance() throws Throwable {
        Object d = NumberDeserializers.find(Boolean.TYPE, Boolean.TYPE.getName());
        assertSame(NumberDeserializers.BooleanDeserializer.primitiveInstance, d);
    }

    // find(): Long.TYPE branch
    @Test
    public void testFind_primitiveLong_returnsPrimitiveLongInstance() throws Throwable {
        Object d = NumberDeserializers.find(Long.TYPE, Long.TYPE.getName());
        assertSame(NumberDeserializers.LongDeserializer.primitiveInstance, d);
    }

    // find(): Double.TYPE branch
    @Test
    public void testFind_primitiveDouble_returnsPrimitiveDoubleInstance() throws Throwable {
        Object d = NumberDeserializers.find(Double.TYPE, Double.TYPE.getName());
        assertSame(NumberDeserializers.DoubleDeserializer.primitiveInstance, d);
    }

    // find(): Character.TYPE branch
    @Test
    public void testFind_primitiveChar_returnsPrimitiveCharacterInstance() throws Throwable {
        Object d = NumberDeserializers.find(Character.TYPE, Character.TYPE.getName());
        assertSame(NumberDeserializers.CharacterDeserializer.primitiveInstance, d);
    }

    // find(): Byte.TYPE branch
    @Test
    public void testFind_primitiveByte_returnsPrimitiveByteInstance() throws Throwable {
        Object d = NumberDeserializers.find(Byte.TYPE, Byte.TYPE.getName());
        assertSame(NumberDeserializers.ByteDeserializer.primitiveInstance, d);
    }

    // find(): Short.TYPE branch
    @Test
    public void testFind_primitiveShort_returnsPrimitiveShortInstance() throws Throwable {
        Object d = NumberDeserializers.find(Short.TYPE, Short.TYPE.getName());
        assertSame(NumberDeserializers.ShortDeserializer.primitiveInstance, d);
    }

    // find(): Float.TYPE branch
    @Test
    public void testFind_primitiveFloat_returnsPrimitiveFloatInstance() throws Throwable {
        Object d = NumberDeserializers.find(Float.TYPE, Float.TYPE.getName());
        assertSame(NumberDeserializers.FloatDeserializer.primitiveInstance, d);
    }

    // find(): _classNames branch, Integer.class
    @Test
    public void testFind_wrapperInteger_returnsWrapperIntegerInstance() throws Throwable {
        Object d = NumberDeserializers.find(Integer.class, Integer.class.getName());
        assertSame(NumberDeserializers.IntegerDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Boolean.class
    @Test
    public void testFind_wrapperBoolean_returnsWrapperBooleanInstance() throws Throwable {
        Object d = NumberDeserializers.find(Boolean.class, Boolean.class.getName());
        assertSame(NumberDeserializers.BooleanDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Long.class
    @Test
    public void testFind_wrapperLong_returnsWrapperLongInstance() throws Throwable {
        Object d = NumberDeserializers.find(Long.class, Long.class.getName());
        assertSame(NumberDeserializers.LongDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Double.class
    @Test
    public void testFind_wrapperDouble_returnsWrapperDoubleInstance() throws Throwable {
        Object d = NumberDeserializers.find(Double.class, Double.class.getName());
        assertSame(NumberDeserializers.DoubleDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Character.class
    @Test
    public void testFind_wrapperCharacter_returnsWrapperCharacterInstance() throws Throwable {
        Object d = NumberDeserializers.find(Character.class, Character.class.getName());
        assertSame(NumberDeserializers.CharacterDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Byte.class
    @Test
    public void testFind_wrapperByte_returnsWrapperByteInstance() throws Throwable {
        Object d = NumberDeserializers.find(Byte.class, Byte.class.getName());
        assertSame(NumberDeserializers.ByteDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Short.class
    @Test
    public void testFind_wrapperShort_returnsWrapperShortInstance() throws Throwable {
        Object d = NumberDeserializers.find(Short.class, Short.class.getName());
        assertSame(NumberDeserializers.ShortDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Float.class
    @Test
    public void testFind_wrapperFloat_returnsWrapperFloatInstance() throws Throwable {
        Object d = NumberDeserializers.find(Float.class, Float.class.getName());
        assertSame(NumberDeserializers.FloatDeserializer.wrapperInstance, d);
    }

    // find(): _classNames branch, Number.class
    @Test
    public void testFind_number_returnsNumberDeserializerInstance() throws Throwable {
        Object d = NumberDeserializers.find(Number.class, Number.class.getName());
        assertSame(NumberDeserializers.NumberDeserializer.instance, d);
    }

    // find(): _classNames branch, BigDecimal.class
    @Test
    public void testFind_bigDecimal_returnsBigDecimalDeserializerInstance() throws Throwable {
        Object d = NumberDeserializers.find(BigDecimal.class, BigDecimal.class.getName());
        assertSame(NumberDeserializers.BigDecimalDeserializer.instance, d);
    }

    // find(): _classNames branch, BigInteger.class
    @Test
    public void testFind_bigInteger_returnsBigIntegerDeserializerInstance() throws Throwable {
        Object d = NumberDeserializers.find(BigInteger.class, BigInteger.class.getName());
        assertSame(NumberDeserializers.BigIntegerDeserializer.instance, d);
    }

    // find(): else branch, className not in _classNames -> null
    @Test
    public void testFind_unknownClassName_returnsNull() throws Throwable {
        Object d = NumberDeserializers.find(String.class, String.class.getName());
        assertNull(d);
    }

    // find(): _classNames contains clsName but rawType doesn't match any case -> IllegalArgumentException
    @Test
    public void testFind_mismatchedRawTypeAndClassName_throwsIllegalArgumentException() throws Throwable {
        try {
            NumberDeserializers.find(String.class, Integer.class.getName());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // BooleanDeserializer.deserialize: true token -> Boolean.TRUE
    @Test
    public void testBooleanDeserialize_trueToken_returnsTrue() throws Throwable {
        Boolean result = mapper.readValue("true", Boolean.class);
        assertEquals(Boolean.TRUE, result);
    }

    // BooleanDeserializer wrapper: null token -> getNullValue returns null (nvl == null)
    @Test
    public void testBooleanWrapper_nullToken_returnsNull() throws Throwable {
        Boolean result = mapper.readValue("null", Boolean.class);
        assertNull(result);
    }

    // IntegerDeserializer.deserialize: VALUE_NUMBER_INT fast path
    @Test
    public void testIntegerDeserialize_numberIntToken_returnsIntValue() throws Throwable {
        Integer result = mapper.readValue("42", Integer.class);
        assertEquals(Integer.valueOf(42), result);
    }

    // LongDeserializer.deserialize: VALUE_NUMBER_INT fast path, large value
    @Test
    public void testLongDeserialize_largeValue_returnsLongValue() throws Throwable {
        Long result = mapper.readValue("123456789012345", Long.class);
        assertEquals(Long.valueOf(123456789012345L), result);
    }

    // DoubleDeserializer.deserialize: decimal number
    @Test
    public void testDoubleDeserialize_decimalNumber_returnsDoubleValue() throws Throwable {
        Double result = mapper.readValue("3.14", Double.class);
        assertEquals(3.14, result.doubleValue(), 1e-9);
    }

    // FloatDeserializer.deserialize: decimal number
    @Test
    public void testFloatDeserialize_decimalNumber_returnsFloatValue() throws Throwable {
        Float result = mapper.readValue("2.5", Float.class);
        assertEquals(2.5f, result.floatValue(), 1e-6f);
    }

    // ByteDeserializer.deserialize: number token
    @Test
    public void testByteDeserialize_numberToken_returnsByteValue() throws Throwable {
        Byte result = mapper.readValue("5", Byte.class);
        assertEquals((byte) 5, result.byteValue());
    }

    // ShortDeserializer.deserialize: number token
    @Test
    public void testShortDeserialize_numberToken_returnsShortValue() throws Throwable {
        Short result = mapper.readValue("300", Short.class);
        assertEquals((short) 300, result.shortValue());
    }

    // CharacterDeserializer.deserialize: ID_STRING length==1 branch
    @Test
    public void testCharacterDeserialize_singleCharString_returnsChar() throws Throwable {
        Character result = mapper.readValue("\"A\"", Character.class);
        assertEquals(Character.valueOf('A'), result);
    }

    // CharacterDeserializer.deserialize: ID_STRING length==0 branch -> getEmptyValue -> null
    @Test
    public void testCharacterDeserialize_emptyString_returnsNull() throws Throwable {
        Character result = mapper.readValue("\"\"", Character.class);
        assertNull(result);
    }

    // CharacterDeserializer.deserialize: ID_STRING length>1 -> falls through to mapping exception
    @Test
    public void testCharacterDeserialize_multiCharString_throwsException() throws Throwable {
        try {
            mapper.readValue("\"AB\"", Character.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // CharacterDeserializer.deserialize: ID_NUMBER_INT boundary value == 0xFFFF
    @Test
    public void testCharacterDeserialize_intBoundaryMax_returnsChar() throws Throwable {
        Character result = mapper.readValue("65535", Character.class);
        assertEquals((char) 65535, result.charValue());
    }

    // CharacterDeserializer.deserialize: ID_NUMBER_INT value above 0xFFFF -> exception
    @Test
    public void testCharacterDeserialize_intAboveMax_throwsException() throws Throwable {
        try {
            mapper.readValue("65536", Character.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // CharacterDeserializer.deserialize: ID_NUMBER_INT negative value -> exception
    @Test
    public void testCharacterDeserialize_intNegative_throwsException() throws Throwable {
        try {
            mapper.readValue("-1", Character.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // CharacterDeserializer.deserialize: array unwrap enabled, single element
    @Test
    public void testCharacterDeserialize_arrayUnwrapEnabled_singleElement_returnsChar() throws Throwable {
        mapper.enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        Character result = mapper.readValue("[\"Z\"]", Character.class);
        assertEquals(Character.valueOf('Z'), result);
    }

    // CharacterDeserializer.deserialize: array unwrap enabled, multiple elements -> exception
    @Test
    public void testCharacterDeserialize_arrayUnwrapEnabled_multipleElements_throwsException() throws Throwable {
        mapper.enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        try {
            mapper.readValue("[\"A\",\"B\"]", Character.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // NumberDeserializer.deserialize: ID_NUMBER_INT branch -> plain int value
    @Test
    public void testNumberDeserializer_intToken_returnsInteger() throws Throwable {
        Number result = mapper.readValue("42", Number.class);
        assertTrue(result instanceof Integer);
        assertEquals(Integer.valueOf(42), result);
    }

    // NumberDeserializer.deserialize: ID_NUMBER_FLOAT branch, default -> Double
    @Test
    public void testNumberDeserializer_floatToken_returnsDouble() throws Throwable {
        Number result = mapper.readValue("3.14", Number.class);
        assertTrue(result instanceof Double);
        assertEquals(3.14, result.doubleValue(), 1e-9);
    }

    // NumberDeserializer.deserialize: USE_BIG_DECIMAL_FOR_FLOATS enabled -> BigDecimal
    @Test
    public void testNumberDeserializer_floatTokenUseBigDecimal_returnsBigDecimal() throws Throwable {
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        Number result = mapper.readValue("3.14", Number.class);
        assertTrue(result instanceof BigDecimal);
    }

    // NumberDeserializer.deserialize: ID_STRING empty -> getEmptyValue -> null
    @Test
    public void testNumberDeserializer_emptyString_returnsNull() throws Throwable {
        Number result = mapper.readValue("\"\"", Number.class);
        assertNull(result);
    }

    // NumberDeserializer.deserialize: ID_STRING textual "null" -> getNullValue -> null
    @Test
    public void testNumberDeserializer_textualNull_returnsNull() throws Throwable {
        Number result = mapper.readValue("\"null\"", Number.class);
        assertNull(result);
    }

    // NumberDeserializer.deserialize: ID_STRING "Infinity" -> Double.POSITIVE_INFINITY
    @Test
    public void testNumberDeserializer_posInfString_returnsPositiveInfinity() throws Throwable {
        Number result = mapper.readValue("\"Infinity\"", Number.class);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), result.doubleValue(), 0.0);
    }

    // NumberDeserializer.deserialize: ID_STRING "-Infinity" -> Double.NEGATIVE_INFINITY
    @Test
    public void testNumberDeserializer_negInfString_returnsNegativeInfinity() throws Throwable {
        Number result = mapper.readValue("\"-Infinity\"", Number.class);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), result.doubleValue(), 0.0);
    }

    // NumberDeserializer.deserialize: ID_STRING "NaN" -> Double.NaN
    @Test
    public void testNumberDeserializer_nanString_returnsNaN() throws Throwable {
        Number result = mapper.readValue("\"NaN\"", Number.class);
        assertTrue(Double.isNaN(result.doubleValue()));
    }

    // NumberDeserializer.deserialize: ID_STRING int within Integer range -> Integer
    @Test
    public void testNumberDeserializer_intStringWithinIntRange_returnsInteger() throws Throwable {
        Number result = mapper.readValue("\"123\"", Number.class);
        assertTrue(result instanceof Integer);
        assertEquals(Integer.valueOf(123), result);
    }

    // NumberDeserializer.deserialize: ID_STRING int exceeding Integer range -> Long
    @Test
    public void testNumberDeserializer_intStringExceedsIntRange_returnsLong() throws Throwable {
        Number result = mapper.readValue("\"99999999999\"", Number.class);
        assertTrue(result instanceof Long);
        assertEquals(Long.valueOf(99999999999L), result);
    }

    // NumberDeserializer.deserialize: USE_LONG_FOR_INTS enabled -> always Long
    @Test
    public void testNumberDeserializer_useLongForInts_returnsLong() throws Throwable {
        mapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        Number result = mapper.readValue("\"5\"", Number.class);
        assertTrue(result instanceof Long);
        assertEquals(Long.valueOf(5L), result);
    }

    // NumberDeserializer.deserialize: USE_BIG_INTEGER_FOR_INTS enabled -> BigInteger
    @Test
    public void testNumberDeserializer_useBigIntegerForInts_returnsBigInteger() throws Throwable {
        mapper.enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        Number result = mapper.readValue("\"5\"", Number.class);
        assertTrue(result instanceof BigInteger);
        assertEquals(BigInteger.valueOf(5L), result);
    }

    // NumberDeserializer.deserialize: invalid number string -> weirdStringException
    @Test
    public void testNumberDeserializer_invalidNumberString_throwsException() throws Throwable {
        try {
            mapper.readValue("\"notANumber\"", Number.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // NumberDeserializer.deserialize: ID_START_ARRAY, unwrap disabled -> mapping exception
    @Test
    public void testNumberDeserializer_arrayUnwrapDisabled_throwsException() throws Throwable {
        try {
            mapper.readValue("[123]", Number.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // NumberDeserializer.deserialize: ID_START_ARRAY, unwrap enabled, single element
    @Test
    public void testNumberDeserializer_arrayUnwrapEnabled_singleElement_returnsValue() throws Throwable {
        mapper.enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        Number result = mapper.readValue("[123]", Number.class);
        assertEquals(Integer.valueOf(123), result);
    }

    // NumberDeserializer.deserialize: ID_START_ARRAY, unwrap enabled, multiple elements -> exception
    @Test
    public void testNumberDeserializer_arrayUnwrapEnabled_multipleElements_throwsException() throws Throwable {
        mapper.enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        try {
            mapper.readValue("[1,2]", Number.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // BigIntegerDeserializer.deserialize: ID_NUMBER_INT branch
    @Test
    public void testBigIntegerDeserializer_intToken_returnsBigInteger() throws Throwable {
        BigInteger result = mapper.readValue("12345", BigInteger.class);
        assertEquals(BigInteger.valueOf(12345L), result);
    }

    // BigIntegerDeserializer.deserialize: ID_NUMBER_FLOAT, ACCEPT_FLOAT_AS_INT true (default) -> truncated
    @Test
    public void testBigIntegerDeserializer_floatTokenAcceptTrue_returnsTruncatedBigInteger() throws Throwable {
        BigInteger result = mapper.readValue("1.9", BigInteger.class);
        assertEquals(BigInteger.valueOf(1L), result);
    }

    // BigIntegerDeserializer.deserialize: ID_NUMBER_FLOAT, ACCEPT_FLOAT_AS_INT disabled -> exception
    @Test
    public void testBigIntegerDeserializer_floatTokenAcceptFalse_throwsException() throws Throwable {
        mapper.configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false);
        try {
            mapper.readValue("1.5", BigInteger.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // BigIntegerDeserializer.deserialize: ID_STRING branch
    @Test
    public void testBigIntegerDeserializer_stringToken_returnsBigInteger() throws Throwable {
        BigInteger result = mapper.readValue("\"12345\"", BigInteger.class);
        assertEquals(BigInteger.valueOf(12345L), result);
    }

    // BigIntegerDeserializer.deserialize: ID_STRING empty -> null
    @Test
    public void testBigIntegerDeserializer_emptyString_returnsNull() throws Throwable {
        BigInteger result = mapper.readValue("\"\"", BigInteger.class);
        assertNull(result);
    }

    // BigIntegerDeserializer.deserialize: ID_STRING invalid -> weirdStringException
    @Test
    public void testBigIntegerDeserializer_invalidString_throwsException() throws Throwable {
        try {
            mapper.readValue("\"notANumber\"", BigInteger.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // BigDecimalDeserializer.deserialize: ID_NUMBER_INT / ID_NUMBER_FLOAT branch
    @Test
    public void testBigDecimalDeserializer_floatToken_returnsBigDecimal() throws Throwable {
        BigDecimal result = mapper.readValue("1.25", BigDecimal.class);
        assertEquals(0, new BigDecimal("1.25").compareTo(result));
    }

    // BigDecimalDeserializer.deserialize: ID_STRING branch
    @Test
    public void testBigDecimalDeserializer_stringToken_returnsBigDecimal() throws Throwable {
        BigDecimal result = mapper.readValue("\"1.25\"", BigDecimal.class);
        assertEquals(0, new BigDecimal("1.25").compareTo(result));
    }

    // BigDecimalDeserializer.deserialize: ID_STRING empty -> null
    @Test
    public void testBigDecimalDeserializer_emptyString_returnsNull() throws Throwable {
        BigDecimal result = mapper.readValue("\"\"", BigDecimal.class);
        assertNull(result);
    }

    // BigDecimalDeserializer.deserialize: ID_STRING invalid -> weirdStringException
    @Test
    public void testBigDecimalDeserializer_invalidString_throwsException() throws Throwable {
        try {
            mapper.readValue("\"notANumber\"", BigDecimal.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }
}
