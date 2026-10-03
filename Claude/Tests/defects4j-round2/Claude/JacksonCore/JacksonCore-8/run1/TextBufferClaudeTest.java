package com.fasterxml.jackson.core.util;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TextBufferClaudeTest
{
    private TextBuffer buffer;

    @Before
    public void setUp() throws Throwable {
        buffer = new TextBuffer(null);
    }

    // releaseBuffers(): allocator == null branch delegates to resetWithEmpty()
    @Test
    public void testReleaseBuffers_withNullAllocator_resetsToEmpty() throws Throwable {
        buffer.append("data", 0, 4);
        buffer.releaseBuffers();
        assertEquals(0, buffer.size());
    }

    // resetWithEmpty(): clears previously appended content
    @Test
    public void testResetWithEmpty_afterContent_sizeZero() throws Throwable {
        buffer.append("hello", 0, 5);
        buffer.resetWithEmpty();
        assertEquals(0, buffer.size());
    }

    // resetWithShared(): size() returns shared length branch
    @Test
    public void testResetWithShared_size_returnsLen() throws Throwable {
        char[] src = "helloworld".toCharArray();
        buffer.resetWithShared(src, 2, 5);
        assertEquals(5, buffer.size());
    }

    // resetWithShared(): getTextOffset() returns shared start
    @Test
    public void testResetWithShared_getTextOffset_returnsStart() throws Throwable {
        char[] src = "helloworld".toCharArray();
        buffer.resetWithShared(src, 3, 4);
        assertEquals(3, buffer.getTextOffset());
    }

    // resetWithShared(): hasTextAsCharacters() true when inputStart>=0
    @Test
    public void testResetWithShared_hasTextAsCharacters_true() throws Throwable {
        char[] src = "abcdef".toCharArray();
        buffer.resetWithShared(src, 0, 6);
        assertTrue(buffer.hasTextAsCharacters());
    }

    // contentsAsString(): shared branch with inputLen < 1 returns ""
    @Test
    public void testResetWithShared_contentsAsString_zeroLen_returnsEmpty() throws Throwable {
        char[] src = "abcdef".toCharArray();
        buffer.resetWithShared(src, 2, 0);
        assertEquals("", buffer.contentsAsString());
    }

    // contentsAsString(): shared branch returns correct substring
    @Test
    public void testResetWithShared_contentsAsString_returnsSubstring() throws Throwable {
        char[] src = "helloworld".toCharArray();
        buffer.resetWithShared(src, 2, 5);
        assertEquals("llowo", buffer.contentsAsString());
    }

    // getTextBuffer(): shared branch returns exact same array reference
    @Test
    public void testResetWithShared_getTextBuffer_returnsSharedArray() throws Throwable {
        char[] src = "abcdef".toCharArray();
        buffer.resetWithShared(src, 1, 3);
        assertSame(src, buffer.getTextBuffer());
    }

    // resetWithCopy(): copies content, independent of later source mutation
    @Test
    public void testResetWithCopy_copiesIndependently() throws Throwable {
        char[] src = "abcdef".toCharArray();
        buffer.resetWithCopy(src, 1, 3);
        src[1] = 'Z';
        assertEquals("bcd", buffer.contentsAsString());
    }

    // resetWithCopy(): _hasSegments branch clears prior multi-segment state
    @Test
    public void testResetWithCopy_afterMultiSegment_clearsOldSegments() throws Throwable {
        buffer.append('a');
        char[] filler = new char[1500];
        Arrays.fill(filler, 'x');
        buffer.append(filler, 0, filler.length);
        buffer.resetWithCopy("new".toCharArray(), 0, 3);
        assertEquals("new", buffer.contentsAsString());
        assertEquals(3, buffer.size());
    }

    // resetWithString(): size() returns resultString length branch
    @Test
    public void testResetWithString_size_matchesLength() throws Throwable {
        buffer.resetWithString("hello world");
        assertEquals(11, buffer.size());
    }

    // hasTextAsCharacters(): false when only resultString is set
    @Test
    public void testResetWithString_hasTextAsCharacters_false() throws Throwable {
        buffer.resetWithString("plain");
        assertFalse(buffer.hasTextAsCharacters());
    }

    // contentsAsString(): resultString branch returns stored value directly
    @Test
    public void testResetWithString_contentsAsString_returnsSameValue() throws Throwable {
        buffer.resetWithString("jackson");
        assertEquals("jackson", buffer.contentsAsString());
    }

    // getTextBuffer(): resultString branch materializes char array on demand
    @Test
    public void testResetWithString_getTextBuffer_matchesCharArray() throws Throwable {
        buffer.resetWithString("abc");
        char[] arr = buffer.getTextBuffer();
        assertArrayEquals("abc".toCharArray(), arr);
    }

    // getTextOffset(): non-shared branch always returns zero
    @Test
    public void testGetTextOffset_nonShared_returnsZero() throws Throwable {
        buffer.append("x", 0, 1);
        assertEquals(0, buffer.getTextOffset());
    }

    // hasTextAsCharacters(): once resultArray cached via getTextBuffer(), becomes true
    @Test
    public void testHasTextAsCharacters_afterGetTextBufferCachesArray_true() throws Throwable {
        buffer.resetWithString("cached");
        buffer.getTextBuffer();
        assertTrue(buffer.hasTextAsCharacters());
    }

    // contentsAsString(): empty single-segment branch (segLen==0, currLen==0)
    @Test
    public void testContentsAsString_emptyBuffer_returnsEmptyString() throws Throwable {
        buffer.resetWithEmpty();
        assertEquals("", buffer.contentsAsString());
    }

    // contentsAsArray(): size<1 branch returns NO_CHARS (empty array)
    @Test
    public void testContentsAsArray_emptyBuffer_returnsEmptyArray() throws Throwable {
        buffer.resetWithEmpty();
        char[] arr = buffer.contentsAsArray();
        assertEquals(0, arr.length);
    }

    // contentsAsArray(): single segment branch matches appended content
    @Test
    public void testContentsAsArray_matchesString() throws Throwable {
        buffer.append("testdata", 0, 8);
        char[] arr = buffer.contentsAsArray();
        assertArrayEquals("testdata".toCharArray(), arr);
    }

    // contentsAsArray(): multi-segment branch combines all segments correctly
    @Test
    public void testContentsAsArray_multiSegment_matchesConcatenation() throws Throwable {
        buffer.append('M');
        char[] src = new char[2500];
        for (int i = 0; i < src.length; i++) {
            src[i] = (char) ('0' + (i % 10));
        }
        buffer.append(src, 0, src.length);
        char[] expected = new char[2501];
        expected[0] = 'M';
        System.arraycopy(src, 0, expected, 1, src.length);
        assertArrayEquals(expected, buffer.contentsAsArray());
    }

    // contentsAsDecimal(): parses textual content as BigDecimal per standard format
    @Test
    public void testContentsAsDecimal_parsesCorrectValue() throws Throwable {
        buffer.resetWithString("123.45");
        BigDecimal result = buffer.contentsAsDecimal();
        assertEquals(0, result.compareTo(new BigDecimal("123.45")));
    }

    // contentsAsDouble(): parses textual content as double
    @Test
    public void testContentsAsDouble_parsesCorrectValue() throws Throwable {
        buffer.resetWithString("3.5");
        double d = buffer.contentsAsDouble();
        assertEquals(3.5, d, 1e-9);
    }

    // ensureNotShared(): unshares content, getTextOffset resets to 0, content preserved
    @Test
    public void testEnsureNotShared_afterShared_noLongerShared() throws Throwable {
        char[] src = "shared".toCharArray();
        buffer.resetWithShared(src, 0, 6);
        buffer.ensureNotShared();
        assertEquals(0, buffer.getTextOffset());
        assertEquals("shared", buffer.contentsAsString());
    }

    // append(char): simple accumulation into a single segment
    @Test
    public void testAppendChar_singleChar_contentsCorrect() throws Throwable {
        buffer.append('x');
        buffer.append('y');
        assertEquals("xy", buffer.contentsAsString());
    }

    // append(char): loop triggers expand() multiple times (multi-segment path)
    @Test
    public void testAppendChar_manySingleChars_multiSegment_contentsCorrect() throws Throwable {
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            char c = (char) ('a' + (i % 26));
            buffer.append(c);
            expected.append(c);
        }
        assertEquals(expected.toString(), buffer.contentsAsString());
        assertEquals(2500, buffer.size());
    }

    // append(char[],start,len): fits in one segment (max>=len branch)
    @Test
    public void testAppendCharArray_partial_contentsCorrect() throws Throwable {
        char[] src = "helloworld".toCharArray();
        buffer.append(src, 2, 5);
        assertEquals("llowo", buffer.contentsAsString());
    }

    // append(char[],start,len): exceeds remaining room, forces multi-segment loop
    @Test
    public void testAppendCharArray_multiSegment_contentsCorrect() throws Throwable {
        buffer.append('Z');
        char[] src = new char[3000];
        for (int i = 0; i < src.length; i++) {
            src[i] = (char) ('A' + (i % 26));
        }
        buffer.append(src, 0, src.length);
        assertEquals("Z" + new String(src), buffer.contentsAsString());
        assertEquals(3001, buffer.size());
    }

    // append(char[],start,0): zero-length append leaves content unchanged
    @Test
    public void testAppendCharArray_zeroLength_noChange() throws Throwable {
        buffer.append('a');
        buffer.append(new char[]{'b', 'c'}, 0, 0);
        assertEquals("a", buffer.contentsAsString());
    }

    // append(String,offset,len): fits in one segment
    @Test
    public void testAppendString_partial_contentsCorrect() throws Throwable {
        buffer.append("helloworld", 2, 5);
        assertEquals("llowo", buffer.contentsAsString());
    }

    // append(String,offset,len): exceeds remaining room, forces multi-segment loop
    @Test
    public void testAppendString_multiSegment_contentsCorrect() throws Throwable {
        buffer.append('Q');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        String s = sb.toString();
        buffer.append(s, 0, s.length());
        assertEquals("Q" + s, buffer.contentsAsString());
        assertEquals(3001, buffer.size());
    }

    // append(char): unshares previously shared buffer before appending
    @Test
    public void testAppend_afterSharedBuffer_unsharesAndAppends() throws Throwable {
        char[] src = "hello".toCharArray();
        buffer.resetWithShared(src, 0, 5);
        buffer.append('!');
        assertEquals("hello!", buffer.contentsAsString());
    }

    // getCurrentSegment(): curr==null branch allocates fresh MIN_SEGMENT_LEN buffer
    @Test
    public void testGetCurrentSegment_initialAllocation_lengthAtLeastMinSegment() throws Throwable {
        buffer.resetWithEmpty();
        char[] seg = buffer.getCurrentSegment();
        assertNotNull(seg);
        assertTrue(seg.length >= TextBuffer.MIN_SEGMENT_LEN);
    }

    // getCurrentSegment(): inputStart>=0 branch unshares shared content first
    @Test
    public void testGetCurrentSegment_sharedInput_unshares() throws Throwable {
        char[] src = "abc".toCharArray();
        buffer.resetWithShared(src, 0, 3);
        char[] seg = buffer.getCurrentSegment();
        assertEquals(3, buffer.getCurrentSegmentSize());
        assertEquals('a', seg[0]);
    }

    // emptyAndGetCurrentSegment(): resets size to zero and returns usable segment
    @Test
    public void testEmptyAndGetCurrentSegment_resetsSizeAndReturnsBuffer() throws Throwable {
        buffer.append("data", 0, 4);
        char[] seg = buffer.emptyAndGetCurrentSegment();
        assertNotNull(seg);
        assertEquals(0, buffer.size());
    }

    // getCurrentSegmentSize()/setCurrentLength(): raw size bookkeeping
    @Test
    public void testGetCurrentSegmentSize_and_setCurrentLength() throws Throwable {
        buffer.resetWithEmpty();
        buffer.getCurrentSegment();
        buffer.setCurrentLength(42);
        assertEquals(42, buffer.getCurrentSegmentSize());
    }

    // setCurrentAndReturn(): single-segment branch (segmentSize==0)
    @Test
    public void testSetCurrentAndReturn_singleSegment_returnsExactString() throws Throwable {
        buffer.resetWithEmpty();
        char[] seg = buffer.getCurrentSegment();
        seg[0] = 'h';
        seg[1] = 'i';
        String result = buffer.setCurrentAndReturn(2);
        assertEquals("hi", result);
    }

    // finishCurrentSegment(): new segment grows by 50% of old length
    @Test
    public void testFinishCurrentSegment_growsBy50Percent() throws Throwable {
        buffer.resetWithEmpty();
        char[] seg = buffer.getCurrentSegment();
        int oldLen = seg.length;
        buffer.setCurrentLength(oldLen);
        char[] next = buffer.finishCurrentSegment();
        assertEquals(oldLen + (oldLen >> 1), next.length);
    }

    // contentsAsString(): multi-segment branch combines finished segment + current
    @Test
    public void testContentsAsString_multiSegmentAfterFinish_combinesCorrectly() throws Throwable {
        buffer.resetWithEmpty();
        char[] seg1 = buffer.getCurrentSegment();
        int len1 = seg1.length;
        Arrays.fill(seg1, 'a');
        buffer.setCurrentLength(len1);
        char[] seg2 = buffer.finishCurrentSegment();
        seg2[0] = 'b';
        buffer.setCurrentLength(1);
        char[] aArr = new char[len1];
        Arrays.fill(aArr, 'a');
        assertEquals(new String(aArr) + "b", buffer.contentsAsString());
    }

    // expandCurrentSegment(): no-arg version grows current segment by 50%
    @Test
    public void testExpandCurrentSegment_growsBy50Percent() throws Throwable {
        buffer.resetWithEmpty();
        char[] seg = buffer.getCurrentSegment();
        int oldLen = seg.length;
        char[] expanded = buffer.expandCurrentSegment();
        assertEquals(oldLen + (oldLen >> 1), expanded.length);
    }

    // expandCurrentSegment(minSize): minSize <= current length returns same array
    @Test
    public void testExpandCurrentSegmentWithMinSize_smallerThanCurrent_returnsSameArray() throws Throwable {
        buffer.resetWithEmpty();
        char[] seg = buffer.getCurrentSegment();
        char[] result = buffer.expandCurrentSegment(10);
        assertSame(seg, result);
    }

    // expandCurrentSegment(minSize): minSize > current length grows to exactly minSize
    @Test
    public void testExpandCurrentSegmentWithMinSize_largerThanCurrent_growsToMinSize() throws Throwable {
        buffer.resetWithEmpty();
        buffer.getCurrentSegment();
        char[] result = buffer.expandCurrentSegment(2500);
        assertEquals(2500, result.length);
    }

    // toString(): delegates to contentsAsString()
    @Test
    public void testToString_delegatesToContentsAsString() throws Throwable {
        buffer.append("abc", 0, 3);
        assertEquals(buffer.contentsAsString(), buffer.toString());
    }

    // size(): resultArray branch reflects cached array length
    @Test
    public void testSize_afterContentsAsArrayCached_matchesArrayLength() throws Throwable {
        buffer.append("hello", 0, 5);
        buffer.contentsAsArray();
        assertEquals(5, buffer.size());
    }
}
