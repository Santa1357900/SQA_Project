package com.fasterxml.jackson.databind.deser;

import java.io.IOException;
import java.util.*;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BeanDeserializerClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    public static class SimpleBean {
        private String name;
        private int age;
        public SimpleBean() { }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    public static class NestedBean {
        private SimpleBean inner;
        private String label;
        public NestedBean() { }
        public SimpleBean getInner() { return inner; }
        public void setInner(SimpleBean inner) { this.inner = inner; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class ListBean {
        private List<String> items;
        public ListBean() { }
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
    }

    public static class MapBean {
        private Map<String, Integer> values;
        public MapBean() { }
        public Map<String, Integer> getValues() { return values; }
        public void setValues(Map<String, Integer> values) { this.values = values; }
    }

    public static class CreatorBean {
        private final String id;
        private final int value;
        @JsonCreator
        public CreatorBean(@JsonProperty("id") String id, @JsonProperty("value") int value) {
            this.id = id;
            this.value = value;
        }
        public String getId() { return id; }
        public int getValue() { return value; }
    }

    @JsonIgnoreProperties({"secret"})
    public static class IgnorePropsBean {
        private String name;
        public IgnorePropsBean() { }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class AnySetterBean {
        private String known;
        private Map<String, Object> extra = new HashMap<String, Object>();
        public AnySetterBean() { }
        public String getKnown() { return known; }
        public void setKnown(String known) { this.known = known; }
        @JsonAnySetter
        public void addExtra(String key, Object value) { extra.put(key, value); }
        public Map<String, Object> getExtra() { return extra; }
    }

    public static class BooleanBean {
        private boolean flag;
        public BooleanBean() { }
        public boolean isFlag() { return flag; }
        public void setFlag(boolean flag) { this.flag = flag; }
    }

    public static class EnumBean {
        public enum Color { RED, GREEN, BLUE }
        private Color color;
        public EnumBean() { }
        public Color getColor() { return color; }
        public void setColor(Color color) { this.color = color; }
    }

    public static class ArrayBean {
        private String[] items;
        public ArrayBean() { }
        public String[] getItems() { return items; }
        public void setItems(String[] items) { this.items = items; }
    }

    public static class UnwrappedBean {
        private String outer;
        private SimpleBean inner;
        public UnwrappedBean() { }
        public String getOuter() { return outer; }
        public void setOuter(String outer) { this.outer = outer; }
        @JsonUnwrapped
        public SimpleBean getInner() { return inner; }
        public void setInner(SimpleBean inner) { this.inner = inner; }
    }

    public static class BeanListBean {
        private List<SimpleBean> beans;
        public BeanListBean() { }
        public List<SimpleBean> getBeans() { return beans; }
        public void setBeans(List<SimpleBean> beans) { this.beans = beans; }
    }

    // Covers vanillaDeserialize normal path: field found, prop.deserializeAndSet called
    @Test
    public void testDeserialize_simpleObject_setsProperties() throws Throwable {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        SimpleBean bean = mapper.readValue(json, SimpleBean.class);
        assertEquals("Alice", bean.getName());
        assertEquals(30, bean.getAge());
    }

    // Covers empty object: hasTokenId(FIELD_NAME) false, defaults kept
    @Test
    public void testDeserialize_emptyObject_defaultValues() throws Throwable {
        SimpleBean bean = mapper.readValue("{}", SimpleBean.class);
        assertNull(bean.getName());
        assertEquals(0, bean.getAge());
    }

    // Covers one property set, other remains default
    @Test
    public void testDeserialize_missingProperty_keepsDefault() throws Throwable {
        SimpleBean bean = mapper.readValue("{\"name\":\"Bob\"}", SimpleBean.class);
        assertEquals("Bob", bean.getName());
        assertEquals(0, bean.getAge());
    }

    // Covers handleUnknownVanilla branch with FAIL_ON_UNKNOWN_PROPERTIES enabled (default)
    @Test
    public void testDeserialize_unknownProperty_throwsException() throws Throwable {
        String json = "{\"name\":\"Bob\",\"extra\":\"x\"}";
        try {
            mapper.readValue(json, SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    // Covers handleUnknownVanilla branch when ignoring unknown properties is allowed
    @Test
    public void testDeserialize_disableFailOnUnknown_ignoresUnknown() throws Throwable {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = "{\"name\":\"Bob\",\"extra\":\"x\"}";
        SimpleBean bean = mapper.readValue(json, SimpleBean.class);
        assertEquals("Bob", bean.getName());
    }

    // Covers nested-object property deserialization (recursive deserializeFromObject)
    @Test
    public void testDeserialize_nestedObject_setsInnerProperties() throws Throwable {
        String json = "{\"label\":\"L\",\"inner\":{\"name\":\"X\",\"age\":7}}";
        NestedBean bean = mapper.readValue(json, NestedBean.class);
        assertEquals("L", bean.getLabel());
        assertNotNull(bean.getInner());
        assertEquals("X", bean.getInner().getName());
        assertEquals(7, bean.getInner().getAge());
    }

    // Covers explicit null value assigned to object property
    @Test
    public void testDeserialize_nestedObject_nullInner() throws Throwable {
        String json = "{\"inner\":null,\"label\":\"L2\"}";
        NestedBean bean = mapper.readValue(json, NestedBean.class);
        assertNull(bean.getInner());
        assertEquals("L2", bean.getLabel());
    }

    // Covers list property with multiple elements
    @Test
    public void testDeserialize_listProperty_populatesList() throws Throwable {
        String json = "{\"items\":[\"a\",\"b\",\"c\"]}";
        ListBean bean = mapper.readValue(json, ListBean.class);
        assertNotNull(bean.getItems());
        assertEquals(3, bean.getItems().size());
        assertEquals("b", bean.getItems().get(1));
    }

    // Covers list property with zero elements (loop 0 rounds)
    @Test
    public void testDeserialize_listProperty_emptyList() throws Throwable {
        String json = "{\"items\":[]}";
        ListBean bean = mapper.readValue(json, ListBean.class);
        assertNotNull(bean.getItems());
        assertEquals(0, bean.getItems().size());
    }

    // Covers map property population
    @Test
    public void testDeserialize_mapProperty_populatesMap() throws Throwable {
        String json = "{\"values\":{\"one\":1,\"two\":2}}";
        MapBean bean = mapper.readValue(json, MapBean.class);
        assertNotNull(bean.getValues());
        assertEquals(Integer.valueOf(1), bean.getValues().get("one"));
        assertEquals(Integer.valueOf(2), bean.getValues().get("two"));
    }

    // Covers empty map (loop 0 rounds)
    @Test
    public void testDeserialize_emptyMap() throws Throwable {
        String json = "{\"values\":{}}";
        MapBean bean = mapper.readValue(json, MapBean.class);
        assertNotNull(bean.getValues());
        assertEquals(0, bean.getValues().size());
    }

    // Covers property-based creator path (_deserializeUsingPropertyBased) full set
    @Test
    public void testDeserialize_creatorBased_setsFields() throws Throwable {
        String json = "{\"id\":\"abc\",\"value\":42}";
        CreatorBean bean = mapper.readValue(json, CreatorBean.class);
        assertEquals("abc", bean.getId());
        assertEquals(42, bean.getValue());
    }

    // Covers creator property buffer default value when property missing
    @Test
    public void testDeserialize_creatorBased_missingProperty_defaultsToZero() throws Throwable {
        String json = "{\"id\":\"only-id\"}";
        CreatorBean bean = mapper.readValue(json, CreatorBean.class);
        assertEquals("only-id", bean.getId());
        assertEquals(0, bean.getValue());
    }

    // Covers ignorable-properties branch: named property is silently dropped
    @Test
    public void testDeserialize_ignoreProperties_ignoresSecret() throws Throwable {
        String json = "{\"name\":\"Jack\",\"secret\":\"topsecret\"}";
        IgnorePropsBean bean = mapper.readValue(json, IgnorePropsBean.class);
        assertEquals("Jack", bean.getName());
    }

    // Covers any-setter branch capturing otherwise-unknown properties
    @Test
    public void testDeserialize_anySetter_capturesExtraProperties() throws Throwable {
        String json = "{\"known\":\"K\",\"extra1\":\"v1\",\"extra2\":\"v2\"}";
        AnySetterBean bean = mapper.readValue(json, AnySetterBean.class);
        assertEquals("K", bean.getKnown());
        assertEquals("v1", bean.getExtra().get("extra1"));
        assertEquals("v2", bean.getExtra().get("extra2"));
    }

    // Covers boolean property set to true
    @Test
    public void testDeserialize_booleanProperty_true() throws Throwable {
        BooleanBean bean = mapper.readValue("{\"flag\":true}", BooleanBean.class);
        assertTrue(bean.isFlag());
    }

    // Covers boolean property set to false (default/explicit)
    @Test
    public void testDeserialize_booleanProperty_false() throws Throwable {
        BooleanBean bean = mapper.readValue("{\"flag\":false}", BooleanBean.class);
        assertFalse(bean.isFlag());
    }

    // Covers enum property with a valid constant name
    @Test
    public void testDeserialize_enumProperty_validValue() throws Throwable {
        EnumBean bean = mapper.readValue("{\"color\":\"GREEN\"}", EnumBean.class);
        assertEquals(EnumBean.Color.GREEN, bean.getColor());
    }

    // Covers enum property with invalid constant, error path via wrapAndThrow
    @Test
    public void testDeserialize_enumProperty_invalidValue_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"color\":\"PURPLE\"}", EnumBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    // Covers array property population
    @Test
    public void testDeserialize_arrayProperty_populatesArray() throws Throwable {
        String json = "{\"items\":[\"x\",\"y\"]}";
        ArrayBean bean = mapper.readValue(json, ArrayBean.class);
        assertNotNull(bean.getItems());
        assertEquals(2, bean.getItems().length);
        assertEquals("y", bean.getItems()[1]);
    }

    // Covers START_ARRAY path in _deserializeOther -> deserializeFromArray without a creator
    @Test
    public void testDeserialize_fromJsonArray_noCreator_throwsException() throws Throwable {
        try {
            mapper.readValue("[1,2,3]", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    // Covers VALUE_STRING path in _deserializeOther -> deserializeFromString without string creator
    @Test
    public void testDeserialize_fromJsonString_noCreator_throwsException() throws Throwable {
        try {
            mapper.readValue("\"hello\"", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    // Covers repeated field name handling: later value overwrites earlier one
    @Test
    public void testDeserialize_duplicateFieldNames_lastValueWins() throws Throwable {
        String json = "{\"name\":\"A\",\"age\":5,\"name\":\"B\"}";
        SimpleBean bean = mapper.readValue(json, SimpleBean.class);
        assertEquals("B", bean.getName());
        assertEquals(5, bean.getAge());
    }

    // Covers deserializeWithUnwrapped: flattened fields routed to unwrapped sub-bean
    @Test
    public void testDeserialize_unwrappedProperty_flattensFields() throws Throwable {
        String json = "{\"outer\":\"X\",\"name\":\"Y\",\"age\":5}";
        UnwrappedBean bean = mapper.readValue(json, UnwrappedBean.class);
        assertEquals("X", bean.getOuter());
        assertNotNull(bean.getInner());
        assertEquals("Y", bean.getInner().getName());
        assertEquals(5, bean.getInner().getAge());
    }

    // Covers negative int value assignment (boundary value)
    @Test
    public void testDeserialize_negativeIntProperty() throws Throwable {
        SimpleBean bean = mapper.readValue("{\"age\":-5}", SimpleBean.class);
        assertEquals(-5, bean.getAge());
    }

    // Covers max int boundary value assignment
    @Test
    public void testDeserialize_maxIntProperty() throws Throwable {
        String json = "{\"age\":2147483647}";
        SimpleBean bean = mapper.readValue(json, SimpleBean.class);
        assertEquals(Integer.MAX_VALUE, bean.getAge());
    }

    // Covers unicode escape handling in String property value
    @Test
    public void testDeserialize_unicodeEscapeInString() throws Throwable {
        String json = "{\"name\":\"caf\\u00e9\"}";
        SimpleBean bean = mapper.readValue(json, SimpleBean.class);
        assertEquals("caf\u00e9", bean.getName());
    }

    // Covers whitespace-only string value (edge input)
    @Test
    public void testDeserialize_whitespaceStringProperty() throws Throwable {
        String json = "{\"name\":\" \"}";
        SimpleBean bean = mapper.readValue(json, SimpleBean.class);
        assertEquals(" ", bean.getName());
    }

    // Covers repeated independent invocations produce independent bean instances/state
    @Test
    public void testDeserialize_multipleBeansSequentially_independentState() throws Throwable {
        SimpleBean first = mapper.readValue("{\"name\":\"One\",\"age\":1}", SimpleBean.class);
        SimpleBean second = mapper.readValue("{\"name\":\"Two\",\"age\":2}", SimpleBean.class);
        assertEquals("One", first.getName());
        assertEquals(1, first.getAge());
        assertEquals("Two", second.getName());
        assertEquals(2, second.getAge());
    }

    // Covers nested list of beans: each array element fully deserialized
    @Test
    public void testDeserialize_nestedListOfBeans_populatesEachElement() throws Throwable {
        String json = "{\"beans\":[{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}]}";
        BeanListBean bean = mapper.readValue(json, BeanListBean.class);
        assertNotNull(bean.getBeans());
        assertEquals(2, bean.getBeans().size());
        assertEquals("A", bean.getBeans().get(0).getName());
        assertEquals(1, bean.getBeans().get(0).getAge());
        assertEquals("B", bean.getBeans().get(1).getName());
        assertEquals(2, bean.getBeans().get(1).getAge());
    }
}
