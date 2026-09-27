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

        JsonPointer p2 = JsonPointer.compile("");
        assertNotNull(p2);
        assertTrue(p2.matches());
        assertEquals("", p2.toString());

        JsonPointer p3 = JsonPointer.valueOf(null);
        assertNotNull(p3);
        assertTrue(p3.matches());
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
    public void testSimpleCompileAndGetters() throws Throwable {
        JsonPointer p = JsonPointer.compile("/foo");
        assertFalse(p.matches());
        assertEquals("foo", p.getMatchingProperty());
        assertEquals(-1, p.getMatchingIndex());
        assertTrue(p.mayMatchProperty());
        assertFalse(p.mayMatchElement());
        assertEquals("/foo", p.toString());
        
        JsonPointer tail = p.tail();
        assertNotNull(tail);
        assertTrue(tail.matches());
        assertEquals("", tail.toString());
    }

    @Test
    public void testNumericIndexParsing() throws Throwable {
        JsonPointer p = JsonPointer.compile("/123");
        assertEquals("123", p.getMatchingProperty());
        assertEquals(123, p.getMatchingIndex());
        assertTrue(p.mayMatchProperty());
        assertTrue(p.mayMatchElement());

        // Test boundary index cases and parse index limits
        JsonPointer pZero = JsonPointer.compile("/0");
        assertEquals(0, pZero.getMatchingIndex());

        // Length > 10
        JsonPointer pLong = JsonPointer.compile("/12345678901");
        assertEquals(-1, pLong.getMatchingIndex());

        // Exceeds Integer.MAX_VALUE
        JsonPointer pOverflow = JsonPointer.compile("/3000000000");
        assertEquals(-1, pOverflow.getMatchingIndex());

        // Non-digit characters in index
        JsonPointer pNonDigit = JsonPointer.compile("/12a3");
        assertEquals(-1, pNonDigit.getMatchingIndex());
    }

    @Test
    public void testMultiSegmentAndMatching() throws Throwable {
        JsonPointer p = JsonPointer.compile("/foo/456/bar");
        
        assertEquals("foo", p.getMatchingProperty());
        assertFalse(p.matches());

        JsonPointer next1 = p.matchProperty("foo");
        assertNotNull(next1);
        assertEquals("456", next1.getMatchingProperty());
        assertEquals(456, next1.getMatchingIndex());

        JsonPointer nextElem = next1.matchElement(456);
        assertNotNull(nextElem);
        assertEquals("bar", nextElem.getMatchingProperty());

        // Negative match cases
        assertNull(p.matchProperty("bar"));
        assertNull(next1.matchElement(999));
        assertNull(next1.matchElement(-1));
    }

    @Test
    public void testQuotedEscapes() throws Throwable {
        // ~0 represents tilde (~), ~1 represents slash (/)
        JsonPointer p = JsonPointer.compile("/a~0b/c~1d/e~2f");
        assertNotNull(p);
        assertEquals("a~b", p.getMatchingProperty());
        
        JsonPointer tail1 = p.tail();
        assertEquals("c/d", tail1.getMatchingProperty());

        JsonPointer tail2 = tail1.tail();
        assertEquals("e~2f", tail2.getMatchingProperty());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        JsonPointer p1 = JsonPointer.compile("/foo/bar");
        JsonPointer p2 = JsonPointer.compile("/foo/bar");
        JsonPointer p3 = JsonPointer.compile("/foo/baz");

        assertTrue(p1.equals(p1));
        assertTrue(p1.equals(p2));
        assertFalse(p1.equals(null));
        assertFalse(p1.equals("StringObject"));
        assertFalse(p1.equals(p3));

        assertEquals(p1.hashCode(), p2.hashCode());
    }
}