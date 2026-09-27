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
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        Element p = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        div.appendChild(p);
        root.appendChild(div);

        Elements results = Selector.select("p", root);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("p", results.first().tagName());
    }

    @Test
    public void testSelectById() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        div.attr("id", "myId");
        root.appendChild(div);

        Elements results = Selector.select("#myId", root);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("myId", results.first().id());
    }

    @Test
    public void testSelectByClass() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        div.addClass("myClass");
        root.appendChild(div);

        Elements results = Selector.select(".myClass", root);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.first().hasClass("myClass"));
    }

    @Test
    public void testSelectByAttribute() throws Throwable {
        Element root = new Document("http://example.com");
        Element a = new Element(org.jsoup.parser.Tag.valueOf("a"), "http://example.com");
        a.attr("href", "http://jsoup.org");
        a.attr("data-test", "val1");
        root.appendChild(a);

        Elements r1 = Selector.select("[href]", root);
        assertEquals(1, r1.size());

        Elements r2 = Selector.select("[data-test=val1]", root);
        assertEquals(1, r2.size());

        Elements r3 = Selector.select("[data-test!=val2]", root);
        assertEquals(1, r3.size());

        Elements r4 = Selector.select("[data-test^=val]", root);
        assertEquals(1, r4.size());

        Elements r5 = Selector.select("[data-test$=1]", root);
        assertEquals(1, r5.size());

        Elements r6 = Selector.select("[data-test*=al]", root);
        assertEquals(1, r6.size());

        Elements r7 = Selector.select("[data-test~=val\\d]", root);
        assertEquals(1, r7.size());
        
        Elements r8 = Selector.select("[^data-]", root);
        assertEquals(1, r8.size());
    }

    @Test
    public void testSelectAllElements() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        root.appendChild(div);

        Elements results = Selector.select("*", root);
        assertTrue(results.size() >= 1);
    }

    @Test
    public void testCombinatorDescendant() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        Element p = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        div.appendChild(p);
        root.appendChild(div);

        Elements results = Selector.select("div p", root);
        assertEquals(1, results.size());
        assertEquals("p", results.first().tagName());
    }

    @Test
    public void testCombinatorChild() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        Element span = new Element(org.jsoup.parser.Tag.valueOf("span"), "http://example.com");
        Element p = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        span.appendChild(p);
        div.appendChild(span);
        root.appendChild(div);

        Elements results = Selector.select("div > p", root);
        assertEquals(0, results.size());

        Elements results2 = Selector.select("div > span", root);
        assertEquals(1, results2.size());
    }

    @Test
    public void testCombinatorAdjacentSibling() throws Throwable {
        Element root = new Document("http://example.com");
        Element p1 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        Element p2 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        root.appendChild(p1);
        root.appendChild(p2);

        Elements results = Selector.select("p + p", root);
        assertEquals(1, results.size());
    }

    @Test
    public void testCombinatorGeneralSibling() throws Throwable {
        Element root = new Document("http://example.com");
        Element h1 = new Element(org.jsoup.parser.Tag.valueOf("h1"), "http://example.com");
        Element p1 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        Element p2 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        root.appendChild(h1);
        root.appendChild(p1);
        root.appendChild(p2);

        Elements results = Selector.select("h1 ~ p", root);
        assertEquals(2, results.size());
    }

    @Test
    public void testGroupSelector() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        Element span = new Element(org.jsoup.parser.Tag.valueOf("span"), "http://example.com");
        root.appendChild(div);
        root.appendChild(span);

        Elements results = Selector.select("div, span", root);
        assertEquals(2, results.size());
    }

    @Test
    public void testIndexPseudoSelectors() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        Element p1 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        Element p2 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        Element p3 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        div.appendChild(p1);
        div.appendChild(p2);
        div.appendChild(p3);
        root.appendChild(div);

        Elements lt = Selector.select("p:lt(2)", div);
        assertEquals(2, lt.size());

        Elements gt = Selector.select("p:gt(0)", div);
        assertEquals(2, gt.size());

        Elements eq = Selector.select("p:eq(1)", div);
        assertEquals(1, eq.size());
    }

    @Test
    public void testHasPseudoSelector() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        Element p = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        div.appendChild(p);
        root.appendChild(div);

        Elements results = Selector.select("div:has(p)", root);
        assertEquals(1, results.size());
        assertEquals("div", results.first().tagName());
    }

    @Test
    public void testContainsPseudoSelectors() throws Throwable {
        Element root = new Document("http://example.com");
        Element p1 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        p1.text("Hello world");
        Element p2 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        p2.text("Own text only");
        root.appendChild(p1);
        root.appendChild(p2);

        Elements c = Selector.select("p:contains(Hello)", root);
        assertEquals(1, c.size());

        Elements co = Selector.select("p:containsOwn(Only)", root);
        assertEquals(1, co.size());
    }

    @Test
    public void testMatchesPseudoSelectors() throws Throwable {
        Element root = new Document("http://example.com");
        Element p1 = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        p1.text("Item 123");
        root.appendChild(p1);

        Elements m = Selector.select("p:matches(\\d+)", root);
        assertEquals(1, m.size());

        Elements mo = Selector.select("p:matchesOwn(\\d+)", root);
        assertEquals(1, mo.size());
    }

    @Test
    public void testIterableRootsSelect() throws Throwable {
        Element root1 = new Document("http://example.com");
        Element div1 = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        root1.appendChild(div1);

        Element root2 = new Document("http://example.com");
        Element div2 = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        root2.appendChild(div2);

        List<Element> roots = new ArrayList<Element>();
        roots.add(root1);
        roots.add(root2);

        Elements results = Selector.select("div", roots);
        assertEquals(2, results.size());
    }

    @Test
    public void testNamespacedTag() throws Throwable {
        Element root = new Document("http://example.com");
        Element fb = new Element(org.jsoup.parser.Tag.valueOf("fb:name"), "http://example.com");
        root.appendChild(fb);

        Elements results = Selector.select("fb|name", root);
        assertEquals(1, results.size());
    }

    @Test
    public void testLeadingCombinator() throws Throwable {
        Element root = new Document("http://example.com");
        Element div = new Element(org.jsoup.parser.Tag.valueOf("div"), "http://example.com");
        Element p = new Element(org.jsoup.parser.Tag.valueOf("p"), "http://example.com");
        div.appendChild(p);
        root.appendChild(div);

        Elements results = Selector.select("> p", div);
        assertEquals(1, results.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidQueryNull() throws Throwable {
        Element root = new Document("http://example.com");
        Selector.select(null, root);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidQueryEmpty() throws Throwable {
        Element root = new Document("http://example.com");
        Selector.select("   ", root);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRootNull() throws Throwable {
        Selector.select("div", (Element) null);
    }

    @Test(expected = Selector.SelectorParseException.class)
    public void testSelectorParseException() throws Throwable {
        Element root = new Document("http://example.com");
        Selector.select(":unknownSelector", root);
    }
}