package com.fasterxml.jackson.databind.ser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class BeanPropertyWriterClaudeTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    public static class SimpleBean {
        private String name;
        private int value;
        public SimpleBean() {}
        public SimpleBean(String name, int value) { this.name = name; this.value = value; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    public static class NullableBean {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NonNullBean {
        private String a;
        public String getA() { return a; }
        public void setA(String a) { this.a = a; }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class NonEmptyStringBean {
        private String text;
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class NonEmptyListBean {
        private List<String> items;
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class NonEmptyMapBean {
        private Map<String, String> data;
        public Map<String, String> getData() { return data; }
        public void setData(Map<String, String> data) { this.data = data; }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class NonDefaultStringBean {
        private String status = "ACTIVE";
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class SelfRefBean {
        private SelfRefBean self;
        public SelfRefBean getSelf() { return self; }
        public void setSelf(SelfRefBean self) { this.self = self; }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "id")
    public static class IdentitySelfRefBean {
        private int id;
        private IdentitySelfRefBean self;
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public IdentitySelfRefBean getSelf() { return self; }
        public void setSelf(IdentitySelfRefBean self) { this.self = self; }
    }

    @JsonPropertyOrder({"first", "second", "third"})
    public static class ThreePropertyBean {
        private String first;
        private String second;
        private String third;
        public String getFirst() { return first; }
        public void setFirst(String first) { this.first = first; }
        public String getSecond() { return second; }
        public void setSecond(String second) { this.second = second; }
        public String getThird() { return third; }
        public void setThird(String third) { this.third = third; }
    }

    public static class Address {
        private String city;
        private String street;
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }

    public static class PersonWithPrefixedUnwrapped {
        private String name;
        private Address address;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        @JsonUnwrapped(prefix = "addr_")
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    public static class PersonWithPlainUnwrapped {
        private String name;
        private Address address;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        @JsonUnwrapped
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"name", "age"})
    public static class ArrayShapeNullableBean {
        private String name;
        private int age;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"x", "y", "z"})
    public static class ArrayShapeThreeBean {
        private int x;
        private int y;
        private int z;
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public int getZ() { return z; }
        public void setZ(int z) { this.z = z; }
    }

    public static class PublicView {}
    public static class InternalView {}

    public static class ViewedBean {
        private String publicField;
        private String internalField;
        @JsonView(PublicView.class)
        public String getPublicField() { return publicField; }
        public void setPublicField(String publicField) { this.publicField = publicField; }
        @JsonView(InternalView.class)
        public String getInternalField() { return internalField; }
        public void setInternalField(String internalField) { this.internalField = internalField; }
    }

    public static class IntBean {
        private int value;
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    // Covers serializeAsField: value non-null, no suppression -> writes field name and value
    @Test
    public void testSerializeAsField_normalProperty_includesNameAndValue() throws Throwable {
        SimpleBean bean = new SimpleBean("John", 42);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"name\":\"John\""));
        assertTrue(json.contains("\"value\":42"));
    }

    // Covers serializeAsField: value == null branch, _nullSerializer != null (default ALWAYS include)
    @Test
    public void testSerializeAsField_nullValueDefaultInclude_writesNullField() throws Throwable {
        NullableBean bean = new NullableBean();
        bean.setName(null);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"name\":null"));
    }

    // Covers serializeAsField: value == null, _nullSerializer == null (NON_NULL) -> field omitted
    @Test
    public void testSerializeAsField_nullValueNonNullInclude_omitsField() throws Throwable {
        NonNullBean bean = new NonNullBean();
        bean.setA(null);
        String json = mapper.writeValueAsString(bean);
        assertFalse(json.contains("\"a\""));
    }

    // Covers serializeAsField: NON_NULL with non-null value -> field included
    @Test
    public void testSerializeAsField_nonNullValueNonNullInclude_includesField() throws Throwable {
        NonNullBean bean = new NonNullBean();
        bean.setA("hello");
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"a\":\"hello\""));
    }

    // Covers serializeAsField: _suppressableValue == MARKER_FOR_EMPTY, ser.isEmpty(value) true -> omitted
    @Test
    public void testSerializeAsField_emptyStringNonEmptyInclude_omitsField() throws Throwable {
        NonEmptyStringBean bean = new NonEmptyStringBean();
        bean.setText("");
        String json = mapper.writeValueAsString(bean);
        assertFalse(json.contains("\"text\""));
    }

    // Covers serializeAsField: MARKER_FOR_EMPTY, ser.isEmpty(value) false -> included
    @Test
    public void testSerializeAsField_nonEmptyStringNonEmptyInclude_includesField() throws Throwable {
        NonEmptyStringBean bean = new NonEmptyStringBean();
        bean.setText("hi");
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"text\":\"hi\""));
    }

    // Covers serializeAsField: NON_EMPTY with empty List -> suppressed
    @Test
    public void testSerializeAsField_emptyListNonEmptyInclude_omitsField() throws Throwable {
        NonEmptyListBean bean = new NonEmptyListBean();
        bean.setItems(new ArrayList<String>());
        String json = mapper.writeValueAsString(bean);
        assertFalse(json.contains("\"items\""));
    }

    // Covers serializeAsField: NON_EMPTY with non-empty List -> included
    @Test
    public void testSerializeAsField_nonEmptyListNonEmptyInclude_includesField() throws Throwable {
        NonEmptyListBean bean = new NonEmptyListBean();
        List<String> list = new ArrayList<String>();
        list.add("x");
        bean.setItems(list);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"items\":[\"x\"]"));
    }

    // Covers serializeAsField: NON_EMPTY with empty Map -> suppressed
    @Test
    public void testSerializeAsField_emptyMapNonEmptyInclude_omitsField() throws Throwable {
        NonEmptyMapBean bean = new NonEmptyMapBean();
        bean.setData(new HashMap<String, String>());
        String json = mapper.writeValueAsString(bean);
        assertFalse(json.contains("\"data\""));
    }

    // Covers serializeAsField: NON_EMPTY with non-empty Map -> included
    @Test
    public void testSerializeAsField_nonEmptyMapNonEmptyInclude_includesField() throws Throwable {
        NonEmptyMapBean bean = new NonEmptyMapBean();
        Map<String, String> map = new HashMap<String, String>();
        map.put("k", "v");
        bean.setData(map);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"data\":{\"k\":\"v\"}"));
    }

    // Covers serializeAsField: _suppressableValue.equals(value) true branch (NON_DEFAULT, matches default) -> omitted
    @Test
    public void testSerializeAsField_defaultStringNonDefaultInclude_omitsField() throws Throwable {
        NonDefaultStringBean bean = new NonDefaultStringBean();
        String json = mapper.writeValueAsString(bean);
        assertFalse(json.contains("\"status\""));
    }

    // Covers serializeAsField: _suppressableValue.equals(value) false branch (NON_DEFAULT, differs) -> included
    @Test
    public void testSerializeAsField_nonDefaultStringNonDefaultInclude_includesField() throws Throwable {
        NonDefaultStringBean bean = new NonDefaultStringBean();
        bean.setStatus("INACTIVE");
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"status\":\"INACTIVE\""));
    }

    // Covers serializeAsField: value == bean (self reference), ser.usesObjectId() false -> throws JsonMappingException
    @Test
    public void testSerializeAsField_selfReference_throwsJsonMappingException() throws Throwable {
        SelfRefBean bean = new SelfRefBean();
        bean.setSelf(bean);
        try {
            mapper.writeValueAsString(bean);
            fail("expected JsonMappingException due to direct self-reference cycle");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().contains("cycle"));
        }
    }

    // Covers _handleSelfReference: ser.usesObjectId() true -> returns normally, no exception
    @Test
    public void testSerializeAsField_selfReferenceWithObjectId_doesNotThrow() throws Throwable {
        IdentitySelfRefBean bean = new IdentitySelfRefBean();
        bean.setId(1);
        bean.setSelf(bean);
        String json = mapper.writeValueAsString(bean);
        assertNotNull(json);
        assertTrue(json.contains("\"id\""));
    }

    // Covers serializeAsField called for multiple properties in declared/configured order
    @Test
    public void testSerializeAsField_multiplePropertiesPreserveDeclarationOrder() throws Throwable {
        ThreePropertyBean bean = new ThreePropertyBean();
        bean.setFirst("a");
        bean.setSecond("b");
        bean.setThird("c");
        String json = mapper.writeValueAsString(bean);
        int idxFirst = json.indexOf("\"first\"");
        int idxSecond = json.indexOf("\"second\"");
        int idxThird = json.indexOf("\"third\"");
        assertTrue(idxFirst >= 0 && idxSecond > idxFirst && idxThird > idxSecond);
    }

    // Covers serializeAsField with unicode content, string value written verbatim
    @Test
    public void testSerializeAsField_unicodeStringValue_preservesContent() throws Throwable {
        SimpleBean bean = new SimpleBean("\u65e5\u672c\u8a9e", 1);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\u65e5\u672c\u8a9e"));
    }

    // Covers serializeAsField with negative int edge value
    @Test
    public void testSerializeAsField_negativeIntValue_serializesCorrectly() throws Throwable {
        IntBean bean = new IntBean();
        bean.setValue(-1);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"value\":-1"));
    }

    // Covers serializeAsField with Integer.MAX_VALUE edge value
    @Test
    public void testSerializeAsField_maxIntValue_serializesCorrectly() throws Throwable {
        IntBean bean = new IntBean();
        bean.setValue(Integer.MAX_VALUE);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"value\":" + Integer.MAX_VALUE));
    }

    // Covers rename(): newName differs from original -> new BeanPropertyWriter created with prefix applied
    @Test
    public void testRename_withPrefix_appliesPrefixToUnwrappedFieldNames() throws Throwable {
        PersonWithPrefixedUnwrapped person = new PersonWithPrefixedUnwrapped();
        person.setName("Alice");
        Address addr = new Address();
        addr.setCity("NYC");
        addr.setStreet("5th Ave");
        person.setAddress(addr);
        String json = mapper.writeValueAsString(person);
        assertTrue(json.contains("\"addr_city\":\"NYC\""));
        assertTrue(json.contains("\"addr_street\":\"5th Ave\""));
    }

    // Covers rename(): newName.equals(_name.toString()) -> returns same writer (name unchanged)
    @Test
    public void testRename_noPrefixNoSuffix_keepsOriginalFieldNames() throws Throwable {
        PersonWithPlainUnwrapped person = new PersonWithPlainUnwrapped();
        person.setName("Bob");
        Address addr = new Address();
        addr.setCity("LA");
        addr.setStreet("Main St");
        person.setAddress(addr);
        String json = mapper.writeValueAsString(person);
        assertTrue(json.contains("\"city\":\"LA\""));
        assertTrue(json.contains("\"street\":\"Main St\""));
    }

    // Covers serializeAsColumn: value == null branch must write null and return (missing return causes NPE on value.getClass())
    @Test
    public void testSerializeAsColumn_nullValueArrayShape_writesNullWithoutException() throws Throwable {
        ArrayShapeNullableBean bean = new ArrayShapeNullableBean();
        bean.setName(null);
        bean.setAge(30);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.startsWith("["));
        assertTrue(json.contains("null"));
        assertTrue(json.contains("30"));
    }

    // Covers serializeAsColumn: value non-null branch, writes value without field name
    @Test
    public void testSerializeAsColumn_nonNullValueArrayShape_writesValue() throws Throwable {
        ArrayShapeNullableBean bean = new ArrayShapeNullableBean();
        bean.setName("Carl");
        bean.setAge(25);
        String json = mapper.writeValueAsString(bean);
        assertEquals("[\"Carl\",25]", json);
    }

    // Covers serializeAsColumn for multiple non-null properties, preserving configured order
    @Test
    public void testSerializeAsColumn_arrayShapeMultipleProperties_preservesOrder() throws Throwable {
        ArrayShapeThreeBean bean = new ArrayShapeThreeBean();
        bean.setX(1);
        bean.setY(2);
        bean.setZ(3);
        String json = mapper.writeValueAsString(bean);
        assertEquals("[1,2,3]", json);
    }

    // Covers view filtering: no active view set -> all properties (with or without @JsonView) are included
    @Test
    public void testJsonView_noViewSpecified_includesAllProperties() throws Throwable {
        ViewedBean bean = new ViewedBean();
        bean.setPublicField("pub");
        bean.setInternalField("int");
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"publicField\":\"pub\""));
        assertTrue(json.contains("\"internalField\":\"int\""));
    }

    // Covers _includeInViews filtering: active view matches only publicField's view
    @Test
    public void testJsonView_filterByPublicView_includesOnlyPublicField() throws Throwable {
        ViewedBean bean = new ViewedBean();
        bean.setPublicField("pub");
        bean.setInternalField("int");
        ObjectWriter writer = mapper.writerWithView(PublicView.class);
        String json = writer.writeValueAsString(bean);
        assertTrue(json.contains("\"publicField\":\"pub\""));
        assertFalse(json.contains("\"internalField\""));
    }

    // Covers _includeInViews filtering: active view matches only internalField's view
    @Test
    public void testJsonView_filterByInternalView_includesOnlyInternalField() throws Throwable {
        ViewedBean bean = new ViewedBean();
        bean.setPublicField("pub");
        bean.setInternalField("int");
        ObjectWriter writer = mapper.writerWithView(InternalView.class);
        String json = writer.writeValueAsString(bean);
        assertTrue(json.contains("\"internalField\":\"int\""));
        assertFalse(json.contains("\"publicField\""));
    }

    // Covers serializeAsField with default int (0) and ALWAYS inclusion -> still written (contrast to NON_DEFAULT)
    @Test
    public void testSerializeAsField_zeroDefaultInt_writtenWhenAlwaysInclude() throws Throwable {
        IntBean bean = new IntBean();
        bean.setValue(0);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"value\":0"));
    }

    // Covers serializeAsField with Integer.MIN_VALUE edge value
    @Test
    public void testSerializeAsField_minIntValue_serializesCorrectly() throws Throwable {
        IntBean bean = new IntBean();
        bean.setValue(Integer.MIN_VALUE);
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"value\":" + Integer.MIN_VALUE));
    }
}
