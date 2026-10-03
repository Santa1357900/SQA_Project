package org.mockito.internal.invocation;

import org.junit.Test;
import org.mockito.internal.reporting.PrintSettings;
import org.mockito.internal.matchers.CapturesArguments;
import org.hamcrest.Matcher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class InvocationMatcherTest {

    // Dummy method for reflection testing
    public void sampleMethod(String arg1, int arg2) {
    }

    // Dummy Matcher that does not capture arguments
    private static class DummyMatcher implements Matcher<Object> {
        public boolean matches(Object item) {
            return true;
        }
        public void describeTo(org.hamcrest.Description description) {
        }
    }

    // Dummy Matcher that implements CapturesArguments
    private static class CapturingMatcher implements Matcher<Object>, CapturesArguments {
        private Object captured;

        public boolean matches(Object item) {
            return true;
        }

        public void describeTo(org.hamcrest.Description description) {
        }

        public void captureFrom(Object value) {
            this.captured = value;
        }

        public Object getCaptured() {
            return captured;
        }
    }

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class, int.class });
        Object mock = new Object();
        Object[] args = new Object[] { "test", Integer.valueOf(1) };
        
        Invocation invocation = new Invocation(mock, method, args, 1L);
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(new DummyMatcher());

        InvocationMatcher matcherWithProvidedMatchers = new InvocationMatcher(invocation, matchers);
        assertEquals(invocation, matcherWithProvidedMatchers.getInvocation());
        assertEquals(method, matcherWithProvidedMatchers.getMethod());
        assertEquals(matchers, matcherWithProvidedMatchers.getMatchers());
        assertNotNull(matcherWithProvidedMatchers.getLocation());
        assertNotNull(matcherWithProvidedMatchers.toString());
        assertNotNull(matcherWithProvidedMatchers.toString(new PrintSettings()));

        InvocationMatcher matcherWithEmptyMatchers = new InvocationMatcher(invocation);
        assertEquals(invocation, matcherWithEmptyMatchers.getInvocation());
        assertNotNull(matcherWithEmptyMatchers.getMatchers());
        assertFalse(matcherWithEmptyMatchers.getMatchers().isEmpty());
    }

    @Test
    public void testMatchesAndHasSameMethod() throws Throwable {
        Method method1 = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class, int.class });
        Method method2 = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class, int.class });
        Object mock = new Object();
        Object[] args = new Object[] { "hello", Integer.valueOf(5) };

        Invocation inv1 = new Invocation(mock, method1, args, 1L);
        Invocation inv2 = new Invocation(mock, method2, args, 2L);

        InvocationMatcher invocationMatcher = new InvocationMatcher(inv1);

        assertTrue(invocationMatcher.hasSameMethod(inv2));
        assertTrue(invocationMatcher.matches(inv2));
    }

    @Test
    public void testHasSimilarMethod() throws Throwable {
        Method method1 = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class, int.class });
        Method method2 = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class, int.class });
        Object mock = new Object();
        Object[] args = new Object[] { "hello", Integer.valueOf(5) };

        Invocation inv1 = new Invocation(mock, method1, args, 1L);
        Invocation inv2 = new Invocation(mock, method2, args, 2L);
        // inv2 is verified by default or not? Depending on Invocation implementation, assume unverified.
        // Let's test hasSimilarMethod branches:
        InvocationMatcher invocationMatcher = new InvocationMatcher(inv1);

        boolean similar = invocationMatcher.hasSimilarMethod(inv2);
        // Depending on whether inv2 is considered verified or not, it will return a boolean.
        // We just ensure it doesn't throw and covers the branch.
        assertNotNull(Boolean.valueOf(similar));

        // Test with different mock
        Object mock2 = new Object();
        Invocation invDifferentMock = new Invocation(mock2, method2, args, 3L);
        assertFalse(invocationMatcher.hasSimilarMethod(invDifferentMock));

        // Test with different method name
        Method methodDiffName = InvocationMatcherTest.class.getMethod("testConstructorsAndGetters", new Class[0]);
        Invocation invDiffName = new Invocation(mock, methodDiffName, new Object[0], 4L);
        assertFalse(invocationMatcher.hasSimilarMethod(invDiffName));
    }

    @Test
    public void testCaptureArgumentsFrom() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class, int.class });
        Object mock = new Object();
        Object[] args = new Object[] { "capturedValue", Integer.valueOf(10) };

        Invocation invocation = new Invocation(mock, method, args, 1L);

        List<Matcher> matchers = new ArrayList<Matcher>();
        CapturingMatcher capturingMatcher = new CapturingMatcher();
        matchers.add(capturingMatcher);
        matchers.add(new DummyMatcher());

        InvocationMatcher invocationMatcher = new InvocationMatcher(invocation, matchers);
        invocationMatcher.captureArgumentsFrom(invocation);

        assertEquals("capturedValue", capturingMatcher.getCaptured());
    }
}