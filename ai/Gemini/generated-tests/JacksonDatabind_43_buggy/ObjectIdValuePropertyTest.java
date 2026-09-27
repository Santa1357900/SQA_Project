package com.fasterxml.jackson.databind.deser.impl;

import junit.framework.TestCase;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import com.fasterxml.jackson.annotation.SimpleObjectIdResolver;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ObjectIdValuePropertyTest extends TestCase {

    private final ObjectMapper mapper = new ObjectMapper();

    public void testConstructorsAndWithMethods() throws Throwable {
        PropertyName propName = new PropertyName("idProp");
        JavaType idType = TypeFactory.defaultInstance().constructType(String.class);
        ObjectIdGenerator<?> generator = new ObjectIdGenerators.IntSequenceGenerator();
        ObjectIdResolver resolver = new SimpleObjectIdResolver();
        
        ObjectIdReader reader = ObjectIdReader.construct(
                idType, propName, generator, null, null, resolver);
        
        PropertyMetadata metadata = PropertyMetadata.STD_REQUIRED;
        ObjectIdValueProperty prop = new ObjectIdValueProperty(reader, metadata);
        
        assertNotNull(prop);
        assertNull(prop.getAnnotation(null));
        assertNull(prop.getMember());
        
        // Test withName
        PropertyName newName = new PropertyName("newIdProp");
        ObjectIdValueProperty renamed = prop.withName(newName);
        assertNotNull(renamed);
        assertEquals("newIdProp", renamed.getName());
        
        // Test withValueDeserializer
        JsonDeserializer<Object> dummyDeser = new JsonDeserializer<Object>() {
            @Override
            public Object deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
                return "testId";
            }
        };
        ObjectIdValueProperty withDeser = prop.withValueDeserializer(dummyDeser);
        assertNotNull(withDeser);
    }

    public void testSetAndReturnUnsupported() throws Throwable {
        PropertyName propName = new PropertyName("idProp");
        JavaType idType = TypeFactory.defaultInstance().constructType(String.class);
        ObjectIdGenerator<?> generator = new ObjectIdGenerators.IntSequenceGenerator();
        ObjectIdResolver resolver = new SimpleObjectIdResolver();
        
        // reader with idProperty = null
        ObjectIdReader reader = ObjectIdReader.construct(
                idType, propName, generator, null, null, resolver);
        
        PropertyMetadata metadata = PropertyMetadata.STD_OPTIONAL;
        ObjectIdValueProperty prop = new ObjectIdValueProperty(reader, metadata);
        
        Object instance = new Object();
        try {
            prop.setAndReturn(instance, "someValue");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Should not call set()"));
        }
        
        try {
            prop.set(instance, "someValue");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Should not call set()"));
        }
    }

    public void testDeserializeSetAndReturnNullId() throws Throwable {
        PropertyName propName = new PropertyName("idProp");
        JavaType idType = TypeFactory.defaultInstance().constructType(String.class);
        ObjectIdGenerator<?> generator = new ObjectIdGenerators.IntSequenceGenerator();
        ObjectIdResolver resolver = new SimpleObjectIdResolver();
        
        JsonDeserializer<Object> nullDeser = new JsonDeserializer<Object>() {
            @Override
            public Object deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
                return null;
            }
        };
        
        ObjectIdReader reader = ObjectIdReader.construct(
                idType, propName, generator, nullDeser, null, resolver);
        
        PropertyMetadata metadata = PropertyMetadata.STD_OPTIONAL;
        ObjectIdValueProperty prop = new ObjectIdValueProperty(reader, metadata);
        
        com.fasterxml.jackson.core.JsonParser p = mapper.createParser("\"test\"");
        DeserializationContext ctxt = mapper.getDeserializationContext();
        Object instance = new Object();
        
        Object result = prop.deserializeSetAndReturn(p, ctxt, instance);
        assertNull(result);
        
        p.close();
    }
}