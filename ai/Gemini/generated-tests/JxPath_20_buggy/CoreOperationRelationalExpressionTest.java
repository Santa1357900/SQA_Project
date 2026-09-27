package org.apache.commons.jxpath.ri.compiler;

import junit.framework.TestCase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.SelfContext;

public class CoreOperationRelationalExpressionTest extends TestCase {

    private static class ConcreteRelationalExpression extends CoreOperationRelationalExpression {
        private final int expectedCompareResult;

        public ConcreteRelationalExpression(Expression[] args, int expectedCompareResult) {
            super(args);
            this.expectedCompareResult = expectedCompareResult;
        }

        protected boolean evaluateCompare(int compare) {
            return compare == expectedCompareResult;
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

        public boolean computeContextDependent() {
            return false;
        }
    }

    public void testPrecedenceAndSymmetric() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(Integer.valueOf(1)),
            new DummyExpression(Integer.valueOf(2))
        };
        ConcreteRelationalExpression expr = new ConcreteRelationalExpression(args, -1);
        assertEquals(Expression.RELATIONAL_EXPR_PRECEDENCE, expr.getPrecedence());
        assertEquals(false, expr.isSymmetric());
    }

    public void testComputeValueBasicNumbers() throws Throwable {
        Expression[] args1 = new Expression[] {
            new DummyExpression(Integer.valueOf(1)),
            new DummyExpression(Integer.valueOf(2))
        };
        ConcreteRelationalExpression expr1 = new ConcreteRelationalExpression(args1, -1);
        assertEquals(Boolean.TRUE, expr1.computeValue(null));

        Expression[] args2 = new Expression[] {
            new DummyExpression(Integer.valueOf(5)),
            new DummyExpression(Integer.valueOf(2))
        };
        ConcreteRelationalExpression expr2 = new ConcreteRelationalExpression(args2, 1);
        assertEquals(Boolean.TRUE, expr2.computeValue(null));

        Expression[] args3 = new Expression[] {
            new DummyExpression(Integer.valueOf(3)),
            new DummyExpression(Integer.valueOf(3))
        };
        ConcreteRelationalExpression expr3 = new ConcreteRelationalExpression(args3, 0);
        assertEquals(Boolean.TRUE, expr3.computeValue(null));
    }

    public void testComputeValueNaN() throws Throwable {
        Expression[] args1 = new Expression[] {
            new DummyExpression(Double.valueOf(Double.NaN)),
            new DummyExpression(Integer.valueOf(2))
        };
        ConcreteRelationalExpression expr1 = new ConcreteRelationalExpression(args1, -1);
        assertEquals(Boolean.FALSE, expr1.computeValue(null));

        Expression[] args2 = new Expression[] {
            new DummyExpression(Integer.valueOf(2)),
            new DummyExpression(Double.valueOf(Double.NaN))
        };
        ConcreteRelationalExpression expr2 = new ConcreteRelationalExpression(args2, -1);
        assertEquals(Boolean.FALSE, expr2.computeValue(null));
    }

    public void testComputeValueCollectionsAndIterators() throws Throwable {
        Collection<Integer> leftCol = new ArrayList<Integer>();
        leftCol.add(Integer.valueOf(1));
        leftCol.add(Integer.valueOf(5));

        Expression[] args1 = new Expression[] {
            new DummyExpression(leftCol),
            new DummyExpression(Integer.valueOf(5))
        };
        ConcreteRelationalExpression expr1 = new ConcreteRelationalExpression(args1, 0);
        assertEquals(Boolean.TRUE, expr1.computeValue(null));

        Collection<Integer> rightCol = new ArrayList<Integer>();
        rightCol.add(Integer.valueOf(5));

        Expression[] args2 = new Expression[] {
            new DummyExpression(Integer.valueOf(5)),
            new DummyExpression(rightCol)
        };
        ConcreteRelationalExpression expr2 = new ConcreteRelationalExpression(args2, 0);
        assertEquals(Boolean.TRUE, expr2.computeValue(null));

        Collection<Integer> col1 = new ArrayList<Integer>();
        col1.add(Integer.valueOf(2));

        Collection<Integer> col2 = new ArrayList<Integer>();
        col2.add(Integer.valueOf(2));

        Expression[] args3 = new Expression[] {
            new DummyExpression(col1),
            new DummyExpression(col2)
        };
        ConcreteRelationalExpression expr3 = new ConcreteRelationalExpression(args3, 0);
        assertEquals(Boolean.TRUE, expr3.computeValue(null));
    }

    public void testComputeValueEmptyCollections() throws Throwable {
        Collection<Integer> emptyCol = new ArrayList<Integer>();
        Collection<Integer> nonEmptyCol = new ArrayList<Integer>();
        nonEmptyCol.add(Integer.valueOf(1));

        Expression[] args1 = new Expression[] {
            new DummyExpression(emptyCol),
            new DummyExpression(nonEmptyCol)
        };
        ConcreteRelationalExpression expr1 = new ConcreteRelationalExpression(args1, 0);
        assertEquals(Boolean.FALSE, expr1.computeValue(null));
    }
}