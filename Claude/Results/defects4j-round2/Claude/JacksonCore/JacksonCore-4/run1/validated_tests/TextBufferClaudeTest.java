package com.fasterxml.jackson.core.util;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.util.Arrays;

public class TextBufferClaudeTest
{
    private TextBuffer tb;

    @Before
    public void setUp() throws Throwable {
        tb = new TextBuffer(null);
        tb.resetWithEmpty();
    }

    // size(): resetWithEmpty -> 0
    @Test
    public void testSize_afterResetWithEmpty_returnsZero() throws Throwable {
        assertEquals(0, tb.size());
    }



    // size(): resultString branch
    @Test
    public void testSize_afterResetWithString_returnsStringLength() throws Throwable {
        tb.resetWithString("hello world");
        assertEquals(11, tb.size());
    }

    // size(): shared input branch
    @Test
    public void testSize_afterResetWithShared_returnsLen() throws Throwable {
        char[] buf = new char[]{'a','b','c','d'};
        tb.resetWithShared(buf, 1, 2);
        assertEquals(2, tb.size());
    }

    // size(): resetWithCopy uses append internally (segmented branch)
    @Test
    public void testSize_afterResetWithCopy_returnsLen() throws Throwable {
        char[] buf = new char[]{'x','y','z'};
        tb.resetWithCopy(buf, 0, 3);
        assertEquals(3, tb.size());
    }



    // getTextOffset(): shared branch returns start
    @Test
    public void testGetTextOffset_shared_returnsStart() throws Throwable {
        char[] buf = new char[]{'a','b','c','d','e'};
        tb.resetWithShared(buf, 3, 2);
        assertEquals(3, tb.getTextOffset());
    }

    // hasTextAsCharacters(): shared input -> true
    @Test
    public void testHasTextAsCharacters_sharedBuffer_true() throws Throwable {
        tb.resetWithShared(new char[]{'a','b'}, 0, 2);
        assertTrue(tb.hasTextAsCharacters());
    }

    // hasTextAsCharacters(): only resultString set -> false
    @Test
    public void testHasTextAsCharacters_resultStringOnly_false() throws Throwable {
        tb.resetWithString("abc");
        assertFalse(tb.hasTextAsCharacters());
    }

    // hasTextAsCharacters(): after getTextBuffer() caches resultArray -> true
    @Test
    public void testHasTextAsCharacters_afterGetTextBufferOnString_true() throws Throwable {
        tb.resetWithString("abc");
        tb.getTextBuffer();
        assertTrue(tb.hasTextAsCharacters());
    }



    // getTextBuffer(): shared branch returns exact same array reference
    @Test
    public void testGetTextBuffer_sharedBuffer_returnsSameReference() throws Throwable {
        char[] shared = new char[]{'h','i'};
        tb.resetWithShared(shared, 0, 2);
        assertSame(shared, tb.getTextBuffer());
    }





    // contentsAsString(): empty after reset
    @Test
    public void testContentsAsString_emptyAfterReset() throws Throwable {
        assertEquals("", tb.contentsAsString());
    }





    // contentsAsString(): shared zero-length branch returns ""
    @Test
    public void testContentsAsString_sharedZeroLen_returnsEmpty() throws Throwable {
        tb.resetWithShared(new char[]{'a','b'}, 0, 0);
        assertEquals("", tb.contentsAsString());
        assertEquals(0, tb.contentsAsArray().length);
    }

    // contentsAsString(): shared non-zero length branch
    @Test
    public void testContentsAsString_sharedNonZeroLen_returnsSubstring() throws Throwable {
        char[] buf = new char[]{'a','b','c','d','e'};
        tb.resetWithShared(buf, 1, 3);
        assertEquals("bcd", tb.contentsAsString());
    }

    // contentsAsArray(): shared start==0 -> Arrays.copyOf branch
    @Test
    public void testContentsAsArray_sharedStartZero_usesCopyOf() throws Throwable {
        char[] buf = new char[]{'a','b','c'};
        tb.resetWithShared(buf, 0, 3);
        char[] result = tb.contentsAsArray();
        assertEquals("abc", new String(result));
    }

    // contentsAsArray(): shared start!=0 -> Arrays.copyOfRange branch
    @Test
    public void testContentsAsArray_sharedStartNonZero_usesCopyOfRange() throws Throwable {
        char[] buf = new char[]{'a','b','c','d'};
        tb.resetWithShared(buf, 2, 2);
        char[] result = tb.contentsAsArray();
        assertEquals("cd", new String(result));
    }

    // contentsAsDecimal(): fallback branch via resultString
    @Test
    public void testContentsAsDecimal_fromResultString_parsesCorrectly() throws Throwable {
        tb.resetWithString("123.45");
        BigDecimal bd = tb.contentsAsDecimal();
        assertEquals(0, bd.compareTo(new BigDecimal("123.45")));
    }

    // contentsAsDecimal(): shared buffer branch
    @Test
    public void testContentsAsDecimal_fromSharedBuffer_parsesCorrectly() throws Throwable {
        char[] buf = new char[]{'4','2'};
        tb.resetWithShared(buf, 0, 2);
        BigDecimal bd = tb.contentsAsDecimal();
        assertEquals(0, bd.compareTo(new BigDecimal("42")));
    }

    // contentsAsDecimal(): single segment branch (_segmentSize==0)
    @Test
    public void testContentsAsDecimal_fromSingleSegment_parsesCorrectly() throws Throwable {
        char[] buf = new char[]{'7','.','2','5'};
        tb.resetWithCopy(buf, 0, 4);
        BigDecimal bd = tb.contentsAsDecimal();
        assertEquals(0, bd.compareTo(new BigDecimal("7.25")));
    }

    // contentsAsDecimal(): resultArray branch (highest priority)
    @Test
    public void testContentsAsDecimal_fromResultArray_parsesCorrectly() throws Throwable {
        tb.resetWithString("3.5");
        tb.contentsAsArray();
        BigDecimal bd = tb.contentsAsDecimal();
        assertEquals(0, bd.compareTo(new BigDecimal("3.5")));
    }

    // contentsAsDecimal(): invalid content throws NumberFormatException
    @Test
    public void testContentsAsDecimal_invalidContent_throwsNumberFormatException() throws Throwable {
        tb.resetWithString("not-a-number");
        try {
            tb.contentsAsDecimal();
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // contentsAsDouble(): delegates to NumberInput.parseDouble
    @Test
    public void testContentsAsDouble_parsesCorrectly() throws Throwable {
        tb.resetWithString("2.5");
        assertEquals(2.5, tb.contentsAsDouble(), 1e-9);
    }

    // ensureNotShared(): makes a defensive copy of shared content
    @Test
    public void testEnsureNotShared_copiesContentIndependentOfSourceMutation() throws Throwable {
        char[] shared = "hello".toCharArray();
        tb.resetWithShared(shared, 0, 5);
        tb.ensureNotShared();
        shared[0] = 'X';
        assertEquals("hello", tb.contentsAsString());
    }

    // append(char[]): unshare branch, then merge with newly appended data
    @Test
    public void testAppendCharArray_afterSharedReset_unshareAndAppendCorrect() throws Throwable {
        tb.resetWithShared(new char[]{'h','i'}, 0, 2);
        tb.append(new char[]{'!','!','!'}, 0, 3);
        assertEquals("hi!!!", tb.contentsAsString());
    }







    // getCurrentSegment(): allocates when null, expands when full
    @Test
    public void testGetCurrentSegment_allocatesWhenNull_andExpandsWhenFull() throws Throwable {
        char[] seg = tb.getCurrentSegment();
        assertNotNull(seg);
        tb.setCurrentLength(seg.length);
        char[] seg2 = tb.getCurrentSegment();
        assertTrue(seg2.length > seg.length);
        assertEquals(0, tb.getCurrentSegmentSize());
    }

    // getCurrentSegmentSize()/setCurrentLength(): simple getter/setter contract
    @Test
    public void testSetCurrentLength_and_getCurrentSegmentSize() throws Throwable {
        tb.getCurrentSegment();
        tb.setCurrentLength(7);
        assertEquals(7, tb.getCurrentSegmentSize());
    }

    // finishCurrentSegment(): full segment moved to list, combined correctly later
    @Test
    public void testFinishCurrentSegment_combinesSegmentsInContentsAsString() throws Throwable {
        char[] seg = tb.getCurrentSegment();
        int segLen = seg.length;
        Arrays.fill(seg, 'a');
        tb.setCurrentLength(segLen);
        char[] next = tb.finishCurrentSegment();
        next[0] = 'b';
        tb.setCurrentLength(1);
        String result = tb.contentsAsString();
        assertEquals(segLen + 1, result.length());
        assertEquals('a', result.charAt(0));
        assertEquals('b', result.charAt(segLen));
    }



    // expandCurrentSegment(): normal growth path (+50%)
    @Test
    public void testExpandCurrentSegment_normalGrowth_increasesByHalf() throws Throwable {
        tb.getCurrentSegment();
        char[] expanded = tb.expandCurrentSegment();
        assertEquals(TextBuffer.MIN_SEGMENT_LEN + (TextBuffer.MIN_SEGMENT_LEN >> 1), expanded.length);
    }



    // expandCurrentSegment(int): already large enough -> same reference returned
    @Test
    public void testExpandCurrentSegmentWithMinSize_alreadyLargeEnough_returnsSameArray() throws Throwable {
        char[] seg = tb.getCurrentSegment();
        char[] result = tb.expandCurrentSegment(seg.length);
        assertSame(seg, result);
    }

    // expandCurrentSegment(int): too small -> grows to exactly minSize
    @Test
    public void testExpandCurrentSegmentWithMinSize_tooSmall_growsToMinSize() throws Throwable {
        tb.getCurrentSegment();
        char[] result = tb.expandCurrentSegment(2500);
        assertEquals(2500, result.length);
    }

    // toString(): delegates to contentsAsString()
    @Test
    public void testToString_equalsContentsAsString() throws Throwable {
        tb.resetWithString("xyz");
        assertEquals("xyz", tb.toString());
    }



    // resetWithCopy(): makes defensive copy, unaffected by later source mutation
    @Test
    public void testResetWithCopy_isDefensiveCopy_notAffectedByLaterMutation() throws Throwable {
        char[] src = new char[]{'a','b','c'};
        tb.resetWithCopy(src, 0, 3);
        src[0] = 'Z';
        assertEquals("abc", tb.contentsAsString());
    }

    private static char[] buildPattern(int len) {
        char[] arr = new char[len];
        for (int i = 0; i < len; ++i) {
            arr[i] = (char) ('A' + (i % 26));
        }
        return arr;
    }
}
