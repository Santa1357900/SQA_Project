package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.select.NodeVisitor;

import java.util.List;

public class NodeClaudeTest {

    // attr(): missing key returns empty string (else branch)
    @Test
    public void testAttr_missingKey_returnsEmptyString() throws Throwable {
        Element a = Jsoup.parse("<a></a>").select("a").first();
        assertEquals("", a.attr("href"));
    }

    // attr(): existing key returns its value
    @Test
    public void testAttr_existingKey_returnsValue() throws Throwable {
        Element a = Jsoup.parse("<a href=\"http://x.com\"></a>").select("a").first();
        assertEquals("http://x.com", a.attr("href"));
    }

    // attr(): "abs:" prefix branch resolves via absUrl
    @Test
    public void testAttr_absPrefix_resolvesAbsoluteUrl() throws Throwable {
        Element a = Jsoup.parse("<a href=\"http://example.com/page\"></a>").select("a").first();
        assertEquals("http://example.com/page", a.attr("abs:href"));
    }

    // attr(key,value): sets attribute and returns this for chaining
    @Test
    public void testAttrSet_addsAttribute() throws Throwable {
        Element a = Jsoup.parse("<a></a>").select("a").first();
        Node ret = a.attr("data-x", "123");
        assertSame(a, ret);
        assertEquals("123", a.attr("data-x"));
    }

    // hasAttr(): plain existing/missing key branches
    @Test
    public void testHasAttr_existingAndMissing() throws Throwable {
        Element a = Jsoup.parse("<a href=\"http://x.com\"></a>").select("a").first();
        assertTrue(a.hasAttr("href"));
        assertFalse(a.hasAttr("title"));
    }

    // hasAttr(): "abs:" prefix, key present but unresolvable -> false
    @Test
    public void testHasAttr_absPrefixUnresolvable_false() throws Throwable {
        Element a = Jsoup.parse("<a href=\"/relative\"></a>").select("a").first();
        assertFalse(a.hasAttr("abs:href"));
    }

    // hasAttr(): "abs:" prefix, key present and resolvable -> true
    @Test
    public void testHasAttr_absPrefixResolvable_true() throws Throwable {
        Element a = Jsoup.parse("<a href=\"http://example.com/page\"></a>").select("a").first();
        assertTrue(a.hasAttr("abs:href"));
    }

    // removeAttr(): removes attribute
    @Test
    public void testRemoveAttr_removesAttribute() throws Throwable {
        Element a = Jsoup.parse("<a href=\"http://x.com\"></a>").select("a").first();
        a.removeAttr("href");
        assertFalse(a.hasAttr("href"));
        assertEquals("", a.attr("href"));
    }

    // setBaseUri(): traverses and updates baseUri for all descendants
    @Test
    public void testSetBaseUri_updatesDescendants() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"su\"><p id=\"sc\">Hi</p></div>");
        Element div = doc.select("#su").first();
        Element p = doc.select("#sc").first();
        div.setBaseUri("http://new-base.example/");
        assertEquals("http://new-base.example/", div.baseUri());
        assertEquals("http://new-base.example/", p.baseUri());
    }

    // absUrl(): attribute missing -> empty string
    @Test
    public void testAbsUrl_missingAttribute_returnsEmpty() throws Throwable {
        Element a = Jsoup.parse("<a></a>").select("a").first();
        assertEquals("", a.absUrl("href"));
    }

    // absUrl(): relative value, no usable base -> empty string
    @Test
    public void testAbsUrl_relativeNoBase_returnsEmpty() throws Throwable {
        Element a = Jsoup.parse("<a href=\"/path\"></a>").select("a").first();
        assertEquals("", a.absUrl("href"));
    }

    // absUrl(): already absolute value is returned as-is
    @Test
    public void testAbsUrl_absoluteUrl_returnsSame() throws Throwable {
        Element a = Jsoup.parse("<a href=\"http://example.com/page\"></a>").select("a").first();
        assertEquals("http://example.com/page", a.absUrl("href"));
    }

    // absUrl(): empty attributeKey -> Validate.notEmpty throws
    @Test
    public void testAbsUrl_emptyKey_throws() throws Throwable {
        Element a = Jsoup.parse("<a></a>").select("a").first();
        try {
            a.absUrl("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // childNode(): valid index returns correct node
    @Test
    public void testChildNode_validIndex() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"d\"><p id=\"only\"></p></div>");
        Element div = doc.select("#d").first();
        Element p = doc.select("#only").first();
        assertSame(p, div.childNode(0));
    }

    // childNode(): out of bounds index throws IndexOutOfBoundsException
    @Test
    public void testChildNode_outOfBounds_throws() throws Throwable {
        Element div = Jsoup.parse("<div></div>").select("div").first();
        try {
            div.childNode(0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // childNodes(): returned list is unmodifiable
    @Test
    public void testChildNodes_unmodifiable_throws() throws Throwable {
        Element div = Jsoup.parse("<div><p></p></div>").select("div").first();
        List<Node> kids = div.childNodes();
        try {
            kids.add(null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // childNodesCopy(): deep copy independent from original
    @Test
    public void testChildNodesCopy_independentDeepCopy() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"cd\"><p id=\"cp\"></p></div>");
        Element div = doc.select("#cd").first();
        List<Node> copy = div.childNodesCopy();
        Node copyChild = copy.get(0);
        copyChild.attr("data-mark", "yes");
        Element originalChild = doc.select("#cp").first();
        assertEquals("", originalChild.attr("data-mark"));
        assertEquals("yes", copyChild.attr("data-mark"));
    }

    // childNodeSize(): counts children correctly
    @Test
    public void testChildNodeSize() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"sz\"><p></p><p></p></div>");
        Element div = doc.select("#sz").first();
        assertEquals(2, div.childNodeSize());
    }

    // parent()/parentNode(): root has null parent, child has correct parent
    @Test
    public void testParent_rootNullAndChildCorrect() throws Throwable {
        Document doc = Jsoup.parse("<html></html>");
        assertNull(doc.parent());
        Document doc2 = Jsoup.parseBodyFragment("<div id=\"pp\"><p id=\"cc\"></p></div>");
        Element parentDiv = doc2.select("#pp").first();
        Element child = doc2.select("#cc").first();
        assertSame(parentDiv, child.parent());
        assertSame(parentDiv, child.parentNode());
    }

    // ownerDocument(): nested node returns document; document returns itself
    @Test
    public void testOwnerDocument_nestedAndSelf() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"od\"><p id=\"oc\"></p></div>");
        Element child = doc.select("#oc").first();
        assertSame(doc, child.ownerDocument());
        assertSame(doc, doc.ownerDocument());
    }

    // remove(): detaches node from its parent
    @Test
    public void testRemove_detaches() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"rd\"><p id=\"rc\"></p></div>");
        Element parentDiv = doc.select("#rd").first();
        Element child = doc.select("#rc").first();
        child.remove();
        assertEquals(0, parentDiv.childNodeSize());
        assertNull(child.parent());
    }

    // remove(): no parent -> Validate.notNull throws
    @Test
    public void testRemove_noParent_throws() throws Throwable {
        Document doc = Jsoup.parse("<html></html>");
        try {
            doc.remove();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // before(String): inserts parsed html as preceding sibling
    @Test
    public void testBeforeHtml_insertsSibling() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"bh\"><p id=\"bt\"></p></div>");
        Element parentDiv = doc.select("#bh").first();
        Element target = doc.select("#bt").first();
        target.before("<span id=\"bs\"></span>");
        assertEquals(2, parentDiv.childNodeSize());
        assertEquals("span", parentDiv.childNode(0).nodeName());
        assertSame(target, parentDiv.childNode(1));
    }

    // before(Node): null node -> Validate.notNull throws
    @Test
    public void testBeforeNode_null_throws() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<p id=\"t\"></p>");
        Element t = doc.select("#t").first();
        try {
            t.before((Node) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // after(String): inserts parsed html as following sibling
    @Test
    public void testAfterHtml_insertsSibling() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"ah\"><p id=\"at\"></p></div>");
        Element parentDiv = doc.select("#ah").first();
        Element target = doc.select("#at").first();
        target.after("<span id=\"as\"></span>");
        assertEquals(2, parentDiv.childNodeSize());
        assertSame(target, parentDiv.childNode(0));
        assertEquals("span", parentDiv.childNode(1).nodeName());
    }

    // after(Node): no parent -> Validate.notNull(parentNode) throws
    @Test
    public void testAfterNode_noParent_throws() throws Throwable {
        Document doc = Jsoup.parse("<html></html>");
        Element other = Jsoup.parse("<a></a>").select("a").first();
        try {
            doc.after(other);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // wrap(): KEY bug test - wrapping must keep wrap element attached to grandparent
    @Test
    public void testWrap_bug_preservesAttachment() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"gp\"><p id=\"target\">Hi</p></div>");
        Element grandParent = doc.select("#gp").first();
        Element target = doc.select("#target").first();
        Node result = target.wrap("<span></span>");
        assertSame(target, result);
        Node newParent = target.parent();
        assertEquals("span", newParent.nodeName());
        assertSame(grandParent, newParent.parent());
        assertEquals(1, grandParent.childNodeSize());
    }

    // wrap(): unbalanced html - remainder should become child of wrap, wrap stays attached
    @Test
    public void testWrap_unbalancedHtml_remainderBecomesChildOfWrap() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"gp2\"><p id=\"target2\">Hi</p></div>");
        Element grandParent = doc.select("#gp2").first();
        Element target = doc.select("#target2").first();
        target.wrap("<span></span><b></b>");
        Node newParent = target.parent();
        assertEquals("span", newParent.nodeName());
        assertSame(grandParent, newParent.parent());
        assertEquals(2, newParent.childNodeSize());
        assertEquals("b", newParent.childNode(1).nodeName());
    }

    // wrap(): html that parses to a non-Element node is a no-op returning null
    @Test
    public void testWrap_nonElement_returnsNullNoop() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"gp3\"><p id=\"target3\">Hi</p></div>");
        Element grandParent = doc.select("#gp3").first();
        Element target = doc.select("#target3").first();
        Node result = target.wrap("just plain text");
        assertNull(result);
        assertSame(grandParent, target.parent());
        assertEquals(1, grandParent.childNodeSize());
    }

    // wrap(): empty html -> Validate.notEmpty throws
    @Test
    public void testWrap_emptyHtml_throws() throws Throwable {
        Element target = Jsoup.parseBodyFragment("<p id=\"t4\"></p>").select("#t4").first();
        try {
            target.wrap("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // unwrap(): removes node, promotes children into parent, returns first child
    @Test
    public void testUnwrap_promotesChildren() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"outer\"><span id=\"mid\"><p id=\"inner\"></p></span></div>");
        Element outer = doc.select("#outer").first();
        Element mid = doc.select("#mid").first();
        Element inner = doc.select("#inner").first();
        Node returned = mid.unwrap();
        assertSame(inner, returned);
        assertSame(outer, inner.parent());
        assertEquals(1, outer.childNodeSize());
    }

    // unwrap(): no children -> returns null
    @Test
    public void testUnwrap_noChildren_returnsNull() throws Throwable {
        Element span = Jsoup.parseBodyFragment("<div><span id=\"empty\"></span></div>").select("#empty").first();
        Node result = span.unwrap();
        assertNull(result);
    }

    // unwrap(): no parent -> Validate.notNull throws
    @Test
    public void testUnwrap_noParent_throws() throws Throwable {
        Document doc = Jsoup.parse("<html></html>");
        try {
            doc.unwrap();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // replaceWith(): replaces node in the DOM tree
    @Test
    public void testReplaceWith_replacesNode() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"gp5\"><p id=\"t5\"></p></div>");
        Element grandParent = doc.select("#gp5").first();
        Element target = doc.select("#t5").first();
        Element replacement = Jsoup.parse("<em id=\"r\"></em>").select("#r").first();
        target.replaceWith(replacement);
        assertSame(replacement, grandParent.childNode(0));
        assertNull(target.parent());
    }

    // replaceWith(): null -> Validate.notNull throws
    @Test
    public void testReplaceWith_null_throws() throws Throwable {
        Element target = Jsoup.parseBodyFragment("<p id=\"t6\"></p>").select("#t6").first();
        try {
            target.replaceWith(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // siblingIndex(), nextSibling(), previousSibling() across multiple siblings
    @Test
    public void testSiblingIndexAndNavigation() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"root2\"><p id=\"p1\"></p><p id=\"p2\"></p><p id=\"p3\"></p></div>");
        Element p1 = doc.select("#p1").first();
        Element p2 = doc.select("#p2").first();
        Element p3 = doc.select("#p3").first();
        assertEquals(0, p1.siblingIndex());
        assertEquals(2, p3.siblingIndex());
        assertSame(p2, p1.nextSibling());
        assertNull(p3.nextSibling());
        assertSame(p1, p2.previousSibling());
        assertNull(p1.previousSibling());
    }

    // siblingNodes(): excludes self, includes others
    @Test
    public void testSiblingNodes_excludesSelf() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"root3\"><p id=\"s1\"></p><p id=\"s2\"></p><p id=\"s3\"></p></div>");
        Element s2 = doc.select("#s2").first();
        List<Node> sibs = s2.siblingNodes();
        assertEquals(2, sibs.size());
        assertFalse(sibs.contains(s2));
    }

    // traverse(): visits every node (head/tail) depth-first
    @Test
    public void testTraverse_visitsAllNodes() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<div id=\"root\"><p id=\"inner\">Hi</p></div>");
        Element root = doc.select("#root").first();
        final int[] headCount = new int[1];
        final int[] tailCount = new int[1];
        root.traverse(new NodeVisitor() {
            public void head(Node node, int depth) {
                headCount[0]++;
            }
            public void tail(Node node, int depth) {
                tailCount[0]++;
            }
        });
        assertEquals(3, headCount[0]);
        assertEquals(3, tailCount[0]);
    }

    // outerHtml()/toString(): consistent and contains expected text
    @Test
    public void testOuterHtmlAndToString() throws Throwable {
        Element p = Jsoup.parse("<p id=\"x\">Hello</p>").select("#x").first();
        String outer = p.outerHtml();
        assertEquals(outer, p.toString());
        assertTrue(outer.contains("Hello"));
    }

    // equals(): content-based equality, independent of tree position
    @Test
    public void testEquals_samePositionIndependent_true() throws Throwable {
        Element a = Jsoup.parse("<span class=\"a\"></span>").select("span").first();
        Element b = Jsoup.parse("<span class=\"a\"></span>").select("span").first();
        assertTrue(a.equals(b));
    }

    // equals(): different attributes -> not equal
    @Test
    public void testEquals_differentAttributes_false() throws Throwable {
        Element a = Jsoup.parse("<span class=\"a\"></span>").select("span").first();
        Element c = Jsoup.parse("<span class=\"b\"></span>").select("span").first();
        assertFalse(a.equals(c));
    }

    // equals(): different getClass() -> not equal
    @Test
    public void testEquals_differentClass_false() throws Throwable {
        Element p = Jsoup.parse("<p>Hi</p>").select("p").first();
        Node textChild = p.childNode(0);
        assertFalse(p.equals(textChild));
    }

    // hashCode(): equal nodes produce equal hash codes
    @Test
    public void testHashCode_matchesEqualNodes() throws Throwable {
        Element a = Jsoup.parse("<span class=\"a\"></span>").select("span").first();
        Element b = Jsoup.parse("<span class=\"a\"></span>").select("span").first();
        assertEquals(a.hashCode(), b.hashCode());
    }

    // clone(): deep copy, standalone, independent modifications
    @Test
    public void testClone_deepCopyIndependent() throws Throwable {
        Element original = Jsoup.parse("<div id=\"o\"><p></p></div>").select("#o").first();
        Node cloneNode = original.clone();
        assertNotSame(original, cloneNode);
        assertNull(cloneNode.parent());
        assertEquals(original.childNodeSize(), cloneNode.childNodeSize());
        cloneNode.attr("id", "changed");
        assertEquals("o", original.attr("id"));
        assertEquals("changed", cloneNode.attr("id"));
    }
}
