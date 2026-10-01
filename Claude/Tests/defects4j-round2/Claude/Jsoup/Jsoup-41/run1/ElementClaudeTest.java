package org.jsoup.nodes;

import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;
import static org.junit.Assert.*;

public class ElementClaudeTest {

    // tagName() getter returns tag name from parsed html
    @Test
    public void testTagName_getter_returnsParsedTag() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"a\"></div>");
        Element div = doc.getElementById("a");
        assertEquals("div", div.tagName());
    }

    // tagName(String) setter changes the tag and returns this for chaining
    @Test
    public void testTagName_setter_changesTagAndReturnsThis() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element returned = el.tagName("span");
        assertSame(el, returned);
        assertEquals("span", el.tagName());
    }

    // tagName(String) with empty string must throw IllegalArgumentException (Validate.notEmpty)
    @Test
    public void testTagName_setter_emptyThrowsIllegalArgumentException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.tagName("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // tag() returns the underlying Tag whose name matches tagName()
    @Test
    public void testTag_returnsTagObjectMatchingName() throws Throwable {
        Element el = new Element(Tag.valueOf("p"), "");
        assertEquals("p", el.tag().getName());
    }

    // isBlock() true for block-level element div
    @Test
    public void testIsBlock_trueForDiv() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertTrue(el.isBlock());
    }

    // isBlock() false for inline element span
    @Test
    public void testIsBlock_falseForSpan() throws Throwable {
        Element el = new Element(Tag.valueOf("span"), "");
        assertFalse(el.isBlock());
    }

    // id() returns attribute value when present, empty string when absent
    @Test
    public void testId_presentAndAbsent() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"header\"></div><div></div>");
        Elements divs = doc.getElementsByTag("div");
        assertEquals("header", divs.get(0).id());
        assertEquals("", divs.get(1).id());
    }

    // attr(key,value) sets attribute and returns this element for chaining
    @Test
    public void testAttr_setterReturnsThisAndPersists() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Element returned = el.attr("data-x", "5");
        assertSame(el, returned);
        assertEquals("5", el.attr("data-x"));
    }

    // dataset() reflects data- prefixed attributes with prefix stripped
    @Test
    public void testDataset_reflectsDataAttributes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("data-name", "value1");
        Map<String, String> dataset = el.dataset();
        assertEquals("value1", dataset.get("name"));
    }

    // parent() returns null for a standalone element with no parent
    @Test
    public void testParent_standaloneReturnsNull() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertNull(el.parent());
    }

    // parents() returns ancestor chain excluding #root
    @Test
    public void testParents_excludesRootReturnsAncestorChain() throws Throwable {
        Document doc = Jsoup.parse("<html><body><div><p>Text</p></div></body></html>");
        Element p = doc.getElementsByTag("p").get(0);
        Elements parents = p.parents();
        assertEquals(3, parents.size());
        assertEquals("div", parents.get(0).tagName());
        assertEquals("html", parents.get(2).tagName());
    }

    // child(index) throws IndexOutOfBoundsException when no children exist
    @Test
    public void testChild_outOfBounds_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        try {
            el.child(0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // children() filters out text nodes, keeping only Element children
    @Test
    public void testChildren_filtersOnlyElementNodes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("Hello");
        el.appendElement("span");
        Elements children = el.children();
        assertEquals(1, children.size());
        assertEquals("span", children.get(0).tagName());
    }

    // textNodes() returns only TextNode children, skipping elements
    @Test
    public void testTextNodes_returnsOnlyTextNodes() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("Hello");
        el.appendElement("span");
        List<TextNode> textNodes = el.textNodes();
        assertEquals(1, textNodes.size());
        assertEquals("Hello", textNodes.get(0).getWholeText());
    }

    // dataNodes() returns DataNode content of a script element
    @Test
    public void testDataNodes_returnsScriptDataNode() throws Throwable {
        Document doc = Jsoup.parse("<script>var x = 1;</script>");
        Element script = doc.getElementsByTag("script").get(0);
        List<DataNode> dataNodes = script.dataNodes();
        assertEquals(1, dataNodes.size());
        assertTrue(dataNodes.get(0).getWholeData().contains("x = 1"));
    }

    // select(cssQuery) finds matching descendant elements
    @Test
    public void testSelect_cssQueryFindsMatch() throws Throwable {
        Document doc = Jsoup.parse("<div><p class=\"a\">One</p><p>Two</p></div>");
        Element div = doc.getElementsByTag("div").get(0);
        Elements ps = div.select("p.a");
        assertEquals(1, ps.size());
        assertEquals("One", ps.get(0).text());
    }

    // appendChild adds a node at the end of the child list
    @Test
    public void testAppendChild_addsAtEnd() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("first");
        el.appendChild(new TextNode("second", ""));
        assertEquals(2, el.childNodeSize());
        List<TextNode> tn = el.textNodes();
        assertEquals("second", tn.get(1).getWholeText());
    }

    // prependChild adds a node at the start of the child list
    @Test
    public void testPrependChild_addsAtStart() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("first");
        el.prependChild(new TextNode("zero", ""));
        List<TextNode> tn = el.textNodes();
        assertEquals("zero", tn.get(0).getWholeText());
    }

    // insertChildren(index, children) inserts at the given position
    @Test
    public void testInsertChildren_atSpecificIndex() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("a");
        el.appendText("c");
        List<Node> toInsert = new ArrayList<Node>();
        toInsert.add(new TextNode("b", ""));
        el.insertChildren(1, toInsert);
        List<TextNode> tn = el.textNodes();
        assertEquals(3, tn.size());
        assertEquals("b", tn.get(1).getWholeText());
    }

    // insertChildren(-1, children) rolls around to append at the end
    @Test
    public void testInsertChildren_negativeIndexRollsAroundToEnd() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("a");
        List<Node> toInsert = new ArrayList<Node>();
        toInsert.add(new TextNode("b", ""));
        el.insertChildren(-1, toInsert);
        List<TextNode> tn = el.textNodes();
        assertEquals("b", tn.get(1).getWholeText());
    }

    // insertChildren with out-of-bounds index throws IllegalArgumentException
    @Test
    public void testInsertChildren_outOfBounds_throwsException() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        List<Node> toInsert = new ArrayList<Node>();
        toInsert.add(new TextNode("b", ""));
        try {
            el.insertChildren(5, toInsert);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("bounds"));
        }
    }

    // appendElement creates a new child element with the correct base uri
    @Test
    public void testAppendElement_createsAndAddsChild() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com/");
        Element child = el.appendElement("span");
        assertEquals("span", child.tagName());
        assertEquals(1, el.children().size());
        assertEquals("http://example.com/", child.baseUri());
    }

    // appendText creates and appends a TextNode reflected in text()
    @Test
    public void testAppendText_createsTextNodeChild() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("Hello");
        assertEquals("Hello", el.text());
    }

    // append(html) parses fragment and adds it after existing children
    @Test
    public void testAppend_parsesAndAddsHtmlAtEnd() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com/");
        el.append("<p>Hi</p>");
        assertEquals(1, el.children().size());
        assertEquals("p", el.child(0).tagName());
    }

    // prepend(html) parses fragment and adds it before existing children
    @Test
    public void testPrepend_parsesAndAddsHtmlAtStart() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com/");
        el.appendElement("span");
        el.prepend("<p>Hi</p>");
        assertEquals("p", el.child(0).tagName());
        assertEquals("span", el.child(1).tagName());
    }

    // before(html) inserts a preceding sibling in the parent's child list
    @Test
    public void testBefore_insertsSiblingBeforeElement() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"target\">Mid</p></div>");
        Element p = doc.getElementById("target");
        p.before("<span>Before</span>");
        Element parentDiv = p.parent();
        assertEquals("span", parentDiv.child(0).tagName());
    }

    // after(html) inserts a following sibling in the parent's child list
    @Test
    public void testAfter_insertsSiblingAfterElement() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"target\">Mid</p></div>");
        Element p = doc.getElementById("target");
        p.after("<span>After</span>");
        Element parentDiv = p.parent();
        assertEquals(2, parentDiv.children().size());
        assertEquals("span", parentDiv.children().get(1).tagName());
    }

    // empty() clears all child nodes, leaving attributes intact
    @Test
    public void testEmpty_removesAllChildren() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("hi");
        el.appendElement("span");
        el.empty();
        assertEquals(0, el.childNodeSize());
    }

    // cssSelector() returns #id when element has an id attribute
    @Test
    public void testCssSelector_usesIdWhenPresent() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"main\"></div>");
        Element div = doc.getElementById("main");
        assertEquals("#main", div.cssSelector());
    }

    // cssSelector() falls back to tag.class path when no id present
    @Test
    public void testCssSelector_usesTagAndClassWhenNoId() throws Throwable {
        Document doc = Jsoup.parse("<html><body><div class=\"a\"></div></body></html>");
        Element div = doc.getElementsByTag("div").get(0);
        String sel = div.cssSelector();
        assertTrue(sel.contains("div.a"));
    }

    // siblingElements() returns all element siblings excluding itself
    @Test
    public void testSiblingElements_excludesSelf() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\"></p><p id=\"b\"></p><p id=\"c\"></p></div>");
        Element b = doc.getElementById("b");
        Elements siblings = b.siblingElements();
        assertEquals(2, siblings.size());
        assertFalse(siblings.get(0).id().equals("b"));
        assertFalse(siblings.get(1).id().equals("b"));
    }

    // nextElementSibling() returns null for last element, sibling otherwise
    @Test
    public void testNextElementSibling_nullWhenLastOtherwiseNext() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\"></p><p id=\"b\"></p></div>");
        Element a = doc.getElementById("a");
        Element b = doc.getElementById("b");
        assertEquals("b", a.nextElementSibling().id());
        assertNull(b.nextElementSibling());
    }

    // previousElementSibling() returns null for first element, sibling otherwise
    @Test
    public void testPreviousElementSibling_nullWhenFirstOtherwisePrevious() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\"></p><p id=\"b\"></p></div>");
        Element a = doc.getElementById("a");
        Element b = doc.getElementById("b");
        assertNull(a.previousElementSibling());
        assertEquals("a", b.previousElementSibling().id());
    }

    // firstElementSibling() on an only-child must return itself, per javadoc contract
    @Test
    public void testFirstElementSibling_onlyChild_returnsSelf() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"solo\"></p></div>");
        Element solo = doc.getElementById("solo");
        Element first = solo.firstElementSibling();
        assertNotNull(first);
        assertEquals("solo", first.id());
    }

    // firstElementSibling() with multiple siblings returns the actual first
    @Test
    public void testFirstElementSibling_multipleChildren_returnsFirstChild() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\"></p><p id=\"b\"></p></div>");
        Element b = doc.getElementById("b");
        Element first = b.firstElementSibling();
        assertEquals("a", first.id());
    }

    // lastElementSibling() on an only-child must return itself, per javadoc contract
    @Test
    public void testLastElementSibling_onlyChild_returnsSelf() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"solo\"></p></div>");
        Element solo = doc.getElementById("solo");
        Element last = solo.lastElementSibling();
        assertNotNull(last);
        assertEquals("solo", last.id());
    }

    // lastElementSibling() with multiple siblings returns the actual last
    @Test
    public void testLastElementSibling_multipleChildren_returnsLastChild() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\"></p><p id=\"b\"></p></div>");
        Element a = doc.getElementById("a");
        Element last = a.lastElementSibling();
        assertEquals("b", last.id());
    }

    // elementSiblingIndex() returns 0-based position among element siblings
    @Test
    public void testElementSiblingIndex_returnsPosition() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\"></p><p id=\"b\"></p><p id=\"c\"></p></div>");
        Element c = doc.getElementById("c");
        assertEquals(Integer.valueOf(2), c.elementSiblingIndex());
    }

    // getElementsByTag finds all matching descendant elements case-insensitively
    @Test
    public void testGetElementsByTag_findsMatches() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><span>3</span></div>");
        Elements ps = doc.getElementsByTag("P");
        assertEquals(2, ps.size());
    }

    // getElementById returns null when the id does not exist in the tree
    @Test
    public void testGetElementById_returnsNullWhenNotFound() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"x\"></div>");
        assertNull(doc.getElementById("nonexistent"));
    }

    // getElementsByClass finds elements having the given class name
    @Test
    public void testGetElementsByClass_findsMatchingElements() throws Throwable {
        Document doc = Jsoup.parse("<div class=\"header\"></div><div class=\"footer\"></div>");
        Elements headers = doc.getElementsByClass("header");
        assertEquals(1, headers.size());
    }

    // getElementsByAttribute finds elements that have the given attribute set
    @Test
    public void testGetElementsByAttribute_findsElementsWithAttribute() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"x\"></a><a></a>");
        Elements withHref = doc.getElementsByAttribute("href");
        assertEquals(1, withHref.size());
    }

    // getElementsByAttributeValueMatching(String) with invalid regex throws IllegalArgumentException
    @Test
    public void testGetElementsByAttributeValueMatching_invalidRegex_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<div class=\"a\"></div>");
        Element div = doc.getElementsByTag("div").get(0);
        try {
            div.getElementsByAttributeValueMatching("class", "[");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern"));
        }
    }

    // getElementsByAttributeValueMatching(Pattern) overload finds attribute values matching pattern
    @Test
    public void testGetElementsByAttributeValueMatching_patternOverload_findsMatch() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"http://example.com/1\"></a><a href=\"http://example.com/abc\"></a>");
        Pattern pattern = Pattern.compile("\\d+");
        Elements matches = doc.getElementsByAttributeValueMatching("href", pattern);
        assertEquals(1, matches.size());
    }

    // getElementsByIndexEquals finds the element at the given sibling index
    @Test
    public void testGetElementsByIndexEquals_findsCorrectElement() throws Throwable {
        Document doc = Jsoup.parse("<div><p>1</p><p>2</p><p>3</p></div>");
        Element div = doc.getElementsByTag("div").get(0);
        Elements atIndex1 = div.getElementsByIndexEquals(1);
        assertEquals(1, atIndex1.size());
        assertEquals("2", atIndex1.get(0).text());
    }

    // getElementsMatchingText(String) with invalid regex throws IllegalArgumentException
    @Test
    public void testGetElementsMatchingText_invalidRegex_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<p>abc123</p>");
        Element p = doc.getElementsByTag("p").get(0);
        try {
            p.getElementsMatchingText("[");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Pattern"));
        }
    }

    // getAllElements includes the element itself and all descendants
    @Test
    public void testGetAllElements_includesSelfAndDescendants() throws Throwable {
        Document doc = Jsoup.parse("<div><p><span></span></p></div>");
        Element div = doc.getElementsByTag("div").get(0);
        Elements all = div.getAllElements();
        assertEquals(3, all.size());
    }

    // text() combines normalized text of this element and all descendants
    @Test
    public void testText_combinesDescendantText() throws Throwable {
        Document doc = Jsoup.parse("<p>One <b>Two</b> Three</p>");
        Element p = doc.getElementsByTag("p").get(0);
        assertEquals("One Two Three", p.text());
    }

    // ownText() only includes text directly owned by this element, not descendants
    @Test
    public void testOwnText_excludesDescendantText() throws Throwable {
        Document doc = Jsoup.parse("<p>One <b>Two</b> Three</p>");
        Element p = doc.getElementsByTag("p").get(0);
        assertEquals("One Three", p.ownText());
    }

    // hasText() is false for empty/whitespace-only content, true once real text is added
    @Test
    public void testHasText_falseWhenBlankTrueWhenNonBlank() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        assertFalse(el.hasText());
        el.appendText("   ");
        assertFalse(el.hasText());
        el.appendText("hi");
        assertTrue(el.hasText());
    }

    // data() concatenates DataNode content across nested elements
    @Test
    public void testData_concatenatesScriptContent() throws Throwable {
        Document doc = Jsoup.parse("<div><script>var a=1;</script></div>");
        Element div = doc.getElementsByTag("div").get(0);
        assertTrue(div.data().contains("var a=1;"));
    }

    // className() returns the literal class attribute, trimmed
    @Test
    public void testClassName_returnsLiteralAttribute() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", " header gray ");
        assertEquals("header gray", el.className());
    }

    // classNames() splits the class attribute into a set of distinct names
    @Test
    public void testClassNames_returnsSetOfClasses() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "a b c");
        Set<String> classes = el.classNames();
        assertEquals(3, classes.size());
        assertTrue(classes.contains("b"));
    }

    // classNames(Set) persists the given class names back to the class attribute
    @Test
    public void testClassNamesSetter_persistsToAttribute() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        Set<String> classes = new java.util.LinkedHashSet<String>();
        classes.add("x");
        classes.add("y");
        el.classNames(classes);
        assertTrue(el.hasClass("x"));
        assertTrue(el.hasClass("y"));
    }

    // hasClass() matches case-insensitively
    @Test
    public void testHasClass_caseInsensitiveMatch() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "Header");
        assertTrue(el.hasClass("header"));
        assertFalse(el.hasClass("footer"));
    }

    // addClass() adds a new class name to the element's class attribute
    @Test
    public void testAddClass_addsNewClassName() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.addClass("newclass");
        assertTrue(el.hasClass("newclass"));
    }

    // removeClass() removes an existing class while keeping others
    @Test
    public void testRemoveClass_removesOnlyTargetClass() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("class", "a b");
        el.removeClass("a");
        assertFalse(el.hasClass("a"));
        assertTrue(el.hasClass("b"));
    }

    // toggleClass() adds an absent class and removes a present class
    @Test
    public void testToggleClass_addsThenRemoves() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.toggleClass("x");
        assertTrue(el.hasClass("x"));
        el.toggleClass("x");
        assertFalse(el.hasClass("x"));
    }

    // val() returns text() for textarea, attribute value for other form elements
    @Test
    public void testVal_textareaUsesTextOthersUseValueAttribute() throws Throwable {
        Document doc = Jsoup.parse("<textarea>content</textarea><input value=\"abc\">");
        Element ta = doc.getElementsByTag("textarea").get(0);
        Element input = doc.getElementsByTag("input").get(0);
        assertEquals("content", ta.val());
        assertEquals("abc", input.val());
    }

    // val(value) setter updates text() for textarea, attribute for other elements
    @Test
    public void testValSetter_updatesUnderlyingStorage() throws Throwable {
        Element input = new Element(Tag.valueOf("input"), "");
        input.val("hello");
        assertEquals("hello", input.val());
    }

    // html() getter returns inner HTML of children only
    @Test
    public void testHtml_getterReturnsInnerHtml() throws Throwable {
        Document doc = Jsoup.parse("<div><p>Hi</p></div>");
        Element div = doc.getElementsByTag("div").get(0);
        assertTrue(div.html().contains("<p>Hi</p>"));
    }

    // html(String) setter clears existing content and parses new HTML into children
    @Test
    public void testHtmlSetter_replacesExistingContent() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "http://example.com/");
        el.appendText("old");
        el.html("<span>new</span>");
        assertEquals(1, el.children().size());
        assertEquals("span", el.child(0).tagName());
    }

    // toString() delegates to outerHtml(), containing the tag markup
    @Test
    public void testToString_containsTagMarkup() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.attr("id", "x");
        assertTrue(el.toString().contains("id=\"x\""));
    }

    // equals() is true for same reference, false for a distinct instance
    @Test
    public void testEquals_sameReferenceTrueDifferentInstanceFalse() throws Throwable {
        Element el1 = new Element(Tag.valueOf("div"), "");
        Element el2 = new Element(Tag.valueOf("div"), "");
        assertTrue(el1.equals(el1));
        assertFalse(el1.equals(el2));
    }

    // hashCode() is consistent across repeated calls on the same object
    @Test
    public void testHashCode_consistentAcrossCalls() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        int h1 = el.hashCode();
        int h2 = el.hashCode();
        assertEquals(h1, h2);
    }

    // clone() produces a distinct instance carrying the same tag name
    @Test
    public void testClone_createsDistinctInstanceSameTag() throws Throwable {
        Element el = new Element(Tag.valueOf("div"), "");
        el.appendText("hi");
        Element cloned = el.clone();
        assertNotSame(el, cloned);
        assertEquals(el.tagName(), cloned.tagName());
    }
}
