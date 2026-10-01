package com.fasterxml.jackson.databind.deser;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.NavigableSet;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Deque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.LinkedList;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;

public class BasicDeserializerFactoryClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // --- Test POJOs ---

    public static class StringCtorBean {
        public final String value;
        @JsonCreator
        public StringCtorBean(String value) { this.value = value; }
    }

    public static class ImplicitStringBean {
        public final String value;
        public ImplicitStringBean(String value) { this.value = value; }
    }

    public static class IntCtorBean {
        public final int value;
        @JsonCreator
        public IntCtorBean(int value) { this.value = value; }
    }

    public static class LongCtorBean {
        public final long value;
        @JsonCreator
        public LongCtorBean(long value) { this.value = value; }
    }

    public static class DoubleCtorBean {
        public final double value;
        @JsonCreator
        public DoubleCtorBean(double value) { this.value = value; }
    }

    public static class BooleanCtorBean {
        public final boolean value;
        @JsonCreator
        public BooleanCtorBean(boolean value) { this.value = value; }
    }

    public static class PersonBean {
        private final String name;
        private final int age;
        @JsonCreator
        public PersonBean(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    public static class BadCtorBean {
        public final String a;
        public final String b;
        @JsonCreator
        public BadCtorBean(@JsonProperty("a") String a, String b) {
            this.a = a;
            this.b = b;
        }
    }

    public static class DelegatingBean {
        public final List<String> items;
        @JsonCreator
        public DelegatingBean(List<String> items) { this.items = items; }
    }

    public static enum Color { RED, GREEN, BLUE }

    public static enum Size {
        SMALL(1), LARGE(2);
        private final int code;
        private Size(int code) { this.code = code; }
        @JsonCreator
        public static Size fromCode(int code) {
            Size[] vals = Size.values();
            for (int i = 0; i < vals.length; i++) {
                if (vals[i].code == code) {
                    return vals[i];
                }
            }
            return null;
        }
    }

    public static class Point {
        public int x;
        public int y;
    }

    public interface MyCollection<E> extends Collection<E> { }

    public interface MyMap<K, V> extends Map<K, V> { }

    // --- Single-arg constructor creator branches ---

    // covers _handleSingleArgumentConstructor: type == String.class, isCreator=true
    @Test
    public void testStringCtorBean_JsonCreatorAnnotated_constructsFromString() throws Throwable {
        StringCtorBean bean = mapper.readValue("\"hello\"", StringCtorBean.class);
        assertEquals("hello", bean.value);
    }

    // covers _handleSingleArgumentConstructor: isVisible branch without @JsonCreator
    @Test
    public void testImplicitStringBean_NoAnnotationPublicVisible_constructsFromString() throws Throwable {
        ImplicitStringBean bean = mapper.readValue("\"world\"", ImplicitStringBean.class);
        assertEquals("world", bean.value);
    }

    // covers _handleSingleArgumentConstructor: type == int.class/Integer.class
    @Test
    public void testIntCtorBean_JsonCreatorAnnotated_constructsFromInt() throws Throwable {
        IntCtorBean bean = mapper.readValue("42", IntCtorBean.class);
        assertEquals(42, bean.value);
    }

    // covers _handleSingleArgumentConstructor: type == long.class/Long.class
    @Test
    public void testLongCtorBean_JsonCreatorAnnotated_constructsFromLong() throws Throwable {
        LongCtorBean bean = mapper.readValue("123456789012", LongCtorBean.class);
        assertEquals(123456789012L, bean.value);
    }

    // covers _handleSingleArgumentConstructor: type == double.class/Double.class
    @Test
    public void testDoubleCtorBean_JsonCreatorAnnotated_constructsFromDouble() throws Throwable {
        DoubleCtorBean bean = mapper.readValue("3.14", DoubleCtorBean.class);
        assertEquals(3.14, bean.value, 1e-9);
    }

    // covers _handleSingleArgumentConstructor: type == boolean.class/Boolean.class
    @Test
    public void testBooleanCtorBean_JsonCreatorAnnotated_constructsFromBoolean() throws Throwable {
        BooleanCtorBean bean = mapper.readValue("true", BooleanCtorBean.class);
        assertTrue(bean.value);
    }

    // covers _handleSingleArgumentConstructor: delegating creator fallback for non-standard type
    @Test
    public void testDelegatingBean_SingleArgListType_constructsFromArrayDelegate() throws Throwable {
        DelegatingBean bean = mapper.readValue("[\"a\",\"b\"]", DelegatingBean.class);
        assertEquals(2, bean.items.size());
        assertEquals("a", bean.items.get(0));
        assertEquals("b", bean.items.get(1));
    }

    // --- Multi-arg constructor creator branches ---

    // covers _addDeserializerConstructors: property-based multi-arg creator, constructCreatorProperty
    @Test
    public void testPersonBean_MultiArgJsonCreatorWithNames_constructsPropertyBased() throws Throwable {
        PersonBean p = mapper.readValue("{\"name\":\"Alice\",\"age\":30}", PersonBean.class);
        assertEquals("Alice", p.getName());
        assertEquals(30, p.getAge());
    }

    // covers _addDeserializerConstructors: epic-fail throw when a param has no name annotation
    @Test
    public void testBadCtorBean_MissingPropertyNameOnSecondArg_throwsIllegalArgument() throws Throwable {
        try {
            mapper.readValue("{\"a\":\"x\",\"b\":\"y\"}", BadCtorBean.class);
            fail("expected exception due to missing property name annotation");
        } catch (Exception e) {
            assertTrue(e instanceof JsonMappingException || e instanceof IllegalArgumentException);
        }
    }

    // --- Enum deserializer branches ---

    // covers createEnumDeserializer: default name-based path
    @Test
    public void testEnum_DefaultNameBased_returnsMatchingConstant() throws Throwable {
        Color c = mapper.readValue("\"RED\"", Color.class);
        assertEquals(Color.RED, c);
    }

    // covers createEnumDeserializer: @JsonCreator static factory method branch
    @Test
    public void testEnum_JsonCreatorFactoryMethod_returnsMatchingConstantByCode() throws Throwable {
        Size s = mapper.readValue("2", Size.class);
        assertEquals(Size.LARGE, s);
    }

    // --- Collection/Map special-case branches ---

    // covers createCollectionDeserializer: EnumSet.class.isAssignableFrom branch
    @Test
    public void testEnumSet_DeserializesGivenElements() throws Throwable {
        TypeReference<EnumSet<Color>> tr = new TypeReference<EnumSet<Color>>() { };
        EnumSet<Color> set = mapper.readValue("[\"RED\",\"BLUE\"]", tr);
        assertEquals(2, set.size());
        assertTrue(set.contains(Color.RED));
        assertTrue(set.contains(Color.BLUE));
        assertFalse(set.contains(Color.GREEN));
    }

    // covers createMapDeserializer: EnumMap.class.isAssignableFrom branch
    @Test
    public void testEnumMap_DeserializesGivenEntries() throws Throwable {
        TypeReference<EnumMap<Color, String>> tr = new TypeReference<EnumMap<Color, String>>() { };
        EnumMap<Color, String> map = mapper.readValue("{\"RED\":\"r\"}", tr);
        assertEquals("r", map.get(Color.RED));
    }

    // --- Map fallback branches (_mapFallbacks) ---

    // covers _mapFallbacks: Map.class -> LinkedHashMap
    @Test
    public void testMapInterface_FallsBackToLinkedHashMap() throws Throwable {
        TypeReference<Map<String, Integer>> tr = new TypeReference<Map<String, Integer>>() { };
        Map<String, Integer> map = mapper.readValue("{\"a\":1,\"b\":2}", tr);
        assertTrue(map instanceof LinkedHashMap);
        assertEquals(Integer.valueOf(1), map.get("a"));
    }

    // covers _mapFallbacks: ConcurrentMap.class -> ConcurrentHashMap
    @Test
    public void testConcurrentMapInterface_FallsBackToConcurrentHashMap() throws Throwable {
        TypeReference<ConcurrentMap<String, Integer>> tr = new TypeReference<ConcurrentMap<String, Integer>>() { };
        ConcurrentMap<String, Integer> map = mapper.readValue("{\"a\":1}", tr);
        assertTrue(map instanceof ConcurrentHashMap);
    }

    // covers _mapFallbacks: SortedMap.class -> TreeMap
    @Test
    public void testSortedMapInterface_FallsBackToTreeMap() throws Throwable {
        TypeReference<SortedMap<String, Integer>> tr = new TypeReference<SortedMap<String, Integer>>() { };
        SortedMap<String, Integer> map = mapper.readValue("{\"b\":2,\"a\":1}", tr);
        assertTrue(map instanceof TreeMap);
        assertEquals("a", map.firstKey());
    }

    // covers _mapFallbacks: java.util.NavigableMap -> TreeMap
    @Test
    public void testNavigableMapInterface_FallsBackToTreeMap() throws Throwable {
        TypeReference<NavigableMap<String, Integer>> tr = new TypeReference<NavigableMap<String, Integer>>() { };
        NavigableMap<String, Integer> map = mapper.readValue("{\"b\":2,\"a\":1}", tr);
        assertTrue(map instanceof TreeMap);
    }

    // covers _mapFallbacks: java.util.concurrent.ConcurrentNavigableMap -> ConcurrentSkipListMap
    @Test
    public void testConcurrentNavigableMapInterface_FallsBackToConcurrentSkipListMap() throws Throwable {
        TypeReference<ConcurrentNavigableMap<String, Integer>> tr =
                new TypeReference<ConcurrentNavigableMap<String, Integer>>() { };
        ConcurrentNavigableMap<String, Integer> map = mapper.readValue("{\"a\":1}", tr);
        assertTrue(map instanceof ConcurrentSkipListMap);
    }

    // --- Collection fallback branches (_collectionFallbacks) ---

    // covers _collectionFallbacks: List.class -> ArrayList
    @Test
    public void testListInterface_FallsBackToArrayList() throws Throwable {
        TypeReference<List<String>> tr = new TypeReference<List<String>>() { };
        List<String> list = mapper.readValue("[\"x\",\"y\"]", tr);
        assertTrue(list instanceof ArrayList);
        assertEquals(2, list.size());
    }

    // covers _collectionFallbacks: Set.class -> HashSet
    @Test
    public void testSetInterface_FallsBackToHashSet() throws Throwable {
        TypeReference<Set<String>> tr = new TypeReference<Set<String>>() { };
        Set<String> set = mapper.readValue("[\"x\",\"y\",\"x\"]", tr);
        assertTrue(set instanceof HashSet);
        assertEquals(2, set.size());
    }

    // covers _collectionFallbacks: SortedSet.class -> TreeSet
    @Test
    public void testSortedSetInterface_FallsBackToTreeSet() throws Throwable {
        TypeReference<SortedSet<Integer>> tr = new TypeReference<SortedSet<Integer>>() { };
        SortedSet<Integer> set = mapper.readValue("[3,1,2]", tr);
        assertTrue(set instanceof TreeSet);
        assertEquals(Integer.valueOf(1), set.first());
    }

    // covers _collectionFallbacks: "java.util.NavigableSet" -> TreeSet
    @Test
    public void testNavigableSetInterface_FallsBackToTreeSet() throws Throwable {
        TypeReference<NavigableSet<Integer>> tr = new TypeReference<NavigableSet<Integer>>() { };
        NavigableSet<Integer> set = mapper.readValue("[3,1,2]", tr);
        assertTrue(set instanceof TreeSet);
        assertEquals(Integer.valueOf(1), set.first());
    }

    // covers _collectionFallbacks: Queue.class -> LinkedList
    @Test
    public void testQueueInterface_FallsBackToLinkedList() throws Throwable {
        TypeReference<Queue<Integer>> tr = new TypeReference<Queue<Integer>>() { };
        Queue<Integer> q = mapper.readValue("[1,2,3]", tr);
        assertTrue(q instanceof LinkedList);
        assertEquals(Integer.valueOf(1), q.peek());
    }

    // covers _collectionFallbacks: "java.util.Deque" -> LinkedList
    @Test
    public void testDequeInterface_FallsBackToLinkedList() throws Throwable {
        TypeReference<Deque<Integer>> tr = new TypeReference<Deque<Integer>>() { };
        Deque<Integer> dq = mapper.readValue("[1,2,3]", tr);
        assertTrue(dq instanceof LinkedList);
    }

    // covers _collectionFallbacks: Collection.class -> ArrayList
    @Test
    public void testCollectionInterface_FallsBackToArrayList() throws Throwable {
        TypeReference<Collection<Integer>> tr = new TypeReference<Collection<Integer>>() { };
        Collection<Integer> c = mapper.readValue("[1,2]", tr);
        assertTrue(c instanceof ArrayList);
        assertEquals(2, c.size());
    }

    // --- findDefaultDeserializer special-case branches ---

    // covers findDefaultDeserializer: rawType == CLASS_ITERABLE upgrade to Collection
    @Test
    public void testIterableType_UpgradesToCollectionDeserializer() throws Throwable {
        TypeReference<Iterable<Integer>> tr = new TypeReference<Iterable<Integer>>() { };
        Iterable<Integer> it = mapper.readValue("[1,2,3]", tr);
        Iterator<Integer> iter = it.iterator();
        assertEquals(Integer.valueOf(1), iter.next());
        assertEquals(Integer.valueOf(2), iter.next());
        assertEquals(Integer.valueOf(3), iter.next());
    }

    // covers findDefaultDeserializer: rawType == CLASS_MAP_ENTRY branch
    @Test
    public void testMapEntryType_DeserializesKeyAndValue() throws Throwable {
        TypeReference<Map.Entry<String, Integer>> tr = new TypeReference<Map.Entry<String, Integer>>() { };
        Map.Entry<String, Integer> entry = mapper.readValue("{\"key\":5}", tr);
        assertEquals("key", entry.getKey());
        assertEquals(Integer.valueOf(5), entry.getValue());
    }

    // covers findDefaultDeserializer: isReferenceType / AtomicReference branch
    @Test
    public void testAtomicReference_DeserializesWrappedStringValue() throws Throwable {
        TypeReference<AtomicReference<String>> tr = new TypeReference<AtomicReference<String>>() { };
        AtomicReference<String> ref = mapper.readValue("\"hi\"", tr);
        assertEquals("hi", ref.get());
    }

    // --- Array deserializer branches ---

    // covers createArrayDeserializer: elemType.isPrimitive() branch
    @Test
    public void testPrimitiveIntArray_Deserializes() throws Throwable {
        int[] arr = mapper.readValue("[1,2,3]", int[].class);
        assertArrayEquals(new int[] {1, 2, 3}, arr);
    }

    // covers createArrayDeserializer: raw == String.class branch
    @Test
    public void testStringArray_Deserializes() throws Throwable {
        String[] arr = mapper.readValue("[\"a\",\"b\"]", String[].class);
        assertArrayEquals(new String[] {"a", "b"}, arr);
    }

    // covers createArrayDeserializer: generic ObjectArrayDeserializer branch
    @Test
    public void testObjectArrayOfPojos_Deserializes() throws Throwable {
        Point[] pts = mapper.readValue("[{\"x\":1,\"y\":2}]", Point[].class);
        assertEquals(1, pts.length);
        assertEquals(1, pts[0].x);
        assertEquals(2, pts[0].y);
    }

    // --- ArrayBlockingQueue special-case branch (Issue#161) ---

    // covers createCollectionDeserializer: ArrayBlockingQueue special-case (no default constructor)
    @Test
    public void testArrayBlockingQueue_DeserializesElementsInOrder() throws Throwable {
        TypeReference<ArrayBlockingQueue<Integer>> tr = new TypeReference<ArrayBlockingQueue<Integer>>() { };
        ArrayBlockingQueue<Integer> queue = mapper.readValue("[1,2,3]", tr);
        assertEquals(3, queue.size());
        Iterator<Integer> it = queue.iterator();
        assertEquals(Integer.valueOf(1), it.next());
        assertEquals(Integer.valueOf(2), it.next());
        assertEquals(Integer.valueOf(3), it.next());
        assertFalse(it.hasNext());
    }

    // --- Non-concrete type without fallback throws ---

    // covers createCollectionDeserializer: no fallback found, type.getTypeHandler()==null -> throw
    @Test
    public void testAbstractCollectionWithoutFallback_ThrowsForUnmappableInterface() throws Throwable {
        try {
            mapper.readValue("[1,2,3]", MyCollection.class);
            fail("expected exception for non-concrete Collection type without fallback");
        } catch (Exception e) {
            assertTrue(e instanceof JsonMappingException || e instanceof IllegalArgumentException);
        }
    }

    // covers createMapDeserializer: no fallback found, type.getTypeHandler()==null -> throw
    @Test
    public void testAbstractMapWithoutFallback_ThrowsForUnmappableInterface() throws Throwable {
        try {
            mapper.readValue("{}", MyMap.class);
            fail("expected exception for non-concrete Map type without fallback");
        } catch (Exception e) {
            assertTrue(e instanceof JsonMappingException || e instanceof IllegalArgumentException);
        }
    }
}
