package com.fasterxml.jackson.databind.deser.std;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;

public class StringArrayDeserializerClaudeTest {

    private ObjectMapper mapper;

    public static class Container {
        private String[] values;
        public String[] getValues() { return values; }
        public void setValues(String[] values) { this.values = values; }
    }

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // instance field must exist and be usable
    @Test
    public void testInstance_isNotNullAndSameType() throws Throwable {
        assertNotNull(StringArrayDeserializer.instance);
        assertTrue(StringArrayDeserializer.instance instanceof StringArrayDeserializer);
    }

    // loop 0 iterations: while(...) != END_ARRAY immediately true
    @Test
    public void testDeserialize_emptyArray_returnsEmptyArray() throws Throwable {
        String[] result = mapper.readValue("[]", String[].class);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    // loop 1 iteration, VALUE_STRING branch
    @Test
    public void testDeserialize_singleStringElement_returnsArrayWithOneElement() throws Throwable {
        String[] result = mapper.readValue("[\"hello\"]", String[].class);
        assertArrayEquals(new String[]{"hello"}, result);
    }

    // loop multiple iterations preserving order
    @Test
    public void testDeserialize_multipleStringElements_returnsArrayInOrder() throws Throwable {
        String[] result = mapper.readValue("[\"a\",\"b\",\"c\"]", String[].class);
        assertArrayEquals(new String[]{"a", "b", "c"}, result);
    }

    // BUG: VALUE_NULL branch with default (null) elementDeserializer must yield null, not NPE
    @Test
    public void testDeserialize_nullElementInArray_defaultDeserializer_returnsNullElement() throws Throwable {
        String[] result = mapper.readValue("[\"a\", null, \"b\"]", String[].class);
        assertEquals(3, result.length);
        assertNull(result[1]);
        assertEquals("a", result[0]);
        assertEquals("b", result[2]);
    }

    // buffer resize: many elements to force appendCompletedChunk path
    @Test
    public void testDeserialize_manyElements_triggersBufferResize_returnsCorrectArray() throws Throwable {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"v").append(i).append("\"");
        }
        sb.append("]");
        String[] result = mapper.readValue(sb.toString(), String[].class);
        assertEquals(30, result.length);
        assertEquals("v0", result[0]);
        assertEquals("v29", result[29]);
    }

    // unicode and escape sequences preserved
    @Test
    public void testDeserialize_stringWithUnicodeAndEscapes_preservesValue() throws Throwable {
        String[] result = mapper.readValue("[\"caf\\u00e9\\ttab\"]", String[].class);
        assertEquals("caf\u00e9\ttab", result[0]);
    }

    // whitespace-only string element preserved as-is (not null)
    @Test
    public void testDeserialize_whitespaceStringElement_preservesValue() throws Throwable {
        String[] result = mapper.readValue("[\" \"]", String[].class);
        assertEquals(" ", result[0]);
    }

    // numeric token inside array goes through _parseString conversion
    @Test
    public void testDeserialize_numericElementInArray_convertsToStringRepresentation() throws Throwable {
        String[] result = mapper.readValue("[123]", String[].class);
        assertEquals("123", result[0]);
    }

    // boolean token inside array goes through _parseString conversion
    @Test
    public void testDeserialize_booleanElementInArray_convertsToStringRepresentation() throws Throwable {
        String[] result = mapper.readValue("[true]", String[].class);
        assertEquals("true", result[0]);
    }

    // handleNonArray: non-array string token, ACCEPT_SINGLE_VALUE_AS_ARRAY disabled (default) -> throws
    @Test
    public void testDeserialize_nonArrayString_singleValueAsArrayDisabled_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("\"hello\"", String[].class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // handleNonArray: non-array, non-string token -> throws regardless of empty-string feature
    @Test
    public void testDeserialize_nonArrayNumber_singleValueAsArrayDisabled_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("123", String[].class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // handleNonArray: empty string + ACCEPT_EMPTY_STRING_AS_NULL_OBJECT enabled -> returns null
    @Test
    public void testDeserialize_emptyString_acceptEmptyStringAsNullEnabled_returnsNull() throws Throwable {
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        String[] result = mapper.readValue("\"\"", String[].class);
        assertNull(result);
    }

    // handleNonArray: empty string without the feature enabled -> still throws
    @Test
    public void testDeserialize_emptyString_acceptEmptyStringAsNullDisabled_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("\"\"", String[].class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // handleNonArray: ACCEPT_SINGLE_VALUE_AS_ARRAY enabled, string value wrapped
    @Test
    public void testDeserialize_nonEmptyString_singleValueAsArrayEnabled_wrapsInArray() throws Throwable {
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        String[] result = mapper.readValue("\"solo\"", String[].class);
        assertArrayEquals(new String[]{"solo"}, result);
    }

    // handleNonArray: ACCEPT_SINGLE_VALUE_AS_ARRAY enabled, numeric root goes through _parseString
    @Test
    public void testDeserialize_numericRoot_singleValueAsArrayEnabled_wrapsConvertedString() throws Throwable {
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        String[] result = mapper.readValue("42", String[].class);
        assertArrayEquals(new String[]{"42"}, result);
    }

    // createContextual path via bean property: empty array field
    @Test
    public void testDeserialize_pojoWithEmptyArrayField_returnsEmptyArray() throws Throwable {
        Container c = mapper.readValue("{\"values\":[]}", Container.class);
        assertNotNull(c.getValues());
        assertEquals(0, c.getValues().length);
    }

    // BUG via contextual path: null element inside bean property array must not NPE
    @Test
    public void testDeserialize_pojoWithNullElementInArrayField_returnsNullElement() throws Throwable {
        Container c = mapper.readValue("{\"values\":[\"a\", null]}", Container.class);
        assertEquals(2, c.getValues().length);
        assertEquals("a", c.getValues()[0]);
        assertNull(c.getValues()[1]);
    }

    // contextual path with multiple plain string elements
    @Test
    public void testDeserialize_pojoWithMultipleStringElements_returnsCorrectArray() throws Throwable {
        Container c = mapper.readValue("{\"values\":[\"x\",\"y\",\"z\"]}", Container.class);
        assertArrayEquals(new String[]{"x", "y", "z"}, c.getValues());
    }

    // BUG variant: array containing only a single null element
    @Test
    public void testDeserialize_arrayWithOnlyNullElement_returnsArrayWithNull() throws Throwable {
        String[] result = mapper.readValue("[null]", String[].class);
        assertEquals(1, result.length);
        assertNull(result[0]);
    }

    // empty string as array element must remain "" even if empty-string-as-null feature enabled
    @Test
    public void testDeserialize_nestedEmptyStringElement_preservesEmptyString() throws Throwable {
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        String[] result = mapper.readValue("[\"\"]", String[].class);
        assertEquals(1, result.length);
        assertEquals("", result[0]);
    }

    // multiple null elements in sequence, default deserializer path
    @Test
    public void testDeserialize_consecutiveNullElements_allReturnNull() throws Throwable {
        String[] result = mapper.readValue("[null, null, \"x\"]", String[].class);
        assertEquals(3, result.length);
        assertNull(result[0]);
        assertNull(result[1]);
        assertEquals("x", result[2]);
    }
}
