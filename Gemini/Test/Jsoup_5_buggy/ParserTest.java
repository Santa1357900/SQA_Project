package org.jsoup.parser;

import org.jsoup.nodes.Document;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParserTest {

    @Test
    public void testParseBasicHtml() throws Throwable {
        String html = "<html><head><title>Test</title></head><body><p>Hello World</p></body></html>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("Test", doc.title());
        assertEquals("Hello World", doc.select("p").first().text());
    }

    @Test
    public void testParseBodyFragment() throws Throwable {
        String html = "<p>Body Fragment</p>";
        Document doc = Parser.parseBodyFragment(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("Body Fragment", doc.body().text());
    }

    @Test
    public void testParseBodyFragmentRelaxed() throws Throwable {
        String html = "<div>Relaxed Fragment</div>";
        Document doc = Parser.parseBodyFragmentRelaxed(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("Relaxed Fragment", doc.body().text());
    }

    @Test
    public void testParseComment() throws Throwable {
        String html = "<div><!-- This is a comment --><span>Text</span></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("Text", doc.select("span").first().text());
    }

    @Test
    public void testParseCommentWithHyphen() throws Throwable {
        String html = "<div><!-- Comment with - hyphen --></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
    }

    @Test
    public void testParseXmlDeclaration() throws Throwable {
        String html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
    }

    @Test
    public void testParseCdata() throws Throwable {
        String html = "<div><![CDATA[ some cdata content ]]></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.text().contains("some cdata content"));
    }

    @Test
    public void testParseEndTag() throws Throwable {
        String html = "<div><p>Paragraph</p></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("Paragraph", doc.select("p").first().text());
    }

    @Test
    public void testParseAttributesVariants() throws Throwable {
        String html = "<div id='id1' class=\"class1\" data-test=val unquoted attrWithoutVal></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("id1", doc.select("div").first().id());
        assertEquals("class1", doc.select("div").first().attr("class"));
        assertEquals("val", doc.select("div").first().attr("data-test"));
        assertEquals("unquoted", doc.select("div").first().attr("attrWithoutVal"));
    }

    @Test
    public void testParseSelfClosingAndEmptyTags() throws Throwable {
        String html = "<img src=\"test.jpg\" /><br/><hr>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("test.jpg", doc.select("img").first().attr("src"));
    }

    @Test
    public void testParseDataTags() throws Throwable {
        String html = "<script>var a = '<script>';</script><textarea>Some area text</textarea>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertNotNull(doc.select("script").first());
        assertEquals("Some area text", doc.select("textarea").first().text());
    }

    @Test
    public void testParseBaseHref() throws Throwable {
        String html = "<html><head><base href=\"http://example.org/path/\"></head><body><a href=\"page.html\">Link</a></body></html>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("http://example.org/path/", doc.baseUri());
    }

    @Test
    public void testParseTextNodeWithLessThan() throws Throwable {
        String html = "<div>hello < there</div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.text().contains("hello"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullHtmlThrowsException() throws Throwable {
        Parser.parse(null, "http://example.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullBaseUriThrowsException() throws Throwable {
        Parser.parse("<html></html>", null);
    }
}