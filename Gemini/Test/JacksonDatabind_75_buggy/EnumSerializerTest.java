package com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.EnumValues;

import org.junit.Test;
import static org.junit.Assert.*;

public class EnumSerializerTest {

    private enum TestEnum {
        FIRST, SECOND, THIRD
    }

    @Test
    public void testConstructAndGetters() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        EnumValues values = EnumValues.constructFromName(config, TestEnum.class);
        
        EnumSerializer serializer = new EnumSerializer(values, Boolean.TRUE);
        assertEquals(values, serializer.getEnumValues());
        assertEquals(TestEnum.class, serializer.handledType());
    }

    @Test
    public void testDeprecatedConstructor() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        EnumValues values = EnumValues.constructFromName(config, TestEnum.class);
        
        EnumSerializer serializer = new EnumSerializer(values);
        assertEquals(values, serializer.getEnumValues());
    }

    @Test
    public void testConstructFactoryMethod() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = mapper.getSerializationConfig().introspect(mapper.constructType(TestEnum.class));
        JsonFormat.Value format = JsonFormat.Value.forShape(Shape.NUMBER);
        
        EnumSerializer serializer = EnumSerializer.construct(TestEnum.class, config, beanDesc, format);
        assertNotNull(serializer);
        assertNotNull(serializer.getEnumValues());
    }

    @Test
    public void testCreateContextualWithNullProperty() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        EnumValues values = EnumValues.constructFromName(config, TestEnum.class);
        EnumSerializer serializer = new EnumSerializer(values, Boolean.FALSE);
        
        SerializerProvider provider = mapper.getSerializerProviderInstance();
        JsonSerializer<?> contextual = serializer.createContextual(provider, null);
        assertEquals(serializer, contextual);
    }

    @Test
    public void testShapeWrittenUsingIndexValidations() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = mapper.getSerializationConfig().introspect(mapper.constructType(TestEnum.class));
        
        // Shape STRING -> Boolean.FALSE
        JsonFormat.Value formatString = JsonFormat.Value.forShape(Shape.STRING);
        EnumSerializer serString = EnumSerializer.construct(TestEnum.class, config, beanDesc, formatString);
        assertNotNull(serString);

        // Shape NUMBER / ARRAY -> Boolean.TRUE
        JsonFormat.Value formatNumber = JsonFormat.Value.forShape(Shape.NUMBER);
        EnumSerializer serNumber = EnumSerializer.construct(TestEnum.class, config, beanDesc, formatNumber);
        assertNotNull(serNumber);

        // Shape ANY / SCALAR -> null
        JsonFormat.Value formatAny = JsonFormat.Value.forShape(Shape.ANY);
        EnumSerializer serAny = EnumSerializer.construct(TestEnum.class, config, beanDesc, formatAny);
        assertNotNull(serAny);
    }

    @Test
    public void testUnsupportedShapeException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig config = mapper.getSerializationConfig();
        BeanDescription beanDesc = mapper.getSerializationConfig().introspect(mapper.constructType(TestEnum.class));
        
        // Shape OBJECT is unsupported for enums
        JsonFormat.Value formatObject = JsonFormat.Value.forShape(Shape.OBJECT);
        boolean exceptionThrown = false;
        try {
            EnumSerializer.construct(TestEnum.class, config, beanDesc, formatObject);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("Unsupported serialization shape"));
        }
        assertTrue(exceptionThrown);
    }
}