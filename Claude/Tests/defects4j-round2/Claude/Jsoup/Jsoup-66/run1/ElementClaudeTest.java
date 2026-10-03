package org.jsoup.nodes;

import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ElementClaudeTest {

    private Document doc;

    @Before
    public void setUp() throws Throwable {
        doc = Jsoup.parse("<html><head></head><body><div id=\"main\" class=\"a b\"><p>One</p><p>Two</p></div></body></html>");
    }

    // constructor Element(String tag): tagName reflects the given tag
    @Test
    public void testConstructorStringTag_setsTagName() throws Throwable {
        Element el = new Element("span");
        assertEquals("span", el.tagName());
    }

    // constructor Element(Tag, baseUri): attributes null allowed, lazily created empty
    @Test
    public void testConstructorTagBaseUri_nullAttributes_lazyCreate() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        assertNotNull(el.attributes());
        assertTrue(el.dataset().isEmpty());
    }

    // tagName(String): empty string branch throws IllegalArgumentException
    @Test
    public void testTagNameSetter_empty_throwsIllegalArgumentException() throws Throwable {
        Element el = new Element("div");
        try {
            el.tagName("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // tagName(String): valid name preserves case and returns this for chaining
    @Test
    public void testTagNameSetter_valid_changesTagPreservingCase() throws Throwable {
        Element el = new Element("div");
        Element returned = el.tagName("SPAN");
        assertSame(el, returned);
        assertEquals("SPAN", el.tagName());
    }

    // isBlock(): true branch (div) vs false branch (span)
    @Test
    public void testIsBlock_divTrue_spanFalse() throws Throwable {
        Element div = new Element("div");
        Element span = new Element("span");
        assertTrue(div.isBlock());
        assertFalse(span.isBlock());
    }

    // id(): absent attribute returns empty string; present returns value
    @Test
    public void testId_presentAndAbsent() throws Throwable {
        Element el = new Element("div");
        assertEquals("", el.id());
        el.attr("id", "main");
        assertEquals("main", el.id());
    }

    // attr(String,String): chaining and getter round trip
    @Test
    public void testAttrSetter_chaining_updatesValue() throws Throwable {
        Element el = new Element("div");
        Element returned = el.attr("class", "test");
        assertSame(el, returned);
        assertEquals("test", el.attr("class"));
    }

    // dataset(): filtered view of data- attributes
    @Test
    public void testDataset_reflectsDataAttributes() throws Throwable {
        Element el = new Element("div");
        el.attr("data-foo", "bar");
        assertEquals("bar", el.dataset().get("foo"));
    }

    // parent(): null when standalone, non-null after appendChild
    @Test
    public void testParent_nullStandalone_nonNullAfterAppend() throws Throwable {
        Element child = new Element("span");
        assertNull(child.parent());
        Element parent = new Element("div");
        parent.appendChild(child);
        assertSame(parent, child.parent());
    }

    // parents(): ancestor chain excludes #root, closest first, includes html
    @Test
    public void testParents_returnsAncestorsClosestFirst() throws Throwable {
        Element p = doc.select("p").get(0);
        Elements parents = p.parents();
        assertEquals(3, parents.size());
        assertEquals("div", parents.get(0).tagName());
        assertEquals("body", parents.get(1).tagName());
        assertEquals("html", parents.get(2).tagName());
    }

    // child(int): valid index returns proper child
    @Test
    public void testChild_validIndex_returnsElement() throws Throwable {
        Element div = doc.select("div").get(0);
        assertEquals("p", div.child(0).tagName());
        assertEquals("Two", div.child(1).text());
    }

    // child(int): out of range throws IndexOutOfBoundsException
    @Test
    public void testChild_invalidIndex_throwsIndexOutOfBounds() throws Throwable {
        Element div = doc.select("div").get(0);
        try {
            div.child(5);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // children(): filters to element nodes only, excluding text nodes
    @Test
    public void testChildren_filtersOnlyElementNodes() throws Throwable {
        Element p = new Element("p");
        p.appendText("Hello");
        p.appendElement("b").text("world");
        Elements children = p.children();
        assertEquals(1, children.size());
        assertEquals("b", children.get(0).tagName());
    }

    // textNodes(): filters to text nodes only
    @Test
    public void testTextNodes_returnsOnlyTextNodes() throws Throwable {
        Element p = new Element("p");
        p.appendText("Hello ");
        p.appendElement("b").text("world");
        p.appendText("!");
        List<TextNode> textNodes = p.textNodes();
        assertEquals(2, textNodes.size());
        assertEquals("Hello ", textNodes.get(0).getWholeText());
    }

    // dataNodes(): script content becomes a DataNode
    @Test
    public void testDataNodes_scriptContent() throws Throwable {
        Document d = Jsoup.parse("<html><head><script>var x=1;</script></head><body></body></html>");
        Element script = d.select("script").get(0);
        List<DataNode> dataNodes = script.dataNodes();
        assertEquals(1, dataNodes.size());
        assertTrue(dataNodes.get(0).getWholeData().contains("x=1"));
    }

    // select(): finds matching descendant elements
    @Test
    public void testSelect_findsMatchingElements() throws Throwable {
        Elements ps = doc.select("p");
        assertEquals(2, ps.size());
    }

    // selectFirst(): null when no match, element when match found
    @Test
    public void testSelectFirst_noMatch_returnsNull() throws Throwable {
        assertNull(doc.selectFirst("span"));
    }

    @Test
    public void testSelectFirst_match_returnsElement() throws Throwable {
        Element el = doc.selectFirst("p");
        assertEquals("One", el.text());
    }

    // is(String): true and false branches
    @Test
    public void testIs_trueAndFalse() throws Throwable {
        Element div = doc.select("div").get(0);
        assertTrue(div.is("div#main"));
        assertFalse(div.is("span"));
    }

    // appendChild(null): Validate.notNull throws
    @Test
    public void testAppendChild_null_throwsException() throws Throwable {
        Element el = new Element("div");
        try {
            el.appendChild(null);
            fail("expected exception for null child");
        } catch (IllegalArgumentException expected) { }
    }

    // appendChild(Node): appends at end, preserving order
    @Test
    public void testAppendChild_addsAtEnd_maintainsOrder() throws Throwable {
        Element el = new Element("div");
        el.appendChild(new Element("span"));
        el.appendChild(new Element("b"));
        assertEquals(2, el.childNodeSize());
        assertEquals("span", el.child(0).tagName());
        assertEquals("b", el.child(1).tagName());
    }

    // appendTo(Element): moves this element under supplied parent
    @Test
    public void testAppendTo_movesElementToParent() throws Throwable {
        Element parent = new Element("div");
        Element child = new Element("span");
        Element returned = child.appendTo(parent);
        assertSame(child, returned);
        assertSame(parent, child.parent());
    }

    // prependChild(Node): adds at start of children
    @Test
    public void testPrependChild_addsAtStart() throws Throwable {
        Element el = new Element("div");
        el.appendChild(new Element("span"));
        el.prependChild(new Element("b"));
        assertEquals("b", el.child(0).tagName());
        assertEquals("span", el.child(1).tagName());
    }

    // insertChildren(int,Collection): negative index rolls around to end
    @Test
    public void testInsertChildren_negativeIndex_insertsAtEnd() throws Throwable {
        Element el = new Element("div");
        el.appendChild(new Element("span"));
        List<Node> toInsert = new ArrayList<Node>();
        toInsert.add(new Element("b"));
        el.insertChildren(-1, toInsert);
        assertEquals(2, el.childNodeSize());
        assertEquals("b", el.child(1).tagName());
    }

    // insertChildren(int,Collection): out-of-bounds index throws IllegalArgumentException
    @Test
    public void testInsertChildren_indexOutOfBounds_throws() throws Throwable {
        Element el = new Element("div");
        List<Node> toInsert = new ArrayList<Node>();
        toInsert.add(new Element("b"));
        try {
            el.insertChildren(5, toInsert);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // appendElement(String): creates and appends child, returns child
    @Test
    public void testAppendElement_createsAndAppendsChild() throws Throwable {
        Element el = new Element("div");
        Element child = el.appendElement("span");
        assertEquals("span", child.tagName());
        assertSame(el, child.parent());
    }

    // prependElement(String): creates and prepends child, returns child
    @Test
    public void testPrependElement_createsAndPrependsChild() throws Throwable {
        Element el = new Element("div");
        el.appendElement("span");
        Element first = el.prependElement("b");
        assertSame(first, el.child(0));
    }

    // appendText/prependText: build up text content in order
    @Test
    public void testAppendAndPrependText_buildsTextInOrder() throws Throwable {
        Element el = new Element("p");
        el.appendText("World");
        el.prependText("Hello ");
        assertEquals("Hello World", el.text());
    }

    // append(String html): parses fragment and appends at end
    @Test
    public void testAppend_parsesAndAppendsHtml() throws Throwable {
        Element el = new Element("div");
        el.append("<p>Test</p>");
        assertEquals(1, el.children().size());
        assertEquals("p", el.child(0).tagName());
    }

    // prepend(String html): parses fragment and inserts at start
    @Test
    public void testPrepend_parsesAndPrependsHtml() throws Throwable {
        Element el = new Element("div");
        el.append("<p>Second</p>");
        el.prepend("<b>First</b>");
        assertEquals("b", el.child(0).tagName());
    }

    // before(String)/after(String): insert sibling html nodes around this element
    @Test
    public void testBeforeAndAfter_insertSiblings() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p1 = div.child(0);
        p1.before("<span>Before</span>");
        p1.after("<span>After</span>");
        assertEquals(4, div.childNodeSize());
    }

    // empty(): removes all children, keeps attributes
    @Test
    public void testEmpty_removesAllChildren() throws Throwable {
        Element div = doc.select("div").get(0);
        Element returned = div.empty();
        assertSame(div, returned);
        assertEquals(0, div.childNodeSize());
    }

    // cssSelector(): id branch returns "#id"
    @Test
    public void testCssSelector_withId_returnsHashId() throws Throwable {
        Element div = doc.select("div").get(0);
        assertEquals("#main", div.cssSelector());
    }

    // cssSelector(): no-id branch with duplicate siblings gets nth-child suffix
    @Test
    public void testCssSelector_withoutId_buildsNthChildPath() throws Throwable {
        Element p = doc.select("p").get(0);
        String selector = p.cssSelector();
        assertTrue(selector.contains("p:nth-child"));
    }

    // siblingElements(): excludes this element itself
    @Test
    public void testSiblingElements_excludesSelf() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p1 = div.child(0);
        Elements siblings = p1.siblingElements();
        assertEquals(1, siblings.size());
        assertEquals("Two", siblings.get(0).text());
    }

    // nextElementSibling(): last element has no next sibling
    @Test
    public void testNextElementSibling_lastElement_returnsNull() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p2 = div.child(1);
        assertNull(p2.nextElementSibling());
    }

    // nextElementSibling(): normal case returns the following element
    @Test
    public void testNextElementSibling_hasNext_returnsCorrectElement() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p1 = div.child(0);
        Element next = p1.nextElementSibling();
        assertEquals("Two", next.text());
    }

    // previousElementSibling(): first element has no previous sibling
    @Test
    public void testPreviousElementSibling_firstElement_returnsNull() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p1 = div.child(0);
        assertNull(p1.previousElementSibling());
    }

    // BUG HUNT: per Javadoc, firstElementSibling() is "the parent's first element child";
    // when this element is the ONLY child, it IS that first child, so it must return itself, not null.
    @Test
    public void testFirstElementSibling_onlyChild_returnsSelf() throws Throwable {
        Element parent = new Element("div");
        Element only = parent.appendElement("p");
        assertSame(only, only.firstElementSibling());
    }

    // firstElementSibling(): multiple children branch returns the actual first sibling
    @Test
    public void testFirstElementSibling_multipleChildren_returnsFirst() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p2 = div.child(1);
        Element first = p2.firstElementSibling();
        assertEquals("One", first.text());
    }

    // BUG HUNT: same contract issue for lastElementSibling() with a sole child.
    @Test
    public void testLastElementSibling_onlyChild_returnsSelf() throws Throwable {
        Element parent = new Element("div");
        Element only = parent.appendElement("p");
        assertSame(only, only.lastElementSibling());
    }

    // lastElementSibling(): multiple children branch returns the actual last sibling
    @Test
    public void testLastElementSibling_multipleChildren_returnsLast() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p1 = div.child(0);
        Element last = p1.lastElementSibling();
        assertEquals("Two", last.text());
    }

    // elementSiblingIndex(): returns 0-based position among element siblings
    @Test
    public void testElementSiblingIndex_returnsCorrectPosition() throws Throwable {
        Element div = doc.select("div").get(0);
        Element p2 = div.child(1);
        assertEquals(1, p2.elementSiblingIndex());
    }

    // getElementsByTag(): finds all matching descendants
    @Test
    public void testGetElementsByTag_findsMatches() throws Throwable {
        Elements ps = doc.getElementsByTag("p");
        assertEquals(2, ps.size());
    }

    // getElementById(): found vs not-found branches
    @Test
    public void testGetElementById_findsMatchAndReturnsNullIfAbsent() throws Throwable {
        Element found = doc.getElementById("main");
        assertNotNull(found);
        assertNull(doc.getElementById("missing"));
    }

    // getElementsByClass(): case-insensitive matching
    @Test
    public void testGetElementsByClass_caseInsensitive() throws Throwable {
        Elements found = doc.getElementsByClass("A");
        assertEquals(1, found.size());
    }

    // getElementsByAttributeValueMatching(String,String): invalid regex throws IllegalArgumentException
    @Test
    public void testGetElementsByAttributeValueMatching_badRegex_throwsIllegalArgument() throws Throwable {
        Element div = doc.select("div").get(0);
        try {
            div.getElementsByAttributeValueMatching("class", "[");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // getAllElements(): includes self plus all descendants
    @Test
    public void testGetAllElements_includesSelfAndDescendants() throws Throwable {
        Element div = doc.select("div").get(0);
        Elements all = div.getAllElements();
        assertTrue(all.size() >= 3);
    }

    // text(): combines descendant text, adding whitespace between block elements
    @Test
    public void testText_combinesChildTextWithSpacing() throws Throwable {
        Element div = doc.select("div").get(0);
        assertEquals("One Two", div.text());
    }

    // ownText(): excludes text owned by child elements
    @Test
    public void testOwnText_excludesChildElementText() throws Throwable {
        Element p = new Element("p");
        p.appendText("Hello ");
        p.appendElement("b").text("there");
        p.appendText(" now!");
        assertEquals("Hello now!", p.ownText());
    }

    // hasText(): true for non-blank content, false for empty element
    @Test
    public void testHasText_trueForNonBlank_falseForEmpty() throws Throwable {
        Element withText = new Element("p");
        withText.appendText("Hi");
        Element empty = new Element("p");
        assertTrue(withText.hasText());
        assertFalse(empty.hasText());
    }

    // data(): collects DataNode content recursively (e.g. script body)
    @Test
    public void testData_returnsScriptContent() throws Throwable {
        Document d = Jsoup.parse("<html><head><script>abc</script></head><body></body></html>");
        Element head = d.select("head").get(0);
        assertTrue(head.data().contains("abc"));
    }

    // className(): returns the literal class attribute string
    @Test
    public void testClassName_returnsLiteralAttribute() throws Throwable {
        Element div = doc.select("div").get(0);
        assertEquals("a b", div.className());
    }

    // classNames()/classNames(Set): get reflects attribute, set persists new value
    @Test
    public void testClassNames_getSetRoundTrip() throws Throwable {
        Element div = doc.select("div").get(0);
        Set<String> classes = div.classNames();
        assertTrue(classes.contains("a"));
        assertTrue(classes.contains("b"));
        Set<String> newClasses = new LinkedHashSet<String>();
        newClasses.add("x");
        div.classNames(newClasses);
        assertEquals("x", div.className());
    }

    // hasClass(): covers len==0, len<wantLen, len==wantLen, and scan-middle/last branches
    @Test
    public void testHasClass_variousLengthBranches() throws Throwable {
        Element el = new Element("div");
        assertFalse(el.hasClass("foo"));
        el.attr("class", "ab");
        assertFalse(el.hasClass("abc"));
        el.attr("class", "foo");
        assertTrue(el.hasClass("FOO"));
        el.attr("class", "foo bar baz");
        assertTrue(el.hasClass("bar"));
        assertTrue(el.hasClass("baz"));
        assertFalse(el.hasClass("qux"));
    }

    // addClass/removeClass/toggleClass: mutate class set as documented
    @Test
    public void testAddRemoveToggleClass() throws Throwable {
        Element el = new Element("div");
        el.addClass("foo");
        assertTrue(el.hasClass("foo"));
        el.removeClass("foo");
        assertFalse(el.hasClass("foo"));
        el.toggleClass("bar");
        assertTrue(el.hasClass("bar"));
        el.toggleClass("bar");
        assertFalse(el.hasClass("bar"));
    }

    // val()/val(String): textarea branch uses text(), others use value attribute
    @Test
    public void testVal_getSet_inputAndTextarea() throws Throwable {
        Element input = new Element("input");
        input.val("hello");
        assertEquals("hello", input.val());
        Element textarea = new Element("textarea");
        textarea.val("world");
        assertEquals("world", textarea.val());
    }

    // html()/html(String): set clears existing content then parses new HTML
    @Test
    public void testHtml_getSet() throws Throwable {
        Element div = new Element("div");
        Element returned = div.html("<p>Hi</p>");
        assertSame(div, returned);
        assertEquals("<p>Hi</p>", div.html());
    }

    // toString(): delegates to outerHtml()
    @Test
    public void testToString_equalsOuterHtml() throws Throwable {
        Element el = new Element("p");
        el.text("Hi");
        assertEquals(el.outerHtml(), el.toString());
    }

    // clone(): produces an independent deep copy of attributes and children
    @Test
    public void testClone_independentCopy() throws Throwable {
        Element original = new Element("div");
        original.appendElement("p").text("Hi");
        Element clone = original.clone();
        clone.attr("id", "cloned");
        assertEquals("", original.attr("id"));
        assertEquals("cloned", clone.attr("id"));
        assertEquals(1, clone.children().size());
    }
}
