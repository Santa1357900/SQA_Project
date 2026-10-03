package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.zip.ZipException;

public class ZipArchiveEntryTest {

    @Test
    public void testConstructorsAndBasicGettersSetters() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("testName");
        assertEquals("testName", entry.getName());
        assertEquals(ZipArchiveEntry.PLATFORM_FAT, entry.getPlatform());

        entry.setMethod(ZipArchiveEntry.STORED);
        assertEquals(ZipArchiveEntry.STORED, entry.getMethod());
        assertTrue(entry.isSupportedCompressionMethod());

        entry.setMethod(ZipArchiveEntry.DEFLATED);
        assertTrue(entry.isSupportedCompressionMethod());

        entry.setMethod(99);
        assertFalse(entry.isSupportedCompressionMethod());

        entry.setInternalAttributes(123);
        assertEquals(123, entry.getInternalAttributes());

        entry.setExternalAttributes(456L);
        assertEquals(456L, entry.getExternalAttributes());

        ZipArchiveEntry defaultEntry = new ZipArchiveEntry();
        assertEquals("", defaultEntry.getName());

        java.util.zip.ZipEntry jdkEntry = new java.util.zip.ZipEntry("jdkEntry");
        jdkEntry.setMethod(ZipArchiveEntry.STORED);
        ZipArchiveEntry entryFromJdk = new ZipArchiveEntry(jdkEntry);
        assertEquals("jdkEntry", entryFromJdk.getName());
        assertEquals(ZipArchiveEntry.STORED, entryFromJdk.getMethod());

        ZipArchiveEntry copyEntry = new ZipArchiveEntry(entryFromJdk);
        assertEquals("jdkEntry", copyEntry.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeMethod() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("test");
        entry.setMethod(-1);
    }

    @Test
    public void testFileConstructor() throws Throwable {
        File tempFile = File.createTempFile("compress", ".tmp");
        tempFile.deleteOnExit();

        ZipArchiveEntry fileEntry = new ZipArchiveEntry(tempFile, "fileEntry");
        assertEquals("fileEntry", fileEntry.getName());

        ZipArchiveEntry dirEntry = new ZipArchiveEntry(tempFile, "dirEntry");
        // simulating directory by name ending with /
        ZipArchiveEntry slashDirEntry = new ZipArchiveEntry(tempFile, "dirEntry/");
        assertTrue(slashDirEntry.isDirectory());
    }

    @Test
    public void testUnixModeAndPlatform() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("unixTest/");
        entry.setUnixMode(0755);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
        assertEquals(0755, entry.getUnixMode());

        ZipArchiveEntry fatEntry = new ZipArchiveEntry("fatTest");
        assertEquals(0, fatEntry.getUnixMode());
    }

    @Test
    public void testExtraFieldsManagement() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("extraTest");
        assertEquals(0, entry.getExtraFields().length);
        assertNull(entry.getExtraField(new ZipShort(1)));

        AsiExtraField field1 = new AsiExtraField();
        field1.setMode(0755);

        AsiExtraField field2 = new AsiExtraField();
        field2.setMode(0644);

        ZipExtraField[] fields = new ZipExtraField[1];
        fields[0] = field1;

        entry.setExtraFields(fields);
        assertEquals(1, entry.getExtraFields().length);
        assertNotNull(entry.getExtraField(field1.getHeaderId()));

        entry.addExtraField(field2);
        assertEquals(2, entry.getExtraFields().length);

        entry.addAsFirstExtraField(field1);
        assertEquals(2, entry.getExtraFields().length);

        byte[] centralData = entry.getCentralDirectoryExtra();
        assertNotNull(centralData);

        byte[] localData = entry.getLocalFileDataExtra();
        assertNotNull(localData);

        entry.removeExtraField(field1.getHeaderId());
        assertEquals(1, entry.getExtraFields().length);

        entry.removeExtraField(field2.getHeaderId());
        assertEquals(0, entry.getExtraFields().length);
    }

    @Test
    public void testRemoveExtraFieldExceptions() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("emptyExtra");
        
        boolean threwNoSuchElement = false;
        try {
            entry.removeExtraField(new ZipShort(999));
        } catch (NoSuchElementException e) {
            threwNoSuchElement = true;
        }
        assertTrue(threwNoSuchElement);

        AsiExtraField field = new AsiExtraField();
        entry.addExtraField(field);

        boolean threwNoSuchElement2 = false;
        try {
            entry.removeExtraField(new ZipShort(999));
        } catch (NoSuchElementException e) {
            threwNoSuchElement2 = true;
        }
        assertTrue(threwNoSuchElement2);
    }

    @Test
    public void testExtraDataParsing() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("parseExtra");
        byte[] data = new byte[0];
        entry.setExtra(data);
        entry.setCentralDirectoryExtra(data);

        // invalid extra parse test expecting RuntimeException
        boolean threwRuntime = false;
        try {
            byte[] invalidData = new byte[] { 1, 2, 3 };
            entry.setExtra(invalidData);
        } catch (RuntimeException e) {
            threwRuntime = true;
        }
        assertTrue(threwRuntime);
    }

    @Test
    public void testCloneAndEqualsAndHashCode() throws Throwable {
        ZipArchiveEntry entry1 = new ZipArchiveEntry("entryName");
        entry1.setMethod(ZipArchiveEntry.STORED);
        entry1.setInternalAttributes(5);
        entry1.setExternalAttributes(10L);

        ZipArchiveEntry cloneEntry = (ZipArchiveEntry) entry1.clone();
        assertEquals(entry1.getName(), cloneEntry.getName());
        assertEquals(entry1.getMethod(), cloneEntry.getMethod());
        assertEquals(entry1.getInternalAttributes(), cloneEntry.getInternalAttributes());
        assertEquals(entry1.getExternalAttributes(), cloneEntry.getExternalAttributes());

        assertTrue(entry1.equals(entry1));
        assertFalse(entry1.equals(null));
        assertFalse(entry1.equals(new Object()));

        ZipArchiveEntry entry2 = new ZipArchiveEntry("entryName");
        assertTrue(entry1.equals(entry2));
        assertEquals(entry1.hashCode(), entry2.hashCode());

        ZipArchiveEntry entry3 = new ZipArchiveEntry("differentName");
        assertFalse(entry1.equals(entry3));

        ZipArchiveEntry entry4 = new ZipArchiveEntry((String) null);
        ZipArchiveEntry entry5 = new ZipArchiveEntry("notNull");
        assertFalse(entry4.equals(entry5));
        assertFalse(entry5.equals(entry4));
        assertTrue(entry4.equals(new ZipArchiveEntry((String) null)));
    }

    @Test
    public void testLastModifiedDate() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("dateTest");
        entry.setTime(1000L);
        Date date = entry.getLastModifiedDate();
        assertNotNull(date);
        assertEquals(1000L, date.getTime());
    }
}