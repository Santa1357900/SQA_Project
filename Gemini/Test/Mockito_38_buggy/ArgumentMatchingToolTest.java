package org.mockito.internal.verification.argumentmatching;

import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.Test;
import org.mockito.internal.matchers.ContainsExtraTypeInformation;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ArgumentMatchingToolTest {

    private static final class DummyMatcher implements Matcher<Object>, ContainsExtraTypeInformation {
        private final boolean matchesResult;
        private final boolean typeMatchesResult;
        private final String toStringValue;

        public DummyMatcher(boolean matchesResult, boolean typeMatchesResult, String toStringValue) {
            this.matchesResult = matchesResult;
            this.typeMatchesResult = typeMatchesResult;
            this.toStringValue = toStringValue;
        }

        public boolean matches(Object item) {
            return matchesResult;
        }

        public void describeTo(org.hamcrest.Description description) {
            description.appendText(toStringValue);
        }

        public boolean typeMatches(Object target) {
            return typeMatchesResult;
        }

        public Matcher<Object> getInformationProvidingMatcher() {
            return this;
        }

        public Object internal_should_use_the_equals_method(Object parameter) {
            return parameter;
        }
    }

    private static final class ThrowingMatcher implements Matcher<Object>, ContainsExtraTypeInformation {
        public boolean matches(Object item) {
            throw new RuntimeException("Simulated error");
        }

        public void describeTo(org.hamcrest.Description description) {
            description.appendText("throwing");
        }

        public boolean typeMatches(Object target) {
            return false;
        }

        public Matcher<Object> getInformationProvidingMatcher() {
            return this;
        }

        public Object internal_should_use_the_equals_method(Object parameter) {
            return parameter;
        }
    }

    private static final class NonExtraTypeMatcher implements Matcher<Object> {
        public boolean matches(Object item) {
            return false;
        }

        public void describeTo(org.hamcrest.Description description) {
            description.appendText("non-extra");
        }
    }

    @Test
    public void testSizeMismatchReturnsEmptyArray() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        Object[] args = new Object[] { "arg1", "arg2" };

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testEmptyMatchersAndArguments() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        Object[] args = new Object[0];

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testSuspiciousArgumentDetected() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        
        // matches = false, toStringEquals = true (both describe as "123"), typeMatches = false
        matchers.add(new DummyMatcher(false, false, "123"));
        Object[] args = new Object[] { Integer.valueOf(123) };

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertArrayEquals(new Integer[] { Integer.valueOf(0) }, result);
    }

    @Test
    public void testNotSuspiciousWhenMatchesIsTrue() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        
        // matches = true, which makes safelyMatches true, so not suspicious
        matchers.add(new DummyMatcher(true, false, "123"));
        Object[] args = new Object[] { Integer.valueOf(123) };

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testNotSuspiciousWhenToStringDiffers() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        
        // toString representations differ ("abc" vs "123")
        matchers.add(new DummyMatcher(false, false, "abc"));
        Object[] args = new Object[] { Integer.valueOf(123) };

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testNotSuspiciousWhenTypeMatchesIsTrue() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        
        // typeMatches = true, so not suspicious
        matchers.add(new DummyMatcher(false, true, "123"));
        Object[] args = new Object[] { Integer.valueOf(123) };

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testMatcherNotImplementingContainsExtraTypeInformation() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        
        matchers.add(new NonExtraTypeMatcher());
        Object[] args = new Object[] { "test" };

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testSafelyMatchesCatchesException() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        
        matchers.add(new ThrowingMatcher());
        Object[] args = new Object[] { "test" };

        // Should catch exception in safelyMatches and treat matches as false,
        // but since toString is "throwing" and arg is "test", toStringEquals will be false anyway.
        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testMultipleMatchersMixedConditions() throws Throwable {
        ArgumentMatchingTool tool = new ArgumentMatchingTool();
        List<Matcher> matchers = new LinkedList<Matcher>();
        
        // Index 0: Suspicious (matches=false, toString equals, typeMatches=false)
        matchers.add(new DummyMatcher(false, false, "100"));
        // Index 1: Not suspicious (typeMatches=true)
        matchers.add(new DummyMatcher(false, true, "200"));
        // Index 2: Suspicious (matches=false, toString equals, typeMatches=false)
        matchers.add(new DummyMatcher(false, false, "300"));

        Object[] args = new Object[] { Integer.valueOf(100), Integer.valueOf(200), Integer.valueOf(300) };

        Integer[] result = tool.getSuspiciouslyNotMatchingArgsIndexes(matchers, args);
        assertNotNull(result);
        assertArrayEquals(new Integer[] { Integer.valueOf(0), Integer.valueOf(2) }, result);
    }
}