package org.apache.commons.jxpath.ri.model;

import java.util.Locale;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.Compiler;
import org.apache.commons.jxpath.ri.NamespaceResolver;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.compiler.NodeNameTest;
import org.apache.commons.jxpath.ri.compiler.NodeTest;
import org.apache.commons.jxpath.ri.compiler.NodeTypeTest;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;

import junit.framework.TestCase;

public class NodePointerTest extends TestCase {

    private static class ConcreteNodePointer extends NodePointer {
        private Object value;
        private boolean collection;
        private int length = 1;
        private QName name;

        public ConcreteNodePointer(NodePointer parent, QName name, Object value) {
            super(parent);
            this.name = name;
            this.value = value;
        }

        public ConcreteNodePointer(NodePointer parent, Locale locale, QName name, Object value) {
            super(parent, locale);
            this.name = name;
            this.value = value;
        }

        public boolean isLeaf() {
            return true;
        }

        public boolean isCollection() {
            return collection;
        }

        public void setCollection(boolean collection) {
            this.collection = collection;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public QName getName() {
            return name;
        }

        public Object getBaseValue() {
            return value;
        }

        public Object getImmediateNode() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public int compareChildNodePointers(NodePointer pointer1, NodePointer pointer2) {
            return 0;
        }
    }

    private static class ContainerNodePointer extends ConcreteNodePointer {
        public ContainerNodePointer(NodePointer parent, QName name, Object value) {
            super(parent, name, value);
        }

        public boolean isContainer() {
            return true;
        }
    }

    public void testNewNodePointerNullBean() throws Throwable {
        QName qName = new QName("test");
        Locale locale = Locale.US;
        NodePointer np = NodePointer.newNodePointer(qName, null, locale);
        assertNotNull(np);
        assertTrue(np instanceof NullPointer);
        assertEquals(qName, np.getName());
        assertEquals(locale, np.getLocale());
    }

    public void testNewNodePointerWithBean() throws Throwable {
        QName qName = new QName("test");
        Object bean = new Object();
        try {
            NodePointer.newNodePointer(qName, bean, Locale.US);
            fail("Expected JXPathException for unhandled bean class");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Could not allocate a NodePointer"));
        }
    }

    public void testNewChildNodePointer() throws Throwable {
        ConcreteNodePointer parent = new ConcreteNodePointer(null, new QName("parent"), new Object());
        QName qName = new QName("child");
        Object bean = new Object();
        try {
            NodePointer.newChildNodePointer(parent, qName, bean);
            fail("Expected JXPathException for unhandled bean class");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("Could not allocate a NodePointer"));
        }
    }

    public void testNamespaceResolverAndParent() throws Throwable {
        ConcreteNodePointer parent = new ConcreteNodePointer(null, new QName("parent"), new Object());
        NamespaceResolver resolver = new NamespaceResolver();
        parent.setNamespaceResolver(resolver);

        ConcreteNodePointer child = new ConcreteNodePointer(parent, new QName("child"), new Object());
        assertEquals(resolver, child.getNamespaceResolver());
    }

    public void testGetParentSkippingContainers() throws Throwable {
        ConcreteNodePointer grandParent = new ConcreteNodePointer(null, new QName("gp"), new Object());
        ContainerNodePointer container = new ContainerNodePointer(grandParent, new QName("container"), new Object());
        ConcreteNodePointer child = new ConcreteNodePointer(container, new QName("child"), new Object());

        assertEquals(container, child.getImmediateParentPointer());
        assertEquals(grandParent, child.getParent());
    }

    public void testAttributeAndRoot() throws Throwable {
        ConcreteNodePointer parent = new ConcreteNodePointer(null, new QName("root"), new Object());
        assertTrue(parent.isRoot());
        assertFalse(parent.isAttribute());

        parent.setAttribute(true);
        assertTrue(parent.isAttribute());

        ConcreteNodePointer child = new ConcreteNodePointer(parent, new QName("child"), new Object());
        assertFalse(child.isRoot());
    }

    public void testIsNodeAndIsContainer() throws Throwable {
        ConcreteNodePointer node = new ConcreteNodePointer(null, new QName("node"), new Object());
        assertTrue(node.isNode());
        assertFalse(node.isContainer());

        ContainerNodePointer container = new ContainerNodePointer(null, new QName("cont"), new Object());
        assertFalse(container.isNode());
        assertTrue(container.isContainer());
    }

    public void testIndexAndActual() throws Throwable {
        ConcreteNodePointer np = new ConcreteNodePointer(null, new QName("test"), new Object());
        assertEquals(NodePointer.WHOLE_COLLECTION, np.getIndex());

        np.setIndex(0);
        assertEquals(0, np.getIndex());
        assertTrue(np.isActual());

        np.setCollection(true);
        np.setLength(2);
        np.setIndex(1);
        assertTrue(np.isActual());

        np.setIndex(2);
        assertFalse(np.isActual());
    }

    public void testGetValueAndNodeValue() throws Throwable {
        Object val = "testValue";
        ConcreteNodePointer np = new ConcreteNodePointer(null, new QName("test"), val);
        assertEquals(val, np.getValue());
        assertEquals(val, np.getNodeValue());
        assertEquals(val, np.getNode());
    }

    public void testGetRootNode() throws Throwable {
        ConcreteNodePointer gp = new ConcreteNodePointer(null, new QName("gp"), "rootVal");
        ConcreteNodePointer parent = new ConcreteNodePointer(gp, new QName("parent"), "parentVal");
        ConcreteNodePointer child = new ConcreteNodePointer(parent, new QName("child"), "childVal");

        assertEquals("rootVal", child.getRootNode());
    }

    public void testTestNodeNull() throws Throwable {
        ConcreteNodePointer np = new ConcreteNodePointer(null, new QName("test"), new Object());
        assertTrue(np.testNode(null));
    }

    public void testTestNodeNameTest() throws Throwable {
        QName qName = new QName("prefix", "local");
        ConcreteNodePointer np = new ConcreteNodePointer(null, qName, new Object());

        NodeNameTest matchTest = new NodeNameTest(qName);
        assertTrue(np.testNode(matchTest));

        NodeNameTest wildcardTest = new NodeNameTest(new QName("prefix", "*"));
        assertTrue(np.testNode(wildcardTest));

        NodeNameTest nonMatchTest = new NodeNameTest(new QName("prefix", "other"));
        assertFalse(np.testNode(nonMatchTest));

        NodeNameTest nullNameTest = new NodeNameTest(new QName(null, null)) {
            public QName getNodeName() {
                return null;
            }
        };
        assertFalse(np.testNode(nullNameTest));
    }

    public void testTestNodeTypeTest() throws Throwable {
        ConcreteNodePointer np = new ConcreteNodePointer(null, new QName("test"), new Object());
        NodeTypeTest nodeTypeTest = new NodeTypeTest(Compiler.NODE_TYPE_NODE);
        assertTrue(np.testNode(nodeTypeTest));

        NodeTypeTest commentTypeTest = new NodeTypeTest(Compiler.NODE_TYPE_COMMENT);
        assertFalse(np.testNode(commentTypeTest));
    }

    public void testCreatePathAndChildExceptions() throws Throwable {
        ConcreteNodePointer np = new ConcreteNodePointer(null, new QName("test"), new Object());
        assertEquals(np, np.createPath(null, "val"));
        assertEquals(np, np.createPath(null));

        try {
            np.createChild(null, new QName("sub"), 0, "val");
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("operation is not allowed"));
        }

        try {
            np.createChild(null, new QName("sub"), 0);
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("operation is not allowed"));
        }

        try {
            np.createAttribute(null, new QName("attr"));
            fail("Expected JXPathException");
        } catch (JXPathException e) {
            assertTrue(e.getMessage().contains("operation is not allowed"));
        }
    }

    public void testLocaleAndIsLanguage() throws Throwable {
        ConcreteNodePointer parent = new ConcreteNodePointer(null, null, new Locale("en", "US"), new QName("test"), new Object());
        ConcreteNodePointer child = new ConcreteNodePointer(parent, new QName("child"), new Object());

        assertEquals(Locale.US, child.getLocale());
        assertTrue(child.isLanguage("EN"));
        assertTrue(child.isLanguage("en-us"));
        assertFalse(child.isLanguage("fr"));
    }

    public void testIteratorsAndNamespacesDefaults() throws Throwable {
        ConcreteNodePointer np = new ConcreteNodePointer(null, new QName("test"), new Object());
        assertNull(np.childIterator(null, false, null));
        assertNull(np.attributeIterator(new QName("attr")));
        assertNull(np.namespaceIterator());
        assertNull(np.namespacePointer("ns"));
        assertNull(np.getNamespaceURI("prefix"));
        assertNull(np.getNamespaceURI());
        assertNull(np.getDefaultNamespaceURI());
        assertFalse(np.isDefaultNamespace("prefix"));
        assertTrue(np.isDefaultNamespace(null));
    }

    public void testAsPath() throws Throwable {
        ConcreteNodePointer parent = new ConcreteNodePointer(null, new QName("parent"), new Object());
        ConcreteNodePointer child = new ConcreteNodePointer(parent, new QName("child"), new Object());
        assertEquals("/parent/child", child.asPath());

        child.setAttribute(true);
        assertEquals("/parent/@child", child.asPath());

        child.setAttribute(false);
        child.setCollection(true);
        child.setIndex(2);
        assertEquals("/parent/child[3]", child.asPath());
    }

    public void testCloneAndToString() throws Throwable {
        ConcreteNodePointer parent = new ConcreteNodePointer(null, new QName("parent"), new Object());
        ConcreteNodePointer clone = (ConcreteNodePointer) parent.clone();
        assertNotNull(clone);
        assertEquals(parent.asPath(), clone.asPath());
        assertEquals(parent.toString(), parent.asPath());
    }

    public void testCompareTo() throws Throwable {
        ConcreteNodePointer p1 = new ConcreteNodePointer(null, new QName("p1"), new Object());
        ConcreteNodePointer p2 = new ConcreteNodePointer(null, new QName("p2"), new Object());

        assertEquals(0, p1.compareTo(p1));
    }

    public void testPrintPointerChain() throws Throwable {
        ConcreteNodePointer parent = new ConcreteNodePointer(null, new QName("parent"), new Object());
        ConcreteNodePointer child = new ConcreteNodePointer(parent, new QName("child"), new Object());
        child.printPointerChain();
    }
}