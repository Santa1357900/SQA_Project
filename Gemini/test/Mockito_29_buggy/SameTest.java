package org.mockito.internal.matchers;

import org.hamcrest.BaseDescription;
import org.hamcrest.Description;
import org.junit.Test;

import static org.junit.Assert.*;

public class SameTest {

    @Test
    public void testMatches() throws Throwable {
        Object obj1 = new Object();
        Object obj2 = new Object();

        Same sameMatcher = new Same(obj1);

        assertTrue(sameMatcher.matches(obj1));
        assertFalse(sameMatcher.matches(obj2));
        assertFalse(sameMatcher.matches(null));
    }

    @Test
    public void testMatchesNullWanted() throws Throwable {
        Same sameMatcher = new Same(null);

        assertTrue(sameMatcher.matches(null));
        assertFalse(sameMatcher.matches(new Object()));
    }

    @Test
    public void testDescribeToObject() throws Throwable {
        Object wanted = new Integer(123);
        Same sameMatcher = new Same(wanted);

        StringBuilder sb = new StringBuilder();
        Description description = new StringDescriptionMock(sb);

        sameMatcher.describeTo(description);

        assertEquals("same(123)", sb.toString());
    }

    @Test
    public void testDescribeToString() throws Throwable {
        String wanted = "testString";
        Same sameMatcher = new Same(wanted);

        StringBuilder sb = new StringBuilder();
        Description description = new StringDescriptionMock(sb);

        sameMatcher.describeTo(description);

        assertEquals("same(\"testString\")", sb.toString());
    }

    @Test
    public void testDescribeToCharacter() throws Throwable {
        Character wanted = Character.valueOf('a');
        Same sameMatcher = new Same(wanted);

        StringBuilder sb = new StringBuilder();
        Description description = new StringDescriptionMock(sb);

        sameMatcher.describeTo(description);

        assertEquals("same('a')", sb.toString());
    }

    private static class StringDescriptionMock extends BaseDescription {
        private final StringBuilder sb;

        public StringDescriptionMock(StringBuilder sb) {
            this.sb = sb;
        }

        protected void append(String text) {
            sb.append(text);
        }

        protected void append(char c) {
            sb.append(c);
        }
    }
}