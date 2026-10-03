package org.apache.commons.jxpath.ri.model.dom;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodePointer;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class DOMAttributeIteratorClaudeTest {

    private Document doc;
    private Element root;
    private NodePointer rootPointer;

    @Before
    public void setUp() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        doc = db.newDocument();
        root = doc.createElement("root");
        doc.appendChild(root);
        root.setAttribute("attr1", "value1");
        root.setAttribute("attr2", "value2");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:ns1", "http://ns1-uri");
        root.setAttributeNS("http://ns1-uri", "ns1:attrB", "valueB");
        rootPointer = (NodePointer) JXPathContext.newContext(root).getPointer(".");
    }

    private Attr asAttr(NodePointer pointer) {
        Node node = (Node) pointer.getNode();
        return (Attr) node;
    }

    // constructor: ELEMENT_NODE + ชื่อเฉพาะที่มีอยู่จริง -> เพิ่ม attribute 1 ตัว
    @Test
    public void testConstructor_specificExistingName_addsOneAttribute() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        assertTrue(iter.setPosition(1));
        assertFalse(iter.setPosition(2));
    }

    // constructor: ELEMENT_NODE + ชื่อเฉพาะที่ไม่มีอยู่จริง -> ไม่เพิ่ม attribute
    @Test
    public void testConstructor_specificMissingName_addsNoAttribute() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("nope"));
        assertFalse(iter.setPosition(1));
    }

    // constructor: wildcard "*" -> นับเฉพาะ attribute ที่ไม่ใช่ xmlns และ prefix ตรงกัน (attr1, attr2)
    @Test
    public void testConstructor_wildcardName_countsOnlyMatchingUnprefixedAttrs() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("*"));
        assertTrue(iter.setPosition(1));
        assertTrue(iter.setPosition(2));
        assertFalse(iter.setPosition(3));
    }

    // constructor: node ไม่ใช่ ELEMENT_NODE (Document) + wildcard -> attributes ว่างเสมอ
    @Test
    public void testConstructor_nonElementNodeWildcard_emptyAttributes() throws Throwable {
        NodePointer docPointer = (NodePointer) JXPathContext.newContext(doc).getPointer(".");
        DOMAttributeIterator iter = new DOMAttributeIterator(docPointer, new QName("*"));
        assertFalse(iter.setPosition(1));
    }

    // constructor: node ไม่ใช่ ELEMENT_NODE + ชื่อเฉพาะ -> attributes ว่างเสมอ
    @Test
    public void testConstructor_nonElementNodeSpecificName_emptyAttributes() throws Throwable {
        NodePointer docPointer = (NodePointer) JXPathContext.newContext(doc).getPointer(".");
        DOMAttributeIterator iter = new DOMAttributeIterator(docPointer, new QName("attr1"));
        assertFalse(iter.setPosition(1));
    }

    // getAttribute: prefix ที่รู้จัก namespace -> ใช้ getAttributeNodeNS พบ attribute
    @Test
    public void testConstructor_knownPrefixedName_findsAttributeViaNamespace() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("ns1", "attrB"));
        assertTrue(iter.setPosition(1));
        assertEquals("valueB", asAttr(iter.getNodePointer()).getValue());
    }

    // getAttribute: prefix ที่ไม่รู้จัก (testNS null) -> fallback ไปใช้ getAttributeNode(localName)
    @Test
    public void testConstructor_unknownPrefixedName_fallsBackToLocalNameLookup() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("unknownpfx", "attr1"));
        assertTrue(iter.setPosition(1));
        assertEquals("value1", asAttr(iter.getNodePointer()).getValue());
    }

    // testAttr: prefix null และ localName=="xmlns" (default namespace decl) ถูกกรองออกจาก wildcard
    @Test
    public void testTestAttr_defaultXmlnsDeclaration_excludedFromWildcard() throws Throwable {
        Element child = doc.createElement("child");
        root.appendChild(child);
        child.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", "urn:test-default");
        child.setAttribute("attrA", "x");
        NodePointer childPointer = (NodePointer) JXPathContext.newContext(child).getPointer(".");
        DOMAttributeIterator iter = new DOMAttributeIterator(childPointer, new QName("*"));
        NodePointer np = iter.getNodePointer();
        assertEquals("attrA", asAttr(np).getName());
        assertTrue(iter.setPosition(1));
        assertFalse(iter.setPosition(2));
    }

    // getNodePointer: ไม่มี attribute เลย -> คืนค่า null
    @Test
    public void testGetNodePointer_emptyAttributes_returnsNull() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("nope"));
        assertNull(iter.getNodePointer());
    }

    // บั๊ก: getNodePointer() เรียก setPosition(1) สำเร็จ แต่ภายในรีเซ็ต position กลับเป็น 0
    // ตามสัญญา หลัง lazy-init ครั้งแรกสำเร็จ position ควรเป็น 1 (ตรงกับที่ setPosition(1) คืน true)
    @Test
    public void testGetNodePointer_positionZeroWithAttributes_initializesPositionToOne() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        assertEquals(0, iter.getPosition());
        NodePointer np = iter.getNodePointer();
        assertNotNull(np);
        assertEquals(1, iter.getPosition());
    }

    // getNodePointer: position=0 เริ่มต้น -> คืน attribute ตัวแรกที่ถูกต้องตามชื่อ/ค่า
    @Test
    public void testGetNodePointer_positionZero_returnsFirstMatchingAttribute() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        Attr attr = asAttr(iter.getNodePointer());
        assertEquals("attr1", attr.getName());
        assertEquals("value1", attr.getValue());
    }

    // getNodePointer: หลัง setPosition(1) ชัดเจน -> คืน attribute ตัวแรก (ไม่ผ่าน lazy init branch)
    @Test
    public void testGetNodePointer_afterExplicitSetPositionOne_returnsFirstAttribute() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("*"));
        assertTrue(iter.setPosition(1));
        NodePointer np = iter.getNodePointer();
        assertNotNull(np);
        assertEquals(1, iter.getPosition());
    }

    // getNodePointer: หลัง setPosition(2) -> index=1 -> คืน attribute ตัวที่สองของ wildcard
    @Test
    public void testGetNodePointer_afterExplicitSetPositionTwo_returnsSecondAttribute() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("*"));
        assertTrue(iter.setPosition(2));
        Attr attr = asAttr(iter.getNodePointer());
        assertTrue(attr.getName().equals("attr1") || attr.getName().equals("attr2"));
    }

    // getNodePointer: position ติดลบ + attributes ว่าง -> index ถูก clamp เป็น 0 แล้ว get(0) บน list ว่างต้อง throw
    @Test
    public void testGetNodePointer_negativePositionEmptyAttributes_throwsIndexOutOfBounds() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("nope"));
        iter.setPosition(-1);
        try {
            iter.getNodePointer();
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // getNodePointer: position เกินขนาด list ที่ไม่ว่าง -> get(index) ต้อง throw
    @Test
    public void testGetNodePointer_positionBeyondSize_throwsIndexOutOfBounds() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        iter.setPosition(5);
        try {
            iter.getNodePointer();
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // getPosition: ค่าเริ่มต้นก่อนเรียก setPosition หรือ getNodePointer ต้องเป็น 0
    @Test
    public void testGetPosition_initialState_returnsZero() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        assertEquals(0, iter.getPosition());
    }

    // getPosition: หลัง setPosition(1) ต้องสะท้อนค่า 1
    @Test
    public void testGetPosition_afterSetPositionOne_returnsOne() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        iter.setPosition(1);
        assertEquals(1, iter.getPosition());
    }

    // getPosition: แม้ setPosition คืน false (นอกขอบเขต) ค่า position ภายในก็ยังถูกตั้งตามที่ส่งเข้าไป
    @Test
    public void testGetPosition_afterInvalidSetPosition_stillReflectsGivenValue() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        iter.setPosition(0);
        assertEquals(0, iter.getPosition());
    }

    // setPosition: ขอบล่างที่ถูกต้อง (1) กับ list ที่มี 1 สมาชิก -> true
    @Test
    public void testSetPosition_lowerBoundValid_returnsTrue() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        assertTrue(iter.setPosition(1));
    }

    // setPosition: ขอบบนที่ถูกต้อง (เท่ากับขนาด list) -> true
    @Test
    public void testSetPosition_upperBoundValid_returnsTrue() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("*"));
        assertTrue(iter.setPosition(2));
    }

    // setPosition: 0 อยู่นอกขอบเขต [1, size] -> false
    @Test
    public void testSetPosition_zero_returnsFalse() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        assertFalse(iter.setPosition(0));
    }

    // setPosition: ค่าติดลบ -> false
    @Test
    public void testSetPosition_negative_returnsFalse() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("attr1"));
        assertFalse(iter.setPosition(-1));
    }

    // setPosition: เกินขนาด list (size+1) -> false
    @Test
    public void testSetPosition_beyondSize_returnsFalse() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("*"));
        assertFalse(iter.setPosition(3));
    }

    // setPosition: list ว่าง, position=1 ก็ต้อง false เพราะ 1 > size(0)
    @Test
    public void testSetPosition_emptyAttributes_returnsFalse() throws Throwable {
        DOMAttributeIterator iter = new DOMAttributeIterator(rootPointer, new QName("nope"));
        assertFalse(iter.setPosition(1));
    }
}
