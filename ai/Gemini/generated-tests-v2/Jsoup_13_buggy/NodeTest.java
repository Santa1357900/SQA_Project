package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class NodeTest {

    private static class DummyNode extends Node {
        private String name;

        public DummyNode(String baseUri, String name) {
            super(baseUri);
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
    public void testConstructorsAndBasicAttributes() throws Throwable {
        DummyNode node1 = new DummyNode("http://example.com", "test");
        assertEquals("http://example.com", node1.baseUri());
        assertEquals("test", node1.nodeName());
        assertNotNull(node1.attributes());

        DummyNode node2 = new DummyNode("http://example.com/");
        assertEquals("http://example.com", node2.baseUri());

        DummyNode node3 = new DummyNode();
        assertNull(node3.baseUri());
        assertNull(node3.attributes());
        assertEquals(0, node3.childNodes().size());
    }

    @Test
    public void testAttrOperations() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        Attributes attrs = new Attributes();
        attrs.put("key1", "value1");
        
        node = new DummyNode("http://example.com", attrs);
        assertEquals("value1", node.attr("key1"));
        assertEquals("", node.attr("nonexistent"));

        node.attr("key2", "value2");
        assertEquals("value2", node.attr("key2"));

        assertTrue(node.hasAttr("key1"));
        assertFalse(node.hasAttr("nonexistent"));

        node.removeAttr("key1");
        assertFalse(node.hasAttr("key1"));

        assertEquals("http://example.com/foo", node.attr("abs:href"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAttrNullKey() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        node.attr(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasAttrNullKey() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        node.hasAttr(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveAttrNullKey() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        node.removeAttr(null);
    }

    @Test
    public void testBaseUriUpdates() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        node.setBaseUri("http://example.org");
        assertEquals("http://example.org", node.baseUri());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetBaseUriNull() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        node.setBaseUri(null);
    }

    @Test
    public void testAbsUrlHandling() throws Throwable {
        DummyNode node = new DummyNode("http://example.com/path/");
        node.attr("href", "relative.html");
        assertEquals("http://example.com/path/relative.html", node.absUrl("href"));

        node.attr("href", "https://absolute.com/foo");
        assertEquals("https://absolute.com/foo", node.absUrl("href"));

        assertEquals("", node.absUrl("missing"));

        DummyNode badBase = new DummyNode("not a url");
        badBase.attr("href", "https://absolute.com/bar");
        assertEquals("https://absolute.com/bar", badBase.absUrl("href"));

        badBase.attr("href", "relative.html");
        assertEquals("", badBase.absUrl("href"));

        node.attr("href", "?query=1");
        assertEquals("http://example.com/path/?query=1", node.absUrl("href"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAbsUrlEmptyKey() throws Throwable {
        DummyNode node = new DummyNode("http://example.com");
        node.absUrl("");
    }

    @Test
    public void testChildNodesAndHierarchy() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com", "parent");
        DummyNode child1 = new DummyNode("http://example.com", "child1");
        DummyNode child2 = new DummyNode("http://example.com", "child2");

        parent.addChildren(child1, child2);
        assertEquals(2, parent.childNodes().size());
        assertEquals(child1, parent.childNode(0));
        assertEquals(child2, parent.childNode(1));

        assertEquals(parent, child1.parent());
        assertEquals(parent, child2.parent());
        assertEquals(0, child1.siblingIndex());
        assertEquals(1, child2.siblingIndex());

        DummyNode child3 = new DummyNode("http://example.com", "child3");
        parent.addChildren(1, child3);
        assertEquals(3, parent.childNodes().size());
        assertEquals(child3, parent.childNode(1));
        assertEquals(0, child1.siblingIndex());
        assertEquals(1, child3.siblingIndex());
        assertEquals(2, child2.siblingIndex());

        assertEquals(child2, child3.nextSibling());
        assertEquals(child1, child3.previousSibling());
        assertNull(child1.previousSibling());
        assertNull(child2.nextSibling());

        DummyNode orphan = new DummyNode("http://example.com", "orphan");
        assertNull(orphan.nextSibling());

        child1.remove();
        assertEquals(2, parent.childNodes().size());
        assertEquals(child3, parent.childNode(0));
        assertNull(child1.parent());
    }

    @Test
    public void testSiblingNodes() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com", "parent");
        DummyNode child1 = new DummyNode("http://example.com", "child1");
        DummyNode child2 = new DummyNode("http://example.com", "child2");
        parent.addChildren(child1, child2);

        List<Node> siblings = child1.siblingNodes();
        assertEquals(2, siblings.size());
        assertTrue(siblings.contains(child1));
        assertTrue(siblings.contains(child2));
    }

    @Test
    public void testOwnerDocument() throws Throwable {
        Document doc = new Document("http://example.com");
        Element el = new Element(Tag.valueOf("p"), "http://example.com");
        doc.appendChild(el);

        assertEquals(doc, el.ownerDocument());

        DummyNode orphan = new DummyNode("http://example.com");
        assertNull(orphan.ownerDocument());

        assertEquals(doc, doc.ownerDocument());
    }

    @Test
    public void testReplaceAndBeforeAfter() throws Throwable {
        DummyNode parent = new DummyNode("http://example.com", "parent");
        DummyNode child1 = new DummyNode("http://example.com", "child1");
        DummyNode child2 = new DummyNode("http://example.com", "child2");
        parent.addChildren(child1, child2);

        DummyNode replacement = new DummyNode("http://example.com", "replacement");
        child1.replaceWith(replacement);
        assertEquals(replacement, parent.childNode(0));
        assertNull(child1.parent());
        assertEquals(parent, replacement.parent());

        DummyNode beforeNode = new DummyNode("http://example.com", "before");
        replacement.before(beforeNode);
        assertEquals(beforeNode, parent.childNode(0));
        assertEquals(replacement, parent.childNode(1));

        DummyNode afterNode = new DummyNode("http://example.com", "after");
        replacement.after(afterNode);
        assertEquals(replacement, parent.childNode(1));
        assertEquals(afterNode, parent.childNode(2));

        replacement.before("<span>htmlBefore</span>");
        replacement.after("<span>htmlAfter</span>");
        assertTrue(parent.childNodes().size() > 3);
    }

    @Test
    public void testWrap() throws Throwable {
        Element parent = new Element(Tag.valueOf("div"), "http://example.com");
        Element child = new Element(Tag.valueOf("p"), "http://example.com");
        parent.appendChild(child);

        Node wrapped = child.wrap("<div class='wrapper'></div>");
        assertNotNull(wrapped);
        assertEquals("div", parent.childNode(0).nodeName());
    }

    @Test
    public void testCloneNode() throws Throwable {
        DummyNode node = new DummyNode("http://example.com", "test");
        node.attr("key", "val");
        DummyNode child = new DummyNode("http://example.com", "child");
        node.addChildren(child);

        Node clone = node.clone();
        assertNotSame(node, clone);
        assertEquals(node.nodeName(), clone.nodeName());
        assertEquals(node.baseUri(), clone.baseUri());
        assertEquals(node.attr("key"), clone.attr("key"));
        assertEquals(1, clone.childNodes().size());
        assertNull(clone.parent());
        assertNotNull(clone.attributes());
        assertNotSame(node.attributes(), clone.attributes());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        DummyNode node1 = new DummyNode("http://example.com");
        DummyNode node2 = new DummyNode("http://example.com");

        assertTrue(node1.equals(node1));
        assertFalse(node1.equals(node2));
        assertFalse(node1.equals(null));

        int hash1 = node1.hashCode();
        int hash2 = node2.hashCode();
        assertEquals(hash1, hash2);

        DummyNode nodeWithParent = new DummyNode("http://example.com");
        DummyNode parent = new DummyNode("http://example.com");
        parent.addChildren(nodeWithParent);
        assertTrue(nodeWithParent.hashCode() != 0);
    }

    @Test
    public void testToStringAndOuterHtml() throws Throwable {
        DummyNode node = new DummyNode("http://example.com", "span");
        String html = node.toString();
        assertEquals("<span></span>", html);
    }
}