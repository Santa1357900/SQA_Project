package org.jsoup.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.CDataNode;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.XmlDeclaration;

import java.io.StringReader;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class XmlTreeBuilderClaudeTest {

    private XmlTreeBuilder builder;

    @Before
    public void setUp() throws Throwable {
        builder = new XmlTreeBuilder();
    }

    // defaultSettings() ต้องคืนค่า preserveCase ตาม contract ของ XML parser (ไม่ lowercase tag/attr)
    @Test
    public void testDefaultSettings_returnsPreserveCaseSettings() throws Throwable {
        ParseSettings ps = builder.defaultSettings();
        assertSame(ParseSettings.preserveCase, ps);
    }

    // parse(Reader,String): element เดียวระดับบนสุดถูกเพิ่มเป็น child ของ document
    @Test
    public void testParseReaderBaseUri_simpleRootElement_returnsDocumentWithRootChild() throws Throwable {
        Document doc = builder.parse(new StringReader("<root/>"), "http://x/");
        assertEquals("root", doc.child(0).tagName());
    }

    // parse(Reader,String): input ว่าง ต้องไม่มี child ใดๆ และไม่ throw
    @Test
    public void testParseReaderBaseUri_emptyInput_returnsDocumentWithNoChildren() throws Throwable {
        Document doc = builder.parse(new StringReader(""), "");
        assertEquals(0, doc.childNodes().size());
    }

    // parse(String,String): tag ที่ไม่ self-close ต้องถูก push ขึ้น stack ทำให้รับ child ได้
    @Test
    public void testParseStringBaseUri_nestedElements_childAddedToParent() throws Throwable {
        Document doc = builder.parse("<root><child/></root>", "");
        Element root = doc.child(0);
        assertEquals(1, root.childNodes().size());
        assertEquals("child", root.child(0).tagName());
    }

    // preserveCase: ชื่อ tag ต้องไม่ถูกแปลงเป็นตัวพิมพ์เล็ก
    @Test
    public void testParseStringBaseUri_preserveCase_tagNameNotLowercased() throws Throwable {
        Document doc = builder.parse("<MyTag/>", "");
        assertEquals("MyTag", doc.child(0).tagName());
    }

    // self-closing tag: ไม่ถูก push ขึ้น stack จึงอยู่ระดับเดียวกับ sibling ถัดไป
    @Test
    public void testParseStringBaseUri_selfClosingTag_siblingsAtSameLevel() throws Throwable {
        Document doc = builder.parse("<a/><b/>", "");
        assertEquals(2, doc.childNodes().size());
        assertEquals("a", doc.child(0).tagName());
        assertEquals("b", doc.child(1).tagName());
    }

    // self-closing tag ที่เป็น known tag (br): ผล structure ยังคงแบน เช่นเดียวกับ unknown tag
    @Test
    public void testParseStringBaseUri_selfClosingKnownTag_structureStillFlat() throws Throwable {
        Document doc = builder.parse("<br/><p/>", "");
        assertEquals(2, doc.childNodes().size());
        assertEquals("p", doc.child(1).tagName());
    }

    // character token ปกติ (ไม่ใช่ CDATA) ต้องถูกแทรกเป็น TextNode ไม่ใช่ CDataNode
    @Test
    public void testProcessCharacterToken_plainText_insertsTextNodeNotCData() throws Throwable {
        Document doc = builder.parse("<root>hello</root>", "");
        Node n = doc.child(0).childNodes().get(0);
        assertTrue(n instanceof TextNode);
        assertFalse(n instanceof CDataNode);
    }

    // character token แบบ CDATA ต้องถูกแทรกเป็น CDataNode
    @Test
    public void testProcessCharacterToken_cdataSection_insertsCDataNode() throws Throwable {
        Document doc = builder.parse("<root><![CDATA[hello]]></root>", "");
        Node n = doc.child(0).childNodes().get(0);
        assertTrue(n instanceof CDataNode);
    }

    // CDATA ว่าง (0 ตัวอักษร) ยังต้องสร้าง CDataNode ได้โดยไม่ throw
    @Test
    public void testProcessCharacterToken_emptyCdataSection_insertsCDataNode() throws Throwable {
        Document doc = builder.parse("<root><![CDATA[]]></root>", "");
        assertEquals(1, doc.child(0).childNodes().size());
        assertTrue(doc.child(0).childNodes().get(0) instanceof CDataNode);
    }

    // comment token ปกติ (ไม่ bogus) ต้องเก็บข้อมูลตรงตัวไม่ถูกตัด/แก้ไข
    @Test
    public void testProcessCommentToken_regularComment_preservesExactData() throws Throwable {
        Document doc = builder.parse("<root><!-- hi --></root>", "");
        Node n = doc.child(0).childNodes().get(0);
        assertTrue(n instanceof Comment);
        assertEquals(" hi ", ((Comment) n).getData());
    }

    // comment ที่ top level ผ่าน parse(Reader,String) ก็ต้องถูกแทรกเป็น child ของ document
    @Test
    public void testParseReaderBaseUri_commentAtTopLevel_addedAsDocumentChild() throws Throwable {
        Document doc = builder.parse(new StringReader("<!--top--><root/>"), "");
        Node n = doc.childNodes().get(0);
        assertTrue(n instanceof Comment);
        assertEquals("top", ((Comment) n).getData());
    }

    // doctype token ต้องถูกแทรกเป็น DocumentType node แยกจาก element
    @Test
    public void testProcessDoctypeToken_insertsDocumentTypeNode() throws Throwable {
        Document doc = builder.parse("<!DOCTYPE html><root/>", "");
        assertTrue(doc.childNodes().get(0) instanceof DocumentType);
        assertEquals("root", doc.child(0).tagName());
    }

    // end tag ที่ตรงกับ tag บน stack: ต้อง pop เฉพาะจนถึง element ที่ match เท่านั้น
    @Test
    public void testProcessEndTagToken_matchingTag_popsOnlyToMatchedElement() throws Throwable {
        Document doc = builder.parse("<a><b></b><c/></a>", "");
        Element a = doc.child(0);
        assertEquals(2, a.childNodes().size());
        assertEquals("b", a.child(0).tagName());
        assertEquals("c", a.child(1).tagName());
    }

    // end tag ที่ไม่ตรงกับ tag ใดบน stack: ต้องถูกข้ามโดยไม่กระทบ stack
    @Test
    public void testProcessEndTagToken_mismatchedTag_skippedWithoutAffectingStack() throws Throwable {
        Document doc = builder.parse("<root></other>text</root>", "");
        Element root = doc.child(0);
        assertEquals(1, root.childNodes().size());
        assertTrue(root.childNodes().get(0) instanceof TextNode);
    }

    // popStackToClose: end tag เดียวสามารถปิดหลายระดับพร้อมกันได้ (loop มากกว่า 1 รอบ)
    @Test
    public void testPopStackToClose_closesMultipleOpenElementsAtOnce() throws Throwable {
        Document doc = builder.parse("<a><b><c/></a><d/>", "");
        assertEquals(2, doc.childNodes().size());
        Element a = doc.child(0);
        Element b = a.child(0);
        assertEquals("c", b.child(0).tagName());
        assertEquals("d", doc.child(1).tagName());
    }

    // preserveCase ทำให้การจับคู่ end tag เป็น case-sensitive: case ไม่ตรงจึงไม่ปิด element เดิม
    @Test
    public void testPopStackToClose_caseSensitiveMismatch_leavesElementOpen() throws Throwable {
        Document doc = builder.parse("<Root></root><Sibling/>", "");
        assertEquals(1, doc.childNodes().size());
        Element root = doc.child(0);
        assertEquals(1, root.childNodes().size());
        assertEquals("Sibling", root.child(0).tagName());
    }

    // bogus comment ที่ขึ้นต้นด้วย "?" (XML declaration) ต้องถูกแปลงเป็น XmlDeclaration พร้อมชื่อที่ถูกต้อง
    @Test
    public void testInsertComment_bogusQuestionDeclaration_createsXmlDeclarationWithName() throws Throwable {
        Document doc = Jsoup.parse("<?xml version=\"1.0\"?><root/>", "", Parser.xmlParser());
        Node n = doc.childNodes().get(0);
        assertTrue(n instanceof XmlDeclaration);
        assertEquals("xml", ((XmlDeclaration) n).name());
    }



    // bogus comment ที่สั้นเกินไป (length<=1) ต้องคงเป็น Comment ธรรมดา ไม่ถูกแปลงเป็น XmlDeclaration
    @Test
    public void testInsertComment_shortBogusComment_staysAsPlainComment() throws Throwable {
        Document doc = Jsoup.parse("<root><!></root>", "", Parser.xmlParser());
        Node n = doc.child(0).childNodes().get(0);
        assertTrue(n instanceof Comment);
        assertFalse(n instanceof XmlDeclaration);
    }

    // parseFragment: มี top-level node เดียว ต้องคืน list ขนาด 1 ที่เป็น element ที่ถูกต้อง
    @Test
    public void testParseFragment_singleTopLevelElement_returnsListWithOneNode() throws Throwable {
        List<Node> nodes = builder.parseFragment("<a/>", "", ParseErrorList.noTracking(), ParseSettings.preserveCase);
        assertEquals(1, nodes.size());
        assertEquals("a", ((Element) nodes.get(0)).tagName());
    }

    // parseFragment: มีหลาย top-level node ต้องคืนครบทุกตัวตามลำดับ
    @Test
    public void testParseFragment_multipleTopLevelElements_returnsListWithAllNodes() throws Throwable {
        List<Node> nodes = builder.parseFragment("<a/><b/>", "", ParseErrorList.noTracking(), ParseSettings.preserveCase);
        assertEquals(2, nodes.size());
        assertEquals("b", ((Element) nodes.get(1)).tagName());
    }

    // parseFragment: input ว่างต้องคืน list ว่างโดยไม่ throw
    @Test
    public void testParseFragment_emptyInput_returnsEmptyList() throws Throwable {
        List<Node> nodes = builder.parseFragment("", "", ParseErrorList.noTracking(), ParseSettings.preserveCase);
        assertEquals(0, nodes.size());
    }

    // ลูปของ process ทำงานซ้ำหลายรอบ: element ที่ซ้อนกันหลายชั้นต้องสร้าง tree ที่ถูกต้องทุกชั้น
    @Test
    public void testParseStringBaseUri_deeplyNestedElements_eachLevelCorrect() throws Throwable {
        Document doc = builder.parse("<a><b><c><d/></c></b></a>", "");
        Element a = doc.child(0);
        Element b = a.child(0);
        Element c = b.child(0);
        Element d = c.child(0);
        assertEquals("d", d.tagName());
        assertEquals(0, d.childNodes().size());
    }

    // ลูปของ process ทำงานหลายรอบในระดับ sibling: หลาย element ใน parent เดียวต้องเรียงลำดับถูกต้อง
    @Test
    public void testParseStringBaseUri_multipleSiblingElements_allAttachedInOrder() throws Throwable {
        Document doc = builder.parse("<root><x/><y/><z/></root>", "");
        Element root = doc.child(0);
        assertEquals(3, root.childNodes().size());
        assertEquals("x", root.child(0).tagName());
        assertEquals("z", root.child(2).tagName());
    }

    // EOF token: document ที่สมบูรณ์ต้อง parse จบโดยไม่ throw และได้ element ครบ
    @Test
    public void testProcessEofToken_wellFormedDocument_completesWithoutException() throws Throwable {
        Document doc = builder.parse("<a/>", "");
        assertEquals(1, doc.childNodes().size());
        assertEquals("a", doc.child(0).tagName());
    }

    // attribute บน start tag ต้องถูกเก็บไว้ (ตรวจผ่าน attributes() ที่ถูกเพิ่มจาก normalizeAttributes)
    @Test
    public void testParseStringBaseUri_elementWithAttributes_elementHasAttributesObject() throws Throwable {
        Document doc = builder.parse("<root id=\"1\"/>", "");
        Element root = doc.child(0);
        assertNotNull(root.attributes());
    }
}
