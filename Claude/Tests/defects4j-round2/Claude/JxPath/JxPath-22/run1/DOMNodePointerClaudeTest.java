package org.apache.commons.jxpath.ri.model.dom;

import static org.junit.Assert.*;

import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.NamespaceResolver;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.compiler.NodeNameTest;
import org.apache.commons.jxpath.ri.model.NodeIterator;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

public class DOMNodePointerClaudeTest {

    private Document doc;

    @Before
    public void setUp() throws Throwable {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.newDocument();
    }

    // testNode: test=null ต้อง true เสมอ, NodeNameTest ที่ชื่อตรงกันและไม่มี namespace ต้อง true
    @Test
    public void testTestNode_nullOrMatchingName_returnsTrue() throws Throwable {
        Element el = doc.createElement("foo");
        assertTrue(DOMNodePointer.testNode(el, null));
        NodeNameTest test = new NodeNameTest(new QName(null, "foo"), null);
        assertTrue(DOMNodePointer.testNode(el, test));
    }

    // testNode: ชื่อไม่ตรงคืน false, และ node ที่ไม่ใช่ ELEMENT_NODE คืน false เสมอสำหรับ NodeNameTest
    @Test
    public void testTestNode_nonMatchingNameOrNonElement_returnsFalse() throws Throwable {
        Element el = doc.createElement("foo");
        NodeNameTest test = new NodeNameTest(new QName(null, "bar"), null);
        assertFalse(DOMNodePointer.testNode(el, test));
        Text t = doc.createTextNode("x");
        NodeNameTest test2 = new NodeNameTest(new QName(null, "foo"), null);
        assertFalse(DOMNodePointer.testNode(t, test2));
    }

    // getName: element ไม่มี prefix ต้องได้ QName(null, tagName)
    @Test
    public void testGetName_elementNoPrefix() throws Throwable {
        Element el = doc.createElement("foo");
        QName name = new DOMNodePointer(el, Locale.US).getName();
        assertNull(name.getPrefix());
        assertEquals("foo", name.getName());
    }

    // getName: element มี prefix ต้องได้ QName(prefix, localName)
    @Test
    public void testGetName_elementWithPrefix() throws Throwable {
        Element el = doc.createElementNS("http://example.com", "ns:tag");
        QName name = new DOMNodePointer(el, Locale.US).getName();
        assertEquals("ns", name.getPrefix());
        assertEquals("tag", name.getName());
    }

    // getName: processing-instruction ใช้ target เป็นชื่อ, node type อื่นคืนชื่อ null
    @Test
    public void testGetName_nonElementNodeTypes() throws Throwable {
        ProcessingInstruction pi = doc.createProcessingInstruction("target1", "data");
        QName piName = new DOMNodePointer(pi, Locale.US).getName();
        assertEquals("target1", piName.getName());
        Comment c = doc.createComment("x");
        QName cName = new DOMNodePointer(c, Locale.US).getName();
        assertNull(cName.getName());
    }

    // getNamespaceURI (instance): element ธรรมดาไม่มี namespace ต้องคืน null
    @Test
    public void testGetNamespaceURI_instance_plainElement_returnsNull() throws Throwable {
        Element el = doc.createElement("foo");
        assertNull(new DOMNodePointer(el, Locale.US).getNamespaceURI());
    }

    // getNamespaceURI (instance): element ที่สร้างแบบ NS-aware ต้องคืน namespace URI ตรงๆ
    @Test
    public void testGetNamespaceURI_instance_nsAwareElement_returnsURI() throws Throwable {
        Element el = doc.createElementNS("http://example.com", "ns:tag");
        assertEquals("http://example.com", new DOMNodePointer(el, Locale.US).getNamespaceURI());
    }

    // getNamespaceURI (instance): ไม่มี namespaceURI จาก DOM แต่มี attribute xmlns ต้องคืนค่า attribute นั้น
    @Test
    public void testGetNamespaceURI_instance_xmlnsAttribute_returnsAttributeValue() throws Throwable {
        Element el = doc.createElement("foo");
        el.setAttribute("xmlns", "http://foo.example.com");
        assertEquals("http://foo.example.com", new DOMNodePointer(el, Locale.US).getNamespaceURI());
    }

    // attributeIterator: ต้อง iterate ไปยัง attribute ที่ชื่อตรงกันและคืนค่าตามจริง
    @Test
    public void testAttributeIterator_returnsAttributeValue() throws Throwable {
        Element el = doc.createElement("foo");
        el.setAttribute("attr1", "val1");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        NodeIterator it = ptr.attributeIterator(new QName(null, "attr1"));
        assertTrue(it.setPosition(1));
        assertEquals("val1", it.getNodePointer().getValue());
    }

    // getNamespaceResolver: ต้อง cache instance เดียวกันเมื่อเรียกซ้ำ
    @Test
    public void testGetNamespaceResolver_cachesSameInstance() throws Throwable {
        DOMNodePointer ptr = new DOMNodePointer(doc.createElement("foo"), Locale.US);
        NamespaceResolver r1 = ptr.getNamespaceResolver();
        NamespaceResolver r2 = ptr.getNamespaceResolver();
        assertSame(r1, r2);
    }

    // getNamespaceURI(prefix): prefix null หรือ "" ต้องคืนค่า default namespace
    @Test
    public void testGetNamespaceURIPrefix_emptyOrNullPrefix_returnsDefaultNamespace() throws Throwable {
        Element el = doc.createElement("foo");
        el.setAttribute("xmlns", "http://default.example.com");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        assertEquals("http://default.example.com", ptr.getNamespaceURI(""));
        assertEquals("http://default.example.com", ptr.getNamespaceURI(null));
    }

    // getNamespaceURI(prefix): "xml" และ "xmlns" ต้องคืนค่า constant ที่กำหนดไว้เสมอ
    @Test
    public void testGetNamespaceURIPrefix_xmlAndXmlnsPrefixes_returnConstants() throws Throwable {
        DOMNodePointer ptr = new DOMNodePointer(doc.createElement("foo"), Locale.US);
        assertEquals(DOMNodePointer.XML_NAMESPACE_URI, ptr.getNamespaceURI("xml"));
        assertEquals(DOMNodePointer.XMLNS_NAMESPACE_URI, ptr.getNamespaceURI("xmlns"));
    }

    // getNamespaceURI(prefix): prefix ที่ไม่รู้จักและไม่มี declaration ใดๆ ต้องคืน null
    @Test
    public void testGetNamespaceURIPrefix_unknownPrefix_returnsNull() throws Throwable {
        DOMNodePointer ptr = new DOMNodePointer(doc.createElement("foo"), Locale.US);
        assertNull(ptr.getNamespaceURI("unknownPfx"));
    }

    // getNamespaceURI(prefix): prefix ที่ประกาศผ่าน xmlns:prefix attribute ต้องคืนค่า URI นั้น
    @Test
    public void testGetNamespaceURIPrefix_declaredPrefix_returnsURI() throws Throwable {
        Element el = doc.createElement("foo");
        el.setAttribute("xmlns:pfx", "http://pfx.example.com");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        assertEquals("http://pfx.example.com", ptr.getNamespaceURI("pfx"));
    }

    // getDefaultNamespaceURI: ไม่มี xmlns, มี xmlns ปกติ, และ xmlns="" ต้องคืน null ทั้งกรณีว่าง
    @Test
    public void testGetDefaultNamespaceURI_variousCases() throws Throwable {
        Element el1 = doc.createElement("a");
        assertNull(new DOMNodePointer(el1, Locale.US).getDefaultNamespaceURI());
        Element el2 = doc.createElement("b");
        el2.setAttribute("xmlns", "http://d.example.com");
        assertEquals("http://d.example.com", new DOMNodePointer(el2, Locale.US).getDefaultNamespaceURI());
        Element el3 = doc.createElement("c");
        el3.setAttribute("xmlns", "");
        assertNull(new DOMNodePointer(el3, Locale.US).getDefaultNamespaceURI());
    }

    // getBaseValue/getImmediateNode คืน node เดิม, isActual=true, isCollection=false, getLength=1 เสมอ
    @Test
    public void testSimpleAccessors_defaults() throws Throwable {
        Element el = doc.createElement("foo");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        assertSame(el, ptr.getBaseValue());
        assertSame(el, ptr.getImmediateNode());
        assertTrue(ptr.isActual());
        assertFalse(ptr.isCollection());
        assertEquals(1, ptr.getLength());
    }

    // isLeaf: ไม่มีลูก=true, มีลูก=false
    @Test
    public void testIsLeaf_variousCases() throws Throwable {
        Element el1 = doc.createElement("foo");
        assertTrue(new DOMNodePointer(el1, Locale.US).isLeaf());
        Element el2 = doc.createElement("foo");
        el2.appendChild(doc.createTextNode("x"));
        assertFalse(new DOMNodePointer(el2, Locale.US).isLeaf());
    }

    // isLanguage: xml:lang ตรงกัน (ไม่สนตัวพิมพ์เล็กใหญ่) = true, ไม่ตรง = false
    @Test
    public void testIsLanguage_variousCases() throws Throwable {
        Element el = doc.createElement("foo");
        el.setAttribute("xml:lang", "en-US");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        assertTrue(ptr.isLanguage("en"));
        assertFalse(ptr.isLanguage("fr"));
    }

    // findEnclosingAttribute: พบบน node เอง คืนค่า, ไม่พบเลยคืน null
    @Test
    public void testFindEnclosingAttribute_onNodeOrNotFound() throws Throwable {
        Element el = doc.createElement("foo");
        el.setAttribute("lang", "th");
        assertEquals("th", DOMNodePointer.findEnclosingAttribute(el, "lang"));
        Element el2 = doc.createElement("bar");
        assertNull(DOMNodePointer.findEnclosingAttribute(el2, "lang"));
    }

    // findEnclosingAttribute: ไม่พบบน node ตัวเองแต่พบที่บรรพบุรุษ
    @Test
    public void testFindEnclosingAttribute_onAncestor() throws Throwable {
        Element parent = doc.createElement("parent");
        parent.setAttribute("lang", "th");
        Element child = doc.createElement("child");
        parent.appendChild(child);
        assertEquals("th", DOMNodePointer.findEnclosingAttribute(child, "lang"));
    }

    // setValue บน text node ด้วยสตริงไม่ว่าง ต้องแทนที่เนื้อหาตามสัญญา
    @Test
    public void testSetValue_textNode_nonEmptyString_updatesValue() throws Throwable {
        Element parent = doc.createElement("p");
        Text t = doc.createTextNode("old");
        parent.appendChild(t);
        DOMNodePointer ptr = new DOMNodePointer(t, Locale.US);
        ptr.setValue("new");
        assertEquals("new", t.getNodeValue());
    }

    // setValue บน text node ด้วยสตริงว่าง ต้องลบ node ออกจาก parent
    @Test
    public void testSetValue_textNode_emptyString_removesNode() throws Throwable {
        Element parent = doc.createElement("p");
        Text t = doc.createTextNode("old");
        parent.appendChild(t);
        DOMNodePointer ptr = new DOMNodePointer(t, Locale.US);
        ptr.setValue("");
        assertEquals(0, parent.getChildNodes().getLength());
    }

    // setValue บน element ด้วยสตริง ต้องลบลูกเดิมทั้งหมดแล้วแทนที่ด้วยข้อความใหม่
    @Test
    public void testSetValue_elementNode_stringValue_replacesChildren() throws Throwable {
        Element el = doc.createElement("p");
        el.appendChild(doc.createTextNode("old"));
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        ptr.setValue("new");
        assertEquals("new", ptr.getValue());
    }

    // setValue บน element ด้วยค่าเป็น Element ต้องคัดลอกลูกของ value มาแทน
    @Test
    public void testSetValue_elementNode_elementValue_copiesChildren() throws Throwable {
        Element source = doc.createElement("src");
        source.appendChild(doc.createTextNode("child-text"));
        Element target = doc.createElement("tgt");
        DOMNodePointer ptr = new DOMNodePointer(target, Locale.US);
        ptr.setValue(source);
        assertEquals("child-text", ptr.getValue());
    }

    // setValue บน element ด้วยค่าเป็น Node ธรรมดา (ไม่ใช่ Element/Document) ต้อง clone node นั้นมาเป็นลูกโดยตรง
    @Test
    public void testSetValue_elementNode_plainNodeValue_appendsClone() throws Throwable {
        Element target = doc.createElement("tgt");
        Text valueNode = doc.createTextNode("directclone");
        DOMNodePointer ptr = new DOMNodePointer(target, Locale.US);
        ptr.setValue(valueNode);
        assertEquals("directclone", ptr.getValue());
    }

    // setValue บน element ด้วยสตริงว่าง ต้องลบลูกเดิมโดยไม่เพิ่ม text node ใหม่
    @Test
    public void testSetValue_elementNode_emptyString_noChildrenAdded() throws Throwable {
        Element el = doc.createElement("p");
        el.appendChild(doc.createTextNode("old"));
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        ptr.setValue("");
        assertTrue(ptr.isLeaf());
    }

    // createAttribute: ไม่มี prefix และยังไม่มี attribute นั้น ต้องสร้าง attribute ค่าว่าง
    @Test
    public void testCreateAttribute_noPrefix_createsEmptyAttribute() throws Throwable {
        Element el = doc.createElement("foo");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        NodePointer attrPtr = ptr.createAttribute(null, new QName(null, "newAttr"));
        assertTrue(el.hasAttribute("newAttr"));
        assertEquals("", attrPtr.getValue());
    }

    // createAttribute: prefix ที่ resolve namespace ไม่ได้ ต้องโยน JXPathException
    @Test
    public void testCreateAttribute_unknownPrefix_throwsJXPathException() throws Throwable {
        Element el = doc.createElement("foo");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US);
        try {
            ptr.createAttribute(null, new QName("unknownpfx", "attr"));
            fail("expected JXPathException");
        }
        catch (JXPathException expected) {
            assertTrue(expected.getMessage().indexOf("Unknown namespace prefix") >= 0);
        }
    }

    // remove: ลบ node ออกจาก parent สำเร็จเมื่อมี parent
    @Test
    public void testRemove_removesFromParent() throws Throwable {
        Element parent = doc.createElement("p");
        Element child = doc.createElement("c");
        parent.appendChild(child);
        DOMNodePointer ptr = new DOMNodePointer(child, Locale.US);
        ptr.remove();
        assertEquals(0, parent.getChildNodes().getLength());
    }

    // remove: ลบ root node (ไม่มี parent) ต้องโยน JXPathException
    @Test
    public void testRemove_rootNode_throwsJXPathException() throws Throwable {
        DOMNodePointer ptr = new DOMNodePointer(doc, Locale.US);
        try {
            ptr.remove();
            fail("expected JXPathException");
        }
        catch (JXPathException expected) {
            assertTrue(expected.getMessage().indexOf("remove") >= 0);
        }
    }

    // asPath: มี id ต้องคืน "id('...')"
    @Test
    public void testAsPath_withId_returnsIdExpression() throws Throwable {
        Element el = doc.createElement("foo");
        DOMNodePointer ptr = new DOMNodePointer(el, Locale.US, "abc123");
        assertEquals("id('abc123')", ptr.asPath());
    }

    // asPath: DOCUMENT_NODE คืนค่าว่าง และ element ที่ parent ไม่ใช่ DOMNodePointer ก็คืนค่าว่างเช่นกัน
    @Test
    public void testAsPath_documentNodeOrRootElementNoParent_returnsEmptyString() throws Throwable {
        DOMNodePointer docPtr = new DOMNodePointer(doc, Locale.US);
        assertEquals("", docPtr.asPath());
        Element root = doc.createElement("root");
        doc.appendChild(root);
        DOMNodePointer rootPtr = new DOMNodePointer(root, Locale.US);
        assertEquals("", rootPtr.asPath());
    }

    // asPath: element ลูกที่มี parent pointer เป็น DOMNodePointer ต้องได้ /tag[1]
    @Test
    public void testAsPath_elementWithDomParent_returnsIndexedPath() throws Throwable {
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element item = doc.createElement("item");
        root.appendChild(item);
        DOMNodePointer rootPtr = new DOMNodePointer(root, Locale.US);
        DOMNodePointer itemPtr = new DOMNodePointer(rootPtr, item);
        assertEquals("/item[1]", itemPtr.asPath());
    }

    // asPath: element ที่มีพี่น้องชื่อเดียวกัน ต้องนับตำแหน่งสัมพัทธ์ถูกต้อง
    @Test
    public void testAsPath_multipleSameNamedSiblings_incrementsIndex() throws Throwable {
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element item1 = doc.createElement("item");
        Element item2 = doc.createElement("item");
        root.appendChild(item1);
        root.appendChild(item2);
        DOMNodePointer rootPtr = new DOMNodePointer(root, Locale.US);
        DOMNodePointer item2Ptr = new DOMNodePointer(rootPtr, item2);
        assertEquals("/item[2]", item2Ptr.asPath());
    }

    // asPath: text node ต้องได้ /text()[n] ตามตำแหน่งสัมพัทธ์
    @Test
    public void testAsPath_textNode_returnsTextIndexPath() throws Throwable {
        Element parent = doc.createElement("p");
        Text t = doc.createTextNode("x");
        parent.appendChild(t);
        DOMNodePointer ptr = new DOMNodePointer(t, Locale.US);
        assertEquals("/text()[1]", ptr.asPath());
    }

    // asPath: processing-instruction ต้องได้ /processing-instruction('target')[n]
    @Test
    public void testAsPath_processingInstruction_returnsPIIndexPath() throws Throwable {
        Element parent = doc.createElement("p");
        ProcessingInstruction pi = doc.createProcessingInstruction("tgt", "d");
        parent.appendChild(pi);
        DOMNodePointer ptr = new DOMNodePointer(pi, Locale.US);
        assertEquals("/processing-instruction('tgt')[1]", ptr.asPath());
    }

    // hashCode ต้องตรงกับ node.hashCode() และ equals ต้องเปรียบเทียบตาม node ที่อ้างถึง
    @Test
    public void testHashCodeAndEquals_variousCases() throws Throwable {
        Element el1 = doc.createElement("foo");
        Element el2 = doc.createElement("bar");
        DOMNodePointer ptr1 = new DOMNodePointer(el1, Locale.US);
        DOMNodePointer ptr2 = new DOMNodePointer(el1, Locale.US);
        DOMNodePointer ptr3 = new DOMNodePointer(el2, Locale.US);
        assertEquals(el1.hashCode(), ptr1.hashCode());
        assertTrue(ptr1.equals(ptr1));
        assertTrue(ptr1.equals(ptr2));
        assertFalse(ptr1.equals(ptr3));
        assertFalse(ptr1.equals("not a pointer"));
    }

    // getPrefix(Node) static: เมื่อ DOM ไม่มี prefix แต่ nodeName มี ':' ต้องตัดส่วนหน้ามาเป็น prefix
    @Test
    public void testGetPrefix_static_colonFallback() throws Throwable {
        Element el = doc.createElement("ns:tag");
        assertEquals("ns", DOMNodePointer.getPrefix(el));
    }

    // getLocalName(Node) static: เมื่อ DOM ไม่มี localName แต่ nodeName มี ':' ต้องตัดส่วนหลัง ':'
    @Test
    public void testGetLocalName_static_colonFallback() throws Throwable {
        Element el = doc.createElement("ns:tag");
        assertEquals("tag", DOMNodePointer.getLocalName(el));
    }

    // getNamespaceURI(Node) static: เมื่อ node เป็น Document ต้องใช้ documentElement แทน
    @Test
    public void testGetNamespaceURI_static_documentNode_delegatesToDocumentElement() throws Throwable {
        Element root = doc.createElementNS("http://doc.example.com", "root");
        doc.appendChild(root);
        assertEquals("http://doc.example.com", DOMNodePointer.getNamespaceURI(doc));
    }

    // getValue: comment node ต้อง trim ช่องว่าง, element รวมค่าจาก text node ลูกทั้งหมด
    @Test
    public void testGetValue_variousCases() throws Throwable {
        Comment c = doc.createComment("  hello  ");
        assertEquals("hello", new DOMNodePointer(c, Locale.US).getValue());
        Element el = doc.createElement("p");
        el.appendChild(doc.createTextNode("a"));
        el.appendChild(doc.createTextNode("b"));
        assertEquals("ab", new DOMNodePointer(el, Locale.US).getValue());
    }

    // getPointerByID: พบ element (ผ่าน setIdAttribute) คืน DOMNodePointer, ไม่พบคืน NullPointer
    @Test
    public void testGetPointerByID_variousCases() throws Throwable {
        Element root = doc.createElement("root");
        doc.appendChild(root);
        root.setAttribute("id", "theId");
        root.setIdAttribute("id", true);
        DOMNodePointer ptr = new DOMNodePointer(doc, Locale.US);
        Pointer found = ptr.getPointerByID(null, "theId");
        assertTrue(found instanceof DOMNodePointer);
        assertSame(root, ((DOMNodePointer) found).getBaseValue());
        Pointer notFound = ptr.getPointerByID(null, "missing");
        assertTrue(notFound instanceof NullPointer);
    }

    // compareChildNodePointers: element ที่มาก่อนในลำดับลูกต้องได้ -1, สลับด้านต้องได้ 1
    @Test
    public void testCompareChildNodePointers_elementOrder() throws Throwable {
        Element parent = doc.createElement("p");
        Element c1 = doc.createElement("c1");
        Element c2 = doc.createElement("c2");
        parent.appendChild(c1);
        parent.appendChild(c2);
        DOMNodePointer parentPtr = new DOMNodePointer(parent, Locale.US);
        DOMNodePointer p1 = new DOMNodePointer(c1, Locale.US);
        DOMNodePointer p2 = new DOMNodePointer(c2, Locale.US);
        assertEquals(-1, parentPtr.compareChildNodePointers(p1, p2));
        assertEquals(1, parentPtr.compareChildNodePointers(p2, p1));
    }

    // compareChildNodePointers: node เดียวกันทั้งสองฝั่งต้องได้ 0
    @Test
    public void testCompareChildNodePointers_sameNode_returnsZero() throws Throwable {
        Element parent = doc.createElement("p");
        Element c1 = doc.createElement("c1");
        parent.appendChild(c1);
        DOMNodePointer parentPtr = new DOMNodePointer(parent, Locale.US);
        DOMNodePointer p1 = new DOMNodePointer(c1, Locale.US);
        DOMNodePointer p1b = new DOMNodePointer(c1, Locale.US);
        assertEquals(0, parentPtr.compareChildNodePointers(p1, p1b));
    }

    // compareChildNodePointers: attribute ต้องมาก่อน element เสมอไม่ว่าจะอยู่ด้านใด
    @Test
    public void testCompareChildNodePointers_attributeVsElement() throws Throwable {
        Element parent = doc.createElement("p");
        Element c1 = doc.createElement("c1");
        parent.appendChild(c1);
        Attr attr = doc.createAttribute("a1");
        DOMNodePointer parentPtr = new DOMNodePointer(parent, Locale.US);
        DOMNodePointer attrPtr = new DOMNodePointer(attr, Locale.US);
        DOMNodePointer elemPtr = new DOMNodePointer(c1, Locale.US);
        assertEquals(-1, parentPtr.compareChildNodePointers(attrPtr, elemPtr));
        assertEquals(1, parentPtr.compareChildNodePointers(elemPtr, attrPtr));
    }
}
