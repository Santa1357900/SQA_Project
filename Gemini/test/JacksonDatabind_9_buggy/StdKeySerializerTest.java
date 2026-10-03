package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.util.Date;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;

public class StdKeySerializerTest {

    @Test
    public void testConstructorAndGetSchema() throws Throwable {
        StdKeySerializer serializer = new StdKeySerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();
        
        JsonNode schema = serializer.getSchema(provider, String.class);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("string", schema.get("type").asText());
    }

    @Test
    public void testAcceptJsonFormatVisitor() throws Throwable {
        StdKeySerializer serializer = new StdKeySerializer();
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.constructType(String.class);
        
        // Dummy implementation of JsonFormatVisitorWrapper using an anonymous inner class (Java 6 compatible)
        JsonFormatVisitorWrapper visitor = new JsonFormatVisitorWrapper() {
            private boolean visited = false;

            public SerializerProvider getProvider() { return null; }
            public void setProvider(SerializerProvider provider) {}

            public JsonStringFormatVisitor expectStringFormat(JavaType hint) {
                visited = true;
                return null;
            }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor expectArrayFormat(JavaType hint) { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor expectObjectFormat(JavaType hint) { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor expectNumberFormat(JavaType hint) { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor expectIntegerFormat(JavaType hint) { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor expectBooleanFormat(JavaType hint) { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor expectNullFormat(JavaType hint) { return null; }
            public com.fasterxml.jackson.databind.jsonFormatVisitors.JsonAnyFormatVisitor expectAnyFormat(JavaType hint) { return null; }
            
            public boolean isVisited() { return visited; }
        };

        serializer.acceptJsonFormatVisitor(visitor, type);
        assertTrue(((DummyVisitor) visitor).isVisited());
    }

    @Test
    public void testSerializeRegularObject() throws Throwable {
        StdKeySerializer serializer = new StdKeySerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        JsonFactory f = new JsonFactory();
        StringWriter sw = new StringWriter();
        JsonGenerator jgen = f.createGenerator(sw);

        serializer.serialize("test-key", jgen, provider);
        jgen.close();

        assertEquals("\"test-key\"", sw.toString());
    }

    @Test
    public void testSerializeDateObject() throws Throwable {
        StdKeySerializer serializer = new StdKeySerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        JsonFactory f = new JsonFactory();
        StringWriter sw = new StringWriter();
        JsonGenerator jgen = f.createGenerator(sw);

        Date date = new Date(0L); // Epoch time
        serializer.serialize(date, jgen, provider);
        jgen.close();

        // Date key serialization via provider should produce a string representation (e.g. timestamp or ISO string)
        assertNotNull(sw.toString());
        assertTrue(sw.toString().length() > 0);
    }

    @Test(expected = NullPointerException.class)
    public void testSerializeNullObject() throws Throwable {
        StdKeySerializer serializer = new StdKeySerializer();
        ObjectMapper mapper = new ObjectMapper();
        SerializerProvider provider = mapper.getSerializerProvider();

        JsonFactory f = new JsonFactory();
        StringWriter sw = new StringWriter();
        JsonGenerator jgen = f.createGenerator(sw);

        try {
            serializer.serialize(null, jgen, provider);
        } finally {
            jgen.close();
        }
    }

    // Helper interface / class to inspect visitor state safely without lambdas
    private interface DummyVisitor extends JsonFormatVisitorWrapper {
        boolean isVisited();
    }
}