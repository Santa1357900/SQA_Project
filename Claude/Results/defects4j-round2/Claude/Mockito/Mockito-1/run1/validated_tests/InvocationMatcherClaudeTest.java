package org.mockito.internal.invocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.mockito.internal.matchers.CapturesArguments;
import org.mockito.invocation.Invocation;

import static org.junit.Assert.*;

public class InvocationMatcherClaudeTest {

    private static class AlwaysMatcher extends BaseMatcher {
        public boolean matches(Object item) { return true; }
        public void describeTo(Description description) { }
    }

    private static class NeverMatcher extends BaseMatcher {
        public boolean matches(Object item) { return false; }
        public void describeTo(Description description) { }
    }

    private static class CapturingMatcher extends BaseMatcher implements CapturesArguments {
        final List<Object> capturedValues = new ArrayList<Object>();
        public boolean matches(Object item) { return true; }
        public void describeTo(Description description) { }
        public void captureFrom(Object argument) { capturedValues.add(argument); }
    }

    static class SampleTarget {
        public void simpleMethod(String s) { }
        public void noArgMethod() { }
        public void twoArgMethod(String a, String b) { }
        public void varargsMethod(String... args) { }
        public void overloaded(String s) { }
        public void overloaded(int i) { }
        public void overloaded(String a, String b) { }
    }

    private Invocation createInvocation(final Object mock, final Method method, final Object[] arguments,
            final Object[] rawArguments, final boolean verified) {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method calledMethod, Object[] callArgs) throws Throwable {
                String name = calledMethod.getName();
                if ("getMock".equals(name)) return mock;
                if ("getMethod".equals(name)) return method;
                if ("getArguments".equals(name)) return arguments;
                if ("getRawArguments".equals(name)) return rawArguments;
                if ("getArgumentAt".equals(name)) {
                    int idx = ((Integer) callArgs[0]).intValue();
                    return arguments[idx];
                }
                if ("isVerified".equals(name)) return Boolean.valueOf(verified);
                if ("getLocation".equals(name)) return null;
                if ("equals".equals(name)) return Boolean.valueOf(proxy == callArgs[0]);
                if ("hashCode".equals(name)) return Integer.valueOf(System.identityHashCode(proxy));
                if ("toString".equals(name)) return "InvocationProxy";
                return null;
            }
        };
        return (Invocation) Proxy.newProxyInstance(Invocation.class.getClassLoader(),
                new Class[] { Invocation.class }, handler);
    }

    private static Method m(String name, Class[] params) throws Throwable {
        return SampleTarget.class.getMethod(name, params);
    }

    // Constructor: matchers non-empty -> stored as-is (same reference)
    @Test
    public void testConstructorTwoArgs_nonEmptyMatchers_storesProvidedMatchersReference() throws Throwable {
        Method method = m("noArgMethod", new Class[0]);
        Invocation inv = createInvocation(new Object(), method, new Object[0], new Object[0], false);
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(new AlwaysMatcher());
        matchers.add(new NeverMatcher());
        InvocationMatcher im = new InvocationMatcher(inv, matchers);
        assertSame(matchers, im.getMatchers());
    }

    // Constructor: matchers empty + no arguments -> produced matcher list is empty
    @Test
    public void testConstructorTwoArgs_emptyMatchersEmptyArguments_producesEmptyMatcherList() throws Throwable {
        Method method = m("noArgMethod", new Class[0]);
        Invocation inv = createInvocation(new Object(), method, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(inv, new ArrayList<Matcher>());
        assertTrue(im.getMatchers().isEmpty());
    }

    // Constructor: matchers empty + one argument -> one matcher produced per argument
    @Test
    public void testConstructorTwoArgs_emptyMatchersNonEmptyArguments_producesOneMatcherPerArgument() throws Throwable {
        Method method = m("simpleMethod", new Class[] { String.class });
        Invocation inv = createInvocation(new Object(), method, new Object[] { "hello" }, new Object[] { "hello" }, false);
        InvocationMatcher im = new InvocationMatcher(inv, new ArrayList<Matcher>());
        assertEquals(1, im.getMatchers().size());
    }

    // Single-arg constructor delegates to empty matcher list -> empty args produce empty matchers
    @Test
    public void testConstructorOneArg_noArguments_producesEmptyMatcherList() throws Throwable {
        Method method = m("noArgMethod", new Class[0]);
        Invocation inv = createInvocation(new Object(), method, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(inv);
        assertTrue(im.getMatchers().isEmpty());
    }

    // getMethod() delegates to underlying invocation's method
    @Test
    public void testGetMethod_returnsUnderlyingInvocationMethod() throws Throwable {
        Method method = m("simpleMethod", new Class[] { String.class });
        Invocation inv = createInvocation(new Object(), method, new Object[] { "a" }, new Object[] { "a" }, false);
        InvocationMatcher im = new InvocationMatcher(inv);
        assertSame(method, im.getMethod());
    }

    // getInvocation() returns exactly the invocation reference passed to the constructor
    @Test
    public void testGetInvocation_returnsSameInvocationReference() throws Throwable {
        Method method = m("noArgMethod", new Class[0]);
        Invocation inv = createInvocation(new Object(), method, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(inv);
        assertSame(inv, im.getInvocation());
    }

    // matches(): different mock short-circuits to false (first && operand)
    @Test
    public void testMatches_differentMock_returnsFalse() throws Throwable {
        Method method = m("noArgMethod", new Class[0]);
        Object mockA = new Object();
        Object mockB = new Object();
        Invocation wanted = createInvocation(mockA, method, new Object[0], new Object[0], false);
        Invocation actual = createInvocation(mockB, method, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.matches(actual));
    }

    // matches(): same mock but different method name short-circuits to false
    @Test
    public void testMatches_sameMockDifferentMethodName_returnsFalse() throws Throwable {
        Object mockA = new Object();
        Method methodA = m("noArgMethod", new Class[0]);
        Method methodB = m("simpleMethod", new Class[] { String.class });
        Invocation wanted = createInvocation(mockA, methodA, new Object[0], new Object[0], false);
        Invocation actual = createInvocation(mockA, methodB, new Object[] { "x" }, new Object[] { "x" }, false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.matches(actual));
    }

    // hasSimilarMethod: different method name -> false
    @Test
    public void testHasSimilarMethod_differentMethodName_returnsFalse() throws Throwable {
        Object mockA = new Object();
        Method methodA = m("simpleMethod", new Class[] { String.class });
        Method methodB = m("noArgMethod", new Class[0]);
        Invocation wanted = createInvocation(mockA, methodA, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(mockA, methodB, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.hasSimilarMethod(candidate));
    }

    // hasSimilarMethod: candidate already verified -> false
    @Test
    public void testHasSimilarMethod_candidateAlreadyVerified_returnsFalse() throws Throwable {
        Object mockA = new Object();
        Method method = m("simpleMethod", new Class[] { String.class });
        Invocation wanted = createInvocation(mockA, method, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(mockA, method, new Object[] { "a" }, new Object[] { "a" }, true);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.hasSimilarMethod(candidate));
    }

    // hasSimilarMethod: different mock instance -> false
    @Test
    public void testHasSimilarMethod_differentMock_returnsFalse() throws Throwable {
        Object mockA = new Object();
        Object mockB = new Object();
        Method method = m("simpleMethod", new Class[] { String.class });
        Invocation wanted = createInvocation(mockA, method, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(mockB, method, new Object[] { "a" }, new Object[] { "a" }, false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.hasSimilarMethod(candidate));
    }

    // hasSimilarMethod: same signature, same mock, unverified -> true (methodEquals short-circuits args check)
    @Test
    public void testHasSimilarMethod_sameMethodSignature_returnsTrue() throws Throwable {
        Object mockA = new Object();
        Method method = m("simpleMethod", new Class[] { String.class });
        Invocation wanted = createInvocation(mockA, method, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(mockA, method, new Object[] { "b" }, new Object[] { "b" }, false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertTrue(im.hasSimilarMethod(candidate));
    }

    // hasSimilarMethod: overloaded method with matching arguments -> false (cannot be overloaded per contract)
    @Test
    public void testHasSimilarMethod_overloadedWithMatchingArguments_returnsFalse() throws Throwable {
        Object mockA = new Object();
        Method wantedMethod = m("overloaded", new Class[] { String.class });
        Method candidateMethod = m("overloaded", new Class[] { int.class });
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(new AlwaysMatcher());
        Invocation wanted = createInvocation(mockA, wantedMethod, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(mockA, candidateMethod, new Object[] { Integer.valueOf(5) }, new Object[] { Integer.valueOf(5) }, false);
        InvocationMatcher im = new InvocationMatcher(wanted, matchers);
        assertFalse(im.hasSimilarMethod(candidate));
    }

    // hasSimilarMethod: overloaded method with non-matching arguments -> true
    @Test
    public void testHasSimilarMethod_overloadedWithNonMatchingArguments_returnsTrue() throws Throwable {
        Object mockA = new Object();
        Method wantedMethod = m("overloaded", new Class[] { String.class });
        Method candidateMethod = m("overloaded", new Class[] { int.class });
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(new NeverMatcher());
        Invocation wanted = createInvocation(mockA, wantedMethod, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(mockA, candidateMethod, new Object[] { Integer.valueOf(5) }, new Object[] { Integer.valueOf(5) }, false);
        InvocationMatcher im = new InvocationMatcher(wanted, matchers);
        assertTrue(im.hasSimilarMethod(candidate));
    }

    // hasSameMethod: same name, same parameter types -> true
    @Test
    public void testHasSameMethod_sameNameSameParams_returnsTrue() throws Throwable {
        Method method = m("simpleMethod", new Class[] { String.class });
        Invocation wanted = createInvocation(new Object(), method, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(new Object(), method, new Object[] { "b" }, new Object[] { "b" }, false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertTrue(im.hasSameMethod(candidate));
    }

    // hasSameMethod: different method name -> false
    @Test
    public void testHasSameMethod_differentName_returnsFalse() throws Throwable {
        Method methodA = m("simpleMethod", new Class[] { String.class });
        Method methodB = m("noArgMethod", new Class[0]);
        Invocation wanted = createInvocation(new Object(), methodA, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(new Object(), methodB, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.hasSameMethod(candidate));
    }

    // hasSameMethod: same name, different parameter count -> false
    @Test
    public void testHasSameMethod_differentParamCount_returnsFalse() throws Throwable {
        Method methodA = m("overloaded", new Class[] { String.class });
        Method methodB = m("overloaded", new Class[] { String.class, String.class });
        Invocation wanted = createInvocation(new Object(), methodA, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(new Object(), methodB, new Object[] { "a", "b" }, new Object[] { "a", "b" }, false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.hasSameMethod(candidate));
    }

    // hasSameMethod: same name, same count, different parameter type -> false
    @Test
    public void testHasSameMethod_differentParamType_returnsFalse() throws Throwable {
        Method methodA = m("overloaded", new Class[] { String.class });
        Method methodB = m("overloaded", new Class[] { int.class });
        Invocation wanted = createInvocation(new Object(), methodA, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation candidate = createInvocation(new Object(), methodB, new Object[] { Integer.valueOf(1) }, new Object[] { Integer.valueOf(1) }, false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertFalse(im.hasSameMethod(candidate));
    }

    // hasSameMethod: zero-parameter methods with same name -> true (loop executes zero iterations)
    @Test
    public void testHasSameMethod_noArgMethods_returnsTrue() throws Throwable {
        Method method = m("noArgMethod", new Class[0]);
        Invocation wanted = createInvocation(new Object(), method, new Object[0], new Object[0], false);
        Invocation candidate = createInvocation(new Object(), method, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(wanted);
        assertTrue(im.hasSameMethod(candidate));
    }

    // getLocation() delegates to the underlying invocation's location
    @Test
    public void testGetLocation_delegatesToInvocation() throws Throwable {
        Method method = m("noArgMethod", new Class[0]);
        Invocation inv = createInvocation(new Object(), method, new Object[0], new Object[0], false);
        InvocationMatcher im = new InvocationMatcher(inv);
        assertNull(im.getLocation());
    }

    // captureArgumentsFrom: non-varargs, single capturing matcher gets argument at its position
    @Test
    public void testCaptureArgumentsFrom_singleCapturingMatcher_capturesArgumentAtPosition() throws Throwable {
        Method method = m("simpleMethod", new Class[] { String.class });
        CapturingMatcher capturer = new CapturingMatcher();
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(capturer);
        Invocation wanted = createInvocation(new Object(), method, new Object[] { "ignored" }, new Object[] { "ignored" }, false);
        InvocationMatcher im = new InvocationMatcher(wanted, matchers);
        Invocation actual = createInvocation(new Object(), method, new Object[] { "onlyArg" }, new Object[] { "onlyArg" }, false);
        im.captureArgumentsFrom(actual);
        assertEquals(1, capturer.capturedValues.size());
        assertEquals("onlyArg", capturer.capturedValues.get(0));
    }

    // captureArgumentsFrom: non-varargs, non-capturing matcher at position 0 is skipped, position 1 captured
    @Test
    public void testCaptureArgumentsFrom_nonCapturingMatcherSkipped_onlyCapturingMatcherRecords() throws Throwable {
        Method method = m("twoArgMethod", new Class[] { String.class, String.class });
        CapturingMatcher capturer = new CapturingMatcher();
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(new AlwaysMatcher());
        matchers.add(capturer);
        Invocation wanted = createInvocation(new Object(), method, new Object[] { "w1", "w2" }, new Object[] { "w1", "w2" }, false);
        InvocationMatcher im = new InvocationMatcher(wanted, matchers);
        Invocation actual = createInvocation(new Object(), method, new Object[] { "foo", "bar" }, new Object[] { "foo", "bar" }, false);
        im.captureArgumentsFrom(actual);
        assertEquals(1, capturer.capturedValues.size());
        assertEquals("bar", capturer.capturedValues.get(0));
    }

    // captureArgumentsFrom: varargs invocation with a capturing matcher must capture, not throw
    @Test
    public void testCaptureArgumentsFrom_varargsMethod_shouldCaptureWithoutThrowing() throws Throwable {
        Method method = m("varargsMethod", new Class[] { String[].class });
        CapturingMatcher capturer = new CapturingMatcher();
        List<Matcher> matchers = new ArrayList<Matcher>();
        matchers.add(capturer);
        Invocation wanted = createInvocation(new Object(), method, new Object[] { new String[] { "w" } }, new Object[] { new String[] { "w" } }, false);
        InvocationMatcher im = new InvocationMatcher(wanted, matchers);
        Invocation actual = createInvocation(new Object(), method, new Object[] { new String[] { "p", "q" } }, new Object[] { new String[] { "p", "q" } }, false);
        im.captureArgumentsFrom(actual);
        assertFalse(capturer.capturedValues.isEmpty());
    }

    // createFrom: empty invocation list -> empty result list
    @Test
    public void testCreateFrom_emptyList_returnsEmptyList() throws Throwable {
        List<Invocation> invocations = new ArrayList<Invocation>();
        List<InvocationMatcher> result = InvocationMatcher.createFrom(invocations);
        assertTrue(result.isEmpty());
    }

    // createFrom: multiple invocations -> wrapped in same order, preserving references
    @Test
    public void testCreateFrom_multipleInvocations_preservesOrderAndWrapsEach() throws Throwable {
        Method methodA = m("simpleMethod", new Class[] { String.class });
        Method methodB = m("noArgMethod", new Class[0]);
        Invocation inv1 = createInvocation(new Object(), methodA, new Object[] { "a" }, new Object[] { "a" }, false);
        Invocation inv2 = createInvocation(new Object(), methodB, new Object[0], new Object[0], false);
        List<Invocation> invocations = new ArrayList<Invocation>();
        invocations.add(inv1);
        invocations.add(inv2);
        List<InvocationMatcher> result = InvocationMatcher.createFrom(invocations);
        assertEquals(2, result.size());
        assertSame(inv1, result.get(0).getInvocation());
        assertSame(inv2, result.get(1).getInvocation());
    }
}
