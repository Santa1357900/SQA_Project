package com.fasterxml.jackson.databind.deser.std;

import java.net.URI;
import java.net.URL;
import java.util.Currency;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StdKeyDeserializerClaudeTest {

    private ObjectMapper mapper;

    public enum TestColor { RED, GREEN, BLUE }

    public enum Suit {
        CLUB("c"), DIAMOND("d");
        private final String code;
        Suit(String c) { code = c; }
        public String toString() { return code; }
    }

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // forType: unsupported raw class falls through every branch -> null
    @Test
    public void testForType_unsupportedType_returnsNull() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(StringBuilder.class);
        assertNull(kd);
    }

    // forType(String.class): StringKD echoes key unchanged regardless of context
    @Test
    public void testForType_stringType_echoesKey() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(String.class);
        assertEquals(String.class, kd.getKeyClass());
        Object result = kd.deserializeKey("weird !@# key", null);
        assertEquals("weird !@# key", result);
    }

    // forType(Object.class): StringKD variant keyed to Object.class
    @Test
    public void testForType_objectType_getKeyClass() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(Object.class);
        assertEquals(Object.class, kd.getKeyClass());
    }

    // forType(Integer.class): getKeyClass reflects requested class
    @Test
    public void testForType_integerType_getKeyClass() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(Integer.class);
        assertEquals(Integer.class, kd.getKeyClass());
    }

    // TYPE_BOOLEAN: "true" literal branch
    @Test
    public void testDeserializeKey_booleanTrue_returnsTrue() throws Throwable {
        Map<Boolean, String> map = mapper.readValue("{\"true\":\"a\"}",
                new TypeReference<Map<Boolean, String>>() {});
        assertEquals("a", map.get(Boolean.TRUE));
    }

    // TYPE_BOOLEAN: "false" literal branch
    @Test
    public void testDeserializeKey_booleanFalse_returnsFalse() throws Throwable {
        Map<Boolean, String> map = mapper.readValue("{\"false\":\"a\"}",
                new TypeReference<Map<Boolean, String>>() {});
        assertEquals("a", map.get(Boolean.FALSE));
    }

    // TYPE_BOOLEAN: neither true nor false -> weird key exception
    @Test
    public void testDeserializeKey_booleanInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"maybe\":\"a\"}", new TypeReference<Map<Boolean, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_BYTE: normal in-range value
    @Test
    public void testDeserializeKey_byteNormal_returnsByteValue() throws Throwable {
        Map<Byte, String> map = mapper.readValue("{\"100\":\"a\"}",
                new TypeReference<Map<Byte, String>>() {});
        assertEquals("a", map.get(Byte.valueOf((byte) 100)));
    }

    // TYPE_BYTE: upper boundary 255 allowed for unsigned-byte support, wraps to -1
    @Test
    public void testDeserializeKey_byteUnsignedBoundary_returnsWrapped() throws Throwable {
        Map<Byte, String> map = mapper.readValue("{\"255\":\"a\"}",
                new TypeReference<Map<Byte, String>>() {});
        assertEquals("a", map.get(Byte.valueOf((byte) 255)));
    }

    // TYPE_BYTE: value above 255 overflows -> exception
    @Test
    public void testDeserializeKey_byteOverflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"256\":\"a\"}", new TypeReference<Map<Byte, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_BYTE: value below Byte.MIN_VALUE underflows -> exception
    @Test
    public void testDeserializeKey_byteUnderflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"-129\":\"a\"}", new TypeReference<Map<Byte, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_SHORT: normal in-range value
    @Test
    public void testDeserializeKey_shortNormal_returnsShortValue() throws Throwable {
        Map<Short, String> map = mapper.readValue("{\"12345\":\"a\"}",
                new TypeReference<Map<Short, String>>() {});
        assertEquals("a", map.get(Short.valueOf((short) 12345)));
    }

    // TYPE_SHORT: upper boundary Short.MAX_VALUE is allowed
    @Test
    public void testDeserializeKey_shortMaxBoundary_returnsMaxValue() throws Throwable {
        Map<Short, String> map = mapper.readValue("{\"32767\":\"a\"}",
                new TypeReference<Map<Short, String>>() {});
        assertEquals("a", map.get(Short.valueOf(Short.MAX_VALUE)));
    }

    // TYPE_SHORT: value above Short.MAX_VALUE overflows -> exception
    @Test
    public void testDeserializeKey_shortOverflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"32768\":\"a\"}", new TypeReference<Map<Short, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_SHORT: lower boundary Short.MIN_VALUE is allowed
    @Test
    public void testDeserializeKey_shortMinBoundary_returnsMinValue() throws Throwable {
        Map<Short, String> map = mapper.readValue("{\"-32768\":\"a\"}",
                new TypeReference<Map<Short, String>>() {});
        assertEquals("a", map.get(Short.valueOf(Short.MIN_VALUE)));
    }

    // TYPE_SHORT: value below Short.MIN_VALUE underflows -> exception
    @Test
    public void testDeserializeKey_shortUnderflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"-32769\":\"a\"}", new TypeReference<Map<Short, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_CHAR: single-character key converts to Character
    @Test
    public void testDeserializeKey_charSingle_returnsChar() throws Throwable {
        Map<Character, String> map = mapper.readValue("{\"x\":\"a\"}",
                new TypeReference<Map<Character, String>>() {});
        assertEquals("a", map.get(Character.valueOf('x')));
    }

    // TYPE_CHAR: multi-character key is weird -> exception
    @Test
    public void testDeserializeKey_charMultiple_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"xy\":\"a\"}", new TypeReference<Map<Character, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_INT: negative integer parses correctly
    @Test
    public void testDeserializeKey_intNegative_returnsIntValue() throws Throwable {
        Map<Integer, String> map = mapper.readValue("{\"-456\":\"a\"}",
                new TypeReference<Map<Integer, String>>() {});
        assertEquals("a", map.get(Integer.valueOf(-456)));
    }

    // TYPE_LONG: value beyond int range parses correctly
    @Test
    public void testDeserializeKey_longLarge_returnsLongValue() throws Throwable {
        Map<Long, String> map = mapper.readValue("{\"123456789012\":\"a\"}",
                new TypeReference<Map<Long, String>>() {});
        assertEquals("a", map.get(Long.valueOf(123456789012L)));
    }

    // TYPE_FLOAT: exactly-representable decimal avoids double-rounding ambiguity
    @Test
    public void testDeserializeKey_floatNormal_returnsFloatValue() throws Throwable {
        Map<Float, String> map = mapper.readValue("{\"3.5\":\"a\"}",
                new TypeReference<Map<Float, String>>() {});
        assertEquals("a", map.get(Float.valueOf(3.5f)));
    }

    // TYPE_DOUBLE: decimal value parses to double
    @Test
    public void testDeserializeKey_doubleNormal_returnsDoubleValue() throws Throwable {
        Map<Double, String> map = mapper.readValue("{\"2.71828\":\"a\"}",
                new TypeReference<Map<Double, String>>() {});
        assertEquals("a", map.get(Double.valueOf(2.71828)));
    }

    // TYPE_UUID: well-formed UUID string parses correctly
    @Test
    public void testDeserializeKey_uuidValid_returnsUUID() throws Throwable {
        String uuidStr = "123e4567-e89b-12d3-a456-426614174000";
        Map<UUID, String> map = mapper.readValue("{\"" + uuidStr + "\":\"a\"}",
                new TypeReference<Map<UUID, String>>() {});
        assertEquals("a", map.get(UUID.fromString(uuidStr)));
    }

    // TYPE_UUID: malformed UUID string -> exception
    @Test
    public void testDeserializeKey_uuidInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"not-a-uuid\":\"a\"}", new TypeReference<Map<UUID, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_URI: valid URI string parses correctly
    @Test
    public void testDeserializeKey_uriValid_returnsURI() throws Throwable {
        Map<URI, String> map = mapper.readValue("{\"http://example.com/path\":\"a\"}",
                new TypeReference<Map<URI, String>>() {});
        assertEquals("a", map.get(URI.create("http://example.com/path")));
    }

    // TYPE_URI: unescaped space makes an invalid URI -> exception
    @Test
    public void testDeserializeKey_uriInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"not a uri\":\"a\"}", new TypeReference<Map<URI, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_URL: valid URL string parses correctly
    @Test
    public void testDeserializeKey_urlValid_returnsURL() throws Throwable {
        Map<URL, String> map = mapper.readValue("{\"http://example.com/page\":\"a\"}",
                new TypeReference<Map<URL, String>>() {});
        assertEquals("a", map.get(new URL("http://example.com/page")));
    }

    // TYPE_URL: missing protocol -> MalformedURLException -> exception
    @Test
    public void testDeserializeKey_urlInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"notaurl\":\"a\"}", new TypeReference<Map<URL, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_CLASS: fully qualified class name resolves to Class object
    @Test
    public void testDeserializeKey_classValid_returnsClass() throws Throwable {
        Map<Class<?>, String> map = mapper.readValue("{\"java.lang.String\":\"a\"}",
                new TypeReference<Map<Class<?>, String>>() {});
        assertEquals("a", map.get(String.class));
    }

    // TYPE_CLASS: unresolvable class name -> exception
    @Test
    public void testDeserializeKey_classInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"com.nonexistent.NoSuchClassXYZ\":\"a\"}",
                    new TypeReference<Map<Class<?>, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_CURRENCY: valid ISO currency code resolves via delegate deserializer
    @Test
    public void testDeserializeKey_currencyValid_returnsCurrency() throws Throwable {
        Map<Currency, String> map = mapper.readValue("{\"USD\":\"a\"}",
                new TypeReference<Map<Currency, String>>() {});
        assertEquals("a", map.get(Currency.getInstance("USD")));
    }

    // TYPE_LOCALE: underscore separated locale string resolves via delegate deserializer
    @Test
    public void testDeserializeKey_localeValid_returnsLocale() throws Throwable {
        Map<Locale, String> map = mapper.readValue("{\"en_US\":\"a\"}",
                new TypeReference<Map<Locale, String>>() {});
        Locale found = null;
        for (Locale loc : map.keySet()) {
            found = loc;
        }
        assertNotNull(found);
        assertEquals("en", found.getLanguage());
        assertEquals("US", found.getCountry());
    }

    // TYPE_DATE: ISO-8601 string parses to Date at expected epoch millis
    @Test
    public void testDeserializeKey_dateValid_returnsDate() throws Throwable {
        Map<Date, String> map = mapper.readValue("{\"1970-01-01T00:00:00.000+0000\":\"a\"}",
                new TypeReference<Map<Date, String>>() {});
        assertEquals("a", map.get(new Date(0L)));
    }

    // TYPE_CALENDAR: ISO-8601 string parses to Calendar at expected epoch millis
    @Test
    public void testDeserializeKey_calendarValid_returnsCalendar() throws Throwable {
        Map<Calendar, String> map = mapper.readValue("{\"1970-01-01T00:00:00.000+0000\":\"a\"}",
                new TypeReference<Map<Calendar, String>>() {});
        Calendar found = null;
        for (Calendar c : map.keySet()) {
            found = c;
        }
        assertNotNull(found);
        assertEquals(0L, found.getTimeInMillis());
    }

    // Enum key: name-based lookup resolves valid enum constant
    @Test
    public void testDeserializeKey_enumValid_returnsEnumConstant() throws Throwable {
        Map<TestColor, String> map = mapper.readValue("{\"RED\":\"a\"}",
                new TypeReference<Map<TestColor, String>>() {});
        assertEquals("a", map.get(TestColor.RED));
    }

    // Enum key: unknown value without lenient flag -> exception
    @Test
    public void testDeserializeKey_enumInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"PURPLE\":\"a\"}", new TypeReference<Map<TestColor, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // Enum key: unknown value with READ_UNKNOWN_ENUM_VALUES_AS_NULL results in null key, no exception
    @Test
    public void testDeserializeKey_enumUnknownAsNull_returnsNullKey() throws Throwable {
        ObjectMapper lenientMapper = new ObjectMapper();
        lenientMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        Map<TestColor, String> map = lenientMapper.readValue("{\"PURPLE\":\"a\"}",
                new TypeReference<Map<TestColor, String>>() {});
        assertEquals(1, map.size());
        assertTrue(map.containsKey(null));
    }

    // Enum key: READ_ENUMS_USING_TO_STRING resolves enum via overridden toString() value
    @Test
    public void testDeserializeKey_enumUsingToString_returnsEnumConstant() throws Throwable {
        ObjectMapper toStringMapper = new ObjectMapper();
        toStringMapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
        Map<Suit, String> map = toStringMapper.readValue("{\"c\":\"a\"}",
                new TypeReference<Map<Suit, String>>() {});
        assertEquals("a", map.get(Suit.CLUB));
    }
}
