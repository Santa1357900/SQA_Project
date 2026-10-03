package org.jsoup.nodes;

import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
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
        assertNotNull(el.tag());
        assertEquals("", el.baseUri());
        assertFalse(el.isBlock());

        Element el2 = new Element(Tag.valueOf("p"), "http://example.com");
        assertEquals("p", el2.tagName());
        assertEquals("http://example.com", el2.baseUri());
        assertTrue(el2.isBlock());

        Attributes attrs = new Attributes();
        attrs.put("id", "myId");
        Element el3 = new Element(Tag.valueOf("span"), "http://example.com", attrs);
        assertEquals("myId", el3.id());
        assertTrue(el3.hasAttributes());
        assertEquals("http://example.com", el3.baseUri());
    }

    @Test
    public void testTagNameChange() throws Throwable {
        Element el = new Element("span");
        el.tagName("div");
        assertEquals("div", el.tagName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTagNameEmptyThrows() throws Throwable {
        Element el = new Element("div");
        el.tagName("");
    }

    @Test
    public void testAttributesManipulation() throws Throwable {
        Element el = new Element("div");
        el.attr("key1", "val1");
        assertEquals("val1", el.attr("key1"));

        el.attr("booleanKey", true);
        assertEquals("", el.attr("booleanKey"));

        assertNotNull(el.attributes());
        assertNotNull(el.dataset());
    }

    @Test
    public void testBaseUri() throws Throwable {
        Element el = new Element("div");
        el.doSetBaseUri("http://foo.com");
        assertEquals("http://foo.com", el.baseUri());
    }

    @Test
    public void testChildNodesAndChildren() throws Throwable {
        Element parent = new Element("div");
        assertEquals(0, parent.childNodeSize());
        assertEquals(0, parent.children().size());

        Element child1 = new Element("p");
        TextNode textNode = new TextNode("Hello");
        Element child2 = new Element("span");

        parent.appendChild(child1);
        parent.appendChild(textNode);
        parent.appendChild(child2);

        assertEquals(3, parent.childNodeSize());
        assertEquals(2, parent.children().size());
        assertEquals(child1, parent.child(0));
        assertEquals(child2, parent.child(1));

        parent.nodelistChanged();
        assertEquals(2, parent.children().size());
    }

    @Test
    public void testTextNodesAndDataNodes() throws Throwable {
        Element parent = new Element("div");
        TextNode t1 = new TextNode("T1");
        DataNode d1 = new DataNode("D1");
        parent.appendChild(t1);
        parent.appendChild(d1);

        List<TextNode> tNodes = parent.textNodes();
        assertEquals(1, tNodes.size());
        assertEquals("T1", tNodes.get(0).getWholeText());

        List<DataNode> dNodes = parent.dataNodes();
        assertEquals(1, dNodes.size());
        assertEquals("D1", dNodes.get(0).getWholeData());
    }

    @Test
    public void testSelectorsAndQueries() throws Throwable {
        Element parent = new Element("div");
        parent.attr("id", "main");
        Element child = parent.appendElement("a");
        child.attr("href", "http://example.com");
        child.addClass("link-class");

        Elements foundByTag = parent.getElementsByTag("a");
        assertEquals(1, foundByTag.size());

        Element foundById = parent.getElementById("main");
        assertNotNull(foundById);
        assertEquals("div", foundById.tagName());

        Element missingId = parent.getElementById("nonexistent");
        assertNull(missingId);

        Elements foundByClass = parent.getElementsByClass("link-class");
        assertEquals(1, foundByClass.size());

        Elements foundByAttr = parent.getElementsByAttribute("href");
        assertEquals(1, foundByAttr.size());

        Elements foundByAttrStart = parent.getElementsByAttributeStarting("hr");
        assertEquals(1, foundByAttrStart.size());

        Elements foundByAttrVal = parent.getElementsByAttributeValue("href", "http://example.com");
        assertEquals(1, foundByAttrVal.size());

        Elements foundByAttrValNot = parent.getElementsByAttributeValueNot("href", "wrong");
        assertEquals(1, foundByAttrValNot.size());

        Elements foundByAttrValStart = parent.getElementsByAttributeValueStarting("href", "http://");
        assertEquals(1, foundByAttrValStart.size());

        Elements foundByAttrValEnd = parent.getElementsByAttributeValueEnding("href", "com");
        assertEquals(1, foundByAttrValEnd.size());

        Elements foundByAttrValCont = parent.getElementsByAttributeValueContaining("href", "example");
        assertEquals(1, foundByAttrValCont.size());

        Elements foundByAttrValMatch = parent.getElementsByAttributeValueMatching("href", Pattern.compile("https?://.*"));
        assertEquals(1, foundByAttrValMatch.size());

        Elements foundByAttrValMatchStr = parent.getElementsByAttributeValueMatching("href", "http://.*");
        assertEquals(1, foundByAttrValMatchStr.size());

        Elements indexLessThan = parent.getElementsByIndexLessThan(5);
        assertTrue(indexLessThan.size() > 0);

        Elements indexGreaterThan = parent.getElementsByIndexGreaterThan(-1);
        assertTrue(indexGreaterThan.size() > 0);

        Elements indexEquals = parent.getElementsByIndexEquals(0);
        assertTrue(indexEquals.size() > 0);

        child.text("Some search text here");
        Elements containsText = parent.getElementsContainingText("search text");
        assertTrue(containsText.size() > 0);

        Elements containsOwnText = parent.getElementsContainingOwnText("search text");
        assertTrue(containsOwnText.size() > 0);

        Elements matchesText = parent.getElementsMatchingText(Pattern.compile("Some.*"));
        assertTrue(matchesText.size() > 0);

        Elements matchesTextStr = parent.getElementsMatchingText("Some.*");
        assertTrue(matchesTextStr.size() > 0);

        Elements matchesOwnText = parent.getElementsMatchingOwnText(Pattern.compile("Some.*"));
        assertTrue(matchesOwnText.size() > 0);

        Elements matchesOwnTextStr = parent.getElementsMatchingOwnText("Some.*");
        assertTrue(matchesOwnTextStr.size() > 0);

        Elements allEls = parent.getAllElements();
        assertTrue(allEls.size() >= 2);

        assertNotNull(parent.select("a"));
        assertNotNull(parent.selectFirst("a"));
        assertTrue(child.is("a"));
        assertTrue(child.is(new Evaluator.Tag("a")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPatternSyntax() throws Throwable {
        Element parent = new Element("div");
        parent.getElementsByAttributeValueMatching("href", "[invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTextRegexPatternSyntax() throws Throwable {
        Element parent = new Element("div");
        parent.getElementsMatchingText("[invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidOwnTextRegexPatternSyntax() throws Throwable {
        Element parent = new Element("div");
        parent.getElementsMatchingOwnText("[invalid");
    }

    @Test
    public void testAddInsertAppendPrependOperations() throws Throwable {
        Element el = new Element("div");
        el.appendChild(new TextNode("Child1"));
        el.prependChild(new TextNode("Child0"));
        assertEquals("Child0", el.childNode(0).outerHtml());

        el.appendTo(new Element("body"));

        List<Node> collectionNodes = new ArrayList<Node>();
        collectionNodes.add(new TextNode("Col1"));
        el.insertChildren(1, collectionNodes);

        el.insertChildren(0, new Node[]{new TextNode("Arr1")});

        el.appendElement("span");
        el.prependElement("span");

        el.appendText("AppendedText");
        el.prependText("PrependedText");

        el.append("<span>FragmentAppend</span>");
        el.prepend("<span>FragmentPrepend</span>");

        el.empty();
        assertEquals(0, el.childNodeSize());
    }

    @Test
    public void testSiblingsAndParents() throws Throwable {
        Element root = new Element("div");
        Element p1 = root.appendElement("p");
        Element p2 = root.appendElement("p");
        Element p3 = root.appendElement("p");

        assertEquals(root, p2.parent());
        assertEquals(2, p2.parents().size());
        assertEquals(p3, p2.nextElementSibling());
        assertEquals(p1, p2.previousElementSibling());
        assertEquals(p1, p2.firstElementSibling());
        assertEquals(p3, p2.lastElementSibling());
        assertEquals(1, p2.elementSiblingIndex());
        assertEquals(2, p2.siblingElements().size());

        Element orphan = new Element("div");
        assertNull(orphan.nextElementSibling());
        assertNull(orphan.previousElementSibling());
        assertEquals(0, orphan.siblingElements().size());
        assertEquals(0, orphan.elementSiblingIndex());
        assertNull(orphan.firstElementSibling());
        assertNull(orphan.lastElementSibling());
    }

    @Test
    public void testCssSelector() throws Throwable {
        Element root = new Element("div");
        root.attr("id", "myId");
        assertEquals("#myId", root.cssSelector());

        Element child = root.appendElement("span");
        child.addClass("cls");
        assertEquals("span.cls", child.cssSelector());
    }

    @Test
    public void testTextAndOwnTextAndHasText() throws Throwable {
        Element p = new Element("p");
        p.appendText("Hello ");
        Element b = p.appendElement("b");
        b.appendText("there");
        p.appendText(" now!");

        assertEquals("Hello there now!", p.text());
        assertEquals("Hello now!", p.ownText());
        assertTrue(p.hasText());

        Element emptyEl = new Element("div");
        assertFalse(emptyEl.hasText());
        assertEquals("", emptyEl.text());
    }

    @Test
    public void testDataHandling() throws Throwable {
        Element script = new Element("script");
        script.appendChild(new DataNode("var x = 1;"));
        script.appendChild(new Comment("a comment"));
        Element sub = script.appendElement("div");
        sub.appendChild(new DataNode("subdata"));

        String data = script.data();
        assertTrue(data.contains("var x = 1;"));
        assertTrue(data.contains("a comment"));
        assertTrue(data.contains("subdata"));
    }

    @Test
    public void testClassOperations() throws Throwable {
        Element el = new Element("div");
        el.attr("class", "foo bar  baz ");
        assertEquals("foo bar  baz", el.className());

        Set<String> names = el.classNames();
        assertTrue(names.contains("foo"));
        assertTrue(names.contains("bar"));
        assertTrue(names.contains("baz"));

        Set<String> newNames = new HashSet<String>();
        newNames.add("alpha");
        newNames.add("beta");
        el.classNames(newNames);
        assertTrue(el.hasClass("alpha"));

        el.classNames(new HashSet<String>());
        assertFalse(el.hasAttributes() && el.attributes().hasKey("class"));

        el.attr("class", "one two three");
        assertTrue(el.hasClass("two"));
        assertFalse(el.hasClass("four"));
        assertTrue(el.hasClass("one"));
        assertTrue(el.hasClass("three"));

        Element el2 = new Element("div");
        el2.attr("class", "short");
        assertFalse(el2.hasClass("muchlongerclassname"));

        Element el3 = new Element("div");
        el3.attr("class", "exact");
        assertTrue(el3.hasClass("exact"));

        el.addClass("four");
        assertTrue(el.hasClass("four"));

        el.removeClass("four");
        assertFalse(el.hasClass("four"));

        el.toggleClass("toggled");
        assertTrue(el.hasClass("toggled"));
        el.toggleClass("toggled");
        assertFalse(el.hasClass("toggled"));
    }

    @Test
    public void testValOperations() throws Throwable {
        Element input = new Element("input");
        input.val("testValue");
        assertEquals("testValue", input.val());

        Element textarea = new Element("textarea");
        textarea.val("textareaContent");
        assertEquals("textareaContent", textarea.val());
    }

    @Test
    public void testHtmlAndOuterHtmlAndCloning() throws Throwable {
        Element el = new Element("div");
        el.append("<span>content</span>");
        
        assertNotNull(el.html());
        assertNotNull(el.toString());

        Element clone = el.clone();
        assertNotNull(clone);
        assertEquals(el.tagName(), clone.tagName());

        Element shallow = el.shallowClone();
        assertNotNull(shallow);
        assertEquals(0, shallow.childNodeSize());

        Element clonedDo = el.doClone(null);
        assertNotNull(clonedDo);
    }
}