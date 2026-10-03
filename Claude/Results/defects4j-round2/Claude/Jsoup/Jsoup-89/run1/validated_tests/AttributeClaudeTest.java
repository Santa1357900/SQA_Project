package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttributeClaudeTest {

    // Constructor: valid key/value stored as-is
    @Test
    public void testConstructor_validKeyValue_storesAsIs() throws Throwable {
        Attribute a = new Attribute("foo", "bar");
        assertEquals("foo", a.getKey());
        assertEquals("bar", a.getValue());
    }

    // Constructor: leading/trailing whitespace in key is trimmed
    @Test
    public void testConstructor_keyWithWhitespace_trimsKey() throws Throwable {
        Attribute a = new Attribute("  foo  ", "bar");
        assertEquals("foo", a.getKey());
    }

    // Constructor: null key throws IllegalArgumentException (Validate.notNull)
    @Test
    public void testConstructor_nullKey_throwsIllegalArgumentException() throws Throwable {
        try {
            new Attribute(null, "bar");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Constructor: key that becomes empty after trim throws IllegalArgumentException
    @Test
    public void testConstructor_blankKeyAfterTrim_throwsIllegalArgumentException() throws Throwable {
        try {
            new Attribute("   ", "bar");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // 3-arg constructor with null parent behaves like the 2-arg constructor
    @Test
    public void testConstructorWithParent_nullParent_setsKeyAndValue() throws Throwable {
        Attribute a = new Attribute("foo", "bar", null);
        assertEquals("foo", a.getKey());
        assertEquals("bar", a.getValue());
    }

    // getKey returns the stored key
    @Test
    public void testGetKey_returnsStoredKey() throws Throwable {
        Attribute a = new Attribute("Data-X", "1");
        assertEquals("Data-X", a.getKey());
    }

    // setKey updates the key when no parent is attached
    @Test
    public void testSetKey_updatesKey() throws Throwable {
        Attribute a = new Attribute("foo", "bar");
        a.setKey("baz");
        assertEquals("baz", a.getKey());
    }

    // setKey trims whitespace from the new key
    @Test
    public void testSetKey_trimsWhitespace() throws Throwable {
        Attribute a = new Attribute("foo", "bar");
        a.setKey("  qux  ");
        assertEquals("qux", a.getKey());
    }

    // setKey(null) throws IllegalArgumentException
    @Test
    public void testSetKey_nullKey_throwsIllegalArgumentException() throws Throwable {
        Attribute a = new Attribute("foo", "bar");
        try {
            a.setKey(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setKey with key blank after trim throws IllegalArgumentException
    @Test
    public void testSetKey_blankAfterTrim_throwsIllegalArgumentException() throws Throwable {
        Attribute a = new Attribute("foo", "bar");
        try {
            a.setKey("   ");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // getValue returns the stored value
    @Test
    public void testGetValue_returnsStoredValue() throws Throwable {
        Attribute a = new Attribute("foo", "bar");
        assertEquals("bar", a.getValue());
    }

    // getValue never exposes null; null value normalized to empty string
    @Test
    public void testGetValue_nullValue_returnsEmptyStringNotNull() throws Throwable {
        Attribute a = new Attribute("foo", null);
        assertEquals("", a.getValue());
    }

    // Bug: setValue on an Attribute without a parent must not NPE, and must return prior value
    @Test
    public void testSetValue_parentNull_returnsOldValueWithoutNPE() throws Throwable {
        Attribute a = new Attribute("foo", "bar");
        String oldVal = a.setValue("baz");
        assertEquals("bar", oldVal);
        assertEquals("baz", a.getValue());
    }

    // setValue updates the stored value so subsequent getValue reflects it
    @Test
    public void testSetValue_updatesStoredValue() throws Throwable {
        Attribute a = new Attribute("foo", "one");
        a.setValue("two");
        assertEquals("two", a.getValue());
    }

    // html(): non-boolean attribute renders as key="value"
    @Test
    public void testHtml_simpleAttribute_formatsAsKeyEqualsQuotedValue() throws Throwable {
        Attribute a = new Attribute("class", "btn");
        assertEquals("class=\"btn\"", a.html());
    }

    // html(): boolean attribute with empty value collapses to just the key
    @Test
    public void testHtml_booleanAttributeEmptyValue_collapsesToKeyOnly() throws Throwable {
        Attribute a = new Attribute("hidden", "");
        assertEquals("hidden", a.html());
    }

    // html(): boolean attribute whose value equals the key (ignore case) collapses
    @Test
    public void testHtml_booleanAttributeValueSameAsKeyIgnoreCase_collapses() throws Throwable {
        Attribute a = new Attribute("hidden", "HIDDEN");
        assertEquals("hidden", a.html());
    }

    // html(): boolean attribute with a different, non-empty value does not collapse
    @Test
    public void testHtml_booleanKeyDifferentValue_doesNotCollapse() throws Throwable {
        Attribute a = new Attribute("hidden", "true");
        assertEquals("hidden=\"true\"", a.html());
    }

    // toString() is implemented as html()
    @Test
    public void testToString_equalsHtml() throws Throwable {
        Attribute a = new Attribute("class", "btn");
        assertEquals(a.html(), a.toString());
    }

    // createFromEncoded decodes HTML entities in the value
    @Test
    public void testCreateFromEncoded_decodesHtmlEntity() throws Throwable {
        Attribute a = Attribute.createFromEncoded("name", "&amp;");
        assertEquals("&", a.getValue());
    }

    // createFromEncoded preserves the given key unchanged
    @Test
    public void testCreateFromEncoded_keyPreserved() throws Throwable {
        Attribute a = Attribute.createFromEncoded("data-x", "val");
        assertEquals("data-x", a.getKey());
    }

    // isDataAttribute (instance): key with data- prefix and extra chars is a data attribute
    @Test
    public void testIsDataAttribute_withDataPrefix_true() throws Throwable {
        Attribute a = new Attribute("data-foo", "1");
        assertTrue(a.isDataAttribute());
    }

    // isDataAttribute (instance): key without data- prefix is not a data attribute
    @Test
    public void testIsDataAttribute_withoutDataPrefix_false() throws Throwable {
        Attribute a = new Attribute("foo", "1");
        assertFalse(a.isDataAttribute());
    }

    // isDataAttribute (static): key exactly equal to prefix length is not a data attribute
    @Test
    public void testIsDataAttributeStatic_exactlyPrefixLength_false() throws Throwable {
        assertFalse(Attribute.isDataAttribute("data-"));
    }

    // isDataAttribute (static): key longer than the prefix with prefix match is a data attribute
    @Test
    public void testIsDataAttributeStatic_longerThanPrefix_true() throws Throwable {
        assertTrue(Attribute.isDataAttribute("data-x"));
    }

    // shouldCollapseAttribute (instance): boolean key with empty value collapses under html syntax
    @Test
    public void testShouldCollapseAttributeInstance_booleanEmptyValue_true() throws Throwable {
        Attribute a = new Attribute("checked", "");
        Document.OutputSettings out = new Document("").outputSettings();
        assertTrue(a.shouldCollapseAttribute(out));
    }

    // shouldCollapseAttribute (static): non-boolean key with non-empty value never collapses
    @Test
    public void testShouldCollapseAttributeStatic_nonBooleanKeyNonEmptyValue_false() throws Throwable {
        Document.OutputSettings out = new Document("").outputSettings();
        assertFalse(Attribute.shouldCollapseAttribute("class", "btn", out));
    }

    // shouldCollapseAttribute (static): boolean key with null value collapses under html syntax
    @Test
    public void testShouldCollapseAttributeStatic_booleanKeyNullValue_true() throws Throwable {
        Document.OutputSettings out = new Document("").outputSettings();
        assertTrue(Attribute.shouldCollapseAttribute("checked", null, out));
    }

    // isBooleanAttribute (instance, deprecated): known boolean key with non-null value is boolean
    @Test
    public void testIsBooleanAttributeInstance_knownBooleanKey_true() throws Throwable {
        Attribute a = new Attribute("checked", "checked");
        assertTrue(a.isBooleanAttribute());
    }

    // isBooleanAttribute (instance): null value makes it boolean regardless of key
    @Test
    public void testIsBooleanAttributeInstance_nullValue_trueRegardlessOfKey() throws Throwable {
        Attribute a = new Attribute("href", null);
        assertTrue(a.isBooleanAttribute());
    }

    // isBooleanAttribute (static): known HTML5 boolean attribute name returns true
    @Test
    public void testIsBooleanAttributeStatic_knownKey_true() throws Throwable {
        assertTrue(Attribute.isBooleanAttribute("checked"));
    }

    // isBooleanAttribute (static): unknown key returns false
    @Test
    public void testIsBooleanAttributeStatic_unknownKey_false() throws Throwable {
        assertFalse(Attribute.isBooleanAttribute("href"));
    }

    // equals: same key and value are equal
    @Test
    public void testEquals_sameKeyAndValue_true() throws Throwable {
        Attribute a1 = new Attribute("foo", "bar");
        Attribute a2 = new Attribute("foo", "bar");
        assertTrue(a1.equals(a2));
    }

    // equals: same reference is equal (identity shortcut)
    @Test
    public void testEquals_sameReference_true() throws Throwable {
        Attribute a1 = new Attribute("foo", "bar");
        assertTrue(a1.equals(a1));
    }

    // equals: different value makes attributes unequal
    @Test
    public void testEquals_differentValue_false() throws Throwable {
        Attribute a1 = new Attribute("foo", "bar");
        Attribute a2 = new Attribute("foo", "baz");
        assertFalse(a1.equals(a2));
    }

    // equals: null and a different class are both unequal
    @Test
    public void testEquals_differentClassOrNull_false() throws Throwable {
        Attribute a1 = new Attribute("foo", "bar");
        assertFalse(a1.equals(null));
        assertFalse(a1.equals("foo"));
    }

    // hashCode: equal objects produce the same hash code
    @Test
    public void testHashCode_equalObjectsHaveSameHashCode() throws Throwable {
        Attribute a1 = new Attribute("foo", "bar");
        Attribute a2 = new Attribute("foo", "bar");
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    // clone: produces an equal but distinct instance
    @Test
    public void testClone_producesEqualIndependentCopy() throws Throwable {
        Attribute original = new Attribute("foo", "bar");
        Attribute copy = original.clone();
        assertEquals(original, copy);
        assertNotSame(original, copy);
    }
}
