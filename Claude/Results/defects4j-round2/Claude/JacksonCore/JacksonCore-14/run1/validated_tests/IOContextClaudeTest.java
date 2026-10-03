package com.fasterxml.jackson.core.io;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.TextBuffer;

public class IOContextClaudeTest
{
    private BufferRecycler recycler;
    private IOContext context;

    @Before
    public void setUp() throws Throwable {
        recycler = new BufferRecycler();
        context = new IOContext(recycler, "sourceRef", true);
    }

    // Constructor stores sourceRef and managedResource=true
    @Test
    public void testConstructor_storesSourceReferenceAndManagedFlag() throws Throwable {
        assertEquals("sourceRef", context.getSourceReference());
        assertTrue(context.isResourceManaged());
    }

    // Constructor with managedResource=false
    @Test
    public void testConstructor_managedResourceFalse() throws Throwable {
        IOContext ctx2 = new IOContext(recycler, "other", false);
        assertFalse(ctx2.isResourceManaged());
    }

    // Constructor with null sourceRef
    @Test
    public void testConstructor_nullSourceReference() throws Throwable {
        IOContext ctx2 = new IOContext(recycler, null, false);
        assertNull(ctx2.getSourceReference());
    }

    // setEncoding updates value returned by getEncoding
    @Test
    public void testSetEncoding_updatesGetEncoding() throws Throwable {
        context.setEncoding(JsonEncoding.UTF8);
        assertEquals(JsonEncoding.UTF8, context.getEncoding());
    }

    // withEncoding returns same instance (fluent) and sets encoding
    @Test
    public void testWithEncoding_returnsSameInstanceAndSetsEncoding() throws Throwable {
        IOContext result = context.withEncoding(JsonEncoding.UTF16_BE);
        assertSame(context, result);
        assertEquals(JsonEncoding.UTF16_BE, context.getEncoding());
    }

    // getEncoding default value is null before any set
    @Test
    public void testGetEncoding_defaultIsNull() throws Throwable {
        assertNull(context.getEncoding());
    }

    // constructTextBuffer returns non-null TextBuffer
    @Test
    public void testConstructTextBuffer_returnsNonNullInstance() throws Throwable {
        TextBuffer tb = context.constructTextBuffer();
        assertNotNull(tb);
    }

    // allocReadIOBuffer first call returns non-null buffer
    @Test
    public void testAllocReadIOBuffer_returnsNonNullBuffer() throws Throwable {
        byte[] buf = context.allocReadIOBuffer();
        assertNotNull(buf);
        assertTrue(buf.length > 0);
    }

    // allocReadIOBuffer second call throws IllegalStateException
    @Test
    public void testAllocReadIOBuffer_calledTwice_throwsIllegalStateException() throws Throwable {
        context.allocReadIOBuffer();
        try {
            context.allocReadIOBuffer();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // allocReadIOBuffer(minSize) returns buffer at least requested size
    @Test
    public void testAllocReadIOBufferWithMinSize_returnsBufferAtLeastRequestedSize() throws Throwable {
        byte[] buf = context.allocReadIOBuffer(5000);
        assertTrue(buf.length >= 5000);
    }

    // allocWriteEncodingBuffer first call returns non-null buffer
    @Test
    public void testAllocWriteEncodingBuffer_returnsNonNullBuffer() throws Throwable {
        byte[] buf = context.allocWriteEncodingBuffer();
        assertNotNull(buf);
        assertTrue(buf.length > 0);
    }

    // allocWriteEncodingBuffer second call throws IllegalStateException
    @Test
    public void testAllocWriteEncodingBuffer_calledTwice_throwsIllegalStateException() throws Throwable {
        context.allocWriteEncodingBuffer();
        try {
            context.allocWriteEncodingBuffer();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // allocWriteEncodingBuffer(minSize) returns buffer at least requested size
    @Test
    public void testAllocWriteEncodingBufferWithMinSize_returnsBufferAtLeastRequestedSize() throws Throwable {
        byte[] buf = context.allocWriteEncodingBuffer(3000);
        assertTrue(buf.length >= 3000);
    }

    // allocBase64Buffer first call returns non-null buffer
    @Test
    public void testAllocBase64Buffer_returnsNonNullBuffer() throws Throwable {
        byte[] buf = context.allocBase64Buffer();
        assertNotNull(buf);
        assertTrue(buf.length > 0);
    }

    // allocBase64Buffer second call throws IllegalStateException
    @Test
    public void testAllocBase64Buffer_calledTwice_throwsIllegalStateException() throws Throwable {
        context.allocBase64Buffer();
        try {
            context.allocBase64Buffer();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // allocTokenBuffer first call returns non-null char buffer
    @Test
    public void testAllocTokenBuffer_returnsNonNullBuffer() throws Throwable {
        char[] buf = context.allocTokenBuffer();
        assertNotNull(buf);
        assertTrue(buf.length > 0);
    }

    // allocTokenBuffer second call throws IllegalStateException
    @Test
    public void testAllocTokenBuffer_calledTwice_throwsIllegalStateException() throws Throwable {
        context.allocTokenBuffer();
        try {
            context.allocTokenBuffer();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // allocTokenBuffer(minSize) returns buffer at least requested size
    @Test
    public void testAllocTokenBufferWithMinSize_returnsBufferAtLeastRequestedSize() throws Throwable {
        char[] buf = context.allocTokenBuffer(4096);
        assertTrue(buf.length >= 4096);
    }

    // allocConcatBuffer first call returns non-null char buffer
    @Test
    public void testAllocConcatBuffer_returnsNonNullBuffer() throws Throwable {
        char[] buf = context.allocConcatBuffer();
        assertNotNull(buf);
        assertTrue(buf.length > 0);
    }

    // allocConcatBuffer second call throws IllegalStateException
    @Test
    public void testAllocConcatBuffer_calledTwice_throwsIllegalStateException() throws Throwable {
        context.allocConcatBuffer();
        try {
            context.allocConcatBuffer();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // allocNameCopyBuffer(minSize) returns buffer at least requested size
    @Test
    public void testAllocNameCopyBuffer_returnsBufferAtLeastRequestedSize() throws Throwable {
        char[] buf = context.allocNameCopyBuffer(256);
        assertTrue(buf.length >= 256);
    }

    // allocNameCopyBuffer second call throws IllegalStateException
    @Test
    public void testAllocNameCopyBuffer_calledTwice_throwsIllegalStateException() throws Throwable {
        context.allocNameCopyBuffer(256);
        try {
            context.allocNameCopyBuffer(256);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // releaseReadIOBuffer(null) is a no-op, buffer still allocatable afterwards
    @Test
    public void testReleaseReadIOBuffer_nullArgument_noExceptionAndBufferStillAllocatable() throws Throwable {
        context.releaseReadIOBuffer(null);
        byte[] buf = context.allocReadIOBuffer();
        assertNotNull(buf);
    }

    // releasing the exact same reference succeeds and allows re-allocation
    @Test
    public void testReleaseReadIOBuffer_sameReference_allowsReallocation() throws Throwable {
        byte[] buf = context.allocReadIOBuffer();
        context.releaseReadIOBuffer(buf);
        byte[] buf2 = context.allocReadIOBuffer();
        assertNotNull(buf2);
    }

    // BUG: per javadoc/comment "only prevent shrinking of buffer", a different buffer
    // of the SAME length as the owned buffer must be accepted (not treated as shrinking)
    @Test
    public void testReleaseReadIOBuffer_differentBufferSameLength_doesNotThrow() throws Throwable {
        byte[] original = context.allocReadIOBuffer();
        byte[] sameLength = new byte[original.length];
        context.releaseReadIOBuffer(sameLength);
        byte[] buf2 = context.allocReadIOBuffer();
        assertNotNull(buf2);
    }

    // releasing a strictly smaller buffer must throw IllegalArgumentException
    @Test
    public void testReleaseReadIOBuffer_smallerBuffer_throwsIllegalArgumentException() throws Throwable {
        byte[] original = context.allocReadIOBuffer();
        byte[] smaller = new byte[original.length - 1];
        try {
            context.releaseReadIOBuffer(smaller);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // releasing a larger buffer (upgrade) must not throw
    @Test
    public void testReleaseReadIOBuffer_largerBuffer_doesNotThrow() throws Throwable {
        byte[] original = context.allocReadIOBuffer();
        byte[] larger = new byte[original.length + 16];
        context.releaseReadIOBuffer(larger);
        byte[] buf2 = context.allocReadIOBuffer();
        assertNotNull(buf2);
    }

    // releaseWriteEncodingBuffer with smaller buffer throws IllegalArgumentException
    @Test
    public void testReleaseWriteEncodingBuffer_smallerBuffer_throwsIllegalArgumentException() throws Throwable {
        byte[] original = context.allocWriteEncodingBuffer();
        byte[] smaller = new byte[original.length - 1];
        try {
            context.releaseWriteEncodingBuffer(smaller);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // releaseBase64Buffer with smaller buffer throws IllegalArgumentException
    @Test
    public void testReleaseBase64Buffer_smallerBuffer_throwsIllegalArgumentException() throws Throwable {
        byte[] original = context.allocBase64Buffer();
        byte[] smaller = new byte[original.length - 1];
        try {
            context.releaseBase64Buffer(smaller);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // releaseTokenBuffer with smaller char buffer throws IllegalArgumentException
    @Test
    public void testReleaseTokenBuffer_smallerBuffer_throwsIllegalArgumentException() throws Throwable {
        char[] original = context.allocTokenBuffer();
        char[] smaller = new char[original.length - 1];
        try {
            context.releaseTokenBuffer(smaller);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // releaseConcatBuffer with smaller char buffer throws IllegalArgumentException
    @Test
    public void testReleaseConcatBuffer_smallerBuffer_throwsIllegalArgumentException() throws Throwable {
        char[] original = context.allocConcatBuffer();
        char[] smaller = new char[original.length - 1];
        try {
            context.releaseConcatBuffer(smaller);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // releaseNameCopyBuffer with smaller char buffer throws IllegalArgumentException
    @Test
    public void testReleaseNameCopyBuffer_smallerBuffer_throwsIllegalArgumentException() throws Throwable {
        char[] original = context.allocNameCopyBuffer(64);
        char[] smaller = new char[original.length - 1];
        try {
            context.releaseNameCopyBuffer(smaller);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // _verifyAlloc(null) must not throw; state remains usable for subsequent allocation
    @Test
    public void test_verifyAlloc_nullBuffer_allowsSubsequentAllocation() throws Throwable {
        context._verifyAlloc(null);
        byte[] buf = context.allocReadIOBuffer();
        assertNotNull(buf);
    }

    // _verifyAlloc(nonNull) throws IllegalStateException (buffer already allocated)
    @Test
    public void test_verifyAlloc_nonNullBuffer_throwsIllegalStateException() throws Throwable {
        try {
            context._verifyAlloc(new Object());
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // _verifyRelease(byte[]): same reference never throws, regardless of length
    @Test
    public void test_verifyRelease_byteArraySameReference_doesNotThrow() throws Throwable {
        byte[] buf = new byte[5];
        context._verifyRelease(buf, buf);
        assertEquals(5, buf.length);
    }

    // BUG direct: per "only prevent shrinking", different ref with EQUAL length must not throw
    @Test
    public void test_verifyRelease_byteArraySameLengthDifferentReference_doesNotThrow() throws Throwable {
        byte[] src = new byte[10];
        byte[] toRelease = new byte[10];
        context._verifyRelease(toRelease, src);
        assertEquals(10, toRelease.length);
    }

    // _verifyRelease(byte[]): different ref with SMALLER length must throw
    @Test
    public void test_verifyRelease_byteArraySmaller_throwsIllegalArgumentException() throws Throwable {
        byte[] src = new byte[10];
        byte[] toRelease = new byte[9];
        try {
            context._verifyRelease(toRelease, src);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // BUG direct (char[] overload): different ref with EQUAL length must not throw
    @Test
    public void test_verifyRelease_charArraySameLengthDifferentReference_doesNotThrow() throws Throwable {
        char[] src = new char[8];
        char[] toRelease = new char[8];
        context._verifyRelease(toRelease, src);
        assertEquals(8, toRelease.length);
    }

    // _verifyRelease(char[]): different ref with SMALLER length must throw
    @Test
    public void test_verifyRelease_charArraySmaller_throwsIllegalArgumentException() throws Throwable {
        char[] src = new char[8];
        char[] toRelease = new char[7];
        try {
            context._verifyRelease(toRelease, src);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // _verifyRelease(char[]): same reference never throws
    @Test
    public void test_verifyRelease_charArraySameReference_doesNotThrow() throws Throwable {
        char[] buf = new char[6];
        context._verifyRelease(buf, buf);
        assertEquals(6, buf.length);
    }
}
