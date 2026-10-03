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
import org.apache.commons.jxpath.ri.compiler.NodeTest;
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

    private Document createTestDocument() throws Throwable {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://example.com/ns", "root");
        root.setAttribute("xmlns", "http://example.com/default");
        root.setAttribute("xmlns:ns1", "http://example.com/ns1");
        root.setAttribute("xml:lang", "en-US");
        doc.appendChild(root);

        Element child = doc.createElementNS("http://example.com/ns1", "ns1:child");
        child.setAttribute("id", "elem-id");
        child.setAttribute("attr1", "value1");
        root.appendChild(child);

        Comment comment = doc.createComment(" my comment ");
        root.appendChild(comment);

        Text text = doc.createTextNode("  hello text  ");
        root.appendChild(text);

        ProcessingInstruction pi = doc.createProcessingInstruction("target", "data");
        root.appendChild(pi);

        return doc;
    }

    @Test
    public void testConstructorsAndBasics() throws Throwable {
        Document doc = createTestDocument();
        DOMNodePointer pointer1 = new DOMNodePointer(doc, Locale.ENGLISH);
        DOMNodePointer pointer2 = new DOMNodePointer(doc, Locale.ENGLISH, "myid");
        DOMNodePointer pointer3 = new DOMNodePointer(pointer1, doc.getDocumentElement());

        assertNotNull(pointer1.getBaseValue());
        assertNotNull(pointer1.getImmediateNode());
        assertTrue(pointer1.isActual());
        assertFalse(pointer1.isCollection());
        assertEquals(1, pointer1.getLength());
        assertFalse(pointer1.isLeaf());
        assertEquals(Locale.ENGLISH, pointer1.getLocale());
        
        assertEquals(doc, pointer2.getBaseValue());
        assertEquals("id('myid')", pointer2.asPath());
        
        assertEquals(pointer1, pointer1);
        assertFalse(pointer1.equals(null));
        assertFalse(pointer1.equals("string"));
        assertTrue(pointer1.equals(new DOMNodePointer(doc, Locale.FRENCH)));
        
        assertEquals(System.identityHashCode(doc), pointer1.hashCode());
    }

    @Test
    public void testTestNodeNull() throws Throwable {
        Document doc = createTestDocument();
        DOMNodePointer pointer = new DOMNodePointer(doc, Locale.ENGLISH);
        assertTrue(DOMNodePointer.testNode(doc, null));
        assertTrue(pointer.testNode(null));
    }

    @Test
    public void testTestNodeNameTest() throws Throwable {
        Document doc = createTestDocument();
        Element root = doc.getDocumentElement();
        Element child = (Element) root.getFirstChild();

        // Wildcard with prefix == null
        NodeNameTest testWildcardNullPrefix = new NodeNameTest(new QName(null, "*"), null);
        assertTrue(DOMNodePointer.testNode(root, testWildcardNullPrefix));

        // Non-element node should fail NodeNameTest
        Comment comment = doc.createComment("test");
        assertFalse(DOMNodePointer.testNode(comment, testWildcardNullPrefix));

        // Wildcard with matching namespace
        NodeNameTest testWildcardNS = new NodeNameTest(new QName("ns1", "*"), "http://example.com/ns1");
        assertTrue(DOMNodePointer.testNode(child, testWildcardNS));

        // Specific local name and namespace
        NodeNameTest testSpecific = new NodeNameTest(new QName("ns1", "child"), "http://example.com/ns1");
        assertTrue(DOMNodePointer.testNode(child, testSpecific));

        // Mismatched local name
        NodeNameTest testMismatchName = new NodeNameTest(new QName("ns1", "wrong"), "http://example.com/ns1");
        assertFalse(DOMNodePointer.testNode(child, testMismatchName));
    }

    @Test
    public void testTestNodeTypeTest() throws Throwable {
        Document doc = createTestDocument();
        Element root = doc.getDocumentElement();
        Text text = doc.createTextNode("text");
        Comment comment = doc.createComment("comment");
        ProcessingInstruction pi = doc.createProcessingInstruction("t", "d");

        NodeTypeTest nodeTest = new NodeTypeTest(Compiler.NODE_TYPE_NODE);
        assertTrue(DOMNodePointer.testNode(root, nodeTest));
        assertFalse(DOMNodePointer.testNode(text, nodeTest));

        NodeTypeTest textTest = new NodeTypeTest(Compiler.NODE_TYPE_TEXT);
        assertTrue(DOMNodePointer.testNode(text, textTest));
        assertTrue(DOMNodePointer.testNode(doc.createCDATASection("cdata"), textTest));
        assertFalse(DOMNodePointer.testNode(root, textTest));

        NodeTypeTest commentTest = new NodeTypeTest(Compiler.NODE_TYPE_COMMENT);
        assertTrue(DOMNodePointer.testNode(comment, commentTest));
        assertFalse(DOMNodePointer.testNode(root, commentTest));

        NodeTypeTest piTest = new NodeTypeTest(Compiler.NODE_TYPE_PI);
        assertTrue(DOMNodePointer.testNode(pi, piTest));
        assertFalse(DOMNodePointer.testNode(root, piTest));

        NodeTypeTest unknownTest = new NodeTypeTest(999);
        assertFalse(DOMNodePointer.testNode(root, unknownTest));
    }

    @Test
    public void testTestNodeProcessingInstructionTest() throws Throwable {
        Document doc = createTestDocument();
        ProcessingInstruction pi = doc.createProcessingInstruction("target1", "data");
        ProcessingInstructionTest piTest = new ProcessingInstructionTest("target1");
        assertTrue(DOMNodePointer.testNode(pi, piTest));

        ProcessingInstructionTest piTestMismatch = new ProcessingInstructionTest("target2");
        assertFalse(DOMNodePointer.testNode(pi, piTestMismatch));

        assertFalse(DOMNodePointer.testNode(doc.getDocumentElement(), piTest));
    }

    @Test
    public void testGetName() throws Throwable {
        Document doc = createTestDocument();
        Element root = doc.getDocumentElement();
        DOMNodePointer rootPtr = new DOMNodePointer(root, Locale.ENGLISH);
        QName rootName = rootPtr.getName();
        assertEquals("root", rootName.getName());

        ProcessingInstruction pi = doc.createProcessingInstruction("my-target", "data");
        DOMNodePointer piPtr = new DOMNodePointer(pi, Locale.ENGLISH);
        assertEquals("my-target", piPtr.getName().getName());
    }

    @Test
    public void testNamespacesAndPrefixes() throws Throwable {
        Document doc = createTestDocument();
        Element child = (Element) doc.getDocumentElement().getFirstChild();
        DOMNodePointer ptr = new DOMNodePointer(child, Locale.ENGLISH);

        assertEquals("http://example.com/ns1", ptr.getNamespaceURI());
        assertEquals("http://example.com/ns1", ptr.getNamespaceURI("ns1"));
        assertEquals("http://www.w3.org/XML/1998/namespace", ptr.getNamespaceURI("xml"));
        assertEquals("http://www.w3.org/2000/xmlns/", ptr.getNamespaceURI("xmlns"));
        assertEquals("http://example.com/default", ptr.getNamespaceURI(""));
        assertEquals("http://example.com/default", ptr.getNamespaceURI(null));

        // Test unknown prefix caching
        assertNull(ptr.getNamespaceURI("unknownPrefix"));
        // Hit cache branch
        assertNull(ptr.getNamespaceURI("unknownPrefix"));

        // Test Document node namespace resolution
        DOMNodePointer docPtr = new DOMNodePointer(doc, Locale.ENGLISH);
        assertEquals("http://example.com/default", docPtr.getDefaultNamespaceURI());

        // Test static helpers
        assertEquals("ns1", DOMNodePointer.getPrefix(child));
        assertEquals("child", DOMNodePointer.getLocalName(child));

        Element noPrefixElem = doc.createElement("simple");
        assertNull(DOMNodePointer.getPrefix(noPrefixElem));
        assertEquals("simple", DOMNodePointer.getLocalName(noPrefixElem));
    }

    @Test
    public void testIteratorsAndPointers() throws Throwable {
        Document doc = createTestDocument();
        DOMNodePointer ptr = new DOMNodePointer(doc.getDocumentElement(), Locale.ENGLISH);

        assertNotNull(ptr.childIterator(null, false, null));
        assertNotNull(ptr.attributeIterator(new QName("attr1")));
        assertNotNull(ptr.namespacePointer("ns1"));
        assertNotNull(ptr.namespaceIterator());
    }

    @Test
    public void testIsLanguage() throws Throwable {
        Document doc = createTestDocument();
        Element child = (Element) doc.getDocumentElement().getFirstChild();
        DOMNodePointer ptr = new DOMNodePointer(child, Locale.ENGLISH);

        assertTrue(ptr.isLanguage("en"));
        assertTrue(ptr.isLanguage("EN-US"));
        assertFalse(ptr.isLanguage("fr"));
    }

    @Test
    public void testSetValueTextNode() throws Throwable {
        Document doc = createTestDocument();
        Text text = doc.createTextNode("old");
        doc.getDocumentElement().appendChild(text);
        DOMNodePointer ptr = new DOMNodePointer(text, Locale.ENGLISH);

        ptr.setValue("new value");
        assertEquals("new value", text.getNodeValue());

        // Empty value removes node
        ptr.setValue("");
        assertNull(text.getParentNode());
    }

    @Test
    public void testSetValueElementNode() throws Throwable {
        Document doc = createTestDocument();
        Element parent = doc.createElement("parent");
        parent.appendChild(doc.createTextNode("childText"));
        DOMNodePointer ptr = new DOMNodePointer(parent, Locale.ENGLISH);

        Element replacement = doc.createElement("replacement");
        replacement.appendChild(doc.createTextNode("repText"));

        ptr.setValue(replacement);
        assertEquals(1, parent.getChildNodes().getLength());
        assertEquals("replacement", parent.getFirstChild().getNodeName());

        // Set with string value
        ptr.setValue("text string");
        assertEquals(1, parent.getChildNodes().getLength());
        assertEquals("text string", parent.getFirstChild().getNodeValue());
    }

    @Test
    public void testCreateChildAndAttribute() throws Throwable {
        Document doc = createTestDocument();
        Element root = doc.getDocumentElement();
        DOMNodePointer ptr = new DOMNodePointer(root, Locale.ENGLISH);

        JXPathContext context = JXPathContext.newContext(doc);
        context.setFactory(new AbstractFactory() {
            public boolean createObject(JXPathContext context, Pointer pointer, Object parent, String name, int index) {
                Element el = ((Document) ((Node) parent).getOwnerDocument()).createElement(name);
                ((Node) parent).appendChild(el);
                return true;
            }
        });

        // Test createChild WHOLE_COLLECTION (-1)
        NodePointer created = ptr.createChild(context, new QName("newChild"), NodePointer.WHOLE_COLLECTION);
        assertNotNull(created);

        // Test createChild with value
        NodePointer createdWithValue = ptr.createChild(context, new QName("valChild"), 0, "testVal");
        assertNotNull(createdWithValue);
        assertEquals("testVal", createdWithValue.getValue());

        // Test createAttribute with prefix and without prefix
        DOMNodePointer childPtr = new DOMNodePointer(root.getFirstChild(), Locale.ENGLISH);
        NodePointer attrPtr1 = childPtr.createAttribute(context, new QName("ns1", "newAttr"));
        assertNotNull(attrPtr1);

        NodePointer attrPtr2 = childPtr.createAttribute(context, new QName("plainAttr"));
        assertNotNull(attrPtr2);
        // Test existing attribute branch
        NodePointer attrPtr3 = childPtr.createAttribute(context, new QName("plainAttr"));
        assertNotNull(attrPtr3);

        // Test createAttribute on non-element falls back
        DOMNodePointer textPtr = new DOMNodePointer(root.getLastChild(), Locale.ENGLISH);
        try {
            textPtr.createAttribute(context, new QName("attr"));
        } catch (Throwable t) {
            // Expected fallback or exception
        }
    }

    @Test(expected = JXPathAbstractFactoryException.class)
    public void testCreateChildFactoryFailure() throws Throwable {
        Document doc = createTestDocument();
        DOMNodePointer ptr = new DOMNodePointer(doc.getDocumentElement(), Locale.ENGLISH);
        JXPathContext context = JXPathContext.newContext(doc);
        // No factory set -> should throw JXPathAbstractFactoryException
        ptr.createChild(context, new QName("failChild"), 0);
    }

    @Test(expected = JXPathException.class)
    public void testRemoveRootNode() throws Throwable {
        Document doc = createTestDocument();
        DOMNodePointer ptr = new DOMNodePointer(doc, Locale.ENGLISH);
        ptr.remove();
    }

    @Test
    public void testRemoveNormalNode() throws Throwable {
        Document doc = createTestDocument();
        Element child = (Element) doc.getDocumentElement().getFirstChild();
        DOMNodePointer ptr = new DOMNodePointer(child, Locale.ENGLISH);
        ptr.remove();
        assertNull(child.getParentNode());
    }

    @Test
    public void testAsPathVariations() throws Throwable {
        Document doc = createTestDocument();
        Element root = doc.getDocumentElement();
        Element child = (Element) root.getFirstChild();

        DOMNodePointer rootPtr = new DOMNodePointer(root, Locale.ENGLISH);
        DOMNodePointer childPtr = new DOMNodePointer(rootPtr, child);

        // Element with default namespace match
        String path1 = childPtr.asPath();
        assertNotNull(path1);

        // Add second child with same name to test relative position by name
        Element child2 = (Element) root.insertBefore(child.cloneNode(true), child);
        DOMNodePointer childPtr2 = new DOMNodePointer(rootPtr, child2);
        assertTrue(childPtr2.asPath().contains("[1]"));

        // Text node path
        Text text = doc.createTextNode("abc");
        root.appendChild(text);
        DOMNodePointer textPtr = new DOMNodePointer(rootPtr, text);
        assertTrue(textPtr.asPath().contains("/text()"));

        // Comment / PI path
        ProcessingInstruction pi = doc.createProcessingInstruction("my-pi", "val");
        root.appendChild(pi);
        DOMNodePointer piPtr = new DOMNodePointer(rootPtr, pi);
        assertTrue(piPtr.asPath().contains("/processing-instruction("));
    }

    @Test
    public void testGetPointerByID() throws Throwable {
        Document doc = createTestDocument();
        DOMNodePointer ptr = new DOMNodePointer(doc, Locale.ENGLISH);

        Pointer found = ptr.getPointerByID(JXPathContext.newContext(doc), "elem-id");
        assertNotNull(found);
        assertFalse(found instanceof NullPointer);

        Pointer notFound = ptr.getPointerByID(JXPathContext.newContext(doc), "non-existent");
        assertTrue(notFound instanceof NullPointer);

        // Test from non-document node
        DOMNodePointer childPtr = new DOMNodePointer(doc.getDocumentElement(), Locale.ENGLISH);
        Pointer found2 = childPtr.getPointerByID(JXPathContext.newContext(doc), "elem-id");
        assertNotNull(found2);
    }

    @Test
    public void testCompareChildNodePointers() throws Throwable {
        Document doc = createTestDocument();
        Element root = doc.getDocumentElement();
        DOMNodePointer rootPtr = new DOMNodePointer(root, Locale.ENGLISH);

        Element c1 = (Element) root.appendChild(doc.createElement("c1"));
        Element c2 = (Element) root.appendChild(doc.createElement("c2"));

        DOMNodePointer p1 = new DOMNodePointer(rootPtr, c1);
        DOMNodePointer p2 = new DOMNodePointer(rootPtr, c2);

        assertEquals(0, rootPtr.compareChildNodePointers(p1, p1));
        assertEquals(-1, rootPtr.compareChildNodePointers(p1, p2));
        assertEquals(1, rootPtr.compareChildNodePointers(p2, p1));

        // Attribute comparison
        DOMNodePointer attr1 = new DOMNodePointer(rootPtr, c1); // dummy base for test
        // Test attribute branch
        Element el = doc.createElement("el");
        el.setAttribute("a", "1");
        el.setAttribute("b", "2");
        DOMNodePointer elPtr = new DOMNodePointer(el, Locale.ENGLISH);
        DOMNodePointer ap1 = new DOMNodePointer(elPtr, el.getAttributeNode("a"));
        DOMNodePointer ap2 = new DOMNodePointer(elPtr, el.getAttributeNode("b"));
        assertEquals(-1, elPtr.compareChildNodePointers(ap1, ap2));
        assertEquals(1, elPtr.compareChildNodePointers(ap2, ap1));
        assertEquals(0, elPtr.compareChildNodePointers(ap1, ap1));
    }

    @Test
    public void testValueStringifyVariations() throws Throwable {
        Document doc = createTestDocument();
        DOMNodePointer commentPtr = new DOMNodePointer(doc.createComment("  comment data  "), Locale.ENGLISH);
        assertEquals("comment data", commentPtr.getValue());

        DOMNodePointer piPtr = new DOMNodePointer(doc.createProcessingInstruction("target", "  pi data  "), Locale.ENGLISH);
        assertEquals("pi data", piPtr.getValue());
    }
}