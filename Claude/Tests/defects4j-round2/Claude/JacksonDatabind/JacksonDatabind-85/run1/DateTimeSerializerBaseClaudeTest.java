package com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.StdDateFormat;

public class DateTimeSerializerBaseClaudeTest
{
    // Concrete test double: needed because DateTimeSerializerBase is abstract.
    public static class TestDateSerializer extends DateTimeSerializerBase<Date>
    {
        public TestDateSerializer() {
            this(null, null);
        }

        public TestDateSerializer(Boolean useTimestamp, DateFormat customFormat) {
            super(Date.class, useTimestamp, customFormat);
        }

        @Override
        public DateTimeSerializerBase<Date> withFormat(Boolean timestamp, DateFormat customFormat) {
            return new TestDateSerializer(timestamp, customFormat);
        }

        @Override
        protected long _timestamp(Date value) {
            return (value == null) ? 0L : value.getTime();
        }

        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (_asTimestamp(serializers)) {
                gen.writeNumber(_timestamp(value));
            } else if (_customFormat != null) {
                gen.writeString(_customFormat.format(value));
            } else {
                gen.writeString(value.toString());
            }
        }
    }

    public static class NumericDateHolder {
        private Date date;
        public NumericDateHolder(Date date) { this.date = date; }
        @JsonSerialize(using = TestDateSerializer.class)
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class PatternDateHolder {
        private Date date;
        public PatternDateHolder(Date date) { this.date = date; }
        @JsonSerialize(using = TestDateSerializer.class)
        @JsonFormat(pattern = "yyyy", locale = "en", timezone = "UTC")
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class IsoStringDateHolder {
        private Date date;
        public IsoStringDateHolder(Date date) { this.date = date; }
        @JsonSerialize(using = TestDateSerializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class LocaleOnlyDateHolder {
        private Date date;
        public LocaleOnlyDateHolder(Date date) { this.date = date; }
        @JsonSerialize(using = TestDateSerializer.class)
        @JsonFormat(locale = "de")
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class TimezoneOnlyDateHolder {
        private Date date;
        public TimezoneOnlyDateHolder(Date date) { this.date = date; }
        @JsonSerialize(using = TestDateSerializer.class)
        @JsonFormat(timezone = "America/New_York")
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    public static class DefaultDateHolder {
        private Date date;
        public DefaultDateHolder(Date date) { this.date = date; }
        @JsonSerialize(using = TestDateSerializer.class)
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    private Date utcDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(year, month, day, 12, 0, 0);
        return cal.getTime();
    }

    // withFormat(TRUE, null) must yield an instance that reports timestamp mode true.
    @Test
    public void testWithFormat_TimestampTrue_NewInstanceUsesTimestamp() throws Throwable {
        TestDateSerializer base = new TestDateSerializer();
        DateTimeSerializerBase<Date> configured = base.withFormat(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertTrue(((TestDateSerializer) configured)._asTimestamp(provider));
    }

    // withFormat(FALSE, customFormat) must yield an instance that reports timestamp mode false.
    @Test
    public void testWithFormat_CustomFormat_NewInstanceUsesFormat() throws Throwable {
        TestDateSerializer base = new TestDateSerializer();
        DateFormat fmt = new SimpleDateFormat("yyyy");
        DateTimeSerializerBase<Date> configured = base.withFormat(Boolean.FALSE, fmt);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertFalse(((TestDateSerializer) configured)._asTimestamp(provider));
    }

    // property == null branch: createContextual must return the same instance.
    @Test
    public void testCreateContextual_NullProperty_ReturnsSameInstance() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        JsonSerializer<?> result = ser.createContextual(provider, null);
        assertSame(ser, result);
    }

    // shape.isNumeric() branch: numeric shape must force serialization as raw timestamp.
    @Test
    public void testCreateContextual_NumericShape_SerializesAsTimestampNumber() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Date d = new Date(123456789L);
        String json = mapper.writeValueAsString(new NumericDateHolder(d));
        assertEquals("{\"date\":123456789}", json);
    }

    // hasPattern()+hasLocale()+hasTimeZone() branch: explicit pattern/locale/timezone are honored.
    @Test
    public void testCreateContextual_PatternLocaleTimezone_SerializesWithCustomPattern() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Date d = utcDate(2020, Calendar.JULY, 15);
        String json = mapper.writeValueAsString(new PatternDateHolder(d));
        assertEquals("{\"date\":\"2020\"}", json);
    }

    // shape == STRING with no pattern: default ISO8601 pattern must be used.
    @Test
    public void testCreateContextual_StringShapeNoPattern_UsesDefaultIso8601Format() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setLocale(Locale.US);
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d = utcDate(2020, Calendar.JULY, 15);

        SimpleDateFormat expectedFormat = new SimpleDateFormat(StdDateFormat.DATE_FORMAT_STR_ISO8601, Locale.US);
        expectedFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String expected = "{\"date\":\"" + expectedFormat.format(d) + "\"}";

        String json = mapper.writeValueAsString(new IsoStringDateHolder(d));
        assertEquals(expected, json);
    }

    // hasLocale() alone triggers the custom-format branch with default ISO8601 pattern.
    @Test
    public void testCreateContextual_LocaleOnly_TriggersCustomFormatBranch() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d = utcDate(2020, Calendar.JULY, 15);

        SimpleDateFormat expectedFormat = new SimpleDateFormat(StdDateFormat.DATE_FORMAT_STR_ISO8601, new Locale("de"));
        expectedFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String expected = "{\"date\":\"" + expectedFormat.format(d) + "\"}";

        String json = mapper.writeValueAsString(new LocaleOnlyDateHolder(d));
        assertEquals(expected, json);
    }

    // hasTimeZone() alone: per-property timezone must override the mapper's global timezone.
    @Test
    public void testCreateContextual_TimeZoneOnly_OverridesGlobalTimeZone() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setLocale(Locale.US);
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d = utcDate(2020, Calendar.JULY, 15);

        SimpleDateFormat expectedFormat = new SimpleDateFormat(StdDateFormat.DATE_FORMAT_STR_ISO8601, Locale.US);
        expectedFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        String expected = "{\"date\":\"" + expectedFormat.format(d) + "\"}";

        String json = mapper.writeValueAsString(new TimezoneOnlyDateHolder(d));
        assertEquals(expected, json);
    }

    // deprecated isEmpty(value): null value counts as empty.
    @Test
    public void testIsEmptyDeprecated_NullValue_ReturnsTrue() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer();
        assertTrue(ser.isEmpty((Date) null));
    }

    // deprecated isEmpty(value): timestamp 0 counts as empty.
    @Test
    public void testIsEmptyDeprecated_ZeroTimestamp_ReturnsTrue() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer();
        assertTrue(ser.isEmpty(new Date(0L)));
    }

    // deprecated isEmpty(value): non-zero timestamp is not empty.
    @Test
    public void testIsEmptyDeprecated_NonZeroTimestamp_ReturnsFalse() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer();
        assertFalse(ser.isEmpty(new Date(42L)));
    }

    // isEmpty(provider, value): null value counts as empty.
    @Test
    public void testIsEmptyWithProvider_NullValue_ReturnsTrue() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertTrue(ser.isEmpty(provider, (Date) null));
    }

    // isEmpty(provider, value): timestamp 0 counts as empty.
    @Test
    public void testIsEmptyWithProvider_ZeroTimestamp_ReturnsTrue() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertTrue(ser.isEmpty(provider, new Date(0L)));
    }

    // isEmpty(provider, value): non-zero timestamp is not empty.
    @Test
    public void testIsEmptyWithProvider_NonZeroTimestamp_ReturnsFalse() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertFalse(ser.isEmpty(provider, new Date(42L)));
    }

    // getSchema: when asTimestamp is true, schema type must be "number".
    @Test
    public void testGetSchema_AsTimestampTrue_TypeIsNumber() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        JsonNode schema = ser.getSchema(provider, Date.class);
        assertEquals("number", schema.get("type").asText());
    }

    // getSchema: when asTimestamp is false, schema type must be "string".
    @Test
    public void testGetSchema_AsTimestampFalse_TypeIsString() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer(Boolean.FALSE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        JsonNode schema = ser.getSchema(provider, Date.class);
        assertEquals("string", schema.get("type").asText());
    }

    // Integration: no per-property format, default useTimestamp null falls back to provider feature.
    @Test
    public void testSerialize_NoFormatAnnotation_UsesProviderTimestampFeature() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        Date d = new Date(987654321L);
        String json = mapper.writeValueAsString(new DefaultDateHolder(d));
        assertEquals("{\"date\":987654321}", json);
    }

    // _useTimestamp == TRUE always wins, regardless of customFormat or provider settings.
    @Test
    public void testAsTimestamp_UseTimestampTrue_ReturnsTrue() throws Throwable {
        DateFormat fmt = new SimpleDateFormat("yyyy");
        TestDateSerializer ser = new TestDateSerializer(Boolean.TRUE, fmt);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertTrue(ser._asTimestamp(provider));
    }

    // _useTimestamp == FALSE always wins, regardless of provider settings.
    @Test
    public void testAsTimestamp_UseTimestampFalse_ReturnsFalse() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer(Boolean.FALSE, null);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertFalse(ser._asTimestamp(provider));
    }

    // _useTimestamp == null but customFormat set: must return false even if feature enabled.
    @Test
    public void testAsTimestamp_CustomFormatSet_ReturnsFalseEvenIfFeatureEnabled() throws Throwable {
        DateFormat fmt = new SimpleDateFormat("yyyy");
        TestDateSerializer ser = new TestDateSerializer(null, fmt);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertFalse(ser._asTimestamp(provider));
    }

    // _useTimestamp == null, customFormat == null, feature enabled: delegate to provider -> true.
    @Test
    public void testAsTimestamp_NullUseTimestampNullFormat_FeatureEnabled_ReturnsTrue() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer(null, null);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertTrue(ser._asTimestamp(provider));
    }

    // _useTimestamp == null, customFormat == null, feature disabled: delegate to provider -> false.
    @Test
    public void testAsTimestamp_NullUseTimestampNullFormat_FeatureDisabled_ReturnsFalse() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer(null, null);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        assertFalse(ser._asTimestamp(provider));
    }

    // Null SerializerProvider with null customFormat must throw IllegalArgumentException.
    @Test
    public void testAsTimestamp_NullSerializerAndNullFormat_ThrowsIllegalArgumentException() throws Throwable {
        TestDateSerializer ser = new TestDateSerializer(null, null);
        try {
            ser._asTimestamp(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("SerializerProvider"));
        }
    }
}
