package com.fasterxml.jackson.databind.node;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

public class POJONodeClaudeTest
{
    // Simple public POJO with getter, used to test the "writeObject" branch of serialize()
    public static class SamplePojo
    {
        private String name;
        public SamplePojo(String name) { this.name = name; }
        public String getName() { return name; }
    }

    // ---- constructor / getPojo ----

    // constructor stores value, getPojo() returns exactly that reference
    @Test
    public void testConstructorAndGetPojo_nonNullValue_returnsSameValue() throws Throwable {
        String value = "hello";
        POJONode node = new POJONode(value);
        assertSame(value, node.getPojo());
    }

    // getPojo() with null-wrapped value returns null
    @Test
    public void testGetPojo_wrappingNull_returnsNull() throws Throwable {
        POJONode node = new POJONode(null);
        assertNull(node.getPojo());
    }

    // ---- getNodeType ----

    // getNodeType always returns JsonNodeType.POJO
    @Test
    public void testGetNodeType_returnsPOJO() throws Throwable {
        POJONode node = new POJONode("x");
        assertEquals(JsonNodeType.POJO, node.getNodeType());
    }

    // ---- asToken ----

    // asToken always returns VALUE_EMBEDDED_OBJECT
    @Test
    public void testAsToken_returnsValueEmbeddedObject() throws Throwable {
        POJONode node = new POJONode(new Object());
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, node.asToken());
    }

    // ---- binaryValue ----

    // binaryValue() when value is a byte[] returns the same array
    @Test
    public void testBinaryValue_byteArrayValue_returnsSameArray() throws Throwable {
        byte[] data = new byte[] { 1, 2, 3 };
        POJONode node = new POJONode(data);
        assertArrayEquals(data, node.binaryValue());
    }

    // ---- asText ----

    // asText() with null value returns literal "null"
    @Test
    public void testAsText_nullValue_returnsStringNull() throws Throwable {
        POJONode node = new POJONode(null);
        assertEquals("null", node.asText());
    }

    // asText() with non-null value returns value.toString()
    @Test
    public void testAsText_nonNullValue_returnsToStringOfValue() throws Throwable {
        Integer value = Integer.valueOf(42);
        POJONode node = new POJONode(value);
        assertEquals("42", node.asText());
    }

    // asText(default) with null value returns provided default
    @Test
    public void testAsTextWithDefault_nullValue_returnsDefault() throws Throwable {
        POJONode node = new POJONode(null);
        assertEquals("fallback", node.asText("fallback"));
    }

    // asText(default) with non-null value returns value.toString(), ignoring default
    @Test
    public void testAsTextWithDefault_nonNullValue_returnsToStringOfValue() throws Throwable {
        POJONode node = new POJONode(Integer.valueOf(7));
        assertEquals("7", node.asText("fallback"));
    }

    // ---- asBoolean ----

    // asBoolean(default) when value is Boolean.TRUE returns true
    @Test
    public void testAsBoolean_trueValue_returnsTrue() throws Throwable {
        POJONode node = new POJONode(Boolean.TRUE);
        assertTrue(node.asBoolean(false));
    }

    // asBoolean(default) when value is Boolean.FALSE returns false
    @Test
    public void testAsBoolean_falseValue_returnsFalse() throws Throwable {
        POJONode node = new POJONode(Boolean.FALSE);
        assertFalse(node.asBoolean(true));
    }

    // asBoolean(default) when value is not a Boolean returns the default
    @Test
    public void testAsBoolean_nonBooleanValue_returnsDefault() throws Throwable {
        POJONode node = new POJONode("not-boolean");
        assertTrue(node.asBoolean(true));
    }

    // asBoolean(default) when value is null returns the default (null check via &&)
    @Test
    public void testAsBoolean_nullValue_returnsDefault() throws Throwable {
        POJONode node = new POJONode(null);
        assertFalse(node.asBoolean(false));
    }

    // ---- asInt ----

    // asInt(default) when value is a Number returns intValue()
    @Test
    public void testAsInt_numberValue_returnsIntValue() throws Throwable {
        POJONode node = new POJONode(Integer.valueOf(99));
        assertEquals(99, node.asInt(-1));
    }

    // asInt(default) when value is not a Number returns the default
    @Test
    public void testAsInt_nonNumberValue_returnsDefault() throws Throwable {
        POJONode node = new POJONode("not-a-number");
        assertEquals(123, node.asInt(123));
    }

    // asInt(default) narrows a large long following standard Number narrowing contract
    @Test
    public void testAsInt_longOverflowValue_returnsTruncatedIntValue() throws Throwable {
        POJONode node = new POJONode(Long.valueOf(Long.MAX_VALUE));
        assertEquals((int) Long.MAX_VALUE, node.asInt(0));
    }

    // ---- asLong ----

    // asLong(default) when value is a Number returns longValue()
    @Test
    public void testAsLong_numberValue_returnsLongValue() throws Throwable {
        POJONode node = new POJONode(Integer.valueOf(55));
        assertEquals(55L, node.asLong(-1L));
    }

    // asLong(default) when value is not a Number returns the default
    @Test
    public void testAsLong_nonNumberValue_returnsDefault() throws Throwable {
        POJONode node = new POJONode("not-a-number");
        assertEquals(456L, node.asLong(456L));
    }

    // ---- asDouble ----

    // asDouble(default) when value is a Number returns doubleValue()
    @Test
    public void testAsDouble_numberValue_returnsDoubleValue() throws Throwable {
        POJONode node = new POJONode(Integer.valueOf(3));
        assertEquals(3.0, node.asDouble(-1.0), 1e-9);
    }

    // asDouble(default) when value is not a Number returns the default
    @Test
    public void testAsDouble_nonNumberValue_returnsDefault() throws Throwable {
        POJONode node = new POJONode("not-a-number");
        assertEquals(7.5, node.asDouble(7.5), 1e-9);
    }

    // ---- serialize ----

    // serialize() when value is null delegates to ctxt.defaultSerializeNull -> JSON "null"
    @Test
    public void testSerialize_nullValue_writesJsonNull() throws Throwable {
        POJONode node = new POJONode(null);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(node);
        assertEquals("null", json);
    }

    // serialize() when value is a plain POJO delegates to gen.writeObject -> normal JSON object
    @Test
    public void testSerialize_plainPojo_writesJsonObject() throws Throwable {
        POJONode node = new POJONode(new SamplePojo("abc"));
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(node);
        assertTrue(json.contains("\"name\":\"abc\""));
    }

    // ---- equals ----

    // equals() same instance returns true (identity shortcut)
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        POJONode node = new POJONode("x");
        assertTrue(node.equals(node));
    }

    // equals(null) returns false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        POJONode node = new POJONode("x");
        assertFalse(node.equals(null));
    }

    // equals() against a different type returns false
    @Test
    public void testEquals_differentType_returnsFalse() throws Throwable {
        POJONode node = new POJONode("x");
        assertFalse(node.equals("x"));
    }

    // equals() when both wrapped values are null returns true
    @Test
    public void testEquals_bothNullValues_returnsTrue() throws Throwable {
        POJONode a = new POJONode(null);
        POJONode b = new POJONode(null);
        assertTrue(a.equals(b));
    }

    // equals() when only one wrapped value is null returns false
    @Test
    public void testEquals_oneNullOneNonNull_returnsFalse() throws Throwable {
        POJONode a = new POJONode(null);
        POJONode b = new POJONode("x");
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
    }

    // equals() when wrapped values are equal (non-null) returns true
    @Test
    public void testEquals_equalValues_returnsTrue() throws Throwable {
        POJONode a = new POJONode(Integer.valueOf(5));
        POJONode b = new POJONode(Integer.valueOf(5));
        assertTrue(a.equals(b));
    }

    // equals() when wrapped values differ returns false
    @Test
    public void testEquals_differentValues_returnsFalse() throws Throwable {
        POJONode a = new POJONode(Integer.valueOf(5));
        POJONode b = new POJONode(Integer.valueOf(6));
        assertFalse(a.equals(b));
    }

    // ---- _pojoEquals (protected, same package, direct call) ----

    // _pojoEquals when both values are null returns true
    @Test
    public void testPojoEquals_directCall_bothNull_returnsTrue() throws Throwable {
        POJONode a = new POJONode(null);
        POJONode b = new POJONode(null);
        assertTrue(a._pojoEquals(b));
    }

    // _pojoEquals when values equal (non-null) returns true
    @Test
    public void testPojoEquals_directCall_valuesEqual_returnsTrue() throws Throwable {
        POJONode a = new POJONode(Integer.valueOf(7));
        POJONode b = new POJONode(Integer.valueOf(7));
        assertTrue(a._pojoEquals(b));
    }

    // ---- hashCode ----

    // hashCode() for non-null value returns value.hashCode()
    @Test
    public void testHashCode_nonNullValue_matchesValueHashCode() throws Throwable {
        String value = "abc";
        POJONode node = new POJONode(value);
        assertEquals(value.hashCode(), node.hashCode());
    }



    // ---- toString ----

    // toString() for byte[] value returns the binary-length description
    @Test
    public void testToString_byteArrayValue_returnsBinaryDescription() throws Throwable {
        byte[] data = new byte[] { 1, 2, 3 };
        POJONode node = new POJONode(data);
        assertEquals("(binary value of 3 bytes)", node.toString());
    }

    // toString() for a plain (non-byte[], non-RawValue) value returns String.valueOf(value)
    @Test
    public void testToString_plainValue_returnsStringValueOf() throws Throwable {
        POJONode node = new POJONode(Integer.valueOf(42));
        assertEquals("42", node.toString());
    }

    // toString() for null value returns "null"
    @Test
    public void testToString_nullValue_returnsStringNull() throws Throwable {
        POJONode node = new POJONode(null);
        assertEquals("null", node.toString());
    }
}
