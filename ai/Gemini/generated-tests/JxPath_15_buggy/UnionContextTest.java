package org.apache.commons.jxpath.ri.axes;

import java.util.ArrayList;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.jxpath.BasicNodeSet;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.JXPathContextReferenceImpl;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;

public class UnionContextTest {

    @Test
    public void testGetDocumentOrderSingleContext() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext parent = new InitialContext(new EvalContext(null, null) {
            public NodePointer getCurrentNodePointer() {
                return null;
            }
            public boolean nextNode() {
                return false;
            }
            public boolean nextSet() {
                return false;
            }
        });

        EvalContext[] subContexts = new EvalContext[] {
            new InitialContext(parent)
        };

        UnionContext unionContext = new UnionContext(parent, subContexts);
        int order = unionContext.getDocumentOrder();
        assertEquals(parent.getDocumentOrder(), order);
    }

    @Test
    public void testGetDocumentOrderMultipleContexts() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext parent = new InitialContext(new EvalContext(null, null) {
            public NodePointer getCurrentNodePointer() {
                return null;
            }
            public boolean nextNode() {
                return false;
            }
            public boolean nextSet() {
                return false;
            }
        });

        EvalContext[] subContexts = new EvalContext[] {
            new InitialContext(parent),
            new InitialContext(parent)
        };

        UnionContext unionContext = new UnionContext(parent, subContexts);
        int order = unionContext.getDocumentOrder();
        assertEquals(1, order);
    }

    @Test
    public void testSetPositionPreparationAndDuplicates() throws Throwable {
        Object root = new Object();
        JXPathContextReferenceImpl jxpathContext = (JXPathContextReferenceImpl) JXPathContext.newContext(root);
        NodePointer rootPointer = NodePointer.newNodePointer(new QName("root"), root, null);
        EvalContext parent = new RootContext(jxpathContext, rootPointer);

        final NodePointer ptr1 = NodePointer.newNodePointer(new QName("a"), "valA", null);
        final NodePointer ptr2 = NodePointer.newNodePointer(new QName("b"), "valB", null);

        EvalContext ctx1 = new EvalContext(parent, null) {
            private int state = 0;
            public boolean nextSet() {
                if (state == 0) {
                    state++;
                    return true;
                }
                return false;
            }
            public boolean nextNode() {
                if (state == 1) {
                    state++;
                    return true;
                } else if (state == 2) {
                    state++;
                    return true;
                }
                return false;
            }
            public NodePointer getCurrentNodePointer() {
                if (state == 2) {
                    return ptr1;
                } else if (state == 3) {
                    return ptr2;
                }
                return null;
            }
        };

        EvalContext ctx2 = new EvalContext(parent, null) {
            private boolean setCalled = false;
            private boolean nodeCalled = false;
            public boolean nextSet() {
                if (!setCalled) {
                    setCalled = true;
                    return true;
                }
                return false;
            }
            public boolean nextNode() {
                if (!nodeCalled) {
                    nodeCalled = true;
                    return true;
                }
                return false;
            }
            public NodePointer getCurrentNodePointer() {
                return ptr1; // Duplicate of ptr1 to test deduplication
            }
        };

        EvalContext[] subContexts = new EvalContext[] { ctx1, ctx2 };
        UnionContext unionContext = new UnionContext(parent, subContexts);

        boolean result = unionContext.setPosition(1);
        assertTrue(result);
        assertEquals(ptr1, unionContext.getCurrentNodePointer());

        boolean result2 = unionContext.setPosition(2);
        assertTrue(result2);
        assertEquals(ptr2, unionContext.getCurrentNodePointer());

        boolean result3 = unionContext.setPosition(3);
        assertFalse(result3);
    }

    @Test
    public void testEmptyContextsUnion() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext parent = new InitialContext(new EvalContext(null, null) {
            public NodePointer getCurrentNodePointer() {
                return null;
            }
            public boolean nextNode() {
                return false;
            }
            public boolean nextSet() {
                return false;
            }
        });

        EvalContext[] subContexts = new EvalContext[0];
        UnionContext unionContext = new UnionContext(parent, subContexts);

        assertEquals(0, unionContext.getDocumentOrder());
        assertFalse(unionContext.setPosition(1));
    }
}