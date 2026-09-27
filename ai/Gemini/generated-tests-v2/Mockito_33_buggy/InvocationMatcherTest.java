package org.mockito.internal.invocation;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.mockito.internal.reporting.PrintSettings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class InvocationMatcherTest {

    private static class DummyInvocation extends Invocation {
        private final Object mock;
        private final Method method;
        private final Object[] arguments;
        private final boolean verified;

        public DummyInvocation(Object mock, Method method, Object[] arguments, boolean verified) {
            super(mock, null, method, arguments, null, 0);
            this.mock = mock;
            this.method = method;
            this.arguments = arguments;
            this.verified = verified;
        }

        @Override
        public Object getMock() {
            return mock;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public boolean isVerified() {
            return verified;
        }

        @Override
        public List<Matcher> argumentsToMatchers() {
            List<Matcher> list = new ArrayList<Matcher>();
            return list;
        }

        @Override
        public String toString(List<Matcher> matchers, PrintSettings printSettings) {
            return "DummyInvocationToString";
        }
    }

    private static class DummyMatcher implements Matcher, org.mockito.internal.matchers.CapturesArguments {
        private Object captured;

        public boolean matches(Object item) {
            return true;
        }

        public void _satisfies_pragma_() {}

        public void describeTo(org.hamcrest.Description description) {
        }

        public void captureFrom(Object value) {
            this.captured = value;
        }

        public Object getCaptured() {
            return captured;
        }
    }

    public void _dummyMethod() {}

    public void _dummyMethodWithArg(String arg) {}

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        Object mock = new Object();
        Method method = InvocationMatcherTest.class.getMethod("_dummyMethod", new Class[0]);
        Object[] args = new Object[0];
        Invocation invocation = new DummyInvocation(mock, method, args, false);

        List<Matcher> emptyMatchers = Collections.emptyList();
        InvocationMatcher matcher1 = new InvocationMatcher(invocation, emptyMatchers);
        assertNotNull(matcher1.getInvocation());
        assertEquals(method, matcher1.getMethod());
        assertNotNull(matcher1.getMatchers());

        List<Matcher> customMatchers = new ArrayList<Matcher>();
        DummyMatcher dummyMatcher = new DummyMatcher();
        customMatchers.add(dummyMatcher);
        InvocationMatcher matcher2 = new InvocationMatcher(invocation, customMatchers);
        assertEquals(1, matcher2.getMatchers().size());
        assertEquals(dummyMatcher, matcher2.getMatchers().get(0));

        InvocationMatcher matcher3 = new InvocationMatcher(invocation);
        assertNotNull(matcher3.getMatchers());
    }

    @Test
    public void testToStringMethods() throws Throwable {
        Object mock = new Object();
        Method method = InvocationMatcherTest.class.getMethod("_dummyMethod", new Class[0]);
        Invocation invocation = new DummyInvocation(mock, method, new Object[0], false);
        InvocationMatcher matcher = new InvocationMatcher(invocation);

        assertEquals("DummyInvocationToString", matcher.toString());
        assertEquals("DummyInvocationToString", matcher.toString(new PrintSettings()));
    }

    @Test
    public void testMatches() throws Throwable {
        Object mock1 = new Object();
        Object mock2 = new Object();
        Method method1 = InvocationMatcherTest.class.getMethod("_dummyMethod", new Class[0]);
        Method method2 = InvocationMatcherTest.class.getMethod("_dummyMethodWithArg", new Class[] { String.class });

        Invocation inv1 = new DummyInvocation(mock1, method1, new Object[0], false);
        Invocation inv1Duplicate = new DummyInvocation(mock1, method1, new Object[0], false);
        Invocation invDifferentMock = new DummyInvocation(mock2, method1, new Object[0], false);
        Invocation invDifferentMethod = new DummyInvocation(mock1, method2, new Object[] { "test" }, false);

        InvocationMatcher matcher = new InvocationMatcher(inv1);

        assertTrue(matcher.matches(inv1Duplicate));
        assertFalse(matcher.matches(invDifferentMock));
        assertFalse(matcher.matches(invDifferentMethod));
    }

    @Test
    public void testHasSameMethod() throws Throwable {
        Object mock = new Object();
        Method method1 = InvocationMatcherTest.class.getMethod("_dummyMethod", new Class[0]);
        Method method2 = InvocationMatcherTest.class.getMethod("_dummyMethodWithArg", new Class[] { String.class });

        Invocation inv1 = new DummyInvocation(mock, method1, new Object[0], false);
        Invocation inv1Dup = new DummyInvocation(mock, method1, new Object[0], false);
        Invocation inv2 = new DummyInvocation(mock, method2, new Object[] { "test" }, false);

        InvocationMatcher matcher = new InvocationMatcher(inv1);

        assertTrue(matcher.hasSameMethod(inv1Dup));
        assertFalse(matcher.hasSameMethod(inv2));
    }

    @Test
    public void testHasSimilarMethod() throws Throwable {
        Object mock1 = new Object();
        Object mock2 = new Object();
        Method method1 = InvocationMatcherTest.class.getMethod("_dummyMethodWithArg", new Class[] { String.class });
        Method method2 = InvocationMatcherTest.class.getMethod("_dummyMethod", new Class[0]);

        Invocation unverifiedInv = new DummyInvocation(mock1, method1, new Object[] { "arg" }, false);
        Invocation verifiedInv = new DummyInvocation(mock1, method1, new Object[] { "arg" }, true);
        Invocation diffMockInv = new DummyInvocation(mock2, method1, new Object[] { "arg" }, false);
        Invocation diffNameInv = new DummyInvocation(mock1, method2, new Object[0], false);

        InvocationMatcher matcher = new InvocationMatcher(unverifiedInv);

        // Same method name, unverified, same mock -> similar
        assertTrue(matcher.hasSimilarMethod(unverifiedInv));

        // Verified -> not similar
        assertFalse(matcher.hasSimilarMethod(verifiedInv));

        // Different mock -> not similar
        assertFalse(matcher.hasSimilarMethod(diffMockInv));

        // Different method name -> not similar
        assertFalse(matcher.hasSimilarMethod(diffNameInv));
    }

    @Test
    public void testGetLocation() throws Throwable {
        Object mock = new Object();
        Method method = InvocationMatcherTest.class.getMethod("_dummyMethod", new Class[0]);
        Invocation invocation = new DummyInvocation(mock, method, new Object[0], false);
        InvocationMatcher matcher = new InvocationMatcher(invocation);

        assertNull(matcher.getLocation());
    }

    @Test
    public void testCaptureArgumentsFrom() throws Throwable {
        Object mock = new Object();
        Method method = InvocationMatcherTest.class.getMethod("_dummyMethodWithArg", new Class[] { String.class });
        
        DummyMatcher dummyMatcher = new DummyMatcher();
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(dummyMatcher);

        Invocation baseInv = new DummyInvocation(mock, method, new Object[] { "initial" }, false);
        InvocationMatcher matcher = new InvocationMatcher(baseInv, matchers);

        Invocation targetInv = new DummyInvocation(mock, method, new Object[] { "capturedValue" }, false);
        matcher.captureArgumentsFrom(targetInv);

        assertEquals("capturedValue", dummyMatcher.getCaptured());
    }

    @Test
    public void testCreateFrom() throws Throwable {
        Object mock = new Object();
        Method method = InvocationMatcherTest.class.getMethod("_dummyMethod", new Class[0]);
        
        List<Invocation> invocations = new ArrayList<Invocation>();
        invocations.add(new DummyInvocation(mock, method, new Object[0], false));
        invocations.add(new DummyInvocation(mock, method, new Object[0], false));

        List<InvocationMatcher> matchers = InvocationMatcher.createFrom(invocations);
        assertNotNull(matchers);
        assertEquals(2, matchers.size());
    }
}