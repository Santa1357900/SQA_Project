package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class NodeTest {

    private static class DummyNode extends Node {
        private String name;

        public DummyNode(String baseUri, Attributes attributes, String name) {
            super(baseUri, attributes);
            this.name = name;
        }

        public DummyNode(String baseUri, String name) {
            super(baseUri);
            this.name = name;
        }

        public DummyNode(String name) {
            super();
            this.name = name;
        }

        @Override
        public String nodeName() {
            return name;
        }

        @Override
        void outerHtmlHead(StringBuilder accum, int depth, Document.OutputSettings out) {
            accum.append("<").append(nodeName()).append(">");
        }

        @Override
        void outerHtmlTail(StringBuilder accum, int depth, Document.OutputSettings out) {
            accum.append("</").append(nodeName()).append(">");
        }
    }

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        Attributes attr = new Attributes();
        attr.put("id", "testId");
        DummyNode node1 = new DummyNode("http://example.com", attr, "div");
        assertEquals("http://example.com", node1.baseUri());
        assertEquals("div", node1.nodeName());
        assertEquals("testId", node1.attr("id"));
        assertTrue(node1.hasAttr("id"));
        assertFalse(node1.hasAttr("class"));

        DummyNode node2 = new DummyNode("http://example.org", "span");
        assertEquals("http://example.org", node2.baseUri());
        assertEquals("span", node2.nodeName());
        assertNotNull(node2.attributes());

        DummyNode node3 = new DummyNode("p");
        assertEquals("p", node3.nodeName());
        assertNull(node3.attributes());
    }

    @Test
    public void testAttributeManipulation() throws Throwable {
        DummyNode node = new DummyNode("http://example.com", "a");
        node.attr("href", "page.html");
        assertEquals("page.html", node.attr("href"));
        assertTrue(node.hasAttr("href"));

        node.removeAttr("href");
        assertFalse(node.hasAttr("href"));
        assertEquals("", node.attr("href"));
    }

    @Test
    public void testAbsUrl() throws Throwable {
        DummyNode node = new DummyNode("http://example.com/path/", "a");
        node.attr("href", "sub/file.html");
        assertEquals("http://example.com/path/sub/file.html", node.absUrl("href"));

        node.attr("absHref", "http://absolute.com/resource");
        assertEquals("http://absolute.com/resource", node.absUrl("absHref"));

        node.attr("bad", ":invalid-url");
        assertEquals("", node.absUrl("bad"));

        assertEquals("", node.absUrl("nonexistent"));
    }

    @Test
    public void testAbsUrlWithMalformedBase() throws Throwable {
        DummyNode node = new DummyNode("not-a-valid-base-url", "a");
        node.attr("href", "http://absolute.com/ok");
        assertEquals("http://absolute.com/ok", node.absUrl("href"));

        node.attr("href2", "malformed");
        assertEquals("", node.absUrl("href2"));
    }

    @Test
    public void testBaseUriUpdates() throws Throwable {
        DummyNode node = new DummyNode("http://example.com", "div");
        node.setBaseUri("http://example.org");
        assertEquals("http://example.org", node.baseUri());
    }

    @Test
    public void testChildNodesAndHierarchy() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com", "div");
        DummyNode child1 = new DummyNode("http://example.com", "p");
        DummyNode child2 = new DummyNode("http://example.com", "span");

        parent.addChildren(child1, child2);
        assertEquals(2, parent.childNodes().size());
        assertEquals(child1, parent.childNode(0));
        assertEquals(child2, parent.childNode(1));
        assertEquals(parent, child1.parent());
        assertEquals(parent, child2.parent());

        assertEquals(child2, child1.nextSibling());
        assertNull(child1.previousSibling());
        assertEquals(child1, child2.previousSibling());
        assertNull(child2.nextSibling());

        Node[] arr = parent.childNodesAsArray();
        assertEquals(2, arr.length);
        assertEquals(child1, arr[0]);
        assertEquals(child2, arr[1]);
    }

    @Test
    public void testAddChildrenAtIndex() throws Throwable {
        DummyNode parent = new DummyNode("div");
        DummyNode child1 = new DummyNode("p1");
        DummyNode child2 = new DummyNode("p2");
        DummyNode child3 = new DummyNode("p3");

        parent.addChildren(child1, child3);
        parent.addChildren(1, child2);

        assertEquals(3, parent.childNodes().size());
        assertEquals(child1, parent.childNode(0));
        assertEquals(child2, parent.childNode(1));
        assertEquals(child3, parent.childNode(2));
        assertEquals(0, child1.siblingIndex().intValue());
        assertEquals(1, child2.siblingIndex().intValue());
        assertEquals(2, child3.siblingIndex().intValue());
    }

    @Test
    public void testRemoveChild() throws Throwable {
        DummyNode parent = new DummyNode("div");
        DummyNode child1 = new DummyNode("p1");
        DummyNode child2 = new DummyNode("p2");
        parent.addChildren(child1, child2);

        child1.remove();
        assertEquals(1, parent.childNodes().size());
        assertEquals(child2, parent.childNode(0));
        assertNull(child1.parent());
        assertEquals(0, child2.siblingIndex().intValue());
    }

    @Test
    public void testReplaceWith() throws Throwable {
        DummyNode parent = new DummyNode("div");
        DummyNode child1 = new DummyNode("p1");
        DummyNode child2 = new DummyNode("p2");
        parent.addChildren(child1);

        child1.replaceWith(child2);
        assertEquals(1, parent.childNodes().size());
        assertEquals(child2, parent.childNode(0));
        assertNull(child1.parent());
        assertEquals(parent, child2.parent());
        assertEquals(0, child2.siblingIndex().intValue());
    }

    @Test
    public void testOwnerDocument() throws Throwable {
        Document doc = new Document("http://example.com");
        DummyNode node = new DummyNode("p");
        doc.appendChild(node);
        assertEquals(doc, node.ownerDocument());
        assertEquals(doc, doc.ownerDocument());

        DummyNode orphan = new DummyNode("span");
        assertNull(orphan.ownerDocument());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        DummyNode node1 = new DummyNode("div");
        DummyNode node2 = new DummyNode("div");
        assertTrue(node1.equals(node1));
        assertFalse(node1.equals(node2));
        assertFalse(node1.equals(null));

        node1.hashCode();
        node2.hashCode();
    }

    @Test
    public void testToStringAndOuterHtml() throws Throwable {
        DummyNode node = new DummyNode("span");
        String html = node.toString();
        assertNotNull(html);
        assertTrue(html.contains("span"));
    }
}