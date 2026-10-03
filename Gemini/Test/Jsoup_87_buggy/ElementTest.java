package org.jsoup.nodes;

import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class ElementTest {

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        Element el = new Element("div");
        assertEquals("div", el.tagName());
        assertEquals("div", el.nodeName());
        assertEquals("", el.baseUri());
        assertNotNull(el.attributes());

        Tag tag = Tag.valueOf("span");
        Element el2 = new Element(tag, "http://example.com", new Attributes());
        assertEquals("span", el2.tagName());
        assertEquals("http://example.com", el2.baseUri());
        assertTrue(el2.hasAttributes());

        Element el3 = new Element(tag, "http://example.com");
        assertEquals("span", el3.tagName());
        assertEquals("http://example.com", el3.baseUri());
    }

    @Test
    public void testTagNameChange() throws Throwable {
        Element el = new Element("div");
        el.tagName("p");
        assertEquals("p", el.tagName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameEmpty() throws Throwable {
        Element el = new Element("div");
        el.tagName("");
    }

    @Test
    public void testBlockAndIdAndAttr() throws Throwable {
        Element el = new Element("div");
        assertTrue(el.isBlock());
        
        Element p = new Element("p");
        assertFalse(p.isBlock());

        el.attr("id", "myId");
        assertEquals("myId", el.id());

        Element elNoId = new Element("span");
        assertEquals("", elNoId.id());

        el.attr("data-test", "val");
        assertEquals("val", el.dataset().get("test"));

        el.attr("boolAttr", true);
        assertEquals("", el.attr("boolAttr"));

        el.attr("boolAttr", false);
        assertFalse(el.hasAttr("boolAttr"));
    }

    @Test
    public void testParentsAndHierarchy() throws Throwable {
        Element root = new Element("div");
        Element parent = new Element("p");
        Element child = new Element("span");

        root.appendChild(parent);
        parent.appendChild(child);

        assertEquals(parent, child.parent());
        assertEquals(root, parent.parent());

        Elements parents = child.parents();
        assertEquals(2, parents.size());
        assertEquals("p", parents.get(0).tagName());
        assertEquals("div", parents.get(1).tagName());
    }

    @Test
    public void testChildrenAndIndices() throws Throwable {
        Element parent = new Element("div");
        Element c1 = new Element("span");
        Element c2 = new Element("a");
        Element c3 = new Element("b");

        parent.appendChild(c1);
        parent.appendChild(c2);
        parent.appendChild(c3);

        assertEquals(3, parent.childNodeSize());
        assertEquals(c1, parent.child(0));
        assertEquals(c2, parent.child(1));
        assertEquals(c3, parent.child(2));

        Elements children = parent.children();
        assertEquals(3, children.size());

        assertEquals(c2, c1.nextElementSibling());
        assertEquals(c1, c2.previousElementSibling());
        assertNull(c1.previousElementSibling());
        assertNull(c3.nextElementSibling());

        assertEquals(c1, parent.firstElementSibling());
        assertEquals(c3, parent.lastElementSibling());
        assertEquals(0, c1.elementSiblingIndex());
        assertEquals(1, c2.elementSiblingIndex());

        Element orphan = new Element("div");
        assertNull(orphan.nextElementSibling());
        assertNull(orphan.previousElementSibling());
        assertEquals(0, orphan.elementSiblingIndex());
        assertEquals(0, orphan.siblingElements().size());
        assertNull(orphan.firstElementSibling());
        assertNull(orphan.lastElementSibling());
    }

    @Test
    public void testTextNodesAndDataNodes() throws Throwable {
        Element el = new Element("div");
        TextNode tn1 = new TextNode("One");
        DataNode dn1 = new DataNode("data content");
        TextNode tn2 = new TextNode("Two");

        el.appendChild(tn1);
        el.appendChild(dn1);
        el.appendChild(tn2);

        List<TextNode> textNodes = el.textNodes();
        assertEquals(2, textNodes.size());
        assertEquals("One", textNodes.get(0).text());

        List<DataNode> dataNodes = el.dataNodes();
        assertEquals(1, dataNodes.size());
        assertEquals("data content", dataNodes.get(0).getWholeData());
    }

    @Test
    public void testManipulationMethods() throws Throwable {
        Element parent = new Element("div");
        Element child = new Element("span");

        parent.appendChild(child);
        assertEquals(1, parent.childNodeSize());

        parent.empty();
        assertEquals(0, parent.childNodeSize());

        parent.prependChild(child);
        assertEquals(1, parent.childNodeSize());
        assertEquals(child, parent.child(0));

        Element appended = parent.appendElement("p");
        assertEquals("p", appended.tagName());
        assertEquals(2, parent.childNodeSize());

        Element prepended = parent.prependElement("a");
        assertEquals("a", prepended.tagName());
        assertEquals(3, parent.childNodeSize());
        assertEquals("a", parent.child(0).tagName());

        parent.appendText("Hello Text");
        assertTrue(parent.childNodeSize() > 3);

        parent.prependText("Prepend Text");
        assertTrue(parent.childNodeSize() > 4);

        Element newEl = new Element("div");
        child.appendTo(newEl);
        assertEquals(1, newEl.childNodeSize());

        List<Node> list = new ArrayList<Node>();
        list.add(new TextNode("A"));
        list.add(new TextNode("B"));
        Element insertEl = new Element("div");
        insertEl.insertChildren(0, list);
        assertEquals(2, insertEl.childNodeSize());

        Element insertEl2 = new Element("div");
        insertEl2.insertChildren(-1, new TextNode("X"));
        assertEquals(1, insertEl2.childNodeSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertChildrenOutOfBounds() throws Throwable {
        Element el = new Element("div");
        List<Node> list = new ArrayList<Node>();
        list.add(new TextNode("A"));
        el.insertChildren(10, list);
    }

    @Test
    public void testHtmlAndTextMethods() throws Throwable {
        Element el = new Element("div");
        el.html("<p>Hello</p>");
        assertEquals("<p>Hello</p>", el.html());

        el.text("New Text");
        assertEquals("New Text", el.text());
        assertTrue(el.hasText());

        Element emptyEl = new Element("div");
        assertFalse(emptyEl.hasText());
        assertEquals("", emptyEl.text());
        assertEquals("", emptyEl.wholeText());
        assertEquals("", emptyEl.ownText());
        assertEquals("", emptyEl.data());

        Element brEl = new Element("div");
        brEl.appendText("Line1");
        brEl.appendElement("br");
        brEl.appendText("Line2");
        assertEquals("Line1 Line2", brEl.text());
        assertEquals("Line1 Line2", brEl.ownText());
    }

    @Test
    public void testClassesAndCssSelector() throws Throwable {
        Element el = new Element("div");
        el.attr("class", "foo bar baz");
        assertEquals("foo bar baz", el.className());

        Set<String> classes = el.classNames();
        assertEquals(3, classes.size());
        assertTrue(classes.contains("foo"));

        Set<String> newClasses = new HashSet<String>();
        newClasses.add("alpha");
        newClasses.add("beta");
        el.classNames(newClasses);
        assertEquals("alpha beta", el.className());

        el.classNames(new HashSet<String>());
        assertFalse(el.hasAttr("class"));

        Element el2 = new Element("div");
        el2.addClass("test-class");
        assertTrue(el2.hasClass("test-class"));
        assertTrue(el2.hasClass("TEST-CLASS"));

        el2.removeClass("test-class");
        assertFalse(el2.hasClass("test-class"));

        el2.toggleClass("toggle-me");
        assertTrue(el2.hasClass("toggle-me"));
        el2.toggleClass("toggle-me");
        assertFalse(el2.hasClass("toggle-me"));

        Element idEl = new Element("div");
        idEl.attr("id", "uniqueId");
        assertEquals("#uniqueId", idEl.cssSelector());

        Element classEl = new Element("div");
        classEl.addClass("myClass");
        assertEquals("div.myClass", classEl.cssSelector());
    }

    @Test
    public void testFormValMethods() throws Throwable {
        Element input = new Element("input");
        input.val("testValue");
        assertEquals("testValue", input.val());

        Element textarea = new Element("textarea");
        textarea.val("textareaContent");
        assertEquals("textareaContent", textarea.val());
    }

    @Test
    public void testQueryMethods() throws Throwable {
        Element root = new Element("div");
        Element child = root.appendElement("a").attr("href", "http://example.com").attr("id", "linkId");
        root.appendElement("div").addClass("myClass");

        assertNotNull(root.getElementById("linkId"));
        assertNull(root.getElementById("nonExistent"));

        assertEquals(1, root.getElementsByTag("a").size());
        assertEquals(1, root.getElementsByClass("myClass").size());
        assertEquals(1, root.getElementsByAttribute("href").size());
        assertEquals(1, root.getElementsByAttributeStarting("href").size());
        assertEquals(1, root.getElementsByAttributeValue("href", "http://example.com").size());
        assertEquals(1, root.getElementsByAttributeValueNot("href", "other").size());
        assertEquals(1, root.getElementsByAttributeValueStarting("href", "http").size());
        assertEquals(1, root.getElementsByAttributeValueEnding("href", "com").size());
        assertEquals(1, root.getElementsByAttributeValueContaining("href", "example").size());
        assertEquals(1, root.getElementsByAttributeValueMatching("href", Pattern.compile("https?://.*")).size());
        assertEquals(1, root.getElementsByAttributeValueMatching("href", "https?://.*").size());

        assertEquals(1, root.getElementsByIndexLessThan(5).size());
        assertEquals(0, root.getElementsByIndexGreaterThan(5).size());
        assertEquals(1, root.getElementsByIndexEquals(0).size());

        root.text("Searchable text content here");
        assertEquals(1, root.getElementsContainingText("searchable").size());
        assertEquals(1, root.getElementsContainingOwnText("Searchable").size());
        assertEquals(1, root.getElementsMatchingText(Pattern.compile("Searchable.*")).size());
        assertEquals(1, root.getElementsMatchingText("Searchable.*").size());
        assertEquals(1, root.getElementsMatchingOwnText(Pattern.compile("Searchable.*")).size());
        assertEquals(1, root.getElementsMatchingOwnText("Searchable.*").size());
        assertEquals(2, root.getAllElements().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPattern() throws Throwable {
        Element root = new Element("div");
        root.getElementsByAttributeValueMatching("href", "[invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTextRegexPattern() throws Throwable {
        Element root = new Element("div");
        root.getElementsMatchingText("[invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidOwnTextRegexPattern() throws Throwable {
        Element root = new Element("div");
        root.getElementsMatchingOwnText("[invalid");
    }

    @Test
    public void testCloningAndShallowCloning() throws Throwable {
        Element el = new Element("div");
        el.attr("class", "test");
        Element child = el.appendElement("span");

        Element clone = el.clone();
        assertEquals(1, clone.childNodeSize());
        assertEquals("test", clone.className());

        Element shallow = el.shallowClone();
        assertEquals(0, shallow.childNodeSize());
        assertEquals("test", shallow.className());
    }
}