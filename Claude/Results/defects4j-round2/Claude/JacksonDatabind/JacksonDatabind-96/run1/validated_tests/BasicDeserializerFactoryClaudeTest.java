package com.fasterxml.jackson.databind.deser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BasicDeserializerFactoryClaudeTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // ---- test POJOs ----

    public enum Color { RED, GREEN, BLUE }

    public enum Code {
        ONE(1), TWO(2);
        private final int code;
        Code(int code) { this.code = code; }
        @JsonCreator
        public static Code fromCode(int code) {
            for (Code c : values()) {
                if (c.code == code) {
                    return c;
                }
            }
            return null;
        }
    }

    public static class StringWrapper {
        private final String value;
        public StringWrapper(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    public static class IntWrapper {
        private final int value;
        public IntWrapper(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    public static class SingleNamedCreatorBean {
        private final String name;
        @JsonCreator
        public SingleNamedCreatorBean(@JsonProperty("name") String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static class DelegatingCreatorBean {
        private final String raw;
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public DelegatingCreatorBean(String raw) { this.raw = raw; }
        public String getRaw() { return raw; }
    }

    public static class PropsCreatorBean {
        private final String name;
        private final int age;
        @JsonCreator
        public PropsCreatorBean(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    public static class BadCreatorBean {
        private final String a;
        private final String b;
        @JsonCreator
        public BadCreatorBean(String a, String b) {
            this.a = a;
            this.b = b;
        }
        public String getA() { return a; }
        public String getB() { return b; }
    }

    public static class FactoryCreatorBean {
        private final String v;
        private FactoryCreatorBean(String v) { this.v = v; }
        @JsonCreator
        public static FactoryCreatorBean create(@JsonProperty("v") String v) {
            return new FactoryCreatorBean(v);
        }
        public String getV() { return v; }
    }

    public interface NoImplCollection extends Collection<String> { }
    public interface NoImplMap extends Map<String, String> { }

    // ---- tests: createArrayDeserializer ----

    // Branch: elemType.isPrimitive() -> PrimitiveArrayDeserializers
    @Test
    public void testCreateArrayDeserializer_primitiveIntArray() throws Throwable {
        int[] result = mapper.readValue("[1,2,3]", int[].class);
        assertArrayEquals(new int[] {1, 2, 3}, result);
    }

    // Branch: raw == String.class -> StringArrayDeserializer
    @Test
    public void testCreateArrayDeserializer_stringArray() throws Throwable {
        String[] result = mapper.readValue("[\"a\",\"b\"]", String[].class);
        assertArrayEquals(new String[] {"a", "b"}, result);
    }

    // Branch: fallback ObjectArrayDeserializer for boxed element type
    @Test
    public void testCreateArrayDeserializer_integerArray() throws Throwable {
        Integer[] result = mapper.readValue("[1,2,3]", Integer[].class);
        assertArrayEquals(new Integer[] {1, 2, 3}, result);
    }

    // ---- tests: createCollectionDeserializer ----

    // Branch: List interface -> fallback ArrayList
    @Test
    public void testCreateCollectionDeserializer_listInterfaceFallsBackToArrayList() throws Throwable {
        List<String> result = mapper.readValue("[\"a\",\"b\"]", new TypeReference<List<String>>() { });
        assertEquals(ArrayList.class, result.getClass());
        assertEquals(Arrays.asList("a", "b"), result);
    }

    // Branch: Set interface -> fallback HashSet
    @Test
    public void testCreateCollectionDeserializer_setInterfaceFallsBackToHashSet() throws Throwable {
        Set<String> result = mapper.readValue("[\"a\",\"b\"]", new TypeReference<Set<String>>() { });
        assertEquals(HashSet.class, result.getClass());
        assertTrue(result.contains("a") && result.contains("b"));
    }

    // Branch: SortedSet interface -> fallback TreeSet, verify natural sort order
    @Test
    public void testCreateCollectionDeserializer_sortedSetFallsBackToTreeSetSorted() throws Throwable {
        SortedSet<Integer> result = mapper.readValue("[3,1,2]", new TypeReference<SortedSet<Integer>>() { });
        assertEquals(TreeSet.class, result.getClass());
        assertEquals(Integer.valueOf(1), result.first());
    }

    // Branch: Queue interface -> fallback LinkedList
    @Test
    public void testCreateCollectionDeserializer_queueFallsBackToLinkedList() throws Throwable {
        Queue<String> result = mapper.readValue("[\"x\",\"y\"]", new TypeReference<Queue<String>>() { });
        assertEquals(LinkedList.class, result.getClass());
        assertEquals("x", result.peek());
    }

    // Branch: JDK1.6 "java.util.Deque" fallback -> LinkedList
    @Test
    public void testCreateCollectionDeserializer_dequeFallsBackToLinkedList() throws Throwable {
        Deque<String> result = mapper.readValue("[\"a\",\"b\"]", new TypeReference<Deque<String>>() { });
        assertEquals(LinkedList.class, result.getClass());
    }

    // Branch: JDK1.6 "java.util.NavigableSet" fallback -> TreeSet
    @Test
    public void testCreateCollectionDeserializer_navigableSetFallsBackToTreeSet() throws Throwable {
        NavigableSet<Integer> result = mapper.readValue("[3,1,2]", new TypeReference<NavigableSet<Integer>>() { });
        assertEquals(TreeSet.class, result.getClass());
    }

    // Branch: contentType.hasRawClass(String.class) -> StringCollectionDeserializer keeps order
    @Test
    public void testCreateCollectionDeserializer_stringContentPreservesOrder() throws Throwable {
        List<String> result = mapper.readValue("[\"z\",\"a\",\"m\"]", new TypeReference<List<String>>() { });
        assertEquals(Arrays.asList("z", "a", "m"), result);
    }



    // ---- tests: createMapDeserializer ----

    // Branch: Map interface -> fallback LinkedHashMap, insertion order preserved
    @Test
    public void testCreateMapDeserializer_mapInterfaceFallsBackToLinkedHashMap() throws Throwable {
        Map<String, Integer> result = mapper.readValue("{\"a\":1,\"b\":2}",
                new TypeReference<Map<String, Integer>>() { });
        assertEquals(LinkedHashMap.class, result.getClass());
        assertEquals(Integer.valueOf(1), result.get("a"));
    }

    // Branch: SortedMap interface -> fallback TreeMap, sorted keys
    @Test
    public void testCreateMapDeserializer_sortedMapFallsBackToTreeMapSorted() throws Throwable {
        SortedMap<String, Integer> result = mapper.readValue("{\"b\":2,\"a\":1}",
                new TypeReference<SortedMap<String, Integer>>() { });
        assertEquals(TreeMap.class, result.getClass());
        assertEquals("a", result.firstKey());
    }

    // Branch: ConcurrentMap interface -> fallback ConcurrentHashMap
    @Test
    public void testCreateMapDeserializer_concurrentMapFallsBackToConcurrentHashMap() throws Throwable {
        ConcurrentMap<String, Integer> result = mapper.readValue("{\"a\":1}",
                new TypeReference<ConcurrentMap<String, Integer>>() { });
        assertEquals(ConcurrentHashMap.class, result.getClass());
    }

    // Branch: EnumMap.class.isAssignableFrom(mapClass), mapClass == EnumMap.class -> inst=null
    @Test
    public void testCreateMapDeserializer_enumMapDeserializesCorrectly() throws Throwable {
        EnumMap<Color, String> result = mapper.readValue("{\"RED\":\"r\"}",
                new TypeReference<EnumMap<Color, String>>() { });
        assertEquals("r", result.get(Color.RED));
    }



    // ---- tests: createEnumDeserializer ----

    // Branch: plain EnumResolver based deserializer
    @Test
    public void testCreateEnumDeserializer_basicEnum() throws Throwable {
        Color result = mapper.readValue("\"GREEN\"", Color.class);
        assertEquals(Color.GREEN, result);
    }

    // Branch: static factory method annotated @JsonCreator used for enum
    @Test
    public void testCreateEnumDeserializer_withJsonCreatorFactoryMethod() throws Throwable {
        Code result = mapper.readValue("1", Code.class);
        assertEquals(Code.ONE, result);
    }

    // ---- tests: createReferenceDeserializer ----

    // Branch: AtomicReference referential type, rawType == AtomicReference.class -> inst=null
    @Test
    public void testCreateReferenceDeserializer_atomicReference() throws Throwable {
        AtomicReference<String> result = mapper.readValue("\"hello\"",
                new TypeReference<AtomicReference<String>>() { });
        assertEquals("hello", result.get());
    }

    // ---- tests: createTreeDeserializer ----

    // Branch: default path via JsonNodeDeserializer
    @Test
    public void testCreateTreeDeserializer_jsonNode() throws Throwable {
        JsonNode node = mapper.readValue("{\"a\":1}", JsonNode.class);
        assertTrue(node.isObject());
        assertEquals(1, node.get("a").asInt());
    }

    // ---- tests: findDefaultDeserializer ----

    // Branch: rawType == CLASS_MAP_ENTRY -> MapEntryDeserializer
    @Test
    public void testFindDefaultDeserializer_mapEntry() throws Throwable {
        Map.Entry<String, Integer> result = mapper.readValue("{\"a\":5}",
                new TypeReference<Map.Entry<String, Integer>>() { });
        assertEquals("a", result.getKey());
        assertEquals(Integer.valueOf(5), result.getValue());
    }

    // Branch: rawType == CLASS_ITERABLE -> upgraded to Collection deserializer
    @Test
    public void testFindDefaultDeserializer_iterableUpgradedToCollection() throws Throwable {
        Iterable<String> result = mapper.readValue("[\"p\",\"q\"]", new TypeReference<Iterable<String>>() { });
        List<String> asList = new ArrayList<String>();
        for (String s : result) {
            asList.add(s);
        }
        assertEquals(Arrays.asList("p", "q"), asList);
    }

    // Branch: rawType == CLASS_OBJECT -> UntypedObjectDeserializer, JSON object becomes Map
    @Test
    public void testFindDefaultDeserializer_untypedObjectReturnsMap() throws Throwable {
        Object result = mapper.readValue("{\"x\":1}", Object.class);
        assertTrue(result instanceof Map);
        assertEquals(Integer.valueOf(1), ((Map<?, ?>) result).get("x"));
    }

    // ---- tests: createKeyDeserializer ----

    // Branch: type.isEnumType() -> _createEnumKeyDeserializer
    @Test
    public void testCreateKeyDeserializer_enumKey() throws Throwable {
        Map<Color, String> result = mapper.readValue("{\"RED\":\"r\"}",
                new TypeReference<Map<Color, String>>() { });
        assertEquals("r", result.get(Color.RED));
    }

    // Branch: StdKeyDeserializers.findStringBasedKeyDeserializer for Integer key
    @Test
    public void testCreateKeyDeserializer_integerKey() throws Throwable {
        Map<Integer, String> result = mapper.readValue("{\"1\":\"one\",\"2\":\"two\"}",
                new TypeReference<Map<Integer, String>>() { });
        assertEquals("one", result.get(Integer.valueOf(1)));
    }

    // ---- tests: creator introspection (_addDeserializerConstructors / _addExplicitAnyCreator) ----

    // Branch: _handleSingleArgumentCreator, String type, implicit delegating creator via visibility
    @Test
    public void testConstructor_singleStringArgAutoDetectedAsDelegating() throws Throwable {
        StringWrapper result = mapper.readValue("\"hello\"", StringWrapper.class);
        assertEquals("hello", result.getValue());
    }

    // Branch: _handleSingleArgumentCreator, int type, implicit delegating creator
    @Test
    public void testConstructor_singleIntArgAutoDetectedAsDelegating() throws Throwable {
        IntWrapper result = mapper.readValue("42", IntWrapper.class);
        assertEquals(42, result.getValue());
    }

    // Branch: single-arg creator with explicit @JsonProperty name -> property based (JSON object)
    @Test
    public void testConstructor_singleArgExplicitNamePropertyBased() throws Throwable {
        SingleNamedCreatorBean result = mapper.readValue("{\"name\":\"Bob\"}", SingleNamedCreatorBean.class);
        assertEquals("Bob", result.getName());
    }

    // Branch: explicit DELEGATING mode single-arg creator
    @Test
    public void testConstructor_explicitDelegatingModeSingleArg() throws Throwable {
        DelegatingCreatorBean result = mapper.readValue("\"xyz\"", DelegatingCreatorBean.class);
        assertEquals("xyz", result.getRaw());
    }

    // Branch: multi-arg constructor, all params explicitly named -> property based creator
    @Test
    public void testConstructor_multiArgAllExplicitlyNamed() throws Throwable {
        PropsCreatorBean result = mapper.readValue("{\"name\":\"Tom\",\"age\":30}", PropsCreatorBean.class);
        assertEquals("Tom", result.getName());
        assertEquals(30, result.getAge());
    }

    // Branch: multi-arg @JsonCreator constructor with no property names -> reportBadTypeDefinition throws
    @Test
    public void testConstructor_multiArgWithoutNamesThrowsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("{\"a\":\"1\",\"b\":\"2\"}", BadCreatorBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().contains("property name"));
        }
    }

    // Branch: static factory method with @JsonCreator and explicit property name
    @Test
    public void testFactoryMethodCreator_explicitPropertyName() throws Throwable {
        FactoryCreatorBean result = mapper.readValue("{\"v\":\"hi\"}", FactoryCreatorBean.class);
        assertEquals("hi", result.getV());
    }
}
