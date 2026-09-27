package org.apache.commons.jxpath.ri.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.SelfContext;
import org.apache.commons.jxpath.ri.axes.RootContext;
import org.apache.commons.jxpath.ri.JXPathContextReferenceImpl;
import org.apache.commons.jxpath.JXPathContext;

public class CoreOperationRelationalExpressionTest {

    private static class ConcreteRelationalExpression extends CoreOperationRelationalExpression {
        private final boolean compareResult;

        public ConcreteRelationalExpression(Expression[] args, boolean compareResult) {
            super(args);
            this.compareResult = compareResult;
        }

        protected boolean evaluateCompare(int compare) {
            return compareResult;
        }
    }

    private static class DummyExpression extends Expression {
        private final Object value;

        public DummyExpression(Object value) {
            this.value = value;
        }

        public Object computeValue(EvalContext context) {
            return value;
        }

        public Object compute(EvalContext context) {
            return value;
        }

        protected int getPrecedence() {
            return 0;
        }

        public boolean computeContexts(EvalContext context) {
            return false;
        }
    }

    @Test
    public void testPrecedenceAndSymmetric() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(Integer.valueOf(1)),
            new DummyExpression(Integer.valueOf(2))
        };
        ConcreteRelationalExpression expr = new ConcreteRelationalExpression(args, true);

        assertEquals(3, expr.getPrecedence());
        assertFalse(expr.isSymmetric());
    }

    @Test
    public void testComputeValueBasicNumbers() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(Integer.valueOf(5)),
            new DummyExpression(Integer.valueOf(10))
        };
        ConcreteRelationalExpression expr = new ConcreteRelationalExpression(args, true);

        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        Object result = expr.computeValue(evalContext);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testNaNHandling() throws Throwable {
        Expression[] args1 = new Expression[] {
            new DummyExpression("not-a-number"),
            new DummyExpression(Integer.valueOf(5))
        };
        ConcreteRelationalExpression expr1 = new ConcreteRelationalExpression(args1, true);

        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        assertEquals(Boolean.FALSE, expr1.computeValue(evalContext));

        Expression[] args2 = new Expression[] {
            new DummyExpression(Integer.valueOf(5)),
            new DummyExpression("not-a-number")
        };
        ConcreteRelationalExpression expr2 = new ConcreteRelationalExpression(args2, true);

        assertEquals(Boolean.FALSE, expr2.computeValue(evalContext));
    }

    @Test
    public void testCollectionAndIteratorMatching() throws Throwable {
        List<Integer> list1 = new ArrayList<Integer>();
        list1.add(Integer.valueOf(1));
        list1.add(Integer.valueOf(2));

        List<Integer> list2 = new ArrayList<Integer>();
        list2.add(Integer.valueOf(2));
        list2.add(Integer.valueOf(3));

        Expression[] args = new Expression[] {
            new DummyExpression(list1),
            new DummyExpression(list2)
        };
        ConcreteRelationalExpression expr = new ConcreteRelationalExpression(args, true);

        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        assertEquals(Boolean.TRUE, expr.computeValue(evalContext));
    }

    @Test
    public void testIteratorAndSingleObjectMatching() throws Throwable {
        List<Integer> list = new ArrayList<Integer>();
        list.add(Integer.valueOf(10));

        Expression[] args = new Expression[] {
            new DummyExpression(list),
            new DummyExpression(Integer.valueOf(10))
        };
        ConcreteRelationalExpression expr = new ConcreteRelationalExpression(args, true);

        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        assertEquals(Boolean.TRUE, expr.computeValue(evalContext));

        Expression[] argsReverse = new Expression[] {
            new DummyExpression(Integer.valueOf(10)),
            new DummyExpression(list)
        };
        ConcreteRelationalExpression exprReverse = new ConcreteRelationalExpression(argsReverse, true);

        assertEquals(Boolean.TRUE, exprReverse.computeValue(evalContext));
    }

    @Test
    public void testInitialContextReset() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext rootContext = new RootContext((JXPathContextReferenceImpl) context, null);
        InitialContext initContext = new InitialContext(rootContext);

        Expression[] args = new Expression[] {
            new DummyExpression(initContext),
            new DummyExpression(Integer.valueOf(1))
        };
        ConcreteRelationalExpression expr = new ConcreteRelationalExpression(args, false);

        assertEquals(Boolean.FALSE, expr.computeValue(rootContext));
    }

    @Test
    public void testSelfContextReduction() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new Object());
        EvalContext rootContext = new RootContext((JXPathContextReferenceImpl) context, null);
        SelfContext selfContext = new SelfContext(rootContext, null);

        Expression[] args = new Expression[] {
            new DummyExpression(selfContext),
            new DummyExpression(Integer.valueOf(0))
        };
        ConcreteRelationalExpression expr = new ConcreteRelationalExpression(args, true);

        Object result = expr.computeValue(rootContext);
        assertNotNull(result);
    }
}