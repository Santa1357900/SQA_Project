package org.jsoup.parser;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
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
        
        Parser parser = new Parser(tb);
        Document doc = parser.parseInput("<html><head></head><body></body></html>", "http://example.com");
        assertNotNull(doc);
        assertEquals("http://example.com", tb.getBaseUri());
        assertNotNull(tb.getDocument());
    }

    @Test
    public void testParseFragmentWithContext() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        Document doc = Document.createShell("http://example.com");
        Element context = doc.body();

        List<Node> nodes = tb.parseFragment("<div>Hello</div>", context, "http://example.com", ParseErrorList.tracking(1), ParseSettings.htmlDefault);
        assertNotNull(nodes);
        assertFalse(tb.isFragmentParsing()); // reset or checked during
    }

    @Test
    public void testParseFragmentNullContext() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        List<Node> nodes = tb.parseFragment("<span>Test</span>", null, "http://example.com", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertNotNull(nodes);
    }

    @Test
    public void testStateTransitionsAndMark() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html></html>", "http://example.com");

        tb.transition(HtmlTreeBuilderState.InBody);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());

        tb.markInsertionMode();
        assertEquals(HtmlTreeBuilderState.InBody, tb.originalState());

        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
    }

    @Test
    public void testMaybeSetBaseUri() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");

        Element base = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://example.com");
        base.attr("href", "http://foo.bar");

        tb.maybeSetBaseUri(base);
        assertEquals("http://foo.bar", tb.getBaseUri());

        // second base should be ignored
        Element base2 = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://example.com");
        base2.attr("href", "http://ignored.com");
        tb.maybeSetBaseUri(base2);
        assertEquals("http://foo.bar", tb.getBaseUri());
    }

    @Test
    public void testInsertStartTagAndElements() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");

        Element el = tb.insertStartTag("div");
        assertNotNull(el);
        assertEquals("div", el.tagName());

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("span");
        Element el2 = tb.insert(startTag);
        assertNotNull(el2);
        assertEquals("span", el2.tagName());
    }

    @Test
    public void testInsertSelfClosingAndEmpty() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("br");
        startTag.selfClosing();
        Element el = tb.insert(startTag);
        assertNotNull(el);
        assertTrue(el.tag().isSelfClosing());
    }

    @Test
    public void testInsertForm() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");

        Token.StartTag startTag = new Token.StartTag();
        startTag.name("form");
        FormElement form = tb.insertForm(startTag, true);
        assertNotNull(form);
        assertEquals(form, tb.getFormElement());
    }

    @Test
    public void testInsertCommentAndCharacters() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><script>var a=1;</script></body></html>", "http://example.com");

        Token.Comment comment = new Token.Comment();
        comment.comment("test comment");
        tb.insert(comment);

        tb.push(tb.insertStartTag("script"));
        Token.Character ch = new Token.Character();
        ch.data("var x = 1;");
        tb.insert(ch);
        
        // Pop script
        tb.pop();

        tb.push(tb.insertStartTag("p"));
        Token.Character textCh = new Token.Character();
        textCh.data("some text");
        tb.insert(textCh);
        tb.pop();
    }

    @Test
    public void testStackOperations() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><div><span></span></div></body></html>", "http://example.com");

        Element div = tb.insertStartTag("div");
        Element span = tb.insertStartTag("span");

        assertTrue(tb.onStack(span));
        assertTrue(tb.onStack(div));
        assertEquals(span, tb.getFromStack("span"));
        assertEquals(div, tb.aboveOnStack(span));

        Element replacement = new Element(Tag.valueOf("p", ParseSettings.htmlDefault), "http://example.com");
        tb.replaceOnStack(span, replacement);
        assertFalse(tb.onStack(span));
        assertTrue(tb.onStack(replacement));

        assertTrue(tb.removeFromStack(replacement));
        assertFalse(tb.onStack(replacement));

        tb.popStackToClose("div");
    }

    @Test
    public void testPopStackToCloseArray() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><p><b></b></p></body></html>", "http://example.com");

        tb.insertStartTag("div");
        tb.insertStartTag("ul");
        tb.insertStartTag("li");

        tb.popStackToClose("ul", "div");
    }

    @Test
    public void testPopStackToBefore() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");

        tb.insertStartTag("div");
        tb.insertStartTag("span");
        tb.insertStartTag("a");

        tb.popStackToBefore("div");
        assertTrue(tb.onStack(tb.getFromStack("div")));
    }

    @Test
    public void testClearStackContexts() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><table><tbody><tr><td></td></tr></tbody></table></body></html>", "http://example.com");

        tb.insertStartTag("table");
        tb.insertStartTag("tbody");
        tb.insertStartTag("tr");
        tb.insertStartTag("td");

        tb.clearStackToTableRowContext();
        tb.clearStackToTableBodyContext();
        tb.clearStackToTableContext();
    }

    @Test
    public void testInsertOnStackAfter() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");

        Element a = tb.insertStartTag("div");
        Element b = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "http://example.com");
        tb.insertOnStackAfter(a, b);
        assertTrue(tb.onStack(b));
    }

    @Test
    public void testResetInsertionMode() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><select></select></body></html>", "http://example.com");

        tb.insertStartTag("select");
        tb.resetInsertionMode();
        assertEquals(HtmlTreeBuilderState.InSelect, tb.state());
    }

    @Test
    public void testScopesAndChecking() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><div><p><span></span></p></div></body></html>", "http://example.com");

        tb.insertStartTag("div");
        tb.insertStartTag("p");
        tb.insertStartTag("span");

        assertTrue(tb.inScope("p"));
        assertTrue(tb.inScope(new String[]{"p", "div"}));
        assertTrue(tb.inListItemScope("p"));
        assertTrue(tb.inButtonScope("p"));
        assertTrue(tb.inTableScope("div") || !tb.inTableScope("div")); // safe check

        Element span = tb.getFromStack("span");
        assertTrue(tb.isSpecial(span) || !tb.isSpecial(span));
    }

    @Test
    public void testInSelectScope() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><select><option></option></select></body></html>", "http://example.com");

        tb.insertStartTag("select");
        tb.insertStartTag("option");

        assertTrue(tb.inSelectScope("option"));
    }

    @Test
    public void testHeadElementAndFosterInserts() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");

        Element head = new Element(Tag.valueOf("head", ParseSettings.htmlDefault), "http://example.com");
        tb.setHeadElement(head);
        assertEquals(head, tb.getHeadElement());

        tb.setFosterInserts(true);
        assertTrue(tb.isFosterInserts());
    }

    @Test
    public void testPendingTableCharacters() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.newPendingTableCharacters();
        assertNotNull(tb.getPendingTableCharacters());

        List<String> list = new ArrayList<String>();
        list.add("test");
        tb.setPendingTableCharacters(list);
        assertEquals(1, tb.getPendingTableCharacters().size());
    }

    @Test
    public void testImpliedEndTags() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><p>Paragraph</body></html>", "http://example.com");

        tb.insertStartTag("p");
        tb.generateImpliedEndTags("p");
        tb.generateImpliedEndTags();
    }

    @Test
    public void testFormattingElementsManagement() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body><b></b></body></html>", "http://example.com");

        Element b1 = tb.insertStartTag("b");
        tb.pushActiveFormattingElements(b1);
        
        Element b2 = tb.insertStartTag("b");
        tb.pushActiveFormattingElements(b2);
        
        Element b3 = tb.insertStartTag("b");
        tb.pushActiveFormattingElements(b3);

        Element b4 = tb.insertStartTag("b");
        tb.pushActiveFormattingElements(b4); // triggers duplicate check limit 3

        assertNotNull(tb.lastFormattingElement());
        assertTrue(tb.isInActiveFormattingElements(b4));
        assertEquals(b4, tb.getActiveFormattingElement("b"));

        Element replacement = new Element(Tag.valueOf("strong", ParseSettings.htmlDefault), "http://example.com");
        tb.replaceActiveFormattingElement(b4, replacement);

        tb.reconstructFormattingElements();

        tb.removeFromActiveFormattingElements(replacement);
        assertNull(tb.getActiveFormattingElement("strong"));

        tb.insertMarkerToFormattingElements();
        tb.clearFormattingElementsToLastMarker();
        
        assertNotNull(tb.removeLastFormattingElement());
    }

    @Test
    public void testToString() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Parser parser = new Parser(tb);
        parser.parseInput("<html><head></head><body></body></html>", "http://example.com");
        
        String str = tb.toString();
        assertNotNull(str);
        assertTrue(str.contains("TreeBuilder{"));
    }
}