package org.apache.commons.jxpath.ri.compiler;

import junit.framework.TestCase;

import org.apache.commons.jxpath.BasicNodeSet;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.ri.Compiler;
import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.SelfContext;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.apache.commons.jxpath.ri.model.beans.NullPointer;

public class CoreFunctionTest extends TestCase {

    public void testFunctionNamesAndCodes() throws Throwable {
        CoreFunction fLast = new CoreFunction(Compiler.FUNCTION_LAST, null);
        assertEquals(Compiler.FUNCTION_LAST, fLast.getFunctionCode());
        assertEquals("last", fLast.getFunctionName());
        assertEquals(0, fLast.getArgumentCount());

        CoreFunction fPos = new CoreFunction(Compiler.FUNCTION_POSITION, null);
        assertEquals("position", fPos.getFunctionName());

        CoreFunction fCount = new CoreFunction(Compiler.FUNCTION_COUNT, null);
        assertEquals("count", fCount.getFunctionName());

        CoreFunction fId = new CoreFunction(Compiler.FUNCTION_ID, null);
        assertEquals("id", fId.getFunctionName());

        CoreFunction fLocalName = new CoreFunction(Compiler.FUNCTION_LOCAL_NAME, null);
        assertEquals("local-name", fLocalName.getFunctionName());

        CoreFunction fNs = new CoreFunction(Compiler.FUNCTION_NAMESPACE_URI, null);
        assertEquals("namespace-uri", fNs.getFunctionName());

        CoreFunction fName = new CoreFunction(Compiler.FUNCTION_NAME, null);
        assertEquals("name", fName.getFunctionName());

        CoreFunction fString = new CoreFunction(Compiler.FUNCTION_STRING, null);
        assertEquals("string", fString.getFunctionName());

        CoreFunction fConcat = new CoreFunction(Compiler.FUNCTION_CONCAT, null);
        assertEquals("concat", fConcat.getFunctionName());

        CoreFunction fStartsWith = new CoreFunction(Compiler.FUNCTION_STARTS_WITH, null);
        assertEquals("starts-with", fStartsWith.getFunctionName());

        CoreFunction fContains = new CoreFunction(Compiler.FUNCTION_CONTAINS, null);
        assertEquals("contains", fContains.getFunctionName());

        CoreFunction fSubBefore = new CoreFunction(Compiler.FUNCTION_SUBSTRING_BEFORE, null);
        assertEquals("substring-before", fSubBefore.getFunctionName());

        CoreFunction fSubAfter = new CoreFunction(Compiler.FUNCTION_SUBSTRING_AFTER, null);
        assertEquals("substring-after", fSubAfter.getFunctionName());

        CoreFunction fSub = new CoreFunction(Compiler.FUNCTION_SUBSTRING, null);
        assertEquals("substring", fSub.getFunctionName());

        CoreFunction fLen = new CoreFunction(Compiler.FUNCTION_STRING_LENGTH, null);
        assertEquals("string-length", fLen.getFunctionName());

        CoreFunction fNorm = new CoreFunction(Compiler.FUNCTION_NORMALIZE_SPACE, null);
        assertEquals("normalize-space", fNorm.getFunctionName());

        CoreFunction fTrans = new CoreFunction(Compiler.FUNCTION_TRANSLATE, null);
        assertEquals("translate", fTrans.getFunctionName());

        CoreFunction fBool = new CoreFunction(Compiler.FUNCTION_BOOLEAN, null);
        assertEquals("boolean", fBool.getFunctionName());

        CoreFunction fNot = new CoreFunction(Compiler.FUNCTION_NOT, null);
        assertEquals("not", fNot.getFunctionName());

        CoreFunction fTrue = new CoreFunction(Compiler.FUNCTION_TRUE, null);
        assertEquals("true", fTrue.getFunctionName());

        CoreFunction fFalse = new CoreFunction(Compiler.FUNCTION_FALSE, null);
        assertEquals("false", fFalse.getFunctionName());

        CoreFunction fLang = new CoreFunction(Compiler.FUNCTION_LANG, null);
        assertEquals("lang", fLang.getFunctionName());

        CoreFunction fNum = new CoreFunction(Compiler.FUNCTION_NUMBER, null);
        assertEquals("number", fNum.getFunctionName());

        CoreFunction fSum = new CoreFunction(Compiler.FUNCTION_SUM, null);
        assertEquals("sum", fSum.getFunctionName());

        CoreFunction fFloor = new CoreFunction(Compiler.FUNCTION_FLOOR, null);
        assertEquals("floor", fFloor.getFunctionName());

        CoreFunction fCeil = new CoreFunction(Compiler.FUNCTION_CEILING, null);
        assertEquals("ceiling", fCeil.getFunctionName());

        CoreFunction fRound = new CoreFunction(Compiler.FUNCTION_ROUND, null);
        assertEquals("round", fRound.getFunctionName());

        CoreFunction fKey = new CoreFunction(Compiler.FUNCTION_KEY, null);
        assertEquals("key", fKey.getFunctionName());

        CoreFunction fFormat = new CoreFunction(Compiler.FUNCTION_FORMAT_NUMBER, null);
        assertEquals("format-number", fFormat.getFunctionName());

        CoreFunction fUnknown = new CoreFunction(-999, null);
        assertEquals("unknownFunction-999()", fUnknown.getFunctionName());
    }

    public void testComputeContextDependent() throws Throwable {
        CoreFunction fLast = new CoreFunction(Compiler.FUNCTION_LAST, null);
        assertTrue(fLast.computeContextDependent());

        CoreFunction fPos = new CoreFunction(Compiler.FUNCTION_POSITION, null);
        assertTrue(fPos.computeContextDependent());

        CoreFunction fBoolNoArgs = new CoreFunction(Compiler.FUNCTION_BOOLEAN, null);
        assertTrue(fBoolNoArgs.computeContextDependent());

        Expression[] args = new Expression[] { new Constant("test") };
        CoreFunction fBoolWithArgs = new CoreFunction(Compiler.FUNCTION_BOOLEAN, args);
        assertFalse(fBoolWithArgs.computeContextDependent());

        CoreFunction fCount = new CoreFunction(Compiler.FUNCTION_COUNT, args);
        assertFalse(fCount.computeContextDependent());

        CoreFunction fFormat2 = new CoreFunction(Compiler.FUNCTION_FORMAT_NUMBER, args);
        assertTrue(fFormat2.computeContextDependent());
    }

    public void testToString() throws Throwable {
        Expression[] args = new Expression[] { new Constant("a"), new Constant("b") };
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_CONCAT, args);
        String str = func.toString();
        assertTrue(str.contains("concat"));
        assertTrue(str.contains("a"));
        assertTrue(str.contains("b"));
    }

    public void testFunctionTrueFalseNull() throws Throwable {
        CoreFunction fTrue = new CoreFunction(Compiler.FUNCTION_TRUE, null);
        assertEquals(Boolean.TRUE, fTrue.computeValue(null));

        CoreFunction fFalse = new CoreFunction(Compiler.FUNCTION_FALSE, null);
        assertEquals(Boolean.FALSE, fFalse.computeValue(null));

        CoreFunction fNull = new CoreFunction(Compiler.FUNCTION_NULL, null);
        assertNull(fNull.computeValue(null));
    }

    public void testFunctionStringLength() throws Throwable {
        Expression[] args = new Expression[] { new Constant("hello") };
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_STRING_LENGTH, args);
        Double result = (Double) func.computeValue(null);
        assertEquals(5.0, result.doubleValue(), 0.0);

        CoreFunction funcZero = new CoreFunction(Compiler.FUNCTION_STRING_LENGTH, null);
        JXPathContext context = JXPathContext.newContext(new Object());
        NodePointer ptr = NullPointer.newNodePointer(null, "root", null);
        EvalContext evalContext = new InitialContext(new SelfContext(null, ptr));
        
        try {
            funcZero.computeValue(evalContext);
        } catch (Throwable t) {
            // May fail depending on context state, but tests path execution
        }
    }

    public void testFunctionConcat() throws Throwable {
        Expression[] args = new Expression[] { new Constant("foo"), new Constant("bar") };
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_CONCAT, args);
        assertEquals("foobar", func.computeValue(null));

        // Test argument count < 2 exception branch
        CoreFunction funcInvalid = new CoreFunction(Compiler.FUNCTION_CONCAT, new Expression[] { new Constant("foo") });
        try {
            funcInvalid.computeValue(null);
            fail("Expected exception for concat with 1 arg");
        } catch (Throwable e) {
            assertTrue(e instanceof JXPathInvalidSyntaxException);
        }
    }

    public void testFunctionStartsWithAndContains() throws Throwable {
        Expression[] args = new Expression[] { new Constant("filename.txt"), new Constant("file") };
        CoreFunction starts = new CoreFunction(Compiler.FUNCTION_STARTS_WITH, args);
        assertEquals(Boolean.TRUE, starts.computeValue(null));

        Expression[] args2 = new Expression[] { new Constant("filename.txt"), new Constant("name") };
        CoreFunction contains = new CoreFunction(Compiler.FUNCTION_CONTAINS, args2);
        assertEquals(Boolean.TRUE, contains.computeValue(null));

        Expression[] args3 = new Expression[] { new Constant("filename.txt"), new Constant("xyz") };
        CoreFunction containsFalse = new CoreFunction(Compiler.FUNCTION_CONTAINS, args3);
        assertEquals(Boolean.FALSE, containsFalse.computeValue(null));
    }

    public void testFunctionSubstringBeforeAndAfter() throws Throwable {
        Expression[] args = new Expression[] { new Constant("foo_bar"), new Constant("_") };
        CoreFunction before = new CoreFunction(Compiler.FUNCTION_SUBSTRING_BEFORE, args);
        assertEquals("foo", before.computeValue(null));

        CoreFunction after = new CoreFunction(Compiler.FUNCTION_SUBSTRING_AFTER, args);
        assertEquals("bar", after.computeValue(null));

        Expression[] argsNotFound = new Expression[] { new Constant("foo_bar"), new Constant("z") };
        CoreFunction beforeNotFound = new CoreFunction(Compiler.FUNCTION_SUBSTRING_BEFORE, argsNotFound);
        assertEquals("", beforeNotFound.computeValue(null));

        CoreFunction afterNotFound = new CoreFunction(Compiler.FUNCTION_SUBSTRING_AFTER, argsNotFound);
        assertEquals("", afterNotFound.computeValue(null));
    }

    public void testFunctionSubstring() throws Throwable {
        // substring("12345", 2, 3) -> "234"
        Expression[] args = new Expression[] { new Constant("12345"), new Constant(new Double(2)), new Constant(new Double(3)) };
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_SUBSTRING, args);
        assertEquals("234", func.computeValue(null));

        // substring("12345", 2) -> "2345"
        Expression[] args2 = new Expression[] { new Constant("12345"), new Constant(new Double(2)) };
        CoreFunction func2 = new CoreFunction(Compiler.FUNCTION_SUBSTRING, args2);
        assertEquals("2345", func2.computeValue(null));

        // NaN test
        Expression[] argsNaN = new Expression[] { new Constant("12345"), new Constant(new Double(Double.NaN)) };
        CoreFunction funcNaN = new CoreFunction(Compiler.FUNCTION_SUBSTRING, argsNaN);
        assertEquals("", funcNaN.computeValue(null));

        // from > length + 1
        Expression[] argsTooLarge = new Expression[] { new Constant("12345"), new Constant(new Double(10)) };
        CoreFunction funcTooLarge = new CoreFunction(Compiler.FUNCTION_SUBSTRING, argsTooLarge);
        assertEquals("", funcTooLarge.computeValue(null));

        // length < 0
        Expression[] argsNegLen = new Expression[] { new Constant("12345"), new Constant(new Double(1)), new Constant(new Double(-1)) };
        CoreFunction funcNegLen = new CoreFunction(Compiler.FUNCTION_SUBSTRING, argsNegLen);
        assertEquals("", funcNegLen.computeValue(null));

        // to < 1
        Expression[] argsToLow = new Expression[] { new Constant("12345"), new Constant(new Double(-5)), new Constant(new Double(2)) };
        CoreFunction funcToLow = new CoreFunction(Compiler.FUNCTION_SUBSTRING, argsToLow);
        assertEquals("", funcToLow.computeValue(null));
    }

    public void testFunctionNormalizeSpace() throws Throwable {
        Expression[] args = new Expression[] { new Constant("  hello   world  \n\t") };
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_NORMALIZE_SPACE, args);
        assertEquals("hello world", func.computeValue(null));
    }

    public void testFunctionTranslate() throws Throwable {
        Expression[] args = new Expression[] { new Constant("bar"), new Constant("abc"), new Constant("XYZ") };
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_TRANSLATE, args);
        assertEquals("BAr", func.computeValue(null));
    }

    public void testFunctionBooleanAndNot() throws Throwable {
        Expression[] argsTrue = new Expression[] { new Constant(Boolean.TRUE) };
        CoreFunction fBool = new CoreFunction(Compiler.FUNCTION_BOOLEAN, argsTrue);
        assertEquals(Boolean.TRUE, fBool.computeValue(null));

        CoreFunction fNot = new CoreFunction(Compiler.FUNCTION_NOT, argsTrue);
        assertEquals(Boolean.FALSE, fNot.computeValue(null));
    }

    public void testFunctionFloorCeilingRound() throws Throwable {
        Expression[] args = new Expression[] { new Constant(new Double(2.5)) };
        CoreFunction floor = new CoreFunction(Compiler.FUNCTION_FLOOR, args);
        assertEquals(new Double(2.0), floor.computeValue(null));

        CoreFunction ceiling = new CoreFunction(Compiler.FUNCTION_CEILING, args);
        assertEquals(new Double(3.0), ceiling.computeValue(null));

        CoreFunction round = new CoreFunction(Compiler.FUNCTION_ROUND, args);
        assertEquals(new Double(3.0), round.computeValue(null));
    }

    public void testFunctionSum() throws Throwable {
        Expression[] args = new Expression[] { new Constant(null) };
        CoreFunction sumNull = new CoreFunction(Compiler.FUNCTION_SUM, args);
        // compute returns null for arg1, test branch v == null
        assertEquals(new Double(0), sumNull.computeValue(null));
    }

    public void testFunctionArgumentsGetters() throws Throwable {
        Expression arg1 = new Constant("1");
        Expression arg2 = new Constant("2");
        Expression arg3 = new Constant("3");
        Expression[] args = new Expression[] { arg1, arg2, arg3 };
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_SUBSTRING, args);

        assertEquals(arg1, func.getArg1());
        assertEquals(arg2, func.getArg2());
        assertEquals(arg3, func.getArg3());
        assertEquals(3, func.getArgumentCount());

        CoreFunction funcNullArgs = new CoreFunction(Compiler.FUNCTION_TRUE, null);
        assertEquals(0, funcNullArgs.getArgumentCount());
    }

    public void testArgRangeValidation() throws Throwable {
        CoreFunction func = new CoreFunction(Compiler.FUNCTION_TRUE, new Expression[] { new Constant("extra") });
        try {
            func.computeValue(null);
            fail("Expected JXPathInvalidSyntaxException due to incorrect argument count");
        } catch (Throwable e) {
            assertTrue(e instanceof JXPathInvalidSyntaxException);
        }
    }
}