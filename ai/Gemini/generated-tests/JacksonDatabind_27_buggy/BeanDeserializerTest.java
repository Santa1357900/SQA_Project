package com.fasterxml.jackson.databind.deser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.impl.ObjectIdReader;
import com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer;
import com.fasterxml.jackson.databind.deser.impl.BeanPropertyMap;
import com.fasterxml.jackson.databind.util.NameTransformer;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class BeanDeserializerTest {

    private static class DummyBeanDeserializerBuilder extends BeanDeserializerBuilder {
        public DummyBeanDeserializerBuilder(BeanDescription beanDesc, ObjectMapper mapper) {
            super(beanDesc, mapper.getDeserializationConfig());
        }
    }

    private BeanDeserializer createDummyBeanDeserializer() {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription beanDesc = mapper.getDeserializationConfig().introspectClassAnnotations(Object.class);
        BeanDeserializerBuilder builder = new DummyBeanDeserializerBuilder(beanDesc, mapper);
        BeanPropertyMap properties = BeanPropertyMap.construct(new ArrayList<SettableBeanProperty>());
        Map<String, SettableBeanProperty> backRefs = new HashMap<String, SettableBeanProperty>();
        HashSet<String> ignorableProps = new HashSet<String>();
        
        return new BeanDeserializer(
            builder, beanDesc, properties, backRefs,
            ignorableProps, false, false
        );
    }

    @Test
    public void testUnwrappingDeserializerNonStandard() throws Throwable {
        BeanDeserializer deserializer = createDummyBeanDeserializer();
        NameTransformer transformer = NameTransformer.simpleTransformer("prefix", "suffix");
        JsonDeserializer<Object> result = deserializer.unwrappingDeserializer(transformer);
        assertNotNull(result);
    }

    @Test
    public void testWithObjectIdReader() throws Throwable {
        BeanDeserializer deserializer = createDummyBeanDeserializer();
        ObjectIdReader oir = null;
        BeanDeserializer result = deserializer.withObjectIdReader(oir);
        assertNotNull(result);
        assertNotSame(deserializer, result);
    }

    @Test
    public void testWithIgnorableProperties() throws Throwable {
        BeanDeserializer deserializer = createDummyBeanDeserializer();
        HashSet<String> props = new HashSet<String>();
        props.add("testProp");
        BeanDeserializer result = deserializer.withIgnorableProperties(props);
        assertNotNull(result);
        assertNotSame(deserializer, result);
    }

    @Test
    public void testDeserializeMissingToken() throws Throwable {
        BeanDeserializer deserializer = createDummyBeanDeserializer();
        ObjectMapper mapper = new ObjectMapper();
        JsonParser p = mapper.getFactory().createParser("{}");
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        p.nextToken();
        
        try {
            deserializer._missingToken(p, ctxt);
            fail("Expected exception");
        } catch (IOException e) {
            assertTrue(e != null);
        }
    }
}