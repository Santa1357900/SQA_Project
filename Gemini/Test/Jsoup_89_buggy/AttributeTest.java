package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class AttributeTest {

    @Test
    public void testConstructorAndGetters() throws Throwable {
        Attribute attr = new Attribute("testKey", "testValue");
        assertEquals("testKey", attr.getKey());
        assertEquals("testValue", attr.getValue());
        assertNull(attr.parent);
    }

    @Test
    public void testConstructorWithParent() throws Throwable {
        Attributes attributes = new Attributes();
        Attribute attr = new Attribute("  parentKey  ", "val", attributes);
        assertEquals("parentKey", attr.getKey());
        assertEquals("val", attr.getValue());
        assertEquals(attributes, attr.parent);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullKey() throws Throwable {
        new Attribute(null, "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyKey() throws Throwable {
        new Attribute("   ", "value");
    }

    @Test
    public void testSetKey() throws Throwable {
        Attribute attr = new Attribute("key1", "val1");
        attr.setKey("  newKey  ");
        assertEquals("newKey", attr.getKey());
    }

    @Test
    public void testSetKeyWithParent() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("key1", "val1");
        Attribute attr = attributes.dataset().isEmpty() ? new Attribute("key1", "val1", attributes) : null;
        // Let's manually test parent update logic via Attributes put/create
        Attributes parentAttrs = new Attributes();
        parentAttrs.put("oldKey", "val");
        Attribute attribute = parentAttrs.iterator().next();
        
        attribute.setKey("newKey");
        assertEquals("newKey", attribute.getKey());
        assertEquals("newKey", parentAttrs.getKey(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetKeyNull() throws Throwable {
        Attribute attr = new Attribute("key1", "val1");
        attr.setKey(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetKeyEmpty() throws Throwable {
        Attribute attr = new Attribute("key1", "val1");
        attr.setKey("");
    }

    @Test
    public void testSetValue() throws Throwable {
        Attributes parentAttrs = new Attributes();
        parentAttrs.put("key1", "oldVal");
        Attribute attr = new Attribute("key1", "oldVal", parentAttrs);
        
        String old = attr.setValue("newVal");
        assertEquals("oldVal", old);
        assertEquals("newVal", attr.getValue());
        assertEquals("newVal", parentAttrs.get("key1"));
    }

    @Test
    public void testHtml() throws Throwable {
        Attribute attr = new Attribute("href", "index.html");
        String html = attr.html();
        assertEquals("href=\"index.html\"", html);
        assertEquals(html, attr.toString());
    }

    @Test
    public void testCreateFromEncoded() throws Throwable {
        Attribute attr = Attribute.createFromEncoded("data", "a&amp;b");
        assertEquals("data", attr.getKey());
        assertEquals("a&b", attr.getValue());
    }

    @Test
    public void testIsDataAttribute() throws Throwable {
        Attribute attr1 = new Attribute("data-test", "val");
        assertTrue(attr1.isDataAttribute());
        assertTrue(Attribute.isDataAttribute("data-foo"));

        Attribute attr2 = new Attribute("data-", "val");
        assertFalse(attr2.isDataAttribute());
        assertFalse(Attribute.isDataAttribute("data-"));
        assertFalse(Attribute.isDataAttribute("href"));
    }

    @Test
    public void testBooleanAttributes() throws Throwable {
        Attribute attrBool = new Attribute("checked", "");
        assertTrue(attrBool.isBooleanAttribute());
        assertTrue(Attribute.isBooleanAttribute("checked"));
        assertTrue(Attribute.isBooleanAttribute("disabled"));

        Attribute attrNormal = new Attribute("href", "val");
        assertFalse(attrNormal.isBooleanAttribute());
        assertFalse(Attribute.isBooleanAttribute("class"));
    }

    @Test
    public void testShouldCollapseAttribute() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);

        Attribute attr1 = new Attribute("checked", "");
        assertTrue(attr1.shouldCollapseAttribute(doc.outputSettings()));

        Attribute attr2 = new Attribute("checked", "checked");
        assertTrue(attr2.shouldCollapseAttribute(doc.outputSettings()));

        Attribute attr3 = new Attribute("checked", "other");
        assertFalse(attr3.shouldCollapseAttribute(doc.outputSettings()));

        Attribute attr4 = new Attribute("href", "");
        assertFalse(attr4.shouldCollapseAttribute(doc.outputSettings()));

        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        assertFalse(attr1.shouldCollapseAttribute(doc.outputSettings()));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Attribute attr1 = new Attribute("key", "val");
        Attribute attr2 = new Attribute("key", "val");
        Attribute attr3 = new Attribute("key", "other");
        Attribute attr4 = new Attribute("other", "val");

        assertEquals(attr1, attr1);
        assertEquals(attr1, attr2);
        assertEquals(attr1.hashCode(), attr2.hashCode());

        assertFalse(attr1.equals(null));
        assertFalse(attr1.equals("someString"));
        assertFalse(attr1.equals(attr3));
        assertFalse(attr1.equals(attr4));

        Attribute attrNullVal1 = new Attribute("key", null);
        Attribute attrNullVal2 = new Attribute("key", null);
        Attribute attrNonNullVal = new Attribute("key", "val");

        assertEquals(attrNullVal1, attrNullVal2);
        assertEquals(attrNullVal1.hashCode(), attrNullVal2.hashCode());
        assertFalse(attrNullVal1.equals(attrNonNullVal));
        assertFalse(attrNonNullVal.equals(attrNullVal1));

        Attribute attrNullKey1 = new Attribute("key", "val");
        // reflection or direct field manipulation if needed, but constructor validates key.
        // Let's test clone instead.
    }

    @Test
    public void testClone() throws Throwable {
        Attribute attr = new Attribute("key", "val");
        Attribute clone = attr.clone();

        assertEquals(attr, clone);
        assertNotSame(attr, clone);
        assertEquals(attr.getKey(), clone.getKey());
        assertEquals(attr.getValue(), clone.getValue());
    }

    @Test
    public void testExplicitStaticHtmlAppend() throws Throwable {
        StringBuilder sb = new StringBuilder();
        Document.OutputSettings out = new Document("").outputSettings();
        Attribute.html("foo", "bar", sb, out);
        assertEquals("foo=\"bar\"", sb.toString());
    }
}