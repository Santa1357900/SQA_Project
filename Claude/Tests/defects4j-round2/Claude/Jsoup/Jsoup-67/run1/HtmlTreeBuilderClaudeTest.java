package org.jsoup.parser;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.io.StringReader;
import java.util.List;

public class HtmlTreeBuilderClaudeTest {

    private HtmlTreeBuilder tb;
    private ParseErrorList errors;

    @Before
    public void setUp() throws Throwable {
        tb = new HtmlTreeBuilder();
        errors = ParseErrorList.noTracking();
        tb.initialiseParse(new StringReader(""), "http://example.com/", errors, ParseSettings.htmlDefault);
    }

    private Element el(String name) {
        return new Element(Tag.valueOf(name, ParseSettings.htmlDefault), "");
    }

    // defaultSettings() always returns the shared htmlDefault ParseSettings instance
    @Test
    public void testDefaultSettings_returnsHtmlDefaultParseSettings() throws Throwable {
        HtmlTreeBuilder newTb = new HtmlTreeBuilder();
        assertSame(ParseSettings.htmlDefault, newTb.defaultSettings());
    }

    // initialiseParse resets stack/state/flags to their fresh-parse defaults
    @Test
    public void testInitialiseParse_resetsStateAndStackToDefaults() throws Throwable {
        assertEquals(0, tb.getStack().size());
        assertSame(HtmlTreeBuilderState.Initial, tb.state());
        assertFalse(tb.isFragmentParsing());
        assertTrue(tb.framesetOk());
        assertFalse(tb.isFosterInserts());
        assertNull(tb.getHeadElement());
    }

    // parseFragment with null context returns the document's own child nodes
    @Test
    public void testParseFragment_nullContext_returnsDocumentChildNodes() throws Throwable {
        List<Node> result = tb.parseFragment("<p>Hi</p>", null, "http://example.com/", errors, ParseSettings.htmlDefault);
        assertNotNull(result);
        assertTrue(tb.isFragmentParsing());
        assertTrue(result.size() > 0);
    }

    // parseFragment with a non-null context builds a root and returns its child nodes
    @Test
    public void testParseFragment_withContextElement_returnsRootChildNodes() throws Throwable {
        Element context = el("div");
        List<Node> result = tb.parseFragment("<span>Hi</span>", context, "http://example.com/", errors, ParseSettings.htmlDefault);
        assertNotNull(result);
        assertTrue(tb.isFragmentParsing());
    }

    // ancestor chain search finds a FormElement and sets it as the current form
    @Test
    public void testParseFragment_contextWithFormAncestor_setsFormElement() throws Throwable {
        Document doc = Jsoup.parse("<form><div id=d></div></form>");
        Element context = doc.getElementById("d");
        tb.parseFragment("x", context, "http://example.com/", errors, ParseSettings.htmlDefault);
        assertNotNull(tb.getFormElement());
        assertEquals("form", tb.getFormElement().tagName());
    }

    // no FormElement in ancestor chain leaves formElement null
    @Test
    public void testParseFragment_contextWithoutFormAncestor_formElementStaysNull() throws Throwable {
        Document doc = Jsoup.parse("<div id=d2></div>");
        Element context = doc.getElementById("d2");
        tb.parseFragment("x", context, "http://example.com/", errors, ParseSettings.htmlDefault);
        assertNull(tb.getFormElement());
    }

    // transition()/state() set and read the current insertion mode
    @Test
    public void testTransitionAndState_updatesCurrentState() throws Throwable {
        tb.transition(HtmlTreeBuilderState.InTable);
        assertSame(HtmlTreeBuilderState.InTable, tb.state());
    }

    // markInsertionMode captures state at call time; later transitions don't affect it
    @Test
    public void testMarkInsertionModeAndOriginalState_savesCurrentStateAtTimeOfCall() throws Throwable {
        tb.transition(HtmlTreeBuilderState.InCaption);
        tb.markInsertionMode();
        tb.transition(HtmlTreeBuilderState.InTable);
        assertSame(HtmlTreeBuilderState.InCaption, tb.originalState());
    }

    // framesetOk setter/getter toggle both ways
    @Test
    public void testFramesetOk_setterAndGetterToggle() throws Throwable {
        tb.framesetOk(false);
        assertFalse(tb.framesetOk());
        tb.framesetOk(true);
        assertTrue(tb.framesetOk());
    }

    // getBaseUri reflects the uri passed to initialiseParse
    @Test
    public void testGetBaseUri_returnsUriPassedToInitialiseParse() throws Throwable {
        assertEquals("http://example.com/", tb.getBaseUri());
    }

    // isFragmentParsing flips from false to true once parseFragment runs
    @Test
    public void testIsFragmentParsing_falseBeforeParse_trueAfterParseFragment() throws Throwable {
        assertFalse(tb.isFragmentParsing());
        tb.parseFragment("x", null, "http://example.com/", errors, ParseSettings.htmlDefault);
        assertTrue(tb.isFragmentParsing());
    }

    // only the first <base href> is honoured; later calls are ignored
    @Test
    public void testMaybeSetBaseUri_firstHrefSets_subsequentCallsIgnored() throws Throwable {
        Element base1 = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://example.com/");
        base1.attr("href", "sub/page.html");
        tb.maybeSetBaseUri(base1);
        assertEquals("http://example.com/sub/page.html", tb.getBaseUri());
        Element base2 = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://example.com/");
        base2.attr("href", "other.html");
        tb.maybeSetBaseUri(base2);
        assertEquals("http://example.com/sub/page.html", tb.getBaseUri());
    }

    // empty href (no attribute) is ignored, baseUri stays unchanged
    @Test
    public void testMaybeSetBaseUri_emptyHref_isIgnored() throws Throwable {
        Element base = new Element(Tag.valueOf("base", ParseSettings.htmlDefault), "http://example.com/");
        tb.maybeSetBaseUri(base);
        assertEquals("http://example.com/", tb.getBaseUri());
    }

    // insert(Element) with an empty stack appends directly to the document
    @Test
    public void testInsertElement_emptyStack_appendsDirectlyToDocument() throws Throwable {
        Element d = el("div");
        tb.insert(d);
        assertEquals(1, tb.getStack().size());
        assertSame(tb.getDocument(), d.parent());
    }

    // foster insert with a table that has a parent inserts before that table
    @Test
    public void testInsertElement_fosterWithTableHavingParent_insertsBeforeTable() throws Throwable {
        Element div = el("div");
        Element table = el("table");
        div.appendChild(table);
        tb.push(div);
        tb.push(table);
        tb.setFosterInserts(true);
        Element span = el("span");
        tb.insert(span);
        assertSame(span, div.childNodes().get(0));
        assertSame(table, div.childNodes().get(1));
    }

    // foster insert with no table on stack appends to stack's base element
    @Test
    public void testInsertElement_fosterWithNoTableOnStack_appendsToStackBase() throws Throwable {
        Element div = el("div");
        tb.push(div);
        tb.setFosterInserts(true);
        Element p = el("p");
        tb.insert(p);
        assertSame(p, div.childNodes().get(0));
    }

    // insertStartTag builds an element with the given name and pushes it
    @Test
    public void testInsertStartTag_createsElementAndPushesOntoStack() throws Throwable {
        Element inserted = tb.insertStartTag("span");
        assertEquals("span", inserted.tagName());
        assertEquals(1, tb.getStack().size());
        assertSame(inserted, tb.getStack().get(0));
    }

    // push/pop follow LIFO order
    @Test
    public void testPushAndPop_followLifoOrder() throws Throwable {
        Element a = el("a");
        Element b = el("b");
        tb.push(a);
        tb.push(b);
        assertSame(b, tb.pop());
        assertSame(a, tb.pop());
        assertEquals(0, tb.getStack().size());
    }

    // onStack true for pushed element, false for an unrelated one
    @Test
    public void testOnStack_trueForPushedElement_falseForOther() throws Throwable {
        Element a = el("a");
        Element c = el("c");
        tb.push(a);
        assertTrue(tb.onStack(a));
        assertFalse(tb.onStack(c));
    }

    // getFromStack finds by node name or returns null when absent
    @Test
    public void testGetFromStack_findsByNodeNameOrReturnsNull() throws Throwable {
        Element p = el("p");
        tb.push(p);
        assertSame(p, tb.getFromStack("p"));
        assertNull(tb.getFromStack("span"));
    }

    // removeFromStack returns true once, then false since it's no longer present
    @Test
    public void testRemoveFromStack_removesPresentElement_falseWhenAlreadyRemoved() throws Throwable {
        Element p = el("p");
        tb.push(p);
        assertTrue(tb.removeFromStack(p));
        assertFalse(tb.removeFromStack(p));
    }

    // popStackToClose(name) removes until match inclusive; vararg form with no match empties stack
    @Test
    public void testPopStackToClose_singleNameAndVarargs_removeUntilMatchOrEmpty() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.getStack().add(el("div"));
        tb.popStackToClose("table");
        assertEquals(1, tb.getStack().size());
        tb.getStack().add(el("div"));
        tb.popStackToClose(new String[]{"body", "table"});
        assertEquals(0, tb.getStack().size());
    }

    // popStackToBefore stops immediately before the matching element, leaving it on stack
    @Test
    public void testPopStackToBefore_stopsImmediatelyBeforeMatchingElement() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.getStack().add(el("div"));
        tb.popStackToBefore("table");
        assertEquals(2, tb.getStack().size());
        assertEquals("table", tb.getStack().get(1).nodeName());
    }

    // clearStackToTableRowContext removes elements above tr/template/html boundary
    @Test
    public void testClearStackToTableRowContext_removesElementsAboveRowContext() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.getStack().add(el("tbody"));
        tb.getStack().add(el("tr"));
        tb.getStack().add(el("td"));
        tb.clearStackToTableRowContext();
        assertEquals(4, tb.getStack().size());
        assertEquals("tr", tb.getStack().get(3).nodeName());
    }

    // aboveOnStack returns the element directly below the given one
    @Test
    public void testAboveOnStack_returnsElementDirectlyBelowGivenElement() throws Throwable {
        Element a = el("a");
        Element b = el("b");
        Element c = el("c");
        tb.push(a);
        tb.push(b);
        tb.push(c);
        assertSame(b, tb.aboveOnStack(c));
    }

    // insertOnStackAfter inserts right after the anchor; throws when anchor is missing
    @Test
    public void testInsertOnStackAfter_insertsAfterElement_throwsWhenElementMissing() throws Throwable {
        Element a = el("a");
        Element b = el("b");
        Element c = el("c");
        tb.push(a);
        tb.push(b);
        tb.insertOnStackAfter(a, c);
        assertSame(c, tb.getStack().get(1));
        try {
            tb.insertOnStackAfter(el("x"), el("y"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // replaceOnStack swaps the element in place; throws when target is missing
    @Test
    public void testReplaceOnStack_replacesElement_throwsWhenElementMissing() throws Throwable {
        Element a = el("a");
        Element b = el("b");
        Element c = el("c");
        tb.push(a);
        tb.push(b);
        tb.replaceOnStack(a, c);
        assertSame(c, tb.getStack().get(0));
        try {
            tb.replaceOnStack(el("x"), el("y"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // select -> InSelect; table/tr -> InRow
    @Test
    public void testResetInsertionMode_selectAndTr_transitionToInSelectAndInRow() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("select"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InSelect, tb.state());
        tb.getStack().clear();
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.getStack().add(el("tr"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InRow, tb.state());
    }

    // tbody -> InTableBody; caption -> InCaption
    @Test
    public void testResetInsertionMode_tbodyAndCaption_transitionToInTableBodyAndInCaption() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.getStack().add(el("tbody"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InTableBody, tb.state());
        tb.getStack().clear();
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.getStack().add(el("caption"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InCaption, tb.state());
    }

    // colgroup -> InColumnGroup; table -> InTable
    @Test
    public void testResetInsertionMode_colgroupAndTable_transitionToInColumnGroupAndInTable() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("colgroup"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InColumnGroup, tb.state());
        tb.getStack().clear();
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InTable, tb.state());
    }

    // body -> InBody; frameset -> InFrameset
    @Test
    public void testResetInsertionMode_bodyAndFrameset_transitionToInBodyAndInFrameset() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("body"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InBody, tb.state());
        tb.getStack().clear();
        tb.getStack().add(el("html"));
        tb.getStack().add(el("frameset"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.InFrameset, tb.state());
    }

    // html at top of stack (not last) -> BeforeHead
    @Test
    public void testResetInsertionMode_htmlAtTopOfStack_transitionToBeforeHead() throws Throwable {
        tb.getStack().add(el("div"));
        tb.getStack().add(el("html"));
        tb.resetInsertionMode();
        assertSame(HtmlTreeBuilderState.BeforeHead, tb.state());
    }

    // BUG ORACLE: per spec, "td"/"th" only switch to InCell when last == false.
    // In a fragment parse whose context element is <td> (reached as the last/pos==0 node),
    // last is true, so the mode must fall through to InBody, NOT InCell.
    @Test
    public void testResetInsertionMode_fragmentContextTdIsLastNode_switchesToInBodyPerSpec() throws Throwable {
        Document contextDoc = Jsoup.parse("<table><tr><td></td></tr></table>");
        Element context = contextDoc.select("td").first();
        List<Node> result = tb.parseFragment("x", context, "http://example.com/", errors, ParseSettings.htmlDefault);
        assertNotNull(result);
        assertSame(HtmlTreeBuilderState.InBody, tb.state());
    }

    // inScope true for a target on stack, false once the html boundary is reached
    @Test
    public void testInScope_targetFoundTrue_boundaryReachedFalse() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("body"));
        tb.getStack().add(el("div"));
        assertTrue(tb.inScope("div"));
        assertFalse(tb.inScope("p"));
    }

    // inTableScope true for target, false once table boundary is reached
    @Test
    public void testInTableScope_targetFoundTrue_tableBoundaryFalse() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("table"));
        tb.getStack().add(el("tr"));
        assertTrue(tb.inTableScope("tr"));
        assertFalse(tb.inTableScope("td"));
    }

    // inSelectScope true for target, false once a non optgroup/option element is seen
    @Test
    public void testInSelectScope_targetFoundTrue_nonSelectElementBoundaryFalse() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("select"));
        tb.getStack().add(el("option"));
        assertTrue(tb.inSelectScope("option"));
        tb.getStack().clear();
        tb.getStack().add(el("html"));
        tb.getStack().add(el("select"));
        tb.getStack().add(el("div"));
        assertFalse(tb.inSelectScope("option"));
    }

    // inListItemScope true for target, false once ol/ul boundary is reached
    @Test
    public void testInListItemScope_targetFoundTrue_listBoundaryFalse() throws Throwable {
        tb.getStack().add(el("html"));
        tb.getStack().add(el("ul"));
        tb.getStack().add(el("li"));
        assertTrue(tb.inListItemScope("li"));
        tb.getStack().clear();
        tb.getStack().add(el("html"));
        tb.getStack().add(el("ul"));
        tb.getStack().add(el("p"));
        assertFalse(tb.inListItemScope("li"));
    }

    // generateImpliedEndTags pops matching tags until a non-matching current element
    @Test
    public void testGenerateImpliedEndTags_popsMatchingTagsUntilNonMatching() throws Throwable {
        tb.getStack().add(el("body"));
        tb.getStack().add(el("p"));
        tb.generateImpliedEndTags();
        assertEquals(1, tb.getStack().size());
        assertEquals("body", tb.getStack().get(0).nodeName());
    }

    // generateImpliedEndTags(exclude) stops immediately when current equals exclude
    @Test
    public void testGenerateImpliedEndTags_excludeTagMatchesCurrent_doesNotPop() throws Throwable {
        tb.getStack().add(el("body"));
        tb.getStack().add(el("p"));
        tb.generateImpliedEndTags("p");
        assertEquals(2, tb.getStack().size());
    }

    // isSpecial true for a listed special tag, false for an unlisted tag
    @Test
    public void testIsSpecial_knownSpecialTagTrue_unknownTagFalse() throws Throwable {
        assertTrue(tb.isSpecial(el("div")));
        assertFalse(tb.isSpecial(el("span")));
    }

    // Noah's ark clause: a 4th identical formatting element removes the earliest duplicate
    @Test
    public void testPushActiveFormattingElements_noahsArkClause_removesEarliestDuplicateAfterThree() throws Throwable {
        Element e1 = el("font");
        Element e2 = el("font");
        Element e3 = el("font");
        Element e4 = el("font");
        tb.pushActiveFormattingElements(e1);
        tb.pushActiveFormattingElements(e2);
        tb.pushActiveFormattingElements(e3);
        tb.pushActiveFormattingElements(e4);
        assertFalse(tb.isInActiveFormattingElements(e1));
        assertTrue(tb.isInActiveFormattingElements(e4));
        assertSame(e4, tb.getActiveFormattingElement("font"));
    }

    // reconstructFormattingElements is a no-op when there's no last entry, or it's already on stack
    @Test
    public void testReconstructFormattingElements_noLastOrLastOnStack_isNoOp() throws Throwable {
        tb.reconstructFormattingElements();
        assertEquals(0, tb.getStack().size());
        Element b = el("b");
        tb.push(b);
        tb.pushActiveFormattingElements(b);
        tb.reconstructFormattingElements();
        assertEquals(1, tb.getStack().size());
        assertSame(b, tb.getStack().get(0));
    }

    // getActiveFormattingElement stops searching at a marker; clearFormattingElementsToLastMarker
    // removes down through (and including) the marker but keeps entries below it
    @Test
    public void testClearFormattingElementsToLastMarker_stopsAtMarkerKeepsEntriesBelow() throws Throwable {
        Element b1 = el("b");
        tb.pushActiveFormattingElements(b1);
        tb.insertMarkerToFormattingElements();
        Element b2 = el("i");
        tb.pushActiveFormattingElements(b2);
        assertNull(tb.getActiveFormattingElement("b"));
        tb.clearFormattingElementsToLastMarker();
        assertTrue(tb.isInActiveFormattingElements(b1));
        assertFalse(tb.isInActiveFormattingElements(b2));
    }

    // replaceActiveFormattingElement swaps the entry in place by position
    @Test
    public void testReplaceActiveFormattingElement_replacesEntryInPlace() throws Throwable {
        Element out = el("b");
        tb.pushActiveFormattingElements(out);
        Element in = el("i");
        tb.replaceActiveFormattingElement(out, in);
        assertFalse(tb.isInActiveFormattingElements(out));
        assertSame(in, tb.getActiveFormattingElement("i"));
    }

    // removeFromActiveFormattingElements removes only the specified element
    @Test
    public void testRemoveFromActiveFormattingElements_removesOnlyGivenElement() throws Throwable {
        Element e1 = el("b");
        Element e2 = el("i");
        tb.pushActiveFormattingElements(e1);
        tb.pushActiveFormattingElements(e2);
        tb.removeFromActiveFormattingElements(e1);
        assertFalse(tb.isInActiveFormattingElements(e1));
        assertTrue(tb.isInActiveFormattingElements(e2));
    }
}
