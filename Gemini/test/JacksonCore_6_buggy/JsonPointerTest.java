package com.fasterxml.jackson.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonPointerTest {

    @Test
    public void testEmptyAndNullCompile() throws Throwable {
        JsonPointer p1 = JsonPointer.compile(null);
        assertNotNull(p1);
        assertTrue(p1.matches());
        assertEquals("", p1.toString());
        assertEquals("", p1.getMatchingProperty());
        assertEquals(-1, p1.getMatchingIndex());
        assertFalse(p1.mayMatchProperty());
        assertFalse(p1.mayMatchElement());
        assertNull(p1.tail());

        JsonPointer p2 = JsonPointer.compile("");
        assertSame(p1, p2);

        JsonPointer p3 = JsonPointer.valueOf(null);
        assertSame(p1, p3);

        JsonPointer p4 = JsonPointer.valueOf("");
        assertSame(p1, p4);
    }

    @Test
    public void testInvalidCompile() throws Throwable {
        try {
            JsonPointer.compile("invalid");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must start with '/'"));
        }
    }

    @Test
    public void testSimpleCompile() throws Throwable {
        JsonPointer ptr = JsonPointer.compile("/foo");
        assertNotNull(ptr);
        assertFalse(ptr.matches());
        assertEquals("/foo", ptr.toString());
        assertEquals("foo", ptr.getMatchingProperty());
        assertEquals(-1, ptr.getMatchingIndex());
        assertTrue(ptr.mayMatchProperty());
        assertFalse(ptr.mayMatchElement());

        JsonPointer tail = ptr.tail();
        assertNotNull(tail);
        assertTrue(tail.matches());
        assertEquals("", tail.toString());
    }

    @Test
    public void testMultipleSegmentsAndIndices() throws Throwable {
        JsonPointer ptr = JsonPointer.compile("/foo/123/bar");
        assertNotNull(ptr);
        assertEquals("foo", ptr.getMatchingProperty());
        assertEquals(-1, ptr.getMatchingIndex());
        assertTrue(ptr.mayMatchProperty());
        assertFalse(ptr.mayMatchElement());

        JsonPointer next1 = ptr.tail();
        assertNotNull(next1);
        assertEquals("123", next1.getMatchingProperty());
        assertEquals(123, next1.getMatchingIndex());
        assertTrue(next1.mayMatchProperty());
        assertTrue(next1.mayMatchElement());

        JsonPointer next2 = next1.tail();
        assertNotNull(next2);
        assertEquals("bar", next2.getMatchingProperty());
        assertEquals(-1, next2.getMatchingIndex());
        assertTrue(next2.mayMatchProperty());
        assertFalse(next2.mayMatchElement());

        assertTrue(next2.tail().matches());
    }

    @Test
    public void testIndexParsingEdgeCases() throws Throwable {
        // Leading zero
        JsonPointer ptr1 = JsonPointer.compile("/01");
        assertEquals("01", ptr1.getMatchingProperty());
        assertEquals(-1, ptr1.getMatchingIndex());
        assertFalse(ptr1.mayMatchElement());

        // Zero index
        JsonPointer ptr2 = JsonPointer.compile("/0");
        assertEquals("0", ptr2.getMatchingProperty());
        assertEquals(0, ptr2.getMatchingIndex());
        assertTrue(ptr2.mayMatchElement());

        // Negative-looking or non-numeric
        JsonPointer ptr3 = JsonPointer.compile("/123a");
        assertEquals("123a", ptr3.getMatchingProperty());
        assertEquals(-1, ptr3.getMatchingIndex());

        // Too long index (> 10 chars or overflow)
        JsonPointer ptr4 = JsonPointer.compile("/12345678901");
        assertEquals(-1, ptr4.getMatchingIndex());

        // Large valid int and overflow
        JsonPointer ptr5 = JsonPointer.compile("/2147483647"); // Integer.MAX_VALUE
        assertEquals(2147483647, ptr5.getMatchingIndex());

        JsonPointer ptr6 = JsonPointer.compile("/2147483648"); // Integer.MAX_VALUE + 1
        assertEquals(-1, ptr6.getMatchingIndex());
    }

    @Test
    public void testEscapedPointers() throws Throwable {
        // ~0 -> ~, ~1 -> /
        JsonPointer ptr = JsonPointer.compile("/a~0b/c~1d");
        assertEquals("a~b", ptr.getMatchingProperty());
        
        JsonPointer next = ptr.tail();
        assertEquals("c/d", next.getMatchingProperty());

        // Edge cases with escape
        JsonPointer ptr2 = JsonPointer.compile("/~0/~1");
        assertEquals("~", ptr2.getMatchingProperty());
        assertEquals("/", ptr2.tail().getMatchingProperty());

        JsonPointer ptr3 = JsonPointer.compile("/a~b"); // non-standard escape ending or single tilde
        assertEquals("a~b", ptr3.getMatchingProperty());
    }

    @Test
    public void testMatchingAndMatchMethods() throws Throwable {
        JsonPointer ptr = JsonPointer.compile("/foo/0");

        // matchProperty
        assertNull(ptr.matchProperty("bar"));
        JsonPointer matchedProp = ptr.matchProperty("foo");
        assertNotNull(matchedProp);
        assertEquals("/0", matchedProp.toString());

        // matchElement
        assertNull(matchedProp.matchElement(-1));
        assertNull(matchedProp.matchElement(1));
        JsonPointer matchedElem = matchedProp.matchElement(0);
        assertNotNull(matchedElem);
        assertTrue(matchedElem.matches());

        // Matches on empty
        assertTrue(JsonPointer.compile("").matches());
    }

    @Test
    public void testEqualsAndHashCodeAndToString() throws Throwable {
        JsonPointer p1 = JsonPointer.compile("/foo/bar");
        JsonPointer p2 = JsonPointer.compile("/foo/bar");
        JsonPointer p3 = JsonPointer.compile("/foo/baz");
        JsonPointer empty = JsonPointer.compile("");

        assertTrue(p1.equals(p1));
        assertTrue(p1.equals(p2));
        assertFalse(p1.equals(p3));
        assertFalse(p1.equals(null));
        assertFalse(p1.equals("StringObj"));

        assertEquals(p1.hashCode(), p2.hashCode());
        assertEquals("/foo/bar", p1.toString());
        assertEquals("", empty.toString());
    }
}