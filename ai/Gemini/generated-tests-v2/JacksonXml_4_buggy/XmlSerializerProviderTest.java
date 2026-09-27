package com.fasterxml.jackson.dataformat.xml.ser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.util.XmlRootNameLookup;

public class XmlSerializerProviderTest {

    @Test
    public void testConstructorsAndCreateInstance() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider1 = new XmlSerializerProvider(lookup);
        assertNotNull(provider1);

        SerializationConfig config = provider1.getConfig();
        SerializerFactory factory = provider1.getSerializerFactory();

        XmlSerializerProvider provider2 = new XmlSerializerProvider(provider1, config, factory);
        assertNotNull(provider2);

        DefaultSerializerProvider instance = provider1.createInstance(config, factory);
        assertNotNull(instance);
        assertTrue(instance instanceof XmlSerializerProvider);
    }

    @Test
    public void testSerializeValueNull() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        XmlMapper mapper = new XmlMapper();
        StringWriter sw = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = mapper.getFactory().createGenerator(sw);

        try {
            provider.serializeValue(gen, null);
        } finally {
            gen.close();
        }
    }

    @Test
    public void testSerializeValueWithTypeNull() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        XmlMapper mapper = new XmlMapper();
        StringWriter sw = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = mapper.getFactory().createGenerator(sw);
        JavaType type = mapper.constructType(String.class);

        try {
            provider.serializeValue(gen, null, type);
        } finally {
            gen.close();
        }
    }

    @Test
    public void testSerializeValueWithTypeAndSerializerNull() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        XmlMapper mapper = new XmlMapper();
        StringWriter sw = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = mapper.getFactory().createGenerator(sw);
        JavaType type = mapper.constructType(String.class);
        JsonSerializer<Object> ser = null;

        try {
            provider.serializeValue(gen, null, type, ser);
        } finally {
            gen.close();
        }
    }

    @Test
    public void testSerializeValueWithTokenBuffer() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        ObjectMapper mapper = new ObjectMapper();
        TokenBuffer tb = new TokenBuffer(mapper, false);

        try {
            provider.serializeValue(tb, "testValue");
        } finally {
            tb.close();
        }
    }

    @Test
    public void testSerializeValueWithInvalidGenerator() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        ObjectMapper mapper = new ObjectMapper();
        java.io.StringWriter sw = new java.io.StringWriter();
        com.fasterxml.jackson.core.JsonGenerator invalidGen = mapper.getFactory().createGenerator(sw);

        boolean caught = false;
        try {
            provider.serializeValue(invalidGen, "testValue");
        } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
            caught = true;
            assertTrue(e.getMessage().contains("XmlMapper does not with generators of type other than ToXmlGenerator"));
        } finally {
            invalidGen.close();
        }
        assertTrue(caught);
    }

    @Test
    public void testSerializeIndexedType() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        XmlMapper mapper = new XmlMapper();
        StringWriter sw = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = mapper.getFactory().createGenerator(sw);

        List<String> list = new ArrayList<String>();
        list.add("item1");

        try {
            provider.serializeValue(gen, list);
        } finally {
            gen.close();
        }
    }

    @Test
    public void testSerializeValueWithRootType() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        XmlMapper mapper = new XmlMapper();
        StringWriter sw = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = mapper.getFactory().createGenerator(sw);
        JavaType type = mapper.constructType(String.class);

        try {
            provider.serializeValue(gen, "hello", type);
        } finally {
            gen.close();
        }
    }

    @Test
    public void testSerializeValueWithRootTypeAndSerializer() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);

        XmlMapper mapper = new XmlMapper();
        StringWriter sw = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator gen = mapper.getFactory().createGenerator(sw);
        JavaType type = mapper.constructType(String.class);
        JsonSerializer<Object> ser = provider.findTypedValueSerializer(type, true, null);

        try {
            provider.serializeValue(gen, "hello", type, ser);
        } finally {
            gen.close();
        }
    }
}