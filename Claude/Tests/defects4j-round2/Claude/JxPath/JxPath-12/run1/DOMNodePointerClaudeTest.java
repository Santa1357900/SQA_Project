package org.apache.commons.jxpath.ri.model.dom;

import java.util.Iterator;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import static org.junit.Assert.*;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;

public class DOMNodePointerClaudeTest {

    private Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }

    // Constructor(Node, Locale): getImmediateNode/getBaseValue ต้องคืนโหนดเดิม
    @Test
    public void testConstructor_nodeLocale_getImmediateNodeReturnsNode() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertSame(elem, pointer.getImmediateNode());
        assertSame(elem, pointer.getBaseValue());
    }

    // Constructor(Node, Locale, id): asPath() ต้องคืน id('...') และ escape single quote เป็น &apos;
    @Test
    public void testConstructor_withId_asPathEscapesQuote() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US, "a'b");
        assertEquals("id('a&apos;b')", pointer.asPath());
    }

    // testNode: test==null ต้องคืน true เสมอ ทั้ง instance และ static
    @Test
    public void testTestNode_nullTest_returnsTrue() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertTrue(pointer.testNode(null));
        assertTrue(DOMNodePointer.testNode(elem, null));
    }

    // บั๊ก: NodeTypeTest ชนิด node() ตามสเปค XPath ต้อง match โหนดทุกชนิด (element, comment, text)
    // ไม่ใช่เฉพาะ ELEMENT_NODE/DOCUMENT_NODE
    @Test
    public void testTestNode_xpathNodeTest_matchesAllChildNodeTypes() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element child = doc.createElement("child");
        Comment comment = doc.createComment("c");
        Text text = doc.createTextNode("hello");
        root.appendChild(child);
        root.appendChild(comment);
        root.appendChild(text);
        JXPathContext context = JXPathContext.newContext(doc);
        Iterator it = context.iterate("/root/node()");
        int count = 0;
        while (it.hasNext()) {
            it.next();
            count++;
        }
        assertEquals(3, count);
    }

    // getName(): element ไม่มี prefix ต้องได้ QName(null, localName)
    @Test
    public void testGetName_elementNoPrefix() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        QName name = pointer.getName();
        assertEquals("foo", name.getName());
        assertNull(name.getPrefix());
    }

    // getName(): element มี namespace prefix ต้องได้ QName(prefix, localName)
    @Test
    public void testGetName_elementWithPrefix() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElementNS("urn:test", "p:foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        QName name = pointer.getName();
        assertEquals("foo", name.getName());
        assertEquals("p", name.getPrefix());
    }

    // getName(): processing-instruction ต้องได้ target เป็นชื่อ, prefix เป็น null
    @Test
    public void testGetName_processingInstruction() throws Throwable {
        Document doc = newDocument();
        ProcessingInstruction pi = doc.createProcessingInstruction("target", "data");
        DOMNodePointer pointer = new DOMNodePointer(pi, Locale.US);
        QName name = pointer.getName();
        assertEquals("target", name.getName());
        assertNull(name.getPrefix());
    }

    // getName(): node ที่ไม่ใช่ element/PI (text) ต้องได้ QName(null,null)
    @Test
    public void testGetName_textNode_nullName() throws Throwable {
        Document doc = newDocument();
        Text text = doc.createTextNode("abc");
        DOMNodePointer pointer = new DOMNodePointer(text, Locale.US);
        QName name = pointer.getName();
        assertNull(name.getName());
        assertNull(name.getPrefix());
    }

    // getNamespaceURI(): element ไม่มี namespace -> null, element ที่สร้างด้วย namespace URI -> คืน URI นั้น
    @Test
    public void testGetNamespaceURI_instance_noneAndDirect() throws Throwable {
        Document doc = newDocument();
        Element plain = doc.createElement("foo");
        DOMNodePointer plainPointer = new DOMNodePointer(plain, Locale.US);
        assertNull(plainPointer.getNamespaceURI());

        Element ns = doc.createElementNS("urn:test", "p:bar");
        DOMNodePointer nsPointer = new DOMNodePointer(ns, Locale.US);
        assertEquals("urn:test", nsPointer.getNamespaceURI());
    }

    // getNamespaceURI(): ไม่มี namespace โดยตรง แต่ต้องไล่หา attribute xmlns จาก ancestor
    @Test
    public void testGetNamespaceURI_instance_inheritedFromXmlnsAttribute() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        root.setAttribute("xmlns", "urn:default");
        Element child = doc.createElement("child");
        root.appendChild(child);
        DOMNodePointer pointer = new DOMNodePointer(child, Locale.US);
        assertEquals("urn:default", pointer.getNamespaceURI());
    }

    // getNamespaceURI(prefix): prefix null หรือ "" ต้อง delegate ไปที่ getDefaultNamespaceURI()
    @Test
    public void testGetNamespaceURIPrefix_nullOrEmpty_delegatesToDefault() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        root.setAttribute("xmlns", "urn:def");
        DOMNodePointer pointer = new DOMNodePointer(root, Locale.US);
        assertEquals("urn:def", pointer.getNamespaceURI((String) null));
        assertEquals("urn:def", pointer.getNamespaceURI(""));
    }

    // getNamespaceURI(prefix): prefix "xml" และ "xmlns" ต้องคืนค่าคงที่ตามมาตรฐาน
    @Test
    public void testGetNamespaceURIPrefix_xmlAndXmlnsConstants() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertEquals(DOMNodePointer.XML_NAMESPACE_URI, pointer.getNamespaceURI("xml"));
        assertEquals(DOMNodePointer.XMLNS_NAMESPACE_URI, pointer.getNamespaceURI("xmlns"));
    }

    // getNamespaceURI(prefix): prefix ไม่รู้จัก -> null, prefix ที่มี xmlns:prefix attribute -> คืน URI (รวม cache)
    @Test
    public void testGetNamespaceURIPrefix_unknownAndKnownAttribute() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        root.setAttribute("xmlns:ns1", "urn:ns1");
        DOMNodePointer pointer = new DOMNodePointer(root, Locale.US);
        assertNull(pointer.getNamespaceURI("unknownprefix"));
        assertEquals("urn:ns1", pointer.getNamespaceURI("ns1"));
        assertEquals("urn:ns1", pointer.getNamespaceURI("ns1"));
    }

    // getDefaultNamespaceURI(): ไม่มี attribute xmlns ที่ไหนเลย ต้องคืน null
    @Test
    public void testGetDefaultNamespaceURI_none_returnsNull() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertNull(pointer.getDefaultNamespaceURI());
    }

    // getDefaultNamespaceURI(): มี attribute xmlns บน element ต้องคืนค่า URI นั้น
    @Test
    public void testGetDefaultNamespaceURI_withAttr_returnsURI() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        elem.setAttribute("xmlns", "urn:dflt");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertEquals("urn:dflt", pointer.getDefaultNamespaceURI());
    }

    // isActual/isCollection/getLength: ค่าคงที่ตามสัญญาเสมอ
    @Test
    public void testIsActualIsCollectionGetLength_constants() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertTrue(pointer.isActual());
        assertFalse(pointer.isCollection());
        assertEquals(1, pointer.getLength());
    }

    // isLeaf(): element ไม่มีลูก ต้องเป็น true
    @Test
    public void testIsLeaf_noChildren_true() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertTrue(pointer.isLeaf());
    }

    // isLeaf(): element มีลูก ต้องเป็น false
    @Test
    public void testIsLeaf_withChildren_false() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        elem.appendChild(doc.createElement("child"));
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertFalse(pointer.isLeaf());
    }

    // isLanguage(): มี xml:lang ที่ตรง prefix (case-insensitive) ต้องคืน true
    @Test
    public void testIsLanguage_matchingPrefix_true() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        elem.setAttribute("xml:lang", "en-US");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertTrue(pointer.isLanguage("en"));
    }

    // isLanguage(): มี xml:lang แต่ไม่ตรง prefix ต้องคืน false
    @Test
    public void testIsLanguage_nonMatchingPrefix_false() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        elem.setAttribute("xml:lang", "en-US");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertFalse(pointer.isLanguage("fr"));
    }

    // setValue(): text node ค่าที่ไม่ว่าง ต้องตั้งค่า node value ใหม่
    @Test
    public void testSetValue_textNode_nonEmpty_updatesValue() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        Text text = doc.createTextNode("old");
        root.appendChild(text);
        DOMNodePointer pointer = new DOMNodePointer(text, Locale.US);
        pointer.setValue("new");
        assertEquals("new", text.getNodeValue());
    }

    // setValue(): text node ค่าว่าง ต้องลบ node ออกจาก parent
    @Test
    public void testSetValue_textNode_empty_removesNode() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        Text text = doc.createTextNode("old");
        root.appendChild(text);
        DOMNodePointer pointer = new DOMNodePointer(text, Locale.US);
        pointer.setValue("");
        assertEquals(0, root.getChildNodes().getLength());
    }

    // setValue(): element ด้วยค่า String ต้องลบลูกเดิมแล้วสร้าง text node ใหม่
    @Test
    public void testSetValue_elementNode_stringAppendsTextChild() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        root.appendChild(doc.createElement("old"));
        DOMNodePointer pointer = new DOMNodePointer(root, Locale.US);
        pointer.setValue("hello");
        assertEquals(1, root.getChildNodes().getLength());
        assertEquals("hello", root.getFirstChild().getNodeValue());
    }

    // setValue(): element ด้วยค่าเป็น Element ต้อง clone ลูกของ value มาแทนที่ลูกเดิม
    @Test
    public void testSetValue_elementNode_elementValueCopiesChildren() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element source = doc.createElement("source");
        source.appendChild(doc.createElement("a"));
        DOMNodePointer pointer = new DOMNodePointer(root, Locale.US);
        pointer.setValue(source);
        assertEquals(1, root.getChildNodes().getLength());
        assertEquals("a", root.getFirstChild().getNodeName());
    }

    // createChild(): ถ้า context ไม่มี AbstractFactory ตั้งไว้ ต้อง throw JXPathException
    @Test
    public void testCreateChild_noFactory_throwsJXPathException() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        DOMNodePointer pointer = new DOMNodePointer(root, Locale.US);
        JXPathContext context = JXPathContext.newContext(new Object());
        try {
            pointer.createChild(context, new QName(null, "child"), 0);
            fail("expected JXPathException");
        }
        catch (JXPathException expected) {
            assertTrue(expected.getMessage().indexOf("Factory") >= 0);
        }
    }

    // createAttribute(): ไม่มี prefix และยังไม่มี attribute นี้ ต้องสร้าง attribute ค่าว่าง
    @Test
    public void testCreateAttribute_noPrefix_setsAttribute() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        DOMNodePointer pointer = new DOMNodePointer(root, Locale.US);
        pointer.createAttribute(null, new QName(null, "attr1"));
        assertTrue(root.hasAttribute("attr1"));
        assertEquals("", root.getAttribute("attr1"));
    }

    // createAttribute(): prefix ที่ไม่รู้จัก (ไม่มี xmlns:prefix) ต้อง throw JXPathException
    @Test
    public void testCreateAttribute_unknownPrefix_throwsJXPathException() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        DOMNodePointer pointer = new DOMNodePointer(root, Locale.US);
        try {
            pointer.createAttribute(null, new QName("unknownprefix", "attr2"));
            fail("expected JXPathException");
        }
        catch (JXPathException expected) {
            assertTrue(expected.getMessage().indexOf("prefix") >= 0);
        }
    }

    // remove(): node ที่มี parent ต้องถูกลบออกจาก parent จริง
    @Test
    public void testRemove_withParent_removesNode() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element child = doc.createElement("child");
        root.appendChild(child);
        DOMNodePointer pointer = new DOMNodePointer(child, Locale.US);
        pointer.remove();
        assertEquals(0, root.getChildNodes().getLength());
    }

    // remove(): node ที่ไม่มี parent (document root) ต้อง throw JXPathException
    @Test
    public void testRemove_rootNode_throwsJXPathException() throws Throwable {
        Document doc = newDocument();
        DOMNodePointer pointer = new DOMNodePointer(doc, Locale.US);
        try {
            pointer.remove();
            fail("expected JXPathException");
        }
        catch (JXPathException expected) {
            assertTrue(expected.getMessage().indexOf("root") >= 0);
        }
    }

    // asPath(): document node ที่ไม่มี parent/id ต้องได้ path ว่าง
    @Test
    public void testAsPath_documentNode_empty() throws Throwable {
        Document doc = newDocument();
        DOMNodePointer pointer = new DOMNodePointer(doc, Locale.US);
        assertEquals("", pointer.asPath());
    }

    // asPath(): element ที่มี parent เป็น DOMNodePointer ต้องได้ /localName[position]
    @Test
    public void testAsPath_elementWithDOMParent_includesPositionIndex() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        DOMNodePointer docPointer = new DOMNodePointer(doc, Locale.US);
        DOMNodePointer rootPointer = new DOMNodePointer(docPointer, root);
        assertEquals("/root[1]", rootPointer.asPath());
    }

    // hashCode(): ต้องเท่ากับ System.identityHashCode(node)
    @Test
    public void testHashCode_matchesIdentityHashCode() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertEquals(System.identityHashCode(elem), pointer.hashCode());
    }

    // equals(): node เดียวกัน -> true, node ต่างกัน -> false, object ไม่ใช่ DOMNodePointer -> false
    @Test
    public void testEquals_variants() throws Throwable {
        Document doc = newDocument();
        Element elem1 = doc.createElement("foo");
        Element elem2 = doc.createElement("bar");
        DOMNodePointer pointer1a = new DOMNodePointer(elem1, Locale.US);
        DOMNodePointer pointer1b = new DOMNodePointer(elem1, Locale.US);
        DOMNodePointer pointer2 = new DOMNodePointer(elem2, Locale.US);
        assertTrue(pointer1a.equals(pointer1b));
        assertFalse(pointer1a.equals(pointer2));
        assertFalse(pointer1a.equals("not a pointer"));
    }

    // getPrefix() static: มี prefix จาก DOM -> คืน prefix, ไม่มีแต่มี ':' ใน nodeName -> คืนส่วนหน้า ':', ไม่มีเลย -> null
    @Test
    public void testGetPrefix_static_variants() throws Throwable {
        Document doc = newDocument();
        Element withPrefix = doc.createElementNS("urn:test", "p:foo");
        assertEquals("p", DOMNodePointer.getPrefix(withPrefix));
        Element fallback = doc.createElement("ns:bar");
        assertEquals("ns", DOMNodePointer.getPrefix(fallback));
        Element none = doc.createElement("plain");
        assertNull(DOMNodePointer.getPrefix(none));
    }

    // getLocalName() static: มี localName จาก DOM -> คืนตรงๆ, ไม่มี -> fallback ตัดส่วนก่อน ':' ออก
    @Test
    public void testGetLocalName_static_variants() throws Throwable {
        Document doc = newDocument();
        Element withLocal = doc.createElementNS("urn:test", "p:foo");
        assertEquals("foo", DOMNodePointer.getLocalName(withLocal));
        Element fallback = doc.createElement("ns:bar");
        assertEquals("bar", DOMNodePointer.getLocalName(fallback));
        Element plain = doc.createElement("plain");
        assertEquals("plain", DOMNodePointer.getLocalName(plain));
    }

    // getNamespaceURI(Node) static: document node ต้องใช้ namespace ของ documentElement
    @Test
    public void testGetNamespaceURI_static_documentNode() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElementNS("urn:docns", "root");
        doc.appendChild(root);
        assertEquals("urn:docns", DOMNodePointer.getNamespaceURI(doc));
    }

    // getValue(): comment node ต้อง trim ข้อความแล้วคืนค่า
    @Test
    public void testGetValue_commentNode_trims() throws Throwable {
        Document doc = newDocument();
        Comment comment = doc.createComment("  hello  ");
        DOMNodePointer pointer = new DOMNodePointer(comment, Locale.US);
        assertEquals("hello", pointer.getValue());
    }

    // getValue(): element node ต้องรวมข้อความลูกทั้งหมด (text ที่ trim แล้ว)
    @Test
    public void testGetValue_elementNode_concatenatesTrimmedText() throws Throwable {
        Document doc = newDocument();
        Element elem = doc.createElement("foo");
        elem.appendChild(doc.createTextNode("  abc  "));
        DOMNodePointer pointer = new DOMNodePointer(elem, Locale.US);
        assertEquals("abc", pointer.getValue());
    }

    // getPointerByID(): หา element ด้วย id attribute ที่ถูก mark เป็น ID -> ได้ DOMNodePointer ของ element นั้น
    @Test
    public void testGetPointerByID_found_returnsElementPointer() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        root.setAttribute("id", "x1");
        root.setIdAttribute("id", true);
        DOMNodePointer docPointer = new DOMNodePointer(doc, Locale.US);
        Pointer result = docPointer.getPointerByID(null, "x1");
        assertTrue(result instanceof DOMNodePointer);
        assertSame(root, ((DOMNodePointer) result).getBaseValue());
    }

    // getPointerByID(): id ไม่พบ ต้องคืน NullPointer
    @Test
    public void testGetPointerByID_notFound_returnsNullPointer() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        DOMNodePointer docPointer = new DOMNodePointer(doc, Locale.US);
        Pointer result = docPointer.getPointerByID(null, "nope");
        assertTrue(result instanceof NullPointer);
    }

    // compareChildNodePointers(): element ก่อนหน้าต้อง -1, หลังต้อง 1, node เดียวกันต้อง 0
    @Test
    public void testCompareChildNodePointers_elementOrderAndSame() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element c1 = doc.createElement("c1");
        Element c2 = doc.createElement("c2");
        root.appendChild(c1);
        root.appendChild(c2);
        DOMNodePointer parentPointer = new DOMNodePointer(root, Locale.US);
        DOMNodePointer p1 = new DOMNodePointer(parentPointer, c1);
        DOMNodePointer p2 = new DOMNodePointer(parentPointer, c2);
        assertEquals(-1, parentPointer.compareChildNodePointers(p1, p2));
        assertEquals(1, parentPointer.compareChildNodePointers(p2, p1));
        assertEquals(0, parentPointer.compareChildNodePointers(p1, p1));
    }

    // compareChildNodePointers(): attribute node ต้องมาก่อน non-attribute node เสมอ
    @Test
    public void testCompareChildNodePointers_attributeBeforeElement() throws Throwable {
        Document doc = newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        root.setAttribute("attr1", "v");
        Attr attr = root.getAttributeNode("attr1");
        Element child = doc.createElement("child");
        root.appendChild(child);
        DOMNodePointer parentPointer = new DOMNodePointer(root, Locale.US);
        DOMNodePointer attrPointer = new DOMNodePointer(parentPointer, attr);
        DOMNodePointer childPointer = new DOMNodePointer(parentPointer, child);
        assertEquals(-1, parentPointer.compareChildNodePointers(attrPointer, childPointer));
        assertEquals(1, parentPointer.compareChildNodePointers(childPointer, attrPointer));
    }
}
