package org.jsoup.select;

import static org.junit.Assert.*;
import org.junit.Test;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

public class SelectorClaudeTest {

    // universal selector "*" should include matching descendant tags
    @Test
    public void testSelect_universalSelector_returnsAllElements() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><span>2</span></div>");
        Element root = doc.body();
        Elements found = Selector.select("*", root);
        boolean hasP = false, hasSpan = false;
        for (int i = 0; i < found.size(); i++) {
            String tag = found.get(i).tagName();
            if ("p".equals(tag)) hasP = true;
            if ("span".equals(tag)) hasSpan = true;
        }
        assertTrue(hasP);
        assertTrue(hasSpan);
    }

    // tag selector matches all elements with given tag
    @Test
    public void testSelect_tagSelector_returnsMatchingElements() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><span>3</span></div>");
        Element root = doc.body();
        Elements found = Selector.select("p", root);
        assertEquals(2, found.size());
    }

    // tag selector is case insensitive
    @Test
    public void testSelect_tagSelector_caseInsensitive_matches() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p></div>");
        Element root = doc.body();
        Elements found = Selector.select("P", root);
        assertEquals(2, found.size());
    }

    // id selector finds element with matching id
    @Test
    public void testSelect_idSelector_returnsElementWithId() throws Throwable {
        Document doc = Jsoup.parse("<div id='main'><p id='one'>Text</p></div>");
        Element root = doc.body();
        Elements found = Selector.select("#one", root);
        assertEquals(1, found.size());
        assertEquals("p", found.get(0).tagName());
    }

    // id selector with no match returns empty
    @Test
    public void testSelect_idSelector_noMatch_returnsEmpty() throws Throwable {
        Document doc = Jsoup.parse("<div id='main'></div>");
        Element root = doc.body();
        Elements found = Selector.select("#missing", root);
        assertEquals(0, found.size());
    }

    // empty id after '#' throws IllegalArgumentException via Validate.notEmpty
    @Test
    public void testSelect_idSelector_emptyId_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        Element root = doc.body();
        try {
            Selector.select("#", root);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // class selector matches elements with given class
    @Test
    public void testSelect_classSelector_returnsMatchingElements() throws Throwable {
        Document doc = Jsoup.parse("<p class='a b'>1</p><p class='b'>2</p>");
        Element root = doc.body();
        Elements found = Selector.select(".b", root);
        assertEquals(2, found.size());
    }

    // class selector matches only elements having that class
    @Test
    public void testSelect_classSelector_onlyMatchingClass() throws Throwable {
        Document doc = Jsoup.parse("<p class='a b'>1</p><p class='b'>2</p>");
        Element root = doc.body();
        Elements found = Selector.select(".a", root);
        assertEquals(1, found.size());
    }

    // attribute exists selector
    @Test
    public void testSelect_attributeExists_returnsElementsWithAttr() throws Throwable {
        Document doc = Jsoup.parse("<a href='x'>1</a><a>2</a>");
        Element root = doc.body();
        Elements found = Selector.select("a[href]", root);
        assertEquals(1, found.size());
    }

    // attribute name prefix selector [^data-]
    @Test
    public void testSelect_attributePrefix_returnsElementsWithPrefix() throws Throwable {
        Document doc = Jsoup.parse("<div data-foo='1' other='3'></div>");
        Element root = doc.body();
        Elements found = Selector.select("div[^data-]", root);
        assertEquals(1, found.size());
        assertEquals("div", found.get(0).tagName());
    }

    // attribute value equals
    @Test
    public void testSelect_attributeValueEquals_matchesExactValue() throws Throwable {
        Document doc = Jsoup.parse("<a href='http://example.com'>1</a><a href='other'>2</a>");
        Element root = doc.body();
        Elements found = Selector.select("a[href=http://example.com]", root);
        assertEquals(1, found.size());
        assertEquals("http://example.com", found.get(0).attr("href"));
    }

    // attribute value not equals excludes matching value, includes other values
    @Test
    public void testSelect_attributeValueNotEquals_excludesGivenValue() throws Throwable {
        Document doc = Jsoup.parse("<a href='x'>1</a><a href='y'>2</a>");
        Element root = doc.body();
        Elements found = Selector.select("a[href!=x]", root);
        assertEquals(1, found.size());
        assertEquals("y", found.get(0).attr("href"));
    }

    // attribute value starting with prefix
    @Test
    public void testSelect_attributeValueStarting_matchesPrefix() throws Throwable {
        Document doc = Jsoup.parse("<a href='http://example.com'>1</a><a href='ftp://example.com'>2</a>");
        Element root = doc.body();
        Elements found = Selector.select("a[href^=http]", root);
        assertEquals(1, found.size());
        assertEquals("http://example.com", found.get(0).attr("href"));
    }

    // attribute value ending with suffix
    @Test
    public void testSelect_attributeValueEnding_matchesSuffix() throws Throwable {
        Document doc = Jsoup.parse("<img src='pic.png'><img src='pic.jpg'>");
        Element root = doc.body();
        Elements found = Selector.select("img[src$=.png]", root);
        assertEquals(1, found.size());
        assertEquals("pic.png", found.get(0).attr("src"));
    }

    // attribute value containing substring
    @Test
    public void testSelect_attributeValueContaining_matchesSubstring() throws Throwable {
        Document doc = Jsoup.parse("<a href='/search/foo'>1</a><a href='/other'>2</a>");
        Element root = doc.body();
        Elements found = Selector.select("a[href*=/search/]", root);
        assertEquals(1, found.size());
        assertEquals("/search/foo", found.get(0).attr("href"));
    }

    // attribute value matching regex
    @Test
    public void testSelect_attributeValueMatchingRegex_matchesPattern() throws Throwable {
        Document doc = Jsoup.parse("<img src='pic.png'><img src='pic.txt'>");
        Element root = doc.body();
        Elements found = Selector.select("img[src~=(?i)\\.(png|jpe?g)]", root);
        assertEquals(1, found.size());
        assertEquals("pic.png", found.get(0).attr("src"));
    }

    // :lt(n) - index less than n
    @Test
    public void testSelect_indexLessThan_returnsElementsBeforeIndex() throws Throwable {
        Document doc = Jsoup.parse("<ul><li>a</li><li>b</li><li>c</li></ul>");
        Element root = doc.body();
        Elements found = Selector.select("li:lt(2)", root);
        assertEquals(2, found.size());
        boolean hasA = false, hasB = false;
        for (int i = 0; i < found.size(); i++) {
            String t = found.get(i).text();
            if ("a".equals(t)) hasA = true;
            if ("b".equals(t)) hasB = true;
        }
        assertTrue(hasA);
        assertTrue(hasB);
    }

    // :gt(n) - index greater than n
    @Test
    public void testSelect_indexGreaterThan_returnsElementsAfterIndex() throws Throwable {
        Document doc = Jsoup.parse("<ul><li>a</li><li>b</li><li>c</li></ul>");
        Element root = doc.body();
        Elements found = Selector.select("li:gt(0)", root);
        assertEquals(2, found.size());
    }

    // :eq(n) - index equals n
    @Test
    public void testSelect_indexEquals_returnsSingleElementAtIndex() throws Throwable {
        Document doc = Jsoup.parse("<ul><li>a</li><li>b</li><li>c</li></ul>");
        Element root = doc.body();
        Elements found = Selector.select("li:eq(1)", root);
        assertEquals(1, found.size());
        assertEquals("b", found.get(0).text());
    }

    // non-numeric index throws IllegalArgumentException
    @Test
    public void testSelect_indexNonNumeric_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<ul><li>a</li></ul>");
        Element root = doc.body();
        try {
            Selector.select("li:lt(abc)", root);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // :has(selector) finds elements containing a descendant match
    @Test
    public void testSelect_has_descendantMatches_returnsParent() throws Throwable {
        Document doc = Jsoup.parse("<div><p>Has</p></div><div>Empty</div>");
        Element root = doc.body();
        Elements found = Selector.select("div:has(p)", root);
        assertEquals(1, found.size());
    }

    // :has(selector) with no descendant match returns empty
    @Test
    public void testSelect_has_noMatch_returnsEmpty() throws Throwable {
        Document doc = Jsoup.parse("<div><p>Has</p></div>");
        Element root = doc.body();
        Elements found = Selector.select("div:has(span)", root);
        assertEquals(0, found.size());
    }

    // :has() empty subquery throws IllegalArgumentException
    @Test
    public void testSelect_has_emptySubquery_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<div><p>Has</p></div>");
        Element root = doc.body();
        try {
            Selector.select("div:has()", root);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // :contains(text) matches elements whose descendant text contains string
    @Test
    public void testSelect_contains_matchesTextInDescendants() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello jsoup world</p><p>Other text</p>");
        Element root = doc.body();
        Elements found = Selector.select("p:contains(jsoup)", root);
        assertEquals(1, found.size());
    }

    // :containsOwn(text) matches only own text, not descendant text
    @Test
    public void testSelect_containsOwn_matchesOnlyOwnText() throws Throwable {
        Document doc = Jsoup.parse("<div>Outer<p>Inner jsoup</p></div>");
        Element root = doc.body();
        Elements divMatch = Selector.select("div:containsOwn(jsoup)", root);
        assertEquals(0, divMatch.size());
        Elements pMatch = Selector.select("p:containsOwn(jsoup)", root);
        assertEquals(1, pMatch.size());
    }

    // :matches(regex) matches text within descendants
    @Test
    public void testSelect_matches_regexMatchesDescendantText() throws Throwable {
        Document doc = Jsoup.parse("<td>123</td><td>abc</td>");
        Element root = doc.body();
        Elements found = Selector.select("td:matches(\\d+)", root);
        assertEquals(1, found.size());
        assertEquals("123", found.get(0).text());
    }

    // :matchesOwn(regex) matches only own text
    @Test
    public void testSelect_matchesOwn_regexMatchesOwnTextOnly() throws Throwable {
        Document doc = Jsoup.parse("<div>abc<span>123</span></div>");
        Element root = doc.body();
        Elements matchAbc = Selector.select("div:matchesOwn(abc)", root);
        assertEquals(1, matchAbc.size());
        Elements matchNum = Selector.select("div:matchesOwn(123)", root);
        assertEquals(0, matchNum.size());
    }

    // combinator '>' selects direct children only
    @Test
    public void testCombinator_childSelector_matchesDirectChildOnly() throws Throwable {
        Document doc = Jsoup.parse("<div><p>Direct</p><span><p>Nested</p></span></div>");
        Element root = doc.body();
        Elements found = Selector.select("div > p", root);
        assertEquals(1, found.size());
        assertEquals("Direct", found.get(0).text());
    }

    // combinator ' ' (descendant) matches direct and nested descendants
    @Test
    public void testCombinator_descendantSelector_matchesAllDescendants() throws Throwable {
        Document doc = Jsoup.parse("<div><p>Direct</p><span><p>Nested</p></span></div>");
        Element root = doc.body();
        Elements found = Selector.select("div p", root);
        assertEquals(2, found.size());
    }

    // combinator '+' selects immediately preceding sibling matches
    @Test
    public void testCombinator_adjacentSiblingSelector_matchesImmediateSiblingsOnly() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><p>3</p></div>");
        Element root = doc.body();
        Elements found = Selector.select("p + p", root);
        assertEquals(2, found.size());
        boolean has1 = false;
        boolean has2 = false;
        boolean has3 = false;
        for (int i = 0; i < found.size(); i++) {
            String t = found.get(i).text();
            if ("1".equals(t)) has1 = true;
            if ("2".equals(t)) has2 = true;
            if ("3".equals(t)) has3 = true;
        }
        assertFalse(has1);
        assertTrue(has2);
        assertTrue(has3);
    }

    // combinator '~' selects all following siblings
    @Test
    public void testCombinator_generalSiblingSelector_matchesFollowingSiblings() throws Throwable {
        Document doc = Jsoup.parse("<div><h1>Head</h1><p>1</p><p>2</p></div>");
        Element root = doc.body();
        Elements found = Selector.select("h1 ~ p", root);
        assertEquals(2, found.size());
    }

    // group selector ',' unions matches from multiple sub-selectors
    @Test
    public void testSelect_groupSelector_unionsMatches() throws Throwable {
        Document doc = Jsoup.parse("<div id='a'>A</div><p id='b'>B</p>");
        Element root = doc.body();
        Elements found = Selector.select("div, p", root);
        assertEquals(2, found.size());
    }

    // query starting with combinator '>' treats root as base element
    @Test
    public void testSelect_startsWithChildCombinator_treatsRootAsBase() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><span>2</span></div>");
        Element root = doc.body();
        Elements found = Selector.select("> div", root);
        assertEquals(1, found.size());
        assertEquals("div", found.get(0).tagName());
    }

    // chained AND selectors (tag.class[attr]) intersect correctly
    @Test
    public void testSelect_chainedAndSelectors_intersectsAllConditions() throws Throwable {
        Document doc = Jsoup.parse("<p class='a' title='t'>Match</p><p class='a'>NoTitle</p><p title='t'>NoClass</p>");
        Element root = doc.body();
        Elements found = Selector.select("p.a[title]", root);
        assertEquals(1, found.size());
        assertEquals("Match", found.get(0).text());
    }

    // unrecognized token throws SelectorParseException
    @Test
    public void testSelect_unknownToken_throwsSelectorParseException() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        Element root = doc.body();
        try {
            Selector.select("$invalid", root);
            fail("expected SelectorParseException");
        } catch (Selector.SelectorParseException expected) { }
    }

    // :not(...) pseudo-class is unimplemented in Selector, throws SelectorParseException
    @Test
    public void testSelect_notPseudoUnsupported_throwsSelectorParseException() throws Throwable {
        Document doc = Jsoup.parse("<div class='x'></div>");
        Element root = doc.body();
        try {
            Selector.select("div:not(.x)", root);
            fail("expected SelectorParseException");
        } catch (Selector.SelectorParseException expected) { }
    }

    // null query throws IllegalArgumentException
    @Test
    public void testSelect_nullQuery_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        Element root = doc.body();
        try {
            Selector.select((String) null, root);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // empty/whitespace query throws IllegalArgumentException
    @Test
    public void testSelect_emptyQuery_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        Element root = doc.body();
        try {
            Selector.select("   ", root);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // select with Iterable<Element> roots dedupes shared results via LinkedHashSet
    @Test
    public void testSelectIterable_multipleRootsSameElement_dedupesResults() throws Throwable {
        Document doc = Jsoup.parse("<div id='shared'><p>Same</p></div>");
        List<Element> roots = new ArrayList<Element>();
        roots.add(doc.body());
        roots.add(doc.body());
        Elements found = Selector.select("p", roots);
        assertEquals(1, found.size());
    }

    // select with Iterable<Element> roots null throws IllegalArgumentException
    @Test
    public void testSelectIterable_nullRoots_throwsException() throws Throwable {
        try {
            Selector.select("p", (Iterable<Element>) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // select with Iterable<Element> roots and empty query throws IllegalArgumentException
    @Test
    public void testSelectIterable_emptyQuery_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        List<Element> roots = new ArrayList<Element>();
        roots.add(doc.body());
        try {
            Selector.select("", roots);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }
}
