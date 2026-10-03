package com.fasterxml.jackson.databind.deser.std;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class JsonNodeDeserializerClaudeTest
{
    private ObjectMapper mapper;
    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable
    {
        mapper = new ObjectMapper();
        factory = new JsonFactory();
    }

    // getDeserializer(ObjectNode.class) must return a singleton instance each call
    @Test
    public void testGetDeserializer_objectNodeClass_returnsSingletonObjectDeserializer() throws Throwable {
        JsonDeserializer<? extends JsonNode> d1 = JsonNodeDeserializer.getDeserializer(ObjectNode.class);
        JsonDeserializer<? extends JsonNode> d2 = JsonNodeDeserializer.getDeserializer(ObjectNode.class);
        assertSame(d1, d2);
    }

    // getDeserializer(ArrayNode.class) must return a singleton instance each call
    @Test
    public void testGetDeserializer_arrayNodeClass_returnsSingletonArrayDeserializer() throws Throwable {
        JsonDeserializer<? extends JsonNode> a1 = JsonNodeDeserializer.getDeserializer(ArrayNode.class);
        JsonDeserializer<? extends JsonNode> a2 = JsonNodeDeserializer.getDeserializer(ArrayNode.class);
        assertSame(a1, a2);
    }

    // default branch of getDeserializer: any non Object/Array class returns same generic instance
    @Test
    public void testGetDeserializer_otherClass_returnsSameGenericInstance() throws Throwable {
        JsonDeserializer<? extends JsonNode> g1 = JsonNodeDeserializer.getDeserializer(TextNode.class);
        JsonDeserializer<? extends JsonNode> g2 = JsonNodeDeserializer.getDeserializer(IntNode.class);
        assertSame(g1, g2);
    }

    // getNullValue(ctxt) must always return NullNode singleton, regardless of ctxt
    @Test
    public void testGetNullValue_withContext_returnsNullNodeSingleton() throws Throwable {
        JsonDeserializer<? extends JsonNode> deser = JsonNodeDeserializer.getDeserializer(TextNode.class);
        JsonNode result = deser.getNullValue((DeserializationContext) null);
        assertSame(NullNode.getInstance(), result);
    }

    // deprecated no-arg getNullValue() must also return NullNode singleton
    @Test
    public void testGetNullValue_noArgDeprecated_returnsNullNodeSingleton() throws Throwable {
        JsonNodeDeserializer deser = new JsonNodeDeserializer();
        assertSame(NullNode.getInstance(), deser.getNullValue());
    }

    // isCachable() inherited from BaseNodeDeserializer must return true
    @Test
    public void testIsCachable_returnsTrue() throws Throwable {
        JsonDeserializer<? extends JsonNode> deser = JsonNodeDeserializer.getDeserializer(ObjectNode.class);
        assertTrue(deser.isCachable());
    }

    // deserialize(): START_OBJECT branch, empty object -> empty ObjectNode
    @Test
    public void testDeserialize_emptyObject_returnsEmptyObjectNode() throws Throwable {
        JsonNode node = mapper.readTree("{}");
        assertTrue(node instanceof ObjectNode);
        assertEquals(0, node.size());
    }

    // deserializeObject switch: string/int/bool/null value types inside an object
    @Test
    public void testDeserialize_objectWithFields_allValueTypes() throws Throwable {
        JsonNode node = mapper.readTree("{\"s\":\"hi\",\"i\":5,\"t\":true,\"f\":false,\"n\":null}");
        assertEquals("hi", node.get("s").asText());
        assertEquals(5, node.get("i").asInt());
        assertTrue(node.get("t").asBoolean());
        assertFalse(node.get("f").asBoolean());
        assertTrue(node.get("n").isNull());
    }

    // deserialize(): START_ARRAY branch, empty array -> empty ArrayNode
    @Test
    public void testDeserialize_emptyArray_returnsEmptyArrayNode() throws Throwable {
        JsonNode node = mapper.readTree("[]");
        assertTrue(node instanceof ArrayNode);
        assertEquals(0, node.size());
    }

    // deserializeArray switch: mixed element types in one array
    @Test
    public void testDeserialize_arrayWithMixedElements() throws Throwable {
        JsonNode node = mapper.readTree("[1,\"a\",true,false,null]");
        assertEquals(5, node.size());
        assertEquals(1, node.get(0).asInt());
        assertEquals("a", node.get(1).asText());
        assertTrue(node.get(2).asBoolean());
        assertFalse(node.get(3).asBoolean());
        assertTrue(node.get(4).isNull());
    }

    // deserialize(): default branch -> deserializeAny -> ID_STRING
    @Test
    public void testDeserialize_scalarString_returnsTextNode() throws Throwable {
        JsonNode node = mapper.readTree("\"hello\"");
        assertTrue(node instanceof TextNode);
        assertEquals("hello", node.asText());
    }

    // ID_STRING branch with unicode escape sequence
    @Test
    public void testDeserialize_scalarUnicodeString_decodesEscape() throws Throwable {
        JsonNode node = mapper.readTree("\"caf\\u00e9\"");
        assertEquals("caf\u00e9", node.asText());
    }

    // _fromInt: default feature state, small int -> IntNode
    @Test
    public void testDeserialize_scalarSmallInt_returnsIntNode() throws Throwable {
        JsonNode node = mapper.readTree("42");
        assertTrue(node instanceof IntNode);
        assertEquals(42, node.asInt());
    }

    // _fromInt: value overflowing int range -> LongNode via parser number type
    @Test
    public void testDeserialize_scalarOverflowInt_returnsLongNode() throws Throwable {
        JsonNode node = mapper.readTree("2147483648");
        assertTrue(node instanceof LongNode);
        assertEquals(2147483648L, node.asLong());
    }

    // _fromInt: value overflowing long range -> BigIntegerNode
    @Test
    public void testDeserialize_scalarOverflowLong_returnsBigIntegerNode() throws Throwable {
        JsonNode node = mapper.readTree("99999999999999999999");
        assertTrue(node instanceof BigIntegerNode);
        assertEquals("99999999999999999999", node.asText());
    }

    // _fromFloat: default -> DoubleNode
    @Test
    public void testDeserialize_scalarFloat_returnsDoubleNode() throws Throwable {
        JsonNode node = mapper.readTree("1.5");
        assertTrue(node instanceof DoubleNode);
        assertEquals(1.5, node.asDouble(), 1e-9);
    }

    // _fromFloat: USE_BIG_DECIMAL_FOR_FLOATS enabled -> DecimalNode
    @Test
    public void testDeserialize_scalarFloatWithBigDecimalFeature_returnsDecimalNode() throws Throwable {
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        JsonNode node = mapper.readTree("1.5");
        assertTrue(node instanceof DecimalNode);
        assertEquals(1.5, node.asDouble(), 1e-9);
    }

    // deserializeAny: ID_TRUE branch
    @Test
    public void testDeserialize_scalarBooleanTrue_returnsBooleanNode() throws Throwable {
        JsonNode node = mapper.readTree("true");
        assertTrue(node instanceof BooleanNode);
        assertTrue(node.asBoolean());
    }

    // deserializeAny: ID_FALSE branch
    @Test
    public void testDeserialize_scalarBooleanFalse_returnsBooleanNode() throws Throwable {
        JsonNode node = mapper.readTree("false");
        assertTrue(node instanceof BooleanNode);
        assertFalse(node.asBoolean());
    }

    // deserializeAny: ID_NULL branch
    @Test
    public void testDeserialize_scalarNull_returnsNullNode() throws Throwable {
        JsonNode node = mapper.readTree("null");
        assertTrue(node instanceof NullNode);
        assertTrue(node.isNull());
    }

    // _fromInt: USE_BIG_INTEGER_FOR_INTS feature forces BigIntegerNode even for small ints
    @Test
    public void testFromInt_useBigIntegerForIntsFeature_returnsBigIntegerNode() throws Throwable {
        mapper.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
        JsonNode node = mapper.readTree("5");
        assertTrue(node instanceof BigIntegerNode);
        assertEquals("5", node.asText());
    }

    // _fromInt: USE_LONG_FOR_INTS feature forces LongNode even for small ints
    @Test
    public void testFromInt_useLongForIntsFeature_returnsLongNode() throws Throwable {
        mapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
        JsonNode node = mapper.readTree("5");
        assertTrue(node instanceof LongNode);
        assertEquals(5L, node.asLong());
    }

    // duplicate field default behavior: last value wins, no exception
    @Test
    public void testDeserializeObject_duplicateFieldDefault_lastValueWins() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1,\"a\":2}");
        assertEquals(2, node.get("a").asInt());
    }

    // duplicate field with FAIL_ON_READING_DUP_TREE_KEY enabled -> JsonMappingException
    @Test
    public void testDeserializeObject_duplicateFieldFailOnDup_throwsJsonMappingException() throws Throwable {
        mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);
        try {
            mapper.readTree("{\"a\":1,\"a\":2}");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // deserializeArray: unclosed array reaches EOF -> "Unexpected end-of-input" mapping exception
    @Test
    public void testDeserializeArray_unclosedArray_throwsJsonMappingExceptionUnexpectedEOF() throws Throwable {
        try {
            mapper.readTree("[1,2");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().contains("Unexpected end-of-input"));
        }
    }

    // ObjectDeserializer.deserialize(): current token START_OBJECT branch
    @Test
    public void testObjectDeserializer_startObjectToken_parsesFields() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1}");
        p.nextToken();
        ObjectNode node = mapper.readValue(p, ObjectNode.class);
        assertEquals(1, node.get("a").asInt());
    }

    // ObjectDeserializer.deserialize(): current token FIELD_NAME branch (caller pre-advanced)
    @Test
    public void testObjectDeserializer_fieldNameToken_parsesFields() throws Throwable {
        JsonParser p = factory.createParser("{\"a\":1}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME
        ObjectNode node = mapper.readValue(p, ObjectNode.class);
        assertEquals(1, node.get("a").asInt());
    }

    // ObjectDeserializer.deserialize(): per Javadoc comment, current token END_OBJECT for an
    // empty object (caller pre-advanced) must be treated like FIELD_NAME and yield empty ObjectNode
    @Test
    public void testObjectDeserializer_endObjectToken_emptyObject() throws Throwable {
        JsonParser p = factory.createParser("{}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // END_OBJECT
        ObjectNode node = mapper.readValue(p, ObjectNode.class);
        assertEquals(0, node.size());
    }

    // ObjectDeserializer.deserialize(): invalid current token -> JsonMappingException
    @Test
    public void testObjectDeserializer_invalidToken_throwsJsonMappingException() throws Throwable {
        JsonParser p = factory.createParser("123");
        p.nextToken();
        try {
            mapper.readValue(p, ObjectNode.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // ArrayDeserializer.deserialize(): valid start array token, multiple elements
    @Test
    public void testArrayDeserializer_startArrayToken_parsesElements() throws Throwable {
        JsonParser p = factory.createParser("[1,2,3]");
        p.nextToken();
        ArrayNode node = mapper.readValue(p, ArrayNode.class);
        assertEquals(3, node.size());
        assertEquals(2, node.get(1).asInt());
    }

    // ArrayDeserializer.deserialize(): invalid current token -> JsonMappingException
    @Test
    public void testArrayDeserializer_invalidToken_throwsJsonMappingException() throws Throwable {
        JsonParser p = factory.createParser("\"abc\"");
        p.nextToken();
        try {
            mapper.readValue(p, ArrayNode.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // deserializeObject: nested object and nested array values inside an object field
    @Test
    public void testDeserializeObject_nestedObjectAndArrayValues() throws Throwable {
        JsonNode node = mapper.readTree("{\"list\":[1,2],\"obj\":{\"k\":\"v\"}}");
        assertEquals(2, node.get("list").size());
        assertEquals(1, node.get("list").get(0).asInt());
        assertEquals("v", node.get("obj").get("k").asText());
    }

    // deserializeArray: nested object inside an array element
    @Test
    public void testDeserializeArray_nestedObjectElement() throws Throwable {
        JsonNode node = mapper.readTree("[{\"x\":1}]");
        assertEquals(1, node.size());
        assertEquals(1, node.get(0).get("x").asInt());
    }
}
