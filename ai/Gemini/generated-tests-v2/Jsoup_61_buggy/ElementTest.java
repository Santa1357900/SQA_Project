package org.jsoup.nodes;

import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ElementTest {

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        Element el = new Element("div");
        assertEquals("div", el.tagName());
        assertEquals("div", el.nodeName());
        assertFalse(el.isBlock()); // Tag.valueOf("div") in jsoup depends on tag definitions, but let's check normal methods

        Tag tag = Tag.valueOf("span");
        Element el2 = new Element(tag, "http://example.com");
        assertEquals("span", el2.tagName());
        assertEquals("http://example.com", el2.baseUri());

        Attributes attrs = new Attributes();
        attrs.put("id", "myId");
        Element el3 = new Element(tag, "http://example.com", attrs);
        assertEquals("myId", el3.id());
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
    public void testAttributesAndDataset() throws Throwable {
        Element el = new Element("div");
        el.attr("data-test", "value");
        el.attr("class", "foo bar");

        Map<String, String> dataset = el.dataset();
        assertEquals("value", dataset.get("test"));

        el.attr("boolAttr", true);
        assertTrue(el.attributes().hasKey("boolAttr"));

        el.attr("boolAttr", false);
        assertFalse(el.attributes().hasKey("boolAttr"));
    }

    @Test
    public void testParentsAndHierarchy() throws Throwable {
        Element parent = new Element("div");
        Element child = new Element("span");
        Element grandchild = new Element("a");

        parent.appendChild(child);
        child.appendChild(grandchild);

        assertEquals(parent, child.parent());
        assertEquals(child, grandchild.parent());

        Elements parents = grandchild.parents();
        assertEquals(2, parents.size());
        assertEquals(child, parents.get(0));
        assertEquals(parent, parents.get(1));
    }

    @Test
    public void testChildrenAndFiltering() throws Throwable {
        Element parent = new Element("div");
        TextNode textNode = new TextNode("Hello", "");
        Element childEl = new Element("span");
        DataNode dataNode = new DataNode("data", "");

        parent.appendChild(textNode);
        parent.appendChild(childEl);
        parent.appendChild(dataNode);

        assertEquals(1, parent.children().size());
        assertEquals(childEl, parent.child(0));

        List<TextNode> textNodes = parent.textNodes();
        assertEquals(1, textNodes.size());
        assertEquals("Hello", textNodes.get(0).getWholeText());

        List<DataNode> dataNodes = parent.dataNodes();
        assertEquals(1, dataNodes.size());
        assertEquals("data", dataNodes.get(0).getWholeData());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testChildOutOfBounds() throws Throwable {
        Element parent = new Element("div");
        parent.child(0);
    }

    @Test
    public void testInsertChildrenAndAppendPrepend() throws Throwable {
        Element parent = new Element("div");
        parent.appendChild(new Element("span"));
        parent.prependChild(new Element("p"));

        assertEquals(2, parent.children().size());
        assertEquals("p", parent.child(0).tagName());
        assertEquals("span", parent.child(1).tagName());

        parent.appendElement("a");
        assertEquals("a", parent.child(parent.children().size() - 1).tagName());

        parent.prependElement("b");
        assertEquals("b", parent.child(0).tagName());

        parent.appendText("TextAppend");
        parent.prependText("TextPrepend");

        assertTrue(parent.text().contains("TextAppend"));
        assertTrue(parent.text().contains("TextPrepend"));

        List<Node> listToInsert = new ArrayList<Node>();
        listToInsert.add(new Element("em"));
        parent.insertChildren(0, listToInsert);
        assertEquals("em", parent.child(0).tagName());

        parent.insertChildren(-1, listToInsert);
        
        parent.empty();
        assertEquals(0, parent.childNodeSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertChildrenNull() throws Throwable {
        Element parent = new Element("div");
        parent.insertChildren(0, null);
    }

    @Test
    public void testHtmlParsingAppendPrepend() throws Throwable {
        Element parent = new Element("div");
        parent.append("<span>child</span>");
        assertEquals(1, parent.children().size());
        assertEquals("child", parent.child(0).text());

        parent.prepend("<b>first</b>");
        assertEquals("b", parent.child(0).tagName());
    }

    @Test
    public void testCssSelector() throws Throwable {
        Element parent = new Element("div").attr("id", "parentId");
        Element child = new Element("span").addClass("myClass");
        parent.appendChild(child);

        assertEquals("#parentId", parent.cssSelector());
        assertTrue(child.cssSelector().contains("span"));
    }

    @Test
    public void testSiblingNavigation() throws Throwable {
        Element parent = new Element("div");
        Element child1 = new Element("p");
        Element child2 = new Element("span");
        Element child3 = new Element("a");

        parent.appendChild(child1);
        parent.appendChild(child2);
        parent.appendChild(child3);

        assertEquals(2, child1.siblingElements().size());
        assertEquals(child2, child1.nextElementSibling());
        assertNull(child1.previousElementSibling());

        assertEquals(child2, child3.previousElementSibling());
        assertNull(child3.nextElementSibling());

        assertEquals(child1, child2.firstElementSibling());
        assertEquals(child3, child2.lastElementSibling());

        assertEquals(Integer.valueOf(0), child1.elementSiblingIndex());
        assertEquals(Integer.valueOf(1), child2.elementSiblingIndex());
        assertEquals(Integer.valueOf(2), child3.elementSiblingIndex());

        Element standalone = new Element("div");
        assertNull(standalone.nextElementSibling());
        assertNull(standalone.previousElementSibling());
        assertEquals(0, standalone.siblingElements().size());
        assertEquals(Integer.valueOf(0), standalone.elementSiblingIndex());
        assertNull(standalone.firstElementSibling());
        assertNull(standalone.lastElementSibling());
    }

    @Test
    public void testGetElementsByVarious() throws Throwable {
        Element root = new Element("div").attr("id", "rootId").addClass("mainClass");
        Element child = new Element("a").attr("href", "http://example.com").attr("data-custom", "val").text("Click here");
        Element subChild = new Element("span").addClass("subClass").text("sub text");
        root.appendChild(child);
        child.appendChild(subChild);

        assertNotNull(root.getElementsByTag("a").get(0));
        assertNotNull(root.getElementById("rootId"));
        assertNull(root.getElementById("nonexistent"));
        assertNotNull(root.getElementsByClass("subClass").get(0));
        assertNotNull(root.getElementsByAttribute("href").get(0));
        assertNotNull(root.getElementsByAttributeStarting("data-").get(0));
        assertNotNull(root.getElementsByAttributeValue("href", "http://example.com").get(0));
        assertNotNull(root.getElementsByAttributeValueNot("href", "other").get(0));
        assertNotNull(root.getElementsByAttributeValueStarting("href", "http").get(0));
        assertNotNull(root.getElementsByAttributeValueEnding("href", "com").get(0));
        assertNotNull(root.getElementsByAttributeValueContaining("href", "example").get(0));
        assertNotNull(root.getElementsByAttributeValueMatching("href", Pattern.compile("https?://.*")).get(0));
        assertNotNull(root.getElementsByAttributeValueMatching("href", "https?://.*").get(0));

        assertNotNull(child.getElementsByIndexLessThan(5).get(0));
        assertNotNull(child.getElementsByIndexGreaterThan(-1).get(0));
        assertNotNull(child.getElementsByIndexEquals(0).get(0));

        assertNotNull(root.getElementsContainingText("sub text").get(0));
        assertNotNull(child.getElementsContainingOwnText("Click here").get(0));
        assertNotNull(root.getElementsMatchingText(Pattern.compile("sub.*")).get(0));
        assertNotNull(root.getElementsMatchingText("sub.*").get(0));
        assertNotNull(child.getElementsMatchingOwnText(Pattern.compile("Click.*")).get(0));
        assertNotNull(child.getElementsMatchingOwnText("Click.*").get(0));
        assertFalse(root.getAllElements().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPattern() throws Throwable {
        Element root = new Element("div");
        root.getElementsByAttributeValueMatching("id", "[invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPatternText() throws Throwable {
        Element root = new Element("div");
        root.getElementsMatchingText("[invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPatternOwnText() throws Throwable {
        Element root = new Element("div");
        root.getElementsMatchingOwnText("[invalid");
    }

    @Test
    public void testTextAndDataManipulation() throws Throwable {
        Element root = new Element("p");
        root.appendText("Hello ");
        Element b = new Element("b");
        b.appendText("there");
        root.appendChild(b);
        root.appendText(" now!");

        assertEquals("Hello there now!", root.text());
        assertEquals("Hello  now!", root.ownText());
        assertTrue(root.hasText());

        Element emptyEl = new Element("p");
        assertFalse(emptyEl.hasText());

        root.text("New text content");
        assertEquals("New text content", root.text());

        Element script = new Element("script");
        script.appendChild(new DataNode("console.log(1);", ""));
        script.appendChild(new Comment("comment data", ""));
        Element innerDiv = new Element("div");
        innerDiv.appendChild(new DataNode("innerData", ""));
        script.appendChild(innerDiv);

        String data = script.data();
        assertTrue(data.contains("console.log(1);"));
        assertTrue(data.contains("comment data"));
        assertTrue(data.contains("innerData"));
    }

    @Test
    public void testClassOperations() throws Throwable {
        Element el = new Element("div").attr("class", "foo bar baz");
        assertEquals("foo bar baz", el.className());

        Set<String> classNames = el.classNames();
        assertTrue(classNames.contains("foo"));
        assertTrue(classNames.contains("bar"));

        Set<String> newClasses = new HashSet<String>();
        newClasses.add("one");
        newClasses.add("two");
        el.classNames(newClasses);
        assertTrue(el.hasClass("one"));
        assertFalse(el.hasClass("foo"));

        assertTrue(el.hasClass("ONE")); // case insensitive
        
        Element emptyClassEl = new Element("div");
        assertFalse(emptyClassEl.hasClass("foo"));
        Element shortClassEl = new Element("div").attr("class", "a");
        assertFalse(shortClassEl.hasClass("toolong"));
        Element exactClassEl = new Element("div").attr("class", "test");
        assertTrue(exactClassEl.hasClass("test"));

        el.addClass("three");
        assertTrue(el.hasClass("three"));

        el.removeClass("three");
        assertFalse(el.hasClass("three"));

        el.toggleClass("four");
        assertTrue(el.hasClass("four"));
        el.toggleClass("four");
        assertFalse(el.hasClass("four"));
    }

    @Test
    public void testFormValOperations() throws Throwable {
        Element input = new Element("input").attr("value", "initialValue");
        assertEquals("initialValue", input.val());
        input.val("newValue");
        assertEquals("newValue", input.val());

        Element textarea = new Element("textarea");
        textarea.val("textarea text");
        assertEquals("textarea text", textarea.val());
    }

    @Test
    public void testIsQueryAndEvaluator() throws Throwable {
        Element el = new Element("div").attr("id", "testId");
        Document doc = Document.createShell("");
        doc.body().appendChild(el);

        assertTrue(el.is("#testId"));
        assertTrue(el.is(new Evaluator.Id("testId")));
    }

    @Test
    public void testCloneAndToString() throws Throwable {
        Element el = new Element("div").attr("id", "id1");
        Element clone = el.clone();
        assertEquals(el.toString(), clone.toString());
    }

    @Test
    public void testHtmlGeneration() throws Throwable {
        Element el = new Element("div");
        el.appendChild(new Element("span").text("hello"));
        String html = el.html();
        assertTrue(html.contains("span"));
        
        StringBuilder sb = new StringBuilder();
        el.html(sb);
        assertTrue(sb.toString().contains("hello"));
    }
}