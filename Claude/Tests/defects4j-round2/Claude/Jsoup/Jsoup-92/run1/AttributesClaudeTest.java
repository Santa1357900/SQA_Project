package org.jsoup.nodes;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AttributesClaudeTest {

    private Attributes attrs;

    @Before
    public void setUp() throws Throwable {
        attrs = new Attributes();
    }

    // get(): key present with explicit value -> returns stored value
    @Test
    public void testGet_existingKey_returnsValue() throws Throwable {
        attrs.put("key", "value");
        assertEquals("value", attrs.get("key"));
    }

    // get(): key not found (indexOfKey == NotFound) -> returns empty string, not null
    @Test
    public void testGet_missingKey_returnsEmptyString() throws Throwable {
        assertEquals("", attrs.get("missing"));
    }

    // get(): boolean attribute (val stored as null) -> checkNotNull converts to ""
    @Test
    public void testGet_booleanAttribute_returnsEmptyStringNotNull() throws Throwable {
        attrs.put("disabled", true);
        assertEquals("", attrs.get("disabled"));
    }

    // getIgnoreCase(): case-insensitive match found -> value returned
    @Test
    public void testGetIgnoreCase_differentCaseKey_returnsValue() throws Throwable {
        attrs.put("Class", "foo");
        assertEquals("foo", attrs.getIgnoreCase("class"));
    }

    // getIgnoreCase(): no match -> empty string
    @Test
    public void testGetIgnoreCase_missingKey_returnsEmptyString() throws Throwable {
        assertEquals("", attrs.getIgnoreCase("missing"));
    }

    // put(String,String): key not present -> add() branch, new entry created
    @Test
    public void testPut_newKey_addsAttribute() throws Throwable {
        attrs.put("a", "1");
        assertTrue(attrs.hasKey("a"));
        assertEquals(1, attrs.size());
    }

    // put(String,String): key already present -> vals[i] replaced, size unchanged
    @Test
    public void testPut_existingKey_replacesValue() throws Throwable {
        attrs.put("a", "1");
        attrs.put("a", "2");
        assertEquals("2", attrs.get("a"));
        assertEquals(1, attrs.size());
    }

    // putIgnoreCase(): existing key same case -> value updated, key string unchanged
    @Test
    public void testPutIgnoreCase_sameCaseKeyExists_updatesValueKeepsKey() throws Throwable {
        attrs.put("foo", "1");
        attrs.putIgnoreCase("foo", "2");
        assertEquals("2", attrs.get("foo"));
        assertEquals(1, attrs.size());
    }

    // putIgnoreCase(): existing key different case -> key AND value updated
    @Test
    public void testPutIgnoreCase_differentCaseKeyExists_updatesKeyAndValue() throws Throwable {
        attrs.put("Foo", "1");
        attrs.putIgnoreCase("foo", "2");
        assertEquals("2", attrs.get("foo"));
        assertFalse(attrs.hasKey("Foo"));
        assertEquals(1, attrs.size());
    }

    // putIgnoreCase(): key not found at all -> add() branch (else)
    @Test
    public void testPutIgnoreCase_keyNotExists_addsNewEntry() throws Throwable {
        attrs.putIgnoreCase("foo", "1");
        assertEquals("1", attrs.get("foo"));
        assertEquals(1, attrs.size());
    }

    // put(String,boolean) true branch -> putIgnoreCase(key,null), get() returns ""
    @Test
    public void testPutBoolean_trueValue_addsAttributeWithNullValueAndEmptyGet() throws Throwable {
        attrs.put("disabled", true);
        assertTrue(attrs.hasKey("disabled"));
        assertEquals("", attrs.get("disabled"));
    }

    // put(String,boolean) false branch, key exists -> remove() called, attribute gone
    @Test
    public void testPutBoolean_falseValue_removesExistingAttribute() throws Throwable {
        attrs.put("disabled", true);
        attrs.put("disabled", false);
        assertFalse(attrs.hasKey("disabled"));
        assertEquals(0, attrs.size());
    }

    // put(String,boolean) false branch, key absent -> remove() is a no-op
    @Test
    public void testPutBoolean_falseValue_keyNotExists_noOp() throws Throwable {
        attrs.put("disabled", false);
        assertEquals(0, attrs.size());
        assertFalse(attrs.hasKey("disabled"));
    }

    // put(Attribute): adds entry and sets attribute.parent to this Attributes instance
    @Test
    public void testPutAttribute_addsAttributeAndSetsParent() throws Throwable {
        Attribute attr = new Attribute("foo", "bar");
        attrs.put(attr);
        assertEquals("bar", attrs.get("foo"));
        assertSame(attrs, attr.parent);
    }

    // remove(String): key found -> removed, shift-up logic executed
    @Test
    public void testRemove_existingKey_removesAttribute() throws Throwable {
        attrs.put("a", "1");
        attrs.remove("a");
        assertFalse(attrs.hasKey("a"));
        assertEquals(0, attrs.size());
    }

    // remove(String): key not found -> no-op, size and content unchanged
    @Test
    public void testRemove_missingKey_noOp() throws Throwable {
        attrs.put("a", "1");
        attrs.remove("b");
        assertEquals(1, attrs.size());
        assertTrue(attrs.hasKey("a"));
    }

    // removeIgnoreCase(): case-insensitive match removed
    @Test
    public void testRemoveIgnoreCase_differentCaseKey_removesAttribute() throws Throwable {
        attrs.put("Foo", "1");
        attrs.removeIgnoreCase("foo");
        assertFalse(attrs.hasKey("Foo"));
        assertEquals(0, attrs.size());
    }

    // hasKey(): case-sensitive true/false branches
    @Test
    public void testHasKey_existingAndMissingKey() throws Throwable {
        attrs.put("a", "1");
        assertTrue(attrs.hasKey("a"));
        assertFalse(attrs.hasKey("b"));
        assertFalse(attrs.hasKey("A"));
    }

    // hasKeyIgnoreCase(): case-insensitive true branch
    @Test
    public void testHasKeyIgnoreCase_differentCaseKey_true() throws Throwable {
        attrs.put("A", "1");
        assertTrue(attrs.hasKeyIgnoreCase("a"));
    }

    // size(): grows with distinct puts, stays flat for duplicate key
    @Test
    public void testSize_afterMultiplePuts() throws Throwable {
        attrs.put("a", "1");
        attrs.put("b", "2");
        attrs.put("a", "3");
        assertEquals(2, attrs.size());
    }

    // addAll(): incoming.size()==0 -> early return branch, no change
    @Test
    public void testAddAll_emptyIncoming_noChange() throws Throwable {
        attrs.put("a", "1");
        attrs.addAll(new Attributes());
        assertEquals(1, attrs.size());
        assertEquals("1", attrs.get("a"));
    }

    // addAll(): non-empty incoming -> loop merges every attribute via put()
    @Test
    public void testAddAll_nonEmptyIncoming_mergesAttributes() throws Throwable {
        attrs.put("a", "1");
        Attributes incoming = new Attributes();
        incoming.put("b", "2");
        incoming.put("c", "3");
        attrs.addAll(incoming);
        assertEquals(3, attrs.size());
        assertEquals("2", attrs.get("b"));
        assertEquals("3", attrs.get("c"));
    }

    // iterator(): hasNext/next traverse entries in insertion order
    @Test
    public void testIterator_iteratesInInsertionOrder() throws Throwable {
        attrs.put("a", "1");
        attrs.put("b", "2");
        Iterator<Attribute> it = attrs.iterator();
        Attribute first = it.next();
        assertEquals("a", first.getKey());
        Attribute second = it.next();
        assertEquals("b", second.getKey());
        assertFalse(it.hasNext());
    }

    // iterator().remove(): removes currently-yielded attribute from backing Attributes
    @Test
    public void testIteratorRemove_removesCurrentAttribute() throws Throwable {
        attrs.put("a", "1");
        attrs.put("b", "2");
        Iterator<Attribute> it = attrs.iterator();
        it.next();
        it.remove();
        assertEquals(1, attrs.size());
        assertFalse(attrs.hasKey("a"));
        assertTrue(attrs.hasKey("b"));
    }

    // asList(): regular attribute content correctly represented
    @Test
    public void testAsList_returnsCorrectContent() throws Throwable {
        attrs.put("a", "1");
        List<Attribute> list = attrs.asList();
        assertEquals(1, list.size());
        assertEquals("a", list.get(0).getKey());
        assertEquals("1", list.get(0).getValue());
    }

    // asList(): boolean attribute (null value) represented as BooleanAttribute
    @Test
    public void testAsList_booleanAttributeRepresented() throws Throwable {
        attrs.put("disabled", true);
        List<Attribute> list = attrs.asList();
        assertEquals(1, list.size());
        assertTrue(list.get(0) instanceof BooleanAttribute);
    }

    // asList(): returned list is unmodifiable -> add() throws UnsupportedOperationException
    @Test
    public void testAsList_addThrowsUnsupportedOperationException() throws Throwable {
        attrs.put("a", "1");
        List<Attribute> list = attrs.asList();
        try {
            list.add(new Attribute("b", "2"));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // dataset(): Map.put() adds attribute with data- prefix
    @Test
    public void testDataset_putAddsDataPrefixedAttribute() throws Throwable {
        Map<String, String> ds = attrs.dataset();
        ds.put("foo", "bar");
        assertTrue(attrs.hasKey("data-foo"));
        assertEquals("bar", attrs.get("data-foo"));
    }

    // dataset(): entrySet only exposes data- prefixed attributes, strips prefix for get()
    @Test
    public void testDataset_entrySetFiltersOnlyDataAttributes() throws Throwable {
        attrs.put("data-foo", "1");
        attrs.put("class", "x");
        Map<String, String> ds = attrs.dataset();
        assertEquals(1, ds.size());
        assertEquals("1", ds.get("foo"));
    }

    // dataset() iterator remove(): removes the underlying data- attribute from Attributes
    @Test
    public void testDataset_iteratorRemove_removesUnderlyingAttribute() throws Throwable {
        attrs.put("data-foo", "1");
        Map<String, String> ds = attrs.dataset();
        Iterator<Map.Entry<String, String>> it = ds.entrySet().iterator();
        assertTrue(it.hasNext());
        it.next();
        it.remove();
        assertFalse(attrs.hasKey("data-foo"));
    }

    // html(): renders key="value" pair for a non-boolean attribute
    @Test
    public void testHtml_rendersKeyValuePair() throws Throwable {
        attrs.put("class", "foo");
        String html = attrs.html();
        assertTrue(html.contains("class=\"foo\""));
    }

    // html(): boolean attribute (shouldCollapseAttribute) collapses, no ="..." part written
    @Test
    public void testHtml_collapsesBooleanAttribute() throws Throwable {
        attrs.put("disabled", true);
        String html = attrs.html();
        assertTrue(html.contains("disabled"));
        assertFalse(html.contains("disabled=\""));
    }

    // toString(): delegates to html(), must produce identical output
    @Test
    public void testToString_equalsHtml() throws Throwable {
        attrs.put("a", "1");
        assertEquals(attrs.html(), attrs.toString());
    }

    // equals(): same size and same key/value arrays -> true, symmetric
    @Test
    public void testEquals_sameContent_true() throws Throwable {
        Attributes a1 = new Attributes();
        a1.put("x", "1");
        Attributes a2 = new Attributes();
        a2.put("x", "1");
        assertTrue(a1.equals(a2));
        assertTrue(a2.equals(a1));
    }

    // equals(): differing values -> false
    @Test
    public void testEquals_differentContent_false() throws Throwable {
        Attributes a1 = new Attributes();
        a1.put("x", "1");
        Attributes a2 = new Attributes();
        a2.put("x", "2");
        assertFalse(a1.equals(a2));
    }

    // equals(): null and different class -> false branch
    @Test
    public void testEquals_differentClassOrNull_false() throws Throwable {
        Attributes a1 = new Attributes();
        assertFalse(a1.equals("not attributes"));
        assertFalse(a1.equals(null));
    }

    // hashCode(): equal attributes must produce equal hash codes
    @Test
    public void testHashCode_consistentWithEquals() throws Throwable {
        Attributes a1 = new Attributes();
        a1.put("x", "1");
        Attributes a2 = new Attributes();
        a2.put("x", "1");
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    // clone(): general clone() contract - must NOT mutate the original's internal state/arrays
    @Test
    public void testClone_doesNotMutateOriginal() throws Throwable {
        Attributes original = new Attributes();
        original.put("key1", "val1");
        String[] keysBefore = original.keys;
        String[] valsBefore = original.vals;
        original.clone();
        assertSame(keysBefore, original.keys);
        assertSame(valsBefore, original.vals);
    }

    // clone(): resulting clone has equivalent content to the source
    @Test
    public void testClone_cloneHasSameContentAsOriginal() throws Throwable {
        Attributes original = new Attributes();
        original.put("a", "1");
        original.put("b", "2");
        Attributes clone = original.clone();
        assertEquals(original.size(), clone.size());
        assertEquals(original.get("a"), clone.get("a"));
        assertEquals(original.get("b"), clone.get("b"));
    }

    // clone(): mutating the clone afterwards must not affect the original
    @Test
    public void testClone_modifyingCloneDoesNotAffectOriginal() throws Throwable {
        Attributes original = new Attributes();
        original.put("a", "1");
        Attributes clone = original.clone();
        clone.put("b", "2");
        assertFalse(original.hasKey("b"));
        assertEquals(1, original.size());
    }

    // normalize(): all keys lower-cased in place
    @Test
    public void testNormalize_lowercasesKeys() throws Throwable {
        attrs.put("Class", "foo");
        attrs.normalize();
        assertTrue(attrs.hasKey("class"));
        assertEquals("foo", attrs.get("class"));
    }

    // checkCapacity(): growth beyond InitialCapacity(4) via repeated puts still keeps correct content
    @Test
    public void testCheckCapacity_growthBeyondInitialCapacity() throws Throwable {
        for (int i = 0; i < 5; i++) {
            attrs.put("k" + i, "v" + i);
        }
        assertEquals(5, attrs.size());
        assertEquals("v4", attrs.get("k4"));
        assertEquals("v0", attrs.get("k0"));
    }
}
