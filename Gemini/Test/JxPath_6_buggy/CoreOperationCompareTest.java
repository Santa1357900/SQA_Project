package org.apache.commons.jxpath.ri.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.SelfContext;
import org.apache.commons.jxpath.ri.model.NodePointer;

public class CoreOperationCompareTest extends TestCase {

    private static class ConcreteCoreOperationCompare extends CoreOperationCompare {
        public ConcreteCoreOperationCompare(Expression arg1, Expression arg2) {
            super(arg1, arg2);
        }

        public Object computeValue(EvalContext context) {
            return null;
        }

        public int getOpCode() {
            return 0;
        }

        public boolean publicEqual(EvalContext context, Expression left, Expression right) {
            return equal(context, left, right);
        }

        public boolean publicEqualObjects(Object l, Object r) {
            return equal(l, r);
        }

        public boolean publicContains(Iterator it, Object value) {
            return contains(it, value);
        }

        public boolean publicFindMatch(Iterator lit, Iterator rit) {
            return findMatch(lit, rit);
        }
    }

    private static class DummyExpression extends Expression {
        private final Object value;

        public DummyExpression(Object value) {
            this.value = value;
        }

        public Object compute(EvalContext context) {
            return value;
        }

        public Object computeValue(EvalContext context) {
            return value;
        }

        public int getOpCode() {
            return 0;
        }

        public boolean computeContextDependent() {
            return false;
        }
    }

    public void testEqualObjectsPrimitiveAndString() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new Constant("a"), new Constant("b")
        );

        assertTrue(op.publicEqualObjects(Boolean.TRUE, Boolean.TRUE));
        assertFalse(op.publicEqualObjects(Boolean.TRUE, Boolean.FALSE));

        assertTrue(op.publicEqualObjects(Double.valueOf(5.0), Integer.valueOf(5)));
        assertFalse(op.publicEqualObjects(Double.valueOf(5.0), Integer.valueOf(6)));

        assertTrue(op.publicEqualObjects("test", "test"));
        assertFalse(op.publicEqualObjects("test", "other"));

        assertTrue(op.publicEqualObjects(null, null));
        assertFalse(op.publicEqualObjects("test", null));
        assertFalse(op.publicEqualObjects(null, "test"));

        Object obj1 = new Object();
        assertTrue(op.publicEqualObjects(obj1, obj1));
        assertFalse(op.publicEqualObjects(obj1, new Object()));
    }

    public void testEqualContextExpressions() throws Throwable {
        DummyExpression leftExpr = new DummyExpression(Boolean.TRUE);
        DummyExpression rightExpr = new DummyExpression(Boolean.TRUE);
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(leftExpr, rightExpr);

        assertTrue(op.publicEqual(null, leftExpr, rightExpr));
    }

    public void testEqualCollectionsAndIterators() throws Throwable {
        List<String> leftList = new ArrayList<String>();
        leftList.add("match");
        leftList.add("other");

        List<String> rightList = new ArrayList<String>();
        rightList.add("match");

        DummyExpression leftExpr = new DummyExpression(leftList);
        DummyExpression rightExpr = new DummyExpression("match");
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(leftExpr, rightExpr);

        assertTrue(op.publicEqual(null, leftExpr, rightExpr));

        DummyExpression rightExprList = new DummyExpression(rightList);
        ConcreteCoreOperationCompare op2 = new ConcreteCoreOperationCompare(leftExpr, rightExprList);
        assertTrue(op2.publicEqual(null, leftExpr, rightExprList));
    }

    public void testContainsAndFindMatch() throws Throwable {
        ConcreteCoreOperationCompare op = new ConcreteCoreOperationCompare(
            new Constant("a"), new Constant("b")
        );

        List<String> list = new ArrayList<String>();
        list.add("val1");
        list.add("val2");

        assertTrue(op.publicContains(list.iterator(), "val2"));
        assertFalse(op.publicContains(list.iterator(), "val3"));

        List<String> list1 = new ArrayList<String>();
        list1.add("A");
        list1.add("B");

        List<String> list2 = new ArrayList<String>();
        list2.add("C");
        list2.add("B");

        assertTrue(op.publicFindMatch(list1.iterator(), list2.iterator()));

        List<String> list3 = new ArrayList<String>();
        list3.add("X");
        assertFalse(op.publicFindMatch(list1.iterator(), list3.iterator()));
    }
}