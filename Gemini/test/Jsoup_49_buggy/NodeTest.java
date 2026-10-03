package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;

public class NodeTest {

    private static class DummyNode extends Node {
        private String name;

        public DummyNode(String baseUri, String name) {
            super(baseUri, new Attributes());
            this.name = name;
        }

        public DummyNode(String baseUri) {
            super(baseUri);
            this.name = "dummy";
        }

        public DummyNode() {
            super();
            this.name = "dummy";
        }

        @Override
        public String nodeName() {
            return name;
        }

        @Override
        void outerHtmlHead(StringBuilder accum, int depth, Document.OutputSettings out) {
            accum.append("<").append(name).append(">");
        }

        @Override
        void outerHtmlTail(StringBuilder accum, int depth, Document.OutputSettings out) {
            accum.append("</").append(name).append(">");
        }
    }

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        DummyNode node1 = new DummyNode("http://example.com", "test");
        assertEquals("http://example.com", node1.baseUri());
        assertEquals("test", node1.nodeName());
        assertNotNull(node1.attributes());
        assertEquals(0, node1.childNodeSize());
        assertNull(node1.parent());
        assertNull(node1.parentNode());
        assertEquals(0, node1.siblingIndex());

        DummyNode node2 = new DummyNode("http://example.org");
        assertEquals("http://example.org", node2.baseUri());

        DummyNode node3 = new DummyNode();
        assertNull(node3.baseUri());
        assertNull(node3.attributes());
    }

    @Test
    public void testAttributesHandling() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        node.attr("key1", "value1");
        
        assertEquals("value1", node.attr("key1"));
        assertEquals("", node.attr("nonexistent"));
        assertTrue(node.hasAttr("key1"));
        assertFalse(node.hasAttr("nonexistent"));

        node.removeAttr("key1");
        assertFalse(node.hasAttr("key1"));
        assertEquals("", node.attr("key1"));
    }

    @Test
    public void testAbsUrlHandling() throws Throwable {
        DummyNode node = new DummyNode("http://example.com/path/");
        node.attr("href", "sub/page.html");
        node.attr("absHref", "http://other.com/abs");

        assertEquals("http://example.com/path/sub/page.html", node.absUrl("href"));
        assertEquals("http://other.com/abs", node.absUrl("absHref"));
        assertEquals("", node.absUrl("missing"));

        assertEquals("http://example.com/path/sub/page.html", node.attr("abs:href"));
    }

    @Test
    public void testSetBaseUri() throws Throwable {
        DummyNode parent = new DummyNode("http://parent.com");
        DummyNode child = new DummyNode("http://child.com");
        parent.addChildren(child);

        parent.setBaseUri("http://newbase.com");
        assertEquals("http://newbase.com", parent.baseUri());
        assertEquals("http://newbase.com", child.baseUri());
    }

    @Test
    public void testChildNodesManipulation() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com");
        DummyNode child1 = new DummyNode("http://example.com", "c1");
        DummyNode child2 = new DummyNode("http://example.com", "c2");

        parent.addChildren(child1, child2);
        assertEquals(2, parent.childNodeSize());
        assertEquals(child1, parent.childNode(0));
        assertEquals(child2, parent.childNode(1));
        assertEquals(0, child1.siblingIndex());
        assertEquals(1, child2.siblingIndex());
        assertEquals(parent, child1.parentNode());

        List<Node> children = parent.childNodes();
        assertEquals(2, children.size());

        List<Node> copy = parent.childNodesCopy();
        assertEquals(2, copy.size());
        assertNotEquals(child1, copy.get(0));

        DummyNode child3 = new DummyNode("http://example.com", "c3");
        parent.addChildren(1, child3);
        assertEquals(3, parent.childNodeSize());
        assertEquals(child3, parent.childNode(1));
        assertEquals(1, child3.siblingIndex());
        assertEquals(2, child2.siblingIndex());
    }

    @Test
    public void testSiblingsAndNavigation() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com");
        DummyNode c1 = new DummyNode("http://example.com", "c1");
        DummyNode c2 = new DummyNode("http://example.com", "c2");
        DummyNode c3 = new DummyNode("http://example.com", "c3");

        parent.addChildren(c1, c2, c3);

        assertNull(c1.previousSibling());
        assertEquals(c2, c1.nextSibling());

        assertEquals(c1, c2.previousSibling());
        assertEquals(c3, c2.nextSibling());

        assertEquals(c2, c3.previousSibling());
        assertNull(c3.nextSibling());

        List<Node> siblingsC2 = c2.siblingNodes();
        assertEquals(2, siblingsC2.size());
        assertTrue(siblingsC2.contains(c1));
        assertTrue(siblingsC2.contains(c3));

        DummyNode orphan = new DummyNode("http://example.com");
        assertTrue(orphan.siblingNodes().isEmpty());
        assertNull(orphan.nextSibling());
        assertNull(orphan.previousSibling());
    }

    @Test
    public void testRemoveAndReplace() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com");
        DummyNode c1 = new DummyNode("http://example.com", "c1");
        DummyNode c2 = new DummyNode("http://example.com", "c2");

        parent.addChildren(c1, c2);
        assertEquals(2, parent.childNodeSize());

        c1.remove();
        assertEquals(1, parent.childNodeSize());
        assertEquals(c2, parent.childNode(0));
        assertEquals(0, c2.siblingIndex());
        assertNull(c1.parentNode());

        DummyNode c3 = new DummyNode("http://example.com", "c3");
        c2.replaceWith(c3);
        assertEquals(1, parent.childNodeSize());
        assertEquals(c3, parent.childNode(0));
        assertNull(c2.parentNode());
        assertEquals(parent, c3.parentNode());
    }

    @Test
    public void testCloneNode() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com", "parent");
        DummyNode child = new DummyNode("http://example.com", "child");
        parent.addChildren(child);
        parent.attr("attr", "val");

        Node clone = parent.clone();
        assertNotEquals(parent, clone); // Object equality vs Content equality check
        assertEquals(parent.nodeName(), clone.nodeName());
        assertEquals(1, clone.childNodeSize());
        assertNull(clone.parentNode());
        assertNotNull(clone.childNode(0).parentNode());
        assertEquals(clone, clone.childNode(0).parentNode());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        DummyNode n1 = new DummyNode("http://example.com", "test");
        DummyNode n2 = new DummyNode("http://example.com", "test");
        DummyNode n3 = new DummyNode("http://example.com", "other");

        n1.attr("a", "1");
        n2.attr("a", "1");

        assertEquals(n1, n1);
        assertEquals(n1, n2);
        assertEquals(n1.hashCode(), n2.hashCode());

        assertFalse(n1.equals(null));
        assertFalse(n1.equals("string"));
        assertFalse(n1.equals(n3));

        DummyNode n4 = new DummyNode("http://example.com", "test");
        n4.addChildren(new DummyNode("http://example.com", "child"));
        assertFalse(n1.equals(n4));
    }

    @Test
    public void testOwnerDocument() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        assertNull(node.ownerDocument());

        Document doc = new Document("http://example.com");
        doc.appendChild(node);
        assertEquals(doc, node.ownerDocument());
    }

    @Test
    public void testToStringAndOuterHtml() throws Throwable {
        DummyNode node = new DummyNode("http://example.com", "div");
        String html = node.outerHtml();
        assertEquals("<div></div>", html);
        assertEquals(html, node.toString());
    }
}