package org.mockito.internal.stubbing.defaultanswers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.cglib.proxy.MethodInterceptor;
import org.mockito.cglib.proxy.MethodProxy;

public class ReturnsSmartNullsTest {

    private static class DummyInvocation implements InvocationOnMock {
        private static final long serialVersionUID = 1L;
        private final Method method;
        private final Object[] arguments;
        private final Object mock;

        public DummyInvocation(Method method, Object[] arguments, Object mock) {
            this.method = method;
            this.arguments = arguments;
            this.mock = mock;
        }

        public Object getMock() {
            return mock;
        }

        public Method getMethod() {
            return method;
        }

        public Object[] getArguments() {
            return arguments;
        }

        public <T> T getArgumentAt(int index, Class<T> clazz) {
            return clazz.cast(arguments[index]);
        }
        
        public Object callRealMethod() throws Throwable {
            return null;
        }
    }

    public interface SampleInterface {
        String nonMockableReturnType();
        SampleInterface mockableReturnType();
    }

    public final class FinalClass {
        public String someMethod() {
            return "final";
        }
    }

    public interface AnotherInterface {
        FinalClass finalReturnType();
    }

    @Test
    public void testAnswerReturnsDefaultWhenDelegateProvidesValue() throws Throwable {
        ReturnsSmartNulls returnsSmartNulls = new ReturnsSmartNulls();
        Method method = String.class.getMethod("toString", new Class[0]);
        InvocationOnMock invocation = new DummyInvocation(method, new Object[0], new Object());

        Object result = returnsSmartNulls.answer(invocation);
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    public void testAnswerReturnsNullWhenTypeNotImposterisable() throws Throwable {
        ReturnsSmartNulls returnsSmartNulls = new ReturnsSmartNulls();
        Method method = SampleInterface.class.getMethod("nonMockableReturnType", new Class[0]);
        InvocationOnMock invocation = new DummyInvocation(method, new Object[0], new Object());

        Object result = returnsSmartNulls.answer(invocation);
        assertNull(result);
    }

    @Test
    public void testAnswerReturnsNullWhenReturnTypeIsFinalClass() throws Throwable {
        ReturnsSmartNulls returnsSmartNulls = new ReturnsSmartNulls();
        Method method = AnotherInterface.class.getMethod("finalReturnType", new Class[0]);
        InvocationOnMock invocation = new DummyInvocation(method, new Object[0], new Object());

        Object result = returnsSmartNulls.answer(invocation);
        assertNull(result);
    }

    @Test
    public void testAnswerReturnsSmartNullWhenMockable() throws Throwable {
        ReturnsSmartNulls returnsSmartNulls = new ReturnsSmartNulls();
        Method method = SampleInterface.class.getMethod("mockableReturnType", new Class[0]);
        InvocationOnMock invocation = new DummyInvocation(method, new Object[0], new Object());

        Object result = returnsSmartNulls.answer(invocation);
        assertNotNull(result);

        MethodInterceptor interceptor = null;
        if (result instanceof org.mockito.cglib.proxy.Factory) {
            org.mockito.cglib.proxy.Callback callback = ((org.mockito.cglib.proxy.Factory) result).getCallback(0);
            if (callback instanceof MethodInterceptor) {
                interceptor = (MethodInterceptor) callback;
            }
        }

        assertNotNull(interceptor);

        Method toStringMethod = Object.class.getMethod("toString", new Class[0]);
        Object toStringResult = interceptor.intercept(result, toStringMethod, new Object[0], null);
        assertNotNull(toStringResult);
        assertTrue(((String) toStringResult).contains("SmartNull returned by unstubbed"));

        try {
            Method dummyMethod = Object.class.getMethod("hashCode", new Class[0]);
            interceptor.intercept(result, dummyMethod, new Object[0], null);
            fail("Should have thrown an exception");
        } catch (Throwable e) {
            assertTrue(e.getClass().getName().contains("SmartNullPointerException") || e instanceof RuntimeException);
        }
    }
}