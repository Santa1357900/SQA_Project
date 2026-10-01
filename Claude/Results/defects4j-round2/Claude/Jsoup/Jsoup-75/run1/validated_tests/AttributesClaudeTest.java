package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AttributesClaudeTest {

    // get(): existing key returns its value
    @Test
    public void testGet_existingKey_returnsValue() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("href", "http://example.com");
        assertEquals("http://example.com", attrs.get("href"));
    }

    // get(): missing key returns empty string, not null
    @Test
    public void testGet_nonExistingKey_returnsEmptyString() throws Throwable {
        Attributes attrs = new Attributes();
        assertEquals("", attrs.get("missing"));
    }

    // get(): boolean attribute (null internal value) returns empty string via checkNotNull
    @Test
    public void testGet_booleanAttribute_returnsEmptyString() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.putIgnoreCase("checked", null);
        assertEquals("", attrs.get("checked"));
    }

    // getIgnoreCase(): matches regardless of case
    @Test
    public void testGetIgnoreCase_matchesDifferentCase_returnsValue() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("Href", "x");
        assertEquals("x", attrs.getIgnoreCase("href"));
    }

    // getIgnoreCase(): missing key returns empty string
    @Test
    public void testGetIgnoreCase_nonExisting_returnsEmptyString() throws Throwable {
        Attributes attrs = new Attributes();
        assertEquals("", attrs.getIgnoreCase("missing"));
    }

    // indexOfKey(): found returns index, not found returns NotFound constant
    @Test
    public void testIndexOfKey_foundAndNotFound() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        assertEquals(0, attrs.indexOfKey("a"));
        assertEquals(Attributes.NotFound, attrs.indexOfKey("missing"));
    }

    // put(String,String): new key branch (add)
    @Test
    public void testPut_newKey_addsAttribute() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        assertEquals(1, attrs.size());
        assertEquals("1", attrs.get("a"));
    }

    // put(String,String): existing key branch (replace value)
    @Test
    public void testPut_existingKey_replacesValue() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        attrs.put("a", "2");
        assertEquals(1, attrs.size());
        assertEquals("2", attrs.get("a"));
    }

    // putIgnoreCase(): new key branch
    @Test
    public void testPutIgnoreCase_newKey_adds() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.putIgnoreCase("Key", "1");
        assertEquals(1, attrs.size());
        assertEquals("1", attrs.get("Key"));
    }

    // putIgnoreCase(): existing key different case updates value and key casing
    @Test
    public void testPutIgnoreCase_existingKeyDifferentCase_updatesValueAndKeyCase() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.putIgnoreCase("Key", "1");
        attrs.putIgnoreCase("KEY", "2");
        assertEquals(1, attrs.size());
        assertEquals("2", attrs.get("KEY"));
        assertEquals("", attrs.get("Key"));
    }

    // put(String,boolean) true branch: adds boolean (null-valued) attribute
    @Test
    public void testPutBoolean_true_addsBooleanAttribute() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("disabled", true);
        assertTrue(attrs.hasKeyIgnoreCase("disabled"));
        assertEquals("", attrs.get("disabled"));
        assertEquals(1, attrs.size());
    }



    // put(String,boolean) false branch with matching case: sanity check removal still works
    @Test
    public void testPutBoolean_false_removesSameCase() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("checked", true);
        attrs.put("checked", false);
        assertFalse(attrs.hasKey("checked"));
        assertEquals(0, attrs.size());
    }

    // put(Attribute): adds attribute and its value is accessible
    @Test
    public void testPutAttribute_addsAndSetsValue() throws Throwable {
        Attributes attrs = new Attributes();
        Attribute attribute = new Attribute("data-x", "y");
        attrs.put(attribute);
        assertEquals("y", attrs.get("data-x"));
        assertEquals(1, attrs.size());
    }

    // remove(String): existing key is removed (case sensitive)
    @Test
    public void testRemove_existingKey_removesIt() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        attrs.remove("a");
        assertEquals(0, attrs.size());
        assertFalse(attrs.hasKey("a"));
    }

    // remove(String): non-existing key is a no-op
    @Test
    public void testRemove_nonExistingKey_noOp() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        attrs.remove("b");
        assertEquals(1, attrs.size());
        assertTrue(attrs.hasKey("a"));
    }

    // remove(String): middle element removal exercises shifted>0 branch
    @Test
    public void testRemove_middleElement_shiftsRemaining() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        attrs.put("b", "2");
        attrs.put("c", "3");
        attrs.remove("b");
        assertEquals(2, attrs.size());
        assertEquals("1", attrs.get("a"));
        assertEquals("3", attrs.get("c"));
        assertFalse(attrs.hasKey("b"));
    }

    // removeIgnoreCase(): removes regardless of case
    @Test
    public void testRemoveIgnoreCase_removesRegardlessOfCase() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("Key", "1");
        attrs.removeIgnoreCase("key");
        assertEquals(0, attrs.size());
    }

    // hasKey(): case sensitive check
    @Test
    public void testHasKey_trueAndFalse() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        assertTrue(attrs.hasKey("a"));
        assertFalse(attrs.hasKey("A"));
        assertFalse(attrs.hasKey("b"));
    }

    // hasKeyIgnoreCase(): case insensitive check
    @Test
    public void testHasKeyIgnoreCase_trueAndFalse() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        assertTrue(attrs.hasKeyIgnoreCase("A"));
        assertFalse(attrs.hasKeyIgnoreCase("b"));
    }

    // size(): tracks adds and removes correctly
    @Test
    public void testSize_afterAddsAndRemoves() throws Throwable {
        Attributes attrs = new Attributes();
        assertEquals(0, attrs.size());
        attrs.put("a", "1");
        attrs.put("b", "2");
        assertEquals(2, attrs.size());
        attrs.remove("a");
        assertEquals(1, attrs.size());
    }

    // addAll(): empty incoming set leaves target unchanged (early return branch)
    @Test
    public void testAddAll_emptyIncoming_noChange() throws Throwable {
        Attributes a = new Attributes();
        a.put("a", "1");
        Attributes empty = new Attributes();
        a.addAll(empty);
        assertEquals(1, a.size());
    }

    // addAll(): non-empty incoming set adds all attributes
    @Test
    public void testAddAll_nonEmptyIncoming_addsAll() throws Throwable {
        Attributes a = new Attributes();
        a.put("a", "1");
        Attributes b = new Attributes();
        b.put("b", "2");
        b.put("c", "3");
        a.addAll(b);
        assertEquals(3, a.size());
        assertEquals("2", a.get("b"));
        assertEquals("3", a.get("c"));
    }

    // iterator(): hasNext/next traverse in insertion order
    @Test
    public void testIterator_hasNextNextAndOrder() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        attrs.put("b", "2");
        Iterator<Attribute> it = attrs.iterator();
        assertTrue(it.hasNext());
        Attribute first = it.next();
        assertEquals("a", first.getKey());
        assertTrue(it.hasNext());
        Attribute second = it.next();
        assertEquals("b", second.getKey());
        assertFalse(it.hasNext());
    }

    // iterator().remove(): removes the last returned element
    @Test
    public void testIteratorRemove_removesCurrentElement() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        attrs.put("b", "2");
        Iterator<Attribute> it = attrs.iterator();
        it.next();
        it.remove();
        assertEquals(1, attrs.size());
        assertFalse(attrs.hasKey("a"));
        assertTrue(attrs.hasKey("b"));
    }

    // asList(): returns correct content and is unmodifiable
    @Test
    public void testAsList_returnsAttributesAndIsUnmodifiable() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        attrs.put("b", "2");
        List<Attribute> list = attrs.asList();
        assertEquals(2, list.size());
        assertEquals("1", list.get(0).getValue());
        try {
            list.add(new Attribute("c", "3"));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }

    // dataset().put(): adds prefixed attribute and returns null for new key
    @Test
    public void testDataset_put_addsPrefixedAttribute() throws Throwable {
        Attributes attrs = new Attributes();
        Map<String, String> dataset = attrs.dataset();
        String old = dataset.put("lang", "en");
        assertNull(old);
        assertEquals("en", attrs.get("data-lang"));
    }

    // dataset().put(): replacing existing returns previous value
    @Test
    public void testDataset_put_replacesReturnsOldValue() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("data-lang", "en");
        Map<String, String> dataset = attrs.dataset();
        String old = dataset.put("lang", "fr");
        assertEquals("en", old);
        assertEquals("fr", attrs.get("data-lang"));
    }

    // dataset entrySet iterator only yields data- prefixed attributes
    @Test
    public void testDataset_entrySetIteratesOnlyDataAttributes() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("data-name", "jsoup");
        attrs.put("class", "x");
        Map<String, String> dataset = attrs.dataset();
        Iterator<Map.Entry<String, String>> it = dataset.entrySet().iterator();
        assertTrue(it.hasNext());
        Map.Entry<String, String> entry = it.next();
        assertEquals("name", entry.getKey());
        assertEquals("jsoup", entry.getValue());
        assertFalse(it.hasNext());
    }

    // dataset entrySet().size() counts only data attributes
    @Test
    public void testDataset_entrySetSize() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("data-a", "1");
        attrs.put("data-b", "2");
        attrs.put("other", "x");
        Map<String, String> dataset = attrs.dataset();
        assertEquals(2, dataset.entrySet().size());
    }

    // html(): renders " key=\"value\"" pairs in insertion order
    @Test
    public void testHtml_rendersKeyValuePairs() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("class", "test");
        attrs.put("id", "1");
        String html = attrs.html();
        assertEquals(" class=\"test\" id=\"1\"", html);
    }

    // toString(): delegates to html()
    @Test
    public void testToString_equalsHtml() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        assertEquals(attrs.html(), attrs.toString());
    }

    // equals(): same reference returns true (short-circuit branch)
    @Test
    public void testEquals_sameReference_returnsTrue() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        assertTrue(attrs.equals(attrs));
    }

    // equals(): null and different class both return false
    @Test
    public void testEquals_nullAndDifferentClass_returnsFalse() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("a", "1");
        assertFalse(attrs.equals(null));
        assertFalse(attrs.equals("not attributes"));
    }

    // equals(): different size returns false
    @Test
    public void testEquals_differentSize_returnsFalse() throws Throwable {
        Attributes a = new Attributes();
        a.put("a", "1");
        Attributes b = new Attributes();
        b.put("a", "1");
        b.put("b", "2");
        assertFalse(a.equals(b));
    }

    // equals()/hashCode(): identical construction yields equal objects with matching hashcodes
    @Test
    public void testEquals_and_hashCode_sameContentSameConstruction() throws Throwable {
        Attributes a = new Attributes();
        a.put("a", "1");
        Attributes b = new Attributes();
        b.put("a", "1");
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    // clone(): produces a copy with identical content
    @Test
    public void testClone_hasSameContentAsOriginal() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("one", "1");
        attrs.put("two", "2");
        Attributes clone = attrs.clone();
        assertEquals(2, clone.size());
        assertEquals("1", clone.get("one"));
        assertEquals("2", clone.get("two"));
    }

    // clone(): mutating the clone must not affect the original
    @Test
    public void testClone_modifyingCloneDoesNotAffectOriginal() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("one", "1");
        Attributes clone = attrs.clone();
        clone.put("one", "changed");
        clone.put("two", "2");
        assertEquals("1", attrs.get("one"));
        assertEquals(1, attrs.size());
    }

    // clone(): mutating the original after cloning must not affect the clone
    @Test
    public void testClone_modifyingOriginalDoesNotAffectClone() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("one", "1");
        Attributes clone = attrs.clone();
        attrs.put("one", "changed");
        attrs.put("two", "2");
        assertEquals("1", clone.get("one"));
        assertEquals(1, clone.size());
    }

    // normalize(): lowercases all keys
    @Test
    public void testNormalize_lowercasesKeys() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("ID", "x");
        attrs.put("Class", "y");
        attrs.normalize();
        assertTrue(attrs.hasKey("id"));
        assertTrue(attrs.hasKey("class"));
        assertFalse(attrs.hasKey("ID"));
    }

    // checkCapacity(): repeated puts force growth beyond InitialCapacity (loop many rounds)
    @Test
    public void testPut_manyAttributes_growsCapacityCorrectly() throws Throwable {
        Attributes attrs = new Attributes();
        for (int i = 0; i < 10; i++) {
            attrs.put("key" + i, "val" + i);
        }
        assertEquals(10, attrs.size());
        assertEquals("val0", attrs.get("key0"));
        assertEquals("val9", attrs.get("key9"));
    }
}
