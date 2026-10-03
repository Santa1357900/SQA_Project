package org.apache.commons.jxpath.ri.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.jxpath.JXPathContext;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoreOperationRelationalExpressionClaudeTest {

    private static List toList(int[] values) {
        List list = new ArrayList();
        for (int i = 0; i < values.length; i++) {
            list.add(new Integer(values[i]));
        }
        return list;
    }

    public static class ListBean {
        private List values;
        private List others;
        public List getValues() { return values; }
        public void setValues(List values) { this.values = values; }
        public List getOthers() { return others; }
        public void setOthers(List others) { this.others = others; }
    }

    private static JXPathContext newLiteralContext() {
        return JXPathContext.newContext(new Object());
    }

    // ---- Group A: scalar numeric comparisons at compare = -1,0,1 boundary for each operator ----

    // compute(): ld<rd -> compare=-1, evaluateCompare for '<' must be true
    @Test
    public void testLessThan_leftSmaller_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 < 3");
        assertEquals(Boolean.TRUE, result);
    }

    // compute(): ld==rd -> compare=0, evaluateCompare for '<' must be false (strict)
    @Test
    public void testLessThan_equalValues_false() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 < 2");
        assertEquals(Boolean.FALSE, result);
    }

    // compute(): ld>rd -> compare=1, evaluateCompare for '<' must be false
    @Test
    public void testLessThan_leftGreater_false() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("3 < 2");
        assertEquals(Boolean.FALSE, result);
    }

    // '<=' at compare=-1 must be true
    @Test
    public void testLessOrEqual_leftSmaller_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 <= 3");
        assertEquals(Boolean.TRUE, result);
    }

    // '<=' boundary compare=0 must be true (inclusive)
    @Test
    public void testLessOrEqual_equalValues_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 <= 2");
        assertEquals(Boolean.TRUE, result);
    }

    // '<=' at compare=1 must be false
    @Test
    public void testLessOrEqual_leftGreater_false() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("3 <= 2");
        assertEquals(Boolean.FALSE, result);
    }

    // '>' at compare=1 must be true
    @Test
    public void testGreaterThan_leftGreater_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("3 > 2");
        assertEquals(Boolean.TRUE, result);
    }

    // '>' boundary compare=0 must be false (strict)
    @Test
    public void testGreaterThan_equalValues_false() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 > 2");
        assertEquals(Boolean.FALSE, result);
    }

    // '>' at compare=-1 must be false
    @Test
    public void testGreaterThan_leftSmaller_false() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 > 3");
        assertEquals(Boolean.FALSE, result);
    }

    // '>=' at compare=1 must be true
    @Test
    public void testGreaterOrEqual_leftGreater_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("3 >= 2");
        assertEquals(Boolean.TRUE, result);
    }

    // '>=' boundary compare=0 must be true (inclusive)
    @Test
    public void testGreaterOrEqual_equalValues_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 >= 2");
        assertEquals(Boolean.TRUE, result);
    }

    // '>=' at compare=-1 must be false
    @Test
    public void testGreaterOrEqual_leftSmaller_false() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("2 >= 3");
        assertEquals(Boolean.FALSE, result);
    }

    // ---- Group B: string operands compared numerically, not lexicographically (XPath 1.0 spec) ----

    // string literals must be converted via InfoSetUtil.doubleValue and compared as numbers
    @Test
    public void testLessThan_stringNumericOperands_comparedNumerically() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("'9' < '10'");
        assertEquals(Boolean.TRUE, result);
    }

    // lexicographically '9' > '10' but numerically 9 < 10, so '>' must be false
    @Test
    public void testGreaterThan_stringNumericOperands_comparedNumerically() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("'9' > '10'");
        assertEquals(Boolean.FALSE, result);
    }

    // ---- Group C: NaN handling branches in compute() ----

    // left reduces to NaN (empty string not numeric) -> compute must return false regardless of right
    @Test
    public void testLessThan_leftOperandNaN_returnsFalse() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("'' < 5");
        assertEquals(Boolean.FALSE, result);
    }

    // right reduces to NaN (non numeric string) -> compute must return false regardless of left
    @Test
    public void testLessThan_rightOperandNaN_returnsFalse() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("5 < 'abc'");
        assertEquals(Boolean.FALSE, result);
    }

    // both operands non numeric -> both NaN checks short-circuit to false
    @Test
    public void testLessThan_bothOperandsNaN_returnsFalse() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("'foo' < 'bar'");
        assertEquals(Boolean.FALSE, result);
    }

    // NaN handling must also hold for '>=' operator, not just '<'
    @Test
    public void testGreaterOrEqual_nanOperand_returnsFalse() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("'abc' >= 1");
        assertEquals(Boolean.FALSE, result);
    }

    // ---- Group D: boolean operand numeric conversion (true=1.0, false=0.0) ----

    // false() converts to 0, true() converts to 1, so false() < true() must be true
    @Test
    public void testLessThan_booleanOperands_falseLessThanTrue() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("false() < true()");
        assertEquals(Boolean.TRUE, result);
    }

    // true() > false() numerically 1 > 0 must be true
    @Test
    public void testGreaterThan_booleanOperands_trueGreaterThanFalse() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("true() > false()");
        assertEquals(Boolean.TRUE, result);
    }

    // ---- Group E: decimal and negative number boundary values ----

    // equal decimal values at '<=' boundary must be true
    @Test
    public void testLessOrEqual_decimalEqualValues_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("1.5 <= 1.5");
        assertEquals(Boolean.TRUE, result);
    }

    // near decimal boundary still correctly ordered
    @Test
    public void testLessThan_decimalBoundary_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("1.49 < 1.5");
        assertEquals(Boolean.TRUE, result);
    }

    // negative numbers compared correctly
    @Test
    public void testLessThan_negativeNumbers_true() throws Throwable {
        JXPathContext ctx = newLiteralContext();
        Object result = ctx.getValue("-5 < -1");
        assertEquals(Boolean.TRUE, result);
    }

    // ---- Group F: Map-based bean property access (JXPathContext.newContext(Map)) ----

    // scalar properties from a Map context, equal values, '<=' inclusive must be true
    @Test
    public void testLessOrEqual_mapPropertiesEqual_true() throws Throwable {
        Map map = new HashMap();
        map.put("x", new Integer(5));
        map.put("y", new Integer(5));
        JXPathContext ctx = JXPathContext.newContext(map);
        assertEquals(Boolean.TRUE, ctx.getValue("x <= y"));
    }

    // scalar properties from a Map context, equal values, '>=' inclusive must be true
    @Test
    public void testGreaterOrEqual_mapPropertiesEqual_true() throws Throwable {
        Map map = new HashMap();
        map.put("x", new Integer(5));
        map.put("y", new Integer(5));
        JXPathContext ctx = JXPathContext.newContext(map);
        assertEquals(Boolean.TRUE, ctx.getValue("x >= y"));
    }

    // differing map properties, strict '<' must be true when left smaller
    @Test
    public void testLessThan_mapPropertiesDifferent_true() throws Throwable {
        Map map = new HashMap();
        map.put("x", new Integer(3));
        map.put("y", new Integer(7));
        JXPathContext ctx = JXPathContext.newContext(map);
        assertEquals(Boolean.TRUE, ctx.getValue("x < y"));
    }

    // differing map properties, strict '>' must be false when left smaller
    @Test
    public void testGreaterThan_mapPropertiesDifferent_false() throws Throwable {
        Map map = new HashMap();
        map.put("x", new Integer(3));
        map.put("y", new Integer(7));
        JXPathContext ctx = JXPathContext.newContext(map);
        assertEquals(Boolean.FALSE, ctx.getValue("x > y"));
    }

    // ---- Group G: node-set(left) vs scalar(right) (containsMatch: existential comparison) ----

    // at least one element satisfies relation -> containsMatch must return true (existential)
    @Test
    public void testCompute_nodeSetLeftContainsMatchingElement_true() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(toList(new int[] {1, 5, 10}));
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.TRUE, ctx.getValue("values < 3"));
    }

    // no element satisfies relation -> containsMatch must return false
    @Test
    public void testCompute_nodeSetLeftNoMatchingElement_false() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(toList(new int[] {10, 20, 30}));
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.FALSE, ctx.getValue("values < 3"));
    }

    // empty node-set vs scalar: loop runs zero times -> must be false
    @Test
    public void testCompute_emptyNodeSetVsScalar_false() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(new ArrayList());
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.FALSE, ctx.getValue("values < 3"));
    }

    // ---- Group H: node-set vs node-set (findMatch: existential pairwise comparison) ----

    // at least one (left,right) pair satisfies relation -> findMatch true
    @Test
    public void testCompute_nodeSetVsNodeSetExistsMatch_true() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(toList(new int[] {1, 2, 3}));
        bean.setOthers(toList(new int[] {10, 20, 30}));
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.TRUE, ctx.getValue("values < others"));
    }

    // no pair satisfies relation -> findMatch false
    @Test
    public void testCompute_nodeSetVsNodeSetNoMatch_false() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(toList(new int[] {10, 20, 30}));
        bean.setOthers(toList(new int[] {1, 2, 3}));
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.FALSE, ctx.getValue("values < others"));
    }

    // equal single-element node-sets at '>=' boundary (compare=0) must be true
    @Test
    public void testCompute_nodeSetVsNodeSetEqualBoundary_true() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(toList(new int[] {5}));
        bean.setOthers(toList(new int[] {5}));
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.TRUE, ctx.getValue("values >= others"));
    }

    // ---- Group I: scalar(left) vs node-set(right) orientation must be preserved per XPath spec ----

    // "3 < values": spec requires exists e in values with (3 < e) true; all of 10,20,30 satisfy 3<e
    @Test
    public void testLessThan_scalarLeftNodeSetRight_preservesOrientation_true() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(toList(new int[] {10, 20, 30}));
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.TRUE, ctx.getValue("3 < values"));
    }

    // "3 > values": spec requires exists e in values with (3 > e) true; all of 1,2,0 satisfy 3>e
    @Test
    public void testGreaterThan_scalarLeftNodeSetRight_preservesOrientation_true() throws Throwable {
        ListBean bean = new ListBean();
        bean.setValues(toList(new int[] {1, 2, 0}));
        JXPathContext ctx = JXPathContext.newContext(bean);
        assertEquals(Boolean.TRUE, ctx.getValue("3 > values"));
    }
}
