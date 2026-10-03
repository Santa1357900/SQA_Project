package com.fasterxml.jackson.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonPointerClaudeTest {

    // compile(null) -> returns EMPTY marker instance
    @Test
    public void testCompile_nullInput_returnsEmptyPointer() throws Throwable {
        JsonPointer p = JsonPointer.compile(null);
        assertTrue(p.matches());
        assertEquals("", p.toString());
    }

    // compile("") -> returns EMPTY marker instance (length==0 branch)
    @Test
    public void testCompile_emptyString_returnsEmptyPointer() throws Throwable {
        JsonPointer p = JsonPointer.compile("");
        assertTrue(p.matches());
        assertEquals("", p.toString());
    }

    // compile with no leading slash -> IllegalArgumentException
    @Test
    public void testCompile_noLeadingSlash_throwsIllegalArgumentException() throws Throwable {
        try {
            JsonPointer.compile("abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("JSON Pointer"));
        }
    }

    // compile with leading space (not slash) -> throws
    @Test
    public void testCompile_leadingSpace_throwsIllegalArgumentException() throws Throwable {
        try {
            JsonPointer.compile(" /a");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // compile single segment -> not matches, matching property set
    @Test
    public void testCompile_singleSegment_matchesProperty() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a");
        assertFalse(p.matches());
        assertEquals("a", p.getMatchingProperty());
    }

    // valueOf delegates to compile for null input
    @Test
    public void testValueOf_nullInput_returnsEmptyPointer() throws Throwable {
        JsonPointer p = JsonPointer.valueOf(null);
        assertTrue(p.matches());
    }

    // valueOf delegates to compile for normal input
    @Test
    public void testValueOf_normalInput_equalsCompile() throws Throwable {
        JsonPointer a = JsonPointer.valueOf("/x");
        JsonPointer b = JsonPointer.compile("/x");
        assertEquals(a, b);
    }

    // matches() true for empty pointer (nextSegment == null)
    @Test
    public void testMatches_emptyPointer_true() throws Throwable {
        assertTrue(JsonPointer.compile("").matches());
    }

    // matches() false for non-empty pointer
    @Test
    public void testMatches_nonEmptyPointer_false() throws Throwable {
        assertFalse(JsonPointer.compile("/a").matches());
    }

    // getMatchingProperty() empty string for EMPTY pointer
    @Test
    public void testGetMatchingProperty_emptyPointer_returnsEmptyString() throws Throwable {
        assertEquals("", JsonPointer.compile("").getMatchingProperty());
    }

    // getMatchingIndex() -1 for EMPTY pointer
    @Test
    public void testGetMatchingIndex_emptyPointer_returnsMinusOne() throws Throwable {
        assertEquals(-1, JsonPointer.compile("").getMatchingIndex());
    }

    // numeric segment -> getMatchingIndex returns parsed value
    @Test
    public void testGetMatchingIndex_numericSegment_returnsIndex() throws Throwable {
        assertEquals(3, JsonPointer.compile("/3").getMatchingIndex());
    }

    // non-numeric segment -> getMatchingIndex returns -1
    @Test
    public void testGetMatchingIndex_nonNumericSegment_returnsMinusOne() throws Throwable {
        assertEquals(-1, JsonPointer.compile("/abc").getMatchingIndex());
    }

    // zero segment -> valid index 0
    @Test
    public void testGetMatchingIndex_zero_returnsZero() throws Throwable {
        assertEquals(0, JsonPointer.compile("/0").getMatchingIndex());
    }

    // leading zero segment must NOT be treated as valid array index per RFC 6901 (bug hunt)
    @Test
    public void testGetMatchingIndex_leadingZero_returnsMinusOne() throws Throwable {
        JsonPointer p = JsonPointer.compile("/01");
        assertEquals(-1, p.getMatchingIndex());
    }

    // too-long digit string (len > 10) -> -1
    @Test
    public void testGetMatchingIndex_tooLongDigits_returnsMinusOne() throws Throwable {
        assertEquals(-1, JsonPointer.compile("/12345678901").getMatchingIndex());
    }

    // exactly 10 digits, overflowing Integer.MAX_VALUE -> -1
    @Test
    public void testGetMatchingIndex_tenDigitsOverflow_returnsMinusOne() throws Throwable {
        assertEquals(-1, JsonPointer.compile("/9999999999").getMatchingIndex());
    }

    // exactly 10 digits, equal to Integer.MAX_VALUE -> valid
    @Test
    public void testGetMatchingIndex_tenDigitsAtMax_returnsIndex() throws Throwable {
        JsonPointer p = JsonPointer.compile("/2147483647");
        assertEquals(Integer.MAX_VALUE, p.getMatchingIndex());
    }

    // mayMatchProperty true when property name segment present
    @Test
    public void testMayMatchProperty_nonEmptySegment_true() throws Throwable {
        assertTrue(JsonPointer.compile("/a").mayMatchProperty());
    }

    // mayMatchElement true for numeric segment
    @Test
    public void testMayMatchElement_numericSegment_true() throws Throwable {
        assertTrue(JsonPointer.compile("/3").mayMatchElement());
    }

    // mayMatchElement false for non-numeric segment
    @Test
    public void testMayMatchElement_nonNumericSegment_false() throws Throwable {
        assertFalse(JsonPointer.compile("/abc").mayMatchElement());
    }

    // matchProperty with matching name returns tail pointer
    @Test
    public void testMatchProperty_matchingName_returnsTail() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a/b");
        JsonPointer tail = p.matchProperty("a");
        assertNotNull(tail);
        assertEquals("/b", tail.toString());
    }

    // matchProperty with non-matching name returns null
    @Test
    public void testMatchProperty_nonMatchingName_returnsNull() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a");
        assertNull(p.matchProperty("x"));
    }

    // matchProperty on EMPTY pointer (nextSegment null) returns null
    @Test
    public void testMatchProperty_atEmptyPointer_returnsNull() throws Throwable {
        JsonPointer p = JsonPointer.compile("");
        assertNull(p.matchProperty("a"));
    }

    // matchElement with matching index returns tail pointer
    @Test
    public void testMatchElement_matchingIndex_returnsTail() throws Throwable {
        JsonPointer p = JsonPointer.compile("/0/x");
        JsonPointer tail = p.matchElement(0);
        assertNotNull(tail);
        assertEquals("/x", tail.toString());
    }

    // matchElement with negative index always returns null
    @Test
    public void testMatchElement_negativeIndex_returnsNull() throws Throwable {
        JsonPointer p = JsonPointer.compile("/0");
        assertNull(p.matchElement(-1));
    }

    // matchElement with non-matching index returns null
    @Test
    public void testMatchElement_nonMatchingIndex_returnsNull() throws Throwable {
        JsonPointer p = JsonPointer.compile("/2");
        assertNull(p.matchElement(1));
    }

    // tail() of single-segment pointer returns EMPTY-equivalent (matches true)
    @Test
    public void testTail_singleSegment_returnsEmptyPointer() throws Throwable {
        JsonPointer tail = JsonPointer.compile("/a").tail();
        assertNotNull(tail);
        assertTrue(tail.matches());
        assertEquals("", tail.toString());
    }

    // tail() of multi-segment pointer returns remaining path
    @Test
    public void testTail_multipleSegments_returnsRemainingPath() throws Throwable {
        JsonPointer tail = JsonPointer.compile("/a/b").tail();
        assertEquals("/b", tail.toString());
    }

    // tail() of EMPTY pointer returns null (_nextSegment field is null)
    @Test
    public void testTail_emptyPointer_returnsNull() throws Throwable {
        assertNull(JsonPointer.compile("").tail());
    }

    // toString() returns original input string
    @Test
    public void testToString_returnsOriginalString() throws Throwable {
        assertEquals("/a/b", JsonPointer.compile("/a/b").toString());
    }

    // hashCode consistent with equal instances
    @Test
    public void testHashCode_consistentForEqualPointers() throws Throwable {
        JsonPointer a = JsonPointer.compile("/a/b");
        JsonPointer b = JsonPointer.compile("/a/b");
        assertEquals(a.hashCode(), b.hashCode());
    }

    // equals: same string -> true
    @Test
    public void testEquals_sameString_true() throws Throwable {
        JsonPointer a = JsonPointer.compile("/a");
        JsonPointer b = JsonPointer.compile("/a");
        assertTrue(a.equals(b));
    }

    // equals: different string -> false
    @Test
    public void testEquals_differentString_false() throws Throwable {
        JsonPointer a = JsonPointer.compile("/a");
        JsonPointer b = JsonPointer.compile("/b");
        assertFalse(a.equals(b));
    }

    // equals: null argument -> false
    @Test
    public void testEquals_null_false() throws Throwable {
        JsonPointer a = JsonPointer.compile("/a");
        assertFalse(a.equals(null));
    }

    // equals: different type -> false
    @Test
    public void testEquals_differentType_false() throws Throwable {
        JsonPointer a = JsonPointer.compile("/a");
        assertFalse(a.equals("/a"));
    }

    // equals: same instance -> true (reference equality shortcut)
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        JsonPointer a = JsonPointer.compile("/a");
        assertTrue(a.equals(a));
    }

    // escape ~0 decodes to literal '~'
    @Test
    public void testCompile_escapeTilde_decodesToTildeChar() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a~0b");
        assertEquals("a~b", p.getMatchingProperty());
    }

    // escape ~1 decodes to literal '/'
    @Test
    public void testCompile_escapeSlash_decodesToSlashChar() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a~1b");
        assertEquals("a/b", p.getMatchingProperty());
    }

    // unrecognized escape sequence (~ followed by non 0/1) is kept literally
    @Test
    public void testCompile_unrecognizedEscape_keptLiterally() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a~2b");
        assertEquals("a~2b", p.getMatchingProperty());
    }

    // escaped segment followed by another segment continues parsing correctly
    @Test
    public void testCompile_escapedSegmentWithTail_parsesRemainder() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a~0b/c");
        assertEquals("a~b", p.getMatchingProperty());
        assertEquals("c", p.tail().getMatchingProperty());
    }

    // multi-segment pointer with property then array index chain
    @Test
    public void testCompile_multiSegmentChain_matchesInOrder() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a/0/b");
        assertEquals("a", p.getMatchingProperty());
        JsonPointer t1 = p.tail();
        assertEquals(0, t1.getMatchingIndex());
        JsonPointer t2 = t1.tail();
        assertEquals("b", t2.getMatchingProperty());
        assertTrue(t2.tail().matches());
    }

    // trailing slash produces an empty-string final segment
    @Test
    public void testCompile_trailingSlash_producesEmptySegment() throws Throwable {
        JsonPointer p = JsonPointer.compile("/a/");
        assertEquals("a", p.getMatchingProperty());
        assertEquals("", p.tail().getMatchingProperty());
    }
}
