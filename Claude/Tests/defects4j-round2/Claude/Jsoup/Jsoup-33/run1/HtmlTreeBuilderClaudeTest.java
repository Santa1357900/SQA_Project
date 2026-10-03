package org.jsoup.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class HtmlTreeBuilderClaudeTest {

    private HtmlTreeBuilder newInitializedBuilder(String html) throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        b.parse(html, "http://example.com/", ParseErrorList.noTracking());
        return b;
    }

    // covers parse(): basic document build produces non-empty child node list
    @Test
    public void testParse_basicHtml_producesNonEmptyDocument() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Document doc = b.parse("<html><head><title>T</title></head><body>hello</body></html>",
                "http://example.com/", ParseErrorList.noTracking());
        assertFalse(doc.childNodes().isEmpty());
    }

    // covers getBaseUri(): returns the baseUri passed to parse() when no <base> tag is present
    @Test
    public void testGetBaseUri_returnsGivenBaseUriWhenNoBaseTag() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        b.parse("<html><body></body></html>", "http://example.com/", ParseErrorList.noTracking());
        assertEquals("http://example.com/", b.getBaseUri());
    }

    // covers getDocument(): returns the same Document instance produced by parse()
    @Test
    public void testGetDocument_returnsParsedDocument() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Document doc = b.parse("<html><body></body></html>", "http://example.com/", ParseErrorList.noTracking());
        assertSame(doc, b.getDocument());
    }

    // covers parseFragment() with null context: falls back to full-document parsing, returns non-empty doc children
    @Test
    public void testParseFragment_nullContext_returnsNonEmptyNodeList() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        List<Node> nodes = b.parseFragment("<p>Hello</p>", null, "http://example.com/", ParseErrorList.noTracking());
        assertFalse(nodes.isEmpty());
    }

    // covers parseFragment() with a context element: result contains the parsed child under the synthetic root
    @Test
    public void testParseFragment_withContext_returnsContextChildNodes() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("body"), "http://example.com/");
        List<Node> nodes = b.parseFragment("<p>Hi</p>", context, "http://example.com/", ParseErrorList.noTracking());
        assertFalse(nodes.isEmpty());
        assertEquals("p", ((Element) nodes.get(0)).tagName());
    }

    // covers isFragmentParsing(): false before any parse, true once fragment parsing has started
    @Test
    public void testIsFragmentParsing_falseByDefault_trueDuringFragmentParse() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        assertFalse(b.isFragmentParsing());
        b.parseFragment("<div></div>", null, "http://example.com/", ParseErrorList.noTracking());
        assertTrue(b.isFragmentParsing());
    }

    // covers resetInsertionMode(): context "select" transitions to InSelect
    @Test
    public void testParseFragment_contextSelect_transitionsToInSelect() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("select"), "http://example.com/");
        b.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking());
        assertEquals(HtmlTreeBuilderState.InSelect, b.state());
    }

    // covers resetInsertionMode(): context "table" transitions to InTable
    @Test
    public void testParseFragment_contextTable_transitionsToInTable() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("table"), "http://example.com/");
        b.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking());
        assertEquals(HtmlTreeBuilderState.InTable, b.state());
    }

    // covers resetInsertionMode(): context "tr" transitions to InRow
    @Test
    public void testParseFragment_contextTr_transitionsToInRow() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("tr"), "http://example.com/");
        b.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking());
        assertEquals(HtmlTreeBuilderState.InRow, b.state());
    }

    // covers resetInsertionMode(): context "thead" transitions to InTableBody
    @Test
    public void testParseFragment_contextThead_transitionsToInTableBody() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("thead"), "http://example.com/");
        b.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking());
        assertEquals(HtmlTreeBuilderState.InTableBody, b.state());
    }

    // covers resetInsertionMode(): context "frameset" transitions to InFrameset
    @Test
    public void testParseFragment_contextFrameset_transitionsToInFrameset() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("frameset"), "http://example.com/");
        b.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking());
        assertEquals(HtmlTreeBuilderState.InFrameset, b.state());
    }

    // covers resetInsertionMode(): context "body" transitions to InBody
    @Test
    public void testParseFragment_contextBody_transitionsToInBody() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("body"), "http://example.com/");
        b.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking());
        assertEquals(HtmlTreeBuilderState.InBody, b.state());
    }

    // BUG CHECK: per HTML5 spec, "td"/"th" only switch to InCell when 'last' is false. In fragment
    // parsing the context element is always the last (only) node examined, so 'last' is true and the
    // insertion mode must fall through to InBody, not InCell.
    @Test
    public void testParseFragment_contextTd_bugCheck_transitionsToInBody() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element context = new Element(Tag.valueOf("td"), "http://example.com/");
        b.parseFragment("", context, "http://example.com/", ParseErrorList.noTracking());
        assertEquals(HtmlTreeBuilderState.InBody, b.state());
    }

    // covers transition()/state(): sets and returns the current insertion mode
    @Test
    public void testTransitionAndState_setsAndReturnsCurrentState() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        b.transition(HtmlTreeBuilderState.InBody);
        assertEquals(HtmlTreeBuilderState.InBody, b.state());
    }

    // covers markInsertionMode()/originalState(): saves current state as the original marked state
    @Test
    public void testMarkInsertionMode_savesCurrentStateAsOriginal() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        b.transition(HtmlTreeBuilderState.InTable);
        b.markInsertionMode();
        b.transition(HtmlTreeBuilderState.InBody);
        assertEquals(HtmlTreeBuilderState.InTable, b.originalState());
    }

    // covers framesetOk()/framesetOk(boolean): default true, settable to false
    @Test
    public void testFramesetOk_defaultsTrueAndCanBeSetFalse() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        assertTrue(b.framesetOk());
        b.framesetOk(false);
        assertFalse(b.framesetOk());
    }

    // covers setHeadElement()/getHeadElement(): default null, settable
    @Test
    public void testHeadElement_defaultNullAndSettable() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        assertNull(b.getHeadElement());
        Element head = new Element(Tag.valueOf("head"), "http://example.com/");
        b.setHeadElement(head);
        assertEquals(head, b.getHeadElement());
    }

    // covers getFormElement(): default null
    @Test
    public void testGetFormElement_defaultNull() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        assertNull(b.getFormElement());
    }

    // covers insert(String): creates element, appends to tree and pushes onto stack
    @Test
    public void testInsertString_createsElementAndAddsToStackAndTree() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element el = b.insert("span");
        assertEquals("span", el.tagName());
        assertTrue(b.onStack(el));
    }

    // covers push()/pop(): adds to and removes the last element from the open elements stack
    @Test
    public void testPushAndPop_addsAndRemovesFromStack() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        int sizeBefore = b.getStack().size();
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        b.push(div);
        assertEquals(sizeBefore + 1, b.getStack().size());
        Element popped = b.pop();
        assertEquals(div, popped);
        assertEquals(sizeBefore, b.getStack().size());
    }

    // covers getFromStack(): returns null when the element name is not present
    @Test
    public void testGetFromStack_elementNotPresent_returnsNull() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        assertNull(b.getFromStack("nonexistenttag123"));
    }

    // covers removeFromStack(): removes a present element and returns true
    @Test
    public void testRemoveFromStack_elementPresent_removesAndReturnsTrue() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        b.push(div);
        boolean removed = b.removeFromStack(div);
        assertTrue(removed);
        assertFalse(b.onStack(div));
    }

    // covers popStackToClose(String): pops elements above and including the named element
    @Test
    public void testPopStackToClose_popsUntilNamedElement() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        int base = b.getStack().size();
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        Element span = new Element(Tag.valueOf("span"), "http://example.com/");
        b.push(div);
        b.push(span);
        b.popStackToClose("div");
        assertEquals(base, b.getStack().size());
        assertFalse(b.onStack(div));
        assertFalse(b.onStack(span));
    }

    // covers popStackToBefore(String): pops elements above the named element but keeps it
    @Test
    public void testPopStackToBefore_stopsBeforeNamedElement_keepsIt() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        int base = b.getStack().size();
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        Element span = new Element(Tag.valueOf("span"), "http://example.com/");
        b.push(div);
        b.push(span);
        b.popStackToBefore("div");
        assertEquals(base + 1, b.getStack().size());
        assertTrue(b.onStack(div));
        assertFalse(b.onStack(span));
    }

    // covers clearStackToTableContext(): removes elements above the nearest table/html
    @Test
    public void testClearStackToTableContext_removesElementsAboveTable() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element table = new Element(Tag.valueOf("table"), "http://example.com/");
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        b.push(table);
        b.push(div);
        b.clearStackToTableContext();
        assertTrue(b.onStack(table));
        assertFalse(b.onStack(div));
    }

    // covers insertOnStackAfter(): inserts the given element immediately after another on the stack
    @Test
    public void testInsertOnStackAfter_insertsElementAfterGiven() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        b.push(div);
        Element span = new Element(Tag.valueOf("span"), "http://example.com/");
        b.insertOnStackAfter(div, span);
        int idx = b.getStack().indexOf(div);
        assertEquals(span, b.getStack().get(idx + 1));
    }

    // covers replaceOnStack(): replaces an element in the stack with another
    @Test
    public void testReplaceOnStack_replacesElementInStack() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        b.push(div);
        Element span = new Element(Tag.valueOf("span"), "http://example.com/");
        b.replaceOnStack(div, span);
        assertFalse(b.onStack(div));
        assertTrue(b.onStack(span));
    }

    // covers inScope(String): true when target found before a base-type ancestor, false when a base type is hit first
    @Test
    public void testInScope_coversBothBranches() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        assertTrue(b.inScope("body"));
        assertFalse(b.inScope("nonexistenttagxyz"));
    }

    // covers inTableScope(String): false when target "table" is not on the stack (html base type hit first)
    @Test
    public void testInTableScope_falseWhenTableNotOnStack() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        assertFalse(b.inTableScope("table"));
    }

    // covers inSelectScope(String): false immediately when current element is not target/optgroup/option
    @Test
    public void testInSelectScope_falseWhenCurrentNotOptionOrOptgroup() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        assertFalse(b.inSelectScope("select"));
    }

    // covers inListItemScope(String): true when target found on top of stack
    @Test
    public void testInListItemScope_targetPresentOnStack_true() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element li = new Element(Tag.valueOf("li"), "http://example.com/");
        b.push(li);
        assertTrue(b.inListItemScope("li"));
    }

    // covers generateImpliedEndTags(): pops a current element that is in the implied-end-tag list
    @Test
    public void testGenerateImpliedEndTags_popsImpliedElement() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element p = new Element(Tag.valueOf("p"), "http://example.com/");
        b.push(p);
        b.generateImpliedEndTags();
        assertFalse(b.onStack(p));
    }

    // covers generateImpliedEndTags(String): excludeTag matching current element prevents popping
    @Test
    public void testGenerateImpliedEndTags_withExcludeTagMatchingCurrent_doesNotPop() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element p = new Element(Tag.valueOf("p"), "http://example.com/");
        b.push(p);
        b.generateImpliedEndTags("p");
        assertTrue(b.onStack(p));
    }

    // covers isSpecial(Element): true for a tag present in the special elements list (div)
    @Test
    public void testIsSpecial_divIsSpecial_true() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        assertTrue(b.isSpecial(div));
    }

    // covers isSpecial(Element): false for a tag not present in the special elements list (span)
    @Test
    public void testIsSpecial_spanIsNotSpecial_false() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element span = new Element(Tag.valueOf("span"), "http://example.com/");
        assertFalse(b.isSpecial(span));
    }

    // covers pushActiveFormattingElements()/getActiveFormattingElement(): pushed element is retrievable by name
    @Test
    public void testPushActiveFormattingElements_andGetActiveFormattingElement() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element em = new Element(Tag.valueOf("em"), "http://example.com/");
        b.pushActiveFormattingElements(em);
        assertEquals(em, b.getActiveFormattingElement("em"));
    }

    // covers isInActiveFormattingElements(): true after pushing the element
    @Test
    public void testIsInActiveFormattingElements_trueAfterPush() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element em = new Element(Tag.valueOf("em"), "http://example.com/");
        b.pushActiveFormattingElements(em);
        assertTrue(b.isInActiveFormattingElements(em));
    }

    // covers removeFromActiveFormattingElements(): removes a previously pushed element
    @Test
    public void testRemoveFromActiveFormattingElements_removesElement() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element em = new Element(Tag.valueOf("em"), "http://example.com/");
        b.pushActiveFormattingElements(em);
        b.removeFromActiveFormattingElements(em);
        assertFalse(b.isInActiveFormattingElements(em));
    }

    // covers insertMarkerToFormattingElements()/clearFormattingElementsToLastMarker(): clears up to and including marker
    @Test
    public void testInsertMarkerAndClearFormattingElementsToLastMarker() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element em = new Element(Tag.valueOf("em"), "http://example.com/");
        b.insertMarkerToFormattingElements();
        b.pushActiveFormattingElements(em);
        b.clearFormattingElementsToLastMarker();
        assertFalse(b.isInActiveFormattingElements(em));
    }

    // covers replaceActiveFormattingElement(): replaces an entry in the active formatting elements list
    @Test
    public void testReplaceActiveFormattingElement_replacesEntry() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element em = new Element(Tag.valueOf("em"), "http://example.com/");
        Element strong = new Element(Tag.valueOf("strong"), "http://example.com/");
        b.pushActiveFormattingElements(em);
        b.replaceActiveFormattingElement(em, strong);
        assertFalse(b.isInActiveFormattingElements(em));
        assertTrue(b.isInActiveFormattingElements(strong));
    }

    // covers pushActiveFormattingElements(): Noah's Ark clause removes earliest duplicate after three matches
    @Test
    public void testPushActiveFormattingElements_noahsArkClause() throws Throwable {
        HtmlTreeBuilder b = new HtmlTreeBuilder();
        Element e1 = new Element(Tag.valueOf("b"), "http://example.com/");
        Element e2 = new Element(Tag.valueOf("b"), "http://example.com/");
        Element e3 = new Element(Tag.valueOf("b"), "http://example.com/");
        Element e4 = new Element(Tag.valueOf("b"), "http://example.com/");
        b.pushActiveFormattingElements(e1);
        b.pushActiveFormattingElements(e2);
        b.pushActiveFormattingElements(e3);
        b.pushActiveFormattingElements(e4);
        assertFalse(b.isInActiveFormattingElements(e1));
        assertTrue(b.isInActiveFormattingElements(e4));
    }

    // covers reconstructFormattingElements(): no-op when the last formatting entry is already on the stack
    @Test
    public void testReconstructFormattingElements_lastEntryAlreadyOnStack_noOp() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element em = new Element(Tag.valueOf("em"), "http://example.com/");
        b.push(em);
        b.pushActiveFormattingElements(em);
        int sizeBefore = b.getStack().size();
        b.reconstructFormattingElements();
        assertEquals(sizeBefore, b.getStack().size());
    }

    // covers insertInFosterParent() via insert(): no table on stack, appends to first stack element
    @Test
    public void testInsert_fosterInsertsNoTable_appendsToFirstStackElement() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        b.setFosterInserts(true);
        Element inserted = b.insert("span");
        Element root = b.getStack().get(0);
        assertTrue(root.childNodes().contains(inserted));
    }

    // covers insertInFosterParent() via insert(): table on stack with no parent, appends above the table
    @Test
    public void testInsert_fosterInsertsWithTableNoParent_appendsAboveTable() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element body = b.getStack().peekLast();
        Element table = new Element(Tag.valueOf("table"), "http://example.com/");
        b.push(table);
        b.setFosterInserts(true);
        Element inserted = b.insert("span");
        assertTrue(body.childNodes().contains(inserted));
    }

    // covers insertInFosterParent() via insert(): table on stack with a parent, inserts before the table
    @Test
    public void testInsert_fosterInsertsWithTableHavingParent_insertsBeforeTable() throws Throwable {
        HtmlTreeBuilder b = newInitializedBuilder("<html><body></body></html>");
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        Element table = new Element(Tag.valueOf("table"), "http://example.com/");
        div.appendChild(table);
        b.push(table);
        b.setFosterInserts(true);
        Element inserted = b.insert("span");
        assertEquals(0, div.childNodes().indexOf(inserted));
        assertEquals(1, div.childNodes().indexOf(table));
    }
}
