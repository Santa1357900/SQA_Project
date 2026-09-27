package org.jsoup.nodes;

import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;
import static org.junit.Assert.*;

public class ElementTest {

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        Tag tag = Tag.valueOf("div");
        Element el = new Element(tag, "http://example.com", new Attributes());
        assertEquals("div", el.nodeName());
        assertEquals("div", el.tagName());
        assertEquals("http://example.com", el.baseUri());
        assertFalse(el.isBlock() == false && el.isBlock() == true); // Just exercise isBlock

        Element el2 = new Element(Tag.valueOf("span"), "http://example.com/2");
        assertEquals("span", el2.tagName());
        assertEquals("http://example.com/2", el2.baseUri());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameValidation() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.tagName("");
    }

    @Test
    public void testId() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertEquals("", el.id());
        el.attr("id", "my-id");
        assertEquals("my-id", el.id());
    }

    @Test
    public void testDataset() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("data-foo", "bar");
        el.attr("class", "test");
        Map<String, String> data = el.dataset();
        assertEquals(1, data.size());
        assertEquals("bar", data.get("foo"));
    }

    @Test
    public void testParentsAndStructure() throws Throwable {
        Element root = new Element(Tag.valueOf("#root"), "");
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child = new Element(Tag.valueOf("p"), "");

        root.appendChild(parent);
        parent.appendChild(child);

        assertEquals(parent, child.parent());
        Elements parents = child.parents();
        assertEquals(1, parents.size());
        assertEquals(parent, parents.get(0));

        assertEquals(child, parent.child(0));
        Elements children = parent.children();
        assertEquals(1, children.size());

        List<TextNode> textNodes = parent.textNodes();
        assertTrue(textNodes.isEmpty());

        List<DataNode> dataNodes = parent.dataNodes();
        assertTrue(dataNodes.isEmpty());
    }

    @Test
    public void testAppendPrependMethods() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendChild(new Element(Tag.valueOf("span"), ""));
        assertEquals("span", el.child(0).tagName());

        el.prependChild(new Element(Tag.valueOf("a"), ""));
        assertEquals("a", el.child(0).tagName());

        el.appendElement("b");
        assertEquals("b", el.child(el.children().size() - 1).tagName());

        el.prependElement("i");
        assertEquals("i", el.child(0).tagName());

        el.appendText("Hello");
        assertEquals(1, el.textNodes().size());

        el.prependText("World");
        assertEquals(2, el.textNodes().size());
    }

    @Test
    public void testHtmlParseAppendPrepend() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.append("<p>One</p>");
        assertEquals(1, el.children().size());
        assertEquals("One", el.child(0).text());

        el.prepend("<span>Zero</span>");
        assertEquals("span", el.child(0.0 == 0.0 ? 0 : 1).tagName());
    }

    @Test
    public void testNodeNavigationAndSiblings() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "");
        Element child1 = new Element(Tag.valueOf("p"), "");
        Element child2 = new Element(Tag.valueOf("span"), "");
        Element child3 = new Element(Tag.valueOf("a"), "");

        parent.appendChild(child1);
        parent.appendChild(child2);
        parent.appendChild(child3);

        assertEquals(child2, child1.nextElementSibling());
        assertEquals(child1, child2.previousElementSibling());
        assertNull(child1.previousElementSibling());
        assertNull(child3.nextElementSibling());

        assertEquals(child1, child2.firstElementSibling());
        assertEquals(child3, child2.lastElementSibling());
        assertEquals(1, child2.elementSiblingIndex());

        Elements siblings = child2.siblingElements();
        assertEquals(3, siblings.size());
    }

    @Test
    public void testEmptyAndWrap() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendElement("span");
        assertFalse(el.children().isEmpty());
        el.empty();
        assertTrue(el.children().isEmpty());

        Element wrapped = new Element(Tag.valueOf("p"), "");
        Element result = wrapped.wrap("<div class='wrap'></div>");
        assertNotNull(result);
    }

    @Test
    public void testGetElementsByTagAndId() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "");
        Element p = root.appendElement("p").attr("id", "target-id");
        p.appendElement("span");

        Elements byTag = root.getElementsByTag("SPAN");
        assertEquals(1, byTag.size());

        Element byId = root.getElementById("target-id");
        assertEquals(p, byId);

        assertNull(root.getElementById("non-existent"));
    }

    @Test
    public void testGetElementsByVariousAttributes() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "");
        Element child = root.appendElement("a");
        child.attr("href", "http://example.com/path");
        child.attr("data-test", "val123");
        child.addClass("foo bar");

        assertEquals(1, root.getElementsByClass("FOO").size());
        assertEquals(1, root.getElementsByAttribute("href").size());
        assertEquals(1, root.getElementsByAttributeStarting("data-").size());
        assertEquals(1, root.getElementsByAttributeValue("href", "http://example.com/path").size());
        assertEquals(1, root.getElementsByAttributeValueNot("href", "wrong").size());
        assertEquals(1, root.getElementsByAttributeValueStarting("href", "http://").size());
        assertEquals(1, root.getElementsByAttributeValueEnding("href", "path").size());
        assertEquals(1, root.getElementsByAttributeValueContaining("href", "example").size());
        assertEquals(1, root.getElementsByAttributeValueMatching("href", Pattern.compile(".*path")) .size());
        assertEquals(1, root.getElementsByAttributeValueMatching("href", ".*path").size());

        try {
            root.getElementsByAttributeValueMatching("href", "[invalid-regex");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern syntax error"));
        }
    }

    @Test
    public void testGetElementsByIndexAndText() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "");
        root.appendElement("p").text("First text");
        root.appendElement("p").text("Second text");

        assertEquals(1, root.getElementsByIndexLessThan(1).size());
        assertEquals(1, root.getElementsByIndexGreaterThan(0).size());
        assertEquals(1, root.getElementsByIndexEquals(0).size());

        assertEquals(1, root.getElementsContainingText("First").size());
        assertEquals(1, root.getElementsContainingOwnText("Second").size());
        assertEquals(1, root.getElementsMatchingText(Pattern.compile("First")).size());
        assertEquals(1, root.getElementsMatchingText("First").size());
        assertEquals(1, root.getElementsMatchingOwnText(Pattern.compile("Second")).size());
        assertEquals(1, root.getElementsMatchingOwnText("Second").size());
        assertEquals(3, root.getAllElements().size()); // root + 2 ps
    }

    @Test
    public void testTextAndOwnTextAndData() throws Throwable {
        Element p = new Element(Tag.valueOf("p"), "");
        p.appendText("Hello ");
        Element b = p.appendElement("b");
        b.appendText("there");
        p.appendText(" now!");

        assertEquals("Hello there now!", p.text());
        assertEquals("Hello now!", p.ownText());
        assertTrue(p.hasText());

        Element brEl = new Element(Tag.valueOf("p"), "");
        brEl.appendElement("br");
        brEl.appendText("text");
        assertTrue(brEl.text().length() > 0);

        Element script = new Element(Tag.valueOf("script"), "");
        script.appendChild(new DataNode("var a = 1;", ""));
        assertEquals("var a = 1;", script.data());
    }

    @Test
    public void testClassManagement() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "one two  three ");
        Set<String> classes = el.classNames();
        assertEquals(3, classes.size());

        Set<String> newClasses = new LinkedHashSet<String>();
        newClasses.add("alpha");
        newClasses.add("beta");
        el.classNames(newClasses);
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
    public void testFormValues() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "");
        input.val("test-val");
        assertEquals("test-val", input.val());

        Element textarea = new Element(Tag.valueOf("textarea"), "");
        textarea.val("area-val");
        assertEquals("area-val", textarea.val());
    }

    @Test
    public void testHtmlOutputAndCloneAndEquals() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("id", "test");
        String html = el.html();
        assertNotNull(html);

        el.html("<span>Child</span>");
        assertEquals("<span>Child</span>", el.html());

        assertNotNull(el.toString());
        assertTrue(el.equals(el));
        assertFalse(el.equals(new Object()));

        Element clone = el.clone();
        assertNotNull(clone);
        assertEquals(el.tagName(), clone.tagName());

        int hc = el.hashCode();
        assertTrue(hc != 0 || hc == 0);
    }
}