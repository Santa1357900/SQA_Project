package org.apache.commons.jxpath.ri.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.SelfContext;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;
import org.junit.Test;

import static org.junit.Assert.*;

public class CoreOperationCompareTest {

    private static class DummyExpression extends Expression {
        private final Object value;

        public DummyExpression(Object value) {
            super();
            this.value = value;
        }

        public Object compute(EvalContext context) {
            return value;
        }

        public Object computeValue(EvalContext context) {
            return value;
        }

        public boolean computeContextDependency() {
            return false;
        }
    }

    private static class ConcreteCoreOperationCompare extends CoreOperationCompare {
        public ConcreteCoreOperationCompare(Expression arg1, Expression arg2) {
            super(arg1, arg2);
        }

        public boolean testEqual(EvalContext context, Expression left, Expression right) {
            return super.equal(context, left, right);
        }

        public boolean testEqualObjects(Object l, Object r) {
            return super.equal(l, r);
        }

        public boolean testContains(Iterator it, Object value) {
            return super.contains(it, value);
        }

        public boolean testFindMatch(Iterator lit, Iterator rit) {
            return super.findMatch(lit, rit);
        }

        public int testGetPrecedence() {
            return super.getPrecedence();
        }

        public boolean testIsSymmetric() {
            return super.isSymmetric();
        }
    }

    @Test
    public void testPrecedenceAndSymmetric() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new DummyExpression("a"), new DummyExpression("b")
        );
        assertEquals(2, op.testGetPrecedence());
        assertTrue(op.testIsSymmetric());
    }

    @Test
    public void testEqualObjectsPrimitiveAndNull() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new DummyExpression(null), new DummyExpression(null)
        );

        assertTrue(op.testEqualObjects(null, null));
        assertTrue(op.testEqualObjects(Boolean.TRUE, Boolean.TRUE));
        assertFalse(op.testEqualObjects(Boolean.TRUE, Boolean.FALSE));
        assertTrue(op.testEqualObjects(Double.valueOf(5.0), Double.valueOf(5.0)));
        assertFalse(op.testEqualObjects(Double.valueOf(5.0), Double.valueOf(6.0)));
        assertTrue(op.testEqualObjects("test", "test"));
        assertFalse(op.testEqualObjects("test", "other"));
        assertFalse(op.testEqualObjects(null, "test"));
        assertFalse(op.testEqualObjects("test", null));
        
        Object obj1 = new Object();
        assertTrue(op.testEqualObjects(obj1, obj1));
        assertFalse(op.testEqualObjects(obj1, new Object()));
    }

    @Test
    public void testEqualObjectsNaN() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new DummyExpression(null), new DummyExpression(null)
        );
        assertFalse(op.testEqualObjects(Double.valueOf(Double.NaN), Double.valueOf(Double.NaN)));
        assertFalse(op.testEqualObjects(Double.valueOf(Double.NaN), Double.valueOf(5.0)));
    }

    @Test
    public void testEqualObjectsPointers() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new DummyExpression(null), new DummyExpression(null)
        );
        NodePointer p1 = NodePointer.newNodePointer(null, "val", null);
        NodePointer p2 = NodePointer.newNodePointer(null, "val", null);
        NodePointer p3 = NodePointer.newNodePointer(null, "other", null);

        assertTrue(op.testEqualObjects(p1, p1));
        assertTrue(op.testEqualObjects(p1, p2));
        assertFalse(op.testEqualObjects(p1, p3));
    }

    @Test
    public void testContainsAndFindMatch() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new DummyExpression(null), new DummyExpression(null)
        );

        List<String> list1 = new ArrayList<String>();
        list1.add("apple");
        list1.add("banana");

        assertTrue(op.testContains(list1.iterator(), "banana"));
        assertFalse(op.testContains(list1.iterator(), "cherry"));

        List<String> list2 = new ArrayList<String>();
        list2.add("banana");
        list2.add("date");

        assertTrue(op.testFindMatch(list1.iterator(), list2.iterator()));

        List<String> list3 = new ArrayList<String>();
        list3.add("cherry");
        assertFalse(op.testFindMatch(list1.iterator(), list3.iterator()));
    }

    @Test
    public void testEqualWithContextAndCollections() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new DummyExpression(null), new DummyExpression(null)
        );

        List<String> leftList = new ArrayList<String>();
        leftList.add("a");
        List<String> rightList = new ArrayList<String>();
        rightList.add("a");

        DummyExpression leftExpr = new DummyExpression(leftList);
        DummyExpression rightExpr = new DummyExpression(rightList);

        assertTrue(op.testEqual(null, leftExpr, rightExpr));

        DummyExpression leftSingle = new DummyExpression("a");
        DummyExpression rightCol = new DummyExpression(rightList);
        assertTrue(op.testEqual(null, leftSingle, rightCol));
        assertTrue(op.testEqual(null, rightCol, leftSingle));
    }

    @Test
    public void testSpecialContexts() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new DummyExpression(null), new DummyExpression(null)
        );

        NullPointer np = new NullPointer(null, null);
        SelfContext selfContext = new SelfContext(null, np);
        DummyExpression leftExpr = new DummyExpression(selfContext);
        DummyExpression rightExpr = new DummyExpression(null);

        boolean result = op.testEqual(null, leftExpr, rightExpr);
        assertFalse(result);
    }
}