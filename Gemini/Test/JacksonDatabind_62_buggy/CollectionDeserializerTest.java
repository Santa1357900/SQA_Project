package com.fasterxml.jackson.databind.deser.std;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class CollectionDeserializerTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        JavaType collectionType = TypeFactory.defaultInstance().constructCollectionType(ArrayList.class, String.class);
        JsonDeserializer<Object> valueDeser = new StringDeserializer();
        ValueInstantiator instantiator = new ValueInstantiator.Base(collectionType);

        CollectionDeserializer deser = new CollectionDeserializer(collectionType, valueDeser, null, instantiator);
        assertEquals(String.class, deser.getContentType().getRawClass());
        assertEquals(valueDeser, deser.getContentDeserializer());
        assertTrue(deser.isCachable());

        CollectionDeserializer copy = new CollectionDeserializer(deser);
        assertEquals(String.class, copy.getContentType().getRawClass());

        CollectionDeserializer resolved = copy.withResolved(null, valueDeser, null, Boolean.TRUE);
        assertNotNull(resolved);
        
        CollectionDeserializer resolvedSame = copy.withResolved(null, valueDeser, null, Boolean.TRUE);
        assertSame(copy, resolvedSame);

        CollectionDeserializer resolvedDeprecated = copy.withResolved(null, valueDeser, null);
        assertNotNull(resolvedDeprecated);
    }

    @Test
    public void testIsCachableWithConditions() throws Throwable {
        JavaType collectionType = TypeFactory.defaultInstance().constructCollectionType(ArrayList.class, String.class);
        ValueInstantiator instantiator = new ValueInstantiator.Base(collectionType);

        CollectionDeserializer deserWithVal = new CollectionDeserializer(collectionType, new StringDeserializer(), null, instantiator);
        assertFalse(deserWithVal.isCachable());
    }

    @Test
    public void testCollectionReferringAccumulatorAdd() throws Throwable {
        List<Object> result = new ArrayList<Object>();
        CollectionDeserializer.CollectionReferringAccumulator accumulator = 
                new CollectionDeserializer.CollectionReferringAccumulator(String.class, result);

        accumulator.add("item1");
        assertEquals(1, result.size());
        assertEquals("item1", result.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolveForwardReferenceNotFound() throws Throwable {
        List<Object> result = new ArrayList<Object>();
        CollectionDeserializer.CollectionReferringAccumulator accumulator = 
                new CollectionDeserializer.CollectionReferringAccumulator(String.class, result);

        accumulator.resolveForwardReference("unknownId", "value");
    }

    @Test
    public void testCollectionReferringAccumulatorUnresolved() throws Throwable {
        List<Object> result = new ArrayList<Object>();
        CollectionDeserializer.CollectionReferringAccumulator accumulator = 
                new CollectionDeserializer.CollectionReferringAccumulator(String.class, result);

        JsonParser p = null;
        UnresolvedForwardReference ref = new UnresolvedForwardReference(p, "Unresolved", null, null);
        com.fasterxml.jackson.databind.deser.impl.ReadableObjectId.Referring referring = accumulator.handleUnresolvedReference(ref);
        assertNotNull(referring);
    }

    @Test
    public void testCreateContextualInvalidDelegate() throws Throwable {
        JavaType collectionType = TypeFactory.defaultInstance().constructCollectionType(ArrayList.class, String.class);
        ValueInstantiator instantiator = new ValueInstantiator.Base(collectionType) {
            @Override
            public boolean canCreateUsingDelegate() {
                return true;
            }
            @Override
            public JavaType getDelegateType(DeserializationConfig config) {
                return null;
            }
        };

        CollectionDeserializer deser = new CollectionDeserializer(collectionType, null, null, instantiator);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        try {
            deser.createContextual(ctxt, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid delegate-creator definition"));
        }
    }
}