package com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class DateTimeSerializerBaseClaudeTest
{
    private TimeZone originalDefaultTimeZone;

    @Before
    public void setUp() throws Throwable {
        originalDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void tearDown() throws Throwable {
        TimeZone.setDefault(originalDefaultTimeZone);
    }

    static class TestDateTimeSerializer extends DateTimeSerializerBase<Date> {
        public TestDateTimeSerializer(Boolean useTimestamp, DateFormat customFormat) {
            super(Date.class, useTimestamp, customFormat);
        }
        @Override
        public DateTimeSerializerBase<Date> withFormat(Boolean timestamp, DateFormat customFormat) {
            return new TestDateTimeSerializer(timestamp, customFormat);
        }
        @Override
        protected long _timestamp(Date value) {
            return (value == null) ? -1L : value.getTime();
        }
        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (_asTimestamp(serializers)) {
                gen.writeNumber(_timestamp(value));
            } else {
                _serializeAsString(value, gen, serializers);
            }
        }
    }

    static class ProbeAsTimestampSerializer extends DateTimeSerializerBase<Date> {
        public ProbeAsTimestampSerializer() {
            this(null, null);
        }
        public ProbeAsTimestampSerializer(Boolean useTimestamp, DateFormat customFormat) {
            super(Date.class, useTimestamp, customFormat);
        }
        @Override
        public DateTimeSerializerBase<Date> withFormat(Boolean timestamp, DateFormat customFormat) {
            return new ProbeAsTimestampSerializer(timestamp, customFormat);
        }
        @Override
        protected long _timestamp(Date value) {
            return value.getTime();
        }
        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeBoolean(_asTimestamp(serializers));
        }
    }

    static class DummyDateFormat extends DateFormat {
        @Override
        public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
            toAppendTo.append("dummy");
            return toAppendTo;
        }
        @Override
        public Date parse(String source, ParsePosition pos) {
            return null;
        }
    }

    public static class PlainDateHolder {
        private Date date;
        public PlainDateHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class NumericShapeHolder {
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
        private Date date;
        public NumericShapeHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class PatternTimezoneHolder {
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
        private Date date;
        public PatternTimezoneHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class PatternLocaleHolder {
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC", locale = "fr")
        private Date date;
        public PatternLocaleHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class PatternNoTimezoneHolder {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private Date date;
        public PatternNoTimezoneHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class StringShapeHolder {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Date date;
        public StringShapeHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class StringShapeLocaleHolder {
        @JsonFormat(shape = JsonFormat.Shape.STRING, locale = "fr")
        private Date date;
        public StringShapeLocaleHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class StringShapeTimezoneHolder {
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "America/New_York")
        private Date date;
        public StringShapeTimezoneHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class LocaleOnlyHolder {
        @JsonFormat(locale = "fr")
        private Date date;
        public LocaleOnlyHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class TimezoneOnlyHolder {
        @JsonFormat(timezone = "America/New_York")
        private Date date;
        public TimezoneOnlyHolder(Date date) { this.date = date; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class ProbeHolder {
        @JsonSerialize(using = ProbeAsTimestampSerializer.class)
        private Date date = new Date(0L);
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    // constructor: customFormat null => _reusedCustomFormat must stay null
    @Test
    public void testConstructor_customFormatNull_reusedCustomFormatIsNull() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(Boolean.TRUE, null);
        assertNull(ser._reusedCustomFormat);
        assertNull(ser._customFormat);
    }

    // constructor: customFormat non-null => _reusedCustomFormat is a fresh empty AtomicReference
    @Test
    public void testConstructor_customFormatNonNull_reusedCustomFormatStartsEmpty() throws Throwable {
        SimpleDateFormat df = new SimpleDateFormat("yyyy");
        TestDateTimeSerializer ser = new TestDateTimeSerializer(null, df);
        assertNotNull(ser._reusedCustomFormat);
        assertNull(ser._reusedCustomFormat.get());
    }

    // withFormat: returns new instance with updated fields, does not mutate original
    @Test
    public void testWithFormat_returnsNewInstanceWithUpdatedFields() throws Throwable {
        TestDateTimeSerializer original = new TestDateTimeSerializer(null, null);
        SimpleDateFormat df = new SimpleDateFormat("yyyy");
        DateTimeSerializerBase<Date> result = original.withFormat(Boolean.FALSE, df);
        assertNotSame(original, result);
        assertEquals(Boolean.FALSE, result._useTimestamp);
        assertSame(df, result._customFormat);
    }



    // isEmpty: per contract (2.9+) timestamp 0 / epoch date is NOT empty
    @Test
    public void testIsEmpty_epochDate_returnsFalse() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(null, null);
        assertFalse(ser.isEmpty(null, new Date(0L)));
    }

    // isEmpty: null value also treated as not-empty per unconditional contract
    @Test
    public void testIsEmpty_nullValue_returnsFalse() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(null, null);
        assertFalse(ser.isEmpty(null, null));
    }

    // getSchema: useTimestamp TRUE => schema type "number"
    @Test
    public void testGetSchema_useTimestampTrue_returnsNumberSchema() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(Boolean.TRUE, null);
        JsonNode schema = ser.getSchema(null, Date.class);
        assertTrue(schema.toString().contains("number"));
    }

    // getSchema: useTimestamp FALSE => schema type "string"
    @Test
    public void testGetSchema_useTimestampFalse_returnsStringSchema() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(Boolean.FALSE, null);
        JsonNode schema = ser.getSchema(null, Date.class);
        assertTrue(schema.toString().contains("string"));
    }

    // _asTimestamp: useTimestamp TRUE short-circuits, provider not touched even if null
    @Test
    public void testAsTimestamp_useTimestampTrue_ignoresNullProvider() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(Boolean.TRUE, null);
        assertTrue(ser._asTimestamp(null));
    }

    // _asTimestamp: useTimestamp FALSE short-circuits, provider not touched even if null
    @Test
    public void testAsTimestamp_useTimestampFalse_ignoresNullProvider() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(Boolean.FALSE, null);
        assertFalse(ser._asTimestamp(null));
    }

    // _asTimestamp: useTimestamp null but customFormat set => always false
    @Test
    public void testAsTimestamp_customFormatSetUseTimestampNull_returnsFalse() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(null, new SimpleDateFormat("yyyy"));
        assertFalse(ser._asTimestamp(null));
    }

    // _asTimestamp: useTimestamp null, customFormat null, provider null => IllegalArgumentException
    @Test
    public void testAsTimestamp_allNull_throwsIllegalArgumentException() throws Throwable {
        TestDateTimeSerializer ser = new TestDateTimeSerializer(null, null);
        try {
            ser._asTimestamp(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("SerializerProvider"));
        }
    }

    // _asTimestamp via real provider: WRITE_DATES_AS_TIMESTAMPS enabled => true
    @Test
    public void testAsTimestamp_viaMapper_timestampsEnabled_returnsTrue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(new ProbeHolder());
        assertTrue(json.contains("true"));
    }

    // _asTimestamp via real provider: WRITE_DATES_AS_TIMESTAMPS disabled => false
    @Test
    public void testAsTimestamp_viaMapper_timestampsDisabled_returnsFalse() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(new ProbeHolder());
        assertTrue(json.contains("false"));
    }

    // _serializeAsString: customFormat non-null, first call clones format and populates reuse pool
    @Test
    public void testSerializeAsString_firstCall_formatsAndPopulatesReuse() throws Throwable {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        TestDateTimeSerializer ser = new TestDateTimeSerializer(Boolean.FALSE, df);
        StringWriter sw = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(sw);
        ser._serializeAsString(new Date(0L), gen, null);
        gen.close();
        assertEquals("\"1970-01-01\"", sw.toString());
        assertNotNull(ser._reusedCustomFormat.get());
    }

    // _serializeAsString: second call reuses pooled formatter instance from first call
    @Test
    public void testSerializeAsString_secondCall_reusesPooledFormatter() throws Throwable {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        TestDateTimeSerializer ser = new TestDateTimeSerializer(Boolean.FALSE, df);
        StringWriter sw1 = new StringWriter();
        JsonGenerator gen1 = new JsonFactory().createGenerator(sw1);
        ser._serializeAsString(new Date(0L), gen1, null);
        gen1.close();
        StringWriter sw2 = new StringWriter();
        JsonGenerator gen2 = new JsonFactory().createGenerator(sw2);
        ser._serializeAsString(new Date(86400000L), gen2, null);
        gen2.close();
        assertEquals("\"1970-01-02\"", sw2.toString());
    }

    // createContextual: numeric shape forces timestamp output regardless of global config
    @Test
    public void testCreateContextual_numericShape_forcesTimestamp() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(new NumericShapeHolder(new Date(123456L)));
        assertEquals("{\"date\":123456}", json);
    }

    // createContextual: explicit pattern + explicit timezone
    @Test
    public void testCreateContextual_patternWithTimezone_formatsWithPattern() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new PatternTimezoneHolder(new Date(0L)));
        assertEquals("{\"date\":\"1970-01-01\"}", json);
    }

    // createContextual: explicit pattern + explicit locale (locale ignored by purely numeric pattern)
    @Test
    public void testCreateContextual_patternWithLocale_formatsWithPattern() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new PatternLocaleHolder(new Date(0L)));
        assertEquals("{\"date\":\"1970-01-01\"}", json);
    }

    // createContextual: pattern without timezone falls back to provider's configured timezone
    @Test
    public void testCreateContextual_patternWithoutTimezone_usesProviderTimezone() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        String json = mapper.writeValueAsString(new PatternNoTimezoneHolder(new Date(0L)));
        assertEquals("{\"date\":\"1970-01-01\"}", json);
    }

    // createContextual: shape STRING with default StdDateFormat produces quoted string
    @Test
    public void testCreateContextual_stringShapeDefault_producesQuotedString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new StringShapeHolder(new Date(0L)));
        assertTrue(json.matches("\\{\"date\":\"[^\"]+\"\\}"));
    }

    // createContextual: shape STRING with locale override on StdDateFormat still produces string
    @Test
    public void testCreateContextual_stringShapeWithLocale_producesQuotedString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new StringShapeLocaleHolder(new Date(0L)));
        assertTrue(json.matches("\\{\"date\":\"[^\"]+\"\\}"));
    }

    // createContextual: shape STRING with explicit timezone applies that timezone (date rolls back)
    @Test
    public void testCreateContextual_stringShapeWithTimezone_appliesTimezone() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new StringShapeTimezoneHolder(new Date(0L)));
        assertTrue(json.contains("1969-12-31"));
    }

    // createContextual: locale-only with default StdDateFormat still forces string serialization
    @Test
    public void testCreateContextual_localeOnlyDefaultStdDateFormat_producesQuotedString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new LocaleOnlyHolder(new Date(0L)));
        assertTrue(json.matches("\\{\"date\":\"[^\"]+\"\\}"));
    }



    // createContextual: timezone-only override on a SimpleDateFormat correctly applies new timezone
    @Test
    public void testCreateContextual_timezoneOnlyWithSimpleDateFormat_appliesTimezone() throws Throwable {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        df.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(df);
        String json = mapper.writeValueAsString(new TimezoneOnlyHolder(new Date(0L)));
        assertEquals("{\"date\":\"1969-12-31 19:00\"}", json);
    }

    // createContextual: no @JsonFormat annotation, format==null => unchanged, uses global timestamp default
    @Test
    public void testCreateContextual_noFormatAnnotation_defaultsToTimestamp() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(new PlainDateHolder(new Date(999L)));
        assertEquals("{\"date\":999}", json);
    }

    // createContextual: no annotation, global timestamps disabled => serialized as string
    @Test
    public void testCreateContextual_noFormatAnnotation_timestampsDisabled_producesQuotedString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(new PlainDateHolder(new Date(999L)));
        assertTrue(json.matches("\\{\"date\":\"[^\"]+\"\\}"));
    }

    // createContextual: asString required but configured DateFormat is neither StdDateFormat nor SimpleDateFormat
    @Test
    public void testCreateContextual_incompatibleDateFormat_throwsJsonMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(new DummyDateFormat());
        try {
            mapper.writeValueAsString(new StringShapeHolder(new Date(0L)));
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected);
        }
    }

    // root-level Date (no property context) with default timestamp setting produces plain number
    @Test
    public void testRootLevelDate_defaultTimestampsEnabled_numericOutput() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(new Date(0L));
        assertEquals("0", json);
    }
}
