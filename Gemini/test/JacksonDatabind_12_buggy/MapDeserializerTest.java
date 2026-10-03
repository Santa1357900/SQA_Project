package com.fasterxml.jackson.databind.deser.std;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class MapDeserializerTest {

    @Test
    public void testGettersAndBasicProperties() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType mapType = TypeFactory.defaultInstance().constructMapType(HashMap.class, String.class, Object.class);
        ValueInstantiator instantiator = new ValueInstantiator.Base(mapType) {
            @Override
            public boolean canCreateUsingDefault() {
                return true;
            }
            @Override
            public Object createUsingDefault(DeserializationContext ctxt) throws IOException {
                return new HashMap<Object, Object>();
            }
        };

        MapDeserializer deserializer = new MapDeserializer(mapType, instantiator, null, null, null);
        
        assertNotNull(deserializer.getValueType());
        assertNotNull(deserializer.getMapClass());
        assertNull(deserializer.getContentDeserializer());
        assertNull(deserializer.getContentType());
        assertTrue(deserializer.isCachable());

        MapDeserializer copy = new MapDeserializer(deserializer);
        assertNotNull(copy);

        deserializer.setIgnorableProperties(new String[]{"prop1", "prop2"});
        assertFalse(deserializer.isCachable());
    }

    @Test
    public void testSetIgnorablePropertiesEdgeCases() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType mapType = TypeFactory.defaultInstance().constructMapType(HashMap.class, String.class, Object.class);
        ValueInstantiator instantiator = new ValueInstantiator.Base(mapType) {
            @Override
            public boolean canCreateUsingDefault() {
                return true;
            }
            @Override
            public Object createUsingDefault(DeserializationContext ctxt) throws IOException {
                return new HashMap<Object, Object>();
            }
        };

        MapDeserializer deserializer = new MapDeserializer(mapType, instantiator, null, null, null);
        deserializer.setIgnorableProperties(null);
        deserializer.setIgnorableProperties(new String[0]);
    }

    @Test
    public void testResolveWithInvalidDelegate() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType mapType = TypeFactory.defaultInstance().constructMapType(HashMap.class, String.class, Object.class);
        ValueInstantiator instantiator = new ValueInstantiator.Base(mapType) {
            @Override
            public boolean canCreateUsingDelegate() {
                return true;
            }
            @Override
            public JavaType getDelegateType(DeserializationConfig config) {
                return null;
            }
        };

        MapDeserializer deserializer = new MapDeserializer(mapType, instantiator, null, null, null);
        DeserializationContext ctxt = mapper.getDeserializationContext();

        try {
            deserializer.resolve(ctxt);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid delegate-creator definition"));
        }
    }
}