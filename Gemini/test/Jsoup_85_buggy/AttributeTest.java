package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class AttributeTest {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullKey() throws Throwable {
        new Attribute(null, "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyKey() throws Throwable {
        new Attribute("   ", "value");
    }

    @Test
    public void testConstructorValid() throws Throwable {
        Attribute attr = new Attribute("  testKey  ", "testVal");
        assertEquals("testKey", attr.getKey());
        assertEquals("testVal", attr.getValue());
    }

    @Test
    public void testWithParent() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("oldKey", "val");
        Attribute attr = new Attribute("oldKey", "newVal", attributes);
        
        assertEquals("oldKey", attr.getKey());
        assertEquals("newVal", attr.getValue());

        attr.setKey("newKey");
        assertEquals("newKey", attr.getKey());
        
        attr.setValue("val2");
        assertEquals("val2", attr.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetKeyNull() throws Throwable {
        Attribute attr = new Attribute("key", "val");
        attr.setKey(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetKeyEmpty() throws Throwable {
        Attribute attr = new Attribute("key", "val");
        attr.setKey("   ");
    }

    @Test
    public void testHtmlOutput() throws Throwable {
        Attribute attr = new Attribute("href", "index.html");
        assertEquals("href=\"index.html\"", attr.html());
        assertEquals("href=\"index.html\"", attr.toString());
    }

    @Test
    public void testCreateFromEncoded() throws Throwable {
        Attribute attr = Attribute.createFromEncoded("data", "val&amp;ue");
        assertEquals("data", attr.getKey());
        assertEquals("val&ue", attr.getValue());
    }

    @Test
    public void testIsDataAttribute() throws Throwable {
        Attribute dataAttr = new Attribute("data-test", "val");
        assertTrue(dataAttr.isDataAttribute());
        assertTrue(Attribute.isDataAttribute("data-test"));

        Attribute normalAttr = new Attribute("test", "val");
        assertFalse(normalAttr.isDataAttribute());
        assertFalse(Attribute.isDataAttribute("test"));
        assertFalse(Attribute.isDataAttribute("data-"));
    }

    @Test
    public void testBooleanAttribute() throws Throwable {
        Attribute boolAttr = new Attribute("checked", "");
        assertTrue(boolAttr.isBooleanAttribute());
        assertTrue(Attribute.isBooleanAttribute("checked"));

        Attribute nonBoolAttr = new Attribute("class", "foo");
        assertFalse(nonBoolAttr.isBooleanAttribute());
        assertFalse(Attribute.isBooleanAttribute("class"));
        
        Attribute nullValBoolAttr = new Attribute("checked", null);
        assertTrue(nullValBoolAttr.isBooleanAttribute());
    }

    @Test
    public void testShouldCollapseAttribute() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);

        Attribute collapseAttr = new Attribute("checked", "");
        assertTrue(collapseAttr.shouldCollapseAttribute(doc.outputSettings()));

        Attribute nameValCollapse = new Attribute("checked", "checked");
        assertTrue(nameValCollapse.shouldCollapseAttribute(doc.outputSettings()));

        Attribute noCollapse = new Attribute("class", "foo");
        assertFalse(noCollapse.shouldCollapseAttribute(doc.outputSettings()));
        
        Attribute nullValCollapse = new Attribute("checked", null);
        assertTrue(nullValCollapse.shouldCollapseAttribute(doc.outputSettings()));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Attribute attr1 = new Attribute("key1", "val1");
        Attribute attr2 = new Attribute("key1", "val1");
        Attribute attr3 = new Attribute("key2", "val1");
        Attribute attr4 = new Attribute("key1", "val2");

        assertTrue(attr1.equals(attr1));
        assertTrue(attr1.equals(attr2));
        assertEquals(attr1.hashCode(), attr2.hashCode());

        assertFalse(attr1.equals(null));
        assertFalse(attr1.equals("some string"));
        assertFalse(attr1.equals(attr3));
        assertFalse(attr1.equals(attr4));
        
        Attribute nullKey1 = new Attribute("key1", null);
        Attribute nullKey2 = new Attribute("key1", null);
        Attribute diffKey1 = new Attribute(null, "val1");
        Attribute diffKey2 = new Attribute("key1", "val1");

        assertTrue(nullKey1.equals(nullKey2));
        assertFalse(diffKey1.equals(diffKey2));
        assertFalse(diffKey2.equals(diffKey1));
    }

    @Test
    public void testClone() throws Throwable {
        Attribute attr = new Attribute("key", "val");
        Attribute clone = attr.clone();

        assertEquals(attr, clone);
        assertNotSame(attr, clone);
    }
}