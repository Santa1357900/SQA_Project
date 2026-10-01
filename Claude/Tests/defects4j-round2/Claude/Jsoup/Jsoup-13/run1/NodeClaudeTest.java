package org.jsoup.nodes;

import org.jsoup.Jsoup;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class NodeClaudeTest {

    // attr(key): existing attribute key returns its value
    @Test
    public void testAttr_existingKey_returnsValue() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>");
        Element a = doc.select("a").first();
        assertEquals("/foo", a.attr("href"));
    }

    // attr(key): missing key without "abs:" prefix returns empty string branch
    @Test
    public void testAttr_missingKeyNoAbsPrefix_returnsEmptyString() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>");
        Element a = doc.select("a").first();
        assertEquals("", a.attr("title"));
    }

    // attr(key): "abs:" prefix branch delegates to absUrl
    @Test
    public void testAttr_absPrefix_returnsAbsoluteUrl() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>", "http://example.com/");
        Element a = doc.select("a").first();
        assertEquals("http://example.com/foo", a.attr("abs:href"));
    }

    // attr(key): Validate.notNull throws for null key
    @Test
    public void testAttr_nullKey_throwsIllegalArgumentException() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>");
        Element a = doc.select("a").first();
        try {
            a.attr(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // attr(key,value) setter: sets value and returns this for chaining
    @Test
    public void testAttrSetter_addsAttributeAndReturnsThis() throws Throwable {
        Document doc = Jsoup.parse("<a>link</a>");
        Element a = doc.select("a").first();
        Node result = a.attr("data-x", "123");
        assertSame(a, result);
        assertEquals("123", a.attr("data-x"));
    }

    // hasAttr: true when present, false when absent
    @Test
    public void testHasAttr_trueAndFalseCases() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>");
        Element a = doc.select("a").first();
        assertTrue(a.hasAttr("href"));
        assertFalse(a.hasAttr("title"));
    }

    // removeAttr: attribute is removed and hasAttr becomes false
    @Test
    public void testRemoveAttr_removesExistingAttribute() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">link</a>");
        Element a = doc.select("a").first();
        a.removeAttr("href");
        assertFalse(a.hasAttr("href"));
    }

    // baseUri(): getter returns value configured during parse
    @Test
    public void testBaseUri_returnsConfiguredValue() throws Throwable {
        Document doc = Jsoup.parse("<a>x</a>", "http://example.com/dir/");
        assertEquals("http://example.com/dir/", doc.baseUri());
    }

    // setBaseUri: Validate.notNull throws for null argument
    @Test
    public void testSetBaseUri_nullThrowsIllegalArgumentException() throws Throwable {
        Document doc = Jsoup.parse("<a>x</a>");
        try {
            doc.setBaseUri(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // absUrl: attribute missing branch returns empty string
    @Test
    public void testAbsUrl_missingAttribute_returnsEmptyString() throws Throwable {
        Document doc = Jsoup.parse("<a>x</a>", "http://example.com/");
        Element a = doc.select("a").first();
        assertEquals("", a.absUrl("href"));
    }

    // absUrl: relative href resolved against valid base
    @Test
    public void testAbsUrl_relativeHrefWithValidBase_returnsAbsoluteUrl() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/foo\">x</a>", "http://example.com/dir/");
        Element a = doc.select("a").first();
        assertEquals("http://example.com/foo", a.absUrl("href"));
    }

    // absUrl: already-absolute href with valid base still resolves correctly
    @Test
    public void testAbsUrl_alreadyAbsoluteHref_returnsSameUrl() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"http://other.com/page\">x</a>", "http://example.com/dir/");
        Element a = doc.select("a").first();
        assertEquals("http://other.com/page", a.absUrl("href"));
    }

    // absUrl: malformed base falls back to using href itself if it is absolute
    @Test
    public void testAbsUrl_malformedBaseButAbsoluteHref_returnsHrefAsUrl() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"http://example.com/page\">x</a>", "not a valid url");
        Element a = doc.select("a").first();
        assertEquals("http://example.com/page", a.absUrl("href"));
    }

    // absUrl: malformed base and non-absolute href -> outer catch returns empty string
    @Test
    public void testAbsUrl_malformedBaseAndRelativeHref_returnsEmptyString() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/relative\">x</a>", "not a valid url");
        Element a = doc.select("a").first();
        assertEquals("", a.absUrl("href"));
    }

    // absUrl: query-string workaround branch prepends base path
    @Test
    public void testAbsUrl_queryStringWorkaround_prependsBasePath() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"?foo=1\">x</a>", "http://example.com/path/page.html");
        Element a = doc.select("a").first();
        assertEquals("http://example.com/path/page.html?foo=1", a.absUrl("href"));
    }

    // absUrl: Validate.notEmpty throws for empty key
    @Test
    public void testAbsUrl_emptyKey_throwsIllegalArgumentException() throws Throwable {
        Document doc = Jsoup.parse("<a href=\"/x\">x</a>");
        Element a = doc.select("a").first();
        try {
            a.absUrl("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // childNode(index): valid index returns expected node reference
    @Test
    public void testChildNode_validIndex_returnsExpectedNode() throws Throwable {
        Document doc = Jsoup.parse("<div><p>one</p><p>two</p></div>");
        Element div = doc.select("div").first();
        assertSame(div.childNodes().get(1), div.childNode(1));
    }

    // childNode(index): invalid index propagates IndexOutOfBoundsException
    @Test
    public void testChildNode_invalidIndex_throwsIndexOutOfBounds() throws Throwable {
        Document doc = Jsoup.parse("<div><p>one</p></div>");
        Element div = doc.select("div").first();
        try {
            div.childNode(5);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // childNodes(): returned list is unmodifiable
    @Test
    public void testChildNodes_isUnmodifiable() throws Throwable {
        Document doc = Jsoup.parse("<div><p>one</p></div>");
        Element div = doc.select("div").first();
        List<Node> children = div.childNodes();
        try {
            children.add(div);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }

    // parent(): root has null parent, child returns actual parent node
    @Test
    public void testParent_rootIsNullChildIsParent() throws Throwable {
        Document doc = Jsoup.parse("<div><p>one</p></div>");
        Element div = doc.select("div").first();
        Element p = doc.select("p").first();
        assertNull(doc.parent());
        assertSame(div, p.parent());
    }

    // ownerDocument(): self for Document, document for nested element, null for orphan
    @Test
    public void testOwnerDocument_forDocumentAndElementAndOrphan() throws Throwable {
        Document doc = Jsoup.parse("<div><p>one</p></div>");
        Element p = doc.select("p").first();
        Node orphan = p.clone();
        assertSame(doc, doc.ownerDocument());
        assertSame(doc, p.ownerDocument());
        assertNull(orphan.ownerDocument());
    }

    // remove(): detaches node from parent and nulls its parent reference
    @Test
    public void testRemove_removesFromParentAndNullsParentReference() throws Throwable {
        Document doc = Jsoup.parse("<div><p>one</p></div>");
        Element div = doc.select("div").first();
        Element p = doc.select("p").first();
        p.remove();
        assertNull(p.parent());
        assertEquals(0, div.childNodes().size());
    }

    // remove(): Validate.notNull(parentNode) throws when node has no parent
    @Test
    public void testRemove_noParent_throwsIllegalArgumentException() throws Throwable {
        Document doc = Jsoup.parse("<div><p>one</p></div>");
        try {
            doc.remove();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // before(String): inserts parsed html as preceding sibling
    @Test
    public void testBeforeHtml_insertsSiblingBeforeThisNode() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">one</p></div>");
        Element div = doc.select("div").first();
        Element p = doc.select("p#t").first();
        p.before("<span>pre</span>");
        assertEquals(2, div.childNodes().size());
        assertSame(p, div.childNode(1));
    }

    // before(Node): inserts given node as preceding sibling
    @Test
    public void testBeforeNode_insertsSiblingBeforeThisNode() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">one</p></div>");
        Document other = Jsoup.parse("<span>pre</span>");
        Element div = doc.select("div").first();
        Element p = doc.select("p#t").first();
        Node span = other.select("span").first();
        p.before(span);
        assertSame(span, div.childNode(0));
    }

    // after(String): inserts parsed html as following sibling
    @Test
    public void testAfterHtml_insertsSiblingAfterThisNode() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">one</p></div>");
        Element div = doc.select("div").first();
        Element p = doc.select("p#t").first();
        p.after("<span>post</span>");
        assertEquals(2, div.childNodes().size());
        assertSame(p, div.childNode(0));
    }

    // after(Node): inserts given node as following sibling
    @Test
    public void testAfterNode_insertsSiblingAfterThisNode() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">one</p></div>");
        Document other = Jsoup.parse("<span>post</span>");
        Element div = doc.select("div").first();
        Element p = doc.select("p#t").first();
        Node span = other.select("span").first();
        p.after(span);
        assertSame(span, div.childNode(1));
    }

    // wrap(): per contract, the newly created wrap element must stay attached to
    // the original grandparent; known bug orphans/self-references the wrap node.
    @Test
    public void testWrap_simpleDiv_bugRevealed() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"outer\"><p id=\"inner\">Text</p></div>");
        Element inner = doc.select("p#inner").first();
        Node originalParent = inner.parent();
        inner.wrap("<div class=\"w\"></div>");
        Node newWrap = inner.parent();
        assertNotNull(newWrap);
        assertSame(originalParent, newWrap.parent());
        assertEquals(1, originalParent.childNodes().size());
    }

    // wrap(): Validate.notEmpty throws for empty html
    @Test
    public void testWrap_emptyHtml_throwsIllegalArgumentException() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">one</p></div>");
        Element p = doc.select("p#t").first();
        try {
            p.wrap("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // replaceWith(node): replaces target node in parent's child list
    @Test
    public void testReplaceWith_replacesNodeInParentTree() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">one</p></div>");
        Document other = Jsoup.parse("<span>new</span>");
        Element div = doc.select("div").first();
        Element p = doc.select("p#t").first();
        Node span = other.select("span").first();
        p.replaceWith(span);
        assertSame(span, div.childNode(0));
        assertNull(p.parent());
    }

    // replaceWith(null): Validate.notNull throws
    @Test
    public void testReplaceWith_null_throwsIllegalArgumentException() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">one</p></div>");
        Element p = doc.select("p#t").first();
        try {
            p.replaceWith(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // siblingNodes(): equals parent's childNodes contents
    @Test
    public void testSiblingNodes_matchesParentChildNodes() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\">1</p><p id=\"b\">2</p></div>");
        Element div = doc.select("div").first();
        Element p = doc.select("p#a").first();
        assertEquals(div.childNodes(), p.siblingNodes());
    }

    // nextSibling(): last node returns null, middle node returns next sibling
    @Test
    public void testNextSibling_lastReturnsNullOthersReturnNext() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\">1</p><p id=\"b\">2</p></div>");
        Element a = doc.select("p#a").first();
        Element b = doc.select("p#b").first();
        assertSame(b, a.nextSibling());
        assertNull(b.nextSibling());
    }

    // previousSibling(): first node returns null, later node returns previous sibling
    @Test
    public void testPreviousSibling_firstReturnsNullOthersReturnPrevious() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\">1</p><p id=\"b\">2</p></div>");
        Element a = doc.select("p#a").first();
        Element b = doc.select("p#b").first();
        assertNull(a.previousSibling());
        assertSame(a, b.previousSibling());
    }

    // siblingIndex(): returns correct position within parent's child list
    @Test
    public void testSiblingIndex_returnsPositionInParent() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"a\">1</p><p id=\"b\">2</p></div>");
        Element b = doc.select("p#b").first();
        assertEquals(1, b.siblingIndex());
    }

    // outerHtml(): rendered markup contains the element's own tag
    @Test
    public void testOuterHtml_containsElementMarkup() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">hello</p></div>");
        Element p = doc.select("p#t").first();
        String html = p.outerHtml();
        assertTrue(html.contains("hello"));
    }

    // toString(): delegates to outerHtml() and returns identical content
    @Test
    public void testToString_equalsOuterHtml() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">hello</p></div>");
        Element p = doc.select("p#t").first();
        assertEquals(p.outerHtml(), p.toString());
    }

    // equals(): only reference equality returns true, distinct equal-content nodes are not equal
    @Test
    public void testEquals_identityBasedComparison() throws Throwable {
        Document doc = Jsoup.parse("<div><p>same</p><p>same</p></div>");
        Element first = doc.select("p").get(0);
        Element second = doc.select("p").get(1);
        assertTrue(first.equals(first));
        assertFalse(first.equals(second));
    }

    // hashCode(): stable across repeated calls on the same instance
    @Test
    public void testHashCode_stableForSameInstance() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">1</p></div>");
        Element p = doc.select("p#t").first();
        int h1 = p.hashCode();
        int h2 = p.hashCode();
        assertEquals(h1, h2);
    }

    // clone(): produces an independent orphan copy; edits do not affect the original
    @Test
    public void testClone_isIndependentOrphanCopy() throws Throwable {
        Document doc = Jsoup.parse("<div><p id=\"t\">1</p></div>");
        Element p = doc.select("p#t").first();
        p.attr("data-k", "orig");
        Node clone = p.clone();
        clone.attr("data-k", "changed");
        assertEquals("orig", p.attr("data-k"));
        assertNull(clone.parent());
    }
}
