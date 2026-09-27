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
        Tag tag = Tag.valueOf("div");
        Element el = new Element(tag, "http://example.com");
        assertEquals("div", el.nodeName());
        assertEquals("div", el.tagName());
        assertEquals("http://example.com", el.baseUri());
        assertNotNull(el.tag());
        assertFalse(el.isBlock()); // depending on Tag definition, but safe to call

        Attributes attributes = new Attributes();
        attributes.put("id", "test-id");
        Element el2 = new Element(tag, "http://example.com", attributes);
        assertEquals("test-id", el2.id());
    }

    @Test
    public void testTagNameMutation() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        el.tagName("div");
        assertEquals("div", el.tagName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameEmpty() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        el.tagName("");
    }

    @Test
    public void testIdWithoutAttribute() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        assertEquals("", el.id());
    }

    @Test
    public void testDataset() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("data-test", "value");
        assertEquals("value", el.dataset().get("test"));
    }

    @Test
    public void testParents() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("p"), "http://example.com");
        parent.appendChild(child);

        Elements parents = child.parents();
        assertEquals(1, parents.size());
        assertEquals(parent, parents.get(0));
    }

    @Test
    public void testChildMethods() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("span"), "http://example.com");
        TextNode textNode = new TextNode("hello", "http://example.com");
        Element child2 = new Element(Tag.valueOf("a"), "http://example.com");

        parent.appendChild(child1);
        parent.appendChild(textNode);
        parent.appendChild(child2);

        assertEquals(2, parent.children().size());
        assertEquals(child1, parent.child(0));
        assertEquals(child2, parent.child(1));

        List<TextNode> textNodes = parent.textNodes();
        assertEquals(1, textNodes.size());
        assertEquals("hello", textNodes.get(0).getWholeText());

        List<DataNode> dataNodes = parent.dataNodes();
        assertEquals(0, dataNodes.size());
    }

    @Test
    public void testSelectQuery() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("a"), "http://example.com");
        child.attr("href", "http://example.com");
        parent.appendChild(child);

        Elements found = parent.select("a[href]");
        assertEquals(1, found.size());
        assertEquals(child, found.get(0));
    }

    @Test
    public void testPrependChild() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("span"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("a"), "http://example.com");

        parent.appendChild(child1);
        parent.prependChild(child2);

        assertEquals(child2, parent.child(0));
        assertEquals(child1, parent.child(1));
    }

    @Test
    public void testInsertChildren() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("span"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("a"), "http://example.com");

        List<Node> list = new ArrayList<Node>();
        list.add(child1);
        list.add(child2);

        parent.insertChildren(0, list);
        assertEquals(2, parent.children().size());
        assertEquals(child1, parent.child(0));
        assertEquals(child2, parent.child(1));

        // Test negative index roll around
        Element child3 = new Element(Tag.valueOf("b"), "http://example.com");
        List<Node> list2 = new ArrayList<Node>();
        list2.add(child3);
        parent.insertChildren(-1, list2);
        assertEquals(child3, parent.child(parent.children().size() - 1));
    }

    @Test
    public void testAppendPrependElementAndText() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.appendElement("span").attr("id", "s1");
        parent.prependElement("a").attr("id", "a1");
        parent.appendText("text1");
        parent.prependText("text0");

        assertEquals("a1", parent.child(0).id());
        assertEquals("s1", parent.child(1).id());
        assertEquals(2, parent.children().size());
    }

    @Test
    public void testAppendAndPrependHtml() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.append("<p>Hello</p>");
        assertEquals(1, parent.children().size());
        assertEquals("p", parent.child(0).tagName());

        parent.prepend("<span>Start</span>");
        assertEquals("span", parent.child(0).tagName());
    }

    @Test
    public void testBeforeAndAfterMutations() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        parent.appendChild(el);

        Element beforeEl = el.before("<p>before</p>");
        assertNotNull(beforeEl);

        Element afterEl = el.after("<p>after</p>");
        assertNotNull(afterEl);

        Element beforeNode = el.before(new TextNode("bnode", "http://example.com"));
        assertNotNull(beforeNode);

        Element afterNode = el.after(new TextNode("anode", "http://example.com"));
        assertNotNull(afterNode);
    }

    @Test
    public void testEmptyAndWrap() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.appendChild(new Element(Tag.valueOf("span"), "http://example.com"));
        assertEquals(1, parent.children().size());

        parent.empty();
        assertEquals(0, parent.children().size());

        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        Element wrapped = el.wrap("<div class=\"wrapper\"></div>");
        assertNotNull(wrapped);
    }

    @Test
    public void testCssSelector() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.attr("id", "myid");
        assertEquals("#myid", parent.cssSelector());

        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        el.addClass("myclass");
        parent.appendChild(el);
        // Test class and tag selector path
        assertNotNull(el.cssSelector());
    }

    @Test
    public void testSiblingNavigation() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element el1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("p"), "http://example.com");
        Element el3 = new Element(Tag.valueOf("p"), "http://example.com");

        parent.appendChild(el1);
        parent.appendChild(el2);
        parent.appendChild(el3);

        assertEquals(2, el1.siblingElements().size());
        assertEquals(el2, el1.nextElementSibling());
        assertNull(el3.nextElementSibling());
        assertEquals(el2, el3.previousElementSibling());
        assertNull(el1.previousElementSibling());
        assertEquals(el1, el1.firstElementSibling());
        assertEquals(el3, el1.lastElementSibling());
        assertEquals(Integer.valueOf(0), el1.elementSiblingIndex());
        assertEquals(Integer.valueOf(1), el2.elementSiblingIndex());
    }

    @Test
    public void testSiblingNavigationNoParent() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        assertEquals(0, el.siblingElements().size());
        assertNull(el.nextElementSibling());
        assertNull(el.previousElementSibling());
        assertEquals(Integer.valueOf(0), el.elementSiblingIndex());
    }

    @Test
    public void testGetElementsByTagAndId() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("span"), "http://example.com");
        child.attr("id", "span-id");
        parent.appendChild(child);

        Elements spans = parent.getElementsByTag("SPAN");
        assertEquals(1, spans.size());
        assertEquals(child, spans.get(0));

        Element foundId = parent.getElementById("span-id");
        assertEquals(child, foundId);

        assertNull(parent.getElementById("non-existent"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetElementsByTagEmpty() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.getElementsByTag("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetElementByIdEmpty() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.getElementById("");
    }

    @Test
    public void testGetElementsByVariousAttributes() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("a"), "http://example.com");
        child.attr("href", "http://example.com/path");
        child.attr("class", "foo bar");
        parent.appendChild(child);

        assertFalse(parent.getElementsByAttribute("href").isEmpty());
        assertFalse(parent.getElementsByAttributeStarting("hr").isEmpty());
        assertFalse(parent.getElementsByAttributeValue("href", "http://example.com/path").isEmpty());
        assertFalse(parent.getElementsByAttributeValueNot("href", "other").isEmpty());
        assertFalse(parent.getElementsByAttributeValueStarting("href", "http://").isEmpty());
        assertFalse(parent.getElementsByAttributeValueEnding("href", "path").isEmpty());
        assertFalse(parent.getElementsByAttributeValueContaining("href", "example").isEmpty());
        assertFalse(parent.getElementsByAttributeValueMatching("href", Pattern.compile("https?://.*")).isEmpty());
        assertFalse(parent.getElementsByAttributeValueMatching("href", "https?://.*").isEmpty());

        try {
            parent.getElementsByAttributeValueMatching("href", "[invalid-regex");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern syntax error"));
        }
    }

    @Test
    public void testGetElementsByIndexAndText() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("p"), "http://example.com");
        child1.text("Hello World");
        Element child2 = new Element(Tag.valueOf("p"), "http://example.com");
        child2.text("Second Paragraph");
        parent.appendChild(child1);
        parent.appendChild(child2);

        assertFalse(parent.getElementsByIndexLessThan(2).isEmpty());
        assertFalse(parent.getElementsByIndexGreaterThan(0).isEmpty());
        assertFalse(parent.getElementsByIndexEquals(0).isEmpty());
        assertFalse(parent.getElementsContainingText("Hello").isEmpty());
        assertFalse(parent.getElementsContainingOwnText("World").isEmpty());
        assertFalse(parent.getElementsMatchingText(Pattern.compile("Hello.*")).isEmpty());
        assertFalse(parent.getElementsMatchingText("Hello.*").isEmpty());
        assertFalse(parent.getElementsMatchingOwnText(Pattern.compile("World.*")).isEmpty());
        assertFalse(parent.getElementsMatchingOwnText("World.*").isEmpty());
        assertFalse(parent.getAllElements().isEmpty());

        try {
            parent.getElementsMatchingText("[invalid-regex");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern syntax error"));
        }

        try {
            parent.getElementsMatchingOwnText("[invalid-regex");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern syntax error"));
        }
    }

    @Test
    public void testTextAndOwnTextAndData() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.appendText("ParentText ");
        Element child = new Element(Tag.valueOf("b"), "http://example.com");
        child.appendText("ChildText");
        parent.appendChild(child);

        assertEquals("ParentText ChildText", parent.text());
        assertEquals("ParentText", parent.ownText());
        assertTrue(parent.hasText());

        Element script = new Element(Tag.valueOf("script"), "http://example.com");
        script.appendChild(new DataNode("console.log('test');", "http://example.com"));
        parent.appendChild(script);
        assertEquals("console.log('test');", parent.data());

        parent.text("New Text");
        assertEquals("New Text", parent.text());
    }

    @Test
    public void testHasTextBlank() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.appendText("   ");
        assertFalse(parent.hasText());
    }

    @Test
    public void classNamesAndAttributesManipulation() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("class", "one two");
        assertEquals("one two", el.className());

        Set<String> names = el.classNames();
        assertTrue(names.contains("one"));
        assertTrue(names.contains("two"));

        Set<String> newNames = new HashSet<String>();
        newNames.add("three");
        el.classNames(newNames);
        assertEquals("three", el.className());

        assertTrue(el.hasClass("three"));
        assertFalse(el.hasClass("nonexistent"));

        el.addClass("four");
        assertTrue(el.hasClass("four"));

        el.removeClass("four");
        assertFalse(el.hasClass("four"));

        el.toggleClass("three");
        assertFalse(el.hasClass("three"));
        el.toggleClass("three");
        assertTrue(el.hasClass("three"));
    }

    @Test
    public void testFormVal() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "http://example.com");
        input.val("test-value");
        assertEquals("test-value", input.val());

        Element textarea = new Element(Tag.valueOf("textarea"), "http://example.com");
        textarea.val("area-value");
        assertEquals("area-value", textarea.val());
    }

    @Test
    public void testHtmlMethodsAndToString() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.append("<p>Hello</p>");
        assertNotNull(parent.html());

        parent.html("<p>Updated</p>");
        assertEquals("<p>Updated</p>", parent.html());

        assertNotNull(parent.toString());
        assertNotNull(parent.clone());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Element el1 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("div"), "http://example.com");
        
        // Element overrides equals to check `this == o` based on super.equals if extended, let's verify standard contract
        assertTrue(el1.equals(el1));
        assertFalse(el1.equals(null));
        assertFalse(el1.equals(new Object()));
        assertEquals(el1.hashCode(), el1.hashCode());
    }
}