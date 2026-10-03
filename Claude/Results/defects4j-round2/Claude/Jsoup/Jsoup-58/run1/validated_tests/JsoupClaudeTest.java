package org.jsoup;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

public class JsoupClaudeTest {

    // parse(String,baseUri): basic tag/text extraction and title parsing
    @Test
    public void testParseWithBaseUri_basicHtml_extractsTitleAndText() throws Throwable {
        Document doc = Jsoup.parse("<html><head><title>Test</title></head><body><p>Hello World</p></body></html>", "http://example.com/");
        assertEquals("Test", doc.title());
        Element p = doc.select("p").first();
        assertEquals("Hello World", p.text());
    }

    // parse(String,baseUri): unclosed <p> tags are balanced into siblings per HTML5 rules
    @Test
    public void testParseWithBaseUri_unclosedTags_balancesTreeIntoTwoParagraphs() throws Throwable {
        Document doc = Jsoup.parse("<div><p>One<p>Two", "http://example.com/");
        Elements ps = doc.select("p");
        assertEquals(2, ps.size());
        assertEquals("One", ps.get(0).text());
    }

    // parse(String,baseUri): relative href resolved to absolute url using base
    @Test
    public void testParseWithBaseUri_relativeHref_resolvesToAbsoluteUrl() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>", "http://example.com/bar/");
        Element a = doc.select("a").first();
        assertEquals("http://example.com/foo", a.absUrl("href"));
    }

    // parse(String,baseUri): protocol-relative href resolves using base's scheme
    @Test
    public void testParseWithBaseUri_protocolRelativeHref_resolvesUsingBaseProtocol() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"//cdn.example.com/x.js\">x</a>", "https://example.com/");
        Element a = doc.select("a").first();
        assertEquals("https://cdn.example.com/x.js", a.absUrl("href"));
    }

    // parse(String,baseUri): already-absolute href remains unchanged
    @Test
    public void testParseWithBaseUri_absoluteHref_staysUnchanged() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"http://other.com/page\">x</a>", "http://example.com/");
        Element a = doc.select("a").first();
        assertEquals("http://other.com/page", a.absUrl("href"));
    }

    // parse(String,baseUri): query-only relative href keeps base path, replaces query
    @Test
    public void testParseWithBaseUri_queryOnlyHref_resolvesAgainstBasePath() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"?x=1\">x</a>", "http://example.com/dir/page.html");
        Element a = doc.select("a").first();
        assertEquals("http://example.com/dir/page.html?x=1", a.absUrl("href"));
    }

    // parse(String,baseUri): document without title tag returns empty title
    @Test
    public void testParseWithBaseUri_noTitleTag_returnsEmptyTitle() throws Throwable {
        Document doc = Jsoup.parse("<p>No title</p>", "http://example.com/");
        assertEquals("", doc.title());
    }

    // parse(String,baseUri,Parser): xmlParser preserves original tag case
    @Test
    public void testParseWithXmlParser_preservesTagCase() throws Throwable {
        Document doc = Jsoup.parse("<Data id=\"1\">Text</Data>", "", Parser.xmlParser());
        Element el = doc.select("Data").first();
        assertNotNull(el);
        assertEquals("Data", el.tagName());
    }

    // parse(String,baseUri): default html parsing lowercases tag names
    @Test
    public void testParseWithBaseUri_uppercaseTag_lowercasedByHtmlParser() throws Throwable {
        Document doc = Jsoup.parse("<DATA id=\"1\">Text</DATA>", "http://example.com/");
        Element el = doc.select("data").first();
        assertNotNull(el);
        assertEquals("data", el.tagName());
    }

    // parse(String,baseUri,Parser): explicit htmlParser behaves like default html parse
    @Test
    public void testParseWithExplicitHtmlParser_matchesDefaultParserBehavior() throws Throwable {
        Document doc = Jsoup.parse("<P>Hi</P>", "http://example.com/", Parser.htmlParser());
        Element el = doc.select("p").first();
        assertNotNull(el);
        assertEquals("p", el.tagName());
    }

    // parse(String): no baseUri means relative urls cannot resolve, absUrl returns empty
    @Test
    public void testParseNoBaseUri_relativeHref_absUrlEmpty() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>");
        Element a = doc.select("a").first();
        assertEquals("", a.absUrl("href"));
    }

    // parse(String): empty html yields empty body text
    @Test
    public void testParseNoBaseUri_emptyHtml_producesEmptyBodyText() throws Throwable {
        Document doc = Jsoup.parse("");
        assertEquals("", doc.body().text());
    }

    // connect(String): valid http url returns a non-null Connection without network I/O
    @Test
    public void testConnect_validHttpUrl_returnsNonNullConnection() throws Throwable {
        Connection con = Jsoup.connect("http://example.com");
        assertNotNull(con);
    }

    // parseBodyFragment(bodyHtml,baseUri): elements wrapped as children of <body>
    @Test
    public void testParseBodyFragmentWithBaseUri_wrapsElementsInBody() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<p>One</p><p>Two</p>", "http://example.com/");
        Elements ps = doc.body().children();
        assertEquals(2, ps.size());
        assertEquals("p", ps.first().tagName());
    }

    // parseBodyFragment(bodyHtml,baseUri): relative href resolved using given baseUri
    @Test
    public void testParseBodyFragmentWithBaseUri_relativeHref_resolvesAbsolute() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<a href=\"/x\">link</a>", "http://example.com/");
        Element a = doc.select("a").first();
        assertEquals("http://example.com/x", a.absUrl("href"));
    }

    // parseBodyFragment(bodyHtml): default baseUri "" means absUrl cannot resolve
    @Test
    public void testParseBodyFragmentNoBaseUri_relativeHref_absUrlEmpty() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<a href=\"/x\">link</a>");
        Element a = doc.select("a").first();
        assertEquals("", a.absUrl("href"));
    }

    // clean(bodyHtml,baseUri,whitelist): Whitelist.none() strips all tags but keeps text
    @Test
    public void testClean_withNoneWhitelist_stripsAllTagsKeepsText() throws Throwable {
        String cleaned = Jsoup.clean("<p>Hello <b>World</b></p>", "http://example.com/", Whitelist.none());
        assertFalse(cleaned.contains("<b>"));
        assertFalse(cleaned.contains("<p>"));
        assertTrue(cleaned.contains("Hello"));
        assertTrue(cleaned.contains("World"));
    }

    // clean(bodyHtml,baseUri,whitelist): Whitelist.basic() keeps allowed tags like <b> and <p>
    @Test
    public void testClean_withBasicWhitelist_keepsAllowedTags() throws Throwable {
        String cleaned = Jsoup.clean("<p>Hello <b>World</b></p>", "http://example.com/", Whitelist.basic());
        assertTrue(cleaned.contains("<b>World</b>"));
        assertTrue(cleaned.contains("<p>"));
    }

    // clean(bodyHtml,baseUri,whitelist): disallowed <script> tag is stripped, content unaffected
    @Test
    public void testClean_scriptTagNotInWhitelist_isStripped() throws Throwable {
        String cleaned = Jsoup.clean("<script>alert(1)</script><p>Safe</p>", "http://example.com/", Whitelist.basic());
        assertFalse(cleaned.contains("<script>"));
        assertTrue(cleaned.contains("Safe"));
    }

    // clean(bodyHtml,whitelist): overload with default "" baseUri matches explicit "" call
    @Test
    public void testCleanOverload_defaultBaseUri_matchesExplicitEmptyBaseUri() throws Throwable {
        String a = Jsoup.clean("<p>Test</p>", Whitelist.basic());
        String b = Jsoup.clean("<p>Test</p>", "", Whitelist.basic());
        assertEquals(b, a);
    }

    // clean(bodyHtml,baseUri,whitelist,outputSettings): prettyPrint(false) avoids newlines
    @Test
    public void testCleanWithOutputSettings_prettyPrintFalse_noNewlineInOutput() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.prettyPrint(false);
        String cleaned = Jsoup.clean("<p>One</p><p>Two</p>", "http://example.com/", Whitelist.basic(), settings);
        assertFalse(cleaned.contains("\n"));
    }

    // clean(bodyHtml,baseUri,whitelist,outputSettings): prettyPrint(true) adds newline between blocks
    @Test
    public void testCleanWithOutputSettings_prettyPrintTrue_containsNewline() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.prettyPrint(true);
        String cleaned = Jsoup.clean("<p>One</p><p>Two</p>", "http://example.com/", Whitelist.basic(), settings);
        assertTrue(cleaned.contains("\n"));
    }

    // isValid(bodyHtml,whitelist): only allowed tags present returns true
    @Test
    public void testIsValid_allowedTagsOnly_returnsTrue() throws Throwable {
        boolean valid = Jsoup.isValid("<p>Hello <b>World</b></p>", Whitelist.basic());
        assertTrue(valid);
    }

    // isValid(bodyHtml,whitelist): disallowed tag causes false
    @Test
    public void testIsValid_disallowedTag_returnsFalse() throws Throwable {
        boolean valid = Jsoup.isValid("<script>alert(1)</script>", Whitelist.basic());
        assertFalse(valid);
    }

    // isValid(bodyHtml,whitelist): Whitelist.none() with plain text (no tags) is valid
    @Test
    public void testIsValid_noneWhitelistPlainText_returnsTrue() throws Throwable {
        boolean valid = Jsoup.isValid("Hello World", Whitelist.none());
        assertTrue(valid);
    }

    // isValid(bodyHtml,whitelist): Whitelist.none() with any tag present is invalid
    @Test
    public void testIsValid_noneWhitelistWithTag_returnsFalse() throws Throwable {
        boolean valid = Jsoup.isValid("<p>Hello</p>", Whitelist.none());
        assertFalse(valid);
    }
}
