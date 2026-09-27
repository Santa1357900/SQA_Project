package org.apache.commons.jxpath.ri.compiler;

import junit.framework.TestCase;
import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.axes.InitialContext;
import org.apache.commons.jxpath.ri.axes.RootContext;
import org.apache.commons.jxpath.ri.InfoSetUtil;
import org.apache.commons.jxpath.ri.JXPathContextReferenceImpl;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.Pointer;

public class CoreOperationGreaterThanTest extends TestCase {

    public void testComputeValueGreaterThan() throws Throwable {
        Constant arg1 = new Constant(Double.valueOf(5.0));
        Constant arg2 = new Constant(Double.valueOf(3.0));
        CoreOperationGreaterThan op = new CoreOperationGreaterThan(arg1, arg2);

        JXPathContext context = JXPathContext.newContext(null);
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        Object result = op.computeValue(evalContext);
        assertEquals(Boolean.TRUE, result);
    }

    public void testComputeValueNotGreaterThan() throws Throwable {
        Constant arg1 = new Constant(Double.valueOf(2.0));
        Constant arg2 = new Constant(Double.valueOf(3.0));
        CoreOperationGreaterThan op = new CoreOperationGreaterThan(arg1, arg2);

        JXPathContext context = JXPathContext.newContext(null);
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        Object result = op.computeValue(evalContext);
        assertEquals(Boolean.FALSE, result);
    }

    public void testComputeValueEqual() throws Throwable {
        Constant arg1 = new Constant(Double.valueOf(3.0));
        Constant arg2 = new Constant(Double.valueOf(3.0));
        CoreOperationGreaterThan op = new CoreOperationGreaterThan(arg1, arg2);

        JXPathContext context = JXPathContext.newContext(null);
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        Object result = op.computeValue(evalContext);
        assertEquals(Boolean.FALSE, result);
    }

    public void testGetSymbol() throws Throwable {
        Constant arg1 = new Constant(Double.valueOf(1.0));
        Constant arg2 = new Constant(Double.valueOf(1.0));
        CoreOperationGreaterThan op = new CoreOperationGreaterThan(arg1, arg2);

        assertEquals(">", op.getSymbol());
    }

    public void testComputeValueWithStrings() throws Throwable {
        Constant arg1 = new Constant("10");
        Constant arg2 = new Constant("2");
        CoreOperationGreaterThan op = new CoreOperationGreaterThan(arg1, arg2);

        JXPathContext context = JXPathContext.newContext(null);
        EvalContext evalContext = new RootContext((JXPathContextReferenceImpl) context, null);

        Object result = op.computeValue(evalContext);
        assertEquals(Boolean.TRUE, result);
    }
}