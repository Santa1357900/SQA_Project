package com.fasterxml.jackson.databind.deser;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

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
        public String getName() { return name; }
        public void setName(String n) { name = n; }
        public int getAge() { return age; }
        public void setAge(int a) { age = a; }
    }

    @JsonIgnoreProperties({"secret"})
    public static class BeanIgnoreNamed {
        private String name;
        public String getName() { return name; }
        public void setName(String n) { name = n; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BeanIgnoreAllUnknown {
        private String name;
        public String getName() { return name; }
        public void setName(String n) { name = n; }
    }

    public static class StringCreatorBean {
        private final String value;
        @JsonCreator
        public StringCreatorBean(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    public static class IntCreatorBean {
        private final int value;
        @JsonCreator
        public IntCreatorBean(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    public static class DoubleCreatorBean {
        private final double value;
        @JsonCreator
        public DoubleCreatorBean(double value) { this.value = value; }
        public double getValue() { return value; }
    }

    public static class BooleanCreatorBean {
        private final boolean value;
        @JsonCreator
        public BooleanCreatorBean(boolean value) { this.value = value; }
        public boolean getValue() { return value; }
    }

    public static class ListDelegateBean {
        private final List<String> items;
        @JsonCreator
        public ListDelegateBean(List<String> items) { this.items = items; }
        public List<String> getItems() { return items; }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PointBeanLenient {
        private final int x;
        private final int y;
        @JsonCreator
        public PointBeanLenient(@JsonProperty("x") int x, @JsonProperty("y") int y) {
            this.x = x;
            this.y = y;
        }
        public int getX() { return x; }
        public int getY() { return y; }
    }

    public static class AnySetterBean {
        private String known;
        private Map<String, Object> extra = new HashMap<String, Object>();
        public String getKnown() { return known; }
        public void setKnown(String k) { known = k; }
        @JsonAnySetter
        public void addExtra(String name, Object value) { extra.put(name, value); }
        public Map<String, Object> getExtra() { return extra; }
    }

    public static class ViewOne {}
    public static class ViewTwo {}

    public static class ViewBean {
        @JsonView(ViewOne.class)
        private String a;
        @JsonView(ViewTwo.class)
        private String b;
        public String getA() { return a; }
        public void setA(String v) { a = v; }
        public String getB() { return b; }
        public void setB(String v) { b = v; }
    }

    public static class Address {
        private String city;
        private String zip;
        public String getCity() { return city; }
        public void setCity(String c) { city = c; }
        public String getZip() { return zip; }
        public void setZip(String z) { zip = z; }
    }

    public static class Person {
        private String name;
        @JsonUnwrapped
        private Address address;
        public String getName() { return name; }
        public void setName(String n) { name = n; }
        public Address getAddress() { return address; }
        public void setAddress(Address a) { address = a; }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class Node {
        private int id;
        private String name;
        private Node next;
        public int getId() { return id; }
        public void setId(int i) { id = i; }
        public String getName() { return name; }
        public void setName(String n) { name = n; }
        public Node getNext() { return next; }
        public void setNext(Node n) { next = n; }
    }

    // deserialize(): vanilla path, two normal properties, do-while loop runs twice
    @Test
    public void testDeserialize_twoProperties_vanilla_setsFields() throws Throwable {
        SimpleBean bean = mapper.readValue("{\"name\":\"Bob\",\"age\":30}", SimpleBean.class);
        assertEquals("Bob", bean.getName());
        assertEquals(30, bean.getAge());
    }

    // deserialize(): vanilla path, empty object -> loop body never executes (FIELD_NAME null)
    @Test
    public void testDeserialize_emptyObject_vanilla_defaultValues() throws Throwable {
        SimpleBean bean = mapper.readValue("{}", SimpleBean.class);
        assertNull(bean.getName());
        assertEquals(0, bean.getAge());
    }

    // handleUnknownVanilla: unknown property with FAIL_ON_UNKNOWN_PROPERTIES enabled -> throws
    @Test
    public void testDeserialize_unknownProperty_defaultConfig_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"name\":\"Bob\",\"extra\":1}", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // unknown property must be reported by default
        }
    }

    // handleUnknownVanilla: unknown property ignored when feature disabled
    @Test
    public void testDeserialize_unknownProperty_failOnUnknownDisabled_ignored() throws Throwable {
        ObjectMapper lenient = new ObjectMapper();
        lenient.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SimpleBean bean = lenient.readValue("{\"name\":\"Bob\",\"extra\":1}", SimpleBean.class);
        assertEquals("Bob", bean.getName());
    }

    // _ignoreAllUnknown=true overrides FAIL_ON_UNKNOWN_PROPERTIES, even though it's enabled
    @Test
    public void testDeserialize_classLevelIgnoreUnknownTrue_ignoredEvenWhenFailEnabled() throws Throwable {
        BeanIgnoreAllUnknown bean = mapper.readValue(
                "{\"name\":\"Bob\",\"randomField\":123}", BeanIgnoreAllUnknown.class);
        assertEquals("Bob", bean.getName());
    }

    // named ignorable props set: listed property is skipped without throwing
    @Test
    public void testDeserialize_namedIgnoreProperties_ignoresListedProperty() throws Throwable {
        BeanIgnoreNamed bean = mapper.readValue(
                "{\"name\":\"Bob\",\"secret\":\"hidden\"}", BeanIgnoreNamed.class);
        assertEquals("Bob", bean.getName());
    }

    // _deserializeOther: VALUE_STRING with string creator -> deserializeFromString success
    @Test
    public void testDeserializeFromString_withCreator_buildsInstance() throws Throwable {
        StringCreatorBean bean = mapper.readValue("\"hello\"", StringCreatorBean.class);
        assertEquals("hello", bean.getValue());
    }

    // _deserializeOther: VALUE_STRING without any creator -> mismatch exception
    @Test
    public void testDeserializeFromString_withoutCreator_throwsException() throws Throwable {
        try {
            mapper.readValue("\"hello\"", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // no string creator available
        }
    }

    // _deserializeOther: VALUE_NUMBER_INT with int creator -> deserializeFromNumber success
    @Test
    public void testDeserializeFromNumberInt_withCreator_buildsInstance() throws Throwable {
        IntCreatorBean bean = mapper.readValue("42", IntCreatorBean.class);
        assertEquals(42, bean.getValue());
    }

    // _deserializeOther: VALUE_NUMBER_INT without creator -> mismatch exception
    @Test
    public void testDeserializeFromNumberInt_withoutCreator_throwsException() throws Throwable {
        try {
            mapper.readValue("42", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // no numeric creator available
        }
    }

    // _deserializeOther: VALUE_NUMBER_FLOAT with double creator -> deserializeFromDouble success
    @Test
    public void testDeserializeFromDouble_withCreator_buildsInstance() throws Throwable {
        DoubleCreatorBean bean = mapper.readValue("3.5", DoubleCreatorBean.class);
        assertEquals(3.5, bean.getValue(), 1e-9);
    }

    // _deserializeOther: VALUE_TRUE with boolean creator -> deserializeFromBoolean success
    @Test
    public void testDeserializeFromBoolean_trueWithCreator_buildsInstance() throws Throwable {
        BooleanCreatorBean bean = mapper.readValue("true", BooleanCreatorBean.class);
        assertTrue(bean.getValue());
    }

    // _deserializeOther: VALUE_FALSE with boolean creator -> deserializeFromBoolean success
    @Test
    public void testDeserializeFromBoolean_falseWithCreator_buildsInstance() throws Throwable {
        BooleanCreatorBean bean = mapper.readValue("false", BooleanCreatorBean.class);
        assertFalse(bean.getValue());
    }

    // _deserializeOther: START_ARRAY with array/list-delegate creator -> success
    @Test
    public void testDeserializeFromArray_withListDelegateCreator_buildsInstance() throws Throwable {
        ListDelegateBean bean = mapper.readValue("[\"a\",\"b\"]", ListDelegateBean.class);
        assertEquals(2, bean.getItems().size());
        assertEquals("a", bean.getItems().get(0));
        assertEquals("b", bean.getItems().get(1));
    }

    // _deserializeOther: START_ARRAY without any delegate -> mismatch exception
    @Test
    public void testDeserializeFromArray_withoutDelegate_throwsException() throws Throwable {
        try {
            mapper.readValue("[1,2]", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // array cannot be mapped to plain bean without delegate creator
        }
    }

    // _deserializeUsingPropertyBased: properties supplied in declaration order
    @Test
    public void testDeserializeUsingPropertyBased_inOrder_buildsBean() throws Throwable {
        PointBean p = mapper.readValue("{\"x\":3,\"y\":4}", PointBean.class);
        assertEquals(3, p.getX());
        assertEquals(4, p.getY());
    }

    // _deserializeUsingPropertyBased: creator properties buffered when supplied out of order
    @Test
    public void testDeserializeUsingPropertyBased_outOfOrder_buildsBean() throws Throwable {
        PointBean p = mapper.readValue("{\"y\":4,\"x\":3}", PointBean.class);
        assertEquals(3, p.getX());
        assertEquals(4, p.getY());
    }

    // _deserializeUsingPropertyBased: unknown extra property buffered then rejected by default
    @Test
    public void testDeserializeUsingPropertyBased_unknownProperty_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"x\":1,\"y\":2,\"z\":3}", PointBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // unknown property must be reported by default
        }
    }

    // _deserializeUsingPropertyBased: unknown extra property tolerated when ignoreUnknown=true
    @Test
    public void testDeserializeUsingPropertyBased_unknownPropertyIgnored_whenIgnoreUnknownTrue() throws Throwable {
        PointBeanLenient p = mapper.readValue("{\"x\":1,\"y\":2,\"z\":99}", PointBeanLenient.class);
        assertEquals(1, p.getX());
        assertEquals(2, p.getY());
    }

    // _deserializeUsingPropertyBased: missing creator property falls back to primitive default
    @Test
    public void testDeserializeUsingPropertyBased_missingCreatorProperty_usesTypeDefault() throws Throwable {
        PointBean p = mapper.readValue("{\"x\":5}", PointBean.class);
        assertEquals(5, p.getX());
        assertEquals(0, p.getY());
    }

    // handleUnknownVanilla + _anySetter: unknown properties routed to any-setter and captured
    @Test
    public void testAnySetter_capturesUnknownIntoMap() throws Throwable {
        AnySetterBean bean = mapper.readValue(
                "{\"known\":\"k1\",\"other\":\"v1\",\"other2\":\"v2\"}", AnySetterBean.class);
        assertEquals("k1", bean.getKnown());
        assertEquals("v1", bean.getExtra().get("other"));
        assertEquals("v2", bean.getExtra().get("other2"));
    }

    // deserializeWithView: property visible in active view is set
    @Test
    public void testDeserializeWithView_matchingView_propertyIncluded() throws Throwable {
        ObjectReader reader = mapper.readerFor(ViewBean.class).withView(ViewOne.class);
        ViewBean bean = reader.readValue("{\"a\":\"AAA\",\"b\":\"BBB\"}");
        assertEquals("AAA", bean.getA());
        assertNull(bean.getB());
    }

    // deserializeWithView: property not visible in active view is skipped
    @Test
    public void testDeserializeWithView_nonMatchingView_propertyExcluded() throws Throwable {
        ObjectReader reader = mapper.readerFor(ViewBean.class).withView(ViewTwo.class);
        ViewBean bean = reader.readValue("{\"a\":\"AAA\",\"b\":\"BBB\"}");
        assertNull(bean.getA());
        assertEquals("BBB", bean.getB());
    }

    // deserializeFromObject/_nonStandardCreation: _unwrappedPropertyHandler flattens nested props
    @Test
    public void testDeserializeWithUnwrapped_flattensNestedObject() throws Throwable {
        Person person = mapper.readValue(
                "{\"name\":\"John\",\"city\":\"NYC\",\"zip\":\"10001\"}", Person.class);
        assertEquals("John", person.getName());
        assertNotNull(person.getAddress());
        assertEquals("NYC", person.getAddress().getCity());
        assertEquals("10001", person.getAddress().getZip());
    }

    // deserialize()/_handleTypedObjectId: object-id property resolves reference to earlier bean
    @Test
    public void testDeserializeWithObjectId_resolvesBackwardReference() throws Throwable {
        String json = "[{\"id\":1,\"name\":\"A\",\"next\":null},{\"id\":2,\"name\":\"B\",\"next\":1}]";
        Node[] nodes = mapper.readValue(json, Node[].class);
        assertSame(nodes[0], nodes[1].getNext());
        assertEquals("A", nodes[1].getNext().getName());
    }

    // prop.deserializeAndSet: nested bean property triggers recursive deserialization
    @Test
    public void testDeserialize_nestedBeanProperty_recursiveAssignment() throws Throwable {
        Person person = mapper.readValue(
                "{\"name\":\"Outer\",\"city\":\"LA\",\"zip\":\"90001\"}", Person.class);
        assertEquals("LA", person.getAddress().getCity());
    }

    // deserialize(): explicit JSON null for object-typed property sets field to null
    @Test
    public void testDeserialize_explicitNullProperty_setsFieldNull() throws Throwable {
        SimpleBean bean = mapper.readValue("{\"name\":null,\"age\":5}", SimpleBean.class);
        assertNull(bean.getName());
        assertEquals(5, bean.getAge());
    }
}
