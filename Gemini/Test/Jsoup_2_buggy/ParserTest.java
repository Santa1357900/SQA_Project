package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;

public class ParserTest {

    @Test
    public void testParseSimpleHtml() throws Throwable {
        String html = "<html><head><title>Test</title></head><body><p>Hello World</p></body></html>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertEquals("Test", doc.title());
        assertNotNull(doc.body());
        assertTrue(doc.body().text().contains("Hello World"));
    }

    @Test
    public void testParseBodyFragment() throws Throwable {
        String fragment = "<div><span>Fragment Content</span></div>";
        Document doc = Parser.parseBodyFragment(fragment, "http://example.com");
        assertNotNull(doc);
        assertNotNull(doc.body());
        assertTrue(doc.body().text().contains("Fragment Content"));
    }

    @Test
    public void testParseComment() throws Throwable {
        String html = "<div><!-- This is a comment --><span>Text</span></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.toString().contains("This is a comment"));
    }

    @Test
    public void testParseCdata() throws Throwable {
        String html = "<div><![CDATA[ <notAtag> ]]></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.toString().contains("<notAtag>"));
    }

    @Test
    public void testParseXmlDecl() throws Throwable {
        String html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><div>Content</div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.toString().contains("xml version"));
    }

    @Test
    public void testParseAttributesAndQuotes() throws Throwable {
        String html = "<a href='http://foo.bar' title=\"baz\" class=test_class id=myid>Link</a>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        org.jsoup.nodes.Element link = doc.select("a").first();
        assertNotNull(link);
        assertEquals("http://foo.bar", link.attr("href"));
        assertEquals("baz", link.attr("title"));
        assertEquals("test_class", link.attr("class"));
        assertEquals("myid", link.attr("id"));
    }

    @Test
    public void testParseSelfClosingAndEmptyTags() throws Throwable {
        String html = "<img src=\"foo.png\" /><br /><input type=\"text\" />";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertNotNull(doc.select("img").first());
        assertNotNull(doc.select("br").first());
        assertNotNull(doc.select("input").first());
    }

    @Test
    public void testParseDataTags() throws Throwable {
        String html = "<script>var x = '</script>';</script><textarea>Some text area</textarea>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertNotNull(doc.select("script").first());
        assertNotNull(doc.select("textarea").first());
        assertEquals("Some text area", doc.select("textarea").first().text());
    }

    @Test
    public void testParseBaseTagUpdatesBaseUri() throws Throwable {
        String html = "<html><head><base href=\"http://newbase.com/\"/></head><body><a href=\"path\">Link</a></body></html>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        org.jsoup.nodes.Element link = doc.select("a").first();
        assertNotNull(link);
        assertEquals("http://newbase.com/path", link.absUrl("href"));
    }

    @Test
    public void testParseMalformedStartTag() throws Throwable {
        String html = "< >not a tag";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertTrue(doc.body().text().contains("&lt;"));
    }

    @Test
    public void testParseEndTagHandling() throws Throwable {
        String html = "<div><p>Paragraph 1</p><span>Span text</span></div>";
        Document doc = Parser.parse(html, "http://example.com");
        assertNotNull(doc);
        assertNotNull(doc.select("div").first());
        assertNotNull(doc.select("p").first());
        assertNotNull(doc.select("span").first());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullHtml() throws Throwable {
        Parser.parse(null, "http://example.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullBaseUri() throws Throwable {
        Parser.parse("<html></html>", null);
    }
}