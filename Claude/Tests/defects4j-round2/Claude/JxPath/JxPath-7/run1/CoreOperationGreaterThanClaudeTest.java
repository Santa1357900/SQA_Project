package org.apache.commons.jxpath.ri.compiler;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.jxpath.JXPathContext;

/**
 * Tests for CoreOperationGreaterThan ("&gt;" operator).
 * Exercised indirectly through JXPathContext (public API) per project convention,
 * since CoreOperationGreaterThan's constructor requires internal Expression
 * implementations that are not part of the given source.
 * Oracle: XPath 1.0 numeric comparison contract - operands are converted to
 * double (number()), non-numeric strings become NaN, and any comparison
 * involving NaN is false; result is true iff left double is strictly greater
 * than right double.
 */
public class CoreOperationGreaterThanClaudeTest {

    public static class Bean {
        private double a;
        private double b;

        public double getA() {
            return a;
        }

        public void setA(double a) {
            this.a = a;
        }

        public double getB() {
            return b;
        }

        public void setB(double b) {
            this.b = b;
        }
    }

    private JXPathContext newPlainContext() {
        return JXPathContext.newContext(new Object());
    }

    // l > r with l strictly greater (integers) -> true branch
    @Test
    public void testComputeValue_integerGreater_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("5 > 3");
        assertEquals(Boolean.TRUE, result);
    }

    // l > r with l strictly less (integers) -> false branch
    @Test
    public void testComputeValue_integerLess_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("3 > 5");
        assertEquals(Boolean.FALSE, result);
    }

    // boundary: l == r must be false (strict greater-than, not >=)
    @Test
    public void testComputeValue_integerEqual_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("4 > 4");
        assertEquals(Boolean.FALSE, result);
    }

    // negative numbers, l greater -> true branch
    @Test
    public void testComputeValue_negativeNumbersGreater_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("-1 > -2");
        assertEquals(Boolean.TRUE, result);
    }

    // negative numbers, l smaller -> false branch
    @Test
    public void testComputeValue_negativeNumbersLess_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("-5 > -3");
        assertEquals(Boolean.FALSE, result);
    }

    // decimal values, l greater -> true branch
    @Test
    public void testComputeValue_decimalGreater_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("1.5 > 1.4");
        assertEquals(Boolean.TRUE, result);
    }

    // decimal values equal -> false (boundary with fractional values)
    @Test
    public void testComputeValue_decimalEqual_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("2.5 > 2.5");
        assertEquals(Boolean.FALSE, result);
    }

    // zero vs positive: 0 > positive -> false
    @Test
    public void testComputeValue_zeroVsPositive_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("0 > 1");
        assertEquals(Boolean.FALSE, result);
    }

    // positive vs zero: positive > 0 -> true
    @Test
    public void testComputeValue_positiveVsZero_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("1 > 0");
        assertEquals(Boolean.TRUE, result);
    }

    // zero vs negative: 0 > negative -> true
    @Test
    public void testComputeValue_zeroVsNegative_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("0 > -1");
        assertEquals(Boolean.TRUE, result);
    }

    // large numbers, l greater -> true branch
    @Test
    public void testComputeValue_largeNumbers_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("1000000 > 999999");
        assertEquals(Boolean.TRUE, result);
    }

    // numeric strings compared as numbers, not lexicographically: '10' > '9' is true
    @Test
    public void testComputeValue_numericStringsGreater_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("'10' > '9'");
        assertEquals(Boolean.TRUE, result);
    }

    // numeric strings compared as numbers: '2' > '10' is false numerically
    @Test
    public void testComputeValue_numericStringsLess_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("'2' > '10'");
        assertEquals(Boolean.FALSE, result);
    }

    // boolean conversion: true()=1.0 > false()=0.0 -> true branch
    @Test
    public void testComputeValue_booleanTrueGreaterFalse_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("true() > false()");
        assertEquals(Boolean.TRUE, result);
    }

    // boolean conversion: false()=0.0 > true()=1.0 -> false branch
    @Test
    public void testComputeValue_booleanFalseGreaterTrue_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("false() > true()");
        assertEquals(Boolean.FALSE, result);
    }

    // boolean equal values boundary: true() > true() -> false
    @Test
    public void testComputeValue_booleanEqualTrue_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("true() > true()");
        assertEquals(Boolean.FALSE, result);
    }

    // non-numeric string on left becomes NaN; any NaN comparison must be false
    @Test
    public void testComputeValue_nonNumericStringLeft_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("'abc' > 5");
        assertEquals(Boolean.FALSE, result);
    }

    // non-numeric string on right becomes NaN; any NaN comparison must be false
    @Test
    public void testComputeValue_nonNumericStringRight_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("5 > 'xyz'");
        assertEquals(Boolean.FALSE, result);
    }

    // bean property comparison, left greater -> true branch
    @Test
    public void testComputeValue_beanPropertyGreater_returnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setA(10.0);
        bean.setB(5.0);
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("a > b");
        assertEquals(Boolean.TRUE, result);
    }

    // bean property comparison, left smaller -> false branch
    @Test
    public void testComputeValue_beanPropertyLess_returnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setA(2.0);
        bean.setB(8.0);
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("a > b");
        assertEquals(Boolean.FALSE, result);
    }

    // bean property comparison, equal values boundary -> false
    @Test
    public void testComputeValue_beanPropertyEqual_returnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setA(4.0);
        bean.setB(4.0);
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("a > b");
        assertEquals(Boolean.FALSE, result);
    }

    // map-backed context, left greater -> true branch
    @Test
    public void testComputeValue_mapValuesGreater_returnsTrue() throws Throwable {
        Map map = new HashMap();
        map.put("x", new Double(20.0));
        map.put("y", new Double(5.0));
        JXPathContext ctx = JXPathContext.newContext(map);
        Object result = ctx.getValue("x > y");
        assertEquals(Boolean.TRUE, result);
    }

    // map-backed context, left smaller -> false branch
    @Test
    public void testComputeValue_mapValuesLess_returnsFalse() throws Throwable {
        Map map = new HashMap();
        map.put("x", new Double(3.0));
        map.put("y", new Double(9.0));
        JXPathContext ctx = JXPathContext.newContext(map);
        Object result = ctx.getValue("x > y");
        assertEquals(Boolean.FALSE, result);
    }

    // arithmetic sub-expressions equal at boundary: (2-3) > -1 -> -1 > -1 -> false
    @Test
    public void testComputeValue_arithmeticExpressionEqual_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("(2 - 3) > -1");
        assertEquals(Boolean.FALSE, result);
    }

    // arithmetic sub-expressions, left greater -> true branch
    @Test
    public void testComputeValue_arithmeticExpressionGreater_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("(5 + 5) > 9");
        assertEquals(Boolean.TRUE, result);
    }

    // small negative decimal vs zero -> false branch
    @Test
    public void testComputeValue_negativeDecimalVsZero_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("-0.0001 > 0");
        assertEquals(Boolean.FALSE, result);
    }

    // very close values, left slightly greater -> true branch
    @Test
    public void testComputeValue_closeValuesGreater_returnsTrue() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("1.0001 > 1.0");
        assertEquals(Boolean.TRUE, result);
    }

    // numeric string vs equal numeric literal at boundary -> false
    @Test
    public void testComputeValue_stringVsNumberEqualValue_returnsFalse() throws Throwable {
        JXPathContext ctx = newPlainContext();
        Object result = ctx.getValue("'5' > 5");
        assertEquals(Boolean.FALSE, result);
    }

    // mixed bean property vs literal, left greater -> true branch
    @Test
    public void testComputeValue_mixedTypesBeanVsLiteral_returnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setA(7.0);
        bean.setB(0.0);
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("a > 2");
        assertEquals(Boolean.TRUE, result);
    }

    // mixed bean property vs literal, left smaller -> false branch
    @Test
    public void testComputeValue_mixedTypesBeanVsLiteral_returnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setA(1.0);
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("a > 2");
        assertEquals(Boolean.FALSE, result);
    }
}
