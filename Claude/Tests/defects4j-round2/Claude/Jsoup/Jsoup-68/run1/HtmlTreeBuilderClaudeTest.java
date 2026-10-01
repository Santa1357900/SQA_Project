package org.jsoup.parser;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

public class HtmlTreeBuilderClaudeTest {

    private HtmlTreeBuilder builder;

    @Before
    public void setUp() throws Throwable {
        builder = new HtmlTreeBuilder();
        builder.initialiseParse(new StringReader(""), "http://example.com/",
                ParseErrorList.noTracking(), ParseSettings.htmlDefault);
    }

    private Element el(String tagName) {
        return new Element(Tag.valueOf(tagName, ParseSettings.htmlDefault), "http://example.com/");
    }

    // covers defaultSettings(): returns the shared htmlDefault settings instance
    @Test
    public void testDefaultSettings_returnsHtmlDefault() throws Throwable {
        HtmlTreeBuilder tb = new HtmlTreeBuilder();
        assertSame(ParseSettings.htmlDefault, tb.defaultSettings());
    }

    // covers initialiseParse(): sets Initial state and framesetOk true
    @Test
    public void testInitialiseParse_setsInitialStateAndFramesetOk() throws Throwable {
        assertEquals(HtmlTreeBuilderState.Initial, builder.state());
        assertTrue(builder.framesetOk());
    }

    // covers parseFragment(): null context returns doc's own children and marks fragment parsing
    @Test
    public void testParseFragment_nullContext_setsFragmentParsingAndReturnsDocChildren() throws Throwable {
        List<Node> nodes = builder.parseFragment("<p>Hi</p>", null, "http://example.com/",
                ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertNotNull(nodes);
        assertTrue(builder.isFragmentParsing());
    }

    // BUG: spec says td/th only switch to InCell when !last; with last=true and context=td, must fall through to InBody
    @Test
    public void testResetInsertionMode_tdContextAsOnlyElement_transitionsToInBodyPerSpec() throws Throwable {
        Element context = el("td");
        builder.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.InBody, builder.state());
    }

    // covers resetInsertionMode(): select branch
    @Test
    public void testResetInsertionMode_selectContext_transitionsToInSelect() throws Throwable {
        Element context = el("select");
        builder.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking(), ParseSettings.htmlDefault);
        assertEquals(HtmlTreeBuilderState.InSelect, builder.state());
    }

    // covers resetInsertionMode(): td branch when not the last element on stack -> InCell
    @Test
    public void testResetInsertionMode_tdElementNotLastOnStack_transitionsToInCell() throws Throwable {
        builder.push(el("html"));
        builder.push(el("td"));
        builder.resetInsertionMode();
        assertEquals(HtmlTreeBuilderState.InCell, builder.state());
    }

    // covers transition()/state(): sets and returns given state
    @Test
    public void testTransitionAndState_setsAndReturnsGivenState() throws Throwable {
        builder.transition(HtmlTreeBuilderState.InTable);
        assertEquals(HtmlTreeBuilderState.InTable, builder.state());
    }

    // covers markInsertionMode()/originalState(): stores current state as original
    @Test
    public void testMarkInsertionMode_storesCurrentStateAsOriginal() throws Throwable {
        builder.transition(HtmlTreeBuilderState.InCaption);
        builder.markInsertionMode();
        assertEquals(HtmlTreeBuilderState.InCaption, builder.originalState());
    }

    // covers framesetOk(boolean)/framesetOk(): setter changes getter result
    @Test
    public void testFramesetOk_setFalse_getFalse() throws Throwable {
        builder.framesetOk(false);
        assertFalse(builder.framesetOk());
    }

    // covers getDocument(): returns non-null Document created during initialiseParse
    @Test
    public void testGetDocument_returnsNonNullDocument() throws Throwable {
        assertNotNull(builder.getDocument());
    }

    // covers getBaseUri(): returns baseUri set during initialiseParse
    @Test
    public void testGetBaseUri_returnsInitialisedBaseUri() throws Throwable {
        assertEquals("http://example.com/", builder.getBaseUri());
    }

    // covers maybeSetBaseUri(): empty href is ignored, baseUri unchanged
    @Test
    public void testMaybeSetBaseUri_emptyHref_doesNotChangeBaseUri() throws Throwable {
        Element base = el("base");
        builder.maybeSetBaseUri(base);
        assertEquals("http://example.com/", builder.getBaseUri());
    }

    // covers isFragmentParsing(): false before parseFragment is called
    @Test
    public void testIsFragmentParsing_falseBeforeParseFragmentCalled() throws Throwable {
        assertFalse(builder.isFragmentParsing());
    }

    // covers insertStartTag()/insert(Element): new element appended as child of current, then pushed
    @Test
    public void testInsertStartTag_addsElementOntoStackAndAsChildOfCurrent() throws Throwable {
        Element root = el("html");
        builder.push(root);
        Element div = builder.insertStartTag("div");
        assertSame(div, builder.getStack().get(builder.getStack().size() - 1));
        assertTrue(root.childNodes().contains(div));
    }

    // covers insert(Element): pushes element and appends as child of current element
    @Test
    public void testInsertElement_appendsAsChildAndPushesOntoStack() throws Throwable {
        Element root = el("html");
        builder.push(root);
        Element span = el("span");
        builder.insert(span);
        assertSame(span, builder.getStack().get(builder.getStack().size() - 1));
        assertTrue(root.childNodes().contains(span));
    }

    // covers pop()/push(): pop removes and returns top element, order preserved below
    @Test
    public void testPopAndPush_stackOrderMaintained() throws Throwable {
        Element e1 = el("div");
        Element e2 = el("span");
        builder.push(e1);
        builder.push(e2);
        Element popped = builder.pop();
        assertSame(e2, popped);
        assertEquals(1, builder.getStack().size());
        assertSame(e1, builder.getStack().get(0));
    }

    // covers onStack(): true for pushed element, false for unrelated element
    @Test
    public void testOnStack_trueForPushedElement_falseForOther() throws Throwable {
        Element e1 = el("div");
        Element e2 = el("span");
        builder.push(e1);
        assertTrue(builder.onStack(e1));
        assertFalse(builder.onStack(e2));
    }

    // covers getFromStack(): finds element by node name, null if absent
    @Test
    public void testGetFromStack_findsByNodeName_returnsNullIfAbsent() throws Throwable {
        Element table = el("table");
        builder.push(table);
        assertSame(table, builder.getFromStack("table"));
        assertNull(builder.getFromStack("div"));
    }

    // covers removeFromStack(): true when removed, false when not present
    @Test
    public void testRemoveFromStack_removesElementReturnsTrue_falseIfNotPresent() throws Throwable {
        Element div = el("div");
        builder.push(div);
        assertTrue(builder.removeFromStack(div));
        assertFalse(builder.onStack(div));
        assertFalse(builder.removeFromStack(div));
    }

    // covers popStackToClose(String): pops until and including matching name
    @Test
    public void testPopStackToClose_singleName_popsUntilAndIncludingMatch() throws Throwable {
        builder.push(el("html"));
        builder.push(el("div"));
        builder.push(el("p"));
        builder.popStackToClose("div");
        assertEquals(1, builder.getStack().size());
        assertEquals("html", builder.getStack().get(0).nodeName());
    }

    // covers popStackToClose(String...): stops at first match found in sorted varargs
    @Test
    public void testPopStackToClose_varargs_stopsAtFirstMatchInSortedArray() throws Throwable {
        builder.push(el("html"));
        builder.push(el("table"));
        builder.push(el("tr"));
        builder.popStackToClose("table", "td");
        assertEquals(1, builder.getStack().size());
    }

    // covers popStackToBefore(): pops entries above target name, keeping target on stack
    @Test
    public void testPopStackToBefore_popsUntilNameFoundKeepingIt() throws Throwable {
        builder.push(el("html"));
        builder.push(el("table"));
        builder.push(el("tr"));
        builder.popStackToBefore("table");
        assertEquals(2, builder.getStack().size());
        assertEquals("table", builder.getStack().get(1).nodeName());
    }

    // covers clearStackToTableContext(): removes elements until table or html boundary
    @Test
    public void testClearStackToTableContext_removesUntilTableOrHtml() throws Throwable {
        builder.push(el("html"));
        builder.push(el("table"));
        builder.push(el("tbody"));
        builder.push(el("tr"));
        builder.clearStackToTableContext();
        assertEquals(2, builder.getStack().size());
        assertEquals("table", builder.getStack().get(1).nodeName());
    }

    // covers aboveOnStack(): returns element directly below given element
    @Test
    public void testAboveOnStack_returnsElementImmediatelyBelow() throws Throwable {
        Element html = el("html");
        Element div = el("div");
        builder.push(html);
        builder.push(div);
        assertSame(html, builder.aboveOnStack(div));
    }

    // covers insertOnStackAfter(): inserts element immediately after given element
    @Test
    public void testInsertOnStackAfter_insertsImmediatelyAfterGivenElement() throws Throwable {
        Element html = el("html");
        Element div = el("div");
        builder.push(html);
        builder.push(div);
        Element span = el("span");
        builder.insertOnStackAfter(html, span);
        assertSame(span, builder.getStack().get(1));
        assertSame(div, builder.getStack().get(2));
    }

    // covers replaceOnStack(): replaces element at same position
    @Test
    public void testReplaceOnStack_replacesElementAtSamePosition() throws Throwable {
        Element div = el("div");
        builder.push(div);
        Element span = el("span");
        builder.replaceOnStack(div, span);
        assertSame(span, builder.getStack().get(0));
    }

    // covers inScope(): true when target within scope, false when boundary hit first
    @Test
    public void testInScope_trueWhenTargetOnStack_falseWhenBoundaryHitFirst() throws Throwable {
        builder.push(el("html"));
        builder.push(el("p"));
        assertTrue(builder.inScope("p"));
        assertFalse(builder.inScope("div"));
    }

    // covers inListItemScope() and inButtonScope(): each respects its own extra boundary set
    @Test
    public void testInListItemScopeAndInButtonScope_respectSpecificBoundaries() throws Throwable {
        builder.push(el("html"));
        builder.push(el("li"));
        assertTrue(builder.inListItemScope("li"));
        builder.push(el("button"));
        assertFalse(builder.inButtonScope("li"));
    }

    // covers inTableScope(): true when target present, false when base boundary hit
    @Test
    public void testInTableScope_trueWhenTableOnStack_falseWhenHtmlBoundaryHit() throws Throwable {
        builder.push(el("table"));
        assertTrue(builder.inTableScope("table"));
        assertFalse(builder.inTableScope("div"));
    }

    // covers inSelectScope(): true for target itself, false when a disallowed element blocks search
    @Test
    public void testInSelectScope_trueForSelectItself_falseWhenNonAllowedElementEncountered() throws Throwable {
        builder.push(el("select"));
        assertTrue(builder.inSelectScope("select"));
        builder.push(el("div"));
        assertFalse(builder.inSelectScope("select"));
    }

    // covers setHeadElement()/getHeadElement()
    @Test
    public void testSetAndGetHeadElement() throws Throwable {
        Element head = el("head");
        builder.setHeadElement(head);
        assertSame(head, builder.getHeadElement());
    }

    // covers setFosterInserts()/isFosterInserts()
    @Test
    public void testSetAndIsFosterInserts() throws Throwable {
        builder.setFosterInserts(true);
        assertTrue(builder.isFosterInserts());
    }

    // covers newPendingTableCharacters()/getPendingTableCharacters()/setPendingTableCharacters()
    @Test
    public void testPendingTableCharacters_newGetSet() throws Throwable {
        builder.newPendingTableCharacters();
        assertTrue(builder.getPendingTableCharacters().isEmpty());
        List<String> list = new ArrayList<String>();
        list.add("abc");
        builder.setPendingTableCharacters(list);
        assertEquals(1, builder.getPendingTableCharacters().size());
        assertEquals("abc", builder.getPendingTableCharacters().get(0));
    }

    // covers generateImpliedEndTags(): pops listed tags, stops at non-listed current element
    @Test
    public void testGenerateImpliedEndTags_popsUntilNonListed() throws Throwable {
        builder.push(el("div"));
        builder.push(el("p"));
        builder.generateImpliedEndTags();
        assertEquals(1, builder.getStack().size());
        assertEquals("div", builder.getStack().get(0).nodeName());
    }

    // covers generateImpliedEndTags(excludeTag): stops immediately when current equals excluded tag
    @Test
    public void testGenerateImpliedEndTags_withExcludeTag_stopsBeforeExcluded() throws Throwable {
        builder.push(el("div"));
        builder.push(el("li"));
        builder.generateImpliedEndTags("li");
        assertEquals(2, builder.getStack().size());
    }

    // covers isSpecial(): true for a known special element, false otherwise
    @Test
    public void testIsSpecial_trueForKnownSpecialElement_falseForUnknown() throws Throwable {
        assertTrue(builder.isSpecial(el("div")));
        assertFalse(builder.isSpecial(el("span")));
    }

    // covers lastFormattingElement()/removeLastFormattingElement(): null when empty, correct element after push
    @Test
    public void testFormattingElementsStack_lastAndRemoveLast() throws Throwable {
        assertNull(builder.lastFormattingElement());
        Element b = el("b");
        builder.pushActiveFormattingElements(b);
        assertSame(b, builder.lastFormattingElement());
        assertSame(b, builder.removeLastFormattingElement());
        assertNull(builder.lastFormattingElement());
    }

    // covers getActiveFormattingElement(): finds by name; a marker blocks further search
    @Test
    public void testGetActiveFormattingElement_findsByNameAndRespectsMarker() throws Throwable {
        Element b = el("b");
        builder.pushActiveFormattingElements(b);
        assertSame(b, builder.getActiveFormattingElement("b"));
        builder.insertMarkerToFormattingElements();
        assertNull(builder.getActiveFormattingElement("b"));
    }

    // covers replaceActiveFormattingElement()/removeFromActiveFormattingElements()/isInActiveFormattingElements()
    @Test
    public void testReplaceAndRemoveActiveFormattingElement() throws Throwable {
        Element b = el("b");
        builder.pushActiveFormattingElements(b);
        Element newB = el("b");
        builder.replaceActiveFormattingElement(b, newB);
        assertSame(newB, builder.getActiveFormattingElement("b"));
        builder.removeFromActiveFormattingElements(newB);
        assertFalse(builder.isInActiveFormattingElements(newB));
    }

    // covers insertMarkerToFormattingElements()/clearFormattingElementsToLastMarker(): clears up to and including marker
    @Test
    public void testClearFormattingElementsToLastMarker_removesEntriesUntilMarker() throws Throwable {
        Element b = el("b");
        builder.pushActiveFormattingElements(b);
        builder.insertMarkerToFormattingElements();
        Element i = el("i");
        builder.pushActiveFormattingElements(i);
        builder.clearFormattingElementsToLastMarker();
        assertSame(b, builder.lastFormattingElement());
        assertFalse(builder.isInActiveFormattingElements(i));
    }

    // covers insertInFosterParent(): no table on stack -> appends to first stack element
    @Test
    public void testInsertInFosterParent_noTableOnStack_appendsToFirstStackElement() throws Throwable {
        Element root = el("html");
        builder.push(root);
        TextNode text = new TextNode("hi");
        builder.insertInFosterParent(text);
        assertTrue(root.childNodes().contains(text));
    }

    // covers insertInFosterParent(): table has a parent -> node inserted immediately before the table
    @Test
    public void testInsertInFosterParent_tableHasParent_insertsBeforeTable() throws Throwable {
        Element root = el("html");
        Element table = el("table");
        root.appendChild(table);
        builder.push(root);
        builder.push(table);
        TextNode text = new TextNode("x");
        builder.insertInFosterParent(text);
        assertEquals(0, root.childNodes().indexOf(text));
    }

    // covers insertInFosterParent(): table has no parent -> appends to element above table on stack
    @Test
    public void testInsertInFosterParent_tableWithoutParent_appendsToElementAboveTableOnStack() throws Throwable {
        Element root = el("html");
        Element table = el("table");
        builder.push(root);
        builder.push(table);
        TextNode text = new TextNode("y");
        builder.insertInFosterParent(text);
        assertTrue(root.childNodes().contains(text));
    }

    // covers toString(): includes current state name in output
    @Test
    public void testToString_containsStateName() throws Throwable {
        builder.push(el("html"));
        builder.transition(HtmlTreeBuilderState.InBody);
        String s = builder.toString();
        assertTrue(s.contains("InBody"));
    }
}
