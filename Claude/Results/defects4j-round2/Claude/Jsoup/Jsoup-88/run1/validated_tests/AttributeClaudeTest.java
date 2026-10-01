package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttributeClaudeTest {

    // Validate.notNull(key): null key must throw on 2-arg constructor
    @Test
    public void testConstructorTwoArg_nullKey_throwsException() throws Throwable {
        try {
            new Attribute(null, "val");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Validate.notEmpty(key) after trim: blank key must throw on 3-arg constructor
    @Test
    public void testConstructorThreeArg_emptyKeyAfterTrim_throwsException() throws Throwable {
        try {
            new Attribute("   ", "val", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // key.trim() applied, parent defaults to null via 2-arg constructor delegation
    @Test
    public void testConstructorTwoArg_trimsKeyAndSetsParentNull() throws Throwable {
        Attribute attr = new Attribute("  class  ", "foo");
        assertEquals("class", attr.getKey());
        assertNull(attr.parent);
    }

    // 3-arg constructor stores key/value as-is when key already valid
    @Test
    public void testConstructorThreeArg_validKey_setsFields() throws Throwable {
        Attribute attr = new Attribute("class", "foo", null);
        assertEquals("class", attr.getKey());
        assertEquals("foo", attr.getValue());
    }

    // getKey returns the stored key
    @Test
    public void testGetKey_returnsStoredKey() throws Throwable {
        Attribute attr = new Attribute("href", "http://example.com");
        assertEquals("href", attr.getKey());
    }

    // getValue returns the stored value
    @Test
    public void testGetValue_returnsStoredValue() throws Throwable {
        Attribute attr = new Attribute("href", "http://example.com");
        assertEquals("http://example.com", attr.getValue());
    }

    // setKey: Validate.notNull throws on null key
    @Test
    public void testSetKey_nullKey_throwsException() throws Throwable {
        Attribute attr = new Attribute("class", "foo");
        try {
            attr.setKey(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setKey: Validate.notEmpty throws when trimmed key is blank
    @Test
    public void testSetKey_blankKeyAfterTrim_throwsException() throws Throwable {
        Attribute attr = new Attribute("class", "foo");
        try {
            attr.setKey("   ");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setKey: parent==null branch, key trimmed and updated locally
    @Test
    public void testSetKey_trimsAndUpdatesKey_noParent() throws Throwable {
        Attribute attr = new Attribute("class", "foo");
        attr.setKey("  id  ");
        assertEquals("id", attr.getKey());
    }





    // html(): non-boolean attribute renders key="value"
    @Test
    public void testHtml_simpleAttribute_producesKeyEqualsQuotedValue() throws Throwable {
        Attribute attr = new Attribute("class", "foo");
        assertEquals("class=\"foo\"", attr.html());
    }

    // html(): '&' in value is escaped per HTML attribute escaping rules
    @Test
    public void testHtml_escapesAmpersandInValue() throws Throwable {
        Attribute attr = new Attribute("class", "a&b");
        assertTrue(attr.html().contains("&amp;"));
    }

    // html(): '"' in value is escaped per HTML attribute escaping rules
    @Test
    public void testHtml_escapesQuoteInValue() throws Throwable {
        Attribute attr = new Attribute("class", "a\"b");
        assertTrue(attr.html().contains("&quot;"));
    }

    // shouldCollapseAttribute: boolean key + empty value collapses to bare key
    @Test
    public void testHtml_booleanAttributeEmptyValue_collapses() throws Throwable {
        Attribute attr = new Attribute("hidden", "");
        assertEquals("hidden", attr.html());
    }

    // shouldCollapseAttribute: boolean key + value equalsIgnoreCase(key) collapses
    @Test
    public void testHtml_booleanAttributeValueEqualsKeyIgnoreCase_collapses() throws Throwable {
        Attribute attr = new Attribute("hidden", "HIDDEN");
        assertEquals("hidden", attr.html());
    }

    // shouldCollapseAttribute: non-boolean key + empty value must NOT collapse
    @Test
    public void testHtml_nonBooleanAttributeEmptyValue_doesNotCollapse() throws Throwable {
        Attribute attr = new Attribute("class", "");
        assertEquals("class=\"\"", attr.html());
    }

    // toString() is implemented as html()
    @Test
    public void testToString_equalsHtml() throws Throwable {
        Attribute attr = new Attribute("class", "foo");
        assertEquals(attr.html(), attr.toString());
    }

    // createFromEncoded: HTML entity in encoded value is unescaped
    @Test
    public void testCreateFromEncoded_unescapesAmpersandEntity() throws Throwable {
        Attribute attr = Attribute.createFromEncoded("class", "a&amp;b");
        assertEquals("a&b", attr.getValue());
    }

    // createFromEncoded: parent is always null per javadoc "parent will get set when Put"
    @Test
    public void testCreateFromEncoded_parentIsNull() throws Throwable {
        Attribute attr = Attribute.createFromEncoded("class", "foo");
        assertNull(attr.parent);
    }

    // static html(): non-boolean attribute writes key="value" pair to Appendable
    @Test
    public void testHtmlStatic_nonBooleanAttribute_writesKeyValuePair() throws Throwable {
        StringBuilder sb = new StringBuilder();
        Document.OutputSettings out = new Document("").outputSettings();
        Attribute.html("class", "foo", sb, out);
        assertEquals("class=\"foo\"", sb.toString());
    }

    // static html(): boolean attribute whose value equals key collapses to key only
    @Test
    public void testHtmlStatic_booleanAttributeValueEqualsKey_collapsesToKeyOnly() throws Throwable {
        StringBuilder sb = new StringBuilder();
        Document.OutputSettings out = new Document("").outputSettings();
        Attribute.html("hidden", "hidden", sb, out);
        assertEquals("hidden", sb.toString());
    }

    // instance html(Appendable,out) delegates to static html with this.key/this.val
    @Test
    public void testHtmlInstance_delegatesToStaticHtml() throws Throwable {
        Attribute attr = new Attribute("class", "foo");
        StringBuilder sb = new StringBuilder();
        Document.OutputSettings out = new Document("").outputSettings();
        attr.html(sb, out);
        assertEquals("class=\"foo\"", sb.toString());
    }

    // isDataAttribute(): key with data- prefix (plus content) returns true
    @Test
    public void testIsDataAttributeInstance_withDataPrefix_returnsTrue() throws Throwable {
        Attribute attr = new Attribute(Attributes.dataPrefix + "custom", "val");
        assertTrue(attr.isDataAttribute());
    }

    // isDataAttribute(): key without data- prefix returns false
    @Test
    public void testIsDataAttributeInstance_withoutDataPrefix_returnsFalse() throws Throwable {
        Attribute attr = new Attribute("class", "val");
        assertFalse(attr.isDataAttribute());
    }

    // boundary: key length == dataPrefix length must NOT count as data attribute
    @Test
    public void testIsDataAttributeStatic_exactPrefixLength_returnsFalse() throws Throwable {
        String key = Attributes.dataPrefix;
        assertFalse(Attribute.isDataAttribute(key));
    }

    // boundary: key length == dataPrefix length + 1 DOES count as data attribute
    @Test
    public void testIsDataAttributeStatic_prefixPlusOneChar_returnsTrue() throws Throwable {
        String key = Attributes.dataPrefix + "x";
        assertTrue(Attribute.isDataAttribute(key));
    }

    // shouldCollapseAttribute instance: boolean key, null value -> true
    @Test
    public void testShouldCollapseAttributeInstance_booleanKeyNullValue_true() throws Throwable {
        Attribute attr = new Attribute("hidden", null);
        Document.OutputSettings out = new Document("").outputSettings();
        assertTrue(attr.shouldCollapseAttribute(out));
    }

    // shouldCollapseAttribute static: val==null short-circuits true regardless of boolean-ness
    @Test
    public void testShouldCollapseAttributeStatic_nonBooleanKeyNullValue_trueDueToNullCheck() throws Throwable {
        Document.OutputSettings out = new Document("").outputSettings();
        assertTrue(Attribute.shouldCollapseAttribute("class", null, out));
    }

    // shouldCollapseAttribute static: boolean key + empty string value -> true
    @Test
    public void testShouldCollapseAttributeStatic_booleanKeyEmptyValue_true() throws Throwable {
        Document.OutputSettings out = new Document("").outputSettings();
        assertTrue(Attribute.shouldCollapseAttribute("hidden", "", out));
    }

    // shouldCollapseAttribute static: non-boolean key + empty string value -> false
    @Test
    public void testShouldCollapseAttributeStatic_nonBooleanKeyEmptyValue_false() throws Throwable {
        Document.OutputSettings out = new Document("").outputSettings();
        assertFalse(Attribute.shouldCollapseAttribute("class", "", out));
    }

    // shouldCollapseAttribute static: boolean key + value equalsIgnoreCase(key) -> true
    @Test
    public void testShouldCollapseAttributeStatic_booleanKeyValueEqualsKeyIgnoreCase_true() throws Throwable {
        Document.OutputSettings out = new Document("").outputSettings();
        assertTrue(Attribute.shouldCollapseAttribute("hidden", "HIDDEN", out));
    }

    // shouldCollapseAttribute static: boolean key + unrelated non-empty value -> false
    @Test
    public void testShouldCollapseAttributeStatic_booleanKeyDifferentValue_false() throws Throwable {
        Document.OutputSettings out = new Document("").outputSettings();
        assertFalse(Attribute.shouldCollapseAttribute("hidden", "true", out));
    }

    // isBooleanAttribute(key): known HTML5 boolean attribute returns true
    @Test
    public void testIsBooleanAttributeStatic_knownBooleanKey_true() throws Throwable {
        assertTrue(Attribute.isBooleanAttribute("checked"));
    }

    // isBooleanAttribute(key): unknown attribute key returns false
    @Test
    public void testIsBooleanAttributeStatic_unknownKey_false() throws Throwable {
        assertFalse(Attribute.isBooleanAttribute("class"));
    }

    // deprecated isBooleanAttribute(): non-boolean key but val==null -> true (OR branch)
    @Test
    public void testIsBooleanAttributeInstance_nonBooleanKeyNullValue_trueDueToNullVal() throws Throwable {
        Attribute attr = new Attribute("class", null);
        assertTrue(attr.isBooleanAttribute());
    }

    // deprecated isBooleanAttribute(): non-boolean key with non-null value -> false
    @Test
    public void testIsBooleanAttributeInstance_nonBooleanKeyNonNullValue_false() throws Throwable {
        Attribute attr = new Attribute("class", "foo");
        assertFalse(attr.isBooleanAttribute());
    }

    // equals: same key and value -> equal
    @Test
    public void testEquals_sameKeyAndValue_true() throws Throwable {
        Attribute a1 = new Attribute("class", "foo");
        Attribute a2 = new Attribute("class", "foo");
        assertTrue(a1.equals(a2));
    }

    // equals: different key -> not equal
    @Test
    public void testEquals_differentKey_false() throws Throwable {
        Attribute a1 = new Attribute("class", "foo");
        Attribute a2 = new Attribute("id", "foo");
        assertFalse(a1.equals(a2));
    }

    // equals: different value -> not equal
    @Test
    public void testEquals_differentValue_false() throws Throwable {
        Attribute a1 = new Attribute("class", "foo");
        Attribute a2 = new Attribute("class", "bar");
        assertFalse(a1.equals(a2));
    }

    // equals: different runtime class -> not equal
    @Test
    public void testEquals_differentClass_false() throws Throwable {
        Attribute a1 = new Attribute("class", "foo");
        assertFalse(a1.equals("class"));
    }

    // equals: null argument -> not equal
    @Test
    public void testEquals_null_false() throws Throwable {
        Attribute a1 = new Attribute("class", "foo");
        assertFalse(a1.equals(null));
    }

    // hashCode: equal attributes produce equal hash codes
    @Test
    public void testHashCode_sameKeyValue_sameHash() throws Throwable {
        Attribute a1 = new Attribute("class", "foo");
        Attribute a2 = new Attribute("class", "foo");
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    // clone(): produces an equal but distinct object instance
    @Test
    public void testClone_producesEqualButDistinctInstance() throws Throwable {
        Attribute a1 = new Attribute("class", "foo");
        Attribute clone = a1.clone();
        assertTrue(a1.equals(clone));
        assertNotSame(a1, clone);
    }
}
