package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;

public class DateTimeSerializerBaseTest {

    private static class ConcreteDateTimeSerializer extends DateTimeSerializerBase<Date> {
        public ConcreteDateTimeSerializer() {
            super(Date.class, null, null);
        }

        public ConcreteDateTimeSerializer(Boolean useTimestamp, DateFormat customFormat) {
            super(Date.class, useTimestamp, customFormat);
        }

        @Override
        public DateTimeSerializerBase<Date> withFormat(Boolean timestamp, DateFormat customFormat) {
            return new ConcreteDateTimeSerializer(timestamp, customFormat);
        }

        @Override
        protected long _timestamp(Date value) {
            if (value == null) {
                return 0L;
            }
            return value.getTime();
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

    private static class DummyJsonFormatVisitorWrapper implements JsonFormatVisitorWrapper {
        private SerializerProvider provider;

        public DummyJsonFormatVisitorWrapper(SerializerProvider provider) {
            this.provider = provider;
        }

        @Override
        public SerializerProvider getProvider() {
            return provider;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor expectMapFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor expectNumberFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor expectStringFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonAnyFormatVisitor expectAnyFormat(JavaType type) {
            return null;
        }
    }

    @Test
    public void testIsEmpty() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        boolean empty = serializer.isEmpty(provider, new Date());
        assertFalse(empty);
    }

    @Test
    public void testGetSchema() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        JsonNode schema = serializer.getSchema(provider, null);
        assertNotNull(schema);
    }

    @Test
    public void testAcceptJsonFormatVisitorAsTimestamp() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(Boolean.TRUE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        DummyJsonFormatVisitorWrapper visitor = new DummyJsonFormatVisitorWrapper(provider);
        serializer.acceptJsonFormatVisitor(visitor, mapper.constructType(Date.class));
    }

    @Test
    public void testAcceptJsonFormatVisitorAsString() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(Boolean.FALSE, null);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        DummyJsonFormatVisitorWrapper visitor = new DummyJsonFormatVisitorWrapper(provider);
        serializer.acceptJsonFormatVisitor(visitor, mapper.constructType(Date.class));
    }

    @Test
    public void testAsTimestampNullProviderException() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(null, null);
        boolean thrown = false;
        try {
            serializer._asTimestamp(null);
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Null SerializerProvider passed"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testContextualizerNullProperty() throws Throwable {
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        JsonSerializer<?> contextual = serializer.createContextual(provider, null);
        assertSame(serializer, contextual);
    }

    @Test
    public void testSerializeAsStringWithCustomFormatReuse() throws Throwable {
        SimpleDateFormat customFormat = new SimpleDateFormat("yyyy-MM-dd");
        ConcreteDateTimeSerializer serializer = new ConcreteDateTimeSerializer(Boolean.FALSE, customFormat);
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        java.io.StringWriter sw = new java.io.StringWriter();
        JsonGenerator gen = mapper.getFactory().createGenerator(sw);
        
        serializer._serializeAsString(new Date(0L), gen, provider);
        gen.flush();
        assertTrue(sw.toString().length() > 0);
        gen.close();
    }
}