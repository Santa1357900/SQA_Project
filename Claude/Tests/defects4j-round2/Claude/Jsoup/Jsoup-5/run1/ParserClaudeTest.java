package org.jsoup.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParserClaudeTest {

    private static final String BASE = "http://example.com/";

    // covers: full document parse, title extraction, body text, stack valid ancestor (html root)
    @Test
    public void testParse_fullHtmlDocument_parsesTitleAndBodyText() throws Throwable {
        Document doc = Parser.parse("<html><head><title>My Title</title></head><body><p>Hello World</p></body></html>", BASE);
        assertEquals("My Title", doc.title());
        assertEquals("Hello World", doc.body().text());
    }

    // covers: implicit html/body wrapping when fragment has no root tags (addChildToParent, invalid ancestor branch)
    @Test
    public void testParse_fragmentWithoutHtmlTags_wrapsInImplicitHtmlBodyStructure() throws Throwable {
        Document doc = Parser.parse("<p>One</p>", BASE);
        assertEquals("One", doc.body().text());
        assertEquals("p", doc.body().child(0).tagName());
    }

    // covers: Validate.notNull(html) throw path
    @Test
    public void testParse_nullHtml_throwsIllegalArgumentException() throws Throwable {
        try {
            Parser.parse(null, BASE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: Validate.notNull(baseUri) throw path
    @Test
    public void testParse_nullBaseUri_throwsIllegalArgumentException() throws Throwable {
        try {
            Parser.parse("<p>x</p>", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: parse loop with zero iterations (tq empty from start), normalise still runs
    @Test
    public void testParse_emptyString_returnsDocumentWithEmptyBody() throws Throwable {
        Document doc = Parser.parse("", BASE);
        assertNotNull(doc.body());
        assertEquals("", doc.body().text());
    }

    // covers: body as top level child triggers implicit head creation special case
    @Test
    public void testParse_topLevelBodyTag_createsImplicitHeadAndHtmlWrapper() throws Throwable {
        Document doc = Parser.parse("<body><p>X</p></body>", BASE);
        assertEquals(0, doc.head().childNodeSize());
        assertEquals("X", doc.body().text());
    }

    // covers: parseBodyFragment entry point, doc.head empty, body populated
    @Test
    public void testParseBodyFragment_producesEmptyHeadAndPopulatedBody() throws Throwable {
        Document doc = Parser.parseBodyFragment("<p>Fragment</p>", BASE);
        assertEquals(0, doc.head().childNodeSize());
        assertEquals("Fragment", doc.body().text());
    }

    // covers: relaxed=true skips implicit parent creation (getImplicitParent branch bypassed)
    @Test
    public void testParseBodyFragmentRelaxed_liWithoutList_doesNotCreateImplicitUl() throws Throwable {
        Document doc = Parser.parseBodyFragmentRelaxed("<li>Item</li>", BASE);
        Element first = doc.body().child(0);
        assertEquals("li", first.tagName());
    }

    // covers: default (non relaxed) creates implicit parent via getImplicitParent
    @Test
    public void testParseBodyFragment_liWithoutList_createsImplicitUlParent() throws Throwable {
        Document doc = Parser.parseBodyFragment("<li>Item</li>", BASE);
        Element first = doc.body().child(0);
        assertEquals("ul", first.tagName());
        assertEquals("li", first.child(0).tagName());
    }

    // covers: void element (tag.isEmpty true) not pushed to stack, following content is sibling not child
    @Test
    public void testParse_selfClosingBr_notPushedOntoStack() throws Throwable {
        Document doc = Parser.parseBodyFragment("<p>Before<br/>After</p>", BASE);
        Element p = doc.body().child(0);
        assertEquals(3, p.childNodeSize());
        assertEquals("br", p.child(0).tagName());
    }

    // covers: void element attribute parsing without explicit close
    @Test
    public void testParseBodyFragment_voidImgElement_parsesAttributesWithoutChildren() throws Throwable {
        Document doc = Parser.parseBodyFragment("<img src=\"x.jpg\">", BASE);
        Element img = doc.body().child(0);
        assertEquals("x.jpg", img.attr("src"));
        assertEquals(0, img.childNodeSize());
    }

    // covers: unknown tag self closed -> isKnownTag false branch, setSelfClosing, treated as empty element
    @Test
    public void testParseBodyFragment_unknownSelfClosingTag_treatedAsEmptyElement() throws Throwable {
        Document doc = Parser.parseBodyFragment("<custom/>text", BASE);
        assertEquals("custom", doc.body().child(0).tagName());
        assertEquals(2, doc.body().childNodeSize());
    }

    // covers: tag.isData() true, non title/textarea branch -> raw DataNode content
    @Test
    public void testParseBodyFragment_scriptTag_rawContentNotParsedAsHtml() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div><script>var x = 1 < 2;</script></div>", BASE);
        Element script = doc.body().child(0).child(0);
        assertEquals(1, script.childNodeSize());
        assertTrue(doc.toString().contains("var x = 1 < 2;"));
    }

    // covers: tag.isData() true, title branch -> TextNode.createFromEncoded decoding
    @Test
    public void testParse_titleWithEntity_decodesAsText() throws Throwable {
        Document doc = Parser.parse("<html><head><title>A &amp; B</title></head><body></body></html>", BASE);
        assertEquals("A & B", doc.title());
    }

    // covers: tag.isData() true, textarea branch -> TextNode.createFromEncoded decoding
    @Test
    public void testParseBodyFragment_textareaWithEntity_decodesAsText() throws Throwable {
        Document doc = Parser.parseBodyFragment("<textarea>Line &amp; Break</textarea>", BASE);
        Element textarea = doc.body().child(0);
        assertEquals("Line & Break", textarea.text());
    }

    // covers: base tag with href updates baseUri, subsequent element absUrl resolves against new base
    @Test
    public void testParse_baseHrefPresent_updatesBaseUriForAbsUrl() throws Throwable {
        Document doc = Parser.parse("<html><head><base href=\"http://foo.com/sub/\"></head><body><a href=\"page.html\">L</a></body></html>", BASE);
        Element a = doc.body().child(0);
        assertEquals("http://foo.com/sub/page.html", a.absUrl("href"));
    }

    // covers: base tag without href (href.length()==0) leaves baseUri unchanged
    @Test
    public void testParse_baseWithoutHref_doesNotChangeBaseUri() throws Throwable {
        Document doc = Parser.parse("<html><head><base target=\"_blank\"></head><body><a href=\"page.html\">L</a></body></html>", BASE);
        Element a = doc.body().child(0);
        assertEquals(BASE + "page.html", a.absUrl("href"));
    }

    // covers: parseAttribute single quoted value branch
    @Test
    public void testParseBodyFragment_singleQuotedAttribute_parsesValue() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div id='main'>Text</div>", BASE);
        assertEquals("main", doc.body().child(0).attr("id"));
    }

    // covers: parseAttribute double quoted value branch
    @Test
    public void testParseBodyFragment_doubleQuotedAttribute_parsesValue() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div id=\"main\">Text</div>", BASE);
        assertEquals("main", doc.body().child(0).attr("id"));
    }

    // covers: parseAttribute unquoted value branch (StringBuilder scan)
    @Test
    public void testParseBodyFragment_unquotedAttribute_parsesValue() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div id=main>Text</div>", BASE);
        assertEquals("main", doc.body().child(0).attr("id"));
    }

    // covers: parseAttribute matchChomp("=") false branch (boolean attribute)
    @Test
    public void testParseBodyFragment_booleanAttributeWithoutValue_parsesEmptyValue() throws Throwable {
        Document doc = Parser.parseBodyFragment("<input disabled>", BASE);
        Element input = doc.body().child(0);
        assertEquals("", input.attr("disabled"));
    }

    // covers: attribute parsing loop executes multiple iterations
    @Test
    public void testParseBodyFragment_multipleAttributes_parsesAll() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div id=\"a\" class=\"b\" data-x=\"c\">T</div>", BASE);
        Element div = doc.body().child(0);
        assertEquals("a", div.attr("id"));
        assertEquals("b", div.attr("class"));
        assertEquals("c", div.attr("data-x"));
    }

    // covers: popStackToClose no match found before hitting body -> end tag ignored
    @Test
    public void testParseBodyFragment_mismatchedEndTag_isIgnored() throws Throwable {
        Document doc = Parser.parseBodyFragment("<p>Text</b></p>", BASE);
        Element p = doc.body().child(0);
        assertEquals("p", p.tagName());
        assertEquals("Text", p.text());
    }

    // covers: parseEndTag with empty tagName (consumeTagName returns "") is ignored, no exception
    @Test
    public void testParseBodyFragment_emptyEndTagName_isIgnored() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div>Content</></div>", BASE);
        Element div = doc.body().child(0);
        assertEquals("div", div.tagName());
        assertEquals("Content", div.text());
    }

    // covers: parseComment with proper "-->" close, endsWith("-") true branch strips trailing dash
    @Test
    public void testParseBodyFragment_comment_extractsContentBetweenMarkers() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div><!-- Hello World --></div>", BASE);
        Element div = doc.body().child(0);
        assertTrue(div.toString().contains("Hello World"));
    }

    // covers: parseComment where content immediately precedes closing dashes (off by one dash trim check)
    @Test
    public void testParseBodyFragment_commentImmediatelyBeforeClose_trimsExactlyOneDash() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div><!--NoSpace--></div>", BASE);
        Element div = doc.body().child(0);
        assertTrue(div.toString().contains("NoSpace"));
        assertFalse(div.toString().contains("NoSpace-E"));
    }

    // covers: parseCdata branch, raw text not parsed as html tags
    @Test
    public void testParseBodyFragment_cdata_treatedAsRawText() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div><![CDATA[raw <b> data]]></div>", BASE);
        Element div = doc.body().child(0);
        assertEquals("raw <b> data", div.text());
        assertEquals(0, div.children().size());
    }

    // covers: parseXmlDecl branch (matchesCS("<?")), appended as sibling node to following elements
    @Test
    public void testParseBodyFragment_processingInstruction_appendedAsNode() throws Throwable {
        Document doc = Parser.parseBodyFragment("<?xml version=\"1.0\"?><div>Text</div>", BASE);
        assertEquals(2, doc.body().childNodeSize());
        assertEquals("div", doc.body().child(0).tagName());
        assertEquals("Text", doc.body().child(0).text());
    }

    // covers: parseTextNode branch where peek() == '<' (literal less-than not a tag)
    @Test
    public void testParseBodyFragment_literalLessThanInText_preservedAsText() throws Throwable {
        Document doc = Parser.parseBodyFragment("1 < 2", BASE);
        assertEquals("1 < 2", doc.body().text());
    }

    // covers: popStackToSuitableContainer loop pops previous paragraph (p cannot contain p)
    @Test
    public void testParseBodyFragment_consecutiveParagraphs_autoClosesPrevious() throws Throwable {
        Document doc = Parser.parseBodyFragment("<p>One<p>Two</p>", BASE);
        assertEquals(2, doc.body().children().size());
        assertEquals("One", doc.body().child(0).text());
        assertEquals("Two", doc.body().child(1).text());
    }
}
