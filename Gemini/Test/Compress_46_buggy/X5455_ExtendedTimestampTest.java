package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import java.util.Date;
import java.util.zip.ZipException;

import static org.junit.Assert.*;

public class X5455_ExtendedTimestampTest {

    @Test
    public void testConstructorAndHeaderId() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        assertNotNull(x.getHeaderId());
        assertEquals(0x5455, x.getHeaderId().getValue());
    }

    @Test
    public void testDefaultState() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        assertFalse(x.isBit0_modifyTimePresent());
        assertFalse(x.isBit1_accessTimePresent());
        assertFalse(x.isBit2_createTimePresent());
        assertNull(x.getModifyTime());
        assertNull(x.getAccessTime());
        assertNull(x.getCreateTime());
        assertNull(x.getModifyJavaTime());
        assertNull(x.getAccessJavaTime());
        assertNull(x.getCreateJavaTime());
        assertEquals(1, x.getLocalFileDataLength().getValue());
        assertEquals(1, x.getCentralDirectoryLength().getValue());
    }

    @Test
    public void testSetFlags() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        x.setFlags((byte) 7);
        assertEquals(7, x.getFlags());
        assertTrue(x.isBit0_modifyTimePresent());
        assertTrue(x.isBit1_accessTimePresent());
        assertTrue(x.isBit2_createTimePresent());

        x.setFlags((byte) 0);
        assertEquals(0, x.getFlags());
        assertFalse(x.isBit0_modifyTimePresent());
        assertFalse(x.isBit1_accessTimePresent());
        assertFalse(x.isBit2_createTimePresent());
    }

    @Test
    public void testSetModifyTime() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        ZipLong zl = new ZipLong(12345678L);
        x.setModifyTime(zl);
        assertTrue(x.isBit0_modifyTimePresent());
        assertEquals(zl, x.getModifyTime());
        assertEquals((byte) 1, x.getFlags());

        x.setModifyTime(null);
        assertFalse(x.isBit0_modifyTimePresent());
        assertNull(x.getModifyTime());
        assertEquals((byte) 0, x.getFlags());
    }

    @Test
    public void testSetAccessTime() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        ZipLong zl = new ZipLong(87654321L);
        x.setAccessTime(zl);
        assertTrue(x.isBit1_accessTimePresent());
        assertEquals(zl, x.getAccessTime());
        assertEquals((byte) 2, x.getFlags());

        x.setAccessTime(null);
        assertFalse(x.isBit1_accessTimePresent());
        assertNull(x.getAccessTime());
        assertEquals((byte) 0, x.getFlags());
    }

    @Test
    public void testSetCreateTime() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        ZipLong zl = new ZipLong(11223344L);
        x.setCreateTime(zl);
        assertTrue(x.isBit2_createTimePresent());
        assertEquals(zl, x.getCreateTime());
        assertEquals((byte) 4, x.getFlags());

        x.setCreateTime(null);
        assertFalse(x.isBit2_createTimePresent());
        assertNull(x.getCreateTime());
        assertEquals((byte) 0, x.getFlags());
    }

    @Test
    public void testJavaTimeSettersAndGetters() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        Date d = new Date(1000000000L); // milliseconds

        x.setModifyJavaTime(d);
        assertNotNull(x.getModifyJavaTime());
        assertEquals(d.getTime() / 1000 * 1000, x.getModifyJavaTime().getTime());

        x.setAccessJavaTime(d);
        assertNotNull(x.getAccessJavaTime());
        assertEquals(d.getTime() / 1000 * 1000, x.getAccessJavaTime().getTime());

        x.setCreateJavaTime(d);
        assertNotNull(x.getCreateJavaTime());
        assertEquals(d.getTime() / 1000 * 1000, x.getCreateJavaTime().getTime());

        x.setModifyJavaTime(null);
        assertNull(x.getModifyJavaTime());
        x.setAccessJavaTime(null);
        assertNull(x.getAccessJavaTime());
        x.setCreateJavaTime(null);
        assertNull(x.getCreateJavaTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDateToZipLongOverflow() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        // 0x100000000L seconds or greater should throw IllegalArgumentException
        Date farFuture = new Date(0x100000000L * 1000L);
        x.setModifyJavaTime(farFuture);
    }

    @Test
    public void testLocalFileDataAndCentralDirectoryData() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        x.setModifyTime(new ZipLong(100));
        x.setAccessTime(new ZipLong(200));
        x.setCreateTime(new ZipLong(300));

        byte[] localData = x.getLocalFileDataData();
        assertEquals(13, localData.length);
        assertEquals(7, localData[0]); // flags: bit 0, 1, 2 set

        byte[] centralData = x.getCentralDirectoryData();
        assertEquals(5, centralData.length);
        assertEquals(7, centralData[0]);

        // Test with flags set but timestamps null (decoupled check)
        X5455_ExtendedTimestamp x2 = new X5455_ExtendedTimestamp();
        x2.setFlags((byte) 7);
        // timestamps are null, so lengths should be 1
        assertEquals(1, x2.getLocalFileDataLength().getValue());
        assertEquals(1, x2.getCentralDirectoryLength().getValue());
        assertEquals(1, x2.getLocalFileDataData().length);
        assertEquals(1, x2.getCentralDirectoryData().length);
    }

    @Test
    public void testParseFromLocalFileData() throws Throwable {
        byte[] data = new byte[] {
            (byte) 7, // flags
            (byte) 100, 0, 0, 0, // modify time
            (byte) 200, 0, 0, 0, // access time
            (byte) 50, 0, 0, 0   // create time
        };

        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        x.parseFromLocalFileData(data, 0, data.length);

        assertTrue(x.isBit0_modifyTimePresent());
        assertTrue(x.isBit1_accessTimePresent());
        assertTrue(x.isBit2_createTimePresent());
        assertEquals(100, x.getModifyTime().getValue());
        assertEquals(200, x.getAccessTime().getValue());
        assertEquals(50, x.getCreateTime().getValue());
    }

    @Test
    public void testParseFromCentralDirectoryData() throws Throwable {
        byte[] data = new byte[] {
            (byte) 7, // flags (central usually only has modify, but parse uses local logic)
            (byte) 123, 0, 0, 0
        };

        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        x.parseFromCentralDirectoryData(data, 0, data.length);

        assertTrue(x.isBit0_modifyTimePresent());
        assertEquals(123, x.getModifyTime().getValue());
        // Access and create should be null because length was short
        assertNull(x.getAccessTime());
        assertNull(x.getCreateTime());
    }

    @Test
    public void testParseWithShorterLengthForAccessAndCreate() throws Throwable {
        // Data contains flags and modify time, but length cuts off before create time
        byte[] data = new byte[] {
            (byte) 7, // flags: modify, access, create all flagged
            (byte) 1, 0, 0, 0, // modify
            (byte) 2, 0, 0, 0  // access, but length restricts create
        };

        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        x.parseFromLocalFileData(data, 0, 9);

        assertTrue(x.isBit0_modifyTimePresent());
        assertTrue(x.isBit1_accessTimePresent());
        assertTrue(x.isBit2_createTimePresent()); // flag is true
        assertNotNull(x.getModifyTime());
        assertNotNull(x.getAccessTime());
        assertNull(x.getCreateTime()); // but field is null due to length check
    }

    @Test
    public void testToString() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        x.setModifyTime(new ZipLong(1000));
        x.setAccessTime(new ZipLong(2000));
        x.setCreateTime(new ZipLong(3000));

        String str = x.toString();
        assertNotNull(str);
        assertTrue(str.contains("0x5455 Zip Extra Field"));
        assertTrue(str.contains("Modify:"));
        assertTrue(str.contains("Access:"));
        assertTrue(str.contains("Create:"));
    }

    @Test
    public void testClone() throws Throwable {
        X5455_ExtendedTimestamp x = new X5455_ExtendedTimestamp();
        x.setModifyTime(new ZipLong(500));
        X5455_ExtendedTimestamp clone = (X5455_ExtendedTimestamp) x.clone();

        assertEquals(x, clone);
        assertNotSame(x, clone);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        X5455_ExtendedTimestamp x1 = new X5455_ExtendedTimestamp();
        X5455_ExtendedTimestamp x2 = new X5455_ExtendedTimestamp();

        assertTrue(x1.equals(x2));
        assertEquals(x1.hashCode(), x2.hashCode());

        assertFalse(x1.equals(null));
        assertFalse(x1.equals("SomeString"));

        x1.setModifyTime(new ZipLong(10));
        assertFalse(x1.equals(x2));

        x2.setModifyTime(new ZipLong(10));
        assertTrue(x1.equals(x2));
        assertEquals(x1.hashCode(), x2.hashCode());

        x1.setAccessTime(new ZipLong(20));
        assertFalse(x1.equals(x2));
        x2.setAccessTime(new ZipLong(20));
        assertTrue(x1.equals(x2));

        x1.setCreateTime(new ZipLong(30));
        assertFalse(x1.equals(x2));
        x2.setCreateTime(new ZipLong(30));
        assertTrue(x1.equals(x2));

        // Test flags difference affecting equals/hashCode
        x1.setFlags((byte) 1);
        x2.setFlags((byte) 2);
        assertFalse(x1.equals(x2));
    }
}