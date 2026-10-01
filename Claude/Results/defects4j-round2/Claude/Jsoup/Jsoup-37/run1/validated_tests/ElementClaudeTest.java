package org.jsoup.nodes;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ElementClaudeTest {

    private Document doc;

    @Before
    public void setUp() throws Throwable {
        doc = Jsoup.parse("<html><head></head><body><div id=\"div1\" class=\"header main\">"
                + "<p>One <b>Two</b> Three</p></div><div id=\"div2\"></div></body></html>");
    }

    // Constructor: Validate.notNull(tag) branch - null tag throws
    @Test
    public void testConstructor_nullTag_throwsException() throws Throwable {
        try {
            new Element(null, "http://example.com");
            fail("expected exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    // tagName() returns tag name set via constructor
    @Test
    public void testTagName_returnsName() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "");
        assertEquals("span", el.tagName());
    }

    // tagName(String) changes tag and returns this for chaining
    @Test
    public void testTagNameSetter_changesTag() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "");
        Element ret = el.tagName("div");
        assertEquals("div", el.tagName());
        assertSame(el, ret);
    }

    // tagName(String) empty string branch throws IllegalArgumentException
    @Test
    public void testTagNameSetter_empty_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "");
        try {
            el.tagName("");
            fail("expected exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isBlock() true for block tag, false for inline tag
    @Test
    public void testIsBlock_divTrue_spanFalse() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        Element span = new Element(Tag.valueOf("span"), "");
        assertTrue(div.isBlock());
        assertFalse(span.isBlock());
    }

    // id() present-attribute branch
    @Test
    public void testId_present_returnsValue() throws Throwable {
        Element div1 = doc.getElementById("div1");
        assertEquals("div1", div1.id());
    }

    // id() absent-attribute branch returns empty string not null
    @Test
    public void testId_absent_returnsEmptyString() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "");
        assertEquals("", el.id());
    }

    // attr(key,value) sets attribute and chains
    @Test
    public void testAttrSetter_chaining() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "");
        Element ret = el.attr("data-x", "1");
        assertSame(el, ret);
        assertEquals("1", el.attr("data-x"));
    }

    // dataset() filters only data- prefixed attributes
    @Test
    public void testDataset_filtersDataAttributes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("data-name", "jsoup");
        el.attr("class", "x");
        Map<String, String> dataset = el.dataset();
        assertEquals("jsoup", dataset.get("name"));
        assertNull(dataset.get("class"));
    }

    // parent() returns null when element has no parent
    @Test
    public void testParent_standalone_returnsNull() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertNull(el.parent());
    }

    // parents() excludes #root node and includes ancestors
    @Test
    public void testParents_excludesRoot() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Elements parents = div1.parents();
        for (Element p : parents) {
            assertFalse(p.tagName().equals("#root"));
        }
        assertTrue(parents.size() >= 2);
    }

    // child(int) valid index branch
    @Test
    public void testChild_validIndex_returnsElement() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Element p = div1.child(0);
        assertEquals("p", p.tagName());
    }

    // child(int) out of bounds throws IndexOutOfBoundsException
    @Test
    public void testChild_outOfBounds_throwsException() throws Throwable {
        Element div2 = doc.getElementById("div2");
        try {
            div2.child(0);
            fail("expected exception");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // children() empty-list branch when no element children
    @Test
    public void testChildren_noChildren_returnsEmpty() throws Throwable {
        Element div2 = doc.getElementById("div2");
        assertEquals(0, div2.children().size());
    }

    // textNodes() filters only TextNode children
    @Test
    public void testTextNodes_returnsOnlyTextNodes() throws Throwable {
        Element p = doc.getElementById("div1").child(0);
        List<TextNode> textNodes = p.textNodes();
        assertEquals(2, textNodes.size());
    }

    // select() css query basic match
    @Test
    public void testSelect_findsMatchingElements() throws Throwable {
        Elements found = doc.select("div.header");
        assertEquals(1, found.size());
        assertEquals("div1", found.get(0).id());
    }

    // appendChild(null) Validate.notNull branch throws
    @Test
    public void testAppendChild_null_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.appendChild(null);
            fail("expected exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    // appendChild appends to end of children list
    @Test
    public void testAppendChild_appendsAtEnd() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendChild(new Element(Tag.valueOf("p"), ""));
        el.appendChild(new Element(Tag.valueOf("span"), ""));
        assertEquals("span", el.children().get(1).tagName());
    }

    // insertChildren negative index rolls around to end
    @Test
    public void testInsertChildren_negativeIndex_insertsAtEnd() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendChild(new Element(Tag.valueOf("p"), ""));
        List<Element> newChildren = new ArrayList<Element>();
        newChildren.add(new Element(Tag.valueOf("span"), ""));
        el.insertChildren(-1, newChildren);
        assertEquals("span", el.children().get(1).tagName());
    }

    // insertChildren out-of-bounds index throws IllegalArgumentException
    @Test
    public void testInsertChildren_outOfBounds_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        List<Element> newChildren = new ArrayList<Element>();
        newChildren.add(new Element(Tag.valueOf("span"), ""));
        try {
            el.insertChildren(5, newChildren);
            fail("expected exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    // appendElement creates new element, appends it, returns child
    @Test
    public void testAppendElement_returnsNewChild() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element child = el.appendElement("span");
        assertEquals("span", child.tagName());
        assertEquals(1, el.children().size());
    }

    // appendText adds a TextNode reflected via text()
    @Test
    public void testAppendText_addsTextNode() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("Hello");
        assertEquals("Hello", el.text());
    }

    // append(html) parses fragment and appends resulting nodes
    @Test
    public void testAppend_parsesAndAppendsHtml() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.append("<p>Hi</p>");
        assertEquals("p", el.children().get(0).tagName());
    }

    // empty() clears all child nodes
    @Test
    public void testEmpty_removesAllChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("text");
        el.appendElement("p");
        el.empty();
        assertEquals(0, el.childNodeSize());
    }

    // siblingElements() excludes self from result
    @Test
    public void testSiblingElements_excludesSelf() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Elements siblings = div1.siblingElements();
        for (Element s : siblings) {
            assertNotSame(div1, s);
        }
        assertEquals(1, siblings.size());
    }

    // nextElementSibling() returns next element, null at end of list
    @Test
    public void testNextElementSibling_returnsNextOrNull() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Element div2 = doc.getElementById("div2");
        assertSame(div2, div1.nextElementSibling());
        assertNull(div2.nextElementSibling());
    }

    // previousElementSibling() returns previous element, null at start
    @Test
    public void testPreviousElementSibling_returnsPreviousOrNull() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Element div2 = doc.getElementById("div2");
        assertNull(div1.previousElementSibling());
        assertSame(div1, div2.previousElementSibling());
    }

    // elementSiblingIndex() returns 0-based position among siblings
    @Test
    public void testElementSiblingIndex_returnsPosition() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Element div2 = doc.getElementById("div2");
        assertEquals(0, div1.elementSiblingIndex().intValue());
        assertEquals(1, div2.elementSiblingIndex().intValue());
    }

    // getElementById found and not-found branches
    @Test
    public void testGetElementById_foundAndNotFound() throws Throwable {
        assertNotNull(doc.getElementById("div1"));
        assertNull(doc.getElementById("nope"));
    }

    // getElementsByAttributeValueMatching(String) invalid regex throws IllegalArgumentException
    @Test
    public void testGetElementsByAttributeValueMatching_invalidRegex_throwsException() throws Throwable {
        try {
            doc.getElementsByAttributeValueMatching("id", "[");
            fail("expected exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getElementsMatchingText(String) invalid regex throws IllegalArgumentException
    @Test
    public void testGetElementsMatchingText_invalidRegex_throwsException() throws Throwable {
        try {
            doc.getElementsMatchingText("[");
            fail("expected exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getAllElements includes self and all descendants
    @Test
    public void testGetAllElements_includesSelfAndDescendants() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Elements all = div1.getAllElements();
        assertTrue(all.contains(div1));
        assertTrue(all.size() >= 3);
    }

    // text() combines text of this element and descendants
    @Test
    public void testText_combinesChildText() throws Throwable {
        Element p = doc.getElementById("div1").child(0);
        assertEquals("One Two Three", p.text());
    }

    // ownText() excludes text owned by child elements
    @Test
    public void testOwnText_excludesChildElementText() throws Throwable {
        Element p = doc.getElementById("div1").child(0);
        assertEquals("One Three", p.ownText());
    }

    // text(String) setter clears existing content then sets text
    @Test
    public void testTextSetter_clearsAndSetsText() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendElement("p");
        el.text("New Text");
        assertEquals("New Text", el.text());
        assertEquals(0, el.children().size());
    }

    // hasText() blank vs non-blank branches
    @Test
    public void testHasText_trueAndFalse() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertFalse(el.hasText());
        el.appendText("  ");
        assertFalse(el.hasText());
        el.appendText("hi");
        assertTrue(el.hasText());
    }

    // className() returns empty string when no class attribute set
    @Test
    public void testClassName_absent_returnsEmptyString() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals("", el.className());
    }



    // classNames() splits multiple space-separated classes correctly
    @Test
    public void testClassNames_multipleClasses_splitsCorrectly() throws Throwable {
        Element div1 = doc.getElementById("div1");
        Set<String> names = div1.classNames();
        assertTrue(names.contains("header"));
        assertTrue(names.contains("main"));
        assertEquals(2, names.size());
    }

    // hasClass() is case insensitive, true and false branches
    @Test
    public void testHasClass_caseInsensitive() throws Throwable {
        Element div1 = doc.getElementById("div1");
        assertTrue(div1.hasClass("HEADER"));
        assertFalse(div1.hasClass("nope"));
    }

    // val() non-textarea branch returns value attribute
    @Test
    public void testVal_nonTextarea_returnsAttrValue() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "");
        input.attr("value", "abc");
        assertEquals("abc", input.val());
    }

    // html() returns inner html of the element
    @Test
    public void testHtml_returnsInnerHtml() throws Throwable {
        Element div1 = doc.getElementById("div1");
        String html = div1.html();
        assertTrue(html.indexOf("<p>") >= 0);
    }

    // html(String) setter clears existing content and sets new html
    @Test
    public void testHtmlSetter_clearsAndSetsNewHtml() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("old");
        el.html("<span>new</span>");
        assertEquals("span", el.children().get(0).tagName());
    }

    // equals() is identity based per override
    @Test
    public void testEquals_identityBased() throws Throwable {
        Element el1 = new Element(Tag.valueOf("div"), "");
        Element el2 = new Element(Tag.valueOf("div"), "");
        assertFalse(el1.equals(el2));
        assertTrue(el1.equals(el1));
    }

    // clone() produces an independent copy with separate attribute state
    @Test
    public void testClone_producesIndependentCopy() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("id", "orig");
        Element clone = el.clone();
        clone.attr("id", "cloned");
        assertEquals("orig", el.attr("id"));
        assertEquals("cloned", clone.attr("id"));
    }
}
