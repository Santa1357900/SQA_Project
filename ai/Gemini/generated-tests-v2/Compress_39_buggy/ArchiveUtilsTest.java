package org.apache.commons.compress.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Test;

public class ArchiveUtilsTest {

    private static class DummyArchiveEntry implements ArchiveEntry {
        private final String name;
        private final long size;
        private final boolean isDirectory;

        public DummyArchiveEntry(String name, long size, boolean isDirectory) {
            this.name = name;
            this.size = size;
            this.isDirectory = isDirectory;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public long getLastModifiedDate() {
            return 0L;
        }
    }

    @Test
    public void testToStringFile() throws Throwable {
        ArchiveEntry entry = new DummyArchiveEntry("main.c", 2000L, false);
        String result = ArchiveUtils.toString(entry);
        assertEquals("-    2000 main.c", result);
    }

    @Test
    public void testToStringDirectory() throws Throwable {
        ArchiveEntry entry = new DummyArchiveEntry("testfiles", 100L, true);
        String result = ArchiveUtils.toString(entry);
        assertEquals("d     100 testfiles", result);
    }

    @Test
    public void testToStringLargeSize() throws Throwable {
        ArchiveEntry entry = new DummyArchiveEntry("big.bin", 123456789L, false);
        String result = ArchiveUtils.toString(entry);
        assertEquals("- 123456789 big.bin", result);
    }

    @Test
    public void testMatchAsciiBuffer() throws Throwable {
        byte[] buffer = "hello world".getBytes("US-ASCII");
        assertTrue(ArchiveUtils.matchAsciiBuffer("hello", buffer, 0, 5));
        assertFalse(ArchiveUtils.matchAsciiBuffer("world", buffer, 0, 5));
        assertTrue(ArchiveUtils.matchAsciiBuffer("hello world", buffer));
        assertFalse(ArchiveUtils.matchAsciiBuffer("hello world!", buffer));
    }

    @Test
    public void testToAsciiBytesAndString() throws Throwable {
        String testStr = "Apache Commons Compress";
        byte[] bytes = ArchiveUtils.toAsciiBytes(testStr);
        assertNotNull(bytes);

        String convertedStr = ArchiveUtils.toAsciiString(bytes);
        assertEquals(testStr, convertedStr);

        String partialStr = ArchiveUtils.toAsciiString(bytes, 7, 7);
        assertEquals("Commons", partialStr);
    }

    @Test
    public void testIsEqualBuffers() throws Throwable {
        byte[] buf1 = new byte[] { 1, 2, 3, 4, 5 };
        byte[] buf2 = new byte[] { 1, 2, 3, 4, 5 };
        byte[] buf3 = new byte[] { 1, 2, 3, 4, 6 };
        byte[] buf4 = new byte[] { 1, 2, 3 };

        assertTrue(ArchiveUtils.isEqual(buf1, buf2));
        assertTrue(ArchiveUtils.isEqual(buf1, 0, 5, buf2, 0, 5));
        assertFalse(ArchiveUtils.isEqual(buf1, 0, 5, buf3, 0, 5));
        assertFalse(ArchiveUtils.isEqual(buf1, 0, 5, buf4, 0, 3));
    }

    @Test
    public void testIsEqualWithNulls() throws Throwable {
        byte[] buf1 = new byte[] { 1, 2, 3, 0, 0 };
        byte[] buf2 = new byte[] { 1, 2, 3 };
        byte[] buf3 = new byte[] { 1, 2, 4, 0, 0 };

        assertTrue(ArchiveUtils.isEqual(buf1, 0, 5, buf2, 0, 3, true));
        assertTrue(ArchiveUtils.isEqual(buf2, 0, 3, buf1, 0, 5, true));
        assertTrue(ArchiveUtils.isEqual(buf1, buf2, true));
        assertTrue(ArchiveUtils.isEqualWithNull(buf1, 0, 5, buf2, 0, 3));

        assertFalse(ArchiveUtils.isEqual(buf3, 0, 5, buf2, 0, 3, true));
        assertFalse(ArchiveUtils.isEqual(buf1, 0, 5, buf3, 0, 5, true));
    }

    @Test
    public void testIsArrayZero() throws Throwable {
        byte[] zeros = new byte[] { 0, 0, 0, 0, 5 };
        assertTrue(ArchiveUtils.isArrayZero(zeros, 4));
        assertFalse(ArchiveUtils.isArrayZero(zeros, 5));
    }

    @Test
    public void testSanitize() throws Throwable {
        String normal = "file_name.txt";
        assertEquals("file_name.txt", ArchiveUtils.sanitize(normal));

        String withControl = "file\nname\r.txt";
        assertEquals("file?name?.txt", ArchiveUtils.sanitize(withControl));
    }
}