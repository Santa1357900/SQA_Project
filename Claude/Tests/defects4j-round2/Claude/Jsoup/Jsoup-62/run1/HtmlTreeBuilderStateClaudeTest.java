package org.jsoup.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlTreeBuilderStateClaudeTest {

    // Initial: whitespace before <html> is ignored (state stays Initial)
    @Test
    public void testInitial_leadingWhitespace_ignoredBeforeHtml() throws Throwable {
        Document doc = Jsoup.parse("   \n  <html><head></head><body>content</body></html>");
        assertEquals("content", doc.body().text());
    }

    // Initial: a doctype token creates a DocumentType node as the first document child
    @Test
    public void testInitial_doctypePresent_createsDocumentTypeNode() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html><html><head></head><body>hi</body></html>");
        assertTrue(doc.childNode(0) instanceof DocumentType);
    }

    // Initial: a standard html5 doctype does not force quirks mode
    @Test
    public void testInitial_doctypeStandard_notQuirksMode() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html><html><head></head><body>hi</body></html>");
        assertFalse(doc.quirksMode() == Document.QuirksMode.quirks);
    }

    // Initial: non-doctype/non-comment token triggers transition to BeforeHtml and reprocess
    @Test
    public void testInitial_bareTextReprocessed_wrappedInHtmlBodyStructure() throws Throwable {
        Document doc = Jsoup.parse("hello world");
        assertEquals("hello world", doc.body().text());
    }

    // BeforeHtml/BeforeHead: a doctype token appearing after <html> is a parse error and is ignored
    @Test
    public void testBeforeHtml_doctypeAfterHtml_ignored() throws Throwable {
        Document doc = Jsoup.parse("<html><!DOCTYPE html><body>hi</body></html>");
        boolean hasDoctype = false;
        for (int i = 0; i < doc.childNodeSize(); i++) {
            if (doc.childNode(i) instanceof DocumentType) hasDoctype = true;
        }
        assertFalse(hasDoctype);
    }

    // Initial: a comment appearing before <html> is inserted as a document-level child node
    @Test
    public void testBeforeHtml_commentBeforeHtml_insertedAsDocumentChild() throws Throwable {
        Document doc = Jsoup.parse("<!--c--><html><body>hi</body></html>");
        Node first = doc.childNode(0);
        assertTrue(first instanceof Comment);
        assertEquals("c", ((Comment) first).getData());
    }

    // BeforeHtml: end tag not in {head,body,html,br} list is a parse error and ignored
    @Test
    public void testBeforeHtml_unknownEndTag_ignoredDocStillValid() throws Throwable {
        Document doc = Jsoup.parse("</div><html><body>hi</body></html>");
        assertEquals("hi", doc.body().text());
    }

    // BeforeHead: head/title elements are created and the document title is set
    @Test
    public void testBeforeHead_titleInsideHead_setsDocumentTitle() throws Throwable {
        Document doc = Jsoup.parse("<html><head><title>My Title</title></head><body>Body</body></html>");
        assertEquals("My Title", doc.title());
        assertEquals("Body", doc.body().text());
    }

    // BeforeHead: a second html start tag before head merges its attributes onto the root html element
    @Test
    public void testBeforeHead_duplicateHtmlStartTag_mergesAttributesOntoRoot() throws Throwable {
        Document doc = Jsoup.parse("<html id=foo><html class=bar><head></head><body>hi</body></html>");
        assertEquals("foo", doc.child(0).attr("id"));
        assertEquals("bar", doc.child(0).attr("class"));
    }

    // InHead: script content is parsed as raw text, not as nested HTML markup
    @Test
    public void testInHead_scriptContent_treatedAsRawText() throws Throwable {
        Document doc = Jsoup.parse("<html><head><script>var x = \"<div>\";</script></head><body></body></html>");
        Element script = doc.select("script").first();
        assertEquals("var x = \"<div>\";", script.data());
    }

    // InHead: style content is parsed as raw text, not as nested HTML markup
    @Test
    public void testInHead_styleContent_treatedAsRawText() throws Throwable {
        Document doc = Jsoup.parse("<html><head><style>body { color: red; }</style></head><body></body></html>");
        Element style = doc.select("style").first();
        assertEquals("body { color: red; }", style.data());
    }

    // InHead: meta start tag is inserted as an empty element inside head
    @Test
    public void testInHead_metaElement_insertedIntoHead() throws Throwable {
        Document doc = Jsoup.parse("<html><head><meta charset=utf-8></head><body>hi</body></html>");
        assertEquals(1, doc.head().select("meta").size());
    }

    // InHeadNoscript: end tag noscript pops the element and returns processing to InHead
    @Test
    public void testInHeadNoscript_endTag_returnsToInHead() throws Throwable {
        Document doc = Jsoup.parse("<html><head><noscript></noscript><title>T</title></head><body>hi</body></html>");
        assertEquals("T", doc.title());
        assertEquals(1, doc.select("noscript").size());
    }

    // AfterHead: a frameset start tag transitions the tree builder into frameset mode
    @Test
    public void testAfterHead_framesetStartTag_transitionsToInFrameset() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head><frameset><frame src=\"a.html\"></frameset></html>");
        assertEquals(1, doc.select("frameset").size());
        assertEquals(1, doc.select("frame").size());
    }

    // AfterHead: a meta tag appearing after head is closed is re-inserted into the head element
    @Test
    public void testAfterHead_metaAfterHeadClosed_reinsertedIntoHead() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head><meta charset=utf-8><body>hi</body></html>");
        assertEquals(1, doc.head().select("meta").size());
        assertEquals(0, doc.body().select("meta").size());
    }

    // InBody: a start tag with an active formatting "a" element closes the first anchor and starts a sibling
    @Test
    public void testInBody_duplicateAnchor_closesFirstCreatesSibling() throws Throwable {
        Document doc = Jsoup.parse("<body><a href=\"1\">one<a href=\"2\">two</a></body>");
        Elements anchors = doc.select("a");
        assertEquals(2, anchors.size());
        assertEquals("one", anchors.first().text());
        assertEquals("two", anchors.last().text());
    }

    // InBody: br is an empty-formatter start tag, inserted without becoming a container for later content
    @Test
    public void testInBody_brEmptyElement_hasNoChildren() throws Throwable {
        Document doc = Jsoup.parse("<body><br></body>");
        Elements brs = doc.select("br");
        assertEquals(1, brs.size());
        assertEquals(0, brs.first().children().size());
    }

    // InBody: a p start tag closes an already open p (button scope) before inserting the new one
    @Test
    public void testInBody_nestedParagraph_autoClosesPrevious() throws Throwable {
        Document doc = Jsoup.parse("<body><p>1<p>2</body>");
        Elements ps = doc.select("p");
        assertEquals(2, ps.size());
        assertEquals("1", ps.first().text());
        assertEquals("2", ps.last().text());
    }

    // InBody: an li start tag closes a previous open li sibling
    @Test
    public void testInBody_listItems_autoCloseSiblings() throws Throwable {
        Document doc = Jsoup.parse("<body><ul><li>1<li>2</ul></body>");
        Elements lis = doc.select("li");
        assertEquals(2, lis.size());
        assertEquals("1", lis.first().text());
        assertEquals("2", lis.last().text());
    }

    // InBody: starting a heading while current element is already a heading pops it first (siblings, not nested)
    @Test
    public void testInBody_headingInsideHeading_popsPrevious() throws Throwable {
        Document doc = Jsoup.parse("<body><h1>hello<h2>world</h2></body>");
        assertEquals("hello", doc.select("h1").text());
        assertEquals("world", doc.select("h2").text());
        assertEquals(2, doc.body().children().size());
    }

    // InBody: a form start tag while a form is already open is ignored (only one form element open at a time)
    @Test
    public void testInBody_nestedForm_secondIgnored() throws Throwable {
        Document doc = Jsoup.parse("<body><form><form></form></form></body>");
        assertEquals(1, doc.select("form").size());
    }

    // InBody: dd/dt auto-closes a sibling dd/dt that is still open
    @Test
    public void testInBody_definitionListTerms_autoCloseSiblings() throws Throwable {
        Document doc = Jsoup.parse("<body><dl><dt>term<dd>def</dl></body>");
        assertEquals("term", doc.select("dt").text());
        assertEquals("def", doc.select("dd").text());
        assertEquals(2, doc.select("dl").first().children().size());
    }

    // InBody: a button start tag while a button is already in scope closes the first, then inserts the new one as sibling
    @Test
    public void testInBody_nestedButton_closesFirstButton() throws Throwable {
        Document doc = Jsoup.parse("<body><button><button>x</button></button></body>");
        Elements buttons = doc.select("button");
        assertEquals(2, buttons.size());
        assertEquals("", buttons.first().text());
        assertEquals("x", buttons.last().text());
    }

    // InTable/InTableText: stray non-whitespace text inside a table is foster-parented out of the table
    @Test
    public void testInBody_tableWithStrayText_fosterParented() throws Throwable {
        Document doc = Jsoup.parse("<table>stray<tr><td>cell</td></tr></table>");
        Element table = doc.select("table").first();
        assertEquals("cell", doc.select("td").text());
        assertEquals("", table.ownText());
        assertTrue(doc.body().text().contains("stray"));
    }

    // InTable: a tr start tag without an existing tbody causes one to be implicitly inserted
    @Test
    public void testInBody_tableRowWithoutTbody_autoInsertsTbody() throws Throwable {
        Document doc = Jsoup.parse("<table><tr><td>1</td></tr></table>");
        assertEquals(1, doc.select("tbody").size());
        assertEquals("1", doc.select("td").text());
    }

    // InRow: both td and th cells are inserted correctly within the same row
    @Test
    public void testInBody_tableCells_thAndTdBothPresent() throws Throwable {
        Document doc = Jsoup.parse("<table><tr><td>A</td><th>B</th></tr></table>");
        assertEquals("A", doc.select("td").text());
        assertEquals("B", doc.select("th").text());
    }

    // InCaption: a tr start tag implicitly closes an open caption element
    @Test
    public void testInBody_captionClosedImplicitlyByTableRow() throws Throwable {
        Document doc = Jsoup.parse("<table><caption>Cap<tr><td>1</td></tr></table>");
        Element caption = doc.select("caption").first();
        assertEquals("Cap", caption.text());
        assertEquals(0, caption.children().size());
    }

    // InColumnGroup: col start tags are inserted as empty elements inside colgroup
    @Test
    public void testInBody_colgroupWithMultipleCols() throws Throwable {
        Document doc = Jsoup.parse("<table><colgroup><col><col></colgroup><tr><td>1</td></tr></table>");
        assertEquals(2, doc.select("col").size());
        assertEquals("1", doc.select("td").text());
    }

    // InSelect: multiple option start tags are each inserted as siblings under select
    @Test
    public void testInBody_selectWithOptions_bothPresent() throws Throwable {
        Document doc = Jsoup.parse("<select><option>1</option><option>2</option></select>");
        Elements options = doc.select("option");
        assertEquals(2, options.size());
        assertEquals("1", options.first().text());
        assertEquals("2", options.last().text());
    }

    // Bug check: </object> end tag must close a nested unknown element that is in scope
    // (the scope check must use the actual end tag name, not a hard-coded literal)
    @Test
    public void testInBody_objectEndTagWithNestedUnknownElementInScope_closesBothElements() throws Throwable {
        Document doc = Jsoup.parse("<body><object><name>text</object>MORE</body>");
        Element nameEl = doc.select("name").first();
        assertEquals("text", nameEl.text());
    }

    // Bug check (marquee variant): </marquee> end tag must close a nested unknown element that is in scope
    @Test
    public void testInBody_marqueeEndTagWithNestedUnknownElementInScope_closesBothElements() throws Throwable {
        Document doc = Jsoup.parse("<body><marquee><xcustom>data</marquee>TAIL</body>");
        Element customEl = doc.select("xcustom").first();
        assertEquals("data", customEl.text());
    }

    // AfterBody: trailing content after </body> is an error and is reprocessed by InBody, merging into body
    @Test
    public void testAfterBody_trailingTextAfterBodyClose_mergedIntoBody() throws Throwable {
        Document doc = Jsoup.parse("<html><body>Hi</body>World</html>");
        assertTrue(doc.body().text().contains("Hi"));
        assertTrue(doc.body().text().contains("World"));
    }

    // InFrameset: nested frameset and frame elements are all preserved in the tree
    @Test
    public void testInFrameset_nestedFramesetAndFrame_bothPresent() throws Throwable {
        Document doc = Jsoup.parse("<html><frameset><frameset><frame src='a'></frameset><frame src='b'></frameset></html>");
        assertEquals(2, doc.select("frameset").size());
        assertEquals(2, doc.select("frame").size());
    }

    // AfterAfterBody: a comment appearing after </html> is still added as a document-level child node
    @Test
    public void testAfterAfterBody_trailingCommentAfterHtmlClose_addedAsDocumentChild() throws Throwable {
        Document doc = Jsoup.parse("<html><body>Hi</body></html><!--tail-->");
        Node last = doc.childNode(doc.childNodeSize() - 1);
        assertTrue(last instanceof Comment);
        assertEquals("tail", ((Comment) last).getData());
    }

    // InBody: svg foreign content start tags are handled without breaking the parse
    @Test
    public void testInBody_svgForeignContent_parsedWithoutError() throws Throwable {
        Document doc = Jsoup.parse("<html><body><svg><circle></circle></svg></body></html>");
        assertEquals(1, doc.select("svg").size());
    }
}
