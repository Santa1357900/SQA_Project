package com.fasterxml.jackson.databind.deser.std;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

public class CollectionDeserializerClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable
    {
        mapper = new ObjectMapper();
    }

    public static class Point {
        private int x;
        private int y;
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
    }

    public static class WrapEnabledBean {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Integer> values;
        public List<Integer> getValues() { return values; }
        public void setValues(List<Integer> values) { this.values = values; }
    }

    public static class WrapDisabledBean {
        @JsonFormat(without = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Integer> values;
        public List<Integer> getValues() { return values; }
        public void setValues(List<Integer> values) { this.values = values; }
    }

    // covers: deserialize(p,ctxt,result) normal array path, multiple loop iterations
    @Test
    public void testDeserialize_basicArrayOfIntegers_returnsListWithAllElements() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) mapper.readValue("[1,2,3]", type);
        assertEquals(3, list.size());
        assertEquals(Integer.valueOf(1), list.get(0));
        assertEquals(Integer.valueOf(2), list.get(1));
        assertEquals(Integer.valueOf(3), list.get(2));
    }

    // covers: loop with 0 iterations, immediate END_ARRAY
    @Test
    public void testDeserialize_emptyArray_returnsEmptyCollection() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) mapper.readValue("[]", type);
        assertTrue(list.isEmpty());
    }

    // covers: VALUE_NULL branch inside loop -> getNullValue used
    @Test
    public void testDeserialize_arrayWithNullElement_containsNull() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) mapper.readValue("[1,null,3]", type);
        assertEquals(3, list.size());
        assertNull(list.get(1));
    }

    // covers: whole value token is null -> handled before array logic, result null
    @Test
    public void testDeserialize_wholeValueNull_returnsNullCollection() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        Object result = mapper.readValue("null", type);
        assertNull(result);
    }

    // covers: nested collections, content type resolution recursively
    @Test
    public void testDeserialize_nestedArrays_intoListOfLists() throws Throwable {
        JavaType innerType = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        JavaType outerType = mapper.getTypeFactory().constructCollectionType(List.class, innerType);
        List<List<Integer>> outer = (List<List<Integer>>) mapper.readValue("[[1,2],[3]]", outerType);
        assertEquals(2, outer.size());
        assertEquals(2, outer.get(0).size());
        assertEquals(1, outer.get(1).size());
        assertEquals(Integer.valueOf(3), outer.get(1).get(0));
    }

    // covers: catch(Exception e) -> wrap with path when element fails to deserialize
    @Test
    public void testDeserialize_typeMismatchElement_throwsJsonMappingException() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        try {
            mapper.readValue("[1,\"abc\",3]", type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers: loop with many iterations
    @Test
    public void testDeserialize_largeArray_allElementsPreserved() throws Throwable {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb.append(",");
            sb.append(i);
        }
        sb.append("]");
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) mapper.readValue(sb.toString(), type);
        assertEquals(50, list.size());
        assertEquals(Integer.valueOf(0), list.get(0));
        assertEquals(Integer.valueOf(49), list.get(49));
    }

    // covers: Set target collection, duplicate handling per Set contract
    @Test
    public void testDeserialize_setTarget_duplicatesRemoved() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(Set.class, Integer.class);
        Set<Integer> set = (Set<Integer>) mapper.readValue("[1,1,2,2,3]", type);
        assertEquals(3, set.size());
        assertTrue(set.contains(1));
        assertTrue(set.contains(2));
        assertTrue(set.contains(3));
    }

    // covers: String element deserialization with escapes/unicode
    @Test
    public void testDeserialize_stringArrayWithUnicodeAndEscapes() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> list = (List<String>) mapper.readValue("[\"a\\nb\", \"\\u00e9\"]", type);
        assertEquals(2, list.size());
        assertEquals("a\nb", list.get(0));
        assertEquals("\u00e9", list.get(1));
    }

    // covers: boundary Long values within array
    @Test
    public void testDeserialize_boundaryLongValues() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Long.class);
        List<Long> list = (List<Long>) mapper.readValue(
                "[-9223372036854775808, 9223372036854775807]", type);
        assertEquals(Long.valueOf(Long.MIN_VALUE), list.get(0));
        assertEquals(Long.valueOf(Long.MAX_VALUE), list.get(1));
    }

    // covers: handleNonArray with global feature enabled -> single value wrapped as list
    @Test
    public void testDeserialize_nonArraySingleValue_withGlobalFeatureEnabled_wrapsAsSingleElementList() throws Throwable {
        ObjectMapper wrapMapper = new ObjectMapper();
        wrapMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        JavaType type = wrapMapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) wrapMapper.readValue("5", type);
        assertEquals(1, list.size());
        assertEquals(Integer.valueOf(5), list.get(0));
    }

    // covers: handleNonArray canWrap==false branch -> throws mapping exception
    @Test
    public void testDeserialize_nonArraySingleValue_withoutFeature_throwsException() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        try {
            mapper.readValue("5", type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers: handleNonArray with String single value wrapped
    @Test
    public void testDeserialize_nonArrayString_withFeatureEnabled_wrapsAsSingleElementList() throws Throwable {
        ObjectMapper wrapMapper = new ObjectMapper();
        wrapMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        JavaType type = wrapMapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> list = (List<String>) wrapMapper.readValue("\"hello\"", type);
        assertEquals(1, list.size());
        assertEquals("hello", list.get(0));
    }

    // covers: createContextual per-property JsonFormat override enabling wrap though global disabled
    @Test
    public void testCreateContextual_perPropertyFormatOverride_enablesSingleValueWrap_evenWhenGlobalDisabled() throws Throwable {
        WrapEnabledBean bean = mapper.readValue("{\"values\":5}", WrapEnabledBean.class);
        assertNotNull(bean.getValues());
        assertEquals(1, bean.getValues().size());
        assertEquals(Integer.valueOf(5), bean.getValues().get(0));
    }

    // covers: createContextual per-property JsonFormat override disabling wrap though global enabled
    @Test
    public void testCreateContextual_perPropertyFormatOverride_disablesSingleValueWrap_evenWhenGlobalEnabled() throws Throwable {
        ObjectMapper wrapMapper = new ObjectMapper();
        wrapMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        try {
            wrapMapper.readValue("{\"values\":5}", WrapDisabledBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers: empty array into Set target
    @Test
    public void testDeserialize_emptyArrayIntoSet_returnsEmptySet() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(Set.class, Integer.class);
        Set<Integer> set = (Set<Integer>) mapper.readValue("[]", type);
        assertTrue(set.isEmpty());
    }

    // covers: array with empty string elements preserved as-is
    @Test
    public void testDeserialize_emptyStringsArray_preserved() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> list = (List<String>) mapper.readValue("[\"\",\"\"]", type);
        assertEquals(2, list.size());
        assertEquals("", list.get(0));
        assertEquals("", list.get(1));
    }

    // covers: Boolean element deserialization sequence
    @Test
    public void testDeserialize_booleanArray_sequencePreserved() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Boolean.class);
        List<Boolean> list = (List<Boolean>) mapper.readValue("[true,false,true]", type);
        assertEquals(3, list.size());
        assertTrue(list.get(0).booleanValue());
        assertFalse(list.get(1).booleanValue());
        assertTrue(list.get(2).booleanValue());
    }

    // covers: Double element deserialization including negative and zero values
    @Test
    public void testDeserialize_doubleArrayWithNegativeAndZero() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Double.class);
        List<Double> list = (List<Double>) mapper.readValue("[-1.5,0.0,3.25]", type);
        assertEquals(-1.5, list.get(0).doubleValue(), 1e-9);
        assertEquals(0.0, list.get(1).doubleValue(), 1e-9);
        assertEquals(3.25, list.get(2).doubleValue(), 1e-9);
    }

    // covers: normal array path with exactly one element (distinct from non-array wrap path)
    @Test
    public void testDeserialize_singleElementArray_normalArrayPathSizeOne() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) mapper.readValue("[42]", type);
        assertEquals(1, list.size());
        assertEquals(Integer.valueOf(42), list.get(0));
    }

    // covers: POJO element deserialization inside array
    @Test
    public void testDeserialize_pojoElementsArray_valuesMapped() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Point.class);
        List<Point> list = (List<Point>) mapper.readValue(
                "[{\"x\":1,\"y\":2},{\"x\":3,\"y\":4}]", type);
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).getX());
        assertEquals(2, list.get(0).getY());
        assertEquals(3, list.get(1).getX());
        assertEquals(4, list.get(1).getY());
    }

    // covers: mix of null and object elements in same array
    @Test
    public void testDeserialize_mixedNullAndPojoElements() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Point.class);
        List<Point> list = (List<Point>) mapper.readValue(
                "[null, {\"x\":1,\"y\":2}]", type);
        assertEquals(2, list.size());
        assertNull(list.get(0));
        assertEquals(1, list.get(1).getX());
    }

    // covers: negative integers including Integer.MIN_VALUE boundary
    @Test
    public void testDeserialize_negativeIntegersIncludingMinValue() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) mapper.readValue(
                "[-1,-100,-2147483648]", type);
        assertEquals(Integer.valueOf(-1), list.get(0));
        assertEquals(Integer.valueOf(-100), list.get(1));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), list.get(2));
    }

    // covers: Integer.MAX_VALUE boundary preserved
    @Test
    public void testDeserialize_maxIntegerValuePreserved() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        List<Integer> list = (List<Integer>) mapper.readValue("[2147483647]", type);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), list.get(0));
    }

    // covers: value instantiator path for concrete LinkedList collection type
    @Test
    public void testDeserialize_linkedListConcreteTarget() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(LinkedList.class, Integer.class);
        LinkedList<Integer> list = (LinkedList<Integer>) mapper.readValue("[7,8,9]", type);
        assertEquals(3, list.size());
        assertEquals(Integer.valueOf(7), list.getFirst());
        assertEquals(Integer.valueOf(9), list.getLast());
    }

    // covers: exception path carries non-empty JsonMappingException path info
    @Test
    public void testDeserialize_typeMismatch_exceptionHasNonEmptyPath() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        try {
            mapper.readValue("[1,2,\"x\"]", type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertFalse(expected.getPath().isEmpty());
        }
    }

    // covers: LinkedHashSet preserves insertion order while removing duplicates
    @Test
    public void testDeserialize_linkedHashSetPreservesInsertionOrder() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(LinkedHashSet.class, Integer.class);
        LinkedHashSet<Integer> set = (LinkedHashSet<Integer>) mapper.readValue("[3,1,2,1]", type);
        assertEquals(3, set.size());
        Object[] arr = set.toArray();
        assertEquals(Integer.valueOf(3), arr[0]);
        assertEquals(Integer.valueOf(1), arr[1]);
        assertEquals(Integer.valueOf(2), arr[2]);
    }

    // covers: deserialize(p,ctxt,result) reuse path via readerForUpdating, appends to existing collection
    @Test
    public void testDeserialize_readerForUpdating_appendsToExistingCollection() throws Throwable {
        List<Integer> existing = new ArrayList<Integer>();
        existing.add(100);
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Integer.class);
        ObjectReader reader = mapper.readerForUpdating(existing).forType(type);
        List<Integer> result = (List<Integer>) reader.readValue("[1,2]");
        assertEquals(3, result.size());
        assertEquals(Integer.valueOf(100), result.get(0));
        assertEquals(Integer.valueOf(1), result.get(1));
        assertEquals(Integer.valueOf(2), result.get(2));
    }
}
