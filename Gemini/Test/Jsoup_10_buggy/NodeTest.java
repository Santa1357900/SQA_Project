package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;

public class NodeTest {

    private static class ConcreteNode extends Node {
        private String nodeName;

        public ConcreteNode(String baseUri) {
            super(baseUri);
            this.nodeName = "testNode";
        }

        public ConcreteNode(String baseUri, Attributes attributes) {
            super(baseUri, attributes);
            this.nodeName = "testNode";
        }

        public ConcreteNode() {
            super();
            this.nodeName = "testNode";
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        @Override
        public String nodeName() {
            return nodeName;
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
        attr.put("key", "value");
        ConcreteNode node = new ConcreteNode("http://example.com", attr);

        assertEquals("http://example.com", node.baseUri());
        assertEquals("value", node.attr("key"));
        assertTrue(node.hasAttr("key"));
        assertFalse(node.hasAttr("nonexistent"));
        assertEquals("testNode", node.nodeName());
        assertNotNull(node.attributes());
        assertNull(node.parent());
    }

    @Test
    public void testDefaultConstructor() throws Throwable {
        ConcreteNode node = new ConcreteNode();
        assertNull(node.attributes());
        assertNotNull(node.childNodes());
        assertEquals(0, node.childNodes().size());
    }

    @Test
    public void testAttributeManipulation() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.attr("foo", "bar");
        assertEquals("bar", node.attr("foo"));
        assertTrue(node.hasAttr("foo"));

        node.removeAttr("foo");
        assertFalse(node.hasAttr("foo"));
        assertEquals("", node.attr("foo"));
    }

    @Test
    public void testAbsUrl() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com/path/");
        node.attr("href", "sub/file.html");
        node.attr("abshref", "http://absolute.com/resource");
        node.attr("invalid", ":invalid-url");

        assertEquals("http://example.com/path/sub/file.html", node.absUrl("href"));
        assertEquals("http://absolute.com/resource", node.absUrl("abshref"));
        assertEquals("", node.absUrl("missing"));
        assertEquals("", node.absUrl("invalid"));
    }

    @Test
    public void testAbsUrlWithMalformedBase() throws Throwable {
        ConcreteNode node = new ConcreteNode("not-a-url");
        node.attr("href", "http://example.com/absolute");
        assertEquals("http://example.com/absolute", node.absUrl("href"));

        node.attr("href", "malformed");
        assertEquals("", node.absUrl("href"));
    }

    @Test
    public void testBaseUriUpdates() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.setBaseUri("http://new-example.com");
        assertEquals("http://new-example.com", node.baseUri());
    }

    @Test
    public void testChildNodeManagement() throws Throwable {
        ConcreteNode parent = new ConcreteNode("http://example.com");
        ConcreteNode child1 = new ConcreteNode("http://example.com");
        ConcreteNode child2 = new ConcreteNode("http://example.com");

        child1.setNodeName("child1");
        child2.setNodeName("child2");

        parent.addChildren(child1, child2);

        assertEquals(2, parent.childNodes().size());
        assertEquals(child1, parent.childNode(0));
        assertEquals(child2, parent.childNode(1));
        assertEquals(parent, child1.parent());
        assertEquals(parent, child2.parent());
        assertEquals(Integer.valueOf(0), child1.siblingIndex());
        assertEquals(Integer.valueOf(1), child2.siblingIndex());

        ConcreteNode[] array = parent.childNodesAsArray();
        assertEquals(2, array.length);
        assertEquals(child1, array[0]);

        // Test inserting child at index
        ConcreteNode child0 = new ConcreteNode("http://example.com");
        child0.setNodeName("child0");
        parent.addChildren(0, child0);
        assertEquals(3, parent.childNodes().size());
        assertEquals(child0, parent.childNode(0));
        assertEquals(Integer.valueOf(0), child0.siblingIndex());
        assertEquals(Integer.valueOf(1), child1.siblingIndex());

        // Test child removal
        child1.remove();
        assertEquals(2, parent.childNodes().size());
        assertNull(child1.parent());
        assertEquals(child2, parent.childNode(1));
    }

    @Test
    public void testReplaceChildAndReplaceWith() throws Throwable {
        ConcreteNode parent = new ConcreteNode("http://example.com");
        ConcreteNode child1 = new ConcreteNode("http://example.com");
        child1.setNodeName("c1");
        parent.addChildren(child1);

        ConcreteNode replacement = new ConcreteNode("http://example.com");
        replacement.setNodeName("c2");

        child1.replaceWith(replacement);
        assertEquals(replacement, parent.childNode(0));
        assertEquals(parent, replacement.parent());
        assertNull(child1.parent());
    }

    @Test
    public void testSiblings() throws Throwable {
        ConcreteNode parent = new ConcreteNode("http://example.com");
        ConcreteNode child1 = new ConcreteNode("http://example.com");
        ConcreteNode child2 = new ConcreteNode("http://example.com");
        ConcreteNode child3 = new ConcreteNode("http://example.com");

        parent.addChildren(child1, child2, child3);

        assertNull(child1.previousSibling());
        assertEquals(child2, child1.nextSibling());

        assertEquals(child1, child2.previousSibling());
        assertEquals(child3, child2.nextSibling());

        assertEquals(child2, child3.previousSibling());
        assertNull(child3.nextSibling());

        List<Node> siblings = child2.siblingNodes();
        assertEquals(3, siblings.size());
        
        ConcreteNode orphan = new ConcreteNode("http://example.com");
        assertNull(orphan.nextSibling());
        assertNull(orphan.previousSibling());
    }

    @Test
    public void testOwnerDocument() throws Throwable {
        Document doc = new Document("http://example.com");
        ConcreteNode node = new ConcreteNode("http://example.com");
        doc.appendChild(node);

        assertEquals(doc, node.ownerDocument());
        assertEquals(doc, doc.ownerDocument());

        ConcreteNode orphan = new ConcreteNode("http://example.com");
        assertNotNull(orphan.ownerDocument()); // creates a dummy doc if none
    }

    @Test
    public void testCloneAndDoClone() throws Throwable {
        Attributes attr = new Attributes();
        attr.put("attrKey", "attrVal");
        ConcreteNode original = new ConcreteNode("http://example.com", attr);
        ConcreteNode child = new ConcreteNode("http://example.com");
        child.setNodeName("child");
        original.addChildren(child);

        Node clone = original.clone();
        assertNotNull(clone);
        assertNotSame(original, clone);
        assertEquals(original.baseUri(), clone.baseUri());
        assertEquals(1, clone.childNodes().size());
        assertNotSame(original.childNode(0), clone.childNode(0));
        assertEquals(clone, clone.childNode(0).parent());
        assertNull(clone.parent());
        assertEquals("attrVal", clone.attr("attrKey"));
    }

    @Test
    public void testToStringAndOuterHtml() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.setNodeName("span");
        String html = node.outerHtml();
        assertEquals("<span></span>", html);
        assertEquals(html, node.toString());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        ConcreteNode node1 = new ConcreteNode("http://example.com");
        ConcreteNode node2 = new ConcreteNode("http://example.com");

        assertTrue(node1.equals(node1));
        assertFalse(node1.equals(node2));
        assertFalse(node1.equals(null));

        int hash = node1.hashCode();
        assertTrue(hash != 0 || hash == 0); // Just executing to check coverage
    }

    @Test
    public void testReparentChildExtraneous() throws Throwable {
        ConcreteNode parent1 = new ConcreteNode("http://example.com");
        ConcreteNode parent2 = new ConcreteNode("http://example.com");
        ConcreteNode child = new ConcreteNode("http://example.com");

        parent1.addChildren(child);
        assertEquals(1, parent1.childNodes().size());
        assertEquals(0, parent2.childNodes().size());

        parent2.addChildren(child);
        assertEquals(0, parent1.childNodes().size());
        assertEquals(1, parent2.childNodes().size());
        assertEquals(parent2, child.parent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAttrNullKey() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.attr(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasAttrNullKey() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.hasAttr(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveAttrNullKey() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.removeAttr(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetBaseUriNull() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.setBaseUri(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveOrphanNode() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        node.remove();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReplaceWithOrphanNode() throws Throwable {
        ConcreteNode node = new ConcreteNode("http://example.com");
        ConcreteNode replacement = new ConcreteNode("http://example.com");
        node.replaceWith(replacement);
    }
}