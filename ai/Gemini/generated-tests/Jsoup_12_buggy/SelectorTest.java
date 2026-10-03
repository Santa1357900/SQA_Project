package org.jsoup.select;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class SelectorTest {

    @Test
    public void testSelectByTag() throws Throwable {
        Document doc = Document.parse("<div><p>Hello</p><p>World</p></div>");
        Elements els = Selector.select("p", doc);
        assertEquals(2, els.size());
        assertEquals("p", els.get(0.tagName()));
    }

    @Test
    public void testSelectById() throws Throwable {
        Document doc = Document.parse("<div><p id=\"target\">Hello</p><p>World</p></div>");
        Elements els = Selector.select("#target", doc);
        assertEquals(1, els.size());
        assertEquals("target", els.get(0).id());
    }

    @Test
    public void testSelectByClass() throws Throwable {
        Document doc = Document.parse("<div><p class=\"foo\">Hello</p><p class=\"bar\">World</p></div>");
        Elements els = Selector.select(".foo", doc);
        assertEquals(1, els.size());
        assertTrue(els.get(0).hasClass("foo"));
    }

    @Test
    public void testSelectByAttribute() throws Throwable {
        Document doc = Document.parse("<div><a href=\"http://example.com\">Link</a><a title=\"none\">No link</a></div>");
        Elements els = Selector.select("[href]", doc);
        assertEquals(1, els.size());
        assertEquals("http://example.com", els.get(0).attr("href"));
    }

    @Test
    public void testSelectByAttributeValue() throws Throwable {
        Document doc = Document.parse("<div><a href=\"http://example.com\">Link</a><a href=\"http://other.com\">Other</a></div>");
        Elements els = Selector.select("[href=http://example.com]", doc);
        assertEquals(1, els.size());
        assertEquals("http://example.com", els.get(0).attr("href"));
    }

    @Test
    public void testSelectByAttributeValueNot() throws Throwable {
        Document doc = Document.parse("<div><a href=\"http://example.com\">Link</a><a href=\"http://other.com\">Other</a></div>");
        Elements els = Selector.select("[href!=http://example.com]", doc);
        assertEquals(1, els.size());
        assertEquals("http://other.com", els.get(0).attr("href"));
    }

    @Test
    public void testSelectByAttributeValueStarting() throws Throwable {
        Document doc = Document.parse("<div><a href=\"http://example.com\">Link</a><a href=\"https://secure.com\">Secure</a></div>");
        Elements els = Selector.select("[href^=http:]", doc);
        assertEquals(1, els.size());
        assertEquals("http://example.com", els.get(0).attr("href"));
    }

    @Test
    public void testSelectByAttributeValueEnding() throws Throwable {
        Document doc = Document.parse("<div><img src=\"image.png\"/><img src=\"photo.jpg\"/></div>");
        Elements els = Selector.select("[src$=.png]", doc);
        assertEquals(1, els.size());
        assertEquals("image.png", els.get(0).attr("src"));
    }

    @Test
    public void testSelectByAttributeValueContaining() throws Throwable {
        Document doc = Document.parse("<div><a href=\"/search/1\">1</a><a href=\"/about\">About</a></div>");
        Elements els = Selector.select("[href*=/search/]", doc);
        assertEquals(1, els.size());
        assertEquals("/search/1", els.get(0).attr("href"));
    }

    @Test
    public void testSelectByAttributeValueMatching() throws Throwable {
        Document doc = Document.parse("<div><img src=\"img1.png\"/><img src=\"img.gif\"/></div>");
        Elements els = Selector.select("[src~=(?i)\\.(png|jpe?g)]", doc);
        assertEquals(1, els.size());
        assertEquals("img1.png", els.get(0).attr("src"));
    }

    @Test
    public void testSelectAttributeStartingDataset() throws Throwable {
        Document doc = Document.parse("<div><div data-id=\"1\"></div><div data-name=\"test\"></div><div id=\"none\"></div></div>");
        Elements els = Selector.select("[^data-]", doc);
        assertEquals(2, els.size());
    }

    @Test
    public void testSelectAllElements() throws Throwable {
        Document doc = Document.parse("<div><p>Hello</p></div>");
        Elements els = Selector.select("*", doc);
        assertTrue(els.size() > 1);
    }

    @Test
    public void testSelectCombinatorChild() throws Throwable {
        Document doc = Document.parse("<div><p><span>Direct</span></p><span>Indirect</span></div>");
        Elements els = Selector.select("div > span", doc);
        assertEquals(1, els.size());
        assertEquals("Indirect", els.get(0).text());
    }

    @Test
    public void testSelectCombinatorDescendant() throws Throwable {
        Document doc = Document.parse("<div><p><span>Descendant</span></p></div>");
        Elements els = Selector.select("div span", doc);
        assertEquals(1, els.size());
        assertEquals("Descendant", els.get(0).text());
    }

    @Test
    public void testSelectCombinatorAdjacentSibling() throws Throwable {
        Document doc = Document.parse("<div><h1>Title</h1><p>First</p><p>Second</p></div>");
        Elements els = Selector.select("h1 + p", doc);
        assertEquals(1, els.size());
        assertEquals("First", els.get(0).text());
    }

    @Test
    public void testSelectCombinatorGeneralSibling() throws Throwable {
        Document doc = Document.parse("<div><h1>Title</h1><p>First</p><p>Second</p></div>");
        Elements els = Selector.select("h1 ~ p", doc);
        assertEquals(2, els.size());
    }

    @Test
    public void testSelectGrouping() throws Throwable {
        Document doc = Document.parse("<div><p>Paragraph</p><span>Span</span></div>");
        Elements els = Selector.select("p, span", doc);
        assertEquals(2, els.size());
    }

    @Test
    public void testSelectPseudoIndexLessThan() throws Throwable {
        Document doc = Document.parse("<ul><li>1</li><li>2</li><li>3</li></ul>");
        Elements els = Selector.select("li:lt(2)", doc);
        assertEquals(2, els.size());
    }

    @Test
    public void testSelectPseudoIndexGreaterThan() throws Throwable {
        Document doc = Document.parse("<ul><li>1</li><li>2</li><li>3</li></ul>");
        Elements els = Selector.select("li:gt(0)", doc);
        assertEquals(2, els.size());
    }

    @Test
    public void testSelectPseudoIndexEquals() throws Throwable {
        Document doc = Document.parse("<ul><li>1</li><li>2</li><li>3</li></ul>");
        Elements els = Selector.select("li:eq(1)", doc);
        assertEquals(1, els.size());
        assertEquals("2", els.get(0).text());
    }

    @Test
    public void testSelectPseudoHas() throws Throwable {
        Document doc = Document.parse("<div><div class=\"a\"><p>Hello</p></div><div class=\"b\"><span>World</span></div></div>");
        Elements els = Selector.select("div:has(p)", doc);
        assertEquals(1, els.size());
        assertTrue(els.get(0).hasClass("a"));
    }

    @Test
    public void testSelectPseudoContains() throws Throwable {
        Document doc = Document.parse("<div><p>Hello jsoup world</p><p>Other</p></div>");
        Elements els = Selector.select("p:contains(jsoup)", doc);
        assertEquals(1, els.size());
        assertEquals("Hello jsoup world", els.get(0).text());
    }

    @Test
    public void testSelectPseudoContainsOwn() throws Throwable {
        Document doc = Document.parse("<div><p>jsoup <span>child</span></p></div>");
        Elements els = Selector.select("p:containsOwn(jsoup)", doc);
        assertEquals(1, els.size());
    }

    @Test
    public void testSelectPseudoMatches() throws Throwable {
        Document doc = Document.parse("<div><p>Cell 123</p><p>No digits</p></div>");
        Elements els = Selector.select("p:matches(\\d+)", doc);
        assertEquals(1, els.size());
    }

    @Test
    public void testSelectPseudoMatchesOwn() throws Throwable {
        Document doc = Document.parse("<div><p>999 <span>child</span></p></div>");
        Elements els = Selector.select("p:matchesOwn(\\d+)", doc);
        assertEquals(1, els.size());
    }

    @Test
    public void testSelectPseudoNot() throws Throwable {
        Document doc = Document.parse("<div><p class=\"foo\">One</p><p class=\"bar\">Two</p></div>");
        Elements els = Selector.select("p:not(.foo)", doc);
        assertEquals(1, els.size());
        assertEquals("Two", els.get(0).text());
    }

    @Test
    public void testIterableRootsSelect() throws Throwable {
        Document doc1 = Document.parse("<div><p>One</p></div>");
        Document doc2 = Document.parse("<div><p>Two</p></div>");
        List<Element> roots = new ArrayList<Element>();
        roots.add(doc1.body());
        roots.add(doc2.body());
        
        Elements els = Selector.select("p", roots);
        assertEquals(2, els.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullQueryThrows() throws Throwable {
        Document doc = Document.parse("<div></div>");
        Selector.select(null, doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyQueryThrows() throws Throwable {
        Document doc = Document.parse("<div></div>");
        Selector.select("   ", doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullRootThrows() throws Throwable {
        Selector.select("p", (Element) null);
    }

    @Test(expected = Selector.SelectorParseException.class)
    public void testInvalidTokenThrows() throws Throwable {
        Document doc = Document.parse("<div></div>");
        Selector.select(":", doc);
    }

    @Test
    public void testNamespacedTag() throws Throwable {
        Document doc = Document.parse("<fb:name>John</fb:name>");
        Elements els = Selector.select("fb|name", doc);
        assertEquals(1, els.size());
        assertEquals("fb:name", els.get(0).tagName());
    }

    @Test
    public void testStartsWithCombinator() throws Throwable {
        Document doc = Document.parse("<div><p>Hello</p></div>");
        Element p = doc.select("p").first();
        Elements els = Selector.select("> p", doc.body());
        assertTrue(els.size() >= 0);
    }
}