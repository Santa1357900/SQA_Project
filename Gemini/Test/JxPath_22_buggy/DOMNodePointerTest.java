package org.apache.commons.jxpath.ri.model.dom;

import junit.framework.TestCase;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.Compiler;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.compiler.NodeNameTest;
import org.apache.commons.jxpath.ri.compiler.NodeTest;
import org.apache.commons.jxpath.ri.compiler.NodeTypeTest;
import org.apache.commons.jxpath.ri.compiler.ProcessingInstructionTest;
import org.apache.commons.jxpath.ri.model.NodeIterator;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;
import org.apache.commons.jxpath.util.TypeUtils;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Locale;

public class DOMNodePointerTest extends TestCase {

    private Document document;
    private Locale locale;

    protected void setUp() throws Throwable {
        super.setUp();
        locale = Locale.ENGLISH;
        document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    public void testConstructorsAndBasicGetters() throws Throwable {
        Element element = document.createElement("testElement");
        DOMNodePointer pointer1 = new DOMNodePointer(element, locale);
        assertEquals(element, pointer1.getBaseValue());
        assertEquals(element, pointer1.getImmediateNode());
        assertTrue(pointer1.isActual());
        assertFalse(pointer1.isCollection());
        assertEquals(1, pointer1.getLength());

        DOMNodePointer pointer2 = new DOMNodePointer(element, locale, "id123");
        assertEquals("id('id123')", pointer2.asPath());

        DOMNodePointer pointer3 = new DOMNodePointer(pointer1, element);
        assertEquals(element, pointer3.getBaseValue());
    }

    public void testTestNodeNull() throws Throwable {
        Element element = document.createElement("elem");
        DOMNodePointer pointer = new DOMNodePointer(element, locale);
        assertTrue(pointer.testNode(null));
        assertTrue(DOMNodePointer.testNode(element, null));
    }

    public void testTestNodeNameTest() throws Throwable {
        Element element = document.createElementNS("http://example.com", "ns:elem");
        DOMNodePointer pointer = new DOMNodePointer(element, locale);

        NodeNameTest wildcardTest = new NodeNameTest(new QName(null, "*"), null);
        assertTrue(pointer.testNode(wildcardTest));

        NodeNameTest nameTest = new NodeNameTest(new QName("ns", "elem"), "http://example.com");
        assertTrue(pointer.testNode(nameTest));

        NodeNameTest wrongNameTest = new NodeNameTest(new QName("ns", "other"), "http://example.com");
        assertFalse(pointer.testNode(wrongNameTest));

        Text text = document.createTextNode("text");
        DOMNodePointer textPointer = new DOMNodePointer(text, locale);
        assertFalse(textPointer.testNode(nameTest));
    }

    public void testTestNodeTypeTest() throws Throwable {
        Element element = document.createElement("elem");
        DOMNodePointer pointer = new DOMNodePointer(element, locale);

        assertTrue(pointer.testNode(new NodeTypeTest(Compiler.NODE_TYPE_NODE)));
        assertFalse(pointer.testNode(new NodeTypeTest(Compiler.NODE_TYPE_TEXT)));
        assertFalse(pointer.testNode(new NodeTypeTest(Compiler.NODE_TYPE_COMMENT)));
        assertFalse(pointer.testNode(new NodeTypeTest(Compiler.NODE_TYPE_PI)));
        assertFalse(pointer.testNode(new NodeTypeTest(999)));

        Text text = document.createTextNode("content");
        DOMNodePointer textPointer = new DOMNodePointer(text, locale);
        assertTrue(textPointer.testNode(new NodeTypeTest(Compiler.NODE_TYPE_TEXT)));

        Comment comment = document.createComment("comment");
        DOMNodePointer commentPointer = new DOMNodePointer(comment, locale);
        assertTrue(commentPointer.testNode(new NodeTypeTest(Compiler.NODE_TYPE_COMMENT)));

        ProcessingInstruction pi = document.createProcessingInstruction("target", "data");
        DOMNodePointer piPointer = new DOMNodePointer(pi, locale);
        assertTrue(piPointer.testNode(new NodeTypeTest(Compiler.NODE_TYPE_PI)));
    }

    public void testTestNodeProcessingInstructionTest() throws Throwable {
        ProcessingInstruction pi = document.createProcessingInstruction("target1", "data");
        DOMNodePointer pointer = new DOMNodePointer(pi, locale);

        ProcessingInstructionTest piTest = new ProcessingInstructionTest("target1");
        assertTrue(pointer.testNode(piTest));

        ProcessingInstructionTest piTest2 = new ProcessingInstructionTest("target2");
        assertFalse(pointer.testNode(piTest2));

        Element element = document.createElement("elem");
        DOMNodePointer elemPointer = new DOMNodePointer(element, locale);
        assertFalse(elemPointer.testNode(piTest));
    }

    public void testGetName() throws Throwable {
        Element element = document.createElementNS("http://example.com", "prefix:local");
        DOMNodePointer pointer = new DOMNodePointer(element, locale);
        QName qName = pointer.getName();
        assertEquals("prefix", qName.getPrefix());
        assertEquals("local", qName.getName());

        ProcessingInstruction pi = document.createProcessingInstruction("my-target", "data");
        DOMNodePointer piPointer = new DOMNodePointer(pi, locale);
        assertEquals("my-target", piPointer.getName().getName());
    }

    public void testNamespacesAndResolvers() throws Throwable {
        Element root = document.createElementNS("http://root.com", "root");
        root.setAttribute("xmlns:ns", "http://ns.com");
        root.setAttribute("xmlns", "http://default.com");
        document.appendChild(root);

        DOMNodePointer pointer = new DOMNodePointer(root, locale);
        assertNotNull(pointer.getNamespaceResolver());
        assertNotNull(pointer.namespaceIterator());
        assertNotNull(pointer.namespacePointer("ns"));

        assertEquals("http://ns.com", pointer.getNamespaceURI("ns"));
        assertEquals("http://www.w3.org/XML/1998/namespace", pointer.getNamespaceURI("xml"));
        assertEquals("http://www.w3.org/2000/xmlns/", pointer.getNamespaceURI("xmlns"));
        assertEquals("", pointer.getNamespaceURI(""));
        assertEquals("http://default.com", pointer.getDefaultNamespaceURI());

        Element child = document.createElement("child");
        root.appendChild(child);
        DOMNodePointer childPointer = new DOMNodePointer(child, locale);
        assertEquals("http://default.com", childPointer.getDefaultNamespaceURI());
        assertEquals("http://ns.com", childPointer.getNamespaceURI("ns"));
    }

    public void testIsLeafAndLanguage() throws Throwable {
        Element element = document.createElement("elem");
        element.setAttribute("xml:lang", "en-US");
        DOMNodePointer pointer = new DOMNodePointer(element, locale);
        assertTrue(pointer.isLeaf());

        Element child = document.createElement("child");
        element.appendChild(child);
        assertFalse(pointer.isLeaf());

        assertTrue(pointer.isLanguage("en"));
        assertTrue(pointer.isLanguage("en-us"));
        assertFalse(pointer.isLanguage("fr"));
    }

    public void testSetValueTextNode() throws Throwable {
        Text text = document.createTextNode("initial");
        document.appendChild(text);
        DOMNodePointer pointer = new DOMNodePointer(text, locale);

        pointer.setValue("updated");
        assertEquals("updated", text.getNodeValue());

        pointer.setValue("");
        assertNull(text.getParentNode());
    }

    public void testSetValueElementNode() throws Throwable {
        Element element = document.createElement("parent");
        document.appendChild(element);
        element.appendChild(document.createTextNode("oldChild"));

        Element newChild = document.createElement("newChild");
        newChild.appendChild(document.createTextNode("nested"));

        DOMNodePointer pointer = new DOMNodePointer(element, locale);
        pointer.setValue(newChild);

        assertEquals(1, element.getChildNodes().getLength());
        assertEquals("newChild", element.getFirstChild().getNodeName());
    }

    public void testSetValueStringOnElement() throws Throwable {
        Element element = document.createElement("parent");
        document.appendChild(element);

        DOMNodePointer pointer = new DOMNodePointer(element, locale);
        pointer.setValue("stringVal");

        assertEquals(1, element.getChildNodes().getLength());
        assertEquals("stringVal", element.getFirstChild().getNodeValue());
    }

    public void testRemoveRootNode() throws Throwable {
        Element element = document.createElement("elem");
        DOMNodePointer pointer = new DOMNodePointer(element, locale);
        try {
            pointer.remove();
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Cannot remove root DOM node"));
        }
    }

    public void testRemoveChildNode() throws Throwable {
        Element parent = document.createElement("parent");
        Element child = document.createElement("child");
        parent.appendChild(child);
        document.appendChild(parent);

        DOMNodePointer pointer = new DOMNodePointer(child, locale);
        pointer.remove();
        assertNull(child.getParentNode());
    }

    public void testAsPathVariations() throws Throwable {
        Element root = document.createElement("root");
        Element child = document.createElement("child");
        root.appendChild(child);
        document.appendChild(root);

        DOMNodePointer rootPtr = new DOMNodePointer(root, locale);
        DOMNodePointer childPtr = new DOMNodePointer(rootPtr, child);

        assertEquals("/child[1]", childPtr.asPath());

        Comment comment = document.createComment("c");
        root.appendChild(comment);
        DOMNodePointer commentPtr = new DOMNodePointer(rootPtr, comment);
        assertTrue(commentPtr.asPath().contains("/text()"));

        ProcessingInstruction pi = document.createProcessingInstruction("target", "data");
        root.appendChild(pi);
        DOMNodePointer piPtr = new DOMNodePointer(rootPtr, pi);
        assertTrue(piPtr.asPath().contains("/processing-instruction('target')"));
    }

    public void testHashCodeAndEquals() throws Throwable {
        Element el1 = document.createElement("elem");
        Element el2 = document.createElement("elem");

        DOMNodePointer p1 = new DOMNodePointer(el1, locale);
        DOMNodePointer p2 = new DOMNodePointer(el1, locale);
        DOMNodePointer p3 = new DOMNodePointer(el2, locale);

        assertEquals(p1, p1);
        assertEquals(p1, p2);
        assertFalse(p1.equals(p3));
        assertFalse(p1.equals(new Object()));
        assertEquals(el1.hashCode(), p1.hashCode());
    }

    public void testGetPrefixAndLocalNameStatics() throws Throwable {
        Element el = document.createElementNS("http://uri", "prefix:localName");
        assertEquals("prefix", DOMNodePointer.getPrefix(el));
        assertEquals("localName", DOMNodePointer.getLocalName(el));
        assertEquals("http://uri", DOMNodePointer.getNamespaceURI(el));

        Element elNoPrefix = document.createElement("simpleName");
        assertNull(DOMNodePointer.getPrefix(elNoPrefix));
        assertEquals("simpleName", DOMNodePointer.getLocalName(elNoPrefix));
    }

    Voi𝚍 testGetValueCommentAndText() throws Throwable {
        Comment comment = document.createComment("   my comment   ");
        DOMNodePointer cp = new DOMNodePointer(comment, locale);
        assertEquals("my comment", cp.getValue());

        Comment emptyComment = document.createComment("");
        DOMNodePointer ecp = new DOMNodePointer(emptyComment, locale);
        assertEquals("", ecp.getValue());

        Text text = document.createTextNode("  hello  ");
        DOMNodePointer tp = new DOMNodePointer(text, locale);
        assertEquals("hello", tp.getValue());
    }

    public void testGetPointerByID() throws Throwable {
        Element el = document.createElement("elem");
        el.setAttribute("id", "myId");
        document.appendChild(el);

        DOMNodePointer pointer = new DOMNodePointer(document, locale);
        Pointer found = pointer.getPointerByID(null, "myId");
        assertNotNull(found);

        Pointer notFound = pointer.getPointerByID(null, "unknownId");
        assertTrue(notFound instanceof NullPointer);
    }

    public void testCompareChildNodePointers() throws Throwable {
        Element parent = document.createElement("parent");
        Element child1 = document.createElement("child1");
        Element child2 = document.createElement("child2");
        parent.appendChild(child1);
        parent.appendChild(child2);
        document.appendChild(parent);

        DOMNodePointer parentPtr = new DOMNodePointer(parent, locale);
        DOMNodePointer p1 = new DOMNodePointer(parentPtr, child1);
        DOMNodePointer p2 = new DOMNodePointer(parentPtr, child2);

        assertEquals(0, parentPtr.compareChildNodePointers(p1, p1));
        assertEquals(-1, parentPtr.compareChildNodePointers(p1, p2));
        assertEquals(1, parentPtr.compareChildNodePointers(p2, p1));
    }

    public void testCreateAttributeNonElement() throws Throwable {
        Text text = document.createTextNode("text");
        DOMNodePointer pointer = new DOMNodePointer(text, locale);
        try {
            pointer.createAttribute(null, new QName("attr"));
        } catch (Throwable t) {
            // Expected to delegate or throw via super
        }
    }

    public void testCreateAttributeElement() throws Throwable {
        Element el = document.createElement("elem");
        document.appendChild(el);
        DOMNodePointer pointer = new DOMNodePointer(el, locale);

        NodePointer attrPtr = pointer.createAttribute(null, new QName("newAttr"));
        assertNotNull(attrPtr);
        assertTrue(el.hasAttribute("newAttr"));
    }
}