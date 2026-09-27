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
        assertFalse(el.isBlock()); // div is block depending on Tag definition, but let's test tag retrieval
        assertNotNull(el.tag());

        Attributes attrs = new Attributes();
        attrs.put("id", "my-id");
        Element el2 = new Element(tag, "http://example.com", attrs);
        assertEquals("my-id", el2.id());
    }

    @Test
    public void testTagNameChange() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        el.tagName("div");
        assertEquals("div", el.tagName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameEmptyValidation() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "http://example.com");
        el.tagName("");
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
    public void testChildOperations() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("span"), "http://example.com");

        parent.appendChild(child1);
        parent.prependChild(child2);

        assertEquals(2, parent.children().size());
        assertEquals(child2, parent.child(0));
        assertEquals(child1, parent.child(1));
    }

    @Test
    public void testTextNodesAndDataNodes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        TextNode textNode = new TextNode("Hello", "http://example.com");
        DataNode dataNode = new DataNode("var a = 1;", "http://example.com");

        el.appendChild(textNode);
        el.appendChild(dataNode);

        List<TextNode> tNodes = el.textNodes();
        assertEquals(1, tNodes.size());
        assertEquals("Hello", tNodes.get(0).getWholeText());

        List<DataNode> dNodes = el.dataNodes();
        assertEquals(1, dNodes.size());
        assertEquals("var a = 1;", dNodes.get(0).getWholeData());
    }

    @Test
    public void testInsertChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        List<Node> newChildren = new ArrayList<Node>();
        newChildren.add(new TextNode("A", "http://example.com"));
        newChildren.add(new TextNode("B", "http://example.com"));

        el.insertChildren(0, newChildren);
        assertEquals(2, el.childNodeSize());
        assertEquals("A", ((TextNode) el.childNode(0)).getWholeText());
    }

    @Test
    public void testAppendPrependElementAndText() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.appendElement("span").attr("id", "span1");
        el.prependElement("p").attr("id", "p1");
        el.appendText("TextEnd");
        el.prependText("TextStart");

        assertEquals(4, el.childNodeSize());
        assertEquals("TextStart", ((TextNode) el.childNode(0)).getWholeText());
        assertEquals("p", el.childNode(1).nodeName());
        assertEquals("span", el.childNode(2).nodeName());
        assertEquals("TextEnd", ((TextNode) el.childNode(3)).getWholeText());
    }

    @Test
    public void testHtmlAppendPrependParse() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.append("<span>Appended</span>");
        el.prepend("<span>Prepended</span>");

        assertEquals(2, el.children().size());
        assertEquals("Prepended", el.child(0).text());
        assertEquals("Appended", el.child(1).text());
    }

    @Test
    public void testEmpty() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.appendChild(new TextNode("Content", "http://example.com"));
        assertFalse(el.childNodes.isEmpty());

        el.empty();
        assertTrue(el.childNodes.isEmpty());
    }

    @Test
    public void testCssSelector() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("id", "unique-id");
        assertEquals("#unique-id", el.cssSelector());

        Element el2 = new Element(Tag.valueOf("div"), "http://example.com");
        el2.addClass("my-class");
        assertEquals("div.my-class", el2.cssSelector());
    }

    @Test
    public void testSiblingElements() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child1 = new Element(Tag.valueOf("p"), "http://example.com");
        Element child2 = new Element(Tag.valueOf("span"), "http://example.com");
        parent.appendChild(child1);
        parent.appendChild(child2);

        Elements siblings = child1.siblingElements();
        assertEquals(1, siblings.size());
        assertEquals(child2, siblings.get(0));

        assertEquals(child2, child1.nextElementSibling());
        assertNull(child1.previousElementSibling());
        assertEquals(child1, child2.previousElementSibling());
        assertNull(child2.nextElementSibling());

        assertEquals(child1, child1.firstElementSibling());
        assertEquals(child2, child1.lastElementSibling());
        assertEquals(Integer.valueOf(0), child1.elementSiblingIndex());
        assertEquals(Integer.valueOf(1), child2.elementSiblingIndex());
    }

    @Test
    public void testGetElementsByMethods() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("a"), "http://example.com");
        child.attr("id", "link-id");
        child.attr("class", "btn primary");
        child.attr("href", "http://example.com");
        child.appendText("Click here");
        root.appendChild(child);

        assertNotNull(root.getElementsByTag("a").get(0));
        assertNotNull(root.getElementById("link-id"));
        assertNotNull(root.getElementsByClass("btn").get(0));
        assertNotNull(root.getElementsByAttribute("href").get(0));
        assertNotNull(root.getElementsByAttributeStarting("hr").get(0));
        assertNotNull(root.getElementsByAttributeValue("href", "http://example.com").get(0));
        assertNotNull(root.getElementsByAttributeValueNot("href", "http://wrong.com").get(0));
        assertNotNull(root.getElementsByAttributeValueStarting("href", "http://").get(0));
        assertNotNull(root.getElementsByAttributeValueEnding("href", ".com").get(0));
        assertNotNull(root.getElementsByAttributeValueContaining("href", "example").get(0));
        assertNotNull(root.getElementsByAttributeValueMatching("href", Pattern.compile("http://.*")).get(0));
        assertNotNull(root.getElementsByAttributeValueMatching("href", "http://.*").get(0));

        assertNotNull(root.getElementsByIndexLessThan(5).get(0));
        assertNotNull(root.getElementsByIndexGreaterThan(-1).get(0));
        assertNotNull(root.getElementsByIndexEquals(0).get(0));

        assertNotNull(root.getElementsContainingText("Click").get(0));
        assertNotNull(root.getElementsContainingOwnText("Click").get(0));
        assertNotNull(root.getElementsMatchingText(Pattern.compile("Click.*")).get(0));
        assertNotNull(root.getElementsMatchingText("Click.*").get(0));
        assertNotNull(root.getElementsMatchingOwnText(Pattern.compile("Click.*")).get(0));
        assertNotNull(root.getElementsMatchingOwnText("Click.*").get(0));
        assertNotNull(root.getAllElements());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPatternSyntax() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "http://example.com");
        root.getElementsMatchingText("[invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidAttributeRegexPatternSyntax() throws Throwable {
        Element root = new Element(Tag.valueOf("div"), "http://example.com");
        root.getElementsByAttributeValueMatching("class", "[invalid");
    }

    @Test
    public void testTextAndOwnText() throws Throwable {
        Element p = new Element(Tag.valueOf("p"), "http://example.com");
        p.appendText("Hello ");
        Element b = new Element(Tag.valueOf("b"), "http://example.com");
        b.appendText("there");
        p.appendChild(b);
        p.appendText(" now!");

        assertEquals("Hello there now!", p.text());
        assertEquals("Hello  now!", p.ownText());
        assertTrue(p.hasText());
    }

    @Test
    public void testDataMethod() throws Throwable {
        Element script = new Element(Tag.valueOf("script"), "http://example.com");
        script.appendChild(new DataNode("console.log('test');", "http://example.com"));
        assertEquals("console.log('test');", script.data());
    }

    @Test
    public void testClassManipulations() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.attr("class", "foo bar");

        assertEquals("foo bar", el.className());
        Set<String> classNames = el.classNames();
        assertTrue(classNames.contains("foo"));
        assertTrue(classNames.contains("bar"));

        Set<String> newClasses = new HashSet<String>();
        newClasses.add("alpha");
        newClasses.add("beta");
        el.classNames(newClasses);
        assertTrue(el.hasClass("alpha"));

        assertTrue(el.hasClass("BETA")); // case insensitive check

        el.addClass("gamma");
        assertTrue(el.hasClass("gamma"));

        el.removeClass("alpha");
        assertFalse(el.hasClass("alpha"));

        el.toggleClass("beta");
        assertFalse(el.hasClass("beta"));
        el.toggleClass("beta");
        assertTrue(el.hasClass("beta"));
    }

    @Test
    public void testValMethods() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "http://example.com");
        input.val("testValue");
        assertEquals("testValue", input.val());

        Element textarea = new Element(Tag.valueOf("textarea"), "http://example.com");
        textarea.val("areaValue");
        assertEquals("areaValue", textarea.val());
    }

    @Test
    public void testHtmlGetAndSet() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com");
        el.html("<span>Inner</span>");
        assertEquals("<span>Inner</span>", el.html());
    }

    @Test
    public void testToStringAndEqualsAndClone() throws Throwable {
        Element el1 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("div"), "http://example.com");

        assertEquals(el1, el2);
        assertEquals(el1.hashCode(), el2.hashCode());
        assertNotNull(el1.toString());

        Element clone = el1.clone();
        assertEquals(el1, clone);
        assertNotSame(el1, clone);
    }
}