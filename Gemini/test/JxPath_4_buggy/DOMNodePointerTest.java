package org.apache.commons.jxpath.ri.model.dom;

import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

import org.apache.commons.jxpath.AbstractFactory;
import org.apache.commons.jxpath.JXPathAbstractFactoryException;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.Compiler;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.compiler.NodeNameTest;
import org.apache.commons.jxpath.ri.compiler.NodeTypeTest;
import org.apache.commons.jxpath.ri.compiler.ProcessingInstructionTest;
import org.apache.commons.jxpath.ri.model.NodeIterator;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

public class DOMNodePointerTest extends TestCase {

    private Document document;

    protected void setUp() throws Exception {
        super.setUp();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        document = db.newDocument();
    }

    public void testConstructorsAndBasics() throws Throwable {
        Element elem = document.createElement("root");
        DOMNodePointer pointer1 = new DOMNodePointer(elem, Locale.ENGLISH);
        assertEquals(elem, pointer1.getBaseValue());
        assertEquals(elem, pointer1.getImmediateNode());
        assertTrue(pointer1.isActual());
        assertFalse(pointer1.isCollection());
        assertEquals(1, pointer1.getLength());
        assertEquals(Locale.ENGLISH, pointer1.getLocale());

        DOMNodePointer pointer2 = new DOMNodePointer(elem, Locale.ENGLISH, "id123");
        assertEquals("id('id123')", pointer2.asPath());

        DOMNodePointer pointer3 = new DOMNodePointer(pointer1, elem);
        assertEquals(pointer1, pointer3.getParent());
    }

    public void testTestNodeNull() throws Throwable {
        Element elem = document.createElement("test");
        assertTrue(DOMNodePointer.testNode(elem, null));
    }

    public void testTestNodeName() throws Throwable {
        Element elem = document.createElementNS("http://example.com", "prefix:local");
        document.appendChild(elem);

        NodeNameTest testWildcard = new NodeNameTest(new QName(null, "*"));
        assertTrue(DOMNodePointer.testNode(elem, testWildcard));

        NodeNameTest testWildcardWithPrefix = new NodeNameTest(new QName("prefix", "*"));
        assertTrue(DOMNodePointer.testNode(elem, testWildcardWithPrefix));

        NodeNameTest testExact = new NodeNameTest(new QName("prefix", "local"), "http://example.com");
        assertTrue(DOMNodePointer.testNode(elem, testExact));

        NodeNameTest testWrongName = new NodeNameTest(new QName("prefix", "wrong"), "http://example.com");
        assertFalse(DOMNodePointer.testNode(elem, testWrongName));

        Text text = document.createTextNode("content");
        assertFalse(DOMNodePointer.testNode(text, testExact));
    }

    public void testTestNodeType() throws Throwable {
        Element elem = document.createElement("elem");
        Text text = document.createTextNode("text");
        Comment comment = document.createComment("comment");
        ProcessingInstruction pi = document.createProcessingInstruction("target", "data");

        NodeTypeTest nodeTest = new NodeTypeTest(Compiler.NODE_TYPE_NODE);
        assertTrue(DOMNodePointer.testNode(elem, nodeTest));
        assertTrue(DOMNodePointer.testNode(document, nodeTest));
        assertFalse(DOMNodePointer.testNode(text, nodeTest));

        NodeTypeTest textTest = new NodeTypeTest(Compiler.NODE_TYPE_TEXT);
        assertTrue(DOMNodePointer.testNode(text, textTest));
        assertFalse(DOMNodePointer.testNode(elem, textTest));

        NodeTypeTest commentTest = new NodeTypeTest(Compiler.NODE_TYPE_COMMENT);
        assertTrue(DOMNodePointer.testNode(comment, commentTest));

        NodeTypeTest piTest = new NodeTypeTest(Compiler.NODE_TYPE_PI);
        assertTrue(DOMNodePointer.testNode(pi, piTest));

        NodeTypeTest unknownTest = new NodeTypeTest(999);
        assertFalse(DOMNodePointer.testNode(elem, unknownTest));
    }

    public void testTestNodeProcessingInstruction() throws Throwable {
        ProcessingInstruction pi = document.createProcessingInstruction("myTarget", "myData");
        ProcessingInstructionTest piTest = new ProcessingInstructionTest("myTarget");
        assertTrue(DOMNodePointer.testNode(pi, piTest));

        ProcessingInstructionTest piTestWrong = new ProcessingInstructionTest("otherTarget");
        assertFalse(DOMNodePointer.testNode(pi, piTestWrong));

        Element elem = document.createElement("elem");
        assertFalse(DOMNodePointer.testNode(elem, piTest));
    }

    public void testGettersNameAndNamespace() throws Throwable {
        Element elem = document.createElementNS("http://example.com", "ns:myElem");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);
        QName qname = pointer.getName();
        assertEquals("ns", qname.getPrefix());
        assertEquals("myElem", qname.getName());
        assertEquals("http://example.com", pointer.getNamespaceURI());

        ProcessingInstruction pi = document.createProcessingInstruction("myTarget", "data");
        DOMNodePointer piPointer = new DOMNodePointer(pi, Locale.ENGLISH);
        assertEquals("myTarget", piPointer.getName().getName());
    }

    public void testIteratorsAndPointers() throws Throwable {
        Element elem = document.createElement("elem");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);
        assertNotNull(pointer.childIterator(null, false, null));
        assertNotNull(pointer.attributeIterator(new QName("attr")));
        assertNotNull(pointer.namespacePointer("prefix"));
        assertNotNull(pointer.namespaceIterator());
    }

    public void testNamespaceURIsAndResolving() throws Throwable {
        Element root = document.createElementNS("http://example.com/root", "root");
        root.setAttribute("xmlns:foo", "http://example.com/foo");
        root.setAttribute("xmlns", "http://example.com/default");
        document.appendChild(root);

        Element child = document.createElement("child");
        root.appendChild(child);

        DOMNodePointer rootPointer = new DOMNodePointer(root, Locale.ENGLISH);
        assertEquals("http://example.com/foo", rootPointer.getNamespaceURI("foo"));
        assertEquals("http://example.com/root", rootPointer.getNamespaceURI(""));
        assertEquals("http://www.w3.org/XML/1998/namespace", rootPointer.getNamespaceURI("xml"));
        assertEquals("http://www.w3.org/2000/xmlns/", rootPointer.getNamespaceURI("xmlns"));
        assertEquals("http://example.com/default", rootPointer.getDefaultNamespaceURI());

        DOMNodePointer childPointer = new DOMNodePointer(child, Locale.ENGLISH);
        assertEquals("http://example.com/foo", childPointer.getNamespaceURI("foo"));
        assertEquals("http://example.com/default", childPointer.getDefaultNamespaceURI());
        
        assertEquals(NodePointer.UNKNOWN_NAMESPACE, rootPointer.getNamespaceURI("unknown"));
    }

    public void testDocumentNamespaceURI() throws Throwable {
        document.setDocumentURI("http://doc");
        DOMNodePointer docPointer = new DOMNodePointer(document, Locale.ENGLISH);
        assertNull(docPointer.getDefaultNamespaceURI());
    }

    public void testIsLeafAndLanguage() throws Throwable {
        Element elem = document.createElement("elem");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);
        assertTrue(pointer.isLeaf());

        elem.appendChild(document.createTextNode("text"));
        assertFalse(pointer.isLeaf());

        elem.setAttribute("xml:lang", "en-US");
        assertTrue(pointer.isLanguage("en"));
        assertFalse(pointer.isLanguage("fr"));

        elem.removeAttribute("xml:lang");
        assertFalse(pointer.isLanguage("en"));
    }

    public void testSetValueTextNode() throws Throwable {
        Text text = document.createTextNode("old");
        document.appendChild(text);
        DOMNodePointer pointer = new DOMNodePointer(text, Locale.ENGLISH);

        pointer.setValue("new");
        assertEquals("new", text.getNodeValue());

        pointer.setValue("");
        assertNull(text.getParentNode());
    }

    public void testSetValueElementNode() throws Throwable {
        Element elem = document.createElement("elem");
        document.appendChild(elem);
        Element child = document.createElement("child");
        elem.appendChild(child);

        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);
        
        Element newChild = document.createElement("newChild");
        pointer.setValue(newChild);
        assertEquals(1, elem.getChildNodes().getLength());
        assertEquals("newChild", elem.getChildNodes().item(0).getNodeName());

        pointer.setValue("simple text");
        assertEquals(1, elem.getChildNodes().getLength());
        assertTrue(elem.getChildNodes().item(0) instanceof Text);
        assertEquals("simple text", elem.getChildNodes().item(0).getNodeValue());
    }

    public void testSetValueElementNodeWithDocument() throws Throwable {
        Element elem = document.createElement("elem");
        document.appendChild(elem);

        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);
        pointer.setValue(document);
        assertEquals(0, elem.getChildNodes().getLength());
    }

    public void testCreateChildThrowsException() throws Throwable {
        Element elem = document.createElement("elem");
        document.appendChild(elem);
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);

        JXPathContext context = JXPathContext.newContext(new Object());
        try {
            pointer.createChild(context, new QName("child"), 0);
            fail("Expected JXPathAbstractFactoryException");
        } catch (JXPathAbstractFactoryException e) {
            assertTrue(e.getMessage().indexOf("Factory could not create a child node") != -1);
        }
    }

    public void testCreateChildSuccess() throws Throwable {
        Element elem = document.createElement("elem");
        document.appendChild(elem);
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);

        JXPathContext context = JXPathContext.newContext(new Object());
        context.setFactory(new AbstractFactory() {
            public boolean createObject(JXPathContext context, Pointer pointer, Object parent, String name, int index) {
                Element p = (Element) parent;
                p.appendChild(p.getOwnerDocument().createElement(name));
                return true;
            }
        });

        NodePointer childPtr = pointer.createChild(context, new QName("child"), 0, "value");
        assertNotNull(childPtr);
    }

    public void testCreateAttribute() throws Throwable {
        Element elem = document.createElement("elem");
        document.appendChild(elem);
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH);
        JXPathContext context = JXPathContext.newContext(new Object());

        NodePointer attrPtr = pointer.createAttribute(context, new QName("myAttr"));
        assertNotNull(attrPtr);
        assertEquals("", elem.getAttribute("myAttr"));

        elem.setAttribute("xmlns:ns", "http://example.com/ns");
        NodePointer nsAttrPtr = pointer.createAttribute(context, new QName("ns", "attr"));
        assertNotNull(nsAttrPtr);
        
        try {
            pointer.createAttribute(context, new QName("unknownNs", "attr"));
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().indexOf("Unknown namespace prefix") != -1);
        }

        Text text = document.createTextNode("text");
        DOMNodePointer textPointer = new DOMNodePointer(text, Locale.ENGLISH);
        try {
            textPointer.createAttribute(context, new QName("attr"));
            fail("Expected exception");
        } catch (Throwable t) {
            // Expected fallback to super
        }
    }

    public void testRemove() throws Throwable {
        Element elem = document.createElement("elem");
        Element child = document.createElement("child");
        elem.appendChild(child);
        DOMNodePointer pointer = new DOMNodePointer(child, Locale.ENGLISH);

        pointer.remove();
        assertNull(child.getParentNode());

        DOMNodePointer rootPointer = new DOMNodePointer(document, Locale.ENGLISH);
        try {
            rootPointer.remove();
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().indexOf("Cannot remove root DOM node") != -1);
        }
    }

    public void testAsPathVariations() throws Throwable {
        Element root = document.createElement("root");
        Element child1 = document.createElement("elem");
        Element child2 = document.createElement("elem");
        Text text = document.createTextNode("txt");
        ProcessingInstruction pi = document.createProcessingInstruction("pi", "data");
        Comment comment = document.createComment("comment");

        root.appendChild(child1);
        root.appendChild(child2);
        child2.appendChild(text);
        root.appendChild(pi);
        root.appendChild(comment);
        document.appendChild(root);

        DOMNodePointer rootPtr = new DOMNodePointer(root, Locale.ENGLISH);
        DOMNodePointer child1Ptr = new DOMNodePointer(rootPtr, child1);
        assertEquals("/root/elem[1]", child1Ptr.asPath());

        DOMNodePointer child2Ptr = new DOMNodePointer(rootPtr, child2);
        assertEquals("/root/elem[2]", child2Ptr.asPath());

        DOMNodePointer textPtr = new DOMNodePointer(child2Ptr, text);
        assertEquals("/root/elem[2]/text()[1]", textPtr.asPath());

        DOMNodePointer piPtr = new DOMNodePointer(rootPtr, pi);
        assertEquals("/root/processing-instruction('pi')[1]", piPtr.asPath());

        DOMNodePointer commentPtr = new DOMNodePointer(rootPtr, comment);
        assertEquals("/root", commentPtr.asPath()); // Default/unhandled node type in switch returns buffer from parent
    }

    public void testEscape() throws Throwable {
        Element elem = document.createElement("root");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.ENGLISH, "id'\"test");
        assertEquals("id('id&apos;&quot;test')", pointer.asPath());
    }

    public void testHashCodeAndEquals() throws Throwable {
        Element elem1 = document.createElement("elem");
        Element elem2 = document.createElement("elem");

        DOMNodePointer p1 = new DOMNodePointer(elem1, Locale.ENGLISH);
        DOMNodePointer p2 = new DOMNodePointer(elem1, Locale.ENGLISH);
        DOMNodePointer p3 = new DOMNodePointer(elem2, Locale.ENGLISH);

        assertTrue(p1.equals(p1));
        assertTrue(p1.equals(p2));
        assertFalse(p1.equals(p3));
        assertFalse(p1.equals(new Object()));
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    public void testGetPrefixAndLocalNameAndNamespaceStatic() throws Throwable {
        Element elem = document.createElementNS("http://ns", "p:local");
        assertEquals("p", DOMNodePointer.getPrefix(elem));
        assertEquals("local", DOMNodePointer.getLocalName(elem));
        assertEquals("http://ns", DOMNodePointer.getNamespaceURI(elem));

        Element elemNoPrefix = document.createElement("localNameOnly");
        assertNull(DOMNodePointer.getPrefix(elemNoPrefix));
        assertEquals("localNameOnly", DOMNodePointer.getLocalName(elemNoPrefix));
    }

    public void testGetValueNodeVariations() throws Throwable {
        Comment comment = document.createComment("  comment text  ");
        DOMNodePointer cp = new DOMNodePointer(comment, Locale.ENGLISH);
        assertEquals("comment text", cp.getValue());

        ProcessingInstruction pi = document.createProcessingInstruction("target", "  pi data  ");
        DOMNodePointer pip = new DOMNodePointer(pi, Locale.ENGLISH);
        assertEquals("pi data", pip.getValue());

        Element elem = document.createElement("elem");
        Text t1 = document.createTextNode("hello ");
        Text t2 = document.createTextNode("world");
        elem.appendChild(t1);
        elem.appendChild(t2);
        DOMNodePointer ep = new DOMNodePointer(elem, Locale.ENGLISH);
        assertEquals("helloworld", ep.getValue());
    }

    public void testGetPointerByID() throws Throwable {
        Element elem = document.createElement("elem");
        elem.setAttribute("id", "myId");
        document.appendChild(elem);

        DOMNodePointer pointer = new DOMNodePointer(document, Locale.ENGLISH);
        Pointer found = pointer.getPointerByID(JXPathContext.newContext(new Object()), "myId");
        assertNotNull(found);

        Pointer notFound = pointer.getPointerByID(JXPathContext.newContext(new Object()), "badId");
        assertTrue(notFound instanceof NullPointer);

        Element elemDoc = document.createElement("elem2");
        document.appendChild(elemDoc);
        DOMNodePointer elemDocPtr = new DOMNodePointer(elemDoc, Locale.ENGLISH);
        Pointer found2 = elemDocPtr.getPointerByID(JXPathContext.newContext(new Object()), "myId");
        assertNotNull(found2);
    }

    public void testCompareChildNodePointers() throws Throwable {
        Element parent = document.createElement("parent");
        Element attr1 = document.createElement("a1");
        Element attr2 = document.createElement("a2");
        Element child1 = document.createElement("c1");
        Element child2 = document.createElement("c2");
        parent.appendChild(child1);
        parent.appendChild(child2);
        parent.setAttribute("at1", "v1");
        parent.setAttribute("at2", "v2");
        document.appendChild(parent);

        DOMNodePointer parentPtr = new DOMNodePointer(parent, Locale.ENGLISH);
        DOMNodePointer c1Ptr = new DOMNodePointer(parentPtr, child1);
        DOMNodePointer c2Ptr = new DOMNodePointer(parentPtr, child2);

        assertEquals(0, parentPtr.compareChildNodePointers(c1Ptr, c1Ptr));
        assertEquals(-1, parentPtr.compareChildNodePointers(c1Ptr, c2Ptr));
        assertEquals(1, parentPtr.compareChildNodePointers(c2Ptr, c1Ptr));

        org.w3c.dom.Attr a1 = parent.getAttributeNode("at1");
        org.w3c.dom.Attr a2 = parent.getAttributeNode("at2");
        DOMNodePointer a1Ptr = new DOMNodePointer(parentPtr, a1);
        DOMNodePointer a2Ptr = new DOMNodePointer(parentPtr, a2);

        assertEquals(-1, parentPtr.compareChildNodePointers(a1Ptr, c1Ptr));
        assertEquals(1, parentPtr.compareChildNodePointers(c1Ptr, a1Ptr));
        assertEquals(-1, parentPtr.compareChildNodePointers(a1Ptr, a2Ptr));
        assertEquals(1, parentPtr.compareChildNodePointers(a2Ptr, a1Ptr));
        
        Element foreign = document.createElement("foreign");
        DOMNodePointer foreignPtr = new DOMNodePointer(parentPtr, foreign);
        assertEquals(0, parentPtr.compareChildNodePointers(c1Ptr, foreignPtr));
    }
}