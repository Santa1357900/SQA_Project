package com.fasterxml.jackson.databind.ser.std;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NumberSerializersClaudeTest
{
    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable {
        factory = new JsonFactory();
    }

    public static class IntegerHolder {
        private Integer value;
        public IntegerHolder() { }
        public IntegerHolder(Integer value) { this.value = value; }
        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }
    }

    public static class IntegerStringHolder {
        private Integer value;
        public IntegerStringHolder() { }
        public IntegerStringHolder(Integer value) { this.value = value; }
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }
    }

    public static class ShortStringHolder {
        private Short value;
        public ShortStringHolder() { }
        public ShortStringHolder(Short value) { this.value = value; }
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Short getValue() { return value; }
        public void setValue(Short value) { this.value = value; }
    }

    // addAll: Integer.class and int.class must map to the SAME IntegerSerializer instance
    @Test
    public void testAddAll_intAndInt_mapToSameIntegerSerializerInstance() throws Throwable {
        Map<String, JsonSerializer<?>> map = new HashMap<String, JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        JsonSerializer<?> forInteger = map.get(Integer.class.getName());
        JsonSerializer<?> forInt = map.get(Integer.TYPE.getName());
        assertTrue(forInteger instanceof NumberSerializers.IntegerSerializer);
        assertSame(forInteger, forInt);
    }

    // addAll: Long.class and long.class map to shared LongSerializer.instance
    @Test
    public void testAddAll_longAndLongType_mapToLongSerializerInstance() throws Throwable {
        Map<String, JsonSerializer<?>> map = new HashMap<String, JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        assertSame(NumberSerializers.LongSerializer.instance, map.get(Long.class.getName()));
        assertSame(NumberSerializers.LongSerializer.instance, map.get(Long.TYPE.getName()));
    }

    // addAll: Byte.class and byte.class map to shared IntLikeSerializer.instance
    @Test
    public void testAddAll_byteAndByteType_mapToIntLikeSerializerInstance() throws Throwable {
        Map<String, JsonSerializer<?>> map = new HashMap<String, JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        assertSame(NumberSerializers.IntLikeSerializer.instance, map.get(Byte.class.getName()));
        assertSame(NumberSerializers.IntLikeSerializer.instance, map.get(Byte.TYPE.getName()));
    }

    // addAll: Short.class and short.class map to shared ShortSerializer.instance
    @Test
    public void testAddAll_shortAndShortType_mapToShortSerializerInstance() throws Throwable {
        Map<String, JsonSerializer<?>> map = new HashMap<String, JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        assertSame(NumberSerializers.ShortSerializer.instance, map.get(Short.class.getName()));
        assertSame(NumberSerializers.ShortSerializer.instance, map.get(Short.TYPE.getName()));
    }

    // addAll: Float.class and float.class map to shared FloatSerializer.instance
    @Test
    public void testAddAll_floatAndFloatType_mapToFloatSerializerInstance() throws Throwable {
        Map<String, JsonSerializer<?>> map = new HashMap<String, JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        assertSame(NumberSerializers.FloatSerializer.instance, map.get(Float.class.getName()));
        assertSame(NumberSerializers.FloatSerializer.instance, map.get(Float.TYPE.getName()));
    }

    // addAll: Double.class and double.class map to shared DoubleSerializer.instance
    @Test
    public void testAddAll_doubleAndDoubleType_mapToDoubleSerializerInstance() throws Throwable {
        Map<String, JsonSerializer<?>> map = new HashMap<String, JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        assertSame(NumberSerializers.DoubleSerializer.instance, map.get(Double.class.getName()));
        assertSame(NumberSerializers.DoubleSerializer.instance, map.get(Double.TYPE.getName()));
    }

    // addAll: loop runs exactly 12 puts total, map must contain all 12 keys
    @Test
    public void testAddAll_populatesAllTwelveEntries() throws Throwable {
        Map<String, JsonSerializer<?>> map = new HashMap<String, JsonSerializer<?>>();
        NumberSerializers.addAll(map);
        assertEquals(12, map.size());
    }

    // ShortSerializer.serialize: positive short written as plain JSON number
    @Test
    public void testShortSerializerSerialize_positiveValue_writesPlainNumber() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.ShortSerializer ser = new NumberSerializers.ShortSerializer();
        ser.serialize(Short.valueOf((short) 7), gen, null);
        gen.flush();
        assertEquals("7", sw.toString());
    }

    // ShortSerializer.serialize: negative short written as plain JSON number
    @Test
    public void testShortSerializerSerialize_negativeValue_writesPlainNumber() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.ShortSerializer ser = new NumberSerializers.ShortSerializer();
        ser.serialize(Short.valueOf((short) -3), gen, null);
        gen.flush();
        assertEquals("-3", sw.toString());
    }

    // IntegerSerializer.serialize: MIN_VALUE and MAX_VALUE boundaries written correctly
    @Test
    public void testIntegerSerializerSerialize_minAndMaxValue_writesPlainNumber() throws Throwable {
        NumberSerializers.IntegerSerializer ser = new NumberSerializers.IntegerSerializer();
        StringWriter sw1 = new StringWriter();
        JsonGenerator gen1 = factory.createGenerator(sw1);
        ser.serialize(Integer.valueOf(Integer.MAX_VALUE), gen1, null);
        gen1.flush();
        assertEquals(String.valueOf(Integer.MAX_VALUE), sw1.toString());
    }

    // IntegerSerializer.serializeWithType: must ignore typeSer and write plain number
    @Test
    public void testIntegerSerializerSerializeWithType_ignoresTypeSerializer_writesPlainNumber() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.IntegerSerializer ser = new NumberSerializers.IntegerSerializer();
        ser.serializeWithType(Integer.valueOf(99), gen, null, null);
        gen.flush();
        assertEquals("99", sw.toString());
    }

    // IntLikeSerializer.serialize: positive Byte converted via intValue()
    @Test
    public void testIntLikeSerializerSerialize_bytePositiveValue_writesIntValue() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.IntLikeSerializer ser = new NumberSerializers.IntLikeSerializer();
        ser.serialize(Byte.valueOf((byte) 5), gen, null);
        gen.flush();
        assertEquals("5", sw.toString());
    }

    // IntLikeSerializer.serialize: negative Byte converted via intValue()
    @Test
    public void testIntLikeSerializerSerialize_byteNegativeValue_writesIntValue() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.IntLikeSerializer ser = new NumberSerializers.IntLikeSerializer();
        ser.serialize(Byte.valueOf((byte) -5), gen, null);
        gen.flush();
        assertEquals("-5", sw.toString());
    }

    // LongSerializer.serialize: large long value written as plain number
    @Test
    public void testLongSerializerSerialize_largeValue_writesPlainNumber() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.LongSerializer ser = new NumberSerializers.LongSerializer();
        ser.serialize(Long.valueOf(9999999999L), gen, null);
        gen.flush();
        assertEquals("9999999999", sw.toString());
    }

    // FloatSerializer.serialize: exact float value written as plain number
    @Test
    public void testFloatSerializerSerialize_writesPlainNumber() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.FloatSerializer ser = new NumberSerializers.FloatSerializer();
        ser.serialize(Float.valueOf(2.5f), gen, null);
        gen.flush();
        assertEquals("2.5", sw.toString());
    }

    // DoubleSerializer.serialize: exact double value written as plain number
    @Test
    public void testDoubleSerializerSerialize_writesPlainNumber() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.DoubleSerializer ser = new NumberSerializers.DoubleSerializer();
        ser.serialize(Double.valueOf(3.5d), gen, null);
        gen.flush();
        assertEquals("3.5", sw.toString());
    }

    // DoubleSerializer.serializeWithType: must ignore typeSer and write plain number
    @Test
    public void testDoubleSerializerSerializeWithType_ignoresTypeSerializer_writesPlainNumber() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        NumberSerializers.DoubleSerializer ser = new NumberSerializers.DoubleSerializer();
        ser.serializeWithType(Double.valueOf(7.25d), gen, null, null);
        gen.flush();
        assertEquals("7.25", sw.toString());
    }

    // Base.getSchema for Short: numberType is INT (_isInt true) so schema "type" must be "integer",
    // consistent with sibling IntegerSerializer/IntLikeSerializer -- this is the bug-catching oracle
    @Test
    public void testShortSerializerGetSchema_schemaTypeMatchesIntegerContract() throws Throwable {
        NumberSerializers.ShortSerializer ser = new NumberSerializers.ShortSerializer();
        JsonNode schema = ser.getSchema(null, null);
        assertEquals("integer", schema.get("type").asText());
    }

    // Base.getSchema for Integer: numberType INT -> schema "type" is "integer"
    @Test
    public void testIntegerSerializerGetSchema_schemaTypeIsInteger() throws Throwable {
        NumberSerializers.IntegerSerializer ser = new NumberSerializers.IntegerSerializer();
        JsonNode schema = ser.getSchema(null, null);
        assertEquals("integer", schema.get("type").asText());
    }

    // Base.getSchema for IntLike: numberType INT -> schema "type" is "integer"
    @Test
    public void testIntLikeSerializerGetSchema_schemaTypeIsInteger() throws Throwable {
        NumberSerializers.IntLikeSerializer ser = new NumberSerializers.IntLikeSerializer();
        JsonNode schema = ser.getSchema(null, null);
        assertEquals("integer", schema.get("type").asText());
    }

    // Base.getSchema for Float: numberType FLOAT (_isInt false) -> schema "type" is "number"
    @Test
    public void testFloatSerializerGetSchema_schemaTypeIsNumber() throws Throwable {
        NumberSerializers.FloatSerializer ser = new NumberSerializers.FloatSerializer();
        JsonNode schema = ser.getSchema(null, null);
        assertEquals("number", schema.get("type").asText());
    }

    // Base.getSchema for Double: numberType DOUBLE (_isInt false) -> schema "type" is "number"
    @Test
    public void testDoubleSerializerGetSchema_schemaTypeIsNumber() throws Throwable {
        NumberSerializers.DoubleSerializer ser = new NumberSerializers.DoubleSerializer();
        JsonNode schema = ser.getSchema(null, null);
        assertEquals("number", schema.get("type").asText());
    }

    // Base.createContextual: property == null branch returns "this" without touching provider
    @Test
    public void testCreateContextual_nullProperty_returnsSameInstance() throws Throwable {
        NumberSerializers.IntegerSerializer ser = new NumberSerializers.IntegerSerializer();
        JsonSerializer<?> result = ser.createContextual(null, null);
        assertSame(ser, result);
    }

    // Base.createContextual: shape STRING on Integer property -> serialized as JSON string
    @Test
    public void testCreateContextual_stringShapeAnnotation_convertsIntegerToStringOutput() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new IntegerStringHolder(Integer.valueOf(5)));
        assertTrue(json.contains("\"value\":\"5\""));
    }

    // Base.createContextual: no annotation -> default numeric serialization retained
    @Test
    public void testCreateContextual_defaultShape_keepsNumericOutput() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new IntegerHolder(Integer.valueOf(5)));
        assertTrue(json.contains("\"value\":5"));
    }

    // Base.createContextual applied to ShortSerializer: shape STRING -> serialized as JSON string
    @Test
    public void testShortCreateContextual_stringShapeAnnotation_convertsShortToStringOutput() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new ShortStringHolder(Short.valueOf((short) 9)));
        assertTrue(json.contains("\"value\":\"9\""));
    }
}
