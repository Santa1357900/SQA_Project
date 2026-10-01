package org.mockito.internal.invocation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.matchers.CapturesArguments;
import org.mockito.invocation.Invocation;

import static org.junit.Assert.*;

public class InvocationMatcherClaudeTest {

    public interface SampleMock {
        void simpleMethod();
        void simpleMethod(String s);
        void argMethod(String a, String b);
        void overloaded(Object o);
        void overloaded(String s);
        void varargsMethod(String fixed, Object... items);
    }

    private static class AlwaysTrueMatcher extends BaseMatcher {
        public boolean matches(Object item) { return true; }
        public void describeTo(Description description) { description.appendText("always"); }
    }

    private static class CaptureMatcher extends BaseMatcher implements CapturesArguments {
        Object captured;
        public boolean matches(Object item) { return true; }
        public void describeTo(Description description) { description.appendText("capture"); }
        public void captureFrom(Object argument) { this.captured = argument; }
    }

    private SampleMock mock;

    @Before
    public void setUp() throws Throwable {
        mock = Mockito.mock(SampleMock.class);
    }

    private List<Invocation> invocationsOf(Object m) {
        return new ArrayList<Invocation>(Mockito.mockingDetails(m).getInvocations());
    }

    // constructor(single-arg): null invocation reaches ArgumentsProcessor branch -> NPE
    @Test
    public void testConstructorSingleArg_nullInvocation_throwsNPE() throws Throwable {
        try {
            new InvocationMatcher(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // constructor(two-arg): null matchers list -> matchers.isEmpty() throws NPE
    @Test
    public void testConstructorTwoArg_nullMatchersList_throwsNPE() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        try {
            new InvocationMatcher(inv, null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // getMatchers: empty list branch derives one matcher per argument (two args)
    @Test
    public void testGetMatchers_emptyMatchersListProvided_derivesFromArguments() throws Throwable {
        mock.argMethod("a", "b");
        Invocation inv = invocationsOf(mock).get(0);
        InvocationMatcher im = new InvocationMatcher(inv, new ArrayList<Matcher>());
        assertEquals(2, im.getMatchers().size());
    }

    // getMatchers: non-empty list branch uses the exact given list instance
    @Test
    public void testGetMatchers_nonEmptyMatchersListProvided_usesGivenListDirectly() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        List<Matcher> given = new ArrayList<Matcher>();
        given.add(new AlwaysTrueMatcher());
        InvocationMatcher im = new InvocationMatcher(inv, given);
        assertSame(given, im.getMatchers());
    }

    // default (single-arg) constructor: derives matchers from invocation arguments count
    @Test
    public void testGetMatchers_defaultConstructor_derivesOneMatcherPerArgument() throws Throwable {
        mock.simpleMethod("x");
        Invocation inv = invocationsOf(mock).get(0);
        InvocationMatcher im = new InvocationMatcher(inv);
        assertEquals(1, im.getMatchers().size());
    }

    // getMethod: delegates to underlying invocation's method
    @Test
    public void testGetMethod_returnsUnderlyingInvocationMethod() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        InvocationMatcher im = new InvocationMatcher(inv);
        Method expected = SampleMock.class.getMethod("simpleMethod");
        assertEquals(expected.getName(), im.getMethod().getName());
        assertEquals(0, im.getMethod().getParameterTypes().length);
    }

    // getInvocation: returns the exact same invocation reference passed in
    @Test
    public void testGetInvocation_returnsSameInvocationInstance() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        InvocationMatcher im = new InvocationMatcher(inv);
        assertSame(inv, im.getInvocation());
    }

    // toString: printed description should mention the invoked method name
    @Test
    public void testToString_containsMethodName() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        InvocationMatcher im = new InvocationMatcher(inv);
        String s = im.toString();
        assertNotNull(s);
        assertTrue(s.contains("simpleMethod"));
    }

    // matches: same mock, same method, same args -> true
    @Test
    public void testMatches_sameMockSameMethodSameArgs_true() throws Throwable {
        mock.argMethod("a", "b");
        mock.argMethod("a", "b");
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertTrue(im.matches(invs.get(1)));
    }

    // matches: different mock -> false even if method/args equal
    @Test
    public void testMatches_differentMock_false() throws Throwable {
        mock.simpleMethod();
        SampleMock mock2 = Mockito.mock(SampleMock.class);
        mock2.simpleMethod();
        InvocationMatcher im = new InvocationMatcher(invocationsOf(mock).get(0));
        assertFalse(im.matches(invocationsOf(mock2).get(0)));
    }

    // matches: same mock but different method -> false
    @Test
    public void testMatches_differentMethod_false() throws Throwable {
        mock.simpleMethod();
        mock.argMethod("a", "b");
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertFalse(im.matches(invs.get(1)));
    }

    // matches: same mock/method but different arguments -> false
    @Test
    public void testMatches_sameMethodDifferentArgs_false() throws Throwable {
        mock.argMethod("a", "b");
        mock.argMethod("x", "y");
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertFalse(im.matches(invs.get(1)));
    }

    // hasSimilarMethod: same method, unverified, same mock -> true
    @Test
    public void testHasSimilarMethod_sameMethodUnverifiedSameMock_true() throws Throwable {
        mock.simpleMethod();
        mock.simpleMethod();
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertTrue(im.hasSimilarMethod(invs.get(1)));
    }

    // hasSimilarMethod: different method name -> false
    @Test
    public void testHasSimilarMethod_differentMethodName_false() throws Throwable {
        mock.simpleMethod();
        mock.argMethod("a", "b");
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertFalse(im.hasSimilarMethod(invs.get(1)));
    }

    // hasSimilarMethod: different mock -> false
    @Test
    public void testHasSimilarMethod_differentMock_false() throws Throwable {
        mock.simpleMethod();
        SampleMock mock2 = Mockito.mock(SampleMock.class);
        mock2.simpleMethod();
        InvocationMatcher im = new InvocationMatcher(invocationsOf(mock).get(0));
        assertFalse(im.hasSimilarMethod(invocationsOf(mock2).get(0)));
    }

    // hasSimilarMethod: candidate already verified -> false
    @Test
    public void testHasSimilarMethod_candidateVerified_false() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        Mockito.verify(mock).simpleMethod();
        InvocationMatcher im = new InvocationMatcher(inv);
        assertFalse(im.hasSimilarMethod(inv));
    }

    // hasSimilarMethod: overloaded method but identical argument values -> false per javadoc
    @Test
    public void testHasSimilarMethod_overloadedMethodSameArguments_false() throws Throwable {
        mock.overloaded((Object) "same");
        mock.overloaded("same");
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertFalse(im.hasSimilarMethod(invs.get(1)));
    }

    // hasSameMethod: same name and same parameter types -> true
    @Test
    public void testHasSameMethod_sameNameSameParamTypes_true() throws Throwable {
        mock.simpleMethod();
        mock.simpleMethod();
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertTrue(im.hasSameMethod(invs.get(1)));
    }

    // hasSameMethod: same name but different parameter count -> false
    @Test
    public void testHasSameMethod_sameNameDifferentParamCount_false() throws Throwable {
        mock.simpleMethod();
        mock.simpleMethod("x");
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertFalse(im.hasSameMethod(invs.get(1)));
    }

    // hasSameMethod: different method name -> false
    @Test
    public void testHasSameMethod_differentName_false() throws Throwable {
        mock.simpleMethod();
        mock.argMethod("a", "b");
        List<Invocation> invs = invocationsOf(mock);
        InvocationMatcher im = new InvocationMatcher(invs.get(0));
        assertFalse(im.hasSameMethod(invs.get(1)));
    }

    // getLocation: delegates to the underlying invocation's location
    @Test
    public void testGetLocation_delegatesToInvocation() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        InvocationMatcher im = new InvocationMatcher(inv);
        assertSame(inv.getLocation(), im.getLocation());
    }

    // captureArgumentsFrom: non-varargs method, each position captured correctly
    @Test
    public void testCaptureArgumentsFrom_nonVarargsMethod_capturesEachArgumentAtItsPosition() throws Throwable {
        mock.argMethod("x", "y");
        Invocation inv = invocationsOf(mock).get(0);
        List<Matcher> matchers = new ArrayList<Matcher>();
        CaptureMatcher m0 = new CaptureMatcher();
        CaptureMatcher m1 = new CaptureMatcher();
        matchers.add(m0);
        matchers.add(m1);
        InvocationMatcher im = new InvocationMatcher(inv, matchers);
        im.captureArgumentsFrom(inv);
        assertEquals("x", m0.captured);
        assertEquals("y", m1.captured);
    }

    // captureArgumentsFrom: varargs method, fixed arg plus each vararg element captured correctly
    @Test
    public void testCaptureArgumentsFrom_varargsMethod_capturesFixedAndEachVarargElement() throws Throwable {
        mock.varargsMethod("F", "A", "B");
        Invocation inv = invocationsOf(mock).get(0);
        List<Matcher> matchers = new ArrayList<Matcher>();
        CaptureMatcher m0 = new CaptureMatcher();
        CaptureMatcher m1 = new CaptureMatcher();
        CaptureMatcher m2 = new CaptureMatcher();
        matchers.add(m0);
        matchers.add(m1);
        matchers.add(m2);
        InvocationMatcher im = new InvocationMatcher(inv, matchers);
        im.captureArgumentsFrom(inv);
        assertEquals("F", m0.captured);
        assertEquals("A", m1.captured);
        assertEquals("B", m2.captured);
    }

    // captureArgumentsFrom: matcher not implementing CapturesArguments -> skipped, list unchanged
    @Test
    public void testCaptureArgumentsFrom_matcherNotCapturesArguments_listUnchangedNoException() throws Throwable {
        mock.simpleMethod("x");
        Invocation inv = invocationsOf(mock).get(0);
        List<Matcher> matchers = new ArrayList<Matcher>();
        AlwaysTrueMatcher m = new AlwaysTrueMatcher();
        matchers.add(m);
        InvocationMatcher im = new InvocationMatcher(inv, matchers);
        im.captureArgumentsFrom(inv);
        assertSame(m, im.getMatchers().get(0));
    }

    // createFrom: empty input list -> empty output list
    @Test
    public void testCreateFrom_emptyList_returnsEmptyList() throws Throwable {
        List<Invocation> empty = new ArrayList<Invocation>();
        List<InvocationMatcher> result = InvocationMatcher.createFrom(empty);
        assertTrue(result.isEmpty());
    }

    // createFrom: single invocation -> single wrapped matcher referring to it
    @Test
    public void testCreateFrom_singleInvocation_wrapsInMatcher() throws Throwable {
        mock.simpleMethod();
        Invocation inv = invocationsOf(mock).get(0);
        List<Invocation> single = new ArrayList<Invocation>();
        single.add(inv);
        List<InvocationMatcher> result = InvocationMatcher.createFrom(single);
        assertEquals(1, result.size());
        assertSame(inv, result.get(0).getInvocation());
    }

    // createFrom: multiple invocations -> size and order preserved
    @Test
    public void testCreateFrom_multipleInvocations_preservesOrderAndSize() throws Throwable {
        mock.simpleMethod();
        mock.argMethod("a", "b");
        List<Invocation> multi = invocationsOf(mock);
        List<InvocationMatcher> result = InvocationMatcher.createFrom(multi);
        assertEquals(2, result.size());
        assertSame(multi.get(0), result.get(0).getInvocation());
        assertSame(multi.get(1), result.get(1).getInvocation());
    }
}
