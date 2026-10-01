package com.fasterxml.jackson.databind.deser;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BasicDeserializerFactoryClaudeTest {

    public static class SimpleBean {
        private String name;
        private int age;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    public enum Color { RED, GREEN, BLUE }

    public static class EnumSetWrapper {
        private EnumSet<Color> colors;
        public EnumSet<Color> getColors() { return colors; }
        public void setColors(EnumSet<Color> colors) { this.colors = colors; }
    }

    public static class EnumMapWrapper {
        private EnumMap<Color, Integer> counts;
        public EnumMap<Color, Integer> getCounts() { return counts; }
        public void setCounts(EnumMap<Color, Integer> counts) { this.counts = counts; }
    }

    public static class AtomicRefWrapper {
        private AtomicReference<String> ref;
        public AtomicReference<String> getRef() { return ref; }
        public void setRef(AtomicReference<String> ref) { this.ref = ref; }
    }

    public static class IterableWrapper {
        private Iterable<String> items;
        public Iterable<String> getItems() { return items; }
        public void setItems(Iterable<String> items) { this.items = items; }
    }

    public static class EntryWrapper {
        private Map.Entry<String, Integer> entry;
        public Map.Entry<String, Integer> getEntry() { return entry; }
        public void setEntry(Map.Entry<String, Integer> entry) { this.entry = entry; }
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

    public static class LongWrapper {
        private final long value;
        public LongWrapper(long value) { this.value = value; }
        public long getValue() { return value; }
    }

    public static class DoubleWrapper {
        private final double value;
        public DoubleWrapper(double value) { this.value = value; }
        public double getValue() { return value; }
    }

    public static class BooleanWrapper {
        private final boolean value;
        public BooleanWrapper(boolean value) { this.value = value; }
        public boolean isValue() { return value; }
    }

    public static class PointBean {
        private final int x;
        private final int y;
        @JsonCreator
        public PointBean(@JsonProperty("x") int x, @JsonProperty("y") int y) {
            this.x = x;
            this.y = y;
        }
        public int getX() { return x; }
        public int getY() { return y; }
    }

    public static class BadPointBean {
        private final int x;
        private final int y;
        @JsonCreator
        public BadPointBean(@JsonProperty("x") int x, int y) {
            this.x = x;
            this.y = y;
        }
        public int getX() { return x; }
        public int getY() { return y; }
    }

    public static class FactoryBean {
        private final String name;
        private FactoryBean(String name) { this.name = name; }
        @JsonCreator
        public static FactoryBean create(@JsonProperty("name") String name) {
            return new FactoryBean(name);
        }
        public String getName() { return name; }
    }

    // Intentionally non-static inner class to exercise the special
    // "non-static inner class + @JsonCreator" check in the factory.
    public class NonStaticInnerBean {
        private final String value;
        @JsonCreator
        public NonStaticInnerBean(@JsonProperty("value") String value) {
            this.value = value;
        }
        public String getValue() { return value; }
    }

    // _collectionFallbacks: List -> ArrayList
    @Test
    public void testListInterface_FallsBackToArrayList() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        List<String> result = mapper.readValue("[\"a\",\"b\"]", new TypeReference<List<String>>() {});
        assertEquals(ArrayList.class, result.getClass());
        assertEquals(2, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
    }

    // _collectionFallbacks: Collection -> ArrayList
    @Test
    public void testCollectionInterface_FallsBackToArrayList() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Collection<String> result = mapper.readValue("[\"x\",\"y\"]", new TypeReference<Collection<String>>() {});
        assertEquals(ArrayList.class, result.getClass());
        assertTrue(result.contains("x"));
        assertTrue(result.contains("y"));
    }

    // _collectionFallbacks: Set -> HashSet
    @Test
    public void testSetInterface_FallsBackToHashSet() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> result = mapper.readValue("[\"a\",\"b\"]", new TypeReference<Set<String>>() {});
        assertEquals(HashSet.class, result.getClass());
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
    }

    // _collectionFallbacks: SortedSet -> TreeSet, natural ordering
    @Test
    public void testSortedSetInterface_FallsBackToTreeSet_NaturalOrdering() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SortedSet<Integer> result = mapper.readValue("[3,1,2]", new TypeReference<SortedSet<Integer>>() {});
        assertEquals(TreeSet.class, result.getClass());
        Iterator<Integer> it = result.iterator();
        assertEquals(Integer.valueOf(1), it.next());
        assertEquals(Integer.valueOf(2), it.next());
        assertEquals(Integer.valueOf(3), it.next());
    }

    // _collectionFallbacks: Queue -> LinkedList, FIFO order
    @Test
    public void testQueueInterface_FallsBackToLinkedList_FIFOOrder() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Queue<String> result = mapper.readValue("[\"first\",\"second\"]", new TypeReference<Queue<String>>() {});
        assertEquals(LinkedList.class, result.getClass());
        assertEquals("first", result.poll());
        assertEquals("second", result.poll());
    }

    // _collectionFallbacks: Deque (string-keyed fallback) -> LinkedList
    @Test
    public void testDequeInterface_FallsBackToLinkedList() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Deque<String> result = mapper.readValue("[\"a\",\"b\"]", new TypeReference<Deque<String>>() {});
        assertEquals(LinkedList.class, result.getClass());
        assertEquals("a", result.peekFirst());
        assertEquals("b", result.peekLast());
    }

    // _collectionFallbacks: NavigableSet (string-keyed fallback) -> TreeSet
    @Test
    public void testNavigableSetInterface_FallsBackToTreeSet() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        NavigableSet<Integer> result = mapper.readValue("[5,1,3]", new TypeReference<NavigableSet<Integer>>() {});
        assertEquals(TreeSet.class, result.getClass());
        assertEquals(Integer.valueOf(1), result.first());
        assertEquals(Integer.valueOf(5), result.last());
    }

    // _mapFallbacks: Map -> LinkedHashMap, preserves insertion order
    @Test
    public void testMapInterface_FallsBackToLinkedHashMap_PreservesInsertionOrder() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Integer> result = mapper.readValue("{\"b\":2,\"a\":1}", new TypeReference<Map<String, Integer>>() {});
        assertEquals(LinkedHashMap.class, result.getClass());
        Iterator<String> keys = result.keySet().iterator();
        assertEquals("b", keys.next());
        assertEquals("a", keys.next());
    }

    // _mapFallbacks: ConcurrentMap -> ConcurrentHashMap
    @Test
    public void testConcurrentMapInterface_FallsBackToConcurrentHashMap() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ConcurrentMap<String, Integer> result = mapper.readValue("{\"a\":1}", new TypeReference<ConcurrentMap<String, Integer>>() {});
        assertEquals(ConcurrentHashMap.class, result.getClass());
        assertEquals(Integer.valueOf(1), result.get("a"));
    }

    // _mapFallbacks: SortedMap -> TreeMap, sorted keys
    @Test
    public void testSortedMapInterface_FallsBackToTreeMap_SortedKeys() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SortedMap<String, Integer> result = mapper.readValue("{\"b\":2,\"a\":1}", new TypeReference<SortedMap<String, Integer>>() {});
        assertEquals(TreeMap.class, result.getClass());
        assertEquals("a", result.firstKey());
    }

    // _mapFallbacks: NavigableMap -> TreeMap
    @Test
    public void testNavigableMapInterface_FallsBackToTreeMap() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        NavigableMap<String, Integer> result = mapper.readValue("{\"z\":1,\"a\":2}", new TypeReference<NavigableMap<String, Integer>>() {});
        assertEquals(TreeMap.class, result.getClass());
        assertEquals("a", result.firstKey());
    }

    // _mapFallbacks: ConcurrentNavigableMap -> ConcurrentSkipListMap
    @Test
    public void testConcurrentNavigableMapInterface_FallsBackToConcurrentSkipListMap() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ConcurrentNavigableMap<String, Integer> result = mapper.readValue("{\"a\":1}", new TypeReference<ConcurrentNavigableMap<String, Integer>>() {});
        assertEquals(ConcurrentSkipListMap.class, result.getClass());
    }

    // createCollectionDeserializer: EnumSet special-cased branch
    @Test
    public void testEnumSet_DeserializesElementsCorrectly() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        EnumSetWrapper wrapper = mapper.readValue("{\"colors\":[\"RED\",\"BLUE\"]}", EnumSetWrapper.class);
        assertTrue(wrapper.getColors().contains(Color.RED));
        assertTrue(wrapper.getColors().contains(Color.BLUE));
        assertFalse(wrapper.getColors().contains(Color.GREEN));
    }

    // createMapDeserializer: EnumMap special-cased branch
    @Test
    public void testEnumMap_DeserializesEntriesCorrectly() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        EnumMapWrapper wrapper = mapper.readValue("{\"counts\":{\"RED\":1,\"BLUE\":2}}", EnumMapWrapper.class);
        assertEquals(Integer.valueOf(1), wrapper.getCounts().get(Color.RED));
        assertEquals(Integer.valueOf(2), wrapper.getCounts().get(Color.BLUE));
    }

    // createReferenceDeserializer: AtomicReference branch
    @Test
    public void testAtomicReference_DeserializesWrappedValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        AtomicRefWrapper wrapper = mapper.readValue("{\"ref\":\"hello\"}", AtomicRefWrapper.class);
        assertEquals("hello", wrapper.getRef().get());
    }

    // findDefaultDeserializer: CLASS_ITERABLE upgraded to Collection
    @Test
    public void testIterableField_UpgradedToCollection() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        IterableWrapper wrapper = mapper.readValue("{\"items\":[\"a\",\"b\"]}", IterableWrapper.class);
        assertTrue(wrapper.getItems() instanceof Collection);
        Iterator<String> it = wrapper.getItems().iterator();
        assertEquals("a", it.next());
        assertEquals("b", it.next());
    }

    // findDefaultDeserializer: CLASS_MAP_ENTRY branch
    @Test
    public void testMapEntryField_DeserializesKeyAndValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        EntryWrapper wrapper = mapper.readValue("{\"entry\":{\"k\":5}}", EntryWrapper.class);
        assertEquals("k", wrapper.getEntry().getKey());
        assertEquals(Integer.valueOf(5), wrapper.getEntry().getValue());
    }

    // _handleSingleArgumentConstructor: String branch
    @Test
    public void testSingleArgConstructor_StringCreator() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        StringWrapper w = mapper.readValue("\"hello\"", StringWrapper.class);
        assertEquals("hello", w.getValue());
    }

    // _handleSingleArgumentConstructor: int branch
    @Test
    public void testSingleArgConstructor_IntCreator() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        IntWrapper w = mapper.readValue("42", IntWrapper.class);
        assertEquals(42, w.getValue());
    }

    // _handleSingleArgumentConstructor: long branch
    @Test
    public void testSingleArgConstructor_LongCreator() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        LongWrapper w = mapper.readValue("123456789012", LongWrapper.class);
        assertEquals(123456789012L, w.getValue());
    }

    // _handleSingleArgumentConstructor: double branch
    @Test
    public void testSingleArgConstructor_DoubleCreator() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DoubleWrapper w = mapper.readValue("3.14", DoubleWrapper.class);
        assertEquals(3.14, w.getValue(), 1e-9);
    }

    // _handleSingleArgumentConstructor: boolean branch
    @Test
    public void testSingleArgConstructor_BooleanCreator() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        BooleanWrapper w = mapper.readValue("true", BooleanWrapper.class);
        assertTrue(w.isValue());
    }

    // _addDeserializerConstructors: multi-arg @JsonCreator, all explicitly named -> propertyCreator
    @Test
    public void testMultiArgConstructor_JsonCreatorWithExplicitNames_BuildsBean() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        PointBean p = mapper.readValue("{\"x\":3,\"y\":7}", PointBean.class);
        assertEquals(3, p.getX());
        assertEquals(7, p.getY());
    }

    // _addDeserializerConstructors: multi-arg @JsonCreator missing a name -> IllegalArgumentException
    @Test
    public void testMultiArgConstructor_JsonCreatorMissingPropertyName_ThrowsIllegalArgumentException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"x\":1,\"y\":2}", BadPointBean.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("property name"));
        }
    }

    // _addDeserializerConstructors: non-static inner class + @JsonCreator -> special IllegalArgumentException
    @Test
    public void testNonStaticInnerClassConstructor_JsonCreator_ThrowsIllegalArgumentException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"value\":\"x\"}", NonStaticInnerBean.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("inner classes"));
        }
    }

    // _addDeserializerFactoryMethods: static factory method with @JsonCreator and named property
    @Test
    public void testStaticFactoryMethod_JsonCreatorWithNames_BuildsBean() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        FactoryBean f = mapper.readValue("{\"name\":\"joe\"}", FactoryBean.class);
        assertEquals("joe", f.getName());
    }

    // _constructDefaultValueInstantiator: default (zero-arg) constructor + setters
    @Test
    public void testDefaultConstructorBean_DeserializesViaSetters() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SimpleBean b = mapper.readValue("{\"name\":\"anna\",\"age\":25}", SimpleBean.class);
        assertEquals("anna", b.getName());
        assertEquals(25, b.getAge());
    }

    // createEnumDeserializer: standard enum-by-name resolution
    @Test
    public void testEnumDeserialization_ByName() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Color c = mapper.readValue("\"GREEN\"", Color.class);
        assertEquals(Color.GREEN, c);
    }

    // findDefaultDeserializer: CLASS_OBJECT (untyped) branch returns a Map for JSON object
    @Test
    public void testObjectClassDeserialization_ReturnsMapForJsonObject() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Object o = mapper.readValue("{\"a\":1}", Object.class);
        assertTrue(o instanceof Map);
        assertEquals(Integer.valueOf(1), ((Map<?, ?>) o).get("a"));
    }

    // createArrayDeserializer: String[] uses StringArrayDeserializer path
    @Test
    public void testStringArrayDeserialization_UsesStringArrayDeserializer() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] arr = mapper.readValue("[\"a\",\"b\"]", String[].class);
        assertEquals(2, arr.length);
        assertEquals("a", arr[0]);
        assertEquals("b", arr[1]);
    }

    // createArrayDeserializer: primitive int[] uses PrimitiveArrayDeserializers
    @Test
    public void testPrimitiveIntArrayDeserialization() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        int[] arr = mapper.readValue("[1,2,3]", int[].class);
        assertArrayEquals(new int[] {1, 2, 3}, arr);
    }

    // createArrayDeserializer: boxed Integer[] uses generic ObjectArrayDeserializer
    @Test
    public void testObjectArrayDeserialization_IntegerArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Integer[] arr = mapper.readValue("[1,2,3]", Integer[].class);
        assertEquals(Integer.valueOf(1), arr[0]);
        assertEquals(Integer.valueOf(3), arr[2]);
    }

    // findDefaultDeserializer: java.* type falls through to JdkDeserializers (UUID)
    @Test
    public void testUUIDDeserialization_FromString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        UUID uuid = mapper.readValue("\"123e4567-e89b-12d3-a456-426614174000\"", UUID.class);
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), uuid);
    }
}
