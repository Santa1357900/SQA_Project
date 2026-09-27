package org.apache.commons.jxpath.ri.model.dom;

import junit.framework.TestCase;

import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.ri.NamespaceResolver;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Attr;

public class DOMAttributeIteratorTest extends TestCase {

    private Document document;
    private Element element;
    private DOMNodePointer nodePointer;

    protected void setUp() throws Throwable {
        super.setUp();
        document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        element = document.createElement("testElement");
        element.setAttribute("name", "value1");
        element.setAttribute("xmlns", "http://example.com/xmlns");
        element.setAttribute("xmlns:ns", "http://example.com/ns");
        element.setAttribute("ns:local", "value2");

        JXPathContext context = JXPathContext.newContext(element);
        nodePointer = new DOMNodePointer(null, element, null);
    }

    public void testConstructorWithWildcard() throws Throwable {
        QName qName = new QName("*");
        DOMAttributeIterator iterator = new DOMAttributeIterator(nodePointer, qName);
        
        assertEquals(0, iterator.getPosition());
        boolean hasFirst = iterator.setPosition(1);
        assertTrue(hasFirst);
        assertNotNull(iterator.getNodePointer());
    }

    public void testConstructorWithSpecificAttribute() throws Throwable {
        QName qName = new QName("name");
        DOMAttributeIterator iterator = new DOMAttributeIterator(nodePointer, qName);
        
        assertEquals(0, iterator.getPosition());
        assertTrue(iterator.setPosition(1));
        assertNotNull(iterator.getNodePointer());
        assertFalse(iterator.setPosition(2));
    }

    public void testConstructorWithNamespacePrefix() throws Throwable {
        QName qName = new QName("ns", "local");
        DOMAttributeIterator iterator = new DOMAttributeIterator(nodePointer, qName);
        
        assertEquals(0, iterator.getPosition());
        assertTrue(iterator.setPosition(1));
        assertNotNull(iterator.getNodePointer());
    }

    public void testNonElementNode() throws Throwable {
        org.w3c.dom.Text textNode = document.createTextNode("some text");
        DOMNodePointer textPointer = new DOMNodePointer(null, textNode, null);
        QName qName = new QName("name");
        DOMAttributeIterator iterator = new DOMAttributeIterator(textPointer, qName);

        assertEquals(0, iterator.getPosition());
        assertFalse(iterator.setPosition(1));
        assertNull(iterator.getNodePointer());
    }

    public void testSetPositionBoundaries() throws Throwable {
        QName qName = new QName("name");
        DOMAttributeIterator iterator = new DOMAttributeIterator(nodePointer, qName);

        assertFalse(iterator.setPosition(0));
        assertFalse(iterator.setPosition(-1));
        assertTrue(iterator.setPosition(1));
        assertFalse(iterator.setPosition(2));
    }

    public void testGetNodePointerImplicitPosition() throws Throwable {
        QName qName = new QName("name");
        DOMAttributeIterator iterator = new DOMAttributeIterator(nodePointer, qName);

        // position is 0, calling getNodePointer should set position to 1 temporarily and return pointer
        NodePointer ptr = iterator.getNodePointer();
        assertNotNull(ptr);
        assertEquals(0, iterator.getPosition());
    }
}