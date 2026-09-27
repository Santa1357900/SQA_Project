package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;

public class DateTimeSerializerBaseTest {

    static class ConcreteDateTimeSerializer extends DateTimeSerializerBase<Long> {
        public ConcreteDateTimeSerializer() {
            super(Long.class, null, null);
        }

        public ConcreteDateTimeSerializer(Boolean useTimestamp, DateFormat customFormat) {
            super(Long.class, useTimestamp, customFormat);
        }

        @Override
        public ConcreteDateTimeSerializer withFormat(Boolean timestamp, DateFormat customFormat) {
            return new ConcreteDateTimeSerializer(timestamp, customFormat);
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
            // No-op for testing
        }
    }

    @Test
    public void testIsEmptyWithNull() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer();
        assertTrue(serializer.isEmpty(null));
        
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        assertTrue(serializer.isEmpty(provider, null));
    }

    @Test
    public void testIsEmptyWithZeroTimestamp() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer();
        Long zero = Long.valueOf(0L);
        assertTrue(serializer.isEmpty(zero));
        
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        assertTrue(serializer.isEmpty(provider, zero));
    }

    @Test
    public void testIsEmptyWithNonZeroTimestamp() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer();
        Long nonZero = Long.valueOf(123456789L);
        assertFalse(serializer.isEmpty(nonZero));
        
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        assertFalse(serializer.isEmpty(provider, nonZero));
    }

    @Test
    public void testAsTimestampWithUseTimestampSet() throws Throwable {
        ConcreteDateTimeSerializer serializerTrue = new ConcreteDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        
        assertTrue(serializerTrue._asTimestamp(provider));

        ConcreteDateTimeSerializer serializerFalse = new ConcreteDateTimeSerializer(Boolean.FALSE, null);
        assertFalse(serializerFalse._asTimestamp(provider));
    }

    @Test
    public void testAsTimestampWithCustomFormat() throws Throwable {
        SimpleDateFormat customFormat = new SimpleDateFormat("yyyy-MM-dd");
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(null, customFormat);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        
        assertFalse(serializer._asTimestamp(provider));
    }

    @Test
    public void testAsTimestampWithSerializationFeature() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(null, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        assertTrue(serializer._asTimestamp(provider));

        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        assertFalse(serializer._asTimestamp(provider));
    }

    @Test
    public void testAsTimestampNullProviderThrowsException() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(null, null);
        try {
            serializer._asTimestamp(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null SerializerProvider passed"));
        }
    }

    @Test
    public void testGetSchema() throws Throwable {
        ConcreteDateTimeSerializer serializerTrue = new ConcreteDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        
        JsonNode schemaNumber = serializerTrue.getSchema(provider, null);
        assertNotNull(schemaNumber);

        ConcreteDateTimeSerializer serializerFalse = new ConcreteDateTimeSerializer(Boolean.FALSE, null);
        JsonNode schemaString = serializerFalse.getSchema(provider, null);
        assertNotNull(schemaString);
    }

    @Test
    public void testCreateContextualWithNullProperty() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        
        JsonSerializer<?> contextual = serializer.createContextual(provider, null);
        assertEquals(serializer, contextual);
    }
}