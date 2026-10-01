package com.fasterxml.jackson.databind.deser.std;

import java.net.URI;
import java.net.URL;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
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

public class StdKeyDeserializerClaudeTest
{
    private ObjectMapper mapper;

    public static class DummyKeyType {
        private int value;
        public DummyKeyType() { }
        public int getValue() { return value; }
        public void setValue(int v) { this.value = v; }
    }

    public enum SampleEnum { FOO, BAR }

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // forType(): String.class returns cached singleton instance on repeated calls
    @Test
    public void testForType_stringClass_returnsSameSingletonInstance() throws Throwable {
        StdKeyDeserializer kd1 = StdKeyDeserializer.forType(String.class);
        StdKeyDeserializer kd2 = StdKeyDeserializer.forType(String.class);
        assertSame(kd1, kd2);
    }

    // forType(): Object.class returns cached singleton instance, correct keyClass
    @Test
    public void testForType_objectClass_returnsSameSingletonInstance() throws Throwable {
        StdKeyDeserializer kd1 = StdKeyDeserializer.forType(Object.class);
        StdKeyDeserializer kd2 = StdKeyDeserializer.forType(Object.class);
        assertSame(kd1, kd2);
        assertEquals(Object.class, kd1.getKeyClass());
    }

    // forType(): unsupported class falls through to final else branch returning null
    @Test
    public void testForType_unsupportedClass_returnsNull() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(DummyKeyType.class);
        assertNull(kd);
    }

    // forType(): Integer.class produces deserializer with correct keyClass
    @Test
    public void testForType_integerClass_correctKeyClass() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(Integer.class);
        assertNotNull(kd);
        assertEquals(Integer.class, kd.getKeyClass());
    }

    // forType(): UUID.class produces deserializer with correct keyClass
    @Test
    public void testForType_uuidClass_correctKeyClass() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(UUID.class);
        assertNotNull(kd);
        assertEquals(UUID.class, kd.getKeyClass());
    }

    // TYPE_BOOLEAN: "true" literal branch returns Boolean.TRUE
    @Test
    public void testDeserializeKey_booleanTrue_returnsTrue() throws Throwable {
        Map<Boolean, String> map = mapper.readValue("{\"true\":\"x\"}", new TypeReference<Map<Boolean, String>>() {});
        assertTrue(map.containsKey(Boolean.TRUE));
        assertEquals("x", map.get(Boolean.TRUE));
    }

    // TYPE_BOOLEAN: "false" literal branch returns Boolean.FALSE
    @Test
    public void testDeserializeKey_booleanFalse_returnsFalse() throws Throwable {
        Map<Boolean, String> map = mapper.readValue("{\"false\":\"x\"}", new TypeReference<Map<Boolean, String>>() {});
        assertTrue(map.containsKey(Boolean.FALSE));
    }

    // TYPE_BOOLEAN: neither "true" nor "false" -> weirdKeyException
    @Test
    public void testDeserializeKey_booleanInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"True\":\"x\"}", new TypeReference<Map<Boolean, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_BYTE: value within normal signed byte range parses exactly
    @Test
    public void testDeserializeKey_byteWithinRange_returnsCorrectValue() throws Throwable {
        Map<Byte, String> map = mapper.readValue("{\"127\":\"x\"}", new TypeReference<Map<Byte, String>>() {});
        assertEquals(Byte.valueOf((byte) 127), map.keySet().iterator().next());
    }

    // TYPE_BYTE: lower bound Byte.MIN_VALUE parses exactly
    @Test
    public void testDeserializeKey_byteLowerBound_returnsCorrectValue() throws Throwable {
        Map<Byte, String> map = mapper.readValue("{\"-128\":\"x\"}", new TypeReference<Map<Byte, String>>() {});
        assertEquals(Byte.valueOf((byte) -128), map.keySet().iterator().next());
    }

    // TYPE_BYTE: extended range up to 255 (JACKSON-804) truncates via (byte) cast
    @Test
    public void testDeserializeKey_byteUpperExtendedRange_truncatesCorrectly() throws Throwable {
        Map<Byte, String> map = mapper.readValue("{\"255\":\"x\"}", new TypeReference<Map<Byte, String>>() {});
        assertEquals(Byte.valueOf((byte) 255), map.keySet().iterator().next());
    }

    // TYPE_BYTE: value beyond 255 triggers overflow exception
    @Test
    public void testDeserializeKey_byteOverflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"256\":\"x\"}", new TypeReference<Map<Byte, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_BYTE: value below Byte.MIN_VALUE triggers overflow exception
    @Test
    public void testDeserializeKey_byteUnderflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"-129\":\"x\"}", new TypeReference<Map<Byte, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_SHORT: Short.MAX_VALUE boundary parses exactly
    @Test
    public void testDeserializeKey_shortMaxValue_returnsCorrectValue() throws Throwable {
        Map<Short, String> map = mapper.readValue("{\"32767\":\"x\"}", new TypeReference<Map<Short, String>>() {});
        assertEquals(Short.valueOf((short) 32767), map.keySet().iterator().next());
    }

    // TYPE_SHORT: Short.MIN_VALUE boundary parses exactly
    @Test
    public void testDeserializeKey_shortMinValue_returnsCorrectValue() throws Throwable {
        Map<Short, String> map = mapper.readValue("{\"-32768\":\"x\"}", new TypeReference<Map<Short, String>>() {});
        assertEquals(Short.valueOf((short) -32768), map.keySet().iterator().next());
    }

    // TYPE_SHORT: value beyond max triggers overflow exception
    @Test
    public void testDeserializeKey_shortOverflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"32768\":\"x\"}", new TypeReference<Map<Short, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_SHORT: value below min triggers overflow exception
    @Test
    public void testDeserializeKey_shortUnderflow_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"-32769\":\"x\"}", new TypeReference<Map<Short, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_CHAR: single-character key parses to matching Character
    @Test
    public void testDeserializeKey_charSingle_returnsCorrectValue() throws Throwable {
        Map<Character, String> map = mapper.readValue("{\"a\":\"x\"}", new TypeReference<Map<Character, String>>() {});
        assertEquals(Character.valueOf('a'), map.keySet().iterator().next());
    }

    // TYPE_CHAR: multi-character key triggers exception
    @Test
    public void testDeserializeKey_charMultiple_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"ab\":\"x\"}", new TypeReference<Map<Character, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_INT: negative integer literal parses correctly
    @Test
    public void testDeserializeKey_intNegative_returnsCorrectValue() throws Throwable {
        Map<Integer, String> map = mapper.readValue("{\"-123\":\"x\"}", new TypeReference<Map<Integer, String>>() {});
        assertEquals(Integer.valueOf(-123), map.keySet().iterator().next());
    }

    // TYPE_INT: non-numeric literal triggers exception
    @Test
    public void testDeserializeKey_intInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"abc\":\"x\"}", new TypeReference<Map<Integer, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_LONG: Long.MAX_VALUE literal parses correctly
    @Test
    public void testDeserializeKey_longValue_returnsCorrectValue() throws Throwable {
        Map<Long, String> map = mapper.readValue("{\"9223372036854775807\":\"x\"}", new TypeReference<Map<Long, String>>() {});
        assertEquals(Long.valueOf(Long.MAX_VALUE), map.keySet().iterator().next());
    }

    // TYPE_FLOAT: decimal literal parses to correct float value
    @Test
    public void testDeserializeKey_floatValue_returnsCorrectValue() throws Throwable {
        Map<Float, String> map = mapper.readValue("{\"3.5\":\"x\"}", new TypeReference<Map<Float, String>>() {});
        Float key = map.keySet().iterator().next();
        assertEquals(3.5f, key.floatValue(), 1e-6f);
    }

    // TYPE_DOUBLE: decimal literal parses to correct double value
    @Test
    public void testDeserializeKey_doubleValue_returnsCorrectValue() throws Throwable {
        Map<Double, String> map = mapper.readValue("{\"3.14\":\"x\"}", new TypeReference<Map<Double, String>>() {});
        Double key = map.keySet().iterator().next();
        assertEquals(3.14, key.doubleValue(), 1e-9);
    }

    // TYPE_LOCALE: language-only tag parses into Locale with matching language
    @Test
    public void testDeserializeKey_localeValue_returnsCorrectLanguage() throws Throwable {
        Map<Locale, String> map = mapper.readValue("{\"en\":\"x\"}", new TypeReference<Map<Locale, String>>() {});
        Locale loc = map.keySet().iterator().next();
        assertEquals("en", loc.getLanguage());
    }

    // TYPE_CURRENCY: valid ISO currency code parses to matching Currency instance
    @Test
    public void testDeserializeKey_currencyValue_returnsCorrectInstance() throws Throwable {
        Map<Currency, String> map = mapper.readValue("{\"USD\":\"x\"}", new TypeReference<Map<Currency, String>>() {});
        assertEquals(Currency.getInstance("USD"), map.keySet().iterator().next());
    }

    // TYPE_DATE: ISO8601 epoch string parses to time 0
    @Test
    public void testDeserializeKey_dateValue_returnsCorrectEpochMillis() throws Throwable {
        Map<Date, String> map = mapper.readValue("{\"1970-01-01T00:00:00.000+0000\":\"x\"}", new TypeReference<Map<Date, String>>() {});
        Date d = map.keySet().iterator().next();
        assertEquals(0L, d.getTime());
    }

    // TYPE_CALENDAR: ISO8601 epoch string parses to time 0 via constructCalendar
    @Test
    public void testDeserializeKey_calendarValue_returnsCorrectEpochMillis() throws Throwable {
        Map<Calendar, String> map = mapper.readValue("{\"1970-01-01T00:00:00.000+0000\":\"x\"}", new TypeReference<Map<Calendar, String>>() {});
        Calendar c = map.keySet().iterator().next();
        assertEquals(0L, c.getTimeInMillis());
    }

    // TYPE_UUID: well-formed UUID string parses to matching UUID instance
    @Test
    public void testDeserializeKey_uuidValid_returnsCorrectInstance() throws Throwable {
        String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
        Map<UUID, String> map = mapper.readValue("{\"" + uuidStr + "\":\"x\"}", new TypeReference<Map<UUID, String>>() {});
        assertEquals(UUID.fromString(uuidStr), map.keySet().iterator().next());
    }

    // TYPE_UUID: malformed UUID string triggers exception
    @Test
    public void testDeserializeKey_uuidInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"not-a-uuid\":\"x\"}", new TypeReference<Map<UUID, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_URI: well-formed URI string parses to matching URI instance
    @Test
    public void testDeserializeKey_uriValid_returnsCorrectInstance() throws Throwable {
        Map<URI, String> map = mapper.readValue("{\"http://example.com\":\"x\"}", new TypeReference<Map<URI, String>>() {});
        assertEquals(URI.create("http://example.com"), map.keySet().iterator().next());
    }

    // TYPE_URI: unescaped space makes an illegal URI, triggers exception
    @Test
    public void testDeserializeKey_uriInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"http:// bad uri\":\"x\"}", new TypeReference<Map<URI, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_URL: well-formed URL string parses to matching URL instance
    @Test
    public void testDeserializeKey_urlValid_returnsCorrectInstance() throws Throwable {
        Map<URL, String> map = mapper.readValue("{\"http://example.com\":\"x\"}", new TypeReference<Map<URL, String>>() {});
        assertEquals(new URL("http://example.com"), map.keySet().iterator().next());
    }

    // TYPE_URL: string without protocol is malformed, triggers exception
    @Test
    public void testDeserializeKey_urlInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"not a url\":\"x\"}", new TypeReference<Map<URL, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // TYPE_CLASS: fully-qualified class name resolves via ctxt.findClass
    @Test
    public void testDeserializeKey_classValid_returnsCorrectClass() throws Throwable {
        Map<Class<?>, String> map = mapper.readValue("{\"java.lang.String\":\"x\"}", new TypeReference<Map<Class<?>, String>>() {});
        assertEquals(String.class, map.keySet().iterator().next());
    }

    // TYPE_CLASS: unresolvable class name triggers exception
    @Test
    public void testDeserializeKey_classInvalid_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"com.does.not.Exist\":\"x\"}", new TypeReference<Map<Class<?>, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // StringKD: String-typed key is returned as-is
    @Test
    public void testDeserializeKey_stringKey_returnsSameString() throws Throwable {
        Map<String, String> map = mapper.readValue("{\"hello\":\"x\"}", new TypeReference<Map<String, String>>() {});
        assertTrue(map.containsKey("hello"));
        assertEquals("x", map.get("hello"));
    }

    // EnumKD: valid enum name resolves to matching constant
    @Test
    public void testDeserializeKey_enumValid_returnsCorrectConstant() throws Throwable {
        Map<SampleEnum, String> map = mapper.readValue("{\"FOO\":\"x\"}", new TypeReference<Map<SampleEnum, String>>() {});
        assertTrue(map.containsKey(SampleEnum.FOO));
    }

    // EnumKD: unknown enum value with feature disabled (default) triggers exception
    @Test
    public void testDeserializeKey_enumUnknownFeatureDisabled_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"UNKNOWN\":\"x\"}", new TypeReference<Map<SampleEnum, String>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // EnumKD: unknown enum value with READ_UNKNOWN_ENUM_VALUES_AS_NULL enabled maps to null key
    @Test
    public void testDeserializeKey_enumUnknownFeatureEnabled_returnsNullKey() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        Map<SampleEnum, String> map = m.readValue("{\"UNKNOWN\":\"x\"}", new TypeReference<Map<SampleEnum, String>>() {});
        assertEquals("x", map.get(null));
    }

    // getKeyClass(): package-accessible protected constructor sets keyClass correctly
    @Test
    public void testGetKeyClass_directConstruction_returnsCorrectClass() throws Throwable {
        StdKeyDeserializer kd = new StdKeyDeserializer(StdKeyDeserializer.TYPE_LONG, Long.class);
        assertEquals(Long.class, kd.getKeyClass());
    }

    // deserializeKey(): null key short-circuits to null without touching context
    @Test
    public void testDeserializeKey_nullKey_returnsNull() throws Throwable {
        StdKeyDeserializer kd = StdKeyDeserializer.forType(Integer.class);
        Object result = kd.deserializeKey(null, null);
        assertNull(result);
    }
}
