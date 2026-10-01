package com.fasterxml.jackson.databind.node;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectNodeClaudeTest {

    private ObjectNode newObject() {
        return new ObjectNode(JsonNodeFactory.instance);
    }

    // Covers: public constructor taking pre-built children map
    @Test
    public void testConstructorWithMap_storesGivenEntries() throws Throwable {
        Map<String, JsonNode> kids = new LinkedHashMap<String, JsonNode>();
        kids.put("a", JsonNodeFactory.instance.numberNode(1));
        ObjectNode obj = new ObjectNode(JsonNodeFactory.instance, kids);
        assertEquals(1, obj.size());
        assertEquals(JsonNodeFactory.instance.numberNode(1), obj.get("a"));
    }

    // Covers: getNodeType() branch returning OBJECT
    @Test
    public void testGetNodeType_returnsObjectType() throws Throwable {
        assertEquals(JsonNodeType.OBJECT, newObject().getNodeType());
    }

    // Covers: asToken() returning START_OBJECT
    @Test
    public void testAsToken_returnsStartObjectToken() throws Throwable {
        assertEquals(JsonToken.START_OBJECT, newObject().asToken());
    }

    // Covers: size() for empty and populated maps
    @Test
    public void testSize_reflectsChildCount() throws Throwable {
        ObjectNode obj = newObject();
        assertEquals(0, obj.size());
        obj.put("a", 1);
        obj.put("b", 2);
        assertEquals(2, obj.size());
    }

    // Covers: fieldNames(), elements(), fields() iteration in insertion order
    @Test
    public void testElementsFieldNamesFields_iterateInInsertionOrder() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("a", 1);
        obj.put("b", 2);
        Iterator<String> names = obj.fieldNames();
        assertEquals("a", names.next());
        assertEquals("b", names.next());
        assertFalse(names.hasNext());
        Iterator<JsonNode> values = obj.elements();
        assertEquals(JsonNodeFactory.instance.numberNode(1), values.next());
        assertEquals(JsonNodeFactory.instance.numberNode(2), values.next());
        Iterator<Map.Entry<String, JsonNode>> entries = obj.fields();
        Map.Entry<String, JsonNode> first = entries.next();
        assertEquals("a", first.getKey());
    }

    // Covers: get(String) missing branch and get(int) always-null branch
    @Test
    public void testGet_missingOrIndexReturnsNull() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("a", 1);
        assertNull(obj.get("missing"));
        assertNull(obj.get(0));
        assertNull(obj.get(-1));
    }

    // Covers: path(int)/path(String) missing returns MissingNode, existing returns value
    @Test
    public void testPath_missingReturnsMissingNodeSingletonAndExistingReturnsValue() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("k", "v");
        assertEquals(JsonNodeFactory.instance.textNode("v"), obj.path("k"));
        assertSame(MissingNode.getInstance(), obj.path("missing"));
        assertSame(MissingNode.getInstance(), obj.path(0));
    }

    // Covers: with() branch creating new ObjectNode when absent
    @Test
    public void testWith_createsObjectNodeWhenMissing() throws Throwable {
        ObjectNode obj = newObject();
        ObjectNode child = obj.with("child");
        assertNotNull(child);
        assertSame(child, obj.get("child"));
    }

    // Covers: with() branch returning existing ObjectNode value
    @Test
    public void testWith_returnsExistingObjectNodeWhenPresent() throws Throwable {
        ObjectNode obj = newObject();
        ObjectNode child = obj.putObject("child");
        ObjectNode again = obj.with("child");
        assertSame(child, again);
    }

    // Covers: with() branch throwing when existing value is not ObjectNode
    @Test
    public void testWith_throwsWhenExistingValueIsNotObjectNode() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("child", "text");
        try {
            obj.with("child");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("child"));
        }
    }

    // Covers: withArray() branch creating new ArrayNode when absent
    @Test
    public void testWithArray_createsArrayNodeWhenMissing() throws Throwable {
        ObjectNode obj = newObject();
        ArrayNode arr = obj.withArray("arr");
        assertNotNull(arr);
        assertSame(arr, obj.get("arr"));
    }

    // Covers: withArray() branch returning existing ArrayNode value
    @Test
    public void testWithArray_returnsExistingArrayNodeWhenPresent() throws Throwable {
        ObjectNode obj = newObject();
        ArrayNode arr = obj.putArray("arr");
        ArrayNode again = obj.withArray("arr");
        assertSame(arr, again);
    }

    // Covers: withArray() branch throwing when existing value is not ArrayNode
    @Test
    public void testWithArray_throwsWhenExistingValueIsNotArrayNode() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("arr", "text");
        try {
            obj.withArray("arr");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("arr"));
        }
    }

    // Covers: findValue() direct-match branch (no recursion needed)
    @Test
    public void testFindValue_directMatchReturnsValue() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("target", "direct");
        assertEquals(JsonNodeFactory.instance.textNode("direct"), obj.findValue("target"));
    }

    // Covers: findValue() recursion into child ObjectNode branch
    @Test
    public void testFindValue_nestedMatchRecursesIntoChildren() throws Throwable {
        ObjectNode root = newObject();
        ObjectNode child = root.putObject("child");
        child.put("target", "nested");
        assertEquals(JsonNodeFactory.instance.textNode("nested"), root.findValue("target"));
    }

    // Covers: findValue() no-match branch returning null
    @Test
    public void testFindValue_noMatchReturnsNull() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("other", 1);
        assertNull(obj.findValue("missing"));
    }

    // Covers: findValues() accumulation across multiple matching children
    @Test
    public void testFindValues_accumulatesAcrossChildren() throws Throwable {
        ObjectNode root = newObject();
        ObjectNode c1 = root.putObject("c1");
        c1.put("n", 1);
        ObjectNode c2 = root.putObject("c2");
        c2.put("n", 2);
        List<JsonNode> found = root.findValues("n", null);
        assertEquals(2, found.size());
        assertEquals(JsonNodeFactory.instance.numberNode(1), found.get(0));
        assertEquals(JsonNodeFactory.instance.numberNode(2), found.get(1));
    }

    // Covers: findValuesAsText() converting matched values via asText()
    @Test
    public void testFindValuesAsText_convertsMatchesToText() throws Throwable {
        ObjectNode root = newObject();
        ObjectNode c1 = root.putObject("c1");
        c1.put("n", "alpha");
        ObjectNode c2 = root.putObject("c2");
        c2.put("n", "beta");
        List<String> found = root.findValuesAsText("n", null);
        assertEquals(2, found.size());
        assertEquals("alpha", found.get(0));
        assertEquals("beta", found.get(1));
    }

    // Covers: findParent() direct-match branch returning this node
    @Test
    public void testFindParent_directMatchReturnsSelf() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("target", 1);
        assertSame(obj, obj.findParent("target"));
    }

    // Covers: findParent() recursive branch returning containing child, not root
    @Test
    public void testFindParent_nestedMatchReturnsContainingChild() throws Throwable {
        ObjectNode root = newObject();
        ObjectNode child = root.putObject("child");
        child.put("target", 1);
        assertSame(child, root.findParent("target"));
    }

    // Covers: findParent() no-match branch returning null
    @Test
    public void testFindParent_noMatchReturnsNull() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("other", 1);
        assertNull(obj.findParent("missing"));
    }

    // Covers: findParents() collecting both root-level and nested containing nodes
    @Test
    public void testFindParents_collectsAllContainingNodes() throws Throwable {
        ObjectNode root = newObject();
        root.put("n", 1);
        ObjectNode child = root.putObject("child");
        child.put("n", 2);
        List<JsonNode> parents = root.findParents("n", null);
        assertEquals(2, parents.size());
        assertSame(root, parents.get(0));
        assertSame(child, parents.get(1));
    }

    // Covers: set() replacing value, returning this, and converting null to NullNode
    @Test
    public void testSet_replacesValueAndReturnsThisAndConvertsNull() throws Throwable {
        ObjectNode obj = newObject();
        JsonNode ret = obj.set("k", JsonNodeFactory.instance.textNode("v"));
        assertSame(obj, ret);
        assertEquals(JsonNodeFactory.instance.textNode("v"), obj.get("k"));
        obj.set("k2", null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("k2"));
    }

    // Covers: setAll(Map) adding new field and overriding existing, converting null
    @Test
    public void testSetAllMap_addsAndOverridesConvertingNulls() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("keep", 1);
        Map<String, JsonNode> updates = new LinkedHashMap<String, JsonNode>();
        updates.put("keep", JsonNodeFactory.instance.numberNode(99));
        updates.put("added", null);
        JsonNode ret = obj.setAll(updates);
        assertSame(obj, ret);
        assertEquals(JsonNodeFactory.instance.numberNode(99), obj.get("keep"));
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("added"));
    }

    // Covers: setAll(ObjectNode) merging another node's children
    @Test
    public void testSetAllObjectNode_mergesOtherChildren() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("a", 1);
        ObjectNode other = newObject();
        other.put("b", 2);
        JsonNode ret = obj.setAll(other);
        assertSame(obj, ret);
        assertEquals(2, obj.size());
        assertEquals(JsonNodeFactory.instance.numberNode(2), obj.get("b"));
    }

    // Covers: replace() returning null when absent then old value, and deprecated put() returning old value
    @Test
    public void testReplaceAndDeprecatedPut_returnOldValueOrNull() throws Throwable {
        ObjectNode obj = newObject();
        JsonNode old1 = obj.replace("k", JsonNodeFactory.instance.textNode("first"));
        assertNull(old1);
        JsonNode old2 = obj.replace("k", JsonNodeFactory.instance.textNode("second"));
        assertEquals(JsonNodeFactory.instance.textNode("first"), old2);
        JsonNode old3 = obj.put("k", JsonNodeFactory.instance.textNode("third"));
        assertEquals(JsonNodeFactory.instance.textNode("second"), old3);
        assertEquals(JsonNodeFactory.instance.textNode("third"), obj.get("k"));
    }

    // Covers: without(String) single removal and without(Collection) multi removal
    @Test
    public void testWithout_removesSingleAndMultipleFields() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("a", 1);
        obj.put("b", 2);
        obj.put("c", 3);
        JsonNode ret1 = obj.without("a");
        assertSame(obj, ret1);
        assertNull(obj.get("a"));
        ObjectNode ret2 = obj.without(Arrays.asList("b", "missing"));
        assertSame(obj, ret2);
        assertNull(obj.get("b"));
        assertEquals(1, obj.size());
    }

    // Covers: remove(String) returning removed value and null when absent
    @Test
    public void testRemoveString_returnsRemovedValueOrNullWhenMissing() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("a", 1);
        JsonNode removed = obj.remove("a");
        assertEquals(JsonNodeFactory.instance.numberNode(1), removed);
        assertNull(obj.remove("a"));
    }

    // Covers: remove(Collection) partial removal and removeAll() clearing all
    @Test
    public void testRemoveCollectionAndRemoveAll_removeAsExpected() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("a", 1);
        obj.put("b", 2);
        obj.put("c", 3);
        ObjectNode ret1 = obj.remove(Arrays.asList("a", "b"));
        assertSame(obj, ret1);
        assertEquals(1, obj.size());
        ObjectNode ret2 = obj.removeAll();
        assertSame(obj, ret2);
        assertEquals(0, obj.size());
    }

    // Covers: retain(Collection) and retain(String...) keeping only specified fields
    @Test
    public void testRetain_keepsOnlySpecifiedFieldsForCollectionAndVarargs() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("a", 1);
        obj.put("b", 2);
        obj.put("c", 3);
        ObjectNode ret1 = obj.retain(Arrays.asList("b"));
        assertSame(obj, ret1);
        assertEquals(1, obj.size());
        obj.put("c", 3);
        ObjectNode ret2 = obj.retain("b", "c");
        assertEquals(2, obj.size());
        assertNotNull(obj.get("b"));
        assertNotNull(obj.get("c"));
    }

    // Covers: putArray() and putObject() returning the newly created node, not this
    @Test
    public void testPutArrayAndPutObject_returnNewNodeNotThis() throws Throwable {
        ObjectNode obj = newObject();
        ArrayNode arr = obj.putArray("arr");
        assertNotSame(obj, arr);
        assertSame(arr, obj.get("arr"));
        ObjectNode child = obj.putObject("child");
        assertNotSame(obj, child);
        assertSame(child, obj.get("child"));
    }

    // Covers: putPOJO() returning this and storing a non-null node, putNull() storing NullNode
    @Test
    public void testPutPOJOAndPutNull_storeExpectedNodes() throws Throwable {
        ObjectNode obj = newObject();
        ObjectNode ret1 = obj.putPOJO("p", "somePojo");
        assertSame(obj, ret1);
        assertNotNull(obj.get("p"));
        ObjectNode ret2 = obj.putNull("n");
        assertSame(obj, ret2);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("n"));
    }

    // Covers: put(short/int/long/float/double/BigDecimal) storing proper number nodes
    @Test
    public void testPutNumberTypes_storeExpectedNumberNodes() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("s", (short) 5);
        assertEquals(JsonNodeFactory.instance.numberNode((short) 5), obj.get("s"));
        obj.put("i", 7);
        assertEquals(JsonNodeFactory.instance.numberNode(7), obj.get("i"));
        obj.put("l", 123456789012L);
        assertEquals(JsonNodeFactory.instance.numberNode(123456789012L), obj.get("l"));
        obj.put("f", 1.5f);
        assertEquals(JsonNodeFactory.instance.numberNode(1.5f), obj.get("f"));
        obj.put("d", 2.5d);
        assertEquals(JsonNodeFactory.instance.numberNode(2.5d), obj.get("d"));
        obj.put("bd", new BigDecimal("3.14"));
        assertEquals(JsonNodeFactory.instance.numberNode(new BigDecimal("3.14")), obj.get("bd"));
    }

    // Covers: put(Short/Integer/Long/Float/Double/BigDecimal) null-wrapper branches converting to NullNode
    @Test
    public void testPutNumberWrapperNulls_convertToNullNode() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("s", (Short) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("s"));
        obj.put("i", (Integer) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("i"));
        obj.put("l", (Long) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("l"));
        obj.put("f", (Float) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("f"));
        obj.put("d", (Double) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("d"));
        obj.put("bd", (BigDecimal) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("bd"));
    }

    // Covers: put(String), put(boolean/Boolean), put(byte[]) including null-conversion branches
    @Test
    public void testPutStringBooleanByteArray_storeExpectedNodesOrNull() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("str", "hello");
        assertEquals(JsonNodeFactory.instance.textNode("hello"), obj.get("str"));
        obj.put("strNull", (String) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("strNull"));
        obj.put("bool", true);
        assertEquals(JsonNodeFactory.instance.booleanNode(true), obj.get("bool"));
        obj.put("boolNull", (Boolean) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("boolNull"));
        byte[] bytes = new byte[] {1, 2, 3};
        obj.put("bytes", bytes);
        assertEquals(JsonNodeFactory.instance.binaryNode(bytes), obj.get("bytes"));
        obj.put("bytesNull", (byte[]) null);
        assertEquals(JsonNodeFactory.instance.nullNode(), obj.get("bytesNull"));
    }

    // Covers: equals() reflexive/null/different-type/equal-content/different-content branches, hashCode() consistency
    @Test
    public void testEqualsAndHashCode_contractHonored() throws Throwable {
        ObjectNode a = newObject();
        a.put("x", 1);
        ObjectNode b = newObject();
        b.put("x", 1);
        assertTrue(a.equals(a));
        assertFalse(a.equals(null));
        assertFalse(a.equals(JsonNodeFactory.instance.textNode("x")));
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
        b.put("x", 2);
        assertFalse(a.equals(b));
    }

    // Covers: toString() empty-object branch, single-field branch, and comma-separated multi-field branch
    @Test
    public void testToString_formatsFieldsCorrectly() throws Throwable {
        ObjectNode empty = newObject();
        assertEquals("{}", empty.toString());
        ObjectNode one = newObject();
        one.putObject("child");
        assertEquals("{\"child\":{}}", one.toString());
        ObjectNode two = newObject();
        two.putObject("a");
        two.putObject("b");
        assertEquals("{\"a\":{},\"b\":{}}", two.toString());
    }

    // Covers: deepCopy() producing an independent clone (mutating copy must not affect original)
    @Test
    public void testDeepCopy_isIndependentDeepClone() throws Throwable {
        ObjectNode original = newObject();
        ObjectNode nested = original.putObject("nested");
        nested.put("v", 1);
        ObjectNode copy = original.deepCopy();
        assertEquals(original, copy);
        assertNotSame(original, copy);
        JsonNode copiedNested = copy.get("nested");
        assertNotSame(nested, copiedNested);
        ((ObjectNode) copiedNested).put("v", 2);
        assertEquals(JsonNodeFactory.instance.numberNode(1), nested.get("v"));
    }

    // Covers: serialize() writing all fields correctly, round-tripped through ObjectMapper
    @Test
    public void testSerialize_viaObjectMapperRoundTrips() throws Throwable {
        ObjectNode obj = newObject();
        obj.put("name", "Bob");
        obj.put("age", 42);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(obj);
        JsonNode parsed = mapper.readTree(json);
        assertEquals(obj, parsed);
    }
}
