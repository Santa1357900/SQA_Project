package com.fasterxml.jackson.databind.deser.std;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.StdConverter;

public class StringArrayDeserializerClaudeTest
{
    public static class StringArrayHolder {
        private String[] values;
        public String[] getValues() { return values; }
        public void setValues(String[] values) { this.values = values; }
    }

    public static class UpperCaseStringDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.getText().toUpperCase();
        }
    }

    public static class NullDefaultStringDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.getText();
        }
        @Override
        public String getNullValue() {
            return "NULL_DEFAULT";
        }
    }

    public static class ReverseConverter extends StdConverter<String, String> {
        @Override
        public String convert(String value) {
            StringBuilder sb = new StringBuilder(value);
            return sb.reverse().toString();
        }
    }

    public static class ContentUsingHolder {
        @JsonDeserialize(contentUsing = UpperCaseStringDeserializer.class)
        private String[] values;
        public String[] getValues() { return values; }
        public void setValues(String[] values) { this.values = values; }
    }

    public static class NullDefaultHolder {
        @JsonDeserialize(contentUsing = NullDefaultStringDeserializer.class)
        private String[] values;
        public String[] getValues() { return values; }
        public void setValues(String[] values) { this.values = values; }
    }

    public static class ContentConverterHolder {
        @JsonDeserialize(contentConverter = ReverseConverter.class)
        private String[] values;
        public String[] getValues() { return values; }
        public void setValues(String[] values) { this.values = values; }
    }

    // covers: public static final field `instance` initialized correctly
    @Test
    public void testInstance_singletonFieldIsNotNull() throws Throwable {
        assertNotNull(StringArrayDeserializer.instance);
        assertTrue(StringArrayDeserializer.instance instanceof StringArrayDeserializer);
    }

    // covers: deserialize() - loop executes 0 times, empty array
    @Test
    public void testDeserialize_emptyArray_returnsEmptyArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] result = mapper.readValue("[]", String[].class);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    // covers: deserialize() - loop executes once, VALUE_STRING branch
    @Test
    public void testDeserialize_singleElementArray_returnsArrayWithOneElement() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] result = mapper.readValue("[\"abc\"]", String[].class);
        assertEquals(1, result.length);
        assertEquals("abc", result[0]);
    }

    // covers: deserialize() - loop executes multiple times, order preserved
    @Test
    public void testDeserialize_multipleElements_returnsArrayInOrder() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] result = mapper.readValue("[\"a\",\"b\",\"c\"]", String[].class);
        assertEquals(3, result.length);
        assertEquals("a", result[0]);
        assertEquals("b", result[1]);
        assertEquals("c", result[2]);
    }

    // covers: deserialize() - VALUE_NULL branch inside loop
    @Test
    public void testDeserialize_nullElementInArray_preservesNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] result = mapper.readValue("[\"a\", null, \"b\"]", String[].class);
        assertEquals(3, result.length);
        assertEquals("a", result[0]);
        assertNull(result[1]);
        assertEquals("b", result[2]);
    }

    // covers: deserialize() - all elements are null
    @Test
    public void testDeserialize_allNullElements_returnsAllNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] result = mapper.readValue("[null, null]", String[].class);
        assertEquals(2, result.length);
        assertNull(result[0]);
        assertNull(result[1]);
    }

    // covers: deserialize() - else branch calling _parseString for non-string/non-null token
    @Test
    public void testDeserialize_numericElements_convertedToStringRepresentation() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] result = mapper.readValue("[1, 2, 3]", String[].class);
        assertEquals(3, result.length);
        assertEquals("1", result[0]);
        assertEquals("2", result[1]);
        assertEquals("3", result[2]);
    }

    // covers: deserialize() - buffer growth (appendCompletedChunk) with many elements
    @Test
    public void testDeserialize_largeArray_preservesOrderAndLength() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder json = new StringBuilder();
        json.append("[");
        int size = 100;
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"v").append(i).append("\"");
        }
        json.append("]");
        String[] result = mapper.readValue(json.toString(), String[].class);
        assertEquals(size, result.length);
        assertEquals("v0", result[0]);
        assertEquals("v99", result[99]);
    }

    // covers: deserialize() loop - ACCEPT_EMPTY_STRING_AS_NULL_OBJECT does not apply to in-array elements
    @Test
    public void testDeserialize_emptyStringElementInArray_defaultSettings_preservesEmptyString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        String[] result = mapper.readValue("[\"\"]", String[].class);
        assertEquals(1, result.length);
        assertEquals("", result[0]);
    }

    // covers: handleNonArray() - ACCEPT_SINGLE_VALUE_AS_ARRAY disabled -> throws mapping exception
    @Test
    public void testDeserialize_nonArraySingleString_defaultSettings_throwsMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("\"hello\"", String[].class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers: handleNonArray() - ACCEPT_EMPTY_STRING_AS_NULL_OBJECT disabled by default -> falls through to throw
    @Test
    public void testDeserialize_nonArrayEmptyString_defaultSettings_throwsMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("\"\"", String[].class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers: handleNonArray() - VALUE_STRING + ACCEPT_EMPTY_STRING_AS_NULL_OBJECT + empty string -> null
    @Test
    public void testDeserialize_nonArrayEmptyString_acceptEmptyStringAsNull_returnsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        String[] result = mapper.readValue("\"\"", String[].class);
        assertNull(result);
    }

    // covers: handleNonArray() - non-empty string still throws even with ACCEPT_EMPTY_STRING_AS_NULL_OBJECT enabled
    @Test
    public void testDeserialize_nonArrayNonEmptyString_acceptEmptyStringAsNull_stillThrows() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        try {
            mapper.readValue("\"hello\"", String[].class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers: handleNonArray() - str.length()==0 boundary, whitespace has length 1, not treated as empty
    @Test
    public void testDeserialize_nonArrayWhitespaceString_acceptEmptyStringAsNull_stillThrows() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        try {
            mapper.readValue("\" \"", String[].class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers: handleNonArray() - ACCEPT_SINGLE_VALUE_AS_ARRAY enabled, wraps string into single-element array
    @Test
    public void testDeserialize_nonArraySingleString_acceptSingleValueAsArray_wrapsInArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        String[] result = mapper.readValue("\"hello\"", String[].class);
        assertEquals(1, result.length);
        assertEquals("hello", result[0]);
    }

    // covers: handleNonArray() - ACCEPT_SINGLE_VALUE_AS_ARRAY enabled with numeric token uses _parseString
    @Test
    public void testDeserialize_nonArrayNumber_acceptSingleValueAsArray_wrapsAsStringElement() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        String[] result = mapper.readValue("42", String[].class);
        assertEquals(1, result.length);
        assertEquals("42", result[0]);
    }

    // covers: handleNonArray() - ACCEPT_SINGLE_VALUE_AS_ARRAY enabled skips empty-string-as-null check entirely
    @Test
    public void testDeserialize_nonArrayEmptyString_acceptSingleValueAsArray_wrapsAsEmptyStringElement() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        String[] result = mapper.readValue("\"\"", String[].class);
        assertEquals(1, result.length);
        assertEquals("", result[0]);
    }

    // covers: handleNonArray() - ACCEPT_SINGLE_VALUE_AS_ARRAY takes precedence over ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
    @Test
    public void testDeserialize_nonArrayEmptyString_bothFeaturesEnabled_wrapsArrayTakesPrecedence() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        String[] result = mapper.readValue("\"\"", String[].class);
        assertEquals(1, result.length);
        assertEquals("", result[0]);
    }

    // covers: deserialize() invoked via bean property path
    @Test
    public void testDeserialize_beanProperty_stringArrayField_deserializesCorrectly() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        StringArrayHolder holder = mapper.readValue("{\"values\":[\"x\",\"y\"]}", StringArrayHolder.class);
        assertNotNull(holder.getValues());
        assertEquals(2, holder.getValues().length);
        assertEquals("x", holder.getValues()[0]);
        assertEquals("y", holder.getValues()[1]);
    }

    // covers: bean property remains null when JSON field value is null
    @Test
    public void testDeserialize_beanProperty_jsonNullField_setsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        StringArrayHolder holder = mapper.readValue("{\"values\":null}", StringArrayHolder.class);
        assertNull(holder.getValues());
    }

    // covers: createContextual() with contentUsing -> _deserializeCustom() applies custom deserializer per element
    @Test
    public void testCreateContextual_contentUsingDeserializer_appliesToEachElement() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ContentUsingHolder holder = mapper.readValue("{\"values\":[\"ab\",\"cd\"]}", ContentUsingHolder.class);
        assertEquals(2, holder.getValues().length);
        assertEquals("AB", holder.getValues()[0]);
        assertEquals("CD", holder.getValues()[1]);
    }

    // covers: _deserializeCustom() - VALUE_NULL branch calls deser.getNullValue()
    @Test
    public void testCreateContextual_contentUsingDeserializer_nullUsesGetNullValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        NullDefaultHolder holder = mapper.readValue("{\"values\":[\"ab\", null]}", NullDefaultHolder.class);
        assertEquals(2, holder.getValues().length);
        assertEquals("ab", holder.getValues()[0]);
        assertEquals("NULL_DEFAULT", holder.getValues()[1]);
    }

    // covers: createContextual() - findConvertingContentDeserializer path applies content converter
    @Test
    public void testCreateContextual_contentConverter_appliesConversionToEachElement() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ContentConverterHolder holder = mapper.readValue("{\"values\":[\"ab\",\"cd\"]}", ContentConverterHolder.class);
        assertEquals(2, holder.getValues().length);
        assertEquals("ba", holder.getValues()[0]);
        assertEquals("dc", holder.getValues()[1]);
    }

    // covers: deserialize() - VALUE_STRING branch preserves unicode and escaped characters via jp.getText()
    @Test
    public void testDeserialize_unicodeAndEscapedCharacters_preservesValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] result = mapper.readValue("[\"caf\\u00e9\", \"line1\\nline2\"]", String[].class);
        assertEquals(2, result.length);
        assertEquals("caf\u00e9", result[0]);
        assertEquals("line1\nline2", result[1]);
    }
}
