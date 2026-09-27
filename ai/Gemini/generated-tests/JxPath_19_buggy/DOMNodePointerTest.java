package org.apache.commons.jxpath.ri.model.dom;

import junit.framework.TestCase;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.ri.Compiler;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.compiler.NodeNameTest;
import org.apache.commons.jxpath.ri.compiler.NodeTest;
import org.apache.commons.jxpath.ri.compiler.NodeTypeTest;
import org.apache.commons.jxpath.ri.compiler.ProcessingInstructionTest;
import org.apache.commons.jxpath.ri.model.NodeIterator;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import java.util.Locale;

public class DOMNodePointerTest extends TestCase {

    private Document document;
    private Locale locale;

    protected void setUp() throws Exception {
        super.setUp();
        locale = Locale.ENGLISH;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.newDocument();
    }

    public void testConstructorsAndBasicGetters() throws Throwable {
        Element root = document.createElement("root");
        document.appendChild(root);

        DOMNodePointer ptr1 = new DOMNodePointer(root, locale);
        assertSame(root, ptr1.getBaseValue());
        assertSame(root, ptr1.getImmediateNode());
        assertEquals(locale, ptr1.getLocale());
        assertTrue(ptr1.isActual());
        assertFalse(ptr1.isCollection());
        assertEquals(1, ptr1.getLength());

        DOMNodePointer ptr2 = new DOMNodePointer(root, locale, "id123");
        assertEquals("id('id123')", ptr2.asPath());

        DOMNodePointer ptr3 = new DOMNodePointer(ptr1, root);
        assertSame(root, ptr3.getBaseValue());
    }

    public void testTestNodeNull() throws Throwable {
        Element root = document.createElement("root");
        DOMNodePointer ptr = new DOMNodePointer(root, locale);
        assertTrue(ptr.testNode(null));
        assertTrue(DOMNodePointer.testNode(root, null));
    }

    public void testTestNodeNameTest() throws Throwable {
        Element root = document.createElement("child");
        root.setAttribute("xmlns:ns", "http://example.com");
        document.appendChild(root);

        DOMNodePointer ptr = new DOMNodePointer(root, locale);

        // Wildcard test with null prefix
        NodeNameTest wildcardTest1 = new NodeNameTest(new QName(null, "*"));
        assertTrue(ptr.testNode(wildcardTest1));

        // Wildcard test with prefix
        NodeNameTest wildcardTest2 = new NodeNameTest(new QName("ns", "*"), "http://example.com");
        assertTrue(ptr.testNode(wildcardTest2));

        // Specific name test matching local name and namespace
        NodeNameTest nameTest = new NodeNameTest(new QName("child"), null);
        assertTrue(ptr.testNode(nameTest));

        // Non-element node failing NodeNameTest
        Text text = document.createTextNode("hello");
        assertFalse(DOMNodePointer.testNode(text, nameTest));

        // Mismatched name
        NodeNameTest mismatchTest = new NodeNameTest(new QName("other"), null);
        assertFalse(ptr.testNode(mismatchTest));

        // Wildcard with prefix mismatch or match
        NodeNameTest wildcardNsTest = new NodeNameTest(new QName("ns", "*"), "http://other.com");
        assertFalse(ptr.testNode(wildcardNsTest));
    }

    public void testTestNodeTypeTest() throws Throwable {
        Element root = document.createElement("root");
        Comment comment = document.createElement("root").getOwnerDocument().createComment("comment");
        ProcessingInstruction pi = document.createProcessingInstruction("target", "data");
        Text text = document.createTextNode("text");

        DOMNodePointer nodePtr = new DOMNodePointer(root, locale);

        assertTrue(nodePtr.testNode(new NodeTypeTest(Compiler.NODE_TYPE_NODE)));

        DOMNodePointer textPtr = new DOMNodePointer(text, locale);
        assertTrue(textPtr.testNode(new NodeTypeTest(Compiler.NODE_TYPE_TEXT)));

        DOMNodePointer commentPtr = new DOMNodePointer(comment, locale);
        assertTrue(commentPtr.testNode(new NodeTypeTest(Compiler.NODE_TYPE_COMMENT)));

        DOMNodePointer piPtr = new DOMNodePointer(pi, locale);
        assertTrue(piPtr.testNode(new NodeTypeTest(Compiler.NODE_TYPE_PI)));

        // Default / unknown node type test
        NodeTypeTest unknownTest = new NodeTypeTest(999);
        assertFalse(nodePtr.testNode(unknownTest));
    }

    public void testTestNodeProcessingInstructionTest() throws Throwable {
        ProcessingInstruction pi = document.createProcessingInstruction("myTarget", "myData");
        DOMNodePointer ptr = new DOMNodePointer(pi, locale);

        ProcessingInstructionTest piTest = new ProcessingInstructionTest("myTarget");
        assertTrue(ptr.testNode(piTest));

        ProcessingInstructionTest piTestMismatch = new ProcessingInstructionTest("otherTarget");
        assertFalse(ptr.testNode(piTestMismatch));

        Element root = document.createElement("root");
        assertFalse(DOMNodePointer.testNode(root, piTest));
    }

    public void testGetName() throws Throwable {
        Element elem = document.createElementNS("http://example.com", "ns:myelem");
        document.appendChild(elem);
        DOMNodePointer ptr = new DOMNodePointer(elem, locale);
        QName qName = ptr.getName();
        assertEquals("ns", qName.getPrefix());
        assertEquals("myelem", qName.getName());

        ProcessingInstruction pi = document.createProcessingInstruction("targetVal", "dataVal");
        DOMNodePointer piPtr = new DOMNodePointer(pi, locale);
        assertEquals("targetVal", piPtr.getName().getName());

        Text text = document.createTextNode("txt");
        DOMNodePointer textPtr = new DOMNodePointer(text, locale);
        assertNull(textPtr.getName().getName());
    }

    public void testNamespacesAndResolvers() throws Throwable {
        Element root = document.createElement("root");
        root.setAttribute("xmlns:prefix1", "http://uri1");
        root.setAttribute("xmlns", "http://default-uri");
        document.appendChild(root);

        DOMNodePointer ptr = new DOMNodePointer(root, locale);

        assertEquals("http://uri1", ptr.getNamespaceURI("prefix1"));
        assertEquals(DOMNodePointer.XML_NAMESPACE_URI, ptr.getNamespaceURI("xml"));
        assertEquals(DOMNodePointer.XMLNS_NAMESPACE_URI, ptr.getNamespaceURI("xmlns"));
        assertEquals("http://default-uri", ptr.getNamespaceURI(""));
        assertEquals("http://default-uri", ptr.getNamespaceURI(null));
        assertNull(ptr.getNamespaceURI("nonexistent"));

        assertNotNull(ptr.getNamespaceResolver());
        assertNotNull(ptr.namespacePointer("prefix1"));
        
        NodeIterator nsIter = ptr.namespaceIterator();
        assertNotNull(nsIter);

        NodeIterator attrIter = ptr.attributeIterator(new QName("attr"));
        assertNotNull(attrIter);

        NodeIterator childIter = ptr.childIterator(null, false, null);
        assertNotNull(childIter);
    }

    public void testIsLeafAndLanguage() throws Throwable {
        Element root = document.createElement("root");
        root.setAttribute("xml:lang", "en-US");
        document.appendChild(root);

        DOMNodePointer ptr = new DOMNodePointer(root, locale);
        assertTrue(ptr.isLeaf());

        Element child = document.createElement("child");
        root.appendChild(child);
        assertFalse(ptr.isLeaf());

        assertTrue(ptr.isLanguage("en"));
        assertTrue(ptr.isLanguage("EN-US"));
        assertFalse(ptr.isLanguage("fr"));
    }

    public void testSetValueTextNode() throws Throwable {
        Text text = document.createTextNode("initial");
        document.appendChild(text);
        DOMNodePointer ptr = new DOMNodePointer(text, locale);

        ptr.setValue("updated");
        assertEquals("updated", text.getNodeValue());

        // Empty string removal
        ptr.setValue("");
        assertNull(text.getParentNode());
    }

    public void testSetValueElementNode() throws Throwable {
        Element root = document.createElement("root");
        Element child = document.createElement("child");
        root.appendChild(child);
        document.appendChild(root);

        DOMNodePointer ptr = new DOMNodePointer(root, locale);

        Element newValueNode = document.createElement("newChild");
        ptr.setValue(newValueNode);
        assertEquals(1, root.getChildNodes().getLength());
        assertEquals("newChild", root.getFirstChild().getNodeName());

        // String value setting
        ptr.setValue("textValue");
        assertEquals(1, root.getChildNodes().getLength());
        assertEquals("textValue", root.getFirstChild().getNodeValue());
    }

    public void testRemoveRoot() throws Throwable {
        Element root = document.createElement("root");
        DOMNodePointer ptr = new DOMNodePointer(root, locale);
        try {
            ptr.remove();
            fail("Expected exception when removing root node");
        } catch (org.apache.commons.jxpath.JXPathException e) {
            assertTrue(e.getMessage().contains("Cannot remove root DOM node"));
        }
    }

    public void testRemoveNonRoot() throws Throwable {
        Element root = document.createElement("root");
        Element child = document.createElement("child");
        root.appendChild(child);
        document.appendChild(root);

        DOMNodePointer ptr = new DOMNodePointer(child, locale);
        ptr.remove();
        assertNull(child.getParentNode());
    }

    public void testAsPathVariations() throws Throwable {
        Element root = document.createElement("root");
        Element child1 = document.createElement("child");
        Element child2 = document.createElement("child");
        Text text = document.createTextNode("content");
        ProcessingInstruction pi = document.createProcessingInstruction("target", "data");

        root.appendChild(child1);
        root.appendChild(child2);
        child2.appendChild(text);
        child2.appendChild(pi);
        document.appendChild(root);

        DOMNodePointer rootPtr = new DOMNodePointer(root, locale);
        DOMNodePointer child1Ptr = new DOMNodePointer(rootPtr, child1);
        DOMNodePointer child2Ptr = new DOMNodePointer(rootPtr, child2);
        DOMNodePointer textPtr = new DOMNodePointer(child2Ptr, text);
        DOMNodePointer piPtr = new DOMNodePointer(child2Ptr, pi);

        assertEquals("child[1]", child1Ptr.asPath());
        assertEquals("child[2]", child2Ptr.asPath());
        assertEquals("child[2]/text()[1]", textPtr.asPath());
        assertEquals("child[2]/processing-instruction('target')[1]", piPtr.asPath());
    }

    public void testHashCodeAndEquals() throws Throwable {
        Element root1 = document.createElement("root");
        Element root2 = document.createElement("root");

        DOMNodePointer ptr1 = new DOMNodePointer(root1, locale);
        DOMNodePointer ptr2 = new DOMNodePointer(root1, locale);
        DOMNodePointer ptr3 = new DOMNodePointer(root2, locale);

        assertTrue(ptr1.equals(ptr1));
        assertTrue(ptr1.equals(ptr2));
        assertFalse(ptr1.equals(ptr3));
        assertFalse(ptr1.equals("someString"));

        assertEquals(root1.hashCode(), ptr1.hashCode());
    }

    public void testGetStaticGetPrefixAndLocalName() throws Throwable {
        Element elem = document.createElementNS("http://example.com", "myPrefix:localName");
        assertEquals("myPrefix", DOMNodePointer.getPrefix(elem));
        assertEquals("localName", DOMNodePointer.getLocalName(elem));

        Element elemNoPrefix = document.createElement("plainName");
        assertNull(DOMNodePointer.getPrefix(elemNoPrefix));
        assertEquals("plainName", DOMNodePointer.getLocalName(elemNoPrefix));
    }

    public void testGetValueCommentAndStringValue() throws Throwable {
        Comment comment = document.createComment("   my comment   ");
        DOMNodePointer commentPtr = new DOMNodePointer(comment, locale);
        assertEquals("my comment", commentPtr.getValue());

        Comment emptyComment = document.createComment("");
        DOMNodePointer emptyCommentPtr = new DOMNodePointer(emptyComment, locale);
        assertEquals("", emptyCommentPtr.getValue());

        Element root = document.createElement("root");
        root.appendChild(document.createTextNode("  hello  "));
        DOMNodePointer rootPtr = new DOMNodePointer(root, locale);
        assertEquals("hello", rootPtr.getValue());
    }

    public void testGetPointerByID() throws Throwable {
        Element root = document.createElement("root");
        Element child = document.createElement("child");
        child.setAttribute("id", "uniqueId");
        root.appendChild(child);
        document.appendChild(root);

        DOMNodePointer ptr = new DOMNodePointer(root, locale);
        Pointer found = ptr.getPointerByID(null, "uniqueId");
        assertNotNull(found);

        Pointer notFound = ptr.getPointerByID(null, "nonexistent");
        assertTrue(notFound instanceof NullPointer);
    }

    public void testCompareChildNodePointers() throws Throwable {
        Element root = document.createElement("root");
        Element child1 = document.createElement("child1");
        Element child2 = document.createElement("child2");
        root.appendChild(child1);
        root.appendChild(child2);
        document.appendChild(root);

        DOMNodePointer parentPtr = new DOMNodePointer(root, locale);
        DOMNodePointer p1 = new DOMNodePointer(parentPtr, child1);
        DOMNodePointer p2 = new DOMNodePointer(parentPtr, child2);

        assertEquals(0, parentPtr.compareChildNodePointers(p1, p1));
        assertEquals(-1, parentPtr.compareChildNodePointers(p1, p2));
        assertEquals(1, parentPtr.compareChildNodePointers(p2, p1));

        // Attribute comparison branches
        root.setAttribute("attr1", "val1");
        root.setAttribute("attr2", "val2");
        DOMNodePointer attrPtr1 = new DOMNodePointer(parentPtr, root.getAttributeNode("attr1"));
        DOMNodePointer attrPtr2 = new DOMNodePointer(parentPtr, root.getAttributeNode("attr2"));

        assertEquals(-1, parentPtr.compareChildNodePointers(attrPtr1, p1));
        assertEquals(1, parentPtr.compareChildNodePointers(p1, attrPtr1));
        assertEquals(-1, parentPtr.compareChildNodePointers(attrPtr1, attrPtr2));
    }

    public void testCreateAttributeWithNamespace() throws Throwable {
        Element root = document.createElement("root");
        document.appendChild(root);
        DOMNodePointer ptr = new DOMNodePointer(root, locale);

        try {
            ptr.createAttribute(null, new QName("unknownNs", "attrName"));
            fail("Expected exception for unknown namespace prefix");
        } catch (org.apache.commons.jxpath.JXPathException e) {
            assertTrue(e.getMessage().contains("Unknown namespace prefix"));
        }
    }
}