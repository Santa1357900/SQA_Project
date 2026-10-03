package org.apache.commons.jxpath.ri.model.dom;

import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
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
import org.junit.Test;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import static org.junit.Assert.*;

public class DOMNodePointerTest {

    private Document createDocument() throws Throwable {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElement("testElem");
        doc.appendChild(elem);

        DOMNodePointer pointer1 = new DOMNodePointer(elem, Locale.ENGLISH);
        DOMNodePointer pointer2 = new DOMNodePointer(elem, Locale.ENGLISH, "id123");
        DOMNodePointer pointer3 = new DOMNodePointer(pointer1, elem);

        assertEquals(elem, pointer1.getBaseValue());
        assertEquals(elem, pointer1.getImmediateNode());
        assertTrue(pointer1.isActual());
        assertFalse(pointer1.isCollection());
        assertEquals(1, pointer1.getLength());
        assertEquals(Locale.ENGLISH, pointer1.getLocale());
        assertNotNull(pointer1.hashCode());
        assertTrue(pointer1.equals(new DOMNodePointer(elem, Locale.ENGLISH)));
        assertFalse(pointer1.equals(new Object()));
        assertFalse(pointer1.equals(null));

        assertNotNull(pointer1.getNamespaceResolver());
        assertNotNull(pointer1.namespacePointer("xml"));
        assertNotNull(pointer1.namespaceIterator());
        assertNotNull(pointer1.attributeIterator(new QName("attr")));
        assertNotNull(pointer1.childIterator(null, false, null));
    }

    @Test
    public void testTestNodeNullAndTypes() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElement("elem");
        doc.appendChild(elem);
        Text text = doc.createTextNode("hello");
        elem.appendChild(text);
        Comment comment = doc.createComment("comment");
        elem.appendChild(comment);
        ProcessingInstruction pi = doc.createProcessingInstruction("target", "data");
        elem.appendChild(pi);

        assertTrue(DOMNodePointer.testNode(elem, null));

        // NodeNameTest
        NodeNameTest nameTest = new NodeNameTest(new QName("elem"));
        assertTrue(DOMNodePointer.testNode(elem, nameTest));
        assertFalse(DOMNodePointer.testNode(text, nameTest));

        NodeNameTest wildcardTest = new NodeNameTest(new QName(null, "*"));
        assertTrue(DOMNodePointer.testNode(elem, wildcardTest));

        NodeNameTest nsWildcardTest = new NodeNameTest(new QName("prefix", "local"), "http://uri");
        assertFalse(DOMNodePointer.testNode(elem, nsWildcardTest));

        // NodeTypeTest
        NodeTypeTest nodeTypeNode = new NodeTypeTest(Compiler.NODE_TYPE_NODE);
        assertTrue(DOMNodePointer.testNode(elem, nodeTypeNode));
        assertTrue(DOMNodePointer.testNode(doc, nodeTypeNode));
        assertFalse(DOMNodePointer.testNode(text, nodeTypeNode));

        NodeTypeTest nodeTypeText = new NodeTypeTest(Compiler.NODE_TYPE_TEXT);
        assertTrue(DOMNodePointer.testNode(text, nodeTypeText));
        assertFalse(DOMNodePointer.testNode(elem, nodeTypeText));

        NodeTypeTest nodeTypeComment = new NodeTypeTest(Compiler.NODE_TYPE_COMMENT);
        assertTrue(DOMNodePointer.testNode(comment, nodeTypeComment));

        NodeTypeTest nodeTypePi = new NodeTypeTest(Compiler.NODE_TYPE_PI);
        assertTrue(DOMNodePointer.testNode(pi, nodeTypePi));
        assertFalse(DOMNodePointer.testNode(elem, nodeTypePi));

        NodeTypeTest nodeTypeInvalid = new NodeTypeTest(999);
        assertFalse(DOMNodePointer.testNode(elem, nodeTypeInvalid));

        // ProcessingInstructionTest
        ProcessingInstructionTest piTest = new ProcessingInstructionTest("target");
        assertTrue(DOMNodePointer.testNode(pi, piTest));
        ProcessingInstructionTest piTestDiff = new ProcessingInstructionTest("other");
        assertFalse(DOMNodePointer.testNode(pi, piTestDiff));
        assertFalse(DOMNodePointer.testNode(elem, piTest));
    }

    @Test
    public void testGetName() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElementNS("http://example.com", "p:elem");
        doc.appendChild(elem);
        DOMNodePointer ptrElem = new DOMNodePointer(elem, Locale.ENGLISH);
        assertEquals("elem", ptrElem.getName().getName());
        assertEquals("p", ptrElem.getName().getPrefix());

        ProcessingInstruction pi = doc.createProcessingInstruction("myTarget", "myData");
        DOMNodePointer ptrPi = new DOMNodePointer(pi, Locale.ENGLISH);
        assertEquals("myTarget", ptrPi.getName().getName());

        Text text = doc.createTextNode("txt");
        DOMNodePointer ptrText = new DOMNodePointer(text, Locale.ENGLISH);
        assertNull(ptrText.getName().getName());
    }

    @Test
    public void testNamespacesAndLanguage() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElement("root");
        elem.setAttribute("xmlns:foo", "http://foo.com");
        elem.setAttribute("xmlns", "http://default.com");
        elem.setAttribute("xml:lang", "en-US");
        doc.appendChild(elem);

        DOMNodePointer ptr = new DOMNodePointer(elem, Locale.ENGLISH);
        assertEquals("http://foo.com", ptr.getNamespaceURI("foo"));
        assertEquals("http://www.w3.org/XML/1998/namespace", ptr.getNamespaceURI("xml"));
        assertEquals("http://www.w3.org/2000/xmlns/", ptr.getNamespaceURI("xmlns"));
        assertEquals("http://default.com", ptr.getNamespaceURI(""));
        assertEquals("http://default.com", ptr.getNamespaceURI(null));
        assertEquals("http://foo.com", ptr.getNamespaceURI("foo")); // cache test

        assertNotNull(ptr.getDefaultNamespaceURI());
        assertTrue(ptr.isLanguage("en"));
        assertTrue(ptr.isLanguage("EN-US"));
        assertFalse(ptr.isLanguage("fr"));

        // Document node namespace check
        DOMNodePointer docPtr = new DOMNodePointer(doc, Locale.ENGLISH);
        assertNotNull(docPtr.getDefaultNamespaceURI());
        assertNotNull(docPtr.getNamespaceURI("foo"));

        // Unknown prefix
        assertNull(ptr.getNamespaceURI("unknown"));
    }

    @Test
    public void testSetValueAndLeaf() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElement("elem");
        doc.appendChild(elem);
        Text text = doc.createTextNode("initial");
        elem.appendChild(text);

        DOMNodePointer textPtr = new DOMNodePointer(text, Locale.ENGLISH);
        assertFalse(textPtr.isLeaf());
        textPtr.setValue("updated");
        assertEquals("updated", text.getNodeValue());

        // Empty string removal
        textPtr.setValue("");
        assertNull(text.getParentNode());

        // Set value on Element with Element value
        Element childElem = doc.createElement("child");
        childElem.setTextContent("childText");
        DOMNodePointer elemPtr = new DOMNodePointer(elem, Locale.ENGLISH);
        elemPtr.setValue(childElem);
        assertTrue(elem.hasChildNodes());

        // Set value on Element with Document value
        Document doc2 = createDocument();
        Element doc2Elem = doc2.createElement("doc2Elem");
        doc2.appendChild(doc2Elem);
        elemPtr.setValue(doc2);

        // Set value on Element with String value
        elemPtr.setValue("stringVal");
        assertEquals("stringVal", elem.getTextContent());

        // Comment node value
        Comment comment = doc.createComment("   my comment   ");
        DOMNodePointer commentPtr = new DOMNodePointer(comment, Locale.ENGLISH);
        assertEquals("my comment", commentPtr.getValue());
        
        Comment emptyComment = doc.createComment(null);
        DOMNodePointer emptyCommentPtr = new DOMNodePointer(emptyComment, Locale.ENGLISH);
        assertEquals("", emptyCommentPtr.getValue());
    }

    @Test
    public void testCreateChildAndAttribute() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElement("root");
        doc.appendChild(elem);
        DOMNodePointer ptr = new DOMNodePointer(elem, Locale.ENGLISH);

        JXPathContext context = JXPathContext.newContext(new Object());
        
        // Without AbstractFactory should throw exception
        try {
            ptr.createChild(context, new QName("child"), 0);
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Factory is not set"));
        }

        // With AbstractFactory returning false
        context.setFactory(new AbstractFactory() {
            public boolean createObject(JXPathContext context, Pointer pointer, Object parent, String name, int index) {
                return false;
            }
        });

        try {
            ptr.createChild(context, new QName("child"), 0);
            fail("Expected JXPathAbstractFactoryException");
        } catch (JXPathAbstractFactoryException e) {
            assertTrue(e.getMessage().contains("Factory could not create"));
        }

        // With AbstractFactory returning true
        context.setFactory(new AbstractFactory() {
            public boolean createObject(JXPathContext context, Pointer pointer, Object parent, String name, int index) {
                Element newChild = doc.createElement(name);
                ((Node) parent).appendChild(newChild);
                return true;
            }
        });

        NodePointer newChildPtr = ptr.createChild(context, new QName("child"), 0);
        assertNotNull(newChildPtr);

        NodePointer newChildValPtr = ptr.createChild(context, new QName("childVal"), 0, "val");
        assertNotNull(newChildValPtr);

        // Create Attribute on non-element
        DOMNodePointer textPtr = new DOMNodePointer(doc.createTextNode("txt"), Locale.ENGLISH);
        try {
            textPtr.createAttribute(context, new QName("attr"));
        } catch (Exception e) {
            // expected or super behavior
        }

        // Create Attribute on element without prefix
        NodePointer attrPtr = ptr.createAttribute(context, new QName("myAttr"));
        assertNotNull(attrPtr);

        // Create Attribute with prefix and unknown namespace
        try {
            ptr.createAttribute(context, new QName("prefix", "myAttr"));
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Unknown namespace prefix"));
        }
    }

    @Test
    public void testRemoveAndAsPath() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElement("root");
        doc.appendChild(elem);
        Element child = doc.createElement("child");
        elem.appendChild(child);
        Element child2 = doc.createElement("child");
        elem.appendChild(child2);

        DOMNodePointer rootPtr = new DOMNodePointer(elem, Locale.ENGLISH);
        DOMNodePointer childPtr = new DOMNodePointer(rootPtr, child);
        DOMNodePointer child2Ptr = new DOMNodePointer(rootPtr, child2);

        assertEquals("/child[1]", childPtr.asPath());
        assertEquals("/child[2]", child2Ptr.asPath());

        // Text node path
        Text text = doc.createTextNode("txt");
        child.appendChild(text);
        DOMNodePointer textPtr = new DOMNodePointer(childPtr, text);
        assertTrue(textPtr.asPath().contains("/text()"));

        // Processing instruction path
        ProcessingInstruction pi = doc.createProcessingInstruction("target", "data");
        child.appendChild(pi);
        DOMNodePointer piPtr = new DOMNodePointer(childPtr, pi);
        assertTrue(piPtr.asPath().contains("/processing-instruction('target')"));

        // ID path
        DOMNodePointer idPtr = new DOMNodePointer(elem, Locale.ENGLISH, "my'Id\"String");
        assertEquals("id('my&apos;Id&quot;String')", idPtr.asPath());

        // Remove root node should throw exception
        try {
            rootPtr.remove();
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Cannot remove root DOM node"));
        }

        // Remove child node
        childPtr.remove();
        assertNull(child.getParentNode());
    }

    @Test
    public void testGetPointerByIDAndCompareChildNodePointers() throws Throwable {
        Document doc = createDocument();
        Element elem = doc.createElement("root");
        elem.setAttribute("id", "elemId");
        doc.appendChild(elem);

        DOMNodePointer rootPtr = new DOMNodePointer(doc, Locale.ENGLISH);
        Pointer ptrById = rootPtr.getPointerByID(JXPathContext.newContext(new Object()), "elemId");
        assertNotNull(ptrById);

        Pointer ptrByIdNull = rootPtr.getPointerByID(JXPathContext.newContext(new Object()), "nonexistent");
        assertTrue(ptrByIdNull instanceof NullPointer);

        // Compare child node pointers
        Element c1 = doc.createElement("c1");
        Element c2 = doc.createElement("c2");
        elem.appendChild(c1);
        elem.appendChild(c2);

        DOMNodePointer p1 = new DOMNodePointer(rootPtr, c1);
        DOMNodePointer p2 = new DOMNodePointer(rootPtr, c2);
        DOMNodePointer p1Again = new DOMNodePointer(rootPtr, c1);

        assertEquals(0, rootPtr.compareChildNodePointers(p1, p1Again));
        assertEquals(-1, rootPtr.compareChildNodePointers(p1, p2));
        assertEquals(1, rootPtr.compareChildNodePointers(p2, p1));

        // Attribute comparison
        elem.setAttribute("a1", "val1");
        elem.setAttribute("a2", "val2");
        DOMNodePointer a1Ptr = new DOMNodePointer(rootPtr, elem.getAttributeNode("a1"));
        DOMNodePointer a2Ptr = new DOMNodePointer(rootPtr, elem.getAttributeNode("a2"));
        assertEquals(-1, rootPtr.compareChildNodePointers(a1Ptr, a2Ptr));
        assertEquals(1, rootPtr.compareChildNodePointers(a2Ptr, a1Ptr));
        assertEquals(-1, rootPtr.compareChildNodePointers(a1Ptr, p1));
        assertEquals(1, rootPtr.compareChildNodePointers(p1, a1Ptr));
    }
}