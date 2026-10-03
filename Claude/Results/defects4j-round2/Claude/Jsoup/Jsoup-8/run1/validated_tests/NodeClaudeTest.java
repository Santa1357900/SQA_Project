package org.jsoup.nodes;

import java.util.List;

import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;

public class NodeClaudeTest {

    private Node getBody(Document doc) {
        Node htmlNode = doc.childNode(0);
        return htmlNode.childNode(1);
    }

    // attr(): key not present and not "abs:" prefixed -> returns ""
    @Test
    public void testAttr_missingKey_returnsEmptyString() throws Throwable {
        Document doc = Jsoup.parse("<p id='a'>Hello</p>");
        Node p = getBody(doc).childNode(0);
        assertEquals("", p.attr("class"));
    }

    // attr(): key present -> returns stored value
    @Test
    public void testAttr_presentKey_returnsValue() throws Throwable {
        Document doc = Jsoup.parse("<p id='a'>Hello</p>");
        Node p = getBody(doc).childNode(0);
        assertEquals("a", p.attr("id"));
    }

    // attr(): "abs:" prefix delegates to absUrl()
    @Test
    public void testAttr_absPrefix_delegatesToAbsUrl() throws Throwable {
        Document doc = Jsoup.parse("<a href='page.html'>link</a>");
        Node a = getBody(doc).childNode(0);
        a.setBaseUri("http://example.com/dir/");
        assertEquals("http://example.com/dir/page.html", a.attr("abs:href"));
    }

    // attr(): null key -> Validate.notNull throws
    @Test
    public void testAttr_nullKey_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node p = getBody(doc).childNode(0);
        try {
            p.attr((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // attr(key,value): sets attribute and returns this for chaining
    @Test
    public void testAttrSet_addsAttribute_andChaining() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node p = getBody(doc).childNode(0);
        Node ret = p.attr("data-x", "42");
        assertSame(p, ret);
        assertEquals("42", p.attr("data-x"));
    }

    // hasAttr(): true when present, false when absent
    @Test
    public void testHasAttr_trueForExisting_falseForMissing() throws Throwable {
        Document doc = Jsoup.parse("<p id='a'>Hello</p>");
        Node p = getBody(doc).childNode(0);
        assertTrue(p.hasAttr("id"));
        assertFalse(p.hasAttr("class"));
    }

    // hasAttr(): null key throws
    @Test
    public void testHasAttr_nullKey_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node p = getBody(doc).childNode(0);
        try {
            p.hasAttr(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // removeAttr(): removes the attribute
    @Test
    public void testRemoveAttr_removesAttribute() throws Throwable {
        Document doc = Jsoup.parse("<p id='a'>Hello</p>");
        Node p = getBody(doc).childNode(0);
        p.removeAttr("id");
        assertFalse(p.hasAttr("id"));
    }

    // baseUri(): get and set
    @Test
    public void testBaseUri_getSet() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node p = getBody(doc).childNode(0);
        p.setBaseUri("http://example.com/");
        assertEquals("http://example.com/", p.baseUri());
    }

    // setBaseUri(): null throws
    @Test
    public void testSetBaseUri_null_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node p = getBody(doc).childNode(0);
        try {
            p.setBaseUri(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // absUrl(): attribute missing -> ""
    @Test
    public void testAbsUrl_missingAttribute_returnsEmpty() throws Throwable {
        Document doc = Jsoup.parse("<a id='x'>t</a>");
        Node a = getBody(doc).childNode(0);
        a.setBaseUri("http://example.com/");
        assertEquals("", a.absUrl("href"));
    }

    // absUrl(): valid base + relative attr -> resolved absolute URL
    @Test
    public void testAbsUrl_relativeWithValidBase_returnsAbsolute() throws Throwable {
        Document doc = Jsoup.parse("<a href='page.html'>t</a>");
        Node a = getBody(doc).childNode(0);
        a.setBaseUri("http://example.com/dir/");
        assertEquals("http://example.com/dir/page.html", a.absUrl("href"));
    }

    // absUrl(): invalid base but attr itself absolute -> returns attr as URL
    @Test
    public void testAbsUrl_invalidBaseButAbsoluteAttr_returnsAttr() throws Throwable {
        Document doc = Jsoup.parse("<a href='http://example.com/abs.html'>t</a>");
        Node a = getBody(doc).childNode(0);
        a.setBaseUri("not a valid url");
        assertEquals("http://example.com/abs.html", a.absUrl("href"));
    }

    // absUrl(): invalid base and non-absolute attr -> ""
    @Test
    public void testAbsUrl_invalidBaseAndInvalidAttr_returnsEmpty() throws Throwable {
        Document doc = Jsoup.parse("<a href='page.html'>t</a>");
        Node a = getBody(doc).childNode(0);
        a.setBaseUri("not a valid url");
        assertEquals("", a.absUrl("href"));
    }

    // absUrl(): empty key -> Validate.notEmpty throws
    @Test
    public void testAbsUrl_emptyKey_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<a href='page.html'>t</a>");
        Node a = getBody(doc).childNode(0);
        try {
            a.absUrl("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // childNode(): valid index returns expected node
    @Test
    public void testChildNode_validIndex() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p><p>2</p>");
        Node body = getBody(doc);
        assertNotNull(body.childNode(0));
        assertNotNull(body.childNode(1));
    }

    // childNode(): out of range -> IndexOutOfBoundsException
    @Test
    public void testChildNode_outOfRange_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p>");
        Node body = getBody(doc);
        try {
            body.childNode(99);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // childNodes(): returned list is unmodifiable
    @Test
    public void testChildNodes_unmodifiable_throwsOnAdd() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        Node body = getBody(doc);
        Node div = body.childNode(0);
        List<Node> children = body.childNodes();
        try {
            children.add(div);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }

    // childNodesAsArray(): array matches childNodes list contents/order
    @Test
    public void testChildNodesAsArray_matchesList() throws Throwable {
        Document doc = Jsoup.parse("<div><span>A</span><span>B</span></div>");
        Node div = getBody(doc).childNode(0);
        Node[] arr = div.childNodesAsArray();
        assertEquals(2, arr.length);
        assertSame(div.childNode(0), arr[0]);
        assertSame(div.childNode(1), arr[1]);
    }

    // parent(): root has null parent; child has element parent
    @Test
    public void testParent_rootIsNull_childHasParentElement() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node body = getBody(doc);
        assertNull(doc.parent());
        assertSame(body, body.childNode(0).parent());
    }

    // ownerDocument(): Document instance returns itself
    @Test
    public void testOwnerDocument_onDocument_returnsSelf() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        assertSame(doc, doc.ownerDocument());
    }

    // ownerDocument(): nested node returns root document
    @Test
    public void testOwnerDocument_nestedNode_returnsRootDocument() throws Throwable {
        Document doc = Jsoup.parse("<div><span>A</span></div>");
        Node span = getBody(doc).childNode(0).childNode(0);
        assertSame(doc, span.ownerDocument());
    }

    // ownerDocument(): detached non-Document node (parent null) -> null
    @Test
    public void testOwnerDocument_detachedNonDocument_returnsNull() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        Node body = getBody(doc);
        Node div = body.childNode(0);
        div.remove();
        assertNull(div.ownerDocument());
    }

    // remove(): detaches node from its parent's child list
    @Test
    public void testRemove_detachesFromParent() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p><p>2</p>");
        Node body = getBody(doc);
        Node p1 = body.childNode(0);
        p1.remove();
        assertEquals(1, body.childNodes().size());
        assertNull(p1.parent());
    }

    // remove(): no parent -> Validate.notNull throws
    @Test
    public void testRemove_noParent_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p>");
        Node body = getBody(doc);
        Node p1 = body.childNode(0);
        p1.remove();
        try {
            p1.remove();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // replaceWith(): replaces node in parent with supplied node
    @Test
    public void testReplaceWith_replacesNodeInParent() throws Throwable {
        Document doc = Jsoup.parse("<p id='a'>A</p><p id='b'>B</p>");
        Node body = getBody(doc);
        Node p1 = body.childNode(0);
        Node p2 = body.childNode(1);
        p1.replaceWith(p2);
        assertEquals(1, body.childNodes().size());
        assertSame(p2, body.childNode(0));
        assertNull(p1.parent());
        assertSame(body, p2.parent());
    }

    // replaceWith(): null arg -> Validate.notNull throws
    @Test
    public void testReplaceWith_nullArg_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<p>A</p>");
        Node p = getBody(doc).childNode(0);
        try {
            p.replaceWith(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setParentNode(): had previous parent -> removed from old parent's children
    @Test
    public void testSetParentNode_hadPreviousParent_removesFromOld() throws Throwable {
        Document doc = Jsoup.parse("<div></div><p>x</p>");
        Node body = getBody(doc);
        Node div = body.childNode(0);
        Node p = body.childNode(1);
        div.setParentNode(p);
        assertSame(p, div.parent());
        assertEquals(1, body.childNodes().size());
    }

    // setParentNode(): no previous parent -> sets directly without removal
    @Test
    public void testSetParentNode_noPreviousParent_setsDirectly() throws Throwable {
        Document doc = Jsoup.parse("<p>x</p>");
        Node other = getBody(doc).childNode(0);
        doc.setParentNode(other);
        assertSame(other, doc.parent());
    }

    // replaceChild(): out not child of this -> Validate.isTrue throws
    @Test
    public void testReplaceChild_wrongParent_throwsException() throws Throwable {
        Document doc1 = Jsoup.parse("<div></div>");
        Node body1 = getBody(doc1);
        Node div = body1.childNode(0);
        Document doc2 = Jsoup.parse("<span></span>");
        Node body2 = getBody(doc2);
        Node span = body2.childNode(0);
        try {
            body1.replaceChild(span, div);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // removeChild(): out not child of this -> Validate.isTrue throws
    @Test
    public void testRemoveChild_wrongParent_throwsException() throws Throwable {
        Document doc1 = Jsoup.parse("<div></div>");
        Node body1 = getBody(doc1);
        Document doc2 = Jsoup.parse("<span></span>");
        Node span = getBody(doc2).childNode(0);
        try {
            body1.removeChild(span);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addChildren(Node...): reparents given node and appends at end
    @Test
    public void testAddChildren_varargs_reparentsAndAppends() throws Throwable {
        Document doc = Jsoup.parse("<div id='d'></div><span id='s'>X</span>");
        Node body = getBody(doc);
        Node div = body.childNode(0);
        Node span = body.childNode(1);
        div.addChildren(span);
        assertEquals(1, body.childNodes().size());
        assertEquals(1, div.childNodes().size());
        assertSame(span, div.childNode(0));
        assertSame(div, span.parent());
    }

    // addChildren(int, Node...): inserts at index, shifting existing children
    @Test
    public void testAddChildrenAtIndex_insertsAndReparents() throws Throwable {
        Document doc = Jsoup.parse("<div id='d'><em>Z</em></div><span id='s'>X</span>");
        Node body = getBody(doc);
        Node div = body.childNode(0);
        Node span = body.childNode(1);
        Node em = div.childNode(0);
        div.addChildren(0, span);
        assertEquals(2, div.childNodes().size());
        assertSame(span, div.childNode(0));
        assertSame(em, div.childNode(1));
        assertEquals(1, body.childNodes().size());
    }

    // addChildren(int, Node...): null element -> Validate.noNullElements throws
    @Test
    public void testAddChildrenAtIndex_nullElement_throwsException() throws Throwable {
        Document doc = Jsoup.parse("<div></div>");
        Node div = getBody(doc).childNode(0);
        try {
            div.addChildren(0, (Node) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // siblingNodes(): returns parent's full child node list (includes self)
    @Test
    public void testSiblingNodes_returnsParentChildNodes() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p><p>2</p><p>3</p>");
        Node body = getBody(doc);
        Node p2 = body.childNode(1);
        List<Node> sibs = p2.siblingNodes();
        assertEquals(3, sibs.size());
        assertSame(body.childNode(0), sibs.get(0));
    }

    // nextSibling(): middle returns next, last returns null, root returns null
    @Test
    public void testNextSibling_middleLastAndRootNull() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p><p>2</p>");
        Node body = getBody(doc);
        Node p1 = body.childNode(0);
        Node p2 = body.childNode(1);
        assertSame(p2, p1.nextSibling());
        assertNull(p2.nextSibling());
        assertNull(doc.nextSibling());
    }



    // previousSibling(): first sibling returns null, second returns first
    @Test
    public void testPreviousSibling_middleAndFirst() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p><p>2</p>");
        Node body = getBody(doc);
        Node p1 = body.childNode(0);
        Node p2 = body.childNode(1);
        assertNull(p1.previousSibling());
        assertSame(p1, p2.previousSibling());
    }

    // siblingIndex(): reflects position in parent's child list
    @Test
    public void testSiblingIndex_correctPositions() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p><p>2</p><p>3</p>");
        Node body = getBody(doc);
        assertEquals(Integer.valueOf(0), body.childNode(0).siblingIndex());
        assertEquals(Integer.valueOf(2), body.childNode(2).siblingIndex());
    }

    // outerHtml(): contains tag markup and content
    @Test
    public void testOuterHtml_containsExpectedMarkup() throws Throwable {
        Document doc = Jsoup.parse("<p id='a'>Hello</p>");
        Node p = getBody(doc).childNode(0);
        String html = p.outerHtml();
        assertTrue(html.contains("Hello"));
        assertTrue(html.contains("id=\"a\""));
    }

    // toString(): equals outerHtml()
    @Test
    public void testToString_equalsOuterHtml() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node p = getBody(doc).childNode(0);
        assertEquals(p.outerHtml(), p.toString());
    }

    // equals(): true for same reference, false for different instances
    @Test
    public void testEquals_sameReferenceTrue_differentReferenceFalse() throws Throwable {
        Document doc = Jsoup.parse("<p>1</p><p>1</p>");
        Node body = getBody(doc);
        Node p1 = body.childNode(0);
        Node p2 = body.childNode(1);
        assertTrue(p1.equals(p1));
        assertFalse(p1.equals(p2));
    }

    // hashCode(): consistent across repeated calls on same instance
    @Test
    public void testHashCode_consistentAcrossCalls() throws Throwable {
        Document doc = Jsoup.parse("<p>Hello</p>");
        Node p = getBody(doc).childNode(0);
        int h1 = p.hashCode();
        int h2 = p.hashCode();
        assertEquals(h1, h2);
    }
}
