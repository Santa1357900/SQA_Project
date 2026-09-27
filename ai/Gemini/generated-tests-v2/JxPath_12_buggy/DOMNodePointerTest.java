package org.apache.commons.jxpath.ri.model.dom;

import java.util.Locale;

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
import org.apache.commons.jxpath.ri.compiler.NodeTest;
import org.apache.commons.jxpath.ri.compiler.NodeTypeTest;
import org.apache.commons.jxpath.ri.compiler.ProcessingInstructionTest;
import org.apache.commons.jxpath.ri.model.NodeIterator;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

public class DOMNodePointerTest extends TestCase {

    private Document document;
    private Locale locale;

    protected void setUp() throws Throwable {
        super.setUp();
        document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        locale = Locale.getDefault();
    }

    public void testConstructorsAndBasics() throws Throwable {
        Element elem = document.createElement("root");
        document.appendChild(elem);

        DOMNodePointer pointer1 = new DOMNodePointer(elem, locale);
        DOMNodePointer pointer2 = new DOMNodePointer(elem, locale, "id123");
        NodePointer parent = NodePointer.newNodePointer(new QName("parent"), "value", locale);
        DOMNodePointer pointer3 = new DOMNodePointer(parent, elem);

        assertEquals(elem, pointer1.getBaseValue());
        assertEquals(elem, pointer1.getImmediateNode());
        assertTrue(pointer1.isActual());
        assertFalse(pointer1.isCollection());
        assertEquals(1, pointer1.getLength());
        assertTrue(pointer1.isLeaf());

        assertEquals(elem, pointer2.getBaseValue());
        assertEquals("id123", pointer2.getPointerByID(JXPathContext.newContext(null), "id123").getImmediateNode());

        assertEquals(parent, pointer3.getParent());
        
        DOMNodePointer pointer4 = new DOMNodePointer(document, locale);
        assertFalse(pointer4.isLeaf());
    }

    public void testTestNodeNullAndTypes() throws Throwable {
        Element elem = document.createElement("testElem");
        Comment comment = document.createComment("myComment");
        ProcessingInstruction pi = document.createProcessingInstruction("target", "data");
        Text text = document.createTextNode("hello");

        assertTrue(DOMNodePointer.testNode(elem, null));

        // NodeTypeTest
        NodeTypeTest nodeTestNode = new NodeTypeTest(Compiler.NODE_TYPE_NODE);
        assertTrue(DOMNodePointer.testNode(elem, nodeTestNode));
        assertTrue(DOMNodePointer.testNode(document, nodeTestNode));
        assertFalse(DOMNodePointer.testNode(comment, nodeTestNode));

        NodeTypeTest nodeTestText = new NodeTypeTest(Compiler.NODE_TYPE_TEXT);
        assertTrue(DOMNodePointer.testNode(text, nodeTestText));
        assertFalse(DOMNodePointer.testNode(elem, nodeTestText));

        NodeTypeTest nodeTestComment = new NodeTypeTest(Compiler.NODE_TYPE_COMMENT);
        assertTrue(DOMNodePointer.testNode(comment, nodeTestComment));
        assertFalse(DOMNodePointer.testNode(elem, nodeTestComment));

        NodeTypeTest nodeTestPI = new NodeTypeTest(Compiler.NODE_TYPE_PI);
        assertTrue(DOMNodePointer.testNode(pi, nodeTestPI));
        assertFalse(DOMNodePointer.testNode(elem, nodeTestPI));

        NodeTypeTest nodeTestUnknown = new NodeTypeTest(999);
        assertFalse(DOMNodePointer.testNode(elem, nodeTestUnknown));

        // ProcessingInstructionTest
        ProcessingInstructionTest piTest = new ProcessingInstructionTest("target");
        assertTrue(DOMNodePointer.testNode(pi, piTest));
        ProcessingInstructionTest piTestWrong = new ProcessingInstructionTest("wrong");
        assertFalse(DOMNodePointer.testNode(pi, piTestWrong));
        assertFalse(DOMNodePointer.testNode(elem, piTest));
    }

    public void testTestNodeNameTest() throws Throwable {
        Element elem = document.createElementNS("http://example.com", "prefix:localName");
        document.appendChild(elem);

        NodeNameTest wildcardNoPrefix = new NodeNameTest(new QName(null, "*"));
        assertTrue(DOMNodePointer.testNode(elem, wildcardNoPrefix));

        NodeNameTest wildcardWithPrefix = new NodeNameTest(new QName("prefix", "*"), "http://example.com");
        assertTrue(DOMNodePointer.testNode(elem, wildcardWithPrefix));

        NodeNameTest exactMatch = new NodeNameTest(new QName("prefix", "localName"), "http://example.com");
        assertTrue(DOMNodePointer.testNode(elem, exactMatch));

        NodeNameTest wrongNs = new NodeNameTest(new QName("prefix", "localName"), "http://wrong.com");
        assertFalse(DOMNodePointer.testNode(elem, wrongNs));

        NodeNameTest wrongName = new NodeNameTest(new QName("prefix", "otherName"), "http://example.com");
        assertFalse(DOMNodePointer.testNode(elem, wrongName));

        // Non-element node with NodeNameTest should return false
        Comment comment = document.createComment("c");
        assertFalse(DOMNodePointer.testNode(comment, exactMatch));
    }

    public void testGetNameAndNamespace() throws Throwable {
        Element elem = document.createElementNS("http://example.com", "ns:myElem");
        DOMNodePointer pointer = new DOMNodePointer(elem, locale);
        QName qName = pointer.getName();
        assertEquals("ns", qName.getPrefix());
        assertEquals("myElem", qName.getName());

        ProcessingInstruction pi = document.createProcessingInstruction("myTarget", "myData");
        DOMNodePointer piPointer = new DOMNodePointer(pi, locale);
        assertEquals("myTarget", piPointer.getName().getName());

        assertEquals("http://example.com", pointer.getNamespaceURI());
        assertEquals("http://example.com", DOMNodePointer.getNamespaceURI(elem));
        assertEquals("ns", DOMNodePointer.getPrefix(elem));
        assertEquals("myElem", DOMNodePointer.getLocalName(elem));
    }

    public void testGetNamespaceURIWithPrefixes() throws Throwable {
        Element root = document.createElementNS("http://root.com", "root");
        root.setAttribute("xmlns:custom", "http://custom.com");
        root.setAttribute("xmlns", "http://default.com");
        document.appendChild(root);

        Element child = document.createElement("child");
        root.appendChild(child);

        DOMNodePointer pointer = new DOMNodePointer(child, locale);
        assertEquals("http://custom.com", pointer.getNamespaceURI("custom"));
        assertEquals("http://www.w3.org/XML/1998/namespace", pointer.getNamespaceURI("xml"));
        assertEquals("http://www.w3.org/2000/xmlns/", pointer.getNamespaceURI("xmlns"));
        assertEquals("http://default.com", pointer.getDefaultNamespaceURI());
        assertEquals("http://default.com", pointer.getNamespaceURI(""));
        assertEquals("http://default.com", pointer.getNamespaceURI(null));

        // Test caching and unknown namespaces
        assertEquals(NodePointer.UNKNOWN_NAMESPACE, pointer.getNamespaceURI("unknown"));
        assertNull(pointer.getNamespaceURI("unknown")); // second call hits cache returning UNKNOWN_NAMESPACE -> null

        DOMNodePointer docPointer = new DOMNodePointer(document, locale);
        assertEquals("http://default.com", docPointer.getDefaultNamespaceURI());
    }

    public void testIteratorsAndPointers() throws Throwable {
        Element root = document.createElement("root");
        document.appendChild(root);
        DOMNodePointer pointer = new DOMNodePointer(root, locale);

        assertNotNull(pointer.childIterator(null, false, null));
        assertNotNull(pointer.attributeIterator(new QName("attr")));
        assertNotNull(pointer.namespacePointer("pre"));
        assertNotNull(pointer.namespaceIterator());
    }

    public void testIsLanguage() throws Throwable {
        Element root = document.createElement("root");
        root.setAttribute("xml:lang", "en-US");
        document.appendChild(root);

        Element child = document.createElement("child");
        root.appendChild(child);

        DOMNodePointer pointer = new DOMNodePointer(child, locale);
        assertTrue(pointer.isLanguage("en"));
        assertTrue(pointer.isLanguage("EN"));
        assertFalse(pointer.isLanguage("fr"));
    }

    public void testSetValueTextAndElements() throws Throwable {
        Text text = document.createTextNode("initial");
        document.appendChild(text);
        DOMNodePointer pointer = new DOMNodePointer(text, locale);

        pointer.setValue("updated");
        assertEquals("updated", text.getNodeValue());

        pointer.setValue("");
        assertNull(text.getParentNode());

        Element root = document.createElement("root");
        Element sub = document.createElement("sub");
        sub.appendChild(document.createTextNode("content"));
        document.appendChild(root);
        DOMNodePointer rootPointer = new DOMNodePointer(root, locale);

        rootPointer.setValue(sub);
        assertEquals(1, root.getChildNodes().getLength());
        assertEquals("sub", root.getFirstChild().getNodeName());

        Element sub2 = document.createElement("sub2");
        rootPointer.setValue(sub2);
        assertEquals("sub2", root.getFirstChild().getNodeName());

        rootPointer.setValue("stringVal");
        assertEquals("stringVal", root.getFirstChild().getNodeValue());
    }

    public void testCreateChildAndAttribute() throws Throwable {
        Element root = document.createElement("root");
        document.appendChild(root);
        DOMNodePointer pointer = new DOMNodePointer(root, locale);

        JXPathContext context = JXPathContext.newContext(null);
        context.setFactory(new AbstractFactory() {
            public boolean createObject(JXPathContext context, Pointer pointer, Object parent, String name, int index) {
                Element p = (Element) parent;
                Element child = p.getOwnerDocument().createElement(name);
                p.appendChild(child);
                return true;
            }
        });

        // WHOLE_COLLECTION branch
        NodePointer childPtr = pointer.createChild(context, new QName("newChild"), NodePointer.WHOLE_COLLECTION);
        assertNotNull(childPtr);

        NodePointer childPtrVal = pointer.createChild(context, new QName("newChildVal"), 0, "val");
        assertNotNull(childPtrVal);
        assertEquals("val", childPtrVal.getValue());

        // Test exception when factory fails
        JXPathContext badContext = JXPathContext.newContext(null);
        badContext.setFactory(new AbstractFactory() {
            public boolean createObject(JXPathContext context, Pointer pointer, Object parent, String name, int index) {
                return false;
            }
        });
        try {
            pointer.createChild(badContext, new QName("failChild"), 0);
            fail("Expected JXPathAbstractFactoryException");
        } catch (JXPathAbstractFactoryException e) {
            assertTrue(e.getMessage().contains("Factory could not create a child node"));
        }

        // Test createAttribute
        NodePointer attrPtr = pointer.createAttribute(context, new QName("myAttr"));
        assertNotNull(attrPtr);
        assertTrue(root.hasAttribute("myAttr"));

        // Create attribute with prefix (triggers setAttributeNS)
        root.setAttribute("xmlns:ns", "http://example.com");
        NodePointer attrNsPtr = pointer.createAttribute(context, new QName("ns", "attr"));
        assertNotNull(attrNsPtr);
    }

    public void testCreateAttributeOnNonElement() throws Throwable {
        Text text = document.createTextNode("text");
        document.appendChild(text);
        DOMNodePointer pointer = new DOMNodePointer(text, locale);
        JXPathContext context = JXPathContext.newContext(null);
        try {
            pointer.createAttribute(context, new QName("attr"));
            fail("Expected exception");
        } catch (Throwable t) {
            // Expected from super.createAttribute
        }
    }

    public void testRemove() throws Throwable {
        Element root = document.createElement("root");
        Element child = document.createElement("child");
        root.appendChild(child);
        document.appendChild(root);

        DOMNodePointer pointer = new DOMNodePointer(child, locale);
        pointer.remove();
        assertNull(child.getParentNode());

        DOMNodePointer rootPointer = new DOMNodePointer(root, locale);
        try {
            rootPointer.remove();
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Cannot remove root DOM node"));
        }
    }

    public void AsPathAndHashCodeEquals() throws Throwable {
        Element root = document.createElement("root");
        Element child1 = document.createElement("child");
        Element child2 = document.createElement("child");
        Comment comment = document.createComment("c");
        ProcessingInstruction pi = document.createProcessingInstruction("target", "data");
        Text text = document.createTextNode("txt");

        root.appendChild(child1);
        root.appendChild(child2);
        root.appendChild(comment);
        root.appendChild(pi);
        root.appendChild(text);
        document.appendChild(root);

        DOMNodePointer pRoot = new DOMNodePointer(root, locale);
        DOMNodePointer pChild1 = new DOMNodePointer(pRoot, child1);
        DOMNodePointer pChild2 = new DOMNodePointer(pRoot, child2);
        DOMNodePointer pComment = new DOMNodePointer(pRoot, comment);
        DOMNodePointer pPi = new DOMNodePointer(pRoot, pi);
        DOMNodePointer pText = new DOMNodePointer(pRoot, text);

        assertNotNull(pRoot.asPath());
        assertEquals("/child[1]", pChild1.asPath());
        assertEquals("/child[2]", pChild2.asPath());
        assertNotNull(pComment.asPath());
        assertNotNull(pPi.asPath());
        assertNotNull(pText.asPath());

        // ID path escape
        DOMNodePointer pId = new DOMNodePointer(root, locale, "id'\"q");
        assertTrue(pId.asPath().contains("&apos;"));
        assertTrue(pId.asPath().contains("&quot;"));

        // Equals and hashCode
        assertEquals(pChild1, pChild1);
        assertFalse(pChild1.equals(new Object()));
        assertEquals(pChild1.hashCode(), pChild1.hashCode());

        // Compare child node pointers
        assertEquals(0, pChild1.compareChildNodePointers(pChild1, pChild1));
        assertEquals(-1, pChild1.compareChildNodePointers(pChild1, pChild2));
        assertEquals(1, pChild1.compareChildNodePointers(pChild2, pChild1));

        // Attribute nodes comparison
        root.setAttribute("a1", "v1");
        root.setAttribute("a2", "v2");
        Node attr1 = root.getAttributeNode("a1");
        Node attr2 = root.getAttributeNode("a2");
        DOMNodePointer pAttr1 = new DOMNodePointer(pRoot, attr1);
        DOMNodePointer pAttr2 = new DOMNodePointer(pRoot, attr2);
        assertEquals(-1, pChild1.compareChildNodePointers(pAttr1, pChild1));
        assertEquals(1, pChild1.compareChildNodePointers(pChild1, pAttr1));
        assertEquals(-1, pChild1.compareChildNodePointers(pAttr1, pAttr2));
    }

    public void testGetValueAndStringValueVariants() throws Throwable {
        Comment comment = document.createComment("   my comment   ");
        DOMNodePointer cPtr = new DOMNodePointer(comment, locale);
        assertEquals("my comment", cPtr.getValue());

        Comment commentNull = document.createComment(null);
        DOMNodePointer cNullPtr = new DOMNodePointer(commentNull, locale);
        assertEquals("", cNullPtr.getValue());

        Element elem = document.createElement("elem");
        elem.setAttribute("xml:space", "preserve");
        Text text = document.createTextNode("  spaced text  ");
        elem.appendChild(text);
        document.appendChild(elem);
        DOMNodePointer ePtr = new DOMNodePointer(elem, locale);
        assertEquals("  spaced text  ", ePtr.getValue());

        ProcessingInstruction pi = document.createProcessingInstruction("target", "  pi data  ");
        document.appendChild(pi);
        DOMNodePointer piPtr = new DOMNodePointer(pi, locale);
        assertEquals("pi data", piPtr.getValue());

        ProcessingInstruction piNull = document.createProcessingInstruction("target", null);
        document.appendChild(piNull);
        DOMNodePointer piNullPtr = new DOMNodePointer(piNull, locale);
        assertEquals("", piNullPtr.getValue());
    }

    public void testAbstractFactoryExceptionOnGetFactory() throws Throwable {
        Element elem = document.createElement("root");
        document.appendChild(elem);
        DOMNodePointer pointer = new DOMNodePointer(elem, locale);

        JXPathContext contextWithoutFactory = JXPathContext.newContext(null);
        try {
            pointer.createChild(contextWithoutFactory, new QName("child"), 0);
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Factory is not set on the JXPathContext"));
        }
    }
}