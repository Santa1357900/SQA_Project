package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

public class HtmlTreeBuilderClaudeTest {

    private HtmlTreeBuilder newInitializedBuilder() {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.parse("<html><head></head><body></body></html>", "http://example.com/", ParseErrorList.noTracking());
        return tb;
    }

    // parse(): returns a Document and getDocument() exposes the same instance
    @Test
    public void testParse_returnsDocument_andGetDocumentMatches() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Document doc = tb.parse("<html><head></head><body>Hi</body></html>", "http://example.com/", ParseErrorList.noTracking());
        assertNotNull(doc);
        assertSame(doc, tb.getDocument());
    }

    // parse(): baseUriSetFromDoc is reset to false on every new parse() call on a reused builder
    @Test
    public void testParse_reusedBuilder_resetsBaseUriFlagBetweenParses() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.parse("<html><head><base href='http://first.com/'></head></html>", "http://orig1.com/", ParseErrorList.noTracking());
        assertEquals("http://first.com/", tb.getBaseUri());
        tb.parse("<html><head></head></html>", "http://orig2.com/", ParseErrorList.noTracking());
        assertEquals("http://orig2.com/", tb.getBaseUri());
    }

    // parseFragment(): context == null branch returns doc.childNodes() and sets fragmentParsing true
    @Test
    public void testParseFragment_nullContext_returnsDocChildNodes_andSetsFragmentParsing() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        List<Node> nodes = tb.parseFragment("<p>Hi</p>", null, "http://example.com/", ParseErrorList.noTracking());
        assertTrue(tb.isFragmentParsing());
        assertNotNull(nodes);
    }

    // parseFragment(): with a plain context tag (default tokeniser branch) content is inserted under the synthetic root
    @Test
    public void testParseFragment_withDivContext_returnsRootChildNodesContainingSpan() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("div"), "http://example.com/");
        List<Node> nodes = tb.parseFragment("<span>hi</span>", context, "http://example.com/", ParseErrorList.noTracking());
        assertTrue(nodes.size() >= 1);
        assertTrue(nodes.get(0) instanceof Element);
        assertEquals("span", ((Element) nodes.get(0)).tagName());
    }



    // transition()/state()/markInsertionMode()/originalState() basic state bookkeeping
    @Test
    public void testTransitionStateMarkInsertionModeOriginalState() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.transition(HtmlTreeBuilderState.InBody);
        assertSame(HtmlTreeBuilderState.InBody, tb.state());
        tb.markInsertionMode();
        tb.transition(HtmlTreeBuilderState.InTable);
        assertSame(HtmlTreeBuilderState.InBody, tb.originalState());
        assertSame(HtmlTreeBuilderState.InTable, tb.state());
    }

    // framesetOk(): default true, settable to false
    @Test
    public void testFramesetOk_defaultTrue_thenSetFalse() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertTrue(tb.framesetOk());
        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
    }

    // ma    // maybeSetBaseUri(): first <base href> on parse sets baseUri; a second <base> is ignored (baseUriSetFromDoc guard)
    @Test
    public void testMaybeSetBaseUri_firstBaseSets_secondBaseIgnored() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.parse("<html><head><base href='http://first.com/'><base href='http://second.com/'></head></html>", "http://orig.com/", ParseErrorList.noTracking());
        assertEquals("http://first.com/", tb.getBaseUri());
    }

    // getBaseUri(): with no <base> tag present, baseUri stays as the original parse baseUri
    @Test
    public void testGetBaseUri_noBaseTag_remainsOriginalParseBaseUri() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        tb.parse("<html><head></head><body></body></html>", "http://orig.com/", ParseErrorList.noTracking());
        assertEquals("http://orig.com/", tb.getBaseUri());
    }

    // error(): with a tracking error list (canAddError() true) an entry is recorded
    @Test
    public void testError_trackingList_addsError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.tracking(10);
        tb.parse("<html></html>", "http://example.com/", errors);
        int before = errors.size();
        tb.error(HtmlTreeBuilderState.InBody);
        assertEquals(before + 1, errors.size());
    }

    // error(): with a noTracking error list (canAddError() false) no entry is recorded
    @Test
    public void testError_noTrackingList_doesNotAddError() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        ParseErrorList errors = ParseErrorList.noTracking();
        tb.parse("<html></html>", "http://example.com/", errors);
        tb.error(HtmlTreeBuilderState.InBody);
        assertEquals(0, errors.size());
    }

    // insert(Element): when stack is empty, node is appended directly to the document
    @Test
    public void testInsert_element_stackEmpty_appendsToDocument() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        tb.getStack().clear();
        Element el = new Element(Tag.valueOf("div"), "");
        tb.insert(el);
        assertEquals(1, tb.getStack().size());
        List<Node> docChildren = tb.getDocument().childNodes();
        assertSame(el, docChildren.get(docChildren.size() - 1));
    }

    // insertStartTag(String): creates a new element, inserts it, and pushes it on the stack
    @Test
    public void testInsertStartTag_pushesNewElementOnStack() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        tb.getStack().clear();
        Element el = tb.insertStartTag("span");
        assertEquals("span", el.tagName());
        ArrayList<Element> stack = tb.getStack();
        assertSame(el, stack.get(stack.size() - 1));
    }

    // push()/pop(): LIFO stack operations
    @Test
    public void testPopPush_stackOperations() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        tb.getStack().clear();
        Element e = new Element(Tag.valueOf("div"), "");
        tb.push(e);
        assertEquals(1, tb.getStack().size());
        assertSame(e, tb.pop());
        assertEquals(0, tb.getStack().size());
    }

    // onStack(): true for an element pushed on the stack, false otherwise
    @Test
    public void testOnStack_trueForPushedElement_falseForOther() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        tb.getStack().clear();
        Element e1 = new Element(Tag.valueOf("div"), "");
        Element e2 = new Element(Tag.valueOf("span"), "");
        tb.push(e1);
        assertTrue(tb.onStack(e1));
        assertFalse(tb.onStack(e2));
    }

    // getFromStack(): finds by node name or returns null; removeFromStack(): true then false once removed
    @Test
    public void testGetFromStack_andRemoveFromStack() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        Element html = new Element(Tag.valueOf("html"), "");
        Element div = new Element(Tag.valueOf("div"), "");
        stack.add(html);
        stack.add(div);
        assertSame(div, tb.getFromStack("div"));
        assertNull(tb.getFromStack("span"));
        assertTrue(tb.removeFromStack(div));
        assertFalse(tb.removeFromStack(div));
    }

    // popStackToClose(String): pops elements until (and including) the named element
    @Test
    public void testPopStackToClose_singleName_stopsAtMatch() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("div"), ""));
        stack.add(new Element(Tag.valueOf("p"), ""));
        tb.popStackToClose("div");
        assertEquals(1, tb.getStack().size());
        assertEquals("html", tb.getStack().get(0).nodeName());
    }

    // popStackToClose(String...): pops elements until any name in the array matches
    @Test
    public void testPopStackToClose_varargNames_stopsAtAnyMatch() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("tr"), ""));
        stack.add(new Element(Tag.valueOf("td"), ""));
        tb.popStackToClose("td", "th");
        assertEquals(3, tb.getStack().size());
    }

    // popStackToBefore(): removes elements above the match without removing the match itself
    @Test
    public void testPopStackToBefore_stopsBeforeMatchWithoutRemovingIt() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("tr"), ""));
        stack.add(new Element(Tag.valueOf("td"), ""));
        tb.popStackToBefore("tr");
        assertEquals(3, tb.getStack().size());
        assertEquals("tr", tb.getStack().get(tb.getStack().size() - 1).nodeName());
    }

    // clearStackToTableContext(): pops until a "table" (or "html") element is found
    @Test
    public void testClearStackToTableContext_popsUntilTableOrHtml() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("tbody"), ""));
        stack.add(new Element(Tag.valueOf("tr"), ""));
        tb.clearStackToTableContext();
        assertEquals(2, tb.getStack().size());
        assertEquals("table", tb.getStack().get(1).nodeName());
    }

    // aboveOnStack(): returns the element directly below the given element on the stack
    @Test
    public void testAboveOnStack_returnsElementDirectlyBelow() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        Element html = new Element(Tag.valueOf("html"), "");
        Element div = new Element(Tag.valueOf("div"), "");
        Element p = new Element(Tag.valueOf("p"), "");
        stack.add(html);
        stack.add(div);
        stack.add(p);
        assertSame(div, tb.aboveOnStack(p));
    }

    // insertOnStackAfter(): inserts right after the given element; replaceOnStack(): swaps a stack slot
    @Test
    public void testInsertOnStackAfter_andReplaceOnStack() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        Element html = new Element(Tag.valueOf("html"), "");
        Element div = new Element(Tag.valueOf("div"), "");
        stack.add(html);
        stack.add(div);
        Element span = new Element(Tag.valueOf("span"), "");
        tb.insertOnStackAfter(div, span);
        assertSame(span, stack.get(2));
        Element replacement = new Element(Tag.valueOf("em"), "");
        tb.replaceOnStack(span, replacement);
        assertSame(replacement, stack.get(2));
    }

    // resetInsertionMode(): node == select -> InSelect
    @Test
    public void testResetInsertionMode_selectOnTop_setsInSelect() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("select"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InSelect, tb.state());
    }

    // BUG: node == th and last == false must set InCell (HTML5 spec). The duplicated "td" check
    // never matches "th", so it wrongly falls through to the "tr" branch below it.
    @Test
    public void testResetInsertionMode_thNotLastOnStack_setsInCellMode() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("tr"), ""));
        stack.add(new Element(Tag.valueOf("th"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InCell, tb.state());
    }

    // resetInsertionMode(): node == tr -> InRow
    @Test
    public void testResetInsertionMode_trOnTop_setsInRow() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("tr"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InRow, tb.state());
    }

    // resetInsertionMode(): node == tbody/thead/tfoot -> InTableBody
    @Test
    public void testResetInsertionMode_tbodyOnTop_setsInTableBody() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("tbody"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InTableBody, tb.state());
    }

    // resetInsertionMode(): node == caption -> InCaption
    @Test
    public void testResetInsertionMode_captionOnTop_setsInCaption() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("caption"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InCaption, tb.state());
    }

    // resetInsertionMode(): node == colgroup -> InColumnGroup
    @Test
    public void testResetInsertionMode_colgroupOnTop_setsInColumnGroup() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("colgroup"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InColumnGroup, tb.state());
    }

    // resetInsertionMode(): node == table -> InTable
    @Test
    public void testResetInsertionMode_tableOnTop_setsInTable() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InTable, tb.state());
    }

    // resetInsertionMode(): node == body -> InBody
    @Test
    public void testResetInsertionMode_bodyOnTop_setsInBody() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("body"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InBody, tb.state());
    }

    // resetInsertionMode(): node == frameset -> InFrameset
    @Test
    public void testResetInsertionMode_framesetOnTop_setsInFrameset() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("frameset"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InFrameset, tb.state());
    }

    // resetInsertionMode(): node == html (not last) -> BeforeHead
    @Test
    public void testResetInsertionMode_htmlOnTop_setsBeforeHead() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("body"), ""));
        stack.add(new Element(Tag.valueOf("html"), ""));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.BeforeHead, tb.state());
    }

    // inScope(): true when target is found before a boundary type, false once a boundary type is hit first
    @Test
    public void testInScope_targetFound_true_blockedByBaseType_false() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("table"), ""));
        stack.add(new Element(Tag.valueOf("div"), ""));
        assertTrue(tb.inScope("div"));
        assertFalse(tb.inScope("span"));
    }

    // inSelectScope(): matches target directly; inButtonScope(): blocked by the "button" extra type
    @Test
    public void testInSelectScope_and_inButtonScope_blocking() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("select"), ""));
        stack.add(new Element(Tag.valueOf("option"), ""));
        assertTrue(tb.inSelectScope("option"));
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("button"), ""));
        assertFalse(tb.inButtonScope("p"));
    }

    // headElement get/set and fosterInserts get/set basic accessors
    @Test
    public void testSimpleGettersSetters_headElement_fosterInserts_formElement() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertNull(tb.getHeadElement());
        Element head = new Element(Tag.valueOf("head"), "");
        tb.setHeadElement(head);
        assertSame(head, tb.getHeadElement());
        assertFalse(tb.isFosterInserts());
        tb.setFosterInserts(true);
        assertTrue(tb.isFosterInserts());
        assertNull(tb.getFormElement());
    }

    // newPendingTableCharacters()/getPendingTableCharacters()/setPendingTableCharacters()
    @Test
    public void testPendingTableCharacters_newSetGet() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertTrue(tb.getPendingTableCharacters().isEmpty());
        List<String> custom = new ArrayList<String>();
        custom.add("x");
        tb.setPendingTableCharacters(custom);
        assertEquals(1, tb.getPendingTableCharacters().size());
        tb.newPendingTableCharacters();
        assertTrue(tb.getPendingTableCharacters().isEmpty());
    }



    // generateImpliedEndTags(excludeTag): when current element already equals excludeTag, nothing is popped
    @Test
    public void testGenerateImpliedEndTags_excludeTagEqualsCurrent_stopsImmediately() throws Throwable {
        HtmlTreeBuilder tb = newInitializedBuilder();
        ArrayList<Element> stack = tb.getStack();
        stack.clear();
        stack.add(new Element(Tag.valueOf("html"), ""));
        stack.add(new Element(Tag.valueOf("p"), ""));
        stack.add(new Element(Tag.valueOf("li"), ""));
        tb.generateImpliedEndTags("li");
        assertEquals(3, tb.getStack().size());
    }

    // isSpecial(): true for a tag in the special list, false for one not in it
    @Test
    public void testIsSpecial_trueForDiv_falseForSpan() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element div = new Element(Tag.valueOf("div"), "");
        Element span = new Element(Tag.valueOf("span"), "");
        assertTrue(tb.isSpecial(div));
        assertFalse(tb.isSpecial(span));
    }

    // lastFormattingElement()/removeLastFormattingElement(): null on empty, element then null after removal
    @Test
    public void testFormattingElements_lastAndRemoveLast_emptyAndNonEmpty() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertNull(tb.lastFormattingElement());
        assertNull(tb.removeLastFormattingElement());
        Element e = new Element(Tag.valueOf("b"), "");
        tb.pushActiveFormattingElements(e);
        assertSame(e, tb.lastFormattingElement());
        assertSame(e, tb.removeLastFormattingElement());
        assertNull(tb.lastFormattingElement());
    }

    // pushActiveFormattingElements(): Noah's Ark clause removes the earliest of 3 identical duplicates
    @Test
    public void testPushActiveFormattingElements_noahsArkRemovesEarliestDuplicate() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element e1 = new Element(Tag.valueOf("div"), "");
        Element e2 = new Element(Tag.valueOf("div"), "");
        Element e3 = new Element(Tag.valueOf("div"), "");
        Element e4 = new Element(Tag.valueOf("div"), "");
        tb.pushActiveFormattingElements(e1);
        tb.pushActiveFormattingElements(e2);
        tb.pushActiveFormattingElements(e3);
        tb.pushActiveFormattingElements(e4);
        assertFalse(tb.isInActiveFormattingElements(e1));
        assertTrue(tb.isInActiveFormattingElements(e4));
    }

    // insertMarkerToFormattingElements()/clearFormattingElementsToLastMarker(): clears back to (and including) the marker
    @Test
    public void testInsertMarker_clearFormattingElementsToLastMarker() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element e1 = new Element(Tag.valueOf("div"), "");
        Element e2 = new Element(Tag.valueOf("span"), "");
        tb.pushActiveFormattingElements(e1);
        tb.insertMarkerToFormattingElements();
        tb.pushActiveFormattingElements(e2);
        tb.clearFormattingElementsToLastMarker();
        assertSame(e1, tb.lastFormattingElement());
    }

    // getActiveFormattingElement(): scan stops at a marker, so an element beyond it is not found
    @Test
    public void testGetActiveFormattingElement_stopsAtMarker() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element e1 = new Element(Tag.valueOf("div"), "");
        Element e2 = new Element(Tag.valueOf("span"), "");
        tb.pushActiveFormattingElements(e1);
        tb.insertMarkerToFormattingElements();
        tb.pushActiveFormattingElements(e2);
        assertNull(tb.getActiveFormattingElement("div"));
        assertSame(e2, tb.getActiveFormattingElement("span"));
    }

    // replaceActiveFormattingElement(): swaps the stored reference in place
    @Test
    public void testReplaceActiveFormattingElement_swapsReference() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        Element e1 = new Element(Tag.valueOf("div"), "");
        Element e2 = new Element(Tag.valueOf("span"), "");
        tb.pushActiveFormattingElements(e1);
        tb.replaceActiveFormattingElement(e1, e2);
        assertSame(e2, tb.lastFormattingElement());
    }
}
