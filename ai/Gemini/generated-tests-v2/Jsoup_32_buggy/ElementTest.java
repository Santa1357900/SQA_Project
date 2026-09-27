package org.jsoup.nodes;

import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class ElementTest {

    @Test
    public void testConstructorsAndBasicProperties() throws Throwable {
        Tag tag = Tag.valueOf("div");
        Element el = new Element(tag, "http://example.com");
        assertEquals("div", el.nodeName());
        assertEquals("div", el.tagName());
        assertEquals("http://example.com", el.baseUri());
        assertFalse(el.isBlock());
        assertEquals("", el.id());

        Element el2 = new Element(tag, "http://example.com", new Attributes());
        assertEquals("div", el2.tagName());

        el.tagName("span");
        assertEquals("span", el.tagName());

        try {
            el.tagName("");
            fail("Expected exception for empty tag name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testAttributesAndDataset() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("id", "my-id");
        el.attr("data-foo", "bar");
        assertEquals("my-id", el.id());

        Map<String, String> dataset = el.dataset();
        assertEquals(1, dataset.size());
        assertEquals("bar", dataset.get("foo"));
    }

    @Test
    public void testParentsAndHierarchy() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("p"), "http://example.com");
        parent.appendChild(child);

        assertEquals(parent, child.parent());
        Elements parents = child.parents();
        assertEquals(1, parents.size());
        assertEquals(parent, parents.get(0));

        Element standalone = new Element(Tag.valueOf("span"), "http://example.com");
        assertEquals(0, standalone.parents().size());
    }

    @Test
    public void testChildManipulationAndFiltering() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("span"), "http://example.com");
        TextNode textNode = new TextNode("Hello", "http://example.com");
        DataNode dataNode = new DataNode("var a = 1;", "http://example.com");

        el.appendChild(child1);
        el.appendChild(textNode);
        el.appendChild(child2);
        el.appendChild(dataNode);

        assertEquals(2, el.children().size());
        assertEquals(child1, el.child(0));
        assertEquals(child2, el.child(1));

        try {
            el.child(5);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertTrue(true);
        }

        List<TextNode> textNodes = el.textNodes();
        assertEquals(1, textNodes.size());
        assertEquals("Hello", textNodes.get(0).text());

        List<DataNode> dataNodes = el.dataNodes();
        assertEquals(1, dataNodes.size());
        assertEquals("var a = 1;", dataNodes.get(0).getWholeData());

        Element prependChild = new Element(Tag.valueOf("a"), "http://example.com");
        el.prependChild(prependChild);
        assertEquals(prependChild, el.child(0));

        List<Node> insertList = new ArrayList<Node>();
        Element insertEl = new Element(Tag.valueOf("b"), "http://example.com");
        insertList.add(insertEl);
        el.insertChildren(1, insertList);
        assertEquals(insertEl, el.child(1));

        el.insertChildren(-1, insertList);
        assertEquals(insertEl, el.child(el.children().size() - 1));

        try {
            el.insertChildren(100, insertList);
            fail("Expected out of bounds");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            el.insertChildren(0, null);
            fail("Expected null check");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testAppendPretendElementAndText() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        Element appEl = el.appendElement("p");
        assertEquals("p", appEl.tagName());
        assertEquals(1, el.children().size());

        Element prepEl = el.prependElement("span");
        assertEquals("span", prepEl.tagName());
        assertEquals("span", el.child(0).tagName());

        el.appendText("Text appended");
        assertTrue(el.text().contains("Text appended"));

        el.prependText("Text prepended");
        assertTrue(el.text().contains("Text prepended"));
    }

    @Test
    public void testHtmlParsingHelpers() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.append("<p>Appended</p>");
        assertEquals(1, el.children().size());

        el.prepend("<b>Prepended</b>");
        assertEquals("b", el.child(0).tagName());

        Element beforeEl = new Element(Tag.valueOf("div"), "http://example.com");
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        parent.appendChild(beforeEl);
        beforeEl.before("<span id='before'></span>");
        assertEquals("span", parent.child(0).tagName());

        beforeEl.before(new TextNode("node-before", "http://example.com"));
        beforeEl.after("<span id='after'></span>");
        beforeEl.after(new TextNode("node-after", "http://example.com"));

        assertNotNull(beforeEl.wrap("<div class='wrapper'></div>"));
    }

    @Test
    public void testEmptyAndSiblings() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("span"), "http://example.com");
        parent.appendChild(child1);
        parent.appendChild(child2);

        assertEquals(0, child1.siblingElements().size());
        assertEquals(1, child1.nextElementSibling().tagName().equals("span") ? 0 : 1);
        assertEquals(child2, child1.nextElementSibling());
        assertEquals(child1, child2.previousElementSibling());
        assertEquals(child1, child2.firstElementSibling());
        assertEquals(child2, child1.lastElementSibling());
        assertEquals(Integer.valueOf(0), child1.elementSiblingIndex());
        assertEquals(Integer.valueOf(1), child2.elementSiblingIndex());

        Element standalone = new Element(Tag.valueOf("div"), "http://example.com");
        assertEquals(0, standalone.siblingElements().size());
        assertNull(standalone.nextElementSibling());
        assertNull(standalone.previousElementSibling());
        assertEquals(Integer.valueOf(0), standalone.elementSiblingIndex());

        child1.empty();
        assertEquals(0, child1.childNodes.size());
    }

    @Test
    public void testCollectorMethods() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("id", "main");
        el.attr("class", "container active");
        el.attr("data-test", "val");

        Element child = el.appendElement("a");
        child.attr("href", "http://example.com");
        child.attr("class", "link");
        child.text("Click here example.com");

        assertNotNull(el.getElementsByTag("a"));
        assertNotNull(el.getElementById("main"));
        assertNull(el.getElementById("non-existent"));
        assertNotNull(el.getElementsByClass("container"));
        assertNotNull(el.getElementsByAttribute("href"));
        assertNotNull(el.getElementsByAttributeStarting("data-"));
        assertNotNull(el.getElementsByAttributeValue("href", "http://example.com"));
        assertNotNull(el.getElementsByAttributeValueNot("href", "other"));
        assertNotNull(el.getElementsByAttributeValueStarting("href", "http"));
        assertNotNull(el.getElementsByAttributeValueEnding("href", "com"));
        assertNotNull(el.getElementsByAttributeValueContaining("href", "example"));
        assertNotNull(el.getElementsByAttributeValueMatching("href", Pattern.compile("http.*")));
        assertNotNull(el.getElementsByAttributeValueMatching("href", "http.*"));

        try {
            el.getElementsByAttributeValueMatching("href", "[invalid-regex");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        assertNotNull(el.getElementsByIndexLessThan(5));
        assertNotNull(el.getElementsByIndexGreaterThan(-1));
        assertNotNull(el.getElementsByIndexEquals(0));
        assertNotNull(el.getElementsContainingText("Click"));
        assertNotNull(el.getElementsContainingOwnText("Click"));
        assertNotNull(el.getElementsMatchingText(Pattern.compile("Click")));
        assertNotNull(el.getElementsMatchingText("Click"));
        assertNotNull(el.getElementsMatchingOwnText(Pattern.compile("Click")));
        assertNotNull(el.getElementsMatchingOwnText("Click"));

        try {
            el.getElementsMatchingText("[invalid-regex");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            el.getElementsMatchingOwnText("[invalid-regex");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        assertNotNull(el.getAllElements());
    }

    @Test
    public void testTextAndDataMethods() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.append("<p>Hello <b>there</b> now!</p>");
        el.appendChild(new TextNode("   ", "http://example.com"));
        
        String text = el.text();
        assertTrue(text.contains("Hello"));

        String ownText = el.ownText();
        assertNotNull(ownText);

        assertFalse(new Element(Tag.valueOf("div"), "http://example.com").hasText());
        assertTrue(el.hasText());

        Element script = el.appendElement("script");
        script.appendChild(new DataNode("console.log('hi');", "http://example.com"));
        assertEquals("console.log('hi');", script.data());

        el.text("New text content");
        assertEquals("New text content", el.text());

        try {
            el.text((String) null);
            fail("Expected null check");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testClassAndFormValMethods() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("class", "foo bar");
        assertEquals("foo bar", el.className());

        Set<String> classNames = el.classNames();
        assertEquals(2, classNames.size());
        assertTrue(classNames.contains("foo"));

        Set<String> newClasses = new HashSet<String>();
        newClasses.add("alpha");
        newClasses.add("beta");
        el.classNames(newClasses);
        assertEquals("alpha beta", el.className());

        assertTrue(el.hasClass("ALPHA"));
        assertFalse(el.hasClass("gamma"));

        el.addClass("gamma");
        assertTrue(el.hasClass("gamma"));

        el.removeClass("alpha");
        assertFalse(el.hasClass("alpha"));

        el.toggleClass("beta");
        assertFalse(el.hasClass("beta"));
        el.toggleClass("beta");
        assertTrue(el.hasClass("beta"));

        Element textarea = new Element(Tag.valueOf("textarea"), "http://example.com");
        textarea.text("textarea value");
        assertEquals("textarea value", textarea.val());
        textarea.val("new textarea value");
        assertEquals("new textarea value", textarea.val());

        Element input = new Element(Tag.valueOf("input"), "http://example.com");
        input.attr("value", "input value");
        assertEquals("input value", input.val());
        input.val("new input value");
        assertEquals("new input value", input.attr("value"));
    }

    @Test
    public void testRenderingAndOutput() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("id", "test");
        assertNotNull(el.html());
        el.html("<span>inner</span>");
        assertEquals("<span>inner</span>", el.html());

        assertNotNull(el.toString());
        assertNotNull(el.hashCode());
        
        Element clone = el.clone();
        assertNotNull(clone);
        assertEquals(el.tagName(), clone.tagName());

        Element br = new Element(Tag.valueOf("br"), "http://example.com");
        Document doc = Document.createShell("http://example.com");
        StringBuilder sb = new StringBuilder();
        br.outerHtmlHead(sb, 0, doc.outputSettings());
        br.outerHtmlTail(sb, 0, doc.outputSettings());
        assertTrue(sb.length() > 0);
    }
}