package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public class ElementClaudeTest {

    // covers Validate.notNull(tag) enforcement in constructor
    @Test
    public void testConstructor_nullTag_throwsException() throws Throwable {
        try {
            new Element(null, "");
            fail("expected exception for null tag");
        } catch (RuntimeException expected) {}
    }

    // covers tagName() getter after construction
    @Test
    public void testTagName_getterAfterConstruction() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals("div", el.tagName());
    }

    // covers tagName(String) changing the tag and reflecting in tag()
    @Test
    public void testTagName_setNewName_updatesTagAndName() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.tagName("span");
        assertEquals("span", el.tagName());
        assertEquals("span", el.tag().getName());
    }

    // covers Validate.notEmpty(tagName) throwing on empty string
    @Test
    public void testTagName_emptyString_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.tagName("");
            fail("expected exception for empty tag name");
        } catch (RuntimeException expected) {}
    }

    // covers isBlock() true for block tag and false for inline tag
    @Test
    public void testIsBlock_divTrue_spanFalse() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        Element span = new Element(Tag.valueOf("span"), "");
        assertTrue(div.isBlock());
        assertFalse(span.isBlock());
    }

    // covers id() returning empty string when no id attribute set
    @Test
    public void testId_noAttribute_returnsEmptyString() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals("", el.id());
    }

    // covers id() returning the attribute value when present
    @Test
    public void testId_withAttribute_returnsValue() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("id", "main");
        assertEquals("main", el.id());
    }

    // covers attr(String,String) chaining and attr(String) getter
    @Test
    public void testAttr_setterReturnsSelf_getterReturnsValue() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element returned = el.attr("class", "test");
        assertSame(el, returned);
        assertEquals("test", el.attr("class"));
    }

    // covers dataset() filtering data-* attributes without prefix
    @Test
    public void testDataset_filtersDataAttributes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("data-foo", "bar");
        el.attr("id", "x");
        Map<String, String> dataset = el.dataset();
        assertEquals("bar", dataset.get("foo"));
        assertNull(dataset.get("id"));
    }

    // covers parent() returning the element attached via appendChild
    @Test
    public void testParent_setAfterAppendChild() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child = new Element(Tag.valueOf("p"), "");
        parent.appendChild(child);
        assertSame(parent, child.parent());
    }

    // covers parents() walking up ancestors and excluding element named "#root"
    @Test
    public void testParents_excludesRootTag() throws Throwable {
        Element root = new Element(Tag.valueOf("#root"), "");
        Element html = new Element(Tag.valueOf("html"), "");
        Element body = new Element(Tag.valueOf("body"), "");
        Element div = new Element(Tag.valueOf("div"), "");
        root.appendChild(html);
        html.appendChild(body);
        body.appendChild(div);
        Elements parents = div.parents();
        assertEquals(2, parents.size());
        assertSame(body, parents.get(0));
    }

    // covers child(index) delegating to children().get(index)
    @Test
    public void testChild_validIndex_returnsElement() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        Element p1 = new Element(Tag.valueOf("p"), "");
        Element p2 = new Element(Tag.valueOf("p"), "");
        div.appendChild(p1);
        div.appendChild(p2);
        assertSame(p2, div.child(1));
    }

    // covers child(index) throwing when index is out of range
    @Test
    public void testChild_invalidIndex_throwsIndexOutOfBoundsException() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        try {
            div.child(0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {}
    }

    // covers children() filtering out non-Element nodes such as TextNode
    @Test
    public void testChildren_filtersOnlyElementNodes() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        div.appendChild(new TextNode("text", ""));
        div.appendChild(new Element(Tag.valueOf("p"), ""));
        Elements kids = div.children();
        assertEquals(1, kids.size());
        assertEquals("p", kids.get(0).tagName());
    }

    // covers textNodes() filtering only TextNode children
    @Test
    public void testTextNodes_returnsOnlyTextNodes() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        div.appendChild(new TextNode("Hello ", ""));
        div.appendChild(new Element(Tag.valueOf("span"), ""));
        div.appendChild(new TextNode("World", ""));
        List<TextNode> texts = div.textNodes();
        assertEquals(2, texts.size());
    }

    // covers dataNodes() filtering DataNode children, e.g. script content
    @Test
    public void testDataNodes_viaScriptTag() throws Throwable {
        Document doc = Jsoup.parse("<script>var i=1;</script>");
        Element script = doc.getElementsByTag("script").get(0);
        List<DataNode> nodes = script.dataNodes();
        assertEquals(1, nodes.size());
    }

    // covers select() delegating to Selector with this as context
    @Test
    public void testSelect_cssQuery_returnsMatchingElements() throws Throwable {
        Document doc = Jsoup.parse("<div><p class=\"a\">1</p><p>2</p></div>");
        Elements matched = doc.select("p.a");
        assertEquals(1, matched.size());
        assertEquals("1", matched.get(0).text());
    }

    // covers appendChild adding at end and prependChild adding at start
    @Test
    public void testAppendChildAndPrependChild_correctOrder() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element first = new Element(Tag.valueOf("p"), "");
        Element last = new Element(Tag.valueOf("span"), "");
        Element start = new Element(Tag.valueOf("h1"), "");
        parent.appendChild(first);
        parent.appendChild(last);
        parent.prependChild(start);
        Elements kids = parent.children();
        assertEquals(3, kids.size());
        assertSame(start, kids.get(0));
        assertSame(last, kids.get(2));
    }

    // covers appendElement creating a new Element, appending it, and returning it
    @Test
    public void testAppendElement_createsChildAndReturnsIt() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element newEl = parent.appendElement("span");
        assertEquals("span", newEl.tagName());
        assertSame(newEl, parent.children().get(0));
    }

    // covers prependElement creating a new Element and inserting it first
    @Test
    public void testPrependElement_createsChildAtStart() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        parent.appendElement("p");
        Element prepended = parent.prependElement("h1");
        assertSame(prepended, parent.children().get(0));
    }

    // covers appendText adding at end and prependText adding at start
    @Test
    public void testAppendTextAndPrependText_correctOrder() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("World");
        el.prependText("Hello ");
        assertEquals("Hello World", el.text());
    }

    // covers append(html) parsing a fragment and adding it to the end
    @Test
    public void testAppendHtml_parsesAndAppendsNodes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendElement("span");
        el.append("<p>Hi</p>");
        Elements kids = el.children();
        assertEquals(2, kids.size());
        assertEquals("p", kids.get(1).tagName());
    }

    // covers prepend(html) parsing a fragment and adding it to the start
    @Test
    public void testPrependHtml_parsesAndPrependsAtStart() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendElement("span");
        el.prepend("<p>Hi</p>");
        Elements kids = el.children();
        assertEquals("p", kids.get(0).tagName());
    }

    // covers html(String) clearing existing children then appending parsed html
    @Test
    public void testHtmlSetter_replacesExistingContent() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendElement("span");
        el.html("<p>New</p>");
        Elements kids = el.children();
        assertEquals(1, kids.size());
        assertEquals("p", kids.get(0).tagName());
    }

    // covers empty() clearing all child nodes
    @Test
    public void testEmpty_removesAllChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendElement("span");
        el.appendText("text");
        Element result = el.empty();
        assertSame(el, result);
        assertEquals(0, el.children().size());
    }

    // covers siblingElements() contract: "not a sibling of itself" per Javadoc
    @Test
    public void testSiblingElements_excludesSelf() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        Element p1 = new Element(Tag.valueOf("p"), "");
        Element p2 = new Element(Tag.valueOf("p"), "");
        Element p3 = new Element(Tag.valueOf("p"), "");
        div.appendChild(p1);
        div.appendChild(p2);
        div.appendChild(p3);
        Elements siblings = p2.siblingElements();
        assertEquals(2, siblings.size());
        assertNotSame(p2, siblings.get(0));
        assertNotSame(p2, siblings.get(1));
    }

    // covers nextElementSibling/previousElementSibling for middle, first and last positions
    @Test
    public void testElementSiblingNavigation_nextAndPrevious() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        Element p1 = new Element(Tag.valueOf("p"), "");
        Element p2 = new Element(Tag.valueOf("p"), "");
        Element p3 = new Element(Tag.valueOf("p"), "");
        div.appendChild(p1);
        div.appendChild(p2);
        div.appendChild(p3);
        assertSame(p2, p1.nextElementSibling());
        assertNull(p3.nextElementSibling());
        assertSame(p2, p3.previousElementSibling());
        assertNull(p1.previousElementSibling());
    }

    // covers elementSiblingIndex() with parent (index) and without parent (returns 0)
    @Test
    public void testElementSiblingIndex_correctPositionAndNoParent() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        Element p1 = new Element(Tag.valueOf("p"), "");
        Element p2 = new Element(Tag.valueOf("p"), "");
        div.appendChild(p1);
        div.appendChild(p2);
        assertEquals(Integer.valueOf(1), p2.elementSiblingIndex());
        Element standalone = new Element(Tag.valueOf("span"), "");
        assertEquals(Integer.valueOf(0), standalone.elementSiblingIndex());
    }

    // covers getElementsByTag matching descendants case-insensitively
    @Test
    public void testGetElementsByTag_findsDescendants() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><P>2</P><span>3</span></div>");
        Elements ps = doc.getElementsByTag("p");
        assertEquals(2, ps.size());
    }

    // covers getElementById returning match or null when absent
    @Test
    public void testGetElementById_foundAndNotFound() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"target\">Content</div>");
        Element found = doc.getElementById("target");
        assertNotNull(found);
        assertEquals("Content", found.text());
        assertNull(doc.getElementById("missing"));
    }

    // covers getElementsByClass matching regardless of case
    @Test
    public void testGetElementsByClass_caseInsensitive() throws Throwable {
        Document doc = Jsoup.parse("<div class=\"Header\">H</div>");
        Elements found = doc.getElementsByClass("header");
        assertEquals(1, found.size());
    }

    // covers PatternSyntaxException wrapped as IllegalArgumentException in regex methods
    @Test
    public void testRegexMethods_invalidPattern_throwsIllegalArgumentException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.getElementsByAttributeValueMatching("href", "[");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {}
        try {
            el.getElementsMatchingText("[");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {}
    }

    // covers getElementsContainingText matching combined descendant text, case-insensitively
    @Test
    public void testGetElementsContainingText_matchesCombinedText() throws Throwable {
        Document doc = Jsoup.parse("<div><p>Hello World</p><p>Other</p></div>");
        Element div = doc.getElementsByTag("div").get(0);
        Elements found = div.getElementsContainingText("hello");
        assertEquals(2, found.size());
    }

    // covers getElementsContainingOwnText matching only direct text, not descendant text
    @Test
    public void testGetElementsContainingOwnText_directTextOnly() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello <b>World</b></p>");
        Element p = doc.getElementsByTag("p").get(0);
        Elements foundHello = p.getElementsContainingOwnText("Hello");
        assertEquals(1, foundHello.size());
        assertSame(p, foundHello.get(0));
        Elements foundWorld = p.getElementsContainingOwnText("World");
        assertEquals("b", foundWorld.get(0).tagName());
    }

    // covers getAllElements returning self plus all descendant elements
    @Test
    public void testGetAllElements_includesSelfAndDescendants() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><span>2</span></div>");
        Element div = doc.getElementsByTag("div").get(0);
        Elements all = div.getAllElements();
        assertEquals(3, all.size());
        assertSame(div, all.get(0));
    }

    // covers text() combining descendant text and ownText() excluding child element text
    @Test
    public void testTextAndOwnText_combinedVsDirect() throws Throwable {
        Document doc = Jsoup.parse("<p>One <span>Two</span> Three</p>");
        Element p = doc.getElementsByTag("p").get(0);
        assertEquals("One Two Three", p.text());
        assertEquals("One Three", p.ownText());
    }

    // covers hasText() false for blank content, true once non-blank text present
    @Test
    public void testHasText_blankVsNonBlank() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertFalse(el.hasText());
        el.appendText("   ");
        assertFalse(el.hasText());
        el.appendText("Hi");
        assertTrue(el.hasText());
    }

    // covers data() concatenating DataNode content such as script body
    @Test
    public void testData_combinesDataNodeContent() throws Throwable {
        Document doc = Jsoup.parse("<script>var i=1;</script>");
        Element script = doc.getElementsByTag("script").get(0);
        assertTrue(script.data().contains("var i=1;"));
    }

    // covers className() literal value, classNames() split set, classNames(Set) setter round-trip
    @Test
    public void testClassNameClassNamesAndSetter() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "header gray");
        assertEquals("header gray", el.className());
        Set<String> names = el.classNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("header"));
        Set<String> newNames = new LinkedHashSet<String>();
        newNames.add("a");
        newNames.add("b");
        el.classNames(newNames);
        assertEquals("a b", el.className());
    }

    // covers hasClass case-insensitivity plus addClass/removeClass/toggleClass
    @Test
    public void testHasClassAddRemoveToggle_modifiesClassAttribute() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.addClass("Foo");
        assertTrue(el.hasClass("foo"));
        el.removeClass("Foo");
        assertFalse(el.hasClass("foo"));
        el.toggleClass("bar");
        assertTrue(el.hasClass("bar"));
        el.toggleClass("bar");
        assertFalse(el.hasClass("bar"));
    }

    // covers val() reading value attribute generically and text() for textarea
    @Test
    public void testVal_inputAndTextarea() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "");
        input.attr("value", "abc");
        assertEquals("abc", input.val());
        Element textarea = new Element(Tag.valueOf("textarea"), "");
        textarea.val("hello");
        assertEquals("hello", textarea.text());
    }

    // covers html() serializing only child nodes, not the element itself
    @Test
    public void testHtmlGetter_returnsInnerHtml() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element p = el.appendElement("p");
        p.text("Hi");
        String html = el.html();
        assertTrue(html.contains("<p>"));
        assertTrue(html.contains("Hi"));
        assertFalse(html.contains("<div>"));
    }

    // covers toString() as outerHtml, equals() reference-only equality, hashCode consistency
    @Test
    public void testToStringEqualsHashCode_basicContracts() throws Throwable {
        Element a = new Element(Tag.valueOf("div"), "");
        a.attr("id", "x");
        assertTrue(a.toString().contains("id=\"x\""));
        Element b = new Element(Tag.valueOf("div"), "");
        assertFalse(a.equals(b));
        assertTrue(a.equals(a));
        assertEquals(a.hashCode(), a.hashCode());
    }

    // covers clone() producing a distinct element with same tag and class names
    @Test
    public void testClone_createsIndependentCopy() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "a b");
        Element clone = el.clone();
        assertNotSame(el, clone);
        assertEquals(el.tagName(), clone.tagName());
        assertEquals(el.classNames(), clone.classNames());
    }
}
