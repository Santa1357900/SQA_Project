package org.apache.commons.jxpath.ri.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.SelfContext;
import org.junit.Test;

public class CoreOperationRelationalExpressionTest {

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

        public Object compute(EvalContext context) {
            return value;
        }

        protected int getPrecedence() {
            return 0;
        }

        protected boolean computeContextDependent() {
            return false;
        }
    }

    @Test
    public void testComputeValueLessThan() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(Double.valueOf(1.0)),
            new DummyExpression(Double.valueOf(2.0))
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, -1);
        Object result = op.computeValue(null);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testComputeValueGreaterThan() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(Double.valueOf(5.0)),
            new DummyExpression(Double.valueOf(2.0))
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, 1);
        Object result = op.computeValue(null);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testComputeValueEqual() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(Double.valueOf(3.0)),
            new DummyExpression(Double.valueOf(3.0))
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, 0);
        Object result = op.computeValue(null);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testGetPrecedenceAndSymmetric() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(Double.valueOf(1.0)),
            new DummyExpression(Double.valueOf(2.0))
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, -1);
        assertEquals(3, op.getPrecedence());
        assertFalse(op.isSymmetric());
    }

    @Test
    public void testComputeWithCollection() throws Throwable {
        List<Double> leftList = new ArrayList<Double>();
        leftList.add(Double.valueOf(1.0));
        leftList.add(Double.valueOf(10.0));

        Expression[] args = new Expression[] {
            new DummyExpression(leftList),
            new DummyExpression(Double.valueOf(5.0))
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, 1);
        Object result = op.computeValue(null);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testComputeWithRightCollection() throws Throwable {
        List<Double> rightList = new ArrayList<Double>();
        rightList.add(Double.valueOf(0.0));
        rightList.add(Double.valueOf(2.0));

        Expression[] args = new Expression[] {
            new DummyExpression(Double.valueOf(1.0)),
            new DummyExpression(rightList)
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, -1);
        Object result = op.computeValue(null);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testComputeWithBothIterators() throws Throwable {
        List<Double> leftList = new ArrayList<Double>();
        leftList.add(Double.valueOf(2.0));

        List<Double> rightList = new ArrayList<Double>();
        rightList.add(Double.valueOf(2.0));

        Expression[] args = new Expression[] {
            new DummyExpression(leftList.iterator()),
            new DummyExpression(rightList.iterator())
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, 0);
        Object result = op.computeValue(null);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testNullInputs() throws Throwable {
        Expression[] args = new Expression[] {
            new DummyExpression(null),
            new DummyExpression(null)
        };
        CoreOperationRelationalExpression op = new ConcreteRelationalExpression(args, 0);
        Object result = op.computeValue(null);
        assertEquals(Boolean.TRUE, result);
    }
}