package com.fasterxml.jackson.databind.node;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ObjectNodeTest {

    @Test
    public void testConstructorsAndBasics() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node1 = new ObjectNode(factory);
        assertEquals(0, node1.size());
        assertEquals(JsonNodeType.OBJECT, node1.getNodeType());
        assertEquals(JsonToken.START_OBJECT, node1.asToken());

        Map<String, JsonNode> kids = new LinkedHashMap<String, JsonNode>();
        kids.put("a", factory.textNode("value"));
        ObjectNode node2 = new ObjectNode(factory, kids);
        assertEquals(1, node2.size());
        assertEquals("value", node2.get("a").asText());
    }

    @Test
    public void testDeepCopy() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode original = new ObjectNode(factory);
        original.put("str", "test");
        original.putArray("arr").add(1);

        ObjectNode copy = original.deepCopy();
        assertNotNull(copy);
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());

        copy.put("str", "changed");
        assertNotEquals(original.get("str").asText(), copy.get("str").asText());
    }

    @Test
    public void testGetAndPathMethods() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);
        node.put("field1", 123);

        assertNull(node.get(0));
        assertNotNull(node.get("field1"));
        assertNull(node.get("nonexistent"));

        assertTrue(node.path(0) instanceof MissingNode);
        assertEquals(123, node.path("field1").asInt());
        assertTrue(node.path("nonexistent") instanceof MissingNode);

        // _at test via JsonPointer
        JsonPointer ptr = JsonPointer.compile("/field1");
        assertEquals(123, node._at(ptr).asInt());
    }

    @Test
    public void testIteratorsAndFields() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);
        node.put("k1", "v1");
        node.put("k2", "v2");

        Iterator<JsonNode> elems = node.elements();
        assertTrue(elems.hasNext());
        assertEquals("v1", elems.next().asText());

        Iterator<String> names = node.fieldNames();
        assertTrue(names.hasNext());
        assertEquals("k1", names.next());

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        assertTrue(fields.hasNext());
        Map.Entry<String, JsonNode> entry = fields.next();
        assertEquals("k1", entry.getKey());
        assertEquals("v1", entry.getValue().asText());
    }

    @Test
    public void testWithAndWithArray() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);

        ObjectNode subObj = node.with("sub");
        assertNotNull(subObj);
        assertSame(subObj, node.with("sub"));

        ArrayNode subArr = node.withArray("arr");
        assertNotNull(subArr);
        assertSame(subArr, node.withArray("arr"));

        // Exception cases for 'with' and 'withArray' type mismatch
        try {
            node.with("arr");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("is not of type ObjectNode"));
        }

        try {
            node.withArray("sub");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("is not of type ArrayNode"));
        }
    }

    @Test
    public void testFindMethods() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode root = new ObjectNode(factory);
        ObjectNode child = root.putObject("child");
        child.put("target", "foundMe");
        root.put("target", "rootFound");

        assertNotNull(root.findValue("target"));
        
        List<JsonNode> values = root.findValues("target", null);
        assertNotNull(values);
        assertTrue(values.size() >= 1);

        List<String> texts = root.findValuesAsText("target", null);
        assertNotNull(texts);
        assertTrue(texts.size() >= 1);

        ObjectNode parentNode = root.findParent("target");
        assertNotNull(parentNode);

        List<JsonNode> parents = root.findParents("target", null);
        assertNotNull(parents);
        assertTrue(parents.size() >= 1);
        
        // Nonexistent
        assertNull(root.findValue("nonexistent"));
        assertNull(root.findParent("nonexistent"));
    }

    @Test
    public void testMutatorsSetAndReplace() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);

        assertSame(node, node.set("f1", factory.textNode("v1")));
        assertSame(node, node.set("f2", null)); // should convert to NullNode

        Map<String, JsonNode> map = new HashMap<String, JsonNode>();
        map.put("f3", factory.textNode("v3"));
        map.put("f4", null);
        assertSame(node, node.setAll(map));

        ObjectNode other = new ObjectNode(factory);
        other.put("f5", "v5");
        assertSame(node, node.setAll(other));

        JsonNode replaced = node.replace("f1", factory.textNode("newV1"));
        assertNotNull(replaced);
        assertEquals("v1", replaced.asText());

        JsonNode replacedNull = node.replace("f_null", null);
        assertTrue(replacedNull instanceof NullNode);
    }

    @Test
    public void testMutatorsRemoveAndRetain() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);
        node.put("a", 1);
        node.put("b", 2);
        node.put("c", 3);

        assertSame(node, node.without("a"));
        assertNull(node.get("a"));

        Collection<String> toRemove = Arrays.asList("b");
        assertSame(node, node.without(toRemove));
        assertNull(node.get("b"));

        node.put("x", 10);
        node.put("y", 20);
        assertSame(node, node.remove("x"));
        assertNull(node.get("x"));

        assertSame(node, node.remove(Arrays.asList("y")));
        
        node.put("keep1", 1);
        node.put("keep2", 2);
        node.put("drop", 3);
        
        node.retain("keep1", "keep2");
        assertNotNull(node.get("keep1"));
        assertNotNull(node.get("keep2"));
        assertNull(node.get("drop"));

        node.retain(Arrays.asList("keep1"));
        assertNotNull(node.get("keep1"));
        assertNull(node.get("keep2"));

        assertSame(node, node.removeAll());
        assertEquals(0, node.size());
    }

    @Test
    public void testDeprecatedPutAndPutAll() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);

        node.put("a", factory.textNode("val"));
        node.put("a_null", (JsonNode) null);

        Map<String, JsonNode> map = new HashMap<String, JsonNode>();
        map.put("b", factory.textNode("bval"));
        node.putAll(map);

        ObjectNode other = new ObjectNode(factory);
        other.put("c", "cval");
        node.putAll(other);

        assertEquals("cval", node.get("c").asText());
    }

    @Test
    public void testTypedPuts() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);

        node.putArray("arrField");
        node.putObject("objField");
        node.putPOJO("pojoField", "somePojo");
        node.putNull("nullField");

        node.put("shortPrim", (short) 1);
        node.put("shortBox", Short.valueOf((short) 2));
        node.put("shortNull", (Short) null);

        node.put("intPrim", 10);
        node.put("intBox", Integer.valueOf(20));
        node.put("intNull", (Integer) null);

        node.put("longPrim", 100L);
        node.put("longBox", Long.valueOf(200L));
        node.put("longNull", (Long) null);

        node.put("floatPrim", 1.5f);
        node.put("floatBox", Float.valueOf(2.5f));
        node.put("floatNull", (Float) null);

        node.put("doublePrim", 10.5);
        node.put("doubleBox", Double.valueOf(20.5));
        node.put("doubleNull", (Double) null);

        node.put("bigDec", new BigDecimal("123.45"));
        node.put("bigDecNull", (BigDecimal) null);

        node.put("str", "hello");
        node.put("strNull", (String) null);

        node.put("boolPrim", true);
        node.put("boolBox", Boolean.FALSE);
        node.put("boolNull", (Boolean) null);

        node.put("bytes", new byte[] { 1, 2, 3 });
        node.put("bytesNull", (byte[]) null);

        assertEquals(10, node.get("intPrim").asInt());
        assertTrue(node.get("strNull") instanceof NullNode);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode n1 = new ObjectNode(factory);
        n1.put("a", 1);

        ObjectNode n2 = new ObjectNode(factory);
        n2.put("a", 1);

        ObjectNode n3 = new ObjectNode(factory);
        n3.put("a", 2);

        assertTrue(n1.equals(n1));
        assertFalse(n1.equals(null));
        assertFalse(n1.equals("notAnObjectNode"));
        assertTrue(n1.equals(n2));
        assertFalse(n1.equals(n3));

        assertEquals(n1.hashCode(), n2.hashCode());
    }

    @Test
    public void testToString() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);
        node.put("key", "val");
        String json = node.toString();
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"val\""));
    }

    @Test
    public void testSerialization() throws Throwable {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode node = new ObjectNode(factory);
        node.put("testKey", "testVal");

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        StringWriter sw = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator jg = mapper.getFactory().createGenerator(sw);
        SerializerProvider provider = mapper.getSerializerProvider();

        node.serialize(jg, provider);
        jg.flush();
        assertTrue(sw.toString().contains("testKey"));

        StringWriter sw2 = new StringWriter();
        com.fasterxml.jackson.core.JsonGenerator jg2 = mapper.getFactory().createGenerator(sw2);
        com.fasterxml.jackson.databind.jsontype.impl.AsPropertyTypeSerializer typeSer = 
            new com.fasterxml.jackson.databind.jsontype.impl.AsPropertyTypeSerializer(null, null, "type");
        
        node.serializeWithType(jg2, provider, typeSer);
        jg2.flush();
        assertTrue(sw2.toString().contains("testKey"));
    }
}