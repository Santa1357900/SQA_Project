package org.apache.commons.compress.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;
import static org.junit.Assert.*;

public class IOUtilsClaudeTest {

    // Helper InputStream whose skip() always returns 0 even though read() works fine.
    // Per Javadoc, IOUtils.skip must fall back to read() in this situation.
    private static class ZeroSkipInputStream extends InputStream {
        private final byte[] data;
        private int pos = 0;
        ZeroSkipInputStream(byte[] data) {
            this.data = data;
        }
        public int read() throws IOException {
            if (pos >= data.length) {
                return -1;
            }
            return data[pos++] & 0xff;
        }
        public long skip(long n) throws IOException {
            return 0;
        }
    }

    // Helper InputStream that only skips up to 2 bytes per call, forcing multiple loop rounds.
    private static class PartialSkipInputStream extends InputStream {
        private long remaining;
        PartialSkipInputStream(long totalBytes) {
            this.remaining = totalBytes;
        }
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return 0;
        }
        public long skip(long n) throws IOException {
            long toSkip = Math.min(n, Math.min(2, remaining));
            remaining -= toSkip;
            return toSkip;
        }
    }

    // Helper InputStream whose read(byte[], off, len) only ever returns 1 byte, forcing readFully loop.
    private static class OneByteAtATimeInputStream extends InputStream {
        private final byte[] data;
        private int pos = 0;
        OneByteAtATimeInputStream(byte[] data) {
            this.data = data;
        }
        public int read() throws IOException {
            if (pos >= data.length) {
                return -1;
            }
            return data[pos++] & 0xff;
        }
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos >= data.length) {
                return -1;
            }
            b[off] = data[pos++];
            return 1;
        }
    }

    // Helper Closeable that tracks whether close() was invoked and can optionally throw.
    private static class TrackingCloseable implements Closeable {
        boolean closeCalled = false;
        boolean throwOnClose = false;
        public void close() throws IOException {
            closeCalled = true;
            if (throwOnClose) {
                throw new IOException("close failed");
            }
        }
    }

    // copy(InputStream, OutputStream): empty input -> loop runs 0 times, returns 0.
    @Test
    public void testCopy_defaultBuffer_emptyInput_returnsZeroAndEmptyOutput() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count = IOUtils.copy(input, output);
        assertEquals(0L, count);
        assertEquals(0, output.toByteArray().length);
    }

    // copy(InputStream, OutputStream): small data, loop runs once, returns correct count and data.
    @Test
    public void testCopy_defaultBuffer_smallData_returnsCorrectCountAndData() throws Throwable {
        byte[] data = "Hello World".getBytes("UTF-8");
        InputStream input = new ByteArrayInputStream(data);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count = IOUtils.copy(input, output);
        assertEquals(11L, count);
        assertArrayEquals(data, output.toByteArray());
    }

    // copy(InputStream, OutputStream): data larger than default buffer (8024) forces loop multiple rounds.
    @Test
    public void testCopy_defaultBuffer_dataLargerThanBufferSize_loopsMultipleTimes() throws Throwable {
        byte[] data = new byte[20000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        InputStream input = new ByteArrayInputStream(data);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count = IOUtils.copy(input, output);
        assertEquals(20000L, count);
        assertArrayEquals(data, output.toByteArray());
    }

    // copy(InputStream, OutputStream): null input causes NullPointerException on read.
    @Test
    public void testCopy_nullInput_throwsNullPointerException() throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            IOUtils.copy(null, output);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // copy(InputStream, OutputStream, buffersize=1): forces byte-by-byte reads, multiple loop rounds.
    @Test
    public void testCopy_customBufferSizeOne_readsByteByByteCorrectly() throws Throwable {
        byte[] data = "Hello".getBytes("UTF-8");
        InputStream input = new ByteArrayInputStream(data);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count = IOUtils.copy(input, output, 1);
        assertEquals(5L, count);
        assertArrayEquals(data, output.toByteArray());
    }

    // copy(InputStream, OutputStream, buffersize=data.length): single read loop then EOF.
    @Test
    public void testCopy_customBufferSizeEqualsDataLength_singleLoopIteration() throws Throwable {
        byte[] data = "World".getBytes("UTF-8");
        InputStream input = new ByteArrayInputStream(data);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count = IOUtils.copy(input, output, data.length);
        assertEquals(5L, count);
        assertArrayEquals(data, output.toByteArray());
    }

    // skip: numToSkip == 0 means the while loop body never executes.
    @Test
    public void testSkip_zeroNumToSkip_returnsZero() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        long skipped = IOUtils.skip(input, 0);
        assertEquals(0L, skipped);
    }

    // skip: negative numToSkip means while(numToSkip > 0) is false from the start.
    @Test
    public void testSkip_negativeNumToSkip_returnsZero() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        long skipped = IOUtils.skip(input, -5);
        assertEquals(0L, skipped);
    }

    // skip: normal stream supporting skip() fully satisfies the request in one iteration.
    @Test
    public void testSkip_normalSkipSupported_returnsRequestedAmount() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        long skipped = IOUtils.skip(input, 3);
        assertEquals(3L, skipped);
    }

    // skip: requesting more than available data legitimately returns less (end of stream reached).
    @Test
    public void testSkip_requestMoreThanAvailable_returnsActualAvailable() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        long skipped = IOUtils.skip(input, 10);
        assertEquals(5L, skipped);
    }

    // skip: per Javadoc, when skip() returns 0 but data remains, implementation must fall back to read().
    @Test
    public void testSkip_skipReturnsZeroWithDataAvailable_fallsBackAndSkipsAll() throws Throwable {
        InputStream input = new ZeroSkipInputStream(new byte[] {1, 2, 3, 4, 5});
        long skipped = IOUtils.skip(input, 5);
        assertEquals(5L, skipped);
    }

    // skip: stream returning small partial skip amounts repeatedly forces several loop iterations.
    @Test
    public void testSkip_partialSkipsAcrossMultipleIterations_sumsToRequested() throws Throwable {
        InputStream input = new PartialSkipInputStream(10);
        long skipped = IOUtils.skip(input, 10);
        assertEquals(10L, skipped);
    }

    // readFully(b): array shorter than stream data fills the array completely.
    @Test
    public void testReadFully_arrayShorterThanStream_fillsCompletely() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {10, 20, 30, 40, 50});
        byte[] b = new byte[3];
        int read = IOUtils.readFully(input, b);
        assertEquals(3, read);
        assertArrayEquals(new byte[] {10, 20, 30}, b);
    }

    // readFully(b): stream shorter than array only reads the available bytes (end of stream).
    @Test
    public void testReadFully_streamShorterThanArray_returnsPartialCount() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {10, 20, 30});
        byte[] b = new byte[5];
        int read = IOUtils.readFully(input, b);
        assertEquals(3, read);
        assertEquals((byte) 10, b[0]);
        assertEquals((byte) 20, b[1]);
        assertEquals((byte) 30, b[2]);
    }

    // readFully(b): zero-length array means while(count != len) is false immediately, returns 0.
    @Test
    public void testReadFully_emptyArray_returnsZero() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        byte[] b = new byte[0];
        int read = IOUtils.readFully(input, b);
        assertEquals(0, read);
    }

    // readFully(b): stream returning only 1 byte per call forces multiple while-loop rounds.
    @Test
    public void testReadFully_multipleReadCallsNeeded_fillsArrayAcrossIterations() throws Throwable {
        InputStream input = new OneByteAtATimeInputStream(new byte[] {7, 8, 9, 10, 11});
        byte[] b = new byte[5];
        int read = IOUtils.readFully(input, b);
        assertEquals(5, read);
        assertArrayEquals(new byte[] {7, 8, 9, 10, 11}, b);
    }

    // readFully(b, offset, len): negative len must throw IndexOutOfBoundsException.
    @Test
    public void testReadFully_negativeLen_throwsIndexOutOfBoundsException() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        byte[] b = new byte[5];
        try {
            IOUtils.readFully(input, b, 0, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // readFully(b, offset, len): negative offset must throw IndexOutOfBoundsException.
    @Test
    public void testReadFully_negativeOffset_throwsIndexOutOfBoundsException() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        byte[] b = new byte[5];
        try {
            IOUtils.readFully(input, b, -1, 2);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // readFully(b, offset, len): len + offset exceeding array length must throw IndexOutOfBoundsException.
    @Test
    public void testReadFully_lenPlusOffsetExceedsArrayLength_throwsIndexOutOfBoundsException() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        byte[] b = new byte[5];
        try {
            IOUtils.readFully(input, b, 3, 3);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // readFully(b, offset, len): boundary where len + offset == b.length must NOT throw.
    @Test
    public void testReadFully_lenPlusOffsetEqualsArrayLength_boundaryNoException() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {88, 89, 90});
        byte[] b = new byte[5];
        int read = IOUtils.readFully(input, b, 2, 3);
        assertEquals(3, read);
        assertEquals((byte) 88, b[2]);
        assertEquals((byte) 89, b[3]);
        assertEquals((byte) 90, b[4]);
    }

    // readFully(b, offset, len): len == 0 means while(count != len) false immediately, returns 0.
    @Test
    public void testReadFully_zeroLen_returnsZeroImmediately() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        byte[] b = new byte[3];
        int read = IOUtils.readFully(input, b, 0, 0);
        assertEquals(0, read);
    }

    // readFully(b, offset, len): valid mid-array offset reads expected bytes at expected positions.
    @Test
    public void testReadFully_validOffsetMidArray_readsCorrectBytesAtOffset() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[] {5, 6});
        byte[] b = new byte[4];
        int read = IOUtils.readFully(input, b, 1, 2);
        assertEquals(2, read);
        assertEquals((byte) 5, b[1]);
        assertEquals((byte) 6, b[2]);
    }

    // toByteArray: empty stream yields an empty byte array.
    @Test
    public void testToByteArray_emptyStream_returnsEmptyArray() throws Throwable {
        InputStream input = new ByteArrayInputStream(new byte[0]);
        byte[] result = IOUtils.toByteArray(input);
        assertEquals(0, result.length);
    }

    // toByteArray: non-empty stream content is fully and exactly copied into the array.
    @Test
    public void testToByteArray_nonEmptyStream_returnsExactBytes() throws Throwable {
        byte[] data = "abc123".getBytes("UTF-8");
        InputStream input = new ByteArrayInputStream(data);
        byte[] result = IOUtils.toByteArray(input);
        assertArrayEquals(data, result);
    }

    // toByteArray: null input propagates NullPointerException from the underlying copy/read.
    @Test
    public void testToByteArray_nullInput_throwsNullPointerException() throws Throwable {
        try {
            IOUtils.toByteArray(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // closeQuietly: null Closeable must not throw any exception.
    @Test
    public void testCloseQuietly_nullCloseable_doesNotThrowException() throws Throwable {
        boolean noException = true;
        try {
            IOUtils.closeQuietly(null);
        } catch (Throwable t) {
            noException = false;
        }
        assertTrue(noException);
    }

    // closeQuietly: close() throwing IOException must be swallowed while still invoking close().
    @Test
    public void testCloseQuietly_closeableThrowsIOException_exceptionSwallowedButCloseInvoked() throws Throwable {
        TrackingCloseable c = new TrackingCloseable();
        c.throwOnClose = true;
        boolean noException = true;
        try {
            IOUtils.closeQuietly(c);
        } catch (Throwable t) {
            noException = false;
        }
        assertTrue(noException);
        assertTrue(c.closeCalled);
    }

    // closeQuietly: normal Closeable has close() invoked successfully.
    @Test
    public void testCloseQuietly_normalCloseable_closeInvokedSuccessfully() throws Throwable {
        TrackingCloseable c = new TrackingCloseable();
        IOUtils.closeQuietly(c);
        assertTrue(c.closeCalled);
    }
}
