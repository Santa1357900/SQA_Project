package org.apache.commons.jxpath.ri.model.dom;

import junit.framework.TestCase;

import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DOMAttributeIteratorTest extends TestCase {

    private Document document;
    private Element element;
    private NodePointer parentPointer;

    protected void setUp() throws Exception {
        super.setUp();
        document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        element = document.createElementNS("http://example.com/ns", "ns:testElement");
        element.setAttribute("id", "123");
        element.setAttribute("name", "testName");
        element.setAttribute("xmlns:foo", "http://example.com/foo");
        element.setAttribute("xmlns", "http://default.ns");

        parentPointer = NodePointer.newChildNodePointer(null, new QName("test"), element);
    }

    public void testConstructorWithWildcardName() throws Throwable {
        QName qName = new QName("*");
        DOMAttributeIterator iterator = new DOMAttributeIterator(parentPointer, qName);
        
        assertEquals(0, iterator.getPosition());
        boolean hasSet = iterator.setPosition(1);
        assertTrue(hasSet);
        assertNotNull(iterator.getNodePointer());
    }

    public void testConstructorWithSpecificAttribute() throws Throwable {
        QName qName = new QName("name");
        DOMAttributeIterator iterator = new DOMAttributeIterator(parentPointer, qName);
        
        assertTrue(iterator.setPosition(1));
        assertNotNull(iterator.getNodePointer());
        assertFalse(iterator.setPosition(2));
    }

    public void testConstructorWithNamespacePrefix() throws Throwable {
        QName qName = new QName("ns", "testElement");
        // Create an iterator where the name has a prefix
        DOMAttributeIterator iterator = new DOMAttributeIterator(parentPointer, qName);
        assertNotNull(iterator);
    }

    public void testGetPositionAndSetPositionBoundaries() throws Throwable {
        QName qName = new QName("id");
        DOMAttributeIterator iterator = new DOMAttributeIterator(parentPointer, qName);

        assertEquals(0, iterator.getPosition());
        
        assertFalse(iterator.setPosition(0));
        assertFalse(iterator.setPosition(2));
        assertTrue(iterator.setPosition(1));
        assertEquals(1, iterator.getPosition());
    }

    public void testGetNodePointerWithoutSettingPosition() throws Throwable {
        QName qName = new QName("id");
        DOMAttributeIterator iterator = new DOMAttributeIterator(parentPointer, qName);

        NodePointer np = iterator.getNodePointer();
        assertNotNull(np);
        assertEquals(0, iterator.getPosition());
    }

    public void testNonElementNode() throws Throwable {
        org.w3c.dom.Text textNode = document.createTextNode("Some text");
        NodePointer textPointer = NodePointer.newChildNodePointer(null, new QName("text"), textNode);

        QName qName = new QName("*");
        DOMAttributeIterator iterator = new DOMAttributeIterator(textPointer, qName);
        
        assertFalse(iterator.setPosition(1));
        assertNull(iterator.getNodePointer());
    }

    public void testXmlnsAttributesIgnored() throws Throwable {
        QName qName = new QName("xmlns");
        DOMAttributeIterator iterator = new DOMAttributeIterator(parentPointer, qName);
        assertFalse(iterator.setPosition(1));
    }
}