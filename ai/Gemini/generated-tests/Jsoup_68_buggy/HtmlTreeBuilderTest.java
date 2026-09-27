package org.jsoup.parser;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.helper.DataUtil;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class HtmlTreeBuilderTest {

    @Test
    public void testInitialisationAndDefaults() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertNotNull(tb.defaultSettings());
        
        tb.initialiseParse(new StringReader("<html></html>"), "http://example.com", new ParseErrorList(), ParseSettings.htmlDefault);
        assertNotNull(tb.getDocument());
        assertEquals("http://example.com", tb.getBaseUri());
        assertFalse(tb.isFragmentParsing());
        assertTrue(tb.framesetOk());
        assertNull(tb.getHeadElement());
        assertNull(tb.getFormElement());
        assertNotNull(tb.getStack());
    }

    @Test
    public void testStateTransitionsAndMarking() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);
        
        assertNull(tb.originalState());
        tb.markInsertionMode();
        assertEquals(HtmlTreeBuilderState.Initial, tb.originalState());

        tb.transition(HtmlTreeBuilderState.InBody);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());

        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
    }

    @Test
    public void testBaseUriHandling() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://base.com", new ParseErrorList(), ParseSettings.htmlDefault);

        Element baseEl = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://base.com");
        baseEl.attr("href", "http://newbase.com");

        tb.maybeSetBaseUri(baseEl);
        assertEquals("http://newbase.com", tb.getBaseUri());

        // Second base should be ignored
        Element baseEl2 = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://base.com");
        baseEl2.attr("href", "http://ignoredbase.com");
        tb.maybeSetBaseUri(baseEl2);
        assertEquals("http://newbase.com", tb.getBaseUri());
    }

    @Test
    public void testInsertionsAndStackOperations() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("div");
        Element el = tb.insert(startTag);
        assertNotNull(el);
        assertEquals("div", el.tagName());
        assertTrue(tb.onStack(el));

        Element popped = tb.pop();
        assertEquals(el, popped);
        assertFalse(tb.onStack(el));

        tb.push(el);
        assertTrue(tb.onStack(el));
        assertEquals(el, tb.getFromStack("div"));
        assertNull(tb.getFromStack("span"));

        assertTrue(tb.removeFromStack(el));
        assertFalse(tb.removeFromStack(el));
    }

    @Test
    public void testInsertStartTagAndEmpty() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Element el1 = tb.insertStartTag("span");
        assertEquals("span", el1.tagName());
        assertTrue(tb.onStack(el1));

        Token.StartTag selfClosing = new Token.StartTag();
        selfClosing.name("br");
        selfClosing.selfClose();
        Element el2 = tb.insert(selfClosing);
        assertEquals("br", el2.tagName());

        Token.StartTag emptyTag = new Token.StartTag();
        emptyTag.name("img");
        Element el3 = tb.insertEmpty(emptyTag);
        assertEquals("img", el3.tagName());
    }

    @Test
    public void testInsertForm() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Token.StartTag formTag = new Token.StartTag();
        formTag.name("form");
        FormElement form = tb.insertForm(formTag, true);
        assertNotNull(form);
        assertEquals(form, tb.getFormElement());
        assertTrue(tb.onStack(form));
    }

    @Test
    public void testInsertCommentsAndCharacters() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Token.Comment commentToken = new Token.Comment();
        commentToken.data("test comment");
        tb.insert(commentToken);
        
        Element div = tb.insertStartTag("div");
        Token.Character charToken = new Token.Character();
        charToken.data("hello text");
        tb.insert(charToken);
        assertEquals(1, div.childNodeSize());
        assertTrue(div.childNode(0) instanceof TextNode);

        Element script = tb.insertStartTag("script");
        Token.Character scriptChar = new Token.Character();
        scriptChar.data("var a = 1;");
        tb.insert(scriptChar);
        assertTrue(script.childNode(0) instanceof org.jsoup.nodes.DataNode);
    }

    @Test
    public void testStackClosingOperations() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Element d1 = tb.insertStartTag("div");
        Element p1 = tb.insertStartTag("p");
        Element span1 = tb.insertStartTag("span");

        tb.popStackToClose("p");
        assertFalse(tb.onStack(p1));
        assertTrue(tb.onStack(d1));
        assertFalse(tb.onStack(span1));

        tb.insertStartTag("ul");
        tb.insertStartTag("li");
        tb.popStackToClose("ul", "ol");

        Element d2 = tb.insertStartTag("div");
        Element span2 = tb.insertStartTag("span");
        Element b2 = tb.insertStartTag("b");
        tb.popStackToBefore("div");
        assertTrue(tb.onStack(d2));
        assertFalse(tb.onStack(span2));
        assertFalse(tb.onStack(b2));
    }

    @Test
    public void testClearStackContexts() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        tb.insertStartTag("html");
        tb.insertStartTag("table");
        tb.insertStartTag("tr");
        tb.insertStartTag("td");

        tb.clearStackToTableContext();
        assertNull(tb.getFromStack("tr"));
        assertNotNull(tb.getFromStack("table"));

        tb.insertStartTag("tbody");
        tb.insertStartTag("tr");
        tb.clearStackToTableBodyContext();
        assertNotNull(tb.getFromStack("table"));
        assertNull(tb.getFromStack("tr"));
    }

    @Test
    public void testAboveOnStackAndReplace() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Element d1 = tb.insertStartTag("div");
        Element p1 = tb.insertStartTag("p");

        assertEquals(d1, tb.aboveOnStack(p1));

        Element p2 = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "");
        tb.replaceOnStack(p1, p2);
        assertFalse(tb.onStack(p1));
        assertTrue(tb.onStack(p2));

        Element beforeEl = tb.insertStartTag("a");
        Element newEl = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        tb.insertOnStackAfter(beforeEl, newEl);
        assertEquals(newEl, tb.aboveOnStack(p2));
    }

    @Test
    public void testScopes() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        tb.insertStartTag("html");
        tb.insertStartTag("div");
        tb.insertStartTag("p");

        assertTrue(tb.inScope("p"));
        assertTrue(tb.inScope(new String[]{"p", "div"}));
        assertTrue(tb.inButtonScope("p"));
        assertTrue(tb.inListItemScope("p"));
        assertFalse(tb.inTableScope("p"));
        assertTrue(tb.inTableScope("html"));
    }

    @Test
    public void testSelectScope() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        tb.insertStartTag("select");
        tb.insertStartTag("option");
        assertTrue(tb.inSelectScope("option"));
        assertTrue(tb.inSelectScope("select"));
    }

    @Test
    public void testImpliedEndTags() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        tb.insertStartTag("div");
        Element p = tb.insertStartTag("p");
        tb.generateImpliedEndTags();
        assertFalse(tb.onStack(p));

        Element p2 = tb.insertStartTag("p");
        tb.generateImpliedEndTags("p");
        assertTrue(tb.onStack(p2));
    }

    @Test
    public void testSpecialElements() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        Element span = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "");

        assertTrue(tb.isSpecial(div));
        assertFalse(tb.isSpecial(span));
    }

    @Test
    public void testFormattingElements() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        assertNull(tb.lastFormattingElement());
        assertNull(tb.removeLastFormattingElement());

        Element b1 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        tb.pushActiveFormattingElements(b1);
        assertEquals(b1, tb.lastFormattingElement());
        assertTrue(tb.isInActiveFormattingElements(b1));
        assertEquals(b1, tb.getActiveFormattingElement("b"));

        tb.insertMarkerToFormattingElements();
        assertNull(tb.getActiveFormattingElement("b"));

        tb.clearFormattingElementsToLastMarker();
        assertNull(tb.lastFormattingElement());

        tb.pushActiveFormattingElements(b1);
        tb.removeFromActiveFormattingElements(b1);
        assertNull(tb.lastFormattingElement());

        Element b2 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        tb.pushActiveFormattingElements(b1);
        tb.replaceActiveFormattingElement(b1, b2);
        assertEquals(b2, tb.lastFormattingElement());
    }

    @Test
    public void testReconstructFormattingElements() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        // Should return early if stack contains last formatting element or empty
        tb.reconstructFormattingElements();

        Element b = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        tb.pushActiveFormattingElements(b);
        tb.reconstructFormattingElements();
        assertTrue(tb.onStack(b));
    }

    @Test
    public void testFosterParenting() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);

        Element html = tb.insertStartTag("html");
        Element table = tb.insertStartTag("table");
        Element tr = tb.insertStartTag("tr");
        
        tb.setFosterInserts(true);
        assertTrue(tb.isFosterInserts());

        TextNode node = new TextNode("fostered", "");
        tb.insert(node);
        assertEquals(html, node.parent());
    }

    @Test
    public void testParseFragment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        List<Node> nodes = tb.parseFragment("<span>hello</span>", context, "http://example.com", new ParseErrorList(), ParseSettings.htmlDefault);
        assertNotNull(nodes);
        assertFalse(nodes.isEmpty());

        List<Node> docNodes = tb.parseFragment("<span>hello</span>", null, "http://example.com", new ParseErrorList(), ParseSettings.htmlDefault);
        assertNotNull(docNodes);
    }

    @Test
    public void testToString() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(), ParseSettings.htmlDefault);
        String str = tb.toString();
        assertNotNull(str);
        assertTrue(str.contains("TreeBuilder"));
    }
}