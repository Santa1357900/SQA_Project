package com.fasterxml.jackson.databind.ser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BasicSerializerFactoryClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    public enum Color { RED, GREEN, BLUE }

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public enum Level {
        LOW(1), MEDIUM(2), HIGH(3);
        private final int code;
        Level(int code) { this.code = code; }
        public int getCode() { return code; }
    }

    public static class BoolBean {
        private boolean flag;
        private Boolean flagObj;
        public boolean isFlag() { return flag; }
        public void setFlag(boolean flag) { this.flag = flag; }
        public Boolean getFlagObj() { return flagObj; }
        public void setFlagObj(Boolean flagObj) { this.flagObj = flagObj; }
    }

    public static class CharBean {
        private char c;
        public char getC() { return c; }
        public void setC(char c) { this.c = c; }
    }

    public static class NumBean {
        private Integer value;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }
    }

    public static class ValueHolder {
        private final String v;
        public ValueHolder(String v) { this.v = v; }
        @JsonValue
        public String asText() { return v; }
    }

    public static class NameIterable implements Iterable<String> {
        private final List<String> items;
        public NameIterable(List<String> items) { this.items = items; }
        public Iterator<String> iterator() { return items.iterator(); }
    }

    // covers findSerializerByLookup exact match for String.class
    @Test
    public void testWriteString_producesQuotedJson() throws Throwable {
        String json = mapper.writeValueAsString("hello");
        assertEquals("\"hello\"", json);
    }

    // covers _concrete lookup for StringBuilder using ToStringSerializer
    @Test
    public void testWriteStringBuilder_usesToStringSerializer() throws Throwable {
        StringBuilder sb = new StringBuilder("abc");
        String json = mapper.writeValueAsString(sb);
        assertEquals("\"abc\"", json);
    }

    // covers _concrete lookup for Character.class
    @Test
    public void testWriteCharacter_producesSingleCharString() throws Throwable {
        String json = mapper.writeValueAsString(Character.valueOf('Q'));
        assertEquals("\"Q\"", json);
    }

    // covers _concrete lookup for boolean primitive and Boolean wrapper
    @Test
    public void testWriteBoolean_primitiveAndWrapperInBean() throws Throwable {
        BoolBean b = new BoolBean();
        b.setFlag(true);
        b.setFlagObj(Boolean.FALSE);
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"flag\":true"));
        assertTrue(json.contains("\"flagObj\":false"));
    }

    // covers _concrete lookup for BigInteger via NumberSerializer
    @Test
    public void testWriteBigInteger_producesPlainNumber() throws Throwable {
        String json = mapper.writeValueAsString(BigInteger.valueOf(123456789L));
        assertEquals("123456789", json);
    }

    // covers _concrete lookup for BigDecimal via NumberSerializer
    @Test
    public void testWriteBigDecimal_producesPlainNumber() throws Throwable {
        String json = mapper.writeValueAsString(new BigDecimal("3.14"));
        assertEquals("3.14", json);
    }

    // covers _concrete lookup for java.util.Date, default timestamp shape
    @Test
    public void testWriteDate_defaultAsTimestamp() throws Throwable {
        String json = mapper.writeValueAsString(new Date(0L));
        assertEquals("0", json);
    }

    // covers _concrete lookup for Calendar via CalendarSerializer
    @Test
    public void testWriteCalendar_asTimestamp() throws Throwable {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(0L);
        String json = mapper.writeValueAsString(cal);
        assertEquals("0", json);
    }

    // covers _concrete lookup for java.sql.Timestamp reusing dateSer instance
    @Test
    public void testWriteSqlTimestamp_sameAsDateSerializer() throws Throwable {
        String json = mapper.writeValueAsString(new java.sql.Timestamp(0L));
        assertEquals("0", json);
    }

    // covers buildArraySerializer branch for String[].class
    @Test
    public void testWriteStringArray_producesJsonArray() throws Throwable {
        String json = mapper.writeValueAsString(new String[]{"a", "b"});
        assertEquals("[\"a\",\"b\"]", json);
    }

    // covers buildArraySerializer fallback path for non-String array types
    @Test
    public void testWriteIntegerArray_producesJsonArray() throws Throwable {
        String json = mapper.writeValueAsString(new Integer[]{1, 2, 3});
        assertEquals("[1,2,3]", json);
    }

    // covers ObjectArraySerializer branch for generic Object[]
    @Test
    public void testWriteMixedObjectArray_producesJsonArray() throws Throwable {
        Object[] arr = new Object[]{"x", Integer.valueOf(5)};
        String json = mapper.writeValueAsString(arr);
        assertEquals("[\"x\",5]", json);
    }

    // covers isIndexedList true + elementRaw==String.class -> IndexedStringListSerializer
    @Test
    public void testWriteArrayListString_indexedStringListPath() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        String json = mapper.writeValueAsString(list);
        assertEquals("[\"a\",\"b\"]", json);
    }

    // covers isIndexedList false (LinkedList) + elementRaw==String.class -> StringCollectionSerializer
    @Test
    public void testWriteLinkedListString_nonIndexedStringPath() throws Throwable {
        List<String> list = new LinkedList<String>();
        list.add("a");
        list.add("b");
        String json = mapper.writeValueAsString(list);
        assertEquals("[\"a\",\"b\"]", json);
    }

    // covers buildIndexedListSerializer for non-String element type
    @Test
    public void testWriteArrayListInteger_indexedListPath() throws Throwable {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        String json = mapper.writeValueAsString(list);
        assertEquals("[1,2,3]", json);
    }

    // covers buildCollectionSerializer(non-indexed, non-String) path
    @Test
    public void testWriteLinkedListInteger_collectionSerializerPath() throws Throwable {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        String json = mapper.writeValueAsString(list);
        assertEquals("[1,2]", json);
    }

    // covers 0-element loop for collection serializer
    @Test
    public void testWriteEmptyArrayList_producesEmptyArray() throws Throwable {
        List<String> list = new ArrayList<String>();
        String json = mapper.writeValueAsString(list);
        assertEquals("[]", json);
    }

    // covers buildEnumSetSerializer branch (EnumSet.class assignable)
    @Test
    public void testWriteEnumSet_naturalOrder() throws Throwable {
        EnumSet<Color> set = EnumSet.of(Color.BLUE, Color.RED);
        String json = mapper.writeValueAsString(set);
        assertEquals("[\"RED\",\"BLUE\"]", json);
    }

    // covers buildMapSerializer for true Map type
    @Test
    public void testWriteLinkedHashMap_preservesInsertionOrder() throws Throwable {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        String json = mapper.writeValueAsString(map);
        assertEquals("{\"a\":1,\"b\":2}", json);
    }

    // covers 0-entry map serialization
    @Test
    public void testWriteEmptyMap_producesEmptyObject() throws Throwable {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        String json = mapper.writeValueAsString(map);
        assertEquals("{}", json);
    }

    // covers buildEnumSerializer default (non-OBJECT shape) branch
    @Test
    public void testWriteEnumPlain_producesNameString() throws Throwable {
        String json = mapper.writeValueAsString(Color.GREEN);
        assertEquals("\"GREEN\"", json);
    }

    // covers buildEnumSerializer OBJECT shape branch returning null -> bean-style serialization
    @Test
    public void testWriteEnumWithObjectShape_producesJsonObject() throws Throwable {
        String json = mapper.writeValueAsString(Level.HIGH);
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"code\":3"));
    }

    // covers findSerializerByPrimaryType Number branch with STRING shape format
    @Test
    public void testWriteNumberFieldWithStringShape_producesQuotedNumber() throws Throwable {
        NumBean bean = new NumBean();
        bean.setValue(Integer.valueOf(5));
        String json = mapper.writeValueAsString(bean);
        assertEquals("{\"value\":\"5\"}", json);
    }

    // covers findSerializerByAddonType Iterator branch
    @Test
    public void testWriteIterator_producesJsonArray() throws Throwable {
        List<String> src = Arrays.asList("p", "q");
        Iterator<String> it = src.iterator();
        String json = mapper.writeValueAsString(it);
        assertEquals("[\"p\",\"q\"]", json);
    }

    // covers findSerializerByAddonType Iterable branch
    @Test
    public void testWriteCustomIterable_producesJsonArray() throws Throwable {
        List<String> items = new ArrayList<String>();
        items.add("x");
        items.add("y");
        NameIterable ni = new NameIterable(items);
        String json = mapper.writeValueAsString(ni);
        assertEquals("[\"x\",\"y\"]", json);
    }

    // covers findSerializerByAddonType CharSequence branch using ToStringSerializer
    @Test
    public void testWriteCharBuffer_producesQuotedToString() throws Throwable {
        CharBuffer cb = CharBuffer.wrap("hello");
        String json = mapper.writeValueAsString(cb);
        assertEquals("\"hello\"", json);
    }

    // covers findSerializerByLookup reference-type branch for AtomicReference
    @Test
    public void testWriteAtomicReferenceWithValue_unwrapsValue() throws Throwable {
        AtomicReference<String> ref = new AtomicReference<String>("hi");
        String json = mapper.writeValueAsString(ref);
        assertEquals("\"hi\"", json);
    }

    // covers AtomicReferenceSerializer with absent/null referenced value
    @Test
    public void testWriteAtomicReferenceNull_producesJsonNull() throws Throwable {
        AtomicReference<String> ref = new AtomicReference<String>(null);
        String json = mapper.writeValueAsString(ref);
        assertEquals("null", json);
    }

    // covers findSerializerByPrimaryType Map.Entry branch + buildMapEntrySerializer
    @Test
    public void testWriteMapEntry_producesSingleFieldObject() throws Throwable {
        Map<String, Integer> src = Collections.singletonMap("k", Integer.valueOf(5));
        Map.Entry<String, Integer> entry = src.entrySet().iterator().next();
        String json = mapper.writeValueAsString(entry);
        assertEquals("{\"k\":5}", json);
    }

    // covers buildMapEntrySerializer path with null value included by default
    @Test
    public void testWriteMapEntryWithNullValue_includesNullField() throws Throwable {
        Map<String, String> src = new HashMap<String, String>();
        src.put("a", null);
        Map.Entry<String, String> entry = src.entrySet().iterator().next();
        String json = mapper.writeValueAsString(entry);
        assertEquals("{\"a\":null}", json);
    }

    // covers findSerializerByAnnotations @JsonValue branch
    @Test
    public void testWriteJsonValueAnnotatedBean_usesCustomValue() throws Throwable {
        ValueHolder vh = new ValueHolder("zzz");
        String json = mapper.writeValueAsString(vh);
        assertEquals("\"zzz\"", json);
    }

    // covers _concrete lookup for char primitive type inside bean property
    @Test
    public void testWriteCharPrimitiveFieldInBean_producesSingleCharString() throws Throwable {
        CharBean cb = new CharBean();
        cb.setC('Z');
        String json = mapper.writeValueAsString(cb);
        assertEquals("{\"c\":\"Z\"}", json);
    }

    // covers createKeySerializer default numeric-key coercion path
    @Test
    public void testWriteMapWithIntegerKeys_keysCoercedToStrings() throws Throwable {
        Map<Integer, String> map = new LinkedHashMap<Integer, String>();
        map.put(Integer.valueOf(1), "one");
        map.put(Integer.valueOf(2), "two");
        String json = mapper.writeValueAsString(map);
        assertEquals("{\"1\":\"one\",\"2\":\"two\"}", json);
    }
}
