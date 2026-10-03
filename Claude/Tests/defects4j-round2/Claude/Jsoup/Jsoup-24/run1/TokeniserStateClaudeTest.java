package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DocumentType;
import org.jsoup.select.Elements;

public class TokeniserStateClaudeTest {

    // Data: plain text with no special characters, consumed as a single run
    @Test
    public void testData_plainText_noSpecialChars() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<p>Hello World</p>");
        Element p = doc.body().select("p").first();
        assertEquals("Hello World", p.text());
    }

    // Data -> CharacterReferenceInData: known named entity is decoded
    @Test
    public void testData_ampersandKnownEntity_decodesToLiteral() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("&amp;");
        assertEquals("&", doc.body().text());
    }

    // Data -> CharacterReferenceInData: unknown entity emits literal '&' then continues as text
    @Test
    public void testData_ampersandUnknownEntity_emitsLiteralAmpersand() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("&zzzznope;");
        assertEquals("&zzzznope;", doc.body().text());
    }

    // Data: nullChar case emits the raw null character (NOT the replacement char)
    @Test
    public void testData_nullChar_emitsRawNullNotReplacement() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("a\u0000b");
        Node first = doc.body().childNode(0);
        assertTrue(first instanceof TextNode);
        assertEquals("a\u0000b", ((TextNode) first).getWholeText());
    }

    // Rcdata (title): character references are decoded
    @Test
    public void testRcdata_title_decodesEntities() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<title>&lt;b&gt;</title>");
        Element title = doc.body().select("title").first();
        assertEquals("<b>", title.text());
    }

    // Rcdata: nullChar is replaced with the Unicode replacement character
    @Test
    public void testRcdata_title_nullChar_emitsReplacementChar() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<title>a\u0000b</title>");
        Element title = doc.body().select("title").first();
        assertEquals("a\uFFFDb", title.text());
    }

    // Rawtext (style): entities are NOT decoded
    @Test
    public void testRawtext_style_doesNotDecodeEntities() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<style>&amp;</style>");
        Element style = doc.body().select("style").first();
        assertEquals("&amp;", style.data());
    }

    // RawtextEndTagName: inappropriate end tag (name mismatch) emits literal text and stays in rawtext
    @Test
    public void testRawtext_inappropriateEndTag_emitsLiteralAndContinues() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<style></div></style>");
        Element style = doc.body().select("style").first();
        assertEquals("</div", style.data());
    }

    // ScriptData: '<' not followed by '/' or '!' emits literal '<' and unconsumes
    @Test
    public void testScriptData_lessThanFallback_emitsLiteral() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<script>if(a<b){}</script>");
        Element script = doc.body().select("script").first();
        assertEquals("if(a<b){}", script.data());
    }

    // PLAINTEXT: never interprets any subsequent tags, everything is literal text
    @Test
    public void testPlaintext_neverInterpretsTags() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<plaintext>abc<p>def");
        Element plaintext = doc.body().select("plaintext").first();
        assertEquals("abc<p>def", plaintext.text());
    }

    // TagOpen: '<' followed by a non-letter/non-special char emits literal '<' and returns to Data
    @Test
    public void testTagOpen_nonLetterAfterLt_emitsLiteralLessThan() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("1 < 2");
        assertEquals("1 < 2", doc.body().text());
    }

    // TagOpen: '?' triggers a bogus comment
    @Test
    public void testTagOpen_questionMark_createsBogusComment() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<?xml version=\"1.0\"?>text");
        Node first = doc.body().childNode(0);
        assertTrue(first instanceof Comment);
        assertTrue(((Comment) first).getData().indexOf("xml") >= 0);
    }

    // EndTagOpen: EOF right after "</" emits literal "</" text
    @Test
    public void testEndTagOpen_eofAfterSlash_emitsLiteralEndTagText() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("a</");
        assertEquals("a</", doc.body().text());
    }

    // EndTagOpen: "</>" produces no output at all
    @Test
    public void testEndTagOpen_immediateGreaterThan_emitsNothing() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("a</>b");
        assertEquals("ab", doc.body().text());
    }

    // EndTagOpen: non-letter, non '>' char triggers bogus comment
    @Test
    public void testEndTagOpen_nonLetterNonGreater_createsBogusComment() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("a</1>b");
        Element body = doc.body();
        assertEquals("ab", body.text());
        Comment foundComment = null;
        for (int i = 0; i < body.childNodeSize(); i++) {
            Node n = body.childNode(i);
            if (n instanceof Comment) foundComment = (Comment) n;
        }
        assertNotNull(foundComment);
        assertEquals("1", foundComment.getData());
    }

    // TagName: EOF mid tag name discards the pending tag entirely
    @Test
    public void testTagName_eofMidTagName_discardsTagPending() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<di");
        assertEquals(0, doc.body().children().size());
    }

    // TagName -> SelfClosingStartTag: void element with trailing slash parses correctly
    @Test
    public void testTagName_selfClosingSlash_parsesTag() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<br/>");
        Element br = doc.body().select("br").first();
        assertNotNull(br);
        assertEquals("br", br.tagName());
    }

    // MarkupDeclarationOpen: unrecognized "<!...>" sequence falls back to bogus comment
    @Test
    public void testMarkupDeclarationOpen_unrecognized_fallsBackToBogusComment() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<!foo>bar");
        Element body = doc.body();
        Comment foundComment = null;
        for (int i = 0; i < body.childNodeSize(); i++) {
            Node n = body.childNode(i);
            if (n instanceof Comment) foundComment = (Comment) n;
        }
        assertNotNull(foundComment);
        assertEquals("foo", foundComment.getData());
    }

    // MarkupDeclarationOpen -> CdataSection: content is emitted as raw text
    @Test
    public void testMarkupDeclarationOpen_cdataSection_emitsRawText() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<![CDATA[hello]]>");
        assertEquals("hello", doc.body().text());
    }

    // CommentStart/Comment: basic comment content is captured
    @Test
    public void testComment_simple_capturesData() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<!-- comment -->");
        Node n = doc.body().childNode(0);
        assertTrue(n instanceof Comment);
        assertEquals(" comment ", ((Comment) n).getData());
    }

    // CommentEnd -> CommentEndBang: "--!>" still terminates the comment
    @Test
    public void testComment_endBangThenGreaterThan_closesComment() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<!--a--!>");
        Node n = doc.body().childNode(0);
        assertTrue(n instanceof Comment);
        assertEquals("a", ((Comment) n).getData());
    }

    // MarkupDeclarationOpen -> Doctype: DOCTYPE declaration creates a DocumentType node
    @Test
    public void testDoctype_simple_createsDocumentTypeNode() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html><html><head></head><body></body></html>");
        boolean foundDoctype = false;
        for (int i = 0; i < doc.childNodeSize(); i++) {
            if (doc.childNode(i) instanceof DocumentType) foundDoctype = true;
        }
        assertTrue(foundDoctype);
    }

    // ScriptDataEscapeStart: no second '-' returns directly to ScriptData
    @Test
    public void testScriptDataEscapeStart_noDash_returnsToScriptData() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<script><!x</script>");
        Element script = doc.body().select("script").first();
        assertEquals("<!x", script.data());
    }

    // ScriptDataEscapeStartDash: no second dash returns directly to ScriptData
    @Test
    public void testScriptDataEscapeStartDash_noDash_returnsToScriptData() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<script><!-x</script>");
        Element script = doc.body().select("script").first();
        assertEquals("<!-x", script.data());
    }

    // ScriptDataEscaped: nullChar is replaced with the replacement character
    @Test
    public void testScriptDataEscaped_nullChar_emitsReplacementChar() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<script><!--a\u0000b--></script>");
        Element script = doc.body().select("script").first();
        assertEquals("<!--a\uFFFDb-->", script.data());
    }

    // ScriptDataEscapedEndTagOpen: non-letter after "</" emits literal "</" and stays escaped
    @Test
    public void testScriptDataEscapedEndTagOpen_nonLetter_emitsLiteral() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<script><!--</1x--></script>");
        Element script = doc.body().select("script").first();
        assertEquals("<!--</1x-->", script.data());
    }

    // ScriptDataDoubleEscapeStart/End: nested <script>...</script> inside escaped data round-trips
    @Test
    public void testScriptDataDoubleEscape_nestedScriptTag_roundTrips() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<script><!--<script></script>--></script>");
        Element script = doc.body().select("script").first();
        assertEquals("<!--<script></script>-->", script.data());
    }

    // AttributeValue_doubleQuoted: character reference inside quoted value is decoded
    @Test
    public void testAttributeValueDoubleQuoted_entityDecoded() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<a href=\"a&amp;b\">x</a>");
        Element a = doc.body().select("a").first();
        assertEquals("a&b", a.attr("href"));
    }

    // BeforeAttributeValue -> AttributeValue_unquoted: leading '&' is unconsumed and decoded
    @Test
    public void testAttributeValueUnquoted_ampersandDecoded() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<a href=&amp;>x</a>");
        Element a = doc.body().select("a").first();
        assertEquals("&", a.attr("href"));
    }

    // BeforeAttributeValue default case (unquoted) plus AfterAttributeName boolean attribute
    @Test
    public void testBeforeAttributeValue_unquotedDefault_andBooleanAttribute() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<input type=text disabled>");
        Element input = doc.body().select("input").first();
        assertEquals("text", input.attr("type"));
        assertTrue(input.hasAttr("disabled"));
    }

    // SelfClosingStartTag: '>' after '/' marks tag self-closing and parses attributes correctly
    @Test
    public void testSelfClosingStartTag_voidElement_parsesAttribute() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<img src=\"a.png\"/>");
        Element img = doc.body().select("img").first();
        assertTrue(img.hasAttr("src"));
        assertEquals("a.png", img.attr("src"));
    }

    // Bug-catching test: ScriptDataEscapedEndTagName must close the script tag on an
    // appropriate end tag without consuming extra input beyond '>'.
    @Test
    public void testScriptDataEscapedEndTagName_appropriateEndTag_closesScriptProperly() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<script><!--x</script>-->y");
        Element body = doc.body();
        Elements scripts = body.select("script");
        assertEquals(1, scripts.size());
        Element script = scripts.first();
        assertEquals("<!--x", script.data());
        Node sibling = script.nextSibling();
        assertTrue(sibling instanceof TextNode);
        assertEquals("-->y", ((TextNode) sibling).text());
    }
}
