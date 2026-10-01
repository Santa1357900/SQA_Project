package org.apache.commons.jxpath.ri.compiler;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.JXPathInvalidSyntaxException;

public class CoreFunctionClaudeTest {

    public static class SampleBean {
        private List names;
        private List numbers;

        public SampleBean() {
            names = new ArrayList();
            names.add("Alice");
            names.add("Bob");
            names.add("Carol");
            numbers = new ArrayList();
            numbers.add(new Integer(10));
            numbers.add(new Integer(20));
            numbers.add(new Integer(30));
        }

        public List getNames() {
            return names;
        }

        public List getNumbers() {
            return numbers;
        }
    }

    private JXPathContext context;

    @Before
    public void setUp() throws Throwable {
        context = JXPathContext.newContext(new SampleBean());
    }

    // functionPosition: position()=1 selects the first node
    @Test
    public void testFunctionPosition_firstNode_returnsFirstElement() throws Throwable {
        Object result = context.getValue("names[position()=1]");
        assertEquals("Alice", result);
    }

    // functionLast + functionPosition: position()=last() selects the last node after full iteration
    @Test
    public void testFunctionLast_positionEqualsLast_returnsLastElement() throws Throwable {
        Object result = context.getValue("names[position()=last()]");
        assertEquals("Carol", result);
    }

    // functionCount: argument is an EvalContext (node-set) -> counts all nodes
    @Test
    public void testFunctionCount_withNodeSet_returnsSize() throws Throwable {
        Object result = context.getValue("count(names)");
        assertEquals(3.0, ((Double) result).doubleValue(), 1e-9);
    }

    // functionCount: scalar argument (not null, not EvalContext, not Collection) -> count=1
    @Test
    public void testFunctionCount_withScalarArgument_returnsOne() throws Throwable {
        Object result = context.getValue("count(5)");
        assertEquals(1.0, ((Double) result).doubleValue(), 1e-9);
    }

    // functionCount: null argument -> count=0
    @Test
    public void testFunctionCount_withNullArgument_returnsZero() throws Throwable {
        context.getVariables().declareVariable("nullVar", null);
        Object result = context.getValue("count($nullVar)");
        assertEquals(0.0, ((Double) result).doubleValue(), 1e-9);
    }

    // functionLang: no xml:lang information present -> false
    @Test
    public void testFunctionLang_noLanguageInfo_returnsFalse() throws Throwable {
        Object result = context.getValue("lang('en')");
        assertEquals(Boolean.FALSE, result);
    }

    // functionString: 1-arg branch converts a number to its canonical string form
    @Test
    public void testFunctionString_withArgument_convertsNumberToString() throws Throwable {
        Object result = context.getValue("string(10)");
        assertEquals("10", result);
    }

    // functionConcat: exactly two arguments
    @Test
    public void testFunctionConcat_twoArguments_joinsStrings() throws Throwable {
        Object result = context.getValue("concat('foo','bar')");
        assertEquals("foobar", result);
    }

    // functionConcat: more than two arguments are all appended
    @Test
    public void testFunctionConcat_threeArguments_joinsStrings() throws Throwable {
        Object result = context.getValue("concat('a','b','c')");
        assertEquals("abc", result);
    }

    // functionConcat: fewer than 2 arguments triggers assertArgCount -> JXPathInvalidSyntaxException
    @Test
    public void testFunctionConcat_tooFewArguments_throws() throws Throwable {
        try {
            context.getValue("concat('a')");
            fail("expected JXPathInvalidSyntaxException");
        }
        catch (JXPathInvalidSyntaxException expected) {
        }
    }

    // functionStartsWith: ternary true and false branches
    @Test
    public void testFunctionStartsWith_matchesAndNonMatches() throws Throwable {
        assertEquals(Boolean.TRUE, context.getValue("starts-with('hello','he')"));
        assertEquals(Boolean.FALSE, context.getValue("starts-with('hello','lo')"));
    }

    // functionContains: ternary true and false branches
    @Test
    public void testFunctionContains_matchesAndNonMatches() throws Throwable {
        assertEquals(Boolean.TRUE, context.getValue("contains('hello','ell')"));
        assertEquals(Boolean.FALSE, context.getValue("contains('hello','xyz')"));
    }

    // functionSubstringBefore: delimiter found vs not found (index==-1 branch)
    @Test
    public void testFunctionSubstringBefore_foundAndNotFound() throws Throwable {
        assertEquals("2023", context.getValue("substring-before('2023-10-01','-')"));
        assertEquals("", context.getValue("substring-before('hello','x')"));
    }

    // functionSubstringAfter: delimiter found vs not found (index==-1 branch)
    @Test
    public void testFunctionSubstringAfter_foundAndNotFound() throws Throwable {
        assertEquals("10-01", context.getValue("substring-after('2023-10-01','-')"));
        assertEquals("", context.getValue("substring-after('hello','x')"));
    }

    // functionSubstring: two-argument form (ac==2 branch)
    @Test
    public void testFunctionSubstring_twoArgs_basic() throws Throwable {
        Object result = context.getValue("substring('12345',2)");
        assertEquals("2345", result);
    }

    // functionSubstring: three-argument form (ac==3 branch)
    @Test
    public void testFunctionSubstring_threeArgs_basic() throws Throwable {
        Object result = context.getValue("substring('12345',2,3)");
        assertEquals("234", result);
    }

    // functionSubstring: fractional start/length rounding, classic XPath 1.0 spec example
    @Test
    public void testFunctionSubstring_fractionalRounding_matchesSpecExample() throws Throwable {
        Object result = context.getValue("substring('12345',1.5,2.6)");
        assertEquals("234", result);
    }

    // functionSubstring: NaN start value -> empty string branch
    @Test
    public void testFunctionSubstring_NaNStart_returnsEmpty() throws Throwable {
        Object result = context.getValue("substring('12345', 0 div 0, 3)");
        assertEquals("", result);
    }

    // functionSubstring: negative rounded length -> empty string branch
    @Test
    public void testFunctionSubstring_negativeLength_returnsEmpty() throws Throwable {
        Object result = context.getValue("substring('12345',1,-2)");
        assertEquals("", result);
    }

    // functionSubstring: start beyond string length+1 -> empty string branch
    @Test
    public void testFunctionSubstring_startBeyondLength_returnsEmpty() throws Throwable {
        Object result = context.getValue("substring('12345',10)");
        assertEquals("", result);
    }

    // functionSubstring: wrong argument count (neither 2 nor 3) -> throws
    @Test
    public void testFunctionSubstring_wrongArgCount_throws() throws Throwable {
        try {
            context.getValue("substring('12345')");
            fail("expected JXPathInvalidSyntaxException");
        }
        catch (JXPathInvalidSyntaxException expected) {
        }
    }

    // functionStringLength: 1-arg branch
    @Test
    public void testFunctionStringLength_withArgument() throws Throwable {
        Object result = context.getValue("string-length('hello')");
        assertEquals(5.0, ((Double) result).doubleValue(), 1e-9);
    }

    // functionNormalizeSpace: leading/trailing trim and internal whitespace collapse
    @Test
    public void testFunctionNormalizeSpace_collapsesAndTrimsWhitespace() throws Throwable {
        Object result = context.getValue("normalize-space('  a   b  c ')");
        assertEquals("a b c", result);
    }

    // functionTranslate: mapped characters replaced, official XPath 1.0 spec example
    @Test
    public void testFunctionTranslate_replacesMappedCharacters() throws Throwable {
        Object result = context.getValue("translate('bar','abc','ABC')");
        assertEquals("BAr", result);
    }

    // functionTranslate: mapped character with no corresponding replacement is dropped
    @Test
    public void testFunctionTranslate_removesCharactersWithNoReplacement() throws Throwable {
        Object result = context.getValue("translate('--aaa--','abc-','ABC')");
        assertEquals("AAA", result);
    }

    // functionTranslate: wrong argument count -> throws
    @Test
    public void testFunctionTranslate_wrongArgCount_throws() throws Throwable {
        try {
            context.getValue("translate('a','b')");
            fail("expected JXPathInvalidSyntaxException");
        }
        catch (JXPathInvalidSyntaxException expected) {
        }
    }

    // functionBoolean: non-empty string is true
    @Test
    public void testFunctionBoolean_nonEmptyString_returnsTrue() throws Throwable {
        Object result = context.getValue("boolean('x')");
        assertEquals(Boolean.TRUE, result);
    }

    // functionBoolean: zero number is false
    @Test
    public void testFunctionBoolean_zeroNumber_returnsFalse() throws Throwable {
        Object result = context.getValue("boolean(0)");
        assertEquals(Boolean.FALSE, result);
    }

    // functionNot: negates the boolean value of its argument
    @Test
    public void testFunctionNot_negatesBooleanValue() throws Throwable {
        Object result = context.getValue("not(false())");
        assertEquals(Boolean.TRUE, result);
    }

    // functionTrue: always returns Boolean.TRUE
    @Test
    public void testFunctionTrue_returnsTrue() throws Throwable {
        Object result = context.getValue("true()");
        assertEquals(Boolean.TRUE, result);
    }

    // functionFalse: always returns Boolean.FALSE
    @Test
    public void testFunctionFalse_returnsFalse() throws Throwable {
        Object result = context.getValue("false()");
        assertEquals(Boolean.FALSE, result);
    }

    // functionNumber: valid numeric string parses to a double
    @Test
    public void testFunctionNumber_validString_parsesDouble() throws Throwable {
        Object result = context.getValue("number('42')");
        assertEquals(42.0, ((Double) result).doubleValue(), 1e-9);
    }

    // functionNumber: non-numeric string converts to NaN per XPath 1.0 contract
    @Test
    public void testFunctionNumber_invalidString_returnsNaN() throws Throwable {
        Object result = context.getValue("number('abc')");
        assertTrue(Double.isNaN(((Double) result).doubleValue()));
    }

    // functionSum: EvalContext branch sums node values
    @Test
    public void testFunctionSum_withNodeSet_sumsValues() throws Throwable {
        Object result = context.getValue("sum(numbers)");
        assertEquals(60.0, ((Double) result).doubleValue(), 1e-9);
    }

    // functionSum: null argument branch returns ZERO
    @Test
    public void testFunctionSum_nullArgument_returnsZero() throws Throwable {
        context.getVariables().declareVariable("nullVar", null);
        Object result = context.getValue("sum($nullVar)");
        assertEquals(0.0, ((Double) result).doubleValue(), 1e-9);
    }

    // functionSum: non-EvalContext, non-null argument -> throws JXPathException
    @Test
    public void testFunctionSum_invalidArgumentType_throws() throws Throwable {
        try {
            context.getValue("sum('abc')");
            fail("expected JXPathException");
        }
        catch (JXPathException expected) {
            assertTrue(expected.getMessage().indexOf("sum") >= 0);
        }
    }

    // functionFloor: rounds toward negative infinity for positive and negative values
    @Test
    public void testFunctionFloor_roundsTowardNegativeInfinity() throws Throwable {
        assertEquals(1.0, ((Double) context.getValue("floor(1.9)")).doubleValue(), 1e-9);
        assertEquals(-2.0, ((Double) context.getValue("floor(-1.1)")).doubleValue(), 1e-9);
    }

    // functionCeiling: rounds toward positive infinity for positive and negative values
    @Test
    public void testFunctionCeiling_roundsTowardPositiveInfinity() throws Throwable {
        assertEquals(2.0, ((Double) context.getValue("ceiling(1.1)")).doubleValue(), 1e-9);
        assertEquals(-1.0, ((Double) context.getValue("ceiling(-1.9)")).doubleValue(), 1e-9);
    }

    // functionRound: .5 rounds toward positive infinity per XPath 1.0 fn:round contract
    @Test
    public void testFunctionRound_halfRoundsTowardPositiveInfinity() throws Throwable {
        assertEquals(2.0, ((Double) context.getValue("round(2.4)")).doubleValue(), 1e-9);
        assertEquals(3.0, ((Double) context.getValue("round(2.5)")).doubleValue(), 1e-9);
    }

    // functionRound: XPath 1.0 contract requires round(NaN) to remain NaN
    @Test
    public void testFunctionRound_NaN_shouldRemainNaN() throws Throwable {
        Object result = context.getValue("round(0 div 0)");
        assertTrue(Double.isNaN(((Double) result).doubleValue()));
    }

    // functionRound: XPath 1.0 contract requires round(+infinity) to remain +infinity
    @Test
    public void testFunctionRound_positiveInfinity_shouldRemainInfinite() throws Throwable {
        Object result = context.getValue("round(1 div 0)");
        double v = ((Double) result).doubleValue();
        assertTrue(Double.isInfinite(v) && v > 0);
    }

    // functionFormatNumber: 2-arg form with explicit deterministic locale
    @Test
    public void testFunctionFormatNumber_basicPattern() throws Throwable {
        context.setLocale(Locale.US);
        Object result = context.getValue("format-number(1234.5,'#,##0.00')");
        assertEquals("1,234.50", result);
    }

    // functionFormatNumber: wrong argument count (neither 2 nor 3) -> throws
    @Test
    public void testFunctionFormatNumber_wrongArgCount_throws() throws Throwable {
        try {
            context.getValue("format-number(1)");
            fail("expected JXPathInvalidSyntaxException");
        }
        catch (JXPathInvalidSyntaxException expected) {
        }
    }
}
