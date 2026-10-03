package org.jsoup.nodes;

import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.Set;
import java.util.LinkedHashSet;

import org.junit.Test;
import static org.junit.Assert.*;

public class ElementClaudeTest {

    // Validate.notNull(tag) branch -> IllegalArgumentException
    @Test
    public void testConstructor_nullTag_throwsIllegalArgumentException() throws Throwable {
        try {
            new Element(null, "http://example.com");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // tagName(), nodeName(), tag() basic accessors
    @Test
    public void testTagNameNodeNameAndTag_returnConsistentValues() throws Throwable {
        Tag tag = Tag.valueOf("div");
        Element el = new Element(tag, "");
        assertEquals("div", el.tagName());
        assertEquals("div", el.nodeName());
        assertSame(tag, el.tag());
    }

    // isBlock() true for block tag, false for inline tag
    @Test
    public void testIsBlock_blockAndInlineTags() throws Throwable {
        Document doc = Jsoup.parse("<div><span>text</span></div>");
        Element div = doc.select("div").get(0);
        Element span = doc.select("span").get(0);
        assertTrue(div.isBlock());
        assertFalse(span.isBlock());
    }

    // id() ternary: null -> "" ; present -> value
    @Test
    public void testId_noAttributeAndWithAttribute() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals("", el.id());
        el.attr("id", "main");
        assertEquals("main", el.id());
    }

    // attr(key,value) chaining returns this, attr(key) reads value
    @Test
    public void testAttr_setAndGet_chaining() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element result = el.attr("data-x", "value1");
        assertSame(el, result);
        assertEquals("value1", el.attr("data-x"));
    }

    // parent() with no parent set
    @Test
    public void testParent_standaloneElement_returnsNull() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertNull(el.parent());
    }

    // parents() accumulates ancestors, excludes #root
    @Test
    public void testParents_returnsAncestorsExcludingRoot() throws Throwable {
        Document doc = Jsoup.parse("<body><div><p>text</p></div></body>");
        Element p = doc.select("p").get(0);
        Elements parents = p.parents();
        assertEquals(3, parents.size());
        assertEquals("div", parents.get(0).tagName());
        assertEquals("html", parents.get(2).tagName());
    }

    // child(index) valid indices
    @Test
    public void testChild_validIndex_returnsCorrectElement() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p></div>");
        Element div = doc.select("div").get(0);
        assertEquals("1", div.child(0).text());
        assertEquals("2", div.child(1).text());
    }

    // child(index) out of bounds throws
    @Test
    public void testChild_invalidIndex_throwsIndexOutOfBoundsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.child(0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // children() filters only Element instances
    @Test
    public void testChildren_filtersOnlyElementNodes() throws Throwable {
        Document doc = Jsoup.parse("<div>text<p>a</p>more<p>b</p></div>");
        Element div = doc.select("div").get(0);
        assertEquals(2, div.children().size());
    }

    // children() loop zero iterations -> empty
    @Test
    public void testChildren_noChildren_returnsEmptyElements() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals(0, el.children().size());
    }

    // select() delegates to Selector
    @Test
    public void testSelect_findsMatchingElements() throws Throwable {
        Document doc = Jsoup.parse("<div><p class=\"a\">1</p><p>2</p></div>");
        Elements found = doc.select("p.a");
        assertEquals(1, found.size());
        assertEquals("1", found.get(0).text());
    }

    // appendChild adds at end, prependChild adds at start
    @Test
    public void testAppendChildAndPrependChild_orderIsCorrect() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        Element child2 = new Element(Tag.valueOf("span"), "");
        el.appendChild(child1);
        el.prependChild(child2);
        assertEquals("span", el.child(0).tagName());
        assertEquals("p", el.child(1).tagName());
    }

    // appendChild null -> Validate.notNull throws
    @Test
    public void testAppendChild_nullChild_throwsIllegalArgumentException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.appendChild(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // appendElement / prependElement create and insert new elements
    @Test
    public void testAppendElementAndPrependElement_addsNewElementChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element appended = el.appendElement("p");
        Element prepended = el.prependElement("span");
        assertSame(prepended, el.child(0));
        assertSame(appended, el.child(1));
    }

    // appendText / prependText add text nodes
    @Test
    public void testAppendTextAndPrependText_buildsCorrectTextContent() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("world");
        el.prependText("hello ");
        assertEquals("hello world", el.text());
    }

    // append(html) parses and appends fragment nodes
    @Test
    public void testAppend_parsesAndAppendsHtml() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("start");
        Element result = el.append("<p>end</p>");
        assertSame(el, result);
        assertEquals("p", el.child(0).tagName());
        assertEquals("end", el.child(0).text());
    }

    // prepend(html) parses and prepends fragment nodes
    @Test
    public void testPrepend_parsesAndPrependsHtml() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("end");
        Element result = el.prepend("<p>start</p>");
        assertSame(el, result);
        assertEquals("p", el.child(0).tagName());
        assertEquals("start", el.child(0).text());
    }

    // empty() clears all children
    @Test
    public void testEmpty_removesAllChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("hello");
        Element result = el.empty();
        assertSame(el, result);
        assertEquals(0, el.childNodes().size());
    }

    // wrap(html) normal case: wrap found, structure updated
    @Test
    public void testWrap_wrapsElementWithProvidedHtml() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"inner\">content</div>");
        Element inner = doc.getElementById("inner");
        Element result = inner.wrap("<section></section>");
        assertSame(inner, result);
        assertEquals("section", inner.parent().tagName());
    }



    // wrap("") -> Validate.notEmpty throws
    @Test
    public void testWrap_emptyHtml_throwsIllegalArgumentException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.wrap("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // siblingElements() returns parent's children including self
    @Test
    public void testSiblingElements_returnsAllParentChildren() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><p>3</p></div>");
        Element second = doc.select("p").get(1);
        Elements siblings = second.siblingElements();
        assertEquals(3, siblings.size());
    }

    // nextElementSibling() has-next and is-last branches
    @Test
    public void testNextElementSibling_hasNextAndIsLast() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p></div>");
        Element first = doc.select("p").get(0);
        Element second = doc.select("p").get(1);
        assertEquals("2", first.nextElementSibling().text());
        assertNull(second.nextElementSibling());
    }

    // previousElementSibling() is-first and has-previous branches
    @Test
    public void testPreviousElementSibling_isFirstAndHasPrevious() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p></div>");
        Element first = doc.select("p").get(0);
        Element second = doc.select("p").get(1);
        assertNull(first.previousElementSibling());
        assertEquals("1", second.previousElementSibling().text());
    }

    // firstElementSibling() / lastElementSibling() with multiple siblings
    @Test
    public void testFirstElementSiblingAndLastElementSibling_multipleSiblings() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><p>3</p></div>");
        Element second = doc.select("p").get(1);
        assertEquals("1", second.firstElementSibling().text());
        assertEquals("3", second.lastElementSibling().text());
    }

    // elementSiblingIndex(): no-parent branch and with-siblings branch
    @Test
    public void testElementSiblingIndex_noParentAndWithSiblings() throws Throwable {
        Element standalone = new Element(Tag.valueOf("div"), "");
        assertEquals(Integer.valueOf(0), standalone.elementSiblingIndex());
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><p>3</p></div>");
        Element third = doc.select("p").get(2);
        assertEquals(Integer.valueOf(2), third.elementSiblingIndex());
    }

    // getElementsByTag() case-insensitive tag search
    @Test
    public void testGetElementsByTag_findsAllMatches() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><span>2</span><p>3</p></div>");
        Elements ps = doc.select("div").get(0).getElementsByTag("P");
        assertEquals(2, ps.size());
    }

    // getElementById() found and not-found branches
    @Test
    public void testGetElementById_foundAndNotFound() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"x\">hi</div>");
        assertNotNull(doc.getElementById("x"));
        assertNull(doc.getElementById("missing"));
    }



    // getElementsByAttribute / value / valueNot matchers
    @Test
    public void testGetElementsByAttributeAndValue_matchesExpected() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"http://example.com/page\">x</a><a href=\"ftp://other.org\">y</a>");
        Elements withAttr = doc.getElementsByAttribute("href");
        assertTrue(containsHref(withAttr, "http://example.com/page"));
        Elements exactValue = doc.getElementsByAttributeValue("href", "ftp://other.org");
        assertFalse(containsHref(exactValue, "http://example.com/page"));
        Elements notValue = doc.getElementsByAttributeValueNot("href", "ftp://other.org");
        assertTrue(containsHref(notValue, "http://example.com/page"));
        assertFalse(containsHref(notValue, "ftp://other.org"));
    }

    // getElementsByAttributeValue starting/ending/containing matchers
    @Test
    public void testGetElementsByAttributeValueVariants_startingEndingContaining() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"http://example.com/page\">x</a><a href=\"ftp://other.org\">y</a>");
        Elements starting = doc.getElementsByAttributeValueStarting("href", "http");
        assertTrue(containsHref(starting, "http://example.com/page"));
        Elements ending = doc.getElementsByAttributeValueEnding("href", ".org");
        assertTrue(containsHref(ending, "ftp://other.org"));
        Elements containing = doc.getElementsByAttributeValueContaining("href", "example");
        assertTrue(containsHref(containing, "http://example.com/page"));
        assertFalse(containsHref(containing, "ftp://other.org"));
    }

    // getElementsByIndexLessThan/GreaterThan/Equals branches
    @Test
    public void testGetElementsByIndex_lessGreaterEquals() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><p>3</p></div>");
        Element div = doc.select("div").get(0);
        Elements lessThan = div.getElementsByIndexLessThan(1);
        Elements greaterThan = div.getElementsByIndexGreaterThan(1);
        Elements equalsIdx = div.getElementsByIndexEquals(1);
        assertTrue(containsText(lessThan, "1"));
        assertFalse(containsText(lessThan, "3"));
        assertTrue(containsText(greaterThan, "3"));
        assertFalse(containsText(greaterThan, "1"));
        assertTrue(containsText(equalsIdx, "2"));
    }

    // getAllElements() includes self and descendants
    @Test
    public void testGetAllElements_includesSelfAndDescendants() throws Throwable {
        Document doc = Jsoup.parse("<div><p><span>x</span></p></div>");
        Element div = doc.select("div").get(0);
        assertEquals(3, div.getAllElements().size());
    }

    // text() normalises whitespace and inserts space before block children
    @Test
    public void testText_combinesAndTrimsWithBlockSpacing() throws Throwable {
        Document doc = Jsoup.parse("<div>  hello <span>world</span>  </div>");
        Element div = doc.select("div").get(0);
        assertEquals("hello world", div.text());
        Document doc2 = Jsoup.parse("<div><p>one</p><p>two</p></div>");
        Element div2 = doc2.select("div").get(0);
        assertEquals("one two", div2.text());
    }

    // hasText(): blank content false, non-blank content true
    @Test
    public void testHasText_blankAndNonBlank() throws Throwable {
        Document doc = Jsoup.parse("<div>   </div><p>hello</p>");
        Element div = doc.select("div").get(0);
        Element p = doc.select("p").get(0);
        assertFalse(div.hasText());
        assertTrue(p.hasText());
    }

    // data() collects DataNode content (e.g. script body)
    @Test
    public void testData_returnsScriptContent() throws Throwable {
        Document doc = Jsoup.parse("<script>var a = 1;</script>");
        Element script = doc.select("script").get(0);
        assertEquals("var a = 1;", script.data());
    }

    // className() empty/no-attribute vs literal value, classNames() split
    @Test
    public void testClassNameAndClassNames_basicBehavior() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals("", el.className());
        el.attr("class", "header gray");
        assertEquals("header gray", el.className());
        Set<String> names = el.classNames();
        assertTrue(names.contains("header"));
        assertTrue(names.contains("gray"));
        assertEquals(2, names.size());
    }

    // classNames(Set) setter persists joined class attribute
    @Test
    public void testClassNamesSetter_updatesClassAttribute() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Set<String> classes = new LinkedHashSet<String>();
        classes.add("foo");
        classes.add("bar");
        Element result = el.classNames(classes);
        assertSame(el, result);
        assertEquals("foo bar", el.className());
    }

    // addClass/removeClass/toggleClass branches
    @Test
    public void testHasClassAddRemoveToggle_updatesClassSet() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.addClass("foo");
        assertTrue(el.hasClass("foo"));
        el.removeClass("foo");
        assertFalse(el.hasClass("foo"));
        el.toggleClass("bar");
        assertTrue(el.hasClass("bar"));
        el.toggleClass("bar");
        assertFalse(el.hasClass("bar"));
    }

    // val() getter: textarea branch uses text(), others use attr("value")
    @Test
    public void testVal_getterForTextareaAndInput() throws Throwable {
        Element textarea = new Element(Tag.valueOf("textarea"), "");
        textarea.text("hello");
        assertEquals("hello", textarea.val());
        Element input = new Element(Tag.valueOf("input"), "");
        input.attr("value", "abc");
        assertEquals("abc", input.val());
    }

    // val(value) setter: textarea branch sets text, others set attribute
    @Test
    public void testValSetter_forTextareaAndInput() throws Throwable {
        Element textarea = new Element(Tag.valueOf("textarea"), "");
        textarea.val("content");
        assertEquals("content", textarea.text());
        Element input = new Element(Tag.valueOf("input"), "");
        input.val("xyz");
        assertEquals("xyz", input.attr("value"));
    }

    // html() getter returns inner html, setter replaces content
    @Test
    public void testHtml_getterAndSetter() throws Throwable {
        Document doc = Jsoup.parse("<div><p>hi</p></div>");
        Element div = doc.select("div").get(0);
        assertTrue(div.html().contains("<p>hi</p>"));
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("old");
        Element result = el.html("<p>new</p>");
        assertSame(el, result);
        assertEquals("new", el.text());
    }

    // toString() delegates to outerHtml()
    @Test
    public void testToString_returnsOuterHtml() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("hi");
        assertEquals(el.outerHtml(), el.toString());
    }

    // equals() reflexive/different-type branches, hashCode() consistency
    @Test
    public void testEqualsAndHashCode_basicContract() throws Throwable {
        Element el1 = new Element(Tag.valueOf("div"), "");
        assertTrue(el1.equals(el1));
        assertFalse(el1.equals("not an element"));
        assertEquals(el1.hashCode(), el1.hashCode());
    }

    private boolean containsText(Elements elements, String text) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).text().equals(text)) return true;
        }
        return false;
    }

    private boolean containsHref(Elements elements, String href) {
        for (int i = 0; i < elements.size(); i++) {
            if (href.equals(elements.get(i).attr("href"))) return true;
        }
        return false;
    }
}
