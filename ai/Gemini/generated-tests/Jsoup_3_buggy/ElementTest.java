package org.jsoup.nodes;

import org.junit.Test;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ElementTest {

    @Test
    public void testConstructorsAndBasics() throws Throwable {
        Tag tag = Tag.valueOf("div");
        Element el = new Element(tag, "http://example.com");
        assertEquals("div", el.nodeName());
        assertEquals("div", el.tagName());
        assertEquals(tag, el.tag());
        assertTrue(el.isBlock());
        assertEquals("", el.id());

        Attributes attrs = new Attributes();
        attrs.put("id", "test-id");
        Element elWithAttrs = new Element(tag, "http://example.com", attrs);
        assertEquals("test-id", elWithAttrs.id());
    }

    @Test
    public void testParents() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("p"), "http://example.com");
        parent.appendChild(child);

        assertEquals(parent, child.parent());
        Elements parents = child.parents();
        assertEquals(1, parents.size());
        assertEquals(parent, parents.get(0));
    }

    @Test
    public void testChildrenAndIndices() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("span"), "http://example.com");
        parent.appendChild(child1);
        parent.appendChild(child2);

        assertEquals(2, parent.children().size());
        assertEquals(child1, parent.child(0));
        assertEquals(child2, parent.child(1));

        assertEquals(0, child1.elementSiblingIndex().intValue());
        assertEquals(1, child2.elementSiblingIndex().intValue());

        assertEquals(child2, child1.nextElementSibling());
        assertNull(child2.nextElementSibling());

        assertNull(child1.previousElementSibling());
        assertEquals(child1, child2.previousElementSibling());

        assertEquals(child1, child1.firstElementSibling());
        assertEquals(child2, child1.lastElementSibling());

        Elements siblings = child1.siblingElements();
        assertEquals(2, siblings.size());
    }

    @Test
    public void testManipulation() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.appendText("Hello");
        assertEquals("Hello", el.text());

        el.prependText("Start ");
        assertEquals("Start Hello", el.text());

        el.appendElement("span").text("World");
        assertTrue(el.html().contains("<span>World</span>"));

        el.prependElement("b").text("Bold");
        assertTrue(el.html().startsWith("<b>Bold</b>"));

        el.empty();
        assertEquals(0, el.children().size());
        assertEquals("", el.text());
    }

    @Test
    public void testHtmlAndAppendPrependBody() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.html("<p>One</p>");
        assertEquals("<p>One</p>", el.html());

        el.append("<p>Two</p>");
        assertTrue(el.html().contains("Two"));

        el.prepend("<p>Zero</p>");
        assertTrue(el.html().startsWith("<p>Zero</p>"));
    }

    @Test
    public void testClassOperations() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        assertEquals("", el.className());
        assertEquals(0, el.classNames().size());

        el.attr("class", "foo bar");
        assertEquals(2, el.classNames().size());
        assertTrue(el.hasClass("foo"));
        assertTrue(el.hasClass("bar"));
        assertFalse(el.hasClass("baz"));

        el.addClass("baz");
        assertTrue(el.hasClass("baz"));

        el.removeClass("foo");
        assertFalse(el.hasClass("foo"));

        el.toggleClass("bar");
        assertFalse(el.hasClass("bar"));
        el.toggleClass("bar");
        assertTrue(el.hasClass("bar"));

        Set<String> newClasses = new HashSet<String>();
        newClasses.add("a");
        newClasses.add("b");
        el.classNames(newClasses);
        assertEquals(2, el.classNames().size());
        assertTrue(el.hasClass("a"));
    }

    @Test
    public void testFormVal() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "http://example.com");
        input.val("my-value");
        assertEquals("my-value", input.val());

        Element textarea = new Element(Tag.valueOf("textarea"), "http://example.com");
        textarea.val("area-text");
        assertEquals("area-text", textarea.val());
    }

    @Test
    public void testSelectorsAndGetters() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "http://example.com");
        Element p = root.appendElement("p").attr("id", "p-id").attr("class", "my-class");
        p.attr("data-test", "val");
        p.appendText("Some text content");

        assertNotNull(root.getElementById("p-id"));
        assertNull(root.getElementById("non-existent"));

        assertEquals(1, root.getElementsByTag("p").size());
        assertEquals(1, root.getElementsByClass("my-class").size());
        assertEquals(1, root.getElementsByAttribute("data-test").size());
        assertEquals(1, root.getElementsByAttributeValue("data-test", "val").size());
        assertEquals(1, root.getElementsByAttributeValueNot("data-test", "wrong").size());
        assertEquals(1, root.getElementsByAttributeValueStarting("data-test", "va").size());
        assertEquals(1, root.getElementsByAttributeValueEnding("data-test", "al").size());
        assertEquals(1, root.getElementsByAttributeValueContaining("data-test", "a").size());
        assertEquals(1, root.getAllElements().size());

        assertEquals(1, root.getElementsByIndexEquals(0).size());
        assertEquals(0, root.getElementsByIndexLessThan(0).size());
        assertEquals(0, root.getElementsByIndexGreaterThan(0).size());

        assertEquals(1, root.select("p").size());
        assertTrue(root.hasText());
        assertEquals("Some text content", p.text());
    }

    @Test
    public void testData() throws Throwable {
        Element el = new Element(Tag.valueOf("script"), "http://example.com");
        DataNode dataNode = new DataNode("var i=0;", "http://example.com");
        el.appendChild(dataNode);
        assertEquals("var i=0;", el.data());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Element el1 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el3 = new Element(Tag.valueOf("span"), "http://example.com");

        assertTrue(el1.equals(el2));
        assertEquals(el1.hashCode(), el2.hashCode());

        assertFalse(el1.equals(el3));
        assertFalse(el1.equals(null));
        assertFalse(el1.equals(new Object()));
    }

    @Test
    public void testWrap() throws Throwable {
        Element el = new Element(Tag.valueOf("p"), "http://example.com");
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.appendChild(el);

        Element wrapped = el.wrap("<div class='wrapper'></div>");
        assertNotNull(wrapped);
        assertTrue(parent.html().contains("wrapper"));
    }
}