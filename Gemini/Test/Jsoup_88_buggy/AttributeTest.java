package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class AttributeTest {

    @Test
    public void testConstructorAndGetters() throws Throwable {
        Attribute attr = new Attribute("testKey", "testVal");
        assertEquals("testKey", attr.getKey());
        assertEquals("testVal", attr.getValue());
        assertNull(attr.parent);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullKey() throws Throwable {
        new Attribute(null, "val");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyKey() throws Throwable {
        new Attribute("   ", "val");
    }

    @Test
    public void testWithParentAttributes() throws Throwable {
        Attributes parent = new Attributes();
        parent.put("oldKey", "val");
        
        Attribute attr = new Attribute("oldKey", "val", parent);
        attr.setKey("newKey");
        
        assertEquals("newKey", attr.getKey());
        assertEquals("newKey", parent.keys[0]);
    }

    @Test
    public void testSetValueWithParent() throws Throwable {
        Attributes parent = new Attributes();
        parent.put("key1", "oldVal");
        
        Attribute attr = new Attribute("key1", "oldVal", parent);
        String old = attr.setValue("newVal");
        
        assertEquals("oldVal", old);
        assertEquals("newVal", attr.getValue());
        assertEquals("newVal", parent.vals[0]);
    }

    @Test
    public void testHtmlOutput() throws Throwable {
        Attribute attr = new Attribute("href", "index.html");
        String html = attr.html();
        assertEquals("href=\"index.html\"", html);
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
        assertTrue(Attribute.isDataAttribute("data-foo"));
        
        Attribute normalAttr = new Attribute("test", "val");
        assertFalse(normalAttr.isDataAttribute());
        assertFalse(Attribute.isDataAttribute("data-"));
        assertFalse(Attribute.isDataAttribute("nodata"));
    }

    @Test
    public void testBooleanAttributes() throws Throwable {
        Attribute boolAttr = new Attribute("checked", "");
        assertTrue(boolAttr.isBooleanAttribute());
        assertTrue(Attribute.isBooleanAttribute("checked"));
        
        Attribute normalAttr = new Attribute("class", "val");
        assertFalse(normalAttr.isBooleanAttribute());
        assertFalse(Attribute.isBooleanAttribute("not-boolean"));
    }

    @Test
    public void testShouldCollapseAttribute() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
        
        Attribute checkedAttr = new Attribute("checked", "");
        assertTrue(checkedAttr.shouldCollapseAttribute(doc.outputSettings()));
        
        Attribute checkedAttrSameVal = new Attribute("checked", "checked");
        assertTrue(checkedAttrSameVal.shouldCollapseAttribute(doc.outputSettings()));

        Attribute checkedAttrNullVal = new Attribute("checked", null);
        assertTrue(checkedAttrNullVal.shouldCollapseAttribute(doc.outputSettings()));

        Attribute normalAttr = new Attribute("class", "val");
        assertFalse(normalAttr.shouldCollapseAttribute(doc.outputSettings()));
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
        
        Attribute attrNullKey1 = new Attribute("key1", null);
        Attribute attrNullKey2 = new Attribute("key1", null);
        Attribute attrDiffKey1 = new Attribute("key2", null);
        
        assertTrue(attrNullKey1.equals(attrNullKey2));
        assertFalse(attrNullKey1.equals(attrDiffKey1));
    }

    @Test
    public void testClone() throws Throwable {
        Attribute attr = new Attribute("key", "val");
        Attribute clone = attr.clone();
        
        assertEquals(attr.getKey(), clone.getKey());
        assertEquals(attr.getValue(), clone.getValue());
        assertNotSame(attr, clone);
    }
}