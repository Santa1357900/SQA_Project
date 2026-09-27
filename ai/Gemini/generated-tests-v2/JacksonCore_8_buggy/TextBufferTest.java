package com.fasterxml.jackson.core.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;

public class TextBufferTest {

    @Test
    public void testLifeCycleAndReset() throws Throwable {
        BufferRecycler recycler = new BufferRecycler();
        TextBuffer tb = new TextBuffer(recycler);
        
        assertNotNull(tb.getCurrentSegment());
        tb.append('a');
        assertEquals(1, tb.size());
        
        tb.resetWithEmpty();
        assertEquals(0, tb.size());
        assertEquals("", tb.contentsAsString());
        
        tb.releaseBuffers();
    }

    @Test
    public void testReleaseBuffersNullAllocator() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.append('x');
        tb.releaseBuffers();
        assertEquals(0, tb.size());
    }

    @Test
    public void testResetWithShared() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        char[] shared = new char[] {'H', 'e', 'l', 'l', 'o'};
        tb.resetWithShared(shared, 1, 3);
        
        assertEquals(3, tb.size());
        assertEquals(1, tb.getTextOffset());
        assertTrue(tb.hasTextAsCharacters());
        assertArrayEquals(shared, tb.getTextBuffer());
        assertEquals("ell", tb.contentsAsString());
        
        // Test shared with empty len
        tb.resetWithShared(shared, 0, 0);
        assertEquals(0, tb.size());
        assertEquals("", tb.contentsAsString());
    }

    @Test
    public void testResetWithCopy() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        char[] data = new char[] {'W', 'o', 'r', 'l', 'd'};
        tb.resetWithCopy(data, 0, 5);
        
        assertEquals(5, tb.size());
        assertEquals("World", tb.contentsAsString());
        assertArrayEquals(data, tb.contentsAsArray());
    }

    @Test
    public void testResetWithString() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.resetWithString("Jackson");
        
        assertEquals(7, tb.size());
        assertFalse(tb.hasTextAsCharacters());
        assertEquals("Jackson", tb.contentsAsString());
        assertArrayEquals(new char[] {'J', 'a', 'c', 'k', 's', 'o', 'n'}, tb.getTextBuffer());
        // Second call to check caching/resultArray branch
        assertArrayEquals(new char[] {'J', 'a', 'c', 'k', 's', 'o', 'n'}, tb.contentsAsArray());
    }

    @Test
    public void testAppendOperations() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.append('A');
        tb.append(new char[] {'B', 'C', 'D'}, 0, 3);
        tb.append("EFG", 0, 3);
        
        assertEquals(7, tb.size());
        assertEquals("ABCDEFG", tb.contentsAsString());
        
        // Test append exceeding current segment and triggering unshare/expand
        tb.resetWithShared(new char[] {'X', 'Y', 'Z'}, 0, 3);
        tb.append('W');
        assertEquals("XYZW", tb.contentsAsString());
    }

    @Test
    public void testLargeAppendAndSegments() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        char[] largeData = new char[TextBuffer.MIN_SEGMENT_LEN + 500];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = 't';
        }
        tb.append(largeData, 0, largeData.length);
        assertEquals(largeData.length, tb.size());
        assertNotNull(tb.contentsAsString());
        assertNotNull(tb.contentsAsArray());
    }

    @Test
    public void testAppendStringWithOffsets() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        // Force small segment room or trigger max < len
        char[] curr = tb.getCurrentSegment();
        tb.setCurrentLength(curr.length - 1);
        
        tb.append("12345", 0, 3);
        assertTrue(tb.size() >= 3);
    }

    @Test
    public void testSegmentManipulation() throws Throwable {
        BufferRecycler recycler = new BufferRecycler();
        TextBuffer tb = new TextBuffer(recycler);
        
        char[] seg1 = tb.getCurrentSegment();
        assertNotNull(seg1);
        
        char[] seg2 = tb.finishCurrentSegment();
        assertNotNull(seg2);
        
        char[] expanded = tb.expandCurrentSegment();
        assertNotNull(expanded);
        
        char[] expandedMin = tb.expandCurrentSegment(2000);
        assertTrue(expandedMin.length >= 2000);
        
        char[] emptyCurr = tb.emptyAndGetCurrentSegment();
        assertNotNull(emptyCurr);
        
        assertEquals(0, tb.getCurrentSegmentSize());
        tb.setCurrentLength(5);
        assertEquals(5, tb.getCurrentSegmentSize());
        
        assertEquals("     ", tb.setCurrentAndReturn(5));
        assertEquals("", tb.setCurrentAndReturn(0));
    }

    @Test
    public void testContentsAsDecimal() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.resetWithString("123.45");
        BigDecimal dec = tb.contentsAsDecimal();
        assertEquals(new BigDecimal("123.45"), dec);

        // Test with resultArray
        tb.resetWithCopy(new char[] {'9', '9', '.', '9'}, 0, 4);
        BigDecimal dec2 = tb.contentsAsDecimal();
        assertEquals(new BigDecimal("99.9"), dec2);

        // Test with shared buffer
        tb.resetWithShared(new char[] {'0', '.', '5'}, 0, 3);
        BigDecimal dec3 = tb.contentsAsDecimal();
        assertEquals(new BigDecimal("0.5"), dec3);

        // Test with single segment
        tb.resetWithEmpty();
        tb.append("7.7", 0, 3);
        BigDecimal dec4 = tb.contentsAsDecimal();
        assertEquals(new BigDecimal("7.7"), dec4);
    }

    @Test
    public void testContentsAsDouble() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.resetWithString("123.45");
        double d = tb.contentsAsDouble();
        assertEquals(123.45, d, 0.001);
    }

    @Test
    public void testToStringOverride() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.resetWithString("TestString");
        assertEquals("TestString", tb.toString());
    }

    @Test
    public void testResultArrayEdgeCases() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        // Empty shared buffer with start == 0
        tb.resetWithShared(new char[] {'a'}, 0, 0);
        assertArrayEquals(TextBuffer.NO_CHARS, tb.contentsAsArray());

        // Shared buffer with start > 0 and len > 0
        tb.resetWithShared(new char[] {'x', 'y', 'z'}, 1, 2);
        assertArrayEquals(new char[] {'y', 'z'}, tb.contentsAsArray());
        
        // Zero size contents as array
        tb.resetWithEmpty();
        assertArrayEquals(TextBuffer.NO_CHARS, tb.contentsAsArray());
    }
}