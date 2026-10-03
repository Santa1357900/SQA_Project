package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TarArchiveEntryTest {

    @Test
    public void testDefaultConstructorAndGetters() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("testName");
        assertEquals("testName", entry.getName());
        assertFalse(entry.isDirectory());
        assertTrue(entry.isFile());
        assertNull(entry.getFile());
        assertEquals("", entry.getLinkName());
        assertEquals(0, entry.getLongUserId());
        assertEquals(0, entry.getLongGroupId());
        assertNotNull(entry.getUserName());
        assertEquals("", entry.getGroupName());
        assertEquals(0, entry.getDevMajor());
        assertEquals(0, entry.getDevMinor());
        assertFalse(entry.isExtended());
        assertEquals(0L, entry.getRealSize());
        assertFalse(entry.isGNUSparse());
        assertFalse(entry.isOldGNUSparse());
        assertFalse(entry.isPaxGNUSparse());
        assertFalse(entry.isStarSparse());
        assertFalse(entry.isGNULongLinkEntry());
        assertFalse(entry.isGNULongNameEntry());
        assertFalse(entry.isPaxHeader());
        assertFalse(entry.isGlobalPaxHeader());
        assertFalse(entry.isSymbolicLink());
        assertFalse(entry.isLink());
        assertFalse(entry.isCharacterDevice());
        assertFalse(entry.isBlockDevice());
        assertFalse(entry.isFIFO());
        assertFalse(entry.isSparse());
        assertNotNull(entry.getModTime());
        assertNotNull(entry.getLastModifiedDate());
        assertFalse(entry.isCheckSumOK());
    }

    @Test
    public void testDirectoryEntryByName() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("someDir/", true);
        assertEquals("someDir/", entry.getName());
        assertTrue(entry.isDirectory());
        assertFalse(entry.isFile());
        assertEquals(TarArchiveEntry.DEFAULT_DIR_MODE, entry.getMode());
    }

    @Test
    public void testLinkFlagConstructor() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("longNameEntry", TarConstants.LF_GNUTYPE_LONGNAME, false);
        assertTrue(entry.isGNULongNameEntry());
    }

    @Test
    public void testFileConstructor() throws Throwable {
        File tempFile = File.createTempFile("tarTest", ".tmp");
        tempFile.deleteOnExit();

        TarArchiveEntry entry = new TarArchiveEntry(tempFile);
        assertEquals(tempFile.getPath().replace(File.separatorChar, '/'), entry.getName());
        assertEquals(tempFile.length(), entry.getSize());
        assertTrue(entry.isFile());
        assertFalse(entry.isDirectory());
        assertEquals(tempFile, entry.getFile());

        File tempDir = tempFile.getParentFile();
        TarArchiveEntry dirEntry = new TarArchiveEntry(tempDir, tempDir.getName());
        assertTrue(dirEntry.isDirectory());
        assertEquals(tempDir, dirEntry.getFile());

        tempFile.delete();
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TarArchiveEntry entry1 = new TarArchiveEntry("file.txt");
        TarArchiveEntry entry2 = new TarArchiveEntry("file.txt");
        TarArchiveEntry entry3 = new TarArchiveEntry("other.txt");

        assertTrue(entry1.equals(entry1));
        assertTrue(entry1.equals(entry2));
        assertFalse(entry1.equals(entry3));
        assertFalse(entry1.equals(null));
        assertFalse(entry1.equals("NotATarEntry"));

        assertEquals(entry1.hashCode(), entry2.hashCode());
    }

    @Test
    public void testIsDescendent() throws Throwable {
        TarArchiveEntry parent = new TarArchiveEntry("dir/");
        TarArchiveEntry child = new TarArchiveEntry("dir/subdir/file.txt");
        TarArchiveEntry unrelated = new TarArchiveEntry("other/file.txt");

        assertTrue(parent.isDescendent(child));
        assertFalse(parent.isDescendent(unrelated));
    }

    @Test
    public void testSettersAndGetters() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("name.txt");

        entry.setName("newName.txt");
        assertEquals("newName.txt", entry.getName());

        entry.setMode(0777);
        assertEquals(0777, entry.getMode());

        entry.setLinkName("link.txt");
        assertEquals("link.txt", entry.getLinkName());

        entry.setUserId(123L);
        assertEquals(123L, entry.getLongUserId());
        assertEquals(123, entry.getUserId());
        entry.setUserId(456);
        assertEquals(456L, entry.getLongUserId());

        entry.setGroupId(789L);
        assertEquals(789L, entry.getLongGroupId());
        assertEquals(789, entry.getGroupId());
        entry.setGroupId(321);
        assertEquals(321L, entry.getLongGroupId());

        entry.setUserName("user");
        assertEquals("user", entry.getUserName());

        entry.setGroupName("group");
        assertEquals("group", entry.getGroupName());

        entry.setIds(11, 22);
        assertEquals(11L, entry.getLongUserId());
        assertEquals(22L, entry.getLongGroupId());

        entry.setNames("user2", "group2");
        assertEquals("user2", entry.getUserName());
        assertEquals("group2", entry.getGroupName());

        entry.setModTime(10000L);
        assertEquals(10000L / TarArchiveEntry.MILLIS_PER_SECOND * TarArchiveEntry.MILLIS_PER_SECOND, entry.getModTime().getTime());

        Date now = new Date();
        entry.setModTime(now);
        assertEquals(now.getTime() / TarArchiveEntry.MILLIS_PER_SECOND * TarArchiveEntry.MILLIS_PER_SECOND, entry.getModTime().getTime());

        entry.setSize(500L);
        assertEquals(500L, entry.getSize());

        entry.setDevMajor(5);
        assertEquals(5, entry.getDevMajor());

        entry.setDevMinor(10);
        assertEquals(10, entry.getDevMinor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSizeNegative() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("test");
        entry.setSize(-1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetDevMajorNegative() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("test");
        entry.setDevMajor(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetDevMinorNegative() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("test");
        entry.setDevMinor(-1);
    }

    @Test
    public void testDeviceAndLinkTypeChecks() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("test", TarConstants.LF_SYMLINK);
        assertTrue(entry.isSymbolicLink());

        entry = new TarArchiveEntry("test", TarConstants.LF_LINK);
        assertTrue(entry.isLink());

        entry = new TarArchiveEntry("test", TarConstants.LF_CHR);
        assertTrue(entry.isCharacterDevice());

        entry = new TarArchiveEntry("test", TarConstants.LF_BLK);
        assertTrue(entry.isBlockDevice());

        entry = new TarArchiveEntry("test", TarConstants.LF_FIFO);
        assertTrue(entry.isFIFO());

        entry = new TarArchiveEntry("test", TarConstants.LF_PAX_EXTENDED_HEADER_LC);
        assertTrue(entry.isPaxHeader());

        entry = new TarArchiveEntry("test", TarConstants.LF_PAX_EXTENDED_HEADER_UC);
        assertTrue(entry.isPaxHeader());

        entry = new TarArchiveEntry("test", TarConstants.LF_PAX_GLOBAL_EXTENDED_HEADER);
        assertTrue(entry.isGlobalPaxHeader());

        entry = new TarArchiveEntry("test", TarConstants.LF_GNUTYPE_SPARSE);
        assertTrue(entry.isOldGNUSparse());
        assertTrue(entry.isGNUSparse());
        assertTrue(entry.isSparse());
    }

    @Test
    public void testGetDirectoryEntries() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("notADir.txt");
        TarArchiveEntry[] children = entry.getDirectoryEntries();
        assertNotNull(children);
        assertEquals(0, children.length);

        File tempDir = File.createTempFile("tarDir", "");
        tempDir.delete();
        tempDir.mkdir();
        tempDir.deleteOnExit();

        TarArchiveEntry dirEntry = new TarArchiveEntry(tempDir);
        TarArchiveEntry[] dirChildren = dirEntry.getDirectoryEntries();
        assertNotNull(dirChildren);
        
        tempDir.delete();
    }

    @Test
    public void testWriteAndParseHeader() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("hello.txt");
        entry.setSize(1234L);
        entry.setUserId(100L);
        entry.setGroupId(200L);
        entry.setModTime(5000L);

        byte[] headerBuf = new byte[512];
        entry.writeEntryHeader(headerBuf);

        TarArchiveEntry parsedEntry = new TarArchiveEntry(headerBuf);
        assertEquals(entry.getName(), parsedEntry.getName());
        assertEquals(entry.getSize(), parsedEntry.getSize());
        assertEquals(entry.getLongUserId(), parsedEntry.getLongUserId());
        assertEquals(entry.getLongGroupId(), parsedEntry.getLongGroupId());
    }

    @Test
    public void testFillGNUSparseAndStarData() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("sparse.txt");
        Map<String, String> headers1 = new HashMap<String, String>();
        headers1.put("GNU.sparse.size", "1024");
        headers1.put("GNU.sparse.name", "newSparseName.txt");
        entry.fillGNUSparse0xData(headers1);
        assertTrue(entry.isPaxGNUSparse());
        assertEquals(1024L, entry.getRealSize());
        assertEquals("newSparseName.txt", entry.getName());

        TarArchiveEntry entry2 = new TarArchiveEntry("sparse2.txt");
        Map<String, String> headers2 = new HashMap<String, String>();
        headers2.put("GNU.sparse.realsize", "2048");
        headers2.put("GNU.sparse.name", "newSparseName2.txt");
        entry2.fillGNUSparse1xData(headers2);
        assertTrue(entry2.isPaxGNUSparse());
        assertEquals(2048L, entry2.getRealSize());
        assertEquals("newSparseName2.txt", entry2.getName());

        TarArchiveEntry entry3 = new TarArchiveEntry("star.txt");
        Map<String, String> headers3 = new HashMap<String, String>();
        headers3.put("SCHILY.realsize", "4096");
        entry3.fillStarSparseData(headers3);
        assertTrue(entry3.isStarSparse());
        assertEquals(4096L, entry3.getRealSize());
    }

    @Test
    public void testWriteEntryHeaderEncodingFallback() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("test_encoding.txt");
        byte[] headerBuf = new byte[512];
        entry.writeEntryHeader(headerBuf);
        assertTrue(true);
    }

    @Test
    public void testParseTarHeaderWithEncodingAndOldStyle() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("oldstyle.txt");
        byte[] headerBuf = new byte[512];
        entry.writeEntryHeader(headerBuf);

        TarArchiveEntry parsed = new TarArchiveEntry(headerBuf, org.apache.commons.compress.archivers.zip.ZipEncodingHelper.getZipEncoding("UTF-8"));
        assertNotNull(parsed);
    }
}