package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.DocumentType;
import org.jsoup.select.Elements;

public class HtmlTreeBuilderStateClaudeTest {

    // Initial: a doctype token must create a DocumentType node as the first child of the document
    @Test
    public void testInitial_doctypeToken_createsDocumentTypeNode() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html><html><head></head><body>Hi</body></html>");
        assertTrue(doc.childNode(0) instanceof DocumentType);
    }

    // Initial: leading whitespace before <html> is ignored, parsing still succeeds normally
    @Test
    public void testInitial_leadingWhitespace_ignoredBeforeHtml() throws Throwable {
        Document doc = Jsoup.parse("   <html><body>Content</body></html>");
        assertEquals("Content", doc.body().text());
    }

    // BeforeHtml: anythingElse path - raw text with no tags implicitly creates html/head/body
    @Test
    public void testBeforeHtml_rawTextNoTags_createsImplicitHtmlHeadBody() throws Throwable {
        Document doc = Jsoup.parse("Hello World");
        assertEquals("Hello World", doc.body().text());
    }

    // BeforeHead: a second <html> start tag is delegated to InBody (merge attrs) without transitioning
    @Test
    public void testBeforeHead_secondHtmlStartTag_mergesAttributesOntoRootWithoutTransition() throws Throwable {
        Document doc = Jsoup.parse("<html id='first'><html lang='en'><head></head><body>Hi</body></html>");
        Elements htmls = doc.select("html");
        assertEquals(1, htmls.size());
        assertEquals("first", htmls.first().attr("id"));
        assertEquals("en", htmls.first().attr("lang"));
    }

    // InHead: <title> content sets the document title
    @Test
    public void testInHead_titleTag_setsDocumentTitle() throws Throwable {
        Document doc = Jsoup.parse("<html><head><title>My Page</title></head><body></body></html>");
        assertEquals("My Page", doc.title());
    }

    // InHead: <style> switches tokeniser to rawtext, content preserved literally via data()
    @Test
    public void testInHead_styleTag_preservesRawTextContent() throws Throwable {
        Document doc = Jsoup.parse("<html><head><style>body{color:red}</style></head><body></body></html>");
        Element style = doc.select("style").first();
        assertNotNull(style);
        assertTrue(style.data().indexOf("color:red") >= 0);
    }

    // InHead: <script> switches tokeniser to ScriptData, content preserved literally via data()
    @Test
    public void testInHead_scriptTag_preservesScriptContent() throws Throwable {
        Document doc = Jsoup.parse("<html><head><script>var a=1;</script></head><body></body></html>");
        Element script = doc.select("script").first();
        assertNotNull(script);
        assertTrue(script.data().indexOf("var a=1;") >= 0);
    }

    // InHead: unknown start tag triggers anythingElse -> closes head, element ends up in body
    @Test
    public void testInHead_unknownStartTag_closesHeadAndMovesToBody() throws Throwable {
        Document doc = Jsoup.parse("<html><head><div>Hi</div></head></html>");
        Element div = doc.select("div").first();
        assertNotNull(div);
        assertEquals("body", div.parent().tagName());
        assertEquals("Hi", div.text());
    }

    // AfterHead: explicit <body> tag is inserted with its attributes, framesetOk set false
    @Test
    public void testAfterHead_explicitBodyTag_setsBodyAttributesAndText() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head><body id='b'>Hi</body></html>");
        assertEquals("b", doc.body().attr("id"));
        assertEquals("Hi", doc.body().text());
    }

    // AfterHead: anythingElse path - content without explicit <body> creates body implicitly
    @Test
    public void testAfterHead_contentWithoutBodyTag_createsBodyImplicitly() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head>World</html>");
        assertEquals("World", doc.body().text());
    }

    // AfterHead: a head-related tag (base) after head closed is pushed into head then removed
    @Test
    public void testAfterHead_baseTagAfterHeadClosed_insertedIntoHead() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head><base href='http://example.com/'><body>x</body></html>");
        Element base = doc.select("base").first();
        assertNotNull(base);
        assertEquals("http://example.com/", base.attr("href"));
        assertEquals("head", base.parent().tagName());
    }

    // InBody: encountering a second heading while a heading is open pops the first (siblings result)
    @Test
    public void testInBody_adjacentHeadings_closesPreviousHeading() throws Throwable {
        Document doc = Jsoup.parse("<body><h1>A<h2>B</h2></body>");
        assertEquals(2, doc.body().children().size());
        assertEquals("h1", doc.body().children().get(0).tagName());
        assertEquals("A", doc.body().children().get(0).text());
        assertEquals("h2", doc.body().children().get(1).tagName());
        assertEquals("B", doc.body().children().get(1).text());
    }

    // InBody: a <div> (p-closer) auto-closes an open <p> in button scope
    @Test
    public void testInBody_divClosesOpenParagraph() throws Throwable {
        Document doc = Jsoup.parse("<body><p>Hello<div>World</div></body>");
        assertEquals("Hello", doc.select("body > p").first().text());
        assertEquals("World", doc.select("body > div").first().text());
        assertEquals(0, doc.select("p > div").size());
    }

    // InBody: a nested <form> start tag while a form is already open is ignored entirely
    @Test
    public void testInBody_nestedFormStartTag_secondIgnored() throws Throwable {
        Document doc = Jsoup.parse("<body><form id='a'><form id='b'></form></body>");
        Elements forms = doc.select("form");
        assertEquals(1, forms.size());
        assertEquals("a", forms.first().attr("id"));
    }

    // InBody: consecutive <dt> tags auto-close the previous one, producing sibling elements
    @Test
    public void testInBody_consecutiveDtTags_autoClosesPrevious() throws Throwable {
        Document doc = Jsoup.parse("<body><dt>A<dt>B</body>");
        Elements dts = doc.select("dt");
        assertEquals(2, dts.size());
        assertEquals("A", dts.get(0).text());
        assertEquals("B", dts.get(1).text());
    }

    // InBody: <plaintext> permanently switches tokeniser mode, remaining content becomes literal text
    @Test
    public void testInBody_plaintext_treatsRemainingContentAsLiteralText() throws Throwable {
        Document doc = Jsoup.parse("<body><plaintext>Hello <b>World</b>");
        Element pt = doc.select("plaintext").first();
        assertNotNull(pt);
        assertTrue(pt.text().indexOf("<b>World</b>") >= 0);
        assertEquals(0, doc.select("b").size());
    }

    // InBody: a nested <button> while one is open closes the first and reprocesses as sibling
    @Test
    public void testInBody_reentrantButton_closesFirstButton() throws Throwable {
        Document doc = Jsoup.parse("<body><button>First<button>Second</button></body>");
        Elements buttons = doc.select("button");
        assertEquals(2, buttons.size());
        assertEquals("First", buttons.get(0).text());
        assertEquals("Second", buttons.get(1).text());
    }

    // InBody: a stray </div> end tag with no matching open div is ignored without crashing
    @Test
    public void testInBody_strayEndTagDiv_ignoredWithoutCrash() throws Throwable {
        Document doc = Jsoup.parse("<body></div>Remaining</body>");
        assertEquals(0, doc.select("div").size());
        assertEquals("Remaining", doc.body().text());
    }

    // InBody: <image> is renamed to <img> per jsoup-special handling
    @Test
    public void testInBody_imageTagRenamedToImg() throws Throwable {
        Document doc = Jsoup.parse("<body><image src='pic.png'></body>");
        assertEquals(0, doc.select("image").size());
        Element img = doc.select("img").first();
        assertNotNull(img);
        assertEquals("pic.png", img.attr("src"));
    }

    // InBody: <isindex> builds the classic form/hr/label/input structure
    @Test
    public void testInBody_isindexTag_buildsFormStructure() throws Throwable {
        Document doc = Jsoup.parse("<body><isindex></body>");
        assertEquals(1, doc.select("form").size());
        assertEquals(2, doc.select("hr").size());
        assertEquals(1, doc.select("label").size());
        assertEquals("isindex", doc.select("input").attr("name"));
    }

    // InBody: <textarea> uses RCDATA, embedded markup is preserved as literal text, not parsed
    @Test
    public void testInBody_textareaContent_preservedAsRcData() throws Throwable {
        Document doc = Jsoup.parse("<body><textarea>Hi <b>there</b></textarea></body>");
        Element ta = doc.select("textarea").first();
        assertNotNull(ta);
        assertTrue(ta.text().indexOf("<b>there</b>") >= 0);
        assertEquals(0, doc.select("b").size());
    }

    // InBody: <xmp> uses RAWTEXT, embedded markup is preserved as literal text, not parsed
    @Test
    public void testInBody_xmpContent_preservedAsRawText() throws Throwable {
        Document doc = Jsoup.parse("<body><xmp>Raw <i>text</i></xmp></body>");
        Element xmp = doc.select("xmp").first();
        assertNotNull(xmp);
        assertTrue(xmp.text().indexOf("<i>text</i>") >= 0);
        assertEquals(0, doc.select("i").size());
    }

    // InBody: InBodyStartDrop tags like <td> appearing outside a table are dropped entirely
    @Test
    public void testInBody_strayTableCellStartTag_dropped() throws Throwable {
        Document doc = Jsoup.parse("<body><td>Text</td></body>");
        assertEquals(0, doc.select("td").size());
        assertEquals("Text", doc.body().text());
    }

    // InBody: end tag <br> is special-cased to act like a start tag, inserting a br element
    @Test
    public void testInBody_strayEndTagBr_insertsBrElement() throws Throwable {
        Document doc = Jsoup.parse("<body></br></body>");
        assertEquals(1, doc.select("br").size());
    }

    // InBody: a nested <option> start tag auto-closes the previous option as a sibling
    @Test
    public void testInBody_nestedOptionStartTag_autoClosesPrevious() throws Throwable {
        Document doc = Jsoup.parse("<body><option>A<option>B</body>");
        Elements options = doc.select("option");
        assertEquals(2, options.size());
        assertEquals("A", options.get(0).text());
        assertEquals("B", options.get(1).text());
    }

    // InBody end tag (applet/marquee/object group): closing </object> must properly pop it
    // even when an element literally named "name" wraps it (guards against the inScope("name") typo bug)
    @Test
    public void testInBody_objectEndTagWithNameSiblingElement_closesObjectProperly() throws Throwable {
        Document doc = Jsoup.parse("<body><name><object>A</object>B</name></body>");
        Element object = doc.select("object").first();
        assertNotNull(object);
        assertEquals("A", object.text());
    }



    // InTable: a lone <col> auto-creates a <colgroup> wrapper
    @Test
    public void testInTable_colWithoutColgroup_autoCreatesColgroup() throws Throwable {
        Document doc = Jsoup.parse("<body><table><col></table></body>");
        assertEquals(1, doc.select("colgroup").size());
        assertEquals(1, doc.select("colgroup > col").size());
    }

    // InTable: <tr> without a tbody auto-creates the tbody wrapper
    @Test
    public void testInTable_cellWithoutTbody_autoCreatesTbody() throws Throwable {
        Document doc = Jsoup.parse("<body><table><tr><td>X</td></tr></table></body>");
        assertEquals(1, doc.select("tbody").size());
        assertEquals("X", doc.select("td").first().text());
    }



    // InTable: a hidden <input> is inserted directly inside the table (no foster parenting)
    @Test
    public void testInTable_hiddenInput_staysInsideTable() throws Throwable {
        Document doc = Jsoup.parse("<body><table><input type='hidden' name='h'></table></body>");
        Element input = doc.select("input").first();
        assertNotNull(input);
        assertEquals("table", input.parent().tagName());
    }

    // InTable: a non-hidden <input> is foster-parented outside the table
    @Test
    public void testInTable_nonHiddenInput_fosterParentedOutsideTable() throws Throwable {
        Document doc = Jsoup.parse("<body><table><input type='text' name='t'></table></body>");
        Element input = doc.select("input").first();
        assertNotNull(input);
        assertFalse("table".equals(input.parent().tagName()));
    }

    // InColumnGroup: multiple <col> tags inside an explicit colgroup are all inserted
    @Test
    public void testInColumnGroup_multipleColTags_bothInsertedUnderColgroup() throws Throwable {
        Document doc = Jsoup.parse("<body><table><colgroup><col><col></colgroup></table></body>");
        assertEquals(2, doc.select("colgroup > col").size());
    }

    // InCell: a new <td> without closing the previous one implicitly closes the prior cell
    @Test
    public void testInCell_implicitCloseOnNextCellStartTag() throws Throwable {
        Document doc = Jsoup.parse("<body><table><tr><td>A<td>B</tr></table></body>");
        Elements tds = doc.select("td");
        assertEquals(2, tds.size());
        assertEquals("A", tds.get(0).text());
        assertEquals("B", tds.get(1).text());
    }

    // InSelect: multiple <option> elements inside select are preserved as siblings
    @Test
    public void testInSelect_multipleOptions_bothPreservedAsSiblings() throws Throwable {
        Document doc = Jsoup.parse("<body><select><option>A</option><option>B</option></select></body>");
        Elements options = doc.select("select option");
        assertEquals(2, options.size());
        assertEquals("A", options.get(0).text());
        assertEquals("B", options.get(1).text());
    }

    // InSelect: an <input> inside <select> closes the select and is inserted outside it
    @Test
    public void testInSelect_inputStartTag_closesSelectAndInsertsInput() throws Throwable {
        Document doc = Jsoup.parse("<body><select><input name='x'></select></body>");
        assertEquals(1, doc.select("select").size());
        Element input = doc.select("input").first();
        assertNotNull(input);
        assertFalse("select".equals(input.parent().tagName()));
    }

    // InFrameset: a <frame> start tag is inserted as an empty element under frameset
    @Test
    public void testInFrameset_frameStartTag_insertedUnderFrameset() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head><frameset><frame></frameset></html>");
        assertEquals(1, doc.select("frameset").size());
        Element frame = doc.select("frame").first();
        assertNotNull(frame);
        assertEquals("frameset", frame.parent().tagName());
    }

    // parseBodyFragment entry point: simple fragment content is parsed under a synthetic body
    @Test
    public void testParseBodyFragment_simpleParagraph_parsedUnderFragmentBody() throws Throwable {
        Document frag = Jsoup.parseBodyFragment("<p>Hello</p>");
        assertEquals("Hello", frag.body().select("p").first().text());
    }
}
