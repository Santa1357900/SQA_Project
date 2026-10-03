package org.apache.commons.jxpath.ri.compiler;

import static org.junit.Assert.*;
import org.junit.Test;
import org.apache.commons.jxpath.JXPathContext;
import java.util.List;
import java.util.ArrayList;

public class CoreOperationRelationalExpressionClaudeTest {

    public static class NumberBean {
        private List numbers;
        public NumberBean(List numbers) {
            this.numbers = numbers;
        }
        public List getNumbers() {
            return numbers;
        }
    }

    public static class PairBean {
        private List left;
        private List right;
        public PairBean(List left, List right) {
            this.left = left;
            this.right = right;
        }
        public List getLeft() {
            return left;
        }
        public List getRight() {
            return right;
        }
    }

    private NumberBean numberBean(int[] values) {
        List list = new ArrayList();
        for (int i = 0; i < values.length; i++) {
            list.add(Integer.valueOf(values[i]));
        }
        return new NumberBean(list);
    }

    private PairBean pairBean(int[] leftValues, int[] rightValues) {
        List left = new ArrayList();
        for (int i = 0; i < leftValues.length; i++) {
            left.add(Integer.valueOf(leftValues[i]));
        }
        List right = new ArrayList();
        for (int i = 0; i < rightValues.length; i++) {
            right.add(Integer.valueOf(rightValues[i]));
        }
        return new PairBean(left, right);
    }

    // ---- scalar vs scalar: basic operators ----

    // < true case: 1 < 2
    @Test
    public void testLessThan_trueCase_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("1 < 2");
        assertTrue(((Boolean) result).booleanValue());
    }

    // < false case: 2 < 1
    @Test
    public void testLessThan_falseCase_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("2 < 1");
        assertFalse(((Boolean) result).booleanValue());
    }

    // < boundary (equal values, strict less-than must be false)
    @Test
    public void testLessThan_equalValues_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("2 < 2");
        assertFalse(((Boolean) result).booleanValue());
    }

    // <= boundary (equal values, must be true)
    @Test
    public void testLessThanOrEqual_equalValues_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("2 <= 2");
        assertTrue(((Boolean) result).booleanValue());
    }

    // <= false case
    @Test
    public void testLessThanOrEqual_falseCase_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("3 <= 2");
        assertFalse(((Boolean) result).booleanValue());
    }

    // > true case
    @Test
    public void testGreaterThan_trueCase_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("3 > 2");
        assertTrue(((Boolean) result).booleanValue());
    }

    // > boundary (equal values must be false)
    @Test
    public void testGreaterThan_equalValues_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("2 > 2");
        assertFalse(((Boolean) result).booleanValue());
    }

    // >= boundary (equal values must be true)
    @Test
    public void testGreaterThanOrEqual_equalValues_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("2 >= 2");
        assertTrue(((Boolean) result).booleanValue());
    }

    // >= false case
    @Test
    public void testGreaterThanOrEqual_falseCase_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("1 >= 2");
        assertFalse(((Boolean) result).booleanValue());
    }

    // < with negative numbers
    @Test
    public void testLessThan_negativeNumbers_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("-5 < -1");
        assertTrue(((Boolean) result).booleanValue());
    }

    // > with negative numbers
    @Test
    public void testGreaterThan_negativeNumbers_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("-1 > -5");
        assertTrue(((Boolean) result).booleanValue());
    }

    // string operands compared numerically, not lexicographically
    @Test
    public void testLessThan_stringNumericConversion_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("'5' < '10'");
        assertTrue(((Boolean) result).booleanValue());
    }

    // string operands compared numerically for >
    @Test
    public void testGreaterThan_stringNumericConversion_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("'10' > '5'");
        assertTrue(((Boolean) result).booleanValue());
    }





    // decimal operands for <
    @Test
    public void testLessThan_decimalBoundary_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("1.5 < 2.5");
        assertTrue(((Boolean) result).booleanValue());
    }

    // decimal operands equal for <=
    @Test
    public void testLessThanOrEqual_decimalBoundary_returnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Object());
        Object result = ctx.getValue("2.5 <= 2.5");
        assertTrue(((Boolean) result).booleanValue());
    }

    // ---- left operand is a node-set (containsMatch branch, left side) ----



    // left nodeset > scalar: no match among elements
    @Test
    public void testGreaterThan_leftNodeSet_noMatch_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(numberBean(new int[] {1, 2, 3}));
        Object result = ctx.getValue("numbers > 10");
        assertFalse(((Boolean) result).booleanValue());
    }



    // left nodeset empty: loop runs zero times, must be false
    @Test
    public void testGreaterThan_leftNodeSet_emptyCollection_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(numberBean(new int[] {}));
        Object result = ctx.getValue("numbers > 1");
        assertFalse(((Boolean) result).booleanValue());
    }

    // ---- right operand is a node-set (containsMatch branch, right side) ----
    // These expose the argument-order bug: operator must stay "scalar OP member",
    // not be silently reversed to "member OP scalar".

    // BUG CHECK: 3 < [1,2] -- no member of {1,2} is greater than 3, must be false
    @Test
    public void testLessThan_rightNodeSet_bugCheck_noMemberGreater_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(numberBean(new int[] {1, 2}));
        Object result = ctx.getValue("3 < numbers");
        assertFalse(((Boolean) result).booleanValue());
    }









    // 100 < [1,2,3] -- no member exceeds 100, must be false
    @Test
    public void testLessThan_rightNodeSet_noMatch_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(numberBean(new int[] {1, 2, 3}));
        Object result = ctx.getValue("100 < numbers");
        assertFalse(((Boolean) result).booleanValue());
    }



    // ---- both operands are node-sets (findMatch branch) ----



    // left < right: no matching pair exists
    @Test
    public void testLessThan_bothNodeSets_noMatch_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(pairBean(new int[] {5, 6}, new int[] {1, 2}));
        Object result = ctx.getValue("left < right");
        assertFalse(((Boolean) result).booleanValue());
    }



    // right nodeset empty: outer loop over right runs zero times, must be false
    @Test
    public void testLessThan_bothNodeSets_rightEmpty_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(pairBean(new int[] {1, 2}, new int[] {}));
        Object result = ctx.getValue("left < right");
        assertFalse(((Boolean) result).booleanValue());
    }

    // left nodeset empty: inner set is empty, must be false
    @Test
    public void testLessThan_bothNodeSets_leftEmpty_returnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(pairBean(new int[] {}, new int[] {1, 2}));
        Object result = ctx.getValue("left < right");
        assertFalse(((Boolean) result).booleanValue());
    }


}
