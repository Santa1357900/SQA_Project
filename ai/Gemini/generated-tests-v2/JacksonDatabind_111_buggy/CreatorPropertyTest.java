package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.NullValueProvider;

public class CreatorPropertyTest {

    @Test
    public void testConstructionAndGetters() throws Throwable {
        PropertyName name = new PropertyName("testProp");
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        PropertyMetadata metadata = PropertyMetadata.STD_REQUIRED_OR_OPTIONAL;
        
        CreatorProperty prop = new CreatorProperty(
            name, type, null, null, null, null, 0, "injectId", metadata
        );
        
        assertEquals("testProp", prop.getName());
        assertEquals("injectId", prop.getInjectableValueId());
        assertEquals(0, prop.getCreatorIndex());
        assertFalse(prop.isIgnorable());
        assertNull(prop.getMember());
        assertNull(prop.getAnnotation(null));
        
        prop.markAsIgnorable();
        assertTrue(prop.isIgnorable());
        
        String str = prop.toString();
        assertNotNull(str);
        assertTrue(str.contains("testProp"));
        assertTrue(str.contains("injectId"));
    }

    @Test
    public void testWithName() throws Throwable {
        PropertyName name = new PropertyName("originalName");
        JavaType type = TypeFactory.defaultInstance().constructType(Integer.class);
        PropertyMetadata metadata = PropertyMetadata.STD_REQUIRED;
        
        CreatorProperty prop = new CreatorProperty(
            name, type, null, null, null, null, 1, null, metadata
        );
        
        PropertyName newName = new PropertyName("newName");
        CreatorProperty updated = (CreatorProperty) prop.withName(newName);
        
        assertEquals("newName", updated.getName());
        assertEquals(1, updated.getCreatorIndex());
        assertNull(updated.getInjectableValueId());
    }

    @Test
    public void testWithValueDeserializer() throws Throwable {
        PropertyName name = new PropertyName("prop");
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        PropertyMetadata metadata = PropertyMetadata.STD_REQUIRED;
        
        CreatorProperty prop = new CreatorProperty(
            name, type, null, null, null, null, 0, null, metadata
        );
        
        JsonDeserializer<Object> dummyDeser = new JsonDeserializer<Object>() {
            public Object deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt) throws IOException {
                return "value";
            }
        };
        
        CreatorProperty updated = (CreatorProperty) prop.withValueDeserializer(dummyDeser);
        assertNotNull(updated);
        
        // Same deserializer should return same instance
        CreatorProperty same = (CreatorProperty) updated.withValueDeserializer(dummyDeser);
        assertSame(updated, same);
    }

    @Test
    public void testWithNullProvider() throws Throwable {
        PropertyName name = new PropertyName("prop");
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        PropertyMetadata metadata = PropertyMetadata.STD_REQUIRED;
        
        CreatorProperty prop = new CreatorProperty(
            name, type, null, null, null, null, 0, null, metadata
        );
        
        NullValueProvider dummyNullProv = new NullValueProvider() {
            public Object getNullValue(com.fasterxml.jackson.databind.DeserializationContext ctxt) {
                return null;
            }
        };
        
        CreatorProperty updated = (CreatorProperty) prop.withNullProvider(dummyNullProv);
        assertNotNull(updated);
    }

    @Test
    public void testVerifySetterMissing() throws Throwable {
        PropertyName name = new PropertyName("missingSetterProp");
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        PropertyMetadata metadata = PropertyMetadata.STD_REQUIRED;
        
        CreatorProperty prop = new CreatorProperty(
            name, type, null, null, null, null, 0, null, metadata
        );
        
        boolean exceptionThrown = false;
        try {
            prop.set(new Object(), "val");
        } catch (com.fasterxml.jackson.databind.exc.InvalidDefinitionException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("No fallback setter/field defined"));
        } catch (Throwable t) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
}