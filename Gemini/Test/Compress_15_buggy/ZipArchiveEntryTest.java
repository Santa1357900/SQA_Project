package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.zip.ZipException;

public class ZipArchiveEntryTest {

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        ZipArchiveEntry entry1 = new ZipArchiveEntry("test.txt");
        assertEquals("test.txt", entry1.getName());
        assertFalse(entry1.isDirectory());

        ZipArchiveEntry entry2 = new ZipArchiveEntry("testDir/");
        assertEquals("testDir/", entry2.getName());
        assertTrue(entry2.isDirectory());

        ZipArchiveEntry entry3 = new ZipArchiveEntry();
        assertEquals("", entry3.getName());

        java.util.zip.ZipEntry jdkEntry = new java.util.zip.ZipEntry("jdkEntry.txt");
        jdkEntry.setMethod(ZipArchiveEntry.DEFLATED);
        jdkEntry.setSize(100L);
        ZipArchiveEntry entry4 = new ZipArchiveEntry(jdkEntry);
        assertEquals("jdkEntry.txt", entry4.getName());
        assertEquals(ZipArchiveEntry.DEFLATED, entry4.getMethod());
        assertEquals(100L, entry4.getSize());

        ZipArchiveEntry entry5 = new ZipArchiveEntry(entry4);
        assertEquals("jdkEntry.txt", entry5.getName());
        assertEquals(ZipArchiveEntry.DEFLATED, entry5.getMethod());
        assertEquals(100L, entry5.getSize());
    }

    @Test
    public void testFileConstructor() throws Throwable {
        File tempFile = File.createTempFile("compress-test", ".tmp");
        tempFile.deleteOnExit();

        ZipArchiveEntry entry1 = new ZipArchiveEntry(tempFile, "fileEntry");
        assertEquals("fileEntry", entry1.getName());
        assertEquals(tempFile.length(), entry1.getSize());

        ZipArchiveEntry entry2 = new ZipArchiveEntry(tempFile, "dirEntry/");
        assertEquals("dirEntry/", entry2.getName());

        File tempDir = tempFile.getParentFile();
        ZipArchiveEntry entry3 = new ZipArchiveEntry(tempDir, "dirWithoutSlash");
        assertEquals("dirWithoutSlash/", entry3.getName());
    }

    @Test
    public void testClone() throws Throwable {
        ZipArchiveEntry original = new ZipArchiveEntry("original.txt");
        original.setInternalAttributes(5);
        original.setExternalAttributes(10L);
        original.setMethod(ZipArchiveEntry.STORED);
        original.setSize(50L);

        ZipArchiveEntry clone = (ZipArchiveEntry) original.clone();
        assertEquals(original.getName(), clone.getName());
        assertEquals(original.getInternalAttributes(), clone.getInternalAttributes());
        assertEquals(original.getExternalAttributes(), clone.getExternalAttributes());
        assertEquals(original.getMethod(), clone.getMethod());
        assertEquals(original.getSize(), clone.getSize());
    }

    @Test
    public void testMethodValidation() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("method.txt");
        entry.setMethod(ZipArchiveEntry.STORED);
        assertEquals(ZipArchiveEntry.STORED, entry.getMethod());

        boolean exceptionThrown = false;
        try {
            entry.setMethod(-2);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("negative"));
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testAttributesAndPlatform() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("attrs.txt");
        entry.setInternalAttributes(123);
        assertEquals(123, entry.getInternalAttributes());

        entry.setExternalAttributes(456L);
        assertEquals(456L, entry.getExternalAttributes());

        assertEquals(ZipArchiveEntry.PLATFORM_FAT, entry.getPlatform());

        entry.setUnixMode(0644);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
        assertEquals(0644, entry.getUnixMode());
    }

    @Test
    public void testUnixModeOnNonUnixPlatform() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("fat.txt");
        entry.setPlatform(ZipArchiveEntry.PLATFORM_FAT);
        assertEquals(0, entry.getUnixMode());
    }

    @Test
    public void testSizeValidation() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("size.txt");
        entry.setSize(1024L);
        assertEquals(1024L, entry.getSize());

        boolean exceptionThrown = false;
        try {
            entry.setSize(-1L);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("invalid entry size"));
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testNameAndRawName() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("initialName");
        byte[] raw = new byte[] { (byte)'r', (byte)'a', (byte)'w' };
        entry.setName("newName", raw);
        assertEquals("newName", entry.getName());
        assertArrayEquals(raw, entry.getRawName());

        ZipArchiveEntry entryWithoutRaw = new ZipArchiveEntry("plain.txt");
        assertNull(entryWithoutRaw.getRawName());
    }

    @Test
    public void testNameFatWindowsSlashAdjustment() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("foo\\bar");
        assertEquals("foo/bar", entry.getName());
    }

    @Test
    public void testExtraFieldsOperations() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("extra.txt");
        
        AsiExtraField asi = new AsiExtraField();
        asi.setUserId(1000);

        entry.addExtraField(asi);
        ZipExtraField[] fields = entry.getExtraFields();
        assertEquals(1, fields.length);
        assertEquals(asi.getHeaderId(), fields[0].getHeaderId());

        assertNotNull(entry.getExtraField(asi.getHeaderId()));
        assertNull(entry.getExtraField(new ZipShort(9999)));

        entry.addAsFirstExtraField(asi);
        assertEquals(1, entry.getExtraFields().length);

        UnparseableExtraFieldData unparseable = new UnparseableExtraFieldData();
        unparseable.parseFromLocalFileData(new byte[] { 1, 2, 3 }, 0, 3);
        entry.addExtraField(unparseable);
        
        assertSame(unparseable, entry.getUnparseableExtraFieldData());
        assertEquals(2, entry.getExtraFields(true).length);
        assertEquals(1, entry.getExtraFields(false).length);

        entry.removeExtraField(asi.getHeaderId());
        assertNull(entry.getExtraField(asi.getHeaderId()));

        entry.removeUnparseableExtraFieldData();
        assertNull(entry.getUnparseableExtraFieldData());
    }

    @Test
    public void testExtraFieldExceptions() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("exceptions.txt");

        boolean ex1 = false;
        try {
            entry.removeExtraField(new ZipShort(1234));
        } catch (NoSuchElementException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            entry.removeUnparseableExtraFieldData();
        } catch (NoSuchElementException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testSetExtraAndCentralDirectoryExtra() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("bytes.txt");
        byte[] extraBytes = new byte[] { 1, 0, 2, 0, 5, 6 };
        entry.setExtra(extraBytes);
        assertNotNull(entry.getLocalFileDataExtra());

        entry.setCentralDirectoryExtra(extraBytes);
        assertNotNull(entry.getCentralDirectoryExtra());

        boolean ex = false;
        try {
            entry.setCentralDirectoryExtra(new byte[] { 1, 0 });
        } catch (RuntimeException e) {
            ex = true;
        }
        assertTrue(ex);
    }

    @Test
    public void testGeneralPurposeBit() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("gpb.txt");
        GeneralPurposeBit gpb = new GeneralPurposeBit();
        gpb.useUTF8ForNames(true);
        entry.setGeneralPurposeBit(gpb);
        assertSame(gpb, entry.getGeneralPurposeBit());
    }

    @Test
    public void testLastModifiedDateAndHashCode() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("time.txt");
        entry.setTime(100000L);
        Date date = entry.getLastModifiedDate();
        assertEquals(new Date(100000L), date);

        int hash = entry.hashCode();
        assertEquals("time.txt".hashCode(), hash);
    }

    @Test
    public void testEqualsAndHashCodeContract() throws Throwable {
        ZipArchiveEntry entry1 = new ZipArchiveEntry("same.txt");
        ZipArchiveEntry entry2 = new ZipArchiveEntry("same.txt");
        ZipArchiveEntry entry3 = new ZipArchiveEntry("different.txt");

        assertTrue(entry1.equals(entry1));
        assertTrue(entry1.equals(entry2));
        assertFalse(entry1.equals(null));
        assertFalse(entry1.equals("NotAZipEntry"));
        assertFalse(entry1.equals(entry3));

        entry2.setComment("comment");
        assertFalse(entry1.equals(entry2));

        ZipArchiveEntry entry4 = new ZipArchiveEntry("same.txt");
        entry4.setComment("comment");
        ZipArchiveEntry entry5 = new ZipArchiveEntry("same.txt");
        entry5.setComment("differentComment");
        assertFalse(entry4.equals(entry5));

        ZipArchiveEntry entry6 = new ZipArchiveEntry((String) null);
        ZipArchiveEntry entry7 = new ZipArchiveEntry("notNull.txt");
        assertFalse(entry6.equals(entry7));
        assertFalse(entry7.equals(entry6));
        assertTrue(entry6.equals(new ZipArchiveEntry((String) null)));
    }
}