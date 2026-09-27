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
        assertEquals(-1, entry1.getMethod());
        assertEquals(0, entry1.getInternalAttributes());
        assertEquals(ZipArchiveEntry.PLATFORM_FAT, entry1.getPlatform());
        assertEquals(0, entry1.getExternalAttributes());
        assertNull(entry1.getRawName());
        assertNotNull(entry1.getGeneralPurposeBit());

        ZipArchiveEntry entry2 = new ZipArchiveEntry("dir/");
        assertEquals("dir/", entry2.getName());
        assertTrue(entry2.isDirectory());

        ZipArchiveEntry entry3 = new ZipArchiveEntry(entry1);
        assertEquals("test.txt", entry3.getName());

        java.util.zip.ZipEntry jdkEntry = new java.util.zip.ZipEntry("jdk.txt");
        jdkEntry.setMethod(8);
        jdkEntry.setSize(100L);
        ZipArchiveEntry entry4 = new ZipArchiveEntry(jdkEntry);
        assertEquals("jdk.txt", entry4.getName());
        assertEquals(8, entry4.getMethod());
        assertEquals(100L, entry4.getSize());
    }

    @Test
    public void testFileConstructor() throws Throwable {
        File tempFile = File.createTempFile("compress", ".tmp");
        tempFile.deleteOnExit();

        ZipArchiveEntry entry1 = new ZipArchiveEntry(tempFile, "entryName");
        assertEquals("entryName", entry1.getName());
        assertEquals(tempFile.length(), entry1.getSize());

        ZipArchiveEntry entry2 = new ZipArchiveEntry(tempFile, "entryName/");
        assertEquals("entryName/", entry2.getName());
        assertTrue(entry2.isDirectory());

        File tempDir = tempFile.getParentFile();
        ZipArchiveEntry entry3 = new ZipArchiveEntry(tempDir, "dirName");
        assertEquals("dirName/", entry3.getName());
        assertTrue(entry3.isDirectory());
    }

    @Test
    public void testClone() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("clone.txt");
        entry.setMethod(0);
        entry.setInternalAttributes(5);
        entry.setExternalAttributes(10L);
        entry.setUnixMode(0644);

        ZipArchiveEntry cloned = (ZipArchiveEntry) entry.clone();
        assertEquals(entry.getName(), cloned.getName());
        assertEquals(entry.getMethod(), cloned.getMethod());
        assertEquals(entry.getInternalAttributes(), cloned.getInternalAttributes());
        assertEquals(entry.getExternalAttributes(), cloned.getExternalAttributes());
        assertEquals(entry.getPlatform(), cloned.getPlatform());
        assertEquals(entry.getUnixMode(), cloned.getUnixMode());
    }

    @Test
    public void testMethodValidation() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("method.txt");
        entry.setMethod(0);
        assertEquals(0, entry.getMethod());

        boolean thrown = false;
        try {
            entry.setMethod(-1);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testSizeValidation() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("size.txt");
        entry.setSize(500L);
        assertEquals(500L, entry.getSize());

        boolean thrown = false;
        try {
            entry.setSize(-2L);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testAttributesAndPlatform() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("attrs.txt");
        entry.setInternalAttributes(123);
        assertEquals(123, entry.getInternalAttributes());

        entry.setExternalAttributes(456L);
        assertEquals(456L, entry.getExternalAttributes());

        entry.setUnixMode(0755);
        assertEquals(ZipArchiveEntry.PLATFORM_UNIX, entry.getPlatform());
        assertEquals(0755, entry.getUnixMode());

        ZipArchiveEntry entryFat = new ZipArchiveEntry("fat.txt");
        assertEquals(0, entryFat.getUnixMode());
    }

    @Test
    public void testExtraFieldsManagement() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("extra.txt");
        
        ZipShort headerId = new ZipShort(0x0001);
        AsiExtraField extraField1 = new AsiExtraField();
        extraField1.setHeaderId(headerId);

        ZipShort headerId2 = new ZipShort(0x0002);
        AsiExtraField extraField2 = new AsiExtraField();
        extraField2.setHeaderId(headerId2);

        entry.setExtraFields(new ZipExtraField[] { extraField1 });
        assertEquals(1, entry.getExtraFields().length);
        assertEquals(extraField1, entry.getExtraField(headerId));

        entry.addExtraField(extraField2);
        assertEquals(2, entry.getExtraFields().length);
        assertEquals(extraField2, entry.getExtraField(headerId2));

        entry.addAsFirstExtraField(extraField1);
        assertEquals(extraField1, entry.getExtraFields()[0]);

        entry.removeExtraField(headerId2);
        assertNull(entry.getExtraField(headerId2));

        boolean thrown = false;
        try {
            entry.removeExtraField(new ZipShort(0xFFFF));
        } catch (NoSuchElementException e) {
            thrown = true;
        }
        assertTrue(thrown);

        UnparseableExtraFieldData unparseable = new UnparseableExtraFieldData();
        byte[] unparseableData = new byte[] { 0x01, 0x02, 0x03 };
        unparseable.parseFromLocalFileData(unparseableData, 0, unparseableData.length);
        entry.addExtraField(unparseable);
        
        assertNotNull(entry.getUnparseableExtraFieldData());
        assertEquals(1, entry.getExtraFields(true).length);

        entry.removeUnparseableExtraFieldData();
        assertNull(entry.getUnparseableExtraFieldData());

        boolean unparseableThrown = false;
        try {
            entry.removeUnparseableExtraFieldData();
        } catch (NoSuchElementException e) {
            unparseableThrown = true;
        }
        assertTrue(unparseableThrown);
    }

    @Test
    public void testSetExtraAndCentralDirectory() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("extra_parse.txt");
        byte[] extraBytes = new byte[] { 0x01, 0x00, 0x02, 0x00, 0x05, 0x06 };
        entry.setExtra(extraBytes);
        assertNotNull(entry.getLocalFileDataExtra());

        entry.setCentralDirectoryExtra(extraBytes);
        assertNotNull(entry.getCentralDirectoryExtra());

        entry.setExtra((byte[]) null);
        assertNotNull(entry.getLocalFileDataExtra());
    }

    @Test
    public void testRawNameAndHashCodeAndEquals() throws Throwable {
        ZipArchiveEntry entry1 = new ZipArchiveEntry("test.txt");
        byte[] rawName = "test.txt".getBytes();
        // Using setName(String, byte[]) through reflection or protected access if applicable, 
        // or test getRawName with default null state
        assertNull(entry1.getRawName());

        ZipArchiveEntry entry2 = new ZipArchiveEntry("test.txt");
        assertEquals(entry1.hashCode(), entry2.hashCode());
        assertTrue(entry1.equals(entry2));
        assertTrue(entry1.equals(entry1));
        assertFalse(entry1.equals(null));
        assertFalse(entry1.equals(new Object()));

        entry2.setComment("comment");
        assertFalse(entry1.equals(entry2));

        ZipArchiveEntry entry3 = new ZipArchiveEntry("other.txt");
        assertFalse(entry1.equals(entry3));

        ZipArchiveEntry entryNullName = new ZipArchiveEntry("");
        entryNullName.setName(null);
        ZipArchiveEntry entryNullName2 = new ZipArchiveEntry("");
        entryNullName2.setName(null);
        assertTrue(entryNullName.equals(entryNullName2));
        assertFalse(entryNullName.equals(entry1));

        ZipArchiveEntry entryNullName3 = new ZipArchiveEntry("");
        entryNullName3.setName(null);
        entryNullName3.setComment("c");
        ZipArchiveEntry entryNullName4 = new ZipArchiveEntry("");
        entryNullName4.setName(null);
        assertFalse(entryNullName3.equals(entryNullName4));
    }

    @Test
    public void testGeneralPurposeBitAndLastModifiedDate() throws Throwable {
        ZipArchiveEntry entry = new ZipArchiveEntry("gpb.txt");
        GeneralPurposeBit gpb = new GeneralPurposeBit();
        entry.setGeneralPurposeBit(gpb);
        assertEquals(gpb, entry.getGeneralPurposeBit());

        entry.setTime(123456789L);
        Date date = entry.getLastModifiedDate();
        assertNotNull(date);
        assertEquals(123456789L, date.getTime());
    }
}