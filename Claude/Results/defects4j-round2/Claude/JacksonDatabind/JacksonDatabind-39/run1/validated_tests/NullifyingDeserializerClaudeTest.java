package com.fasterxml.jackson.databind.deser.std;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class NullifyingDeserializerClaudeTest {

    private NullifyingDeserializer deserializer;
    private JsonFactory jsonFactory;

    @Before
    public void setUp() throws Throwable {
        deserializer = new NullifyingDeserializer();
        jsonFactory = new JsonFactory();
    }

    private JsonParser createParser(String json) throws IOException {
        return jsonFactory.createParser(json);
    }

    // Branch: current token START_OBJECT -> skipChildren moves to matching END_OBJECT, returns null
    @Test
    public void testDeserialize_startObject_movesToEndObjectAndReturnsNull() throws Throwable {
        JsonParser p = createParser("{\"a\":1,\"b\":2}");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.END_OBJECT, p.getCurrentToken());
    }

    // Branch: current token START_ARRAY -> skipChildren moves to matching END_ARRAY, returns null
    @Test
    public void testDeserialize_startArray_movesToEndArrayAndReturnsNull() throws Throwable {
        JsonParser p = createParser("[1,2,3]");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.END_ARRAY, p.getCurrentToken());
    }

    // Branch: nested object/array inside array fully skipped, ends at outer END_ARRAY
    @Test
    public void testDeserialize_nestedStructures_skipsAllNestedContent() throws Throwable {
        JsonParser p = createParser("[{\"x\":[1,2]},{\"y\":2}]");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.END_ARRAY, p.getCurrentToken());
    }

    // Branch: current token is scalar VALUE_NUMBER_INT -> skipChildren does nothing, token unchanged
    @Test
    public void testDeserialize_scalarNumber_tokenUnchangedAndReturnsNull() throws Throwable {
        JsonParser p = createParser("123");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.getCurrentToken());
    }

    // Branch: current token is scalar string -> skipChildren does nothing, token unchanged
    @Test
    public void testDeserialize_scalarString_tokenUnchangedAndReturnsNull() throws Throwable {
        JsonParser p = createParser("\"hello\"");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.VALUE_STRING, p.getCurrentToken());
    }



    // Empty object: START_OBJECT immediately followed by END_OBJECT
    @Test
    public void testDeserialize_emptyObject_movesToEndObject() throws Throwable {
        JsonParser p = createParser("{}");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.END_OBJECT, p.getCurrentToken());
    }

    // Empty array: START_ARRAY immediately followed by END_ARRAY
    @Test
    public void testDeserialize_emptyArray_movesToEndArray() throws Throwable {
        JsonParser p = createParser("[]");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.END_ARRAY, p.getCurrentToken());
    }

    // Boolean scalar value: token unchanged, returns null
    @Test
    public void testDeserialize_booleanValue_tokenUnchangedAndReturnsNull() throws Throwable {
        JsonParser p = createParser("true");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.VALUE_TRUE, p.getCurrentToken());
    }

    // Null literal value: token unchanged, returns null
    @Test
    public void testDeserialize_nullLiteral_tokenUnchangedAndReturnsNull() throws Throwable {
        JsonParser p = createParser("null");
        p.nextToken();
        Object result = deserializer.deserialize(p, null);
        assertNull(result);
        assertEquals(JsonToken.VALUE_NULL, p.getCurrentToken());
    }



    // default branch: VALUE_STRING token id is not ARRAY/OBJECT/FIELD_NAME -> returns null directly
    @Test
    public void testDeserializeWithType_valueString_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("\"hello\"");
        p.nextToken();
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // default branch: VALUE_NUMBER_INT
    @Test
    public void testDeserializeWithType_valueNumberInt_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("42");
        p.nextToken();
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // default branch: VALUE_NUMBER_FLOAT
    @Test
    public void testDeserializeWithType_valueNumberFloat_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("3.14");
        p.nextToken();
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // default branch: VALUE_TRUE
    @Test
    public void testDeserializeWithType_valueTrue_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("true");
        p.nextToken();
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // default branch: VALUE_FALSE
    @Test
    public void testDeserializeWithType_valueFalse_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("false");
        p.nextToken();
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // default branch: VALUE_NULL
    @Test
    public void testDeserializeWithType_valueNull_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("null");
        p.nextToken();
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // default branch: END_OBJECT reached after consuming an empty object
    @Test
    public void testDeserializeWithType_endObject_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("{}");
        p.nextToken();
        p.nextToken();
        assertEquals(JsonToken.END_OBJECT, p.getCurrentToken());
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // default branch: END_ARRAY reached after consuming an empty array
    @Test
    public void testDeserializeWithType_endArray_defaultBranchReturnsNull() throws Throwable {
        JsonParser p = createParser("[]");
        p.nextToken();
        p.nextToken();
        assertEquals(JsonToken.END_ARRAY, p.getCurrentToken());
        Object result = deserializer.deserializeWithType(p, null, null);
        assertNull(result);
    }

    // Constructor creates a usable, non-null instance
    @Test
    public void testConstructor_createsNonNullInstance() throws Throwable {
        NullifyingDeserializer d = new NullifyingDeserializer();
        assertNotNull(d);
    }

    // Static singleton instance is non-null and behaves like a regular instance
    @Test
    public void testStaticInstance_isNotNullAndUsable() throws Throwable {
        assertNotNull(NullifyingDeserializer.instance);
        JsonParser p = createParser("1");
        p.nextToken();
        Object result = NullifyingDeserializer.instance.deserialize(p, null);
        assertNull(result);
    }
}
