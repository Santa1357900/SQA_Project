package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class HtmlTreeBuilderTest {

    @Test
    public void testInitialisationAndDefaults() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertNotNull(tb.defaultSettings());
        
        tb.initialiseParse(new StringReader("<html></html>"), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        assertNotNull(tb.getDocument());
        assertEquals("http://example.com", tb.getBaseUri());
        assertNull(tb.state());
        assertFalse(tb.isFragmentParsing());
        assertTrue(tb.framesetOk());
        assertFalse(tb.isFosterInserts());
        assertNull(tb.getHeadElement());
        assertNull(tb.getFormElement());
        assertNotNull(tb.getPendingTableCharacters());
    }

    @Test
    public void testStateAndTransitions() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        tb.transition(HtmlTreeBuilderState.Initial);
        assertEquals(HtmlTreeBuilderState.Initial, tb.state());
        
        tb.markInsertionMode();
        assertEquals(HtmlTreeBuilderState.Initial, tb.originalState());
        
        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
    }

    @Test
    public void testBaseUriHandling() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Element base = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://example.com");
        base.attr("href", "http://new-example.com");
        
        tb.maybeSetBaseUri(base);
        assertEquals("http://new-example.com", tb.getBaseUri());
        
        // Second base should be ignored due to baseUriSetFromDoc
        Element base2 = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://new-example.com");
        base2.attr("href", "http://another.com");
        tb.maybeSetBaseUri(base2);
        assertEquals("http://new-example.com", tb.getBaseUri());
    }

    @Test
    public void testStackOperations() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com");
        Element span = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "http://example.com");
        
        tb.push(div);
        tb.push(span);
        
        assertTrue(tb.onStack(div));
        assertTrue(tb.onStack(span));
        assertEquals(span, tb.currentElement());
        assertEquals(div, tb.aboveOnStack(span));
        
        assertEquals(span, tb.getFromStack("span"));
        assertEquals(div, tb.getFromStack("div"));
        assertNull(tb.getFromStack("nonexistent"));
        
        Element popped = tb.pop();
        assertEquals(span, popped);
        assertFalse(tb.onStack(span));
        
        assertTrue(tb.removeFromStack(div));
        assertFalse(tb.removeFromStack(div));
    }

    @Test
    public void testStackPopClosingAndContexts() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Element root = new Element(Tag.valueOf("html", ParseSettings.htmlDefault), "http://example.com");
        Element body = new Element(Tag.valueOf("body", ParseSettings.htmlDefault), "http://example.com");
        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com");
        Element p = new Element(Tag.valueOf("p", ParseSettings.htmlDefault), "http://example.com");
        
        tb.push(root);
        tb.push(body);
        tb.push(div);
        tb.push(p);
        
        tb.popStackToClose("div");
        assertFalse(tb.onStack(div));
        assertFalse(tb.onStack(p));
        assertTrue(tb.onStack(body));
        
        tb.push(div);
        tb.push(p);
        tb.popStackToBefore("body");
        assertTrue(tb.onStack(root));
        assertTrue(tb.onStack(body));
        assertFalse(tb.onStack(div));
    }

    @Test
    public void testTableContextClearing() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Element table = new Element(Tag.valueOf("table", ParseSettings.htmlDefault), "http://example.com");
        Element tbody = new Element(Tag.valueOf("tbody", ParseSettings.htmlDefault), "http://example.com");
        Element tr = new Element(Tag.valueOf("tr", ParseSettings.htmlDefault), "http://example.com");
        Element span = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "http://example.com");
        
        tb.push(table);
        tb.push(tbody);
        tb.push(tr);
        tb.push(span);
        
        tb.clearStackToTableRowContext();
        assertTrue(tb.onStack(tr));
        assertFalse(tb.onStack(span));
        
        tb.push(span);
        tb.clearStackToTableBodyContext();
        assertTrue(tb.onStack(tbody));
        assertFalse(tb.onStack(span));
        
        tb.push(span);
        tb.clearStackToTableContext();
        assertTrue(tb.onStack(table));
        assertFalse(tb.onStack(span));
    }

    @Test
    public void testInsertions() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Element startEl = tb.insertStartTag("p");
        assertEquals("p", startEl.tagName());
        assertEquals(startEl, tb.currentElement());
        
        Token.Comment commentToken = new Token.Comment();
        commentToken.data("test comment");
        tb.insert(commentToken);
        
        Token.Character charToken = new Token.Character();
        charToken.data("hello text");
        tb.insert(charToken);
        
        assertTrue(tb.currentElement().childNodeSize() > 0);
    }

    @Test
    public void testInsertScriptStyleCharacters() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        tb.insertStartTag("script");
        Token.Character charToken = new Token.Character();
        charToken.data("var a = 1;");
        tb.insert(charToken);
        
        Node node = tb.currentElement().childNode(0);
        assertTrue(node instanceof DataNode);
        
        tb.pop();
        tb.insertStartTag("style");
        tb.insert(charToken);
        Node styleNode = tb.currentElement().childNode(0);
        assertTrue(styleNode instanceof DataNode);
    }

    @Test
    public void testInsertForm() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Token.StartTag startTag = new Token.StartTag();
        startTag.name("form");
        
        FormElement form = tb.insertForm(startTag, true);
        assertNotNull(form);
        assertEquals(form, tb.getFormElement());
        assertTrue(tb.onStack(form));
    }

    @Test
    public void testFormattingElements() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        assertNull(tb.lastFormattingElement());
        assertNull(tb.removeLastFormattingElement());
        
        Element b1 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "http://example.com");
        Element b2 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "http://example.com");
        Element b3 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "http://example.com");
        Element b4 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "http://example.com");
        
        tb.pushActiveFormattingElements(b1);
        tb.pushActiveFormattingElements(b2);
        tb.pushActiveFormattingElements(b3);
        tb.pushActiveFormattingElements(b4); // Should trigger limit/removal of oldest duplicate if 3 seen
        
        assertTrue(tb.isInActiveFormattingElements(b4));
        assertEquals(b4, tb.getActiveFormattingElement("b"));
        
        tb.insertMarkerToFormattingElements();
        assertNull(tb.getActiveFormattingElement("b"));
        
        tb.clearFormattingElementsToLastMarker();
        assertNull(tb.lastFormattingElement());
        
        tb.pushActiveFormattingElements(b1);
        tb.removeFromActiveFormattingElements(b1);
        assertFalse(tb.isInActiveFormattingElements(b1));
    }

    @Test
    public void testScopes() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com");
        Element option = new Element(Tag.valueOf("option", ParseSettings.htmlDefault), "http://example.com");
        Element button = new Element(Tag.valueOf("button", ParseSettings.htmlDefault), "http://example.com");
        Element select = new Element(Tag.valueOf("select", ParseSettings.htmlDefault), "http://example.com");
        
        tb.push(div);
        tb.push(option);
        
        assertTrue(tb.inScope("div"));
        assertTrue(tb.inListItemScope("option"));
        
        tb.pop(); // pop option
        tb.push(button);
        assertTrue(tb.inButtonScope("button"));
        
        tb.push(select);
        assertTrue(tb.inSelectScope("select"));
        assertTrue(tb.inTableScope("div"));
    }

    @Test
    public void testResetInsertionMode() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        
        Element select = new Element(Tag.valueOf("select", ParseSettings.htmlDefault), "http://example.com");
        tb.push(select);
        tb.resetInsertionMode();
        assertEquals(HtmlTreeBuilderState.InSelect, tb.state());
        
        tb.getStack().clear();
        Element tr = new Element(Tag.valueOf("tr", ParseSettings.htmlDefault), "http://example.com");
        tb.push(tr);
        tb.resetInsertionMode();
        assertEquals(HtmlTreeBuilderState.InRow, tb.state());

        tb.getStack().clear();
        Element table = new Element(Tag.valueOf("table", ParseSettings.htmlDefault), "http://example.com");
        tb.push(table);
        tb.resetInsertionMode();
        assertEquals(HtmlTreeBuilderState.InTable, tb.state());
        
        tb.getStack().clear();
        Element body = new Element(Tag.valueOf("body", ParseSettings.htmlDefault), "http://example.com");
        tb.push(body);
        tb.resetInsertionMode();
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());

        tb.getStack().clear();
        Element html = new Element(Tag.valueOf("html", ParseSettings.htmlDefault), "http://example.com");
        tb.push(html);
        tb.resetInsertionMode();
        assertEquals(HtmlTreeBuilderState.BeforeHead, tb.state());
    }

    @Test
    public void testParseFragment() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com");
        List<Node> nodes = tb.parseFragment("<span>hello</span>", context, "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        assertNotNull(nodes);
        assertTrue(tb.isFragmentParsing());
    }

    @Test
    public void testToString() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 0), ParseSettings.htmlDefault);
        String str = tb.toString();
        assertTrue(str.contains("TreeBuilder"));
    }
}