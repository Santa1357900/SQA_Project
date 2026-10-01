package org.apache.commons.jxpath.ri.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.jxpath.JXPathContext;

/**
 * Tests for CoreOperationRelationalExpression, exercised indirectly through
 * JXPathContext evaluating XPath relational expressions ("&lt;", "&lt;=", "&gt;", "&gt;=").
 */
public class CoreOperationRelationalExpressionClaudeTest {

    private List buildItems(int[] values) {
        List items = new ArrayList();
        for (int i = 0; i < values.length; i++) {
            Map item = new HashMap();
            item.put("value", new Integer(values[i]));
            items.add(item);
        }
        return items;
    }

    private JXPathContext createItemsContext(int[] values) {
        Map root = new HashMap();
        root.put("items", buildItems(values));
        return JXPathContext.newContext(root);
    }

    private JXPathContext createTwoItemsContext(int[] valuesA, int[] valuesB) {
        Map root = new HashMap();
        root.put("itemsA", buildItems(valuesA));
        root.put("itemsB", buildItems(valuesB));
        return JXPathContext.newContext(root);
    }

    private void assertRelation(JXPathContext context, String xpath, boolean expected) throws Throwable {
        Object result = context.getValue(xpath);
        assertTrue(result instanceof Boolean);
        assertEquals(expected, ((Boolean) result).booleanValue());
    }

    // scalar < scalar: left strictly smaller -> true
    @Test
    public void testComputeValue_lessThan_trueWhenLeftSmaller() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "1 < 2", true);
    }

    // scalar < scalar: equal values -> false (boundary of strict <)
    @Test
    public void testComputeValue_lessThan_falseWhenEqual() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "2 < 2", false);
    }

    // scalar < scalar: left greater -> false
    @Test
    public void testComputeValue_lessThan_falseWhenLeftGreater() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "3 < 2", false);
    }

    // scalar <= scalar: equal values -> true (boundary of <=)
    @Test
    public void testComputeValue_lessThanOrEqual_trueWhenEqual() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "2 <= 2", true);
    }

    // scalar <= scalar: left smaller -> true
    @Test
    public void testComputeValue_lessThanOrEqual_trueWhenLess() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "1 <= 2", true);
    }

    // scalar <= scalar: left greater -> false
    @Test
    public void testComputeValue_lessThanOrEqual_falseWhenGreater() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "3 <= 2", false);
    }

    // scalar > scalar: left greater -> true
    @Test
    public void testComputeValue_greaterThan_trueWhenLeftGreater() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "3 > 2", true);
    }

    // scalar > scalar: equal values -> false (boundary of strict >)
    @Test
    public void testComputeValue_greaterThan_falseWhenEqual() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "2 > 2", false);
    }

    // scalar >= scalar: equal values -> true (boundary of >=)
    @Test
    public void testComputeValue_greaterThanOrEqual_trueWhenEqual() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "2 >= 2", true);
    }

    // scalar >= scalar: left smaller -> false
    @Test
    public void testComputeValue_greaterThanOrEqual_falseWhenLess() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "1 >= 2", false);
    }

    // left operand non-numeric -> NaN -> compute() returns false regardless of operator
    @Test
    public void testComputeValue_nonNumericLeftString_returnsFalse() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "'abc' < 5", false);
    }

    // right operand non-numeric -> NaN -> compute() returns false regardless of operator
    @Test
    public void testComputeValue_nonNumericRightString_returnsFalse() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "5 < 'xyz'", false);
    }

    // both operands non-numeric -> NaN short-circuit on left -> false
    @Test
    public void testComputeValue_bothNonNumericStrings_returnsFalse() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "'abc' > 'xyz'", false);
    }

    // numeric strings compared numerically, not lexically
    @Test
    public void testComputeValue_numericStringParsing_greaterTrue() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "'10' > '9'", true);
    }

    // numeric strings compared numerically, not lexically
    @Test
    public void testComputeValue_numericStringParsing_lessTrue() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "'2' < '10'", true);
    }

    // negative numbers scalar comparison
    @Test
    public void testComputeValue_negativeNumbers_lessThanTrue() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "-5 < -1", true);
    }

    // zero boundary: equal zero with <=
    @Test
    public void testComputeValue_zeroBoundary_lessOrEqualTrue() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "0 <= 0", true);
    }

    // zero boundary: equal zero with strict <
    @Test
    public void testComputeValue_zeroBoundary_lessThanFalse() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "0 < 0", false);
    }

    // large integer values
    @Test
    public void testComputeValue_largeIntegers_greaterThanTrue() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "1000000000 > 999999999", true);
    }

    // decimal (non-integer) numeric values
    @Test
    public void testComputeValue_decimalValues_lessThanTrue() throws Throwable {
        JXPathContext context = JXPathContext.newContext(new HashMap());
        assertRelation(context, "2.5 < 3.5", true);
    }

    // right operand is node-set (iterator), all elements smaller than left: "5 < items" must be false
    @Test
    public void testComputeValue_rightNodeSet_lessThan_allSmaller_returnsFalse() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {1, 2, 3});
        assertRelation(context, "5 < items/value", false);
    }

    // right operand is node-set, some element larger than left: "5 < items" must be true
    @Test
    public void testComputeValue_rightNodeSet_lessThan_someLarger_returnsTrue() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {10, 20, 30});
        assertRelation(context, "5 < items/value", true);
    }

    // right operand is node-set, left greater than some element: "5 > items" must be true
    @Test
    public void testComputeValue_rightNodeSet_greaterThan_someSmaller_returnsTrue() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {1, 2, 3});
        assertRelation(context, "5 > items/value", true);
    }

    // right operand is node-set, left smaller than all elements: "5 > items" must be false
    @Test
    public void testComputeValue_rightNodeSet_greaterThan_allLarger_returnsFalse() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {10, 20, 30});
        assertRelation(context, "5 > items/value", false);
    }

    // right operand is node-set, <= with a reachable element equal/greater: "5 <= items" must be true
    @Test
    public void testComputeValue_rightNodeSet_lessOrEqual_matchFound_returnsTrue() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {6, 7, 8});
        assertRelation(context, "5 <= items/value", true);
    }

    // right operand is node-set, >= with no element satisfying: "5 >= items" must be false
    @Test
    public void testComputeValue_rightNodeSet_greaterOrEqual_noMatch_returnsFalse() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {10, 20, 30});
        assertRelation(context, "5 >= items/value", false);
    }

    // right operand is single-element node-set (loop executes exactly once)
    @Test
    public void testComputeValue_rightNodeSet_singleElement_returnsTrue() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {100});
        assertRelation(context, "5 < items/value", true);
    }

    // left operand is node-set, match found: "items < 5" must be true
    @Test
    public void testComputeValue_leftNodeSet_lessThan_matchFound_returnsTrue() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {1, 2, 3});
        assertRelation(context, "items/value < 5", true);
    }

    // left operand is node-set, no match: "items < 5" must be false
    @Test
    public void testComputeValue_leftNodeSet_lessThan_noMatch_returnsFalse() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {10, 20, 30});
        assertRelation(context, "items/value < 5", false);
    }

    // left operand is node-set, <= boundary match
    @Test
    public void testComputeValue_leftNodeSet_lessOrEqual_matchFound_returnsTrue() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {5, 6, 7});
        assertRelation(context, "items/value <= 5", true);
    }

    // left operand is single-element node-set, greater-than match
    @Test
    public void testComputeValue_leftNodeSet_singleElement_greaterThan_returnsTrue() throws Throwable {
        JXPathContext context = createItemsContext(new int[] {100});
        assertRelation(context, "items/value > 5", true);
    }

    // both operands are node-sets (findMatch), intersection exists -> true
    @Test
    public void testComputeValue_bothNodeSets_lessThan_matchFound_returnsTrue() throws Throwable {
        JXPathContext context = createTwoItemsContext(new int[] {1, 2, 3}, new int[] {4, 5, 6});
        assertRelation(context, "itemsA/value < itemsB/value", true);
    }

    // both operands are node-sets, no pair satisfies relation -> false
    @Test
    public void testComputeValue_bothNodeSets_lessThan_noMatch_returnsFalse() throws Throwable {
        JXPathContext context = createTwoItemsContext(new int[] {10, 11}, new int[] {1, 2});
        assertRelation(context, "itemsA/value < itemsB/value", false);
    }

    // both operands are single-element node-sets, strict < with equal values -> false
    @Test
    public void testComputeValue_bothNodeSets_singleElementEach_lessThan_falseWhenEqual() throws Throwable {
        JXPathContext context = createTwoItemsContext(new int[] {1}, new int[] {1});
        assertRelation(context, "itemsA/value < itemsB/value", false);
    }

    // both operands are single-element node-sets, <= with equal values -> true
    @Test
    public void testComputeValue_bothNodeSets_singleElementEach_lessOrEqual_trueWhenEqual() throws Throwable {
        JXPathContext context = createTwoItemsContext(new int[] {2}, new int[] {2});
        assertRelation(context, "itemsA/value <= itemsB/value", true);
    }

    // both operands are node-sets, > with intersection present -> true
    @Test
    public void testComputeValue_bothNodeSets_greaterThan_matchFound_returnsTrue() throws Throwable {
        JXPathContext context = createTwoItemsContext(new int[] {4, 5, 6}, new int[] {1, 2, 3});
        assertRelation(context, "itemsA/value > itemsB/value", true);
    }

    // both operands are node-sets, >= with no pair satisfying -> false
    @Test
    public void testComputeValue_bothNodeSets_greaterOrEqual_noMatch_returnsFalse() throws Throwable {
        JXPathContext context = createTwoItemsContext(new int[] {1, 2}, new int[] {10, 11});
        assertRelation(context, "itemsA/value >= itemsB/value", false);
    }
}
