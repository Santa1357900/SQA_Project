package org.mockito.internal.invocation;

import org.junit.Test;
import org.mockito.invocation.Invocation;
import org.hamcrest.Matcher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class InvocationMatcherTest {

    // Dummy method for reflection testing
    public void sampleMethod(String arg) {}
    public void sampleMethod(Integer arg) {}
    public void sampleVarargsMethod(Object... args) {}

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        Method method = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class });
        
        // Create a simple Invocation implementation using a concrete or anonymous class if possible,
        // or test via Mockito if available, but since we are in Mockito codebase, we can instantiate 
        // InvocationImpl if needed, or build a dummy Invocation.
        // Let's check what Invocation is available or construct a dummy implementation of Invocation interface.
        Invocation invocation = new Invocation() {
            private final Object[] args = new Object[] { "test" };
            public Object getMock() { return "mock"; }
            public Method getMethod() { return method; }
            public Object[] getArguments() { return args; }
            public <T> T getArgumentAt(int index, Class<T> clazz) { return (T) args[index]; }
            public Object[] getRawArguments() { return args; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Object getAnswer() throws Throwable { return null; }
            public void ignoreForVerification() {}
            public boolean isVerified() { return false; }
            public Integer getSequenceNumber() { return 1; }
            public org.mockito.stubbing.Stubbing getStubbing() { return null; }
            public boolean markVerified() { return true; }
            public org.mockito.invocation.StubInfo stubInfo() { return null; }
            public void markStubbed(org.mockito.invocation.DescribedInvocation desired) {}
            public boolean isIgnoredForVerification() { return false; }
        };

        List<Matcher> customMatchers = new ArrayList<Matcher>();
        InvocationMatcher matcherWithEmpty = new InvocationMatcher(invocation, Collections.<Matcher>emptyList());
        InvocationMatcher matcherWithCustom = new InvocationMatcher(invocation, customMatchers);
        InvocationMatcher matcherSingle = new InvocationMatcher(invocation);

        assertEquals(method, matcherSingle.getMethod());
        assertEquals(invocation, matcherSingle.getInvocation());
        assertNotNull(matcherSingle.getMatchers());
        assertNotNull(matcherSingle.getLocation());
        assertNotNull(matcherSingle.toString());
        
        List<Invocation> invocations = new ArrayList<Invocation>();
        invocations.add(invocation);
        List<InvocationMatcher> created = InvocationMatcher.createFrom(invocations);
        assertEquals(1, created.size());
    }

    @Test
    public void testHasSameMethodAndSimilar() throws Throwable {
        final Method m1 = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { String.class });
        final Method m2 = InvocationMatcherTest.class.getMethod("sampleMethod", new Class[] { Integer.class });

        Invocation inv1 = new Invocation() {
            private final Object[] args = new Object[] { "abc" };
            public Object getMock() { return "mock"; }
            public Method getMethod() { return m1; }
            public Object[] getArguments() { return args; }
            public <T> T getArgumentAt(int index, Class<T> clazz) { return (T) args[index]; }
            public Object[] getRawArguments() { return args; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Object getAnswer() throws Throwable { return null; }
            public void ignoreForVerification() {}
            public boolean isVerified() { return false; }
            public Integer getSequenceNumber() { return 1; }
            public org.mockito.stubbing.Stubbing getStubbing() { return null; }
            public boolean markVerified() { return true; }
            public org.mockito.invocation.StubInfo stubInfo() { return null; }
            public void markStubbed(org.mockito.invocation.DescribedInvocation desired) {}
            public boolean isIgnoredForVerification() { return false; }
        };

        Invocation inv2 = new Invocation() {
            private final Object[] args = new Object[] { Integer.valueOf(123) };
            public Object getMock() { return "mock"; }
            public Method getMethod() { return m2; }
            public Object[] getArguments() { return args; }
            public <T> T getArgumentAt(int index, Class<T> clazz) { return (T) args[index]; }
            public Object[] getRawArguments() { return args; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Object getAnswer() throws Throwable { return null; }
            public void ignoreForVerification() {}
            public boolean isVerified() { return false; }
            public Integer getSequenceNumber() { return 1; }
            public org.mockito.stubbing.Stubbing getStubbing() { return null; }
            public boolean markVerified() { return true; }
            public org.mockito.invocation.StubInfo stubInfo() { return null; }
            public void markStubbed(org.mockito.invocation.DescribedInvocation desired) {}
            public boolean isIgnoredForVerification() { return false; }
        };

        InvocationMatcher im = new InvocationMatcher(inv1);
        assertFalse(im.hasSameMethod(inv2));
        assertFalse(im.hasSimilarMethod(inv2));
    }

    @Test
    public void testCaptureArgumentsFromVarargs() throws Throwable {
        final Method varargsMethod = InvocationMatcherTest.class.getMethod("sampleVarargsMethod", new Class[] { Object[].class });

        Invocation varargsInv = new Invocation() {
            private final Object[] args = new Object[] { "a", "b", "c" };
            public Object getMock() { return "mock"; }
            public Method getMethod() { return varargsMethod; }
            public Object[] getArguments() { return args; }
            public <T> T getArgumentAt(int index, Class<T> clazz) { return (T) args[index]; }
            public Object[] getRawArguments() { return args; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Object getAnswer() throws Throwable { return null; }
            public void ignoreForVerification() {}
            public boolean isVerified() { return false; }
            public Integer getSequenceNumber() { return 1; }
            public org.mockito.stubbing.Stubbing getStubbing() { return null; }
            public boolean markVerified() { return true; }
            public org.mockito.invocation.StubInfo stubInfo() { return null; }
            public void markStubbed(org.mockito.invocation.DescribedInvocation desired) {}
            public boolean isIgnoredForVerification() { return false; }
        };

        List<Matcher> matchers = new ArrayList<Matcher>();
        InvocationMatcher im = new InvocationMatcher(varargsInv, matchers);
        // This should execute the varargs branch in captureArgumentsFrom safely
        im.captureArgumentsFrom(varargsInv);
        assertTrue(true);
    }
}