package org.jsoup.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.Attributes;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class HtmlTreeBuilderTest {

    @Test
    public void testInitialStateAndParse() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        Document doc = treeBuilder.parse("<html><head><title>Test</title></head><body><p>Hello World</p></body></html>", "http://example.com", errors);
        
        assertNotNull(doc);
        assertEquals("http://example.com", treeBuilder.getBaseUri());
        assertFalse(treeBuilder.isFragmentParsing());
        assertEquals(HtmlTreeBuilderState.InBody, treeBuilder.state());
    }

    @Test
    public void testParseFragmentWithContext() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        Element context = new Element(Tag.valueOf("body"), "http://example.com");
        
        List<Node> nodes = treeBuilder.parseFragment("<div>Fragment</div>", context, "http://example.com", errors);
        
        assertNotNull(nodes);
        assertTrue(treeBuilder.isFragmentParsing());
    }

    @Test
    public void testParseFragmentNullContext() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        
        List<Node> nodes = treeBuilder.parseFragment("<div>Fragment</div>", null, "http://example.com", errors);
        
        assertNotNull(nodes);
        assertTrue(treeBuilder.isFragmentParsing());
    }

    @Test
    public void testStateAndTransitions() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        assertNull(treeBuilder.state());
        
        treeBuilder.transition(HtmlTreeBuilderState.Initial);
        assertEquals(HtmlTreeBuilderState.Initial, treeBuilder.state());

        treeBuilder.markInsertionMode();
        assertEquals(HtmlTreeBuilderState.Initial, treeBuilder.originalState());

        treeBuilder.transition(HtmlTreeBuilderState.InBody);
        assertEquals(HtmlTreeBuilderState.InBody, treeBuilder.state());
        
        treeBuilder.framesetOk(false);
        assertFalse(treeBuilder.framesetOk());
        
        treeBuilder.framesetOk(true);
        assertTrue(treeBuilder.framesetOk());
    }

    @Test
    public void testBaseUriHandling() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        treeBuilder.parse("<html><head><base href=\"http://foo.com/bar\"></head><body></body></html>", "http://example.com", errors);
        
        assertEquals("http://foo.com/bar", treeBuilder.getBaseUri());
    }

    @Test
    public void testInsertionMethods() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        treeBuilder.parse("<html><head></head><body></body></html>", "http://example.com", errors);

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("div");
        Element el = treeBuilder.insert(startTag);
        assertNotNull(el);
        assertEquals("div", el.tagName());

        Element startTagNamed = treeBuilder.insertStartTag("span");
        assertNotNull(startTagNamed);
        assertEquals("span", startTagNamed.tagName());

        Token.StartTag selfClosing = new Token.StartTag();
        selfClosing.name("br");
        selfClosing.selfClose();
        Element emptyEl = treeBuilder.insert(selfClosing);
        assertNotNull(emptyEl);
        assertEquals("br", emptyEl.tagName());

        Token.Comment commentToken = new Token.Comment();
        commentToken.data("comment data");
        treeBuilder.insert(commentToken);

        Token.Character charToken = new Token.Character();
        charToken.data("Some text");
        treeBuilder.insert(charToken);
    }

    @Test
    public void testScriptStyleCharacterInsertion() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        treeBuilder.parse("<html><head></head><body><script>var x = 1;</script><style>.a {}</style></body></html>", "http://example.com", errors);
        
        Token.Character charToken = new Token.Character();
        charToken.data("var y = 2;");
        
        // Push script to stack manually to trigger DataNode insertion
        Element script = new Element(Tag.valueOf("script"), "http://example.com");
        treeBuilder.push(script);
        treeBuilder.insert(charToken);
        
        Element style = new Element(Tag.valueOf("style"), "http://example.com");
        treeBuilder.push(style);
        treeBuilder.insert(charToken);
    }

    @Test
    public void testFormInsertion() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        treeBuilder.parse("<html><head></head><body></body></html>", "http://example.com", errors);

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("form");
        FormElement formEl = treeBuilder.insertForm(startTag, true);
        
        assertNotNull(formEl);
        assertEquals(formEl, treeBuilder.getFormElement());
    }

    @Test
    public void testStackOperations() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element el1 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("span"), "http://example.com");

        treeBuilder.push(el1);
        treeBuilder.push(el2);

        assertTrue(treeBuilder.onStack(el1));
        assertTrue(treeBuilder.onStack(el2));
        assertEquals(el2, treeBuilder.getFromStack("span"));
        assertNull(treeBuilder.getFromStack("nonexistent"));

        assertEquals(el2, treeBuilder.aboveOnStack(el2));

        Element popped = treeBuilder.pop();
        assertEquals(el2, popped);
        assertFalse(treeBuilder.onStack(el2));

        assertTrue(treeBuilder.removeFromStack(el1));
        assertFalse(treeBuilder.removeFromStack(el1));

        treeBuilder.push(el1);
        treeBuilder.push(el2);
        treeBuilder.popStackToClose("div");
        assertFalse(treeBuilder.onStack(el1));

        treeBuilder.push(el1);
        treeBuilder.push(el2);
        treeBuilder.popStackToClose("div", "span");

        treeBuilder.push(el1);
        treeBuilder.push(el2);
        treeBuilder.popStackToBefore("div");
    }

    @Test
    public void testStackTableContextClearing() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element html = new Element(Tag.valueOf("html"), "http://example.com");
        Element table = new Element(Tag.valueOf("table"), "http://example.com");
        Element tbody = new Element(Tag.valueOf("tbody"), "http://example.com");
        Element tr = new Element(Tag.valueOf("tr"), "http://example.com");
        Element div = new Element(Tag.valueOf("div"), "http://example.com");

        treeBuilder.push(html);
        treeBuilder.push(table);
        treeBuilder.push(tbody);
        treeBuilder.push(div);

        treeBuilder.clearStackToTableContext();
        treeBuilder.clearStackToTableBodyContext();

        treeBuilder.push(tr);
        treeBuilder.clearStackToTableRowContext();
    }

    @Test
    public void testStackQueueManipulation() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element el1 = new Element(Tag.valueOf("div"), "http://example.com");
        Element el2 = new Element(Tag.valueOf("span"), "http://example.com");
        Element el3 = new Element(Tag.valueOf("p"), "http://example.com");

        treeBuilder.push(el1);
        treeBuilder.insertOnStackAfter(el1, el2);
        assertTrue(treeBuilder.onStack(el2));

        treeBuilder.replaceOnStack(el2, el3);
        assertTrue(treeBuilder.onStack(el3));
        assertFalse(treeBuilder.onStack(el2));
    }

    @Test
    public void testResetInsertionModeVariations() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        
        String[] tags = {"select", "td", "tr", "tbody", "caption", "colgroup", "table", "head", "body", "frameset", "html"};
        for (String tag : tags) {
            treeBuilder.getStack().clear();
            treeBuilder.push(new Element(Tag.valueOf(tag), "http://example.com"));
            treeBuilder.resetInsertionMode();
        }

        // Test last element fallback
        treeBuilder.getStack().clear();
        Element context = new Element(Tag.valueOf("div"), "http://example.com");
        // Use parseFragment or set context via parsing to test last = true scenario
        treeBuilder.parseFragment("content", context, "http://example.com", ParseErrorList.tracking(10));
    }

    @Test
    public void testScopesAndChecking() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element html = new Element(Tag.valueOf("html"), "http://example.com");
        Element div = new Element(Tag.valueOf("div"), "http://example.com");
        Element button = new Element(Tag.valueOf("button"), "http://example.com");
        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        Element option = new Element(Tag.valueOf("option"), "http://example.com");
        Element li = new Element(Tag.valueOf("li"), "http://example.com");

        treeBuilder.push(html);
        treeBuilder.push(div);
        treeBuilder.push(button);

        assertTrue(treeBuilder.inScope("div"));
        assertTrue(treeBuilder.inScope(new String[]{"div", "span"}));
        assertTrue(treeBuilder.inButtonScope("button"));
        assertTrue(treeBuilder.inListItemScope("div"));

        treeBuilder.getStack().clear();
        treeBuilder.push(html);
        treeBuilder.push(select);
        treeBuilder.push(option);
        assertTrue(treeBuilder.inSelectScope("option"));

        treeBuilder.getStack().clear();
        treeBuilder.push(html);
        treeBuilder.push(new Element(Tag.valueOf("table"), "http://example.com"));
        assertTrue(treeBuilder.inTableScope("table"));
    }

    @Test
    public void testHeadElementAndFosterInserts() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element head = new Element(Tag.valueOf("head"), "http://example.com");
        treeBuilder.setHeadElement(head);
        assertEquals(head, treeBuilder.getHeadElement());

        assertFalse(treeBuilder.isFosterInserts());
        treeBuilder.setFosterInserts(true);
        assertTrue(treeBuilder.isFosterInserts());

        treeBuilder.newPendingTableCharacters();
        assertNotNull(treeBuilder.getPendingTableCharacters());
        treeBuilder.setPendingTableCharacters(new ArrayList<String>());
    }

    @Test
    public void testImpliedEndTags() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element html = new Element(Tag.valueOf("html"), "http://example.com");
        Element p = new Element(Tag.valueOf("p"), "http://example.com");
        
        treeBuilder.push(html);
        treeBuilder.push(p);

        treeBuilder.generateImpliedEndTags("html");
        treeBuilder.generateImpliedEndTags();

        assertTrue(treeBuilder.isSpecial(new Element(Tag.valueOf("div"), "http://example.com")));
        assertFalse(treeBuilder.isSpecial(new Element(Tag.valueOf("span"), "http://example.com")));
    }

    @Test
    public void testFormattingElements() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element b1 = new Element(Tag.valueOf("b"), "http://example.com");
        Element b2 = new Element(Tag.valueOf("b"), "http://example.com");
        Element b3 = new Element(Tag.valueOf("b"), "http://example.com");
        Element b4 = new Element(Tag.valueOf("b"), "http://example.com");

        assertNull(treeBuilder.lastFormattingElement());
        assertNull(treeBuilder.removeLastFormattingElement());

        treeBuilder.pushActiveFormattingElements(b1);
        treeBuilder.pushActiveFormattingElements(b2);
        treeBuilder.pushActiveFormattingElements(b3);
        treeBuilder.pushActiveFormattingElements(b4); // Should remove first identical element due to limit of 3

        assertEquals(b4, treeBuilder.lastFormattingElement());
        assertTrue(treeBuilder.isInActiveFormattingElements(b4));
        assertEquals(b4, treeBuilder.getActiveFormattingElement("b"));

        Element b5 = new Element(Tag.valueOf("b"), "http://example.com");
        treeBuilder.replaceActiveFormattingElement(b4, b5);
        assertTrue(treeBuilder.isInActiveFormattingElements(b5));

        treeBuilder.insertMarkerToFormattingElements();
        treeBuilder.clearFormattingElementsToLastMarker();

        treeBuilder.removeFromActiveFormattingElements(b5);
        assertFalse(treeBuilder.isInActiveFormattingElements(b5));
    }

    @Test
    public void testReconstructFormattingElements() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        Element b = new Element(Tag.valueOf("b"), "http://example.com");
        treeBuilder.pushActiveFormattingElements(b);
        
        // Element b is not on stack, should trigger reconstruction
        treeBuilder.reconstructFormattingElements();
        
        // Push to stack, then reconstruct (should return early)
        treeBuilder.push(b);
        treeBuilder.reconstructFormattingElements();
    }

    @Test
    public void testFosterParenting() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        treeBuilder.parse("<html><head></head><body><table><tbody><tr><td></td></tr></tbody></table></body></html>", "http://example.com", errors);

        treeBuilder.setFosterInserts(true);
        TextNode textNode = new TextNode("foster text", "http://example.com");
        
        // Test insert in foster parent when table has a parent
        treeBuilder.insert(textNode);

        // Test insert in foster parent when table has no parent (last table above on stack or root)
        treeBuilder.getStack().clear();
        Element root = new Element(Tag.valueOf("html"), "http://example.com");
        treeBuilder.push(root);
        Element table = new Element(Tag.valueOf("table"), "http://example.com");
        treeBuilder.push(table);
        
        treeBuilder.insertInFosterParent(new TextNode("frag foster", "http://example.com"));
    }

    @Test
    public void testToString() throws Throwable {
        HtmlTreeBuilder treeBuilder = new HtmlTreeBuilder();
        String str = treeBuilder.toString();
        assertNotNull(str);
        assertTrue(str.contains("TreeBuilder{"));
    }
}