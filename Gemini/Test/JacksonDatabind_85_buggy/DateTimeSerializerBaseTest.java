package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat;

public class DateTimeSerializerBaseTest {

    private static class DummyDateTimeSerializer extends DateTimeSerializerBase<Long> {
        public DummyDateTimeSerializer(Boolean useTimestamp, DateFormat customFormat) {
            super(Long.class, useTimestamp, customFormat);
        }

        @Override
        public DateTimeSerializerBase<Long> withFormat(Boolean timestamp, DateFormat customFormat) {
            return new DummyDateTimeSerializer(timestamp, customFormat);
        }

        @Override
        protected long _timestamp(Long value) {
            if (value == null) {
                return 0L;
            }
            return value.longValue();
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            // No-op for test
        }
    }

    private static class DummyJsonFormatVisitorWrapper implements JsonFormatVisitorWrapper {
        private SerializerProvider provider;

        public DummyJsonFormatVisitorWrapper(SerializerProvider provider) {
            this.provider = provider;
        }

        public SerializerProvider getProvider() {
            return provider;
        }

        public void setProvider(SerializerProvider provider) {
            this.provider = provider;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor expectStringFormat(JavaType type) {
            return null;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor expectNumberFormat(JavaType type) {
            return null;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) {
            return null;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) {
            return null;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(JavaType type) {
            return null;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(JavaType type) {
            return null;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
            return null;
        }

        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor expectMapFormat(JavaType type) {
            return null;
        }
    }

    @Test
    public void testCreateContextualNullProperty() throws Throwable {
        DummyDateTimeSerializer serializer = new DummyDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        JsonSerializer<?> result = serializer.createContextual(provider, null);
        assertSame(serializer, result);
    }

    @Test
    public void testIsEmptyValues() throws Throwable {
        DummyDateTimeSerializer serializer = new DummyDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        assertTrue(serializer.isEmpty(null));
        assertTrue(serializer.isEmpty(Long.valueOf(0L)));
        assertFalse(serializer.isEmpty(Long.valueOf(12345L)));

        assertTrue(serializer.isEmpty(provider, null));
        assertTrue(serializer.isEmpty(provider, Long.valueOf(0L)));
        assertFalse(serializer.isEmpty(provider, Long.valueOf(12345L)));
    }

    @Test
    public void testGetSchema() throws Throwable {
        DummyDateTimeSerializer timestampSerializer = new DummyDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        JsonNode schemaTimestamp = timestampSerializer.getSchema(provider, null);
        assertNotNull(schemaTimestamp);

        DummyDateTimeSerializer stringSerializer = new DummyDateTimeSerializer(Boolean.FALSE, null);
        JsonNode schemaString = stringSerializer.getSchema(provider, null);
        assertNotNull(schemaString);
    }

    @Test
    public void testAcceptJsonFormatVisitor() throws Throwable {
        DummyDateTimeSerializer serializer = new DummyDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        DummyJsonFormatVisitorWrapper visitor = new DummyJsonFormatVisitorWrapper(provider);

        serializer.acceptJsonFormatVisitor(visitor, mapper.constructType(Long.class));

        DummyDateTimeSerializer stringSerializer = new DummyDateTimeSerializer(Boolean.FALSE, null);
        stringSerializer.acceptJsonFormatVisitor(visitor, mapper.constructType(Long.class));
    }

    @Test
    public void testAsTimestampLogic() throws Throwable {
        // _useTimestamp is explicitly true
        DummyDateTimeSerializer s1 = new DummyDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        // Access via public schema method which uses _asTimestamp internally
        assertNotNull(s1.getSchema(provider, null));

        // _useTimestamp is explicitly false
        DummyDateTimeSerializer s2 = new DummyDateTimeSerializer(Boolean.FALSE, null);
        assertNotNull(s2.getSchema(provider, null));

        // _useTimestamp is null, customFormat is present -> returns false
        DummyDateTimeSerializer s3 = new DummyDateTimeSerializer(null, new SimpleDateFormat("yyyy-MM-dd"));
        assertNotNull(s3.getSchema(provider, null));

        // _useTimestamp is null, customFormat is null, serializers is null -> should throw IllegalArgumentException
        DummyDateTimeSerializer s4 = new DummyDateTimeSerializer(null, null);
        try {
            s4.getSchema(null, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null SerializerProvider passed"));
        }
    }
}