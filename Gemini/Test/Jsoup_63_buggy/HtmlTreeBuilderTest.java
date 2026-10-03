package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.DataNode;
import org.jsoup.select.Elements;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class HtmlTreeBuilderTest {

    @Test
    public void testInitialisationAndDefaults() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertNotNull(tb.defaultSettings());
        
        tb.initialiseParse(new StringReader(""), "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);
        assertEquals("http://example.com", tb.getBaseUri());
        assertNotNull(tb.getDocument());
        assertFalse(tb.isFragmentParsing());
        assertTrue(tb.framesetOk());
        assertNull(tb.getHeadElement());
        assertNull(tb.getFormElement());
        assertNotNull(tb.getPendingTableCharacters());
    }

    @Test
    public void testParseFragmentNullContext() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        List<Node> nodes = tb.parseFragment("<div>Hello</div>", null, "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);
        assertNotNull(nodes);
        assertFalse(nodes.isEmpty());
    }

    @Test
    public void testParseFragmentWithContext() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = Document.createShell("http://example.com");
        Element context = doc.body();
        List<Node> nodes = tb.parseFragment("<span>SpanContent</span>", context, "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);
        assertNotNull(nodes);
    }

    @Test
    public void testParseFragmentContextSpecialTags() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = Document.createShell("http://example.com");
        
        Element titleContext = doc.createElement("title");
        tb.parseFragment("TitleText", titleContext, "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element iframeContext = doc.createElement("iframe");
        tb.parseFragment("IframeText", iframeContext, "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element scriptContext = doc.createElement("script");
        tb.parseFragment("ScriptText", scriptContext, "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element noscriptContext = doc.createElement("noscript");
        tb.parseFragment("NoscriptText", noscriptContext, "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element plaintextContext = doc.createElement("plaintext");
        tb.parseFragment("PlaintextText", plaintextContext, "http://example.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);
    }

    @Test
    public void testStateAndTransitions() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);
        
        tb.transition(HtmlTreeBuilderState.InBody);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());

        tb.markInsertionMode();
        assertEquals(HtmlTreeBuilderState.InBody, tb.originalState());

        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
    }

    @Test
    public void testBaseUriHandling() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "http://initial.com", new ParseErrorList(0, 10), ParseSettings.htmlDefault);
        
        Element base = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://initial.com");
        base.attr("href", "http://new.com");
        
        tb.maybeSetBaseUri(base);
        assertEquals("http://new.com", tb.getBaseUri());

        // Second base should be ignored
        Element base2 = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://initial.com");
        base2.attr("href", "http://ignore.com");
        tb.maybeSetBaseUri(base2);
        assertEquals("http://new.com", tb.getBaseUri());
    }

    @Test
    public void testErrorHandling() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(1, 10), ParseSettings.htmlDefault);
        tb.error(HtmlTreeBuilderState.Initial);
        // Should add error without throwing
    }

    @Test
    public void testStackOperations() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element el1 = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        Element el2 = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "");

        tb.push(el1);
        tb.push(el2);
        
        assertTrue(tb.onStack(el1));
        assertTrue(tb.onStack(el2));
        assertEquals(el2, tb.getFromStack("span"));
        assertEquals(el1, tb.aboveOnStack(el2));

        assertTrue(tb.removeFromStack(el2));
        assertFalse(tb.onStack(el2));

        Element popped = tb.pop();
        assertEquals(el1, popped);
    }

    @Test
    public void testStackCloseOperations() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        Element p = new Element(Tag.valueOf("p", ParseSettings.htmlDefault), "");
        Element span = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "");

        tb.push(div);
        tb.push(p);
        tb.push(span);

        tb.popStackToClose("p");
        assertFalse(tb.onStack(span));
        assertFalse(tb.onStack(p));
        assertTrue(tb.onStack(div));

        tb.push(new Element(Tag.valueOf("ul", ParseSettings.htmlDefault), ""));
        tb.push(new Element(Tag.valueOf("li", ParseSettings.htmlDefault), ""));
        tb.popStackToClose("ul", "ol");
    }

    @Test
    public void testStackToBeforeAndClearContexts() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        Element p = new Element(Tag.valueOf("p", ParseSettings.htmlDefault), "");
        Element span = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "");

        tb.push(div);
        tb.push(p);
        tb.push(span);

        tb.popStackToBefore("p");
        assertFalse(tb.onStack(span));
        assertTrue(tb.onStack(p));

        tb.clearStackToTableContext();
        tb.clearStackToTableBodyContext();
        tb.clearStackToTableRowContext();
    }

    @Test
    public void testStackReplacementAndInsertion() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        Element span = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "");
        Element a = new Element(Tag.valueOf("a", ParseSettings.htmlDefault), "");

        tb.push(div);
        tb.insertOnStackAfter(div, span);
        assertEquals(span, tb.aboveOnStack(div));

        tb.replaceOnStack(span, a);
        assertEquals(a, tb.aboveOnStack(div));
    }

    @Test
    public void testResetInsertionModeVariants() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        String[] tags = {"select", "td", "th", "tr", "tbody", "thead", "tfoot", "caption", "colgroup", "table", "head", "body", "frameset", "html"};
        for (String tag : tags) {
            tb.getStack().clear();
            tb.push(new Element(Tag.valueOf(tag, ParseSettings.htmlDefault), ""));
            tb.resetInsertionMode();
        }
        
        tb.getStack().clear();
        tb.push(new Element(Tag.valueOf("unknown", ParseSettings.htmlDefault), ""));
        tb.resetInsertionMode();
    }

    @Test
    public void testScopeMethods() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element html = new Element(Tag.valueOf("html", ParseSettings.htmlDefault), "");
        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        tb.push(html);
        tb.push(div);

        assertTrue(tb.inScope("div"));
        assertTrue(tb.inScope(new String[]{"div"}));
        assertTrue(tb.inScope("div", new String[]{"span"}));
        assertTrue(tb.inListItemScope("div"));
        assertTrue(tb.inButtonScope("div"));
        assertTrue(tb.inTableScope("div"));

        tb.getStack().clear();
        tb.push(new Element(Tag.valueOf("option", ParseSettings.htmlDefault), ""));
        assertTrue(tb.inSelectScope("option"));
    }

    @Test
    public void testHeadAndFormAndFosterGettersSetters() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element head = new Element(Tag.valueOf("head", ParseSettings.htmlDefault), "");
        tb.setHeadElement(head);
        assertEquals(head, tb.getHeadElement());

        FormElement form = new FormElement(Tag.valueOf("form", ParseSettings.htmlDefault), "", null);
        tb.setFormElement(form);
        assertEquals(form, tb.getFormElement());

        tb.setFosterInserts(true);
        assertTrue(tb.isFosterInserts());

        tb.newPendingTableCharacters();
        assertNotNull(tb.getPendingTableCharacters());
        tb.setPendingTableCharacters(new ArrayList<String>());
    }

    @Test
    public void testImpliedEndTagsAndSpecial() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element p = new Element(Tag.valueOf("p", ParseSettings.htmlDefault), "");
        tb.push(p);
        tb.generateImpliedEndTags("div");
        tb.generateImpliedEndTags();

        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        assertTrue(tb.isSpecial(div));
    }

    @Test
    public void testFormattingElementsManagement() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        assertNull(tb.lastFormattingElement());
        assertNull(tb.removeLastFormattingElement());

        Element b1 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        Element b2 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        Element b3 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        Element b4 = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");

        tb.pushActiveFormattingElements(b1);
        tb.pushActiveFormattingElements(b2);
        tb.pushActiveFormattingElements(b3);
        tb.pushActiveFormattingElements(b4); // Should trigger removal of duplicates/max limit

        assertTrue(tb.isInActiveFormattingElements(b4));
        assertEquals(b4, tb.getActiveFormattingElement("b"));

        Element bNew = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "");
        tb.replaceActiveFormattingElement(b4, bNew);
        assertTrue(tb.isInActiveFormattingElements(bNew));

        tb.insertMarkerToFormattingElements();
        tb.reconstructFormattingElements();
        tb.clearFormattingElementsToLastMarker();

        tb.removeFromActiveFormattingElements(bNew);
        assertFalse(tb.isInActiveFormattingElements(bNew));
    }

    @Test
    public void testInsertionsAndTokens() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("div");
        Element el = tb.insert(startTag);
        assertNotNull(el);

        Element elStart = tb.insertStartTag("span");
        assertNotNull(elStart);

        Token.StartTag selfClosing = new Token.StartTag();
        selfClosing.name("img");
        selfClosing.selfClose();
        Element elEmpty = tb.insert(selfClosing);
        assertNotNull(elEmpty);

        Token.StartTag formTag = new Token.StartTag();
        formTag.name("form");
        FormElement formEl = tb.insertForm(formTag, true);
        assertNotNull(formEl);

        Token.Comment comment = new Token.Comment();
        comment.data("test comment");
        tb.insert(comment);

        tb.push(new Element(Tag.valueOf("script", ParseSettings.htmlDefault), ""));
        Token.Character chScript = new Token.Character();
        chScript.data("var x = 1;");
        tb.insert(chScript);

        tb.pop();
        tb.push(new Element(Tag.valueOf("div", ParseSettings.htmlDefault), ""));
        Token.Character chText = new Token.Character();
        chText.data("Hello text");
        tb.insert(chText);

        assertNotNull(tb.toString());
    }

    @Test
    public void testFosterParenting() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.initialiseParse(new StringReader(""), "", new ParseErrorList(0, 10), ParseSettings.htmlDefault);

        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "");
        Element table = new Element(Tag.valueOf("table", ParseSettings.htmlDefault), "");
        
        tb.push(div);
        tb.push(table);
        tb.setFosterInserts(true);

        TextNode tn = new TextNode("foster text", "");
        tb.insertInFosterParent(tn);

        // Test foster parent with table having a parent
        tb.getStack().clear();
        Element root = new Element(Tag.valueOf("body", ParseSettings.htmlDefault), "");
        tb.push(root);
        tb.push(table);
        root.appendChild(table);
        
        TextNode tn2 = new TextNode("foster text 2", "");
        tb.insertInFosterParent(tn2);
    }
}