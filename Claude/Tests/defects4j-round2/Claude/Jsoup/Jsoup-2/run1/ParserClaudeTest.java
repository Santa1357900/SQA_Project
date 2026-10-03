package org.jsoup.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParserClaudeTest {

    // Validate.notNull(html) branch: null html must throw IllegalArgumentException
    @Test
    public void testParse_nullHtml_throwsIllegalArgumentException() throws Throwable {
        try {
            Parser.parse(null, "http://example.com/");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Validate.notNull(baseUri) branch: null baseUri must throw IllegalArgumentException
    @Test
    public void testParse_nullBaseUri_throwsIllegalArgumentException() throws Throwable {
        try {
            Parser.parse("<p>hi</p>", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // parseBodyFragment constructor path: null html must throw
    @Test
    public void testParseBodyFragment_nullHtml_throwsIllegalArgumentException() throws Throwable {
        try {
            Parser.parseBodyFragment(null, "http://example.com/");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // parseBodyFragment constructor path: null baseUri must throw
    @Test
    public void testParseBodyFragment_nullBaseUri_throwsIllegalArgumentException() throws Throwable {
        try {
            Parser.parseBodyFragment("<p>hi</p>", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // parse() loop with 0 iterations: empty html still yields normalised doc with body
    @Test
    public void testParse_emptyString_producesBodyTag() throws Throwable {
        Document doc = Parser.parse("", "http://example.com/");
        Element body = doc.body();
        assertNotNull(body);
        assertEquals("body", body.tagName());
    }

    // parseTextNode: plain text appears in rendered output
    @Test
    public void testParse_plainText_appearsInOutput() throws Throwable {
        Document doc = Parser.parse("hello world", "http://example.com/");
        assertTrue(doc.toString().indexOf("hello world") >= 0);
    }

    // parseComment: normal comment content preserved in output
    @Test
    public void testParseComment_basic_preservedInOutput() throws Throwable {
        Document doc = Parser.parse("<p><!-- hello world --></p>", "http://example.com/");
        assertTrue(doc.toString().indexOf("<!-- hello world -->") >= 0);
    }

    // parseComment: data.endsWith("-") branch trims redundant dash before close
    @Test
    public void testParseComment_extraDashBeforeClose_trimmedCorrectly() throws Throwable {
        Document doc = Parser.parse("<!--x--->", "http://example.com/");
        assertTrue(doc.toString().indexOf("<!--x-->") >= 0);
    }

    // parseCdata: raw content is preserved as text
    @Test
    public void testParseCdata_basic_rawContentPreserved() throws Throwable {
        Document doc = Parser.parse("<![CDATA[hello world]]>", "http://example.com/");
        assertTrue(doc.toString().indexOf("hello world") >= 0);
    }

    // parseXmlDecl bug: '!' first char is a declaration (e.g. DOCTYPE), must render with '<!' not '<?'
    @Test
    public void testParseXmlDecl_doctypeDeclaration_notRenderedAsProcessingInstruction() throws Throwable {
        Document doc = Parser.parse("<!DOCTYPE html><html><head></head><body>Hi</body></html>", "http://example.com/");
        assertTrue(doc.toString().indexOf("<!DOCTYPE html>") >= 0);
    }

    // parseXmlDecl: '?' first char is a processing instruction, must render with '<?'
    @Test
    public void testParseXmlDecl_processingInstruction_renderedWithQuestionMark() throws Throwable {
        Document doc = Parser.parse("<?xml version=\"1.0\"?>", "http://example.com/");
        assertTrue(doc.toString().indexOf("<?xml") >= 0);
    }

    // parseEndTag: matching open tag on stack closes element correctly
    @Test
    public void testParseEndTag_matchingOpenTag_closesElementProperly() throws Throwable {
        Document doc = Parser.parse("<div><p>Hello</p></div>", "http://example.com/");
        assertTrue(doc.toString().indexOf("<p>Hello</p>") >= 0);
    }

    // parseEndTag: tagName.length()==0 branch must not throw
    @Test
    public void testParseEndTag_emptyTagName_doesNotThrow() throws Throwable {
        Document doc = Parser.parse("<p>Hello</>", "http://example.com/");
        assertNotNull(doc);
        assertTrue(doc.toString().indexOf("Hello") >= 0);
    }

    // parseEndTag: unmatched closing tag is ignored gracefully (elToClose stays null)
    @Test
    public void testParseEndTag_unmatchedTag_ignoredGracefully() throws Throwable {
        Document doc = Parser.parse("<p>Hi</span>", "http://example.com/");
        assertNotNull(doc);
        assertTrue(doc.toString().indexOf("Hi") >= 0);
    }

    // parseStartTag: tagName.length()==0 branch treats '<' as literal escaped text
    @Test
    public void testParseStartTag_malformedLessThan_treatedAsEscapedText() throws Throwable {
        Document doc = Parser.parse("< foo bar", "http://example.com/");
        assertTrue(doc.toString().indexOf("&lt;") >= 0);
    }

    // parseAttribute: single-quoted value branch
    @Test
    public void testParseStartTag_singleQuotedAttribute_parsedCorrectly() throws Throwable {
        Document doc = Parser.parse("<p id='main'>content</p>", "http://example.com/");
        assertTrue(doc.toString().indexOf("id=\"main\"") >= 0);
    }

    // parseAttribute: double-quoted value branch
    @Test
    public void testParseStartTag_doubleQuotedAttribute_parsedCorrectly() throws Throwable {
        Document doc = Parser.parse("<p id=\"main\">content</p>", "http://example.com/");
        assertTrue(doc.toString().indexOf("id=\"main\"") >= 0);
    }

    // parseAttribute: unquoted value scanning branch
    @Test
    public void testParseStartTag_unquotedAttribute_parsedCorrectly() throws Throwable {
        Document doc = Parser.parse("<img src=image.png>", "http://example.com/");
        assertTrue(doc.toString().indexOf("src=\"image.png\"") >= 0);
    }

    // parseAttribute: matchChomp("=") false branch, key only, value stays ""
    @Test
    public void testParseStartTag_booleanAttributeNoValue_keyPresent() throws Throwable {
        Document doc = Parser.parse("<input disabled>", "http://example.com/");
        assertTrue(doc.toString().indexOf("disabled") >= 0);
    }

    // parseStartTag: matchChomp("/>") branch marks element as empty
    @Test
    public void testParseStartTag_selfClosingSlash_treatedAsEmptyElement() throws Throwable {
        Document doc = Parser.parse("<br/>", "http://example.com/");
        assertTrue(doc.toString().indexOf("<br") >= 0);
    }

    // tag.isData() branch with script: raw DataNode content, not encoded
    @Test
    public void testParseStartTag_scriptTag_rawContentNotEscaped() throws Throwable {
        Document doc = Parser.parse("<script>var a = 1 < 2;</script>", "http://example.com/");
        assertTrue(doc.toString().indexOf("var a = 1 < 2;") >= 0);
    }

    // tag.isData() branch with title: text node created from encoded content, roundtrips entity
    @Test
    public void testParseStartTag_titleTag_entityDecodedAndReencoded() throws Throwable {
        Document doc = Parser.parse("<title>Fish &amp; Chips</title>", "http://example.com/");
        assertTrue(doc.toString().indexOf("Fish &amp; Chips") >= 0);
    }

    // tag.isData() branch with textarea: content preserved as text
    @Test
    public void testParseStartTag_textareaTag_treatedAsText() throws Throwable {
        Document doc = Parser.parse("<textarea>Some text</textarea>", "http://example.com/");
        assertTrue(doc.toString().indexOf("Some text") >= 0);
    }

    // child.tagName().equals("base") branch: href attribute is parsed correctly
    @Test
    public void testParseStartTag_baseHrefAttribute_parsedCorrectly() throws Throwable {
        Document doc = Parser.parse("<base href=\"http://new.example.com/\">", "http://example.com/");
        assertNotNull(doc);
        assertTrue(doc.toString().indexOf("href=\"http://new.example.com/\"") >= 0);
    }

    // parseTextNode: stray ampersand is escaped on output
    @Test
    public void testParseTextNode_ampersand_escapedInOutput() throws Throwable {
        Document doc = Parser.parse("a & b", "http://example.com/");
        assertTrue(doc.toString().indexOf("&amp;") >= 0);
    }

    // multiple attributes on one tag are all parsed
    @Test
    public void testParseAttribute_multipleAttributes_allParsed() throws Throwable {
        Document doc = Parser.parse("<a href=\"http://example.com\" target=\"_blank\">link</a>", "http://example.com/");
        String html = doc.toString();
        assertTrue(html.indexOf("href=\"http://example.com\"") >= 0);
        assertTrue(html.indexOf("target=\"_blank\"") >= 0);
    }

    // void element img with src attribute parses without crashing
    @Test
    public void testParse_selfClosingVoidElement_imgTag() throws Throwable {
        Document doc = Parser.parse("<img src=\"x.png\">", "http://example.com/");
        assertTrue(doc.toString().indexOf("src=\"x.png\"") >= 0);
    }

    // parseBodyFragment: nested tags inside body fragment produce correct structure
    @Test
    public void testParseBodyFragment_nestedTags_producesValidStructure() throws Throwable {
        Document doc = Parser.parseBodyFragment("<div><p>Test</p></div>", "http://example.com/");
        assertTrue(doc.body().toString().indexOf("<p>Test</p>") >= 0);
    }

    // multiple nested closes via popStackToClose loop (>1 iteration) leave later siblings intact
    @Test
    public void testParseEndTag_multiLevelNesting_siblingAfterCloseParsedCorrectly() throws Throwable {
        Document doc = Parser.parse("<div><section><p>Text</p></section></div><p>After</p>", "http://example.com/");
        String html = doc.toString();
        assertTrue(html.indexOf("Text") >= 0);
        assertTrue(html.indexOf("After") >= 0);
    }

    // full document with explicit html/head/body structure parses tags into proper places
    @Test
    public void testParse_fullDocumentStructure_headAndBodyContentPresent() throws Throwable {
        Document doc = Parser.parse("<html><head><title>T</title></head><body>B</body></html>", "http://example.com/");
        String html = doc.toString();
        assertTrue(html.indexOf("<title>T</title>") >= 0);
        assertTrue(html.indexOf("B") >= 0);
    }

    // parseBodyFragment: doc.body() tag name is always "body"
    @Test
    public void testParseBodyFragment_bodyTagName_isBody() throws Throwable {
        Document doc = Parser.parseBodyFragment("<p>hello</p>", "http://example.com/");
        assertEquals("body", doc.body().tagName());
    }

    // parseComment inside cdata-free plain doc: multiple comments in sequence both preserved
    @Test
    public void testParseComment_multipleComments_bothPreserved() throws Throwable {
        Document doc = Parser.parse("<!--one--><!--two-->", "http://example.com/");
        String html = doc.toString();
        assertTrue(html.indexOf("<!--one-->") >= 0);
        assertTrue(html.indexOf("<!--two-->") >= 0);
    }
}
