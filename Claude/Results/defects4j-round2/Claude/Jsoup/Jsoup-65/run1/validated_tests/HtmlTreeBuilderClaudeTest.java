package org.jsoup.parser;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class HtmlTreeBuilderClaudeTest {

    private HtmlTreeBuilder tb;

    @Before
    public void setUp() throws Throwable {
        tb = new HtmlTreeBuilder();
    }

    // covers: defaultSettings() returns the shared htmlDefault ParseSettings instance
    @Test
    public void testDefaultSettings_returnsHtmlDefaultSettings() throws Throwable {
        ParseSettings settings = tb.defaultSettings();
        assertSame(ParseSettings.htmlDefault, settings);
    }

    // covers: initialiseParse resets all fields to their initial values
    @Test
    public void testInitialiseParse_setsInitialFieldValues() throws Throwable {
        tb.initialiseParse(new StringReader("abc"), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.Initial, tb.state());
        assertTrue(tb.framesetOk());
        assertFalse(tb.isFragmentParsing());
        assertEquals("http://example.com/", tb.getBaseUri());
        assertNotNull(tb.getDocument());
        assertNull(tb.getHeadElement());
        assertNull(tb.getFormElement());
        assertTrue(tb.getPendingTableCharacters().isEmpty());
    }

    // covers: parseFragment with null context falls back to full document parse and sets fragmentParsing true
    @Test
    public void testParseFragment_contextNull_returnsDocChildNodesAndFragmentFlagTrue() throws Throwable {
        List<Node> nodes = tb.parseFragment("<p>Hi</p>", null, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertNotNull(nodes);
        assertTrue(nodes.size() > 0);
        assertTrue(tb.isFragmentParsing());
    }



    // covers: th branch with last=true also must fall back to InBody
    @Test
    public void testParseFragment_contextTh_lastTrue_resetsToInBody() throws Throwable {
        Element context = new Element(Tag.valueOf("th", ParseSettings.htmlDefault), "http://example.com/");
        tb.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());
    }

    // covers: select branch transitions unconditionally to InSelect
    @Test
    public void testParseFragment_contextSelect_resetsToInSelect() throws Throwable {
        Element context = new Element(Tag.valueOf("select", ParseSettings.htmlDefault), "http://example.com/");
        tb.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.InSelect, tb.state());
    }

    // covers: table branch transitions to InTable
    @Test
    public void testParseFragment_contextTable_resetsToInTable() throws Throwable {
        Element context = new Element(Tag.valueOf("table", ParseSettings.htmlDefault), "http://example.com/");
        tb.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.InTable, tb.state());
    }



    // covers: frameset branch transitions to InFrameset
    @Test
    public void testParseFragment_contextFrameset_resetsToInFrameset() throws Throwable {
        Element context = new Element(Tag.valueOf("frameset", ParseSettings.htmlDefault), "http://example.com/");
        tb.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.InFrameset, tb.state());
    }

    // covers: unmatched tag name falls through to final "else if (last)" -> InBody
    @Test
    public void testParseFragment_contextUnknownTag_fallsBackToInBody() throws Throwable {
        Element context = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "http://example.com/");
        tb.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.InBody, tb.state());
    }

    // covers: transition(state) and state() getter/setter
    @Test
    public void testTransitionAndState_setsCurrentState() throws Throwable {
        tb.transition(HtmlTreeBuilderState.InTable);
        assertEquals(HtmlTreeBuilderState.InTable, tb.state());
    }

    // covers: markInsertionMode stores current state into originalState
    @Test
    public void testMarkInsertionMode_savesOriginalState() throws Throwable {
        tb.transition(HtmlTreeBuilderState.InBody);
        tb.markInsertionMode();
        tb.transition(HtmlTreeBuilderState.InCell);
        assertEquals(HtmlTreeBuilderState.InBody, tb.originalState());
        assertEquals(HtmlTreeBuilderState.InCell, tb.state());
    }

    // covers: framesetOk(boolean) setter and framesetOk() getter
    @Test
    public void testFramesetOk_setterGetter() throws Throwable {
        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
        tb.framesetOk(true);
        assertTrue(tb.framesetOk());
    }

    // covers: insertStartTag/insert(Element)/insertNode with empty stack branch
    @Test
    public void testInsertStartTag_emptyStack_appendsToDocumentAndPushesStack() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element el = tb.insertStartTag("div");
        assertEquals("div", el.tagName());
        assertEquals(1, tb.getStack().size());
        assertSame(el, tb.getStack().get(0));
    }

    // covers: pop() and push(Element)
    @Test
    public void testPopAndPush_stackManipulation() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        Element b = tb.insertStartTag("body");
        Element popped = tb.pop();
        assertSame(b, popped);
        assertEquals(1, tb.getStack().size());
        tb.push(popped);
        assertEquals(2, tb.getStack().size());
        assertSame(b, tb.getStack().get(1));
    }

    // covers: onStack true and false cases
    @Test
    public void testOnStack_trueAndFalseCases() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element a = tb.insertStartTag("html");
        Element notPushed = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com/");
        assertTrue(tb.onStack(a));
        assertFalse(tb.onStack(notPushed));
    }

    // covers: getFromStack found and not-found cases
    @Test
    public void testGetFromStack_foundAndNotFound() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("body");
        assertNotNull(tb.getFromStack("body"));
        assertNull(tb.getFromStack("table"));
    }

    // covers: removeFromStack true and false cases
    @Test
    public void testRemoveFromStack_trueAndFalseCases() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element html = tb.insertStartTag("html");
        Element notPushed = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com/");
        assertFalse(tb.removeFromStack(notPushed));
        assertTrue(tb.removeFromStack(html));
        assertEquals(0, tb.getStack().size());
    }

    // covers: popStackToClose(String) single name match
    @Test
    public void testPopStackToClose_singleName() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("div");
        tb.insertStartTag("p");
        tb.popStackToClose("div");
        assertEquals(1, tb.getStack().size());
        assertEquals("html", tb.getStack().get(0).nodeName());
    }

    // covers: popStackToClose(String...) varargs match
    @Test
    public void testPopStackToClose_varargsNames() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("table");
        tb.insertStartTag("tr");
        tb.popStackToClose("td", "tr");
        assertEquals(2, tb.getStack().size());
        assertEquals("table", tb.getStack().get(1).nodeName());
    }

    // covers: popStackToBefore stops before the matching element (does not remove it)
    @Test
    public void testPopStackToBefore_stopsBeforeMatchingElement() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("div");
        tb.insertStartTag("p");
        tb.popStackToBefore("div");
        assertEquals(2, tb.getStack().size());
        assertEquals("div", tb.getStack().get(1).nodeName());
    }

    // covers: clearStackToTableRowContext, clearStackToTableBodyContext, clearStackToTableContext
    @Test
    public void testClearStackToTableContexts_stopsAtAppropriateElement() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("table");
        tb.insertStartTag("tbody");
        tb.insertStartTag("tr");
        tb.insertStartTag("td");
        tb.clearStackToTableRowContext();
        assertEquals("tr", tb.getStack().get(tb.getStack().size() - 1).nodeName());
        tb.clearStackToTableBodyContext();
        assertEquals("tbody", tb.getStack().get(tb.getStack().size() - 1).nodeName());
        tb.clearStackToTableContext();
        assertEquals("table", tb.getStack().get(tb.getStack().size() - 1).nodeName());
    }

    // covers: aboveOnStack returns the element directly below the given element
    @Test
    public void testAboveOnStack_returnsElementBelow() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element html = tb.insertStartTag("html");
        Element body = tb.insertStartTag("body");
        assertSame(html, tb.aboveOnStack(body));
    }

    // covers: insertOnStackAfter inserts element right after the given anchor
    @Test
    public void testInsertOnStackAfter_insertsAtCorrectPosition() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element html = tb.insertStartTag("html");
        Element body = tb.insertStartTag("body");
        Element inserted = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com/");
        tb.insertOnStackAfter(html, inserted);
        assertEquals(3, tb.getStack().size());
        assertSame(inserted, tb.getStack().get(1));
        assertSame(body, tb.getStack().get(2));
    }

    // covers: insertOnStackAfter throws when anchor is not on stack
    @Test
    public void testInsertOnStackAfter_notOnStack_throwsException() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element notPushed = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com/");
        Element inserted = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "http://example.com/");
        try {
            tb.insertOnStackAfter(notPushed, inserted);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: replaceOnStack replaces element at its stack position
    @Test
    public void testReplaceOnStack_replacesElement() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element html = tb.insertStartTag("html");
        Element replacement = new Element(Tag.valueOf("body", ParseSettings.htmlDefault), "http://example.com/");
        tb.replaceOnStack(html, replacement);
        assertSame(replacement, tb.getStack().get(0));
    }

    // covers: inScope true (target found) and false (boundary hit) cases
    @Test
    public void testInScope_trueAndFalseCases() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("div");
        tb.insertStartTag("p");
        assertTrue(tb.inScope("p"));
        assertFalse(tb.inScope("span"));
    }

    // covers: inTableScope true and false cases
    @Test
    public void testInTableScope_trueAndFalseCases() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("table");
        tb.insertStartTag("tr");
        tb.insertStartTag("td");
        assertTrue(tb.inTableScope("td"));
        assertFalse(tb.inTableScope("span"));
    }

    // covers: inListItemScope, inButtonScope, inSelectScope true cases
    @Test
    public void testScopeVariants_listItemButtonSelect() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("ul");
        tb.insertStartTag("li");
        assertTrue(tb.inListItemScope("li"));
        tb.insertStartTag("button");
        assertTrue(tb.inButtonScope("button"));
        tb.insertStartTag("select");
        tb.insertStartTag("option");
        assertTrue(tb.inSelectScope("option"));
    }

    // covers: setHeadElement/getHeadElement
    @Test
    public void testSetHeadElementGetHeadElement() throws Throwable {
        Element head = new Element(Tag.valueOf("head", ParseSettings.htmlDefault), "http://example.com/");
        tb.setHeadElement(head);
        assertSame(head, tb.getHeadElement());
    }

    // covers: isFosterInserts/setFosterInserts
    @Test
    public void testFosterInserts_setterGetter() throws Throwable {
        assertFalse(tb.isFosterInserts());
        tb.setFosterInserts(true);
        assertTrue(tb.isFosterInserts());
    }

    // covers: newPendingTableCharacters/getPendingTableCharacters/setPendingTableCharacters
    @Test
    public void testPendingTableCharacters_defaultAndSetter() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertTrue(tb.getPendingTableCharacters().isEmpty());
        List<String> custom = new ArrayList<String>();
        custom.add("x");
        tb.setPendingTableCharacters(custom);
        assertEquals(1, tb.getPendingTableCharacters().size());
        tb.newPendingTableCharacters();
        assertTrue(tb.getPendingTableCharacters().isEmpty());
    }



    // covers: generateImpliedEndTags(excludeTag) does not pop when current element equals excludeTag
    @Test
    public void testGenerateImpliedEndTags_withExcludeTag_noPop() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        tb.insertStartTag("dt");
        tb.generateImpliedEndTags("dt");
        assertEquals(2, tb.getStack().size());
    }

    // covers: isSpecial true and false cases
    @Test
    public void testIsSpecial_trueAndFalseCases() throws Throwable {
        Element div = new Element(Tag.valueOf("div", ParseSettings.htmlDefault), "http://example.com/");
        Element span = new Element(Tag.valueOf("span", ParseSettings.htmlDefault), "http://example.com/");
        assertTrue(tb.isSpecial(div));
        assertFalse(tb.isSpecial(span));
    }

    // covers: pushActiveFormattingElements, lastFormattingElement, getActiveFormattingElement, removeLastFormattingElement
    @Test
    public void testFormattingElements_pushLastRemoveAndGetActive() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertNull(tb.lastFormattingElement());
        Element b = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "http://example.com/");
        tb.pushActiveFormattingElements(b);
        assertSame(b, tb.lastFormattingElement());
        assertSame(b, tb.getActiveFormattingElement("b"));
        assertNull(tb.getActiveFormattingElement("i"));
        Element removed = tb.removeLastFormattingElement();
        assertSame(b, removed);
        assertNull(tb.lastFormattingElement());
    }

    // covers: reconstructFormattingElements no-op when there is no last element, and no-op when last is on stack
    @Test
    public void testReconstructFormattingElements_noOpWhenNoneOrOnStack() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.reconstructFormattingElements();
        assertEquals(0, tb.getStack().size());
        Element b = tb.insertStartTag("b");
        tb.pushActiveFormattingElements(b);
        tb.reconstructFormattingElements();
        assertEquals(1, tb.getStack().size());
    }

    // covers: insertMarkerToFormattingElements, replaceActiveFormattingElement, clearFormattingElementsToLastMarker
    @Test
    public void testReplaceActiveFormattingElementAndMarkerClear() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element b = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "http://example.com/");
        Element i = new Element(Tag.valueOf("i", ParseSettings.htmlDefault), "http://example.com/");
        tb.insertMarkerToFormattingElements();
        tb.pushActiveFormattingElements(b);
        tb.replaceActiveFormattingElement(b, i);
        assertSame(i, tb.getActiveFormattingElement("i"));
        tb.clearFormattingElementsToLastMarker();
        assertNull(tb.lastFormattingElement());
    }

    // covers: isInActiveFormattingElements true/false and removeFromActiveFormattingElements
    @Test
    public void testIsInActiveFormattingElements_trueAndFalse() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element b = new Element(Tag.valueOf("b", ParseSettings.htmlDefault), "http://example.com/");
        assertFalse(tb.isInActiveFormattingElements(b));
        tb.pushActiveFormattingElements(b);
        assertTrue(tb.isInActiveFormattingElements(b));
        tb.removeFromActiveFormattingElements(b);
        assertFalse(tb.isInActiveFormattingElements(b));
    }

    // covers: insertInFosterParent with no table on stack appends to the bottom-most stack element
    @Test
    public void testInsertInFosterParent_noTable_appendsToStackBase() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element html = tb.insertStartTag("html");
        TextNode text = new TextNode("foo");
        tb.insertInFosterParent(text);
        List<Node> children = html.childNodes();
        assertSame(text, children.get(children.size() - 1));
    }

    // covers: insertInFosterParent with a table whose parent is not null inserts node before the table
    @Test
    public void testInsertInFosterParent_withTable_insertsBeforeTable() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        Element html = tb.insertStartTag("html");
        Element table = tb.insertStartTag("table");
        TextNode text = new TextNode("foo");
        tb.insertInFosterParent(text);
        List<Node> children = html.childNodes();
        assertSame(text, children.get(0));
        assertSame(table, children.get(1));
    }

    // covers: toString includes builder state information
    @Test
    public void testToString_containsClassInfo() throws Throwable {
        tb.initialiseParse(new StringReader(""), "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        tb.insertStartTag("html");
        String s = tb.toString();
        assertTrue(s.contains("TreeBuilder"));
    }
}
