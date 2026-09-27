package com.fasterxml.jackson.core.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;

public class TextBufferTest {

    @Test
    public void testLifeCycleAndReleaseBuffers() throws Throwable {
        BufferRecycler recycler = new BufferRecycler();
        TextBuffer tb = new TextBuffer(recycler);
        
        tb.getCurrentSegment();
        assertNotNull(tb.getCurrentSegment());
        
        tb.releaseBuffers();
        assertEquals(0, tb.size());
        
        TextBuffer tbNullAllocator = new TextBuffer(null);
        tbNullAllocator.releaseBuffers();
        assertEquals(0, tbNullAllocator.size());
    }

    @Test
    public void testResetWithEmpty() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.append('a');
        assertEquals(1, tb.size());
        
        tb.resetWithEmpty();
        assertEquals(0, tb.size());
        assertEquals("", tb.contentsAsString());
    }

    @Test
    public void testResetWithShared() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        char[] data = new char[] { 'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd' };
        tb.resetWithShared(data, 6, 5);
        
        assertEquals(5, tb.size());
        assertEquals(6, tb.getTextOffset());
        assertTrue(tb.hasTextAsCharacters());
        assertArrayEquals(data, tb.getTextBuffer());
        assertEquals("World", tb.contentsAsString());
        
        // Test shared with len < 1
        tb.resetWithShared(data, 0, 0);
        assertEquals(0, tb.size());
        assertEquals("", tb.contentsAsString());
    }

    @Test
    public void testResetWithCopy() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        char[] data = new char[] { 'T', 'e', 's', 't' };
        tb.resetWithCopy(data, 0, 4);
        
        assertEquals(4, tb.size());
        assertEquals("Test", tb.contentsAsString());
    }

    @Test
    public void testResetWithString() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.resetWithString("Jackson");
        
        assertEquals(7, tb.size());
        assertFalse(tb.hasTextAsCharacters());
        assertEquals("Jackson", tb.contentsAsString());
        assertNotNull(tb.getTextBuffer()); // triggers toCharArray cache
    }

    @Test
    public void testAppendCharAndUnshare() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        char[] data = new char[] { 'S', 'h', 'a', 'r', 'e', 'd' };
        tb.resetWithShared(data, 0, 6);
        
        // Append char should trigger unshare
        tb.append('!');
        assertEquals(7, tb.size());
        assertEquals("Shared!", tb.contentsAsString());
    }

    @Test
    public void testAppendCharArrayAndSegments() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        
        // Force multiple segments by appending large data
        char[] largeData = new char[2500];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = 'x';
        }
        
        tb.append(largeData, 0, largeData.length);
        assertEquals(2500, tb.size());
        
        // Test finishCurrentSegment and segment expansion
        tb.finishCurrentSegment();
        tb.append('y');
        assertEquals(2501, tb.size());
        
        char[] arr = tb.contentsAsArray();
        assertEquals(2501, arr.length);
        
        // Test cached resultArray
        char[] arr2 = tb.contentsAsArray();
        assertSame(arr, arr2);
    }

    @Test
    public void testAppendStringCases() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        
        // Small append fitting in current segment
        tb.append("Hello", 0, 5);
        assertEquals(5, tb.size());
        
        // Append exceeding current segment space to test multi-segment String append
        char[] dummy = new char[TextBuffer.MIN_SEGMENT_LEN + 100];
        for(int i=0; i<dummy.length; i++) {
            dummy[i] = 'a';
        }
        String largeStr = new String(dummy);
        tb.append(largeStr, 0, largeStr.length());
        assertTrue(tb.size() > TextBuffer.MIN_SEGMENT_LEN);
    }

    @Test
    public void testGettersAndAccessors() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.append("Test");
        
        assertEquals(4, tb.size());
        assertEquals(0, tb.getTextOffset());
        assertTrue(tb.hasTextAsCharacters());
        
        // Test toString
        assertEquals("Test", tb.toString());
    }

    @Test
    public void testContentsAsDecimal() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.append("123.45");
        BigDecimal dec = tb.contentsAsDecimal();
        assertEquals(new BigDecimal("123.45"), dec);
        
        // Test with shared buffer decimal
        TextBuffer tbShared = new TextBuffer(null);
        char[] numChars = "999.99".toCharArray();
        tbShared.resetWithShared(numChars, 0, 6);
        assertEquals(new BigDecimal("999.99"), tbShared.contentsAsDecimal());
        
        // Test with result array decimal
        TextBuffer tbArray = new TextBuffer(null);
        tbArray.resetWithString("50.5");
        tbArray.contentsAsArray(); // caches resultArray
        assertEquals(new BigDecimal("50.5"), tbArray.contentsAsDecimal());
        
        // Test segmented decimal
        TextBuffer tbSeg = new TextBuffer(null);
        char[] segPart1 = new char[TextBuffer.MIN_SEGMENT_LEN];
        Arrays.fill(segPart1, '1');
        tbSeg.append(segPart1, 0, segPart1.length);
        tbSeg.append(".5", 0, 2);
        try {
            tbSeg.contentsAsDecimal();
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // Expected
        }
    }

    @Test
    public void testContentsAsDouble() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        tb.append("123.45");
        assertEquals(123.45, tb.contentsAsDouble(), 0.001);
    }

    @Test
    public void testEnsureNotShared() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        char[] data = new char[] { 'D', 'a', 't', 'a' };
        tb.resetWithShared(data, 0, 4);
        
        tb.ensureNotShared();
        assertEquals("Data", tb.contentsAsString());
    }

    @Test
    public void testRawSegmentOperations() throws Throwable {
        TextBuffer tb = new TextBuffer(null);
        
        char[] seg = tb.getCurrentSegment();
        assertNotNull(seg);
        
        tb.setCurrentLength(3);
        assertEquals(3, tb.getCurrentSegmentSize());
        
        char[] emptySeg = tb.emptyAndGetCurrentSegment();
        assertNotNull(emptySeg);
        assertEquals(0, tb.getCurrentSegmentSize());
        
        char[] expanded = tb.expandCurrentSegment();
        assertNotNull(expanded);
        
        char[] expandedMin = tb.expandCurrentSegment(5000);
        assertTrue(expandedMin.length >= 5000);
    }
}