package org.jsoup.nodes;

import org.jsoup.parser.Tag;
import org.jsoup.parser.ParseSettings;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class ElementTest {

    @Test
    public void testConstructors() throws Throwable {
        Element el1 = new Element("div");
        assertEquals("div", el1.tagName());
        assertEquals("", el1.baseUri());
        assertNotNull(el1.attributes());

        Tag tag = Tag.valueOf("p");
        Attributes attrs = new Attributes();
        attrs.put("id", "test-id");
        Element el2 = new Element(tag, "http://example.com", attrs);
        assertEquals("p", el2.tagName());
        assertEquals("http://example.com", el2.baseUri());
        assertEquals("test-id", el2.id());

        Element el3 = new Element(tag, "http://example.com");
        assertEquals("p", el3.tagName());
        assertEquals("http://example.com", el3.baseUri());
    }

    @Test
    public void testTagNameChange() throws Throwable {
        Element el = new Element("span");
        assertEquals("span", el.tagName());
        el.tagName("div");
        assertEquals("div", el.tagName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameEmpty() throws Throwable {
        Element el = new Element("span");
        el.tagName("");
    }

    @Test
    public void testIsBlock() throws Throwable {
        Element blockEl = new Element("div");
        assertTrue(blockEl.isBlock());

        Element inlineEl = new Element("span");
        assertFalse(inlineEl.isBlock());
    }

    @Test
    public void testAttributesAndDataset() throws Throwable {
        Element el = new Element("div");
        el.attr("data-foo", "bar");
        el.attr("class", "my-class");
        el.attr("boolAttr", true);

        assertEquals("bar", el.dataset().get("foo"));
        assertEquals("my-class", el.attr("class"));
        assertEquals("", el.attr("boolAttr"));
        assertTrue(el.hasAttributes());

        el.attr("boolAttr", false);
        assertFalse(el.attributes().hasKey("boolAttr"));
    }

    @Test
    public void testParentsAndAncestors() throws Throwable {
        Element root = new Element("div");
        Element parent = new Element("p");
        Element child = new Element("span");

        root.appendChild(parent);
        parent.appendChild(child);

        Elements parents = child.parents();
        assertEquals(2, parents.size());
        assertEquals("p", parents.get(0).tagName());
        assertEquals("div", parents.get(1).tagName());
    }

    @Test
    public void testChildManipulationAndAccess() throws Throwable {
        Element parent = new Element("div");
        Element child1 = new Element("span");
        Element child2 = new Element("a");
        TextNode textNode = new TextNode("hello");

        parent.appendChild(child1);
        parent.appendChild(textNode);
        parent.appendChild(child2);

        assertEquals(3, parent.childNodeSize());
        assertEquals(child1, parent.child(0));
        assertEquals(child2, parent.child(1));
        assertEquals(2, parent.children().size());

        List<TextNode> textNodes = parent.textNodes();
        assertEquals(1, textNodes.size());
        assertEquals("hello", textNodes.get(0).text());

        List<DataNode> dataNodes = parent.dataNodes();
        assertTrue(dataNodes.isEmpty());

        DataNode dataNode = new DataNode("var a = 1;");
        parent.appendChild(dataNode);
        assertEquals(1, parent.dataNodes().size());

        parent.empty();
        assertEquals(0, parent.childNodeSize());
    }

    @Test
    public void testInsertChildren() throws Throwable {
        Element parent = new Element("div");
        Element c1 = new Element("span");
        Element c2 = new Element("a");

        parent.insertChildren(0, Arrays.asList(c1, c2));
        assertEquals(2, parent.childNodeSize());
        assertEquals(c1, parent.child(0));

        Element c3 = new Element("b");
        parent.insertChildren(-1, c3);
        assertEquals(3, parent.childNodeSize());
        assertEquals(c3, parent.child(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertChildrenOutOfBounds() throws Throwable {
        Element parent = new Element("div");
        parent.insertChildren(5, new Element("span"));
    }

    @Test
    public void testAppendPrependHelpers() throws Throwable {
        Element parent = new Element("div");
        parent.appendElement("span").attr("id", "s1");
        parent.prependElement("a").attr("id", "a1");

        assertEquals(2, parent.children().size());
        assertEquals("a1", parent.child(0).id());
        assertEquals("s1", parent.child(1).id());

        parent.appendText("text1");
        parent.prependText("text2");
        assertEquals("text2", parent.textNodes().get(0).text());
        assertEquals("text1", parent.textNodes().get(1).text());

        parent.append("<b>bold</b>");
        parent.prepend("<i>italic</i>");
        assertTrue(parent.html().contains("italic"));
        assertTrue(parent.html().contains("bold"));
    }

    @Test
    public void testCssSelector() throws Throwable {
        Element root = new Element("div");
        root.attr("id", "myId");
        assertEquals("#myId", root.cssSelector());

        Element parent = new Element("div");
        Element child = new Element("span");
        child.addClass("cls");
        parent.appendChild(child);

        String selector = child.cssSelector();
        assertTrue(selector.contains("span.cls"));
    }

    @Test
    public void testSiblingsNavigation() throws Throwable {
        Element parent = new Element("div");
        Element e1 = new Element("span");
        Element e2 = new Element("span");
        Element e3 = new Element("span");

        parent.appendChild(e1);
        parent.appendChild(e2);
        parent.appendChild(e3);

        assertEquals(2, e1.siblingElements().size());
        assertEquals(e2, e1.nextElementSibling());
        assertNull(e1.previousElementSibling());
        assertEquals(e2, e3.previousElementSibling());
        assertNull(e3.nextElementSibling());
        assertEquals(e1, e2.firstElementSibling());
        assertEquals(e3, e2.lastElementSibling());
        assertEquals(1, e2.elementSiblingIndex());

        Element orphan = new Element("p");
        assertNull(orphan.nextElementSibling());
        assertNull(orphan.previousElementSibling());
        assertTrue(orphan.siblingElements().isEmpty());
        assertEquals(0, orphan.elementSiblingIndex());
    }

    @Test
    public void testGetElementsByTagAndId() throws Throwable {
        Element root = new Element("div");
        Element child = new Element("span");
        child.attr("id", "target");
        root.appendChild(child);

        Elements byTag = root.getElementsByTag("span");
        assertEquals(1, byTag.size());
        assertEquals(child, byTag.get(0));

        Element byId = root.getElementById("target");
        assertEquals(child, byId);

        assertNull(root.getElementById("nonexistent"));
    }

    @Test
    public void testGetElementsByClassAndAttributes() throws Throwable {
        Element root = new Element("div");
        Element child1 = new Element("div");
        child1.addClass("foo bar");
        child1.attr("href", "http://example.com");
        child1.attr("data-test", "val1");
        root.appendChild(child1);

        assertEquals(1, root.getElementsByClass("FOO").size());
        assertEquals(1, root.getElementsByAttribute("href").size());
        assertEquals(1, root.getElementsByAttributeStarting("data-").size());
        assertEquals(1, root.getElementsByAttributeValue("href", "http://example.com").size());
        assertEquals(1, root.getElementsByAttributeValueNot("href", "other").size());
        assertEquals(1, root.getElementsByAttributeValueStarting("href", "http").size());
        assertEquals(1, root.getElementsByAttributeValueEnding("href", ".com").size());
        assertEquals(1, root.getElementsByAttributeValueContaining("href", "example").size());
        assertEquals(1, root.getElementsByAttributeValueMatching("href", Pattern.compile("https?://.*")).size());
        assertEquals(1, root.getElementsByAttributeValueMatching("href", "https?://.*").size());

        try {
            root.getElementsByAttributeValueMatching("href", "[invalid");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern syntax error"));
        }
    }

    @Test
    public void testGetElementsByIndexAndText() throws Throwable {
        Element root = new Element("div");
        Element c0 = new Element("span");
        Element c1 = new Element("span");
        Element c2 = new Element("span");
        c0.text("Hello World");
        root.appendChild(c0);
        root.appendChild(c1);
        root.appendChild(c2);

        assertEquals(1, root.getElementsByIndexLessThan(1).size());
        assertEquals(1, root.getElementsByIndexGreaterThan(1).size());
        assertEquals(1, root.getElementsByIndexEquals(1).size());

        assertEquals(1, root.getElementsContainingText("World").size());
        assertEquals(1, root.getElementsContainingOwnText("World").size());
        assertEquals(1, root.getElementsMatchingText(Pattern.compile("Hello.*")).size());
        assertEquals(1, root.getElementsMatchingText("Hello.*").size());
        assertEquals(1, root.getElementsMatchingOwnText(Pattern.compile("Hello.*")).size());
        assertEquals(1, root.getElementsMatchingOwnText("Hello.*").size());

        try {
            root.getElementsMatchingText("[invalid");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern syntax error"));
        }

        try {
            root.getElementsMatchingOwnText("[invalid");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern syntax error"));
        }

        assertEquals(4, root.getAllElements().size());
    }

    @Test
    public void testTextAndOwnTextOperations() throws Throwable {
        Element p = new Element("p");
        p.appendText("Hello ");
        Element b = p.appendElement("b");
        b.appendText("there");
        p.appendText(" now!");

        assertEquals("Hello there now!", p.text());
        assertEquals("Hello  now!", p.ownText());
        assertTrue(p.hasText());

        Element emptyEl = new Element("div");
        assertFalse(emptyEl.hasText());

        p.text("New Text Only");
        assertEquals("New Text Only", p.text());
    }

    @Test
    public void testDataRetrieval() throws Throwable {
        Element script = new Element("script");
        script.appendChild(new DataNode("int x = 0;"));
        script.appendChild(new Comment("a comment"));
        
        Element inner = new Element("div");
        inner.appendChild(new DataNode("inner data"));
        script.appendChild(inner);

        String data = script.data();
        assertTrue(data.contains("int x = 0;"));
        assertTrue(data.contains("a comment"));
        assertTrue(data.contains("inner data"));
    }

    @Test
    public void testClassManipulations() throws Throwable {
        Element el = new Element("div");
        el.attr("class", "one two");

        assertEquals("one two", el.className());
        Set<String> names = el.classNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("one"));

        Set<String> newNames = new HashSet<String>();
        newNames.add("three");
        newNames.add("four");
        el.classNames(newNames);
        assertEquals("three four", el.className());

        assertTrue(el.hasClass("three"));
        assertTrue(el.hasClass("THREE"));
        assertFalse(el.hasClass("one"));

        el.addClass("five");
        assertTrue(el.hasClass("five"));

        el.removeClass("five");
        assertFalse(el.hasClass("five"));

        el.toggleClass("six");
        assertTrue(el.hasClass("six"));
        el.toggleClass("six");
        assertFalse(el.hasClass("six"));
    }

    @Test
    public void testFormElementVal() throws Throwable {
        Element input = new Element("input");
        input.val("foo");
        assertEquals("foo", input.val());

        Element textarea = new Element("textarea");
        textarea.val("bar text");
        assertEquals("bar text", textarea.val());
    }

    @Test
    public void testHtmlAndToStringAndClone() throws Throwable {
        Element el = new Element("div");
        el.appendElement("p").text("paragraph");

        String html = el.html();
        assertTrue(html.contains("<p>paragraph</p>"));
        assertEquals(el.html(), el.toString());

        Element clone = el.clone();
        assertNotNull(clone);
        assertEquals(el.tagName(), clone.tagName());
        assertEquals(el.childNodeSize(), clone.childNodeSize());
    }

    @Test
    public void testQuerySelectorMethods() throws Throwable {
        Element el = new Element("div");
        el.appendElement("span").attr("class", "test");

        Elements selected = el.select("span.test");
        assertEquals(1, selected.size());

        Element first = el.selectFirst("span.test");
        assertNotNull(first);

        assertNull(el.selectFirst("div.none"));

        assertTrue(el.selectFirst("span.test").is("span"));
        assertTrue(el.selectFirst("span.test").is(new Evaluator.Class("test")));
    }
}