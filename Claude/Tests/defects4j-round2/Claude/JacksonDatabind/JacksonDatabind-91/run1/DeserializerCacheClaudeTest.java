package com.fasterxml.jackson.databind.deser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapLikeType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class DeserializerCacheClaudeTest
{
    private DeserializerCache cache;
    private TypeFactory typeFactory;

    @Before
    public void setUp() throws Throwable
    {
        cache = new DeserializerCache();
        typeFactory = new ObjectMapper().getTypeFactory();
    }

    private JsonDeserializer<Object> newDummyDeserializer()
    {
        return new JsonDeserializer<Object>() {
            public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return null;
            }
        };
    }

    private KeyDeserializer newDummyKeyDeserializer()
    {
        return new KeyDeserializer() {
            public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
                return key;
            }
        };
    }

    // Covers: constructor initializes empty cache (cachedDeserializersCount branch: size()==0)
    @Test
    public void testConstructor_defaultState_cacheEmpty() throws Throwable {
        assertEquals(0, cache.cachedDeserializersCount());
    }

    // Covers: cachedDeserializersCount reflects size of _cachedDeserializers map
    @Test
    public void testCachedDeserializersCount_afterDirectPut_returnsCorrectSize() throws Throwable {
        JavaType t1 = typeFactory.constructType(String.class);
        JavaType t2 = typeFactory.constructType(Integer.class);
        cache._cachedDeserializers.put(t1, newDummyDeserializer());
        cache._cachedDeserializers.put(t2, newDummyDeserializer());
        assertEquals(2, cache.cachedDeserializersCount());
    }

    // Covers: flushCachedDeserializers clears non-empty map
    @Test
    public void testFlushCachedDeserializers_nonEmptyCache_clearsAll() throws Throwable {
        JavaType t = typeFactory.constructType(String.class);
        cache._cachedDeserializers.put(t, newDummyDeserializer());
        cache.flushCachedDeserializers();
        assertEquals(0, cache.cachedDeserializersCount());
    }

    // Covers: flushCachedDeserializers is safe no-op on empty map
    @Test
    public void testFlushCachedDeserializers_emptyCache_noOpNoException() throws Throwable {
        cache.flushCachedDeserializers();
        assertEquals(0, cache.cachedDeserializersCount());
    }

    // Covers: _findCachedDeserializer null-check branch throws IllegalArgumentException
    @Test
    public void testFindCachedDeserializer_nullType_throwsIllegalArgumentException() throws Throwable {
        try {
            cache._findCachedDeserializer(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Covers: type not present in cache returns null
    @Test
    public void testFindCachedDeserializer_simpleTypeNotCached_returnsNull() throws Throwable {
        JavaType t = typeFactory.constructType(String.class);
        assertNull(cache._findCachedDeserializer(t));
    }

    // Covers: type present in cache, no custom handlers -> returns cached instance
    @Test
    public void testFindCachedDeserializer_simpleTypeCached_returnsSameInstance() throws Throwable {
        JavaType t = typeFactory.constructType(String.class);
        JsonDeserializer<Object> d = newDummyDeserializer();
        cache._cachedDeserializers.put(t, d);
        assertSame(d, cache._findCachedDeserializer(t));
    }

    // Covers: primitive type not cached returns null
    @Test
    public void testFindCachedDeserializer_primitiveType_notCached_returnsNull() throws Throwable {
        JavaType t = typeFactory.constructType(int.class);
        assertNull(cache._findCachedDeserializer(t));
    }

    // Covers: Object.class type not cached returns null
    @Test
    public void testFindCachedDeserializer_objectType_notCached_returnsNull() throws Throwable {
        JavaType t = typeFactory.constructType(Object.class);
        assertNull(cache._findCachedDeserializer(t));
    }

    // Covers: container(array) type with content value handler -> _hasCustomHandlers true -> bypass cache, return null
    @Test
    public void testFindCachedDeserializer_arrayTypeWithContentValueHandler_returnsNullBypassingCache() throws Throwable {
        ArrayType arrType = typeFactory.constructArrayType(String.class);
        JavaType typeWithHandler = arrType.withContentValueHandler(newDummyDeserializer());
        cache._cachedDeserializers.put(typeWithHandler, newDummyDeserializer());
        assertNull(cache._findCachedDeserializer(typeWithHandler));
    }

    // Covers: container(array) type without content value handler -> normal cache lookup succeeds
    @Test
    public void testFindCachedDeserializer_arrayTypeWithoutContentValueHandler_returnsCachedInstance() throws Throwable {
        ArrayType arrType = typeFactory.constructArrayType(String.class);
        JsonDeserializer<Object> d = newDummyDeserializer();
        cache._cachedDeserializers.put(arrType, d);
        assertSame(d, cache._findCachedDeserializer(arrType));
    }

    // BUG-CATCHING: map type with custom KEY value handler must bypass cache per _hasCustomHandlers contract
    // (buggy version only checks content type, ignoring key type, so it wrongly returns the cached instance)
    @Test
    public void testFindCachedDeserializer_mapTypeWithKeyValueHandler_returnsNullBypassingCache() throws Throwable {
        MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, Integer.class);
        KeyDeserializer kd = newDummyKeyDeserializer();
        JavaType typeWithKeyHandler = ((MapLikeType) mapType).withKeyValueHandler(kd);
        cache._cachedDeserializers.put(typeWithKeyHandler, newDummyDeserializer());
        assertNull(cache._findCachedDeserializer(typeWithKeyHandler));
    }

    // Covers: map type with content(value) handler -> _hasCustomHandlers true -> bypass cache, return null
    @Test
    public void testFindCachedDeserializer_mapTypeWithContentValueHandler_returnsNullBypassingCache() throws Throwable {
        MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, Integer.class);
        JavaType typeWithContentHandler = mapType.withContentValueHandler(newDummyDeserializer());
        cache._cachedDeserializers.put(typeWithContentHandler, newDummyDeserializer());
        assertNull(cache._findCachedDeserializer(typeWithContentHandler));
    }

    // Covers: map type with both key and content value handlers -> still bypasses cache
    @Test
    public void testFindCachedDeserializer_mapTypeWithKeyAndContentHandlers_returnsNullBypassingCache() throws Throwable {
        MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, Integer.class);
        JavaType withContent = mapType.withContentValueHandler(newDummyDeserializer());
        JavaType withBoth = ((MapLikeType) withContent).withKeyValueHandler(newDummyKeyDeserializer());
        cache._cachedDeserializers.put(withBoth, newDummyDeserializer());
        assertNull(cache._findCachedDeserializer(withBoth));
    }

    // Covers: plain map type without any custom handlers -> normal cache lookup succeeds
    @Test
    public void testFindCachedDeserializer_mapTypeWithoutHandlers_returnsCachedInstance() throws Throwable {
        MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, Integer.class);
        JsonDeserializer<Object> d = newDummyDeserializer();
        cache._cachedDeserializers.put(mapType, d);
        assertSame(d, cache._findCachedDeserializer(mapType));
    }

    // Covers: collection type with content value handler -> bypass cache
    @Test
    public void testFindCachedDeserializer_collectionTypeWithContentValueHandler_returnsNullBypassingCache() throws Throwable {
        CollectionType collType = typeFactory.constructCollectionType(ArrayList.class, String.class);
        JavaType typeWithHandler = collType.withContentValueHandler(newDummyDeserializer());
        cache._cachedDeserializers.put(typeWithHandler, newDummyDeserializer());
        assertNull(cache._findCachedDeserializer(typeWithHandler));
    }

    // Covers: collection type without content value handler -> normal cache lookup succeeds
    @Test
    public void testFindCachedDeserializer_collectionTypeWithoutHandler_returnsCachedInstance() throws Throwable {
        CollectionType collType = typeFactory.constructCollectionType(ArrayList.class, String.class);
        JsonDeserializer<Object> d = newDummyDeserializer();
        cache._cachedDeserializers.put(collType, d);
        assertSame(d, cache._findCachedDeserializer(collType));
    }

    // Covers: separately constructed but equal JavaType instances still hit the same cache entry
    @Test
    public void testFindCachedDeserializer_equalTypeInstances_cacheHitByEquality() throws Throwable {
        JavaType t1 = typeFactory.constructType(String.class);
        JavaType t2 = typeFactory.constructType(String.class);
        JsonDeserializer<Object> d = newDummyDeserializer();
        cache._cachedDeserializers.put(t1, d);
        assertSame(d, cache._findCachedDeserializer(t2));
    }

    // Covers: writeReplace returns 'this' and clears _incompleteDeserializers map
    @Test
    public void testWriteReplace_returnsThisAndClearsIncompleteDeserializers() throws Throwable {
        JavaType t = typeFactory.constructType(String.class);
        cache._incompleteDeserializers.put(t, newDummyDeserializer());
        Object result = cache.writeReplace();
        assertSame(cache, result);
        assertTrue(cache._incompleteDeserializers.isEmpty());
    }

    // Covers: flush then re-populate reflects only the latest state in count
    @Test
    public void testCachedDeserializersCount_afterFlushThenAdd_reflectsLatestState() throws Throwable {
        cache._cachedDeserializers.put(typeFactory.constructType(String.class), newDummyDeserializer());
        cache.flushCachedDeserializers();
        cache._cachedDeserializers.put(typeFactory.constructType(Integer.class), newDummyDeserializer());
        assertEquals(1, cache.cachedDeserializersCount());
    }

    // Covers: multiple distinct container/simple types tracked correctly by cachedDeserializersCount
    @Test
    public void testCachedDeserializersCount_multipleDistinctTypes_reflectsExactCount() throws Throwable {
        cache._cachedDeserializers.put(typeFactory.constructType(String.class), newDummyDeserializer());
        cache._cachedDeserializers.put(typeFactory.constructMapType(HashMap.class, String.class, Integer.class), newDummyDeserializer());
        cache._cachedDeserializers.put(typeFactory.constructCollectionType(ArrayList.class, String.class), newDummyDeserializer());
        assertEquals(3, cache.cachedDeserializersCount());
    }
}
