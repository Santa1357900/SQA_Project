package com.fasterxml.jackson.databind.deser.impl;

import java.util.*;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.deser.std.StdDelegatingDeserializer;
import com.fasterxml.jackson.databind.util.Converter;

public class JavaUtilCollectionsDeserializersTest {

    @Test
    public void testFindForCollectionArraysList() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        List<String> list = Arrays.asList("a", "b");
        JavaType type = mapper.constructType(list.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForCollection(ctxt, type);
        assertNotNull(deser);
        assertTrue(deser instanceof StdDelegatingDeserializer);
    }

    @Test
    public void testFindForCollectionSingletonList() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        List<String> list = Collections.singletonList("test");
        JavaType type = mapper.constructType(list.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForCollection(ctxt, type);
        assertNotNull(deser);
    }

    @Test
    public void testFindForCollectionSingletonSet() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Set<String> set = Collections.singleton("test");
        JavaType type = mapper.constructType(set.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForCollection(ctxt, type);
        assertNotNull(deser);
    }

    @Test
    public void testFindForCollectionUnmodifiableList() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        List<String> list = Collections.unmodifiableList(new ArrayList<String>());
        JavaType type = mapper.constructType(list.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForCollection(ctxt, type);
        assertNotNull(deser);
    }

    @Test
    public void testFindForCollectionUnmodifiableSet() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Set<String> set = Collections.unmodifiableSet(new HashSet<String>());
        JavaType type = mapper.constructType(set.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForCollection(ctxt, type);
        assertNotNull(deser);
    }

    @Test
    public void testFindForCollectionUnknownReturnsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JavaType type = mapper.constructType(ArrayList.class);
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForCollection(ctxt, type);
        assertNull(deser);
    }

    @Test
    public void testFindForMapSingletonMap() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Map<String, String> map = Collections.singletonMap("key", "val");
        JavaType type = mapper.constructType(map.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForMap(ctxt, type);
        assertNotNull(deser);
    }

    @Test
    public void testFindForMapUnmodifiableMap() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Map<String, String> map = Collections.unmodifiableMap(new HashMap<String, String>());
        JavaType type = mapper.constructType(map.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForMap(ctxt, type);
        assertNotNull(deser);
    }

    @Test
    public void testFindForMapUnknownReturnsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        JavaType type = mapper.constructType(HashMap.class);
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForMap(ctxt, type);
        assertNull(deser);
    }

    @Test
    public void testConverterDirectExecution() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.constructType(List.class);
        
        List<String> sampleList = Arrays.asList("item1");
        JavaType concreteType = mapper.constructType(sampleList.getClass());
        
        JsonDeserializer<?> deser = JavaUtilCollectionsDeserializers.findForCollection(mapper.getDeserializationContext(), concreteType);
        assertNotNull(deser);
    }
}