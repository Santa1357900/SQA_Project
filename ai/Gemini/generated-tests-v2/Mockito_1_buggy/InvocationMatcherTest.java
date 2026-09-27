package org.mockito.internal.invocation;

import org.junit.Test;
import org.hamcrest.Matcher;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.Location;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class InvocationMatcherTest {

    // Dummy implementation of Invocation for testing purposes
    private static class DummyInvocation implements Invocation {
        private final Object mock;
        private final Method method;
        private final Object[] arguments;
        private final boolean verified;
        private final Location location;

        public DummyInvocation(Object mock, Method method, Object[] arguments, boolean verified, Location location) {
            this.mock = mock;
            this.method = method;
            this.arguments = arguments;
            this.verified = verified;
            this.location = location;
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

        public Object[] getRawArguments() {
            return arguments;
        }

        public <T> T getArgumentAt(int index, Class<T> clazz) {
            return (T) arguments[index];
        }

        public boolean isVerified() {
            return verified;
        }

        public Location getLocation() {
            return location;
        }

        public void markVerified() {}

        public StubInfo getStubInfo() {
            return null;
        }

        public Integer getSequenceNumber() {
            return 0;
        }

        public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
            return null;
        }
    }

    public void sampleMethod() {}
    public void sampleMethodWithParam(String p) {}
    public void sampleVarargsMethod(Object... p) {}

    @Test
    public void testConstructorAndGettersWithEmptyMatchers() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethod");
        Object mock = new Object();
        DummyInvocation invocation = new DummyInvocation(mock, method, new Object[0], false, null);

        InvocationMatcher invocationMatcher = new InvocationMatcher(invocation, Collections.<Matcher>emptyList());

        assertEquals(method, invocationMatcher.getMethod());
        assertEquals(invocation, invocationMatcher.getInvocation());
        assertNotNull(invocationMatcher.getMatchers());
        assertNotNull(invocationMatcher.getLocation());
        assertNotNull(invocationMatcher.toString());
    }

    @Test
    public void testConstructorWithProvidedMatchers() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethod");
        Object mock = new Object();
        DummyInvocation invocation = new DummyInvocation(mock, method, new Object[0], false, null);
        List<Matcher> matchers = new ArrayList<Matcher>();

        InvocationMatcher invocationMatcher = new InvocationMatcher(invocation, matchers);

        assertEquals(matchers, invocationMatcher.getMatchers());
    }

    @Test
    public void testSingleInvocationConstructor() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethod");
        Object mock = new Object();
        DummyInvocation invocation = new DummyInvocation(mock, method, new Object[0], false, null);

        InvocationMatcher invocationMatcher = new InvocationMatcher(invocation);

        assertEquals(invocation, invocationMatcher.getInvocation());
        assertNotNull(invocationMatcher.getMatchers());
    }

    @Test
    public void testHasSameMethod() throws Throwable {
        Method method1 = InvocationMatcherTest.class.getMethod("sampleMethodWithParam", String.class);
        Method method2 = InvocationMatcherTest.class.getMethod("sampleMethodWithParam", String.class);
        Method method3 = InvocationMatcherTest.class.getMethod("sampleMethod");

        Object mock = new Object();
        DummyInvocation inv1 = new DummyInvocation(mock, method1, new Object[]{"test"}, false, null);
        DummyInvocation inv2 = new DummyInvocation(mock, method2, new Object[]{"other"}, false, null);
        DummyInvocation inv3 = new DummyInvocation(mock, method3, new Object[0], false, null);

        InvocationMatcher matcher = new InvocationMatcher(inv1);

        assertTrue(matcher.hasSameMethod(inv1));
        assertTrue(matcher.hasSameMethod(inv2));
        assertFalse(matcher.hasSameMethod(inv3));
    }

    @Test
    public void testMatches() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethodWithParam", String.class);
        Object mock1 = new Object();
        Object mock2 = new Object();

        DummyInvocation inv1 = new DummyInvocation(mock1, method, new Object[]{"test"}, false, null);
        DummyInvocation inv2 = new DummyInvocation(mock1, method, new Object[]{"test"}, false, null);
        DummyInvocation inv3 = new DummyInvocation(mock2, method, new Object[]{"test"}, false, null);

        InvocationMatcher matcher = new InvocationMatcher(inv1);

        assertTrue(matcher.matches(inv2));
        assertFalse(matcher.matches(inv3));
    }

    @Test
    public void testHasSimilarMethod() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethodWithParam", String.class);
        Object mock = new Object();

        DummyInvocation inv1 = new DummyInvocation(mock, method, new Object[]{"test"}, false, null);
        DummyInvocation inv2 = new DummyInvocation(mock, method, new Object[]{"other"}, false, null);
        DummyInvocation invVerified = new DummyInvocation(mock, method, new Object[]{"other"}, true, null);

        InvocationMatcher matcher = new InvocationMatcher(inv1);

        assertFalse(matcher.hasSimilarMethod(invVerified));
        assertTrue(matcher.hasSimilarMethod(inv2));
    }

    @Test
    public void testCaptureArgumentsFromNonVarargs() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethodWithParam", String.class);
        Object mock = new Object();
        DummyInvocation invocation = new DummyInvocation(mock, method, new Object[]{"arg"}, false, null);

        InvocationMatcher matcher = new InvocationMatcher(invocation);
        // Should execute without exception even if matchers do not implement CapturesArguments
        matcher.captureArgumentsFrom(invocation);
    }

    @Test
    public void testCaptureArgumentsFromVarargsUnsupported() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleVarargsMethod", Object[].class);
        Object mock = new Object();
        DummyInvocation invocation = new DummyInvocation(mock, method, new Object[]{new Object[]{}}, false, null);

        InvocationMatcher matcher = new InvocationMatcher(invocation);
        try {
            matcher.captureArgumentsFrom(invocation);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testCreateFromList() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethod");
        Object mock = new Object();
        DummyInvocation inv = new DummyInvocation(mock, method, new Object[0], false, null);

        List<Invocation> list = new ArrayList<Invocation>();
        list.add(inv);

        List<InvocationMatcher> matchers = InvocationMatcher.createFrom(list);
        assertEquals(1, matchers.size());
        assertEquals(inv, matchers.get(0).getInvocation());
    }
}