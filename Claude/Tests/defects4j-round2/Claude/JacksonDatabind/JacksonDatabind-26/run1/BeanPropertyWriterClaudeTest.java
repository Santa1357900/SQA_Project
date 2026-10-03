package com.fasterxml.jackson.databind.ser;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;

public class BeanPropertyWriterClaudeTest {

    private BeanPropertyWriter base;
    private BeanPropertyWriter namedWriter;
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        base = new BeanPropertyWriter();
        namedWriter = new BeanPropertyWriter(base, new SerializedString("myProp"));
        mapper = new ObjectMapper();
    }

    // ---- POJOs used for ObjectMapper integration tests ----

    public static class SimpleBean {
        private String name;
        private int age;
        public SimpleBean() { }
        public SimpleBean(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    public static class NullableBean {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class NonNullBean {
        private String value;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class NonEmptyBean {
        private String text;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class SelfRefBean {
        public SelfRefBean getSelf() { return this; }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"a", "b"})
    public static class ArrayBean {
        public String a = "x";
        public int b = 5;
    }

    // ---- Accessor / mutator tests using directly-constructed writer ----

    // covers getName() built from configured SerializedString
    @Test
    public void testGetName_returnsConfiguredName() throws Throwable {
        assertEquals("myProp", namedWriter.getName());
    }

    // covers getFullName() constructing PropertyName from _name
    @Test
    public void testGetFullName_returnsPropertyNameWithSameSimpleName() throws Throwable {
        assertEquals("myProp", namedWriter.getFullName().getSimpleName());
    }

    // covers getSerializedName() accessor
    @Test
    public void testGetSerializedName_returnsSameSerializedString() throws Throwable {
        assertEquals("myProp", namedWriter.getSerializedName().getValue());
    }

    // covers toString() virtual branch (no field/method) and "no static serializer" branch
    @Test
    public void testToString_virtualNoSerializer_containsExpectedParts() throws Throwable {
        String s = namedWriter.toString();
        assertTrue(s.contains("myProp"));
        assertTrue(s.contains("virtual"));
        assertTrue(s.contains("no static serializer"));
    }

    // covers wouldConflictWithName() true branch: null wrapperName, matching simple name, no namespace
    @Test
    public void testWouldConflictWithName_matchingNameNoNamespace_true() throws Throwable {
        assertTrue(namedWriter.wouldConflictWithName(new PropertyName("myProp")));
    }

    // covers wouldConflictWithName() false branch: different simple name
    @Test
    public void testWouldConflictWithName_differentName_false() throws Throwable {
        assertFalse(namedWriter.wouldConflictWithName(new PropertyName("other")));
    }

    // covers getType() returning declared type (null when unset)
    @Test
    public void testGetType_unset_returnsNull() throws Throwable {
        assertNull(namedWriter.getType());
    }

    // covers getWrapperName() returning null when unset
    @Test
    public void testGetWrapperName_unset_returnsNull() throws Throwable {
        assertNull(namedWriter.getWrapperName());
    }

    // covers getMetadata() returning null when unset
    @Test
    public void testGetMetadata_unset_returnsNull() throws Throwable {
        assertNull(namedWriter.getMetadata());
    }

    // covers getMember() returning null when unset
    @Test
    public void testGetMember_unset_returnsNull() throws Throwable {
        assertNull(namedWriter.getMember());
    }

    // covers getAnnotation() member==null branch
    @Test
    public void testGetAnnotation_memberNull_returnsNull() throws Throwable {
        assertNull(namedWriter.getAnnotation(Deprecated.class));
    }

    // covers getContextAnnotation() contextAnnotations==null branch
    @Test
    public void testGetContextAnnotation_contextAnnotationsNull_returnsNull() throws Throwable {
        assertNull(namedWriter.getContextAnnotation(Deprecated.class));
    }

    // covers findFormatOverrides() first lookup (member null, cache NO_FORMAT) and cached subsequent call
    @Test
    public void testFindFormatOverrides_memberNull_returnsNullBothCallsCached() throws Throwable {
        assertNull(namedWriter.findFormatOverrides(null));
        assertNull(namedWriter.findFormatOverrides(null));
    }

    // covers isVirtual() default false
    @Test
    public void testIsVirtual_defaultFalse() throws Throwable {
        assertFalse(namedWriter.isVirtual());
    }

    // covers isUnwrapping() default false
    @Test
    public void testIsUnwrapping_defaultFalse() throws Throwable {
        assertFalse(namedWriter.isUnwrapping());
    }

    // covers willSuppressNulls() default false from protected no-arg constructor
    @Test
    public void testWillSuppressNulls_defaultFalse() throws Throwable {
        assertFalse(namedWriter.willSuppressNulls());
    }

    // covers hasSerializer() false before any assignment
    @Test
    public void testHasSerializer_falseInitially() throws Throwable {
        assertFalse(namedWriter.hasSerializer());
    }

    // covers assignSerializer() setting serializer then hasSerializer()/getSerializer()
    @Test
    public void testAssignSerializer_setsSerializer() throws Throwable {
        JsonSerializer<Object> ser = newDummySerializer();
        namedWriter.assignSerializer(ser);
        assertTrue(namedWriter.hasSerializer());
        assertSame(ser, namedWriter.getSerializer());
    }

    // covers assignSerializer() re-assigning same instance does not throw
    @Test
    public void testAssignSerializer_sameInstanceTwice_noException() throws Throwable {
        JsonSerializer<Object> ser = newDummySerializer();
        namedWriter.assignSerializer(ser);
        namedWriter.assignSerializer(ser);
        assertSame(ser, namedWriter.getSerializer());
    }

    // covers assignSerializer() throwing IllegalStateException for conflicting override
    @Test
    public void testAssignSerializer_differentInstanceTwice_throwsIllegalStateException() throws Throwable {
        JsonSerializer<Object> ser1 = newDummySerializer();
        JsonSerializer<Object> ser2 = newDummySerializer();
        namedWriter.assignSerializer(ser1);
        try {
            namedWriter.assignSerializer(ser2);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // covers hasNullSerializer() false before assignment
    @Test
    public void testHasNullSerializer_falseInitially() throws Throwable {
        assertFalse(namedWriter.hasNullSerializer());
    }

    // covers assignNullSerializer() setting null-value serializer
    @Test
    public void testAssignNullSerializer_setsNullSerializer() throws Throwable {
        JsonSerializer<Object> nullSer = newDummySerializer();
        namedWriter.assignNullSerializer(nullSer);
        assertTrue(namedWriter.hasNullSerializer());
    }

    // covers assignNullSerializer() throwing IllegalStateException for conflicting override
    @Test
    public void testAssignNullSerializer_differentInstanceTwice_throwsIllegalStateException() throws Throwable {
        JsonSerializer<Object> nullSer1 = newDummySerializer();
        JsonSerializer<Object> nullSer2 = newDummySerializer();
        namedWriter.assignNullSerializer(nullSer1);
        try {
            namedWriter.assignNullSerializer(nullSer2);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // covers assignTypeSerializer() setter path and getTypeSerializer() accessor
    @Test
    public void testAssignTypeSerializer_null_getTypeSerializerNull() throws Throwable {
        namedWriter.assignTypeSerializer(null);
        assertNull(namedWriter.getTypeSerializer());
    }

    // covers getSerializationType() returning null when unset
    @Test
    public void testGetSerializationType_unset_returnsNull() throws Throwable {
        assertNull(namedWriter.getSerializationType());
    }

    // covers getRawSerializationType() null-check branch when serialization type unset
    @Test
    public void testGetRawSerializationType_unset_returnsNull() throws Throwable {
        assertNull(namedWriter.getRawSerializationType());
    }

    // covers getViews() returning null when unset
    @Test
    public void testGetViews_unset_returnsNull() throws Throwable {
        assertNull(namedWriter.getViews());
    }

    // covers getGenericPropertyType() both accessorMethod and field null -> returns null
    @Test
    public void testGetGenericPropertyType_bothNull_returnsNull() throws Throwable {
        assertNull(namedWriter.getGenericPropertyType());
    }

    // covers getInternalSetting() returning null for unset key (map null)
    @Test
    public void testGetInternalSetting_unsetKey_returnsNull() throws Throwable {
        assertNull(namedWriter.getInternalSetting("k"));
    }

    // covers setInternalSetting() returning null on first assignment and storing value
    @Test
    public void testSetInternalSetting_firstAssignment_returnsNullAndStoresValue() throws Throwable {
        Object old = namedWriter.setInternalSetting("k", "v1");
        assertNull(old);
        assertEquals("v1", namedWriter.getInternalSetting("k"));
    }

    // covers setInternalSetting() returning previous value on overwrite
    @Test
    public void testSetInternalSetting_overwrite_returnsPreviousValue() throws Throwable {
        namedWriter.setInternalSetting("k", "v1");
        Object old = namedWriter.setInternalSetting("k", "v2");
        assertEquals("v1", old);
        assertEquals("v2", namedWriter.getInternalSetting("k"));
    }

    // covers removeInternalSetting() existing key returns value and clears map
    @Test
    public void testRemoveInternalSetting_existingKey_returnsValueAndClears() throws Throwable {
        namedWriter.setInternalSetting("k", "v1");
        Object removed = namedWriter.removeInternalSetting("k");
        assertEquals("v1", removed);
        assertNull(namedWriter.getInternalSetting("k"));
    }

    // covers removeInternalSetting() when internal settings map was never created
    @Test
    public void testRemoveInternalSetting_neverSet_returnsNull() throws Throwable {
        assertNull(namedWriter.removeInternalSetting("absent"));
    }

    // ---- Integration tests via ObjectMapper exercising serializeAsField/serializeAsElement ----

    // covers serializeAsField() normal path writing string and int property values
    @Test
    public void testSerializeAsField_basicBean_writesFields() throws Throwable {
        String json = mapper.writeValueAsString(new SimpleBean("Alice", 30));
        assertTrue(json.contains("\"name\":\"Alice\""));
        assertTrue(json.contains("\"age\":30"));
    }

    // covers serializeAsField() null value with default inclusion -> field written as null
    @Test
    public void testSerializeAsField_nullValueDefaultInclusion_writesNull() throws Throwable {
        String json = mapper.writeValueAsString(new NullableBean());
        assertTrue(json.contains("\"value\":null"));
    }

    // covers serializeAsField() null value with NON_NULL suppression -> early return, field omitted
    @Test
    public void testSerializeAsField_nonNullSuppression_omitsNullField() throws Throwable {
        String json = mapper.writeValueAsString(new NonNullBean());
        assertFalse(json.contains("value"));
    }

    // covers serializeAsField() MARKER_FOR_EMPTY branch: empty string suppressed
    @Test
    public void testSerializeAsField_nonEmptySuppression_emptyString_omitted() throws Throwable {
        NonEmptyBean bean = new NonEmptyBean();
        bean.setText("");
        String json = mapper.writeValueAsString(bean);
        assertFalse(json.contains("\"text\""));
    }

    // covers serializeAsField() MARKER_FOR_EMPTY branch: non-empty string included
    @Test
    public void testSerializeAsField_nonEmptySuppression_nonEmptyString_included() throws Throwable {
        NonEmptyBean bean = new NonEmptyBean();
        bean.setText("hi");
        String json = mapper.writeValueAsString(bean);
        assertTrue(json.contains("\"text\":\"hi\""));
    }

    // covers serializeAsField() direct self-reference handling (FAIL_ON_SELF_REFERENCES enabled by default)
    @Test
    public void testSerializeAsField_directSelfReference_throwsJsonMappingException() throws Throwable {
        try {
            mapper.writeValueAsString(new SelfRefBean());
            fail("expected JsonMappingException due to direct self-reference");
        } catch (JsonMappingException expected) {
        }
    }

    // covers serializeAsElement() path used when bean is serialized as JSON array (no field names written)
    @Test
    public void testSerializeAsElement_arrayShape_producesOrderedArray() throws Throwable {
        String json = mapper.writeValueAsString(new ArrayBean());
        assertEquals("[\"x\",5]", json);
    }

    // ---- helper ----

    private JsonSerializer<Object> newDummySerializer() {
        return new JsonSerializer<Object>() {
            @Override
            public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeNull();
            }
        };
    }
}
