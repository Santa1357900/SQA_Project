package com.fasterxml.jackson.core.io;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.TextBuffer;

public class IOContextTest {

    @Test
    public void testLifeCycleAndAccessors() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        Object sourceRef = "testSource";
        boolean managedResource = true;

        IOContext context = new IOContext(br, sourceRef, managedResource);

        assertSame(sourceRef, context.getSourceReference());
        assertTrue(context.isResourceManaged());
        assertNull(context.getEncoding());

        context.setEncoding(JsonEncoding.UTF8);
        assertEquals(JsonEncoding.UTF8, context.getEncoding());

        IOContext chained = context.withEncoding(JsonEncoding.UTF16_BE);
        assertSame(context, chained);
        assertEquals(JsonEncoding.UTF16_BE, context.getEncoding());

        TextBuffer tb = context.constructTextBuffer();
        assertNotNull(tb);
    }

    @Test
    public void testReadIOBufferAllocAndRelease() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        byte[] buf = context.allocReadIOBuffer();
        assertNotNull(buf);

        // Second allocation should throw IllegalStateException
        try {
            context.allocReadIOBuffer();
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Trying to call same allocXxx() method second time"));
        }

        // Release with null should be safe
        context.releaseReadIOBuffer(null);

        // Release valid buffer
        context.releaseReadIOBuffer(buf);

        // Can allocate again after release
        byte[] buf2 = context.allocReadIOBuffer();
        assertNotNull(buf2);
        context.releaseReadIOBuffer(buf2);
    }

    @Test
    public void testReadIOBufferWithMinSize() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        byte[] buf = context.allocReadIOBuffer(100);
        assertNotNull(buf);
        assertTrue(buf.length >= 100);

        context.releaseReadIOBuffer(buf);
    }

    @Test
    public void testWriteEncodingBufferAllocAndRelease() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        byte[] buf = context.allocWriteEncodingBuffer();
        assertNotNull(buf);

        try {
            context.allocWriteEncodingBuffer(50);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Trying to call same allocXxx() method second time"));
        }

        context.releaseWriteEncodingBuffer(buf);
    }

    @Test
    public void testBase64BufferAllocAndRelease() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        byte[] buf = context.allocBase64Buffer();
        assertNotNull(buf);

        try {
            context.allocBase64Buffer();
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Trying to call same allocXxx() method second time"));
        }

        context.releaseBase64Buffer(buf);
    }

    @Test
    public void testTokenBufferAllocAndRelease() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        char[] buf = context.allocTokenBuffer();
        assertNotNull(buf);

        try {
            context.allocTokenBuffer(20);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Trying to call same allocXxx() method second time"));
        }

        context.releaseTokenBuffer(buf);
    }

    @Test
    public void testConcatBufferAllocAndRelease() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        char[] buf = context.allocConcatBuffer();
        assertNotNull(buf);

        try {
            context.allocConcatBuffer();
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Trying to call same allocXxx() method second time"));
        }

        context.releaseConcatBuffer(buf);
    }

    @Test
    public void testNameCopyBufferAllocAndRelease() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        char[] buf = context.allocNameCopyBuffer(15);
        assertNotNull(buf);

        try {
            context.allocNameCopyBuffer(10);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Trying to call same allocXxx() method second time"));
        }

        context.releaseNameCopyBuffer(buf);
    }

    @Test
    public void testVerifyReleaseByteArray() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        byte[] buf = context.allocReadIOBuffer();
        
        // Trying to release a different buffer that is smaller or equal in length should throw IllegalArgumentException
        byte[] smallerBuf = new byte[buf.length - 1];
        try {
            context.releaseReadIOBuffer(smallerBuf);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Trying to release buffer not owned by the context"));
        }

        // A larger buffer should be allowed (upgrade scenario)
        byte[] largerBuf = new byte[buf.length + 10];
        context.releaseReadIOBuffer(largerBuf);
    }

    @Test
    public void testVerifyReleaseCharArray() throws Throwable {
        BufferRecycler br = new BufferRecycler();
        IOContext context = new IOContext(br, null, false);

        char[] buf = context.allocConcatBuffer();
        
        // Trying to release a different buffer that is smaller or equal in length should throw IllegalArgumentException
        char[] smallerBuf = new char[buf.length - 1];
        try {
            context.releaseConcatBuffer(smallerBuf);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Trying to release buffer not owned by the context"));
        }

        // A larger buffer should be allowed (upgrade scenario)
        char[] largerBuf = new char[buf.length + 5];
        context.releaseConcatBuffer(largerBuf);
    }
}