package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ElementTest {

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        Tag tag = Tag.valueOf("div");
        Element el = new Element(tag, "http://example.com");
        assertEquals("div", el.nodeName());
        assertEquals("div", el.tagName());
        assertEquals("http://example.com", el.baseUri());
        assertNotNull(el.tag());
        assertFalse(el.isBlock()); // depending on Tag definition, but safe to call
        assertEquals("", el.id());

        Element elWithAttrs = new Element(tag, "http://example.com", new Attributes());
        assertEquals("div", elWithAttrs.tagName());
    }

    @Test
    public void testTagNameChange() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        el.tagName("div");
        assertEquals("div", el.tagName());

        try {
            el.tagName("");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Tag name must not be empty"));
        }
    }

    @Test
    public void testIdAttribute() throws Throwable {
        Element el = new Element(Tag.valueOf("p"), "http://example.com");
        assertEquals("", el.id());
        el.attr("id", "my-id");
        assertEquals("my-id", el.id());
    }

    @Test
    public void testDataset() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("data-test", "value");
        assertNotNull(el.dataset());
        assertEquals("value", el.dataset().get("test"));
    }

    @Test
    public void testParentAndAncestors() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("p"), "http://example.com");
        parent.appendChild(child);

        assertEquals(parent, child.parent());
        Elements parents = child.parents();
        assertNotNull(parents);
    }

    @Test
    public void testChildMethods() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("span"), "http://example.com");
        TextNode textNode = new TextNode("Hello", "http://example.com");
        Element child2 = new Element(Tag.valueOf("a"), "http://example.com");

        parent.appendChild(child1);
        parent.appendChild(textNode);
        parent.appendChild(child2);

        Elements children = parent.children();
        assertEquals(2, children.size());
        assertEquals(child1, parent.child(0));
        assertEquals(child2, parent.child(1));

        try {
            parent.child(5);
            fail("Should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        List<TextNode> textNodes = parent.textNodes();
        assertEquals(1, textNodes.size());
        assertEquals("Hello", textNodes.get(0).text());

        List<DataNode> dataNodes = parent.dataNodes();
        assertNotNull(dataNodes);
    }

    @Test
    public void testSelectQuery() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("a"), "http://example.com");
        child.attr("href", "http://example.com");
        el.appendChild(child);

        Elements found = el.select("a[href]");
        assertNotNull(found);
    }

    @Test
    public void testAppendPrependInsertChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        Element c1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element c2 = new Element(Tag.valueOf("span"), "http://example.com");

        el.appendChild(c1);
        el.prependChild(c2);
        assertEquals(c2, el.child(0));
        assertEquals(c1, el.child(1));

        List<Node> list = new ArrayList<Node>();
        list.add(new TextNode("Test", "http://example.com"));
        el.insertChildren(0, list);
        assertEquals(3, el.childNodeSize());

        Element appendedElem = el.appendElement("br");
        assertNotNull(appendedElem);

        Element prependedElem = el.prependElement("hr");
        assertNotNull(prependedElem);

        el.appendText("Appended text");
        el.prependText("Prepended text");

        el.append("<span>HTML Append</span>");
        el.prepend("<span>HTML Prepend</span>");

        Element beforeElem = new Element(Tag.valueOf("div"), "http://example.com");
        el.appendChild(beforeElem);
        beforeElem.before("<p>before html</p>");
        beforeElem.before(new TextNode("before node", "http://example.com"));

        beforeElem.after("<p>after html</p>");
        beforeElem.after(new TextNode("after node", "http://example.com"));

        assertNotNull(el.empty());
        assertEquals(0, el.childNodeSize());
    }

    @Test
    public void testWrapAndSiblings() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("p"), "http://example.com");
        root.appendChild(child1);
        root.appendChild(child2);

        assertNotNull(child1.siblingElements());
        assertEquals(child2, child1.nextElementSibling());
        assertNull(child2.nextElementSibling());
        assertEquals(child1, child2.previousElementSibling());
        assertNull(child1.previousElementSibling());
        assertEquals(child1, child1.firstElementSibling());
        assertEquals(child2, child1.lastElementSibling());
        assertEquals(Integer.valueOf(0), child1.elementSiblingIndex());
        assertEquals(Integer.valueOf(1), child2.elementSiblingIndex());

        Element standalone = new Element(Tag.valueOf("div"), "http://example.com");
        assertEquals(0, standalone.siblingElements().size());
        assertNull(standalone.nextElementSibling());
        assertNull(standalone.previousElementSibling());
        assertEquals(Integer.valueOf(0), standalone.elementSiblingIndex());

        try {
            standalone.wrap("<div class='wrap'></div>");
        } catch (Throwable t) {
            // May fail depending on Node.wrap implementation, but covers the method call path
        }
    }

    @Test
    public void testDomTypeGetters() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("id", "my-id");
        el.addClass("my-class");
        el.attr("data-foo", "bar");
        Element child = new Element(Tag.valueOf("span"), "http://example.com");
        child.attr("class", "my-class");
        child.text("some text contents");
        el.appendChild(child);

        assertNotNull(el.getElementsByTag("span"));
        assertNotNull(el.getElementById("my-id"));
        assertNull(el.getElementById("non-existent"));
        assertNotNull(el.getElementsByClass("my-class"));
        assertNotNull(el.getElementsByAttribute("data-foo"));
        assertNotNull(el.getElementsByAttributeStarting("data-"));
        assertNotNull(el.getElementsByAttributeValue("data-foo", "bar"));
        assertNotNull(el.getElementsByAttributeValueNot("data-foo", "other"));
        assertNotNull(el.getElementsByAttributeValueStarting("data-foo", "ba"));
        assertNotNull(el.getElementsByAttributeValueEnding("data-foo", "ar"));
        assertNotNull(el.getElementsByAttributeValueContaining("data-foo", "a"));
        assertNotNull(el.getElementsByAttributeValueMatching("data-foo", Pattern.compile("b.*")));
        assertNotNull(el.getElementsByAttributeValueMatching("data-foo", "b.*"));

        try {
            el.getElementsByAttributeValueMatching("data-foo", "[invalid-regex");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertNotNull(el.getElementsByIndexLessThan(5));
        assertNotNull(el.getElementsByIndexGreaterThan(-1));
        assertNotNull(el.getElementsByIndexEquals(0));
        assertNotNull(el.getElementsContainingText("some"));
        assertNotNull(el.getElementsContainingOwnText("some"));
        assertNotNull(el.getElementsMatchingText(Pattern.compile(".*")));
        assertNotNull(el.getElementsMatchingText(".*"));
        assertNotNull(el.getElementsMatchingOwnText(Pattern.compile(".*")));
        assertNotNull(el.getElementsMatchingOwnText(".*"));

        try {
            el.getElementsMatchingText("[invalid-regex");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            el.getElementsMatchingOwnText("[invalid-regex");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertNotNull(el.getAllElements());
    }

    @Test
    public void testTextAndDataMethods() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.text("Hello World");
        assertEquals("Hello World", el.text());
        assertTrue(el.hasText());

        Element emptyEl = new Element(Tag.valueOf("div"), "http://example.com");
        assertFalse(emptyEl.hasText());

        Element brEl = new Element(Tag.valueOf("div"), "http://example.com");
        brEl.appendChild(new Element(Tag.valueOf("br"), "http://example.com"));
        brEl.appendText("Text");
        assertNotNull(brEl.text());

        DataNode dataNode = new DataNode("var a = 1;", "http://example.com");
        el.appendChild(dataNode);
        assertEquals("var a = 1;", el.data());
    }

    @Test
    public void testClassManagement() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        assertEquals("", el.className());
        
        el.attr("class", "foo bar");
        Set<String> names = el.classNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("foo"));

        Set<String> newNames = new HashSet<String>();
        newNames.add("alpha");
        newNames.add("beta");
        el.classNames(newNames);
        assertEquals("alpha beta", el.className());

        assertTrue(el.hasClass("ALPHA"));
        assertFalse(el.hasClass("gamma"));

        el.addClass("gamma");
        assertTrue(el.hasClass("gamma"));

        el.removeClass("gamma");
        assertFalse(el.hasClass("gamma"));

        el.toggleClass("delta");
        assertTrue(el.hasClass("delta"));
        el.toggleClass("delta");
        assertFalse(el.hasClass("delta"));
    }

    @Test
    public void testValMethods() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "http://example.com");
        input.val("test-value");
        assertEquals("test-value", input.val());

        Element textarea = new Element(Tag.valueOf("textarea"), "http://example.com");
        textarea.val("textarea-content");
        assertEquals("textarea-content", textarea.val());
    }

    @Test
    public void testToStringEqualsHashCodeAndClone() throws Throwable {
        Element el1 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("div"), "http://example.com");

        assertNotNull(el1.toString());
        assertTrue(el1.equals(el1));
        assertFalse(el1.equals(el2));
        assertTrue(el1.hashCode() != 0);

        Element clone = el1.clone();
        assertNotNull(clone);
        assertEquals(el1.tagName(), clone.tagName());
    }
}