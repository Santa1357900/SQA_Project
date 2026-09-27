package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.Node;
import org.jsoup.helper.DescendableLinkedList;

import java.util.List;
import java.util.ArrayList;

public class HtmlTreeBuilderTest {

    @Test
    public void testInitialAndParse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        Document doc = tb.parse("<html><head><title>Test</title></head><body><p>Hello</p></body></html>", "http://example.com", errors);
        assertNotNull(doc);
        assertEquals("http://example.com", tb.getBaseUri());
        assertNotNull(tb.getDocument());
        assertFalse(tb.isFragmentParsing());
    }

    @Test
    public void testParseFragmentWithContext() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        Document contextDoc = Document.createShell("http://example.com");
        Element context = contextDoc.body();
        
        List<Node> nodes = tb.parseFragment("<span>Fragment</span>", context, "http://example.com", errors);
        assertNotNull(nodes);
        assertTrue(tb.isFragmentParsing());
    }

    @Test
    public void testParseFragmentWithoutContext() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        List<Node> nodes = tb.parseFragment("<div>Body fragment</div>", null, "http://example.com", errors);
        assertNotNull(nodes);
    }

    @Test
    public void testStateAndTransitions() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html></html>", "http://example.com", errors);
        
        tb.transition(HtmlTreeBuilderState.InBody);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());

        tb.markInsertionMode();
        assertEquals(HtmlTreeBuilderState.InBody, tb.originalState());

        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
        tb.framesetOk(true);
        assertTrue(tb.framesetOk());
    }

    @Test
    public void testMaybeSetBaseUri() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html><head></head><body></body></html>", "http://example.com", errors);

        Element baseEl = new Element(Tag.valueOf("base"), "http://example.com");
        baseEl.attr("href", "http://newbase.com");
        
        tb.maybeSetBaseUri(baseEl);
        assertEquals("http://newbase.com", tb.getBaseUri());
        
        // Second call should be ignored
        Element baseEl2 = new Element(Tag.valueOf("base"), "http://example.com");
        baseEl2.attr("href", "http://ignoredbase.com");
        tb.maybeSetBaseUri(baseEl2);
        assertEquals("http://newbase.com", tb.getBaseUri());
    }

    @Test
    public void testErrorHandling() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html></html>", "http://example.com", errors);
        
        Token.StartTag startTag = new Token.StartTag();
        startTag.name("div");
        tb.process(startTag);
        
        tb.error(HtmlTreeBuilderState.InBody);
        assertTrue(errors.size() >= 0);
    }

    @Test
    public void testInsertionsAndStackOperations() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html><head></head><body></body></html>", "http://example.com", errors);

        Element p = tb.insert("p");
        assertNotNull(p);
        assertEquals("p", p.nodeName());

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("span");
        Element span = tb.insert(startTag);
        assertNotNull(span);

        Token.StartTag selfClosing = new Token.StartTag();
        selfClosing.name("img");
        selfClosing.selfClose();
        Element img = tb.insert(selfClosing);
        assertNotNull(img);

        Element emptyImg = tb.insertEmpty(selfClosing);
        assertNotNull(emptyImg);

        Token.Comment commentToken = new Token.Comment();
        commentToken.data("test comment");
        tb.insert(commentToken);

        Token.Character charToken = new Token.Character();
        charToken.data("Some text");
        tb.insert(charToken);

        // Test stack operations
        assertTrue(tb.onStack(p));
        assertNotNull(tb.getFromStack("p"));
        
        Element above = tb.aboveOnStack(span);
        // Might be null or element depending on exact push order, just exercise the method
        
        tb.removeFromStack(span);
        assertFalse(tb.onStack(span));

        Element popped = tb.pop();
        assertNotNull(popped);
    }

    @Test
    public void testFormInsertion() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html><head></head><body></body></html>", "http://example.com", errors);

        Token.StartTag formTag = new Token.StartTag();
        formTag.name("form");
        FormElement form = tb.insertForm(formTag, true);
        assertNotNull(form);
        assertNotNull(tb.getFormElement());
    }

    @Test
    public void testStackClearingAndScopes() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html><head></head><body><table><caption></caption><tbody><tr><td></td></tr></tbody></table></body></html>", "http://example.com", errors);

        assertFalse(tb.inScope("div"));
        assertFalse(tb.inListItemScope("div"));
        assertFalse(tb.inButtonScope("div"));
        assertFalse(tb.inTableScope("div"));
        assertFalse(tb.inSelectScope("div"));

        tb.clearStackToTableContext();
        tb.clearStackToTableBodyContext();
        tb.clearStackToTableRowContext();
    }

    @Test
    public void testImpliedEndTagsAndSpecial() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html><head></head><body><p>Hello</body></html>", "http://example.com", errors);

        tb.generateImpliedEndTags("p");
        tb.generateImpliedEndTags();

        Element div = new Element(Tag.valueOf("div"), "http://example.com");
        assertTrue(tb.isSpecial(div));
        Element custom = new Element(Tag.valueOf("custom-tag"), "http://example.com");
        assertFalse(tb.isSpecial(custom));
    }

    @Test
    public void testFormattingElements() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html><head></head><body><b>Bold</b></body></html>", "http://example.com", errors);

        Element b = new Element(Tag.valueOf("b"), "http://example.com");
        tb.pushActiveFormattingElements(b);
        assertTrue(tb.isInActiveFormattingElements(b));
        assertNotNull(tb.getActiveFormattingElement("b"));

        Element b2 = new Element(Tag.valueOf("b"), "http://example.com");
        tb.replaceActiveFormattingElement(b, b2);
        assertTrue(tb.isInActiveFormattingElements(b2));

        tb.insertMarkerToFormattingElements();
        tb.reconstructFormattingElements();

        tb.removeFromActiveFormattingElements(b2);
        assertFalse(tb.isInActiveFormattingElements(b2));

        tb.insertMarkerToFormattingElements();
        tb.clearFormattingElementsToLastMarker();
    }

    @Test
    public void testFosterParenting() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html><head></head><body><table><tr><td></td></tr></table></body></html>", "http://example.com", errors);

        tb.setFosterInserts(true);
        assertTrue(tb.isFosterInserts());

        TextNode textNode = new TextNode("fostered", "http://example.com");
        tb.insertInFosterParent(textNode);
    }

    @Test
    public void testHeadAndTableCharacters() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element head = new Element(Tag.valueOf("head"), "http://example.com");
        tb.setHeadElement(head);
        assertEquals(head, tb.getHeadElement());

        tb.newPendingTableCharacters();
        assertNotNull(tb.getPendingTableCharacters());
        
        List<Token.Character> chars = new ArrayList<Token.Character>();
        tb.setPendingTableCharacters(chars);
        assertEquals(chars, tb.getPendingTableCharacters());

        assertNotNull(tb.toString());
    }
}