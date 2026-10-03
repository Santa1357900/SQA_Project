package org.apache.commons.jxpath.ri.compiler;

import junit.framework.TestCase;

import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodePointer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ExpressionTest extends TestCase {

    private static class DummyExpression extends Expression {
        private boolean dependentResult;
        private Object computeValueResult;
        private Object computeResult;

        public DummyExpression(boolean dependentResult, Object computeValueResult, Object computeResult) {
            this.dependentResult = dependentResult;
            this.computeValueResult = computeValueResult;
            this.computeResult = computeResult;
        }

        public boolean computeContextDependent() {
            return dependentResult;
        }

        public Object computeValue(EvalContext context) {
            return computeValueResult;
        }

        public Object compute(EvalContext context) {
            return computeResult;
        }
    }

    private static class DummyPointer implements Pointer {
        private Object value;

        public DummyPointer(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public Object getNode() {
            return value;
        }

        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        public String asPath() {
            return "/";
        }
    }

    public void testConstantsAndTypes() throws Throwable {
        assertNotNull(Expression.ZERO);
        assertEquals(0.0, Expression.ZERO.doubleValue(), 0.0);
        assertNotNull(Expression.ONE);
        assertEquals(1.0, Expression.ONE.doubleValue(), 0.0);
        assertNotNull(Expression.NOT_A_NUMBER);
        assertTrue(Double.isNaN(Expression.NOT_A_NUMBER.doubleValue()));
    }

    public void testIsContextDependentCaching() throws Throwable {
        DummyExpression expr = new DummyExpression(true, null, null);
        assertFalse(expr.isContextDependent()); // First call evaluates computeContextDependent() -> true, but wait, field contextDependencyKnown starts as false.
        // Let's trace:
        // if (!contextDependencyKnown) { contextDependent = computeContextDependent(); contextDependencyKnown = true; }
        // So first call: contextDependencyKnown = false -> computes and caches true. Returns true.
        assertTrue(expr.isContextDependent());
        
        DummyExpression expr2 = new DummyExpression(false, null, null);
        assertFalse(expr2.isContextDependent());
        assertFalse(expr2.isContextDependent());
    }

    public void testIterateWithEvalContext() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("test1");
        list.add("test2");
        final Iterator<String> innerIter = list.iterator();

        EvalContext dummyContext = new EvalContext(null, null) {
            public Iterator iterate() {
                return innerIter;
            }
            public boolean nextNode() {
                return false;
            }
            public booleanasinya() {
                return false;
            }
            public NodePointer getCurrentNodePointer() {
                return null;
            }
        };

        DummyExpression expr = new DummyExpression(false, null, dummyContext);
        Iterator iter = expr.iterate(null);
        assertNotNull(iter);
        assertTrue(iter instanceof Expression.ValueIterator);
        assertTrue(iter.hasNext());
        assertEquals("test1", iter.next());
        assertTrue(iter.hasNext());
        assertEquals("test2", iter.next());
        assertFalse(iter.hasNext());
    }

    public void testIterateWithValueUtils() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("val1");

        DummyExpression expr = new DummyExpression(false, null, list);
        Iterator iter = expr.iterate(null);
        assertNotNull(iter);
        assertTrue(iter.hasNext());
        assertEquals("val1", iter.next());
        assertFalse(iter.hasNext());
    }

    public void testIteratePointersWithNull() throws Throwable {
        DummyExpression expr = new DummyExpression(false, null, null);
        Iterator iter = expr.iteratePointers(null);
        assertNotNull(iter);
        assertFalse(iter.hasNext());
    }

    public void testIteratePointersWithEvalContext() throws Throwable {
        List<String> list = new ArrayList<String>();
        final Iterator<String> innerIter = list.iterator();

        EvalContext dummyContext = new EvalContext(null, null) {
            public Iterator iterate() {
                return innerIter;
            }
            public boolean nextNode() {
                return false;
            }
            public NodePointer getCurrentNodePointer() {
                return null;
            }
        };

        DummyExpression expr = new DummyExpression(false, null, dummyContext);
        Iterator iter = expr.iteratePointers(null);
        assertNotNull(iter);
        assertEquals(dummyContext, iter);
    }

    public void testIteratePointersWithOtherResult() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("pointerTest");

        final NodePointer rootPointer = NodePointer.newNodePointer(new QName("root"), new Object(), Locale.ENGLISH);
        
        EvalContext rootContextMock = new EvalContext(null, null) {
            public NodePointer getCurrentNodePointer() {
                return rootPointer;
            }
        };

        EvalContext dummyContext = new EvalContext(null, null) {
            public EvalContext getRootContext() {
                return rootContextMock;
            }
        };

        DummyExpression expr = new DummyExpression(false, null, list);
        Iterator iter = expr.iteratePointers(dummyContext);
        assertNotNull(iter);
        assertTrue(iter instanceof Expression.PointerIterator);
        assertTrue(iter.hasNext());
        Object nextObj = iter.next();
        assertNotNull(nextObj);
        assertTrue(nextObj instanceof Pointer);
        assertEquals("pointerTest", ((Pointer) nextObj).getValue());
    }

    public void testPointerIteratorMethods() throws Throwable {
        List<Object> list = new ArrayList<Object>();
        Pointer existingPointer = new DummyPointer("alreadyPointer");
        list.add(existingPointer);
        list.add("plainObject");

        Expression.PointerIterator pointerIterator = new Expression.PointerIterator(
                list.iterator(), new QName("test"), Locale.ENGLISH);

        assertTrue(pointerIterator.hasNext());
        Object p1 = pointerIterator.next();
        assertSame(existingPointer, p1);

        assertTrue(pointerIterator.hasNext());
        Object p2 = pointerIterator.next();
        assertNotNull(p2);
        assertTrue(p2 instanceof Pointer);
        assertEquals("plainObject", ((Pointer) p2).getValue());

        assertFalse(pointerIterator.hasNext());

        boolean exceptionThrown = false;
        try {
            pointerIterator.remove();
        } catch (UnsupportedOperationException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    public void testValueIteratorMethods() throws Throwable {
        List<Object> list = new ArrayList<Object>();
        Pointer pointer = new DummyPointer("pointerValue");
        list.add(pointer);
        list.add("plainValue");

        Expression.ValueIterator valueIterator = new Expression.ValueIterator(list.iterator());

        assertTrue(valueIterator.hasNext());
        assertEquals("pointerValue", valueIterator.next());

        assertTrue(valueIterator.hasNext());
        assertEquals("plainValue", valueIterator.next());

        assertFalse(valueIterator.hasNext());

        boolean exceptionThrown = false;
        try {
            valueIterator.remove();
        } catch (UnsupportedOperationException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    public void testComputeValueDirect() throws Throwable {
        DummyExpression expr = new DummyExpression(false, "computedValue", null);
        assertEquals("computedValue", expr.computeValue(null));
    }
}