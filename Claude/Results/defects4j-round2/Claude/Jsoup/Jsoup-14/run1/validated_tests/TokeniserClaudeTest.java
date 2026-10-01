package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Comment;

public class TokeniserClaudeTest {

    // Data state: plain text with no markup, read() loop consumes chars into charBuffer
    @Test
    public void testParse_plainText_returnsCharacterToken() throws Throwable {
        Document doc = Jsoup.parse("hello world");
        assertEquals("hello world", doc.body().text());
    }

    // Data state: zero-iteration case, empty input produces empty body text
    @Test
    public void testParse_emptyInput_producesEmptyBody() throws Throwable {
        Document doc = Jsoup.parse("");
        assertEquals("", doc.body().text());
    }

    // consumeCharacterReference named branch: found entity "amp" -> '&'
    @Test
    public void testParse_namedEntityAmp_decodesToAmpersand() throws Throwable {
        Document doc = Jsoup.parse("a&amp;b");
        assertEquals("a&b", doc.body().text());
    }

    // consumeCharacterReference named branch: found entities "lt"/"gt"
    @Test
    public void testParse_namedEntityLtGt_decodesAngleBrackets() throws Throwable {
        Document doc1 = Jsoup.parse("a&lt;b");
        Document doc2 = Jsoup.parse("a&gt;b");
        assertEquals("a<b", doc1.body().text());
        assertEquals("a>b", doc2.body().text());
    }

    // consumeCharacterReference named branch: found entity "quot"
    @Test
    public void testParse_namedEntityQuot_decodesToQuote() throws Throwable {
        Document doc = Jsoup.parse("a&quot;b");
        assertEquals("a\"b", doc.body().text());
    }

    // consumeCharacterReference named branch: shrinking loop never finds valid entity -> rewind, literal kept
    @Test
    public void testParse_unrecognizedNamedEntity_keepsLiteral() throws Throwable {
        Document doc = Jsoup.parse("a&qzxjklw;b");
        assertEquals("a&qzxjklw;b", doc.body().text());
    }

    // consumeCharacterReference named branch: nameRef.length()==0 with semicolon present -> literal kept
    @Test
    public void testParse_emptyEntityNameWithSemicolon_keepsLiteral() throws Throwable {
        Document doc = Jsoup.parse("a&;b");
        assertEquals("a&;b", doc.body().text());
    }

    // consumeCharacterReference: reader.isEmpty() branch, ampersand at EOF -> literal kept
    @Test
    public void testParse_ampersandAtEOF_keepsLiteralAmp() throws Throwable {
        Document doc = Jsoup.parse("a&");
        assertEquals("a&", doc.body().text());
    }

    // consumeCharacterReference: matchesAny('&') branch triggers null return, both literals kept
    @Test
    public void testParse_ampersandFollowedByAmpersand_keepsLiteral() throws Throwable {
        Document doc = Jsoup.parse("x&&y");
        assertEquals("x&&y", doc.body().text());
    }

    // numbered branch: numRef.length()==0 -> characterReferenceError, rewind, literal kept
    @Test
    public void testParse_numericEntityMissingDigits_keepsLiteral() throws Throwable {
        Document doc = Jsoup.parse("a&#;b");
        assertEquals("a&#;b", doc.body().text());
    }

    // numbered branch decimal: valid charval -> correct character returned
    @Test
    public void testParse_numericDecimalEntity_decodesCorrectCharacter() throws Throwable {
        Document doc = Jsoup.parse("a&#65;b");
        assertEquals("aAb", doc.body().text());
    }

    // numbered branch hex lowercase x: isHexMode true via matchConsumeIgnoreCase
    @Test
    public void testParse_numericHexEntityLowercaseX_decodesCorrectCharacter() throws Throwable {
        Document doc = Jsoup.parse("a&#x41;b");
        assertEquals("aAb", doc.body().text());
    }

    // numbered branch hex uppercase X: isHexMode true, case-insensitive marker
    @Test
    public void testParse_numericHexEntityUppercaseX_decodesCorrectCharacter() throws Throwable {
        Document doc = Jsoup.parse("a&#X41;b");
        assertEquals("aAb", doc.body().text());
    }



    // numbered branch: charval in surrogate range (0xD800-0xDFFF) -> replacementChar
    @Test
    public void testParse_numericEntitySurrogateRange_returnsReplacementChar() throws Throwable {
        Document doc = Jsoup.parse("a&#55296;b");
        assertEquals("a\uFFFDb", doc.body().text());
    }

    // numbered branch: charval > 0x10FFFF -> replacementChar
    @Test
    public void testParse_numericEntityOverMaxUnicode_returnsReplacementChar() throws Throwable {
        Document doc = Jsoup.parse("a&#1114112;b");
        assertEquals("a\uFFFDb", doc.body().text());
    }

    // numbered branch: NumberFormatException on overflow -> charval stays -1 -> replacementChar
    @Test
    public void testParse_numericEntityOverflowNumber_returnsReplacementChar() throws Throwable {
        Document doc = Jsoup.parse("a&#99999999999999999999;b");
        assertEquals("a\uFFFDb", doc.body().text());
    }

    // start tag parsing / createTagPending(true), attribute value with entity decoding in attribute context
    @Test
    public void testParse_attributeWithEntityValue_decodesEntity() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"&amp;\">link</a>");
        Element a = doc.body().child(0);
        assertEquals("&", a.attr("href"));
    }

    // consumeCharacterReference: inAttribute && matchesLetter after found entity -> ambiguous amp, rewind, literal kept
    @Test
    public void testParse_ambiguousAmpInAttribute_keepsLiteral() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"&amplitude\">link</a>");
        Element a = doc.body().child(0);
        assertEquals("&amplitude", a.attr("href"));
    }

    // self-closing tag: attribute parsing plus selfClosing flag handling for void element
    @Test
    public void testParse_selfClosingTag_parsesAttributes() throws Throwable {
        Document doc = Jsoup.parse("<img src=\"pic.png\"/>");
        Element img = doc.body().child(0);
        assertEquals("img", img.tagName());
        assertEquals("pic.png", img.attr("src"));
    }

    // emit(Token.EndTag) branch: end tag with attributes triggers error() but must not throw and still parses
    @Test
    public void testParse_endTagWithAttributes_doesNotThrowAndParses() throws Throwable {
        Document doc = Jsoup.parse("<div></div extra=\"val\">");
        Element div = doc.body().child(0);
        assertEquals("div", div.tagName());
    }





    // RCDATA state (title): character references ARE decoded
    @Test
    public void testParse_titleTagContent_isEntityDecoded() throws Throwable {
        Document doc = Jsoup.parse("<html><head><title>1 &lt; 2</title></head><body></body></html>");
        assertEquals("1 < 2", doc.title());
    }

    // unquoted attribute value parsing path
    @Test
    public void testParse_unquotedAttributeValue_parsedCorrectly() throws Throwable {
        Document doc = Jsoup.parse("<a href=foo>bar</a>");
        Element a = doc.body().child(0);
        assertEquals("foo", a.attr("href"));
    }

    // single-quoted attribute value parsing path
    @Test
    public void testParse_singleQuotedAttributeValue_parsedCorrectly() throws Throwable {
        Document doc = Jsoup.parse("<a href='foo'>bar</a>");
        Element a = doc.body().child(0);
        assertEquals("foo", a.attr("href"));
    }

    // parseBodyFragment entry point basic sanity
    @Test
    public void testParseBodyFragment_returnsBodyWithContent() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<p>Hello</p>");
        assertEquals("Hello", doc.body().text());
    }

    // multiple attributes parsed on a single start tag (loop multiple iterations)
    @Test
    public void testParse_multipleAttributes_allParsed() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"x\" title=\"y\">z</a>");
        Element a = doc.body().child(0);
        assertEquals("x", a.attr("href"));
        assertEquals("y", a.attr("title"));
    }

    // nested tags: charBuffer emission across multiple text tokens/elements concatenates correctly
    @Test
    public void testParse_nestedTags_textConcatenated() throws Throwable {
        Document doc = Jsoup.parse("<div><span>foo</span> bar</div>");
        assertEquals("foo bar", doc.body().text());
    }
}
