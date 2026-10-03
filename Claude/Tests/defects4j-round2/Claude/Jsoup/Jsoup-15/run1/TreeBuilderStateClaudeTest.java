package org.jsoup.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import static org.junit.Assert.*;

public class TreeBuilderStateClaudeTest {

    // Initial: isWhitespace(t) branch ignores leading whitespace tokens
    @Test
    public void testInitial_leadingWhitespaceIgnored_bodyTextPreserved() throws Throwable {
        Document doc = Jsoup.parse("   <html><body>Hello</body></html>");
        assertEquals("Hello", doc.body().text());
    }

    // Initial: comment branch inserts comment node before html is seen
    @Test
    public void testInitial_commentBeforeHtml_preservedInOutput() throws Throwable {
        Document doc = Jsoup.parse("<!--c--><html><body>Hi</body></html>");
        assertTrue(doc.outerHtml().contains("<!--c-->"));
    }

    // BeforeHtml: end tag in {head,body,html,br} falls through anythingElse, html still built
    @Test
    public void testBeforeHtml_strayEndTagIgnored_thenParsesBody() throws Throwable {
        Document doc = Jsoup.parse("</head><html><body>Hi</body></html>");
        assertEquals("Hi", doc.body().text());
    }

    // BeforeHtml: non-html start tag triggers anythingElse -> implicit html/body creation
    @Test
    public void testBeforeHtml_nonHtmlStartTag_impliedHtmlAndBody() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        assertEquals("Hello", doc.body().text());
        assertEquals("body", doc.body().tagName());
    }

    // BeforeHead: else branch inserts implicit head then reprocesses content in body
    @Test
    public void testBeforeHead_impliedHeadCreation_bodyContentUnderBody() throws Throwable {
        Document doc = Jsoup.parse("<html><p>Hi</p></html>");
        assertEquals("Hi", doc.body().text());
    }

    // InHead: title start tag handled via handleRcData, sets document title
    @Test
    public void testInHead_titleSetsDocumentTitle() throws Throwable {
        Document doc = Jsoup.parse("<html><head><title>T</title></head><body>B</body></html>");
        assertEquals("T", doc.title());
        assertEquals("B", doc.body().text());
    }

    // InHead: link start tag inserted as empty element via insertEmpty
    @Test
    public void testInHead_linkInsertedAsEmptyElement() throws Throwable {
        Document doc = Jsoup.parse("<html><head><link rel=\"stylesheet\" href=\"a.css\"></head><body>B</body></html>");
        Elements links = doc.select("link");
        assertEquals(1, links.size());
        assertEquals(0, links.first().childNodeSize());
        assertEquals("a.css", links.first().attr("href"));
    }

    // InHead: unknown start tag triggers anythingElse -> closes head, falls to body via InBody
    @Test
    public void testInHead_unknownStartTag_closesHeadFallsToBody() throws Throwable {
        Document doc = Jsoup.parse("<title>T</title><foo>X</foo>");
        assertEquals("T", doc.title());
        assertEquals("X", doc.body().text());
    }

    // AfterHead: explicit body start tag sets attributes and transitions to InBody
    @Test
    public void testAfterHead_bodyStartTag_setsAttributesAndTransitions() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head><body class=\"x\">Y</body></html>");
        assertEquals("x", doc.body().attr("class"));
        assertEquals("Y", doc.body().text());
    }

    // AfterHead: anythingElse branch implicitly creates body for non-head content
    @Test
    public void testAfterHead_impliedBodyForNonHeadContent() throws Throwable {
        Document doc = Jsoup.parse("<html><head></head>Content</html>");
        assertEquals("Content", doc.body().text());
    }

    // InBody StartTag: block element (div) auto-closes an open <p> via inButtonScope check
    @Test
    public void testInBody_pTagAutoClosedBeforeDiv() throws Throwable {
        Document doc = Jsoup.parse("<body><p>One<div>Two</div></body>");
        Elements ps = doc.select("p");
        assertEquals(1, ps.size());
        assertEquals("One", ps.first().text());
        assertEquals(0, ps.first().select("div").size());
    }

    // InBody StartTag: heading auto-pops an existing open heading before inserting new one
    @Test
    public void testInBody_headingAutoPopsPreviousHeading() throws Throwable {
        Document doc = Jsoup.parse("<h1>One<h2>Two</h2>");
        Elements h1s = doc.select("h1");
        Elements h2s = doc.select("h2");
        assertEquals(1, h1s.size());
        assertEquals(1, h2s.size());
        assertEquals(0, h1s.first().children().size());
    }

    // InBody StartTag: pre element inserted, text content preserved
    @Test
    public void testInBody_preElementPreservesContent() throws Throwable {
        Document doc = Jsoup.parse("<pre>code</pre>");
        assertTrue(doc.select("pre").first().text().contains("code"));
    }

    // InBody StartTag: form - second form ignored because tb.getFormElement() != null
    @Test
    public void testInBody_duplicateFormIgnored() throws Throwable {
        Document doc = Jsoup.parse("<form id=\"f1\"></form><form id=\"f2\"></form>");
        Elements forms = doc.select("form");
        assertEquals(1, forms.size());
        assertEquals("f1", forms.first().attr("id"));
    }

    // InBody StartTag: li auto-closes previous open li in scope loop
    @Test
    public void testInBody_liAutoClosesPreviousLi() throws Throwable {
        Document doc = Jsoup.parse("<ul><li>One<li>Two</ul>");
        Elements lis = doc.select("li");
        assertEquals(2, lis.size());
        assertEquals("One", lis.get(0).text());
        assertEquals("Two", lis.get(1).text());
        assertEquals(0, lis.get(0).children().size());
    }

    // InBody StartTag: dd/dt auto-close each other as siblings
    @Test
    public void testInBody_ddDtAutoCloseSiblings() throws Throwable {
        Document doc = Jsoup.parse("<dl><dt>Term<dd>Def</dl>");
        Elements dts = doc.select("dt");
        Elements dds = doc.select("dd");
        assertEquals(1, dts.size());
        assertEquals(1, dds.size());
        assertEquals("Term", dts.first().text());
        assertEquals("Def", dds.first().text());
    }

    // InBody StartTag: nested button closes previous button then reprocesses
    @Test
    public void testInBody_buttonNestedClosesAndReprocesses() throws Throwable {
        Document doc = Jsoup.parse("<button>One<button>Two</button></button>");
        Elements buttons = doc.select("button");
        assertEquals(2, buttons.size());
        assertEquals("One", buttons.get(0).text());
        assertEquals("Two", buttons.get(1).text());
    }

    // InBody StartTag: a tag closes an existing unmatched active-formatting <a>
    @Test
    public void testInBody_aTagClosesExistingActiveAnchor() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"1\">One<a href=\"2\">Two</a>");
        Elements as = doc.select("a");
        assertEquals(2, as.size());
        assertEquals("1", as.get(0).attr("href"));
        assertEquals("2", as.get(1).attr("href"));
    }

    // InBody StartTag: br inserted as empty self-closing element
    @Test
    public void testInBody_brSelfClosingEmptyElement() throws Throwable {
        Document doc = Jsoup.parse("<p>Text<br>More</p>");
        Elements brs = doc.select("br");
        assertEquals(1, brs.size());
        assertEquals(0, brs.first().childNodeSize());
    }

    // InBody StartTag: image tag name rewritten to img and reprocessed
    @Test
    public void testInBody_imageTagRenamedToImg() throws Throwable {
        Document doc = Jsoup.parse("<image src=\"x.png\">");
        Elements imgs = doc.select("img");
        assertEquals(1, imgs.size());
        assertEquals("x.png", imgs.first().attr("src"));
        assertEquals(0, doc.select("image").size());
    }

    // InBody StartTag: textarea switches tokeniser to Rcdata, inner tags not parsed
    @Test
    public void testInBody_textareaContentNotParsedAsTags() throws Throwable {
        Document doc = Jsoup.parse("<textarea><b>bold</b></textarea>");
        Elements tas = doc.select("textarea");
        assertEquals(1, tas.size());
        assertEquals(0, tas.first().select("b").size());
        assertTrue(tas.first().text().contains("bold"));
    }

    // InBody StartTag select -> InSelect: option correctly auto-closes previous option
    @Test
    public void testInBody_selectOptionAutoClosesWithinSelect() throws Throwable {
        Document doc = Jsoup.parse("<select><option>A<option>B</select>");
        Elements opts = doc.select("option");
        assertEquals(2, opts.size());
        assertEquals("A", opts.get(0).text());
        assertEquals("B", opts.get(1).text());
    }

    // BUG TARGET: InBody StartTag optgroup/option must also auto-close stray <option> outside select
    @Test
    public void testInBody_optionOutsideSelect_autoClosesPreviousOption_bugTest() throws Throwable {
        Document doc = Jsoup.parse("<option>One<option>Two");
        Elements bodyOptions = doc.body().children();
        assertEquals(2, bodyOptions.size());
        assertEquals("One", bodyOptions.get(0).text());
        assertEquals("Two", bodyOptions.get(1).text());
        assertEquals(0, bodyOptions.get(0).children().size());
    }

    // InBody StartTag html: merges attributes onto existing root html element
    @Test
    public void testInBody_duplicateHtmlTag_mergesAttributesOntoRoot() throws Throwable {
        Document doc = Jsoup.parse("</head><html id=\"first\"><body>B</body></html><html class=\"extra\">");
        Element root = doc.child(0);
        assertEquals("first", root.attr("id"));
        assertEquals("extra", root.attr("class"));
        assertEquals("B", doc.body().text());
    }

    // InBody StartTag body: merges attributes onto existing body, does not replace it
    @Test
    public void testInBody_duplicateBodyTag_mergesAttributesOntoBody() throws Throwable {
        Document doc = Jsoup.parse("<html><body id=\"b1\">Hi<body class=\"extra\">Bye</body></body></html>");
        assertEquals("b1", doc.body().attr("id"));
        assertEquals("extra", doc.body().attr("class"));
        assertTrue(doc.body().text().contains("Hi"));
        assertTrue(doc.body().text().contains("Bye"));
    }

    // InBody StartTag hr: closes open paragraph first then inserts empty hr
    @Test
    public void testInBody_hrAutoClosesOpenParagraph() throws Throwable {
        Document doc = Jsoup.parse("<p>Before<hr>");
        Elements ps = doc.select("p");
        Elements hrs = doc.select("hr");
        assertEquals(1, ps.size());
        assertEquals("Before", ps.first().text());
        assertEquals(1, hrs.size());
        assertEquals(0, hrs.first().childNodeSize());
    }

    // InBody EndTag br: special-cased to synthesize a br start tag instead
    @Test
    public void testInBody_endTagBr_convertsToBrElement() throws Throwable {
        Document doc = Jsoup.parse("<p>Test</br></p>");
        Elements brs = doc.select("br");
        assertEquals(1, brs.size());
    }

    // InTable StartTag td/th/tr: implicit tbody created via clearStackToTableContext
    @Test
    public void testInTable_tdCreatesImplicitTbody() throws Throwable {
        Document doc = Jsoup.parse("<table><tr><td>Cell</td></tr></table>");
        Elements tds = doc.select("td");
        Elements tbodies = doc.select("tbody");
        assertEquals(1, tds.size());
        assertEquals("Cell", tds.first().text());
        assertEquals(1, tbodies.size());
    }

    // InTableBody/InRow: multiple rows each retaining their own cells
    @Test
    public void testInTableBody_multipleRowsEachWithCells() throws Throwable {
        Document doc = Jsoup.parse("<table><tr><td>A</td><td>B</td></tr><tr><td>C</td></tr></table>");
        Elements rows = doc.select("tr");
        Elements tds = doc.select("td");
        assertEquals(2, rows.size());
        assertEquals(3, tds.size());
        assertEquals("A", tds.get(0).text());
        assertEquals("B", tds.get(1).text());
        assertEquals("C", tds.get(2).text());
    }

    // InCell EndTag td/th: new td auto-closes previous td in the same row (closeCell)
    @Test
    public void testInCell_tdAutoClosesPreviousTdInSameRow() throws Throwable {
        Document doc = Jsoup.parse("<table><tr><td>A<td>B</tr></table>");
        Elements tds = doc.select("td");
        assertEquals(2, tds.size());
        assertEquals("A", tds.get(0).text());
        assertEquals("B", tds.get(1).text());
        assertEquals(0, tds.get(0).children().size());
    }

    // InColumnGroup StartTag col: inserted as empty element under colgroup
    @Test
    public void testInColumnGroup_colElementInsertedEmpty() throws Throwable {
        Document doc = Jsoup.parse("<table><colgroup><col span=\"2\"></colgroup><tr><td>A</td></tr></table>");
        Elements cols = doc.select("col");
        assertEquals(1, cols.size());
        assertEquals("2", cols.first().attr("span"));
        assertEquals(0, cols.first().childNodeSize());
    }

    // InCaption: caption text content preserved, closes to InTable on end tag
    @Test
    public void testInCaption_captionTextPreserved() throws Throwable {
        Document doc = Jsoup.parse("<table><caption>Cap</caption><tr><td>A</td></tr></table>");
        Elements captions = doc.select("caption");
        assertEquals(1, captions.size());
        assertEquals("Cap", captions.first().text());
    }

    // AfterBody -> AfterAfterBody: stray content after </html> reprocessed via InBody
    @Test
    public void testAfterBody_contentAfterHtmlMergedIntoBody() throws Throwable {
        Document doc = Jsoup.parse("<html><body>Hi</body></html>Extra");
        String text = doc.body().text();
        assertTrue(text.contains("Hi"));
        assertTrue(text.contains("Extra"));
    }

    // InFrameset StartTag frame: inserted as empty elements, no body created
    @Test
    public void testInFrameset_frameElementsInsertedEmpty() throws Throwable {
        Document doc = Jsoup.parse("<html><frameset><frame src=\"a.html\"><frame src=\"b.html\"></frameset></html>");
        Elements frames = doc.select("frame");
        assertEquals(2, frames.size());
        assertEquals("a.html", frames.get(0).attr("src"));
        assertEquals(0, frames.get(0).childNodeSize());
    }

    // Entry point parseBodyFragment: content wrapped directly under body element
    @Test
    public void testParseBodyFragment_wrapsContentInBody() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<p>Fragment</p>");
        assertEquals("Fragment", doc.body().text());
    }
}
