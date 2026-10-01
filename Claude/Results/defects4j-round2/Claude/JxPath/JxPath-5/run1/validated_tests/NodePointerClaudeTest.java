package org.apache.commons.jxpath.ri.model;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;

public class NodePointerClaudeTest {

    private static class StubNodePointer extends NodePointer {
        private boolean collection = false;
        private int length = 1;
        private Object immediateNode;
        private boolean containerFlag = false;
        private Object lastSetValue;
        private int compareResult = 0;

        protected StubNodePointer(NodePointer parent) {
            super(parent);
        }

        protected StubNodePointer(NodePointer parent, Locale locale) {
            super(parent, locale);
        }

        public boolean isLeaf() {
            return false;
        }

        public boolean isCollection() {
            return collection;
        }

        public int getLength() {
            return length;
        }

        public QName getName() {
            return null;
        }

        public Object getBaseValue() {
            return null;
        }

        public Object getImmediateNode() {
            return immediateNode;
        }

        public void setValue(Object value) {
            this.lastSetValue = value;
        }

        public int compareChildNodePointers(NodePointer pointer1, NodePointer pointer2) {
            return compareResult;
        }

        public boolean isContainer() {
            return containerFlag;
        }

        void setCollectionFlag(boolean v) {
            collection = v;
        }

        void setLengthValue(int v) {
            length = v;
        }

        void setImmediateNodeValue(Object v) {
            immediateNode = v;
        }

        void setContainerFlagValue(boolean v) {
            containerFlag = v;
        }

        void setCompareResultValue(int v) {
            compareResult = v;
        }

        Object getLastSetValue() {
            return lastSetValue;
        }
    }

    private StubNodePointer root;

    @Before
    public void setUp() throws Throwable {
        root = new StubNodePointer(null);
    }

    // Covers newNodePointer: bean == null branch returns a NullPointer instance
    @Test
    public void testNewNodePointer_beanNull_returnsNullPointer() throws Throwable {
        NodePointer pointer = NodePointer.newNodePointer(null, null, null);
        assertTrue(pointer instanceof NullPointer);
    }

    // Covers getNamespaceResolver() default null and getDefaultNamespaceURI() default null
    @Test
    public void testGetNamespaceResolverAndDefaultNamespaceURI_default_null() throws Throwable {
        assertNull(root.getNamespaceResolver());
        assertNull(root.getDefaultNamespaceURI());
    }

    // Covers getParent(): loop skips a parent pointer for which isContainer() is true
    @Test
    public void testGetParent_skipsContainerParent() throws Throwable {
        StubNodePointer grandparent = new StubNodePointer(null);
        StubNodePointer containerParent = new StubNodePointer(grandparent);
        containerParent.setContainerFlagValue(true);
        StubNodePointer child = new StubNodePointer(containerParent);
        assertSame(grandparent, child.getParent());
    }

    // Covers getParent(): loop does not run when immediate parent is not a container
    @Test
    public void testGetParent_nonContainerParent_returnsDirectParent() throws Throwable {
        StubNodePointer parent = new StubNodePointer(null);
        StubNodePointer child = new StubNodePointer(parent);
        assertSame(parent, child.getParent());
    }

    // Covers getImmediateParentPointer(): returns raw parent field even if it is a container
    @Test
    public void testGetImmediateParentPointer_returnsRawParentEvenIfContainer() throws Throwable {
        StubNodePointer containerParent = new StubNodePointer(null);
        containerParent.setContainerFlagValue(true);
        StubNodePointer child = new StubNodePointer(containerParent);
        assertSame(containerParent, child.getImmediateParentPointer());
    }

    // Covers setAttribute/isAttribute default false and set true
    @Test
    public void testSetAttributeIsAttribute() throws Throwable {
        assertFalse(root.isAttribute());
        root.setAttribute(true);
        assertTrue(root.isAttribute());
    }

    // Covers isRoot(): true when parent null, false when parent set
    @Test
    public void testIsRoot_rootAndNonRoot() throws Throwable {
        assertTrue(root.isRoot());
        StubNodePointer child = new StubNodePointer(root);
        assertFalse(child.isRoot());
    }

    // Covers isNode() delegation to !isContainer() for both container states
    @Test
    public void testIsNodeAndIsContainer_defaultAndOverridden() throws Throwable {
        assertFalse(root.isContainer());
        assertTrue(root.isNode());
        root.setContainerFlagValue(true);
        assertTrue(root.isContainer());
        assertFalse(root.isNode());
    }

    // Covers getIndex default WHOLE_COLLECTION and setIndex updates value
    @Test
    public void testGetIndexSetIndex_defaultIsWholeCollectionAndUpdatable() throws Throwable {
        assertEquals(NodePointer.WHOLE_COLLECTION, root.getIndex());
        root.setIndex(5);
        assertEquals(5, root.getIndex());
    }

    // Covers isActual(): index == WHOLE_COLLECTION branch always true, regardless of length
    @Test
    public void testIsActual_wholeCollectionDefault_true() throws Throwable {
        root.setLengthValue(0);
        assertTrue(root.isActual());
    }

    // Covers isActual(): index within bounds true, index at length boundary false, negative index false
    @Test
    public void testIsActual_indexBoundaries_withinAtAndNegative() throws Throwable {
        root.setLengthValue(3);
        root.setIndex(2);
        assertTrue(root.isActual());
        root.setIndex(3);
        assertFalse(root.isActual());
        root.setIndex(-1);
        assertFalse(root.isActual());
    }

    // Covers getImmediateValuePointer() and getValuePointer() default return self
    @Test
    public void testGetImmediateValuePointerAndGetValuePointer_default_returnsSelf() throws Throwable {
        assertSame(root, root.getImmediateValuePointer());
        assertSame(root, root.getValuePointer());
    }

    // Covers getNode() and getValue(): when valuePointer == this, returns getImmediateNode()
    @Test
    public void testGetNodeAndGetValue_returnImmediateNode() throws Throwable {
        root.setImmediateNodeValue("nodeX");
        assertEquals("nodeX", root.getNode());
        assertEquals("nodeX", root.getValue());
    }

    // Covers deprecated getNodeValue() delegates to getNode()
    @Test
    public void testGetNodeValue_deprecated_delegatesToGetNode() throws Throwable {
        root.setImmediateNodeValue("nodeY");
        assertEquals("nodeY", root.getNodeValue());
    }

    // Covers getRootNode(): parent == null branch returns own immediate node
    @Test
    public void testGetRootNode_noParent_returnsOwnImmediateNode() throws Throwable {
        root.setImmediateNodeValue("rootObj");
        assertEquals("rootObj", root.getRootNode());
    }

    // Covers getRootNode(): parent != null branch delegates to parent's root node
    @Test
    public void testGetRootNode_withParent_returnsParentRootNode() throws Throwable {
        StubNodePointer parent = new StubNodePointer(null);
        parent.setImmediateNodeValue("parentNode");
        StubNodePointer child = new StubNodePointer(parent);
        child.setImmediateNodeValue("childNode");
        assertEquals("parentNode", child.getRootNode());
    }

    // Covers testNode(): test == null branch returns true
    @Test
    public void testTestNode_nullTest_true() throws Throwable {
        assertTrue(root.testNode(null));
    }

    // Covers createPath(context,value) sets value and returns this; createPath(context) returns this
    @Test
    public void testCreatePath_withAndWithoutValue() throws Throwable {
        NodePointer result = root.createPath(null, "v1");
        assertSame(root, result);
        assertEquals("v1", root.getLastSetValue());
        NodePointer result2 = root.createPath(null);
        assertSame(root, result2);
    }

    // Covers remove(): documented as a no-op, state must remain unchanged
    @Test
    public void testRemove_noOpDoesNotChangeState() throws Throwable {
        root.setIndex(4);
        root.remove();
        assertEquals(4, root.getIndex());
    }

    // Covers both createChild overloads: base implementation always throws JXPathException
    @Test
    public void testCreateChild_bothOverloads_throwJXPathException() throws Throwable {
        try {
            root.createChild(null, null, 0, "v");
            fail("expected JXPathException");
        } catch (JXPathException expected) {
            assertTrue(expected.getMessage().contains("Cannot create"));
        }
        try {
            root.createChild(null, null, 0);
            fail("expected JXPathException");
        } catch (JXPathException expected) {
            assertTrue(expected.getMessage().contains("Cannot create"));
        }
    }

    // Covers createAttribute(): base implementation always throws JXPathException
    @Test
    public void testCreateAttribute_throwsJXPathException() throws Throwable {
        try {
            root.createAttribute(null, null);
            fail("expected JXPathException");
        } catch (JXPathException expected) {
            assertTrue(expected.getMessage().contains("Cannot create"));
        }
    }

    // Covers getLocale(): own locale explicitly set via two-arg constructor is returned
    @Test
    public void testGetLocale_ownLocaleSet_returnsOwn() throws Throwable {
        StubNodePointer p = new StubNodePointer(null, Locale.US);
        assertEquals(Locale.US, p.getLocale());
    }

    // Covers getLocale(): own locale null, falls back to parent's locale
    @Test
    public void testGetLocale_inheritsFromParent_whenOwnNull() throws Throwable {
        StubNodePointer parent = new StubNodePointer(null, Locale.FRENCH);
        StubNodePointer child = new StubNodePointer(parent);
        assertEquals(Locale.FRENCH, child.getLocale());
    }

    // Covers isLanguage(): matching case-insensitive prefix returns true
    @Test
    public void testIsLanguage_matchingPrefix_true() throws Throwable {
        StubNodePointer p = new StubNodePointer(null, Locale.US);
        assertTrue(p.isLanguage("en"));
    }

    // Covers isLanguage(): non-matching prefix returns false
    @Test
    public void testIsLanguage_nonMatchingPrefix_false() throws Throwable {
        StubNodePointer p = new StubNodePointer(null, Locale.US);
        assertFalse(p.isLanguage("fr"));
    }

    // Covers childIterator, attributeIterator, namespaceIterator, namespacePointer default null behavior
    @Test
    public void testIterators_default_returnNull() throws Throwable {
        assertNull(root.childIterator(null, false, null));
        assertNull(root.attributeIterator(null));
        assertNull(root.namespaceIterator());
        assertNull(root.namespacePointer("prefix"));
    }

    // Covers getNamespaceURI(String) and getNamespaceURI() default null
    @Test
    public void testGetNamespaceURI_bothOverloads_returnNull() throws Throwable {
        assertNull(root.getNamespaceURI("prefix"));
        assertNull(root.getNamespaceURI());
    }

    // Covers isDefaultNamespace(): null prefix branch true, non-null prefix branch false (URI unresolved)
    @Test
    public void testIsDefaultNamespace_nullAndNonNullPrefix() throws Throwable {
        assertTrue(root.isDefaultNamespace(null));
        assertFalse(root.isDefaultNamespace("ns1"));
    }

    // Covers asPath(): root pointer, no attribute flag, no index bracket
    @Test
    public void testAsPath_rootNoAttribute() throws Throwable {
        assertEquals("/null", root.asPath());
    }

    // Covers asPath(): attribute flag prepends '@'
    @Test
    public void testAsPath_withAttributeFlag() throws Throwable {
        root.setAttribute(true);
        assertEquals("/@null", root.asPath());
    }

    // Covers asPath(): collection index 0 displayed as [1] (index starts at 0, displayed from 1)
    @Test
    public void testAsPath_collectionIndexZero_appendsBracketOne() throws Throwable {
        root.setCollectionFlag(true);
        root.setIndex(0);
        assertEquals("/null[1]", root.asPath());
    }

    // Covers asPath(): collection index 2 displayed as [3]
    @Test
    public void testAsPath_collectionIndexTwo_appendsBracketThree() throws Throwable {
        root.setCollectionFlag(true);
        root.setIndex(2);
        assertEquals("/null[3]", root.asPath());
    }

    // Covers asPath(): index set but isCollection() false -> no bracket appended
    @Test
    public void testAsPath_nonCollectionIndexed_noBracket() throws Throwable {
        root.setCollectionFlag(false);
        root.setIndex(2);
        assertEquals("/null", root.asPath());
    }

    // Covers asPath(): parent.isContainer() true -> delegates entirely to parent.asPath()
    @Test
    public void testAsPath_parentIsContainer_delegatesToParentPath() throws Throwable {
        StubNodePointer parent = new StubNodePointer(null);
        parent.setContainerFlagValue(true);
        StubNodePointer child = new StubNodePointer(parent);
        assertEquals(parent.asPath(), child.asPath());
        assertEquals("/null", child.asPath());
    }

    // Covers toString() delegates to asPath()
    @Test
    public void testToString_delegatesToAsPath() throws Throwable {
        assertEquals(root.asPath(), root.toString());
    }



    // Covers compareTo(): same object, same (null) parent -> returns 0 directly
    @Test
    public void testCompareTo_samePointer_zero() throws Throwable {
        assertEquals(0, root.compareTo(root));
    }

    // Covers compareTo(): parent == pointer.parent branch delegates to parent.compareChildNodePointers
    @Test
    public void testCompareTo_sameParent_delegatesToCompareChildNodePointers() throws Throwable {
        StubNodePointer parent = new StubNodePointer(null);
        parent.setCompareResultValue(-7);
        StubNodePointer a = new StubNodePointer(parent);
        StubNodePointer b = new StubNodePointer(parent);
        assertEquals(-7, a.compareTo(b));
    }



    // Covers compareTo(): ancestor/descendant comparison must be antisymmetric in sign
    @Test
    public void testCompareTo_ancestorDescendant_antisymmetric() throws Throwable {
        StubNodePointer r = new StubNodePointer(null);
        StubNodePointer mid = new StubNodePointer(r);
        StubNodePointer leaf = new StubNodePointer(mid);
        int r1 = leaf.compareTo(r);
        int r2 = r.compareTo(leaf);
        assertTrue((r1 > 0 && r2 < 0) || (r1 < 0 && r2 > 0));
    }
}
