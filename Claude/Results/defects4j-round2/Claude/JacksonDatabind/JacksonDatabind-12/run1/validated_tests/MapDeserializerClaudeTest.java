package com.fasterxml.jackson.databind.deser.std;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

public class MapDeserializerClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable
    {
        mapper = new ObjectMapper();
    }

    // Simple POJO used as Map value type
    public static class SimplePojo
    {
        private String name;
        private int value;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    // Wrapper with a single ignored key on the Map-valued property
    public static class MapHolder
    {
        @JsonIgnoreProperties({"secret"})
        private Map<String,String> data;
        public Map<String,String> getData() { return data; }
        public void setData(Map<String,String> data) { this.data = data; }
    }

    // Wrapper with multiple ignored keys
    public static class MapHolderMultiIgnore
    {
        @JsonIgnoreProperties({"secret", "hidden"})
        private Map<String,String> data;
        public Map<String,String> getData() { return data; }
        public void setData(Map<String,String> data) { this.data = data; }
    }

    // Wrapper without any ignore annotation, used as control
    public static class MapHolderNoIgnore
    {
        private Map<String,String> data;
        public Map<String,String> getData() { return data; }
        public void setData(Map<String,String> data) { this.data = data; }
    }

    // Concrete Map without a default (no-arg) constructor and no creator
    public static class NoDefaultCtorMap extends AbstractMap<String,String>
    {
        private final String a;
        private final String b;
        public NoDefaultCtorMap(String a, String b) { this.a = a; this.b = b; }
        @Override
        public Set<Map.Entry<String,String>> entrySet() {
            return new LinkedHashMap<String,String>().entrySet();
        }
    }

    // Covers START_OBJECT handling + standard string key branch + basic put
    @Test
    public void testDeserialize_simpleStringMap_returnsPopulatedMap() throws Throwable {
        Map<String,String> result = mapper.readValue("{\"a\":\"1\",\"b\":\"2\"}",
                new TypeReference<Map<String,String>>() {});
        assertEquals("1", result.get("a"));
        assertEquals("2", result.get("b"));
    }

    // Covers loop-zero-iterations: FIELD_NAME loop never runs, empty object
    @Test
    public void testDeserialize_emptyObject_returnsEmptyMap() throws Throwable {
        Map<String,String> result = mapper.readValue("{}",
                new TypeReference<Map<String,String>>() {});
        assertTrue(result.isEmpty());
    }

    // Covers multiple loop iterations
    @Test
    public void testDeserialize_multipleEntries_allPresent() throws Throwable {
        Map<String,Integer> result = mapper.readValue("{\"a\":1,\"b\":2,\"c\":3}",
                new TypeReference<Map<String,Integer>>() {});
        assertEquals(3, result.size());
        assertEquals(Integer.valueOf(3), result.get("c"));
    }

    // Covers duplicate key handling: last value wins (standard Map.put semantics)
    @Test
    public void testDeserialize_duplicateKeys_keepsLastValue() throws Throwable {
        Map<String,Integer> result = mapper.readValue("{\"a\":1,\"a\":2}",
                new TypeReference<Map<String,Integer>>() {});
        assertEquals(1, result.size());
        assertEquals(Integer.valueOf(2), result.get("a"));
    }

    // Covers VALUE_NULL branch: value comes from valueDes.getNullValue()
    @Test
    public void testDeserialize_nullValue_mapsToNullValue() throws Throwable {
        Map<String,String> result = mapper.readValue("{\"a\":null}",
                new TypeReference<Map<String,String>>() {});
        assertTrue(result.containsKey("a"));
        assertNull(result.get("a"));
    }

    // Covers non-standard key deserializer branch (_readAndBind, Integer keys)
    @Test
    public void testDeserialize_integerKeyMap_usesKeyDeserializer() throws Throwable {
        Map<Integer,String> result = mapper.readValue("{\"1\":\"one\",\"2\":\"two\"}",
                new TypeReference<Map<Integer,String>>() {});
        assertEquals("one", result.get(Integer.valueOf(1)));
        assertEquals("two", result.get(Integer.valueOf(2)));
    }

    // Covers Object key type branch treated as standard string key
    @Test
    public void testDeserialize_objectKeyType_usesStandardKeyBranch() throws Throwable {
        Map<Object,Object> result = mapper.readValue("{\"a\":\"b\"}",
                new TypeReference<Map<Object,Object>>() {});
        assertEquals("b", result.get("a"));
    }

    // Covers value deserialization delegating into a nested Map
    @Test
    public void testDeserialize_nestedMap_correctStructure() throws Throwable {
        Map<String,Map<String,Integer>> result = mapper.readValue(
                "{\"outer\":{\"inner\":5}}",
                new TypeReference<Map<String,Map<String,Integer>>>() {});
        assertEquals(Integer.valueOf(5), result.get("outer").get("inner"));
    }

    // Covers value deserialization delegating into a List value type
    @Test
    public void testDeserialize_mapOfLists_correctStructure() throws Throwable {
        Map<String,List<Integer>> result = mapper.readValue(
                "{\"nums\":[1,2,3]}",
                new TypeReference<Map<String,List<Integer>>>() {});
        List<Integer> nums = result.get("nums");
        assertEquals(3, nums.size());
        assertEquals(Integer.valueOf(2), nums.get(1));
    }

    // Covers boolean value deserialization
    @Test
    public void testDeserialize_booleanValues_correct() throws Throwable {
        Map<String,Boolean> result = mapper.readValue("{\"a\":true,\"b\":false}",
                new TypeReference<Map<String,Boolean>>() {});
        assertTrue(result.get("a").booleanValue());
        assertFalse(result.get("b").booleanValue());
    }

    // Covers empty-string field name key edge case
    @Test
    public void testDeserialize_emptyStringKey_allowed() throws Throwable {
        Map<String,String> result = mapper.readValue("{\"\":\"empty\"}",
                new TypeReference<Map<String,String>>() {});
        assertEquals("empty", result.get(""));
    }

    // Covers unicode/escaped characters in key and value
    @Test
    public void testDeserialize_unicodeKeyAndValue_correct() throws Throwable {
        Map<String,String> result = mapper.readValue("{\"k\\u00e9y\":\"v\\u00e9lue\"}",
                new TypeReference<Map<String,String>>() {});
        assertEquals("v\u00e9lue", result.get("k\u00e9y"));
    }

    // Covers key with special punctuation characters
    @Test
    public void testDeserialize_keyWithSpecialCharacters_correct() throws Throwable {
        Map<String,String> result = mapper.readValue("{\"a.b-c\":\"val\"}",
                new TypeReference<Map<String,String>>() {});
        assertEquals("val", result.get("a.b-c"));
    }

    // Covers throw for non-object, non-string token (JSON array input)
    @Test
    public void testDeserialize_arrayInput_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("[1,2,3]", new TypeReference<Map<String,Integer>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected: current token is not START_OBJECT/FIELD_NAME/END_OBJECT/VALUE_STRING
        }
    }

    // Covers throw for non-object, non-string token (JSON number input)
    @Test
    public void testDeserialize_numberInput_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("123", new TypeReference<Map<String,Integer>>() {});
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // Covers _ignorableProperties branch: single ignored key excluded from result
    @Test
    public void testDeserialize_ignorablePropertiesOnField_excludesIgnoredKeys() throws Throwable {
        MapHolder holder = mapper.readValue("{\"data\":{\"a\":\"1\",\"secret\":\"x\"}}",
                MapHolder.class);
        assertFalse(holder.getData().containsKey("secret"));
        assertEquals("1", holder.getData().get("a"));
    }

    // Covers _ignorableProperties branch with multiple ignored keys, still keeps skipChildren for array/obj value
    @Test
    public void testDeserialize_ignorablePropertiesMultiple_excludesAll() throws Throwable {
        MapHolderMultiIgnore holder = mapper.readValue(
                "{\"data\":{\"a\":\"1\",\"secret\":\"x\",\"hidden\":\"y\"}}",
                MapHolderMultiIgnore.class);
        assertEquals(1, holder.getData().size());
        assertTrue(holder.getData().containsKey("a"));
    }

    // Control test: without ignore annotation, all keys are retained
    @Test
    public void testDeserialize_noIgnorableProperties_includesAllKeys() throws Throwable {
        MapHolderNoIgnore holder = mapper.readValue(
                "{\"data\":{\"a\":\"1\",\"secret\":\"x\"}}", MapHolderNoIgnore.class);
        assertEquals(2, holder.getData().size());
        assertEquals("x", holder.getData().get("secret"));
    }

    // Covers !_hasDefaultCreator branch: throws instantiationException
    @Test
    public void testDeserialize_noDefaultConstructor_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("{}", NoDefaultCtorMap.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected: no default constructor found for target Map type
        }
    }

    // Covers deserialize(jp, ctxt, result) merge/update variant: adds new key, keeps existing
    @Test
    public void testDeserializeForUpdating_mergesIntoExistingMap() throws Throwable {
        Map<String,String> existing = new LinkedHashMap<String,String>();
        existing.put("x", "y");
        ObjectReader reader = mapper.readerForUpdating(existing);
        Map<String,String> result = (Map<String,String>) reader.readValue("{\"a\":\"b\"}");
        assertEquals("y", result.get("x"));
        assertEquals("b", result.get("a"));
    }

    // Covers deserialize(jp, ctxt, result) overwriting an existing key's value
    @Test
    public void testDeserializeForUpdating_overwritesExistingKey() throws Throwable {
        Map<String,String> existing = new LinkedHashMap<String,String>();
        existing.put("x", "old");
        ObjectReader reader = mapper.readerForUpdating(existing);
        Map<String,String> result = (Map<String,String>) reader.readValue("{\"x\":\"new\"}");
        assertEquals("new", result.get("x"));
    }

    // Covers different concrete Map type (TreeMap) with default constructor, sorted order
    @Test
    public void testDeserialize_treeMapType_sortedOrder() throws Throwable {
        TreeMap<String,String> result = mapper.readValue("{\"b\":\"2\",\"a\":\"1\"}",
                new TypeReference<TreeMap<String,String>>() {});
        Iterator<String> it = result.keySet().iterator();
        assertEquals("a", it.next());
        assertEquals("b", it.next());
    }

    // Covers LinkedHashMap concrete type preserving insertion order
    @Test
    public void testDeserialize_linkedHashMapPreservesInsertionOrder() throws Throwable {
        LinkedHashMap<String,Integer> result = mapper.readValue(
                "{\"z\":1,\"a\":2,\"m\":3}",
                new TypeReference<LinkedHashMap<String,Integer>>() {});
        Iterator<String> it = result.keySet().iterator();
        assertEquals("z", it.next());
        assertEquals("a", it.next());
        assertEquals("m", it.next());
    }

    // Covers large map with many loop iterations
    @Test
    public void testDeserialize_largeMap_allEntriesPresent() throws Throwable {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"k").append(i).append("\":").append(i);
        }
        sb.append("}");
        Map<String,Integer> result = mapper.readValue(sb.toString(),
                new TypeReference<Map<String,Integer>>() {});
        assertEquals(50, result.size());
        assertEquals(Integer.valueOf(49), result.get("k49"));
    }

    // Covers value deserialization into a POJO type
    @Test
    public void testDeserialize_valueAsNestedPojo_correct() throws Throwable {
        Map<String,SimplePojo> result = mapper.readValue(
                "{\"p\":{\"name\":\"bob\",\"value\":7}}",
                new TypeReference<Map<String,SimplePojo>>() {});
        assertEquals("bob", result.get("p").getName());
        assertEquals(7, result.get("p").getValue());
    }

    // Covers empty-string value preserved exactly
    @Test
    public void testDeserialize_emptyStringValue_preserved() throws Throwable {
        Map<String,String> result = mapper.readValue("{\"a\":\"\"}",
                new TypeReference<Map<String,String>>() {});
        assertEquals("", result.get("a"));
    }

    // Covers plain HashMap (raw target via List.class analog) with mixed numeric values
    @Test
    public void testDeserialize_hashMapWithNegativeAndZeroValues() throws Throwable {
        Map<String,Integer> result = mapper.readValue(
                "{\"neg\":-1,\"zero\":0,\"max\":2147483647}",
                new TypeReference<Map<String,Integer>>() {});
        assertEquals(Integer.valueOf(-1), result.get("neg"));
        assertEquals(Integer.valueOf(0), result.get("zero"));
        assertEquals(Integer.valueOf(2147483647), result.get("max"));
    }

    // Sanity check that returned instance is a real, mutable HashMap-family object
    @Test
    public void testDeserialize_returnsActualHashMapInstance() throws Throwable {
        Map<String,String> result = mapper.readValue("{\"a\":\"1\"}",
                new TypeReference<HashMap<String,String>>() {});
        assertTrue(result instanceof HashMap);
        result.put("extra", "2");
        assertEquals(2, result.size());
    }
}
