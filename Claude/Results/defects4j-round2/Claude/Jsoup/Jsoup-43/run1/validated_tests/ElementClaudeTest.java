package org.jsoup.nodes;

import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class ElementClaudeTest {

    // covers Validate.notNull(tag) throw branch in constructor
    @Test
    public void testConstructor_nullTag_throwsException() throws Throwable {
        try {
            new Element((Tag) null, "");
            fail("expected exception for null tag");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers Validate.notEmpty branch in tagName(String)
    @Test
    public void testTagName_emptyString_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.tagName("");
            fail("expected exception for empty tag name");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers normal rename path and chaining return
    @Test
    public void testTagName_rename_updatesTagAndName() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "");
        Element result = el.tagName("div");
        assertEquals("div", el.tagName());
        assertSame(el, result);
    }

    // covers tag() getter
    @Test
    public void testTag_returnsTagObject() throws Throwable {
        Tag tag = Tag.valueOf("p");
        Element el = new Element(tag, "");
        assertEquals(tag, el.tag());
    }

    // covers isBlock() delegation for block vs inline tags
    @Test
    public void testIsBlock_divIsBlock_spanIsInline() throws Throwable {
        Element div = new Element(Tag.valueOf("div"), "");
        Element span = new Element(Tag.valueOf("span"), "");
        assertTrue(div.isBlock());
        assertFalse(span.isBlock());
    }

    // covers id() when attribute not set
    @Test
    public void testId_notSet_returnsEmptyString() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals("", el.id());
    }

    // covers id() when attribute set
    @Test
    public void testId_set_returnsValue() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("id", "header");
        assertEquals("header", el.id());
    }

    // covers attr(key,value) chaining and effect via className()
    @Test
    public void testAttr_setClass_updatesClassName() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element result = el.attr("class", "foo bar");
        assertSame(el, result);
        assertEquals("foo bar", el.className());
    }

    // covers dataset() filtered view of data- attributes
    @Test
    public void testDataset_getsDataAttributes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("data-package", "jsoup");
        Map<String, String> dataset = el.dataset();
        assertEquals("jsoup", dataset.get("package"));
    }

    // covers parent() when no parent exists
    @Test
    public void testParent_noParent_returnsNull() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertNull(el.parent());
    }

    // covers parent() when a parent exists
    @Test
    public void testParent_withParent_returnsParent() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child = new Element(Tag.valueOf("p"), "");
        parent.appendChild(child);
        assertSame(parent, child.parent());
    }

    // covers parents() excluding the synthetic #root element
    @Test
    public void testParents_excludesRoot() throws Throwable {
        Document doc = Jsoup.parse("<html><body><div id=a><p id=b>text</p></div></body></html>");
        Element p = doc.getElementById("b");
        Elements parents = p.parents();
        for (int i = 0; i < parents.size(); i++) {
            assertFalse(parents.get(i).tagName().equals("#root"));
        }
        assertTrue(parents.size() > 0);
    }

    // covers child(index) valid index path
    @Test
    public void testChild_validIndex_returnsElement() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child0 = new Element(Tag.valueOf("p"), "");
        Element child1 = new Element(Tag.valueOf("span"), "");
        parent.appendChild(child0);
        parent.appendChild(child1);
        assertSame(child1, parent.child(1));
    }

    // covers child(index) out of bounds exception path
    @Test
    public void testChild_outOfBounds_throwsException() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        try {
            parent.child(0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // covers children() filtering out non-Element nodes
    @Test
    public void testChildren_mixedNodes_onlyReturnsElements() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        parent.appendText("hello");
        Element child = new Element(Tag.valueOf("span"), "");
        parent.appendChild(child);
        Elements children = parent.children();
        assertEquals(1, children.size());
        assertSame(child, children.get(0));
    }

    // covers textNodes() filtering to only TextNode children
    @Test
    public void testTextNodes_returnsTextNodesOnly() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        parent.appendText("hello");
        parent.appendElement("span");
        List<TextNode> textNodes = parent.textNodes();
        assertEquals(1, textNodes.size());
        assertEquals("hello", textNodes.get(0).getWholeText());
    }

    // covers dataNodes() picking up script contents
    @Test
    public void testDataNodes_scriptElement_returnsDataNode() throws Throwable {
        Document doc = Jsoup.parse("<script>var x = 1;</script>");
        Elements scripts = doc.getElementsByTag("script");
        Element script = scripts.get(0);
        List<DataNode> dataNodes = script.dataNodes();
        assertEquals(1, dataNodes.size());
    }

    // covers select() with a valid CSS query
    @Test
    public void testSelect_validQuery_returnsMatchingElements() throws Throwable {
        Document doc = Jsoup.parse("<div class='a'><p>1</p></div><div class='b'><p>2</p></div>");
        Elements divs = doc.select("div.a");
        assertEquals(1, divs.size());
    }

    // covers select() throwing on an invalid CSS query
    @Test
    public void testSelect_invalidQuery_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.select(":::invalid:::");
            fail("expected SelectorParseException");
        } catch (Selector.SelectorParseException expected) {
        }
    }

    // covers appendChild() adding to the end of children
    @Test
    public void testAppendChild_addsAtEnd() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        Element child2 = new Element(Tag.valueOf("span"), "");
        parent.appendChild(child1);
        parent.appendChild(child2);
        assertEquals(2, parent.children().size());
        assertSame(child2, parent.child(1));
    }

    // covers appendChild() null check exception path
    @Test
    public void testAppendChild_nullChild_throwsException() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        try {
            parent.appendChild(null);
            fail("expected exception for null child");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers prependChild() adding to start of children
    @Test
    public void testPrependChild_addsAtStart() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        Element child2 = new Element(Tag.valueOf("span"), "");
        parent.appendChild(child1);
        parent.prependChild(child2);
        assertSame(child2, parent.child(0));
    }

    // covers insertChildren() negative index rollaround branch
    @Test
    public void testInsertChildren_negativeIndex_insertsFromEnd() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        parent.appendChild(child1);
        List<Node> toInsert = new ArrayList<Node>();
        Element child2 = new Element(Tag.valueOf("span"), "");
        toInsert.add(child2);
        parent.insertChildren(-1, toInsert);
        assertSame(child2, parent.child(1));
    }

    // covers insertChildren() out of bounds Validate.isTrue exception path
    @Test
    public void testInsertChildren_outOfBounds_throwsException() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        List<Node> toInsert = new ArrayList<Node>();
        try {
            parent.insertChildren(5, toInsert);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers appendElement() creating and appending new element
    @Test
    public void testAppendElement_createsAndAppends() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child = parent.appendElement("span");
        assertEquals("span", child.tagName());
        assertSame(child, parent.child(0));
    }

    // covers appendText() creating and appending a TextNode
    @Test
    public void testAppendText_addsTextNodeAtEnd() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("hello");
        assertEquals("hello", el.text());
    }

    // covers prependText() creating and prepending a TextNode
    @Test
    public void testPrependText_addsTextNodeAtStart() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("world");
        el.prependText("hello ");
        assertEquals("hello world", el.text());
    }

    // covers append(html) parsing fragment and appending nodes
    @Test
    public void testAppend_parsesAndAppendsHtml() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.append("<p>one</p>");
        assertEquals(1, el.children().size());
        assertEquals("p", el.child(0).tagName());
    }

    // covers empty() clearing all children
    @Test
    public void testEmpty_removesAllChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("hi");
        el.empty();
        assertEquals(0, el.childNodeSize());
    }

    // covers cssSelector() id() branch returning "#id"
    @Test
    public void testCssSelector_withId_returnsIdSelector() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("id", "main");
        assertEquals("#main", el.cssSelector());
    }

    // covers cssSelector() no-parent branch with class suffix appended
    @Test
    public void testCssSelector_noParentNoId_returnsTagAndClassOnly() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "foo");
        assertEquals("div.foo", el.cssSelector());
    }

    // covers siblingElements() when there is no parent
    @Test
    public void testSiblingElements_noParent_returnsEmpty() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals(0, el.siblingElements().size());
    }

    // covers nextElementSibling() when this is the last child
    @Test
    public void testNextElementSibling_isLast_returnsNull() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        parent.appendChild(child1);
        assertNull(child1.nextElementSibling());
    }

    // covers previousElementSibling() when this is the first child
    @Test
    public void testPreviousElementSibling_isFirst_returnsNull() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        parent.appendChild(child1);
        assertNull(child1.previousElementSibling());
    }



    // covers firstElementSibling() with multiple children returning the first
    @Test
    public void testFirstElementSibling_multipleChildren_returnsFirst() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        Element child2 = new Element(Tag.valueOf("span"), "");
        parent.appendChild(child1);
        parent.appendChild(child2);
        assertSame(child1, child2.firstElementSibling());
    }



    // covers lastElementSibling() with multiple children returning the last
    @Test
    public void testLastElementSibling_multipleChildren_returnsLast() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        Element child2 = new Element(Tag.valueOf("span"), "");
        parent.appendChild(child1);
        parent.appendChild(child2);
        assertSame(child2, child1.lastElementSibling());
    }

    // covers elementSiblingIndex() returning correct position
    @Test
    public void testElementSiblingIndex_returnsCorrectIndex() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        Element child2 = new Element(Tag.valueOf("span"), "");
        parent.appendChild(child1);
        parent.appendChild(child2);
        assertEquals(Integer.valueOf(1), child2.elementSiblingIndex());
    }

    // covers getElementsByTag() case-insensitive matching
    @Test
    public void testGetElementsByTag_findsMatchingElements() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p></div>");
        Elements ps = doc.getElementsByTag("P");
        assertEquals(2, ps.size());
    }

    // covers getElementById() found branch
    @Test
    public void testGetElementById_findsMatchingElement() throws Throwable {
        Document doc = Jsoup.parse("<div id='main'><p>hi</p></div>");
        Element el = doc.getElementById("main");
        assertNotNull(el);
        assertEquals("div", el.tagName());
    }

    // covers getElementById() not-found branch returning null
    @Test
    public void testGetElementById_notFound_returnsNull() throws Throwable {
        Document doc = Jsoup.parse("<div><p>hi</p></div>");
        assertNull(doc.getElementById("missing"));
    }

    // covers text() combining and normalising nested text content
    @Test
    public void testText_getCombinedText() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello  <b>there</b> now! </p>");
        Element p = doc.getElementsByTag("p").get(0);
        assertEquals("Hello there now!", p.text());
    }

    // covers hasClass() case-insensitive matching
    @Test
    public void testHasClass_caseInsensitive_returnsTrue() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "Header Round");
        assertTrue(el.hasClass("header"));
    }

    // covers html(String) clearing existing content and setting new parsed HTML
    @Test
    public void testHtml_setInnerHtml_replacesContent() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("old");
        Element result = el.html("<p>new</p>");
        assertSame(el, result);
        assertEquals(1, el.children().size());
        assertEquals("p", el.child(0).tagName());
    }
}
