package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

public class LeafNodeTest {

    private static class ConcreteLeafNode extends LeafNode {
        private final String nodeName;

        public ConcreteLeafNode(String nodeName) {
            this.nodeName = nodeName;
            this.value = "";
        }

        public ConcreteLeafNode(String nodeName, String initialValue) {
            this.nodeName = nodeName;
            this.value = initialValue;
        }

        @Override
        public String nodeName() {
            return nodeName;
        }
    }

    @Test
    public void testInitialStateAndCoreValue() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("testnode", "initial");
        assertEquals("initial", node.coreValue());
        
        node.coreValue("updated");
        assertEquals("updated", node.coreValue());
    }

    @Test
    public void testHasAttributesTransition() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("div", "textvalue");
        // Initially it has a String value, so hasAttributes() should be false
        assertEquals("textvalue", node.attr("div"));
        
        // Setting an attribute different from nodeName should trigger ensureAttributes()
        node.attr("class", "my-class");
        
        // Now it should have attributes
        assertTrue(node.hasAttr("class"));
        assertEquals("my-class", node.attr("class"));
        // The original core value should be preserved in attributes under nodeName()
        assertEquals("textvalue", node.attr("div"));
    }

    @Test
    public void testEnsureAttributesWithoutCoreValue() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("span");
        node.value = null; // force null core value
        
        Attributes attrs = node.attributes();
        assertNotNull(attrs);
        assertFalse(node.hasAttr("span"));
    }

    @Test
    public void testAttrEdgeCases() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("p", "hello");
        
        // Requesting attribute matching nodeName when no attributes map exists
        assertEquals("hello", node.attr("p"));
        
        // Requesting attribute not matching nodeName when no attributes map exists
        assertEquals("", node.attr("other"));
        
        // Requesting null key should throw IllegalArgumentException via Validate.notNull
        try {
            node.attr(null);
            fail("Expected exception for null key");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testAttrModificationWithoutAttributesMap() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("b", "bold");
        
        // Modifying attribute matching nodeName
        node.attr("b", "bolder");
        assertEquals("bolder", node.coreValue());
        
        // Modifying attribute not matching nodeName should convert to Attributes
        node.attr("id", "main");
        assertEquals("main", node.attr("id"));
        assertEquals("bolder", node.attr("b"));
    }

    @Test
    public void testRemoveAttrAndAbsUrl() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("a", "val");
        node.attr("href", "http://example.com");
        
        assertTrue(node.hasAttr("href"));
        node.removeAttr("href");
        assertFalse(node.hasAttr("href"));
        
        // Test absUrl with baseUri
        node.setBaseUri("http://base.com/");
        node.attr("href", "/path");
        assertEquals("http://base.com/path", node.absUrl("href"));
    }

    @Test
    public void testBaseUriAndSetBaseUri() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("div", "content");
        assertEquals("", node.baseUri());
        
        Element parent = new Element(Tag.valueOf("div"), "http://parent.base/");
        parent.appendChild(node);
        
        assertEquals("http://parent.base/", node.baseUri());
        
        // doSetBaseUri is a no-op, just call it for coverage
        node.setBaseUri("http://new.base/");
    }

    @Test
    public void testChildNodeSizeAndEnsureChildNodes() throws Throwable {
        ConcreteLeafNode node = new ConcreteLeafNode("div", "content");
        assertEquals(0, node.childNodeSize());
        
        try {
            node.ensureChildNodes();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Leaf Nodes do not have child nodes"));
        }
    }
}