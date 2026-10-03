package com.fasterxml.jackson.databind.deser.std;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.DeserializationFeature;

public class StringCollectionDeserializerClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // Test basic array of strings deserializes into List<String> preserving order
    @Test
    public void testDeserialize_arrayOfStrings_returnsListWithSameOrder() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[\"a\",\"b\",\"c\"]", type);
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
        assertEquals("c", result.get(2));
    }

    // Test empty array covers the 0-iteration loop case, END_ARRAY reached immediately
    @Test
    public void testDeserialize_emptyArray_returnsEmptyList() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[]", type);
        assertTrue(result.isEmpty());
    }

    // Test single string element array covers the 1-iteration loop case
    @Test
    public void testDeserialize_singleElementArray_returnsSingleElementList() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[\"only\"]", type);
        assertEquals(1, result.size());
        assertEquals("only", result.get(0));
    }

    // Test null element in array preserved (VALUE_NULL branch, default _skipNullValues=false)
    @Test
    public void testDeserialize_arrayWithNullValue_nullPreservedInResult() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[\"a\",null,\"b\"]", type);
        assertEquals(3, result.size());
        assertNull(result.get(1));
    }

    // Test numeric element coerced to String via _parseString branch (t != END_ARRAY, != VALUE_NULL)
    @Test
    public void testDeserialize_arrayWithNumber_coercedToString() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[123]", type);
        assertEquals("123", result.get(0));
    }

    // Test boolean elements coerced to string representations
    @Test
    public void testDeserialize_arrayWithBoolean_coercedToString() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[true,false]", type);
        assertEquals("true", result.get(0));
        assertEquals("false", result.get(1));
    }

    // Test non-array scalar with ACCEPT_SINGLE_VALUE_AS_ARRAY disabled throws (canWrap == false branch)
    @Test
    public void testDeserialize_nonArrayScalarWithoutUnwrap_throwsJsonMappingException() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        mapper.disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        try {
            mapper.readValue("\"solo\"", type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // Test non-array scalar wrapped into single-element list when feature enabled (canWrap == true branch)
    @Test
    public void testDeserialize_nonArrayScalarWithUnwrapEnabled_wrapsIntoSingleElementList() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        List<String> result = (List<String>) mapper.readValue("\"solo\"", type);
        assertEquals(1, result.size());
        assertEquals("solo", result.get(0));
    }

    // Test Set<String> collection type also uses this deserializer correctly
    @Test
    public void testDeserialize_setOfStrings_returnsSetWithElements() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(Set.class, String.class);
        Set<String> result = (Set<String>) mapper.readValue("[\"x\",\"y\"]", type);
        assertEquals(2, result.size());
        assertTrue(result.contains("x"));
        assertTrue(result.contains("y"));
    }

    // Test JSON literal null as whole document returns null collection (no array token expected)
    @Test
    public void testDeserialize_jsonNull_returnsNull() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("null", type);
        assertNull(result);
    }

    // Test POJO with List<String> field populated from JSON array property
    @Test
    public void testDeserialize_pojoWithStringListField_populatesField() throws Throwable {
        StringListBean bean = mapper.readValue("{\"values\":[\"p\",\"q\"]}", StringListBean.class);
        assertNotNull(bean.getValues());
        assertEquals(2, bean.getValues().size());
        assertEquals("p", bean.getValues().get(0));
        assertEquals("q", bean.getValues().get(1));
    }

    // Test POJO with empty array field results in empty list (0-iteration loop through property path)
    @Test
    public void testDeserialize_pojoWithEmptyArrayField_emptyList() throws Throwable {
        StringListBean bean = mapper.readValue("{\"values\":[]}", StringListBean.class);
        assertNotNull(bean.getValues());
        assertTrue(bean.getValues().isEmpty());
    }

    // Test array with nested object element causes type mismatch, wrapped into JsonMappingException (catch block)
    @Test
    public void testDeserialize_arrayWithNestedObject_throwsJsonMappingException() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        try {
            mapper.readValue("[{\"a\":1}]", type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // Test array containing nested array element also triggers the wrapped exception path
    @Test
    public void testDeserialize_arrayContainingArray_throwsJsonMappingException() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        try {
            mapper.readValue("[[\"nested\"]]", type);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // Test non-array number wrapped into single-element list when unwrap enabled, exercising non-null non-string coercion
    @Test
    public void testDeserialize_nonArrayNumberWithUnwrapEnabled_wrapsIntoSingleElementList() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        List<String> result = (List<String>) mapper.readValue("123", type);
        assertEquals(1, result.size());
        assertEquals("123", result.get(0));
    }

    // Test whitespace-only string element preserved exactly (no trimming)
    @Test
    public void testDeserialize_arrayWithWhitespaceString_preservedExactly() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[\"  \"]", type);
        assertEquals("  ", result.get(0));
    }

    // Test unicode escape sequence decoded correctly in array element
    @Test
    public void testDeserialize_arrayWithUnicodeString_preservedCorrectly() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[\"caf\\u00e9\"]", type);
        assertEquals("caf\u00e9", result.get(0));
    }

    // Test escaped newline character decoded correctly in array element
    @Test
    public void testDeserialize_arrayWithEscapedString_preservedCorrectly() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[\"line1\\nline2\"]", type);
        assertEquals("line1\nline2", result.get(0));
    }

    // Test concrete ArrayList target type results in an actual ArrayList instance via ValueInstantiator
    @Test
    public void testDeserialize_arrayListConcreteType_returnsArrayListInstance() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(ArrayList.class, String.class);
        Object result = mapper.readValue("[\"a\"]", type);
        assertTrue(result instanceof ArrayList);
    }

    // Test many elements array covers multiple-iteration loop path, order and size preserved
    @Test
    public void testDeserialize_manyElementsArray_allElementsPreservedInOrder() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) { sb.append(","); }
            sb.append("\"v").append(i).append("\"");
        }
        sb.append("]");
        List<String> result = (List<String>) mapper.readValue(sb.toString(), type);
        assertEquals(50, result.size());
        assertEquals("v0", result.get(0));
        assertEquals("v49", result.get(49));
    }

    // Test @JsonFormat(with=ACCEPT_SINGLE_VALUE_AS_ARRAY) at property level enables wrapping even if global feature disabled
    @Test
    public void testDeserialize_propertyLevelJsonFormatUnwrap_wrapsSingleValue() throws Throwable {
        mapper.disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        SingleValueWrapBean bean = mapper.readValue("{\"tags\":\"solo\"}", SingleValueWrapBean.class);
        assertNotNull(bean.getTags());
        assertEquals(1, bean.getTags().size());
        assertEquals("solo", bean.getTags().get(0));
    }

    // Test @JsonFormat(without=ACCEPT_SINGLE_VALUE_AS_ARRAY) forces failure even if global feature enabled
    @Test
    public void testDeserialize_propertyLevelJsonFormatDenyUnwrap_throwsException() throws Throwable {
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        try {
            mapper.readValue("{\"tags\":\"solo\"}", SingleValueNoWrapBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // Test empty string element preserved exactly, distinct from null handling
    @Test
    public void testDeserialize_arrayWithEmptyStringElement_preservedAsEmptyString() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[\"\"]", type);
        assertEquals(1, result.size());
        assertEquals("", result.get(0));
    }

    // Test raw Collection<String> interface type also resolves to this deserializer
    @Test
    public void testDeserialize_collectionInterfaceType_returnsCollectionWithElements() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(Collection.class, String.class);
        Collection<String> result = (Collection<String>) mapper.readValue("[\"m\",\"n\"]", type);
        assertEquals(2, result.size());
        assertTrue(result.contains("m"));
        assertTrue(result.contains("n"));
    }

    // Test multiple null values at start, middle and end of array all preserved correctly
    @Test
    public void testDeserialize_arrayWithMultipleNulls_allPreserved() throws Throwable {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> result = (List<String>) mapper.readValue("[null,\"mid\",null]", type);
        assertEquals(3, result.size());
        assertNull(result.get(0));
        assertEquals("mid", result.get(1));
        assertNull(result.get(2));
    }

    public static class StringListBean {
        private List<String> values;
        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
    }

    public static class SingleValueWrapBean {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> tags;
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    public static class SingleValueNoWrapBean {
        @JsonFormat(without = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> tags;
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
}
