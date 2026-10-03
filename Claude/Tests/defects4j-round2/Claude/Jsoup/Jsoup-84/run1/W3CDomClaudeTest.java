package org.jsoup.helper;

import org.jsoup.Jsoup;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class W3CDomClaudeTest {

    private W3CDom w3c;

    @Before
    public void setUp() throws Throwable {
        w3c = new W3CDom();
    }

    // Validate.notNull branch: null input must throw IllegalArgumentException
    @Test
    public void testFromJsoup_nullInput_throwsIllegalArgumentException() throws Throwable {
        try {
            w3c.fromJsoup(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // basic conversion: root element tag name copied
    @Test
    public void testFromJsoup_simpleDocument_createsRootElementWithTagName() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><head></head><body></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        assertNotNull(out.getDocumentElement());
        assertEquals("html", out.getDocumentElement().getTagName());
    }

    // convert(): StringUtil.isBlank(in.location()) true branch -> documentURI not set
    @Test
    public void testFromJsoup_blankLocation_documentURINotSet() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        assertNull(out.getDocumentURI());
    }

    // head(): TextNode branch copies whole text
    @Test
    public void testFromJsoup_textNode_copiesTextContent() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><p>Hello World</p></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        NodeList paragraphs = out.getElementsByTagName("p");
        assertEquals(1, paragraphs.getLength());
        assertEquals("Hello World", paragraphs.item(0).getTextContent());
    }

    // head(): Comment branch copies comment data
    @Test
    public void testFromJsoup_commentNode_copiesCommentData() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><!-- a comment --></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element body = (Element) out.getElementsByTagName("body").item(0);
        Node commentNode = body.getFirstChild();
        assertTrue(commentNode instanceof Comment);
        assertEquals(" a comment ", ((Comment) commentNode).getData());
    }

    // head(): DataNode branch (script content) copies raw data
    @Test
    public void testFromJsoup_dataNode_scriptContent_copiesRawData() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><script>var a = 1;</script></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element script = (Element) out.getElementsByTagName("script").item(0);
        assertEquals("var a = 1;", script.getTextContent());
    }

    // copyAttributes(): valid attribute key is copied as-is
    @Test
    public void testFromJsoup_validAttribute_copiedToElement() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><div class=\"box\"></div></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element div = (Element) out.getElementsByTagName("div").item(0);
        assertEquals("box", div.getAttribute("class"));
    }

    // copyAttributes(): invalid chars stripped from key, remaining key matches pattern -> copied
    @Test
    public void testFromJsoup_attributeWithInvalidChars_stripsInvalidCharsFromKey() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><div data@foo=\"bar\"></div></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element div = (Element) out.getElementsByTagName("div").item(0);
        assertEquals("bar", div.getAttribute("datafoo"));
    }

    // copyAttributes(): key starting with digit fails pattern match -> not copied
    @Test
    public void testFromJsoup_attributeStartingWithDigit_notCopied() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><div 1data=\"x\"></div></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element div = (Element) out.getElementsByTagName("div").item(0);
        assertFalse(div.hasAttribute("1data"));
    }

    // updateNamespaces(): xmlns="" key sets root namespace URI
    @Test
    public void testFromJsoup_xmlnsAttribute_setsNamespaceURI() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        assertEquals("http://www.w3.org/1999/xhtml", out.getDocumentElement().getNamespaceURI());
    }

    // updateNamespaces(): xmlns:prefix declared and used by a prefixed child tag
    @Test
    public void testFromJsoup_prefixedChildWithDeclaredNamespace_setsNamespaceURI() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse(
            "<html xmlns:foo=\"http://example.com/foo\"><body><foo:bar>hi</foo:bar></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element bar = (Element) out.getElementsByTagName("foo:bar").item(0);
        assertNotNull(bar);
        assertEquals("http://example.com/foo", bar.getNamespaceURI());
    }

    // Bug-catching test: a prefixed tag name with NO xmlns declaration must still convert
    // without throwing (buggy code calls createElementNS(null, "fb:like") -> DOMException NAMESPACE_ERR)
    @Test
    public void testFromJsoup_prefixedTagWithoutNamespaceDeclaration_doesNotThrow() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><fb:like>Test</fb:like></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        assertNotNull(out.getDocumentElement());
    }

    // head()/tail(): loop over multiple sibling children, order preserved
    @Test
    public void testFromJsoup_multipleSiblings_allChildrenCopiedInOrder() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><ul><li>A</li><li>B</li><li>C</li></ul></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        NodeList items = out.getElementsByTagName("li");
        assertEquals(3, items.getLength());
        assertEquals("A", items.item(0).getTextContent());
        assertEquals("C", items.item(2).getTextContent());
    }

    // loop zero iterations: element with no children produces zero child nodes
    @Test
    public void testFromJsoup_emptyBody_noChildrenCopied() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element body = (Element) out.getElementsByTagName("body").item(0);
        assertEquals(0, body.getChildNodes().getLength());
    }

    // tail(): undescend after nested element, subsequent sibling attaches to correct parent
    @Test
    public void testFromJsoup_nestedElements_parentChildStructurePreserved() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><div><span>inner</span></div><p>after</p></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element body = (Element) out.getElementsByTagName("body").item(0);
        assertEquals(2, body.getChildNodes().getLength());
        Element p = (Element) out.getElementsByTagName("p").item(0);
        assertEquals("after", p.getTextContent());
    }

    // updateNamespaces(): tag without colon -> prefix "" and no xmlns declared -> null namespace
    @Test
    public void testFromJsoup_plainTagNoNamespace_namespaceURIIsNull() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><div></div></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element div = (Element) out.getElementsByTagName("div").item(0);
        assertNull(div.getNamespaceURI());
    }

    // convert(): caller-provided w3c Document gets the converted root appended
    @Test
    public void testConvert_setsRootElementOnProvidedDocument() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><p>hi</p></body></html>");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document out = builder.newDocument();
        w3c.convert(jdoc, out);
        assertEquals("html", out.getDocumentElement().getTagName());
    }

    // convert(): null jsoup input dereferenced via in.location() -> NullPointerException
    @Test
    public void testConvert_nullInput_throwsNullPointerException() throws Throwable {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document out = builder.newDocument();
        try {
            w3c.convert(null, out);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // asString(): serializes w3c document into a string containing tag and text
    @Test
    public void testAsString_simpleDocument_containsTagAndText() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><p>Hello</p></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        String str = w3c.asString(out);
        assertTrue(str.contains("Hello"));
        assertTrue(str.contains("p"));
    }

    // asString(): XML serialization escapes raw ampersand back to entity
    @Test
    public void testAsString_ampersandInText_escapesAmpersand() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><p>A &amp; B</p></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        String str = w3c.asString(out);
        assertTrue(str.contains("&amp;"));
    }

    // copyAttributes(): multiple valid attributes all copied with correct values
    @Test
    public void testFromJsoup_multipleAttributes_allCopiedWithCorrectValues() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><a href=\"http://x.com\" id=\"link1\" title=\"t\">x</a></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element a = (Element) out.getElementsByTagName("a").item(0);
        assertEquals("http://x.com", a.getAttribute("href"));
        assertEquals("link1", a.getAttribute("id"));
        assertEquals("t", a.getAttribute("title"));
    }

    // alternate entry point parseBodyFragment() still converts correctly
    @Test
    public void testFromJsoup_parseBodyFragment_convertsSuccessfully() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parseBodyFragment("<div><p>frag</p></div>");
        Document out = w3c.fromJsoup(jdoc);
        NodeList ps = out.getElementsByTagName("p");
        assertEquals(1, ps.getLength());
        assertEquals("frag", ps.item(0).getTextContent());
    }

    // namespacesStack push/pop: namespace declared in a subtree does not leak to a later sibling
    @Test
    public void testFromJsoup_namespaceScopedToSubtree_siblingHasNullNamespace() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse(
            "<html><body><div xmlns=\"http://example.com/d\"><span>in</span></div><p>out</p></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element p = (Element) out.getElementsByTagName("p").item(0);
        assertNull(p.getNamespaceURI());
    }

    // attribute values keep decoded entities (parser decodes, DOM stores literal string)
    @Test
    public void testFromJsoup_attributeValueWithAmpersand_valuePreservedUnescaped() throws Throwable {
        org.jsoup.nodes.Document jdoc = Jsoup.parse("<html><body><a href=\"http://x.com?a=1&amp;b=2\">link</a></body></html>");
        Document out = w3c.fromJsoup(jdoc);
        Element a = (Element) out.getElementsByTagName("a").item(0);
        assertEquals("http://x.com?a=1&b=2", a.getAttribute("href"));
    }
}
