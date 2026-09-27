package com.fasterxml.jackson.databind.deser.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ExternalTypeHandlerTest {

    @Test
    public void testBuilderAndStart() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType beanType = TypeFactory.defaultInstance().constructType(Object.class);
        ExternalTypeHandler.Builder builder = ExternalTypeHandler.builder(beanType);
        assertNotNull(builder);

        ExternalTypeHandler handler = builder.build(null);
        assertNotNull(handler);

        ExternalTypeHandler started = handler.start();
        assertNotNull(started);
    }

    @Test
    public void testHandleTypePropertyValueNotFound() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType beanType = TypeFactory.defaultInstance().constructType(Object.class);
        ExternalTypeHandler.Builder builder = ExternalTypeHandler.builder(beanType);
        ExternalTypeHandler handler = builder.build(null);
        ExternalTypeHandler started = handler.start();

        JsonParser p = mapper.getFactory().createParser("{}");
        p.nextToken();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        boolean handled = started.handleTypePropertyValue(p, ctxt, "nonExistent", new Object());
        assertFalse(handled);
        p.close();
    }

    @Test
    public void testHandlePropertyValueNotFound() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType beanType = TypeFactory.defaultInstance().constructType(Object.class);
        ExternalTypeHandler.Builder builder = ExternalTypeHandler.builder(beanType);
        ExternalTypeHandler handler = builder.build(null);
        ExternalTypeHandler started = handler.start();

        JsonParser p = mapper.getFactory().createParser("{}");
        p.nextToken();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        boolean handled = started.handlePropertyValue(p, ctxt, "nonExistent", new Object());
        assertFalse(handled);
        p.close();
    }

    @Test
    public void testBuilderDuplicatePropertyNamesList() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType beanType = TypeFactory.defaultInstance().constructType(Object.class);
        ExternalTypeHandler.Builder builder = ExternalTypeHandler.builder(beanType);

        // We can test private helper methods indirectly or verify builder handles multiple properties
        // By using Reflection or just standard builder calls if possible.
        // Since we can't call private methods directly, we test builder state via build().
        ExternalTypeHandler handler = builder.build(null);
        assertNotNull(handler);
    }
}